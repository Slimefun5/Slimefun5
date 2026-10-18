package io.github.thebusybiscuit.slimefun5.implementation.listeners;

import java.util.logging.Level;

import javax.annotation.Nonnull;

import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;

import io.github.thebusybiscuit.slimefun5.core.checks.sandbox.Sandbox;
import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun5.storage.backend.migration.MigrationService;

import me.mrCookieSlime.Slimefun.api.BlockStorage;

public class WorldListener implements Listener {

    public WorldListener(@Nonnull Slimefun plugin) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent e) {
        if (isChecksWorld(e.getWorld())) {
            Slimefun.getWorldSettingsService().loadDefaults(e.getWorld());
        } else {
            Slimefun.getWorldSettingsService().load(e.getWorld());

            MigrationService svc = Slimefun.getStorageMigration();

            if (svc != null) {
                svc.migrateWorldIfNeeded(e.getWorld());
            }
        }

        BlockStorage.getOrCreate(e.getWorld());
    }

    /**
     * The checks' worlds keep their data in memory and are gone after the run, so they have nothing to migrate,
     * must not leave a migration flag in the real database, and must not leave a world-settings file.
     */
    private static boolean isChecksWorld(@Nonnull World world) {
        return Sandbox.covers(null, world);
    }

    @EventHandler
    public void onWorldUnload(WorldUnloadEvent e) {
        BlockStorage storage = BlockStorage.getStorage(e.getWorld());

        if (storage != null) {
            storage.saveAndRemove();
        } else {
            Slimefun.logger().log(Level.SEVERE, "Could not save Slimefun Blocks for World \"{0}\"", e.getWorld().getName());
        }
    }

}

