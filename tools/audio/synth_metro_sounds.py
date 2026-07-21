"""
Synthesises RCMC's metro sounds from scratch.

Nothing here samples, filters or otherwise derives from any third-party recording. The reference
clips supplied by the project owner were measured for *facts* only — a fundamental of 612 Hz with
odd harmonics, two dings 0.72 s apart, a door cycle of roughly 1.5 s, an air-release swell and
decay — and those numbers are reproduced here with generated waveforms. See the asset rule in
CLAUDE.md: reference for knowledge, recreate from scratch.
"""
import numpy as np
import struct

SR = 44100


def band_noise(n, lo, hi, seed):
    """White noise band-limited by zeroing an FFT outside [lo, hi]. numpy only, no scipy."""
    rng = np.random.default_rng(seed)
    x = rng.standard_normal(n)
    spec = np.fft.rfft(x)
    freqs = np.fft.rfftfreq(n, 1.0 / SR)
    # Soft shoulders, so the band does not ring like a resonator.
    gain = np.ones_like(freqs)
    gain[freqs < lo] = 0.0
    gain[freqs > hi] = 0.0
    skirt = (freqs >= lo) & (freqs < lo * 1.4)
    gain[skirt] = (freqs[skirt] - lo) / (lo * 0.4)
    skirt = (freqs <= hi) & (freqs > hi * 0.7)
    gain[skirt] = (hi - freqs[skirt]) / (hi * 0.3)
    return np.fft.irfft(spec * gain, n)


def ding(t, f0, decay, partials=((1, 1.0), (3, 0.34), (5, 0.12), (7, 0.04))):
    """One struck tone: odd harmonics over an exponential decay, as an electronic chime rings."""
    out = np.zeros_like(t)
    for mult, amp in partials:
        # Higher partials die faster, which is what stops it sounding like a synth pad.
        out += amp * np.sin(2 * np.pi * f0 * mult * t) * np.exp(-t / (decay / (1 + 0.35 * (mult - 1))))
    return out


def attack(t, ms=4.0):
    """A short raised-cosine attack; an instant edge clicks."""
    a = np.clip(t / (ms / 1000.0), 0.0, 1.0)
    return 0.5 - 0.5 * np.cos(np.pi * a)


def door_chime():
    """Two dings 0.72 s apart: 612 Hz fundamental with a 416 Hz partner about a fifth below."""
    total = 1.75
    n = int(SR * total)
    out = np.zeros(n)
    for start in (0.0, 0.72):
        i0 = int(start * SR)
        t = np.arange(n - i0) / SR
        voice = ding(t, 612.0, 0.35) + 0.55 * ding(t, 416.0, 0.39)
        out[i0:] += voice * attack(t)
    return out


def door_close():
    """
    The door cycle, timed to the mod's own 30-tick (1.5 s) close: motors take up, the leaves run,
    and the two halves meet with a soft latch near the end.
    """
    total = 1.6
    n = int(SR * total)
    t = np.arange(n) / SR

    # Mechanism rumble across the travel.
    run = band_noise(n, 180.0, 900.0, seed=11)
    run_env = np.clip((t - 0.05) / 0.12, 0.0, 1.0) * np.clip((1.15 - t) / 0.25, 0.0, 1.0)
    # A little wow, so it reads as machinery rather than as a noise gate.
    run_env *= 0.85 + 0.15 * np.sin(2 * np.pi * 5.5 * t)

    # Air/pneumatic hiss riding along with it.
    hiss = band_noise(n, 1200.0, 6000.0, seed=12)
    hiss_env = np.clip((t - 0.02) / 0.08, 0.0, 1.0) * np.exp(-np.maximum(t - 0.15, 0) / 0.55)

    # The latch: a low, short thump as the leaves meet.
    thunk = band_noise(n, 90.0, 420.0, seed=13)
    thunk_env = np.where(t >= 1.12, np.exp(-(t - 1.12) / 0.09), 0.0)

    return 0.55 * run * run_env + 0.16 * hiss * hiss_env + 0.75 * thunk * thunk_env


def brake_release():
    """Air dumping from the brake pipe: a fast swell, then a long fall as the reservoir empties."""
    total = 2.9
    n = int(SR * total)
    t = np.arange(n) / SR

    air = band_noise(n, 400.0, 7000.0, seed=21)
    # Swell to a peak at ~0.35 s, then a long exponential fall.
    env = np.where(t < 0.35, t / 0.35, np.exp(-(t - 0.35) / 0.85))
    # The hiss darkens as pressure drops — a second, lower band fading in behind it.
    low = band_noise(n, 120.0, 700.0, seed=22)
    low_env = np.clip(t / 0.6, 0.0, 1.0) * np.exp(-np.maximum(t - 0.4, 0) / 1.1)

    out = 0.7 * air * env + 0.35 * low * low_env
    # A final clunk as the shoes come off, well down in the mix.
    clunk = band_noise(n, 80.0, 350.0, seed=23)
    out += 0.5 * clunk * np.where(t >= 1.95, np.exp(-(t - 1.95) / 0.07), 0.0)
    return out


def write_wav(path, x, peak=0.89):
    x = np.asarray(x, dtype=np.float64)
    x = x / (np.abs(x).max() + 1e-12) * peak
    # 8 ms fades top and tail: any discontinuity at either end is an audible click.
    fade = int(SR * 0.008)
    if len(x) > 2 * fade:
        ramp = np.linspace(0.0, 1.0, fade)
        x[:fade] *= ramp
        x[-fade:] *= ramp[::-1]
    pcm = np.clip(x * 32767.0, -32768, 32767).astype('<i2')
    data = pcm.tobytes()
    with open(path, 'wb') as f:
        f.write(b'RIFF' + struct.pack('<I', 36 + len(data)) + b'WAVE')
        f.write(b'fmt ' + struct.pack('<IHHIIHH', 16, 1, 1, SR, SR * 2, 2, 16))
        f.write(b'data' + struct.pack('<I', len(data)) + data)
    print(f"wrote {path}  {len(x)/SR:.2f}s")


if __name__ == '__main__':
    import sys
    out = sys.argv[1].rstrip('/')
    write_wav(f"{out}/metro_door_chime.wav", door_chime())
    write_wav(f"{out}/metro_door_close.wav", door_close())
    write_wav(f"{out}/metro_brake_release.wav", brake_release())
