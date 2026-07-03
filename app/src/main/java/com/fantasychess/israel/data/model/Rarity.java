package com.fantasychess.israel.data.model;

/**
 * Card scarcity tiers, inspired by Sorare (Limited / Rare / Super Rare /
 * Unique). Rarer cards earn a score bonus each game-week.
 */
public enum Rarity {
    LIMITED(0.0),
    RARE(0.05),
    SUPER_RARE(0.10),
    UNIQUE(0.20);

    public final double scoreBonus;

    Rarity(double scoreBonus) {
        this.scoreBonus = scoreBonus;
    }
}
