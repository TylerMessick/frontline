#!/usr/bin/env python3
"""Vehicle models for Frontline. One spec per vehicle (parts -> cubes in entity-model space, y down, 1/16 block) is
written out twice: as Java (VehicleModels.java, a LayerDefinition per vehicle) and as preview JSON. Also paints the
vehicle textures and item icons. Run from the repo root: python3 gen/gen_vehicles.py
"""
import json
import os
import random

from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), '..')
ASSETS = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'frontline')
JAVA = os.path.join(ROOT, 'src', 'main', 'java', 'com', 'messickmedia', 'frontline', 'client', 'VehicleModels.java')
random.seed(21)

TEX_W, TEX_H = 512, 512


class Part:
    def __init__(self, name, pivot=(0, 0, 0), parent=None, rot=(0, 0, 0)):
        self.name, self.pivot, self.parent, self.rot = name, pivot, parent, rot
        self.cubes = []

    def cube(self, x, y, z, w, h, d, mat='camo', inflate=0.0):
        self.cubes.append([x, y, z, w, h, d, 0, 0, inflate, mat])
        return self


# ---------------------------------------------------------------- texture regions (u, v) are hand-assigned per cube so the painter can colour them
# Each vehicle texture is 256x128. We paint whole regions a base colour with noise, then details on named spots.

def tank():
    P = []
    hull = Part('hull'); P.append(hull)
    hull.cube(-20, -22, -34, 40, 12, 68, 'camo')           # main hull
    hull.cube(-20, -24, -8, 40, 2, 40, 'camo')             # rear deck raise
    hull.cube(-19, -16, -42, 38, 6, 8, 'camo')            # lower glacis nose
    hull.cube(-17, -26, 10, 34, 4, 14, 'grille')          # engine deck grille
    hull.cube(-22, -14, -36, 2, 4, 72, 'glass')           # side skirt left
    hull.cube(20, -14, -36, 2, 4, 72, 'glass')            # side skirt right
    hull.cube(-6, -27, -26, 12, 3, 6, 'dark')            # driver hatch
    hull.cube(-21, -26, 24, 6, 6, 10, 'camo')            # stowage box
    hull.cube(15, -26, 24, 6, 6, 10, 'camo')
    glacis = Part('glacis', (0, -22, -34), 'hull', (-0.6, 0, 0)); P.append(glacis)
    glacis.cube(-20, 0, -10, 40, 12, 10, 'camo')         # sloped front plate
    for side, x in (('l', -29), ('r', 21)):
        t = Part('track_' + side, (0, 0, 0), 'hull'); P.append(t)
        t.cube(x, -14, -36, 8, 14, 72, 'rubber')            # track run
        t.cube(x - 1, -16, -38, 10, 3, 76, 'rubber')        # fender
        for i, z in enumerate((-28, -14, 0, 14, 28)):
            w = Part(f'wheel_{side}{i}', (x + 4, -7, z), 'track_' + side); P.append(w)
            w.cube(-4.5, -5, -5, 9, 10, 10, 'rubber')      # road wheel
            w.cube(-5, -3, -3, 10, 6, 6, 'steel')        # hub
    tur = Part('turret', (0, -24, -2), 'hull'); P.append(tur)
    tur.cube(-14, -12, -16, 28, 12, 32, 'camo')           # turret body
    tur.cube(-15, -8, -18, 30, 6, 4, 'camo')              # mantlet front
    tur.cube(-16, -11, 6, 32, 10, 12, 'camo')            # bustle
    tur.cube(-5, -15, 0, 10, 3, 10, 'dark')             # commander hatch
    tur.cube(6, -14, -6, 6, 2, 6, 'dark')               # periscope
    tur.cube(-14, -13, 10, 28, 2, 6, 'grille')            # stowage rack
    gun = Part('gun', (0, -6, -16), 'turret'); P.append(gun)
    gun.cube(-3, -3, -8, 6, 6, 8, 'steel')               # mantlet block
    gun.cube(-2, -2, -44, 4, 4, 36, 'steel')             # barrel
    gun.cube(-2.5, -2.5, -34, 5, 5, 8, 'steel')          # fume extractor
    gun.cube(-2.5, -2.5, -46, 5, 5, 3, 'steel')          # muzzle
    mg = Part('coax', (9, -10, -14), 'turret'); P.append(mg)
    mg.cube(-1, -1, -8, 2, 2, 10, 'steel')
    return P


def plane():
    P = []
    body = Part('body'); P.append(body)
    body.cube(-6, -22, -24, 12, 12, 48, 'camo')            # fuselage mid
    body.cube(-5, -21, -36, 10, 10, 12, 'camo')           # nose
    body.cube(-6, -24, -40, 12, 14, 6, 'seat')           # engine cowl
    body.cube(-4, -19, 24, 8, 8, 20, 'camo')              # tail cone
    body.cube(-3, -17, 44, 6, 5, 8, 'camo')
    body.cube(-5, -30, -12, 10, 8, 14, 'glass')           # canopy glass
    body.cube(-5, -24, -14, 10, 2, 18, 'black')          # canopy frame
    body.cube(-1, -38, 30, 2, 16, 14, 'camo')            # vertical fin
    body.cube(-18, -20, 32, 36, 2, 10, 'camo')          # horizontal stabiliser
    wing = Part('wings', (0, -12, 0), 'body'); P.append(wing)
    wing.cube(-48, -1, -6, 96, 3, 16, 'camo')            # main wing
    wing.cube(-50, -1, -2, 2, 2, 10, 'camo')             # wing tips
    wing.cube(48, -1, -2, 2, 2, 10, 'camo')
    wing.cube(-36, -4, -2, 6, 3, 6, 'steel')             # wing guns
    wing.cube(30, -4, -2, 6, 3, 6, 'steel')
    wing.cube(-36, -5, -8, 2, 2, 8, 'steel')             # gun barrels
    wing.cube(34, -5, -8, 2, 2, 8, 'steel')
    wing.cube(-20, 2, -2, 8, 4, 10, 'dark')             # bombs under wing
    wing.cube(12, 2, -2, 8, 4, 10, 'dark')
    prop = Part('prop', (0, -17, -40), 'body'); P.append(prop)
    prop.cube(-2, -2, -4, 4, 4, 4, 'black')               # spinner
    prop.cube(-1, -22, -3, 2, 44, 1, 'black')            # blade pair 1
    prop.cube(-22, -1, -3, 44, 2, 1, 'black')            # blade pair 2
    for side, x in (('l', -14), ('r', 12)):
        g = Part('gear_' + side, (x + 1, -10, -10), 'body'); P.append(g)
        g.cube(-1, 0, -1, 2, 8, 2, 'steel')              # strut
        g.cube(-1.5, 6, -3, 3, 6, 6, 'rubber')            # wheel
    tw = Part('tail_wheel', (0, -14, 42), 'body'); P.append(tw)
    tw.cube(-0.5, 0, -0.5, 1, 4, 1, 'steel')
    tw.cube(-1, 3, -2, 2, 4, 4, 'rubber')
    return P


def heli():
    P = []
    body = Part('body'); P.append(body)
    body.cube(-8, -26, -22, 16, 16, 36, 'camo')            # cabin
    body.cube(-7, -24, -30, 14, 12, 8, 'camo')            # nose
    body.cube(-6, -22, -34, 12, 8, 4, 'glass')            # nose glass
    body.cube(-7, -30, -20, 14, 4, 20, 'black')          # cabin top / windscreen
    body.cube(-6, -34, -8, 12, 4, 18, 'seat')            # engine housing
    body.cube(-3, -22, 14, 6, 6, 44, 'camo')              # tail boom
    body.cube(-1, -34, 50, 2, 14, 8, 'camo')             # tail fin
    body.cube(-8, -20, 52, 16, 2, 6, 'camo')            # tail plane
    body.cube(-18, -20, -6, 36, 3, 8, 'camo')            # stub wings
    body.cube(-20, -17, -8, 5, 5, 12, 'dark')           # rocket pods
    body.cube(15, -17, -8, 5, 5, 12, 'dark')
    body.cube(-2, -12, -24, 4, 4, 8, 'steel')            # chin gun base
    rot = Part('rotor', (0, -36, 0), 'body'); P.append(rot)
    rot.cube(-2, -4, -2, 4, 6, 4, 'black')                # mast
    rot.cube(-50, -3, -1.5, 100, 1, 3, 'black')          # blade pair
    rot.cube(-1.5, -3, -50, 3, 1, 100, 'black')
    tr = Part('tail_rotor', (3, -26, 54), 'body'); P.append(tr)
    tr.cube(-0.5, -12, -1, 1, 24, 2, 'black')
    tr.cube(-0.5, -1, -12, 1, 2, 24, 'black')
    chin = Part('chin_gun', (0, -10, -24), 'body'); P.append(chin)
    chin.cube(-1, -1, -12, 2, 2, 12, 'steel')
    for side, x in (('l', -11), ('r', 9)):
        s = Part('skid_' + side, (0, 0, 0), 'body'); P.append(s)
        s.cube(x, -2, -22, 2, 2, 40, 'steel')
        s.cube(x, -10, -14, 2, 8, 2, 'steel')
        s.cube(x, -10, 8, 2, 8, 2, 'steel')
    return P


def jeep():
    P = []
    body = Part('body'); P.append(body)
    body.cube(-12, -16, -24, 24, 8, 48, 'camo')            # tub
    body.cube(-12, -20, -24, 24, 4, 16, 'camo')           # hood
    body.cube(-13, -11, -26, 26, 3, 2, 'steel')           # bumper
    body.cube(-12, -30, -8, 24, 10, 1, 'glass')           # windshield glass
    body.cube(-13, -31, -9, 26, 1, 3, 'black')           # windshield frame
    body.cube(-12, -32, 2, 24, 2, 2, 'black')            # roll bar top
    body.cube(-12, -32, 2, 2, 16, 2, 'black')
    body.cube(10, -32, 2, 2, 16, 2, 'black')
    body.cube(-10, -20, -4, 8, 4, 8, 'seat')             # seats
    body.cube(2, -20, -4, 8, 4, 8, 'seat')
    body.cube(-10, -20, 10, 20, 4, 8, 'seat')
    body.cube(-6, -19, -14, 2, 2, 8, 'steel')            # steering column
    body.cube(-9, -22, -10, 8, 1, 8, 'dark')             # wheel
    body.cube(-10, -20, 14, 20, 6, 10, 'grille')          # cargo
    body.cube(-4, -17, 24, 8, 8, 2, 'steel')              # spare bracket
    for side, x in (('l', -15), ('r', 13)):
        for i, z in enumerate((-16, 16)):
            w = Part(f'wheel_{side}{i}', (x + 1, -6, z), 'body'); P.append(w)
            w.cube(-3, -6, -6, 6, 12, 12, 'rubber')
            w.cube(-3.5, -3, -3, 7, 6, 6, 'steel')
    post = Part('mg_mount', (0, -22, 8), 'body'); P.append(post)
    post.cube(-1, -8, -1, 2, 8, 2, 'steel')
    mg = Part('mg', (0, -30, 8), 'body'); P.append(mg)
    mg.cube(-2, -2, -4, 4, 4, 12, 'steel')               # receiver
    mg.cube(-1, -1, -22, 2, 2, 18, 'steel')              # barrel
    mg.cube(-5, -1, 2, 4, 4, 4, 'steel')                 # ammo box
    return P


VEHICLES = [('tank', tank, 'Tank'), ('plane', plane, 'Fighter Plane'), ('heli', heli, 'Attack Helicopter'), ('jeep', jeep, 'Jeep')]


# ---------------------------------------------------------------- textures
def rgb(h):
    return ((h >> 16) & 255, (h >> 8) & 255, h & 255, 255)


def paint(img, x0, y0, x1, y1, col, noise=8):
    for y in range(max(0, y0), min(TEX_H, y1)):
        for x in range(max(0, x0), min(TEX_W, x1)):
            n = random.randint(-noise, noise)
            img.putpixel((x, y), tuple(max(0, min(255, c + n)) for c in col[:3]) + (255,))


def camo(img, x0, y0, x1, y1, base, spots):
    paint(img, x0, y0, x1, y1, base)
    for _ in range(((x1 - x0) * (y1 - y0)) // 5):
        cx, cy = random.randint(x0, x1 - 1), random.randint(y0, y1 - 1)
        c = random.choice(spots)
        for dx in range(-2, 3):
            for dy in range(-1, 2):
                if x0 <= cx + dx < x1 and y0 <= cy + dy < y1 and random.random() < 0.65:
                    img.putpixel((cx + dx, cy + dy), c)


def pack(parts):
    """Shelf-packs every cube's unwrapped footprint (2(w+d) by d+h) into the texture and writes u,v back."""
    cubes = sorted([c for p in parts for c in p.cubes], key=lambda c: -(c[5] + c[4]))
    x = y = shelf = 0
    for c in cubes:
        w = int(2 * (c[3] + c[5]) + 0.999)
        h = int(c[5] + c[4] + 0.999)
        if x + w > TEX_W:
            x = 0
            y += shelf
            shelf = 0
        assert y + h <= TEX_H, ('texture overflow', c)
        c[6], c[7] = x, y
        x += w
        shelf = max(shelf, h)


MATS = {
    'camo': None, 'rubber': rgb(0x262626), 'steel': rgb(0x4A4E54), 'glass': rgb(0x8FC2E8), 'black': rgb(0x1E1E20),
    'dark': rgb(0x3C4229), 'seat': rgb(0x6B5A3E), 'grille': rgb(0x2E3322),
}


def textures(packed):
    olive, dark, sand = rgb(0x5A6239), rgb(0x3C4229), rgb(0x8A7F58)
    out = os.path.join(ASSETS, 'textures', 'entity')
    os.makedirs(out, exist_ok=True)
    for vid, parts in packed.items():
        img = Image.new('RGBA', (TEX_W, TEX_H), (0, 0, 0, 0))
        camo(img, 0, 0, TEX_W, TEX_H, olive, [dark, sand])
        for p in parts:
            for (x, y, z, w, h, d, u, v, inf, mat) in p.cubes:
                col = MATS[mat]
                fw, fh = int(2 * (w + d) + 0.999), int(d + h + 0.999)
                if col is not None:
                    paint(img, u, v, u + fw, v + fh, col, 4 if mat in ('glass', 'black') else 7)
                if mat == 'grille':
                    for yy in range(v, v + fh, 2): paint(img, u, yy, u + fw, yy + 1, rgb(0x141612), 0)
                if mat == 'glass':
                    for i in range(0, fw, 6): paint(img, u + i, v, u + i + 1, v + fh, rgb(0xB8DCF4), 2)
        img.save(os.path.join(out, f'{vid}.png'))
    # item icons
    itm = os.path.join(ASSETS, 'textures', 'item')
    steel, rubber, glass, black = MATS['steel'], MATS['rubber'], MATS['glass'], MATS['black']
    icons = {
        'tank': [(1, 9, 15, 13, olive), (3, 6, 11, 9, olive), (9, 7, 16, 8, steel), (0, 12, 16, 15, rubber)],
        'plane': [(6, 3, 10, 14, olive), (1, 8, 15, 10, olive), (7, 1, 9, 4, steel), (5, 12, 11, 13, olive), (6, 5, 10, 7, glass)],
        'heli': [(4, 7, 11, 12, olive), (11, 8, 16, 10, olive), (1, 4, 15, 5, black), (7, 5, 9, 7, black), (4, 12, 11, 14, steel), (4, 8, 7, 10, glass)],
        'jeep': [(2, 7, 14, 12, olive), (2, 5, 8, 7, olive), (8, 3, 13, 7, glass), (2, 12, 5, 15, rubber), (11, 12, 14, 15, rubber)],
    }
    for vid, rects in icons.items():
        img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
        for (x0, y0, x1, y1, c) in rects:
            for y in range(y0, y1):
                for x in range(x0, x1):
                    img.putpixel((x, y), tuple(max(0, min(255, v + random.randint(-6, 6))) for v in c[:3]) + (255,))
        img.save(os.path.join(itm, f'{vid}.png'))
        json.dump({'parent': 'minecraft:item/generated', 'textures': {'layer0': f'frontline:item/{vid}'}},
                  open(os.path.join(ASSETS, 'models', 'item', f'{vid}.json'), 'w'))


# ---------------------------------------------------------------- java
def java(packed):
    lines = ['package com.messickmedia.frontline.client;', '',
             'import com.messickmedia.frontline.Frontline;',
             'import net.minecraft.client.model.geom.ModelLayerLocation;',
             'import net.minecraft.client.model.geom.PartPose;',
             'import net.minecraft.client.model.geom.builders.CubeListBuilder;',
             'import net.minecraft.client.model.geom.builders.LayerDefinition;',
             'import net.minecraft.client.model.geom.builders.MeshDefinition;',
             'import net.minecraft.client.model.geom.builders.PartDefinition;', '',
             '/** GENERATED by gen/gen_vehicles.py - do not edit by hand. One layer per vehicle; the renderer animates the named parts. */',
             'public final class VehicleModels {', '    private VehicleModels() {}', '']
    for vid, fn, _ in VEHICLES:
        lines.append(f'    public static final ModelLayerLocation {vid.upper()} = new ModelLayerLocation(Frontline.id("{vid}"), "main");')
    lines.append('')
    for vid, fn, _ in VEHICLES:
        parts = packed[vid]
        lines.append(f'    public static LayerDefinition {vid}() {{')
        lines.append('        MeshDefinition mesh = new MeshDefinition();')
        lines.append('        PartDefinition root = mesh.getRoot();')
        for p in parts:
            parent = 'root' if p.parent is None else p.parent
            cl = 'CubeListBuilder.create()'
            for (x, y, z, w, h, d, u, v, inf, mat) in p.cubes:
                cl += f'.texOffs({u}, {v})' + f'.addBox({x}F, {y}F, {z}F, {w}F, {h}F, {d}F' + (f', new net.minecraft.client.model.geom.builders.CubeDeformation({inf}F)' if inf else '') + ')'
            px, py, pz = p.pivot
            rx, ry, rz = p.rot
            pose = f'PartPose.offsetAndRotation({px}F, {py}F, {pz}F, {rx}F, {ry}F, {rz}F)' if any(p.rot) else f'PartPose.offset({px}F, {py}F, {pz}F)'
            lines.append(f'        PartDefinition {p.name} = {parent}.addOrReplaceChild("{p.name}", {cl}, {pose});')
        lines.append(f'        return LayerDefinition.create(mesh, {TEX_W}, {TEX_H});')
        lines.append('    }')
        lines.append('')
    lines.append('}')
    with open(JAVA, 'w') as f:
        f.write('\n'.join(lines) + '\n')


def spec(packed):
    out = {}
    for vid, fn, name in VEHICLES:
        out[vid] = {'name': name, 'parts': [{'name': p.name, 'parent': p.parent, 'pivot': p.pivot, 'rot': p.rot, 'cubes': p.cubes} for p in packed[vid]]}
    json.dump(out, open(os.path.join(os.path.dirname(__file__), 'vehicles_spec.json'), 'w'))


if __name__ == '__main__':
    packed = {}
    for vid, fn, _ in VEHICLES:
        parts = fn()
        pack(parts)
        packed[vid] = parts
    textures(packed)
    java(packed)
    spec(packed)
    print('vehicles written')
