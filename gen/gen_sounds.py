#!/usr/bin/env python3
"""Synthesises every Frontline sound (gunshots, reload clicks, hit markers) from noise bursts, sweeps and simple
reverb, then encodes them to OGG with ffmpeg and writes sounds.json. Run from the repo root: python3 gen/gen_sounds.py
"""
import json
import os
import subprocess
import tempfile

import numpy as np

SR = 44100
ROOT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'frontline')
OUT = os.path.join(ROOT, 'sounds')
rng = np.random.default_rng(5)


def t(n):
    return np.arange(n) / SR


def env(n, attack, decay, curve=1.0):
    x = np.arange(n) / SR
    e = np.where(x < attack, x / max(attack, 1e-5), np.exp(-(x - attack) / decay))
    return e ** curve


def lowpass(x, cutoff):
    # one-pole lowpass, run twice for a steeper slope
    rc = 1.0 / (2 * np.pi * cutoff)
    a = (1 / SR) / (rc + 1 / SR)
    y = np.zeros_like(x)
    acc = 0.0
    for i in range(len(x)):
        acc += a * (x[i] - acc)
        y[i] = acc
    return y


def highpass(x, cutoff):
    return x - lowpass(x, cutoff)


def noise(n):
    return rng.standard_normal(n)


def sweep(n, f0, f1, decay):
    x = t(n)
    f = f0 + (f1 - f0) * (1 - np.exp(-x / decay))
    phase = np.cumsum(2 * np.pi * f / SR)
    return np.sin(phase)


def reverb(x, delays_ms, gains, tail=0.0):
    out = np.copy(x)
    if tail > 0:
        out = np.concatenate([out, np.zeros(int(SR * tail))])
    for d, g in zip(delays_ms, gains):
        k = int(SR * d / 1000)
        y = np.zeros_like(out)
        y[k:] = out[:-k] * g
        out = out + lowpass(y, 2500)
    return out


def norm(x, peak=0.95):
    m = np.max(np.abs(x)) or 1.0
    return x / m * peak


def shot(crack=0.9, body=1.0, boom=0.8, length=0.55, bright=6000, boom_f=(140, 45), tail=0.25, echo=0.35):
    n = int(SR * length)
    # the crack: a very short bright noise transient
    c = highpass(noise(n), 1800) * env(n, 0.0005, 0.012) * crack
    # the body: mid noise with a fast decay
    b = lowpass(noise(n), bright) * env(n, 0.001, 0.045) * body
    # the boom: a pitched sweep from the chamber pressure wave
    bm = sweep(n, boom_f[0], boom_f[1], 0.06) * env(n, 0.002, 0.11, 1.2) * boom
    x = c + b + bm
    x = np.tanh(x * 1.6)
    x = reverb(x, [37, 71, 113, 190], [echo, echo * 0.6, echo * 0.4, echo * 0.25], tail)
    return norm(x)


def click(freq=3200, length=0.06, decay=0.012, tone=0.5):
    n = int(SR * length)
    x = highpass(noise(n), 1200) * env(n, 0.0005, decay) + np.sin(2 * np.pi * freq * t(n)) * env(n, 0.0005, decay * 0.7) * tone
    return norm(x, 0.8)


def seq(parts):
    """parts: list of (start_seconds, array)."""
    total = max(int(SR * s) + len(a) for s, a in parts)
    out = np.zeros(total)
    for s, a in parts:
        k = int(SR * s)
        out[k:k + len(a)] += a
    return norm(out, 0.85)


def write(name, x):
    os.makedirs(OUT, exist_ok=True)
    pcm = (np.clip(x, -1, 1) * 32767).astype(np.int16)
    with tempfile.NamedTemporaryFile(suffix='.raw', delete=False) as f:
        f.write(pcm.tobytes())
        raw = f.name
    subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-f', 's16le', '-ar', str(SR), '-ac', '1', '-i', raw,
                    '-c:a', 'libvorbis', '-q:a', '4', os.path.join(OUT, f'{name}.ogg')], check=True)
    os.unlink(raw)


SOUNDS = {
    'pistol_fire': lambda: shot(crack=1.1, body=0.8, boom=0.5, length=0.4, bright=7000, boom_f=(220, 70), tail=0.15, echo=0.25),
    'smg_fire': lambda: shot(crack=1.0, body=0.7, boom=0.45, length=0.3, bright=6500, boom_f=(200, 70), tail=0.1, echo=0.2),
    'rifle_fire': lambda: shot(crack=1.0, body=1.0, boom=0.8, length=0.55, bright=5500, boom_f=(150, 50), tail=0.3, echo=0.35),
    'battle_rifle_fire': lambda: shot(crack=0.9, body=1.1, boom=1.0, length=0.6, bright=4500, boom_f=(130, 42), tail=0.35, echo=0.4),
    'shotgun_fire': lambda: shot(crack=0.6, body=1.3, boom=1.4, length=0.75, bright=3200, boom_f=(110, 35), tail=0.4, echo=0.45),
    'sniper_fire': lambda: shot(crack=1.2, body=1.1, boom=1.3, length=1.1, bright=5000, boom_f=(120, 30), tail=0.7, echo=0.55),
    'lmg_fire': lambda: shot(crack=0.9, body=1.0, boom=0.9, length=0.5, bright=5000, boom_f=(140, 45), tail=0.25, echo=0.35),
    'cannon_fire': lambda: shot(crack=0.8, body=1.4, boom=2.2, length=1.6, bright=2200, boom_f=(80, 22), tail=1.0, echo=0.6),
    'mag_out': lambda: seq([(0.0, click(2400, 0.08, 0.02, 0.3)), (0.09, lowpass(noise(int(SR * 0.12)), 1500) * env(int(SR * 0.12), 0.01, 0.05) * 0.5)]),
    'mag_in': lambda: seq([(0.0, lowpass(noise(int(SR * 0.08)), 2000) * env(int(SR * 0.08), 0.005, 0.03) * 0.5), (0.1, click(2000, 0.09, 0.025, 0.6)), (0.17, click(3600, 0.06, 0.012, 0.4))]),
    'bolt': lambda: seq([(0.0, click(1800, 0.1, 0.03, 0.6)), (0.14, click(2600, 0.09, 0.025, 0.5))]),
    'dry_fire': lambda: click(3000, 0.05, 0.01, 0.6),
    'hit_marker': lambda: norm(np.sin(2 * np.pi * 1900 * t(int(SR * 0.07))) * env(int(SR * 0.07), 0.001, 0.02) + highpass(noise(int(SR * 0.07)), 3000) * env(int(SR * 0.07), 0.0005, 0.006) * 0.4, 0.6),
    'kill_marker': lambda: seq([(0.0, np.sin(2 * np.pi * 1500 * t(int(SR * 0.08))) * env(int(SR * 0.08), 0.001, 0.03)), (0.07, np.sin(2 * np.pi * 2200 * t(int(SR * 0.12))) * env(int(SR * 0.12), 0.001, 0.04))]),
}


def main():
    sounds_json = {}
    for name, fn in SOUNDS.items():
        write(name, fn())
        sounds_json[name] = {'category': 'player' if 'fire' in name else 'neutral', 'sounds': [f'frontline:{name}'],
                             'subtitle': f'subtitles.frontline.{name}'}
    with open(os.path.join(ROOT, 'sounds.json'), 'w') as f:
        json.dump(sounds_json, f, indent=1)
    print('sounds written:', ', '.join(SOUNDS))


if __name__ == '__main__':
    main()
