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
        if (RAW_ID.matcher(trimmed).matches()) {
            return Optional.of("shows a raw id");
        }
        if (PLACEHOLDER.matcher(trimmed).find()) {
            return Optional.of("shows an unfilled placeholder");
        }
        return Optional.empty();
    }
}
