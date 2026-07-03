package com.fantasychess.israel.data.model;

import java.util.ArrayList;
import java.util.List;

/**
 * A trade offer the user sent for a listed card: a bundle of own cards plus
 * Pawns. The other manager can accept, reject, or send a counter-offer that
 * asks for extra Pawns on top; the user then accepts or declines the counter.
 */
public class TradeOffer {

    public enum Status { PENDING, ACCEPTED, REJECTED, COUNTERED, COUNTER_ACCEPTED, DECLINED }

    public final String id;
    public final String listingId;
    public final String otherManager;

    /** Snapshot of the card being asked for. */
    public final int targetPlayerId;
    public final Rarity targetRarity;
    public final int targetSerial;

    /** What I put on the table. */
    public final List<String> offeredCardIds;
    public final long offeredPawns;

    public final Status status;

    /** COUNTERED: extra Pawns the other manager wants on top of my offer. */
    public final long counterExtraPawns;

    public final long createdAtEpochMs;

    public TradeOffer(String id, String listingId, String otherManager,
                      int targetPlayerId, Rarity targetRarity, int targetSerial,
                      List<String> offeredCardIds, long offeredPawns,
                      Status status, long counterExtraPawns, long createdAtEpochMs) {
        this.id = id;
        this.listingId = listingId;
        this.otherManager = otherManager;
        this.targetPlayerId = targetPlayerId;
        this.targetRarity = targetRarity;
        this.targetSerial = targetSerial;
        this.offeredCardIds = offeredCardIds == null
                ? new ArrayList<>() : new ArrayList<>(offeredCardIds);
        this.offeredPawns = offeredPawns;
        this.status = status;
        this.counterExtraPawns = counterExtraPawns;
        this.createdAtEpochMs = createdAtEpochMs;
    }

    public TradeOffer withStatus(Status newStatus) {
        return new TradeOffer(id, listingId, otherManager, targetPlayerId, targetRarity,
                targetSerial, offeredCardIds, offeredPawns, newStatus,
                counterExtraPawns, createdAtEpochMs);
    }

    public TradeOffer withCounter(long extraPawns) {
        return new TradeOffer(id, listingId, otherManager, targetPlayerId, targetRarity,
                targetSerial, offeredCardIds, offeredPawns, Status.COUNTERED,
                extraPawns, createdAtEpochMs);
    }
}
