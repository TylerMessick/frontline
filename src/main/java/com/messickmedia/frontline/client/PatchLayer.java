package com.messickmedia.frontline.client;

import com.messickmedia.frontline.entity.SoldierEntity;
import com.messickmedia.frontline.nation.FlagDesign;
import com.messickmedia.frontline.registry.ModRegistries;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;

/**
 * The flag patch on the outside of the left shoulder, on top of the armor. Players show the patch sewn onto their
 * chest armor; soldiers show their nation's flag whenever they wear chest armor.
 */
public class PatchLayer<T extends LivingEntity, M extends HumanoidModel<T>> extends RenderLayer<T, M> {
    public PatchLayer(RenderLayerParent<T, M> parent) {
        super(parent);
    }

    static String design(LivingEntity e) {
        ItemStack chest = e.getItemBySlot(EquipmentSlot.CHEST);
        if (!(chest.getItem() instanceof ArmorItem)) return null;
        String d = chest.get(ModRegistries.FLAG.get());
        if (FlagDesign.valid(d)) return d;
        if (e instanceof SoldierEntity s && FlagDesign.valid(s.flag())) return s.flag();
        return null;
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, T e, float limbSwing, float limbAmount, float pt, float age, float yaw, float pitch) {
        if (e.isInvisible()) return;
        String d = design(e);
        if (d == null) return;
        var vc = buffers.getBuffer(RenderType.entityCutoutNoCull(FlagTextures.get(d)));
        pose.pushPose();
        this.getParentModel().leftArm.translateAndRotate(pose);
        // left arm box spans x -1..3, y -2..10 in model units; chest-armor sits 1 unit out, so the patch is at x 4.05
        pose.translate(4.06F / 16.0F, 0.0F, 0.0F);
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(90.0F));
        // now the quad's +Z faces outward; entity model space has +y down, so the flag's top is the smaller y
        float w = 3.2F / 16.0F, h = 2.0F / 16.0F, top = -0.6F / 16.0F;
        FlagTextures.quad(vc, pose.last(), w / 2, top + h, -w / 2, top, 0.0F, 0.0F, 1.0F, 0.0F, 1.0F, light);
        pose.popPose();
    }
}
