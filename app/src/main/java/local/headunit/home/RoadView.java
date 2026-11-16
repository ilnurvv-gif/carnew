package local.headunit.home;

import android.content.Context;
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
 * Фон: ночная дорога, холмы, фонари и чёрный седан, который едет слева направо.
 * Рисуется кодом (без картинок), ~30 кадров/с. Анимация останавливается,
 * когда поверх открыто другое приложение, поэтому магнитолу не грузит.
 */
public class RoadView extends View {

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Path body = new Path();
    private final RectF rect = new RectF();
    private final long t0 = SystemClock.uptimeMillis();
    private final float[] starX = new float[45];
    private final float[] starY = new float[45];
    private final Shader beam = new LinearGradient(
            965f, 0f, 1450f, 0f, 0x66FFE9A8, 0x00FFE9A8, Shader.TileMode.CLAMP);
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
        // Силуэт седана (боком, нос справа) в условных координатах 1000 x 300
        body.moveTo(28f, 252f);
        body.lineTo(24f, 196f);
        body.quadTo(26f, 172f, 60f, 168f);
        body.lineTo(262f, 162f);
        body.lineTo(336f, 108f);
        body.quadTo(440f, 86f, 560f, 98f);
        body.lineTo(668f, 158f);
        body.lineTo(880f, 170f);
        body.quadTo(962f, 178f, 972f, 210f);
        body.lineTo(972f, 252f);
        body.close();
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

    private void poly(Canvas c, float... v) {
        path.reset();
        path.moveTo(v[0], v[1]);
        for (int i = 2; i < v.length; i += 2) path.lineTo(v[i], v[i + 1]);
        path.close();
        c.drawPath(path, p);
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
        float ly = h * 0.775f;
        for (float x = doff; x < w; x += period) {
            c.drawRect(x, ly, x + dl, ly + 6f, p);
        }

        // машина
        float len = Math.min(w * 0.46f, h * 0.78f);
        float s = len / 1000f;
        float gy = h * 0.72f;
        float bounce = (float) Math.sin(t * 9f) * 1.2f;
        float angle = (float) Math.toDegrees(dist / (46f * s)) % 360f;
        c.save();
        c.translate(w * 0.5f - len / 2f, gy - 290f * s + bounce);
        c.scale(s, s);
        drawCar(c, angle);
        c.restore();

        if (running) postInvalidateDelayed(33);
    }

    private void drawCar(Canvas c, float angle) {
        // тень
        fill(0x99000000);
        rect.set(20f, 280f, 980f, 300f);
        c.drawOval(rect, p);

        // свет фар
        p.setStyle(Paint.Style.FILL);
        p.setShader(beam);
        poly(c, 965f, 196f, 1450f, 150f, 1450f, 292f, 965f, 216f);
        p.setShader(null);

        // кузов
        fill(0xFF08090D);
        c.drawPath(body, p);
        line(0xFF3A4458, 3f);
        c.drawPath(body, p);

        // блик по линии крыши и пояс кузова
        line(0xFF6B7791, 2.5f);
        path.reset();
        path.moveTo(262f, 162f);
        path.lineTo(336f, 108f);
        path.quadTo(440f, 86f, 560f, 98f);
        path.lineTo(668f, 158f);
        c.drawPath(path, p);
        line(0xFF2C3446, 2f);
        c.drawLine(60f, 172f, 880f, 174f, p);

        // стёкла
        fill(0xFF1B2638);
        poly(c, 276f, 160f, 344f, 114f, 444f, 102f, 444f, 160f);
        poly(c, 460f, 160f, 460f, 102f, 552f, 108f, 648f, 160f);

        // стыки дверей и ручки
        line(0xFF2C3446, 2f);
        c.drawLine(300f, 164f, 296f, 246f, p);
        c.drawLine(452f, 160f, 452f, 246f, p);
        c.drawLine(640f, 160f, 646f, 246f, p);
        line(0xFF4A556B, 3f);
        c.drawLine(410f, 180f, 432f, 180f, p);
        c.drawLine(570f, 180f, 592f, 180f, p);

        // фары и стоп-сигналы
        fill(0x44FFE9A8);
        c.drawCircle(965f, 198f, 26f, p);
        fill(0xFFFFF0C0);
        rect.set(938f, 190f, 972f, 206f);
        c.drawRoundRect(rect, 6f, 6f, p);
        fill(0x44FF3B30);
        c.drawCircle(30f, 198f, 22f, p);
        fill(0xFFFF2D2D);
        rect.set(24f, 190f, 48f, 206f);
        c.drawRoundRect(rect, 5f, 5f, p);

        // колёса
        drawWheel(c, 205f, angle);
        drawWheel(c, 775f, angle);
    }

    private void drawWheel(Canvas c, float wx, float angle) {
        float wy = 244f;
        line(0xFF2C3446, 3f);
        c.drawCircle(wx, wy, 56f, p);
        fill(0xFF0D0E12);
        c.drawCircle(wx, wy, 46f, p);
        line(0xFF2A2F3A, 3f);
        c.drawCircle(wx, wy, 46f, p);
        fill(0xFF8C95A8);
        c.drawCircle(wx, wy, 30f, p);
        c.save();
        c.rotate(angle, wx, wy);
        line(0xFF2A3040, 5f);
        for (int k = 0; k < 5; k++) {
            c.rotate(72f, wx, wy);
            c.drawLine(wx, wy, wx + 28f, wy, p);
        }
        c.restore();
        fill(0xFF6D768A);
        c.drawCircle(wx, wy, 7f, p);
    }
}
