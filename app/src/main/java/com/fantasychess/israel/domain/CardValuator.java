package com.fantasychess.israel.domain;

import com.fantasychess.israel.data.model.Player;
import com.fantasychess.israel.data.model.Rarity;

/**
 * Fair-value estimate of a card in Pawns (🨅), used to price bot listings and
 * to evaluate trade offers. Value grows with the 1-100 card rating and jumps
 * with scarcity: Pro x1, Rare x5, Super Rare x20, Unique x100.
 */
public final class CardValuator {

    private CardValuator() {
    }

    public static long value(Player player, Rarity rarity) {
        long base = 2L * player.cardRating();
        switch (rarity) {
            case COMMON: return Math.max(1, base / 4); // play-only, trade math only
            case PRO: return base;
            case RARE: return base * 5;
            case SUPER_RARE: return base * 20;
            case UNIQUE: return base * 100;
            default: return base;
        }
    }
}
