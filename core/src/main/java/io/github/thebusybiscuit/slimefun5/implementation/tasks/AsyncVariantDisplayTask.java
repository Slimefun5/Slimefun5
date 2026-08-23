package io.github.thebusybiscuit.slimefun5.implementation.tasks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.annotation.Nonnull;

import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;
import io.github.bakedlibs.dough.collections.LoopIterator;

/**
 * Cycles a guide slot through a {@link io.github.thebusybiscuit.slimefun5.core.guide.variants.VariantGroup}'s
 * members so one slot can advertise all of them.
 *
 * @implNote Deliberately the same shape as {@link AsyncRecipeChoiceTask}, which already animates
 *           {@code MaterialChoice} recipe slots: an async repeating task that cancels itself once nobody is
 *           viewing the inventory, so a closed guide leaves nothing running.
 */
public class AsyncVariantDisplayTask implements Runnable {

    private static final int UPDATE_INTERVAL = 20;

    private final Map<Integer, LoopIterator<ItemStack>> iterators = new HashMap<>();
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    private Inventory inventory;
    private int id;

    /** Starts cycling every registered slot in {@code inv}. */
    public void start(@Nonnull Inventory inv) {
        inventory = inv;
        id = Bukkit.getScheduler().runTaskTimerAsynchronously(Slimefun.instance(), this, UPDATE_INTERVAL, UPDATE_INTERVAL).getTaskId();
    }

    /**
     * Registers {@code stacks} as the rotation for {@code slot}. The first stack is expected to be the one
     * already drawn there, so the rotation starts on the second.
     */
    public void add(int slot, @Nonnull List<ItemStack> stacks) {
        if (stacks.size() < 2) {
            return; // nothing to cycle
        }

        lock.writeLock().lock();

        try {
            iterators.put(slot, new LoopIterator<>(new ArrayList<>(stacks)));
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean isEmpty() {
        lock.readLock().lock();

        try {
            return iterators.isEmpty();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void run() {
        if (inventory == null || inventory.getViewers().isEmpty()) {
            Bukkit.getScheduler().cancelTask(id);
            return;
        }

        lock.readLock().lock();

        try {
            for (Map.Entry<Integer, LoopIterator<ItemStack>> entry : iterators.entrySet()) {
                inventory.setItem(entry.getKey(), entry.getValue().next().clone());
            }
        } finally {
            lock.readLock().unlock();
        }
    }
}
