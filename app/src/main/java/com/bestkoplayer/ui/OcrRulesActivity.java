
package com.bestkoplayer.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
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
import com.bestkoplayer.model.OcrRule;
import com.bestkoplayer.overlay.AppState;
import com.bestkoplayer.storage.ProfileStorage;

import java.util.ArrayList;
import java.util.List;

public class OcrRulesActivity extends Activity {

    private final List<OcrRule> items = new ArrayList<>();
    private ArrayAdapter<OcrRule> adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ocr_rules);

        Button btnBack = findViewById(R.id.btnBack);
        Button btnNew = findViewById(R.id.btnNewRule);
        ListView lv = findViewById(R.id.lvRules);

        btnBack.setOnClickListener(v -> finish());
        btnNew.setOnClickListener(v -> {
            Intent i = new Intent(this, OcrRuleEditorActivity.class);
            i.putExtra("ruleIndex", -1);
            startActivity(i);
        });

        adapter = new ArrayAdapter<OcrRule>(this, R.layout.row_item_with_delete, items) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View v = convertView;
                if (v == null) {
                    v = LayoutInflater.from(getContext()).inflate(R.layout.row_item_with_delete, parent, false);
                }
                TextView tv = v.findViewById(R.id.tvTitle);
                ImageButton del = v.findViewById(R.id.btnDelete);

                OcrRule r = items.get(position);
                String label = (position+1) + ") " + (r.name == null ? "OCR Rule" : r.name)
                        + " | area=(" + r.x + "," + r.y + "," + r.width + "x" + r.height + ")"
                        + " | thr=" + r.thresholdValue
                        + " | actions=" + (r.actions == null ? 0 : r.actions.size());
                tv.setText(label);

                v.setOnClickListener(vv -> {
                    Intent i = new Intent(OcrRulesActivity.this, OcrRuleEditorActivity.class);
                    i.putExtra("ruleIndex", position);
                    startActivity(i);
                });

                del.setOnClickListener(vv -> confirmDelete(position));

                return v;
            }
        };
        lv.setAdapter(adapter);

        refresh();
    }

    private void confirmDelete(int index) {
        new AlertDialog.Builder(this)
                .setTitle("Sil")
                .setMessage("OCR kuralı silinsin mi?")
                .setPositiveButton("Sil", (d,w)->{
                    AppState.profile.ocrRules.remove(index);
                    save();
                    refresh();
                })
                .setNegativeButton("İptal", null)
                .show();
    }

    private void refresh() {
        items.clear();
        if (AppState.profile != null && AppState.profile.ocrRules != null) {
            items.addAll(AppState.profile.ocrRules);
        }
        adapter.notifyDataSetChanged();
    }

    private void save() {
        try { ProfileStorage.save(this, AppState.profile); }
        catch (Exception e) { Toast.makeText(this, "Kaydetme hatası: " + e.getMessage(), Toast.LENGTH_SHORT).show(); }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }
}
