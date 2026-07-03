package com.fantasychess.israel.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fantasychess.israel.MainActivity;
import com.fantasychess.israel.R;
import com.fantasychess.israel.data.model.OwnedCard;
import com.fantasychess.israel.data.model.Player;
import com.fantasychess.israel.data.model.Squad;
import com.fantasychess.israel.data.repo.FantasyRepository;
import com.fantasychess.israel.ui.MainViewModel;
import com.fantasychess.israel.ui.PlayerCardBinder;
import com.fantasychess.israel.ui.adapters.CardGridAdapter;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** The 5-slot lineup with captain selection and a card-picker bottom sheet. */
public class SquadFragment extends Fragment {

    private MainViewModel viewModel;
    private final int[] slotContainerIds = {
            R.id.squad_slot_0, R.id.squad_slot_1, R.id.squad_slot_2,
            R.id.squad_slot_3, R.id.squad_slot_4,
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_squad, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);
        viewModel.getState().observe(getViewLifecycleOwner(), state -> bind(view, state));
    }

    private void bind(View view, FantasyRepository.State state) {
        TextView total = view.findViewById(R.id.squad_total);
        total.setText(getString(R.string.squad_total, state.totalPoints()));

        for (int slot = 0; slot < Squad.SQUAD_SIZE; slot++) {
            bindSlot(view, state, slot);
        }
    }

    private void bindSlot(View view, FantasyRepository.State state, int slot) {
        LinearLayout container = view.findViewById(slotContainerIds[slot]);
        container.removeAllViews();

        OwnedCard card = state.cardById(state.squad.slots.get(slot));
        Player player = card == null ? null : state.playerById(card.playerId);

        if (player != null) {
            View cardView = getLayoutInflater().inflate(
                    R.layout.item_player_card, container, false);
            PlayerCardBinder.bind(cardView, player, card.rarity, card.serial,
                    state.slotPoints(slot));
            final int playerId = player.id;
            cardView.setOnClickListener(v ->
                    ((MainActivity) requireActivity()).showPlayerDetail(playerId));
            container.addView(cardView);

            View controls = getLayoutInflater().inflate(
                    R.layout.view_slot_controls, container, false);
            TextView captain = controls.findViewById(R.id.slot_btn_captain);
            boolean isCaptain = state.squad.captainSlot == slot;
            captain.setText(isCaptain
                    ? R.string.squad_captain_marked : R.string.squad_make_captain);
            captain.setTextColor(ContextCompat.getColor(requireContext(),
                    isCaptain ? R.color.gold : R.color.text_secondary));
            final int slotIndex = slot;
            captain.setOnClickListener(v -> viewModel.setCaptain(slotIndex));
            controls.findViewById(R.id.slot_btn_change)
                    .setOnClickListener(v -> showPicker(slotIndex, state));
            container.addView(controls);
        } else {
            View empty = getLayoutInflater().inflate(
                    R.layout.view_empty_slot, container, false);
            ((TextView) empty.findViewById(R.id.empty_label))
                    .setText(R.string.squad_pick_player);
            float density = getResources().getDisplayMetrics().density;
            empty.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, (int) (170 * density)));
            final int slotIndex = slot;
            empty.setOnClickListener(v -> showPicker(slotIndex, state));
            container.addView(empty);
        }
    }

    private void showPicker(int slot, FantasyRepository.State state) {
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        View content = getLayoutInflater().inflate(R.layout.dialog_card_picker, null);
        dialog.setContentView(content);

        RecyclerView grid = content.findViewById(R.id.picker_grid);
        grid.setLayoutManager(new GridLayoutManager(requireContext(), 3));
        CardGridAdapter adapter = new CardGridAdapter(true, card -> {
            viewModel.setSquadSlot(slot, card.cardId);
            dialog.dismiss();
        });
        List<OwnedCard> sorted = new ArrayList<>(state.ownedCards);
        sorted.sort(Comparator.comparingInt((OwnedCard c) -> {
            Player p = state.playerById(c.playerId);
            return p == null ? 0 : p.cardRating();
        }).reversed());
        adapter.submit(state, sorted);
        grid.setAdapter(adapter);

        content.findViewById(R.id.picker_clear).setOnClickListener(v -> {
            viewModel.setSquadSlot(slot, null);
            dialog.dismiss();
        });
        content.findViewById(R.id.picker_empty).setVisibility(
                state.ownedCards.isEmpty() ? View.VISIBLE : View.GONE);

        dialog.show();
    }
}
