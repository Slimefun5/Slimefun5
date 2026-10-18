package io.github.thebusybiscuit.slimefun5.core.checks;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

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

        new CheckRunner(Arrays.asList(high, medium, low)).runBlocking(Stage.MEDIUM, context(false), ticks -> { });

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
}
