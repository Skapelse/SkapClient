package com.skapclient;

public enum Theme {
    // order: BG, PANEL, ROW, ACCENT, ACCENT2, TEXT, SUBTEXT
    PURPLE("Dark Purple", 0xFF0F0A1C, 0xFF181129, 0xFF21173A, 0xFF9B5CFF, 0xFFC084FC, 0xFFF1EAFF, 0xFF9D8FBF),
    BLUE("Dark Blue",     0xFF08101F, 0xFF0E1A30, 0xFF152643, 0xFF3D8BFF, 0xFF7DB4FF, 0xFFEAF2FF, 0xFF8FA6C9),
    RED("Dark Red",       0xFF1A0A0C, 0xFF281113, 0xFF3A171A, 0xFFE0313F, 0xFFFF6B75, 0xFFFFEBEC, 0xFFC99093);

    public static final int BG = 0, PANEL = 1, ROW = 2, ACC = 3, ACC2 = 4, TEXT = 5, SUB = 6;
    public final String label;
    public final int[] colors;

    Theme(String label, int... colors) {
        this.label = label;
        this.colors = colors;
    }
}
