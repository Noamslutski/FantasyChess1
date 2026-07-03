package com.fantasychess.israel.domain;

/**
 * Maps an Israeli national rating (מדד) to the 1-100 fantasy card rating.
 *
 * The mapping is linear across the realistic national rating range:
 * 1000 (entry level) -> 1 and 2800 (world elite) -> 100, so for example a
 * 2260-rated candidate master lands at 71 and a 1450 club player at 26.
 */
public final class RatingMapper {

    public static final int MIN_NATIONAL_RATING = 1000;
    public static final int MAX_NATIONAL_RATING = 2800;

    private RatingMapper() {
    }

    public static int toCardRating(int nationalRating) {
        int clamped = Math.max(MIN_NATIONAL_RATING, Math.min(MAX_NATIONAL_RATING, nationalRating));
        double fraction = (clamped - MIN_NATIONAL_RATING)
                / (double) (MAX_NATIONAL_RATING - MIN_NATIONAL_RATING);
        int rating = (int) Math.round(1 + fraction * 99);
        return Math.max(1, Math.min(100, rating));
    }
}
