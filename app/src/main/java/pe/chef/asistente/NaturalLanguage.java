package pe.chef.asistente;

final class NaturalLanguage {
    private NaturalLanguage() {}

    static String interpret(String input, int state) {
        if (input == null) return "";
        String s = input.trim();
        if (s.isEmpty()) return s;

        // Al elegir receta o indicar personas conservamos la frase completa.
        if (state <= 1) return s;

        // Preguntas de cantidades deben conservar el nombre del ingrediente.
        if (isAmountQuestion(s)) return s;

        // Ingredientes / lista de compra.
        if (containsAny(s,
                "ingrediente", "que necesito", "que lleva", "que le pongo",
                "dime lo que necesito", "dime que necesito", "que tengo que comprar",
                "que compro", "lista de compra", "lista de compras", "dame la lista",
                "que cosas necesito", "que cosas lleva", "con que se hace")) {
            return "ingredientes";
        }

        // Preparación previa / cómo se hace.
        if (containsAny(s,
                "como se prepara", "como lo preparo", "como lo hago", "como se hace",
                "explicame como", "explica como", "que hago antes", "que debo hacer antes",
                "preparacion", "preparar antes", "antes de cocinar", "antes de empezar",
                "antes de comenzar")) {
            return "preparacion";
        }

        if (state == 2) {
            // Empezar a cocinar sin memorizar la palabra 'comenzar'.
            if (containsAny(s,
                    "ya tengo todo", "ya compre todo", "tengo todo", "tengo los ingredientes",
                    "ya estoy listo", "estoy listo", "estoy lista", "ya podemos cocinar",
                    "vamos a cocinar", "vamos con la receta", "vamos a hacerlo",
                    "podemos empezar", "podemos comenzar", "quiero empezar", "quiero comenzar",
                    "empecemos", "comencemos", "arranquemos", "arranca", "dale empecemos",
                    "dale comenzamos", "vamos", "dale")) {
                return "comenzar";
            }
            return s;
        }

        if (state == 3) {
            // Repetir el paso actual.
            if (containsAny(s,
                    "no entendi", "no te entendi", "no escuche", "no lo entendi",
                    "que dijiste", "como dijiste", "me lo repites", "me puedes repetir",
                    "otra vez", "repitelo", "repite", "dilo de nuevo", "vuelve a decir")) {
                return "repetir";
            }

            // Volver un paso.
            if (containsAny(s,
                    "paso anterior", "regresa", "retrocede", "vuelve atras", "volvamos atras",
                    "devuelvete", "devolvamos", "vuelve al paso", "regresa al paso",
                    "me perdi vuelve", "me perdi regresa")) {
                return "anterior";
            }

            // Pausar sin perder el punto de la receta.
            if (containsAny(s,
                    "pausa", "espera", "esperame", "dame un momento", "un momento",
                    "detente", "para un momento", "alto un momento", "dame un rato")) {
                return "pausa";
            }

            // Avanzar de forma natural.
            if (containsAny(s,
                    "ya esta", "ya quedo", "ya lo hice", "ya termine", "termine este paso",
                    "hecho", "listo", "ya lo tengo", "que sigue", "que hago ahora",
                    "que viene ahora", "cual es el siguiente", "vamos al siguiente",
                    "continuemos", "puedes seguir", "sigue", "continua", "adelante",
                    "vamos con el siguiente", "dale", "seguimos")) {
                return "siguiente";
            }
        }

        return s;
    }

    private static boolean isAmountQuestion(String s) {
        return containsAny(s,
                "cuanto", "cuanta", "cuantos", "cuantas", "cantidad de",
                "que cantidad", "cuanto le pongo", "cuanto pongo", "cuanto era",
                "cuanto necesito", "cuanto necesito de");
    }

    private static boolean containsAny(String value, String... options) {
        for (String option : options) {
            if (value.contains(option)) return true;
        }
        return false;
    }
}
