package com.micatechnologies.minecraft.rcmc.physics.element;

/**
 * Asked by a {@link StationPlatform} once a train's dwell has run: may it go now?
 *
 * <p>The platform decides <em>when a train could go</em> — braked in, held, dwell served. Whether it
 * <em>should</em> is the operator's call: a closed ride keeps its trains, a manual one waits for the
 * DISPATCH button. That decision lives with the ride controller; this is the one question the
 * platform needs answered, so the element package does not have to know controllers exist.</p>
 */
public interface DispatchGate {

    /** Always yes: dispatch after the dwell, as every station did before there were controllers. */
    DispatchGate ALWAYS = () -> true;

    /**
     * Whether the train that has finished its dwell may be dispatched now. Called once per tick
     * while a train waits, and may consume a one-shot request (a manual DISPATCH press).
     */
    boolean mayDispatch();
}
