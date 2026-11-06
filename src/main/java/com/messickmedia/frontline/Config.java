package com.messickmedia.frontline;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server config (frontline-server.toml in the world's serverconfig folder). */
public final class Config {
    private Config() {}

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.DoubleValue GUN_DAMAGE;
    public static final ModConfigSpec.DoubleValue SOLDIER_DAMAGE;
    public static final ModConfigSpec.IntValue CLAIM_RADIUS;
    public static final ModConfigSpec.IntValue MAX_SOLDIERS;
    public static final ModConfigSpec.BooleanValue FRIENDLY_FIRE;
    public static final ModConfigSpec.BooleanValue HEADSHOTS;
    public static final ModConfigSpec.BooleanValue INFINITE_AMMO;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        GUN_DAMAGE = b.comment("Damage multiplier for player-fired bullets.").defineInRange("gunDamage", 1.0D, 0.0D, 20.0D);
        SOLDIER_DAMAGE = b.comment("Damage multiplier for soldier-fired bullets.").defineInRange("soldierDamage", 0.7D, 0.0D, 20.0D);
        CLAIM_RADIUS = b.comment("Radius in blocks a Command Flag claims for its nation.").defineInRange("claimRadius", 48, 8, 256);
        MAX_SOLDIERS = b.comment("Maximum living soldiers a nation may recruit.").defineInRange("maxSoldiers", 60, 1, 1000);
        FRIENDLY_FIRE = b.comment("Bullets hurt members and soldiers of your own nation.").define("friendlyFire", false);
        HEADSHOTS = b.comment("Bullets that strike the head deal double damage.").define("headshots", true);
        INFINITE_AMMO = b.comment("Reloading never consumes ammunition items.").define("infiniteAmmo", false);
        SPEC = b.build();
    }
}
