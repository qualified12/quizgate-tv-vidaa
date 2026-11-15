/*
 * QuizGate TV · VIDAA Edition
 * Fork of QuizGate TV (https://github.com/mtvtopcom/quizgate-tv), Apache-2.0.
 * Modified from the original QuizGate TV project by qualified12.
 * See NOTICE for the full list of changes.
 */
package app.quizgate.tv;

import android.content.Context;
import android.view.KeyEvent;
import android.widget.FrameLayout;

public class UpgradeView extends FrameLayout {
    public UpgradeView(Context c) {
        super(c);
    }

    public boolean consumeKey(KeyEvent e) {
        return false;
    }
}
