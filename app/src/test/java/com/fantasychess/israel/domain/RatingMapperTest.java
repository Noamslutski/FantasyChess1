package com.fantasychess.israel.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RatingMapperTest {

    @Test
    public void entryLevelRatingMapsToOne() {
        assertEquals(1, RatingMapper.toCardRating(1000));
        assertEquals(1, RatingMapper.toCardRating(700)); // clamped
    }

    @Test
    public void eliteRatingMapsToHundred() {
        assertEquals(100, RatingMapper.toCardRating(2800));
        assertEquals(100, RatingMapper.toCardRating(3000)); // clamped
    }

    @Test
    public void midRangeIsLinear() {
        assertApprox(50, RatingMapper.toCardRating(1891), 1);
        assertApprox(71, RatingMapper.toCardRating(2273), 1);
    }

    @Test
    public void strongerPlayerNeverHasLowerCardRating() {
        int previous = 0;
        for (int rating = 900; rating <= 2900; rating += 25) {
            int card = RatingMapper.toCardRating(rating);
            assertTrue("card rating must be monotonic", card >= previous);
            assertTrue(card >= 1 && card <= 100);
            previous = card;
        }
    }

    private static void assertApprox(int expected, int actual, int tolerance) {
        assertTrue("expected " + expected + "±" + tolerance + " but was " + actual,
                Math.abs(expected - actual) <= tolerance);
    }
}
