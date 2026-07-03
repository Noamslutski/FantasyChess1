package com.fantasychess.israel.ui;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.fantasychess.israel.FantasyChessApplication;
import com.fantasychess.israel.data.model.OwnedCard;
import com.fantasychess.israel.data.repo.FantasyRepository;

import java.util.List;

public class MainViewModel extends AndroidViewModel {

    private final FantasyRepository repository;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    /** Cards revealed by the most recent pack, for the reveal dialog. */
    private final MutableLiveData<List<OwnedCard>> lastOpenedPack = new MutableLiveData<>();

    public MainViewModel(@NonNull Application application) {
        super(application);
        repository = ((FantasyChessApplication) application).getRepository();
    }

    public LiveData<FantasyRepository.State> getState() {
        return repository.getState();
    }

    /** One-shot messages (string resource ids) from the repository. */
    public LiveData<Integer> getEvents() {
        return repository.getEvents();
    }

    public void clearEvent() {
        repository.clearEvent();
    }

    public LiveData<List<OwnedCard>> getLastOpenedPack() {
        return lastOpenedPack;
    }

    public void dismissPackReveal() {
        lastOpenedPack.setValue(null);
    }

    public void refresh() {
        repository.refresh();
    }

    public void refreshMarket() {
        repository.refreshMarket();
    }

    // ---- packs ----

    public void openFreePack() {
        repository.openFreePack(lastOpenedPack::setValue, mainHandler);
    }

    public void openProPack() {
        repository.openProPack(lastOpenedPack::setValue, mainHandler);
    }

    public void buyProPack() {
        repository.buyProPack();
    }

    public void grantAdReward() {
        repository.grantAdReward(lastOpenedPack::setValue, mainHandler);
    }

    public void claimWeeklyPack() {
        repository.claimWeeklyPack();
    }

    // ---- squad ----

    public void setSquadSlot(int slot, String cardId) {
        repository.setSquadSlot(slot, cardId);
    }

    public void setCaptain(int slot) {
        repository.setCaptain(slot);
    }

    // ---- market ----

    public void buyNow(String listingId) {
        repository.buyNow(listingId);
    }

    public void placeBid(String listingId, long amount) {
        repository.placeBid(listingId, amount);
    }

    public void listCard(String cardId, long price, boolean auction) {
        repository.listCard(cardId, price, auction);
    }

    public void cancelMyListing(String listingId) {
        repository.cancelMyListing(listingId);
    }

    public void makeOffer(String listingId, List<String> cardIds, long pawns) {
        repository.makeOffer(listingId, cardIds, pawns);
    }

    public void acceptCounter(String offerId) {
        repository.acceptCounter(offerId);
    }

    public void declineCounter(String offerId) {
        repository.declineCounter(offerId);
    }
}
