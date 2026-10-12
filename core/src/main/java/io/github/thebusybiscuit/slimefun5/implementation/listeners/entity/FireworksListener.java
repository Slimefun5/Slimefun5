package io.github.thebusybiscuit.slimefun5.implementation.listeners.entity;

import javax.annotation.Nonnull;

import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import io.github.thebusybiscuit.slimefun5.api.researches.Research;
import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun5.utils.FireworkUtils;

/**
 * This {@link Listener} makes sure that any {@link Firework} caused by a {@link Player}
 * unlocking a {@link Research} does not cause damage to be dealt.
 * 
 * @author TheBusyBiscuit
 *
 */
public class FireworksListener implements Listener {

    public FireworksListener(@Nonnull Slimefun plugin) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onResearchFireworkDamage(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Firework) {
            Firework firework = (Firework) e.getDamager();

            if (FireworkUtils.isSlimefunFirework(firework)) {
                e.setCancelled(true);
            }
        }
    }

}

