# Track Geometry

How RCMC represents a piece of rollercoaster track, and why each choice was made over the
obvious alternatives.

## The problem

Vanilla rails are block-aligned and quantised to 45°. A rollercoaster is defined by exactly
the things that quantisation destroys: a smooth radius, a banked turn, a barrel roll, a
transition that eases rather than snaps. So the track cannot be "a block per position". It has
to be a **continuous curve** that happens to be *anchored* at block positions.

## Layer 1 — the curve: centripetal Catmull-Rom

`CatmullRomSpline`

A builder places **nodes**; the track passes through them. That rules out B-splines (which
approximate rather than interpolate) and makes Béziers awkward (the player would have to manage
tangent handles). Catmull-Rom interpolates its control points by construction, so "the track
goes where I put the node" is true for free.

**Why centripetal (α = 0.5), specifically.** Uniform Catmull-Rom (α = 0) forms *cusps* and
self-intersections when control points are unevenly spaced — precisely what happens when a
player puts a tight corner next to a long straight. A cusp means the tangent reverses, which in
coaster terms means a train instantaneously flips direction and the physics detonates.
Centripetal parameterization is *proven* never to cusp or self-intersect (Yuksel, Schaefer &
Keyser, "Parameterization and Applications of Catmull–Rom Curves"). Choosing α = 0.5 converts a
whole class of player-triggerable explosions into a non-issue, for the cost of a `Math.pow` per
knot at build time.

Evaluation uses the **Barry–Goldman pyramidal recurrence** rather than the familiar basis-matrix
form, because the basis matrix only exists for the uniform case; non-uniform knots require the
recurrence. Tangents are differentiated analytically from that same recurrence rather than by
finite differences, so they stay exact at segment boundaries where a finite difference would
straddle two segments.

## Layer 2 — distance: arc-length reparameterization

`ArcLengthTable`

The spline's native parameter `u` **is not distance**. Advancing `u` at a constant rate makes a
train crawl through wide curves and rocket through tight ones — physically backwards, and
instantly obvious to a rider.

So the physics works in one honest variable: `s`, distance along the track in blocks. The table
converts `s → u`:

1. Sample the curve at uniform steps in `u` (64 per segment by default).
2. Accumulate chord lengths into a monotonically increasing table.
3. Answer a query by binary search + linear interpolation between neighbours.

Chord length always *under*-estimates true arc length, with error falling off as O(1/n²). At 64
samples per segment the residual is far below anything a rider could perceive, and it manifests
as a constant scale factor rather than accumulating position drift.

Build the table once when a section is created or edited — **never per tick**. Construction is
O(samples); lookup is O(log n).

## Layer 3 — orientation: parallel transport, not Frenet

`TrackFrame`, `ParallelTransportFrames`

A car needs a full orthonormal frame, not just a position: forward (direction of travel), up
(out of the car's roof), right. The renderer orients the model from it, the rider camera derives
yaw/pitch/roll from it, and the physics projects gravity onto `forward`.

**The textbook answer is wrong here.** The Frenet–Serret frame derives "up" from the curve's
second derivative. That has two fatal properties for a coaster:

- The normal is **undefined at zero curvature** — i.e. on every straight section, which is where
  track spends most of its length.
- It **flips 180° through an inflection point**, which would snap a train upside down in the
  middle of an S-bend.

The fix is **parallel transport**: start with any valid frame, and at each subsequent sample
rotate the previous frame by the minimal rotation taking the old tangent to the new one
(Rodrigues about their cross product). That rotation is the smallest one keeping the frame
perpendicular to the curve, so the frame accumulates **no spurious twist** — straight track
produces literally zero rotation, and a helix produces exactly the roll the helix implies.

Transport is inherently sequential (frame *i* depends on frame *i−1*), so it cannot be evaluated
at an arbitrary point on demand. Sample once at build time, interpolate between samples at query
time: O(n) becomes O(1), which is what makes it usable from a per-tick loop and a per-frame
renderer.

Interpolation interpolates **only `up`** (nlerp). Position and tangent come from the curve
directly — lerping two sampled positions cuts the corner, which visibly sinks a car into the
inside of a tight turn.

### Known limitation: the closed-circuit seam

Parallel transport is **not periodic**. On a closed circuit, the frame arriving back at `s = 0`
generally differs from the starting frame by a residual roll — the curve's total torsion, a real
geometric quantity (the same effect as a Foucault pendulum's precession). Left alone it shows as
a visible seam where a car crosses the start/finish line.

The fix is to distribute the residual linearly over the circuit's length once the section is
known to be closed. That belongs in the **track-network layer**, which is the only layer that
knows a circuit closes — not in `ParallelTransportFrames`, which sees one open section.

## Layer 4 — banking

Bank is applied *on top of* the transported frame, as an explicit roll about `forward`
(`TrackFrame.withBank`, Rodrigues rotation). This is deliberate: it makes bank angle an
**authored, inspectable property of the track** rather than an emergent accident of curvature.

That matters for both design and gameplay. RCT-style track editing wants a builder to say "bank
this turn 45°", and the ride-rating system wants to compare authored bank against the bank the
curve's lateral G would *require* — the difference is exactly what makes a turn feel
uncomfortable, which is a thing the rating system should be able to punish.

## What is not here yet

- **Track network** — joining sections end-to-end, switches, junctions, the closed-circuit
  residual fix
- **Track styles** — rail gauge, tie spacing, support generation, per-style models
- **Mesh generation** — sweeping a cross-section along the frames into a renderable buffer
- **Persistence** — how a section's nodes survive a world save and a chunk unload

All are scheduled in `docs/AGENT-PLANS/MASTER_PLAN.md`.
