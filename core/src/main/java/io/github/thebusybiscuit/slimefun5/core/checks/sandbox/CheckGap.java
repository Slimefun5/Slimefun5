package io.github.thebusybiscuit.slimefun5.core.checks.sandbox;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nonnull;

/**
 * The calls into sandbox facades that no backing answered, each recorded once, such as
 * {@code Player.getHealthScale()}. A check that hit one did not really run, so a recorded gap must fail it.
 */
public final class CheckGap {

    private final Set<String> signatures = new LinkedHashSet<>();

    public synchronized void record(@Nonnull String signature) {
        signatures.add(signature);
    }

    @Nonnull
    public synchronized List<String> recorded() {
        return new ArrayList<>(signatures);
    }
}
