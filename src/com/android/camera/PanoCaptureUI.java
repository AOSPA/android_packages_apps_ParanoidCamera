/*
 * Copyright (c) 2016, The Linux Foundation. All rights reserved.
 * Not a Contribution.
 *
 * Copyright (C) 2012 The Android Open Source Project
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

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.hardware.Camera.Face;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.support.annotation.LayoutRes;
import android.util.Size;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLayoutChangeListener;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.android.camera.ui.*;
import com.android.camera.util.CameraUtil;

import co.paranoidandroid.camera.R;

public class PanoCaptureUI extends CameraUI implements
        LocationManager.Listener,
        CameraManager.CameraFaceDetectionCallback {

    private static final String TAG = "SnapCam_PanoCaptureUI";
    private PanoCaptureModule mController;

    private ShutterButton mShutterButton;
    private ModuleSwitcher mSwitcher;
    private RotateLayout mSceneModeLabelRect;
    private LinearLayout mSceneModeLabelView;
    private TextView mSceneModeName;
    private ImageView mSceneModeLabelCloseIcon;
    private AlertDialog mSceneModeInstructionalDialog = null;

    // Small indicators which show the camera settings in the viewfinder.
    private OnScreenIndicators mOnScreenIndicators;

    private Matrix mMatrix = null;
    private boolean mUIhidden = false;

    private int mSurfaceMode = 0; //0: INIT 1: TextureView 2: SurfaceView
    private PanoCaptureProcessView mPreviewProcessView;
    private ImageView mThumbnail;

    private int mOrientation;
    private boolean mIsSceneModeLabelClose = false;

    public boolean isPanoCompleting() {
        return mPreviewProcessView.isPanoCompleting();
    }

    public boolean isFrameProcessing() {
        return mPreviewProcessView.isFrameProcessing();
    }

    public void onFrameAvailable(Bitmap bitmap, boolean isCancelling) {
        mPreviewProcessView.onFrameAvailable(bitmap, isCancelling);
    }

    public void onPanoStatusChange(final boolean isStarting) {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if(isStarting) {
                    if (mThumbnail != null) {
                        mThumbnail.setVisibility(View.GONE);
                    }
                    if (mShutterButton != null) {
                        mShutterButton.setImageResource(R.drawable.shutter_button_video_stop);
                    }
                } else {
                    if (mThumbnail != null) {
                        mThumbnail.setVisibility(View.VISIBLE);
                    }
                    if (mShutterButton != null) {
                        mShutterButton.setImageResource(R.drawable.btn_new_shutter_panorama);
                    }
                }
            }
        });
    }

    private OnLayoutChangeListener mLayoutListener = new OnLayoutChangeListener() {
        @Override
        public void onLayoutChange(View v, int left, int top, int right,
                                   int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
            int width = right - left;
            int height = bottom - top;
            Size size = mController.getPictureOutputSize();
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(width, height, Gravity.CENTER);
            mPreviewProcessView.setLayoutParams(lp);
            mPreviewProcessView.setPanoPreviewSize(lp.width,
                    lp.height,
                    size.getWidth(),
                    size.getHeight());
        }
    };


    public void setLayout(Size size) {
        ((AutoFitSurfaceView) getSurfaceView()).setAspectRatio(size.getHeight(), size.getWidth());
    }

    @Override
    public @LayoutRes int getUILayout() {
        return R.layout.pano_capture_module;
    }

    public PanoCaptureUI(final CameraActivity activity, PanoCaptureModule controller, View parent) {
        super(activity, parent);
        mController = controller;

        mPreviewProcessView = (PanoCaptureProcessView) parent.findViewById(R.id.preview_process_view);
        mPreviewProcessView.setContext(activity, mController);
        parent.findViewById(R.id.mute_button).setVisibility(View.GONE);
        parent.findViewById(R.id.settings).setVisibility(View.GONE);
        applySurfaceChange(CameraUI.SURFACE_STATUS.SURFACE_VIEW);

        mShutterButton = (ShutterButton) parent.findViewById(R.id.shutter_button);
        mShutterButton.setLongClickable(false);
        mSwitcher = (ModuleSwitcher) parent.findViewById(R.id.camera_switcher);
        mSwitcher.setVisibility(View.GONE);

        mThumbnail = (ImageView) parent.findViewById(R.id.preview_thumb);
        mThumbnail.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                activity.gotoGallery();
            }
        });

        mSceneModeLabelRect = (RotateLayout) parent.findViewById(R.id.scene_mode_label_rect);
        mSceneModeName = (TextView) parent.findViewById(R.id.scene_mode_label);
        mSceneModeName.setText(R.string.pref_camera_scenemode_entry_panorama);
        mSceneModeLabelCloseIcon = (ImageView) parent.findViewById(R.id.scene_mode_label_close);
        mSceneModeLabelCloseIcon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mIsSceneModeLabelClose = true;
                mSceneModeLabelRect.setVisibility(View.GONE);
            }
        });
        initIndicators();

        if (needShowInstructional() ) {
            showSceneInstructionalDialog(mOrientation);
        }
    }

    private void setTransformMatrix(int width, int height) {
        mMatrix = getSurfaceView().getMatrix();

        // Calculate the new preview rectangle.
        RectF previewRect = new RectF(0, 0, width, height);
        mMatrix.mapRect(previewRect);
        mController.onPreviewRectChanged(CameraUtil.rectFToRect(previewRect));
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        super.surfaceCreated(holder);
        mController.onPreviewUIReady();
        getActivity().updateThumbnail(mThumbnail);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        super.surfaceDestroyed(holder);
        mController.onPreviewUIDestroyed();
    }

    private void initIndicators() {
        mOnScreenIndicators = new OnScreenIndicators(getActivity(),
                getRootView().findViewById(R.id.on_screen_indicators));
    }

    public void onCameraOpened() {
    }

    public void initializeShutterButton() {
        // Initialize shutter button.
        mShutterButton.setImageResource(R.drawable.btn_new_shutter_panorama);
        mShutterButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                //TODO: Any animation is needed?
            }
        });
        mShutterButton.setOnShutterButtonListener(mController);
        mShutterButton.setVisibility(View.VISIBLE);
    }

    /**
     * Enables or disables the shutter button.
     */
    public void enableShutter(boolean enabled) {
        if (mShutterButton != null) {
            mShutterButton.setEnabled(enabled);
        }
    }

    public void overrideSettings(final String... keyvalues) {
    }

    public boolean onBackPressed() {
        // In image capture mode, back button should:
        // 1) if there is any popup, dismiss them, 2) otherwise, get out of
        // image capture
        if (mController.isImageCaptureIntent()) {
            mController.onCaptureCancelled();
            return true;
        } else if (!mController.isCameraIdle()) {
            // ignore backs while we're taking a picture
            return true;
        }
        if (mSwitcher != null) {
            return true;
        } else {
            return false;
        }
    }

    public void onPreviewFocusChanged(boolean previewFocused) {
        if (previewFocused) {
            showUI();
        } else {
            hideUI();
        }
        setShowMenu(previewFocused);
    }

    private void setShowMenu(boolean show) {
        if (mOnScreenIndicators != null) {
            mOnScreenIndicators.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    public boolean collapseCameraControls() {
        return true;
    }

    public void onResume() {
        mPreviewProcessView.onResume();
        onPanoStatusChange(false);
        getCameraControls().getPanoramaExitButton().setVisibility(View.VISIBLE);
        getCameraControls().getPanoramaExitButton().setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    SettingsManager.getInstance().setValueIndex(SettingsManager.KEY_SCENE_MODE,
                            SettingsManager.SCENE_MODE_AUTO_INT);
                } catch(NullPointerException e) {}
                getActivity().onModuleSelected(ModuleSwitcher.CAPTURE_MODULE_INDEX);
            }
        });
    }

    public void onPause() {
        collapseCameraControls();
        mPreviewProcessView.onPause();
        getCameraControls().getPanoramaExitButton().setVisibility(View.GONE);
        getCameraControls().getPanoramaExitButton().setOnClickListener(null);
    }

    // focus UI implementation
    private FocusIndicator getFocusIndicator() {
        return null;
    }

    @Override
    public void onFaceDetection(Face[] faces, CameraManager.CameraProxy camera) {
    }

    @Override
    public void onDisplayChanged() {
        super.onDisplayChanged();
        mController.updateCameraOrientation();
    }

    public void setOrientation(int orientation, boolean animation) {
        mOrientation = orientation;
        getCameraControls().setOrientation(orientation, animation);
        mPreviewProcessView.setOrientation(orientation);

        if ( mSceneModeLabelRect != null ) {
            if (orientation == 180) {
                mSceneModeName.setRotation(180);
                mSceneModeLabelCloseIcon.setRotation(180);
                mSceneModeLabelRect.setOrientation(0, false);
            } else {
                mSceneModeName.setRotation(0);
                mSceneModeLabelCloseIcon.setRotation(0);
                mSceneModeLabelRect.setOrientation(orientation, false);
            }
        }

        if ( mSceneModeInstructionalDialog != null && mSceneModeInstructionalDialog.isShowing()) {
            mSceneModeInstructionalDialog.dismiss();
            mSceneModeInstructionalDialog = null;
            showSceneInstructionalDialog(orientation);
        }
    }

    public int getOrientation() {
        return mOrientation;
    }

    @Override
    public void onErrorListener(int error) {

    }

    private boolean needShowInstructional() {
        final SharedPreferences pref = getActivity().getSharedPreferences(
                ComboPreferences.getGlobalSharedPreferencesName(getActivity()), Context.MODE_PRIVATE);
        SettingsManager settingsManager = SettingsManager.getInstance();
        int index = settingsManager.getValueIndex(SettingsManager.KEY_SCENE_MODE);
        final String instructionalKey = SettingsManager.KEY_SCENE_MODE + "_" + index;
        return !pref.getBoolean(instructionalKey, false);
    }

    private void showSceneInstructionalDialog(int orientation) {
        int layoutId = R.layout.scene_mode_instructional;
        if (orientation == 90 || orientation == 270) {
            layoutId = R.layout.scene_mode_instructional_landscape;
        }
        View view = getActivity().getLayoutInflater().inflate(layoutId, null);

        TextView name = (TextView) view.findViewById(R.id.scene_mode_name);
        name.setText(R.string.pref_camera_scenemode_entry_panorama);

        ImageView icon = (ImageView) view.findViewById(R.id.scene_mode_icon);
        icon.setImageResource(R.drawable.ic_scene_mode_black_panorama);

        TextView instructional = (TextView) view.findViewById(R.id.scene_mode_instructional);
        instructional.setText(R.string.pref_camera2_scene_mode_panorama_instructional_content);

        final CheckBox remember = (CheckBox) view.findViewById(R.id.remember_selected);
        Button ok = (Button) view.findViewById(R.id.scene_mode_instructional_ok);
        ok.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                SharedPreferences pref = getActivity().getSharedPreferences(
                        ComboPreferences.getGlobalSharedPreferencesName(getActivity()),
                        Context.MODE_PRIVATE);
                int index =
                        SettingsManager.getInstance().getValueIndex(SettingsManager.KEY_SCENE_MODE);
                String instructionalKey = SettingsManager.KEY_SCENE_MODE + "_" + index;
                if ( remember.isChecked()) {
                    SharedPreferences.Editor editor = pref.edit();
                    editor.putBoolean(instructionalKey, true);
                    editor.commit();
                }
                mSceneModeInstructionalDialog.dismiss();
                mSceneModeInstructionalDialog = null;
            }
        });
        mSceneModeInstructionalDialog =
                new AlertDialog.Builder(getActivity(), AlertDialog.THEME_HOLO_LIGHT)
                        .setView(view).create();
        try {
            mSceneModeInstructionalDialog.show();
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        if (orientation != 0) {
            rotationSceneModeInstructionalDialog(view, orientation);
        }
    }

    private void rotationSceneModeInstructionalDialog(View view, int orientation) {
        view.setRotation(-orientation);
        int screenWidth = getScreenX();
        int dialogSize = screenWidth * 9 / 10;
        Window dialogWindow = mSceneModeInstructionalDialog.getWindow();
        WindowManager.LayoutParams lp = dialogWindow.getAttributes();
        dialogWindow.setGravity(Gravity.CENTER);
        lp.width = lp.height = dialogSize;
        dialogWindow.setAttributes(lp);
        RelativeLayout layout = (RelativeLayout) view.findViewById(R.id.mode_layout_rect);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(dialogSize, dialogSize);
        layout.setLayoutParams(params);
    }
}
