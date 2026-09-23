# Your first metro line

This guide builds a simple surface metro: a straight line with three stations, platforms to board
from, and a train that runs it on its own. If you have not yet,
[try the metro demos](demos.md#the-metro-line) first to see one running.

## What you need

- A **creative world with cheats on**. A superflat world is easiest.
- From the **Rails & Coasters** creative tab: the **Track Builder** (`rcmc:track_tool`) and the
  **Transit Builder** (`rcmc:transit_tool`). Later, a **Line Control Desk** (`rcmc:line_desk`) and
  some signs.
- Room for a line about 250 blocks long.

Metro track is laid with the **same Track Builder as coasters**. What makes it metro is how it
looks, the stations you add to it, and the trains you run on it.

## Step 1 — Lay the track

1. Hold the Track Builder. The segment type should say **Plain track** (press ++g++ to change it if
   not).
2. Right-click the ground at one end of your line to place the first node.
3. Walk along the route and place a node every **30–40 blocks**. Keep it straight or gently curving:
   trains slow down for tight curves.
4. At the far end, place the last node, then **right-click the air** to finish the track.

Chat gives you the new section's **id**. You'll need it for the train.

!!! tip "Metro track likes it gentle"

    Trains keep line speed through curves with a radius of about 190 blocks or more, and slow down
    for anything tighter: a curve of radius 30 holds them to about 6 blocks/s. Stations are best on
    straight, level track. The Transit Builder points out anything that's a problem (see step 8).

## Step 2 — Give it the metro look

The Transit Builder has several **modes**. Press ++g++ to step through them: **station → platform →
line → signal → switch → track style**. Chat says which one you're in, and while you hold the tool,
a coloured post and a label mark where a click would land and what it would do.

1. Press ++g++ until the mode is **Track style**.
2. Right-click the track. Each click cycles its style: plain transit rails, then with **overhead
   wires** (catenary), then a tunnel portal look, then tunnel, then back to coaster. Stop at the one
   you want. Overhead wires look great on a surface line.

See [Track styles](../reference/track-styles.md) for all of them.

## Step 3 — Add the stations

1. Press ++g++ until the mode is **Station**.
2. **Name it first** (optional): rename the Transit Builder in an anvil, and the next station you place
   takes that name. Unnamed, stations are called `Station1`, `Station2` and so on. Keep names to one
   word, such as `Harbor` or `CentralPark`: commands like `/rcmc platform` take the name as a single word.
3. Right-click the track where a train should stop. A green post shows exactly where.
4. Do the same for two more stations, **at least 60 blocks apart**, the first and last near the ends
   of the line.

![The Transit Builder in station mode: a green post marks where the station would go](../assets/images/transit-tool-station.jpg)

Sneak-right-click a station to remove it.

## Step 4 — Build the platforms

A metro car's floor is two blocks above the rails, so riders need a platform level with the floor
to walk aboard. Build one at each station:

```
/rcmc platform <station name>
```

This lays a platform along the track, following its curve, at exactly the right height, with a
warning-striped edge. By default it's 40 blocks long, 4 wide, on both sides. See
[`/rcmc platform`](../reference/commands.md#rcmc-platform) for the options, such as
`/rcmc platform Central 50 3 left` for one side only.

**The platform decides which doors open.** Where there's a platform, the doors on that side open;
where there isn't, they stay shut. You can also build platforms by hand from any blocks, at the
same height, and they work the same way.

## Step 5 — Create the line

A **line** is the route a train works: which stations, in which order, and what happens at the ends.

1. Press ++g++ until the mode is **Line**.
2. Right-click each station **in order**, from one end to the other. Chat numbers each stop.
3. Press ++v++ to choose what happens at the ends:
    - **Shuttle**: at each end the train stops and reverses back down the same track. This is what
      you want for a single straight line.
    - **Loop**: for a line that runs round a circuit and never ends.
    - **Turnback**: for a circuit with two tracks, where trains carry on round a loop at each end
      onto the other track.
4. Rename the tool in an anvil to name the line (for example `Green`), then press ++c++ to create it.
   Line names must be one word.

## Step 6 — Run a train

```
/rcmc train <section id> 3 0 metro
/rcmc line start <line name> <train id>
```

The first command puts a 3-car metro train on your track. Chat gives its train id. The second puts it
**into service**. From now on it drives itself: it speeds up to line speed, slows for curves, stops
at each station, opens its doors on the platform side, waits, and carries on. At each end it
reverses and heads back.

**Or use a Line Control Desk.** Place one anywhere and right-click it. Press **Add train**, and a train
is put on the first free platform of the line and starts in service. The desk shows every train on
the line and what it's doing: see [Running a metro line](../guide/running-a-metro.md).

To ride: stand on a platform, wait for a train to arrive and open its doors, and **walk in**. To get
off, walk out through an open door at a station.

## Step 7 — Signs and announcements

Stations come alive with signs. All of them **link to the nearest station** when you place them.

| Block | What it does |
| --- | --- |
| **Station Sign** (`rcmc:station_sign`) | A line map on a post: the line's stops, with this station highlighted |
| **Arrival Board** (`rcmc:arrival_board`) | Hangs from a ceiling and shows the trains due in each direction. It's large: it needs 5 blocks of space across and 2 down |
| **Station Speaker** (`rcmc:station_speaker`) | Announces trains as they approach, with a chime |

The trains have their own signs, inside and out, and announce each stop as they arrive.

## Step 8 — Check your line

Hold the Transit Builder and look along your track. Anything worth a look is marked on the track in
amber or red: **curves** that make trains crawl, **steep grades**, and **platforms** that are sloping
or curved. For a list with coordinates:

```
/rcmc line check <line name>
```

Nothing is refused: it's advice.

## Going further

- **More trains.** Add them from the desk. Trains never run into each other: each stops short of the
  one ahead. To space them out properly, add **signals**: see
  [Running a metro line](../guide/running-a-metro.md#signals).
- **Island platforms**, where one station serves two tracks, **junctions** with switches, and
  **underground stations**: [Building a metro line](../guide/building-a-metro.md).
- The [underground network demo](demos.md#the-underground-network) shows all of these together.
