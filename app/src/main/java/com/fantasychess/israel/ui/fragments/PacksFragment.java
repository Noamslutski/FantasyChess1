package com.fantasychess.israel.ui.fragments;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.fantasychess.israel.R;
import com.fantasychess.israel.data.model.OwnedCard;
import com.fantasychess.israel.data.model.Player;
import com.fantasychess.israel.data.repo.FantasyRepository;
import com.fantasychess.israel.ui.MainViewModel;
import com.fantasychess.israel.ui.PlayerCardBinder;

import java.util.List;

/** Pack inventory: open packs with a staggered card-reveal animation. */
public class PacksFragment extends Fragment {

    private MainViewModel viewModel;
    private Dialog revealDialog;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_packs, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);

        view.findViewById(R.id.packs_btn_open).setOnClickListener(v -> viewModel.openPack());
        view.findViewById(R.id.packs_btn_claim)
                .setOnClickListener(v -> viewModel.claimWeeklyPack());

        viewModel.getState().observe(getViewLifecycleOwner(), state -> bind(view, state));
        viewModel.getLastOpenedPack().observe(getViewLifecycleOwner(), this::showReveal);
    }

    @Override
    public void onDestroyView() {
        if (revealDialog != null) {
            revealDialog.dismiss();
            revealDialog = null;
        }
        super.onDestroyView();
    }

    private void bind(View view, FantasyRepository.State state) {
        TextView available = view.findViewById(R.id.packs_available);
        available.setText(getString(R.string.packs_available, state.packsAvailable));

        Button open = view.findViewById(R.id.packs_btn_open);
        open.setEnabled(state.packsAvailable > 0);

        view.findViewById(R.id.packs_none).setVisibility(
                state.packsAvailable == 0 ? View.VISIBLE : View.GONE);

        TextView weeklyStatus = view.findViewById(R.id.packs_weekly_status);
        Button claim = view.findViewById(R.id.packs_btn_claim);
        if (state.weeklyPackReady) {
            weeklyStatus.setText(R.string.packs_weekly_ready);
            claim.setVisibility(View.VISIBLE);
        } else {
            weeklyStatus.setText(R.string.packs_weekly_claimed);
            claim.setVisibility(View.GONE);
        }
    }

    private void showReveal(@Nullable List<OwnedCard> cards) {
        if (cards == null) {
            if (revealDialog != null) {
                revealDialog.dismiss();
                revealDialog = null;
            }
            return;
        }
        FantasyRepository.State state = viewModel.getState().getValue();
        if (state == null) return;

        View content = getLayoutInflater().inflate(R.layout.dialog_pack_reveal, null);
        LinearLayout cardsRow = content.findViewById(R.id.reveal_cards);
        float density = getResources().getDisplayMetrics().density;

        for (int i = 0; i < cards.size(); i++) {
            OwnedCard card = cards.get(i);
            Player player = state.playerById(card.playerId);
            if (player == null) continue;

            View cardView = getLayoutInflater().inflate(
                    R.layout.item_player_card, cardsRow, false);
            PlayerCardBinder.bind(cardView, player, card.rarity, card.serial, null);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            int margin = (int) (4 * density);
            params.setMargins(margin, 0, margin, 0);
            cardsRow.addView(cardView, params);

            // Staggered reveal: cards pop in one after another.
            cardView.setAlpha(0f);
            cardView.setScaleX(0.6f);
            cardView.setScaleY(0.6f);
            cardView.animate()
                    .alpha(1f).scaleX(1f).scaleY(1f)
                    .setStartDelay(300L * i)
                    .setDuration(350)
                    .start();
        }

        content.findViewById(R.id.reveal_btn_done)
                .setOnClickListener(v -> viewModel.dismissPackReveal());

        revealDialog = new AlertDialog.Builder(requireContext())
                .setView(content)
                .setOnDismissListener(d -> viewModel.dismissPackReveal())
                .create();
        revealDialog.show();
    }
}
