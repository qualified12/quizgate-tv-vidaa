/*
 * QuizGate TV · VIDAA Edition
 * Fork of QuizGate TV (https://github.com/mtvtopcom/quizgate-tv), Apache-2.0.
 * Modified from the original QuizGate TV project by qualified12.
 * See NOTICE for the full list of changes.
 */

package app.quizgate.tv;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.WindowManager;

public class GateService extends Service implements MaskView.Host {
    private static final String TAG = "QuizGate";
    public static final String ACTION_START = "app.quizgate.tv.START";
    public static final String ACTION_RESTORE = "app.quizgate.tv.RESTORE";
    public static final String ACTION_EXIT = "app.quizgate.tv.EXIT";
    public static final String ACTION_FORCE_LOCK = "app.quizgate.tv.FORCE_LOCK";
    public static final String ACTION_BONUS_15 = "app.quizgate.tv.BONUS_15";
    public static final String ACTION_ADULT_ON = "app.quizgate.tv.ADULT_ON";
    public static final String ACTION_ADULT_OFF = "app.quizgate.tv.ADULT_OFF";
    public static final String ACTION_ALARM_EXPIRE = "app.quizgate.tv.ALARM_EXPIRE";

    private static final String CHANNEL = "gate";
    private static final int NOTIF_ID = 1001;
    private static final long TICK_MS = 250L;
    private static final long MINI_THRESHOLD_MS = 30_000L;
    private static final long START_HINT_MS = 5_000L;
    private static final long STABLE_MS = 60_000L;

    private Prefs prefs;
    private QuizBank bank;
    private WindowManager wm;
    private PowerManager pm;
    private AudioManager am;
    private AudioFocusRequest focusReqTransient;
    private AudioFocusRequest focusReqGain;
    private LanServer lanServer;
    private boolean mutedByGate = false;
    private boolean wasPlayingBeforeLock = false;
    private boolean pausedByToggle = false;
    private final Handler h = new Handler(Looper.getMainLooper());

    private MaskView mask;
    private MiniView mini;
    private long remaining;
    private long deadlineElapsed = 0L;
    private long eyeElapsedMs = 0L;
    private long lastTickElapsed;
    private int tickCount;
    private boolean running;
    private boolean aborted;
    private long bootAt;
    private boolean stableMarked;
    private BroadcastReceiver screenRx;
    private boolean pendingMask = false;
    private long lastRegrabAt = 0L;
    private int focusLostCount = 0;
    private int regrabStreak = 0;
    private long screenOffElapsedAt = 0L;

    private final AudioManager.OnAudioFocusChangeListener afListener = new AudioManager.OnAudioFocusChangeListener() {
        @Override
        public void onAudioFocusChange(int focusChange) {
            if (running && prefs != null && prefs.phase() == Prefs.LOCKED && focusChange < 0) {
                h.postDelayed(new Runnable() {
                    @Override public void run() {
                        if (running && prefs != null && prefs.phase() == Prefs.LOCKED) {
                            enforceMuteInLock();
                        }
                    }
                }, 150L);
            }
        }
    };

    private final Runnable pauseStep1 = new Runnable() {
        @Override public void run() {
            if (!running || prefs == null || prefs.phase() != Prefs.LOCKED) return;
            requestMovieAudioFocus(false);
        }
    };

    private final Runnable pauseStep2 = new Runnable() {
        @Override public void run() {
            if (!running || prefs == null || prefs.phase() != Prefs.LOCKED) return;
            if (am != null && (wasPlayingBeforeLock || am.isMusicActive())) {
                requestMovieAudioFocus(true);
                dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE);
            }
        }
    };

    private final Runnable pauseStep3 = new Runnable() {
        @Override public void run() {
            if (!running || prefs == null || prefs.phase() != Prefs.LOCKED) return;
            if (am != null && wasPlayingBeforeLock && am.isMusicActive()) {
                // 针对仅响应「播放/暂停切换键」的小视频/短视频播放器自动补发切换指令
                dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
                pausedByToggle = true;
            }
        }
    };

    private final Runnable pauseStep4 = new Runnable() {
        @Override public void run() {
            if (!running || prefs == null || prefs.phase() != Prefs.LOCKED) return;
            if (am != null) {
                try {
                    am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0);
                    mutedByGate = true;
                } catch (Throwable ignore) {}
            }
        }
    };

    private final Runnable resumeStep1 = new Runnable() {
        @Override public void run() {
            if (prefs != null && prefs.phase() == Prefs.LOCKED) return;
            dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY);
        }
    };

    private final Runnable resumeStep2 = new Runnable() {
        @Override public void run() {
            if (prefs != null && prefs.phase() == Prefs.LOCKED) return;
            if (am != null && wasPlayingBeforeLock && !am.isMusicActive()) {
                dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY);
            }
        }
    };

    private final Runnable resumeStep3 = new Runnable() {
        @Override public void run() {
            if (prefs != null && prefs.phase() == Prefs.LOCKED) return;
            if (am != null && (wasPlayingBeforeLock || pausedByToggle) && !am.isMusicActive()) {
                dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
            }
            wasPlayingBeforeLock = false;
            pausedByToggle = false;
        }
    };

    private final Runnable hintDismiss = new Runnable() {
        @Override public void run() {
            prefs.clearStartHint();
            if (running && prefs.phase() == Prefs.RUNNING && remaining > MINI_THRESHOLD_MS) removeMini();
        }
    };

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (!running) return;
            long nowEl = SystemClock.elapsedRealtime();
            long delta = nowEl - lastTickElapsed;
            lastTickElapsed = nowEl;
            if (delta < 0) delta = TICK_MS;

            if (!stableMarked && SystemClock.uptimeMillis() - bootAt >= STABLE_MS) {
                stableMarked = true;
                prefs.markStable();
            }

            if (prefs.adultMode()) {
                if (mask != null) removeMask();
                if (mini != null) removeMini();
                tickCount = tickCount + 1;
                if (tickCount % 120 == 0) updateNotification();
                h.postDelayed(this, TICK_MS);
                return;
            }

            if (prefs.phase() == Prefs.LOCKED && pendingMask && screenOn()) {
                pendingMask = false;
                showMask(prefs.lockReason());
            }

            if (prefs.isInCurfewNow() && (prefs.phase() != Prefs.LOCKED || prefs.lockReason() != Prefs.LOCK_CURFEW)) {
                triggerLock(Prefs.LOCK_CURFEW);
                h.postDelayed(this, TICK_MS);
                return;
            }

            if (prefs.phase() == Prefs.LOCKED) {
                keepMaskOnTop();
                tickCount = tickCount + 1;
                if (tickCount % 4 == 0 && screenOn()) {
                    enforceMuteInLock();
                }
            } else {
                if (screenOn()) {
                    prefs.addDailyUsedMs(Math.min(delta, 10_000L));
                    if (prefs.isDailyLimitReached()) {
                        triggerLock(Prefs.LOCK_DAILY);
                        h.postDelayed(this, TICK_MS);
                        return;
                    }
                    int eyeInt = prefs.eyeIntervalMin();
                    if (eyeInt > 0) {
                        eyeElapsedMs = eyeElapsedMs + Math.min(delta, 10_000L);
                        if (eyeElapsedMs >= eyeInt * 60_000L) {
                            eyeElapsedMs = 0L;
                            triggerLock(Prefs.LOCK_EYECARE);
                            h.postDelayed(this, TICK_MS);
                            return;
                        }
                    }
                    if (prefs.phase() == Prefs.RUNNING) {
                        if (deadlineElapsed > 0L) {
                            remaining = deadlineElapsed - nowEl;
                        } else {
                            remaining = remaining - delta;
                            deadlineElapsed = nowEl + remaining;
                        }
                        if (remaining <= 0L) {
                            remaining = 0L;
                            triggerLock(Prefs.LOCK_QUIZ);
                            h.postDelayed(this, TICK_MS);
                            return;
                        }
                    }
                } else if (prefs.phase() == Prefs.RUNNING) {
                    deadlineElapsed = nowEl + remaining;
                }
                tickCount = tickCount + 1;
                if (tickCount % 4 == 0) {
                    if (prefs.phase() == Prefs.RUNNING) {
                        prefs.tickSnapshot(remaining);
                        updateMini();
                    }
                    if (tickCount % 240 == 0) Anchor.touch(GateService.this);
                    if (tickCount % 80 == 0) updateNotification();
                }
            }
            h.postDelayed(this, TICK_MS);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        App.gateAlive = true;
        prefs = new Prefs(this);
        prefs.sweepStartHint();

        startAsForeground();

        if (!admitBoot()) {
            aborted = true;
            stopForeground(true);
            stopSelf();
            return;
        }

        bank = new QuizBank(this);
        wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        lanServer = new LanServer(this);
        lanServer.start();

        registerScreenReceiver();
        remaining = prefs.remainingMs();
        lastTickElapsed = SystemClock.elapsedRealtime();
        Log.i(TAG, "service_create " + prefs.debugLine());
    }

    private boolean admitBoot() {
        prefs.consumeCrash();
        if (prefs.safeMode()) {
            notifyOnce("守护已停止（安全模式）：请到设置页恢复");
            return false;
        }
        bootAt = SystemClock.uptimeMillis();
        return true;
    }

    private void beginCountdown(long durationMs, boolean showStartHint) {
        remaining = Math.max(1000L, durationMs);
        deadlineElapsed = SystemClock.elapsedRealtime() + remaining;
        prefs.setDeadlineAt(System.currentTimeMillis() + remaining);
        prefs.saveSnapshot(Prefs.RUNNING, remaining);
        prefs.setLockReason(Prefs.LOCK_QUIZ);
        if (showStartHint) {
            prefs.markStartHint(START_HINT_MS);
            scheduleHintDismiss();
        }
        scheduleBackupAlarm(remaining);
        Log.i(TAG, "begin_countdown rem=" + Core.fmt(remaining));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        Log.i(TAG, "onStartCommand action=" + action + " phase=" + (prefs == null ? -1 : prefs.phase()));

        if (aborted || prefs == null || prefs.safeMode()) {
            stopSelf();
            return START_NOT_STICKY;
        }

        if (!Settings.canDrawOverlays(this)) {
            prefs.resetSession();
            restoreMedia(false);
            notifyOnce("缺少悬浮窗权限，守护已停止");
            stopSelf();
            return START_NOT_STICKY;
        }

        if (ACTION_EXIT.equals(action)) {
            exitSession();
            return START_NOT_STICKY;
        }

        if (ACTION_ADULT_ON.equals(action)) {
            prefs.setAdultMode(true);
            cancelBackupAlarm();
            removeMask();
            removeMini();
            restoreMedia(true);
            updateNotification();
        } else if (ACTION_ADULT_OFF.equals(action)) {
            prefs.setAdultMode(false);
            if (prefs.bootMode() == Prefs.BOOT_LOCK) {
                triggerLock(Prefs.LOCK_QUIZ);
            } else {
                beginCountdown(prefs.durationMin() * 60_000L, true);
                reconcile();
            }
        } else if (ACTION_FORCE_LOCK.equals(action)) {
            prefs.setAdultMode(false);
            triggerLock(Prefs.LOCK_QUIZ);
        } else if (ACTION_ALARM_EXPIRE.equals(action)) {
            if (!prefs.adultMode() && prefs.phase() != Prefs.LOCKED) {
                Log.i(TAG, "alarm_expire -> 立即触发到点锁屏答题");
                triggerLock(Prefs.LOCK_QUIZ);
            }
        } else if (ACTION_BONUS_15.equals(action)) {
            if (prefs.phase() == Prefs.LOCKED && prefs.lockReason() == Prefs.LOCK_DAILY) {
                beginCountdown(15 * 60_000L, true);
                removeMask();
                restoreMedia(true);
            }
        } else if (ACTION_START.equals(action)) {
            prefs.setAdultMode(false);
            removeMask();
            restoreMedia(false);
            beginCountdown(prefs.durationMin() * 60_000L, true);
        } else {
            if (!prefs.adultMode()) {
                if (prefs.phase() == Prefs.RUNNING) {
                    long dl = prefs.deadlineAt();
                    long nowWall = System.currentTimeMillis();
                    if (dl > 0L) {
                        long left = dl - nowWall;
                        if (left <= 0L) {
                            triggerLock(Prefs.LOCK_QUIZ);
                        } else {
                            remaining = left;
                            deadlineElapsed = SystemClock.elapsedRealtime() + remaining;
                            scheduleBackupAlarm(remaining);
                        }
                    } else {
                        remaining = prefs.remainingMs();
                        deadlineElapsed = SystemClock.elapsedRealtime() + remaining;
                        scheduleBackupAlarm(remaining);
                    }
                } else if (prefs.phase() == Prefs.IDLE && prefs.extendMin() != -1) {
                    beginCountdown(prefs.durationMin() * 60_000L, true);
                }
            }
        }

        lastTickElapsed = SystemClock.elapsedRealtime();
        running = true;
        scheduleHintDismiss();
        reconcile();
        h.removeCallbacks(ticker);
        h.post(ticker);
        updateNotification();
        return START_STICKY;
    }

    private PendingIntent alarmPi() {
        Intent i = new Intent(this, GateService.class).setAction(ACTION_ALARM_EXPIRE);
        int flags = PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 26) return PendingIntent.getForegroundService(this, 2001, i, flags);
        return PendingIntent.getService(this, 2001, i, flags);
    }

    private void scheduleBackupAlarm(long delayMs) {
        try {
            AlarmManager amgr = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (amgr == null) return;
            long at = System.currentTimeMillis() + Math.max(1000L, delayMs);
            if (Build.VERSION.SDK_INT >= 23) amgr.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, alarmPi());
            else amgr.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, alarmPi());
        } catch (Throwable t) {
            Log.w(TAG, "backup_alarm err: " + t);
        }
    }

    private void cancelBackupAlarm() {
        try {
            AlarmManager amgr = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (amgr != null) amgr.cancel(alarmPi());
        } catch (Throwable ignore) {}
    }

    private void triggerLock(int reason) {
        cancelBackupAlarm();
        Log.i(TAG, "triggerLock reason=" + reason);
        prefs.setLockReason(reason);
        prefs.setDeadlineAt(0L);
        prefs.saveSnapshot(Prefs.LOCKED, 0);
        remaining = 0;
        deadlineElapsed = 0L;
        removeMini();
        removeMask();
        if (screenOn()) showMask(reason);
        else pendingMask = true;
        updateNotification();
    }

    private void cancelPauseCallbacks() {
        h.removeCallbacks(pauseStep1);
        h.removeCallbacks(pauseStep2);
        h.removeCallbacks(pauseStep3);
        h.removeCallbacks(pauseStep4);
    }

    private void cancelResumeCallbacks() {
        h.removeCallbacks(resumeStep1);
        h.removeCallbacks(resumeStep2);
        h.removeCallbacks(resumeStep3);
    }

    private void dispatchMediaKey(int keyCode) {
        if (am == null) am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (am == null) return;
        try {
            long now = SystemClock.uptimeMillis();
            am.dispatchMediaKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0));
            am.dispatchMediaKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0));
        } catch (Throwable ignore) {}
    }

    private void requestMovieAudioFocus(boolean permanentGain) {
        if (am == null) am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (am == null) return;
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                AudioAttributes attrs = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                        .build();
                if (permanentGain) {
                    if (focusReqGain == null) {
                        focusReqGain = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                                .setAudioAttributes(attrs)
                                .setOnAudioFocusChangeListener(afListener, h)
                                .build();
                    }
                    am.requestAudioFocus(focusReqGain);
                } else {
                    if (focusReqTransient == null) {
                        focusReqTransient = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                                .setAudioAttributes(attrs)
                                .setOnAudioFocusChangeListener(afListener, h)
                                .build();
                    }
                    am.requestAudioFocus(focusReqTransient);
                }
            } else {
                am.requestAudioFocus(afListener, AudioManager.STREAM_MUSIC,
                        permanentGain ? AudioManager.AUDIOFOCUS_GAIN : AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
            }
        } catch (Throwable ignore) {}
    }

    private void pauseAndMuteMedia() {
        if (am == null) am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (am == null) return;
        cancelPauseCallbacks();
        cancelResumeCallbacks();

        try {
            if (mutedByGate) {
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0);
                mutedByGate = false;
            }
        } catch (Throwable ignore) {}

        wasPlayingBeforeLock = am.isMusicActive();
        pausedByToggle = false;
        Log.i(TAG, "pauseAndMuteMedia wasPlaying=" + wasPlayingBeforeLock);

        // 第 1 步（0ms）：趁后台小视频/点播播放器仍持有音频焦点与窗口焦点，第一时间发送标准暂停键 KEYCODE_MEDIA_PAUSE
        dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE);

        // 第 2 步（60ms）：使用 USAGE_MEDIA + CONTENT_TYPE_MOVIE 抢占临时影视音频焦点（规避海信语音助手 CONTENT_TYPE_SPEECH 白名单）
        h.postDelayed(pauseStep1, 60L);

        // 第 3 步（160ms）：若仍在播放，升级为永久影视焦点 AUDIOFOCUS_GAIN 并再次发送暂停键
        h.postDelayed(pauseStep2, 160L);

        // 第 4 步（280ms）：若小视频播放器仍未暂停（部分短视频仅监听 PLAY_PAUSE 切换键），自动发送一次切换暂停并记录状态
        h.postDelayed(pauseStep3, 280L);

        // 第 5 步（400ms）：兜底静音 STREAM_MUSIC（确保即便看无法暂停的电视直播也彻底静音）
        h.postDelayed(pauseStep4, 400L);
    }

    private void enforceMuteInLock() {
        if (am == null) return;
        try {
            if (mutedByGate && !am.isStreamMute(AudioManager.STREAM_MUSIC)) {
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0);
            }
        } catch (Throwable ignore) {}
    }

    private void restoreMedia(boolean resumePlayback) {
        if (am == null) am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (am == null) return;
        cancelPauseCallbacks();
        try {
            if (mutedByGate || am.isStreamMute(AudioManager.STREAM_MUSIC)) {
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0);
                mutedByGate = false;
            }
        } catch (Throwable ignore) {}
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                if (focusReqTransient != null) am.abandonAudioFocusRequest(focusReqTransient);
                if (focusReqGain != null) am.abandonAudioFocusRequest(focusReqGain);
            } else {
                am.abandonAudioFocus(afListener);
            }
        } catch (Throwable ignore) {}

        if (resumePlayback) {
            cancelResumeCallbacks();
            // 遮罩移除、焦点归还后分阶段自动恢复小视频/点播画面播放
            h.postDelayed(resumeStep1, 180L);
            h.postDelayed(resumeStep2, 480L);
            h.postDelayed(resumeStep3, 760L);
        }
    }

    @Override
    public void onSolved() {
        int extMin = prefs.extendMin();
        removeMask();
        restoreMedia(true);
        if (extMin == -1) {
            cancelBackupAlarm();
            prefs.setDeadlineAt(0L);
            prefs.saveSnapshot(Prefs.IDLE, 0L);
            prefs.setLockReason(Prefs.LOCK_QUIZ);
            removeMini();
            updateNotification();
            return;
        }
        int nextMin = extMin > 0 ? extMin : prefs.durationMin();
        beginCountdown(nextMin * 60_000L, true);
        lastTickElapsed = SystemClock.elapsedRealtime();
        updateMini();
        updateNotification();
    }

    @Override
    public void onEyeCareDone() {
        eyeElapsedMs = 0L;
        removeMask();
        restoreMedia(true);
        if (remaining > 0L) {
            beginCountdown(remaining, false);
        } else if (prefs.extendMin() != -1) {
            beginCountdown(prefs.durationMin() * 60_000L, false);
        } else {
            prefs.saveSnapshot(Prefs.IDLE, 0L);
        }
        lastTickElapsed = SystemClock.elapsedRealtime();
        updateMini();
        updateNotification();
    }

    @Override
    public void onPinOk() {
        int reason = prefs.lockReason();
        if (reason == Prefs.LOCK_CURFEW) prefs.bypassCurfewToday();
        if (reason == Prefs.LOCK_DAILY) prefs.setDailyBypass(true);
        removeMask();
        removeMini();
        restoreMedia(true);
        if (prefs.extendMin() != -1) {
            int nextMin = prefs.extendMin() > 0 ? prefs.extendMin() : prefs.durationMin();
            beginCountdown(nextMin * 60_000L, true);
        } else {
            cancelBackupAlarm();
            prefs.setDeadlineAt(0L);
            prefs.saveSnapshot(Prefs.IDLE, 0L);
        }
        updateNotification();
    }

    private void exitSession() {
        cancelBackupAlarm();
        removeMask();
        removeMini();
        restoreMedia(true);
        prefs.markStable();
        prefs.setDeadlineAt(0L);
        prefs.resetSession();
        updateNotification();
    }

    private void reconcile() {
        if (prefs.adultMode()) {
            removeMask();
            removeMini();
            restoreMedia(false);
            return;
        }
        int p = prefs.phase();
        if (p == Prefs.LOCKED) {
            removeMini();
            showMask(prefs.lockReason());
        } else if (p == Prefs.RUNNING) {
            removeMask();
            restoreMedia(false);
            if (remaining <= MINI_THRESHOLD_MS || prefs.startHintOn()) showMini(); else removeMini();
        } else {
            removeMask();
            removeMini();
            restoreMedia(false);
        }
    }

    private void keepMaskOnTop() {
        if (!screenOn()) return;
        long now = SystemClock.uptimeMillis();
        if (mask == null) {
            if (now - lastRegrabAt < 2500L) return;
            lastRegrabAt = now;
            showMask(prefs.lockReason());
            return;
        }
        if (mask.hasWindowFocus()) {
            focusLostCount = 0;
            regrabStreak = 0;
            return;
        }
        if (now - mask.lastKeyAt() < 4000L) {
            focusLostCount = 0;
            return;
        }
        focusLostCount = focusLostCount + 1;
        if (focusLostCount == 3) {
            try {
                mask.requestFocus();
                wm.updateViewLayout(mask, maskLp());
            } catch (Throwable ignore) {}
            return;
        }
        long cooldown = Math.min(56_000L, 3500L * (1L << Math.min(4, regrabStreak)));
        if (focusLostCount < 8 || now - lastRegrabAt < cooldown) return;

        lastRegrabAt = now;
        focusLostCount = 0;
        regrabStreak = regrabStreak + 1;
        reattachMaskInPlace();
    }

    private void reattachMaskInPlace() {
        if (mask == null || !screenOn()) return;
        try { wm.removeViewImmediate(mask); } catch (Throwable ignore) {}
        try {
            wm.addView(mask, maskLp());
            mask.requestFocus();
        } catch (Throwable ignore) {}
    }

    private boolean screenOn() {
        if (pm != null && !pm.isInteractive()) return false;
        try {
            DisplayManager dm = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
            Display d = dm == null ? null : dm.getDisplay(Display.DEFAULT_DISPLAY);
            if (d != null) {
                int s = d.getState();
                if (s == Display.STATE_OFF || s == Display.STATE_DOZE
                        || s == Display.STATE_DOZE_SUSPEND || s == Display.STATE_UNKNOWN) {
                    return false;
                }
            }
        } catch (Throwable ignore) {}
        return true;
    }

    private WindowManager.LayoutParams maskLp() {
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM,
                PixelFormat.OPAQUE);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.setTitle("QuizGateMask");
        return lp;
    }

    private void showMask(int reason) {
        if (mask != null) {
            enforceMuteInLock();
            return;
        }
        if (!screenOn()) {
            pendingMask = true;
            return;
        }
        pendingMask = false;
        // 必须在添加遮罩抢走窗口焦点之前，立刻触发视频暂停序列！
        pauseAndMuteMedia();
        mask = new MaskView(this, prefs, bank, reason, this);
        focusLostCount = 0;
        regrabStreak = 0;
        try {
            wm.addView(mask, maskLp());
            mask.requestFocus();
        } catch (Throwable t) {
            mask = null;
        }
    }

    private void removeMask() {
        if (mask == null) return;
        try { wm.removeViewImmediate(mask); } catch (Throwable ignore) {}
        mask = null;
    }

    private void showMini() {
        if (mini != null) return;
        mini = new MiniView(this);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.BOTTOM | Gravity.END;
        lp.x = Ui.dp(this, 40);
        lp.y = Ui.dp(this, 48);
        lp.setTitle("QuizGateMini");
        try {
            wm.addView(mini, lp);
            mini.setRemaining(remaining);
            mini.setCaption(prefs.startHintOn() ? "本次时长" : "剩余");
        } catch (Throwable t) {
            mini = null;
        }
    }

    private void removeMini() {
        if (mini == null) return;
        try { wm.removeViewImmediate(mini); } catch (Throwable ignore) {}
        mini = null;
    }

    private void scheduleHintDismiss() {
        h.removeCallbacks(hintDismiss);
        long left = prefs.startHintLeftMs();
        if (prefs.startHintOn()) h.postDelayed(hintDismiss, left);
    }

    private void updateMini() {
        if (prefs.phase() != Prefs.RUNNING) { removeMini(); return; }
        boolean hint = prefs.startHintOn();
        if (hint || remaining <= MINI_THRESHOLD_MS) {
            if (mini == null) showMini();
            if (mini != null) {
                mini.setRemaining(remaining);
                mini.setCaption(hint ? "本次时长" : "剩余");
            }
        } else if (mini != null) {
            removeMini();
        }
    }

    private void startAsForeground() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "守护运行", NotificationManager.IMPORTANCE_LOW);
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
        }
        Notification n = buildNotification("规则自动守护中", true);
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIF_ID, n);
        }
    }

    private Notification buildNotification(String text, boolean ongoing) {
        PendingIntent pi = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL)
                : new Notification.Builder(this);
        return b.setContentTitle("答题续时 · 规则守护")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
                .setOngoing(ongoing)
                .setAutoCancel(!ongoing)
                .setContentIntent(pi)
                .build();
    }

    private void updateNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        String s = prefs.adultMode() ? "大人模式（管控已暂停）"
                : (prefs.phase() == Prefs.LOCKED ? "已锁屏拦截"
                : (prefs.phase() == Prefs.RUNNING ? ("剩余 " + Core.fmt(remaining)) : "全规则自动监控中"));
        try { nm.notify(NOTIF_ID, buildNotification(s, true)); } catch (Throwable ignore) {}
    }

    private void notifyOnce(String s) {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        try { nm.notify(NOTIF_ID + 1, buildNotification(s, false)); } catch (Throwable ignore) {}
    }

    private void registerScreenReceiver() {
        screenRx = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                String a = i.getAction();
                if (Intent.ACTION_SCREEN_OFF.equals(a)) {
                    screenOffElapsedAt = SystemClock.elapsedRealtime();
                    restoreMedia(false);
                    if (mask != null) {
                        removeMask();
                        pendingMask = true;
                    }
                }
                lastTickElapsed = SystemClock.elapsedRealtime();
                if (Intent.ACTION_SCREEN_ON.equals(a)) {
                    int relockMin = prefs.idleRelockMin();
                    if (!prefs.adultMode() && relockMin > 0 && screenOffElapsedAt > 0L) {
                        long offDur = SystemClock.elapsedRealtime() - screenOffElapsedAt;
                        if (offDur >= relockMin * 60_000L && prefs.phase() != Prefs.LOCKED) {
                            screenOffElapsedAt = 0L;
                            triggerLock(Prefs.LOCK_QUIZ);
                            return;
                        }
                    }
                    if (!prefs.adultMode() && prefs.phase() == Prefs.RUNNING && remaining > 0L) {
                        deadlineElapsed = SystemClock.elapsedRealtime() + remaining;
                        prefs.setDeadlineAt(System.currentTimeMillis() + remaining);
                        scheduleBackupAlarm(remaining);
                    }
                    reconcile();
                }
            }
        };
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_SCREEN_ON);
        f.addAction(Intent.ACTION_SCREEN_OFF);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenRx, f, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(screenRx, f);
    }

    @Override
    public void onDestroy() {
        running = false;
        App.gateAlive = false;
        h.removeCallbacks(ticker);
        cancelPauseCallbacks();
        cancelResumeCallbacks();
        removeMask();
        removeMini();
        restoreMedia(false);
        if (lanServer != null) lanServer.stop();
        if (screenRx != null) {
            try { unregisterReceiver(screenRx); } catch (Throwable ignore) {}
        }
        super.onDestroy();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        if (prefs != null && !prefs.safeMode() && !prefs.adultMode() && prefs.hasPin()) {
            scheduleBackupAlarm(1500L);
        }
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
