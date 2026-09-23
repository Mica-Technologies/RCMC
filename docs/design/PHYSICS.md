# Physics

## The model: one degree of freedom

A train constrained to a track has exactly **one** degree of freedom — how far along it is.
World position, orientation, and which way is down relative to the car are all pure functions of
that scalar and the track geometry.

So RCMC simulates that scalar. It does **not** simulate 3D rigid bodies and constrain them to
the rails afterwards. This is the most important decision in the physics layer, and it buys:

- **Exact energy conservation** — no constraint solver bleeding or injecting energy
- **No derailment through numerical error** — a car cannot leave the rails because "off the
  rails" is not a representable state
- **Cost** — one scalar integration per train, cheap enough for a whole park every tick

`TrainState` is therefore just `(distance, velocity)`, immutable. Immutability makes multi-substep
integration and rollback-for-client-reconciliation trivially correct.

## Forces

All accelerations are along-track, in blocks/s².

### Gravity

Only the component *along* the track does work:

```
a_gravity = -g · (forward · worldUp) = -g · sin(grade)
```

`forward` is a unit vector, so its `y` component **is** `sin(grade)` — +1 pointing straight up,
−1 straight down. No trigonometry needed.

The perpendicular component is absorbed by the rails. It never accelerates the train; it appears
to the rider only as G-force.

### Air drag — quadratic

```
a_drag = -c_air · v · |v|
```

`|v|` rather than `v²` so it opposes motion in both directions. This term dominates at the high
end of a drop and is what makes a tall first drop feel meaningfully different from a short one.
It is also what bounds a circuit's speed across repeated laps.

### Rolling resistance — linear

```
a_roll = -c_roll · v
```

Small, but it is what eventually brings a valleyed train to rest instead of leaving it
oscillating forever.

### External acceleration

Chain lifts, LSM launches, brake runs and station drive tyres are all supplied by the caller as a
signed along-track acceleration. Keeping this a **parameter** rather than baking ride elements
into the integrator is what lets one integrator serve every element type — and lets the element
implementations be tested independently.

## The integrator: semi-implicit (symplectic) Euler

```java
v' = v + a(s) · dt      // velocity first, from acceleration at the CURRENT position
s' = s + v' · dt        // position from the NEW velocity
```

That one-line difference from explicit Euler (`s' = s + v·dt`) is what makes the scheme
**symplectic**: it does not systematically pump energy into or out of the system.

It matters enormously here. A train valleyed in a dip is literally an oscillating system, and
explicit Euler gains energy every cycle — the train would slowly climb out of a valley it should
be trapped in. That reads as "the physics engine exploded" but is really just the wrong
integrator.

**Why not RK4?** More accurate per step, but not symplectic, and costs four force evaluations.
For this system, sub-stepped symplectic Euler is both cheaper *and* better-behaved over long
runs. Accuracy is bought with sub-steps (`RcmcConfig.physicsSubSteps`, default 4), which cost
only CPU — not bandwidth, not memory.

**Why sub-step at all?** One 50 ms tick is far too coarse at coaster speeds: a train doing 30
blocks/s covers 1.5 blocks per tick, enough to cut the corner on a tight helix.

## The invariant the tests assert

`PhysicsIntegrator.specificEnergy()` is not used by the simulation. It exists so the tests can
assert the thing that actually matters:

- **A frictionless track conserves energy.** Any regression in the integrator shows up here
  first and unambiguously.
- **Peak speed at the bottom of a drop matches `v = √(2gh)`.** Ties the simulation to a
  closed-form physical result rather than to its own previous output.

If `PhysicsIntegratorTest` starts failing, the integrator changed — not the test.

## Built on top of this

The model above is the core. These all exist and sit on it:

- **G-forces** — `GForces`: the centripetal term `v²·κ`, from the spline's curvature
  (`CatmullRomSpline.curvatureAt`), plus gravity, projected onto the car's banked axes to give
  vertical and lateral load; longitudinal is `dv/dt`. Because bank is already in the frame, how much
  of a turn the bank absorbed needs no special case. They drive the rider HUD and its screen
  effects, and the ride ratings.
- **Multi-car trains** — a train is rigid, so all cars share one `TrainState` and are offset by
  fixed distances along `s`. Gravity is **averaged over the whole train** (`Train`), not sampled at
  one point: this is why a long train crests a hill differently from a short one, and it is a real,
  felt part of coaster behaviour.
- **Valleying detection** — a train that runs out of speed on a grade too shallow to restart it is
  latched `VALLEYED`, a fault status shown in `/rcmc info` and on the operator panel rather than
  left as a silent hang. Taking the train under control — a metro entering service — clears it.
- **Client prediction** — the client runs the same integrator between the server's corrections,
  four a second, so a ride is smooth at coaster speeds that vanilla entity tracking cannot keep up
  with.

## Not yet implemented

- **Eased reconciliation** — a server correction is applied to the client's train as a hard set,
  not blended toward. A visible snap is more diagnostic than a smoothing that hides drift, so this
  waits until the divergence has been measured.
- **Friction and drag varying with track style** — wooden vs steel, wheel condition, weather.
  Rolling resistance and air drag are single world-wide values for now.
