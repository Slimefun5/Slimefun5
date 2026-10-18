package io.github.thebusybiscuit.slimefun5.core.checks.sandbox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nonnull;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.permissions.Permission;
import org.bukkit.plugin.Plugin;

/**
 * A player who is online without a client: a name, a client locale, an op flag, a real inventory, a cursor
 * and the window it has open. Messages it is sent are kept in {@link #messages()}.
 */
public final class SandboxPlayer {

    private static final AtomicInteger ENTITY_IDS = new AtomicInteger(100_000);

    private final String name;
    private final UUID uniqueId;
    private final String locale;
    private final SandboxWorld world;
    private final int entityId = ENTITY_IDS.incrementAndGet();
    private final MetadataStore metadata = new MetadataStore();
    private final List<String> messages = new ArrayList<>();
    private final Map<String, Boolean> permissions = new HashMap<>();
    private final Player player;
    private final PlayerInventory inventory;
    private final InventoryView craftingView;
    private InventoryView openView;
    private ItemStack cursor;
    private GameMode gameMode = GameMode.SURVIVAL;
    private boolean op;
    private Object persistentData;

    SandboxPlayer(String name, String locale, SandboxWorld world) {
        this.name = name;
        this.uniqueId = UUID.nameUUIDFromBytes(("slimefun-checks:" + name).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        this.locale = locale;
        this.world = world;
        this.player = ProxyFacade.create(Player.class, new Backing(), world.gaps());
        this.inventory = SandboxPlayerInventory.create(player, world.gaps());
        this.craftingView = SandboxInventoryView.create(this, Bukkit.createInventory(player, 9, "Crafting"), InventoryType.CRAFTING, "Crafting");
        this.openView = craftingView;
    }

    @Nonnull
    public Player player() {
        return player;
    }

    @Nonnull
    public List<String> messages() {
        return messages;
    }

    public void setOp(boolean value) {
        op = value;
    }

    public void setPermission(String permission, boolean value) {
        permissions.put(permission, value);
    }

    /** Whether a window other than the player's own inventory is open. */
    public boolean hasWindowOpen() {
        return openView != craftingView;
    }

    CheckGap gaps() {
        return world.gaps();
    }

    ItemStack cursor() {
        return cursor;
    }

    void setCursor(ItemStack item) {
        cursor = item;
    }

    private InventoryView open(Inventory top) {
        if (hasWindowOpen()) {
            close();
        }
        InventoryView view = SandboxInventoryView.create(this, top, top.getType(), InventoryTitles.of(top));
        InventoryOpenEvent event = new InventoryOpenEvent(view);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return null;
        }
        openView = view;
        return view;
    }

    /** Vanilla hands the cursor back to the inventory when a window closes, and drops what does not fit. */
    private void close() {
        if (!hasWindowOpen()) {
            return;
        }
        InventoryView closing = openView;
        Bukkit.getPluginManager().callEvent(new InventoryCloseEvent(closing));
        if (openView == closing) {
            openView = craftingView;
        }
        if (cursor != null && cursor.getType() != Material.AIR) {
            for (ItemStack leftover : inventory.addItem(cursor).values()) {
                world.world().dropItemNaturally(location(), leftover);
            }
        }
        cursor = null;
    }

    private Location location() {
        return new Location(world.world(), 0.5, 64, 0.5);
    }

    private boolean hasPermission(String permission, Permission registered) {
        Boolean explicit = permissions.get(permission.toLowerCase(java.util.Locale.ENGLISH));
        if (explicit != null) {
            return explicit;
        }
        if (registered != null) {
            return registered.getDefault().getValue(op);
        }
        return Permission.DEFAULT_PERMISSION.getValue(op);
    }

    private Object persistentDataContainer() {
        if (persistentData == null) {
            persistentData = PersistentData.newContainer();
        }
        return persistentData;
    }

    /**
     * Places {@code item} at {@code target} as a right-click with it on a neighbouring block does: the block
     * becomes the item's, then the real {@link BlockPlaceEvent} runs with the item in hand, and a cancelled
     * event puts the old block back.
     *
     * @param against   the side of {@code target} the player clicked
     * @param blockType what the item places, which is not always its own material (a 1.8 skull item places
     *                  a skull block)
     * @return whether the block stayed placed
     */
    public boolean place(Block target, BlockFace against, ItemStack item, Material blockType) {
        BlockState replaced = target.getState();
        Material before = target.getType();
        ItemStack held = inventory.getItemInHand();
        inventory.setItemInHand(item);
        target.setType(blockType);
        BlockPlaceEvent event = new BlockPlaceEvent(target, replaced, target.getRelative(against), item, player, true);
        try {
            Bukkit.getPluginManager().callEvent(event);
        } finally {
            inventory.setItemInHand(held);
        }
        if (event.isCancelled() || !event.canBuild()) {
            target.setType(before);
            return false;
        }
        return true;
    }

    final class Backing {

        String getName() {
            return name;
        }

        String getDisplayName() {
            return name;
        }

        String getPlayerListName() {
            return name;
        }

        UUID getUniqueId() {
            return uniqueId;
        }

        String getLocale() {
            return locale;
        }

        int getEntityId() {
            return entityId;
        }

        EntityType getType() {
            return EntityType.PLAYER;
        }

        Server getServer() {
            return Bukkit.getServer();
        }

        boolean isOnline() {
            return world.isOnline(player);
        }

        boolean isValid() {
            return isOnline();
        }

        boolean isDead() {
            return false;
        }

        boolean isOp() {
            return op;
        }

        void setOp(boolean value) {
            op = value;
        }

        boolean hasPermission(String permission) {
            return SandboxPlayer.this.hasPermission(permission, Bukkit.getPluginManager().getPermission(permission));
        }

        boolean hasPermission(Permission permission) {
            return SandboxPlayer.this.hasPermission(permission.getName(), permission);
        }

        boolean isPermissionSet(String permission) {
            return permissions.containsKey(permission.toLowerCase(java.util.Locale.ENGLISH));
        }

        boolean isPermissionSet(Permission permission) {
            return isPermissionSet(permission.getName());
        }

        void sendMessage(String message) {
            messages.add(message);
        }

        void sendMessage(String[] lines) {
            for (String line : lines) {
                messages.add(line);
            }
        }

        void sendRawMessage(String message) {
            messages.add(message);
        }

        World getWorld() {
            return world.world();
        }

        Location getLocation() {
            return location();
        }

        Location getLocation(Location target) {
            if (target != null) {
                Location location = location();
                target.setWorld(location.getWorld());
                target.setX(location.getX());
                target.setY(location.getY());
                target.setZ(location.getZ());
            }
            return target;
        }

        Location getEyeLocation() {
            return location().add(0, 1.62, 0);
        }

        /** A yaw of 0, which every simulated player has, faces south. */
        BlockFace getFacing() {
            return BlockFace.SOUTH;
        }

        GameMode getGameMode() {
            return gameMode;
        }

        void setGameMode(GameMode mode) {
            gameMode = mode;
        }

        double getHealth() {
            return 20;
        }

        double getMaxHealth() {
            return 20;
        }

        int getFoodLevel() {
            return 20;
        }

        int getLevel() {
            return 0;
        }

        float getExp() {
            return 0;
        }

        int getTotalExperience() {
            return 0;
        }

        boolean isSneaking() {
            return false;
        }

        boolean isFlying() {
            return false;
        }

        boolean isInsideVehicle() {
            return false;
        }

        boolean hasPlayedBefore() {
            return true;
        }

        PlayerInventory getInventory() {
            return inventory;
        }

        ItemStack getItemInHand() {
            return inventory.getItemInHand();
        }

        void setItemInHand(ItemStack item) {
            inventory.setItemInHand(item);
        }

        ItemStack getItemOnCursor() {
            return cursor;
        }

        void setItemOnCursor(ItemStack item) {
            cursor = item;
        }

        InventoryView getOpenInventory() {
            return openView;
        }

        InventoryView openInventory(Inventory top) {
            return open(top);
        }

        void closeInventory() {
            close();
        }

        Object getPersistentDataContainer() {
            return persistentDataContainer();
        }

        void setMetadata(String key, MetadataValue value) {
            metadata.setMetadata(key, value);
        }

        List<MetadataValue> getMetadata(String key) {
            return metadata.getMetadata(key);
        }

        boolean hasMetadata(String key) {
            return metadata.hasMetadata(key);
        }

        void removeMetadata(String key, Plugin plugin) {
            metadata.removeMetadata(key, plugin);
        }

        @Override
        public String toString() {
            return "SandboxPlayer{" + name + "}";
        }
    }
}
