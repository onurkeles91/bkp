
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
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.bestkoplayer.R;
import com.bestkoplayer.model.ClickAction;
import com.bestkoplayer.model.Trigger;
import com.bestkoplayer.model.TriggerMode;
import com.bestkoplayer.overlay.PointSelectionOverlay;
import com.bestkoplayer.overlay.TriggerPreviewOverlay;
import com.bestkoplayer.overlay.AppState;
import com.bestkoplayer.storage.ProfileStorage;

public class NewTriggerActivity extends Activity {

    private Trigger draft = new Trigger();
    private TriggerPreviewOverlay preview = null;

    // We treat the user-selected trigger point as the *center* of the trigger button.
    // Stored Trigger.x/y are top-left (WindowManager LayoutParams expects top-left when gravity=TOP|START).
    private int triggerCenterX = 0;
    private int triggerCenterY = 0;

    private ActionsAdapter adapter;

    private TextView tvSize, tvOpacity, tvState;
    private EditText etTriggerName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_new_trigger);

        Button btnBack = findViewById(R.id.btnBack);
        Button btnPickTriggerPoint = findViewById(R.id.btnPickTriggerPoint);
        Button btnAddAction = findViewById(R.id.btnAddAction);
        Button btnCreate = findViewById(R.id.btnCreate);

        etTriggerName = findViewById(R.id.etTriggerName);
        etTriggerName.setText(draft.name == null ? "" : draft.name);

        SeekBar sbSize = findViewById(R.id.sbSize);
        SeekBar sbOpacity = findViewById(R.id.sbOpacity);

        tvSize = findViewById(R.id.tvSize);
        tvOpacity = findViewById(R.id.tvOpacity);
        tvState = findViewById(R.id.tvState);

        ListView lv = findViewById(R.id.lvActions);
        adapter = new ActionsAdapter();
        lv.setAdapter(adapter);
        lv.setOnItemClickListener((p,v,pos,id)-> editDelay(pos));

        btnBack.setOnClickListener(v -> finish());
        btnPickTriggerPoint.setOnClickListener(v -> pickTriggerPoint());

        RadioGroup rgMode = findViewById(R.id.rgMode);
        // default selection
        switch (draft.mode) {
            case HOLD:
                rgMode.check(R.id.rbModeHold);
                break;
            case TOGGLE:
                rgMode.check(R.id.rbModeToggle);
                break;
            case ONCE:
            default:
                rgMode.check(R.id.rbModeOnce);
                break;
        }
        rgMode.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rbModeHold) {
                draft.mode = TriggerMode.HOLD;
                setState("Mod: Basılı tut (Hold)");
            } else if (checkedId == R.id.rbModeToggle) {
                draft.mode = TriggerMode.TOGGLE;
                setState("Mod: Aç / Kapat (Toggle)");
            } else {
                draft.mode = TriggerMode.ONCE;
                setState("Mod: Bir kere (Once)");
            }
        });

        sbSize.setProgress(Math.max(30, draft.widthPx));
        tvSize.setText("Boyut: " + draft.widthPx + "px");
        sbSize.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int s = Math.max(30, progress);
                draft.widthPx = s;
                draft.heightPx = s;
                tvSize.setText("Boyut: " + s + "px");
                refreshPreview();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        sbOpacity.setProgress((int)(draft.opacity * 100));
        tvOpacity.setText("Opaklık: " + (int)(draft.opacity * 100) + "%");
        sbOpacity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                draft.opacity = Math.max(0.05f, progress / 100f);
                tvOpacity.setText("Opaklık: " + progress + "%");
                refreshPreview();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        btnAddAction.setOnClickListener(v -> addAction());
        btnCreate.setOnClickListener(v -> createTrigger());

        setState("Adım: İsim → Trigger Noktası → İşlemler (seçim sırasında hedef uygulama otomatik öne alınır)");
        refreshActions();
    }

    private boolean ensureOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            toast("Önce Overlay izni ver (Profil ekranından)");
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

    // Name is entered directly in the text field.

    private void pickTriggerPoint() {
        if (!ensureOverlayPermission()) return;

        boolean launched = autoLaunchTargetAppIfSet();
        toast(launched
                ? "Hedef uygulama açılıyor… Tıklayacağınız alanı seçin"
                : "Tıklayacağınız alanı seçin (hedef uygulama seçili değil)");

        new Handler(Looper.getMainLooper()).postDelayed(() ->
                new PointSelectionOverlay(this, (x,y)->{
                    triggerCenterX = x;
                    triggerCenterY = y;
                    updateTriggerTopLeftFromCenter();
                    refreshPreview();
                    // "OCR" reading: try to read UI text at the selected point via Accessibility tree.
                    String text = "";
                    if (com.bestkoplayer.BestKoAccessibilityService.isConnected()) {
                        com.bestkoplayer.BestKoAccessibilityService svc = com.bestkoplayer.BestKoAccessibilityService.getInstance();
                        if (svc != null) text = svc.readTextAt(x, y);
                    }
                    if (text == null) text = "";
                    if (!text.trim().isEmpty()) {
                        setState("Trigger noktası seçildi: ("+x+","+y+") | Okunan yazı: " + text);
                    } else {
                        setState("Trigger noktası seçildi: ("+x+","+y+")");
                    }
                }), launched ? 650 : 0);
    }

    private void updateTriggerTopLeftFromCenter() {
        if (triggerCenterX == 0 && triggerCenterY == 0) return;
        int w = Math.max(30, draft.widthPx);
        int h = Math.max(30, draft.heightPx);
        draft.x = triggerCenterX - (w / 2);
        draft.y = triggerCenterY - (h / 2);
    }

    private void refreshPreview() {
        if (triggerCenterX == 0 && triggerCenterY == 0 && (draft.x == 0 && draft.y == 0)) return;

        // If size changed after point selection, keep the trigger centered.
        updateTriggerTopLeftFromCenter();

        if (preview != null) {
            preview.remove();
            preview = null;
        }
        if (!ensureOverlayPermission()) return;

        preview = new TriggerPreviewOverlay(this, draft.x, draft.y, draft.widthPx, draft.opacity);
    }

    private void addAction() {
        if (!ensureOverlayPermission()) return;

        boolean launched = autoLaunchTargetAppIfSet();
        toast(launched
                ? "Hedef uygulama açılıyor… Tıklayacağınız alanı seçin"
                : "Tıklayacağınız alanı seçin (hedef uygulama seçili değil)");

        new Handler(Looper.getMainLooper()).postDelayed(() ->
                new PointSelectionOverlay(this, (x,y)->{
                    // "OCR" reading: try to read UI text at the selected point via Accessibility tree.
                    String text = "";
                    if (com.bestkoplayer.BestKoAccessibilityService.isConnected()) {
                        com.bestkoplayer.BestKoAccessibilityService svc = com.bestkoplayer.BestKoAccessibilityService.getInstance();
                        if (svc != null) text = svc.readTextAt(x, y);
                    }
                    if (text == null) text = "";

                    EditText et = new EditText(this);
                    et.setHint("Delay ms (ör: 50)");
                    et.setText("50");

                    new AlertDialog.Builder(this)
                            .setTitle(text.trim().isEmpty()
                                    ? "Delay (ms)"
                                    : "Delay (ms) | Okunan yazı: " + text)
                            .setView(et)
                            .setPositiveButton("Ekle", (d,w)->{
                                int ms = parseIntSafe(et.getText().toString().trim(), 50);
                                draft.actions.add(new ClickAction(x, y, ms));
                                refreshActions();
                            })
                            .setNegativeButton("İptal", null)
                            .show();
                }), launched ? 650 : 0);
    }

    private void editDelay(int index) {
        ClickAction a = draft.actions.get(index);
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

    private void refreshActions() {
        adapter.notifyDataSetChanged();
    }

    private void createTrigger() {
        String n = etTriggerName == null ? "" : etTriggerName.getText().toString().trim();
        if (n.isEmpty()) n = "Trigger";
        draft.name = n;

        if (draft.x == 0 && draft.y == 0) {
            toast("Önce trigger noktasını seç");
            return;
        }

        AppState.profile.triggers.add(draft);
        try { ProfileStorage.save(this, AppState.profile); } catch (Exception ignored) {}

        toast("Trigger oluşturuldu");
        finish();
    }

    /**
     * Custom list row: shows (x,y) + delay and has a delete button on the right.
     */
    private class ActionsAdapter extends android.widget.BaseAdapter {

        private final android.view.LayoutInflater inflater = android.view.LayoutInflater.from(NewTriggerActivity.this);

        @Override
        public int getCount() {
            return draft.actions == null ? 0 : draft.actions.size();
        }

        @Override
        public Object getItem(int position) {
            return draft.actions.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public android.view.View getView(int position, android.view.View convertView, android.view.ViewGroup parent) {
            android.view.View row = convertView;
            if (row == null) {
                row = inflater.inflate(R.layout.row_item_with_delete, parent, false);
            }

            TextView tvTitle = row.findViewById(R.id.tvTitle);
            android.widget.ImageButton btnDelete = row.findViewById(R.id.btnDelete);

            ClickAction a = draft.actions.get(position);
            String text = (position + 1) + ") (" + a.x + "," + a.y + ")  delay=" + a.delayMs + "ms";
            tvTitle.setText(text);

            btnDelete.setOnClickListener(v -> {
                new AlertDialog.Builder(NewTriggerActivity.this)
                        .setTitle("Sil")
                        .setMessage("Bu işlem silinsin mi?")
                        .setPositiveButton("Sil", (d, w) -> {
                            if (position >= 0 && position < draft.actions.size()) {
                                draft.actions.remove(position);
                                notifyDataSetChanged();
                            }
                        })
                        .setNegativeButton("İptal", null)
                        .show();
            });
            return row;
        }
    }

    private void setState(String s) {
        tvState.setText("Durum: " + s);
    }

    private int parseIntSafe(String s, int def) {
        try { return Integer.parseInt(s); } catch (Exception e) { return def; }
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (preview != null) {
            preview.remove();
            preview = null;
        }
    }
}
