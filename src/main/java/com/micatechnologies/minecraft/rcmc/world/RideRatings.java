package com.micatechnologies.minecraft.rcmc.world;

import com.micatechnologies.minecraft.rcmc.RcmcConfig;
import com.micatechnologies.minecraft.rcmc.physics.PhysicsIntegrator;
import com.micatechnologies.minecraft.rcmc.physics.TrainSpec;
import com.micatechnologies.minecraft.rcmc.physics.element.StationPlatform;
import com.micatechnologies.minecraft.rcmc.rating.RideRater;
import com.micatechnologies.minecraft.rcmc.rating.RideRating;
import com.micatechnologies.minecraft.rcmc.rating.RideStatistics;
import java.util.HashMap;
import java.util.Map;

/**
 * Each ride's rating, worked out once and kept until the ride changes.
 *
 * <p>A rating is a simulated lap — cheap once, not something to redo every time a sign refreshes.
 * So it is cached per ride, against what it was worked out from: the park's
 * {@link RcmcWorldState#trackVersion() track version}, and the train the ride runs. Any edit to
 * track or hardware, or a different train, and the next sign to ask gets a fresh one.</p>
 */
public final class RideRatings {

    /** A ride's rating, with the lap statistics it came from. */
    public static final class Entry {
        public final RideStatistics stats;
        public final RideRating rating;
        final long version;
        final TrainSpec train;

        Entry(RideStatistics stats, RideRating rating, long version, TrainSpec train) {
            this.stats = stats;
            this.rating = rating;
            this.version = version;
            this.train = train;
        }
    }

    /** Per world, by the ride's home section. */
    private static final Map<RcmcWorldState, Map<Integer, Entry>> CACHE = new java.util.WeakHashMap<>();

    private RideRatings() {
    }

    /** The rating of the ride {@code sectionId} is part of, or {@code null} if it has no station. */
    public static synchronized Entry of(RcmcWorldState state, int sectionId) {
        StationPlatform station = CoasterStations.stationOf(state, sectionId);
        if (station == null || !state.network().hasSection(station.sectionId())) {
            return null;
        }
        int home = state.rides().home(sectionId);
        TrainSpec train = RideChecks.trainFor(state, state.rides().members(sectionId));
        Map<Integer, Entry> rides = CACHE.computeIfAbsent(state, k -> new HashMap<>());
        Entry cached = rides.get(home);
        if (cached != null && cached.version == state.trackVersion() && cached.train.equals(train)) {
            return cached;
        }
        RideRater rater = RideRater.standard(new PhysicsIntegrator(RcmcConfig.gravity,
            RcmcConfig.rollingResistance, RcmcConfig.airDrag, RcmcConfig.maxSpeed), RcmcConfig.gravity);
        RideStatistics stats = rater.simulateRide(state.network(), state.elements(), station.sectionId(), train);
        Entry entry = new Entry(stats, RideRating.from(stats), state.trackVersion(), train);
        rides.put(home, entry);
        return entry;
    }
}
