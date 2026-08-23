package io.github.thebusybiscuit.slimefun5.core.guide.widgets;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * The set of {@link GuideWidget}s for one server boot. Empty by default (core registers none); addons
 * register their functional guide screens in {@code onEnable}. Not thread-safe: touched only on the main
 * thread (setup + guide opens).
 */
public final class GuideWidgetRegistry {

    private final Map<String, GuideWidget> byId = new LinkedHashMap<>();

    public void register(@Nonnull GuideWidget widget) {
        byId.put(widget.getId(), widget);
    }

    @Nullable
    public GuideWidget getById(@Nullable String id) {
        return id == null ? null : byId.get(id);
    }

    @Nonnull
    public List<GuideWidget> getAll() {
        List<GuideWidget> all = new ArrayList<>(byId.values());
        all.sort(Comparator.comparingInt(GuideWidget::getOrder).thenComparing(GuideWidget::getId));
        return all;
    }

    /** The widgets shown on the guide's own main menu: those not bound to any addon menu. */
    @Nonnull
    public List<GuideWidget> getForMainMenu() {
        return filter(widget -> widget.getAddon() == null);
    }

    /** The widgets an addon attached to its own guide menu, shown on that menu's bottom row. */
    @Nonnull
    public List<GuideWidget> getForAddon(@Nonnull String addon) {
        return filter(widget -> addon.equals(widget.getAddon()));
    }

    /**
     * The addon widgets that declared this category, shown alongside it in the categorized layout.
     *
     * @implNote Main-menu widgets are excluded: they already have their own place on the main menu in
     *           both layouts, so including them here would show them twice.
     */
    @Nonnull
    public List<GuideWidget> getForCategory(@Nonnull String categoryId) {
        return filter(widget -> widget.getAddon() != null && categoryId.equals(widget.getCategory()));
    }

    @Nonnull
    private List<GuideWidget> filter(@Nonnull Predicate<GuideWidget> predicate) {
        List<GuideWidget> matches = new ArrayList<>();

        for (GuideWidget widget : byId.values()) {
            if (predicate.test(widget)) {
                matches.add(widget);
            }
        }

        matches.sort(Comparator.comparingInt(GuideWidget::getOrder).thenComparing(GuideWidget::getId));
        return matches;
    }
}
