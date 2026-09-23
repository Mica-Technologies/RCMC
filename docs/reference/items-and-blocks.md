# Items and blocks

Everything registers under the `rcmc:` prefix and appears in the mod's own creative tab.

## Items

### Track tool

`rcmc:track_tool` — freeform track building, node by node.

| Input | Effect |
| --- | --- |
| Right-click a block | Place a node one block above it |
| Sneak + right-click a block | Place a node and commit the section |
| Right-click air | Commit the section |
| Sneak + right-click air | Undo the last node. With no nodes pending it does nothing — `/rcmc build cancel` resets the session |
| ++g++ | Open the segment picker: plain track, chain lift, launch track, backward launch, brake run, block brake, drive tyres, transfer track, station |
| Sneak + ++g++ | Step to the next segment type in that order |
| ++r++ | Reset height and bank adjustments |
| Shift + scroll | Height of the pending node, 0.5 blocks per notch |
| ++ctrl++ + scroll | Bank, 5° per notch |

Clicking near the **first** node closes the circuit instead of adding a node. A ghost preview shows
the provisional curve as translucent track while you build.

### Piece tool

`rcmc:piece_tool` — the prefab palette. Nine standard manoeuvres, each with one adjustable
parameter.

| Input | Effect |
| --- | --- |
| Right-click a block | Start a chain, or append the selected piece |
| Sneak + right-click a block | Append and commit |
| Right-click air | Commit the chain |
| Sneak + right-click air | Undo the last piece; on an empty chain, cancel |
| ++g++ or ++ctrl++ + scroll | Cycle the selected piece |
| Shift + scroll | Resize the selected piece |
| ++r++ | Undo the last piece — the same as sneak + right-click air, without having to aim at nothing |

Pieces: straight, slope (signed rise), curve left/right, helix left/right, vertical loop,
corkscrew, zero-g roll, Immelmann, dive loop, airtime hill. Full parameter ranges are in
[building a coaster](../guide/building-a-coaster.md#the-palette).

### Track editor

`rcmc:track_editor` — edits track that already exists, so a coaster does not have to be rebuilt to
change one thing.

| Input | Effect |
| --- | --- |
| Right-click near track | Select it, report what is there, and open the editor screen on the nearest node |
| ++g++ | Cycle the segment type of the selected span |
| ++c++ | Cycle colour |
| ++v++ | Cycle which part the colour applies to: rails, spine, ties, supports |
| Sneak + right-click near track | Delete that section and its hardware |

Edits act on **spans** — the stretch between two placed nodes — not on a point.

The editor screen moves, banks, adds and deletes nodes, sets the type of the span leaving the node,
paints, and deletes the section. Its section tools split, join, close, reverse and restyle the
section. See
[The editor screen](../guide/building-a-coaster.md#the-editor-screen).

### Transit tool

`rcmc:transit_tool` — builds metro systems by pointing at track.

| Input | Effect |
| --- | --- |
| ++g++ | Cycle mode: station → platform → line → switch → track style |
| Right-click track | Do this mode's thing here |
| ++c++ | Commit what is being assembled |
| ++v++ | In line mode, cycle the line kind: loop / shuttle / turnback |
| Sneak + right-click track | The mode's destructive counterpart |
| Sneak + right-click air | Abandon what is being assembled |

While you hold it, a coloured post and label mark the track under the crosshair and say what a
click will do there — which station a platform joins, or which stop a line click adds. In line
mode the stops already picked are marked in order.

**Rename the tool in an anvil to name what you place next** — the tool called `Central` places a
station called Central. Unnamed falls back to `Station N` / `Line N`.

---

## Keybinds

Four bindings, all rebindable under **Controls → Rails & Coasters**, all reused across tools
because each tool gives them its own meaning:

| Key | Track tool | Piece tool | Track editor | Transit tool |
| --- | --- | --- | --- | --- |
| ++g++ | Open the segment picker (sneak: cycle) | Cycle piece | Cycle selected span's type | Cycle mode |
| ++r++ | Reset adjustments | Undo last piece | — | — |
| ++c++ | — | — | Cycle colour | Commit |
| ++v++ | — | — | Cycle painted part | Loop / shuttle / turnback |

---

## Blocks

| Block | Purpose |
| --- | --- |
| `rcmc:track_support` | A stackable pillar. Placing against the top of an existing support extends the column. Because the track tool places a node one block above whatever you click, clicking the top of a column puts a node exactly on top of it |
| `rcmc:platform` | Station platform decking |
| `rcmc:platform_edge` | Platform edge with the tactile warning strip; faces the track |
| `rcmc:station_sign` | Post-mounted line map: the line's stops in order with a "you are here" marker. Right-click cycles lines at an interchange |
| `rcmc:arrival_board` | Ceiling-hung amber-on-black board, double-sided. Per-direction rows reading minutes (`3 min`), or `1 stop` / `N stops` until the line's trains have been timed, `APPR` when this station is the train's next stop and `BRD` while it is berthed, with the platform in brackets for a train due here (`BRD (2)`). A **multiblock**: needs 5 blocks across, centred on where you place it, and 2 down |
| `rcmc:station_speaker` | Wall- or ceiling-mounted PA announcing approaching trains. Mounts on the face you click |
| `rcmc:operator_panel` | A coaster's control desk. Links to the nearest coaster station within 24 blocks when placed (or when first used, if none was near). Right-click to operate the ride: open / test / close, emergency stop, automatic or manual dispatch, the ride's hardware settings, and its trains. See [Operating a ride](../guide/riding-and-operations.md#operating-a-ride) |

The three signage blocks **auto-link to the nearest station** on placement — including via
`/setblock` — and store only that station's name, resolving against the live registry every frame.
They cannot go stale.

!!! note "The arrival board fills the space it looks like it fills"

    The board's screen is four blocks wide and two tall, so the board claims that space: the block
    you place becomes the top-centre of a ten-block panel, and the other nine fill in around it as
    `rcmc:arrival_board_part`. Those are not an item and never appear in the creative tab — break
    any of them, or the one you placed, and the whole board comes down as a single item.

    Placement is refused outright if the footprint is not clear, and the item is handed back. Boards
    placed before this existed stay one block and keep working exactly as they did; re-place one to
    give it the footprint.

!!! note "Track itself is not a block"

    A track section is a spline in the world's saved data, not a run of tile entities. That is what
    lets a train run through unloaded chunks and keeps a 500-block circuit from being 500 tile
    entities. So there is nothing to mine: remove track with `/rcmc rmsection`, `/rcmc undo`, or
    sneak + right-click with the track editor.

    Auto-generated **supports** are the exception in one direction — they are not blocks either,
    but they are solid and you can stand on them.
