package com.fantasychess.israel.ui.fragments;

import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fantasychess.israel.R;
import com.fantasychess.israel.data.model.MarketListing;
import com.fantasychess.israel.data.model.OwnedCard;
import com.fantasychess.israel.data.model.Player;
import com.fantasychess.israel.data.repo.FantasyRepository;
import com.fantasychess.israel.domain.CardValuator;
import com.fantasychess.israel.ui.MainViewModel;
import com.fantasychess.israel.ui.PlayerCardBinder;
import com.fantasychess.israel.ui.adapters.CardGridAdapter;
import com.fantasychess.israel.ui.adapters.MarketAdapter;
import com.fantasychess.israel.ui.adapters.OffersAdapter;

import java.util.List;

/**
 * The transfer market: buy cards from other managers via auctions or direct
 * sales, send trade offers (cards + Pawns) with counter-offer negotiation,
 * and list your own cards for sale.
 */
public class MarketFragment extends Fragment {

    private MainViewModel viewModel;
    private MarketAdapter marketAdapter;
    private OffersAdapter offersAdapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_market, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);

        marketAdapter = new MarketAdapter(this::showListingDialog);
        RecyclerView listings = view.findViewById(R.id.market_listings);
        listings.setLayoutManager(new LinearLayoutManager(requireContext()));
        listings.setNestedScrollingEnabled(false);
        listings.setAdapter(marketAdapter);

        offersAdapter = new OffersAdapter(new OffersAdapter.Listener() {
            @Override
            public void onAcceptCounter(com.fantasychess.israel.data.model.TradeOffer offer) {
                viewModel.acceptCounter(offer.id);
            }

            @Override
            public void onDeclineCounter(com.fantasychess.israel.data.model.TradeOffer offer) {
                viewModel.declineCounter(offer.id);
            }
        });
        RecyclerView offers = view.findViewById(R.id.market_offers);
        offers.setLayoutManager(new LinearLayoutManager(requireContext()));
        offers.setNestedScrollingEnabled(false);
        offers.setAdapter(offersAdapter);

        view.findViewById(R.id.market_btn_sell).setOnClickListener(v -> showSellDialog());

        viewModel.getState().observe(getViewLifecycleOwner(), this::bind);
        viewModel.getEvents().observe(getViewLifecycleOwner(), resId -> {
            if (resId != null) {
                Toast.makeText(requireContext(), resId, Toast.LENGTH_SHORT).show();
                viewModel.clearEvent();
            }
        });
        viewModel.refreshMarket();
    }

    private void bind(FantasyRepository.State state) {
        View view = getView();
        if (view == null) return;
        ((TextView) view.findViewById(R.id.market_pawns))
                .setText(String.valueOf(state.pawns));

        List<MarketListing> sorted = new java.util.ArrayList<>(state.listings);
        sorted.sort((a, b) -> {
            if (a.mine != b.mine) return a.mine ? -1 : 1;         // my listings first
            if (a.rarity != b.rarity) {
                int oa = a.rarity == null ? -1 : a.rarity.ordinal();
                int ob = b.rarity == null ? -1 : b.rarity.ordinal();
                return ob - oa;   // rarest first
            }
            return Long.compare(b.createdAtEpochMs, a.createdAtEpochMs);
        });
        marketAdapter.submit(state, sorted);

        offersAdapter.submit(state, state.offers);
        view.findViewById(R.id.market_offers_title).setVisibility(
                state.offers.isEmpty() ? View.GONE : View.VISIBLE);
    }

    // ---- listing dialog: buy / bid / trade offer -----------------------------

    private void showListingDialog(MarketListing listing) {
        FantasyRepository.State state = viewModel.getState().getValue();
        if (state == null) return;
        Player player = state.playerById(listing.playerId);
        if (player == null) return;

        View content = getLayoutInflater().inflate(R.layout.dialog_listing_actions, null);
        View card = content.findViewById(R.id.listing_dialog_card);
        PlayerCardBinder.bind(card, player, listing.rarity, listing.serial, null);

        ((TextView) content.findViewById(R.id.listing_dialog_price)).setText(
                getString(listing.type == MarketListing.Type.AUCTION
                                ? R.string.market_current_bid_format
                                : R.string.market_ask_price_format,
                        listing.price));

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(content).create();

        View buyButton = content.findViewById(R.id.listing_dialog_buy);
        View bidRow = content.findViewById(R.id.listing_dialog_bid_row);
        View offerButton = content.findViewById(R.id.listing_dialog_offer);
        View cancelButton = content.findViewById(R.id.listing_dialog_cancel_listing);

        if (listing.mine) {
            buyButton.setVisibility(View.GONE);
            bidRow.setVisibility(View.GONE);
            offerButton.setVisibility(View.GONE);
            cancelButton.setVisibility(View.VISIBLE);
            cancelButton.setOnClickListener(v -> {
                viewModel.cancelMyListing(listing.id);
                dialog.dismiss();
            });
        } else if (listing.type == MarketListing.Type.DIRECT_SALE) {
            bidRow.setVisibility(View.GONE);
            cancelButton.setVisibility(View.GONE);
            buyButton.setOnClickListener(v -> {
                viewModel.buyNow(listing.id);
                dialog.dismiss();
            });
            offerButton.setOnClickListener(v -> {
                dialog.dismiss();
                showOfferDialog(listing);
            });
        } else {
            buyButton.setVisibility(View.GONE);
            cancelButton.setVisibility(View.GONE);
            EditText bidInput = content.findViewById(R.id.listing_dialog_bid_amount);
            bidInput.setInputType(InputType.TYPE_CLASS_NUMBER);
            bidInput.setHint(getString(R.string.market_bid_hint, listing.price));
            content.findViewById(R.id.listing_dialog_bid_button).setOnClickListener(v -> {
                long amount = parseLong(bidInput.getText().toString());
                if (amount > 0) {
                    viewModel.placeBid(listing.id, amount);
                    dialog.dismiss();
                }
            });
            offerButton.setOnClickListener(v -> {
                dialog.dismiss();
                showOfferDialog(listing);
            });
        }
        dialog.show();
    }

    // ---- trade offer: my cards + Pawns ---------------------------------------

    private void showOfferDialog(MarketListing listing) {
        FantasyRepository.State state = viewModel.getState().getValue();
        if (state == null) return;

        View content = getLayoutInflater().inflate(R.layout.dialog_make_offer, null);
        RecyclerView grid = content.findViewById(R.id.offer_grid);
        grid.setLayoutManager(new GridLayoutManager(requireContext(), 3));
        CardGridAdapter adapter = new CardGridAdapter(Integer.MAX_VALUE);
        List<OwnedCard> tradable = state.tradableCards();
        adapter.submit(state, tradable);
        grid.setAdapter(adapter);

        content.findViewById(R.id.offer_empty).setVisibility(
                tradable.isEmpty() ? View.VISIBLE : View.GONE);

        EditText pawnsInput = content.findViewById(R.id.offer_pawns);
        pawnsInput.setInputType(InputType.TYPE_CLASS_NUMBER);

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(content).create();
        content.findViewById(R.id.offer_send).setOnClickListener(v -> {
            long pawns = parseLong(pawnsInput.getText().toString());
            List<String> cardIds = adapter.getSelectedIds();
            if (pawns <= 0 && cardIds.isEmpty()) return;
            viewModel.makeOffer(listing.id, cardIds, Math.max(0, pawns));
            dialog.dismiss();
        });
        dialog.show();
    }

    // ---- sell one of my cards -------------------------------------------------

    private void showSellDialog() {
        FantasyRepository.State state = viewModel.getState().getValue();
        if (state == null) return;

        View content = getLayoutInflater().inflate(R.layout.dialog_sell_card, null);
        RecyclerView grid = content.findViewById(R.id.sell_grid);
        grid.setLayoutManager(new GridLayoutManager(requireContext(), 3));
        CardGridAdapter adapter = new CardGridAdapter(1);
        List<OwnedCard> tradable = state.tradableCards();
        adapter.submit(state, tradable);
        grid.setAdapter(adapter);

        content.findViewById(R.id.sell_empty).setVisibility(
                tradable.isEmpty() ? View.VISIBLE : View.GONE);

        EditText priceInput = content.findViewById(R.id.sell_price);
        priceInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        RadioGroup typeGroup = content.findViewById(R.id.sell_type);

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(content).create();
        content.findViewById(R.id.sell_confirm).setOnClickListener(v -> {
            List<String> picked = adapter.getSelectedIds();
            if (picked.isEmpty()) return;
            OwnedCard card = state.cardById(picked.get(0));
            Player player = card == null ? null : state.playerById(card.playerId);
            if (card == null || player == null) return;
            long price = parseLong(priceInput.getText().toString());
            if (price <= 0) price = CardValuator.value(player, card.rarity);
            boolean auction = typeGroup.getCheckedRadioButtonId() == R.id.sell_type_auction;
            viewModel.listCard(card.cardId, price, auction);
            dialog.dismiss();
        });
        dialog.show();
    }

    private static long parseLong(String text) {
        try {
            return Long.parseLong(text.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
