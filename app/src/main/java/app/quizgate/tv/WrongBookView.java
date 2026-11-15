/*
 * QuizGate TV · VIDAA Edition
 * Fork of QuizGate TV (https://github.com/mtvtopcom/quizgate-tv), Apache-2.0.
 * NEW FILE added by qualified12; not present in upstream.
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

import org.json.JSONArray;
import org.json.JSONObject;

public class WrongBookView extends FrameLayout {
    private final Prefs prefs;
    private final Runnable onClose;
    private final LinearLayout listBox;
    private final TextView pageLabel;
    private JSONArray items;
    private int page = 0;
    private static final int PAGE_SIZE = 4;

    public WrongBookView(Context c, Prefs prefs, final Runnable onClose) {
        super(c);
        this.prefs = prefs;
        this.onClose = onClose;
        this.items = prefs.getWrongArray();

        setBackgroundColor(0xF00B0E14);
        setClickable(true);
        setFocusable(true);

        LinearLayout box = Ui.col(c);
        box.setBackground(Ui.panelBg(c));
        box.setPadding(Ui.dp(c, 36), Ui.dp(c, 24), Ui.dp(c, 36), Ui.dp(c, 24));

        LinearLayout top = Ui.row(c);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(Ui.title(c, "错题记录本（共 " + items.length() + " 题）", 26f),
                new LinearLayout.LayoutParams(0, -2, 1f));
        pageLabel = Ui.label(c, "", 16f, Ui.ACCENT);
        top.addView(pageLabel);
        box.addView(top);

        listBox = Ui.col(c);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(-1, 0, 1f);
        llp.topMargin = Ui.dp(c, 12);
        box.addView(listBox, llp);

        TextView hint = Ui.label(c, "左右键翻页 · 按 0 键清空错题 · 按 BACK 返回设置", 16f, Ui.DIM);
        hint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(-1, -2);
        hlp.topMargin = Ui.dp(c, 12);
        box.addView(hint, hlp);

        int w = (int) (c.getResources().getDisplayMetrics().widthPixels * 0.90f);
        int h = (int) (c.getResources().getDisplayMetrics().heightPixels * 0.86f);
        addView(box, new FrameLayout.LayoutParams(w, h, Gravity.CENTER));
        renderPage();
    }

    private void renderPage() {
        listBox.removeAllViews();
        Context c = getContext();
        int total = items.length();
        if (total == 0) {
            pageLabel.setText("0 / 0");
            TextView empty = Ui.label(c, "太棒了！目前还没有错题记录。", 22f, Ui.OK);
            empty.setGravity(Gravity.CENTER);
            listBox.addView(empty, new LinearLayout.LayoutParams(-1, -1));
            return;
        }
        int totalPages = (total + PAGE_SIZE - 1) / PAGE_SIZE;
        page = Math.max(0, Math.min(totalPages - 1, page));
        pageLabel.setText("第 " + (page + 1) + " / " + totalPages + " 页");

        int start = page * PAGE_SIZE;
        int end = Math.min(total, start + PAGE_SIZE);
        for (int i = start; i < end; i++) {
            JSONObject o = items.optJSONObject(i);
            if (o == null) continue;
            LinearLayout card = Ui.col(c);
            card.setBackground(Ui.box(c, 0xFF1A202C, 0x22FFFFFF, 1, 12));
            card.setPadding(Ui.dp(c, 18), Ui.dp(c, 10), Ui.dp(c, 18), Ui.dp(c, 10));
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-1, -2);
            if (i > start) clp.topMargin = Ui.dp(c, 10);

            String head = "[" + o.optString("t", "") + "] " + o.optString("b", "") + "  " + o.optString("s", "");
            TextView tvHead = Ui.label(c, head, 17f, Ui.TEXT);
            tvHead.setTypeface(Typeface.DEFAULT_BOLD);
            card.addView(tvHead);

            String ansLine = "错选：" + o.optString("w", "") + "   |   正解：" + o.optString("r", "")
                    + (o.optString("e", "").isEmpty() ? "" : ("   (" + o.optString("e", "") + ")"));
            TextView tvAns = Ui.label(c, ansLine, 15f, Ui.ACCENT);
            tvAns.setPadding(0, Ui.dp(c, 4), 0, 0);
            card.addView(tvAns);

            listBox.addView(card, clp);
        }
    }

    public boolean consumeKey(KeyEvent e) {
        if (e.getAction() != KeyEvent.ACTION_DOWN) return true;
        int k = e.getKeyCode();
        if (k == KeyEvent.KEYCODE_BACK) {
            if (onClose != null) onClose.run();
            return true;
        }
        int totalPages = Math.max(1, (items.length() + PAGE_SIZE - 1) / PAGE_SIZE);
        if (k == KeyEvent.KEYCODE_DPAD_RIGHT || k == KeyEvent.KEYCODE_DPAD_DOWN) {
            page = (page + 1) % totalPages;
            renderPage();
            return true;
        }
        if (k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_UP) {
            page = (page - 1 + totalPages) % totalPages;
            renderPage();
            return true;
        }
        if (k == KeyEvent.KEYCODE_0 || k == KeyEvent.KEYCODE_NUMPAD_0) {
            prefs.clearWrongBook();
            items = prefs.getWrongArray();
            page = 0;
            renderPage();
            return true;
        }
        return true;
    }
}
