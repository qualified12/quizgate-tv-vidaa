/*
 * QuizGate TV · VIDAA Edition
 * Fork of QuizGate TV (https://github.com/mtvtopcom/quizgate-tv), Apache-2.0.
 * NEW FILE added by qualified12; not present in upstream.
 * See NOTICE for the full list of changes.
 */

package app.quizgate.tv;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 遥控器友好的多选面板（用于「多年级联合出题」与「多学科随机出题」）。
 * - 每次勾选/取消或点击快捷预设时【实时自动保存】到 SharedPreferences；
 * - 按遥控器「返回键（BACK）」或点击底部「✓ 确认保存」均会保存当前多选结果并退出，绝不丢失勾选！
 */
public class MultiSelectPanel extends FrameLayout {
    public interface OnConfirm {
        void onConfirmed(int bitmask);
    }

    private final String[] itemLabels;
    private final String[] presetLabels;
    private final int[] presetMasks;
    private final int cols;
    private int mask;
    private final int initialSavedMask;
    private final int allMask;
    private final OnConfirm onConfirm;
    private final Runnable onCancel;

    private final TextView[] itemViews;
    private final TextView[] actionViews;
    private final TextView statusView;
    private int sel = 0;

    public MultiSelectPanel(Context c, String title, String subtitle,
                            String[] itemLabels, int initialMask,
                            String[] presetLabels, int[] presetMasks,
                            int cols, OnConfirm onConfirm, Runnable onCancel) {
        super(c);
        this.itemLabels = itemLabels;
        this.presetLabels = presetLabels;
        this.presetMasks = presetMasks;
        this.cols = Math.max(1, cols);
        this.allMask = (1 << itemLabels.length) - 1;
        this.mask = (initialMask & allMask) == 0 ? allMask : (initialMask & allMask);
        this.initialSavedMask = this.mask;
        this.onConfirm = onConfirm;
        this.onCancel = onCancel;

        setBackgroundColor(0xE6000000);
        setClickable(true);
        setFocusable(true);

        LinearLayout box = Ui.col(c);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setBackground(Ui.panelBg(c));
        box.setPadding(Ui.dp(c, 36), Ui.dp(c, 26), Ui.dp(c, 36), Ui.dp(c, 26));

        box.addView(Ui.title(c, title, 28f));

        TextView sub = Ui.label(c, subtitle + "（勾选即自动生效，按返回键或点确认均可保存）", 16f, Ui.DIM);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-2, -2);
        slp.topMargin = Ui.dp(c, 6);
        box.addView(sub, slp);

        itemViews = new TextView[itemLabels.length];
        int rows = (itemLabels.length + this.cols - 1) / this.cols;
        for (int r = 0; r < rows; r++) {
            LinearLayout row = Ui.row(c);
            row.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2);
            rlp.topMargin = Ui.dp(c, r == 0 ? 18 : 10);
            for (int col = 0; col < this.cols; col++) {
                final int idx = r * this.cols + col;
                if (idx >= itemLabels.length) break;
                TextView b = Ui.key(c, "");
                b.setFocusable(false);
                b.setOnClickListener(new OnClickListener() {
                    @Override public void onClick(View v) {
                        sel = idx;
                        toggleItem(idx);
                    }
                });
                LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(0, Ui.dp(c, 60), 1f);
                if (col > 0) blp.leftMargin = Ui.dp(c, 10);
                row.addView(b, blp);
                itemViews[idx] = b;
            }
            box.addView(row, rlp);
        }

        statusView = Ui.label(c, "", 16f, Ui.ACCENT);
        LinearLayout.LayoutParams stlp = new LinearLayout.LayoutParams(-2, -2);
        stlp.topMargin = Ui.dp(c, 14);
        box.addView(statusView, stlp);

        int actionCount = presetLabels.length + 2;
        actionViews = new TextView[actionCount];
        LinearLayout actRow = Ui.row(c);
        actRow.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(-1, -2);
        alp.topMargin = Ui.dp(c, 16);

        for (int i = 0; i < actionCount; i++) {
            final int actIdx = i;
            final int globalIdx = itemLabels.length + i;
            String lb;
            if (i < presetLabels.length) lb = presetLabels[i];
            else if (i == presetLabels.length) lb = "✓ 保存并返回";
            else lb = "还原取消";

            TextView ab = Ui.key(c, lb);
            ab.setFocusable(false);
            ab.setOnClickListener(new OnClickListener() {
                @Override public void onClick(View v) {
                    sel = globalIdx;
                    triggerAction(actIdx);
                }
            });
            LinearLayout.LayoutParams ablp = new LinearLayout.LayoutParams(0, Ui.dp(c, 58), 1f);
            if (i > 0) ablp.leftMargin = Ui.dp(c, 10);
            actRow.addView(ab, ablp);
            actionViews[i] = ab;
        }
        box.addView(actRow, alp);

        int w = (int) (c.getResources().getDisplayMetrics().widthPixels * 0.88f);
        addView(box, new FrameLayout.LayoutParams(w, -2, Gravity.CENTER));
        sel = 0;
        paint();
    }

    private void toggleItem(int idx) {
        int bit = 1 << idx;
        if ((mask & bit) != 0) {
            if ((mask & ~bit) != 0) {
                mask = mask & (~bit);
            } else {
                statusView.setText("⚠ 至少需保留 1 个勾选项！");
                paint();
                return;
            }
        } else {
            mask = mask | bit;
        }
        paint();
    }

    private void triggerAction(int actIdx) {
        if (actIdx < presetMasks.length) {
            mask = presetMasks[actIdx] & allMask;
            if (mask == 0) mask = allMask;
            sel = itemLabels.length + presetMasks.length;
            paint();
        } else if (actIdx == presetMasks.length) {
            if (onConfirm != null) onConfirm.onConfirmed(mask);
        } else {
            mask = initialSavedMask;
            if (onCancel != null) onCancel.run();
        }
    }

    private void paint() {
        int selectedCount = 0;
        StringBuilder pickedNames = new StringBuilder();
        for (int i = 0; i < itemLabels.length; i++) {
            boolean checked = (mask & (1 << i)) != 0;
            if (checked) {
                selectedCount++;
                if (pickedNames.length() > 0) pickedNames.append("、");
                pickedNames.append(itemLabels[i]);
            }
            boolean focused = (sel == i);
            itemViews[i].setText((checked ? "☑ " : "☐ ") + itemLabels[i]);
            GradientDrawable d = new GradientDrawable();
            if (focused) {
                d.setColor(Ui.ACCENT);
                d.setStroke(Ui.dp(getContext(), 2), Ui.TEXT);
                itemViews[i].setTextColor(0xFF20160A);
            } else if (checked) {
                d.setColor(0x282EA043);
                d.setStroke(Ui.dp(getContext(), 2), Ui.ACCENT);
                itemViews[i].setTextColor(Ui.ACCENT);
            } else {
                d.setColor(0xFF1E222A);
                d.setStroke(Ui.dp(getContext(), 1), 0x33FFFFFF);
                itemViews[i].setTextColor(Ui.DIM);
            }
            d.setCornerRadius(Ui.dp(getContext(), 12));
            itemViews[i].setBackground(d);
        }

        statusView.setText("当前已选 " + selectedCount + " 项：【" + pickedNames.toString() + "】（按返回键或点「✓ 保存并返回」立即生效）");

        for (int i = 0; i < actionViews.length; i++) {
            int gIdx = itemLabels.length + i;
            boolean focused = (sel == gIdx);
            boolean isConfirm = (i == presetLabels.length);
            GradientDrawable d = new GradientDrawable();
            if (focused) {
                d.setColor(Ui.ACCENT);
                d.setStroke(Ui.dp(getContext(), 2), Ui.TEXT);
                actionViews[i].setTextColor(0xFF20160A);
            } else if (isConfirm) {
                d.setColor(0xFF1F6F45);
                d.setStroke(Ui.dp(getContext(), 2), Ui.ACCENT);
                actionViews[i].setTextColor(Ui.TEXT);
            } else {
                d.setColor(0xFF1E222A);
                d.setStroke(Ui.dp(getContext(), 1), 0x33FFFFFF);
                actionViews[i].setTextColor(Ui.TEXT);
            }
            d.setCornerRadius(Ui.dp(getContext(), 12));
            actionViews[i].setBackground(d);
        }
    }

    public boolean consumeKey(KeyEvent e) {
        if (e.getAction() != KeyEvent.ACTION_DOWN) return true;
        int k = e.getKeyCode();
        int total = itemLabels.length + actionViews.length;

        if (k == KeyEvent.KEYCODE_BACK) {
            // 电视遥控器按返回键时自动保存当前多选结果！
            if (onConfirm != null) onConfirm.onConfirmed(mask);
            else if (onCancel != null) onCancel.run();
            return true;
        }
        int d = PinPadView.digitOf(e);
        if (d >= 1 && d <= itemLabels.length) {
            sel = d - 1;
            toggleItem(sel);
            return true;
        }
        if (k == KeyEvent.KEYCODE_DPAD_LEFT) {
            sel = (sel - 1 + total) % total;
            paint();
            return true;
        }
        if (k == KeyEvent.KEYCODE_DPAD_RIGHT) {
            sel = (sel + 1) % total;
            paint();
            return true;
        }
        if (k == KeyEvent.KEYCODE_DPAD_UP) {
            if (sel >= itemLabels.length) {
                sel = Math.max(0, itemLabels.length - cols);
            } else if (sel >= cols) {
                sel = sel - cols;
            } else {
                sel = itemLabels.length + presetLabels.length;
            }
            paint();
            return true;
        }
        if (k == KeyEvent.KEYCODE_DPAD_DOWN) {
            if (sel < itemLabels.length) {
                if (sel + cols < itemLabels.length) {
                    sel = sel + cols;
                } else {
                    sel = itemLabels.length + presetLabels.length;
                }
            } else {
                sel = 0;
            }
            paint();
            return true;
        }
        if (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER || k == KeyEvent.KEYCODE_NUMPAD_ENTER) {
            if (sel < itemLabels.length) {
                toggleItem(sel);
            } else {
                triggerAction(sel - itemLabels.length);
            }
            return true;
        }
        return true;
    }
}
