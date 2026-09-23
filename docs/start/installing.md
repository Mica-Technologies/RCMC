# Installing

## The easy way: Alto_EXPERIMENTAL

RCMC is included in the **Alto_EXPERIMENTAL** modpack in the Mica Minecraft launcher. Pick that pack,
launch it once so the launcher downloads the new files, and RCMC is installed. Nothing else to do.

!!! warning "Singleplayer for now"

    The Alto server runs the main Alto pack, which does not include RCMC yet. RCMC has to be on
    both the server and your game, so with it in your pack you won't be able to join that server.
    Try RCMC in a **singleplayer world** — a creative, superflat world is ideal.

## Installing it yourself

RCMC is a single jar for **Minecraft 1.12.2** with **Forge**.

| | |
| --- | --- |
| Minecraft | **1.12.2** only |
| Forge | 1.12.2, build 14.23.5.2860 (the last) recommended |
| Java | Java 8, as Minecraft 1.12.2 needs |
| Other mods | None required |

1. Download `rcmc-<version>.jar` from the
   [latest release](https://github.com/Mica-Technologies/RCMC/releases/latest) on GitHub.
2. Put it in your `mods` folder.
3. On a server, put the same jar in the server's `mods` folder too: RCMC must be on both sides.

Every release lists the jar's SHA-1, SHA-256 and MD5 in its notes if you want to check your
download.

### Optional companions

- **City Super Mod (CSM).** If it is installed, metro announcements are spoken aloud with its
  text-to-speech voice. Without it, they appear as on-screen subtitles. RCMC detects it on its own;
  nothing to configure.
- **JEI** and **The One Probe** work alongside RCMC as normal.

## Where to find everything in game

Everything RCMC adds is in its own **Rails & Coasters** creative tab: the four building tools,
platforms, gates, signs, control panels and supports. See
[Items and blocks](../reference/items-and-blocks.md) for what each one does.

Nearly everything else is done with the **`/rcmc`** command, which needs operator permission
(cheats on, in singleplayer). Tab completion fills in the options. See [Commands](../reference/commands.md).

RCMC adds four key bindings, all under **Controls → Rails & Coasters**, and all used by the building
tools: see [Keybinds](../reference/items-and-blocks.md#keybinds).

## Next

[Try the demos](demos.md): one command builds a complete working ride in front of you.
