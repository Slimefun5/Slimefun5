package io.github.thebusybiscuit.slimefun5.utils.compatibility.packet;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

/**
 * A record class's component accessors and canonical constructor, resolved once per class.
 *
 * @implNote Core compiles for Java 8, so {@code Class#isRecord} and {@code Class#getRecordComponents} are
 *           reached by reflection; on a Java 8 to 15 runtime nothing is a record and every class resolves to
 *           {@link #NOT_A_RECORD}.
 */
final class RecordShape {

    private static final RecordShape NOT_A_RECORD = new RecordShape(new Method[0], null);
    private static final Map<Class<?>, RecordShape> SHAPES = new ConcurrentHashMap<>();

    private final Method[] accessors;
    @Nullable
    private final Constructor<?> canonical;

    private RecordShape(Method[] accessors, @Nullable Constructor<?> canonical) {
        this.accessors = accessors;
        this.canonical = canonical;
    }

    /** The shape of {@code type}, or {@code null} when it is not a record or its shape cannot be read. */
    @Nullable
    static RecordShape of(Class<?> type) {
        RecordShape shape = SHAPES.computeIfAbsent(type, RecordShape::resolve);
        return shape == NOT_A_RECORD ? null : shape;
    }

    private static RecordShape resolve(Class<?> type) {
        try {
            if (!Boolean.TRUE.equals(Class.class.getMethod("isRecord").invoke(type))) {
                return NOT_A_RECORD;
            }
            Object[] components = (Object[]) Class.class.getMethod("getRecordComponents").invoke(type);
            Method[] accessors = new Method[components.length];
            Class<?>[] types = new Class<?>[components.length];
            for (int i = 0; i < components.length; i++) {
                Class<?> componentClass = components[i].getClass();
                accessors[i] = (Method) componentClass.getMethod("getAccessor").invoke(components[i]);
                accessors[i].setAccessible(true);
                types[i] = (Class<?>) componentClass.getMethod("getType").invoke(components[i]);
            }
            Constructor<?> canonical = type.getDeclaredConstructor(types);
            canonical.setAccessible(true);
            return new RecordShape(accessors, canonical);
        } catch (Throwable unreadable) {
            return NOT_A_RECORD;
        }
    }

    Object[] components(Object record) throws ReflectiveOperationException {
        Object[] values = new Object[accessors.length];
        for (int i = 0; i < accessors.length; i++) {
            values[i] = accessors[i].invoke(record);
        }
        return values;
    }

    Object construct(Object[] components) throws ReflectiveOperationException {
        return canonical.newInstance(components);
    }
}
