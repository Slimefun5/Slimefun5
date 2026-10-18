package io.github.thebusybiscuit.slimefun5.core.checks.sandbox;

import org.bukkit.entity.HumanEntity;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

/**
 * The sandbox view where {@code InventoryView} is a class with an abstract {@code getTitle} (1.14 to 1.19.3).
 *
 * @implNote Compiled against a stub of that class, because the 1.8.8 API declares {@code getTitle} final, and
 *           created reflectively by {@link SandboxInventoryView} only on that shape: on 1.8 to 1.13 it fails
 *           verification for overriding a final method.
 */
public class DeclaredTitleView extends InventoryView {

    final SandboxInventoryView.Backing backing;

    DeclaredTitleView(SandboxInventoryView.Backing backing) {
        this.backing = backing;
    }

    @Override
    public Inventory getTopInventory() {
        return backing.getTopInventory();
    }

    @Override
    public Inventory getBottomInventory() {
        return backing.getBottomInventory();
    }

    @Override
    public HumanEntity getPlayer() {
        return backing.getPlayer();
    }

    @Override
    public InventoryType getType() {
        return backing.getType();
    }

    @Override
    public String getTitle() {
        return backing.getTitle();
    }

    @Override
    public ItemStack getItem(int rawSlot) {
        return backing.getItem(rawSlot);
    }

    @Override
    public void setItem(int rawSlot, ItemStack item) {
        backing.setItem(rawSlot, item);
    }

    @Override
    public String toString() {
        return backing.toString();
    }
}
