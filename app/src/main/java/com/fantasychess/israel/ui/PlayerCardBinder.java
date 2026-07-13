package com.fantasychess.israel.ui;

import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.fantasychess.israel.R;
import com.fantasychess.israel.data.model.Gender;
import com.fantasychess.israel.data.model.Player;
import com.fantasychess.israel.data.model.Rarity;

/**
 * Binds a player + rarity to the Sorare-style card layout
 * ({@code item_player_card.xml}), which is reused across the collection grid,
 * the picker, the market, the pack reveal and the player page.
 */
public final class PlayerCardBinder {

    private PlayerCardBinder() {
    }

    public static void bind(View card, Player player, Rarity rarity,
                            Integer serial, Integer weekPoints) {
        card.setBackgroundResource(backgroundFor(rarity));
        int accent = ContextCompat.getColor(card.getContext(), accentColorFor(rarity));

        TextView rating = card.findViewById(R.id.card_rating);
        rating.setText(String.valueOf(player.cardRating()));

        TextView rarityLabel = card.findViewById(R.id.card_rarity);
        rarityLabel.setText(rarityLabelFor(rarity));
        rarityLabel.setTextColor(accent);

        ImageView avatar = card.findViewById(R.id.card_avatar);
        avatar.setImageResource(avatarFor(player.gender));

        TextView name = card.findViewById(R.id.card_name);
        name.setText(player.displayName());

        TextView club = card.findViewById(R.id.card_club);
        club.setText(player.club);

        TextView national = card.findViewById(R.id.card_national);
        national.setText(card.getContext().getString(
                R.string.card_national_rating, player.nationalRating));

        TextView serialView = card.findViewById(R.id.card_serial);
        if (serial != null) {
            serialView.setVisibility(View.VISIBLE);
            serialView.setText(serialText(rarity, serial));
            serialView.setTextColor(accent);
        } else {
            serialView.setVisibility(View.INVISIBLE);
        }

        TextView points = card.findViewById(R.id.card_points);
        if (weekPoints != null) {
            points.setVisibility(View.VISIBLE);
            points.setText(card.getContext().getString(R.string.card_week_points, weekPoints));
        } else {
            points.setVisibility(View.GONE);
        }
    }

    /** Scarcity-aware serial: "#3/100" for capped tiers, "#3" for commons. */
    public static String serialText(Rarity rarity, int serial) {
        if (rarity == null) rarity = Rarity.COMMON;
        return rarity.mintLimitPerSeason > 0
                ? "#" + serial + "/" + rarity.mintLimitPerSeason
                : "#" + serial;
    }

    public static int backgroundFor(Rarity rarity) {
        if (rarity == null) return R.drawable.bg_card_common;
        switch (rarity) {
            case PRO: return R.drawable.bg_card_pro;
            case RARE: return R.drawable.bg_card_rare;
            case SUPER_RARE: return R.drawable.bg_card_super_rare;
            case UNIQUE: return R.drawable.bg_card_unique;
            default: return R.drawable.bg_card_common;
        }
    }

    public static int accentColorFor(Rarity rarity) {
        if (rarity == null) return R.color.common_accent;
        switch (rarity) {
            case PRO: return R.color.pro_accent;
            case RARE: return R.color.rare_accent;
            case SUPER_RARE: return R.color.super_rare_accent;
            case UNIQUE: return R.color.unique_accent;
            default: return R.color.common_accent;
        }
    }

    public static int rarityLabelFor(Rarity rarity) {
        if (rarity == null) return R.string.rarity_common;
        switch (rarity) {
            case PRO: return R.string.rarity_pro;
            case RARE: return R.string.rarity_rare;
            case SUPER_RARE: return R.string.rarity_super_rare;
            case UNIQUE: return R.string.rarity_unique;
            default: return R.string.rarity_common;
        }
    }

    public static int avatarFor(Gender gender) {
        return gender == Gender.GIRL ? R.drawable.avatar_girl : R.drawable.avatar_boy;
    }
}
