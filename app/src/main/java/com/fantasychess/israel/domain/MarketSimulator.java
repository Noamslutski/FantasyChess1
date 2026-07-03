package com.fantasychess.israel.domain;

import com.fantasychess.israel.data.model.MarketListing;
import com.fantasychess.israel.data.model.Player;
import com.fantasychess.israel.data.model.Rarity;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Simulated transfer market. There is no game server yet, so other managers
 * are bots with plausible behavior: they list cards around fair value, outbid
 * you sometimes, buy fairly-priced cards you list, and answer trade offers
 * (accept / counter with extra Pawns / reject). The public API is pure, so a
 * real backend can replace this class without touching the UI.
 */
public class MarketSimulator {

    /** Trade-offer verdicts. */
    public enum OfferVerdict { ACCEPT, COUNTER, REJECT }

    public static final double ACCEPT_RATIO = 0.98;
    public static final double COUNTER_RATIO = 0.70;
    public static final double MIN_BID_INCREMENT = 0.05;
    public static final int TARGET_BOT_LISTINGS = 10;

    private static final String[] BOT_MANAGERS = {
            "MagnusFan_IL", "shahmat_pro", "TalForever", "GurionKnight", "PetahRook",
            "kingside_dana", "EloHunter", "sicilian_uri", "hapoel_maniac", "QueenTrap_Noa",
    };

    private final Random random;

    public MarketSimulator(Random random) {
        this.random = random;
    }

    public MarketSimulator() {
        this(new Random());
    }

    /**
     * Tops the market up with bot listings until it has
     * {@link #TARGET_BOT_LISTINGS}. Listed cards are minted immediately (they
     * belong to bot managers), so serial numbers and season caps stay honest.
     */
    public List<MarketListing> topUpListings(List<MarketListing> existing,
                                             List<Player> roster,
                                             MintLedger ledger,
                                             long nowEpochMs) {
        List<MarketListing> listings = new ArrayList<>(existing);
        int bots = 0;
        for (MarketListing listing : listings) {
            if (!listing.mine) bots++;
        }
        while (bots < TARGET_BOT_LISTINGS && !roster.isEmpty()) {
            MarketListing listing = generateListing(roster, ledger, nowEpochMs);
            if (listing == null) break;
            listings.add(listing);
            bots++;
        }
        return listings;
    }

    private MarketListing generateListing(List<Player> roster, MintLedger ledger,
                                          long nowEpochMs) {
        Player player = roster.get(random.nextInt(roster.size()));
        Rarity rarity = rollListingRarity();
        // Respect season caps; if nothing tradable is mintable for this player, skip.
        while (!ledger.canMint(player.id, rarity)) {
            if (rarity == Rarity.UNIQUE) rarity = Rarity.SUPER_RARE;
            else if (rarity == Rarity.SUPER_RARE) rarity = Rarity.RARE;
            else if (rarity == Rarity.RARE) rarity = Rarity.PRO;
            else return null;
        }
        int serial = ledger.mint(player.id, rarity);
        long value = CardValuator.value(player, rarity);
        boolean auction = random.nextInt(100) < 40;
        String seller = BOT_MANAGERS[random.nextInt(BOT_MANAGERS.length)];

        if (auction) {
            long startBid = Math.max(1, Math.round(value * (0.55 + random.nextDouble() * 0.2)));
            long endsAt = nowEpochMs + (2 + random.nextInt(22)) * 3_600_000L;
            return new MarketListing(UUID.randomUUID().toString(), seller, false,
                    player.id, rarity, serial, MarketListing.Type.AUCTION,
                    startBid, endsAt, 0, nowEpochMs);
        }
        long ask = Math.max(1, Math.round(value * (0.85 + random.nextDouble() * 0.45)));
        return new MarketListing(UUID.randomUUID().toString(), seller, false,
                player.id, rarity, serial, MarketListing.Type.DIRECT_SALE,
                ask, 0, 0, nowEpochMs);
    }

    private Rarity rollListingRarity() {
        int roll = random.nextInt(100);
        if (roll < 68) return Rarity.PRO;
        if (roll < 90) return Rarity.RARE;
        if (roll < 98) return Rarity.SUPER_RARE;
        return Rarity.UNIQUE;
    }

    /** Whether a bot outbids the user's active auction bid on this refresh. */
    public boolean botOutbids() {
        return random.nextInt(100) < 30;
    }

    public long outbidAmount(long currentBid) {
        return Math.round(currentBid * (1.05 + random.nextDouble() * 0.15));
    }

    /** Whether a bot buys the user's own listing on this refresh. */
    public boolean botBuysMyListing(long askPrice, long fairValue) {
        if (askPrice <= fairValue) return random.nextInt(100) < 45;
        if (askPrice <= fairValue * 1.15) return random.nextInt(100) < 15;
        return random.nextInt(100) < 3;
    }

    /**
     * Evaluates a trade offer against the listed card's value.
     * At {@link #ACCEPT_RATIO} of value or more: accepted. Between
     * {@link #COUNTER_RATIO} and that: counter-offer asking for the gap in
     * Pawns. Below: rejected.
     */
    public OfferVerdict evaluateOffer(long targetValue, long offeredValue) {
        if (offeredValue >= Math.round(targetValue * ACCEPT_RATIO)) return OfferVerdict.ACCEPT;
        if (offeredValue >= Math.round(targetValue * COUNTER_RATIO)) return OfferVerdict.COUNTER;
        return OfferVerdict.REJECT;
    }

    /** Extra Pawns a counter-offer demands on top of what was offered. */
    public long counterExtraPawns(long targetValue, long offeredValue) {
        long gap = targetValue - offeredValue;
        return Math.max(1, Math.round(gap * (1.0 + random.nextDouble() * 0.1)));
    }

    public long minimumNextBid(long currentBid) {
        return Math.max(currentBid + 1, Math.round(currentBid * (1 + MIN_BID_INCREMENT)));
    }
}
