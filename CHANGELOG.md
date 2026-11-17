# 变更日志 · QuizGate TV · VIDAA Edition

本 fork 的上游基准：[mtvtopcom/quizgate-tv v1.3.0](https://github.com/mtvtopcom/quizgate-tv/releases/tag/v1.3.0)

---

## v1.6.9-VIDAA (2026-11-17)

**真机验证：海信 VIDAA TV / MT9653 / Android 11 (API 30) / OS 02.02.70.01**

### 新增

- **答题进度持久化**（`Prefs.java` +1,369 字节）：
  新增 `activeQuizTarget` / `activeQuizCorrect` / `activeQuizWrongStreak` /
  `activeQuizCooldownUntilWall` 四个字段及 `clearActiveQuizProgress()`。
  进程被杀、电视重启或系统冻结后重新拉起遮罩时，**已答对题数与连错冷却时间全部保留**，
  不会出现「答到第 3 题突然从 0 重来」或「冷却中被重置可以继续瞎按」的问题。
  冷却结束时间用墙钟（wall clock）记录，跨重启依然准确。
- **焦点锚点增强**（`Anchor.java` +968 字节）：守护存活锚点写入的信息更完整，
  便于排查失联原因。
- **遮罩层增强**（`MaskView.java` +1,139 字节）：配合进度持久化，重挂遮罩时恢复
  当前题目、已答对数与冷却剩余时间。
- **局域网控制台增强**（`LanServer.java` +647 字节）
- **`GateService` / `MainActivity` 适配**：接入新的进度字段与设置项

### 已知问题

- `LanServer.localIp()` **仍未排除 `p2p0`**（Wi-Fi Direct 虚拟网卡），
  部分机型首页会显示 `192.168.75.x` 这类手机连不上的地址。
  请手动使用电视 `wlan0` 的实际 IP 访问 `:8787`。
- 悬浮窗授权在部分固件卸载重装后会残留为 `ignore`，需重新执行
  `adb shell appops set app.quizgate.tv SYSTEM_ALERT_WINDOW allow`

### 类数

132 个类（与 v1.6.7 相同，本版只改逻辑未加类）

---

## v1.6.7-VIDAA (2026-11-16)

**真机验证：海信 VIDAA TV / MT9653 / Android 11 (API 30) / OS 02.02.70.01**

### 新增

- **错题精讲覆盖层**（`MaskView.showExplainOverlay()`）：答错后不再只给一行文字，
  而是弹出全屏解析面板——题干、错选项与正确项左右对照、知识点讲解、
  需按 OK 确认「我已看懂本题解析」才进入下一题，把「答错」变成一次学习闭环
- **每题角标**：遮罩顶部实时显示 `本题：【数学·5年级】（抽题范围：3,5 年级…）`，
  让孩子和家长都清楚当前题源
- **动态题带解析**（`Core.java` +9,000 字节）：动态生成的算术/方程题现在自带
  分步讲解文本（口算过程、周长面积公式代入、方程移项步骤），不再只有静态题库有解析
- **题库元数据扩展**（`QuizBank.java` +3,885 字节）：`Q` 结构新增 `grade` /
  `subjectKey` 字段，抽题时按真实年级与学科标注角标

### 修复

- **开机恢复计时起点漂移**（`BootReceiver.java` / `NotifProbe.java`）：
  开机自动开始计时时同时写入 `deadlineAt` 快照，
  避免进程冷启动瞬间倒计时起点偏差

### 类数

132 个类（v1.6.3 为 126 个）

---

## v1.6.3-VIDAA (2026-11-16)

**真机验证：海信 VIDAA TV / MT9653 / Android 11 (API 30) / OS 02.02.70.01**

### 修复

- **媒体暂停/恢复重写为分阶段状态机**（`GateService.java` +4,499 字节）
  - 暂停改为 4 步：`pauseStep1` → `pauseStep2` → `pauseStep3` → `pauseStep4`，
    每步重新校验 `phase() == LOCKED`，避免时序竞争
  - 恢复改为 3 步：`resumeStep1` → `resumeStep2` → `resumeStep3`，
    每步校验 `phase() != LOCKED`，避免遮罩已撤但恢复未完成
  - 新增 `wasPlayingBeforeLock` 记录锁屏前是否在播放，
    仅在原本播放时才发 `KEYCODE_MEDIA_PLAY`，避免误启动播放器
  - 新增 `pausedByToggle` 区分「网关暂停」与「用户自己暂停」
  - 引入 `AudioFocusRequest` 对象（`focusReqTransient` / `focusReqGain`）替代旧式 API
  - 新增 `cancelPauseCallbacks()` / `cancelResumeCallbacks()`，退出时清理未执行的延迟任务

- **口令设置改为两遍确认**：首次设置需输入两次，不一致时提示「两次输入不一致，请重试」

### 变更

- 设置项文案调整：
  - 「每次观看时长（到期锁屏答题）」
  - 「答对后下一轮观看时长（0 分钟表示循环答题）」
  - `Prefs.extendSummary()` 改为返回「单次模式 / 循环答题 / 答对后看 N 分钟再答题」

### 已知问题

- `LanServer.localIp()` 仍未排除 `p2p0`（Wi-Fi Direct 虚拟网卡），
  首页可能显示 `192.168.75.x`，请手动使用 `wlan0` 的 IP 访问 `:8787`
- 悬浮窗授权在部分固件卸载重装后残留为 `ignore`，需重新执行
  `adb shell appops set app.quizgate.tv SYSTEM_ALERT_WINDOW allow`

### 类数

126 个类（与 v1.6.0 / v1.6.2 相同，本版只改逻辑未加类）

---

## v1.6.0-VIDAA (2026-11-16)

**真机验证：海信 VIDAA TV / MT9653 / Android 11 (API 30) / OS 02.02.70.01**

### 新增

- **大人模式**：按口令一键暂停全部管控，本次观看免打扰；电视重启后自动退出并恢复儿童规则
  - 电视端首页按钮、手机局域网控制台均可触发
  - 新增 `GateService.ACTION_ADULT_ON` / `ACTION_ADULT_OFF`
  - 新增 `Prefs.adultMode()` / `setAdultMode()`
- **息屏/待机超时重锁**：电视息屏超过设定时长（默认 15 分钟，0–120 可调）后重新亮屏时自动锁屏答题
  - 新增 `Prefs.idleRelockMin()` / `setIdleRelockMin()`
  - 监听 `ACTION_SCREEN_ON`，配合 `screenOffElapsedAt` 计算熄屏时长
- **局域网控制台新增操作**：开启/关闭大人模式
- **首页状态栏改版**：显示「今日已看 N / 上限 分钟」、宵禁时段、星星积分、LAN 控制台地址

### 修复

- 家长口令验证后不再直接结束会话，改为「解锁当前锁，继续执行规则」（`pin_ok -> unlocked current lock, continuing rules`），避免答完即永久解锁

### 已知问题

- `LanServer.localIp()` 遍历网卡时未排除 `p2p0`（Wi-Fi Direct），部分机型首页会显示 `192.168.75.x` 这类手机连不上的地址。请手动使用 `wlan0` 的 IP 访问 `:8787`

### 类数

126 个类（v1.5.0 为 123 个）

---

## v1.5.0-VIDAA (2026-11-15)

**真机验证：海信 VIDAA TV / MT9653 / Android 11**

### 新增

- **局域网家长控制台**（`LanServer.java`，TCP 8787）
  - 手机同 Wi-Fi 浏览器打开 `http://<电视IP>:8787`
  - 查看今日已看时长 / 上限、星星积分、宵禁时段、最近错题
  - 输入 6 位家长口令后可执行：立即锁屏答题 / 今日 +15 分钟 / 远程解锁
  - **不做任何外联**，纯本地 HTTP 服务；未通过口令校验只读不写
- **每日总时长上限**：跨重启累计，零点重置，默认 90 分钟（0–480 可调）
  - `Prefs.dailyLimitMin()` / `dailyUsedMs()` / `addDailyBonusMin()` / `isDailyLimitReached()`
- **夜间宵禁**：默认 21:00–次日 07:00，宵禁时段硬锁，可配置
  - `Prefs.curfewPreset()` / `curfewName()` / `isInCurfewNow()` / `bypassCurfewToday()`
- **护眼强制休息**：每看 N 分钟（默认 30）强制远眺休息 N 秒（默认 60）
  - `Prefs.eyeIntervalMin()` / `eyeRestSec()`
- **防瞎蒙 · 每题最少读题时间**：默认 3 秒内锁定提交（0–10 可调）
  - `Prefs.minReadSec()`
- **防瞎蒙 · 连错冷却**：默认连错 2 题锁 30 秒（10–120 可调）
  - `Prefs.guessTrigger()` / `guessCooldownSec()`
- **错题记录本**（`WrongBookView.java`）：记录题干、错选、正解、解析；支持清空
  - `Prefs.addWrongRecord()` / `getWrongArray()` / `clearWrongBook()`
- **星星积分银行**：答对 +1 ★，Telegram 式累积，可控台查看
  - `Prefs.stars()` / `addStar()` / `setStars()`
- **锁屏原因区分**：`Prefs.lockReason()` 可区分普通答题锁 / 宵禁锁 / 每日上限锁 / 护眼锁

### 类数

123 个类（v1.4.0 为 100 个）

---

## v1.4.0-VIDAA (2026-11-15)

**真机验证：海信 VIDAA TV / MT9653 / Android 11**

### 修复

- **`mask_regrab` 每 2.6 秒无限循环闪屏**（严重）
  - 根因：`maskLp()` 缺少 `FLAG_ALT_FOCUSABLE_IM` 且启用 `FLAG_DIM_BEHIND`，导致 VIDAA 输入法与语音助手 `com.keylab.speech.view.vidaa` 抢焦点；`keepMaskOnTop()` 每 2.5 秒 `removeMask()+showMask()` 销毁重建 `MaskView`
  - 修复：加入 `FLAG_ALT_FOCUSABLE_IM`、改用 `PixelFormat.OPAQUE`、4 秒按键保护期、软聚焦 `requestFocus()+updateViewLayout()`、原位复用 `reattachMaskInPlace()`、指数退避 3.5s→7s→14s→28s→56s
- **遮罩背后视频继续播放并发声**
  - 修复：`AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE` 抢占音频焦点 + `dispatchMediaKeyEvent(KEYCODE_MEDIA_PAUSE)` + `adjustStreamVolume(STREAM_MUSIC, ADJUST_MUTE)` + 每秒防复活巡检
  - 答对/解锁时 `ADJUST_UNMUTE` 并自动派发 `KEYCODE_MEDIA_PLAY` 续播

### 新增

- **多年级多选**（1–9 年级位掩码 `gradeMask`）+ 小学全选 / 初中全选 / 全年级预设
- **多学科随机**（`subjectMask`：语文 / 数学 / 英语 / 科学百科）
- **多题连答**：每次需答对 1–20 题（默认 3）
- **答错加罚**：每错 1 题加罚 0–5 题（默认 +1）
- **答对延长可关闭**：设为 0 分钟则通关后直接解锁
- **开机自启模式**：开机直接锁屏答题 / 开机自动开始计时 / 关闭
- **离线题库扩充**：36 个分片 JSON（语文/数学/英语/科学 × 1–9 年级）

### 类数

100 个类

---

## 上游版本（供对照）

| 版本 | 说明 |
| --- | --- |
| v1.3.0 | 上游最后一个公开版本：题库扩到语数英 × 1–9 年级（216 题），授权自检收进「守护状态」子页，取消电池白名单 |
| v1.2.9 及以前 | 需三条授权（含电池优化白名单），升级源走已下线的旧目录 |

完整上游变更历史见 [上游 CHANGELOG](https://github.com/mtvtopcom/quizgate-tv/blob/main/CHANGELOG.md)。
