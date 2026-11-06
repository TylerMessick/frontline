package com.messickmedia.frontline.client;

import com.messickmedia.frontline.gun.GunInfo;
import com.messickmedia.frontline.gun.GunItem;
import com.messickmedia.frontline.gun.Trooper;
import com.messickmedia.frontline.net.ModNetwork;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The weapon wheel. Hold the wheel key: eight slots fan out around the crosshair; point at one and let go to draw
 * that gun (the gun in your hand goes into its slot). Click the centre to store the gun you are holding in a free
 * slot; right-click a slot to drop its gun into your inventory.
 */
public class WheelScreen extends Screen {
    private static final int SLOTS = Trooper.ARMORY_SLOTS;
    private int hovered = -1;
    private float open;

    public WheelScreen() {
        super(Component.translatable("screen.frontline.wheel"));
    }

    @Override
    protected void init() {
        open = 0.0F;
    }

    private static boolean keyHeld() {
        Minecraft mc = Minecraft.getInstance();
        return InputConstants.isKeyDown(mc.getWindow().getWindow(), FrontlineClient.KEY_WHEEL.getKey().getValue());
    }

    @Override
    public void tick() {
        if (!keyHeld()) {
            if (hovered >= 0) PacketDistributor.sendToServer(new ModNetwork.ArmoryAction(0, hovered));
            this.onClose();
        }
    }

    private void updateHover(double mx, double my) {
        int cx = this.width / 2, cy = this.height / 2;
        double dx = mx - cx, dy = my - cy;
        double d = Math.sqrt(dx * dx + dy * dy);
        if (d < 22) { hovered = -1; return; }
        double ang = Math.toDegrees(Math.atan2(dy, dx)) + 90.0D + 360.0D / SLOTS / 2.0D;
        hovered = (int) Math.floor(((ang % 360.0D) + 360.0D) % 360.0D / (360.0D / SLOTS));
    }

    @Override
    public void mouseMoved(double mx, double my) {
        updateHover(mx, my);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        updateHover(mx, my);
        if (hovered < 0) {
            // centre: stash the held gun in the first free slot
            ItemStack held = Minecraft.getInstance().player == null ? ItemStack.EMPTY : Minecraft.getInstance().player.getMainHandItem();
            if (held.getItem() instanceof GunItem) {
                int free = -1;
                for (int i = 0; i < SLOTS; i++) if (ClientState.armory.get(i).isEmpty()) { free = i; break; }
                if (free >= 0) PacketDistributor.sendToServer(new ModNetwork.ArmoryAction(1, free));
            }
            return true;
        }
        if (button == 1) {
            PacketDistributor.sendToServer(new ModNetwork.ArmoryAction(2, hovered));
            return true;
        }
        if (button == 0) {
            if (ClientState.armory.get(hovered).isEmpty()) PacketDistributor.sendToServer(new ModNetwork.ArmoryAction(1, hovered));
            else { PacketDistributor.sendToServer(new ModNetwork.ArmoryAction(0, hovered)); this.onClose(); }
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, this.width, this.height, 0x55000000);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        open = Math.min(1.0F, open + pt * 0.25F);
        updateHover(mx, my);
        int cx = this.width / 2, cy = this.height / 2;
        float radius = 78.0F * (0.6F + 0.4F * open);
        ItemStack held = this.minecraft.player == null ? ItemStack.EMPTY : this.minecraft.player.getMainHandItem();
        for (int i = 0; i < SLOTS; i++) {
            double ang = Math.toRadians(-90.0D + i * 360.0D / SLOTS);
            int x = cx + (int) (Math.cos(ang) * radius), y = cy + (int) (Math.sin(ang) * radius);
            boolean hov = i == hovered;
            ItemStack s = ClientState.armory.get(i);
            int bg = hov ? 0xD0D9A441 : s.isEmpty() ? 0x80202630 : 0xC0161B23;
            g.fill(x - 17, y - 17, x + 17, y + 17, bg);
            g.fill(x - 17, y - 17, x + 17, y - 16, hov ? 0xFFFFE0A0 : 0x60FFFFFF);
            if (!s.isEmpty()) {
                g.renderItem(s, x - 8, y - 10);
                String rounds = String.valueOf(GunItem.rounds(s));
                g.pose().pushPose();
                g.pose().scale(0.7F, 0.7F, 1.0F);
                g.drawString(this.font, rounds, (int) ((x + 14 - this.font.width(rounds) * 0.7F) / 0.7F), (int) ((y + 8) / 0.7F), 0xFFFFD040, true);
                g.pose().popPose();
            } else {
                g.drawCenteredString(this.font, String.valueOf(i + 1), x, y - 4, 0x60FFFFFF);
            }
        }
        // centre
        boolean canStore = held.getItem() instanceof GunItem;
        g.fill(cx - 20, cy - 20, cx + 20, cy + 20, hovered < 0 && canStore ? 0xC0D9A441 : 0x90161B23);
        if (canStore) g.renderItem(held, cx - 8, cy - 8);
        Component label;
        if (hovered >= 0) {
            ItemStack s = ClientState.armory.get(hovered);
            if (s.isEmpty()) label = Component.translatable(canStore ? "screen.frontline.wheel_store_here" : "screen.frontline.wheel_empty");
            else {
                GunInfo gun = GunItem.gunOf(s);
                label = Component.literal(s.getHoverName().getString() + (gun == null ? "" : "  " + GunItem.rounds(s) + "/" + gun.magSize()));
            }
        } else label = Component.translatable(canStore ? "screen.frontline.wheel_store" : "screen.frontline.wheel_hint");
        g.drawCenteredString(this.font, label, cx, cy + (int) radius + 26, 0xFFFFFFFF);
        g.drawCenteredString(this.font, Component.translatable("screen.frontline.wheel_tip"), cx, cy + (int) radius + 38, 0xFF8A8F98);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key >= 49 && key < 49 + SLOTS) {
            int slot = key - 49;
            if (!ClientState.armory.get(slot).isEmpty()) { PacketDistributor.sendToServer(new ModNetwork.ArmoryAction(0, slot)); this.onClose(); }
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }
}
