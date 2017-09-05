/*
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

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import co.paranoidandroid.camera.R;

public class CameraControlsBar extends FrameLayout implements Rotatable {

    private final int mDefaultHeight;

    private ModuleSwitcher mModuleSwitcher;
    private ViewGroup mCameraControlsSettingsParent;
    private CameraControlsSettings mCameraControlsSettings;

    private boolean mAnimating;

    public CameraControlsBar(Context context) {
        this(context, null, 0);
    }

    public CameraControlsBar(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CameraControlsBar(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        mDefaultHeight = getResources().getDimensionPixelSize(
                R.dimen.camera_controls_bar_height);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mModuleSwitcher = (ModuleSwitcher) findViewById(R.id.camera_switcher);
        mCameraControlsSettings = (CameraControlsSettings)
                findViewById(R.id.camera_settings);
        mCameraControlsSettingsParent = (ViewGroup) mCameraControlsSettings.getParent();

        mCameraControlsSettingsParent.setLayoutParams(
                new LayoutParams(LayoutParams.MATCH_PARENT, 0, Gravity.BOTTOM));
    }

    @Override
    public void setOrientation(int orientation, boolean animation) {
        mCameraControlsSettings.setOrientation(orientation, animation);
    }

    public void setTransparency(boolean transparent) {
        int color = getResources().getColor(transparent ?
                R.color.camera_control_bg_transparent :
                R.color.camera_controls_bar_bg_opaque);
        mModuleSwitcher.setBackgroundColor(color);
        mCameraControlsSettingsParent.setBackgroundColor(color);
    }

    protected void setCameraSettingsVisibility(final boolean visible, final boolean animate) {
        if (mAnimating) return;
        if (!animate) {
            mModuleSwitcher.getLayoutParams().height = visible ? 0 : mDefaultHeight;
            mCameraControlsSettingsParent.getLayoutParams().height = visible ? mDefaultHeight : 0;
            mModuleSwitcher.requestLayout();
            mCameraControlsSettingsParent.requestLayout();
            return;
        }

        ValueAnimator animator = ValueAnimator.ofInt(
                visible ? mDefaultHeight : 0,
                visible ? 0 : mDefaultHeight);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                mModuleSwitcher.getLayoutParams().height =
                        (int) valueAnimator.getAnimatedValue();
                mModuleSwitcher.requestLayout();
            }
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                super.onAnimationStart(animation);
                mAnimating = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                mAnimating = false;
            }
        });
        animator.setDuration(300);


        ValueAnimator animator2 = ValueAnimator.ofInt(
                visible ? 0 : mDefaultHeight,
                visible ? mDefaultHeight : 0);
        animator2.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                mCameraControlsSettingsParent.getLayoutParams().height =
                        (int) valueAnimator.getAnimatedValue();
                mCameraControlsSettingsParent.requestLayout();
            }
        });
        animator2.setDuration(300);

        animator.start();
        animator2.start();
    }

    protected boolean isCameraControlsSettingsVisible() {
        return mCameraControlsSettingsParent.getLayoutParams().height > 0;
    }

    public int getDefaultHeight() {
        return mDefaultHeight;
    }

    public boolean isAnimating() {
        return mAnimating;
    }

    @Override
    public void setClickable(boolean clickable) {
        super.setClickable(clickable);
        mModuleSwitcher.setClickable(clickable);
        mCameraControlsSettingsParent.setClickable(clickable);
    }

}
