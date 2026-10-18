package io.github.thebusybiscuit.slimefun5.core.checks.sandbox;

import java.util.List;
import java.util.logging.Level;

import javax.annotation.Nonnull;

import org.bukkit.event.Event;
import org.bukkit.event.EventException;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.RegisteredListener;

/**
 * Delivers an event only to Slimefun and to the plugins that depend or soft-depend on it, so protection,
 * logging and anticheat plugins never see what a check does on a live server.
 */
final class SlimefunOnlyEvents {

    private final String slimefunName;

    SlimefunOnlyEvents(@Nonnull Plugin slimefun) {
        this.slimefunName = slimefun.getName();
    }

    void call(@Nonnull Event event) {
        for (RegisteredListener listener : event.getHandlers().getRegisteredListeners()) {
            Plugin owner = listener.getPlugin();
            if (owner.isEnabled() && isSlimefunRelated(owner)) {
                deliver(listener, event);
            }
        }
    }

    boolean isSlimefunRelated(@Nonnull Plugin plugin) {
        PluginDescriptionFile description = plugin.getDescription();
        return slimefunName.equals(plugin.getName()) || names(description.getDepend()) || names(description.getSoftDepend());
    }

    private boolean names(List<String> dependencies) {
        return dependencies != null && dependencies.contains(slimefunName);
    }

    /** Mirrors the plugin manager: one listener's failure is logged against its plugin and the rest still run. */
    private static void deliver(RegisteredListener listener, Event event) {
        try {
            listener.callEvent(event);
        } catch (EventException | RuntimeException failure) {
            Throwable cause = failure instanceof EventException && failure.getCause() != null ? failure.getCause() : failure;
            listener.getPlugin().getLogger().log(Level.SEVERE, "Could not pass sandbox event " + event.getEventName() + " to " + listener.getPlugin().getName(), cause);
        }
    }
}
