package com.fantasychess.israel.data.model;

/**
 * A card offered on the transfer market, either by a simulated manager or by
 * the user. Two sale types, like Sorare:
 *
 *  - DIRECT_SALE: fixed price, instant buy (or send a trade offer)
 *  - AUCTION:     highest bid when the timer ends wins
 */
public class MarketListing {

    public enum Type { DIRECT_SALE, AUCTION }

    public final String id;
    public final String sellerName;
    public final boolean mine;
    public final int playerId;
    public final Rarity rarity;
    public final int serial;
    public final Type type;

    /** Direct sale: the asking price. Auction: the current highest bid. */
    public final long price;

    /** Auction only: when the hammer falls (epoch ms). 0 for direct sales. */
    public final long endsAtEpochMs;

    /** Auction only: my active bid (0 = I have no bid). */
    public final long myBid;

    public final long createdAtEpochMs;

    public MarketListing(String id, String sellerName, boolean mine, int playerId,
                         Rarity rarity, int serial, Type type, long price,
                         long endsAtEpochMs, long myBid, long createdAtEpochMs) {
        this.id = id;
        this.sellerName = sellerName;
        this.mine = mine;
        this.playerId = playerId;
        this.rarity = rarity;
        this.serial = serial;
        this.type = type;
        this.price = price;
        this.endsAtEpochMs = endsAtEpochMs;
        this.myBid = myBid;
        this.createdAtEpochMs = createdAtEpochMs;
    }

    public MarketListing withBid(long newPrice, long newMyBid) {
        return new MarketListing(id, sellerName, mine, playerId, rarity, serial,
                type, newPrice, endsAtEpochMs, newMyBid, createdAtEpochMs);
    }
}
