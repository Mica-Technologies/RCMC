# RCMC Documentation

| Document | What it covers |
| --- | --- |
| [`design/TRACK_GEOMETRY.md`](design/TRACK_GEOMETRY.md) | How track is represented: splines, arc length, frames, banking |
| [`design/PHYSICS.md`](design/PHYSICS.md) | The 1-D simulation model, forces, integrator choice, G-forces |
| [`AGENT-PLANS/`](AGENT-PLANS/) | **Gitignored.** Phased implementation plan and agent working notes |

## Conventions used across these docs

- **Blocks are metres.** A 40-block drop is treated as a 40 m drop. This is a deliberate
  simplification: Minecraft's world scale is roughly 1 block ≈ 1 m for the things that matter
  here (rider eye height, car length), and pretending otherwise would require a scale factor
  in every formula for no gain in fidelity.
- **Distance along track is `s`**, in blocks, always measured from the start of a section.
- **Spline parameter is `u`**, in `[0, 1]`, uniform in *segment index* — never in distance.
  Conflating the two is the single most common source of bugs in this codebase's problem
  domain; see `ArcLengthTable`.
- Physics works in **seconds**; the game works in **ticks** (20/s). Convert exactly once, at
  the tick boundary, via `RcmcConstants.SECONDS_PER_TICK`.
