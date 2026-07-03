package com.fantasychess.israel.data.api;

import com.fantasychess.israel.data.model.Player;
import com.fantasychess.israel.data.model.WeekGame;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

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
        List<Player> players = fetchClubPlayersJson();
        if (players == null) players = fetchClubPlayersHtml();
        return (players == null || players.isEmpty()) ? null : players;
    }

    /** Games in the given date window, or null when unavailable. */
    public List<WeekGame> fetchPlayerGames(int playerId, String fromIsoDate, String toIsoDate) {
        List<WeekGame> games = fetchPlayerGamesJson(playerId, fromIsoDate, toIsoDate);
        if (games == null) games = fetchPlayerGamesHtml(playerId, fromIsoDate, toIsoDate);
        return games;
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
                        firstString(obj, "title"),
                        IcfApiConfig.CLUB_NAME));
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
                if (date == null || opponent == null || opponentRating == null || result == null) {
                    continue;
                }
                if (date.compareTo(fromIsoDate) < 0 || date.compareTo(toIsoDate) > 0) continue;
                com.fantasychess.israel.data.model.GameResult parsed =
                        IcfHtmlParser.parseResult(result);
                if (parsed == null) continue;
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

    private List<WeekGame> fetchPlayerGamesHtml(int playerId, String fromIsoDate, String toIsoDate) {
        String html = get(IcfApiConfig.playerCardUrl(playerId));
        if (html == null) return null;
        try {
            return IcfHtmlParser.parsePlayerGames(html, playerId, fromIsoDate, toIsoDate);
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
