package io.github.thebusybiscuit.slimefun5.core.checks;

import java.util.Iterator;

public interface Check {

    String name();

    Stage stage();

    default CheckThread thread() {
        return CheckThread.ANY;
    }

    default boolean liveSafe() {
        return true;
    }

    Iterator<CheckStep> steps(CheckContext context) throws Exception;
}
