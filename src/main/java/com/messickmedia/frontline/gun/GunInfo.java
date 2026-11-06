package com.messickmedia.frontline.gun;

/**
 * Stats for one firearm. Everything about how it shoots lives here; the look lives in the generated item model
 * (gen/gen_guns.py writes both the JSON models and the preview from the same part list).
 *
 * @param fireTicks   ticks between shots (2 = 600 rpm, 1 = 1200 rpm)
 * @param hipSpread   cone half-angle in degrees when firing from the hip
 * @param adsSpread   cone half-angle while aiming down sights
 * @param bloom       extra spread added per shot, decays every tick
 * @param recoilPitch camera kick upwards per shot (degrees)
 * @param recoilYaw   random sideways kick per shot (degrees)
 * @param adsFov      FOV multiplier while aiming (0.25 = 4x scope)
 * @param muzzleX     muzzle position in model units (x across, y up, z along the gun; the muzzle is at the low-Z end)
 * @param sightY      height (model units) of the sight line the camera looks along when aiming
 * @param gripY       centre of the pistol grip (model units); the hand holds the gun here
 */
public record GunInfo(String id, Kind kind, String ammo, int magSize, int fireTicks, boolean auto, float damage, int pellets,
                      float bulletSpeed, int bulletLife, float hipSpread, float adsSpread, float bloom, float recoilPitch, float recoilYaw,
                      int reloadTicks, float adsFov, boolean scope, float muzzleX, float muzzleY, float muzzleZ, Sound sound, float moveSpeed,
                      float sightY, float gripY, float gripZ) {

    public enum Kind { PISTOL, SMG, RIFLE, SHOTGUN, SNIPER, LMG }

    public enum Sound { PISTOL, SMG, RIFLE, BATTLE, SHOTGUN, SNIPER, LMG }

    public boolean twoHanded() { return kind != Kind.PISTOL; }

    /** Roughly how far (blocks) a bullet flies before it drops out: speed x life. */
    public float range() { return bulletSpeed * bulletLife; }

    public int rpm() { return 1200 / Math.max(1, fireTicks); }
}
