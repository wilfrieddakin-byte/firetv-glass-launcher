package com.ivantrj.glasslauncher;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

/**
 * Fire OS protects its launcher, so it can't be disabled and stays the system
 * home. This service makes Glass the effective home screen instead:
 *  - the remote's Home button is consumed and opens Glass;
 *  - a plain accessibility overlay hides Amazon Home while Glass is brought forward;
 *  - whenever Amazon Home comes to the front anyway, Glass is brought back on top.
 * Event-driven only: it receives window changes from the Amazon launcher's
 * package and key events, and does nothing in between.
 */
public class HomeButtonService extends AccessibilityService {

    private static final String AMAZON_LAUNCHER = "com.amazon.tv.launcher";
    private static final String AMAZON_HOME_ACTIVITY = "com.amazon.tv.launcher.ui.HomeActivity_vNext";
    private static final String GLASS = "com.ivantrj.glasslauncher";
    private static final long COVER_TIMEOUT_MS = 1500L;
    private static final long COVER_RELEASE_DELAY_MS = 80L;
    private long lastLaunch;
    private boolean redirectingAmazonHome;
    private boolean glassVisible;
    private final Handler main = new Handler(Looper.getMainLooper());
    private WindowManager windowManager;
    private View homeCover;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        windowManager = getSystemService(WindowManager.class);
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (event.getKeyCode() != KeyEvent.KEYCODE_HOME) return false;
        // Keep the existing Home key handling, including Fire OS long-press behavior.
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            redirectingAmazonHome = false;
            if (!glassVisible) showHomeCover();
            openGlass(false);
        }
        return true;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        String currentPackage = event.getPackageName() == null
                ? "" : event.getPackageName().toString();
        glassVisible = GLASS.equals(currentPackage);
        if (glassVisible) {
            redirectingAmazonHome = false;
            if (homeCover != null) main.postDelayed(this::removeHomeCover, COVER_RELEASE_DELAY_MS);
            return;
        }
        if (AMAZON_LAUNCHER.equals(currentPackage)
                && AMAZON_HOME_ACTIVITY.contentEquals(event.getClassName())) {
            // Fire OS may launch Home after our key handler already opened Glass.
            // Keep Amazon's first frame covered while Glass takes the foreground.
            if (!redirectingAmazonHome) {
                redirectingAmazonHome = true;
                showHomeCover();
                openGlass(true);
            }
        }
    }

    private void openGlass(boolean force) {
        long now = SystemClock.uptimeMillis();
        if (!force && now - lastLaunch < 600) return; // one launch per Home press
        lastLaunch = now;
        startActivity(new Intent(this, HomeActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP));
    }

    private void showHomeCover() {
        if (windowManager == null) windowManager = getSystemService(WindowManager.class);
        if (windowManager == null) return;
        if (homeCover == null) {
            View cover = new View(this);
            cover.setBackgroundColor(Color.BLACK);
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.OPAQUE);
            params.gravity = Gravity.TOP | Gravity.START;
            try {
                windowManager.addView(cover, params);
                homeCover = cover;
            } catch (WindowManager.BadTokenException ignored) {
                return;
            }
        }
        main.removeCallbacksAndMessages(null);
        main.postDelayed(this::removeHomeCover, COVER_TIMEOUT_MS);
    }

    private void removeHomeCover() {
        if (homeCover == null || windowManager == null) return;
        try {
            windowManager.removeView(homeCover);
        } catch (IllegalArgumentException ignored) {
            // Ignore a service/window teardown race.
        }
        homeCover = null;
    }

    @Override public void onInterrupt() { main.post(this::removeHomeCover); }

    @Override public void onDestroy() {
        main.post(this::removeHomeCover);
        super.onDestroy();
    }
}
