/*
Copyright (c) 2016,2019 The Linux Foundation. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:
    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above
      copyright notice, this list of conditions and the following
      disclaimer in the documentation and/or other materials provided
      with the distribution.
    * Neither the name of The Linux Foundation nor the names of its
      contributors may be used to endorse or promote products derived
      from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.camera.ui;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Color;
import android.graphics.Paint.Style;
import android.graphics.Rect;
import android.graphics.RectF;
import android.hardware.Camera.Face;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.MotionEvent;

import android.hardware.camera2.CameraCharacteristics;

import com.android.camera.CameraActivity;
import com.android.camera.CaptureModule;
import com.android.camera.CaptureUI;
import com.android.camera.util.CameraUtil;
import com.android.camera.util.PersistUtil;
import co.aospa.camera.R;
import co.aospa.camera.wrapper.ExtendedFaceWrapper;

public class TouchTrackFocusRenderer extends View implements FocusIndicator {
    protected static final String TAG = "CAM_TouchTrackFocusRenderer";
    public static final boolean DEBUG =
            (PersistUtil.getCamera2Debug() == PersistUtil.CAMERA2_DEBUG_DUMP_LOG) ||
                    (PersistUtil.getCamera2Debug() == PersistUtil.CAMERA2_DEBUG_DUMP_ALL);
    private CameraActivity mActivity;
    private CaptureModule mModule;
    private int mInX = -1;
    private int mInY = -1;
    private final static int CIRCLE_THUMB_SIZE = 100;
    private final static int RECT_SLIDE_LENGTH = 145;
    private Rect mSurfaceDim;
    private CaptureUI mUI;

    // To trigger the object sync operation to get the latest ROI for the recognized object
    public final static int TRACKER_CMD_SYNC = 0;
    // To trigger the object registration with provided ROI
    public final static int TRACKER_CMD_REG = 1;
    // To cancel sync and unregister the tracking object
    public final static int TRACKER_CMD_CANCEL = 2;
    public final static int TRACKER_CMD_MAX = 3;


    public final static int STATUS_INIT = 0;
    public final static int STATUS_INPUT = 1;
    public final static int STATUS_TRACKING = 2;
    public final static int STATUS_TRACKED = 3;
    private int mStatus = STATUS_INIT;

    protected Paint mPaint;

    private Rect mRectActiveArray=new Rect();
    private int mTrackerScore = 0;
    protected int mUncroppedWidth;
    protected int mUncroppedHeight;
    protected int mDisplayOrientation;
    private   Rect mCameraBound = new Rect();
    protected Matrix mMatrix = new Matrix();
    protected RectF mRect = new RectF();
    protected boolean mMirror;
    protected Rect mOriginalCameraBound = new Rect();
    protected int mOrientation = 0;
    private float mZoom = 1.0f;

    private static final int SIDE_LENGTH = 125;

    public TouchTrackFocusRenderer(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void init(CameraActivity activity, CaptureModule module, CaptureUI ui) {
        mActivity = activity;
        mModule = module;
        mUI = ui;
        mPaint = new Paint();
        mPaint.setAntiAlias(true);
        mPaint.setStyle(Style.STROKE);
        mPaint.setStrokeWidth(3f);
        mPaint.setDither(true);
        mPaint.setColor(Color.WHITE);//setColor(0xFFFFFF00);
        mPaint.setStrokeCap(Paint.Cap.ROUND);
    }

    public void setVisible(boolean visible) {
        setVisibility(visible ? View.VISIBLE : View.GONE);
        if(!visible) {
            mStatus = STATUS_INIT;
            mInX = 0;
            mInY = 0;
        }
    }

    public void setSurfaceDim(int left, int top, int right, int bottom) {
        mSurfaceDim = new Rect(left, top, right, bottom);
    }

    public boolean handlesTouch() {
        return true;
    }

    public void onSingleTapUp(int x, int y) {
        mInX = x;
        mInY = y;
        mStatus = STATUS_INPUT;
        invalidate();
    }

    public void updateTrackerRect(int[] rectInts, int trackerScore) {
        if (mRectActiveArray == null) {
            mRectActiveArray = new Rect();
        }
        mStatus = STATUS_TRACKING;
        mTrackerScore = trackerScore;
        mRectActiveArray.set(rectInts[0], rectInts[1], rectInts[0] + rectInts[2],
                rectInts[1] + rectInts[3]);
        if (DEBUG) {
            Log.v(TAG, "updateTrackerRect mRectActiveArray :" + mRectActiveArray);
        }
        putRegisteredCords();
    }

    public void trackerRectSuccess(int[] rectInts) {
        if (mRectActiveArray == null) {
            mRectActiveArray = new Rect();
        }
        mStatus = STATUS_TRACKED;
        mRectActiveArray.set(rectInts[0], rectInts[1], rectInts[0] + rectInts[2], rectInts[1] + rectInts[3]);
        putRegisteredCords();
    }

    private void putRegisteredCords() {
        mActivity.runOnUiThread(new Runnable() {
            public void run() {
                invalidate();
            }
        });
    }

    public void onSurfaceTextureSizeChanged(int uncroppedWidth, int uncroppedHeight) {
        mUncroppedWidth = uncroppedWidth;
        mUncroppedHeight = uncroppedHeight;
    }

    @Override
    public void onDraw(Canvas canvas) {
        int rw, rh;
        rw = mUncroppedWidth;
        rh = mUncroppedHeight;
        mRect.set(mRectActiveArray);
        if (((rh > rw) && ((mDisplayOrientation == 0) || (mDisplayOrientation == 180)))
                || ((rw > rh) && ((mDisplayOrientation == 90) || (mDisplayOrientation == 270)))) {
            int temp = rw;
            rw = rh;
            rh = temp;
        }

        /*if (rw * mCameraBound.width() != mCameraBound.height() * rh) {
            if (rw == rh || (rh * 288 == rw * 352) || (rh * 480 == rw * 800)) {
                rh = rw * mCameraBound.width() / mCameraBound.height();
            } else {
                rw = rh * mCameraBound.height() / mCameraBound.width();
            }
        }*/
        CameraUtil.prepareMatrix(mMatrix, mMirror, mDisplayOrientation, rw, rh);

        // mMatrix assumes that the face coordinates are from -1000 to 1000.
        // so translate the face coordination to match the assumption.
        Matrix translateMatrix = new Matrix();
        translateMatrix.preTranslate(-mCameraBound.width() / 2f, -mCameraBound.height() / 2f);
        translateMatrix.postScale(2000f / mCameraBound.width(), 2000f / mCameraBound.height());

        int dx = (getWidth() - rw) / 2;
        int dy = (getHeight() - rh) / 2;
        dx -= (rw - mUncroppedWidth) / 2;
        dy -= (rh - mUncroppedHeight) / 2;

        // Focus indicator is directional. Rotate the matrix and the canvas
        // so it looks correctly in all orientations.
        canvas.save();
        mMatrix.postRotate(mOrientation);
        canvas.rotate(-mOrientation);

        mRect.offset(-mOriginalCameraBound.left, -mOriginalCameraBound.top);
        if (mZoom != 1.0f) {
            mRect.left = mRect.left - mCameraBound.left;
            mRect.right = mRect.right - mCameraBound.left;
            mRect.top = mRect.top - mCameraBound.top;
            mRect.bottom = mRect.bottom - mCameraBound.top;
        }
        translateMatrix.mapRect(mRect);
        mMatrix.mapRect(mRect);
        mRect.offset(dx, dy);

        if (mStatus == STATUS_TRACKED) {
            if (mRect != null) {
                mPaint.setColor(Color.GREEN);
                canvas.drawRect(mRect, mPaint);
            }
        } else if (mStatus == STATUS_TRACKING) {
            if (mRect != null) {
                if (mTrackerScore > 80) {
                    mPaint.setColor(Color.GREEN);
                } else{
                    mPaint.setColor(Color.YELLOW);
                }
                canvas.drawRect(mRect, mPaint);
            }
        } else if (mStatus == STATUS_INPUT) {
            mPaint.setColor(Color.WHITE);
            canvas.drawRect(mInX - RECT_SLIDE_LENGTH, mInY - RECT_SLIDE_LENGTH,
                    mInX + RECT_SLIDE_LENGTH, mInY + RECT_SLIDE_LENGTH, mPaint);
        }
    }

    @Override
    public void showStart() {
    }

    @Override
    public void showSuccess(boolean timeout) {
    }

    @Override
    public void showFail(boolean timeout) {

    }

    @Override
    public void clear() {

    }

    public void setOrientation(int orientation) {
        mOrientation = orientation;
    }

    public void setZoom(float zoom) {
        mZoom = zoom;
    }

    public void setOriginalCameraBound(Rect originalCameraBound) {
        mOriginalCameraBound = originalCameraBound;
    }

    public void setMirror(boolean mirror) {
        mMirror = mirror;
    }

    public void setDisplayOrientation(int orientation) {
        mDisplayOrientation = orientation;
    }

    public void setCameraBound(Rect cameraBound) {
        mCameraBound = cameraBound;
    }

}
