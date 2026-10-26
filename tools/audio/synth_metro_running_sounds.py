"""
Synthesises RCMC's metro RUNNING sounds from scratch: the traction motor, the wheels on rail, and
the clack of a wheel crossing a rail joint.

Same rule as synth_metro_sounds.py: nothing here samples, filters or derives from a recording.

The motor and rolling sounds are LOOPS, built the same way as synth_coaster_sounds.py's:
  - noise is band-limited over the whole loop with an inverse FFT, so it is exactly periodic;
  - every partial and modulation completes a whole number of cycles in the loop length;
  - no fades, which would dip once a cycle.

The game re-pitches each loop with the train's speed (MetroAudioMix), so each is voiced at the
pitch it should have at a mid speed, about 12 blocks/s. Both are voiced low: the first version had
the motor's fundamental at 420 Hz and its switching carrier at 1260 Hz, which read as a whine
rather than a motor.

The joint clacks are ONE-SHOTS, one per axle per joint; the game times them from where each axle
is, so the rhythm (two axles of a bogie close together, then a gap to the next bogie) comes from
the train's own geometry and speed, not from anything baked in here. Three variants, so a long run
of them does not sound like a sample repeating.

Usage:  py synth_metro_running_sounds.py <out-dir>      then encode with ffmpeg, mono Ogg Vorbis:
        for f in <out-dir>/metro_*.wav; do ffmpeg -y -i "$f" -ac 1 -c:a libvorbis -q:a 4 "${f%.wav}.ogg"; done
"""
import sys

import numpy as np

from synth_coaster_sounds import loop_noise, tone, write_loop
from synth_metro_sounds import SR, band_noise, write_wav


def motor():
    """VVVF traction growl: a tonal fundamental with harmonics, a switching carrier with sidebands
    either side of it, and a little hiss. Voiced at ~12 blocks/s."""
    seconds = 4.0
    n = int(SR * seconds)
    t = np.arange(n) / SR
    base = 230.0
    x = np.zeros(n)
    for k, a in ((1, 1.0), (2, 0.5), (3, 0.28), (4, 0.10), (6, 0.04)):
        x += a * tone(t, base * k, seconds)
    # The inverter's switching carrier and its sidebands: the "singing" part of a VVVF drive, kept
    # well under the fundamental so it colours the sound rather than leading it.
    carrier = 700.0
    for off, a in ((0.0, 0.22), (-base, 0.11), (base, 0.11), (-2 * base, 0.04), (2 * base, 0.04)):
        x += a * tone(t, carrier + off, seconds)
    # A slow, shallow wobble (whole cycles) so it does not sound like a test tone.
    x *= 1.0 + 0.06 * tone(t, 1.5, seconds)
    noise = loop_noise(seconds, 150.0, 3000.0, seed=211)
    x += 0.2 * noise / (np.abs(noise).max() + 1e-12)
    return x


def rolling():
    """Steel wheels on steel rail under a car body: a deep rumble and a dark roar. Voiced at
    ~12 blocks/s."""
    seconds = 4.0
    n = int(SR * seconds)
    t = np.arange(n) / SR

    def norm(v):
        return v / (np.abs(v).max() + 1e-12)

    x = 1.0 * norm(loop_noise(seconds, 25.0, 150.0, seed=221)) \
        + 0.5 * norm(loop_noise(seconds, 150.0, 1400.0, seed=222)) \
        + 0.05 * norm(loop_noise(seconds, 1400.0, 4500.0, seed=223))
    x *= 1.0 + 0.15 * tone(t, 2.0, seconds)
    x += 0.25 * tone(t, 40.0, seconds)
    return x


def joint(seed, thump_hz, ring_hz):
    """A wheel dropping across a rail joint: a heavy low thump, the knock of steel on steel, and a
    short, dull ring from the rail. Low and short, so a run of them is a rhythm, not a rattle."""
    seconds = 0.32
    lead = 0.009   # silence for write_wav's fade-in to eat, so the transient itself stays sharp
    n = int(SR * seconds)
    t = np.arange(n) / SR
    s = np.clip(t - lead, 0.0, None)
    on = (t >= lead).astype(float)
    # The thump: a low sine whose pitch sags as it decays, as a heavy blow does.
    phase = 2 * np.pi * (thump_hz * s - 18.0 * s * s)
    x = 1.0 * np.sin(phase) * np.exp(-s / 0.045) * on
    # The knock: a burst of mid noise, very short.
    knock = band_noise(n, 180.0, 1600.0, seed=seed)
    x += 0.55 * knock / (np.abs(knock).max() + 1e-12) * np.exp(-s / 0.012) * on
    # The rail's ring: two inharmonic partials, quiet and quick.
    for mult, amp in ((1.0, 0.12), (1.53, 0.06)):
        x += amp * np.sin(2 * np.pi * ring_hz * mult * s) * np.exp(-s / 0.05) * on
    return x


if __name__ == '__main__':
    out = sys.argv[1].rstrip('/')
    write_loop(f"{out}/metro_motor.wav", motor())
    write_loop(f"{out}/metro_rolling.wav", rolling())
    for i, (seed, thump, ring) in enumerate(((231, 92.0, 820.0), (232, 86.0, 760.0), (233, 98.0, 880.0))):
        write_wav(f"{out}/metro_joint_{i + 1}.wav", joint(seed, thump, ring))
