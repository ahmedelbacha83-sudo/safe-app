package com.vault.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.Locale;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/** Decrypts the vault (mirrored from the WebView) for the autofill service.
 *  The PIN is read from the Android Keystore (only exists after the user
 *  enabled fingerprint/face unlock), so autofill only works after the
 *  user's identity has been confirmed at least once. */
public class VaultCrypto {

    public static JSONArray decryptVault(Context ctx) {
        try {
            String pin = readPin(ctx);
            String blob = ctx.getSharedPreferences("safe", Context.MODE_PRIVATE)
                    .getString("vault_mirror", null);
            if (pin == null || blob == null) return null;
            JSONObject o = new JSONObject(blob);
            byte[] salt = Base64.decode(o.getString("salt"), Base64.DEFAULT);
            byte[] iv = Base64.decode(o.getString("iv"), Base64.DEFAULT);
            byte[] data = Base64.decode(o.getString("data"), Base64.DEFAULT);
            PBEKeySpec spec = new PBEKeySpec(pin.toCharArray(), salt, 150000, 256);
            SecretKeyFactory f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            SecretKey key = new SecretKeySpec(f.generateSecret(spec).getEncoded(), "AES");
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
            return new JSONArray(new String(c.doFinal(data), StandardCharsets.UTF_8));
        } catch (Exception e) {
            return null;
        }
    }

    public static String readPin(Context ctx) {
        try {
            SharedPreferences p = ctx.getSharedPreferences("safe", Context.MODE_PRIVATE);
            String iv64 = p.getString("pin_iv", null);
            String data64 = p.getString("pin_data", null);
            if (iv64 == null || data64 == null) return null;
            KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
            ks.load(null);
            SecretKey key = ((KeyStore.SecretKeyEntry) ks.getEntry("safe_pin", null)).getSecretKey();
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, Base64.decode(iv64, Base64.DEFAULT)));
            return new String(c.doFinal(Base64.decode(data64, Base64.DEFAULT)), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    public static JSONArray match(JSONArray vault, String domain) {
        JSONArray out = new JSONArray();
        if (vault == null || domain == null) return out;
        String d = domain.toLowerCase(Locale.ROOT).replace("www.", "");
        String first = d.split("\\.")[0];
        for (int i = 0; i < vault.length(); i++) {
            try {
                JSONObject e = vault.getJSONObject(i);
                String host = hostOf(e.optString("url", "")).toLowerCase(Locale.ROOT);
                String name = e.optString("name", "").toLowerCase(Locale.ROOT);
                if ((host.isEmpty() && name.contains(first)) ||
                    (!host.isEmpty() && (d.contains(host) || host.contains(d))) ||
                    name.contains(first)) {
                    out.put(e);
                    if (out.length() >= 3) break;
                }
            } catch (Exception ignored) { }
        }
        return out;
    }

    public static String hostOf(String url) {
        try {
            String u = url.startsWith("http") ? url : "https://" + url;
            return new java.net.URL(u).getHost().replace("www.", "");
        } catch (Exception e) {
            return "";
        }
    }
}
