package com.infinite.simpleplayer;

import android.media.MediaPlayer;

/**
 * Created by lsq on 11/18/2016.
 */

public interface VideoPlayerCallback {
    void onScaleChange(boolean isFullscreen);

    void onPlayerPrepared(final MediaPlayer mediaPlayer);

    void onPlayerPause(int currentPosition);

    void onPlayerStart(final MediaPlayer mediaPlayer);

    void onPlayCompleted(MediaPlayer mp);

    void onBufferingStart(final MediaPlayer mediaPlayer);

    void onBuffering(int percent);

    void onError(MediaPlayer mp, int what, int extra);

    void onInfo(MediaPlayer mp, int what, int extra);

    void onBufferingEnd(final MediaPlayer mediaPlayer);
}
