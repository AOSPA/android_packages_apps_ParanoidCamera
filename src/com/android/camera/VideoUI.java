/*
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

import java.util.List;

import android.content.res.Configuration;
import android.filterfw.core.Frame;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.Face;
import android.support.annotation.LayoutRes;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLayoutChangeListener;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.widget.FrameLayout;
import android.widget.FrameLayout.LayoutParams;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.TextView;

import com.android.camera.CameraManager.CameraProxy;
import com.android.camera.CameraPreference.OnPreferenceChangedListener;
import com.android.camera.PhotoUI.SurfaceTextureSizeChangedListener;
import com.android.camera.ui.*;
import com.android.camera.util.CameraUtil;

import org.codeaurora.snapcam.R;

public class VideoUI extends CameraUI implements PieRenderer.PieListener,
        PreviewGestures.SingleTapListener,
        PauseButton.OnPauseButtonListener,
        CameraManager.CameraFaceDetectionCallback {
    private static final String TAG = "CAM_VideoUI";
    // module fields
    // An review image having same size as preview. It is displayed when
    // recording is stopped in capture intent.
    private ImageView mReviewImage;
    private View mReviewCancelButton;
    private View mReviewDoneButton;
    private View mReviewPlayButton;
    private ShutterButton mShutterButton;
    private PauseButton mPauseButton;
    private ModuleSwitcher mSwitcher;
    private TextView mRecordingTimeView;
    private LinearLayout mLabelsLinearLayout;
    private View mTimeLapseLabel;
    private RenderOverlay mRenderOverlay;
    private PieRenderer mPieRenderer;
    private VideoMenu mVideoMenu;
    private SettingsPopup mPopup;
    private ZoomRenderer mZoomRenderer;
    private PreviewGestures mGestures;
    private View mMenuButton;
    private OnScreenIndicators mOnScreenIndicators;
    private RotateLayout mRecordingTimeRect;
    private boolean mRecordingStarted = false;
    private VideoController mController;
    private int mZoomMax;
    private List<Integer> mZoomRatios;
    private ImageView mThumbnail;
    private View mFlashOverlay;
    private boolean mOrientationResize;
    private boolean mPrevOrientationResize;
    private boolean mIsTimeLapse = false;
    private RotateLayout mMenuLayout;
    private RotateLayout mSubMenuLayout;
    private LinearLayout mPreviewMenuLayout;

    private View mPreviewCover;
    private int mMaxPreviewWidth = 0;
    private int mMaxPreviewHeight = 0;
    private float mAspectRatio = 4f / 3f;
    private boolean mAspectRatioResize;
    private final AnimationManager mAnimationManager;
    private int mPreviewOrientation = -1;
    private int mOrientation;

    private RotateImageView mMuteButton;

    //Face detection
    private FaceView mFaceView;
    private SurfaceTextureSizeChangedListener mSurfaceTextureSizeListener;
    private float mSurfaceTextureUncroppedWidth;
    private float mSurfaceTextureUncroppedHeight;

    public void showPreviewCover() {
        mPreviewCover.setVisibility(View.VISIBLE);
    }

    public void hidePreviewCover() {
        if (mPreviewCover != null && mPreviewCover.getVisibility() != View.GONE) {
            mPreviewCover.setVisibility(View.GONE);
        }
    }

    public boolean isPreviewCoverVisible() {
        if ((mPreviewCover != null) &&
                (mPreviewCover.getVisibility() == View.VISIBLE)) {
            return true;
        } else {
            return false;
        }
    }

    private class SettingsPopup extends PopupWindow {
        public SettingsPopup(View popup) {
            super(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            setOutsideTouchable(true);
            setFocusable(true);
            popup.setVisibility(View.VISIBLE);
            setContentView(popup);
            showAtLocation(getRootView(), Gravity.CENTER, 0, 0);
        }

        public void dismiss(boolean topLevelOnly) {
            super.dismiss();
            popupDismissed();
            showUI();
            // mVideoMenu.popupDismissed(topLevelOnly);

            // Switch back into fullscreen/lights-out mode after popup
            // is dimissed.
            getActivity().setSystemBarsVisibility(false);
        }

        @Override
        public void dismiss() {
            // Called by Framework when touch outside the popup or hit back key
            dismiss(true);
        }
    }

    @Override
    public @LayoutRes
    int getUILayout() {
        return R.layout.video_module;
    }

    public VideoUI(final CameraActivity activity, VideoController controller, View parent) {
        super(activity, parent);
        mController = controller;
        mPreviewCover = parent.findViewById(R.id.preview_cover);

        View surfaceContainer = getRootView().findViewById(R.id.preview_container);
        surfaceContainer.addOnLayoutChangeListener(new OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right,
                                       int bottom, int oldLeft, int oldTop, int oldRight,
                                       int oldBottom) {
                int width = right - left;
                int height = bottom - top;

                tryToCloseSubList();
                if (mMaxPreviewWidth == 0 && mMaxPreviewHeight == 0) {
                    mMaxPreviewWidth = width;
                    mMaxPreviewHeight = height;
                }

                int orientation = activity.getResources().getConfiguration().orientation;
                if ((orientation == Configuration.ORIENTATION_PORTRAIT && width > height)
                        || (orientation == Configuration.ORIENTATION_LANDSCAPE && width < height)) {
                    // The screen has rotated; swap SurfaceView width & height
                    // to ensure correct preview
                    int oldWidth = width;
                    width = height;
                    height = oldWidth;
                    Log.d(TAG, "Swapping SurfaceView width & height dimensions");
                    if (mMaxPreviewWidth != 0 && mMaxPreviewHeight != 0) {
                        int temp = mMaxPreviewWidth;
                        mMaxPreviewWidth = mMaxPreviewHeight;
                        mMaxPreviewHeight = temp;
                    }
                }
                if (mOrientationResize != mPrevOrientationResize
                        || mAspectRatioResize) {
                    layoutPreview(mAspectRatio);
                    mAspectRatioResize = false;
                }
            }
        });

        mFlashOverlay = getRootView().findViewById(R.id.flash_overlay);
        mShutterButton = (ShutterButton) getRootView().findViewById(R.id.shutter_button);
        mSwitcher = (ModuleSwitcher) getRootView().findViewById(R.id.camera_switcher);
        mSwitcher.setCurrentIndex(ModuleSwitcher.VIDEO_MODULE_INDEX);
        mSwitcher.setSwitchListener(activity);

        mMuteButton = (RotateImageView) getRootView().findViewById(R.id.mute_button);
        mMuteButton.setVisibility(View.VISIBLE);
        if (!((VideoModule) mController).isAudioMute()) {
            mMuteButton.setImageResource(R.drawable.ic_unmuted_button);
        } else {
            mMuteButton.setImageResource(R.drawable.ic_muted_button);
        }
        mMuteButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean isEnabled = !((VideoModule) mController).isAudioMute();
                ((VideoModule) mController).setMute(isEnabled, true);
                if (!isEnabled)
                    mMuteButton.setImageResource(R.drawable.ic_unmuted_button);
                else
                    mMuteButton.setImageResource(R.drawable.ic_muted_button);
            }
        });

        initializeMiscControls();
        initializeControlByIntent();
        initializeOverlay();
        initializePauseButton();

        ViewStub faceViewStub = (ViewStub) getRootView()
                .findViewById(R.id.face_view_stub);
        if (faceViewStub != null) {
            faceViewStub.inflate();
            mFaceView = (FaceView) getRootView().findViewById(R.id.face_view);
            setSurfaceTextureSizeChangedListener(mFaceView);
        }
        mAnimationManager = new AnimationManager();
        mOrientationResize = false;
        mPrevOrientationResize = false;

        ((ViewGroup) getRootView()).removeView(mRecordingTimeRect);
    }

    public void cameraOrientationPreviewResize(boolean orientation) {
        mPrevOrientationResize = mOrientationResize;
        mOrientationResize = orientation;
    }

    public void setSurfaceTextureSizeChangedListener(SurfaceTextureSizeChangedListener listener) {
        mSurfaceTextureSizeListener = listener;
    }

    private void initializeControlByIntent() {
        mMenuButton = getRootView().findViewById(R.id.settings);
        mMenuButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                mVideoMenu.openFirstLevel();
            }
        });

        mOnScreenIndicators = new OnScreenIndicators(getActivity(),
                getRootView().findViewById(R.id.on_screen_indicators));
        mOnScreenIndicators.resetToDefault();
        if (mController.isVideoCaptureIntent()) {
            hideSwitcher();

            getCameraControls().showCameraControlsSettings(false);
            getCameraControls().disableCameraControlsSettingsSwitch();

            // Cannot use RotateImageView for "done" and "cancel" button because
            // the tablet layout uses RotateLayout, which cannot be cast to
            // RotateImageView.
            mReviewDoneButton = getRootView().findViewById(R.id.btn_done);
            mReviewCancelButton = getRootView().findViewById(R.id.btn_cancel);
            mReviewPlayButton = getRootView().findViewById(R.id.btn_play);
            mReviewCancelButton.setVisibility(View.VISIBLE);
            mReviewDoneButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    mController.onReviewDoneClicked(v);
                }
            });
            mReviewCancelButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    mController.onReviewCancelClicked(v);
                }
            });
            mReviewPlayButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    mController.onReviewPlayClicked(v);
                }
            });
        }
    }

    public void setPreviewSize(int width, int height) {
        if (width == 0 || height == 0) {
            Log.w(TAG, "Preview size should not be 0.");
            return;
        }
        float ratio;
        if (width > height) {
            ratio = (float) width / height;
        } else {
            ratio = (float) height / width;
        }
        if (mOrientationResize &&
                getActivity().getResources().getConfiguration().orientation
                        != Configuration.ORIENTATION_PORTRAIT) {
            ratio = 1 / ratio;
        }

        if (ratio != mAspectRatio) {
            mAspectRatioResize = true;
            mAspectRatio = ratio;
        }

        layoutPreview(ratio);
    }

    public void layoutPreview(float camAspectRatio) {
        FrameLayout.LayoutParams lp = getSurfaceSizeParams(camAspectRatio);

        if (mSurfaceTextureUncroppedWidth != lp.width ||
                mSurfaceTextureUncroppedHeight != lp.height) {
            mSurfaceTextureUncroppedWidth = lp.width;
            mSurfaceTextureUncroppedHeight = lp.height;
            if (mSurfaceTextureSizeListener != null) {
                mSurfaceTextureSizeListener.onSurfaceTextureSizeChanged(
                        (int) mSurfaceTextureUncroppedWidth,
                        (int) mSurfaceTextureUncroppedHeight);
            }
        }

        getSurfaceView().setLayoutParams(lp);
        getRootView().requestLayout();
        if (mFaceView != null) {
            mFaceView.setLayoutParams(lp);
        }

        setSurfaceArea();
    }

    /**
     * Starts a flash animation
     */
    public void animateFlash() {
        mAnimationManager.startFlashAnimation(mFlashOverlay);
    }

    /**
     * Starts a capture animation
     */
    public void animateCapture() {
        Bitmap bitmap = null;
        animateCapture(bitmap);
    }

    /**
     * Starts a capture animation
     *
     * @param bitmap the captured image that we shrink and slide in the animation
     */
    public void animateCapture(Bitmap bitmap) {
        if (bitmap == null) {
            Log.e(TAG, "No valid bitmap for capture animation.");
            return;
        }
        getActivity().updateThumbnail(bitmap);
        mAnimationManager.startCaptureAnimation(mThumbnail);
    }

    /**
     * Cancels on-going animations
     */
    public void cancelAnimations() {
        mAnimationManager.cancelAnimations();
    }

    @Override
    public void showUI() {
        if (mVideoMenu != null && mVideoMenu.isMenuBeingShown()) {
            return;
        }
        super.showUI();
    }

    public void hideSwitcher() {
        mSwitcher.setVisibility(View.INVISIBLE);
    }

    public void showSwitcher() {
        mSwitcher.setVisibility(View.VISIBLE);
    }

    public void setSwitcherIndex() {
        mSwitcher.setCurrentIndex(ModuleSwitcher.VIDEO_MODULE_INDEX);
    }

    public boolean collapseCameraControls() {
        boolean ret = false;
        if (mVideoMenu != null) {
            mVideoMenu.closeAllView();
        }
        if (mPopup != null) {
            dismissPopup(false);
            ret = true;
        }
        return ret;
    }

    public boolean removeTopLevelPopup() {
        if (mPopup != null) {
            dismissPopup(true);
            return true;
        }
        return false;
    }

    public void enableCameraControls(boolean enable) {
        if (mGestures != null) {
            mGestures.setZoomOnly(!enable);
        }
        if (mPieRenderer != null && mPieRenderer.showsItems()) {
            mPieRenderer.hide();
        }
    }

    public void setDisplayOrientation(int orientation) {
        if (mFaceView != null) {
            mFaceView.setDisplayOrientation(orientation);
        }

        if ((mPreviewOrientation == -1 || mPreviewOrientation != orientation)
                && mVideoMenu != null && mVideoMenu.isPreviewMenuBeingShown()) {
            dismissSceneModeMenu();
            mVideoMenu.addModeBack();
        }
        mPreviewOrientation = orientation;
    }

    // no customvideo?
    public void overrideSettings(final String... keyvalues) {
        if (mVideoMenu != null) {
            mVideoMenu.overrideSettings(keyvalues);
        }
    }

    public void setOrientationIndicator(int orientation, boolean animation) {
        // We change the orientation of the linearlayout only for phone UI
        // because when in portrait the width is not enough.
        if (mLabelsLinearLayout != null) {
            if (((orientation / 90) & 1) == 0) {
                mLabelsLinearLayout.setOrientation(LinearLayout.VERTICAL);
            } else {
                mLabelsLinearLayout.setOrientation(LinearLayout.HORIZONTAL);
            }
        }
    }

    private void initializeOverlay() {
        mRenderOverlay = (RenderOverlay) getRootView().findViewById(R.id.render_overlay);
        if (mPieRenderer == null) {
            mPieRenderer = new PieRenderer(getActivity());
            mPieRenderer.setPieListener(this);
            mRenderOverlay.addRenderer(mPieRenderer);
            setPieRenderer(mPieRenderer);
        }
        if (mVideoMenu == null) {
            mVideoMenu = new VideoMenu(getActivity(), this);
        }
        if (mZoomRenderer == null) {
            mZoomRenderer = new ZoomRenderer(getActivity());
            mZoomRenderer.setCameraControlHeight(getControlHeight());
            mRenderOverlay.addRenderer(mZoomRenderer);
        }
        if (mGestures == null) {
            mGestures = new PreviewGestures(getActivity(), this,
                    mController.isVideoCaptureIntent() ? null : this,
                    mZoomRenderer, mPieRenderer, null);
            mRenderOverlay.setGestures(mGestures);
            mGestures.setVideoMenu(mVideoMenu);
            mGestures.setRenderOverlay(mRenderOverlay);
        }

        if (!getActivity().isSecureCamera()) {
            mThumbnail = (ImageView) getRootView().findViewById(R.id.preview_thumb);
            mThumbnail.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    // Do not allow navigation to filmstrip during video recording
                    if (!mRecordingStarted) {
                        getActivity().gotoGallery();
                    }
                }
            });
        }
    }

    public void setPreviewGesturesVideoUI() {
        getActivity().setPreviewGestures(mGestures);
    }

    public void setPrefChangedListener(OnPreferenceChangedListener listener) {
        mVideoMenu.setListener(listener);
    }

    private void initializeMiscControls() {
        mReviewImage = (ImageView) getRootView().findViewById(R.id.review_image);
        mShutterButton.setImageResource(R.drawable.btn_new_shutter_video);
        mShutterButton.setOnShutterButtonListener(mController);
        mShutterButton.setVisibility(View.VISIBLE);
        mShutterButton.requestFocus();
        mShutterButton.enableTouch(true);
        mRecordingTimeView = (TextView) getRootView().findViewById(R.id.recording_time);
        mRecordingTimeRect = (RotateLayout) getRootView().findViewById(R.id.recording_time_rect);
        mTimeLapseLabel = getRootView().findViewById(R.id.time_lapse_label);
        // The R.id.labels can only be found in phone layout.
        // That is, mLabelsLinearLayout should be null in tablet layout.
        mLabelsLinearLayout = (LinearLayout) getRootView().findViewById(R.id.labels);
    }

    private void initializePauseButton() {
        mPauseButton = (PauseButton) getRootView().findViewById(R.id.video_pause);
        mPauseButton.setOnPauseButtonListener(this);
    }

    public void updateOnScreenIndicators(Parameters param, ComboPreferences prefs) {
        mOnScreenIndicators.updateFlashOnScreenIndicator(param.getFlashMode());
        boolean location = RecordLocationPreference.get(prefs, CameraSettings.KEY_RECORD_LOCATION);
        mOnScreenIndicators.updateLocationIndicator(location);

    }

    public void setAspectRatio(double ratio) {
        if (mOrientationResize &&
                getActivity().getResources().getConfiguration().orientation
                        != Configuration.ORIENTATION_PORTRAIT) {
            ratio = 1 / ratio;
        }

        if (ratio != mAspectRatio) {
            mAspectRatioResize = true;
            mAspectRatio = (float) ratio;
        }

        layoutPreview((float) ratio);
    }

    public void showTimeLapseUI(boolean enable) {
        if (mTimeLapseLabel != null) {
            mTimeLapseLabel.setVisibility(enable ? View.VISIBLE : View.GONE);
        }
        mIsTimeLapse = enable;
    }

    public void dismissPopup(boolean topLevelOnly) {
        // In review mode, we do not want to bring up the camera UI
        if (mController.isInReviewMode()) return;
        if (mPopup != null) {
            mPopup.dismiss(topLevelOnly);
        }
    }

    public boolean is4KEnabled() {
        if (mController != null)
            return ((VideoModule) mController).is4KEnabled();
        else
            return false;
    }

    private void popupDismissed() {
        mPopup = null;
    }

    public boolean onBackPressed() {
        if (mVideoMenu != null && mVideoMenu.handleBackKey()) {
            return true;
        }
        if (hidePieRenderer()) {
            return true;
        } else {
            return removeTopLevelPopup();
        }
    }

    public void cleanupListview() {
        showUI();
        getActivity().setSystemBarsVisibility(false);
    }

    public void dismissLevel1() {
        if (mMenuLayout != null) {
            ((ViewGroup) getRootView()).removeView(mMenuLayout);
            mMenuLayout = null;
        }
    }

    public void dismissLevel2() {
        if (mSubMenuLayout != null) {
            ((ViewGroup) getRootView()).removeView(mSubMenuLayout);
            mSubMenuLayout = null;
        }
    }

    public boolean sendTouchToPreviewMenu(MotionEvent ev) {
        return mPreviewMenuLayout.dispatchTouchEvent(ev);
    }

    public boolean sendTouchToMenu(MotionEvent ev) {
        if (mMenuLayout != null) {
            View v = mMenuLayout.getChildAt(0);
            return v.dispatchTouchEvent(ev);
        }
        return false;
    }

    public void dismissSceneModeMenu() {
        if (mPreviewMenuLayout != null) {
            ((ViewGroup) getRootView()).removeView(mPreviewMenuLayout);
            mPreviewMenuLayout = null;
        }
    }

    public void removeSceneModeMenu() {
        if (mPreviewMenuLayout != null) {
            ((ViewGroup) getRootView()).removeView(mPreviewMenuLayout);
            mPreviewMenuLayout = null;
        }
        cleanupListview();
    }

    public void removeLevel2() {
        if (mSubMenuLayout != null) {
            View v = mSubMenuLayout.getChildAt(0);
            mSubMenuLayout.removeView(v);
        }
    }

    public void showPopup(ListView popup, int level, boolean animate) {
        hideUI();

        popup.setVisibility(View.VISIBLE);
        if (level == 1) {
            if (mMenuLayout == null) {
                mMenuLayout = new RotateLayout(getActivity());
                mMenuLayout.setLayoutParams(new FrameLayout.LayoutParams(
                        CameraActivity.SETTING_LIST_WIDTH_1, LayoutParams.MATCH_PARENT,
                        Gravity.LEFT));
                ((ViewGroup) getRootView()).addView(mMenuLayout);
            }
            mMenuLayout.setOrientation(mOrientation, true);
            mMenuLayout.addView(popup);
            popup.getLayoutParams().height = LayoutParams.MATCH_PARENT;
            popup.requestLayout();
        } else if (level == 2) {
            if (mSubMenuLayout == null) {
                mSubMenuLayout = new RotateLayout(getActivity());
                ((ViewGroup) getRootView()).addView(mSubMenuLayout);
            }
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    CameraActivity.SETTING_LIST_WIDTH_2, LayoutParams.WRAP_CONTENT,
                    Gravity.LEFT | Gravity.TOP);
            int screenHeight = (mOrientation == 0 || mOrientation == 180)
                    ? getRootView().getHeight() : getRootView().getWidth();
            int height = ((ListSubMenu) popup).getPreCalculatedHeight();
            int yBase = ((ListSubMenu) popup).getYBase();
            int y = Math.max(0, yBase);
            if (yBase + height > screenHeight)
                y = Math.max(0, screenHeight - height);
            params.setMargins(CameraActivity.SETTING_LIST_WIDTH_1, y, 0, 0);

            mSubMenuLayout.setLayoutParams(params);

            mSubMenuLayout.addView(popup);
            mSubMenuLayout.setOrientation(mOrientation, true);
        }
        if (animate) {
            if (level == 1)
                mVideoMenu.animateSlideIn(mMenuLayout, CameraActivity.SETTING_LIST_WIDTH_1, true);
            if (level == 2)
                mVideoMenu.animateFadeIn(popup);
        } else
            popup.setAlpha(0.85f);
    }

    public ViewGroup getMenuLayout() {
        return mMenuLayout;
    }

    public void setPreviewMenuLayout(LinearLayout layout) {
        mPreviewMenuLayout = layout;
    }

    public ViewGroup getPreviewMenuLayout() {
        return mPreviewMenuLayout;
    }

    public void showPopup(AbstractSettingPopup popup) {
        hideUI();

        if (mPopup != null) {
            mPopup.dismiss(false);
        }
        mPopup = new SettingsPopup(popup);
    }

    public boolean hidePieRenderer() {
        if (mPieRenderer != null && mPieRenderer.showsItems()) {
            mPieRenderer.hide();
            return true;
        }
        return false;
    }

    // disable preview gestures after shutter is pressed
    public void setShutterPressed(boolean pressed) {
        if (mGestures == null) return;
        mGestures.setEnabled(!pressed);
    }

    public void enableShutter(boolean enable) {
        if (mShutterButton != null) {
            if (enable) {
                Log.v(TAG, "Shutter Button enabled !!");
            } else {
                Log.v(TAG, "Shutter Button disabled !!");
            }
            mShutterButton.setEnabled(enable);
        }
    }

    // PieListener
    @Override
    public void onPieOpened(int centerX, int centerY) {
        setSwipingEnabled(false);
    }

    @Override
    public void onPieClosed() {
        setSwipingEnabled(true);
    }

    @Override
    public void onPieMoved(int centerX, int centerY) {

    }

    public void setSwipingEnabled(boolean enable) {
        getActivity().setSwipingEnabled(enable);
    }

    public void showPreviewBorder(boolean enable) {
        // TODO: mPreviewFrameLayout.showBorder(enable);
    }

    // SingleTapListener
    // Preview area is touched. Take a picture.
    @Override
    public void onSingleTapUp(View view, int x, int y) {
        mController.onSingleTapUp(view, x, y);
    }

    public void showRecordingUI(boolean recording) {
        mRecordingStarted = recording;
        mMenuButton.setVisibility(recording ? View.GONE : View.VISIBLE);
        mOnScreenIndicators.setVisibility(recording ? View.GONE : View.VISIBLE);
        if (recording) {
            mShutterButton.setImageResource(R.drawable.shutter_button_video_stop);
            hideSwitcher();
            mRecordingTimeView.setText("");
            ((ViewGroup) getRootView()).addView(mRecordingTimeRect);
        } else {
            mShutterButton.setImageResource(R.drawable.btn_new_shutter_video);
            if (!mController.isVideoCaptureIntent()) {
                showSwitcher();
            }
            ((ViewGroup) getRootView()).removeView(mRecordingTimeRect);
        }
    }

    public void hideUIwhileRecording() {
        getCameraControls().setWillNotDraw(true);
        mVideoMenu.hideUI();
    }

    public void showUIafterRecording() {
        getCameraControls().setWillNotDraw(false);
        if (!mController.isVideoCaptureIntent()) {
            getCameraControls().enableCameraControlsSettingsSwitch();
            mVideoMenu.showUI();
        }
    }

    public void showReviewImage(Bitmap bitmap) {
        mReviewImage.setImageBitmap(bitmap);
        mReviewImage.setVisibility(View.VISIBLE);
    }

    public void showReviewControls() {
        CameraUtil.fadeOut(mShutterButton);
        CameraUtil.fadeIn(mReviewDoneButton);
        CameraUtil.fadeIn(mReviewPlayButton);
        mReviewImage.setVisibility(View.VISIBLE);
        mMenuButton.setVisibility(View.GONE);
        getCameraControls().hideUI();
        mVideoMenu.hideUI();
        mOnScreenIndicators.setVisibility(View.GONE);
    }

    public void hideReviewUI() {
        mReviewImage.setVisibility(View.GONE);
        mShutterButton.setEnabled(true);
        mMenuButton.setVisibility(View.VISIBLE);
        getCameraControls().showUI();
        mVideoMenu.showUI();
        mOnScreenIndicators.setVisibility(View.VISIBLE);
        CameraUtil.fadeOut(mReviewDoneButton);
        CameraUtil.fadeOut(mReviewPlayButton);
        CameraUtil.fadeIn(mShutterButton);
    }

    private void setShowMenu(boolean show) {
        if (mController.isVideoCaptureIntent())
            return;
        if (mOnScreenIndicators != null) {
            mOnScreenIndicators.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    public void onPreviewFocusChanged(boolean previewFocused) {
        if (previewFocused) {
            showUI();
        } else {
            hideUI();
        }
        if (mGestures != null) {
            mGestures.setEnabled(previewFocused);
        }
        if (mRenderOverlay != null) {
            // this can not happen in capture mode
            mRenderOverlay.setVisibility(previewFocused ? View.VISIBLE : View.GONE);
        }
        setShowMenu(previewFocused);
    }

    public void initializePopup(PreferenceGroup pref) {
        mVideoMenu.initialize(pref);
    }

    public void initializeZoom(Parameters param) {
        if (param == null || !param.isZoomSupported()) {
            mGestures.setZoomEnabled(false);
            return;
        }
        mGestures.setZoomEnabled(true);
        mZoomMax = param.getMaxZoom();
        mZoomRatios = param.getZoomRatios();
        // Currently we use immediate zoom for fast zooming to get better UX and
        // there is no plan to take advantage of the smooth zoom.
        mZoomRenderer.setZoomMax(mZoomMax);
        mZoomRenderer.setZoom(param.getZoom());
        mZoomRenderer.setZoomValue(mZoomRatios.get(param.getZoom()));
        mZoomRenderer.setOnZoomChangeListener(new ZoomChangeListener());
    }

    public void clickShutter() {
        mShutterButton.performClick();
    }

    public void pressShutter(boolean pressed) {
        mShutterButton.setPressed(pressed);
    }

    public View getShutterButton() {
        return mShutterButton;
    }

    public void setRecordingTime(String text) {
        mRecordingTimeView.setText(text);
    }

    public void setRecordingTimeTextColor(int color) {
        mRecordingTimeView.setTextColor(color);
    }

    public boolean isVisible() {
        return getCameraControls().getVisibility() == View.VISIBLE;
    }

    @Override
    public void onDisplayChanged() {
        super.onDisplayChanged();
        mController.updateCameraOrientation();
    }

    private class ZoomChangeListener implements ZoomRenderer.OnZoomChangedListener {
        @Override
        public void onZoomValueChanged(int index) {
            int newZoom = mController.onZoomChanged(index);
            if (mZoomRenderer != null) {
                mZoomRenderer.setZoomValue(mZoomRatios.get(newZoom));
            }
        }

        @Override
        public void onZoomStart() {
            if (mPieRenderer != null) {
                if (!mRecordingStarted) mPieRenderer.hide();
                mPieRenderer.setBlockFocus(true);
            }
        }

        @Override
        public void onZoomEnd() {
            if (mPieRenderer != null) {
                mPieRenderer.setBlockFocus(false);
            }
        }

        @Override
        public void onZoomValueChanged(float value) {
        }
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

    @Override
    public void onButtonPause() {
        mRecordingTimeView.setCompoundDrawablesWithIntrinsicBounds(
                R.drawable.ic_pausing_indicator, 0, 0, 0);
        mController.onButtonPause();
    }

    @Override
    public void onButtonContinue() {
        mRecordingTimeView.setCompoundDrawablesWithIntrinsicBounds(
                R.drawable.ic_recording_indicator, 0, 0, 0);
        mController.onButtonContinue();
    }

    public void resetPauseButton() {
        mRecordingTimeView.setCompoundDrawablesWithIntrinsicBounds(
                R.drawable.ic_recording_indicator, 0, 0, 0);
        mPauseButton.setPaused(false);
    }

    public void setPreference(String key, String value) {
        mVideoMenu.setPreference(key, value);
    }

    public void setOrientation(int orientation, boolean animation) {
        getCameraControls().setOrientation(orientation, animation);
        if (mMenuLayout != null)
            mMenuLayout.setOrientation(orientation, animation);
        if (mSubMenuLayout != null)
            mSubMenuLayout.setOrientation(orientation, animation);
        if (mRecordingTimeRect != null) {
            if (orientation == 180) {
                mRecordingTimeRect.setOrientation(0, false);
                mRecordingTimeView.setRotation(180);
            } else {
                mRecordingTimeView.setRotation(0);
                mRecordingTimeRect.setOrientation(orientation, false);
            }
        }
        if (mPreviewMenuLayout != null) {
            ViewGroup vg = (ViewGroup) mPreviewMenuLayout.getChildAt(0);
            if (vg != null)
                vg = (ViewGroup) vg.getChildAt(0);
            if (vg != null) {
                for (int i = vg.getChildCount() - 1; i >= 0; --i) {
                    RotateLayout l = (RotateLayout) vg.getChildAt(i);
                    l.setOrientation(orientation, animation);
                }
            }
        }
        if (mZoomRenderer != null) {
            mZoomRenderer.setOrientation(orientation);
        }
        RotateTextToast.setOrientation(orientation);
        mOrientation = orientation;
    }

    public void tryToCloseSubList() {
        if (mVideoMenu != null)
            mVideoMenu.tryToCloseSubList();
    }

    public int getOrientation() {
        return mOrientation;
    }

    public void adjustOrientation() {
        setOrientation(mOrientation, false);
    }

    @Override
    public void onFaceDetection(Face[] faces, CameraProxy camera) {
        Log.d(TAG, "onFacedetectopmn");
        mFaceView.setFaces(faces);
    }

    public void pauseFaceDetection() {
        if (mFaceView != null) mFaceView.pause();
    }

    public void resumeFaceDetection() {
        if (mFaceView != null) mFaceView.resume();
    }

    public void onStartFaceDetection(int orientation, boolean mirror) {
        mFaceView.setBlockDraw(false);
        mFaceView.clear();
        mFaceView.setVisibility(View.VISIBLE);
        mFaceView.setDisplayOrientation(orientation);
        mFaceView.setMirror(mirror);
        mFaceView.resume();
    }

    public void onStopFaceDetection() {
        if (mFaceView != null) {
            mFaceView.setBlockDraw(true);
            mFaceView.clear();
        }
    }
}
