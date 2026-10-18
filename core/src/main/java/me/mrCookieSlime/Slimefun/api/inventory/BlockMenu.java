package me.mrCookieSlime.Slimefun.api.inventory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.logging.Level;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import io.github.bakedlibs.dough.config.Config;
import io.github.thebusybiscuit.slimefun5.core.services.localization.PacketTranslationService;
import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun5.utils.compatibility.ReflectionCompat;

import me.mrCookieSlime.Slimefun.api.BlockStorage;

// This class will be deprecated, relocated and rewritten in a future version.
public class BlockMenu extends DirtyChestMenu {

    private Location location;

    private static String serializeLocation(Location l) {
        return l.getWorld().getName() + ';' + l.getBlockX() + ';' + l.getBlockY() + ';' + l.getBlockZ();
    }

    public BlockMenu(BlockMenuPreset preset, Location l) {
        super(preset);
        this.location = l;

        preset.clone(this);
        this.getContents();
    }

    public BlockMenu(BlockMenuPreset preset, Location l, Config cfg) {
        super(preset);
        this.location = l;

        for (int i = 0; i < 54; i++) {
            if (cfg.contains(String.valueOf(i))) {
                addItem(i, cfg.getItem(String.valueOf(i)));
            }
        }

        preset.clone(this);

        if (preset.getSize() > -1 && !preset.getPresetSlots().contains(preset.getSize() - 1) && cfg.contains(String.valueOf(preset.getSize() - 1))) {
            addItem(preset.getSize() - 1, cfg.getItem(String.valueOf(preset.getSize() - 1)));
        }

        this.getContents();
    }

    public void save(Location l) {
        if (!isDirty()) {
            return;
        }

        // To force CS-CoreLib to build the Inventory
        this.getContents();

        File file = new File("data-storage/Slimefun/stored-inventories/" + serializeLocation(l) + ".sfi");
        Config cfg = new Config(file);
        cfg.setValue("preset", preset.getID());

        for (int slot : preset.getInventorySlots()) {
            cfg.setValue(String.valueOf(slot), getItemInSlot(slot));
        }

        cfg.save();

        changes = 0;
    }

    /**
     * @implNote Deletes through the active backend and then leaves the menu dirty rather than calling
     *           {@link #save(Location)}. Both of this class's own helpers hardcode a flat {@code .sfi}
     *           path, so on a database backend the old row was left behind and the new one never
     *           written (a direct save also clears the dirty flag, which makes the next flush skip the
     *           menu entirely). {@code BlockStorage} has already re-keyed it to the new location, so
     *           the next flush persists it there.
     */
    public void move(Location l) {
        Slimefun.getBlockStorageBackend().deleteInventory(this.location);
        this.location = l;
        this.preset.newInstance(this, l);
        this.markDirty();
    }

    /**
     * Reload this {@link BlockMenu} based on its {@link BlockMenuPreset}.
     */
    public void reload() {
        this.preset.clone(this);
    }

    @Override
    public void open(Player... players) {
        // Re-skin the decorative info items into the opening player's language before showing.
        if (players.length == 1) {
            try {
                Slimefun.getMenuTranslationService().applyToMenu(this, players[0]);
            } catch (Exception | LinkageError ignored) {
                // Translation is cosmetic - never block the menu from opening.
            }
        }

        // Mark this block as viewed so the (async) ticker runs its mutations on the main thread while a
        // player is looking - closing the async-tick-vs-click dupe. Unmarked on close (BlockMenuListener).
        BlockStorage.setInventoryViewed(location, true);

        Player viewer = players.length == 1 ? players[0] : null;
        String title = viewer != null ? titleFor(viewer) : null;
        boolean byPacket = title != null && retitleByPacket(viewer, title);

        super.open(players);

        if (title != null) {
            if (!byPacket) {
                ReflectionCompat.invoke(viewer.getOpenInventory(), "setTitle", title);
            } else if (!toInventory().equals(viewer.getOpenInventory().getTopInventory())) {
                Slimefun.getPacketTranslationService().cancelWindowTitle(viewer);
            }
        }
    }

    /**
     * The title this viewer should see, or {@code null} when the shared inventory's own title already is it.
     *
     * @implNote The inventory, and with it its title, is shared by every viewer of this block, so a viewer's
     *           own title is put on the window as it opens instead.
     */
    @Nullable
    private String titleFor(@Nonnull Player viewer) {
        try {
            return Slimefun.getMenuTranslationService().getTitleFor(this, viewer);
        } catch (Exception | LinkageError ignored) {
            // Translation is cosmetic - never block the menu from opening.
            return null;
        }
    }

    /**
     * Hands the title to the packet layer, which writes it into the open-window packet itself, so it is right
     * from the first frame and on every version. Where that is unavailable the caller uses
     * {@code InventoryView#setTitle} afterwards, which only exists from 1.19.4 and flashes the stored title
     * for a frame.
     *
     * @implNote Whether the window really opened is asked with {@code equals}, never {@code ==}: the open view
     *           returns a new wrapper around the same inventory on some versions, and the packet is written
     *           later, on the network thread, so cancelling on a false mismatch drops the title.
     */
    private static boolean retitleByPacket(@Nonnull Player viewer, @Nonnull String title) {
        PacketTranslationService packets = Slimefun.getPacketTranslationService();
        if (packets == null || !packets.canRetitleWindowFor(viewer)) {
            return false;
        }
        packets.notifyWindowTitle(viewer, title);
        return true;
    }

    public Block getBlock() {
        return location.getBlock();
    }

    public Location getLocation() {
        return location;
    }

    /**
     * This method drops the contents of this {@link BlockMenu} on the ground at the given
     * {@link Location}.
     * 
     * @param l
     *            Where to drop these items
     * @param slots
     *            The slots of items that should be dropped
     */
    public void dropItems(Location l, int... slots) {
        for (int slot : slots) {
            ItemStack item = getItemInSlot(slot);

            if (item != null) {
                l.getWorld().dropItemNaturally(l, item);
                replaceExistingItem(slot, null);
            }
        }
    }

    public void delete(Location l) {
        File file = new File("data-storage/Slimefun/stored-inventories/" + serializeLocation(l) + ".sfi");

        if (file.exists()) {
            try {
                Files.delete(file.toPath());
            } catch (IOException e) {
                Slimefun.logger().log(Level.WARNING, e, () -> "Could not delete file \"" + file.getName() + '"');
            }
        }
    }
}

