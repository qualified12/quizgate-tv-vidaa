/*
 * QuizGate TV · VIDAA Edition
 * Fork of QuizGate TV (https://github.com/mtvtopcom/quizgate-tv), Apache-2.0.
 * Modified from the original QuizGate TV project by qualified12.
 * See NOTICE for the full list of changes.
 */
package app.quizgate.tv;

import android.content.ContentResolver;
import android.content.Context;
import android.provider.Settings;
import org.json.JSONObject;

public final class Anchor {
    public static final String KEY = "quizgate_install_anchor";

    public static final class Report {
        public boolean enabled;
        public boolean justReinstalled;
        public int uninstalls;
        public long firstInstallAt;
        public long lastSeenAt;
        public long gapMs;
        public String warning = "";
    }

    private Anchor() {}

    public static boolean canWrite(Context c) {
        try {
            ContentResolver cr = c.getContentResolver();
            String old = Settings.Secure.getString(cr, KEY);
            boolean ok = Settings.Secure.putString(cr, KEY, old == null ? "" : old);
            if (ok) return true;
        } catch (Throwable ignore) {
        }
        try {
            if (Settings.System.canWrite(c)) {
                ContentResolver cr = c.getContentResolver();
                String old = Settings.System.getString(cr, KEY);
                return Settings.System.putString(cr, KEY, old == null ? "" : old);
            }
        } catch (Throwable ignore) {
        }
        return false;
    }

    public static Report inspectAndBump(Context c, Prefs prefs) {
        Report r = new Report();
        r.enabled = canWrite(c);
        long now = System.currentTimeMillis();
        String raw = readRaw(c);
        if (raw == null || raw.isEmpty()) {
            r.firstInstallAt = now;
            r.lastSeenAt = now;
            if (r.enabled) {
                writeRaw(c, pack(now, now, 0, 0L));
                prefs.markAnchorInit();
            }
            return r;
        }
        try {
            JSONObject o = new JSONObject(raw);
            r.firstInstallAt = o.optLong("first", now);
            r.lastSeenAt = o.optLong("seen", now);
            r.uninstalls = o.optInt("uninstalls", 0);
            r.gapMs = o.optLong("gap", 0L);
            if (!prefs.anchorInit() && !prefs.hasPin()) {
                r.uninstalls = r.uninstalls + 1;
                r.gapMs = Math.max(0L, now - r.lastSeenAt);
                r.justReinstalled = true;
            }
            if (r.uninstalls > 0) {
                long mins = Math.max(1L, r.gapMs / 60000L);
                r.warning = "⚠ 检测到曾被卸载 " + r.uninstalls + " 次 · 最近失联 " + mins + " 分钟";
            }
            if (r.enabled) {
                writeRaw(c, pack(r.firstInstallAt, now, r.uninstalls, r.gapMs));
                prefs.markAnchorInit();
            }
        } catch (Throwable ignore) {
        }
        return r;
    }

    public static void touch(Context c) {
        if (!canWrite(c)) return;
        long now = System.currentTimeMillis();
        String raw = readRaw(c);
        try {
            JSONObject o = (raw == null || raw.isEmpty()) ? new JSONObject() : new JSONObject(raw);
            long first = o.optLong("first", now);
            int u = o.optInt("uninstalls", 0);
            long gap = o.optLong("gap", 0L);
            writeRaw(c, pack(first, now, u, gap));
        } catch (Throwable ignore) {
        }
    }

    private static String pack(long first, long seen, int uninstalls, long gap) {
        try {
            JSONObject o = new JSONObject();
            o.put("v", 1);
            o.put("first", first);
            o.put("seen", seen);
            o.put("uninstalls", uninstalls);
            o.put("gap", gap);
            return o.toString();
        } catch (Throwable t) {
            return "";
        }
    }

    private static String readRaw(Context c) {
        ContentResolver cr = c.getContentResolver();
        String s1 = null, s2 = null;
        try { s1 = Settings.Secure.getString(cr, KEY); } catch (Throwable ignore) {}
        try { s2 = Settings.System.getString(cr, KEY); } catch (Throwable ignore) {}
        if (s1 != null && !s1.isEmpty()) return s1;
        return s2;
    }

    private static void writeRaw(Context c, String val) {
        ContentResolver cr = c.getContentResolver();
        try { Settings.Secure.putString(cr, KEY, val); } catch (Throwable ignore) {}
        try {
            if (Settings.System.canWrite(c)) Settings.System.putString(cr, KEY, val);
        } catch (Throwable ignore) {}
    }
}
