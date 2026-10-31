package io.github.thebusybiscuit.slimefun5.core.commands.subcommands;

import javax.annotation.ParametersAreNonnullByDefault;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import io.github.thebusybiscuit.slimefun5.core.commands.SlimefunCommand;
import io.github.thebusybiscuit.slimefun5.core.commands.SubCommand;
import io.github.thebusybiscuit.slimefun5.core.services.ItemCanonicalizer;
import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;

/**
 * Temporary admin command to normalize legacy/bugged plain Slimefun items in a player's inventory
 * so they stack properly with newly produced items.
 */
class FixCommand extends SubCommand {

    @ParametersAreNonnullByDefault
    FixCommand(Slimefun plugin, SlimefunCommand cmd) {
        super(plugin, cmd, "fix", true);
    }

    @Override
    public void onExecute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command.");
            return;
        }

        Player player = (Player) sender;
        if (!player.isOp()) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            return;
        }

        int fixed = 0;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = contents[i];
            if (stack != null && ItemCanonicalizer.canonicalize(stack)) {
                contents[i] = stack;
                fixed++;
            }
        }

        if (fixed > 0) {
            player.getInventory().setContents(contents);
            player.updateInventory();
        }

        sender.sendMessage(ChatColor.GREEN + "[Slimefun] " + fixed + " item(s) fixed.");
    }
}

