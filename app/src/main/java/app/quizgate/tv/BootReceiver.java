/*
 * QuizGate TV · VIDAA Edition
 * Fork of QuizGate TV (https://github.com/mtvtopcom/quizgate-tv), Apache-2.0.
 * Modified from the original QuizGate TV project by qualified12.
 * See NOTICE for the full list of changes.
 */

package app.quizgate.tv;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.SystemClock;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        triggerOnBoot(context);
    }

    public static void triggerOnBoot(Context context) {
        Prefs p = new Prefs(context);
        if (p.safeMode() || !p.hasPin()) return;

        long bootWall = System.currentTimeMillis() - SystemClock.elapsedRealtime();
        boolean isNewBoot = Math.abs(bootWall - p.lastBootAt()) > 60_000L;

        if (isNewBoot) {
            p.setLastBootAt(bootWall);
            // 每次电视重新开机，自动关闭「大人模式」，恢复儿童规则保护
            p.setAdultMode(false);
            int bm = p.bootMode();
            if (p.isInCurfewNow()) {
                p.setLockReason(Prefs.LOCK_CURFEW);
                p.saveSnapshot(Prefs.LOCKED, 0L);
            } else if (p.isDailyLimitReached()) {
                p.setLockReason(Prefs.LOCK_DAILY);
                p.saveSnapshot(Prefs.LOCKED, 0L);
            } else if (p.phase() != Prefs.LOCKED) {
                if (bm == Prefs.BOOT_LOCK) {
                    p.setLockReason(Prefs.LOCK_QUIZ);
                    p.saveSnapshot(Prefs.LOCKED, 0L);
                } else if (bm == Prefs.BOOT_TIMER) {
                    long dur = p.durationMin() * 60_000L;
                    p.setLockReason(Prefs.LOCK_QUIZ);
                    p.setDeadlineAt(System.currentTimeMillis() + dur);
                    p.saveSnapshot(Prefs.RUNNING, dur);
                    p.markStartHint(5_000L);
                }
            }
        }

        try {
            Intent s = new Intent(context, GateService.class).setAction(GateService.ACTION_RESTORE);
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(s);
            else context.startService(s);
        } catch (Throwable ignore) {}
    }
}
