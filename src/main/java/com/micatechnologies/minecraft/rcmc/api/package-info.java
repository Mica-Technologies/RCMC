/**
 * RCMC's published API surface.
 *
 * <p>Everything in this package and its subpackages is intended for other mods to compile
 * against — park automation, ride-status displays, cross-mod scenery, custom track styles.
 * It is shipped as a separate {@code -api} classifier jar (see {@code apiPackage} in
 * {@code buildscript.properties}) so dependents can compile against it without pulling the
 * whole mod onto their classpath.</p>
 *
 * <p><b>Stability contract.</b> Breaking changes here require a major version bump and a
 * migration note. Everything <em>outside</em> this package is internal and may change in any
 * release, however public its Java visibility looks — treat
 * {@code com.micatechnologies.minecraft.rcmc.track.*}, {@code ...physics.*} and
 * {@code ...client.*} as private regardless of modifiers.</p>
 *
 * <p>The API is intentionally empty until there is real internal structure worth exposing.
 * Publishing an interface before the implementation has settled just locks in a bad
 * abstraction. See {@code docs/AGENT-PLANS/MASTER_PLAN.md} for when each API surface is
 * scheduled to stabilise.</p>
 */
package com.micatechnologies.minecraft.rcmc.api;
