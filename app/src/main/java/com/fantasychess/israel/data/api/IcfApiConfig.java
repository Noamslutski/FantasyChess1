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

    /** Classic site that hosts everything. */
    public static final String BASE_URL = "https://www.chess.org.il";

    /** Classic site that hosts the per-player card pages (כרטיס שחקן). */
    public static final String LEGACY_BASE_URL = "https://www.chess.org.il";

    /** Hapoel Petah Tikva chess club (מועדון השחמט הפועל פתח תקוה). */
    public static final String CLUB_NAME = "הפועל פתח תקווה";

    /**
     * Real club id of מועדון השחמט הפועל פתח תקוה on the federation site.
     */
    public static final int CLUB_ID = 30;

    /** Browser-like UA; some federation pages reject unknown clients. */
    public static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/126.0 Mobile Safari/537.36 FantasyChessIL/1.0";

    private IcfApiConfig() {
    }

    /** No public JSON endpoint for club players; always use HTML. */
    public static String clubPlayersUrl() {
        return clubPageUrl();
    }

    /** No public JSON endpoint for player games; always use HTML. */
    public static String playerGamesUrl(int playerId) {
        return playerCardUrl(playerId);
    }

    /** Classic HTML player card (כרטיס שחקן) — contains games and tournaments. */
    public static String playerCardUrl(int playerId) {
        return BASE_URL + "/players/Player.aspx?Id=" + playerId;
    }

    /** Classic HTML club page. */
    public static String clubPageUrl() {
        return BASE_URL + "/clubs/Club.aspx?Id=" + CLUB_ID;
    }

    /** Players list sorted by rating. */
    public static String allPlayersUrl() {
        return BASE_URL + "/Players/PlayersList.aspx?Ranking=0&Sort=4";
    }
}
