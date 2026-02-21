
package com.bestkoplayer.model;

public class ClickAction {
    public int x;
    public int y;
    public int delayMs;

    public ClickAction() {}

    public ClickAction(int x, int y, int delayMs) {
        this.x = x;
        this.y = y;
        this.delayMs = delayMs;
    }
}
