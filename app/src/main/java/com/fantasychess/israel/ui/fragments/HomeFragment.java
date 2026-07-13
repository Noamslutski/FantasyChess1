package com.fantasychess.israel.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fantasychess.israel.MainActivity;
import com.fantasychess.israel.R;
import com.fantasychess.israel.data.model.DataSource;
import com.fantasychess.israel.data.model.OwnedCard;
import com.fantasychess.israel.data.model.Player;
import com.fantasychess.israel.data.model.Squad;
import com.fantasychess.israel.data.repo.FantasyRepository;
import com.fantasychess.israel.domain.FantasyScoring;
import com.fantasychess.israel.ui.MainViewModel;
import com.fantasychess.israel.ui.PlayerCardBinder;
import com.fantasychess.israel.ui.adapters.LeadersAdapter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Dashboard: game-week banner, squad preview and the week's top performers. */
public class HomeFragment extends Fragment {

    private MainViewModel viewModel;
    private LeadersAdapter leadersAdapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);

        leadersAdapter = new LeadersAdapter(player ->
                ((MainActivity) requireActivity()).showPlayerDetail(player.id));
        RecyclerView leaders = view.findViewById(R.id.home_leaders);
        leaders.setLayoutManager(new LinearLayoutManager(
                requireContext(), LinearLayoutManager.HORIZONTAL, false));
        leaders.setAdapter(leadersAdapter);

        view.findViewById(R.id.home_btn_squad).setOnClickListener(v ->
                ((MainActivity) requireActivity()).switchToTab(R.id.nav_squad));
        view.findViewById(R.id.home_btn_packs).setOnClickListener(v ->
                ((MainActivity) requireActivity()).switchToTab(R.id.nav_packs));
        view.findViewById(R.id.home_data_source).setOnClickListener(v -> viewModel.refresh());
        view.findViewById(R.id.home_search).setOnClickListener(v -> showSearchDialog());
        view.findViewById(R.id.home_logout).setOnClickListener(v -> logout());
        view.findViewById(R.id.home_pawns_container).setOnClickListener(v -> showBuyPawnsDialog());

        viewModel.getState().observe(getViewLifecycleOwner(), state -> bind(view, state));
    }

    private void logout() {
        viewModel.logout();
        requireActivity().finish();
        startActivity(new android.content.Intent(requireContext(), com.fantasychess.israel.LoginActivity.class)
                .putExtra(com.fantasychess.israel.LoginActivity.EXTRA_AUTO_SKIP, false));
    }

    private void showBuyPawnsDialog() {
        String[] options = {"1,000 🨅", "5,000 🨅", "10,000 🨅"};
        long[] amounts = {1000, 5000, 10000};
        
        new android.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.buy_pawns_dialog_title)
                .setItems(options, (dialog, which) -> {
                    viewModel.buyPawns(amounts[which]);
                })
                .setNegativeButton(R.string.common_back, null)
                .show();
    }

    private void showSearchDialog() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(requireContext());
        View view = getLayoutInflater().inflate(R.layout.dialog_search, null);
        builder.setView(view);

        android.widget.EditText input = view.findViewById(R.id.search_input);
        android.widget.ListView list = view.findViewById(R.id.search_results);
        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(
                requireContext(), android.R.layout.simple_list_item_1, new ArrayList<>());
        list.setAdapter(adapter);

        input.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            public void afterTextChanged(android.text.Editable s) {
                if (s.length() >= 2) {
                    viewModel.searchPlayers(s.toString(), results -> {
                        adapter.clear();
                        if (results != null) adapter.addAll(results);
                    });
                }
            }
        });

        android.app.AlertDialog dialog = builder.create();
        list.setOnItemClickListener((parent, v, position, id) -> {
            String item = adapter.getItem(position);
            if (item != null) {
                // Format is usually "Name [ID]"
                int start = item.lastIndexOf('[');
                int end = item.lastIndexOf(']');
                if (start != -1 && end > start) {
                    try {
                        int playerId = Integer.parseInt(item.substring(start + 1, end));
                        ((MainActivity) requireActivity()).showPlayerDetail(playerId);
                        dialog.dismiss();
                    } catch (NumberFormatException ignored) {}
                }
            }
        });

        dialog.show();
    }

    private void bind(View view, FantasyRepository.State state) {
        boolean showLoading = state.loading && state.roster.isEmpty();
        view.findViewById(R.id.home_loading).setVisibility(
                showLoading ? View.VISIBLE : View.GONE);
        view.findViewById(R.id.home_content).setVisibility(
                showLoading ? View.GONE : View.VISIBLE);
        if (showLoading) return;

        TextView gameweek = view.findViewById(R.id.home_gameweek);
        gameweek.setText(getString(R.string.home_gameweek, state.weekNumber));

        TextView points = view.findViewById(R.id.home_points);
        points.setText(String.valueOf(state.totalPoints()));

        TextView pawns = view.findViewById(R.id.home_pawns);
        pawns.setText(String.valueOf(state.pawns));

        TextView packsButton = view.findViewById(R.id.home_btn_packs);
        packsButton.setText(getString(R.string.home_packs_button,
                state.freePacks + state.proPacks));

        TextView dataSource = view.findViewById(R.id.home_data_source);
        if (state.dataSource == DataSource.LIVE_API) {
            dataSource.setText(R.string.home_data_live);
            dataSource.setTextColor(ContextCompat.getColor(requireContext(), R.color.gold));
        } else {
            dataSource.setText(R.string.home_data_sample);
            dataSource.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.text_secondary));
        }

        bindSquadRow(view, state);
        bindLeaders(state);
    }

    private void bindSquadRow(View view, FantasyRepository.State state) {
        LinearLayout row = view.findViewById(R.id.home_squad_row);
        row.removeAllViews();
        float density = getResources().getDisplayMetrics().density;

        for (int slot = 0; slot < Squad.SQUAD_SIZE; slot++) {
            OwnedCard card = state.cardById(state.squad.slots.get(slot));
            Player player = card == null ? null : state.playerById(card.playerId);

            View child;
            if (player != null) {
                child = getLayoutInflater().inflate(R.layout.item_squad_mini, row, false);
                child.setBackgroundResource(PlayerCardBinder.backgroundFor(card.rarity));
                ((TextView) child.findViewById(R.id.mini_rating))
                        .setText(String.valueOf(player.cardRating()));
                ((ImageView) child.findViewById(R.id.mini_avatar))
                        .setImageResource(PlayerCardBinder.avatarFor(player.gender));
                ((TextView) child.findViewById(R.id.mini_name)).setText(player.name);
                Integer slotPoints = state.slotPoints(slot);
                ((TextView) child.findViewById(R.id.mini_points)).setText(getString(
                        R.string.card_week_points, slotPoints == null ? 0 : slotPoints));
                final int playerId = player.id;
                child.setOnClickListener(v ->
                        ((MainActivity) requireActivity()).showPlayerDetail(playerId));
            } else {
                child = getLayoutInflater().inflate(R.layout.view_empty_slot, row, false);
                ((TextView) child.findViewById(R.id.empty_label))
                        .setText(R.string.squad_empty_slot);
                child.setOnClickListener(v ->
                        ((MainActivity) requireActivity()).switchToTab(R.id.nav_squad));
            }

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    0, (int) (118 * density), 1f);
            int margin = (int) (3 * density);
            params.setMargins(margin, 0, margin, 0);
            row.addView(child, params);
        }
    }

    private void bindLeaders(FantasyRepository.State state) {
        Map<Integer, Integer> points = new HashMap<>();
        for (Player player : state.roster) {
            points.put(player.id,
                    FantasyScoring.weekPoints(player, state.gamesFor(player.id)));
        }
        List<Player> sorted = new ArrayList<>(state.roster);
        sorted.sort(Comparator.comparingInt(
                (Player p) -> points.getOrDefault(p.id, 0)).reversed());
        leadersAdapter.submit(sorted.subList(0, Math.min(6, sorted.size())), points);
    }
}
