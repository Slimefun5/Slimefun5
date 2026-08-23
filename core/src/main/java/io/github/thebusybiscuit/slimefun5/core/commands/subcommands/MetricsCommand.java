package io.github.thebusybiscuit.slimefun5.core.commands.subcommands;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
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
        sendReportedData(sender, metrics);

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
     * Lists every installed addon and whether its metrics are actually reaching bStats.
     *
     * @implNote "Working" is not assumed from the addon being installed: it is read back out of the
     *           payload the module would send right now (the addons chart), so an addon missing from the
     *           report shows as not reported rather than being listed as fine. When metrics are
     *           consolidated every addon rides Slimefun's project, so the module running is the other
     *           half of the answer.
     */
    @ParametersAreNonnullByDefault
    private void sendAddons(CommandSender sender, boolean consolidated) {
        MetricsService metrics = Slimefun.getMetricsService();
        Map<String, Integer> itemsByAddon = new HashMap<>();

        for (SlimefunItem item : Slimefun.getRegistry().getEnabledSlimefunItems()) {
            SlimefunAddon addon = item.getAddon();

            if (addon != null) {
                itemsByAddon.merge(addon.getName(), 1, Integer::sum);
            }
        }

        String reported = reportedAddons(metrics);
        List<Plugin> addons = new ArrayList<>(Slimefun.getInstalledAddons());
        addons.sort(Comparator.comparing(Plugin::getName, String.CASE_INSENSITIVE_ORDER));

        sender.sendMessage(line("Installed addons", ChatColor.GREEN + String.valueOf(addons.size())
            + ChatColor.DARK_GRAY + (consolidated ? " (reporting through Slimefun)" : " (reporting to their own projects)")));

        for (Plugin addon : addons) {
            sender.sendMessage(ChatColor.DARK_GRAY + " - " + ChatColor.GRAY + addon.getName()
                + ChatColor.DARK_GRAY + " " + addon.getDescription().getVersion()
                + ChatColor.DARK_GRAY + " (" + ChatColor.AQUA + itemsByAddon.getOrDefault(addon.getName(), 0) + ChatColor.DARK_GRAY + " items) "
                + addonMetricsState(metrics, reported, addon.getName()));
        }
    }

    /**
     * Whether one addon shows up in the data the module would send.
     *
     * @param reported
     *            The addons chart's rendered sample, or {@code null} when the module is not running
     */
    @Nonnull
    @ParametersAreNonnullByDefault
    private String addonMetricsState(MetricsService metrics, @Nullable String reported, String addon) {
        if (!metrics.isRunning()) {
            return ChatColor.RED + "not reported" + ChatColor.DARK_GRAY + " (module not loaded)";
        }

        if (reported == null) {
            return ChatColor.YELLOW + "unknown" + ChatColor.DARK_GRAY + " (no addons chart)";
        }

        return reported.contains(addon) ? ChatColor.GREEN + "reported" : ChatColor.RED + "not reported";
    }

    /** The addons chart's current sample, which names every addon the payload will carry. */
    @Nullable
    private String reportedAddons(@Nonnull MetricsService metrics) {
        for (MetricsService.ChartSample sample : metrics.getChartSamples()) {
            if (sample.getName().toLowerCase(Locale.ROOT).contains("addon")) {
                return sample.getValue();
            }
        }

        return null;
    }

    /**
     * Prints what this server actually sends to bStats: the standard fields bStats attaches to every
     * request, then each of our charts with its current sample.
     */
    @ParametersAreNonnullByDefault
    private void sendReportedData(CommandSender sender, MetricsService metrics) {
        sender.sendMessage(ChatColor.GRAY + "Data sent to bStats:");

        // bStats attaches these to every request itself; they are not ours, but they are still what leaves
        // this server, so an admin asking "what do you send?" should see them here too.
        sender.sendMessage(value("players online", Bukkit.getOnlinePlayers().size() + " / " + Bukkit.getMaxPlayers()));
        sender.sendMessage(value("online mode", String.valueOf(Bukkit.getOnlineMode())));
        sender.sendMessage(value("server", Bukkit.getName() + " " + Bukkit.getBukkitVersion()));
        sender.sendMessage(value("java", System.getProperty("java.version", "unknown")));
        sender.sendMessage(value("os", System.getProperty("os.name", "unknown") + " " + System.getProperty("os.arch", "")));
        sender.sendMessage(value("cores", String.valueOf(Runtime.getRuntime().availableProcessors())));

        List<MetricsService.ChartSample> samples = metrics.getChartSamples();

        if (samples.isEmpty()) {
            sender.sendMessage(ChatColor.DARK_GRAY + "   (no charts - the module is not loaded)");
            return;
        }

        for (MetricsService.ChartSample sample : samples) {
            sender.sendMessage(value(sample.getName(), crop(sample.getValue())));
        }
    }

    /** Keeps a long data sample to one readable chat line. */
    @Nonnull
    private String crop(@Nonnull String text) {
        String flat = text.replaceAll("\\s+", " ").trim();
        return flat.length() <= 90 ? flat : flat.substring(0, 87) + "...";
    }

    @Nonnull
    private String value(@Nonnull String label, @Nonnull String data) {
        return ChatColor.DARK_GRAY + " - " + ChatColor.GRAY + label + ": " + ChatColor.AQUA + data;
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
