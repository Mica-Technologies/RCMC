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
| Sneak + right-click air | Undo the last node; on an empty session, cancel |
| ++g++ | Cycle segment type: plain → chain lift → launch track → brake run → drive tyres → station |
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
| ++r++ | Undo the last piece — the keybind that works while flying |

Pieces: straight, slope (signed rise), curve left/right, helix left/right, vertical loop,
corkscrew, airtime hill. Full parameter ranges are in
[building a coaster](../guide/building-a-coaster.md#the-palette).

### Track editor

`rcmc:track_editor` — edits track that already exists, so a coaster does not have to be rebuilt to
change one thing.

| Input | Effect |
| --- | --- |
| Right-click near track | Select it and report what is there |
| ++g++ | Cycle the segment type of the selected span |
| ++c++ | Cycle colour |
| ++v++ | Cycle which part the colour applies to: rails, spine, ties, supports |
| Sneak + right-click near track | Delete that section and its hardware |

Edits act on **spans** — the stretch between two placed nodes — not on a point.

### Transit tool

`rcmc:transit_tool` — builds metro systems by pointing at track.

| Input | Effect |
| --- | --- |
| ++g++ | Cycle mode: station → platform → line → switch → track style |
| Right-click track | Do this mode's thing here |
| ++c++ | Commit what is being assembled |
| ++v++ | In line mode, toggle loop / shuttle |
| Sneak + right-click track | The mode's destructive counterpart |
| Sneak + right-click air | Abandon what is being assembled |

**Rename the tool in an anvil to name what you place next** — the tool called `Central` places a
station called Central. Unnamed falls back to `Station N` / `Line N`.

---

## Keybinds

Four bindings, all rebindable under **Controls → RCMC**, all reused across tools because each tool
gives them its own meaning:

| Key | Track tool | Piece tool | Track editor | Transit tool |
| --- | --- | --- | --- | --- |
| ++g++ | Cycle segment type | Cycle piece | Cycle selected span's type | Cycle mode |
| ++r++ | Reset adjustments | Undo last piece | — | — |
| ++c++ | — | — | Cycle colour | Commit |
| ++v++ | — | — | Cycle painted part | Loop / shuttle |

---

## Blocks

| Block | Purpose |
| --- | --- |
| `rcmc:track_support` | A stackable pillar. Placing against the top of an existing support extends the column. Because the track tool places a node one block above whatever you click, clicking the top of a column puts a node exactly on top of it |
| `rcmc:platform` | Station platform decking |
| `rcmc:platform_edge` | Platform edge with the tactile warning strip; faces the track |
| `rcmc:station_sign` | Post-mounted line map: the line's stops in order with a "you are here" marker. Right-click cycles lines at an interchange |
| `rcmc:arrival_board` | Ceiling-hung amber-on-black board, 3.2 × 1.5, double-sided. Per-direction "N stops away", "Boarding" while berthed |
| `rcmc:station_speaker` | Wall- or ceiling-mounted PA announcing approaching trains. Mounts on the face you click |

The three signage blocks **auto-link to the nearest station** on placement — including via
`/setblock` — and store only that station's name, resolving against the live registry every frame.
They cannot go stale.

!!! note "Track itself is not a block"

    A track section is a spline in the world's saved data, not a run of tile entities. That is what
    lets a train run through unloaded chunks and keeps a 500-block circuit from being 500 tile
    entities. So there is nothing to mine: remove track with `/rcmc rmsection`, `/rcmc undo`, or
    sneak + right-click with the track editor.

    Auto-generated **supports** are the exception in one direction — they are not blocks either,
    but they are solid and you can stand on them.
