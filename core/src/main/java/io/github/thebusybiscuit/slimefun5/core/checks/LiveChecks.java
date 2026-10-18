package io.github.thebusybiscuit.slimefun5.core.checks;

import java.io.File;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.logging.Level;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.IllegalPluginAccessException;

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

    private static RunningChecks running;
    private static CheckMessenger messenger = (recipient, key, placeholders) -> Slimefun.getLocalization().sendMessage(recipient, key, true, placeholders);

    private LiveChecks() {}

    public static void onStartup() {
        Stage stage = configuredStage();
        if (stage != Stage.OFF) {
            start(stage, Bukkit.getConsoleSender());
        }
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
    }

    public static void announceTo(@Nonnull Player player) {
        if (!player.hasPermission(PERMISSION)) {
            return;
        }
        int problems = reportOf(Slimefun.instance()).latestProblemCount();
        if (problems > 0) {
            messenger.send(player, "commands.check.join-notice", text -> text.replace("%count%", String.valueOf(problems)));
        }
    }

    @Nonnull
    public static CheckReport reportOf(@Nonnull Slimefun plugin) {
        return new CheckReport(new File(plugin.getDataFolder(), REPORT_DIRECTORY));
    }

    static void useMessenger(@Nonnull CheckMessenger replacement) {
        messenger = replacement;
    }

    private static Stage configuredStage() {
        String configured = Slimefun.getCfg().getString(CONFIG_KEY);
        if (configured == null) {
            return DEFAULT_STAGE;
        }
        Optional<Stage> stage = Stage.parse(configured);
        if (!stage.isPresent()) {
            Slimefun.logger().log(Level.WARNING, "Unknown value \"{0}\" for {1}, using {2}", new Object[] { configured, CONFIG_KEY, DEFAULT_STAGE.name().toLowerCase(Locale.ROOT) });
        }
        return stage.orElse(DEFAULT_STAGE);
    }

    private static void logIfDebugging(String line) {
        if (Debug.getTestCase() != null) {
            Slimefun.logger().info(line);
        }
    }

    private static void complete(Slimefun plugin, Stage stage, Findings findings, CommandSender requester) {
        int problems = findings.count();
        File report = writeReport(plugin, stage, findings);
        try {
            Bukkit.getScheduler().runTask(plugin, () -> announceFinished(plugin, stage, problems, report, requester));
        } catch (IllegalPluginAccessException disabled) {
            plugin.getLogger().log(Level.FINE, "Plugin disabled before the check summary could be shown", disabled);
        }
    }

    @Nullable
    private static File writeReport(Slimefun plugin, Stage stage, Findings findings) {
        try {
            return reportOf(plugin).write(stage, findings.lines(), Instant.now());
        } catch (RuntimeException failure) {
            plugin.getLogger().log(Level.WARNING, "Could not write the check report", failure);
            return null;
        }
    }

    private static void announceFinished(Slimefun plugin, Stage stage, int problems, @Nullable File report, CommandSender requester) {
        String location = report == null ? "(no report written)" : "plugins/" + plugin.getDataFolder().getName() + "/" + REPORT_DIRECTORY + "/" + report.getName();
        String stageName = stage.name().toLowerCase(Locale.ROOT);
        plugin.getLogger().info("Checks (" + stageName + "): " + problems + " problems, see " + location);

        if (!(requester instanceof ConsoleCommandSender)) {
            messenger.send(requester, "commands.check.finished", text -> text.replace("%stage%", stageName).replace("%count%", String.valueOf(problems)).replace("%report%", location));
        }
    }
}
