package com.micatechnologies.minecraft.rcmc.physics.transit;

import com.micatechnologies.minecraft.rcmc.track.TrackRef;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * One named stop on a {@link TransitLine}: where trains berth, and what the signage calls it.
 *
 * <p>A station is a <em>place</em>, and a place can have more than one track through it. Each of
 * those is a {@link TransitPlatform} carrying its own stop point and door side. A station with one
 * platform — which is every station authored before platforms existed, and every station on a
 * simple single-track line — behaves exactly as it always did; read that platform through
 * {@link #stopPoint()} and {@link #doorSide()}, which are kept for the many callers that legitimately
 * only care about the primary berth.</p>
 *
 * <p><b>Platform geometry is still not stored here.</b> Span, decking and edging belong to the
 * world-facing blocks. What lives here is only what the route logic, the signage and the doors
 * need, which is a berth position, a side, and a name to call it by.</p>
 *
 * <p>Immutable, pure Java.</p>
 */
public final class TransitStation {

    private final String name;
    private final List<TransitPlatform> platforms;

    public TransitStation(String name, TrackRef stopPoint) {
        this(name, stopPoint, DoorSide.BOTH);
    }

    public TransitStation(String name, TrackRef stopPoint, DoorSide doorSide) {
        this(name, Collections.singletonList(new TransitPlatform(stopPoint, doorSide, "")));
    }

    public TransitStation(String name, List<TransitPlatform> platforms) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("a station needs a name — the signage renders it");
        }
        if (platforms == null || platforms.isEmpty()) {
            throw new IllegalArgumentException(
                "a station needs at least one platform — there is nowhere to berth otherwise");
        }
        for (TransitPlatform platform : platforms) {
            if (platform == null) {
                throw new IllegalArgumentException("a station's platforms must all be present");
            }
        }
        this.name = name;
        this.platforms = Collections.unmodifiableList(new ArrayList<>(platforms));
    }

    public String name() {
        return name;
    }

    /** Every berth at this station, in authoring order. Never empty. */
    public List<TransitPlatform> platforms() {
        return platforms;
    }

    public int platformCount() {
        return platforms.size();
    }

    public TransitPlatform platform(int index) {
        return platforms.get(index);
    }

    /**
     * The primary platform: the first authored, and the only one at a single-track station.
     *
     * <p>Callers that serve a <em>particular train</em> want the platform that train will actually
     * reach, not this one — on an island served from both sides the two differ, and picking wrong
     * opens the doors at the tunnel wall.</p>
     */
    public TransitPlatform primary() {
        return platforms.get(0);
    }

    /** The berth with this label, or {@code null}. Case-insensitive; empty matches nothing. */
    public TransitPlatform platform(String label) {
        if (label == null || label.isEmpty()) {
            return null;
        }
        for (TransitPlatform platform : platforms) {
            if (platform.label().equalsIgnoreCase(label)) {
                return platform;
            }
        }
        return null;
    }

    /** The primary platform's stop point — see {@link #primary()} before using this per-train. */
    public TrackRef stopPoint() {
        return primary().stopPoint();
    }

    /** The primary platform's door side — see {@link #primary()} before using this per-train. */
    public DoorSide doorSide() {
        return primary().doorSide();
    }

    /**
     * A copy whose platforms all open on {@code side}.
     *
     * <p>Applied to every platform rather than only the primary, so that authoring a side on a
     * single-platform station means exactly what it always did. A station whose tracks want
     * different sides authors them per platform instead.</p>
     */
    public TransitStation withDoorSide(DoorSide side) {
        List<TransitPlatform> updated = new ArrayList<>(platforms.size());
        for (TransitPlatform platform : platforms) {
            updated.add(platform.withDoorSide(side));
        }
        return new TransitStation(name, updated);
    }

    /** A copy with {@code platform} appended. */
    public TransitStation withPlatform(TransitPlatform platform) {
        List<TransitPlatform> updated = new ArrayList<>(platforms);
        updated.add(platform);
        return new TransitStation(name, updated);
    }

    /** A copy with {@code platform} replacing the one at {@code index}. */
    public TransitStation withPlatformAt(int index, TransitPlatform platform) {
        List<TransitPlatform> updated = new ArrayList<>(platforms);
        updated.set(index, platform);
        return new TransitStation(name, updated);
    }

    public TransitStation withPlatforms(TransitPlatform... replacements) {
        return new TransitStation(name, Arrays.asList(replacements));
    }

    @Override
    public String toString() {
        return "TransitStation{" + name + " " + platforms + '}';
    }
}
