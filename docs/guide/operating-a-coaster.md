# Operating a coaster

A coaster runs itself as soon as it has a train: it dispatches, runs its lap, and comes back for more.
This page is about taking control: opening and closing the ride, dispatching by hand, running more
than one train, naming it and putting up a sign, and reading its ratings.

## The operator panel

Place a **Ride Operator Panel** (`rcmc:operator_panel`) at the coaster's station and right-click it. It
links itself to the nearest coaster station within 24 blocks, and its controls face you.

![The operator panel: state and dispatch controls on the left, trains below them, and the ride's hardware settings on the right](../assets/images/operator-panel.jpg)

| Control | What it does |
| --- | --- |
| **Open / Test / Close** | **Open** is normal service. **Test** runs the trains but nobody may board. **Close** lets every train finish its lap and holds it in the station |
| **EMERGENCY STOP** | Brakes every train on the ride to a stop wherever it is, including on the lift, and holds it there. It also closes the ride. Press **Reset emergency stop**, then **Open**, to run again. When the ride stops itself, the badge says why: **E-STOP: COLLISION** or **E-STOP: ROLLBACK** |
| **Auto / Manual** | **Auto** dispatches a train by itself once its time in the station is up. **Manual** holds it there until you press **DISPATCH**; one press sends one train |
| **Storage** | With a linked [transfer track](building-a-coaster.md#storing-trains): **Store train** sends the next train to storage, **Retrieve** brings it back. Pressed again, each cancels |
| **Hardware** | The station's dwell time and pass-throughs, lift speed, launch speed and force, brake targets and drive-tyre speed, each with − and +. Changes apply at once, and `/rcmc undo` takes them back |
| **Trains** | The ride's trains and their speeds. **Add train** puts a new one in the station if it's clear. **x** removes a train. **Cars per new train** sets the length of the next one, and **Car** which kind of car: sit-down, over-the-shoulder or wooden classic |

A ride nobody has touched behaves as if it is open and on Auto. The same controls are commands, for an
admin or a command block: `/rcmc ride <id> <open|test|close|stop|reset>`.

### How the station runs

When a train arrives, the station brakes stop it at the platform and the **air gates open**. After the
dwell time (or when you press **DISPATCH** in manual mode), the gates close, and once they have been
shut a moment the brakes release the train. With a ride just opened after being closed, the train
waiting in the station gets a fresh loading time, gates open, before it goes.

A **closed** or **emergency-stopped** ride keeps the gates open by any train it's holding, so riders
can get off.

## Name it and put up a sign

```
/rcmc ride <id> name Thunderbolt
```

Then place a **Ride Sign** (`rcmc:ride_sign`) by the queue. It links to the nearest coaster station
within 32 blocks and keeps itself up to date: the ride's **name**, whether it is **open**, closed,
testing or stopped, its **ratings**, top speed, length and inversions. A ride that fails its safety
check is flagged. Right-click the sign to relink it.

![The ride sign by the queue](../assets/images/ride-sign.jpg)

The operator panel shows the ride's name as its title, too.

## Ratings

```
/rcmc rate <id>
```

Every ride gets RollerCoaster Tycoon's three numbers, worked out from a simulated lap, so you can rate a
ride before it ever opens:

| Rating | What raises it |
| --- | --- |
| **Excitement** | Speed, airtime, inversions, big drops, variety, length |
| **Intensity** | High G in any direction, top speed, how long high G lasts |
| **Nausea** | Sideways G the banking doesn't cancel, inversions, rapid changes of direction, long helices |

The gap between the bank you built and the bank a turn really needed **is** what makes riders feel
sick, which is why a properly banked turn rates so differently from a flat one of the same size.

A **safety verdict** comes separately: a ride that goes past the G limits is flagged as unsafe. As in
RCT, you can build the death machine; you'll just be told exactly what you've built. A lap the train
can't finish is called out as such, so a broken circuit can't produce a plausible-looking rating.

!!! warning "The ratings are not calibrated yet"

    The rating formulas haven't been checked against how rides actually feel to people. If a number
    looks wrong, it may well be, and that's exactly the feedback the project wants.

## The ride check

```
/rcmc check <id>
```

This runs the ride's own train round a lap from its station and lists every stretch where riders
would feel too much, and anywhere the train can't get past:

- **G into the seat** at the bottom of a drop or a tight valley;
- **G out of the seat** over a hill taken too fast;
- **sideways G** in a curve too tight, or banked too little, for its speed;
- **forward or backward G** from a launch or brake that's too sharp;
- **stalls**, where the train can't get past a hill or some hardware.

The same check is drawn on the track, amber and red, while you hold the **Track Editor**: see
[Checking the ride](building-a-coaster.md#checking-the-ride).

## Running more than one train

A coaster runs one train at a time unless it has **block sections**. Each block holds one train; a
train waits at the end of its block until the block ahead is empty, held firmly even on a lift or a
slope.

```
/rcmc block <id> auto
/rcmc block <id> <count|off>
```

**`auto` is the one to use.** It ends a block wherever the ride has hardware that can hold a train: the
end of each **block brake**, the end of the station, and the top of the lift. Put block brakes on the
course with the Track Builder's *Block brake* segment, one before the station and one before the lift,
and a two-train ride needs nothing more. A block brake slows every passing train to a crawl, so it can
always be stopped at the end of the brake if the block ahead is occupied, and lets it through when
it's clear.

A number instead of `auto` cuts the circuit into that many equal blocks wherever they fall, which can
try to hold a train mid-drop where there are no brakes. It's there for testing.

With **N** blocks, a ride can run **N − 1** trains. Add them from the operator panel.

Things to know:

- **A block must be longer than the trains using it.** A train held at the end of a block needs all of
  itself inside that block, or its tail keeps the block behind occupied.
- **As many trains as blocks deadlocks.** Every block is full and no train can move. The command tells
  you the safe number of trains.
- Block sections are saved with the world, and `/rcmc undo` takes them back.

**Without block sections**, only the station keeps trains apart: a train that arrives while another is
still in the station waits where it stopped until the first has gone. Anywhere else, nothing does. If
two trains on one coaster run into each other, the ride **emergency-stops** and everyone in the
dimension is told. Take a train off at the operator panel (or add block sections) before resetting it.

## Storage and shuttles

A **transfer track** can slide a train sideways off the circuit onto a storage track, to take it out of
service. And a **shuttle coaster** runs out and back through its station instead of round a circuit.
Both are covered in [Building a coaster](building-a-coaster.md#storing-trains).

## When something goes wrong

See [Troubleshooting](../help/troubleshooting.md) for what a stopped ride, a stalled train or a
rollback on the lift means, and how to fix each.
