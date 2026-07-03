package com.fantasychess.israel.data.model;

/**
 * Card scarcity ladder with hard per-season mint limits per player:
 *
 *  - COMMON (gray):      unlimited, comes from the free/ad packs, NOT tradable
 *  - PRO (yellow):       up to 5,000 minted per season — the entry-level
 *                        tokenized cards ("Limited" tier), perfect for starting
 *  - RARE (red):         up to 100 minted per season
 *  - SUPER_RARE (blue):  up to 10 minted per season
 *  - UNIQUE (black):     exactly 1 minted per season — the ultimate collectible
 *
 * Rarer cards earn a score bonus every game-week.
 */
public enum Rarity {
    COMMON(0.00, 0),
    PRO(0.05, 5000),
    RARE(0.10, 100),
    SUPER_RARE(0.20, 10),
    UNIQUE(0.40, 1);

    public final double scoreBonus;

    /** Max cards minted per player per season; 0 = unlimited. */
    public final int mintLimitPerSeason;

    Rarity(double scoreBonus, int mintLimitPerSeason) {
        this.scoreBonus = scoreBonus;
        this.mintLimitPerSeason = mintLimitPerSeason;
    }

    /** Commons are play-only; every tokenized tier can be sold or traded. */
    public boolean isTradable() {
        return this != COMMON;
    }
}
