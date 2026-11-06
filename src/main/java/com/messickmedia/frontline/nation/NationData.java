package com.messickmedia.frontline.nation;

import com.mojang.serialization.Codec;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Every nation in the world, saved once in the overworld's data folder as frontline_nations.dat. */
public class NationData extends SavedData {
    private static final String NAME = "frontline_nations";
    private static final Codec<List<Nation>> LIST = Nation.CODEC.listOf();

    private final Map<UUID, Nation> nations = new LinkedHashMap<>();

    public static NationData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(NationData::new, NationData::load, null), NAME);
    }

    private static NationData load(CompoundTag tag, HolderLookup.Provider provider) {
        NationData d = new NationData();
        if (tag.contains("nations")) {
            LIST.parse(NbtOps.INSTANCE, tag.get("nations")).result().ifPresent(list -> { for (Nation n : list) d.nations.put(n.id, n); });
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        Tag list = LIST.encodeStart(NbtOps.INSTANCE, new ArrayList<>(nations.values())).result().orElseGet(net.minecraft.nbt.ListTag::new);
        tag.put("nations", list);
        return tag;
    }

    public Collection<Nation> all() { return nations.values(); }

    @Nullable
    public Nation byId(@Nullable UUID id) { return id == null ? null : nations.get(id); }

    @Nullable
    public Nation byName(String name) {
        for (Nation n : nations.values()) if (n.name.equalsIgnoreCase(name)) return n;
        return null;
    }

    @Nullable
    public Nation ofPlayer(UUID player) {
        for (Nation n : nations.values()) if (n.isMember(player)) return n;
        return null;
    }

    public void add(Nation n) { nations.put(n.id, n); setDirty(); }

    public void remove(UUID id) { nations.remove(id); setDirty(); }
}
