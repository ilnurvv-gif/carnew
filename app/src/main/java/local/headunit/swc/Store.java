package local.headunit.swc;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/** Хранит назначения: "s_<код>" — короткое нажатие, "l_<код>" — долгое. */
public final class Store {
    public static final String SHORT = "s";
    public static final String LONG = "l";

    private Store() {}

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences("mapping", Context.MODE_PRIVATE);
    }

    public static String get(Context c, String type, int code) {
        return prefs(c).getString(type + "_" + code, null);
    }

    public static void set(Context c, String type, int code, String action) {
        SharedPreferences.Editor e = prefs(c).edit();
        if (action == null) e.remove(type + "_" + code);
        else e.putString(type + "_" + code, action);
        e.apply();
    }

    public static void remove(Context c, int code) {
        prefs(c).edit().remove(SHORT + "_" + code).remove(LONG + "_" + code).apply();
    }

    public static void clear(Context c) {
        prefs(c).edit().clear().apply();
    }

    public static List<Integer> codes(Context c) {
        TreeSet<Integer> set = new TreeSet<>();
        for (Map.Entry<String, ?> en : prefs(c).getAll().entrySet()) {
            String k = en.getKey();
            int i = k.indexOf('_');
            if (i < 0) continue;
            try {
                set.add(Integer.parseInt(k.substring(i + 1)));
            } catch (NumberFormatException ignored) {
            }
        }
        List<Integer> list = new ArrayList<>(set);
        Collections.sort(list);
        return list;
    }
}
