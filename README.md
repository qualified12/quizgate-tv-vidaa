# 答题续时 · QuizGate TV · VIDAA Edition

> **本仓库是 [mtvtopcom/quizgate-tv](https://github.com/mtvtopcom/quizgate-tv)（Apache-2.0）的衍生分支**
> An open-source Android TV parental screen-time gate with quiz-to-unlock,
> enhanced for **Hisense VIDAA** TVs.

---

## 这是什么

给电视用的「答题续时」闸门：看到设定时长后自动锁屏出题，答对才能继续看。
不是不可绕过的儿童锁，而是把「白看」的成本提上去。

- **到点全屏出题**，答对才续时 / 解锁
- **后台音视频自动暂停并静音**，遮罩背后不会继续播放
- **免 root、离线运行**，题库全部打包在 APK 内
- **无账号、无广告、无数据上报**

---

## 相比上游的核心改进

### 1. 海信 VIDAA 底层修复

- **`mask_regrab` 每 2.6 秒闪屏并重置题目**
  根因：`maskLp()` 缺少 `FLAG_ALT_FOCUSABLE_IM` 且启用 `FLAG_DIM_BEHIND`，
  VIDAA 输入法与语音助手 `com.keylab.speech.view.vidaa` 会附着到可获焦悬浮窗抢走焦点，
  触发 `keepMaskOnTop()` 每 2.5 秒 `removeMask(); showMask();` 销毁重建。
  修复：加 `FLAG_ALT_FOCUSABLE_IM`、改用 `PixelFormat.OPAQUE`、4 秒按键保护期、
  软聚焦 `requestFocus()+updateViewLayout()`、原位复用同一 `MaskView` 实例、
  指数退避 3.5s → 7s → 14s → 28s → 56s。
- **遮罩背后视频继续播放发声**
  修复：`AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE` 抢占音频焦点 +
  `dispatchMediaKeyEvent(KEYCODE_MEDIA_PAUSE)` + 静音 `STREAM_MUSIC` + 每秒防复活巡检；
  答对或解锁时恢复音量并派发 `KEYCODE_MEDIA_PLAY` 续播。
  自 v1.6.3 起改为分阶段状态机（4 步暂停 / 3 步恢复），记录 `wasPlayingBeforeLock`
  判断原本是否在播放，避免误暂停或误恢复。

### 2. 倒计时可靠性（v1.6.2 起）

- 改用 `SystemClock.elapsedRealtime()` 作为硬件时钟基准，`Prefs.deadlineAt()` 双记录，
  心跳间隔 250ms，解决进程被冻结/回收后倒计时漂移。
- `AlarmManager.setExactAndAllowWhileIdle()` 备份闹钟兜底，即使进程被杀也能准时锁屏。
- 单步计入今日总时长的上限设为 10 秒，防止进程恢复时 delta 过大误统计。

### 3. 家长管控能力

- **大人模式**：口令一键暂停全部管控，重启后自动恢复儿童规则；电视端与手机控制台均可触发
- **息屏超时重锁**：熄屏超过设定时长（默认 15 分钟）后重新亮屏时自动锁屏答题
- **开机自启**：开机直接锁屏答题 / 开机自动开始计时 / 关闭
- **多题连答**：每次需答对 1–20 题（默认 3），答错加罚 0–5 题
- **防瞎蒙**：每题最少读题时间（默认 3 秒）、连错冷却锁定（默认连错 2 题锁 30 秒）
- **作息边界**：每日总时长上限（默认 90 分钟，跨重启累计、零点重置）、
  夜间宵禁（默认 21:00–次日 07:00）、护眼强制远眺休息（每 30 分钟休息 60 秒）
- **激励与复盘**：错题记录本（题干 / 错选 / 正解 / 解析）、星星积分银行
- **错题精讲**（v1.6.7）：答错弹全屏解析面板，错选与正解对照 + 知识点讲解，确认看懂才进下一题；动态题也自带分步解析

### 4. 题库

- 1–9 年级位掩码多选，支持小学全选 / 初中全选 / 全年级预设
- 四科随机：语文、数学、英语、科学百科
- 36 个分片 JSON（1,477 道静态题）+ 动态数学/方程生成器 + 48 题防重复滑动队列

### 5. 局域网家长控制台

手机连同一 Wi-Fi，浏览器打开 `http://<电视IP>:8787`：

- 查看：今日已看时长 / 上限、星星积分、宵禁时段、最近错题
- 需输入 6 位家长口令才可操作：立即锁屏答题、今日 +15 分钟、开关大人模式

纯本地 HTTP 服务，**不做任何外联**，未通过口令校验只读不写。

---

## 实测兼容性

| 机型 / 芯片 | 系统 | 状态 |
| --- | --- | --- |
| **海信 VIDAA TV (`MT9653`)** | Android 11 (API 30) / OS `02.02.70.01` | ✅ 真机验证 |
| 小米电视 MiTV-MFTR0 | Android 11 / 14 | 🔲 未验证 |
| 其他机型 | — | 🔲 未验证 |

> 本 fork 只在海信 `MT9653` / Android 11 上做过真机验证。
> 上游作者验证的是小米 MiTV-MFTR0，但本 fork 改动量较大，
> 小米机型的兼容性**尚未由本 fork 重新验证**。
> 其他机型一律「未验证」——请勿把未验证的格子当成结论。

---

## 安装

```bash
adb connect <电视IP>:5555

# 海信 VIDAA 建议先抑制系统语音助手（否则容易抢焦点）
adb shell appops set com.keylab.speech.view.vidaa SYSTEM_ALERT_WINDOW ignore

adb install -r quizgate-vidaa-pro-1.6.7.apk

# 三项授权（必需）
adb shell appops set app.quizgate.tv SYSTEM_ALERT_WINDOW allow
adb shell cmd notification allow_listener app.quizgate.tv/app.quizgate.tv.NotifProbe
adb shell pm grant app.quizgate.tv android.permission.WRITE_SECURE_SETTINGS
```

Windows 用户可使用 Release 中的 `deploy_vidaa.ps1` 或 `一键部署到海信电视.bat`。

### 首次使用

1. 打开应用 → 设 6 位家长口令（输两遍）→ **拍照保存恢复码**
2. 进「家长设置」配置时长、答题数、年级、学科
3. 回首页点「开始计时」

---

## 已知问题

| 问题 | 影响 | 状态 |
| --- | --- | --- |
| 局域网控制台首页显示的 IP 可能是 `p2p0`（Wi-Fi Direct 虚拟网卡）地址，如 `192.168.75.x` | 手机连不上；请手动使用 `wlan0` 的实际 IP 访问 `:8787` | 待修复（`LanServer.localIp()` 未排除 `p2p0`） |
| 部分固件卸载重装后悬浮窗授权残留为 `ignore` | 到点不弹题 | 重新执行 `adb shell appops set app.quizgate.tv SYSTEM_ALERT_WINDOW allow` |
| 卸载会清空口令、恢复码与全部设置 | 重装后需重新设置 | Android 平台限制 |
| 防不住卸载 | 孩子卸载重装可绕过 | 平台限制；首页有卸载留痕告警 |

---

## 本地构建

需要 **JDK 17** 与 **Android SDK**（`build-tools;35.0.0` + `platforms;android-35`）：

```bash
export ANDROID_HOME=/path/to/android-sdk
export JAVA_HOME=/path/to/jdk-17
./build.sh            # 调试包
./build.sh release    # 发布包（需自备 keystore/release.jks）
```

构建链 `aapt2 → javac → d8 → zipalign → apksigner`，不依赖 Gradle，不联网拉取依赖。

---

## 开源协议与致谢

- **上游**：[mtvtopcom/quizgate-tv](https://github.com/mtvtopcom/quizgate-tv)（Apache-2.0）
- **代码协议**：[Apache License 2.0](LICENSE)
- **题库协议**：[CC BY-SA 4.0](LICENSE-CONTENT)
- **修改声明**：[NOTICE](NOTICE)
- **变更日志**：[CHANGELOG.md](CHANGELOG.md)

### 合规声明

本仓库是 Apache-2.0 派生物，遵循该协议第 4 条：

- 每个修改过的源文件头部带有修改声明注释
- 完整保留上游版权与署名信息
- 附 Apache-2.0 许可证全文（`LICENSE`）
- 题库内容依上游约定单独采用 CC BY-SA 4.0（`LICENSE-CONTENT`）

**APK 签名密钥与上游不同**，无法覆盖安装上游版本，安装前需先卸载。
