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

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.hardware.Camera;
import android.hardware.Camera.Face;
import android.os.AsyncTask;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.annotation.LayoutRes;
import android.util.Log;
import android.util.Size;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLayoutChangeListener;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.FrameLayout.LayoutParams;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.SeekBar;
import android.widget.Toast;

import com.android.camera.CameraPreference.OnPreferenceChangedListener;
import com.android.camera.FocusOverlayManager.FocusUI;
import com.android.camera.TsMakeupManager.MakeupLevelListener;
import com.android.camera.ui.*;
import com.android.camera.ui.CountDownView.OnCountDownFinishedListener;
import com.android.camera.ui.PieRenderer.PieListener;
import com.android.camera.util.CameraUtil;

import co.paranoidandroid.camera.R;

public class PhotoUI extends CameraUI implements PieListener,
        PreviewGestures.SingleTapListener,
        FocusUI,
        CameraManager.CameraFaceDetectionCallback {

    private static final String TAG = "CAM_UI";
    private int mDownSampleFactor = 4;
    private final AnimationManager mAnimationManager;
    private PhotoController mController;
    private PreviewGestures mGestures;

    private PopupWindow mPopup;
    private ShutterButton mShutterButton;
    private View mFrontBackSwitcher;
    private boolean mFrontBackSwitcherVisible;
    private CountDownView mCountDownView;
    private SelfieFlashView mSelfieView;

    private FaceView mFaceView;
    private RenderOverlay mRenderOverlay;
    private View mReviewCancelButton;
    private View mReviewDoneButton;
    private View mReviewRetakeButton;
    private ImageView mReviewImage;
    private DecodeImageForReview mDecodeTaskForReview = null;

    private View mMenuButton;
    private PhotoMenu mMenu;
    private ModuleSwitcher mSwitcher;
    private AlertDialog mLocationDialog;
    private SeekBar mBlurDegreeProgressBar;

    // Small indicators which show the camera settings in the viewfinder.
    private OnScreenIndicators mOnScreenIndicators;

    private PieRenderer mPieRenderer;
    private ZoomRenderer mZoomRenderer;
    private RotateTextToast mNotSelectableToast;

    private Handler mHandler = new Handler();

    private int mZoomMax;
    private List<Integer> mZoomRatios;

    private int mPreviewWidth = 0;
    private int mPreviewHeight = 0;

    public boolean mMenuInitialized = false;
    private int mSurfaceTextureUncroppedWidth;
    private int mSurfaceTextureUncroppedHeight;

    private ImageView mThumbnail;
    private View mFlashOverlay;

    private SurfaceTextureSizeChangedListener mSurfaceTextureSizeListener;
    private float mAspectRatio = 4f / 3f;
    private boolean mAspectRatioResize;

    private boolean mOrientationResize;
    private boolean mPrevOrientationResize;
    private View mPreviewCover;
    private RotateLayout mMenuLayout;
    private RotateLayout mSubMenuLayout;
    private LinearLayout mPreviewMenuLayout;
    private LinearLayout mMakeupMenuLayout;
    private int mPreviewOrientation = -1;

    private boolean mIsLayoutInitializedAlready = false;

    private int mOrientation;
    private float mScreenBrightness = 0.0f;
    private boolean mIsBokehMode = false;

    public interface SurfaceTextureSizeChangedListener {
        public void onSurfaceTextureSizeChanged(int uncroppedWidth, int uncroppedHeight);
    }

    private class DecodeTask extends AsyncTask<Void, Void, Bitmap> {
        private final byte[] mData;
        private int mOrientation;
        private boolean mMirror;

        public DecodeTask(byte[] data, int orientation, boolean mirror) {
            mData = data;
            mOrientation = orientation;
            mMirror = mirror;
        }

        @Override
        protected Bitmap doInBackground(Void... params) {
            // Decode image in background.
            Bitmap bitmap = CameraUtil.downSample(mData, mDownSampleFactor);
            if ((mOrientation != 0 || mMirror) && (bitmap != null)) {
                Matrix m = new Matrix();
                if (mMirror) {
                    // Flip horizontally
                    m.setScale(-1f, 1f);
                }
                m.preRotate(mOrientation);
                return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), m,
                        false);
            }
            return bitmap;
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
        }
    }

    private class DecodeImageForReview extends DecodeTask {
        public DecodeImageForReview(byte[] data, int orientation, boolean mirror) {
            super(data, orientation, mirror);
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            if (isCancelled()) {
                return;
            }
            mReviewImage.setImageBitmap(bitmap);
            mReviewImage.setVisibility(View.VISIBLE);
            mDecodeTaskForReview = null;
        }
    }

    @Override
    public @LayoutRes
    int getUILayout() {
        return R.layout.photo_module;
    }

    public PhotoUI(CameraActivity activity, PhotoController controller, View parent) {
        super(activity, parent);
        mController = controller;
        mPreviewCover = parent.findViewById(R.id.preview_cover);

        getSurfaceView().addOnLayoutChangeListener(new OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right,
                                       int bottom, int oldLeft, int oldTop, int oldRight,
                                       int oldBottom) {
                tryToCloseSubList();

                if (mOrientationResize != mPrevOrientationResize
                        || mAspectRatioResize || !mIsLayoutInitializedAlready) {
                    layoutPreview(mAspectRatio, mPreviewWidth, mPreviewHeight);
                    mAspectRatioResize = false;
                }
            }
        });

        mRenderOverlay = (RenderOverlay) parent.findViewById(R.id.render_overlay);
        mFlashOverlay = parent.findViewById(R.id.flash_overlay);
        mShutterButton = (ShutterButton) parent.findViewById(R.id.shutter_button);
        mFrontBackSwitcher = parent.findViewById(R.id.front_back_switcher);
        mSwitcher = (ModuleSwitcher) parent.findViewById(R.id.camera_switcher);
        mSwitcher.setCurrentIndex(ModuleSwitcher.PHOTO_MODULE_INDEX);
        mMenuButton = parent.findViewById(R.id.settings);

        RotateImageView muteButton = (RotateImageView) parent.findViewById(R.id.mute_button);
        muteButton.setVisibility(View.GONE);

        mBlurDegreeProgressBar = (SeekBar) getRootView().findViewById(R.id.blur_degree_bar);
        mBlurDegreeProgressBar.setMax(100);

        ViewStub faceViewStub = (ViewStub) getRootView()
                .findViewById(R.id.face_view_stub);
        if (faceViewStub != null) {
            faceViewStub.inflate();
            mFaceView = (FaceView) getRootView().findViewById(R.id.face_view);
            setSurfaceTextureSizeChangedListener(mFaceView);
        }
        initIndicators();
        mAnimationManager = new AnimationManager();
        mOrientationResize = false;
        mPrevOrientationResize = false;
    }

    public SeekBar getBokehDegreeBar() {
        return mBlurDegreeProgressBar;
    }

    public void setDownFactor(int factor) {
        mDownSampleFactor = factor;
    }

    public void cameraOrientationPreviewResize(boolean orientation) {
        mPrevOrientationResize = mOrientationResize;
        mOrientationResize = orientation;
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

        mPreviewWidth = width;
        mPreviewHeight = height;

        if (ratio != mAspectRatio) {
            mAspectRatioResize = true;
            mAspectRatio = ratio;
        }

        layoutPreview(ratio, width, height);
    }

    public void layoutPreview(float camAspectRatio, int width, int height) {
        FrameLayout.LayoutParams lp = getSurfaceSizeParams(camAspectRatio, width, height);

        if (mSurfaceTextureUncroppedWidth != lp.width ||
                mSurfaceTextureUncroppedHeight != lp.height) {
            mSurfaceTextureUncroppedWidth = lp.width;
            mSurfaceTextureUncroppedHeight = lp.height;
            if (mSurfaceTextureSizeListener != null) {
                mSurfaceTextureSizeListener.onSurfaceTextureSizeChanged(
                        mSurfaceTextureUncroppedWidth,
                        mSurfaceTextureUncroppedHeight);
            }
        }

        getSurfaceView().setLayoutParams(lp);
        getRootView().requestLayout();
        if (mFaceView != null) {
            mFaceView.setLayoutParams(lp);
        }
        mIsLayoutInitializedAlready = true;

        setSurfaceArea();
    }

    public void setSurfaceTextureSizeChangedListener(SurfaceTextureSizeChangedListener listener) {
        mSurfaceTextureSizeListener = listener;
    }

    // SurfaceHolder callbacks
    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        super.surfaceChanged(holder, format, width, height);
        RectF r = new RectF(getSurfaceView().getLeft(), getSurfaceView().getTop(),
                getSurfaceView().getRight(), getSurfaceView().getBottom());
        mController.onPreviewRectChanged(CameraUtil.rectFToRect(r));
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

    public void onCameraOpened(PreferenceGroup prefGroup, ComboPreferences prefs,
                               Camera.Parameters params, OnPreferenceChangedListener listener,
                               MakeupLevelListener makeupListener) {
        if (mPieRenderer == null) {
            mPieRenderer = new PieRenderer(getActivity());
            mPieRenderer.setPieListener(this);
            mRenderOverlay.addRenderer(mPieRenderer);
            setPieRenderer(mPieRenderer);
        }

        if (mMenu == null) {
            mMenu = new PhotoMenu(getActivity(), this, makeupListener,
                    CameraSettings.isBokehModeSupported(params));
            mMenu.setListener(listener);
        }
        mMenu.initialize(prefGroup);
        mMenuInitialized = true;

        if (mZoomRenderer == null) {
            mZoomRenderer = new ZoomRenderer(getActivity());
            mZoomRenderer.setCameraControlHeight(getControlHeight());
            mRenderOverlay.addRenderer(mZoomRenderer);
        }

        if (mGestures == null) {
            // this will handle gesture disambiguation and dispatching
            mGestures = new PreviewGestures(getActivity(), this,
                    mController.isImageCaptureIntent() ? null : this,
                    mZoomRenderer, mPieRenderer, null);
            mRenderOverlay.setGestures(mGestures);
            mGestures.setPhotoMenu(mMenu);
            mGestures.setZoomEnabled(params.isZoomSupported());
            mGestures.setRenderOverlay(mRenderOverlay);
        }

        initializeZoom(params);
        updateOnScreenIndicators(params, prefGroup, prefs);
        getActivity().setPreviewGestures(mGestures);
    }

    public void animateCapture(final byte[] jpegData) {
        // Decode jpeg byte array and then animate the jpeg
        getActivity().updateThumbnail(jpegData);
    }

    public void showRefocusToast(boolean show) {
        getCameraControls().showRefocusToast(show);
    }

    private void openMenu() {
        if (mPieRenderer != null) {
            // If autofocus is not finished, cancel autofocus so that the
            // subsequent touch can be handled by PreviewGestures
            if (mController.getCameraState() == PhotoController.FOCUSING) {
                mController.cancelAutoFocus();
            }
            mPieRenderer.showInCenter();
        }
    }

    public void initializeControlByIntent() {
        if (!getActivity().isSecureCamera() && !getActivity().isCaptureIntent()) {
            mThumbnail = (ImageView) getRootView().findViewById(R.id.preview_thumb);
            mThumbnail.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (mController.getCameraState() != PhotoController.SNAPSHOT_IN_PROGRESS) {
                        getActivity().gotoGallery();
                    }
                }
            });
        }
        mMenuButton = getRootView().findViewById(R.id.settings);
        mMenuButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mMenu != null) {
                    mMenu.openFirstLevel();
                }
            }
        });
        if (mController.isImageCaptureIntent()) {
            hideSwitcher();
            getCameraControls().hideRemainingPhotoCnt();
            getCameraControls().showCameraControlsSettings(false);
            getCameraControls().disableCameraControlsSettingsSwitch();

            mReviewDoneButton = getRootView().findViewById(R.id.done_button);
            mReviewCancelButton = getRootView().findViewById(R.id.btn_cancel);
            mReviewRetakeButton = getRootView().findViewById(R.id.btn_retake);
            mReviewImage = (ImageView) getRootView().findViewById(R.id.review_image);
            mReviewCancelButton.setVisibility(View.VISIBLE);

            mReviewDoneButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    mController.onCaptureDone();
                }
            });
            mReviewCancelButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    mController.onCaptureCancelled();
                }
            });

            mReviewRetakeButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    mController.onCaptureRetake();
                }
            });
        }
    }

    public void hideRemainingPhotoCnt() {
        getCameraControls().hideRemainingPhotoCnt();
    }

    public void showRemainingPhotoCnt() {
        getCameraControls().showRemainingPhotoCnt();
    }

    @Override
    public void showUI() {
        if (mMenu != null && mMenu.isMenuBeingShown()) {
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
        mSwitcher.setCurrentIndex(ModuleSwitcher.PHOTO_MODULE_INDEX);
    }

    // called from onResume but only the first time
    public void initializeFirstTime() {
        // Initialize shutter button.
        mShutterButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mController.isImageCaptureIntent()) {
                    getCameraControls().showCameraControlsSettings(true);
                }
            }
        });

        mShutterButton.setOnShutterButtonListener(mController);
        mShutterButton.setVisibility(View.VISIBLE);
    }

    // called from onResume every other time
    public void initializeSecondTime(Camera.Parameters params) {
        initializeZoom(params);
        if (mController.isImageCaptureIntent()) {
            hidePostCaptureAlert();
        }
        if (mMenu != null) {
            mMenu.reloadPreferences();
        }
        RotateImageView muteButton = (RotateImageView) getRootView().findViewById(
                R.id.mute_button);
        muteButton.setVisibility(View.GONE);
    }

    public void initializeZoom(Camera.Parameters params) {
        if ((params == null) || !params.isZoomSupported()
                || (mZoomRenderer == null)) return;
        mZoomMax = params.getMaxZoom();
        mZoomRatios = params.getZoomRatios();
        // Currently we use immediate zoom for fast zooming to get better UX and
        // there is no plan to take advantage of the smooth zoom.
        if (mZoomRenderer != null) {
            mZoomRenderer.setZoomMax(mZoomMax);
            mZoomRenderer.setZoom(params.getZoom());
            mZoomRenderer.setZoomValue(mZoomRatios.get(params.getZoom()));
            mZoomRenderer.setOnZoomChangeListener(new ZoomChangeListener());
        }
    }

    public void overrideSettings(final String... keyvalues) {
        if (mMenu == null)
            return;
        mMenu.overrideSettings(keyvalues);
    }

    public void updateOnScreenIndicators(Camera.Parameters params,
                                         PreferenceGroup group, ComboPreferences prefs) {
        if (params == null || group == null) return;
        mOnScreenIndicators.updateSceneOnScreenIndicator(params.getSceneMode());
        mOnScreenIndicators.updateExposureOnScreenIndicator(params,
                CameraSettings.readExposure(prefs));
        mOnScreenIndicators.updateFlashOnScreenIndicator(params.getFlashMode());
        int wbIndex = -1;
        String wb = Camera.Parameters.WHITE_BALANCE_AUTO;
        if (Camera.Parameters.SCENE_MODE_AUTO.equals(params.getSceneMode())) {
            wb = params.getWhiteBalance();
        }
        ListPreference pref = group.findPreference(CameraSettings.KEY_WHITE_BALANCE);
        if (pref != null) {
            wbIndex = pref.findIndexOfValue(wb);
        }
        // make sure the correct value was found
        // otherwise use auto index
        mOnScreenIndicators.updateWBIndicator(wbIndex < 0 ? 2 : wbIndex);
        boolean location = RecordLocationPreference.get(prefs, CameraSettings.KEY_RECORD_LOCATION);
        mOnScreenIndicators.updateLocationIndicator(location);
    }

    public void setCameraState(int state) {
    }

    public void animateFlash() {
        mAnimationManager.startFlashAnimation(mFlashOverlay);
    }

    public void enableGestures(boolean enable) {
        if (mGestures != null) {
            mGestures.setEnabled(enable);
        }
    }

    // forward from preview gestures to controller
    @Override
    public void onSingleTapUp(View view, int x, int y) {
        mController.onSingleTapUp(view, x, y);
    }

    public boolean onBackPressed() {
        if (mMenu != null && mMenu.handleBackKey()) {
            return true;
        }

        if (mPieRenderer != null && mPieRenderer.showsItems()) {
            mPieRenderer.hide();
            return true;
        }
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
        return false;
    }

    public void onPreviewFocusChanged(boolean previewFocused) {
        if (previewFocused) {
            showUI();
        } else {
            hideUI();
        }
        if (mFaceView != null) {
            mFaceView.setBlockDraw(!previewFocused);
        }
        if (mGestures != null) {
            mGestures.setEnabled(previewFocused);
        }
        if (mRenderOverlay != null) {
            // this can not happen in capture mode
            mRenderOverlay.setVisibility(previewFocused ? View.VISIBLE : View.GONE);
        }
        if (mPieRenderer != null) {
            mPieRenderer.setBlockFocus(!previewFocused);
        }
        setShowMenu(previewFocused);
        if (!previewFocused && mCountDownView != null) mCountDownView.cancelCountDown();
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

    public void setMakeupMenuLayout(LinearLayout layout) {
        mMakeupMenuLayout = layout;
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
                mMenu.animateSlideIn(mMenuLayout, CameraActivity.SETTING_LIST_WIDTH_1, true);
            if (level == 2)
                mMenu.animateFadeIn(popup);
        } else
            popup.setAlpha(0.85f);
    }

    public void removeLevel2() {
        if (mSubMenuLayout != null) {
            View v = mSubMenuLayout.getChildAt(0);
            mSubMenuLayout.removeView(v);
        }
    }

    public void showPopup(AbstractSettingPopup popup) {
        hideUI();

        if (mPopup == null) {
            mPopup = new PopupWindow(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            mPopup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            mPopup.setOutsideTouchable(true);
            mPopup.setFocusable(true);
            mPopup.setOnDismissListener(new PopupWindow.OnDismissListener() {
                @Override
                public void onDismiss() {
                    mPopup = null;
                    // mMenu.popupDismissed(mDismissAll);
                    mDismissAll = false;
                    showUI();
                }
            });
        }
        popup.setVisibility(View.VISIBLE);
        mPopup.setContentView(popup);
        mPopup.showAtLocation(getRootView(), Gravity.CENTER, 0, 0);
    }

    public void cleanupListview() {
        showUI();
    }

    public void dismissPopup() {
        if (mPopup != null && mPopup.isShowing()) {
            mPopup.dismiss();
        }
    }

    private boolean mDismissAll = false;

    public void dismissAllPopup() {
        mDismissAll = true;
        if (mPopup != null && mPopup.isShowing()) {
            mPopup.dismiss();
        }
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
        if (mPreviewMenuLayout != null) {
            return mPreviewMenuLayout.dispatchTouchEvent(ev);
        }
        return false;
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

    private void setShowMenu(boolean show) {
        if (mOnScreenIndicators != null) {
            mOnScreenIndicators.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    public boolean collapseCameraControls() {
        // Remove all the popups/dialog boxes
        boolean ret = false;
        if (mMenu != null) {
            mMenu.removeAllView();
        }
        if (mPopup != null) {
            dismissAllPopup();
            ret = true;
        }
        getCameraControls().showRefocusToast(false);
        return ret;
    }

    protected void showCapturedImageForReview(byte[] jpegData, int orientation, boolean mirror) {
        getCameraControls().collapseCameraControlsSettings(true);
        mDecodeTaskForReview = new DecodeImageForReview(jpegData, orientation, mirror);
        mDecodeTaskForReview.execute();
        mOnScreenIndicators.setVisibility(View.GONE);
        mMenuButton.setVisibility(View.GONE);
        CameraUtil.fadeIn(mReviewDoneButton);
        mShutterButton.setVisibility(View.INVISIBLE);
        if (mFrontBackSwitcher.getVisibility() == View.VISIBLE) {
            mFrontBackSwitcher.setVisibility(View.INVISIBLE);
            mFrontBackSwitcherVisible = true;
        }
        CameraUtil.fadeIn(mReviewRetakeButton);
        setOrientation(mOrientation, true);
        pauseFaceDetection();
    }

    protected void hidePostCaptureAlert() {
        getCameraControls().showCameraControlsSettings(true);
        if (mDecodeTaskForReview != null) {
            mDecodeTaskForReview.cancel(true);
        }
        mReviewImage.setVisibility(View.GONE);
        mOnScreenIndicators.setVisibility(View.VISIBLE);
        mMenuButton.setVisibility(View.VISIBLE);
        CameraUtil.fadeOut(mReviewDoneButton);
        mShutterButton.setVisibility(View.VISIBLE);
        if (mFrontBackSwitcherVisible) {
            mFrontBackSwitcher.setVisibility(View.VISIBLE);
            mFrontBackSwitcherVisible = false;
        }
        CameraUtil.fadeOut(mReviewRetakeButton);
        resumeFaceDetection();
    }

    public void setDisplayOrientation(int orientation) {
        if (mFaceView != null) {
            mFaceView.setDisplayOrientation(orientation);
        }
        if ((mPreviewOrientation == -1 || mPreviewOrientation != orientation)
                && mMenu != null && mMenu.isPreviewMenuBeingShown()) {
            dismissSceneModeMenu();
            mMenu.addModeBack();
        }
        mPreviewOrientation = orientation;
    }

    // shutter button handling

    public boolean isShutterPressed() {
        return mShutterButton.isPressed();
    }

    /**
     * Enables or disables the shutter button.
     */
    public void enableShutter(boolean enabled) {
        if (mShutterButton != null) {
            mShutterButton.setEnabled(enabled);
        }
    }

    public void pressShutterButton() {
        if (mShutterButton.isInTouchMode()) {
            mShutterButton.requestFocusFromTouch();
        } else {
            mShutterButton.requestFocus();
        }
        mShutterButton.setPressed(true);
    }

    public ZoomRenderer getZoomRenderer() {
        return mZoomRenderer;
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
                mPieRenderer.hide();
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
    public void onPieOpened(int centerX, int centerY) {
        setSwipingEnabled(false);
        if (mFaceView != null) {
            mFaceView.setBlockDraw(true);
        }
        if (mIsBokehMode && mBlurDegreeProgressBar != null) {
            mBlurDegreeProgressBar.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onPieClosed() {
        setSwipingEnabled(true);
        if (mFaceView != null) {
            mFaceView.setBlockDraw(false);
        }
        if (mBlurDegreeProgressBar != null) {
            mBlurDegreeProgressBar.setVisibility(View.GONE);
        }
    }

    @Override
    public void onPieMoved(int centerX, int centerY) {
        Size bokehCircle = mPieRenderer.getBokehFocusSize();
        int y;
        if (centerY > mPieRenderer.getHeight() / 2) {
            y = centerY - bokehCircle.getHeight() / 2 - mBlurDegreeProgressBar.getHeight();
        } else {
            y = centerY + bokehCircle.getHeight() / 2;
        }
        mBlurDegreeProgressBar.setX(centerX - mBlurDegreeProgressBar.getWidth() / 2);
        mBlurDegreeProgressBar.setY(y);
        if (mIsBokehMode && mBlurDegreeProgressBar.getVisibility() != View.VISIBLE
                && mPieRenderer.isVisible()) {
            mBlurDegreeProgressBar.setVisibility(View.VISIBLE);
        }
    }

    public void enableBokehRender(boolean enable) {
        if (mPieRenderer != null) {
            mPieRenderer.setBokehMode(enable);
            mIsBokehMode = enable;
        }
    }

    public void enableBokehFocus(boolean enable) {
        if (mPieRenderer != null && mIsBokehMode) {
            mPieRenderer.setBokehMode(enable);
            if (mBlurDegreeProgressBar != null) {
                if (enable && mPieRenderer.isVisible()) {
                    mBlurDegreeProgressBar.setVisibility(View.VISIBLE);
                } else {
                    mBlurDegreeProgressBar.setVisibility(View.GONE);
                }
            }
        }
    }

    public void setBokehRenderDegree(int degree) {
        if (mPieRenderer != null) {
            mPieRenderer.setBokehDegree(degree);
        }
    }

    public void setSwipingEnabled(boolean enable) {
        getActivity().setSwipingEnabled(enable);
    }

    // Countdown timer
    private void initializeCountDown() {
        getActivity().getLayoutInflater().inflate(R.layout.count_down_to_capture,
                (ViewGroup) getRootView(), true);
        mCountDownView = (CountDownView) (getRootView().findViewById(R.id.count_down_to_capture));
        mCountDownView.setCountDownFinishedListener((OnCountDownFinishedListener) mController);
        mCountDownView.bringToFront();
        mCountDownView.setOrientation(mOrientation);
    }

    public boolean isCountingDown() {
        return mCountDownView != null && mCountDownView.isCountingDown();
    }

    public void cancelCountDown() {
        if (mCountDownView == null) return;
        mCountDownView.cancelCountDown();
        showUIAfterCountDown();
    }

    public void startCountDown(int sec, boolean playSound) {
        if (mCountDownView == null) initializeCountDown();
        mCountDownView.startCountDown(sec, playSound);
        hideUIWhileCountDown();
    }

    public void startSelfieFlash() {
        if (mSelfieView == null) {
            mSelfieView = (SelfieFlashView) getRootView().findViewById(R.id.selfie_flash);
        }
        mSelfieView.bringToFront();
        mSelfieView.open();
        mScreenBrightness = setScreenBrightness(1F);
    }

    public void stopSelfieFlash() {
        if (mSelfieView == null) {
            mSelfieView = (SelfieFlashView) getRootView().findViewById(R.id.selfie_flash);
        }
        mSelfieView.close();
        if (mScreenBrightness != 0.0f) {
            setScreenBrightness(mScreenBrightness);
        }
    }

    private float setScreenBrightness(float brightness) {
        float originalBrightness;
        Window window = getActivity().getWindow();
        WindowManager.LayoutParams layout = window.getAttributes();
        originalBrightness = layout.screenBrightness;
        layout.screenBrightness = brightness;
        window.setAttributes(layout);
        return originalBrightness;
    }

    public void showPreferencesToast() {
        if (mNotSelectableToast == null) {
            String str = getActivity().getResources().getString(R.string.not_selectable_in_scene_mode);
            mNotSelectableToast = RotateTextToast.makeText(getActivity(), str, Toast.LENGTH_SHORT);
        }
        mNotSelectableToast.show();
    }

    public void showPreviewCover() {
        mPreviewCover.setVisibility(View.VISIBLE);
    }

    public void hidePreviewCover() {
        // Hide the preview cover if need.
        if (mPreviewCover.getVisibility() != View.GONE) {
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

    public void onPause() {
        cancelCountDown();

        // Clear UI.
        collapseCameraControls();
        if (mFaceView != null) mFaceView.clear();

        if (mLocationDialog != null && mLocationDialog.isShowing()) {
            mLocationDialog.dismiss();
        }
        mLocationDialog = null;
        if (mMenu != null) {
            mMenu.animateSlideOutPreviewMenu();
        }
    }

    // focus UI implementation
    private FocusIndicator getFocusIndicator() {
        return (mFaceView != null && mFaceView.faceExists()) ? mFaceView : mPieRenderer;
    }

    @Override
    public boolean hasFaces() {
        return (mFaceView != null && mFaceView.faceExists());
    }

    public void clearFaces() {
        if (mFaceView != null) mFaceView.clear();
    }

    @Override
    public void clearFocus() {
        FocusIndicator indicator = mPieRenderer;
        if (hasFaces()) {
            mHandler.post(() -> {
                mFaceView.showStart();
            });
        }
        if (indicator != null) indicator.clear();
    }

    @Override
    public void setFocusPosition(int x, int y) {
        mPieRenderer.setFocus(x, y);
    }

    @Override
    public void onFocusStarted() {
        FocusIndicator indicator = getFocusIndicator();
        if (indicator != null) indicator.showStart();
    }

    @Override
    public void onFocusSucceeded(boolean timeout) {
        FocusIndicator indicator = getFocusIndicator();
        if (indicator != null) indicator.showSuccess(timeout);
    }

    @Override
    public void onFocusFailed(boolean timeout) {
        FocusIndicator indicator = getFocusIndicator();
        if (indicator != null) indicator.showFail(timeout);
    }

    @Override
    public void pauseFaceDetection() {
        if (mFaceView != null) mFaceView.pause();
    }

    @Override
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

    @Override
    public void onFaceDetection(Face[] faces, CameraManager.CameraProxy camera) {
        if (mIsBokehMode) {
            mFaceView.clear();
            return;
        }
        mFaceView.setFaces(faces);
    }

    @Override
    public void onDisplayChanged() {
        super.onDisplayChanged();
        mController.updateCameraOrientation();
    }

    public void setPreference(String key, String value) {
        mMenu.setPreference(key, value);
    }

    public void updateRemainingPhotos(int remaining) {
        getCameraControls().updateRemainingPhotos(remaining);
    }

    public void setOrientation(int orientation, boolean animation) {
        mOrientation = orientation;
        getCameraControls().setOrientation(orientation, animation);
        if (mMenuLayout != null)
            mMenuLayout.setOrientation(orientation, animation);
        if (mSubMenuLayout != null)
            mSubMenuLayout.setOrientation(orientation, animation);
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
        if (mMakeupMenuLayout != null) {
            View view = mMakeupMenuLayout.getChildAt(0);
            if (view instanceof RotateLayout) {
                for (int i = mMakeupMenuLayout.getChildCount() - 1; i >= 0; --i) {
                    RotateLayout l = (RotateLayout) mMakeupMenuLayout.getChildAt(i);
                    l.setOrientation(orientation, animation);
                }
            } else {
                ViewGroup vg = (ViewGroup) mMakeupMenuLayout.getChildAt(1);
                if (vg != null) {
                    for (int i = vg.getChildCount() - 1; i >= 0; --i) {
                        ViewGroup vewiGroup = (ViewGroup) vg.getChildAt(i);
                        if (vewiGroup instanceof RotateLayout) {
                            RotateLayout l = (RotateLayout) vewiGroup;
                            l.setOrientation(orientation, animation);
                        }
                    }
                }
            }

        }
        if (mCountDownView != null)
            mCountDownView.setOrientation(orientation);
        RotateTextToast.setOrientation(orientation);
        if (mFaceView != null) {
            mFaceView.setDisplayRotation(orientation);
        }
        if (mZoomRenderer != null) {
            mZoomRenderer.setOrientation(orientation);
        }
        if (mReviewImage != null) {
            RotateImageView v = (RotateImageView) mReviewImage;
            v.setOrientation(orientation, animation);
        }
    }

    public void tryToCloseSubList() {
        if (mMenu != null)
            mMenu.tryToCloseSubList();
    }

    public int getOrientation() {
        return mOrientation;
    }

    public void adjustOrientation() {
        setOrientation(mOrientation, true);
    }

    public void showRefocusDialog() {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        int prompt = prefs.getInt(CameraSettings.KEY_REFOCUS_PROMPT, 1);
        if (prompt == 1) {
            AlertDialog dialog = new AlertDialog.Builder(getActivity())
                    .setTitle(R.string.refocus_prompt_title)
                    .setMessage(R.string.refocus_prompt_message)
                    .setPositiveButton(R.string.dialog_ok, null)
                    .show();
            SharedPreferences.Editor editor = prefs.edit();
            editor.putInt(CameraSettings.KEY_REFOCUS_PROMPT, 0);
            editor.apply();
        }
    }

    public void hideUIWhileCountDown() {
        getCameraControls().collapseCameraControlsSettings(true);
        mGestures.setZoomOnly(true);
    }

    public void showUIAfterCountDown() {
        getCameraControls().showCameraControlsSettings(true);
        mGestures.setZoomOnly(false);
    }
}
