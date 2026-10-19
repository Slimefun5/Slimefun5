package io.github.thebusybiscuit.slimefun5.core.services.localization;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.bukkit.ChatColor;
import org.bukkit.inventory.meta.ItemMeta;

import io.github.thebusybiscuit.slimefun5.api.items.LiveLore;
import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;

/**
 * Renders an item's {@link LiveLore} lines in one language and places them below the lore it already shows.
 *
 * @implNote Runs on the Netty thread, so it reads only stores keyed by language id, never a {@code Player}.
 */
final class LiveLoreRenderer {

    private static final String ENGLISH = "en";

    private final AddonMessageService messages;
    private final BiFunction<String, String, String> slimefunItemName;
    private final BiFunction<String, String, String> materialName;

    /**
     * @param slimefunItemName an item id and a language id to the item's name in that language
     * @param materialName     a material name and a language id to its vanilla name in that language
     */
    LiveLoreRenderer(@Nonnull AddonMessageService messages, @Nonnull BiFunction<String, String, String> slimefunItemName,
            @Nonnull BiFunction<String, String, String> materialName) {
        this.messages = messages;
        this.slimefunItemName = slimefunItemName;
        this.materialName = materialName;
    }

    @Nonnull
    static LiveLoreRenderer forPackets(@Nonnull TranslationConfig.FallbackMode fallback) {
        return new LiveLoreRenderer(Slimefun.getAddonMessageService(), (id, language) -> {
            ItemTranslationService.RenderedDisplay display = Slimefun.getItemTranslationService().renderForPacket(id, language, fallback, false);
            return display != null ? display.name : id;
        }, (material, language) -> Slimefun.getVanillaTranslationService().material(language, material));
    }

    /**
     * @return {@code lore} followed by the item's live lines, separated by one blank line; {@code lore} itself when
     *         the item has none
     */
    @Nonnull
    List<String> appendTo(@Nonnull List<String> lore, @Nullable ItemMeta meta, @Nullable String languageId) {
        List<LiveLore.Line> lines = LiveLore.get(meta);
        if (lines.isEmpty()) {
            return lore;
        }
        List<String> combined = new ArrayList<>(lore);
        if (!combined.isEmpty() && !ChatColor.stripColor(combined.get(combined.size() - 1)).trim().isEmpty()) {
            combined.add("");
        }
        for (LiveLore.Line line : lines) {
            combined.add(render(line, languageId));
        }
        return combined;
    }

    @Nonnull
    String render(@Nonnull LiveLore.Line line, @Nullable String languageId) {
        String text = ChatColor.translateAlternateColorCodes('&', template(line, languageId));
        List<String> arguments = line.getArguments();
        for (int index = 0; index < arguments.size(); index++) {
            text = text.replace("{" + index + "}", argument(arguments.get(index), languageId));
        }
        return text;
    }

    private String argument(String stored, @Nullable String languageId) {
        LiveLore.Argument argument = LiveLore.Argument.parse(stored);
        switch (argument.getKind()) {
            case SLIMEFUN_ITEM:
                return slimefunItemName.apply(argument.getValue(), languageId);
            case MATERIAL:
                return materialName.apply(argument.getValue(), languageId);
            default:
                return argument.getValue();
        }
    }

    private String template(LiveLore.Line line, @Nullable String languageId) {
        Object translated = languageId == null ? null : messages.lookup(languageId, line.getAddonId(), line.getKey());
        if (!(translated instanceof String)) {
            translated = messages.lookup(ENGLISH, line.getAddonId(), line.getKey());
        }
        return translated instanceof String ? (String) translated : line.getKey();
    }
}
