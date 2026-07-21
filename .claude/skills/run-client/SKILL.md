---
name: run-client
description: Launch and drive the RCMC dev Minecraft client on macOS (Rosetta path) — chat commands via synthetic keystrokes, screenshots via in-game F2, no screen-recording permission needed. Use when asked to run the client, check visuals in-game, or screenshot mod features.
---

# Running and driving the RCMC dev client (macOS, Apple Silicon)

Verified end-to-end on 2026-07-21 (macOS, Apple Silicon, Temurin 21 for Gradle). The client
is driven **blind**: you never see the screen directly — you send input with AppleScript/
`cliclick` and read back the game's own F2 screenshots with the Read tool. It works even
when the terminal has no Screen Recording permission (`screencapture` failing with "could
not create image from display" is expected and does not matter).

## One-time prerequisites

1. **Rosetta 2** installed (`softwareupdate --install-rosetta --agree-to-license`).
2. **x86_64 Temurin (Adoptium) JDK 8** in a Gradle-auto-detected location
   (`~/Library/Java/JavaVirtualMachines/temurin-8-x64` exists on this machine). The
   toolchain spec in `addon.gradle` resolves it by `Java 8 + Adoptium`.
3. **LWJGL2 x86_64 natives** in `.rosetta-natives/lwjgl2/` (gitignored, machine-local):
   ```bash
   curl -sSL -o /tmp/n.jar https://libraries.minecraft.net/org/lwjgl/lwjgl/lwjgl-platform/2.9.4-nightly-20150209/lwjgl-platform-2.9.4-nightly-20150209-natives-osx.jar
   mkdir -p .rosetta-natives/lwjgl2 && unzip -o -j /tmp/n.jar '*.dylib' -d .rosetta-natives/lwjgl2
   ```
4. **cliclick** for mouse clicks (`brew install cliclick`) — only needed for main-menu
   navigation; everything in-game is keyboard + commands.
5. **Accessibility permission** for the terminal (System Events keystrokes). Test:
   `osascript -e 'tell application "System Events" to get name of first process'` — if it
   answers, you are good.

## Launch

```bash
JAVA_HOME="$HOME/Library/Java/JavaVirtualMachines/temurin-21.0.11/Contents/Home" \
  ./gradlew runClient -Prosetta
```

Run it in the background. **Do not grep the piped gradle log for readiness — the pipe
buffers until process exit.** Poll for the window instead, then allow boot time:

```bash
osascript -e 'tell application "System Events" to tell (first process whose name contains "java") to if (count of windows) > 0 then get {name, position, size} of window 1'
```

Window appears in ~10–30s warm; the game needs a further ~45–60s to reach the menu/world.
First `--shot` returning a fresh PNG is the real readiness signal.

## Driving it

Use `mc.sh` in this directory (chmod +x if needed):

- `mc.sh "/rcmc info"` — focuses the game, opens chat with `t`, types char-by-char, Enter.
- `mc.sh --shot` — presses F2, prints the newest `run/screenshots/*.png`; **Read that file**
  to see the game. Works at the main menu too.
- `mc.sh --key "2"` — bare keypress (hotbar select, `r` builder-reset, Esc is
  `osascript -e 'tell application "System Events" to key code 53'`).

**Main menu → world**: the dev client usually auto-joins the last world. If a `--shot`
shows the menu, click Singleplayer at the window-relative fraction (49% height, centred),
scaled from the window bounds:

```bash
b=$(osascript -e 'tell application "System Events" to tell (first process whose name contains "java") to get {position, size} of window 1' | tr ',' ' ')
read wx wy ww wh <<< "$b"
cliclick c:$((wx + ww/2)),$((wy + 28 + (wh-28)*49/100))   # 28px ≈ title bar
```

Then screenshot again — world selection usually auto-plays the top entry on one click.

## Known pitfalls (each cost real time)

- **Keystrokes go to the focused window.** If the human touches the machine mid-run, your
  keys leak into their windows and their keys leak into the game. Tell them hands-off, and
  expect a stray half-command occasionally — a leaked partial chat string typed into the
  world presses keybinds (`g`, `r` belong to the build tool).
- **Never `TaskStop` the client without saving** — it kills the JVM with no world save.
  Send `mc.sh "/save-all"`, wait ~3s, then stop. Unsaved RCMC track/transit edits vanish.
- **Aim the camera with `/tp @p x y z yaw pitch`** (yaw: 0=south, 90=west, ±180=north,
  -90=east) instead of trying to mouse-look.
- **Finding a moving train**: `/tp @p @e[type=rcmc:coaster_car,c=1]`, then a relative
  `/tp @p ~ ~2 ~8 <yaw> <pitch>` and `--shot` fast — a metro at 15 blocks/s outruns slow
  command sequences. Berths/dwells (~8s) are the photo opportunities.
- **Clouds live at y≈128** and photobomb elevated track built near mountain tops; build
  demos lower or shoot from below.
- Entity tooltips confirm the mod loaded: cars are named "Ride Car".

## Verification recipes

- Metro end-to-end: `/rcmc metrodemo` → `/rcmc train <sectionId> 3 0 metro` →
  `/rcmc line start Metro <trainId>` → screenshots; `/rcmc info` must show the train
  `RUNNING` (never `VALLEYED`) through multiple terminus reversals.
- Coaster: `/rcmc demo` → `/rcmc train <sectionId> 5 0`.
- Track styles: `/rcmc style <sectionId> <coaster|transit|transit-catenary|transit-portal|transit-tunnel>`
  — mesh rebuilds live.
- Signage: `/setblock x y z rcmc:arrival_board` near a station; it self-links within ~2s.
- `/time set day` before glamour shots; `/gamemode 1` for flight.
