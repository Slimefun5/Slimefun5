package io.github.thebusybiscuit.slimefun5.core.checks;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import javax.annotation.Nonnull;

/**
 * @implNote Writes may come from an async check thread, so they are synchronized. File names sort
 *           chronologically, which is how the newest report and the ones to prune are found.
 */
public final class CheckReport {

    static final int REPORTS_KEPT = 10;

    private static final String FILE_PREFIX = "report-";
    private static final String FILE_SUFFIX = ".txt";
    private static final String HEADER_PREFIX = "# ";
    private static final String FINDING_PREFIX = "[check:";
    private static final String SKIPPED_MARKER = "] skipped: ";
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneId.systemDefault());

    private final File directory;

    public CheckReport(@Nonnull File directory) {
        this.directory = directory;
    }

    @Nonnull
    public synchronized File write(@Nonnull Stage stage, @Nonnull List<String> lines, @Nonnull Instant at) {
        File file = new File(directory, FILE_PREFIX + FILE_TIME.format(at) + FILE_SUFFIX);
        List<String> content = new ArrayList<>();
        content.add(HEADER_PREFIX + "Slimefun checks (" + stage.name().toLowerCase(Locale.ROOT) + ") at " + at);
        content.addAll(lines);

        try {
            Files.createDirectories(directory.toPath());
            Files.write(file.toPath(), content, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }

        pruneOldReports();
        return file;
    }

    @Nonnull
    public synchronized Optional<File> latest() {
        List<File> reports = reportsOldestFirst();
        return reports.isEmpty() ? Optional.empty() : Optional.of(reports.get(reports.size() - 1));
    }

    @Nonnull
    public synchronized List<String> latestLines() {
        Optional<File> latest = latest();
        return latest.isPresent() ? readLines(latest.get()) : Collections.<String>emptyList();
    }

    public synchronized int latestProblemCount() {
        int problems = 0;
        for (String line : latestLines()) {
            if (line.startsWith(FINDING_PREFIX) && !line.contains(SKIPPED_MARKER)) {
                problems++;
            }
        }
        return problems;
    }

    private static List<String> readLines(File file) {
        try {
            return Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            return Collections.emptyList();
        }
    }

    private void pruneOldReports() {
        List<File> reports = reportsOldestFirst();
        for (int index = 0; index < reports.size() - REPORTS_KEPT; index++) {
            reports.get(index).delete();
        }
    }

    private List<File> reportsOldestFirst() {
        File[] files = directory.listFiles((parent, name) -> name.startsWith(FILE_PREFIX) && name.endsWith(FILE_SUFFIX));
        if (files == null) {
            return Collections.emptyList();
        }
        Arrays.sort(files);
        return new ArrayList<>(Arrays.asList(files));
    }
}
