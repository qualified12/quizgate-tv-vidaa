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
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class Ui {
    public static final int BG = 0xFF0B0E14;
    public static final int CARD = 0xFF161B24;
    public static final int TEXT = 0xFFFFFFFF;
    public static final int DIM = 0xFF9AA0A6;
    public static final int ACCENT = 0xFFFDC90C;
    public static final int OK = 0xFF2EA043;
    public static final int BAD = 0xFFFF5252;
    public static final int BLACK = 0xFF000000;

    private Ui() {}

    public static int dp(Context c, float dp) {
        return Math.round(dp * c.getResources().getDisplayMetrics().density);
    }

    public static TextView label(Context c, String s, float sp, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        return t;
    }

    public static TextView title(Context c, String s) {
        return title(c, s, 32f);
    }

    public static TextView title(Context c, String s, float sp) {
        TextView t = label(c, s, sp, TEXT);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    public static TextView key(Context c, String s) {
        TextView t = label(c, s, 22f, TEXT);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setGravity(Gravity.CENTER);
        t.setFocusable(true);
        t.setClickable(true);
        t.setBackground(keyBg(c));
        t.setMinimumWidth(dp(c, 96));
        t.setMinimumHeight(dp(c, 64));
        t.setPadding(dp(c, 20), dp(c, 12), dp(c, 20), dp(c, 12));
        return t;
    }

    public static GradientDrawable box(Context c, int fill, int strokeColor, int strokeDp, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        if (strokeDp > 0) d.setStroke(dp(c, strokeDp), strokeColor);
        d.setCornerRadius(dp(c, radiusDp));
        return d;
    }

    public static TextView homeKey(Context c, String s, boolean primary) {
        TextView t = label(c, s, 22f, primary ? BLACK : TEXT);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setGravity(Gravity.CENTER);
        t.setFocusable(true);
        t.setClickable(true);
        t.setPadding(dp(c, 28), dp(c, 16), dp(c, 28), dp(c, 16));
        StateListDrawable sld = new StateListDrawable();
        sld.addState(new int[]{android.R.attr.state_focused},
                box(c, primary ? ACCENT : 0xFF283040, TEXT, 3, 14));
        sld.addState(new int[0],
                box(c, primary ? ACCENT : CARD, 0x33FFFFFF, 1, 14));
        t.setBackground(sld);
        return t;
    }

    public static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        return l;
    }

    public static LinearLayout col(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    public static View spacer(Context c, int dpH) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(c, dpH)));
        return v;
    }

    public static GradientDrawable panelBg(Context c) {
        return box(c, 0xFF141820, 0x44FDC90C, 2, 18);
    }

    public static GradientDrawable optBg(Context c) {
        return box(c, 0x1AFFFFFF, 0x33FFFFFF, 1, 14);
    }

    public static StateListDrawable keyBg(Context c) {
        StateListDrawable sld = new StateListDrawable();
        sld.addState(new int[]{android.R.attr.state_focused},
                box(c, 0x33FDC90C, ACCENT, 3, 12));
        sld.addState(new int[0],
                box(c, 0xFF1E222A, 0x33FFFFFF, 1, 12));
        return sld;
    }

    public static GradientDrawable logoBg(Context c) {
        return box(c, 0xFF1F6F45, ACCENT, 3, 8);
    }
}
