package io.github.thebusybiscuit.slimefun5.core.checks;

import javax.annotation.Nonnull;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;

public class CheckJoinListener implements Listener {

    private static final long NOTICE_DELAY_TICKS = 60L;

    public CheckJoinListener(@Nonnull Slimefun plugin) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(@Nonnull PlayerJoinEvent event) {
        Slimefun plugin = Slimefun.instance();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> LiveChecks.announceTo(event.getPlayer()), NOTICE_DELAY_TICKS);
    }
}
