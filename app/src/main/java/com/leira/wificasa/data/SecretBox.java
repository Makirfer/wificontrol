package com.leira.wificasa.data;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Cifra textos con una clave AES-256 que vive en el Android Keystore: la clave no sale
 * nunca del almacén seguro del móvil, así que la contraseña del router no queda en texto
 * plano en los archivos de la app.
 */
final class SecretBox {

    private static final String KEYSTORE = "AndroidKeyStore";
    private static final String ALIAS = "wificasa.credenciales";
    private static final String TRANSFORM = "AES/GCM/NoPadding";
    private static final String PREFIX = "v1:";

    private SecretBox() { }

    static String encrypt(String plain) throws Exception {
        if (plain == null || plain.isEmpty()) return "";
        Cipher c = Cipher.getInstance(TRANSFORM);
        c.init(Cipher.ENCRYPT_MODE, key());
        byte[] ct = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
        Base64.Encoder b = Base64.getEncoder();
        return PREFIX + b.encodeToString(c.getIV()) + ":" + b.encodeToString(ct);
    }

    static String decrypt(String stored) throws Exception {
        if (stored == null || stored.isEmpty()) return "";
        if (!stored.startsWith(PREFIX)) throw new IllegalArgumentException("Formato desconocido");
        String[] parts = stored.substring(PREFIX.length()).split(":", 2);
        Base64.Decoder b = Base64.getDecoder();
        Cipher c = Cipher.getInstance(TRANSFORM);
        c.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, b.decode(parts[0])));
        return new String(c.doFinal(b.decode(parts[1])), StandardCharsets.UTF_8);
    }

    private static SecretKey key() throws Exception {
        KeyStore ks = KeyStore.getInstance(KEYSTORE);
        ks.load(null);
        KeyStore.Entry e = ks.getEntry(ALIAS, null);
        if (e instanceof KeyStore.SecretKeyEntry) return ((KeyStore.SecretKeyEntry) e).getSecretKey();
        KeyGenerator g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE);
        g.init(new KeyGenParameterSpec.Builder(ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build());
        return g.generateKey();
    }
}
