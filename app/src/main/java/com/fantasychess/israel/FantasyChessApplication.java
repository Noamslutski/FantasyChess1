package com.fantasychess.israel;

import android.app.Application;

import com.fantasychess.israel.data.api.IcfApiClient;
import com.fantasychess.israel.data.local.LocalStore;
import com.fantasychess.israel.data.repo.FantasyRepository;
import com.fantasychess.israel.data.sample.SampleDataSource;

/** Manual dependency container — the app is small enough not to need Hilt. */
public class FantasyChessApplication extends Application {

    private FantasyRepository repository;

    @Override
    public void onCreate() {
        super.onCreate();
        repository = new FantasyRepository(
                new IcfApiClient(),
                new SampleDataSource(this),
                new LocalStore(this));
        repository.initialize();
    }

    public FantasyRepository getRepository() {
        return repository;
    }
}
