package com.messickmedia.frontline.gun;

import com.messickmedia.frontline.Config;
import com.messickmedia.frontline.entity.Bullet;
import com.messickmedia.frontline.net.ModNetwork;
import com.messickmedia.frontline.registry.ModRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/** Server-side firing, reloading and per-tick upkeep for players; soldiers reuse {@link #shoot}. */
public final class GunLogic {
    private GunLogic() {}

    public static Trooper trooper(ServerPlayer p) { return p.getData(ModRegistries.TROOPER); }

    /** The client asked to fire along {@code dir} (its camera ray; in over-the-shoulder mode it differs from the look vector). */
    public static void fire(ServerPlayer p, Vec3 dir) {
        ItemStack stack = p.getMainHandItem();
        GunInfo gun = GunItem.gunOf(stack);
        if (gun == null) return;
        Trooper t = trooper(p);
        if (t.fireCd > 0 || t.reloading()) return;
        // the aim direction must be roughly where the player is facing
        Vec3 look = p.getLookAngle();
        if (dir.lengthSqr() < 0.5D || dir.normalize().dot(look) < 0.55D) dir = look;
        int rounds = GunItem.rounds(stack);
        if (rounds <= 0) {
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), ModRegistries.S_DRY.get(), SoundSource.PLAYERS, 0.8F, 1.0F);
            t.fireCd = 6;
            reload(p);
            return;
        }
        GunItem.setRounds(stack, rounds - 1);
        t.fireCd = gun.fireTicks();
        t.shots++;
        float spread = (t.ads ? gun.adsSpread() : gun.hipSpread()) + t.bloom;
        if (p.isSprinting()) spread *= 1.6F;
        if (!p.onGround()) spread *= 1.4F;
        if (p.isCrouching()) spread *= 0.8F;
        t.bloom = Math.min(t.bloom + gun.bloom(), gun.hipSpread() * 2.5F);
        Vec3 from = p.getEyePosition().add(0, -0.08D, 0).add(dir.normalize().scale(0.4D));
        shoot((ServerLevel) p.level(), p, gun, from, dir.normalize(), spread, false);
    }

    /** Spawns the bullets, the muzzle flash and the shot sound for one trigger pull. */
    public static void shoot(ServerLevel level, LivingEntity shooter, GunInfo gun, Vec3 from, Vec3 dir, float spreadDeg, boolean soldier) {
        RandomSource rand = level.random;
        int style = gun.blast() > 0 ? 5 : gun.kind() == GunInfo.Kind.SHOTGUN ? 2 : (gun.kind() == GunInfo.Kind.SNIPER || gun.kind() == GunInfo.Kind.LMG) ? 1 : 0;
        for (int i = 0; i < gun.pellets(); i++) {
            Vec3 d = cone(rand, dir, spreadDeg);
            Bullet b = Bullet.fire(level, shooter, from, d, gun.bulletSpeed(), gun.damage(), gun.bulletLife(), style);
            if (gun.blast() > 0) b.blast(gun.blast());
        }
        Vec3 muzzle = from.add(dir.scale(0.9D));
        level.sendParticles(ModRegistries.MUZZLE.get(), muzzle.x, muzzle.y, muzzle.z, 1, 0, 0, 0, 0);
        level.sendParticles(ModRegistries.SMOKE.get(), muzzle.x, muzzle.y, muzzle.z, 2, 0.05, 0.05, 0.05, 0.02);
        // spent casing
        Vec3 right = new Vec3(-dir.z, 0, dir.x).normalize();
        Vec3 port = from.add(right.scale(0.25D)).add(0, -0.1D, 0);
        level.sendParticles(ModRegistries.BRASS.get(), port.x, port.y, port.z, 1, 0, 0, 0, 0);
        SoundEvent s = sound(gun);
        float vol = switch (gun.kind()) { case PISTOL -> 1.6F; case SMG -> 1.5F; case SNIPER -> 3.5F; case SHOTGUN -> 2.8F; default -> 2.2F; };
        level.playSound(null, from.x, from.y, from.z, s, soldier ? SoundSource.HOSTILE : SoundSource.PLAYERS, vol * (soldier ? 0.8F : 1.0F), 0.95F + rand.nextFloat() * 0.1F);
    }

    public static SoundEvent sound(GunInfo gun) {
        return switch (gun.sound()) {
            case PISTOL -> ModRegistries.S_PISTOL.get();
            case SMG -> ModRegistries.S_SMG.get();
            case RIFLE -> ModRegistries.S_RIFLE.get();
            case BATTLE -> ModRegistries.S_BATTLE.get();
            case SHOTGUN -> ModRegistries.S_SHOTGUN.get();
            case SNIPER -> ModRegistries.S_SNIPER.get();
            case LMG -> ModRegistries.S_LMG.get();
        };
    }

    /** A random direction inside a cone of the given half-angle around {@code dir}. */
    public static Vec3 cone(RandomSource rand, Vec3 dir, float halfAngleDeg) {
        if (halfAngleDeg <= 0.001F) return dir;
        Vec3 up = Math.abs(dir.y) > 0.9D ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 right = dir.cross(up).normalize();
        Vec3 u = right.cross(dir).normalize();
        double ang = Math.toRadians(halfAngleDeg) * Math.sqrt(rand.nextDouble());
        double rot = rand.nextDouble() * Math.PI * 2;
        return dir.scale(Math.cos(ang)).add(right.scale(Math.sin(ang) * Math.cos(rot))).add(u.scale(Math.sin(ang) * Math.sin(rot))).normalize();
    }

    public static void reload(ServerPlayer p) {
        ItemStack stack = p.getMainHandItem();
        GunInfo gun = GunItem.gunOf(stack);
        if (gun == null) return;
        Trooper t = trooper(p);
        if (t.reloading()) return;
        if (GunItem.rounds(stack) >= gun.magSize()) return;
        if (!Config.INFINITE_AMMO.get() && countAmmo(p, gun) <= 0) {
            p.displayClientMessage(Component.translatable("msg.frontline.no_ammo", Component.translatable("item.frontline." + gun.ammo())), true);
            return;
        }
        t.reloadTicks = t.reloadMax = gun.reloadTicks();
        t.reloadingGun = gun.id();
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), ModRegistries.S_MAG_OUT.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    private static void finishReload(ServerPlayer p, Trooper t) {
        ItemStack stack = p.getMainHandItem();
        GunInfo gun = GunItem.gunOf(stack);
        if (gun == null || !gun.id().equals(t.reloadingGun)) { t.cancelReload(); return; }
        int need = gun.magSize() - GunItem.rounds(stack);
        int have = Config.INFINITE_AMMO.get() ? need : takeAmmo(p, gun, need);
        GunItem.setRounds(stack, GunItem.rounds(stack) + have);
        t.cancelReload();
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), gun.kind() == GunInfo.Kind.SNIPER || gun.kind() == GunInfo.Kind.SHOTGUN ? ModRegistries.S_BOLT.get() : ModRegistries.S_MAG_IN.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    public static int countAmmo(ServerPlayer p, GunInfo gun) {
        int n = 0;
        for (ItemStack s : p.getInventory().items) if (isAmmo(s, gun)) n += s.getCount();
        if (isAmmo(p.getOffhandItem(), gun)) n += p.getOffhandItem().getCount();
        return n;
    }

    private static boolean isAmmo(ItemStack s, GunInfo gun) {
        return !s.isEmpty() && s.getItem() instanceof AmmoItem a && a.ammoId().equals(gun.ammo());
    }

    private static int takeAmmo(ServerPlayer p, GunInfo gun, int want) {
        int got = 0;
        for (ItemStack s : p.getInventory().items) {
            if (got >= want) break;
            if (isAmmo(s, gun)) { int k = Math.min(want - got, s.getCount()); s.shrink(k); got += k; }
        }
        if (got < want && isAmmo(p.getOffhandItem(), gun)) { int k = Math.min(want - got, p.getOffhandItem().getCount()); p.getOffhandItem().shrink(k); got += k; }
        return got;
    }

    /** Keeps the night-vision effect on while the goggles are down; removes only the effect we added. */
    static void tickGoggles(ServerPlayer p, Trooper t) {
        boolean wearing = p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).getItem() instanceof com.messickmedia.frontline.item.TacticalArmorItem a && a.nvg();
        if (!wearing) t.nvMode = 0;
        var nv = net.minecraft.world.effect.MobEffects.NIGHT_VISION;
        if (t.nvMode > 0) {
            var cur = p.getEffect(nv);
            if (cur == null || (t.nvApplied && cur.getDuration() < 260)) {
                p.addEffect(new net.minecraft.world.effect.MobEffectInstance(nv, 400, 0, true, false, false));
                t.nvApplied = true;
            }
        } else if (t.nvApplied) {
            var cur = p.getEffect(nv);
            if (cur != null && cur.isAmbient() && !cur.isVisible() && cur.getDuration() <= 400) p.removeEffect(nv);
            t.nvApplied = false;
        }
    }

    public static void tick(ServerPlayer p) {
        Trooper t = trooper(p);
        tickGoggles(p, t);
        if (t.fireCd > 0) t.fireCd--;
        t.bloom = Math.max(0.0F, t.bloom * 0.86F - 0.02F);
        GunInfo gun = GunItem.gunOf(p.getMainHandItem());
        if (t.reloading()) {
            if (gun == null || !gun.id().equals(t.reloadingGun)) t.cancelReload();
            else if (--t.reloadTicks <= 0) finishReload(p, t);
            else if (t.reloadTicks == t.reloadMax / 2 && (gun.kind() == GunInfo.Kind.SHOTGUN || gun.kind() == GunInfo.Kind.SNIPER)) {
                p.level().playSound(null, p.getX(), p.getY(), p.getZ(), ModRegistries.S_BOLT.get(), SoundSource.PLAYERS, 0.7F, 1.2F);
            }
        }
        if (gun == null) t.ads = false;
        if (t.ads && p.isSprinting()) p.setSprinting(false);
        int ammoCount = gun == null ? 0 : countAmmo(p, gun);
        int hash = (t.reloadTicks * 31 + t.reloadMax) * 31 + Float.floatToIntBits(t.bloom) * 7 + (t.ads ? 1 : 0) + ammoCount * 131 + t.kills * 17;
        if (p.tickCount % 20 == 0) syncArmory(p, t);
        if (hash != t.lastSyncHash || p.tickCount % 40 == 0) {
            t.lastSyncHash = hash;
            PacketDistributor.sendToPlayer(p, new ModNetwork.GunSync(t.reloadTicks, t.reloadMax, t.bloom, ammoCount, t.kills, t.shots, t.hits));
        }
    }

    // ---------------------------------------------------------------- weapon wheel

    /** action 0 = take the gun in this slot into the hand (swapping the held gun into the slot), 1 = store the held gun here, 2 = drop the slot into the inventory. */
    public static void armory(ServerPlayer p, int action, int slot) {
        Trooper t = trooper(p);
        if (slot < 0 || slot >= Trooper.ARMORY_SLOTS) return;
        ItemStack held = p.getMainHandItem();
        ItemStack inSlot = t.armory.get(slot);
        switch (action) {
            case 0 -> {
                if (inSlot.isEmpty()) { if (held.getItem() instanceof GunItem) { t.armory.set(slot, held.copy()); p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY); } }
                else {
                    if (held.getItem() instanceof GunItem) t.armory.set(slot, held.copy());
                    else { t.armory.set(slot, ItemStack.EMPTY); if (!held.isEmpty()) p.getInventory().placeItemBackInInventory(held.copy()); }
                    p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, inSlot.copy());
                }
                t.cancelReload();
                p.level().playSound(null, p.getX(), p.getY(), p.getZ(), ModRegistries.S_MAG_IN.get(), SoundSource.PLAYERS, 0.6F, 1.3F);
            }
            case 1 -> {
                if (!(held.getItem() instanceof GunItem)) return;
                if (!inSlot.isEmpty()) p.getInventory().placeItemBackInInventory(inSlot.copy());
                t.armory.set(slot, held.copy());
                p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                t.cancelReload();
            }
            case 2 -> {
                if (inSlot.isEmpty()) return;
                p.getInventory().placeItemBackInInventory(inSlot.copy());
                t.armory.set(slot, ItemStack.EMPTY);
            }
        }
        syncArmory(p, t);
    }

    public static void syncArmory(ServerPlayer p, Trooper t) {
        int h = t.armoryHash();
        if (h == t.lastArmoryHash) return;
        t.lastArmoryHash = h;
        PacketDistributor.sendToPlayer(p, new ModNetwork.ArmorySync(new java.util.ArrayList<>(t.armory)));
    }

    public static float adsSpreadNow(Trooper t, GunInfo gun) {
        return Mth.clamp((t.ads ? gun.adsSpread() : gun.hipSpread()) + t.bloom, 0, 30);
    }
}
