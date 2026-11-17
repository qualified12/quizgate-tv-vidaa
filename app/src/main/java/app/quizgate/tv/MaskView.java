/*
 * QuizGate TV · VIDAA Edition
 * Fork of QuizGate TV (https://github.com/mtvtopcom/quizgate-tv), Apache-2.0.
 * Modified from the original QuizGate TV project by qualified12.
 * See NOTICE for the full list of changes.
 */

package app.quizgate.tv;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Random;

public class MaskView extends FrameLayout {

    public interface Host {
        void onSolved();
        void onEyeCareDone();
        void onPinOk();
    }

    private final Prefs prefs;
    private final Host host;
    private final QuizBank bank;
    private final Random rnd = new Random();
    private final int lockMode;
    private final int extendMin;
    private final int penaltyPerWrong;
    private final int guessTrigger;
    private final int guessCooldownSec;
    private final int minReadSec;

    private final TextView kickerView;
    private final TextView starView;
    private final TextView stemView;
    private final TextView feedback;
    private final TextView hintView;
    private final TextView[] opts = new TextView[4];
    private final LinearLayout optsGrid;
    private final LinearLayout box;

    private QuizBank.Q q;
    private PinPadView pin;
    private RecoveryFlow recovery;
    private FrameLayout explainOverlay;
    private ScrollView explainScroll;
    private TextView explainBtn;
    private TextView explainTipView;
    private boolean explainVisible = false;
    private long explainUnlockAt = 0L;

    private boolean pinVisible = false;
    private boolean answering = false;
    private int sel = 0;
    private int correctCount = 0;
    private int targetCount = 1;
    private int wrongStreak = 0;
    private long questionShownAt = 0L;
    private long cooldownUntil = 0L;
    private long eyeEndAt = 0L;
    private volatile long lastKeyAt = 0L;

    private final Runnable guardTick = new Runnable() {
        @Override public void run() {
            if (getParent() == null) return;
            long now = SystemClock.uptimeMillis();
            if (lockMode == Prefs.LOCK_EYECARE) {
                long leftMs = eyeEndAt - now;
                if (leftMs <= 0L) {
                    host.onEyeCareDone();
                    return;
                }
                int sec = (int) ((leftMs + 999L) / 1000L);
                stemView.setText("护眼远眺休息中：" + sec + " 秒");
                feedback.setTextColor(Ui.OK);
                feedback.setText("请让孩子离开屏幕远眺窗外，倒计时结束后自动恢复播放");
                postDelayed(this, 500L);
                return;
            }
            if (explainVisible && explainBtn != null) {
                long waitMs = explainUnlockAt - now;
                if (waitMs > 0L) {
                    int sec = (int) ((waitMs + 999L) / 1000L);
                    boolean isGuessCooldown = (cooldownUntil > now && cooldownUntil >= explainUnlockAt);
                    explainBtn.setText(isGuessCooldown
                            ? ("⏳ 连续答错防瞎蒙冷却中，请认真研读上方图文解析（剩余 " + sec + " 秒）")
                            : ("⏳ 请先认真看懂上方图文解析（还需阅读 " + sec + " 秒后可继续…）"));
                    explainBtn.setTextColor(Ui.ACCENT);
                    explainBtn.setBackground(explainBtnWaitBg());
                    postDelayed(this, 300L);
                } else {
                    explainBtn.setText("✓ 我已看懂本题解析，按 OK 键进入下一题 ›");
                    explainBtn.setTextColor(0xFFFFFFFF);
                    explainBtn.setBackground(explainBtnReadyBg());
                    if (explainTipView != null) {
                        explainTipView.setText("已读完解析！请按遥控器「OK / 确认键」进入下一题");
                    }
                }
                return;
            }
            if (lockMode == Prefs.LOCK_QUIZ && !answering && !pinVisible && recovery == null) {
                if (cooldownUntil > now) {
                    int sec = (int) ((cooldownUntil - now + 999L) / 1000L);
                    feedback.setTextColor(Ui.BAD);
                    feedback.setText("⚠ 连续答错！禁止瞎蒙，请认真读题，冷却剩余 " + sec + " 秒");
                    postDelayed(this, 500L);
                    return;
                } else if (cooldownUntil > 0L) {
                    cooldownUntil = 0L;
                    questionShownAt = now;
                    feedback.setTextColor(Ui.ACCENT);
                    feedback.setText("冷却结束，请仔细思考后作答");
                    paintSelection();
                }
                if (minReadSec > 0) {
                    long readLeft = (questionShownAt + minReadSec * 1000L) - now;
                    if (readLeft > 0L) {
                        int s = (int) ((readLeft + 999L) / 1000L);
                        feedback.setTextColor(Ui.DIM);
                        feedback.setText("防瞎蒙读题保护中（" + s + " 秒后可提交答案）");
                        postDelayed(this, 400L);
                    } else if (feedback.getText().toString().startsWith("防瞎蒙读题保护中")) {
                        feedback.setText("");
                    }
                }
            }
        }
    };

    public MaskView(Context c, Prefs prefs, QuizBank bank, int lockMode, Host host) {
        super(c);
        this.prefs = prefs;
        this.bank = bank;
        this.lockMode = lockMode;
        this.host = host;
        this.extendMin = prefs.extendMin();
        int savedTarget = prefs.activeQuizTarget();
        this.targetCount = (lockMode == Prefs.LOCK_QUIZ && savedTarget > 0)
                ? Math.max(prefs.quizCount(), savedTarget)
                : prefs.quizCount();
        if (lockMode == Prefs.LOCK_QUIZ) {
            if (savedTarget <= 0) prefs.setActiveQuizTarget(this.targetCount);
            this.correctCount = Math.max(0, Math.min(this.targetCount - 1, prefs.activeQuizCorrect()));
            this.wrongStreak = prefs.activeQuizWrongStreak();
            long cdWall = prefs.activeQuizCooldownUntilWall();
            long nowWall = System.currentTimeMillis();
            if (cdWall > nowWall && cdWall - nowWall <= 120_000L) {
                this.cooldownUntil = SystemClock.uptimeMillis() + (cdWall - nowWall);
            }
        }
        this.penaltyPerWrong = prefs.wrongPenalty();
        this.guessTrigger = prefs.guessTrigger();
        this.guessCooldownSec = prefs.guessCooldownSec();
        this.minReadSec = prefs.minReadSec();
        this.lastKeyAt = SystemClock.uptimeMillis();

        setBackgroundColor(lockMode == Prefs.LOCK_EYECARE ? 0xFF0A1914 : Ui.BG);
        setFocusable(true);
        setFocusableInTouchMode(true);

        box = Ui.col(c);
        box.setPadding(Ui.dp(c, 72), Ui.dp(c, 28), Ui.dp(c, 72), Ui.dp(c, 28));

        LinearLayout topRow = Ui.row(c);
        topRow.setGravity(Gravity.CENTER_VERTICAL);
        kickerView = Ui.label(c, "", 20f, Ui.ACCENT);
        topRow.addView(kickerView, new LinearLayout.LayoutParams(0, -2, 1f));
        starView = Ui.label(c, "★ 积分：" + prefs.stars(), 20f, Ui.ACCENT);
        starView.setTypeface(Typeface.DEFAULT_BOLD);
        topRow.addView(starView);
        box.addView(topRow);

        stemView = Ui.title(c, "");
        stemView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, -2);
        slp.topMargin = Ui.dp(c, 20);
        box.addView(stemView, slp);

        optsGrid = Ui.col(c);
        for (int row = 0; row < 2; row++) {
            LinearLayout r = Ui.row(c);
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, Ui.dp(c, 88));
            rlp.topMargin = Ui.dp(c, row == 0 ? 20 : 12);
            optsGrid.addView(r, rlp);
            for (int col = 0; col < 2; col++) {
                final int i = row * 2 + col;
                TextView b = Ui.label(c, "", 22f, Ui.TEXT);
                b.setGravity(Gravity.CENTER);
                b.setBackground(Ui.optBg(c));
                b.setFocusable(false);
                b.setClickable(true);
                b.setPadding(Ui.dp(c, 16), Ui.dp(c, 8), Ui.dp(c, 16), Ui.dp(c, 8));
                LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(0, -1, 1f);
                blp.leftMargin = Ui.dp(c, col == 0 ? 0 : 10);
                blp.rightMargin = Ui.dp(c, col == 0 ? 10 : 0);
                r.addView(b, blp);
                b.setOnClickListener(new OnClickListener() {
                    @Override public void onClick(View v) {
                        lastKeyAt = SystemClock.uptimeMillis();
                        answer(i);
                    }
                });
                opts[i] = b;
            }
        }
        box.addView(optsGrid);

        feedback = Ui.label(c, "", 20f, Ui.DIM);
        feedback.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(-1, -2);
        flp.topMargin = Ui.dp(c, 16);
        box.addView(feedback, flp);

        hintView = Ui.label(c, "按 1–4 或方向键选择，OK 确认  ·  菜单键 = 家长口令解锁", 16f, Ui.DIM);
        hintView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(-1, -2);
        hlp.topMargin = Ui.dp(c, 14);
        box.addView(hintView, hlp);

        LinearLayout brand = Ui.row(c);
        brand.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        ImageView logo = new ImageView(c);
        logo.setBackground(Ui.logoBg(c));
        brand.addView(logo, new LinearLayout.LayoutParams(Ui.dp(c, 28), Ui.dp(c, 28)));
        TextView name = Ui.label(c, "答题续时 · VIDAA 增强版", 17f, Ui.DIM);
        name.setPadding(Ui.dp(c, 10), 0, 0, 0);
        brand.addView(name);
        LinearLayout.LayoutParams brlp = new LinearLayout.LayoutParams(-1, -2);
        brlp.topMargin = Ui.dp(c, 10);
        box.addView(brand, brlp);

        addView(box, new FrameLayout.LayoutParams(-1, -1));
        setupMode();
    }

    private void setupMode() {
        if (lockMode == Prefs.LOCK_CURFEW) {
            optsGrid.setVisibility(GONE);
            kickerView.setText("☾ 夜间宵禁保护已开启（" + prefs.curfewName() + "） · 电视已静音");
            stemView.setText("现在是夜间休息时间，请关机早点休息，明天再看电视");
            feedback.setTextColor(Ui.ACCENT);
            feedback.setText("如需临时解锁，请家长按遥控器「菜单键」输入 6 位口令");
            hintView.setText("菜单键 = 输入家长口令临时解锁");
        } else if (lockMode == Prefs.LOCK_DAILY) {
            optsGrid.setVisibility(GONE);
            int lim = prefs.dailyLimitMin() + prefs.dailyBonusMin();
            kickerView.setText("⏰ 今日观看总时长已达上限（" + lim + " 分钟） · 电视已静音");
            stemView.setText("今天看电视时间已经够久啦，保护眼睛，明天再来看吧！");
            feedback.setTextColor(Ui.ACCENT);
            feedback.setText("今日已看 " + (prefs.dailyUsedMs() / 60_000L) + " 分钟 · 家长按「菜单键」可输入口令解锁或加时");
            hintView.setText("菜单键 = 输入家长口令解除今日上限");
        } else if (lockMode == Prefs.LOCK_EYECARE) {
            optsGrid.setVisibility(GONE);
            kickerView.setText("☀ 护眼强制休息 · 电视已自动静音暂停");
            eyeEndAt = SystemClock.uptimeMillis() + prefs.eyeRestSec() * 1000L;
            hintView.setText("休息结束后自动恢复电视播放 · 菜单键 = 家长口令跳过");
            removeCallbacks(guardTick);
            post(guardTick);
        } else {
            optsGrid.setVisibility(VISIBLE);
            nextQuestion();
        }
    }

    public long lastKeyAt() { return lastKeyAt; }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        requestFocus();
        post(new Runnable() {
            @Override public void run() { paintSelection(); }
        });
    }

    public void nextQuestion() {
        hideExplainOverlay();
        q = bank.nextFromPrefs(prefs, rnd);
        answering = false;
        questionShownAt = SystemClock.uptimeMillis();
        String badge = (q.badge == null || q.badge.isEmpty()) ? "" : ("【" + q.badge + "】");
        String scope = prefs.gradesSummary();
        String prog = "已答对 " + correctCount + " / " + targetCount + " 题";
        kickerView.setText(prog + " · 本题：" + badge + "（抽题范围：" + scope + "）");
        starView.setText("★ 积分：" + prefs.stars());
        stemView.setText(q.stem);
        String[] prefixes = {"① ", "② ", "③ ", "④ "};
        for (int i = 0; i < 4; i++) {
            opts[i].setText(prefixes[i] + q.options[i]);
        }
        sel = 0;
        paintSelection();
        removeCallbacks(guardTick);
        if (minReadSec > 0 || cooldownUntil > SystemClock.uptimeMillis()) {
            post(guardTick);
        } else {
            feedback.setText("");
        }
    }

    private void paintSelection() {
        if (lockMode != Prefs.LOCK_QUIZ) return;
        boolean lockedOut = cooldownUntil > SystemClock.uptimeMillis();
        for (int i = 0; i < 4; i++) {
            TextView t = opts[i];
            boolean on = (i == sel) && !answering && !lockedOut;
            t.setBackground(on ? selectedBg() : normalBg());
            t.setTextColor(lockedOut ? Ui.DIM : (on ? Ui.ACCENT : Ui.TEXT));
        }
    }

    private GradientDrawable normalBg() {
        GradientDrawable d = new GradientDrawable();
        d.setColor(0x1AFFFFFF);
        d.setStroke(Ui.dp(getContext(), 1), 0x33FFFFFF);
        d.setCornerRadius(Ui.dp(getContext(), 14));
        return d;
    }

    private GradientDrawable selectedBg() {
        GradientDrawable d = new GradientDrawable();
        d.setColor(0x33FFC24A);
        d.setStroke(Ui.dp(getContext(), 3), Ui.ACCENT);
        d.setCornerRadius(Ui.dp(getContext(), 14));
        return d;
    }

    private GradientDrawable cardBoxBg(int fill, int strokeColor, int strokeDp, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setStroke(Ui.dp(getContext(), strokeDp), strokeColor);
        d.setCornerRadius(Ui.dp(getContext(), radiusDp));
        return d;
    }

    private GradientDrawable explainBtnWaitBg() {
        return cardBoxBg(0xFF282218, Ui.ACCENT, 2, 14);
    }

    private GradientDrawable explainBtnReadyBg() {
        return cardBoxBg(0xFF1F6F45, Ui.ACCENT, 3, 14);
    }

    private void select(int i) {
        sel = Math.max(0, Math.min(3, i));
        paintSelection();
    }

    private void moveSel(int dx, int dy) {
        if (answering || explainVisible || lockMode != Prefs.LOCK_QUIZ) return;
        int col = sel % 2, row = sel / 2;
        col = (col + dx + 2) % 2;
        row = (row + dy + 2) % 2;
        select(row * 2 + col);
    }

    private void answer(int i) {
        if (lockMode != Prefs.LOCK_QUIZ || answering || explainVisible || pinVisible || q == null) return;
        long now = SystemClock.uptimeMillis();
        if (cooldownUntil > now) {
            int sec = (int) ((cooldownUntil - now + 999L) / 1000L);
            feedback.setTextColor(Ui.BAD);
            feedback.setText("⚠ 冷却中，请等待 " + sec + " 秒后认真作答");
            return;
        }
        if (minReadSec > 0 && now < questionShownAt + minReadSec * 1000L) {
            int sec = (int) ((questionShownAt + minReadSec * 1000L - now + 999L) / 1000L);
            feedback.setTextColor(Ui.ACCENT);
            feedback.setText("请先读题（" + sec + " 秒后再选答案，禁止盲按）");
            return;
        }

        answering = true;
        boolean ok = (i == q.answer);
        if (ok) {
            wrongStreak = 0;
            correctCount = correctCount + 1;
            prefs.setActiveQuizWrongStreak(0);
            prefs.setActiveQuizCooldownUntilWall(0L);
            prefs.setActiveQuizCorrect(correctCount);
            prefs.addStar(1);
            starView.setText("★ 积分：" + prefs.stars());
            opts[i].setBackgroundColor(0xFF1F6F45);
            feedback.setTextColor(Ui.OK);
            if (correctCount >= targetCount) {
                int showMin = extendMin > 0 ? extendMin : (extendMin == 0 ? prefs.durationMin() : 0);
                String doneMsg = showMin <= 0
                        ? ("✓ 全部通关（" + correctCount + "/" + targetCount + "）！积分+1★，正在恢复播放…")
                        : ("✓ 全部通关（" + correctCount + "/" + targetCount + "）！可观看 " + showMin + " 分钟，正在续播…");
                feedback.setText(doneMsg);
                postDelayed(new Runnable() {
                    @Override public void run() { host.onSolved(); }
                }, 700);
            } else {
                feedback.setText("✓ 答对了！积分+1★，还需答对 " + (targetCount - correctCount) + " 题…");
                postDelayed(new Runnable() {
                    @Override public void run() { nextQuestion(); }
                }, 750);
            }
        } else {
            wrongStreak = wrongStreak + 1;
            prefs.addWrongRecord(q.badge, q.stem, q.options[i], q.options[q.answer], q.explain);
            if (penaltyPerWrong > 0) {
                targetCount = Math.min(30, targetCount + penaltyPerWrong);
                prefs.setActiveQuizTarget(targetCount);
            }
            if (guessTrigger > 0 && wrongStreak >= guessTrigger) {
                cooldownUntil = SystemClock.uptimeMillis() + guessCooldownSec * 1000L;
                prefs.setActiveQuizCooldownUntilWall(System.currentTimeMillis() + guessCooldownSec * 1000L);
                wrongStreak = 0;
            }
            prefs.setActiveQuizWrongStreak(wrongStreak);
            opts[i].setBackgroundColor(0x8C6B1D1D);
            opts[q.answer].setBackgroundColor(0xFF1F6F45);
            // 弹出「错题图文精讲与确认看懂面板」，必须孩子看懂确认后才进入下一题！
            showExplainOverlay(i);
        }
    }

    // ---------- 错题图文精讲与确认看懂弹窗 ----------

    private LinearLayout buildVisualDiagramCard(Context c, QuizBank.Q item, int wrongIdx) {
        LinearLayout card = Ui.col(c);
        card.setBackground(cardBoxBg(0xFF141A24, 0x44FFC24A, 1, 14));
        card.setPadding(Ui.dp(c, 18), Ui.dp(c, 14), Ui.dp(c, 18), Ui.dp(c, 14));

        String sKey = item.subjectKey == null ? "math" : item.subjectKey;
        String diagTitle;
        String step1Title, step1Body;
        String step2Title, step2Body;
        String diagramArt;

        if ("math".equals(sKey)) {
            if (item.stem.contains("长方形") || item.stem.contains("正方形") || item.stem.contains("周长") || item.stem.contains("面积")
                    || item.stem.contains("三角形") || item.stem.contains("梯形") || item.stem.contains("圆") || item.stem.contains("棱长")) {
                diagTitle = "★ 数学几何图形与公式拆解图谱";
                if (item.stem.contains("三角形")) {
                    diagramArt = "      /\\  ← 顶点\n     / | \\  高 h\n    /  |  \\\n   /___|___\\ 底 b\n  [S = 底×高÷2 | 内角和=180°]";
                } else if (item.stem.contains("圆")) {
                    diagramArt = "     ╭───╮\n   ╭─╯ ● ╰─╮  r (半径)\n   ╰─╮   ╭─╯\n     ╰───╯\n  [C = 2πr | S = πr²]";
                } else {
                    diagramArt = "  ┌───────────┐\n  │  图形区域 │ 宽/边长\n  └───────────┘\n      长/底边\n  [周长=(长+宽)×2 | 面积=长×宽]";
                }
                step1Title = "① 提取图形条件";
                step1Body = "从题干提取已知边长、半径、角度或高";
                step2Title = "② 代入几何公式";
                step2Body = "严格区分周长（一圈长度）与面积（平铺大小）";
            } else {
                diagTitle = "★ 数学算理与分步推导天平图";
                diagramArt = " ┌──────────┐     ┌──────────┐\n │ 已知条件 │ ══▶ │ 运算规律 │\n └──────────┘     └────┬─────┘\n                       ▼\n              ┏━━━━━━━━━━━━━━━━┓\n              ┃ 唯一正解：" + item.options[item.answer] + " ┃\n              ┗━━━━━━━━━━━━━━━━┛";
                step1Title = "① 审清运算顺序";
                step1Body = "先看括号与乘方，再算乘除，最后算加减/移项";
                step2Title = "② 逆向验算核对";
                step2Body = "把结果代回原式检验左右两边是否相等";
            }
        } else if ("chinese".equals(sKey)) {
            diagTitle = "★ 语文诗文典故与字词结构图谱";
            diagramArt = " ╔══════════════════════╗\n ║ 【篇目/出处】锁定语境 ║\n ║          ⇓           ║\n ║ 【关键字义】古今辨析 ║\n ║          ⇓           ║\n ║ 【千古名句】精准对应 ║\n ╚══════════════════════╝";
            step1Title = "① 定位诗文出处";
            step1Body = "结合朝代、作者、题目与前后句意境联想";
            step2Title = "② 辨析关键字义";
            step2Body = "注意古今异义词、通假字与托物言志主旨";
        } else if ("english".equals(sKey)) {
            diagTitle = "★ 英语语法标志词与句型结构图";
            diagramArt = " [Signal 信号词/语境]\n         │\n         ▼\n [Grammar 语法/词性规则]\n         │\n         ▼\n [Target 正确搭配: " + item.options[item.answer] + "]";
            step1Title = "① 圈出时间/语境词";
            step1Body = "找准主语单复数（三单）、时间状语或介词搭配";
            step2Title = "② 匹配句法结构";
            step2Body = "核对时态变化、冠词 a/an/the 及交际应答习惯";
        } else {
            diagTitle = "★ 科学实验现象与原理机制图";
            diagramArt = " ╭──────────────╮\n │ 自然/实验现象 │\n ╰──────┬───────╯\n        ▼ 物理/生物/化学规律\n ╭──────────────╮\n │ 科学必然结论 │\n ╰──────────────╯";
            step1Title = "① 观察实验现象";
            step1Body = "明确实验中的控制变量与客观自然条件";
            step2Title = "② 对应科学定律";
            step2Body = "从声、光、热、力、电或生物结构原理推导结论";
        }

        TextView head = Ui.label(c, diagTitle, 18f, Ui.ACCENT);
        head.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(head);

        TextView artView = Ui.label(c, diagramArt, 15f, 0xFF7CE38B);
        artView.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        artView.setBackground(cardBoxBg(0xFF0C1017, 0x337CE38B, 1, 10));
        artView.setPadding(Ui.dp(c, 14), Ui.dp(c, 10), Ui.dp(c, 14), Ui.dp(c, 10));
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(-1, -2);
        alp.topMargin = Ui.dp(c, 10);
        card.addView(artView, alp);

        LinearLayout s1 = Ui.col(c);
        s1.setBackground(cardBoxBg(0xFF1C2536, 0x4458A6FF, 1, 10));
        s1.setPadding(Ui.dp(c, 12), Ui.dp(c, 8), Ui.dp(c, 12), Ui.dp(c, 8));
        s1.addView(Ui.label(c, step1Title, 15f, 0xFF58A6FF));
        s1.addView(Ui.label(c, step1Body, 14f, Ui.TEXT));
        LinearLayout.LayoutParams s1lp = new LinearLayout.LayoutParams(-1, -2);
        s1lp.topMargin = Ui.dp(c, 10);
        card.addView(s1, s1lp);

        LinearLayout s2 = Ui.col(c);
        s2.setBackground(cardBoxBg(0xFF1B2A22, 0x443FB950, 1, 10));
        s2.setPadding(Ui.dp(c, 12), Ui.dp(c, 8), Ui.dp(c, 12), Ui.dp(c, 8));
        s2.addView(Ui.label(c, step2Title, 15f, Ui.OK));
        s2.addView(Ui.label(c, step2Body, 14f, Ui.TEXT));
        LinearLayout.LayoutParams s2lp = new LinearLayout.LayoutParams(-1, -2);
        s2lp.topMargin = Ui.dp(c, 8);
        card.addView(s2, s2lp);

        return card;
    }

    private void showExplainOverlay(int wrongIdx) {
        hideExplainOverlay();
        Context c = getContext();
        explainVisible = true;
        long now = SystemClock.uptimeMillis();
        // 至少阅读 4 秒解析（若触发了防瞎蒙冷却则等待冷却结束）才能按 OK 进入下一题
        long minExplainWait = now + 4000L;
        explainUnlockAt = Math.max(minExplainWait, cooldownUntil);

        explainOverlay = new FrameLayout(c);
        explainOverlay.setBackgroundColor(0xF20B0E14);
        explainOverlay.setClickable(true);
        explainOverlay.setFocusable(true);

        LinearLayout panel = Ui.col(c);
        panel.setBackground(Ui.panelBg(c));
        panel.setPadding(Ui.dp(c, 42), Ui.dp(c, 26), Ui.dp(c, 42), Ui.dp(c, 26));

        // 1. 顶部标题栏
        LinearLayout topBar = Ui.row(c);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        String badgeStr = (q.badge == null || q.badge.isEmpty()) ? "错题精讲" : q.badge;
        TextView titleTv = Ui.label(c, "✗ 本题答错了 · 错题图文精讲（" + badgeStr + "）", 24f, Ui.BAD);
        titleTv.setTypeface(Typeface.DEFAULT_BOLD);
        topBar.addView(titleTv, new LinearLayout.LayoutParams(0, -2, 1f));

        String penStr = penaltyPerWrong > 0
                ? ("答错加罚 +" + penaltyPerWrong + " 题 · 进度：" + correctCount + " / " + targetCount + " 题")
                : ("当前进度：" + correctCount + " / " + targetCount + " 题");
        TextView penTv = Ui.label(c, penStr, 17f, Ui.ACCENT);
        topBar.addView(penTv);
        panel.addView(topBar);

        // 2. 原题干展示
        TextView stemBox = Ui.label(c, "【原题】 " + q.stem, 21f, Ui.TEXT);
        stemBox.setTypeface(Typeface.DEFAULT_BOLD);
        stemBox.setBackground(cardBoxBg(0xFF161B22, 0x33FFFFFF, 1, 12));
        stemBox.setPadding(Ui.dp(c, 18), Ui.dp(c, 12), Ui.dp(c, 18), Ui.dp(c, 12));
        LinearLayout.LayoutParams stLp = new LinearLayout.LayoutParams(-1, -2);
        stLp.topMargin = Ui.dp(c, 14);
        panel.addView(stemBox, stLp);

        // 3. 你的错选 vs 正确答案 并排对比框
        LinearLayout cmpRow = Ui.row(c);
        LinearLayout.LayoutParams cmpLp = new LinearLayout.LayoutParams(-1, -2);
        cmpLp.topMargin = Ui.dp(c, 12);

        TextView wrongBox = Ui.label(c, "✗ 你的错选：" + q.options[wrongIdx], 19f, 0xFFFF8B8B);
        wrongBox.setTypeface(Typeface.DEFAULT_BOLD);
        wrongBox.setBackground(cardBoxBg(0x446B1D1D, Ui.BAD, 2, 12));
        wrongBox.setPadding(Ui.dp(c, 16), Ui.dp(c, 10), Ui.dp(c, 16), Ui.dp(c, 10));
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(0, -2, 1f);
        wlp.rightMargin = Ui.dp(c, 8);
        cmpRow.addView(wrongBox, wlp);

        TextView rightBox = Ui.label(c, "✓ 正确答案：" + q.options[q.answer], 19f, 0xFF7CE38B);
        rightBox.setTypeface(Typeface.DEFAULT_BOLD);
        rightBox.setBackground(cardBoxBg(0x441F6F45, Ui.OK, 2, 12));
        rightBox.setPadding(Ui.dp(c, 16), Ui.dp(c, 10), Ui.dp(c, 16), Ui.dp(c, 10));
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(0, -2, 1f);
        rlp.leftMargin = Ui.dp(c, 8);
        cmpRow.addView(rightBox, rlp);
        panel.addView(cmpRow, cmpLp);

        // 4. 左右双栏：左侧「图文推导图谱」 + 右侧「详细解析与避坑指南」
        LinearLayout bodyRow = Ui.row(c);
        explainScroll = new ScrollView(c);
        explainScroll.setFillViewport(true);
        LinearLayout.LayoutParams bodyLp = new LinearLayout.LayoutParams(-1, 0, 1f);
        bodyLp.topMargin = Ui.dp(c, 10);

        LinearLayout leftDiagram = buildVisualDiagramCard(c, q, wrongIdx);
        LinearLayout.LayoutParams leftLp = new LinearLayout.LayoutParams(0, -2, 4f);
        leftLp.rightMargin = Ui.dp(c, 10);
        bodyRow.addView(leftDiagram, leftLp);

        LinearLayout rightExplain = Ui.col(c);
        rightExplain.setBackground(cardBoxBg(0xFF141A24, 0x33FFFFFF, 1, 14));
        rightExplain.setPadding(Ui.dp(c, 20), Ui.dp(c, 14), Ui.dp(c, 20), Ui.dp(c, 14));

        TextView expHead = Ui.label(c, "★ 逐步文字详解与记忆要点", 19f, Ui.ACCENT);
        expHead.setTypeface(Typeface.DEFAULT_BOLD);
        rightExplain.addView(expHead);

        String rawExp = (q.explain == null || q.explain.isEmpty())
                ? ("本题正确答案为「" + q.options[q.answer] + "」。")
                : q.explain;
        TextView expContent = Ui.label(c, rawExp, 18f, Ui.TEXT);
        LinearLayout.LayoutParams eclp = new LinearLayout.LayoutParams(-1, -2);
        eclp.topMargin = Ui.dp(c, 10);
        rightExplain.addView(expContent, eclp);

        String memoryTip = "★ 避坑与巩固：你刚才误选了「" + q.options[wrongIdx]
                + "」，请重点记住本题核心结论「" + q.options[q.answer]
                + "」。本题已自动收入错题本，方便日后复习巩固。";
        TextView tipBox = Ui.label(c, memoryTip, 16f, 0xFFD2A8FF);
        tipBox.setBackground(cardBoxBg(0x228957E5, 0x448957E5, 1, 10));
        tipBox.setPadding(Ui.dp(c, 14), Ui.dp(c, 10), Ui.dp(c, 14), Ui.dp(c, 10));
        LinearLayout.LayoutParams tplp = new LinearLayout.LayoutParams(-1, -2);
        tplp.topMargin = Ui.dp(c, 12);
        rightExplain.addView(tipBox, tplp);

        LinearLayout.LayoutParams rightLp = new LinearLayout.LayoutParams(0, -1, 6f);
        rightLp.leftMargin = Ui.dp(c, 10);
        bodyRow.addView(rightExplain, rightLp);
        explainScroll.addView(bodyRow, new FrameLayout.LayoutParams(-1, -2));
        panel.addView(explainScroll, bodyLp);

        // 5. 底部确认看懂按钮
        explainBtn = Ui.label(c, "", 20f, Ui.ACCENT);
        explainBtn.setTypeface(Typeface.DEFAULT_BOLD);
        explainBtn.setGravity(Gravity.CENTER);
        explainBtn.setPadding(Ui.dp(c, 24), Ui.dp(c, 14), Ui.dp(c, 24), Ui.dp(c, 14));
        explainBtn.setBackground(explainBtnWaitBg());
        explainBtn.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) {
                tryConfirmExplain();
            }
        });
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(-1, -2);
        btnLp.topMargin = Ui.dp(c, 16);
        panel.addView(explainBtn, btnLp);

        explainTipView = Ui.label(c, "必须认真看懂上方错题解析并按「OK 键」确认后，才能继续做下一题", 15f, Ui.DIM);
        explainTipView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams etLp = new LinearLayout.LayoutParams(-1, -2);
        etLp.topMargin = Ui.dp(c, 8);
        panel.addView(explainTipView, etLp);

        int w = (int) (c.getResources().getDisplayMetrics().widthPixels * 0.92f);
        int h = (int) (c.getResources().getDisplayMetrics().heightPixels * 0.90f);
        explainOverlay.addView(panel, new FrameLayout.LayoutParams(w, h, Gravity.CENTER));
        addView(explainOverlay, new FrameLayout.LayoutParams(-1, -1));

        removeCallbacks(guardTick);
        post(guardTick);
    }

    private void tryConfirmExplain() {
        if (!explainVisible) return;
        long now = SystemClock.uptimeMillis();
        if (now < explainUnlockAt) {
            int sec = (int) ((explainUnlockAt - now + 999L) / 1000L);
            if (explainTipView != null) {
                explainTipView.setTextColor(Ui.BAD);
                explainTipView.setText("⚠ 请先不要急着按键！认真看完上方图文解析，还需 " + sec + " 秒后才能进入下一题");
            }
            return;
        }
        hideExplainOverlay();
        nextQuestion();
    }

    private void hideExplainOverlay() {
        explainVisible = false;
        if (explainOverlay != null) {
            if (explainOverlay.getParent() != null) removeView(explainOverlay);
            explainOverlay = null;
            explainScroll = null;
            explainBtn = null;
            explainTipView = null;
        }
    }

    // ---------- 家长口令与恢复码 ----------

    private void showPin() {
        if (pinVisible) return;
        pinVisible = true;
        if (pin == null) {
            pin = new PinPadView(getContext(), "家长口令（6 位）", new PinPadView.Listener() {
                @Override public void onPinSubmit(String s) {
                    long r = prefs.verifyPin(s);
                    if (r == 0) {
                        hidePin();
                        host.onPinOk();
                    } else if (r > 0) {
                        pin.fail("尝试次数过多，请 " + (r / 1000) + " 秒后再试");
                    } else {
                        pin.fail("口令不正确");
                    }
                }
                @Override public void onPinCancel() { hidePin(); }
            });
            pin.setFooter("忘记口令？用恢复码重置", new Runnable() {
                @Override public void run() { openRecovery(); }
            });
        }
        if (pin.getParent() != null) removeView(pin);
        pin.setSelfPaint(true);
        addView(pin, new FrameLayout.LayoutParams(-1, -1));
        box.setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
        pin.reset();
        pin.focusFirst();
    }

    private void hidePin() {
        if (!pinVisible) return;
        pinVisible = false;
        if (pin != null && pin.getParent() != null) removeView(pin);
        sel = 0;
        paintSelection();
        if (lockMode == Prefs.LOCK_QUIZ) post(guardTick);
    }

    private void openRecovery() {
        if (recovery != null) return;
        if (pin != null && pin.getParent() != null) removeView(pin);
        pinVisible = false;
        box.setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
        recovery = new RecoveryFlow(getContext(), prefs, new RecoveryFlow.Done() {
            @Override public void onDone(boolean ok) {
                if (recovery != null && recovery.getParent() != null) removeView(recovery);
                recovery = null;
                if (ok) host.onPinOk();
                else showPin();
            }
        });
        recovery.setSelfPaint(true);
        addView(recovery, new FrameLayout.LayoutParams(-1, -1));
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        lastKeyAt = SystemClock.uptimeMillis();
        int k = e.getKeyCode();
        if (recovery != null) { recovery.consumeKey(e); return true; }
        if (pinVisible) {
            if (e.getAction() == KeyEvent.ACTION_DOWN) {
                if (k == KeyEvent.KEYCODE_BACK) { hidePin(); return true; }
                if (pin.consumeNav(e)) return true;
                int d = PinPadView.digitOf(e);
                if (d >= 0) { pin.append((char) ('0' + d)); return true; }
                if (k == KeyEvent.KEYCODE_DEL) { pin.backspace(); return true; }
            }
            return true;
        }
        if (explainVisible) {
            if (e.getAction() == KeyEvent.ACTION_DOWN) {
                if (k == KeyEvent.KEYCODE_MENU || k == KeyEvent.KEYCODE_SETTINGS || k == KeyEvent.KEYCODE_BOOKMARK) {
                    showPin();
                    return true;
                }
                if (k == KeyEvent.KEYCODE_DPAD_UP && explainScroll != null) {
                    explainScroll.smoothScrollBy(0, 0 - Ui.dp(getContext(), 90));
                    return true;
                }
                if (k == KeyEvent.KEYCODE_DPAD_DOWN && explainScroll != null) {
                    explainScroll.smoothScrollBy(0, Ui.dp(getContext(), 90));
                    return true;
                }
                if (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER
                        || k == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                    tryConfirmExplain();
                    return true;
                }
                if (k == KeyEvent.KEYCODE_BACK) {
                    tryConfirmExplain();
                    return true;
                }
            }
            return true;
        }
        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            if (k == KeyEvent.KEYCODE_BACK) return true;
            if (k == KeyEvent.KEYCODE_MENU || k == KeyEvent.KEYCODE_SETTINGS || k == KeyEvent.KEYCODE_BOOKMARK) {
                showPin();
                return true;
            }
            if (lockMode == Prefs.LOCK_QUIZ) {
                if (k == KeyEvent.KEYCODE_DPAD_LEFT) { moveSel(-1, 0); return true; }
                if (k == KeyEvent.KEYCODE_DPAD_RIGHT) { moveSel(1, 0); return true; }
                if (k == KeyEvent.KEYCODE_DPAD_UP) { moveSel(0, -1); return true; }
                if (k == KeyEvent.KEYCODE_DPAD_DOWN) { moveSel(0, 1); return true; }
                if (k == KeyEvent.KEYCODE_DPAD_UP && explainScroll != null) {
                    explainScroll.smoothScrollBy(0, 0 - Ui.dp(getContext(), 90));
                    return true;
                }
                if (k == KeyEvent.KEYCODE_DPAD_DOWN && explainScroll != null) {
                    explainScroll.smoothScrollBy(0, Ui.dp(getContext(), 90));
                    return true;
                }
                if (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER
                        || k == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                    answer(sel);
                    return true;
                }
                int d = PinPadView.digitOf(e);
                if (d >= 1 && d <= 4) {
                    select(d - 1);
                    answer(d - 1);
                    return true;
                }
            }
        }
        return true;
    }
}
