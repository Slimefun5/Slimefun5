package io.github.thebusybiscuit.slimefun5.core.checks.sandbox;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

class FacadeTest {

    interface Greeter {

        String greet(String name);

        int count();

        boolean ready();

        Object thing();

        default String greetTwice(String name) {
            return greet(name) + " " + greet(name);
        }
    }

    static final class GreeterBacking {

        String greet(String name) {
            return "hello " + name;
        }

        @Override
        public String toString() {
            return "GreeterBacking";
        }
    }

    @BeforeAll
    static void mock() {
        MockBukkit.mock();
    }

    @AfterAll
    static void unmock() {
        MockBukkit.unmock();
    }

    @Test
    void facadeAnswersImplementedMethod() {
        CheckGap gaps = new CheckGap();
        Greeter greeter = ProxyFacade.create(Greeter.class, new GreeterBacking(), gaps);

        Assertions.assertEquals("hello steve", greeter.greet("steve"));
        Assertions.assertEquals("hello steve hello steve", greeter.greetTwice("steve"));
        Assertions.assertEquals("GreeterBacking", greeter.toString());
        Assertions.assertEquals(greeter, greeter);
        Assertions.assertNotEquals(greeter, ProxyFacade.create(Greeter.class, new GreeterBacking(), gaps));
        Assertions.assertEquals(System.identityHashCode(greeter), greeter.hashCode());
        Assertions.assertTrue(gaps.recorded().isEmpty());
    }

    @Test
    void unansweredCallRecordsGapAndReturnsDefault() {
        CheckGap gaps = new CheckGap();
        Greeter greeter = ProxyFacade.create(Greeter.class, new GreeterBacking(), gaps);

        Assertions.assertEquals(0, greeter.count());
        Assertions.assertFalse(greeter.ready());
        Assertions.assertNull(greeter.thing());
        greeter.count();

        Assertions.assertEquals(java.util.Arrays.asList("Greeter.count()", "Greeter.ready()", "Greeter.thing()"), gaps.recorded());
    }

    @Test
    void inventoryViewWorksOnBothShapes() {
        CheckGap gaps = new CheckGap();
        SandboxWorld world = SandboxWorld.create("sandbox_test", gaps);
        Player player = world.join("viewer", "de_de").player();
        Inventory top = Bukkit.createInventory(null, 9, "Ofen");

        InventoryView view = SandboxInventoryView.create(world.players().get(0), top, InventoryType.CHEST, "Ofen");

        Assertions.assertSame(top, view.getTopInventory());
        Assertions.assertSame(player.getInventory(), view.getBottomInventory());
        Assertions.assertSame(player, view.getPlayer());
        Assertions.assertEquals("Ofen", view.getTitle());
        view.setTitle("Elektrischer Ofen");
        Assertions.assertEquals("Elektrischer Ofen", view.getTitle());
        Assertions.assertEquals("Ofen", view.getOriginalTitle());
        Assertions.assertEquals(9 + 36, view.countSlots());
        Assertions.assertTrue(gaps.recorded().isEmpty(), () -> "gaps: " + gaps.recorded());
    }

    @Test
    void viewShapeIsReadFromTheRunningApi() {
        Assertions.assertEquals(SandboxInventoryView.Shape.INTERFACE, SandboxInventoryView.Shape.of(InventoryView.class));
        Assertions.assertEquals(SandboxInventoryView.Shape.FINAL_TITLE, SandboxInventoryView.Shape.of(FinalTitleView.class));
        Assertions.assertEquals(SandboxInventoryView.Shape.ABSTRACT_TITLE, SandboxInventoryView.Shape.of(AbstractTitleView.class));
        Assertions.assertEquals(SandboxInventoryView.Shape.RETITLABLE, SandboxInventoryView.Shape.of(RetitlableTitleView.class));
    }

    abstract static class FinalTitleView {

        public final String getTitle() {
            return "";
        }
    }

    abstract static class AbstractTitleView {

        public abstract String getTitle();
    }

    abstract static class RetitlableTitleView {

        public abstract String getTitle();

        public abstract void setTitle(String title);
    }

    public interface IntValue {

        int value();
    }

    public interface DoubleValue {

        double value();
    }

    public interface NumberValue {

        Number value();
    }

    public interface IntegerValue {

        Integer value();
    }

    @Test
    void returnTypeClashIsDetected() {
        Assertions.assertTrue(BundledAsmFacades.hasReturnTypeClash(concat(IntValue.class, DoubleValue.class)));
        Assertions.assertFalse(BundledAsmFacades.hasReturnTypeClash(concat(NumberValue.class, IntegerValue.class)));
        Assertions.assertFalse(BundledAsmFacades.hasReturnTypeClash(Greeter.class.getMethods()));
        Assertions.assertTrue(BundledAsmFacades.hasReturnTypeClash(legacyDamageable().getMethods()));
    }

    static final class HealthBacking {

        double getHealth() {
            return 20.5;
        }

        String getName() {
            return "zombie";
        }

        @Override
        public String toString() {
            return "HealthBacking";
        }
    }

    @Test
    void generatedFacadeAnswersBothReturnTypesOfAClash() throws Exception {
        Class<?> damageable = legacyDamageable();
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> java.lang.reflect.Proxy.newProxyInstance(damageable.getClassLoader(), new Class<?>[] {damageable}, (proxy, method, arguments) -> null));
        CheckGap gaps = new CheckGap();

        Object facade = ProxyFacade.create(damageable, new HealthBacking(), gaps, new BundledAsmFacades(FacadeTest.class.getClassLoader()));

        Assertions.assertEquals(20, healthOf(damageable, facade, int.class));
        Assertions.assertEquals(20.5, healthOf(damageable, facade, double.class));
        Assertions.assertEquals("zombie", damageable.getMethod("getName").invoke(facade));
        damageable.getMethod("setHealth", double.class, boolean.class, long.class).invoke(facade, 3.0, true, 7L);
        Assertions.assertEquals("HealthBacking", facade.toString());
        Assertions.assertEquals(System.identityHashCode(facade), facade.hashCode());
        Assertions.assertEquals(facade, facade);
        Assertions.assertEquals(java.util.Collections.singletonList("Damageable.setHealth(double, boolean, long)"), gaps.recorded());
    }

    @Test
    void missingBundledAsmRecordsGapAndMakesTheFacadeUnavailable() throws Exception {
        Class<?> damageable = legacyDamageable();
        CheckGap gaps = new CheckGap();

        Assertions.assertThrows(FacadeUnavailableException.class,
            () -> ProxyFacade.create(damageable, new HealthBacking(), gaps, new BundledAsmFacades(null)));
        Assertions.assertEquals(java.util.Collections.singletonList("Damageable (no bundled ASM to build it with)"), gaps.recorded());
    }

    private static Object healthOf(Class<?> damageable, Object facade, Class<?> returnType) throws Exception {
        for (java.lang.reflect.Method method : damageable.getMethods()) {
            if (method.getName().equals("getHealth") && method.getReturnType() == returnType) {
                return method.invoke(facade);
            }
        }
        throw new AssertionError("no getHealth returning " + returnType);
    }

    private static java.lang.reflect.Method[] concat(Class<?> first, Class<?> second) {
        java.util.List<java.lang.reflect.Method> methods = new java.util.ArrayList<>(java.util.Arrays.asList(first.getMethods()));
        methods.addAll(java.util.Arrays.asList(second.getMethods()));
        return methods.toArray(new java.lang.reflect.Method[0]);
    }

    /** An interface shaped like 1.8.8's {@code Damageable}: {@code getHealth} returning both {@code int} and {@code double}. */
    private static Class<?> legacyDamageable() {
        com.avaje.ebean.enhance.asm.ClassWriter writer = new com.avaje.ebean.enhance.asm.ClassWriter(0);
        int abstractMethod = com.avaje.ebean.enhance.asm.Opcodes.ACC_PUBLIC | com.avaje.ebean.enhance.asm.Opcodes.ACC_ABSTRACT;
        writer.visit(com.avaje.ebean.enhance.asm.Opcodes.V1_5, abstractMethod | com.avaje.ebean.enhance.asm.Opcodes.ACC_INTERFACE,
            "sandboxtest/Damageable", null, "java/lang/Object", null);
        writer.visitMethod(abstractMethod, "getHealth", "()D", null, null).visitEnd();
        writer.visitMethod(abstractMethod, "getHealth", "()I", null, null).visitEnd();
        writer.visitMethod(abstractMethod, "getName", "()Ljava/lang/String;", null, null).visitEnd();
        writer.visitMethod(abstractMethod, "setHealth", "(DZJ)V", null, null).visitEnd();
        writer.visitEnd();
        byte[] bytes = writer.toByteArray();
        return new ClassLoader(FacadeTest.class.getClassLoader()) {
            Class<?> define() {
                return defineClass("sandboxtest.Damageable", bytes, 0, bytes.length);
            }
        }.define();
    }

    @Test
    void playerFacadeAnswersWhatTheScenariosUse() {
        CheckGap gaps = new CheckGap();
        SandboxWorld world = SandboxWorld.create("sandbox_players", gaps);
        SandboxPlayer sandboxPlayer = world.join("clicker", "en_us");
        Player player = sandboxPlayer.player();

        player.sendMessage("hi");
        player.getInventory().setItem(9, new org.bukkit.inventory.ItemStack(org.bukkit.Material.IRON_INGOT, 8));

        Assertions.assertEquals("clicker", player.getName());
        Assertions.assertEquals("en_us", player.getLocale());
        Assertions.assertTrue(player.isOnline());
        Assertions.assertEquals(java.util.Collections.singletonList("hi"), sandboxPlayer.messages());
        Assertions.assertEquals(8, player.getInventory().getItem(9).getAmount());
        Assertions.assertEquals(org.bukkit.Material.AIR, player.getItemOnCursor().getType());
        Assertions.assertSame(world.world(), player.getWorld());
        Assertions.assertSame(world.blockAt(1, 2, 3), world.world().getBlockAt(1, 2, 3));
        Assertions.assertTrue(gaps.recorded().isEmpty(), () -> "gaps: " + gaps.recorded());
    }
}
