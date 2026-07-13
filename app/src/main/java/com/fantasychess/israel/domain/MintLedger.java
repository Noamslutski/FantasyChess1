package com.fantasychess.israel.domain;

import com.fantasychess.israel.data.model.Rarity;

import java.util.HashMap;
import java.util.Map;

/**
 * Tracks how many cards of each (player, rarity) were minted this season and
 * enforces the per-season caps: Pro 5,000 · Rare 100 · Super Rare 10 ·
 * Unique 1. Commons are unlimited. Serial numbers are mint order (1-based).
 */
public class MintLedger {

    private final Map<String, Integer> counts;

    public MintLedger(Map<String, Integer> counts) {
        this.counts = counts == null ? new HashMap<>() : new HashMap<>(counts);
    }

    public Map<String, Integer> snapshot() {
        return new HashMap<>(counts);
    }

    public int minted(int playerId, Rarity rarity) {
        Integer count = counts.get(key(playerId, rarity));
        return count == null ? 0 : count;
    }

    public boolean canMint(int playerId, Rarity rarity) {
        if (rarity == null) return true; // null/common are unlimited
        return rarity.mintLimitPerSeason == 0
                || minted(playerId, rarity) < rarity.mintLimitPerSeason;
    }

    /** Mints one card and returns its serial number (1-based). */
    public int mint(int playerId, Rarity rarity) {
        if (!canMint(playerId, rarity)) {
            throw new IllegalStateException(
                    "Season mint cap reached for player " + playerId + " " + rarity);
        }
        int next = minted(playerId, rarity) + 1;
        counts.put(key(playerId, rarity), next);
        return next;
    }

    private static String key(int playerId, Rarity rarity) {
        return playerId + ":" + (rarity == null ? "COMMON" : rarity.name());
    }
}
