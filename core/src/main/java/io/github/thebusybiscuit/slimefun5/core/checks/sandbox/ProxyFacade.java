package io.github.thebusybiscuit.slimefun5.core.checks.sandbox;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

/**
 * Implements a Bukkit interface, as the running version declares it, by routing each call to a backing
 * object's method of the same name and compatible parameters. A call nothing answers is recorded in a
 * {@link CheckGap} and returns the type's default ({@code 0}, {@code false} or {@code null}).
 *
 * @implNote Built on {@link Proxy} so core ships no bytecode library; only an interface {@code Proxy} refuses
 *           goes to {@link BundledAsmFacades}. Default interface methods reach the dispatcher too; one the
 *           backing does not answer runs its own body.
 */
public final class ProxyFacade {

    /** Calls whose only effect is on a client the sandbox does not have. */
    private static final Set<String> CLIENT_ONLY = new HashSet<>(Arrays.asList(
        "playSound", "stopSound", "playEffect", "spawnParticle", "playNote", "sendTitle", "resetTitle", "showTitle",
        "sendActionBar", "sendBlockChange", "sendSignChange", "updateInventory", "setPlayerListName",
        "sendPluginMessage", "showDemoScreen", "sendEquipmentChange"));

    private static final Map<Class<?>, Map<Method, Optional<Method>>> BACKING_METHODS = new ConcurrentHashMap<>();

    /** The result an {@link Answering} backing gives for a call it does not answer. */
    public static final Object UNANSWERED = new Object();

    private ProxyFacade() {}

    /** A backing that hands calls it does not answer itself on to another object. */
    public interface Delegating {

        /** The object that answers {@code method}, which must declare or inherit it, or {@code null}. */
        Object delegateFor(Method method);
    }

    /** A backing that answers whole families of calls itself, such as an entity's properties. */
    public interface Answering {

        /** The call's result, or {@link ProxyFacade#UNANSWERED} when this backing cannot answer it. */
        Object answer(Method method, Object[] arguments);
    }

    /**
     * @throws FacadeUnavailableException when the running server offers no way to build {@code api}, which is
     *                                    then recorded as a gap
     */
    @Nonnull
    public static <T> T create(@Nonnull Class<T> api, @Nonnull Object backing, @Nonnull CheckGap gaps) {
        return create(api, backing, gaps, BundledAsmFacades.onServer());
    }

    @Nonnull
    static <T> T create(@Nonnull Class<T> api, @Nonnull Object backing, @Nonnull CheckGap gaps, @Nonnull BundledAsmFacades generator) {
        Dispatcher dispatcher = new Dispatcher(api, backing, gaps);
        if (!BundledAsmFacades.hasReturnTypeClash(api.getMethods())) {
            return api.cast(Proxy.newProxyInstance(api.getClassLoader(), new Class<?>[] {api}, dispatcher));
        }
        if (!generator.isAvailable()) {
            gaps.record(api.getSimpleName() + " (no bundled ASM to build it with)");
        }
        return generator.create(api, dispatcher);
    }

    private static final class Dispatcher implements InvocationHandler {

        private final Class<?> api;
        private final Object backing;
        private final CheckGap gaps;

        Dispatcher(Class<?> api, Object backing, CheckGap gaps) {
            this.api = api;
            this.backing = backing;
            this.gaps = gaps;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] arguments) throws Throwable {
            Object[] actual = arguments == null ? new Object[0] : arguments;
            if (method.getDeclaringClass() == Object.class) {
                return objectMethod(proxy, method, actual);
            }
            try {
                return dispatch(proxy, method, actual);
            } catch (InvocationTargetException failure) {
                throw failure.getCause();
            }
        }

        private Object dispatch(Object proxy, Method method, Object[] arguments) throws Throwable {
            Optional<Method> answer = BACKING_METHODS
                .computeIfAbsent(backing.getClass(), ignored -> new ConcurrentHashMap<>())
                .computeIfAbsent(method, target -> answerFor(backing.getClass(), target));
            if (answer.isPresent()) {
                return answer.get().invoke(backing, arguments);
            }
            if (method.isDefault()) {
                return DefaultMethods.invoke(proxy, method, arguments);
            }
            Object delegate = backing instanceof Delegating ? ((Delegating) backing).delegateFor(method) : null;
            if (delegate != null) {
                method.setAccessible(true);
                return method.invoke(delegate, arguments);
            }
            if (backing instanceof Answering) {
                Object result = ((Answering) backing).answer(method, arguments);
                if (result != UNANSWERED) {
                    return result;
                }
            }
            if (!isClientOnly(method)) {
                gaps.record(signature(method));
            }
            return defaultValue(method.getReturnType());
        }

        private boolean isClientOnly(Method method) {
            return method.getReturnType() == void.class && CLIENT_ONLY.contains(method.getName());
        }

        private Object objectMethod(Object proxy, Method method, Object[] arguments) {
            switch (method.getName()) {
                case "equals":
                    return proxy == arguments[0];
                case "hashCode":
                    return System.identityHashCode(proxy);
                default:
                    return backing.toString();
            }
        }

        private String signature(Method method) {
            String parameters = Arrays.stream(method.getParameterTypes()).map(Class::getSimpleName).collect(Collectors.joining(", "));
            return api.getSimpleName() + "." + method.getName() + "(" + parameters + ")";
        }
    }

    /** The backing method answering {@code target}: same name and arity, every parameter accepting the target's. */
    static Optional<Method> answerFor(Class<?> backingClass, Method target) {
        Method assignable = null;
        for (Method candidate : backingClass.getDeclaredMethods()) {
            if (!candidate.getName().equals(target.getName()) || candidate.getParameterCount() != target.getParameterCount()
                    || Modifier.isStatic(candidate.getModifiers()) || candidate.isSynthetic()) {
                continue;
            }
            if (Arrays.equals(candidate.getParameterTypes(), target.getParameterTypes())) {
                candidate.setAccessible(true);
                return Optional.of(candidate);
            }
            if (assignable == null && accepts(candidate.getParameterTypes(), target.getParameterTypes())) {
                assignable = candidate;
            }
        }
        if (assignable != null) {
            assignable.setAccessible(true);
        }
        return Optional.ofNullable(assignable);
    }

    private static boolean accepts(Class<?>[] declared, Class<?>[] actual) {
        for (int i = 0; i < declared.length; i++) {
            if (!wrap(declared[i]).isAssignableFrom(wrap(actual[i]))) {
                return false;
            }
        }
        return true;
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive() || type == void.class) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == double.class) {
            return 0D;
        }
        if (type == float.class) {
            return 0F;
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        return type == short.class ? (Object) (short) 0 : (Object) 0;
    }

    private static Class<?> wrap(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == int.class) {
            return Integer.class;
        }
        if (type == boolean.class) {
            return Boolean.class;
        }
        if (type == long.class) {
            return Long.class;
        }
        if (type == double.class) {
            return Double.class;
        }
        if (type == float.class) {
            return Float.class;
        }
        if (type == byte.class) {
            return Byte.class;
        }
        if (type == short.class) {
            return Short.class;
        }
        return type == char.class ? Character.class : Void.class;
    }
}
