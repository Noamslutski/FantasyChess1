package com.fantasychess.israel.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fantasychess.israel.MainActivity;
import com.fantasychess.israel.R;
import com.fantasychess.israel.data.model.OwnedCard;
import com.fantasychess.israel.data.model.Player;
import com.fantasychess.israel.data.repo.FantasyRepository;
import com.fantasychess.israel.ui.MainViewModel;
import com.fantasychess.israel.ui.adapters.CardGridAdapter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** All cards the user owns, best players first. */
public class CollectionFragment extends Fragment {

    private MainViewModel viewModel;
    private CardGridAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_collection, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);

        adapter = new CardGridAdapter(false, card ->
                ((MainActivity) requireActivity()).showPlayerDetail(card.playerId));
        RecyclerView grid = view.findViewById(R.id.collection_grid);
        grid.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        grid.setAdapter(adapter);

        viewModel.getState().observe(getViewLifecycleOwner(), state -> bind(view, state));
    }

    private void bind(View view, FantasyRepository.State state) {
        TextView count = view.findViewById(R.id.collection_count);
        count.setText(getString(R.string.collection_count, state.ownedCards.size()));

        view.findViewById(R.id.collection_empty).setVisibility(
                state.ownedCards.isEmpty() ? View.VISIBLE : View.GONE);

        List<OwnedCard> sorted = new ArrayList<>(state.ownedCards);
        sorted.sort(Comparator
                .comparingInt((OwnedCard c) -> {
                    Player p = state.playerById(c.playerId);
                    return p == null ? 0 : p.cardRating();
                })
                .thenComparingInt((OwnedCard c) -> c.rarity == null ? -1 : c.rarity.ordinal())
                .reversed());
        adapter.submit(state, sorted);
    }
}
