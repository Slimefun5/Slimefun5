package io.github.thebusybiscuit.slimefun5.core.services.localization;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class MenuSlotKeyTest {

    @Test
    void formatsPresetAndSlot() {
        Assertions.assertEquals("NUCLEAR_REACTOR/4", MenuSlotKey.format("NUCLEAR_REACTOR", 4));
    }

    @Test
    void parsesWhatItFormatted() {
        MenuSlotKey key = MenuSlotKey.parse(MenuSlotKey.format("CARGO_NODE_INPUT", 24));

        Assertions.assertNotNull(key);
        Assertions.assertEquals("CARGO_NODE_INPUT", key.getPresetId());
        Assertions.assertEquals(24, key.getSlot());
    }

    /** A preset id with a slash would otherwise split in the wrong place. */
    @Test
    void splitsOnTheLastSeparatorSoAnIdMayContainOne() {
        MenuSlotKey key = MenuSlotKey.parse("WEIRD/ID/7");

        Assertions.assertNotNull(key);
        Assertions.assertEquals("WEIRD/ID", key.getPresetId());
        Assertions.assertEquals(7, key.getSlot());
    }

    @Test
    void refusesMalformedValues() {
        Assertions.assertNull(MenuSlotKey.parse(null));
        Assertions.assertNull(MenuSlotKey.parse(""));
        Assertions.assertNull(MenuSlotKey.parse("NO_SLOT"));
        Assertions.assertNull(MenuSlotKey.parse("PRESET/"));
        Assertions.assertNull(MenuSlotKey.parse("/4"));
        Assertions.assertNull(MenuSlotKey.parse("PRESET/x"));
        Assertions.assertNull(MenuSlotKey.parse("PRESET/-1"));
        Assertions.assertNull(MenuSlotKey.parse("PRESET/9999999999"));
    }
}
