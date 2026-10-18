package io.github.thebusybiscuit.slimefun5.core.checks;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.annotation.Nonnull;

public final class CheckRegistry {

    private final Collection<Check> checks = new CopyOnWriteArrayList<>();

    public void register(@Nonnull Check check) {
        checks.add(check);
    }

    @Nonnull
    public Collection<Check> all() {
        return Collections.unmodifiableCollection(checks);
    }
}
