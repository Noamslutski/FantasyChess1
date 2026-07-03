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

    public LiveData<List<OwnedCard>> getLastOpenedPack() {
        return lastOpenedPack;
    }

    public void refresh() {
        repository.refresh();
    }

    public void openPack() {
        repository.openPack(lastOpenedPack::setValue, mainHandler);
    }

    public void dismissPackReveal() {
        lastOpenedPack.setValue(null);
    }

    public void claimWeeklyPack() {
        repository.claimWeeklyPack();
    }

    public void setSquadSlot(int slot, String cardId) {
        repository.setSquadSlot(slot, cardId);
    }

    public void setCaptain(int slot) {
        repository.setCaptain(slot);
    }
}
