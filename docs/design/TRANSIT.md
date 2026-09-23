# Transit Systems

How the metro family works internally — the powered physics, the station cycle, routing, and
signalling — and why it is built as a sibling of the coaster elements rather than a generalisation
of them.

The shared foundations are covered in [Track Geometry](TRACK_GEOMETRY.md) and
[Physics](PHYSICS.md); this document only describes what transit adds.

## The premise

A metro train has **exactly one degree of freedom**, the same as a coaster train: distance along
the track. Traction and braking are additional force terms fed into the same symplectic
integrator. The integrator itself is unchanged — the energy-conservation test still holds with the
new force terms zeroed.

Everything below follows from that. A powered train is the same scalar with a different force on
it, which is why the metro family cost a fraction of what a second vehicle system would have.

## Layer map

```
physics/transit/          Pure Java. Zero Minecraft types, like the rest of physics/.
  TractionProfile         Tractive effort curve
  JerkLimiter             Comfort bound on d(command)/dt
  TrainDriver             The ATO control law
  TransitStopController   The station service cycle
  TransitStation          A named place: one or more platforms
  TransitPlatform         One track's berth: stop point, door side, label
  TransitLine             An ordered list of stations: loop, shuttle or turnback
  LineService             Per-train route following
  LineSignals             Movement authority
  ArrivalEstimator        "N stops away" and minutes, by replaying the service pattern
  LineTimings             Stop-to-stop times, learned from the trains running the line
  TransitSignText         Every piece of signage and announcement phrasing
  ServiceSnapshot         The small per-service state that gets synced
  TransitSystem           Per-world registry + the composed tick

track/TrackWalk           Read-only "how far to there" walker over the track graph
track/storage/TransitCodec  Save and wire format (they are the same format, on purpose)
sound/TransitSounds       Observes controller phases from OUTSIDE and plays audio
```

`physics/transit` is **pure Java with no Minecraft types**, exactly like `physics` and
`track.math`. That is what lets the control law, the stop cycle and the routing be unit-tested on a
bare JVM in seconds — which matters more here than anywhere else in the mod, because the failure
modes are timing bugs that a play-test surfaces as "the train overshot, once".

## Force model: why per-train, not track-side

A coaster's lift hill, brake run and drive tyres are **spans of track**. A train passing over them
has a force applied to it. That is right, because the hardware is genuinely bolted to the ground.

A metro **carries its motors with it**. So `physics/transit` is a per-*train* controller feeding
`TrainManager.ExternalAcceleration`, not a `RideElement` span. The two families never shared a base
class to begin with.

A unification was hoped for — one general traction/brake element serving both — and deliberately
not pursued. What is duplicated instead, on purpose:

- the **deadbeat control law**, and
- the **constant-deceleration stopping curve**.

`VelocityServo` is package-private to `physics.element`, and the transit driver's clamps are
**asymmetric** (traction curve up, service brake down) where a coaster brake may only ever remove
energy. Collapsing the two would couple families with genuinely different physics for the sake of
about thirty lines. Leave it unless a third family wants the same law.

## `TractionProfile`

The tractive effort curve, per unit mass like everything else in `physics`:

1. **Constant force** up to base speed (`ratedPower / maxAcceleration`)
2. **Constant power** (`P/v`) beyond it
3. **Cutout** at maximum service speed

This is the real shape of an electric traction motor's envelope, and it is why a metro accelerates
hard from rest and tails off toward line speed rather than accelerating linearly to it.

**Deliberately dropped:** the Davis equation's constant term. `PhysicsIntegrator`'s existing linear
and quadratic drag *are* Davis' B and C terms; the constant A term would need a new force term in
the integrator for an effect no player would ever see.

## `TrainDriver` — the ATO control law

Target speed is `min(line speed, sqrt(2·a·remaining))` — the braking curve to whatever the current
limit is — closed with a deadbeat law, clamped asymmetrically, then jerk-limited.

The jerk limiter is what separates a metro stop from a coaster brake run. A coaster brake may slam;
a metro carrying standing passengers may not.

Two behaviours in here were found by tests rather than reasoned out in advance, and both have a
constant that exists only because of a bug:

- **Creep-speed cap.** Catching the stopping curve *from below* — chasing it at full power while a
  jerk-delayed brake swings in — produced metres of overshoot.
- **Rollback protection.** On a gradient start, the jerk-limited motor ramp briefly loses to
  gravity. Wrong-way motion at near-rest bypasses the jerk limit, like a holding brake releasing
  into built tractive effort.

`BRAKE_PLANNING_FACTOR` and `CATCH_UP_FRACTION` are both 0.7. Both exist because the line-service
tests found bugs without them. Do not tidy them away.

## `TransitStopController` — the service cycle

```
APPROACHING → DOORS_OPENING → BOARDING → DOORS_CLOSING → APPROACHING
```

All timing is in tick counts, and the **caller supplies the remaining distance** — the controller
does not know the route, because owning the route is `LineService`'s job.

Berthing **tolerates position error but never speed**: a train stopped short of its mark creeps in
under power rather than being declared arrived. `isHolding(velocity)` carries dwell and creep intent
out to `Train.setHeld`, so an ATO-controlled train sitting still is never misread as *valleyed*.
`HOLDING_SPEED` exists because that threshold raced `Train`'s stall latch at terminus berths in a
live session — a bug found in play and then reproduced in
`TransitServiceWorldFlowTest`.

`doorFraction()` exposes the door cycle as 0→1 so the client can interpolate the leaves against the
real timings rather than easing a boolean and inventing its own ramps.

## Routing: `TrackWalk` and `LineService`

`TrackWalk` is a **read-only** walker: "how far is it from here to there", honouring joins, switch
selection, axis flips and closed-circuit wrap. Read-only matters — routing must never mutate the
network it is measuring.

`TransitLine` stores its **stations, not its route**. Routes are discovered by walking, never
stored, so a line does not go stale when the track under it is rebuilt.

`LineService` follows that route per train: terminus turnback, facing resync from the sign of the
velocity, and an overshoot guard — a stop point slid past reads as *negative remaining* via a short
behind-probe, never as a lost station.

### Stations and platforms

A `TransitStation` is a named **place**, not a point. It holds one or more `TransitPlatform`s, one
per track through it: each a stop point, a door side and a label for signage. An ordinary stop has
one; an island platform has two, because it is one place with a running line down each side. The
door side lives on the platform rather than the station, since the two tracks look out at the same
decking from opposite hands.

A line lists stations, and the berth is chosen as the train runs: `TransitStation.platformFor` picks
the platform nearest ahead along the track the train is actually on. `stopPoint()` and `doorSide()`
on the station remain as shorthand for its first platform, for the callers that only care about
that.

### Two kinds of terminus

An out-and-back line's service pattern is the same either way — stations in order, then back in
reverse — but *how it turns round* is not, and `TransitLine.turnsBackOnLoop` is the switch.

A **stub terminus** is a dead end. The train stops, changes ends, and leaves the way it came, on the
same track and the same platform. Both the service direction and the train's physical facing flip.

A **turnback loop** is what most real metros have. Inbound and outbound are separate tracks joined by
a turning loop at each terminus, so the train never reverses — it keeps driving forward, round the
loop, and comes back on the other track. Only the service direction flips; the wheels never do.

That distinction is load-bearing rather than cosmetic, because it is what makes a **two-platform
station** mean anything. On a turnback line the berth a train reaches at each station is the one on
the track it happens to be on, which differs by direction — so `TransitStation.platformFor` has a
real choice to make, the doors open on the right side for each, and inbound and outbound run at the
same time instead of taking turns down one rail. Model the same circuit as a *loop* line instead and
each place needs naming twice, because a loop train passes every second platform without stopping.

## `LineSignals` — movement authority

ATO/ATP in shape rather than fixed-block-permission in shape. The signal system answers one
question: **how far may this train run?** — the distance to the nearest boundary of any block
occupied by another train, walked in the train's own facing, which makes it direction-free and
therefore safe on bidirectional single track.

That distance is fed to the stop controller as a **second limit beside the station**, so one
braking law serves both cases. A train held at red is simply a train whose stopping distance is
zero, with its doors shut; it proceeds when the authority extends.

Live occupancy is **not persisted** — it is recomputed from scratch every tick, so there is nothing
there worth saving. What *is* persisted is each line's blocks, margin and horizon.

!!! warning "A cautionary tale, kept deliberately"

    `LineSignals` shipped complete, well-tested and **entirely unreachable**: `setSignals` had no
    callers in `src/main`, so `signalsByLine` was always empty and the occupancy loop was a no-op.
    The tests all passed. Nothing in the game could reach it.

    This is the project's recurring failure mode, and the rule that catches it is: **after merging
    anything, grep its public entry points from outside their own package. No hits means it is not
    wired, however green the tests are.**

## Persistence

Stations, lines and signalling are authored content and save with the track (`TransitCodec`).
A coaster's `/rcmc block` sections, the other kind of signalling, save the same way in `BlockCodec`,
and both are part of the undo history.
**Trains and their services save alongside them** (`TrainCodec`), so a metro line is still running
when the world comes back.

What a service actually stores is small on purpose: **the line and the cruise speed, nothing else**.
Which stop is next, which way the train faces, where it is in the door cycle — all of it is
re-derived on load by `enterService`, which walks the track from wherever the train stands. That is
not a shortcut around serialising the controller's timers; it is more correct than doing so. The
track can be edited while the world is closed, and a saved *"next stop is index 4, facing +1"* could
come back pointing at a station that has since moved, been renamed, or been deleted. A re-derived
one cannot go stale.

Two things are deliberately absent from the save:

- **Fault status and the held flag.** Both are functions of the train's position, velocity and the
  track under it — all of which *are* saved — so they re-latch on the first tick. Persisting them
  would need a setter on `Train` that exists only for the codec, and would let a stale fault outlive
  the condition that caused it.
- **Car entities.** Cars are not written to chunk NBT at all. The train in the save decides how many
  exist and where they are, and `TrainEntities` creates exactly those. Saving them too would be a
  second source of truth that disagrees with the first whenever a chunk's loaded state at save time
  differs from its state at load time — which, for a coaster spanning hundreds of blocks, is most of
  the time.

### Cars are recreated, never restored

`TrainEntities.spawnMissingCars` runs **once a second, forever**, not once at load. Two independent
reasons, and the first one shipped as a bug:

1. **`World.spawnEntity` silently returns `false` if the target chunk is not loaded**
   (`World:1303`). On the first tick after a world loads, the chunks around a joining player have
   not arrived yet — so a single attempt at load time spawns nothing, reports no error, and leaves
   a world looking as though train persistence did not work. It did: the trains were restored and
   the services were running, invisibly.
2. Cars are not saved with their chunks, so an unloading chunk destroys them. Recreating them is
   the only way they come back.

The check is deliberately proportional to the **number of trains**, not to the number of entities in
the world. A train whose lead car is in an unloaded chunk costs one `isBlockLoaded` call and stops
there — no entity scan, no spawn attempt, because no car could exist there anyway. A loaded train
costs one chunk-local AABB query. Scanning `loadedEntityList` would have made a quiet background
check scale with a busy server's entity count.

### Pausing

A world tick with **nobody connected to the server** does nothing at all — trains freeze in place,
holding position, velocity and dwell timers, and resume untouched when someone joins. Server-wide
rather than per dimension, so a player in the Nether does not stop the overworld's lines. A
non-overworld dimension that Forge has unloaded for being empty gets no world tick at all, so its
lines hold until someone enters it.

## Sync: one format, two uses

- **`PacketTransitSync`** — the authored stations and lines. Full-send on join and on every command
  edit, **reusing `TransitCodec`** so the wire format and the save format cannot drift apart.
- **`PacketServiceSync`** — one small `ServiceSnapshot` per running service (line, direction, next
  stop, at-platform, door fraction, train id), on the train-correction cadence.

The snapshot carries a **train id** because a platform board asks "which line is coming" while a
car must find *its own* service among several running on the same line.

`ArrivalEstimator` gives **stops away**, not minutes — exact and deterministic, replaying the
service pattern including loop wrap and shuttle terminus bounce. A minutes estimate would need a
wall clock and a speed guess; it can layer on later as pure presentation, which means client config
and never synced.

## Sound: observation, not instrumentation

`TransitStopController` is pure Java and must stay that way, so it cannot play a sound — and it is
not given a listener interface just to route one out. Instead `sound/TransitSounds` **observes**
controller phases from outside and plays audio on transitions.

Announcements are scheduled through a small per-tick timer queue: play the chime now, queue the
words to follow once the ding has rung out, so a listener hears "ding … announcement" rather than
both at once.

### Spoken announcements

Station names are spoken as **text**, not played from baked clips — which is the whole reason
arbitrary player-chosen station names work. RCMC has no synthesiser of its own: it detects CSM at
runtime and calls its MaryTTS through reflection, falling back to an on-screen subtitle when CSM is
absent.

Two things about that engine are worth knowing, because both present as "it just uses the narrator":

- **It loads asynchronously, and takes seconds.** `CsmTts.say` starts the engine on its first call
  and speaks *that* line through the game narrator. Announcements were the only caller, so every one
  of them arrived before the engine was ready and MaryTTS was never heard. `TtsBridge.warmUp()`
  starts it when a client learns the world has a transit line — on join, long before any train
  reaches a platform.
- **It needs a CSM build from 2026-07-29 or later.** Before that, CSM shaded commons-lang under its
  own package name, where FML's `org.apache.commons.` classloader exclusion made it permanently
  unreachable — so MaryTTS failed to initialise with `NoClassDefFoundError` and fell back to the
  narrator every time. Fixed on CSM's side by relocating the shaded copy.

All sounds are **synthesised from scratch** by `tools/audio/synth_metro_sounds.py`, checked in
beside the audio so the provenance of every file stays inspectable. Reference recordings were
*measured* — fundamental frequencies, harmonic content, attack and decay envelopes — and
reproduced with generated waveforms. **Do not replace these with sampled audio.**

## Side discipline

The rule that shapes the package split: `TrackStyleIds` (common — the names, validated by the
command) is separate from `TrackStyles` (client — what they look like). No `net.minecraft.client`
import exists anywhere in common transit code, and the CI dedicated-server smoke test is there to
keep it that way.

## Known limits

| Limit | Why |
| --- | --- |
| A resumed service restarts its stop cycle | `TrainCodec` saves a service's line and cruise speed, not its controller's phase and timers. A train saved berthed with its doors open reloads berthed and opens them again. Re-deriving from where the train stands cannot go stale the way a saved "next stop is index 4" can, if the track changed while the world was closed |
| Signal boundaries are equal divisions | Placing individual boundaries is tool work that has not been done; the command divides evenly as a starting point |
| Remote players' in-car walking is not synced | Offsets are computed on each side from local input, so a remote player renders where they boarded |
| No transit-specific build validation | Gentler curve radii, level platforms and station gradient limits are not enforced |
| `TransitLine` has no dedicated tests | Covered only incidentally by the service-flow tests |
