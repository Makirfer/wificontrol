package com.leira.wificasa.data;

import android.content.Context;

import com.leira.wificasa.core.AdapterRegistry;
import com.leira.wificasa.core.recipe.Recipe;
import com.leira.wificasa.core.recipe.RecipeAdapter;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Recetas disponibles en el móvil: las incluidas en la app (assets/recetas) y las instaladas
 * por el usuario (importadas o descargadas del catálogo), guardadas en la carpeta privada de
 * la app. Al registrarse, una instalada sustituye a la incluida con el mismo id.
 */
public final class RecipeStore {

    private final Context ctx;
    private final File dir;

    public RecipeStore(Context ctx) {
        this.ctx = ctx;
        this.dir = new File(ctx.getFilesDir(), "recetas");
    }

    /** Registra todas las recetas válidas. Las que no pasen la validación se ignoran. */
    public void registerAll() {
        try {
            String[] files = ctx.getAssets().list("recetas");
            if (files != null) {
                for (String f : files) {
                    if (!f.endsWith(".json")) continue;
                    try (InputStream in = ctx.getAssets().open("recetas/" + f)) {
                        register(readAll(in));
                    } catch (Exception ignored) {
                        // receta incluida no válida
                    }
                }
            }
        } catch (IOException ignored) {
            // sin recetas incluidas
        }
        File[] installed = dir.listFiles();
        if (installed == null) return;
        for (File f : installed) {
            if (!f.getName().endsWith(".json")) continue;
            try (InputStream in = new FileInputStream(f)) {
                register(readAll(in));
            } catch (Exception ignored) {
                // receta instalada dañada
            }
        }
    }

    /** Valida, guarda y registra una receta. Devuelve el adaptador listo para usar. */
    public RecipeAdapter install(String text) throws Exception {
        Recipe r = Recipe.parse(text);
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("No se puede crear la carpeta de recetas.");
        File f = new File(dir, r.id() + ".json");
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        }
        return register(text);
    }

    /** Texto de una receta instalada (para publicarla), o null si es una incluida en la app. */
    public String installedText(String recipeId) {
        File f = new File(dir, recipeId + ".json");
        if (!f.exists()) return null;
        try (InputStream in = new FileInputStream(f)) {
            return readAll(in);
        } catch (IOException e) {
            return null;
        }
    }

    private static RecipeAdapter register(String text) throws Exception {
        RecipeAdapter a = new RecipeAdapter(Recipe.parse(text));
        AdapterRegistry.register(a);
        return a;
    }

    static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
        return bo.toString("UTF-8");
    }
}
