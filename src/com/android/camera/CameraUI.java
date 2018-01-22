/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.camera;

import android.graphics.Point;
import android.graphics.RectF;
import android.support.annotation.LayoutRes;
import android.util.Log;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.android.camera.ui.*;
import com.android.camera.util.CameraUtil;

import co.paranoidandroid.camera.R;

public abstract class CameraUI implements
        PreviewGestures.SwipeListener,
        SurfaceHolder.Callback,
        CameraRootView.MyDisplayListener {

    private static final String TAG = "CAM_CameraUI";

    private CameraActivity mActivity;

    private View mRootView;
    private CameraControls mCameraControls;
    private GridView mGridView;
    private ModuleSwitcher mModuleSwitcher;

    private SurfaceView mSurfaceView;
    private SurfaceHolder mSurfaceHolder;
    private PieRenderer mPieRenderer;

    private Point mScreenSize = new Point();
    private int mControlHeight;

    public enum SURFACE_STATUS {
        HIDE,
        SURFACE_VIEW
    }

    private boolean mUIhidden;

    protected abstract @LayoutRes
    int getUILayout();

    public CameraUI(CameraActivity activity, View parent) {
        mActivity = activity;
        mRootView = parent;
        activity.getLayoutInflater().inflate(getUILayout(), (ViewGroup) parent, true);

        // display the view
        mSurfaceView = (SurfaceView) parent.findViewById(R.id.mdp_preview_content);
        mSurfaceView.setVisibility(View.VISIBLE);
        mSurfaceHolder = mSurfaceView.getHolder();
        mSurfaceHolder.addCallback(this);
        mSurfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        mCameraControls = (CameraControls) parent.findViewById(R.id.camera_controls);

        mActivity.getWindowManager().getDefaultDisplay().getRealSize(mScreenSize);
        mControlHeight = calculateBottomMargin(
                (float) mScreenSize.y / (float) mScreenSize.x);
        mCameraControls.setControlHeight(mControlHeight);

        mGridView = activity.getGridView();

        mModuleSwitcher = (ModuleSwitcher) parent.findViewById(R.id.camera_switcher);
        mModuleSwitcher.setSwitchListener(activity);
    }

    private int calculateBottomMargin(float screenRatio) {
        if (CameraUtil.determineRatio(screenRatio) == CameraUtil.RATIO_4_3) {
            return mScreenSize.y / 5;
        }
        return (int) (mScreenSize.y - (float) mScreenSize.x * 4f / 3f);
    }

    FrameLayout.LayoutParams getSurfaceSizeParams(float camAspectRatio, int width, int height) {
        float screenX = getScreenX(), screenY = getScreenY();
        float surfaceWidth, surfaceHeight;
        int gravity = 0, bottomMargin = 0;

        float screenRatio = screenY / screenX;
        if (screenRatio > camAspectRatio) {
            surfaceWidth = screenX;
            surfaceHeight = screenX * camAspectRatio;
            bottomMargin = mControlHeight;
            if (camAspectRatio == 1) {
                gravity = Gravity.BOTTOM;
            }
        } else if (screenRatio < camAspectRatio) {
            surfaceWidth = screenY / camAspectRatio;
            surfaceHeight = screenY;
            gravity = Gravity.CENTER_HORIZONTAL;
        } else {
            surfaceWidth = screenX;
            surfaceHeight = screenY;
        }

        if (height > screenY - calculateBottomMargin(screenRatio)) {
            mCameraControls.setTransparency(true);
        }

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                (int) surfaceWidth, (int) surfaceHeight, gravity);
        lp.bottomMargin = bottomMargin;
        return lp;
    }

    public CameraActivity getActivity() {
        return mActivity;
    }

    protected int getControlHeight() {
        return mControlHeight;
    }

    public int getScreenX() {
        return mScreenSize.x;
    }

    public int getScreenY() {
        return mScreenSize.y;
    }

    public CameraControls getCameraControls() {
        return mCameraControls;
    }

    public View getRootView() {
        return mRootView;
    }

    public synchronized void applySurfaceChange(SURFACE_STATUS status) {
        if (status == SURFACE_STATUS.HIDE) {
            mSurfaceView.setVisibility(View.GONE);
            return;
        }
        mSurfaceView.setVisibility(View.VISIBLE);
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        Log.v(TAG, "surfaceChanged: width =" + width + ", height = " + height);
    }

    protected void setSurfaceArea() {
        mSurfaceView.post(new Runnable() {
            @Override
            public void run() {
                RectF r = new RectF(mSurfaceView.getLeft(), mSurfaceView.getTop(),
                        mSurfaceView.getRight(), mSurfaceView.getBottom());
                mGridView.setBounds(r);
                if (mPieRenderer != null) {
                    mPieRenderer.setSurfaceArea(r);
                }
            }
        });
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        Log.v(TAG, "surfaceCreated");
        mSurfaceHolder = holder;
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.v(TAG, "surfaceDestroyed");
        mSurfaceHolder = null;
    }

    protected SurfaceView getSurfaceView() {
        return mSurfaceView;
    }

    public SurfaceHolder getSurfaceHolder() {
        return mSurfaceHolder;
    }

    public Point getSurfaceViewSize() {
        Point point = new Point();
        if (mSurfaceView != null) {
            point.set(mSurfaceView.getWidth(), mSurfaceView.getHeight());
        }
        return point;
    }

    public void hideSurfaceView() {
        mSurfaceView.setVisibility(View.INVISIBLE);
    }

    public void showSurfaceView() {
        mSurfaceView.setVisibility(View.VISIBLE);
    }

    protected void setPieRenderer(PieRenderer pieRenderer) {
        mPieRenderer = pieRenderer;
        setSurfaceArea();
    }

    public void animateCameraSwitch() {
        mCameraControls.animateCameraSwitch();
    }

    public void initDisplayChangeListener() {
        ((CameraRootView) getRootView()).setDisplayChangeListener(this);
    }

    public void removeDisplayChangeListener() {
        ((CameraRootView) getRootView()).removeDisplayChangeListener();
    }

    @Override
    public void onDisplayChanged() {
        Log.d(TAG, "Device flip detected.");
        mCameraControls.checkLayoutFlip();
    }

    @Override
    public void onSwipeLeft(View view) {
        if (mCameraControls.cameraControlsVisible()) {
            mCameraControls.collapseCameraControlsSettings(true);
            mModuleSwitcher.left(300);
        } else {
            mModuleSwitcher.left();
        }
    }

    @Override
    public void onSwipeRight(View view) {
        if (mCameraControls.cameraControlsVisible()) {
            mCameraControls.collapseCameraControlsSettings(true);
            mModuleSwitcher.right(300);
        } else {
            mModuleSwitcher.right();
        }
    }

    @Override
    public void onSwipeUp(View v) {
        mCameraControls.showCameraControlsSettings(true);
    }

    @Override
    public void onSwipeDown(View v) {
        mCameraControls.collapseCameraControlsSettings(true);
    }

    public void hideUI() {
        if (mUIhidden) return;
        mUIhidden = true;
        mGridView.setVisibility(View.GONE);
        mCameraControls.hideUI();
    }

    public void hideAllUI() {
        if (mUIhidden) return;
        mUIhidden = true;
        mGridView.setVisibility(View.GONE);
        mCameraControls.hideAllUI();
    }

    public void showUI() {
        if (!mUIhidden) return;
        mUIhidden = false;
        if (mActivity.isGridEnabled()) {
            mGridView.setVisibility(View.VISIBLE);
        }
        mCameraControls.showUI();
    }

    public boolean arePreviewControlsVisible() {
        return !mUIhidden;
    }
}
