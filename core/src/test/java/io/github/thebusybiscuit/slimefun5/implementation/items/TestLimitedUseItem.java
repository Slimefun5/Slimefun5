package io.github.thebusybiscuit.slimefun5.implementation.items;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.thebusybiscuit.slimefun5.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun5.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun5.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun5.core.handlers.ItemUseHandler;
import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun5.libraries.keys.NamespacedKey;

import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

class TestLimitedUseItem {

    private static ServerMock server;
    private static Slimefun plugin;

    @BeforeAll
    public static void load() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(Slimefun.class);
    }

    @AfterAll
    public static void unload() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("damageItem must never bake a lore line onto the stack")
    void damageItemDoesNotBakeLore() {
        LimitedUseItemMock item = mock("LIMITED_USE_TEST", 3);
        ItemStack stack = item.getItem().clone();

        Assertions.assertEquals(3, item.getUsesLeft(stack));

        Player player = server.addPlayer();
        item.useOnce(player, stack);

        Assertions.assertEquals(2, item.getUsesLeft(stack));
        Assertions.assertFalse(stack.hasItemMeta() && stack.getItemMeta().hasLore(),
            "uses-left must be tracked via PDC only, never baked into the stack's lore");
    }

    @Test
    @DisplayName("the item breaks after its last use")
    void itemBreaksAfterLastUse() {
        LimitedUseItemMock item = mock("LIMITED_USE_BREAK_TEST", 1);
        ItemStack stack = item.getItem().clone();

        Player player = server.addPlayer();
        item.useOnce(player, stack);

        Assertions.assertEquals(Material.AIR, stack.getType());
    }

    private LimitedUseItemMock mock(String id, int maxUses) {
        ItemGroup itemGroup = new ItemGroup(new NamespacedKey(plugin, id.toLowerCase() + "_group"), new ItemStack(Material.EMERALD));
        LimitedUseItemMock item = new LimitedUseItemMock(itemGroup, new SlimefunItemStack(id, Material.STICK), maxUses);
        item.register(plugin);
        return item;
    }

    private static class LimitedUseItemMock extends LimitedUseItem {

        @ParametersAreNonnullByDefault
        protected LimitedUseItemMock(ItemGroup group, SlimefunItemStack item, int maxUses) {
            super(group, item, RecipeType.NULL, new ItemStack[9]);
            setMaxUseCount(maxUses);
        }

        @ParametersAreNonnullByDefault
        void useOnce(Player p, ItemStack item) {
            damageItem(p, item);
        }

        @Override
        @Nonnull
        public ItemUseHandler getItemHandler() {
            return e -> {};
        }
    }
}
