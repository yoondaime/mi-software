package pe.chef.asistente;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RecipeStore {
    public static class Ingredient {
        public String name;
        public double quantity = Double.NaN;
        public String unit = "";
        public String originalMeasure = "";
    }

    public static class Step {
        public String title;
        public String instruction;
        public String duration;
    }

    public static class Recipe {
        public String key;
        public String name;
        public int baseServings = 4;
        public boolean external = false;
        public String sourceLabel = "Chef Asistente";
        public List<String> aliases = new ArrayList<>();
        public List<Ingredient> ingredients = new ArrayList<>();
        public List<String> preparation = new ArrayList<>();
        public List<Step> steps = new ArrayList<>();
    }

    public interface UpdateCallback {
        void onResult(boolean online, boolean updated, String message);
    }

    private static final String REMOTE_BASE = "https://raw.githubusercontent.com/yoondaime/mi-software/main/data/recetas.json";
    private static final String REMOTE_PACK2 = "https://raw.githubusercontent.com/yoondaime/mi-software/main/data/recetas_pack2.json";
    private final Context context;
    private final Map<String, Recipe> recipes = new LinkedHashMap<>();
    private int catalogVersion = 0;

    public RecipeStore(Context context) { this.context = context.getApplicationContext(); }

    public synchronized int getCatalogVersion() { return catalogVersion; }
    public synchronized int size() { return recipes.size(); }

    public synchronized void loadBestLocalCatalog() throws Exception {
        recipes.clear(); catalogVersion = 0;
        loadAssetIfExists("recetas.json");
        loadAssetIfExists("recetas_pack2.json");
        loadFileIfExists("recetas_online.json");
        loadFileIfExists("recetas_pack2_online.json");
        loadFileIfExists("external_recipes.json");
    }

    public synchronized Recipe find(String raw) {
        String query = MainActivity.norm(raw);
        Recipe best = null; int bestLength = 0;
        for (Recipe recipe : recipes.values()) {
            List<String> candidates = new ArrayList<>();
            candidates.add(recipe.key); candidates.add(recipe.name); candidates.addAll(recipe.aliases);
            for (String candidate : candidates) {
                String normalized = MainActivity.norm(candidate);
                if (!normalized.isEmpty() && query.contains(normalized) && normalized.length() > bestLength) {
                    best = recipe; bestLength = normalized.length();
                }
            }
        }
        return best;
    }

    public void updateFromInternet(UpdateCallback callback) {
        new Thread(() -> {
            boolean any = false;
            try {
                String base = download(REMOTE_BASE);
                if (base != null) { writeFile("recetas_online.json", base); any = true; }
            } catch (Exception ignored) {}
            try {
                String pack = download(REMOTE_PACK2);
                if (pack != null) { writeFile("recetas_pack2_online.json", pack); any = true; }
            } catch (Exception ignored) {}
            if (any) {
                try { loadBestLocalCatalog(); } catch (Exception ignored) {}
                post(callback, true, true, "Con internet · recetas sincronizadas · " + size() + " recetas");
            } else {
                post(callback, false, false, "Sin internet · usando " + size() + " recetas guardadas");
            }
        }).start();
    }

    public synchronized void saveExternal(Recipe recipe) {
        try {
            JSONObject root;
            File file = new File(context.getFilesDir(), "external_recipes.json");
            if (file.exists()) root = new JSONObject(readAll(new FileInputStream(file)));
            else { root = new JSONObject(); root.put("version", 1); root.put("recipes", new JSONArray()); }
            JSONArray array = root.optJSONArray("recipes");
            if (array == null) { array = new JSONArray(); root.put("recipes", array); }
            for (int i = 0; i < array.length(); i++) {
                if (MainActivity.norm(array.getJSONObject(i).optString("key")).equals(MainActivity.norm(recipe.key))) return;
            }
            array.put(toJson(recipe));
            writeFile("external_recipes.json", root.toString());
            mergeCatalog(root.toString());
        } catch (Exception ignored) {}
    }

    private void loadAssetIfExists(String name) {
        try (InputStream in = context.getAssets().open(name)) { mergeCatalog(readAll(in)); } catch (Exception ignored) {}
    }

    private void loadFileIfExists(String name) {
        try (InputStream in = new FileInputStream(new File(context.getFilesDir(), name))) { mergeCatalog(readAll(in)); } catch (Exception ignored) {}
    }

    private synchronized void mergeCatalog(String json) throws Exception {
        JSONObject root = new JSONObject(json);
        catalogVersion = Math.max(catalogVersion, root.optInt("version", 0));
        JSONArray array = root.optJSONArray("recipes");
        if (array == null) return;
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.getJSONObject(i);
            Recipe recipe = new Recipe();
            recipe.key = item.optString("key", item.optString("name", "receta"));
            recipe.name = item.optString("name", recipe.key);
            recipe.baseServings = Math.max(1, item.optInt("baseServings", 4));
            recipe.external = item.optBoolean("external", false);
            recipe.sourceLabel = item.optString("sourceLabel", recipe.external ? "Fuente externa" : "Chef Asistente");

            JSONArray aliases = item.optJSONArray("aliases");
            if (aliases != null) for (int a = 0; a < aliases.length(); a++) recipe.aliases.add(aliases.optString(a));

            JSONArray ingredients = item.optJSONArray("ingredients");
            if (ingredients != null) for (int x = 0; x < ingredients.length(); x++) {
                JSONObject source = ingredients.getJSONObject(x);
                Ingredient ingredient = new Ingredient();
                ingredient.name = source.optString("name");
                ingredient.originalMeasure = source.optString("originalMeasure", "");
                if (ingredient.originalMeasure.isEmpty()) {
                    ingredient.quantity = source.optDouble("quantity", 0);
                    ingredient.unit = source.optString("unit", "");
                }
                recipe.ingredients.add(ingredient);
            }

            JSONArray prep = item.optJSONArray("preparation");
            if (prep != null) for (int p = 0; p < prep.length(); p++) recipe.preparation.add(prep.optString(p));

            JSONArray steps = item.optJSONArray("steps");
            if (steps != null) for (int s = 0; s < steps.length(); s++) {
                JSONObject source = steps.getJSONObject(s);
                Step step = new Step();
                step.title = source.optString("title", "Paso " + (s + 1));
                step.instruction = source.optString("instruction", "");
                step.duration = source.optString("duration", "");
                recipe.steps.add(step);
            }
            recipes.put(MainActivity.norm(recipe.key), recipe);
        }
    }

    private JSONObject toJson(Recipe recipe) throws Exception {
        JSONObject item = new JSONObject();
        item.put("key", recipe.key); item.put("name", recipe.name); item.put("baseServings", recipe.baseServings);
        item.put("external", recipe.external); item.put("sourceLabel", recipe.sourceLabel);
        JSONArray aliases = new JSONArray(); for (String a : recipe.aliases) aliases.put(a); item.put("aliases", aliases);
        JSONArray ingredients = new JSONArray();
        for (Ingredient ingredient : recipe.ingredients) {
            JSONObject x = new JSONObject(); x.put("name", ingredient.name);
            if (ingredient.originalMeasure != null && !ingredient.originalMeasure.isEmpty()) x.put("originalMeasure", ingredient.originalMeasure);
            else { x.put("quantity", ingredient.quantity); x.put("unit", ingredient.unit); }
            ingredients.put(x);
        }
        item.put("ingredients", ingredients);
        JSONArray prep = new JSONArray(); for (String p : recipe.preparation) prep.put(p); item.put("preparation", prep);
        JSONArray steps = new JSONArray();
        for (Step step : recipe.steps) { JSONObject s = new JSONObject(); s.put("title", step.title); s.put("instruction", step.instruction); s.put("duration", step.duration); steps.put(s); }
        item.put("steps", steps);
        return item;
    }

    private String download(String address) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(7000); connection.setReadTimeout(10000);
        connection.setRequestProperty("Accept", "application/json"); connection.setRequestProperty("User-Agent", "ChefAsistente/0.5");
        try { int status = connection.getResponseCode(); if (status < 200 || status >= 300) return null; return readAll(connection.getInputStream()); }
        finally { connection.disconnect(); }
    }

    private void writeFile(String name, String text) throws Exception {
        try (FileOutputStream out = new FileOutputStream(new File(context.getFilesDir(), name), false)) { out.write(text.getBytes(StandardCharsets.UTF_8)); }
    }

    private void post(UpdateCallback callback, boolean online, boolean updated, String message) {
        new Handler(Looper.getMainLooper()).post(() -> callback.onResult(online, updated, message));
    }

    private static String readAll(InputStream in) throws Exception {
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line; while ((line = reader.readLine()) != null) builder.append(line).append('\n');
        }
        return builder.toString();
    }
}
