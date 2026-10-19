package io.github.thebusybiscuit.slimefun5.core.checks;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CheckReportTest {

    @TempDir
    File directory;

    @Test
    void writesOneLinePerFindingWithStageHeader() throws Exception {
        CheckReport report = new CheckReport(directory);
        List<String> findings = Arrays.asList("[check:a] x: broke", "[check:b] y: broke too");

        File file = report.write(Stage.MEDIUM, findings, Instant.parse("2026-10-18T10:00:00Z"));

        List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
        Assertions.assertEquals(3, lines.size());
        Assertions.assertTrue(lines.get(0).contains("medium"));
        Assertions.assertEquals(findings, lines.subList(1, 3));
    }

    @Test
    void keepsNewestTen() {
        CheckReport report = new CheckReport(directory);
        List<File> written = new ArrayList<>();
        for (int index = 0; index < 12; index++) {
            written.add(report.write(Stage.LOW, Collections.emptyList(), Instant.parse("2026-10-18T10:00:00Z").plusSeconds(index)));
        }

        Assertions.assertEquals(10, directory.listFiles().length);
        Assertions.assertFalse(written.get(0).exists());
        Assertions.assertFalse(written.get(1).exists());
        Assertions.assertTrue(written.get(2).exists());
        Assertions.assertEquals(written.get(11), report.latest().get());
    }

    @Test
    void latestProblemCountReadsNewestReport() {
        CheckReport report = new CheckReport(directory);
        report.write(Stage.LOW, Arrays.asList("[check:a] x: broke", "[check:a] y: broke", "[check:a] z: broke"), Instant.parse("2026-10-18T10:00:00Z"));
        Assertions.assertEquals(3, report.latestProblemCount());

        report.write(Stage.LOW, Arrays.asList("[check:a] x: broke", "[check:b] skipped: not safe on a live server"), Instant.parse("2026-10-18T10:00:05Z"));
        Assertions.assertEquals(1, report.latestProblemCount());
    }

    @Test
    void reportsNothingWhenNoReportExists() {
        CheckReport report = new CheckReport(new File(directory, "missing"));

        Assertions.assertFalse(report.latest().isPresent());
        Assertions.assertEquals(0, report.latestProblemCount());
        Assertions.assertTrue(report.latestLines().isEmpty());
    }

    @Test
    void reportsOfAnotherPrefixAreNeitherListedNorPruned() {
        CheckReport runs = new CheckReport(directory);
        CheckReport watches = new CheckReport(directory, CheckReport.WATCH_PREFIX);
        File run = runs.write(Stage.LOW, Arrays.asList("[check:a] x: broke"), Instant.parse("2026-10-18T10:00:00Z"));
        for (int index = 0; index < 12; index++) {
            watches.write("watch", Arrays.asList("[check:packet-watch] item A: shows a raw id"), Instant.parse("2026-10-18T10:01:00Z").plusSeconds(index));
        }

        Assertions.assertTrue(run.exists());
        Assertions.assertEquals(run, runs.latest().get());
        Assertions.assertEquals(10, directory.listFiles((parent, name) -> name.startsWith("watch-")).length);
    }

    @Test
    void problemLinesAfterHoldOnlyNewerReports() {
        CheckReport runs = new CheckReport(directory);
        CheckReport watches = new CheckReport(directory, CheckReport.WATCH_PREFIX);
        watches.write("watch", Arrays.asList("[check:packet-watch] item OLD: shows a raw id"), Instant.parse("2026-10-18T09:00:00Z"));
        runs.write(Stage.LOW, Collections.emptyList(), Instant.parse("2026-10-18T10:00:00Z"));
        watches.write("watch", Arrays.asList("[check:packet-watch] item A: shows a raw id", "[check:packet-watch] item B: shows a raw id"), Instant.parse("2026-10-18T10:05:00Z"));

        Assertions.assertEquals(2, watches.problemLinesAfter(runs.latest()).size());
        Assertions.assertEquals(3, watches.problemLinesAfter(Optional.empty()).size());
    }

    @Test
    void aSkipFindingsRecordsIsNeverCountedAsAProblem() {
        Findings findings = new Findings(line -> { });
        findings.report("a", "x", "broke");
        findings.recordSkipped("b", "not safe on a live server");
        CheckReport report = new CheckReport(directory);
        report.write(Stage.LOW, findings.lines(), Instant.parse("2026-10-18T10:00:00Z"));

        Assertions.assertTrue(Findings.isSkipped(findings.lines().get(1)));
        Assertions.assertEquals(findings.count(), report.latestProblemCount());
    }
}
