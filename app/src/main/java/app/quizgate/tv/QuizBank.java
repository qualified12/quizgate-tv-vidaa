/*
 * QuizGate TV · VIDAA Edition
 * Fork of QuizGate TV (https://github.com/mtvtopcom/quizgate-tv), Apache-2.0.
 * Modified from the original QuizGate TV project by qualified12.
 * See NOTICE for the full list of changes.
 */

package app.quizgate.tv;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;

public final class QuizBank {
    private static final String TAG = "QuizGate";
    private static final int RECENT_LIMIT = 240;

    public static final class Q {
        public String stem = "";
        public String[] options = new String[]{"", "", "", ""};
        public int answer = 0;
        public String explain = "";
        public String badge = "";
        public int grade = 3;
        public String subjectKey = "math";
    }

    private final Context ctx;
    private final SharedPreferences historySp;
    private final Map<String, List<Q>> cache = new HashMap<String, List<Q>>();
    private final LinkedList<String> recentStems = new LinkedList<String>();

    private final List<Integer> gradeBag = new ArrayList<Integer>();
    private int lastGradeMask = -1;
    private int lastPickedGrade = -1;

    private final List<Integer> subjectBag = new ArrayList<Integer>();
    private int lastSubjectMask = -1;
    private int lastPickedSubject = -1;

    public QuizBank(Context ctx) {
        this.ctx = ctx.getApplicationContext();
        this.historySp = this.ctx.getSharedPreferences("quiz_history", Context.MODE_PRIVATE);
        loadRecentFromDisk();
    }

    private void loadRecentFromDisk() {
        try {
            String raw = historySp.getString("recent", "");
            if (raw != null && !raw.isEmpty()) {
                JSONArray arr = new JSONArray(raw);
                for (int i = 0; i < arr.length() && i < RECENT_LIMIT; i++) {
                    recentStems.addLast(arr.getString(i));
                }
            }
        } catch (Throwable ignore) {}
    }

    private void saveRecentToDisk() {
        try {
            JSONArray arr = new JSONArray();
            for (int i = 0; i < recentStems.size(); i++) {
                arr.put(recentStems.get(i));
            }
            historySp.edit().putString("recent", arr.toString()).apply();
        } catch (Throwable ignore) {}
    }

    public static int shuffle(String[] opts, int answerIdx, Random r) {
        int[] idx = {0, 1, 2, 3};
        for (int i = 3; i > 0; i--) {
            int j = r.nextInt(i + 1);
            int t = idx[i]; idx[i] = idx[j]; idx[j] = t;
        }
        String[] copy = opts.clone();
        for (int i = 0; i < 4; i++) opts[i] = copy[idx[i]];
        for (int i = 0; i < 4; i++) if (idx[i] == answerIdx) return i;
        return 0;
    }

    private int nextGradeFromBag(Prefs prefs, Random r) {
        int mask = prefs.gradeMask();
        int[] grades = prefs.selectedGrades();
        if (grades.length == 0) return 3;
        if (grades.length == 1) {
            lastGradeMask = mask;
            lastPickedGrade = grades[0];
            return grades[0];
        }
        if (mask != lastGradeMask || gradeBag.isEmpty()) {
            lastGradeMask = mask;
            gradeBag.clear();
            int[] copy = grades.clone();
            for (int i = copy.length - 1; i > 0; i--) {
                int j = r.nextInt(i + 1);
                int t = copy[i]; copy[i] = copy[j]; copy[j] = t;
            }
            if (copy.length > 1 && copy[0] == lastPickedGrade) {
                int t = copy[0];
                copy[0] = copy[copy.length - 1];
                copy[copy.length - 1] = t;
            }
            for (int i = 0; i < copy.length; i++) {
                gradeBag.add(copy[i]);
            }
        }
        int g = gradeBag.remove(0);
        lastPickedGrade = g;
        return g;
    }

    private int nextSubjectFromBag(Prefs prefs, Random r) {
        int mask = prefs.subjectMask();
        int[] subjs = prefs.selectedSubjects();
        if (subjs.length == 0) return Prefs.SUBJECT_MATH;
        if (subjs.length == 1) {
            lastSubjectMask = mask;
            lastPickedSubject = subjs[0];
            return subjs[0];
        }
        if (mask != lastSubjectMask || subjectBag.isEmpty()) {
            lastSubjectMask = mask;
            subjectBag.clear();
            int[] copy = subjs.clone();
            for (int i = copy.length - 1; i > 0; i--) {
                int j = r.nextInt(i + 1);
                int t = copy[i]; copy[i] = copy[j]; copy[j] = t;
            }
            if (copy.length > 1 && copy[0] == lastPickedSubject) {
                int t = copy[0];
                copy[0] = copy[copy.length - 1];
                copy[copy.length - 1] = t;
            }
            for (int i = 0; i < copy.length; i++) {
                subjectBag.add(copy[i]);
            }
        }
        int s = subjectBag.remove(0);
        lastPickedSubject = s;
        return s;
    }

    public Q nextFromPrefs(Prefs prefs, Random r) {
        int g = nextGradeFromBag(prefs, r);
        int sIdx = nextSubjectFromBag(prefs, r);
        String sKey = Prefs.SUBJECT_KEYS[sIdx];
        String sName = Prefs.SUBJECT_NAMES[sIdx];

        if ("math".equals(sKey) && r.nextInt(4) == 0) {
            Q mq = Core.arithmetic(g, r);
            mq.grade = g;
            mq.subjectKey = sKey;
            return mq;
        }

        List<Q> list = load(sKey, g);
        if (!list.isEmpty()) {
            Q src = pickNonRepeating(list, r);
            if (src != null) {
                Q out = new Q();
                out.stem = src.stem;
                out.options = src.options.clone();
                out.answer = shuffle(out.options, src.answer, r);
                out.explain = src.explain;
                out.badge = sName + " · " + gradeLabel(g);
                out.grade = g;
                out.subjectKey = sKey;
                return out;
            }
        }
        return next(sKey, g, g, r);
    }

    public static String gradeLabel(int g) {
        if (g >= 7 && g <= 9) {
            String[] cn = {"初一(7年级)", "初二(8年级)", "初三(9年级)"};
            return cn[g - 7];
        }
        return g + "年级";
    }

    public Q next(String subjectKey, int grade, int floor, Random r) {
        int g = Math.max(1, Math.min(9, grade));
        int lo = Math.max(1, Math.min(g, floor));
        String sName = "数学";
        for (int i = 0; i < Prefs.SUBJECT_KEYS.length; i++) {
            if (Prefs.SUBJECT_KEYS[i].equals(subjectKey)) {
                sName = Prefs.SUBJECT_NAMES[i];
                break;
            }
        }
        for (int cur = g; cur >= lo; cur--) {
            List<Q> list = load(subjectKey, cur);
            if (list.isEmpty()) continue;
            Q src = pickNonRepeating(list, r);
            if (src == null) continue;
            Q out = new Q();
            out.stem = src.stem;
            out.options = src.options.clone();
            out.answer = shuffle(out.options, src.answer, r);
            out.explain = src.explain;
            out.badge = sName + " · " + gradeLabel(cur);
            out.grade = cur;
            out.subjectKey = subjectKey;
            return out;
        }
        Q mq = Core.arithmetic(g, r);
        mq.grade = g;
        mq.subjectKey = subjectKey;
        return mq;
    }

    private Q pickNonRepeating(List<Q> list, Random r) {
        if (list.isEmpty()) return null;
        // 优先随机尝试 24 次避开最近 240 道题，若全撞则全盘扫描找未出现过的题
        for (int attempt = 0; attempt < 24; attempt++) {
            Q q = list.get(r.nextInt(list.size()));
            if (!recentStems.contains(q.stem)) {
                recordRecent(q.stem);
                return q;
            }
        }
        List<Q> unseen = new ArrayList<Q>();
        for (int i = 0; i < list.size(); i++) {
            Q cand = list.get(i);
            if (!recentStems.contains(cand.stem)) {
                unseen.add(cand);
            }
        }
        if (!unseen.isEmpty()) {
            Q q = unseen.get(r.nextInt(unseen.size()));
            recordRecent(q.stem);
            return q;
        }
        Q q = list.get(r.nextInt(list.size()));
        recordRecent(q.stem);
        return q;
    }

    private void recordRecent(String stem) {
        recentStems.addLast(stem);
        while (recentStems.size() > RECENT_LIMIT) {
            recentStems.removeFirst();
        }
        saveRecentToDisk();
    }

    private List<Q> load(String subjectKey, int grade) {
        String key = subjectKey + "_" + grade;
        List<Q> cached = cache.get(key);
        if (cached != null) return cached;
        List<Q> list = new ArrayList<Q>();
        String path = "quiz/" + subjectKey + "_grade_" + grade + ".json";
        try {
            AssetManager am = ctx.getAssets();
            InputStream in = am.open(path);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            in.close();
            JSONObject root = new JSONObject(new String(bos.toByteArray(), "UTF-8"));
            JSONArray items = root.optJSONArray("items");
            if (items != null) {
                for (int i = 0; i < items.length(); i++) {
                    JSONObject o = items.getJSONObject(i);
                    JSONArray opts = o.getJSONArray("options");
                    if (opts.length() != 4) continue;
                    Q q = new Q();
                    q.stem = o.getString("stem");
                    q.options = new String[]{
                        opts.getString(0), opts.getString(1),
                        opts.getString(2), opts.getString(3)
                    };
                    q.answer = o.getInt("answerIndex");
                    if (q.answer < 0 || q.answer > 3) continue;
                    q.explain = o.optString("explanation", "");
                    q.grade = grade;
                    q.subjectKey = subjectKey;
                    list.add(q);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "quiz asset missing/broken: " + path + " (" + e.getClass().getSimpleName() + ")");
        }
        cache.put(key, list);
        return list;
    }
}
