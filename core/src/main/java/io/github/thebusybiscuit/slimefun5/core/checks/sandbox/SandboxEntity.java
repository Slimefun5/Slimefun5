package io.github.thebusybiscuit.slimefun5.core.checks.sandbox;

import java.beans.Introspector;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.Plugin;

/**
 * A non-player entity standing in a {@link SandboxWorld}, of whichever entity interface its type names.
 * Its settable properties ({@code setVisible}/{@code isVisible}, {@code setCustomName}/{@code getCustomName})
 * read back what was set; reading one that was never set is a {@link CheckGap}, never a guessed value.
 */
final class SandboxEntity {

    private static final AtomicInteger ENTITY_IDS = new AtomicInteger(200_000);
    private static final Set<UUID> ISSUED = ConcurrentHashMap.newKeySet();

    private final SandboxWorld world;
    private final EntityType type;
    private final UUID uniqueId = UUID.randomUUID();
    private final int entityId = ENTITY_IDS.incrementAndGet();
    private final MetadataStore metadata = new MetadataStore();
    private final Map<String, Object> properties = new HashMap<>();
    private Location location;
    private boolean removed;
    private Object persistentData;
    private Entity entity;

    private SandboxEntity(SandboxWorld world, EntityType type, Location location) {
        this.world = world;
        this.type = type;
        this.location = location.clone();
        ISSUED.add(uniqueId);
    }

    static boolean isSandboxEntity(UUID id) {
        return ISSUED.contains(id);
    }

    static void forgetIssued() {
        ISSUED.clear();
    }

    static Entity spawn(SandboxWorld world, EntityType type, Location location) {
        Class<? extends Entity> entityClass = type.getEntityClass();
        if (entityClass == null) {
            throw new IllegalArgumentException("Entity type " + type + " has no entity class to spawn");
        }
        SandboxEntity sandbox = new SandboxEntity(world, type, location);
        sandbox.entity = ProxyFacade.create(entityClass, sandbox.new Backing(), world.gaps());
        return sandbox.entity;
    }

    final class Backing implements ProxyFacade.Answering {

        @Override
        public Object answer(Method method, Object[] arguments) {
            String name = method.getName();
            if (arguments.length == 1 && name.startsWith("set") && method.getReturnType() == void.class) {
                properties.put(property(name, 3), arguments[0]);
                return null;
            }
            if (arguments.length == 0) {
                int prefix = name.startsWith("get") || name.startsWith("has") ? 3 : name.startsWith("is") ? 2 : 0;
                if (prefix > 0 && properties.containsKey(property(name, prefix))) {
                    return properties.get(property(name, prefix));
                }
            }
            return ProxyFacade.UNANSWERED;
        }

        private String property(String methodName, int prefix) {
            return Introspector.decapitalize(methodName.substring(prefix));
        }

        /** A real entity's custom name is {@code null} until one is set. */
        String getCustomName() {
            return (String) properties.get("customName");
        }

        UUID getUniqueId() {
            return uniqueId;
        }

        int getEntityId() {
            return entityId;
        }

        EntityType getType() {
            return type;
        }

        Server getServer() {
            return Bukkit.getServer();
        }

        World getWorld() {
            return world.world();
        }

        Location getLocation() {
            return location.clone();
        }

        Location getLocation(Location target) {
            if (target != null) {
                target.setWorld(location.getWorld());
                target.setX(location.getX());
                target.setY(location.getY());
                target.setZ(location.getZ());
            }
            return target;
        }

        boolean teleport(Location destination) {
            location = destination.clone();
            return true;
        }

        boolean teleport(Entity destination) {
            return teleport(destination.getLocation());
        }

        void remove() {
            removed = true;
            world.removeEntity(entity);
        }

        boolean isValid() {
            return !removed;
        }

        boolean isDead() {
            return removed;
        }

        List<Entity> getNearbyEntities(double x, double y, double z) {
            List<Entity> nearby = world.entitiesNear(location, x, y, z, null);
            nearby.remove(entity);
            return nearby;
        }

        Object getPersistentDataContainer() {
            if (persistentData == null) {
                persistentData = PersistentData.newContainer();
            }
            return persistentData;
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
            return "SandboxEntity{" + type + " at " + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ() + "}";
        }
    }
}
