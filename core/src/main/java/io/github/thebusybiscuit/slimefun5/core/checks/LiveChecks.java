package io.github.thebusybiscuit.slimefun5.core.checks;

import java.io.File;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.UnaryOperator;
import java.util.logging.Level;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.scheduler.BukkitTask;

import io.github.thebusybiscuit.slimefun5.core.checks.packets.PacketWatch;
import io.github.thebusybiscuit.slimefun5.core.checks.sandbox.Sandbox;
import io.github.thebusybiscuit.slimefun5.core.checks.sandbox.VoidWorld;
import io.github.thebusybiscuit.slimefun5.core.debug.Debug;
import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;

/**
 * Runs the registered checks on a live server and reports what they found.
 *
 * @implNote {@code onDone} of a sliced run may fire on an async thread, so the report is written from there and
 *           everything that touches Bukkit is handed to the main thread. {@link #stop()} must cancel the run
 *           because a disabled plugin drops its scheduler tasks and the run would never finish.
 */
public final class LiveChecks {

    public static final String PERMISSION = "slimefun.command.check";
    public static final String CONFIG_KEY = "checks.stage";

    static final long BUDGET_NANOS = 5_000_000L;

    private static final Stage DEFAULT_STAGE = Stage.LOW;
    private static final String REPORT_DIRECTORY = "checks";
    private static final String NOTICE_LEDGER_FILE = "notified.yml";

    static final int WATCH_CAP = 200;
    static final long WATCH_DRAIN_TICKS = 1200L;
    static final String WATCH_LABEL = "watch";

    private static RunningChecks running;
    private static volatile PacketWatch watch;
    private static BukkitTask watchDrain;
    private static CheckMessenger messenger = (recipient, key, placeholders) -> Slimefun.getLocalization().sendMessage(recipient, key, true, placeholders);

    private LiveChecks() {}

    public static void onStartup() {
        deleteLeftoverWorld();
        Stage stage = configuredStage();
        if (stage != Stage.OFF) {
            start(stage, Bukkit.getConsoleSender());
        }
    }

    /**
     * @implNote The version harness has no level storage, so its server cannot name a world container; it has
     *           no leftover world either, so this is no failure, and reporting it as one would fail every harness run.
     */
    private static void deleteLeftoverWorld() {
        File worldContainer;
        try {
            worldContainer = Bukkit.getWorldContainer();
        } catch (RuntimeException noLevelStorage) {
            Slimefun.logger().log(Level.FINE, "This server has no world container to clean", noLevelStorage);
            return;
        }
        VoidWorld.deleteLeftover(worldContainer);
    }

    /**
     * @return false when a run is already in progress
     */
    public static synchronized boolean start(@Nonnull Stage stage, @Nonnull CommandSender requester) {
        if (running != null && !running.isDone()) {
            messenger.send(requester, "commands.check.already-running", text -> text);
            return false;
        }

        Slimefun plugin = Slimefun.instance();
        startWatch(plugin);
        Findings findings = new Findings(LiveChecks::logIfDebugging);
        CheckContext context = new CheckContext(findings, stage, true);
        CheckRunner runner = new CheckRunner(Slimefun.getChecks().all());
        running = runner.runSliced(stage, context, plugin, BUDGET_NANOS, () -> complete(plugin, stage, findings, requester));
        return true;
    }

    public static synchronized void stop() {
        if (running != null) {
            running.cancel();
            running = null;
        }
        stopWatch();
        Sandbox.closeOpen();
    }

    /**
     * @return the watch real players' packets are reported to, or {@code null} while it is off; one volatile read
     */
    @Nullable
    public static PacketWatch activeWatch() {
        return watch;
    }

    /**
     * Tells an op about the latest findings, once per change in them.
     */
    public static void announceTo(@Nonnull Player player) {
        if (!player.hasPermission(PERMISSION)) {
            return;
        }
        Slimefun plugin = Slimefun.instance();
        List<String> problems = latestProblemLines(plugin);
        if (noticesOf(plugin).recordIfChanged(player.getUniqueId(), problems)) {
            messenger.send(player, "commands.check.join-notice", text -> text.replace("%count%", String.valueOf(problems.size())));
        }
    }

    /**
     * The latest run report's lines, then the watch findings written after it: what {@code /sf check} prints and
     * {@link #latestProblemCount(Slimefun)} counts.
     */
    @Nonnull
    public static List<String> latestLines(@Nonnull Slimefun plugin) {
        CheckReport runReports = reportOf(plugin);
        List<String> lines = new ArrayList<>(runReports.latestLines());
        lines.addAll(watchReportOf(plugin).problemLinesAfter(runReports.latest()));
        return lines;
    }

    public static int latestProblemCount(@Nonnull Slimefun plugin) {
        return latestProblemLines(plugin).size();
    }

    @Nonnull
    private static List<String> latestProblemLines(@Nonnull Slimefun plugin) {
        CheckReport runReports = reportOf(plugin);
        List<String> problems = new ArrayList<>(runReports.latestProblemLines());
        problems.addAll(watchReportOf(plugin).problemLinesAfter(runReports.latest()));
        return problems;
    }

    @Nonnull
    private static NoticeLedger noticesOf(@Nonnull Slimefun plugin) {
        return new NoticeLedger(new File(new File(plugin.getDataFolder(), REPORT_DIRECTORY), NOTICE_LEDGER_FILE));
    }

    @Nonnull
    public static CheckReport reportOf(@Nonnull Slimefun plugin) {
        return new CheckReport(new File(plugin.getDataFolder(), REPORT_DIRECTORY));
    }

    @Nonnull
    public static CheckReport watchReportOf(@Nonnull Slimefun plugin) {
        return new CheckReport(new File(plugin.getDataFolder(), REPORT_DIRECTORY), CheckReport.WATCH_PREFIX);
    }

    public static void send(@Nonnull CommandSender recipient, @Nonnull String key, @Nonnull UnaryOperator<String> placeholders) {
        messenger.send(recipient, key, placeholders);
    }

    @Nonnull
    public static String locationOf(@Nonnull Slimefun plugin, @Nonnull File report) {
        return "plugins/" + plugin.getDataFolder().getName() + "/" + REPORT_DIRECTORY + "/" + report.getName();
    }

    public static void useMessenger(@Nonnull CheckMessenger replacement) {
        messenger = replacement;
    }

    private static synchronized void startWatch(Slimefun plugin) {
        if (watch != null) {
            return;
        }
        watch = new PacketWatch(WATCH_CAP);
        watchDrain = Bukkit.getScheduler().runTaskTimer(plugin, () -> drainWatch(plugin), WATCH_DRAIN_TICKS, WATCH_DRAIN_TICKS);
    }

    private static synchronized void stopWatch() {
        if (watchDrain != null) {
            watchDrain.cancel();
            watchDrain = null;
        }
        PacketWatch stopped = watch;
        watch = null;
        if (stopped != null) {
            writeWatchReport(Slimefun.instance(), stopped);
        }
    }

    private static List<String> drainedWatch() {
        PacketWatch current = watch;
        return current == null ? Collections.<String>emptyList() : current.drain();
    }

    private static void drainWatch(Slimefun plugin) {
        PacketWatch current = watch;
        if (current != null) {
            writeWatchReport(plugin, current);
        }
    }

    private static void writeWatchReport(Slimefun plugin, PacketWatch source) {
        List<String> lines = source.drain();
        if (lines.isEmpty()) {
            return;
        }
        try {
            watchReportOf(plugin).write(WATCH_LABEL, lines, Instant.now());
        } catch (RuntimeException failure) {
            plugin.getLogger().log(Level.WARNING, "Could not write the packet watch report", failure);
        }
    }

    private static Stage configuredStage() {
        String configured = Slimefun.getCfg().getString(CONFIG_KEY);
        if (configured == null) {
            return DEFAULT_STAGE;
        }
        if (isUnquotedOff(configured)) {
            return Stage.OFF;
        }
        Optional<Stage> stage = Stage.parse(configured);
        if (!stage.isPresent()) {
            Slimefun.logger().log(Level.WARNING, "Unknown value \"{0}\" for {1}, using {2}", new Object[] { configured, CONFIG_KEY, DEFAULT_STAGE.name().toLowerCase(Locale.ROOT) });
        }
        return stage.orElse(DEFAULT_STAGE);
    }

    /** YAML 1.1 reads an unquoted {@code off}, the value the config documents, as the boolean {@code false}. */
    private static boolean isUnquotedOff(String configured) {
        return Boolean.FALSE.toString().equals(configured);
    }

    private static void logIfDebugging(String line) {
        if (Debug.getTestCase() != null) {
            Slimefun.logger().info(line);
        }
    }

    private static void complete(Slimefun plugin, Stage stage, Findings findings, CommandSender requester) {
        List<String> lines = findings.lines();
        List<String> watched = drainedWatch();
        lines.addAll(watched);
        int problems = findings.count() + watched.size();
        File report = writeReport(plugin, stage, lines);
        try {
            Bukkit.getScheduler().runTask(plugin, () -> announceFinished(plugin, stage, problems, report, requester));
        } catch (IllegalPluginAccessException disabled) {
            plugin.getLogger().log(Level.FINE, "Plugin disabled before the check summary could be shown", disabled);
        }
    }

    @Nullable
    private static File writeReport(Slimefun plugin, Stage stage, List<String> lines) {
        try {
            return reportOf(plugin).write(stage, lines, Instant.now());
        } catch (RuntimeException failure) {
            plugin.getLogger().log(Level.WARNING, "Could not write the check report", failure);
            return null;
        }
    }

    private static void announceFinished(Slimefun plugin, Stage stage, int problems, @Nullable File report, CommandSender requester) {
        String stageName = stage.name().toLowerCase(Locale.ROOT);
        String location = report == null ? "none" : locationOf(plugin, report);
        plugin.getLogger().info("Checks (" + stageName + "): " + problems + " problems, see " + location);

        if (requester instanceof ConsoleCommandSender) {
            return;
        }
        if (report == null) {
            messenger.send(requester, "commands.check.finished-no-report", text -> text.replace("%stage%", stageName).replace("%count%", String.valueOf(problems)));
        } else {
            messenger.send(requester, "commands.check.finished", text -> text.replace("%stage%", stageName).replace("%count%", String.valueOf(problems)).replace("%report%", location));
        }
    }
}
