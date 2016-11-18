package com.infinite.simpleplayer;

/**
 * Created by lsq on 11/18/2016.
 */

public interface MediaPlayerControl {
    void start();

    void pause();

    int getDuration();

    int getCurrentPosition();

    void seekTo(int pos);

    boolean isPlaying();

    int getBufferPercentage();

    boolean canPause();

    boolean canSeekBackward();

    boolean canSeekForward();

    void closePlayer();//关闭播放视频,使播放器处于idle状态

    void setFullscreen(boolean fullscreen);

    void setFullscreen(boolean fullscreen, int screenOrientation);
}
