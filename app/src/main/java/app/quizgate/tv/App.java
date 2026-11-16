/*
 * QuizGate TV · VIDAA Edition
 * Fork of QuizGate TV (https://github.com/mtvtopcom/quizgate-tv), Apache-2.0.
 * Modified from the original QuizGate TV project by qualified12.
 * See NOTICE for the full list of changes.
 */

package app.quizgate.tv;

import android.app.Application;
import android.content.Context;
import android.util.Log;

public class App extends Application {
    private static final String TAG = "QuizGate";
    public static volatile boolean gateAlive = false;
    public static volatile boolean parentUnlocked = false;

    @Override
    public void onCreate() {
        super.onCreate();
        final Context ctx = getApplicationContext();
        final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                try {
                    if (gateAlive) {
                        new Prefs(ctx).noteCrash(t.getName(), e);
                        Log.e(TAG, "crash_in_gate", e);
                    } else {
                        Log.e(TAG, "crash_in_ui", e);
                    }
                } catch (Throwable ignore) {
                }
                if (prev != null) {
                    prev.uncaughtException(t, e);
                } else {
                    android.os.Process.killProcess(android.os.Process.myPid());
                }
            }
        });
    }
}
