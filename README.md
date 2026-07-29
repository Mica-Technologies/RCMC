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

📖 **[Documentation & guides → mica-technologies.github.io/RCMC](https://mica-technologies.github.io/RCMC/)**

> **Status: pre-release.** Both ride families are playable from a development checkout —
> track is placeable in-game with four build tools, trains run, and you can ride them. There
> is no published build on CurseForge or Modrinth yet, nothing has been through a public
> test, and the save format is still changing. Expect sharp edges.

## What works today

**Coasters** — build track freehand node by node, or from a palette of nine prefab manoeuvres
(straight, slope, curve, helix, vertical loop, corkscrew, airtime hill). Chain lifts, brake
runs, stations with dwell and dispatch. Auto-generated supports, including on banked and
inverted track. Fixed block sections for multi-train operation, with real crashes when you
switch them off. RCT-style excitement / intensity / nausea ratings from a simulated run, plus
a separate safety verdict. Ride HUD with live G-forces, camera roll through inversions, and
G-force screen effects.

**Metro** — powered trains under an automatic driver with a traction curve, jerk-limited
service braking and a computed stopping curve. Stations with a full door cycle, physical
platforms laid at exactly car-floor height, line-map signs and arrival boards showing "N stops
away", spoken station announcements with a chime, switches and junctions, movement-authority
signalling, and overhead catenary in four track styles. You can stand up and walk around inside
a moving car.

Try either in one command: `/rcmc demo` or `/rcmc metrodemo`. See the
[getting started guide](https://mica-technologies.github.io/RCMC/guide/getting-started/).

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
├── track/        # track model: sections, network, switches, validation, storage
│   └── math/     # spline geometry — pure Java, zero Minecraft types
│       ├── Vec3, CatmullRomSpline
│       ├── ArcLengthTable            # distance <-> spline parameter
│       ├── TrackFrame                # position + forward/up/right at a point
│       └── ParallelTransportFrames   # twist-free frames along a curve
├── physics/      # 1-D along-track simulation — also pure Java
│   ├── TrainState, Train, TrainManager
│   ├── PhysicsIntegrator             # symplectic Euler; conserves energy
│   ├── element/                      # track-side: lifts, brakes, stations, drive tyres
│   ├── block/                        # coaster block sections
│   └── transit/                      # powered trains: traction, ATO driver, stops, signals
├── builder/      # build sessions for the four tools — pure Java, unit-testable
├── item/, block/, entity/, command/  # the in-world surface
├── client/       # rendering, HUD, camera, previews — client-only, reached via the proxy
├── rating/       # excitement / intensity / nausea
└── mixin/        # RcmcCoreMod (mixin config registrar)
```

**The load-bearing constraint:** `track.math`, `physics` and `builder` contain no Minecraft
types. That keeps the things most likely to be subtly wrong — spline evaluation, the physics
integrator, the ATO control law — testable on a bare JVM, with assertions like "a frictionless
track conserves energy" and "peak speed matches `sqrt(2gh)`". Keep it that way; convert to
Minecraft types at the entity/render boundary.

The second constraint: **common code never reaches client-only classes.** A stray
`net.minecraft.client` import in common code compiles perfectly and only fails when a dedicated
server boots — which is exactly what the CI server smoke test exists to catch.

## Documentation

| | |
| --- | --- |
| [**Wiki**](https://mica-technologies.github.io/RCMC/) | Guides, command reference, items and blocks, configuration |
| [`docs/design/TRACK_GEOMETRY.md`](docs/design/TRACK_GEOMETRY.md) | Splines, arc length, frames, banking |
| [`docs/design/PHYSICS.md`](docs/design/PHYSICS.md) | The 1-D model, forces, integrator choice, G-forces |
| [`docs/design/TRANSIT.md`](docs/design/TRANSIT.md) | Powered physics, station cycle, routing, signalling |
| [`CLAUDE.md`](CLAUDE.md) | Repository conventions and the rules that are load-bearing |

Docs live in [`docs/`](docs/) as plain Markdown and are published to GitHub Pages
automatically on every push to `main`.

## CI

- **Pull requests** — compile + unit tests, then a dedicated-server smoke test that boots a
  real server and asserts it reaches startup. That second job exists because client-only code
  reached from common code compiles perfectly and only fails at server boot.
- **Push to `main`** — builds and publishes a pre-release with checksums; a manual dispatch
  with `release=true` cuts a full `YYYY.MM.DD` release. Documentation changes rebuild and
  republish the wiki.
- Pre-releases older than 90 days are pruned automatically.

## Asset policy

RCMC recreates everything from scratch. Reference material — real-world stock, third-party
models, recordings — is used to learn proportions, scale and colour schemes only. No asset from
any third-party source is copied, ported, extracted or redistributed here. Every sound in the
mod is synthesised by a checked-in generator script so its provenance stays inspectable.

## License

See `LICENSE`.
