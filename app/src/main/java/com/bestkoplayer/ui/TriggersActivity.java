
package com.bestkoplayer.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.bestkoplayer.R;
import com.bestkoplayer.model.Trigger;
import com.bestkoplayer.model.TriggerMode;
import com.bestkoplayer.overlay.AppState;
import com.bestkoplayer.overlay.TriggerOverlayService;
import com.bestkoplayer.storage.ProfileStorage;

import java.util.ArrayList;
import java.util.List;

public class TriggersActivity extends Activity {

    private ArrayAdapter<Trigger> triggersAdapter;
    private final List<Trigger> triggerItems = new ArrayList<>();

    private TextView tvTargetApp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_triggers);

        Button btnBack = findViewById(R.id.btnBack);
        Button btnPickTargetApp = findViewById(R.id.btnPickTargetApp);
        Button btnNewTrigger = findViewById(R.id.btnNewTrigger);
        Button btnManageOcr = findViewById(R.id.btnManageOcr);
        Button btnStartOverlay = findViewById(R.id.btnStartOverlay);
        Button btnStopOverlay = findViewById(R.id.btnStopOverlay);

        tvTargetApp = findViewById(R.id.tvTargetApp);

        ListView lvTriggers = findViewById(R.id.lvTriggers);

        btnBack.setOnClickListener(v -> finish());
        btnPickTargetApp.setOnClickListener(v -> {
            startActivity(new Intent(this, TargetAppPickerActivity.class));
        });

        triggersAdapter = new ArrayAdapter<Trigger>(this, R.layout.row_item_with_delete, triggerItems) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View v = convertView;
                if (v == null) {
                    v = LayoutInflater.from(getContext()).inflate(R.layout.row_item_with_delete, parent, false);
                }
                TextView tv = v.findViewById(R.id.tvTitle);
                ImageButton del = v.findViewById(R.id.btnDelete);

                Trigger t = triggerItems.get(position);
                String label = (position+1) + ") " + (t.name == null ? "Trigger" : t.name)
                        + "  (" + t.x + "," + t.y + ")"
                        + "  mod=" + modeTr(t.mode)
                        + "  actions=" + (t.actions == null ? 0 : t.actions.size());
                tv.setText(label);

                v.setOnClickListener(vv -> openEditor(position));
                del.setOnClickListener(vv -> confirmDelete(position));

                return v;
            }
        };
        lvTriggers.setAdapter(triggersAdapter);

        btnNewTrigger.setOnClickListener(v -> openNewTrigger());
        btnManageOcr.setOnClickListener(v -> openOcrRules());
        btnStartOverlay.setOnClickListener(v -> startOverlay());
        btnStopOverlay.setOnClickListener(v -> stopOverlay());

        refresh();
    }

    private String modeTr(TriggerMode mode) {
        if (mode == null) return "Bir kere";
        switch (mode) {
            case HOLD:
                return "Basılı tut";
            case TOGGLE:
                return "Aç/Kapat";
            case ONCE:
            default:
                return "Bir kere";
        }
    }

    private boolean ensureOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            toast("Overlay izni yok. Profil ekranından aç.");
            return false;
        }
        return true;
    }

    private void openNewTrigger() {
        if (!ensureOverlayPermission()) return;
        startActivity(new Intent(this, NewTriggerActivity.class));
    }

    private void openOcrRules() {
        if (!ensureOverlayPermission()) return;
        startActivity(new Intent(this, OcrRulesActivity.class));
    }

    private void openEditor(int index) {
        Intent i = new Intent(this, TriggerEditorActivity.class);
        i.putExtra("triggerIndex", index);
        startActivity(i);
    }

    private void confirmDelete(int index) {
        new AlertDialog.Builder(this)
                .setTitle("Sil")
                .setMessage("Trigger silinsin mi?")
                .setPositiveButton("Sil", (d,w)->{
                    AppState.profile.triggers.remove(index);
                    saveProfile();
                    refresh();
                })
                .setNegativeButton("İptal", null)
                .show();
    }

    private void refresh() {
        triggerItems.clear();
        if (AppState.profile != null && AppState.profile.triggers != null) {
            triggerItems.addAll(AppState.profile.triggers);
        }
        triggersAdapter.notifyDataSetChanged();

        String pkg = (AppState.profile == null) ? "" : AppState.profile.targetPackageName;
        if (pkg == null || pkg.trim().isEmpty()) {
            tvTargetApp.setText("Hedef: - (seçilmedi)");
        } else {
            tvTargetApp.setText("Hedef: " + pkg);
        }
    }

    private void startOverlay() {
        if (!ensureOverlayPermission()) return;
        startService(new Intent(this, TriggerOverlayService.class));
        toast("Overlay başlatıldı");
    }

    private void stopOverlay() {
        stopService(new Intent(this, TriggerOverlayService.class));
        if (AppState.profile != null && AppState.profile.triggers != null) {
            for (Trigger t : AppState.profile.triggers) {
                t.isRunning = false;
            }
        }
        toast("Overlay kapatıldı / makrolar durduruldu");
    }

    private void saveProfile() {
        try {
            ProfileStorage.save(this, AppState.profile);
        } catch (Exception e) {
            toast("Kaydetme hatası: " + e.getMessage());
        }
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }
}
