package com.messickmedia.frontline.client;

import com.messickmedia.frontline.Frontline;
import com.messickmedia.frontline.nation.FlagDesign;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Map;

/** Turns flag designs into small GPU textures (cached, oldest dropped after 64) and draws textured quads with them. */
public final class FlagTextures {
    private FlagTextures() {}

    private static final Map<String, ResourceLocation> CACHE = new LinkedHashMap<>(16, 0.75F, true);
    private static int counter;

    public static ResourceLocation get(String design) {
        ResourceLocation loc = CACHE.get(design);
        if (loc != null) return loc;
        NativeImage img = new NativeImage(FlagDesign.W, FlagDesign.H, false);
        for (int y = 0; y < FlagDesign.H; y++) {
            for (int x = 0; x < FlagDesign.W; x++) {
                int rgb = FlagDesign.rgb(design, x, y);
                int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
                img.setPixelRGBA(x, y, 0xFF000000 | (b << 16) | (g << 8) | r); // ABGR
            }
        }
        loc = Frontline.id("dynamic/flag_" + (counter++));
        Minecraft.getInstance().getTextureManager().register(loc, new DynamicTexture(img));
        CACHE.put(design, loc);
        if (CACHE.size() > 64) {
            var it = CACHE.entrySet().iterator();
            var eldest = it.next();
            Minecraft.getInstance().getTextureManager().release(eldest.getValue());
            it.remove();
        }
        return loc;
    }

    /** Forget every texture (on logout, so a new world starts clean). */
    public static void clear() {
        for (ResourceLocation loc : CACHE.values()) Minecraft.getInstance().getTextureManager().release(loc);
        CACHE.clear();
    }

    /**
     * A flat quad in the XY plane of the current pose, from (x0,y0) to (x1,y1), texture u0..u1 left to right and
     * v from the top edge (y1) to the bottom edge (y0). Drawn on both sides; the back is mirrored like a real flag.
     */
    public static void quad(VertexConsumer vc, PoseStack.Pose pose, float x0, float y0, float x1, float y1, float z,
                            float u0, float u1, float vTop, float vBottom, int light) {
        vert(vc, pose, x0, y1, z, u0, vTop, light, 1);
        vert(vc, pose, x0, y0, z, u0, vBottom, light, 1);
        vert(vc, pose, x1, y0, z, u1, vBottom, light, 1);
        vert(vc, pose, x1, y1, z, u1, vTop, light, 1);
        vert(vc, pose, x1, y1, z, u1, vTop, light, -1);
        vert(vc, pose, x1, y0, z, u1, vBottom, light, -1);
        vert(vc, pose, x0, y0, z, u0, vBottom, light, -1);
        vert(vc, pose, x0, y1, z, u0, vTop, light, -1);
    }

    private static void vert(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, float u, float v, int light, int nz) {
        vc.addVertex(pose, x, y, z).setColor(0xFFFFFFFF).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 0, nz);
    }
}
