package pe.chef.asistente;

public final class OnlineRecipeService {
    public interface Callback {
        void onResult(RecipeStore.Recipe recipe, String error);
    }
}
