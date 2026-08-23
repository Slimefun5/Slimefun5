package io.github.thebusybiscuit.slimefun5.core.commands.subcommands;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;

import io.github.thebusybiscuit.slimefun5.core.commands.SlimefunCommand;
import io.github.thebusybiscuit.slimefun5.core.commands.SubCommand;
import io.github.thebusybiscuit.slimefun5.api.SlimefunAddon;
import io.github.thebusybiscuit.slimefun5.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun5.core.services.MetricsService;
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
        MetricsService metrics = Slimefun.getMetricsService();
        boolean consolidated = Slimefun.instance().getConfig().getBoolean("metrics.disable-addon-metrics");

        sender.sendMessage(ChatColor.YELLOW + "--- Slimefun Metrics ---");
        sender.sendMessage(line("Metrics service", enabled ? ChatColor.GREEN + "enabled" : ChatColor.RED + "disabled"));
        sender.sendMessage(line("bStats project", ChatColor.AQUA + "#" + BSTATS_PROJECT_ID + ChatColor.DARK_GRAY + " (this fork's own project)"));
        sender.sendMessage(line("Metrics module", moduleState(metrics)));

        if (!metrics.isRunning() && metrics.getFailureReason() != null) {
            sender.sendMessage(ChatColor.DARK_GRAY + "   why: " + ChatColor.RED + metrics.getFailureReason());
        }

        sender.sendMessage(line("Module auto-update", bool(metrics.hasAutoUpdates())));
        sender.sendMessage(line("Consolidate addon metrics", bool(consolidated)));

        sender.sendMessage(line("Server", ChatColor.GREEN + Bukkit.getVersion()));
        sender.sendMessage(line("Slimefun version", ChatColor.GREEN + Slimefun.getVersion()));

        sendAddons(sender, consolidated);

        sender.sendMessage(ChatColor.GRAY + "Integrated plugins:");
        IntegrationsManager integrations = Slimefun.getIntegrations();
        sender.sendMessage(integration("PlaceholderAPI", integrations.isPlaceholderAPIInstalled()));
        sender.sendMessage(integration("WorldEdit", integrations.isWorldEditInstalled()));
        sender.sendMessage(integration("mcMMO", integrations.isMcMMOInstalled()));
        sender.sendMessage(integration("ClearLag", integrations.isClearLagInstalled()));
        sender.sendMessage(integration("ItemsAdder", integrations.isItemsAdderInstalled()));
        sender.sendMessage(integration("Orebfuscator", integrations.isOrebfuscatorInstalled()));
    }

    /**
     * Lists every installed addon with the item count it contributes and where its metrics go.
     *
     * @implNote This is the point of the command on a fork whose selling point is its addon set, and it
     *           was missing entirely - only the third-party integrations were listed. Item counts come
     *           from the registry rather than the plugin, since an addon's items are what the metrics
     *           actually describe.
     */
    @ParametersAreNonnullByDefault
    private void sendAddons(CommandSender sender, boolean consolidated) {
        Map<String, Integer> itemsByAddon = new HashMap<>();

        for (SlimefunItem item : Slimefun.getRegistry().getEnabledSlimefunItems()) {
            SlimefunAddon addon = item.getAddon();

            if (addon != null) {
                itemsByAddon.merge(addon.getName(), 1, Integer::sum);
            }
        }

        List<Plugin> addons = new ArrayList<>(Slimefun.getInstalledAddons());
        addons.sort(Comparator.comparing(Plugin::getName, String.CASE_INSENSITIVE_ORDER));

        sender.sendMessage(line("Installed addons", ChatColor.GREEN + String.valueOf(addons.size())
            + ChatColor.DARK_GRAY + (consolidated ? " (reporting through Slimefun)" : " (reporting to their own projects)")));

        for (Plugin addon : addons) {
            int items = itemsByAddon.getOrDefault(addon.getName(), 0);

            sender.sendMessage(ChatColor.DARK_GRAY + " - " + ChatColor.GRAY + addon.getName()
                + ChatColor.DARK_GRAY + " " + addon.getDescription().getVersion()
                + ChatColor.DARK_GRAY + " (" + ChatColor.AQUA + items + ChatColor.DARK_GRAY + " items)");
        }
    }

    /** The module's state: its build when running, otherwise why it is not. */
    @Nonnull
    private String moduleState(@Nonnull MetricsService metrics) {
        if (metrics.isRunning()) {
            String version = metrics.getVersion();
            return ChatColor.GREEN + (version == null ? "running" : "running (#" + version + ")");
        }

        return ChatColor.RED + "not loaded";
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
