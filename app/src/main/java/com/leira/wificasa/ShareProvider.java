package com.leira.wificasa;

import android.content.ClipData;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Comparte archivos de la app (grabaciones y recetas) con otras apps, por ejemplo la de una IA,
 * con su nombre real ("grabacion-askey-rtf8316vw-2026-11-04.txt"). Solo da acceso de lectura y
 * solo a los archivos que la propia app ha preparado para compartir.
 */
public class ShareProvider extends ContentProvider {

    static final String AUTHORITY = "com.leira.wificasa.compartir";
    /** Único archivo en el que se deja escribir: la foto de la pegatina que hace la cámara. */
    static final String PHOTO = "pegatina.jpg";

    /** Guarda el texto como archivo y abre el menú de compartir de Android. */
    static void shareText(Context c, String fileName, String text, String message, String chooserTitle)
            throws IOException {
        File dir = new File(c.getCacheDir(), "compartir");
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("No se puede preparar el archivo.");
        File f = new File(dir, safe(fileName));
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        }
        Uri uri = Uri.parse("content://" + AUTHORITY + "/" + f.getName());
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType(fileName.endsWith(".json") ? "application/json" : "text/plain");
        i.putExtra(Intent.EXTRA_STREAM, uri);
        if (message != null) i.putExtra(Intent.EXTRA_TEXT, message);
        i.putExtra(Intent.EXTRA_SUBJECT, fileName);
        i.setClipData(ClipData.newRawUri(fileName, uri));
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        c.startActivity(Intent.createChooser(i, chooserTitle));
    }

    static String safe(String name) {
        String n = name.replaceAll("[^A-Za-z0-9._-]", "-");
        return n.isEmpty() || n.startsWith(".") ? "archivo.txt" : n;
    }

    private File file(Uri uri) throws FileNotFoundException {
        String name = uri.getLastPathSegment();
        if (name == null || !name.equals(safe(name))) throw new FileNotFoundException();
        File f = new File(new File(getContext().getCacheDir(), "compartir"), name);
        if (!f.exists()) throw new FileNotFoundException();
        return f;
    }

    @Override public boolean onCreate() { return true; }

    @Override
    public String getType(Uri uri) {
        String n = uri.getLastPathSegment();
        if (PHOTO.equals(n)) return "image/jpeg";
        return n != null && n.endsWith(".json") ? "application/json" : "text/plain";
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (PHOTO.equals(uri.getLastPathSegment()) && mode.contains("w")) {
            File dir = new File(getContext().getCacheDir(), "compartir");
            if (!dir.exists() && !dir.mkdirs()) throw new FileNotFoundException();
            return ParcelFileDescriptor.open(new File(dir, PHOTO), ParcelFileDescriptor.MODE_READ_WRITE
                    | ParcelFileDescriptor.MODE_CREATE | ParcelFileDescriptor.MODE_TRUNCATE);
        }
        if (!"r".equals(mode)) throw new FileNotFoundException("Solo lectura");
        return ParcelFileDescriptor.open(file(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        File f;
        try {
            f = file(uri);
        } catch (FileNotFoundException e) {
            return null;
        }
        MatrixCursor c = new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
        c.addRow(new Object[]{f.getName(), f.length()});
        return c;
    }

    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String selection, String[] args) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { return 0; }
}
