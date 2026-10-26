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

`rcmc:piece_tool` — the prefab palette. Twelve standard manoeuvres, each with one adjustable
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
| ++g++ | Cycle mode: station → platform → line → signal → switch → track style |
| Right-click track | Do this mode's thing here |
| ++c++ | Commit what is being assembled |
| ++v++ | In line mode, cycle the line kind: loop / shuttle / turnback |
| Sneak + right-click track | The mode's destructive counterpart |
| Sneak + right-click air | Abandon what is being assembled |

While you hold it, a coloured post and label mark the track under the crosshair and say what a
click will do there — which station a platform joins, or which stop a line click adds. In line
mode the stops already picked are marked in order; in signal mode every signal is marked. While you
hold it the metro checks are drawn on the track too — see
[building a metro](../guide/building-a-metro.md#1-lay-the-alignment).

**Rename the tool in an anvil to name what you place next** — the tool called `Central` places a
station called Central. Unnamed falls back to `Station1`, `Line1` and so on. Keep names to one word:
commands take a name as a single word, and the tool warns when one has a space.

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
| `rcmc:station_sign` | Post-mounted line map: the line's stops in order with a "you are here" marker, scrolled to keep that marker in view on a long line. At a station with more than one platform it also names the platform it stands at — both, on an island between two tracks. Right-click cycles lines at an interchange |
| `rcmc:arrival_board` | Ceiling-hung amber-on-black board, double-sided. Per-direction rows reading minutes (`3 min`), or `1 stop` / `N stops` until the line's trains have been timed, `APPR` when this station is the train's next stop and `BRD` while it is berthed, with the platform in brackets for a train due here (`BRD (2)`). A **multiblock**: needs 5 blocks across, centred on where you place it, and 2 down |
| `rcmc:station_speaker` | Wall- or ceiling-mounted PA announcing approaching trains. At a station with more than one platform the arrival call names the platform ("…is now approaching platform 2"). Mounts on the face you click |
| `rcmc:air_gate` | A gate for a coaster platform's edge, gate side toward the track. Links to the nearest coaster station; opens while a train is loading there and shuts before it leaves, and the train waits for it. Laid automatically with a coaster station's platform — see [Boarding a coaster](../guide/riding.md#boarding-a-coaster) |
| `rcmc:signal_head` | A rail signal, in eighteen styles on eight mounts, each style its own item. Linked to a line's block signalling it shows the line's real signals; it can also be set by hand, by redstone or by command. See [Signal heads](#signal-heads) below. `/rcmc signal heads` places linked heads along a whole line |
| `rcmc:line_desk` | A metro control desk. Right-click for a live view of a line's trains, with buttons to add, remove and hold them and set the line's dwell and headway. Runs any line, opening on the one serving the nearest station — see [running the line](../guide/building-a-metro.md#running-the-line) |
| `rcmc:ride_sign` | Post-mounted board for a coaster's queue: the ride's name, open / closed / testing / stopped, and its rating and statistics. Links to the nearest coaster station within 32 blocks; right-click to relink |
| `rcmc:operator_panel` | A coaster's control desk. Links to the nearest coaster station within 24 blocks when placed (or when first used, if none was near). Right-click to operate the ride: open / test / close, emergency stop, automatic or manual dispatch, the ride's hardware settings, and its trains. See [Operating a coaster](../guide/operating-a-coaster.md) |

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

## Signal heads

A signal head is one block. It draws its head, its lenses or arm, and its mount, so a mast or a
gantry reaches down to the ground on its own. Lenses are lit, so they read in a dark tunnel, and
heads further than 128 blocks away are not drawn.

### Styles

| Style | What it shows |
| --- | --- |
| LED, 3-aspect | Red over yellow over green |
| LED, 2-aspect | Red over green: stop or clear |
| LED with lunar white | Red, yellow and green, with a lunar-white lamp below for the subsidiary aspect |
| Searchlight | One lens that changes colour |
| Dwarf | A low ground signal: a red lens over a proceed lens that shows yellow or green |
| Platform starter | Red over green, with a "departure" box below that lights `RA` (right away) when a train at the platform behind it is due to leave |
| Colour light, 3-aspect | Classic hooded lamps on a white-bordered backplate: green, yellow, red, top to bottom |
| Colour light, 4-aspect | Yellow, green, yellow, red, top to bottom. It adds double yellow (preliminary caution): the next two blocks are clear and the third is occupied |
| Semaphore, upper quadrant, home | A red arm that rises 45° to clear. A spectacle in front of the lamp shows red or green |
| Semaphore, lower quadrant, home | A red arm that drops 45° to clear |
| Semaphore, upper / lower quadrant, distant | A yellow fishtail arm. It is "on" (caution, yellow lamp) until the stop signal ahead is clear |
| Position light | Amber lamps around a black disc: a horizontal row for stop, a diagonal for caution, vertical for clear |
| Colour-position light | Pairs of lamps: red horizontal for stop, yellow diagonal for caution, green vertical for clear |
| Route feather | Five lunar-white lamps in a line at 45°, lit when its switch is set to its branch |
| Theatre | A box that shows up to three characters for the route its switch is set to |
| Call-on | A "stop and proceed" plate with two lunar-white lamps, lit for the subsidiary aspect |
| Train stop (trip cock) | An arm beside the rail, raised while its signal is red, lowered otherwise |

Semaphore arms and trip cocks swing to their new position instead of jumping to it.

### Mounts

The face you click sets the mount: a side gives a **wall** bracket, the underside a **ceiling**
hanger, and the top the style's own mount. Change it afterwards in the head's screen.

| Mount | |
| --- | --- |
| Post | A short post on the block below |
| Wall | A bracket off the wall beside it: for tunnels |
| Ceiling | Hung from the block above: a tunnel roof |
| Ground | A base at ground level: dwarf signals and trip cocks |
| Mast | A tall mast with a ladder and a maintainer's platform, reaching down to the first solid block |
| Gantry | Under a lattice beam across the tracks on two legs. Set its width in the screen |
| Cantilever | Under an arm reaching out from one mast at the side. Set the arm length in the screen |
| Platform | A low post at a driver's eye level, for platform ends |

**Hand** (left or right, as a driver facing the head sees it) puts a cantilever's mast on that
side, and sets which way a semaphore arm and a route feather point.

### Control

Right-click a head with an empty hand to open its screen. It sets the style, mount, hand and
facing (turn by 15° or 90°, or **Face track** to face the nearest track square on), and what
drives the head:

- **Manual:** it shows the aspect you pick. Right-click buttons offer only the aspects that style
  can show.
- **Redstone:** the strongest redstone signal reaching it picks the aspect. No power is red, full
  power (a lever) is green, power 1 to 7 is yellow and 8 to 14 double yellow. A head that cannot
  show an aspect shows the next more restrictive one. A two-aspect head clears at any power, and
  a distant is "off" at any power. A head with a subsidiary lamp lights it while powered.
- **Linked:** it repeats one signal of a signalled line. It shows red while the block it protects
  holds a train, yellow while the next block does, double yellow (four-aspect only) while the one
  after that does, and green otherwise. A head whose signal no longer exists (the line was
  re-signalled or unsignalled) shows red.

On placement a head links to the nearest block boundary of a signalled line within 16 blocks,
takes the signal for trains that see its face, and turns to face them. Sneak-right-click with an
empty hand links an unlinked head or unlinks a linked one.

**Approach lit:** a linked head with this on stays dark until a train is in the block before it.

**Route indicators** (feather and theatre) link to the switch whose throat is nearest, within 48
blocks, when placed or with **Link switch**. A feather lights while the switch is set to the branch
chosen with the branch buttons. A theatre shows the text set for whichever branch the switch is
set to: choose a branch, type up to three characters, and press **Set**. If the indicator is also
linked to a signal, it stays dark while that signal is red.

Hold the **transit tool** to see what each head is linked to: a line runs from the head to its
signal's boundary on the track, in the colour of its aspect, and a lunar-white line to its switch.

`/rcmc signal set` sets any of this from a command or a command block; see
[Commands](commands.md#rcmc-signal).
