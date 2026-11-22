package cn.classfruit.fixow;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Base64;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public class MainActivity extends Activity {
    private static final int REQ_PICK = 1;
    private static final String K_BG = "fixow_bg";
    private static final String K_BLUR = "fixow_blur";
    private static final String K_STYLE = "fixow_style";
    private Button btnStyle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Switch swBypass = findViewById(R.id.sw_bypass);
        Switch swIcon = findViewById(R.id.sw_icon);
        Switch swTiles = findViewById(R.id.sw_tiles);
        Switch swBg = findViewById(R.id.sw_bg);
        Switch swNotif = findViewById(R.id.sw_notif);
        Switch swDetail = findViewById(R.id.sw_detail);
        Switch swVol = findViewById(R.id.sw_vol);
        Switch swGlassHc = findViewById(R.id.sw_glass_hc);
        Switch swLock = findViewById(R.id.sw_lock);
        Switch swLockBg = findViewById(R.id.sw_lock_bg);
        swBypass.setChecked(read("fixow_bypass"));
        swIcon.setChecked(read("fixow_icon"));
        swTiles.setChecked(read("fixow_sw_tiles"));
        swBg.setChecked(read("fixow_sw_bg"));
        swNotif.setChecked(read("fixow_sw_notif"));
        swDetail.setChecked(read("fixow_sw_detail"));
        swVol.setChecked(read("fixow_sw_vol"));
        swGlassHc.setChecked(read("fixow_glass_hc"));
        swLock.setChecked(read("fixow_sw_lock"));
        swLockBg.setChecked(Settings.Secure.getInt(getContentResolver(), "fixow_sw_lock_bg", 0) == 1);
        swBypass.setOnCheckedChangeListener((b, v) -> write("fixow_bypass", v));
        swIcon.setOnCheckedChangeListener((b, v) -> write("fixow_icon", v));
        swTiles.setOnCheckedChangeListener((b, v) -> write("fixow_sw_tiles", v));
        swBg.setOnCheckedChangeListener((b, v) -> write("fixow_sw_bg", v));
        swNotif.setOnCheckedChangeListener((b, v) -> write("fixow_sw_notif", v));
        swDetail.setOnCheckedChangeListener((b, v) -> write("fixow_sw_detail", v));
        swVol.setOnCheckedChangeListener((b, v) -> write("fixow_sw_vol", v));
        swGlassHc.setOnCheckedChangeListener((b, v) -> write("fixow_glass_hc", v));
        swLock.setOnCheckedChangeListener((b, v) -> write("fixow_sw_lock", v));
        swLockBg.setOnCheckedChangeListener((b, v) -> write("fixow_sw_lock_bg", v));

        bind0(R.id.sw_ntf_canpost, "fixow_ntf_canpost");
        bind0(R.id.sw_ntf_nomode, "fixow_ntf_nomode");
        bind0(R.id.sw_ntf_nomute, "fixow_ntf_nomute");
        bind0(R.id.sw_ntf_offwrist, "fixow_ntf_offwrist");
        bind0(R.id.sw_ntf_keepopen, "fixow_ntf_keepopen");
        bind0(R.id.sw_ntf_stale, "fixow_ntf_stale");
        bind0(R.id.sw_ntf_nosellclear, "fixow_ntf_nosellclear");
        bind0(R.id.sw_pm_nopoweroff, "fixow_pm_nopoweroff");
        bindCycle(R.id.btn_pm_shutdown, "fixow_pm_shutdown", getString(R.string.btn_pm_shutdown));
        bindCycle(R.id.btn_pm_restart, "fixow_pm_restart", getString(R.string.btn_pm_restart));
        bindCycle(R.id.btn_pm_smart, "fixow_pm_smart", getString(R.string.btn_pm_smart));
        bindCycle(R.id.btn_pm_sos, "fixow_pm_sos", getString(R.string.btn_pm_sos));
        bind0(R.id.sw_aod_color, "fixow_aod_color");
        bind0(R.id.sw_norlowbatt, "fixow_norlowbatt");

        seedBg();

        Button btnBg = findViewById(R.id.btn_bg);
        btnBg.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_GET_CONTENT);
            i.setType("image/*");
            i.addCategory(Intent.CATEGORY_OPENABLE);
            try {
                startActivityForResult(Intent.createChooser(i, null), REQ_PICK);
            } catch (Throwable t) {
                Toast.makeText(this, "没有可用的图片选择器", Toast.LENGTH_SHORT).show();
            }
        });

        SeekBar sb = findViewById(R.id.sb_blur);
        sb.setProgress(Settings.Secure.getInt(getContentResolver(), K_BLUR, 16));
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
                try {
                    Settings.Secure.putInt(getContentResolver(), K_BLUR, s.getProgress());
                } catch (SecurityException e) {
                    toastPerm();
                }
            }
        });

        btnStyle = findViewById(R.id.btn_style);
        updateStyleLabel();
        btnStyle.setOnClickListener(v -> {
            int st = (Settings.Secure.getInt(getContentResolver(), K_STYLE, 0) + 1) % 3;
            try {
                Settings.Secure.putInt(getContentResolver(), K_STYLE, st);
            } catch (SecurityException e) {
                toastPerm();
            }
            updateStyleLabel();
        });

        Button test = findViewById(R.id.btn_test);
        test.setOnClickListener(v -> fireTest());

        TextView status = findViewById(R.id.tv_status);
        status.setText("作用域：HeySystemUI（白名单/通知规则/电源菜单）+ Launcher（图标兜底、控制中心玻璃）+ 健康中心。\n"
                + "「SystemUI 行为」和「玻璃 UI」两组默认全部关闭，打开后下一次事件生效。\n"
                + "新增 hook 需要 SystemUI/Launcher 重启后才会装载，改完开关若没反应请重启手表。\n"
                + "测试通知不带 largeIcon，用于验证图标兜底。");
    }

    private static final String[] PM_MODES = {"始终显示", "仅解锁显示", "仅锁屏显示", "始终隐藏"};

    private void bindCycle(int id, final String key, final String label) {
        final Button b = findViewById(id);
        b.setText(label + PM_MODES[readInt(key, 0)]);
        b.setOnClickListener(v -> {
            int m = (readInt(key, 0) + 1) % PM_MODES.length;
            writeInt(key, m);
            b.setText(label + PM_MODES[m]);
        });
    }

    private int readInt(String key, int def) {
        try {
            return Settings.Secure.getInt(getContentResolver(), key, def);
        } catch (Throwable t) {
            return def;
        }
    }

    private void writeInt(String key, int v) {
        try {
            Settings.Secure.putInt(getContentResolver(), key, v);
        } catch (SecurityException e) {
            toastPerm();
        }
    }

    private void bind0(int id, String key) {
        Switch sw = findViewById(id);
        sw.setChecked(Settings.Secure.getInt(getContentResolver(), key, 0) == 1);
        sw.setOnCheckedChangeListener((b, v) -> write(key, v));
    }

    private void updateStyleLabel() {
        int st = Settings.Secure.getInt(getContentResolver(), K_STYLE, 0);
        String[] names = {"浅色玻璃", "深色玻璃", "极简"};
        if (btnStyle != null) btnStyle.setText("风格：" + names[st % 3]);
    }

    private void seedBg() {
        try {
            if (Settings.Secure.getString(getContentResolver(), K_BG) != null) return;
            Bitmap b = BitmapFactory.decodeResource(getResources(), R.drawable.bg_default);
            if (b == null) return;
            saveBg(centerCrop(b, 189, 248));
        } catch (Throwable ignored) {
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_PICK || res != RESULT_OK || data == null || data.getData() == null) return;
        try {
            InputStream in = getContentResolver().openInputStream(data.getData());
            Bitmap b = BitmapFactory.decodeStream(in);
            if (in != null) in.close();
            if (b == null) {
                Toast.makeText(this, "图片解码失败", Toast.LENGTH_SHORT).show();
                return;
            }
            saveBg(centerCrop(b, 189, 248));
            Toast.makeText(this, "背景已更新", Toast.LENGTH_SHORT).show();
        } catch (Throwable t) {
            Toast.makeText(this, "设置失败: " + t, Toast.LENGTH_LONG).show();
        }
    }

    private void saveBg(Bitmap b) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        b.compress(Bitmap.CompressFormat.JPEG, 85, o);
        String s = Base64.encodeToString(o.toByteArray(), Base64.NO_WRAP);
        Settings.Secure.putString(getContentResolver(), K_BG, s);
    }

    private static Bitmap centerCrop(Bitmap s, int tw, int th) {
        int w = s.getWidth(), h = s.getHeight();
        float sc = Math.max((float) tw / w, (float) th / h);
        int nw = Math.round(w * sc), nh = Math.round(h * sc);
        Bitmap r = Bitmap.createScaledBitmap(s, nw, nh, true);
        int l = (nw - tw) / 2, t = (nh - th) / 2;
        return Bitmap.createBitmap(r, l, t, tw, th);
    }

    private void toastPerm() {
        Toast.makeText(this, "未授予 WRITE_SECURE_SETTINGS，设置无效", Toast.LENGTH_LONG).show();
    }

    private boolean read(String key) {
        return Settings.Secure.getInt(getContentResolver(), key, 1) == 1;
    }

    private void write(String key, boolean on) {
        try {
            Settings.Secure.putInt(getContentResolver(), key, on ? 1 : 0);
        } catch (SecurityException e) {
            toastPerm();
        }
    }

    private void fireTest() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(
                "fixow_test", "FixOwTest", NotificationManager.IMPORTANCE_DEFAULT));
        Notification n = new Notification.Builder(this, "fixow_test")
                .setSmallIcon(R.drawable.ic_fixow)
                .setContentTitle("FixOw 测试通知")
                .setContentText("非白名单包 cn.classfruit.fixow")
                .build();
        nm.notify(2, n);
        Toast.makeText(this, "已投递，下拉通知中心查看", Toast.LENGTH_SHORT).show();
    }
}