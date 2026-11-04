package com.leira.wificasa.core.recipe;

import com.leira.wificasa.core.RouterException;

/** Receta mal formada o que intenta algo que el motor no permite. */
public class RecipeException extends RouterException {
    public RecipeException(String msg) { super(msg); }
}
