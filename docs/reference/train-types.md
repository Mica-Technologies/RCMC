# Train types

A **train type** is a kind of train: which body it's drawn with, how its cars are proportioned
and seated, how many cars it runs, and its colours. RCMC has six built in, and a server can add its
own, or change the built-in ones, with JSON files. There's nothing to install on clients.

[`/rcmc trains`](commands.md#rcmc-trains) lists every type the server knows.

## Built-in types

| Id | Body | Based on | Cars (default / most) | Seats a car |
| --- | --- | --- | --- | --- |
| `coaster` | Sit-down: bucket seats, a lap bar per row | Modern steel coaster | 5 / 12 | 4 |
| `shoulder` | Tall seats, over-the-shoulder restraints | Looping coaster | 5 / 12 | 4 |
| `wooden` | High sides, a bench and one bar per row | Classic wooden coaster | 5 / 12 | 4 |
| `metrocompact` | Metro | NYC A-Division / R142, 15.65 m | 3 / 10 | 8 |
| `metro` | Metro | MBTA Orange Line, CRRC 65 ft class, ~19.8 m | 3 / 8 | 10 |
| `metrolong` | Metro | LA HR4000 / NYC 75-footers, ~22.9 m | 3 / 8 | 12 |

## Adding your own

Put one `.json` file per type in the server's `config/rcmc/trains/` folder, then run
`/rcmc trains reload` or restart. The first time the server starts, it writes every built-in type
into `config/rcmc/trains/examples/` to copy from. That folder isn't read itself.

```json
{
  "id": "orange_line",
  "name": "Orange Line",
  "body": "metro",
  "carLength": 14.3,
  "couplingGap": 6.2,
  "seatsPerCar": 10,
  "defaultCars": 6,
  "maxCars": 8,
  "colours": {
    "body": "steel",
    "trim": "orange",
    "seats": "blue"
  }
}
```

| Field | Meaning | Allowed |
| --- | --- | --- |
| `id` | What commands and saves call it. Use a built-in's id to replace that type | lower-case letters, digits and `_`, up to 32 |
| `name` | Shown on the operator panel and in `/rcmc trains` | any text |
| `body` | Which drawn car it uses | `sit_down`, `shoulder`, `wooden` (coasters) or `metro` |
| `carLength` | Blocks between a car's bogie centres | 1 – 40 |
| `couplingGap` | Blocks between one car and the next | 0 – 20 |
| `seatsPerCar` | Riders a car holds. Coaster cars seat two abreast | 1 – 40 |
| `defaultCars` | Cars in a new train | 1 – `maxCars` |
| `maxCars` | Most cars a train of this type can have | 1 – 12 |
| `colours` | `body`, `trim` and `seats` | `steel`, `graphite`, `white`, `red`, `orange`, `yellow`, `green`, `teal`, `blue`, `purple`, `pink`, `brown` |

Only `id` and `body` are required. Anything else left out takes the built-in `coaster` type's
value, or the `metro` type's for a metro body.

A file that can't be read, or asks for something outside these ranges, is skipped, and the reason
is logged and shown by `/rcmc trains reload`. The other files still load.

## Using them

- **Commands:** [`/rcmc train`](commands.md#rcmc-train) takes a type id:
  `/rcmc train 1 6 0 orange_line`.
- **Operator panel:** the **Car** button on a coaster's panel steps through every coaster type,
  custom ones included. The ride remembers the type by its id.
- **Existing trains:** a train already built keeps its cars, even if its type later changes or
  its file is removed.
