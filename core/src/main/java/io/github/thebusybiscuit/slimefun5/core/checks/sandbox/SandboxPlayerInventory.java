package io.github.thebusybiscuit.slimefun5.core.checks.sandbox;

import java.lang.reflect.Method;

import org.bukkit.Bukkit;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * A player inventory laid out as Bukkit numbers it (0 to 8 the hotbar, 9 to 35 the rest, 36 to 39 boots to
 * helmet, 40 the off hand from 1.9), stored in two of the server's own inventories, so every item is a real
 * server item.
 */
final class SandboxPlayerInventory {

    private static final int STORAGE = 36;
    private static final int BOOTS = 0;
    private static final int LEGGINGS = 1;
    private static final int CHESTPLATE = 2;
    private static final int HELMET = 3;
    private static final int OFF_HAND = 4;

    private final HumanEntity holder;
    private final Inventory storage;
    private final Inventory equipment;
    private final int size;
    private int heldSlot;

    private SandboxPlayerInventory(HumanEntity holder) {
        this.holder = holder;
        this.storage = Bukkit.createInventory(holder, STORAGE);
        this.equipment = Bukkit.createInventory(holder, 9);
        this.size = hasOffHand() ? STORAGE + 5 : STORAGE;
    }

    static PlayerInventory create(HumanEntity holder, CheckGap gaps) {
        return ProxyFacade.create(PlayerInventory.class, new SandboxPlayerInventory(holder).new Backing(), gaps);
    }

    private static boolean hasOffHand() {
        try {
            PlayerInventory.class.getMethod("getItemInOffHand");
            return true;
        } catch (NoSuchMethodException absent) {
            return false;
        }
    }

    private ItemStack get(int index) {
        if (index < STORAGE) {
            return storage.getItem(index);
        }
        return equipment.getItem(index - STORAGE);
    }

    private void set(int index, ItemStack item) {
        if (index < STORAGE) {
            storage.setItem(index, item);
        } else {
            equipment.setItem(index - STORAGE, item);
        }
    }

    private static int equipmentIndex(Object slot) {
        switch (((Enum<?>) slot).name()) {
            case "HAND":
                return -1;
            case "OFF_HAND":
                return OFF_HAND;
            case "FEET":
                return BOOTS;
            case "LEGS":
                return LEGGINGS;
            case "CHEST":
                return CHESTPLATE;
            case "HEAD":
                return HELMET;
            default:
                throw new IllegalArgumentException("A player has no " + slot + " slot");
        }
    }

    final class Backing implements ProxyFacade.Delegating {

        @Override
        public Object delegateFor(Method method) {
            return method.getDeclaringClass() == Inventory.class ? storage : null;
        }

        int getSize() {
            return size;
        }

        ItemStack getItem(int index) {
            return get(index);
        }

        void setItem(int index, ItemStack item) {
            set(index, item);
        }

        ItemStack getItem(Object equipmentSlot) {
            int index = equipmentIndex(equipmentSlot);
            return index < 0 ? storage.getItem(heldSlot) : equipment.getItem(index);
        }

        void setItem(Object equipmentSlot, ItemStack item) {
            int index = equipmentIndex(equipmentSlot);
            if (index < 0) {
                storage.setItem(heldSlot, item);
            } else {
                equipment.setItem(index, item);
            }
        }

        ItemStack[] getContents() {
            ItemStack[] contents = new ItemStack[size];
            for (int i = 0; i < size; i++) {
                contents[i] = get(i);
            }
            return contents;
        }

        void setContents(ItemStack[] contents) {
            for (int i = 0; i < size; i++) {
                set(i, i < contents.length ? contents[i] : null);
            }
        }

        ItemStack[] getStorageContents() {
            return storage.getContents();
        }

        void setStorageContents(ItemStack[] contents) {
            storage.setContents(contents);
        }

        ItemStack[] getArmorContents() {
            return new ItemStack[] {equipment.getItem(BOOTS), equipment.getItem(LEGGINGS), equipment.getItem(CHESTPLATE), equipment.getItem(HELMET)};
        }

        void setArmorContents(ItemStack[] armor) {
            for (int i = 0; i < 4; i++) {
                equipment.setItem(i, i < armor.length ? armor[i] : null);
            }
        }

        ItemStack[] getExtraContents() {
            return new ItemStack[] {equipment.getItem(OFF_HAND)};
        }

        ItemStack getHelmet() {
            return equipment.getItem(HELMET);
        }

        ItemStack getChestplate() {
            return equipment.getItem(CHESTPLATE);
        }

        ItemStack getLeggings() {
            return equipment.getItem(LEGGINGS);
        }

        ItemStack getBoots() {
            return equipment.getItem(BOOTS);
        }

        void setHelmet(ItemStack item) {
            equipment.setItem(HELMET, item);
        }

        void setChestplate(ItemStack item) {
            equipment.setItem(CHESTPLATE, item);
        }

        void setLeggings(ItemStack item) {
            equipment.setItem(LEGGINGS, item);
        }

        void setBoots(ItemStack item) {
            equipment.setItem(BOOTS, item);
        }

        ItemStack getItemInHand() {
            return storage.getItem(heldSlot);
        }

        void setItemInHand(ItemStack item) {
            storage.setItem(heldSlot, item);
        }

        ItemStack getItemInMainHand() {
            return storage.getItem(heldSlot);
        }

        void setItemInMainHand(ItemStack item) {
            storage.setItem(heldSlot, item);
        }

        ItemStack getItemInOffHand() {
            return equipment.getItem(OFF_HAND);
        }

        void setItemInOffHand(ItemStack item) {
            equipment.setItem(OFF_HAND, item);
        }

        int getHeldItemSlot() {
            return heldSlot;
        }

        void setHeldItemSlot(int slot) {
            heldSlot = slot;
        }

        void clear() {
            storage.clear();
            equipment.clear();
        }

        void clear(int index) {
            set(index, null);
        }

        HumanEntity getHolder() {
            return holder;
        }

        InventoryType getType() {
            return InventoryType.PLAYER;
        }

        String getTitle() {
            return "Inventory";
        }

        String getName() {
            return "container.inventory";
        }

        @Override
        public String toString() {
            return "SandboxPlayerInventory{" + holder.getName() + "}";
        }
    }
}
