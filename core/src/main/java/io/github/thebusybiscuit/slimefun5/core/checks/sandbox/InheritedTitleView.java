package io.github.thebusybiscuit.slimefun5.core.checks.sandbox;

import org.bukkit.entity.HumanEntity;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

/**
 * The sandbox view where {@code InventoryView} is a class with a final {@code getTitle} (1.8 to 1.13), which
 * reads the top inventory's own title.
 *
 * @implNote Loaded only on that shape: from 1.14 it would leave {@code getTitle} abstract, and from 1.21 it
 *           extends an interface. It must not declare {@code setTitle}, which those servers' views lack.
 */
public final class InheritedTitleView extends InventoryView {

    private final SandboxInventoryView.Backing backing;

    private InheritedTitleView(SandboxInventoryView.Backing backing) {
        this.backing = backing;
    }

    static InventoryView create(SandboxInventoryView.Backing backing) {
        return new InheritedTitleView(backing);
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
