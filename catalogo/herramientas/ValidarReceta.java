import com.leira.wificasa.core.recipe.Recipe;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/** Valida recetas con el MISMO validador que usa la app. Uso: java ValidarReceta receta.json [...] */
public class ValidarReceta {
    public static void main(String[] args) throws Exception {
        int fallos = 0;
        for (String a : args) {
            String text = new String(Files.readAllBytes(Paths.get(a)), StandardCharsets.UTF_8);
            try {
                Recipe r = Recipe.parse(text);
                System.out.println("OK " + a + " · " + r.displayName() + " " + r.version());
            } catch (Exception e) {
                System.out.println("ERROR " + a + " · " + e.getMessage());
                fallos++;
            }
        }
        System.exit(fallos == 0 ? 0 : 1);
    }
}
