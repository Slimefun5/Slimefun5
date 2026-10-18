package io.github.thebusybiscuit.slimefun5.core.checks.sandbox;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.event.Event;

import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun5.storage.backend.BlockStorageBackend;

import me.mrCookieSlime.Slimefun.api.BlockStorage;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenuPreset;

/**
 * Where deep checks place blocks, open menus and click: a world and players that exist only for the checks
 * and leave no trace. While a sandbox is open its world's block data goes to memory, its players' profiles
 * are never saved and are not online, and on a live server its events reach only Slimefun and the plugins
 * that depend on it. Closing it removes everything it made.
 *
 * <p>
 * At most one sandbox is open at a time.
 */
public final class Sandbox implements AutoCloseable {

    public static final String WORLD_NAME = "slimefun_checks_sandbox";

    static final String BLOCK_DATA_STEP = "block data";

    private static volatile Sandbox current;
    private static volatile String forcedFailure;

    private final boolean live;
    private final RoutingBlockStorageBackend storage;
    private final SlimefunOnlyEvents slimefunOnly;
    private final Set<UUID> joined = ConcurrentHashMap.newKeySet();
    private final List<String> closeFailures = new ArrayList<>();
    private volatile SandboxWorld world;
    private boolean closed;

    private Sandbox(boolean live, RoutingBlockStorageBackend storage) {
        this.live = live;
        this.storage = storage;
        this.slimefunOnly = new SlimefunOnlyEvents(Slimefun.instance());
    }

    /**
     * @param live whether this is a live server; only the harness, which is not, registers the world with the
     *             server and delivers events to every plugin
     */
    @Nonnull
    public static Sandbox open(boolean live) {
        Sandbox sandbox;
        synchronized (Sandbox.class) {
            if (current != null) {
                throw new IllegalStateException("A sandbox is already open");
            }
            RoutingBlockStorageBackend storage = routerOf(Slimefun.getBlockStorageBackend());
            refuseNameClash();
            sandbox = new Sandbox(live, storage);
            current = sandbox;
        }
        try {
            sandbox.start();
        } catch (RuntimeException | LinkageError failure) {
            sandbox.close();
            throw failure;
        }
        return sandbox;
    }

    public static boolean isOpen() {
        return current != null;
    }

    public static void closeOpen() {
        Sandbox open = current;
        if (open != null) {
            open.close();
        }
    }

    public static boolean isSandboxPlayer(@Nonnull UUID id) {
        return SandboxProfiles.isSandbox(id);
    }

    /** Whether the player is a sandbox player or the world is the open sandbox's. */
    public static boolean covers(@Nullable OfflinePlayer player, @Nullable World world) {
        if (player != null && isSandboxPlayer(player.getUniqueId())) {
            return true;
        }
        Sandbox open = current;
        return open != null && open.isSandboxWorld(world);
    }

    public static boolean covers(@Nullable OfflinePlayer player, @Nonnull Location location) {
        return covers(player, worldOf(location));
    }

    /**
     * Refuses a universal menu in the open sandbox's world and records it as a gap: a universal menu is one
     * shared inventory for every block of its kind, so the sandbox's copy would be real players' items.
     *
     * @return whether the menu must not be placed or opened
     */
    public static boolean refusesUniversalMenu(@Nonnull Location location, @Nullable String id) {
        Sandbox open = current;
        if (open == null || id == null || !open.isSandboxWorld(worldOf(location)) || !BlockMenuPreset.isUniversalInventory(id)) {
            return false;
        }
        open.gaps().record("UniversalBlockMenu(" + id + ")");
        return true;
    }

    /**
     * @implNote From 1.14 a {@link Location} whose world was unloaded and collected throws on
     *           {@link Location#getWorld()}; such a location is in no sandbox.
     */
    @Nullable
    static World worldOf(@Nonnull Location location) {
        try {
            return location.getWorld();
        } catch (IllegalArgumentException unloaded) {
            return null;
        }
    }

    /** Sends an event a sandbox facade raises the way the open sandbox delivers events, or to every plugin when none is open. */
    static void deliver(@Nonnull Event event) {
        Sandbox open = current;
        if (open == null) {
            Bukkit.getPluginManager().callEvent(event);
        } else {
            open.callEvent(event);
        }
    }

    @Nonnull
    public SandboxWorld world() {
        return world;
    }

    @Nonnull
    public CheckGap gaps() {
        return world.gaps();
    }

    @Nonnull
    public SandboxPlayer join(@Nonnull String name, @Nonnull String locale) {
        SandboxPlayer player = world.join(name, locale);
        joined.add(player.player().getUniqueId());
        return player;
    }

    public void quit(@Nonnull SandboxPlayer player) {
        world.quit(player);
    }

    public void callEvent(@Nonnull Event event) {
        if (live) {
            slimefunOnly.call(event);
        } else {
            Bukkit.getPluginManager().callEvent(event);
        }
    }

    public boolean isSandboxWorld(@Nullable World candidate) {
        SandboxWorld sandboxWorld = world;
        return candidate != null && sandboxWorld != null && sandboxWorld.world() == candidate;
    }

    /**
     * Closes this sandbox, undoing each thing it made on its own, so one failure does not leave the rest behind.
     * Routing to memory stops only when the sandbox's block data is gone from the registry; otherwise the next
     * save would carry that data to the real backend. What could not be undone is in {@link #closeFailures()}.
     */
    @Override
    public void close() {
        synchronized (Sandbox.class) {
            if (closed) {
                return;
            }
            closed = true;
            try {
                if (world != null) {
                    closeWorld(world);
                }
                attempt("profiles", () -> joined.forEach(SandboxProfiles::evict));
            } finally {
                if (current == this) {
                    current = null;
                }
            }
        }
    }

    /** One line per step that failed while closing, such as {@code block data: java.lang.IllegalStateException}. */
    @Nonnull
    public List<String> closeFailures() {
        synchronized (Sandbox.class) {
            return new ArrayList<>(closeFailures);
        }
    }

    private void closeWorld(SandboxWorld sandboxWorld) {
        World bukkitWorld = sandboxWorld.world();
        BlockStorage blockStorage = BlockStorage.getStorage(bukkitWorld);
        attempt("ticker", () -> Slimefun.getTickerTask().forgetWorld(bukkitWorld));
        boolean purged = attempt(BLOCK_DATA_STEP, () -> purgeBlockData(blockStorage));
        attempt("ticker", () -> Slimefun.getTickerTask().forgetWorld(bukkitWorld));
        if (blockStorage != null) {
            attempt("viewed inventories", () -> blockStorage.getRawStorage().keySet().forEach(location -> BlockStorage.setInventoryViewed(location, false)));
        }
        attempt("players", this::quitEveryone);
        attempt("world settings", () -> Slimefun.getWorldSettingsService().unload(bukkitWorld));
        if (!live) {
            attempt("server registration", sandboxWorld::unregister);
        }
        if (purged) {
            storage.stopRouting();
        }
    }

    private static void purgeBlockData(@Nullable BlockStorage blockStorage) {
        failIfForced(BLOCK_DATA_STEP);
        if (blockStorage != null) {
            Slimefun.getRegistry().getWorlds().remove(WORLD_NAME, blockStorage);
        }
        Slimefun.getRegistry().getChunks().keySet().removeIf(key -> key.startsWith(WORLD_NAME + ';'));
    }

    private boolean attempt(String step, Runnable undo) {
        try {
            undo.run();
            return true;
        } catch (RuntimeException | LinkageError failure) {
            closeFailures.add(step + ": " + failure);
            Slimefun.logger().log(Level.SEVERE, failure, () -> "The checks' sandbox could not undo its " + step);
            return false;
        }
    }

    /** Makes the named close step throw, so a test can see what a failed close leaves behind. */
    static void failCloseStepForTesting(@Nullable String step) {
        forcedFailure = step;
    }

    private static void failIfForced(String step) {
        if (step.equals(forcedFailure)) {
            throw new IllegalStateException("forced failure of " + step);
        }
    }

    /**
     * @implNote Routing starts before the world exists, and the world's block storage is loaded before anything
     *           can write to it, so even its first load goes to memory.
     */
    private void start() {
        storage.route(this::isSandboxWorld);
        world = SandboxWorld.create(WORLD_NAME, new CheckGap());
        Slimefun.getWorldSettingsService().loadDefaults(world.world());
        if (!live) {
            world.announce();
        }
        BlockStorage.getOrCreate(world.world());
    }

    private void quitEveryone() {
        for (SandboxPlayer player : world.players()) {
            try {
                world.quit(player);
            } catch (RuntimeException | LinkageError failure) {
                Slimefun.logger().log(Level.WARNING, "A sandbox player could not leave cleanly", failure);
            }
        }
    }

    private static RoutingBlockStorageBackend routerOf(BlockStorageBackend backend) {
        if (!(backend instanceof RoutingBlockStorageBackend)) {
            throw new IllegalStateException("Block storage is not routed, so a sandbox would write to it");
        }
        return (RoutingBlockStorageBackend) backend;
    }

    private static void refuseNameClash() {
        if (Bukkit.getWorld(WORLD_NAME) != null || Slimefun.getRegistry().getWorlds().containsKey(WORLD_NAME)) {
            throw new IllegalStateException("A world named " + WORLD_NAME + " already exists, so a sandbox cannot tell its data apart");
        }
    }
}
