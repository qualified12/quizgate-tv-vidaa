/*
 * QuizGate TV · VIDAA Edition
 * Fork of QuizGate TV (https://github.com/mtvtopcom/quizgate-tv), Apache-2.0.
 * Modified from the original QuizGate TV project by qualified12.
 * See NOTICE for the full list of changes.
 */

package app.quizgate.tv;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 首页与设置页（v1.5.0 全功能防沉迷与防瞎蒙增强版）：
 * - 设置一次即自动生效：只要设置了口令，后台守护服务立即按规则自动运行（开机自启、宵禁、每日上限、护眼休息、防瞎蒙答题）；
 * - 只有进入「家长设置」时才验证一次口令，其余时间随时随地按规则运行，不反复弹窗打扰。
 */
public class MainActivity extends Activity {

    private Prefs prefs;
    private FrameLayout root;
    private ScrollView contentScroll;
    private LinearLayout content;

    private TextView valueBootMode, valueDuration, valueExtend, valueQuizCount, valueWrongPenalty;
    private TextView valueGuessGuard, valueMinRead, valueDailyLimit, valueCurfew, valueEyeCare, valueIdleRelock;
    private TextView valueGrade, valueSubject, valueWrongBook, valueStars, valuePin, valueRecovery, valueGuard, valueVersion;
    private TextView anchorWarn;
    private View firstRow;
    private View settingsFocus;
    private View homeRoot;
    private TextView homePhase, homeTime, homeQuizInfo, homeGuard, homeLan, homeAnchor, homeBtnStart, homeBtnAdult;
    private View homeBar;

    private OptionPanel optionPanel;
    private MultiSelectPanel multiPanel;
    private WrongBookView wrongBookView;
    private PinPadView gate;
    private RecoveryFlow recovery;
    private RecoveryFlow.CodePanel codeOverlay;
    private FrameLayout guardPanel;

    private boolean showingSettings = false;
    private boolean settingsUnlocked = false;
    private String firstPin = "";
    private Anchor.Report anchorReport;
    private final Handler h = new Handler(Looper.getMainLooper());

    private final Runnable uiTicker = new Runnable() {
        @Override public void run() {
            tickUi();
            h.postDelayed(this, 1000L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new Prefs(this);
        anchorReport = Anchor.inspectAndBump(this, prefs);

        root = new FrameLayout(this);
        root.setBackgroundColor(Ui.BG);
        setContentView(root);

        buildContent();
        buildHome();
        showHome();
        ensureGuardRunning();

        if (!prefs.hasPin()) {
            showSettings();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
        h.removeCallbacks(uiTicker);
        h.post(uiTicker);
    }

    @Override
    protected void onPause() {
        super.onPause();
        h.removeCallbacks(uiTicker);
    }

    private void ensureGuardRunning() {
        if (!prefs.safeMode() && prefs.hasPin() && Settings.canDrawOverlays(this)) {
            try {
                String act = (prefs.phase() == Prefs.IDLE && prefs.extendMin() != -1 && !prefs.adultMode())
                        ? GateService.ACTION_START
                        : GateService.ACTION_RESTORE;
                if (!App.gateAlive || GateService.ACTION_START.equals(act)) {
                    Intent i = new Intent(this, GateService.class).setAction(act);
                    if (Build.VERSION.SDK_INT >= 26) startForegroundService(i);
                    else startService(i);
                }
            } catch (Throwable ignore) {}
        }
    }

    // ---------- 首页 ----------

    private void buildHome() {
        LinearLayout home = Ui.col(this);
        home.setPadding(Ui.dp(this, 64), Ui.dp(this, 36), Ui.dp(this, 64), Ui.dp(this, 36));

        LinearLayout header = Ui.row(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo = new ImageView(this);
        logo.setBackground(Ui.logoBg(this));
        header.addView(logo, new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48)));

        LinearLayout titles = Ui.col(this);
        titles.setPadding(Ui.dp(this, 16), 0, 0, 0);
        titles.addView(Ui.title(this, "答题续时 · 全功能防沉迷版", 30f));
        titles.addView(Ui.label(this, "开机自启 · 防瞎蒙冷却 · 每日上限 · 夜间宵禁 · 护眼休息 · 错题积分", 16f, Ui.DIM));
        header.addView(titles);
        home.addView(header);

        homeAnchor = Ui.label(this, "", 16f, Ui.BAD);
        homeAnchor.setVisibility(View.GONE);
        LinearLayout.LayoutParams halp = new LinearLayout.LayoutParams(-1, -2);
        halp.topMargin = Ui.dp(this, 10);
        home.addView(homeAnchor, halp);

        LinearLayout card = Ui.row(this);
        card.setBackground(Ui.box(this, Ui.CARD, 0x22FFFFFF, 1, 18));
        card.setPadding(Ui.dp(this, 28), Ui.dp(this, 20), Ui.dp(this, 28), Ui.dp(this, 20));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-1, -2);
        clp.topMargin = Ui.dp(this, 20);

        homeBar = new View(this);
        homeBar.setBackground(Ui.box(this, Ui.DIM, 0, 0, 4));
        card.addView(homeBar, new LinearLayout.LayoutParams(Ui.dp(this, 8), -1));

        LinearLayout body = Ui.col(this);
        body.setPadding(Ui.dp(this, 22), 0, 0, 0);
        homePhase = Ui.label(this, "规则自动守护中", 20f, Ui.OK);
        homeTime = Ui.label(this, "0 / 90 分钟", 44f, Ui.TEXT);
        homeTime.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        homeQuizInfo = Ui.label(this, "", 17f, Ui.ACCENT);
        homeGuard = Ui.label(this, "", 16f, Ui.DIM);
        homeLan = Ui.label(this, "", 15f, Ui.DIM);

        body.addView(homePhase);
        body.addView(homeTime);
        body.addView(homeQuizInfo);
        body.addView(Ui.spacer(this, 6));
        body.addView(homeGuard);
        body.addView(homeLan);
        card.addView(body, new LinearLayout.LayoutParams(-1, -2));
        home.addView(card, clp);

        LinearLayout btns = Ui.row(this);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, -2);
        blp.topMargin = Ui.dp(this, 24);

        homeBtnStart = Ui.homeKey(this, "立即锁屏答题测试", true);
        homeBtnStart.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { onForceLockClicked(); }
        });
        btns.addView(homeBtnStart, new LinearLayout.LayoutParams(0, -2, 1f));

        homeBtnAdult = Ui.homeKey(this, "大人模式：一键关闭管控", false);
        homeBtnAdult.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { onToggleAdultMode(); }
        });
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, -2, 1f);
        tlp.leftMargin = Ui.dp(this, 14);
        btns.addView(homeBtnAdult, tlp);

        TextView btnSet = Ui.homeKey(this, "家长设置（需口令）", false);
        btnSet.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showSettings(); }
        });
        LinearLayout.LayoutParams setLp = new LinearLayout.LayoutParams(0, -2, 1f);
        setLp.leftMargin = Ui.dp(this, 14);
        btns.addView(btnSet, setLp);

        home.addView(btns, blp);
        homeRoot = home;
        root.addView(homeRoot, new FrameLayout.LayoutParams(-1, -1));
    }

    // ---------- 设置页 ----------

    private void buildContent() {
        contentScroll = new ScrollView(this);
        contentScroll.setFillViewport(true);
        content = Ui.col(this);
        content.setPadding(Ui.dp(this, 64), Ui.dp(this, 28), Ui.dp(this, 64), Ui.dp(this, 36));

        content.addView(Ui.title(this, "家长设置（配置一次，开机即自动生效）", 28f));

        anchorWarn = Ui.label(this, "", 16f, Ui.BAD);
        anchorWarn.setVisibility(View.GONE);
        LinearLayout.LayoutParams awlp = new LinearLayout.LayoutParams(-1, -2);
        awlp.topMargin = Ui.dp(this, 8);
        content.addView(anchorWarn, awlp);

        valueBootMode = addRow("开机自动启动模式", new Runnable() {
            @Override public void run() { openBootModePanel(); }
        });
        firstRow = (View) valueBootMode.getParent();

        valueDuration = addRow("每次观看时长（到点锁屏答题）", new Runnable() {
            @Override public void run() { openDurationPanel(); }
        });

        valueExtend = addRow("答对后下一轮观看时长", new Runnable() {
            @Override public void run() { openExtendPanel(); }
        });

        valueQuizCount = addRow("每次锁屏需答对题数", new Runnable() {
            @Override public void run() { openQuizCountPanel(); }
        });

        valueWrongPenalty = addRow("答错额外加罚题数", new Runnable() {
            @Override public void run() { openWrongPenaltyPanel(); }
        });

        valueGuessGuard = addRow("防瞎蒙：连错冷却锁定", new Runnable() {
            @Override public void run() { openGuessGuardPanel(); }
        });

        valueMinRead = addRow("防瞎蒙：每题最少读题时间", new Runnable() {
            @Override public void run() { openMinReadPanel(); }
        });

        valueDailyLimit = addRow("每日观看总时长上限", new Runnable() {
            @Override public void run() { openDailyLimitPanel(); }
        });

        valueCurfew = addRow("夜间宵禁保护时段", new Runnable() {
            @Override public void run() { openCurfewPanel(); }
        });

        valueEyeCare = addRow("护眼强制远眺休息", new Runnable() {
            @Override public void run() { openEyeCarePanel(); }
        });

        valueIdleRelock = addRow("息屏/待机超时重锁答题", new Runnable() {
            @Override public void run() { openIdleRelockPanel(); }
        });

        valueGrade = addRow("题目年级（可多选）", new Runnable() {
            @Override public void run() { openGradeMultiPanel(); }
        });

        valueSubject = addRow("题目学科（可随机）", new Runnable() {
            @Override public void run() { openSubjectMultiPanel(); }
        });

        valueWrongBook = addRow("错题记录本（查看/清空）", new Runnable() {
            @Override public void run() { openWrongBook(); }
        });

        valueStars = addRow("星星积分银行（兑换奖励）", new Runnable() {
            @Override public void run() { openStarsPanel(); }
        });

        valuePin = addRow("修改家长口令（6位）", new Runnable() {
            @Override public void run() { promptSetPin(); }
        });

        valueRecovery = addRow("恢复码", new Runnable() {
            @Override public void run() { onRecoveryRow(); }
        });

        valueGuard = addRow("守护状态与授权自检", new Runnable() {
            @Override public void run() { showGuardPanel(); }
        });

        valueVersion = addRow("当前版本", new Runnable() {
            @Override public void run() {
                toast("答题续时 " + BuildCfg.VERSION_NAME + " · 1500+ 题库 + 局域网控制台");
            }
        });

        TextView backBottom = Ui.homeKey(this, "✓ 保存并返回首页", true);
        backBottom.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showHome(); }
        });
        LinearLayout.LayoutParams bblp = new LinearLayout.LayoutParams(-1, -2);
        bblp.topMargin = Ui.dp(this, 18);
        content.addView(backBottom, bblp);

        contentScroll.addView(content, new FrameLayout.LayoutParams(-1, -2));
        contentScroll.setVisibility(View.GONE);
        root.addView(contentScroll, new FrameLayout.LayoutParams(-1, -1));
    }

    private TextView addRow(String label, final Runnable onClick) {
        LinearLayout r = Ui.row(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setFocusable(true);
        r.setClickable(true);
        r.setBackground(Ui.keyBg(this));
        r.setPadding(Ui.dp(this, 24), Ui.dp(this, 13), Ui.dp(this, 24), Ui.dp(this, 13));

        TextView k = Ui.label(this, label, 19f, Ui.TEXT);
        k.setTypeface(Typeface.DEFAULT_BOLD);
        r.addView(k, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView v = Ui.label(this, "", 19f, Ui.ACCENT);
        r.addView(v, new LinearLayout.LayoutParams(-2, -2));

        r.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                settingsFocus = view;
                if (onClick != null) onClick.run();
            }
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Ui.dp(this, 9);
        content.addView(r, lp);
        return v;
    }

    // ---------- 各项配置面板 ----------

    private void openBootModePanel() {
        String[] labels = {"开机直接锁屏答题", "开机自动开始计时", "关闭（需手动点开始）"};
        int[] vals = {Prefs.BOOT_LOCK, Prefs.BOOT_TIMER, Prefs.BOOT_OFF};
        mountOption(new OptionPanel(this, "电视开机自动启动模式", labels, vals,
                prefs.bootMode(), "", 0, 2,
                new OptionPanel.OnPick() {
                    @Override public void onValue(int v) {
                        prefs.setBootMode(v);
                        if (v == Prefs.BOOT_TIMER && !prefs.adultMode()) {
                            Intent si = new Intent(MainActivity.this, GateService.class).setAction(GateService.ACTION_START);
                            if (Build.VERSION.SDK_INT >= 26) startForegroundService(si);
                            else startService(si);
                        }
                        hideOptionPanel();
                        refresh();
                        toast("已切换：" + prefs.bootModeName());
                    }
                }, new Runnable() { @Override public void run() { hideOptionPanel(); } }));
    }

    private void openExtendPanel() {
        String[] labels = {"同上(循环答题)", "1分后再答", "5分后再答", "10分后再答", "15分后再答", "30分后再答", "只答1次不再弹"};
        int[] vals = {0, 1, 5, 10, 15, 30, -1};
        mountOption(new OptionPanel(this, "答对后看几分钟再次锁屏答题", labels, vals,
                prefs.extendMin(), "", -1, 60,
                new OptionPanel.OnPick() {
                    @Override public void onValue(int v) {
                        prefs.setExtendMin(v);
                        hideOptionPanel();
                        refresh();
                        toast("已设为：" + prefs.extendSummary());
                    }
                }, new Runnable() { @Override public void run() { hideOptionPanel(); } }));
    }

    private void openDurationPanel() {
        String[] labels = {"1", "5", "15", "30", "45", "60"};
        int[] vals = {1, 5, 15, 30, 45, 60};
        mountOption(new OptionPanel(this, "单次观看计时时长（分钟）", labels, vals,
                prefs.durationMin(), "分钟", 1, 240,
                new OptionPanel.OnPick() {
                    @Override public void onValue(int v) {
                        prefs.setDurationMin(v);
                        if (prefs.extendMin() == -1) prefs.setExtendMin(0);
                        prefs.setAdultMode(false);
                        Intent si = new Intent(MainActivity.this, GateService.class).setAction(GateService.ACTION_START);
                        if (Build.VERSION.SDK_INT >= 26) startForegroundService(si);
                        else startService(si);
                        hideOptionPanel();
                        refresh();
                        toast("已设为 " + v + " 分钟并立即开始倒计时！");
                    }
                }, new Runnable() { @Override public void run() { hideOptionPanel(); } }));
    }

    private void openQuizCountPanel() {
        String[] labels = {"1", "2", "3", "5", "8", "10"};
        int[] vals = {1, 2, 3, 5, 8, 10};
        mountOption(new OptionPanel(this, "每次锁屏需答对题数", labels, vals,
                prefs.quizCount(), "题", 1, 20,
                new OptionPanel.OnPick() {
                    @Override public void onValue(int v) {
                        prefs.setQuizCount(v);
                        hideOptionPanel();
                        refresh();
                    }
                }, new Runnable() { @Override public void run() { hideOptionPanel(); } }));
    }

    private void openWrongPenaltyPanel() {
        String[] labels = {"不加罚", "+1 题", "+2 题", "+3 题"};
        int[] vals = {0, 1, 2, 3};
        mountOption(new OptionPanel(this, "每答错 1 题额外加罚题数", labels, vals,
                prefs.wrongPenalty(), "", 0, 5,
                new OptionPanel.OnPick() {
                    @Override public void onValue(int v) {
                        prefs.setWrongPenalty(v);
                        hideOptionPanel();
                        refresh();
                    }
                }, new Runnable() { @Override public void run() { hideOptionPanel(); } }));
    }

    private void openGuessGuardPanel() {
        String[] labels = {"关闭", "连错2题锁30秒", "连错2题锁60秒", "连错3题锁45秒", "连错1题锁20秒"};
        int[] vals = {0, 230, 260, 345, 120};
        int cur = prefs.guessTrigger() == 0 ? 0 : (prefs.guessTrigger() * 100 + prefs.guessCooldownSec());
        mountOption(new OptionPanel(this, "防瞎蒙：连续答错冷却锁定", labels, vals,
                cur, "", 0, 599,
                new OptionPanel.OnPick() {
                    @Override public void onValue(int v) {
                        if (v == 0) {
                            prefs.setGuessTrigger(0);
                        } else {
                            prefs.setGuessTrigger(v / 100);
                            prefs.setGuessCooldownSec(v % 100);
                        }
                        hideOptionPanel();
                        refresh();
                    }
                }, new Runnable() { @Override public void run() { hideOptionPanel(); } }));
    }

    private void openMinReadPanel() {
        String[] labels = {"0秒(关闭)", "2秒", "3秒(推荐)", "5秒"};
        int[] vals = {0, 2, 3, 5};
        mountOption(new OptionPanel(this, "防瞎蒙：每题最少读题保护时间", labels, vals,
                prefs.minReadSec(), "", 0, 10,
                new OptionPanel.OnPick() {
                    @Override public void onValue(int v) {
                        prefs.setMinReadSec(v);
                        hideOptionPanel();
                        refresh();
                    }
                }, new Runnable() { @Override public void run() { hideOptionPanel(); } }));
    }

    private void openDailyLimitPanel() {
        String[] labels = {"不限", "30分钟", "60分钟", "90分钟", "120分钟", "180分钟"};
        int[] vals = {0, 30, 60, 90, 120, 180};
        mountOption(new OptionPanel(this, "每日观看总时长上限（到点硬锁）", labels, vals,
                prefs.dailyLimitMin(), "", 0, 480,
                new OptionPanel.OnPick() {
                    @Override public void onValue(int v) {
                        prefs.setDailyLimitMin(v);
                        hideOptionPanel();
                        refresh();
                    }
                }, new Runnable() { @Override public void run() { hideOptionPanel(); } }));
    }

    private void openCurfewPanel() {
        String[] labels = {"关闭", "21:00-07:00", "21:30-07:00", "22:00-07:00", "20:30-07:00"};
        int[] vals = {0, 1, 2, 3, 4};
        mountOption(new OptionPanel(this, "夜间宵禁保护时段", labels, vals,
                prefs.curfewPreset(), "", 0, 4,
                new OptionPanel.OnPick() {
                    @Override public void onValue(int v) {
                        prefs.setCurfewPreset(v);
                        hideOptionPanel();
                        refresh();
                    }
                }, new Runnable() { @Override public void run() { hideOptionPanel(); } }));
    }

    private void openEyeCarePanel() {
        String[] labels = {"关闭", "每30分休1分", "每45分休2分", "每20分休1分"};
        int[] vals = {0, 3060, 45120, 2060};
        int cur = prefs.eyeIntervalMin() == 0 ? 0 : (prefs.eyeIntervalMin() * 1000 + prefs.eyeRestSec());
        mountOption(new OptionPanel(this, "护眼强制远眺休息", labels, vals,
                cur, "", 0, 999999,
                new OptionPanel.OnPick() {
                    @Override public void onValue(int v) {
                        if (v == 0) {
                            prefs.setEyeIntervalMin(0);
                        } else if (v >= 10000) {
                            prefs.setEyeIntervalMin(v / 1000);
                            prefs.setEyeRestSec(v % 1000);
                        } else {
                            prefs.setEyeIntervalMin(v / 100);
                            prefs.setEyeRestSec(v % 100);
                        }
                        hideOptionPanel();
                        refresh();
                    }
                }, new Runnable() { @Override public void run() { hideOptionPanel(); } }));
    }

    private void openIdleRelockPanel() {
        String[] labels = {"关闭", "5 分钟", "15 分钟(推荐)", "30 分钟", "60 分钟"};
        int[] vals = {0, 5, 15, 30, 60};
        mountOption(new OptionPanel(this, "息屏/待机超过多久后再开屏需重新答题", labels, vals,
                prefs.idleRelockMin(), "", 0, 120,
                new OptionPanel.OnPick() {
                    @Override public void onValue(int v) {
                        prefs.setIdleRelockMin(v);
                        hideOptionPanel();
                        refresh();
                    }
                }, new Runnable() { @Override public void run() { hideOptionPanel(); } }));
    }

    private void openGradeMultiPanel() {
        String[] gradeLabels = {
            "1 年级", "2 年级", "3 年级",
            "4 年级", "5 年级", "6 年级",
            "7 年级(初一)", "8 年级(初二)", "9 年级(初三)"
        };
        String[] presetLabels = {"小学全选(1-6)", "初中全选(7-9)", "全年级(1-9)"};
        int[] presetMasks = {Prefs.PRIMARY_GRADES_MASK, Prefs.JUNIOR_GRADES_MASK, Prefs.ALL_GRADES_MASK};

        mountMulti(new MultiSelectPanel(
                this,
                "选择出题年级（可多选联合展示）",
                "方向键移动 · OK 勾选/取消 · 选好后点底部「✓ 确认保存」",
                gradeLabels,
                prefs.gradeMask(),
                presetLabels,
                presetMasks,
                3,
                new MultiSelectPanel.OnConfirm() {
                    @Override public void onConfirmed(int bitmask) {
                        prefs.setGradeMask(bitmask);
                        hideMultiPanel();
                        refresh();
                    }
                },
                new Runnable() { @Override public void run() { hideMultiPanel(); } }
        ));
    }

    private void openSubjectMultiPanel() {
        String[] subjLabels = {"语文（诗词/成语/字词）", "数学（口算/应用/几何）", "英语（词汇/句型/语法）", "科学百科（自然/物理/常识）"};
        String[] presetLabels = {"★ 全部学科随机", "语数英三科随机"};
        int[] presetMasks = {Prefs.ALL_SUBJECTS_MASK, 0x07};

        mountMulti(new MultiSelectPanel(
                this,
                "选择出题学科（支持多科随机）",
                "勾选多个学科即在所选学科间随机抽题",
                subjLabels,
                prefs.subjectMask(),
                presetLabels,
                presetMasks,
                2,
                new MultiSelectPanel.OnConfirm() {
                    @Override public void onConfirmed(int bitmask) {
                        prefs.setSubjectMask(bitmask);
                        hideMultiPanel();
                        refresh();
                    }
                },
                new Runnable() { @Override public void run() { hideMultiPanel(); } }
        ));
    }

    private void openWrongBook() {
        hideWrongBook();
        wrongBookView = new WrongBookView(this, prefs, new Runnable() {
            @Override public void run() {
                hideWrongBook();
                refresh();
            }
        });
        root.addView(wrongBookView, new FrameLayout.LayoutParams(-1, -1));
    }

    private void hideWrongBook() {
        if (wrongBookView != null) {
            if (wrongBookView.getParent() != null) root.removeView(wrongBookView);
            wrongBookView = null;
        }
        if (settingsFocus != null) settingsFocus.requestFocus();
    }

    private void openStarsPanel() {
        String[] labels = {"兑换10颗星", "兑换20颗星", "全部清零", "奖励+5颗星"};
        int[] vals = {-10, -20, -9999, 5};
        mountOption(new OptionPanel(this, "星星积分银行（当前：" + prefs.stars() + " ★）", labels, vals,
                0, "", -9999, 9999,
                new OptionPanel.OnPick() {
                    @Override public void onValue(int v) {
                        if (v == -9999) prefs.setStars(0);
                        else prefs.addStar(v);
                        hideOptionPanel();
                        refresh();
                        toast("当前剩余积分：" + prefs.stars() + " ★");
                    }
                }, new Runnable() { @Override public void run() { hideOptionPanel(); } }));
    }

    private void mountOption(OptionPanel p) {
        hideOptionPanel();
        optionPanel = p;
        root.addView(optionPanel, new FrameLayout.LayoutParams(-1, -1));
    }

    private void hideOptionPanel() {
        if (optionPanel != null) {
            if (optionPanel.getParent() != null) root.removeView(optionPanel);
            optionPanel = null;
        }
        if (settingsFocus != null) settingsFocus.requestFocus();
    }

    private void mountMulti(MultiSelectPanel p) {
        hideMultiPanel();
        multiPanel = p;
        root.addView(multiPanel, new FrameLayout.LayoutParams(-1, -1));
    }

    private void hideMultiPanel() {
        if (multiPanel != null) {
            if (multiPanel.getParent() != null) root.removeView(multiPanel);
            multiPanel = null;
        }
        if (settingsFocus != null) settingsFocus.requestFocus();
    }

    // ---------- 家长口令与恢复码 ----------

    private void promptSetPin() {
        firstPin = "";
        showPinGate("设置 6 位家长口令", new PinPadView.Listener() {
            @Override public void onPinSubmit(String pin) {
                firstPin = pin;
                showPinGate("再输一次确认口令", new PinPadView.Listener() {
                    @Override public void onPinSubmit(String pin2) {
                        if (!firstPin.equals(pin2)) {
                            gate.fail("两次输入不一致，请重试");
                            return;
                        }
                        prefs.setPin(pin2);
                        hidePinOverlay();
                        settingsUnlocked = true;
                        ensureGuardRunning();
                        String rc = prefs.newRecoveryCode();
                        showRecoveryCodeOverlay(rc);
                        refresh();
                    }
                    @Override public void onPinCancel() {
                        if (prefs.hasPin()) hidePinOverlay();
                    }
                });
            }
            @Override public void onPinCancel() {
                if (prefs.hasPin()) hidePinOverlay();
                else toast("首次使用请先设置 6 位家长口令");
            }
        });
    }

    private void showRecoveryCodeOverlay(String code) {
        hideRecoveryCode();
        codeOverlay = new RecoveryFlow.CodePanel(this, code, new Runnable() {
            @Override public void run() {
                prefs.markRecoverySaved();
                hideRecoveryCode();
                refresh();
            }
        });
        codeOverlay.setSelfPaint(true);
        root.addView(codeOverlay, new FrameLayout.LayoutParams(-1, -1));
    }

    private void hideRecoveryCode() {
        if (codeOverlay != null) {
            if (codeOverlay.getParent() != null) root.removeView(codeOverlay);
            codeOverlay = null;
            if (showingSettings && firstRow != null) firstRow.requestFocus();
            else if (!showingSettings && homeBtnStart != null) homeBtnStart.requestFocus();
        }
    }

    private void onRecoveryRow() {
        String rc = prefs.newRecoveryCode();
        showRecoveryCodeOverlay(rc);
        refresh();
    }

    private void showGuardPanel() {
        hideGuardPanel();
        guardPanel = new FrameLayout(this);
        guardPanel.setBackgroundColor(0xE6000000);
        LinearLayout box = Ui.col(this);
        box.setBackground(Ui.panelBg(this));
        box.setPadding(Ui.dp(this, 40), Ui.dp(this, 30), Ui.dp(this, 40), Ui.dp(this, 30));

        box.addView(Ui.title(this, "守护状态与授权自检", 26f));
        boolean ov = Settings.canDrawOverlays(this);
        boolean nl = hasNotifListener();
        boolean wr = Anchor.canWrite(this);

        box.addView(Ui.spacer(this, 12));
        box.addView(Ui.label(this, (ov ? "✓ " : "✗ ") + "悬浮窗权限 (SYSTEM_ALERT_WINDOW)：" + (ov ? "已开启" : "未开启"), 18f, ov ? Ui.OK : Ui.BAD));
        box.addView(Ui.spacer(this, 8));
        box.addView(Ui.label(this, (nl ? "✓ " : "✗ ") + "通知监听保活 (NotifProbe)：" + (nl ? "已开启" : "未开启"), 18f, nl ? Ui.OK : Ui.BAD));
        box.addView(Ui.spacer(this, 8));
        box.addView(Ui.label(this, (wr ? "✓ " : "○ ") + "卸载留痕锚点 (WRITE_SECURE_SETTINGS)：" + (wr ? "已启用" : "可选未开"), 18f, wr ? Ui.OK : Ui.DIM));
        box.addView(Ui.spacer(this, 8));
        box.addView(Ui.label(this, "✓ 手机局域网控制台：http://" + LanServer.localIp() + ":" + LanServer.PORT, 18f, Ui.ACCENT));

        if (prefs.safeMode()) {
            box.addView(Ui.spacer(this, 10));
            box.addView(Ui.label(this, "⚠ 已自动清除安全熔断状态", 16f, Ui.BAD));
            prefs.clearSafeMode();
        }

        TextView closeBtn = Ui.homeKey(this, "返回设置（按 BACK 键）", true);
        closeBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { hideGuardPanel(); }
        });
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-2, -2);
        clp.topMargin = Ui.dp(this, 22);
        box.addView(closeBtn, clp);

        guardPanel.addView(box, new FrameLayout.LayoutParams(Ui.dp(this, 740), -2, Gravity.CENTER));
        root.addView(guardPanel, new FrameLayout.LayoutParams(-1, -1));
        closeBtn.requestFocus();
    }

    private void hideGuardPanel() {
        if (guardPanel != null) {
            if (guardPanel.getParent() != null) root.removeView(guardPanel);
            guardPanel = null;
        }
        if (settingsFocus != null) settingsFocus.requestFocus();
    }

    private boolean hasNotifListener() {
        try {
            String flat = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
            if (flat == null) return false;
            String me = new ComponentName(this, NotifProbe.class).flattenToString();
            return flat.contains(me) || flat.contains(getPackageName() + "/");
        } catch (Throwable t) {
            return false;
        }
    }

    // ---------- 页面切换 ----------

    private void showHome() {
        showingSettings = false;
        settingsUnlocked = false;
        hidePinOverlay();
        contentScroll.setVisibility(View.GONE);
        homeRoot.setVisibility(View.VISIBLE);
        ensureGuardRunning();
        refresh();
        if (homeBtnStart != null) homeBtnStart.requestFocus();
    }

    private void showSettings() {
        showingSettings = true;
        homeRoot.setVisibility(View.GONE);
        contentScroll.setVisibility(View.VISIBLE);
        refresh();
        if (!prefs.hasPin()) {
            promptSetPin();
            return;
        }
        if (!settingsUnlocked) {
            showPinGate("输入 6 位家长口令进入设置", new PinPadView.Listener() {
                @Override public void onPinSubmit(String s) {
                    long r = prefs.verifyPin(s);
                    if (r == 0) {
                        settingsUnlocked = true;
                        hidePinOverlay();
                        if (firstRow != null) firstRow.requestFocus();
                    } else if (r > 0) {
                        gate.fail("请 " + (r / 1000) + " 秒后再试");
                    } else {
                        gate.fail("口令不正确");
                    }
                }
                @Override public void onPinCancel() {
                    hidePinOverlay();
                    showHome();
                }
            });
            gate.setFooter("忘记口令？用恢复码重置", new Runnable() {
                @Override public void run() {
                    hidePinOverlay();
                    openRecoveryInActivity();
                }
            });
        } else if (firstRow != null) {
            firstRow.requestFocus();
        }
    }

    private void openRecoveryInActivity() {
        if (recovery != null) return;
        recovery = new RecoveryFlow(this, prefs, new RecoveryFlow.Done() {
            @Override public void onDone(boolean ok) {
                if (recovery != null && recovery.getParent() != null) root.removeView(recovery);
                recovery = null;
                if (ok) { settingsUnlocked = true; refresh(); }
                else showHome();
            }
        });
        recovery.setSelfPaint(true);
        root.addView(recovery, new FrameLayout.LayoutParams(-1, -1));
    }

    private void showPinGate(String title, PinPadView.Listener l) {
        hidePinOverlay();
        gate = new PinPadView(this, title, l);
        gate.setSelfPaint(true);
        root.addView(gate, new FrameLayout.LayoutParams(-1, -1));
        gate.focusFirst();
    }

    private void hidePinOverlay() {
        if (gate != null) {
            if (gate.getParent() != null) root.removeView(gate);
            gate = null;
        }
    }

    private void onForceLockClicked() {
        if (!Settings.canDrawOverlays(this)) {
            toast("缺少悬浮窗权限，请先通过 adb 授予 SYSTEM_ALERT_WINDOW");
            return;
        }
        Intent i = new Intent(this, GateService.class).setAction(GateService.ACTION_FORCE_LOCK);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i);
        else startService(i);
        finish();
    }

    private void onToggleAdultMode() {
        if (prefs.adultMode()) {
            Intent i = new Intent(this, GateService.class).setAction(GateService.ACTION_ADULT_OFF);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i);
            else startService(i);
            prefs.setAdultMode(false);
            toast("已恢复儿童规则管控");
            refresh();
            return;
        }
        showPinGate("输入家长口令开启「大人模式」（下次开机自动恢复）", new PinPadView.Listener() {
            @Override public void onPinSubmit(String s) {
                long r = prefs.verifyPin(s);
                if (r == 0) {
                    hidePinOverlay();
                    prefs.setAdultMode(true);
                    Intent i = new Intent(MainActivity.this, GateService.class).setAction(GateService.ACTION_ADULT_ON);
                    if (Build.VERSION.SDK_INT >= 26) startForegroundService(i);
                    else startService(i);
                    toast("大人模式已开启！本次自由看电视，下次开机自动恢复儿童管控");
                    refresh();
                    finish();
                } else if (r > 0) {
                    gate.fail("请 " + (r / 1000) + " 秒后再试");
                } else {
                    gate.fail("口令不正确");
                }
            }
            @Override public void onPinCancel() { hidePinOverlay(); }
        });
    }

    private void refresh() {
        if (valueBootMode == null) return;
        valueBootMode.setText(prefs.bootModeName());
        valueExtend.setText(prefs.extendSummary());
        valueDuration.setText(prefs.durationMin() + " 分钟");
        valueQuizCount.setText(prefs.quizCount() + " 题");
        valueWrongPenalty.setText(prefs.wrongPenalty() == 0 ? "不加罚" : ("每错1题 +" + prefs.wrongPenalty() + " 题"));
        valueGuessGuard.setText(prefs.guessTrigger() == 0 ? "已关闭" : ("连错" + prefs.guessTrigger() + "题锁" + prefs.guessCooldownSec() + "秒"));
        valueMinRead.setText(prefs.minReadSec() == 0 ? "不限制" : ("最少读题 " + prefs.minReadSec() + " 秒"));
        valueDailyLimit.setText(prefs.dailyLimitMin() == 0 ? "不限制" : (prefs.dailyLimitMin() + " 分钟/天"));
        valueCurfew.setText(prefs.curfewName());
        valueEyeCare.setText(prefs.eyeIntervalMin() == 0 ? "已关闭" : ("每" + prefs.eyeIntervalMin() + "分休" + prefs.eyeRestSec() + "秒"));
        valueIdleRelock.setText(prefs.idleRelockMin() == 0 ? "已关闭" : ("待机超 " + prefs.idleRelockMin() + " 分钟重锁"));
        valueGrade.setText(prefs.gradesSummary());
        valueSubject.setText(prefs.subjectsSummary());
        valueWrongBook.setText("共 " + prefs.wrongCount() + " 道错题 ›");
        valueStars.setText("★ " + prefs.stars() + " 颗星 ›");
        valuePin.setText(prefs.hasPin() ? "已设置" : "未设置");
        valueRecovery.setText(!prefs.hasRecoveryCode() ? "未生成" : (prefs.recoverySaved() ? "已保存（点此重生成）" : "待拍照"));
        boolean authOk = Settings.canDrawOverlays(this) && hasNotifListener();
        valueGuard.setText(authOk ? "正常 · 授权齐全 ›" : "查看授权 ›");
        valueVersion.setText(BuildCfg.VERSION_NAME);

        if (anchorReport != null && anchorReport.warning != null && !anchorReport.warning.isEmpty()) {
            anchorWarn.setText(anchorReport.warning);
            anchorWarn.setVisibility(View.VISIBLE);
            if (homeAnchor != null) {
                homeAnchor.setText(anchorReport.warning);
                homeAnchor.setVisibility(View.VISIBLE);
            }
        }
        tickUi();
    }

    private void tickUi() {
        if (homePhase == null) return;
        int usedMin = (int) (prefs.dailyUsedMs() / 60_000L);
        int lim = prefs.dailyLimitMin();
        String limStr = lim == 0 ? "不限" : ((lim + prefs.dailyBonusMin()) + " 分钟");
        homeTime.setText("今日已看 " + usedMin + " / " + limStr);

        if (homeBtnAdult != null) {
            homeBtnAdult.setText(prefs.adultMode() ? "✓ 大人模式中（点此恢复管控）" : "大人模式：一键关闭管控");
        }
        int p = prefs.phase();
        if (prefs.adultMode()) {
            homePhase.setText("● 大人模式已开启（本次免控 · 下次开机自动恢复）");
            homePhase.setTextColor(Ui.ACCENT);
            homeBar.setBackground(Ui.box(this, Ui.ACCENT, 0, 0, 4));
        } else if (p == Prefs.LOCKED) {
            homePhase.setText("● 已锁屏拦截 · 等待答题或家长口令");
            homePhase.setTextColor(Ui.BAD);
            homeBar.setBackground(Ui.box(this, Ui.BAD, 0, 0, 4));
        } else if (p == Prefs.RUNNING) {
            homePhase.setText("● 倒计时守护中 · 剩余 " + Core.fmt(prefs.remainingMs()));
            homePhase.setTextColor(Ui.ACCENT);
            homeBar.setBackground(Ui.box(this, Ui.ACCENT, 0, 0, 4));
        } else {
            homePhase.setText("● 全规则自动监控中（开机自启已就绪）");
            homePhase.setTextColor(Ui.OK);
            homeBar.setBackground(Ui.box(this, Ui.OK, 0, 0, 4));
        }

        if (homeQuizInfo != null) {
            homeQuizInfo.setText(prefs.bootModeName() + " · 每次 " + prefs.quizCount() + " 题 · 宵禁 " + prefs.curfewName() + " · ★ " + prefs.stars() + " 颗星");
        }
        if (homeGuard != null) {
            boolean ov = Settings.canDrawOverlays(this);
            boolean nl = hasNotifListener();
            homeGuard.setText("题库：" + prefs.gradesSummary() + " · " + prefs.subjectsSummary()
                    + " · 授权：" + (ov && nl ? "齐全 ✓" : "请检查"));
        }
        if (homeLan != null) {
            homeLan.setText("手机同 Wi-Fi 浏览器打开：http://" + LanServer.localIp() + ":" + LanServer.PORT + " 可远程查看错题与锁屏");
        }
    }

    private View topOverlay() {
        if (codeOverlay != null && codeOverlay.getParent() != null) return codeOverlay;
        if (recovery != null && recovery.getParent() != null) return recovery;
        if (wrongBookView != null && wrongBookView.getParent() != null) return wrongBookView;
        if (multiPanel != null && multiPanel.getParent() != null) return multiPanel;
        if (optionPanel != null && optionPanel.getParent() != null) return optionPanel;
        if (guardPanel != null && guardPanel.getParent() != null) return guardPanel;
        if (gate != null && gate.getParent() != null) return gate;
        return null;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        View top = topOverlay();
        if (top != null && top == codeOverlay) return codeOverlay.consumeKey(e);
        if (top != null && top == recovery) return recovery.consumeKey(e);
        if (top != null && top == wrongBookView) return wrongBookView.consumeKey(e);
        if (top != null && top == multiPanel) return multiPanel.consumeKey(e);
        if (top != null && top == optionPanel) return optionPanel.consumeKey(e);
        if (top != null && top == guardPanel) {
            if (e.getAction() == KeyEvent.ACTION_DOWN && e.getKeyCode() == KeyEvent.KEYCODE_BACK) {
                hideGuardPanel();
                return true;
            }
            return super.dispatchKeyEvent(e);
        }
        if (top != null && top == gate) {
            if (e.getAction() == KeyEvent.ACTION_DOWN) {
                if (e.getKeyCode() == KeyEvent.KEYCODE_BACK) {
                    if (!prefs.hasPin()) { toast("首次使用请先设置 6 位家长口令"); return true; }
                    hidePinOverlay();
                    if (showingSettings && !settingsUnlocked) showHome();
                    return true;
                }
                if (gate.consumeNav(e)) return true;
                int d = PinPadView.digitOf(e);
                if (d >= 0) { gate.append((char) ('0' + d)); return true; }
                if (e.getKeyCode() == KeyEvent.KEYCODE_DEL) { gate.backspace(); return true; }
            }
            return true;
        }
        if (showingSettings && e.getAction() == KeyEvent.ACTION_DOWN && e.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            showHome();
            return true;
        }
        return super.dispatchKeyEvent(e);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}
