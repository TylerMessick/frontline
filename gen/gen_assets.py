#!/usr/bin/env python3
"""Draws every non-gun texture for Frontline: the soldier skin, flag cloth, scope mask, particle glow, ammo boxes,
enlistment papers, command baton, gun parts; plus the Command Flag block model/blockstate and the particle JSONs.
Run from the repo root: python3 gen/gen_assets.py
"""
import json
import math
import os
import random

from PIL import Image, ImageDraw

ROOT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'frontline')
TEX = os.path.join(ROOT, 'textures')
random.seed(3)


def ensure(p):
    os.makedirs(p, exist_ok=True)
    return p


def rgb(h, a=255):
    return ((h >> 16) & 255, (h >> 8) & 255, h & 255, a)


def jitter(c, n):
    d = random.randint(-n, n)
    return (max(0, min(255, c[0] + d)), max(0, min(255, c[1] + d)), max(0, min(255, c[2] + d)), c[3])


def fill(img, x0, y0, x1, y1, color, noise=0):
    for y in range(y0, y1):
        for x in range(x0, x1):
            img.putpixel((x, y), jitter(color, noise) if noise else color)


def camo(img, x0, y0, x1, y1, base, spots, noise=6):
    fill(img, x0, y0, x1, y1, base, noise)
    for _ in range(((x1 - x0) * (y1 - y0)) // 6):
        cx, cy = random.randint(x0, x1 - 1), random.randint(y0, y1 - 1)
        c = random.choice(spots)
        for dx in range(-1, 2):
            for dy in range(-1, 2):
                if x0 <= cx + dx < x1 and y0 <= cy + dy < y1 and random.random() < 0.7:
                    img.putpixel((cx + dx, cy + dy), jitter(c, 4))


# ---------------------------------------------------------------- soldier skin (128x64; standard humanoid layout + gear at x >= 64)
def soldier():
    img = Image.new('RGBA', (128, 64), (0, 0, 0, 0))
    skin = rgb(0xC98E6A)
    skin_d = rgb(0xA8714F)
    olive = rgb(0x5A6239)
    olive_d = rgb(0x474E2C)
    khaki = rgb(0x7A7452)
    brown = rgb(0x4A3524)
    black = rgb(0x1E1E1E)
    helmet = rgb(0x3F4630)

    # head: 8x8 faces. layout: top(8,0) bottom(16,0) right(0,8) front(8,8) left(16,8) back(24,8)
    for (x, y) in ((8, 0), (16, 0), (0, 8), (8, 8), (16, 8), (24, 8)):
        fill(img, x, y, x + 8, y + 8, skin, 5)
    # face
    fill(img, 10, 12, 12, 13, rgb(0xFFFFFF)); fill(img, 11, 12, 12, 13, rgb(0x2C3E63))
    fill(img, 12, 12, 14, 13, rgb(0xFFFFFF)); fill(img, 12, 12, 13, 13, rgb(0x2C3E63))
    fill(img, 11, 15, 13, 16, skin_d)  # mouth
    fill(img, 11, 13, 13, 14, jitter(skin_d, 3))  # nose shade
    fill(img, 8, 8, 16, 10, rgb(0x3A2A1C), 6)  # hairline peeking under helmet
    fill(img, 0, 8, 8, 10, rgb(0x3A2A1C), 6); fill(img, 16, 8, 24, 10, rgb(0x3A2A1C), 6); fill(img, 24, 8, 32, 10, rgb(0x3A2A1C), 6)
    # hat layer = helmet. layout: top(40,0) bottom(48,0) right(32,8) front(40,8) left(48,8) back(56,8)
    camo(img, 40, 0, 48, 8, helmet, [olive_d, rgb(0x2E3322), khaki])
    for (x, y) in ((32, 8), (40, 8), (48, 8), (56, 8)):
        camo(img, x, y, x + 8, y + 5, helmet, [olive_d, rgb(0x2E3322), khaki])  # sides down to the brow
        fill(img, x, y + 4, x + 8, y + 5, rgb(0x2A2E1F))  # dark rim line
    # chin strap on the sides
    fill(img, 34, 13, 35, 16, black); fill(img, 53, 13, 54, 16, black)

    # body 8x12x4: top(20,16) bottom(28,16) right(16,20) front(20,20) left(28,20) back(32,20)
    fill(img, 20, 16, 28, 20, olive, 4); fill(img, 28, 16, 36, 20, olive_d, 4)
    for (x, w) in ((16, 4), (20, 8), (28, 4), (32, 8)):
        fill(img, x, 20, x + w, 32, olive, 5)
    fill(img, 24, 20, 25, 32, olive_d)  # shirt seam
    for y in (22, 25, 28): fill(img, 23, y, 24, y + 1, rgb(0x8C8C7A))  # buttons
    fill(img, 20, 20, 28, 21, olive_d)  # collar

    # arms 4x12x4 right: top(44,16) bottom(48,16) right(40,20) front(44,20) left(48,20) back(52,20); left arm at (32,48)
    for base in ((40, 16), (32, 48)):
        bx, by = base
        fill(img, bx + 4, by, bx + 8, by + 4, olive, 4)
        fill(img, bx + 8, by, bx + 12, by + 4, skin, 4)
        for x in (bx, bx + 4, bx + 8, bx + 12):
            fill(img, x, by + 4, x + 4, by + 13, olive, 5)  # sleeve
            fill(img, x, by + 13, x + 4, by + 16, skin, 5)  # hand
            fill(img, x, by + 12, x + 4, by + 13, olive_d)  # cuff
    # legs 4x12x4 right: top(4,16) bottom(8,16) right(0,20) front(4,20) left(8,20) back(12,20); left leg at (16,48)
    for base in ((0, 16), (16, 48)):
        bx, by = base
        fill(img, bx + 4, by, bx + 8, by + 4, olive_d, 4)
        fill(img, bx + 8, by, bx + 12, by + 4, brown, 4)
        for x in (bx, bx + 4, bx + 8, bx + 12):
            fill(img, x, by + 4, x + 4, by + 16, olive_d, 5)
            fill(img, x + 1, by + 8, x + 3, by + 11, jitter(olive_d, 10))  # cargo pocket

    # gear (x >= 64). vest 9x7.5x5 at (64,0): top(69,0) bottom(78,0) right(64,5) front(69,5) left(78,5) back(83,5)
    camo(img, 64, 0, 92, 13, khaki, [rgb(0x6A6446), rgb(0x8A8260)], 4)
    fill(img, 69, 5, 78, 6, rgb(0x5A553A))  # vest top edge
    fill(img, 72, 6, 75, 12, rgb(0x5A553A))  # front zip/plate line
    # belt at (64,14): 8.8x2x4.8
    fill(img, 64, 14, 91, 21, brown, 5)
    fill(img, 72, 16, 76, 19, rgb(0x9A8A4A))  # buckle
    # pouches at (92,0) and (92,5)
    fill(img, 92, 0, 100, 4, khaki, 5); fill(img, 92, 5, 100, 9, khaki, 5)
    fill(img, 93, 1, 99, 2, rgb(0x5A553A)); fill(img, 93, 6, 99, 7, rgb(0x5A553A))
    # helmet rim at (64,22): 10x1x10
    fill(img, 64, 22, 104, 33, rgb(0x2E3322), 4)
    # pack at (64,34): 6x6x3
    camo(img, 64, 34, 82, 43, olive_d, [rgb(0x3E4428), khaki], 4)
    # boots at (84,34): 5x4x5
    fill(img, 84, 34, 104, 43, rgb(0x2A2017), 4)
    fill(img, 84, 38, 104, 39, rgb(0x1A1410))  # sole line
    # knee pads at (104,22)
    fill(img, 104, 22, 116, 26, black, 3)
    # armband at (104,28) - white so the tint shows the nation colour
    fill(img, 104, 28, 122, 35, rgb(0xF2F2F2), 3)
    img.save(os.path.join(ensure(os.path.join(TEX, 'entity')), 'soldier.png'))


# ---------------------------------------------------------------- flag (64x64): pole wood, brass finial, white cloth that gets tinted
def flag():
    img = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
    fill(img, 0, 0, 8, 40, rgb(0x6B4A2B), 10)  # pole
    fill(img, 8, 0, 20, 6, rgb(0xC9A227), 12)  # finial
    fill(img, 8, 6, 12, 9, rgb(0xC9A227), 12)
    # cloth: 16 x 12 for strips (texOffs 16..32, 16..28) and bands (16..32, 28..32)
    for y in range(16, 28):
        for x in range(16, 40):
            v = 232 + random.randint(-14, 8)
            img.putpixel((x, y), (v, v, v, 255))
    for y in range(28, 36):
        for x in range(16, 40):
            v = 245 + random.randint(-8, 6)
            img.putpixel((x, y), (v, v, v, 255))
    img.save(os.path.join(TEX, 'entity', 'flag.png'))


# ---------------------------------------------------------------- scope mask (256x256): black with a transparent circle and a soft edge
def scope():
    img = Image.new('RGBA', (256, 256), (0, 0, 0, 255))
    cx = cy = 128
    for y in range(256):
        for x in range(256):
            d = math.hypot(x - cx + 0.5, y - cy + 0.5)
            if d < 118:
                a = 0
            elif d < 124:
                a = int(255 * (d - 118) / 6)
            else:
                a = 255
            # faint lens tint and a vignette inside the glass
            if d < 118:
                tint = int(40 * (d / 118) ** 3)
                img.putpixel((x, y), (0, 0, 0, tint))
            else:
                img.putpixel((x, y), (0, 0, 0, a))
    # thin scope-body ring
    for y in range(256):
        for x in range(256):
            d = math.hypot(x - cx + 0.5, y - cy + 0.5)
            if 118 <= d < 121:
                img.putpixel((x, y), (18, 18, 20, 255))
    img.save(os.path.join(ensure(os.path.join(TEX, 'gui')), 'scope.png'))


# ---------------------------------------------------------------- particle glow (16x16 soft disc)
def glow():
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5) / 7.5
            a = max(0.0, 1.0 - d ** 1.6)
            img.putpixel((x, y), (255, 255, 255, int(255 * a)))
    img.save(os.path.join(ensure(os.path.join(TEX, 'particle')), 'glow.png'))


# ---------------------------------------------------------------- 16x16 item icons
def ammo_box(name, label_color, tip_color, count):
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    # cardboard box
    fill(img, 2, 5, 14, 15, rgb(0x8B6B3E), 8)
    fill(img, 2, 5, 14, 6, rgb(0xA6834E)); fill(img, 2, 14, 14, 15, rgb(0x6B5030))
    fill(img, 2, 5, 3, 15, rgb(0xA6834E)); fill(img, 13, 5, 14, 15, rgb(0x6B5030))
    # label stripe
    fill(img, 3, 9, 13, 12, label_color)
    fill(img, 4, 10, 12, 11, rgb(0xF0F0F0))
    # cartridges standing in the open top
    for i in range(count):
        x = 3 + i * (10 // count) + 1
        fill(img, x, 2, x + 1, 6, rgb(0xB08A3A))
        fill(img, x, 1, x + 1, 2, tip_color)
    img.save(os.path.join(ensure(os.path.join(TEX, 'item')), f'{name}.png'))


def papers():
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    fill(img, 3, 1, 13, 15, rgb(0xEDE4C8), 4)
    fill(img, 3, 1, 13, 2, rgb(0xFFFBEA)); fill(img, 12, 1, 13, 15, rgb(0xC9BE9C)); fill(img, 3, 14, 13, 15, rgb(0xC9BE9C))
    for y in (4, 6, 8, 10):
        fill(img, 5, y, 11 if y != 10 else 8, y + 1, rgb(0x5A5A6A))
    # red wax seal and a stamp
    fill(img, 9, 11, 12, 14, rgb(0xA3242A)); fill(img, 10, 12, 11, 13, rgb(0xD0484E))
    fill(img, 4, 2, 7, 3, rgb(0x2C5A8A))
    img.save(os.path.join(TEX, 'item', 'enlistment_papers.png'))


def baton():
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for i in range(12):
        x, y = 2 + i, 13 - i
        fill(img, x, y, x + 2, y + 2, rgb(0x3B2A1C) if i > 3 else rgb(0xC9A227), 6)
    fill(img, 13, 1, 15, 3, rgb(0xC9A227)); fill(img, 14, 1, 15, 2, rgb(0xE8CC66))
    fill(img, 1, 13, 3, 15, rgb(0xE8CC66)); fill(img, 2, 14, 3, 15, rgb(0xC9A227))
    img.save(os.path.join(TEX, 'item', 'command_baton.png'))


def gun_parts():
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    fill(img, 1, 6, 13, 8, rgb(0x45484E), 6)  # barrel
    fill(img, 9, 8, 12, 13, rgb(0x2B2D31), 5)  # grip
    fill(img, 3, 3, 9, 6, rgb(0x2B2D31), 5)  # receiver block
    fill(img, 11, 2, 15, 4, rgb(0x6B6F76), 5)  # spring
    for x in range(11, 15): img.putpixel((x, 3 if x % 2 else 2), rgb(0x9A9EA6))
    fill(img, 1, 10, 6, 12, rgb(0xB08A3A), 6)  # brass
    fill(img, 2, 13, 8, 14, rgb(0x6B6F76), 5)  # pin
    img.save(os.path.join(TEX, 'item', 'gun_parts.png'))


def flag_item():
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    fill(img, 3, 1, 5, 15, rgb(0x6B4A2B), 8)
    fill(img, 3, 0, 5, 1, rgb(0xC9A227))
    for y in range(2, 9):
        w = 9 - (y - 2) // 3
        fill(img, 5, y, 5 + w, y + 1, rgb(0x8A1C1C), 10)
    fill(img, 5, 4, 12, 6, rgb(0xD04040))
    img.save(os.path.join(TEX, 'item', 'command_flag.png'))


def flag_block_tex():
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    fill(img, 0, 0, 16, 16, rgb(0x6B4A2B), 10)
    for y in range(16):
        if y % 4 == 0: fill(img, 0, y, 16, y + 1, rgb(0x5A3D22))
    img.save(os.path.join(ensure(os.path.join(TEX, 'block')), 'flag_pole.png'))
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    fill(img, 0, 0, 16, 16, rgb(0x3A3A3E), 8)
    img.save(os.path.join(TEX, 'block', 'flag_base.png'))


# ---------------------------------------------------------------- block model, blockstate, item models, particles
def models():
    mdl = ensure(os.path.join(ROOT, 'models'))
    blk = ensure(os.path.join(mdl, 'block'))
    itm = ensure(os.path.join(mdl, 'item'))
    bs = ensure(os.path.join(ROOT, 'blockstates'))
    flag_model = {
        'textures': {'pole': 'frontline:block/flag_pole', 'base': 'frontline:block/flag_base', 'particle': 'frontline:block/flag_pole'},
        'elements': [
            {'from': [5, 0, 5], 'to': [11, 2, 11], 'faces': {f: {'uv': [0, 0, 6, 6 if f in ('up', 'down') else 2], 'texture': '#base'} for f in ('north', 'south', 'east', 'west', 'up', 'down')}},
            {'from': [6.5, 2, 6.5], 'to': [9.5, 3, 9.5], 'faces': {f: {'uv': [0, 0, 3, 3 if f in ('up', 'down') else 1], 'texture': '#base'} for f in ('north', 'south', 'east', 'west', 'up', 'down')}},
            {'from': [7, 3, 7], 'to': [9, 16, 9], 'faces': {f: {'uv': [0, 0, 2, 2 if f in ('up', 'down') else 13], 'texture': '#pole'} for f in ('north', 'south', 'east', 'west', 'up', 'down')}},
        ],
    }
    json.dump(flag_model, open(os.path.join(blk, 'command_flag.json'), 'w'))
    json.dump({'variants': {'facing=north': {'model': 'frontline:block/command_flag'}, 'facing=south': {'model': 'frontline:block/command_flag', 'y': 180},
                            'facing=west': {'model': 'frontline:block/command_flag', 'y': 270}, 'facing=east': {'model': 'frontline:block/command_flag', 'y': 90}}},
              open(os.path.join(bs, 'command_flag.json'), 'w'))
    for name in ('ammo_9mm', 'ammo_556', 'ammo_762', 'ammo_12g', 'ammo_308', 'ammo_45', 'ammo_44', 'ammo_40mm', 'enlistment_papers', 'command_baton', 'gun_parts', 'command_flag'):
        json.dump({'parent': 'minecraft:item/generated', 'textures': {'layer0': f'frontline:item/{name}'}}, open(os.path.join(itm, f'{name}.json'), 'w'))
    part = ensure(os.path.join(ROOT, 'particles'))
    for name in ('muzzle_flash', 'gun_smoke', 'brass', 'spark'):
        json.dump({'textures': ['frontline:glow']}, open(os.path.join(part, f'{name}.json'), 'w'))


# ---------------------------------------------------------------- tactical armor (vanilla armor layers, 64x32)
def armor():
    out = ensure(os.path.join(TEX, 'models', 'armor'))
    camo_base, camo_spots = rgb(0x5A6239), [rgb(0x474E2C), rgb(0x7A7452), rgb(0x3A3F26)]
    dark, black, khaki = rgb(0x2E3322), rgb(0x1A1A1C), rgb(0x7A7452)
    boot, lens = rgb(0x2A2017), rgb(0x3CFF6A)

    def faces(img, u, v, w, h, d, rows, painter):
        """Paint the given rows (0..h) of every side face of a cube unwrapped at (u, v)."""
        for fx, fw in ((u, d), (u + d, w), (u + d + w, d), (u + 2 * d + w, w)):
            painter(img, fx, v + d + rows[0], fx + fw, v + d + rows[1])

    def cam(img, x0, y0, x1, y1):
        camo(img, x0, y0, x1, y1, camo_base, camo_spots, 5)

    for variant in ('tactical', 'tactical_nvg'):
        l1 = Image.new('RGBA', (64, 32), (0, 0, 0, 0))
        # helmet: crown, sides down to the ear, a short brim at the front, dark rim line
        cam(l1, 8, 0, 16, 8)
        faces(l1, 0, 0, 8, 8, 8, (0, 4), cam)
        for fx in (0, 8, 16, 24):
            fill(l1, fx, 12, fx + 8, 13, dark)
        fill(l1, 0, 13, 2, 16, black); fill(l1, 22, 13, 24, 16, black)      # chin strap
        if variant == 'tactical_nvg':
            # goggles on the outer (hat) layer so they stand proud of the face; mount on the brow
            fill(l1, 40 + 0, 8 + 3, 40 + 8, 8 + 6, rgb(0x26282C), 3)
            fill(l1, 40 + 1, 8 + 4, 40 + 3, 8 + 6, lens); fill(l1, 40 + 5, 8 + 4, 40 + 7, 8 + 6, lens)
            fill(l1, 40 + 1, 8 + 4, 40 + 2, 8 + 5, rgb(0xB8FFC8)); fill(l1, 40 + 5, 8 + 4, 40 + 6, 8 + 5, rgb(0xB8FFC8))
            fill(l1, 40 + 3, 8 + 0, 40 + 5, 8 + 3, black)                   # mount arm
            fill(l1, 40 + 2, 8 + 0, 40 + 6, 8 + 1, rgb(0x45484E))
        # plate carrier (body): plates front and back, cummerbund sides, three mag pouches, shoulder straps
        cam(l1, 20, 16, 28, 20); cam(l1, 28, 16, 36, 20)
        faces(l1, 16, 16, 8, 12, 4, (0, 10), cam)
        fill(l1, 20, 20, 28, 21, dark); fill(l1, 32, 20, 40, 21, dark)
        fill(l1, 21, 21, 27, 22, khaki)                                     # name tape
        for i in range(3):
            fill(l1, 20 + 1 + i * 2 + (0 if i == 0 else 0), 25, 20 + 2 + i * 2, 29, khaki)
        fill(l1, 20, 25, 28, 26, dark)
        fill(l1, 22, 22, 26, 24, rgb(0x3A3F26))                              # admin pouch
        fill(l1, 33, 22, 39, 28, rgb(0x474E2C))                              # back panel
        fill(l1, 33, 22, 39, 23, black)                                      # drag handle
        # shoulder pads on the arms (upper four rows only)
        cam(l1, 44, 16, 48, 20)
        faces(l1, 40, 16, 4, 12, 4, (0, 4), cam)
        for fx in (40, 44, 48, 52):
            fill(l1, fx, 23, fx + 4, 24, dark)
        # boots (legs, lower four rows) with laces and soles
        faces(l1, 0, 16, 4, 12, 4, (8, 12), lambda im, a, b, c, d: fill(im, a, b, c, d, boot, 5))
        fill(l1, 4, 28, 8, 29, black)
        for y in (29, 30): fill(l1, 5, y, 7, y + 1, rgb(0x6B5A3E))
        for fx in (0, 4, 8, 12): fill(l1, fx, 31, fx + 4, 32, black)
        fill(l1, 8, 16, 12, 20, black)                                       # sole
        l1.save(os.path.join(out, f'{variant}_layer_1.png'))

        l2 = Image.new('RGBA', (64, 32), (0, 0, 0, 0))
        # combat pants: full legs, knee pads, cargo pockets, belt on the body
        cam(l2, 4, 16, 8, 20)
        faces(l2, 0, 16, 4, 12, 4, (0, 12), cam)
        fill(l2, 4, 24, 8, 26, black)                                        # knee pad front
        fill(l2, 0, 25, 4, 28, rgb(0x474E2C)); fill(l2, 8, 25, 12, 28, rgb(0x474E2C))   # cargo pockets
        faces(l2, 16, 16, 8, 12, 4, (8, 12), cam)
        faces(l2, 16, 16, 8, 12, 4, (9, 11), lambda im, a, b, c, d: fill(im, a, b, c, d, rgb(0x4A3524), 4))
        fill(l2, 23, 29, 25, 31, rgb(0x9A8A4A))                              # buckle
        l2.save(os.path.join(out, f'{variant}_layer_2.png'))

    itm = os.path.join(TEX, 'item')

    def icon(name, rects):
        img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
        for (x0, y0, x1, y1, c) in rects:
            if c == 'camo':
                camo(img, x0, y0, x1, y1, camo_base, camo_spots, 6)
            else:
                fill(img, x0, y0, x1, y1, c, 6)
        img.save(os.path.join(itm, f'{name}.png'))
        json.dump({'parent': 'minecraft:item/generated', 'textures': {'layer0': f'frontline:item/{name}'}},
                  open(os.path.join(ROOT, 'models', 'item', f'{name}.json'), 'w'))

    helmet = [(3, 4, 13, 10, 'camo'), (4, 3, 12, 4, 'camo'), (2, 9, 14, 11, dark), (3, 11, 4, 14, black), (12, 11, 13, 14, black)]
    icon('combat_helmet', helmet)
    icon('nvg_helmet', helmet + [(5, 1, 11, 3, black), (4, 8, 12, 11, rgb(0x26282C)), (5, 9, 7, 11, lens), (9, 9, 11, 11, lens)])
    icon('plate_carrier', [(3, 2, 13, 14, 'camo'), (3, 2, 5, 4, dark), (11, 2, 13, 4, dark), (5, 4, 11, 5, khaki),
                           (4, 9, 6, 13, khaki), (7, 9, 9, 13, khaki), (10, 9, 12, 13, khaki), (3, 13, 13, 14, dark)])
    icon('combat_pants', [(3, 1, 13, 4, rgb(0x4A3524)), (3, 3, 7, 15, 'camo'), (9, 3, 13, 15, 'camo'), (3, 8, 7, 10, black), (9, 8, 13, 10, black)])
    icon('combat_boots', [(2, 8, 7, 13, boot), (9, 8, 14, 13, boot), (1, 12, 7, 14, black), (9, 12, 15, 14, black), (3, 8, 6, 9, rgb(0x6B5A3E)), (10, 8, 13, 9, rgb(0x6B5A3E))])


def main():
    ensure(TEX)
    soldier(); flag(); scope(); glow()
    ammo_box('ammo_9mm', rgb(0x2C5A8A), rgb(0xC9A227), 5)
    ammo_box('ammo_556', rgb(0x2F6B2F), rgb(0xB08A3A), 5)
    ammo_box('ammo_762', rgb(0x8A1C1C), rgb(0xB08A3A), 4)
    ammo_box('ammo_12g', rgb(0xC08A1C), rgb(0xC0302A), 3)
    ammo_box('ammo_308', rgb(0x3A3A3A), rgb(0x9A9EA6), 4)
    ammo_box('ammo_45', rgb(0x5A3A8A), rgb(0xB08A3A), 4)
    ammo_box('ammo_44', rgb(0x8A5A1C), rgb(0xC9A227), 3)
    ammo_box('ammo_40mm', rgb(0x2F6B2F), rgb(0xC0302A), 2)
    papers(); baton(); gun_parts(); flag_item(); flag_block_tex()
    models()
    armor()
    print('assets written')


if __name__ == '__main__':
    main()
