package cn.suizhang.ledger;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;
import java.util.concurrent.TimeUnit;

/**
 * Daily local reminder for upcoming dividend milestones. Reads only the local ledger; no holding
 * quantity, cost or account data leaves the device. The user must grant POST_NOTIFICATIONS on
 * Android 13+ or nothing is posted.
 *
 * <p>Extends {@link Worker} rather than {@code CoroutineWorker}: this project is pure Java, and
 * {@code CoroutineWorker.doWork} is a Kotlin suspend function that Java cannot override.
 */
public final class DividendReminderWorker extends Worker {

    static final String CHANNEL_ID = "dividend_milestones";
    /** Single reminder slot: today's run replaces yesterday's, and an empty window clears it. */
    static final int REMINDER_ID = 1001;
    private static final String WORK_NAME = "suizhang_dividend_reminder";

    public DividendReminderWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    /** Schedules the once-a-day check. Safe to call repeatedly; existing work is kept. */
    static void schedule(Context context) {
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(DividendReminderWorker.class, 1, TimeUnit.DAYS)
                .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.NOT_REQUIRED).build())
                .setInitialDelay(2, TimeUnit.HOURS)
                .build();
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request);
    }

    @NonNull @Override public Result doWork() {
        Context context = getApplicationContext();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return Result.success();
        }
        try {
            String message = buildReminderText(context);
            // With nothing in the window, drop the previous reminder. It used to stay in the shade
            // indefinitely after the ledger changed, describing dividends that no longer exist.
            if (message != null) notify(context, message); else cancel(context);
        } catch (Exception e) {
            // Never let a reminder failure crash or retry-loop; tomorrow's run can still succeed.
            return Result.success();
        }
        return Result.success();
    }

    /** Builds one reminder line, or null when nothing falls inside the lookahead window. */
    private String buildReminderText(Context context) {
        LedgerDatabase db = new LedgerDatabase(context);
        try {
            LocalDate today = LocalDate.now();
            JSONArray rows = db.nextDividends(0, today.toString(), 20);
            StringBuilder builder = new StringBuilder();
            int count = 0;
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.optJSONObject(i);
                if (row == null) continue;
                String kind = milestoneOf(row, today);
                if (kind == null) continue;
                String name = row.optString("holding_name", row.optString("code", "持仓"));
                if (builder.length() > 0) builder.append('\n');
                builder.append(name).append(" · ").append(kind);
                count++;
                if (count >= 4) break;
            }
            return count == 0 ? null : builder.toString();
        } finally {
            db.close();
        }
    }

    /** Delegates to the pure rules so the window can be unit tested off-device. */
    private static String milestoneOf(JSONObject row, LocalDate today) {
        if (row == null) return null;
        return DividendMilestones.milestoneOf(row.optString("record_date", ""), row.optString("ex_date", ""),
                row.optString("pay_date", ""), today);
    }

    private static void notify(Context context, String message) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "分红提醒", NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription("登记日、除息日和派息日的到期提醒");
        manager.createNotificationChannel(channel);

        Intent open = new Intent(context, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pending = PendingIntent.getActivity(context, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_nav_calendar)
                .setContentTitle("未来 7 天分红安排")
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setContentIntent(pending)
                .setVisibility(NotificationCompat.VISIBILITY_SECRET)
                .setAutoCancel(true)
                .build();
        // One stable id: each daily run replaces the previous reminder instead of stacking one
        // notification per day, which is what a per-event id would do inside a 7-day window.
        manager.notify(REMINDER_ID, notification);
    }

    private static void cancel(Context context) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager != null) manager.cancel(REMINDER_ID);
    }

}