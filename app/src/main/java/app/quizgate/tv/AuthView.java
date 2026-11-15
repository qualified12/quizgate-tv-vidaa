/*
 * QuizGate TV · VIDAA Edition
 * Fork of QuizGate TV (https://github.com/mtvtopcom/quizgate-tv), Apache-2.0.
 * Modified from the original QuizGate TV project by qualified12.
 * See NOTICE for the full list of changes.
 */
package app.quizgate.tv;

import android.content.Context;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

public class AuthView extends FrameLayout {
    public static final String ID_OVERLAY = "overlay";
    public static final String ID_NOTIF = "notif";

    public static final class Item {
        public final String id;
        public final String name;
        public final boolean ok;
        public final String why;

        public Item(String id, String name, boolean ok, String why) {
            this.id = id;
            this.name = name;
            this.ok = ok;
            this.why = why;
        }
    }

    public interface Host {
        List<Item> items();
        void fix(String id);
        void startAnyway();
        void cancel();
    }

    private final Host host;
    private final LinearLayout box;

    public AuthView(Context c, final Host host) {
        super(c);
        this.host = host;
        setBackgroundColor(0xE6000000);
        box = Ui.col(c);
        box.setBackground(Ui.panelBg(c));
        box.setPadding(Ui.dp(c, 36), Ui.dp(c, 28), Ui.dp(c, 36), Ui.dp(c, 28));
        addView(box, new FrameLayout.LayoutParams(Ui.dp(c, 720), -2, Gravity.CENTER));
        refresh();
    }

    public void refresh() {
        box.removeAllViews();
        Context c = getContext();
        box.addView(Ui.title(c, "先补齐必需授权", 26f));
        List<Item> list = host.items();
        boolean overlayOk = true;
        for (int i = 0; i < list.size(); i++) {
            final Item it = list.get(i);
            if (ID_OVERLAY.equals(it.id) && !it.ok) overlayOk = false;
            TextView tv = Ui.label(c, (it.ok ? "✓ " : "✗ ") + it.name + "：" + it.why, 18f, it.ok ? Ui.OK : Ui.BAD);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = Ui.dp(c, 12);
            box.addView(tv, lp);
        }
        TextView tip = Ui.label(c, "按确定键继续（或用电脑 adb 补齐授权）", 16f, Ui.DIM);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-1, -2);
        tlp.topMargin = Ui.dp(c, 16);
        box.addView(tip, tlp);
    }

    public boolean consumeKey(KeyEvent e) {
        if (e.getAction() != KeyEvent.ACTION_DOWN) return true;
        int k = e.getKeyCode();
        if (k == KeyEvent.KEYCODE_BACK) {
            host.cancel();
            return true;
        }
        if (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER || k == KeyEvent.KEYCODE_NUMPAD_ENTER) {
            host.startAnyway();
            return true;
        }
        return true;
    }
}
