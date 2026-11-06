#!/usr/bin/env python3
"""Builds every firearm in Frontline as a blocky, realistically proportioned item model (JSON elements), the shared
material atlas they are textured with, and gen/guns_spec.json for the 3D preview page.

Coordinate convention (model units, 16 = one block): X across (8 = centre line), Y up (barrel axis near y = 10),
Z along the gun with the MUZZLE toward negative Z and the stock toward positive Z. The item pivot is (8, 8, 8).
Run from the repo root: python3 gen/gen_guns.py
"""
import json
import math
import os
import random

from PIL import Image, ImageDraw

ROOT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'frontline')
MODELS = os.path.join(ROOT, 'models', 'item')
TEX = os.path.join(ROOT, 'textures', 'item')
random.seed(11)

# ---------------------------------------------------------------- material atlas (128x128, 16x16 swatches, 8 per row)
MATS = [
    ('steel_dark', 0x2b2d31, 6), ('steel_mid', 0x45484e, 8), ('steel_light', 0x6b6f76, 8), ('polymer', 0x1c1d20, 4),
    ('polymer_dark', 0x2a2b2e, 5), ('wood', 0x6b4a2b, 14), ('tan', 0x9a8560, 7), ('rubber', 0x151515, 3),
    ('brass', 0xb08a3a, 10), ('glass', 0x1b2a4a, 6), ('white', 0xe8e8e8, 4), ('red', 0xc0302a, 6),
    ('olive', 0x4b5a3a, 7), ('tritium', 0xff9a2a, 8), ('gunmetal', 0x30363f, 6), ('black', 0x0e0f11, 2),
    ('steel_worn', 0x55585e, 12), ('wood_dark', 0x4a3119, 12), ('canvas', 0x6f6a4e, 9), ('lens', 0x7fb6e8, 10),
]
MAT_INDEX = {m[0]: i for i, m in enumerate(MATS)}


def rgb(h):
    return ((h >> 16) & 255, (h >> 8) & 255, h & 255, 255)


def clamp(v):
    return max(0, min(255, int(v)))


def swatch(img, idx, base, noise):
    sx, sy = (idx % 8) * 16, (idx // 8) * 16
    r, g, b, _ = rgb(base)
    name = MATS[idx][0]
    for y in range(16):
        for x in range(16):
            n = random.randint(-noise, noise)
            if name.startswith('wood'):
                grain = int(6 * math.sin((x * 0.9 + y * 0.25) + random.random() * 0.3))
                n += grain
            if name == 'glass' or name == 'lens':
                # a diagonal highlight
                d = abs((x + y) - 14)
                n += 70 if d <= 1 else (25 if d <= 3 else 0)
            if name in ('steel_dark', 'steel_mid', 'gunmetal', 'steel_worn') and random.random() < 0.06:
                n += 18  # scratches
            img.putpixel((sx + x, sy + y), (clamp(r + n), clamp(g + n), clamp(b + n), 255))
    # a one pixel darker frame keeps edges readable between parts
    for i in range(16):
        for (px, py) in ((sx + i, sy), (sx + i, sy + 15), (sx, sy + i), (sx + 15, sy + i)):
            cr, cg, cb, _ = img.getpixel((px, py))
            img.putpixel((px, py), (clamp(cr * 0.82), clamp(cg * 0.82), clamp(cb * 0.82), 255))


def build_atlas():
    img = Image.new('RGBA', (128, 128), (0, 0, 0, 0))
    for i, (name, base, noise) in enumerate(MATS):
        swatch(img, i, base, noise)
    os.makedirs(TEX, exist_ok=True)
    img.save(os.path.join(TEX, 'gun_atlas.png'))


# ---------------------------------------------------------------- element helpers
def uv_for(mat, w, h):
    i = MAT_INDEX[mat]
    sx, sy = (i % 8) * 16, (i // 8) * 16
    w = max(1, min(16, w))
    h = max(1, min(16, h))
    # item-model UVs live in 0..16 space over the whole texture, so 128 px -> divide by 8
    return [round(sx / 8, 4), round(sy / 8, 4), round((sx + w) / 8, 4), round((sy + h) / 8, 4)]


def box(parts, x0, y0, z0, x1, y1, z1, mat, rot=None, name=''):
    """A cuboid from (x0,y0,z0) to (x1,y1,z1). rot = (axis, angle, (ox, oy, oz))."""
    dx, dy, dz = x1 - x0, y1 - y0, z1 - z0
    faces = {
        'north': {'uv': uv_for(mat, dx, dy), 'texture': '#a'}, 'south': {'uv': uv_for(mat, dx, dy), 'texture': '#a'},
        'east': {'uv': uv_for(mat, dz, dy), 'texture': '#a'}, 'west': {'uv': uv_for(mat, dz, dy), 'texture': '#a'},
        'up': {'uv': uv_for(mat, dx, dz), 'texture': '#a'}, 'down': {'uv': uv_for(mat, dx, dz), 'texture': '#a'},
    }
    el = {'from': [round(x0, 3), round(y0, 3), round(z0, 3)], 'to': [round(x1, 3), round(y1, 3), round(z1, 3)], 'faces': faces}
    if name:
        el['name'] = name
    if rot:
        axis, angle, origin = rot
        el['rotation'] = {'origin': [round(o, 3) for o in origin], 'axis': axis, 'angle': angle}
    el['_mat'] = mat
    parts.append(el)


def tube(parts, cx, cy, z0, z1, r, mat, name='tube'):
    """An octagonal tube along Z: a square box plus the same box turned 45 degrees."""
    box(parts, cx - r, cy - r, z0, cx + r, cy + r, z1, mat, name=name)
    s = r * 0.93
    box(parts, cx - s, cy - s, z0 + 0.01, cx + s, cy + s, z1 - 0.01, mat, rot=('z', 45, (cx, cy, z0)), name=name + '_45')


def ytube(parts, cx, cz, y0, y1, r, mat, name='ytube'):
    box(parts, cx - r, y0, cz - r, cx + r, y1, cz + r, mat, name=name)
    s = r * 0.93
    box(parts, cx - s, y0 + 0.01, cz - s, cx + s, y1 - 0.01, cz + s, mat, rot=('y', 45, (cx, y0, cz)), name=name + '_45')


def rail(parts, x0, x1, y, z0, z1, mat='steel_dark'):
    """A Picatinny-style rail: a base strip with slots every two units."""
    box(parts, x0, y, z0, x1, y + 0.6, z1, mat, name='rail')
    z = z0 + 0.6
    while z + 0.8 < z1:
        box(parts, x0 - 0.05, y + 0.6, z, x1 + 0.05, y + 0.95, z + 0.9, mat, name='rail_tooth')
        z += 2.0


# ---------------------------------------------------------------- the guns
def fl4_carbine():
    p = []
    cx = 8
    # receiver
    box(p, 6.8, 9.0, -4.0, 9.2, 11.6, 6.0, 'steel_dark', name='upper')
    box(p, 7.0, 7.0, -2.0, 9.0, 9.0, 5.0, 'polymer_dark', name='lower')
    box(p, 7.0, 5.5, -1.6, 9.0, 7.0, 1.6, 'polymer_dark', name='magwell')
    box(p, 7.2, 0.0, -1.3, 8.8, 5.5, 1.3, 'steel_mid', name='magazine')
    box(p, 7.1, -0.4, -1.5, 8.9, 0.3, 1.5, 'polymer', name='mag_base')
    # grip, raked back
    box(p, 7.15, 2.6, 3.2, 8.85, 7.0, 5.0, 'polymer', rot=('x', -22.5, (8, 7.0, 4.1)), name='grip')
    box(p, 7.6, 5.7, 0.6, 8.4, 6.1, 3.6, 'steel_dark', name='trigger_guard')
    box(p, 7.8, 6.0, 1.6, 8.2, 7.4, 2.0, 'steel_light', rot=('x', 22.5, (8, 7.0, 1.8)), name='trigger')
    box(p, 9.2, 9.6, -1.6, 9.45, 10.9, 1.4, 'steel_light', name='ejection_port')
    box(p, 6.55, 8.6, 1.5, 6.8, 9.6, 2.5, 'steel_mid', name='mag_release')
    box(p, 9.2, 8.4, 2.0, 9.6, 9.4, 3.2, 'steel_mid', name='bolt_catch')
    # charging handle and rear sight
    box(p, 7.4, 11.6, 5.8, 8.6, 12.2, 7.4, 'steel_dark', name='charging_handle')
    rail(p, 7.3, 8.7, 11.6, -4.0, 5.6)
    box(p, 7.2, 12.5, 3.0, 8.8, 13.6, 4.2, 'steel_dark', name='rear_sight')
    box(p, 7.75, 13.0, 3.4, 8.25, 13.5, 3.8, 'black', name='aperture')
    # buffer tube and stock
    tube(p, cx, 10.3, 6.0, 12.4, 0.95, 'steel_mid', name='buffer_tube')
    box(p, 7.0, 7.6, 9.0, 9.0, 11.4, 13.2, 'polymer', name='stock')
    box(p, 6.9, 7.2, 13.0, 9.1, 12.0, 13.8, 'rubber', name='butt_pad')
    box(p, 7.3, 11.4, 9.5, 8.7, 12.0, 13.0, 'polymer_dark', name='cheek_rest')
    box(p, 7.4, 6.6, 9.4, 8.6, 7.6, 12.6, 'polymer', name='stock_toe')
    # handguard with rails and vents
    box(p, 6.9, 8.5, -15.0, 9.1, 11.6, -4.0, 'polymer_dark', name='handguard')
    rail(p, 7.3, 8.7, 11.6, -15.0, -4.2)
    for z in (-13.5, -11.5, -9.5, -7.5):
        box(p, 6.75, 9.2, z, 6.9, 10.9, z + 1.0, 'black', name='vent_l')
        box(p, 9.1, 9.2, z, 9.25, 10.9, z + 1.0, 'black', name='vent_r')
    box(p, 7.4, 7.9, -11.0, 8.6, 8.5, -7.0, 'steel_dark', name='bottom_rail')
    # gas block, front sight, barrel, muzzle
    box(p, 7.4, 9.4, -15.6, 8.6, 11.0, -15.0, 'steel_dark', name='delta_ring')
    box(p, 7.45, 10.6, -17.4, 8.55, 13.0, -16.0, 'steel_dark', name='front_sight_tower')
    box(p, 7.85, 12.9, -17.0, 8.15, 13.7, -16.6, 'steel_light', name='front_post')
    box(p, 7.9, 13.6, -17.0, 8.1, 13.9, -16.6, 'tritium', name='front_dot')
    tube(p, cx, 10.25, -22.0, -15.6, 0.6, 'steel_mid', name='barrel')
    tube(p, cx, 10.25, -23.4, -21.8, 0.85, 'steel_dark', name='muzzle_device')
    box(p, 7.2, 10.95, -23.2, 8.8, 11.15, -22.0, 'black', name='brake_slot_top')
    box(p, 7.1, 9.9, -23.2, 7.3, 10.6, -22.0, 'black', name='brake_slot_l')
    box(p, 8.7, 9.9, -23.2, 8.9, 10.6, -22.0, 'black', name='brake_slot_r')
    return p, dict(muzzle=(8, 10.25, -23.4), sight_y=13.7, length=37)


def vk47_rifle():
    p = []
    cx = 8
    box(p, 6.9, 8.6, -5.0, 9.1, 11.3, 6.5, 'steel_dark', name='receiver')
    box(p, 7.0, 11.3, -4.5, 9.0, 12.1, 5.5, 'steel_mid', name='dust_cover')
    box(p, 7.0, 6.9, -2.5, 9.0, 8.6, 5.0, 'steel_dark', name='lower')
    # curved magazine: three segments turning forward
    box(p, 7.2, 3.8, -1.6, 8.8, 6.9, 1.3, 'steel_mid', name='mag_1')
    box(p, 7.2, 1.2, -2.3, 8.8, 4.0, 0.6, 'steel_mid', rot=('x', -22.5, (8, 4.0, -0.6)), name='mag_2')
    box(p, 7.2, -1.6, -3.6, 8.8, 1.3, -0.5, 'steel_mid', rot=('x', -45, (8, 1.3, -1.8)), name='mag_3')
    box(p, 7.1, -2.0, -4.1, 8.9, -1.3, -0.9, 'steel_dark', rot=('x', -45, (8, 1.3, -1.8)), name='mag_floor')
    # grip and trigger
    box(p, 7.2, 2.6, 3.6, 8.8, 6.9, 5.4, 'wood_dark', rot=('x', -22.5, (8, 6.9, 4.5)), name='grip')
    box(p, 7.6, 5.6, 0.8, 8.4, 6.0, 3.8, 'steel_dark', name='trigger_guard')
    box(p, 7.8, 5.9, 2.0, 8.2, 7.1, 2.4, 'steel_light', rot=('x', 22.5, (8, 6.9, 2.2)), name='trigger')
    box(p, 9.1, 8.9, 4.0, 9.6, 10.4, 6.2, 'steel_mid', name='selector')
    box(p, 9.1, 9.3, -3.5, 9.35, 10.6, 0.5, 'steel_light', name='ejection_port')
    box(p, 7.4, 12.1, 3.0, 8.6, 13.1, 4.0, 'steel_dark', name='rear_sight_base')
    box(p, 7.3, 13.0, 3.3, 8.7, 13.5, 3.7, 'steel_light', name='rear_sight_leaf')
    box(p, 7.85, 13.3, 3.3, 8.15, 13.9, 3.7, 'black', name='rear_notch')
    # wooden stock
    box(p, 7.0, 7.5, 6.5, 9.0, 11.3, 9.5, 'wood', name='stock_neck')
    box(p, 6.9, 6.2, 9.5, 9.1, 11.6, 15.0, 'wood', name='stock')
    box(p, 6.8, 5.8, 15.0, 9.2, 12.0, 15.9, 'steel_dark', name='butt_plate')
    box(p, 7.3, 11.6, 10.0, 8.7, 12.2, 14.5, 'wood_dark', name='comb')
    # handguard, gas tube, gas block
    box(p, 6.9, 8.3, -13.0, 9.1, 10.6, -5.0, 'wood', name='lower_handguard')
    box(p, 7.1, 11.0, -13.0, 8.9, 12.0, -5.0, 'wood', name='upper_handguard')
    tube(p, cx, 11.6, -17.5, -13.0, 0.5, 'steel_mid', name='gas_tube')
    box(p, 7.3, 9.6, -18.6, 8.7, 12.4, -17.3, 'steel_dark', name='gas_block')
    box(p, 7.0, 7.4, -13.4, 9.0, 8.3, -5.2, 'steel_mid', name='handguard_cap')
    tube(p, cx, 10.0, -24.5, -13.0, 0.6, 'steel_mid', name='barrel')
    box(p, 7.4, 9.6, -24.2, 8.6, 13.8, -23.2, 'steel_dark', name='front_sight_tower')
    box(p, 7.85, 13.0, -23.9, 8.15, 14.3, -23.5, 'steel_light', name='front_post')
    box(p, 7.9, 14.2, -23.9, 8.1, 14.5, -23.5, 'tritium', name='front_dot')
    # slant brake
    tube(p, cx, 10.0, -26.6, -24.4, 0.85, 'steel_dark', name='brake')
    box(p, 7.15, 10.6, -26.5, 8.85, 10.85, -24.6, 'black', rot=('x', 22.5, (8, 10.7, -25.5)), name='brake_slant')
    # cleaning rod under barrel
    tube(p, cx, 8.9, -23.5, -13.4, 0.22, 'steel_light', name='cleaning_rod')
    return p, dict(muzzle=(8, 10.0, -26.6), sight_y=14.3, length=42)


def p9_sidearm():
    p = []
    cx = 8
    box(p, 7.1, 9.0, -6.5, 8.9, 11.3, 3.0, 'steel_dark', name='slide')
    box(p, 7.2, 11.3, -5.5, 8.8, 11.7, 2.4, 'gunmetal', name='slide_top')
    for z in (1.4, 2.0, 2.6):
        box(p, 7.0, 9.4, z, 9.0, 10.9, z + 0.25, 'black', name='serration')
    for z in (-6.0, -5.4, -4.8):
        box(p, 7.0, 9.4, z, 9.0, 10.9, z + 0.25, 'black', name='front_serration')
    box(p, 9.0, 9.6, -3.4, 9.15, 10.8, -0.8, 'steel_light', name='ejection_port')
    box(p, 7.2, 7.6, -5.5, 8.8, 9.0, 3.2, 'polymer', name='frame')
    box(p, 7.25, 2.6, 0.9, 8.75, 7.6, 3.6, 'polymer', rot=('x', -22.5, (8, 7.6, 2.2)), name='grip')
    box(p, 7.1, 2.2, 0.4, 8.9, 2.9, 3.9, 'steel_mid', rot=('x', -22.5, (8, 7.6, 2.2)), name='mag_base')
    box(p, 7.6, 5.4, -3.6, 8.4, 5.8, 0.4, 'polymer', name='trigger_guard')
    box(p, 7.6, 5.4, -3.6, 8.4, 7.6, -3.2, 'polymer', name='guard_front')
    box(p, 7.8, 5.8, -1.6, 8.2, 7.4, -1.2, 'steel_light', rot=('x', 22.5, (8, 7.4, -1.4)), name='trigger')
    box(p, 7.0, 8.8, 2.6, 9.0, 10.0, 3.3, 'steel_dark', name='beavertail')
    box(p, 7.15, 8.2, -5.5, 8.85, 9.0, -3.0, 'polymer_dark', name='frame_rail')
    box(p, 7.0, 7.0, -3.6, 8.9, 7.4, -0.6, 'polymer_dark', name='dust_rail')
    tube(p, cx, 10.15, -7.3, -6.4, 0.55, 'steel_mid', name='barrel')
    # sights
    box(p, 7.2, 11.7, 1.6, 8.8, 12.4, 2.4, 'steel_dark', name='rear_sight')
    box(p, 7.75, 11.9, 1.5, 8.25, 12.4, 1.65, 'black', name='rear_notch')
    box(p, 7.3, 12.0, 1.5, 7.55, 12.3, 1.65, 'tritium', name='rear_dot_l')
    box(p, 8.45, 12.0, 1.5, 8.7, 12.3, 1.65, 'tritium', name='rear_dot_r')
    box(p, 7.8, 11.7, -5.6, 8.2, 12.4, -5.0, 'steel_dark', name='front_sight')
    box(p, 7.88, 12.05, -5.65, 8.12, 12.3, -5.55, 'tritium', name='front_dot')
    box(p, 6.95, 8.4, 0.6, 7.1, 9.6, 1.6, 'steel_mid', name='slide_release')
    box(p, 8.9, 7.6, 0.8, 9.1, 8.3, 1.6, 'steel_mid', name='mag_release')
    return p, dict(muzzle=(8, 10.15, -7.3), sight_y=12.4, length=14)


def mp9_smg():
    p = []
    cx = 8
    box(p, 6.9, 8.4, -6.0, 9.1, 11.8, 5.0, 'polymer_dark', name='receiver')
    box(p, 7.0, 6.6, -1.5, 9.0, 8.4, 4.4, 'polymer', name='lower')
    box(p, 7.2, -2.0, -0.6, 8.8, 6.6, 1.4, 'steel_mid', name='magazine')
    box(p, 7.1, -2.5, -0.8, 8.9, -1.8, 1.6, 'polymer', name='mag_base')
    box(p, 7.15, 2.8, 2.6, 8.85, 6.6, 4.4, 'polymer', rot=('x', -22.5, (8, 6.6, 3.5)), name='grip')
    box(p, 7.6, 5.6, -0.2, 8.4, 6.0, 2.6, 'polymer', name='trigger_guard')
    box(p, 7.8, 5.8, 1.0, 8.2, 6.9, 1.4, 'steel_light', rot=('x', 22.5, (8, 6.6, 1.2)), name='trigger')
    box(p, 9.1, 9.4, -2.5, 9.35, 10.6, 0.5, 'steel_light', name='ejection_port')
    box(p, 6.6, 9.0, 0.5, 6.9, 10.2, 3.5, 'steel_mid', name='charging_handle')
    rail(p, 7.3, 8.7, 11.8, -6.0, 4.6)
    box(p, 7.2, 12.4, 2.4, 8.8, 13.6, 3.4, 'steel_dark', name='rear_sight')
    box(p, 7.75, 13.0, 2.7, 8.25, 13.45, 3.1, 'black', name='aperture')
    box(p, 7.6, 12.4, -5.6, 8.4, 13.4, -4.8, 'steel_dark', name='front_sight')
    box(p, 7.88, 13.4, -5.4, 8.12, 13.8, -5.0, 'steel_light', name='front_post')
    # vertical foregrip
    box(p, 7.2, 4.4, -5.4, 8.8, 8.4, -3.6, 'polymer', rot=('x', 22.5, (8, 8.4, -4.5)), name='foregrip')
    box(p, 7.0, 7.6, -6.0, 9.0, 8.4, -1.5, 'polymer_dark', name='lower_rail_base')
    # barrel shroud with vent holes, barrel and threaded muzzle
    tube(p, cx, 10.1, -14.0, -6.0, 1.15, 'steel_dark', name='shroud')
    for z in (-13.0, -11.2, -9.4, -7.6):
        box(p, 6.75, 9.75, z, 6.95, 10.45, z + 0.8, 'black', name='hole_l')
        box(p, 9.05, 9.75, z, 9.25, 10.45, z + 0.8, 'black', name='hole_r')
        box(p, 7.65, 11.15, z, 8.35, 11.35, z + 0.8, 'black', name='hole_top')
    tube(p, cx, 10.1, -15.8, -14.0, 0.55, 'steel_mid', name='barrel')
    tube(p, cx, 10.1, -16.4, -15.6, 0.7, 'steel_dark', name='thread_protector')
    # side-folding stock, extended
    box(p, 7.4, 9.6, 5.0, 8.6, 10.6, 12.0, 'steel_mid', name='stock_bar')
    box(p, 7.3, 8.2, 5.0, 8.7, 9.6, 6.0, 'steel_dark', name='stock_hinge')
    box(p, 7.2, 7.4, 11.4, 8.8, 12.0, 13.0, 'polymer', name='butt')
    box(p, 7.1, 7.2, 13.0, 8.9, 12.2, 13.5, 'rubber', name='butt_pad')
    return p, dict(muzzle=(8, 10.1, -16.4), sight_y=13.8, length=30)


def sg12_shotgun():
    p = []
    cx = 8
    box(p, 6.9, 8.4, -5.0, 9.1, 11.6, 6.0, 'steel_dark', name='receiver')
    box(p, 7.0, 7.0, -1.0, 9.0, 8.4, 5.0, 'steel_dark', name='lower')
    box(p, 7.6, 5.6, -0.5, 8.4, 6.0, 3.0, 'steel_dark', name='trigger_guard')
    box(p, 7.6, 5.6, -0.5, 8.4, 7.0, -0.1, 'steel_dark', name='guard_front')
    box(p, 7.8, 5.9, 1.2, 8.2, 7.1, 1.6, 'steel_light', rot=('x', 22.5, (8, 7.0, 1.4)), name='trigger')
    box(p, 9.1, 9.0, -3.5, 9.35, 10.8, 0.5, 'steel_light', name='ejection_port')
    box(p, 6.7, 8.6, -4.0, 6.9, 9.8, -1.0, 'black', name='loading_port')
    box(p, 7.4, 11.6, -4.5, 8.6, 12.1, 5.5, 'gunmetal', name='top_vent_rib')
    # wooden stock with grip swell
    box(p, 7.0, 6.8, 6.0, 9.0, 11.4, 9.0, 'wood', rot=('x', -22.5, (8, 11.4, 6.0)), name='wrist')
    box(p, 6.9, 4.6, 8.4, 9.1, 10.8, 15.2, 'wood', rot=('x', -22.5, (8, 11.4, 6.0)), name='stock')
    box(p, 6.8, 4.2, 15.2, 9.2, 11.2, 16.1, 'rubber', rot=('x', -22.5, (8, 11.4, 6.0)), name='recoil_pad')
    # barrel and tube magazine
    tube(p, cx, 10.3, -27.0, -5.0, 0.75, 'steel_mid', name='barrel')
    tube(p, cx, 8.2, -24.0, -5.0, 0.7, 'steel_mid', name='mag_tube')
    box(p, 7.6, 8.0, -24.6, 8.4, 10.6, -23.8, 'steel_dark', name='barrel_band')
    box(p, 7.6, 7.4, -24.8, 8.4, 8.0, -23.6, 'steel_dark', name='mag_cap')
    # pump with ribs
    box(p, 6.9, 7.0, -17.5, 9.1, 9.7, -9.5, 'polymer', name='pump')
    for z in (-16.5, -15.0, -13.5, -12.0, -10.5):
        box(p, 6.8, 7.2, z, 9.2, 9.5, z + 0.6, 'polymer_dark', name='pump_rib')
    box(p, 7.4, 9.7, -9.5, 8.6, 10.3, -5.0, 'steel_mid', name='action_bar')
    # bead sight
    box(p, 7.8, 11.05, -26.6, 8.2, 11.6, -26.1, 'steel_dark', name='bead_base')
    box(p, 7.85, 11.6, -26.55, 8.15, 11.9, -26.15, 'tritium', name='bead')
    box(p, 7.0, 7.0, -1.0, 9.0, 7.5, 5.0, 'steel_mid', name='bottom_plate')
    return p, dict(muzzle=(8, 10.3, -27.0), sight_y=11.9, length=44)


def r7_marksman():
    p = []
    cx = 8
    # stock body (chassis)
    box(p, 7.0, 7.4, -9.0, 9.0, 10.2, 9.0, 'polymer_dark', name='chassis')
    box(p, 7.1, 6.4, -9.0, 8.9, 7.4, -1.0, 'polymer_dark', name='forend')
    box(p, 7.2, 3.6, 0.8, 8.8, 7.4, 2.8, 'polymer', rot=('x', -22.5, (8, 7.4, 1.8)), name='grip')
    box(p, 7.6, 6.0, -2.5, 8.4, 6.4, 1.0, 'steel_dark', name='trigger_guard')
    box(p, 7.8, 6.2, -1.2, 8.2, 7.4, -0.8, 'steel_light', rot=('x', 22.5, (8, 7.4, -1.0)), name='trigger')
    box(p, 7.3, 4.2, -5.0, 8.7, 7.4, -2.6, 'steel_dark', name='magazine')
    # action and bolt
    box(p, 6.9, 10.0, -6.0, 9.1, 12.0, 5.0, 'steel_dark', name='action')
    tube(p, cx, 11.0, 5.0, 8.6, 0.65, 'steel_light', name='bolt')
    box(p, 9.1, 10.4, 2.0, 11.6, 11.4, 3.2, 'steel_light', rot=('z', -22.5, (9.1, 10.9, 2.6)), name='bolt_handle')
    box(p, 11.3, 9.4, 1.8, 12.1, 11.0, 3.4, 'black', rot=('z', -22.5, (9.1, 10.9, 2.6)), name='bolt_knob')
    box(p, 9.1, 10.4, -3.5, 9.3, 11.6, -0.5, 'black', name='ejection_port')
    # stock rear: cheek riser and butt
    box(p, 7.0, 7.0, 9.0, 9.0, 11.2, 15.5, 'polymer_dark', name='buttstock')
    box(p, 7.2, 11.2, 9.5, 8.8, 12.4, 14.0, 'polymer', name='cheek_riser')
    box(p, 6.9, 6.6, 15.5, 9.1, 11.6, 16.4, 'rubber', name='recoil_pad')
    box(p, 7.4, 5.0, 12.0, 8.6, 7.0, 14.5, 'steel_mid', name='monopod')
    # barrel: heavy, with threaded muzzle brake
    tube(p, cx, 11.0, -31.0, -9.0, 0.75, 'steel_mid', name='barrel')
    tube(p, cx, 11.0, -33.5, -30.8, 1.05, 'steel_dark', name='muzzle_brake')
    for z in (-33.0, -32.2, -31.4):
        box(p, 6.8, 10.5, z, 7.0, 11.5, z + 0.4, 'black', name='brake_port_l')
        box(p, 9.0, 10.5, z, 9.2, 11.5, z + 0.4, 'black', name='brake_port_r')
    # folded bipod under the forend
    box(p, 7.2, 5.6, -9.4, 8.8, 6.4, -7.8, 'steel_dark', name='bipod_mount')
    box(p, 6.7, 5.8, -22.0, 7.3, 6.5, -9.4, 'steel_dark', name='bipod_leg_l')
    box(p, 8.7, 5.8, -22.0, 9.3, 6.5, -9.4, 'steel_dark', name='bipod_leg_r')
    box(p, 6.6, 5.6, -22.6, 7.4, 6.7, -22.0, 'rubber', name='bipod_foot_l')
    box(p, 8.6, 5.6, -22.6, 9.4, 6.7, -22.0, 'rubber', name='bipod_foot_r')
    # scope: rail, rings, tube, bells, turrets, lenses
    rail(p, 7.3, 8.7, 12.0, -6.0, 5.0)
    for z in (-3.6, 2.4):
        box(p, 7.2, 12.9, z, 8.8, 14.2, z + 1.2, 'steel_dark', name='scope_ring_base')
        box(p, 7.1, 14.0, z - 0.1, 8.9, 16.9, z + 1.3, 'steel_dark', name='scope_ring')
    tube(p, cx, 15.4, -3.0, 3.0, 1.1, 'gunmetal', name='scope_tube')
    tube(p, cx, 15.4, -8.8, -3.0, 1.55, 'gunmetal', name='objective_bell')
    tube(p, cx, 15.4, 3.0, 6.6, 1.35, 'gunmetal', name='ocular_bell')
    box(p, 6.75, 14.15, -9.0, 9.25, 16.65, -8.75, 'lens', name='objective_lens')
    box(p, 6.95, 14.35, 6.6, 9.05, 16.45, 6.85, 'glass', name='ocular_lens')
    box(p, 7.5, 16.4, -1.2, 8.5, 17.6, 0.2, 'steel_dark', name='elevation_turret')
    box(p, 9.0, 14.9, -1.2, 10.2, 15.9, 0.2, 'steel_dark', name='windage_turret')
    box(p, 5.8, 14.9, -1.2, 7.0, 15.9, 0.2, 'steel_dark', name='parallax_turret')
    tube(p, cx, 15.4, 2.9, 4.6, 1.45, 'steel_dark', name='magnification_ring')
    return p, dict(muzzle=(8, 11.0, -33.5), sight_y=15.4, length=50)


def lm249_lmg():
    p = []
    cx = 8
    box(p, 6.7, 8.2, -8.0, 9.3, 12.2, 7.0, 'steel_dark', name='receiver')
    box(p, 6.8, 12.2, -8.0, 9.2, 13.0, 4.0, 'gunmetal', name='feed_cover')
    box(p, 7.0, 6.6, -3.0, 9.0, 8.2, 6.0, 'steel_dark', name='lower')
    box(p, 7.15, 2.8, 3.6, 8.85, 6.6, 5.6, 'polymer', rot=('x', -22.5, (8, 6.6, 4.6)), name='grip')
    box(p, 7.6, 5.6, 0.0, 8.4, 6.0, 3.4, 'steel_dark', name='trigger_guard')
    box(p, 7.8, 5.9, 1.6, 8.2, 7.0, 2.0, 'steel_light', rot=('x', 22.5, (8, 6.6, 1.8)), name='trigger')
    # ammo box hanging below the feed tray
    box(p, 5.4, 1.0, -6.0, 10.6, 6.6, 0.5, 'olive', name='ammo_box')
    box(p, 5.3, 6.4, -6.2, 10.7, 7.0, 0.7, 'steel_dark', name='box_lid')
    box(p, 5.2, 3.0, -2.5, 5.4, 5.0, -1.0, 'steel_mid', name='box_latch')
    box(p, 6.4, 7.0, -8.0, 6.7, 8.2, -5.0, 'brass', name='belt_links')
    # charging handle on the right
    box(p, 9.3, 9.0, -1.0, 10.4, 9.8, 2.5, 'steel_mid', name='charging_handle')
    # carry handle
    box(p, 7.5, 13.0, -9.5, 8.5, 13.8, -8.5, 'steel_dark', name='handle_post_f')
    box(p, 7.5, 13.0, -3.5, 8.5, 13.8, -2.5, 'steel_dark', name='handle_post_r')
    box(p, 7.4, 13.8, -10.0, 8.6, 15.0, -2.0, 'polymer_dark', name='carry_handle')
    # stock
    box(p, 7.0, 7.6, 7.0, 9.0, 11.6, 9.0, 'steel_mid', name='stock_neck')
    box(p, 6.9, 5.6, 9.0, 9.1, 12.0, 15.0, 'polymer', name='stock')
    box(p, 6.8, 5.2, 15.0, 9.2, 12.4, 15.9, 'rubber', name='butt_pad')
    box(p, 7.4, 7.6, 9.4, 8.6, 10.4, 14.0, 'polymer_dark', name='stock_cutout')
    # gas tube, barrel, heat shield, bipod
    box(p, 6.9, 8.6, -17.0, 9.1, 11.6, -8.0, 'polymer_dark', name='handguard')
    for z in (-16.0, -14.0, -12.0, -10.0):
        box(p, 6.75, 9.2, z, 6.9, 11.0, z + 1.0, 'black', name='vent_l')
        box(p, 9.1, 9.2, z, 9.25, 11.0, z + 1.0, 'black', name='vent_r')
    tube(p, cx, 8.9, -22.0, -17.0, 0.6, 'steel_mid', name='gas_tube')
    tube(p, cx, 10.8, -29.0, -17.0, 0.8, 'steel_mid', name='barrel')
    box(p, 7.2, 8.6, -23.0, 8.8, 12.0, -21.8, 'steel_dark', name='gas_block')
    box(p, 7.85, 12.0, -22.8, 8.15, 13.2, -22.2, 'steel_light', name='front_post')
    box(p, 7.0, 11.9, -23.1, 9.0, 13.4, -22.9, 'steel_dark', name='sight_wing')
    box(p, 7.3, 12.9, 4.4, 8.7, 14.4, 5.4, 'steel_dark', name='rear_sight')
    box(p, 7.75, 13.6, 4.7, 8.25, 14.1, 5.1, 'black', name='aperture')
    tube(p, cx, 10.8, -31.0, -28.8, 1.05, 'steel_dark', name='flash_hider')
    box(p, 7.3, 11.6, -30.8, 8.7, 11.85, -29.0, 'black', name='hider_slot_top')
    box(p, 7.15, 10.2, -30.8, 7.35, 11.2, -29.0, 'black', name='hider_slot_l')
    box(p, 8.65, 10.2, -30.8, 8.85, 11.2, -29.0, 'black', name='hider_slot_r')
    # bipod legs folded forward along the gas tube
    box(p, 7.0, 7.4, -20.6, 9.0, 8.3, -19.4, 'steel_dark', name='bipod_hinge')
    box(p, 6.5, 7.5, -29.0, 7.1, 8.2, -20.6, 'steel_dark', name='bipod_leg_l')
    box(p, 8.9, 7.5, -29.0, 9.5, 8.2, -20.6, 'steel_dark', name='bipod_leg_r')
    box(p, 6.4, 7.3, -29.6, 7.2, 8.4, -29.0, 'rubber', name='bipod_foot_l')
    box(p, 8.8, 7.3, -29.6, 9.6, 8.4, -29.0, 'rubber', name='bipod_foot_r')
    return p, dict(muzzle=(8, 10.8, -31.0), sight_y=14.1, length=47)


def r6_revolver():
    p = []
    cx = 8
    box(p, 7.2, 8.4, -2.0, 8.8, 11.0, 3.4, 'steel_mid', name='frame')
    box(p, 7.0, 11.0, -2.0, 9.0, 11.6, 3.0, 'steel_mid', name='top_strap')
    # fluted cylinder: an octagon with dark flutes
    tube(p, cx, 9.7, -1.8, 1.6, 1.55, 'steel_light', name='cylinder')
    for ang_x, ang_y in ((-1.6, 10.9), (1.6, 10.9), (-1.6, 8.5), (1.6, 8.5)):
        box(p, cx + ang_x - 0.2, ang_y - 0.3, -1.5, cx + ang_x + 0.2, ang_y + 0.3, 1.3, 'black', name='flute')
    box(p, 7.0, 8.2, -1.9, 7.3, 11.2, 1.7, 'steel_mid', name='crane')
    # barrel with underlug and rib
    tube(p, cx, 10.2, -9.0, -2.0, 0.6, 'steel_mid', name='barrel')
    box(p, 7.2, 8.6, -9.0, 8.8, 9.8, -2.0, 'steel_mid', name='underlug')
    box(p, 7.6, 10.7, -8.8, 8.4, 11.2, -2.0, 'steel_dark', name='rib')
    tube(p, cx, 9.0, -8.6, -2.5, 0.3, 'steel_light', name='ejector_rod')
    box(p, 7.8, 11.2, -8.6, 8.2, 12.0, -8.0, 'steel_dark', name='front_sight')
    box(p, 7.88, 11.9, -8.55, 8.12, 12.2, -8.05, 'tritium', name='front_dot')
    box(p, 7.3, 11.6, 2.0, 8.7, 12.2, 2.8, 'steel_dark', name='rear_sight')
    box(p, 7.8, 11.8, 1.95, 8.2, 12.25, 2.1, 'black', name='rear_notch')
    # hammer, trigger, grip
    box(p, 7.6, 10.6, 3.0, 8.4, 12.2, 4.2, 'steel_dark', rot=('x', -22.5, (8, 11.0, 3.4)), name='hammer')
    box(p, 7.6, 6.6, -1.0, 8.4, 7.0, 2.6, 'steel_dark', name='trigger_guard')
    box(p, 7.6, 6.6, -1.0, 8.4, 8.4, -0.6, 'steel_dark', name='guard_front')
    box(p, 7.8, 7.0, 0.6, 8.2, 8.4, 1.0, 'steel_light', rot=('x', 22.5, (8, 8.4, 0.8)), name='trigger')
    box(p, 7.2, 3.0, 1.6, 8.8, 8.4, 4.2, 'wood', rot=('x', -22.5, (8, 8.4, 2.9)), name='grip')
    box(p, 7.1, 2.8, 1.4, 8.9, 3.4, 4.4, 'steel_dark', rot=('x', -22.5, (8, 8.4, 2.9)), name='grip_cap')
    return p, dict(muzzle=(8, 10.2, -9.0), sight_y=12.2, length=16)


def bp5_bullpup():
    p = []
    cx = 8
    # one long polymer body: the action sits behind the grip
    box(p, 6.9, 7.4, -6.0, 9.1, 11.4, 12.0, 'polymer_dark', name='body')
    box(p, 7.0, 6.2, 4.0, 9.0, 7.4, 12.0, 'polymer_dark', name='lower_rear')
    box(p, 6.8, 7.0, 12.0, 9.2, 11.8, 12.8, 'rubber', name='butt_pad')
    box(p, 7.3, 11.4, 6.0, 8.7, 12.2, 11.0, 'polymer', name='cheek')
    box(p, 7.2, -1.0, 6.0, 8.8, 6.2, 8.8, 'steel_mid', rot=('x', -22.5, (8, 6.2, 7.4)), name='magazine')
    box(p, 7.1, -1.4, 5.8, 8.9, -0.7, 9.0, 'polymer', rot=('x', -22.5, (8, 6.2, 7.4)), name='mag_base')
    box(p, 9.1, 9.0, 4.0, 9.35, 10.4, 8.0, 'black', name='ejection_port')
    box(p, 7.15, 3.0, -1.6, 8.85, 7.4, 0.2, 'polymer', rot=('x', -22.5, (8, 7.4, -0.7)), name='grip')
    box(p, 7.6, 5.8, -4.6, 8.4, 6.2, -1.4, 'polymer', name='trigger_guard')
    box(p, 7.6, 5.8, -4.6, 8.4, 7.4, -4.2, 'polymer', name='guard_front')
    box(p, 7.8, 6.0, -3.0, 8.2, 7.4, -2.6, 'steel_light', rot=('x', 22.5, (8, 7.4, -2.8)), name='trigger')
    # carry handle / optic rail
    box(p, 7.4, 11.4, -5.5, 8.6, 12.4, -4.5, 'polymer_dark', name='rail_post_f')
    box(p, 7.4, 11.4, 2.5, 8.6, 12.4, 3.5, 'polymer_dark', name='rail_post_r')
    rail(p, 7.3, 8.7, 12.4, -6.0, 4.0)
    box(p, 7.2, 13.2, 2.0, 8.8, 14.2, 3.4, 'steel_dark', name='rear_sight')
    box(p, 7.75, 13.6, 2.4, 8.25, 14.1, 2.9, 'black', name='aperture')
    box(p, 7.6, 13.2, -5.4, 8.4, 14.4, -4.6, 'steel_dark', name='front_sight')
    box(p, 7.88, 14.3, -5.2, 8.12, 14.8, -4.8, 'tritium', name='front_dot')
    # short handguard, vents, barrel and brake
    box(p, 6.95, 8.0, -12.0, 9.05, 11.2, -6.0, 'polymer', name='handguard')
    for z in (-11.0, -9.4, -7.8):
        box(p, 6.8, 8.6, z, 6.95, 10.6, z + 0.9, 'black', name='vent_l')
        box(p, 9.05, 8.6, z, 9.2, 10.6, z + 0.9, 'black', name='vent_r')
    box(p, 7.4, 7.4, -11.0, 8.6, 8.0, -7.0, 'steel_dark', name='bottom_rail')
    tube(p, cx, 10.2, -16.0, -12.0, 0.6, 'steel_mid', name='barrel')
    tube(p, cx, 10.2, -17.4, -15.8, 0.85, 'steel_dark', name='brake')
    box(p, 7.2, 10.9, -17.2, 8.8, 11.1, -16.0, 'black', name='brake_slot')
    box(p, 6.6, 9.4, 1.0, 6.9, 10.0, 4.0, 'steel_mid', name='charging_handle')
    return p, dict(muzzle=(8, 10.2, -17.4), sight_y=14.8, length=30)


def uc45_smg():
    p = []
    cx = 8
    box(p, 6.9, 8.0, -7.0, 9.1, 11.6, 6.0, 'polymer_dark', name='receiver')
    box(p, 7.0, 11.6, -6.0, 9.0, 12.1, 5.0, 'polymer', name='top_cover')
    for z in (-5.0, -3.0, -1.0, 1.0, 3.0):
        box(p, 6.8, 8.6, z, 6.9, 11.0, z + 0.4, 'black', name='groove_l')
        box(p, 9.1, 8.6, z, 9.2, 11.0, z + 0.4, 'black', name='groove_r')
    box(p, 7.0, 6.4, -2.0, 9.0, 8.0, 5.0, 'polymer', name='lower')
    box(p, 7.1, -1.5, -1.4, 8.9, 6.4, 1.0, 'polymer', name='magazine')
    box(p, 7.0, -2.0, -1.6, 9.0, -1.3, 1.2, 'polymer_dark', name='mag_base')
    box(p, 7.15, 2.6, 2.6, 8.85, 6.4, 4.6, 'polymer', rot=('x', -22.5, (8, 6.4, 3.6)), name='grip')
    box(p, 7.6, 5.4, -1.2, 8.4, 5.8, 2.4, 'polymer', name='trigger_guard')
    box(p, 7.6, 5.4, -1.2, 8.4, 6.4, -0.8, 'polymer', name='guard_front')
    box(p, 7.8, 5.6, 0.8, 8.2, 6.6, 1.2, 'steel_light', rot=('x', 22.5, (8, 6.4, 1.0)), name='trigger')
    box(p, 9.1, 9.2, -4.0, 9.35, 10.6, -1.0, 'black', name='ejection_port')
    box(p, 6.5, 9.6, -5.6, 6.9, 10.4, -3.6, 'steel_mid', name='charging_handle')
    rail(p, 7.3, 8.7, 12.1, -7.0, 5.0)
    box(p, 7.2, 12.9, 2.8, 8.8, 14.0, 3.8, 'steel_dark', name='rear_sight')
    box(p, 7.75, 13.4, 3.1, 8.25, 13.9, 3.5, 'black', name='aperture')
    box(p, 7.6, 12.9, -6.6, 8.4, 13.9, -5.8, 'steel_dark', name='front_sight')
    box(p, 7.88, 13.8, -6.4, 8.12, 14.2, -6.0, 'steel_light', name='front_post')
    box(p, 7.2, 6.8, -7.0, 8.8, 8.0, -3.0, 'polymer_dark', name='handstop')
    tube(p, cx, 10.1, -13.0, -7.0, 0.65, 'steel_mid', name='barrel')
    tube(p, cx, 10.1, -14.6, -12.8, 0.9, 'steel_dark', name='flash_hider')
    box(p, 7.3, 10.8, -14.4, 8.7, 11.0, -13.0, 'black', name='hider_slot')
    # folding stock, extended
    box(p, 7.4, 9.4, 6.0, 8.6, 10.4, 13.0, 'steel_mid', name='stock_bar')
    box(p, 7.2, 7.4, 12.4, 8.8, 12.0, 14.0, 'polymer', name='butt')
    box(p, 7.1, 7.2, 14.0, 8.9, 12.2, 14.5, 'rubber', name='butt_pad')
    return p, dict(muzzle=(8, 10.1, -14.6), sight_y=14.2, length=29)


def br20_dmr():
    p = []
    cx = 8
    box(p, 6.9, 8.6, -8.0, 9.1, 11.6, 6.0, 'steel_dark', name='receiver')
    box(p, 7.0, 7.2, -4.0, 9.0, 8.6, 6.0, 'steel_dark', name='lower')
    box(p, 7.2, 1.0, -3.0, 8.8, 7.2, -0.2, 'steel_mid', rot=('x', -22.5, (8, 7.2, -1.6)), name='magazine')
    box(p, 7.15, 2.8, 3.4, 8.85, 7.2, 5.4, 'polymer', rot=('x', -22.5, (8, 7.2, 4.4)), name='grip')
    box(p, 7.6, 5.8, 0.4, 8.4, 6.2, 3.6, 'steel_dark', name='trigger_guard')
    box(p, 7.6, 5.8, 0.4, 8.4, 7.2, 0.8, 'steel_dark', name='guard_front')
    box(p, 7.8, 6.0, 1.8, 8.2, 7.2, 2.2, 'steel_light', rot=('x', 22.5, (8, 7.2, 2.0)), name='trigger')
    box(p, 9.1, 9.4, -4.5, 9.35, 10.8, -1.0, 'black', name='ejection_port')
    box(p, 9.1, 10.0, 0.5, 10.2, 10.8, 2.5, 'steel_light', name='charging_handle')
    # stock with cheek riser and adjustable butt
    box(p, 7.2, 8.4, 6.0, 8.8, 11.0, 8.0, 'steel_mid', name='stock_hinge')
    box(p, 7.0, 7.0, 8.0, 9.0, 11.6, 15.0, 'polymer_dark', name='stock')
    box(p, 7.2, 11.6, 9.0, 8.8, 12.6, 14.0, 'polymer', name='cheek_riser')
    box(p, 6.9, 6.6, 15.0, 9.1, 12.0, 15.9, 'rubber', name='butt_pad')
    box(p, 7.4, 5.6, 11.0, 8.6, 7.0, 13.5, 'polymer_dark', name='stock_hook')
    # long free-float handguard with slots, long barrel, brake
    box(p, 6.95, 8.3, -22.0, 9.05, 11.5, -8.0, 'polymer_dark', name='handguard')
    for z in (-20.5, -18.0, -15.5, -13.0, -10.5):
        box(p, 6.8, 9.0, z, 6.95, 10.8, z + 1.2, 'black', name='slot_l')
        box(p, 9.05, 9.0, z, 9.2, 10.8, z + 1.2, 'black', name='slot_r')
        box(p, 7.5, 8.15, z, 8.5, 8.3, z + 1.2, 'black', name='slot_b')
    tube(p, cx, 10.4, -31.0, -22.0, 0.7, 'steel_mid', name='barrel')
    tube(p, cx, 10.4, -33.0, -30.8, 1.0, 'steel_dark', name='brake')
    for z in (-32.6, -31.8):
        box(p, 6.85, 10.0, z, 7.05, 10.8, z + 0.4, 'black', name='port_l')
        box(p, 8.95, 10.0, z, 9.15, 10.8, z + 0.4, 'black', name='port_r')
    # low-power scope on the receiver rail
    rail(p, 7.3, 8.7, 11.6, -8.0, 5.0)
    for z in (-5.0, 1.5):
        box(p, 7.2, 12.5, z, 8.8, 13.7, z + 1.2, 'steel_dark', name='ring_base')
        box(p, 7.15, 13.6, z - 0.1, 8.85, 15.9, z + 1.3, 'steel_dark', name='ring')
    tube(p, cx, 14.8, -4.0, 2.5, 1.0, 'gunmetal', name='scope_tube')
    tube(p, cx, 14.8, -8.6, -4.0, 1.3, 'gunmetal', name='objective')
    tube(p, cx, 14.8, 2.5, 5.6, 1.2, 'gunmetal', name='ocular')
    box(p, 6.95, 13.75, -8.8, 9.05, 15.85, -8.55, 'lens', name='objective_lens')
    box(p, 7.05, 13.85, 5.6, 8.95, 15.75, 5.85, 'glass', name='ocular_lens')
    box(p, 7.5, 15.6, -1.5, 8.5, 16.6, -0.3, 'steel_dark', name='elevation')
    box(p, 8.9, 14.3, -1.5, 9.9, 15.3, -0.3, 'steel_dark', name='windage')
    # folded bipod
    box(p, 6.7, 7.5, -20.0, 7.2, 8.15, -9.5, 'steel_dark', name='bipod_l')
    box(p, 8.8, 7.5, -20.0, 9.3, 8.15, -9.5, 'steel_dark', name='bipod_r')
    return p, dict(muzzle=(8, 10.4, -33.0), sight_y=14.8, length=49)


def gl40_launcher():
    p = []
    cx = 8
    # six-shot revolving launcher: big cylinder, fat barrel, frame, folding stock
    tube(p, cx, 10.0, -4.0, 4.0, 3.0, 'steel_dark', name='cylinder')
    for ang in range(6):
        import math as _m
        a = _m.radians(ang * 60 + 30)
        ox, oy = _m.cos(a) * 1.9, _m.sin(a) * 1.9
        box(p, cx + ox - 0.7, 10.0 + oy - 0.7, -4.2, cx + ox + 0.7, 10.0 + oy + 0.7, -3.8, 'black', name='chamber')
    box(p, 7.0, 6.5, 4.0, 9.0, 13.2, 6.0, 'steel_dark', name='breech_plate')
    box(p, 7.0, 6.6, -5.5, 9.0, 8.0, 4.0, 'steel_dark', name='frame_bottom')
    tube(p, cx, 10.0, -12.0, -4.0, 1.6, 'steel_mid', name='barrel')
    box(p, 7.2, 11.6, -11.8, 8.8, 12.4, -4.5, 'steel_dark', name='barrel_rib')
    box(p, 7.4, 8.0, -12.0, 8.6, 8.4, -4.0, 'steel_mid', name='barrel_lug')
    box(p, 7.15, 2.6, 2.2, 8.85, 6.6, 4.2, 'polymer', rot=('x', -22.5, (8, 6.6, 3.2)), name='grip')
    box(p, 7.6, 5.4, -1.8, 8.4, 5.8, 1.8, 'steel_dark', name='trigger_guard')
    box(p, 7.6, 5.4, -1.8, 8.4, 6.6, -1.4, 'steel_dark', name='guard_front')
    box(p, 7.8, 5.6, 0.2, 8.2, 6.6, 0.6, 'steel_light', rot=('x', 22.5, (8, 6.6, 0.4)), name='trigger')
    box(p, 7.0, 6.8, -11.5, 9.0, 8.0, -7.0, 'polymer', name='foregrip')
    # ladder sight and rear sight
    box(p, 7.3, 12.4, -11.0, 8.7, 12.9, -9.6, 'steel_dark', name='sight_base')
    box(p, 7.4, 12.9, -10.6, 8.6, 15.4, -10.2, 'steel_dark', rot=('x', -22.5, (8, 12.9, -10.4)), name='ladder')
    for y in (13.4, 14.0, 14.6):
        box(p, 7.5, y, -10.7, 8.5, y + 0.2, -10.1, 'black', rot=('x', -22.5, (8, 12.9, -10.4)), name='ladder_rung')
    box(p, 7.4, 13.2, 4.8, 8.6, 14.4, 5.8, 'steel_dark', name='rear_sight')
    box(p, 7.8, 13.6, 5.1, 8.2, 14.1, 5.5, 'black', name='aperture')
    # stock
    box(p, 7.4, 9.4, 6.0, 8.6, 10.4, 14.0, 'steel_mid', name='stock_bar')
    box(p, 7.2, 7.4, 13.4, 8.8, 12.2, 15.0, 'polymer', name='butt')
    box(p, 7.1, 7.2, 15.0, 8.9, 12.4, 15.5, 'rubber', name='butt_pad')
    return p, dict(muzzle=(8, 10.0, -12.0), sight_y=14.1, length=28)


GUNS = [
    ('p9_sidearm', p9_sidearm, 'P9 Sidearm', 0.95),
    ('mp9_smg', mp9_smg, 'MP9 Submachine Gun', 0.55),
    ('fl4_carbine', fl4_carbine, 'FL-4 Carbine', 0.44),
    ('vk47_rifle', vk47_rifle, 'VK-47 Battle Rifle', 0.40),
    ('sg12_shotgun', sg12_shotgun, 'SG-12 Pump Shotgun', 0.38),
    ('r7_marksman', r7_marksman, 'R7 Marksman Rifle', 0.33),
    ('lm249_lmg', lm249_lmg, 'LM-249 Light Machine Gun', 0.35),
    ('r6_revolver', r6_revolver, 'R6 Revolver', 0.85),
    ('bp5_bullpup', bp5_bullpup, 'BP-5 Bullpup Carbine', 0.5),
    ('uc45_smg', uc45_smg, 'UC-45 Submachine Gun', 0.52),
    ('br20_dmr', br20_dmr, 'BR-20 Marksman Rifle', 0.34),
    ('gl40_launcher', gl40_launcher, 'GL-40 Grenade Launcher', 0.55),
]

# A gun's length along Z spans more than 16 units; item models allow -16..32, so everything stays inside that.


def remap(parts, meta):
    """Item models only allow coordinates in [-16, 32]. Slide (and if needed squeeze) the gun along Z to fit, and
    report where the grip ended up so the renderers can put it in the hand."""
    zs = [v for el in parts for k in ('from', 'to') for v in (el[k][2],)]
    zmin, zmax = min(zs), max(zs)
    span = zmax - zmin
    k = min(1.0, 46.0 / span)
    off = -15.0 - zmin * k

    def tz(z):
        return z * k + off

    for el in parts:
        el['from'][2] = round(tz(el['from'][2]), 3)
        el['to'][2] = round(tz(el['to'][2]), 3)
        if 'rotation' in el:
            o = el['rotation']['origin']
            o[2] = round(tz(o[2]), 3)
    grip = next((el for el in parts if el.get('name') == 'grip'), None)
    grip_z = (grip['from'][2] + grip['to'][2]) / 2 if grip else 8.0
    grip_y = (grip['from'][1] + grip['to'][1]) / 2 if grip else 6.0
    mx, my, mz = meta['muzzle']
    meta['muzzle'] = (mx, my, round(tz(mz), 3))
    meta['grip'] = (round(grip_y, 3), round(grip_z, 3))
    meta['k'] = k
    # the full extent along Z after the remap, for the preview camera
    meta['z_range'] = (round(tz(zmin), 3), round(tz(zmax), 3))


def write_model(gid, parts, gui_scale, meta):
    remap(parts, meta)
    for el in parts:
        for k in ('from', 'to'):
            for v in el[k]:
                assert -16 <= v <= 32, (gid, el.get('name'), el[k])
    clean = []
    for el in parts:
        e = {k: v for k, v in el.items() if not k.startswith('_')}
        clean.append(e)
    gy, gz = meta['grip']
    tp = 0.45
    # bring the grip to the hand pivot in third person (translation is in 1/16 block, applied before the scale)
    tz_ = -(gz - 8.0) * tp
    ty_ = -(gy - 8.0) * tp + 0.5
    model = {
        'credit': 'Frontline - generated by gen/gen_guns.py',
        'textures': {'a': 'frontline:item/gun_atlas', 'particle': 'frontline:item/gun_atlas'},
        'elements': clean,
        'display': {
            # first person is positioned entirely by FirstPersonGun.java; keep the transform neutral
            'firstperson_righthand': {'rotation': [0, 0, 0], 'translation': [0, 0, 0], 'scale': [1, 1, 1]},
            'firstperson_lefthand': {'rotation': [0, 0, 0], 'translation': [0, 0, 0], 'scale': [1, 1, 1]},
            # third person: the barrel (-Z) runs along the raised arm; the grip sits in the hand
            'thirdperson_righthand': {'rotation': [0, 0, 0], 'translation': [-0.5, round(ty_, 2), round(tz_, 2)], 'scale': [tp, tp, tp]},
            'thirdperson_lefthand': {'rotation': [0, 0, 0], 'translation': [0.5, round(ty_, 2), round(tz_, 2)], 'scale': [tp, tp, tp]},
            'gui': {'rotation': [20, -90, 0], 'translation': [round(-(8.0 - (meta['z_range'][0] + meta['z_range'][1]) / 2) * gui_scale * 0.0, 2), 0.6, 0], 'scale': [gui_scale, gui_scale, gui_scale]},
            'ground': {'rotation': [0, 0, 0], 'translation': [0, 2, 0], 'scale': [0.4, 0.4, 0.4]},
            'fixed': {'rotation': [0, -90, 0], 'translation': [0, 0, 0], 'scale': [gui_scale, gui_scale, gui_scale]},
            'head': {'rotation': [0, 0, 0], 'translation': [0, 0, 0], 'scale': [0.5, 0.5, 0.5]},
        },
    }
    # centre the gun in the GUI slot: after the Y-90 rotation, model Z runs along screen X
    zc = (meta['z_range'][0] + meta['z_range'][1]) / 2
    model['display']['gui']['translation'] = [round((zc - 8.0) * gui_scale * -1.0, 2), 0.6, 0]
    model['display']['fixed']['translation'] = [round((zc - 8.0) * gui_scale * -1.0, 2), 0, 0]
    os.makedirs(MODELS, exist_ok=True)
    with open(os.path.join(MODELS, f'{gid}.json'), 'w') as f:
        json.dump(model, f, separators=(',', ':'))
    return model


def main():
    build_atlas()
    spec = {'mats': {m[0]: m[1] for m in MATS}, 'guns': []}
    for gid, fn, name, gui_scale in GUNS:
        parts, meta = fn()
        write_model(gid, parts, gui_scale, meta)
        spec['guns'].append({'id': gid, 'name': name, 'gui_scale': gui_scale, 'muzzle': meta['muzzle'], 'sight_y': meta['sight_y'],
                             'length': meta['length'], 'grip': meta['grip'], 'z_range': meta['z_range'], 'parts': [{'from': e['from'], 'to': e['to'], 'mat': e['_mat'], 'rot': e.get('rotation'), 'name': e.get('name', '')} for e in parts]})
        print(f"{gid:14s} parts={len(parts):3d} muzzle={meta['muzzle']} sightY={meta['sight_y']} grip={meta['grip']} z={meta['z_range']} k={meta['k']:.3f}")
    with open(os.path.join(os.path.dirname(__file__), 'guns_spec.json'), 'w') as f:
        json.dump(spec, f)


if __name__ == '__main__':
    main()
