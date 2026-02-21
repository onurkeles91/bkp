
package com.bestkoplayer.model;

import java.util.ArrayList;
import java.util.List;

public class Profile {
    public String name = "Default";

    // Optional: target app to bring foreground when selecting points
    public String targetPackageName = "";

    public List<Trigger> triggers = new ArrayList<>();

    // OCR rules are separate from triggers
    public List<OcrRule> ocrRules = new ArrayList<>();
}
