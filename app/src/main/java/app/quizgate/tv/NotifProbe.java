/*
 * QuizGate TV · VIDAA Edition
 * Fork of QuizGate TV (https://github.com/mtvtopcom/quizgate-tv), Apache-2.0.
 * Modified from the original QuizGate TV project by qualified12.
 * See NOTICE for the full list of changes.
 */

package app.quizgate.tv;

import android.content.Intent;
import android.os.Build;
import android.os.PowerManager;
import android.os.SystemClock;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

public class NotifProbe extends NotificationListenerService {
    private static final String TAG = "QuizGate";

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        reviveIfNeeded();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        reviveIfNeeded();
    }

    private void reviveIfNeeded() {
        if (App.gateAlive) return;
        Prefs p = new Prefs(this);
        if (p.safeMode() || !p.hasPin()) return;

        long bootWall = System.currentTimeMillis() - SystemClock.elapsedRealtime();
        boolean isNewBoot = Math.abs(bootWall - p.lastBootAt()) > 60_000L;
        if (isNewBoot) {
            p.setLastBootAt(bootWall);
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

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm != null && !pm.isInteractive()) return;
        try {
            Intent i = new Intent(this, GateService.class).setAction(GateService.ACTION_RESTORE);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i);
            else startService(i);
        } catch (Throwable t) {
            Log.w(TAG, "notif_revive err: " + t);
        }
    }
}
