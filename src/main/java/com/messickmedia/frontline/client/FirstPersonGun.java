package com.messickmedia.frontline.client;

import com.messickmedia.frontline.gun.GunInfo;
import com.messickmedia.frontline.gun.GunItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import org.joml.Matrix4f;

/**
 * Draws the held firearm in first person the way a shooter does: low-ready from the hip, raised so the sights sit on
 * the screen centre when aiming, with walk bob, look sway, recoil kick, the reload dip and a muzzle flash.
 */
public final class FirstPersonGun {
    private FirstPersonGun() {}

    private static final float SCALE = 0.5F;
    private static float sprint, sprintO;

    public static void tick(LocalPlayer p) {
        sprintO = sprint;
        float target = p.isSprinting() && ClientState.adsBlend < 0.5F ? 1.0F : 0.0F;
        sprint += (target - sprint) * 0.3F;
    }

    public static void render(RenderHandEvent e) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;
        ItemStack main = player.getMainHandItem();
        GunInfo gun = GunItem.gunOf(main);
        if (gun == null) return;
        e.setCanceled(true);
        if (e.getHand() != InteractionHand.MAIN_HAND) return;
        float pt = e.getPartialTick();
        float ads = Mth.lerp(pt, ClientState.adsBlendO, ClientState.adsBlend);
        if (gun.scope() && ads > 0.92F) return; // through the scope you see the reticle overlay, not the rifle
        float kick = Mth.lerp(pt, ClientState.kickO, ClientState.kick);
        float spr = Mth.lerp(pt, sprintO, sprint);
        float t = player.tickCount + pt;
        PoseStack pose = e.getPoseStack();
        pose.pushPose();

        // where the grip sits: hip vs. aimed (sight line through the screen centre)
        float sightRise = (gun.sightY() - gun.gripY()) / 16.0F * SCALE;
        float hipX = gun.kind() == GunInfo.Kind.PISTOL ? 0.30F : 0.27F, hipY = gun.kind() == GunInfo.Kind.PISTOL ? -0.38F : -0.30F, hipZ = gun.kind() == GunInfo.Kind.PISTOL ? -0.60F : -0.50F;
        float aimZ = gun.kind() == GunInfo.Kind.PISTOL ? -0.55F : -0.42F;
        float px = Mth.lerp(ads, hipX, 0.0F);
        float py = Mth.lerp(ads, hipY, -sightRise);
        float pz = Mth.lerp(ads, hipZ, aimZ);

        // walk bob (a smaller version of vanilla's hand bob), damped while aiming
        float f = player.walkDist - player.walkDistO;
        float f1 = -(player.walkDist + f * pt);
        float f2 = Mth.lerp(pt, player.oBob, player.bob);
        float damp = 1.0F - ads * 0.85F;
        float bobX = Mth.sin(f1 * (float) Math.PI) * f2 * 0.35F * damp;
        float bobY = -Math.abs(Mth.cos(f1 * (float) Math.PI) * f2) * 0.6F * damp;
        float swayX = Mth.lerp(pt, ClientState.swayXO, ClientState.swayX) * damp;
        float swayY = Mth.lerp(pt, ClientState.swayYO, ClientState.swayY) * damp;

        pose.translate(px + bobX + swayX * 0.004F, py + bobY + swayY * 0.004F + Mth.sin(t * 0.08F) * 0.0025F * damp, pz + kick * 0.07F);
        // from the hip the weapon is canted inward a little, like a real low-ready; it squares up when aimed
        pose.mulPose(Axis.YP.rotationDegrees(-7.0F * (1.0F - ads) + swayX * 0.6F + Mth.sin(f1 * (float) Math.PI) * f2 * 2.0F * damp));
        pose.mulPose(Axis.ZP.rotationDegrees(3.0F * (1.0F - ads)));
        pose.mulPose(Axis.XP.rotationDegrees(kick * 6.0F + swayY * 0.4F + Math.abs(Mth.cos(f1 * (float) Math.PI - 0.2F) * f2) * 3.0F * damp));
        pose.mulPose(Axis.ZP.rotationDegrees(Mth.sin(f1 * (float) Math.PI) * f2 * 2.0F * damp));
        // sprint: tuck the weapon across the chest
        if (spr > 0.01F) {
            pose.translate(-0.12F * spr, -0.06F * spr, 0.08F * spr);
            pose.mulPose(Axis.YP.rotationDegrees(28.0F * spr));
            pose.mulPose(Axis.ZP.rotationDegrees(-8.0F * spr));
        }
        // reload: drop the gun, roll it toward the magwell, bring it back
        if (ClientState.reloading() && ClientState.reloadMax > 0) {
            float prog = 1.0F - (ClientState.reloadTicks - pt) / ClientState.reloadMax;
            float wave = Mth.sin(Mth.clamp(prog, 0, 1) * (float) Math.PI);
            pose.translate(0.0F, -0.22F * wave, 0.04F * wave);
            pose.mulPose(Axis.ZP.rotationDegrees(-28.0F * wave));
            pose.mulPose(Axis.XP.rotationDegrees(18.0F * wave));
        }
        pose.scale(SCALE, SCALE, SCALE);
        // the item renderer centres the model on (8,8,8); shift so the grip is the pivot
        pose.translate(0.0F, -(gun.gripY() - 8.0F) / 16.0F, -(gun.gripZ() - 8.0F) / 16.0F);
        mc.getItemRenderer().renderStatic(main, ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, e.getPackedLight(), OverlayTexture.NO_OVERLAY, pose, e.getMultiBufferSource(), mc.level, 0);

        if (ClientState.flashTicks > 0) {
            float fx = (gun.muzzleX() - 8.0F) / 16.0F, fy = (gun.muzzleY() - 8.0F) / 16.0F, fz = (gun.muzzleZ() - 8.0F) / 16.0F;
            flash(pose, e.getMultiBufferSource(), fx, fy, fz, gun.kind() == GunInfo.Kind.PISTOL || gun.kind() == GunInfo.Kind.SMG ? 0.055F : 0.08F, t);
        }
        pose.popPose();
    }

    /** A short-lived star of additive quads at the muzzle: a bright core, an orange flare and a thin front spike. */
    static void flash(PoseStack pose, MultiBufferSource buffers, float x, float y, float z, float size, float t) {
        VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
        pose.pushPose();
        pose.translate(x, y, z);
        float spin = (t * 37.0F) % 360.0F;
        pose.mulPose(Axis.ZP.rotationDegrees(spin));
        Matrix4f m = pose.last().pose();
        for (int i = 0; i < 3; i++) {
            pose.mulPose(Axis.ZP.rotationDegrees(60.0F));
            m = pose.last().pose();
            quad(vc, m, -size * 0.18F, 0, size * 0.18F, size * 1.1F, 255, 215, 120, 200, 255, 120, 30, 0);
            quad(vc, m, -size * 0.18F, -size * 1.1F, size * 0.18F, 0, 255, 215, 120, 200, 255, 120, 30, 0);
        }
        // forward spike along the barrel
        pose.popPose();
        pose.pushPose();
        pose.translate(x, y, z);
        m = pose.last().pose();
        float s = size * 0.5F;
        spike(vc, m, -s * 0.25F, 0, s * 0.25F, 0, s * 0.05F, 0, -s * 0.05F, 0, size);
        spike(vc, m, 0, -s * 0.25F, 0, s * 0.25F, 0, s * 0.05F, 0, -s * 0.05F, size);
        pose.popPose();
    }

    private static void spike(VertexConsumer vc, Matrix4f m, float ax, float ay, float bx, float by, float cx, float cy, float dx, float dy, float size) {
        float far = -size * 1.6F;
        both(vc, m, new float[][]{{ax, ay, 0}, {bx, by, 0}, {cx, cy, far}, {dx, dy, far}}, new int[][]{{255, 240, 180, 220}, {255, 240, 180, 220}, {255, 140, 40, 0}, {255, 140, 40, 0}});
    }

    /** Emits a quad with both windings so it shows from either side regardless of culling. */
    private static void both(VertexConsumer vc, Matrix4f m, float[][] v, int[][] c) {
        for (int i = 0; i < 4; i++) vc.addVertex(m, v[i][0], v[i][1], v[i][2]).setColor(c[i][0], c[i][1], c[i][2], c[i][3]);
        for (int i = 3; i >= 0; i--) vc.addVertex(m, v[i][0], v[i][1], v[i][2]).setColor(c[i][0], c[i][1], c[i][2], c[i][3]);
    }

    /** A vertical quad in the XY plane from (x0,y0) to (x1,y1), colour fading from the inner edge to the outer edge. */
    private static void quad(VertexConsumer vc, Matrix4f m, float x0, float y0, float x1, float y1, int r0, int g0, int b0, int a0, int r1, int g1, int b1, int a1) {
        boolean up = y1 > 0;
        float yi = up ? y0 : y1, yo = up ? y1 : y0;
        both(vc, m, new float[][]{{x0, yi, 0}, {x1, yi, 0}, {x1 * 0.4F, yo, 0}, {x0 * 0.4F, yo, 0}},
                new int[][]{{r0, g0, b0, a0}, {r0, g0, b0, a0}, {r1, g1, b1, a1}, {r1, g1, b1, a1}});
    }
}
