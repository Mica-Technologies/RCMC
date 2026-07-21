package com.micatechnologies.minecraft.rcmc.physics.block;

import com.micatechnologies.minecraft.rcmc.physics.Train;
import com.micatechnologies.minecraft.rcmc.physics.TrainManager;
import com.micatechnologies.minecraft.rcmc.track.TrackNetwork;
import com.micatechnologies.minecraft.rcmc.track.TrackRef;
import com.micatechnologies.minecraft.rcmc.track.TrackSection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The block sections belonging to one circuit, and the {@link TrainManager.ExternalAcceleration}
 * that turns "which block is occupied" into "hold this train at the boundary".
 *
 * <p>This is what makes running more than one train on a circuit safe: each {@link BlockSection}
 * may hold at most one train, and a train approaching a block whose next block is already occupied
 * is braked to a stop at the boundary rather than being allowed to enter it. It is the physics-side
 * counterpart of a real block signal — see {@code BrakeRun}'s javadoc for why a block-mode brake
 * run is the piece of hardware this rides on top of, both there and here.</p>
 *
 * <h2>Two-phase use — read this before wiring it up</h2>
 *
 * <p>Unlike {@code RideElementSet}, which can answer "what acceleration does this train feel"
 * purely from that one train's own position, a block system's answer genuinely depends on
 * <em>every other train's</em> position too. If occupancy were recomputed lazily inside
 * {@link #forTrain}, the answer for the second train {@link TrainManager#tick} visits in a given
 * tick would depend on whether the first train had already been advanced by the time it was asked
 * — an order dependency that would make the simulation's behaviour depend on
 * {@code TrainManager}'s internal iteration order rather than being a genuine function of the
 * tick's starting state. So occupancy is computed once, as an explicit snapshot, by
 * {@link #updateOccupancy}, which callers <b>must</b> invoke once per tick <em>before</em> handing
 * this object to {@link TrainManager#tick} as the {@code ExternalAcceleration}:</p>
 *
 * <pre>{@code
 * blockSystem.updateOccupancy(trainManager, network);
 * trainManager.tick(network, blockSystem, subSteps, tickSeconds);
 * }</pre>
 *
 * <p>{@link #updateOccupancy} also takes the {@link TrackNetwork} — pure Java, not a Minecraft
 * type, so this does not compromise the package's zero-Minecraft-types rule — because computing a
 * correct distance to a block that lies across the seam of a closed circuit (see "wrap" below)
 * needs to know that section's total length, and {@link #forTrain} cannot be given the network
 * directly: its signature is fixed by {@link TrainManager.ExternalAcceleration}. The distance is
 * therefore resolved once, during the snapshot, and cached for {@link #forTrain} to read back.</p>
 *
 * <h2>Occupancy is tracked by lead-car reference only</h2>
 *
 * <p>A train is considered "in" whichever block contains {@code train.reference()} — the lead
 * car's position — exactly the simplification {@code RideElementSet} already makes for ride
 * elements. For a single-car train this is exact. For a multi-car train, the rear cars can still
 * physically occupy the block behind the one the lead car has entered, for as long as the train's
 * length takes to fully cross the boundary. This is why real block signalling requires blocks to
 * be sized comfortably longer than the longest train that will run through them, and why this
 * class does not attempt the more expensive per-car check: it would still be an approximation
 * (parallel transport, not a physical hull), and getting the sizing right is a park-authoring
 * responsibility this class cannot discharge for the author. {@link #detectCollisions} — used only
 * when safety is disabled, see below — is the one place this class <em>does</em> use full train
 * length, precisely because that check exists to catch exactly this kind of overlap.</p>
 *
 * <h2>Braking targets the next block's entrance, not the current block's exit</h2>
 *
 * <p>A held train is braked to stop just short of the <em>next</em> block's start, not at the end
 * of the block it is currently in. When blocks are contiguous these are the same point, but when a
 * park author leaves neutral track between blocks (see above), they are not — and the difference
 * is exactly what makes that neutral track usable slack rather than dead space. Because occupancy
 * exclusion ({@link #occupancy}) is a fresh, non-sticky snapshot every tick, a block reads as free
 * the instant its occupant's lead reference physically leaves it, even if that occupant is itself
 * still coasting toward a hold further on. A following train aimed at the far side of a generous
 * gap therefore often finds its target block already vacated by the time it gets there, and never
 * has to brake at all — see {@code BlockSystemTest#threeTrainsThreeBlocksWithSlackNeverDeadlock}.
 * Since braking must still work while a train is physically in that neutral gap — no longer inside
 * any block, so {@link #occupancy} alone has nothing to say — {@link #referenceBlock} falls back to
 * {@link #lastEnteredBlock}, a per-train pointer to the most recent block it was ever literally
 * inside, updated every tick that pointer is available and left untouched while coasting through a
 * gap. That pointer is used <em>only</em> to work out which block is "next" for braking purposes; it
 * never feeds {@link #occupancy}, {@link #blockOf}, or {@link #blockIndexOf}, which all report a
 * train's <em>literal</em> current block (or none) and nothing else — conflating the two would let
 * a train's stale, already-vacated block still count as "occupied" against everyone else, defeating
 * the entire point.</p>
 *
 * <h2>Wrap, and its one real limitation</h2>
 *
 * <p>On a closed circuit, the block after the last one added is the first: {@link #nextIndex}
 * wraps when {@code closedCircuit} is set. The distance calculation has to wrap with it. If the
 * last block ends near a section's high distance and the first starts near {@code 0}, "how far to
 * the first block's entrance" is <em>not</em> {@code firstBlock.startDistance() - here} (a large
 * negative number) but that plus the section's total length. Get this wrong and a train that has
 * just wrapped onto the last block computes a wildly incorrect, deeply negative "remaining"
 * distance to a block it is nowhere near yet, which reads as an emergency and triggers full braking
 * immediately — not hypothetical: it is exactly what an earlier, unwrapped version of this class
 * did, caught by a train reversing into oncoming traffic in an early draft of
 * {@code BlockSystemTest}. {@link #remainingToNextBlockEntrance} resolves this using the relevant
 * section's {@link TrackSection#totalLength()}, which is why {@link #updateOccupancy} needs the
 * network. <b>Limitation:</b> the correction only applies when the wrapping pair of blocks (last
 * and first) sit on the <em>same</em> {@code TrackSection} — the ordinary case for a circuit, one
 * closed section, exactly like {@code TrainTest}'s ring helper. A circuit built from several joined
 * sections, wrapping from a block on one section to a block on another, falls back to the naive,
 * potentially-wrong subtraction: flagged here rather than shipped silently, and worth checking
 * before authoring a multi-section closed block sequence.</p>
 *
 * <h2>Deadlock — read this before shipping an N-train, N-block circuit</h2>
 *
 * <p><b>Fixed, exclusive block signalling cannot guarantee forward progress when the number of
 * running trains equals the number of blocks and the blocks tile the circuit with no spare
 * capacity.</b> This is not a bug in this implementation; it is a structural property of the
 * model, provable independently of how the braking law is written. If every block holds exactly
 * one train, then every train's next block is — by the pigeonhole principle — always occupied by
 * some other train. A block-mode brake, by construction, only ever removes energy (see
 * {@code BrakeRun}), so once a held train reaches zero velocity at its boundary it has no way to
 * regain speed on its own; that zero-velocity state is absorbing. Given enough time, every train
 * in a fully saturated ring eventually reaches its own boundary and latches there, and once one
 * has, the block behind it stays permanently occupied, propagating the same fate to the train
 * behind that one, all the way around. The result is a stable, whole-ring gridlock with every
 * train reporting {@link Train.Status#RUNNING} — not faulted, just permanently stationary — which
 * is the "quietly fails" failure mode this whole feature exists to avoid presenting elsewhere.
 * {@code BlockSystemTest#exactlySaturatedRingDeadlocks} demonstrates this directly rather than
 * hiding it.</p>
 *
 * <p>The fix is the one real block-signalled railways and coasters use: <b>always provision more
 * block capacity than running trains</b> — either more blocks than trains, or blocks that do not
 * tile the whole circuit, leaving neutral track between them that belongs to no block and so never
 * holds anyone up. Either gives the ring the one spare "slot" a fully-saturated ring structurally
 * lacks. {@code BlockSystemTest#threeTrainsThreeBlocksWithSlackNeverDeadlock} is the same 3-train,
 * 3-block layout with that slack added, and it runs indefinitely — but only because the braking
 * target is the <em>entrance of the next block</em>, not the exit of the train's own block (see
 * {@link #brakeTowardBoundary}); targeting the train's own exit throws the slack away completely
 * and reintroduces the deadlock regardless of how much neutral track separates the blocks, since a
 * train that stops the instant it leaves its own block never uses the gap at all. This class does
 * not attempt to detect or break a deadlock itself — no timeout-based override, no dispatcher
 * priority — because doing so safely (which train gets to violate the block first, and by how much)
 * is a ride-control policy decision, not a physics one. What it does do is make a stuck train
 * visible rather than silent: see {@link #stuckTicks} and {@link #isStuck}.</p>
 *
 * <h2>Safety-disabled mode: collisions are a feature</h2>
 *
 * <p>Constructing this with {@code safetyEnabled = false} (or calling {@link #setSafetyEnabled})
 * turns off holding entirely — {@link #forTrain} always returns {@code 0.0} and
 * {@link #isHolding} always returns {@code false} — so trains run exactly as if no block system
 * existed and are free to run into each other, exactly like RCT with block brakes turned off.
 * {@link #detectCollisions}, which runs regardless of the safety flag, is how a caller finds out
 * that happened: see {@link Collision}.</p>
 */
public final class BlockSystem implements TrainManager.ExternalAcceleration {

    /**
     * Speed below which a held train is considered genuinely at rest for the purposes of
     * {@link #stuckTicks} — deliberately the same order of magnitude as
     * {@code StationPlatform.STOP_SPEED_EPSILON}, for the same reason: it must sit comfortably
     * above {@code Train}'s own internal stopped threshold so this class's own bookkeeping is not
     * itself racing that check.
     */
    private static final double AT_REST_EPSILON = 0.05D;

    /**
     * Default number of consecutive at-rest, held ticks before {@link #isStuck} reports true — ten
     * seconds at the standard 20 ticks/second. Arbitrary in the sense that any real-time threshold
     * is, but generous enough that no ordinary block-clearing delay (another train dwelling in a
     * station, say) trips it, while still surfacing a genuine deadlock well within a play session.
     */
    public static final int DEFAULT_STUCK_TICKS = 200;

    /**
     * How far short of a block's start a held train is brought to rest. Strictly necessary, not
     * cosmetic: the stopping profile in {@link #brakeTowardBoundary} lands the train's velocity at
     * exactly zero exactly at its target distance (see {@code StationPlatform}'s identical
     * approach-phase profile), and {@link BlockSection#contains} is inclusive at its start — so a
     * target of exactly {@code startDistance} would leave the held train's own final resting
     * position reading as "inside" the very block it was stopped to keep out of.
     */
    private static final double ENTRY_MARGIN = 0.05D;

    /**
     * Fraction of {@link #brakeDeceleration} the stopping curve is <em>planned</em> against, even
     * though the full value is what actually gets applied once braking engages. This is a safety
     * margin, not a style choice, and it exists to fix a real bug: {@link #forTrain} — like every
     * other element's {@code accelerationFor} in this codebase — is evaluated once per outer game
     * tick and its result held constant across that whole tick (see {@code ElementTestSupport}'s
     * javadoc), not recomputed continuously. A train can therefore travel for up to one full tick
     * at its pre-brake speed before this class gets another chance to react. Near the tail of a
     * {@code v = sqrt(2·a·remaining)} curve that lag is not a small, bounded error: {@code dv/ds}
     * diverges as {@code remaining → 0}, so a one-tick-old decision compounds into a real distance
     * overshoot right where there is the least room for one — measured directly (before this
     * factor existed) at several tenths of a block for an 8 blocks/s entry against a 4 blocks/s²
     * brake, comfortably enough to carry a "held" train into the block it was stopped to keep out
     * of. Planning against a curve that only assumes half the available deceleration, while still
     * braking at the full rate once triggered, leaves exactly that much spare stopping power in
     * reserve to absorb the lag. {@code StationPlatform}'s identical-looking approach phase does not
     * need this: overshooting a station's {@code stopDistance} by a few tenths of a block is a
     * documented non-issue there, whereas overshooting a block boundary is the one thing this class
     * exists to prevent.
     */
    private static final double PLANNING_DECELERATION_FACTOR = 0.5D;

    private final List<BlockSection> blocks = new ArrayList<>();
    private final boolean closedCircuit;
    private final double brakeDeceleration;
    private final double tickSeconds;
    private final int stuckTicksThreshold;

    private boolean safetyEnabled;

    /**
     * trainId -> index into {@link #blocks}, for every train whose lead reference is <em>literally,
     * right now</em> inside some block. Recomputed from scratch every {@link #updateOccupancy} —
     * never sticky — because this is the map every train's "is the block ahead of me occupied"
     * check reads, and it must report a block free the instant its occupant's reference leaves it.
     */
    private final Map<Integer, Integer> occupancy = new LinkedHashMap<>();

    /**
     * trainId -> index of the most recent block that train was ever literally inside. Unlike
     * {@link #occupancy}, this persists while a train coasts through neutral track between blocks —
     * see {@link #referenceBlock} and the class javadoc's "Braking targets the next block's
     * entrance" section for why braking needs this and occupancy exclusion must not use it.
     */
    private final Map<Integer, Integer> lastEnteredBlock = new LinkedHashMap<>();

    /**
     * trainId -> wrap-aware distance remaining to the entrance of that train's next block, resolved
     * once per {@link #updateOccupancy} call (where the {@link TrackNetwork} is available) and read
     * back by {@link #forTrain}, whose signature cannot take the network directly. See "Wrap, and
     * its one real limitation" above.
     */
    private final Map<Integer, Double> nextEntranceRemaining = new LinkedHashMap<>();

    /** trainId -> consecutive ticks spent held and at rest; see {@link #stuckTicks}. */
    private final Map<Integer, Integer> heldAtRestTicks = new LinkedHashMap<>();

    private final List<Collision> collisions = new ArrayList<>();

    /**
     * @param closedCircuit       whether the block after the last in {@link #addBlock} order wraps
     *                            back to the first — the block-sequence analogue of
     *                            {@code TrackSection.isClosed}, but a deliberately separate flag:
     *                            a park author can legitimately run an open block sequence over a
     *                            closed track (a spur that never wraps) or vice versa
     * @param safetyEnabled       whether holding is active; see the class javadoc's "safety
     *                            disabled" section
     * @param brakeDeceleration   magnitude of the deceleration a held train is braked at, blocks/s²
     *                            — must be positive regardless of {@code safetyEnabled}, so
     *                            toggling safety on later does not require reconstructing this
     * @param tickSeconds         length of the game tick this system is evaluated at
     * @param stuckTicksThreshold see {@link #isStuck}; must be positive
     */
    public BlockSystem(boolean closedCircuit, boolean safetyEnabled, double brakeDeceleration,
                        double tickSeconds, int stuckTicksThreshold) {
        if (brakeDeceleration <= 0.0D) {
            throw new IllegalArgumentException(
                "brakeDeceleration must be positive, got " + brakeDeceleration);
        }
        if (tickSeconds <= 0.0D) {
            throw new IllegalArgumentException("tickSeconds must be positive, got " + tickSeconds);
        }
        if (stuckTicksThreshold <= 0) {
            throw new IllegalArgumentException(
                "stuckTicksThreshold must be positive, got " + stuckTicksThreshold);
        }
        this.closedCircuit = closedCircuit;
        this.safetyEnabled = safetyEnabled;
        this.brakeDeceleration = brakeDeceleration;
        this.tickSeconds = tickSeconds;
        this.stuckTicksThreshold = stuckTicksThreshold;
    }

    /** Convenience constructor using {@link #DEFAULT_STUCK_TICKS}. */
    public BlockSystem(boolean closedCircuit, boolean safetyEnabled, double brakeDeceleration,
                        double tickSeconds) {
        this(closedCircuit, safetyEnabled, brakeDeceleration, tickSeconds, DEFAULT_STUCK_TICKS);
    }

    /**
     * Appends a block to the sequence. Order matters: it defines which block is "next" for every
     * other block, wrapping from the last back to the first iff {@code closedCircuit}.
     */
    public void addBlock(BlockSection block) {
        if (block == null) {
            throw new IllegalArgumentException("block must not be null");
        }
        blocks.add(block);
    }

    public List<BlockSection> blocks() {
        return Collections.unmodifiableList(blocks);
    }

    public int blockCount() {
        return blocks.size();
    }

    public boolean isSafetyEnabled() {
        return safetyEnabled;
    }

    /** Turns holding on or off at runtime — an operator's "block brakes" switch. */
    public void setSafetyEnabled(boolean safetyEnabled) {
        this.safetyEnabled = safetyEnabled;
    }

    public boolean isClosedCircuit() {
        return closedCircuit;
    }

    /**
     * Recomputes, from scratch, which block (if any) each train in {@code trains} currently
     * occupies, and which pairs of trains currently overlap. Must be called once per tick before
     * this object is used as a {@link TrainManager.ExternalAcceleration} — see the class javadoc.
     *
     * @param network needed to resolve distances across a closed-circuit wrap seam and to check
     *                for wrapped collisions — see "Wrap, and its one real limitation" above
     */
    public void updateOccupancy(TrainManager trains, TrackNetwork network) {
        occupancy.clear();
        for (Map.Entry<Integer, Train> entry : trains.asMap().entrySet()) {
            int index = blockIndexContaining(entry.getValue().reference());
            if (index >= 0) {
                occupancy.put(entry.getKey(), index);
                lastEnteredBlock.put(entry.getKey(), index);
            }
        }
        // Drop bookkeeping for trains that no longer exist at all, so removing a train does not
        // leak an entry forever — but a train merely coasting through a gap keeps its entry, which
        // is the entire point of lastEnteredBlock.
        lastEnteredBlock.keySet().retainAll(trains.asMap().keySet());

        nextEntranceRemaining.clear();
        for (Map.Entry<Integer, Train> entry : trains.asMap().entrySet()) {
            int trainId = entry.getKey();
            Integer index = referenceBlock(trainId);
            if (index == null) {
                continue;
            }
            int next = nextIndex(index);
            if (next < 0) {
                continue;
            }
            nextEntranceRemaining.put(trainId,
                remainingToNextBlockEntrance(network, index, next, entry.getValue().reference()));
        }

        updateStuckTracking(trains);
        detectCollisions(trains, network);
    }

    /**
     * The block index braking decisions for {@code trainId} should be measured against: its
     * literal current block if it is inside one, otherwise the last block it was ever inside (see
     * {@link #lastEnteredBlock}), otherwise {@code null} for a train that has not yet entered any
     * tracked block at all — which this system correctly has no opinion about.
     */
    private Integer referenceBlock(int trainId) {
        Integer literal = occupancy.get(trainId);
        return literal != null ? literal : lastEnteredBlock.get(trainId);
    }

    /**
     * Signed distance, in blocks, from {@code ref} forward to {@code blocks.get(next)}'s entrance
     * (already offset inward by {@link #ENTRY_MARGIN}) — wrap-corrected when {@code next} is the
     * wrap-around case (closing the ring from the last block back to the first) and the two blocks
     * share a section. See "Wrap, and its one real limitation" in the class javadoc.
     */
    private double remainingToNextBlockEntrance(TrackNetwork network, int index, int next, TrackRef ref) {
        BlockSection nextBlock = blocks.get(next);
        double remaining = (nextBlock.startDistance() - ENTRY_MARGIN) - ref.distance();

        boolean wraps = closedCircuit && index + 1 >= blocks.size();
        if (wraps) {
            BlockSection currentBlock = blocks.get(index);
            if (currentBlock.sectionId() == nextBlock.sectionId()) {
                TrackSection section = network.section(currentBlock.sectionId());
                if (section != null) {
                    remaining += section.totalLength();
                }
            }
        }
        return remaining;
    }

    private void updateStuckTracking(TrainManager trains) {
        Map<Integer, Integer> updated = new LinkedHashMap<>();
        for (Map.Entry<Integer, Integer> entry : lastEnteredBlock.entrySet()) {
            int trainId = entry.getKey();
            Train train = trains.train(trainId);
            boolean heldAndAtRest = safetyEnabled
                && nextBlockOccupiedBySomeoneElse(entry.getValue(), trainId)
                && Math.abs(train.velocity()) < AT_REST_EPSILON;
            if (heldAndAtRest) {
                Integer previous = heldAtRestTicks.get(trainId);
                updated.put(trainId, (previous == null ? 0 : previous) + 1);
            }
        }
        heldAtRestTicks.clear();
        heldAtRestTicks.putAll(updated);
    }

    /**
     * Finds every pair of trains whose full along-track extent (see {@code TrainSpec#totalLength})
     * overlaps on the same section right now, regardless of block boundaries — a physical fact,
     * not a block-signalling one. Runs unconditionally, not only when safety is disabled: with
     * safety on this should always come back empty, and if it ever does not, that is a real,
     * useful signal that the block layout or braking is under-provisioned for the speeds involved,
     * not a contradiction to be suppressed.
     *
     * <p>Also checks across a closed section's wrap seam — two trains straddling the join from the
     * end of a ring back to its start are physically close (or overlapping) despite being numerically
     * far apart in raw distance, and a check that only compared raw distances would miss exactly
     * that case.</p>
     */
    private void detectCollisions(TrainManager trains, TrackNetwork network) {
        collisions.clear();
        List<Map.Entry<Integer, Train>> byId = new ArrayList<>(trains.asMap().entrySet());
        for (int i = 0; i < byId.size(); i++) {
            for (int j = i + 1; j < byId.size(); j++) {
                Train a = byId.get(i).getValue();
                Train b = byId.get(j).getValue();
                TrackRef refA = a.reference();
                TrackRef refB = b.reference();
                if (refA.sectionId() != refB.sectionId()) {
                    continue;
                }
                double lowA = refA.distance() - a.spec().totalLength();
                double lowB = refB.distance() - b.spec().totalLength();
                double overlap = rangeOverlap(lowA, refA.distance(), lowB, refB.distance());

                TrackSection section = network.section(refA.sectionId());
                if (section != null && section.isClosed()) {
                    // Also test B shifted by one section length in either direction, catching the
                    // case where the two trains are actually adjacent across the wrap seam even
                    // though their raw distances are nowhere near each other.
                    double total = section.totalLength();
                    overlap = Math.max(overlap,
                        rangeOverlap(lowA, refA.distance(), lowB + total, refB.distance() + total));
                    overlap = Math.max(overlap,
                        rangeOverlap(lowA, refA.distance(), lowB - total, refB.distance() - total));
                }

                if (overlap > 0.0D) {
                    collisions.add(new Collision(
                        byId.get(i).getKey(), byId.get(j).getKey(), refA.sectionId(), overlap));
                }
            }
        }
    }

    private static double rangeOverlap(double lowA, double highA, double lowB, double highB) {
        return Math.min(highA, highB) - Math.max(lowA, lowB);
    }

    private int blockIndexContaining(TrackRef ref) {
        for (int i = 0; i < blocks.size(); i++) {
            if (blocks.get(i).contains(ref)) {
                return i;
            }
        }
        return -1;
    }

    private int nextIndex(int index) {
        int next = index + 1;
        if (next >= blocks.size()) {
            return closedCircuit ? 0 : -1;
        }
        return next;
    }

    private boolean nextBlockOccupiedBySomeoneElse(int index, int excludingTrainId) {
        int next = nextIndex(index);
        if (next < 0) {
            return false;
        }
        for (Map.Entry<Integer, Integer> entry : occupancy.entrySet()) {
            if (entry.getKey() != excludingTrainId && entry.getValue() == next) {
                return true;
            }
        }
        return false;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Zero unless this train's next block (see {@link #referenceBlock}) is occupied by another
     * train, in which case it is a braking acceleration that brings the train to rest just short of
     * that next block's entrance — the classic constant-deceleration stopping profile also used by
     * {@code StationPlatform}'s approach phase, clamped to never push in the train's direction of
     * travel, so a train already coasting slower than the ideal curve is left alone rather than
     * sped up to "catch" it. That clamp is also what keeps this safe to compose with a ride element:
     * see {@code BlockSignaledElementSet}, which lets this override a lift or launch only on the
     * ticks this method would actually apply a nonzero force. See the class javadoc for why the
     * target is the next block's entrance rather than the current block's own exit.</p>
     */
    @Override
    public double forTrain(int trainId, Train train) {
        if (!safetyEnabled) {
            return 0.0D;
        }
        Integer index = referenceBlock(trainId);
        if (index == null) {
            return 0.0D;
        }
        int next = nextIndex(index);
        if (next < 0 || !nextBlockOccupiedBySomeoneElse(index, trainId)) {
            return 0.0D;
        }
        Double remaining = nextEntranceRemaining.get(trainId);
        // Should always be present — updateOccupancy computes it for exactly this trainId whenever
        // referenceBlock and nextIndex are both non-null, which is exactly the condition just
        // checked above. Falling back to 0 (immediate full brake) rather than throwing keeps this
        // a safe failure if that invariant is ever broken by a future change.
        return brakeTowardBoundary(train, remaining == null ? 0.0D : remaining);
    }

    /**
     * Brakes {@code train} to a stop {@link #ENTRY_MARGIN} short of its next block's start, given
     * the wrap-aware {@code remaining} distance {@link #updateOccupancy} already resolved.
     *
     * <p>Deliberately bang-bang (full {@link #brakeDeceleration} or nothing) rather than the smooth
     * deadbeat servo {@code VelocityServo} uses elsewhere in this codebase — see
     * {@link #PLANNING_DECELERATION_FACTOR}'s javadoc for why: this needs a hard, provable "never
     * cross the line" guarantee that a proportional correction computed once per tick cannot give
     * as the curve's slope steepens toward the end.</p>
     *
     * <p>The one place full {@link #brakeDeceleration} is <em>not</em> applied is the final tick: a
     * constant acceleration is held for the whole tick (see the class javadoc's two-phase note), so
     * applying the full rate to an already-slow train could remove more speed than it has, overshooting
     * past zero into reverse within that one tick — precisely the failure mode {@code BrakeRunTest}
     * names {@code neverOvershootsIntoReverse} for the sibling element. The magnitude is capped to
     * exactly what would bring the train to rest by the end of this tick, never less than needed to
     * still be decelerating, never more than would flip its sign.</p>
     */
    private double brakeTowardBoundary(Train train, double remaining) {
        double v = train.velocity();
        double planningDeceleration = brakeDeceleration * PLANNING_DECELERATION_FACTOR;
        double direction = remaining >= 0.0D ? 1.0D : -1.0D;
        double idealSpeed = direction * Math.sqrt(2.0D * planningDeceleration * Math.abs(remaining));

        // A block brake only ever removes energy, exactly like BrakeRun and StationPlatform's
        // approach phase — never push the train faster in the direction it is already travelling,
        // and never brake once it is already at or inside the conservative envelope.
        if (v > 0.0D) {
            if (v <= idealSpeed) {
                return 0.0D;
            }
            double maxWithoutReversing = v / tickSeconds;
            return -Math.min(brakeDeceleration, maxWithoutReversing);
        }
        if (v < 0.0D) {
            if (v >= idealSpeed) {
                return 0.0D;
            }
            double maxWithoutReversing = -v / tickSeconds;
            return Math.min(brakeDeceleration, maxWithoutReversing);
        }
        return 0.0D;
    }

    /**
     * {@inheritDoc}
     *
     * <p>True for the whole time this train's next block is occupied by another train — not only
     * once it has actually stopped — mirroring {@code StationPlatform#isHolding}, which is true
     * throughout its approach phase for the identical reason: {@code Train}'s valleying check would
     * otherwise misread a train braking toward a boundary, or one that has already stopped there,
     * as stalled. See the class javadoc's deadlock section for the one place this eagerness has a
     * real cost — it also means a fully deadlocked train is never flagged {@code VALLEYED}, which
     * is exactly why {@link #isStuck} exists as a separate signal.</p>
     */
    @Override
    public boolean isHolding(int trainId, Train train) {
        if (!safetyEnabled) {
            return false;
        }
        Integer index = referenceBlock(trainId);
        return index != null && nextBlockOccupiedBySomeoneElse(index, trainId);
    }

    /** The block {@code trainId} currently occupies, or {@code null} if it is in none. */
    public BlockSection blockOf(int trainId) {
        Integer index = occupancy.get(trainId);
        return index == null ? null : blocks.get(index);
    }

    /** Index into {@link #blocks()} that {@code trainId} currently occupies, or {@code -1}. */
    public int blockIndexOf(int trainId) {
        Integer index = occupancy.get(trainId);
        return index == null ? -1 : index;
    }

    /**
     * Whether the block ahead of {@code trainId} (see {@link #referenceBlock}) is free to enter —
     * {@code true} for a train that has never entered any tracked block, since this system has no
     * opinion about it.
     */
    public boolean isNextBlockClear(int trainId) {
        Integer index = referenceBlock(trainId);
        return index == null || !nextBlockOccupiedBySomeoneElse(index, trainId);
    }

    /** Equivalent to {@code !isNextBlockClear}, phrased the other way for callers that want it. */
    public boolean mayProceed(int trainId) {
        return isNextBlockClear(trainId);
    }

    /**
     * Consecutive ticks {@code trainId} has spent held at rest by this system because the block
     * ahead of it was occupied — zero if it is not currently in that state. See {@link #isStuck}.
     */
    public int stuckTicks(int trainId) {
        Integer ticks = heldAtRestTicks.get(trainId);
        return ticks == null ? 0 : ticks;
    }

    /**
     * Whether {@code trainId} has been held at rest by this system for at least
     * {@code stuckTicksThreshold} consecutive ticks — this class's answer to the deadlock case
     * documented in the class javadoc. A deadlocked train is deliberately kept
     * {@link Train.Status#RUNNING} rather than faulted (see {@link #isHolding}), because from
     * {@code Train}'s point of view it genuinely is under active control, not stalled — but a ride
     * controller polling this method can still surface the stuck state to an operator instead of
     * it silently never resolving.
     */
    public boolean isStuck(int trainId) {
        return stuckTicks(trainId) >= stuckTicksThreshold;
    }

    /** Every currently-overlapping train pair; see {@link #detectCollisions}. Empty unless two
     *  trains' along-track extents actually overlap right now. */
    public List<Collision> collisions() {
        return Collections.unmodifiableList(collisions);
    }

    public boolean hasCollision() {
        return !collisions.isEmpty();
    }

    @Override
    public String toString() {
        return "BlockSystem{" + blocks.size() + " blocks, safety="
            + (safetyEnabled ? "on" : "off") + ", occupied=" + occupancy.size() + '}';
    }
}
