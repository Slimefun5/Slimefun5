package io.github.thebusybiscuit.slimefun5.implementation.listeners;

import io.github.thebusybiscuit.slimefun5.utils.compatibility.BlockDataCompat;
import javax.annotation.Nonnull;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Piston;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.FallingBlock;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.inventory.ItemStack;

import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun5.utils.tags.SlimefunTag;

import me.mrCookieSlime.Slimefun.api.BlockStorage;

/**
 * This {@link Listener} is responsible for listening to any physics-based events, such
 * as {@link EntityChangeBlockEvent} or a {@link BlockPistonEvent}.
 * 
 * This ensures that a {@link Piston} cannot be abused to break Slimefun blocks.
 * 
 * @author VoidAngel
 * @author Poslovitch
 * @author TheBusyBiscuit
 * @author AccelShark
 *
 */
public class BlockPhysicsListener implements Listener {

    public BlockPhysicsListener(@Nonnull Slimefun plugin) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockFall(EntityChangeBlockEvent e) {
        if (e.getEntity().getType() == EntityType.FALLING_BLOCK && BlockStorage.hasBlockInfo(e.getBlock())) {
            e.setCancelled(true);

            if (isExternalBlockLanding(e)) {
                FallingBlock block = (FallingBlock) e.getEntity();

                if (block.getDropItem()) {
                    block.getWorld().dropItemNaturally(block.getLocation(), new ItemStack(BlockDataCompat.getMaterial(block), 1));
                }
            }
        }
    }

    /**
     * Whether an external falling block is landing <em>on</em> the protected Slimefun block, rather
     * than the Slimefun block itself being dislodged into a falling block (where {@code getTo()} is AIR).
     *
     * @implNote Only the landing case may drop an item. Cancelling already keeps a dislodged Slimefun
     *           gravity block (e.g. a mid-air Rune Anvil) in place, so dropping there would duplicate it.
     */
    private boolean isExternalBlockLanding(@Nonnull EntityChangeBlockEvent e) {
        return e.getTo() != Material.AIR;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent e) {
        if (BlockStorage.hasBlockInfo(e.getBlock())) {
            e.setCancelled(true);
        } else {
            for (Block b : e.getBlocks()) {
                if (BlockStorage.hasBlockInfo(b) || (b.getRelative(e.getDirection()).getType() == Material.AIR && BlockStorage.hasBlockInfo(b.getRelative(e.getDirection())))) {
                    e.setCancelled(true);
                    break;
                }
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent e) {
        if (BlockStorage.hasBlockInfo(e.getBlock())) {
            e.setCancelled(true);
        } else if (e.isSticky()) {
            for (Block b : e.getBlocks()) {
                if (BlockStorage.hasBlockInfo(b) || (b.getRelative(e.getDirection()).getType() == Material.AIR && BlockStorage.hasBlockInfo(b.getRelative(e.getDirection())))) {
                    e.setCancelled(true);
                    break;
                }
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onLiquidFlow(BlockFromToEvent e) {
        Block block = e.getToBlock();
        Material type = block.getType();

        if (SlimefunTag.FLUID_SENSITIVE_MATERIALS.isTagged(type)) {
            if (BlockStorage.hasBlockInfo(block)) {
                e.setCancelled(true);
            } else {
                Location loc = block.getLocation();

                // Fixes #2496 - Make sure it is not a moving block
                if (Slimefun.getTickerTask().isOccupiedSoon(loc)) {
                    e.setCancelled(true);
                }
            }
        }
    }

    @EventHandler
    public void onBucketUse(PlayerBucketEmptyEvent e) {
        // Fix for placing water on player heads
        Location l = e.getBlockClicked().getRelative(e.getBlockFace()).getLocation();

        if (BlockStorage.hasBlockInfo(l)) {
            e.setCancelled(true);
        }
    }
}

