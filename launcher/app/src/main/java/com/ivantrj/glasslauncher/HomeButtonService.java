package com.ivantrj.glasslauncher;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Fire OS protects its launcher, so it can't be disabled and stays the system
 * home. This service makes Glass the effective home screen instead:
 *  - the remote's Home button is consumed and opens Glass;
 *  - whenever the Amazon launcher comes to the front anyway (after boot, or when
 *    an app exits), Glass is brought straight back on top.
 * Event-driven only: it receives window changes from the Amazon launcher's
 * package and key events, and does nothing in between.
 */
public class HomeButtonService extends AccessibilityService {

    private static final String AMAZON_LAUNCHER = "com.amazon.tv.launcher";
    private static final String AMAZON_HOME_ACTIVITY = "com.amazon.tv.launcher.ui.HomeActivity_vNext";
    private static final String GLASS = "com.ivantrj.glasslauncher";
    private long lastLaunch;
    private String previousWindowPackage;

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (event.getKeyCode() != KeyEvent.KEYCODE_HOME) return false;
        // Fire OS may cancel or omit HOME's ACTION_UP while switching to its own
        // launcher. Launch on the initial down event instead; consume both halves.
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) openGlass();
        return true; // swallow both down and up so Fire OS never sees Home
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        String currentPackage = event.getPackageName() == null
                ? "" : event.getPackageName().toString();
        // Fire TV renders Settings inside its launcher Home activity. Redirect only
        // when Home appears after another app; Glass opening Settings must stay there.
        if (AMAZON_LAUNCHER.equals(currentPackage)
                && AMAZON_HOME_ACTIVITY.contentEquals(event.getClassName())
                && !GLASS.equals(previousWindowPackage)) {
            openGlass();
        }
        previousWindowPackage = currentPackage;
    }

    private void openGlass() {
        long now = SystemClock.uptimeMillis();
        if (now - lastLaunch < 600) return; // one launch per Home press / launcher flash
        lastLaunch = now;
        startActivity(new Intent(this, HomeActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP));
    }

    @Override public void onInterrupt() {}
}
