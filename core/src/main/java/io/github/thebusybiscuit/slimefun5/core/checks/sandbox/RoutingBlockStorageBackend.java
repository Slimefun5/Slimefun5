package io.github.thebusybiscuit.slimefun5.core.checks.sandbox;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import io.github.thebusybiscuit.slimefun5.storage.backend.BlockStorageBackend;

import me.mrCookieSlime.CSCoreLibPlugin.Configuration.Config;
import me.mrCookieSlime.Slimefun.api.BlockInfoConfig;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenuPreset;
import me.mrCookieSlime.Slimefun.api.inventory.UniversalBlockMenu;

/**
 * The backend core installs at enable: it sends the sandbox worlds to memory and every other world to the
 * real backend. With no sandbox open, every real world's call goes straight to the real backend.
 *
 * @implNote A chunk key carries only a world name, so a name counts as a sandbox world's once a call for
 *           that world has been routed here. {@link Sandbox} loads its world's storage as it opens, which is
 *           such a call, before anything can write a chunk of it. The names {@link Sandbox#WORLD_NAME} and
 *           {@link VoidWorld#NAME} stay routed here after a sandbox closes, because an async save can snapshot
 *           the sandbox's data before the close and flush it after; only a loaded server world of either name,
 *           which the sandbox refuses to make its own beside, reaches the real backend.
 */
public final class RoutingBlockStorageBackend implements BlockStorageBackend {

    private final BlockStorageBackend real;
    private final MemoryBlockStorageBackend memory = new MemoryBlockStorageBackend();
    private final Set<String> sandboxWorldNames = ConcurrentHashMap.newKeySet();
    private volatile Predicate<World> isSandbox;

    public RoutingBlockStorageBackend(@Nonnull BlockStorageBackend real) {
        this(real, null);
    }

    public RoutingBlockStorageBackend(@Nonnull BlockStorageBackend real, @Nullable Predicate<World> isSandbox) {
        this.real = real;
        this.isSandbox = isSandbox;
    }

    void route(@Nonnull Predicate<World> sandboxWorlds) {
        isSandbox = sandboxWorlds;
    }

    void stopRouting() {
        isSandbox = null;
        sandboxWorldNames.clear();
        memory.clear();
    }

    @Nonnull
    public MemoryBlockStorageBackend memory() {
        return memory;
    }

    private boolean isSandbox(@Nullable World world) {
        if (world == null) {
            return false;
        }
        Predicate<World> predicate = isSandbox;
        if (predicate != null && predicate.test(world)) {
            sandboxWorldNames.add(world.getName());
            return true;
        }
        return isReservedName(world.getName()) && Bukkit.getWorld(world.getName()) != world;
    }

    private boolean isSandboxChunk(@Nonnull String chunkKey) {
        int separator = chunkKey.indexOf(';');
        if (separator <= 0) {
            return false;
        }
        String worldName = chunkKey.substring(0, separator);
        return sandboxWorldNames.contains(worldName) || (isReservedName(worldName) && Bukkit.getWorld(worldName) == null);
    }

    private static boolean isReservedName(@Nonnull String worldName) {
        return Sandbox.WORLD_NAME.equals(worldName) || VoidWorld.NAME.equals(worldName);
    }

    private BlockStorageBackend backendFor(@Nullable World world) {
        return isSandbox(world) ? memory : real;
    }

    @Nonnull
    @Override
    public Map<String, Map<Location, Config>> loadWorldBlocks(@Nonnull World world) {
        return backendFor(world).loadWorldBlocks(world);
    }

    @Nonnull
    @Override
    public Map<String, BlockInfoConfig> loadChunksForWorld(@Nonnull World world) {
        return backendFor(world).loadChunksForWorld(world);
    }

    @Nonnull
    @Override
    public Map<Location, BlockMenu> loadWorldInventories(@Nonnull World world) {
        return backendFor(world).loadWorldInventories(world);
    }

    @Nonnull
    @Override
    public Map<String, UniversalBlockMenu> loadUniversalInventories() {
        return real.loadUniversalInventories();
    }

    @Nullable
    @Override
    public BlockMenu loadInventoryIfPresent(@Nonnull Location location, @Nonnull BlockMenuPreset preset) {
        return backendFor(Sandbox.worldOf(location)).loadInventoryIfPresent(location, preset);
    }

    @Override
    public void flushBlocks(@Nonnull World world, @Nonnull Map<String, Config> blocksCache) {
        backendFor(world).flushBlocks(world, blocksCache);
    }

    @Override
    public void deleteBlocks(@Nonnull World world, @Nonnull Collection<Location> locations) {
        backendFor(world).deleteBlocks(world, locations);
    }

    @Override
    public void flushInventories(@Nonnull Map<Location, BlockMenu> dirtyInventories) {
        Map<Location, BlockMenu> sandboxed = select(dirtyInventories, location -> isSandbox(Sandbox.worldOf(location)));
        memory.flushInventories(sandboxed);
        real.flushInventories(withoutKeys(dirtyInventories, sandboxed));
    }

    @Override
    public void flushUniversalInventories(@Nonnull Map<String, UniversalBlockMenu> universalInventories) {
        real.flushUniversalInventories(universalInventories);
    }

    /**
     * @implNote The flat-file backend rewrites every chunk on each flush, so the real backend must still get
     *           every chunk that is not the sandbox's.
     */
    @Override
    public void flushChunks(@Nonnull Map<String, BlockInfoConfig> chunks) {
        Map<String, BlockInfoConfig> sandboxed = select(chunks, this::isSandboxChunk);
        memory.flushChunks(sandboxed);
        real.flushChunks(withoutKeys(chunks, sandboxed));
    }

    private static <K, V> Map<K, V> select(Map<K, V> entries, Predicate<K> isSandboxKey) {
        Map<K, V> selected = new HashMap<>();
        for (Map.Entry<K, V> entry : entries.entrySet()) {
            if (isSandboxKey.test(entry.getKey())) {
                selected.put(entry.getKey(), entry.getValue());
            }
        }
        return selected;
    }

    /** The original map when nothing was taken out, so a real world's flush gets exactly what it got before. */
    private static <K, V> Map<K, V> withoutKeys(Map<K, V> entries, Map<K, V> taken) {
        if (taken.isEmpty()) {
            return entries;
        }
        Map<K, V> rest = new HashMap<>(entries);
        rest.keySet().removeAll(taken.keySet());
        return rest;
    }

    @Override
    public void deleteInventory(@Nonnull Location location) {
        backendFor(Sandbox.worldOf(location)).deleteInventory(location);
    }

    @Override
    public void close() {
        real.close();
    }
}
