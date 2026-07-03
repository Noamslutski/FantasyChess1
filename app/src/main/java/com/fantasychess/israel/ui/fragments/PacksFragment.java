package com.fantasychess.israel.ui.fragments;

import android.app.Dialog;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.fantasychess.israel.R;
import com.fantasychess.israel.data.model.OwnedCard;
import com.fantasychess.israel.data.model.Player;
import com.fantasychess.israel.data.repo.FantasyRepository;
import com.fantasychess.israel.domain.PackGenerator;
import com.fantasychess.israel.ui.MainViewModel;
import com.fantasychess.israel.ui.PlayerCardBinder;

import java.util.List;

/**
 * Pack shop: free gray packs (starter + weekly), rewarded-ad packs (commons
 * with a chance of Pro), and gold Pro packs bought with Pawns.
 */
public class PacksFragment extends Fragment {

    private MainViewModel viewModel;
    private Dialog revealDialog;
    private Dialog adDialog;
    private CountDownTimer adTimer;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_packs, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);

        view.findViewById(R.id.packs_btn_open_free)
                .setOnClickListener(v -> viewModel.openFreePack());
        view.findViewById(R.id.packs_btn_claim)
                .setOnClickListener(v -> viewModel.claimWeeklyPack());
        view.findViewById(R.id.packs_btn_watch_ad).setOnClickListener(v -> showAd());
        view.findViewById(R.id.packs_btn_open_pro)
                .setOnClickListener(v -> viewModel.openProPack());
        view.findViewById(R.id.packs_btn_buy_pro)
                .setOnClickListener(v -> viewModel.buyProPack());

        viewModel.getState().observe(getViewLifecycleOwner(), state -> bind(view, state));
        viewModel.getLastOpenedPack().observe(getViewLifecycleOwner(), this::showReveal);
        viewModel.getEvents().observe(getViewLifecycleOwner(), resId -> {
            if (resId != null) {
                Toast.makeText(requireContext(), resId, Toast.LENGTH_SHORT).show();
                viewModel.clearEvent();
            }
        });
    }

    @Override
    public void onDestroyView() {
        if (revealDialog != null) {
            revealDialog.dismiss();
            revealDialog = null;
        }
        dismissAd();
        super.onDestroyView();
    }

    private void bind(View view, FantasyRepository.State state) {
        ((TextView) view.findViewById(R.id.packs_pawns))
                .setText(String.valueOf(state.pawns));

        // Free (gray) packs
        ((TextView) view.findViewById(R.id.packs_free_count)).setText(
                getString(R.string.packs_available, state.freePacks));
        view.findViewById(R.id.packs_btn_open_free).setEnabled(state.freePacks > 0);
        TextView weekly = view.findViewById(R.id.packs_weekly_status);
        Button claim = view.findViewById(R.id.packs_btn_claim);
        if (state.weeklyPackReady) {
            weekly.setText(R.string.packs_weekly_ready);
            claim.setVisibility(View.VISIBLE);
        } else {
            weekly.setText(R.string.packs_weekly_claimed);
            claim.setVisibility(View.GONE);
        }

        // Ad packs
        ((TextView) view.findViewById(R.id.packs_ads_left)).setText(
                getString(R.string.packs_ads_left, state.adsLeftToday));
        view.findViewById(R.id.packs_btn_watch_ad).setEnabled(state.adsLeftToday > 0);

        // Pro (gold) packs
        ((TextView) view.findViewById(R.id.packs_pro_count)).setText(
                getString(R.string.packs_available, state.proPacks));
        view.findViewById(R.id.packs_btn_open_pro).setEnabled(state.proPacks > 0);
        ((Button) view.findViewById(R.id.packs_btn_buy_pro)).setText(
                getString(R.string.packs_buy_pro, PackGenerator.PRO_PACK_PRICE_PAWNS));
    }

    /**
     * Simulated rewarded ad: a short countdown, then the reward. To use a real
     * ad network (e.g. AdMob rewarded ads), replace the body of this method
     * with the SDK's show() call and invoke viewModel.grantAdReward() from the
     * onUserEarnedReward callback.
     */
    private void showAd() {
        View content = getLayoutInflater().inflate(R.layout.dialog_ad, null);
        TextView countdown = content.findViewById(R.id.ad_countdown);

        adDialog = new AlertDialog.Builder(requireContext())
                .setView(content)
                .setCancelable(false)
                .create();
        adDialog.show();

        adTimer = new CountDownTimer(5_000, 1_000) {
            @Override
            public void onTick(long millisUntilFinished) {
                countdown.setText(getString(R.string.ad_countdown,
                        (millisUntilFinished / 1_000) + 1));
            }

            @Override
            public void onFinish() {
                dismissAd();
                viewModel.grantAdReward();
            }
        }.start();
    }

    private void dismissAd() {
        if (adTimer != null) {
            adTimer.cancel();
            adTimer = null;
        }
        if (adDialog != null) {
            adDialog.dismiss();
            adDialog = null;
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
