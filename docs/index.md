# RCMC — Rails & Coasters: Minecraft

A **Minecraft 1.12.2 Forge** mod that adds real rail-guided rides: track laid out as continuous
splines instead of block-aligned rails, multi-car trains that ride it under a genuine physics
simulation, and a first-person ride experience with the camera bolted to the car.

Two ride families share one core:

<div class="grid cards" markdown>

-   :material-rollerblade: **Rollercoasters**

    ---

    Arbitrary 3D curves, authored banking, inversions, lift hills, brake runs, block sections,
    and an RCT-style excitement / intensity / nausea rating on every design.

    [:octicons-arrow-right-24: Build a coaster](guide/building-a-coaster.md)

-   :material-subway-variant: **Metro / rail transit**

    ---

    Powered trains with traction and service braking, ATO driving, stations with real dwell and
    sliding doors, platforms, signalling, catenary, signage and spoken announcements.

    [:octicons-arrow-right-24: Build a metro line](guide/building-a-metro.md)

</div>

!!! warning "Status: pre-release, no public build yet"

    RCMC is not on CurseForge or Modrinth. Both families are playable from a development
    checkout, and everything documented here is implemented and in the code — but nothing has
    been through a public test. Expect sharp edges and save-format churn.

## What makes it different from minecarts

| Aspect | Vanilla / Railcraft | RCMC |
| --- | --- | --- |
| Track shape | Block-aligned, 45° increments | Arbitrary 3D splines through placed nodes |
| Banking | None | Authored roll angle, carried along the curve by parallel transport |
| Car motion | Block-to-block hops | Continuous distance along an arc-length-parameterised curve |
| Physics | Fixed speed with a push | Energy-conserving simulation: gravity, drag, lift hills, launches, brakes |
| Riding | Sit on a cart | Camera locked to the car, roll through inversions, walk around inside a moving metro |
| Trains | One cart | Rigidly coupled multi-car trains |

## Start here

- **[Getting started](guide/getting-started.md)** — install, then get something running in under
  a minute with the two demo commands.
- **[Coasters vs metro](guide/coasters-vs-metro.md)** — what the two families share, what they
  don't, and which one you want.
- **[Commands](reference/commands.md)** — every `/rcmc` subcommand, with syntax.
- **[Items and blocks](reference/items-and-blocks.md)** — the four build tools and what each key
  does.

## The one idea behind all of it

**A train on a track has exactly one degree of freedom: distance along the track.** World
position, orientation and rider camera are all pure functions of that single number and the
track's geometry. The simulation moves that number; it does not simulate a 3D rigid body and then
constrain it back onto the rails.

That is why a car can never fall off the track through numerical error, why the energy in the
system is conserved exactly, why a whole park's physics costs less than a millisecond a tick, and
why the client can predict a train's motion perfectly instead of stuttering between position
updates. If you want the detail, it is all in the [internals](design/TRACK_GEOMETRY.md).

## Conventions used across these docs

- **Blocks are metres.** A 40-block drop is treated as a 40 m drop. Minecraft's world scale is
  roughly 1 block ≈ 1 m for the things that matter here — rider eye height, car length — and
  pretending otherwise would put a scale factor in every formula for no gain in fidelity.
- **Distance along track is `s`**, in blocks, always measured from the start of a section.
- **Spline parameter is `u`**, in `[0, 1]`, uniform in *segment index* — never in distance.
  Conflating the two is the single most common bug in this problem domain.
- Physics works in **seconds**; the game works in **ticks** (20/s). The conversion happens
  exactly once, at the tick boundary.

## Contributing to this wiki

Every page here is plain Markdown under [`docs/`](https://github.com/Mica-Technologies/RCMC/tree/main/docs)
in the mod's own repository, so a doc fix is a normal pull request. Pushing to `main` rebuilds and
republishes this site automatically. Use the :material-pencil: edit icon at the top of any page to
jump straight to it on GitHub.
