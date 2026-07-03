package com.fantasychess.israel.data.local;

import android.content.Context;
import android.content.SharedPreferences;

import com.fantasychess.israel.data.model.OwnedCard;
import com.fantasychess.israel.data.model.Squad;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * Persists the user's collection, squad and pack inventory in
 * SharedPreferences as JSON. The data set is one club's worth of cards, so
 * this is simple, dependency-free and more than fast enough.
 */
public class LocalStore {

    private static final String PREFS_NAME = "fantasy_chess_store";
    private static final String KEY_OWNED_CARDS = "owned_cards";
    private static final String KEY_SQUAD_SLOTS = "squad_slots";
    private static final String KEY_CAPTAIN_SLOT = "captain_slot";
    private static final String KEY_PACKS = "packs_available";
    private static final String KEY_LAST_GRANT_WEEK = "last_grant_week";
    private static final String KEY_INITIALIZED = "initialized";

    private final SharedPreferences prefs;
    private final Gson gson = new Gson();

    public LocalStore(Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static class Snapshot {
        public final List<OwnedCard> ownedCards;
        public final Squad squad;
        public final int packsAvailable;
        public final int lastGrantWeek;
        public final boolean initialized;

        public Snapshot(List<OwnedCard> ownedCards, Squad squad, int packsAvailable,
                        int lastGrantWeek, boolean initialized) {
            this.ownedCards = ownedCards;
            this.squad = squad;
            this.packsAvailable = packsAvailable;
            this.lastGrantWeek = lastGrantWeek;
            this.initialized = initialized;
        }
    }

    public Snapshot load() {
        List<OwnedCard> cards = new ArrayList<>();
        String cardsJson = prefs.getString(KEY_OWNED_CARDS, null);
        if (cardsJson != null) {
            try {
                Type type = new TypeToken<List<OwnedCard>>() { }.getType();
                List<OwnedCard> parsed = gson.fromJson(cardsJson, type);
                if (parsed != null) cards = parsed;
            } catch (RuntimeException ignored) {
                // Corrupt store — start with an empty collection.
            }
        }

        Squad squad = new Squad();
        String slotsJson = prefs.getString(KEY_SQUAD_SLOTS, null);
        if (slotsJson != null) {
            try {
                Type type = new TypeToken<List<String>>() { }.getType();
                List<String> slots = gson.fromJson(slotsJson, type);
                squad = new Squad(slots, prefs.getInt(KEY_CAPTAIN_SLOT, 0));
            } catch (RuntimeException ignored) {
                // Corrupt store — start with an empty squad.
            }
        }

        return new Snapshot(
                cards,
                squad,
                prefs.getInt(KEY_PACKS, 0),
                prefs.getInt(KEY_LAST_GRANT_WEEK, 0),
                prefs.getBoolean(KEY_INITIALIZED, false));
    }

    public void save(Snapshot snapshot) {
        prefs.edit()
                .putString(KEY_OWNED_CARDS, gson.toJson(snapshot.ownedCards))
                .putString(KEY_SQUAD_SLOTS, gson.toJson(snapshot.squad.slots))
                .putInt(KEY_CAPTAIN_SLOT, snapshot.squad.captainSlot)
                .putInt(KEY_PACKS, snapshot.packsAvailable)
                .putInt(KEY_LAST_GRANT_WEEK, snapshot.lastGrantWeek)
                .putBoolean(KEY_INITIALIZED, snapshot.initialized)
                .apply();
    }
}
