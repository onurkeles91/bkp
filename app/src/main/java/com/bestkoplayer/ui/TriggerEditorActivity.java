
package com.bestkoplayer.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;

import com.bestkoplayer.R;
import com.bestkoplayer.model.ClickAction;
import com.bestkoplayer.model.Trigger;
import com.bestkoplayer.model.TriggerMode;
import com.bestkoplayer.overlay.AppState;
import com.bestkoplayer.overlay.PointSelectionOverlay;
import com.bestkoplayer.storage.ProfileStorage;


public class TriggerEditorActivity extends Activity {

    private Trigger trigger;
    private int triggerIndex;

    private ActionsAdapter adapter;

    private TextView tvHeader;
    private TextView tvSize;
    private TextView tvOpacity;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trigger_editor);

        triggerIndex = getIntent().getIntExtra("triggerIndex", -1);
        if (triggerIndex < 0 || AppState.profile == null || triggerIndex >= AppState.profile.triggers.size()) {
            toast("Trigger bulunamadı");
            finish();
            return;
        }

        trigger = AppState.profile.triggers.get(triggerIndex);

        Button btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());

        tvHeader = findViewById(R.id.tvTrigHeader);
        tvSize = findViewById(R.id.tvSize);
        tvOpacity = findViewById(R.id.tvOpacity);

        SeekBar sbSize = findViewById(R.id.sbSize);
        SeekBar sbOpacity = findViewById(R.id.sbOpacity);

        Button btnRename = findViewById(R.id.btnRename);
        Button btnAddAction = findViewById(R.id.btnAddAction);
        Button btnSave = findViewById(R.id.btnSave);

        RadioGroup rgMode = findViewById(R.id.rgMode);
        // set initial selection
        switch (trigger.mode) {
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
                trigger.mode = TriggerMode.HOLD;
            } else if (checkedId == R.id.rbModeToggle) {
                trigger.mode = TriggerMode.TOGGLE;
            } else {
                trigger.mode = TriggerMode.ONCE;
            }
            refreshHeader();
        });

        ListView lv = findViewById(R.id.lvActions);
        adapter = new ActionsAdapter();
        lv.setAdapter(adapter);

        lv.setOnItemClickListener((p,v,pos,id)-> editDelay(pos));

        btnRename.setOnClickListener(v -> renameTrigger());

        sbSize.setMax(400);
        sbSize.setProgress(Math.max(30, trigger.widthPx));
        tvSize.setText("Boyut: " + trigger.widthPx + "px");
        sbSize.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int s = Math.max(30, progress);
                trigger.widthPx = s;
                trigger.heightPx = s;
                tvSize.setText("Boyut: " + s + "px");
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        sbOpacity.setMax(100);
        sbOpacity.setProgress((int)(trigger.opacity * 100));
        tvOpacity.setText("Opaklık: " + (int)(trigger.opacity * 100) + "%");
        sbOpacity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float op = Math.max(0.05f, progress / 100f);
                trigger.opacity = op;
                tvOpacity.setText("Opaklık: " + progress + "%");
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        btnAddAction.setOnClickListener(v -> addAction());
        btnSave.setOnClickListener(v -> saveAll());

        refreshHeader();
        refreshActions();
    }

    private void refreshHeader() {
        String name = trigger.name == null ? "Trigger" : trigger.name;
        tvHeader.setText("Trigger: " + name + " | Mod: " + modeTr(trigger.mode) + " | (" + trigger.x + "," + trigger.y + ")");
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

    private void refreshActions() {
        adapter.notifyDataSetChanged();
    }

    /**
     * Custom list row: shows (x,y) + delay and has a delete button on the right.
     */
    private class ActionsAdapter extends BaseAdapter {

        private final LayoutInflater inflater = LayoutInflater.from(TriggerEditorActivity.this);

        @Override
        public int getCount() {
            return trigger.actions == null ? 0 : trigger.actions.size();
        }

        @Override
        public Object getItem(int position) {
            return trigger.actions.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View row = convertView;
            if (row == null) {
                row = inflater.inflate(R.layout.row_item_with_delete, parent, false);
            }

            TextView tvTitle = row.findViewById(R.id.tvTitle);
            ImageButton btnDelete = row.findViewById(R.id.btnDelete);

            ClickAction a = trigger.actions.get(position);
            String text = (position + 1) + ") (" + a.x + "," + a.y + ")  delay=" + a.delayMs + "ms";
            tvTitle.setText(text);

            // Important: bind click with current position safely
            btnDelete.setOnClickListener(v -> {
                new AlertDialog.Builder(TriggerEditorActivity.this)
                        .setTitle("Sil")
                        .setMessage("Bu işlem silinsin mi?")
                        .setPositiveButton("Sil", (d, w) -> {
                            if (position >= 0 && position < trigger.actions.size()) {
                                trigger.actions.remove(position);
                                notifyDataSetChanged();
                            }
                        })
                        .setNegativeButton("İptal", null)
                        .show();
            });

            return row;
        }
    }

    private boolean ensureOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            toast("Overlay izni yok. Profil ekranından aç.");
            return false;
        }
        return true;
    }

    private void addAction() {
        if (!ensureOverlayPermission()) return;

        toast("Ekranda tıklanacak noktayı seç");
        new PointSelectionOverlay(this, (x,y)->{
            EditText et = new EditText(this);
            et.setHint("Delay ms (ör: 50)");
            et.setText("50");

            new AlertDialog.Builder(this)
                    .setTitle("Delay (ms)")
                    .setView(et)
                    .setPositiveButton("Ekle", (d,w)->{
                        int ms = parseIntSafe(et.getText().toString().trim(), 50);
                        trigger.actions.add(new ClickAction(x, y, ms));
                        refreshActions();
                    })
                    .setNegativeButton("İptal", null)
                    .show();
        });
    }

    private void editDelay(int index) {
        ClickAction a = trigger.actions.get(index);
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

    private void renameTrigger() {
        EditText et = new EditText(this);
        et.setHint("Yeni isim");
        et.setText(trigger.name == null ? "" : trigger.name);

        new AlertDialog.Builder(this)
                .setTitle("Trigger adı")
                .setView(et)
                .setPositiveButton("Kaydet", (d,w)->{
                    String n = et.getText().toString().trim();
                    if (n.isEmpty()) n = "Trigger";
                    trigger.name = n;
                    refreshHeader();
                })
                .setNegativeButton("İptal", null)
                .show();
    }

    private void saveAll() {
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
