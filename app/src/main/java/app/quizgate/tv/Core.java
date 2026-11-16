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
                int a = 6 + r.nextInt(9);
                int b = 5 + r.nextInt(8);
                ans = a + b;
                int need = 10 - (a % 10);
                stem = "一年级进位加法：" + a + " + " + b + " = ?";
                exp = "【凑十法分步解析】把第二个加数拆开先凑成整十：先算 " + a + " + " + need + " = " + (a + need) + "，再加上剩下的数，得出正确答案 " + ans + "。";
            } else if (mode == 1) {
                int a = 12 + r.nextInt(7);
                int b = 4 + r.nextInt(8);
                ans = a - b;
                stem = "一年级退位减法：" + a + " - " + b + " = ?";
                exp = "【破十法分步解析】把被减数 " + a + " 拆成 10 和 " + (a - 10) + "，先算 10 - " + b + " = " + (10 - b) + "，再算 " + (10 - b) + " + " + (a - 10) + " = " + ans + "。";
            } else {
                int a = 4 + r.nextInt(8);
                int b = 3 + r.nextInt(6);
                int c = 2 + r.nextInt(5);
                ans = a + b - c;
                stem = "一年级加减混合：" + a + " + " + b + " - " + c + " = ?";
                exp = "【从左往右依次计算】第一步先算加法：" + a + " + " + b + " = " + (a + b) + "；第二步再算减法：" + (a + b) + " - " + c + " = " + ans + "。";
            }
        } else if (g == 2) {
            if (mode == 0) {
                int a = 3 + r.nextInt(7);
                int b = 3 + r.nextInt(7);
                ans = a * b;
                stem = "二年级表内乘法：" + a + " × " + b + " = ?";
                exp = "【九九乘法口诀】表示 " + a + " 个 " + b + " 相加，根据乘法口诀直接得出：" + a + " × " + b + " = " + ans + "。";
            } else if (mode == 1) {
                int b = 3 + r.nextInt(7);
                ans = 3 + r.nextInt(7);
                int a = b * ans;
                stem = "二年级表内除法：" + a + " ÷ " + b + " = ?";
                exp = "【想乘算除】思考：" + b + " 乘几等于 " + a + "？因为 " + b + " × " + ans + " = " + a + "，所以 " + a + " ÷ " + b + " = " + ans + "。";
            } else {
                int a = 24 + r.nextInt(45);
                int b = 15 + r.nextInt(25);
                ans = a + b;
                stem = "二年级两位数加法：" + a + " + " + b + " = ?";
                exp = "【竖式相同数位对齐】先算个位相加（满十向十位进1），再算十位相加，得出 " + a + " + " + b + " = " + ans + "。";
            }
        } else if (g == 3) {
            if (mode == 0) {
                int w = 4 + r.nextInt(9);
                int l = w + 2 + r.nextInt(9);
                ans = (l + w) * 2;
                stem = "三年级几何周长：长方形长 " + l + " 厘米、宽 " + w + " 厘米，它的周长是多少厘米？";
                exp = "【长方形周长图解】长方形有2条长和2条宽，周长公式 = (长 + 宽) × 2 = (" + l + " + " + w + ") × 2 = " + (l + w) + " × 2 = " + ans + "（厘米）。";
            } else if (mode == 1) {
                int w = 4 + r.nextInt(8);
                int l = w + 1 + r.nextInt(8);
                ans = l * w;
                stem = "三年级几何面积：长方形长 " + l + " 分米、宽 " + w + " 分米，它的面积是多少平方分米？";
                exp = "【长方形面积图解】长方形面积公式 = 长 × 宽 = " + l + " × " + w + " = " + ans + "（平方分米）。注意区分面积（长×宽）与周长！";
            } else {
                int a = 12 + r.nextInt(18);
                int b = 3 + r.nextInt(6);
                ans = a * b;
                stem = "三年级两位数乘法：" + a + " × " + b + " = ?";
                exp = "【拆分口算法】把 " + a + " 拆成整十数 " + ((a / 10) * 10) + " 和个位数 " + (a % 10) + "，分别乘 " + b + " 再相加：" + ((a / 10) * 10 * b) + " + " + ((a % 10) * b) + " = " + ans + "。";
            }
        } else if (g == 4) {
            if (mode == 0) {
                int a1 = 35 + r.nextInt(45);
                int a2 = 25 + r.nextInt(45);
                ans = 180 - a1 - a2;
                stem = "四年级三角形内角和：已知三角形的两个内角分别是 " + a1 + "° 和 " + a2 + "°，第三个内角是多少度？";
                exp = "【三角形内角和定理】任意三角形三个内角之和恒等于 180°。所以第三个角 = 180° - " + a1 + "° - " + a2 + "° = " + ans + "°。";
            } else if (mode == 1) {
                int k = 12 + r.nextInt(25);
                int p1 = 20 + r.nextInt(60);
                int p2 = 100 - p1;
                ans = k * 100;
                stem = "四年级乘法分配律巧算：" + k + " × " + p1 + " + " + k + " × " + p2 + " = ?";
                exp = "【乘法分配律 a×b + a×c = a×(b+c)】提取公因数 " + k + "：原式 = " + k + " × (" + p1 + " + " + p2 + ") = " + k + " × 100 = " + ans + "。";
            } else {
                int v = 60 + r.nextInt(5) * 10;
                int t = 3 + r.nextInt(5);
                ans = v * t;
                stem = "四年级行程问题：汽车每小时行驶 " + v + " 千米，匀速行驶 " + t + " 小时一共走多少千米？";
                exp = "【速度×时间=路程】直接代入行程公式：路程 = " + v + " × " + t + " = " + ans + "（千米）。";
            }
        } else if (g == 5) {
            if (mode == 0) {
                int k = 2 + r.nextInt(6);
                ans = 3 + r.nextInt(14);
                int c = 4 + r.nextInt(18);
                int rhs = k * ans + c;
                stem = "五年级解简易方程：" + k + "x + " + c + " = " + rhs + "，x = ?";
                exp = "【等式性质解方程】第一步等式两边同时减去 " + c + " 得：" + k + "x = " + rhs + " - " + c + " = " + (rhs - c) + "；第二步两边同时除以 " + k + " 得：x = " + (rhs - c) + " ÷ " + k + " = " + ans + "。";
            } else if (mode == 1) {
                int b = (3 + r.nextInt(8)) * 2;
                int h = 3 + r.nextInt(9);
                ans = (b * h) / 2;
                stem = "五年级多边形面积：三角形的底是 " + b + " 厘米，高是 " + h + " 厘米，它的面积是多少平方厘米？";
                exp = "【三角形面积公式图解】两个完全一样的三角形可以拼成一个平行四边形，因此三角形面积 = 底 × 高 ÷ 2 = " + b + " × " + h + " ÷ 2 = " + ans + "（平方厘米）。";
            } else {
                int top = 3 + r.nextInt(6);
                int bot = top + 2 + r.nextInt(7);
                int h = (2 + r.nextInt(5)) * 2;
                ans = (top + bot) * h / 2;
                stem = "五年级梯形面积：梯形上底 " + top + " 厘米、下底 " + bot + " 厘米、高 " + h + " 厘米，面积是多少平方厘米？";
                exp = "【梯形面积公式图解】梯形面积 = (上底 + 下底) × 高 ÷ 2 = (" + top + " + " + bot + ") × " + h + " ÷ 2 = " + (top + bot) + " × " + h + " ÷ 2 = " + ans + "（平方厘米）。";
            }
        } else if (g == 6) {
            if (mode == 0) {
                int base = (2 + r.nextInt(8)) * 20;
                int pct = (1 + r.nextInt(8)) * 10;
                ans = base * pct / 100;
                stem = "六年级百分数计算：" + base + " 的 " + pct + "% 是多少？";
                exp = "【百分数乘法】求一个数的百分之几是多少用乘法：把 " + pct + "% 化为小数 0." + (pct / 10) + " 或分数 " + pct + "/100，计算 " + base + " × " + pct + " ÷ 100 = " + ans + "。";
            } else if (mode == 1) {
                int cone = 5 + r.nextInt(15);
                int cyl = cone * 3;
                ans = cone;
                stem = "六年级立体几何：等底等高的圆柱和圆锥，若圆柱体积是 " + cyl + " 立方厘米，则圆锥体积是多少立方厘米？";
                exp = "【圆柱与圆锥体积关系】等底等高的圆锥体积等于圆柱体积的 1/3：V_圆锥 = V_圆柱 ÷ 3 = " + cyl + " ÷ 3 = " + ans + "（立方厘米）。";
            } else {
                int r1 = 2 + r.nextInt(5);
                int mult = 3 + r.nextInt(6);
                ans = r1 * mult;
                int b1 = 3 + r.nextInt(5);
                int b2 = b1 * mult;
                stem = "六年级比例求解：若比例 x : " + b2 + " = " + r1 + " : " + b1 + "，则 x = ?";
                exp = "【比例的基本性质】在比例里，两个内项的积等于两个外项的积：" + b1 + "x = " + b2 + " × " + r1 + " = " + (b2 * r1) + "，解得 x = " + ans + "。";
            }
        } else if (g == 7) {
            if (mode == 0) {
                int k = 2 + r.nextInt(6);
                ans = 2 + r.nextInt(12);
                int b = 4 + r.nextInt(16);
                int rhs = k * ans - b;
                stem = "初一一元一次方程：解方程 " + k + "x - " + b + " = " + rhs + "，x = ?";
                exp = "【移项合并步骤】第一步将 -" + b + " 移到等号右边变号为 +" + b + "：" + k + "x = " + rhs + " + " + b + " = " + (rhs + b) + "；第二步两边同除以系数 " + k + "：x = " + ans + "。";
            } else if (mode == 1) {
                int a = 3 + r.nextInt(9);
                ans = a * a;
                stem = "初一有理数乘方：计算 (-" + a + ")² 的结果是多少？";
                exp = "【有理数乘方符号法则】负数的偶次幂为正数！(-" + a + ")² 表示 (-" + a + ") × (-" + a + ")，同号得正，结果为 " + ans + "。";
            } else {
                int deg = 25 + r.nextInt(50);
                ans = 180 - deg;
                stem = "初一几何角度：已知 ∠α = " + deg + "°，则 ∠α 的补角是多少度？";
                exp = "【余角与补角定义】如果两个角的和等于 180°，则这两个角互为补角。因此 ∠α 的补角 = 180° - " + deg + "° = " + ans + "°。";
            }
        } else if (g == 8) {
            if (mode == 0) {
                int[][] triples = {{3, 4, 5}, {6, 8, 10}, {5, 12, 13}, {8, 15, 17}, {9, 12, 15}};
                int[] tr = triples[r.nextInt(triples.length)];
                ans = tr[2];
                stem = "初二勾股定理：直角三角形的两条直角边长分别为 " + tr[0] + " 和 " + tr[1] + "，则斜边长是多少？";
                exp = "【勾股定理 a² + b² = c²】直角三角形两直角边的平方和等于斜边的平方：c = √(" + tr[0] + "² + " + tr[1] + "²) = √(" + (tr[0] * tr[0]) + " + " + (tr[1] * tr[1]) + ") = √" + (ans * ans) + " = " + ans + "。";
            } else if (mode == 1) {
                int k = 2 + r.nextInt(5);
                int x0 = 2 + r.nextInt(6);
                int b = 1 + r.nextInt(9);
                ans = k * x0 + b;
                stem = "初二一次函数：已知一次函数 y = " + k + "x + " + b + "，当 x = " + x0 + " 时，函数值 y = ?";
                exp = "【一次函数代入求值】把自变量 x = " + x0 + " 代入函数关系式 y = " + k + "x + " + b + " 中：y = " + k + " × " + x0 + " + " + b + " = " + (k * x0) + " + " + b + " = " + ans + "。";
            } else {
                int n = 5 + r.nextInt(6);
                ans = (n - 2) * 180;
                stem = "初二多边形内角和：一个凸 " + n + " 边形的内角和是多少度？";
                exp = "【多边形内角和公式】从 n 边形的一个顶点出发可以引 (n-3) 条对角线，把 n 边形分成 (n-2) 个三角形，故内角和 = (n - 2) × 180° = (" + n + " - 2) × 180° = " + ans + "°。";
            }
        } else {
            if (mode == 0) {
                int r1 = 1 + r.nextInt(6);
                int r2 = r1 + 1 + r.nextInt(6);
                ans = r1 + r2;
                int prod = r1 * r2;
                stem = "初三一元二次方程：方程 x² - " + ans + "x + " + prod + " = 0 的两根之和 x₁ + x₂ = ?";
                exp = "【韦达定理（根与系数的关系）】对于一元二次方程 ax² + bx + c = 0，两根之和 x₁ + x₂ = -b/a = -(-" + ans + ")/1 = " + ans + "（两根分别为 " + r1 + " 和 " + r2 + "）。";
            } else if (mode == 1) {
                int h = 2 + r.nextInt(8);
                int k = 3 + r.nextInt(9);
                ans = h + k;
                stem = "初三二次函数性质：抛物线 y = (x - " + h + ")² + " + k + " 的顶点坐标为 (m, n)，则 m + n = ?";
                exp = "【二次函数顶点式 y = a(x - h)² + k】抛物线的对称轴为直线 x = " + h + "，顶点坐标为 (" + h + ", " + k + ")，因此 m + n = " + h + " + " + k + " = " + ans + "。";
            } else {
                int centerAngle = (2 + r.nextInt(7)) * 20;
                ans = centerAngle / 2;
                stem = "初三圆周角定理：在 ⊙O 中，同弧所对的圆心角 ∠AOB = " + centerAngle + "°，则该弧所对的圆周角 ∠ACB 是多少度？";
                exp = "【圆周角定理】同弧或等弧所对的圆周角等于它所对的圆心角的一半：∠ACB = ∠AOB ÷ 2 = " + centerAngle + "° ÷ 2 = " + ans + "°。";
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
        q.badge = "数学 · " + QuizBank.gradeLabel(g);
        return q;
    }
}
