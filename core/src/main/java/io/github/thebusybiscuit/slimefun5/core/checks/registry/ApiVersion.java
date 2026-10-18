package io.github.thebusybiscuit.slimefun5.core.checks.registry;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * A dotted numeric version such as {@code 5.3.3.3}.
 *
 * @implNote Everything from the first non-numeric segment on is ignored, so {@code 5.3.3.3-UNOFFICIAL}
 *           reads as {@code 5.3.3.3} and a build name such as {@code DEV - abc} has no version at all.
 */
final class ApiVersion implements Comparable<ApiVersion> {

    private static final Pattern NUMERIC_SEGMENT = Pattern.compile("(\\d{1,9})(.*)");

    private final List<Integer> segments;

    private ApiVersion(@Nonnull List<Integer> segments) {
        this.segments = segments;
    }

    @Nonnull
    static Optional<ApiVersion> parse(@Nullable String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String text = raw.trim();
        if (text.startsWith("v") || text.startsWith("V")) {
            text = text.substring(1);
        }
        List<Integer> segments = new ArrayList<>();
        for (String segment : text.split("\\.", -1)) {
            Matcher matcher = NUMERIC_SEGMENT.matcher(segment);
            if (!matcher.matches()) {
                break;
            }
            segments.add(Integer.valueOf(matcher.group(1)));
            if (!matcher.group(2).isEmpty()) {
                break;
            }
        }
        return segments.isEmpty() ? Optional.empty() : Optional.of(new ApiVersion(segments));
    }

    @Override
    public int compareTo(@Nonnull ApiVersion other) {
        int length = Math.max(segments.size(), other.segments.size());
        for (int index = 0; index < length; index++) {
            int difference = Integer.compare(segmentAt(index), other.segmentAt(index));
            if (difference != 0) {
                return difference;
            }
        }
        return 0;
    }

    private int segmentAt(int index) {
        return index < segments.size() ? segments.get(index) : 0;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ApiVersion && compareTo((ApiVersion) other) == 0;
    }

    @Override
    public int hashCode() {
        return segments.hashCode();
    }
}
