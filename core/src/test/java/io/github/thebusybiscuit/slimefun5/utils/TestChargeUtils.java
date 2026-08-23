package io.github.thebusybiscuit.slimefun5.utils;

import java.util.Collections;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun5.utils.compatibility.PdcCompat;

import org.mockbukkit.mockbukkit.MockBukkit;

class TestChargeUtils {

    @BeforeAll
    public static void load() {
        MockBukkit.mock();
        MockBukkit.load(Slimefun.class);
    }

    @AfterAll
    public static void unload() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("Test setting charge")
    void testSetCharge() {
        ItemStack item = new ItemStack(Material.DIAMOND_SWORD);
        ItemMeta meta = item.getItemMeta();

        // setCharge must never bake a lore line onto the stack (see ChargeUtils#setCharge)
        ChargeUtils.setCharge(meta, 1, 10);
        Assertions.assertFalse(meta.hasLore());

        ChargeUtils.setCharge(meta, 10.1f, 100.5f);
        Assertions.assertFalse(meta.hasLore());

        // Make sure the persistent data was set
        Assertions.assertEquals(10.1, (Float) PdcCompat.get(meta, Slimefun.getRegistry().getItemChargeDataKey(), "FLOAT"), 0.001);

        // Test exceptions
        Assertions.assertThrows(IllegalArgumentException.class, () -> ChargeUtils.setCharge(null, 1, 1));
        Assertions.assertThrows(IllegalArgumentException.class, () -> ChargeUtils.setCharge(meta, -1, 10));
        Assertions.assertThrows(IllegalArgumentException.class, () -> ChargeUtils.setCharge(meta, 100, 10));
        Assertions.assertThrows(IllegalArgumentException.class, () -> ChargeUtils.setCharge(meta, 10, -10));
    }

    @Test
    @DisplayName("Test getting charge")
    void testGetCharge() {
        // Test with persistent data
        ItemStack itemWithData = new ItemStack(Material.DIAMOND_SWORD);
        ItemMeta metaWithData = itemWithData.getItemMeta();
        PdcCompat.set(metaWithData, Slimefun.getRegistry().getItemChargeDataKey(), "FLOAT", 10.5f);

        Assertions.assertEquals(10.5f, ChargeUtils.getCharge(metaWithData), 0.001);

        // Test with lore
        ItemStack itemWithLore = new ItemStack(Material.DIAMOND_SWORD);
        ItemMeta metaWithLore = itemWithLore.getItemMeta();
        metaWithLore.setLore(Collections.singletonList("&8\u21E8 &e\u26A1 &710.5 / 100.5 J".replace('&', ChatColor.COLOR_CHAR)));

        Assertions.assertEquals(10.5, ChargeUtils.getCharge(metaWithLore), 0.001);
        Assertions.assertTrue(PdcCompat.has(metaWithLore, Slimefun.getRegistry().getItemChargeDataKey(), "FLOAT"));

        // Test no data and empty lore
        ItemStack itemWithEmptyLore = new ItemStack(Material.DIAMOND_SWORD);
        ItemMeta metaWithEmptyLore = itemWithEmptyLore.getItemMeta();
        metaWithEmptyLore.setLore(Collections.emptyList());

        Assertions.assertEquals(0, ChargeUtils.getCharge(metaWithEmptyLore));

        // Test no data and no lore
        ItemStack itemWithNoDataOrLore = new ItemStack(Material.DIAMOND_SWORD);

        Assertions.assertEquals(0, ChargeUtils.getCharge(itemWithNoDataOrLore.getItemMeta()));

        // Test exceptions
        Assertions.assertThrows(IllegalArgumentException.class, () -> ChargeUtils.getCharge(null));
    }
}

