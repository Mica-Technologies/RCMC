# Command reference

Everything is a subcommand of **`/rcmc`**, which requires **permission level 2** (operator). Tab
completion covers subcommands and most of their arguments: demo kinds, track styles, train styles,
`build`, `block`, `ride` and `transfer` options, and the `station`, `line`, `switch` and `platform`
subcommands and sides.

```
/rcmc <demo|metrodemo|train|trains|clear|info|build|paint|style|rate|check|block|transfer|ride|station|line|switch|platform|rmsection|undo|redo>
```

---

## Demos

### `/rcmc demo`

```
/rcmc demo [scale] [liftHeight]
/rcmc demo shuttle
```

Builds a complete demo coaster, with its platform at your feet: chain lift, a first drop that curves
through the far turnaround, an airtime camelback, a banked climbing turn, and a block brake back
into the station, so `/rcmc block <id> auto` divides it for two trains as built. The station gets
its platform, with air gates where the train stops. Every bank and
hill is worked out from the speed the train carries there, so the demo passes
[`/rcmc check`](#rcmc-check) at any size. Reports the section id it allocated, the spans of each
element, and the exact `/rcmc train` line to run next.

| Argument | Range | Default |
| --- | --- | --- |
| `scale` | 0.4 – 4.0 | 1.0 |
| `liftHeight` | 16 – 120 | 34 |

The scale is held between 1.0 and 1.4 times `liftHeight / 34`: a tall lift on a small layout pulls
more G than a rider can take, and a big layout on a low lift runs out of energy before it gets
home. The reply says when it changed the scale you asked for.

`/rcmc demo shuttle` builds a launched shuttle coaster instead: a straight station with a forward
launch ahead of it, a backward launch behind it, and a 45-block spike at each end. The train goes
out the front, falls back through the station, is launched up the rear spike, and is caught in the
station on its way forward again.

### `/rcmc metrodemo`

```
/rcmc metrodemo [underground|network|loop|subway]
```

With no argument: a flat, catenary-styled, roughly 469-block alignment with three named stations
and a line called **Metro**, registered and ready for a train.

With `underground` (or the synonyms `network` / `loop` / `subway`): the whole two-line underground
network, built at your feet — track, tunnel, platforms, decking, signage, stations and lines in one
command. It places on the order of a hundred thousand blocks, so expect a pause. The Airport line
runs twelve blocks below where you stand; in a world too shallow for that, such as a superflat one,
the whole network is raised until the lower level clears bedrock, and the command says so.

| | **Circle** | **Airport** |
| --- | --- | --- |
| Track | Closed circuit, two tracks 8 apart | Single track, one level down |
| Termini | Turning loops — the train never reverses | Stub ends — the train changes ends |
| Stops | Kingsway, Guildhall, Riverside, Exchange, Foundry, Lakeshore | Airfield, Docklands, Exchange, Parkway |
| Platforms | Islands, side platforms, and one three-berth interchange | Side, plus decking on both sides at Docklands |

**Exchange** is the interchange: one named place, three berths, two lines, on two levels. Its
arrival board shows both lines' trains.

!!! note "Why the Circle line's track is a loop but its service is not"

    Line names are single words because Minecraft's command parser splits on spaces and
    ignores quotes, and `/rcmc line start <name> <trainId>` ends with the train id.

    A metro does not stop inbound service while an outbound train runs — inbound and outbound are
    separate tracks, joined by a turning loop at each terminus. So the track is a closed circuit
    while the *service* is out-and-back: stations are listed once, each has a berth per direction,
    and a train drives forward for ever, out along one track and back along the other. That is what
    the line property in [`turnsBackOnLoop`](../design/TRANSIT.md) selects, and it is what makes a
    two-platform station mean anything.

---

## Track

### `/rcmc info`

Lists the sections and trains in this world — ids, lengths, node counts. The command you use to
find a section id.

### `/rcmc build`

Settings for the in-hand track builder. Operates on *your* build session.

```
/rcmc build status                  pending node count, current bank, circuit mode
/rcmc build bank <degrees>          bank applied to nodes placed from now on (-180 to 180)
/rcmc build height <blocks>         height of the next nodes above the clicked block's top (-32 to 64)
/rcmc build circuit [true|false]    close the next section into a circuit (needs 3+ nodes)
/rcmc build piece <name> [value]    select a piece for the piece tool, and set its parameter
/rcmc build cancel                  discard pending nodes and reset bank/circuit mode
```

`/rcmc build piece` does what ++g++ and shift + scroll do for the
[piece tool](items-and-blocks.md#piece-tool), for when you have no scroll wheel or want an exact
value: `/rcmc build piece immelmann 26`. The pieces are `straight`, `slope`, `curve_left`,
`curve_right`, `helix_left`, `helix_right`, `vertical_loop`, `corkscrew`, `zero_g_roll`,
`immelmann`, `dive_loop` and `airtime_hill`, and tab completion lists them.
Leave the value out to keep the piece's current setting.

`/rcmc build height` sets the same offset that scrolling with the track tool adjusts, but to any
value, fractions included — a 5% grade or a vertical curve wants heights the scroll steps cannot
reach. Like the scrolled offset, it lasts until the section is committed.

### `/rcmc track`

Lays track from coordinates rather than clicks, for building from a plan or a script.

```
/rcmc track lay <style> [open|circuit] <nodes>                  build a new section through these nodes
/rcmc track lay <style> append <sectionId> [close] <nodes>      continue a section from its far end
/rcmc track nodes <sectionId> [file <name>]                     list a section's nodes, or write them to a file
```

`<nodes>` is either the nodes themselves or `file <name>`, which reads
`config/rcmc/polylines/<name>.txt`. Each node is `x,y,z`, or `x,y,z,bank` with the bank in degrees,
and nodes are separated by `;` or by line breaks. In a file, `#` starts a comment.

```
/rcmc track lay transit circuit 55.5,47,-2935.5; 55.5,47,-2895.5; 63.5,47,-2895.5; 63.5,47,-2935.5
/rcmc track lay transit-third-rail open file blue-line-north
```

- **Coordinates are used exactly.** The track tool snaps a click to the block centre and the block
  top; these do not, so curves, reverse curves and turnouts can put nodes off the grid. The height is
  the railhead's.
- **Segment types** follow a node as `@type`: `55.5,47,-2600@station`. As with the track tool's
  selected type, it describes the track *arriving* at that node and carries on until the next `@`;
  `@plain` ends a run. The types are `plain`, `lift`, `launch`, `launch_backward`, `brake`,
  `block_brake`, `tyres`, `transfer` and `station`.
- **The style** is any of the [track styles](track-styles.md), with a suffix if it takes one
  (`transit-catenary-12`, `transit-third-rail-left`).
- **`append`** continues an open section from its end, so nothing already on it moves. `close`
  makes the result a circuit. A different transit style given here starts from the first new node.
- A section laid this way is built exactly as the track tool builds one: same hardware for segment
  types, same station platform, same validator report, and it can be undone.
- A chat line holds 256 characters. Longer alignments go in a file, or through the server console or
  a command block, which have no such limit.

`/rcmc track nodes` prints each node with its distance along the section. With `file <name>` it
writes them to `config/rcmc/polylines/<name>.txt` in the format `lay` reads, so what you export lays
back as the same section, coordinate for coordinate.

### `/rcmc style`

```
/rcmc style <sectionId> <coaster|transit|transit-catenary|transit-portal|transit-tunnel> [wireHeight]
/rcmc style <sectionId> transit-third-rail [left|right]
```

Restyles one section. Purely visual — the spline and the physics are untouched. `wireHeight` is
7–15 blocks and applies only to the styles that carry a wire; it rides on the style id itself as a
suffix (`transit-catenary-12`). A third rail takes a side instead, `right` by default: the side it
runs on away from platforms, stored as `transit-third-rail-left` for the left. See
[track styles](track-styles.md).

### `/rcmc paint`

```
/rcmc paint <trainId> <body|trim|seats> <colour>
```

Repaints a train. Track colour is painted with the [track editor](items-and-blocks.md#track-editor)
instead — a moving train is not something you point at reliably.

**Colours:** `steel`, `graphite`, `white`, `red`, `orange`, `yellow`, `green`, `teal`, `blue`,
`purple`, `pink`, `brown`.

### `/rcmc rmsection`

```
/rcmc rmsection <sectionId>
```

Deletes one section along with its ride elements and trains. Use this rather than `/rcmc clear`
once a park has more than one thing in it.

### `/rcmc clear`

Wipes **everything** RCMC has built in the world: all track, ride hardware, trains, ride
settings, block sections, and every metro station, line and signal. Blunt, and rarely what you
want. `/rcmc undo` brings back the track, ride hardware, block sections and transit, but not the
trains or the operator-panel ride settings.

Section numbers are never reused, so the first section built afterwards does not take over an
operator panel or board that pointed at an old one.

### `/rcmc undo` &nbsp;·&nbsp; `/rcmc redo`

Steps back and forward through track edits, server-side.

---

## Trains

### `/rcmc train`

```
/rcmc train [sectionId] [cars] [startSpeed] [type] [distance]
/rcmc train remove <trainId>
```

Spawns a train on a section. A train parked in a station dispatches itself. Every argument is
optional, but they are positional, so to give one you must give all those before it.

| Argument | Range | Default |
| --- | --- | --- |
| `sectionId` | — | The first section in the world |
| `cars` | 1 – the type's most | The type's default |
| `startSpeed` | 0 – 60 blocks/s | 0 |
| `type` | any [train type](train-types.md) | `coaster` |
| `distance` | 0 – section length | The section's first coaster station stop point, else 0 |

`distance` places the train at a point along the section instead of at its first station stop point.
On a metro circuit whose two directions are its two tracks, *where* a train starts is *which way* it
runs — so this is how you get an inbound and an outbound service running at the same time, rather
than two trains nose to tail on the same track. `/rcmc info` reports section lengths and
`/rcmc station list` reports each berth's distance along one.

`remove` takes one train off the track, coaster or metro, in service or not — `/rcmc info` lists
their ids. A train left parked on a line holds up every service behind it, which stops for it.

The built-in types are `coaster`, `shoulder`, `wooden`, `metrocompact`, `metro` and `metrolong`; a
server can add more. See [Train types](train-types.md), or run `/rcmc trains`.

### `/rcmc trains`

```
/rcmc trains [reload]
```

Lists every train type the server knows: its id, name, body, default and most cars, and seats a
car, marking the ones that come from a file. `reload` reads `config/rcmc/trains/` again and
reports any file it skipped, and why. See [Train types](train-types.md).

### `/rcmc check`

```
/rcmc check <sectionId>
```

Runs the ride's own train round a simulated lap, from its station, and lists where riders would feel
more than is safe, and where a train cannot get past. Each warning says what was measured and
between which nodes: *6.2 g pressing riders into their seats — between nodes 9 and 10 of #1*.
Nothing is changed or blocked. The same check is marked on the track while you hold the track
editor. A section with no station has no ride to run.

### `/rcmc rate`

```
/rcmc rate [sectionId]
```

Simulates a lap **offline** — no entity is spawned, so it is safe to run on a circuit that already
has a train — and reports excitement, intensity and nausea, plus a separate safety verdict. An
incomplete lap is called out explicitly.

`sectionId` may be left off only while the world has exactly one section; once there are several,
name the one to rate.

### `/rcmc block`

```
/rcmc block <sectionId> <auto|count|off>
```

`auto` divides a closed coaster circuit into block sections ending at its hardware — the end of
each block brake, the end of the station and the top of the lift — and needs at least two of them.
A number divides it into that many equal blocks instead, wherever they fall. `off` removes block
signalling. Either way it reports the safe train count. Block sections
are saved with the world and can be undone like any other edit. See
[running more than one train](../guide/operating-a-coaster.md#running-more-than-one-train).

### `/rcmc ride`

```
/rcmc ride <sectionId> <open|test|close|stop|reset>
/rcmc ride <sectionId> platform [left|right|both] [width]
/rcmc ride <sectionId> name [name]
```

The operator panel's state controls, for an admin or a command block. `open` lets riders board;
`test` runs trains with nobody aboard; `close` lets trains finish their lap and hold them in the
station; `stop` is the emergency stop; `reset` clears it and leaves the ride closed. See
[operating a coaster](../guide/operating-a-coaster.md).

`platform` lays a platform along the ride's station: decking at the cars' floor, a warning-striped
edge, and air gates on the edge where the train stops. Both sides by default, 3 blocks wide
(1 – 12). Blocks only go into air, plants or natural ground, so it never replaces anything built;
run it again after changing the train's length and it fills in what is missing.

`name` gives the ride the name its ride signs show; leave it out to clear it.

### `/rcmc transfer`

```
/rcmc transfer <sectionId> <storageSectionId|off>
```

Links the transfer track on a coaster to a storage track: an open section laid alongside it, within
16 blocks and long enough to hold what the transfer track holds. The point on the storage track
nearest the start of the transfer track lines the two up. `off` unlinks it. Unlinking, or linking
different storage, is refused while a train is in storage: retrieve it first. Trains are stored and
retrieved from the ride's operator panel — see
[storing trains](../guide/building-a-coaster.md#storing-trains).

---

## Transit

### `/rcmc station`

```
/rcmc station <name> [<point>] [left|right|both|auto]     create or move a station
/rcmc station list
/rcmc station remove <name>
/rcmc station doors <name> [platform] <left|right|both|auto>    which side the doors open
/rcmc station platform <name> add [<point>] [label]      add a berth
/rcmc station platform <name> remove <label|number>
/rcmc station platform <name> list
/rcmc station platform <name> label <label|number> <new label>    rename a berth; 'none' clears it
```

Where a station or berth goes is the train's lead car's stopping point, given as `<point>`:

| `<point>` | Means |
|---|---|
| *(nothing)* | the point on the track **nearest where you are standing**, within 16 blocks — not where you are looking |
| `at <sectionId> <distance>` | exactly that distance along that section, as `/rcmc station list` and `/rcmc track nodes` report them |
| `<sectionId> <distance>` | the same, without the `at` |
| `near <x> <y> <z>` | the point on the track nearest that position, within 16 blocks |

The last three need no player, so a script or the server console can set up a whole line without
anyone moving: `/rcmc station Harbour at 13 4210.5 right`. For a station, the door side can follow;
`auto`, the default, works it out from the decking beside the track. Running `/rcmc station <name>`
again for a station that already exists moves it to a single new berth: any extra platforms it had
are dropped, and must be added again.

**Platforms** are the berths at a station — one per track through it. A station has one by default,
which is every ordinary single-track stop. An **island platform** has a running line down each side,
so it is *one* station with *two* berths: add the second with `platform … add`, standing beside the
other track.

That matters beyond tidiness. Each berth carries its own door side, because the two tracks look out
at the same decking from opposite hands. A train berths at whichever platform it can actually reach
on the track it is running on, so it opens the correct side automatically once both exist. Give them
labels — `Inbound`, `Outbound`, `1`, `2` — and signage can name them.

Berths are addressed by label or by number (`1` is the first authored):

```
/rcmc station platform Central add Outbound
/rcmc station doors Central Outbound left
/rcmc station doors Central 2 auto
/rcmc station platform Central label Outbound 2
```

Renaming a berth edits the label alone, rather than removing and re-adding it. A berth is identified
everywhere else by its stop point — a running service holds one, and the door side is matched back
through it — so re-adding would have to hit the same point exactly and would lose the door side on
the way past.

Labels are worth setting on an island, because an arrival board names the berth a train is pulling
into: `BRD (2)`. A label that only repeats the direction the row already sits under is left off
the board, so prefer `1` and `2` over `Inbound` and `Outbound` if you want it to show.

**Door side** is normally worked out for you: placing a station, or laying a platform, looks either
side of the track for something a passenger could step out onto — a surface at car-floor height
with two blocks clear above it — and records what it finds. RCMC's own platform blocks always
count; so does a platform you built by hand out of anything else. A tunnel wall does not, because
it is solid all the way up. `doors` is the override — use it for an island platform you only want
served on one side, or anywhere the world as built is not what you meant. `auto` re-runs the
detection — per berth, so at an island each track gets its own answer. With no platform argument the
side applies to every berth at the station.

Left and right are **as a train running forward along the track sees them**, so the answer does not
change when a service reverses. Announcements convert it to the rider's own left and right.

### `/rcmc line`

```
/rcmc line create <name> <loop|shuttle|turnback> <stationA> <stationB> [...]
/rcmc line list
/rcmc line remove <name>
/rcmc line start <name> <trainId> [cruiseSpeed] [forward|reverse]    put a train into service on the line
/rcmc line stop <trainId>                           withdraw it
/rcmc line signals <name> <count|off>               install or clear block signalling
/rcmc line set <name> dwell <seconds>               how long the doors stay open at each stop
/rcmc line set <name> headway <seconds|off>         least time between departures from a platform
/rcmc line trains [name]                            list trains in service, and what holds any up
/rcmc line hold <trainId>                           hold it at its next platform, doors open
/rcmc line release <trainId>                        let it go again
/rcmc line check [name]                             slow curves, steep grades, awkward platforms
```

Dwell defaults to 10 seconds and headway to off; both accept up to 600 seconds, are saved with the
line, and apply to running trains from their next stop. With a headway set, a train that would
leave a platform sooner than that after the previous train in the same direction waits with its
doors open. `line list` shows each line's settings. `line signals` divides a line into 2 to 256
blocks.

On a `turnback` line, a terminus with more than one berth is served twice: trains stop at the
berth they arrive at, run round the loop, and stop again at the next berth ahead before setting
off back down the line. A balloon-loop terminus can have a separate arrival and departure platform
this way.

A line stores its ordered **stations**; the route between them is discovered by walking the track
graph, so it survives changes to the track underneath.

**Which way a train starts.** A moving train keeps going the way it is moving. A standing one runs
the way the line's other trains on its track are already running, and when it is the first, toward
the nearest station either way. `forward` or `reverse` (along the track's own direction, the way its
distances increase) overrides that; if no station of the line can be reached that way, it says so
and runs the other way.

### `/rcmc switch`

```
/rcmc switch create <throatSection> <start|end> <branchSection> <start|end> <branchSection> <start|end> [...]
/rcmc switch create <throughSection> <distance | near <x> <y> <z>> <divergeSection>
/rcmc switch list
/rcmc switch throw <throatSection> <start|end> [branchIndex]     no index = cycle to the next branch
/rcmc switch remove <throatSection> <start|end>
```

A switch needs a throat and **at least two branches**; `create` refuses fewer. Each end is named
by its section id and which end of it — `start` or `end`. Trailing moves against
the points dead-end.

The second form puts a switch **partway along a section**, a circuit included, where a siding or
spur leaves it. Lay the diverging track first, starting at the points; then name the through
section, the point along it, and the diverging section. The through section is split there (see
[`/rcmc section`](#rcmc-section)) and the join at the split becomes the switch. Which side is the
throat follows from which way the diverging track leaves. A point within a block of the section's
end, where it is already joined to the next section, needs no split: the switch goes on that
joint. It starts lined straight, so trains run through exactly as before until it is thrown:

```
/rcmc switch create 13 18342.5 21      siding #21 leaves the Blue Line 18,342.5 blocks along
```

### `/rcmc section`

```
/rcmc section split <sectionId> <distance | near <x> <y> <z>>
```

Splits a section at a point, what the [track editor](items-and-blocks.md#track-editor)'s split does
at a node. If no node is within a block of the point, one is added there on the curve first, so the
track does not move. Open track becomes two sections joined at the point; a circuit is opened into
one section whose end meets its own start there, still joined, so trains run straight through either
way. Ride hardware, stations, berths, trains, joins and switches all follow the track, and the split
can be undone.

### `/rcmc platform`

```
/rcmc platform <station> [length] [width] [left|right|both]
```

Lays a platform along the alignment at exactly car-floor height, following the spline's curve. A
train stops with its **lead car** at the stop point, so the platform runs mostly back along the
train: three-quarters of `length` behind the stop point and a quarter ahead of it. Only fills air
and replaceable blocks.

| Argument | Range | Default |
| --- | --- | --- |
| `length` | 4 – 200 | 40 |
| `width` | 1 – 12 | 4 |
| side | `left`, `right`, `both` | `both` |

It builds at the station's **first berth** only. For the other track of an island, lay that side by
hand — the door detection reads a hand-built platform exactly like this one.

---

## Quick recipes

=== "Coaster from nothing"

    ```
    /rcmc demo
    /rcmc train 1 5 0
    /rcmc rate 1
    ```

=== "Metro from nothing"

    ```
    /rcmc metrodemo
    /rcmc train 1 3 0 metro
    /rcmc line start Metro 1
    ```

=== "Two trains on one coaster"

    ```
    /rcmc block 1 auto
    /rcmc train 1 5 0
    ```

    Then add the second train with **Add train** on the ride's operator panel, which waits until the
    station is clear. Two `/rcmc train` commands would put both trains in the station at once.

=== "Metro line, hand-built"

    ```
    /rcmc style 1 transit-catenary
    /rcmc station North
    /rcmc station Central
    /rcmc station South
    /rcmc line create Green shuttle North Central South
    /rcmc platform Central 24 4 both
    /rcmc train 1 3 0 metro
    /rcmc line start Green 1
    ```
