
package com.bestkoplayer.storage;

import android.content.Context;

import com.bestkoplayer.model.Profile;
import com.google.gson.Gson;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;

public class ProfileStorage {

    public static void save(Context context, Profile profile) throws Exception {
        Gson gson = new Gson();
        String json = gson.toJson(profile);

        File file = new File(context.getFilesDir(), profile.name + ".json");

        FileWriter writer = new FileWriter(file);
        writer.write(json);
        writer.close();
    }

    public static Profile load(Context context, String name) throws Exception {
        File file = new File(context.getFilesDir(), name + ".json");
        Gson gson = new Gson();
        return gson.fromJson(new FileReader(file), Profile.class);
    }


    public static boolean delete(Context context, String name) {
        File file = new File(context.getFilesDir(), name + ".json");
        return file.exists() && file.delete();
    }

    public static List<String> listProfiles(Context context) {
        File dir = context.getFilesDir();
        File[] files = dir.listFiles();
        List<String> names = new ArrayList<>();
        if (files == null) return names;

        for (File f : files) {
            String n = f.getName();
            if (n.endsWith(".json")) {
                names.add(n.substring(0, n.length() - 5));
            }
        }
        return names;
    }
}
