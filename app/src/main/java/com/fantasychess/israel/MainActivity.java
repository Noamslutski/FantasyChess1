package com.fantasychess.israel;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.fantasychess.israel.ui.fragments.CollectionFragment;
import com.fantasychess.israel.ui.fragments.HomeFragment;
import com.fantasychess.israel.ui.fragments.MarketFragment;
import com.fantasychess.israel.ui.fragments.PacksFragment;
import com.fantasychess.israel.ui.fragments.PlayerDetailFragment;
import com.fantasychess.israel.ui.fragments.SquadFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    private BottomNavigationView bottomNav;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // The market and collection belong to an account — gate behind login.
        FantasyChessApplication app = (FantasyChessApplication) getApplication();
        if (!app.getRepository().isLoggedIn()) {
            android.content.Intent intent =
                    new android.content.Intent(this, LoginActivity.class);
            intent.putExtra(LoginActivity.EXTRA_AUTO_SKIP, false);
            startActivity(intent);
            finish();
            return;
        }

        setContentView(R.layout.activity_main);

        bottomNav = findViewById(R.id.bottom_nav);
        bottomNav.setOnItemSelectedListener(item -> {
            Fragment fragment;
            int id = item.getItemId();
            if (id == R.id.nav_squad) {
                fragment = new SquadFragment();
            } else if (id == R.id.nav_market) {
                fragment = new MarketFragment();
            } else if (id == R.id.nav_packs) {
                fragment = new PacksFragment();
            } else if (id == R.id.nav_collection) {
                fragment = new CollectionFragment();
            } else {
                fragment = new HomeFragment();
            }
            getSupportFragmentManager().popBackStack(
                    null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.main_container, fragment)
                    .commit();
            return true;
        });

        if (savedInstanceState == null) {
            bottomNav.setSelectedItemId(R.id.nav_home);
        }
    }

    /** Opens the player page on top of the current tab. */
    public void showPlayerDetail(int playerId) {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.main_container, PlayerDetailFragment.newInstance(playerId))
                .addToBackStack("player")
                .commit();
    }

    public void switchToTab(int menuItemId) {
        bottomNav.setSelectedItemId(menuItemId);
    }
}
