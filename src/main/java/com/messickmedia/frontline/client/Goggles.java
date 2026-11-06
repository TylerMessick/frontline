package com.messickmedia.frontline.client;

import com.messickmedia.frontline.Frontline;
import com.messickmedia.frontline.entity.VehicleEntity;
import com.messickmedia.frontline.item.TacticalArmorItem;
import com.messickmedia.frontline.net.ModNetwork;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

/**
 * Night-vision goggles on the NVG helmet. N cycles OFF → NIGHT → THERMAL.
 * <ul>
 *   <li>Night: the server keeps a hidden night-vision effect on; a post shader turns the frame into green phosphor
 *   with grain, scanlines and a binocular mask.</li>
 *   <li>Thermal: the world is crushed dark before entities draw, living things are redrawn full-bright on a plain
 *   white texture, and a post shader maps brightness to an ironbow palette, so bodies glow yellow-white on cold
 *   purple terrain. Undead read cold, running vehicles warm.</li>
 * </ul>
 * If a post shader fails to load the screen still gets a tinted overlay, so the goggles never stop working.
 */
public final class Goggles {
    private Goggles() {}

    public static final ResourceLocation NVG_POST = Frontline.id("shaders/post/nvg.json");
    public static final ResourceLocation THERMAL_POST = Frontline.id("shaders/post/thermal.json");
    private static final ResourceLocation WHITE = Frontline.id("textures/misc/white.png");

    private static boolean nvgFailed, thermalFailed;
    private static boolean redrawing;

    public static boolean wearing(Minecraft mc) {
        return mc.player != null && mc.player.getItemBySlot(EquipmentSlot.HEAD).getItem() instanceof TacticalArmorItem a && a.nvg();
    }

    /** Cycle the mode (key N). */
    public static void cycle(Minecraft mc) {
        if (!wearing(mc)) {
            mc.player.displayClientMessage(Component.translatable("hud.frontline.no_goggles"), true);
            return;
        }
        set(mc, (ClientState.nvMode + 1) % 3);
        mc.player.playSound(ClientState.nvMode == 0 ? SoundEvents.IRON_TRAPDOOR_CLOSE : SoundEvents.BEACON_POWER_SELECT, 0.5F, ClientState.nvMode == 2 ? 1.6F : 1.9F);
    }

    static void set(Minecraft mc, int mode) {
        if (mode == ClientState.nvMode) return;
        ClientState.nvMode = mode;
        PacketDistributor.sendToServer(new ModNetwork.Goggles(mode));
        apply(mc);
    }

    /** Every client tick: drop the mode if the helmet came off, and keep our post effect loaded (F5 and spectating can clear it). */
    public static void tick(Minecraft mc) {
        if (mc.player == null) return;
        if (ClientState.nvMode != 0 && !wearing(mc)) set(mc, 0);
        if (ClientState.nvMode != 0) apply(mc);
    }

    private static void apply(Minecraft mc) {
        GameRenderer gr = mc.gameRenderer;
        PostChain current = gr.currentEffect();
        boolean ours = current != null && (current.getName().equals(NVG_POST.toString()) || current.getName().equals(THERMAL_POST.toString()));
        if (ClientState.nvMode == 0) {
            if (ours) gr.shutdownEffect();
            return;
        }
        ResourceLocation want = ClientState.nvMode == 1 ? NVG_POST : THERMAL_POST;
        if ((ClientState.nvMode == 1 && nvgFailed) || (ClientState.nvMode == 2 && thermalFailed)) { if (ours) gr.shutdownEffect(); return; }
        if (current != null && current.getName().equals(want.toString())) return;
        gr.loadEffect(want);
        PostChain after = gr.currentEffect();
        if (after == null || !after.getName().equals(want.toString())) {
            if (ClientState.nvMode == 1) nvgFailed = true; else thermalFailed = true;
            gr.shutdownEffect();
        }
    }

    public static boolean postActive() {
        PostChain c = Minecraft.getInstance().gameRenderer.currentEffect();
        return c != null && (c.getName().equals(NVG_POST.toString()) || c.getName().equals(THERMAL_POST.toString()));
    }

    // ---------------------------------------------------------------- thermal: darken the world before entities draw

    public static void levelStage(RenderLevelStageEvent e) {
        if (ClientState.nvMode != 2 || e.getStage() != RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS) return;
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(new Matrix4f(), VertexSorting.ORTHOGRAPHIC_Z);
        Matrix4fStack mv = RenderSystem.getModelViewStack();
        mv.pushMatrix();
        mv.identity();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.DST_COLOR, GlStateManager.DestFactor.ZERO);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float k = 0.2F;
        b.addVertex(-1, -1, 0).setColor(k, k, k * 1.1F, 1.0F);
        b.addVertex(1, -1, 0).setColor(k, k, k * 1.1F, 1.0F);
        b.addVertex(1, 1, 0).setColor(k, k, k * 1.1F, 1.0F);
        b.addVertex(-1, 1, 0).setColor(k, k, k * 1.1F, 1.0F);
        BufferUploader.drawWithShader(b.buildOrThrow());
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        mv.popMatrix();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.restoreProjectionMatrix();
    }

    // ---------------------------------------------------------------- thermal: living things glow

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void renderLiving(RenderLivingEvent.Pre<?, ?> e) {
        if (ClientState.nvMode != 2 || redrawing) return;
        LivingEntity ent = e.getEntity();
        if (ent.getVehicle() instanceof VehicleEntity) return;
        e.setCanceled(true);
        int heat = ent.getType().is(net.minecraft.tags.EntityTypeTags.UNDEAD) ? 110 : 255;
        redrawing = true;
        try {
            ((LivingEntityRenderer) e.getRenderer()).render(ent, ent.getYRot(), e.getPartialTick(), e.getPoseStack(), hot(e.getMultiBufferSource(), heat), LightTexture.FULL_BRIGHT);
        } finally {
            redrawing = false;
        }
    }

    /** Wraps a buffer source so everything drawn through it is a single flat, full-bright shade of grey. */
    public static MultiBufferSource hot(MultiBufferSource real, int shade) {
        // only entity-model geometry turns hot; text (name tags), lines, glints and the like pass through untouched,
        // because their vertices carry different elements than the entity format
        return type -> type.format() == com.mojang.blaze3d.vertex.DefaultVertexFormat.NEW_ENTITY
                ? new Hot(real.getBuffer(RenderType.entityCutoutNoCull(WHITE)), shade)
                : real.getBuffer(type);
    }

    private static final class Hot implements VertexConsumer {
        private final VertexConsumer d;
        private final int shade;
        private boolean started, color, uv, uv1, uv2, normal;

        Hot(VertexConsumer d, int shade) { this.d = d; this.shade = shade; }

        /** Completes the previous vertex with defaults for anything the caller did not set. */
        private void finish() {
            if (!started) return;
            if (!color) d.setColor(shade, shade, shade, 255);
            if (!uv) d.setUv(0.5F, 0.5F);
            if (!uv1) d.setUv1(0, 10);
            if (!uv2) d.setUv2(0xF0, 0xF0);
            if (!normal) d.setNormal(0.0F, 1.0F, 0.0F);
        }

        @Override public VertexConsumer addVertex(float x, float y, float z) {
            finish();
            d.addVertex(x, y, z);
            started = true; color = uv = uv1 = uv2 = normal = false;
            return this;
        }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) { d.setColor(shade, shade, shade, 255); color = true; return this; }
        @Override public VertexConsumer setUv(float u, float v) { d.setUv(u, v); uv = true; return this; }
        @Override public VertexConsumer setUv1(int u, int v) { d.setUv1(u, v); uv1 = true; return this; }
        @Override public VertexConsumer setUv2(int u, int v) { d.setUv2(0xF0, 0xF0); uv2 = true; return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) { d.setNormal(x, y, z); normal = true; return this; }
    }

    // ---------------------------------------------------------------- HUD

    /** Below every other HUD layer: fallback tint if a shader failed, plus the mode readout. */
    public static void hud(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (ClientState.nvMode == 0 || mc.player == null) return;
        int w = g.guiWidth(), h = g.guiHeight();
        if (!postActive()) {
            g.fill(0, 0, w, h, ClientState.nvMode == 1 ? 0x6620E040 : 0x55301060);
            long seed = mc.level == null ? 0 : mc.level.getGameTime();
            java.util.Random r = new java.util.Random(seed * 31);
            for (int i = 0; i < w * h / 220; i++) {
                int x = r.nextInt(w), y = r.nextInt(h);
                g.fill(x, y, x + 1, y + 1, 0x3090FF90);
            }
        }
        String label = ClientState.nvMode == 1 ? "NVG" : "THERMAL";
        int col = ClientState.nvMode == 1 ? 0xFF7CFF8A : 0xFFFFC040;
        g.drawString(mc.font, label, w - mc.font.width(label) - 8, 8, col, true);
        g.drawString(mc.font, "[N]", w - mc.font.width("[N]") - 8, 18, 0x90FFFFFF, true);
    }
}
