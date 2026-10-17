package io.github.thebusybiscuit.slimefun5.core.services.localization;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.LongSupplier;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * The title each player's next open-window packet is to carry, recorded on the main thread just before the
 * window opens and taken, once, by the Netty write handler that sees that packet.
 *
 * @implNote Bukkit hands the open-window packet to the channel in the same main-thread call that records the
 *           entry, so it is normally taken within microseconds. The age bound only guards a packet that is
 *           never sent (an exception aborts the open); without it a stale entry could retitle a later,
 *           unrelated window of the same player.
 */
final class PendingWindowTitles {

    static final long MAX_AGE_NANOS = 2_000_000_000L;

    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();
    private final LongSupplier clock;

    PendingWindowTitles(@Nonnull LongSupplier clock) {
        this.clock = clock;
    }

    /** A title resolved later, on the Netty thread, from the Slimefun item a vanilla container belongs to. */
    void expectItem(@Nonnull UUID player, @Nonnull String itemId) {
        pending.put(player, new Pending(itemId, null, clock.getAsLong()));
    }

    /** A title already resolved on the main thread, sent as it is. */
    void expectTitle(@Nonnull UUID player, @Nonnull String title) {
        pending.put(player, new Pending(null, title, clock.getAsLong()));
    }

    void cancel(@Nonnull UUID player) {
        pending.remove(player);
    }

    /**
     * Removes the player's entry and returns its title, or {@code null} when there was none or it is stale.
     *
     * @param itemTitle resolves an item-form entry's title from the item id
     */
    @Nullable
    String take(@Nonnull UUID player, @Nonnull Function<String, String> itemTitle) {
        Pending entry = pending.remove(player);
        if (entry == null || clock.getAsLong() - entry.setAtNanos > MAX_AGE_NANOS) {
            return null;
        }
        return entry.title != null ? entry.title : itemTitle.apply(entry.itemId);
    }

    /** Exactly one of {@code itemId} and {@code title} is set. */
    private static final class Pending {

        private final String itemId;
        private final String title;
        private final long setAtNanos;

        Pending(@Nullable String itemId, @Nullable String title, long setAtNanos) {
            this.itemId = itemId;
            this.title = title;
            this.setAtNanos = setAtNanos;
        }
    }
}
