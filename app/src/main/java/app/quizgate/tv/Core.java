/*
 * QuizGate TV · VIDAA Edition
 * Fork of QuizGate TV (https://github.com/mtvtopcom/quizgate-tv), Apache-2.0.
 * Modified from the original QuizGate TV project by qualified12.
 * See NOTICE for the full list of changes.
 */
package app.quizgate.tv;

import java.util.Random;

public final class Core {
    private Core() {}

    public static String fmt(long ms) {
        if (ms <= 0) return "0:00";
        long s = (ms + 999L) / 1000L;
        long m = s / 60L;
        long r = s % 60L;
        return m + ":" + (r < 10 ? "0" + r : String.valueOf(r));
    }

    public static QuizBank.Q arithmetic(int grade, Random r) {
        int g = Math.max(1, Math.min(9, grade));
        int mode = r.nextInt(3);
        int ans;
        String stem;
        String exp;
        if (g == 1) {
            if (mode == 0) {
                int a = 5 + r.nextInt(14);
                int b = 2 + r.nextInt(9);
                ans = a + b;
                stem = a + " + " + b + " = ?";
                exp = a + " + " + b + " = " + ans;
            } else if (mode == 1) {
                int a = 11 + r.nextInt(9);
                int b = 2 + r.nextInt(9);
                ans = a - b;
                stem = a + " - " + b + " = ?";
                exp = a + " - " + b + " = " + ans;
            } else {
                int a = 3 + r.nextInt(8);
                int b = 2 + r.nextInt(6);
                int c = 1 + r.nextInt(5);
                ans = a + b - c;
                stem = a + " + " + b + " - " + c + " = ?";
                exp = "按顺序计算得 " + ans;
            }
        } else if (g == 2) {
            if (mode == 0) {
                int a = 2 + r.nextInt(8);
                int b = 2 + r.nextInt(8);
                ans = a * b;
                stem = a + " × " + b + " = ?";
                exp = "九九乘法口诀：" + a + " × " + b + " = " + ans;
            } else if (mode == 1) {
                int b = 2 + r.nextInt(8);
                ans = 2 + r.nextInt(8);
                int a = b * ans;
                stem = a + " ÷ " + b + " = ?";
                exp = b + " × " + ans + " = " + a;
            } else {
                int a = 25 + r.nextInt(55);
                int b = 12 + r.nextInt(25);
                ans = a + b;
                stem = a + " + " + b + " = ?";
                exp = a + " + " + b + " = " + ans;
            }
        } else if (g <= 4) {
            if (mode == 0) {
                int a = 12 + r.nextInt(28);
                int b = 3 + r.nextInt(8);
                ans = a * b;
                stem = a + " × " + b + " = ?";
                exp = a + " × " + b + " = " + ans;
            } else if (mode == 1) {
                int b = 4 + r.nextInt(8);
                ans = 11 + r.nextInt(19);
                int a = b * ans;
                stem = a + " ÷ " + b + " = ?";
                exp = a + " ÷ " + b + " = " + ans;
            } else {
                int w = 4 + r.nextInt(10);
                int l = w + 2 + r.nextInt(10);
                ans = (l + w) * 2;
                stem = "长方形长 " + l + " 厘米、宽 " + w + " 厘米，周长是多少厘米？";
                exp = "周长 = (" + l + " + " + w + ") × 2 = " + ans;
            }
        } else if (g <= 6) {
            if (mode == 0) {
                int k = 2 + r.nextInt(6);
                ans = 3 + r.nextInt(15);
                int c = 4 + r.nextInt(20);
                int rhs = k * ans + c;
                stem = "解方程：" + k + "x + " + c + " = " + rhs + "，x = ?";
                exp = k + "x = " + (rhs - c) + "，所以 x = " + ans;
            } else if (mode == 1) {
                int base = (2 + r.nextInt(9)) * 20;
                int pct = (1 + r.nextInt(9)) * 10;
                ans = base * pct / 100;
                stem = base + " 的 " + pct + "% 是多少？";
                exp = base + " × " + pct + "% = " + ans;
            } else {
                int b = (2 + r.nextInt(9)) * 2;
                int h = 3 + r.nextInt(10);
                ans = (b * h) / 2;
                stem = "三角形底是 " + b + " 厘米，高是 " + h + " 厘米，面积是多少平方厘米？";
                exp = "三角形面积 = " + b + " × " + h + " ÷ 2 = " + ans;
            }
        } else {
            if (mode == 0) {
                int k = 2 + r.nextInt(7);
                ans = 2 + r.nextInt(14);
                int b = 3 + r.nextInt(18);
                int rhs = k * ans - b;
                stem = "解方程：" + k + "x - " + b + " = " + rhs + "，x = ?";
                exp = k + "x = " + (rhs + b) + "，x = " + ans;
            } else if (mode == 1) {
                int r1 = 1 + r.nextInt(6);
                int r2 = r1 + 1 + r.nextInt(6);
                ans = r1 + r2;
                int prod = r1 * r2;
                stem = "方程 x² - " + ans + "x + " + prod + " = 0 的两根之和 x₁ + x₂ = ?";
                exp = "由韦达定理得两根之和为 " + ans;
            } else {
                int a = 3 + r.nextInt(10);
                ans = a * a;
                stem = "计算：(-" + a + ")² = ?";
                exp = "负数的平方为正数：" + ans;
            }
        }

        String[] opts = new String[4];
        opts[0] = String.valueOf(ans);
        int[] deltas = {1, -1, 2, -2, 3, -3, 5, 10, -5};
        int filled = 1;
        for (int i = 0; i < deltas.length && filled < 4; i++) {
            int cand = ans + deltas[i];
            if (cand < 0 && ans >= 0) continue;
            String s = String.valueOf(cand);
            boolean dup = false;
            for (int j = 0; j < filled; j++) {
                if (opts[j].equals(s)) { dup = true; break; }
            }
            if (!dup) { opts[filled] = s; filled = filled + 1; }
        }
        while (filled < 4) {
            opts[filled] = String.valueOf(ans + filled * 4 + 1);
            filled++;
        }
        QuizBank.Q q = new QuizBank.Q();
        q.stem = stem;
        q.options = opts;
        q.answer = QuizBank.shuffle(q.options, 0, r);
        q.explain = exp;
        q.badge = "数学 · " + g + "年级";
        return q;
    }
}
