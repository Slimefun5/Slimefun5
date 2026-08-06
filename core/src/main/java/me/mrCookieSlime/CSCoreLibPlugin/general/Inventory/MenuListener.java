package me.mrCookieSlime.CSCoreLibPlugin.general.Inventory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;

import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ChestMenu.AdvancedMenuClickHandler;
import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ChestMenu.MenuClickHandler;
import me.mrCookieSlime.Slimefun.api.BlockStorage;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;

/**
 * An old {@link Listener} for CS-CoreLib
 * This is an old remnant of CS-CoreLib, the last bits of the past. They will be removed once everything is
 *             updated.
 */
public class MenuListener implements Listener {

    static final Map<UUID, ChestMenu> menus = new HashMap<>();

    // Click-flood guard: a malicious client (or a misbehaving plugin) can spam inventory-click packets far
    // faster than any human, and every click forces a corrective inventory packet - which the per-viewer
    // translation layer re-renders in full. Left unbounded that amplifies into a packet/CPU storm that can
    // crash the server. No human clicks anywhere near this rate, so a ceiling per rolling window is safe.
    private static final long CLICK_WINDOW_MS = 1000L;
    private static final int MAX_CLICKS_PER_WINDOW = 40;
    private final Map<UUID, long[]> clickWindows = new HashMap<>(); // uuid -> [windowStart, count]

    public MenuListener(Plugin plugin) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /** True when this player has exceeded the safe click rate for the current window (flood in progress). */
    private boolean isClickFlooding(UUID uuid) {
        long now = System.currentTimeMillis();
        long[] window = clickWindows.get(uuid);

        if (window == null || now - window[0] > CLICK_WINDOW_MS) {
            clickWindows.put(uuid, new long[] { now, 1L });
            return false;
        }

        window[1]++;
        return window[1] > MAX_CLICKS_PER_WINDOW;
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        clickWindows.remove(e.getPlayer().getUniqueId());
        ChestMenu menu = menus.remove(e.getPlayer().getUniqueId());

        if (menu != null) {
            menu.getMenuCloseHandler().onClose((Player) e.getPlayer());

            // If this was a Slimefun block menu, re-check on the main thread (once the close has settled)
            // whether any viewer remains; if none, let its ticker return to the async fast path. Only ever
            // unmark when truly unviewed, so the async-tick-vs-click dupe guard is never dropped early.
            if (menu instanceof BlockMenu) {
                BlockMenu blockMenu = (BlockMenu) menu;
                Slimefun.runSync(() -> BlockStorage.setInventoryViewed(blockMenu.getLocation(), blockMenu.hasViewer()));
            }
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        ChestMenu menu = menus.get(e.getWhoClicked().getUniqueId());

        if (menu != null) {
            // Drop clicks past the safe rate: cancel (so nothing moves) but skip the handler + re-render
            // path that a flood would otherwise weaponise into a packet storm.
            if (isClickFlooding(e.getWhoClicked().getUniqueId())) {
                e.setCancelled(true);
                return;
            }

            // A double-click (COLLECT_TO_CURSOR) gathers matching items from the WHOLE view, bypassing the
            // per-slot handlers below - so it can vacuum protected display/output slots (which regenerate)
            // into the cursor = a duplication. Cancel it only when it would actually pull from such a slot,
            // leaving free input slots and the player's own inventory gatherable.
            if (e.getAction() == InventoryAction.COLLECT_TO_CURSOR && collectWouldTouchProtectedSlot(e.getCursor(), e.getInventory(), menu)) {
                e.setCancelled(true);
                return;
            }

            if (e.getRawSlot() < e.getInventory().getSize()) {
                MenuClickHandler handler = menu.getMenuClickHandler(e.getSlot());

                if (handler == null) {
                    e.setCancelled(!menu.isEmptySlotsClickable() && (e.getCurrentItem() == null || e.getCurrentItem().getType() == Material.AIR));
                } else if (handler instanceof AdvancedMenuClickHandler) {
                    e.setCancelled(!((AdvancedMenuClickHandler) handler).onClick(e, (Player) e.getWhoClicked(), e.getSlot(), e.getCursor(), new ClickAction(e.isRightClick(), e.isShiftClick())));
                } else {
                    e.setCancelled(!handler.onClick((Player) e.getWhoClicked(), e.getSlot(), e.getCurrentItem(), new ClickAction(e.isRightClick(), e.isShiftClick())));
                }
            } else {
                e.setCancelled(!menu.getPlayerInventoryClickHandler().onClick((Player) e.getWhoClicked(), e.getSlot(), e.getCurrentItem(), new ClickAction(e.isRightClick(), e.isShiftClick())));
            }
        }
    }

    /**
     * A drag isn't routed through the per-slot click handlers at all, so without this a player could
     * drag-place items into (or spread across) protected menu slots - e.g. bypass the output-slot
     * guard. Cancel any drag that touches a menu slot which carries a click handler; free slots (plain
     * input areas) and the player's own inventory are left draggable.
     */
    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        ChestMenu menu = menus.get(e.getWhoClicked().getUniqueId());

        if (menu != null && dragTouchesProtectedSlot(e.getRawSlots(), e.getInventory().getSize(), menu)) {
            e.setCancelled(true);
        }
    }

    /**
     * Whether a {@link InventoryAction#COLLECT_TO_CURSOR} would gather from a protected menu slot: a slot
     * carrying a click handler (display / output / button) that holds an item matching the cursor. Free
     * input slots (no handler) and the player's own inventory are not protected.
     */
    static boolean collectWouldTouchProtectedSlot(ItemStack cursor, Inventory top, ChestMenu menu) {
        if (cursor == null || cursor.getType() == Material.AIR) {
            return false;
        }

        for (int slot = 0; slot < top.getSize(); slot++) {
            if (menu.getMenuClickHandler(slot) != null) {
                ItemStack item = top.getItem(slot);

                if (item != null && item.getType() != Material.AIR && cursor.isSimilar(item)) {
                    return true;
                }
            }
        }

        return false;
    }

    /** Whether a drag touches a protected (handler-bearing) slot of the open menu's top inventory. */
    static boolean dragTouchesProtectedSlot(Iterable<Integer> rawSlots, int topSize, ChestMenu menu) {
        for (int rawSlot : rawSlots) {
            if (rawSlot < topSize && menu.getMenuClickHandler(rawSlot) != null) {
                return true;
            }
        }

        return false;
    }

}
