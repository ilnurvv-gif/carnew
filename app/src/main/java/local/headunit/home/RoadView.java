package local.headunit.home;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import java.util.Random;

/**
 * Фон: ночная дорога, холмы, фонари и ваша машина (фото car.png).
 * Машина едет влево, поэтому декорации двигаются вправо.
 * Рисуется ~30 кадров/с, а когда поверх открыто другое приложение,
 * анимация сама останавливается и магнитолу не грузит.
 */
public class RoadView extends View {

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bmpPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();
    private final long t0 = SystemClock.uptimeMillis();
    private final float[] starX = new float[45];
    private final float[] starY = new float[45];
    private Bitmap car;
    private Shader sky;
    private boolean running;

    public RoadView(Context context) {
        super(context);
        init();
    }

    public RoadView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        Random r = new Random(7);
        for (int i = 0; i < starX.length; i++) {
            starX[i] = r.nextFloat();
            starY[i] = r.nextFloat();
        }
        car = BitmapFactory.decodeResource(getResources(), R.drawable.car);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        running = getWindowVisibility() == VISIBLE;
        invalidate();
    }

    @Override
    protected void onDetachedFromWindow() {
        running = false;
        super.onDetachedFromWindow();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        running = visibility == VISIBLE;
        if (running) invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        sky = new LinearGradient(0f, 0f, 0f, h * 0.70f,
                new int[]{0xFF070B16, 0xFF16224A, 0xFFC96F4A},
                new float[]{0f, 0.62f, 1f}, Shader.TileMode.CLAMP);
    }

    private void fill(int color) {
        p.setStyle(Paint.Style.FILL);
        p.setShader(null);
        p.setColor(color);
    }

    private void line(int color, float width) {
        p.setStyle(Paint.Style.STROKE);
        p.setShader(null);
        p.setColor(color);
        p.setStrokeWidth(width);
    }

    private void hills(Canvas c, int w, float baseY, float amp, int color,
                       float shift, float f1, float f2, float ph) {
        path.reset();
        path.moveTo(0f, baseY);
        for (int x = 0; x <= w + 20; x += 20) {
            float xx = x + shift;
            float y = baseY - amp * (0.55f
                    + 0.30f * (float) Math.sin(xx * f1 + ph)
                    + 0.15f * (float) Math.sin(xx * f2 + 1.7f));
            path.lineTo(x, y);
        }
        path.lineTo(w + 20, baseY);
        path.close();
        fill(color);
        c.drawPath(path, p);
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0 || sky == null) return;

        float t = (SystemClock.uptimeMillis() - t0) / 1000f;
        float dist = t * w * 0.22f;
        float roadTop = h * 0.66f;

        // Фон зеркалим: машина едет влево, значит всё остальное уезжает вправо
        c.save();
        c.scale(-1f, 1f, w / 2f, 0f);

        // небо
        p.setStyle(Paint.Style.FILL);
        p.setShader(sky);
        c.drawRect(0f, 0f, w, h * 0.70f, p);
        p.setShader(null);

        // звёзды
        fill(0xAAFFFFFF);
        for (int i = 0; i < starX.length; i++) {
            c.drawCircle(starX[i] * w, starY[i] * h * 0.38f, 1.4f, p);
        }

        // холмы (параллакс)
        hills(c, w, roadTop, h * 0.20f, 0xFF18244A, dist * 0.05f, 0.0028f, 0.009f, 0f);
        hills(c, w, roadTop, h * 0.11f, 0xFF0E172E, dist * 0.12f, 0.0042f, 0.013f, 2.1f);

        // фонари
        float spacing = w * 0.55f;
        float off = -((dist * 0.45f) % spacing);
        for (float x = off; x < w + spacing; x += spacing) {
            float top = h * 0.40f;
            line(0xFF0A0F1E, 5f);
            c.drawLine(x, roadTop, x, top, p);
            c.drawLine(x, top, x + 40f, top, p);
            fill(0x22FFD9A0);
            c.drawCircle(x + 40f, top + 8f, 42f, p);
            fill(0x33FFD9A0);
            c.drawCircle(x + 40f, top + 8f, 18f, p);
            fill(0xFFFFE6B0);
            c.drawCircle(x + 40f, top + 8f, 6f, p);
        }

        // дорога
        fill(0xFF0B0E16);
        c.drawRect(0f, roadTop, w, h, p);
        fill(0xFF2A3350);
        c.drawRect(0f, roadTop, w, roadTop + 4f, p);

        // разметка
        float dl = w * 0.07f;
        float gap = w * 0.06f;
        float period = dl + gap;
        float doff = -(dist % period);
        fill(0x99C8D0E0);
        float ly = h * 0.795f;
        for (float x = doff; x < w; x += period) {
            c.drawRect(x, ly, x + dl, ly + 6f, p);
        }
        c.restore();

        // машина (фото)
        if (car != null) {
            float ch = h * 0.34f;
            float cw = ch * car.getWidth() / (float) car.getHeight();
            float cx = w * 0.5f;
            float bottom = h * 0.78f;
            float bob = (float) Math.sin(t * 9f) * 1.5f;
            float tilt = (float) Math.sin(t * 5f + 1f) * 0.4f;

            fill(0x99000000);
            rect.set(cx - cw * 0.46f, bottom - ch * 0.03f, cx + cw * 0.46f, bottom + ch * 0.05f);
            c.drawOval(rect, p);

            c.save();
            c.rotate(tilt, cx, bottom);
            rect.set(cx - cw / 2f, bottom - ch + bob, cx + cw / 2f, bottom + bob);
            c.drawBitmap(car, null, rect, bmpPaint);
            c.restore();
        }

        if (running) postInvalidateDelayed(33);
    }
}
