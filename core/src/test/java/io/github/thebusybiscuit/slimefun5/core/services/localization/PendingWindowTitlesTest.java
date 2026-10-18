package io.github.thebusybiscuit.slimefun5.core.services.localization;

import java.util.UUID;
import java.util.function.Function;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class PendingWindowTitlesTest {

    private static final UUID PLAYER = UUID.randomUUID();
    private static final Function<String, String> ITEM_NAME = id -> "name of " + id;

    private long now;
    private final PendingWindowTitles titles = new PendingWindowTitles(() -> now);

    @Test
    void resolvedTitleIsSentAsItIs() {
        titles.expectTitle(PLAYER, "Elektrischer Ofen");
        Assertions.assertEquals("Elektrischer Ofen", titles.take(PLAYER, ITEM_NAME));
    }

    @Test
    void itemTitleIsResolvedFromItsId() {
        titles.expectItem(PLAYER, "ENHANCED_FURNACE");
        Assertions.assertEquals("name of ENHANCED_FURNACE", titles.take(PLAYER, ITEM_NAME));
    }

    @Test
    void latestEntryWins() {
        titles.expectItem(PLAYER, "ENHANCED_FURNACE");
        titles.expectTitle(PLAYER, "Elektrischer Ofen");
        Assertions.assertEquals("Elektrischer Ofen", titles.take(PLAYER, ITEM_NAME));
    }

    @Test
    void titleIsTakenOnce() {
        titles.expectTitle(PLAYER, "Elektrischer Ofen");
        titles.take(PLAYER, ITEM_NAME);
        Assertions.assertNull(titles.take(PLAYER, ITEM_NAME));
    }

    @Test
    void staleTitleIsDropped() {
        titles.expectTitle(PLAYER, "Elektrischer Ofen");
        now += PendingWindowTitles.MAX_AGE_NANOS + 1;
        Assertions.assertNull(titles.take(PLAYER, ITEM_NAME));
    }

    @Test
    void pendingIsAskedWithoutTakingTheTitle() {
        titles.expectTitle(PLAYER, "Elektrischer Ofen");
        Assertions.assertTrue(titles.isPending(PLAYER));
        Assertions.assertEquals("Elektrischer Ofen", titles.take(PLAYER, ITEM_NAME));
        Assertions.assertFalse(titles.isPending(PLAYER));
    }

    @Test
    void staleTitleIsNotPending() {
        titles.expectTitle(PLAYER, "Elektrischer Ofen");
        now += PendingWindowTitles.MAX_AGE_NANOS + 1;
        Assertions.assertFalse(titles.isPending(PLAYER));
    }

    @Test
    void cancelledTitleIsNeverSent() {
        titles.expectTitle(PLAYER, "Elektrischer Ofen");
        titles.cancel(PLAYER);
        Assertions.assertNull(titles.take(PLAYER, ITEM_NAME));
    }

    @Test
    void otherPlayersAreUntouched() {
        titles.expectTitle(PLAYER, "Elektrischer Ofen");
        Assertions.assertNull(titles.take(UUID.randomUUID(), ITEM_NAME));
        Assertions.assertEquals("Elektrischer Ofen", titles.take(PLAYER, ITEM_NAME));
    }
}
