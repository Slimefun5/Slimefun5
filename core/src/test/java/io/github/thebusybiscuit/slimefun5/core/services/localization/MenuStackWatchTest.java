package io.github.thebusybiscuit.slimefun5.core.services.localization;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import io.github.thebusybiscuit.slimefun5.core.checks.LiveChecks;
import io.github.thebusybiscuit.slimefun5.core.checks.Stage;
import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;

class MenuStackWatchTest {

    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        MockBukkit.load(Slimefun.class);
        LiveChecks.useMessenger((recipient, key, placeholders) -> { });
    }

    @AfterEach
    void tearDown() {
        LiveChecks.stop();
        MockBukkit.unmock();
    }

    @Test
    void aThrowingStampReadKeepsTheTranslatedMenuItem() {
        LiveChecks.start(Stage.LOW, server.addPlayer());
        Assertions.assertNotNull(LiveChecks.activeWatch());
        ItemStack translated = new ItemStack(Material.PAPER) {
            @Override
            public ItemMeta getItemMeta() {
                throw new IllegalStateException("unreadable stamp");
            }
        };

        PacketTranslationService.TranslatedStack watched = PacketTranslationService.menuStack(translated);

        Assertions.assertSame(translated, watched.stack);
        Assertions.assertNull(watched.shownName);
    }
}
