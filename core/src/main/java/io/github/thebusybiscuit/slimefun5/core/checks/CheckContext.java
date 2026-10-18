package io.github.thebusybiscuit.slimefun5.core.checks;

import javax.annotation.Nonnull;

public final class CheckContext {

    private final Findings findings;
    private final Stage stage;
    private final boolean live;

    public CheckContext(@Nonnull Findings findings, @Nonnull Stage stage, boolean live) {
        this.findings = findings;
        this.stage = stage;
        this.live = live;
    }

    @Nonnull
    public Findings findings() {
        return findings;
    }

    @Nonnull
    public Stage stage() {
        return stage;
    }

    public boolean live() {
        return live;
    }
}
