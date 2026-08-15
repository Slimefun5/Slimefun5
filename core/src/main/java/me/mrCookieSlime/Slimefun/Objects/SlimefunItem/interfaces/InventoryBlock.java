package me.mrCookieSlime.Slimefun.Objects.SlimefunItem.interfaces;

import java.lang.reflect.Array;
import java.util.function.Consumer;

import org.bukkit.ChatColor;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import io.github.bakedlibs.dough.protection.Interaction;
import io.github.thebusybiscuit.slimefun5.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;

import me.mrCookieSlime.Slimefun.api.inventory.BlockMenuPreset;
import me.mrCookieSlime.Slimefun.api.item_transport.ItemTransportFlow;

/**
 * 
 * @deprecated This interface is not designed to be used by addons. The entire inventory system will be replaced
 *             eventually.
 *
 */
public interface InventoryBlock {

    /**
     * This method returns an {@link Array} of slots that serve as the input
     * for the {@link Inventory} of this block.
     * 
     * @return The input slots for the {@link Inventory} of this block
     */
    int[] getInputSlots();

    /**
     * This method returns an {@link Array} of slots that serve as the output
     * for the {@link Inventory} of this block.
     * 
     * @return The output slots for the {@link Inventory} of this block
     */
    int[] getOutputSlots();

    /**
     * @deprecated Leaves the created preset with neither a declared header item nor an explicit title
     *             colour, so its GUI title silently falls back to gray unless the (accidental)
     *             name-matching heuristic happens to find one. Use
     *             {@link #createPreset(SlimefunItem, String, int, Consumer)} if this menu has a decorative
     *             slot repeating the item's own name, or
     *             {@link #createPreset(SlimefunItem, String, ChatColor, Consumer)} to explicitly declare
     *             that it does not and choose the title colour instead.
     */
    @Deprecated
    default void createPreset(SlimefunItem item, Consumer<BlockMenuPreset> setup) {
        createPreset(item, item.getItemName(), setup);
    }

    /** @deprecated See {@link #createPreset(SlimefunItem, Consumer)}. */
    @Deprecated
    default void createPreset(SlimefunItem item, String title, Consumer<BlockMenuPreset> setup) {
        new BlockMenuPreset(item.getId(), title) {

            @Override
            public void init() {
                setup.accept(this);
            }

            @Override
            public int[] getSlotsAccessedByItemTransport(ItemTransportFlow flow) {
                if (flow == ItemTransportFlow.INSERT) {
                    return getInputSlots();
                } else {
                    return getOutputSlots();
                }
            }

            @Override
            public boolean canOpen(Block b, Player p) {
                if (p.hasPermission("slimefun.inventory.bypass")) {
                    return true;
                } else {
                    return item.canUse(p, false) && (
                        // Protection manager doesn't exist in unit tests
                        Slimefun.instance().isUnitTest()
                        || Slimefun.getProtectionManager().hasPermission(p, b.getLocation(), Interaction.INTERACT_BLOCK)
                    );
                }
            }
        };
    }

    /**
     * Creates a menu preset for {@code item} and declares {@code headerItemSlot} as its header item: the
     * decorative slot whose colour becomes this menu's GUI title colour (see
     * {@link BlockMenuPreset#setHeaderItemSlot(int)}).
     *
     * @param item
     *            The {@link SlimefunItem} this preset belongs to
     * @param title
     *            The preset's inventory title
     * @param headerItemSlot
     *            The slot holding this preset's header item, added by {@code setup}
     * @param setup
     *            Populates the preset's slots
     */
    @SuppressWarnings("deprecation")
    default void createPreset(SlimefunItem item, String title, int headerItemSlot, Consumer<BlockMenuPreset> setup) {
        createPreset(item, title, (Consumer<BlockMenuPreset>) preset -> {
            preset.setHeaderItemSlot(headerItemSlot);
            setup.accept(preset);
        });
    }

    /**
     * Creates a menu preset for {@code item} and explicitly declares that it has no header item, using
     * {@code titleColor} for its GUI title instead (see
     * {@link BlockMenuPreset#optOutOfHeaderItem(ChatColor)}).
     *
     * @param item
     *            The {@link SlimefunItem} this preset belongs to
     * @param title
     *            The preset's inventory title
     * @param titleColor
     *            The title colour to use in place of a header item
     * @param setup
     *            Populates the preset's slots
     */
    @SuppressWarnings("deprecation")
    default void createPreset(SlimefunItem item, String title, ChatColor titleColor, Consumer<BlockMenuPreset> setup) {
        createPreset(item, title, (Consumer<BlockMenuPreset>) preset -> {
            preset.optOutOfHeaderItem(titleColor);
            setup.accept(preset);
        });
    }

}

