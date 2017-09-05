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
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

import com.android.camera.util.PhotoSphereHelper;

import co.paranoidandroid.camera.R;

public class ModuleSwitcher extends View {

    public static final int VIDEO_MODULE_INDEX = 0;
    public static final int PHOTO_MODULE_INDEX = 1;
    public static final int WIDE_ANGLE_PANO_MODULE_INDEX = 2;
    public static final int LIGHTCYCLE_MODULE_INDEX = 3;
    public static final int GCAM_MODULE_INDEX = 4;
    public static final int CAPTURE_MODULE_INDEX = 5;
    public static final int PANOCAPTURE_MODULE_INDEX = 6;

    private static final int[] DRAW_IDS = {
            R.string.video,
            R.string.photo,
            R.string.panorama,
            R.string.photosphere,
            R.string.gcam,
    };

    public interface ModuleSwitchListener {
        public void onModuleSelected(int i);
    }

    private SparseArray<String> mItems = new SparseArray<>();
    private int mCurrentIndex;
    private ModuleSwitchListener mListener;

    private final Paint mPaint;
    private final int mDistance;
    private final int mIndexTextColor;
    private final int mTextColor;
    private final GestureDetector mGestureDetector;

    private int mOffsetX;
    private boolean mAnimating;
    private int mDownTouchX;

    public ModuleSwitcher(Context context) {
        this(context, null);
    }

    public ModuleSwitcher(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ModuleSwitcher(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPaint.setTextSize(getResources().getDimensionPixelSize(R.dimen.module_switcher_text_size));

        mDistance = getResources().getDimensionPixelSize(R.dimen.module_switcher_item_distance);
        mIndexTextColor = getResources().getColor(R.color.module_switcher_index);
        mTextColor = getResources().getColor(R.color.module_switcher_item);

        for (int i = 0; i < DRAW_IDS.length; i++) {
            if (i == LIGHTCYCLE_MODULE_INDEX && !PhotoSphereHelper.hasLightCycleCapture(context)) {
                continue; // not enabled, so don't add to UI
            }
            if (i == GCAM_MODULE_INDEX) {
                continue; // don't add to UI
            }
            mItems.put(i, context.getString(DRAW_IDS[i]).toUpperCase());
        }

        setClickable(true);

        mGestureDetector = new GestureDetector(context,
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onSingleTapConfirmed(MotionEvent e) {
                        float x = e.getX();
                        int centerX = getMeasuredWidth() / 2;
                        String currentIndex = mItems.get(mCurrentIndex);
                        Rect textBounds = new Rect();
                        mPaint.getTextBounds(currentIndex, 0, currentIndex.length(), textBounds);
                        int textCenterWidth = textBounds.width() / 2;
                        if (x < centerX - textCenterWidth) {
                            left();
                        } else if (x > centerX + textCenterWidth) {
                            right();
                        }
                        return true;
                    }
                });
    }

    public void setCurrentIndex(int i) {
        if (i == PANOCAPTURE_MODULE_INDEX) return;
        if (i == GCAM_MODULE_INDEX) {
            i = PHOTO_MODULE_INDEX;
        }
        mOffsetX = 0;
        mCurrentIndex = i;
        invalidate();
    }

    public void setSwitchListener(ModuleSwitchListener listener) {
        mListener = listener;
    }

    private float getScaleX(char c, int x, int width) {
        return getScaleX(String.valueOf(c), x, width);
    }

    private float getScaleX(String text, int x, int width) {
        mPaint.setTextScaleX(1);
        float distance = mDistance * 1.5f;
        float textWidth = mPaint.measureText(text);
        float blockWidth = distance / 6f;
        if (x < distance) {
            return (3 + x / blockWidth) / 10f;
        } else if (x + textWidth > width - distance) {
            return (7 - (x - width + distance) / blockWidth) / 10f;
        }
        return 1;
    }

    private void drawText(Canvas canvas, String text, int x, int y, int width) {
        char[] chars = text.toCharArray();
        int previousX = x;

        Rect textBounds = new Rect();
        mPaint.getTextBounds(text, 0, text.length(), textBounds);
        if (x < width / 2f) {
            previousX += textBounds.width();
            for (int i = chars.length - 1; i >= 0; i--) {
                String c = String.valueOf(chars[i]);
                float scaleX = getScaleX(c,
                        (int) Math.floor(previousX - mPaint.measureText(c)), width);
                mPaint.setTextScaleX(scaleX);
                mPaint.setAlpha(Math.round(255 * scaleX));

                int cX = (int) Math.floor(previousX - mPaint.measureText(c));
                canvas.drawText(c, cX, y, mPaint);
                previousX = cX;
            }
        } else {
            for (char c : chars) {
                float scaleX = getScaleX(c, previousX, width);
                mPaint.setTextScaleX(scaleX);
                mPaint.setAlpha(Math.round(255 * scaleX));
                canvas.drawText(String.valueOf(c), previousX, y, mPaint);
                previousX += mPaint.measureText(String.valueOf(c));
            }
        }
        mPaint.setTextScaleX(1);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getMeasuredWidth();
        int height = getMeasuredHeight();

        String currentIndex = mItems.get(mCurrentIndex);

        Rect textBounds = new Rect();
        mPaint.getTextBounds(currentIndex, 0, currentIndex.length(), textBounds);

        int currentIndexX = Math.round(width / 2f - textBounds.width() / 2f + textBounds.left) + mOffsetX;
        int currentIndexY = Math.round(height / 2f + textBounds.height() / 2f - textBounds.bottom);

        mPaint.setColor(mIndexTextColor);
        drawText(canvas, currentIndex, currentIndexX, currentIndexY, width);

        mPaint.setColor(mTextColor);

        int leftPreviousX = currentIndexX;
        int rightPreviousX = currentIndexX + textBounds.width();

        for (int i = mCurrentIndex - 1; i >= 0; i--) {
            String item = mItems.get(i);
            if (item == null) continue;
            mPaint.getTextBounds(item, 0, item.length(), textBounds);

            int x = leftPreviousX - mDistance - textBounds.width();
            leftPreviousX = x;
            drawText(canvas, item, x, currentIndexY, width);
        }

        for (int i = mCurrentIndex + 1; i < DRAW_IDS.length; i++) {
            String item = mItems.get(i);
            if (item == null) continue;
            mPaint.getTextBounds(item, 0, item.length(), textBounds);

            int x = rightPreviousX + mDistance;
            rightPreviousX = x + textBounds.width();
            drawText(canvas, item, x, currentIndexY, width);
        }
    }

    private float getDiff(String item) {
        String currentIndex = mItems.get(mCurrentIndex);

        Rect textBounds = new Rect();
        mPaint.setTextScaleX(1);
        mPaint.getTextBounds(item, 0, item.length(), textBounds);
        float newIndexWidth = textBounds.width();
        mPaint.getTextBounds(currentIndex, 0, currentIndex.length(), textBounds);
        float indexWidth = textBounds.width();

        return newIndexWidth / 2f + mDistance + indexWidth / 2f;
    }

    public void left() {
        left(0);
    }

    public void left(int delay) {
        if (mAnimating) return;
        String item = null;
        int index = 0;
        for (int i = mCurrentIndex - 1; i >= 0; i--) {
            String value = mItems.get(i);
            if (value != null) {
                item = value;
                index = i;
                break;
            }
        }
        if (item == null) return;
        move(getDiff(item), index, delay);
    }

    public void right() {
        right(0);
    }

    public void right(int delay) {
        if (mAnimating) return;
        String item = null;
        int index = 0;
        for (int i = mCurrentIndex + 1; i < DRAW_IDS.length; i++) {
            String value = mItems.get(i);
            if (value != null) {
                item = value;
                index = i;
                break;
            }
        }
        if (item == null) return;
        move(-getDiff(item), index, delay);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isClickable()) return super.onTouchEvent(event);
        mGestureDetector.onTouchEvent(event);
        return true;
    }

    private void move(float diff, final int index, int delay) {
        ValueAnimator valueAnimator = ValueAnimator.ofInt(0, (int) Math.ceil(diff));
        valueAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                mOffsetX = (int) valueAnimator.getAnimatedValue();
                invalidate();
            }
        });
        valueAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                super.onAnimationStart(animation);
                mAnimating = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                setCurrentIndex(index);
                mAnimating = false;
                if (mListener != null) {
                    mListener.onModuleSelected(index);
                }
            }
        });

        valueAnimator.setStartDelay(delay);
        valueAnimator.start();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        int itemsWidth = 0;
        int itemsMaxHeight = 0;

        Rect textBounds = new Rect();
        for (int i = 0; i < mItems.size(); i++) {
            String item = mItems.valueAt(i);
            mPaint.getTextBounds(item, 0, item.length(), textBounds);
            itemsWidth += textBounds.width();
            if (textBounds.height() > itemsMaxHeight) {
                itemsMaxHeight = textBounds.height();
            }
        }

        itemsWidth += (mItems.size() - 1) * mDistance;
        int itemsHeight = Math.round(itemsMaxHeight * 2.5f);

        if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.EXACTLY) {
            itemsWidth = MeasureSpec.getSize(widthMeasureSpec);
        }
        if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY) {
            itemsHeight = MeasureSpec.getSize(heightMeasureSpec);
        }

        setMeasuredDimension(itemsWidth, itemsHeight);
    }

}
