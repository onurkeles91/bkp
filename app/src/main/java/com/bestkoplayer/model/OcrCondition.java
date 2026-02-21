
package com.bestkoplayer.model;

public class OcrCondition {

    public boolean enabled = false;

    public int x = 0;
    public int y = 0;
    public int width = 0;
    public int height = 0;

    public int thresholdValue = 0;
    public ConditionType conditionType = ConditionType.LESS_THAN;
}
