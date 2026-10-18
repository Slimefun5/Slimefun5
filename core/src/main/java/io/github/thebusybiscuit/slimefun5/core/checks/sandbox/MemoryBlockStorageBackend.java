package io.github.thebusybiscuit.slimefun5.core.checks.sandbox;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.bukkit.Location;
import org.bukkit.World;

import io.github.thebusybiscuit.slimefun5.storage.backend.BlockStorageBackend;

import me.mrCookieSlime.CSCoreLibPlugin.Configuration.Config;
import me.mrCookieSlime.Slimefun.api.BlockInfoConfig;
import me.mrCookieSlime.Slimefun.api.BlockStorage;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenuPreset;
import me.mrCookieSlime.Slimefun.api.inventory.UniversalBlockMenu;

/**
 * Holds what Slimefun saves for the sandbox worlds, so none of it reaches a database or a file. A sandbox
 * world is always new, so loading one finds nothing.
 */
public final class MemoryBlockStorageBackend implements BlockStorageBackend {

    private final Map<String, Config> blocks = new ConcurrentHashMap<>();
    private final Map<Location, BlockMenu> inventories = new ConcurrentHashMap<>();
    private final Map<String, BlockInfoConfig> chunks = new ConcurrentHashMap<>();

    public boolean isEmpty() {
        return blocks.isEmpty() && inventories.isEmpty() && chunks.isEmpty();
    }

    public void clear() {
        blocks.clear();
        inventories.clear();
        chunks.clear();
    }

    @Nonnull
    @Override
    public Map<String, Map<Location, Config>> loadWorldBlocks(@Nonnull World world) {
        return new HashMap<>();
    }

    @Nonnull
    @Override
    public Map<String, BlockInfoConfig> loadChunksForWorld(@Nonnull World world) {
        return new HashMap<>();
    }

    @Nonnull
    @Override
    public Map<Location, BlockMenu> loadWorldInventories(@Nonnull World world) {
        return new HashMap<>();
    }

    @Nonnull
    @Override
    public Map<String, UniversalBlockMenu> loadUniversalInventories() {
        return new HashMap<>();
    }

    @Nullable
    @Override
    public BlockMenu loadInventoryIfPresent(@Nonnull Location location, @Nullable BlockMenuPreset preset) {
        return inventories.get(location);
    }

    @Override
    public void flushBlocks(@Nonnull World world, @Nonnull Map<String, Config> blocksCache) {
        for (Map.Entry<String, Config> entry : blocksCache.entrySet()) {
            blocks.put(world.getName() + ';' + entry.getKey(), entry.getValue());
        }
    }

    @Override
    public void deleteBlocks(@Nonnull World world, @Nonnull Collection<Location> locations) {
        String prefix = world.getName() + ';';
        for (Map.Entry<String, Config> entry : blocks.entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                for (Location location : locations) {
                    entry.getValue().setValue(BlockStorage.serializeLocation(location), null);
                }
            }
        }
    }

    @Override
    public void flushInventories(@Nonnull Map<Location, BlockMenu> dirtyInventories) {
        inventories.putAll(dirtyInventories);
    }

    @Override
    public void flushUniversalInventories(@Nonnull Map<String, UniversalBlockMenu> universalInventories) {
        // Universal inventories belong to no world, so the router never sends them here.
    }

    @Override
    public void flushChunks(@Nonnull Map<String, BlockInfoConfig> dirtyChunks) {
        chunks.putAll(dirtyChunks);
    }

    @Override
    public void deleteInventory(@Nonnull Location location) {
        inventories.remove(location);
    }
}
