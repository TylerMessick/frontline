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
        self.extra = None

    def cube(self, x, y, z, w, h, d, mat='camo', inflate=0.0):
        self.cubes.append([x, y, z, w, h, d, 0, 0, inflate, mat])
        return self


# ---------------------------------------------------------------- texture regions (u, v) are hand-assigned per cube so the painter can colour them
# Each vehicle texture is 256x128. We paint whole regions a base colour with noise, then details on named spots.


def octo(_unused, name, cx, cy, cz, r, w, mat, parent):
    """An eight-sided wheel/roller: a box plus the same box turned 45 degrees (a child part), spun by the renderer."""
    wheel = Part(name, (cx, cy, cz), parent)
    wheel.cube(-w / 2, -r, -r, w, 2 * r, 2 * r, mat)
    turned = Part(name + '_45', (0, 0, 0), name, (0.7854, 0, 0))
    turned.cube(-w / 2 - 0.01, -r * 0.92, -r * 0.92, w + 0.02, 2 * r * 0.92, 2 * r * 0.92, mat)
    wheel.extra = turned
    return wheel


def tank():
    P = []
    hull = Part('hull'); P.append(hull)
    hull.cube(-20, -22, -30, 40, 12, 64, 'camo')                 # lower hull
    hull.cube(-20, -25, -6, 40, 3, 40, 'camo')                   # raised engine deck
    hull.cube(-16, -27, 8, 32, 2, 20, 'grille')                  # engine grilles
    hull.cube(-18, -27, 30, 36, 2, 4, 'grille')
    hull.cube(-20, -20, 34, 40, 10, 4, 'dark')                   # rear plate
    hull.cube(-14, -18, 38, 4, 4, 2, 'black'); hull.cube(10, -18, 38, 4, 4, 2, 'black')   # exhausts
    hull.cube(-17, -24, 38, 6, 3, 1, 'red'); hull.cube(11, -24, 38, 6, 3, 1, 'red')       # tail lights
    hull.cube(-6, -28, -22, 12, 3, 6, 'dark')                    # driver hatch
    hull.cube(-7, -28.5, -16, 14, 1, 1, 'black')                 # driver periscopes
    hull.cube(-19, -27, 16, 6, 5, 14, 'canvas'); hull.cube(13, -27, 16, 6, 5, 14, 'canvas')   # stowage
    hull.cube(-1, -60, 24, 1, 36, 1, 'black')                    # antenna
    hull.cube(-19, -17, -34, 5, 3, 2, 'light'); hull.cube(14, -17, -34, 5, 3, 2, 'light')  # headlights
    hull.cube(-15, -13, -35, 3, 3, 3, 'steel'); hull.cube(12, -13, -35, 3, 3, 3, 'steel')  # tow hooks
    glacis = Part('glacis', (0, -22, -30), 'hull', (-0.95, 0, 0)); P.append(glacis)
    glacis.cube(-20, 0, -10, 40, 12, 10, 'camo')                 # sloped front plate
    glacis.cube(-6, -0.5, -6, 12, 1, 5, 'dark')
    for side, x in (('l', -29), ('r', 21)):
        t = Part('track_' + side, (0, 0, 0), 'hull'); P.append(t)
        t.cube(x, -14, -36, 8, 14, 72, 'rubber')                 # track run
        for z in range(-34, 36, 4):                              # track pads
            t.cube(x - 0.3, -14.3, z, 8.6, 1.2, 2, 'black')
            t.cube(x - 0.3, 0 - 0.9, z, 8.6, 1.2, 2, 'black')
        t.cube(x - 1, -16, -38, 10, 2, 76, 'camo')               # fender
        t.cube(x - 1 if side == 'l' else x + 8, -14, -36, 1, 10, 72, 'dark')   # side skirt
        for i, z in enumerate((-28, -18, -8, 2, 12, 22)):
            P.append(octo(None, f'wheel_{side}{i}', x + 4, -7, z, 6, 9, 'steel', 'track_' + side))
            P[-1].cube(-5, -2.5, -2.5, 10, 5, 5, 'dark')         # hub
        P.append(octo(None, f'sprocket_{side}', x + 4, -9, 32, 5, 7, 'steel', 'track_' + side))
        P.append(octo(None, f'idler_{side}', x + 4, -9, -32, 5, 7, 'steel', 'track_' + side))
        for z in (-20, 0, 20):
            P.append(octo(None, f'roller_{side}{z}', x + 4, -14, z, 2.5, 5, 'steel', 'track_' + side))
    tur = Part('turret', (0, -25, -2), 'hull'); P.append(tur)
    tur.cube(-14, -12, -14, 28, 12, 30, 'camo')                  # turret body
    tur.cube(-17, -8, 6, 34, 8, 12, 'camo')                      # bustle
    tur.cube(-18, -10, 8, 36, 2, 10, 'grille')                   # bustle rack
    tur.cube(-15, -3, -16, 30, 3, 4, 'dark')                     # lower mantlet lip
    tur.cube(-5, -15, 2, 10, 3, 10, 'dark')                      # commander cupola
    tur.cube(-6, -16, 1, 12, 1, 12, 'black')                     # cupola vision ring
    tur.cube(5, -14, -4, 7, 2, 7, 'dark')                        # loader hatch
    tur.cube(-11, -14, -10, 5, 3, 5, 'steel'); tur.cube(-10, -14.5, -9, 3, 1, 3, 'glass')   # gunner sight
    tur.cube(-16, -11, -12, 2, 4, 6, 'steel'); tur.cube(14, -11, -12, 2, 4, 6, 'steel')      # smoke launchers
    for i in range(3):
        tur.cube(-16.5, -10.5 + i * 1.3, -11 + i * 1.5, 1, 1, 1, 'black'); tur.cube(15.5, -10.5 + i * 1.3, -11 + i * 1.5, 1, 1, 1, 'black')
    tur.cube(-1, -36, 12, 1, 24, 1, 'black')                     # turret antenna
    front = Part('turret_face', (0, -12, -14), 'turret', (-0.7, 0, 0)); P.append(front)
    front.cube(-14, 0, -6, 28, 12, 6, 'camo')                    # sloped turret front
    gun = Part('gun', (0, -6, -16), 'turret'); P.append(gun)
    gun.cube(-4, -4, -8, 8, 8, 8, 'dark')                        # mantlet block
    gun.cube(-2, -2, -46, 4, 4, 38, 'steel')                     # barrel
    gun.cube(-2.6, -2.6, -40, 5.2, 5.2, 14, 'camo')              # thermal sleeve
    gun.cube(-2.6, -2.6, -24, 5.2, 5.2, 10, 'camo')
    gun.cube(-2.8, -2.8, -34, 5.6, 5.6, 7, 'dark')               # bore evacuator
    gun.cube(-2.4, -2.4, -48, 4.8, 4.8, 2.5, 'black')            # muzzle
    mg = Part('coax', (9, -8, -14), 'turret'); P.append(mg)
    mg.cube(-1, -1, -8, 2, 2, 10, 'steel')
    roof = Part('roof_mg', (0, -17, 7), 'turret'); P.append(roof)
    roof.cube(-0.7, -3, -0.7, 1.4, 3, 1.4, 'steel')
    roof.cube(-1.5, -5, -9, 3, 3, 12, 'dark'); roof.cube(-0.8, -4.6, -16, 1.6, 1.6, 8, 'steel')
    return P


def plane():
    P = []
    body = Part('body'); P.append(body)
    # tapered fuselage: wide at the cockpit, narrowing to the tail
    body.cube(-7, -24, -22, 14, 14, 20, 'camo')
    body.cube(-6, -23, -2, 12, 12, 16, 'camo')
    body.cube(-5, -22, 14, 10, 10, 14, 'camo')
    body.cube(-4, -21, 28, 8, 8, 10, 'camo')
    body.cube(-3, -19.5, 38, 6, 5, 10, 'camo')
    body.cube(-8, -26, -38, 16, 16, 16, 'camo')                  # engine cowl
    body.cube(-8.5, -26.5, -38.5, 17, 17, 3, 'dark')             # cowl ring
    for x in (-9, 8):
        for z in (-34, -30, -26):
            body.cube(x, -16, z, 1, 2, 3, 'black')               # exhaust stubs
    body.cube(-3, -31, -22, 6, 7, 4, 'dark')                     # windscreen frame
    body.cube(-4, -30, -18, 8, 6, 14, 'glass')                   # canopy glass
    body.cube(-4.5, -30.5, -18.5, 9, 1, 15, 'dark')              # canopy top frame
    body.cube(-4.5, -24.5, -19, 9, 1, 16, 'dark')                # canopy sill
    body.cube(-3, -27, -4, 6, 3, 6, 'camo')                      # headrest fairing
    body.cube(-1, -36, -8, 2, 6, 1, 'black')                     # antenna mast
    body.cube(-1.5, -40, 26, 3, 20, 10, 'camo')                  # vertical fin
    body.cube(-1, -36, 36, 2, 16, 6, 'dark')                     # rudder
    body.cube(-20, -21, 30, 40, 2, 9, 'camo')                    # horizontal stabiliser
    body.cube(-18, -20.5, 39, 36, 1.5, 3, 'dark')                # elevators
    body.cube(-5, -11, -6, 10, 2, 10, 'dark')                    # belly
    for side, sign in (('l', -1), ('r', 1)):
        w = Part('wing_' + side, (sign * 6, -12, 0), 'body', (0, 0, sign * -0.06)); P.append(w)
        x0 = 0 if sign > 0 else -46
        w.cube(x0, -1, -8, 46, 3, 20, 'camo')                    # main panel
        w.cube(x0, -0.5, 12, 46, 2, 5, 'dark')                   # aileron / flap line
        w.cube(x0 if sign < 0 else 44, -2, -4, 2, 2, 8, 'light' if sign < 0 else 'red')   # wingtip light
        gx = sign * 26 - 3
        w.cube(gx, -4, -6, 6, 3, 8, 'camo')                      # gun fairing
        w.cube(gx + 2, -4.5, -16, 2, 2, 10, 'steel')             # gun barrel
        w.cube(sign * 14 - 4, 2, -2, 8, 4, 12, 'dark')           # bomb
        w.cube(sign * 14 - 1.5, 2.5, 10, 3, 3, 3, 'steel')       # bomb fin
        g = Part('gear_' + side, (sign * 13, -10, -8), 'body'); P.append(g)
        g.cube(-1, 0, -1, 2, 8, 2, 'steel')                      # strut
        g.cube(-3, -1, -2, 6, 2, 10, 'camo')                     # gear door
        P.append(octo(None, 'wheel_' + side, sign * 13, -2, -8, 4, 3, 'rubber', 'body'))
    prop = Part('prop', (0, -18, -39), 'body'); P.append(prop)
    prop.cube(-2.5, -2.5, -5, 5, 5, 5, 'black')                  # spinner
    for k in range(3):
        b = Part(f'blade{k}', (0, 0, -2), 'prop', (0, 0, k * 2.094)); P.append(b)
        b.cube(-1.2, -24, -0.5, 2.4, 24, 1, 'black')
        b.cube(-1.4, -24, -0.6, 2.8, 3, 1.2, 'light')            # blade tip stripe
    tw = Part('tail_wheel', (0, -15, 44), 'body'); P.append(tw)
    tw.cube(-0.5, 0, -0.5, 1, 4, 1, 'steel')
    tw.cube(-1, 3, -2, 2, 4, 4, 'rubber')
    return P


def heli():
    P = []
    body = Part('body'); P.append(body)
    body.cube(-6, -26, -14, 12, 14, 28, 'camo')                  # centre fuselage
    body.cube(-5, -25, -24, 10, 12, 10, 'camo')                  # forward fuselage
    body.cube(-4, -22, -30, 8, 8, 6, 'camo')                     # nose
    body.cube(-3, -21, -33, 6, 5, 3, 'black')                    # sensor turret
    body.cube(-2, -20.5, -34, 4, 3, 1, 'glass')
    body.cube(-5, -31, -22, 10, 6, 10, 'glass')                  # front canopy
    body.cube(-5.5, -31.5, -22.5, 11, 1, 11, 'dark')
    body.cube(-6, -35, -12, 12, 9, 12, 'glass')                  # rear (raised) canopy
    body.cube(-6.5, -35.5, -12.5, 13, 1, 13, 'dark')
    body.cube(-6.5, -26.5, -24, 13, 1, 24, 'dark')               # canopy sills
    body.cube(-5, -38, -2, 10, 4, 18, 'camo')                    # engine deck
    body.cube(-7, -37, 0, 2, 5, 12, 'dark'); body.cube(5, -37, 0, 2, 5, 12, 'dark')     # engine nacelles
    body.cube(-8, -34, 10, 3, 2, 3, 'black'); body.cube(5, -34, 10, 3, 2, 3, 'black')   # exhausts
    body.cube(-2, -41, 0, 4, 3, 4, 'dark')                       # rotor mast base
    body.cube(-4, -22, 14, 8, 8, 16, 'camo')                     # tail boom root
    body.cube(-3, -21, 30, 6, 6, 16, 'camo')
    body.cube(-2.5, -20, 46, 5, 4, 14, 'camo')
    body.cube(-1.5, -36, 52, 3, 16, 8, 'camo')                   # tail fin
    body.cube(-1, -34, 60, 2, 12, 2, 'dark')
    body.cube(-9, -21, 50, 18, 2, 5, 'camo')                     # tail plane
    body.cube(-1.5, -10, 52, 3, 8, 6, 'camo')                    # ventral fin
    body.cube(-22, -22, -8, 44, 3, 8, 'camo')                    # stub wings
    for x in (-21, 15):
        pod = Part('pod' + ('_l' if x < 0 else '_r'), (x + 3, -17, -4), 'body'); P.append(pod)
        pod.cube(-3, -3, -8, 6, 6, 16, 'dark')
        pod.cube(-2.8, -2.8, -8.5, 5.6, 5.6, 1, 'black')
        for ox, oy in ((-1.5, -1.5), (1.5, -1.5), (-1.5, 1.5), (1.5, 1.5)):
            pod.cube(ox - 0.6, oy - 0.6, -9, 1.2, 1.2, 1, 'red')
    body.cube(-1, -46, -2, 2, 8, 1, 'black')                     # antenna
    body.cube(-5, -14, -4, 10, 2, 8, 'dark')                     # belly plate
    rot = Part('rotor', (0, -41, 0), 'body'); P.append(rot)
    rot.cube(-2, -5, -2, 4, 6, 4, 'black')                       # hub
    rot.cube(-3.5, -3, -3.5, 7, 2, 7, 'dark')                    # swashplate
    for k in range(4):
        b = Part(f'blade{k}', (0, -3, 0), 'rotor', (0, k * 1.5708, 0)); P.append(b)
        b.cube(-1.5, -0.6, 2, 3, 1.2, 52, 'black')
        b.cube(-1.6, -0.7, 50, 3.2, 1.4, 4, 'light')
    tr = Part('tail_rotor', (3, -28, 56), 'body'); P.append(tr)
    tr.cube(-0.6, -1.5, -1.5, 1.2, 3, 3, 'black')
    for k in range(2):
        b = Part(f'tblade{k}', (0, 0, 0), 'tail_rotor', (k * 1.5708, 0, 0)); P.append(b)
        b.cube(-0.5, -14, -1.2, 1, 28, 2.4, 'black')
    chin = Part('chin_gun', (0, -12, -26), 'body'); P.append(chin)
    chin.cube(-2.5, -2, -2, 5, 4, 5, 'dark')
    chin.cube(-1, -1, -14, 2, 2, 12, 'steel')
    chin.cube(-1.3, -1.3, -15, 2.6, 2.6, 2, 'black')
    for side, x in (('l', -11), ('r', 9)):
        sk = Part('skid_' + side, (0, 0, 0), 'body'); P.append(sk)
        sk.cube(x, -2, -24, 2, 2, 42, 'steel')
        sk.cube(x, -3, -26, 2, 2, 3, 'steel')
        sk.cube(x, -12, -16, 2, 10, 2, 'steel'); sk.cube(x, -12, 8, 2, 10, 2, 'steel')
    return P


def jeep():
    P = []
    body = Part('body'); P.append(body)
    body.cube(-14, -18, -28, 28, 10, 56, 'camo')                 # tub
    body.cube(-14, -22, -28, 28, 4, 18, 'camo')                  # hood
    body.cube(-12, -22.5, -26, 24, 1, 14, 'dark')                # hood panel lines
    body.cube(-15, -12, -30, 30, 4, 2, 'dark')                   # bumper
    body.cube(-4, -14, -32, 8, 3, 2, 'steel')                    # winch
    body.cube(-12, -20, -29, 6, 4, 1, 'light'); body.cube(6, -20, -29, 6, 4, 1, 'light')   # headlights
    body.cube(-13, -15, -29, 3, 2, 1, 'red'); body.cube(10, -15, -29, 3, 2, 1, 'red')      # blackout lights
    for i in range(5):
        body.cube(-5, -21 + i * 0.7, -29, 10, 0.4, 1, 'black')   # grille slats
    body.cube(-14, -32, -10, 28, 10, 1, 'glass')                 # windshield
    body.cube(-15, -33, -11, 30, 1, 3, 'dark')                   # windshield frame
    body.cube(-15, -32, -11, 1, 10, 3, 'dark'); body.cube(14, -32, -11, 1, 10, 3, 'dark')
    body.cube(-14, -33, -8, 28, 1, 30, 'camo')                   # roof
    body.cube(-14.5, -32, 0, 1, 14, 22, 'camo'); body.cube(13.5, -32, 0, 1, 14, 22, 'camo')   # doors
    body.cube(-14.6, -31, 2, 1, 6, 8, 'glass'); body.cube(13.6, -31, 2, 1, 6, 8, 'glass')     # door windows
    body.cube(-14.6, -31, 12, 1, 6, 8, 'glass'); body.cube(13.6, -31, 12, 1, 6, 8, 'glass')
    body.cube(-16, -26, 4, 1, 1, 6, 'steel'); body.cube(15, -26, 4, 1, 1, 6, 'steel')         # door handles
    body.cube(-17, -29, -9, 3, 3, 1, 'dark'); body.cube(14, -29, -9, 3, 3, 1, 'dark')         # mirrors
    body.cube(-14, -24, 22, 28, 16, 1, 'dark')                   # rear door
    body.cube(-6, -20, 23, 12, 12, 2, 'rubber')                  # spare tyre
    body.cube(-3, -17, 25, 6, 6, 1, 'steel')
    body.cube(-13, -22, 24, 5, 7, 3, 'olive'); body.cube(8, -22, 24, 5, 7, 3, 'olive')     # jerry cans
    body.cube(-10, -20, -6, 8, 4, 8, 'seat'); body.cube(2, -20, -6, 8, 4, 8, 'seat')
    body.cube(-9, -22, -11, 8, 1, 8, 'black')                    # steering wheel
    body.cube(-6, -19, -14, 2, 2, 6, 'steel')
    body.cube(-12, -20, 4, 24, 4, 10, 'seat')                    # rear bench
    body.cube(-1, -44, 20, 1, 12, 1, 'black')                    # antenna
    body.cube(12, -10, 20, 2, 2, 8, 'black')                     # exhaust
    for side, x in (('l', -17), ('r', 15)):
        for i, z in enumerate((-18, 16)):
            body.cube(x - 1 if side == 'l' else x, -24, z - 8, 3, 6, 16, 'camo')   # fender
            w = octo(None, f'wheel_{side}{i}', x + 1, -6, z, 6, 6, 'rubber', 'body'); P.append(w)
            w.cube(-3.5, -3, -3, 7, 6, 6, 'steel')               # hub
            for k in range(4):
                w.cube(-3.2, -6.3, -1 + k * 0.5, 6.4, 0.6, 0.8, 'black')   # tread
    ring = Part('mg_ring', (0, -33, 4), 'body'); P.append(ring)
    ring.cube(-7, -1, -7, 14, 1, 14, 'dark')
    ring.cube(-6, -2, -6, 12, 1, 12, 'black')
    mg = Part('mg', (0, -36, 4), 'body'); P.append(mg)
    mg.cube(-1, 0, -1, 2, 3, 2, 'steel')                         # pintle
    mg.cube(-2, -2, -5, 4, 4, 12, 'dark')                        # receiver
    mg.cube(-1, -1, -22, 2, 2, 18, 'steel')                      # barrel
    mg.cube(-1.5, -1.5, -24, 3, 3, 2, 'black')                   # flash hider
    mg.cube(-5, -1, 0, 4, 4, 4, 'olive')                         # ammo can
    mg.cube(-3, -5, 2, 6, 3, 3, 'dark')                          # sight
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
    'camo': None, 'olive': rgb(0x4F5A36), 'light': rgb(0xF2E6A0), 'red': rgb(0xB83030), 'canvas': rgb(0x7A7452), 'rubber': rgb(0x262626), 'steel': rgb(0x4A4E54), 'glass': rgb(0x8FC2E8), 'black': rgb(0x1E1E20),
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
            var = p.name.replace('-', '_m')
            parent_var = parent.replace('-', '_m')
            lines.append(f'        PartDefinition {var} = {parent_var}.addOrReplaceChild("{p.name}", {cl}, {pose});')
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
        for p_ in list(parts):
            if p_.extra is not None:
                parts.insert(parts.index(p_) + 1, p_.extra)
        pack(parts)
        packed[vid] = parts
    textures(packed)
    java(packed)
    spec(packed)
    print('vehicles written')
