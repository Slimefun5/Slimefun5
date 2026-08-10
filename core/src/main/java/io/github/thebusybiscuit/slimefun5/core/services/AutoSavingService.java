package io.github.thebusybiscuit.slimefun5.core.services;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.logging.Level;

import javax.annotation.Nonnull;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import io.github.thebusybiscuit.slimefun5.api.player.PlayerProfile;
import io.github.thebusybiscuit.slimefun5.core.debug.Debug;
import io.github.thebusybiscuit.slimefun5.core.debug.TestCase;
import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;

import me.mrCookieSlime.Slimefun.api.BlockStorage;

/**
 * This Service is responsible for automatically saving {@link Player} and {@link Block}
 * data.
 * 
 * @author TheBusyBiscuit
 *
 */
public class AutoSavingService {

    private int interval;

    /**
     * This method starts the {@link AutoSavingService} with the given interval.
     * 
     * @param plugin
     *            The current instance of Slimefun
     * @param interval
     *            The interval in which to run this task
     */
    public void start(@Nonnull Slimefun plugin, int interval) {
        this.interval = interval;

        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::saveAllPlayers, 2000L, interval * 60L * 20L);
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::saveAllBlocks, 2000L, interval * 60L * 20L);
    }

    /**
     * This method saves every {@link PlayerProfile} in memory and removes profiles
     * that were marked for deletion.
     */
    private void saveAllPlayers() {
        Iterator<PlayerProfile> iterator = PlayerProfile.iterator();
        int players = 0;

        Debug.log(TestCase.PLAYER_PROFILE_DATA, "Saving all players data");

        while (iterator.hasNext()) {
            PlayerProfile profile = iterator.next();

            if (profile.isDirty()) {
                players++;
                profile.save();

                Debug.log(TestCase.PLAYER_PROFILE_DATA, "Saved data for {} ({})",
                    profile.getPlayer() != null ? profile.getPlayer().getName() : "Unknown", profile.getUUID()
                );
            }

            // Safe to evict now: the profile was already saved above, so this cannot cause a data-sync issue.
            if (profile.isMarkedForDeletion() && profile.getPlayer() == null) {
                iterator.remove();

                Debug.log(TestCase.PLAYER_PROFILE_DATA, "Removed data from memory for {}",
                    profile.getUUID()
                );
            }
        }

        if (players > 0) {
            Slimefun.logger().log(Level.INFO, "Auto-saved all player data for {0} player(s)!", players);
        }
    }

    /**
     * This method saves the data of every {@link Block} marked dirty by {@link BlockStorage}.
     */
    private void saveAllBlocks() {
        Set<BlockStorage> worlds = new HashSet<>();

        for (World world : Bukkit.getWorlds()) {
            BlockStorage storage = BlockStorage.getStorage(world);

            if (storage != null) {
                storage.computeChanges();

                if (storage.getChanges() > 0) {
                    worlds.add(storage);
                }
            }
        }

        if (!worlds.isEmpty()) {
            Slimefun.logger().log(Level.INFO, "Auto-saving block data... (Next auto-save: {0}m)", interval);

            for (BlockStorage storage : worlds) {
                storage.save();
            }
        }

        BlockStorage.saveChunks();
    }

}

