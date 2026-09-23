# Building from source

For contributors, and for trying changes that aren't in a release yet. To play, use a
[release](../start/installing.md) instead.

## Requirements

| | |
| --- | --- |
| Minecraft | **1.12.2** |
| JDK to build | JDK 17–22; **21 is the sweet spot** |
| Mod bytecode | Java 8, whichever JDK runs the build |

The build is the GregTechCEu Buildscripts, a wrapper around RetroFuturaGradle. The mod itself targets
Java 8 through Jabel regardless of which JDK runs Gradle: only the JVM running the build changes. Set
`JAVA_HOME` to a JDK 17–22 install before each `gradlew` invocation.

## Commands

```sh
git clone https://github.com/Mica-Technologies/RCMC.git
cd RCMC
./gradlew build           # compile, run the unit tests, and produce the jar
./gradlew test            # unit tests only: seconds, not minutes
./gradlew runClient       # a development client
./gradlew runServer       # a development dedicated server
```

The jar is written to `build/libs/`.

!!! note "Apple Silicon"

    On an ARM Mac, use the Rosetta path: `./gradlew runClient -Prosetta`. The native-ARM `runClient17`
    task launches, but its window is broken. Setup notes are in `addon.gradle`.

!!! warning "Don't build while a dev client is running"

    `./gradlew build` or `test` while `runClient` is running replaces classes under the running game,
    and it crashes with errors that look exactly like code bugs.

## Tests

`track.math` and `physics` never touch a Minecraft type, which is what lets the unit tests run on a
bare JVM in seconds. Keep it that way: convert at the entity and render boundary. CI builds and tests
every pull request, and boots a real dedicated server to catch client-only code leaking into common
code.

## This site

The wiki is the `docs/` folder, built with MkDocs Material. Preview it locally:

```sh
python -m pip install -r docs/requirements.txt
mkdocs serve
```

A new page must be added to `nav:` in `mkdocs.yml`. CI builds with `--strict`, so a broken link
fails the build.
