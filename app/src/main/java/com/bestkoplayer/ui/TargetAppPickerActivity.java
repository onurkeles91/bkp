
package com.bestkoplayer.ui;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import com.bestkoplayer.R;
import com.bestkoplayer.overlay.AppState;
import com.bestkoplayer.storage.ProfileStorage;

import java.util.ArrayList;
import java.util.List;

public class TargetAppPickerActivity extends Activity {

    private static class AppItem {
        String label;
        String pkg;
        AppItem(String label, String pkg) { this.label = label; this.pkg = pkg; }
        @Override public String toString() { return label + " (" + pkg + ")"; }
    }

    private final List<AppItem> apps = new ArrayList<>();
    private ArrayAdapter<AppItem> adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_target_app);

        Button btnBack = findViewById(R.id.btnBack);
        ListView lv = findViewById(R.id.lvApps);

        btnBack.setOnClickListener(v -> finish());

        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, apps);
        lv.setAdapter(adapter);

        lv.setOnItemClickListener((p, v, pos, id) -> {
            AppItem item = apps.get(pos);
            AppState.profile.targetPackageName = item.pkg;
            try { ProfileStorage.save(this, AppState.profile); } catch (Exception ignored) {}
            Toast.makeText(this, "Hedef uygulama seçildi: " + item.label, Toast.LENGTH_SHORT).show();
            finish();
        });

        loadApps();
    }

    private void loadApps() {
        apps.clear();
        PackageManager pm = getPackageManager();
        List<ApplicationInfo> installed = pm.getInstalledApplications(PackageManager.GET_META_DATA);
        for (ApplicationInfo ai : installed) {
            // only launchable apps
            Intent launch = pm.getLaunchIntentForPackage(ai.packageName);
            if (launch == null) continue;
            String label = pm.getApplicationLabel(ai).toString();
            apps.add(new AppItem(label, ai.packageName));
        }
        adapter.notifyDataSetChanged();
    }
}
