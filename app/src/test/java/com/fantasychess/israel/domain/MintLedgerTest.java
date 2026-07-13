package com.fantasychess.israel.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.fantasychess.israel.data.model.Gender;
import com.fantasychess.israel.data.model.OwnedCard;
import com.fantasychess.israel.data.model.Player;
import com.fantasychess.israel.data.model.Rarity;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class MintLedgerTest {

    @Test
    public void uniqueIsOnePerSeason() {
        MintLedger ledger = new MintLedger(null);
        assertTrue(ledger.canMint(1, Rarity.UNIQUE));
        assertEquals(1, ledger.mint(1, Rarity.UNIQUE));
        assertFalse(ledger.canMint(1, Rarity.UNIQUE));
        // ...but a different player still has his Unique available
        assertTrue(ledger.canMint(2, Rarity.UNIQUE));
    }

    @Test
    public void serialsCountUpToTheCap() {
        MintLedger ledger = new MintLedger(null);
        for (int expected = 1; expected <= Rarity.SUPER_RARE.mintLimitPerSeason; expected++) {
            assertEquals(expected, ledger.mint(7, Rarity.SUPER_RARE));
        }
        assertFalse(ledger.canMint(7, Rarity.SUPER_RARE));
    }

    @Test
    public void commonsAreUnlimited() {
        MintLedger ledger = new MintLedger(null);
        for (int i = 0; i < 500; i++) {
            ledger.mint(3, Rarity.COMMON);
        }
        assertTrue(ledger.canMint(3, Rarity.COMMON));
    }

    @Test
    public void freePacksMintCommonsOnly() {
        List<Player> roster = Arrays.asList(
                new Player(1, "א", Gender.BOY, 2000, null, null, null, "מועדון", null, null),
                new Player(2, "ב", Gender.GIRL, 1800, null, null, null, "מועדון", null, null),
                new Player(3, "ג", Gender.BOY, 1600, null, null, null, "מועדון", null, null));
        PackGenerator generator = new PackGenerator(new Random(42));
        MintLedger ledger = new MintLedger(null);
        List<OwnedCard> cards = generator.openPack(
                PackGenerator.PackType.FREE, roster, ledger, 0);
        assertEquals(PackGenerator.CARDS_PER_PACK, cards.size());
        for (OwnedCard card : cards) {
            assertEquals(Rarity.COMMON, card.rarity);
        }
    }

    @Test
    public void proPackDowngradesWhenTierIsSoldOut() {
        List<Player> roster = Arrays.asList(
                new Player(1, "א", Gender.BOY, 2000, null, null, null, "מועדון", null, null));
        MintLedger ledger = new MintLedger(null);
        // Sell out every tokenized tier for this player.
        ledger.mint(1, Rarity.UNIQUE);
        for (int i = 0; i < Rarity.SUPER_RARE.mintLimitPerSeason; i++) {
            ledger.mint(1, Rarity.SUPER_RARE);
        }
        for (int i = 0; i < Rarity.RARE.mintLimitPerSeason; i++) {
            ledger.mint(1, Rarity.RARE);
        }
        PackGenerator generator = new PackGenerator(new Random(7));
        List<OwnedCard> cards = generator.openPack(
                PackGenerator.PackType.PRO, roster, ledger, 0);
        for (OwnedCard card : cards) {
            assertTrue("sold-out tiers must downgrade to PRO",
                    card.rarity == Rarity.PRO);
        }
    }
}
