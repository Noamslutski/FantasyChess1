package com.fantasychess.israel.data.model;

import com.fantasychess.israel.domain.RatingMapper;

/**
 * A chess player registered with the Israeli Chess Federation
 * (איגוד השחמט הישראלי). {@link #id} is the federation player id as used on
 * chess.org.il, so the live player-card page is always
 * {@code IcfApiConfig.playerCardUrl(id)}.
 */
public class Player {

    public final int id;
    public final String name;
    public final Gender gender;
    public final int nationalRating;
    public final Integer fideRating; // nullable
    public final String title;       // nullable, e.g. "IM", "WFM"
    public final String club;
    public final String team;        // nullable, e.g. "הפועל פ\"ת ב'"
    public final String league;      // nullable, e.g. "ליגה לאומית"

    public Player(int id, String name, Gender gender, int nationalRating,
                  Integer fideRating, String title, String club) {
        this(id, name, gender, nationalRating, fideRating, title, club, null, null);
    }

    public Player(int id, String name, Gender gender, int nationalRating,
                  Integer fideRating, String title, String club,
                  String team, String league) {
        this.id = id;
        this.name = name;
        this.gender = gender == null ? Gender.BOY : gender;
        this.nationalRating = nationalRating;
        this.fideRating = fideRating;
        this.title = title;
        this.club = club;
        this.team = team;
        this.league = league;
    }

    /** Fantasy card rating on a 1-100 scale, derived from the national rating. */
    public int cardRating() {
        return RatingMapper.toCardRating(nationalRating);
    }

    /** Display name with the chess title prefix when the player has one. */
    public String displayName() {
        return (title == null || title.isEmpty()) ? name : title + " " + name;
    }
}
