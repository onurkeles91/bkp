
package com.bestkoplayer.model;

import java.util.ArrayList;
import java.util.List;

public class Trigger {

    public String name = "Trigger";

    public int x;
    public int y;

    public int widthPx = 120;
    public int heightPx = 120;

    public float opacity = 0.4f;

    public TriggerMode mode = TriggerMode.ONCE;

    public boolean isRunning = false;

    // Runtime-only flag to prevent overlapping executions when the loop interval
    // is smaller than the time it takes to dispatch all actions.
    public transient boolean isExecuting = false;

    public List<ClickAction> actions = new ArrayList<>();

    // loop delay between macro runs (for HOLD/TOGGLE)
    public int loopIntervalMs = 250;

    // screen-reading condition (optional)
    public OcrCondition ocrCondition = new OcrCondition();
}
