package com.messickmedia.frontline.client;

import com.messickmedia.frontline.gun.Trooper;
import com.messickmedia.frontline.net.ModNetwork;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;

/** Everything the client knows about its own trooper and nation, plus the animation state of the gun in hand. */
public final class ClientState {
    private ClientState() {}

    // synced from the server
    public static int reloadTicks, reloadMax;
    public static float bloom;
    public static int ammoCount, kills, shots, hits;
    public static String nation = "", army = "";
    public static int nationColor;
    public static int soldiers, bases;
    public static String claim = "";
    public static int claimColor;
    public static boolean claimHostile;

    public static final NonNullList<ItemStack> armory = NonNullList.withSize(Trooper.ARMORY_SLOTS, ItemStack.EMPTY);
    /** The camera type the player had before mounting a vehicle, restored on dismount (-1 = not riding). */
    public static int cameraBeforeRide = -1;
    public static int vehicleFireCd;

    // local
    public static boolean ots;
    public static boolean ads;
    /** 0..1 aim-down-sights blend, smoothed per frame. */
    public static float adsBlend, adsBlendO;
    public static int fireCd;
    /** Visual recoil of the gun model (0..1) and of the camera (degrees), both decaying. */
    public static float kick, kickO;
    public static float camPitch, camPitchO, camYaw, camYawO;
    public static int flashTicks;
    public static int hitTicks, hitKind;
    public static int claimBannerTicks;
    public static String claimBanner = "";
    public static int claimBannerColor;
    public static float swayX, swayY, swayXO, swayYO;
    public static float lastYaw, lastPitch;
    public static int holdTicks;
    public static int lastReloadTicks;

    public static void accept(ModNetwork.GunSync s) {
        reloadTicks = s.reloadTicks(); reloadMax = s.reloadMax(); bloom = s.bloom(); ammoCount = s.ammoCount();
        kills = s.kills(); shots = s.shots(); hits = s.hits();
    }

    public static void accept(ModNetwork.NationSync s) {
        nation = s.nation(); army = s.army(); nationColor = s.color(); soldiers = s.soldiers(); bases = s.bases();
        if (!s.claim().equals(claim)) {
            claimBanner = s.claim();
            claimBannerColor = s.claimColor();
            claimBannerTicks = s.claim().isEmpty() ? 0 : 70;
        }
        claim = s.claim(); claimColor = s.claimColor(); claimHostile = s.claimHostile();
    }

    public static void accept(ModNetwork.ArmorySync s) {
        for (int i = 0; i < armory.size(); i++) armory.set(i, i < s.stacks().size() ? s.stacks().get(i) : ItemStack.EMPTY);
    }

    public static void accept(ModNetwork.HitMark m) {
        hitTicks = 10;
        hitKind = m.kind();
    }

    public static boolean reloading() { return reloadTicks > 0; }

    public static void clear() {
        reloadTicks = reloadMax = 0; bloom = 0; ammoCount = 0;
        nation = ""; army = ""; claim = ""; claimBannerTicks = 0;
        ads = false; adsBlend = adsBlendO = 0; fireCd = 0; kick = kickO = 0; camPitch = camYaw = camPitchO = camYawO = 0; flashTicks = 0; hitTicks = 0;
    }

    /** Called once per client tick to advance the animation state. */
    public static void tick(boolean holdingGun) {
        adsBlendO = adsBlend;
        float target = holdingGun && ads && !reloading() ? 1.0F : 0.0F;
        adsBlend += (target - adsBlend) * 0.35F;
        if (Math.abs(target - adsBlend) < 0.01F) adsBlend = target;
        kickO = kick;
        kick *= 0.62F;
        if (kick < 0.01F) kick = 0;
        camPitchO = camPitch; camYawO = camYaw;
        camPitch *= 0.55F; camYaw *= 0.55F;
        if (Math.abs(camPitch) < 0.01F) camPitch = 0;
        if (Math.abs(camYaw) < 0.01F) camYaw = 0;
        if (fireCd > 0) fireCd--;
        if (vehicleFireCd > 0) vehicleFireCd--;
        if (flashTicks > 0) flashTicks--;
        if (hitTicks > 0) hitTicks--;
        if (claimBannerTicks > 0) claimBannerTicks--;
        swayXO = swayX; swayYO = swayY;
        swayX *= 0.7F; swayY *= 0.7F;
        holdTicks = holdingGun ? Math.min(holdTicks + 1, 1000) : 0;
    }
}
