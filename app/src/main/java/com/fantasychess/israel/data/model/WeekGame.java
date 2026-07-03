package com.fantasychess.israel.data.model;

/** A single game a player played during the current fantasy game-week. */
public class WeekGame {

    public final int playerId;
    public final String dateIso;
    public final String opponentName;
    public final String opponentClub;
    public final int opponentRating;
    public final boolean playedWhite;
    public final GameResult result;
    public final String competition;

    public WeekGame(int playerId, String dateIso, String opponentName,
                    String opponentClub, int opponentRating, boolean playedWhite,
                    GameResult result, String competition) {
        this.playerId = playerId;
        this.dateIso = dateIso;
        this.opponentName = opponentName;
        this.opponentClub = opponentClub == null ? "" : opponentClub;
        this.opponentRating = opponentRating;
        this.playedWhite = playedWhite;
        this.result = result;
        this.competition = competition == null ? "" : competition;
    }
}
