package com.bestkoplayer.overlay;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

public class PointSelectionOverlay {

    public interface OnPointSelected {
        void onSelected(int x, int y);
    }

    private final WindowManager wm;
    private final View overlayView;

    public PointSelectionOverlay(Context context, OnPointSelected cb) {

        wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);

        overlayView = new View(context);
        overlayView.setBackgroundColor(Color.argb(40, 0, 0, 0));

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);

        params.gravity = Gravity.TOP | Gravity.START;

        overlayView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                // OEM/ROM differences can offset rawX/rawY for overlays.
                // Compute absolute screen coordinates from view position + local touch.
                int[] loc = new int[2];
                v.getLocationOnScreen(loc);
                int x = (int) (loc[0] + event.getX());
                int y = (int) (loc[1] + event.getY());
                remove();
                cb.onSelected(x, y);
                return true;
            }
            return false;
        });

        wm.addView(overlayView, params);
    }

    public void remove() {
        try {
            wm.removeView(overlayView);
        } catch (Exception ignored) {}
    }
}
