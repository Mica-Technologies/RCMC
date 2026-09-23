package com.micatechnologies.minecraft.rcmc.physics.ride;

import com.micatechnologies.minecraft.rcmc.physics.element.DispatchGate;

/**
 * An operator's control over one ride: whether it is open, how its trains are dispatched, and the
 * emergency stop.
 *
 * <p>A ride is identified by the track section its station sits on — the circuit a coaster's
 * trains run round. Pure state with no Minecraft types, like the rest of {@code physics}; the
 * operator panel, the GUI and the commands only call these methods.</p>
 *
 * <p><b>Defaults are today's behaviour.</b> A ride that has never been touched is {@link State#OPEN}
 * with {@link DispatchMode#AUTOMATIC} dispatch — exactly how every station worked before there was a
 * controller, so existing parks run unchanged.</p>
 */
public final class RideController implements DispatchGate {

    /** Whether the ride is running for guests. */
    public enum State {
        /** Trains finish their lap, return to the station and are held there. Nobody boards. */
        CLOSED,
        /** Trains run their full cycle, but nobody may board: the operator is test-running it. */
        TESTING,
        /** Normal service. */
        OPEN
    }

    /** How a train waiting in the station is sent on its way. */
    public enum DispatchMode {
        /** After the station's dwell time, by itself. */
        AUTOMATIC,
        /** Only when the operator presses DISPATCH, and not before the dwell has run. */
        MANUAL
    }

    private final int sectionId;
    private State state = State.OPEN;
    private DispatchMode dispatchMode = DispatchMode.AUTOMATIC;
    private boolean emergencyStopped;
    private boolean dispatchRequested;
    private int carsPerTrain = DEFAULT_CARS;

    /** Cars in a train the operator adds, unless they choose otherwise. The demo's five. */
    public static final int DEFAULT_CARS = 5;
    public static final int MIN_CARS = 1;
    public static final int MAX_CARS = 12;

    public RideController(int sectionId) {
        this.sectionId = sectionId;
    }

    public int sectionId() {
        return sectionId;
    }

    public State state() {
        return state;
    }

    public void setState(State state) {
        if (state == null) {
            throw new IllegalArgumentException("state is required");
        }
        this.state = state;
    }

    public DispatchMode dispatchMode() {
        return dispatchMode;
    }

    public void setDispatchMode(DispatchMode mode) {
        if (mode == null) {
            throw new IllegalArgumentException("mode is required");
        }
        this.dispatchMode = mode;
        // A request made under the other mode must not fire later by surprise.
        this.dispatchRequested = false;
    }

    /**
     * The operator pressed DISPATCH. Takes effect the next time a train's dwell has run; returns
     * false (and records nothing) if a press cannot dispatch anything right now — the ride is not in
     * manual mode, is closed, or is stopped.
     */
    public boolean requestDispatch() {
        if (dispatchMode != DispatchMode.MANUAL || state == State.CLOSED || emergencyStopped) {
            return false;
        }
        dispatchRequested = true;
        return true;
    }

    public boolean isDispatchRequested() {
        return dispatchRequested;
    }

    /** Why a ride stopped — shown to the operator, who has to know what to look for. */
    public enum StopCause {
        /** Someone pressed the button. */
        OPERATOR,
        /** Two trains on the circuit overlapped. */
        COLLISION,
        /** A train fell back down a lift and the anti-rollback dogs caught it. */
        ROLLBACK
    }

    private StopCause stopCause;

    /**
     * Stops every train on the ride where it is and holds it there, until {@link #resetEmergency}.
     * Also closes the ride, as a real e-stop does: coming back into service is a deliberate act.
     */
    public void emergencyStop() {
        emergencyStop(StopCause.OPERATOR);
    }

    /** {@link #emergencyStop()}, recording why. */
    public void emergencyStop(StopCause cause) {
        emergencyStopped = true;
        stopCause = cause == null ? StopCause.OPERATOR : cause;
        dispatchRequested = false;
        state = State.CLOSED;
    }

    /** Why the ride is stopped, or {@code null} while it is not. */
    public StopCause stopCause() {
        return emergencyStopped ? stopCause : null;
    }

    /** Clears the stop. The ride stays closed until the operator opens it again. */
    public void resetEmergency() {
        emergencyStopped = false;
        stopCause = null;
    }

    public boolean isEmergencyStopped() {
        return emergencyStopped;
    }

    /** Whether a guest may get on a train of this ride. */
    public boolean ridersMayBoard() {
        return state == State.OPEN && !emergencyStopped;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Consumes a manual DISPATCH press: one press sends one train.</p>
     */
    @Override
    public boolean mayDispatch() {
        if (state == State.CLOSED || emergencyStopped) {
            return false;
        }
        if (dispatchMode == DispatchMode.AUTOMATIC) {
            return true;
        }
        if (dispatchRequested) {
            dispatchRequested = false;
            return true;
        }
        return false;
    }

    /** Cars in each train the operator adds from now on. Trains already running keep theirs. */
    public int carsPerTrain() {
        return carsPerTrain;
    }

    public void setCarsPerTrain(int cars) {
        this.carsPerTrain = Math.max(MIN_CARS, Math.min(MAX_CARS, cars));
    }

    /**
     * How many trains a ride can run at once. With block signalling, one fewer than its blocks —
     * N trains on N blocks deadlock, each waiting for the next to move. Without any, one: nothing
     * would keep a second train off the first.
     */
    public static int maxTrains(int blockCount) {
        return blockCount >= 2 ? blockCount - 1 : 1;
    }

    @Override
    public String toString() {
        return "RideController{section " + sectionId + ", " + state + ", " + dispatchMode
            + (emergencyStopped ? ", E-STOP" : "") + '}';
    }
}
