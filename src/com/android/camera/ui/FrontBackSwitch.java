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
import android.content.Context;
import android.util.AttributeSet;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import android.widget.ImageView;

import co.paranoidandroid.camera.R;

public class FrontBackSwitch extends FrameLayout implements Rotatable {

    private final RotateImageView mCamera;
    private final RotateImageView mArrows;

    public FrontBackSwitch(Context context) {
        this(context, null);
    }

    public FrontBackSwitch(Context context, AttributeSet attrs) {
        super(context, attrs);

        mCamera = new RotateImageView(context);
        mArrows = new RotateImageView(context);

        mCamera.setImageResource(R.drawable.ic_camera);
        mArrows.setImageResource(R.drawable.ic_circular_arrows);

        mCamera.setScaleType(ImageView.ScaleType.FIT_CENTER);
        mArrows.setScaleType(ImageView.ScaleType.FIT_CENTER);

        addView(mCamera, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        addView(mArrows, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
    }

    @Override
    public void setOrientation(int orientation, boolean animation) {
        mCamera.setOrientation(orientation, animation);
        mArrows.setOrientation(orientation, animation);
    }

    public void animateArrows() {
        mArrows.animate()
                .rotationBy(-360).setDuration(300)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationStart(Animator animation) {
                        super.onAnimationStart(animation);
                        setClickable(false);
                    }

                    @Override
                    public void onAnimationEnd(Animator animation) {
                        super.onAnimationEnd(animation);
                        setClickable(true);
                    }
                }).start();
    }

}
