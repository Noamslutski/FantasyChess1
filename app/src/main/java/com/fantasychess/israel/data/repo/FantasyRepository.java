package com.fantasychess.israel.data.repo;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.fantasychess.israel.R;
import com.fantasychess.israel.data.api.IcfApiClient;
import com.fantasychess.israel.data.local.LocalStore;
import com.fantasychess.israel.data.model.DataSource;
import com.fantasychess.israel.data.model.MarketListing;
import com.fantasychess.israel.data.model.OwnedCard;
import com.fantasychess.israel.data.model.Player;
import com.fantasychess.israel.data.model.Rarity;
import com.fantasychess.israel.data.model.Squad;
import com.fantasychess.israel.data.model.TradeOffer;
import com.fantasychess.israel.data.model.UserProfile;
import com.fantasychess.israel.data.sample.SampleDataSource;
import com.fantasychess.israel.data.sample.SampleWeekGenerator;
import com.fantasychess.israel.domain.CardValuator;
import com.fantasychess.israel.domain.FantasyScoring;
import com.fantasychess.israel.domain.GameWeek;
import com.fantasychess.israel.domain.MarketSimulator;
import com.fantasychess.israel.domain.MintLedger;
import com.fantasychess.israel.domain.PackGenerator;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Single source of truth for the whole game: roster (live federation data
 * when possible), the user's collection/squad, the Pawns wallet, packs, the
 * transfer market and trade offers.
 *
 * All mutations run on a single background executor; observers get immutable
 * {@link State} snapshots through LiveData. The executor owns {@link #current}
 * — LiveData is never read back (postValue applies later on the main thread).
 */
public class FantasyRepository {

    private static final String TAG = "FantasyRepository";
    public static final long STARTING_PAWNS = 1_000;

    /** Immutable snapshot of everything the UI needs. */
    public static class State {
        public final boolean loading;
        public final List<Player> roster;
        public final Map<Integer, List<com.fantasychess.israel.data.model.WeekGame>> weekGames;
        public final List<OwnedCard> ownedCards;
        public final Map<Integer, Player> playerCache;
        public final Squad squad;
        public final int freePacks;
        public final int proPacks;
        public final boolean weeklyPackReady;
        public final int weekNumber;
        public final DataSource dataSource;
        public final long pawns;
        public final List<MarketListing> listings;
        public final List<TradeOffer> offers;
        public final int adsLeftToday;
        public final String username;

        State(boolean loading, List<Player> roster,
              Map<Integer, List<com.fantasychess.israel.data.model.WeekGame>> weekGames,
              List<OwnedCard> ownedCards, Map<Integer, Player> playerCache, Squad squad, int freePacks, int proPacks,
              boolean weeklyPackReady, int weekNumber, DataSource dataSource,
              long pawns, List<MarketListing> listings, List<TradeOffer> offers,
              int adsLeftToday, String username) {
            this.loading = loading;
            this.roster = Collections.unmodifiableList(roster);
            this.weekGames = Collections.unmodifiableMap(weekGames);
            this.ownedCards = Collections.unmodifiableList(ownedCards);
            this.playerCache = Collections.unmodifiableMap(playerCache);
            this.squad = squad;
            this.freePacks = freePacks;
            this.proPacks = proPacks;
            this.weeklyPackReady = weeklyPackReady;
            this.weekNumber = weekNumber;
            this.dataSource = dataSource;
            this.pawns = pawns;
            this.listings = Collections.unmodifiableList(listings);
            this.offers = Collections.unmodifiableList(offers);
            this.adsLeftToday = adsLeftToday;
            this.username = username;
        }

        static State initial() {
            return new State(true, new ArrayList<>(), new HashMap<>(), new ArrayList<>(),
                    new HashMap<>(), new Squad(), 0, 0, false, GameWeek.weekNumber(),
                    DataSource.BUNDLED_SAMPLE, 0, new ArrayList<>(), new ArrayList<>(),
                    PackGenerator.MAX_ADS_PER_DAY, "");
        }

        public Player playerById(int id) {
            for (Player p : roster) {
                if (p.id == id) return p;
            }
            return playerCache.get(id);
        }

        public OwnedCard cardById(String cardId) {
            if (cardId == null) return null;
            for (OwnedCard c : ownedCards) {
                if (c.cardId.equals(cardId)) return c;
            }
            return null;
        }

        public List<com.fantasychess.israel.data.model.WeekGame> gamesFor(int playerId) {
            List<com.fantasychess.israel.data.model.WeekGame> games = weekGames.get(playerId);
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

        /** Cards that may be listed or offered: tokenized and not the commons. */
        public List<OwnedCard> tradableCards() {
            List<OwnedCard> tradable = new ArrayList<>();
            for (OwnedCard card : ownedCards) {
                if (card.rarity != null && card.rarity.isTradable()) tradable.add(card);
            }
            return tradable;
        }
    }

    private final IcfApiClient api;
    private final SampleDataSource sample;
    private final LocalStore store;
    private final PackGenerator packGenerator = new PackGenerator();
    private final MarketSimulator market = new MarketSimulator();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final MutableLiveData<State> state = new MutableLiveData<>(State.initial());

    /** One-shot UI messages (string resource ids), e.g. "not enough Pawns". */
    private final MutableLiveData<Integer> events = new MutableLiveData<>();

    /** Authoritative snapshot, only mutated on the executor thread. */
    private volatile State current = State.initial();

    private int lastGrantWeek;
    private long adEpochDay;
    private int adCountToday;
    private MintLedger ledger = new MintLedger(null);

    public FantasyRepository(IcfApiClient api, SampleDataSource sample, LocalStore store) {
        this.api = api;
        this.sample = sample;
        this.store = store;
    }

    public LiveData<State> getState() {
        return state;
    }

    public LiveData<Integer> getEvents() {
        return events;
    }

    public void clearEvent() {
        events.postValue(null);
    }

    // ---- account (called synchronously from the login screen) ---------------

    public boolean isLoggedIn() {
        return store.loadProfile() != null;
    }

    public String loggedInUsername() {
        UserProfile profile = store.loadProfile();
        return profile == null ? "" : profile.username;
    }

    /** Creates the local account. Returns false if one already exists. */
    public boolean register(String username, String password) {
        if (store.loadProfile() != null) return false;
        store.saveProfile(new UserProfile(username.trim(), sha256(password),
                System.currentTimeMillis()));
        return true;
    }

    /** Verifies the local credentials. */
    public boolean login(String username, String password) {
        UserProfile profile = store.loadProfile();
        return profile != null
                && profile.username.equalsIgnoreCase(username.trim())
                && profile.passwordHash.equals(sha256(password));
    }

    public void logout() {
        store.saveProfile(null);
        publish(State.initial());
    }

    public void buyPawns(long amount) {
        safeExecute(() -> {
            publish(copy(current).pawns(current.pawns + amount).build());
            persist();
            events.postValue(R.string.event_bought); // Reuse "bought" event or add new one
        });
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    // ---- lifecycle -----------------------------------------------------------

    /** Loads persisted progress, grants starter packs and Pawns, fetches data. */
    public void initialize() {
        safeExecute(() -> {
            LocalStore.Snapshot saved = store.load();
            int currentWeek = GameWeek.currentWeekKey();

            int freePacks = saved.freePacks;
            long pawns = saved.pawns;
            lastGrantWeek = saved.lastGrantWeek;
            if (!saved.initialized) {
                freePacks += PackGenerator.STARTER_PACKS;
                pawns += STARTING_PAWNS;
                lastGrantWeek = currentWeek;
                // Grant Noam Slutzky card (ID 192738)
                saved.ownedCards.add(new OwnedCard(
                        UUID.randomUUID().toString(),
                        192738,
                        Rarity.PRO,
                        1,
                        System.currentTimeMillis()));
            }
            adEpochDay = saved.adEpochDay;
            adCountToday = saved.adCountToday;
            
            // Ensure Noam Slutzky card (ID 192738) is granted
            boolean hasNoam = false;
            for (OwnedCard c : saved.ownedCards) {
                if (c.playerId == 192738) {
                    hasNoam = true;
                    break;
                }
            }
            if (!hasNoam) {
                saved.ownedCards.add(new OwnedCard(UUID.randomUUID().toString(), 192738,
                        Rarity.PRO, 1, System.currentTimeMillis()));
            }

            rolloverAdDay();
            ledger = new MintLedger(saved.mintCounts);

            State c = current;
            List<Player> initialRoster = sample.loadRoster();
            publish(new State(true, initialRoster, c.weekGames, saved.ownedCards, c.playerCache, saved.squad,
                    freePacks, saved.proPacks, lastGrantWeek < currentWeek,
                    GameWeek.weekNumber(), DataSource.BUNDLED_SAMPLE, pawns, saved.listings,
                    saved.offers, adsLeft(), loggedInUsername()));
            persist();
            refreshBlocking();
        });
    }

    /** Fetches roster + weekly games from chess.org.il, falling back to samples. */
    public void refresh() {
        safeExecute(this::refreshBlocking);
    }

    private void refreshBlocking() {
        State before = current;
        publish(before.loading ? before : copy(before).loading(true).build());

        List<Player> live = api.fetchAllPlayers();
        if (live == null) live = api.fetchClubPlayers();
        
        List<Player> roster = live != null ? live : sample.loadRoster();
        roster = new ArrayList<>(roster);
        roster.sort(Comparator.comparingInt((Player p) -> p.nationalRating).reversed());
        DataSource source = live != null ? DataSource.LIVE_API : DataSource.BUNDLED_SAMPLE;

        // Publish roster immediately before fetching games to unblock UI/Packs
        State mid = current;
        publish(copy(mid).roster(roster).dataSource(source).build());

        DateTimeFormatter fmt = DateTimeFormatter.ISO_LOCAL_DATE;
        String from = GameWeek.weekStart().format(fmt);
        // Fetch up to 14 days after the current week to show "next games"
        String to = GameWeek.weekEnd().plusDays(14).format(fmt);

        Map<Integer, List<com.fantasychess.israel.data.model.WeekGame>> games = new HashMap<>();
        
        // Optimization: Only fetch live games for owned players and the top 50 players.
        // The rest get sample data (if needed) to avoid thousands of network calls.
        Set<Integer> priorityIds = new HashSet<>();
        for (OwnedCard card : mid.ownedCards) priorityIds.add(card.playerId);
        for (int i = 0; i < Math.min(50, roster.size()); i++) priorityIds.add(roster.get(i).id);

        for (Player player : roster) {
            List<com.fantasychess.israel.data.model.WeekGame> playerGames = null;
            if (live != null && priorityIds.contains(player.id)) {
                playerGames = api.fetchPlayerGames(player.id, from, to);
            }
            if (playerGames == null) {
                playerGames = SampleWeekGenerator.gamesFor(player);
            }
            games.put(player.id, playerGames);
        }

        State c = current;
        publish(copy(c).loading(false).weekGames(games).build());
        resolveMissingPlayers();
        refreshMarketBlocking();
    }

    public void claimPlayerCard(int playerId) {
        safeExecute(() -> {
            List<OwnedCard> owned = new ArrayList<>(current.ownedCards);
            boolean alreadyOwned = false;
            for (OwnedCard c : owned) {
                if (c.playerId == playerId) {
                    alreadyOwned = true;
                    break;
                }
            }
            if (!alreadyOwned) {
                owned.add(new OwnedCard(UUID.randomUUID().toString(), playerId,
                        Rarity.COMMON, 1, System.currentTimeMillis()));
                publish(copy(current).ownedCards(owned).build());
                persist();
            }
        });
    }

    public void fetchPlayerDetails(int playerId, Consumer<Player> onDone) {
        safeExecute(() -> {
            Player p = current.playerById(playerId);
            if (p == null) p = api.fetchPlayerDetails(playerId);
            if (p != null) {
                Map<Integer, Player> next = new HashMap<>(current.playerCache);
                next.put(p.id, p);
                publish(copy(current).playerCache(next).build());
            }
            final Player result = p;
            new Handler(Looper.getMainLooper()).post(() -> onDone.accept(result));
        });
    }

    public void searchPlayers(String query, Consumer<List<String>> onDone) {
        safeExecute(() -> {
            List<String> results = api.searchPlayers(query);
            new Handler(Looper.getMainLooper()).post(() -> onDone.accept(results));
        });
    }

    public void fetchPairings(int playerId, Integer fideId, Consumer<List<com.fantasychess.israel.data.model.WeekGame>> onDone) {
        safeExecute(() -> {
            List<com.fantasychess.israel.data.model.WeekGame> games = null;
            if (fideId != null) {
                games = api.fetchPairingsFromChessResults(fideId, playerId);
            }
            final List<com.fantasychess.israel.data.model.WeekGame> result = games;
            new Handler(Looper.getMainLooper()).post(() -> onDone.accept(result));
        });
    }

    private void resolveMissingPlayers() {
        safeExecute(() -> {
            State c = current;
            Map<Integer, Player> nextCache = new HashMap<>(c.playerCache);
            boolean changed = false;
            for (OwnedCard card : c.ownedCards) {
                if (c.playerById(card.playerId) == null && !nextCache.containsKey(card.playerId)) {
                    Player p = api.fetchPlayerDetails(card.playerId);
                    if (p != null) {
                        nextCache.put(p.id, p);
                        changed = true;
                    }
                }
            }
            if (changed) {
                publish(copy(current).playerCache(nextCache).build());
            }
        });
    }

    /** Advances the simulated market: auctions, bot buys, fresh listings. */
    public void refreshMarket() {
        safeExecute(this::refreshMarketBlocking);
    }

    private void refreshMarketBlocking() {
        State c = current;
        if (c.roster.isEmpty()) return;
        rolloverAdDay();
        long now = System.currentTimeMillis();

        List<OwnedCard> owned = new ArrayList<>(c.ownedCards);
        List<MarketListing> listings = new ArrayList<>();
        long pawns = c.pawns;

        for (MarketListing listing : c.listings) {
            Player player = c.playerById(listing.playerId);
            if (player == null) continue;

            if (listing.mine) {
                long fair = CardValuator.value(player, listing.rarity);
                boolean ended = listing.type == MarketListing.Type.AUCTION
                        && now >= listing.endsAtEpochMs;
                if (market.botBuysMyListing(listing.price, fair)) {
                    pawns += listing.price; // sold to another manager
                } else if (ended) {
                    owned.add(restoreCard(listing, now)); // unsold — card comes back
                } else {
                    listings.add(listing);
                }
                continue;
            }

            if (listing.type == MarketListing.Type.AUCTION) {
                if (now >= listing.endsAtEpochMs) {
                    if (listing.myBid > 0 && listing.myBid == listing.price) {
                        owned.add(restoreCard(listing, now)); // I won the auction
                        events.postValue(R.string.event_auction_won);
                    }
                    continue; // ended either way
                }
                if (listing.myBid > 0 && market.botOutbids()) {
                    pawns += listing.myBid; // refund, someone outbid me
                    listings.add(listing.withBid(market.outbidAmount(listing.price), 0));
                    events.postValue(R.string.event_outbid);
                    continue;
                }
            } else if (now - listing.createdAtEpochMs > 48L * 3_600_000L) {
                continue; // stale direct sale disappears
            }
            listings.add(listing);
        }

        listings = market.topUpListings(listings, c.roster, ledger, now);

        publish(copy(current).ownedCards(owned).pawns(pawns).listings(listings)
                .adsLeft(adsLeft()).build());
        persist();
    }

    // ---- packs ---------------------------------------------------------------

    /** Opens a free (gray, commons) pack. */
    public void openFreePack(Consumer<List<OwnedCard>> onOpened, Handler mainHandler) {
        openPack(PackGenerator.PackType.FREE, onOpened, mainHandler);
    }

    /** Opens a Pro (gold) pack from inventory. */
    public void openProPack(Consumer<List<OwnedCard>> onOpened, Handler mainHandler) {
        openPack(PackGenerator.PackType.PRO, onOpened, mainHandler);
    }

    private void openPack(PackGenerator.PackType type,
                          Consumer<List<OwnedCard>> onOpened, Handler mainHandler) {
        safeExecute(() -> {
            State c = current;
            if (c.roster.isEmpty()) return;
            if (type == PackGenerator.PackType.PRO && c.proPacks <= 0) return;
            if (type == PackGenerator.PackType.FREE && c.freePacks <= 0) return;

            List<OwnedCard> newCards = packGenerator.openPack(type, c.roster, ledger,
                    System.currentTimeMillis());
            List<OwnedCard> owned = new ArrayList<>(c.ownedCards);
            owned.addAll(newCards);

            publish(copy(c).ownedCards(owned)
                    .freePacks(type == PackGenerator.PackType.FREE ? c.freePacks - 1 : c.freePacks)
                    .proPacks(type == PackGenerator.PackType.PRO ? c.proPacks - 1 : c.proPacks)
                    .build());
            persist();
            mainHandler.post(() -> onOpened.accept(newCards));
        });
    }

    /** Buys and opens a Pro pack with Pawns. */
    public void buyProPack(Consumer<List<OwnedCard>> onOpened, Handler mainHandler) {
        safeExecute(() -> {
            State c = current;
            if (c.pawns < PackGenerator.PRO_PACK_PRICE_PAWNS) {
                events.postValue(R.string.event_not_enough_pawns);
                return;
            }
            if (c.roster.isEmpty()) {
                // Should not happen as we load sample roster at start
                return;
            }

            List<OwnedCard> newCards = packGenerator.openPack(
                    PackGenerator.PackType.PRO, c.roster, ledger, System.currentTimeMillis());
            List<OwnedCard> owned = new ArrayList<>(c.ownedCards);
            owned.addAll(newCards);

            publish(copy(c).pawns(c.pawns - PackGenerator.PRO_PACK_PRICE_PAWNS)
                    .ownedCards(owned).build());
            persist();
            mainHandler.post(() -> onOpened.accept(newCards));
        });
    }

    /**
     * Rewards one ad view with an instant ad pack (commons with a chance of
     * Pro), up to {@link PackGenerator#MAX_ADS_PER_DAY} per day. The ad itself
     * is simulated by the UI; plug a real rewarded-ad SDK in at that call site.
     */
    public void grantAdReward(Consumer<List<OwnedCard>> onOpened, Handler mainHandler) {
        safeExecute(() -> {
            rolloverAdDay();
            if (adsLeft() <= 0) {
                events.postValue(R.string.event_no_ads_left);
                return;
            }
            State c = current;
            if (c.roster.isEmpty()) return;
            adCountToday++;

            List<OwnedCard> newCards = packGenerator.openPack(
                    PackGenerator.PackType.AD, c.roster, ledger, System.currentTimeMillis());
            List<OwnedCard> owned = new ArrayList<>(c.ownedCards);
            owned.addAll(newCards);

            publish(copy(c).ownedCards(owned).adsLeft(adsLeft()).build());
            persist();
            mainHandler.post(() -> onOpened.accept(newCards));
        });
    }

    /** Claims the free weekly (gray) pack — one per game-week. */
    public void claimWeeklyPack() {
        safeExecute(() -> {
            State c = current;
            if (!c.weeklyPackReady) return;
            lastGrantWeek = GameWeek.currentWeekKey();
            publish(copy(c).freePacks(c.freePacks + PackGenerator.WEEKLY_FREE_PACKS)
                    .weeklyPackReady(false).build());
            persist();
        });
    }

    // ---- market --------------------------------------------------------------

    public void buyNow(String listingId) {
        safeExecute(() -> {
            State c = current;
            MarketListing listing = findListing(c, listingId);
            if (listing == null || listing.mine
                    || listing.type != MarketListing.Type.DIRECT_SALE) return;
            if (c.pawns < listing.price) {
                events.postValue(R.string.event_not_enough_pawns);
                return;
            }
            List<MarketListing> listings = withoutListing(c.listings, listingId);
            List<OwnedCard> owned = new ArrayList<>(c.ownedCards);
            owned.add(restoreCard(listing, System.currentTimeMillis()));
            publish(copy(c).pawns(c.pawns - listing.price).ownedCards(owned)
                    .listings(listings).build());
            persist();
            events.postValue(R.string.event_bought);
        });
    }

    public void placeBid(String listingId, long amount) {
        safeExecute(() -> {
            State c = current;
            MarketListing listing = findListing(c, listingId);
            if (listing == null || listing.mine
                    || listing.type != MarketListing.Type.AUCTION) return;
            // First bid may match the current price; raising your own bid must top it.
            long minimum = listing.myBid > 0
                    ? market.minimumNextBid(listing.price) : listing.price;
            if (amount < minimum) {
                events.postValue(R.string.event_bid_too_low);
                return;
            }
            long budget = c.pawns + listing.myBid; // previous bid comes back
            if (budget < amount) {
                events.postValue(R.string.event_not_enough_pawns);
                return;
            }
            List<MarketListing> listings = new ArrayList<>();
            for (MarketListing l : c.listings) {
                listings.add(l.id.equals(listingId) ? l.withBid(amount, amount) : l);
            }
            publish(copy(c).pawns(budget - amount).listings(listings).build());
            persist();
            events.postValue(R.string.event_bid_placed);
        });
    }

    /** Lists one of my cards for sale; the card leaves the collection. */
    public void listCard(String cardId, long price, boolean auction) {
        safeExecute(() -> {
            State c = current;
            OwnedCard card = c.cardById(cardId);
            if (card == null || card.rarity == null || !card.rarity.isTradable() || price <= 0) return;

            List<OwnedCard> owned = new ArrayList<>(c.ownedCards);
            owned.remove(card);
            Squad squad = c.squad;
            for (int slot = 0; slot < Squad.SQUAD_SIZE; slot++) {
                if (cardId.equals(squad.slots.get(slot))) squad = squad.withSlot(slot, null);
            }
            long now = System.currentTimeMillis();
            List<MarketListing> listings = new ArrayList<>(c.listings);
            listings.add(new MarketListing(UUID.randomUUID().toString(),
                    c.username.isEmpty() ? "אני" : c.username, true,
                    card.playerId, card.rarity, card.serial,
                    auction ? MarketListing.Type.AUCTION : MarketListing.Type.DIRECT_SALE,
                    price, auction ? now + 24L * 3_600_000L : 0, 0, now));
            publish(copy(c).ownedCards(owned).squad(squad).listings(listings).build());
            persist();
            events.postValue(R.string.event_listed);
        });
    }

    public void cancelMyListing(String listingId) {
        safeExecute(() -> {
            State c = current;
            MarketListing listing = findListing(c, listingId);
            if (listing == null || !listing.mine) return;
            List<OwnedCard> owned = new ArrayList<>(c.ownedCards);
            owned.add(restoreCard(listing, System.currentTimeMillis()));
            publish(copy(c).ownedCards(owned)
                    .listings(withoutListing(c.listings, listingId)).build());
            persist();
        });
    }

    /** Sends a trade offer (cards + Pawns) for a listing; the bot answers now. */
    public void makeOffer(String listingId, List<String> offeredCardIds, long offeredPawns) {
        safeExecute(() -> {
            State c = current;
            MarketListing listing = findListing(c, listingId);
            if (listing == null || listing.mine) return;
            if (offeredPawns < 0 || c.pawns < offeredPawns) {
                events.postValue(R.string.event_not_enough_pawns);
                return;
            }
            long offeredValue = offeredPawns;
            for (String cardId : offeredCardIds) {
                OwnedCard card = c.cardById(cardId);
                Player player = card == null ? null : c.playerById(card.playerId);
                if (card == null || player == null || card.rarity == null || !card.rarity.isTradable()) return;
                offeredValue += CardValuator.value(player, card.rarity);
            }
            Player target = c.playerById(listing.playerId);
            if (target == null) return;
            long targetValue = Math.max(CardValuator.value(target, listing.rarity),
                    listing.type == MarketListing.Type.DIRECT_SALE ? listing.price : 0);

            TradeOffer offer = new TradeOffer(UUID.randomUUID().toString(), listingId,
                    listing.sellerName, listing.playerId, listing.rarity, listing.serial,
                    offeredCardIds, offeredPawns, TradeOffer.Status.PENDING, 0,
                    System.currentTimeMillis());

            MarketSimulator.OfferVerdict verdict = market.evaluateOffer(targetValue, offeredValue);
            if (verdict == MarketSimulator.OfferVerdict.ACCEPT) {
                executeTrade(listing, offeredCardIds, offeredPawns, 0,
                        offer.withStatus(TradeOffer.Status.ACCEPTED));
                events.postValue(R.string.event_offer_accepted);
            } else if (verdict == MarketSimulator.OfferVerdict.COUNTER) {
                long extra = market.counterExtraPawns(targetValue, offeredValue);
                recordOffer(offer.withCounter(extra));
                events.postValue(R.string.event_offer_countered);
            } else {
                recordOffer(offer.withStatus(TradeOffer.Status.REJECTED));
                events.postValue(R.string.event_offer_rejected);
            }
        });
    }

    /** Accepts a counter-offer: pays the extra Pawns on top of the original offer. */
    public void acceptCounter(String offerId) {
        safeExecute(() -> {
            State c = current;
            TradeOffer offer = findOffer(c, offerId);
            if (offer == null || offer.status != TradeOffer.Status.COUNTERED) return;
            MarketListing listing = findListing(c, offer.listingId);
            if (listing == null) {
                recordOffer(offer.withStatus(TradeOffer.Status.DECLINED));
                events.postValue(R.string.event_listing_gone);
                return;
            }
            long totalPawns = offer.offeredPawns + offer.counterExtraPawns;
            if (c.pawns < totalPawns) {
                events.postValue(R.string.event_not_enough_pawns);
                return;
            }
            for (String cardId : offer.offeredCardIds) {
                if (c.cardById(cardId) == null) { // a card was sold meanwhile
                    recordOffer(offer.withStatus(TradeOffer.Status.DECLINED));
                    return;
                }
            }
            executeTrade(listing, offer.offeredCardIds, offer.offeredPawns,
                    offer.counterExtraPawns,
                    offer.withStatus(TradeOffer.Status.COUNTER_ACCEPTED));
            events.postValue(R.string.event_offer_accepted);
        });
    }

    public void declineCounter(String offerId) {
        safeExecute(() -> {
            TradeOffer offer = findOffer(current, offerId);
            if (offer == null || offer.status != TradeOffer.Status.COUNTERED) return;
            recordOffer(offer.withStatus(TradeOffer.Status.DECLINED));
        });
    }

    // ---- squad (unchanged behavior) -------------------------------------------

    public void setSquadSlot(int slot, String cardId) {
        if (slot < 0 || slot >= Squad.SQUAD_SIZE) return;
        safeExecute(() -> {
            publish(copy(current).squad(current.squad.withSlot(slot, cardId)).build());
            persist();
        });
    }

    public void setCaptain(int slot) {
        if (slot < 0 || slot >= Squad.SQUAD_SIZE) return;
        safeExecute(() -> {
            publish(copy(current).squad(current.squad.withCaptain(slot)).build());
            persist();
        });
    }

    // ---- internals -------------------------------------------------------------

    /** Moves cards/Pawns both ways and finalizes the trade. */
    private void executeTrade(MarketListing listing, List<String> givenCardIds,
                              long givenPawns, long extraPawns, TradeOffer resolved) {
        State c = current;
        List<OwnedCard> owned = new ArrayList<>(c.ownedCards);
        Squad squad = c.squad;
        for (String cardId : givenCardIds) {
            OwnedCard card = c.cardById(cardId);
            if (card != null) owned.remove(card);
            for (int slot = 0; slot < Squad.SQUAD_SIZE; slot++) {
                if (cardId.equals(squad.slots.get(slot))) squad = squad.withSlot(slot, null);
            }
        }
        owned.add(restoreCard(listing, System.currentTimeMillis()));
        List<TradeOffer> offers = withOffer(c.offers, resolved);
        publish(copy(c).pawns(c.pawns - givenPawns - extraPawns).ownedCards(owned)
                .squad(squad).listings(withoutListing(c.listings, listing.id))
                .offers(offers).build());
        persist();
    }

    private void recordOffer(TradeOffer offer) {
        publish(copy(current).offers(withOffer(current.offers, offer)).build());
        persist();
    }

    private static List<TradeOffer> withOffer(List<TradeOffer> offers, TradeOffer offer) {
        List<TradeOffer> next = new ArrayList<>();
        for (TradeOffer o : offers) {
            if (!o.id.equals(offer.id)) next.add(o);
        }
        next.add(0, offer);
        while (next.size() > 20) next.remove(next.size() - 1); // keep recent history
        return next;
    }

    /** Turns a listing back into an owned card (same serial, fresh card id). */
    private static OwnedCard restoreCard(MarketListing listing, long now) {
        return new OwnedCard(UUID.randomUUID().toString(), listing.playerId,
                listing.rarity, listing.serial, now);
    }

    private static MarketListing findListing(State state, String listingId) {
        for (MarketListing listing : state.listings) {
            if (listing.id.equals(listingId)) return listing;
        }
        return null;
    }

    private static TradeOffer findOffer(State state, String offerId) {
        for (TradeOffer offer : state.offers) {
            if (offer.id.equals(offerId)) return offer;
        }
        return null;
    }

    private static List<MarketListing> withoutListing(List<MarketListing> listings, String id) {
        List<MarketListing> next = new ArrayList<>();
        for (MarketListing listing : listings) {
            if (!listing.id.equals(id)) next.add(listing);
        }
        return next;
    }

    private void rolloverAdDay() {
        long today = LocalDate.now().toEpochDay();
        if (today != adEpochDay) {
            adEpochDay = today;
            adCountToday = 0;
        }
    }

    private int adsLeft() {
        return Math.max(0, PackGenerator.MAX_ADS_PER_DAY - adCountToday);
    }

    /**
     * Runs a repository task on the single background thread. Any failure is
     * logged and swallowed, and the UI is released from the loading state, so
     * an unexpected exception (bad network payload, storage hiccup, etc.) can
     * never crash the whole app — it just leaves the last good state on screen.
     */
    private void safeExecute(Runnable task) {
        executor.execute(() -> {
            try {
                task.run();
            } catch (Throwable t) {
                Log.e(TAG, "Background task failed", t);
                try {
                    if (current.loading) {
                        publish(copy(current).loading(false).build());
                    }
                } catch (Throwable ignored) {
                    // last-ditch: never let the executor thread die
                }
            }
        });
    }

    private void publish(State next) {
        current = next;
        state.postValue(next);
    }

    private void persist() {
        State s = current;
        LocalStore.Snapshot snapshot = new LocalStore.Snapshot();
        snapshot.ownedCards = new ArrayList<>(s.ownedCards);
        snapshot.squad = s.squad;
        snapshot.freePacks = s.freePacks;
        snapshot.proPacks = s.proPacks;
        snapshot.lastGrantWeek = lastGrantWeek;
        snapshot.initialized = true;
        snapshot.pawns = s.pawns;
        snapshot.mintCounts = ledger.snapshot();
        snapshot.listings = new ArrayList<>(s.listings);
        snapshot.offers = new ArrayList<>(s.offers);
        snapshot.adEpochDay = adEpochDay;
        snapshot.adCountToday = adCountToday;
        store.save(snapshot);
    }

    // ---- tiny builder to keep the immutable copies readable ---------------------

    private static Builder copy(State s) {
        return new Builder(s);
    }

    private static final class Builder {
        private boolean loading;
        private List<Player> roster;
        private Map<Integer, List<com.fantasychess.israel.data.model.WeekGame>> weekGames;
        private List<OwnedCard> ownedCards;
        private Map<Integer, Player> playerCache;
        private Squad squad;
        private int freePacks;
        private int proPacks;
        private boolean weeklyPackReady;
        private int weekNumber;
        private DataSource dataSource;
        private long pawns;
        private List<MarketListing> listings;
        private List<TradeOffer> offers;
        private int adsLeft;
        private String username;

        Builder(State s) {
            loading = s.loading;
            roster = s.roster;
            weekGames = s.weekGames;
            ownedCards = s.ownedCards;
            playerCache = s.playerCache;
            squad = s.squad;
            freePacks = s.freePacks;
            proPacks = s.proPacks;
            weeklyPackReady = s.weeklyPackReady;
            weekNumber = s.weekNumber;
            dataSource = s.dataSource;
            pawns = s.pawns;
            listings = s.listings;
            offers = s.offers;
            adsLeft = s.adsLeftToday;
            username = s.username;
        }

        Builder loading(boolean v) { loading = v; return this; }
        Builder roster(List<Player> v) { roster = v; return this; }
        Builder weekGames(Map<Integer, List<com.fantasychess.israel.data.model.WeekGame>> v) {
            weekGames = v; return this;
        }
        Builder ownedCards(List<OwnedCard> v) { ownedCards = v; return this; }
        Builder squad(Squad v) { squad = v; return this; }
        Builder freePacks(int v) { freePacks = v; return this; }
        Builder proPacks(int v) { proPacks = v; return this; }
        Builder weeklyPackReady(boolean v) { weeklyPackReady = v; return this; }
        Builder dataSource(DataSource v) { dataSource = v; return this; }
        Builder pawns(long v) { pawns = v; return this; }
        Builder listings(List<MarketListing> v) { listings = v; return this; }
        Builder offers(List<TradeOffer> v) { offers = v; return this; }
        Builder adsLeft(int v) { adsLeft = v; return this; }
        Builder playerCache(Map<Integer, Player> v) { playerCache = v; return this; }

        State build() {
            return new State(loading, new ArrayList<>(roster), new HashMap<>(weekGames),
                    new ArrayList<>(ownedCards), new HashMap<>(playerCache), squad, freePacks, proPacks,
                    weeklyPackReady, weekNumber, dataSource, pawns,
                    new ArrayList<>(listings), new ArrayList<>(offers), adsLeft, username);
        }
    }
}
