package com.fantasychess.israel.data.model;

/** A collectible card owned by the user. You can only field a player you own. */
public class OwnedCard {

    public final String cardId;
    public final int playerId;
    public final Rarity rarity;
    public final int serial;
    public final long acquiredAtEpochMs;

    public OwnedCard(String cardId, int playerId, Rarity rarity, int serial,
                     long acquiredAtEpochMs) {
        this.cardId = cardId;
        this.playerId = playerId;
        this.rarity = rarity;
        this.serial = serial;
        this.acquiredAtEpochMs = acquiredAtEpochMs;
    }
}
