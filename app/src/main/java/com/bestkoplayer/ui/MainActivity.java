
package com.bestkoplayer.ui;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityManager;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.bestkoplayer.R;
import com.bestkoplayer.BestKoAccessibilityService;
import com.bestkoplayer.model.ClickAction;
import com.bestkoplayer.model.Profile;
import com.bestkoplayer.model.Trigger;
import com.bestkoplayer.overlay.AppState;
import com.bestkoplayer.overlay.PointSelectionOverlay;
import com.bestkoplayer.overlay.TriggerOverlayService;
import com.bestkoplayer.overlay.TriggerPreviewOverlay;
import com.bestkoplayer.storage.ProfileStorage;

public class MainActivity extends Activity {

    private TextView tvState;
    private TextView tvSizeValue;
    private TextView tvOpacityValue;

    private int triggerSizePx = 120;
    private float triggerOpacity = 0.4f;

    private Trigger currentTrigger = null;
    private TriggerPreviewOverlay lastPreview = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvState = findViewById(R.id.tvState);
        tvSizeValue = findViewById(R.id.tvSizeValue);
        tvOpacityValue = findViewById(R.id.tvOpacityValue);

        Button btnOverlayPermission = findViewById(R.id.btnOverlayPermission);
        Button btnAccessibilityPermission = findViewById(R.id.btnAccessibilityPermission);
        Button btnAddTrigger = findViewById(R.id.btnAddTrigger);
        Button btnUndoTrigger = findViewById(R.id.btnUndoTrigger);
        Button btnAddClickPoint = findViewById(R.id.btnAddClickPoint);
        Button btnStartOverlay = findViewById(R.id.btnStartOverlay);
        Button btnSaveProfile = findViewById(R.id.btnSaveProfile);
        Button btnLoadProfile = findViewById(R.id.btnLoadProfile);

        SeekBar sbSize = findViewById(R.id.sbSize);
        SeekBar sbOpacity = findViewById(R.id.sbOpacity);

        // size: min 30px, max 260+30=290
        sbSize.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                triggerSizePx = Math.max(30, progress);
                tvSizeValue.setText("Boyut: " + triggerSizePx + "px");
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        sbOpacity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                // 0..100 -> 0.05..1.0 (çok kaybolmasın diye min 0.05)
                triggerOpacity = Math.max(0.05f, progress / 100f);
                tvOpacityValue.setText("Opaklık: " + progress + "%");
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        btnOverlayPermission.setOnClickListener(v -> askOverlayPermission());
        btnAccessibilityPermission.setOnClickListener(v -> openAccessibilitySettings());
        btnAddTrigger.setOnClickListener(v -> pickTriggerPoint());
        btnUndoTrigger.setOnClickListener(v -> undoLastTrigger());
        btnAddClickPoint.setOnClickListener(v -> pickClickPoint());
        btnStartOverlay.setOnClickListener(v -> startOverlay());
        btnSaveProfile.setOnClickListener(v -> saveProfile());
        btnLoadProfile.setOnClickListener(v -> loadProfile());

        // initial labels
        tvSizeValue.setText("Boyut: " + triggerSizePx + "px");
        tvOpacityValue.setText("Opaklık: " + (int)(triggerOpacity * 100) + "%");

        // İlk açılışta da buton görünürlüğünü ayarla
        updateAccessibilityButtonVisibility();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Kullanıcı ayarlardan geri döndüğünde otomatik güncellensin
        updateAccessibilityButtonVisibility();
    }

    private void openAccessibilitySettings() {
        try {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            toast("Erişilebilirlik ekranından BestKoPlayer servisinin 'Açık' olduğundan emin ol");
        } catch (Exception e) {
            toast("Erişilebilirlik ekranı açılamadı: " + e.getMessage());
        }
    }

    private void updateAccessibilityButtonVisibility() {
        Button btn = findViewById(R.id.btnAccessibilityPermission);
        if (btn == null) return;

        // Hem Settings üzerinden "enabled" kontrol et, hem de sistemin gerçekten aktif gördüğü servis listesine bak.
        boolean enabled = isAccessibilityServiceEnabledViaManager() || isAccessibilityServiceEnabled(BestKoAccessibilityService.class);
        boolean connected = BestKoAccessibilityService.isConnected();

        // Servis açık değilse veya açık görünüp bağlı değilse, butonu göster.
        boolean shouldShow = !enabled || !connected;

        // Durum metni
        if (tvState != null) {
            tvState.setText(connected ? "Durum: Erişilebilirlik servisi AÇIK" : "Durum: Erişilebilirlik servisi KAPALI (Ayarlar'dan aç)");
        }

        btn.setVisibility(shouldShow ? View.VISIBLE : View.GONE);
    }

    private boolean isAccessibilityServiceEnabledViaManager() {
        try {
            AccessibilityManager am = (AccessibilityManager) getSystemService(ACCESSIBILITY_SERVICE);
            if (am == null) return false;

            String expectedId = getPackageName() + "/" + BestKoAccessibilityService.class.getName();
            for (AccessibilityServiceInfo s : am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)) {
                if (s != null && expectedId.equals(s.getId())) return true;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isAccessibilityServiceEnabled(Class<?> serviceClass) {
        try {
            String enabledServices = Settings.Secure.getString(
                    getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            );
            if (TextUtils.isEmpty(enabledServices)) return false;

            String expected = getPackageName() + "/" + serviceClass.getName();
            // Bazı cihazlar kısa isim döndürebiliyor; hem tam hem kısa kontrol edelim.
            String expectedAlt = getPackageName() + "/." + serviceClass.getSimpleName();

            return enabledServices.contains(expected) || enabledServices.contains(expectedAlt);
        } catch (Exception ignored) {
            return false;
        }
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

    private boolean ensureOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            toast("Önce Overlay izni ver");
            return false;
        }
        return true;
    }

    private void pickTriggerPoint() {
        if (!ensureOverlayPermission()) return;

        toast("Ekranda trigger noktasını seç");
        new PointSelectionOverlay(this, (x, y) -> {

            // Remove old preview if exists
            if (lastPreview != null) {
                lastPreview.remove();
                lastPreview = null;
            }

            // Create a preview circle so user can see it instantly
            lastPreview = new TriggerPreviewOverlay(this, x, y, triggerSizePx, triggerOpacity);

            Trigger t = new Trigger();
            t.x = x;
            t.y = y;
            t.widthPx = triggerSizePx;
            t.heightPx = triggerSizePx;
            t.opacity = triggerOpacity;

            AppState.profile.triggers.add(t);
            currentTrigger = t;

            setState("Trigger eklendi: (" + x + "," + y + "). Görünür preview oluşturuldu. İstersen 'geri al' yap.");
        });
    }

    private void undoLastTrigger() {
        if (AppState.profile.triggers == null || AppState.profile.triggers.isEmpty()) {
            toast("Geri alınacak trigger yok");
            return;
        }
        // Remove preview overlay if exists
        if (lastPreview != null) {
            lastPreview.remove();
            lastPreview = null;
        }
        // Remove last trigger from profile
        int lastIndex = AppState.profile.triggers.size() - 1;
        Trigger removed = AppState.profile.triggers.remove(lastIndex);
        if (currentTrigger == removed) currentTrigger = null;

        setState("Son trigger silindi.");
    }

    private void pickClickPoint() {
        if (currentTrigger == null) {
            toast("Önce trigger ekle");
            return;
        }
        if (!ensureOverlayPermission()) return;

        toast("Ekranda click noktasını seç (her seçişte 1 aksiyon ekler)");
        new PointSelectionOverlay(this, (x, y) -> {
            // Default: 50ms delay. Sonradan UI editor eklenebilir.
            currentTrigger.actions.add(new ClickAction(x, y, 50));
            setState("Click aksiyonu eklendi: (" + x + "," + y + "), delay=50ms. Toplam=" + currentTrigger.actions.size());
        });
    }

    private void startOverlay() {
        if (!ensureOverlayPermission()) return;
        startService(new Intent(this, TriggerOverlayService.class));
        setState("Overlay başlatıldı. Floating trigger(lar) ekranda olmalı.");
    }

    private void saveProfile() {
        try {
            AppState.profile.name = "Default";
            ProfileStorage.save(this, AppState.profile);
            setState("Profil kaydedildi: Default");
        } catch (Exception e) {
            setState("Kaydetme hatası: " + e.getMessage());
        }
    }

    private void loadProfile() {
        try {
            Profile p = ProfileStorage.load(this, "Default");
            if (p != null) {
                AppState.profile = p;
                setState("Profil yüklendi: Default, trigger sayısı=" + p.triggers.size());
            } else {
                setState("Profil bulunamadı");
            }
        } catch (Exception e) {
            setState("Yükleme hatası: " + e.getMessage());
        }
    }

    private void setState(String s) {
        tvState.setText("Durum: " + s);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}
