package io.github.thebusybiscuit.slimefun5.utils.compatibility;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * On 1.8 {@code CraftMetaItem.clone()} copies lore and enchantments but not {@code unhandledTags}, so a clone
 * and its source share one map. Each meta here shares its map the same way.
 */
class NbtItemCompatSharedTagsTest {

    private static final String KEY = "slimefun:slimefun_item";

    abstract static class ShallowCloneMeta implements ItemMeta {
        private final Map<String, Object> unhandledTags = new HashMap<>();

        @Override
        public abstract ShallowCloneMeta clone();
    }

    static final class Tag {
        private final String data;

        Tag(String data) {
            this.data = data;
        }
    }

    @Test
    void removingTheIdFromACloneLeavesItsSourceTheId() throws ReflectiveOperationException {
        Map<String, Object> shared = new HashMap<>();
        shared.put(KEY, new Tag("URANIUM"));
        ItemMeta source = metaSharing(shared);
        ItemMeta clone = metaSharing(shared);

        Assertions.assertTrue(NbtItemCompat.remove(clone, KEY));

        Assertions.assertNull(NbtItemCompat.getString(clone, KEY));
        Assertions.assertEquals("URANIUM", NbtItemCompat.getString(source, KEY));
    }

    @Test
    void removingAMissingIdChangesNothing() throws ReflectiveOperationException {
        Map<String, Object> shared = new HashMap<>();
        ItemMeta clone = metaSharing(shared);

        Assertions.assertFalse(NbtItemCompat.remove(clone, KEY));
        Assertions.assertSame(shared, tagsOf(clone));
    }

    private static ItemMeta metaSharing(Map<String, Object> tags) throws ReflectiveOperationException {
        ShallowCloneMeta meta = Mockito.mock(ShallowCloneMeta.class);
        Field field = ShallowCloneMeta.class.getDeclaredField("unhandledTags");
        field.setAccessible(true);
        field.set(meta, tags);
        return meta;
    }

    private static Object tagsOf(ItemMeta meta) throws ReflectiveOperationException {
        Field field = ShallowCloneMeta.class.getDeclaredField("unhandledTags");
        field.setAccessible(true);
        return field.get(meta);
    }
}
