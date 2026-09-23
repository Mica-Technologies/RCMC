package com.micatechnologies.minecraft.rcmc.world;

import com.micatechnologies.minecraft.rcmc.RcmcConstants;
import com.micatechnologies.minecraft.rcmc.net.PacketTrainSync;
import com.micatechnologies.minecraft.rcmc.net.PacketTransitSync;
import com.micatechnologies.minecraft.rcmc.net.RcmcNetwork;
import com.micatechnologies.minecraft.rcmc.physics.Train;
import com.micatechnologies.minecraft.rcmc.physics.block.BlockSection;
import com.micatechnologies.minecraft.rcmc.physics.block.BlockSystem;
import com.micatechnologies.minecraft.rcmc.physics.element.RideElement;
import com.micatechnologies.minecraft.rcmc.physics.element.RideElements;
import com.micatechnologies.minecraft.rcmc.physics.element.StorageBerth;
import com.micatechnologies.minecraft.rcmc.physics.element.TransferTrack;
import com.micatechnologies.minecraft.rcmc.physics.transit.LineSignals;
import com.micatechnologies.minecraft.rcmc.physics.transit.TransitPlatform;
import com.micatechnologies.minecraft.rcmc.physics.transit.TransitStation;
import com.micatechnologies.minecraft.rcmc.physics.transit.TransitSystem;
import com.micatechnologies.minecraft.rcmc.track.SectionSurgery;
import com.micatechnologies.minecraft.rcmc.track.TrackNetwork;
import com.micatechnologies.minecraft.rcmc.track.TrackNetwork.End;
import com.micatechnologies.minecraft.rcmc.track.TrackNetwork.SectionEnd;
import com.micatechnologies.minecraft.rcmc.track.TrackRef;
import com.micatechnologies.minecraft.rcmc.track.TrackSection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.world.World;

/**
 * Puts a {@link SectionSurgery} into the world: the new sections, and everything that was on the old
 * ones carried across — hardware, stations, trains, and the joins and switches at their ends.
 *
 * <p>Some things cannot be carried faithfully, and the edit is refused rather than quietly breaking
 * them: a train or a metro platform on track being turned round (its cars and doors would face the
 * wrong way), a transfer table or its storage (the two are laid to match), and line signals (a metro
 * line's blocks are laid out for the track as it was). Block sections on a coaster are cleared and
 * the player is told to lay them out again — they are one command to rebuild.</p>
 */
public final class SectionSurgeries {

    private SectionSurgeries() {
    }

    /** What happened: whether the track changed, and what to tell the player either way. */
    public static final class Outcome {
        public final boolean applied;
        public final String message;

        private Outcome(boolean applied, String message) {
            this.applied = applied;
            this.message = message;
        }

        static Outcome refused(String why) {
            return new Outcome(false, why);
        }
    }

    public static Outcome apply(World world, RcmcWorldState state, SectionSurgery.Result r, String done) {
        TrackNetwork network = state.network();
        Set<Integer> touched = new TreeSet<>();
        for (TrackSection section : network.sections()) {
            if (r.touches(section.id())) {
                touched.add(section.id());
            }
        }

        String refusal = refusal(state, r, touched);
        if (refusal != null) {
            return Outcome.refused(refusal);
        }
        double tick = RcmcConstants.SECONDS_PER_TICK;

        // The ends' joins and switches, as they stand, to rebuild once the sections are replaced.
        List<SectionEnd[]> oldJoins = new ArrayList<>();
        List<TrackNetwork.TrackSwitch> oldSwitches = new ArrayList<>();
        for (int id : touched) {
            for (End end : End.values()) {
                SectionEnd here = new SectionEnd(id, end);
                SectionEnd other = network.joinedTo(here);
                if (other != null) {
                    oldJoins.add(new SectionEnd[] {here, other});
                }
                TrackNetwork.TrackSwitch sw = network.switchInvolving(here);
                if (sw != null && !oldSwitches.contains(sw)) {
                    oldSwitches.add(sw);
                }
            }
        }

        // Hardware: cut wherever the track now parts, then each piece onto where its track went.
        int dropped = 0;
        for (RideElement element : new ArrayList<>(state.elements().elements())) {
            int id = element.sectionId();
            if (!touched.contains(id)) {
                continue;
            }
            state.elements().remove(element);
            List<RideElement> pieces = new ArrayList<>();
            pieces.add(element);
            for (double cut : r.cutsOn(id)) {
                List<RideElement> next = new ArrayList<>();
                for (RideElement piece : pieces) {
                    next.addAll(RideElements.cutAround(piece, id, cut, cut, tick));
                }
                pieces = next;
            }
            boolean reversed = r.reverses(id);
            for (RideElement piece : pieces) {
                TrackRef from = r.map(id, piece.startDistance());
                TrackRef to = r.mapEnd(id, piece.endDistance());
                if (from == null || to == null || from.sectionId() != to.sectionId()) {
                    dropped++;
                    continue;
                }
                RideElement moved = RideElements.relocated(piece, from.sectionId(), from.distance(),
                    to.distance(), reversed, d -> r.map(id, d).distance(), tick);
                if (moved == null) {
                    dropped++;
                }
                else {
                    state.elements().add(moved);
                }
            }
        }

        // Block sections are laid out for the track as it was; clear them to be laid out afresh.
        List<Integer> clearedBlocks = new ArrayList<>();
        for (int key : new ArrayList<>(state.blocks().sectionIds())) {
            BlockSystem system = state.blocks().get(key);
            boolean onEdited = touched.contains(key);
            for (BlockSection block : system.blocks()) {
                onEdited |= touched.contains(block.sectionId());
            }
            if (onEdited) {
                state.blocks().remove(key);
                clearedBlocks.add(key);
            }
        }
        for (int id : r.removedIds()) {
            state.rides().remove(id);
        }

        TransitSystem transit = state.transit();
        for (TransitStation station : new ArrayList<>(transit.stations())) {
            List<TransitPlatform> platforms = new ArrayList<>();
            boolean changed = false;
            for (TransitPlatform platform : station.platforms()) {
                TrackRef stop = platform.stopPoint();
                if (touched.contains(stop.sectionId())) {
                    platforms.add(new TransitPlatform(r.map(stop.sectionId(), stop.distance()),
                        platform.doorSide(), platform.label()));
                    changed = true;
                }
                else {
                    platforms.add(platform);
                }
            }
            if (changed) {
                transit.addStation(new TransitStation(station.name(), platforms));
            }
        }

        int dimension = world.provider.getDimension();
        for (Map.Entry<Integer, Train> entry : state.trains().asMap().entrySet()) {
            Train train = entry.getValue();
            TrackRef at = train.reference();
            if (at != null && touched.contains(at.sectionId())) {
                train.setState(r.map(at.sectionId(), at.distance()), train.velocity());
                RcmcNetwork.sendToAllIn(new PacketTrainSync(entry.getKey(), train), dimension);
            }
        }

        // The track itself, then its ends' connections, mapped onto the new ends.
        for (int id : touched) {
            network.removeSection(id);
        }
        for (TrackSection section : r.sections()) {
            network.addSection(section);
        }
        List<String> lost = new ArrayList<>();
        for (SectionEnd[] join : oldJoins) {
            SectionEnd a = r.endOf(join[0]);
            SectionEnd b = r.touches(join[1].sectionId) ? r.endOf(join[1]) : join[1];
            if (a == null || b == null || b.equals(network.joinedTo(a))) {
                continue;
            }
            try {
                network.connect(a, b);
            }
            catch (IllegalArgumentException e) {
                lost.add("the join at " + a);
            }
        }
        double kink = 0.0D;
        for (SectionEnd[] join : r.joins()) {
            try {
                network.connect(join[0], join[1]);
                com.micatechnologies.minecraft.rcmc.track.JoinAlignment alignment = network.alignmentOf(join[0]);
                if (alignment != null) {
                    kink = Math.max(kink, alignment.tangentAngleDegrees);
                }
            }
            catch (IllegalArgumentException e) {
                lost.add("the join at " + join[0]);
            }
        }
        for (TrackNetwork.TrackSwitch sw : oldSwitches) {
            SectionEnd throat = mapped(r, sw.throat());
            List<SectionEnd> branches = new ArrayList<>();
            for (SectionEnd branch : sw.branches()) {
                branches.add(mapped(r, branch));
            }
            try {
                network.addSwitch(throat, branches);
                network.setSwitchSelection(throat, sw.selectedIndex());
            }
            catch (IllegalArgumentException e) {
                lost.add("the switch at " + throat);
            }
        }

        state.markTrackDirty(world);
        RcmcNetwork.sendToAllIn(new com.micatechnologies.minecraft.rcmc.net.PacketTrackSync(network), dimension);
        RcmcNetwork.sendToAllIn(new com.micatechnologies.minecraft.rcmc.net.PacketElementSync(state.elements()),
            dimension);
        RcmcNetwork.sendToAllIn(new PacketTransitSync(transit), dimension);

        StringBuilder message = new StringBuilder(done);
        if (kink >= 1.0D) {
            message.append(String.format(" The join turns %.0f°.", kink));
        }
        if (!clearedBlocks.isEmpty()) {
            message.append(" Block sections cleared; lay them out again with /rcmc block <id> auto.");
        }
        if (dropped > 0) {
            message.append(" ").append(dropped).append(" piece(s) of hardware too short to keep were removed.");
        }
        if (!lost.isEmpty()) {
            message.append(" Could not keep ").append(String.join(", ", lost)).append(".");
        }
        return new Outcome(true, message.toString());
    }

    private static SectionEnd mapped(SectionSurgery.Result r, SectionEnd end) {
        return r.touches(end.sectionId) ? r.endOf(end) : end;
    }

    /** Why the edit would break something it cannot carry, or {@code null} when it can go ahead. */
    private static String refusal(RcmcWorldState state, SectionSurgery.Result r, Set<Integer> touched) {
        for (int id : touched) {
            if (!r.reverses(id)) {
                continue;
            }
            for (Train train : state.trains().asMap().values()) {
                if (train.reference() != null && train.reference().sectionId() == id) {
                    return "Section #" + id + " has a train on it, which would end up facing backwards. "
                        + "Take the trains off first.";
                }
            }
            for (TransitStation station : state.transit().stations()) {
                for (TransitPlatform platform : station.platforms()) {
                    if (platform.stopPoint().sectionId() == id) {
                        return "Section #" + id + " has a platform of " + station.name()
                            + " on it, which would turn round with its doors on the wrong side. "
                            + "Remove the platform first.";
                    }
                }
            }
        }
        for (RideElement element : state.elements().elements()) {
            boolean linked = element instanceof TransferTrack && ((TransferTrack) element).isLinked();
            if (!linked && !(element instanceof StorageBerth)) {
                continue;
            }
            boolean involved = touched.contains(element.sectionId());
            if (linked) {
                involved |= touched.contains(((TransferTrack) element).storageSectionId());
            }
            if (involved) {
                return "A transfer table or its storage is on this track, and the two are laid to match. "
                    + "Unlink it first with /rcmc transfer <id> off.";
            }
        }
        for (Map.Entry<String, LineSignals> entry : state.transit().signals().entrySet()) {
            for (BlockSection block : entry.getValue().blocks()) {
                if (touched.contains(block.sectionId())) {
                    return "Line " + entry.getKey() + " is signalled over this track. Clear its signals first.";
                }
            }
        }
        for (TrackNetwork.TrackSwitch sw : state.network().switches()) {
            List<SectionEnd> ends = new ArrayList<>(sw.branches());
            ends.add(sw.throat());
            for (SectionEnd end : ends) {
                if (r.consumes(end)) {
                    return "The end at " + end + " belongs to a switch. Remove the switch first.";
                }
            }
        }
        return null;
    }
}
