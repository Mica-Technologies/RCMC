# Building a coaster

There are two ways to lay coaster track, and they answer different questions. Most coasters use
both.

- The **track tool** is freeform: you place every node, so the track goes exactly where you need
  it — between two buildings, over that ravine, through this window.
- The **piece builder** is a prefab palette: you pick a standard manoeuvre and click it onto the
  end of the last one. A spline editor cannot produce a good vertical loop by eye; the numbers
  that keep its G-forces survivable are not something anyone eyeballs by dragging control points.

Both feed the same track. Start a run with pieces, continue it freehand, finish it with pieces.

---

## The track tool (freeform)

Item: **`rcmc:track_tool`**.

| Action | Effect |
| --- | --- |
| Right-click a block | Place a track node one block above it |
| Sneak + right-click a block | Place a node **and commit** the section |
| Right-click air | Commit the section as it stands |
| Sneak + right-click air | Undo the last node (does nothing once none are left — see `/rcmc build cancel` below) |
| ++g++ | Open the segment picker: choose the type of nodes placed from now on |
| Sneak + ++g++ | Step to the next segment type without opening the picker |
| ++r++ | Reset height/bank adjustments |
| Shift + scroll | Adjust the pending node's height (0.5 blocks per notch) |
| ++ctrl++ + scroll | Adjust bank (5° per notch) |

Nodes accumulate in a **build session** rather than going straight into the world: a section needs
at least two nodes to have geometry, every edit rebuilds its curve, and a half-built curve must
never be visible to a train. A **ghost preview** shows the provisional curve as translucent track —
not a centreline, because a centreline does not tell you whether the rails will clip terrain or how
the banking will sit, which are the two things you are actually judging.

While you hold the tool, a **builder HUD** in the top-left corner shows its state as it stands: the
segment type the next nodes will be, the current bank and height offset, how many nodes are
pending (and whether the section will close into a circuit), and the run, rise and grade from the
last node to the cursor. A grade that is too steep is flagged in amber with a hint to place a node
partway, and when the cursor is over the first node the panel says the next click will close the
circuit. A short key legend sits underneath. It hides while the ++f3++ debug screen is open.

!!! tip "Every node you place changes the curve on *both* sides of it"

    The track is a centripetal Catmull-Rom spline, which uses each node as a tangent handle for
    its neighbours. So placing a node reshapes the span *before* it too. This is not something
    anyone predicts by eye — which is exactly why the ghost preview exists. Watch it, don't
    calculate.

### Segment types

++g++ opens a picker listing every segment type, with the current one marked; click one and the
screen closes. It sets what the span from the current node onward *is*, so pick the type before
placing the nodes it should cover. Sneak + ++g++ steps to the next type without the screen.

The types are:

| Type | What it becomes on commit |
| --- | --- |
| **Plain track** | Nothing — just track |
| **Chain lift** | A chain lift: constant pull up to a target speed, and the train cannot outrun the chain while engaged |
| **Launch track** | An LSM-style launch: pushes at ~8 blocks/s² toward 22 blocks/s, then switches the motors off |
| **Backward launch** | The same launch fired the other way, toward where you started laying — what sends a shuttle coaster up its rear spike |
| **Brake run** | A trim brake, bleeding speed to a target |
| **Block brake** | A brake that trims every train to a crawl and ends a block section: with `/rcmc block <id> auto` a train waits at its end while the block ahead is occupied |
| **Drive tyres** | Station friction wheels holding a 2 blocks/s creep — for positioning a train on the platform |
| **Transfer track** | Drive tyres that can slide sideways onto a storage track, train and all — how a ride takes a train out of service. See [storage](#storing-trains) |
| **Station** | A station platform: stop, dwell, dispatch |

!!! note "Launches never fight the train"
    A launch only pushes a train that is at rest or moving its way. A train running back over it the
    other way coasts through with the motors off — which is what lets a shuttle coaster put a forward
    and a backward launch on the same stretch of track it runs both ways.

### Storing trains

A **transfer track** takes a train off the circuit without anyone lifting it off by hand. Lay a
*Transfer track* segment on the approach to the station, at least a train's length long and after
the brakes, then lay a straight, level, open section beside it — the storage track, within 16
blocks and at least as long. Link them:

```
/rcmc transfer <rideSectionId> <storageSectionId>
```

From then on the ride's operator panel has **Store train** and **Retrieve** buttons. *Store* stops
the next train that reaches the transfer track, at its far end, and slides it across to storage;
*Retrieve* slides the stored train back as soon as the transfer track and a train's length either
side of it are clear, and it rolls on into the station. Each button, pressed again, cancels. Storage
holds one train. Until it is linked, and whenever nothing is asked of it, a transfer track is just
drive tyres.

### Shuttle coasters

A shuttle runs out and back rather than round. Lay an open section with a spike at each end, a
station in the middle, a **Launch track** segment ahead of the station and a **Backward launch**
behind it. Then open the operator panel and set the station's **Pass-throughs** to 1: the train is
let back through the platform once, between the two launches, and caught the time after. Set it to
0 and the station catches the train the first time it comes back — right for a ride with a single
launch and a spike at one end. `/rcmc demo shuttle` builds a complete one.

!!! tip "A launch is a force, not a promise"

    Unlike a chain lift, a launch has nothing physically holding the train at its target — the
    motors push, and whatever speed results is the result. So **the length you tag decides the exit
    speed**: at the default 8 blocks/s² you need about 30 blocks of launch to reach 22, and a
    shorter run simply leaves slower (`sqrt(2 × a × length)`). Tag a longer run to launch harder.

    That is exactly how real launches behave, and it is why a launch can be tuned to overshoot or
    fall short in a way a chain lift structurally cannot.

### Committing and validation

On commit the section is run through the validator and any findings are reported to you — and then
it is **committed anyway**. That is deliberate: *RollerCoaster Tycoon* lets you build the lethal
ride and then tells you what you have built, which is far more fun than a refusal.

Useful session commands:

```
/rcmc build status              what is pending, current bank, circuit mode
/rcmc build bank <degrees>      bank for nodes placed from now on
/rcmc build circuit [true|false]  close the next section into a loop (needs 3+ nodes)
/rcmc build cancel              discard the pending nodes
```

Clicking near the **first** node of a run closes the circuit instead of adding a node.

---

## The piece builder (prefabs)

Item: **`rcmc:piece_tool`**.

| Action | Effect |
| --- | --- |
| Right-click a block | Start a chain there, or append the selected piece |
| Sneak + right-click a block | Append **and commit** |
| Right-click air | Commit the chain as a section |
| Sneak + right-click air | Undo the last *piece*; on an empty chain, cancel |
| ++g++ or ++ctrl++ + scroll | Cycle the selected piece |
| Shift + scroll | Resize the selected piece |
| ++r++ | Undo the last piece |

The builder HUD shows the piece tool's state too: the selected piece, its parameter, and how many
pieces and nodes the chain has so far.

!!! note "Why ++r++ duplicates sneak+right-click-air"

    Both work, on the ground or flying. Undo is something you do constantly, and a key of its own
    is a better gesture for it than sneak + right-click, which also has to be aimed at empty air.

### The palette

Nine pieces, each with one adjustable parameter:

| Piece | Parameter | Range (default) |
| --- | --- | --- |
| Straight | Length | 4–64 (12) |
| Slope | Rise — signed, so scrolling through zero turns a climb into a drop | −24 to +24 (6) |
| Curve left | Radius | 4–48 (12) |
| Curve right | Radius | 4–48 (12) |
| Helix left | Radius | 4–32 (10) |
| Helix right | Radius | 4–32 (10) |
| Vertical loop | Top radius | 3–20 (6) |
| Corkscrew | Length | 8–48 (18) |
| Airtime hill | Radius | 6–48 (16) |

You choose the direction of travel exactly once, when the chain is anchored. After that every
piece's placement is fully determined by the exit state of the piece before it — that is the whole
point, and it is what guarantees there is never a gap or a kink at a join.

Curves and helices come out **correctly banked** for their radius at the design speed. That
balance between authored bank and required bank is what the nausea rating measures, so a
well-banked turn is not cosmetic.

---

## Editing what you already built

Item: **`rcmc:track_editor`**.

| Action | Effect |
| --- | --- |
| Right-click near track | Select it, report what is there, and open the **editor screen** on the nearest node |
| ++g++ with a selection | Cycle the segment type of the selected span |
| ++c++ | Cycle the colour |
| ++v++ | Cycle which part the colour applies to |
| Sneak + right-click near track | Delete that whole section and its hardware |

Edits work on **spans** — the stretch between two placed nodes — rather than at a point. Retyping
"this bit of track" means the piece you can see, and a span is the smallest thing with a length for
an element to occupy.

### The editor screen

Right-clicking track opens a panel along the bottom of the screen, so the track stays in view
while you edit it. A yellow post marks the node being edited, drawn through terrain so it is never
hidden.

| Control | Effect |
| --- | --- |
| **< Prev** / **Next >** | Step to the previous or next node of the section |
| **X / Y / Z  − +** | Move the node along that axis by the step size |
| **Step** | Change the step size: 0.5, 1 or 4 blocks |
| **Bank − +** | Bank the track at this node by 5° |
| **Part** / **colour** | Choose which part to paint, then cycle its colour |
| **Add node** | Add a node halfway along the span to the next node (or beyond the end of open track) |
| **Delete node** | Remove this node. A section keeps at least two nodes, or three for a circuit |
| **Delete section** | Delete the whole section. Press it twice to confirm |
| Segment type list | Set what the span from this node to the next *is*: plain track, a lift, a launch, a brake and so on. The span's current type is highlighted. Hardware running on past either end of the span keeps those parts: retyping one span of a long lift leaves the lift on the spans either side |

Everything on the track goes with an edit. Moving, adding or removing a node keeps each piece of
hardware, station, block section and train in the same place between the same two nodes, and
every change can be undone with `/rcmc undo`.

#### Section tools

**Section tools >** at the bottom of the type list swaps it for edits to the whole section:

| Control | Effect |
| --- | --- |
| **Split here** | Split the section at this node into two sections that stay joined, so trains still run straight through. On a circuit, this opens it at the node into one run whose ends are joined |
| **Join** | At an end node: merge with the nearest other section end within 3 blocks into one continuous section, or close the section into a circuit if its own other end is closest. The button says which: *Join to #7* or *Close circuit* |
| **Reverse direction** | Turn the section round so trains run the other way |
| **Style** | Cycle the section's track style: coaster, then each transit look |

A split section is still one ride: the halves share one operator panel, one e-stop and one set of
hardware settings, and a lift or brake cut in two by the split is still one row on the panel.
Merging two sections joins their rides, keeping whichever had been operated.

A section has one style, so to change style part-way along, split it there and restyle one half.
Two sections of different styles won't merge; restyle one to match first.

Hardware, stations and trains go with the track. Split cuts any hardware running across the node
into two pieces, one either side. Reversing keeps hardware on the same stretch of track, working in
the new direction, and a station keeps its stop point the same distance from the platform's exit.

Some edits are refused, with the reason shown:

- **Reversing with trains or metro platforms on the section.** Take them off first: they would
  end up facing backwards.
- **Any edit that touches a transfer table or its storage track.** Unlink it with
  `/rcmc transfer <id> off` first.
- **Any edit to track under a metro line's signals.** Clear the signals first.
- **Merging an end that belongs to a switch.** Remove the switch first.

Block sections on the edited track are cleared, and the message says so; lay them out again with
[`/rcmc block <id> auto`](../reference/commands.md#rcmc-block).

Mistakes are recoverable: **`/rcmc undo`** and **`/rcmc redo`** step through track edits
server-side.

### Continuing an existing run

Starting a chain within snapping distance of a free end **extends that section** rather than laying
a new one beside it. That matters more than it sounds: one section is one spline, continuous by
construction, so an extension has no seam. The same nodes laid as a *separate* joined section kink
by several degrees at the join — which the physics reads as an instantaneous change of direction.

Appending is always safe. **Prepending** onto a section that already has ride elements or a train
is refused with an explanation, because prepending redistributes arc length along the whole
section rather than shifting it by a constant — there is no offset that would keep a lift hill over
the same piece of track.

---

## Supports and colour

**Supports generate automatically** beneath a committed section, at intervals, down to terrain —
including on banked and inverted track, where a column stands outboard of the track and an arm
reaches in. Columns that would pass through the track are abandoned rather than drawn through it.
Generated supports are solid: you can stand on them.

!!! info "A loop's crown is unsupported, on purpose"

    No vertical column can reach the top of a vertical loop without passing through the loop
    itself. Real loops use surrounding structure — A-frames and lattice work, which is planned but
    not built. The gap is intentional, not a missing support.

For hand-built structure, place **`rcmc:track_support`** blocks. They stack: placing against the
top of an existing support extends the column. And because the track tool always places a node one
block above whatever you click, clicking the top of a support column puts a node exactly on top of
it — no special-case snapping needed.

**Colour** is per section, across four parts: rails, spine, ties and supports. Twelve named
colours — steel, graphite, white, red, orange, yellow, green, teal, blue, purple, pink, brown. A
short list rather than a colour picker, for the same reason RCT used one: constrained palettes are
why RCT parks look coherent instead of garish.

Trains are painted by command, since a moving train is not something you point at reliably:

```
/rcmc paint <trainId> <body|trim|seats> <colour>
```

---

## Testing the ride

```
/rcmc train [sectionId] [cars] [startSpeed]     spawn a train (defaults: first section, 5 cars, at rest)
/rcmc rate <sectionId>                          excitement / intensity / nausea
/rcmc block <sectionId> <auto|count|off>        divide into block sections for multi-train running
/rcmc info                                      list sections and trains
```

`/rcmc rate` simulates a lap **offline**: the ride's own train (or five cars if it has none), from
rest at its station, through a copy of its hardware — no entity is spawned and the running ride is
not touched, so it is safe on a circuit that already has a train running. It reports the safety verdict separately from the scores, and calls
out an incomplete lap explicitly, so a coaster that stalls halfway cannot produce a
plausible-looking rating.

See [Riding and operations](riding-and-operations.md) for boarding, the ride HUD, G-force effects
and multi-train block signalling.
