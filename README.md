# FixOw

LSPosed 模块：为 OPPO Watch（ColorOS Watch / Android 11，实测 OWW221）补全通知显示，并把部分系统界面改成 X2 风格的白玻璃 UI。

> 仅供个人学习与研究使用。修改系统 UI 存在风险，请自行备份并可随时停用模块回滚。

## 功能

1. **通知白名单解除**：Hook HeySystemUI 的包名闸门（`w4.y.b`），让任意应用的通知都能显示。
2. **通知图标兜底**：通知缺少 `largeIcon` 时，自动用该应用图标补上（列表 / 详情 / 弹窗统一生效）。
3. **X2 风格玻璃 UI**（可开关）：
   - 控制中心磁贴：白色 11% / 选中 80% 玻璃 + 图标反色
   - 通知中心卡片、"全部清除"按钮
   - 通知详情卡片（SystemUI `e5.c`）
   - 音量卡（宽版 `LauncherToggleButton`）与 SystemUI 音量条
   - 健康中心文字按钮（查看详情 / 测量压力 / 助眠冥想 / 睡眠模式等）
   - 锁屏 PIN 数字键盘
4. **可配置**（模块内 UI）：
   - 选择背景照片（取不到表盘的页面用这张照片做高斯模糊底）
   - 模糊强度（0–24）
   - 玻璃风格（浅色 / 深色 / 极简）
   - 各功能开关

## 原理与限制

- Android 11 没有实时背景模糊 API（`RenderEffect`/`BlurBehind` 是 Android 12+），本模块的背景是**预渲染的静态高斯模糊图**，不是实时模糊。
- 程序列表（`id_app_list_view_3d`）是 GL/3D 表面，Xposed 层无法在其背后插入背景。
- Hook 点依赖当前系统版本（ColorOS Watch V5 / HeySystemUI 3.0.0_5b0396d_260914、HeyLauncher 2.0.2_7141fbb_260930、HeyHealthCenter 4.0.3）。OTA 后类名/字段可能变化，需要重新适配。

## 环境

- 手表：OPPO Watch（OWW221 实测），Android 11，已 Root + Magisk + LSPosed
- 作用域：`com.heytap.wearable.systemui`、`com.heytap.wearable.launcher`、`com.heytap.wearable.healthcenter`
- 权限：安装后需授予 `WRITE_SECURE_SETTINGS`（用于保存配置）：

```bash
adb shell pm grant cn.classfruit.fixow android.permission.WRITE_SECURE_SETTINGS
```

## 构建

需要 Android SDK build-tools（aapt2 / d8 / zipalign / apksigner）与 android.jar：

```bash
export ANDROID_JAR=/path/to/android.jar
export BUILD_TOOLS=/path/to/build-tools
export KS=/path/to/your.jks KS_PASS=yourpass KS_ALIAS=youralias KS_KEY_PASS=yourpass
./build.sh
```

`build.sh` 会编译 `src/`、打包 `res/` 与 `assets/`、生成并签名 `FixOw.apk`。仓库不包含任何签名密钥与已编译 APK。

## 安装

```bash
adb install -r FixOw.apk
adb shell pm grant cn.classfruit.fixow android.permission.WRITE_SECURE_SETTINGS
```

在 LSPosed 中启用模块并勾选三个作用域包，然后重启 SystemUI / Launcher / 健康中心进程（或重启设备）。

## 配置项（Settings.Secure）

| key | 含义 |
| --- | --- |
| `fixow_bypass` | 通知白名单解除（默认 1） |
| `fixow_icon` | 通知图标兜底（默认 1） |
| `fixow_sw_tiles` | 控制中心磁贴玻璃（默认 1） |
| `fixow_sw_bg` | 面板模糊背景（默认 1） |
| `fixow_sw_notif` | 通知中心卡片玻璃（默认 1） |
| `fixow_sw_detail` | 通知详情卡片玻璃（默认 1） |
| `fixow_sw_vol` | 音量条玻璃（默认 1） |
| `fixow_sw_lock` | 锁屏键盘玻璃（默认 1） |
| `fixow_glass_hc` | 健康中心玻璃（默认 1） |
| `fixow_bg` | 背景照片（Base64 JPEG） |
| `fixow_blur` | 模糊强度（默认 16） |
| `fixow_style` | 玻璃风格 0/1/2（浅色/深色/极简） |

## License

MIT
