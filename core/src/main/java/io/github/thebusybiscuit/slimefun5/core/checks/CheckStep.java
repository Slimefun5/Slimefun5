package io.github.thebusybiscuit.slimefun5.core.checks;

@FunctionalInterface
public interface CheckStep {

    /**
     * @return the number of ticks to wait before the next step, 0 to continue immediately
     */
    int run() throws Exception;
}
