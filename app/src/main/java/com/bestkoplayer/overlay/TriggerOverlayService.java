
package com.bestkoplayer.overlay;

import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Looper;
import android.os.Handler;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;

import com.bestkoplayer.BestKoAccessibilityService;
import com.bestkoplayer.R;
import com.bestkoplayer.model.ClickAction;
import com.bestkoplayer.model.Trigger;
import com.bestkoplayer.model.TriggerMode;

import java.util.ArrayList;
import java.util.List;

public class TriggerOverlayService extends Service {

    private WindowManager wm;
    private final List<View> addedViews = new ArrayList<>();
    private int frameIntervalMs = 16;

    @Override
    public void onCreate() {
        super.onCreate();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);

        // Frame-paced scheduling: align injected taps to a reasonable cadence.
        // We clamp to 16ms (60Hz) minimum. On low refresh-rate devices this will
        // naturally behave like ~33ms (30Hz) when delays/loopInterval are larger.
        try {
            float rr = wm.getDefaultDisplay().getRefreshRate();
            if (rr > 1f) {
                int est = Math.round(1000f / rr);
                frameIntervalMs = Math.max(16, est);
            }
        } catch (Exception ignored) {
            frameIntervalMs = 16;
        }

        showAllTriggers();
    }

    private void showAllTriggers() {
        if (AppState.profile == null || AppState.profile.triggers == null) return;

        for (Trigger t : AppState.profile.triggers) {
            addTriggerView(t);
        }
    }

    private void addTriggerView(Trigger trigger) {

        ImageView v = new ImageView(this);
        v.setImageResource(R.drawable.bg_trigger_circle);
        v.setAlpha(clamp01(trigger.opacity));
        v.setClickable(true);
        v.setFocusable(false);
        v.setFocusableInTouchMode(false);

        
        int overlayType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) {
            overlayType = WindowManager.LayoutParams.TYPE_PHONE;
        }

        // Some games render via SurfaceView/GL and may behave oddly with overlays. These flags
        // maximize the chance of the overlay being visible and usable on top.
        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                Math.max(30, trigger.widthPx),
                Math.max(30, trigger.heightPx),
                overlayType,
                flags,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;

        // Allow drawing into display cutout areas (notches) on Android 9+.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            params.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }

        params.x = trigger.x;
        params.y = trigger.y;

        wm.addView(v, params);
        addedViews.add(v);

        // Always use main looper for UI/gesture scheduling.
        Handler handler = new Handler(Looper.getMainLooper());

        Runnable loop = new Runnable() {
            @Override
            public void run() {
                Log.d("BestKoPlayer", "TRIGGER runActions name=" + trigger.name);
                runActions(trigger, trigger.actions);
                if (trigger.isRunning) {
                    handler.postDelayed(this, Math.max(frameIntervalMs, trigger.loopIntervalMs));
                }
            }
        };

        v.setOnTouchListener((view, event) -> {

            Log.d("BestKoPlayer", "TRIGGER touch name=" + trigger.name + " action=" + event.getAction());

            if (event.getAction() != MotionEvent.ACTION_DOWN &&
                    event.getAction() != MotionEvent.ACTION_UP) {
                return true;
            }

            if (trigger.mode == TriggerMode.ONCE) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    Log.d("BestKoPlayer", "TRIGGER runActions name=" + trigger.name);
                    runActions(trigger, trigger.actions);
                }
                return true;
            }

            if (trigger.mode == TriggerMode.HOLD) {
                // User-requested behavior: HOLD behaves like a start/stop toggle (tap once to start,
                // tap again to stop) so the user can keep using the in-game joystick without having
                // to keep a finger on the trigger.
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    trigger.isRunning = !trigger.isRunning;
                    if (trigger.isRunning) handler.post(loop);
                    else handler.removeCallbacks(loop);
                }
                return true;
            }

            if (trigger.mode == TriggerMode.TOGGLE) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    trigger.isRunning = !trigger.isRunning;
                    if (trigger.isRunning) handler.post(loop);
                    else handler.removeCallbacks(loop);
                }
                return true;
            }

            return true;
        });
    }

    /**
     * Single-pointer, sequential execution.
     * - Prevents overlapping runs (per-trigger)
     * - Clamps per-action spacing to at least one frame interval
     */
    private void runActions(Trigger trigger, List<ClickAction> actions) {
        BestKoAccessibilityService svc = BestKoAccessibilityService.getInstance();
        if (actions == null) return;
        if (svc == null) {
            Log.d("BestKoPlayer", "runActions: AccessibilityService is NULL (enable Accessibility)");
            return;
        }

        // Avoid stacking runs on top of each other when loop interval is very small.
        if (trigger != null && trigger.isExecuting) {
            Log.d("BestKoPlayer", "runActions: skip (still executing) name=" + trigger.name);
            return;
        }
        if (trigger != null) trigger.isExecuting = true;

        Handler handler = new Handler(Looper.getMainLooper());

        // Frame-paced sequential chain.
        final int[] index = {0};

        Runnable step = new Runnable() {
            @Override
            public void run() {
                if (index[0] >= actions.size()) {
                    if (trigger != null) trigger.isExecuting = false;
                    return;
                }

                ClickAction a = actions.get(index[0]);
                index[0]++;

                // Execute one tap
                BestKoAccessibilityService s = BestKoAccessibilityService.getInstance();
                if (s != null) s.click(a.x, a.y);

                // Schedule next tap: at least one frame apart
                int gap = Math.max(frameIntervalMs, Math.max(0, a.delayMs));
                handler.postDelayed(this, gap);
            }
        };

        // Start immediately on current frame.
        handler.post(step);
    }

    private float clamp01(float v) {
        if (v < 0f) return 0f;
        if (v > 1f) return 1f;
        return v;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Remove all overlay views
        for (View v : addedViews) {
            try { wm.removeView(v); } catch (Exception ignored) {}
        }
        addedViews.clear();
    }

    @Override
    public android.os.IBinder onBind(Intent intent) {
        return null;
    }
}
