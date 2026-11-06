package com.makirfer.wifitimecontrol.core.catalog;

/** Dónde vive el catálogo público de recetas (un repositorio de GitHub). */
public final class CatalogConfig {
    /** Usuario u organización de GitHub dueño del repositorio. */
    public static String OWNER = "Makirfer";
    /** Nombre del repositorio. */
    public static String REPO = "wificontrol";
    public static String BRANCH = "main";

    private CatalogConfig() { }

    public static boolean configured() { return !OWNER.equals("PENDIENTE"); }

    /** Base para descargar archivos del catálogo (las pruebas la cambian por un servidor local). */
    public static String rawBase() {
        return "https://raw.githubusercontent.com/" + OWNER + "/" + REPO + "/" + BRANCH + "/catalogo/";
    }

    public static String repoUrl() { return "https://github.com/" + OWNER + "/" + REPO; }
}
