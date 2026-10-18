package io.github.thebusybiscuit.slimefun5.core.checks.sandbox;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * A click on the player's open window, as a server handles one: the action vanilla would take is worked out
 * from the slot and cursor, the real {@link InventoryClickEvent} goes through every plugin's listeners, and,
 * unless a listener cancelled it, vanilla's move is applied to the state the listeners left.
 *
 * @implNote The moves conserve items by construction, so any change in the total item count across a click
 *           comes from a listener or a click handler.
 */
public final class InventoryClicks {

    private InventoryClicks() {}

    /** What one click did. */
    public static final class Outcome {

        private final InventoryAction action;
        private final boolean cancelled;

        Outcome(InventoryAction action, boolean cancelled) {
            this.action = action;
            this.cancelled = cancelled;
        }

        public InventoryAction action() {
            return action;
        }

        public boolean cancelled() {
            return cancelled;
        }
    }

    /** @param hotbarButton the hotbar slot a {@link ClickType#NUMBER_KEY} click names; ignored otherwise */
    public static Outcome click(Player player, int rawSlot, ClickType click, int hotbarButton) {
        ViewAccess view = new ViewAccess(player.getOpenInventory());
        InventoryAction action = actionFor(view, rawSlot, click, hotbarButton);
        InventoryType.SlotType slotType = slotType(view, rawSlot);
        InventoryClickEvent event = click == ClickType.NUMBER_KEY
            ? new InventoryClickEvent(view.view(), slotType, rawSlot, click, action, hotbarButton)
            : new InventoryClickEvent(view.view(), slotType, rawSlot, click, action);
        Bukkit.getPluginManager().callEvent(event);
        if (!event.isCancelled() && player.getOpenInventory() == view.view()) {
            apply(player, view, rawSlot, action, hotbarButton);
        }
        return new Outcome(action, event.isCancelled());
    }

    /** What {@code InventoryView#getSlotType} answers from 1.9, for a chest-like window over the player's inventory. */
    private static InventoryType.SlotType slotType(ViewAccess view, int rawSlot) {
        int topSize = view.top().getSize();
        if (rawSlot < 0 || rawSlot >= topSize + 36) {
            return InventoryType.SlotType.OUTSIDE;
        }
        return rawSlot >= topSize + 27 ? InventoryType.SlotType.QUICKBAR : InventoryType.SlotType.CONTAINER;
    }

    static InventoryAction actionFor(ViewAccess view, int rawSlot, ClickType click, int hotbarButton) {
        ItemStack current = view.item(rawSlot);
        ItemStack cursor = view.cursor();
        boolean hasCurrent = present(current);
        boolean hasCursor = present(cursor);
        switch (click) {
            case LEFT:
                if (hasCurrent && hasCursor) {
                    if (!current.isSimilar(cursor)) {
                        return InventoryAction.SWAP_WITH_CURSOR;
                    }
                    int room = current.getMaxStackSize() - current.getAmount();
                    return room <= 0 ? InventoryAction.NOTHING : room >= cursor.getAmount() ? InventoryAction.PLACE_ALL : InventoryAction.PLACE_SOME;
                }
                return hasCurrent ? InventoryAction.PICKUP_ALL : hasCursor ? InventoryAction.PLACE_ALL : InventoryAction.NOTHING;
            case RIGHT:
                if (hasCurrent && hasCursor) {
                    if (!current.isSimilar(cursor)) {
                        return InventoryAction.SWAP_WITH_CURSOR;
                    }
                    return current.getAmount() < current.getMaxStackSize() ? InventoryAction.PLACE_ONE : InventoryAction.NOTHING;
                }
                return hasCurrent ? InventoryAction.PICKUP_HALF : hasCursor ? InventoryAction.PLACE_ONE : InventoryAction.NOTHING;
            case SHIFT_LEFT:
            case SHIFT_RIGHT:
                return hasCurrent ? InventoryAction.MOVE_TO_OTHER_INVENTORY : InventoryAction.NOTHING;
            case NUMBER_KEY:
                return hasCurrent || present(view.bottom().getItem(hotbarButton)) ? InventoryAction.HOTBAR_SWAP : InventoryAction.NOTHING;
            case DROP:
                return hasCurrent && !hasCursor ? InventoryAction.DROP_ONE_SLOT : InventoryAction.NOTHING;
            case CONTROL_DROP:
                return hasCurrent && !hasCursor ? InventoryAction.DROP_ALL_SLOT : InventoryAction.NOTHING;
            case DOUBLE_CLICK:
                return hasCursor ? InventoryAction.COLLECT_TO_CURSOR : InventoryAction.NOTHING;
            default:
                throw new IllegalArgumentException("The sandbox does not simulate " + click + " clicks");
        }
    }

    private static void apply(Player player, ViewAccess view, int rawSlot, InventoryAction action, int hotbarButton) {
        ItemStack current = view.item(rawSlot);
        ItemStack cursor = view.cursor();
        switch (action) {
            case PICKUP_ALL:
                if (present(current)) {
                    view.setCursor(current.clone());
                    view.setItem(rawSlot, null);
                }
                break;
            case PICKUP_HALF:
                if (present(current)) {
                    int taken = (current.getAmount() + 1) / 2;
                    view.setCursor(withAmount(current, taken));
                    view.setItem(rawSlot, withAmount(current, current.getAmount() - taken));
                }
                break;
            case PLACE_ALL:
            case PLACE_SOME:
                place(view, rawSlot, current, cursor, present(cursor) ? cursor.getAmount() : 0);
                break;
            case PLACE_ONE:
                place(view, rawSlot, current, cursor, 1);
                break;
            case SWAP_WITH_CURSOR:
                view.setItem(rawSlot, cursor == null ? null : cursor.clone());
                view.setCursor(current == null ? null : current.clone());
                break;
            case MOVE_TO_OTHER_INVENTORY:
                if (present(current)) {
                    Inventory target = rawSlot < view.top().getSize() ? view.bottom() : view.top();
                    ItemStack leftover = target.addItem(current.clone()).get(0);
                    view.setItem(rawSlot, leftover);
                }
                break;
            case HOTBAR_SWAP:
                ItemStack hotbar = view.bottom().getItem(hotbarButton);
                view.setItem(rawSlot, hotbar == null ? null : hotbar.clone());
                view.bottom().setItem(hotbarButton, current == null ? null : current.clone());
                break;
            case DROP_ONE_SLOT:
                if (present(current)) {
                    player.getWorld().dropItemNaturally(player.getLocation(), withAmount(current, 1));
                    view.setItem(rawSlot, withAmount(current, current.getAmount() - 1));
                }
                break;
            case DROP_ALL_SLOT:
                if (present(current)) {
                    player.getWorld().dropItemNaturally(player.getLocation(), current.clone());
                    view.setItem(rawSlot, null);
                }
                break;
            case COLLECT_TO_CURSOR:
                collect(view, cursor);
                break;
            default:
                break;
        }
    }

    private static void place(ViewAccess view, int rawSlot, ItemStack current, ItemStack cursor, int wanted) {
        if (!present(cursor)) {
            return;
        }
        if (!present(current)) {
            int moved = Math.min(wanted, cursor.getAmount());
            view.setItem(rawSlot, withAmount(cursor, moved));
            view.setCursor(withAmount(cursor, cursor.getAmount() - moved));
        } else if (current.isSimilar(cursor)) {
            int moved = Math.min(Math.min(wanted, cursor.getAmount()), current.getMaxStackSize() - current.getAmount());
            if (moved > 0) {
                view.setItem(rawSlot, withAmount(current, current.getAmount() + moved));
                view.setCursor(withAmount(cursor, cursor.getAmount() - moved));
            }
        }
    }

    private static void collect(ViewAccess view, ItemStack cursor) {
        if (!present(cursor)) {
            return;
        }
        int amount = cursor.getAmount();
        for (int raw = 0; raw < view.countSlots() && amount < cursor.getMaxStackSize(); raw++) {
            ItemStack item = view.item(raw);
            if (present(item) && item.isSimilar(cursor)) {
                int taken = Math.min(item.getAmount(), cursor.getMaxStackSize() - amount);
                amount += taken;
                view.setItem(raw, withAmount(item, item.getAmount() - taken));
            }
        }
        view.setCursor(withAmount(cursor, amount));
    }

    private static ItemStack withAmount(ItemStack item, int amount) {
        if (amount <= 0) {
            return null;
        }
        ItemStack copy = item.clone();
        copy.setAmount(amount);
        return copy;
    }

    static boolean present(ItemStack item) {
        return item != null && item.getType() != Material.AIR && item.getAmount() > 0;
    }
}
