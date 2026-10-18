package io.github.thebusybiscuit.slimefun5.core.checks.sandbox;

import java.lang.reflect.Method;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.Plugin;

/**
 * One block of a {@link SandboxWorld}: a real {@link Material} and, from 1.13, real block data from the
 * server's own factory. There is no tile entity, so a block state is never a container.
 */
final class SandboxBlock {

    private final SandboxWorld world;
    private final int x;
    private final int y;
    private final int z;
    private final MetadataStore metadata = new MetadataStore();
    private Material type = Material.AIR;
    private byte data;
    /** {@code BlockData}, which the 1.8 API core compiles against does not have. */
    private Object blockData;
    private Block block;

    private SandboxBlock(SandboxWorld world, int x, int y, int z) {
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    static Block create(SandboxWorld world, int x, int y, int z) {
        SandboxBlock sandbox = new SandboxBlock(world, x, y, z);
        sandbox.block = ProxyFacade.create(Block.class, sandbox.new Backing(), world.gaps());
        return sandbox.block;
    }

    private Object blockData() {
        if (blockData == null) {
            try {
                Method create = Bukkit.class.getMethod("createBlockData", Material.class);
                blockData = create.invoke(null, type);
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Could not create block data for " + type, failure);
            }
        }
        return blockData;
    }

    final class Backing {

        World getWorld() {
            return world.world();
        }

        int getX() {
            return x;
        }

        int getY() {
            return y;
        }

        int getZ() {
            return z;
        }

        Location getLocation() {
            return new Location(world.world(), x, y, z);
        }

        Location getLocation(Location target) {
            if (target != null) {
                target.setWorld(world.world());
                target.setX(x);
                target.setY(y);
                target.setZ(z);
                target.setYaw(0);
                target.setPitch(0);
            }
            return target;
        }

        Chunk getChunk() {
            return world.chunkAt(x >> 4, z >> 4);
        }

        Material getType() {
            return type;
        }

        void setType(Material material) {
            setType(material, true);
        }

        void setType(Material material, boolean applyPhysics) {
            type = material;
            data = 0;
            blockData = null;
        }

        byte getData() {
            return data;
        }

        void setData(byte value) {
            data = value;
        }

        void setData(byte value, boolean applyPhysics) {
            data = value;
        }

        Object getBlockData() {
            return blockData();
        }

        void setBlockData(Object value) {
            setBlockData(value, true);
        }

        void setBlockData(Object value, boolean applyPhysics) {
            try {
                type = (Material) value.getClass().getMethod("getMaterial").invoke(value);
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Block data without a material: " + value, failure);
            }
            blockData = value;
        }

        Block getRelative(BlockFace face) {
            return getRelative(face, 1);
        }

        Block getRelative(BlockFace face, int distance) {
            return world.blockAt(x + face.getModX() * distance, y + face.getModY() * distance, z + face.getModZ() * distance);
        }

        Block getRelative(int modX, int modY, int modZ) {
            return world.blockAt(x + modX, y + modY, z + modZ);
        }

        boolean isEmpty() {
            return type == Material.AIR;
        }

        boolean isLiquid() {
            return type.name().contains("WATER") || type.name().contains("LAVA");
        }

        BlockState getState() {
            return ProxyFacade.create(BlockState.class, new StateBacking(type), world.gaps());
        }

        BlockState getState(boolean useSnapshot) {
            return getState();
        }

        int getBlockPower() {
            return 0;
        }

        boolean isBlockPowered() {
            return false;
        }

        boolean isBlockIndirectlyPowered() {
            return false;
        }

        byte getLightLevel() {
            return 15;
        }

        byte getLightFromSky() {
            return 15;
        }

        byte getLightFromBlocks() {
            return 0;
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
            return "SandboxBlock{" + world.world().getName() + " " + x + "," + y + "," + z + " " + type + "}";
        }
    }

    /** A snapshot: it keeps the type the block had when the state was taken, as Bukkit's states do. */
    final class StateBacking {

        private final Material snapshotType;

        StateBacking(Material snapshotType) {
            this.snapshotType = snapshotType;
        }

        Block getBlock() {
            return block;
        }

        Material getType() {
            return snapshotType;
        }

        Location getLocation() {
            return new Location(world.world(), x, y, z);
        }

        World getWorld() {
            return world.world();
        }

        int getX() {
            return x;
        }

        int getY() {
            return y;
        }

        int getZ() {
            return z;
        }

        Object getBlockData() {
            return blockData();
        }

        boolean isPlaced() {
            return true;
        }

        boolean update() {
            return true;
        }

        boolean update(boolean force) {
            return true;
        }

        boolean update(boolean force, boolean applyPhysics) {
            return true;
        }
    }
}
