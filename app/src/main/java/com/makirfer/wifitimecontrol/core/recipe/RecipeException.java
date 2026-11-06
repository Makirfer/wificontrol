package com.makirfer.wifitimecontrol.core.recipe;

import com.makirfer.wifitimecontrol.core.RouterException;

/** Receta mal formada o que intenta algo que el motor no permite. */
public class RecipeException extends RouterException {
    public RecipeException(String msg) { super(msg); }
}
