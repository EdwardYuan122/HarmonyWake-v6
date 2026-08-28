package com.oai.harmonywake;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Path;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.accessibility.AccessibilityEvent;

public class UnlockAccessibilityService extends AccessibilityService {

    private static volatile UnlockAccessibilityService instance;
    private static volatile int armedTaskId = -1;
    private static volatile String armedPackageName;
    private static volatile long armedDeadline = 0L;

    public static boolean isReady() {
        return instance != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N;
    }

    public static void armTargetLaunch(int taskId, String packageName, long deadlineMillis) {
        armedTaskId = taskId;
        armedPackageName = packageName;
        armedDeadline = deadlineMillis;

        UnlockAccessibilityService service = instance;
        if (service != null) {
            service.maybeLaunchArmedTarget();
        }
    }

    public static void clearArmedTarget(int taskId) {
        if (armedTaskId != taskId) return;
        armedTaskId = -1;
        armedPackageName = null;
        armedDeadline = 0L;
    }

    public static boolean launchArmedTargetNow() {
        UnlockAccessibilityService service = instance;
        return service != null && service.maybeLaunchArmedTarget();
    }

    /**
     * 由 ScreenOnService 直接请求全局上划，不再依赖 WakeActivity 是否被系统拉起。
     */
    public static boolean performSwipeUnlock(int attempt) {
        UnlockAccessibilityService service = instance;
        if (service == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return false;
        }

        try {
            DisplayMetrics dm = service.getResources().getDisplayMetrics();
            float width = dm.widthPixels;
            float height = dm.heightPixels;

            float startX;
            float startY;
            float endX;
            float endY;
            long duration;

            switch (attempt) {
                case 1:
                    startX = width * 0.50f;
                    startY = height * 0.92f;
                    endX = width * 0.50f;
                    endY = height * 0.12f;
                    duration = 500L;
                    break;
                case 2:
                    startX = width * 0.50f;
                    startY = height * 0.96f;
                    endX = width * 0.50f;
                    endY = height * 0.06f;
                    duration = 760L;
                    break;
                case 3:
                    startX = width * 0.42f;
                    startY = height * 0.90f;
                    endX = width * 0.48f;
                    endY = height * 0.08f;
                    duration = 380L;
                    break;
                case 4:
                    startX = width * 0.58f;
                    startY = height * 0.94f;
                    endX = width * 0.52f;
                    endY = height * 0.05f;
                    duration = 900L;
                    break;
                default:
                    startX = width * 0.50f;
                    startY = height * 0.84f;
                    endX = width * 0.50f;
                    endY = height * 0.18f;
                    duration = 620L;
                    break;
            }

            Path path = new Path();
            path.moveTo(startX, startY);
            path.lineTo(endX, endY);

            GestureDescription.StrokeDescription stroke =
                    new GestureDescription.StrokeDescription(path, 50L, duration);

            GestureDescription gesture =
                    new GestureDescription.Builder()
                            .addStroke(stroke)
                            .build();

            WakeDebugLog.add(service, "AccessibilityService dispatchGesture：attempt=" + attempt);
            return service.dispatchGesture(
                    gesture,
                    new GestureResultCallback() {
                        @Override
                        public void onCompleted(GestureDescription gestureDescription) {
                            super.onCompleted(gestureDescription);
                            service.maybeLaunchArmedTarget();
                        }

                        @Override
                        public void onCancelled(GestureDescription gestureDescription) {
                            super.onCancelled(gestureDescription);
                        }
                    },
                    null
            );

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private boolean maybeLaunchArmedTarget() {
        int taskId = armedTaskId;
        String packageName = armedPackageName;
        long deadline = armedDeadline;

        if (taskId < 0 || packageName == null || packageName.trim().isEmpty()) {
            return false;
        }

        if (deadline > 0L && System.currentTimeMillis() > deadline) {
            clearArmedTarget(taskId);
            return false;
        }

        KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        if (km != null && km.isKeyguardLocked()) {
            return false;
        }

        if (!LaunchGuard.claim(taskId)) {
            clearArmedTarget(taskId);
            return true;
        }

        try {
            Intent launchIntent = getPackageManager().getLaunchIntentForPackage(packageName);
            if (launchIntent == null) {
                LaunchGuard.release(taskId);
                return false;
            }

            launchIntent.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                            | Intent.FLAG_ACTIVITY_CLEAR_TOP
            );
            startActivity(launchIntent);
            WakeDebugLog.add(this, "AccessibilityService startActivity 目标包：" + packageName);
            clearArmedTarget(taskId);
            return true;
        } catch (Exception e) {
            LaunchGuard.release(taskId);
            e.printStackTrace();
            return false;
        }
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        WakeDebugLog.add(this, "自动上划 AccessibilityService 已连接");
        maybeLaunchArmedTarget();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // 只利用窗口状态变化判断锁屏是否已经消失，不读取界面文本或控件内容。
        maybeLaunchArmedTarget();
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onDestroy() {
        if (instance == this) {
            instance = null;
        }
        super.onDestroy();
    }
}
