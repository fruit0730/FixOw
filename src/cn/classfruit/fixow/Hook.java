package cn.classfruit.fixow;

import android.app.Notification;
import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.provider.Settings;
import android.service.notification.StatusBarNotification;
import android.util.AttributeSet;
import android.util.Base64;
import android.view.View;
import android.view.ViewParent;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Hook implements IXposedHookLoadPackage {
    private static final String SYSUI = "com.heytap.wearable.systemui";
    private static final String LAUNCHER = "com.heytap.wearable.launcher";
    private static final String HEALTH = "com.heytap.wearable.healthcenter";
    private static final int ID_WATCHFACE = 0x7f0a02b7;
    private static final int ID_STATUS_PANEL = 0x7f0a0257;
    private static final int ID_APP_LIST = 0x7f0a011b;
    private static final int[] HEALTH_BTNS = {
            0x7f0a0067, 0x7f0a004b, 0x7f0a007d, 0x7f0a0080, 0x7f0a0079};
    private static final String[] HEALTH_BTN_TEXTS = {
            "测量压力", "了解压力", "查看详情", "了解更多", "去了解",
            "助眠冥想", "睡眠模式", "开始测量"};
    private static WeakReference<View> sLauncherDecor = new WeakReference<>(null);
    private static View sGlobalContainer;
    private static Bitmap sLastWf;
    private static Bitmap sBgCache;
    private static int sBgCacheBlur = -1, sBgCacheStyle = -1;
    private static Bitmap sPhotoCache;
    private static final Map<String, Bitmap> ICONS = new HashMap<>();

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) {
        if (SYSUI.equals(lp.packageName)) {
            hookWhitelist(lp);
            hookSbnIcon(lp);
            hookDetailCard(lp);
            hookVolumeBar(lp);
            hookKeyguardPin(lp);
        } else if (LAUNCHER.equals(lp.packageName)) {
            hookIcon(lp);
            hookSbnIcon(lp);
            hookGlassQuickCenter(lp);
            hookGlassControl(lp);
            hookGlobalWindow(lp);
            hookTiles(lp);
            hookBrightness(lp);
            hookVolumeBar(lp);
            hookCardAdd(lp);
        } else if (HEALTH.equals(lp.packageName)) {
            hookGlassHealth(lp);
        }
    }

    private void hookGlassQuickCenter(XC_LoadPackage.LoadPackageParam lp) {
        try {
            Class<?> cls = XposedHelpers.findClass(
                    "com.heytap.wearable.launcher.quickcenter.view.QuickCenterView", lp.classLoader);
            Class<?> ws = XposedHelpers.findClass("com.heytap.wearable.launcher.Workspace", lp.classLoader);
            XposedHelpers.findAndHookMethod(cls, "setWorkspace", ws, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam p) {
                    final View qc = (View) p.thisObject;
                    if (!on("fixow_sw_tiles")) return;
                    qc.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                View wf = qc.getRootView().findViewById(ID_WATCHFACE);
                                Bitmap base = wf == null ? null : blur(snapshot(wf), blurRadius());
                                if (base == null) base = fallbackBase();
                                qc.setBackground(new GlassDrawable(base, 34, glassStyle()));
                                XposedBridge.log("FixOw: quick center glass applied (base=" + (base != null) + ")");
                            } catch (Throwable t) {
                                XposedBridge.log(t);
                            }
                        }
                    }, 1500);
                }
            });
            XposedBridge.log("FixOw: quick center glass hooked");
        } catch (Throwable t) {
            XposedBridge.log("FixOw: quick center glass hook failed");
            XposedBridge.log(t);
        }
    }

    private void hookGlassControl(XC_LoadPackage.LoadPackageParam lp) {
        try {
            Class<?> launch = XposedHelpers.findClass("com.heytap.wearable.launcher.Launcher", lp.classLoader);
            XposedHelpers.findAndHookMethod(launch, "onCreate", android.os.Bundle.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam p) {
                    final android.app.Activity act = (android.app.Activity) p.thisObject;
                    sLauncherDecor = new WeakReference<View>(act.getWindow().getDecorView());
                }
            });
            XposedBridge.log("FixOw: control center glass hooked");
        } catch (Throwable t) {
            XposedBridge.log("FixOw: control center glass hook failed");
            XposedBridge.log(t);
        }
    }

    private static final Paint TILE_PAINT = new Paint(Paint.ANTI_ALIAS_FLAG);

    private static boolean inStatusPanel(View v) {
        ViewParent p = v.getParent();
        while (p instanceof View) {
            if (((View) p).getId() == ID_STATUS_PANEL) return true;
            p = ((View) p).getParent();
        }
        return false;
    }

    private static void restoreTile(View v) {
        int[] cd = ORIG_CD.remove(v);
        if (cd != null) {
            try {
                XposedHelpers.setIntField(v, "c", cd[0]);
                XposedHelpers.setIntField(v, "d", cd[1]);
            } catch (Throwable ignored) {
            }
        }
        restoreBg(v);
    }

    private void hookTiles(XC_LoadPackage.LoadPackageParam lp) {
        try {
            Class<?> cls = XposedHelpers.findClass(
                    "com.heytap.wearable.launcher.statusbar.view.LauncherToggleButton", lp.classLoader);
            XposedHelpers.findAndHookMethod(cls, "onDraw", Canvas.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam p) {
                    View v = (View) p.thisObject;
                    if (!inStatusPanel(v)) return;
                    if (!on("fixow_sw_tiles")) {
                        restoreTile(v);
                        return;
                    }
                    int w = v.getWidth(), h = v.getHeight();
                    if (w <= 0 || h <= 0) return;
                    if (w > h * 1.25f) {
                        try {
                            if (!ORIG_CD.containsKey(v)) {
                                ORIG_CD.put(v, new int[]{
                                        XposedHelpers.getIntField(v, "c"),
                                        XposedHelpers.getIntField(v, "d")});
                            }
                            XposedHelpers.setIntField(v, "c", 0);
                            XposedHelpers.setIntField(v, "d", 0);
                            setGlassBg(v, new GlassDrawable(null, 22, glassStyle(), true));
                            Drawable icon = (Drawable) XposedHelpers.getObjectField(v, "e");
                            if (icon != null) icon.setColorFilter(0xFF15181D,
                                    android.graphics.PorterDuff.Mode.SRC_IN);
                        } catch (Throwable t) {
                            XposedBridge.log(t);
                        }
                        return;
                    }
                    Canvas c = (Canvas) p.args[0];
                    try {
                        boolean checked = ((android.widget.CompoundButton) v).isChecked();
                        boolean enabled = XposedHelpers.getBooleanField(v, "h");
                        Drawable icon = (Drawable) XposedHelpers.getObjectField(v, "e");
                        float r = Math.min(w, h) * 0.22f;
                        TILE_PAINT.setShader(null);
                        TILE_PAINT.setStyle(Paint.Style.FILL);
                        TILE_PAINT.setColor(checked ? 0xCCFFFFFF : (enabled ? 0x1CFFFFFF : 0x10FFFFFF));
                        c.drawRoundRect(0, 0, w, h, r, r, TILE_PAINT);
                        if (icon != null) {
                            int s = Math.min(w, h);
                            int l = (w - s) / 2, t = (h - s) / 2;
                            icon.setBounds(l, t, l + s, t + s);
                            icon.setAlpha(enabled ? 255 : 102);
                            icon.setColorFilter(checked && enabled ? 0xFF15181D : 0xFFFFFFFF,
                                    android.graphics.PorterDuff.Mode.SRC_IN);
                            icon.draw(c);
                            icon.setColorFilter(null);
                        }
                    } catch (Throwable t) {
                        XposedBridge.log(t);
                    }
                    p.setResult(null);
                }

                @Override
                protected void afterHookedMethod(MethodHookParam p) {
                    View v = (View) p.thisObject;
                    if (!inStatusPanel(v)) return;
                    if (!on("fixow_sw_tiles")) return;
                    int w = v.getWidth(), h = v.getHeight();
                    if (w <= 0 || h <= 0 || w <= h * 1.25f) return;
                    try {
                        Drawable icon = (Drawable) XposedHelpers.getObjectField(v, "e");
                        if (icon != null) icon.setColorFilter(null);
                        int lvl = 0;
                        try {
                            lvl = XposedHelpers.getIntField(v, "i");
                        } catch (Throwable ignored) {
                        }
                        if (lvl > 0) {
                            int ww = Math.min(lvl, w);
                            float r = Math.min(w, h) * 0.22f;
                            Canvas c = (Canvas) p.args[0];
                            int save = c.save();
                            Path clip = new Path();
                            clip.addRoundRect(0, 0, w, h, r, r, Path.Direction.CW);
                            c.clipPath(clip);
                            TILE_PAINT.setShader(null);
                            TILE_PAINT.setStyle(Paint.Style.FILL);
                            TILE_PAINT.setColor(0xCCFFFFFF);
                            c.drawRect(0, 0, ww, h, TILE_PAINT);
                            c.restoreToCount(save);
                        }
                    } catch (Throwable t) {
                        XposedBridge.log(t);
                    }
                }
            });
            XposedBridge.log("FixOw: toggle tiles rebuild hooked");
        } catch (Throwable t) {
            XposedBridge.log("FixOw: toggle tiles hook failed");
            XposedBridge.log(t);
        }
    }

    private void hookBrightness(XC_LoadPackage.LoadPackageParam lp) {
        try {
            Class<?> cls = XposedHelpers.findClass("u5.b", lp.classLoader);
            XposedHelpers.findAndHookMethod(cls, "onDraw", Canvas.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam p) {
                    if (!on("fixow_sw_tiles")) return;
                    View v = (View) p.thisObject;
                    if (!inStatusPanel(v)) return;
                    Canvas c = (Canvas) p.args[0];
                    int w = v.getWidth(), h = v.getHeight();
                    if (w <= 0 || h <= 0) {
                        p.setResult(null);
                        return;
                    }
                    if (Math.abs(w - h) > Math.min(w, h) * 0.25f) return;
                    try {
                        boolean enabled = v.isEnabled();
                        float r = Math.min(w, h) * 0.22f;
                        TILE_PAINT.setShader(null);
                        TILE_PAINT.setColor(enabled ? 0x1CFFFFFF : 0x10FFFFFF);
                        c.drawRoundRect(0, 0, w, h, r, r, TILE_PAINT);
                        Drawable icon = (Drawable) XposedHelpers.getObjectField(v, "e");
                        if (icon != null) {
                            int s = Math.min(w, h);
                            int l = (w - s) / 2, t = (h - s) / 2;
                            icon.setBounds(l, t, l + s, t + s);
                            icon.setAlpha(enabled ? 255 : 102);
                            icon.setColorFilter(0xFFFFFFFF, android.graphics.PorterDuff.Mode.SRC_IN);
                            icon.draw(c);
                            icon.setColorFilter(null);
                        }
                    } catch (Throwable t) {
                        XposedBridge.log(t);
                    }
                    p.setResult(null);
                }
            });
            XposedBridge.log("FixOw: brightness tile rebuild hooked");
        } catch (Throwable t) {
            XposedBridge.log("FixOw: brightness tile hook failed");
            XposedBridge.log(t);
        }
    }

    private void hookVolumeBar(XC_LoadPackage.LoadPackageParam lp) {
        try {
            Class<?> cls = XposedHelpers.findClass(
                    "com.heytap.wearable.support.widget.VolumeSeekBar", lp.classLoader);
            XposedHelpers.findAndHookMethod(cls, "onDraw", Canvas.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam p) {
                    View v = (View) p.thisObject;
                    if (!on("fixow_sw_vol")) {
                        restoreBg(v);
                        return;
                    }
                    try {
                        android.graphics.drawable.Drawable bg = v.getBackground();
                        if (!(bg instanceof GlassDrawable)) {
                            setGlassBg(v, new GlassDrawable(null, 24, glassStyle(), true));
                        }
                    } catch (Throwable t) {
                        XposedBridge.log(t);
                    }
                }
            });
            XposedBridge.log("FixOw: volume bar glass hooked");
        } catch (Throwable t) {
            XposedBridge.log("FixOw: volume bar hook failed");
            XposedBridge.log(t);
        }
    }

    private void hookCardAdd(XC_LoadPackage.LoadPackageParam lp) {
        try {
            XposedHelpers.findAndHookMethod("android.view.ViewGroup", lp.classLoader,
                    "addView", View.class, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam p) {
                            View v = (View) p.args[0];
                            int id = v.getId();
                            if (id != 0x7f0a008f && id != 0x7f0a0236) return;
                            if (!on("fixow_sw_notif")) return;
                            ViewParent par = v.getParent();
                            boolean in = false;
                            while (par instanceof View) {
                                View pv = (View) par;
                                if (pv.getId() == 0x7f0a01af) {
                                    in = true;
                                    break;
                                }
                                par = pv.getParent();
                            }
                            if (!in) return;
                            if (id == 0x7f0a008f) {
                                setGlassBg(v, new GlassDrawable(null, 26, glassStyle(), true));
                            } else {
                                setGlassBg(v, new ColorDrawable(0x00000000));
                            }
                        }
                    });
            XposedBridge.log("FixOw: card add hook installed");
        } catch (Throwable t) {
            XposedBridge.log("FixOw: card add hook failed");
            XposedBridge.log(t);
        }
    }

    private void hookGlobalWindow(XC_LoadPackage.LoadPackageParam lp) {
        try {
            XposedHelpers.findAndHookMethod("android.view.WindowManagerImpl", lp.classLoader,
                    "addView", View.class, android.view.ViewGroup.LayoutParams.class, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam p) {
                            View v = (View) p.args[0];
                            if (v.getClass().getName().endsWith("GlobalWindowContainerView")) {
                                sGlobalContainer = v;
                                XposedBridge.log("FixOw: global container captured");
                            }
                        }
                    });
        } catch (Throwable t) {
            XposedBridge.log("FixOw: global window hook failed");
            XposedBridge.log(t);
        }
        hookPanel(lp, "com.heytap.wearable.launcher.statusbar.view.StatusPanelView");
        hookPanel(lp, "com.heytap.wearable.launcher.notification.view.NotificationPanelView");
    }

    private void hookPanel(XC_LoadPackage.LoadPackageParam lp, String clsName) {
        try {
            Class<?> cls = XposedHelpers.findClass(clsName, lp.classLoader);
            XposedHelpers.findAndHookMethod(cls, "onViewStartShowing", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam p) {
                    applyBackdrop((View) p.thisObject);
                }
            });
            XposedBridge.log("FixOw: panel hook installed " + clsName.substring(clsName.lastIndexOf('.') + 1));
        } catch (Throwable t) {
            XposedBridge.log("FixOw: panel hook failed " + clsName);
            XposedBridge.log(t);
        }
    }

    private static void applyBackdrop(final View panel) {
        panel.post(new Runnable() {
            @Override
            public void run() {
                try {
                    boolean bg = on("fixow_sw_bg");
                    Drawable clear = new ColorDrawable(0x00000000);
                    int[] clearIds = {0x7f0a024f, 0x7f0a0274, 0x7f0a0285, 0x7f0a0255};
                    if (bg) {
                        final int br = blurRadius(), st = glassStyle();
                        if (sBgCache != null && br == sBgCacheBlur && st == sBgCacheStyle) {
                            setGlassBg(panel, new GlassDrawable(sBgCache, 0, st));
                        } else {
                            View wf = null;
                            View decor = sLauncherDecor.get();
                            if (decor != null) wf = decor.findViewById(ID_WATCHFACE);
                            Bitmap shot = wf == null ? null : snapshot(wf);
                            if (shot != null && isMostlyDark(shot)) shot = null;
                            if (shot != null) sLastWf = shot;
                            else shot = sLastWf;
                            final Bitmap src = shot;
                            new Thread(new Runnable() {
                                @Override
                                public void run() {
                                    Bitmap base = null;
                                    try {
                                        base = src == null ? null : blur(src, br);
                                    } catch (Throwable ignored) {
                                    }
                                    if (base == null) base = fallbackBase();
                                    final Bitmap fb = base;
                                    panel.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            if (fb == null) return;
                                            sBgCache = fb;
                                            sBgCacheBlur = br;
                                            sBgCacheStyle = st;
                                            if (on("fixow_sw_bg")) {
                                                setGlassBg(panel, new GlassDrawable(fb, 0, st));
                                            }
                                        }
                                    });
                                }
                            }).start();
                        }
                        for (int id : clearIds) {
                            View v = panel.findViewById(id);
                            if (v != null) setGlassBg(v, clear);
                        }
                        if (panel instanceof android.view.ViewGroup && panel.getId() == 0x7f0a01af) {
                            android.view.ViewGroup g = (android.view.ViewGroup) panel;
                            for (int i = 0; i < g.getChildCount(); i++) {
                                View c = g.getChildAt(i);
                                if (c.getClass().getName().contains("Recycler")) continue;
                                setGlassBg(c, clear);
                            }
                        }
                    } else {
                        restoreBg(panel);
                        for (int id : clearIds) {
                            View v = panel.findViewById(id);
                            if (v != null) restoreBg(v);
                        }
                        if (panel instanceof android.view.ViewGroup && panel.getId() == 0x7f0a01af) {
                            android.view.ViewGroup g = (android.view.ViewGroup) panel;
                            for (int i = 0; i < g.getChildCount(); i++) {
                                View c = g.getChildAt(i);
                                if (c.getClass().getName().contains("Recycler")) continue;
                                restoreBg(c);
                            }
                        }
                    }
                    if (panel instanceof android.view.ViewGroup && panel.getId() == 0x7f0a01af) {
                        styleNotification(panel, 0);
                        styleNotification(panel, 400);
                        styleNotification(panel, 1200);
                    }
                    XposedBridge.log("FixOw: panel glass applied (bg=" + bg + ")");
                } catch (Throwable t) {
                    XposedBridge.log(t);
                }
            }
        });
    }

    private static void styleNotification(final View panel, int delay) {
        Runnable r = new Runnable() {
            @Override
            public void run() {
                try {
                    boolean notif = on("fixow_sw_notif");
                    Drawable clear = new ColorDrawable(0x00000000);
                    List<View> wrappers = new ArrayList<>();
                    collectById(panel, 0x7f0a0236, wrappers);
                    List<View> cards = new ArrayList<>();
                    collectById(panel, 0x7f0a008f, cards);
                    View del = panel.findViewById(0x7f0a0073);
                    if (!notif) {
                        for (View w : wrappers) restoreBg(w);
                        for (View card : cards) restoreBg(card);
                        if (del != null) restoreBg(del);
                        return;
                    }
                    for (View w : wrappers) setGlassBg(w, clear);
                    for (View card : cards) setGlassBg(card, new GlassDrawable(null, 26, glassStyle(), true));
                    if (del != null) setGlassBg(del, new GlassDrawable(null, 30, glassStyle(), true));
                } catch (Throwable t) {
                    XposedBridge.log(t);
                }
            }
        };
        if (delay <= 0) r.run();
        else panel.postDelayed(r, delay);
    }

    private void hookGlassHealth(XC_LoadPackage.LoadPackageParam lp) {
        try {
            Class<?> cls = XposedHelpers.findClass(
                    "com.heytap.wearable.healthcenter.view.widget.NoLimitViewPager", lp.classLoader);
            XC_MethodHook cb = new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam p) {
                    final View pager = (View) p.thisObject;
                    pager.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                View root = pager;
                                ViewParent par;
                                while ((par = root.getParent()) instanceof View) root = (View) par;
                                if (!on("fixow_glass_hc")) {
                                    restoreSubtree(root);
                                    return;
                                }
                                Bitmap base = photoBase();
                                if (base != null) base = blur(base, blurRadius());
                                if (base == null) base = wallpaperBlur();
                                if (base == null) base = frostBase();
                                setGlassBg(root, new GlassDrawable(base, 34, glassStyle()));
                                setGlassBg(pager, null);
                                int bn = applyHealthButtons(pager);
                                XposedBridge.log("FixOw: health center glass applied (base=" + (base != null) + ", buttons=" + bn + ")");
                            } catch (Throwable t) {
                                XposedBridge.log(t);
                            }
                        }
                    }, 800);
                }
            };
            XposedHelpers.findAndHookMethod(cls, "onAttachedToWindow", cb);
            try {
                XposedHelpers.findAndHookMethod(cls, "dispatchOnPageSelected", int.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam p) {
                        final View pager = (View) p.thisObject;
                        if (!on("fixow_glass_hc")) return;
                        pager.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                applyHealthButtons(pager);
                            }
                        }, 300);
                    }
                });
            } catch (Throwable t) {
                XposedBridge.log(t);
            }
            XposedBridge.log("FixOw: health center glass hooked");
        } catch (Throwable t) {
            XposedBridge.log("FixOw: health center glass hook failed");
            XposedBridge.log(t);
        }
    }

    private void hookKeyguardPin(XC_LoadPackage.LoadPackageParam lp) {
        try {
            Class<?> cls = XposedHelpers.findClass(
                    "com.heytap.wearable.keyguard.security.ColorNumericKeyboard", lp.classLoader);
            XposedHelpers.findAndHookMethod(cls, "a", Context.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam p) {
                    final android.view.ViewGroup g = (android.view.ViewGroup) p.thisObject;
                    g.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            boolean lock = on("fixow_sw_lock");
                            boolean lockBg = off("fixow_sw_lock_bg");
                            if (!lock && !lockBg) return;
                            if (lock) stylePinChildren(g, 0);
                            try {
                                View root = g;
                                ViewParent par;
                                while ((par = root.getParent()) instanceof View) root = (View) par;
                                if (lockBg) {
                                    Bitmap base = photoBase();
                                    if (base != null) base = blur(base, blurRadius());
                                    if (base == null) base = wallpaperBlur();
                                    if (base == null) base = frostBase();
                                    setGlassBg(root, new GlassDrawable(base, 0, glassStyle()));
                                    Drawable clear = new ColorDrawable(0x00000000);
                                    List<View> all = new ArrayList<>();
                                    collectAll(root, all);
                                    for (View cv : all) {
                                        if (cv.getBackground() instanceof GlassDrawable) continue;
                                        String cn = cv.getClass().getName();
                                        if (cn.startsWith("com.heytap.wearable.keyguard")
                                                || cn.equals("android.view.View")
                                                || cn.equals("android.widget.FrameLayout")
                                                || cn.equals("android.widget.RelativeLayout")
                                                || cn.equals("android.widget.LinearLayout")
                                                || cn.contains("CustomKeyguardPINView")
                                                || cn.contains("ColorNumericKeyboard")) {
                                            setGlassBg(cv, clear);
                                        }
                                    }
                                } else {
                                    restoreSubtree(root);
                                }
                            } catch (Throwable t) {
                                XposedBridge.log(t);
                            }
                            XposedBridge.log("FixOw: keyguard pin glass applied");
                        }
                    }, 400);
                }
            });
            XposedBridge.log("FixOw: keyguard pin hooked");
        } catch (Throwable t) {
            XposedBridge.log("FixOw: keyguard pin hook failed");
            XposedBridge.log(t);
        }
    }

    private static void stylePinChildren(android.view.ViewGroup g, int depth) {
        if (depth > 2) return;
        for (int i = 0; i < g.getChildCount(); i++) {
            View c = g.getChildAt(i);
            boolean style = false;
            if (c instanceof android.widget.TextView) {
                CharSequence t = ((android.widget.TextView) c).getText();
                if (t != null) {
                    String s = t.toString().trim();
                    if (s.length() == 1 && Character.isDigit(s.charAt(0))) style = true;
                }
            }
            if (style) setGlassBg(c, new GlassDrawable(null, 28, glassStyle(), true));
            if (c instanceof android.view.ViewGroup) stylePinChildren((android.view.ViewGroup) c, depth + 1);
        }
    }

    private void hookSbnIcon(XC_LoadPackage.LoadPackageParam lp) {
        try {
            XposedHelpers.findAndHookMethod(StatusBarNotification.class, "getNotification",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam p) {
                            if (!on("fixow_icon")) return;
                            Notification n = (Notification) p.getResult();
                            if (n == null || n.getLargeIcon() != null) return;
                            StatusBarNotification sbn = (StatusBarNotification) p.thisObject;
                            Bitmap b = appIcon(sbn.getPackageName());
                            if (b != null) {
                                XposedHelpers.setObjectField(n, "mLargeIcon", Icon.createWithBitmap(b));
                                XposedBridge.log("FixOw: injected largeIcon for " + sbn.getPackageName());
                            }
                        }
                    });
            XposedBridge.log("FixOw: sbn.getNotification icon fallback hooked");
        } catch (Throwable t) {
            XposedBridge.log("FixOw: sbn icon hook failed");
            XposedBridge.log(t);
        }
    }

    private static Context app() {
        try {
            Class<?> at = Class.forName("android.app.ActivityThread");
            return (Context) at.getMethod("currentApplication").invoke(null);
        } catch (Throwable t) {
            return null;
        }
    }

    private static final String[] CFG_KEYS = {
            "fixow_bypass", "fixow_icon", "fixow_sw_tiles", "fixow_sw_bg",
            "fixow_sw_notif", "fixow_sw_detail", "fixow_sw_vol", "fixow_glass_hc",
            "fixow_sw_lock", "fixow_sw_lock_bg", "fixow_blur", "fixow_style"};
    private static final java.util.HashMap<String, Integer> CFG = new java.util.HashMap<>();
    private static long CFG_TS;

    private static int cfg(String key, int def) {
        long now = android.os.SystemClock.elapsedRealtime();
        synchronized (CFG) {
            if (now - CFG_TS > 1000) {
                Context app = app();
                if (app != null) {
                    for (String k : CFG_KEYS) {
                        int d = k.equals("fixow_sw_lock_bg") ? 0
                                : (k.equals("fixow_blur") ? 16 : (k.equals("fixow_style") ? 0 : 1));
                        try {
                            CFG.put(k, Settings.Secure.getInt(app.getContentResolver(), k, d));
                        } catch (Throwable ignored) {
                        }
                    }
                }
                CFG_TS = now;
            }
            Integer v = CFG.get(key);
            return v == null ? def : v;
        }
    }

    private static boolean on(String key) {
        return cfg(key, 1) == 1;
    }

    private static boolean off(String key) {
        return cfg(key, 0) == 1;
    }

    private void hookWhitelist(XC_LoadPackage.LoadPackageParam lp) {
        try {
            XposedHelpers.findAndHookMethod("w4.y", lp.classLoader, "b",
                    StatusBarNotification.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam p) {
                            if (on("fixow_bypass")) p.setResult(true);
                        }
                    });
            XposedBridge.log("FixOw: whitelist gate w4.y.b hooked");
        } catch (Throwable t) {
            XposedBridge.log("FixOw: whitelist hook failed");
            XposedBridge.log(t);
        }
    }

    private void hookDetailCard(XC_LoadPackage.LoadPackageParam lp) {
        try {
            Class<?> cls = XposedHelpers.findClass("e5.c", lp.classLoader);
            XposedHelpers.findAndHookMethod(cls, "G", Context.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam p) {
                    if (!on("fixow_sw_detail")) return;
                    try {
                        Object self = p.thisObject;
                        View card = (View) XposedHelpers.getObjectField(self, "u");
                        if (card == null) return;
                        int st = glassStyle();
                        card.setBackground(new GlassDrawable(null, 30, st, true));
                        View bg = card.findViewById(0x7f090285);
                        if (bg != null) bg.setBackground(new ColorDrawable(0x00000000));
                        View btn = card.findViewById(0x7f09005a);
                        if (btn != null) btn.setBackground(new GlassDrawable(null, 24, st, true));
                        XposedBridge.log("FixOw: detail card glass applied");
                    } catch (Throwable t) {
                        XposedBridge.log(t);
                    }
                }
            });
            XposedBridge.log("FixOw: detail card hooked");
        } catch (Throwable t) {
            XposedBridge.log("FixOw: detail card hook failed");
            XposedBridge.log(t);
        }
    }

    private void hookIcon(XC_LoadPackage.LoadPackageParam lp) {
        try {
            Class<?> item = XposedHelpers.findClass(
                    "com.heytap.wearable.systemui.notification.sdk.bean.NotificationItem", lp.classLoader);
            XposedHelpers.findAndHookMethod(
                    "com.heytap.wearable.systemui.notification.sdk.internal.HistoryNotificationImpl",
                    lp.classLoader, "handleHistoryUpdate", item, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam p) {
                            if (on("fixow_icon")) fillIcon(p.args[0]);
                        }
                    });
            XposedBridge.log("FixOw: launcher icon fallback hooked");
        } catch (Throwable t) {
            XposedBridge.log("FixOw: icon hook failed");
            XposedBridge.log(t);
        }
    }

    private static void fillIcon(Object item) {
        try {
            Notification n = (Notification) XposedHelpers.callMethod(item, "getNotification");
            if (n == null || n.getLargeIcon() != null) return;
            String pkg = (String) XposedHelpers.callMethod(item, "getPackageName");
            Bitmap b = appIcon(pkg);
            if (b != null) XposedHelpers.setObjectField(n, "mLargeIcon", Icon.createWithBitmap(b));
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    private static Bitmap appIcon(String pkg) {
        synchronized (ICONS) {
            Bitmap c = ICONS.get(pkg);
            if (c != null) return c;
        }
        Context app = app();
        if (app == null || pkg == null) return null;
        try {
            Drawable d = app.getPackageManager().getApplicationIcon(pkg);
            Bitmap b = toBitmap(d);
            if (b != null) synchronized (ICONS) { ICONS.put(pkg, b); }
            return b;
        } catch (Throwable t) {
            return null;
        }
    }

    private static Bitmap toBitmap(Drawable d) {
        if (d instanceof BitmapDrawable) return ((BitmapDrawable) d).getBitmap();
        int w = Math.max(1, d.getIntrinsicWidth());
        int h = Math.max(1, d.getIntrinsicHeight());
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        d.setBounds(0, 0, w, h);
        d.draw(c);
        return b;
    }

    private static Bitmap snapshot(View v) {
        try {
            int w = v.getWidth(), h = v.getHeight();
            if (w <= 0 || h <= 0) return null;
            Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            v.draw(new Canvas(b));
            return b;
        } catch (Throwable t) {
            return null;
        }
    }

    private static Bitmap wallpaperBlur() {
        try {
            Context app = app();
            if (app == null) return null;
            Drawable d = WallpaperManager.getInstance(app).getDrawable();
            return d == null ? null : blur(toBitmap(d), 24);
        } catch (Throwable t) {
            return null;
        }
    }

    private static int blurRadius() {
        Context app = app();
        if (app == null) return 16;
        try {
            return cfg("fixow_blur", 16);
        } catch (Throwable t) {
            return 16;
        }
    }

    private static int glassStyle() {
        Context app = app();
        if (app == null) return 0;
        try {
            return cfg("fixow_style", 0);
        } catch (Throwable t) {
            return 0;
        }
    }

    private static Bitmap photoBase() {
        if (sPhotoCache != null) return sPhotoCache;
        Context app = app();
        if (app == null) return null;
        try {
            String s = Settings.Secure.getString(app.getContentResolver(), "fixow_bg");
            if (s == null || s.length() == 0) return null;
            byte[] d = Base64.decode(s, Base64.DEFAULT);
            Bitmap b = BitmapFactory.decodeByteArray(d, 0, d.length);
            sPhotoCache = b;
            return b;
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean isMostlyDark(Bitmap b) {
        try {
            int w = b.getWidth(), h = b.getHeight();
            int sx = Math.max(1, w / 8), sy = Math.max(1, h / 8);
            long sum = 0;
            int n = 0;
            for (int y = 0; y < h; y += sy) {
                for (int x = 0; x < w; x += sx) {
                    int c = b.getPixel(x, y);
                    sum += (((c >> 16) & 0xff) + ((c >> 8) & 0xff) + (c & 0xff)) / 3;
                    n++;
                }
            }
            return n > 0 && (sum / n) < 24;
        } catch (Throwable t) {
            return false;
        }
    }

    private static Bitmap fallbackBase() {
        Bitmap b = photoBase();
        if (b != null) return blur(b, blurRadius());
        return frostBase();
    }

    private static Bitmap frostBase() {
        try {
            int w = 96, h = 126;
            Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            c.drawColor(0xFF12151B);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setShader(new android.graphics.RadialGradient(w * 0.15f, h * 0.12f, w * 1.0f,
                    new int[]{0xAA2E7CF6, 0x00000000}, null, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, p);
            p.setShader(new android.graphics.RadialGradient(w * 0.95f, h * 0.55f, w * 0.9f,
                    new int[]{0x887A5CFF, 0x00000000}, null, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, p);
            p.setShader(new android.graphics.RadialGradient(w * 0.5f, h * 1.0f, w * 0.8f,
                    new int[]{0x6617C3B2, 0x00000000}, null, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, p);
            p.setShader(null);
            return blur(b, 16);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Bitmap blur(Bitmap src, int radius) {
        if (src == null) return null;
        try {
            int w = Math.max(1, src.getWidth() / 2);
            int h = Math.max(1, src.getHeight() / 2);
            Bitmap s = Bitmap.createScaledBitmap(src, w, h, true);
            int[] px = new int[w * h];
            s.getPixels(px, 0, w, 0, 0, w, h);
            int r = Math.max(1, radius / 2);
            boxBlur(px, w, h, r);
            boxBlur(px, w, h, r);
            boxBlur(px, w, h, r);
            s.setPixels(px, 0, w, 0, 0, w, h);
            return s;
        } catch (Throwable t) {
            return null;
        }
    }

    private static void boxBlur(int[] p, int w, int h, int r) {
        int[] tmp = new int[p.length];
        for (int y = 0; y < h; y++) {
            int idx = y * w, n = 0;
            long a = 0, rr = 0, gg = 0, bb = 0;
            for (int x = -r; x <= r; x++) {
                if (x >= 0 && x < w) {
                    int c = p[idx + x];
                    a += c >>> 24; rr += (c >> 16) & 0xff; gg += (c >> 8) & 0xff; bb += c & 0xff; n++;
                }
            }
            for (int x = 0; x < w; x++) {
                tmp[idx + x] = ((int) (a / n) << 24) | ((int) (rr / n) << 16) | ((int) (gg / n) << 8) | (int) (bb / n);
                int xo = x - r, xi = x + r + 1;
                if (xo >= 0) { int c = p[idx + xo]; a -= c >>> 24; rr -= (c >> 16) & 0xff; gg -= (c >> 8) & 0xff; bb -= c & 0xff; n--; }
                if (xi < w) { int c = p[idx + xi]; a += c >>> 24; rr += (c >> 16) & 0xff; gg += (c >> 8) & 0xff; bb += c & 0xff; n++; }
            }
        }
        for (int x = 0; x < w; x++) {
            int n = 0;
            long a = 0, rr = 0, gg = 0, bb = 0;
            for (int y = -r; y <= r; y++) {
                if (y >= 0 && y < h) {
                    int c = tmp[y * w + x];
                    a += c >>> 24; rr += (c >> 16) & 0xff; gg += (c >> 8) & 0xff; bb += c & 0xff; n++;
                }
            }
            for (int y = 0; y < h; y++) {
                p[y * w + x] = ((int) (a / n) << 24) | ((int) (rr / n) << 16) | ((int) (gg / n) << 8) | (int) (bb / n);
                int yo = y - r, yi = y + r + 1;
                if (yo >= 0) { int c = tmp[yo * w + x]; a -= c >>> 24; rr -= (c >> 16) & 0xff; gg -= (c >> 8) & 0xff; bb -= c & 0xff; n--; }
                if (yi < h) { int c = tmp[yi * w + x]; a += c >>> 24; rr += (c >> 16) & 0xff; gg += (c >> 8) & 0xff; bb += c & 0xff; n++; }
            }
        }
    }


    private static Object tileState() {
        try {
            Context app = app();
            android.content.res.Resources r = app.getPackageManager()
                    .getResourcesForApplication("cn.classfruit.fixow");
            android.graphics.drawable.StateListDrawable s = new android.graphics.drawable.StateListDrawable();
            s.addState(new int[]{android.R.attr.state_checked}, r.getDrawable(R.drawable.x2_tile_select, null));
            s.addState(new int[]{}, r.getDrawable(R.drawable.x2_tile_normal, null));
            return s;
        } catch (Throwable t) {
            XposedBridge.log(t);
            return null;
        }
    }

    private static final java.util.WeakHashMap<View, Drawable> ORIG_BG = new java.util.WeakHashMap<>();
    private static final java.util.WeakHashMap<View, int[]> ORIG_CD = new java.util.WeakHashMap<>();

    private static void setGlassBg(View v, Drawable d) {
        if (!ORIG_BG.containsKey(v)) ORIG_BG.put(v, v.getBackground());
        v.setBackground(d);
    }

    private static void restoreBg(View v) {
        if (ORIG_BG.containsKey(v)) v.setBackground(ORIG_BG.remove(v));
    }

    private static void restoreSubtree(View root) {
        restoreBg(root);
        if (root instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) root;
            for (int i = 0; i < g.getChildCount(); i++) restoreSubtree(g.getChildAt(i));
        }
    }

    private static void collectAll(View v, List<View> out) {
        out.add(v);
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) collectAll(g.getChildAt(i), out);
        }
    }

    private static int applyHealthButtons(View pager) {
        int n = 0;
        for (int id : HEALTH_BTNS) {
            List<View> l = new ArrayList<>();
            collectById(pager, id, l);
            for (View b : l) {
                setGlassBg(b, new GlassDrawable(null, 24, glassStyle(), true));
                n++;
            }
        }
        List<View> all = new ArrayList<>();
        collectAll(pager, all);
        for (View v : all) {
            if (v instanceof android.widget.TextView) {
                CharSequence t = ((android.widget.TextView) v).getText();
                if (t == null) continue;
                String s = t.toString().trim();
                for (String want : HEALTH_BTN_TEXTS) {
                    if (s.equals(want)) {
                        setGlassBg(v, new GlassDrawable(null, 24, glassStyle(), true));
                        n++;
                        break;
                    }
                }
            }
        }
        return n;
    }

    private static void collectById(View v, int id, List<View> out) {
        if (v.getId() == id) out.add(v);
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) collectById(g.getChildAt(i), id, out);
        }
    }

    static class GlassDrawable extends Drawable {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint tint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final RectF rf = new RectF();
        private final float radius;
        private final int style;
        private final boolean translucent;
        private BitmapShader shader;
        private int baseW, baseH;
        private final android.graphics.Matrix matrix = new android.graphics.Matrix();

        GlassDrawable(Bitmap base, float radius, int style) {
            this(base, radius, style, false);
        }

        GlassDrawable(Bitmap base, float radius, int style, boolean translucent) {
            this.radius = radius;
            this.style = style;
            this.translucent = translucent;
            fill.setFilterBitmap(true);
            fill.setDither(true);
            if (base != null) {
                baseW = base.getWidth();
                baseH = base.getHeight();
                shader = new BitmapShader(base, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                fill.setShader(shader);
            } else {
                fill.setColor(translucent ? 0x1CFFFFFF : (style == 1 ? 0xCC0E1116 : 0xCC161A21));
            }
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(translucent ? 1.5f : 2f);
            stroke.setColor(translucent ? 0x2EFFFFFF : (style == 2 ? 0x33FFFFFF : 0x66FFFFFF));
        }

        @Override
        public void draw(Canvas c) {
            Rect b = getBounds();
            rf.set(b);
            path.reset();
            path.addRoundRect(rf, radius, radius, Path.Direction.CW);
            c.save();
            c.clipPath(path);
            if (shader != null && baseW > 0 && baseH > 0) {
                matrix.reset();
                matrix.setScale(b.width() / (float) baseW, b.height() / (float) baseH);
                shader.setLocalMatrix(matrix);
            }
            if (fill.getShader() != null) c.drawRoundRect(rf, radius, radius, fill);
            else c.drawRect(rf, fill);
            tint.setShader(new LinearGradient(b.left, b.top, b.left, b.bottom,
                    translucent
                            ? new int[]{0x10FFFFFF, 0x05FFFFFF, 0x00000000, 0x08000000}
                            : style == 1
                            ? new int[]{0x22000000, 0x0A000000, 0x00000000, 0x33000000}
                            : style == 2
                            ? new int[]{0x14FFFFFF, 0x08FFFFFF, 0x00000000, 0x11000000}
                            : new int[]{0x3DFFFFFF, 0x14FFFFFF, 0x0AFFFFFF, 0x22000000},
                    new float[]{0f, 0.35f, 0.7f, 1f}, Shader.TileMode.CLAMP));
            c.drawRect(rf, tint);
            tint.setShader(null);
            c.restore();
            c.drawRoundRect(rf, radius, radius, stroke);
        }

        @Override
        public void setAlpha(int alpha) {
        }

        @Override
        public void setColorFilter(ColorFilter cf) {
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }


}