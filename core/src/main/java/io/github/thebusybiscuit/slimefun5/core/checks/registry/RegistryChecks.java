package io.github.thebusybiscuit.slimefun5.core.checks.registry;

import javax.annotation.Nonnull;

import io.github.thebusybiscuit.slimefun5.core.checks.CheckRegistry;

public final class RegistryChecks {

    private RegistryChecks() {
    }

    public static void registerInto(@Nonnull CheckRegistry registry) {
        registry.register(new ItemNameCheck());
        registry.register(new ItemRegistrationCheck());
        registry.register(new RecipeCheck());
        registry.register(new MenuPresetCheck());
    }
}
