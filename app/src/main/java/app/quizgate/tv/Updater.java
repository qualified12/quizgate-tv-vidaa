/*
 * QuizGate TV · VIDAA Edition
 * Fork of QuizGate TV (https://github.com/mtvtopcom/quizgate-tv), Apache-2.0.
 * Modified from the original QuizGate TV project by qualified12.
 * See NOTICE for the full list of changes.
 */
package app.quizgate.tv;

import android.content.Context;

public final class Updater {
    public static final class Result {
        public String error;
        public int versionCode;
        public String versionName;
        public String apkUrl;
        public String sha256;
        public String notes;
    }

    public interface Callback {
        void onResult(Result r);
    }

    private Updater() {}

    public static void checkAsync(Context c, final Callback cb) {
        // Offline customized VIDAA build: skip network update check
    }

    public static void deliverInstallResult(Context c, int status, String msg) {
    }
}
