package com.fantasychess.israel.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.fantasychess.israel.R;
import com.fantasychess.israel.data.model.MarketListing;
import com.fantasychess.israel.data.model.Player;
import com.fantasychess.israel.data.repo.FantasyRepository;
import com.fantasychess.israel.ui.PlayerCardBinder;

import java.util.ArrayList;
import java.util.List;

/** Transfer-market listing rows: card summary, seller, sale type, price. */
public class MarketAdapter extends RecyclerView.Adapter<MarketAdapter.ListingViewHolder> {

    public interface Listener {
        void onListingClick(MarketListing listing);
    }

    private final List<MarketListing> listings = new ArrayList<>();
    private FantasyRepository.State state;
    private final Listener listener;

    public MarketAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submit(FantasyRepository.State state, List<MarketListing> newListings) {
        this.state = state;
        listings.clear();
        listings.addAll(newListings);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ListingViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_listing, parent, false);
        return new ListingViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ListingViewHolder holder, int position) {
        MarketListing listing = listings.get(position);
        Player player = state == null ? null : state.playerById(listing.playerId);
        if (player == null) {
            holder.itemView.setVisibility(View.GONE);
            return;
        }
        holder.itemView.setVisibility(View.VISIBLE);
        View root = holder.itemView;

        ((ImageView) root.findViewById(R.id.listing_avatar))
                .setImageResource(PlayerCardBinder.avatarFor(player.gender));
        ((TextView) root.findViewById(R.id.listing_name)).setText(
                player.displayName() + " · " + player.cardRating());

        TextView rarity = root.findViewById(R.id.listing_rarity);
        rarity.setText(root.getContext().getString(
                PlayerCardBinder.rarityLabelFor(listing.rarity)) + " "
                + PlayerCardBinder.serialText(listing.rarity, listing.serial));
        rarity.setTextColor(ContextCompat.getColor(root.getContext(),
                PlayerCardBinder.accentColorFor(listing.rarity)));

        TextView meta = root.findViewById(R.id.listing_meta);
        if (listing.type == MarketListing.Type.AUCTION) {
            long hoursLeft = Math.max(0,
                    (listing.endsAtEpochMs - System.currentTimeMillis()) / 3_600_000L);
            String time = hoursLeft > 0
                    ? root.getContext().getString(R.string.market_hours_left, hoursLeft)
                    : root.getContext().getString(R.string.market_ending_soon);
            meta.setText(root.getContext().getString(R.string.market_auction)
                    + " · " + time + " · " + listing.sellerName);
        } else {
            meta.setText(root.getContext().getString(R.string.market_direct_sale)
                    + " · " + listing.sellerName);
        }

        ((TextView) root.findViewById(R.id.listing_price))
                .setText(String.valueOf(listing.price));

        TextView badge = root.findViewById(R.id.listing_badge);
        if (listing.mine) {
            badge.setVisibility(View.VISIBLE);
            badge.setText(R.string.market_my_listing);
        } else if (listing.myBid > 0) {
            badge.setVisibility(View.VISIBLE);
            badge.setText(R.string.market_my_bid_leading);
        } else {
            badge.setVisibility(View.GONE);
        }

        root.setOnClickListener(v -> listener.onListingClick(listing));
    }

    @Override
    public int getItemCount() {
        return listings.size();
    }

    static class ListingViewHolder extends RecyclerView.ViewHolder {
        ListingViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }
}
