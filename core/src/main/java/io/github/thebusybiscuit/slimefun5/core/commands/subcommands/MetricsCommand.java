package io.github.thebusybiscuit.slimefun5.core.commands.subcommands;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import io.github.thebusybiscuit.slimefun5.core.commands.SlimefunCommand;
import io.github.thebusybiscuit.slimefun5.core.commands.SubCommand;
import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun5.integrations.IntegrationsManager;

/**
 * {@code /sf metrics} reports the state of this fork's bStats metrics: whether the metrics service is
 * enabled, which bStats project it reports to, the loaded metrics-module version, and the third-party
 * plugins Slimefun has integrated with. Admin-only ({@code slimefun.command.metrics}, default op) and
 * purely diagnostic - it changes nothing.
 */
class MetricsCommand extends SubCommand {

    /** This fork's own bStats project id (see options.metrics-service in config.yml). */
    private static final int BSTATS_PROJECT_ID = 31272;

    @ParametersAreNonnullByDefault
    MetricsCommand(Slimefun plugin, SlimefunCommand cmd) {
        super(plugin, cmd, "metrics", false);
    }

    @Override
    @ParametersAreNonnullByDefault
    public void onExecute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("slimefun.command.metrics")) {
            Slimefun.getLocalization().sendMessage(sender, "messages.no-permission", true);
            return;
        }

        boolean enabled = Slimefun.instance().getConfig().getBoolean("options.metrics-service");
        String moduleVersion = Slimefun.getMetricsService().getVersion();

        sender.sendMessage(ChatColor.YELLOW + "--- Slimefun Metrics ---");
        sender.sendMessage(line("Metrics service", enabled ? ChatColor.GREEN + "enabled" : ChatColor.RED + "disabled"));
        sender.sendMessage(line("bStats project", ChatColor.AQUA + "#" + BSTATS_PROJECT_ID + ChatColor.DARK_GRAY + " (this fork's own project)"));
        sender.sendMessage(line("Metrics module", moduleVersion == null ? ChatColor.RED + "not loaded" : ChatColor.GREEN + "#" + moduleVersion));
        sender.sendMessage(line("Module auto-update", bool(Slimefun.getMetricsService().hasAutoUpdates())));
        sender.sendMessage(line("Consolidate addon metrics", bool(Slimefun.instance().getConfig().getBoolean("metrics.disable-addon-metrics"))));

        // The anonymous figures this server contributes to the project.
        sender.sendMessage(line("Server", ChatColor.GREEN + Bukkit.getVersion()));
        sender.sendMessage(line("Slimefun version", ChatColor.GREEN + Slimefun.getVersion()));
        sender.sendMessage(line("Installed addons", ChatColor.GREEN + String.valueOf(Slimefun.getInstalledAddons().size())));

        sender.sendMessage(ChatColor.GRAY + "Integrated plugins:");
        IntegrationsManager integrations = Slimefun.getIntegrations();
        sender.sendMessage(integration("PlaceholderAPI", integrations.isPlaceholderAPIInstalled()));
        sender.sendMessage(integration("WorldEdit", integrations.isWorldEditInstalled()));
        sender.sendMessage(integration("mcMMO", integrations.isMcMMOInstalled()));
        sender.sendMessage(integration("ClearLag", integrations.isClearLagInstalled()));
        sender.sendMessage(integration("ItemsAdder", integrations.isItemsAdderInstalled()));
        sender.sendMessage(integration("Orebfuscator", integrations.isOrebfuscatorInstalled()));
    }

    @Nonnull
    private String line(@Nonnull String label, @Nonnull String value) {
        return ChatColor.GRAY + label + ": " + value;
    }

    @Nonnull
    private String bool(boolean value) {
        return (value ? ChatColor.GREEN + "yes" : ChatColor.RED + "no");
    }

    @Nonnull
    private String integration(@Nonnull String name, boolean detected) {
        return ChatColor.DARK_GRAY + " - " + ChatColor.GRAY + name + ": " + (detected ? ChatColor.GREEN + "detected" : ChatColor.DARK_GRAY + "absent");
    }
}
