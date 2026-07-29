# Getting started

## Requirements

| | |
| --- | --- |
| Minecraft | **1.12.2** only — see [why](#why-1122-only) |
| Loader | Minecraft Forge for 1.12.2 |
| Java (to play) | Java 8, as 1.12.2 requires |
| Java (to build) | JDK 17–22; **21 is the sweet spot** |

There is no release build yet. To play, clone the repository and run a development client:

```sh
git clone https://github.com/Mica-Technologies/RCMC.git
cd RCMC
./gradlew runClient       # dev client
./gradlew runServer       # dev dedicated server
./gradlew build           # compile, unit test, and produce the jar
```

Set `JAVA_HOME` to a JDK 17–22 install before each `gradlew` invocation. The mod itself targets
Java 8 via Jabel regardless of which JDK runs Gradle — only the JVM running the build changes.

!!! note "Apple Silicon"

    `runClient` on an ARM Mac needs the Rosetta path — `./gradlew runClient -Prosetta`. The
    native-ARM `runClient17` task launches but its window is broken. Setup notes are in
    `addon.gradle`.

## Sixty seconds to something moving

Both families ship a demo command that builds a complete, working example at your feet. You need
operator permission (`/rcmc` is permission level 2) and some open space.

=== "A coaster"

    ```
    /rcmc demo
    /rcmc train 0 5 0
    ```

    `/rcmc demo` lays a full circuit — station, chain lift, drop, banked turn, airtime hill,
    brake run — and tells you the section id it used (`0` if this is your first). `/rcmc train`
    parks a five-car train in the station; it dispatches itself. Right-click a car to board.

    Then ask the game what you built:

    ```
    /rcmc rate 0
    ```

=== "A metro"

    ```
    /rcmc metrodemo
    /rcmc train 0 3 0 metro
    /rcmc line start Metro 0
    ```

    `/rcmc metrodemo` lays a flat three-station alignment, styles it with overhead catenary and
    registers a line called **Metro**. The train enters service, drives itself between stations,
    berths, opens its doors and reverses at each terminus.

    For a tunnelled closed loop instead of a straight line:

    ```
    /rcmc metrodemo underground
    ```

    That one builds a double-track subway loop and registers a line called **Subway**.

The demo commands echo the exact follow-up command to run, including the section id they
allocated, so you never have to guess it.

## Where things live

- **Track is not blocks.** A section is a spline stored in the world's saved data, not a run of
  tile entities. That is what lets a train keep running through unloaded chunks and keeps a
  500-block circuit from being 500 tile entities. It also means you cannot break track with a
  pickaxe — use [`/rcmc rmsection`](../reference/commands.md#rcmc-rmsection) or the track editor.
- **Supports, platforms and signage are** real blocks, and behave like blocks.
- Everything persists with the world, versioned from day one: track, ride hardware, stations,
  lines, switches, signalling, **and the trains running on them**. A metro service resumes on the
  same line at the same cruise speed when the world reloads.

## Next steps

- [Coasters vs metro](coasters-vs-metro.md) — pick a family and understand what changes
- [Building a coaster](building-a-coaster.md) — the two coaster tools, node by node
- [Building a metro line](building-a-metro.md) — stations, lines, switches, platforms, signals
- [Riding and operations](riding-and-operations.md) — boarding, camera, HUD, block sections

## Why 1.12.2 only

Deliberate, and stated up front in the project's non-goals. Cross-version support would mean
depending on an abstraction layer, and the mod does enough unusual things — custom rendering with
no chunk binding, a custom network transport for train state, camera roll — that the abstraction
would cost more than it saved. One version, done properly.
