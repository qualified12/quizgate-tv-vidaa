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

public class PinPadView extends FrameLayout {
    public interface Listener {
        void onPinSubmit(String pin);
        void onPinCancel();
    }

    private final int maxLen;
    private final boolean masked;
    private final Listener listener;
    private final TextView titleView;
    private final TextView dotsView;
    private final TextView msgView;
    private final TextView[] keys = new TextView[12];
    private TextView footerView;
    private Runnable footerAction;
    private final StringBuilder buf = new StringBuilder();
    private boolean selfPaint = false;
    private int sel = 4; // default center '5'

    public PinPadView(Context c, String title, Listener listener) {
        this(c, title, 6, true, listener);
    }

    public PinPadView(Context c, String title, int len, boolean masked, final Listener listener) {
        super(c);
        this.maxLen = len;
        this.masked = masked;
        this.listener = listener;

        setBackgroundColor(0xE6000000);
        setClickable(true);
        setFocusable(true);

        LinearLayout box = Ui.col(c);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setBackground(Ui.panelBg(c));
        box.setPadding(Ui.dp(c, 40), Ui.dp(c, 28), Ui.dp(c, 40), Ui.dp(c, 28));

        titleView = Ui.title(c, title, 26f);
        box.addView(titleView);

        dotsView = Ui.label(c, "", 32f, Ui.ACCENT);
        dotsView.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(-2, -2);
        dlp.topMargin = Ui.dp(c, 12);
        box.addView(dotsView, dlp);

        msgView = Ui.label(c, "支持遥控器数字键直输或方向键点按", 16f, Ui.DIM);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(-2, -2);
        mlp.topMargin = Ui.dp(c, 8);
        box.addView(msgView, mlp);

        String[] labels = {"1","2","3","4","5","6","7","8","9","退格","0","取消"};
        for (int r = 0; r < 4; r++) {
            LinearLayout row = Ui.row(c);
            row.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-2, -2);
            rlp.topMargin = Ui.dp(c, r == 0 ? 16 : 8);
            for (int col = 0; col < 3; col++) {
                final int idx = r * 3 + col;
                final String lb = labels[idx];
                TextView k = Ui.key(c, lb);
                LinearLayout.LayoutParams klp = new LinearLayout.LayoutParams(Ui.dp(c, 110), Ui.dp(c, 58));
                if (col > 0) klp.leftMargin = Ui.dp(c, 10);
                k.setOnClickListener(new OnClickListener() {
                    @Override public void onClick(View v) {
                        pressKey(idx);
                    }
                });
                row.addView(k, klp);
                keys[idx] = k;
            }
            box.addView(row, rlp);
        }

        footerView = Ui.label(c, "", 16f, Ui.DIM);
        footerView.setVisibility(GONE);
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(-2, -2);
        flp.topMargin = Ui.dp(c, 14);
        box.addView(footerView, flp);

        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(Ui.dp(c, 480), -2, Gravity.CENTER);
        addView(box, blp);
        updateDots();
    }

    public void setTitle(String s) { titleView.setText(s); }

    public void setFooter(String text, final Runnable action) {
        footerAction = action;
        footerView.setText(text);
        footerView.setVisibility(VISIBLE);
        footerView.setFocusable(!selfPaint);
        footerView.setClickable(true);
        footerView.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) {
                if (footerAction != null) footerAction.run();
            }
        });
    }

    public void setSelfPaint(boolean on) {
        this.selfPaint = on;
        for (int i = 0; i < keys.length; i++) {
            keys[i].setFocusable(!on);
        }
        if (footerView != null) footerView.setFocusable(!on);
        paint();
    }

    public void reset() {
        buf.setLength(0);
        msgView.setTextColor(Ui.DIM);
        msgView.setText("支持遥控器数字键直输或方向键点按");
        updateDots();
        paint();
    }

    public void focusFirst() {
        sel = 4;
        if (selfPaint) {
            paint();
        } else if (keys[0] != null) {
            keys[0].requestFocus();
        }
    }

    public void fail(String s) {
        if (masked) buf.setLength(0);
        updateDots();
        msgView.setTextColor(Ui.BAD);
        msgView.setText(s);
    }

    public void append(char c) {
        if (buf.length() >= maxLen) return;
        buf.append(c);
        updateDots();
        if (buf.length() == maxLen && listener != null) {
            listener.onPinSubmit(buf.toString());
        }
    }

    public void backspace() {
        if (buf.length() > 0) {
            buf.setLength(buf.length() - 1);
            updateDots();
        }
    }

    private void pressKey(int idx) {
        if (idx == 9) {
            backspace();
        } else if (idx == 10) {
            append('0');
        } else if (idx == 11) {
            if (listener != null) listener.onPinCancel();
        } else if (idx >= 0 && idx <= 8) {
            append((char) ('1' + idx));
        } else if (idx == 12 && footerAction != null) {
            footerAction.run();
        }
    }

    private void updateDots() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < maxLen; i++) {
            if (i > 0) sb.append(' ');
            if (i < buf.length()) {
                sb.append(masked ? '●' : buf.charAt(i));
            } else {
                sb.append('○');
            }
        }
        dotsView.setText(sb.toString());
    }

    public boolean consumeNav(KeyEvent e) {
        if (!selfPaint || e.getAction() != KeyEvent.ACTION_DOWN) return false;
        int k = e.getKeyCode();
        int maxIdx = (footerAction != null) ? 12 : 11;
        if (k == KeyEvent.KEYCODE_DPAD_LEFT) {
            if (sel < 12) sel = (sel / 3) * 3 + (sel % 3 + 2) % 3;
            paint();
            return true;
        }
        if (k == KeyEvent.KEYCODE_DPAD_RIGHT) {
            if (sel < 12) sel = (sel / 3) * 3 + (sel % 3 + 1) % 3;
            paint();
            return true;
        }
        if (k == KeyEvent.KEYCODE_DPAD_UP) {
            if (sel == 12) sel = 10;
            else if (sel >= 3) sel = sel - 3;
            paint();
            return true;
        }
        if (k == KeyEvent.KEYCODE_DPAD_DOWN) {
            if (sel <= 8) sel = sel + 3;
            else if (footerAction != null) sel = 12;
            paint();
            return true;
        }
        if (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER || k == KeyEvent.KEYCODE_NUMPAD_ENTER) {
            pressKey(sel);
            return true;
        }
        return false;
    }

    private void paint() {
        if (!selfPaint) return;
        for (int i = 0; i < 12; i++) {
            boolean on = (i == sel);
            GradientDrawable d = new GradientDrawable();
            d.setColor(on ? 0x38FDC90C : 0xFF1E222A);
            d.setStroke(Ui.dp(getContext(), on ? 3 : 1), on ? Ui.ACCENT : 0x33FFFFFF);
            d.setCornerRadius(Ui.dp(getContext(), 12));
            keys[i].setBackground(d);
            keys[i].setTextColor(on ? Ui.ACCENT : Ui.TEXT);
        }
        if (footerView != null) {
            footerView.setTextColor(sel == 12 ? Ui.ACCENT : Ui.DIM);
        }
    }

    public static int digitOf(KeyEvent e) {
        int k = e.getKeyCode();
        if (k >= KeyEvent.KEYCODE_0 && k <= KeyEvent.KEYCODE_9) return k - KeyEvent.KEYCODE_0;
        if (k >= KeyEvent.KEYCODE_NUMPAD_0 && k <= KeyEvent.KEYCODE_NUMPAD_9) return k - KeyEvent.KEYCODE_NUMPAD_0;
        return -1;
    }
}
