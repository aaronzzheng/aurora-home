# Makes each theme's interface sounds: ui_click_<theme>, ui_select_<theme>, ui_chime_<theme>.
#   python3 sounds.py ../res/raw [theme ...]      (needs numpy; names limit which sets are written)
import sys, wave, numpy as np
R = 44100
OUT = sys.argv[1]
rng = np.random.default_rng(7)

def T(d): return np.arange(int(R * d)) / R
def env(d, a=0.002, k=8.0):
    t = T(d); e = np.exp(-t * k)
    na = max(1, int(R * a)); e[:na] *= np.linspace(0, 1, na)
    return e
def sine(f, d, ph=0): return np.sin(2 * np.pi * f * T(d) + ph)
def glide(f0, f1, d, curve=1.0):
    t = T(d); f = f0 + (f1 - f0) * (t / d) ** curve
    return np.sin(2 * np.pi * np.cumsum(f) / R)
def lowpass(x, fc):
    a = np.exp(-2 * np.pi * fc / R); y = np.zeros_like(x); s = 0.0
    for i, v in enumerate(x): s = (1 - a) * v + a * s; y[i] = s
    return y
def highpass(x, fc): return x - lowpass(x, fc)
def noise(d): return rng.standard_normal(int(R * d))
def mix(*parts):
    n = max(len(p) for p in parts); out = np.zeros(n)
    for p in parts: out[:len(p)] += p
    return out
def at(x, sec, total):
    out = np.zeros(int(R * total)); i = int(R * sec); n = min(len(x), len(out) - i); out[i:i + n] += x[:n]; return out
def reverb(x, length=1.2, mix_=0.25, damp=3000, decay=4.0):
    ir = noise(length) * np.exp(-T(length) * decay); ir = lowpass(ir, damp); ir /= np.sqrt(np.sum(ir ** 2))
    n = len(x) + len(ir); N = 1 << int(np.ceil(np.log2(n)))
    wet = np.fft.irfft(np.fft.rfft(x, N) * np.fft.rfft(ir, N), N)[:n]
    return np.concatenate([x, np.zeros(len(ir))]) * (1 - mix_) + wet * mix_ * 0.6
def fade_end(x, ms=30):
    n = min(int(R * ms / 1000), len(x) // 4); x = x.copy(); x[-n:] *= np.linspace(1, 0, n); return x
def save(name, x, peak=0.8):
    x = x / (np.max(np.abs(x)) + 1e-9) * peak
    loud = np.nonzero(np.abs(x) > 0.002)[0]   # drop the silent tail
    x = fade_end(x[:loud[-1] + int(R * .02)] if len(loud) else x)
    w = wave.open(f"{OUT}/{name}.wav", "wb"); w.setnchannels(1); w.setsampwidth(2); w.setframerate(R)
    w.writeframes((np.clip(x, -1, 1) * 32767).astype("<i2").tobytes()); w.close()

# instruments
def note(f): return 440 * 2 ** ((f - 69) / 12)
def bell(f, d=1.5, k=3.0, parts=((1, 1, 1), (2.76, .4, 1.8), (5.4, .2, 3), (8.93, .1, 4.5))):
    return sum(a * sine(f * m, d) * env(d, .001, k * kk) for m, a, kk in parts)
def piano(f, d=1.6):
    x = sum((0.6 / n ** 1.1) * sine(f * n * (1 + 0.0004 * n * n), d) * env(d, .003, 2.2 + n * 0.9) for n in range(1, 8))
    hammer = 0.06 * lowpass(noise(0.03), 2500) * env(0.03, .0005, 160)
    return mix(x, hammer)
def rhodes(f, d=1.4):
    t = T(d); trem = 1 + 0.12 * np.sin(2 * np.pi * 5 * t)
    body = sine(f, d) * env(d, .004, 2.2) + 0.25 * sine(2 * f, d) * env(d, .002, 4)
    tine = 0.35 * sine(f * 7.1, d) * env(d, .001, 30)
    return (body + tine) * trem
def marimba(f, d=0.6):
    return sine(f, d) * env(d, .001, 9) + 0.35 * sine(f * 3.93, d) * env(d, .001, 28) + 0.12 * sine(f * 9.2, d) * env(d, .0005, 60)
def xylo(f, d=0.5):
    return sine(f, d) * env(d, .0005, 12) + 0.5 * sine(f * 3, d) * env(d, .0005, 30) + 0.2 * sine(f * 6.2, d) * env(d, .0003, 55)
def musicbox(f, d=1.2):
    return sine(f, d) * env(d, .0008, 4.5) + 0.3 * sine(f * 2, d) * env(d, .0005, 9) + 0.18 * sine(f * 5.04, d) * env(d, .0003, 18)
def glass(f, d=1.6):
    return bell(f, d, 2.2, ((1, 1, 1), (2.32, .45, 1.4), (4.25, .25, 2.2), (6.63, .12, 3.5)))
def pad_chord(fs, d=2.6, attack=0.5):
    t = T(d); e = np.minimum(1, t / attack) * np.exp(-np.maximum(0, t - attack) * 1.4)
    x = sum(sine(f * dt, d) for f in fs for dt in (0.997, 1.0, 1.004)); return x * e
def knock(f, d=0.12, k=55):
    return sine(f, d) * env(d, .0005, k) + 0.5 * sine(f * 2.3, d) * env(d, .0003, k * 1.8) + 0.25 * lowpass(noise(d), 3000) * env(d, .0003, 300)

def crackle(d, n):
    """Embers: a few tiny dry pops scattered through `d` seconds."""
    out = np.zeros(int(R * d))
    for _ in range(n):
        k = lowpass(highpass(noise(.006), 800), 5000) * env(.006, .0001, 900) * (0.2 + 0.8 * rng.random())
        i0 = int(rng.random() * d * 0.92 * R); m = min(len(k), len(out) - i0); out[i0:i0 + m] += k[:m]
    return out
def switch(d=.03, body=380, bright=1.0):
    """A mechanical key switch: the click leaf plus the keycap bottoming out."""
    leaf = lowpass(lowpass(highpass(noise(d), 2200), 6000), 6000) * env(d, .0002, 520) * .55 * bright
    thock = sine(body, d) * env(d, .0006, 140) * .8 + sine(body * 2.7, d) * env(d, .0004, 260) * .25
    return leaf + thock
def square(f, d, fc=2400): return lowpass(lowpass(np.sign(sine(f, d)), fc), fc)   # two poles: no fizz

sets = {}

# Retro: a rotary tuner clicking into its detent, a piano key going down with a
# burst of channel static, and the set warming up with a soft thoom and a station chime.
c = mix(lowpass(noise(.03), 2600) * env(.03, .0004, 180) * .5, sine(130, .03) * env(.03, .001, 90) * .8,
        0.22 * sine(2100, .03) * env(.03, .0003, 260))
key = mix(sine(95, .14) * env(.14, .002, 30), 0.5 * lowpass(noise(.06), 1400) * env(.06, .0005, 90),
          0.3 * sine(620, .06) * env(.06, .0005, 70))
s = mix(key, at(highpass(lowpass(noise(.12), 6000), 500) * env(.12, .002, 18) * .35, .045, .2))
tt = T(1.6)
hum = (sine(60, 1.6) + 0.5 * sine(120, 1.6)) * np.minimum(1, tt / 0.08) * np.exp(-tt * 2.2)
ch = mix(hum * .6, lowpass(noise(.5), 300) * env(.5, .01, 6) * 1.2,
         at(sum(at(rhodes(note(n), 1.6), i * .12, 2.2) for i, n in enumerate([67, 72, 76])), .45, 2.4) * .6)
sets["retro"] = (c, reverb(s, .4, .12, 3000), reverb(ch, 1.2, .25, 3000))

# Classic: a 90s mouse button (press, then release), a bright bell "ding",
# and a start-up swell: a slow pad that blooms under a few rising bell notes.
press = lowpass(lowpass(highpass(noise(.012), 1800), 6500), 6500) * env(.012, .0002, 520) + sine(3100, .012) * env(.012, .0002, 380) * .5
release = lowpass(lowpass(highpass(noise(.01), 2400), 6500), 6500) * env(.01, .0002, 700) * .55
c = mix(press + lowpass(sine(240, .012), 900) * env(.012, .0005, 300) * .5, at(release, .028, .04))
s = mix(bell(note(88), .9, 3.2, ((1, 1, 1), (2.0, .35, 1.6), (2.76, .25, 2.4), (5.4, .12, 4))),
        0.5 * bell(note(88) * 1.004, .9, 3.4))
pad = pad_chord([note(n) for n in (53, 60, 64, 67, 72)], 3.2, 1.0)
ch = mix(lowpass(pad, 3000) * .55, *[at(bell(note(n), 1.6, 2.4) * .5, .45 + i * .26, 3.2) for i, n in enumerate([72, 76, 79, 84])])
sets["classic"] = (c, reverb(s, .7, .18, 5000), reverb(ch, 2.0, .35, 4200, 2.2))

# Art: soft felt piano in a gallery hall
c = sine(note(69), .06) * env(.06, .002, 60) * 0.8 + lowpass(noise(.06), 900) * env(.06, .001, 120) * 0.3
s = piano(note(76), .9)
ch = sum(at(piano(note(n), 2.2), i * .16, 2.8) for i, n in enumerate([60, 64, 67, 72]))
sets["art"] = (c, reverb(s, 1.5, .3, 3500, 3), reverb(ch, 2.0, .35, 3500, 2.5))

# Space: a guarded toggle switch snapping over, a backlit button's two-tone confirm,
# and the Quindar tone that opens a capsule call over a slow pad.
snap = lambda d: lowpass(lowpass(highpass(noise(d), 2500), 6000), 6000) * env(d, .0002, 600)
c = mix(snap(.02) * .7 + sine(240, .02) * env(.02, .0005, 200) * .5, at(snap(.015) * .45, .012, .035))
s = mix(sine(1046.5, .09) * env(.09, .003, 30), at(sine(1568, .16) * env(.16, .003, 16), .07, .25),
        0.15 * lowpass(noise(.01), 3000) * env(.01, .0003, 500))
qt = T(.25)
quindar = sine(2525, .25) * np.minimum(1, qt / .005) * np.minimum(1, (.25 - qt) / .005)
ch = mix(pad_chord([note(n) for n in (50, 57, 62, 64, 69)], 3.0, .9) * .5, at(quindar * .15, .1, 3.0),
         at(glass(note(81), 1.4) * .35, 1.0, 3.0), at(glass(note(88), 1.4) * .25, 1.35, 3.0))
sets["space"] = (c, reverb(s, .9, .3, 5000, 3), reverb(lowpass(ch, 6000), 2.5, .4, 5000, 1.8))

# Cozy: a soft knock on wood with an ember popping, a warm kalimba pluck,
# and a little music-box tune over the crackle of the fire.
kal = lambda f, d=1.0: sine(f, d) * env(d, .001, 5) + 0.3 * sine(f * 5.4, d) * env(d, .0005, 40) + 0.15 * sine(f * 2.01, d) * env(d, .001, 9)
c = mix(knock(520, .07, 60), at(crackle(.05, 2) * .25, .01, .07))
s = mix(kal(note(64), .9), at(kal(note(71), .9) * .6, .06, 1.0))
ch = mix(*[at(musicbox(note(n), 1.4), .15 + i * .22, 3.0) for i, n in enumerate([76, 79, 84, 79, 88])], crackle(3.0, 26) * .18)
sets["cozy"] = (c, reverb(s, .8, .22, 3000), reverb(ch, 1.4, .28, 4000))

# Minimal: a felt-soft tick, a restrained confirmation, one warm pure interval.
# Keeping the energy below 4 kHz makes repeated D-pad moves restful on TV speakers.
c = lowpass(highpass(noise(.018), 900), 3600) * env(.018, .0015, 280)
s = mix(sine(note(76), .16) * env(.16, .003, 22), 0.16 * sine(note(83), .16) * env(.16, .003, 30))
ch = sine(note(69), 1.8) * env(1.8, .012, 2.4) + 0.12 * sine(note(76), 1.8) * env(1.8, .012, 3.4)
sets["minimal"] = (c * 0.46, s * 0.7, reverb(ch, 1.2, .18, 3200))

# Light: cloudy glass rather than a sharp crystal.  The click is deliberately low and brief.
c = lowpass(glass(1568, .07), 4200) * env(.07, .001, 65)
s = lowpass(glass(note(79), .75), 5200)
ch = sum(at(lowpass(glass(note(n), 1.6), 5200), i * .11, 2.3) for i, n in enumerate([76, 79, 83, 86]))
sets["light"] = (c * .68, reverb(s, .8, .2, 4800), reverb(ch, 1.2, .24, 4800))

for k, (c, s, ch) in sets.items():
    if len(sys.argv) > 2 and k not in sys.argv[2:]: continue
    save(f"ui_click_{k}", c, 0.75)
    save(f"ui_select_{k}", s, 0.8)
    save(f"ui_chime_{k}", ch, 0.75)
    print(k)
