package io.github.thebusybiscuit.slimefun5.implementation.listeners.crafting;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Event.Result;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;

import io.github.thebusybiscuit.slimefun5.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun5.core.guide.SlimefunGuide;
import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;

/**
 * This {@link Listener} prevents any {@link SlimefunItem} from being used in an
 * anvil.
 * 
 * @author TheBusyBiscuit
 *
 */
public class AnvilListener implements SlimefunCraftingListener {

    public AnvilListener(@Nonnull Slimefun plugin) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(ignoreCancelled = true)
    public void onAnvil(InventoryClickEvent e) {
        if (e.getRawSlot() == 2 && e.getInventory().getType() == InventoryType.ANVIL && e.getWhoClicked() instanceof Player) {
            Player player = (Player) e.getWhoClicked();
            ItemStack item1 = e.getInventory().getContents()[0];
            ItemStack item2 = e.getInventory().getContents()[1];

            if (isPureRename(item1, item2)) {
                return;
            }

            if (hasUnallowedItems(item1, item2)) {
                e.setResult(Result.DENY);
                Slimefun.getLocalization().sendMessage(player, "anvil.not-working", true);
            }
        }
    }

    /**
     * A pure rename fills only the left slot with a {@link SlimefunItem}: it changes the name without
     * repairing, enchanting, or combining, so it is allowed while any other anvil use of a
     * {@link SlimefunItem} stays blocked.
     *
     * @implNote {@code AnvilRenameListener} tags the renamed result so the translation layer keeps the
     *           custom name.
     */
    private boolean isPureRename(@Nullable ItemStack item1, @Nullable ItemStack item2) {
        return (item2 == null || item2.getType() == Material.AIR)
            && !SlimefunGuide.isGuideItem(item1)
            && SlimefunItem.getByItem(item1) != null;
    }

}

