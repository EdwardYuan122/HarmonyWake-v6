package com.oai.harmonywake;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import java.util.Calendar;
import java.util.List;

public class AlarmScheduler {

    private static final String ACTION_DAILY = "com.oai.harmonywake.ACTION_DAILY_ALARM";
    private static final String ACTION_TEST = "com.oai.harmonywake.ACTION_TEST_ALARM";

    public static void schedule(Context context, WakeTask task) {
        if (context == null || task == null) return;

        cancel(context, task.id);
        if (!task.enabled) return;

        Calendar next = Calendar.getInstance();
        next.set(Calendar.HOUR_OF_DAY, task.hour);
        next.set(Calendar.MINUTE, task.minute);
        next.set(Calendar.SECOND, 0);
        next.set(Calendar.MILLISECOND, 0);
        if (next.getTimeInMillis() <= System.currentTimeMillis()) {
            next.add(Calendar.DAY_OF_YEAR, 1);
        }

        scheduleAlarmClock(context, task, next.getTimeInMillis(), false);
    }

    /**
     * 10 秒锁屏测试使用独立 PendingIntent，不会覆盖每天的正式任务。
     */
    public static void scheduleTest(Context context, WakeTask task, long delayMillis) {
        if (context == null || task == null) return;
        long triggerAt = System.currentTimeMillis() + Math.max(3000L, delayMillis);
        scheduleAlarmClock(context, task, triggerAt, true);
    }

    private static void scheduleAlarmClock(
            Context context,
            WakeTask task,
            long triggerAtMillis,
            boolean test
    ) {
        AlarmManager alarmManager =
                (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        Intent alarmIntent = new Intent(context, AlarmReceiver.class);
        alarmIntent.setAction(test ? ACTION_TEST : ACTION_DAILY);
        alarmIntent.putExtra("task_id", task.id);
        alarmIntent.putExtra("is_test", test);

        int requestCode = test ? testRequestCode(task.id) : task.id;
        PendingIntent operation = PendingIntent.getBroadcast(
                context,
                requestCode,
                alarmIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        PendingIntent showIntent = PendingIntent.getActivity(
                context,
                showRequestCode(task.id, test),
                new Intent(context, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        alarmManager.setAlarmClock(
                new AlarmManager.AlarmClockInfo(triggerAtMillis, showIntent),
                operation
        );
    }

    public static void cancel(Context context, int taskId) {
        if (context == null) return;
        AlarmManager alarmManager =
                (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        PendingIntent dailyBroadcast = PendingIntent.getBroadcast(
                context,
                taskId,
                new Intent(context, AlarmReceiver.class).setAction(ACTION_DAILY),
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE
        );
        if (dailyBroadcast != null) {
            alarmManager.cancel(dailyBroadcast);
            dailyBroadcast.cancel();
        }

        PendingIntent testBroadcast = PendingIntent.getBroadcast(
                context,
                testRequestCode(taskId),
                new Intent(context, AlarmReceiver.class).setAction(ACTION_TEST),
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE
        );
        if (testBroadcast != null) {
            alarmManager.cancel(testBroadcast);
            testBroadcast.cancel();
        }

        // 清理 v4/v5 可能遗留的 Activity PendingIntent。
        PendingIntent oldActivity = PendingIntent.getActivity(
                context,
                taskId,
                new Intent(context, WakeActivity.class),
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE
        );
        if (oldActivity != null) {
            alarmManager.cancel(oldActivity);
            oldActivity.cancel();
        }
    }

    public static void rescheduleAll(Context context) {
        List<WakeTask> tasks = TaskStore.load(context);
        for (WakeTask task : tasks) {
            if (task != null && task.enabled) schedule(context, task);
        }
    }

    private static int testRequestCode(int taskId) {
        return taskId ^ 0x5A5A5A5A;
    }

    private static int showRequestCode(int taskId, boolean test) {
        return (taskId ^ (test ? 0x13572468 : 0x24681357));
    }
}
