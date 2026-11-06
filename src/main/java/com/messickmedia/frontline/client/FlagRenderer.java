package com.messickmedia.frontline.client;

import com.messickmedia.frontline.Frontline;
import com.messickmedia.frontline.block.CommandFlagBlock;
import com.messickmedia.frontline.block.CommandFlagBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Command Flag above its block: a tall pole with a brass finial, a cloth of eight waving strips tinted the
 * nation's colour (with a lighter band), and the nation and army names floating above when you are close.
 */
public class FlagRenderer implements BlockEntityRenderer<CommandFlagBlockEntity> {
    private static final ResourceLocation TEX = Frontline.id("textures/entity/flag.png");
    private static final int STRIPS = 8;
    private final ModelPart pole, finial;
    private final ModelPart[] strips = new ModelPart[STRIPS];
    private final ModelPart[] bands = new ModelPart[STRIPS];

    public FlagRenderer(BlockEntityRendererProvider.Context ctx) {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("pole", CubeListBuilder.create().texOffs(0, 0).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 36.0F, 2.0F), PartPose.ZERO);
        root.addOrReplaceChild("finial", CubeListBuilder.create().texOffs(8, 0).addBox(-1.5F, 36.0F, -1.5F, 3.0F, 3.0F, 3.0F)
                .texOffs(8, 6).addBox(-0.5F, 39.0F, -0.5F, 1.0F, 2.0F, 1.0F), PartPose.ZERO);
        for (int i = 0; i < STRIPS; i++) {
            root.addOrReplaceChild("strip" + i, CubeListBuilder.create().texOffs(16 + i * 2, 16).addBox(0.0F, 0.0F, -0.25F, 2.0F, 11.0F, 0.5F), PartPose.ZERO);
            root.addOrReplaceChild("band" + i, CubeListBuilder.create().texOffs(16 + i * 2, 28).addBox(0.0F, 4.0F, -0.35F, 2.0F, 3.0F, 0.7F), PartPose.ZERO);
        }
        ModelPart baked = LayerDefinition.create(mesh, 64, 64).bakeRoot();
        this.pole = baked.getChild("pole");
        this.finial = baked.getChild("finial");
        for (int i = 0; i < STRIPS; i++) { strips[i] = baked.getChild("strip" + i); bands[i] = baked.getChild("band" + i); }
    }

    @Override
    public void render(CommandFlagBlockEntity be, float pt, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        BlockState state = be.getBlockState();
        Direction facing = state.hasProperty(CommandFlagBlock.FACING) ? state.getValue(CommandFlagBlock.FACING) : Direction.NORTH;
        float t = (be.getLevel() == null ? 0 : be.getLevel().getGameTime()) + pt;
        int color = be.bound() ? 0xFF000000 | be.color() : 0xFF8A8A8A;
        int lightCol = 0xFF000000 | GunHud.brighten(be.bound() ? be.color() : 0x8A8A8A);
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(TEX));
        pose.pushPose();
        // model units are 1/16 block with +y up (no entity flip here); the pole starts at the top of the block
        pose.translate(0.5F, 1.0F, 0.5F);
        pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        pole.render(pose, vc, light, overlay, 0xFFFFFFFF);
        finial.render(pose, vc, light, overlay, 0xFFFFFFFF);
        // cloth hangs from just under the finial, flying off the pole's side
        pose.pushPose();
        pose.translate(1.0F / 16.0F, 24.0F / 16.0F, 0.0F);
        float wind = be.bound() ? 1.0F : 0.4F;
        String design = be.design();
        if (be.bound() && com.messickmedia.frontline.nation.FlagDesign.valid(design)) {
            // the painted flag: the same eight waving strips, each showing its two columns of the design
            VertexConsumer cloth = buffers.getBuffer(RenderType.entityCutoutNoCull(FlagTextures.get(design)));
            for (int i = 0; i < STRIPS; i++) {
                float phase = t * 0.18F - i * 0.55F;
                float amp = 0.9F * wind * (i + 1) / STRIPS;
                pose.pushPose();
                pose.translate(i * 2.0F / 16.0F, 0.0F, Mth.sin(phase) * amp / 16.0F);
                pose.mulPose(Axis.YP.rotationDegrees(-Mth.cos(phase) * amp * 0.55F * 18.0F));
                FlagTextures.quad(cloth, pose.last(), 0.0F, 1.0F / 16.0F, 2.05F / 16.0F, 11.0F / 16.0F, 0.0F,
                        i / (float) STRIPS, (i + 1) / (float) STRIPS, 0.0F, 1.0F, light);
                pose.popPose();
            }
        } else for (int i = 0; i < STRIPS; i++) {
            float phase = t * 0.18F - i * 0.55F;
            float amp = 0.9F * wind * (i + 1) / STRIPS;
            float z = Mth.sin(phase) * amp;
            float dz = Mth.cos(phase) * amp * 0.55F;
            pose.pushPose();
            pose.translate(i * 2.0F / 16.0F, 0.0F, z / 16.0F);
            pose.mulPose(Axis.YP.rotationDegrees(-dz * 18.0F));
            strips[i].render(pose, vc, light, overlay, color);
            bands[i].render(pose, vc, light, overlay, lightCol);
            pose.popPose();
        }
        pose.popPose();
        pose.popPose();

        if (be.bound()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.player.distanceToSqr(be.getBlockPos().getCenter()) < 32 * 32) {
                label(pose, buffers, mc.font, Component.literal(be.nationName()).withColor(GunHud.brighten(be.color())), 3.85F, 1.0F);
                label(pose, buffers, mc.font, Component.literal(be.armyName()), 3.62F, 0.7F);
            }
        }
    }

    private static void label(PoseStack pose, MultiBufferSource buffers, Font font, Component text, float y, float scale) {
        Minecraft mc = Minecraft.getInstance();
        pose.pushPose();
        pose.translate(0.5F, y, 0.5F);
        pose.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        pose.scale(0.025F * scale, -0.025F * scale, 0.025F * scale);
        float w = -font.width(text) / 2.0F;
        int bg = (int) (mc.options.getBackgroundOpacity(0.25F) * 255.0F) << 24;
        font.drawInBatch(text, w, 0, 0x20FFFFFF, false, pose.last().pose(), buffers, Font.DisplayMode.SEE_THROUGH, bg, LightTexture.FULL_BRIGHT);
        font.drawInBatch(text, w, 0, 0xFFFFFFFF, false, pose.last().pose(), buffers, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
        pose.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(CommandFlagBlockEntity be) { return true; }

    @Override
    public int getViewDistance() { return 96; }
}
