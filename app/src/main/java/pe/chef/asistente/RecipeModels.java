package pe.chef.asistente;

import java.util.ArrayList;
import java.util.List;

public final class RecipeModels {
    private RecipeModels() {}

    public static final class Ingredient {
        public String name;
        public double quantity;
        public String unit;
        public String originalMeasure;
        public Ingredient(String name,double quantity,String unit){this.name=name;this.quantity=quantity;this.unit=unit;}
        public Ingredient(String name,String originalMeasure){this.name=name;this.originalMeasure=originalMeasure;this.quantity=Double.NaN;this.unit="";}
    }

    public static final class Step {
        public String title;
        public String instruction;
        public String duration;
        public Step(String title,String instruction,String duration){this.title=title;this.instruction=instruction;this.duration=duration;}
    }

    public static final class Recipe {
        public String key;
        public String name;
        public int baseServings=4;
        public boolean external=false;
        public String sourceLabel="Chef Asistente";
        public final List<String> aliases=new ArrayList<>();
        public final List<Ingredient> ingredients=new ArrayList<>();
        public final List<String> preparation=new ArrayList<>();
        public final List<Step> steps=new ArrayList<>();
    }
}
