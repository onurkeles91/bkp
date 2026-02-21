
package com.bestkoplayer;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Build;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayDeque;
import java.util.Deque;

public class BestKoAccessibilityService extends AccessibilityService {

    private static BestKoAccessibilityService instance;
    private static volatile boolean connected = false;

    public static BestKoAccessibilityService getInstance() {
        return instance;
    }

    public static boolean isConnected() {
        return connected && instance != null;
    }

    @Override
    protected void onServiceConnected() {
        instance = this;
        connected = true;
        Log.d("BestKoPlayer", "AccessibilityService connected");
    }

    @Override
    public void onDestroy() {
        connected = false;
        instance = null;
        Log.d("BestKoPlayer", "AccessibilityService destroyed");
        super.onDestroy();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override
    public void onInterrupt() {}

    public void click(int x, int y) {

        // Some devices are picky about very short taps.
        // Use a slightly longer press (>= 60ms) and a tiny line segment to improve reliability.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            Log.w("BestKoPlayer", "dispatchGesture requires API 24+. click ignored.");
            return;
        }

        Path path = new Path();
        path.moveTo((float) x, (float) y);
        // Tiny movement helps certain OEMs recognize the gesture.
        path.lineTo((float) x + 1f, (float) y + 1f);

        // Keep taps short to reduce the chance of interfering with the user's own touch (e.g.,
        // holding an in-game joystick) while still being reliable across OEMs.
        GestureDescription.StrokeDescription stroke =
                new GestureDescription.StrokeDescription(path, 0, 30);

        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(stroke);

        dispatchGesture(builder.build(), new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription gestureDescription) {
                Log.d("BestKoPlayer", "Gesture completed at (" + x + "," + y + ")");
            }

            @Override
            public void onCancelled(GestureDescription gestureDescription) {
                Log.w("BestKoPlayer", "Gesture cancelled at (" + x + "," + y + ")");
            }
        }, null);
    }

    /**
     * "OCR"-like helper: reads the text/contentDescription of the smallest Accessibility node
     * whose bounds contain the given screen point.
     *
     * NOTE: This is NOT true bitmap OCR. True OCR would require screen capture (MediaProjection)
     * and a text recognition model. For many apps/menus, Accessibility text is enough and faster.
     */
    public String readTextAt(int x, int y) {
        try {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null) return "";

            Rect r = new Rect();
            AccessibilityNodeInfo best = null;
            int bestArea = Integer.MAX_VALUE;

            Deque<AccessibilityNodeInfo> q = new ArrayDeque<>();
            q.add(root);

            while (!q.isEmpty()) {
                AccessibilityNodeInfo n = q.poll();
                if (n == null) continue;

                n.getBoundsInScreen(r);
                if (r.contains(x, y)) {
                    int area = Math.max(1, r.width()) * Math.max(1, r.height());
                    CharSequence t = n.getText();
                    CharSequence d = n.getContentDescription();

                    // Prefer nodes that actually have text/desc and are smaller (more specific).
                    if ((t != null && t.length() > 0) || (d != null && d.length() > 0)) {
                        if (area < bestArea) {
                            best = n;
                            bestArea = area;
                        }
                    }
                }

                for (int i = 0; i < n.getChildCount(); i++) {
                    AccessibilityNodeInfo c = n.getChild(i);
                    if (c != null) q.add(c);
                }
            }

            if (best != null) {
                CharSequence t = best.getText();
                if (t != null && t.length() > 0) return t.toString();
                CharSequence d = best.getContentDescription();
                if (d != null && d.length() > 0) return d.toString();
            }
        } catch (Exception e) {
            Log.w("BestKoPlayer", "readTextAt failed: " + e.getMessage());
        }
        return "";
    }
}
