package org.bukkit.inventory;

import org.bukkit.entity.HumanEntity;
import org.bukkit.event.inventory.InventoryType;

/**
 * Compile-only stub of {@code InventoryView} as an abstract class with an abstract {@code getTitle} (1.14 to
 * 1.20.6), which the 1.8.8 API core compiles against declares final. Never shipped: the server's own class is
 * used at runtime.
 */
public abstract class InventoryView {

    public abstract Inventory getTopInventory();

    public abstract Inventory getBottomInventory();

    public abstract HumanEntity getPlayer();

    public abstract InventoryType getType();

    public abstract String getTitle();

    public void setItem(int slot, ItemStack item) {
        throw new UnsupportedOperationException("stub");
    }

    public ItemStack getItem(int slot) {
        throw new UnsupportedOperationException("stub");
    }
}
