package io.github.thebusybiscuit.slimefun5.core.checks;

import java.util.Locale;
import java.util.Optional;

public enum Stage {
    OFF,
    LOW,
    MEDIUM,
    HIGH,
    MAX;

    public boolean includes(Stage other) {
        return other != OFF && other.ordinal() <= ordinal();
    }

    public static Optional<Stage> parse(String text) {
        if (text == null) {
            return Optional.empty();
        }
        String normalized = text.trim().toUpperCase(Locale.ROOT);
        for (Stage stage : values()) {
            if (stage.name().equals(normalized)) {
                return Optional.of(stage);
            }
        }
        return Optional.empty();
    }
}
