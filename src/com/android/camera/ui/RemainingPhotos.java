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
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

import co.paranoidandroid.camera.R;

public class RemainingPhotos extends View implements Rotatable {

    private static final int LOW_REMAINING_PHOTOS = 20;
    private static final int HIGH_REMAINING_PHOTOS = 1000000;

    private final Paint mTextPaint;

    private int mRemaining = -1;
    private int mOrientation;

    public RemainingPhotos(Context context) {
        this(context, null);
    }

    public RemainingPhotos(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public RemainingPhotos(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        mTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mTextPaint.setTextSize(getResources().getDimensionPixelSize(R.dimen.remaining_photos_text_size));
        mTextPaint.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
        mTextPaint.setColor(Color.WHITE);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        String text;
        if (mRemaining < LOW_REMAINING_PHOTOS) {
            text = "<" + LOW_REMAINING_PHOTOS;
        } else if (mRemaining >= HIGH_REMAINING_PHOTOS) {
            text = ">" + HIGH_REMAINING_PHOTOS;
        } else {
            text = String.valueOf(mRemaining);
        }
        text += " ";

        int width = getMeasuredWidth();
        int height = getMeasuredHeight();

        Rect textBounds = new Rect();
        mTextPaint.getTextBounds(text, 0, text.length(), textBounds);

        int textWidth = textBounds.width();
        int textHeight = textBounds.height();

        int textX = Math.round(width / 2f - textWidth / 2f - textBounds.left);
        int textY = textHeight - textBounds.bottom;

        int offsetX = 0;
        int offsetY = 0;
        switch (mOrientation) {
            case 90:
                offsetX = -Math.round(height / 2f + textWidth / 2f + textBounds.left);
                offsetY = -Math.round(width / 2f - textWidth / 2f - textBounds.left);
                break;
            case 180:
                offsetX = -(textWidth + textBounds.left);
                offsetY = -(height - textBounds.bottom);
                break;
            case 270:
                offsetX = Math.round(height / 2f - textWidth / 2f - textBounds.left);
                offsetY = -Math.round(width / 2f + textWidth / 2f + textBounds.left);
                break;
        }

        canvas.rotate(-mOrientation, textX, 0);
        canvas.drawText(text, textX + offsetX, textY + offsetY, mTextPaint);
    }

    public void setRemaining(int remaining) {
        mRemaining = remaining;
        invalidate();
    }

    public int getRemaining() {
        return mRemaining;
    }

    @Override
    public void setOrientation(int orientation, boolean animation) {
        mOrientation = orientation;
        invalidate();
    }

}
