package local.headunit.home;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Простой лаунчер: анимированный фон, приветствие, часы, панель приложений.
 * Кнопка с сеткой открывает полный список. Долгое нажатие на приложение:
 * закрепить на панели / убрать / открыть "О приложении".
 */
public class MainActivity extends Activity {

    private static final int DOCK_DEFAULT = 7;
    private static final int DOCK_MAX = 8;

    private static class AppItem {
        String label;
        String pkg;
        Drawable icon;
    }

    private final List<AppItem> apps = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            updateGreeting();
            handler.postDelayed(this, 30000);
        }
    };

    private GridView grid;
    private AppAdapter adapter;
    private View drawer;
    private LinearLayout dock;
    private TextView greeting;
    private TextView dateView;
    private SharedPreferences prefs;
    private FillVideoView video;
    private View poster;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        prefs = getSharedPreferences("home", MODE_PRIVATE);

        grid = findViewById(R.id.grid);
        drawer = findViewById(R.id.drawer);
        dock = findViewById(R.id.dock);
        video = findViewById(R.id.video);
        poster = findViewById(R.id.poster);
        setupVideo();
        greeting = findViewById(R.id.greeting);
        dateView = findViewById(R.id.date);

        adapter = new AppAdapter();
        grid.setAdapter(adapter);
        grid.setOnItemClickListener((parent, view, position, id) -> launch(apps.get(position)));
        grid.setOnItemLongClickListener((parent, view, position, id) -> {
            showAppMenu(apps.get(position));
            return true;
        });

        findViewById(R.id.pill_wifi).setOnClickListener(v -> safeStart(Settings.ACTION_WIFI_SETTINGS));
        findViewById(R.id.pill_vpn).setOnClickListener(v -> safeStart(Settings.ACTION_VPN_SETTINGS));
        findViewById(R.id.btn_bt).setOnClickListener(v -> safeStart(Settings.ACTION_BLUETOOTH_SETTINGS));
        findViewById(R.id.btn_settings).setOnClickListener(v -> safeStart(Settings.ACTION_SETTINGS));
        findViewById(R.id.btn_apps).setOnClickListener(v ->
                drawer.setVisibility(drawer.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));
    }

    private void setupVideo() {
        video.setSource(Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.bg),
                new FillVideoView.Callback() {
                    @Override
                    public void onFirstFrame() {
                        runOnUiThread(() -> poster.setVisibility(View.GONE));
                    }

                    @Override
                    public void onFailed() {
                        runOnUiThread(() -> poster.setVisibility(View.VISIBLE)); // остаётся картинка
                    }
                });
    }

    @Override
    protected void onResume() {
        super.onResume();
        try { video.start(); } catch (Exception ignored) { }
        handler.removeCallbacks(tick);
        handler.post(tick);
        loadAppsAsync();
    }

    @Override
    protected void onPause() {
        try { video.pause(); } catch (Exception ignored) { }
        handler.removeCallbacks(tick);
        super.onPause();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        drawer.setVisibility(View.GONE);
    }

    @Override
    public void onBackPressed() {
        if (drawer.getVisibility() == View.VISIBLE) {
            drawer.setVisibility(View.GONE);
        }
        // на главном экране "назад" ничего не делает
    }

    private void updateGreeting() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String g;
        if (hour < 5) g = "Доброй ночи";
        else if (hour < 12) g = "Доброе утро";
        else if (hour < 18) g = "Добрый день";
        else g = "Добрый вечер";
        greeting.setText(g);

        String d = new SimpleDateFormat("EEEE, d MMMM", new Locale("ru")).format(new Date());
        if (!d.isEmpty()) d = d.substring(0, 1).toUpperCase(new Locale("ru")) + d.substring(1);
        dateView.setText(d);
    }

    private void safeStart(String action) {
        try {
            Intent i = new Intent(action);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, "Недоступно на этой магнитоле", Toast.LENGTH_SHORT).show();
        }
    }

    private void launch(AppItem item) {
        Intent li = getPackageManager().getLaunchIntentForPackage(item.pkg);
        if (li != null) {
            li.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(li);
        }
        drawer.setVisibility(View.GONE);
    }

    // ---------- панель приложений ----------

    private List<String> getPinned() {
        String s = prefs.getString("pinned", "");
        List<String> list = new ArrayList<>();
        if (!s.isEmpty()) {
            for (String p : s.split("\n")) {
                if (!p.isEmpty()) list.add(p);
            }
        }
        return list;
    }

    private void setPinned(List<String> list) {
        prefs.edit().putString("pinned", TextUtils.join("\n", list)).apply();
    }

    private AppItem findApp(String pkg) {
        for (AppItem a : apps) {
            if (a.pkg.equals(pkg)) return a;
        }
        return null;
    }

    private List<AppItem> dockItems() {
        List<AppItem> result = new ArrayList<>();
        for (String pkg : getPinned()) {
            AppItem a = findApp(pkg);
            if (a != null) result.add(a);
        }
        if (result.isEmpty()) {
            for (int i = 0; i < apps.size() && i < DOCK_DEFAULT; i++) result.add(apps.get(i));
        }
        return result;
    }

    private void rebuildDock() {
        dock.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (final AppItem item : dockItems()) {
            View v = inflater.inflate(R.layout.item_dock, dock, false);
            ((ImageView) v.findViewById(R.id.dock_icon)).setImageDrawable(item.icon);
            ((TextView) v.findViewById(R.id.dock_label)).setText(item.label);
            v.setOnClickListener(x -> launch(item));
            v.setOnLongClickListener(x -> {
                showAppMenu(item);
                return true;
            });
            dock.addView(v);
        }
    }

    private void showAppMenu(final AppItem item) {
        final List<String> pinned = getPinned();
        if (pinned.isEmpty()) {
            for (AppItem a : dockItems()) pinned.add(a.pkg);
        }
        final boolean isPinned = pinned.contains(item.pkg);
        String[] options = {
                isPinned ? "Убрать с панели" : "Закрепить на панели",
                "О приложении (отключить / остановить)"
        };
        new AlertDialog.Builder(this)
                .setTitle(item.label)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        if (isPinned) {
                            pinned.remove(item.pkg);
                        } else {
                            if (pinned.size() >= DOCK_MAX) {
                                Toast.makeText(this, "На панели максимум " + DOCK_MAX, Toast.LENGTH_SHORT).show();
                                return;
                            }
                            pinned.add(item.pkg);
                        }
                        setPinned(pinned);
                        rebuildDock();
                    } else {
                        Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:" + item.pkg));
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(i);
                    }
                })
                .show();
    }

    // ---------- загрузка списка приложений ----------

    private void loadAppsAsync() {
        new Thread(() -> {
            PackageManager pm = getPackageManager();
            Intent main = new Intent(Intent.ACTION_MAIN);
            main.addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> list = pm.queryIntentActivities(main, 0);

            List<AppItem> result = new ArrayList<>();
            for (ResolveInfo ri : list) {
                String pkg = ri.activityInfo.packageName;
                if (pkg.equals(getPackageName())) continue;
                AppItem item = new AppItem();
                item.pkg = pkg;
                item.label = String.valueOf(ri.loadLabel(pm));
                item.icon = ri.loadIcon(pm);
                result.add(item);
            }
            Collections.sort(result, (a, b) ->
                    a.label.toLowerCase(Locale.getDefault())
                            .compareTo(b.label.toLowerCase(Locale.getDefault())));

            runOnUiThread(() -> {
                apps.clear();
                apps.addAll(result);
                adapter.notifyDataSetChanged();
                rebuildDock();
            });
        }).start();
    }

    private class AppAdapter extends BaseAdapter {
        @Override public int getCount() { return apps.size(); }
        @Override public Object getItem(int position) { return apps.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView;
            if (v == null) {
                v = LayoutInflater.from(MainActivity.this)
                        .inflate(R.layout.item_app, parent, false);
            }
            AppItem item = apps.get(position);
            ((ImageView) v.findViewById(R.id.icon)).setImageDrawable(item.icon);
            ((TextView) v.findViewById(R.id.label)).setText(item.label);
            return v;
        }
    }
}
