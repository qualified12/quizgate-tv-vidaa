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
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MiniView extends FrameLayout {
    private final TextView cap;
    private final TextView num;

    public MiniView(Context c) {
        super(c);
        LinearLayout box = Ui.col(c);
        box.setGravity(Gravity.CENTER);
        box.setBackground(Ui.panelBg(c));
        box.setPadding(Ui.dp(c, 20), Ui.dp(c, 10), Ui.dp(c, 20), Ui.dp(c, 10));

        cap = Ui.label(c, "剩余", 13f, Ui.DIM);
        num = Ui.label(c, "0:30", 24f, Ui.ACCENT);
        num.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);

        box.addView(cap);
        box.addView(num);
        addView(box, new FrameLayout.LayoutParams(-2, -2));
        setAlpha(0.92f);
    }

    public void setRemaining(long ms) {
        num.setText(Core.fmt(ms));
    }

    public void setCaption(String s) {
        cap.setText(s);
    }
}
