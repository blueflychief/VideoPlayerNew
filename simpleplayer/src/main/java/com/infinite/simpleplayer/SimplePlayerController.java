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

import android.content.Context;
import android.content.res.TypedArray;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;

import java.util.Formatter;
import java.util.Locale;

public class SimplePlayerController extends FrameLayout {

    private Context mContext;

    private MediaPlayerControl mPlayerControl;
    private boolean mIsShowing = true;
    private boolean mIsDragging;
    private boolean mIsScalable = false;
    private boolean mIsFullScreen = false;
//    private boolean mFullscreenEnabled = false;

    private static final int sControlShowTime = 3000;

    private static final int STATE_PLAYING = 1;
    private static final int STATE_PAUSE = 2;
    private static final int STATE_LOADING = 3;
    private static final int STATE_ERROR = 4;
    private static final int STATE_COMPLETE = 5;

    private int mState = STATE_LOADING;


    private static final int FADE_OUT = 1;
    private static final int SHOW_PROGRESS = 2;
    private static final int SHOW_LOADING = 3;
    private static final int HIDE_LOADING = 4;
    private static final int SHOW_ERROR = 5;
    private static final int HIDE_ERROR = 6;
    private static final int SHOW_COMPLETE = 7;
    private static final int HIDE_COMPLETE = 8;
    private StringBuilder mFormatBuilder;

    private Formatter mFormatter;

    private ProgressBar mPbProgress;
    private TextView mTvEndTime;
    private TextView mTvCurrentTime;
    private TextView mTvTitle;
    private ImageButton mBtSwitch;
    private ImageButton mBtScale;

    private View mBtBack;// 返回按钮
    private ViewGroup mVgLoading;
    private ViewGroup errorLayout;
    private View mTitleLayout;
    private View mControlLayout;
    private View mCenterPlayButton;

    public SimplePlayerController(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        TypedArray a = mContext.obtainStyledAttributes(attrs, R.styleable.SimplePlayerController);
        mIsScalable = a.getBoolean(R.styleable.SimplePlayerController_scalable, false);
        a.recycle();
        init(context);
    }

    public SimplePlayerController(Context context) {
        super(context);
        init(context);
    }

    private void init(Context context) {
        mContext = context;
        LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View viewRoot = inflater.inflate(R.layout.uvv_player_controller, this);
        viewRoot.setOnTouchListener(mTouchListener);
        initControllerView(viewRoot);
    }


    private void initControllerView(View v) {
        mTitleLayout = v.findViewById(R.id.title_part);
        mControlLayout = v.findViewById(R.id.control_layout);
        mVgLoading = (ViewGroup) v.findViewById(R.id.ll_loading);
        errorLayout = (ViewGroup) v.findViewById(R.id.error_layout);
        mBtSwitch = (ImageButton) v.findViewById(R.id.turn_button);
        mBtScale = (ImageButton) v.findViewById(R.id.scale_button);
        mCenterPlayButton = v.findViewById(R.id.center_play_btn);
        mBtBack = v.findViewById(R.id.back_btn);

        if (mBtSwitch != null) {
            mBtSwitch.requestFocus();
            mBtSwitch.setOnClickListener(mPauseListener);
        }

        if (mIsScalable) {
            if (mBtScale != null) {
                mBtScale.setVisibility(VISIBLE);
                mBtScale.setOnClickListener(mScaleListener);
            }
        } else {
            if (mBtScale != null) {
                mBtScale.setVisibility(GONE);
            }
        }

        if (mCenterPlayButton != null) {//重新开始播放
            mCenterPlayButton.setOnClickListener(mCenterPlayListener);
        }

        if (mBtBack != null) {//返回按钮仅在全屏状态下可见
            mBtBack.setOnClickListener(mBackListener);
        }

        View bar = v.findViewById(R.id.seekbar);
        mPbProgress = (ProgressBar) bar;
        if (mPbProgress != null) {
            if (mPbProgress instanceof SeekBar) {
                SeekBar seeker = (SeekBar) mPbProgress;
                seeker.setOnSeekBarChangeListener(mSeekListener);
            }
            mPbProgress.setMax(1000);
        }

        mTvEndTime = (TextView) v.findViewById(R.id.duration);
        mTvCurrentTime = (TextView) v.findViewById(R.id.has_played);
        mTvTitle = (TextView) v.findViewById(R.id.title);
        mFormatBuilder = new StringBuilder();
        mFormatter = new Formatter(mFormatBuilder, Locale.getDefault());
    }


    public void setMediaPlayer(MediaPlayerControl player) {
        mPlayerControl = player;
        updatePausePlay();
    }

    /**
     * Show the controller on screen. It will go away
     * automatically after 3 seconds of inactivity.
     */
    public void show() {
        show(sControlShowTime);
    }

    /**
     * Disable pause or seek buttons if the stream cannot be paused or seeked.
     * This requires the control interface to be a MediaPlayerControlExt
     */
    private void disableUnsupportedButtons() {
        try {
            if (mBtSwitch != null && mPlayerControl != null && !mPlayerControl.canPause()) {
                mBtSwitch.setEnabled(false);
            }
        } catch (IncompatibleClassChangeError ex) {
            // We were given an old version of the interface, that doesn't have
            // the canPause/canSeekXYZ methods. This is OK, it just means we
            // assume the media can be paused and seeked, and so we don't disable
            // the buttons.
        }
    }

    /**
     * Show the controller on screen. It will go away
     * automatically after 'timeout' milliseconds of inactivity.
     *
     * @param timeout The timeout in milliseconds. Use 0 to show
     *                the controller until hide() is called.
     */
    public void show(int timeout) {//只负责上下两条bar的显示,不负责中央loading,error,playBtn的显示.
        if (!mIsShowing) {
            setProgress();
            if (mBtSwitch != null) {
                mBtSwitch.requestFocus();
            }
            disableUnsupportedButtons();
            mIsShowing = true;
        }
        updatePausePlay();
        updateBackButton();

        if (getVisibility() != VISIBLE) {
            setVisibility(VISIBLE);
        }
        if (mTitleLayout.getVisibility() != VISIBLE) {
            mTitleLayout.setVisibility(VISIBLE);
        }
        if (mControlLayout.getVisibility() != VISIBLE) {
            mControlLayout.setVisibility(VISIBLE);
        }

        // cause the progress bar to be updated even if mIsShowing
        // was already true. This happens, for example, if we're
        // paused with the progress bar showing the user hits play.
        mHandler.sendEmptyMessage(SHOW_PROGRESS);

        Message msg = mHandler.obtainMessage(FADE_OUT);
        if (timeout != 0) {
            mHandler.removeMessages(FADE_OUT);
            mHandler.sendMessageDelayed(msg, timeout);
        }
    }

    public boolean isShowing() {
        return mIsShowing;
    }


    public void hide() {//只负责上下两条bar的隐藏,不负责中央loading,error,playBtn的隐藏
        if (mIsShowing) {
            mHandler.removeMessages(SHOW_PROGRESS);
            mTitleLayout.setVisibility(GONE);
            mControlLayout.setVisibility(GONE);
            mIsShowing = false;
        }
    }


    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            int pos;
            switch (msg.what) {
                case FADE_OUT: //1
                    hide();
                    break;
                case SHOW_PROGRESS: //2
                    pos = setProgress();
                    if (!mIsDragging && mIsShowing && mPlayerControl != null && mPlayerControl.isPlaying()) {
                        msg = obtainMessage(SHOW_PROGRESS);
                        sendMessageDelayed(msg, 1000 - (pos % 1000));
                    }
                    break;
                case SHOW_LOADING: //3
                    show();
                    showCenterView(R.id.ll_loading);
                    break;
                case SHOW_COMPLETE: //7
                    showCenterView(R.id.center_play_btn);
                    break;
                case SHOW_ERROR: //5
                    show();
                    showCenterView(R.id.error_layout);
                    break;
                case HIDE_LOADING: //4
                case HIDE_ERROR: //6
                case HIDE_COMPLETE: //8
                    hide();
                    hideCenterView();
                    break;
            }
        }
    };

    private void showCenterView(int resId) {
        if (resId == R.id.ll_loading) {
            if (mVgLoading.getVisibility() != VISIBLE) {
                mVgLoading.setVisibility(VISIBLE);
            }
            if (mCenterPlayButton.getVisibility() == VISIBLE) {
                mCenterPlayButton.setVisibility(GONE);
            }
            if (errorLayout.getVisibility() == VISIBLE) {
                errorLayout.setVisibility(GONE);
            }
        } else if (resId == R.id.center_play_btn) {
            if (mCenterPlayButton.getVisibility() != VISIBLE) {
                mCenterPlayButton.setVisibility(VISIBLE);
            }
            if (mVgLoading.getVisibility() == VISIBLE) {
                mVgLoading.setVisibility(GONE);
            }
            if (errorLayout.getVisibility() == VISIBLE) {
                errorLayout.setVisibility(GONE);
            }

        } else if (resId == R.id.error_layout) {
            if (errorLayout.getVisibility() != VISIBLE) {
                errorLayout.setVisibility(VISIBLE);
            }
            if (mCenterPlayButton.getVisibility() == VISIBLE) {
                mCenterPlayButton.setVisibility(GONE);
            }
            if (mVgLoading.getVisibility() == VISIBLE) {
                mVgLoading.setVisibility(GONE);
            }

        }
    }


    private void hideCenterView() {
        if (mCenterPlayButton.getVisibility() == VISIBLE) {
            mCenterPlayButton.setVisibility(GONE);
        }
        if (errorLayout.getVisibility() == VISIBLE) {
            errorLayout.setVisibility(GONE);
        }
        if (mVgLoading.getVisibility() == VISIBLE) {
            mVgLoading.setVisibility(GONE);
        }
    }

    public void reset() {
        mTvCurrentTime.setText("00:00");
        mTvEndTime.setText("00:00");
        mPbProgress.setProgress(0);
        mBtSwitch.setImageResource(R.drawable.uvv_player_player_btn);
        setVisibility(View.VISIBLE);
        hideLoading();
    }

    private String stringForTime(int timeMs) {
        int totalSeconds = timeMs / 1000;

        int seconds = totalSeconds % 60;
        int minutes = (totalSeconds / 60) % 60;
        int hours = totalSeconds / 3600;

        mFormatBuilder.setLength(0);
        if (hours > 0) {
            return mFormatter.format("%d:%02d:%02d", hours, minutes, seconds).toString();
        } else {
            return mFormatter.format("%02d:%02d", minutes, seconds).toString();
        }
    }

    private int setProgress() {
        if (mPlayerControl == null || mIsDragging) {
            return 0;
        }
        int position = mPlayerControl.getCurrentPosition();
        int duration = mPlayerControl.getDuration();
        if (mPbProgress != null) {
            if (duration > 0) {
                // use long to avoid overflow
                long pos = 1000L * position / duration;
                mPbProgress.setProgress((int) pos);
            }
            int percent = mPlayerControl.getBufferPercentage();
            mPbProgress.setSecondaryProgress(percent * 10);
        }

        if (mTvEndTime != null)
            mTvEndTime.setText(stringForTime(duration));
        if (mTvCurrentTime != null)
            mTvCurrentTime.setText(stringForTime(position));

        return position;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                show(0); // show until hide is called
                handled = false;
                break;
            case MotionEvent.ACTION_UP:
                if (!handled) {
                    handled = false;
                    show(sControlShowTime); // start timeout
                }
                break;
            case MotionEvent.ACTION_CANCEL:
                hide();
                break;
            default:
                break;
        }
        return true;
    }

    boolean handled = false;
    //如果正在显示,则使之消失
    private OnTouchListener mTouchListener = new OnTouchListener() {
        public boolean onTouch(View v, MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                if (mIsShowing) {
                    hide();
                    handled = true;
                    return true;
                }
            }
            return false;
        }
    };

    @Override
    public boolean onTrackballEvent(MotionEvent ev) {
        show(sControlShowTime);
        return false;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();
        final boolean uniqueDown = event.getRepeatCount() == 0
                && event.getAction() == KeyEvent.ACTION_DOWN;
        if (keyCode == KeyEvent.KEYCODE_HEADSETHOOK
                || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                || keyCode == KeyEvent.KEYCODE_SPACE) {
            if (uniqueDown) {
                doPauseResume();
                show(sControlShowTime);
                if (mBtSwitch != null) {
                    mBtSwitch.requestFocus();
                }
            }
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY) {
            if (uniqueDown && !mPlayerControl.isPlaying()) {
                mPlayerControl.start();
                updatePausePlay();
                show(sControlShowTime);
            }
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_MEDIA_STOP
                || keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE) {
            if (uniqueDown && mPlayerControl.isPlaying()) {
                mPlayerControl.pause();
                updatePausePlay();
                show(sControlShowTime);
            }
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
                || keyCode == KeyEvent.KEYCODE_VOLUME_UP
                || keyCode == KeyEvent.KEYCODE_VOLUME_MUTE
                || keyCode == KeyEvent.KEYCODE_CAMERA) {
            // don't show the controls for volume adjustment
            return super.dispatchKeyEvent(event);
        } else if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_MENU) {
            if (uniqueDown) {
                hide();
            }
            return true;
        }

        show(sControlShowTime);
        return super.dispatchKeyEvent(event);
    }

    private View.OnClickListener mPauseListener = new View.OnClickListener() {
        public void onClick(View v) {
            if (mPlayerControl != null) {
                doPauseResume();
                show(sControlShowTime);
            }
        }
    };

    private View.OnClickListener mScaleListener = new View.OnClickListener() {
        public void onClick(View v) {
            mIsFullScreen = !mIsFullScreen;
            updateScaleButton();
            updateBackButton();
            mPlayerControl.setFullscreen(mIsFullScreen);
        }
    };

    //仅全屏时才有返回按钮
    private View.OnClickListener mBackListener = new View.OnClickListener() {
        public void onClick(View v) {
            if (mIsFullScreen) {
                mIsFullScreen = false;
                updateScaleButton();
                updateBackButton();
                mPlayerControl.setFullscreen(false);
            }

        }
    };

    private View.OnClickListener mCenterPlayListener = new View.OnClickListener() {
        public void onClick(View v) {
            hideCenterView();
            mPlayerControl.start();
        }
    };

    private void updatePausePlay() {
        if (mPlayerControl != null && mPlayerControl.isPlaying()) {
            mBtSwitch.setImageResource(R.drawable.uvv_stop_btn);
//            mCenterPlayButton.setVisibility(GONE);
        } else {
            mBtSwitch.setImageResource(R.drawable.uvv_player_player_btn);
//            mCenterPlayButton.setVisibility(VISIBLE);
        }
    }

    void updateScaleButton() {
        if (mIsFullScreen) {
            mBtScale.setImageResource(R.drawable.uvv_star_zoom_in);
        } else {
            mBtScale.setImageResource(R.drawable.uvv_player_scale_btn);
        }
    }

    void toggleButtons(boolean isFullScreen) {
        mIsFullScreen = isFullScreen;
        updateScaleButton();
        updateBackButton();
    }

    void updateBackButton() {
        mBtBack.setVisibility(mIsFullScreen ? View.VISIBLE : View.INVISIBLE);
    }

    boolean isFullScreen() {
        return mIsFullScreen;
    }

    private void doPauseResume() {
        if (mPlayerControl.isPlaying()) {
            mPlayerControl.pause();
        } else {
            mPlayerControl.start();
        }
        updatePausePlay();
    }


    private OnSeekBarChangeListener mSeekListener = new OnSeekBarChangeListener() {
        int newPosition = 0;

        boolean change = false;

        public void onStartTrackingTouch(SeekBar bar) {
            if (mPlayerControl == null) {
                return;
            }
            show(3600000);

            mIsDragging = true;
            mHandler.removeMessages(SHOW_PROGRESS);
        }

        public void onProgressChanged(SeekBar bar, int progress, boolean fromuser) {
            if (mPlayerControl == null || !fromuser) {
                // We're not interested in programmatically generated changes to
                // the progress bar's position.
                return;
            }

            long duration = mPlayerControl.getDuration();
            long newposition = (duration * progress) / 1000L;
            newPosition = (int) newposition;
            change = true;
        }

        public void onStopTrackingTouch(SeekBar bar) {
            if (mPlayerControl == null) {
                return;
            }
            if (change) {
                mPlayerControl.seekTo(newPosition);
                if (mTvCurrentTime != null) {
                    mTvCurrentTime.setText(stringForTime(newPosition));
                }
            }
            mIsDragging = false;
            setProgress();
            updatePausePlay();
            show(sControlShowTime);

            // Ensure that progress is properly updated in the future,
            // the call to show() does not guarantee this because it is a
            // no-op if we are already showing.
            mIsShowing = true;
            mHandler.sendEmptyMessage(SHOW_PROGRESS);
        }
    };

    @Override
    public void setEnabled(boolean enabled) {
//        super.setEnabled(enabled);
        if (mBtSwitch != null) {
            mBtSwitch.setEnabled(enabled);
        }
        if (mPbProgress != null) {
            mPbProgress.setEnabled(enabled);
        }
        if (mIsScalable) {
            mBtScale.setEnabled(enabled);
        }
        mBtBack.setEnabled(true);// 全屏状态下右上角的返回键总是可用.
    }

    public void showLoading() {
        mHandler.sendEmptyMessage(SHOW_LOADING);
    }

    public void hideLoading() {
        mHandler.sendEmptyMessage(HIDE_LOADING);
    }

    public void showError() {
        mHandler.sendEmptyMessage(SHOW_ERROR);
    }

    public void hideError() {
        mHandler.sendEmptyMessage(HIDE_ERROR);
    }

    public void showComplete() {
        mHandler.sendEmptyMessage(SHOW_COMPLETE);
    }

    public void hideComplete() {
        mHandler.sendEmptyMessage(HIDE_COMPLETE);
    }

    public void setTitle(String titile) {
        mTvTitle.setText(titile);
    }

//    public void setFullscreenEnabled(boolean enabled) {
//        mFullscreenEnabled = enabled;
//        mBtScale.setVisibility(mIsFullScreen ? VISIBLE : GONE);
//    }


    public void setOnErrorView(int resId) {
        errorLayout.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(mContext);
        inflater.inflate(resId, errorLayout, true);
    }

    public void setOnErrorView(View onErrorView) {
        errorLayout.removeAllViews();
        errorLayout.addView(onErrorView);
    }

    public void setOnLoadingView(int resId) {
        mVgLoading.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(mContext);
        inflater.inflate(resId, mVgLoading, true);
    }

    public void setOnLoadingView(View onLoadingView) {
        mVgLoading.removeAllViews();
        mVgLoading.addView(onLoadingView);
    }

    public void setOnErrorViewClick(View.OnClickListener onClickListener) {
        errorLayout.setOnClickListener(onClickListener);
    }

}
