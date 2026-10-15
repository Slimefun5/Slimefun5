package io.github.thebusybiscuit.slimefun5.implementation.listeners;

import io.github.thebusybiscuit.slimefun5.utils.compatibility.MaterialCompat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.inventory.ItemStack;

import io.github.thebusybiscuit.slimefun5.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun5.core.attributes.WitherProof;
import io.github.thebusybiscuit.slimefun5.core.handlers.BlockBreakHandler;
import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;

import me.mrCookieSlime.Slimefun.api.BlockStorage;

/**
 * The {@link ExplosionsListener} is a {@link Listener} which listens to any explosion events.
 * Any {@link WitherProof} block is excluded from these explosions and this {@link Listener} also
 * calls the explosive part of the {@link BlockBreakHandler}.
 * 
 * @author TheBusyBiscuit
 * 
 * @see BlockBreakHandler
 * @see WitherProof
 *
 */
public class ExplosionsListener implements Listener {

    private static final Set<String> NON_DESTRUCTIVE_RESULTS = new HashSet<>(Arrays.asList("KEEP", "TRIGGER_BLOCK"));

    public ExplosionsListener(@Nonnull Slimefun plugin) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        removeResistantBlocks(e.blockList().iterator(), destroysBlocks(e));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        removeResistantBlocks(e.blockList().iterator(), destroysBlocks(e));
    }

    /**
     * @implNote Wind and breeze charges (1.21+) fire an explode event whose block list vanilla only
     *           triggers (doors, buttons), never destroys. {@code getExplosionResult()} is absent from the
     *           1.8.8 API this module compiles against, hence the reflection; without it every explosion
     *           destroys, as it always did.
     */
    private static boolean destroysBlocks(@Nonnull Event e) {
        try {
            Object result = e.getClass().getMethod("getExplosionResult").invoke(e);
            return result == null || !NON_DESTRUCTIVE_RESULTS.contains(result.toString());
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return true;
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityBreak(EntityChangeBlockEvent e) {
        if (e.getEntity().getType() == EntityType.WITHER || e.getEntity().getType() == EntityType.WITHER_SKULL) {
            removeResistantBlock(e.getBlock());
        }
    }

    private void removeResistantBlocks(@Nonnull Iterator<Block> blocks, boolean destroy) {
        while (blocks.hasNext()) {
            Block block = blocks.next();
            SlimefunItem item = BlockStorage.check(block);

            if (item != null) {
                blocks.remove();
                if (destroy) {
                    removeResistantBlock(block, item);
                }
            }
        }
    }

    private void removeResistantBlock(@Nonnull Block block) {
        SlimefunItem slimefunItem = BlockStorage.check(block);

        if (slimefunItem != null) {
            removeResistantBlock(block, slimefunItem);
        }
    }

    private void removeResistantBlock(@Nonnull Block block, @Nonnull SlimefunItem slimefunItem) {
        // Fixes #3414 - This check removes the ghost block created by withers.
        if (!(slimefunItem instanceof WitherProof)
            && !slimefunItem.callItemHandler(BlockBreakHandler.class, handler -> handleExplosion(handler, block))
        ) {
            BlockStorage.clearBlockInfo(block);
            block.setType(Material.AIR);
        }
    }

    @ParametersAreNonnullByDefault
    private void handleExplosion(BlockBreakHandler handler, Block block) {
        if (handler.isExplosionAllowed(block)) {
            BlockStorage.clearBlockInfo(block);
            block.setType(Material.AIR);

            List<ItemStack> drops = new ArrayList<>();
            handler.onExplode(block, drops);

            for (ItemStack drop : drops) {
                if (drop != null && !MaterialCompat.isAir(drop.getType())) {
                    block.getWorld().dropItemNaturally(block.getLocation(), drop);
                }
            }
        }
    }
}

