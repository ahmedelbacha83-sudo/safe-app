package com.vault.app;

import android.app.Activity;
import android.content.SharedPreferences;
import android.hardware.biometrics.BiometricPrompt;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

public class MainActivity extends Activity {
    private WebView web;
    private CancellationSignal cancelSignal;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        web = new WebView(this);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setAllowFileAccess(true);
        web.setWebViewClient(new WebViewClient());
        web.addJavascriptInterface(new Bridge(), "SafeBio");
        web.loadUrl("file:///android_asset/index.html");
        setContentView(web);
    }

    /* ---------- biometric + keystore bridge exposed to JS as window.SafeBio ---------- */
    final class Bridge {

        @JavascriptInterface
        public boolean isAvailable() {
            if (Build.VERSION.SDK_INT < 28) return false;
            PackageManager pm = getPackageManager();
            if (pm.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)) return true;
            if (Build.VERSION.SDK_INT >= 29 &&
                (pm.hasSystemFeature(PackageManager.FEATURE_FACE) ||
                 pm.hasSystemFeature(PackageManager.FEATURE_IRIS))) return true;
            return false;
        }

        @JavascriptInterface
        public boolean hasSavedPin() {
            return prefs().contains("pin_iv");
        }

        @JavascriptInterface
        public void savePin(String pin) {
            try {
                Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
                c.init(Cipher.ENCRYPT_MODE, key());
                byte[] enc = c.doFinal(pin.getBytes(StandardCharsets.UTF_8));
                prefs().edit()
                    .putString("pin_iv", Base64.getEncoder().encodeToString(c.getIV()))
                    .putString("pin_data", Base64.getEncoder().encodeToString(enc))
                    .apply();
            } catch (Exception ignored) { }
        }

        @JavascriptInterface
        public void saveVault(String blobJson) {
            prefs().edit().putString("vault_mirror", blobJson).apply();
        }

        @JavascriptInterface
        public void requestAuth() {
            if (Build.VERSION.SDK_INT < 28) return;
            runOnUiThread(() -> {
                try {
                    cancelSignal = new CancellationSignal();
                    BiometricPrompt prompt = new BiometricPrompt.Builder(MainActivity.this)
                        .setTitle("Unlock Safe")
                        .setSubtitle("Confirm it's you")
                        .setNegativeButton("Use PIN", getMainExecutor(),
                            (DialogInterface d, int w) -> { })
                        .build();
                    prompt.authenticate(null, cancelSignal, getMainExecutor(),
                        new BiometricPrompt.AuthenticationCallback() {
                            @Override
                            public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult r) {
                                String pin = readPin();
                                if (pin != null) js("onBioUnlock('" + pin + "')");
                                else js("onBioError('no pin stored')");
                            }
                            @Override
                            public void onAuthenticationError(int code, CharSequence msg) {
                                js("onBioError('')");
                            }
                            @Override
                            public void onAuthenticationFailed() {
                                js("onBioError('')");
                            }
                        });
                } catch (Exception e) {
                    js("onBioError('')");
                }
            });
        }

        private String readPin() {
            try {
                byte[] iv = Base64.getDecoder().decode(prefs().getString("pin_iv", ""));
                byte[] data = Base64.getDecoder().decode(prefs().getString("pin_data", ""));
                Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
                c.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
                return new String(c.doFinal(data), StandardCharsets.UTF_8);
            } catch (Exception e) { return null; }
        }

        private SecretKey key() throws Exception {
            KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
            ks.load(null);
            if (!ks.containsAlias("safe_pin")) {
                KeyGenerator kg = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
                kg.init(new KeyGenParameterSpec.Builder(
                    "safe_pin", KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build());
                return kg.generateKey();
            }
            return ((KeyStore.SecretKeyEntry) ks.getEntry("safe_pin", null)).getSecretKey();
        }

        private SharedPreferences prefs() {
            return getSharedPreferences("safe", MODE_PRIVATE);
        }
    }

    private void js(String code) {
        web.post(() -> web.evaluateJavascript(code, null));
    }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack(); else super.onBackPressed();
    }
}
