package io.github.thebusybiscuit.slimefun5.core.checks.sandbox;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

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
 * real backend. With no sandbox open, every call goes straight to the real backend.
 *
 * @implNote A chunk key carries only a world name, so a name counts as a sandbox world's once a call for
 *           that world has been routed here. {@link Sandbox} loads its world's storage as it opens, which is
 *           such a call, before anything can write a chunk of it.
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
        Predicate<World> predicate = isSandbox;
        if (predicate == null || world == null || !predicate.test(world)) {
            return false;
        }
        sandboxWorldNames.add(world.getName());
        return true;
    }

    private boolean isSandbox(@Nonnull Location location) {
        return isSandbox(location.getWorld());
    }

    private boolean isSandboxChunk(@Nonnull String chunkKey) {
        int separator = chunkKey.indexOf(';');
        return separator > 0 && sandboxWorldNames.contains(chunkKey.substring(0, separator));
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
        return backendFor(location.getWorld()).loadInventoryIfPresent(location, preset);
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
        if (isSandbox == null) {
            real.flushInventories(dirtyInventories);
            return;
        }
        Map<Location, BlockMenu> sandboxed = new HashMap<>();
        Map<Location, BlockMenu> rest = new HashMap<>();
        for (Map.Entry<Location, BlockMenu> entry : dirtyInventories.entrySet()) {
            (isSandbox(entry.getKey()) ? sandboxed : rest).put(entry.getKey(), entry.getValue());
        }
        memory.flushInventories(sandboxed);
        real.flushInventories(sandboxed.isEmpty() ? dirtyInventories : rest);
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
        if (sandboxWorldNames.isEmpty()) {
            real.flushChunks(chunks);
            return;
        }
        Map<String, BlockInfoConfig> sandboxed = new HashMap<>();
        Map<String, BlockInfoConfig> rest = new HashMap<>();
        for (Map.Entry<String, BlockInfoConfig> entry : chunks.entrySet()) {
            (isSandboxChunk(entry.getKey()) ? sandboxed : rest).put(entry.getKey(), entry.getValue());
        }
        memory.flushChunks(sandboxed);
        real.flushChunks(sandboxed.isEmpty() ? chunks : rest);
    }

    @Override
    public void deleteInventory(@Nonnull Location location) {
        backendFor(location.getWorld()).deleteInventory(location);
    }

    @Override
    public void close() {
        real.close();
    }
}
