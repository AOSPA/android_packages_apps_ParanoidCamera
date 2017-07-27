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

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import com.android.camera.TsMakeupManager;

public class CameraControlsSettings extends ViewGroup implements Rotatable {

    private static final int WIDTH_DIVIDER = TsMakeupManager.HAS_TS_MAKEUP ? 6 : 5;

    public CameraControlsSettings(Context context) {
        this(context, null, 0);
    }

    public CameraControlsSettings(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CameraControlsSettings(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    private void setOrientation(View child, int orientation, boolean animation) {
        if (child instanceof Rotatable) {
            ((Rotatable) child).setOrientation(orientation, animation);
        } else if (child instanceof ViewGroup) {
            ViewGroup childGroup = (ViewGroup) child;
            for (int x = 0; x < childGroup.getChildCount(); x++) {
                setOrientation(childGroup.getChildAt(x), orientation, animation);
            }
        }
    }

    @Override
    public void setOrientation(int orientation, boolean animation) {
        for (int i = 0; i < getChildCount(); i++) {
            setOrientation(getChildAt(i), orientation, animation);
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int parentWidth = ((View) getParent()).getMeasuredWidth();
        int height = getMeasuredHeight();
        int widthAvailable = parentWidth / WIDTH_DIVIDER;

        int offset = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) {
                continue;
            }

            int childWidth = child.getMeasuredWidth();
            int childHeight = child.getMeasuredHeight();

            int left = offset * widthAvailable + widthAvailable / 2 - childWidth / 2;
            int top = height / 2 - childHeight / 2;
            int right = left + (childWidth > widthAvailable ? widthAvailable : childWidth);
            int bottom = top + childHeight;
            child.layout(left, top, right, bottom);
            offset++;
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        int childrenCount = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) {
                continue;
            }
            measureChild(child, widthMeasureSpec, heightMeasureSpec);
            childrenCount++;
        }

        int parentWidth = ((View) getParent()).getMeasuredWidth();
        setMeasuredDimension(parentWidth / WIDTH_DIVIDER * childrenCount,
                MeasureSpec.getSize(heightMeasureSpec));
    }
}
