package io.github.thebusybiscuit.slimefun5.core.services.localization;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import javax.annotation.Nonnull;

import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;

public final class ItemTranslationTestAccess {

    private ItemTranslationTestAccess() {
    }

    public static void enableBundledLanguages() {
        Slimefun.getLocalization().loadEmbeddedLanguages();
    }

    public static void load(@Nonnull String language, @Nonnull String yaml) {
        ItemTranslationService service = Slimefun.getItemTranslationService();
        service.loadTranslationsForTest(language, new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));
        service.clearRenderCache();
    }
}
