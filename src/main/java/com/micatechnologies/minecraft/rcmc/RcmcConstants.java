package com.micatechnologies.minecraft.rcmc;

/**
 * Compile-time constants for the mod's identity.
 *
 * <p>The values come from {@code Tags}, a class generated at build time by the GTCEu
 * buildscript from {@code buildscript.properties} ({@code generateGradleTokenClass}). Do
 * not hard-code the mod id or version anywhere else — the version in particular is derived
 * from the latest git tag, so a literal would drift the moment a release is cut.</p>
 */
public final class RcmcConstants {

    /** Registry namespace and Forge mod id. Every {@code ResourceLocation} we create uses this. */
    public static final String MOD_NAMESPACE = Tags.MODID;

    /** Human-readable mod name, as shown in the Forge mod list. */
    public static final String MOD_NAME = Tags.MODNAME;

    /** Version string, derived from the latest git tag ({@code YYYY.MM.DD} for releases). */
    public static final String MOD_VERSION = Tags.VERSION;

    /**
     * Minecraft ticks per second. Physics is integrated per-tick, so every real-world unit
     * in the simulation (m/s, m/s²) is converted through this constant exactly once, at the
     * boundary between {@code rcmc.physics} and the entity update. Blocks are treated as
     * metres.
     */
    public static final double TICKS_PER_SECOND = 20.0D;

    /** Seconds of simulated time per Minecraft tick. The physics integrator's base timestep. */
    public static final double SECONDS_PER_TICK = 1.0D / TICKS_PER_SECOND;

    private RcmcConstants() {
        throw new AssertionError("No instances.");
    }
}
