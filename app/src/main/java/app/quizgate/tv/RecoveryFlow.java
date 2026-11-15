/*
 * QuizGate TV · VIDAA Edition
 * Fork of QuizGate TV (https://github.com/mtvtopcom/quizgate-tv), Apache-2.0.
 * Modified from the original QuizGate TV project by qualified12.
 * See NOTICE for the full list of changes.
 */
package app.quizgate.tv;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

public class RecoveryFlow extends FrameLayout {
    public interface Done {
        void onDone(boolean pinReset);
    }

    private static final int STEP_CODE = 0, STEP_PIN1 = 1, STEP_PIN2 = 2, STEP_SHOW = 3;
    private final Prefs prefs;
    private final Done done;
    private int step = STEP_CODE;
    private PinPadView pad;
    private CodePanel showPanel;
    private String firstPin = "";
    private boolean selfPaint = false;

    public RecoveryFlow(Context c, Prefs prefs, Done done) {
        super(c);
        this.prefs = prefs;
        this.done = done;
        buildCodeStep();
    }

    public void setSelfPaint(boolean on) {
        this.selfPaint = on;
        if (pad != null) pad.setSelfPaint(on);
        if (showPanel != null) showPanel.setSelfPaint(on);
    }

    private void buildCodeStep() {
        step = STEP_CODE;
        removeAllViews();
        pad = new PinPadView(getContext(), "输入 8 位恢复码", 8, false, new PinPadView.Listener() {
            @Override public void onPinSubmit(String code) {
                long r = prefs.verifyRecoveryCode(code);
                if (r == 0) {
                    buildPin1Step();
                } else if (r == -2) {
                    pad.fail("恢复码格式有误（请核对8位数字）");
                } else if (r > 0) {
                    pad.fail("错误过多，请 " + (r / 1000) + " 秒后再试");
                } else {
                    pad.fail("恢复码不正确");
                }
            }
            @Override public void onPinCancel() {
                done.onDone(false);
            }
        });
        pad.setSelfPaint(selfPaint);
        addView(pad, new FrameLayout.LayoutParams(-1, -1));
        pad.focusFirst();
    }

    private void buildPin1Step() {
        step = STEP_PIN1;
        removeAllViews();
        pad = new PinPadView(getContext(), "设置新 6 位家长口令", new PinPadView.Listener() {
            @Override public void onPinSubmit(String pin) {
                firstPin = pin;
                buildPin2Step();
            }
            @Override public void onPinCancel() {
                done.onDone(false);
            }
        });
        pad.setSelfPaint(selfPaint);
        addView(pad, new FrameLayout.LayoutParams(-1, -1));
        pad.focusFirst();
    }

    private void buildPin2Step() {
        step = STEP_PIN2;
        removeAllViews();
        pad = new PinPadView(getContext(), "再输一次新口令确认", new PinPadView.Listener() {
            @Override public void onPinSubmit(String pin) {
                if (!firstPin.equals(pin)) {
                    pad.fail("两次口令不一致，请重输");
                    return;
                }
                String newRc = Recovery.generate();
                prefs.resetPinAndRecovery(pin, newRc);
                buildShowStep(newRc);
            }
            @Override public void onPinCancel() {
                buildPin1Step();
            }
        });
        pad.setSelfPaint(selfPaint);
        addView(pad, new FrameLayout.LayoutParams(-1, -1));
        pad.focusFirst();
    }

    private void buildShowStep(String newRc) {
        step = STEP_SHOW;
        removeAllViews();
        showPanel = new CodePanel(getContext(), newRc, new Runnable() {
            @Override public void run() {
                prefs.markRecoverySaved();
                done.onDone(true);
            }
        });
        showPanel.setSelfPaint(selfPaint);
        addView(showPanel, new FrameLayout.LayoutParams(-1, -1));
    }

    public boolean consumeKey(KeyEvent e) {
        if (step == STEP_SHOW && showPanel != null) {
            return showPanel.consumeKey(e);
        }
        if (pad != null && e.getAction() == KeyEvent.ACTION_DOWN) {
            if (e.getKeyCode() == KeyEvent.KEYCODE_BACK) {
                done.onDone(false);
                return true;
            }
            if (pad.consumeNav(e)) return true;
            int d = PinPadView.digitOf(e);
            if (d >= 0) {
                pad.append((char) ('0' + d));
                return true;
            }
            if (e.getKeyCode() == KeyEvent.KEYCODE_DEL) {
                pad.backspace();
                return true;
            }
        }
        return true;
    }

    public static class CodePanel extends FrameLayout {
        private final TextView btn;
        private final Runnable onOk;
        private boolean selfPaint = false;

        public CodePanel(Context c, String code, final Runnable onOk) {
            super(c);
            this.onOk = onOk;
            setBackgroundColor(0xE6000000);
            LinearLayout box = Ui.col(c);
            box.setGravity(Gravity.CENTER_HORIZONTAL);
            box.setBackground(Ui.panelBg(c));
            box.setPadding(Ui.dp(c, 48), Ui.dp(c, 36), Ui.dp(c, 48), Ui.dp(c, 36));

            box.addView(Ui.title(c, "请拍照保存恢复码", 28f));
            TextView sub = Ui.label(c, "忘记家长口令时凭此码重置（本机唯一，仅显示这一次）", 16f, Ui.DIM);
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-2, -2);
            slp.topMargin = Ui.dp(c, 10);
            box.addView(sub, slp);

            TextView codeView = Ui.label(c, Recovery.pretty(code), 40f, Ui.ACCENT);
            codeView.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-2, -2);
            clp.topMargin = Ui.dp(c, 22);
            box.addView(codeView, clp);

            btn = Ui.homeKey(c, "我已拍照保存", true);
            btn.setOnClickListener(new OnClickListener() {
                @Override public void onClick(View v) {
                    if (onOk != null) onOk.run();
                }
            });
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-2, -2);
            blp.topMargin = Ui.dp(c, 26);
            box.addView(btn, blp);

            addView(box, new FrameLayout.LayoutParams(Ui.dp(c, 640), -2, Gravity.CENTER));
        }

        public void setSelfPaint(boolean on) {
            this.selfPaint = on;
        }

        public boolean consumeKey(KeyEvent e) {
            if (e.getAction() == KeyEvent.ACTION_DOWN) {
                int k = e.getKeyCode();
                if (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER || k == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                    if (onOk != null) onOk.run();
                    return true;
                }
            }
            return true;
        }
    }
}
