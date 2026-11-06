package com.messickmedia.frontline.client;

import com.messickmedia.frontline.nation.FlagDesign;
import com.messickmedia.frontline.net.ModNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Paint your nation's flag: a 16 x 10 grid, the 16 dye colours, pencil and bucket fill, mirror painting, undo, and a
 * few starter layouts. Left mouse paints with the primary colour, right mouse with the secondary; middle mouse picks.
 */
public class FlagPainterScreen extends Screen {
    private static final int CELL = 11;
    private final String nation;
    private final char[] px;
    private final Deque<String> undo = new ArrayDeque<>();
    private int primary = 14, secondary = 0;
    private boolean bucket, mirror;
    private Button bucketBtn, mirrorBtn;
    private int gx, gy;

    public FlagPainterScreen(String nation, String design) {
        super(Component.translatable("screen.frontline.flag_painter"));
        this.nation = nation;
        this.px = (FlagDesign.valid(design) ? design : FlagDesign.filled(0)).toCharArray();
    }

    @Override
    protected void init() {
        gx = this.width / 2 - FlagDesign.W * CELL / 2 - 40;
        gy = this.height / 2 - FlagDesign.H * CELL / 2 - 16;
        int bx = gx + FlagDesign.W * CELL + 14, by = gy;
        bucketBtn = this.addRenderableWidget(Button.builder(Component.translatable("screen.frontline.tool_bucket"), b -> { bucket = !bucket; label(); }).bounds(bx, by, 70, 18).build());
        mirrorBtn = this.addRenderableWidget(Button.builder(Component.translatable("screen.frontline.tool_mirror"), b -> { mirror = !mirror; label(); }).bounds(bx, by + 21, 70, 18).build());
        this.addRenderableWidget(Button.builder(Component.translatable("screen.frontline.undo"), b -> pop()).bounds(bx, by + 42, 70, 18).build());
        this.addRenderableWidget(Button.builder(Component.translatable("screen.frontline.clear"), b -> { push(); java.util.Arrays.fill(px, hex(secondary)); }).bounds(bx, by + 63, 70, 18).build());
        this.addRenderableWidget(Button.builder(Component.translatable("screen.frontline.preset"), b -> preset()).bounds(bx, by + 84, 70, 18).build());
        int sy = gy + FlagDesign.H * CELL + 44;
        this.addRenderableWidget(Button.builder(Component.translatable("screen.frontline.save_flag"), b -> save()).bounds(this.width / 2 - 102, sy, 100, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose()).bounds(this.width / 2 + 2, sy, 100, 20).build());
        label();
    }

    private void label() {
        bucketBtn.setMessage(Component.translatable(bucket ? "screen.frontline.tool_bucket_on" : "screen.frontline.tool_bucket"));
        mirrorBtn.setMessage(Component.translatable(mirror ? "screen.frontline.tool_mirror_on" : "screen.frontline.tool_mirror"));
    }

    private int presetIdx;

    /** Cycles a few classic layouts in the current primary/secondary colours. */
    private void preset() {
        push();
        char a = hex(primary), b = hex(secondary);
        int k = presetIdx++ % 5;
        for (int y = 0; y < FlagDesign.H; y++) for (int x = 0; x < FlagDesign.W; x++) {
            boolean p = switch (k) {
                case 0 -> x < 6;                                       // vertical bicolour
                case 1 -> y < 5;                                       // horizontal bicolour
                case 2 -> x >= 4 && x <= 5 || y >= 4 && y <= 5;        // nordic cross
                case 3 -> Math.abs(x - 7.5) * 10 / 16 + Math.abs(y - 4.5) < 3.2;  // diamond
                default -> x <= y * 0.8F;                              // hoist triangle
            };
            px[y * FlagDesign.W + x] = p ? a : b;
        }
    }

    private static char hex(int i) { return Character.forDigit(i, 16); }

    private void push() {
        undo.push(new String(px));
        while (undo.size() > 50) undo.removeLast();
    }

    private void pop() {
        if (!undo.isEmpty()) { String s = undo.pop(); s.getChars(0, s.length(), px, 0); }
    }

    private void save() {
        PacketDistributor.sendToServer(new ModNetwork.SaveFlag(new String(px)));
        onClose();
    }

    private int cellAt(double mx, double my) {
        int cx = (int) Math.floor((mx - gx) / CELL), cy = (int) Math.floor((my - gy) / CELL);
        if (cx < 0 || cy < 0 || cx >= FlagDesign.W || cy >= FlagDesign.H) return -1;
        return cy * FlagDesign.W + cx;
    }

    private void paint(int cell, int colour) {
        char c = hex(colour);
        set(cell, c);
        if (mirror) set((cell / FlagDesign.W) * FlagDesign.W + (FlagDesign.W - 1 - cell % FlagDesign.W), c);
    }

    private void set(int cell, char c) {
        if (!bucket) { px[cell] = c; return; }
        char from = px[cell];
        if (from == c) return;
        Deque<Integer> q = new ArrayDeque<>();
        q.push(cell);
        while (!q.isEmpty()) {
            int i = q.pop();
            if (px[i] != from) continue;
            px[i] = c;
            int x = i % FlagDesign.W, y = i / FlagDesign.W;
            if (x > 0) q.push(i - 1);
            if (x < FlagDesign.W - 1) q.push(i + 1);
            if (y > 0) q.push(i - FlagDesign.W);
            if (y < FlagDesign.H - 1) q.push(i + FlagDesign.W);
        }
    }

    private int paletteAt(double mx, double my) {
        int py = gy + FlagDesign.H * CELL + 10;
        int px0 = this.width / 2 - 8 * 17;
        if (my < py || my >= py + 16) return -1;
        int i = (int) Math.floor((mx - px0) / 17);
        return i >= 0 && i < 16 ? i : -1;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int pal = paletteAt(mx, my);
        if (pal >= 0) { if (button == 1) secondary = pal; else primary = pal; return true; }
        int cell = cellAt(mx, my);
        if (cell >= 0) {
            if (button == 2) { primary = Character.digit(px[cell], 16); return true; }
            push();
            paint(cell, button == 1 ? secondary : primary);
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        int cell = cellAt(mx, my);
        if (cell >= 0 && !bucket && button <= 1) { paint(cell, button == 1 ? secondary : primary); return true; }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == 90 && (mods & 2) != 0) { pop(); return true; } // ctrl+z
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        super.renderBackground(g, mx, my, pt);
        int w = FlagDesign.W * CELL, h = FlagDesign.H * CELL;
        g.fill(gx - 12, gy - 28, gx + w + 96, gy + h + 72, 0xC0101418);
        g.drawCenteredString(this.font, Component.translatable("screen.frontline.flag_of", nation), gx + w / 2 + 40, gy - 20, 0xFFFFD27A);
        g.fill(gx - 1, gy - 1, gx + w + 1, gy + h + 1, 0xFF000000);
        int hover = cellAt(mx, my);
        for (int i = 0; i < FlagDesign.SIZE; i++) {
            int x = gx + (i % FlagDesign.W) * CELL, y = gy + (i / FlagDesign.W) * CELL;
            g.fill(x, y, x + CELL, y + CELL, 0xFF000000 | FlagDesign.PALETTE[Character.digit(px[i], 16)]);
            if (i == hover) g.renderOutline(x, y, CELL, CELL, 0xFFFFFFFF);
        }
        // palette
        int py = gy + h + 10, p0 = this.width / 2 - 8 * 17;
        for (int i = 0; i < 16; i++) {
            int x = p0 + i * 17;
            g.fill(x, py, x + 16, py + 16, 0xFF000000 | FlagDesign.PALETTE[i]);
            if (i == primary) g.renderOutline(x - 1, py - 1, 18, 18, 0xFFFFFFFF);
            if (i == secondary) g.renderOutline(x + 2, py + 2, 12, 12, 0xFF888888);
        }
        int pal = paletteAt(mx, my);
        Component hint = pal >= 0 ? Component.literal(FlagDesign.NAMES[pal]) : Component.translatable("screen.frontline.painter_hint");
        g.drawCenteredString(this.font, hint, this.width / 2, py + 20, 0xFFAAAAAA);
        // small preview: how it looks on a patch
        int ppx = gx + w + 14, ppy = gy + 108;
        g.drawString(this.font, Component.translatable("screen.frontline.patch_preview"), ppx, ppy, 0xFF8A8F98);
        g.fill(ppx - 1, ppy + 10, ppx + 33, ppy + 31, 0xFF2A2B2E);
        for (int i = 0; i < FlagDesign.SIZE; i++) {
            int x = ppx + (i % FlagDesign.W) * 2, y = ppy + 11 + (i / FlagDesign.W) * 2;
            g.fill(x, y, x + 2, y + 2, 0xFF000000 | FlagDesign.PALETTE[Character.digit(px[i], 16)]);
        }
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
