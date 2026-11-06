package com.messickmedia.frontline.client;

import com.messickmedia.frontline.Frontline;
import com.messickmedia.frontline.gun.GunInfo;
import com.messickmedia.frontline.gun.GunItem;
import com.messickmedia.frontline.entity.VehicleEntity;
import com.messickmedia.frontline.entity.VehicleKind;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/**
 * Shooter HUD: a dynamic crosshair that opens with spread, a scope overlay for the marksman rifle, hit markers,
 * the ammo counter with reload bar at the bottom right, and the nation chip at the top left.
 */
public final class GunHud {
    private GunHud() {}

    private static final ResourceLocation SCOPE = Frontline.id("textures/gui/scope.png");

    /** Crosshair layer (replaces vanilla's while a gun is held). */
    public static void crosshair(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        GunInfo gun = GunItem.gunOf(mc.player.getMainHandItem());
        if (mc.player.getVehicle() instanceof VehicleEntity v) { vehicleCrosshair(g, v); return; }
        if (gun == null) return;
        float pt = delta.getGameTimeDeltaPartialTick(true);
        float ads = Mth.lerp(pt, ClientState.adsBlendO, ClientState.adsBlend);
        int cx = g.guiWidth() / 2, cy = g.guiHeight() / 2;
        if (gun.scope() && ads > 0.92F) {
            scope(g, cx, cy);
        } else if (!(ads > 0.6F && !ClientState.ots)) {
            // open crosshair: gap grows with the current spread
            float spread = (ads > 0.5F ? gun.adsSpread() : gun.hipSpread()) + ClientState.bloom;
            if (mc.player.isSprinting()) spread *= 1.6F;
            int gap = 3 + Math.round(spread * 3.2F);
            int len = 5;
            int col = ClientState.reloading() ? 0x80FFFFFF : 0xE0FFFFFF;
            g.fill(cx - gap - len, cy, cx - gap, cy + 1, col);
            g.fill(cx + gap + 1, cy, cx + gap + len + 1, cy + 1, col);
            g.fill(cx, cy - gap - len, cx + 1, cy - gap, col);
            g.fill(cx, cy + gap + 1, cx + 1, cy + gap + len + 1, col);
            g.fill(cx, cy, cx + 1, cy + 1, col);
        } else {
            g.fill(cx, cy, cx + 1, cy + 1, 0x90FFFFFF);
        }
        if (ClientState.hitTicks > 0) {
            int a = Math.min(255, ClientState.hitTicks * 32);
            int col = (a << 24) | (ClientState.hitKind == 2 ? 0xE03030 : ClientState.hitKind == 1 ? 0xFFD040 : 0xFFFFFF);
            int d = ClientState.hitKind == 2 ? 6 : 4;
            for (int i = 0; i < 4; i++) {
                int x = cx + (d + i) * (1), y = cy + (d + i);
                g.fill(x, y, x + 1, y + 1, col);
                g.fill(cx - (d + i), y, cx - (d + i) + 1, y + 1, col);
                g.fill(x, cy - (d + i), x + 1, cy - (d + i) + 1, col);
                g.fill(cx - (d + i), cy - (d + i), cx - (d + i) + 1, cy - (d + i) + 1, col);
            }
        }
    }

    private static void vehicleCrosshair(GuiGraphics g, VehicleEntity v) {
        int cx = g.guiWidth() / 2, cy = g.guiHeight() / 2;
        int col = 0xE0FFFFFF;
        // a gunsight box with a centre dot
        g.fill(cx - 9, cy - 9, cx - 3, cy - 8, col); g.fill(cx - 9, cy - 9, cx - 8, cy - 3, col);
        g.fill(cx + 4, cy - 9, cx + 10, cy - 8, col); g.fill(cx + 9, cy - 9, cx + 10, cy - 3, col);
        g.fill(cx - 9, cy + 9, cx - 3, cy + 10, col); g.fill(cx - 9, cy + 4, cx - 8, cy + 10, col);
        g.fill(cx + 4, cy + 9, cx + 10, cy + 10, col); g.fill(cx + 9, cy + 4, cx + 10, cy + 10, col);
        g.fill(cx, cy, cx + 1, cy + 1, col);
        if (ClientState.hitTicks > 0) {
            int a = Math.min(255, ClientState.hitTicks * 32);
            int hc = (a << 24) | (ClientState.hitKind == 2 ? 0xE03030 : 0xFFFFFF);
            for (int i = 0; i < 4; i++) {
                g.fill(cx + 4 + i, cy + 4 + i, cx + 5 + i, cy + 5 + i, hc); g.fill(cx - 4 - i, cy + 4 + i, cx - 3 - i, cy + 5 + i, hc);
                g.fill(cx + 4 + i, cy - 4 - i, cx + 5 + i, cy - 3 - i, hc); g.fill(cx - 4 - i, cy - 4 - i, cx - 3 - i, cy - 3 - i, hc);
            }
        }
    }

    private static void vehiclePanel(GuiGraphics g, Font font, VehicleEntity v, int w, int h) {
        int x = w - 12, y = h - 30;
        Component name = Component.translatable("entity.frontline." + v.kind().id);
        g.drawString(font, name, x - font.width(name), y - 22, 0xFFDDDDDD, true);
        int barW = 90;
        float hp = v.health() / v.kind().maxHealth;
        g.fill(x - barW - 1, y - 9, x + 1, y - 3, 0xAA000000);
        g.fill(x - barW, y - 8, x - barW + Math.round(barW * Mth.clamp(hp, 0, 1)), y - 4, hp < 0.3F ? 0xFFE03030 : hp < 0.6F ? 0xFFFFC040 : 0xFF6FCF5A);
        String hpText = Math.round(v.health()) + " / " + Math.round(v.kind().maxHealth);
        g.pose().pushPose(); g.pose().scale(0.75F, 0.75F, 1.0F);
        g.drawString(font, hpText, (int) ((x - font.width(hpText) * 0.75F) / 0.75F), (int) ((y - 1) / 0.75F), 0xFFBBBBBB, true);
        g.pose().popPose();
        if (v.kind().flies() || v.kind() == VehicleKind.JEEP || v.kind() == VehicleKind.TANK) {
            float th = v.throttle();
            g.fill(x - barW - 1, y + 7, x + 1, y + 11, 0xAA000000);
            g.fill(x - barW, y + 8, x - barW + Math.round(barW * Mth.clamp(th, 0, 1)), y + 10, 0xFFD9A441);
            String tl = (v.kind() == VehicleKind.HELI ? "ROTOR " : v.kind() == VehicleKind.PLANE ? "THROTTLE " : "SPEED ") + Math.round(th * 100) + "%";
            g.pose().pushPose(); g.pose().scale(0.75F, 0.75F, 1.0F);
            g.drawString(font, tl, (int) ((x - font.width(tl) * 0.75F) / 0.75F), (int) ((y + 13) / 0.75F), 0xFF8A8F98, true);
            g.pose().popPose();
        }
        Component hint = Component.translatable("hud.frontline.vehicle." + v.kind().id);
        g.pose().pushPose(); g.pose().scale(0.75F, 0.75F, 1.0F);
        g.drawString(font, hint, (int) ((w / 2 - font.width(hint) * 0.75F / 2) / 0.75F), (int) ((h - 46) / 0.75F), 0x90FFFFFF, true);
        g.pose().popPose();
    }

    private static void scope(GuiGraphics g, int cx, int cy) {
        int h = g.guiHeight(), w = g.guiWidth();
        int r = Math.round(h * 0.47F);
        // black bars left and right of the scope circle, then the circular mask texture
        g.fill(0, 0, cx - r, h, 0xFF000000);
        g.fill(cx + r, 0, w, h, 0xFF000000);
        g.blit(SCOPE, cx - r, cy - r, 2 * r, 2 * r, 0, 0, 256, 256, 256, 256);
        // reticle
        int col = 0xFF101010;
        g.fill(cx - r, cy, cx - 3, cy + 1, col);
        g.fill(cx + 4, cy, cx + r, cy + 1, col);
        g.fill(cx, cy - r, cx + 1, cy - 3, col);
        g.fill(cx, cy + 4, cx + 1, cy + r, col);
        for (int i = 1; i <= 4; i++) {
            int d = i * r / 5;
            g.fill(cx + d, cy - 3, cx + d + 1, cy + 4, col);
            g.fill(cx - d, cy - 3, cx - d + 1, cy + 4, col);
            g.fill(cx - 3, cy + d, cx + 4, cy + d + 1, col);
            g.fill(cx - 3, cy - d, cx + 4, cy - d + 1, col);
        }
        g.fill(cx, cy, cx + 1, cy + 1, 0xFFE03030);
        Component z = Component.translatable("hud.frontline.zoom", String.format("%.1f", ClientState.zoom));
        g.drawString(Minecraft.getInstance().font, z, cx - Minecraft.getInstance().font.width(z) / 2, cy + r - 14, 0xFFDDDDDD, true);
    }

    /** Ammo counter, reload bar, kill tally and the nation chip. */
    public static void panel(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        Font font = mc.font;
        ItemStack stack = mc.player.getMainHandItem();
        GunInfo gun = GunItem.gunOf(stack);
        int w = g.guiWidth(), h = g.guiHeight();
        if (mc.player.getVehicle() instanceof VehicleEntity v) vehiclePanel(g, font, v, w, h);
        else if (gun != null) {
            int rounds = GunItem.rounds(stack);
            int x = w - 12, y = h - 30;
            String big = String.valueOf(rounds);
            String rest = "/ " + ClientState.ammoCount;
            int col = rounds == 0 ? 0xFFE03030 : rounds <= gun.magSize() / 4 ? 0xFFFFC040 : 0xFFFFFFFF;
            g.drawString(font, rest, x - font.width(rest), y + 1, 0xFFBBBBBB, true);
            g.pose().pushPose();
            g.pose().translate(x - font.width(rest) - 5, y - 8, 0);
            g.pose().scale(2.0F, 2.0F, 1.0F);
            g.drawString(font, big, -font.width(big), 0, col, true);
            g.pose().popPose();
            Component name = Component.translatable("item.frontline." + gun.id());
            g.drawString(font, name, x - font.width(name), y - 22, 0xFFDDDDDD, true);
            String mode = (gun.auto() ? "AUTO" : gun.kind() == GunInfo.Kind.SNIPER || gun.kind() == GunInfo.Kind.SHOTGUN ? "PUMP/BOLT" : "SEMI") + "  " + (ClientState.ots ? "OTS" : "FPS");
            g.drawString(font, mode, x - font.width(mode), y + 6, 0xFF8A8F98, true);
            if (ClientState.reloading() && ClientState.reloadMax > 0) {
                float pt = delta.getGameTimeDeltaPartialTick(true);
                float prog = 1.0F - (ClientState.reloadTicks - pt) / ClientState.reloadMax;
                int barW = 72;
                int bx = x - barW, by = y - 32;
                g.fill(bx - 1, by - 1, bx + barW + 1, by + 4, 0xAA000000);
                g.fill(bx, by, bx + Math.round(barW * Mth.clamp(prog, 0, 1)), by + 3, 0xFFFFC040);
                Component rl = Component.translatable("hud.frontline.reloading");
                g.drawString(font, rl, x - font.width(rl), by - 11, 0xFFFFC040, true);
            } else if (GunItem.rounds(stack) == 0) {
                Component rl = Component.translatable("hud.frontline.press_reload", "R");
                g.drawString(font, rl, x - font.width(rl), y - 32 - 11, 0xFFE03030, true);
            }
            if (ClientState.kills > 0 || ClientState.shots > 0) {
                int acc = ClientState.shots == 0 ? 0 : Math.round(100.0F * ClientState.hits / ClientState.shots);
                String st = ClientState.kills + " kills  " + acc + "% acc";
                g.pose().pushPose();
                g.pose().scale(0.75F, 0.75F, 1.0F);
                g.drawString(font, st, (int) ((x - font.width(st) * 0.75F) / 0.75F), (int) ((y + 16) / 0.75F), 0xFF8A8F98, true);
                g.pose().popPose();
            }
            // ADS hint on first hold
            if (ClientState.holdTicks > 0 && ClientState.holdTicks < 120 && ClientState.adsBlend < 0.1F) {
                Component hint = Component.translatable("hud.frontline.hint");
                g.drawString(font, hint, w / 2 - font.width(hint) / 2, h - 70, 0x90FFFFFF, true);
            }
        }
        // nation chip
        if (!ClientState.nation.isEmpty()) {
            int col = 0xFF000000 | brighten(ClientState.nationColor);
            int x = 6, y = 6;
            g.fill(x - 2, y - 2, x + 2, y + 20, col);
            g.drawString(font, Component.literal(ClientState.nation), x + 4, y, col, true);
            String sub = ClientState.army + "  ·  " + ClientState.soldiers + " troops  ·  " + ClientState.bases + (ClientState.bases == 1 ? " base" : " bases");
            g.pose().pushPose();
            g.pose().scale(0.75F, 0.75F, 1.0F);
            g.drawString(font, sub, (int) ((x + 4) / 0.75F), (int) ((y + 11) / 0.75F), 0xFFBBBBBB, true);
            g.pose().popPose();
        }
        if (!ClientState.claim.isEmpty()) {
            int col = 0xFF000000 | brighten(ClientState.claimColor);
            Component t = Component.translatable(ClientState.claimHostile ? "hud.frontline.hostile_land" : ClientState.claim.equals(ClientState.nation) ? "hud.frontline.home_land" : "hud.frontline.land", ClientState.claim);
            g.pose().pushPose();
            g.pose().scale(0.75F, 0.75F, 1.0F);
            g.drawString(font, t, (int) (6 / 0.75F), (int) ((ClientState.nation.isEmpty() ? 6 : 26) / 0.75F), ClientState.claimHostile ? 0xFFE03030 : col, true);
            g.pose().popPose();
        }
        if (ClientState.claimBannerTicks > 0 && !ClientState.claimBanner.isEmpty()) {
            float pt = delta.getGameTimeDeltaPartialTick(true);
            float a = Mth.clamp((ClientState.claimBannerTicks - pt) / 20.0F, 0.0F, 1.0F);
            int alpha = Math.max(8, Math.round(a * 255));
            int col = (alpha << 24) | brighten(ClientState.claimBannerColor);
            Component t = Component.translatable("hud.frontline.entering", ClientState.claimBanner);
            g.pose().pushPose();
            g.pose().translate(w / 2.0F, h * 0.22F, 0);
            g.pose().scale(1.5F, 1.5F, 1.0F);
            g.drawString(font, t, -font.width(t) / 2, 0, col, true);
            g.pose().popPose();
        }
    }

    static int brighten(int c) {
        int r = Math.min(255, ((c >> 16) & 255) + 70), gg = Math.min(255, ((c >> 8) & 255) + 70), b = Math.min(255, (c & 255) + 70);
        return (r << 16) | (gg << 8) | b;
    }
}
