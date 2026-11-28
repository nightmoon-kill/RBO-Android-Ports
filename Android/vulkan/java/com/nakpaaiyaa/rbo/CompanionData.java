package com.nakpaaiyaa.rbo;

final class CompanionData {
    static final int PLAYER_WORDS = 24;
    static final int WORDS = 288;
    // The EX3 filename table starts with Novice, then the six original classes.
    static final String[] ROLES = {"Novice", "Swordman", "Archer", "Magician", "Merchant", "Acolyte", "Thief"};
    static final int[] MOVE_JOBS = {6, 0, 1, 2, 3, 4, 5};
    private final int[] words;

    private CompanionData(int[] data) {
        words = data != null && data.length == WORDS && data[0] == 1 ? data : new int[WORDS];
    }
    static CompanionData read() {
        try { return new CompanionData(readNative()); }
        catch (UnsatisfiedLinkError unavailable) { return new CompanionData(null); }
    }
    static CompanionData empty() { return new CompanionData(null); }
    boolean supported() { return words[1] == 1; }
    boolean playerAvailable() { return words[2] == 1; }
    int player(int field) { return words[8 + field]; }
    boolean monstersAvailable() { return words[3] == 1; }
    int monsterCount() { return words[4]; }
    int speciesCount() { return Math.max(0, Math.min(128, words[5])); }
    int species(int index) { return words[32 + index * 2]; }
    int remaining(int index) { return words[33 + index * 2]; }
    static String role(int code) { return code >= 0 && code < 14 ? ROLES[code / 2] : "Character"; }
    static String portrait(int code) {
        return role(code).toLowerCase(java.util.Locale.ROOT) + ((code & 1) != 0 ? "_female" : "_male");
    }
    private static native int[] readNative();
}
