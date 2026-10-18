package io.github.thebusybiscuit.slimefun5.core.checks.sandbox;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Builds a facade class for an interface {@link java.lang.reflect.Proxy} refuses, with the ASM the server
 * bundles, every method handing its call to the same {@link InvocationHandler} a proxy would get.
 *
 * @implNote Exists for 1.8.x only, where the Bukkit API jar declares methods that differ only in their return
 *           type ({@code Damageable#getHealth} as both {@code int} and {@code double}, likewise
 *           {@code getMaxHealth}, {@code getLastDamage}, {@code Minecart#getDamage}); {@code Proxy} rejects
 *           those and Java source cannot implement them. Those servers ship Ebean's ASM 3 as
 *           {@code com.avaje.ebean.enhance.asm}, which is driven here by reflection so core neither compiles
 *           against nor bundles it. It writes Java 5 class files, which need no stack map frames.
 */
final class BundledAsmFacades {

    private static final String ASM_PACKAGE = "com.avaje.ebean.enhance.asm.";
    private static final String GENERATED_PACKAGE = "io/github/thebusybiscuit/slimefun5/core/checks/sandbox/generated/";
    private static final String HANDLER_FIELD = "handler";
    private static final String METHODS_FIELD = "methods";
    private static final String HANDLER = "java/lang/reflect/InvocationHandler";
    private static final String INVOKE_DESCRIPTOR = "(Ljava/lang/Object;Ljava/lang/reflect/Method;[Ljava/lang/Object;)Ljava/lang/Object;";
    private static final AtomicInteger CLASS_NUMBERS = new AtomicInteger();

    private static final int V1_5 = 49;
    private static final int COMPUTE_MAXS = 1;
    private static final int ACC_PUBLIC = 0x0001;
    private static final int ACC_STATIC = 0x0008;
    private static final int ACC_FINAL = 0x0010;
    private static final int ACC_SUPER = 0x0020;
    private static final int ILOAD = 21;
    private static final int LLOAD = 22;
    private static final int FLOAD = 23;
    private static final int DLOAD = 24;
    private static final int ALOAD = 25;
    private static final int AALOAD = 50;
    private static final int AASTORE = 83;
    private static final int POP = 87;
    private static final int DUP = 89;
    private static final int IRETURN = 172;
    private static final int LRETURN = 173;
    private static final int FRETURN = 174;
    private static final int DRETURN = 175;
    private static final int ARETURN = 176;
    private static final int RETURN = 177;
    private static final int GETSTATIC = 178;
    private static final int GETFIELD = 180;
    private static final int PUTFIELD = 181;
    private static final int INVOKEVIRTUAL = 182;
    private static final int INVOKESPECIAL = 183;
    private static final int INVOKESTATIC = 184;
    private static final int INVOKEINTERFACE = 185;
    private static final int ANEWARRAY = 189;
    private static final int CHECKCAST = 192;

    private static final Map<Class<?>, Character> PRIMITIVE_DESCRIPTORS = primitiveDescriptors();
    private static final BundledAsmFacades ON_SERVER = new BundledAsmFacades(org.bukkit.Bukkit.class.getClassLoader());

    private final ClassLoader asmLoader;
    private final Map<Class<?>, Constructor<?>> generated = new ConcurrentHashMap<>();

    BundledAsmFacades(@Nullable ClassLoader asmLoader) {
        this.asmLoader = asmLoader;
    }

    @Nonnull
    static BundledAsmFacades onServer() {
        return ON_SERVER;
    }

    /** Whether {@code methods} hold two with one name and parameter list whose return types no single type satisfies. */
    static boolean hasReturnTypeClash(@Nonnull Method[] methods) {
        Map<String, Set<Class<?>>> returnTypes = new HashMap<>();
        for (Method method : methods) {
            if (!Modifier.isStatic(method.getModifiers())) {
                returnTypes.computeIfAbsent(method.getName() + parameterDescriptor(method), key -> new HashSet<>()).add(method.getReturnType());
            }
        }
        for (Set<Class<?>> types : returnTypes.values()) {
            if (types.size() > 1 && !hasMostSpecific(types)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasMostSpecific(Set<Class<?>> types) {
        for (Class<?> candidate : types) {
            if (isAssignableToAll(candidate, types)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isAssignableToAll(Class<?> candidate, Set<Class<?>> types) {
        for (Class<?> type : types) {
            if (!returnsAs(candidate, type)) {
                return false;
            }
        }
        return true;
    }

    private static boolean returnsAs(Class<?> returned, Class<?> declared) {
        if (returned.isPrimitive() || declared.isPrimitive()) {
            return returned == declared;
        }
        return declared.isAssignableFrom(returned);
    }

    boolean isAvailable() {
        return asmClass("ClassWriter") != null;
    }

    @Nonnull
    <T> T create(@Nonnull Class<T> api, @Nonnull InvocationHandler handler) {
        if (!isAvailable()) {
            throw new FacadeUnavailableException(api.getSimpleName() + " needs the server's bundled ASM to be simulated, and this server has none");
        }
        try {
            return api.cast(generated.computeIfAbsent(api, this::generate).newInstance(handler));
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Could not create a " + api.getName() + " facade", failure);
        }
    }

    private Constructor<?> generate(Class<?> api) {
        try {
            String className = GENERATED_PACKAGE + api.getSimpleName() + "Facade" + CLASS_NUMBERS.incrementAndGet();
            List<Method> methods = methodsToImplement(api);
            byte[] bytes = classBytes(className, api, methods);
            Class<?> facadeClass = new DefiningLoader(api.getClassLoader(), ProxyFacade.class.getClassLoader()).define(className.replace('/', '.'), bytes);
            facadeClass.getField(METHODS_FIELD).set(null, methods.toArray(new Method[0]));
            return facadeClass.getConstructor(InvocationHandler.class);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Could not generate a " + api.getName() + " facade", failure);
        }
    }

    private static List<Method> methodsToImplement(Class<?> api) throws NoSuchMethodException {
        Map<String, Method> byDescriptor = new LinkedHashMap<>();
        for (Method method : api.getMethods()) {
            if (!Modifier.isStatic(method.getModifiers())) {
                byDescriptor.putIfAbsent(method.getName() + methodDescriptor(method), method);
            }
        }
        List<Method> objectMethods = new ArrayList<>();
        objectMethods.add(Object.class.getMethod("equals", Object.class));
        objectMethods.add(Object.class.getMethod("hashCode"));
        objectMethods.add(Object.class.getMethod("toString"));
        for (Method method : objectMethods) {
            byDescriptor.putIfAbsent(method.getName() + methodDescriptor(method), method);
        }
        return new ArrayList<>(byDescriptor.values());
    }

    private byte[] classBytes(String className, Class<?> api, List<Method> methods) throws ReflectiveOperationException {
        Object writer = asmClass("ClassWriter").getConstructor(int.class).newInstance(COMPUTE_MAXS);
        call(writer, "visit", V1_5, ACC_PUBLIC | ACC_SUPER, className, null, "java/lang/Object", new String[] {internalName(api)});
        endField(call(writer, "visitField", ACC_PUBLIC | ACC_STATIC, METHODS_FIELD, "[Ljava/lang/reflect/Method;", null, null));
        endField(call(writer, "visitField", ACC_PUBLIC | ACC_FINAL, HANDLER_FIELD, "L" + HANDLER + ";", null, null));
        writeConstructor(writer, className);
        for (int index = 0; index < methods.size(); index++) {
            writeMethod(writer, className, methods.get(index), index);
        }
        call(writer, "visitEnd");
        return (byte[]) call(writer, "toByteArray");
    }

    private void writeConstructor(Object writer, String className) throws ReflectiveOperationException {
        Object code = call(writer, "visitMethod", ACC_PUBLIC, "<init>", "(L" + HANDLER + ";)V", null, null);
        call(code, "visitCode");
        call(code, "visitVarInsn", ALOAD, 0);
        call(code, "visitMethodInsn", INVOKESPECIAL, "java/lang/Object", "<init>", "()V");
        call(code, "visitVarInsn", ALOAD, 0);
        call(code, "visitVarInsn", ALOAD, 1);
        call(code, "visitFieldInsn", PUTFIELD, className, HANDLER_FIELD, "L" + HANDLER + ";");
        call(code, "visitInsn", RETURN);
        call(code, "visitMaxs", 0, 0);
        call(code, "visitEnd");
    }

    /** {@code return (R) handler.invoke(this, methods[index], new Object[] {arguments...})}, unboxing a primitive result. */
    private void writeMethod(Object writer, String className, Method method, int index) throws ReflectiveOperationException {
        Object code = call(writer, "visitMethod", ACC_PUBLIC, method.getName(), methodDescriptor(method), null, null);
        call(code, "visitCode");
        call(code, "visitVarInsn", ALOAD, 0);
        call(code, "visitFieldInsn", GETFIELD, className, HANDLER_FIELD, "L" + HANDLER + ";");
        call(code, "visitVarInsn", ALOAD, 0);
        call(code, "visitFieldInsn", GETSTATIC, className, METHODS_FIELD, "[Ljava/lang/reflect/Method;");
        call(code, "visitLdcInsn", index);
        call(code, "visitInsn", AALOAD);
        writeArgumentArray(code, method.getParameterTypes());
        call(code, "visitMethodInsn", INVOKEINTERFACE, HANDLER, "invoke", INVOKE_DESCRIPTOR);
        writeReturn(code, method.getReturnType());
        call(code, "visitMaxs", 0, 0);
        call(code, "visitEnd");
    }

    private void writeArgumentArray(Object code, Class<?>[] parameters) throws ReflectiveOperationException {
        call(code, "visitLdcInsn", parameters.length);
        call(code, "visitTypeInsn", ANEWARRAY, "java/lang/Object");
        int slot = 1;
        for (int i = 0; i < parameters.length; i++) {
            Class<?> parameter = parameters[i];
            call(code, "visitInsn", DUP);
            call(code, "visitLdcInsn", i);
            call(code, "visitVarInsn", loadOpcode(parameter), slot);
            if (parameter.isPrimitive()) {
                Class<?> box = boxOf(parameter);
                call(code, "visitMethodInsn", INVOKESTATIC, internalName(box), "valueOf", "(" + descriptor(parameter) + ")" + descriptor(box));
            }
            call(code, "visitInsn", AASTORE);
            slot += parameter == long.class || parameter == double.class ? 2 : 1;
        }
    }

    /** A primitive result may come back as another number type: an {@code int getHealth()} answered by a {@code double}. */
    private void writeReturn(Object code, Class<?> returnType) throws ReflectiveOperationException {
        if (returnType == void.class) {
            call(code, "visitInsn", POP);
            call(code, "visitInsn", RETURN);
        } else if (returnType == boolean.class) {
            unbox(code, "java/lang/Boolean", "booleanValue", "()Z");
            call(code, "visitInsn", IRETURN);
        } else if (returnType == char.class) {
            unbox(code, "java/lang/Character", "charValue", "()C");
            call(code, "visitInsn", IRETURN);
        } else if (returnType.isPrimitive()) {
            unbox(code, "java/lang/Number", returnType.getName() + "Value", "()" + descriptor(returnType));
            call(code, "visitInsn", returnOpcode(returnType));
        } else {
            call(code, "visitTypeInsn", CHECKCAST, internalName(returnType));
            call(code, "visitInsn", ARETURN);
        }
    }

    private void unbox(Object code, String owner, String name, String methodDescriptor) throws ReflectiveOperationException {
        call(code, "visitTypeInsn", CHECKCAST, owner);
        call(code, "visitMethodInsn", INVOKEVIRTUAL, owner, name, methodDescriptor);
    }

    private static int loadOpcode(Class<?> type) {
        if (type == long.class) {
            return LLOAD;
        }
        if (type == float.class) {
            return FLOAD;
        }
        if (type == double.class) {
            return DLOAD;
        }
        return type.isPrimitive() ? ILOAD : ALOAD;
    }

    private static int returnOpcode(Class<?> type) {
        if (type == long.class) {
            return LRETURN;
        }
        if (type == float.class) {
            return FRETURN;
        }
        return type == double.class ? DRETURN : IRETURN;
    }

    private static Class<?> boxOf(Class<?> primitive) {
        if (primitive == int.class) {
            return Integer.class;
        }
        if (primitive == boolean.class) {
            return Boolean.class;
        }
        if (primitive == long.class) {
            return Long.class;
        }
        if (primitive == double.class) {
            return Double.class;
        }
        if (primitive == float.class) {
            return Float.class;
        }
        if (primitive == byte.class) {
            return Byte.class;
        }
        if (primitive == short.class) {
            return Short.class;
        }
        return Character.class;
    }

    private static String internalName(Class<?> type) {
        return type.getName().replace('.', '/');
    }

    private static String methodDescriptor(Method method) {
        return parameterDescriptor(method) + descriptor(method.getReturnType());
    }

    private static String parameterDescriptor(Method method) {
        StringBuilder builder = new StringBuilder("(");
        for (Class<?> parameter : method.getParameterTypes()) {
            builder.append(descriptor(parameter));
        }
        return builder.append(')').toString();
    }

    private static String descriptor(Class<?> type) {
        if (type.isArray()) {
            return internalName(type);
        }
        if (!type.isPrimitive()) {
            return "L" + internalName(type) + ";";
        }
        return String.valueOf(PRIMITIVE_DESCRIPTORS.get(type));
    }

    private static Map<Class<?>, Character> primitiveDescriptors() {
        Map<Class<?>, Character> descriptors = new HashMap<>();
        descriptors.put(void.class, 'V');
        descriptors.put(boolean.class, 'Z');
        descriptors.put(byte.class, 'B');
        descriptors.put(char.class, 'C');
        descriptors.put(short.class, 'S');
        descriptors.put(int.class, 'I');
        descriptors.put(long.class, 'J');
        descriptors.put(float.class, 'F');
        descriptors.put(double.class, 'D');
        return descriptors;
    }

    private static void endField(Object fieldVisitor) throws ReflectiveOperationException {
        call(fieldVisitor, "visitEnd");
    }

    private Class<?> asmClass(String simpleName) {
        if (asmLoader == null) {
            return null;
        }
        try {
            return Class.forName(ASM_PACKAGE + simpleName, false, asmLoader);
        } catch (ClassNotFoundException | LinkageError absent) {
            return null;
        }
    }

    /** Calls the one public method of that name and arity, as ASM 3 has no overloads among those used here. */
    private static Object call(Object target, String name, Object... arguments) throws ReflectiveOperationException {
        Method method = publicMethod(target.getClass(), name, arguments.length);
        method.setAccessible(true);
        return method.invoke(target, arguments);
    }

    private static Method publicMethod(Class<?> owner, String name, int arity) throws NoSuchMethodException {
        Collection<Method> candidates = new ArrayList<>();
        for (Method method : owner.getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == arity) {
                candidates.add(method);
            }
        }
        if (candidates.size() != 1) {
            throw new NoSuchMethodException(owner.getName() + "." + name + " with " + arity + " parameters: " + candidates);
        }
        return candidates.iterator().next();
    }

    /** Sees the interface's own loader (Bukkit, on a server) first, then core's. */
    private static final class DefiningLoader extends ClassLoader {

        private final ClassLoader core;

        DefiningLoader(ClassLoader apiLoader, ClassLoader core) {
            super(apiLoader);
            this.core = core;
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            return core.loadClass(name);
        }

        Class<?> define(String name, byte[] bytes) {
            return defineClass(name, bytes, 0, bytes.length);
        }
    }
}
