package pe.chef.asistente;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends Activity implements TextToSpeech.OnInitListener {
    private static final int VOICE_REQUEST = 300;

    private final int GREEN = Color.rgb(23, 92, 76);
    private final int GREEN_DARK = Color.rgb(14, 68, 56);
    private final int CREAM = Color.rgb(255, 249, 238);
    private final int ORANGE = Color.rgb(240, 138, 75);
    private final int TEXT = Color.rgb(29, 42, 38);
    private final int MUTED = Color.rgb(96, 110, 105);

    private RecipeStore store;
    private RecipeStore.Recipe current;
    private int servings = 0;
    private int stepIndex = 0;
    private int state = 0;

    private LinearLayout messages;
    private ScrollView scroll;
    private EditText input;
    private TextView recipeStatus;
    private TextView networkStatus;
    private TextView heardStatus;
    private Button updateButton;
    private TextToSpeech tts;
    private boolean ttsReady = false;
    private boolean greetingPending = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(GREEN_DARK);
        getWindow().setNavigationBarColor(CREAM);

        store = new RecipeStore(this);
        try {
            store.loadBestLocalCatalog();
        } catch (Exception e) {
            Toast.makeText(this, "No pude cargar las recetas locales.", Toast.LENGTH_LONG).show();
        }

        setContentView(buildUi());
        tts = new TextToSpeech(this, this);
        say("Hola. Soy Chef Asistente. Tengo " + store.size() + " recetas disponibles. ¿Qué quieres cocinar hoy?", false);
        syncRecipes(false);
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(12), dp(14), dp(12));
        root.setBackgroundColor(CREAM);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(18), dp(16), dp(18), dp(16));
        header.setBackground(round(GREEN, 22));
        TextView brand = label("CHEF ASISTENTE · HÍBRIDO v0.4", 13, Color.WHITE, true);
        TextView question = label("¿Qué quieres cocinar hoy?", 25, Color.WHITE, true);
        question.setPadding(0, dp(5), 0, dp(3));
        TextView sub = label("Funciona sin internet y actualiza recetas cuando hay conexión", 14, Color.rgb(224, 242, 236), false);
        header.addView(brand);
        header.addView(question);
        header.addView(sub);
        root.addView(header);

        LinearLayout networkRow = new LinearLayout(this);
        networkRow.setOrientation(LinearLayout.HORIZONTAL);
        networkRow.setGravity(Gravity.CENTER_VERTICAL);
        networkRow.setPadding(dp(3), dp(9), dp(3), dp(4));
        networkStatus = label("📵 Catálogo local v" + store.getCatalogVersion() + " · " + store.size() + " recetas", 13, MUTED, true);
        networkRow.addView(networkStatus, new LinearLayout.LayoutParams(0, -2, 1f));
        updateButton = new Button(this);
        updateButton.setText("Actualizar");
        updateButton.setAllCaps(false);
        updateButton.setTextColor(Color.WHITE);
        updateButton.setTextSize(13);
        updateButton.setBackground(round(GREEN_DARK, 16));
        updateButton.setOnClickListener(v -> syncRecipes(true));
        networkRow.addView(updateButton, new LinearLayout.LayoutParams(dp(100), dp(42)));
        root.addView(networkRow);

        recipeStatus = label("Esperando receta", 13, MUTED, true);
        recipeStatus.setPadding(dp(4), dp(2), dp(4), dp(3));
        root.addView(recipeStatus);
        heardStatus = label("🎙️ Aún no has usado el dictado", 12, MUTED, false);
        heardStatus.setPadding(dp(4), dp(2), dp(4), dp(6));
        root.addView(heardStatus);

        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        messages = new LinearLayout(this);
        messages.setOrientation(LinearLayout.VERTICAL);
        messages.setPadding(0, dp(4), 0, dp(8));
        scroll.addView(messages);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        Button mic = new Button(this);
        mic.setText("🎤  HABLAR");
        mic.setTextSize(18);
        mic.setAllCaps(false);
        mic.setTypeface(Typeface.DEFAULT_BOLD);
        mic.setTextColor(Color.WHITE);
        mic.setBackground(round(ORANGE, 28));
        mic.setOnClickListener(v -> launchVoiceInput());
        root.addView(mic, new LinearLayout.LayoutParams(-1, dp(60)));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(9), 0, 0);
        input = new EditText(this);
        input.setTextSize(16);
        input.setHint("O escribe: quiero arroz chaufa");
        input.setSingleLine(true);
        input.setImeOptions(EditorInfo.IME_ACTION_SEND);
        input.setPadding(dp(12), 0, dp(12), 0);
        input.setBackground(round(Color.WHITE, 18));
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendTyped();
                return true;
            }
            return false;
        });
        Button send = new Button(this);
        send.setText("Enviar");
        send.setAllCaps(false);
        send.setTextColor(Color.WHITE);
        send.setTypeface(Typeface.DEFAULT_BOLD);
        send.setBackground(round(GREEN, 18));
        send.setOnClickListener(v -> sendTyped());
        row.addView(input, new LinearLayout.LayoutParams(0, dp(52), 1f));
        LinearLayout.LayoutParams sendParams = new LinearLayout.LayoutParams(dp(92), dp(52));
        sendParams.setMargins(dp(7), 0, 0, 0);
        row.addView(send, sendParams);
        root.addView(row);
        return root;
    }

    private void syncRecipes(boolean requestedByUser) {
        updateButton.setEnabled(false);
        networkStatus.setText("🌐 Buscando actualización…");
        store.updateFromInternet((online, updated, message) -> {
            updateButton.setEnabled(true);
            networkStatus.setText((online ? "🌐 " : "📵 ") + message);
            if (requestedByUser) {
                say(online ? "Recetas actualizadas. Tienes " + store.size() + " recetas disponibles." : "No hay conexión. Seguiremos usando las recetas guardadas en el celular.", true);
            }
        });
    }

    private void sendTyped() {
        String value = input.getText().toString().trim();
        if (value.isEmpty()) return;
        input.setText("");
        process(value);
    }

    private void process(String raw) {
        bubble(raw, true);
        String in = norm(raw);
        String response;

        if (in.contains("otra receta") || in.contains("cambiar receta") || in.contains("nueva receta")) {
            resetRecipe();
            response = "Perfecto. ¿Qué quieres cocinar ahora?";
        } else if (in.contains("terminar receta") || in.equals("terminar")) {
            resetRecipe();
            response = "De acuerdo. Terminamos esta sesión. Cuando quieras, dime otra receta.";
        } else if (state == 0) {
            current = store.find(in);
            if (current == null) {
                response = "Esa receta todavía no está en mi catálogo guardado. En esta versión puedo cocinar contigo arroz chaufa, lomo saltado, ají de gallina, arroz con pollo, tallarín saltado, pollo al horno, causa rellena, papa a la huancaína, estofado de pollo y ceviche.";
            } else {
                state = 1;
                recipeStatus.setText(current.name + " · falta indicar personas");
                response = "Claro. Prepararemos " + current.name + ". ¿Para cuántas personas?";
            }
        } else if (state == 1) {
            int n = detectNumber(in);
            if (n < 1 || n > 20) {
                response = "Dime un número entre 1 y 20. Por ejemplo: para cuatro personas.";
            } else {
                servings = n;
                state = 2;
                recipeStatus.setText(current.name + " · " + servings + " personas · listo para preparar");
                response = "Perfecto. " + ingredientSummary() + " También tengo la preparación previa completa. Puedes decir preparación o comenzar.";
            }
        } else if (in.contains("ingrediente") || in.contains("que necesito") || in.contains("lista completa")) {
            response = ingredientSummary();
        } else if (in.contains("preparacion") || in.contains("antes de empezar") || in.contains("antes de comenzar")) {
            response = preparationSummary();
        } else if (in.contains("cuanto") || in.contains("cantidad de") || in.contains("cuanta") || in.contains("cuantas")) {
            response = amountAnswer(in);
        } else if (state == 2) {
            if (in.contains("comenz") || in.contains("empez") || in.equals("listo") || in.equals("vamos") || in.equals("si")) {
                state = 3;
                stepIndex = 0;
                recipeStatus.setText(current.name + " · paso 1 de " + current.steps.size());
                response = "Antes del primer paso: " + preparationSummary() + " Si ya lo tienes listo, empezamos. " + currentStep();
            } else {
                response = "Puedes decir comenzar, preparación, ingredientes o preguntarme cuánto necesitas de un ingrediente.";
            }
        } else {
            if (in.contains("repet")) {
                response = currentStep();
            } else if (in.contains("anterior") || in.contains("atras")) {
                if (stepIndex > 0) stepIndex--;
                updateStepStatus();
                response = currentStep();
            } else if (in.contains("siguiente") || in.contains("que sigue") || in.contains("sigue") || in.contains("ya esta") || in.equals("listo") || in.contains("continua")) {
                if (stepIndex < current.steps.size() - 1) {
                    stepIndex++;
                    updateStepStatus();
                    response = currentStep();
                } else {
                    response = "¡Listo! Terminamos " + current.name + ". Buen provecho. Cuando quieras puedes pedirme otra receta.";
                    resetRecipe();
                }
            } else if (in.contains("pausa") || in.contains("espera")) {
                response = "De acuerdo. Nos quedamos en el paso " + (stepIndex + 1) + ". Cuando quieras continuar, dime repetir o siguiente.";
            } else {
                response = "Estamos en el paso " + (stepIndex + 1) + ". Puedes decir siguiente, repetir, anterior, ingredientes, cuánto necesitas de algo o pausa.";
            }
        }
        say(response, true);
    }

    private String ingredientSummary() {
        if (current == null || servings < 1) return "Primero dime la receta y para cuántas personas.";
        double factor = (double) servings / current.baseServings;
        StringBuilder out = new StringBuilder("Para ").append(servings).append(" personas necesitas:\n");
        for (int i = 0; i < current.ingredients.size(); i++) {
            RecipeStore.Ingredient ingredient = current.ingredients.get(i);
            out.append("• ").append(format(ingredient.quantity * factor)).append(" ").append(ingredient.unit).append(" de ").append(ingredient.name);
            if (i < current.ingredients.size() - 1) out.append("\n");
        }
        return out.toString();
    }

    private String preparationSummary() {
        if (current == null) return "Primero elige una receta.";
        StringBuilder out = new StringBuilder("Preparación previa:\n");
        for (int i = 0; i < current.preparation.size(); i++) {
            out.append(i + 1).append(". ").append(current.preparation.get(i));
            if (i < current.preparation.size() - 1) out.append("\n");
        }
        return out.toString();
    }

    private String currentStep() {
        if (current == null || current.steps.isEmpty()) return "No hay pasos cargados.";
        RecipeStore.Step step = current.steps.get(stepIndex);
        StringBuilder out = new StringBuilder();
        out.append("Paso ").append(stepIndex + 1).append(" de ").append(current.steps.size()).append(": ").append(step.title).append(". ").append(step.instruction);
        if (step.duration != null && !step.duration.isEmpty()) out.append(" Tiempo aproximado: ").append(step.duration).append(".");
        out.append(" Cuando termines, dime siguiente.");
        return out.toString();
    }

    private String amountAnswer(String inputText) {
        if (current == null || servings < 1) return "Primero dime la receta y para cuántas personas.";
        double factor = (double) servings / current.baseServings;
        RecipeStore.Ingredient best = null;
        int length = 0;
        for (RecipeStore.Ingredient ingredient : current.ingredients) {
            String normalized = norm(ingredient.name);
            for (String part : normalized.split("\\s+")) {
                if (part.length() > 2 && inputText.contains(part) && part.length() > length) {
                    best = ingredient;
                    length = part.length();
                }
            }
            if (inputText.contains(normalized)) {
                best = ingredient;
                break;
            }
        }
        if (best == null) return "No identifiqué el ingrediente. Puedes decir, por ejemplo: cuánto sillao necesito.";
        return "Para " + servings + " personas necesitas " + format(best.quantity * factor) + " " + best.unit + " de " + best.name + ".";
    }

    private void updateStepStatus() {
        if (current != null) recipeStatus.setText(current.name + " · paso " + (stepIndex + 1) + " de " + current.steps.size());
    }

    private void resetRecipe() {
        current = null;
        servings = 0;
        stepIndex = 0;
        state = 0;
        recipeStatus.setText("Esperando receta");
    }

    private int detectNumber(String inputText) {
        for (String token : inputText.split("[^0-9]+")) {
            if (!token.isEmpty()) {
                try { return Integer.parseInt(token); } catch (Exception ignored) {}
            }
        }
        String[] words = {"cero", "uno", "dos", "tres", "cuatro", "cinco", "seis", "siete", "ocho", "nueve", "diez", "once", "doce", "trece", "catorce", "quince", "dieciseis", "diecisiete", "dieciocho", "diecinueve", "veinte"};
        for (int i = 1; i < words.length; i++) if (inputText.contains(words[i])) return i;
        return -1;
    }

    private void launchVoiceInput() {
        if (tts != null) tts.stop();
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-PE");
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Habla con Chef Asistente");
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        try {
            startActivityForResult(intent, VOICE_REQUEST);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "No encontré el dictado de voz de Android. Puedes escribir en la parte inferior.", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != VOICE_REQUEST) return;
        if (resultCode == RESULT_OK && data != null) {
            ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (results != null && !results.isEmpty()) {
                String heard = results.get(0);
                heardStatus.setText("🎙️ Último escuchado: “" + heard + "”");
                process(heard);
                return;
            }
        }
        heardStatus.setText("🎙️ No recibí texto del dictado. Intenta otra vez.");
    }

    private void say(String text, boolean speak) {
        bubble(text, false);
        if (speak && ttsReady) tts.speak(text.replace("\n", ". "), TextToSpeech.QUEUE_FLUSH, null, "chef_voice");
    }

    private void bubble(String text, boolean user) {
        if (messages == null) return;
        TextView bubble = label(text, 15, user ? Color.WHITE : TEXT, false);
        bubble.setPadding(dp(13), dp(10), dp(13), dp(10));
        bubble.setBackground(round(user ? GREEN : Color.WHITE, 17));
        bubble.setMaxWidth(dp(330));
        LinearLayout line = new LinearLayout(this);
        line.setGravity(user ? Gravity.END : Gravity.START);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(4), 0, dp(4));
        line.addView(bubble);
        messages.addView(line, params);
        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            int result = tts.setLanguage(new Locale("es", "PE"));
            tts.setSpeechRate(0.94f);
            ttsReady = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED;
            if (ttsReady && greetingPending) {
                greetingPending = false;
                tts.speak("Hola. Soy Chef Asistente. ¿Qué quieres cocinar hoy?", TextToSpeech.QUEUE_FLUSH, null, "greeting");
            }
        }
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }

    private TextView label(String text, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private String format(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.0001) return String.valueOf((int) Math.rint(value));
        return String.format(Locale.US, "%.2f", value).replaceAll("0+$", "").replaceAll("\\.$", "").replace(".", ",");
    }

    public static String norm(String value) {
        if (value == null) return "";
        String normalized = Normalizer.normalize(value.toLowerCase(new Locale("es", "PE")), Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{M}", "").trim();
    }
}
