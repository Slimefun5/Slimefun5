package io.github.thebusybiscuit.slimefun5.core.checks.registry;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import io.github.thebusybiscuit.slimefun5.core.checks.Check;
import io.github.thebusybiscuit.slimefun5.core.checks.CheckContext;
import io.github.thebusybiscuit.slimefun5.core.checks.CheckStep;
import io.github.thebusybiscuit.slimefun5.core.checks.Findings;
import io.github.thebusybiscuit.slimefun5.core.checks.Stage;
import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;

/**
 * Reports addons whose {@code slimefun-api} plugin.yml key is older than the running core, and addons
 * that predate the key.
 *
 * @implNote Runs on {@code CheckThread.ANY}: it only reads the plugin list and each plugin's own jar
 *           resource, and calls no other Bukkit API. The key is read from the jar because
 *           {@code PluginDescriptionFile} drops unknown keys on 1.8.8. A core version that does not
 *           parse (a DEV build) makes staleness incomparable, so only the missing-key line is reported.
 */
public final class AddonStalenessCheck implements Check {

    static final String API_KEY = "slimefun-api";

    /** One addon as the check sees it; {@code api} is null when its plugin.yml has no key. */
    static final class AddonInfo {

        private final String name;
        private final String api;

        AddonInfo(@Nonnull String name, @Nullable String api) {
            this.name = name;
            this.api = api;
        }
    }

    private final Supplier<List<AddonInfo>> addons;
    private final Supplier<String> coreVersion;

    public AddonStalenessCheck() {
        this(AddonStalenessCheck::installedAddons, Slimefun::getVersion);
    }

    AddonStalenessCheck(@Nonnull Supplier<List<AddonInfo>> addons, @Nonnull Supplier<String> coreVersion) {
        this.addons = addons;
        this.coreVersion = coreVersion;
    }

    @Override
    public String name() {
        return "addon-staleness";
    }

    @Override
    public Stage stage() {
        return Stage.LOW;
    }

    @Override
    public Iterator<CheckStep> steps(CheckContext context) {
        CheckStep step = () -> {
            report(context.findings(), addons.get(), coreVersion.get());
            return 0;
        };
        return Collections.singletonList(step).iterator();
    }

    private void report(Findings findings, List<AddonInfo> installed, String core) {
        Optional<ApiVersion> running = ApiVersion.parse(core);
        List<String> unrecorded = new ArrayList<>();
        for (AddonInfo addon : installed) {
            if (addon.api == null) {
                unrecorded.add(addon.name);
            } else if (running.isPresent() && isOlder(addon.api, running.get())) {
                findings.report(name(), addon.name, "built against Slimefun " + addon.api + ", running on " + core);
            }
        }
        if (!unrecorded.isEmpty()) {
            findings.report(name(), "addons", "built before slimefun-api was recorded: " + String.join(", ", unrecorded));
        }
    }

    private static boolean isOlder(String api, ApiVersion running) {
        Optional<ApiVersion> built = ApiVersion.parse(api);
        return built.isPresent() && built.get().compareTo(running) < 0;
    }

    @Nonnull
    private static List<AddonInfo> installedAddons() {
        List<AddonInfo> found = new ArrayList<>();
        for (Plugin plugin : Bukkit.getPluginManager().getPlugins()) {
            if (plugin != Slimefun.instance() && dependsOnSlimefun(plugin)) {
                found.add(new AddonInfo(plugin.getName(), readApiKey(plugin)));
            }
        }
        return found;
    }

    private static boolean dependsOnSlimefun(Plugin plugin) {
        return plugin.getDescription().getDepend().contains("Slimefun")
            || plugin.getDescription().getSoftDepend().contains("Slimefun");
    }

    @Nullable
    private static String readApiKey(Plugin plugin) {
        try (InputStream stream = plugin.getResource("plugin.yml")) {
            if (stream == null) {
                return null;
            }
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
            Object value = yaml.get(API_KEY);
            return value == null ? null : value.toString();
        } catch (Exception failure) {
            return null;
        }
    }
}
