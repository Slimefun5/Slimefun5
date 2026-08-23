package io.github.thebusybiscuit.slimefun5.utils.compatibility;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import io.github.thebusybiscuit.slimefun5.libraries.keys.NamespacedKey;

/**
 * Java-8 universal port: bridges Slimefun's own {@link NamespacedKey} to the real
 * {@code org.bukkit.NamespacedKey} at the (1.12+/1.14+) server-API boundaries (PDC, registries, recipe
 * lookups). Everything is reflective so no {@code org.bukkit.NamespacedKey} type reference exists in the
 * bytecode; on servers without it (1.8&ndash;1.11) the boundaries are never reached and this returns
 * {@code null}.
 *
 * @implNote Every lookup here is resolved once into a static field and every converted key is cached.
 *           This sits under {@code PdcCompat}, which sits under {@code SlimefunItem#getByItem}, which
 *           cargo calls thousands of times per tick; a per-call {@code Class.forName} measured ~500ns
 *           and dominated the whole item-comparison path.
 */
public final class BukkitKeys {

    private BukkitKeys() {}

    /**
     * Cache sentinel. {@link ConcurrentHashMap} cannot store {@code null}, so a legacy server (where
     * conversion always fails) would otherwise retry the reflection on every call.
     */
    private static final Object UNAVAILABLE = new Object();

    private static final Class<?> KEY_CLASS = resolveKeyClass();
    private static final Constructor<?> KEY_CONSTRUCTOR = resolveConstructor();
    private static final Method KEY_FACTORY = resolveFactory();

    private static final ConcurrentHashMap<NamespacedKey, Object> CONVERTED = new ConcurrentHashMap<>();

    /**
     * Converts an own {@link NamespacedKey} to a real {@code org.bukkit.NamespacedKey} instance.
     *
     * @return the real key as an {@link Object}, or {@code null} if the type is absent (legacy) or
     *         construction failed
     */
    @Nullable
    public static Object toBukkit(@Nullable NamespacedKey key) {
        if (key == null) {
            return null;
        }

        Object converted = CONVERTED.computeIfAbsent(key, BukkitKeys::convert);
        return converted == UNAVAILABLE ? null : converted;
    }

    private static Object convert(NamespacedKey key) {
        if (KEY_CONSTRUCTOR != null) {
            try {
                return KEY_CONSTRUCTOR.newInstance(key.getNamespace(), key.getKey());
            } catch (Throwable ignored) {
                // fall through to the factory
            }
        }

        if (KEY_FACTORY != null) {
            try {
                Object created = KEY_FACTORY.invoke(null, key.getNamespace() + ":" + key.getKey());

                if (created != null) {
                    return created;
                }
            } catch (Throwable ignored) {
                // fall through
            }
        }

        return UNAVAILABLE;
    }

    @Nullable
    private static Class<?> resolveKeyClass() {
        try {
            return Class.forName("org.bukkit.NamespacedKey");
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Nullable
    private static Constructor<?> resolveConstructor() {
        if (KEY_CLASS == null) {
            return null;
        }

        try {
            Constructor<?> ctor = KEY_CLASS.getDeclaredConstructor(String.class, String.class);
            ctor.setAccessible(true);
            return ctor;
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Nullable
    private static Method resolveFactory() {
        if (KEY_CLASS == null) {
            return null;
        }

        try {
            return KEY_CLASS.getMethod("fromString", String.class);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
