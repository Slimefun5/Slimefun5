package io.github.thebusybiscuit.slimefun5.core.checks.sandbox;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import javax.annotation.Nonnull;

import org.bukkit.entity.HumanEntity;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

/**
 * An open window of a {@link SandboxPlayer}: a top inventory over the player's own, with vanilla's raw slot
 * numbering (the top's slots, then the player's 9 to 35, then the hotbar).
 *
 * @implNote {@code InventoryView} has had four shapes, read from the running API by {@link Shape#of}: an
 *           abstract class whose {@code getTitle} is final (1.8 to 1.13), one where it is abstract (1.14 to
 *           1.19.3), one that also has an abstract {@code setTitle} (1.19.4 to 1.20.6), and an interface
 *           (1.21+). The interface gets a {@link ProxyFacade}; each class shape gets its own subclass, loaded
 *           only on its shape, since a subclass made for one fails verification on the others. The subclasses
 *           for the abstract-title shapes are compiled in their own source set against a stub of the 1.14
 *           class. Every shape answers through the one {@link Backing}.
 */
public final class SandboxInventoryView {

    private static final String DECLARED_TITLE_VIEW = "io.github.thebusybiscuit.slimefun5.core.checks.sandbox.DeclaredTitleView";
    private static final String RETITLABLE_VIEW = "io.github.thebusybiscuit.slimefun5.core.checks.sandbox.RetitlableView";
    private static final int PLAYER_SLOTS = 36;

    private SandboxInventoryView() {}

    enum Shape {
        FINAL_TITLE,
        ABSTRACT_TITLE,
        RETITLABLE,
        INTERFACE;

        static Shape of(Class<?> viewType) {
            if (viewType.isInterface()) {
                return INTERFACE;
            }
            if (Modifier.isFinal(publicMethod(viewType, "getTitle").getModifiers())) {
                return FINAL_TITLE;
            }
            return hasPublicMethod(viewType, "setTitle", String.class) ? RETITLABLE : ABSTRACT_TITLE;
        }

        private static Method publicMethod(Class<?> owner, String name, Class<?>... parameters) {
            try {
                return owner.getMethod(name, parameters);
            } catch (NoSuchMethodException absent) {
                throw new IllegalStateException(owner.getName() + " has no " + name + " method", absent);
            }
        }

        private static boolean hasPublicMethod(Class<?> owner, String name, Class<?>... parameters) {
            try {
                owner.getMethod(name, parameters);
                return true;
            } catch (NoSuchMethodException absent) {
                return false;
            }
        }
    }

    @Nonnull
    public static InventoryView create(@Nonnull SandboxPlayer player, @Nonnull Inventory top, @Nonnull InventoryType type, @Nonnull String title) {
        Backing backing = new Backing(player, top, type, title);
        switch (Shape.of(InventoryView.class)) {
            case INTERFACE:
                return ProxyFacade.create(InventoryView.class, backing, player.gaps());
            case FINAL_TITLE:
                return InheritedTitleView.create(backing);
            case ABSTRACT_TITLE:
                return compiledSeparately(DECLARED_TITLE_VIEW, backing);
            default:
                return compiledSeparately(RETITLABLE_VIEW, backing);
        }
    }

    private static InventoryView compiledSeparately(String className, Backing backing) {
        try {
            Class<?> viewClass = Class.forName(className, true, SandboxInventoryView.class.getClassLoader());
            Constructor<?> constructor = viewClass.getDeclaredConstructor(Backing.class);
            constructor.setAccessible(true);
            return (InventoryView) constructor.newInstance(backing);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Could not create a " + className, failure);
        }
    }

    /** The calls every shape of view answers. */
    static final class Backing {

        private final SandboxPlayer player;
        private final Inventory top;
        private final InventoryType type;
        private final String originalTitle;
        private String title;

        Backing(SandboxPlayer player, Inventory top, InventoryType type, String title) {
            this.player = player;
            this.top = top;
            this.type = type;
            this.originalTitle = title;
            this.title = title;
        }

        Inventory getTopInventory() {
            return top;
        }

        Inventory getBottomInventory() {
            return player.player().getInventory();
        }

        HumanEntity getPlayer() {
            return player.player();
        }

        InventoryType getType() {
            return type;
        }

        String getTitle() {
            return title;
        }

        String getOriginalTitle() {
            return originalTitle;
        }

        void setTitle(String newTitle) {
            title = newTitle;
        }

        ItemStack getCursor() {
            return player.cursor();
        }

        void setCursor(ItemStack item) {
            player.setCursor(item);
        }

        int countSlots() {
            return top.getSize() + PLAYER_SLOTS;
        }

        int convertSlot(int rawSlot) {
            if (rawSlot < top.getSize()) {
                return rawSlot;
            }
            int slot = rawSlot - top.getSize();
            return slot >= 27 ? slot - 27 : slot + 9;
        }

        Inventory getInventory(int rawSlot) {
            if (rawSlot < 0 || rawSlot >= countSlots()) {
                return null;
            }
            return rawSlot < top.getSize() ? top : getBottomInventory();
        }

        ItemStack getItem(int rawSlot) {
            Inventory inventory = getInventory(rawSlot);
            return inventory == null ? null : inventory.getItem(convertSlot(rawSlot));
        }

        void setItem(int rawSlot, ItemStack item) {
            Inventory inventory = getInventory(rawSlot);
            if (inventory != null) {
                inventory.setItem(convertSlot(rawSlot), item);
            } else if (item != null) {
                player.player().getWorld().dropItemNaturally(player.player().getLocation(), item);
            }
        }

        InventoryType.SlotType getSlotType(int rawSlot) {
            if (rawSlot < 0 || rawSlot >= countSlots()) {
                return InventoryType.SlotType.OUTSIDE;
            }
            if (rawSlot >= top.getSize() && convertSlot(rawSlot) < 9) {
                return InventoryType.SlotType.QUICKBAR;
            }
            return InventoryType.SlotType.CONTAINER;
        }

        void close() {
            player.player().closeInventory();
        }

        @Override
        public String toString() {
            return "SandboxInventoryView{" + title + ", " + type + "}";
        }
    }
}
