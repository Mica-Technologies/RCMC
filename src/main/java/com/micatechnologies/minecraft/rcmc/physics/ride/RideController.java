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
    private String name = "";
    /** Ticks the station's air gates still hold dispatch for — see {@link #holdForGates}. Not saved:
     *  the gates renew it every tick they are open. */
    private int gateHold;
    /** Ticks of the loading period a newly opened ride still gives the train in its station. */
    private int reopenLoading;

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
        if (state == State.OPEN && this.state != State.OPEN) {
            // A train held in the station while the ride was closed has served its dwell long ago,
            // and left the instant the ride opened — nobody had a chance to get on. Opening starts
            // a loading period instead: see loadingRemaining().
            reopenLoading = REOPEN_LOADING_TICKS;
        }
        this.state = state;
    }

    /** Ticks a newly opened ride holds its train for boarding: a station's usual dwell. */
    public static final int REOPEN_LOADING_TICKS = 60;

    /** Ticks of the loading period after opening still to run — see {@link #setState}. */
    public int loadingRemaining() {
        return reopenLoading;
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

    /** What the ride's transfer table has been asked to do. */
    public enum TransferRequest {
        NONE,
        /** Stop the next train on the table and slide it to storage. */
        STORE,
        /** Slide the stored train back onto the table as soon as it is clear. */
        RETRIEVE
    }

    private TransferRequest transferRequest = TransferRequest.NONE;

    /**
     * The id of the train type new trains on this ride are built from — see {@code TrainTypes}.
     * Kept as the id, not the type, so a type a server later changes in its files is picked up, and
     * one it removes falls back to the default rather than failing to load.
     */
    private String carType = com.micatechnologies.minecraft.rcmc.physics.TrainTypes.DEFAULT_COASTER;

    public String carType() {
        return carType;
    }

    public void setCarType(String id) {
        this.carType = id == null || id.isEmpty()
            ? com.micatechnologies.minecraft.rcmc.physics.TrainTypes.DEFAULT_COASTER : id;
    }

    public TransferRequest transferRequest() {
        return transferRequest;
    }

    public void setTransferRequest(TransferRequest request) {
        this.transferRequest = request == null ? TransferRequest.NONE : request;
    }

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
        // Checked before a manual press is consumed: pressing DISPATCH closes the gates, and the
        // press still counts once they are shut.
        if (gateHold > 0 || reopenLoading > 0) {
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

    /**
     * The station's air gates are open, or have only just shut: no train leaves for {@code ticks}
     * more ticks. The gates call this every tick they are open, so dispatch waits for them to close
     * and then for them to have been shut a moment — a gate still swinging is not a closed gate.
     */
    public void holdForGates(int ticks) {
        gateHold = Math.max(gateHold, ticks);
    }

    /** One tick of the gates' hold running out. */
    public void tickGates() {
        if (gateHold > 0) {
            gateHold--;
        }
        if (reopenLoading > 0) {
            reopenLoading--;
        }
    }

    public boolean isHeldForGates() {
        return gateHold > 0;
    }

    /** What guests call the ride; empty until someone names it. */
    public String name() {
        return name;
    }

    public void setName(String name) {
        this.name = name == null ? "" : name.trim();
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
