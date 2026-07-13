package com.fantasychess.israel.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fantasychess.israel.R;
import com.fantasychess.israel.data.model.OwnedCard;
import com.fantasychess.israel.data.model.Player;
import com.fantasychess.israel.data.repo.FantasyRepository;
import com.fantasychess.israel.ui.PlayerCardBinder;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Grid of owned cards. Three modes:
 *  - click mode (collection / squad picker): taps go to the listener
 *  - single-select (sell dialog) and multi-select (trade-offer dialog):
 *    taps toggle selection, read the result with {@link #getSelectedIds()}
 */
public class CardGridAdapter extends RecyclerView.Adapter<CardGridAdapter.CardViewHolder> {

    public interface Listener {
        void onCardClick(OwnedCard card);
    }

    private final List<OwnedCard> cards = new ArrayList<>();
    private final Set<String> selected = new LinkedHashSet<>();
    private FantasyRepository.State state;
    private final boolean dimCardsInSquad;
    private final Listener listener;
    private final int maxSelection; // 0 = click mode

    /** Click mode. */
    public CardGridAdapter(boolean dimCardsInSquad, Listener listener) {
        this.dimCardsInSquad = dimCardsInSquad;
        this.listener = listener;
        this.maxSelection = 0;
    }

    /** Selection mode: 1 = single select, larger = multi select. */
    public CardGridAdapter(int maxSelection) {
        this.dimCardsInSquad = false;
        this.listener = null;
        this.maxSelection = Math.max(1, maxSelection);
    }

    public void submit(FantasyRepository.State state, List<OwnedCard> newCards) {
        this.state = state;
        cards.clear();
        cards.addAll(newCards);
        selected.retainAll(idsOf(newCards));
        notifyDataSetChanged();
    }

    public List<String> getSelectedIds() {
        return new ArrayList<>(selected);
    }

    @NonNull
    @Override
    public CardViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_player_card, parent, false);
        return new CardViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CardViewHolder holder, int position) {
        OwnedCard card = cards.get(position);
        Player player = state == null ? null : state.playerById(card.playerId);
        if (player == null) {
            holder.itemView.setVisibility(View.GONE);
            return;
        }
        holder.itemView.setVisibility(View.VISIBLE);
        PlayerCardBinder.bind(holder.itemView, player, card.rarity, card.serial, null);

        if (maxSelection > 0) {
            boolean isSelected = selected.contains(card.cardId);
            holder.itemView.setAlpha(isSelected ? 1f : 0.5f);
            holder.itemView.setScaleX(isSelected ? 1f : 0.94f);
            holder.itemView.setScaleY(isSelected ? 1f : 0.94f);
            holder.itemView.setOnClickListener(v -> toggle(card.cardId));
            return;
        }

        boolean inSquad = state.squad.contains(card.cardId);
        boolean disabled = dimCardsInSquad && inSquad;
        holder.itemView.setAlpha(disabled ? 0.35f : 1f);
        holder.itemView.setScaleX(1f);
        holder.itemView.setScaleY(1f);
        holder.itemView.setOnClickListener(
                disabled || listener == null ? null : v -> listener.onCardClick(card));
    }

    private void toggle(String cardId) {
        if (selected.contains(cardId)) {
            selected.remove(cardId);
        } else {
            if (maxSelection == 1) selected.clear();
            if (selected.size() < maxSelection) selected.add(cardId);
        }
        notifyDataSetChanged();
    }

    private static Set<String> idsOf(List<OwnedCard> cards) {
        Set<String> ids = new LinkedHashSet<>();
        for (OwnedCard card : cards) {
            ids.add(card.cardId);
        }
        return ids;
    }

    @Override
    public int getItemCount() {
        return cards.size();
    }

    static class CardViewHolder extends RecyclerView.ViewHolder {
        CardViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }
}
