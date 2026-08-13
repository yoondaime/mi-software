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
import android.widget.HorizontalScrollView;
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
    private static final int VOICE_REQUEST = 2001;

    static class Recipe {
        final String name; final String[][] ingredients; final String[] steps;
        Recipe(String name, String[][] ingredients, String[] steps) {
            this.name=name; this.ingredients=ingredients; this.steps=steps;
        }
    }

    private final Map<String,Recipe> recipes = new LinkedHashMap<>();
    private Recipe current;
    private int servings=0, step=0, state=0;

    private LinearLayout messages;
    private ScrollView chatScroll;
    private EditText input;
    private TextView contextText, listeningText, heardText;
    private TextToSpeech tts;
    private boolean ttsReady=false, greetingSpoken=false;

    private final int GREEN=Color.rgb(23,92,76), GREEN_DARK=Color.rgb(14,68,56);
    private final int CREAM=Color.rgb(255,249,238), ORANGE=Color.rgb(240,138,75);
    private final int TEXT=Color.rgb(29,42,38), MUTED=Color.rgb(102,115,111);

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        loadRecipes();
        getWindow().setStatusBarColor(GREEN_DARK);
        getWindow().setNavigationBarColor(CREAM);
        setContentView(buildUi());
        addMessage("Hola. Soy Chef Asistente. ¿Qué quieres cocinar hoy?", false);
        tts=new TextToSpeech(this,this);
    }

    private void loadRecipes(){
        add("arroz chaufa","Arroz chaufa",new String[][]{{"4","tazas","arroz cocido frío"},{"400","gramos","pollo"},{"4","unidades","huevos"},{"1","taza","cebolla china"},{"4","cucharadas","sillao"}},new String[]{"Corta el pollo y pica la cebolla china.","Bate los huevos.","Cocina el huevo y resérvalo.","Saltea el pollo hasta que esté cocido.","Agrega el arroz y saltea a fuego alto.","Añade el sillao.","Incorpora huevo y cebolla china, mezcla y sirve."});
        add("lomo saltado","Lomo saltado",new String[][]{{"600","gramos","carne de res"},{"2","unidades","cebolla roja"},{"3","unidades","tomate"},{"800","gramos","papas"},{"4","cucharadas","sillao"}},new String[]{"Corta carne, cebolla y tomate.","Fríe las papas y resérvalas.","Sella la carne a fuego alto.","Añade cebolla y tomate.","Agrega sillao, mezcla y sirve con papas."});
        add("aji de gallina","Ají de gallina",new String[][]{{"500","gramos","pollo"},{"5","cucharadas","ají amarillo"},{"4","rebanadas","pan"},{"1","taza","leche evaporada"},{"4","unidades","papa"}},new String[]{"Sancocha el pollo y deshiláchalo.","Remoja el pan con leche.","Sofríe cebolla y ají.","Agrega el pan licuado.","Incorpora el pollo y cocina.","Sirve sobre papa sancochada."});
        add("arroz con pollo","Arroz con pollo",new String[][]{{"4","unidades","presas de pollo"},{"3","tazas","arroz"},{"1","taza","culantro licuado"},{"1","taza","arvejas"},{"4","tazas","caldo"}},new String[]{"Sazona y dora el pollo.","Sofríe cebolla y culantro.","Añade verduras y caldo.","Incorpora arroz y pollo.","Cocina tapado hasta que el arroz esté listo."});
        add("tallarin saltado","Tallarín saltado",new String[][]{{"500","gramos","tallarines"},{"500","gramos","pollo o carne"},{"2","unidades","cebolla roja"},{"3","unidades","tomate"},{"4","cucharadas","sillao"}},new String[]{"Cocina los tallarines al dente.","Corta proteína y verduras.","Saltea la proteína.","Añade cebolla y tomate.","Agrega tallarines y sillao, mezcla y sirve."});
        add("pollo al horno","Pollo al horno",new String[][]{{"4","unidades","presas de pollo"},{"800","gramos","papas"},{"2","cucharadas","ajo molido"},{"2","cucharadas","mostaza"},{"2","unidades","limón"}},new String[]{"Mezcla ajo, mostaza y limón.","Unta el pollo con el aderezo.","Corta las papas y colócalas en una fuente.","Hornea pollo y papas hasta que estén bien cocidos."});
        add("causa rellena","Causa rellena",new String[][]{{"1","kilogramo","papa amarilla"},{"3","cucharadas","ají amarillo"},{"3","unidades","limón"},{"2","latas","atún"},{"5","cucharadas","mayonesa"},{"2","unidades","palta"}},new String[]{"Sancocha y prensa las papas.","Mezcla papa, ají, limón y sal.","Mezcla el atún con mayonesa.","Forma capas de papa, relleno y palta."});
        add("papa a la huancaina","Papa a la huancaína",new String[][]{{"8","unidades","papas"},{"300","gramos","queso fresco"},{"4","unidades","ají amarillo"},{"1","taza","leche evaporada"},{"6","unidades","galletas de soda"}},new String[]{"Sancocha las papas.","Licúa queso, ají, leche y galletas.","Ajusta la textura con leche.","Corta las papas y sirve con la salsa."});
        add("estofado de pollo","Estofado de pollo",new String[][]{{"4","unidades","presas de pollo"},{"4","unidades","papas"},{"2","unidades","zanahorias"},{"3","unidades","tomates"},{"1","taza","arvejas"}},new String[]{"Sazona y dora el pollo.","Sofríe cebolla y tomate.","Agrega zanahoria, arvejas y caldo.","Añade pollo y papas y cocina hasta que estén listos."});
        add("ceviche","Ceviche",new String[][]{{"800","gramos","pescado fresco"},{"12","unidades","limones"},{"2","unidades","cebolla roja"},{"2","unidades","ají limo"},{"2","unidades","camote"}},new String[]{"Mantén el pescado frío y córtalo en cubos.","Corta la cebolla en pluma.","Sazona el pescado y agrega ají.","Exprime el limón justo antes de mezclar.","Mezcla brevemente y sirve de inmediato."});
    }
    private void add(String key,String name,String[][] ing,String[] steps){recipes.put(key,new Recipe(name,ing,steps));}

    private View buildUi(){
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(CREAM); root.setPadding(dp(16),dp(12),dp(16),dp(12));
        LinearLayout header=new LinearLayout(this); header.setOrientation(LinearLayout.VERTICAL); header.setPadding(dp(18),dp(18),dp(18),dp(18)); header.setBackground(round(GREEN,22));
        TextView small=txt("CHEF ASISTENTE · VOZ v0.3",12,Color.WHITE,true); small.setLetterSpacing(.08f);
        TextView title=txt("¿Qué quieres cocinar hoy?",25,Color.WHITE,true); title.setPadding(0,dp(6),0,dp(4));
        header.addView(small); header.addView(title); header.addView(txt("Toca HABLAR. Te mostraré exactamente lo que Android entendió.",14,Color.rgb(226,242,237),false)); root.addView(header);
        contextText=txt("Esperando receta",13,MUTED,true); contextText.setPadding(dp(4),dp(10),0,dp(4)); root.addView(contextText);
        heardText=txt("Último escuchado: —",13,GREEN_DARK,true); heardText.setPadding(dp(4),0,0,dp(7)); root.addView(heardText);
        root.addView(shortcuts());
        chatScroll=new ScrollView(this); messages=new LinearLayout(this); messages.setOrientation(LinearLayout.VERTICAL); messages.setPadding(0,dp(8),0,dp(8)); chatScroll.addView(messages); root.addView(chatScroll,new LinearLayout.LayoutParams(-1,0,1));
        listeningText=txt("Toca HABLAR para abrir el dictado de voz",13,MUTED,false); listeningText.setGravity(Gravity.CENTER); listeningText.setPadding(0,dp(5),0,dp(5)); root.addView(listeningText);
        Button mic=new Button(this); mic.setText("🎤  HABLAR"); mic.setTextSize(18); mic.setTextColor(Color.WHITE); mic.setAllCaps(false); mic.setTypeface(Typeface.DEFAULT_BOLD); mic.setBackground(round(ORANGE,28)); mic.setOnClickListener(v->voice()); root.addView(mic,new LinearLayout.LayoutParams(-1,dp(60)));
        LinearLayout row=new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setPadding(0,dp(10),0,0);
        input=new EditText(this); input.setHint("También puedes escribir…"); input.setTextSize(16); input.setSingleLine(true); input.setImeOptions(EditorInfo.IME_ACTION_SEND); input.setPadding(dp(14),0,dp(14),0); input.setBackground(round(Color.WHITE,18)); input.setOnEditorActionListener((v,id,e)->{if(id==EditorInfo.IME_ACTION_SEND){sendTyped();return true;}return false;});
        Button send=new Button(this); send.setText("Enviar"); send.setAllCaps(false); send.setTextColor(Color.WHITE); send.setTypeface(Typeface.DEFAULT_BOLD); send.setBackground(round(GREEN,18)); send.setOnClickListener(v->sendTyped());
        row.addView(input,new LinearLayout.LayoutParams(0,dp(52),1)); LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(92),dp(52)); p.setMargins(dp(8),0,0,0); row.addView(send,p); root.addView(row);
        return root;
    }

    private View shortcuts(){
        HorizontalScrollView s=new HorizontalScrollView(this); s.setHorizontalScrollBarEnabled(false); LinearLayout row=new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL);
        String[] a={"Arroz chaufa","Lomo saltado","Ají de gallina","Ceviche","Arroz con pollo"};
        for(String r:a){Button b=new Button(this); b.setText(r); b.setAllCaps(false); b.setTextColor(GREEN_DARK); b.setBackground(round(Color.WHITE,18)); b.setOnClickListener(v->process(((Button)v).getText().toString())); LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,dp(44)); p.setMargins(0,0,dp(8),0); row.addView(b,p);} s.addView(row); return s;
    }

    private void voice(){
        if(tts!=null)tts.stop();
        Intent i=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH); i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM); i.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"es-PE"); i.putExtra(RecognizerIntent.EXTRA_PROMPT,"Habla con Chef Asistente"); i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,3);
        try{listeningText.setText("Habla ahora…"); startActivityForResult(i,VOICE_REQUEST);}catch(ActivityNotFoundException e){listeningText.setText("No hay servicio de dictado disponible"); Toast.makeText(this,"No encontré el dictado de voz de Android. Puedes escribir mientras incorporamos el motor offline propio.",Toast.LENGTH_LONG).show();}
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data); if(requestCode!=VOICE_REQUEST)return; listeningText.setText("Toca HABLAR para volver a hablar");
        if(resultCode==RESULT_OK&&data!=null){ArrayList<String> a=data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS); if(a!=null&&!a.isEmpty()){String h=a.get(0).trim(); heardText.setText("Último escuchado: “"+h+"”"); process(h); return;}}
        heardText.setText("Último escuchado: no se recibió texto"); say("No recibí ninguna frase. Toca hablar e inténtalo otra vez.",true);
    }

    private void sendTyped(){String s=input.getText().toString().trim(); if(s.isEmpty())return; input.setText(""); heardText.setText("Último escrito: “"+s+"”"); process(s);}

    private void process(String raw){
        bubble(raw,true); String in=norm(raw), response;
        if(in.contains("otra receta")||in.contains("cambiar receta")){reset(); response="Claro. ¿Qué quieres cocinar ahora?";}
        else if(state==0){current=find(in); if(current==null)response="Todavía no tengo esa receta. Prueba con arroz chaufa, lomo saltado, ají de gallina, arroz con pollo, tallarín saltado, pollo al horno, causa, papa a la huancaína, estofado de pollo o ceviche."; else{state=1; response="Claro, prepararemos "+current.name+". ¿Para cuántas personas?";}}
        else if(state==1){int n=number(in); if(n<1||n>20)response="Dime un número entre 1 y 20. Por ejemplo: para 4 personas."; else{servings=n; state=2; response="Perfecto. "+ingredients()+" Cuando estés listo, dime comenzar.";}}
        else if(in.contains("ingrediente")||in.contains("que necesito"))response=ingredients();
        else if(in.contains("cuanto")||in.contains("cantidad"))response=amount(in);
        else if(state==2){if(in.contains("comenz")||in.contains("empez")||in.equals("listo")||in.equals("vamos")){state=3;step=0;response=currentStep();}else response="Cuando quieras iniciar, dime comenzar.";}
        else {if(in.contains("repet"))response=currentStep(); else if(in.contains("anterior")){if(step>0)step--;response=currentStep();} else if(in.contains("siguiente")||in.contains("sigue")||in.contains("ya esta")||in.equals("listo")){if(step<current.steps.length-1){step++;response=currentStep();}else{response="¡Listo! Terminamos "+current.name+". Buen provecho. Si quieres otra receta, dímelo."; reset();}} else response="Puedes decir siguiente, repetir, anterior, ingredientes o preguntarme una cantidad.";}
        updateStatus(); say(response,true);
    }

    private Recipe find(String in){for(Map.Entry<String,Recipe> e:recipes.entrySet())if(in.contains(e.getKey()))return e.getValue(); if(in.contains("chaufa"))return recipes.get("arroz chaufa"); if(in.contains("lomo"))return recipes.get("lomo saltado"); if(in.contains("gallina"))return recipes.get("aji de gallina"); if(in.contains("huancaina"))return recipes.get("papa a la huancaina"); if(in.contains("causa"))return recipes.get("causa rellena"); if(in.contains("estofado"))return recipes.get("estofado de pollo"); if(in.contains("ceviche"))return recipes.get("ceviche"); return null;}
    private int number(String in){for(String x:in.split("[^0-9]+"))if(!x.isEmpty())try{return Integer.parseInt(x);}catch(Exception ignored){} String[] w={"cero","uno","dos","tres","cuatro","cinco","seis","siete","ocho","nueve","diez","once","doce","trece","catorce","quince","dieciseis","diecisiete","dieciocho","diecinueve","veinte"}; for(int n=1;n<w.length;n++)if(in.contains(w[n]))return n; return -1;}
    private String ingredients(){if(current==null||servings<1)return "Primero necesito saber la receta y las personas."; double f=servings/4.0; StringBuilder b=new StringBuilder("Necesitaremos: "); for(int i=0;i<current.ingredients.length;i++){String[] x=current.ingredients[i]; if(i>0)b.append(i==current.ingredients.length-1?", y ":", "); b.append(fmt(Double.parseDouble(x[0])*f)).append(" ").append(x[1]).append(" de ").append(x[2]);} return b.append(".").toString();}
    private String amount(String in){if(current==null||servings<1)return "Primero dime para cuántas personas."; double f=servings/4.0; for(String[] x:current.ingredients){String n=norm(x[2]); String first=n.split(" ")[0]; if(in.contains(n)||in.contains(first))return "Para "+servings+" personas necesitas "+fmt(Double.parseDouble(x[0])*f)+" "+x[1]+" de "+x[2]+".";} return "Dime el ingrediente que quieres consultar.";}
    private String currentStep(){return "Paso "+(step+1)+" de "+current.steps.length+": "+current.steps[step]+" Cuando termines, dime siguiente.";}
    private void reset(){current=null;servings=0;step=0;state=0;}
    private void updateStatus(){if(current==null)contextText.setText("Esperando receta"); else if(servings==0)contextText.setText(current.name+" · faltan personas"); else contextText.setText(current.name+" · "+servings+" personas · "+(state==3?"cocinando":"listo"));}

    private void say(String m,boolean speak){bubble(m,false); if(speak&&ttsReady)tts.speak(m,TextToSpeech.QUEUE_FLUSH,null,"chef_response"); else if(speak&&!ttsReady)listeningText.setText("Respuesta visible; la voz del teléfono no está disponible todavía");}
    private void bubble(String m,boolean user){TextView b=txt(m,16,user?Color.WHITE:TEXT,false); b.setPadding(dp(14),dp(11),dp(14),dp(11)); b.setBackground(round(user?GREEN:Color.WHITE,18)); b.setMaxWidth(dp(315)); LinearLayout line=new LinearLayout(this); line.setGravity(user?Gravity.END:Gravity.START); LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2); p.setMargins(0,dp(4),0,dp(4)); line.addView(b); messages.addView(line,p); chatScroll.post(()->chatScroll.fullScroll(View.FOCUS_DOWN));}
    private void addMessage(String m,boolean user){bubble(m,user);}

    @Override public void onInit(int status){if(status==TextToSpeech.SUCCESS){int r=tts.setLanguage(new Locale("es","PE")); tts.setSpeechRate(.95f); ttsReady=r!=TextToSpeech.LANG_MISSING_DATA&&r!=TextToSpeech.LANG_NOT_SUPPORTED; if(ttsReady&&!greetingSpoken){greetingSpoken=true; tts.speak("Hola. Soy Chef Asistente. ¿Qué quieres cocinar hoy?",TextToSpeech.QUEUE_FLUSH,null,"chef_greeting");}}}
    @Override protected void onDestroy(){if(tts!=null){tts.stop();tts.shutdown();}super.onDestroy();}
    private TextView txt(String s,int sp,int color,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);v.setTextColor(color);if(bold)v.setTypeface(Typeface.DEFAULT_BOLD);return v;}
    private GradientDrawable round(int color,int r){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(r));return g;}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private String fmt(double v){if(Math.abs(v-Math.rint(v))<.0001)return String.valueOf((int)Math.rint(v));return String.format(Locale.US,"%.1f",v).replace(".0","").replace(".",",");}
    private String norm(String s){String n=Normalizer.normalize(s.toLowerCase(new Locale("es","PE")),Normalizer.Form.NFD);return n.replaceAll("\\p{M}","").trim();}
}
