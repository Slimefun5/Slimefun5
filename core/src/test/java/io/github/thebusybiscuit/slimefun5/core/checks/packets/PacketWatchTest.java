package io.github.thebusybiscuit.slimefun5.core.checks.packets;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class PacketWatchTest {

    @Test
    void cleanTextReportsNothing() {
        PacketWatch watch = new PacketWatch(200);

        watch.observe("title", "Chest", "Chest");
        watch.observe("item", "ENERGY_REGULATOR", "Energy Regulator");

        Assertions.assertTrue(watch.drain().isEmpty());
    }

    @Test
    void rawIdTitleReportsOnce() {
        PacketWatch watch = new PacketWatch(200);

        watch.observe("title", "ENERGY_REGULATOR", "ENERGY_REGULATOR");
        watch.observe("title", "ENERGY_REGULATOR", "ENERGY_REGULATOR");

        List<String> lines = watch.drain();
        Assertions.assertEquals(1, lines.size());
        Assertions.assertEquals("[check:packet-watch] title ENERGY_REGULATOR: shows a raw id", lines.get(0));
    }

    @Test
    void colourCodesAreIgnoredWhenJudgingText() {
        PacketWatch watch = new PacketWatch(200);

        watch.observe("item", "SOUL_JAR", "\u00a7aSoul Jar");

        Assertions.assertTrue(watch.drain().isEmpty());
    }

    @Test
    void blankTextIsNotReported() {
        PacketWatch watch = new PacketWatch(200);

        watch.observe("item", "STAINED_GLASS_PANE", " ");

        Assertions.assertTrue(watch.drain().isEmpty());
    }

    @Test
    void identicalHitsReportOnce() throws InterruptedException {
        PacketWatch watch = new PacketWatch(200);
        ExecutorService threads = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);

        for (int thread = 0; thread < 8; thread++) {
            threads.execute(() -> {
                awaitQuietly(start);
                for (int hit = 0; hit < 1250; hit++) {
                    watch.observe("item", "SOUL_JAR", "Soul Jar (%mob%)");
                }
            });
        }
        start.countDown();
        threads.shutdown();
        Assertions.assertTrue(threads.awaitTermination(30, TimeUnit.SECONDS));

        Assertions.assertEquals(1, watch.drain().size());
    }

    @Test
    void queueStopsAtCap() {
        PacketWatch watch = new PacketWatch(200);

        for (int index = 0; index < 500; index++) {
            watch.observe("item", "ITEM_" + index, "RAW_ID_" + index);
        }

        Assertions.assertEquals(200, watch.drain().size());
    }

    @Test
    void drainEmptiesQueue() {
        PacketWatch watch = new PacketWatch(200);
        watch.observe("title", "A_B", "A_B");

        Assertions.assertEquals(1, watch.drain().size());
        Assertions.assertTrue(watch.drain().isEmpty());
    }

    @Test
    void hiddenLoreIsReportedOncePerItem() {
        PacketWatch watch = new PacketWatch(10);

        watch.reportHiddenLore("ANGEL_GEM", "Flight: Enabled");
        watch.reportHiddenLore("ANGEL_GEM", "Flight Speed: 2");

        java.util.List<String> lines = watch.drain();
        Assertions.assertEquals(1, lines.size());
        Assertions.assertTrue(lines.get(0).startsWith("[check:packet-watch] hidden-lore ANGEL_GEM: lore line \"Flight: Enabled\""), lines.get(0));
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
