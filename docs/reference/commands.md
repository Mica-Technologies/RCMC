# Command reference

Everything is a subcommand of **`/rcmc`**, which requires **permission level 2** (operator). Tab
completion covers subcommands, track styles, line and switch subcommands, platform sides and train
styles.

```
/rcmc <demo|metrodemo|train|clear|info|build|paint|style|rate|block|station|line|switch|platform|rmsection|undo|redo>
```

---

## Demos

### `/rcmc demo`

```
/rcmc demo [scale] [liftHeight]
```

Builds a complete demo coaster at your feet: station, chain lift, drop, banked turn, airtime hill,
brake run, back to the station. Reports the section id it allocated, the spans of each element, and
the exact `/rcmc train` line to run next.

| Argument | Range | Default |
| --- | --- | --- |
| `scale` | 0.4 – 4.0 | 1.0 |
| `liftHeight` | 8 – 120 | 34 |

### `/rcmc metrodemo`

```
/rcmc metrodemo [underground|network|loop|subway]
```

With no argument: a flat, catenary-styled, roughly 469-block alignment with three named stations
and a line called **Metro**, registered and ready for a train.

With `underground` (or the synonyms `network` / `loop` / `subway`): the whole two-line underground
network, built at your feet — track, tunnel, platforms, decking, signage, stations and lines in one
command. It places on the order of a hundred thousand blocks, so expect a pause.

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
/rcmc build circuit [true|false]    close the next section into a circuit (needs 3+ nodes)
/rcmc build cancel                  discard pending nodes and reset bank/circuit mode
```

### `/rcmc style`

```
/rcmc style <sectionId> <coaster|transit|transit-catenary|transit-portal|transit-tunnel> [wireHeight]
```

Restyles one section. Purely visual — the spline and the physics are untouched. `wireHeight` is
7–15 blocks and applies only to the styles that carry a wire; it rides on the style id itself as a
suffix (`transit-catenary-12`). See [track styles](track-styles.md).

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
/rcmc train <sectionId> <cars> <startSpeed> [coaster|metro|metrocompact|metrolong] [distance]
```

Spawns a train on a section. Defaults to the coaster car style. A train parked in a station
dispatches itself.

`distance` places the train at a point along the section instead of at its first station stop point.
On a metro circuit whose two directions are its two tracks, *where* a train starts is *which way* it
runs — so this is how you get an inbound and an outbound service running at the same time, rather
than two trains nose to tail on the same track. `/rcmc info` reports section lengths and
`/rcmc station list` reports each berth's distance along one.

| Style | Based on | Body length |
| --- | --- | --- |
| `coaster` | — | Coaster car |
| `metrocompact` | NYC A-Division / R142 | 15.65 m |
| `metro` | MBTA Orange Line, CRRC 65 ft class | ~19.8 m |
| `metrolong` | LA HR4000 / NYC 75-footers | ~22.9 m |

### `/rcmc rate`

```
/rcmc rate [sectionId]
```

Simulates a lap **offline** — no entity is spawned, so it is safe to run on a circuit that already
has a train — and reports excitement, intensity and nausea, plus a separate safety verdict. An
incomplete lap is called out explicitly.

### `/rcmc block`

```
/rcmc block <sectionId> <count|off>
```

Divides a coaster circuit into `count` equal block sections, each holding at most one train, or
removes block signalling with `off`. Reports the safe train count for the division. See
[multi-train operation](../guide/riding-and-operations.md#multi-train-operation).

---

## Transit

### `/rcmc station`

```
/rcmc station <name>                                  create or move a station at the track you are aiming at
/rcmc station list
/rcmc station remove <name>
/rcmc station doors <name> [platform] <left|right|both|auto>    which side the doors open
/rcmc station platform <name> add [label]             add a berth at the track you are standing by
/rcmc station platform <name> remove <label|number>
/rcmc station platform <name> list
/rcmc station platform <name> label <label|number> <new label>    rename a berth; 'none' clears it
```

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
into: `Boarding (2)`. A label that only repeats the direction the row already sits under is left off
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
/rcmc line create <name> <loop|shuttle> <stationA> <stationB> [...]
/rcmc line list
/rcmc line remove <name>
/rcmc line start <name> <trainId> [cruiseSpeed]     put a train into service on the line
/rcmc line stop <trainId>                           withdraw it
/rcmc line signals <name> <count|off>               install or clear block signalling
```

A line stores its ordered **stations**; the route between them is discovered by walking the track
graph, so it survives changes to the track underneath.

### `/rcmc switch`

```
/rcmc switch create <throatSection> <start|end> <branchSection> <start|end> [...]
/rcmc switch list
/rcmc switch throw <throatSection> <start|end> [branchIndex]     no index = cycle to the next branch
/rcmc switch remove <throatSection> <start|end>
```

Each end is named by its section id and which end of it — `start` or `end`. Trailing moves against
the points dead-end.

### `/rcmc platform`

```
/rcmc platform <station> [length] [width] [left|right|both]
```

Lays a platform along the alignment at exactly car-floor height, following the spline's curve and
centred on the stop point. Only fills air and replaceable blocks.

---

## Quick recipes

=== "Coaster from nothing"

    ```
    /rcmc demo
    /rcmc train 0 5 0
    /rcmc rate 0
    ```

=== "Metro from nothing"

    ```
    /rcmc metrodemo
    /rcmc train 0 3 0 metro
    /rcmc line start Metro 0
    ```

=== "Two trains on one coaster"

    ```
    /rcmc block 0 3
    /rcmc train 0 5 0
    /rcmc train 0 5 0
    ```

=== "Metro line, hand-built"

    ```
    /rcmc style 0 transit-catenary
    /rcmc station North
    /rcmc station Central
    /rcmc station South
    /rcmc line create Green shuttle North Central South
    /rcmc platform Central 24 4 both
    /rcmc train 0 3 0 metro
    /rcmc line start Green 0
    ```
