package io.github.thebusybiscuit.slimefun5.core.checks;

import java.io.File;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import io.github.thebusybiscuit.slimefun5.core.debug.Debug;
import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;

class LiveChecksTest {

    private static final int MAXIMUM_TICKS = 400;

    private ServerMock server;
    private Slimefun plugin;
    private List<String> sentKeys;
    private List<String> sentMessages;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(Slimefun.class);
        sentKeys = new ArrayList<>();
        sentMessages = new ArrayList<>();
        clearReports();
        LiveChecks.useMessenger((recipient, key, placeholders) -> {
            sentKeys.add(key);
            sentMessages.add(placeholders.apply(key + ":%count%"));
        });
    }

    @AfterEach
    void tearDown() {
        Debug.setTestCase(null);
        LiveChecks.stop();
        MockBukkit.unmock();
    }

    private static Check check(String name, CheckThread thread, CheckStep... steps) {
        return new Check() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public Stage stage() {
                return Stage.LOW;
            }

            @Override
            public CheckThread thread() {
                return thread;
            }

            @Override
            public Iterator<CheckStep> steps(CheckContext context) {
                return Arrays.asList(steps).iterator();
            }
        };
    }

    private void tickUntil(BooleanSupplier condition) throws InterruptedException {
        for (int tick = 0; tick < MAXIMUM_TICKS && !condition.getAsBoolean(); tick++) {
            server.getScheduler().performOneTick();
            Thread.sleep(5);
        }
        Assertions.assertTrue(condition.getAsBoolean(), "condition not reached");
    }

    private CheckReport report() {
        return LiveChecks.reportOf(plugin);
    }

    private void clearReports() {
        File[] files = new File(plugin.getDataFolder(), "checks").listFiles();
        if (files != null) {
            Arrays.stream(files).forEach(File::delete);
        }
    }

    @Test
    void offStageRunsNothing() throws Exception {
        clearReports();
        AtomicBoolean ran = new AtomicBoolean();
        Slimefun.getChecks().register(check("probe", CheckThread.MAIN, () -> {
            ran.set(true);
            return 0;
        }));
        Slimefun.getCfg().setValue("checks.stage", "off");

        LiveChecks.onStartup();
        for (int tick = 0; tick < 20; tick++) {
            server.getScheduler().performOneTick();
            Thread.sleep(5);
        }

        Assertions.assertFalse(ran.get());
        Assertions.assertFalse(report().latest().isPresent());
    }

    @Test
    void lowRunsAtStartupWithoutBlocking() throws Exception {
        clearReports();
        AtomicBoolean ran = new AtomicBoolean();
        Slimefun.getChecks().register(check("probe", CheckThread.MAIN, () -> {
            ran.set(true);
            return 0;
        }));
        Slimefun.getCfg().setValue("checks.stage", "low");

        LiveChecks.onStartup();

        Assertions.assertFalse(ran.get());
        tickUntil(() -> report().latest().isPresent());
        Assertions.assertTrue(ran.get());
    }

    @Test
    void unknownConfiguredStageFallsBackToLow() throws Exception {
        clearReports();
        AtomicBoolean ran = new AtomicBoolean();
        Slimefun.getChecks().register(check("probe", CheckThread.MAIN, () -> {
            ran.set(true);
            return 0;
        }));
        Slimefun.getCfg().setValue("checks.stage", "loud");

        LiveChecks.onStartup();

        tickUntil(() -> report().latest().isPresent());
        Assertions.assertTrue(ran.get());
    }

    @Test
    void secondRunIsRefusedWhileOneIsRunning() {
        Slimefun.getChecks().register(check("slow", CheckThread.MAIN, () -> 100, () -> 0));
        CommandSender requester = server.addPlayer();

        Assertions.assertTrue(LiveChecks.start(Stage.LOW, requester));
        server.getScheduler().performOneTick();
        Assertions.assertFalse(LiveChecks.start(Stage.LOW, requester));

        Assertions.assertEquals(Collections.singletonList("commands.check.already-running"), sentKeys);
    }

    @Test
    void stopCancelsARunAndWritesNoReport() {
        clearReports();
        Slimefun.getChecks().register(check("slow", CheckThread.MAIN, () -> 100, () -> 0));
        LiveChecks.start(Stage.LOW, server.addPlayer());
        server.getScheduler().performOneTick();

        LiveChecks.stop();
        server.getScheduler().performTicks(200);

        Assertions.assertFalse(report().latest().isPresent());
        Assertions.assertTrue(LiveChecks.start(Stage.LOW, server.addPlayer()));
    }

    @Test
    void asyncFindingsEndUpInTheReport() throws Exception {
        clearReports();
        Slimefun.getChecks().register(check("async", CheckThread.ANY, () -> {
            throw new IllegalStateException("boom");
        }));

        LiveChecks.start(Stage.LOW, server.addPlayer());

        tickUntil(() -> report().latest().isPresent());
        Assertions.assertEquals(1, report().latestProblemCount());
        tickUntil(() -> sentKeys.contains("commands.check.finished"));
    }

    @Test
    void debugLogsEveryFinding() throws Exception {
        clearReports();
        List<String> logged = Collections.synchronizedList(new ArrayList<>());
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                logged.add(record.getMessage());
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
        };
        plugin.getLogger().addHandler(handler);
        Debug.setTestCase("slimefun_analytics");
        Slimefun.getChecks().register(check("noisy", CheckThread.MAIN, () -> {
            throw new IllegalStateException("boom");
        }));

        LiveChecks.start(Stage.LOW, server.addPlayer());
        tickUntil(() -> report().latest().isPresent());
        tickUntil(() -> logged.stream().anyMatch(line -> line.startsWith("Checks (low): 1 problems, see ")));

        Assertions.assertTrue(logged.stream().anyMatch(line -> line.startsWith("[check:noisy]")));
        plugin.getLogger().removeHandler(handler);
    }

    @Test
    void opGetsJoinNoticeWhenLatestReportHasProblems() {
        report().write(Stage.LOW, Arrays.asList("[check:a] x: broke", "[check:a] y: broke"), Instant.now());
        Player operator = server.addPlayer();
        operator.setOp(true);

        LiveChecks.announceTo(operator);

        Assertions.assertEquals(Collections.singletonList("commands.check.join-notice"), sentKeys);
        Assertions.assertEquals(Collections.singletonList("commands.check.join-notice:2"), sentMessages);
    }

    @Test
    void opGetsNoNoticeWhenLatestReportIsClean() {
        report().write(Stage.LOW, Collections.emptyList(), Instant.now());
        Player operator = server.addPlayer();
        operator.setOp(true);

        LiveChecks.announceTo(operator);

        Assertions.assertTrue(sentKeys.isEmpty());
    }

    @Test
    void nonOpGetsNoNotice() {
        report().write(Stage.LOW, Arrays.asList("[check:a] x: broke"), Instant.now());
        Player player = server.addPlayer();

        LiveChecks.announceTo(player);

        Assertions.assertTrue(sentKeys.isEmpty());
    }

    private File[] filesStartingWith(String prefix) {
        File[] files = new File(plugin.getDataFolder(), "checks").listFiles((directory, name) -> name.startsWith(prefix));
        return files == null ? new File[0] : files;
    }

    @Test
    void runEndMergesWatchedFindingsIntoTheRunReport() throws Exception {
        clearReports();
        Slimefun.getChecks().register(check("broken", CheckThread.MAIN, () -> {
            throw new IllegalStateException("boom");
        }));

        LiveChecks.start(Stage.LOW, server.addPlayer());
        LiveChecks.activeWatch().observe("item", "SOUL_JAR", "SOUL_JAR");
        tickUntil(() -> report().latest().isPresent());

        Assertions.assertEquals(2, report().latestProblemCount());
        Assertions.assertTrue(report().latestLines().stream().anyMatch(line -> line.startsWith("[check:packet-watch] item SOUL_JAR")));
        Assertions.assertEquals(0, filesStartingWith("watch-").length);
    }

    @Test
    void stopWritesWatchedFindingsToTheirOwnReport() {
        clearReports();
        report().write(Stage.LOW, Arrays.asList("[check:a] x: broke"), Instant.parse("2026-10-18T10:00:00Z"));
        LiveChecks.start(Stage.LOW, server.addPlayer());
        LiveChecks.activeWatch().observe("title", "ENERGY_REGULATOR", "ENERGY_REGULATOR");

        LiveChecks.stop();

        Assertions.assertEquals(1, filesStartingWith("watch-").length);
        Assertions.assertEquals(1, report().latestProblemCount());
        Assertions.assertEquals(1, LiveChecks.watchReportOf(plugin).latestProblemCount());
    }

    @Test
    void joinNoticeAddsWatchFindingsNewerThanTheRunReport() {
        report().write(Stage.LOW, Arrays.asList("[check:a] x: broke"), Instant.parse("2026-10-18T10:00:00Z"));
        LiveChecks.watchReportOf(plugin).write("watch", Arrays.asList("[check:packet-watch] item A: shows a raw id", "[check:packet-watch] item B: shows a raw id"), Instant.parse("2026-10-18T10:05:00Z"));
        Player operator = server.addPlayer();
        operator.setOp(true);

        LiveChecks.announceTo(operator);

        Assertions.assertEquals(Collections.singletonList("commands.check.join-notice:3"), sentMessages);
    }
}
