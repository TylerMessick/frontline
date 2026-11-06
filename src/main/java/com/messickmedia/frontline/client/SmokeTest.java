package com.messickmedia.frontline.client;

import com.messickmedia.frontline.entity.VehicleEntity;
import com.messickmedia.frontline.entity.VehicleKind;
import com.messickmedia.frontline.registry.ModRegistries;
import com.mojang.logging.LogUtils;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

/**
 * CI-only in-world test (enabled with -Dfrontline.smoke=true). Creates a flat creative world, then for every vehicle:
 * spawn it, board it, drive it with the real movement keys, dismount with the real sneak key, and log the player's
 * state and take screenshots before and after. Quits the game when done.
 */
public final class SmokeTest {
    private SmokeTest() {}

    private static final Logger LOG = LogUtils.getLogger();
    private static final VehicleKind[] ORDER = {VehicleKind.JEEP, VehicleKind.TANK, VehicleKind.HELI, VehicleKind.PLANE};
    private static final int SPAN = 220;
    private static boolean started;
    private static int t = -1;
    private static int vehicleId = -1;

    public static void install() {
        if (!Boolean.getBoolean("frontline.smoke")) return;
        LOG.info("FRONTLINE-SMOKE enabled");
        NeoForge.EVENT_BUS.addListener(SmokeTest::onScreen);
        NeoForge.EVENT_BUS.addListener(SmokeTest::onTick);
    }

    private static void onScreen(ScreenEvent.Init.Post e) {
        if (started || !(e.getScreen() instanceof TitleScreen || e.getScreen() instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen)) return;
        Minecraft.getInstance().options.onboardAccessibility = false;
        started = true;
        Minecraft mc = Minecraft.getInstance();
        LevelSettings settings = new LevelSettings("frontline_smoke_" + System.currentTimeMillis(), GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
                new GameRules(), WorldDataConfiguration.DEFAULT);
        LOG.info("FRONTLINE-SMOKE creating world");
        mc.createWorldOpenFlows().createFreshLevel(settings.levelName(), settings, new WorldOptions(20261106L, false, false),
                reg -> reg.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(), e.getScreen());
    }

    private static void key(KeyMapping k, boolean down) {
        KeyMapping.set(k.getKey(), down);
        if (!down) k.setDown(false);
    }

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, "smoke_" + name + ".png", mc.getMainRenderTarget(), msg -> {});
    }

    private static void state(Minecraft mc, String label) {
        LocalPlayer p = mc.player;
        Entity cam = mc.getCameraEntity();
        LOG.info("FRONTLINE-SMOKE STATE {} passenger={} vehicle={} spectator={} invisible={} removed={} alive={} mode={} camEntity={} camIsPlayer={} hideGui={} camera={} pos={} screen={} health={}",
                label, p.isPassenger(), p.getVehicle() == null ? "none" : p.getVehicle().getType().toShortString(), p.isSpectator(), p.isInvisible(),
                p.isRemoved(), p.isAlive(), mc.gameMode == null ? "?" : mc.gameMode.getPlayerMode(), cam == null ? "null" : cam.getType().toShortString() + "#" + cam.getId(),
                cam == p, mc.options.hideGui, mc.options.getCameraType(), p.position(), mc.screen == null ? "none" : mc.screen.getClass().getSimpleName(), p.getHealth());
        var server = mc.getSingleplayerServer();
        if (server != null) {
            server.execute(() -> {
                ServerPlayer sp = server.getPlayerList().getPlayers().isEmpty() ? null : server.getPlayerList().getPlayers().get(0);
                if (sp != null) LOG.info("FRONTLINE-SMOKE SERVER {} passenger={} spectator={} removed={} pos={} mode={}", label, sp.isPassenger(), sp.isSpectator(), sp.isRemoved(), sp.position(), sp.gameMode.getGameModeForPlayer());
            });
        }
    }

    private static void onTick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.getSingleplayerServer() == null) return;
        if (t < 0 && mc.screen != null) {
            // some world-creation dialogs (e.g. a backup / experimental prompt) would block the run; log what is up
            if (mc.player.tickCount % 100 == 0) LOG.info("FRONTLINE-SMOKE waiting on screen {}", mc.screen.getClass().getName());
            if (mc.player.tickCount > 200) mc.setScreen(null);
            return;
        }
        t++;
        var server = mc.getSingleplayerServer();
        if (t == 1) {
            LOG.info("FRONTLINE-SMOKE in world");
            server.execute(() -> {
                ServerPlayer sp = server.getPlayerList().getPlayers().get(0);
                ServerLevel lvl = sp.serverLevel();
                lvl.setDayTime(6000);
                sp.getInventory().setItem(0, ModRegistries.GUNS.get("fl4_carbine").get().getDefaultInstance());
                sp.getInventory().selected = 0;
            });
            state(mc, "start");
            return;
        }
        int phase = (t - 40) / SPAN, k = (t - 40) % SPAN;
        if (t < 40) return;
        if (phase >= ORDER.length) {
            if (k == 10) {
                LOG.info("FRONTLINE-SMOKE DONE");
                mc.stop();
            }
            return;
        }
        VehicleKind kind = ORDER[phase];
        String n = kind.id;
        switch (k) {
            case 0 -> server.execute(() -> {
                ServerPlayer sp = server.getPlayerList().getPlayers().get(0);
                ServerLevel lvl = sp.serverLevel();
                sp.teleportTo(lvl, phase * 40.5, sp.getY(), 0.5, 0.0F, 10.0F);
                VehicleEntity v = new VehicleEntity(ModRegistries.VEHICLES.get(kind).get(), lvl, kind);
                v.setPos(phase * 40.5, sp.getY(), 5.5);
                v.setYRot(0.0F);
                lvl.addFreshEntity(v);
                vehicleId = v.getId();
                LOG.info("FRONTLINE-SMOKE spawned {} id={} at {}", n, v.getId(), v.position());
            });
            case 20 -> server.execute(() -> {
                ServerPlayer sp = server.getPlayerList().getPlayers().get(0);
                Entity v = sp.serverLevel().getEntity(vehicleId);
                boolean ok = v != null && sp.startRiding(v, true);
                LOG.info("FRONTLINE-SMOKE board {} ok={}", n, ok);
            });
            case 30 -> { state(mc, n + "_boarded"); key(mc.options.keyUp, true); if (kind == VehicleKind.HELI) key(mc.options.keyJump, true); }
            case 60 -> { if (kind == VehicleKind.HELI) key(mc.options.keyJump, false); }
            case 70 -> shot(mc, n + "_riding");
            case 100 -> { key(mc.options.keyUp, false); state(mc, n + "_driving"); }
            case 120 -> key(mc.options.keyShift, true);
            case 126 -> key(mc.options.keyShift, false);
            case 140 -> {
                state(mc, n + "_dismounted");
                Entity v = mc.level.getEntity(vehicleId);
                boolean inside = v != null && v.getBoundingBox().inflate(0.3).intersects(mc.player.getBoundingBox());
                LOG.info("FRONTLINE-SMOKE CHECK {} outside_vehicle={} distance={}", n, !inside, v == null ? -1 : mc.player.distanceTo(v));
                shot(mc, n + "_after_fp");
            }
            case 142 -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            case 146 -> shot(mc, n + "_after_tp");
            case 148 -> mc.options.setCameraType(CameraType.FIRST_PERSON);
            case 190 -> state(mc, n + "_later");
            default -> {}
        }
    }
}
