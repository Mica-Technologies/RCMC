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
/rcmc metrodemo [underground|loop|subway]
```

With no argument: a flat, catenary-styled, roughly 469-block alignment with three named stations
and a line called **Metro**, registered and ready for a train.

With `underground` (or the synonyms `loop` / `subway`): a closed, flat, tunnel-styled **double-track
loop** with a line called **Subway**.

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

Wipes **all** track in the world. Blunt, and rarely what you want.

### `/rcmc undo` &nbsp;·&nbsp; `/rcmc redo`

Steps back and forward through track edits, server-side.

---

## Trains

### `/rcmc train`

```
/rcmc train <sectionId> <cars> <startSpeed> [coaster|metro|metrocompact|metrolong]
```

Spawns a train on a section. Defaults to the coaster car style. A train parked in a station
dispatches itself.

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
/rcmc station <name>            create or move a station at the track you are aiming at
/rcmc station list
/rcmc station remove <name>
```

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
