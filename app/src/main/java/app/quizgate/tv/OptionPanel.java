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
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

public class OptionPanel extends FrameLayout {
    public interface OnPick {
        void onValue(int v);
    }

    private static final int STEP_CHOOSE = 0, STEP_NUM = 1;
    private static final int CUSTOM = Integer.MIN_VALUE;

    private final String title;
    private final String[] labels;
    private final int[] values;
    private final int current;
    private final String unit;
    private final int min;
    private final int max;
    private final OnPick onPick;
    private final Runnable onCancel;

    private final LinearLayout box;
    private TextView[] keys;
    private int[] keyValues;
    private TextView display;
    private TextView msg;
    private final StringBuilder buf = new StringBuilder();
    private int step = STEP_CHOOSE;
    private int sel = 0;

    public OptionPanel(Context c, String title, String[] labels, int[] values,
                       int current, String unit, int min, int max,
                       OnPick onPick, Runnable onCancel) {
        super(c);
        this.title = title;
        this.labels = labels;
        this.values = values;
        this.current = current;
        this.unit = unit == null ? "" : unit;
        this.min = min;
        this.max = max;
        this.onPick = onPick;
        this.onCancel = onCancel;

        setBackgroundColor(0xE6000000);
        setClickable(true);
        setFocusable(true);

        box = Ui.col(c);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setBackground(Ui.panelBg(c));
        box.setPadding(Ui.dp(c, 36), Ui.dp(c, 28), Ui.dp(c, 36), Ui.dp(c, 28));

        int w = (int) (c.getResources().getDisplayMetrics().widthPixels * 0.86f);
        addView(box, new FrameLayout.LayoutParams(w, -2, Gravity.CENTER));
        buildChoose();
    }

    private void buildChoose() {
        step = STEP_CHOOSE;
        sel = 0;
        box.removeAllViews();
        Context ctx = getContext();

        box.addView(Ui.title(ctx, title, 28f));
        TextView sub = Ui.label(ctx, "方向键选择 · OK 确认 · BACK 取消", 16f, Ui.DIM);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-2, -2);
        slp.topMargin = Ui.dp(ctx, 8);
        box.addView(sub, slp);

        boolean custom = !unit.isEmpty();
        int n = values.length + (custom ? 1 : 0);
        keys = new TextView[n];
        keyValues = new int[n];

        LinearLayout row = Ui.row(ctx);
        row.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-2, -2);
        rlp.topMargin = Ui.dp(ctx, 22);

        for (int i = 0; i < n; i++) {
            final int idx = i;
            boolean isCustom = custom && i == values.length;
            String lb = isCustom ? "自定义" : (labels[i] + (unit.isEmpty() ? "" : " " + unit));
            TextView b = Ui.key(ctx, lb);
            b.setFocusable(false);
            b.setClickable(true);
            b.setOnClickListener(new OnClickListener() {
                @Override public void onClick(View v) {
                    sel = idx;
                    paint();
                    choose(idx);
                }
            });
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-2, Ui.dp(ctx, 64));
            if (i > 0) blp.leftMargin = Ui.dp(ctx, 12);
            row.addView(b, blp);
            keys[i] = b;
            keyValues[i] = isCustom ? CUSTOM : values[i];
            if (!isCustom && values[i] == current) sel = i;
        }
        box.addView(row, rlp);

        if (custom) {
            TextView hint = Ui.label(ctx, "「自定义」可输入 " + min + "-" + max + " " + unit + "，用遥控器数字键或屏幕键盘", 15f, Ui.DIM);
            LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(-2, -2);
            hlp.topMargin = Ui.dp(ctx, 16);
            box.addView(hint, hlp);
        }
        paint();
    }

    private void choose(int i) {
        if (i >= 0 && i < values.length) {
            if (onPick != null) onPick.onValue(values[i]);
        } else if (!unit.isEmpty()) {
            showNumpad();
        }
    }

    private void showNumpad() {
        step = STEP_NUM;
        sel = 0;
        buf.setLength(0);
        box.removeAllViews();
        Context ctx = getContext();

        box.addView(Ui.title(ctx, "自定义" + title, 26f));
        display = Ui.label(ctx, "- " + unit, 34f, Ui.ACCENT);
        display.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(-2, -2);
        dlp.topMargin = Ui.dp(ctx, 10);
        box.addView(display, dlp);

        msg = Ui.label(ctx, "输入 " + min + "-" + max + " " + unit + "，输完按「确定」", 16f, Ui.DIM);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(-2, -2);
        mlp.topMargin = Ui.dp(ctx, 6);
        box.addView(msg, mlp);

        String[] lbs = {"1","2","3","4","5","6","7","8","9","删除","0","确定"};
        keys = new TextView[12];
        keyValues = new int[12];
        for (int r = 0; r < 4; r++) {
            LinearLayout row = Ui.row(ctx);
            row.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-2, -2);
            rlp.topMargin = Ui.dp(ctx, r == 0 ? 14 : 8);
            for (int col = 0; col < 3; col++) {
                final int idx = r * 3 + col;
                TextView k = Ui.key(ctx, lbs[idx]);
                k.setFocusable(false);
                k.setOnClickListener(new OnClickListener() {
                    @Override public void onClick(View v) {
                        sel = idx;
                        paint();
                        pressNumKey(idx);
                    }
                });
                LinearLayout.LayoutParams klp = new LinearLayout.LayoutParams(Ui.dp(ctx, 110), Ui.dp(ctx, 56));
                if (col > 0) klp.leftMargin = Ui.dp(ctx, 10);
                row.addView(k, klp);
                keys[idx] = k;
                keyValues[idx] = idx;
            }
            box.addView(row, rlp);
        }
        paint();
    }

    private void pressNumKey(int idx) {
        if (idx == 9) {
            if (buf.length() > 0) buf.setLength(buf.length() - 1);
            updateDisplay();
        } else if (idx == 10) {
            appendDigit(0);
        } else if (idx == 11) {
            submit();
        } else if (idx >= 0 && idx <= 8) {
            appendDigit(idx + 1);
        }
    }

    private void appendDigit(int d) {
        if (buf.length() == 0 && d == 0) return;
        if (buf.length() >= 4) return;
        buf.append((char) ('0' + d));
        updateDisplay();
    }

    private void updateDisplay() {
        if (display != null) {
            display.setText((buf.length() == 0 ? "-" : buf.toString()) + " " + unit);
        }
    }

    private void submit() {
        int v = 0;
        try { v = buf.length() == 0 ? 0 : Integer.parseInt(buf.toString()); } catch (Exception ignore) {}
        if (v < min || v > max) {
            if (msg != null) {
                msg.setTextColor(Ui.BAD);
                msg.setText("请输入 " + min + "-" + max + " 之间的" + unit);
            }
            return;
        }
        if (onPick != null) onPick.onValue(v);
    }

    private void paint() {
        if (keys == null) return;
        for (int i = 0; i < keys.length; i++) {
            boolean on = (i == sel);
            boolean cur = (step == STEP_CHOOSE && keyValues[i] == current && keyValues[i] != CUSTOM);
            GradientDrawable d = new GradientDrawable();
            if (on) {
                d.setColor(Ui.ACCENT);
                d.setStroke(Ui.dp(getContext(), 2), Ui.TEXT);
                keys[i].setTextColor(0xFF20160A);
            } else if (cur) {
                d.setColor(0x26FDC90C);
                d.setStroke(Ui.dp(getContext(), 2), Ui.ACCENT);
                keys[i].setTextColor(Ui.ACCENT);
            } else {
                d.setColor(0xFF1E222A);
                d.setStroke(Ui.dp(getContext(), 1), 0x33FFFFFF);
                keys[i].setTextColor(Ui.TEXT);
            }
            d.setCornerRadius(Ui.dp(getContext(), 12));
            keys[i].setBackground(d);
        }
    }

    public boolean consumeKey(KeyEvent e) {
        if (e.getAction() != KeyEvent.ACTION_DOWN) return true;
        int k = e.getKeyCode();
        if (k == KeyEvent.KEYCODE_BACK) {
            if (step == STEP_NUM) buildChoose();
            else if (onCancel != null) onCancel.run();
            return true;
        }
        if (step == STEP_CHOOSE) {
            if (k == KeyEvent.KEYCODE_DPAD_LEFT) {
                sel = (sel - 1 + keys.length) % keys.length;
                paint();
                return true;
            }
            if (k == KeyEvent.KEYCODE_DPAD_RIGHT) {
                sel = (sel + 1) % keys.length;
                paint();
                return true;
            }
            if (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER || k == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                choose(sel);
                return true;
            }
            int d = PinPadView.digitOf(e);
            if (d >= 1 && d <= keys.length) {
                sel = d - 1;
                paint();
                choose(sel);
                return true;
            }
        } else {
            int d = PinPadView.digitOf(e);
            if (d >= 0) {
                appendDigit(d);
                return true;
            }
            if (k == KeyEvent.KEYCODE_DEL) {
                pressNumKey(9);
                return true;
            }
            if (k == KeyEvent.KEYCODE_DPAD_LEFT) {
                sel = (sel / 3) * 3 + (sel % 3 + 2) % 3;
                paint();
                return true;
            }
            if (k == KeyEvent.KEYCODE_DPAD_RIGHT) {
                sel = (sel / 3) * 3 + (sel % 3 + 1) % 3;
                paint();
                return true;
            }
            if (k == KeyEvent.KEYCODE_DPAD_UP) {
                sel = (sel - 3 + 12) % 12;
                paint();
                return true;
            }
            if (k == KeyEvent.KEYCODE_DPAD_DOWN) {
                sel = (sel + 3) % 12;
                paint();
                return true;
            }
            if (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER || k == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                pressNumKey(sel);
                return true;
            }
        }
        return true;
    }
}
