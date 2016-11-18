/*
* Copyright (C) 2015 Author <dictfb#gmail.com>
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/


package com.infinite.simpleplayer;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.TypedArray;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.io.IOException;


public class UniversalVideoView extends SurfaceView
        implements MediaPlayerControl, OrientationChangeListener {
    private final String TAG = "UniversalVideoView";
    private Uri mUri;

    // all possible internal states
    private static final int STATE_ERROR = -1;
    private static final int STATE_IDLE = 0;
    private static final int STATE_PREPARING = 1;
    private static final int STATE_PREPARED = 2;
    private static final int STATE_PLAYING = 3;
    private static final int STATE_PAUSED = 4;
    private static final int STATE_PLAYBACK_COMPLETED = 5;

    private int mCurrentState = STATE_IDLE;
    private int mTargetState = STATE_IDLE;

    private Context mContext;
    private int mAudioSession;
    private int mVideoWidth;
    private int mVideoHeight;
    private int mSurfaceWidth;
    private int mSurfaceHeight;
    private SimplePlayerController mMediaController;
    private int mCurrentBufferPercentage;
    private int mSeekWhenPrepared;  // recording the seek position while preparing
    private boolean mCanPause;
    private boolean mCanSeekBack;
    private boolean mCanSeekForward;
    private boolean mPreparedBeforeStart;
    private boolean mFitXY = false;
    private boolean mAutoRotation = false;  //自动旋转
    private int mVideoViewLayoutWidth = 0;
    private int mVideoViewLayoutHeight = 0;
    private OrientationDetector mOrientationDetector;
    private VideoPlayerCallback mVideoPlayerCallback;

    private SurfaceHolder mSurfaceHolder = null;
    private MediaPlayer mMediaPlayer = null;
    private int mVideoDuring;
    private int mCurrentPosition = 0;  //记录当前的播放位置

    public UniversalVideoView(Context context) {
        this(context, null);
    }

    public UniversalVideoView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public UniversalVideoView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mContext = context;
        TypedArray a = mContext.obtainStyledAttributes(attrs, R.styleable.UniversalVideoView, 0, 0);
        mFitXY = a.getBoolean(R.styleable.UniversalVideoView_isFitXY, false);
        mAutoRotation = a.getBoolean(R.styleable.UniversalVideoView_isAutoRotation, false);
        a.recycle();
        initVideoView();
    }

    private void initVideoView() {
        mVideoWidth = 0;
        mVideoHeight = 0;
        getHolder().addCallback(mSHCallback);
        getHolder().setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        setFocusable(true);
        setFocusableInTouchMode(true);
        requestFocus();
        mCurrentState = STATE_IDLE;
        mTargetState = STATE_IDLE;
    }

    @Override
    public void onOrientationChanged(int screenOrientation, OrientationDetector.Direction direction) {
        if (!mAutoRotation) {
            return;
        }
        if (direction == OrientationDetector.Direction.PORTRAIT) {
            setFullscreen(false, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        } else if (direction == OrientationDetector.Direction.REVERSE_PORTRAIT) {
            setFullscreen(false, ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
        } else if (direction == OrientationDetector.Direction.LANDSCAPE) {
            setFullscreen(true, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        } else if (direction == OrientationDetector.Direction.REVERSE_LANDSCAPE) {
            setFullscreen(true, ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE);
        }
    }


    /**
     * 设置画面fitXY
     *
     * @param fitXY
     */
    public void setFitXY(boolean fitXY) {
        mFitXY = fitXY;
    }

    /**
     * 是否自动调整画面
     *
     * @param auto
     */
    public void setAutoRotation(boolean auto) {
        mAutoRotation = auto;
    }


    public void setVideoPath(String path) {
        setVideoURI(Uri.parse(path));
    }

    public void setVideoURI(Uri uri) {
        mUri = uri;
        mSeekWhenPrepared = 0;
        openVideo();
        requestLayout();
        invalidate();
    }

//    /**
//     * Sets video URI using specific headers.
//     *
//     * @param uri     the URI of the video.
//     * @param headers the headers for the URI request.
//     *                Note that the cross domain redirection is allowed by default, but that can be
//     *                changed with key/value pairs through the headers parameter with
//     *                "android-allow-cross-domain-redirect" as the key and "0" or "1" as the value
//     *                to disallow or allow cross domain redirection.
//     */
//    public void setVideoURI(Uri uri, Map<String, String> headers) {
//        mUri = uri;
//        mSeekWhenPrepared = 0;
//        openVideo();
//        requestLayout();
//        invalidate();
//    }


    /**
     * 停止播放
     */
    public void stopPlayback() {
        if (mMediaPlayer != null) {
            mMediaPlayer.stop();
            mMediaPlayer.release();
            mMediaPlayer = null;
            mCurrentState = STATE_IDLE;
            mTargetState = STATE_IDLE;
        }
    }

    /**
     * 开始播放
     */
    private void openVideo() {
        if (mUri == null || mSurfaceHolder == null) {
            return;
        }
        AudioManager am = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        am.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);

        //这里不清除下一个状态，因为可能之前调用了start()方法
        release(false);
        try {
            mMediaPlayer = new MediaPlayer();
            if (mAudioSession != 0) {
                mMediaPlayer.setAudioSessionId(mAudioSession);
            } else {
                mAudioSession = mMediaPlayer.getAudioSessionId();
            }
            mMediaPlayer.setOnPreparedListener(mPreparedListener);
            mMediaPlayer.setOnVideoSizeChangedListener(mVideoSizeChangedListener);
            mMediaPlayer.setOnCompletionListener(mCompletionListener);
            mMediaPlayer.setOnErrorListener(mErrorListener);
            mMediaPlayer.setOnInfoListener(mInfoListener);
            mMediaPlayer.setOnBufferingUpdateListener(mBufferingUpdateListener);
            mCurrentBufferPercentage = 0;
            mMediaPlayer.setDataSource(mContext, mUri);
            mMediaPlayer.setDisplay(mSurfaceHolder);
            mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mMediaPlayer.setScreenOnWhilePlaying(true);
            mMediaPlayer.prepareAsync();

            // 这里不设置目标状态,但保存目标状态
            mCurrentState = STATE_PREPARING;
            attachMediaController();
        } catch (IOException ex) {
            mCurrentState = STATE_ERROR;
            mTargetState = STATE_ERROR;
            mErrorListener.onError(mMediaPlayer, MediaPlayer.MEDIA_ERROR_UNKNOWN, 0);
        }
    }

    /**
     * 设置播放界面控制器
     *
     * @param controller
     */
    public void setMediaController(SimplePlayerController controller) {
        if (mMediaController != null) {
            mMediaController.hide();
        }
        mMediaController = controller;
        attachMediaController();
    }

    private void attachMediaController() {
        if (mMediaPlayer != null && mMediaController != null) {
            mMediaController.setMediaPlayer(this);
            mMediaController.setEnabled(isInPlaybackState());
            mMediaController.hide();
        }
    }

    private MediaPlayer.OnVideoSizeChangedListener mVideoSizeChangedListener =
            new MediaPlayer.OnVideoSizeChangedListener() {
                public void onVideoSizeChanged(MediaPlayer mp, int width, int height) {
                    mVideoWidth = mp.getVideoWidth();
                    mVideoHeight = mp.getVideoHeight();
                    if (mVideoWidth != 0 && mVideoHeight != 0) {
                        getHolder().setFixedSize(mVideoWidth, mVideoHeight);
//                        if (!isInLayout()) {
                        requestLayout();
//                        }
                    }
                }
            };


    private MediaPlayer.OnPreparedListener mPreparedListener = new MediaPlayer.OnPreparedListener() {
        public void onPrepared(MediaPlayer mp) {

            mCurrentState = STATE_PREPARED;
            mCanPause = mCanSeekBack = mCanSeekForward = true;
            mPreparedBeforeStart = true;
            if (mMediaController != null) {
                mMediaController.hideLoading();
            }
            if (mVideoPlayerCallback != null) {
                mVideoPlayerCallback.onPlayerPrepared(mMediaPlayer);
            }
            if (mMediaController != null) {
                mMediaController.setEnabled(true);
            }
            mVideoWidth = mp.getVideoWidth();
            mVideoHeight = mp.getVideoHeight();

            int seekToPosition = mSeekWhenPrepared;  // mSeekWhenPrepared may be changed after seekTo() call
            if (seekToPosition != 0) {
                seekTo(seekToPosition);
            }
            if (mVideoWidth != 0 && mVideoHeight != 0) {
                getHolder().setFixedSize(mVideoWidth, mVideoHeight);
                if (mSurfaceWidth == mVideoWidth && mSurfaceHeight == mVideoHeight) { //Surface大小和Video大小一致了，可以开始播放了
                    if (mTargetState == STATE_PLAYING) {
                        mMediaPlayer.seekTo(mCurrentPosition);
                        start();
                        if (mMediaController != null) {
                            mMediaController.show();
                        }
                    } else if (!isPlaying() && (seekToPosition != 0 || getCurrentPosition() > 0)) {
                        if (mMediaController != null) {
                            // 暂停时显示控制面板
                            mMediaController.show(0);
                        }
                    }
                }
            } else {
                //这种情况下不知道Video的大小，但是还是要开始播放，可能需要过一段时间才能知道视频的信息
                if (mTargetState == STATE_PLAYING) {
                    start();
                }
            }
        }
    };

    private MediaPlayer.OnCompletionListener mCompletionListener =
            new MediaPlayer.OnCompletionListener() {
                public void onCompletion(MediaPlayer mp) {
                    mCurrentState = STATE_PLAYBACK_COMPLETED;
                    mTargetState = STATE_PLAYBACK_COMPLETED;
                    if (mMediaController != null) {
                        boolean a = mMediaPlayer.isPlaying();
                        int b = mCurrentState;
                        mMediaController.showComplete();
                        //FIXME 播放完成后,视频中央会显示一个播放按钮,点击播放按钮会调用start重播,
                        // 但start后竟然又回调到这里,导致第一次点击按钮不会播放视频,需要点击第二次.
                        Log.d(TAG, String.format("a=%s,b=%d", a, b));
                    }
                    if (mVideoPlayerCallback != null) {
                        mVideoPlayerCallback.onPlayCompleted(mMediaPlayer);
                    }
                }
            };

    private MediaPlayer.OnInfoListener mInfoListener = new MediaPlayer.OnInfoListener() {
        public boolean onInfo(MediaPlayer mp, int what, int extra) {
            switch (what) {
                case MediaPlayer.MEDIA_INFO_BUFFERING_START:
                    Log.i(TAG, "------onInfo:BUFFERING_START ");
                    if (mVideoPlayerCallback != null) {
                        mVideoPlayerCallback.onBufferingStart(mMediaPlayer);
                    }
                    if (mMediaController != null) {
                        Log.i(TAG, "------onInfo:showLoading ");
                        mMediaController.showLoading();
                    }
                    break;
                case MediaPlayer.MEDIA_INFO_BUFFERING_END:
                    if (mVideoPlayerCallback != null) {
                        mVideoPlayerCallback.onBufferingEnd(mMediaPlayer);
                    }
                    if (mMediaController != null) {
                        mMediaController.hideLoading();
                    }
                    break;
                default:
                    if (mVideoPlayerCallback != null) {
                        mVideoPlayerCallback.onInfo(mp, what, extra);
                    }
                    break;
            }
            return true;
        }
    };

    private MediaPlayer.OnErrorListener mErrorListener = new MediaPlayer.OnErrorListener() {
        public boolean onError(MediaPlayer mp, int what, int extra) {
            mCurrentState = STATE_ERROR;
            mTargetState = STATE_ERROR;
            if (mMediaController != null) {
                mMediaController.showError();
            }
            //让用户自己处理
            if (mVideoPlayerCallback != null) {
                mVideoPlayerCallback.onError(mp, what, extra);
            }
            return true;
        }
    };

    private MediaPlayer.OnBufferingUpdateListener mBufferingUpdateListener = new MediaPlayer.OnBufferingUpdateListener() {
        public void onBufferingUpdate(MediaPlayer mp, int percent) {
            mCurrentBufferPercentage = percent;
            if (mVideoPlayerCallback != null) {
                mVideoPlayerCallback.onBuffering(percent);
            }
        }
    };


    private SurfaceHolder.Callback mSHCallback = new SurfaceHolder.Callback() {
        public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
            mSurfaceWidth = w;
            mSurfaceHeight = h;
            boolean isValidState = (mTargetState == STATE_PLAYING);
            boolean hasValidSize = (mVideoWidth == w && mVideoHeight == h);
            if (mMediaPlayer != null && isValidState && hasValidSize) {
                if (mSeekWhenPrepared != 0) {
                    seekTo(mSeekWhenPrepared);
                }
                start();
            }
        }

        public void surfaceCreated(SurfaceHolder holder) {
            mSurfaceHolder = holder;
            openVideo();
            enableOrientationDetect();
        }

        public void surfaceDestroyed(SurfaceHolder holder) {
            mSurfaceHolder = null;
            if (mMediaController != null) {
                mMediaController.hide();
            }
            release(true);
            disableOrientationDetect();
        }
    };

    private void enableOrientationDetect() {
        if (mAutoRotation && mOrientationDetector == null) {
            mOrientationDetector = new OrientationDetector(mContext);
            mOrientationDetector.setOrientationChangeListener(UniversalVideoView.this);
            mOrientationDetector.enable();
        }
    }

    private void disableOrientationDetect() {
        if (mOrientationDetector != null) {
            mOrientationDetector.disable();
        }
    }

    /**
     * 释放播放器
     *
     * @param clearTargetState 是否清除目标状态
     */
    private void release(boolean clearTargetState) {
        if (mMediaPlayer != null) {
            mMediaPlayer.reset();
            mMediaPlayer.release();
            mMediaPlayer = null;
            mCurrentState = STATE_IDLE;
            if (clearTargetState) {
                mTargetState = STATE_IDLE;
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (isInPlaybackState() && mMediaController != null) {
            toggleMediaControlsVisibility();
        }
        return false;
    }

    @Override
    public boolean onTrackballEvent(MotionEvent ev) {
        if (isInPlaybackState() && mMediaController != null) {
            toggleMediaControlsVisibility();
        }
        return false;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        boolean isKeyCodeSupported = keyCode != KeyEvent.KEYCODE_BACK &&
                keyCode != KeyEvent.KEYCODE_VOLUME_UP &&
                keyCode != KeyEvent.KEYCODE_VOLUME_DOWN &&
                keyCode != KeyEvent.KEYCODE_VOLUME_MUTE &&
                keyCode != KeyEvent.KEYCODE_MENU &&
                keyCode != KeyEvent.KEYCODE_CALL &&
                keyCode != KeyEvent.KEYCODE_ENDCALL;
        if (isInPlaybackState() && isKeyCodeSupported && mMediaController != null) {
            if (keyCode == KeyEvent.KEYCODE_HEADSETHOOK ||
                    keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
                if (mMediaPlayer.isPlaying()) {
                    pause();
                    mMediaController.show();
                } else {
                    start();
                    mMediaController.hide();
                }
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY) {
                if (!mMediaPlayer.isPlaying()) {
                    start();
                    mMediaController.hide();
                }
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_MEDIA_STOP
                    || keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE) {
                if (mMediaPlayer.isPlaying()) {
                    pause();
                    mMediaController.show();
                }
                return true;
            } else {
                toggleMediaControlsVisibility();
            }
        }

        return super.onKeyDown(keyCode, event);
    }

    private void toggleMediaControlsVisibility() {
        if (mMediaController.isShowing()) {
            mMediaController.hide();
        } else {
            mMediaController.show();
        }
    }

    /**
     * 恢复播放
     */
    public void rePlay() {
        if ((mCurrentState == STATE_PAUSED) && (mCurrentPosition > 0 && mCurrentPosition < mVideoDuring)) {
            start();
        }
    }

    @Override
    public void start() {
        if (!mPreparedBeforeStart && mMediaController != null) {
            Log.i(TAG, "------start:showLoading ");
            mMediaController.showLoading();
        }
        mVideoDuring = getDuration();
        Log.i(TAG, "-----onPrepared_mVideoDuring" + mVideoDuring);
        if (isInPlaybackState()) {
            Log.i(TAG, "-----isInPlaybackState" + isInPlaybackState());
            mMediaPlayer.start();
            mCurrentState = STATE_PLAYING;
            if (this.mVideoPlayerCallback != null) {
                this.mVideoPlayerCallback.onPlayerStart(mMediaPlayer);
            }
        }
        mTargetState = STATE_PLAYING;
    }

    @Override
    public void pause() {
        pause(true);
    }

    /**
     * @param normalPause 如果是响应屏幕关闭用false,正常点击按钮暂停时true
     */
    public void pause(boolean normalPause) {
        if (isInPlaybackState()) {
            if (mMediaPlayer.isPlaying()) {
                mCurrentPosition = getCurrentPosition();
                if (normalPause) {
                    mMediaPlayer.pause();
                } else {
                    release(false);
                }
                mCurrentState = STATE_PAUSED;
                if (this.mVideoPlayerCallback != null) {
                    this.mVideoPlayerCallback.onPlayerPause(mCurrentPosition);
                }
            }
        }
        if (normalPause) {
            mTargetState = STATE_PAUSED;
        } else {
            mPreparedBeforeStart = false;
            mTargetState = STATE_PLAYING;
        }
    }

    @Override
    public int getDuration() {
        if (isInPlaybackState()) {
            return mMediaPlayer.getDuration();
        }
        return -1;
    }

    @Override
    public int getCurrentPosition() {
        if (isInPlaybackState()) {
            return mMediaPlayer.getCurrentPosition();
        }
        return 0;
    }

    @Override
    public void seekTo(int msec) {
        if (isInPlaybackState()) {
            mMediaPlayer.seekTo(msec);
            mSeekWhenPrepared = 0;
        } else {
            mSeekWhenPrepared = msec;
        }
    }

    @Override
    public boolean isPlaying() {
        return isInPlaybackState() && mMediaPlayer.isPlaying();
    }

    @Override
    public int getBufferPercentage() {
        if (mMediaPlayer != null) {
            return mCurrentBufferPercentage;
        }
        return 0;
    }

    /**
     * 是否是播放状态
     *
     * @return
     */
    private boolean isInPlaybackState() {
        return (mMediaPlayer != null &&
                mCurrentState != STATE_ERROR &&
                mCurrentState != STATE_IDLE &&
                mCurrentState != STATE_PREPARING);
    }

    @Override
    public boolean canPause() {
        return mCanPause;
    }

    @Override
    public boolean canSeekBackward() {
        return mCanSeekBack;
    }

    @Override
    public boolean canSeekForward() {
        return mCanSeekForward;
    }

    @Override
    public void closePlayer() {
        release(true);
    }

    @Override
    public void setFullscreen(boolean fullscreen) {
        int screenOrientation = fullscreen ? ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
        setFullscreen(fullscreen, screenOrientation);
    }

    @Override
    public void setFullscreen(boolean fullscreen, int screenOrientation) {
        // Activity需要设置为: android:configChanges="keyboardHidden|orientation|screenSize"
        Activity activity = (Activity) mContext;

        if (fullscreen) {
            if (mVideoViewLayoutWidth == 0 && mVideoViewLayoutHeight == 0) {
                ViewGroup.LayoutParams params = getLayoutParams();
                mVideoViewLayoutWidth = params.width;//保存全屏之前的参数
                mVideoViewLayoutHeight = params.height;
            }
            activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            activity.setRequestedOrientation(screenOrientation);
        } else {
            ViewGroup.LayoutParams params = getLayoutParams();
            params.width = mVideoViewLayoutWidth;//使用全屏之前的参数
            params.height = mVideoViewLayoutHeight;
            setLayoutParams(params);

            activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            activity.setRequestedOrientation(screenOrientation);
        }
        mMediaController.toggleButtons(fullscreen);
        if (mVideoPlayerCallback != null) {
            mVideoPlayerCallback.onScaleChange(fullscreen);
        }
    }

    public void setVideoPlayerCallback(VideoPlayerCallback callback) {
        this.mVideoPlayerCallback = callback;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (mFitXY) {
            onMeasureFitXY(widthMeasureSpec, heightMeasureSpec);
        } else {
            onMeasureKeepAspectRatio(widthMeasureSpec, heightMeasureSpec);
        }
    }

    private void onMeasureFitXY(int widthMeasureSpec, int heightMeasureSpec) {
        int width = getDefaultSize(mVideoWidth, widthMeasureSpec);
        int height = getDefaultSize(mVideoHeight, heightMeasureSpec);
        setMeasuredDimension(width, height);
    }

    private void onMeasureKeepAspectRatio(int widthMeasureSpec, int heightMeasureSpec) {
        int width = getDefaultSize(mVideoWidth, widthMeasureSpec);
        int height = getDefaultSize(mVideoHeight, heightMeasureSpec);
        if (mVideoWidth > 0 && mVideoHeight > 0) {
            int widthSpecMode = MeasureSpec.getMode(widthMeasureSpec);
            int widthSpecSize = MeasureSpec.getSize(widthMeasureSpec);
            int heightSpecMode = MeasureSpec.getMode(heightMeasureSpec);
            int heightSpecSize = MeasureSpec.getSize(heightMeasureSpec);
            if (widthSpecMode == MeasureSpec.EXACTLY && heightSpecMode == MeasureSpec.EXACTLY) {
                // the size is fixed
                width = widthSpecSize;
                height = heightSpecSize;
                // for compatibility, we adjust size based on aspect ratio
                if (mVideoWidth * height < width * mVideoHeight) {
                    //Log.i("@@@", "image too wide, correcting");
                    width = height * mVideoWidth / mVideoHeight;
                } else if (mVideoWidth * height > width * mVideoHeight) {
                    //Log.i("@@@", "image too tall, correcting");
                    height = width * mVideoHeight / mVideoWidth;
                }
            } else if (widthSpecMode == MeasureSpec.EXACTLY) {
                // only the width is fixed, adjust the height to match aspect ratio if possible
                width = widthSpecSize;
                height = width * mVideoHeight / mVideoWidth;
                if (heightSpecMode == MeasureSpec.AT_MOST && height > heightSpecSize) {
                    // couldn't match aspect ratio within the constraints
                    height = heightSpecSize;
                }
            } else if (heightSpecMode == MeasureSpec.EXACTLY) {
                // only the height is fixed, adjust the width to match aspect ratio if possible
                height = heightSpecSize;
                width = height * mVideoWidth / mVideoHeight;
                if (widthSpecMode == MeasureSpec.AT_MOST && width > widthSpecSize) {
                    // couldn't match aspect ratio within the constraints
                    width = widthSpecSize;
                }
            } else {
                // neither the width nor the height are fixed, try to use actual video size
                width = mVideoWidth;
                height = mVideoHeight;
                if (heightSpecMode == MeasureSpec.AT_MOST && height > heightSpecSize) {
                    // too tall, decrease both width and height
                    height = heightSpecSize;
                    width = height * mVideoWidth / mVideoHeight;
                }
                if (widthSpecMode == MeasureSpec.AT_MOST && width > widthSpecSize) {
                    // too wide, decrease both width and height
                    width = widthSpecSize;
                    height = width * mVideoHeight / mVideoWidth;
                }
            }
        } else {
            // no size yet, just adopt the given spec sizes
        }
        setMeasuredDimension(width, height);
    }

    @Override
    public void onInitializeAccessibilityEvent(AccessibilityEvent event) {
        super.onInitializeAccessibilityEvent(event);
        event.setClassName(UniversalVideoView.class.getName());
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            info.setClassName(UniversalVideoView.class.getName());
        }
    }
}
