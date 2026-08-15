package io.github.thebusybiscuit.slimefun5.core.multiblocks;

import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;

import io.github.bakedlibs.dough.protection.Interaction;
import io.github.thebusybiscuit.slimefun5.api.events.BlockPlacerPlaceEvent;
import io.github.thebusybiscuit.slimefun5.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun5.core.handlers.BlockPlaceHandler;
import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun5.utils.compatibility.InventoryCompat;
import io.github.thebusybiscuit.slimefun5.utils.compatibility.ReflectionCompat;
import io.github.thebusybiscuit.slimefun5.utils.compatibility.SoundCategory;
import io.github.thebusybiscuit.slimefun5.utils.compatibility.SoundCompat;
import io.github.thebusybiscuit.slimefun5.utils.compatibility.Tag;

import me.mrCookieSlime.Slimefun.api.BlockStorage;

/**
 * Builds a multiblock's whole structure in the world when a player "places" the machine item, rather than
 * the item being an unplaceable no-op. This works for every core and addon multiblock because the layout is
 * read straight from its {@link MultiBlock}, and (via {@link #assembleAround}) for any other addon item that
 * anchors its own structure the same way, without having to become a {@link MultiBlockMachine} itself.
 * <p>
 * The structure is anchored on the clicked surface - its bottom row sits where a normal block would go and
 * it rises upward - and oriented to the player's facing. It only builds if every required cell is free (or
 * already the matching hand-placed block) and the player may build there; otherwise nothing is placed or
 * consumed. Cells are placed bottom-up over a few ticks as a simple assembly animation. A cell may require a
 * specific custom (Slimefun) block rather than just a {@link Material}; that block is placed and registered
 * in {@link BlockStorage} exactly as a normal placement would, including calling its
 * {@link BlockPlaceHandler}. If a required custom block's id cannot be resolved, or its item declines
 * automated placement, nothing is built and nothing is consumed.
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
        String[] customBlocks = machine.getMultiBlock().getCustomBlocks();

        // blocks[4] (structure centre) sits one above the clicked surface so the bottom row rests on it.
        Block center = base.getRelative(BlockFace.UP);

        if (!canBuildAt(layout, customBlocks, center, p)) {
            return false;
        }

        InventoryCompat.consumeHeldItem(p, hand, 1, false);
        placeStaggered(layout, customBlocks, center, p);

        // Ownership is keyed by the structure's dispenser (if any) and gates the redstone auto-craft.
        Block[] targets = targetsAround(center, widthFacing(p));

        for (int i = 0; i < 9; i++) {
            if (layout[i] == Material.DISPENSER) {
                Slimefun.getMultiBlockOwnership().setOwnerIfAbsent(targets[i].getLocation(), p.getUniqueId());
                break;
            }
        }

        return true;
    }

    /**
     * Builds a 9-cell structure (see {@link MultiBlock#getStructure()} for the cell layout) with its centre
     * at {@code center}, for a {@link SlimefunItem} that anchors its own multiblock structure directly -
     * rather than being a {@link MultiBlockMachine} whose item is an unplaceable token - such as a
     * placeable controller block that should auto-build the machinery around it. The anchor block is
     * expected to already occupy the centre cell (index 4), whose {@code layout} entry should be
     * {@code null} so it is left alone.
     * <p>
     * Unlike {@link #assemble}, nothing is consumed and no dispenser ownership is claimed - both are
     * specific to the core multiblock-machine/auto-craft feature, and are the caller's concern if relevant.
     *
     * @param layout
     *            The 9 required {@link Material materials}, {@code null} for a cell to leave alone
     * @param customBlocks
     *            Per-cell Slimefun item id (or {@code null}), see {@link MultiBlock#getCustomBlocks()};
     *            may itself be {@code null} for an all-{@link Material} structure
     * @param center
     *            The block the structure's centre cell (index 4) occupies
     * @param p
     *            The player building it
     *
     * @return Whether the structure was built (false = blocked or no permission; nothing placed)
     */
    public static boolean assembleAround(@Nonnull Material[] layout, @Nullable String[] customBlocks, @Nonnull Block center, @Nonnull Player p) {
        String[] custom = customBlocks != null ? customBlocks : new String[layout.length];

        if (!canBuildAt(layout, custom, center, p)) {
            return false;
        }

        placeStaggered(layout, custom, center, p);
        return true;
    }

    /**
     * Whether {@code layout} could be built around {@code center}: every required cell is in-bounds, the
     * player may build there, and (if occupied) it already satisfies its requirement. Resolves every
     * required custom block up front so a build never starts if one cannot be placed. Consumes and places
     * nothing either way.
     */
    @ParametersAreNonnullByDefault
    private static boolean canBuildAt(Material[] layout, String[] customBlocks, Block center, Player p) {
        if (layout.length != 9 || customBlocks.length != 9) {
            throw new IllegalArgumentException("A multiblock layout must have a length of 9!");
        }

        Block[] targets = targetsAround(center, widthFacing(p));
        World world = center.getWorld();

        for (int i = 0; i < 9; i++) {
            Material required = layout[i];

            if (required == null) {
                continue;
            }

            String customId = customBlocks[i];

            if (customId != null && !canPlaceCustomBlock(customId)) {
                return fail(p, "messages.multiblock-assembler.missing-block");
            }

            Block t = targets[i];

            if (t.getY() < minHeight(world) || t.getY() >= world.getMaxHeight()) {
                return fail(p, "messages.multiblock-assembler.no-space");
            }

            // ProtectionManager is only wired up once IntegrationsManager#onServerStart runs, which unit
            // tests never tick; skip the check there, same as e.g. RuneAnvil#canUse does.
            if (!Slimefun.instance().isUnitTest() && !Slimefun.getProtectionManager().hasPermission(p, t.getLocation(), Interaction.PLACE_BLOCK)) {
                return fail(p, "messages.multiblock-assembler.no-permission");
            }

            if (!t.isEmpty() && !satisfies(t, required, customId)) {
                return fail(p, "messages.multiblock-assembler.no-space");
            }
        }

        return true;
    }

    /**
     * Places cells bottom row -> middle -> top, staggered, skipping any already provided by hand. Assumes
     * {@link #canBuildAt} already passed for the exact same arguments.
     */
    @ParametersAreNonnullByDefault
    private static void placeStaggered(Material[] layout, String[] customBlocks, Block center, Player p) {
        Block[] targets = targetsAround(center, widthFacing(p));
        int[] order = { 6, 7, 8, 3, 4, 5, 0, 1, 2 };
        long delay = 0;

        for (int idx : order) {
            Material required = layout[idx];

            if (required == null) {
                continue;
            }

            Block cell = targets[idx];
            String customId = customBlocks[idx];

            if (!cell.isEmpty() && satisfies(cell, required, customId)) {
                continue;
            }

            Slimefun.runSync(() -> {
                if (customId != null) {
                    placeCustomBlock(center, cell, required, customId);
                } else {
                    cell.setType(required);
                }

                SoundCompat.playAt(cell.getLocation(), "BLOCK_STONE_PLACE", SoundCategory.BLOCKS, 0.6F, 1F);
            }, delay);

            delay += CELL_STAGGER;
        }
    }

    /**
     * Places the custom block registered as {@code customId} into {@code cell}, mirroring what a normal
     * hand-placement does: the {@link Material}, its {@link BlockStorage} identity (and tile-entity block
     * data, if any), and its {@link BlockPlaceHandler}. Uses {@link BlockPlacerPlaceEvent} - the same
     * programmatic-placement hook a {@link io.github.thebusybiscuit.slimefun5.implementation.items.blocks.BlockPlacer}
     * fires - since, like that case, no real {@link org.bukkit.event.block.BlockPlaceEvent} exists for this
     * cell. {@code anchor} is the structure's anchor block, passed through as the event's "placer" block.
     */
    @ParametersAreNonnullByDefault
    private static void placeCustomBlock(Block anchor, Block cell, Material required, String customId) {
        SlimefunItem sfItem = SlimefunItem.getById(customId);

        if (sfItem == null) {
            // Resolved during canBuildAt; something would have had to unregister it in the meantime.
            return;
        }

        BlockPlacerPlaceEvent event = new BlockPlacerPlaceEvent(anchor, sfItem.getItem(), cell);
        Bukkit.getPluginManager().callEvent(event);

        if (event.isCancelled()) {
            return;
        }

        cell.setType(required);

        if (Slimefun.getBlockDataService().isTileEntity(cell.getType())) {
            Slimefun.getBlockDataService().setBlockData(cell, sfItem.getId());
        }

        BlockStorage.addBlockInfo(cell, "id", sfItem.getId(), true);
        sfItem.callItemHandler(BlockPlaceHandler.class, handler -> handler.onBlockPlacerPlace(event));
    }

    /**
     * Whether {@code customId} both resolves to a registered {@link SlimefunItem} and, if it declares a
     * {@link BlockPlaceHandler}, that handler allows automated (non-player-click) placement.
     */
    private static boolean canPlaceCustomBlock(@Nonnull String customId) {
        SlimefunItem sfItem = SlimefunItem.getById(customId);

        if (sfItem == null) {
            return false;
        }

        AtomicBoolean allowed = new AtomicBoolean(true);
        boolean hasHandler = sfItem.callItemHandler(BlockPlaceHandler.class, handler -> allowed.set(handler.isBlockPlacerAllowed()));

        return !hasHandler || allowed.get();
    }

    /**
     * Maps the 9-cell layout to world blocks in the same orientation {@link MultiBlock#matches} reads them.
     */
    @Nonnull
    private static Block[] targetsAround(@Nonnull Block center, @Nonnull BlockFace width) {
        BlockFace far = width.getOppositeFace();
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

        return targets;
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
     * Whether {@code existing} satisfies a required structure cell. A cell requiring a specific custom
     * block is only satisfied by a block whose {@link BlockStorage} id matches - its {@link Material} alone
     * is not enough. Otherwise falls back to {@link MultiBlock}'s tolerant {@link Material} matching (wood
     * variants, ...) so hand-placed parts count.
     */
    @ParametersAreNonnullByDefault
    private static boolean satisfies(Block existing, Material required, @Nullable String requiredCustomId) {
        if (requiredCustomId != null) {
            return requiredCustomId.equals(BlockStorage.checkID(existing));
        }

        return satisfiesMaterial(existing.getType(), required);
    }

    private static boolean satisfiesMaterial(@Nonnull Material existing, @Nonnull Material required) {
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
    private static boolean fail(Player p, String messageKey) {
        Slimefun.getLocalization().sendMessage(p, messageKey, true);
        return false;
    }
}
