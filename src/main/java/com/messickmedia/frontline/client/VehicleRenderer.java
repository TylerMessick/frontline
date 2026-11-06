package com.messickmedia.frontline.client;

import com.messickmedia.frontline.Frontline;
import com.messickmedia.frontline.entity.VehicleEntity;
import com.messickmedia.frontline.entity.VehicleKind;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.EnumMap;
import java.util.Map;

/** Draws every vehicle from its generated model and animates turrets, wheels, propellers and rotors. */
public class VehicleRenderer extends EntityRenderer<VehicleEntity> {
    private final Map<VehicleKind, ModelPart> roots = new EnumMap<>(VehicleKind.class);
    private final Map<VehicleKind, ResourceLocation> textures = new EnumMap<>(VehicleKind.class);

    public VehicleRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        roots.put(VehicleKind.TANK, ctx.bakeLayer(VehicleModels.TANK));
        roots.put(VehicleKind.PLANE, ctx.bakeLayer(VehicleModels.PLANE));
        roots.put(VehicleKind.HELI, ctx.bakeLayer(VehicleModels.HELI));
        roots.put(VehicleKind.JEEP, ctx.bakeLayer(VehicleModels.JEEP));
        for (VehicleKind k : VehicleKind.values()) textures.put(k, Frontline.id("textures/entity/" + k.id + ".png"));
        this.shadowRadius = 1.2F;
    }

    @Override
    public void render(VehicleEntity v, float yaw, float pt, PoseStack pose, MultiBufferSource buffers, int light) {
        VehicleKind kind = v.kind();
        ModelPart root = roots.get(kind);
        float yRot = Mth.rotLerp(pt, v.yRotO, v.getYRot());
        float xRot = Mth.lerp(pt, v.xRotO, v.getXRot());
        float roll = Mth.lerp(pt, v.rollO, v.roll);
        float spin = Mth.lerp(pt, v.wheelSpinO, v.wheelSpin);
        float rotor = Mth.lerp(pt, v.rotorO, v.rotor);
        float tYaw = v.turretYaw(), tPitch = v.turretPitch();
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(180.0F - yRot));
        if (kind.flies()) {
            pose.mulPose(Axis.XP.rotationDegrees(-xRot));
            pose.mulPose(Axis.ZP.rotationDegrees(roll));
        }
        if (v.hurtTicks() > 0) {
            float shake = (float) Math.sin(v.tickCount * 1.7F) * 0.6F * v.hurtTicks() / 10.0F;
            pose.mulPose(Axis.ZP.rotationDegrees(shake));
        }
        pose.scale(-1.0F, -1.0F, 1.0F);
        animate(kind, root, spin, rotor, tYaw, tPitch, v.firedTicks(), pt);
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(textures.get(kind)));
        root.render(pose, vc, light, OverlayTexture.pack(0.0F, v.hurtTicks() > 0), 0xFFFFFFFF);
        pose.popPose();
        super.render(v, yaw, pt, pose, buffers, light);
    }

    private static void animate(VehicleKind kind, ModelPart root, float spin, float rotor, float tYaw, float tPitch, int fired, float pt) {
        float sp = (float) Math.toRadians(spin), rt = (float) Math.toRadians(rotor);
        switch (kind) {
            case TANK -> {
                ModelPart hull = root.getChild("hull");
                ModelPart turret = hull.getChild("turret");
                turret.yRot = (float) Math.toRadians(-tYaw);
                ModelPart gun = turret.getChild("gun");
                gun.xRot = (float) Math.toRadians(tPitch);
                // recoil: the gun slides back for a few ticks after a shot
                gun.z = -16.0F + (fired > 0 ? 3.0F * fired / 6.0F : 0.0F);
                for (String side : new String[]{"l", "r"}) {
                    ModelPart track = hull.getChild("track_" + side);
                    for (int i = 0; i < 6; i++) track.getChild("wheel_" + side + i).xRot = sp;
                    track.getChild("sprocket_" + side).xRot = sp * 1.2F;
                    track.getChild("idler_" + side).xRot = sp * 1.2F;
                    for (int z : new int[]{-20, 0, 20}) track.getChild("roller_" + side + z).xRot = sp * 2.4F;
                }
            }
            case PLANE -> {
                ModelPart body = root.getChild("body");
                body.getChild("prop").zRot = rt * 3.0F;
                body.getChild("wheel_l").xRot = sp;
                body.getChild("wheel_r").xRot = sp;
            }
            case HELI -> {
                ModelPart body = root.getChild("body");
                body.getChild("rotor").yRot = rt;
                body.getChild("tail_rotor").xRot = rt * 2.5F;
                body.getChild("chin_gun").yRot = (float) Math.toRadians(-tYaw * 0.3F);
            }
            case JEEP -> {
                ModelPart body = root.getChild("body");
                for (String s : new String[]{"wheel_l0", "wheel_l1", "wheel_r0", "wheel_r1"}) body.getChild(s).xRot = sp;
                ModelPart mg = body.getChild("mg");
                mg.yRot = (float) Math.toRadians(-tYaw);
                mg.xRot = (float) Math.toRadians(tPitch);
            }
        }
    }

    @Override
    public ResourceLocation getTextureLocation(VehicleEntity v) {
        return textures.get(v.kind());
    }
}
