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

/**
 * {@code /sf owner} reports who owns the multiblock machine (any core or addon {@link MultiBlockMachine} -
 * Enhanced Crafting Table, Ore Crusher, ...) the player is looking at. Ownership is what gates the redstone
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

            if (!(item instanceof MultiBlockMachine)) {
                continue;
            }

            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        Block center = target.getRelative(dx, dy, dz);

                        if (mb.matches(center)) {
                            report(p, item, MultiBlockOwnership.ownershipKey(center));
                            return;
                        }
                    }
                }
            }
        }

        Slimefun.getLocalization().sendMessage(p, "messages.owner.not-looking", true);
    }

    @ParametersAreNonnullByDefault
    private void report(Player p, SlimefunItem machine, Location key) {
        String machineName = Slimefun.getItemTranslationService().getName(p, machine);
        UUID owner = Slimefun.getMultiBlockOwnership().getOwner(key);

        if (owner == null) {
            Slimefun.getLocalization().sendMessage(p, "messages.owner.unowned", true, msg -> msg.replace("%machine%", machineName));
            return;
        }

        OfflinePlayer ownerPlayer = Bukkit.getOfflinePlayer(owner);
        String ownerName = ownerPlayer.getName() != null ? ownerPlayer.getName() : owner.toString();
        Slimefun.getLocalization().sendMessage(p, "messages.owner.owned", true,
            msg -> msg.replace("%machine%", machineName).replace("%owner%", ownerName));
    }
}
