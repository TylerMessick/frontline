# Frontline

Army mod for NeoForge 1.21.1. Blocky, realistically proportioned firearms with first-person and over-the-shoulder
shooting, villager recruiting, Command Flag bases, and named nations with their own armies.

## Weapons

| Gun | Ammo | Mag | Fire | Notes |
| --- | --- | --- | --- | --- |
| P9 Sidearm | 9mm | 15 | semi, 400 rpm | tritium three-dot sights |
| MP9 Submachine Gun | 9mm | 32 | auto, 1200 rpm | vented shroud, vertical grip, folding stock |
| FL-4 Carbine | 5.56 | 30 | auto, 600 rpm | railed handguard, birdcage brake, collapsible stock |
| VK-47 Battle Rifle | 7.62 | 30 | auto, 600 rpm | wood furniture, curved mag, slant brake |
| SG-12 Pump Shotgun | 12 ga | 6 | pump | 8 pellets, tube magazine, ribbed pump |
| R7 Marksman Rifle | .308 | 5 | bolt | 4x scope overlay, bipod, brake |
| LM-249 Light Machine Gun | 5.56 | 100 | auto, 600 rpm | belt box, carry handle, bipod |

- **Left mouse** fires (hold for automatics). **Right mouse** aims down the sights (FOV zoom, tighter spread;
  sneak + right-click still interacts with blocks). **R** reloads from ammo in your inventory. **V** toggles the
  over-the-shoulder camera; the crosshair stays true because bullets are fired toward what the camera ray hits.
- Recoil kicks the camera and the gun; spread blooms with sustained fire and shrinks when crouched or aimed.
- Bullets are tracer entities with light drop after one second. Headshots deal double damage. Hit markers and
  kill markers sound on the shooter's client.
- Every gun is a JSON element model (28 to 54 cuboids each) textured from one material atlas, built by
  `gen/gen_guns.py`; sounds are synthesised by `gen/gen_sounds.py`.

## Nations and armies

1. Craft a **Command Flag** (red wool, gold nugget, sticks) and place it. The first flag opens the naming screen:
   nation name, army name, banner colour. Each flag claims 48 blocks around it (config); more flags are outposts.
2. Craft **Enlistment Papers** (paper, paper, ink sac, emerald) and right-click villagers to recruit them. Soldiers
   get a surname, a uniform in your colour, and rank up with kills (Pvt. → Cpl. → Sgt. → Lt.).
3. Right-click a soldier with a gun to arm them. Empty hand cycles their order: **Follow**, **Hold position**,
   **Patrol the base**. Sneak + right-click discharges them (they become a villager again).
4. The **Command Baton**: right-click to have nearby troops follow you, sneak + right-click to hold, right-click a
   block to rally them there.
5. `/nation info|list|invite <player>|join <name>|leave|kick|rename <name>|army <name>|color <name|hex>|war <name>|peace <name>|recall|disband`.
   Soldiers fight hostile mobs always, and enemy nations' soldiers and players only while at war.

## Building

CI (`.github/workflows/build.yml`) builds the jar to the `jar` branch and runs client/server smoke tests to the
`smoke-log` branch. Locally: `./gradlew build`.

`preview/index.html` is a three.js page with every gun, the first-person view (same maths as `FirstPersonGun`),
the soldier model and the flag. Regenerate with `python3 gen/gen_guns.py && python3 gen/gen_assets.py &&
python3 gen/gen_sounds.py && python3 gen/build_preview.py`.
