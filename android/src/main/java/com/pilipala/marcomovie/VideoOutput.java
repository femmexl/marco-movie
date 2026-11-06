package com.pilipala.marcomovie;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.os.Build;
import android.view.*;
import android.widget.FrameLayout;

/** SurfaceView preserves the decoder's HDR output; TextureView remains an explicit fallback. */
final class VideoOutput extends FrameLayout {
    interface Listener {
        void available(Surface surface);

        void destroyed();
    }

    private Listener listener;
    private Surface current;
    private final TextureView texture;

    VideoOutput(Context context, boolean compatible) {
        super(context);
        setTag("video-output");
        setFocusable(false);
        if (compatible || Build.VERSION.SDK_INT < 24) {
            texture = new TextureView(context);
            addView(texture, new LayoutParams(-1, -1));
            texture.setSurfaceTextureListener(
                    new TextureView.SurfaceTextureListener() {
                        public void onSurfaceTextureAvailable(SurfaceTexture value, int w, int h) {
                            ready(new Surface(value));
                        }

                        public void onSurfaceTextureSizeChanged(
                                SurfaceTexture value, int w, int h) {}

                        public boolean onSurfaceTextureDestroyed(SurfaceTexture value) {
                            gone();
                            return true;
                        }

                        public void onSurfaceTextureUpdated(SurfaceTexture value) {}
                    });
        } else {
            texture = null;
            SurfaceView surface = new SurfaceView(context);
            addView(surface, new LayoutParams(-1, -1));
            surface.getHolder()
                    .addCallback(
                            new SurfaceHolder.Callback() {
                                public void surfaceCreated(SurfaceHolder holder) {
                                    ready(holder.getSurface());
                                }

                                public void surfaceChanged(
                                        SurfaceHolder holder, int format, int w, int h) {}

                                public void surfaceDestroyed(SurfaceHolder holder) {
                                    gone();
                                }
                            });
        }
    }

    void listen(Listener value) {
        listener = value;
        if (current != null && current.isValid()) listener.available(current);
    }

    private void ready(Surface value) {
        current = value;
        if (listener != null) listener.available(value);
    }

    private void gone() {
        Surface old = current;
        current = null;
        if (listener != null) listener.destroyed();
        if (texture != null && old != null) old.release();
    }

    boolean supportsPicture() {
        return texture != null;
    }

    String outputName() {
        return texture == null ? "SurfaceView · 保真输出" : "TextureView · 兼容输出";
    }

    void picture(int preset) {
        if (texture != null) PlaybackEffects.picture(texture, preset);
    }
}
