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
import java.util.List;

/** Grid of owned cards, used by the collection screen and the squad picker. */
public class CardGridAdapter extends RecyclerView.Adapter<CardGridAdapter.CardViewHolder> {

    public interface Listener {
        void onCardClick(OwnedCard card);
    }

    private final List<OwnedCard> cards = new ArrayList<>();
    private FantasyRepository.State state;
    private final boolean dimCardsInSquad;
    private final Listener listener;

    public CardGridAdapter(boolean dimCardsInSquad, Listener listener) {
        this.dimCardsInSquad = dimCardsInSquad;
        this.listener = listener;
    }

    public void submit(FantasyRepository.State state, List<OwnedCard> newCards) {
        this.state = state;
        cards.clear();
        cards.addAll(newCards);
        notifyDataSetChanged();
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
            holder.itemView.setVisibility(View.INVISIBLE);
            return;
        }
        holder.itemView.setVisibility(View.VISIBLE);
        PlayerCardBinder.bind(holder.itemView, player, card.rarity, card.serial, null);

        boolean inSquad = state.squad.contains(card.cardId);
        boolean disabled = dimCardsInSquad && inSquad;
        holder.itemView.setAlpha(disabled ? 0.35f : 1f);
        holder.itemView.setOnClickListener(disabled ? null : v -> listener.onCardClick(card));
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
