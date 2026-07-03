package com.fantasychess.israel.data.model;

/**
 * An upcoming, not-yet-played game for a player — the "next games" shown on
 * the player page. Unlike {@link WeekGame} it has no result yet.
 */
public class Fixture {

    public final int playerId;
    public final String dateIso;
    public final String opponentClub;
    public final String league;
    public final boolean home;
    public final int roundNumber;

    public Fixture(int playerId, String dateIso, String opponentClub,
                   String league, boolean home, int roundNumber) {
        this.playerId = playerId;
        this.dateIso = dateIso;
        this.opponentClub = opponentClub;
        this.league = league;
        this.home = home;
        this.roundNumber = roundNumber;
    }
}
