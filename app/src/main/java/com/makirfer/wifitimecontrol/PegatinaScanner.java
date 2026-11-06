package com.makirfer.wifitimecontrol;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.provider.MediaStore;
import android.widget.Toast;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.makirfer.wifitimecontrol.core.learn.Pegatina;

import java.io.File;
import java.io.InputStream;

/**
 * Lee la pegatina del router con una foto. El texto se reconoce en el propio móvil (ML Kit,
 * sin conexión) y la foto no se envía a ningún sitio; se borra al terminar.
 * Uso: {@link #scan} y reenviar {@link #onActivityResult} desde la actividad.
 */
final class PegatinaScanner {

    interface Callback { void onRead(Pegatina p); }

    static final int REQ_CAMERA = 54, REQ_GALLERY = 55;

    private final Activity a;
    private Callback pending;

    PegatinaScanner(Activity a) { this.a = a; }

    private File photo() {
        File dir = new File(a.getCacheDir(), "compartir");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, ShareProvider.PHOTO);
    }

    /** Pregunta si hacer una foto o elegir una de la galería, y devuelve lo leído al callback. */
    void scan(Callback cb) {
        pending = cb;
        String[] items = { a.getString(R.string.photo_take), a.getString(R.string.photo_pick) };
        new AlertDialog.Builder(a)
                .setTitle(a.getString(R.string.photo_title))
                .setItems(items, (d, w) -> {
                    try {
                        if (w == 0) {
                            File f = photo();
                            if (f.exists()) f.delete();
                            Uri out = Uri.parse("content://" + ShareProvider.AUTHORITY + "/" + ShareProvider.PHOTO);
                            Intent i = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                            i.putExtra(MediaStore.EXTRA_OUTPUT, out);
                            i.setClipData(ClipData.newRawUri("foto", out));
                            i.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            a.startActivityForResult(i, REQ_CAMERA);
                        } else {
                            Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                            i.setType("image/*");
                            i.addCategory(Intent.CATEGORY_OPENABLE);
                            a.startActivityForResult(i, REQ_GALLERY);
                        }
                    } catch (Exception e) {
                        Toast.makeText(a, a.getString(R.string.photo_no_camera), Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton(a.getString(R.string.cancel), null)
                .show();
    }

    /** true si el resultado era de este escáner. */
    boolean onActivityResult(int req, int res, Intent data) {
        if (req != REQ_CAMERA && req != REQ_GALLERY) return false;
        if (res != Activity.RESULT_OK || pending == null) return true;
        Uri src = req == REQ_CAMERA ? Uri.fromFile(photo()) : (data == null ? null : data.getData());
        if (src == null) return true;
        final Callback cb = pending;
        try {
            Bitmap bmp = load(src);
            if (bmp == null) throw new Exception("imagen vacía");
            final AlertDialog wait = new AlertDialog.Builder(a).setMessage(a.getString(R.string.photo_reading))
                    .setCancelable(false).show();
            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                    .process(InputImage.fromBitmap(bmp, 0))
                    .addOnSuccessListener(text -> {
                        wait.dismiss();
                        cleanup();
                        review(text.getText(), cb);
                    })
                    .addOnFailureListener(e -> {
                        wait.dismiss();
                        cleanup();
                        Toast.makeText(a, a.getString(R.string.photo_error, String.valueOf(e.getMessage())),
                                Toast.LENGTH_LONG).show();
                    });
        } catch (Exception e) {
            cleanup();
            Toast.makeText(a, a.getString(R.string.photo_error, String.valueOf(e.getMessage())), Toast.LENGTH_LONG).show();
        }
        return true;
    }

    /**
     * Enseña lo leído antes de usarlo. «Copiar lo que se ha leído» copia el texto reconocido, con la
     * contraseña tapada, para poder mandarlo y mejorar la lectura de ese tipo de pegatina.
     */
    private void review(String raw, Callback cb) {
        Pegatina p = Pegatina.parse(raw);
        StringBuilder sb = new StringBuilder();
        line(sb, R.string.photo_vendor, p.vendor);
        line(sb, R.string.photo_model, p.model);
        line(sb, R.string.photo_isp, p.isp);
        line(sb, R.string.label_host, p.host);
        line(sb, R.string.label_user, p.user);
        line(sb, R.string.password, p.password);
        sb.append("\n").append(a.getString(p.isEmpty() ? R.string.photo_nothing : R.string.photo_check));
        String masked = p.password.isEmpty() ? raw : raw.replace(p.password, "«CONTRASEÑA»");
        AlertDialog.Builder b = new AlertDialog.Builder(a)
                .setTitle(a.getString(R.string.photo_title))
                .setMessage(sb.toString().trim())
                .setNeutralButton(a.getString(R.string.photo_copy_raw), (d, w) -> {
                    android.content.ClipboardManager cm =
                            (android.content.ClipboardManager) a.getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                    if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("pegatina", masked));
                    Toast.makeText(a, a.getString(R.string.copied), Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(a.getString(R.string.cancel), null);
        if (!p.isEmpty()) b.setPositiveButton(a.getString(R.string.photo_use), (d, w) -> cb.onRead(p));
        b.show();
    }

    private void line(StringBuilder sb, int label, String value) {
        sb.append(a.getString(label)).append(": ").append(value.isEmpty() ? "—" : value).append("\n");
    }

    private void cleanup() {
        File f = photo();
        if (f.exists()) f.delete();
    }

    /** Carga la foto reducida (unos 2000 px) y girada según la orientación con que se hizo. */
    private Bitmap load(Uri uri) throws Exception {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        try (InputStream in = open(uri)) { BitmapFactory.decodeStream(in, null, o); }
        int sample = 1;
        while (Math.max(o.outWidth, o.outHeight) / sample > 2200) sample *= 2;
        BitmapFactory.Options o2 = new BitmapFactory.Options();
        o2.inSampleSize = sample;
        Bitmap bmp;
        try (InputStream in = open(uri)) { bmp = BitmapFactory.decodeStream(in, null, o2); }
        if (bmp == null) return null;
        int rot = 0;
        try (InputStream in = open(uri)) {
            int ori = new ExifInterface(in).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            rot = ori == ExifInterface.ORIENTATION_ROTATE_90 ? 90 : ori == ExifInterface.ORIENTATION_ROTATE_180 ? 180
                    : ori == ExifInterface.ORIENTATION_ROTATE_270 ? 270 : 0;
        } catch (Exception ignored) {
            // sin datos de orientación
        }
        if (rot == 0) return bmp;
        Matrix m = new Matrix();
        m.postRotate(rot);
        return Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), m, true);
    }

    private InputStream open(Uri uri) throws Exception {
        if ("file".equals(uri.getScheme())) return new java.io.FileInputStream(new File(uri.getPath()));
        return a.getContentResolver().openInputStream(uri);
    }
}
