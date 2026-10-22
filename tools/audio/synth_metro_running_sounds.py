"""
Synthesises RCMC's metro RUNNING sounds from scratch: the traction motor and the wheels on rail.

Same rule as synth_metro_sounds.py: nothing here samples, filters or derives from a recording.

Both are LOOPS, built the same way as synth_coaster_sounds.py's:
  - noise is band-limited over the whole loop with an inverse FFT, so it is exactly periodic;
  - every partial and modulation completes a whole number of cycles in the loop length;
  - no fades, which would dip once a cycle.

The game re-pitches each loop with the train's speed (MetroAudioMix), so each is voiced at the
pitch it should have at a mid speed, about 12 blocks/s.

Usage:  py synth_metro_running_sounds.py <out-dir>      then encode with ffmpeg, mono Ogg Vorbis:
        for f in <out-dir>/metro_*.wav; do ffmpeg -y -i "$f" -ac 1 -c:a libvorbis -q:a 4 "${f%.wav}.ogg"; done
"""
import sys

import numpy as np

from synth_coaster_sounds import loop_noise, tone, write_loop
from synth_metro_sounds import SR


def motor():
    """VVVF traction whine: a tonal fundamental with harmonics, a switching carrier with sidebands
    either side of it, and a little hiss. Voiced at ~12 blocks/s."""
    seconds = 4.0
    n = int(SR * seconds)
    t = np.arange(n) / SR
    base = 420.0
    x = np.zeros(n)
    for k, a in ((1, 1.0), (2, 0.45), (3, 0.30), (4, 0.12), (6, 0.08)):
        x += a * tone(t, base * k, seconds)
    # The inverter's switching carrier and its sidebands: the "singing" part of a VVVF drive.
    carrier = 1260.0
    for off, a in ((0.0, 0.35), (-base, 0.18), (base, 0.18), (-2 * base, 0.07), (2 * base, 0.07)):
        x += a * tone(t, carrier + off, seconds)
    # A slow, shallow wobble (whole cycles) so it does not sound like a test tone.
    x *= 1.0 + 0.06 * tone(t, 1.5, seconds)
    noise = loop_noise(seconds, 300.0, 6000.0, seed=211)
    x += 0.25 * noise / (np.abs(noise).max() + 1e-12)
    return x


def rolling():
    """Steel wheels on steel rail under a car body: a deep rumble and a darker roar than a
    coaster's. Voiced at ~12 blocks/s."""
    seconds = 4.0
    n = int(SR * seconds)
    t = np.arange(n) / SR

    def norm(v):
        return v / (np.abs(v).max() + 1e-12)

    x = 1.0 * norm(loop_noise(seconds, 30.0, 180.0, seed=221)) \
        + 0.6 * norm(loop_noise(seconds, 180.0, 2200.0, seed=222)) \
        + 0.12 * norm(loop_noise(seconds, 2200.0, 7000.0, seed=223))
    x *= 1.0 + 0.15 * tone(t, 2.0, seconds)
    x += 0.2 * tone(t, 48.0, seconds)
    return x


if __name__ == '__main__':
    out = sys.argv[1].rstrip('/')
    write_loop(f"{out}/metro_motor.wav", motor())
    write_loop(f"{out}/metro_rolling.wav", rolling())
