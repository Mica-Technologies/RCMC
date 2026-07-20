# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Purpose

RCMC ("Rollercoaster Minecraft") is a **Minecraft 1.12.2 Forge mod** (mod id: `rcmc`) that adds
spline-based rollercoaster track and physics-simulated, rideable coaster trains. The goal is
RollerCoaster Tycoon-grade rides — arbitrary 3D curves, banking, inversions, lift hills,
launches, brake runs, block sections — not retextured minecarts.

Build system is GregTechCEu Buildscripts (a RetroFuturaGradle wrapper), the same as the
sibling mods `minecraft-city-super-mod` (CSM) and `uia-server-utility-mod` (SUM).

## Build Commands

Set `JAVA_HOME` to a **JDK 17–22** install before each `./gradlew` invocation. **21 is the
recommended sweet spot** (and what CI uses): RetroFuturaGradle wants the Gradle process on
Java 21+ (older is deprecated and slated for removal), and the pinned Gradle 8.9 officially
supports running only on Java ≤ 22. Newer JDKs (23–26) still compile the mod correctly via
Jabel but run Gradle past its supported ceiling. Either way the compiler and mod code target
**Java 8** — only the JVM that runs Gradle changes.

IntelliJ manages these JDKs (point `JAVA_HOME` at your install; exact patch version varies):
- Windows: `C:/Users/<username>/.jdks/azul-21.x`
- macOS:   `/Users/<username>/Library/Java/JavaVirtualMachines/azul-21.x/Contents/Home`

```bash
JAVA_HOME="..." ./gradlew build      # compile + unit tests + jar
JAVA_HOME="..." ./gradlew test       # unit tests only — pure JVM, seconds not minutes
JAVA_HOME="..." ./gradlew runClient  # dev client
JAVA_HOME="..." ./gradlew runServer  # dev dedicated server
JAVA_HOME="..." ./gradlew clean

# Apple Silicon: runClient17 launches but its window is broken on macOS.
# Use the Rosetta path instead (see addon.gradle for the setup it needs):
JAVA_HOME="..." ./gradlew runClient -Prosetta
```

Heap is `-Xmx3G` in `gradle.properties` for decompilation.

## Architecture

### The central design decision

**A train on a track has exactly one degree of freedom: distance along the track.** Everything
else — world position, orientation, rider camera — is a pure function of that scalar and the
track geometry. The physics simulates that one variable; it does not simulate 3D rigid bodies
and constrain them afterwards.

This is why the code is shaped the way it is, and it buys three things: the simulation exactly
conserves energy, a car cannot leave the rails through numerical error, and it is cheap enough
to run for every train in a park every tick.

### Layers

```
track/math/     Geometry. Pure Java, ZERO Minecraft types.
  Vec3                     immutable double 3-vector
  CatmullRomSpline         centripetal (alpha=0.5) Catmull-Rom through control points
  ArcLengthTable           distance <-> spline parameter, by sampling + binary search
  TrackFrame               position + orthonormal forward/up/right, with .withBank()
  ParallelTransportFrames  twist-free frames sampled along a section

physics/        Simulation. Also pure Java, zero Minecraft types.
  TrainState               immutable (distance, velocity)
  PhysicsIntegrator        semi-implicit (symplectic) Euler

api/            Published surface for other mods; ships as a separate -api jar.
mixin/          RcmcCoreMod — mixin config registrar (see below).
Rcmc*.java      Forge plumbing: @Mod class, config, registry, creative tab, proxies.
```

### Rules that are load-bearing — do not break these

1. **`track.math` and `physics` must never import a Minecraft type.** That is what makes the
   two most error-prone parts of the mod unit-testable on a bare JVM (`./gradlew test` runs in
   seconds with no game instance). Convert at the entity/render boundary, not before. If you
   need `Vec3d`, convert; do not reach for it inside these packages.

2. **Common code must never reach client-only classes.** Renderers, camera control and the
   ride HUD are reached only through `RcmcClientProxy`. A stray `net.minecraft.client` import
   in common code compiles perfectly and only fails when a dedicated server boots — which is
   precisely what the CI server smoke test exists to catch. SUM shipped three such bugs at
   once and took a server down.

3. **Spline parameter `u` is not distance.** Never advance `u` linearly and call it speed.
   Always go through `ArcLengthTable`. Getting this wrong makes trains crawl through wide
   curves and rocket through tight ones.

4. **Use parallel transport, not Frenet frames, for track orientation.** The Frenet normal is
   undefined on straight track (zero curvature) and flips 180° through inflection points,
   which would snap a train upside down mid-transition. `ParallelTransportFrames` explains
   this at length; read it before touching orientation.

5. **The integrator is symplectic on purpose.** Semi-implicit Euler updates velocity first,
   then position from the *new* velocity. Explicit Euler pumps energy into oscillating systems
   — a train valleyed in a dip would slowly climb out of it. `PhysicsIntegratorTest` asserts
   energy conservation; if that test starts failing, the integrator changed, not the test.

### Mixins

`usesMixins = true`. `RcmcCoreMod` is a plain mixin-config registrar — it exists because
MixinBooter only auto-discovers configs from a jar's `MANIFEST.MF#MixinConfigs`, which doesn't
exist in a dev launch running from `build/classes`. It self-registers **only** in dev
(registering in a production jar crashes during coremod discovery, before
`MixinBootstrap.init()` has run).

`generateMixinConfig = false` — `src/main/resources/mixins.rcmc.json` is hand-maintained so
client-only mixins stay in the `client` array. The auto-generated config lists everything under
`mixins`, which loads client mixins on a dedicated server and crashes it.

`addon.gradle` has a build-time **manifest guard** on `reobfJar` that fails the build if the
published jar declares a `TweakClass` (any TweakClass silently removes a jar from FML 1.12.2
mod discovery) or is missing `FMLCorePlugin` / `MixinConfigs`. Don't disable it; it encodes a
real production incident from the sibling mod.

### Config

`RcmcConfig` reads Forge `Configuration` into static fields at load time — never query
`Configuration` per-tick. Values under `physics` change simulation *results*, so the server's
copy is authoritative and must be synced to clients; values under `client` are presentation
only and are never synced.

## Conventions

- Package root: `com.micatechnologies.minecraft.rcmc`
- Feature code lives in its own subpackage; event handlers register on `MinecraftForge.EVENT_BUS`
  in `Rcmc.preInit()`
- Registry names use the `rcmc:` prefix
- Never hard-code the mod id/name/version — use `RcmcConstants`, which reads the build-generated
  `Tags` class. The version is derived from the latest git tag (`YYYY.MM.DD` for releases).
- Blocks are metres; time is seconds inside `physics`, converted at the tick boundary via
  `RcmcConstants.SECONDS_PER_TICK`

## Planning docs

`docs/AGENT-PLANS/` is **gitignored** — it holds the phased implementation plan and agent
working notes. `docs/AGENT-PLANS/MASTER_PLAN.md` is the 0→100 roadmap; read it before starting
substantial work, and update the phase checkboxes as things land.

## CI

- `test-mod-build-pr.yml` — compile + unit tests, then a **dedicated-server smoke test** that
  boots a real server and greps for `Done (`. The second job is the one that catches side
  violations.
- `build-mod-release-pre-release-main.yml` — on push to `main`, tags and publishes a
  pre-release with checksums; `workflow_dispatch` with `release=true` cuts a full release.
- `cleanup-mod-pre-releases.yml` — prunes pre-releases older than 90 days.
