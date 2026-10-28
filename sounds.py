"""Synthesize Aurora's interface sounds into res/raw (16-bit mono WAV).

Aiming for "satisfying": short, warm and tactile rather than beepy.
  ui_click  - a soft wooden tick with a tiny felt thump, for moving focus
  ui_select - a round bubble "pop" (quick downward pitch bend), for opening/confirming
  ui_chime  - three soft bell notes (E major) with a gentle room tail, for wake-up

    venv/bin/python sounds.py
"""
import wave
from pathlib import Path

import numpy as np

RATE = 44100
OUT = Path(__file__).parent / "res" / "raw"
rng = np.random.default_rng(3)


def t_axis(dur):
    return np.arange(int(RATE * dur)) / RATE


def env(t, attack, decay):
    return np.clip(t / attack, 0, 1) * np.exp(-t / decay)


def band_noise(n, lo, hi):
    spec = np.fft.rfft(rng.standard_normal(n))
    f = np.fft.rfftfreq(n, 1 / RATE)
    spec[(f < lo) | (f > hi)] = 0
    out = np.fft.irfft(spec, n)
    return out / (np.max(np.abs(out)) + 1e-9)


def room(s, length=0.7, mix=0.18):
    """Very small, soft reverb: convolve with a decaying, darkened noise tail."""
    n = int(RATE * length)
    tail = band_noise(n, 200, 5000) * np.exp(-np.arange(n) / RATE / (length / 5))
    wet = np.convolve(s, tail)[: len(s) + n]
    wet = np.pad(wet, (0, len(s) + n - len(wet)))
    dry = np.concatenate([s, np.zeros(n)])
    return dry + mix * wet / (np.max(np.abs(wet)) + 1e-9) * np.max(np.abs(s))


def save(name, s, peak):
    s = s / np.max(np.abs(s)) * peak
    fade = min(len(s), int(RATE * 0.008))
    s[-fade:] *= np.linspace(1, 0, fade)
    with wave.open(str(OUT / f"{name}.wav"), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes((s * 32767).astype("<i2").tobytes())


# --- click: wooden tick = short resonant body + tiny bright transient + felt thump
t = t_axis(0.045)
body = np.sin(2 * np.pi * 1550 * t) * env(t, 0.0003, 0.0075)
body += 0.35 * np.sin(2 * np.pi * 2730 * t) * env(t, 0.0002, 0.004)
transient = band_noise(len(t), 2500, 7000) * env(t, 0.0001, 0.0015) * 0.35
thump = np.sin(2 * np.pi * 190 * t) * env(t, 0.0005, 0.007) * 0.45
save("ui_click", body + transient + thump, 0.32)

# --- select: bubble pop = sine whose pitch drops fast, plus a soft attack transient
t = t_axis(0.11)
freq = 380 + 620 * np.exp(-t / 0.018)              # ~1000 Hz falling to ~380 Hz
phase = 2 * np.pi * np.cumsum(freq) / RATE
pop = np.sin(phase) * env(t, 0.0008, 0.032)
pop += 0.18 * np.sin(2 * phase) * env(t, 0.0008, 0.02)
pop += band_noise(len(t), 1500, 6000) * env(t, 0.0001, 0.002) * 0.25
save("ui_select", pop, 0.5)

# --- chime: E5, G#5, B5 rolled like a soft harp/bell, with a little room
bell = ((1, 1.0), (2.0, 0.28), (3.0, 0.08), (4.16, 0.04))
dur = 1.9
chime = np.zeros(int(RATE * (dur + 0.3)))
for k, f0 in enumerate((659.25, 830.61, 987.77)):
    t = t_axis(dur)
    note = sum(a * np.sin(2 * np.pi * f0 * h * t) * env(t, 0.003, 0.55 / h ** 0.5) for h, a in bell)
    start = int(RATE * 0.11 * k)
    chime[start:start + len(note)] += note * (1.0 - 0.12 * k)
save("ui_chime", room(chime), 0.5)
