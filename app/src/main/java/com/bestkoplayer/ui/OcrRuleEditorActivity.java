
package com.bestkoplayer.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.bestkoplayer.R;
import com.bestkoplayer.model.ClickAction;
import com.bestkoplayer.model.OcrRule;
import com.bestkoplayer.overlay.AppState;
import com.bestkoplayer.overlay.PointSelectionOverlay;
import com.bestkoplayer.storage.ProfileStorage;

import java.util.ArrayList;
import java.util.List;

public class OcrRuleEditorActivity extends Activity {

    private OcrRule rule;
    private int ruleIndex;

    private ArrayAdapter<String> adapter;
    private final List<String> items = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ocr_rule_editor);

        ruleIndex = getIntent().getIntExtra("ruleIndex", -1);

        if (AppState.profile == null) {
            toast("Profil yok");
            finish();
            return;
        }

        if (ruleIndex >= 0 && ruleIndex < AppState.profile.ocrRules.size()) {
            rule = AppState.profile.ocrRules.get(ruleIndex);
        } else {
            rule = new OcrRule();
            AppState.profile.ocrRules.add(rule);
            ruleIndex = AppState.profile.ocrRules.size() - 1;
        }

        Button btnBack = findViewById(R.id.btnBack);
        Button btnRename = findViewById(R.id.btnRename);
        Button btnPickArea = findViewById(R.id.btnPickArea);
        Button btnSetThreshold = findViewById(R.id.btnSetThreshold);
        Button btnAddAction = findViewById(R.id.btnAddAction);
        Button btnSave = findViewById(R.id.btnSave);

        TextView tvHeader = findViewById(R.id.tvHeader);

        ListView lv = findViewById(R.id.lvActions);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, items);
        lv.setAdapter(adapter);

        btnBack.setOnClickListener(v -> finish());
        btnRename.setOnClickListener(v -> rename());
        btnPickArea.setOnClickListener(v -> pickArea());
        btnSetThreshold.setOnClickListener(v -> setThreshold());
        btnAddAction.setOnClickListener(v -> addAction());
        btnSave.setOnClickListener(v -> save());

        lv.setOnItemClickListener((p,v,pos,id)-> editDelay(pos));
        lv.setOnItemLongClickListener((p,v,pos,id)->{
            new AlertDialog.Builder(this)
                    .setTitle("Sil")
                    .setMessage("Bu işlem silinsin mi?")
                    .setPositiveButton("Sil", (d,w)->{
                        rule.actions.remove(pos);
                        refreshActions();
                    })
                    .setNegativeButton("İptal", null)
                    .show();
            return true;
        });

        tvHeader.setText("OCR Kuralı: " + (rule.name == null ? "OCR Rule" : rule.name));
        refreshActions();
    }

    private boolean ensureOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            toast("Overlay izni yok. Profil ekranından aç.");
            return false;
        }
        return true;
    }

    private boolean autoLaunchTargetAppIfSet() {
        String pkg = (AppState.profile == null) ? "" : AppState.profile.targetPackageName;
        if (pkg == null || pkg.trim().isEmpty()) return false;

        try {
            PackageManager pm = getPackageManager();
            Intent launch = pm.getLaunchIntentForPackage(pkg);
            if (launch == null) return false;
            startActivity(launch);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void pickArea() {
        if (!ensureOverlayPermission()) return;

        boolean launched = autoLaunchTargetAppIfSet();
        toast(launched
                ? "Hedef uygulama açılıyor… OCR alanı için sol-üst noktayı seç"
                : "OCR alanı için sol-üst noktayı seç (hedef uygulama seçili değil)");

        new Handler(Looper.getMainLooper()).postDelayed(() ->
                new PointSelectionOverlay(this, (x1,y1)->{
                    toast("OCR alanı için sağ-alt noktayı seç");
                    new PointSelectionOverlay(this, (x2,y2)->{
                        int left = Math.min(x1, x2);
                        int top = Math.min(y1, y2);
                        int w = Math.max(1, Math.abs(x2 - x1));
                        int h = Math.max(1, Math.abs(y2 - y1));

                        rule.x = left;
                        rule.y = top;
                        rule.width = w;
                        rule.height = h;

                        toast("OCR alanı kaydedildi: ("+left+","+top+") "+w+"x"+h);
                    });
                }), launched ? 650 : 0);
    }

    private void setThreshold() {
        EditText et = new EditText(this);
        et.setHint("Eşik değer (ör: 300)");
        et.setText(String.valueOf(rule.thresholdValue));

        new AlertDialog.Builder(this)
                .setTitle("OCR eşik değeri")
                .setView(et)
                .setPositiveButton("Kaydet", (d,w)->{
                    rule.thresholdValue = parseIntSafe(et.getText().toString().trim(), rule.thresholdValue);
                    toast("Threshold kaydedildi");
                })
                .setNegativeButton("İptal", null)
                .show();
    }

    private void addAction() {
        if (!ensureOverlayPermission()) return;

        boolean launched = autoLaunchTargetAppIfSet();
        toast(launched
                ? "Hedef uygulama açılıyor… Tıklayacağınız alanı seçin"
                : "Tıklayacağınız alanı seçin (hedef uygulama seçili değil)");

        new Handler(Looper.getMainLooper()).postDelayed(() ->
                new PointSelectionOverlay(this, (x,y)->{
                    EditText et = new EditText(this);
                    et.setHint("Delay ms (ör: 50)");
                    et.setText("50");

                    new AlertDialog.Builder(this)
                            .setTitle("Delay (ms)")
                            .setView(et)
                            .setPositiveButton("Ekle", (d,w)->{
                                int ms = parseIntSafe(et.getText().toString().trim(), 50);
                                rule.actions.add(new ClickAction(x, y, ms));
                                refreshActions();
                            })
                            .setNegativeButton("İptal", null)
                            .show();
                }), launched ? 650 : 0);
    }

    private void editDelay(int index) {
        ClickAction a = rule.actions.get(index);
        EditText et = new EditText(this);
        et.setHint("Delay ms");
        et.setText(String.valueOf(a.delayMs));

        new AlertDialog.Builder(this)
                .setTitle("Delay düzenle (ms)")
                .setView(et)
                .setPositiveButton("Kaydet", (d,w)->{
                    a.delayMs = Math.max(0, parseIntSafe(et.getText().toString().trim(), a.delayMs));
                    refreshActions();
                })
                .setNegativeButton("İptal", null)
                .show();
    }

    private void rename() {
        EditText et = new EditText(this);
        et.setHint("Kural adı");
        et.setText(rule.name == null ? "" : rule.name);

        new AlertDialog.Builder(this)
                .setTitle("OCR Kural adı")
                .setView(et)
                .setPositiveButton("Kaydet", (d,w)->{
                    String n = et.getText().toString().trim();
                    if (n.isEmpty()) n = "OCR Rule";
                    rule.name = n;
                    toast("Kaydedildi: " + n);
                })
                .setNegativeButton("İptal", null)
                .show();
    }

    private void refreshActions() {
        items.clear();
        for (int i=0;i<rule.actions.size();i++) {
            ClickAction a = rule.actions.get(i);
            items.add((i+1) + ") (" + a.x + "," + a.y + ") delay=" + a.delayMs + "ms");
        }
        adapter.notifyDataSetChanged();
    }

    private void save() {
        try {
            ProfileStorage.save(this, AppState.profile);
            toast("Kaydedildi");
        } catch (Exception e) {
            toast("Hata: " + e.getMessage());
        }
    }

    private int parseIntSafe(String s, int def) {
        try { return Integer.parseInt(s); } catch (Exception e) { return def; }
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}
