package space.sybb.awatch;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.util.Collections;

public final class MainActivity extends Activity {
    static final ComponentName HARDWARE_PAGE = new ComponentName(
            "com.android.settings", "com.android.settings.HrTestSwitchActivity");
    static final String PREFS = "debug";
    private TextView status;
    private TextView result;
    private TextView wifi;
    private Intent usbState;

    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            usbState = intent;
            refresh();
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(dp(18), dp(18), dp(18), dp(22));
        scroll.addView(content);
        setContentView(scroll);

        text(content, "手表调试", 23, Color.WHITE);
        status = text(content, "", 13, Color.rgb(181, 197, 193));
        button(content, "开启 USB 调试", this::enableUsb);
        result = text(content, "", 13, Color.rgb(112, 222, 188));
        button(content, "打开原厂 USB 开关", () -> openHardwarePage(false));
        button(content, "原生开发者选项", this::openNativeDeveloper);
        button(content, "原生系统设置", () -> openNativePage("com.android.settings.Settings"));
        wifi = text(content, "", 13, Color.rgb(181, 197, 193));
        wifi.setOnClickListener(view -> copyWifiCommand());
        button(content, "首次授权 / 帮助", this::showSetup);
        usbState = registerReceiver(usbReceiver,
                new IntentFilter("android.hardware.usb.action.USB_STATE"));
    }

    @Override protected void onResume() {
        super.onResume();
        UsbModeService service = UsbModeService.connected();
        if (service != null) service.cancel();
        refresh();
    }

    @Override protected void onDestroy() {
        unregisterReceiver(usbReceiver);
        super.onDestroy();
    }

    private void enableUsb() {
        if (checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS)
                != PackageManager.PERMISSION_GRANTED) {
            showSetup();
            return;
        }
        UsbModeService service = UsbModeService.connected();
        if (service == null) {
            new AlertDialog.Builder(this)
                    .setTitle("启用操作助手")
                    .setMessage("需要一次启用“手表调试操作助手”。它只在你点击 USB 按钮后，关闭原厂页面中的 HRTEST 开关。")
                    .setPositiveButton("去启用", (dialog, which) -> openAccessibility())
                    .setNegativeButton("取消", null).show();
            return;
        }
        if (!hardwarePageAvailable()) {
            setResult("未找到此固件的原厂 USB 开关，未执行操作。");
            return;
        }
        service.requestUsb();
        openHardwarePage(true);
    }

    private boolean hardwarePageAvailable() {
        try {
            return getPackageManager().getActivityInfo(HARDWARE_PAGE, 0).exported;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private void openHardwarePage(boolean automatic) {
        UsbModeService service = UsbModeService.connected();
        if (!automatic && service != null) service.cancel();
        try {
            startActivity(new Intent().setComponent(HARDWARE_PAGE));
            if (!automatic) {
                Toast.makeText(this, "关闭 HRTEST 开关；页面读数应为 Current: 0", Toast.LENGTH_LONG).show();
            }
        } catch (RuntimeException e) {
            if (automatic && service != null) service.cancel();
            setResult("无法打开原厂开关：" + e.getMessage());
        }
    }

    private void openAccessibility() {
        try {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        } catch (RuntimeException e) {
            setResult("系统未提供无障碍设置入口，请查看首次授权说明。");
        }
    }

    private void openNativeDeveloper() {
        if (checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS)
                == PackageManager.PERMISSION_GRANTED) {
            try {
                Settings.Global.putInt(getContentResolver(), Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 1);
            } catch (SecurityException e) {
                setResult("开发者总开关未获授权，请运行一次性授权脚本。");
                return;
            }
        }
        openNativePage("com.android.settings.Settings$DevelopmentSettingsDashboardActivity");
    }

    private void openNativePage(String activity) {
        try {
            startActivity(new Intent().setComponent(new ComponentName("com.android.settings", activity)));
        } catch (RuntimeException e) {
            setResult("原生页面被固件禁用或限制，请先运行 scripts/adb-authorize-app。");
        }
    }

    private void showSetup() {
        String grant = "adb shell pm grant " + getPackageName()
                + " android.permission.WRITE_SECURE_SETTINGS";
        TextView explanation = new TextView(this);
        explanation.setText("推荐在电脑执行项目的 scripts/adb-authorize-app，它会保留现有设置，授权本应用并启用完整开发者页面。\n\n"
                + "手动授权方式：\n1. 电脑通过已有 ADB 连接执行下面的授权命令。\n"
                + "2. 在系统无障碍设置中启用“手表调试操作助手”。\n"
                + "之后直接点击“开启 USB 调试”。\n\n" + grant
                + "\n\n只操作原厂 HRTEST 开关和 USB 调试开关。此版本不会自行开启 Wi-Fi ADB。"
                + "\n若系统隐藏了无障碍页面，电脑可使用项目 scripts/adb-authorize-app 完成一次授权。");
        explanation.setPadding(dp(16), dp(8), dp(16), dp(8));
        explanation.setTextSize(13);
        explanation.setTextIsSelectable(true);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(explanation);
        new AlertDialog.Builder(this).setTitle("首次授权")
                .setView(scroll)
                .setPositiveButton("操作助手设置", (dialog, which) -> openAccessibility())
                .setNegativeButton("关闭", null).show();
    }

    private void refresh() {
        if (status == null) return;
        boolean adb = Settings.Global.getInt(getContentResolver(), Settings.Global.ADB_ENABLED, 0) == 1;
        boolean configured = usbState != null && usbState.getBooleanExtra("configured", false);
        status.setText("ADB 开关" + (adb ? "已开启" : "未开启")
                + " · USB " + (configured ? "已枚举" : "未枚举"));
        String previous = getSharedPreferences(PREFS, MODE_PRIVATE).getString("result", null);
        result.setText(previous == null ? "首次使用请先完成授权。" : "上次操作：" + previous);
        String ip = wifiIp();
        wifi.setText(ip == null ? "Wi-Fi 未取得地址" : "Wi-Fi：" + ip
                + "\n网络 ADB 已开启时，点此复制连接命令");
    }

    private void setResult(String message) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString("result", message).apply();
        refresh();
    }

    private String wifiIp() {
        try {
            NetworkInterface network = NetworkInterface.getByName("wlan0");
            if (network != null) {
                for (java.net.InetAddress address : Collections.list(network.getInetAddresses())) {
                    if (address instanceof Inet4Address && !address.isLoopbackAddress()) {
                        return address.getHostAddress();
                    }
                }
            }
        } catch (java.net.SocketException ignored) {
            // 没有 Wi-Fi 地址时显示未连接，不把它当作 ADB 错误。
        }
        return null;
    }

    private void copyWifiCommand() {
        String ip = wifiIp();
        if (ip == null) return;
        getSystemService(ClipboardManager.class).setPrimaryClip(
                ClipData.newPlainText("ADB", "adb connect " + ip + ":5555"));
        Toast.makeText(this, "已复制；此操作不会开启网络 ADB", Toast.LENGTH_SHORT).show();
    }

    private TextView text(LinearLayout parent, String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER);
        view.setPadding(0, dp(4), 0, dp(8));
        parent.addView(view, new LinearLayout.LayoutParams(-1, -2));
        return view;
    }

    private void button(LinearLayout parent, String title, Runnable action) {
        Button button = new Button(this);
        button.setText(title);
        button.setTextSize(16);
        button.setAllCaps(false);
        button.setMinHeight(dp(48));
        button.setOnClickListener(view -> action.run());
        parent.addView(button, new LinearLayout.LayoutParams(-1, -2));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
