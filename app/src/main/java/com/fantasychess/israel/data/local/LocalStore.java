package com.fantasychess.israel.data.local;

import android.content.Context;
import android.content.SharedPreferences;

import com.fantasychess.israel.data.model.MarketListing;
import com.fantasychess.israel.data.model.OwnedCard;
import com.fantasychess.israel.data.model.Squad;
import com.fantasychess.israel.data.model.TradeOffer;
import com.fantasychess.israel.data.model.UserProfile;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Persists the whole game state in SharedPreferences as JSON: collection,
 * squad, packs, Pawns balance, mint ledger, market listings, trade offers,
 * ad counters and the local account. One club's worth of data, so this is
 * simple, dependency-free and more than fast enough.
 */
public class LocalStore {

    private static final String PREFS_NAME = "fantasy_chess_store";
    private static final String KEY_OWNED_CARDS = "owned_cards";
    private static final String KEY_SQUAD_SLOTS = "squad_slots";
    private static final String KEY_CAPTAIN_SLOT = "captain_slot";
    private static final String KEY_FREE_PACKS = "free_packs";
    private static final String KEY_PRO_PACKS = "pro_packs";
    private static final String KEY_LAST_GRANT_WEEK = "last_grant_week";
    private static final String KEY_INITIALIZED = "initialized";
    private static final String KEY_PAWNS = "pawns";
    private static final String KEY_MINTS = "mint_counts";
    private static final String KEY_LISTINGS = "market_listings";
    private static final String KEY_OFFERS = "trade_offers";
    private static final String KEY_AD_DAY = "ad_epoch_day";
    private static final String KEY_AD_COUNT = "ad_count_today";
    private static final String KEY_PROFILE = "user_profile";

    private final SharedPreferences prefs;
    private final Gson gson = new Gson();

    public LocalStore(Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static class Snapshot {
        public List<OwnedCard> ownedCards = new ArrayList<>();
        public Squad squad = new Squad();
        public int freePacks;
        public int proPacks;
        public int lastGrantWeek;
        public boolean initialized;
        public long pawns;
        public Map<String, Integer> mintCounts = new HashMap<>();
        public List<MarketListing> listings = new ArrayList<>();
        public List<TradeOffer> offers = new ArrayList<>();
        public long adEpochDay;
        public int adCountToday;
    }

    public Snapshot load() {
        Snapshot s = new Snapshot();
        s.ownedCards = readList(KEY_OWNED_CARDS, new TypeToken<List<OwnedCard>>() { });
        List<String> slots = readList(KEY_SQUAD_SLOTS, new TypeToken<List<String>>() { });
        s.squad = new Squad(slots, prefs.getInt(KEY_CAPTAIN_SLOT, 0));
        s.freePacks = prefs.getInt(KEY_FREE_PACKS, 0);
        s.proPacks = prefs.getInt(KEY_PRO_PACKS, 0);
        s.lastGrantWeek = prefs.getInt(KEY_LAST_GRANT_WEEK, 0);
        s.initialized = prefs.getBoolean(KEY_INITIALIZED, false);
        s.pawns = prefs.getLong(KEY_PAWNS, 0);
        Map<String, Integer> mints = readJson(KEY_MINTS,
                new TypeToken<Map<String, Integer>>() { });
        if (mints != null) s.mintCounts = mints;
        s.listings = readList(KEY_LISTINGS, new TypeToken<List<MarketListing>>() { });
        s.offers = readList(KEY_OFFERS, new TypeToken<List<TradeOffer>>() { });
        s.adEpochDay = prefs.getLong(KEY_AD_DAY, 0);
        s.adCountToday = prefs.getInt(KEY_AD_COUNT, 0);
        return s;
    }

    public void save(Snapshot s) {
        prefs.edit()
                .putString(KEY_OWNED_CARDS, gson.toJson(s.ownedCards))
                .putString(KEY_SQUAD_SLOTS, gson.toJson(s.squad.slots))
                .putInt(KEY_CAPTAIN_SLOT, s.squad.captainSlot)
                .putInt(KEY_FREE_PACKS, s.freePacks)
                .putInt(KEY_PRO_PACKS, s.proPacks)
                .putInt(KEY_LAST_GRANT_WEEK, s.lastGrantWeek)
                .putBoolean(KEY_INITIALIZED, s.initialized)
                .putLong(KEY_PAWNS, s.pawns)
                .putString(KEY_MINTS, gson.toJson(s.mintCounts))
                .putString(KEY_LISTINGS, gson.toJson(s.listings))
                .putString(KEY_OFFERS, gson.toJson(s.offers))
                .putLong(KEY_AD_DAY, s.adEpochDay)
                .putInt(KEY_AD_COUNT, s.adCountToday)
                .apply();
    }

    // ---- account -----------------------------------------------------------

    public UserProfile loadProfile() {
        String json = prefs.getString(KEY_PROFILE, null);
        if (json == null) return null;
        try {
            return gson.fromJson(json, UserProfile.class);
        } catch (RuntimeException e) {
            return null;
        }
    }

    public void saveProfile(UserProfile profile) {
        if (profile == null) {
            prefs.edit().remove(KEY_PROFILE).apply();
        } else {
            prefs.edit().putString(KEY_PROFILE, gson.toJson(profile)).apply();
        }
    }

    // ---- helpers -----------------------------------------------------------

    private <T> List<T> readList(String key, TypeToken<List<T>> typeToken) {
        List<T> list = readJson(key, typeToken);
        return list == null ? new ArrayList<>() : list;
    }

    private <T> T readJson(String key, TypeToken<T> typeToken) {
        String json = prefs.getString(key, null);
        if (json == null) return null;
        try {
            Type type = typeToken.getType();
            return gson.fromJson(json, type);
        } catch (RuntimeException e) {
            return null; // corrupt entry — fall back to defaults
        }
    }
}
