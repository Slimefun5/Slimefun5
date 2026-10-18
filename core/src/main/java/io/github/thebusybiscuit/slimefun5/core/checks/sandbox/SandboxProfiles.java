package io.github.thebusybiscuit.slimefun5.core.checks.sandbox;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;

import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;

/**
 * The ids of sandbox players. Their {@code PlayerProfile}s live only in memory: they are never saved and are
 * dropped when their sandbox closes.
 *
 * @implNote An id stays known after its sandbox closes, so a profile load still in flight when the sandbox
 *           closed can never be saved either. Ids come from the {@code slimefun-checks:} name namespace, which
 *           no real player's id (random, or {@code OfflinePlayer:} for offline mode) can share.
 */
final class SandboxProfiles {

    private static final String NAMESPACE = "slimefun-checks:";
    private static final Set<UUID> ISSUED = ConcurrentHashMap.newKeySet();

    private SandboxProfiles() {}

    @Nonnull
    static UUID issue(@Nonnull String playerName) {
        UUID id = UUID.nameUUIDFromBytes((NAMESPACE + playerName).getBytes(StandardCharsets.UTF_8));
        ISSUED.add(id);
        return id;
    }

    static boolean isSandbox(@Nonnull UUID id) {
        return ISSUED.contains(id);
    }

    static void evict(@Nonnull UUID id) {
        Slimefun.getRegistry().getPlayerProfiles().remove(id);
    }
}
