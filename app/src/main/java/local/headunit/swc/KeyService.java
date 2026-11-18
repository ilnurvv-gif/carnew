package local.headunit.swc;

import android.accessibilityservice.AccessibilityService;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/** Служба специальных возможностей: видит нажатия кнопок раньше всех приложений. */
public class KeyService extends AccessibilityService {

    public interface Listener {
        /** Нажатие кнопки (для журнала и обучения). */
        void onKey(int code);
    }

    private static final long LONG_PRESS_MS = 600;

    static volatile KeyService instance;
    /** Режим обучения: следующее нажатие отдаётся в приложение и не выполняет действий. */
    static volatile boolean learning;
    static Listener listener;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private int downCode = -1;
    private boolean longFired;
    private Runnable longRunnable;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
    }

    @Override
    public boolean onUnbind(android.content.Intent intent) {
        instance = null;
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        instance = null;
        super.onDestroy();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    protected boolean onKeyEvent(KeyEvent e) {
        final int code = e.getKeyCode();
        final int action = e.getAction();
        boolean firstDown = action == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0;

        if (firstDown && listener != null) listener.onKey(code);
        if (learning) return true;

        final String shortAct = Store.get(this, Store.SHORT, code);
        final String longAct = Store.get(this, Store.LONG, code);
        if (shortAct == null && longAct == null) return false; // кнопка не настроена — пропускаем как есть

        if (longAct == null) {
            if (action == KeyEvent.ACTION_DOWN && (e.getRepeatCount() == 0 || Actions.repeatable(shortAct))) {
                Actions.run(this, shortAct);
            }
            return true;
        }

        if (action == KeyEvent.ACTION_DOWN) {
            if (e.getRepeatCount() == 0) {
                cancelLong();
                downCode = code;
                longFired = false;
                longRunnable = new Runnable() {
                    @Override
                    public void run() {
                        longFired = true;
                        Actions.run(KeyService.this, longAct);
                    }
                };
                handler.postDelayed(longRunnable, LONG_PRESS_MS);
            }
            return true;
        }
        if (action == KeyEvent.ACTION_UP) {
            if (code == downCode) {
                cancelLong();
                if (!longFired && shortAct != null) Actions.run(this, shortAct);
                downCode = -1;
            }
            return true;
        }
        return true;
    }

    private void cancelLong() {
        if (longRunnable != null) handler.removeCallbacks(longRunnable);
        longRunnable = null;
    }
}
