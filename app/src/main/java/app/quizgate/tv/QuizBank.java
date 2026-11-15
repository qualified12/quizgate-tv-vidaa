/*
 * QuizGate TV · VIDAA Edition
 * Fork of QuizGate TV (https://github.com/mtvtopcom/quizgate-tv), Apache-2.0.
 * Modified from the original QuizGate TV project by qualified12.
 * See NOTICE for the full list of changes.
 */
package app.quizgate.tv;

import android.content.Context;
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
    private static final int RECENT_LIMIT = 48;

    public static final class Q {
        public String stem = "";
        public String[] options = new String[]{"", "", "", ""};
        public int answer = 0;
        public String explain = "";
        public String badge = "";
    }

    private final Context ctx;
    private final Map<String, List<Q>> cache = new HashMap<String, List<Q>>();
    private final LinkedList<String> recentStems = new LinkedList<String>();

    public QuizBank(Context ctx) {
        this.ctx = ctx.getApplicationContext();
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

    /**
     * 从家长勾选的多个年级 + 多个学科（或全科随机）池中随机抽题。
     */
    public Q nextFromPrefs(Prefs prefs, Random r) {
        int[] grades = prefs.selectedGrades();
        int[] subjs = prefs.selectedSubjects();
        int g = grades[r.nextInt(grades.length)];
        int sIdx = subjs[r.nextInt(subjs.length)];
        String sKey = Prefs.SUBJECT_KEYS[sIdx];
        String sName = Prefs.SUBJECT_NAMES[sIdx];

        // 数学学科有 25% 概率直接生成动态随机算术/方程/几何题，保证题源永远刷不完
        if ("math".equals(sKey) && r.nextInt(4) == 0) {
            return Core.arithmetic(g, r);
        }

        // 先从选定年级加载，若为空则在已选年级中轮询
        for (int attempt = 0; attempt < grades.length; attempt++) {
            int curG = grades[(attempt + g) % grades.length];
            List<Q> list = load(sKey, curG);
            if (!list.isEmpty()) {
                Q src = pickNonRepeating(list, r);
                if (src != null) {
                    Q out = new Q();
                    out.stem = src.stem;
                    out.options = src.options.clone();
                    out.answer = shuffle(out.options, src.answer, r);
                    out.explain = src.explain;
                    out.badge = sName + " · " + gradeLabel(curG);
                    return out;
                }
            }
        }
        return next(sKey, g, 1, r);
    }

    public static String gradeLabel(int g) {
        if (g >= 7 && g <= 9) {
            String[] cn = {"初一", "初二", "初三"};
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
            return out;
        }
        return Core.arithmetic(g, r);
    }

    private Q pickNonRepeating(List<Q> list, Random r) {
        if (list.isEmpty()) return null;
        for (int attempt = 0; attempt < 12; attempt++) {
            Q q = list.get(r.nextInt(list.size()));
            if (!recentStems.contains(q.stem)) {
                recordRecent(q.stem);
                return q;
            }
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
