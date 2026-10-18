package io.github.thebusybiscuit.slimefun5.core.checks.sandbox;

import javax.annotation.Nonnull;

/**
 * A Bukkit type the running server offers no way to simulate. The type is recorded as a {@link CheckGap}
 * first; a check that meets this skips itself rather than failing the run.
 */
public final class FacadeUnavailableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public FacadeUnavailableException(@Nonnull String message) {
        super(message);
    }
}
