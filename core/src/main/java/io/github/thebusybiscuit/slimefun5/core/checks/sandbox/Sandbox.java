package io.github.thebusybiscuit.slimefun5.core.checks.sandbox;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.Event;

import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun5.implementation.tasks.TickerTask;
import io.github.thebusybiscuit.slimefun5.storage.backend.BlockStorageBackend;

import me.mrCookieSlime.Slimefun.api.BlockStorage;

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

    private static volatile Sandbox current;

    private final boolean live;
    private final RoutingBlockStorageBackend storage;
    private final SlimefunOnlyEvents slimefunOnly;
    private final Set<UUID> joined = ConcurrentHashMap.newKeySet();
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

    @Override
    public void close() {
        synchronized (Sandbox.class) {
            if (closed) {
                return;
            }
            closed = true;
            try {
                if (world != null) {
                    quitEveryone();
                    forgetWorld(world);
                }
                joined.forEach(SandboxProfiles::evict);
            } finally {
                storage.stopRouting();
                if (current == this) {
                    current = null;
                }
            }
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

    /** Ticker state is dropped on both sides of removing the block data, so a tick in between finds nothing of it. */
    private void forgetWorld(SandboxWorld sandboxWorld) {
        World bukkitWorld = sandboxWorld.world();
        TickerTask ticker = Slimefun.getTickerTask();
        ticker.forgetWorld(bukkitWorld);
        BlockStorage blockStorage = BlockStorage.getStorage(bukkitWorld);
        if (blockStorage != null) {
            for (Location location : blockStorage.getRawStorage().keySet()) {
                BlockStorage.setInventoryViewed(location, false);
            }
            Slimefun.getRegistry().getWorlds().remove(WORLD_NAME, blockStorage);
        }
        ticker.forgetWorld(bukkitWorld);
        Slimefun.getRegistry().getChunks().keySet().removeIf(key -> key.startsWith(WORLD_NAME + ';'));
        Slimefun.getWorldSettingsService().unload(bukkitWorld);
        if (!live) {
            sandboxWorld.unregister();
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
