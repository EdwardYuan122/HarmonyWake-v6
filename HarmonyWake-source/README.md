# Harmony Wake v6

Android / Huawei HarmonyOS 4 兼容的定时唤醒工具。

## v6 为什么重构

v5 的正式闹钟直接使用 `PendingIntent.getActivity()` 启动 `WakeActivity`。在部分 Huawei/HarmonyOS 设备上，锁屏且应用位于后台时，系统会延迟或拦截这个 Activity 启动。结果是后续逻辑根本没有开始：屏幕不亮、无障碍手势没有发送、手动解锁后也没有目标 App 启动；只有再次进入 Harmony Wake，系统才可能放行之前被延迟的 Activity。

v6 改成：

`AlarmManager.setAlarmClock()`
→ `AlarmReceiver`（BroadcastReceiver）
→ 短时亮屏 WakeLock
→ `ScreenOnService`（Foreground Service）
→ 持续亮屏 WakeLock
→ `UnlockAccessibilityService.dispatchGesture()`
→ Keyguard 消失
→ AccessibilityService 直接启动目标 App

`WakeActivity` 只保留为 full-screen alarm notification 的 UI/前台启动兜底，不再承担核心定时链路。

## 测试方法

1. 安装后打开 Harmony Wake。
2. 开启“自动上划”无障碍服务。
3. 华为启动管理改为手动，并允许自启动、关联启动、后台活动。
4. 关闭 Harmony Wake 电池优化。
5. 创建任务并选择目标 App。
6. 点击“10秒锁屏测试”，马上锁屏并不要触碰手机。
7. 如果仍失败，解锁后进入 Harmony Wake → “⑤ 查看最近执行日志”，把日志内容提供出来即可精确定位。

## 安全限制

检测到 PIN / 密码 / 图案等安全锁时，不执行模拟上划绕过认证；只保持亮屏并等待用户完成系统认证。

## GitHub Actions

仓库根目录已包含 `.github/workflows/build-apk.yml`。上传完整源码后 push 到 `main` 或 `master` 即会自动构建 Debug APK，Artifact 名称：`HarmonyWake-v6-APK`。
