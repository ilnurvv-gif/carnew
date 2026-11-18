package local.headunit.swc;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

public class MainActivity extends Activity implements KeyService.Listener {

    private TextView status;
    private Button enableBtn;
    private TextView log;
    private LinearLayout list;
    private AlertDialog learnDialog;
    private final LinkedList<String> logLines = new LinkedList<>();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = dp(20);
        root.setPadding(p, p, p, p);
        scroll.addView(root);

        TextView title = text("Кнопки пульта", 26, true);
        root.addView(title);

        status = text("", 18, false);
        status.setPadding(0, dp(12), 0, dp(4));
        root.addView(status);

        enableBtn = button("Включить службу");
        enableBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            }
        });
        root.addView(enableBtn);

        Button add = button("＋ Добавить кнопку");
        add.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startLearning();
            }
        });
        root.addView(add);

        root.addView(section("Назначенные кнопки"));
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        root.addView(list);

        root.addView(section("Что видит магнитола (последние нажатия)"));
        log = text("Нажмите любую кнопку на пульте…", 16, false);
        log.setTypeface(Typeface.MONOSPACE);
        root.addView(log);

        Button reset = button("Сбросить все назначения");
        reset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new AlertDialog.Builder(MainActivity.this)
                        .setMessage("Удалить все назначения кнопок?")
                        .setPositiveButton("Удалить", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int w) {
                                Store.clear(MainActivity.this);
                                refreshList();
                            }
                        })
                        .setNegativeButton("Отмена", null)
                        .show();
            }
        });
        root.addView(reset);

        TextView help = text(
                "Как пользоваться:\n"
                        + "1. Нажмите «Включить службу» и включите «Кнопки пульта» в специальных возможностях.\n"
                        + "2. Нажмите «Добавить кнопку», затем кнопку на пульте.\n"
                        + "3. Выберите короткое или долгое нажатие и действие.\n\n"
                        + "Если при нажатии кнопки в журнале ничего не появляется, эту кнопку "
                        + "обрабатывает сама магнитола (провода KEY1/KEY2), и настраивать её нужно "
                        + "в «Настройках рулевого колеса».", 15, false);
        help.setPadding(0, dp(20), 0, 0);
        help.setTextColor(0xFFAAAAAA);
        root.addView(help);

        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        KeyService.listener = this;
        refreshStatus();
        refreshList();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (KeyService.listener == this) KeyService.listener = null;
        stopLearning();
    }

    // ---------- журнал и обучение ----------

    @Override
    public void onKey(int code) {
        addLog(code);
        if (learnDialog != null) learned(code);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        // Запасной путь, если служба выключена: ловим кнопки в самом окне.
        if (KeyService.instance == null && e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) {
            addLog(e.getKeyCode());
        }
        return super.dispatchKeyEvent(e);
    }

    private void addLog(int code) {
        logLines.addFirst(name(code) + "  (" + code + ")");
        while (logLines.size() > 8) logLines.removeLast();
        StringBuilder sb = new StringBuilder();
        for (String s : logLines) sb.append(s).append('\n');
        log.setText(sb.toString().trim());
    }

    private void startLearning() {
        KeyService.learning = true;
        learnDialog = new AlertDialog.Builder(this)
                .setTitle("Обучение")
                .setMessage("Нажмите кнопку на пульте…")
                .setNegativeButton("Отмена", null)
                .setOnKeyListener(new DialogInterface.OnKeyListener() {
                    @Override
                    public boolean onKey(DialogInterface d, int code, KeyEvent e) {
                        // Если служба выключена, кнопка придёт сюда.
                        if (KeyService.instance == null && e.getAction() == KeyEvent.ACTION_DOWN
                                && e.getRepeatCount() == 0) {
                            addLog(code);
                            learned(code);
                            return true;
                        }
                        return false;
                    }
                })
                .setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface d) {
                        KeyService.learning = false;
                        learnDialog = null;
                    }
                })
                .show();
    }

    private void stopLearning() {
        KeyService.learning = false;
        if (learnDialog != null) learnDialog.dismiss();
        learnDialog = null;
    }

    private void learned(final int code) {
        stopLearning();
        // Выбор действия показываем после короткой паузы, чтобы отпускание кнопки не попало в диалог.
        log.postDelayed(new Runnable() {
            @Override
            public void run() {
                chooseType(code);
            }
        }, 300);
    }

    private void chooseType(final int code) {
        String[] items = {
                "Короткое нажатие: " + Actions.label(this, Store.get(this, Store.SHORT, code)),
                "Долгое нажатие: " + Actions.label(this, Store.get(this, Store.LONG, code)),
                "Удалить назначения этой кнопки"
        };
        new AlertDialog.Builder(this)
                .setTitle(name(code) + " (" + code + ")")
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        if (which == 0) chooseAction(code, Store.SHORT);
                        else if (which == 1) chooseAction(code, Store.LONG);
                        else {
                            Store.remove(MainActivity.this, code);
                            refreshList();
                        }
                    }
                })
                .setNegativeButton("Закрыть", null)
                .show();
    }

    private void chooseAction(final int code, final String type) {
        String[] items = new String[Actions.LABELS.length + 1];
        System.arraycopy(Actions.LABELS, 0, items, 0, Actions.LABELS.length);
        items[items.length - 1] = "Убрать назначение";
        new AlertDialog.Builder(this)
                .setTitle(Store.SHORT.equals(type) ? "Короткое нажатие" : "Долгое нажатие")
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        if (which == Actions.IDS.length) {
                            Store.set(MainActivity.this, type, code, null);
                            refreshList();
                        } else if ("app_pick".equals(Actions.IDS[which])) {
                            chooseApp(code, type);
                        } else {
                            Store.set(MainActivity.this, type, code, Actions.IDS[which]);
                            refreshList();
                        }
                    }
                })
                .show();
    }

    private void chooseApp(final int code, final String type) {
        final PackageManager pm = getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> apps = pm.queryIntentActivities(main, 0);
        final List<String[]> rows = new ArrayList<>();
        for (ResolveInfo ri : apps) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(getPackageName())) continue;
            rows.add(new String[]{String.valueOf(ri.loadLabel(pm)), pkg});
        }
        Collections.sort(rows, new Comparator<String[]>() {
            @Override
            public int compare(String[] a, String[] b) {
                return a[0].compareToIgnoreCase(b[0]);
            }
        });
        String[] labels = new String[rows.size()];
        for (int i = 0; i < rows.size(); i++) labels[i] = rows.get(i)[0];
        new AlertDialog.Builder(this)
                .setTitle("Какое приложение открыть")
                .setItems(labels, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        Store.set(MainActivity.this, type, code, Actions.APP_PREFIX + rows.get(which)[1]);
                        refreshList();
                    }
                })
                .show();
    }

    // ---------- экран ----------

    private void refreshStatus() {
        boolean on = KeyService.instance != null;
        status.setText(on ? "✓ Служба включена" : "✗ Служба выключена — без неё кнопки не переназначаются");
        status.setTextColor(on ? 0xFF66DD88 : 0xFFFF7766);
        enableBtn.setVisibility(on ? View.GONE : View.VISIBLE);
    }

    private void refreshList() {
        list.removeAllViews();
        List<Integer> codes = Store.codes(this);
        if (codes.isEmpty()) {
            TextView t = text("Пока ничего не назначено", 16, false);
            t.setTextColor(0xFFAAAAAA);
            list.addView(t);
            return;
        }
        for (final int code : codes) {
            TextView row = text(name(code) + " (" + code + ")\n"
                    + "  коротко: " + Actions.label(this, Store.get(this, Store.SHORT, code)) + "\n"
                    + "  долго: " + Actions.label(this, Store.get(this, Store.LONG, code)), 17, false);
            int p = dp(14);
            row.setPadding(p, p, p, p);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(0xFF2A2D33);
            bg.setCornerRadius(dp(10));
            row.setBackground(bg);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(8);
            row.setLayoutParams(lp);
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    chooseType(code);
                }
            });
            list.addView(row);
        }
    }

    private static String name(int code) {
        String s = KeyEvent.keyCodeToString(code);
        return s.startsWith("KEYCODE_") ? s.substring(8) : s;
    }

    private TextView text(String s, int sp, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(Color.WHITE);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private TextView section(String s) {
        TextView t = text(s, 19, true);
        t.setPadding(0, dp(22), 0, dp(8));
        return t;
    }

    private Button button(String s) {
        Button btn = new Button(this);
        btn.setText(s);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        btn.setAllCaps(false);
        btn.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(64));
        lp.topMargin = dp(8);
        btn.setLayoutParams(lp);
        return btn;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
