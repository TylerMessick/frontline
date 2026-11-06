package com.messickmedia.frontline.client;

import com.messickmedia.frontline.net.ModNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** Opens when a player plants their first Command Flag: name the nation, name its army, pick the banner colour. */
public class NationScreen extends Screen {
    private static final int[] COLORS = {0x8A1C1C, 0x1C4F8A, 0x2F6B2F, 0x7A5A1C, 0x4F1C7A, 0x1C7A6B, 0x3A3A3A, 0xC08A1C};
    private final BlockPos pos;
    private EditBox nameBox, armyBox;
    private int color = COLORS[0];
    private Button found;

    public NationScreen(BlockPos pos) {
        super(Component.translatable("screen.frontline.found_nation"));
        this.pos = pos;
    }

    @Override
    protected void init() {
        int cx = this.width / 2, top = this.height / 2 - 70;
        nameBox = new EditBox(this.font, cx - 100, top + 16, 200, 20, Component.translatable("screen.frontline.nation_name"));
        nameBox.setMaxLength(24);
        nameBox.setHint(Component.translatable("screen.frontline.nation_hint"));
        nameBox.setResponder(s -> validate());
        armyBox = new EditBox(this.font, cx - 100, top + 56, 200, 20, Component.translatable("screen.frontline.army_name"));
        armyBox.setMaxLength(24);
        armyBox.setHint(Component.translatable("screen.frontline.army_hint"));
        this.addRenderableWidget(nameBox);
        this.addRenderableWidget(armyBox);
        for (int i = 0; i < COLORS.length; i++) {
            final int c = COLORS[i];
            Button b = Button.builder(Component.literal("■").withColor(c), btn -> { color = c; }).bounds(cx - 100 + i * 25, top + 96, 22, 20).build();
            this.addRenderableWidget(b);
        }
        found = Button.builder(Component.translatable("screen.frontline.found"), b -> submit()).bounds(cx - 100, top + 130, 120, 20).build();
        this.addRenderableWidget(found);
        this.addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> this.onClose()).bounds(cx + 25, top + 130, 75, 20).build());
        this.setInitialFocus(nameBox);
        validate();
    }

    private void validate() {
        found.active = nameBox.getValue().trim().length() >= 2;
    }

    private void submit() {
        String name = nameBox.getValue().trim();
        if (name.length() < 2) return;
        String army = armyBox.getValue().trim();
        if (army.isEmpty()) army = name + " Army";
        PacketDistributor.sendToServer(new ModNetwork.FoundNation(pos, name, army, color));
        this.onClose();
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if ((key == 257 || key == 335) && found.active) { submit(); return true; }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        super.renderBackground(g, mx, my, pt);
        int cx = this.width / 2, top = this.height / 2 - 70;
        g.fill(cx - 112, top - 24, cx + 112, top + 160, 0x88000000);
        g.drawCenteredString(this.font, this.title, cx, top - 14, 0xFFFFFFFF);
        g.drawString(this.font, Component.translatable("screen.frontline.nation_name"), cx - 100, top + 4, 0xFFBBBBBB);
        g.drawString(this.font, Component.translatable("screen.frontline.army_name"), cx - 100, top + 44, 0xFFBBBBBB);
        g.drawString(this.font, Component.translatable("screen.frontline.banner"), cx - 100, top + 84, 0xFFBBBBBB);
        g.fill(cx - 100 + 8 * 25 + 2, top + 96, cx + 100, top + 116, 0xFF000000 | color);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
