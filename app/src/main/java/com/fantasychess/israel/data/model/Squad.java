package com.fantasychess.israel.data.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The user's fantasy squad: exactly {@link #SQUAD_SIZE} slots, each holding
 * an owned card id (or null when empty), plus one captain slot that scores
 * x{@link #CAPTAIN_MULTIPLIER}.
 */
public class Squad {

    public static final int SQUAD_SIZE = 5;
    public static final double CAPTAIN_MULTIPLIER = 1.5;

    public final List<String> slots; // size SQUAD_SIZE, entries may be null
    public final int captainSlot;

    public Squad() {
        this(emptySlots(), 0);
    }

    public Squad(List<String> slots, int captainSlot) {
        List<String> normalized = new ArrayList<>(emptySlots());
        if (slots != null) {
            for (int i = 0; i < Math.min(slots.size(), SQUAD_SIZE); i++) {
                normalized.set(i, slots.get(i));
            }
        }
        this.slots = Collections.unmodifiableList(normalized);
        this.captainSlot = (captainSlot >= 0 && captainSlot < SQUAD_SIZE) ? captainSlot : 0;
    }

    private static List<String> emptySlots() {
        List<String> list = new ArrayList<>(SQUAD_SIZE);
        for (int i = 0; i < SQUAD_SIZE; i++) list.add(null);
        return list;
    }

    /** Returns a copy with {@code cardId} in {@code slot} (and removed from any other slot). */
    public Squad withSlot(int slot, String cardId) {
        List<String> next = new ArrayList<>(slots);
        if (cardId != null) {
            for (int i = 0; i < next.size(); i++) {
                if (cardId.equals(next.get(i))) next.set(i, null);
            }
        }
        next.set(slot, cardId);
        return new Squad(next, captainSlot);
    }

    public Squad withCaptain(int slot) {
        return new Squad(new ArrayList<>(slots), slot);
    }

    public boolean contains(String cardId) {
        return cardId != null && slots.contains(cardId);
    }
}
