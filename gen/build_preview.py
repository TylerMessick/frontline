#!/usr/bin/env python3
"""Builds preview/index.html: a three.js page showing every Frontline gun from the real item-model JSON and atlas,
a first-person view that uses the same hip/aim maths as FirstPersonGun.java, the soldier model with the real skin
holding a rifle, and the Command Flag. Run from the repo root: python3 gen/build_preview.py"""
import base64
import json
import os

ROOT = os.path.join(os.path.dirname(__file__), '..')
ASSETS = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'frontline')
OUT = os.path.join(ROOT, 'preview')


def b64(path):
    with open(path, 'rb') as f:
        return 'data:image/png;base64,' + base64.b64encode(f.read()).decode()


spec = json.load(open(os.path.join(os.path.dirname(__file__), 'guns_spec.json')))
models = {}
for g in spec['guns']:
    models[g['id']] = json.load(open(os.path.join(ASSETS, 'models', 'item', f"{g['id']}.json")))
# gun stats from Guns.java for the first-person maths
stats = {}
for line in open(os.path.join(ROOT, 'src', 'main', 'java', 'com', 'messickmedia', 'frontline', 'gun', 'Guns.java')):
    if 'new GunInfo(' in line:
        body = line[line.index('new GunInfo(') + 12:line.rindex(')')]
        parts = [p.strip() for p in body.split(',')]
        gid = parts[0].strip('"')
        kind = parts[1]
        nums = parts[-5:-2]
        stats[gid] = {'kind': kind, 'sightY': float(nums[0].rstrip('F')), 'gripY': float(nums[1].rstrip('F')), 'gripZ': float(nums[2].rstrip('F')),
                      'muzzle': [float(parts[18].rstrip('F')), float(parts[19].rstrip('F')), float(parts[20].rstrip('F'))], 'scope': parts[17] == 'true'}

vehicles = json.load(open(os.path.join(os.path.dirname(__file__), 'vehicles_spec.json')))
textures = {
    'tank': b64(os.path.join(ASSETS, 'textures', 'entity', 'tank.png')),
    'plane': b64(os.path.join(ASSETS, 'textures', 'entity', 'plane.png')),
    'heli': b64(os.path.join(ASSETS, 'textures', 'entity', 'heli.png')),
    'jeep': b64(os.path.join(ASSETS, 'textures', 'entity', 'jeep.png')),
    'atlas': b64(os.path.join(ASSETS, 'textures', 'item', 'gun_atlas.png')),
    'soldier': b64(os.path.join(ASSETS, 'textures', 'entity', 'soldier.png')),
    'flag': b64(os.path.join(ASSETS, 'textures', 'entity', 'flag.png')),
    'pole': b64(os.path.join(ASSETS, 'textures', 'block', 'flag_pole.png')),
}

html = r'''<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Frontline Armoury</title>
<style>
  :root { --bg: #0e1116; --panel: #161b23; --ink: #e8ecf2; --mute: #8a93a6; --accent: #d9a441; }
  * { box-sizing: border-box; }
  html, body { margin: 0; height: 100%; background: var(--bg); color: var(--ink); font: 14px/1.4 system-ui, -apple-system, Segoe UI, Roboto, sans-serif; overflow: hidden; }
  #ui { position: fixed; left: 16px; top: 16px; right: 16px; display: flex; gap: 8px; flex-wrap: wrap; z-index: 2; }
  button { background: var(--panel); color: var(--ink); border: 1px solid #2a3140; border-radius: 8px; padding: 8px 12px; cursor: pointer; font: inherit; }
  button.on { border-color: var(--accent); color: var(--accent); }
  #info { position: fixed; left: 16px; bottom: 16px; max-width: 520px; background: rgba(22,27,35,.92); border: 1px solid #2a3140; border-radius: 10px; padding: 12px 14px; z-index: 2; }
  #info h2 { margin: 0 0 4px; font-size: 16px; color: var(--accent); }
  #info p { margin: 0; color: var(--mute); }
  #ctl { position: fixed; right: 16px; bottom: 16px; background: rgba(22,27,35,.92); border: 1px solid #2a3140; border-radius: 10px; padding: 10px 14px; z-index: 2; display: none; }
  #ctl label { display: block; color: var(--mute); margin: 4px 0; }
  #cross { position: fixed; left: 50%; top: 50%; width: 2px; height: 2px; background: #fff; transform: translate(-50%,-50%); z-index: 2; display: none; box-shadow: 0 0 0 1px rgba(0,0,0,.6); }
  canvas { display: block; }
</style>
</head>
<body>
<div id="ui"></div>
<div id="cross"></div>
<div id="info"><h2 id="title"></h2><p id="desc"></p></div>
<div id="ctl"><label>Aim down sights <input id="ads" type="range" min="0" max="100" value="0"></label><label><input id="fire" type="checkbox"> muzzle flash</label><label><input id="walk" type="checkbox" checked> walk bob</label></div>
<script src="https://cdnjs.cloudflare.com/ajax/libs/three.js/r128/three.min.js"></script>
<script>
const SPEC = __SPEC__;
const MODELS = __MODELS__;
const STATS = __STATS__;
const TEX = __TEX__;
const VEHICLES = __VEHICLES__;

const loader = new THREE.TextureLoader();
function tex(uri) { const t = loader.load(uri); t.magFilter = THREE.NearestFilter; t.minFilter = THREE.NearestFilter; return t; }
const T = { atlas: tex(TEX.atlas), soldier: tex(TEX.soldier), flag: tex(TEX.flag), pole: tex(TEX.pole), tank: tex(TEX.tank), plane: tex(TEX.plane), heli: tex(TEX.heli), jeep: tex(TEX.jeep) };
const MAT = {};
function mat(name, opts) {
  const key = name + JSON.stringify(opts || {});
  if (!MAT[key]) MAT[key] = new THREE.MeshLambertMaterial(Object.assign({ map: T[name], transparent: true, alphaTest: 0.1, side: THREE.DoubleSide }, opts || {}));
  return MAT[key];
}

// ---------------------------------------------------------------- a box with a texture rectangle per face
// faces: {px, nx, py, ny, pz, nz} each [u0, v0, u1, v1] in texture pixels; texture size tw x th
function faceBox(from, to, faces, tw, th, material, inflate) {
  inflate = inflate || 0;
  const a = [from[0] - inflate, from[1] - inflate, from[2] - inflate], b = [to[0] + inflate, to[1] + inflate, to[2] + inflate];
  const pos = [], uv = [], idx = [];
  // each face: four corners (counter-clockwise seen from outside) and texture corners top-left, top-right, bottom-right, bottom-left
  const F = {
    px: [[b[0], b[1], a[2]], [b[0], b[1], b[2]], [b[0], a[1], b[2]], [b[0], a[1], a[2]]],
    nx: [[a[0], b[1], b[2]], [a[0], b[1], a[2]], [a[0], a[1], a[2]], [a[0], a[1], b[2]]],
    py: [[a[0], b[1], a[2]], [b[0], b[1], a[2]], [b[0], b[1], b[2]], [a[0], b[1], b[2]]],
    ny: [[a[0], a[1], b[2]], [b[0], a[1], b[2]], [b[0], a[1], a[2]], [a[0], a[1], a[2]]],
    pz: [[a[0], b[1], b[2]], [b[0], b[1], b[2]], [b[0], a[1], b[2]], [a[0], a[1], b[2]]],
    nz: [[b[0], b[1], a[2]], [a[0], b[1], a[2]], [a[0], a[1], a[2]], [b[0], a[1], a[2]]],
  };
  let n = 0;
  for (const k of Object.keys(F)) {
    const r = faces[k]; if (!r) continue;
    const c = F[k];
    const u0 = r[0] / tw, v0 = 1 - r[1] / th, u1 = r[2] / tw, v1 = 1 - r[3] / th;
    const uvs = [[u0, v0], [u1, v0], [u1, v1], [u0, v1]];
    for (let i = 0; i < 4; i++) { pos.push(...c[i]); uv.push(...uvs[i]); }
    idx.push(n, n + 1, n + 2, n, n + 2, n + 3);
    n += 4;
  }
  const g = new THREE.BufferGeometry();
  g.setAttribute('position', new THREE.Float32BufferAttribute(pos, 3));
  g.setAttribute('uv', new THREE.Float32BufferAttribute(uv, 2));
  g.setIndex(idx);
  g.computeVertexNormals();
  return new THREE.Mesh(g, material);
}

// Minecraft entity-model cube: texOffs (u,v), size (w,h,d) -> the standard unwrapped layout
function mcCube(x, y, z, w, h, d, u, v, tw, th, material, inflate) {
  const faces = {
    ny: [u + d, v, u + d + w, v + d],            // visually the top (model -Y)
    py: [u + d + w, v, u + d + 2 * w, v + d],    // visually the bottom
    nx: [u, v + d, u + d, v + d + h],            // character's right
    nz: [u + d, v + d, u + d + w, v + d + h],    // front
    px: [u + d + w, v + d, u + d + w + d, v + d + h],
    pz: [u + d + w + d, v + d, u + 2 * d + 2 * w, v + d + h],
  };
  return faceBox([x, y, z], [x + w, y + h, z + d], faces, tw, th, material, inflate);
}

// ---------------------------------------------------------------- item model (JSON elements) -> group, in model units
const FACE_MAP = { east: 'px', west: 'nx', up: 'py', down: 'ny', south: 'pz', north: 'nz' };
function itemModel(json) {
  const grp = new THREE.Group();
  for (const el of json.elements) {
    const faces = {};
    for (const f in el.faces) faces[FACE_MAP[f]] = el.faces[f].uv;
    const mesh = faceBox(el.from, el.to, faces, 16, 16, mat('atlas'));
    if (el.rotation) {
      const piv = new THREE.Group();
      const o = el.rotation.origin;
      piv.position.set(o[0], o[1], o[2]);
      mesh.position.set(-o[0], -o[1], -o[2]);
      const ang = THREE.MathUtils.degToRad(el.rotation.angle);
      if (el.rotation.axis === 'x') piv.rotation.x = ang; else if (el.rotation.axis === 'y') piv.rotation.y = ang; else piv.rotation.z = ang;
      piv.add(mesh); grp.add(piv);
    } else grp.add(mesh);
  }
  return grp;
}

// applies a Minecraft display transform (translation in 1/16 block, rotation XYZ degrees, scale) around the model centre (8,8,8)
function displayed(json, which) {
  const d = (json.display || {})[which] || { rotation: [0, 0, 0], translation: [0, 0, 0], scale: [1, 1, 1] };
  const outer = new THREE.Group();
  const rot = new THREE.Group();
  const sc = new THREE.Group();
  const m = itemModel(json);
  m.position.set(-0.5, -0.5, -0.5); m.scale.set(1 / 16, 1 / 16, 1 / 16);
  sc.scale.set(d.scale[0], d.scale[1], d.scale[2]);
  rot.rotation.set(THREE.MathUtils.degToRad(d.rotation[0]), THREE.MathUtils.degToRad(d.rotation[1]), THREE.MathUtils.degToRad(d.rotation[2]), 'XYZ');
  outer.position.set(d.translation[0] / 16, d.translation[1] / 16, d.translation[2] / 16);
  sc.add(m); rot.add(sc); outer.add(rot);
  return outer;
}

// ---------------------------------------------------------------- scene
const renderer = new THREE.WebGLRenderer({ antialias: true });
renderer.setPixelRatio(Math.min(devicePixelRatio, 2));
renderer.setSize(innerWidth, innerHeight);
document.body.appendChild(renderer.domElement);
const scene = new THREE.Scene();
scene.background = new THREE.Color(0x0e1116);
const camera = new THREE.PerspectiveCamera(50, innerWidth / innerHeight, 0.01, 200);
scene.add(new THREE.HemisphereLight(0xdfe8ff, 0x3a3326, 0.9));
const sun = new THREE.DirectionalLight(0xffffff, 0.9); sun.position.set(3, 6, 4); scene.add(sun);
const sun2 = new THREE.DirectionalLight(0xffe6c0, 0.35); sun2.position.set(-4, 2, -3); scene.add(sun2);
const floor = new THREE.Mesh(new THREE.PlaneGeometry(40, 40), new THREE.MeshLambertMaterial({ color: 0x1a2028 }));
floor.rotation.x = -Math.PI / 2; floor.position.y = -0.01; scene.add(floor);
const grid = new THREE.GridHelper(40, 40, 0x2a3140, 0x1d2430); grid.position.y = 0; scene.add(grid);

let root = new THREE.Group(); scene.add(root);
let mode = 'turntable', orbitYaw = 0.6, orbitPitch = 0.35, orbitDist = 3.2, target = new THREE.Vector3(0, 0.9, 0), drag = null, auto = true;
let clock = 0, current = null, fps = null;

renderer.domElement.addEventListener('mousedown', e => { drag = { x: e.clientX, y: e.clientY }; auto = false; });
addEventListener('mouseup', () => drag = null);
addEventListener('mousemove', e => { if (!drag || mode === 'fps') return; orbitYaw -= (e.clientX - drag.x) * 0.006; orbitPitch = Math.max(-1.2, Math.min(1.4, orbitPitch + (e.clientY - drag.y) * 0.006)); drag = { x: e.clientX, y: e.clientY }; });
addEventListener('wheel', e => { orbitDist = Math.max(0.6, Math.min(12, orbitDist * (1 + e.deltaY * 0.001))); });
addEventListener('resize', () => { camera.aspect = innerWidth / innerHeight; camera.updateProjectionMatrix(); renderer.setSize(innerWidth, innerHeight); });

function clear() { floor.visible = grid.visible = true; scene.remove(root); root = new THREE.Group(); scene.add(root); fps = null; document.getElementById('ctl').style.display = 'none'; document.getElementById('cross').style.display = 'none'; }
function info(t, d) { document.getElementById('title').textContent = t; document.getElementById('desc').textContent = d; }

// turntable: one gun, large
function showGun(g) {
  clear(); mode = 'turntable';
  const json = MODELS[g.id];
  const m = itemModel(json);
  const zc = (g.z_range[0] + g.z_range[1]) / 2;
  m.position.set(-0.5, -0.5, -zc / 16); m.scale.set(1 / 16, 1 / 16, 1 / 16);
  const hold = new THREE.Group(); hold.add(m); hold.position.y = 1.0; hold.scale.set(1.6, 1.6, 1.6);
  root.add(hold); current = hold;
  target.set(0, 1.0, 0); orbitDist = 4.2; auto = true;
  // ghost markers: muzzle and sight line
  info(g.name, `${g.parts.length} elements. Drag to orbit, wheel to zoom.`);
}

// GUI icon strip: every gun as the inventory sees it
function showIcons() {
  clear(); mode = 'turntable'; auto = false;
  const n = SPEC.guns.length;
  SPEC.guns.forEach((g, i) => {
    const d = displayed(MODELS[g.id], 'gui');
    d.position.set((i - (n - 1) / 2) * 1.25, 1.1, 0);
    // a dark slot behind each
    const slot = new THREE.Mesh(new THREE.PlaneGeometry(1.1, 1.1), new THREE.MeshBasicMaterial({ color: 0x20262f }));
    slot.position.set(d.position.x, 1.1, -0.6); root.add(slot);
    root.add(d);
  });
  target.set(0, 1.1, 0); orbitYaw = 0; orbitPitch = 0; orbitDist = 7.5;
  info('Inventory icons', 'Each gun through its gui display transform (rotation 20,-90,0). Muzzles should point right and fit the slot.');
}

// first person: camera at the eye, the gun placed with FirstPersonGun.java's maths
function showFps(g) {
  clear(); mode = 'fps';
  const json = MODELS[g.id], st = STATS[g.id];
  const m = itemModel(json);
  m.position.set(-0.5, -0.5, -0.5); m.scale.set(1 / 16, 1 / 16, 1 / 16);
  const inner = new THREE.Group(); inner.add(m);
  const SCALE = 0.5;
  // pivot on the grip, like the Java: translate(0, -(gripY-8)/16, -(gripZ-8)/16) after scale
  inner.position.set(0, -(st.gripY - 8) / 16, -(st.gripZ - 8) / 16);
  const sc = new THREE.Group(); sc.scale.set(SCALE, SCALE, SCALE); sc.add(inner);
  const hand = new THREE.Group(); hand.add(sc);
  // muzzle flash: a star of planes at the muzzle
  const flash = new THREE.Group();
  for (let i = 0; i < 3; i++) {
    const p = new THREE.Mesh(new THREE.PlaneGeometry(0.03, 0.17), new THREE.MeshBasicMaterial({ color: 0xffc860, transparent: true, opacity: 0.85, blending: THREE.AdditiveBlending, depthWrite: false, side: THREE.DoubleSide }));
    p.rotation.z = i * Math.PI / 3; flash.add(p);
  }
  const spike = new THREE.Mesh(new THREE.ConeGeometry(0.012, 0.14, 6), new THREE.MeshBasicMaterial({ color: 0xff9a30, transparent: true, opacity: 0.8, blending: THREE.AdditiveBlending, depthWrite: false }));
  spike.rotation.x = -Math.PI / 2; spike.position.z = -0.07; flash.add(spike);
  flash.position.set((st.muzzle[0] - 8) / 16, (st.muzzle[1] - 8) / 16, (st.muzzle[2] - 8) / 16);
  inner.add(flash);
  root.add(hand);
  // a target wall with a bullseye to aim at
  const wall = new THREE.Mesh(new THREE.PlaneGeometry(6, 3), new THREE.MeshLambertMaterial({ color: 0x3b4350 }));
  wall.position.set(0, 0, -8); root.add(wall);
  for (const [r, c] of [[0.5, 0xffffff], [0.35, 0xc0302a], [0.2, 0xffffff], [0.08, 0xc0302a]]) {
    const ring = new THREE.Mesh(new THREE.CircleGeometry(r, 32), new THREE.MeshBasicMaterial({ color: c })); ring.position.set(0, 0, -7.99 + r * 0.001); root.add(ring);
  }
  floor.visible = grid.visible = false;
  fps = { hand, flash, st, kind: st.kind, t: 0 };
  document.getElementById('ctl').style.display = 'block';
  document.getElementById('cross').style.display = 'block';
  info(g.name + ' — first person', 'Slide "aim down sights": the sight post (or scope) should land on the white dot at the centre. Walk bob and flash as in game.');
}

// soldier with rifle
function soldierModel(gunId) {
  const S = (x, y, z, w, h, d, u, v, inf) => mcCube(x, y, z, w, h, d, u, v, 128, 64, mat('soldier'), inf || 0);
  const rig = new THREE.Group();
  const head = new THREE.Group(); head.add(S(-4, -8, -4, 8, 8, 8, 0, 0)); head.add(S(-4, -8, -4, 8, 8, 8, 32, 0, 0.5)); head.add(S(-5, -5.4, -5, 10, 1, 10, 64, 22));
  const body = new THREE.Group(); body.add(S(-4, 0, -2, 8, 12, 4, 16, 16)); body.add(S(-4.5, 0.5, -2.5, 9, 7.5, 5, 64, 0)); body.add(S(-4.4, 10, -2.4, 8.8, 2, 4.8, 64, 14));
  body.add(S(-3.8, 4, -3.4, 3, 3, 1, 92, 0)); body.add(S(0.8, 4, -3.4, 3, 3, 1, 92, 5)); body.add(S(-3, 1, 2.5, 6, 6, 3, 64, 34));
  const rArm = new THREE.Group(); rArm.position.set(-5, 2, 0); rArm.add(S(-3, -2, -2, 4, 12, 4, 40, 16));
  const band = mcCube(-3.5, -1, -2.5, 4, 2, 5, 104, 28, 128, 64, mat('soldier', { color: 0x1c4f8a }), 0); rArm.add(band);
  const lArm = new THREE.Group(); lArm.position.set(5, 2, 0); lArm.add(S(-1, -2, -2, 4, 12, 4, 32, 48));
  const rLeg = new THREE.Group(); rLeg.position.set(-1.9, 12, 0); rLeg.add(S(-2, 0, -2, 4, 12, 4, 0, 16)); rLeg.add(S(-2.5, 8, -2.5, 5, 4, 5, 84, 34)); rLeg.add(S(-2.3, 4, -2.7, 4.6, 3, 1, 104, 22));
  const lLeg = new THREE.Group(); lLeg.position.set(1.9, 12, 0); lLeg.add(S(-2, 0, -2, 4, 12, 4, 16, 48)); lLeg.add(S(-2.5, 8, -2.5, 5, 4, 5, 84, 34)); lLeg.add(S(-2.3, 4, -2.7, 4.6, 3, 1, 104, 22));
  rig.add(head, body, rArm, lArm, rLeg, lLeg);
  // crossbow-hold pose (AnimationUtils.animateCrossbowHold, right handed): arms forward, left hand across to the foregrip
  rArm.rotation.set(-Math.PI / 2 - 0.1 + 0.3, -0.3, 0); lArm.rotation.set(-1.5 + 0.1, 0.5 + 0.25, 0);
  head.rotation.set(0.1, 0, 0);
  // the gun in the right hand: ItemInHandLayer transforms then the thirdperson_righthand display
  const handG = new THREE.Group(); handG.position.set(-1, 10, 0); // translateToHand: arm pivot then down the arm
  const flip = new THREE.Group(); flip.rotation.x = THREE.MathUtils.degToRad(-90); const flip2 = new THREE.Group(); flip2.rotation.y = Math.PI;
  const off = new THREE.Group(); off.position.set(1, 2, -10);
  const gun = displayed(MODELS[gunId], 'thirdperson_righthand'); gun.scale.set(16, 16, 16);
  off.add(gun); flip2.add(off); flip.add(flip2); handG.add(flip); rArm.add(handG);
  // model space is y-down, x mirrored: undo it like the entity renderer
  const world = new THREE.Group(); world.scale.set(-1 / 16, -1 / 16, 1 / 16); world.position.y = 24 / 16; world.add(rig);
  return world;
}

function showSoldier() {
  clear(); mode = 'turntable';
  const a = soldierModel('fl4_carbine'); a.position.x = -0.9; root.add(a);
  const b = soldierModel('vk47_rifle'); b.position.x = 0.9; b.rotation.y = -0.4; root.add(b);
  target.set(0, 0.95, 0); orbitDist = 4.0; auto = true;
  info('Soldiers', 'Recruited villagers in uniform: helmet, plate carrier, pouches, pack, knee pads, boots and the nation armband (tinted). Rifles in the two-handed hold.');
}

function showFlag() {
  clear(); mode = 'turntable';
  const P = (x, y, z, w, h, d, u, v, m) => mcCube(x, y, z, w, h, d, u, v, 64, 64, m || mat('flag'));
  const blk = new THREE.Group();
  blk.add(faceBox([5, 0, 5], [11, 2, 11], { px: [0, 0, 6, 2], nx: [0, 0, 6, 2], py: [0, 0, 6, 6], ny: [0, 0, 6, 6], pz: [0, 0, 6, 2], nz: [0, 0, 6, 2] }, 16, 16, mat('pole', { color: 0x555555 })));
  blk.add(faceBox([7, 3, 7], [9, 16, 9], { px: [0, 0, 2, 13], nx: [0, 0, 2, 13], py: [0, 0, 2, 2], ny: [0, 0, 2, 2], pz: [0, 0, 2, 13], nz: [0, 0, 2, 13] }, 16, 16, mat('pole')));
  const top = new THREE.Group(); top.position.set(8, 16, 8);
  top.add(P(-1, 0, -1, 2, 36, 2, 0, 0)); top.add(P(-1.5, 36, -1.5, 3, 3, 3, 8, 0)); top.add(P(-0.5, 39, -0.5, 1, 2, 1, 8, 6));
  const strips = [];
  for (let i = 0; i < 8; i++) {
    const s = new THREE.Group(); s.position.set(1 + i * 2, 24, 0);
    s.add(mcCube(0, 0, -0.25, 2, 11, 0.5, 16 + i * 2, 16, 64, 64, mat('flag', { color: 0x8a1c1c })));
    s.add(mcCube(0, 4, -0.35, 2, 3, 0.7, 16 + i * 2, 28, 64, 64, mat('flag', { color: 0xd04040 })));
    top.add(s); strips.push(s);
  }
  blk.add(top); blk.scale.set(1 / 16, 1 / 16, 1 / 16);
  root.add(blk); root.userData.strips = strips;
  target.set(0.5, 1.6, 0.5); orbitDist = 5.0; auto = true;
  info('Command Flag', 'Planting your first one founds your nation (a naming screen opens); the cloth is tinted the nation colour. Later flags are outposts that claim land.');
}

// vehicles: the generated part tree (entity model space, y down), animated like VehicleRenderer
function vehicleModel(vid) {
  const spec = VEHICLES[vid];
  const parts = {};
  const rig = new THREE.Group();
  for (const p of spec.parts) {
    const g = new THREE.Group();
    g.position.set(p.pivot[0], p.pivot[1], p.pivot[2]);
    g.rotation.set(p.rot[0], p.rot[1], p.rot[2]);
    for (const c of p.cubes) g.add(mcCube(c[0], c[1], c[2], c[3], c[4], c[5], c[6], c[7], 512, 512, mat(vid), c[8] || 0));
    parts[p.name] = g;
    (p.parent ? parts[p.parent] : rig).add(g);
  }
  const world = new THREE.Group(); world.scale.set(-1 / 16, -1 / 16, 1 / 16); world.add(rig);
  world.userData.parts = parts; world.userData.vid = vid;
  return world;
}

function showVehicles() {
  clear(); mode = 'turntable';
  const layout = { tank: [-3.2, 0, -1.5], jeep: [3.0, 0, -2.0], plane: [-2.0, 0, 4.0], heli: [3.5, 0, 3.0] };
  root.userData.vehicles = [];
  for (const vid of Object.keys(layout)) {
    const v = vehicleModel(vid);
    v.position.set(...layout[vid]);
    v.rotation.y = vid === 'plane' ? 0.5 : vid === 'heli' ? -0.6 : 0.3;
    root.add(v); root.userData.vehicles.push(v);
  }
  target.set(0, 1.2, 1); orbitDist = 14; auto = true;
  info('Vehicles', 'Tank (turret follows the driver, explosive shells + coax MG), jeep (ring-mounted MG, three seats), fighter plane (wing guns, bombs) and attack helicopter (chin gun, rockets). Models animate like in game.');
}

function showVehicle(vid) {
  clear(); mode = 'turntable';
  const v = vehicleModel(vid); root.add(v); root.userData.vehicles = [v];
  target.set(0, 1.0, 0); orbitDist = vid === 'plane' ? 9 : 8; auto = true;
  info(VEHICLES[vid].name, `${VEHICLES[vid].parts.reduce((n, p) => n + p.cubes.length, 0)} cuboids. Drag to orbit.`);
}

// ---------------------------------------------------------------- ui
const ui = document.getElementById('ui');
const buttons = [];
function addBtn(label, fn) { const b = document.createElement('button'); b.textContent = label; b.onclick = () => { buttons.forEach(x => x.classList.remove('on')); b.classList.add('on'); fn(); }; ui.appendChild(b); buttons.push(b); return b; }
SPEC.guns.forEach(g => addBtn(g.name, () => showGun(g)));
addBtn('FPS: carbine', () => showFps(SPEC.guns.find(g => g.id === 'fl4_carbine')));
addBtn('FPS: pistol', () => showFps(SPEC.guns.find(g => g.id === 'p9_sidearm')));
addBtn('FPS: marksman', () => showFps(SPEC.guns.find(g => g.id === 'r7_marksman')));
addBtn('Icons', showIcons);
addBtn('Soldiers', showSoldier);
addBtn('Command Flag', showFlag);
addBtn('Vehicles', showVehicles);
for (const vid of ['tank', 'plane', 'heli', 'jeep']) addBtn(VEHICLES[vid].name, () => showVehicle(vid));
const params = new URLSearchParams(location.search);
const start = params.get('view');
const startBtn = buttons.find(b => b.textContent.toLowerCase().startsWith((start || 'fl-4').toLowerCase())) || buttons[2];
startBtn.click();

function animate() {
  requestAnimationFrame(animate);
  clock += 1 / 60;
  if (mode === 'fps' && fps) {
    camera.fov = 70; camera.updateProjectionMatrix();
    camera.position.set(0, 0, 0); camera.lookAt(0, 0, -1);
    const ads = +document.getElementById('ads').value / 100;
    const st = fps.st, SCALE = 0.5, pistol = st.kind === 'PISTOL';
    const sightRise = (st.sightY - st.gripY) / 16 * SCALE;
    const hipX = pistol ? 0.30 : 0.27, hipY = pistol ? -0.38 : -0.30, hipZ = pistol ? -0.60 : -0.50, aimZ = pistol ? -0.55 : -0.42;
    const walk = document.getElementById('walk').checked ? 1 : 0;
    const damp = 1 - ads * 0.85;
    const f1 = clock * 2.2, f2 = 0.08 * walk;
    const bobX = Math.sin(f1 * Math.PI) * f2 * 0.35 * damp, bobY = -Math.abs(Math.cos(f1 * Math.PI) * f2) * 0.6 * damp;
    fps.hand.position.set(hipX + (0 - hipX) * ads + bobX, hipY + (-sightRise - hipY) * ads + bobY + Math.sin(clock * 1.6) * 0.0025 * damp, hipZ + (aimZ - hipZ) * ads);
    fps.hand.rotation.set(THREE.MathUtils.degToRad(Math.abs(Math.cos(f1 * Math.PI - 0.2) * f2) * 3 * damp), THREE.MathUtils.degToRad(-7 * (1 - ads) + Math.sin(f1 * Math.PI) * f2 * 2 * damp), THREE.MathUtils.degToRad(3 * (1 - ads) + Math.sin(f1 * Math.PI) * f2 * 2 * damp), 'YXZ');
    fps.flash.visible = document.getElementById('fire').checked && Math.floor(clock * 12) % 2 === 0;
    fps.flash.rotation.z = clock * 9;
    fps.hand.visible = !(st.scope && ads > 0.92);
  } else {
    camera.fov = 50; camera.updateProjectionMatrix();
    if (auto) orbitYaw += 0.004;
    camera.position.set(target.x + Math.sin(orbitYaw) * Math.cos(orbitPitch) * orbitDist, target.y + Math.sin(orbitPitch) * orbitDist, target.z + Math.cos(orbitYaw) * Math.cos(orbitPitch) * orbitDist);
    camera.lookAt(target);
    if (root.userData.vehicles) for (const v of root.userData.vehicles) {
      const P = v.userData.parts, t = clock;
      if (v.userData.vid === 'tank') { P.turret.rotation.y = Math.sin(t * 0.7) * 0.8; P.gun.rotation.x = Math.sin(t * 0.9) * 0.12 - 0.05; for (const k in P) if (k.startsWith('wheel')) P[k].rotation.x = t * 3; }
      if (v.userData.vid === 'plane') P.prop.rotation.z = t * 40;
      if (v.userData.vid === 'heli') { P.rotor.rotation.y = t * 25; P.tail_rotor.rotation.x = t * 50; }
      if (v.userData.vid === 'jeep') { for (const k in P) if (k.startsWith('wheel')) P[k].rotation.x = t * 4; P.mg.rotation.y = Math.sin(t * 0.8) * 1.2; }
    }
    if (root.userData.strips) root.userData.strips.forEach((s, i) => { const ph = clock * 3.6 - i * 0.55, amp = 0.9 * (i + 1) / 8; s.position.z = Math.sin(ph) * amp; s.rotation.y = -Math.cos(ph) * amp * 0.55 * 0.31; });
  }
  renderer.render(scene, camera);
}
animate();
</script>
</body>
</html>
'''
html = html.replace('__VEHICLES__', json.dumps(vehicles)).replace('__SPEC__', json.dumps(spec)).replace('__MODELS__', json.dumps(models)).replace('__STATS__', json.dumps(stats)).replace('__TEX__', json.dumps(textures))
os.makedirs(OUT, exist_ok=True)
with open(os.path.join(OUT, 'index.html'), 'w') as f:
    f.write(html)
print('preview written', len(html) // 1024, 'KB')
