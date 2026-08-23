package io.github.thebusybiscuit.slimefun5.core.commands.subcommands;

import java.util.Set;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import io.github.thebusybiscuit.slimefun5.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun5.core.commands.SlimefunCommand;
import io.github.thebusybiscuit.slimefun5.core.commands.SubCommand;
import io.github.thebusybiscuit.slimefun5.core.multiblocks.MultiBlock;
import io.github.thebusybiscuit.slimefun5.core.multiblocks.MultiBlockMachine;
import io.github.thebusybiscuit.slimefun5.core.multiblocks.MultiBlockOwnership;
import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;

import me.mrCookieSlime.Slimefun.api.BlockStorage;

/**
 * {@code /sf owner} reports who owns the multiblock (any registered {@link MultiBlock} - Enhanced Crafting
 * Table, Ore Crusher, Tinkers Smeltery, ...) or plain Slimefun block the player is looking at. Ownership is what gates the redstone
 * auto-craft, so this is the way to check why a machine will or will not auto-craft. Admin-only
 * ({@code slimefun.command.owner}, default op) and purely diagnostic - it changes nothing.
 */
class OwnerCommand extends SubCommand {

    // How far the player's line of sight is traced to find the multiblock they mean.
    private static final int REACH = 12;

    @ParametersAreNonnullByDefault
    OwnerCommand(Slimefun plugin, SlimefunCommand cmd) {
        super(plugin, cmd, "owner", false);
    }

    @Override
    @ParametersAreNonnullByDefault
    public void onExecute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("slimefun.command.owner")) {
            Slimefun.getLocalization().sendMessage(sender, "messages.no-permission", true);
            return;
        }

        if (!(sender instanceof Player)) {
            Slimefun.getLocalization().sendMessage(sender, "messages.only-players", true);
            return;
        }

        Player p = (Player) sender;
        Block target = p.getTargetBlock((Set<Material>) null, REACH);

        if (target == null || target.getType() == Material.AIR) {
            Slimefun.getLocalization().sendMessage(p, "messages.owner.not-looking", true);
            return;
        }

        // The targeted block can be any cell of the structure, so test every center within one block.
        for (MultiBlock mb : Slimefun.getRegistry().getMultiBlocks()) {
            SlimefunItem item = mb.getSlimefunItem();

            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        Block center = target.getRelative(dx, dy, dz);

                        if (!mb.matches(center)) {
                            continue;
                        }

                        // A MultiBlockMachine's owner lives in multiblock-owners.yml, but a structure whose
                        // centre is itself a placed Slimefun block records its owner in BlockStorage, so read
                        // whichever store actually holds it.
                        if (item instanceof MultiBlockMachine) {
                            report(p, item, MultiBlockOwnership.ownershipKey(center));
                            return;
                        }

                        if (reportPlacedBlock(p, center)) {
                            return;
                        }
                    }
                }
            }
        }

        // Not part of a registered MultiBlock: fall back to the Slimefun block itself, which covers
        // ordinary placed machines.
        if (reportPlacedBlock(p, target)) {
            return;
        }

        Slimefun.getLocalization().sendMessage(p, "messages.owner.not-looking", true);
    }

    /** Reports the recorded placer of the Slimefun block at {@code target}, if it is one. */
    @ParametersAreNonnullByDefault
    private boolean reportPlacedBlock(Player p, Block target) {
        SlimefunItem item = BlockStorage.check(target);

        if (item == null) {
            return false;
        }

        String recorded = BlockStorage.getLocationInfo(target.getLocation(), "owner");
        String machineName = Slimefun.getItemTranslationService().getName(p, item);

        if (recorded == null) {
            Slimefun.getLocalization().sendMessage(p, "messages.owner.unowned", true, msg -> msg.replace("%machine%", machineName));
            return true;
        }

        reportOwner(p, machineName, UUID.fromString(recorded));
        return true;
    }

    @ParametersAreNonnullByDefault
    private void report(Player p, SlimefunItem machine, Location key) {
        String machineName = Slimefun.getItemTranslationService().getName(p, machine);
        UUID owner = Slimefun.getMultiBlockOwnership().getOwner(key);

        if (owner == null) {
            Slimefun.getLocalization().sendMessage(p, "messages.owner.unowned", true, msg -> msg.replace("%machine%", machineName));
            return;
        }

        reportOwner(p, machineName, owner);
    }

    @ParametersAreNonnullByDefault
    private void reportOwner(Player p, String machineName, UUID owner) {
        OfflinePlayer ownerPlayer = Bukkit.getOfflinePlayer(owner);
        String ownerName = ownerPlayer.getName() != null ? ownerPlayer.getName() : owner.toString();
        Slimefun.getLocalization().sendMessage(p, "messages.owner.owned", true,
            msg -> msg.replace("%machine%", machineName).replace("%owner%", ownerName));
    }
}
