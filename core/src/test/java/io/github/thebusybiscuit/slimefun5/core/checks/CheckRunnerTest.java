package io.github.thebusybiscuit.slimefun5.core.checks;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import io.github.thebusybiscuit.slimefun5.core.checks.sandbox.Sandbox;
import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;

class CheckRunnerTest {

    private ServerMock server;
    private Findings findings;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        findings = new Findings(line -> { });
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private CheckContext context(boolean live) {
        return new CheckContext(findings, Stage.MAX, live);
    }

    private static Check check(String name, Stage stage, CheckThread thread, boolean liveSafe, CheckStep... steps) {
        return new Check() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public Stage stage() {
                return stage;
            }

            @Override
            public CheckThread thread() {
                return thread;
            }

            @Override
            public boolean liveSafe() {
                return liveSafe;
            }

            @Override
            public Iterator<CheckStep> steps(CheckContext context) {
                return Arrays.asList(steps).iterator();
            }
        };
    }

    private static Check check(String name, Stage stage, CheckStep... steps) {
        return check(name, stage, CheckThread.ANY, true, steps);
    }

    @Test
    void runsOnlyChecksAtOrBelowStage() {
        List<String> ran = new ArrayList<>();
        Check low = check("low", Stage.LOW, () -> { ran.add("low"); return 0; });
        Check medium = check("medium", Stage.MEDIUM, () -> { ran.add("medium"); return 0; });
        Check high = check("high", Stage.HIGH, () -> { ran.add("high"); return 0; });

        // A MEDIUM check runs in a sandbox, which needs Slimefun's storage; only a live one opens on MockBukkit.
        MockBukkit.load(Slimefun.class);
        new CheckRunner(Arrays.asList(high, medium, low)).runBlocking(Stage.MEDIUM, context(true), ticks -> { });

        Assertions.assertEquals(Arrays.asList("low", "medium"), ran);
    }

    @Test
    void crashingStepBecomesOneFindingAndRunContinues() {
        AtomicInteger thirdStepRuns = new AtomicInteger();
        AtomicInteger otherCheckRuns = new AtomicInteger();
        Check crashing = check("a", Stage.LOW,
                () -> 0,
                () -> { throw new IllegalStateException("boom"); },
                () -> { thirdStepRuns.incrementAndGet(); return 0; });
        Check other = check("b", Stage.LOW, () -> { otherCheckRuns.incrementAndGet(); return 0; });

        new CheckRunner(Arrays.asList(crashing, other)).runBlocking(Stage.LOW, context(false), ticks -> { });

        Assertions.assertEquals(1, findings.count());
        Assertions.assertEquals("[check:a] a: the check itself crashed: java.lang.IllegalStateException: boom",
                findings.lines().get(0));
        Assertions.assertEquals(0, thirdStepRuns.get());
        Assertions.assertEquals(1, otherCheckRuns.get());
    }

    @Test
    void waitingStepAdvancesClockInBlockingMode() {
        List<Integer> advanced = new ArrayList<>();
        Check waiting = check("wait", Stage.LOW, () -> 20, () -> 0);

        new CheckRunner(Collections.singletonList(waiting)).runBlocking(Stage.LOW, context(false), advanced::add);

        Assertions.assertEquals(Collections.singletonList(20), advanced);
    }

    @Test
    void slicedModeYieldsWhenBudgetSpent() throws Exception {
        AtomicInteger stepsRun = new AtomicInteger();
        CheckStep[] steps = new CheckStep[10];
        for (int index = 0; index < steps.length; index++) {
            steps[index] = () -> {
                Thread.sleep(2);
                stepsRun.incrementAndGet();
                return 0;
            };
        }
        Check slow = check("slow", Stage.LOW, CheckThread.MAIN, true, steps);
        AtomicInteger doneCalls = new AtomicInteger();
        Plugin plugin = MockBukkit.createMockPlugin();

        RunningChecks running = new CheckRunner(Collections.singletonList(slow))
                .runSliced(Stage.LOW, context(false), plugin, 5_000_000L, doneCalls::incrementAndGet);

        server.getScheduler().performOneTick();
        Assertions.assertTrue(stepsRun.get() < 10);

        for (int tick = 0; tick < 50 && !running.isDone(); tick++) {
            server.getScheduler().performOneTick();
        }
        server.getScheduler().waitAsyncTasksFinished();
        Assertions.assertEquals(10, stepsRun.get());
        Assertions.assertEquals(1, doneCalls.get());
        Assertions.assertTrue(running.isDone());
    }

    @Test
    void liveUnsafeCheckIsSkippedOnLiveServer() {
        AtomicInteger runs = new AtomicInteger();
        Check unsafe = check("risky", Stage.LOW, CheckThread.ANY, false, () -> { runs.incrementAndGet(); return 0; });

        new CheckRunner(Collections.singletonList(unsafe)).runBlocking(Stage.LOW, context(true), ticks -> { });

        Assertions.assertEquals(0, runs.get());
        Assertions.assertEquals(0, findings.count());
        Assertions.assertTrue(findings.lines().contains("[check:risky] skipped: not safe on a live server"));
    }

    @Test
    void asyncStepsRunBeforeMainStepsAndDoneFiresOnce() {
        List<String> order = Collections.synchronizedList(new ArrayList<>());
        Check async = check("async", Stage.LOW, CheckThread.ANY, true,
                () -> { order.add("async1"); return 1; },
                () -> { order.add("async2"); return 0; });
        Check main = check("main", Stage.LOW, CheckThread.MAIN, true, () -> { order.add("main1"); return 0; });
        AtomicInteger doneCalls = new AtomicInteger();

        RunningChecks running = new CheckRunner(Arrays.asList(main, async))
                .runSliced(Stage.LOW, context(false), MockBukkit.createMockPlugin(), 5_000_000L,
                        doneCalls::incrementAndGet);

        tickUntilDone(running);

        Assertions.assertEquals(Arrays.asList("async1", "async2", "main1"), order);
        Assertions.assertEquals(1, doneCalls.get());
    }

    @Test
    void cancelBeforeMainPhaseRunsNoMainStepAndEndsTheRun() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger mainRuns = new AtomicInteger();
        Check async = check("async", Stage.LOW, CheckThread.ANY, true, () -> { release.await(); return 0; });
        Check main = check("main", Stage.LOW, CheckThread.MAIN, true, () -> { mainRuns.incrementAndGet(); return 0; });

        RunningChecks running = new CheckRunner(Arrays.asList(async, main))
                .runSliced(Stage.LOW, context(false), MockBukkit.createMockPlugin(), 5_000_000L, () -> { });
        server.getScheduler().performOneTick();
        running.cancel();
        release.countDown();
        server.getScheduler().waitAsyncTasksFinished();
        for (int tick = 0; tick < 5; tick++) {
            server.getScheduler().performOneTick();
        }

        Assertions.assertEquals(0, mainRuns.get());
        Assertions.assertTrue(running.isDone());
    }

    @Test
    void errorOnAsyncThreadStillFinishesTheRunOnce() {
        Check failing = check("failing", Stage.LOW, CheckThread.ANY, true, () -> { throw new StackOverflowError(); });
        AtomicInteger doneCalls = new AtomicInteger();

        RunningChecks running = new CheckRunner(Collections.singletonList(failing))
                .runSliced(Stage.LOW, context(false), MockBukkit.createMockPlugin(), 5_000_000L,
                        doneCalls::incrementAndGet);

        tickUntilDone(running);

        Assertions.assertTrue(running.isDone());
        Assertions.assertEquals(1, doneCalls.get());
    }

    @Test
    void checkWithoutThreadOverrideRunsOnTheMainThread() {
        Check plain = new Check() {
            @Override
            public String name() {
                return "plain";
            }

            @Override
            public Stage stage() {
                return Stage.LOW;
            }

            @Override
            public Iterator<CheckStep> steps(CheckContext context) {
                return Collections.<CheckStep>emptyList().iterator();
            }
        };

        Assertions.assertEquals(CheckThread.MAIN, plain.thread());
    }

    @Test
    void mediumCheckDeclaringAnyThreadStillRunsOnTheMainThread() {
        MockBukkit.load(Slimefun.class);
        AtomicBoolean ranOnMain = new AtomicBoolean();
        Check deep = check("deep", Stage.MEDIUM, CheckThread.ANY, true, () -> {
            ranOnMain.set(Bukkit.isPrimaryThread());
            return 0;
        });

        RunningChecks running = new CheckRunner(Collections.singletonList(deep))
                .runSliced(Stage.MEDIUM, context(true), MockBukkit.createMockPlugin(), 5_000_000L, () -> { });
        tickUntilDone(running);

        Assertions.assertTrue(ranOnMain.get());
        Assertions.assertFalse(Sandbox.isOpen());
    }

    @Test
    void sandboxRefusesToOpenOffTheMainThread() throws Exception {
        MockBukkit.load(Slimefun.class);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try {
                Sandbox.open(true).close();
            } catch (Throwable thrown) {
                failure.set(thrown);
            }
        });
        worker.start();
        worker.join();

        Assertions.assertInstanceOf(IllegalStateException.class, failure.get());
        Assertions.assertFalse(Sandbox.isOpen());
    }

    private void tickUntilDone(RunningChecks running) {
        for (int tick = 0; tick < 200 && !running.isDone(); tick++) {
            server.getScheduler().performOneTick();
            server.getScheduler().waitAsyncTasksFinished();
        }
        Assertions.assertTrue(running.isDone());
    }
}
