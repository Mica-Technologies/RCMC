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

    /**
     * Stops every train on the ride where it is and holds it there, until {@link #resetEmergency}.
     * Also closes the ride, as a real e-stop does: coming back into service is a deliberate act.
     */
    public void emergencyStop() {
        emergencyStopped = true;
        dispatchRequested = false;
        state = State.CLOSED;
    }

    /** Clears the stop. The ride stays closed until the operator opens it again. */
    public void resetEmergency() {
        emergencyStopped = false;
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

    @Override
    public String toString() {
        return "RideController{section " + sectionId + ", " + state + ", " + dispatchMode
            + (emergencyStopped ? ", E-STOP" : "") + '}';
    }
}
