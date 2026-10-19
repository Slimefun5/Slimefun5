package io.github.thebusybiscuit.slimefun5.core.checks.packets;

import java.util.Optional;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;

public final class TextProblems {

    private static final Pattern RAW_ID = Pattern.compile("[A-Z0-9]+(_[A-Z0-9]+)+");
    private static final Pattern PLACEHOLDER = Pattern.compile("%[A-Za-z_-]+%|\\{\\d+}");

    private TextProblems() {
    }

    /**
     * @param shownText the text with colour codes already stripped
     * @return what is wrong with the text a player would read, or empty when it is fine
     */
    @Nonnull
    public static Optional<String> problemWith(@Nonnull String shownText) {
        String trimmed = shownText.trim();
        if (trimmed.isEmpty()) {
            return Optional.of("blank");
        }
        if (looksLikeRawId(trimmed)) {
            return Optional.of("shows a raw id");
        }
        if (hasPlaceholder(trimmed)) {
            return Optional.of("shows an unfilled placeholder");
        }
        return Optional.empty();
    }

    /** @param shownText the text with colour codes already stripped */
    public static boolean looksLikeRawId(@Nonnull String shownText) {
        return RAW_ID.matcher(shownText.trim()).matches();
    }

    /** @param shownText the text with colour codes already stripped */
    public static boolean hasPlaceholder(@Nonnull String shownText) {
        return PLACEHOLDER.matcher(shownText).find();
    }
}
