package com.oai.harmonywake;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    private LinearLayout list;
    private TextView reliabilityStatus;
    private List<WakeTask> tasks = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        tasks = TaskStore.load(this);
        try {
            AlarmScheduler.rescheduleAll(this);
        } catch (Exception ignored) {
        }
        updateReliabilityStatus();
        render();
    }

    private TextView text(String value, int sp) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(sp);
        v.setPadding(0, 12, 0, 12);
        return v;
    }

    private Button button(String value) {
        Button b = new Button(this);
        b.setText(value);
        return b;
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(36, 36, 36, 36);
        scroll.addView(root);

        root.addView(text("Harmony Wake", 28));
        root.addView(text(
                "v6：系统闹钟先触发广播，再由前台服务亮屏、自动上划并启动指定 App。",
                15
        ));

        reliabilityStatus = text("", 15);
        root.addView(reliabilityStatus);

        Button accessibility = button("① 开启自动上划解锁权限");
        accessibility.setOnClickListener(v -> openAccessibilitySettings());
        root.addView(accessibility);

        Button huawei = button("② 华为自启动 / 后台运行设置");
        huawei.setOnClickListener(v -> openHuaweiStartupSettings());
        root.addView(huawei);

        Button battery = button("③ 关闭 Harmony Wake 电池优化");
        battery.setOnClickListener(v -> openBatteryOptimizationSettings());
        root.addView(battery);

        Button details = button("④ Harmony Wake 应用详情");
        details.setOnClickListener(v -> openAppDetails());
        root.addView(details);

        Button logs = button("⑤ 查看最近执行日志");
        logs.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Harmony Wake 最近执行日志")
                .setMessage(WakeDebugLog.get(this))
                .setPositiveButton("关闭", null)
                .setNeutralButton("清空", (dialog, which) -> WakeDebugLog.clear(this))
                .show());
        root.addView(logs);

        root.addView(text(
                "自动上划说明：\n"
                        + "• 只在没有 PIN、密码或图案的“上划进入”锁屏上执行。\n"
                        + "• 如果系统检测到安全锁，Harmony Wake 不会尝试绕过认证。\n"
                        + "• 无障碍服务不读取其他 App 内容，只在定时唤醒时发送上划手势并在解锁后启动目标 App。\n"
                        + "• v6 的亮屏/上划不再依赖 WakeActivity 能否在锁屏状态被系统拉起。\n\n"
                        + "华为/HarmonyOS 建议关闭自动管理，并允许自启动、关联启动和后台活动。",
                14
        ));

        Button add = button("＋ 添加唤醒任务");
        add.setOnClickListener(v -> editTask(null));
        root.addView(add);

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        root.addView(list);
        setContentView(scroll);
    }

    private void updateReliabilityStatus() {
        StringBuilder s = new StringBuilder("运行保障状态\n");
        s.append("✓ 系统闹钟模式：已启用\n");
        s.append("✓ 手机重启后：自动恢复任务\n");
        s.append(isAccessibilityServiceEnabled()
                ? "✓ 自动上划权限：已开启\n"
                : "⚠ 自动上划权限：未开启\n");
        s.append(isIgnoringBatteryOptimizations()
                ? "✓ 电池优化：已关闭\n"
                : "⚠ 电池优化：建议关闭\n");
        s.append("⚠ 华为自启动：请在系统设置中确认");
        reliabilityStatus.setText(s.toString());
    }

    private boolean isAccessibilityServiceEnabled() {
        try {
            String enabled = Settings.Secure.getString(
                    getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            );
            if (enabled == null || enabled.trim().isEmpty()) return false;

            ComponentName target = new ComponentName(this, UnlockAccessibilityService.class);
            String[] parts = enabled.split(":");
            for (String part : parts) {
                ComponentName c = ComponentName.unflattenFromString(part);
                if (target.equals(c)) return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private boolean isIgnoringBatteryOptimizations() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true;
        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            return pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
        } catch (Exception ignored) {
            return false;
        }
    }

    private void openAccessibilitySettings() {
        try {
            Toast.makeText(
                    this,
                    "请找到“Harmony Wake 自动上划”并开启。",
                    Toast.LENGTH_LONG
            ).show();
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        } catch (Exception ignored) {
            openAppDetails();
        }
    }

    private void openHuaweiStartupSettings() {
        String[][] components = {
                {"com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"},
                {"com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity"},
                {"com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"}
        };
        for (String[] c : components) {
            try {
                Intent i = new Intent();
                i.setComponent(new ComponentName(c[0], c[1]));
                startActivity(i);
                Toast.makeText(
                        this,
                        "请关闭自动管理，并允许自启动、关联启动和后台活动。",
                        Toast.LENGTH_LONG
                ).show();
                return;
            } catch (Exception ignored) {
            }
        }
        openAppDetails();
    }

    private void openBatteryOptimizationSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            openAppDetails();
            return;
        }
        try {
            Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            i.setData(Uri.parse("package:" + getPackageName()));
            startActivity(i);
        } catch (Exception e) {
            try {
                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            } catch (Exception ignored) {
                openAppDetails();
            }
        }
    }

    private void openAppDetails() {
        try {
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            i.setData(Uri.parse("package:" + getPackageName()));
            startActivity(i);
        } catch (Exception ignored) {
        }
    }

    private void render() {
        list.removeAllViews();
        if (tasks.isEmpty()) {
            list.addView(text("还没有任务。", 17));
            return;
        }

        tasks.sort((a, b) -> (a.hour * 60 + a.minute) - (b.hour * 60 + b.minute));

        for (WakeTask task : new ArrayList<>(tasks)) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(12, 18, 12, 18);

            card.addView(text(String.format(
                    Locale.getDefault(), "%02d:%02d", task.hour, task.minute), 26));

            String appName = task.appName != null && !task.appName.isEmpty()
                    ? task.appName
                    : (task.packageName == null || task.packageName.isEmpty()
                    ? "未选择" : task.packageName);

            card.addView(text(
                    "目标 App：" + appName + "\n亮屏：" + task.screenMinutes + " 分钟",
                    16
            ));

            Switch enabled = new Switch(this);
            enabled.setText("启用任务");
            enabled.setChecked(task.enabled);
            enabled.setOnCheckedChangeListener((buttonView, checked) -> {
                task.enabled = checked;
                persist();
            });
            card.addView(enabled);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);

            Button edit = button("编辑");
            edit.setOnClickListener(v -> editTask(task));
            row.addView(edit);

            Button test = button("10秒锁屏测试");
            test.setOnClickListener(v -> {
                AlarmScheduler.scheduleTest(this, task, 10000L);
                Toast.makeText(
                        this,
                        "10 秒后按完整定时链路触发，请现在锁屏并不要触碰手机。",
                        Toast.LENGTH_LONG
                ).show();
            });
            row.addView(test);

            Button delete = button("删除");
            delete.setOnClickListener(v -> {
                AlarmScheduler.cancel(this, task.id);
                tasks.remove(task);
                persist();
                render();
            });
            row.addView(delete);

            card.addView(row);
            list.addView(card);
        }
    }

    private void editTask(WakeTask existing) {
        final WakeTask draft;
        if (existing == null) {
            draft = new WakeTask(
                    (int) (System.currentTimeMillis() & 0x7fffffff),
                    7, 30, "", "", 10, true
            );
        } else {
            draft = new WakeTask(
                    existing.id, existing.hour, existing.minute,
                    existing.packageName, existing.appName,
                    existing.screenMinutes, existing.enabled
            );
        }

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(36, 12, 36, 0);

        Button time = button(String.format(
                Locale.getDefault(), "时间：%02d:%02d", draft.hour, draft.minute));
        time.setOnClickListener(v -> new TimePickerDialog(
                this,
                (view, hour, minute) -> {
                    draft.hour = hour;
                    draft.minute = minute;
                    time.setText(String.format(
                            Locale.getDefault(), "时间：%02d:%02d", hour, minute));
                },
                draft.hour,
                draft.minute,
                true
        ).show());
        box.addView(time);

        Button app = button(
                draft.appName == null || draft.appName.isEmpty()
                        ? "选择目标 App"
                        : "目标 App：" + draft.appName
        );
        app.setOnClickListener(v -> chooseApp(draft, app));
        box.addView(app);

        EditText minutes = new EditText(this);
        minutes.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        minutes.setHint("亮屏分钟数，例如 10");
        minutes.setText(String.valueOf(draft.screenMinutes));
        box.addView(minutes);

        new AlertDialog.Builder(this)
                .setTitle(existing == null ? "添加任务" : "编辑任务")
                .setView(box)
                .setPositiveButton("保存", (dialog, which) -> {
                    try {
                        draft.screenMinutes = Math.max(
                                1,
                                Integer.parseInt(minutes.getText().toString().trim())
                        );
                    } catch (Exception ignored) {
                        draft.screenMinutes = 10;
                    }

                    if (existing == null) {
                        tasks.add(draft);
                    } else {
                        int index = tasks.indexOf(existing);
                        if (index >= 0) tasks.set(index, draft);
                    }
                    persist();
                    render();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void chooseApp(WakeTask draft, Button appButton) {
        PackageManager pm = getPackageManager();
        List<ApplicationInfo> installed = pm.getInstalledApplications(0);
        List<ApplicationInfo> launchable = new ArrayList<>();

        for (ApplicationInfo app : installed) {
            if (!app.packageName.equals(getPackageName())
                    && pm.getLaunchIntentForPackage(app.packageName) != null) {
                launchable.add(app);
            }
        }

        launchable.sort(Comparator.comparing(
                app -> pm.getApplicationLabel(app).toString().toLowerCase(Locale.getDefault())
        ));

        String[] labels = new String[launchable.size()];
        for (int i = 0; i < launchable.size(); i++) {
            labels[i] = pm.getApplicationLabel(launchable.get(i)).toString();
        }

        new AlertDialog.Builder(this)
                .setTitle("选择目标 App")
                .setItems(labels, (dialog, which) -> {
                    ApplicationInfo app = launchable.get(which);
                    draft.packageName = app.packageName;
                    draft.appName = pm.getApplicationLabel(app).toString();
                    appButton.setText("目标 App：" + draft.appName);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void persist() {
        TaskStore.save(this, tasks);
        for (WakeTask task : tasks) {
            AlarmScheduler.schedule(this, task);
        }
    }
}
