package io.github.thebusybiscuit.slimefun5.core.checks.sandbox;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

/**
 * Calls on an {@link InventoryView}, made by reflection.
 *
 * @implNote {@code InventoryView} became an interface in 1.21. Core is compiled against the 1.8 class and only
 *           a real server's plugin loader rewrites direct calls for that, so elsewhere (MockBukkit) a direct
 *           call fails with {@code IncompatibleClassChangeError}.
 */
final class ViewAccess {

    private final InventoryView view;

    ViewAccess(InventoryView view) {
        this.view = view;
    }

    InventoryView view() {
        return view;
    }

    Inventory top() {
        return (Inventory) call("getTopInventory", new Class<?>[0]);
    }

    Inventory bottom() {
        return (Inventory) call("getBottomInventory", new Class<?>[0]);
    }

    ItemStack item(int rawSlot) {
        return (ItemStack) call("getItem", new Class<?>[] {int.class}, rawSlot);
    }

    void setItem(int rawSlot, ItemStack item) {
        call("setItem", new Class<?>[] {int.class, ItemStack.class}, rawSlot, item);
    }

    ItemStack cursor() {
        return (ItemStack) call("getCursor", new Class<?>[0]);
    }

    void setCursor(ItemStack item) {
        call("setCursor", new Class<?>[] {ItemStack.class}, item);
    }

    int countSlots() {
        return (Integer) call("countSlots", new Class<?>[0]);
    }

    private Object call(String name, Class<?>[] parameters, Object... arguments) {
        try {
            Method method = InventoryView.class.getMethod(name, parameters);
            return method.invoke(view, arguments);
        } catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            throw cause instanceof RuntimeException ? (RuntimeException) cause : new IllegalStateException(cause);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("InventoryView." + name + " is not callable", failure);
        }
    }
}
