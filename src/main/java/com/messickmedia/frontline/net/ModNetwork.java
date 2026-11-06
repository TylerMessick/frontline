package com.messickmedia.frontline.net;

import com.messickmedia.frontline.Frontline;
import com.messickmedia.frontline.GameEvents;
import com.messickmedia.frontline.gun.GunLogic;
import com.messickmedia.frontline.gun.Trooper;
import com.messickmedia.frontline.registry.ModRegistries;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.function.Consumer;

public final class ModNetwork {
    private ModNetwork() {}

    // ---------------------------------------------------------------- client -> server

    /** Pull the trigger along the camera ray (differs from the look vector in over-the-shoulder mode). */
    public record Fire(float x, float y, float z) implements CustomPacketPayload {
        public static final Type<Fire> TYPE = new Type<>(Frontline.id("fire"));
        public static final StreamCodec<ByteBuf, Fire> CODEC = StreamCodec.composite(ByteBufCodecs.FLOAT, Fire::x, ByteBufCodecs.FLOAT, Fire::y, ByteBufCodecs.FLOAT, Fire::z, Fire::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
        public Vec3 dir() { return new Vec3(x, y, z); }
    }

    public record Reload() implements CustomPacketPayload {
        public static final Type<Reload> TYPE = new Type<>(Frontline.id("reload"));
        public static final StreamCodec<ByteBuf, Reload> CODEC = StreamCodec.unit(new Reload());
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record Aim(boolean ads) implements CustomPacketPayload {
        public static final Type<Aim> TYPE = new Type<>(Frontline.id("aim"));
        public static final StreamCodec<ByteBuf, Aim> CODEC = ByteBufCodecs.BOOL.map(Aim::new, Aim::ads);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** The naming screen's answer: found a nation at the flag that was just placed. */
    public record FoundNation(BlockPos pos, String name, String army, int color) implements CustomPacketPayload {
        public static final Type<FoundNation> TYPE = new Type<>(Frontline.id("found_nation"));
        public static final StreamCodec<FriendlyByteBuf, FoundNation> CODEC = StreamCodec.of((buf, p) -> {
            buf.writeBlockPos(p.pos); buf.writeUtf(p.name, 32); buf.writeUtf(p.army, 32); buf.writeInt(p.color);
        }, buf -> new FoundNation(buf.readBlockPos(), buf.readUtf(32), buf.readUtf(32), buf.readInt()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // ---------------------------------------------------------------- server -> client

    public record GunSync(int reloadTicks, int reloadMax, float bloom, int ammoCount, int kills, int shots, int hits) implements CustomPacketPayload {
        public static final Type<GunSync> TYPE = new Type<>(Frontline.id("gun_sync"));
        public static final StreamCodec<FriendlyByteBuf, GunSync> CODEC = StreamCodec.of((buf, s) -> {
            buf.writeVarInt(s.reloadTicks); buf.writeVarInt(s.reloadMax); buf.writeFloat(s.bloom); buf.writeVarInt(s.ammoCount);
            buf.writeVarInt(s.kills); buf.writeVarInt(s.shots); buf.writeVarInt(s.hits);
        }, buf -> new GunSync(buf.readVarInt(), buf.readVarInt(), buf.readFloat(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** kind 0 = hit, 1 = headshot, 2 = kill. */
    public record HitMark(int kind) implements CustomPacketPayload {
        public static final Type<HitMark> TYPE = new Type<>(Frontline.id("hit_mark"));
        public static final StreamCodec<ByteBuf, HitMark> CODEC = ByteBufCodecs.VAR_INT.map(HitMark::new, HitMark::kind);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record OpenNationScreen(BlockPos pos) implements CustomPacketPayload {
        public static final Type<OpenNationScreen> TYPE = new Type<>(Frontline.id("open_nation"));
        public static final StreamCodec<FriendlyByteBuf, OpenNationScreen> CODEC = StreamCodec.of((buf, p) -> buf.writeBlockPos(p.pos), buf -> new OpenNationScreen(buf.readBlockPos()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Your nation and the claim you are standing in (empty strings when none). */
    public record NationSync(String nation, String army, int color, int soldiers, int bases, String claim, int claimColor, boolean claimHostile) implements CustomPacketPayload {
        public static final Type<NationSync> TYPE = new Type<>(Frontline.id("nation_sync"));
        public static final StreamCodec<FriendlyByteBuf, NationSync> CODEC = StreamCodec.of((buf, s) -> {
            buf.writeUtf(s.nation, 64); buf.writeUtf(s.army, 64); buf.writeInt(s.color); buf.writeVarInt(s.soldiers); buf.writeVarInt(s.bases);
            buf.writeUtf(s.claim, 64); buf.writeInt(s.claimColor); buf.writeBoolean(s.claimHostile);
        }, buf -> new NationSync(buf.readUtf(64), buf.readUtf(64), buf.readInt(), buf.readVarInt(), buf.readVarInt(), buf.readUtf(64), buf.readInt(), buf.readBoolean()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // ---------------------------------------------------------------- wiring

    /** Filled in by the client mod class; the server never touches these. */
    public static final class ClientSink {
        public static Consumer<GunSync> gun = s -> {};
        public static Consumer<HitMark> hit = s -> {};
        public static Consumer<OpenNationScreen> open = s -> {};
        public static Consumer<NationSync> nation = s -> {};
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1");
        r.playToServer(Fire.TYPE, Fire.CODEC, (p, ctx) -> { if (ctx.player() instanceof ServerPlayer sp) GunLogic.fire(sp, p.dir()); });
        r.playToServer(Reload.TYPE, Reload.CODEC, (p, ctx) -> { if (ctx.player() instanceof ServerPlayer sp) GunLogic.reload(sp); });
        r.playToServer(Aim.TYPE, Aim.CODEC, (p, ctx) -> { if (ctx.player() instanceof ServerPlayer sp) { Trooper t = sp.getData(ModRegistries.TROOPER); t.ads = p.ads(); } });
        r.playToServer(FoundNation.TYPE, FoundNation.CODEC, (p, ctx) -> { if (ctx.player() instanceof ServerPlayer sp) GameEvents.foundNation(sp, p); });
        r.playToClient(GunSync.TYPE, GunSync.CODEC, (s, ctx) -> ClientSink.gun.accept(s));
        r.playToClient(HitMark.TYPE, HitMark.CODEC, (s, ctx) -> ClientSink.hit.accept(s));
        r.playToClient(OpenNationScreen.TYPE, OpenNationScreen.CODEC, (s, ctx) -> ClientSink.open.accept(s));
        r.playToClient(NationSync.TYPE, NationSync.CODEC, (s, ctx) -> ClientSink.nation.accept(s));
    }
}
