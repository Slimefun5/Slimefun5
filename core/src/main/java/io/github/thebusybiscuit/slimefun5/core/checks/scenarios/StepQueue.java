package io.github.thebusybiscuit.slimefun5.core.checks.scenarios;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Supplier;

import javax.annotation.Nonnull;

import io.github.thebusybiscuit.slimefun5.core.checks.CheckStep;

/** A check's steps, where a step can add the ones that follow it once it knows them, or wait and try again. */
final class StepQueue implements Iterator<CheckStep> {

    @FunctionalInterface
    interface Condition {

        boolean isMet() throws Exception;
    }

    private final Deque<CheckStep> pending = new ArrayDeque<>();

    @Nonnull
    StepQueue then(@Nonnull CheckStep step) {
        pending.addLast(step);
        return this;
    }

    /** Adds the steps that can only be planned once the steps before them have run, such as one per machine placed. */
    @Nonnull
    StepQueue thenPlanned(@Nonnull Supplier<List<CheckStep>> plan) {
        return then(() -> {
            List<CheckStep> planned = plan.get();
            for (int index = planned.size() - 1; index >= 0; index--) {
                pending.addFirst(planned.get(index));
            }
            return 0;
        });
    }

    /** Tries {@code ready} once a tick until it is met, holding back every step after it. */
    @Nonnull
    StepQueue thenWaitUntil(@Nonnull Condition ready) {
        return then(new CheckStep() {

            @Override
            public int run() throws Exception {
                if (ready.isMet()) {
                    return 0;
                }
                pending.addFirst(this);
                return 1;
            }
        });
    }

    @Override
    public boolean hasNext() {
        return !pending.isEmpty();
    }

    @Override
    public CheckStep next() {
        if (pending.isEmpty()) {
            throw new NoSuchElementException();
        }
        return pending.pollFirst();
    }
}
