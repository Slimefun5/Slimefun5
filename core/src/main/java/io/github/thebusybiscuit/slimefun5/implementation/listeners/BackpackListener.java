package io.github.thebusybiscuit.slimefun5.implementation.listeners;

import io.github.thebusybiscuit.slimefun5.utils.compatibility.HandCompat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

import org.apache.commons.lang.Validate;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import io.github.thebusybiscuit.slimefun5.utils.compatibility.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;

import io.github.thebusybiscuit.slimefun5.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun5.api.player.PlayerBackpack;
import io.github.thebusybiscuit.slimefun5.api.player.PlayerProfile;
import io.github.thebusybiscuit.slimefun5.core.services.sounds.SoundEffect;
import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun5.implementation.items.backpacks.Cooler;
import io.github.thebusybiscuit.slimefun5.implementation.items.backpacks.SlimefunBackpack;

/**
 * This {@link Listener} is responsible for all events centered around a {@link SlimefunBackpack}.
 * This also includes the {@link Cooler}
 * 
 * @author TheBusyBiscuit
 * @author Walshy
 * @author NihilistBrew
 * @author AtomicScience
 * @author VoidAngel
 * @author John000708
 * 
 * @see SlimefunBackpack
 * @see PlayerBackpack
 *
 */
public class BackpackListener implements Listener {

    private final Map<UUID, ItemStack> backpacks = new HashMap<>();

    public void register(@Nonnull Slimefun plugin) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        Player p = (Player) e.getPlayer();

        if (markBackpackDirty(p)) {
            SoundEffect.BACKPACK_CLOSE_SOUND.playFor(p);
        }
    }

    private boolean markBackpackDirty(@Nonnull Player p) {
        ItemStack backpack = backpacks.remove(p.getUniqueId());

        if (backpack != null) {
            PlayerProfile.getBackpack(backpack, PlayerBackpack::markDirty);
            return true;
        } else {
            return false;
        }
    }

    @EventHandler
    public void onItemDrop(PlayerDropItemEvent e) {
        if (backpacks.containsKey(e.getPlayer().getUniqueId())) {
            ItemStack item = e.getItemDrop().getItemStack();
            SlimefunItem sfItem = SlimefunItem.getByItem(item);

            if (sfItem instanceof SlimefunBackpack) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        ItemStack item = backpacks.get(e.getWhoClicked().getUniqueId());

        if (item != null) {
            SlimefunItem backpack = SlimefunItem.getByItem(item);

            if (backpack instanceof SlimefunBackpack) {
                SlimefunBackpack slimefunBackpack = (SlimefunBackpack) backpack;                if (e.getClick() == ClickType.NUMBER_KEY) {
                    // Prevent disallowed items from being moved using number keys.
                    if (e.getClickedInventory().getType() != InventoryType.PLAYER) {
                        ItemStack hotbarItem = e.getWhoClicked().getInventory().getItem(e.getHotbarButton());

                        if (!isAllowed(slimefunBackpack, hotbarItem)) {
                            e.setCancelled(true);
                        }
                    }
                } else if ("SWAP_OFFHAND".equals(e.getClick().name())) {
                    if (e.getClickedInventory().getType() != InventoryType.PLAYER) {
                        // Fixes #3265 - Don't move disallowed items using the off hand.
                        ItemStack offHandItem = HandCompat.getOffHand(e.getWhoClicked().getInventory());

                        if (!isAllowed(slimefunBackpack, offHandItem)) {
                            e.setCancelled(true);
                        }
                    } else {
                        // Fixes #3664 - Do not swap the backpack to your off hand.
                        if (e.getCurrentItem() != null && e.getCurrentItem().isSimilar(item)) {
                            e.setCancelled(true);
                        }
                    }
                } else if (!isAllowed(slimefunBackpack, e.getCurrentItem())) {
                    e.setCancelled(true);
                }
            }
        }
    }

    private boolean isAllowed(@Nonnull SlimefunBackpack backpack, @Nullable ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return true;
        }

        return backpack.isItemAllowed(item, SlimefunItem.getByItem(item));
    }

    @ParametersAreNonnullByDefault
    public void openBackpack(Player p, ItemStack item, SlimefunBackpack backpack) {
        if (item.getAmount() == 1) {
            if (backpack.canUse(p, true) && !PlayerProfile.get(p, profile -> openBackpack(p, item, profile, backpack.getSize()))) {
                Slimefun.getLocalization().sendMessage(p, "messages.opening-backpack");
            }
        } else {
            Slimefun.getLocalization().sendMessage(p, "backpack.no-stack", true);
        }
    }

    /**
     * @implNote Gives the backpack a persistent identity if it has none yet - migrating a legacy lore id to
     *           persistent data, or assigning a fresh one via the supplier (which creates a new backpack and
     *           only runs when the backpack is genuinely new).
     */
    @ParametersAreNonnullByDefault
    private void openBackpack(Player p, ItemStack item, PlayerProfile profile, int size) {
        PlayerBackpack.ensureIdentity(item, p.getUniqueId(), () -> profile.createBackpack(size).getId());

        /*
         * If the current Player is already viewing a backpack (for whatever reason),
         * terminate that view.
         */
        if (markBackpackDirty(p)) {
            p.closeInventory();
        }

        // Check if someone else is currently viewing this backpack
        if (!backpacks.containsValue(item)) {
            PlayerProfile.getBackpack(item, backpack -> {
                // Commit to opening (play the sound, mark it as being viewed) only once the backpack has
                // actually resolved. Previously the sound played and the item was marked viewed up-front,
                // so a lost/stale identity left the player hearing the open sound while nothing opened.
                // On failure the callback never fires (PlayerProfile#getBackpack logs why) - no sound, no
                // stuck "being viewed" entry.
                if (backpack != null) {
                    SoundEffect.BACKPACK_OPEN_SOUND.playAt(p.getLocation(), SoundCategory.PLAYERS);
                    backpacks.put(p.getUniqueId(), item);
                    backpack.open(p);
                }
            });
        } else {
            Slimefun.getLocalization().sendMessage(p, "backpack.already-open", true);
        }
    }

    /**
     * This method sets the id for a backpack onto the given {@link ItemStack}.
     *
     * @param backpackOwner
     *            The owner of this backpack
     * @param item
     *            The {@link ItemStack} to modify
     * @param line
     *            Unused. Kept for binary compatibility with addons written against the lore-based identity
     * @param id
     *            The id of this backpack
     *
     * @implNote Identity is written to persistent data, not to a {@code "§7ID: <ID>"} lore line. Items carry
     *           no baked lore any more, so the old placeholder never exists to replace and this used to throw
     *           for every backpack it was given.
     */
    public void setBackpackId(@Nonnull OfflinePlayer backpackOwner, @Nonnull ItemStack item, int line, int id) {
        Validate.notNull(backpackOwner, "Backpacks must have an owner!");
        Validate.notNull(item, "Cannot set the id onto null!");

        PlayerBackpack.writeIdentity(item, backpackOwner.getUniqueId() + "#" + id);
    }
}

