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
import com.micatechnologies.minecraft.rcmc.physics.transit.LineSignals;
import com.micatechnologies.minecraft.rcmc.physics.transit.TransitPlatform;
import com.micatechnologies.minecraft.rcmc.physics.transit.TransitStation;
import com.micatechnologies.minecraft.rcmc.physics.transit.TransitSystem;
import com.micatechnologies.minecraft.rcmc.track.TrackRef;
import com.micatechnologies.minecraft.rcmc.track.TrackSection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.DoubleUnaryOperator;
import net.minecraft.world.World;

/**
 * Puts an edited section into the world, carrying everything placed along it to where it now is.
 *
 * <p>Ride hardware, block sections, station stop points, line signals and trains all sit at
 * distances along a section, and an edited section has different distances. Replacing the geometry
 * alone would leave every one of them where the old numbers put it on the new track — a chain lift
 * ending mid-drop, a platform beside the wrong stretch, a train teleported. {@link #apply} moves
 * them all through the edit's {@code SectionRemap}, then saves, records the edit for undo, and tells
 * every client.</p>
 */
public final class TrackEdits {

    private TrackEdits() {
    }

    /** Replaces {@code edited}'s section with it and carries everything on it through {@code remap}. */
    public static void apply(World world, RcmcWorldState state, TrackSection edited,
                             DoubleUnaryOperator remap) {
        int sectionId = edited.id();
        double tick = RcmcConstants.SECONDS_PER_TICK;
        state.network().replaceSection(edited);

        for (RideElement element : new ArrayList<>(state.elements().elements())) {
            RideElement moved = RideElements.moved(element, sectionId, remap, tick);
            if (moved != element) {
                state.elements().replace(element, moved);
            }
        }

        BlockSystem blocks = state.blocks().get(sectionId);
        if (blocks != null) {
            BlockSystem moved = new BlockSystem(blocks.isClosedCircuit(), blocks.isSafetyEnabled(),
                blocks.brakeDeceleration(), tick);
            for (BlockSection block : blocks.blocks()) {
                moved.addBlock(movedBlock(block, sectionId, remap));
            }
            state.blocks().put(sectionId, moved);
        }

        TransitSystem transit = state.transit();
        for (TransitStation station : new ArrayList<>(transit.stations())) {
            List<TransitPlatform> platforms = new ArrayList<>();
            boolean changed = false;
            for (TransitPlatform platform : station.platforms()) {
                TrackRef stop = platform.stopPoint();
                if (stop.sectionId() == sectionId) {
                    platforms.add(new TransitPlatform(
                        new TrackRef(sectionId, remap.applyAsDouble(stop.distance())),
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
        for (Map.Entry<String, LineSignals> entry : new ArrayList<>(transit.signals().entrySet())) {
            LineSignals signals = entry.getValue();
            List<BlockSection> moved = new ArrayList<>();
            boolean changed = false;
            for (BlockSection block : signals.blocks()) {
                moved.add(movedBlock(block, sectionId, remap));
                changed |= block.sectionId() == sectionId;
            }
            if (changed) {
                transit.setSignals(entry.getKey(), new LineSignals(moved, signals.margin(), signals.horizon()));
            }
        }

        int dimension = world.provider.getDimension();
        for (Map.Entry<Integer, Train> entry : state.trains().asMap().entrySet()) {
            Train train = entry.getValue();
            TrackRef at = train.reference();
            if (at != null && at.sectionId() == sectionId) {
                train.setState(new TrackRef(sectionId, remap.applyAsDouble(at.distance())), train.velocity());
                RcmcNetwork.sendToAllIn(new PacketTrainSync(entry.getKey(), train), dimension);
            }
        }

        // Services keep the berth they are running to; its stop point may just have moved.
        for (com.micatechnologies.minecraft.rcmc.physics.transit.LineService service
            : state.transit().services().values()) {
            service.refreshBerth();
        }
        state.markTrackDirty(world);
        RcmcNetwork.sendToAllIn(new com.micatechnologies.minecraft.rcmc.net.PacketTrackSync(state.network()),
            dimension);
        RcmcNetwork.sendToAllIn(new com.micatechnologies.minecraft.rcmc.net.PacketElementSync(state.elements()),
            dimension);
        RcmcNetwork.sendToAllIn(new PacketTransitSync(transit), dimension);
    }

    private static BlockSection movedBlock(BlockSection block, int sectionId, DoubleUnaryOperator remap) {
        if (block.sectionId() != sectionId) {
            return block;
        }
        double from = remap.applyAsDouble(block.startDistance());
        double to = remap.applyAsDouble(block.endDistance());
        return block.wraps()
            ? BlockSection.wrapping(block.id(), sectionId, from, Math.min(to, from))
            : new BlockSection(block.id(), sectionId, from, Math.max(from, to));
    }
}
