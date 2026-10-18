package io.github.thebusybiscuit.slimefun5.core.checks.registry;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

import javax.annotation.Nonnull;

import io.github.thebusybiscuit.slimefun5.core.checks.CheckStep;

final class CheckBatches {

    private CheckBatches() {
    }

    /**
     * @implNote Works on a snapshot taken now, so a registry that changes while the run is sliced cannot
     *           throw a concurrent modification.
     */
    @Nonnull
    static <T> Iterator<CheckStep> stepsOver(@Nonnull Iterable<T> subjects, int batchSize, @Nonnull Consumer<T> perSubject) {
        List<T> snapshot = new ArrayList<>();
        subjects.forEach(snapshot::add);
        List<CheckStep> steps = new ArrayList<>();
        for (int start = 0; start < snapshot.size(); start += batchSize) {
            List<T> batch = snapshot.subList(start, Math.min(start + batchSize, snapshot.size()));
            steps.add(() -> {
                batch.forEach(perSubject);
                return 0;
            });
        }
        return steps.iterator();
    }
}
