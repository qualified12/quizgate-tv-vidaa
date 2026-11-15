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
        this.targetCount = prefs.quizCount();
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
        q = bank.nextFromPrefs(prefs, rnd);
        answering = false;
        questionShownAt = SystemClock.uptimeMillis();
        String badge = (q.badge == null || q.badge.isEmpty()) ? "" : ("【" + q.badge + "】 · ");
        String prog = "已答对 " + correctCount + " / " + targetCount + " 题";
        kickerView.setText(prog + " · " + badge + "后台音视频已静音");
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

    private void select(int i) {
        sel = Math.max(0, Math.min(3, i));
        paintSelection();
    }

    private void moveSel(int dx, int dy) {
        if (answering || lockMode != Prefs.LOCK_QUIZ) return;
        int col = sel % 2, row = sel / 2;
        col = (col + dx + 2) % 2;
        row = (row + dy + 2) % 2;
        select(row * 2 + col);
    }

    private void answer(int i) {
        if (lockMode != Prefs.LOCK_QUIZ || answering || pinVisible || q == null) return;
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
            }
            if (guessTrigger > 0 && wrongStreak >= guessTrigger) {
                cooldownUntil = SystemClock.uptimeMillis() + guessCooldownSec * 1000L;
                wrongStreak = 0;
            }
            opts[i].setBackgroundColor(0x8C6B1D1D);
            opts[q.answer].setBackgroundColor(0xFF1F6F45);
            feedback.setTextColor(Ui.BAD);
            String penTip = penaltyPerWrong > 0 ? ("（加罚 " + penaltyPerWrong + " 题，共需 " + targetCount + " 题）") : "";
            String ex = (q.explain == null || q.explain.isEmpty()) ? "" : (" " + q.explain);
            feedback.setText("✗ 答错了，正解「" + q.options[q.answer] + "」" + penTip + ex);
            postDelayed(new Runnable() {
                @Override public void run() { nextQuestion(); }
            }, 2100);
        }
    }

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
                if (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER
                        || k == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                    answer(sel);
                    return true;
                }
                int d = PinPadView.digitOf(e);
                if (d >= 1 && d <= 4) {
                    select(d - 1);
                    answer(sel);
                    return true;
                }
            }
            if (swallow(k)) return true;
        }
        return super.dispatchKeyEvent(e);
    }

    private boolean swallow(int k) {
        return k == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            || k == KeyEvent.KEYCODE_MEDIA_PLAY
            || k == KeyEvent.KEYCODE_MEDIA_PAUSE
            || k == KeyEvent.KEYCODE_MEDIA_NEXT
            || k == KeyEvent.KEYCODE_MEDIA_PREVIOUS
            || k == KeyEvent.KEYCODE_MEDIA_STOP
            || k == KeyEvent.KEYCODE_MEDIA_REWIND
            || k == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD
            || k == KeyEvent.KEYCODE_TV_INPUT
            || k == KeyEvent.KEYCODE_TV
            || k == KeyEvent.KEYCODE_GUIDE
            || k == KeyEvent.KEYCODE_INFO
            || k == KeyEvent.KEYCODE_VOICE_ASSIST
            || k == KeyEvent.KEYCODE_SEARCH;
    }
}
