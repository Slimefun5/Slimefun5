package io.github.thebusybiscuit.slimefun5.core.multiblocks;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;

import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;

/**
 * Tracks which {@link UUID} owns each multiblock crafting table (Enhanced Crafting Table, Magic
 * Workbench, Armor Forge), keyed by its dispenser {@link Location}. A multiblock is owned by the
 * first player who interacts with it; ownership then gates the redstone auto-craft on the owner's
 * unlocked research.
 * <p>
 * This is a standalone side-store (a {@code multiblock-owners.yml} file) rather than a
 * {@link me.mrCookieSlime.Slimefun.api.BlockStorage} entry on purpose: the redstone listener
 * identifies a multiblock dispenser precisely by it having <em>no</em> {@code BlockStorage} entry,
 * so writing one would break auto-craft detection.
 * <p>
 * The in-memory map is a {@link ConcurrentHashMap} and every mutation triggers a debounced async
 * save, so it is safe to read/write from the main thread and to flush from an async task.
 *
 * @author TheBusyBiscuit
 */
public class MultiBlockOwnership {

    private static final String FILE_NAME = "multiblock-owners.yml";
    private static final String KEY = "owners";

    // Keyed by "world;x;y;z" -> owner UUID.
    private final Map<String, UUID> owners = new ConcurrentHashMap<>();

    // Coalesces bursts of mutations into a single async write.
    private final AtomicBoolean saveScheduled = new AtomicBoolean(false);

    private final Slimefun plugin;
    private final File file;

    public MultiBlockOwnership(@Nonnull Slimefun plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), FILE_NAME);
    }

    /**
     * The {@link Location} a multiblock's ownership is keyed by: its auto-craft dispenser when the
     * structure has one, else the structure's own centre.
     *
     * @implNote Dispenser-keyed is the original scheme and stays so that existing {@code
     *           multiblock-owners.yml} entries and the redstone auto-craft lookup (which only ever knows
     *           the dispenser) keep working. Falling back to the centre is what lets a dispenser-less
     *           multiblock - the Automated Panning Machine and Table Saw in core - be owned at all;
     *           before this they were silently unownable and {@code /sf owner} always reported them free.
     *
     * @param center
     *            The block the structure matched on
     *
     * @return The location to key ownership by
     */
    @Nonnull
    public static Location ownershipKey(@Nonnull Block center) {
        for (int ox = -1; ox <= 1; ox++) {
            for (int oy = -1; oy <= 1; oy++) {
                for (int oz = -1; oz <= 1; oz++) {
                    Block near = center.getRelative(ox, oy, oz);

                    if (near.getType() == Material.DISPENSER) {
                        return near.getLocation();
                    }
                }
            }
        }

        return center.getLocation();
    }
    /**
     * Builds the map key for a dispenser {@link Location} ("world;x;y;z", block coordinates).
     */
    @Nonnull
    private static String key(@Nonnull Location location) {
        return location.getWorld().getName() + ';' + location.getBlockX() + ';' + location.getBlockY() + ';' + location.getBlockZ();
    }

    /**
     * @return The owner of the multiblock at this dispenser location, or null if it is unowned.
     */
    @Nullable
    public UUID getOwner(@Nonnull Location location) {
        return owners.get(key(location));
    }

    /**
     * Assigns an owner only if the multiblock is currently unowned.
     *
     * @return Whether this call actually set the owner (i.e. it was previously unowned).
     */
    public boolean setOwnerIfAbsent(@Nonnull Location location, @Nonnull UUID owner) {
        boolean changed = owners.putIfAbsent(key(location), owner) == null;

        if (changed) {
            scheduleSave();
        }

        return changed;
    }

    /**
     * Removes any ownership record for the multiblock at this dispenser location (e.g. when the
     * structure is broken).
     */
    public void clear(@Nonnull Location location) {
        if (owners.remove(key(location)) != null) {
            scheduleSave();
        }
    }

    /**
     * Loads the persisted ownership records from disk once, at boot. Malformed entries are skipped.
     */
    public void load() {
        if (!file.exists()) {
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

        for (String line : config.getStringList(KEY)) {
            // Format: world;x;y;z;uuid - split from the right so world names containing ';' survive.
            String[] parts = line.split(";");

            if (parts.length < 5) {
                continue;
            }

            try {
                UUID owner = UUID.fromString(parts[parts.length - 1]);
                StringBuilder locationKey = new StringBuilder();

                for (int i = 0; i < parts.length - 1; i++) {
                    if (i > 0) {
                        locationKey.append(';');
                    }
                    locationKey.append(parts[i]);
                }

                owners.put(locationKey.toString(), owner);
            } catch (IllegalArgumentException e) {
                Slimefun.logger().log(Level.WARNING, "Skipping malformed multiblock-owner entry: {0}", line);
            }
        }
    }

    private void scheduleSave() {
        // Outside a live server (e.g. unit tests) just write straight through.
        if (!plugin.isEnabled()) {
            save();
            return;
        }

        if (saveScheduled.compareAndSet(false, true)) {
            plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin, () -> {
                saveScheduled.set(false);
                save();
            }, 100L);
        }
    }

    /**
     * Writes the current ownership records to disk. Safe to call from any thread; reads a snapshot
     * of the map.
     */
    public synchronized void save() {
        YamlConfiguration config = new YamlConfiguration();
        List<String> lines = new ArrayList<>(owners.size());

        for (Map.Entry<String, UUID> entry : owners.entrySet()) {
            lines.add(entry.getKey() + ';' + entry.getValue());
        }

        config.set(KEY, lines);

        try {
            config.save(file);
        } catch (IOException e) {
            Slimefun.logger().log(Level.SEVERE, e, () -> "Could not save multiblock ownership data to " + file);
        }
    }
}
