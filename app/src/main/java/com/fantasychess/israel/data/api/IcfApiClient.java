package com.fantasychess.israel.data.api;

import com.fantasychess.israel.data.model.Player;
import com.fantasychess.israel.data.model.WeekGame;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Fetches live data from the Israeli Chess Federation site.
 *
 * Every call returns null instead of throwing when the site is unreachable or
 * the payload shape is unexpected — the repository then falls back to the
 * HTML parser and finally to the bundled sample data, so the UI never breaks.
 * All methods are blocking; the repository calls them from a background thread.
 */
public class IcfApiClient {

    private final OkHttpClient http;

    public IcfApiClient() {
        this.http = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build();
    }

    /** Roster of the configured club, or null when live data is unavailable. */
    public List<Player> fetchClubPlayers() {
        List<Player> json = fetchClubPlayersJson();
        return json != null ? json : fetchClubPlayersHtml();
    }

    /** Roster of all players in Israel (top players from the rankings page). */
    public List<Player> fetchAllPlayers() {
        String html = get(IcfApiConfig.allPlayersUrl());
        if (html == null) return null;
        try {
            List<Player> players = IcfHtmlParser.parseClubRoster(html);
            return players.isEmpty() ? null : players;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * Searches for any player by name prefix. Uses the federation's autocomplete API.
     * Returns list of "Player Name [ID]".
     */
    public List<String> searchPlayers(String query) {
        String url = IcfApiConfig.BASE_URL + "/WebService.asmx/GetCompletionList";
        String json = "{\"prefixText\":\"" + query + "\", \"count\":20}";
        
        okhttp3.RequestBody body = okhttp3.RequestBody.create(
                json, okhttp3.MediaType.get("application/json; charset=utf-8"));
        
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .header("User-Agent", IcfApiConfig.USER_AGENT)
                .build();
                
        try (Response response = http.newCall(request).execute()) {
            if (!response.isSuccessful()) return null;
            String respBody = response.body() != null ? response.body().string() : "";
            JsonObject root = JsonParser.parseString(respBody).getAsJsonObject();
            JsonArray d = root.getAsJsonArray("d");
            List<String> results = new ArrayList<>();
            if (d != null) {
                for (JsonElement el : d) results.add(el.getAsString());
            }
            return results;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** Details of a specific player, or null when unavailable. */
    public Player fetchPlayerDetails(int playerId) {
        String html = get(IcfApiConfig.playerCardUrl(playerId));
        if (html == null) return null;
        try {
            return IcfHtmlParser.parsePlayerDetails(html, playerId);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Games in the given date window, or null when unavailable. */
    public List<WeekGame> fetchPlayerGames(int playerId, String fromIsoDate, String toIsoDate) {
        List<WeekGame> games = fetchPlayerGamesJson(playerId, fromIsoDate, toIsoDate);
        if (games == null) games = fetchPlayerGamesHtml(playerId);
        
        if (games == null) return null;
        
        // Filter by date window if provided
        if (fromIsoDate == null && toIsoDate == null) return games;
        
        List<WeekGame> filtered = new ArrayList<>();
        for (WeekGame g : games) {
            if (fromIsoDate != null && g.dateIso.compareTo(fromIsoDate) < 0) continue;
            if (toIsoDate != null && g.dateIso.compareTo(toIsoDate) > 0) continue;
            filtered.add(g);
        }
        return filtered;
    }

    /** Fetches pairings from chess-results.com for a given FIDE ID. */
    public List<WeekGame> fetchPairingsFromChessResults(int fideId, int playerId) {
        String url = "https://chess-results.com/fide.aspx?id=" + fideId + "&lan=1";
        String html = get(url);
        if (html == null) return null;
        try {
            // Very simple parser for chess-results: find rows that look like pairings
            Document doc = org.jsoup.Jsoup.parse(html);
            List<WeekGame> games = new ArrayList<>();
            for (org.jsoup.nodes.Element row : doc.select("table.CRtable tr")) {
                org.jsoup.select.Elements tds = row.select("td");
                if (tds.size() >= 5) {
                    String tournament = tds.get(1).text();
                    String result = tds.get(tds.size() - 2).text();
                    if (result.contains("+") || result.contains("-") || result.contains("=")) {
                        // Likely a tournament summary or recent game
                        String date = IcfHtmlParser.parseIsoDate(tds.get(0).text());
                        if (date != null) {
                            games.add(new WeekGame(playerId, date, tournament, "", 0,
                                    true, IcfHtmlParser.parseResult(result), "Chess-Results"));
                        }
                    }
                }
            }
            return games;
        } catch (RuntimeException e) {
            return null;
        }
    }

    // ---- JSON attempts -----------------------------------------------------

    private List<Player> fetchClubPlayersJson() {
        try {
            String body = get(IcfApiConfig.clubPlayersUrl());
            if (body == null) return null;
            JsonArray items = asArray(JsonParser.parseString(body), "players", "data");
            if (items == null) return null;
            List<Player> players = new ArrayList<>();
            for (JsonElement el : items) {
                JsonObject obj = el.getAsJsonObject();
                Integer id = firstInt(obj, "id", "player_id", "Id");
                String name = firstString(obj, "name", "full_name", "Name");
                Integer rating = firstInt(obj, "rating", "national_rating", "Rating");
                if (id == null || name == null || rating == null) continue;
                players.add(new Player(id, name,
                        IcfHtmlParser.guessGender(firstString(obj, "gender", "sex")),
                        rating,
                        firstInt(obj, "fide_rating", "fide"),
                         null,
                        firstString(obj, "title"),
                        IcfApiConfig.CLUB_NAME,
                        firstString(obj, "team", "squad"),
                        firstString(obj, "league")));
            }
            return players.isEmpty() ? null : players;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private List<WeekGame> fetchPlayerGamesJson(int playerId, String fromIsoDate, String toIsoDate) {
        try {
            String body = get(IcfApiConfig.playerGamesUrl(playerId));
            if (body == null) return null;
            JsonArray items = asArray(JsonParser.parseString(body), "games", "data");
            if (items == null) return null;
            List<WeekGame> games = new ArrayList<>();
            for (JsonElement el : items) {
                JsonObject obj = el.getAsJsonObject();
                String date = firstString(obj, "date", "game_date");
                String opponent = firstString(obj, "opponent", "opponent_name");
                Integer opponentRating = firstInt(obj, "opponent_rating");
                String result = firstString(obj, "result");
                if (date == null || opponent == null || opponentRating == null) {
                    continue;
                }
                if (date.compareTo(fromIsoDate) < 0 || date.compareTo(toIsoDate) > 0) continue;
                
                com.fantasychess.israel.data.model.GameResult parsed =
                        result != null ? IcfHtmlParser.parseResult(result) : com.fantasychess.israel.data.model.GameResult.UPCOMING;
                
                if (parsed == null) parsed = com.fantasychess.israel.data.model.GameResult.UPCOMING;
                String color = firstString(obj, "color");
                games.add(new WeekGame(playerId, date, opponent,
                        firstString(obj, "opponent_club"),
                        opponentRating,
                        color == null || color.toLowerCase().startsWith("w"),
                        parsed,
                        firstString(obj, "competition", "tournament")));
            }
            return games;
        } catch (RuntimeException e) {
            return null;
        }
    }

    // ---- HTML fallbacks ----------------------------------------------------

    private List<Player> fetchClubPlayersHtml() {
        String html = get(IcfApiConfig.clubPageUrl());
        if (html == null) return null;
        try {
            List<Player> players = IcfHtmlParser.parseClubRoster(html);
            return players.isEmpty() ? null : players;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private List<WeekGame> fetchPlayerGamesHtml(int playerId) {
        String html = get(IcfApiConfig.playerCardUrl(playerId));
        if (html == null) return null;
        try {
            return IcfHtmlParser.parsePlayerGames(html, playerId);
        } catch (RuntimeException e) {
            return null;
        }
    }

    // ---- plumbing ----------------------------------------------------------

    private String get(String url) {
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", IcfApiConfig.USER_AGENT)
                .header("Accept", "application/json, text/html;q=0.9, */*;q=0.8")
                .build();
        try (Response response = http.newCall(request).execute()) {
            if (!response.isSuccessful()) return null;
            ResponseBody body = response.body();
            return body == null ? null : body.string();
        } catch (IOException e) {
            return null;
        }
    }

    private static JsonArray asArray(JsonElement root, String... wrapperKeys) {
        if (root.isJsonArray()) return root.getAsJsonArray();
        if (root.isJsonObject()) {
            JsonObject obj = root.getAsJsonObject();
            for (String key : wrapperKeys) {
                JsonElement inner = obj.get(key);
                if (inner != null && inner.isJsonArray()) return inner.getAsJsonArray();
            }
        }
        return null;
    }

    private static String firstString(JsonObject obj, String... keys) {
        for (String key : keys) {
            JsonElement el = obj.get(key);
            if (el != null && el.isJsonPrimitive()) {
                String value = el.getAsString();
                if (!value.isEmpty()) return value;
            }
        }
        return null;
    }

    private static Integer firstInt(JsonObject obj, String... keys) {
        for (String key : keys) {
            JsonElement el = obj.get(key);
            if (el != null && el.isJsonPrimitive()) {
                try {
                    return Integer.parseInt(el.getAsString().trim());
                } catch (NumberFormatException ignored) {
                    // fall through to the next key
                }
            }
        }
        return null;
    }
}
