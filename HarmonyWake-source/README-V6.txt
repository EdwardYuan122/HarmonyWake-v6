Harmony Wake v6
===============

本版针对 Huawei/HarmonyOS 锁屏状态下“定时不亮屏、自动上划不执行、手动解锁后也不自动打开目标 App”的问题重构了触发链路。

v6 核心变化：
1. AlarmManager 不再直接 PendingIntent.getActivity()。
2. 改为 setAlarmClock() -> BroadcastReceiver。
3. BroadcastReceiver 第一时间申请短时亮屏 WakeLock。
4. BroadcastReceiver 启动前台 ScreenOnService。
5. ScreenOnService 持有任务时长 WakeLock，并直接请求 AccessibilityService 执行全局上划。
6. 无障碍服务在检测到 Keyguard 消失后直接启动目标 App，不再依赖用户进入 Harmony Wake。
7. Full-screen alarm notification / WakeActivity 仅作为 UI 和启动目标 App 的兜底，不再承担核心触发。
8. 新增“10秒锁屏测试”，使用与正式任务相同的 BroadcastReceiver -> Service 链路。
9. 新增“查看最近执行日志”，可定位闹钟广播、前台服务、WakeLock、上划手势、解锁和目标 App 启动分别执行到哪一步。

安全限制：
- 只对无 PIN、无密码、无图案的非安全上划锁屏发送手势。
- 如果系统检测到安全凭据，只亮屏并等待用户完成认证，不尝试绕过认证。
