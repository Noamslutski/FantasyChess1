package com.fantasychess.israel.data.repo;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.fantasychess.israel.data.api.IcfApiClient;
import com.fantasychess.israel.data.local.LocalStore;
import com.fantasychess.israel.data.model.DataSource;
import com.fantasychess.israel.data.model.OwnedCard;
import com.fantasychess.israel.data.model.Player;
import com.fantasychess.israel.data.model.Squad;
import com.fantasychess.israel.data.model.WeekGame;
import com.fantasychess.israel.data.sample.SampleDataSource;
import com.fantasychess.israel.data.sample.SampleWeekGenerator;
import com.fantasychess.israel.domain.FantasyScoring;
import com.fantasychess.israel.domain.GameWeek;
import com.fantasychess.israel.domain.PackGenerator;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Single source of truth for the whole game. Loads the roster (live
 * federation data when possible, bundled sample otherwise), the user's
 * collection/squad/packs, and this week's games per player.
 *
 * All mutations run on a single background executor; observers get immutable
 * {@link State} snapshots through LiveData.
 */
public class FantasyRepository {

    /** Immutable snapshot of everything the UI needs. */
    public static class State {
        public final boolean loading;
        public final List<Player> roster;
        public final Map<Integer, List<WeekGame>> weekGames;
        public final List<OwnedCard> ownedCards;
        public final Squad squad;
        public final int packsAvailable;
        public final boolean weeklyPackReady;
        public final int weekNumber;
        public final DataSource dataSource;

        State(boolean loading, List<Player> roster, Map<Integer, List<WeekGame>> weekGames,
              List<OwnedCard> ownedCards, Squad squad, int packsAvailable,
              boolean weeklyPackReady, int weekNumber, DataSource dataSource) {
            this.loading = loading;
            this.roster = Collections.unmodifiableList(roster);
            this.weekGames = Collections.unmodifiableMap(weekGames);
            this.ownedCards = Collections.unmodifiableList(ownedCards);
            this.squad = squad;
            this.packsAvailable = packsAvailable;
            this.weeklyPackReady = weeklyPackReady;
            this.weekNumber = weekNumber;
            this.dataSource = dataSource;
        }

        static State initial() {
            return new State(true, new ArrayList<>(), new HashMap<>(), new ArrayList<>(),
                    new Squad(), 0, false, GameWeek.weekNumber(), DataSource.BUNDLED_SAMPLE);
        }

        public Player playerById(int id) {
            for (Player p : roster) {
                if (p.id == id) return p;
            }
            return null;
        }

        public OwnedCard cardById(String cardId) {
            if (cardId == null) return null;
            for (OwnedCard c : ownedCards) {
                if (c.cardId.equals(cardId)) return c;
            }
            return null;
        }

        public List<WeekGame> gamesFor(int playerId) {
            List<WeekGame> games = weekGames.get(playerId);
            return games == null ? Collections.emptyList() : games;
        }

        /** Points for one squad slot, or null when the slot is empty. */
        public Integer slotPoints(int slot) {
            OwnedCard card = cardById(slot < squad.slots.size() ? squad.slots.get(slot) : null);
            if (card == null) return null;
            Player player = playerById(card.playerId);
            if (player == null) return null;
            return FantasyScoring.slotPoints(player, gamesFor(player.id), card.rarity,
                    squad.captainSlot == slot);
        }

        public int totalPoints() {
            int total = 0;
            for (int slot = 0; slot < Squad.SQUAD_SIZE; slot++) {
                Integer points = slotPoints(slot);
                if (points != null) total += points;
            }
            return total;
        }
    }

    private final IcfApiClient api;
    private final SampleDataSource sample;
    private final LocalStore store;
    private final PackGenerator packGenerator = new PackGenerator();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final MutableLiveData<State> state = new MutableLiveData<>(State.initial());

    /**
     * Authoritative snapshot, only mutated on {@link #executor}. LiveData's
     * postValue applies on the main thread with a delay, so the executor must
     * never read state back from the LiveData — it would see stale values.
     */
    private volatile State current = State.initial();

    private int lastGrantWeek;

    public FantasyRepository(IcfApiClient api, SampleDataSource sample, LocalStore store) {
        this.api = api;
        this.sample = sample;
        this.store = store;
    }

    public LiveData<State> getState() {
        return state;
    }

    /** Loads persisted progress, grants starter packs, fetches data. */
    public void initialize() {
        executor.execute(() -> {
            LocalStore.Snapshot saved = store.load();
            int currentWeek = GameWeek.currentWeekKey();

            int packs = saved.packsAvailable;
            lastGrantWeek = saved.lastGrantWeek;
            if (!saved.initialized) {
                packs += PackGenerator.STARTER_PACKS;
                lastGrantWeek = currentWeek;
            }
            boolean weeklyReady = lastGrantWeek < currentWeek;

            State current = requireState();
            publish(new State(true, current.roster, current.weekGames,
                    saved.ownedCards, saved.squad, packs, weeklyReady,
                    GameWeek.weekNumber(), current.dataSource));
            persist();
            refreshBlocking();
        });
    }

    /** Fetches roster + weekly games from chess.org.il, falling back to samples. */
    public void refresh() {
        executor.execute(this::refreshBlocking);
    }

    private void refreshBlocking() {
        State before = requireState();
        publish(withLoading(before, true));

        List<Player> live = api.fetchClubPlayers();
        List<Player> roster = live != null ? live : sample.loadRoster();
        roster = new ArrayList<>(roster);
        roster.sort(Comparator.comparingInt((Player p) -> p.nationalRating).reversed());
        DataSource source = live != null ? DataSource.LIVE_API : DataSource.BUNDLED_SAMPLE;

        DateTimeFormatter fmt = DateTimeFormatter.ISO_LOCAL_DATE;
        String from = GameWeek.weekStart().format(fmt);
        String to = GameWeek.weekEnd().format(fmt);

        Map<Integer, List<WeekGame>> games = new HashMap<>();
        for (Player player : roster) {
            List<WeekGame> playerGames = null;
            if (live != null) {
                playerGames = api.fetchPlayerGames(player.id, from, to);
            }
            if (playerGames == null) {
                playerGames = SampleWeekGenerator.gamesFor(player);
            }
            games.put(player.id, playerGames);
        }

        State current = requireState();
        publish(new State(false, roster, games, current.ownedCards, current.squad,
                current.packsAvailable, current.weeklyPackReady, GameWeek.weekNumber(), source));
    }

    /** Opens one pack; the revealed cards are delivered to {@code onOpened} on the UI thread. */
    public void openPack(Consumer<List<OwnedCard>> onOpened,
                         android.os.Handler mainHandler) {
        executor.execute(() -> {
            State current = requireState();
            if (current.packsAvailable <= 0 || current.roster.isEmpty()) return;

            List<OwnedCard> newCards = packGenerator.openPack(
                    current.roster,
                    playerId -> {
                        int count = 1;
                        for (OwnedCard c : current.ownedCards) {
                            if (c.playerId == playerId) count++;
                        }
                        return count;
                    },
                    System.currentTimeMillis());

            List<OwnedCard> owned = new ArrayList<>(current.ownedCards);
            owned.addAll(newCards);
            publish(new State(current.loading, current.roster, current.weekGames, owned,
                    current.squad, current.packsAvailable - 1, current.weeklyPackReady,
                    current.weekNumber, current.dataSource));
            persist();
            mainHandler.post(() -> onOpened.accept(newCards));
        });
    }

    /** Claims the free weekly pack (one per game-week). */
    public void claimWeeklyPack() {
        executor.execute(() -> {
            State current = requireState();
            if (!current.weeklyPackReady) return;
            lastGrantWeek = GameWeek.currentWeekKey();
            publish(new State(current.loading, current.roster, current.weekGames,
                    current.ownedCards, current.squad,
                    current.packsAvailable + PackGenerator.WEEKLY_FREE_PACKS,
                    false, current.weekNumber, current.dataSource));
            persist();
        });
    }

    /** Puts an owned card into a squad slot (removing it from any other slot). */
    public void setSquadSlot(int slot, String cardId) {
        if (slot < 0 || slot >= Squad.SQUAD_SIZE) return;
        executor.execute(() -> {
            State current = requireState();
            publish(withSquad(current, current.squad.withSlot(slot, cardId)));
            persist();
        });
    }

    public void setCaptain(int slot) {
        if (slot < 0 || slot >= Squad.SQUAD_SIZE) return;
        executor.execute(() -> {
            State current = requireState();
            publish(withSquad(current, current.squad.withCaptain(slot)));
            persist();
        });
    }

    // ---- helpers -----------------------------------------------------------

    private State requireState() {
        return current;
    }

    private void publish(State next) {
        current = next;
        state.postValue(next);
    }

    private static State withLoading(State s, boolean loading) {
        return new State(loading, s.roster, s.weekGames, s.ownedCards, s.squad,
                s.packsAvailable, s.weeklyPackReady, s.weekNumber, s.dataSource);
    }

    private static State withSquad(State s, Squad squad) {
        return new State(s.loading, s.roster, s.weekGames, s.ownedCards, squad,
                s.packsAvailable, s.weeklyPackReady, s.weekNumber, s.dataSource);
    }

    private void persist() {
        State s = requireState();
        store.save(new LocalStore.Snapshot(
                new ArrayList<>(s.ownedCards), s.squad, s.packsAvailable,
                lastGrantWeek, true));
    }
}
