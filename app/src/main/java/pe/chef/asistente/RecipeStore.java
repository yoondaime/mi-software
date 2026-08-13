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
        public double quantity;
        public String unit;
    }

    public static class Step {
        public String title;
        public String instruction;
        public String duration;
    }

    public static class Recipe {
        public String key;
        public String name;
        public int baseServings;
        public List<String> aliases = new ArrayList<>();
        public List<Ingredient> ingredients = new ArrayList<>();
        public List<String> preparation = new ArrayList<>();
        public List<Step> steps = new ArrayList<>();
    }

    public interface UpdateCallback {
        void onResult(boolean online, boolean updated, String message);
    }

    private static final String CACHE_FILE = "recetas_hibridas.json";
    private static final String REMOTE_URL = "https://raw.githubusercontent.com/yoondaime/mi-software/main/data/recetas.json";

    private final Context context;
    private final Map<String, Recipe> recipes = new LinkedHashMap<>();
    private int catalogVersion = 0;

    public RecipeStore(Context context) {
        this.context = context.getApplicationContext();
    }

    public int getCatalogVersion() {
        return catalogVersion;
    }

    public int size() {
        return recipes.size();
    }

    public void loadBestLocalCatalog() throws Exception {
        File cache = new File(context.getFilesDir(), CACHE_FILE);
        if (cache.exists()) {
            try {
                parse(readAll(new FileInputStream(cache)));
                return;
            } catch (Exception ignored) {
                // Si el cache quedó incompleto, usamos la copia incluida en el APK.
            }
        }
        try (InputStream in = context.getAssets().open("recetas.json")) {
            parse(readAll(in));
        }
    }

    public Recipe find(String raw) {
        String query = MainActivity.norm(raw);
        Recipe best = null;
        int bestLength = 0;
        for (Recipe recipe : recipes.values()) {
            List<String> candidates = new ArrayList<>();
            candidates.add(recipe.key);
            candidates.add(recipe.name);
            candidates.addAll(recipe.aliases);
            for (String candidate : candidates) {
                String normalized = MainActivity.norm(candidate);
                if (!normalized.isEmpty() && query.contains(normalized) && normalized.length() > bestLength) {
                    best = recipe;
                    bestLength = normalized.length();
                }
            }
        }
        return best;
    }

    public void updateFromInternet(UpdateCallback callback) {
        new Thread(() -> {
            boolean updated = false;
            String message;
            try {
                HttpURLConnection connection = (HttpURLConnection) new URL(REMOTE_URL).openConnection();
                connection.setConnectTimeout(7000);
                connection.setReadTimeout(7000);
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("Cache-Control", "no-cache");
                int status = connection.getResponseCode();
                if (status < 200 || status >= 300) throw new Exception("HTTP " + status);
                String remoteJson = readAll(connection.getInputStream());
                JSONObject root = new JSONObject(remoteJson);
                int remoteVersion = root.optInt("version", 0);
                if (remoteVersion >= catalogVersion) {
                    File cache = new File(context.getFilesDir(), CACHE_FILE);
                    try (FileOutputStream out = new FileOutputStream(cache, false)) {
                        out.write(remoteJson.getBytes(StandardCharsets.UTF_8));
                    }
                    parse(remoteJson);
                    updated = remoteVersion > catalogVersion || remoteVersion == catalogVersion;
                    message = "Con internet · recetas sincronizadas · catálogo v" + remoteVersion;
                } else {
                    message = "Con internet · ya tienes un catálogo más nuevo";
                }
                connection.disconnect();
                post(callback, true, updated, message);
            } catch (Exception e) {
                message = "Sin internet · usando " + recipes.size() + " recetas guardadas";
                post(callback, false, false, message);
            }
        }).start();
    }

    private void post(UpdateCallback callback, boolean online, boolean updated, String message) {
        new Handler(Looper.getMainLooper()).post(() -> callback.onResult(online, updated, message));
    }

    private synchronized void parse(String json) throws Exception {
        JSONObject root = new JSONObject(json);
        int newVersion = root.optInt("version", 0);
        JSONArray array = root.getJSONArray("recipes");
        Map<String, Recipe> parsed = new LinkedHashMap<>();

        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.getJSONObject(i);
            Recipe recipe = new Recipe();
            recipe.key = item.getString("key");
            recipe.name = item.getString("name");
            recipe.baseServings = item.optInt("baseServings", 4);

            JSONArray aliases = item.optJSONArray("aliases");
            if (aliases != null) {
                for (int a = 0; a < aliases.length(); a++) recipe.aliases.add(aliases.getString(a));
            }

            JSONArray ingredients = item.getJSONArray("ingredients");
            for (int x = 0; x < ingredients.length(); x++) {
                JSONObject source = ingredients.getJSONObject(x);
                Ingredient ingredient = new Ingredient();
                ingredient.name = source.getString("name");
                ingredient.quantity = source.getDouble("quantity");
                ingredient.unit = source.getString("unit");
                recipe.ingredients.add(ingredient);
            }

            JSONArray prep = item.optJSONArray("preparation");
            if (prep != null) {
                for (int p = 0; p < prep.length(); p++) recipe.preparation.add(prep.getString(p));
            }

            JSONArray steps = item.getJSONArray("steps");
            for (int s = 0; s < steps.length(); s++) {
                JSONObject source = steps.getJSONObject(s);
                Step step = new Step();
                step.title = source.optString("title", "Paso " + (s + 1));
                step.instruction = source.getString("instruction");
                step.duration = source.optString("duration", "");
                recipe.steps.add(step);
            }
            parsed.put(recipe.key, recipe);
        }

        recipes.clear();
        recipes.putAll(parsed);
        catalogVersion = newVersion;
    }

    private static String readAll(InputStream in) throws Exception {
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) builder.append(line).append('\n');
        }
        return builder.toString();
    }
}
