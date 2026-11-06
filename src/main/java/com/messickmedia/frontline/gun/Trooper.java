package com.messickmedia.frontline.gun;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Per-player firearm state (attachment). Saved: kills and shots. Live-only: cooldowns, reload, bloom, aim. */
public class Trooper {
    public static final Codec<Trooper> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("kills", 0).forGetter(t -> t.kills),
            Codec.INT.optionalFieldOf("shots", 0).forGetter(t -> t.shots),
            Codec.INT.optionalFieldOf("hits", 0).forGetter(t -> t.hits),
            ItemStack.OPTIONAL_CODEC.listOf().optionalFieldOf("armory", List.of()).forGetter(t -> new ArrayList<>(t.armory))
    ).apply(i, Trooper::new));

    public static final int ARMORY_SLOTS = 8;

    public int kills, shots, hits;
    /** The weapon wheel: a separate inventory of guns the player can swap to with the wheel key. */
    public final NonNullList<ItemStack> armory = NonNullList.withSize(ARMORY_SLOTS, ItemStack.EMPTY);

    // live-only
    public int fireCd;
    public int reloadTicks, reloadMax;
    public float bloom;
    public boolean ads;
    public int lastSyncHash, lastArmoryHash = -1;
    /** Goggles: 0 off, 1 night vision, 2 thermal. nvApplied = we put the night-vision effect on. */
    public int nvMode;
    public boolean nvApplied;
    /** Item id of the gun being reloaded, so swapping weapons cancels it. */
    public String reloadingGun = "";

    public Trooper() { this(0, 0, 0, List.of()); }

    public Trooper(int kills, int shots, int hits, List<ItemStack> armory) {
        this.kills = kills; this.shots = shots; this.hits = hits;
        for (int i = 0; i < Math.min(ARMORY_SLOTS, armory.size()); i++) this.armory.set(i, armory.get(i));
    }

    public int armoryHash() {
        int h = 0;
        for (ItemStack s : armory) h = h * 31 + (s.isEmpty() ? 0 : s.getItem().hashCode() * 7 + GunItem.rounds(s));
        return h;
    }

    public boolean reloading() { return reloadTicks > 0; }

    public void cancelReload() { reloadTicks = 0; reloadMax = 0; reloadingGun = ""; }
}
