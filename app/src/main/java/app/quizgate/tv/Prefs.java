/*
 * QuizGate TV · VIDAA Edition
 * Fork of QuizGate TV (https://github.com/mtvtopcom/quizgate-tv), Apache-2.0.
 * Modified from the original QuizGate TV project by qualified12.
 * See NOTICE for the full list of changes.
 */

package app.quizgate.tv;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class Prefs {
    public static final int IDLE = 0, RUNNING = 1, LOCKED = 2;
    public static final int BOOT_OFF = 0, BOOT_LOCK = 1, BOOT_TIMER = 2;

    // Lock reasons
    public static final int LOCK_QUIZ = 0;
    public static final int LOCK_DAILY = 1;
    public static final int LOCK_CURFEW = 2;
    public static final int LOCK_EYECARE = 3;

    private static final String F = "gate";
    private static final int PBKDF2_ITER = 60000;
    private static final int MAX_FAIL = 5;
    private static final long LOCKOUT_MS = 30_000L;
    private static final int MAX_RC_FAIL = 5;
    private static final long RC_LOCKOUT_MS = 30 * 60_000L;

    public static final int STAGE_PRIMARY = 0, STAGE_JUNIOR = 1;
    public static final int SUBJECT_CHINESE = 0;
    public static final int SUBJECT_MATH = 1;
    public static final int SUBJECT_ENGLISH = 2;
    public static final int SUBJECT_SCIENCE = 3;
    public static final String[] SUBJECT_KEYS = {"chinese", "math", "english", "science"};
    public static final String[] SUBJECT_NAMES = {"语文", "数学", "英语", "科学百科"};
    public static final int ALL_SUBJECTS_MASK = 0x0F;
    public static final int PRIMARY_GRADES_MASK = 0x3F;
    public static final int JUNIOR_GRADES_MASK = 0x1C0;
    public static final int ALL_GRADES_MASK = 0x1FF;

    private final Context appCtx;
    private final SharedPreferences sp;

    public Prefs(Context c) {
        appCtx = c.getApplicationContext();
        sp = appCtx.getSharedPreferences(F, Context.MODE_PRIVATE);
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    // ---------- 基础定时与题库设置 ----------
    public int durationMin() { return sp.getInt("durationMin", 30); }
    public void setDurationMin(int v) { sp.edit().putInt("durationMin", clamp(v, 1, 240)).commit(); }

    public int extendMin() { return clamp(sp.getInt("extendMin", 0), -1, 60); }
    public void setExtendMin(int v) { sp.edit().putInt("extendMin", clamp(v, -1, 60)).commit(); }

    public String extendSummary() {
        int e = extendMin();
        if (e == -1) return "单次模式（答对1次后不再弹题）";
        if (e == 0) return "同上（每满 " + durationMin() + " 分钟循环答题）";
        return "答对后看 " + e + " 分钟再答题";
    }

    public long deadlineAt() { return sp.getLong("deadlineAt", 0L); }
    public void setDeadlineAt(long v) { sp.edit().putLong("deadlineAt", v).commit(); }


    // ---------- 断电/重启持久化当前答题进度与加罚题数 ----------
    public int activeQuizTarget() { return sp.getInt("activeQuizTarget", 0); }
    public void setActiveQuizTarget(int v) { sp.edit().putInt("activeQuizTarget", clamp(v, 0, 50)).commit(); }

    public int activeQuizCorrect() { return sp.getInt("activeQuizCorrect", 0); }
    public void setActiveQuizCorrect(int v) { sp.edit().putInt("activeQuizCorrect", Math.max(0, v)).commit(); }

    public int activeQuizWrongStreak() { return sp.getInt("activeQuizWrongStreak", 0); }
    public void setActiveQuizWrongStreak(int v) { sp.edit().putInt("activeQuizWrongStreak", Math.max(0, v)).commit(); }

    public long activeQuizCooldownUntilWall() { return sp.getLong("activeQuizCooldownWall", 0L); }
    public void setActiveQuizCooldownUntilWall(long wallMs) { sp.edit().putLong("activeQuizCooldownWall", Math.max(0L, wallMs)).commit(); }

    public void clearActiveQuizProgress() {
        sp.edit()
            .remove("activeQuizTarget")
            .remove("activeQuizCorrect")
            .remove("activeQuizWrongStreak")
            .remove("activeQuizCooldownWall")
            .commit();
    }

    public int quizCount() { return sp.getInt("quizCount", 3); }
    public void setQuizCount(int v) { sp.edit().putInt("quizCount", clamp(v, 1, 20)).commit(); }

    public int wrongPenalty() { return sp.getInt("wrongPenalty", 1); }
    public void setWrongPenalty(int v) { sp.edit().putInt("wrongPenalty", clamp(v, 0, 5)).commit(); }

    public int bootMode() { return clamp(sp.getInt("bootMode", BOOT_TIMER), 0, 2); }
    public void setBootMode(int v) { sp.edit().putInt("bootMode", clamp(v, 0, 2)).commit(); }

    public String bootModeName() {
        int m = bootMode();
        if (m == BOOT_LOCK) return "开机直接锁屏答题";
        if (m == BOOT_TIMER) return "开机自动开始计时";
        return "关闭（需手动点开始）";
    }

    public long lastBootAt() { return sp.getLong("lastBootAt", 0L); }
    public void setLastBootAt(long v) { sp.edit().putLong("lastBootAt", v).commit(); }

    // ---------- 大人模式（一键免控，下次开机自动恢复） ----------
    public boolean adultMode() { return sp.getBoolean("adultMode", false); }
    public void setAdultMode(boolean v) { sp.edit().putBoolean("adultMode", v).commit(); }

    // ---------- 息屏/待机超时重锁（分钟，0=关闭，默认15分钟） ----------
    public int idleRelockMin() { return clamp(sp.getInt("idleRelockMin", 15), 0, 120); }
    public void setIdleRelockMin(int v) { sp.edit().putInt("idleRelockMin", clamp(v, 0, 120)).commit(); }

    // ---------- 防瞎蒙设置 ----------
    /** 连续答错几题触发冷却锁定（0=关闭，默认2题） */
    public int guessTrigger() { return clamp(sp.getInt("guessTrigger", 2), 0, 5); }
    public void setGuessTrigger(int v) { sp.edit().putInt("guessTrigger", clamp(v, 0, 5)).commit(); }

    /** 冷却锁定秒数（默认30秒） */
    public int guessCooldownSec() { return clamp(sp.getInt("guessCooldownSec", 30), 10, 120); }
    public void setGuessCooldownSec(int v) { sp.edit().putInt("guessCooldownSec", clamp(v, 10, 120)).commit(); }

    /** 每题最少读题秒数（默认3秒，防连按1111瞎蒙） */
    public int minReadSec() { return clamp(sp.getInt("minReadSec", 3), 0, 10); }
    public void setMinReadSec(int v) { sp.edit().putInt("minReadSec", clamp(v, 0, 10)).commit(); }

    // ---------- 每日总时长上限 ----------
    /** 每日最大观看分钟数（0=不限，默认90分钟） */
    public int dailyLimitMin() { return clamp(sp.getInt("dailyLimitMin", 90), 0, 480); }
    public void setDailyLimitMin(int v) { sp.edit().putInt("dailyLimitMin", clamp(v, 0, 480)).commit(); }

    public static String todayKey() {
        Calendar cal = Calendar.getInstance();
        return String.format(Locale.US, "%04d-%02d-%02d",
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH));
    }

    private void syncDailyDate() {
        String today = todayKey();
        String saved = sp.getString("dailyDate", "");
        if (!today.equals(saved)) {
            sp.edit()
                .putString("dailyDate", today)
                .putLong("dailyUsedMs", 0L)
                .putInt("dailyBonusMin", 0)
                .putBoolean("dailyBypass", false)
                .commit();
        }
    }

    public long dailyUsedMs() {
        syncDailyDate();
        return sp.getLong("dailyUsedMs", 0L);
    }

    public void addDailyUsedMs(long delta) {
        if (delta <= 0) return;
        syncDailyDate();
        long cur = sp.getLong("dailyUsedMs", 0L) + delta;
        sp.edit().putLong("dailyUsedMs", cur).apply();
    }

    public int dailyBonusMin() {
        syncDailyDate();
        return sp.getInt("dailyBonusMin", 0);
    }

    public void addDailyBonusMin(int min) {
        syncDailyDate();
        int cur = sp.getInt("dailyBonusMin", 0) + min;
        sp.edit().putInt("dailyBonusMin", cur).putBoolean("dailyBypass", false).commit();
    }

    public boolean dailyBypass() {
        syncDailyDate();
        return sp.getBoolean("dailyBypass", false);
    }

    public void setDailyBypass(boolean v) {
        syncDailyDate();
        sp.edit().putBoolean("dailyBypass", v).commit();
    }

    public boolean isDailyLimitReached() {
        int lim = dailyLimitMin();
        if (lim <= 0 || dailyBypass()) return false;
        long totalAllowed = (long) (lim + dailyBonusMin()) * 60_000L;
        return dailyUsedMs() >= totalAllowed;
    }

    // ---------- 夜间宵禁时段 ----------
    /** 宵禁结束分钟数/开始分钟数，预设模式：0=关闭, 1=21:00-07:00, 2=21:30-07:00, 3=22:00-07:00, 4=20:30-07:00 */
    public int curfewPreset() { return clamp(sp.getInt("curfewPreset", 1), 0, 4); }
    public void setCurfewPreset(int v) { sp.edit().putInt("curfewPreset", clamp(v, 0, 4)).commit(); }

    public String curfewName() {
        int p = curfewPreset();
        if (p == 1) return "21:00 - 次日 07:00";
        if (p == 2) return "21:30 - 次日 07:00";
        if (p == 3) return "22:00 - 次日 07:00";
        if (p == 4) return "20:30 - 次日 07:00";
        return "已关闭";
    }

    public boolean isInCurfewNow() {
        int p = curfewPreset();
        if (p == 0) return false;
        String today = todayKey();
        if (today.equals(sp.getString("curfewBypassDate", ""))) return false;

        int startMin = 21 * 60;
        if (p == 2) startMin = 21 * 60 + 30;
        else if (p == 3) startMin = 22 * 60;
        else if (p == 4) startMin = 20 * 60 + 30;
        int endMin = 7 * 60;

        Calendar cal = Calendar.getInstance();
        int nowMin = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE);
        return nowMin >= startMin || nowMin < endMin;
    }

    public void bypassCurfewToday() {
        sp.edit().putString("curfewBypassDate", todayKey()).commit();
    }

    // ---------- 护眼强制休息 ----------
    /** 连续观看多少分钟休息一次（0=关闭，默认30分钟） */
    public int eyeIntervalMin() { return clamp(sp.getInt("eyeIntervalMin", 30), 0, 120); }
    public void setEyeIntervalMin(int v) { sp.edit().putInt("eyeIntervalMin", clamp(v, 0, 120)).commit(); }

    /** 每次护眼休息秒数（默认60秒） */
    public int eyeRestSec() { return clamp(sp.getInt("eyeRestSec", 60), 15, 300); }
    public void setEyeRestSec(int v) { sp.edit().putInt("eyeRestSec", clamp(v, 15, 300)).commit(); }

    // ---------- 锁屏原因 ----------
    public int lockReason() { return clamp(sp.getInt("lockReason", LOCK_QUIZ), 0, 3); }
    public void setLockReason(int r) { sp.edit().putInt("lockReason", clamp(r, 0, 3)).commit(); }

    // ---------- 星星积分银行 ----------
    public int stars() { return sp.getInt("stars", 0); }
    public void addStar(int delta) {
        int cur = Math.max(0, sp.getInt("stars", 0) + delta);
        sp.edit().putInt("stars", cur).commit();
        Anchor.saveStarsBackup(appCtx, cur);
    }
    public void setStars(int v) {
        int cur = Math.max(0, v);
        sp.edit().putInt("stars", cur).commit();
        Anchor.saveStarsBackup(appCtx, cur);
    }

    // ---------- 错题记录本 ----------
    public void addWrongRecord(String badge, String stem, String wrongOpt, String rightOpt, String explain) {
        try {
            JSONArray arr = getWrongArray();
            JSONObject o = new JSONObject();
            Calendar cal = Calendar.getInstance();
            String ts = String.format(Locale.US, "%02d-%02d %02d:%02d",
                    cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH),
                    cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE));
            o.put("t", ts);
            o.put("b", badge == null ? "" : badge);
            o.put("s", stem == null ? "" : stem);
            o.put("w", wrongOpt == null ? "" : wrongOpt);
            o.put("r", rightOpt == null ? "" : rightOpt);
            o.put("e", explain == null ? "" : explain);

            JSONArray next = new JSONArray();
            next.put(o);
            int maxKeep = 49;
            for (int i = 0; i < arr.length() && i < maxKeep; i++) {
                next.put(arr.getJSONObject(i));
            }
            sp.edit().putString("wrongBook", next.toString()).commit();
        } catch (Throwable ignore) {}
    }

    public JSONArray getWrongArray() {
        String raw = sp.getString("wrongBook", "");
        if (raw == null || raw.isEmpty()) return new JSONArray();
        try {
            return new JSONArray(raw);
        } catch (Throwable t) {
            return new JSONArray();
        }
    }

    public int wrongCount() {
        return getWrongArray().length();
    }

    public void clearWrongBook() {
        sp.edit().remove("wrongBook").commit();
    }

    // ---------- 学科与年级多选 ----------
    public int subject() { return clamp(sp.getInt("subject", SUBJECT_MATH), 0, SUBJECT_KEYS.length - 1); }
    public void setSubject(int v) { sp.edit().putInt("subject", clamp(v, 0, SUBJECT_KEYS.length - 1)).commit(); }

    public int subjectMask() {
        if (sp.contains("subjectMask")) {
            int m = sp.getInt("subjectMask", ALL_SUBJECTS_MASK) & ALL_SUBJECTS_MASK;
            return m == 0 ? ALL_SUBJECTS_MASK : m;
        }
        return ALL_SUBJECTS_MASK;
    }

    public void setSubjectMask(int mask) {
        int m = mask & ALL_SUBJECTS_MASK;
        if (m == 0) m = ALL_SUBJECTS_MASK;
        sp.edit().putInt("subjectMask", m).commit();
    }

    public int[] selectedSubjects() {
        int m = subjectMask();
        List<Integer> list = new ArrayList<Integer>();
        for (int i = 0; i < SUBJECT_KEYS.length; i++) {
            if ((m & (1 << i)) != 0) list.add(i);
        }
        if (list.isEmpty()) list.add(SUBJECT_MATH);
        int[] res = new int[list.size()];
        for (int i = 0; i < list.size(); i++) res[i] = list.get(i);
        return res;
    }

    public String subjectsSummary() {
        int m = subjectMask();
        if (m == ALL_SUBJECTS_MASK) return "随机（语/数/英/科）";
        if (m == 0x07) return "随机（语/数/英）";
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (int i = 0; i < SUBJECT_NAMES.length; i++) {
            if ((m & (1 << i)) != 0) {
                if (sb.length() > 0) sb.append("/");
                sb.append(SUBJECT_NAMES[i]);
                count++;
            }
        }
        if (count > 1) return "随机（" + sb.toString() + "）";
        return sb.toString();
    }

    public int gradeMask() {
        if (sp.contains("gradeMask")) {
            int m = sp.getInt("gradeMask", 0) & ALL_GRADES_MASK;
            if (m != 0) return m;
        }
        int g = clamp(sp.getInt("grade", 3), 1, 9);
        return 1 << (g - 1);
    }

    public void setGradeMask(int mask) {
        int m = mask & ALL_GRADES_MASK;
        if (m == 0) m = 1 << (grade() - 1);
        int firstGrade = 1;
        for (int g = 1; g <= 9; g++) {
            if ((m & (1 << (g - 1))) != 0) { firstGrade = g; break; }
        }
        sp.edit()
            .putInt("gradeMask", m)
            .putInt("grade", firstGrade)
            .putInt("stage", firstGrade >= 7 ? STAGE_JUNIOR : STAGE_PRIMARY)
            .commit();
    }

    public int[] selectedGrades() {
        int m = gradeMask();
        List<Integer> list = new ArrayList<Integer>();
        for (int g = 1; g <= 9; g++) {
            if ((m & (1 << (g - 1))) != 0) list.add(g);
        }
        if (list.isEmpty()) list.add(3);
        int[] res = new int[list.size()];
        for (int i = 0; i < list.size(); i++) res[i] = list.get(i);
        return res;
    }

    public String gradesSummary() {
        int m = gradeMask();
        if (m == ALL_GRADES_MASK) return "全年级（1-9年级）";
        if (m == PRIMARY_GRADES_MASK) return "小学全选（1-6年级）";
        if (m == JUNIOR_GRADES_MASK) return "初中全选（7-9年级）";
        int[] gs = selectedGrades();
        if (gs.length == 1) {
            int g = gs[0];
            if (g >= 7) {
                String[] cn = {"初一", "初二", "初三"};
                return "初中 · " + cn[g - 7];
            }
            return "小学 · " + g + " 年级";
        }
        boolean contig = true;
        for (int i = 1; i < gs.length; i++) {
            if (gs[i] != gs[i - 1] + 1) { contig = false; break; }
        }
        if (contig) return gs[0] + "-" + gs[gs.length - 1] + " 年级（共" + gs.length + "个年级）";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < gs.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(gs[i]);
        }
        return sb.toString() + " 年级";
    }

    public boolean parentUnlocked() { return sp.getBoolean("parentUnlocked", false); }
    public void setParentUnlocked(boolean v) { sp.edit().putBoolean("parentUnlocked", v).commit(); }

    public int stage() { return clamp(sp.getInt("stage", STAGE_PRIMARY), 0, 1); }
    public int grade() { return clamp(sp.getInt("grade", 3), 1, 9); }

    public int phase() { return sp.getInt("phase", IDLE); }
    public long remainingMs() { return sp.getLong("remainingMs", durationMin() * 60_000L); }

    public void saveSnapshot(int phase, long remainingMs) {
        sp.edit().putInt("phase", phase).putLong("remainingMs", Math.max(0, remainingMs)).commit();
    }

    public void tickSnapshot(long remainingMs) {
        sp.edit()
            .putLong("remainingMs", Math.max(0, remainingMs))
            .putLong("heartbeatAt", System.currentTimeMillis())
            .apply();
    }

    public long heartbeatAt() { return sp.getLong("heartbeatAt", 0L); }

    public void addGap(long ms) {
        long v = sp.getLong("serviceGapMs", 0L) + ms;
        sp.edit().putLong("serviceGapMs", v).commit();
    }
    public long serviceGapMs() { return sp.getLong("serviceGapMs", 0L); }
    public void markInterrupted(long gapMs) { sp.edit().putLong("interruptedGapMs", Math.max(1, gapMs)).commit(); }

    public void resetSession() {
        saveSnapshot(IDLE, durationMin() * 60_000L);
        setLockReason(LOCK_QUIZ);
        clearStartHint();
    }

    public void markStartHint(long ms) { sp.edit().putLong("startHintUntil", System.currentTimeMillis() + ms).commit(); }
    public long startHintLeftMs() { return Math.max(0L, sp.getLong("startHintUntil", 0L) - System.currentTimeMillis()); }
    public boolean startHintOn() { return phase() == RUNNING && startHintLeftMs() > 0L; }
    public void clearStartHint() { sp.edit().remove("startHintUntil").commit(); }
    public void sweepStartHint() { if (!startHintOn()) clearStartHint(); }

    public boolean anchorInit() { return sp.getBoolean("anchorInit", false); }
    public void markAnchorInit() { if (!anchorInit()) sp.edit().putBoolean("anchorInit", true).commit(); }

    // ---------- 家长口令与恢复码 ----------
    public boolean hasPin() { return sp.getString("pinHash", null) != null; }

    public boolean setPin(String pin) {
        if (pin == null || pin.length() < 4) return false;
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        byte[] hash = pbkdf2(pin, salt);
        if (hash == null) return false;
        sp.edit()
            .putString("pinSalt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString("pinHash", Base64.encodeToString(hash, Base64.NO_WRAP))
            .putInt("failCount", 0)
            .putLong("lockUntil", 0L)
            .commit();
        return true;
    }

    public long verifyPin(String pin) {
        long lockUntil = sp.getLong("lockUntil", 0L);
        long now = System.currentTimeMillis();
        if (lockUntil > now) return lockUntil - now;

        String saltB64 = sp.getString("pinSalt", null);
        String hashB64 = sp.getString("pinHash", null);
        if (saltB64 == null || hashB64 == null) return -1;
        byte[] salt = Base64.decode(saltB64, Base64.NO_WRAP);
        byte[] expect = Base64.decode(hashB64, Base64.NO_WRAP);
        byte[] actual = pbkdf2(pin, salt);
        if (actual != null && MessageDigest.isEqual(expect, actual)) {
            sp.edit().putInt("failCount", 0).putLong("lockUntil", 0L).commit();
            return 0;
        }
        int fail = sp.getInt("failCount", 0) + 1;
        if (fail >= MAX_FAIL) {
            sp.edit().putInt("failCount", 0).putLong("lockUntil", now + LOCKOUT_MS).commit();
            return LOCKOUT_MS;
        }
        sp.edit().putInt("failCount", fail).commit();
        return -1;
    }

    private static byte[] pbkdf2(String pin, byte[] salt) {
        try {
            PBEKeySpec spec = new PBEKeySpec(pin.toCharArray(), salt, PBKDF2_ITER, 256);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (Exception e) {
            return null;
        }
    }

    public boolean hasRecoveryCode() { return sp.getString("rcHash", null) != null; }
    public boolean recoverySaved() { return sp.getBoolean("rcSaved", false); }
    public void markRecoverySaved() { sp.edit().putBoolean("rcSaved", true).commit(); }

    public String newRecoveryCode() {
        String code = Recovery.generate();
        setRecoveryCode(code);
        return code;
    }

    public boolean setRecoveryCode(String code) {
        String c = Recovery.normalize(code);
        if (!Recovery.valid(c)) return false;
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        byte[] hash = pbkdf2(c, salt);
        if (hash == null) return false;
        sp.edit()
            .putString("rcSalt", b64(salt))
            .putString("rcHash", b64(hash))
            .putLong("rcSetAt", System.currentTimeMillis())
            .putBoolean("rcSaved", false)
            .putInt("rcFail", 0)
            .putLong("rcLockUntil", 0L)
            .commit();
        return true;
    }

    public long verifyRecoveryCode(String code) {
        String c = Recovery.normalize(code);
        if (!Recovery.valid(c)) return -2;

        long lockUntil = sp.getLong("rcLockUntil", 0L);
        long now = System.currentTimeMillis();
        if (lockUntil > now) return lockUntil - now;

        String saltB64 = sp.getString("rcSalt", null);
        String hashB64 = sp.getString("rcHash", null);
        if (saltB64 == null || hashB64 == null) return -1;
        byte[] salt = Base64.decode(saltB64, Base64.NO_WRAP);
        byte[] expect = Base64.decode(hashB64, Base64.NO_WRAP);
        byte[] actual = pbkdf2(c, salt);
        if (actual != null && MessageDigest.isEqual(expect, actual)) {
            sp.edit().putInt("rcFail", 0).putLong("rcLockUntil", 0L).commit();
            return 0;
        }
        int fail = sp.getInt("rcFail", 0) + 1;
        if (fail >= MAX_RC_FAIL) {
            sp.edit().putInt("rcFail", 0).putLong("rcLockUntil", now + RC_LOCKOUT_MS).commit();
            return RC_LOCKOUT_MS;
        }
        sp.edit().putInt("rcFail", fail).commit();
        return -1;
    }

    public boolean resetPinAndRecovery(String newPin, String newRecoveryCode) {
        if (newPin == null || newPin.length() < 4) return false;
        String rc = Recovery.normalize(newRecoveryCode);
        if (!Recovery.valid(rc)) return false;

        byte[] pinSalt = new byte[16], rcSalt = new byte[16];
        SecureRandom rnd = new SecureRandom();
        rnd.nextBytes(pinSalt);
        rnd.nextBytes(rcSalt);
        byte[] pinHash = pbkdf2(newPin, pinSalt);
        byte[] rcHash = pbkdf2(rc, rcSalt);
        if (pinHash == null || rcHash == null) return false;

        return sp.edit()
            .putString("pinSalt", b64(pinSalt))
            .putString("pinHash", b64(pinHash))
            .putString("rcSalt", b64(rcSalt))
            .putString("rcHash", b64(rcHash))
            .putLong("rcSetAt", System.currentTimeMillis())
            .putBoolean("rcSaved", false)
            .putInt("failCount", 0)
            .putLong("lockUntil", 0L)
            .putInt("rcFail", 0)
            .putLong("rcLockUntil", 0L)
            .commit();
    }

    private static String b64(byte[] v) { return Base64.encodeToString(v, Base64.NO_WRAP); }

    public static final int MAX_CRASH = 3;

    public void noteCrash(String thread, Throwable e) {
        Throwable root = e;
        while (root != null && root.getCause() != null && root.getCause() != root) root = root.getCause();
        String msg = thread + ": " + (root == null ? "?" : root.getClass().getSimpleName() + ": " + root.getMessage());
        if (msg.length() > 200) msg = msg.substring(0, 200);
        sp.edit()
            .putBoolean("pendingCrash", true)
            .putString("crashInfo", msg)
            .putLong("crashAt", System.currentTimeMillis())
            .commit();
    }

    public boolean consumeCrash() {
        if (!sp.getBoolean("pendingCrash", false)) return false;
        int n = sp.getInt("crashCount", 0) + 1;
        SharedPreferences.Editor ed = sp.edit().putBoolean("pendingCrash", false).putInt("crashCount", n);
        if (n >= MAX_CRASH) ed.putBoolean("safeMode", true).putLong("safeAt", System.currentTimeMillis());
        ed.commit();
        return true;
    }

    public boolean safeMode() { return sp.getBoolean("safeMode", false); }
    public int crashCount() { return sp.getInt("crashCount", 0); }
    public String crashInfo() { return sp.getString("crashInfo", ""); }
    public void markStable() {
        if (sp.getInt("crashCount", 0) == 0 && !sp.getBoolean("pendingCrash", false)) return;
        sp.edit().putInt("crashCount", 0).putBoolean("pendingCrash", false).commit();
    }
    public void clearSafeMode() {
        sp.edit().putBoolean("safeMode", false).putInt("crashCount", 0)
            .putBoolean("pendingCrash", false).putLong("safeAt", 0L).commit();
    }

    public String debugLine() {
        return String.format(Locale.US,
            "phase=%s rem=%s daily=%dm/%dm stars=%d grades=%s subjs=%s",
            phaseName(phase()), Core.fmt(remainingMs()),
            (int) (dailyUsedMs() / 60_000L), dailyLimitMin(), stars(),
            gradesSummary(), subjectsSummary());
    }

    public static String phaseName(int p) {
        if (p == RUNNING) return "计时中";
        if (p == LOCKED) return "已锁屏";
        return "待机";
    }
}
