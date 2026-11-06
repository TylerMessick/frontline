package com.messickmedia.frontline.client;

import com.messickmedia.frontline.Frontline;
import com.messickmedia.frontline.entity.SoldierEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class SoldierRenderer extends HumanoidMobRenderer<SoldierEntity, SoldierModel> {
    private static final ResourceLocation TEX = Frontline.id("textures/entity/soldier.png");

    public SoldierRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new SoldierModel(ctx.bakeLayer(SoldierModel.LAYER)), 0.5F);
        this.addLayer(new net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer<>(this,
                new net.minecraft.client.model.HumanoidArmorModel<>(ctx.bakeLayer(net.minecraft.client.model.geom.ModelLayers.PLAYER_INNER_ARMOR)),
                new net.minecraft.client.model.HumanoidArmorModel<>(ctx.bakeLayer(net.minecraft.client.model.geom.ModelLayers.PLAYER_OUTER_ARMOR)),
                ctx.getModelManager()));
        this.addLayer(new BandLayer(this));
        this.addLayer(new PatchLayer<>(this));
    }

    @Override
    public ResourceLocation getTextureLocation(SoldierEntity e) { return TEX; }

    @Override
    protected void renderNameTag(SoldierEntity e, Component name, PoseStack pose, MultiBufferSource buffers, int light, float pt) {
        super.renderNameTag(e, name, pose, buffers, light, pt);
        String nation = e.nationName();
        if (nation.isEmpty()) return;
        pose.pushPose();
        pose.translate(0.0F, -0.27F, 0.0F);
        super.renderNameTag(e, Component.literal(nation).withColor(GunHud.brighten(e.color())), pose, buffers, light, pt);
        pose.popPose();
    }

    /** Nation armband, tinted. */
    static class BandLayer extends RenderLayer<SoldierEntity, SoldierModel> {
        BandLayer(RenderLayerParent<SoldierEntity, SoldierModel> parent) { super(parent); }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, SoldierEntity e, float limbSwing, float limbSwingAmount, float pt, float age, float yaw, float pitch) {
            if (e.isInvisible() || !e.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).isEmpty()) return;
            var model = this.getParentModel();
            model.band.visible = true;
            model.band.copyFrom(model.rightArm);
            model.band.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(TEX)), light, OverlayTexture.NO_OVERLAY, 0xFF000000 | e.color());
            model.band.visible = false;
        }
    }
}
