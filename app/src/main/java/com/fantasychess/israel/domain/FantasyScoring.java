package com.fantasychess.israel.domain;

import com.fantasychess.israel.data.model.GameResult;
import com.fantasychess.israel.data.model.Player;
import com.fantasychess.israel.data.model.Rarity;
import com.fantasychess.israel.data.model.Squad;
import com.fantasychess.israel.data.model.WeekGame;

import java.util.List;

/**
 * Sorare-style weekly scoring, adapted to over-the-board chess.
 *
 * Per game:
 *  - base points:       win 60, draw 30, loss 10 (showing up matters)
 *  - opponent strength: wins/draws against stronger opponents pay extra,
 *                       beating much weaker opponents pays a bit less
 *  - upset bonus:       +20 for beating an opponent rated 150+ above you
 *
 * Per week:
 *  - unbeaten bonus:    +10 for 2+ games without a loss
 *  - captain:           x1.5 on the captain's total
 *  - rarity bonus:      Limited +0%, Rare +5%, Super Rare +10%, Unique +20%
 */
public final class FantasyScoring {

    public static final int WIN_BASE = 60;
    public static final int DRAW_BASE = 30;
    public static final int LOSS_BASE = 10;
    public static final int UPSET_THRESHOLD = 150;
    public static final int UPSET_BONUS = 20;
    public static final int UNBEATEN_BONUS = 10;

    private FantasyScoring() {
    }

    public static int gamePoints(int playerNationalRating, WeekGame game) {
        int base;
        switch (game.result) {
            case WIN: base = WIN_BASE; break;
            case DRAW: base = DRAW_BASE; break;
            default: base = LOSS_BASE; break;
        }
        int diff = game.opponentRating - playerNationalRating;
        int strength = 0;
        if (game.result == GameResult.WIN) {
            strength = clamp((int) Math.round(diff * 0.05), -15, 25);
        } else if (game.result == GameResult.DRAW) {
            strength = clamp((int) Math.round(diff * 0.03), -10, 15);
        }
        int upset = (game.result == GameResult.WIN && diff >= UPSET_THRESHOLD) ? UPSET_BONUS : 0;
        return Math.max(0, base + strength + upset);
    }

    /** Raw weekly total for a player, before captain/rarity multipliers. */
    public static int weekPoints(Player player, List<WeekGame> games) {
        if (games == null || games.isEmpty()) return 0;
        int sum = 0;
        boolean lostAny = false;
        for (WeekGame game : games) {
            sum += gamePoints(player.nationalRating, game);
            if (game.result == GameResult.LOSS) lostAny = true;
        }
        if (games.size() >= 2 && !lostAny) sum += UNBEATEN_BONUS;
        return sum;
    }

    /** Final score for one squad slot, applying rarity and captain multipliers. */
    public static int slotPoints(Player player, List<WeekGame> games,
                                 Rarity rarity, boolean isCaptain) {
        double bonus = (rarity == null) ? 0.0 : rarity.scoreBonus;
        double points = weekPoints(player, games) * (1.0 + bonus);
        if (isCaptain) points *= Squad.CAPTAIN_MULTIPLIER;
        return (int) Math.round(points);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
