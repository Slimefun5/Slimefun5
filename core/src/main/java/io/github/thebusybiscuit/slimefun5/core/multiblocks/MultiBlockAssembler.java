package io.github.thebusybiscuit.slimefun5.core.multiblocks;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;

import io.github.bakedlibs.dough.protection.Interaction;
import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun5.utils.compatibility.InventoryCompat;
import io.github.thebusybiscuit.slimefun5.utils.compatibility.ReflectionCompat;
import io.github.thebusybiscuit.slimefun5.utils.compatibility.SoundCategory;
import io.github.thebusybiscuit.slimefun5.utils.compatibility.SoundCompat;
import io.github.thebusybiscuit.slimefun5.utils.compatibility.Tag;

/**
 * Builds a {@link MultiBlockMachine}'s whole structure in the world when a player "places" the machine
 * item, rather than the item being an unplaceable no-op. This works for every core and addon multiblock
 * because the layout is read straight from its {@link MultiBlock}.
 * <p>
 * The structure is anchored on the clicked surface - its bottom row sits where a normal block would go and
 * it rises upward - and oriented to the player's facing. It only builds if every required cell is free (or
 * already the matching hand-placed block) and the player may build there; otherwise nothing is placed or
 * consumed. Cells are placed bottom-up over a few ticks as a simple assembly animation, and (for a
 * dispenser-holding multiblock) the builder is set as owner so the redstone auto-craft works immediately.
 */
public final class MultiBlockAssembler {

    // Ticks between each placed cell - turns the build into a short bottom-up animation.
    private static final long CELL_STAGGER = 2L;

    private MultiBlockAssembler() {}

    /**
     * Attempts to assemble {@code machine}'s structure with its bottom-centre cell at {@code base}.
     *
     * @param machine
     *            The multiblock machine to build
     * @param base
     *            The block the structure's bottom-centre cell should occupy (the clicked surface)
     * @param p
     *            The player building it
     * @param hand
     *            The hand holding the machine item (for consumption)
     *
     * @return Whether the structure was built (false = blocked or no permission; nothing consumed)
     */
    @ParametersAreNonnullByDefault
    public static boolean assemble(MultiBlockMachine machine, Block base, Player p, EquipmentSlot hand) {
        Material[] layout = machine.getMultiBlock().getStructure();

        // blocks[4] (structure centre) sits one above the clicked surface so the bottom row rests on it.
        Block center = base.getRelative(BlockFace.UP);
        BlockFace width = widthFacing(p);
        BlockFace far = width.getOppositeFace();

        // Map the 9-cell layout to world blocks in the same orientation MultiBlock#matches reads them.
        Block[] targets = new Block[9];
        targets[0] = center.getRelative(BlockFace.UP).getRelative(width);
        targets[1] = center.getRelative(BlockFace.UP);
        targets[2] = center.getRelative(BlockFace.UP).getRelative(far);
        targets[3] = center.getRelative(width);
        targets[4] = center;
        targets[5] = center.getRelative(far);
        targets[6] = center.getRelative(BlockFace.DOWN).getRelative(width);
        targets[7] = center.getRelative(BlockFace.DOWN);
        targets[8] = center.getRelative(BlockFace.DOWN).getRelative(far);

        World world = base.getWorld();

        // Build nothing unless the entire structure can be placed.
        for (int i = 0; i < 9; i++) {
            Material required = layout[i];

            if (required == null) {
                continue;
            }

            Block t = targets[i];

            if (t.getY() < minHeight(world) || t.getY() >= world.getMaxHeight()) {
                return fail(p, "there is not enough vertical space");
            }

            if (!Slimefun.getProtectionManager().hasPermission(p, t.getLocation(), Interaction.PLACE_BLOCK)) {
                return fail(p, "you cannot build here");
            }

            // A cell must be empty, or already hold a block that satisfies this part of the structure.
            if (!t.isEmpty() && !satisfies(t.getType(), required)) {
                return fail(p, "there is not enough space");
            }
        }

        InventoryCompat.consumeHeldItem(p, hand, 1, false);

        // Place cells bottom row -> middle -> top, staggered, skipping any already provided by hand.
        int[] order = { 6, 7, 8, 3, 4, 5, 0, 1, 2 };
        long delay = 0;

        for (int idx : order) {
            Material required = layout[idx];

            if (required == null) {
                continue;
            }

            Block cell = targets[idx];

            if (!cell.isEmpty() && satisfies(cell.getType(), required)) {
                continue;
            }

            Slimefun.runSync(() -> {
                cell.setType(required);
                SoundCompat.playAt(cell.getLocation(), "BLOCK_STONE_PLACE", SoundCategory.BLOCKS, 0.6F, 1F);
            }, delay);

            delay += CELL_STAGGER;
        }

        // Ownership is keyed by the structure's dispenser (if any) and gates the redstone auto-craft.
        for (int i = 0; i < 9; i++) {
            if (layout[i] == Material.DISPENSER) {
                Slimefun.getMultiBlockOwnership().setOwnerIfAbsent(targets[i].getLocation(), p.getUniqueId());
                break;
            }
        }

        return true;
    }

    /**
     * The horizontal axis the 3-wide structure spans, chosen so its face points at the player. Symmetric
     * structures are unaffected; asymmetric ones still match because the cells are placed in this same axis.
     */
    @Nonnull
    private static BlockFace widthFacing(@Nonnull Player p) {
        float yaw = ((p.getLocation().getYaw() % 360) + 360) % 360;

        // Facing west (~90) or east (~270): the wall spans north-south. Otherwise it spans east-west.
        if ((yaw >= 45 && yaw < 135) || (yaw >= 225 && yaw < 315)) {
            return BlockFace.NORTH;
        }

        return BlockFace.EAST;
    }

    /**
     * Whether an existing block satisfies a required structure material, mirroring
     * {@link MultiBlock}'s tolerant matching (wood variants, ...) so hand-placed parts count.
     */
    private static boolean satisfies(@Nonnull Material existing, @Nonnull Material required) {
        if (existing == required) {
            return true;
        }

        for (Tag<Material> tag : MultiBlock.getSupportedTags()) {
            if (tag.isTagged(existing) && tag.isTagged(required)) {
                return true;
            }
        }

        return false;
    }

    /**
     * The world's minimum build height. {@code World#getMinHeight()} is 1.17+ and absent from the legacy
     * compile API, so it is resolved reflectively; older servers (min height 0) fall back to 0.
     */
    private static int minHeight(@Nonnull World world) {
        try {
            Object result = ReflectionCompat.invoke(world, "getMinHeight");
            return result instanceof Integer ? (Integer) result : 0;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    @ParametersAreNonnullByDefault
    private static boolean fail(Player p, String reason) {
        p.sendMessage(ChatColor.RED + "Cannot assemble this multiblock: " + reason + ".");
        return false;
    }
}
