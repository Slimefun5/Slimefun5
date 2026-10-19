package io.github.thebusybiscuit.slimefun5.api.items;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;

class SlimefunItemStackQuantityTest {

    @BeforeAll
    static void load() {
        MockBukkit.mock();
        MockBukkit.load(Slimefun.class);
    }

    @AfterAll
    static void unload() {
        MockBukkit.unmock();
    }

    @Test
    void asQuantityCopiesWithoutChangingTheOriginal() {
        SlimefunItemStack stack = new SlimefunItemStack("QUANTITY_TEST_COPY", Material.PAPER);

        ItemStack eight = stack.asQuantity(8);

        Assertions.assertEquals(8, eight.getAmount());
        Assertions.assertEquals(1, stack.item().getAmount());
        Assertions.assertEquals(1, stack.asOne().getAmount());
    }

    @Test
    void addStopsAtTheMaximumStackSize() {
        SlimefunItemStack stack = new SlimefunItemStack("QUANTITY_TEST_ADD", Material.PAPER);

        Assertions.assertEquals(64, stack.add(100).getAmount());
    }

    @Test
    void subtractStopsAtZero() {
        SlimefunItemStack stack = new SlimefunItemStack("QUANTITY_TEST_SUBTRACT", Material.PAPER);
        stack.add(4);

        Assertions.assertEquals(2, stack.subtract(3).getAmount());
        Assertions.assertEquals(0, stack.subtract(5).getAmount());
    }
}
