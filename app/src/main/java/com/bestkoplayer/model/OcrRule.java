
package com.bestkoplayer.model;

import java.util.ArrayList;
import java.util.List;

public class OcrRule {
    public String name = "OCR Rule";

    public boolean enabled = true;

    // area
    public int x = 0;
    public int y = 0;
    public int width = 0;
    public int height = 0;

    public int thresholdValue = 0;
    public ConditionType conditionType = ConditionType.LESS_THAN;

    // actions to execute when condition matches
    public List<ClickAction> actions = new ArrayList<>();
}
