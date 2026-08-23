package io.github.thebusybiscuit.slimefun5.utils.compatibility;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

/**
 * Java-8 universal port: a tiny reflective method invoker for calling Bukkit/Paper APIs that do not
 * exist at the 1.8.8 compile floor but are present on newer servers.
 * <p>
 * It resolves a method by name, argument count, and assignable parameter types (so overloads with the
 * same arity are disambiguated), then invokes it. Crucially, the matched method is re-resolved against a
 * <em>public</em> supertype/interface before invocation: server objects are frequently instances of
 * non-public {@code org.bukkit.craftbukkit.*} implementation classes (e.g. {@code CraftMetaItem},
 * {@code CraftPersistentDataContainer}), and invoking a {@link Method} whose declaring class is non-public
 * throws {@link IllegalAccessException}. Resolving the same signature on a public interface (e.g.
 * {@code PersistentDataHolder}, {@code PersistentDataContainer}) yields an invocable handle.
 * <p>
 * Returns {@code null} when the method is absent (e.g. on legacy servers) or the call fails - callers
 * supply a sensible default for primitive returns. This preserves full behaviour on modern servers while
 * degrading gracefully on legacy ones.
 *
 * @author Slimefun (Java-8 port)
 */
public final class ReflectionCompat {

    private ReflectionCompat() {}

    /**
     * Resolved handles, cached per class and then per method name. Resolution scans {@code getMethods()}
     * (O(n)) plus a public-supertype walk on hot paths (per-tick, per-event), so it is cached. An empty
     * candidate array means "no such method", so absent APIs aren't re-scanned on legacy servers.
     *
     * @implNote Keyed by {@link ClassValue} plus the method name (a constant at every call site) rather
     *           than by a composed {@code class#name/argtypes} string. Building that string measured
     *           ~160ns per call and dominated {@link PdcCompat}, which every item comparison goes
     *           through and cargo drives thousands of times per tick. {@link ClassValue} also releases
     *           its entries with the class, where the old static map retained {@link Method} handles
     *           (and their classloaders) across a plugin reload.
     */
    private static final ClassValue<ConcurrentHashMap<String, Candidate[]>> RESOLVE_CACHE = new ClassValue<ConcurrentHashMap<String, Candidate[]>>() {

        @Override
        protected ConcurrentHashMap<String, Candidate[]> computeValue(Class<?> type) {
            return new ConcurrentHashMap<>();
        }
    };

    private static final Candidate[] NO_CANDIDATES = new Candidate[0];

    /**
     * A resolved, invocable overload paired with its parameter types, so matching an argument list does
     * not have to call {@link Method#getParameterTypes()} (which clones its array) on every invocation.
     */
    private static final class Candidate {

        private final Method method;
        private final Class<?>[] parameterTypes;

        private Candidate(Method method) {
            this.parameterTypes = method.getParameterTypes();
            this.method = invocable(method, this.parameterTypes);
        }

        private boolean accepts(Object[] args) {
            if (parameterTypes.length != args.length) {
                return false;
            }

            for (int i = 0; i < args.length; i++) {
                if (args[i] != null && !box(parameterTypes[i]).isAssignableFrom(args[i].getClass())) {
                    return false;
                }
            }

            return true;
        }
    }

    @Nullable
    public static Object invoke(@Nullable Object target, String name, Object... args) {
        if (target == null) {
            return null;
        }

        try {
            Method method = resolve(target.getClass(), name, args);

            if (method != null) {
                return method.invoke(target, args);
            }
        } catch (Throwable ignored) {
            // Method missing on this server version or invocation failed - fall through to null.
        }

        return null;
    }

    /**
     * Invokes a {@code public static} method on {@code clazz} by name + arity + assignable param types.
     * Returns {@code null} when the method is absent (e.g. on legacy servers) or the call fails.
     */
    @Nullable
    public static Object invokeStatic(Class<?> clazz, String name, Object... args) {
        try {
            Method method = resolve(clazz, name, args);

            if (method != null) {
                return method.invoke(null, args);
            }
        } catch (Throwable ignored) {
            // Method missing on this server version or invocation failed - fall through to null.
        }

        return null;
    }

    @Nullable
    private static Method resolve(Class<?> type, String name, Object[] args) {
        Candidate[] candidates = RESOLVE_CACHE.get(type).computeIfAbsent(name, methodName -> collect(type, methodName));

        for (Candidate candidate : candidates) {
            if (candidate.accepts(args)) {
                return candidate.method;
            }
        }

        return null;
    }

    private static Candidate[] collect(Class<?> type, String name) {
        List<Candidate> candidates = new ArrayList<>(2);

        for (Method method : type.getMethods()) {
            if (method.getName().equals(name)) {
                candidates.add(new Candidate(method));
            }
        }

        return candidates.isEmpty() ? NO_CANDIDATES : candidates.toArray(new Candidate[0]);
    }

    /**
     * Ensures the matched method can actually be invoked. If its declaring class is non-public (a
     * craftbukkit implementation type), the same signature is looked up on a public supertype/interface;
     * forcing access is the last resort.
     */
    private static Method invocable(Method method, Class<?>[] paramTypes) {
        if (Modifier.isPublic(method.getDeclaringClass().getModifiers())) {
            return method;
        }

        Method publicMethod = searchPublic(method.getDeclaringClass(), method.getName(), paramTypes);

        if (publicMethod != null) {
            return publicMethod;
        }

        try {
            method.setAccessible(true);
        } catch (Throwable ignored) {
            // Strong encapsulation may forbid this - invocation will then fail and the caller gets null.
        }

        return method;
    }

    @Nullable
    private static Method searchPublic(Class<?> type, String name, Class<?>[] paramTypes) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            if (Modifier.isPublic(current.getModifiers())) {
                try {
                    Method candidate = current.getMethod(name, paramTypes);

                    if (Modifier.isPublic(candidate.getDeclaringClass().getModifiers())) {
                        return candidate;
                    }
                } catch (NoSuchMethodException ignored) {
                    // Not declared here - keep walking.
                }
            }

            for (Class<?> iface : current.getInterfaces()) {
                Method candidate = searchPublic(iface, name, paramTypes);

                if (candidate != null) {
                    return candidate;
                }
            }
        }

        return null;
    }

    private static Class<?> box(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }

        if (type == int.class) {
            return Integer.class;
        } else if (type == boolean.class) {
            return Boolean.class;
        } else if (type == long.class) {
            return Long.class;
        } else if (type == double.class) {
            return Double.class;
        } else if (type == float.class) {
            return Float.class;
        } else if (type == short.class) {
            return Short.class;
        } else if (type == byte.class) {
            return Byte.class;
        } else if (type == char.class) {
            return Character.class;
        }

        return type;
    }
}
