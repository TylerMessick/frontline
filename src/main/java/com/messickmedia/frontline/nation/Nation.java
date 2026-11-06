package com.messickmedia.frontline.nation;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** A player-founded country with a named army, members, Command Flag bases and wars. */
public class Nation {
    public record Base(String dimension, BlockPos pos) {
        public static final Codec<Base> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("dim").forGetter(Base::dimension),
                BlockPos.CODEC.fieldOf("pos").forGetter(Base::pos)).apply(i, Base::new));
    }

    public static final Codec<Nation> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(n -> n.id),
            Codec.STRING.fieldOf("name").forGetter(n -> n.name),
            Codec.STRING.fieldOf("army").forGetter(n -> n.army),
            Codec.INT.fieldOf("color").forGetter(n -> n.color),
            UUIDUtil.CODEC.fieldOf("leader").forGetter(n -> n.leader),
            UUIDUtil.CODEC.listOf().fieldOf("members").forGetter(n -> new ArrayList<>(n.members)),
            Base.CODEC.listOf().fieldOf("bases").forGetter(n -> new ArrayList<>(n.bases)),
            UUIDUtil.CODEC.listOf().optionalFieldOf("wars", List.of()).forGetter(n -> new ArrayList<>(n.wars)),
            Codec.INT.optionalFieldOf("kills", 0).forGetter(n -> n.kills),
            Codec.INT.optionalFieldOf("recruits", 0).forGetter(n -> n.recruits),
            Codec.LONG.optionalFieldOf("founded", 0L).forGetter(n -> n.founded),
            Codec.STRING.optionalFieldOf("flag", "").forGetter(n -> n.flag),
            Codec.INT.optionalFieldOf("uniform", 0).forGetter(n -> n.uniform)
    ).apply(i, Nation::new));

    public final UUID id;
    public String name, army;
    public int color;
    public UUID leader;
    public final Set<UUID> members = new HashSet<>();
    public final List<Base> bases = new ArrayList<>();
    public final Set<UUID> wars = new HashSet<>();
    public int kills, recruits;
    public long founded;
    /** Painted flag (see {@link FlagDesign}); empty = plain colour. */
    public String flag = "";
    /** Camo issued to recruits: 0 woodland, 1 arctic, 2 black ops. */
    public int uniform;

    public Nation(UUID id, String name, String army, int color, UUID leader, List<UUID> members, List<Base> bases, List<UUID> wars, int kills, int recruits, long founded, String flag, int uniform) {
        this.id = id; this.name = name; this.army = army; this.color = color; this.leader = leader;
        this.members.addAll(members); this.bases.addAll(bases); this.wars.addAll(wars);
        this.kills = kills; this.recruits = recruits; this.founded = founded;
        this.flag = FlagDesign.valid(flag) ? flag : FlagDesign.starter(color);
        this.uniform = Math.floorMod(uniform, 3);
    }

    public Nation(String name, String army, int color, UUID leader, long founded) {
        this(UUID.randomUUID(), name, army, color, leader, List.of(leader), List.of(), List.of(), 0, 0, founded, "", 0);
    }

    public boolean isMember(UUID player) { return members.contains(player); }

    public boolean isLeader(UUID player) { return leader.equals(player); }

    /** The nation's banner colour as 0xRRGGBB with the colour's brighter text variant. */
    public int textColor() {
        int r = (color >> 16) & 255, g = (color >> 8) & 255, b = color & 255;
        r = Math.min(255, r + 60); g = Math.min(255, g + 60); b = Math.min(255, b + 60);
        return (r << 16) | (g << 8) | b;
    }
}
