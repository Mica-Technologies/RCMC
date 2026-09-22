"""
Synthesises RCMC's roller coaster sounds from scratch.

Same rule as synth_metro_sounds.py: nothing here samples, filters or derives from a recording. Every
sound is generated from noise and oscillators with numbers chosen for the job (a chain dog every
quarter-second at lift speed, a launch sweeping 180 -> 1100 Hz, and so on).

Five of the six are LOOPS, and a loop has one hard requirement: its last sample must run straight
into its first. So:

  - noise is band-limited in the frequency domain over the whole loop (numpy's inverse FFT of a
    shaped spectrum is exactly periodic in the loop length, so the noise wraps seamlessly);
  - every tone, modulation and rhythm completes a whole number of cycles in the loop;
  - loops are written WITHOUT the fade-in/out the one-shots get, which would dip every cycle.

The game varies each loop's pitch and volume with the train's speed, so each is voiced at the pitch
it should have at a representative speed (noted per sound), not at its lowest.

Usage:  python synth_coaster_sounds.py <out-dir>      then encode with ffmpeg, mono Ogg Vorbis:
        for f in <out-dir>/*.wav; do ffmpeg -y -i "$f" -ac 1 -c:a libvorbis -q:a 4 "${f%.wav}.ogg"; done
"""
import numpy as np
import struct
import sys

from synth_metro_sounds import SR, band_noise, write_wav


def loop_noise(seconds, lo, hi, seed):
    """Band-limited noise exactly `seconds` long, periodic in that length (see the module note)."""
    return band_noise(int(round(SR * seconds)), lo, hi, seed)


def whole_cycles(freq, seconds):
    """The frequency nearest `freq` that fits a whole number of cycles in `seconds`."""
    return max(1, round(freq * seconds)) / seconds


def tone(t, freq, seconds):
    return np.sin(2 * np.pi * whole_cycles(freq, seconds) * t)


def write_loop(path, x, peak=0.89):
    """Like write_wav, but with no fades: a loop's ends already meet."""
    x = np.asarray(x, dtype=np.float64)
    x = x / (np.abs(x).max() + 1e-12) * peak
    pcm = np.clip(x * 32767.0, -32768, 32767).astype('<i2')
    data = pcm.tobytes()
    with open(path, 'wb') as f:
        f.write(b'RIFF' + struct.pack('<I', 36 + len(data)) + b'WAVE')
        f.write(b'fmt ' + struct.pack('<IHHIIHH', 16, 1, 1, SR, SR * 2, 2, 16))
        f.write(b'data' + struct.pack('<I', len(data)) + data)
    print(f"wrote {path}  {len(x)/SR:.2f}s (loop)")


def roll():
    """Steel wheels on steel rail: a low rumble under a broad roar. Voiced for ~15 blocks/s."""
    seconds = 4.0
    n = int(SR * seconds)
    t = np.arange(n) / SR
    rumble = loop_noise(seconds, 40.0, 240.0, seed=101)
    roar = loop_noise(seconds, 240.0, 1600.0, seed=102)
    hiss = loop_noise(seconds, 1600.0, 5000.0, seed=103)
    # A slow swell so it is not a flat hash: 3 Hz, a whole number of cycles in the loop.
    swell = 1.0 + 0.18 * tone(t, 3.0, seconds)
    return swell * (1.0 * rumble + 0.42 * roar + 0.08 * hiss)


def chain():
    """
    A chain lift at 5 blocks/s: the anti-rollback dog dropping into the next tooth four times a
    second (a hard clack over a low clunk), the chain rattling through its guide, and the drive
    motor humming underneath.
    """
    seconds = 2.0
    n = int(SR * seconds)
    t = np.arange(n) / SR
    out = np.zeros(n)
    clack_noise = loop_noise(seconds, 900.0, 5200.0, seed=111)
    for k in range(8):                       # 4 per second x 2 s
        start = k * 0.25
        dt = t - start
        live = dt >= 0.0
        env_hi = np.where(live, np.exp(-dt / 0.018), 0.0)
        env_lo = np.where(live, np.exp(-dt / 0.045), 0.0)
        out += 0.9 * clack_noise * env_hi
        out += 0.6 * np.sin(2 * np.pi * 170.0 * dt) * env_lo
    # Chain links over the sprocket: 40 Hz flutter on a bright rattle.
    rattle = loop_noise(seconds, 1500.0, 6500.0, seed=112)
    out += 0.16 * rattle * (0.6 + 0.4 * tone(t, 40.0, seconds))
    # Motor: mains-ish fundamental and two harmonics.
    out += 0.10 * tone(t, 50.0, seconds) + 0.06 * tone(t, 100.0, seconds) + 0.03 * tone(t, 150.0, seconds)
    return out


def wind():
    """Air past a rider's ears. Voiced for ~20 blocks/s; faded out entirely when slow."""
    seconds = 3.0
    n = int(SR * seconds)
    t = np.arange(n) / SR
    body = loop_noise(seconds, 250.0, 2600.0, seed=121)
    edge = loop_noise(seconds, 2600.0, 8000.0, seed=122)
    gust = 1.0 + 0.25 * tone(t, 2.0, seconds) + 0.1 * tone(t, 5.0, seconds)
    return gust * (1.0 * body + 0.35 * edge)


def brake():
    """Fin brakes biting: a bright friction hiss, a low grind, and a faint squeal that wavers."""
    seconds = 2.0
    n = int(SR * seconds)
    t = np.arange(n) / SR
    hiss = loop_noise(seconds, 2000.0, 7500.0, seed=131)
    grind = loop_noise(seconds, 140.0, 650.0, seed=132)
    squeal_freq = whole_cycles(1250.0, seconds)
    # Vibrato by phase modulation; its rate is a whole number of cycles too.
    vibrato = 0.0035 * np.sin(2 * np.pi * whole_cycles(3.0, seconds) * t) / (2 * np.pi * 3.0 / squeal_freq)
    squeal = np.sin(2 * np.pi * squeal_freq * (t + vibrato))
    return 1.0 * hiss + 0.55 * grind + 0.12 * squeal


def tyres():
    """Drive tyres: rubber whirring on the fin, each tyre's rotation a 14 Hz pulse. At ~6 blocks/s."""
    seconds = 2.0
    n = int(SR * seconds)
    t = np.arange(n) / SR
    whir = loop_noise(seconds, 180.0, 1300.0, seed=141)
    pulse = 0.55 + 0.45 * np.clip(tone(t, 14.0, seconds), 0.0, None)
    motor = 0.25 * tone(t, 120.0, seconds) + 0.12 * tone(t, 240.0, seconds)
    return pulse * whir + motor


def launch():
    """
    A linear-motor launch, one shot: a thump as the motors engage, then a whine sweeping up from
    180 Hz to 1100 Hz with a rising rush of air, fading as the train leaves the launch.
    """
    seconds = 3.4
    n = int(SR * seconds)
    t = np.arange(n) / SR
    progress = np.clip(t / 2.8, 0.0, 1.0)
    freq = 180.0 * (1100.0 / 180.0) ** progress              # exponential sweep
    phase = 2 * np.pi * np.cumsum(freq) / SR
    whine = np.sin(phase) + 0.35 * np.sin(3 * phase) + 0.12 * np.sin(5 * phase)
    rush = band_noise(n, 500.0, 4500.0, seed=151)
    env = np.clip(t / 0.12, 0.0, 1.0) * np.where(t < 2.8, 1.0, np.exp(-(t - 2.8) / 0.22))
    thump = np.sin(2 * np.pi * 55.0 * t) * np.exp(-t / 0.09)
    return env * (0.55 * whine + 0.45 * rush * (0.3 + 0.7 * progress)) + 0.8 * thump


if __name__ == '__main__':
    out = sys.argv[1].rstrip('/')
    write_loop(f"{out}/coaster_roll.wav", roll())
    write_loop(f"{out}/coaster_chain.wav", chain())
    write_loop(f"{out}/coaster_wind.wav", wind())
    write_loop(f"{out}/coaster_brake.wav", brake())
    write_loop(f"{out}/coaster_tyres.wav", tyres())
    write_wav(f"{out}/coaster_launch.wav", launch())
