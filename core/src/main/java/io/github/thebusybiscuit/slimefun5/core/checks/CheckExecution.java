package io.github.thebusybiscuit.slimefun5.core.checks;

import java.util.Iterator;

final class CheckExecution {

    private final Check check;
    private final CheckContext context;
    private Iterator<CheckStep> steps;
    private boolean finished;

    CheckExecution(Check check, CheckContext context) {
        this.check = check;
        this.context = context;
    }

    boolean isFinished() {
        return finished;
    }

    /**
     * @return the ticks to wait before the next step; 0 when the check crashed or finished
     */
    int runStep() {
        try {
            if (steps == null) {
                steps = check.steps(context);
            }
            if (!steps.hasNext()) {
                finished = true;
                return 0;
            }
            int wait = steps.next().run();
            finished = !steps.hasNext();
            return Math.max(wait, 0);
        } catch (Exception | LinkageError failure) {
            finished = true;
            context.findings().report(check.name(), check.name(), "the check itself crashed: " + failure);
            return 0;
        }
    }
}
