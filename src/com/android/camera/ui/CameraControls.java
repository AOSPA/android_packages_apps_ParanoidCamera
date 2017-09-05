/*
 * Copyright (C) 2013 The Android Open Source Project
 * Copyright (C) 2017 Paranoid Android
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.camera.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewPropertyAnimator;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;

import com.android.camera.Storage;

import co.paranoidandroid.camera.R;

public class CameraControls extends RotatableLayout implements Rotatable {

    private static final String TAG = "CAM_Controls";

    private CameraControlsBar mCameraControlsBar;
    private View mCameraControlsBarArrow;
    private View mControlsParent;
    private View mShutter;
    private View mVideoShutter;
    private View mExitPanorama;
    private View mPreview;
    private FrontBackSwitch mFrontBackSwitcher;
    private RemainingPhotos mRemainingPhotos;
    private ArrowTextView mRefocusToast;

    private static final int WIDTH_GRID = 5;
    private static final int HEIGHT_GRID = 7;

    private ArrayList<View> mViewList;
    private ArrayList<View> mVisibleViewsList;
    private boolean mShowCameraControlsSettings;

    public CameraControls(Context context) {
        this(context, null);
    }

    public CameraControls(Context context, AttributeSet attrs) {
        super(context, attrs);

        mRefocusToast = new ArrowTextView(context);
        addView(mRefocusToast);
        setClipChildren(false);

        setMeasureAllChildren(true);
    }

    public View getPanoramaExitButton() {
        return mExitPanorama;
    }

    private void enableTouch(boolean enable) {
        if (enable) {
            mShutter.setPressed(false);
            mVideoShutter.setPressed(false);
            mExitPanorama.setPressed(false);
        }

        mCameraControlsBar.setClickable(enable);
        ((ShutterButton) mShutter).enableTouch(enable);
        mVideoShutter.setClickable(enable);
        mExitPanorama.setClickable(enable);
        mPreview.setClickable(enable);
        mFrontBackSwitcher.setClickable(enable);
    }

    @Override
    public void onFinishInflate() {
        super.onFinishInflate();
        mViewList = new ArrayList<>();
        mVisibleViewsList = new ArrayList<>();

        mControlsParent = findViewById(R.id.camera_controls_row);

        mCameraControlsBar = (CameraControlsBar)
                findViewById(R.id.camera_controls_bar);
        mCameraControlsBarArrow = findViewById(R.id.camera_controls_bar_arrow);
        mShutter = findViewById(R.id.shutter_button);
        mVideoShutter = findViewById(R.id.video_button);
        mExitPanorama = findViewById(R.id.exit_panorama);
        mPreview = findViewById(R.id.preview_thumb);
        mFrontBackSwitcher = (FrontBackSwitch) findViewById(R.id.front_back_switcher);
        mRemainingPhotos = (RemainingPhotos) findViewById(R.id.remaining_photos);

        mExitPanorama.setVisibility(View.GONE);

        mViewList.add(mCameraControlsBar);
        mViewList.add(mShutter);
        mViewList.add(mVideoShutter);
        mViewList.add(mExitPanorama);
        mViewList.add(findViewById(R.id.on_screen_indicators));
        mViewList.add(mPreview);
        mViewList.add(mFrontBackSwitcher);
        mViewList.add(findViewById(R.id.done_button));
        mViewList.add(findViewById(R.id.btn_cancel));
        mViewList.add(findViewById(R.id.btn_retake));
        mViewList.add(mRemainingPhotos);

        mCameraControlsBarArrow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mCameraControlsBar.isCameraControlsSettingsVisible()) {
                    collapseCameraControlsSettings(true);
                } else {
                    showCameraControlsSettings(true);
                }
            }
        });
    }

    @Override
    public void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        // As l,t,r,b are positions relative to parents, we need to convert them
        // to child's coordinates
        r = r - l;
        b = b - t;
        layoutToast(mRefocusToast, r, b);
    }

    private void layoutToast(final View v, int w, int h) {
        int tw = v.getMeasuredWidth();
        int th = v.getMeasuredHeight();
        int l = w / WIDTH_GRID / 4;
        int b = (int) (h / HEIGHT_GRID * (HEIGHT_GRID - 1.25));
        int r = l + tw;
        int t = b - th;
        mRefocusToast.setArrow(0, th, th / 2, th, 0, th * 3 / 2);
        mRefocusToast.layout(l, t, r, b);
    }

    private void markVisibility() {
        mVisibleViewsList.clear();
        for (View view : mViewList) {
            if (view.getVisibility() == View.VISIBLE) {
                mVisibleViewsList.add(view);
            }
        }
    }

    public void animateCameraSwitch() {
        mFrontBackSwitcher.animateArrows();
    }

    public void disableCameraControlsSettingsSwitch() {
        mCameraControlsBarArrow.setVisibility(INVISIBLE);
    }

    public void enableCameraControlsSettingsSwitch() {
        mCameraControlsBarArrow.setVisibility(VISIBLE);
    }

    public void hideUI() {
        enableTouch(false);
        markVisibility();
        mShowCameraControlsSettings =
                mCameraControlsBar.isCameraControlsSettingsVisible();
        collapseCameraControlsSettings(true);
        mRefocusToast.setVisibility(View.GONE);
    }

    public void hideAllUI() {
        hideUI();
        mShutter.setVisibility(INVISIBLE);
        mVideoShutter.setVisibility(INVISIBLE);
        mPreview.setVisibility(INVISIBLE);
        mFrontBackSwitcher.setVisibility(INVISIBLE);
    }

    public void showUI() {
        enableTouch(true);
        for (View view : mVisibleViewsList) {
            view.setVisibility(View.VISIBLE);
        }
        AnimationDrawable shutterAnim = (AnimationDrawable) mShutter.getBackground();
        if (shutterAnim != null) shutterAnim.stop();

        if (mShowCameraControlsSettings) {
            showCameraControlsSettings(true);
        }

        mRefocusToast.setVisibility(View.GONE);
    }

    public void updateRemainingPhotos(int remaining) {
        long remainingStorage = Storage.getAvailableSpace() - Storage.LOW_STORAGE_THRESHOLD_BYTES;
        if (remaining < 0 && remainingStorage <= 0) {
            mRemainingPhotos.setVisibility(View.INVISIBLE);
        } else {
            mRemainingPhotos.setRemaining(remaining);
        }
    }

    public void setControlHeight(int height) {
        mControlsParent.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                height - mCameraControlsBar.getDefaultHeight()));
    }

    public void setTransparency(boolean transparent) {
        mCameraControlsBar.setTransparency(transparent);
        mControlsParent.setBackgroundColor(getResources().getColor(transparent ?
                R.color.camera_control_bg_transparent :
                R.color.camera_control_bg_opaque));
    }

    public void showRefocusToast(boolean show) {
        mRefocusToast.setVisibility(show ? View.VISIBLE : View.GONE);
        if (mRemainingPhotos.getRemaining() > 0) {
            //mRemainingPhotos.setVisibility(show ? View.INVISIBLE : View.VISIBLE);
        }
    }

    private void animateCameraControlsBarArrow(int rotation) {
        ViewPropertyAnimator animator =
                mCameraControlsBarArrow.animate().rotation(rotation);
        animator.setDuration(300);
        animator.start();
    }

    public void showCameraControlsSettings(boolean animate) {
        if (mCameraControlsBar.isAnimating()
                || mCameraControlsBar.isCameraControlsSettingsVisible()) {
            return;
        }
        animateCameraControlsBarArrow(180);
        mCameraControlsBar.setCameraSettingsVisibility(true, animate);
    }

    public void collapseCameraControlsSettings(boolean animate) {
        if (mCameraControlsBar.isAnimating()
                || !mCameraControlsBar.isCameraControlsSettingsVisible()) {
            return;
        }
        animateCameraControlsBarArrow(0);
        mCameraControlsBar.setCameraSettingsVisibility(false, animate);
    }

    public boolean cameraControlsVisible() {
        return mCameraControlsBar.isCameraControlsSettingsVisible();
    }

    @Override
    public void setOrientation(int orientation, boolean animation) {
        for (View v : mViewList) {
            if (v instanceof Rotatable) {
                ((Rotatable) v).setOrientation(orientation, animation);
            }
        }
    }

    public void hideRemainingPhotoCnt() {
        mRemainingPhotos.setVisibility(View.INVISIBLE);
    }

    public void showRemainingPhotoCnt() {
        //mRemainingPhotos.setVisibility(View.VISIBLE);
    }

    private class ArrowTextView extends TextView {
        private static final int TEXT_SIZE = 14;
        private static final int PADDING_SIZE = 18;
        private static final int BACKGROUND = 0x80000000;

        private Paint mPaint;
        private Path mPath;

        public ArrowTextView(Context context) {
            super(context);

            setText(context.getString(R.string.refocus_toast));
            setBackgroundColor(BACKGROUND);
            setVisibility(View.GONE);
            setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            setTextSize(TEXT_SIZE);
            setPadding(PADDING_SIZE, PADDING_SIZE, PADDING_SIZE, PADDING_SIZE);

            mPaint = new Paint();
            mPaint.setStyle(Paint.Style.FILL);
            mPaint.setColor(BACKGROUND);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (mPath != null) {
                canvas.drawPath(mPath, mPaint);
            }
        }

        public void setArrow(float x1, float y1, float x2, float y2, float x3, float y3) {
            mPath = new Path();
            mPath.reset();
            mPath.moveTo(x1, y1);
            mPath.lineTo(x2, y2);
            mPath.lineTo(x3, y3);
            mPath.lineTo(x1, y1);
        }

    }

}
