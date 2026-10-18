package io.github.thebusybiscuit.slimefun5.core.checks;

import java.util.function.Consumer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import io.github.thebusybiscuit.slimefun5.core.checks.sandbox.Sandbox;

public final class CheckContext {

    static final String GAP_CHECK = "sandbox-gap";
    static final String GAP_PROBLEM = "the sandbox could not answer this call";
    static final String CLOSE_CHECK = "sandbox-close";
    static final String CLOSE_PROBLEM = "the sandbox could not undo this when it closed";

    private final Findings findings;
    private final Stage stage;
    private final boolean live;
    private final Consumer<String> activity;
    private Sandbox sandbox;

    public CheckContext(@Nonnull Findings findings, @Nonnull Stage stage, boolean live) {
        this(findings, stage, live, ignored -> {});
    }

    /**
     * @param activity told what a check is doing as it does it, such as the menu and slot it clicks, and null
     *                 between checks
     */
    public CheckContext(@Nonnull Findings findings, @Nonnull Stage stage, boolean live, @Nonnull Consumer<String> activity) {
        this.findings = findings;
        this.stage = stage;
        this.live = live;
        this.activity = activity;
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

    /** Names what the running check is doing now, so whatever is logged meanwhile can be traced to it. */
    public void describe(@Nullable String what) {
        activity.accept(what);
    }

    /**
     * @return the open sandbox from the first {@link Stage#MEDIUM} or higher check on, null before it
     */
    @Nullable
    public synchronized Sandbox sandbox() {
        return sandbox;
    }

    synchronized void openSandboxFor(@Nonnull Check check) {
        if (sandbox == null && check.stage().compareTo(Stage.MEDIUM) >= 0) {
            sandbox = Sandbox.open(live);
        }
    }

    /**
     * What the sandbox could not answer is reported before it closes, and what it could not undo after, so
     * either fails the run it happened in.
     */
    synchronized void closeSandbox() {
        if (sandbox == null) {
            return;
        }
        Sandbox closing = sandbox;
        sandbox = null;
        try {
            for (String gap : closing.gaps().recorded()) {
                findings.report(GAP_CHECK, gap, GAP_PROBLEM);
            }
        } finally {
            closing.close();
            for (String failure : closing.closeFailures()) {
                findings.report(CLOSE_CHECK, failure, CLOSE_PROBLEM);
            }
        }
    }
}
