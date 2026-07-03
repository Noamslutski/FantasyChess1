package com.fantasychess.israel.data.sample;

import android.content.Context;

import com.fantasychess.israel.data.model.Gender;
import com.fantasychess.israel.data.model.Player;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads the bundled Hapoel Petah Tikva roster from assets. Used on first
 * launch and whenever the live federation API is unreachable, so the app
 * always has data to show.
 */
public class SampleDataSource {

    public static final String ASSET_NAME = "hapoel_pt_players.json";

    private final Context context;

    public SampleDataSource(Context context) {
        this.context = context.getApplicationContext();
    }

    public List<Player> loadRoster() {
        String raw = readAsset();
        JsonObject root = JsonParser.parseString(raw).getAsJsonObject();
        String club = root.get("club").getAsString();
        JsonArray items = root.getAsJsonArray("players");

        List<Player> players = new ArrayList<>();
        for (JsonElement el : items) {
            JsonObject obj = el.getAsJsonObject();
            players.add(new Player(
                    obj.get("id").getAsInt(),
                    obj.get("name").getAsString(),
                    Gender.valueOf(obj.get("gender").getAsString()),
                    obj.get("nationalRating").getAsInt(),
                    obj.has("fideRating") ? obj.get("fideRating").getAsInt() : null,
                    obj.has("title") ? obj.get("title").getAsString() : null,
                    club,
                    obj.has("team") ? obj.get("team").getAsString() : null,
                    obj.has("league") ? obj.get("league").getAsString() : null));
        }
        return players;
    }

    private String readAsset() {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                context.getAssets().open(ASSET_NAME), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
        } catch (IOException e) {
            throw new IllegalStateException("Bundled roster asset is missing", e);
        }
        return sb.toString();
    }
}
