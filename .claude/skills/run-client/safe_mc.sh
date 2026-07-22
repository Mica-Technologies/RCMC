#!/bin/bash
# Guarded wrapper around mc.sh: only sends keystrokes when a Minecraft java window
# actually exists and can be focused. Otherwise aborts WITHOUT sending anything, so
# keystrokes can never leak into other apps when the client isn't running.
DIR="/Users/ahawk/GitProjects/RCMC/.claude/skills/run-client"
WIN=$(osascript -e 'tell application "System Events"
  set n to 0
  repeat with p in (every process whose name is "java")
    set n to n + (count of windows of p)
  end repeat
  return n
end tell' 2>/dev/null || echo 0)
if [ "${WIN:-0}" -lt 1 ]; then
  echo "GUARD-ABORT: no Minecraft window present; refusing to send keystrokes"
  exit 3
fi
osascript -e 'tell application "System Events" to set frontmost of (first process whose name is "java") to true' 2>/dev/null
sleep 0.4
exec "$DIR/mc.sh" "$@"
