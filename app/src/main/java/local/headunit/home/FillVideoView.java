package local.headunit.home;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.net.Uri;
import android.util.AttributeSet;
import android.view.Surface;
import android.view.TextureView;

/**
 * Видео без звука, по кругу, на весь экран с обрезкой по краям (как centerCrop),
 * поэтому чёрных полос нет на любом соотношении сторон экрана.
 */
public class FillVideoView extends TextureView implements TextureView.SurfaceTextureListener {

    public interface Callback {
        void onFirstFrame();
        void onFailed();
    }

    private MediaPlayer player;
    private Surface surface;
    private Uri uri;
    private Callback callback;
    private boolean ready;
    private boolean wantPlay;
    private int vw;
    private int vh;

    public FillVideoView(Context context) {
        super(context);
        setSurfaceTextureListener(this);
    }

    public FillVideoView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setSurfaceTextureListener(this);
    }

    public void setSource(Uri u, Callback cb) {
        uri = u;
        callback = cb;
        if (surface != null) open();
    }

    public void start() {
        wantPlay = true;
        if (ready && player != null) {
            try { player.start(); } catch (Exception ignored) { }
        }
    }

    public void pause() {
        wantPlay = false;
        if (ready && player != null) {
            try { if (player.isPlaying()) player.pause(); } catch (Exception ignored) { }
        }
    }

    private void open() {
        release();
        try {
            player = new MediaPlayer();
            player.setDataSource(getContext(), uri);
            player.setSurface(surface);
            player.setLooping(true);
            player.setVolume(0f, 0f);
            player.setOnVideoSizeChangedListener((m, w, h) -> {
                vw = w;
                vh = h;
                applyCrop();
            });
            player.setOnInfoListener((m, what, extra) -> {
                if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START && callback != null) {
                    callback.onFirstFrame();
                }
                return false;
            });
            player.setOnErrorListener((m, what, extra) -> {
                if (callback != null) callback.onFailed();
                return true;
            });
            player.setOnPreparedListener(m -> {
                ready = true;
                if (wantPlay) {
                    try { m.start(); } catch (Exception ignored) { }
                }
            });
            player.prepareAsync();
        } catch (Exception e) {
            if (callback != null) callback.onFailed();
        }
    }

    private void release() {
        ready = false;
        if (player != null) {
            try { player.release(); } catch (Exception ignored) { }
            player = null;
        }
    }

    private void applyCrop() {
        int w = getWidth();
        int h = getHeight();
        if (vw == 0 || vh == 0 || w == 0 || h == 0) return;
        float s = Math.max((float) w / vw, (float) h / vh);
        Matrix m = new Matrix();
        m.setScale(vw * s / w, vh * s / h, w / 2f, h / 2f);
        setTransform(m);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        applyCrop();
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture st, int width, int height) {
        surface = new Surface(st);
        if (uri != null) open();
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture st, int width, int height) {
        applyCrop();
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture st) {
        release();
        if (surface != null) {
            surface.release();
            surface = null;
        }
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture st) { }
}
