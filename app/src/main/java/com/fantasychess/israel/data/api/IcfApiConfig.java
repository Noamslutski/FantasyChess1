package com.fantasychess.israel.data.api;

/**
 * All Israeli Chess Federation (chess.org.il) endpoints in one place.
 *
 * The app tries, in order:
 *  1. the JSON endpoints below,
 *  2. HTML scraping of the classic player-card pages ({@link IcfHtmlParser}),
 *  3. the bundled sample roster in assets/ (so the app always works offline).
 *
 * The federation does not publish official API docs, and page/endpoint paths
 * occasionally change when the site is updated. If live data stops loading,
 * open chess.org.il in Chrome, press F12 -> Network, search for a player, and
 * update the constants below to whatever request the site itself makes —
 * nothing else in the app needs to change.
 */
public final class IcfApiConfig {

    /** New federation site. */
    public static final String BASE_URL = "https://chess.org.il";

    /** Classic site that hosts the per-player card pages (כרטיס שחקן). */
    public static final String LEGACY_BASE_URL = "https://www.chess.org.il";

    /** Hapoel Petah Tikva chess club (מועדון שחמט הפועל פתח תקווה). */
    public static final String CLUB_NAME = "הפועל פתח תקווה";

    /**
     * Club id on the federation site. Find it by opening the club page on
     * chess.org.il and copying the id from the URL, then update this value.
     */
    public static final int CLUB_ID = 89;

    /** Browser-like UA; some federation pages reject unknown clients. */
    public static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/126.0 Mobile Safari/537.36 FantasyChessIL/1.0";

    private IcfApiConfig() {
    }

    /** Candidate JSON endpoint for a club's player list. */
    public static String clubPlayersUrl() {
        return BASE_URL + "/api/players?club_id=" + CLUB_ID;
    }

    /** Candidate JSON endpoint for a player's recent games. */
    public static String playerGamesUrl(int playerId) {
        return BASE_URL + "/api/player/" + playerId + "/games";
    }

    /** Classic HTML player card (כרטיס שחקן) — scraped as a fallback. */
    public static String playerCardUrl(int playerId) {
        return LEGACY_BASE_URL + "/Players/Player.aspx?Id=" + playerId;
    }

    /** Classic HTML club page — scraped as a fallback for the roster. */
    public static String clubPageUrl() {
        return LEGACY_BASE_URL + "/Clubs/Club.aspx?Id=" + CLUB_ID;
    }
}
