package com.fantasychess.israel.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fantasychess.israel.R;
import com.fantasychess.israel.data.model.Player;
import com.fantasychess.israel.data.model.Rarity;
import com.fantasychess.israel.ui.PlayerCardBinder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Horizontal strip of the week's best-scoring club players. */
public class LeadersAdapter extends RecyclerView.Adapter<LeadersAdapter.LeaderViewHolder> {

    public interface Listener {
        void onPlayerClick(Player player);
    }

    private final List<Player> players = new ArrayList<>();
    private final Map<Integer, Integer> weekPoints = new HashMap<>();
    private final Listener listener;

    public LeadersAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submit(List<Player> newPlayers, Map<Integer, Integer> newPoints) {
        players.clear();
        players.addAll(newPlayers);
        weekPoints.clear();
        weekPoints.putAll(newPoints);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public LeaderViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_player_card, parent, false);
        float density = parent.getResources().getDisplayMetrics().density;
        RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(
                (int) (136 * density), ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMarginEnd((int) (10 * density));
        view.setLayoutParams(params);
        return new LeaderViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull LeaderViewHolder holder, int position) {
        Player player = players.get(position);
        Integer points = weekPoints.get(player.id);
        PlayerCardBinder.bind(holder.itemView, player, Rarity.COMMON, null,
                points == null ? 0 : points);
        holder.itemView.setOnClickListener(v -> listener.onPlayerClick(player));
    }

    @Override
    public int getItemCount() {
        return players.size();
    }

    static class LeaderViewHolder extends RecyclerView.ViewHolder {
        LeaderViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }
}
