package io.github.thebusybiscuit.slimefun5.implementation.listeners;

import io.github.thebusybiscuit.slimefun5.utils.compatibility.HandCompat;

import java.util.Optional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event.Result;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import io.github.bakedlibs.dough.protection.Interaction;
import io.github.thebusybiscuit.slimefun5.api.events.PlayerRightClickEvent;
import io.github.thebusybiscuit.slimefun5.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun5.core.handlers.BlockUseHandler;
import io.github.thebusybiscuit.slimefun5.core.handlers.ItemUseHandler;
import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun5.implementation.SlimefunItems;
import io.github.thebusybiscuit.slimefun5.utils.SlimefunUtils;

import me.mrCookieSlime.Slimefun.api.BlockStorage;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenuPreset;
import me.mrCookieSlime.Slimefun.api.inventory.UniversalBlockMenu;

/**
 * This {@link Listener} listens to the {@link PlayerInteractEvent}.
 * It is also responsible for calling our {@link PlayerRightClickEvent} and triggering any
 * {@link ItemUseHandler} or {@link BlockUseHandler} for the clicked {@link ItemStack} or {@link Block}.
 * 
 * @author TheBusyBiscuit
 * @author Liruxo
 * 
 * @see PlayerRightClickEvent
 * @see ItemUseHandler
 * @see BlockUseHandler
 *
 */
public class SlimefunItemInteractListener implements Listener {

    public SlimefunItemInteractListener(@Nonnull Slimefun plugin) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onRightClick(PlayerInteractEvent e) {
        if (e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            // Exclude the Debug Fish here because it is handled in a seperate Listener
            if (SlimefunUtils.isItemSimilar(e.getItem(), SlimefunItems.DEBUG_FISH.item(), true)) {
                return;
            }

            // Fixes #4087 - Prevents players from interacting with a block that is about to be deleted
            // We especially don't want to open inventories as that can cause duplication
            if (e.getClickedBlock() != null && Slimefun.getTickerTask().isDeletedSoon(e.getClickedBlock().getLocation())) {
                e.setCancelled(true);
                return;
            }

            // Fire our custom Event
            PlayerRightClickEvent event = new PlayerRightClickEvent(e);
            Bukkit.getPluginManager().callEvent(event);

            boolean itemUsed = HandCompat.getHand(e) == HandCompat.OFF_HAND;

            // Only handle the Item if it hasn't been denied
            if (event.useItem() != Result.DENY) {
                rightClickItem(e, event, itemUsed);
            }

            if (!itemUsed && event.useBlock() != Result.DENY && !rightClickBlock(event)) {
                return;
            }

            /**
             * If the original Event was not denied but the custom one was,
             * we also want to deny the original one.
             * This only applies for non-denied events because we do not want to
             * override any protective checks.
             */

            if (e.useInteractedBlock() != Result.DENY) {
                e.setUseInteractedBlock(event.useBlock());
            }

            if (e.useItemInHand() != Result.DENY) {
                e.setUseItemInHand(event.useItem());
            }
        }
    }

    @ParametersAreNonnullByDefault
    private boolean rightClickItem(PlayerInteractEvent e, PlayerRightClickEvent event, boolean defaultValue) {
        Optional<SlimefunItem> optional = event.getSlimefunItem();

        if (optional.isPresent()) {
            SlimefunItem sfItem = optional.get();

            if (sfItem.canUse(e.getPlayer(), true)) {
                return sfItem.callItemHandler(ItemUseHandler.class, handler -> handler.onRightClick(event));
            } else {
                event.setUseItem(Result.DENY);
            }
        }

        return defaultValue;
    }

    @ParametersAreNonnullByDefault
    private boolean rightClickBlock(PlayerRightClickEvent event) {
        Optional<SlimefunItem> optional = event.getSlimefunBlock();

        if (optional.isPresent()) {
            SlimefunItem sfItem = optional.get();

            if (!sfItem.canUse(event.getPlayer(), true)) {
                event.getInteractEvent().setCancelled(true);
                return false;
            }

            claimOwnership(event.getPlayer(), event.getInteractEvent().getClickedBlock());

            boolean interactable = sfItem.callItemHandler(BlockUseHandler.class, handler -> handler.onRightClick(event));

            if (!interactable) {
                Player p = event.getPlayer();

                if (BlockMenuPreset.isInventory(sfItem.getId())) {
                    openInventory(p, sfItem, event.getInteractEvent().getClickedBlock(), event);
                    return false;
                }
            }
        }

        return true;
    }

    @ParametersAreNonnullByDefault
    private void openInventory(Player p, SlimefunItem item, Block clickedBlock, PlayerRightClickEvent event) {
        try {
            if (!p.isSneaking() || event.getItem().getType() == Material.AIR) {
                event.getInteractEvent().setCancelled(true);

                if (BlockStorage.hasUniversalInventory(item.getId())) {
                    UniversalBlockMenu menu = BlockStorage.getUniversalInventory(item.getId());

                    if (menu.canOpen(clickedBlock, p)) {
                        menu.open(p);
                    } else {
                        denyAccess(p, menu.getPreset(), clickedBlock);
                    }
                } else if (BlockStorage.getStorage(clickedBlock.getWorld()).hasInventory(clickedBlock.getLocation())) {
                    BlockMenu menu = BlockStorage.getInventory(clickedBlock.getLocation());

                    if (menu.canOpen(clickedBlock, p)) {
                        menu.open(p);
                    } else {
                        denyAccess(p, menu.getPreset(), clickedBlock);
                    }
                }
            }
        } catch (Exception | LinkageError x) {
            item.error("An Exception was caught while trying to open the Inventory", x);
        }
    }

    /**
     * Tells {@code p} why a menu refused to open, preferring the preset's own reason over the generic
     * permission message - a bespoke multiblock that is not finished is not a permission problem, and
     * saying so sends the player looking for a claim they do not need.
     */
    @ParametersAreNonnullByDefault
    private void denyAccess(Player p, BlockMenuPreset preset, Block block) {
        String reason = preset == null ? null : preset.getAccessDenialMessage(block, p);

        if (reason != null) {
            p.sendMessage(reason);
            return;
        }

        Slimefun.getLocalization().sendMessage(p, "inventory.no-access", true);
    }

    /**
     * Claims the Slimefun block at {@code block} for {@code p} when nobody owns it yet, so a machine placed
     * before ownership was recorded - or by a player who has since been removed - still gets an owner from
     * ordinary use, the way a registered multiblock already does.
     *
     * @implNote Gated on the region protection check rather than claiming on any interact: without it a
     *           visitor right-clicking an unowned machine inside someone else's claim would become its
     *           owner. Mirrors the gate {@code MultiBlockMachine}'s interaction handler already applies.
     */
    private void claimOwnership(@Nonnull Player p, @Nullable Block block) {
        if (block == null || BlockStorage.getLocationInfo(block.getLocation(), "owner") != null) {
            return;
        }

        if (Slimefun.getProtectionManager().hasPermission(p, block.getLocation(), Interaction.INTERACT_BLOCK)) {
            BlockStorage.addBlockInfo(block, "owner", p.getUniqueId().toString(), true);
        }
    }

}

