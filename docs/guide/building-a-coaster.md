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
| Sneak + right-click air | Undo the last node; on an empty session, cancel |
| ++g++ | Cycle the segment type of nodes placed from now on |
| ++r++ | Reset height/bank adjustments |
| Shift + scroll | Adjust the pending node's height (0.5 blocks per notch) |
| ++ctrl++ + scroll | Adjust bank (5° per notch) |

Nodes accumulate in a **build session** rather than going straight into the world: a section needs
at least two nodes to have geometry, every edit rebuilds its curve, and a half-built curve must
never be visible to a train. A **ghost preview** shows the provisional curve as translucent track —
not a centreline, because a centreline does not tell you whether the rails will clip terrain or how
the banking will sit, which are the two things you are actually judging.

!!! tip "Every node you place changes the curve on *both* sides of it"

    The track is a centripetal Catmull-Rom spline, which uses each node as a tangent handle for
    its neighbours. So placing a node reshapes the span *before* it too. This is not something
    anyone predicts by eye — which is exactly why the ghost preview exists. Watch it, don't
    calculate.

### Segment types

++g++ cycles what the span from the current node onward *is*. Pick the type before placing the
nodes it should cover:

| Type | What it becomes on commit |
| --- | --- |
| **Plain track** | Nothing — just track |
| **Chain lift** | A chain lift: constant pull up to a target speed, and the train cannot outrun the chain while engaged |
| **Brake run** | A trim brake, bleeding speed to a target |
| **Station** | A station platform: stop, dwell, dispatch |

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

!!! note "Why ++r++ duplicates sneak+right-click-air"

    The sneak *flag* is never reliably set while flying, and coaster building is done flying. The
    keybind is the path that actually works in the air.

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
| Right-click near track | Select it and report what is there |
| ++g++ with a selection | Cycle the segment type of the selected span |
| ++c++ | Cycle the colour |
| ++v++ | Cycle which part the colour applies to |
| Sneak + right-click near track | Delete that whole section and its hardware |

Edits work on **spans** — the stretch between two placed nodes — rather than at a point. Retyping
"this bit of track" means the piece you can see, and a span is the smallest thing with a length for
an element to occupy.

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
/rcmc train <sectionId> <cars> <startSpeed>     spawn a train
/rcmc rate <sectionId>                          excitement / intensity / nausea
/rcmc block <sectionId> <count|off>             divide into block sections for multi-train running
/rcmc info                                      list sections and trains
```

`/rcmc rate` simulates a lap **offline** — no entity is spawned, so it is safe on a circuit that
already has a train running. It reports the safety verdict separately from the scores, and calls
out an incomplete lap explicitly, so a coaster that stalls halfway cannot produce a
plausible-looking rating.

See [Riding and operations](riding-and-operations.md) for boarding, the ride HUD, G-force effects
and multi-train block signalling.
