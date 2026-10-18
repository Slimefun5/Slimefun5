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
        Slimefun.getWorldSettingsService().load(e.getWorld());

        MigrationService svc = Slimefun.getStorageMigration();

        if (svc != null && mayHaveDataToMigrate(e.getWorld())) {
            svc.migrateWorldIfNeeded(e.getWorld());
        }

        BlockStorage.getOrCreate(e.getWorld());
    }

    /**
     * The checks sandbox's world, which is announced only in the version harness, keeps its data in memory, so it
     * has nothing to migrate and must not leave a migration flag in the real database.
     */
    private static boolean mayHaveDataToMigrate(@Nonnull World world) {
        return !Sandbox.covers(null, world);
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

