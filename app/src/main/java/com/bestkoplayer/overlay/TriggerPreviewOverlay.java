
package com.bestkoplayer.overlay;

import android.content.Context;
import android.graphics.PixelFormat;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;

import com.bestkoplayer.R;

public class TriggerPreviewOverlay {

    private final WindowManager wm;
    private final View view;

    public TriggerPreviewOverlay(Context context, int x, int y, int sizePx, float opacity) {

        wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);

        ImageView v = new ImageView(context);
        v.setImageResource(R.drawable.bg_trigger_circle);
        v.setAlpha(opacity);
        view = v;

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                Math.max(30, sizePx),
                Math.max(30, sizePx),
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = x;
        params.y = y;

        wm.addView(view, params);
    }

    public void remove() {
        try { wm.removeView(view); } catch (Exception ignored) {}
    }
}
