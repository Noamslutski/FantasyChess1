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
 * Opens card packs. Every pack contains {@link #CARDS_PER_PACK} random club
 * players; the rarity odds depend on the pack type:
 *
 *  - FREE (gray pack):   commons only — the starter and weekly packs
 *  - AD (gray pack):     watch an ad — commons with a {@link #AD_PRO_CHANCE}%
 *                        chance per card of a Pro
 *  - PRO (gold pack):    bought with Pawns — Pro 70%, Rare 24%,
 *                        Super Rare 5%, Unique 1%
 *
 * Minting always respects the per-season caps in {@link MintLedger}; when a
 * tier is sold out for a player, the card downgrades to the next tier below.
 */
public class PackGenerator {

    public enum PackType { FREE, AD, PRO }

    public static final int CARDS_PER_PACK = 3;
    public static final int STARTER_PACKS = 3;
    public static final int WEEKLY_FREE_PACKS = 1;
    public static final int AD_PRO_CHANCE = 15;         // percent, per card
    public static final int MAX_ADS_PER_DAY = 5;
    public static final long PRO_PACK_PRICE_PAWNS = 600;

    private final Random random;

    public PackGenerator() {
        this(new Random());
    }

    public PackGenerator(Random random) {
        this.random = random;
    }

    public List<OwnedCard> openPack(PackType type, List<Player> roster,
                                    MintLedger ledger, long nowEpochMs) {
        if (roster == null || roster.isEmpty()) {
            throw new IllegalArgumentException("Cannot open a pack with an empty roster");
        }
        List<Player> pool = new ArrayList<>(roster);
        Collections.shuffle(pool, random);
        List<Player> picks = pool.subList(0, Math.min(CARDS_PER_PACK, pool.size()));

        List<OwnedCard> cards = new ArrayList<>(picks.size());
        for (Player player : picks) {
            Rarity rarity = downgradeUntilMintable(player.id, rollRarity(type), ledger);
            cards.add(new OwnedCard(
                    UUID.randomUUID().toString(),
                    player.id,
                    rarity,
                    ledger.mint(player.id, rarity),
                    nowEpochMs));
        }
        return cards;
    }

    private Rarity rollRarity(PackType type) {
        int roll = random.nextInt(100);
        switch (type) {
            case FREE:
                return Rarity.COMMON;
            case AD:
                return roll < AD_PRO_CHANCE ? Rarity.PRO : Rarity.COMMON;
            case PRO:
            default:
                if (roll < 70) return Rarity.PRO;
                if (roll < 94) return Rarity.RARE;
                if (roll < 99) return Rarity.SUPER_RARE;
                return Rarity.UNIQUE;
        }
    }

    /** Falls back tier by tier when the season cap for this player is reached. */
    private static Rarity downgradeUntilMintable(int playerId, Rarity wanted, MintLedger ledger) {
        Rarity rarity = wanted;
        while (!ledger.canMint(playerId, rarity)) {
            switch (rarity) {
                case UNIQUE: rarity = Rarity.SUPER_RARE; break;
                case SUPER_RARE: rarity = Rarity.RARE; break;
                case RARE: rarity = Rarity.PRO; break;
                default: return Rarity.COMMON; // commons are unlimited
            }
        }
        return rarity;
    }
}
