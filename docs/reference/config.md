# Configuration

RCMC's config file is a standard Forge configuration under `config/`, split into two categories
that behave very differently.

!!! danger "The split is not cosmetic"

    **`physics` values change simulation *results*.** On a multiplayer server the **server's copy
    is authoritative** and is synced to clients on join. A client running different physics values
    would visibly desync from the train it is riding, because the client predicts a train's motion
    by running the same integrator against the same numbers.

    **`client` values are presentation only.** Never synced, safe to differ per player, and no
    concern of the server's.

Values are read into static fields at load time — never queried per tick.

## `physics`

| Key | Default | What it does |
| --- | --- | --- |
| `gravity` | `9.81` | Downward acceleration in blocks/s². Real gravity reads rather floaty at one block per metre, where a "tall" coaster is 40 blocks rather than 40 m of real steel. Tune here rather than in code |
| `rollingResistance` | `0.01` | Fraction of speed lost per second to rolling resistance and drivetrain friction, applied as exponential decay so it is timestep-independent |
| `airDrag` | `0.0015` | Quadratic air-drag coefficient. Dominates at the high end of a drop, and is what stops a well-designed circuit accumulating speed forever across laps |
| `physicsSubSteps` | `4` | Physics sub-steps per Minecraft tick. One 50 ms step is far too coarse at coaster speeds — a train doing 30 blocks/s covers 1.5 blocks per tick, enough to cut the corner on a tight helix. Costs integrator time only, never network traffic |
| `maxSpeed` | `60.0` | Hard ceiling on train speed in blocks/s. A safety valve, not a design target: beyond roughly this speed 1.12.2's entity movement and tracking produce artefacts no amount of interpolation hides |

### Tuning notes

- **Raising `gravity`** makes rides feel snappier and less floaty — often the first thing worth
  trying if drops feel weightless at Minecraft scale.
- **Lowering `airDrag`** lets a circuit keep more speed lap to lap. Lower it far enough and a
  well-shaped circuit will never settle.
- **`physicsSubSteps` is cheap.** If tight geometry feels like it is being cut, raise it before
  reaching for anything else.
- Every one of these feeds a **deterministic, pure** integrator with no randomness and no world
  lookups. That property is what makes client prediction exact — and it is why the values must
  match across a server and its clients.

## `client`

### Camera and HUD

| Key | Default | What it does |
| --- | --- | --- |
| `enableCameraRoll` | `true` | Whether the rider camera rolls with banked track and inversions |
| `enableRideHud` | `true` | The live speed / G-force / height readout while riding |

### G-force screen effects

| Key | Default | What it does |
| --- | --- | --- |
| `gForceSmoothingSeconds` | `1.0` | Time constant of the low-pass filter applied before effects react to a G reading. Larger is slower to ramp up *and* release, but more resistant to single-tick spikes |
| `enableGForceTint` | `true` | Whether sustained vertical G drives the grey-out / red-out tint |
| `grayOutThresholdG` | `4.5` | Sustained vertical G at which grey-out begins. Roughly where real pilots and coaster riders start greying out under sustained positive Gz |
| `grayOutRangeG` | `1.5` | G above the threshold over which grey-out ramps to full |
| `redOutThresholdG` | `-2.0` | Sustained vertical G at or below which red-out begins. Negative Gz pools blood toward the head at a smaller magnitude than positive Gz causes greying — hence the smaller number |
| `redOutRangeG` | `1.5` | G below the threshold over which red-out ramps to full |
| `gForceTintMaxAlpha` | `0.4` | Peak opacity of the tint at full ramp. Deliberately modest: a comfort cue, not an attempt to blind the rider |
| `enableGForceFovKick` | `true` | Whether sustained longitudinal G kicks the camera FOV |
| `fovKickDegreesPerG` | `2.0` | Degrees of FOV change per g of sustained longitudinal acceleration |
| `fovKickMaxDegrees` | `8.0` | Hard cap on the FOV kick in either direction |

!!! tip "Motion sensitivity"

    Set `enableCameraRoll = false` and `enableGForceTint = false` for a much calmer ride without
    changing anything about the simulation. Both are pure presentation, so they never affect what
    other players see or how the train behaves.
