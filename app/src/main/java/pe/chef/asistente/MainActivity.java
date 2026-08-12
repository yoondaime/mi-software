package pe.chef.asistente;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
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
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity implements TextToSpeech.OnInitListener {
    private static final int AUDIO_PERMISSION = 101;

    static class Recipe {
        String name;
        int base;
        String[][] ingredients;
        String[] steps;
        Recipe(String name, int base, String[][] ingredients, String[] steps) {
            this.name = name; this.base = base; this.ingredients = ingredients; this.steps = steps;
        }
    }

    private final Map<String, Recipe> recipes = new LinkedHashMap<>();
    private Recipe current;
    private int servings = 0;
    private int step = 0;
    private int state = 0; // 0 receta, 1 personas, 2 listo, 3 cocinando

    private LinearLayout messages;
    private ScrollView scroll;
    private EditText input;
    private Button mic;
    private TextView status;
    private SpeechRecognizer recognizer;
    private Intent recognizerIntent;
    private TextToSpeech tts;
    private boolean ttsReady = false;

    private final int GREEN = Color.rgb(23,92,76);
    private final int CREAM = Color.rgb(255,249,238);
    private final int ORANGE = Color.rgb(240,138,75);
    private final int TEXT = Color.rgb(29,42,38);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        loadRecipes();
        tts = new TextToSpeech(this, this);
        setupRecognizer();
        setContentView(buildUi());
        say("Hola. Soy Chef Asistente. ¿Qué quieres cocinar hoy?", false);
    }

    private void loadRecipes() {
        add("arroz chaufa", "Arroz chaufa", new String[][]{
                {"4","tazas","arroz cocido frío"},{"400","gramos","pollo"},{"4","unidades","huevos"},
                {"1","taza","cebolla china"},{"4","cucharadas","sillao"},{"3","cucharadas","aceite"}
        }, new String[]{
                "Corta el pollo en trozos pequeños y pica la cebolla china.",
                "Bate los huevos con una pizca de sal.",
                "Calienta una sartén y cocina el huevo. Retíralo y resérvalo.",
                "Saltea el pollo hasta que esté bien cocido.",
                "Agrega el arroz frío y saltea a fuego alto.",
                "Añade el sillao y mezcla bien.",
                "Incorpora el huevo y la cebolla china, mezcla y sirve caliente."
        });
        add("lomo saltado", "Lomo saltado", new String[][]{
                {"600","gramos","carne de res"},{"2","unidades","cebolla roja"},{"3","unidades","tomate"},
                {"800","gramos","papas"},{"4","cucharadas","sillao"},{"4","tazas","arroz cocido"}
        }, new String[]{
                "Corta la carne en tiras, la cebolla en gajos y el tomate en trozos grandes.",
                "Fríe las papas y resérvalas.",
                "Sella la carne a fuego alto.",
                "Añade cebolla y saltea rápidamente.",
                "Agrega tomate y sillao.",
                "Mezcla brevemente y sirve con arroz y papas."
        });
        add("aji de gallina", "Ají de gallina", new String[][]{
                {"500","gramos","pollo"},{"5","cucharadas","ají amarillo"},{"4","rebanadas","pan"},
                {"1","taza","leche evaporada"},{"1","unidad","cebolla"},{"4","unidades","papa"}
        }, new String[]{
                "Sancocha el pollo y deshiláchalo.","Remoja el pan con leche.","Sofríe cebolla y ají amarillo.",
                "Agrega el pan licuado y remueve.","Incorpora el pollo y cocina unos minutos.","Sirve sobre papa sancochada."
        });
        add("arroz con pollo", "Arroz con pollo", new String[][]{
                {"4","unidades","presas de pollo"},{"3","tazas","arroz"},{"1","taza","culantro licuado"},
                {"1","taza","arvejas"},{"1","unidad","zanahoria"},{"4","tazas","caldo"}
        }, new String[]{
                "Sazona y dora las presas de pollo.","Sofríe cebolla y agrega el culantro.","Añade verduras y caldo.",
                "Incorpora el arroz y el pollo.","Cocina tapado a fuego bajo hasta que el arroz esté listo."
        });
        add("tallarin saltado", "Tallarín saltado", new String[][]{
                {"500","gramos","tallarines"},{"500","gramos","pollo o carne"},{"2","unidades","cebolla roja"},
                {"3","unidades","tomate"},{"4","cucharadas","sillao"}
        }, new String[]{
                "Cocina los tallarines al dente.","Corta la proteína y las verduras.","Saltea la proteína a fuego alto.",
                "Añade cebolla y tomate.","Agrega los tallarines y sillao, mezcla y sirve."
        });
        add("pollo al horno", "Pollo al horno", new String[][]{
                {"4","unidades","presas de pollo"},{"800","gramos","papas"},{"2","cucharadas","ajo molido"},
                {"2","cucharadas","mostaza"},{"2","unidades","limón"}
        }, new String[]{
                "Mezcla ajo, mostaza y limón.","Unta el pollo con el aderezo.","Corta las papas y colócalas en una fuente.",
                "Acomoda el pollo encima y hornea hasta que esté completamente cocido y dorado."
        });
        add("causa rellena", "Causa rellena", new String[][]{
                {"1","kilogramo","papa amarilla"},{"3","cucharadas","ají amarillo"},{"3","unidades","limón"},
                {"2","latas","atún"},{"5","cucharadas","mayonesa"},{"2","unidades","palta"}
        }, new String[]{
                "Sancocha y prensa las papas.","Mezcla la papa con ají, limón y sal.","Mezcla el atún con mayonesa.",
                "Forma una capa de papa, agrega relleno y palta, y cubre con otra capa de papa."
        });
        add("papa a la huancaina", "Papa a la huancaína", new String[][]{
                {"8","unidades","papas"},{"300","gramos","queso fresco"},{"4","unidades","ají amarillo"},
                {"1","taza","leche evaporada"},{"6","unidades","galletas de soda"}
        }, new String[]{
                "Sancocha las papas.","Licúa queso, ají, leche y galletas.","Ajusta la textura agregando leche poco a poco.",
                "Corta las papas y sirve cubiertas con la salsa."
        });
        add("estofado de pollo", "Estofado de pollo", new String[][]{
                {"4","unidades","presas de pollo"},{"4","unidades","papas"},{"2","unidades","zanahorias"},
                {"3","unidades","tomates"},{"1","taza","arvejas"}
        }, new String[]{
                "Sazona y dora el pollo.","Sofríe cebolla y tomate.","Agrega zanahoria, arvejas y un poco de caldo.",
                "Devuelve el pollo, añade las papas y cocina hasta que todo esté listo."
        });
        add("ceviche", "Ceviche", new String[][]{
                {"800","gramos","pescado fresco"},{"12","unidades","limones"},{"2","unidades","cebolla roja"},
                {"2","unidades","ají limo"},{"2","unidades","camote"}
        }, new String[]{
                "Mantén el pescado refrigerado y córtalo en cubos.","Corta la cebolla en pluma.",
                "Sazona el pescado y agrega ají.","Exprime el limón justo antes de mezclar.",
                "Mezcla brevemente y sirve inmediatamente con cebolla y camote."
        });
    }

    private void add(String key, String name, String[][] ing, String[] steps) {
        recipes.put(key, new Recipe(name, 4, ing, steps));
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(16));
        root.setBackgroundColor(CREAM);

        TextView title = label("CHEF ASISTENTE", 13, Color.WHITE, true);
        TextView question = label("¿Qué quieres cocinar hoy?", 26, Color.WHITE, true);
        TextView sub = label("Habla conmigo y te guío paso a paso.", 15, Color.rgb(225,242,237), false);
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(18),dp(18),dp(18),dp(18));
        header.setBackground(round(GREEN, 22));
        header.addView(title); header.addView(question); header.addView(sub);
        root.addView(header);

        status = label("Esperando receta", 13, Color.DKGRAY, true);
        status.setPadding(dp(4),dp(12),0,dp(8));
        root.addView(status);

        scroll = new ScrollView(this);
        messages = new LinearLayout(this);
        messages.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(messages);
        root.addView(scroll, new LinearLayout.LayoutParams(-1,0,1));

        mic = new Button(this);
        mic.setText("🎤  HABLAR");
        mic.setTextSize(18); mic.setTextColor(Color.WHITE); mic.setAllCaps(false);
        mic.setTypeface(Typeface.DEFAULT_BOLD); mic.setBackground(round(ORANGE, 28));
        mic.setOnClickListener(v -> startListening());
        root.addView(mic, new LinearLayout.LayoutParams(-1, dp(60)));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL); row.setPadding(0,dp(10),0,0);
        input = new EditText(this);
        input.setHint("O escribe aquí…"); input.setTextSize(16); input.setSingleLine(true);
        input.setImeOptions(EditorInfo.IME_ACTION_SEND); input.setBackground(round(Color.WHITE,18));
        input.setPadding(dp(12),0,dp(12),0);
        input.setOnEditorActionListener((v,id,event)->{ if(id==EditorInfo.IME_ACTION_SEND){sendTyped(); return true;} return false; });
        Button send = new Button(this);
        send.setText("Enviar"); send.setAllCaps(false); send.setTextColor(Color.WHITE); send.setBackground(round(GREEN,18));
        send.setOnClickListener(v -> sendTyped());
        row.addView(input,new LinearLayout.LayoutParams(0,dp(52),1));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(dp(90),dp(52)); sp.setMargins(dp(8),0,0,0);
        row.addView(send,sp); root.addView(row);
        return root;
    }

    private void sendTyped() {
        String s=input.getText().toString().trim(); if(s.isEmpty()) return; input.setText(""); process(s);
    }

    private void process(String raw) {
        bubble(raw,true);
        String in=norm(raw);
        String response;
        if(in.contains("otra receta") || in.contains("cambiar receta")) { reset(); response="Claro. ¿Qué quieres cocinar ahora?"; }
        else if(state==0) {
            current=findRecipe(in);
            if(current==null) response="Aún no tengo esa receta. Prueba con arroz chaufa, lomo saltado, ají de gallina, arroz con pollo, tallarín saltado, pollo al horno, causa, papa a la huancaína, estofado de pollo o ceviche.";
            else { state=1; status.setText(current.name+" · faltan personas"); response="Claro, prepararemos "+current.name+". ¿Para cuántas personas?"; }
        } else if(state==1) {
            int n=number(in);
            if(n<1 || n>20) response="Dime un número entre 1 y 20. Por ejemplo: para 4 personas.";
            else { servings=n; state=2; status.setText(current.name+" · "+servings+" personas"); response="Perfecto. "+ingredients()+" Cuando estés listo, dime comenzar."; }
        } else if(in.contains("ingrediente") || in.contains("que necesito")) response=ingredients();
        else if(in.contains("cuanto") || in.contains("cantidad")) response=amount(in);
        else if(state==2) {
            if(in.contains("comenz") || in.contains("empez") || in.equals("listo") || in.equals("vamos")) { state=3; step=0; response=currentStep(); }
            else response="Cuando quieras iniciar, dime comenzar.";
        } else {
            if(in.contains("repet")) response=currentStep();
            else if(in.contains("anterior")) { if(step>0) step--; response=currentStep(); }
            else if(in.contains("siguiente") || in.contains("sigue") || in.contains("ya esta") || in.equals("listo")) {
                if(step<current.steps.length-1){step++; response=currentStep();}
                else { response="¡Listo! Terminamos "+current.name+". Buen provecho. Si quieres otra receta, dímelo."; reset(); }
            } else response="Puedes decir siguiente, repetir, anterior, ingredientes o preguntarme una cantidad.";
        }
        say(response,true);
    }

    private String ingredients() {
        if(current==null || servings<1) return "Primero necesito saber la receta y para cuántas personas.";
        double factor=(double)servings/current.base; StringBuilder b=new StringBuilder("Necesitaremos: ");
        for(int i=0;i<current.ingredients.length;i++){
            String[] x=current.ingredients[i]; double q=Double.parseDouble(x[0])*factor;
            if(i>0) b.append(i==current.ingredients.length-1?", y ":", ");
            b.append(format(q)).append(" ").append(x[1]).append(" de ").append(x[2]);
        }
        return b.append(".").toString();
    }

    private String amount(String in) {
        if(current==null || servings<1) return "Primero elige una receta y dime para cuántas personas.";
        double factor=(double)servings/current.base;
        for(String[] x:current.ingredients){
            String name=norm(x[2]); String first=name.split(" ")[0];
            if(in.contains(name)||in.contains(first)) return "Para "+servings+" personas necesitas "+format(Double.parseDouble(x[0])*factor)+" "+x[1]+" de "+x[2]+".";
        }
        return "No encontré ese ingrediente en la receta actual.";
    }

    private String currentStep(){ return "Paso "+(step+1)+" de "+current.steps.length+": "+current.steps[step]+" Cuando termines, dime siguiente."; }

    private Recipe findRecipe(String in){
        for(Map.Entry<String,Recipe> e:recipes.entrySet()) if(in.contains(e.getKey())) return e.getValue();
        if(in.contains("chaufa")) return recipes.get("arroz chaufa");
        if(in.contains("lomo")) return recipes.get("lomo saltado");
        if(in.contains("gallina")) return recipes.get("aji de gallina");
        if(in.contains("huancaina")) return recipes.get("papa a la huancaina");
        if(in.contains("causa")) return recipes.get("causa rellena");
        if(in.contains("estofado")) return recipes.get("estofado de pollo");
        if(in.contains("ceviche")) return recipes.get("ceviche");
        return null;
    }

    private int number(String in){
        String digits=in.replaceAll("[^0-9]"," ").trim();
        if(!digits.isEmpty()) try{return Integer.parseInt(digits.split("\\s+")[0]);}catch(Exception ignored){}
        String[] w={"cero","uno","dos","tres","cuatro","cinco","seis","siete","ocho","nueve","diez","once","doce","trece","catorce","quince","dieciseis","diecisiete","dieciocho","diecinueve","veinte"};
        for(int i=1;i<w.length;i++) if(in.contains(w[i])) return i; return -1;
    }

    private void reset(){ current=null; servings=0; step=0; state=0; status.setText("Esperando receta"); }
    private String format(double n){ if(Math.abs(n-Math.rint(n))<0.001) return String.valueOf((int)Math.rint(n)); return String.format(Locale.US,"%.1f",n).replace(".0","").replace(".",","); }
    private String norm(String s){ String n=Normalizer.normalize(s.toLowerCase(new Locale("es","PE")),Normalizer.Form.NFD); return n.replaceAll("\\p{M}","").trim(); }

    private void say(String s, boolean speak){ bubble(s,false); if(speak && ttsReady) tts.speak(s,TextToSpeech.QUEUE_FLUSH,null,"chef"); }
    private void bubble(String s, boolean user){
        TextView v=label(s,16,user?Color.WHITE:TEXT,false); v.setPadding(dp(14),dp(11),dp(14),dp(11)); v.setBackground(round(user?GREEN:Color.WHITE,18));
        LinearLayout line=new LinearLayout(this); line.setGravity(user?Gravity.END:Gravity.START); line.setPadding(0,dp(4),0,dp(4)); line.addView(v); messages.addView(line);
        scroll.post(()->scroll.fullScroll(View.FOCUS_DOWN));
    }

    private void setupRecognizer(){
        if(!SpeechRecognizer.isRecognitionAvailable(this)) return;
        recognizer=SpeechRecognizer.createSpeechRecognizer(this);
        recognizer.setRecognitionListener(new RecognitionListener(){
            public void onReadyForSpeech(Bundle b){mic.setText("🎙️  ESCUCHANDO…");}
            public void onBeginningOfSpeech(){} public void onRmsChanged(float f){} public void onBufferReceived(byte[] b){} public void onEndOfSpeech(){}
            public void onError(int e){mic.setText("🎤  HABLAR"); Toast.makeText(MainActivity.this,"No pude reconocer la frase. Intenta otra vez.",Toast.LENGTH_SHORT).show();}
            public void onResults(Bundle b){mic.setText("🎤  HABLAR"); ArrayList<String> r=b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION); if(r!=null&&!r.isEmpty()) process(r.get(0));}
            public void onPartialResults(Bundle b){} public void onEvent(int i,Bundle b){}
        });
        recognizerIntent=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"es-PE");
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,true);
    }

    private void startListening(){
        if(recognizer==null){Toast.makeText(this,"No hay reconocedor de voz disponible. Puedes escribir.",Toast.LENGTH_LONG).show();return;}
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},AUDIO_PERMISSION);return;}
        if(tts!=null) tts.stop(); recognizer.startListening(recognizerIntent);
    }

    @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){super.onRequestPermissionsResult(r,p,g); if(r==AUDIO_PERMISSION&&g.length>0&&g[0]==PackageManager.PERMISSION_GRANTED) startListening();}
    @Override public void onInit(int statusCode){if(statusCode==TextToSpeech.SUCCESS){int r=tts.setLanguage(new Locale("es","PE")); tts.setSpeechRate(.95f); ttsReady=r!=TextToSpeech.LANG_MISSING_DATA&&r!=TextToSpeech.LANG_NOT_SUPPORTED;}}
    @Override protected void onDestroy(){if(recognizer!=null)recognizer.destroy(); if(tts!=null){tts.stop();tts.shutdown();} super.onDestroy();}

    private TextView label(String s,int size,int color,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(color);if(bold)v.setTypeface(Typeface.DEFAULT_BOLD);return v;}
    private GradientDrawable round(int color,int radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));return g;}
    private int dp(int x){return Math.round(x*getResources().getDisplayMetrics().density);}
}
