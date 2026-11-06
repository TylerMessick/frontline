package com.messickmedia.frontline.gun;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Per-player firearm state (attachment). Saved: kills and shots. Live-only: cooldowns, reload, bloom, aim. */
public class Trooper {
    public static final Codec<Trooper> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("kills", 0).forGetter(t -> t.kills),
            Codec.INT.optionalFieldOf("shots", 0).forGetter(t -> t.shots),
            Codec.INT.optionalFieldOf("hits", 0).forGetter(t -> t.hits)
    ).apply(i, Trooper::new));

    public int kills, shots, hits;

    // live-only
    public int fireCd;
    public int reloadTicks, reloadMax;
    public float bloom;
    public boolean ads;
    public int lastSyncHash;
    /** Item id of the gun being reloaded, so swapping weapons cancels it. */
    public String reloadingGun = "";

    public Trooper() { this(0, 0, 0); }

    public Trooper(int kills, int shots, int hits) {
        this.kills = kills; this.shots = shots; this.hits = hits;
    }

    public boolean reloading() { return reloadTicks > 0; }

    public void cancelReload() { reloadTicks = 0; reloadMax = 0; reloadingGun = ""; }
}
