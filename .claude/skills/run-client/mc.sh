#!/bin/bash
# Drives the RCMC dev client (macOS) by focusing the Minecraft window and
# synthesizing input. See SKILL.md in this directory for the full workflow.
#
#   mc.sh "/rcmc info"   type a chat command (chat opens with 't', Enter sends)
#   mc.sh --key "r"      press bare key(s) in-game (no chat)
#   mc.sh --shot         press F2 and print the newest screenshot's path
#
# Keystrokes land in whatever window has focus — warn the human to keep hands
# off the machine while this runs.
osascript -e 'tell application "System Events" to tell (first process whose name contains "java") to set frontmost to true' >/dev/null
sleep 0.6
if [ "$1" = "--shot" ]; then
  osascript -e 'tell application "System Events" to key code 120'
  sleep 1.2
  ls -t "$(git rev-parse --show-toplevel 2>/dev/null || pwd)"/run/screenshots/*.png 2>/dev/null | head -1
elif [ "$1" = "--key" ]; then
  osascript -e "tell application \"System Events\" to keystroke \"$2\""
else
  osascript -e 'tell application "System Events" to keystroke "t"'
  sleep 0.5
  # Character-by-character with a small delay: bulk `keystroke` of a whole
  # string drops characters against Minecraft's input polling, and a mangled
  # command half-typed into the WORLD becomes keybind presses (g toggles the
  # builder segment, r resets a session...). Learned the hard way.
  osascript - "$1" <<'APPLESCRIPT'
on run argv
  set cmd to item 1 of argv
  tell application "System Events"
    repeat with c in characters of cmd
      keystroke (c as string)
      delay 0.025
    end repeat
  end tell
end run
APPLESCRIPT
  sleep 0.3
  osascript -e 'tell application "System Events" to key code 36'
  sleep 0.5
fi
