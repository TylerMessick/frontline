package com.messickmedia.frontline.nation;

/**
 * A painted flag: 16 x 10 pixels, each one of the 16 dye colours. Stored as a 160-character string of hex digits
 * (row by row, top-left first) so it travels easily through saves, packets and item components. Empty = no design
 * (the flag falls back to the plain nation colour).
 */
public final class FlagDesign {
    private FlagDesign() {}

    public static final int W = 16, H = 10, SIZE = W * H;

    /** Dye colours in hex-digit order 0-f: white, orange, magenta, light blue, yellow, lime, pink, gray, light gray, cyan, purple, blue, brown, green, red, black. */
    public static final int[] PALETTE = {
            0xF9FFFE, 0xF9801D, 0xC74EBD, 0x3AB3DA, 0xFED83D, 0x80C71F, 0xF38BAA, 0x474F52,
            0x9D9D97, 0x169C9C, 0x8932B8, 0x3C44AA, 0x835432, 0x5E7C16, 0xB02E26, 0x1D1D21};
    public static final String[] NAMES = {"White", "Orange", "Magenta", "Light Blue", "Yellow", "Lime", "Pink", "Gray",
            "Light Gray", "Cyan", "Purple", "Blue", "Brown", "Green", "Red", "Black"};

    public static boolean valid(String d) {
        if (d == null || d.length() != SIZE) return false;
        for (int i = 0; i < SIZE; i++) if (Character.digit(d.charAt(i), 16) < 0) return false;
        return true;
    }

    public static boolean present(String d) { return valid(d); }

    public static int index(String d, int x, int y) { return Character.digit(d.charAt(y * W + x), 16); }

    public static int rgb(String d, int x, int y) { return PALETTE[index(d, x, y)]; }

    public static String filled(int idx) { return String.valueOf(Character.forDigit(idx, 16)).repeat(SIZE); }

    /** The palette entry closest to an arbitrary colour (used to seed a design from the nation's banner colour). */
    public static int nearest(int rgb) {
        int best = 0; long bd = Long.MAX_VALUE;
        for (int i = 0; i < PALETTE.length; i++) {
            int p = PALETTE[i];
            long dr = ((p >> 16) & 255) - ((rgb >> 16) & 255), dg = ((p >> 8) & 255) - ((rgb >> 8) & 255), db = (p & 255) - (rgb & 255);
            long d = dr * dr * 3 + dg * dg * 4 + db * db * 2;
            if (d < bd) { bd = d; best = i; }
        }
        return best;
    }

    /** A starter design: the nation colour with a white band across the middle. */
    public static String starter(int nationRgb) {
        char c = Character.forDigit(nearest(nationRgb), 16);
        StringBuilder b = new StringBuilder(SIZE);
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) b.append(y >= 4 && y <= 5 ? '0' : c);
        return b.toString();
    }
}
