package io.github.thebusybiscuit.slimefun5.core.checks.sandbox;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

import javax.annotation.Nonnull;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.inventory.ItemStack;

/**
 * A world held entirely in memory: every chunk is loaded, every block starts as air, whatever is dropped is
 * kept in {@link #drops()} so item counts can account for it, and its players are the ones who
 * {@link #join joined} it.
 */
public final class SandboxWorld {

    private static final int MAX_HEIGHT = 256;

    private final String name;
    private final CheckGap gaps;
    private final UUID uid = UUID.randomUUID();
    private final World world;
    private final Map<BlockPosition, Block> blocks = new HashMap<>();
    private final Map<Long, Chunk> chunks = new HashMap<>();
    private final List<ItemStack> drops = new ArrayList<>();
    private final List<Entity> entities = new ArrayList<>();
    private final List<SandboxPlayer> players = new CopyOnWriteArrayList<>();

    private SandboxWorld(String name, CheckGap gaps) {
        this.name = name;
        this.gaps = gaps;
        this.world = ProxyFacade.create(World.class, new Backing(), gaps);
    }

    /** A world no server knows of. */
    @Nonnull
    public static SandboxWorld create(@Nonnull String name, @Nonnull CheckGap gaps) {
        return new SandboxWorld(name, gaps);
    }

    /** Creates the world, registers it with the server and announces it the way a loading world is announced. */
    @Nonnull
    public static SandboxWorld load(@Nonnull String name, @Nonnull CheckGap gaps) {
        SandboxWorld sandbox = new SandboxWorld(name, gaps);
        sandbox.register();
        Bukkit.getPluginManager().callEvent(new WorldInitEvent(sandbox.world));
        Bukkit.getPluginManager().callEvent(new WorldLoadEvent(sandbox.world));
        return sandbox;
    }

    @Nonnull
    public World world() {
        return world;
    }

    @Nonnull
    public CheckGap gaps() {
        return gaps;
    }

    @Nonnull
    public Block blockAt(int x, int y, int z) {
        return blocks.computeIfAbsent(new BlockPosition(x, y, z), position -> SandboxBlock.create(this, x, y, z));
    }

    /** Every stack dropped into this world, in order. */
    @Nonnull
    public List<ItemStack> drops() {
        return drops;
    }

    /**
     * A player who is online in this world from now on.
     *
     * @param locale the client's locale, such as {@code de_de}; the language a plugin reads from the client
     */
    @Nonnull
    public SandboxPlayer join(@Nonnull String playerName, @Nonnull String locale) {
        SandboxPlayer player = new SandboxPlayer(playerName, locale, this);
        players.add(player);
        return player;
    }

    public void quit(@Nonnull SandboxPlayer player) {
        player.player().closeInventory();
        players.remove(player);
    }

    @Nonnull
    public List<SandboxPlayer> players() {
        return new ArrayList<>(players);
    }

    boolean isOnline(Player player) {
        for (SandboxPlayer online : players) {
            if (online.player() == player) {
                return true;
            }
        }
        return false;
    }

    void removeEntity(Entity entity) {
        entities.remove(entity);
    }

    /** Every entity, players included, within the given distance on each axis of {@code center}. */
    List<Entity> entitiesNear(Location center, double x, double y, double z, Predicate<Entity> filter) {
        List<Entity> near = new ArrayList<>();
        for (Entity entity : allEntities()) {
            Location location = entity.getLocation();
            if (location.getWorld() == world && Math.abs(location.getX() - center.getX()) <= x
                    && Math.abs(location.getY() - center.getY()) <= y && Math.abs(location.getZ() - center.getZ()) <= z
                    && (filter == null || filter.test(entity))) {
                near.add(entity);
            }
        }
        return near;
    }

    Chunk chunkAt(int chunkX, int chunkZ) {
        long key = ((long) chunkX << 32) ^ (chunkZ & 0xffffffffL);
        return chunks.computeIfAbsent(key, ignored -> ProxyFacade.create(Chunk.class, new ChunkBacking(chunkX, chunkZ), gaps));
    }

    private List<Player> onlinePlayers() {
        List<Player> online = new ArrayList<>();
        for (SandboxPlayer player : players) {
            online.add(player.player());
        }
        return online;
    }

    private List<Entity> allEntities() {
        List<Entity> all = new ArrayList<>(entities);
        all.addAll(onlinePlayers());
        return all;
    }

    @SuppressWarnings("unchecked")
    private void register() {
        try {
            Field field = Bukkit.getServer().getClass().getDeclaredField("worlds");
            field.setAccessible(true);
            ((Map<String, Object>) field.get(Bukkit.getServer())).put(name.toLowerCase(Locale.ENGLISH), world);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Could not register the sandbox world with the server", failure);
        }
    }

    private Location spawnLocation() {
        return new Location(world, 0.5, 64, 0.5);
    }

    private static final class BlockPosition {

        private final int x;
        private final int y;
        private final int z;

        BlockPosition(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof BlockPosition)) {
                return false;
            }
            BlockPosition position = (BlockPosition) other;
            return x == position.x && y == position.y && z == position.z;
        }

        @Override
        public int hashCode() {
            return (x * 31 + y) * 31 + z;
        }
    }

    /** The {@code World} calls Slimefun and its addons make. */
    final class Backing {

        String getName() {
            return name;
        }

        UUID getUID() {
            return uid;
        }

        Block getBlockAt(int x, int y, int z) {
            return blockAt(x, y, z);
        }

        Block getBlockAt(Location location) {
            return blockAt(location.getBlockX(), location.getBlockY(), location.getBlockZ());
        }

        Chunk getChunkAt(int chunkX, int chunkZ) {
            return chunkAt(chunkX, chunkZ);
        }

        Chunk getChunkAt(Location location) {
            return chunkAt(location.getBlockX() >> 4, location.getBlockZ() >> 4);
        }

        Chunk getChunkAt(Block block) {
            return chunkAt(block.getX() >> 4, block.getZ() >> 4);
        }

        boolean isChunkLoaded(int chunkX, int chunkZ) {
            return true;
        }

        boolean isChunkLoaded(Chunk chunk) {
            return true;
        }

        boolean isChunkGenerated(int chunkX, int chunkZ) {
            return true;
        }

        boolean loadChunk(int chunkX, int chunkZ, boolean generate) {
            return true;
        }

        void loadChunk(int chunkX, int chunkZ) {}

        void loadChunk(Chunk chunk) {}

        Chunk[] getLoadedChunks() {
            return chunks.values().toArray(new Chunk[0]);
        }

        int getMaxHeight() {
            return MAX_HEIGHT;
        }

        int getMinHeight() {
            return 0;
        }

        int getSeaLevel() {
            return 63;
        }

        World.Environment getEnvironment() {
            return World.Environment.NORMAL;
        }

        Location getSpawnLocation() {
            return spawnLocation();
        }

        long getTime() {
            return 0;
        }

        long getFullTime() {
            return 0;
        }

        List<Player> getPlayers() {
            return onlinePlayers();
        }

        List<Entity> getEntities() {
            return allEntities();
        }

        Collection<Entity> getNearbyEntities(Location location, double x, double y, double z) {
            return entitiesNear(location, x, y, z, null);
        }

        @SuppressWarnings("unchecked")
        Collection<Entity> getNearbyEntities(Location location, double x, double y, double z, Predicate<?> filter) {
            return entitiesNear(location, x, y, z, (Predicate<Entity>) filter);
        }

        Entity spawnEntity(Location location, EntityType type) {
            Entity entity = SandboxEntity.spawn(SandboxWorld.this, type, location);
            entities.add(entity);
            return entity;
        }

        Entity spawn(Location location, Class<?> entityClass) {
            for (EntityType type : EntityType.values()) {
                if (type.getEntityClass() == entityClass) {
                    return spawnEntity(location, type);
                }
            }
            throw new IllegalArgumentException("No entity type spawns " + entityClass.getName());
        }

        Item dropItem(Location location, ItemStack item) {
            return drop(item);
        }

        Item dropItemNaturally(Location location, ItemStack item) {
            return drop(item);
        }

        int getHighestBlockYAt(int x, int z) {
            for (int y = MAX_HEIGHT - 1; y > 0; y--) {
                if (blocks.containsKey(new BlockPosition(x, y, z)) && blockAt(x, y, z).getType() != Material.AIR) {
                    return y;
                }
            }
            return 0;
        }

        /** No item entity exists to return; callers that need one show up as their own gap. */
        private Item drop(ItemStack item) {
            if (item != null && item.getType() != Material.AIR) {
                drops.add(item.clone());
            }
            return null;
        }

        @Override
        public String toString() {
            return "SandboxWorld{" + name + "}";
        }
    }

    final class ChunkBacking {

        private final int chunkX;
        private final int chunkZ;

        ChunkBacking(int chunkX, int chunkZ) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
        }

        World getWorld() {
            return world;
        }

        int getX() {
            return chunkX;
        }

        int getZ() {
            return chunkZ;
        }

        boolean isLoaded() {
            return true;
        }

        boolean load() {
            return true;
        }

        boolean load(boolean generate) {
            return true;
        }

        Block getBlock(int x, int y, int z) {
            return blockAt((chunkX << 4) + x, y, (chunkZ << 4) + z);
        }

        Entity[] getEntities() {
            return new Entity[0];
        }

        @Override
        public String toString() {
            return "SandboxChunk{" + name + " " + chunkX + "," + chunkZ + "}";
        }
    }
}
