
package com.bestkoplayer.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.bestkoplayer.R;
import com.bestkoplayer.model.Profile;
import com.bestkoplayer.overlay.AppState;
import com.bestkoplayer.storage.ProfileStorage;

import java.util.ArrayList;
import java.util.List;

public class ProfilesActivity extends Activity {

    private static final String TAG = "BestKoPlayer";

    private ArrayAdapter<String> adapter;
    private final List<String> items = new ArrayList<>();

    private Button btnOverlayPermission;
    private Button btnAccessibilityPermission;
    private TextView tvAccessibilityState;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profiles);

        btnOverlayPermission = findViewById(R.id.btnOverlayPermission);
        btnAccessibilityPermission = findViewById(R.id.btnAccessibilityPermission);
        tvAccessibilityState = findViewById(R.id.tvAccessibilityState);
        Button btnNew = findViewById(R.id.btnNewProfile);
        ListView lv = findViewById(R.id.lvProfiles);

        adapter = new ArrayAdapter<String>(this, R.layout.row_item_with_delete, items) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View v = convertView;
                if (v == null) {
                    v = LayoutInflater.from(getContext()).inflate(R.layout.row_item_with_delete, parent, false);
                }
                TextView tv = v.findViewById(R.id.tvTitle);
                ImageButton del = v.findViewById(R.id.btnDelete);

                String name = items.get(position);
                tv.setText(name);

                v.setOnClickListener(vv -> openProfile(name));

                del.setOnClickListener(vv -> confirmDelete(name));

                return v;
            }
        };

        lv.setAdapter(adapter);

        btnNew.setOnClickListener(v -> showCreateDialog());
        btnOverlayPermission.setOnClickListener(v -> askOverlayPermission());
        if (btnAccessibilityPermission != null) {
            btnAccessibilityPermission.setOnClickListener(v -> openAccessibilitySettings());
        }

        refresh();
        refreshOverlayPermissionButton();
        refreshAccessibilityButton();
    }

    private void refreshOverlayPermissionButton() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            boolean allowed = Settings.canDrawOverlays(this);
            btnOverlayPermission.setVisibility(allowed ? View.GONE : View.VISIBLE);
        } else {
            btnOverlayPermission.setVisibility(View.GONE);
        }
    }

    private void refresh() {
        items.clear();
        items.addAll(ProfileStorage.listProfiles(this));
        if (items.isEmpty()) {
            try {
                Profile p = new Profile();
                p.name = "Default";
                ProfileStorage.save(this, p);
                items.add("Default");
            } catch (Exception ignored) {}
        }
        adapter.notifyDataSetChanged();
    }

    private void showCreateDialog() {
        EditText et = new EditText(this);
        et.setHint("Profil adı (ör: RiseFarm)");

        new AlertDialog.Builder(this)
                .setTitle("Yeni Profil")
                .setView(et)
                .setPositiveButton("Oluştur", (d, w) -> {
                    String name = et.getText().toString().trim();
                    if (name.isEmpty()) {
                        toast("Profil adı boş olamaz");
                        return;
                    }
                    try {
                        Profile p = new Profile();
                        p.name = name;
                        ProfileStorage.save(this, p);
                        refresh();
                        openProfile(name);
                    } catch (Exception e) {
                        toast("Hata: " + e.getMessage());
                    }
                })
                .setNegativeButton("İptal", null)
                .show();
    }

    private void confirmDelete(String name) {
        if ("Default".equals(name)) {
            toast("Default profili silinemez");
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Profili Sil")
                .setMessage(name + " silinsin mi?")
                .setPositiveButton("Sil", (d,w)->{
                    boolean ok = ProfileStorage.delete(this, name);
                    if (!ok) toast("Silinemedi: " + name);
                    refresh();
                })
                .setNegativeButton("İptal", null)
                .show();
    }

    private void askOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivity(intent);
                toast("Overlay izni ver");
            } else {
                toast("Overlay izni zaten var");
            }
        }
    }

    private void openAccessibilitySettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
        } catch (Exception e) {
            Log.d(TAG, "Accessibility settings açılamadı: " + e.getMessage());
        }
    }

    /**
     * Servis sınıf adını bilmeden, sistem ayarından kontrol eder.
     * ENABLED_ACCESSIBILITY_SERVICES içinde paket adımız geçiyorsa "enabled" kabul ederiz.
     */
    private boolean isAnyAccessibilityServiceFromThisAppEnabled() {
        try {
            int accEnabled = Settings.Secure.getInt(
                    getContentResolver(),
                    Settings.Secure.ACCESSIBILITY_ENABLED
            );
            if (accEnabled != 1) return false;

            String enabledServices = Settings.Secure.getString(
                    getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            );
            if (TextUtils.isEmpty(enabledServices)) return false;

            return enabledServices.toLowerCase().contains(getPackageName().toLowerCase());
        } catch (Exception e) {
            Log.d(TAG, "Accessibility check error: " + e.getMessage());
            return false;
        }
    }

    private void refreshAccessibilityButton() {
        boolean enabled = isAnyAccessibilityServiceFromThisAppEnabled();

        if (btnAccessibilityPermission != null) {
            btnAccessibilityPermission.setVisibility(enabled ? View.GONE : View.VISIBLE);
        }

        if (tvAccessibilityState != null) {
            tvAccessibilityState.setText(
                    enabled
                            ? "Durum: Erişilebilirlik servisi AÇIK"
                            : "Durum: Erişilebilirlik servisi KAPALI (Açmak için butona bas)"
            );
        }

        Log.d(TAG, "Accessibility enabled (package scan) = " + enabled);
    }

    private void openProfile(String name) {
        try {
            Profile p = ProfileStorage.load(this, name);
            if (p == null) p = new Profile();
            p.name = name;
            AppState.profile = p;
            AppState.activeProfileName = name;

            Intent i = new Intent(this, TriggersActivity.class);
            i.putExtra("profileName", name);
            startActivity(i);
        } catch (Exception e) {
            toast("Profil açılamadı: " + e.getMessage());
        }
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
        refreshOverlayPermissionButton();
        refreshAccessibilityButton();
    }
}
