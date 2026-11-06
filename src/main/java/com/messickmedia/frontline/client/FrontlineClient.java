package com.messickmedia.frontline.client;

import com.messickmedia.frontline.Frontline;
import com.messickmedia.frontline.gun.GunInfo;
import com.messickmedia.frontline.gun.GunItem;
import com.messickmedia.frontline.net.ModNetwork;
import com.messickmedia.frontline.registry.ModRegistries;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import com.messickmedia.frontline.entity.VehicleEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;

/**
 * Client wiring: R reloads, V switches between first person and the over-the-shoulder camera, left mouse fires,
 * right mouse aims. Also the first-person gun, HUD, camera recoil and zoom, renderers and particles.
 */
@Mod(value = Frontline.MODID, dist = Dist.CLIENT)
public class FrontlineClient {
    private static final String CATEGORY = "key.categories.frontline";
    public static final KeyMapping KEY_RELOAD = new KeyMapping("key.frontline.reload", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, CATEGORY);
    public static final KeyMapping KEY_CAMERA = new KeyMapping("key.frontline.camera", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, CATEGORY);
    public static final KeyMapping KEY_GOGGLES = new KeyMapping("key.frontline.goggles", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_N, CATEGORY);
    public static final KeyMapping KEY_WHEEL = new KeyMapping("key.frontline.wheel", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, CATEGORY);

    public FrontlineClient(IEventBus modBus, ModContainer container) {
        modBus.addListener(FrontlineClient::keys);
        modBus.addListener(FrontlineClient::particles);
        modBus.addListener(FrontlineClient::renderers);
        modBus.addListener(FrontlineClient::layerDefinitions);
        modBus.addListener(FrontlineClient::addLayers);
        modBus.addListener(FrontlineClient::hud);
        modBus.addListener(FrontlineClient::extensions);
        NeoForge.EVENT_BUS.addListener(FrontlineClient::tick);
        NeoForge.EVENT_BUS.addListener(FirstPersonGun::render);
        NeoForge.EVENT_BUS.addListener(FrontlineClient::fov);
        NeoForge.EVENT_BUS.addListener(FrontlineClient::cameraAngles);
        NeoForge.EVENT_BUS.addListener(FrontlineClient::guiLayer);
        NeoForge.EVENT_BUS.addListener(FrontlineClient::scroll);
        NeoForge.EVENT_BUS.addListener(Goggles::levelStage);
        NeoForge.EVENT_BUS.addListener(Goggles::renderLiving);
        NeoForge.EVENT_BUS.addListener(FrontlineClient::interactionKey);
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> { ClientState.clear(); FlagTextures.clear(); });

        SmokeTest.install();
        ModNetwork.ClientSink.gun = ClientState::accept;
        ModNetwork.ClientSink.nation = ClientState::accept;
        ModNetwork.ClientSink.armory = ClientState::accept;
        ModNetwork.ClientSink.painter = p -> Minecraft.getInstance().setScreen(new FlagPainterScreen(p.nation(), p.design()));
        VehicleEntity.clientJumpHook = e -> e == Minecraft.getInstance().player && Minecraft.getInstance().options.keyJump.isDown();
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.RenderPlayerEvent.Pre e) -> { if (e.getEntity().getVehicle() instanceof VehicleEntity) e.setCanceled(true); });
        ModNetwork.ClientSink.hit = FrontlineClient::hitMark;
        ModNetwork.ClientSink.open = p -> Minecraft.getInstance().setScreen(new NationScreen(p.pos()));
    }

    // ---------------------------------------------------------------- registration

    static void keys(RegisterKeyMappingsEvent e) {
        e.register(KEY_RELOAD);
        e.register(KEY_CAMERA);
        e.register(KEY_WHEEL);
        e.register(KEY_GOGGLES);
    }

    static void particles(RegisterParticleProvidersEvent e) {
        e.registerSpriteSet(ModRegistries.MUZZLE.get(), s -> new Mote.Provider(s, Mote.Kind.MUZZLE));
        e.registerSpriteSet(ModRegistries.SMOKE.get(), s -> new Mote.Provider(s, Mote.Kind.SMOKE));
        e.registerSpriteSet(ModRegistries.BRASS.get(), s -> new Mote.Provider(s, Mote.Kind.BRASS));
        e.registerSpriteSet(ModRegistries.SPARK.get(), s -> new Mote.Provider(s, Mote.Kind.SPARK));
    }

    static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(ModRegistries.BULLET.get(), BulletRenderer::new);
        e.registerEntityRenderer(ModRegistries.SOLDIER.get(), SoldierRenderer::new);
        for (var v : ModRegistries.VEHICLES.values()) e.registerEntityRenderer(v.get(), VehicleRenderer::new);
        e.registerBlockEntityRenderer(ModRegistries.FLAG_BE.get(), FlagRenderer::new);
    }

    static void layerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions e) {
        e.registerLayerDefinition(SoldierModel.LAYER, SoldierModel::createLayer);
        e.registerLayerDefinition(VehicleModels.TANK, VehicleModels::tank);
        e.registerLayerDefinition(VehicleModels.PLANE, VehicleModels::plane);
        e.registerLayerDefinition(VehicleModels.HELI, VehicleModels::heli);
        e.registerLayerDefinition(VehicleModels.JEEP, VehicleModels::jeep);
    }

    /** Shoulder patches on every player model. */
    static void addLayers(EntityRenderersEvent.AddLayers e) {
        for (net.minecraft.client.resources.PlayerSkin.Model skin : e.getSkins()) {
            if (e.getSkin(skin) instanceof net.minecraft.client.renderer.entity.player.PlayerRenderer r) r.addLayer(new PatchLayer<>(r));
        }
    }

    static void hud(RegisterGuiLayersEvent e) {
        e.registerBelowAll(Frontline.id("goggles"), Goggles::hud);
        e.registerAbove(VanillaGuiLayers.CROSSHAIR, Frontline.id("crosshair"), GunHud::crosshair);
        e.registerAbove(VanillaGuiLayers.HOTBAR, Frontline.id("panel"), GunHud::panel);
    }

    static void extensions(RegisterClientExtensionsEvent e) {
        IClientItemExtensions ext = new IClientItemExtensions() {
            @Nullable
            @Override
            public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
                return hand == InteractionHand.MAIN_HAND ? HumanoidModel.ArmPose.CROSSBOW_HOLD : null;
            }
        };
        e.registerItem(ext, ModRegistries.GUNS.values().stream().map(d -> (net.minecraft.world.item.Item) d.get()).toArray(net.minecraft.world.item.Item[]::new));
    }

    // ---------------------------------------------------------------- per tick

    @Nullable
    private static GunInfo held(LocalPlayer p) { return GunItem.gunOf(p.getMainHandItem()); }

    static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) return;
        GunInfo gun = held(p);
        boolean holding = gun != null;
        float dy = Mth.wrapDegrees(p.getYRot() - ClientState.lastYaw);
        float dp = p.getXRot() - ClientState.lastPitch;
        ClientState.lastYaw = p.getYRot();
        ClientState.lastPitch = p.getXRot();
        ClientState.tick(holding);
        ClientState.swayX = Mth.clamp(ClientState.swayX - dy * 0.5F, -14.0F, 14.0F);
        ClientState.swayY = Mth.clamp(ClientState.swayY + dp * 0.5F, -14.0F, 14.0F);
        FirstPersonGun.tick(p);
        tickRiding(mc, p);
        Goggles.tick(mc);
        while (KEY_GOGGLES.consumeClick()) { if (mc.screen == null) Goggles.cycle(mc); }
        while (KEY_WHEEL.consumeClick()) { if (mc.screen == null && !(p.getVehicle() instanceof VehicleEntity)) mc.setScreen(new WheelScreen()); }
        if (ClientState.ots && mc.options.getCameraType() != CameraType.THIRD_PERSON_BACK) ClientState.ots = false;
        if (!holding || mc.screen != null) {
            if (!Double.isNaN(ClientState.baseSensitivity)) { mc.options.sensitivity().set(ClientState.baseSensitivity); ClientState.baseSensitivity = Double.NaN; }
            if (ClientState.ads) { ClientState.ads = false; PacketDistributor.sendToServer(new ModNetwork.Aim(false)); }
            while (KEY_RELOAD.consumeClick()) {}
            while (KEY_CAMERA.consumeClick()) {}
            return;
        }
        if (!gun.id().equals(ClientState.lastGun)) { ClientState.lastGun = gun.id(); ClientState.zoom = gun.zoom(); }
        // a scope turns the mouse down so the view does not fly around at high magnification
        if (gun.scope() && ClientState.adsBlend > 0.5F) {
            if (Double.isNaN(ClientState.baseSensitivity)) ClientState.baseSensitivity = mc.options.sensitivity().get();
            mc.options.sensitivity().set(ClientState.baseSensitivity * Mth.clamp(1.6D / ClientState.zoom, 0.12D, 1.0D));
        } else if (!Double.isNaN(ClientState.baseSensitivity)) {
            mc.options.sensitivity().set(ClientState.baseSensitivity);
            ClientState.baseSensitivity = Double.NaN;
        }
        boolean wantAds = mc.options.keyUse.isDown() && !ClientState.reloading();
        if (wantAds != ClientState.ads) {
            ClientState.ads = wantAds;
            PacketDistributor.sendToServer(new ModNetwork.Aim(wantAds));
        }
        if (ClientState.ads && p.isSprinting()) p.setSprinting(false);
        if (gun.auto() && mc.options.keyAttack.isDown()) tryFire(mc, p, gun);
        while (KEY_RELOAD.consumeClick()) PacketDistributor.sendToServer(new ModNetwork.Reload());
        while (KEY_CAMERA.consumeClick()) {
            ClientState.ots = !ClientState.ots;
            mc.options.setCameraType(ClientState.ots ? CameraType.THIRD_PERSON_BACK : CameraType.FIRST_PERSON);
        }
    }

    /** Riding a vehicle: third-person camera, mouse buttons drive its weapons. */
    static void tickRiding(Minecraft mc, LocalPlayer p) {
        boolean riding = p.getVehicle() instanceof VehicleEntity;
        if (riding && ClientState.cameraBeforeRide < 0) {
            ClientState.cameraBeforeRide = mc.options.getCameraType().ordinal();
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        } else if (!riding && ClientState.cameraBeforeRide >= 0) {
            mc.options.setCameraType(ClientState.ots ? CameraType.THIRD_PERSON_BACK : CameraType.values()[ClientState.cameraBeforeRide]);
            ClientState.cameraBeforeRide = -1;
        }
        if (!riding || mc.screen != null) return;
        VehicleEntity v = (VehicleEntity) p.getVehicle();
        if (mc.options.keyAttack.isDown() && ClientState.vehicleFireCd <= 0) {
            Vec3 dir = cameraAim(mc, p);
            PacketDistributor.sendToServer(new ModNetwork.VehicleFire(true, (float) dir.x, (float) dir.y, (float) dir.z));
            ClientState.vehicleFireCd = Math.max(1, v.kind().primaryCd);
            if (v.kind() == com.messickmedia.frontline.entity.VehicleKind.TANK) ClientState.camPitch += 1.5F;
        }
        if (mc.options.keyUse.isDown() && v.kind().hasSecondary() && p.tickCount % 3 == 0) {
            Vec3 dir = cameraAim(mc, p);
            PacketDistributor.sendToServer(new ModNetwork.VehicleFire(false, (float) dir.x, (float) dir.y, (float) dir.z));
        }
    }

    /** The direction from the player's eyes to whatever the third-person camera ray hits. */
    static Vec3 cameraAim(Minecraft mc, LocalPlayer p) {
        Camera cam = mc.gameRenderer.getMainCamera();
        Vector3f lv = cam.getLookVector();
        Vec3 look = new Vec3(lv.x(), lv.y(), lv.z()).normalize();
        Vec3 from = cam.getPosition();
        Vec3 to = from.add(look.scale(200.0D));
        HitResult block = mc.level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        Vec3 end = block.getType() == HitResult.Type.MISS ? to : block.getLocation();
        Entity vehicle = p.getVehicle();
        EntityHitResult ehr = ProjectileUtil.getEntityHitResult(mc.level, p, from.add(look.scale(6.0D)), end, p.getBoundingBox().expandTowards(look.scale(200.0D)).inflate(2.0D),
                e -> !e.isSpectator() && e.isPickable() && e != p && e != vehicle, 0.0F);
        if (ehr != null) end = ehr.getLocation();
        Vec3 dir = end.subtract(p.getEyePosition());
        return dir.lengthSqr() < 0.25D ? look : dir.normalize();
    }

    static void tryFire(Minecraft mc, LocalPlayer p, GunInfo gun) {
        if (ClientState.fireCd > 0 || ClientState.reloading()) return;
        ItemStack stack = p.getMainHandItem();
        int rounds = GunItem.rounds(stack);
        if (rounds <= 0) {
            PacketDistributor.sendToServer(new ModNetwork.Fire(0, 0, 0));
            ClientState.fireCd = 10;
            return;
        }
        Vec3 dir = aimDir(mc, p);
        PacketDistributor.sendToServer(new ModNetwork.Fire((float) dir.x, (float) dir.y, (float) dir.z));
        ClientState.fireCd = gun.fireTicks();
        ClientState.kick = 1.0F;
        ClientState.flashTicks = 2;
        float adsMul = Mth.lerp(ClientState.adsBlend, 1.0F, 0.6F);
        float pitchKick = gun.recoilPitch() * adsMul;
        float yawKick = (p.getRandom().nextFloat() - 0.5F) * 2.0F * gun.recoilYaw() * adsMul;
        p.setXRot(p.getXRot() - pitchKick * 0.5F);
        p.setYRot(p.getYRot() + yawKick * 0.5F);
        ClientState.lastYaw = p.getYRot();
        ClientState.lastPitch = p.getXRot();
        ClientState.camPitch += pitchKick * 0.5F;
        ClientState.camYaw += yawKick * 0.5F;
        GunItem.setRounds(stack, rounds - 1); // prediction; the server's component sync overwrites it
    }

    /** Where the crosshair points: the camera ray's first hit, turned into a direction from the player's eyes. */
    static Vec3 aimDir(Minecraft mc, LocalPlayer p) {
        Camera cam = mc.gameRenderer.getMainCamera();
        Vector3f lv = cam.getLookVector();
        Vec3 look = new Vec3(lv.x(), lv.y(), lv.z()).normalize();
        if (!ClientState.ots) return look;
        Vec3 from = cam.getPosition();
        Vec3 to = from.add(look.scale(160.0D));
        HitResult block = mc.level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        Vec3 end = block.getType() == HitResult.Type.MISS ? to : block.getLocation();
        EntityHitResult ehr = ProjectileUtil.getEntityHitResult(mc.level, p, from, end, p.getBoundingBox().expandTowards(look.scale(160.0D)).inflate(1.0D),
                e -> !e.isSpectator() && e.isPickable() && e != p, 0.0F);
        if (ehr != null) end = ehr.getLocation();
        Vec3 dir = end.subtract(p.getEyePosition());
        return dir.lengthSqr() < 0.25D ? look : dir.normalize();
    }

    // ---------------------------------------------------------------- hooks

    static void interactionKey(InputEvent.InteractionKeyMappingTriggered e) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null) return;
        if (p.getVehicle() instanceof VehicleEntity) { e.setCanceled(true); e.setSwingHand(false); return; }
        GunInfo gun = held(p);
        if (gun == null) return;
        if (e.isAttack()) {
            e.setCanceled(true);
            e.setSwingHand(false);
            if (!gun.auto()) tryFire(mc, p, gun);
        } else if (e.isUseItem() && !p.isShiftKeyDown()) {
            e.setCanceled(true);
            e.setSwingHand(false);
        }
    }

    /** Scroll while looking through a scope to change its magnification. */
    static void scroll(InputEvent.MouseScrollingEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        GunInfo gun = held(mc.player);
        if (gun == null || !gun.scope() || ClientState.adsBlend < 0.9F) return;
        float step = e.getScrollDeltaY() > 0 ? 1.0F : -1.0F;
        ClientState.zoom = Mth.clamp(ClientState.zoom + step * (ClientState.zoom >= 4 ? 1.0F : 0.5F), 1.5F, 10.0F);
        e.setCanceled(true);
    }

    /** True while the player is actually looking through a scope. */
    static boolean scoped(Minecraft mc) {
        GunInfo gun = mc.player == null ? null : held(mc.player);
        return gun != null && gun.scope() && ClientState.adsBlend > 0.5F;
    }

    static void fov(ViewportEvent.ComputeFov e) {
        if (!e.usedConfiguredFov()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        GunInfo gun = held(mc.player);
        if (gun == null) return;
        float ads = Mth.lerp((float) e.getPartialTick(), ClientState.adsBlendO, ClientState.adsBlend);
        if (ads <= 0.001F) return;
        float target = gun.scope() ? 1.0F / ClientState.zoom : gun.adsFov();
        e.setFOV(e.getFOV() * Mth.lerp(ads, 1.0F, target));
    }

    static void cameraAngles(ViewportEvent.ComputeCameraAngles e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        float pt = (float) e.getPartialTick();
        e.setPitch(e.getPitch() - Mth.lerp(pt, ClientState.camPitchO, ClientState.camPitch));
        e.setYaw(e.getYaw() + Mth.lerp(pt, ClientState.camYawO, ClientState.camYaw));
        GunInfo gun = held(mc.player);
        Camera cam = e.getCamera();
        if (mc.player.getVehicle() instanceof VehicleEntity v && cam.isDetached()) {
            float back = v.kind().flies() ? -3.0F : -1.5F;
            cam.move(back, v.kind() == com.messickmedia.frontline.entity.VehicleKind.TANK ? 1.2F : 0.9F, 0.0F);
            return;
        }
        if (gun != null && ClientState.ots && cam.isDetached() && mc.options.getCameraType() == CameraType.THIRD_PERSON_BACK) {
            float ads = Mth.lerp(pt, ClientState.adsBlendO, ClientState.adsBlend);
            // closer and over the right shoulder; tighter still while aiming
            cam.move(Mth.lerp(ads, 1.7F, 2.4F), Mth.lerp(ads, 0.15F, 0.05F), Mth.lerp(ads, -0.72F, -0.55F));
        }
    }

    static void guiLayer(RenderGuiLayerEvent.Pre e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (e.getName().equals(VanillaGuiLayers.CROSSHAIR) && (held(mc.player) != null || mc.player.getVehicle() instanceof VehicleEntity)) e.setCanceled(true);
    }

    static void hitMark(ModNetwork.HitMark m) {
        ClientState.accept(m);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        SoundEvent s = m.kind() == 2 ? ModRegistries.S_KILL.get() : ModRegistries.S_HIT.get();
        mc.player.playSound(s, m.kind() == 2 ? 0.9F : 0.6F, m.kind() == 1 ? 1.3F : 1.0F);
    }
}
