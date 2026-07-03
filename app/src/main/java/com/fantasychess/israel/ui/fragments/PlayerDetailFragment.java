package com.fantasychess.israel.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.fantasychess.israel.R;
import com.fantasychess.israel.data.model.GameResult;
import com.fantasychess.israel.data.model.OwnedCard;
import com.fantasychess.israel.data.model.Player;
import com.fantasychess.israel.data.model.Rarity;
import com.fantasychess.israel.data.model.WeekGame;
import com.fantasychess.israel.data.repo.FantasyRepository;
import com.fantasychess.israel.domain.FantasyScoring;
import com.fantasychess.israel.ui.MainViewModel;
import com.fantasychess.israel.ui.PlayerCardBinder;

import java.util.List;

/**
 * Player page: the best card the user owns of this player (or a Limited
 * preview), federation ratings, and every game played this week with the
 * fantasy points earned per game.
 */
public class PlayerDetailFragment extends Fragment {

    private static final String ARG_PLAYER_ID = "player_id";

    public static PlayerDetailFragment newInstance(int playerId) {
        PlayerDetailFragment fragment = new PlayerDetailFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_PLAYER_ID, playerId);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_player_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        MainViewModel viewModel =
                new ViewModelProvider(requireActivity()).get(MainViewModel.class);
        int playerId = requireArguments().getInt(ARG_PLAYER_ID);

        view.findViewById(R.id.detail_back).setOnClickListener(v ->
                requireActivity().getOnBackPressedDispatcher().onBackPressed());

        viewModel.getState().observe(getViewLifecycleOwner(),
                state -> bind(view, state, playerId));
    }

    private void bind(View view, FantasyRepository.State state, int playerId) {
        Player player = state.playerById(playerId);
        if (player == null) return;

        OwnedCard best = null;
        for (OwnedCard card : state.ownedCards) {
            if (card.playerId != playerId) continue;
            if (best == null || card.rarity.ordinal() > best.rarity.ordinal()) best = card;
        }
        List<WeekGame> games = state.gamesFor(playerId);

        FrameLayout cardContainer = view.findViewById(R.id.detail_card_container);
        cardContainer.removeAllViews();
        View cardView = getLayoutInflater().inflate(
                R.layout.item_player_card, cardContainer, false);
        PlayerCardBinder.bind(cardView, player,
                best == null ? Rarity.COMMON : best.rarity,
                best == null ? null : best.serial,
                FantasyScoring.weekPoints(player, games));
        cardContainer.addView(cardView);

        view.findViewById(R.id.detail_not_owned).setVisibility(
                best == null ? View.VISIBLE : View.GONE);

        ((TextView) view.findViewById(R.id.stat_national))
                .setText(String.valueOf(player.nationalRating));
        ((TextView) view.findViewById(R.id.stat_fide))
                .setText(player.fideRating == null ? "—" : String.valueOf(player.fideRating));
        ((TextView) view.findViewById(R.id.stat_card))
                .setText(String.valueOf(player.cardRating()));

        bindGames(view, player, games);
    }

    private void bindGames(View view, Player player, List<WeekGame> games) {
        LinearLayout container = view.findViewById(R.id.detail_games_container);
        container.removeAllViews();

        view.findViewById(R.id.detail_no_games).setVisibility(
                games.isEmpty() ? View.VISIBLE : View.GONE);
        float density = getResources().getDisplayMetrics().density;

        for (WeekGame game : games) {
            View row = getLayoutInflater().inflate(R.layout.item_game_row, container, false);

            ((TextView) row.findViewById(R.id.game_opponent))
                    .setText(getString(R.string.game_vs, game.opponentName));

            StringBuilder meta = new StringBuilder(game.dateIso);
            if (!game.competition.isEmpty()) meta.append(" · ").append(game.competition);
            if (!game.opponentClub.isEmpty()) meta.append(" · ").append(game.opponentClub);
            meta.append(" · (").append(game.opponentRating).append(")");
            ((TextView) row.findViewById(R.id.game_meta)).setText(meta.toString());

            TextView result = row.findViewById(R.id.game_result);
            int resultText;
            int resultColor;
            if (game.result == GameResult.WIN) {
                resultText = R.string.result_win;
                resultColor = R.color.win_green;
            } else if (game.result == GameResult.DRAW) {
                resultText = R.string.result_draw;
                resultColor = R.color.draw_gray;
            } else {
                resultText = R.string.result_loss;
                resultColor = R.color.loss_red;
            }
            result.setText(resultText);
            result.setTextColor(ContextCompat.getColor(requireContext(), resultColor));

            ((TextView) row.findViewById(R.id.game_points)).setText(
                    "+" + FantasyScoring.gamePoints(player.nationalRating, game));

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.bottomMargin = (int) (8 * density);
            container.addView(row, params);
        }
    }
}
