# FixOw 交接文档

面向下一个接手的人。适用设备：OPPO Watch（OWW221，Android 11 / ColorOS Watch 5.0，378×496，armeabi-v7a，已 Root + Magisk + LSPosed）。

## 0. 一句话

FixOw 是一个 LSPosed 模块（包名 `cn.classfruit.fixow`）：解除 HeySystemUI 的通知包名白名单、给缺图标的通知补应用图标，并把控制中心/通知/音量/健康中心/锁屏等界面改成 X2 风格白玻璃；所有玻璃项都有独立开关。

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

## 6. Hook 清单

### SystemUI（com.heytap.wearable.systemui）

| 目标 | 作用 | 开关 |
| --- | --- | --- |
| `w4.y.b(StatusBarNotification)Z` | 白名单闸门，命中直接返回 true | `fixow_bypass` |
| `StatusBarNotification.getNotification()` | 返回时 largeIcon 为空则注入应用图标（缓存） | `fixow_icon` |
| `e5.c.G(Context)` | 通知详情卡：`layout_card` 玻璃、`view_bg` 透明、按钮玻璃 | `fixow_sw_detail` |
| `VolumeSeekBar.onDraw` | 音量弹层：只设玻璃背景，不拦截原绘制 | `fixow_sw_vol` |
| `ColorNumericKeyboard.a(Context)` | 锁屏 PIN：数字键玻璃；可选模糊背景 | `fixow_sw_lock` / `fixow_sw_lock_bg` |

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
- 系统升级后需按新 dex 重新核对混淆类名/字段（`w4.y`、`e5.c`、`u5.b`、`LauncherToggleButton` 等）。