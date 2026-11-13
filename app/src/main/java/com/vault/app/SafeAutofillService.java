package com.vault.app;

import android.app.assist.AssistStructure;
import android.os.CancellationSignal;
import android.service.autofill.AutofillService;
import android.service.autofill.Dataset;
import android.service.autofill.FillCallback;
import android.service.autofill.FillRequest;
import android.service.autofill.FillResponse;
import android.service.autofill.SaveCallback;
import android.service.autofill.SaveRequest;
import android.text.InputType;
import android.view.View;
import android.view.autofill.AutofillId;
import android.view.autofill.AutofillValue;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

public class SafeAutofillService extends AutofillService {

    @Override
    public void onFillRequest(FillRequest request, CancellationSignal cs, FillCallback cb) {
        try {
            AssistStructure structure = request.getFillContext().getStructure();
            Found f = new Found();
            for (int w = 0; w < structure.getWindowNodeCount(); w++)
                scan(structure.getWindowNodeAt(w).getRootViewNode(), f);
            if (f.passId == null) { cb.onSuccess(null); return; }

            JSONArray matches = VaultCrypto.match(VaultCrypto.decryptVault(this), f.domain);
            if (matches.length() == 0) { cb.onSuccess(null); return; }

            FillResponse.Builder resp = new FillResponse.Builder();
            for (int i = 0; i < matches.length(); i++) {
                JSONObject e = matches.getJSONObject(i);
                String email = e.optString("email", "");
                String pass = e.optString("pass", "");
                if (pass.isEmpty()) continue;
                RemoteViews rv = new RemoteViews(getPackageName(), android.R.layout.simple_list_item_1);
                rv.setTextViewText(android.R.id.text1,
                    "\uD83D\uDD12 " + e.optString("name") + (email.isEmpty() ? "" : " \u00B7 " + email));
                Dataset.Builder d = new Dataset.Builder(rv);
                if (f.userId != null && !email.isEmpty())
                    d.setValue(f.userId, AutofillValue.forText(email));
                d.setValue(f.passId, AutofillValue.forText(pass));
                resp.addDataset(d.build());
            }
            cb.onSuccess(resp.build());
        } catch (Exception e) {
            cb.onSuccess(null);
        }
    }

    private void scan(AssistStructure.ViewNode n, Found f) {
        if (f.domain == null && n.getWebDomain() != null) f.domain = n.getWebDomain();
        String[] hints = n.getAutofillHints();
        int type = n.getInputType();
        boolean isPass = (type & InputType.TYPE_TEXT_VARIATION_PASSWORD) != 0
                || (type & InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD) != 0
                || (type & InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD) != 0
                || has(hints, View.AUTOFILL_HINT_PASSWORD);
        boolean isUser = has(hints, View.AUTOFILL_HINT_USERNAME)
                || has(hints, View.AUTOFILL_HINT_EMAIL_ADDRESS);
        if (f.passId == null && isPass) f.passId = n.getAutofillId();
        if (f.userId == null && isUser) f.userId = n.getAutofillId();
        for (int i = 0; i < n.getChildCount(); i++) scan(n.getChildAt(i), f);
    }

    private boolean has(String[] a, String s) {
        if (a == null) return false;
        for (String x : a) if (s.equalsIgnoreCase(x)) return true;
        return false;
    }

    @Override
    public void onSaveRequest(SaveRequest r, SaveCallback cb) { cb.onSuccess(); }

    private static class Found {
        AutofillId userId, passId;
        String domain;
    }
}
