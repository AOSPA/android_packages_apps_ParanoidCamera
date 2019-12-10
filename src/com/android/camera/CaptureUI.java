/*
 * Copyright (c) 2016-2017 The Linux Foundation. All rights reserved.
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

import android.animation.Animator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.AnimationDrawable;
import android.hardware.Camera.Face;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicYuvToRGB;
import android.renderscript.Type;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Size;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewPropertyAnimator;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.android.camera.imageprocessor.filter.BeautificationFilter;
import com.android.camera.data.Camera2ModeAdapter;
import com.android.camera.imageprocessor.filter.DeepPortraitFilter;
import com.android.camera.ui.AutoFitSurfaceView;
import com.android.camera.ui.Camera2FaceView;
import com.android.camera.ui.CameraControls;
import com.android.camera.ui.MenuHelp;
import com.android.camera.ui.OneUICameraControls;
import com.android.camera.ui.CountDownView;
import com.android.camera.ui.FlashToggleButton;
import com.android.camera.ui.FocusIndicator;
import com.android.camera.ui.PieRenderer;
import com.android.camera.ui.RenderOverlay;
import com.android.camera.ui.RotateImageView;
import com.android.camera.ui.RotateLayout;
import com.android.camera.ui.RotateTextToast;
import com.android.camera.ui.SelfieFlashView;
import com.android.camera.ui.TrackingFocusRenderer;
import com.android.camera.ui.ZoomRenderer;
import com.android.camera.ui.TouchTrackFocusRenderer;
import com.android.camera.util.CameraUtil;
import com.android.camera.deepportrait.GLCameraPreview;
import com.android.camera.util.PersistUtil;

import org.codeaurora.snapcam.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CaptureUI implements FocusOverlayManager.FocusUI,
        PreviewGestures.SingleTapListener,
        CameraManager.CameraFaceDetectionCallback,
        SettingsManager.Listener,
        PauseButton.OnPauseButtonListener {
    private static final int HIGHLIGHT_COLOR = 0xff33b5e5;
    private static final String TAG = "SnapCam_CaptureUI";
    private static final boolean DEV_LEVEL_ALL =
            PersistUtil.getDevOptionLevel() == PersistUtil.CAMERA2_DEV_OPTION_ALL;
    private static final int FILTER_MENU_NONE = 0;
    private static final int FILTER_MENU_IN_ANIMATION = 1;
    private static final int FILTER_MENU_ON = 2;
    private static final int ANIMATION_DURATION = 300;
    private static final int CLICK_THRESHOLD = 200;
    private static final int AUTOMATIC_MODE = 0;
    private static final String[] AWB_INFO_TITLE = {" R gain "," G gain "," B gain "," CCT "};
    private static final String[] AEC_INFO_TITLE = {" Lux "," Gain "," Sensitivity "," Exp Time "};
    private CameraActivity mActivity;
    private View mRootView;
    private View mPreviewCover;
    private CaptureModule mModule;
    private AutoFitSurfaceView mSurfaceView;
    private AutoFitSurfaceView mSurfaceViewMono;
    private SurfaceHolder mSurfaceHolder;
    private SurfaceHolder mSurfaceHolderMono;
    private GLCameraPreview mGLSurfaceView = null;
    private int mOrientation;
    private int mFilterMenuStatus;
    private PreviewGestures mGestures;
    private boolean mUIhidden = false;
    private SettingsManager mSettingsManager;
    private TrackingFocusRenderer mTrackingFocusRenderer;
    private TouchTrackFocusRenderer mT2TFocusRenderer;
    private ImageView mThumbnail;
    private Camera2FaceView mFaceView;
    private Point mDisplaySize = new Point();
    private SelfieFlashView mSelfieView;
    private float mScreenBrightness = 0.0f;
    private ProgressBar mProgressBar;

    private SurfaceHolder.Callback callbackMono = new SurfaceHolder.Callback() {
        // SurfaceHolder callbacks
        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            mSurfaceHolderMono = holder;
            if(mMonoDummyOutputAllocation != null) {
                mMonoDummyOutputAllocation.setSurface(mSurfaceHolderMono.getSurface());
            }
        }
        @Override
        public void surfaceCreated(SurfaceHolder holder) {
        }
        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
        }
    };

    private SurfaceHolder.Callback callback = new SurfaceHolder.Callback() {

        // SurfaceHolder callbacks
        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            Log.v(TAG, "surfaceChanged: width =" + width + ", height = " + height);
        }

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            Log.v(TAG, "surfaceCreated");
            mSurfaceHolder = holder;
            previewUIReady();
            if(mTrackingFocusRenderer != null && mTrackingFocusRenderer.isVisible()) {
                mTrackingFocusRenderer.setSurfaceDim(mSurfaceView.getLeft(), mSurfaceView.getTop(), mSurfaceView.getRight(), mSurfaceView.getBottom());
            }
            if(mT2TFocusRenderer != null && mT2TFocusRenderer.isShown()) {
                mT2TFocusRenderer.setSurfaceDim(mSurfaceView.getLeft(), mSurfaceView.getTop(),
                        mSurfaceView.getRight(), mSurfaceView.getBottom());
            }
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            Log.v(TAG, "surfaceDestroyed");
            mSurfaceHolder = null;
            if (mDeepZoomModeRect != null) {
                mDeepZoomModeRect.setVisibility(View.GONE);
            }
            previewUIDestroyed();
        }
    };

    private ShutterButton mShutterButton;
    private ImageView mVideoButton;
    private RenderOverlay mRenderOverlay;
    private FlashToggleButton mFlashButton;
    private CountDownView mCountDownView;
    private OneUICameraControls mCameraControls;
    private MenuHelp mMenuHelp;
    private PieRenderer mPieRenderer;
    private ZoomRenderer mZoomRenderer;
    private Allocation mMonoDummyAllocation;
    private Allocation mMonoDummyOutputAllocation;
    private boolean mIsMonoDummyAllocationEverUsed = false;
    private boolean mIsTouchAF = false;

    private int mScreenRatio = CameraUtil.RATIO_UNKNOWN;
    private int mTopMargin = 0;
    private int mBottomMargin = 0;
    private ViewGroup mFilterLayout;

    private View mFilterModeSwitcher;
    private View mSceneModeSwitcher;
    private View mFrontBackSwitcher;
    private ImageView mMakeupButton;
    private SeekBar mMakeupSeekBar;
    private SeekBar mDeepportraitSeekBar;
    private SeekBar mZoomSeekBar;
    private View mMakeupSeekBarLayout;
    private View mSeekbarBody;
    private TextView mRecordingTimeView;
    private View mTimeLapseLabel;
    private RotateLayout mRecordingTimeRect;
    private PauseButton mPauseButton;
    private RotateImageView mMuteButton;
    private ImageView mSeekbarToggleButton;
    private RotateLayout mSceneModeLabelRect;
    private LinearLayout mSceneModeLabelView;
    private TextView mSceneModeName;
    private ImageView mExitBestMode;
    private RotateLayout mDeepZoomModeRect;
    private TextView mDeepzoomSetName;
    private int mDeepZoomIndex = 0;
    private float mDeepZoomValue = 1.0f;
    private ImageView mSettingsIcon;
    private ArrayList<TextView> mCameraModeTexts = new ArrayList<>();
    private RecyclerView mModeSelectLayout;
    private Camera2ModeAdapter mCameraModeAdapter;

    private ImageView mSceneModeLabelCloseIcon;
    private AlertDialog  mSceneModeInstructionalDialog = null;

    private ImageView mCancelButton;
    private View mReviewCancelButton;
    private View mReviewDoneButton;
    private View mReviewRetakeButton;
    private View mReviewPlayButton;
    private FrameLayout mPreviewLayout;
    private ImageView mReviewImage;
    private int mDownSampleFactor = 4;
    private DecodeImageForReview mDecodeTaskForReview = null;

    private View mStatsAwbInfo;
    private TextView mStatsAwbRText;
    private TextView mStatsAwbGText;
    private TextView mStatsAwbBText;
    private TextView mStatsAwbCcText;
    private TextView mZoomValueText;

    private View mStatsAecInfo;
    private TextView mStatsAecLuxText;
    private TextView mStatsAeclinearGainText;
    private TextView mStatsAecSensitivityText;
    private TextView mStatsAecExposureTimeText;

    private LinearLayout mZoomLinearLayout;

    private TextView mZoomSwitch;
    private int mZoomIndex = 0;

    int mPreviewWidth;
    int mPreviewHeight;
    private boolean mIsVideoUI = false;
    private boolean mIsSceneModeLabelClose = false;

    private void previewUIReady() {
        if((mSurfaceHolder != null && mSurfaceHolder.getSurface().isValid())) {
            mModule.onPreviewUIReady();
            if ((mIsVideoUI || mModule.getCurrentIntentMode() != CaptureModule.INTENT_MODE_NORMAL)
                    && mThumbnail != null){
                mThumbnail.setVisibility(View.INVISIBLE);
                mThumbnail = null;
                mActivity.updateThumbnail(mThumbnail);
            } else if (!mIsVideoUI &&
                    mModule.getCurrentIntentMode() == CaptureModule.INTENT_MODE_NORMAL){
                if (mThumbnail == null)
                    mThumbnail = (ImageView) mRootView.findViewById(R.id.preview_thumb);
                mActivity.updateThumbnail(mThumbnail);
            }
        }
    }

    public void initThumbnail() {
        if (mThumbnail == null)
            mThumbnail = (ImageView) mRootView.findViewById(R.id.preview_thumb);
        mActivity.updateThumbnail(mThumbnail);
    }

    private void previewUIDestroyed() {
        mModule.onPreviewUIDestroyed();
    }

    public TrackingFocusRenderer getTrackingFocusRenderer() {
        return mTrackingFocusRenderer;
    }

    public TouchTrackFocusRenderer getT2TFocusRenderer() {
        return mT2TFocusRenderer;
    }

    public void updateT2TCameraBound(Rect cameraBound) {
        mT2TFocusRenderer.setZoom(mModule.getZoomValue());
    }

    public Point getDisplaySize() {
        return mDisplaySize;
    }

    public CaptureUI(CameraActivity activity, final CaptureModule module, View parent) {
        mActivity = activity;
        mModule = module;
        mRootView = parent;
        mSettingsManager = SettingsManager.getInstance();
        mSettingsManager.registerListener(this);
        mActivity.getLayoutInflater().inflate(R.layout.capture_module,
                (ViewGroup) mRootView, true);
        mPreviewCover = mRootView.findViewById(R.id.preview_cover);
        // display the view
        mSurfaceView = (AutoFitSurfaceView) mRootView.findViewById(R.id.mdp_preview_content);
        mSurfaceHolder = mSurfaceView.getHolder();
        mSurfaceHolder.addCallback(callback);
        mSurfaceView.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right,
                                       int bottom, int oldLeft, int oldTop, int oldRight,
                                       int oldBottom) {
                int width = right - left;
                int height = bottom - top;
                if (mFaceView != null) {
                    mFaceView.onSurfaceTextureSizeChanged(width, height);
                }
                if (mT2TFocusRenderer != null) {
                    mT2TFocusRenderer.onSurfaceTextureSizeChanged(width, height);
                }
            }
        });

        mSurfaceViewMono = (AutoFitSurfaceView) mRootView.findViewById(R.id.mdp_preview_content_mono);
        mSurfaceViewMono.setZOrderMediaOverlay(true);
        mSurfaceHolderMono = mSurfaceViewMono.getHolder();
        mSurfaceHolderMono.addCallback(callbackMono);

        mProgressBar = (ProgressBar) mRootView.findViewById(R.id.progress_bar);
        mRenderOverlay = (RenderOverlay) mRootView.findViewById(R.id.render_overlay);
        mShutterButton = (ShutterButton) mRootView.findViewById(R.id.shutter_button);
        mVideoButton = (ImageView) mRootView.findViewById(R.id.video_button);
        mExitBestMode = (ImageView) mRootView.findViewById(R.id.exit_best_mode);
        mFilterModeSwitcher = mRootView.findViewById(R.id.filter_mode_switcher);
        mSceneModeSwitcher = mRootView.findViewById(R.id.scene_mode_switcher);
        mFrontBackSwitcher = mRootView.findViewById(R.id.front_back_switcher);
        mMakeupButton = (ImageView) mRootView.findViewById(R.id.ts_makeup_switcher);
        mMakeupSeekBarLayout = mRootView.findViewById(R.id.makeup_seekbar_layout);
        mSeekbarBody = mRootView.findViewById(R.id.seekbar_body);
        mSeekbarToggleButton = (ImageView) mRootView.findViewById(R.id.seekbar_toggle);
        mSeekbarToggleButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(mSeekbarBody.getVisibility() == View.VISIBLE) {
                    mSeekbarBody.setVisibility(View.GONE);
                    mSeekbarToggleButton.setImageResource(R.drawable.seekbar_show);
                } else {
                    mSeekbarBody.setVisibility(View.VISIBLE);
                    mSeekbarToggleButton.setImageResource(R.drawable.seekbar_hide);
                }
            }
        });
        mMakeupSeekBar = (SeekBar)mRootView.findViewById(R.id.makeup_seekbar);
        mMakeupSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progresValue, boolean fromUser) {
                if ( progresValue != 0 ) {
                    int value = 10 + 9 * progresValue / 10;
                    mSettingsManager.setValue(SettingsManager.KEY_MAKEUP, value + "");
                }
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        mDeepportraitSeekBar = (SeekBar)mRootView.findViewById(R.id.deepportrait_seekbar);
        mDeepportraitSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                 if (mModule.getCamGLRender() != null) {
                     module.getCamGLRender().setBlurLevel(progress);
                 }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                final SharedPreferences prefs =
                        PreferenceManager.getDefaultSharedPreferences(mActivity);
                SharedPreferences.Editor editor = prefs.edit();
                editor.putInt(SettingsManager.KEY_DEEPPORTRAIT_VALUE, seekBar.getProgress());
                editor.commit();
            }
        });
        mMakeupButton.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                if (module != null && !module.isAllSessionClosed()) {
                    toggleMakeup();
                    updateMenus();
                }
            }
        });
        setMakeupButtonIcon();
        initZoomSeekBar();

        mFlashButton = (FlashToggleButton) mRootView.findViewById(R.id.flash_button);
        mModeSelectLayout = (RecyclerView) mRootView.findViewById(R.id.mode_select_layout);
        mModeSelectLayout.setLayoutManager(new LinearLayoutManager(mActivity,
                LinearLayoutManager.HORIZONTAL, false));
        mCameraModeAdapter = new Camera2ModeAdapter(mModule.getCameraModeList());
        mCameraModeAdapter.setOnItemClickListener(mModule.getModeItemClickListener());
        mModeSelectLayout.setAdapter(mCameraModeAdapter);
        mSettingsIcon = (ImageView) mRootView.findViewById(R.id.settings);
        mSettingsIcon.setImageResource(R.drawable.settings);
        mSettingsIcon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openSettingsMenu();
            }
        });

        initFilterModeButton();
        initSceneModeButton();
        initCameraSwitcher();
        initFlashButton();
        updateMenus();

        mRecordingTimeView = (TextView) mRootView.findViewById(R.id.recording_time);
        mRecordingTimeRect = (RotateLayout) mRootView.findViewById(R.id.recording_time_rect);
        mTimeLapseLabel = mRootView.findViewById(R.id.time_lapse_label);
        mPauseButton = (PauseButton) mRootView.findViewById(R.id.video_pause);
        mPauseButton.setOnPauseButtonListener(this);

        mStatsAwbInfo = mRootView.findViewById(R.id.stats_awb_info);
        mStatsAwbRText = mRootView.findViewById(R.id.stats_awb_r_text);
        mStatsAwbGText = mRootView.findViewById(R.id.stats_awb_g_text);
        mStatsAwbBText = mRootView.findViewById(R.id.stats_awb_b_text);
        mStatsAwbCcText = mRootView.findViewById(R.id.stats_awb_cc_text);

        mStatsAecInfo = mRootView.findViewById(R.id.stats_aec_info);
        mStatsAecLuxText= mRootView.findViewById(R.id.stats_aec_lux_text);
        mStatsAeclinearGainText= mRootView.findViewById(R.id.stats_aec_linear_gain_text);
        mStatsAecSensitivityText= mRootView.findViewById(R.id.stats_aec_sensitivity_text);
        mStatsAecExposureTimeText= mRootView.findViewById(R.id.stats_aec_exp_time_text);

        mMuteButton = (RotateImageView)mRootView.findViewById(R.id.mute_button);
        mMuteButton.setVisibility(View.VISIBLE);
        setMuteButtonResource(!mModule.isAudioMute());
        mMuteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean isEnabled = !mModule.isAudioMute();
                mModule.setMute(isEnabled, true);
                setMuteButtonResource(!isEnabled);
            }
        });

        mExitBestMode.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SettingsManager.getInstance().setValueIndex(SettingsManager.KEY_SCENE_MODE,
                        AUTOMATIC_MODE);
            }
        });

        RotateImageView muteButton = (RotateImageView) mRootView.findViewById(R.id.mute_button);
        muteButton.setVisibility(View.GONE);

        mDeepZoomModeRect = (RotateLayout)mRootView.findViewById(R.id.deepzoom_set_layout);
        mDeepzoomSetName = (TextView)mRootView.findViewById(R.id.deepzoom_set);
        mDeepzoomSetName.setText("Zoom OFF");
        mDeepzoomSetName.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mDeepZoomIndex = (mDeepZoomIndex + 1) % 3;
                updateDeepZoomIndex();
            }
        });
        mSceneModeLabelRect = (RotateLayout)mRootView.findViewById(R.id.scene_mode_label_rect);
        mSceneModeName = (TextView)mRootView.findViewById(R.id.scene_mode_label);
        mSceneModeLabelCloseIcon = (ImageView)mRootView.findViewById(R.id.scene_mode_label_close);
        mSceneModeLabelCloseIcon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mIsSceneModeLabelClose = true;
                mSceneModeLabelRect.setVisibility(View.GONE);
            }
        });

        mCameraControls = (OneUICameraControls) mRootView.findViewById(R.id.camera_controls);
        mFaceView = (Camera2FaceView) mRootView.findViewById(R.id.face_view);
        mFaceView.initMode();

        //Touch track focus
        mT2TFocusRenderer = (TouchTrackFocusRenderer) mRootView.findViewById(R.id.touch_track_focus);
        mT2TFocusRenderer.init(mActivity, mModule, this);
        if (mModule.isT2TFocusSettingOn()) {
            mT2TFocusRenderer.setVisible(true);
        } else {
            mT2TFocusRenderer.setVisible(false);
        }

        mZoomSwitch = (TextView)mRootView.findViewById(R.id.zoom_switch);

        mZoomSwitch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String[] entries = mActivity.getResources().getStringArray(
                        R.array.pref_camera2_zomm_switch_entries);
                String[] values = mActivity.getResources().getStringArray(
                        R.array.pref_camera2_zomm_switch_entryvalues);
                mZoomIndex = mZoomIndex + 1;
                if (mZoomIndex > values.length -1)
                    mZoomIndex = 0;
                mZoomSwitch.setText(entries[mZoomIndex]);
                mModule.onZoomChanged(Float.valueOf(values[mZoomIndex]));
                if (mZoomRenderer != null) {
                    mZoomRenderer.setZoom(Float.valueOf(values[mZoomIndex]));
                }
            }
        });

        mCancelButton = (ImageView) mRootView.findViewById(R.id.cancel_button);
        final int intentMode = mModule.getCurrentIntentMode();
        if (intentMode != CaptureModule.INTENT_MODE_NORMAL) {
            mModeSelectLayout.setVisibility(View.GONE);
            mCameraControls.setIntentMode(intentMode);
            mCameraControls.setVideoMode(false);
            mCancelButton.setVisibility(View.VISIBLE);
            mReviewCancelButton = mRootView.findViewById(R.id.preview_btn_cancel);
            mReviewDoneButton = mRootView.findViewById(R.id.done_button);
            mReviewRetakeButton = mRootView.findViewById(R.id.preview_btn_retake);
            mReviewPlayButton = mRootView.findViewById(R.id.preview_play);
            mPreviewLayout = (FrameLayout)mRootView.findViewById(R.id.preview_of_intent);
            mReviewImage = (ImageView)mRootView.findViewById(R.id.preview_content);
            mReviewCancelButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    mActivity.setResultEx(Activity.RESULT_CANCELED, new Intent());
                    mActivity.finish();
                }
            });
            mReviewRetakeButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    mPreviewLayout.setVisibility(View.GONE);
                    mReviewImage.setImageBitmap(null);
                    mModule.setJpegImageData(null);
                    if (intentMode == CaptureModule.INTENT_MODE_VIDEO) {
                        mModule.onRetakeVideo();
                    }
                }
            });
            mReviewDoneButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (intentMode == CaptureModule.INTENT_MODE_CAPTURE) {
                        mModule.onCaptureDone();
                    } else if (intentMode == CaptureModule.INTENT_MODE_VIDEO) {
                        mModule.onRecordingDone(true);
                    }
                }
            });
            mReviewPlayButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    mModule.startPlayVideoActivity();
                }
            });
            mCancelButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    mModule.cancelCapture();
                }
            });
        }

        mActivity.getWindowManager().getDefaultDisplay().getSize(mDisplaySize);
        mScreenRatio = CameraUtil.determineRatio(mDisplaySize.x, mDisplaySize.y);
        if (mScreenRatio == CameraUtil.RATIO_16_9) {
            int l = mDisplaySize.x > mDisplaySize.y ? mDisplaySize.x : mDisplaySize.y;
            int tm = mActivity.getResources().getDimensionPixelSize(R.dimen.preview_top_margin);
            int bm = mActivity.getResources().getDimensionPixelSize(R.dimen.preview_bottom_margin);
            mTopMargin = l / 4 * tm / (tm + bm);
            mBottomMargin = l / 4 - mTopMargin;
        }

        if (mPieRenderer == null) {
            mPieRenderer = new PieRenderer(mActivity);
            mRenderOverlay.addRenderer(mPieRenderer);
        }

        if (mZoomRenderer == null) {
            mZoomRenderer = new ZoomRenderer(mActivity);
            mRenderOverlay.addRenderer(mZoomRenderer);
        }

        if(mTrackingFocusRenderer == null) {
            mTrackingFocusRenderer = new TrackingFocusRenderer(mActivity, mModule, this);
            mRenderOverlay.addRenderer(mTrackingFocusRenderer);
        }
        if(mModule.isTrackingFocusSettingOn()) {
            mTrackingFocusRenderer.setVisible(true);
        } else {
            mTrackingFocusRenderer.setVisible(false);
        }

        if (mGestures == null) {
            // this will handle gesture disambiguation and dispatching
            mGestures = new PreviewGestures(mActivity, this, mZoomRenderer, mPieRenderer,
                    mTrackingFocusRenderer);
            mRenderOverlay.setGestures(mGestures);
        }

        mGestures.setRenderOverlay(mRenderOverlay);
        mRenderOverlay.requestLayout();

        mActivity.setPreviewGestures(mGestures);
        mRecordingTimeRect.setVisibility(View.GONE);
        showFirstTimeHelp();
    }

    private void initZoomSeekBar() {
        mZoomLinearLayout = (LinearLayout) mRootView.findViewById(R.id.zoom_linearlayout);
        mZoomValueText = (TextView) mRootView.findViewById(R.id.zoom_value_text);
        mZoomSeekBar = (SeekBar) mRootView.findViewById(R.id.zoom_seekbar);
        Float zoomMax = mSettingsManager.getMaxZoom(mModule.getMainCameraId());
        mZoomSeekBar.setMax(zoomMax.intValue() * 10 - 10);
        updateZoomSeekBar(1.0f);
        mZoomLinearLayout.setVisibility(View.VISIBLE);
        mZoomSeekBar.setVisibility(View.VISIBLE);
        mZoomSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float zoomValue = (progress + 10) / 10f;
                mModule.updateZoomChanged(zoomValue);
                if (mZoomRenderer != null) {
                    mZoomRenderer.setZoom(zoomValue);
                }
                int zoomSig = Math.round((progress + 10)) / 10;
                int zoomFraction = Math.round(progress + 10) % 10;
                String txt = zoomSig + "." + zoomFraction + "x";
                if (mZoomValueText != null) {
                    mZoomValueText.setText(txt);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                float zoomValue = (seekBar.getProgress() + 10) / 10f;
                mModule.updateZoomChanged(zoomValue);
                if (mZoomRenderer != null) {
                    mZoomRenderer.setZoom(zoomValue);
                }
                if (mZoomValueText != null) {
                    mZoomValueText.setText(zoomValue + "x");
                }
            }
        });
    }

    public void hideZoomSeekBar() {
        if (mZoomLinearLayout != null) {
            mZoomLinearLayout.setVisibility(View.GONE);
        }
        if (mZoomValueText != null) {
            mZoomValueText.setVisibility(View.GONE);
        }
        if (mZoomSeekBar != null) {
            mZoomSeekBar.setVisibility(View.GONE);
        }
        if (mZoomSwitch != null) {
            mZoomSwitch.setVisibility(View.GONE);
        }
    }

    public void showZoomSeekBar() {
        if(mFilterMenuStatus == FILTER_MENU_ON){
            return;
        }
        if (mZoomLinearLayout != null) {
            mZoomLinearLayout.setVisibility(View.VISIBLE);
        }
        if (mZoomValueText != null) {
            mZoomValueText.setVisibility(View.VISIBLE);
        }
        if (mZoomSeekBar != null) {
            mZoomSeekBar.setVisibility(View.VISIBLE);
        }
        if (mZoomSwitch != null) {
            mZoomSwitch.setVisibility(View.VISIBLE);
        }
    }

    public void updateZoomSeekBar(float zoomValue) {
        int zoomSig = Math.round(zoomValue * 10) / 10;
        int zoomFraction = Math.round(zoomValue * 10) % 10;
        String txt = zoomSig + "." + zoomFraction + "x";
        if (mZoomValueText != null) {
            mZoomValueText.setText(txt);
        }
        if (mZoomSeekBar != null) {
            mZoomSeekBar.setProgress(zoomSig * 10 + zoomFraction - 10);
        }
    }

    private void selectModeText(View view) {
        for (TextView textView : mCameraModeTexts) {
            textView.setSelected(textView == view);
        }
    }

    protected void showCapturedImageForReview(byte[] jpegData, int orientation) {
        mDecodeTaskForReview = new CaptureUI.DecodeImageForReview(jpegData, orientation);
        mDecodeTaskForReview.execute();
        if (getCurrentIntentMode() != CaptureModule.INTENT_MODE_NORMAL) {
            if (mFilterMenuStatus == FILTER_MENU_ON) {
                removeFilterMenu(false);
            }
            mPreviewLayout.setVisibility(View.VISIBLE);
            CameraUtil.fadeIn(mReviewDoneButton);
            CameraUtil.fadeIn(mReviewRetakeButton);
        }
    }

    protected void showRecordVideoForReview(Bitmap preview) {
        if (getCurrentIntentMode() != CaptureModule.INTENT_MODE_NORMAL) {
            if (mFilterMenuStatus == FILTER_MENU_ON) {
                removeFilterMenu(false);
            }
            mReviewImage.setImageBitmap(preview);
            mPreviewLayout.setVisibility(View.VISIBLE);
            mReviewPlayButton.setVisibility(View.VISIBLE);
            CameraUtil.fadeIn(mReviewDoneButton);
            CameraUtil.fadeIn(mReviewRetakeButton);
        }
    }

    public void updateAwbInfoText(String[] info) {
        if (info == null || info.length <4)
            return;
        mStatsAwbRText.setText(AWB_INFO_TITLE[0]+info[0]);
        mStatsAwbGText.setText(AWB_INFO_TITLE[1]+info[1]);
        mStatsAwbBText.setText(AWB_INFO_TITLE[2]+info[2]);
        mStatsAwbCcText.setText(AWB_INFO_TITLE[3]+info[3]);
    }

    public void updateAecInfoText(String[] info) {
        if (info == null || info.length <10)
            return;
        mStatsAecLuxText.setText(AEC_INFO_TITLE[0]+info[0]);
        mStatsAeclinearGainText.setText(AEC_INFO_TITLE[1]+info[1]+" "+info[2]+" "+info[3]);
        mStatsAecSensitivityText.setText(AEC_INFO_TITLE[2]+info[4]+" "+info[5]+" "+info[6]);
        mStatsAecExposureTimeText.setText(AEC_INFO_TITLE[3]+info[7]+" "+info[8]+" "+info[9]);
    }

    public void updateAWBInfoVisibility(int visibility) {
        mActivity.runOnUiThread(new Runnable() {
            public void run() {
                if(mStatsAwbInfo != null) {
                    mStatsAwbInfo.setVisibility(visibility);
                }
            }
        });
    }

    public void updateCameraSwitchEnable(boolean enable) {
        mActivity.runOnUiThread(new Runnable() {
            public void run() {
                if(mFrontBackSwitcher != null) {
                    mFrontBackSwitcher.setEnabled(enable);
                }
            }
        });
    }

    public void updateAECInfoVisibility(int visibility) {
        mActivity.runOnUiThread(new Runnable() {
            public void run() {
                if(mStatsAecInfo != null) {
                    mStatsAecInfo.setVisibility(visibility);
                }
            }
        });
    }

    private int getCurrentIntentMode() {
        return mModule.getCurrentIntentMode();
    }

    private void updateDeepZoomIndex() {
        switch(mDeepZoomIndex) {
            case 0:
                mDeepzoomSetName.setText("Zoom OFF");
                mDeepZoomValue = 1.0f;
                break;
            case 1:
                mDeepzoomSetName.setText("Zoom 2X");
                mDeepZoomValue = 2.0f;
                break;
            case 2:
                mDeepzoomSetName.setText("Zoom 4X");
                mDeepZoomValue = 4.0f;
                break;
            default:
                mDeepZoomValue = 1.0f;
                mDeepzoomSetName.setText("Zoom OFF");
                break;
        }
        mModule.updateDeepZoomIndex(mDeepZoomValue);
    }

    private void toggleMakeup() {
        String value = mSettingsManager.getValue(SettingsManager.KEY_MAKEUP);
        if(value != null && !mIsVideoUI) {
            if(value.equals("0")) {
                mSettingsManager.setValue(SettingsManager.KEY_MAKEUP, "50");
                mMakeupSeekBar.setProgress(50);
                mMakeupSeekBarLayout.setVisibility(View.VISIBLE);
                mSeekbarBody.setVisibility(View.VISIBLE);
                mSeekbarToggleButton.setImageResource(R.drawable.seekbar_hide);
            } else {
                mSettingsManager.setValue(SettingsManager.KEY_MAKEUP, "0");
                mMakeupSeekBar.setProgress(0);
                mMakeupSeekBarLayout.setVisibility(View.GONE);
            }
            setMakeupButtonIcon();
            mModule.restartSession(true);
        }
    }

    private void setMakeupButtonIcon() {
        final String value = mSettingsManager.getValue(SettingsManager.KEY_MAKEUP);
        mActivity.runOnUiThread(new Runnable() {
            public void run() {
                if(value != null && !value.equals("0")) {
                    mMakeupButton.setImageResource(R.drawable.beautify_on);
                    mMakeupSeekBarLayout.setVisibility(View.GONE);
                } else {
                    mMakeupButton.setImageResource(R.drawable.beautify);
                    mMakeupSeekBarLayout.setVisibility(View.GONE);
                }
            }
        });
    }

    public float getDeepZoomValue() {
        return mDeepZoomValue;
    }

    public void onCameraOpened(int cameraId) {
        mGestures.setCaptureUI(this);
        if (mModule.isDeepZoom() || mModule.getCurrenCameraMode() == CaptureModule.CameraMode.RTB) {
            mGestures.setZoomEnabled(false);
        } else {
            mGestures.setZoomEnabled(mSettingsManager.isZoomSupported(cameraId));
            initializeZoom(cameraId);
        }
    }

    public void reInitUI() {
        initSceneModeButton();
        initFilterModeButton();
        initFlashButton();
        setMakeupButtonIcon();
        showSceneModeLabel();
        updateMenus();
        if(mModule.isTrackingFocusSettingOn()) {
            mTrackingFocusRenderer.setVisible(false);
            mTrackingFocusRenderer.setVisible(true);
        } else {
            mTrackingFocusRenderer.setVisible(false);
        }

        if (mModule.isT2TFocusSettingOn()) {
            mT2TFocusRenderer.setVisible(false);
            mT2TFocusRenderer.setVisible(true);
        } else {
            mT2TFocusRenderer.setVisible(false);
        }

        if (mSurfaceViewMono != null) {
            if (mSettingsManager != null && mSettingsManager.getValue(SettingsManager.KEY_MONO_PREVIEW) != null
                    && mSettingsManager.getValue(SettingsManager.KEY_MONO_PREVIEW).equalsIgnoreCase("on")) {
                mSurfaceViewMono.setVisibility(View.VISIBLE);
            } else {
                mSurfaceViewMono.setVisibility(View.GONE);
            }
        }
        mZoomIndex = 0;
        mZoomSwitch.setText("1x");
        if(getCurrentIntentMode() == CaptureModule.TYPE_RTB) {
            mZoomSwitch.setVisibility(View.GONE);
        } else {
            mZoomSwitch.setVisibility(View.VISIBLE);
        }
        mFaceView.initMode();
    }

    public void initializeProMode(boolean promode) {
        mCameraControls.setProMode(promode);
        if (promode) {
            mVideoButton.setVisibility(View.INVISIBLE);
            mZoomSwitch.setVisibility(View.INVISIBLE);
            mFlashButton.setVisibility(View.INVISIBLE);
        } else if (mModule.getCurrentIntentMode() == CaptureModule.INTENT_MODE_NORMAL &&
                mModule.getCurrenCameraMode() == CaptureModule.CameraMode.VIDEO) {
            mVideoButton.setVisibility(View.VISIBLE);
        } else if (mModule.getCurrenCameraMode() == CaptureModule.CameraMode.RTB){
            mZoomSwitch.setVisibility(View.GONE);
        } else {
            mZoomSwitch.setVisibility(View.VISIBLE);
        }
    }

    // called from onResume but only the first time
    public void initializeFirstTime() {
        // Initialize shutter button.
        int intentMode = mModule.getCurrentIntentMode();
        if (intentMode == CaptureModule.INTENT_MODE_CAPTURE) {
            mVideoButton.setVisibility(View.INVISIBLE);
        } else if (intentMode == CaptureModule.INTENT_MODE_VIDEO) {
            mShutterButton.setVisibility(View.INVISIBLE);
        } else {
            mShutterButton.setVisibility(View.VISIBLE);
            mVideoButton.setVisibility(View.INVISIBLE);
        }
        mShutterButton.setOnShutterButtonListener(mModule);
        mShutterButton.setImageResource(R.drawable.one_ui_shutter_anim);
        mShutterButton.setOnClickListener(new View.OnClickListener()  {
            @Override
            public void onClick(View v) {
                    doShutterAnimation();
            }
        });
        mVideoButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cancelCountDown();
                mModule.onVideoButtonClick();
            }
        });
    }

    public void initializeZoom(int id) {
        if (!mSettingsManager.isZoomSupported(id) || (mZoomRenderer == null))
            return;

        Float zoomMax = mSettingsManager.getMaxZoom(id);
        mZoomRenderer.setZoomMax(zoomMax);
        String zoomStr = mSettingsManager.getValue(SettingsManager.KEY_ZOOM);
        int zoom = Integer.parseInt(zoomStr);

        mZoomRenderer.setZoom(zoom > 1 ? zoom : 1f);
        mZoomRenderer.setOnZoomChangeListener(new ZoomChangeListener());
    }

    public void enableGestures(boolean enable) {
        if (mGestures != null) {
            mGestures.setEnabled(enable);
        }
    }

    public boolean isPreviewMenuBeingShown() {
        return mFilterMenuStatus == FILTER_MENU_ON;
    }

    public void removeFilterMenu(boolean animate) {
        if (animate) {
            animateSlideOut(mFilterLayout);
        } else {
            mFilterMenuStatus = FILTER_MENU_NONE;
            if (mFilterLayout != null) {
                ((ViewGroup) mRootView).removeView(mFilterLayout);
                mFilterLayout = null;
            }
            if (mModule.getCurrentIntentMode() == CaptureModule.INTENT_MODE_NORMAL) {
                mModeSelectLayout.setVisibility(View.VISIBLE);
            }
            mModule.updateZoomSeekBarVisible();
        }
        updateMenus();
    }

    public void openSettingsMenu() {
        if (mPreviewLayout != null && mPreviewLayout.getVisibility() == View.VISIBLE) {
            return;
        }
        clearFocus();
        removeFilterMenu(false);
        Intent intent = new Intent(mActivity, SettingsActivity.class);
        intent.putExtra(SettingsActivity.CAMERA_MODULE, mModule.getCurrenCameraMode());
        mActivity.startActivity(intent);
    }

    public void initCameraSwitcher() {
        mFrontBackSwitcher.setVisibility(View.INVISIBLE);
        String value = mSettingsManager.getValue(SettingsManager.KEY_FRONT_REAR_SWITCHER_VALUE);
        Log.d(TAG,"value of KEY_FRONT_REAR_SWITCHER_VALUE is null? " + (value==null));
        if (value == null)
            return;

        mFrontBackSwitcher.setVisibility(View.VISIBLE);
        mFrontBackSwitcher.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mModule.writeXMLForWarmAwb();
                switchFrontBackCamera();
            }
        });
    }

    public void switchFrontBackCamera() {
        if (mIsVideoUI || !mModule.getCameraModeSwitcherAllowed()
                || !isSupportFrontCamera(mModule.getCurrenCameraMode())) {
            return;
        }
        mModule.setCameraModeSwitcherAllowed(false);
        removeFilterMenu(false);

        String value = mSettingsManager.getValue(
                SettingsManager.KEY_FRONT_REAR_SWITCHER_VALUE);
        if (value == null)
            return;

        int index = mSettingsManager.getValueIndex(SettingsManager.KEY_FRONT_REAR_SWITCHER_VALUE);
        index = (index + 1) % 2;
        if (index == 1 && (mModule.getCurrenCameraMode() == CaptureModule.CameraMode.RTB ||
                mModule.getCurrenCameraMode() == CaptureModule.CameraMode.SAT)) {
            switchToPhotoModeDueToError(false);
        }
        mSettingsManager.setValueIndex(SettingsManager.KEY_FRONT_REAR_SWITCHER_VALUE, index);
    }

    private boolean isSupportFrontCamera(CaptureModule.CameraMode mode) {
        return mode != CaptureModule.CameraMode.PRO_MODE;
    }

    public void initFlashButton() {
        mFlashButton.init(mModule.getCurrenCameraMode() == CaptureModule.CameraMode.VIDEO ||
                mModule.getCurrenCameraMode() == CaptureModule.CameraMode.PRO_MODE ||
                mModule.getCurrenCameraMode() == CaptureModule.CameraMode.HFR);
        enableView(mFlashButton, SettingsManager.KEY_FLASH_MODE);
    }

    public void initSceneModeButton() {
        mSceneModeSwitcher.setVisibility(View.INVISIBLE);
        String value = mSettingsManager.getValue(SettingsManager.KEY_SCENE_MODE);
        if (value == null) return;
        mSceneModeSwitcher.setVisibility(View.VISIBLE);
        mSceneModeSwitcher.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clearFocus();
                removeFilterMenu(false);
                Intent intent = new Intent(mActivity, SceneModeActivity.class);
                intent.putExtra(CameraUtil.KEY_IS_SECURE_CAMERA, mActivity.isSecureCamera());
                intent.putExtra(SettingsActivity.CAMERA_MODULE, mModule.getCurrenCameraMode());
                mActivity.startActivity(intent);
            }
        });
    }

    public void initFilterModeButton() {
        mFilterModeSwitcher.setVisibility(View.INVISIBLE);
        String value = mSettingsManager.getValue(SettingsManager.KEY_COLOR_EFFECT);
        if (value == null) return;

        enableView(mFilterModeSwitcher, SettingsManager.KEY_COLOR_EFFECT);

        mFilterModeSwitcher.setVisibility(View.VISIBLE);
        mFilterModeSwitcher.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addFilterMode();
                adjustOrientation();
                updateMenus();
                mModeSelectLayout.setVisibility(View.GONE);
                hideZoomSeekBar();
            }
        });
    }

    private void enableView(View view, String key) {
        Map<String, SettingsManager.Values> map = mSettingsManager.getValuesMap();
        SettingsManager.Values values = map.get(key);
        if ( values != null ) {
            boolean enabled = values.overriddenValue == null;
            view.setEnabled(enabled);
        }
    }

    public void showTimeLapseUI(boolean enable) {
        if (mTimeLapseLabel != null) {
            mTimeLapseLabel.setVisibility(enable ? View.VISIBLE : View.GONE);
        }
    }

    public void showRecordingUI(boolean recording, boolean highspeed) {
        //this is handled in child thread, but mode has changed
        if(mModule.CURRENT_MODE == CaptureModule.CameraMode.DEFAULT && getCurrentIntentMode() != CaptureModule.INTENT_MODE_VIDEO){
            return;
        }
        if (recording) {
            if (highspeed) {
                mFlashButton.setVisibility(View.GONE);
            } else {
                mFlashButton.init(true);
            }
            mVideoButton.setImageResource(R.drawable.video_stop);
            mRecordingTimeView.setText("00:00");
            mRecordingTimeRect.setVisibility(View.VISIBLE);
            mMuteButton.setVisibility((mModule.isHSRMode() || mModule.getCurrenCameraMode() == CaptureModule.CameraMode.VIDEO) ? View.VISIBLE : View.INVISIBLE);
            setMuteButtonResource(!mModule.isAudioMute());
        } else {
            mFlashButton.setVisibility(View.VISIBLE);
//            mSettingsManager.setValue(SettingsManager.KEY_VIDEO_FLASH_MODE, "off");
            mFlashButton.init(true);
            mVideoButton.setImageResource(R.drawable.video_capture);
            mRecordingTimeRect.setVisibility(View.GONE);
            mMuteButton.setVisibility(View.INVISIBLE);
        }
    }

    private void setMuteButtonResource(boolean isUnMute) {
        if(isUnMute) {
            mMuteButton.setImageResource(R.drawable.ic_unmuted_button);
        } else {
            mMuteButton.setImageResource(R.drawable.ic_muted_button);
        }
    }

    private boolean needShowInstructional() {
        boolean needShow = true;
        final SharedPreferences pref = mActivity.getSharedPreferences(
                ComboPreferences.getGlobalSharedPreferencesName(mActivity), Context.MODE_PRIVATE);
        int index = mSettingsManager.getValueIndex(SettingsManager.KEY_SCENE_MODE);
        if ( index < 1 ) {
            needShow = false;
        }else{
            final String instructionalKey = SettingsManager.KEY_SCENE_MODE + "_" + index;
            needShow = pref.getBoolean(instructionalKey, false) ? false : true;
        }

        return needShow;

    }

    private void showSceneInstructionalDialog(int orientation) {
        int layoutId = R.layout.scene_mode_instructional;
        if ( orientation == 90 || orientation == 270 ) {
            layoutId = R.layout.scene_mode_instructional_landscape;
        }
        LayoutInflater inflater =
                (LayoutInflater)mActivity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View view = inflater.inflate(layoutId, null);

       final int index = mSettingsManager.getValueIndex(SettingsManager.KEY_SCENE_MODE);
        TextView name = (TextView)view.findViewById(R.id.scene_mode_name);
        CharSequence sceneModeNameArray[] =
                mSettingsManager.getEntries(SettingsManager.KEY_SCENE_MODE);
        name.setText(sceneModeNameArray[index]);

        ImageView icon = (ImageView)view.findViewById(R.id.scene_mode_icon);
        int[] resId = mSettingsManager.getResource(SettingsManager.KEY_SCEND_MODE_INSTRUCTIONAL,
                SettingsManager.RESOURCE_TYPE_THUMBNAIL);
        icon.setImageResource(resId[index]);

        TextView instructional = (TextView)view.findViewById(R.id.scene_mode_instructional);
        CharSequence instructionalArray[] =
                mSettingsManager.getEntries(SettingsManager.KEY_SCEND_MODE_INSTRUCTIONAL);
        if ( instructionalArray[index].length() == 0 ) {
            //For now, not all scene mode has instructional
            return;
        }
        instructional.setText(instructionalArray[index]);

        final CheckBox remember = (CheckBox)view.findViewById(R.id.remember_selected);
        Button ok = (Button)view.findViewById(R.id.scene_mode_instructional_ok);
        ok.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {

                if ( remember.isChecked()) {
                    final SharedPreferences pref = mActivity.getSharedPreferences(
                            ComboPreferences.getGlobalSharedPreferencesName(mActivity),
                            Context.MODE_PRIVATE);

                    String instructionalKey = SettingsManager.KEY_SCENE_MODE + "_" + index;
                    SharedPreferences.Editor editor = pref.edit();
                    editor.putBoolean(instructionalKey, true);
                    editor.commit();
                }
                mSceneModeInstructionalDialog.dismiss();
                mSceneModeInstructionalDialog = null;
            }
        });

        mSceneModeInstructionalDialog =
                new AlertDialog.Builder(mActivity, AlertDialog.THEME_HOLO_LIGHT)
                        .setView(view).create();
        try {
            mSceneModeInstructionalDialog.show();
        }catch(Exception e) {
            e.printStackTrace();
            return;
        }
        if ( orientation != 0 ) {
            rotationSceneModeInstructionalDialog(view, orientation);
        }
    }

    private int getScreenWidth() {
        DisplayMetrics metric = new DisplayMetrics();
        mActivity.getWindowManager().getDefaultDisplay().getMetrics(metric);
        return metric.widthPixels < metric.heightPixels ? metric.widthPixels : metric.heightPixels;
    }

    private void rotationSceneModeInstructionalDialog(View view, int orientation) {
        view.setRotation(-orientation);
        int screenWidth = getScreenWidth();
        int dialogSize = screenWidth*9/10;
        Window dialogWindow = mSceneModeInstructionalDialog.getWindow();
        WindowManager.LayoutParams lp = dialogWindow.getAttributes();
        dialogWindow.setGravity(Gravity.CENTER);
        lp.width = lp.height = dialogSize;
        dialogWindow.setAttributes(lp);
        RelativeLayout layout = (RelativeLayout)view.findViewById(R.id.mode_layout_rect);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(dialogSize, dialogSize);
        layout.setLayoutParams(params);
    }

    private void showSceneModeLabel() {
        mIsSceneModeLabelClose = false;
        int index = mSettingsManager.getValueIndex(SettingsManager.KEY_SCENE_MODE);
        CharSequence sceneModeNameArray[] = mSettingsManager.getEntries(SettingsManager.KEY_SCENE_MODE);
        if (mModule.isDeepPortraitMode()) {
            mSceneModeLabelRect.setVisibility(View.GONE);
            mExitBestMode.setVisibility(View.GONE);
            return;
        }
        if ( index > 0 && index < sceneModeNameArray.length) {
            mSceneModeName.setText(sceneModeNameArray[index]);
            mSceneModeLabelRect.setVisibility(View.VISIBLE);
            mExitBestMode.setVisibility(View.VISIBLE);
            if (mModule.isDeepZoom()) {
                mDeepZoomModeRect.setVisibility(View.VISIBLE);
            } else {
                mDeepZoomModeRect.setVisibility(View.GONE);
            }
        }else{
            mSceneModeLabelRect.setVisibility(View.GONE);
            mExitBestMode.setVisibility(View.GONE);
            mDeepZoomModeRect.setVisibility(View.GONE);
        }
    }


    public void resetTrackingFocus() {
        if(mModule.isTrackingFocusSettingOn()) {
            mTrackingFocusRenderer.setVisible(false);
            mTrackingFocusRenderer.setVisible(true);
        }
    }

    public void resetTouchTrackingFocus() {
        if (mModule.isT2TFocusSettingOn()) {
            mT2TFocusRenderer.setVisible(false);
            mT2TFocusRenderer.setVisible(true);
        }
    }

    public void hideUIwhileRecording() {
        mCameraControls.setVideoMode(true);
        mModeSelectLayout.setVisibility(View.INVISIBLE);
        mSceneModeLabelRect.setVisibility(View.INVISIBLE);
        mDeepZoomModeRect.setVisibility(View.INVISIBLE);
        mFrontBackSwitcher.setVisibility(View.INVISIBLE);
        mFilterModeSwitcher.setVisibility(View.INVISIBLE);
        mSceneModeSwitcher.setVisibility(View.INVISIBLE);
        mSettingsIcon.setVisibility(View.INVISIBLE);
        String value = mSettingsManager.getValue(SettingsManager.KEY_MAKEUP);
        if(value != null && value.equals("0")) {
            mMakeupButton.setVisibility(View.GONE);
        }
        mIsVideoUI = true;
        if (!mModule.isSSMEnabled()) {
            mPauseButton.setVisibility(View.VISIBLE);
        }
        if (mModule.getCurrentIntentMode() == CaptureModule.INTENT_MODE_NORMAL) {
            mShutterButton.setVisibility(View.VISIBLE);
        }
    }

    public void showUIafterRecording() {
        mCameraControls.setVideoMode(false);
        mFrontBackSwitcher.setVisibility(View.VISIBLE);
        mSettingsIcon.setVisibility(View.VISIBLE);
        mIsVideoUI = false;
        mPauseButton.setVisibility(View.INVISIBLE);
        if (mModule.getCurrentIntentMode() == CaptureModule.INTENT_MODE_NORMAL) {
            mShutterButton.setVisibility(View.INVISIBLE);
            mModeSelectLayout.setVisibility(View.VISIBLE);
        }
        mFilterModeSwitcher.setVisibility(View.VISIBLE);
        if (mFilterMenuStatus == FILTER_MENU_ON) {
            removeFilterMenu(true);
        }
        //exit recording mode needs to refresh scene mode label.
        showSceneModeLabel();
    }

    public void showRelatedIcons(CaptureModule.CameraMode mode) {
        //common settings
        mShutterButton.setVisibility(View.VISIBLE);
        mFrontBackSwitcher.setVisibility(View.VISIBLE);
        mMakeupButton.setVisibility(View.INVISIBLE);
        mSceneModeSwitcher.setVisibility(View.INVISIBLE);
        //settings for each mode
        switch (mode) {
            case DEFAULT:
                mFilterModeSwitcher.setVisibility(View.VISIBLE);
                mSceneModeSwitcher.setVisibility(View.VISIBLE);
                mVideoButton.setVisibility(View.INVISIBLE);
                mMuteButton.setVisibility(View.INVISIBLE);
                mPauseButton.setVisibility(View.INVISIBLE);
                break;
            case RTB:
            case SAT:
                mFilterModeSwitcher.setVisibility(View.VISIBLE);
                mSceneModeSwitcher.setVisibility(View.VISIBLE);
                mVideoButton.setVisibility(View.INVISIBLE);
                mFlashButton.setVisibility(View.INVISIBLE);
                mMuteButton.setVisibility(View.INVISIBLE);
                mPauseButton.setVisibility(View.INVISIBLE);
                if (!DEV_LEVEL_ALL) {
                    mFrontBackSwitcher.setVisibility(View.INVISIBLE);
                }
                break;
            case VIDEO:
            case HFR:
                mVideoButton.setVisibility(View.VISIBLE);
                mFilterModeSwitcher.setVisibility(View.VISIBLE);
                mShutterButton.setVisibility(View.INVISIBLE);
                break;
            case PRO_MODE:
                mFilterModeSwitcher.setVisibility(View.INVISIBLE);
                mVideoButton.setVisibility(View.INVISIBLE);
                mFrontBackSwitcher.setVisibility(View.INVISIBLE);
                mMuteButton.setVisibility(View.INVISIBLE);
                mPauseButton.setVisibility(View.INVISIBLE);
                break;
            default:
                break;
        }
        String value = mSettingsManager.getValue(SettingsManager.KEY_FRONT_REAR_SWITCHER_VALUE);
        if (value == null) {
            mFrontBackSwitcher.setVisibility(View.INVISIBLE);
        }
        if(mModule.mMFNREnable && mModule.getMainCameraId() ==  android.hardware.Camera.CameraInfo.CAMERA_FACING_FRONT){
            mFilterModeSwitcher.setVisibility(View.INVISIBLE);
        }
    }

    public void addFilterMode() {
        if (mSettingsManager.getValue(SettingsManager.KEY_COLOR_EFFECT) == null)
            return;

        int rotation = CameraUtil.getDisplayRotation(mActivity);
        boolean mIsDefaultToPortrait = CameraUtil.isDefaultToPortrait(mActivity);
        if (!mIsDefaultToPortrait) {
            rotation = (rotation + 90) % 360;
        }
        WindowManager wm = (WindowManager) mActivity.getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        CharSequence[] entries = mSettingsManager.getEntries(SettingsManager.KEY_COLOR_EFFECT);

        Resources r = mActivity.getResources();
        int height = (int) (r.getDimension(R.dimen.filter_mode_height) + 2
                * r.getDimension(R.dimen.filter_mode_padding) + 1);
        int width = (int) (r.getDimension(R.dimen.filter_mode_width) + 2
                * r.getDimension(R.dimen.filter_mode_padding) + 1);

        int gridRes;
        boolean portrait = (rotation == 0) || (rotation == 180);
        int size = height;
        if (!portrait) {
            gridRes = R.layout.vertical_grid;
            size = width;
        } else {
            gridRes = R.layout.horiz_grid;
        }

        int[] thumbnails = mSettingsManager.getResource(SettingsManager.KEY_COLOR_EFFECT,
                SettingsManager.RESOURCE_TYPE_THUMBNAIL);
        LayoutInflater inflater = (LayoutInflater) mActivity.getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);
        FrameLayout gridOuterLayout = (FrameLayout) inflater.inflate(
                gridRes, null, false);
        gridOuterLayout.setBackgroundColor(android.R.color.transparent);
        removeFilterMenu(false);
        mFilterMenuStatus = FILTER_MENU_ON;
        mFilterLayout = new LinearLayout(mActivity);

        ViewGroup.LayoutParams params = null;
        if (!portrait) {
            params = new ViewGroup.LayoutParams(size, FrameLayout.LayoutParams.MATCH_PARENT);
            mFilterLayout.setLayoutParams(params);
            ((ViewGroup) mRootView).addView(mFilterLayout);
        } else {
            params = new ViewGroup.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, size);
            mFilterLayout.setLayoutParams(params);
            ((ViewGroup) mRootView).addView(mFilterLayout);
            mFilterLayout.setY(display.getHeight() - 2 * size);
        }
        gridOuterLayout.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams
                .MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        LinearLayout gridLayout = (LinearLayout) gridOuterLayout.findViewById(R.id.layout);
        final View[] views = new View[entries.length];

        int init = mSettingsManager.getValueIndex(SettingsManager.KEY_COLOR_EFFECT);
        for (int i = 0; i < entries.length; i++) {
            RotateLayout filterBox = (RotateLayout) inflater.inflate(
                    R.layout.filter_mode_view, null, false);
            ImageView imageView = (ImageView) filterBox.findViewById(R.id.image);
            final int j = i;

            filterBox.setOnTouchListener(new View.OnTouchListener() {
                private long startTime;

                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
                        startTime = System.currentTimeMillis();
                    } else if (event.getAction() == MotionEvent.ACTION_UP) {
                        if (System.currentTimeMillis() - startTime < CLICK_THRESHOLD) {
                            mSettingsManager.setValueIndex(SettingsManager
                                    .KEY_COLOR_EFFECT, j);
                            for (View v1 : views) {
                                v1.setBackground(null);
                            }
                            ImageView image = (ImageView) v.findViewById(R.id.image);
                            image.setBackgroundColor(HIGHLIGHT_COLOR);
                        }
                    }
                    return true;
                }
            });

            views[j] = imageView;
            if (i == init)
                imageView.setBackgroundColor(HIGHLIGHT_COLOR);
            TextView label = (TextView) filterBox.findViewById(R.id.label);

            imageView.setImageResource(thumbnails[i]);
            label.setText(entries[i]);
            gridLayout.addView(filterBox);
        }
        mFilterLayout.addView(gridOuterLayout);
    }

    public void removeAndCleanUpFilterMenu() {
        removeFilterMenu(false);
        cleanUpMenus();
    }

    public void animateFadeIn(View v) {
        ViewPropertyAnimator vp = v.animate();
        vp.alpha(0.85f).setDuration(ANIMATION_DURATION);
        vp.start();
    }

    private void animateSlideOut(final View v) {
        if (v == null || mFilterMenuStatus == FILTER_MENU_IN_ANIMATION)
            return;
        mFilterMenuStatus = FILTER_MENU_IN_ANIMATION;

        ViewPropertyAnimator vp = v.animate();
        if (View.LAYOUT_DIRECTION_RTL == TextUtils
                .getLayoutDirectionFromLocale(Locale.getDefault())) {
            vp.translationXBy(v.getWidth()).setDuration(ANIMATION_DURATION);
        } else {
            vp.translationXBy(-v.getWidth()).setDuration(ANIMATION_DURATION);
        }
        vp.setListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {
            }

            @Override
            public void onAnimationRepeat(Animator animation) {

            }

            @Override
            public void onAnimationEnd(Animator animation) {
                removeAndCleanUpFilterMenu();
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                removeAndCleanUpFilterMenu();
            }
        });
        vp.start();
    }

    public void animateSlideIn(View v, int delta, boolean forcePortrait) {
        int orientation = getOrientation();
        if (!forcePortrait)
            orientation = 0;

        ViewPropertyAnimator vp = v.animate();
        float dest;
        if (View.LAYOUT_DIRECTION_RTL == TextUtils
                .getLayoutDirectionFromLocale(Locale.getDefault())) {
            switch (orientation) {
                case 0:
                    dest = v.getX();
                    v.setX(-(dest - delta));
                    vp.translationX(dest);
                    break;
                case 90:
                    dest = v.getY();
                    v.setY(-(dest + delta));
                    vp.translationY(dest);
                    break;
                case 180:
                    dest = v.getX();
                    v.setX(-(dest + delta));
                    vp.translationX(dest);
                    break;
                case 270:
                    dest = v.getY();
                    v.setY(-(dest - delta));
                    vp.translationY(dest);
                    break;
            }
        } else {
            switch (orientation) {
                case 0:
                    dest = v.getX();
                    v.setX(dest - delta);
                    vp.translationX(dest);
                    break;
                case 90:
                    dest = v.getY();
                    v.setY(dest + delta);
                    vp.translationY(dest);
                    break;
                case 180:
                    dest = v.getX();
                    v.setX(dest + delta);
                    vp.translationX(dest);
                    break;
                case 270:
                    dest = v.getY();
                    v.setY(dest - delta);
                    vp.translationY(dest);
                    break;
            }
        }
        vp.setDuration(ANIMATION_DURATION).start();
    }

    public void hideUIWhileCountDown() {
        hideCameraControls(true);
        mGestures.setZoomOnly(true);
    }

    public void showUIAfterCountDown() {
        hideCameraControls(false);
        mGestures.setZoomOnly(false);
        updateMenus();
    }

    public void hideCameraControls(boolean hide) {
        final boolean status = !hide;
        if (mFlashButton != null){
            mFlashButton.setEnabled(status);
            if (!hide) {
                mFlashButton.init(mModule.getCurrenCameraMode() == CaptureModule.CameraMode.VIDEO ||
                        mModule.getCurrenCameraMode() == CaptureModule.CameraMode.PRO_MODE);
            }
        }
        if (mFrontBackSwitcher != null) mFrontBackSwitcher.setEnabled(status);
        if (mSceneModeSwitcher != null) mSceneModeSwitcher.setEnabled(status);
        if (mFilterModeSwitcher != null) mFilterModeSwitcher.setEnabled(status);
        if (mMakeupButton != null) mMakeupButton.setVisibility(View.GONE);
    }

    public void initializeControlByIntent() {
        mThumbnail = (ImageView) mRootView.findViewById(R.id.preview_thumb);
        mThumbnail.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!CameraControls.isAnimating() && !mModule.isTakingPicture() &&
                        !mModule.isRecordingVideo())
                    mActivity.gotoGallery();
            }
        });
        if (mModule.getCurrentIntentMode() != CaptureModule.INTENT_MODE_NORMAL) {
            mCameraControls.setIntentMode(mModule.getCurrentIntentMode());
        }
    }

    public void doShutterAnimation() {
        AnimationDrawable frameAnimation = (AnimationDrawable) mShutterButton.getDrawable();
        frameAnimation.stop();
        frameAnimation.start();
    }

    public void showUI() {
        if (!mUIhidden)
            return;
        mUIhidden = false;
        mPieRenderer.setBlockFocus(false);
        mCameraControls.showUI();
    }

    public void hideUI() {
        if (mUIhidden)
            return;
        mUIhidden = true;
        mPieRenderer.setBlockFocus(true);
        mCameraControls.hideUI();
    }

    public void cleanUpMenus() {
        showUI();
        updateMenus();
        mActivity.setSystemBarsVisibility(false);
    }

    public void startDeepPortraitMode(Size preview) {
        mSurfaceView.setVisibility(View.GONE);
        mSurfaceViewMono.setVisibility(View.GONE);
        mGLSurfaceView = new GLCameraPreview(
                    mActivity, preview.getWidth(), preview.getHeight(), mModule);
        FrameLayout layout = (FrameLayout) mActivity.findViewById(R.id.camera_glpreview);
        layout.addView(mGLSurfaceView);
        mGLSurfaceView.setVisibility(View.VISIBLE);
        mRootView.requestLayout();
        final SharedPreferences prefs =
                PreferenceManager.getDefaultSharedPreferences(mActivity);
        int progress = prefs.getInt(SettingsManager.KEY_DEEPPORTRAIT_VALUE,50);
        mDeepportraitSeekBar.setProgress(progress);
        mDeepportraitSeekBar.setVisibility(View.VISIBLE);
        mRenderOverlay.setVisibility(View.GONE);
    }

    public void stopDeepPortraitMode() {
        FrameLayout layout = (FrameLayout)mActivity.findViewById(R.id.camera_glpreview);
        if (mGLSurfaceView != null) {
            mGLSurfaceView.setVisibility(View.GONE);
            layout.removeAllViews();
            mGLSurfaceView = null;
        }
        mDeepportraitSeekBar.setVisibility(View.GONE);
        mRenderOverlay.setVisibility(View.VISIBLE);
    }

    public GLCameraPreview getGLCameraPreview() {
        return  mGLSurfaceView;
    }

    public void updateMenus() {
        boolean enableMakeupMenu = true;
        boolean enableFilterMenu = true;
        boolean enableSceneMenu = true;
        String makeupValue = mSettingsManager.getValue(SettingsManager.KEY_MAKEUP);
        int colorEffect = mSettingsManager.getValueIndex(SettingsManager.KEY_COLOR_EFFECT);
        String sceneMode = mSettingsManager.getValue(SettingsManager.KEY_SCENE_MODE);
        if (makeupValue != null && !makeupValue.equals("0")) {
            enableSceneMenu = false;
            enableFilterMenu = false;
        } else if (colorEffect != 0 || mFilterMenuStatus == FILTER_MENU_ON){
            enableSceneMenu = false;
            enableMakeupMenu = false;
        }else if ( sceneMode != null && !sceneMode.equals("0")){
             enableMakeupMenu = false;
             enableFilterMenu = false;
        }
        if(!BeautificationFilter.isSupportedStatic()) {
            enableMakeupMenu = false;
        }
        mMakeupButton.setEnabled(enableMakeupMenu);
        if(!BeautificationFilter.isSupportedStatic()) {
            mMakeupButton.setVisibility(View.GONE);
        }
        mFilterModeSwitcher.setEnabled(enableFilterMenu);
        mSceneModeSwitcher.setEnabled(enableSceneMenu);
    }

    public void toggleProgressBar(boolean show) {
        mProgressBar.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    public boolean arePreviewControlsVisible() {
        return !mUIhidden;
    }

    public void onOrientationChanged() {
    }

    /**
     * Enables or disables the shutter button.
     */
    public void enableShutter(boolean enabled) {
        if (mShutterButton != null) {
            mShutterButton.setEnabled(enabled);
        }
    }

    public boolean isShutterEnabled() {
        return mShutterButton.isEnabled();
    }

    /**
     * Enables or disables the video button.
     */
    public void enableVideo(boolean enabled) {
        if (mVideoButton != null) {
            mVideoButton.setEnabled(enabled);
        }
    }

    private boolean handleBackKeyOnMenu() {
        if (mFilterMenuStatus == FILTER_MENU_ON) {
            removeFilterMenu(true);
            return true;
        }
        return false;
    }

    public boolean onBackPressed() {
        if (handleBackKeyOnMenu()) return true;
        if (mPieRenderer != null && mPieRenderer.showsItems()) {
            mPieRenderer.hide();
            return true;
        }

        if (!mModule.isCameraIdle()) {
            // ignore backs while we're taking a picture
            return true;
        }
        return false;
    }

    public SurfaceHolder getSurfaceHolder() {
        return mSurfaceHolder;
    }

    private class MonoDummyListener implements Allocation.OnBufferAvailableListener {
        ScriptIntrinsicYuvToRGB yuvToRgbIntrinsic;
        public MonoDummyListener(ScriptIntrinsicYuvToRGB yuvToRgbIntrinsic) {
            this.yuvToRgbIntrinsic = yuvToRgbIntrinsic;
        }

        @Override
        public void onBufferAvailable(Allocation a) {
            if(mMonoDummyAllocation != null) {
                mMonoDummyAllocation.ioReceive();
                mIsMonoDummyAllocationEverUsed = true;
                if(mSurfaceViewMono.getVisibility() == View.VISIBLE) {
                    try {
                        yuvToRgbIntrinsic.forEach(mMonoDummyOutputAllocation);
                        mMonoDummyOutputAllocation.ioSend();
                    } catch(Exception e)
                    {
                        Log.e(TAG, e.toString());
                    }
                }
            }
        }
    }

    public Surface getMonoDummySurface() {
        if (mMonoDummyAllocation == null) {
            RenderScript rs = RenderScript.create(mActivity);
            Type.Builder yuvTypeBuilder = new Type.Builder(rs, Element.YUV(rs));
            yuvTypeBuilder.setX(mPreviewWidth);
            yuvTypeBuilder.setY(mPreviewHeight);
            yuvTypeBuilder.setYuvFormat(ImageFormat.YUV_420_888);
            mMonoDummyAllocation = Allocation.createTyped(rs, yuvTypeBuilder.create(), Allocation.USAGE_IO_INPUT|Allocation.USAGE_SCRIPT);
            ScriptIntrinsicYuvToRGB yuvToRgbIntrinsic = ScriptIntrinsicYuvToRGB.create(rs, Element.RGBA_8888(rs));
            yuvToRgbIntrinsic.setInput(mMonoDummyAllocation);

            if(mSettingsManager.getValue(SettingsManager.KEY_MONO_PREVIEW).equalsIgnoreCase("on")) {
                Type.Builder rgbTypeBuilder = new Type.Builder(rs, Element.RGBA_8888(rs));
                rgbTypeBuilder.setX(mPreviewWidth);
                rgbTypeBuilder.setY(mPreviewHeight);
                mMonoDummyOutputAllocation = Allocation.createTyped(rs, rgbTypeBuilder.create(), Allocation.USAGE_SCRIPT | Allocation.USAGE_IO_OUTPUT);
                mMonoDummyOutputAllocation.setSurface(mSurfaceHolderMono.getSurface());
                mActivity.runOnUiThread(new Runnable() {
                    public void run() {
                        mSurfaceHolderMono.setFixedSize(mPreviewWidth, mPreviewHeight);
                        mSurfaceViewMono.setVisibility(View.VISIBLE);
                    }
                });
            }
            mMonoDummyAllocation.setOnBufferAvailableListener(new MonoDummyListener(yuvToRgbIntrinsic));

            mIsMonoDummyAllocationEverUsed = false;
        }
        return mMonoDummyAllocation.getSurface();
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

    private void initializeCountDown() {
        mActivity.getLayoutInflater().inflate(R.layout.count_down_to_capture,
                (ViewGroup) mRootView, true);
        mCountDownView = (CountDownView) (mRootView.findViewById(R.id.count_down_to_capture));
        mCountDownView.setCountDownFinishedListener((CountDownView.OnCountDownFinishedListener) mModule);
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

    public void initCountDownView() {
        if (mCountDownView == null) {
            initializeCountDown();
        } else {
            mCountDownView.initSoundPool();
        }
    }

    public void releaseSoundPool() {
        if (mCountDownView != null) {
            mCountDownView.releaseSoundPool();
        }
    }

    public void startCountDown(int sec, boolean playSound) {
        mCountDownView.startCountDown(sec, playSound);
        hideUIWhileCountDown();
    }

    public void onPause() {
        cancelCountDown();
        collapseCameraControls();

        if (mFaceView != null) mFaceView.clear();
        if(mTrackingFocusRenderer != null) {
            mTrackingFocusRenderer.setVisible(false);
        }
        if (mT2TFocusRenderer != null) {
            mT2TFocusRenderer.setVisible(false);
        }
        if (mMonoDummyAllocation != null && mIsMonoDummyAllocationEverUsed) {
            mMonoDummyAllocation.setOnBufferAvailableListener(null);
            mMonoDummyAllocation.destroy();
            mMonoDummyAllocation = null;
        }
        if (mMonoDummyOutputAllocation != null && mIsMonoDummyAllocationEverUsed) {
            mMonoDummyOutputAllocation.destroy();
            mMonoDummyOutputAllocation = null;
        }
        mSurfaceViewMono.setVisibility(View.GONE);
    }

    public boolean collapseCameraControls() {
        // Remove all the popups/dialog boxes
        boolean ret = false;
        mCameraControls.showRefocusToast(false);
        return ret;
    }

    public void showRefocusToast(boolean show) {
        mCameraControls.showRefocusToast(show);
    }

    private FocusIndicator getFocusIndicator() {
        if (mModule.isTrackingFocusSettingOn()) {
            if (mPieRenderer != null) {
                mPieRenderer.clear();
            }
            return mTrackingFocusRenderer;
        }
        String value = mSettingsManager.getValue(SettingsManager.KEY_TOUCH_TRACK_FOCUS);
        if (value != null && value.equals("on")) {
            if (mPieRenderer != null) {
                mPieRenderer.clear();
            }
            return mT2TFocusRenderer;
        }
        FocusIndicator focusIndicator;
        if (mFaceView != null && mFaceView.faceExists() && !mIsTouchAF) {
            if (mPieRenderer != null) {
                mPieRenderer.clear();
            }
            focusIndicator = mFaceView;
        } else {
            focusIndicator = mPieRenderer;
        }

        return focusIndicator;
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
        FocusIndicator indicator = getFocusIndicator();
        if (indicator != null) indicator.clear();
        mIsTouchAF = false;
    }

    @Override
    public void setFocusPosition(int x, int y) {
        mPieRenderer.setFocus(x, y);
        mIsTouchAF = true;
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
    public void onFocusFailed(boolean timeOut) {
        FocusIndicator indicator = getFocusIndicator();
        if (indicator != null) indicator.showFail(timeOut);

    }

    @Override
    public void pauseFaceDetection() {

    }

    @Override
    public void resumeFaceDetection() {
    }

    public void onStartFaceDetection(int orientation, boolean mirror, Rect cameraBound,
                                     Rect originalCameraBound) {
        mFaceView.setBlockDraw(false);
        mFaceView.clear();
        mFaceView.setVisibility(View.VISIBLE);
        mFaceView.setDisplayOrientation(orientation);
        mFaceView.setMirror(mirror);
        mFaceView.setCameraBound(cameraBound);
        mFaceView.setOriginalCameraBound(originalCameraBound);
        mFaceView.setZoom(mModule.getZoomValue());
        mFaceView.resume();
    }

    public void updateFaceViewCameraBound(Rect cameraBound) {
        mFaceView.setCameraBound(cameraBound);
        mFaceView.setZoom(mModule.getZoomValue());
    }

    public void onStopFaceDetection() {
        if (mFaceView != null) {
            mFaceView.setBlockDraw(true);
            mFaceView.clear();
        }
    }

    @Override
    public void onFaceDetection(Face[] faces, CameraManager.CameraProxy camera) {
    }

    public void onFaceDetection(android.hardware.camera2.params.Face[] faces,
                                ExtendedFace[] extendedFaces) {
        mFaceView.setFaces(faces,extendedFaces);
    }

    public Point getSurfaceViewSize() {
        Point point = new Point();
        if (mSurfaceView != null) point.set(mSurfaceView.getWidth(), mSurfaceView.getHeight());
        return point;
    }

    public void adjustOrientation() {
        setOrientation(mOrientation, true);
    }

    public void setOrientation(int orientation, boolean animation) {
        mOrientation = orientation;
        mCameraControls.setOrientation(orientation, animation);
        if (mMenuHelp != null) {
            mMenuHelp.setOrientation(orientation, animation);
        }
        if (mFilterLayout != null) {
            ViewGroup vg = (ViewGroup) mFilterLayout.getChildAt(0);
            if (vg != null)
                vg = (ViewGroup) vg.getChildAt(0);
            if (vg != null) {
                for (int i = vg.getChildCount() - 1; i >= 0; --i) {
                    RotateLayout l = (RotateLayout) vg.getChildAt(i);
                    l.setOrientation(orientation, animation);
                }
            }
        }
        if (mRecordingTimeRect != null) {
            mRecordingTimeView.setRotation(-orientation);
        }
        if (mFaceView != null) {
            mFaceView.setDisplayRotation(orientation);
        }
        if (mCountDownView != null)
            mCountDownView.setOrientation(orientation);
        RotateTextToast.setOrientation(orientation);
        if (mZoomRenderer != null) {
            mZoomRenderer.setOrientation(orientation);
        }

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
        if ( mDeepZoomModeRect != null ) {
            if (orientation == 180) {
                mDeepzoomSetName.setRotation(180);
                mDeepZoomModeRect.setOrientation(0, false);
            } else {
                mDeepzoomSetName.setRotation(0);
                mDeepZoomModeRect.setOrientation(orientation, false);
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

    public void showFirstTimeHelp() {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mActivity);
        boolean isMenuShown = prefs.getBoolean(CameraSettings.KEY_SHOW_MENU_HELP, true);
        if(!isMenuShown) {
            showFirstTimeHelp(mTopMargin, mBottomMargin);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putBoolean(CameraSettings.KEY_SHOW_MENU_HELP, true);
            editor.apply();
        }
    }

    private void showFirstTimeHelp(int topMargin, int bottomMargin) {
        mMenuHelp = (MenuHelp) mRootView.findViewById(R.id.menu_help);
        mMenuHelp.setForCamera2(true);
        mMenuHelp.setVisibility(View.VISIBLE);
        mMenuHelp.setMargins(topMargin, bottomMargin);
        mMenuHelp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mMenuHelp != null) {
                    mMenuHelp.setVisibility(View.GONE);
                    mMenuHelp = null;
                }
            }
        });
    }

    @Override
    public void onSingleTapUp(View view, int x, int y) {
        mModule.onSingleTapUp(view, x, y);
    }

    @Override
    public void onLongPress(View view, int x, int y) {
        mModule.onLongPress(view, x, y);
    }

    public boolean isOverControlRegion(int[] xy) {
        int x = xy[0];
        int y = xy[1];
        return mCameraControls.isControlRegion(x, y);
    }

    public boolean isOverSurfaceView(int[] xy) {
        int x = xy[0];
        int y = xy[1];
        int[] surfaceViewLocation = new int[2];
        mSurfaceView.getLocationInWindow(surfaceViewLocation);
        int surfaceViewX = surfaceViewLocation[0];
        int surfaceViewY = surfaceViewLocation[1];
        xy[0] = x - surfaceViewX;
        xy[1] = y - surfaceViewY;
        return (x > surfaceViewX) && (x < surfaceViewX + mSurfaceView.getWidth())
                && (y > surfaceViewY) && (y < surfaceViewY + mSurfaceView.getHeight());
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
        if (mRenderOverlay != null && !mModule.isDeepPortraitMode()) {
            // this can not happen in capture mode
            mRenderOverlay.setVisibility(previewFocused ? View.VISIBLE : View.GONE);
        }
        if (mPieRenderer != null) {
            mPieRenderer.setBlockFocus(!previewFocused);
        }
        if (!previewFocused && mCountDownView != null) mCountDownView.cancelCountDown();
    }

    public boolean isShutterPressed() {
        return mShutterButton.isPressed();
    }

    public void pressShutterButton() {
        if (mShutterButton.isInTouchMode()) {
            mShutterButton.requestFocusFromTouch();
        } else {
            mShutterButton.requestFocus();
        }
        mShutterButton.setPressed(true);
    }

    public void setRecordingTime(String text) {
        mRecordingTimeView.setText(text);
    }

    public void setRecordingTimeTextColor(int color) {
        mRecordingTimeView.setTextColor(color);
    }

    public void resetPauseButton() {
        mRecordingTimeView.setCompoundDrawablesWithIntrinsicBounds(
                R.drawable.ic_recording_indicator, 0, 0, 0);
        mPauseButton.setPaused(false);
    }

    @Override
    public void onButtonPause() {
        mRecordingTimeView.setCompoundDrawablesWithIntrinsicBounds(
                R.drawable.ic_pausing_indicator, 0, 0, 0);
        mModule.onButtonPause();
    }

    @Override
    public void onButtonContinue() {
        mRecordingTimeView.setCompoundDrawablesWithIntrinsicBounds(
                R.drawable.ic_recording_indicator, 0, 0, 0);
        mModule.onButtonContinue();
    }

    @Override
    public void onSettingsChanged(List<SettingsManager.SettingState> settings) {
        for( SettingsManager.SettingState state : settings) {
            if (state.key.equals(SettingsManager.KEY_COLOR_EFFECT)) {
                enableView(mFilterModeSwitcher, SettingsManager.KEY_COLOR_EFFECT);
            } else if (state.key.equals(SettingsManager.KEY_SCENE_MODE)) {
                String value = mSettingsManager.getValue(SettingsManager.KEY_SCENE_MODE);
                if ( value.equals("104") ) {//panorama
                    mSceneModeLabelRect.setVisibility(View.GONE);
                }else{
                    if ( needShowInstructional() ) {
                        showSceneInstructionalDialog(mOrientation);
                    }
                    showSceneModeLabel();
                }
            }else if(state.key.equals(SettingsManager.KEY_FLASH_MODE) ) {
                enableView(mFlashButton, SettingsManager.KEY_FLASH_MODE);
            }else if (state.key.equals(SettingsManager.KEY_FOCUS_DISTANCE)) {
                if (mPieRenderer != null)
                    mPieRenderer.setVisible(false);
            }
        }
    }

    public void startSelfieFlash() {
        if (mSelfieView == null)
            mSelfieView = (SelfieFlashView) (mRootView.findViewById(R.id.selfie_flash));
        mSelfieView.bringToFront();
        mSelfieView.open();
        mScreenBrightness = setScreenBrightness(1F);
    }

    public void stopSelfieFlash() {
        if (mSelfieView == null)
            mSelfieView = (SelfieFlashView) (mRootView.findViewById(R.id.selfie_flash));
        mSelfieView.close();
        if (mScreenBrightness != 0.0f)
            setScreenBrightness(mScreenBrightness);
    }

    private float setScreenBrightness(float brightness) {
        float originalBrightness;
        Window window = mActivity.getWindow();
        WindowManager.LayoutParams layout = window.getAttributes();
        originalBrightness = layout.screenBrightness;
        layout.screenBrightness = brightness;
        window.setAttributes(layout);
        return originalBrightness;
    }

    public void hideSurfaceView() {
        mSurfaceView.setVisibility(View.INVISIBLE);
    }

    public void showSurfaceView() {
        Log.d(TAG, "showSurfaceView" + mPreviewWidth+" "+mPreviewHeight);
        mSurfaceView.getHolder().setFixedSize(mPreviewWidth, mPreviewHeight);
        mSurfaceView.setAspectRatio(mPreviewHeight, mPreviewWidth);
        mSurfaceView.setVisibility(View.VISIBLE);
        mIsVideoUI = false;
    }

    public boolean setPreviewSize(int width, int height) {
        Log.d(TAG, "setPreviewSize " + width + " " + height);
        boolean changed = (width != mPreviewWidth) || (height != mPreviewHeight);
        mPreviewWidth = width;
        mPreviewHeight = height;
        if (changed) {
            showSurfaceView();
        }
        return changed;
    }

    private class ZoomChangeListener implements ZoomRenderer.OnZoomChangedListener {
        @Override
        public void onZoomValueChanged(float mZoomValue) {
            mModule.onZoomChanged(mZoomValue);
            if (mZoomRenderer != null) {
                mZoomRenderer.setZoom(mZoomValue);
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
            mModule.onZoomEnd();
        }

        @Override
        public void onZoomValueChanged(int index) {

        }
    }

    private class DecodeTask extends AsyncTask<Void, Void, Bitmap> {
        private final byte [] mData;
        private int mOrientation;

        public DecodeTask(byte[] data, int orientation) {
            mData = data;
            mOrientation = orientation;
        }

        @Override
        protected Bitmap doInBackground(Void... params) {
            Bitmap bitmap = CameraUtil.downSample(mData, mDownSampleFactor);
            // Decode image in background.
            if ((mOrientation != 0) && (bitmap != null)) {
                Matrix m = new Matrix();
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

    private class DecodeImageForReview extends CaptureUI.DecodeTask {
        public DecodeImageForReview(byte[] data, int orientation) {
            super(data, orientation);
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

    public ImageView getVideoButton() {
        return mVideoButton;
    }

    public int getCurrentProMode() {
        return mCameraControls.getPromode();
    }

    public void swipeCameraMode(int move) {
        if (mIsVideoUI || !mModule.getCameraModeSwitcherAllowed() ||
                mModule.getCurrentIntentMode() != CaptureModule.INTENT_MODE_NORMAL) {
            return;
        }
        int index = mModule.getCurrentModeIndex() + move;
        int modeListSize = mModule.getCameraModeList().size();
        if (index >= modeListSize || index == -1) {
            return;
        }
        int mode = index % modeListSize;
        mModule.setCameraModeSwitcherAllowed(false);
        mCameraModeAdapter.setSelectedPosition(mode);
        mModeSelectLayout.smoothScrollToPosition(mode);
        mModule.selectCameraMode(mode);
    }

    public void switchToPhotoModeDueToError(boolean switchCamera) {
        int photoModeIndex = 1;
        List<String> modeList = mModule.getCameraModeList();
        for (; photoModeIndex < modeList.size(); photoModeIndex++) {
            if (modeList.get(photoModeIndex).equals(
                    mModule.getSelectableModes()[CaptureModule.CameraMode.DEFAULT.ordinal()])) {
                break;
            }
        }
        mCameraModeAdapter.setSelectedPosition(photoModeIndex);
        mModeSelectLayout.smoothScrollToPosition(photoModeIndex);
        if (switchCamera) {
            mModule.selectCameraMode(photoModeIndex);
        } else {
            mModule.setNextSceneMode(photoModeIndex);
        }
    }

}
