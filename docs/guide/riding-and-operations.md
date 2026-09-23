# Riding and operations

## Boarding

**A coaster** is boarded by right-clicking a car. You take a seat; the ride does the rest.

**A metro** is boarded by *walking in*. While a train is berthed with its doors open, the car stops
being a solid box and becomes just a floor, and anyone standing inside is seated automatically. So
you walk through the doorway off a level platform, exactly as you would in life.

That is safe because doors only open when a train is berthed and stopped — no moving car is ever
non-solid. A short grace period after dismounting stops the auto-seat trapping you aboard.

**Only the platform side opens.** Which side that is comes from where the platform was built, and
the train announces it as it runs in — *"Entering Harbor. The doors will open on the left."* — with
enough warning to cross the car first.

**And you get off the same way: walk out through an open door.** No sneak, no dismount key — step
through the doorway and you are on the platform, placed beside the door you used. The doorways are
real openings, so this only works where there is one: walking into the wall between them stops you,
as it should.

Boarding a train **in service** is gated on its doors being open. If it refuses, it says so: a
closed door and a broken feature look identical otherwise.

## The camera

The rider camera is locked to the car — yaw, pitch **and roll**. Roll is what makes a banked turn
read as a banked turn and an inversion read as an inversion, and it is the one thing a vanilla
minecart fundamentally cannot do, since 1.12.2 entities have only yaw and pitch.

You keep limited free-look. If you would rather the world stayed upright, camera roll is a config
toggle (`client.enableCameraRoll`).

## Standing and walking in a metro

You can stand up and walk around inside a moving metro car.

The design underneath is worth knowing, because it explains the limits: **standing is implemented
as riding**. Minecraft has no moving reference frames, so a player merely *standing* in a car at 15
blocks/s would have to be teleported by the car's delta every tick — fighting client prediction and
the server's own movement checks the whole way. As a passenger, vanilla already moves you with the
vehicle perfectly, so the only remaining question is *where in the car* you are. The hard problem is
sidestepped rather than solved.

Consequences:

- You board where you walked in, not in an assigned seat.
- The walls and seat fronts are clamps on your position, not collision — vanilla collision never
  runs for you while aboard.
- **You cannot step off a moving train.** Sneaking mid-run would have vanilla shove you out through
  a solid car at line speed, so a dismount with the doors shut and the train moving simply re-boards
  you. With the doors open you leave by walking through one.
- The walls are the clamps on your position rather than collision, which is exactly why walking out
  works: at a doorway, with the doors open, the clamp is simply not there.
- In multiplayer, other players appear standing where they boarded. Their own client is right;
  per-passenger offsets are not synced yet.

## The ride HUD

Client-side, on while riding, toggleable with `client.enableRideHud`:

- Current **speed**
- **G-forces** in all three axes — vertical, lateral, longitudinal
- **Height** and **ride time**

Vertical G is the one to watch: negative vertical G is **airtime**, the single most sought-after
sensation in coaster design, and sustained high positive G is what makes a ride intense rather than
exciting.

### G-force screen effects

Tied to what the ride is actually doing to you, and all configurable or disableable:

| Effect | Trigger | Config |
| --- | --- | --- |
| **Greyout** — tunnel vision | Above ~4.5 vertical G | `client.grayOutThresholdG`, `grayOutRangeG` |
| **Redout** | Below −2.0 G, sustained negative | `client.redOutThresholdG`, `redOutRangeG` |
| **FOV kick** | Longitudinal G on launches and brakes | `client.enableGForceFovKick`, `fovKickDegreesPerG`, `fovKickMaxDegrees` |

Values are smoothed over a configurable window (`client.gForceSmoothingSeconds`) so a single
frame's spike does not flash the screen.

## Operating a ride

Place a **Ride Operator Panel** (`rcmc:operator_panel`) at a coaster's station and right-click it.
It links to the nearest coaster station within 24 blocks, and its controls face you.

| Control | What it does |
| --- | --- |
| Open / Test / Close | **Open** is normal service. **Test** runs the trains but nobody may board. **Close** lets every train finish its lap and holds it in the station |
| EMERGENCY STOP | Brakes every train on the ride to a stop wherever it is, including on the lift, and holds it there. It also closes the ride. Press **Reset emergency stop**, then **Open**, to run again. A stop the ride made itself says why in the badge: **E-STOP: COLLISION** or **E-STOP: ROLLBACK** |
| Auto / Manual | **Auto** dispatches a train by itself once its dwell has run. **Manual** holds it until you press **DISPATCH**; one press sends one train |
| Hardware | Station dwell and pass-throughs, lift speed, launch speed and force, brake targets and drive-tyre speed, each with − and +. Changes apply at once and can be undone with `/rcmc undo` |
| Trains | Lists the ride's trains with their speed. **Add train** puts a new one in the station, if it is clear. **x** removes a train. **Cars per new train** sets the length of the next one |

A ride runs one train at a time unless it has block sections (`/rcmc block`); with N blocks it can
run N − 1. Guests are turned away, with the reason, from a ride that is closed, testing or stopped.
A ride nobody has operated behaves as it always has: open, dispatching automatically.

## Sound

A coaster train sounds the way it is moving. Everything below follows the train's speed, so a
train running faster rolls louder and higher:

| Sound | When you hear it |
| --- | --- |
| Rolling | Whenever the train moves; carries up to about 40 blocks at speed |
| Chain lift | While the train climbs a lift: the anti-rollback dogs clack four times a second at 5 blocks/s |
| Brakes | Only while a brake run is actually slowing the train, louder the harder it brakes |
| Drive tyres | While tyres are pushing the train |
| Launch | Once, when a launch starts driving the train |
| Wind | Riders only, once the train passes about 5 blocks/s |

Riders hear their own train from inside it; everyone else hears it from where it is. Coaster sounds
follow Minecraft's **Blocks** volume slider, and `coasterSoundVolume` in the
[client config](../reference/config.md) scales or silences them. Metro trains have their own door,
chime and brake sounds.

## Ratings — what have I built?

```
/rcmc rate <sectionId>
```

RCT's three-number verdict, computed from a **simulated run at design time** rather than from an
actual ride — so a rating is available before the ride ever opens, exactly as in RCT.

| Number | Driven by |
| --- | --- |
| **Excitement** | Max and average speed, airtime duration, inversion count, drop height, lateral variety, ride length |
| **Intensity** | Peak G in all three axes, max speed, how long high G is sustained |
| **Nausea** | Lateral G *unmatched by bank*, inversion count, direction-change frequency, sustained helices |

The difference between the bank you authored and the bank the turn actually required **is** the
discomfort — which is why a properly banked curve rates so differently from an unbanked one of the
same radius.

A **safety verdict** is reported separately from the scores: a ride exceeding the configured G
limits is flagged as unsafe. In the RCT tradition, you are allowed to build the death machine and
then told exactly what you have done.

!!! warning "The formulae are uncalibrated"

    The rating maths has never been checked against how a ride actually *feels* to a human. If a
    number seems wrong, it may well be. That comparison is exactly the feedback the project wants.

An incomplete lap — a train that stalls halfway — is called out explicitly, so a broken circuit
cannot produce a plausible-looking rating.

## Multi-train operation

### Coasters: fixed block sections

```
/rcmc block <sectionId> auto
/rcmc block <sectionId> <count|off>
```

Divides a circuit into block sections, each holding at most one train. A train may not enter a
block while any part of another train is still in it; it waits at the end of the block it is in,
held there — on a lift or a slope as firmly as on the flat — until the block ahead is clear. Block
sections are saved with the world and can be undone like any other edit.

**`auto` is the one to use.** It ends a block wherever the ride has hardware that can hold a
train: the end of each **block brake**, the end of the station, and the top of the lift. Lay block
brakes with the track tool's *Block brake* segment — at the end of the course before the station,
and before the lift — and a two-train ride needs nothing more. A block brake trims every train to
a crawl as it passes, so it is always slow enough to be stopped at the end of the brake if the
block ahead is occupied, and lets it through when it is not.

A number instead of `auto` cuts the circuit into that many equal blocks, whatever is there, which
can hold a train mid-drop on a brake that does not exist. It is kept for testing.

Without block sections, nothing keeps two trains apart. If two trains on one coaster run into each
other, the ride **emergency-stops** and everyone in the dimension is told. It stays stopped while
the trains overlap, so take one off at the [operator panel](#operating-a-ride) (or add block
sections) before resetting it.

Know the limits before you rely on it:

- **A block must be longer than the trains using it.** A train held at a block's end needs the
  whole of itself inside that block, or its tail keeps the block behind it occupied.
- **N trains on N wall-to-wall blocks deadlock permanently.** That is a property of exclusive
  fixed-block signalling, not a defect — the command reports the safe train count when it divides a
  section.
- The closed-circuit wrap correction assumes the wrapping pair of blocks share one section. A
  circuit assembled from several joined sections falls back to a simpler calculation that may be
  wrong.

**Turn it off and trains crash.** That is a feature, and it comes with appropriate drama.

### Metro: movement authority

```
/rcmc line signals <lineName> <count|off>
```

Different model, same underlying braking law. Rather than a permission to enter the next block, the
driver is given a **distance it is authorised to run** — the nearest boundary of any block occupied
by another train, walked in the train's own facing, so it is safe on bidirectional single track. It
brakes to that limit the same way it brakes to a station.

## Failure modes and recovery

| Symptom | What it is | Fix |
| --- | --- | --- |
| A train stops mid-circuit and stays there | **Valleying** — it did not have the energy to crest something. Detected and surfaced rather than left as a silent hang | Give it more energy: a taller lift, a shallower hill |
| A metro train is parked and stuck | Same detection, but recoverable | `/rcmc line start` — entering service clears a valleyed train |
| A coaster stopped with "trains collided — emergency stop" | Two trains on the ride overlapped | Remove a train at the operator panel, reset the emergency stop, and open the ride. Add block sections to run several trains |
| A coaster stopped with "a train rolled back on the lift" | A train failed to clear something after the lift, fell back over the crest, and the lift's anti-rollback caught it — it is held where it was caught | Reset the emergency stop to send it up again; if it fails the same hill every time, give it more energy: a taller lift or a lower hill after it |
| A train did not come back after a restart | Its track was deleted while the world was closed, so it has nowhere to be | Rebuild the section, then `/rcmc train` |
| A service did not resume, but its train did | The line it worked was deleted or renamed, or the train can no longer reach any of its stations | `/rcmc line start <name> <trainId>` |
| Track "ignores" a height you placed | Was the vertical-overshoot sag; now fixed by clamped node tangents. If you still see it, report it | — |

## What happens when nobody is watching

**Trains pause in place when nobody is online.** Not a shutdown — nothing advances at all, so a
train holds its exact position and speed, a dwell timer holds its remaining ticks, and a service
stays a service. When someone joins it continues as though no time had passed. One player anywhere
on the server keeps the overworld's lines running, even from another dimension.

**A train in unloaded chunks keeps running.** Its physics is one number advanced against saved
geometry and never reads the world, so there is nothing to load. What it does not have out there is
*car entities* — those exist only where the world is loaded enough to hold them, and are recreated
from the train within a second of the chunks coming back. The train was never the entities.

Between those two, a park costs a busy server almost nothing: an empty server is no work, and the work when there are players is proportional to the number of trains rather than to the
size of the park.

## Performance notes

Track meshes are cached per section and rebuilt only on edit, so a large park costs nothing per
frame for track that has not changed. **Recolouring rebuilds a section's mesh** — the same cost as
any other edit, which is fine because painting is occasional, but it is not free during a live
colour sweep.

Track more than 256 blocks from the camera is not drawn at all. Level of detail is **not
implemented yet**, so everything nearer than that is drawn in full detail, which in a very large
park is more track geometry than it strictly needs.
