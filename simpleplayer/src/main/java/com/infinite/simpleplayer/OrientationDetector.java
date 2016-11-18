
package com.infinite.simpleplayer;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.hardware.SensorManager;
import android.view.OrientationEventListener;


public class OrientationDetector {

    private static final int HOLDING_THRESHOLD = 500;  //方向变换时间阈值
    private Context mContext;
    private OrientationEventListener mOrientationEventListener;
    private OrientationChangeListener mChangeListener;

    private int mRotationThreshold = 20;  //方向敏感度阈值
    private long mHoldingTime = 0;
    private long mLastCalcTime = 0;
    private Direction mLastDirection = Direction.PORTRAIT;  //上一次的方向
    private int mCurrentOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;//初始为竖屏


    public OrientationDetector(Context context) {
        this.mContext = context;
    }

    /**
     * 设置初始方向
     *
     * @param direction
     */
    public void setInitialDirection(Direction direction) {
        mLastDirection = direction;
    }

    /**
     * 设置方向感应的敏感阈值
     *
     * @param degree
     */
    public void setThresholdDegree(int degree) {
        mRotationThreshold = degree;
    }


    /**
     * 方向改变的监听
     *
     * @param listener
     */
    public void setOrientationChangeListener(OrientationChangeListener listener) {
        this.mChangeListener = listener;
    }

    public void enable() {
        if (mOrientationEventListener == null) {
            mOrientationEventListener = new OrientationEventListener(mContext, SensorManager.SENSOR_DELAY_UI) {
                @Override
                public void onOrientationChanged(int orientation) {
                    Direction currDirection = calcDirection(orientation);
                    if (currDirection == null) {
                        return;
                    }
                    if (currDirection != mLastDirection) {
                        mHoldingTime = mLastCalcTime = 0;
                        mLastDirection = currDirection;
                    } else {
                        calcHoldingTime();
                        if (mHoldingTime > HOLDING_THRESHOLD) {
                            switch (currDirection) {
                                case LANDSCAPE://正向横屏
                                    OnOrientationChange(currDirection, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                                    break;
                                case PORTRAIT://正向竖屏
                                    OnOrientationChange(currDirection, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                                    break;
                                case REVERSE_PORTRAIT://反向竖屏
                                    OnOrientationChange(currDirection, ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT);
                                    break;
                                case REVERSE_LANDSCAPE://反向横屏
                                    OnOrientationChange(currDirection, ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE);
                                    break;
                            }

                        }
                    }

                }
            };
        }
        mOrientationEventListener.enable();
    }


    /**
     * 关闭方向感应监听
     */
    public void disable() {
        if (mOrientationEventListener != null) {
            mOrientationEventListener.disable();
        }
    }

    private void OnOrientationChange(Direction orientation, int newOrientation) {
        if (mCurrentOrientation != newOrientation) {
            mCurrentOrientation = newOrientation;
            if (mChangeListener != null) {
                mChangeListener.onOrientationChanged(newOrientation, orientation);
            }
        }
    }

    public enum Direction {
        PORTRAIT, REVERSE_PORTRAIT, LANDSCAPE, REVERSE_LANDSCAPE
    }

    private void calcHoldingTime() {
        long current = System.currentTimeMillis();
        if (mLastCalcTime == 0) {
            mLastCalcTime = current;
        }
        mHoldingTime += current - mLastCalcTime;
        mLastCalcTime = current;
    }

    private Direction calcDirection(int orientation) {
        if (orientation <= mRotationThreshold || orientation >= 360 - mRotationThreshold) {
            return Direction.PORTRAIT;
        } else if (Math.abs(orientation - 180) <= mRotationThreshold) {
            return Direction.REVERSE_PORTRAIT;
        } else if (Math.abs(orientation - 90) <= mRotationThreshold) {
            return Direction.REVERSE_LANDSCAPE;
        } else if (Math.abs(orientation - 270) <= mRotationThreshold) {
            return Direction.LANDSCAPE;
        }
        return null;
    }

}
