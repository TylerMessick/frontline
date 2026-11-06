package com.messickmedia.frontline.gun;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.messickmedia.frontline.gun.GunInfo.Kind.*;
import static com.messickmedia.frontline.gun.GunInfo.Sound;

/** The armoury. Keep gen/gen_guns.py's GUNS list in the same order; the codex and preview read both. */
public final class Guns {
    private Guns() {}

    public static final String AMMO_9MM = "ammo_9mm", AMMO_556 = "ammo_556", AMMO_762 = "ammo_762", AMMO_12G = "ammo_12g", AMMO_308 = "ammo_308";

    //                                        id              kind     ammo      mag fire auto  dmg  pel speed life hip  ads  bloom rPit rYaw reload fov   scope  muzzle x y z            sound          speed
    public static final GunInfo P9 = new GunInfo("p9_sidearm", PISTOL, AMMO_9MM, 15, 3, false, 4.5F, 1, 9.0F, 40, 1.6F, 0.4F, 0.9F, 2.6F, 0.8F, 32, 0.80F, false, 8.0F, 10.15F, -15.0F, Sound.PISTOL, 1.0F, 12.40F, 5.10F, -5.45F);
    public static final GunInfo MP9 = new GunInfo("mp9_smg", SMG, AMMO_9MM, 32, 1, true, 3.2F, 1, 9.0F, 45, 2.4F, 0.9F, 0.45F, 1.3F, 0.7F, 44, 0.75F, false, 8.0F, 10.10F, -15.0F, Sound.SMG, 1.0F, 13.80F, 4.70F, 4.90F);
    public static final GunInfo FL4 = new GunInfo("fl4_carbine", RIFLE, AMMO_556, 30, 2, true, 5.5F, 1, 12.0F, 60, 1.8F, 0.25F, 0.5F, 1.9F, 0.6F, 48, 0.68F, false, 8.0F, 10.25F, -15.0F, Sound.RIFLE, 0.95F, 13.70F, 4.80F, 12.50F);
    public static final GunInfo VK47 = new GunInfo("vk47_rifle", RIFLE, AMMO_762, 30, 2, true, 7.0F, 1, 11.0F, 60, 2.3F, 0.4F, 0.7F, 2.8F, 1.1F, 52, 0.68F, false, 8.0F, 10.00F, -15.0F, Sound.BATTLE, 0.95F, 14.30F, 4.75F, 16.10F);
    public static final GunInfo SG12 = new GunInfo("sg12_shotgun", SHOTGUN, AMMO_12G, 6, 16, false, 2.6F, 8, 8.0F, 24, 5.5F, 3.5F, 1.0F, 5.0F, 1.5F, 70, 0.78F, false, 8.0F, 10.30F, -15.0F, Sound.SHOTGUN, 0.95F, 11.90F, 6.00F, 8.00F);
    public static final GunInfo R7 = new GunInfo("r7_marksman", SNIPER, AMMO_308, 5, 24, false, 24.0F, 1, 16.0F, 90, 4.0F, 0.0F, 0.3F, 4.5F, 1.0F, 64, 0.25F, true, 8.0F, 11.00F, -15.0F, Sound.SNIPER, 0.90F, 15.40F, 5.50F, 17.54F);
    public static final GunInfo LM249 = new GunInfo("lm249_lmg", LMG, AMMO_556, 100, 2, true, 5.0F, 1, 12.0F, 60, 3.2F, 1.1F, 0.35F, 1.6F, 1.2F, 110, 0.72F, false, 8.0F, 10.80F, -15.0F, Sound.LMG, 0.85F, 14.10F, 4.70F, 19.92F);

    public static final List<GunInfo> ALL = List.of(P9, MP9, FL4, VK47, SG12, R7, LM249);
    public static final List<String> AMMO = List.of(AMMO_9MM, AMMO_556, AMMO_762, AMMO_12G, AMMO_308);

    private static final Map<String, GunInfo> BY_ID = new LinkedHashMap<>();
    static { for (GunInfo g : ALL) BY_ID.put(g.id(), g); }

    public static GunInfo byId(String id) { return BY_ID.get(id); }
}
