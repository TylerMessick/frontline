package com.messickmedia.frontline.client;

import com.messickmedia.frontline.Frontline;
import com.messickmedia.frontline.entity.Bullet;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/** A tracer: a thin glowing streak stretched back along the bullet's flight, bright at the tip. */
public class BulletRenderer extends EntityRenderer<Bullet> {
    private static final ResourceLocation TEX = Frontline.id("textures/item/gun_atlas.png");

    public BulletRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.0F;
    }

    @Override
    public void render(Bullet b, float yaw, float pt, PoseStack pose, MultiBufferSource buffers, int light) {
        if (b.tickCount < 1) return;
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(Mth.lerp(pt, b.yRotO, b.getYRot()) - 90.0F));
        pose.mulPose(Axis.ZP.rotationDegrees(Mth.lerp(pt, b.xRotO, b.getXRot())));
        // the entity now points along +X; the streak trails behind (negative X)
        int style = b.style();
        float speed = (float) b.getDeltaMovement().length();
        float len = style == 2 ? speed * 0.5F : speed * 0.9F;
        float w = style == 1 ? 0.035F : 0.022F;
        VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
        Matrix4f m = pose.last().pose();
        int r = 255, g = style == 1 ? 200 : 230, bl = style == 1 ? 90 : 140;
        // a cross of two long quads, both windings
        streak(vc, m, len, w, r, g, bl, true);
        streak(vc, m, len, w, r, g, bl, false);
        pose.popPose();
        super.render(b, yaw, pt, pose, buffers, light);
    }

    private static void streak(VertexConsumer vc, Matrix4f m, float len, float w, int r, int g, int b, boolean vertical) {
        float[][] v = vertical
                ? new float[][]{{0.1F, -w, 0}, {0.1F, w, 0}, {-len, w * 0.3F, 0}, {-len, -w * 0.3F, 0}}
                : new float[][]{{0.1F, 0, -w}, {0.1F, 0, w}, {-len, 0, w * 0.3F}, {-len, 0, -w * 0.3F}};
        int[][] c = {{255, 255, 240, 255}, {255, 255, 240, 255}, {r, g, b, 0}, {r, g, b, 0}};
        for (int i = 0; i < 4; i++) vc.addVertex(m, v[i][0], v[i][1], v[i][2]).setColor(c[i][0], c[i][1], c[i][2], c[i][3]);
        for (int i = 3; i >= 0; i--) vc.addVertex(m, v[i][0], v[i][1], v[i][2]).setColor(c[i][0], c[i][1], c[i][2], c[i][3]);
    }

    @Override
    public ResourceLocation getTextureLocation(Bullet b) { return TEX; }
}
