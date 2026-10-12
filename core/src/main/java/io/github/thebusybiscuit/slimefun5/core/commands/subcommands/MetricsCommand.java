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

        sender.sendMessage(ChatColor.YELLOW + msg("header"));
        sender.sendMessage(line(msg("metrics-service-label"), enabled ? ChatColor.GREEN + msg("enabled") : ChatColor.RED + msg("disabled")));
        sender.sendMessage(line(msg("bstats-project-label"), ChatColor.AQUA + "#" + BSTATS_PROJECT_ID + ChatColor.DARK_GRAY + " " + msg("bstats-project-note")));
        sender.sendMessage(line(msg("metrics-module-label"), moduleState(metrics)));

        if (!metrics.isRunning() && metrics.getFailureReason() != null) {
            sender.sendMessage(ChatColor.DARK_GRAY + "   " + msg("why-label") + ChatColor.RED + metrics.getFailureReason());
        }

        sender.sendMessage(line(msg("module-auto-update-label"), bool(metrics.hasAutoUpdates())));
        sender.sendMessage(line(msg("consolidate-addon-metrics-label"), bool(consolidated)));

        sender.sendMessage(line(msg("server-label"), ChatColor.GREEN + Bukkit.getVersion()));
        sender.sendMessage(line(msg("slimefun-version-label"), ChatColor.GREEN + Slimefun.getVersion()));

        sendAddons(sender, consolidated);
        sendReportedData(sender, metrics);

        sender.sendMessage(ChatColor.GRAY + msg("integrated-plugins-header"));
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

        sender.sendMessage(line(msg("installed-addons-label"), ChatColor.GREEN + String.valueOf(addons.size())
            + ChatColor.DARK_GRAY + " " + (consolidated ? msg("reporting-through-slimefun") : msg("reporting-to-own-projects"))));

        for (Plugin addon : addons) {
            sender.sendMessage(ChatColor.DARK_GRAY + " - " + ChatColor.GRAY + addon.getName()
                + ChatColor.DARK_GRAY + " " + addon.getDescription().getVersion()
                + ChatColor.DARK_GRAY + " (" + ChatColor.AQUA + itemsByAddon.getOrDefault(addon.getName(), 0) + ChatColor.DARK_GRAY + " " + msg("items-suffix") + ") "
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
            return ChatColor.RED + msg("not-reported") + ChatColor.DARK_GRAY + " " + msg("module-not-loaded-note");
        }

        if (reported == null) {
            return ChatColor.YELLOW + msg("unknown") + ChatColor.DARK_GRAY + " " + msg("no-addons-chart-note");
        }

        return reported.contains(addon) ? ChatColor.GREEN + msg("reported") : ChatColor.RED + msg("not-reported");
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
        sender.sendMessage(ChatColor.GRAY + msg("data-sent-header"));

        // bStats attaches these to every request itself; they are not ours, but they are still what leaves
        // this server, so an admin asking "what do you send?" should see them here too.
        sender.sendMessage(value(msg("players-online-label"), Bukkit.getOnlinePlayers().size() + " / " + Bukkit.getMaxPlayers()));
        sender.sendMessage(value(msg("online-mode-label"), String.valueOf(Bukkit.getOnlineMode())));
        sender.sendMessage(value(msg("server-data-label"), Bukkit.getName() + " " + Bukkit.getBukkitVersion()));
        sender.sendMessage(value(msg("java-label"), System.getProperty("java.version", "unknown")));
        sender.sendMessage(value(msg("os-label"), System.getProperty("os.name", "unknown") + " " + System.getProperty("os.arch", "")));
        sender.sendMessage(value(msg("cores-label"), String.valueOf(Runtime.getRuntime().availableProcessors())));

        List<MetricsService.ChartSample> samples = metrics.getChartSamples();

        if (samples.isEmpty()) {
            sender.sendMessage(ChatColor.DARK_GRAY + "   " + msg("no-charts-note"));
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
            return ChatColor.GREEN + (version == null ? msg("running") : msg("running-build").replace("%version%", version));
        }

        return ChatColor.RED + msg("not-loaded");
    }

    @Nonnull
    private String line(@Nonnull String label, @Nonnull String value) {
        return ChatColor.GRAY + label + ": " + value;
    }

    @Nonnull
    private String bool(boolean value) {
        return (value ? ChatColor.GREEN + msg("yes") : ChatColor.RED + msg("no"));
    }

    @Nonnull
    private String integration(@Nonnull String name, boolean detected) {
        return ChatColor.DARK_GRAY + " - " + ChatColor.GRAY + name + ": " + (detected ? ChatColor.GREEN + msg("detected") : ChatColor.DARK_GRAY + msg("absent"));
    }

    /** Shorthand for a {@code commands.metrics_report.*} lookup in the server's default language. */
    @Nonnull
    private String msg(@Nonnull String key) {
        return Slimefun.getLocalization().getMessage("commands.metrics_report." + key);
    }
}
