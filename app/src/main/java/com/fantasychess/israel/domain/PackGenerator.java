package com.fantasychess.israel.domain;

import com.fantasychess.israel.data.model.OwnedCard;
import com.fantasychess.israel.data.model.Player;
import com.fantasychess.israel.data.model.Rarity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Opens card packs. Every pack contains {@link #CARDS_PER_PACK} random cards
 * from the club roster with Sorare-like scarcity odds:
 * Limited 70%, Rare 20%, Super Rare 8%, Unique 2%.
 */
public class PackGenerator {

    public static final int CARDS_PER_PACK = 3;
    public static final int STARTER_PACKS = 3;
    public static final int WEEKLY_FREE_PACKS = 1;

    public interface SerialProvider {
        int nextSerial(int playerId);
    }

    private final Random random;

    public PackGenerator() {
        this(new Random());
    }

    public PackGenerator(Random random) {
        this.random = random;
    }

    public List<OwnedCard> openPack(List<Player> roster, SerialProvider serials, long nowEpochMs) {
        if (roster == null || roster.isEmpty()) {
            throw new IllegalArgumentException("Cannot open a pack with an empty roster");
        }
        List<Player> pool = new ArrayList<>(roster);
        Collections.shuffle(pool, random);
        List<Player> picks = pool.subList(0, Math.min(CARDS_PER_PACK, pool.size()));

        List<OwnedCard> cards = new ArrayList<>(picks.size());
        for (Player player : picks) {
            cards.add(new OwnedCard(
                    UUID.randomUUID().toString(),
                    player.id,
                    rollRarity(),
                    serials.nextSerial(player.id),
                    nowEpochMs));
        }
        return cards;
    }

    private Rarity rollRarity() {
        int roll = random.nextInt(100);
        if (roll < 70) return Rarity.LIMITED;
        if (roll < 90) return Rarity.RARE;
        if (roll < 98) return Rarity.SUPER_RARE;
        return Rarity.UNIQUE;
    }
}
