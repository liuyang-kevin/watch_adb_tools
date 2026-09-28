package space.sybb.awatch;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.List;

/** 仅在用户点击按钮后的有限时间内操作指定的厂商页面。 */
public final class UsbModeService extends AccessibilityService {
    private static UsbModeService instance;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean pending;
    private boolean clicked;
    private final Runnable timeout = () -> finish(false, "原厂开关未确认关闭，请手动查看开关页面。", false);

    static UsbModeService connected() { return instance; }

    @Override protected void onServiceConnected() { instance = this; }

    void requestUsb() {
        cancel();
        pending = true;
        clicked = false;
        handler.postDelayed(timeout, 8000);
    }

    void cancel() {
        pending = false;
        handler.removeCallbacks(timeout);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (!pending || !"com.android.settings".contentEquals(event.getPackageName() == null
                ? "" : event.getPackageName())) return;
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;
        try {
            if (!"com.android.settings".contentEquals(root.getPackageName() == null
                    ? "" : root.getPackageName())) return;
            List<AccessibilityNodeInfo> switches = root.findAccessibilityNodeInfosByViewId(
                    "com.android.settings:id/hrtest_switch");
            List<AccessibilityNodeInfo> values = root.findAccessibilityNodeInfosByViewId(
                    "com.android.settings:id/hrtest_value");
            try {
                if (switches.size() != 1 || values.size() != 1) return;
                AccessibilityNodeInfo toggle = switches.get(0);
                CharSequence value = values.get(0).getText();
                if (value == null) return;
                if (value.toString().contains("read_failed")) {
                    finish(false, "原厂页面无法读取开关，未执行切换。", false);
                } else if (!toggle.isChecked() && value.toString().startsWith("Current: 0 ")) {
                    enableAdb();
                } else if (toggle.isChecked() && toggle.isEnabled() && !clicked) {
                    clicked = true;
                    if (!toggle.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                        finish(false, "无法点击原厂开关，请手动关闭。", false);
                    }
                }
            } finally {
                for (AccessibilityNodeInfo node : switches) node.recycle();
                for (AccessibilityNodeInfo node : values) node.recycle();
            }
        } finally {
            root.recycle();
        }
    }

    private void enableAdb() {
        try {
            boolean written = Settings.Global.putInt(getContentResolver(), Settings.Global.ADB_ENABLED, 1);
            boolean enabled = Settings.Global.getInt(getContentResolver(), Settings.Global.ADB_ENABLED, 0) == 1;
            if (written && enabled) {
                finish(true, "原厂开关已读回 0，ADB 开关已开启。请连接 USB。", true);
            } else {
                finish(false, "原厂开关已关闭，但 ADB 设置未生效。", true);
            }
        } catch (SecurityException e) {
            finish(false, "原厂开关已关闭；尚缺一次 ADB 设置授权。", true);
        }
    }

    private void finish(boolean success, String message, boolean returnToApp) {
        if (!pending) return;
        cancel();
        getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE).edit()
                .putString("result", message).putBoolean("last_request_completed", success).apply();
        if (returnToApp) {
            startActivity(new Intent(this, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
                            | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        }
    }

    @Override public void onInterrupt() {
        finish(false, "操作助手被中断，请重新点击 USB 按钮。", false);
    }

    @Override public void onDestroy() {
        cancel();
        if (instance == this) instance = null;
        super.onDestroy();
    }
}
