# FixOw 交接文档

面向下一个接手的人。适用设备：OPPO Watch（OWW221，Android 11 / ColorOS Watch 5.0，378×496，armeabi-v7a，已 Root + Magisk + LSPosed）。

## 0. 一句话

FixOw 是一个 LSPosed 模块（包名 `cn.classfruit.fixow`）：解除 HeySystemUI 的通知包名白名单、给缺图标的通知补应用图标，把控制中心/通知/音量/健康中心/锁屏等界面改成 X2 风格白玻璃，另有一组 SystemUI 行为定制（电源菜单逐条目分叉、勿扰/运动模式拦截、静音位、AOD、卖场模式等）；每一项都有独立开关。

**当前设备状态（2026-11-22 用户要求）**：玻璃 UI 全部开关已置 0、Magisk 守护模块已放 `disable` 且循环已 kill，只保留通知白名单 + 图标兜底。理由是避免主动重启 SystemUI 触发 OEM 看门狗。模块版本 v2.4（versionCode 6）。

仓库：https://github.com/fruit0730/FixOw （MIT）

## 1. 环境与版本

| 项 | 值 |
| --- | --- |
| 设备 | OWW221（monaco），Android 11 |
| 分辨率 | 378×496，density 约 2.0 |
| HeySystemUI | 3.0.0_5b0396d_260914 |
| HeyLauncher | 2.0.2_7141fbb_260930 |
| HeyHealthCenter | 4.0.3_35707a9_240617 |
| Root | Magisk 27002 + zygisk_lsposed |
| 调试 | 无线 adb：`adb connect <手表IP>:5555`（IP 会变；重启后需重开手表里的 adb-wifi 应用） |

注意：类名/字段名是混淆的，和上面版本绑定；系统 OTA 后必须重新核对。

## 2. 仓库结构

```
src/cn/classfruit/fixow/Hook.java        # 全部 hook 逻辑
src/cn/classfruit/fixow/MainActivity.java# 模块 UI（开关/选图/模糊/风格）
res/                                     # 布局、字符串、图标、X2 磁贴底色
stubs/                                   # Xposed API 编译桩（不打包进 dex）
assets/xposed_init / xposed_scope        # LSPosed 入口与作用域
guard/                                   # Magisk 守护模块（见 §5）
build.sh / build.bat                     # 本地构建
.github/workflows/build.yml              # CI 构建未签名 APK
```

`classes/`、`dexout/`、`*.apk`、`*.jks` 全部被 `.gitignore` 排除；仓库不含签名密钥。

## 3. 构建、签名、安装

依赖：`aapt2`、`javac(11+)`、`d8`、`zipalign`、`apksigner`、`android.jar`。

```bash
export ANDROID_JAR=/path/to/android.jar
export BUILD_TOOLS=/path/to/build-tools
export KS=/path/to/your.jks KS_PASS=*** KS_ALIAS=*** KS_KEY_PASS=***
./build.sh          # 生成并签名 FixOw.apk
```

要点：
- 先 `aapt2 compile res` → `aapt2 link --java gen -A assets` → `javac`（带 stubs sourcepath）→ `d8` 只喂 `classes/cn/classfruit/fixow/*.class`（**不要把 stubs 的 class 打进 dex**，否则会覆盖 LSPosed 的 XposedBridge/XposedHelpers，hook 静默失效）。
- 安装：`adb install -r FixOw.apk`。
- **`install -r` 会重置权限**，装完必须重新 `pm grant cn.classfruit.fixow android.permission.WRITE_SECURE_SETTINGS`（配置存在 Settings.Secure）。

## 4. LSPosed 配置

作用域三包：`com.heytap.wearable.systemui`、`com.heytap.wearable.launcher`、`com.heytap.wearable.healthcenter`。

LSPosed 把启用状态存 `/data/adb/lspd/config/modules_config.db`（WAL 模式，root 600）：
- `modules(module_pkg_name, apk_path)`、`modules_state`、`scope(module_pkg_name, app_pkg_name, user_id)`。
- 修改方式（本机验证过）：`su -c base64 数据库` → 本地 sqlite3 改 → `su -c 'base64 -d > ...'` 写回 → 删 `-wal/-shm` → 重启 lspd。
- 注意：adb push 到 `/data/local/tmp` 会 `fchown failed`，用 base64 走 su 重定向最稳。
- lspd 不会自动重启（daemon 脚本只是等待）；手动：`su -c 'nohup /data/adb/modules/zygisk_lsposed/daemon >/dev/null 2>&1 &'`。
- 旧备份：`/data/adb/lspd/config/modules_config.db.bak-nowl`。
- 换包名/更新 APK 后务必同步 `modules.apk_path`，否则 LSPosed 加载旧路径。

## 5. Magisk 守护（防 lspd 被杀）

`guard/`（安装到 `/data/adb/modules/fixow_guard/`）：
- Magisk 开机执行 `service.sh`；
- 每 60s `pidof lspd`，缺失则启动 LSPosed daemon，等待 6s 后 kill SystemUI/Launcher/HealthCenter，让它们重启并重新注入 hook（LSPosed 只在进程启动时注入）。
- 平时开销≈每分钟一次 pidof；只恢复，不阻止被杀。
- 关闭：`touch /data/adb/modules/fixow_guard/disable` 后重启，或 `pkill -f fixow_guard/service.sh`；删除目录即卸载。
- **当前设备状态：已放 `disable` 并 kill 循环**（2026-11-22 用户要求"免得崩 systemui"）。主动 kill UI 进程会让 OEM 看门狗有机会累积成整机重启（`ro.boot.bootreason=reboot,systemui_reboot` 也可能是用户自己用电源菜单「重启」产生的，两者要区分：`y5.p.C()` 调 `IPowerManager.reboot(false,"systemui_reboot",false)`，所以这个 bootreason 本身不代表崩溃）。

## 6. Hook 清单

### SystemUI（com.heytap.wearable.systemui）

| 目标 | 作用 | 开关 |
| --- | --- | --- |
| `w4.y.b(StatusBarNotification)Z` | 白名单闸门，命中直接返回 true | `fixow_bypass` |
| `StatusBarNotification.getNotification()` | 返回时 largeIcon 为空则注入应用图标（缓存） | `fixow_icon` |
| `e5.c.G(Context)` | 通知详情卡：`layout_card` 玻璃、`view_bg` 透明、按钮玻璃 | `fixow_sw_detail` |
| `VolumeSeekBar.onDraw` | 音量弹层：只设玻璃背景，不拦截原绘制 | `fixow_sw_vol` |
| `ColorNumericKeyboard.a(Context)` | 锁屏 PIN：数字键玻璃；可选模糊背景 | `fixow_sw_lock` / `fixow_sw_lock_bg` |
| `t1.w.q(Bundle)` / `t1.w.r(JJ)` / `t1.w.i()Z` | 跟踪锁屏状态写进 `sLocked`（q=prepareLock 置锁，r=解锁动画清锁，i() 读同一 `h:Z` 字段）；纯进程内，不发 binder | — |
| `y5.c.show()V` + `y5.c.k(Z)V` + `y5.p.F()V` | 电源菜单逐条目分叉：收起 `slide_*.getParent()`（固定高度的条目外壳），并在 OEM 自己刷完可见性后重刷 + `post()` 补刷 | `fixow_pm_shutdown/restart/smart/sos` |
| `y5.p$a.b(View,Z)V` | 滑动完成统一分发（invoke-interface，不会被 dexpreopt 内联）；tag=="shutdown" 时 SKIP，关机动作不执行 | `fixow_pm_nopoweroff` |
| `LocalNotificationService.f(SBN)Z` | canPost 总闸强制 true，连 `notification_forbidden_list` 黑名单一起绕开 | `fixow_ntf_canpost` |
| `p5.g.b(Notification,String)[Z` | 模式拦截总闸，返回 `{display,vibrate,sound}`；强制 `{true,true,true}` 一次解掉勿扰/睡眠/运动/锁屏/学校模式 | `fixow_ntf_nomode` |
| `x4.g.x(Notification)Z` | `notification_flag & 0x10000000` 静音位判定，返回 false = 无视静音 | `fixow_ntf_nomute` |
| `y2.b.c()Z` | 摘腕状态；返回 false = 摘腕也照常提醒 | `fixow_ntf_offwrist` |
| `g4.e.h()V` | AOD 横幅 10s 显示超时的派发；SKIP = 不自动收起 | `fixow_ntf_keepopen` |
| `notification.manager.r.l(NotificationItem)Z` | 「接收超过 10s」判定；返回 false = 旧通知仍可显示 | `fixow_ntf_stale` |
| `SellModeReceiver.onReceive(Context,Intent)` | 卖场模式清空全部通知；SKIP = 不清 | `fixow_ntf_nosellclear` |
| `r1.a.e(Context)Z` | `hasSystemFeature("oplus.wear.colorful.aod.support")`；返回 true = 强开彩色 AOD | `fixow_aod_color` |
| `LowBatteryAutoSwitchRsModeView.onAttachedToWindow()` | 低电量自动进轻智能模式的提示页；attach 后置 GONE | `fixow_norlowbatt` |

### Launcher（com.heytap.wearable.launcher）

| 目标 | 作用 | 开关 |
| --- | --- | --- |
| `HistoryNotificationImpl.handleHistoryUpdate(item)` | 旧方案：历史列表图标兜底 | `fixow_icon` |
| `StatusBarNotification.getNotification()` | 同上（列表/详情/横幅统一） | `fixow_icon` |
| `QuickCenterView.setWorkspace(Workspace)` | 快捷中心（蓝页）玻璃 | `fixow_sw_tiles` |
| `Launcher.onCreate(Bundle)` | 记录 decor，供截表盘 | — |
| `StatusPanelView / NotificationPanelView.onViewStartShowing()` | 面板显示时铺模糊背景、透明容器、通知卡片/清除按钮玻璃 | `fixow_sw_bg` / `fixow_sw_notif` |
| `LauncherToggleButton.onDraw` | 方形磁贴：白玻璃+图标；宽卡（音量）：原背景色 `c/d` 置 0，玻璃垫底，按字段 `i`（像素宽）画白色填充 | `fixow_sw_tiles` |
| `u5.b.onDraw`（亮度条） | 白玻璃 + 太阳图标（原进度由 ProgressBar 保留） | `fixow_sw_tiles` |
| `VolumeSeekBar.onDraw` | 玻璃垫底，不拦截原绘制 | `fixow_sw_vol` |
| `ViewGroup.addView(View)` | 通知列表新增卡片时自动重贴玻璃 | `fixow_sw_notif` |

### 健康中心（com.heytap.wearable.healthcenter）

| 目标 | 作用 | 开关 |
| --- | --- | --- |
| `NoLimitViewPager.onAttachedToWindow()` | 根视图照片模糊底 + 按钮玻璃 | `fixow_glass_hc` |
| `NoLimitViewPager.dispatchOnPageSelected(int)` | 翻页后重新贴按钮 | `fixow_glass_hc` |

按钮按 id + **文字**匹配（查看详情/测量压力/了解压力/助眠冥想/睡眠模式/开始测量…），避免漏。

## 7. 配置项（Settings.Secure）

| key | 默认 | 含义 |
| --- | --- | --- |
| `fixow_bypass` | 1 | 通知白名单解除 |
| `fixow_icon` | 1 | 通知图标兜底 |
| `fixow_sw_tiles` | 1 | 控制中心磁贴/音量卡/亮度 |
| `fixow_sw_bg` | 1 | 面板模糊背景 |
| `fixow_sw_notif` | 1 | 通知卡片/清除按钮 |
| `fixow_sw_detail` | 1 | 通知详情卡 |
| `fixow_sw_vol` | 1 | 音量条 |
| `fixow_glass_hc` | 1 | 健康中心 |
| `fixow_sw_lock` | 1 | 锁屏键盘 |
| `fixow_sw_lock_bg` | **0** | 锁屏模糊背景（默认关闭=透出表盘） |
| `fixow_bg` | 内置图 | 背景照片（Base64 JPEG，189×248） |
| `fixow_blur` | 16 | 模糊强度 0–24 |
| `fixow_style` | 0 | 0 浅色 / 1 深色 / 2 极简 |
| `fixow_pm_shutdown` | 0 | 电源菜单「关机」：0 始终显示 / 1 仅解锁 / 2 仅锁屏 / 3 始终隐藏 |
| `fixow_pm_restart` | 0 | 同上，「重启」 |
| `fixow_pm_smart` | 0 | 同上，「轻智能模式」（`lightweight_smart_mode`，代码里叫 wristband） |
| `fixow_pm_sos` | 0 | 同上，「紧急呼叫」 |
| `fixow_pm_nopoweroff` | **0** | 滑动到关机也不执行关机动作 |
| `fixow_ntf_canpost` | **0** | canPost 全放行（含系统黑名单） |
| `fixow_ntf_nomode` | **0** | 勿扰/睡眠/运动/锁屏不再拦截横幅与振动铃声 |
| `fixow_ntf_nomute` | **0** | 无视静音标记 |
| `fixow_ntf_offwrist` | **0** | 摘腕仍提醒 |
| `fixow_ntf_keepopen` | **0** | AOD 横幅不 10s 自动收起 |
| `fixow_ntf_stale` | **0** | 超过 10s 的旧通知仍可显示 |
| `fixow_ntf_nosellclear` | **0** | 卖场模式不清空通知 |
| `fixow_aod_color` | **0** | 强开彩色 AOD |
| `fixow_norlowbatt` | **0** | 低电量进轻智能模式的提示不弹 |

电源菜单四个条目在 UI 上是**循环按钮**（点一下换一档，值 0–3），不是开关；其余新增项是开关，**全部默认 0（关闭）**，玻璃 UI 那组也默认关闭。

Hook 进程内对 Settings 做了 1 秒缓存（`cfg()`），避免每帧读取。

## 8. 实现要点与坑

1. **LSPosed 这版取不到 `AndroidAppHelper`**（内部包名重映射缺类）→ 用反射 `ActivityThread.currentApplication()` 拿 Context。
2. **构造器 hook 会 `NoSuchMethodError`**（如 QuickCenterView `<init>(Context)`）→ 改挂非构造方法（setWorkspace / onAttachedToWindow / 初始化方法）。
3. **BitmapShader 必须 setLocalMatrix 缩放**，否则模糊图只画在左上角（曾踩）。
4. **背景来源**：优先截当前表盘（launcher 自己的 View），做黑屏检测，过暗则回落到预设照片；`sLastWf`/`sBgCache`/`sPhotoCache` 做缓存。模糊计算放后台线程，避免打开面板卡顿。
5. **不要跳过原 onDraw**（音量卡/音量条）——原绘制里有跟手/阻尼状态，跳过会导致"灵敏度不对"。正确做法：把原背景色字段置 0（`c`、`d`）或只设玻璃背景，让原绘制继续跑。
6. **音量卡**：`LauncherToggleButton` 字段 `i` 是填充像素宽，`c` 选中色、`d` 未选中色，`e` 图标，`h` 可用位。改 x2 样式：c/d 置 0 + 玻璃底 + 按 `i` 画白填充；关闭开关时用 WeakHashMap 快照还原原背景与 c/d。
7. **通知卡片会刷新**：只在弹出时贴玻璃会"变回去"，已挂 `ViewGroup.addView` 重贴（id `cl_item` / `slideLayout`）。
8. **双击/长按顶部在 app 内会先出锁屏**：测试时要保持解锁。
9. **锁屏**：默认不垫底（透出表盘）+ 数字键玻璃；表盘在别的进程，SystemUI 截不到，想要模糊底只能开 `fixow_sw_lock_bg` 用预设照片。
10. **adb 自动化注意**：`input` 注入偶发 `INJECT_EVENTS permission`；设备会自动锁屏/息屏，`screencap` 前先 `KEYCODE_WAKEUP`，PIN 页用 BACK+HOME 退出。
11. **程序列表（`id_app_list_view_3d`）是 GL/3D 表面**，View 层插不进背景，无法做模糊（Xposed 层上限）。
12. **X2 桌面 APK 无法安装**：与现有桌面同包名同签名（WearTeam），但 ColorOS 的 `persist.sys.oplus.install` 核心应用校验直接拒绝：`INSTALL_FAILED_ALREADY_EXISTS: forbid install core app`。改属性也无效（这台 services 逻辑不同）。Magisk 覆盖方案被用户否决，改为逐屏仿制。
13. **电源菜单没有列表适配器**：4 个条目是 `y5.c` 里硬编码的 `SlideButton`+`ImageView`，且每个条目外面套一层**固定高度**的壳（`layout_height=@dimen`）。只藏 `slide_*`/`iv_*` 会留下空位、后面的条目不补位——必须藏 `slide_*.getParent()`。注意关机/重启**各有各的壳**（早期误判成共享容器 `0x7f090122`，那个其实是标题/占位区）。
14. **OEM 会在 `show()` 之后重刷轻智能那行**：`y5.c.k(Z)V` 直接对 `Ly5/c;.o`（=`layout_wristband`）`setVisibility(0/8)`，由 `y5.p.F()V` 调用。只 hook `show()` 的话我们的 GONE 会被覆盖回去（用户实测"轻智能无法隐藏"就是这个）。现在 `show()` / `k(Z)V` / `F()V` 三处都重刷，并在 decor 上 `post()` 到消息队列尾部再补一次。
15. **`KEY_CONFIRM` 不是锁屏标志**：`ShutdownActivity.onCreate` 用它决定"要不要弹这个菜单"——为 false 时直接起静默关机线程并 `finish()`，所以菜单能出现时它必然为 true。锁屏状态要用 SystemUI 自己的 `t1.w`（KeyguardViewMediator）字段 `h:Z`：`q(Bundle)`=prepareLock 置 true、`r(JJ)`=解锁动画置 false、`i()Z` 读同一字段。
16. **别 hook `public final` + invoke-virtual 的方法**：HeySystemUI 是系统应用，可能被 dexpreopt 内联，hook 会静默不生效。`y5.p.C/D/E`、`y5.c.e..i` 都是这种。可靠选择：非-final 方法（`y5.c.show/k/p`）、接口实现（`y5.p$a.b(View,Z)V`，invoke-interface 必命中）、静态纯函数（`a3.r.p`、`x4.g.x`、`p5.g.b`、`r1.a.e`、`y2.b.c`）。
17. **资源 id 以 `aapt dump resources` 为准**：`layout_sos=0x7f090148`、`layout_wristband=0x7f09014a`（早期笔记把两者写反过）；`slide_shutdown=0x7f0901e3`、`slide_reboot=0x7f0901e1`、`slide_wristband=0x7f0901e5`、`slide_sos=0x7f0901e4`，图标 `iv_*` 依次 `0x7f090133/0x7f090130/0x7f09013c/0x7f090134`。
18. **新增 hook 只在进程启动时装载**：`install -r` 之后要等 SystemUI/Launcher 自然重启（重启手表最稳），不要主动 kill（见 §5）。lspd 自己会监听重装并同步 `modules.apk_path`，本机验证过无需手改 DB。

## 9. 性能/功耗

无服务、无定时器、无 wakelock；只在亮屏交互产生 CPU。首次打开面板计算一次背景（后台线程+缓存），之后复用；设置读取 1s 缓存；内存缓存为几百 KB 位图。lspd 被杀属于 LSPosed/系统层面，由 `guard` 模块恢复。

## 10. 回滚

- 模块：`adb uninstall cn.classfruit.fixow`（先关 LSPosed 里的模块/作用域更干净）。
- 开关：FixOw 页逐项关闭；关闭后会在下次绘制/弹出时还原原背景与原字段。
- 守护：删除 `/data/adb/modules/fixow_guard` 或放 `disable` 文件重启。
- LSPosed：恢复 `modules_config.db.bak-nowl`，或删除模块行后重启 lspd；kill 三个 UI 进程让它们回到未注入状态。

## 11. 排障命令

```bash
ADB=/c/platform-tools/adb.exe
$ADB connect <IP>:5555
$ADB shell 'pidof lspd; pidof com.heytap.wearable.systemui; pidof com.heytap.wearable.launcher'
$ADB shell 'su -c "ls -t /data/adb/lspd/log/modules_*.log | head -1 | xargs grep -a FixOw"'
$ADB shell 'settings get secure fixow_sw_bg'
$ADB shell 'uiautomator dump /data/local/tmp/ui.xml; cat /data/local/tmp/ui.xml'
$ADB shell 'su -c "screencap -p /data/local/tmp/s.png"'
$ADB pull //data/local/tmp/s.png
```

## 12. 已知未完成

- 程序栏（GL 3D）背景无法处理。
- 通知详情（SystemUI `SysUI.NormalNotification`）已挂卡片玻璃，但"查看详情/关闭"按钮那一版布局未单独适配。
- 部分健康中心自绘按钮（非文字按钮）未覆盖。
- 系统升级后需按新 dex 重新核对混淆类名/字段（`w4.y`、`e5.c`、`u5.b`、`LauncherToggleButton`、`y5.c`/`y5.p`、`p5.g`、`x4.g`、`t1.w` 等）。

### 固件层面做不到（已用 dexdump 全文核实，别再试）

- **锁屏上的"紧急呼叫"入口不存在**：全 HeySystemUI dex 里 `CALL_EMERGENCY` 只出现在电源菜单 `y5.p.B()`；`launcher.dex.txt` 里 `emergency` 命中数 0；`keyguard.*` 类里没有任何通知/紧急呼叫 UI。
- **SystemUI 不绘制状态栏图标**：dex 里没有 `StatusBar*`/`IconView`/`Panel`/`Shade` 类，`setViewVisibility` 命中 0。信号/WiFi/NFC/电量都在 launcher（`status_bar_top`、`signal_strength`、`iv_wifi`、`nfc_view`、`battery`），要动图标只能进 launcher 进程。
- **通知条数上限（30）改不了**：`const/16 v6, #int 30` 内联在 `i4.k.Z()` 里，那方法 262 条指令且持 `monitor-enter/exit`，整段替换必崩。
- **"2 分钟以上旧通知丢弃"改不了**：`Lw4/e;.b:J` 是 `public static final`，读取方式是 `sget-wide`（没有方法边界），Xposed 写不了 final static。
- **heads-up 时长没有本机常量**：由手机下发 `NotificationExtendPush.getShowDuration()` → `setExpiredTime`；本机只有 AOD 的 10000ms 超时（`g4.c.b()`）和 10s 新鲜度（`manager.r.l()`）两处，已分别做成开关。
- **抬手亮屏不在 SystemUI**：无 raise/lift/tilt/palm 相关键或方法，只有 `y2.b` 戴/摘腕状态与 `debug.force.wrist` 属性。
- **锁屏通知正文隐私/锁屏禁止下拉**：dex 里 `NotificationVisibility`/`showPublic`/`redact`/`VISIBILITY_SECRET` 命中 0，keyguard 视图树里没有通知容器；面板归 launcher，SystemUI 侧无从下手。
- `sLocked` 初值是"未锁"：开机后若 SystemUI 还没跑过一次锁/解锁就长按电源键，会看到完整 4 项——状态未知时宁可不改，也不乱裁菜单。