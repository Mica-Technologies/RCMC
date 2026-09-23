# Troubleshooting

## Coasters

| What you see | What it means | What to do |
| --- | --- | --- |
| A train stops out on the course and stays there | It didn't have the energy to get over something: a hill too high, or a lift too low. The game calls this **valleying** | Lower the hill or raise the lift. `/rcmc check <id>` shows where it stalls |
| The ride stopped with *"trains collided — emergency stop"* | Two trains on the ride ran into each other | Remove a train at the operator panel, reset the emergency stop, and open the ride. To run several trains, add [block sections](../guide/operating-a-coaster.md#running-more-than-one-train) |
| The ride stopped with *"a train rolled back on the lift"* | A train couldn't get over something after the lift, rolled back over the crest, and the lift's anti-rollback caught it | Reset the emergency stop to send it up again. If it fails the same hill every time, give it more height: a taller lift or a lower hill |
| I can't board | The ride is closed, testing or stopped, or the train isn't stopped in its station | Open the ride from the operator panel, and board in the station |
| I can't get off | You can only get off in the station | Wait for the train to come back. On a closed or emergency-stopped ride, you can get off wherever it stopped |
| The train never leaves the station | The ride is on **Manual**, or **Closed** | Press **DISPATCH** or switch to **Auto** on the operator panel, or **Open** the ride |
| The ride check or rating flags my ride | Riders would feel too much somewhere, or the train can't get round | Hold the Track Editor to see the problem marked on the track, then fix it. See [the common fixes](../start/first-coaster.md#step-6-check-it-and-fix-it) |
| My last click finished the track early | Right-clicking air finishes a section, and a block out of reach counts as air | `/rcmc undo`, then lay it again, staying within reach of where you click |

## Metro

| What you see | What it means | What to do |
| --- | --- | --- |
| Two trains stand nose to nose and never move | They are running **at each other** on one track; `/rcmc line trains` shows them as HEAD-ON | `/rcmc line stop` one of them, and restart it facing the other way |
| A train waits out on the line for no reason | It is stopping for a train ahead; `/rcmc line trains` says which. Often a train left parked on the track | `/rcmc train remove <id>` the parked train, or put it into service |
| A train stopped and never moved again | It was taken out of service, or it stalled | `/rcmc line start <line> <trainId>` puts it back into service and recovers a stall |
| The doors don't open, or open on the wrong side | The doors open where there is a platform at car-floor height | Build a platform with `/rcmc platform`, or set the side by hand with `/rcmc station doors <name> <left\|right\|both\|auto>` |
| A command says there's no station or line by that name | Names are one word in commands, and a name with a space can't be typed | Rename it without spaces (`CentralPark`), using the tool |
| Trains crawl round part of the line | They slow for tight curves | Widen the curve. `/rcmc line check` lists them |
| An arrival board shows only one direction | Only one track at that station is a platform so far | Add a platform on the other track: see [island platforms](../guide/building-a-metro.md#island-platforms-one-station-two-berths) |
| A board shows stops instead of minutes | The line hasn't timed its trips yet | Wait a lap: boards learn how long each leg takes |
| A service didn't come back after a restart, but its train did | The line was deleted or renamed, or the train can't reach any of its stations now | `/rcmc line start <name> <trainId>` |

## Everywhere

| What you see | What it means | What to do |
| --- | --- | --- |
| `/rcmc` says I don't have permission | The command needs operator permission | Turn on cheats in singleplayer, or be an operator on a server |
| A train didn't come back after a restart | Its track was deleted while the world was closed | Rebuild the section, then `/rcmc train` |
| I can't break the track | Track isn't made of blocks | Use `/rcmc rmsection <id>`, or sneak-right-click it with the Track Editor |
| I deleted something by mistake | | `/rcmc undo` and `/rcmc redo` step back and forth through track edits |
| I can't join the Alto server with Alto_EXPERIMENTAL | RCMC must be on both the server and your game, and the Alto server doesn't run it yet | Use RCMC in singleplayer for now |

## Questions

**Does RCMC need any other mods?**
No. City Super Mod is used for spoken announcements if it's there; otherwise they're subtitles.

**Can I use it on a server?**
Yes: install the same jar on the server and every player's game. Physics runs on the server and every
player's game predicts it, so trains are smooth even at speed.

**Does a big park slow the game down?**
It's built to cost very little. A train's physics is one number moved along its track, so running
trains is cheap, and an empty server does no work for them at all. Track is drawn from a cached model,
rebuilt only when you edit it, and track more than 256 blocks away isn't drawn. Very large parks do draw
a lot of track up close, since lower detail at a distance isn't in yet. A full performance test of big
parks is still to come, so reports of slowdowns are welcome.

**Why only Minecraft 1.12.2?**
It's a deliberate choice: one version, done properly. RCMC does unusual things (its own track rendering,
its own train networking, a rolling camera) that wouldn't survive being spread across versions.

**Why can't I break track with a pickaxe?**
Track is a smooth curve stored with the world, not a line of blocks. That's what lets a train keep
running in unloaded chunks, and keeps a 500-block circuit from being 500 blocks. Supports, platforms,
gates and signs *are* ordinary blocks.

## Known gaps

These are known and listed so they read as gaps, not bugs:

- **The rating formulas aren't calibrated** against how rides feel to people yet.
- **Holds aren't saved.** A train held at the Line Control Desk is released by a restart.
- **A resumed metro service restarts its stop.** A train saved with its doors open reopens them after
  a reload, and serves a full dwell.
- **In multiplayer, another player walking inside a moving car** appears standing where they boarded.
- **No door markers on platforms.** The platform knows where a train stops, but nothing marks where each
  door will be.
- **One metro livery.** Metro trains are one design, repainted with `/rcmc paint`.
- **No distance detail for track.** Everything within 256 blocks is drawn in full.

## Reporting a problem

Please report bugs and ideas on [GitHub](https://github.com/Mica-Technologies/RCMC/issues). The most
useful reports say what you did, what you expected, what happened instead, and include a screenshot and
your `latest.log` if something crashed.
