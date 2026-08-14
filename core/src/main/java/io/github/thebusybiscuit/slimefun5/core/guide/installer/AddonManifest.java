package io.github.thebusybiscuit.slimefun5.core.guide.installer;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.cryptomorin.xseries.XMaterial;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Reads the addon manifest (a Gson snapshot of {@code Slimefun5/manifest}'s {@code addons.json})
 * and maps it to {@link AddonCatalog.Entry} instances.
 */
final class AddonManifest {

    private AddonManifest() {}

    static List<AddonCatalog.Entry> loadBundled() {
        try (InputStream in = AddonManifest.class.getResourceAsStream("/addons.json")) {
            if (in == null) {
                return new ArrayList<>();
            }

            StringBuilder sb = new StringBuilder();
            InputStreamReader r = new InputStreamReader(in, StandardCharsets.UTF_8);
            char[] buf = new char[4096];
            int n;

            while ((n = r.read(buf)) != -1) {
                sb.append(buf, 0, n);
            }

            return parse(sb.toString());
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    @SuppressWarnings("deprecation")
    static List<AddonCatalog.Entry> parse(String json) {
        JsonObject root = new JsonParser().parse(json).getAsJsonObject();
        List<AddonCatalog.Entry> out = new ArrayList<>();

        if (root.has("core") && root.get("core").isJsonObject()) {
            out.add(toEntry(root.getAsJsonObject("core"), "core"));
        }

        addAll(out, root, "libraries", "library");
        addAll(out, root, "addons", "addon");
        return out;
    }

    private static void addAll(List<AddonCatalog.Entry> out, JsonObject root, String key, String kind) {
        if (root.has(key) && root.get(key).isJsonArray()) {
            for (JsonElement el : root.getAsJsonArray(key)) {
                out.add(toEntry(el.getAsJsonObject(), kind));
            }
        }
    }

    private static AddonCatalog.Entry toEntry(JsonObject o, String kind) {
        String id = o.get("id").getAsString();
        String repo = shortRepo(o.get("repo").getAsString());
        String name = o.has("name") && !o.get("name").isJsonNull() ? o.get("name").getAsString() : repo;
        String pluginName = o.has("pluginName") && !o.get("pluginName").isJsonNull() ? o.get("pluginName").getAsString() : repo;
        List<String> deps = new ArrayList<>();

        if (o.has("dependencies") && o.get("dependencies").isJsonArray()) {
            for (JsonElement d : o.getAsJsonArray("dependencies")) {
                deps.add(d.getAsString());
            }
        }

        boolean core = "core".equals(kind);
        boolean library = "library".equals(kind);
        XMaterial icon = core ? XMaterial.BLAZE_POWDER : library ? XMaterial.BOOK : XMaterial.NETHER_STAR;
        return new AddonCatalog.Entry(id, repo, name, icon, deps, core, library, pluginName);
    }

    private static String shortRepo(String ownerRepo) {
        int slash = ownerRepo.indexOf('/');
        return slash >= 0 ? ownerRepo.substring(slash + 1) : ownerRepo;
    }
}
