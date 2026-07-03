package com.fantasychess.israel.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.fantasychess.israel.R;
import com.fantasychess.israel.data.model.Player;
import com.fantasychess.israel.data.model.TradeOffer;
import com.fantasychess.israel.data.repo.FantasyRepository;
import com.fantasychess.israel.ui.PlayerCardBinder;

import java.util.ArrayList;
import java.util.List;

/** The user's trade offers and their outcomes, newest first. */
public class OffersAdapter extends RecyclerView.Adapter<OffersAdapter.OfferViewHolder> {

    public interface Listener {
        void onAcceptCounter(TradeOffer offer);

        void onDeclineCounter(TradeOffer offer);
    }

    private final List<TradeOffer> offers = new ArrayList<>();
    private FantasyRepository.State state;
    private final Listener listener;

    public OffersAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submit(FantasyRepository.State state, List<TradeOffer> newOffers) {
        this.state = state;
        offers.clear();
        offers.addAll(newOffers);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public OfferViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_offer, parent, false);
        return new OfferViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull OfferViewHolder holder, int position) {
        TradeOffer offer = offers.get(position);
        View root = holder.itemView;
        Player target = state == null ? null : state.playerById(offer.targetPlayerId);
        String targetName = target == null
                ? "#" + offer.targetPlayerId : target.displayName();

        ((TextView) root.findViewById(R.id.offer_title)).setText(
                root.getContext().getString(R.string.offer_title_format, targetName,
                        root.getContext().getString(
                                PlayerCardBinder.rarityLabelFor(offer.targetRarity)),
                        PlayerCardBinder.serialText(offer.targetRarity, offer.targetSerial)));

        ((TextView) root.findViewById(R.id.offer_detail)).setText(
                root.getContext().getString(R.string.offer_detail_format,
                        offer.offeredCardIds.size(), offer.offeredPawns, offer.otherManager));

        TextView status = root.findViewById(R.id.offer_status);
        View actions = root.findViewById(R.id.offer_actions);
        actions.setVisibility(View.GONE);

        int statusText;
        int statusColor = R.color.text_secondary;
        switch (offer.status) {
            case ACCEPTED:
            case COUNTER_ACCEPTED:
                statusText = R.string.offer_status_accepted;
                statusColor = R.color.win_green;
                break;
            case REJECTED:
                statusText = R.string.offer_status_rejected;
                statusColor = R.color.loss_red;
                break;
            case COUNTERED:
                statusText = R.string.offer_status_countered;
                statusColor = R.color.gold;
                actions.setVisibility(View.VISIBLE);
                ((TextView) root.findViewById(R.id.offer_counter_text)).setText(
                        root.getContext().getString(R.string.offer_counter_format,
                                offer.counterExtraPawns));
                root.findViewById(R.id.offer_btn_accept)
                        .setOnClickListener(v -> listener.onAcceptCounter(offer));
                root.findViewById(R.id.offer_btn_decline)
                        .setOnClickListener(v -> listener.onDeclineCounter(offer));
                break;
            case DECLINED:
                statusText = R.string.offer_status_declined;
                break;
            default:
                statusText = R.string.offer_status_pending;
                break;
        }
        status.setText(statusText);
        status.setTextColor(ContextCompat.getColor(root.getContext(), statusColor));
    }

    @Override
    public int getItemCount() {
        return offers.size();
    }

    static class OfferViewHolder extends RecyclerView.ViewHolder {
        OfferViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }
}
