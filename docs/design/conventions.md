# Conventions

How the internals pages, and the code, talk about track and time.

## The one idea behind all of it

**A train on a track has exactly one degree of freedom: distance along the track.** World position,
orientation and rider camera are all pure functions of that single number and the track's geometry.
The simulation moves that number; it does not simulate a 3D rigid body and then constrain it back onto
the rails.

That is why a car can never fall off the track through numerical error, why the energy in the system is
conserved exactly, why a whole park's physics costs less than a millisecond a tick, and why a client can
predict a train's motion instead of stuttering between position updates.

## Units and symbols

- **Blocks are metres.** A 40-block drop is treated as a 40 m drop. Minecraft's world scale is roughly
  1 block ≈ 1 m for the things that matter here, such as rider eye height and car length, and pretending
  otherwise would put a scale factor in every formula for no gain in fidelity.
- **Distance along track is `s`**, in blocks, always measured from the start of a section.
- **The spline parameter is `u`**, in `[0, 1]`, uniform in *segment index*, never in distance.
  Conflating the two is the single most common bug in this problem domain.
- Physics works in **seconds**; the game works in **ticks** (20 a second). The conversion happens exactly
  once, at the tick boundary.
- A **grade** is rise over run: 0.05 is a 5% grade. **Curvature** is in blocks⁻¹; its reciprocal is the
  curve's radius.

## Where to read next

- [Track geometry](TRACK_GEOMETRY.md): the curve, arc length, orientation and banking.
- [Physics](PHYSICS.md): forces, the integrator, and what is built on it.
- [Transit systems](TRANSIT.md): the metro driver, services, signalling and persistence.
- [Building from source](building-from-source.md): compiling and running the mod.
