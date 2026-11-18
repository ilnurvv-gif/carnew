package local.headunit.swc;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Build;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.widget.Toast;

/** Список действий и их выполнение. */
public final class Actions {
    public static final String APP_PREFIX = "app:";

    public static final String[] IDS = {
            "media_next", "media_prev", "media_play_pause", "media_stop",
            "vol_up", "vol_down", "mute",
            "home", "back", "recents", "notifications",
            "block", "app_pick"
    };
    public static final String[] LABELS = {
            "Следующий трек", "Предыдущий трек", "Пауза / Воспроизведение", "Стоп",
            "Громкость +", "Громкость −", "Без звука",
            "Домой", "Назад", "Недавние приложения", "Шторка уведомлений",
            "Ничего не делать (заблокировать кнопку)", "Открыть приложение…"
    };

    private Actions() {}

    public static String label(Context c, String id) {
        if (id == null) return "—";
        if (id.startsWith(APP_PREFIX)) {
            String pkg = id.substring(APP_PREFIX.length());
            try {
                PackageManager pm = c.getPackageManager();
                ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
                return "Открыть «" + pm.getApplicationLabel(ai) + "»";
            } catch (Exception e) {
                return "Открыть " + pkg;
            }
        }
        for (int i = 0; i < IDS.length; i++) if (IDS[i].equals(id)) return LABELS[i];
        return id;
    }

    /** Действия, которые можно повторять, пока кнопка зажата. */
    public static boolean repeatable(String id) {
        return "vol_up".equals(id) || "vol_down".equals(id);
    }

    public static void run(AccessibilityService s, String id) {
        if (id == null || "block".equals(id)) return;
        AudioManager am = (AudioManager) s.getSystemService(Context.AUDIO_SERVICE);
        switch (id) {
            case "media_next": media(am, KeyEvent.KEYCODE_MEDIA_NEXT); return;
            case "media_prev": media(am, KeyEvent.KEYCODE_MEDIA_PREVIOUS); return;
            case "media_play_pause": media(am, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE); return;
            case "media_stop": media(am, KeyEvent.KEYCODE_MEDIA_STOP); return;
            case "vol_up":
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI);
                return;
            case "vol_down":
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI);
                return;
            case "mute":
                if (Build.VERSION.SDK_INT >= 23) {
                    am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI);
                } else {
                    am.setStreamMute(AudioManager.STREAM_MUSIC, am.getStreamVolume(AudioManager.STREAM_MUSIC) > 0);
                }
                return;
            case "home": s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME); return;
            case "back": s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK); return;
            case "recents": s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS); return;
            case "notifications": s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS); return;
        }
        if (id.startsWith(APP_PREFIX)) {
            String pkg = id.substring(APP_PREFIX.length());
            Intent i = s.getPackageManager().getLaunchIntentForPackage(pkg);
            if (i == null) {
                Toast.makeText(s, "Приложение не найдено: " + pkg, Toast.LENGTH_SHORT).show();
                return;
            }
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            try {
                s.startActivity(i);
            } catch (Exception e) {
                Toast.makeText(s, "Не удалось открыть приложение", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private static void media(AudioManager am, int code) {
        long t = SystemClock.uptimeMillis();
        am.dispatchMediaKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0));
        am.dispatchMediaKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0));
    }
}
