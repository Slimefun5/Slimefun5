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

    public static final String RUN_PREFIX = "report-";
    public static final String WATCH_PREFIX = "watch-";

    private static final String FILE_SUFFIX = ".txt";
    private static final String HEADER_PREFIX = "# ";
    private static final String FINDING_PREFIX = "[check:";
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneId.systemDefault());

    private final File directory;
    private final String filePrefix;

    public CheckReport(@Nonnull File directory) {
        this(directory, RUN_PREFIX);
    }

    /**
     * @param filePrefix what the file names start with; reports of another prefix in the same directory are
     *                   never listed, read or pruned by this one
     */
    public CheckReport(@Nonnull File directory, @Nonnull String filePrefix) {
        this.directory = directory;
        this.filePrefix = filePrefix;
    }

    @Nonnull
    public synchronized File write(@Nonnull Stage stage, @Nonnull List<String> lines, @Nonnull Instant at) {
        return write(stage.name().toLowerCase(Locale.ROOT), lines, at);
    }

    /**
     * @implNote A report never replaces one written in the same second: the name moves on a second until it is free.
     */
    @Nonnull
    public synchronized File write(@Nonnull String label, @Nonnull List<String> lines, @Nonnull Instant at) {
        File file = unusedFile(at);
        List<String> content = new ArrayList<>();
        content.add(HEADER_PREFIX + "Slimefun checks (" + label + ") at " + at);
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

    @Nonnull
    public synchronized List<String> latestProblemLines() {
        return problemsIn(latestLines());
    }

    public synchronized int latestProblemCount() {
        return latestProblemLines().size();
    }

    /**
     * Findings in this prefix's reports written after {@code reference}; all of them when there is no reference.
     *
     * @implNote Compared by the timestamp in the file name, which sorts chronologically whatever the prefix.
     */
    @Nonnull
    public synchronized List<String> problemLinesAfter(@Nonnull Optional<File> reference) {
        String referenceStamp = reference.isPresent() ? stampOf(reference.get()) : "";
        List<String> problems = new ArrayList<>();
        for (File file : reportsOldestFirst()) {
            if (stampOf(file).compareTo(referenceStamp) > 0) {
                problems.addAll(problemsIn(readLines(file)));
            }
        }
        return problems;
    }

    private static String stampOf(File file) {
        String name = file.getName();
        return name.substring(name.indexOf('-') + 1, name.length() - FILE_SUFFIX.length());
    }

    private static List<String> problemsIn(List<String> lines) {
        List<String> problems = new ArrayList<>();
        for (String line : lines) {
            if (line.startsWith(FINDING_PREFIX) && !Findings.isSkipped(line)) {
                problems.add(line);
            }
        }
        return problems;
    }

    private File unusedFile(Instant at) {
        File file = new File(directory, filePrefix + FILE_TIME.format(at) + FILE_SUFFIX);
        return file.exists() ? unusedFile(at.plusSeconds(1)) : file;
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
        File[] files = directory.listFiles((parent, name) -> name.startsWith(filePrefix) && name.endsWith(FILE_SUFFIX));
        if (files == null) {
            return Collections.emptyList();
        }
        Arrays.sort(files);
        return new ArrayList<>(Arrays.asList(files));
    }
}
