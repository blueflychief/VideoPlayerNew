package com.infinite.simpleplayer;

import android.media.MediaPlayer;

/**
 * Created by lsq on 11/18/2016.
 */

public interface VideoPlayerCallback {
    void onScaleChange(boolean isFullscreen);

    void onPlayerPause(final MediaPlayer mediaPlayer);

    void onPlayerStart(final MediaPlayer mediaPlayer);

    void onBufferingStart(final MediaPlayer mediaPlayer);

    void onBufferingEnd(final MediaPlayer mediaPlayer);
}
