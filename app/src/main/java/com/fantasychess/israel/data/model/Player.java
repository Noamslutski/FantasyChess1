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
    public final Integer fideId;     // nullable
    public final String title;       // nullable, e.g. "IM", "WFM"
    public final String club;
    public final String team;        // nullable
    public final String league;      // nullable

    /** Traditional 7-arg constructor for simple cases and tests. */
    public Player(int id, String name, Gender gender, int nationalRating,
                  Integer fideRating, String title, String club) {
        this(id, name, gender, nationalRating, fideRating, null, title, club, null, null);
    }

    /** 8-arg constructor used by the HTML parser fallback. */
    public Player(int id, String name, Gender gender, int nationalRating,
                  Integer fideRating, Integer fideId, String title, String club) {
        this(id, name, gender, nationalRating, fideRating, fideId, title, club, null, null);
    }

    /** 9-arg constructor used by the JSON API client and Sample DataSource. */
    public Player(int id, String name, Gender gender, int nationalRating,
                  Integer fideRating, String title, String club, String team, String league) {
        this(id, name, gender, nationalRating, fideRating, null, title, club, team, league);
    }

    public Player(int id, String name, Gender gender, int nationalRating,
                  Integer fideRating, Integer fideId, String title, String club,
                  String team, String league) {
        this.id = id;
        this.name = name;
        this.gender = gender == null ? Gender.BOY : gender;
        this.nationalRating = nationalRating;
        this.fideRating = fideRating;
        this.fideId = fideId;
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
