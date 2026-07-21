# RCMC — Rails & Coasters: Minecraft

A Minecraft **1.12.2 Forge** mod that adds real rail-guided rides: track laid out as
continuous splines rather than block-aligned rails, multi-car trains that ride it under a
genuine physics simulation, and a first-person ride experience with the camera bolted to
the car.

Rollercoasters are the first ride family — think *RollerCoaster Tycoon*, in Minecraft, not
minecarts with a fresh texture. Powered rail transit (metro-style trains, with traction,
service braking, stations and signalling) is the second, built on the same spline track and
physics core. The scope stays deliberately focused: a few kinds of ride done properly, not
an everything-on-rails megamod.

> **Status: pre-alpha.** The repository is scaffolded and the geometry/physics core is in
> place and unit-tested. There is no placeable track in-game yet. See
> `docs/AGENT-PLANS/MASTER_PLAN.md` (local only, gitignored) for the phased roadmap.

## What makes it different from minecarts

| Aspect | Vanilla / Railcraft | RCMC |
| --- | --- | --- |
| Track shape | Block-aligned, 45° increments | Arbitrary 3D splines through placed nodes |
| Banking | None | Authored roll angle, transported along the curve |
| Car motion | Block-to-block hops | Continuous distance along an arc-length-parameterised curve |
| Physics | Fixed speed with a push | Energy-conserving simulation: gravity, drag, lift hills, launches, brakes |
| Riding | Sit on a cart | Camera locked to the car, including roll through inversions |
| Trains | One cart | Rigidly coupled multi-car trains |

## Building

Requires a **JDK 17–22** (`21` is the sweet spot — see `CLAUDE.md` for the reasoning). The mod
itself targets Java 8 via Jabel regardless of which JDK runs Gradle.

```sh
./gradlew build          # compile, run unit tests, produce the jar
./gradlew test           # unit tests only (pure JVM, no game instance needed)
./gradlew runClient      # dev client
./gradlew runServer      # dev dedicated server
```

Build system is [GregTechCEu Buildscripts](https://github.com/GregTechCEu/Buildscripts)
(a RetroFuturaGradle wrapper), matching the other Mica Technologies 1.12.2 mods.

## Architecture at a glance

```
com.micatechnologies.minecraft.rcmc
├── Rcmc, RcmcConfig, RcmcRegistry, RcmcTab, *Proxy   # Forge plumbing
├── api/          # published, stable surface for other mods (-api jar)
├── track/math/   # spline geometry — pure Java, zero Minecraft types
│   ├── Vec3, CatmullRomSpline
│   ├── ArcLengthTable            # distance <-> spline parameter
│   ├── TrackFrame                # position + forward/up/right at a point
│   └── ParallelTransportFrames   # twist-free frames along a curve
├── physics/      # 1-D along-track simulation — also pure Java
│   ├── TrainState
│   └── PhysicsIntegrator         # symplectic Euler; conserves energy
└── mixin/        # RcmcCoreMod (mixin config registrar)
```

**The load-bearing constraint:** `track.math` and `physics` contain no Minecraft types. That
keeps the two things most likely to be subtly wrong — spline evaluation and the physics
integrator — testable on a bare JVM, with assertions like "a frictionless track conserves
energy" and "peak speed matches `sqrt(2gh)`". Keep it that way; convert to Minecraft types at
the entity/render boundary.

## CI

- **Pull requests** — compile + unit tests, then a dedicated-server smoke test that boots a
  real server and asserts it reaches startup. That second job exists because client-only code
  reached from common code compiles perfectly and only fails at server boot.
- **Push to `main`** — builds and publishes a pre-release with checksums; a manual dispatch
  with `release=true` cuts a full `YYYY.MM.DD` release.
- Pre-releases older than 90 days are pruned automatically.

## License

See `LICENSE`.
