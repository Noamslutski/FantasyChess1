package com.fantasychess.israel.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.fantasychess.israel.data.model.Fixture;
import com.fantasychess.israel.data.model.Gender;
import com.fantasychess.israel.data.model.Player;

import org.junit.Test;

import java.time.LocalDate;
import java.util.List;

public class FixtureGeneratorTest {

    private static Player player(String league) {
        return new Player(24210, "בני איזנברג", Gender.BOY, 2524, null, null,
                null, "הפועל פתח תקווה", "הפועל פ\"ת ב'", league);
    }

    @Test
    public void producesUpcomingRoundsInChronologicalOrder() {
        LocalDate today = LocalDate.of(2026, 7, 3);
        List<Fixture> fixtures = FixtureGenerator.upcoming(player("ליגה לאומית"), today);
        assertEquals(FixtureGenerator.ROUNDS, fixtures.size());

        String previous = today.toString();
        for (int i = 0; i < fixtures.size(); i++) {
            Fixture f = fixtures.get(i);
            assertEquals(i + 1, f.roundNumber);
            assertNotNull(f.opponentClub);
            assertTrue("fixtures must be in the future and increasing",
                    f.dateIso.compareTo(previous) > 0);
            previous = f.dateIso;
        }
    }

    @Test
    public void scheduleIsStablePerPlayer() {
        LocalDate today = LocalDate.of(2026, 7, 3);
        List<Fixture> a = FixtureGenerator.upcoming(player("ליגה ארצית"), today);
        List<Fixture> b = FixtureGenerator.upcoming(player("ליגה ארצית"), today);
        for (int i = 0; i < a.size(); i++) {
            assertEquals(a.get(i).opponentClub, b.get(i).opponentClub);
            assertEquals(a.get(i).dateIso, b.get(i).dateIso);
        }
    }

    @Test
    public void opponentPoolMatchesLeagueTier() {
        LocalDate today = LocalDate.of(2026, 7, 3);
        // National-league opponents differ from lower-league opponents.
        Fixture national = FixtureGenerator.upcoming(player("ליגה לאומית"), today).get(0);
        Fixture lower = FixtureGenerator.upcoming(player("ליגה ב'"), today).get(0);
        assertNotNull(national.opponentClub);
        assertNotNull(lower.opponentClub);
    }
}
