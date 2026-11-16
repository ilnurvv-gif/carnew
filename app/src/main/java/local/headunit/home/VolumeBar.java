package local.headunit.home;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.media.AudioManager;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/** Вертикальная «качелька» громкости (STREAM_MUSIC). Тянуть вверх — громче. */
public class VolumeBar extends View {
    private final AudioManager am;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private int max = 15, cur = 0;

    public VolumeBar(Context c, AttributeSet a) {
        super(c, a);
        am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        refresh();
    }

    public void refresh() {
        max = Math.max(1, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        cur = am.getStreamVolume(AudioManager.STREAM_MUSIC);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas cv) {
        float w = getWidth(), h = getHeight(), rad = w / 2f;
        r.set(0, 0, w, h);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0x33FFFFFF);
        cv.drawRoundRect(r, rad, rad, p);
        float fh = h * cur / (float) max;
        if (fh > 0) {
            cv.save();
            cv.clipRect(0, h - fh, w, h);
            p.setColor(0xCCFFFFFF);
            cv.drawRoundRect(r, rad, rad, p);
            cv.restore();
        }
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2f);
        p.setColor(0x99CFE2FF);
        r.inset(1, 1);
        cv.drawRoundRect(r, rad, rad, p);
        p.setStyle(Paint.Style.FILL);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(w * 0.34f);
        p.setColor(fh > h * 0.12f ? 0xFF0B1A33 : 0xFFFFFFFF);
        cv.drawText(String.valueOf(cur), w / 2f, h - w * 0.28f, p);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int a = e.getActionMasked();
        if (a == MotionEvent.ACTION_DOWN || a == MotionEvent.ACTION_MOVE) {
            getParent().requestDisallowInterceptTouchEvent(true);
            float f = 1f - e.getY() / getHeight();
            f = Math.max(0f, Math.min(1f, f));
            int v = Math.round(f * max);
            if (v != cur) {
                cur = v;
                try { am.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0); } catch (Throwable ignore) {}
                invalidate();
            }
            return true;
        }
        return a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL || super.onTouchEvent(e);
    }
}
