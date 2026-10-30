package io.github.thebusybiscuit.slimefun5.implementation.listeners;

import javax.annotation.Nonnull;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;

import io.github.thebusybiscuit.slimefun5.core.services.ItemCanonicalizer;
import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;

/**
 * Handles automatic canonicalization of plain stackable Slimefun resources across
 * ground pickups, hoppers, player drops, and item spawns.
 *
 * @author MaikonPlotegher
 */
public class ItemCanonicalizationListener implements Listener {

    public ItemCanonicalizationListener(@Nonnull Slimefun plugin) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityPickup(EntityPickupItemEvent e) {
        ItemStack stack = e.getItem().getItemStack();
        if (ItemCanonicalizer.canonicalize(stack)) {
            e.getItem().setItemStack(stack);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHopperPickup(InventoryPickupItemEvent e) {
        ItemStack stack = e.getItem().getItemStack();
        if (ItemCanonicalizer.canonicalize(stack)) {
            e.getItem().setItemStack(stack);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlayerDrop(PlayerDropItemEvent e) {
        ItemStack stack = e.getItemDrop().getItemStack();
        if (ItemCanonicalizer.canonicalize(stack)) {
            e.getItemDrop().setItemStack(stack);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent e) {
        ItemStack stack = e.getEntity().getItemStack();
        if (ItemCanonicalizer.canonicalize(stack)) {
            e.getEntity().setItemStack(stack);
        }
    }
}

