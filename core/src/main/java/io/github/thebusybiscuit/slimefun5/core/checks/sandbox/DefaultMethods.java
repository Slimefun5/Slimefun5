package io.github.thebusybiscuit.slimefun5.core.checks.sandbox;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Runs a default interface method's own body on a facade, a proxy or a generated class.
 *
 * @implNote Core is Java 8 bytecode but runs on 8 to 25: {@code InvocationHandler.invokeDefault} exists from
 *           16, {@code MethodHandles.privateLookupIn} from 9, and on 8 only the private {@code Lookup}
 *           constructor can unreflect a special call into an interface. Each is looked up reflectively.
 */
final class DefaultMethods {

    private static final Method INVOKE_DEFAULT = methodOrNull(InvocationHandler.class, "invokeDefault", Object.class, Method.class, Object[].class);
    private static final Method PRIVATE_LOOKUP_IN = methodOrNull(MethodHandles.class, "privateLookupIn", Class.class, MethodHandles.Lookup.class);

    private DefaultMethods() {}

    static Object invoke(Object proxy, Method method, Object[] arguments) throws Throwable {
        try {
            if (INVOKE_DEFAULT != null && Proxy.isProxyClass(proxy.getClass())) {
                return INVOKE_DEFAULT.invoke(null, proxy, method, arguments);
            }
            return lookupFor(method.getDeclaringClass())
                .unreflectSpecial(method, method.getDeclaringClass())
                .bindTo(proxy)
                .invokeWithArguments(arguments);
        } catch (InvocationTargetException failure) {
            throw failure.getCause();
        }
    }

    private static MethodHandles.Lookup lookupFor(Class<?> owner) throws ReflectiveOperationException {
        if (PRIVATE_LOOKUP_IN != null) {
            return (MethodHandles.Lookup) PRIVATE_LOOKUP_IN.invoke(null, owner, MethodHandles.lookup());
        }
        Constructor<MethodHandles.Lookup> constructor = MethodHandles.Lookup.class.getDeclaredConstructor(Class.class, int.class);
        constructor.setAccessible(true);
        return constructor.newInstance(owner, MethodHandles.Lookup.PRIVATE);
    }

    private static Method methodOrNull(Class<?> owner, String name, Class<?>... parameters) {
        try {
            return owner.getMethod(name, parameters);
        } catch (NoSuchMethodException absent) {
            return null;
        }
    }
}
