/*
 * Copyright (c) 2016-2017, The Linux Foundation. All rights reserved.
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

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraConstrainedHighSpeedCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.Face;
import android.hardware.camera2.params.InputConfiguration;
import android.hardware.camera2.params.MeteringRectangle;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.hardware.camera2.params.OutputConfiguration;
import android.location.Location;
import android.media.AudioManager;
import android.media.CamcorderProfile;
import android.media.CameraProfile;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.media.MediaRecorder;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.media.MediaRecorder;
import android.media.MediaCodecInfo;
import android.media.MediaCodecInfo.CodecCapabilities;
import android.media.MediaCodecInfo.VideoCapabilities;
import android.media.MediaCodecList;
import android.net.Uri;
import android.os.Bundle;
import android.os.Debug;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.os.Trace;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Range;
import android.util.Size;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.FrameLayout;
import android.graphics.Paint;
import android.graphics.Canvas;
import android.graphics.Color;
import android.util.AttributeSet;

import com.android.camera.data.Camera2ModeAdapter.OnItemClickListener;
import com.android.camera.deepportrait.CamGLRenderObserver;
import com.android.camera.deepportrait.CamGLRenderer;
import com.android.camera.deepportrait.DPImage;
import com.android.camera.deepportrait.GLCameraPreview;
import com.android.camera.exif.ExifInterface;
import com.android.camera.imageprocessor.filter.BlurbusterFilter;
import com.android.camera.imageprocessor.filter.ChromaflashFilter;
import com.android.camera.imageprocessor.filter.DeepPortraitFilter;
import com.android.camera.imageprocessor.filter.ImageFilter;
import com.android.camera.imageprocessor.PostProcessor;
import com.android.camera.imageprocessor.FrameProcessor;
import com.android.camera.PhotoModule.NamedImages;
import com.android.camera.PhotoModule.NamedImages.NamedEntity;
import com.android.camera.imageprocessor.filter.SharpshooterFilter;
import com.android.camera.imageprocessor.filter.StillmoreFilter;
import com.android.camera.imageprocessor.filter.UbifocusFilter;
import com.android.camera.ui.CountDownView;
import com.android.camera.ui.ModuleSwitcher;
import com.android.camera.ui.ProMode;
import com.android.camera.ui.RotateTextToast;
import com.android.camera.ui.TrackingFocusRenderer;
import com.android.camera.ui.TouchTrackFocusRenderer;
import com.android.camera.util.ApiHelper;
import com.android.camera.util.CameraUtil;
import com.android.camera.util.PersistUtil;
import com.android.camera.util.SettingTranslation;
import com.android.camera.util.AccessibilityUtils;
import com.android.camera.util.VendorTagUtil;

import org.codeaurora.snapcam.R;
import org.codeaurora.snapcam.filter.ClearSightImageProcessor;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Executor;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.concurrent.TimeoutException;

import androidx.heifwriter.HeifWriter;


public class CaptureModule implements CameraModule, PhotoController,
        MediaSaveService.Listener, ClearSightImageProcessor.Callback,
        SettingsManager.Listener, LocationManager.Listener,
        CountDownView.OnCountDownFinishedListener,
        MediaRecorder.OnErrorListener, MediaRecorder.OnInfoListener,
        CamGLRenderObserver {
    public static final int DUAL_MODE = 0;
    public static final int BAYER_MODE = 1;
    public static final int MONO_MODE = 2;
    public static final int SWITCH_MODE = 3;
    public static final int BAYER_ID = 0;
    public static int CURRENT_ID = 0;
    public static CameraMode CURRENT_MODE = CameraMode.DEFAULT;
    public static final int TYPE_DEFAULT = 0;
    public static final int TYPE_RTB = 1;
    public static final int TYPE_SAT = 2;
    public static final int TYPE_VR360 = 3;
    public static int MONO_ID = -1;
    public static int FRONT_ID = -1;
    public static int SWITCH_ID = -1;
    public static final int INTENT_MODE_NORMAL = 0;
    public static final int INTENT_MODE_CAPTURE = 1;
    public static final int INTENT_MODE_VIDEO = 2;
    public static final int INTENT_MODE_CAPTURE_SECURE = 3;
    public static final int INTENT_MODE_STILL_IMAGE_CAMERA = 4;
    private static final int BACK_MODE = 0;
    private static final int FRONT_MODE = 1;
    private static final int CANCEL_TOUCH_FOCUS_DELAY = PersistUtil.getCancelTouchFocusDelay();
    private static final int OPEN_CAMERA = 0;
    private static final int CANCEL_TOUCH_FOCUS = 1;
    private static final int MAX_NUM_CAM = 16;
    private static final int CONTOUR_POINTS_COUNT = 89;
    private String DEPTH_CAM_ID;
    private static final MeteringRectangle[] ZERO_WEIGHT_3A_REGION = new MeteringRectangle[]{
            new MeteringRectangle(0, 0, 0, 0, 0)};
    private static final String EXTRA_QUICK_CAPTURE =
            "android.intent.extra.quickCapture";
    private static final int SESSION_REGULAR = 0;
    private static final int SESSION_HIGH_SPEED = 1;
    /**
     * Camera state: Showing camera preview.
     */
    private static final int STATE_PREVIEW = 0;
    /**
     * Camera state: Waiting for the focus to be locked.
     */
    private static final int STATE_WAITING_AF_LOCK = 1;
    /**
     * Camera state: Waiting for the exposure to be precapture state.
     */
    private static final int STATE_WAITING_PRECAPTURE = 2;
    /**
     * Camera state: Waiting for the exposure state to be locked.
     */
    private static final int STATE_WAITING_AE_LOCK = 3;
    /**
     * Camera state: Picture was taken.
     */
    private static final int STATE_PICTURE_TAKEN = 4;
    /**
     * Camera state: Waiting for the touch-to-focus to converge.
     */
    private static final int STATE_WAITING_TOUCH_FOCUS = 5;
    /**
     * Camera state: Focus and exposure has been locked and converged.
     */
    private static final int STATE_AF_AE_LOCKED = 6;
    private static final int STATE_WAITING_AF_LOCKING = 7;
    private static final int STATE_WAITING_AF_AE_LOCK = 8;
    private static final int STATE_WAITING_AE_PRECAPTURE = 9;
    private static final String TAG = "SnapCam_CaptureModule";

    // Used for check memory status for longshot mode
    // Currently, this cancel threshold selection is based on test experiments,
    // we can change it based on memory status or other requirements.
    private static final int LONGSHOT_CANCEL_THRESHOLD = 40 * 1024 * 1024;

    private static final int NORMAL_SESSION_MAX_FPS = 60;
    private static final int HIGH_SESSION_MAX_FPS = 120;

    private static final int SCREEN_DELAY = 2 * 60 * 1000;

    private static final int mShotNum = PersistUtil.getLongshotShotLimit();
    private boolean mLongshoting = false;
    private AtomicInteger mNumFramesArrived = new AtomicInteger(0);
    private final int MAX_IMAGEREADERS = 10;

    private boolean mIsRTBCameraId = false;

    private SharedPreferences mGlobalSharedPref = null;
    private boolean mSupportT2TFocus = true;

    /** For temporary save warmstart gains and cct value*/
    private float mRGain = -1.0f;
    private float mGGain = -1.0f;
    private float mBGain = -1.0f;
    private float mCctAWB = -1.0f;
    private float[] mAWBDecisionAfterTC = new float[2];
    private float[] mAECSensitivity = new float[3];

    private long[] mAecFramecontrolExosureTime = new long[3];
    private float[] mAecFramecontrolLinearGain = new float[3];
    private float[] mAecFramecontrolSensitivity = new float[3];
    private float mAecFramecontrolLuxIndex = -1.0f;

    /** Add for EIS and FOVC Configuration */
    private int mStreamConfigOptMode = 0;
    private static final int STREAM_CONFIG_MODE_QTIEIS_REALTIME = 0xF004;
    private static final int STREAM_CONFIG_MODE_QTIEIS_LOOKAHEAD = 0xF008;
    private static final int STREAM_CONFIG_MODE_FOVC = 0xF010;
    private static final int STREAM_CONFIG_MODE_ZZHDR  = 0xF002;
    private static final int STREAM_CONFIG_MODE_FS2    =  0xF040;
    /** Add for SSM Configuration */
    private static final int STREAM_CONFIG_SSM = 0xF080;
    private int mCaptureCompleteCount = 0;
    private boolean mSSMCaptureCompleteFlag = false;

    public static final boolean DEBUG =
            (PersistUtil.getCamera2Debug() == PersistUtil.CAMERA2_DEBUG_DUMP_LOG) ||
            (PersistUtil.getCamera2Debug() == PersistUtil.CAMERA2_DEBUG_DUMP_ALL);

    private static final boolean BSGC_DEBUG = PersistUtil.getBsgcebug();
    private static final String BSGC_TAG = "BSGC";

    private static final String HFR_RATE = PersistUtil.getHFRRate();

    MeteringRectangle[][] mAFRegions = new MeteringRectangle[MAX_NUM_CAM][];
    MeteringRectangle[][] mAERegions = new MeteringRectangle[MAX_NUM_CAM][];
    MeteringRectangle[][] mT2TrackRegions = new MeteringRectangle[MAX_NUM_CAM][];
    CaptureRequest.Key<Byte> BayerMonoLinkEnableKey =
            new CaptureRequest.Key<>("org.codeaurora.qcamera3.dualcam_link_meta_data.enable",
                    Byte.class);
    CaptureRequest.Key<Byte> BayerMonoLinkMainKey =
            new CaptureRequest.Key<>("org.codeaurora.qcamera3.dualcam_link_meta_data.is_main",
                    Byte.class);
    CaptureRequest.Key<Integer> BayerMonoLinkSessionIdKey =
            new CaptureRequest.Key<>("org.codeaurora.qcamera3.dualcam_link_meta_data" +
                    ".related_camera_id", Integer.class);
    public static CameraCharacteristics.Key<Byte> MetaDataMonoOnlyKey =
            new CameraCharacteristics.Key<>("org.codeaurora.qcamera3.sensor_meta_data.is_mono_only",
                    Byte.class);
    public static CameraCharacteristics.Key<int[]> InstantAecAvailableModes =
            new CameraCharacteristics.Key<>("org.codeaurora.qcamera3.instant_aec.instant_aec_available_modes", int[].class);
    public static final CaptureRequest.Key<Integer> INSTANT_AEC_MODE =
            new CaptureRequest.Key<>("org.codeaurora.qcamera3.instant_aec.instant_aec_mode", Integer.class);
    public static final CaptureRequest.Key<Integer> SATURATION=
            new CaptureRequest.Key<>("org.codeaurora.qcamera3.saturation.use_saturation", Integer.class);
    public static final CaptureRequest.Key<Byte> histMode =
            new CaptureRequest.Key<>("org.codeaurora.qcamera3.histogram.enable", byte.class);
    public static final CaptureRequest.Key<Byte> bgStatsMode =
            new CaptureRequest.Key<>("org.codeaurora.qcamera3.bayer_grid.enable", byte.class);
    public static final CaptureRequest.Key<Byte> beStatsMode =
            new CaptureRequest.Key<>("org.codeaurora.qcamera3.bayer_exposure.enable", byte.class);

    public static CameraCharacteristics.Key<int[]> ISO_AVAILABLE_MODES =
            new CameraCharacteristics.Key<>("org.codeaurora.qcamera3.iso_exp_priority.iso_available_modes", int[].class);
    public static CameraCharacteristics.Key<long[]> EXPOSURE_RANGE =
            new CameraCharacteristics.Key<>("org.codeaurora.qcamera3.iso_exp_priority.exposure_time_range", long[].class);

    // manual WB color temperature and gains
    public static CameraCharacteristics.Key<int[]> WB_COLOR_TEMPERATURE_RANGE =
            new CameraCharacteristics.Key<>("org.codeaurora.qcamera3.manualWB.color_temperature_range", int[].class);
    public static CameraCharacteristics.Key<float[]> WB_RGB_GAINS_RANGE =
            new CameraCharacteristics.Key<>("org.codeaurora.qcamera3.manualWB.gains_range", float[].class);

    public static CameraCharacteristics.Key<Integer> buckets =
            new CameraCharacteristics.Key<>("org.codeaurora.qcamera3.histogram.buckets", Integer.class);
    public static CameraCharacteristics.Key<Integer> maxCount =
            new CameraCharacteristics.Key<>("org.codeaurora.qcamera3.histogram.max_count", Integer.class);
    public static CaptureResult.Key<int[]> histogramStats =
            new CaptureResult.Key<>("org.codeaurora.qcamera3.histogram.stats", int[].class);

    public static CaptureResult.Key<int[]> bgRStats =
	new CaptureResult.Key<>("org.codeaurora.qcamera3.bayer_grid.r_stats", int[].class);
    public static CaptureResult.Key<int[]> bgGStats =
	new CaptureResult.Key<>("org.codeaurora.qcamera3.bayer_grid.g_stats", int[].class);
    public static CaptureResult.Key<int[]> bgBStats =
	new CaptureResult.Key<>("org.codeaurora.qcamera3.bayer_grid.b_stats", int[].class);
    public static CaptureResult.Key<Integer> bgHeight =
        new CaptureResult.Key<>("org.codeaurora.qcamera3.bayer_grid.height", int.class);
    public static CaptureResult.Key<Integer> bgWidth =
        new CaptureResult.Key<>("org.codeaurora.qcamera3.bayer_grid.width", int.class);

    public static CaptureResult.Key<int[]> beRStats =
	new CaptureResult.Key<>("org.codeaurora.qcamera3.bayer_exposure.r_stats", int[].class);
    public static CaptureResult.Key<int[]> beGStats =
	new CaptureResult.Key<>("org.codeaurora.qcamera3.bayer_exposure.g_stats", int[].class);
    public static CaptureResult.Key<int[]> beBStats =
	new CaptureResult.Key<>("org.codeaurora.qcamera3.bayer_exposure.b_stats", int[].class);
    public static CaptureResult.Key<Integer> beHeight =
        new CaptureResult.Key<>("org.codeaurora.qcamera3.bayer_exposure.height", int.class);
    public static CaptureResult.Key<Integer> beWidth =
        new CaptureResult.Key<>("org.codeaurora.qcamera3.bayer_exposure.width", int.class);
    public static CaptureResult.Key<Float> roiBeX =
        new CaptureResult.Key<>("org.codeaurora.qcamera3.bayer_exposure.roi_be_x", Float.class);
    public static CaptureResult.Key<Float> roiBeY =
        new CaptureResult.Key<>("org.codeaurora.qcamera3.bayer_exposure.roi_be_y", Float.class);
    public static CaptureResult.Key<Float> roiBeWidth =
        new CaptureResult.Key<>("org.codeaurora.qcamera3.bayer_exposure.roi_be_width", Float.class);
    public static CaptureResult.Key<Float> roiBeHeight =
        new CaptureResult.Key<>("org.codeaurora.qcamera3.bayer_exposure.roi_be_height", Float.class);

    public static CaptureResult.Key<Byte> isHdr =
            new CaptureResult.Key<>("org.codeaurora.qcamera3.stats.is_hdr_scene", Byte.class);
    public static CameraCharacteristics.Key<Byte> IS_SUPPORT_QCFA_SENSOR =
            new CameraCharacteristics.Key<>("org.codeaurora.qcamera3.quadra_cfa.is_qcfa_sensor", Byte.class);
    public static CameraCharacteristics.Key<int[]> QCFA_SUPPORT_DIMENSION =
            new CameraCharacteristics.Key<>("org.codeaurora.qcamera3.quadra_cfa.qcfa_dimension", int[].class);
    public static CameraCharacteristics.Key<int[]> support_video_hdr_modes =
            new CameraCharacteristics.Key<>("org.codeaurora.qcamera3.available_video_hdr_modes.video_hdr_modes", int[].class);
    public static CameraCharacteristics.Key<Byte> logical_camera_type =
            new CameraCharacteristics.Key<>("org.codeaurora.qcamera3.logicalCameraType.logical_camera_type", Byte.class);
    public static CaptureRequest.Key<Integer> support_video_hdr_values =
            new CaptureRequest.Key<>("org.codeaurora.qcamera3.available_video_hdr_modes.video_hdr_values", Integer.class);
    public static CameraCharacteristics.Key<int[]> max_preview_size =
            new CameraCharacteristics.Key<>("org.quic.camera.MaxPreviewSize.MaxPreviewSize", int[].class);
    public static CameraCharacteristics.Key<Byte> is_burstshot_supported =
            new CameraCharacteristics.Key<>("org.quic.camera.BurstFPS.isBurstShotSupported", Byte.class);
    public static CameraCharacteristics.Key<Float> max_burstshot_fps =
            new CameraCharacteristics.Key<>("org.quic.camera.BurstFPS.MaxBurstShotFPS", Float.class);
    public static CameraCharacteristics.Key<Byte> is_liveshot_size_same_as_video =
            new CameraCharacteristics.Key<>("org.quic.camera.LiveshotSize.isLiveshotSizeSameAsVideoSize", Byte.class);
    public static CameraCharacteristics.Key<Byte> is_FD_Rendering_In_Video_UI_Supported =
            new CameraCharacteristics.Key<>("org.quic.camera.FDRendering.isFDRenderingInVideoUISupported", Byte.class);

    public static CameraCharacteristics.Key<Byte> bsgcAvailable =
            new CameraCharacteristics.Key<>("org.codeaurora.qcamera3.stats.bsgc_available", Byte.class);
    public static CaptureResult.Key<byte[]> blinkDetected =
            new CaptureResult.Key<>("org.codeaurora.qcamera3.stats.blink_detected", byte[].class);
    public static CaptureResult.Key<byte[]> blinkDegree =
            new CaptureResult.Key<>("org.codeaurora.qcamera3.stats.blink_degree", byte[].class);
    public static CaptureResult.Key<byte[]> smileDegree =
            new CaptureResult.Key<>("org.codeaurora.qcamera3.stats.smile_degree", byte[].class);
    public static CaptureResult.Key<byte[]> smileConfidence =
            new CaptureResult.Key<>("org.codeaurora.qcamera3.stats.smile_confidence", byte[].class);
    public static CaptureResult.Key<byte[]> gazeAngle =
            new CaptureResult.Key<>("org.codeaurora.qcamera3.stats.gaze_angle", byte[].class);
    public static CaptureResult.Key<int[]> gazeDirection =
            new CaptureResult.Key<>("org.codeaurora.qcamera3.stats.gaze_direction",
                    int[].class);
    public static CaptureResult.Key<byte[]> gazeDegree =
            new CaptureResult.Key<>("org.codeaurora.qcamera3.stats.gaze_degree",
                    byte[].class);
    public static CaptureResult.Key<int[]> contourPoints =
            new CaptureResult.Key<>("org.codeaurora.qcamera3.stats.contours",
                    int[].class);
    public static CaptureRequest.Key<Byte> facialContourEnable =
            new CaptureRequest.Key<>("org.codeaurora.qcamera3.facial_attr.contour_enable",
                    Byte.class);
    public static CaptureRequest.Key<Byte> smileEnable =
            new CaptureRequest.Key<>("org.codeaurora.qcamera3.facial_attr.smile_enable",
                    Byte.class);
    public static CaptureRequest.Key<Byte> gazeEnable =
            new CaptureRequest.Key<>("org.codeaurora.qcamera3.facial_attr.gaze_enable",
                    Byte.class);
    public static CaptureRequest.Key<Byte> blinkEnable =
            new CaptureRequest.Key<>("org.codeaurora.qcamera3.facial_attr.blink_enable",
                    Byte.class);

    public static CaptureResult.Key<Integer> ssmCaptureComplete =
            new CaptureResult.Key<>("com.qti.chi.superslowmotionfrc.CaptureComplete", Integer.class);
    public static CaptureResult.Key<Integer> ssmProcessingComplete =
            new CaptureResult.Key<>("com.qti.chi.superslowmotionfrc.ProcessingComplete", Integer.class);
    public static CaptureRequest.Key<Integer> ssmCaptureStart =
            new CaptureRequest.Key<>("com.qti.chi.superslowmotionfrc.CaptureStart", Integer.class);
    public static CaptureRequest.Key<Integer> ssmInterpFactor =
            new CaptureRequest.Key<>("com.qti.chi.superslowmotionfrc.InterpolationFactor", Integer.class);

    public static final CameraCharacteristics.Key<int[]> hfrFpsTable =
            new CameraCharacteristics.Key<>("org.quic.camera2.customhfrfps.info.CustomHFRFpsTable", int[].class);
    public static final CameraCharacteristics.Key<int[]> sensorModeTable  =
            new CameraCharacteristics.Key<>("org.quic.camera2.sensormode.info.SensorModeTable", int[].class);
    public static final CameraCharacteristics.Key<int[]> highSpeedVideoConfigs  =
            new CameraCharacteristics.Key<>("android.control.availableHighSpeedVideoConfigurations", int[].class);

    // AWB WarmStart gain and AWB WarmStart CCT
    private static final CaptureResult.Key<Float> awbFrame_control_rgain =
            new CaptureResult.Key<>("org.quic.camera2.statsconfigs.AWBFrameControlRGain", Float.class);
    private static final CaptureResult.Key<Float> awbFrame_control_ggain =
            new CaptureResult.Key<>("org.quic.camera2.statsconfigs.AWBFrameControlGGain", Float.class);
    private static final CaptureResult.Key<Float> awbFrame_control_bgain =
            new CaptureResult.Key<>("org.quic.camera2.statsconfigs.AWBFrameControlBGain", Float.class);
    private static final CaptureResult.Key<Integer> awbFrame_control_cct =
            new CaptureResult.Key<>("org.quic.camera2.statsconfigs.AWBFrameControlCCT", Integer.class);
    private static final CaptureResult.Key<float[]> awbFrame_decision_after_tc =
            new CaptureResult.Key<>("org.quic.camera2.statsconfigs.AWBDecisionAfterTC", float[].class);

    private static final CaptureRequest.Key<Float[]> awbWarmStart_gain =
            new CaptureRequest.Key<>("org.quic.camera2.statsconfigs.AWBWarmstartGain", Float[].class);
    private static final CaptureRequest.Key<Float> awbWarmStart_cct =
            new CaptureRequest.Key<>("org.quic.camera2.statsconfigs.AWBWarmstartCCT", Float.class);
    private static final CaptureRequest.Key<Float[]> awbWarmStart_decision_after_tc =
            new CaptureRequest.Key<>("org.quic.camera2.statsconfigs.AWBDecisionAfterTC", Float[].class);

    //AEC warm start
    private static final CaptureResult.Key<float[]> aec_sensitivity =
            new CaptureResult.Key<>("org.quic.camera2.statsconfigs.AECSensitivity", float[].class);

    private static final CaptureRequest.Key<Float[]> aec_start_up_sensitivity =
            new CaptureRequest.Key<>("org.quic.camera2.statsconfigs.AECStartUpSensitivity", Float[].class);
    private static final CaptureResult.Key<long[]> aec_frame_control_exposure_time =
            new CaptureResult.Key<>("org.quic.camera2.statsconfigs.AECExposureTime", long[].class);
    private static final CaptureResult.Key<float[]> aec_frame_control_linear_gain =
            new CaptureResult.Key<>("org.quic.camera2.statsconfigs.AECLinearGain", float[].class);
    private static final CaptureResult.Key<float[]> aec_frame_control_sensitivity =
            new CaptureResult.Key<>("org.quic.camera2.statsconfigs.AECSensitivity", float[].class);
    private static final CaptureResult.Key<Float> aec_frame_control_lux_index =
            new CaptureResult.Key<>("org.quic.camera2.statsconfigs.AECLuxIndex", Float.class);

    public static final CaptureRequest.Key<Integer> sharpness_control = new CaptureRequest.Key<>(
            "org.codeaurora.qcamera3.sharpness.strength", Integer.class);
    public static final CaptureRequest.Key<Integer> exposure_metering = new CaptureRequest.Key<>(
            "org.codeaurora.qcamera3.exposure_metering.exposure_metering_mode", Integer.class);
    public static final CaptureRequest.Key<Byte> eis_mode =
            new CaptureRequest.Key<>("org.quic.camera.eis3enable.EISV3Enable", byte.class);
    public static final CaptureRequest.Key<Byte> recording_end_stream =
            new CaptureRequest.Key<>("org.quic.camera.recording.endOfStream", byte.class);
    public static final CaptureRequest.Key<Integer> earlyPCR =
            new CaptureRequest.Key<>("org.codeaurora.qcamera3.sessionParameters.numPCRsBeforeStreamOn", Integer.class);
    public static final CaptureRequest.Key<Byte> mctf =
            new CaptureRequest.Key<>("org.codeaurora.qcamera3.sessionParameters.enableMCTFwithReferenceFrame", byte.class);
    private static final CaptureResult.Key<Byte> is_depth_focus =
            new CaptureResult.Key<>("org.quic.camera.isDepthFocus.isDepthFocus", byte.class);
    private static final CaptureRequest.Key<Byte> capture_burst_fps =
            new CaptureRequest.Key<>("org.quic.camera.BurstFPS.burstfps", byte.class);
    private static final CaptureRequest.Key<Byte> custom_noise_reduction =
            new CaptureRequest.Key<>("org.quic.camera.CustomNoiseReduction.CustomNoiseReduction", byte.class);

    public static final CameraCharacteristics.Key<int[]> eis_config_table = new CameraCharacteristics.Key<>(
            "org.quic.camera2.VideoConfigurations.info.VideoConfigurationsTable",int[].class);
    public static final CameraCharacteristics.Key<Byte> is_camera_fd_supported = new CameraCharacteristics.Key<>(
            "org.quic.camera.FDRendering.isFDRenderingInCameraUISupported",byte.class);

    public static final CaptureRequest.Key<Byte> sensor_mode_fs =
            new CaptureRequest.Key<>("org.quic.camera.SensorModeFS ", byte.class);
    public static CameraCharacteristics.Key<Byte> fs_mode_support =
            new CameraCharacteristics.Key<>("org.quic.camera.SensorModeFS.isFastShutterModeSupported", Byte.class);

    public static final CameraCharacteristics.Key<Byte> heic_support_enable =
            new CameraCharacteristics.Key<>("org.quic.camera.HEICSupport.HEICEnabled",Byte.class);

    // Touch Track Focus
    public static final CaptureRequest.Key<Byte> t2t_enable = new CaptureRequest.Key<>(
            "org.quic.camera2.objectTrackingConfig.Enable", Byte.class);
    public static final CaptureRequest.Key<int[]> t2t_register_roi = new CaptureRequest.Key<>(
            "org.quic.camera2.objectTrackingConfig.RegisterROI", int[].class);
    public static final CaptureRequest.Key<Integer> t2t_cmd_trigger = new CaptureRequest.Key<>(
            "org.quic.camera2.objectTrackingConfig.CmdTrigger", Integer.class);

    public static final CameraCharacteristics.Key<Byte> is_t2t_supported =
            new CameraCharacteristics.Key<>(
                    "org.quic.camera2.objectTrackingResults.TrackerEnable", byte.class);
    private static final CaptureResult.Key<Integer> t2t_tracker_status =
            new CaptureResult.Key<>("org.quic.camera2.objectTrackingResults.TrackerStatus", Integer.class);
    private static final CaptureResult.Key<int[]> t2t_tracker_result_roi =
            new CaptureResult.Key<>("org.quic.camera2.objectTrackingResults.ResultROI", int[].class);
    private static final CaptureResult.Key<Integer> t2t_tracker_score =
            new CaptureResult.Key<>("org.quic.camera2.objectTrackingResults.TrackerScore", Integer.class);
    public static final CameraCharacteristics.Key<Integer> mfnr_type =
            new CameraCharacteristics.Key<>("org.quic.camera.swcapabilities.MFNRType", Integer.class);
    public static final CameraCharacteristics.Key<Byte> swmctf =
            new CameraCharacteristics.Key<>("org.quic.camera.swcapabilities.SWMCTFEnable", byte.class);

    private TouchTrackFocusRenderer mT2TFocusRenderer;
    private boolean mIsDepthFocus = false;
    private boolean[] mTakingPicture = new boolean[MAX_NUM_CAM];
    private int mControlAFMode = CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE;
    private int mLastResultAFState = -1;
    private boolean isFlashRequiredInDriver = false;
    private Rect[] mCropRegion = new Rect[MAX_NUM_CAM];
    private Rect[] mOriginalCropRegion = new Rect[MAX_NUM_CAM];
    private boolean mAutoFocusRegionSupported;
    private boolean mAutoExposureRegionSupported;
    // The degrees of the device rotated clockwise from its natural orientation.
    private int mOrientation = OrientationEventListener.ORIENTATION_UNKNOWN;
    /*Histogram variables*/
    private Camera2GraphView mGraphViewR,mGraphViewGB,mGraphViewB;
    private Camera2BGBitMap    bgstats_view;
    private Camera2BEBitMap    bestats_view;
    private TextView mBgStatsLabel;
    private TextView mBeStatsLabel;
    private DrawAutoHDR2 mDrawAutoHDR2;
    public boolean mAutoHdrEnable;
    private MFNRDrawer mMFNRDrawer;
    public boolean mMFNREnable;
    /*HDR Test*/
    private boolean mCaptureHDRTestEnable = false;
    boolean mHiston    = false;
    boolean mBGStatson = false;
    boolean mBEStatson = false;
    private boolean mFirstTimeInitialized;
    private boolean mCamerasOpened = false;
    private boolean mIsLinked = false;
    private long mCaptureStartTime;
    private boolean mPaused = true;
    private boolean mIsSupportedQcfa = false;
    private Semaphore mSurfaceReadyLock = new Semaphore(1);
    private boolean mSurfaceReady = true;
    private boolean[] mCameraOpened = new boolean[MAX_NUM_CAM];
    private CameraDevice[] mCameraDevice = new CameraDevice[MAX_NUM_CAM];
    private String[] mCameraId = new String[MAX_NUM_CAM];
    private String[] mSelectableModes = {"Video", "HFR", "Photo", "Bokeh", "SAT", "ProMode"};
    private ArrayList<SceneModule> mSceneCameraIds = new ArrayList<>();
    private SceneModule mCurrentSceneMode;
    private int mNextModeIndex = 1;
    private int mCurrentModeIndex = 1;
    private int mLastT2tTrackState = -1;
    private int mT2TTrackState = 0;
    public enum CameraMode {
        VIDEO,
        HFR,
        DEFAULT,
        RTB,
        SAT,
        PRO_MODE
    }

    public enum MFNRSupportValues {
        NoMFNRSupport, ///< No MFNR Support
        HWMFNRSupport,     ///< Hardware MFNR Support
        SWMFNRSupport,     ///< Software MFNR Support
    }

    private View mRootView;
    private CaptureUI mUI;
    private CameraActivity mActivity;
    private float mZoomValue = 1f;
    private FocusStateListener mFocusStateListener;
    private LocationManager mLocationManager;
    private SettingsManager mSettingsManager;
    private long SECONDARY_SERVER_MEM;
    private boolean mLongshotActive = false;
    private long mLastLongshotTimestamp = 0;
    private CameraCharacteristics mMainCameraCharacteristics;
    private int mDisplayRotation;
    private int mDisplayOrientation;
    private boolean mIsRefocus = false;
    private int mChosenImageFormat;
    private Toast mToast;

    private boolean mStartRecPending = false;
    private boolean mStopRecPending = false;

    boolean mUnsupportedResolution = false;
    private boolean mExistAWBVendorTag = true;
    private boolean mExistAECWarmTag = true;
    private boolean mExistAECFrameControlTag = true;

    private static final long SDCARD_SIZE_LIMIT = 4000 * 1024 * 1024L;
    private static final String sTempCropFilename = "crop-temp";
    private static final int REQUEST_CROP = 1000;
    private int mIntentMode = INTENT_MODE_NORMAL;
    private boolean mIsVoiceTakePhote = false;
    private String mCropValue;
    private Uri mCurrentVideoUri;
    private boolean mTempHoldVideoInVideoIntent = false;
    private boolean mCurrentSessionClosed = false;
    private ParcelFileDescriptor mVideoFileDescriptor;
    private Uri mSaveUri;
    private boolean mQuickCapture;
    private byte[] mJpegImageData;
    private boolean mSaveRaw = false;
    private boolean mSupportZoomCapture = true;
    private long mStartRecordingTime;
    private long mStopRecordingTime;

    private int mLastAeState = -1;
    private int mLastAfState = -1;
    private boolean mIsCanceled = false;
    private boolean mIsAutoFocusStarted = false;
    private boolean mIsAutoFlash = false;
    private int mSetAePrecaptureTriggerIdel = 0;

    /**
     * A {@link CameraCaptureSession } for camera preview.
     */
    private CameraCaptureSession[] mCaptureSession = new CameraCaptureSession[MAX_NUM_CAM];
    /**
     * An additional thread for running tasks that shouldn't block the UI.
     */
    private HandlerThread mCameraThread;
    private HandlerThread mImageAvailableThread;
    private HandlerThread mCaptureCallbackThread;
    private HandlerThread mMpoSaveThread;

    /**
     * A {@link Handler} for running tasks in the background.
     */
    private PostProcessor mPostProcessor;
    private FrameProcessor mFrameProcessor;
    private CaptureResult mPreviewCaptureResult;
    private Face[] mPreviewFaces = null;
    private Face[] mStickyFaces = null;
    private ExtendedFace[] mExFaces = null;
    private ExtendedFace[] mStickyExFaces = null;
    private Rect mBayerCameraRegion;
    private Handler mCameraHandler;
    private Handler mImageAvailableHandler;
    private Handler mCaptureCallbackHandler;
    private Handler mMpoSaveHandler;

    /**
     * An {@link ImageReader} that handles still image capture.
     */
    private ImageReader[] mImageReader = new ImageReader[MAX_NUM_CAM];
    private ImageReader[] mRawImageReader = new ImageReader[MAX_NUM_CAM];
    private boolean mYUVCallback = false;
    private int mYUVCount = 3;
    private Size[] mYUVsize = new Size[mYUVCount];
    private ImageReader[] mYUVImageReader = new ImageReader[3];
    private HeifWriter mInitHeifWriter;
    private OutputConfiguration mHeifOutput;
    private HeifImage mHeifImage;
    private HeifWriter mLiveShotInitHeifWriter;
    private OutputConfiguration mLiveShotOutput;
    private HeifImage mLiveShotImage;
    private NamedImages mNamedImages;
    private ContentResolver mContentResolver;
    private byte[] mLastJpegData;
    private int mJpegFileSizeEstimation;
    private boolean mFirstPreviewLoaded;
    private int[] mPrecaptureRequestHashCode = new int[MAX_NUM_CAM];
    private int[] mLockRequestHashCode = new int[MAX_NUM_CAM];
    private final Handler mHandler = new MainHandler();
    private CameraCaptureSession mCurrentSession;
    private Size mPreviewSize;
    private Size mPictureSize;
    private Size mVideoPreviewSize;
    private Size mVideoSize;
    private Size mVideoSnapshotSize;
    private Size mPictureThumbSize;
    private Size mVideoSnapshotThumbSize;

    private MediaRecorder mMediaRecorder;
    private boolean mIsRecordingVideo;
    private boolean mIsPreviewingVideo;
    private boolean mNeedSetupMediaRecorder;
    // The video duration limit. 0 means no limit.
    private int mMaxVideoDurationInMs;
    private boolean mIsMute = false;
    // Default 0. If it is larger than 0, the camcorder is in time lapse mode.
    private int mTimeBetweenTimeLapseFrameCaptureMs = 0;
    private boolean mCaptureTimeLapse = false;
    private CamcorderProfile mProfile;
    private static final int CLEAR_SCREEN_DELAY = 4;
    private static final int UPDATE_RECORD_TIME = 5;
    private static final int VOICE_INTERACTION_CAPTURE = 7;
    private ContentValues mCurrentVideoValues;
    private String mVideoFilename;
    private boolean mMediaRecorderPausing = false;
    private boolean mMediaRecorderStarted = false;
    private long mRecordingStartTime;
    private long mRecordingTotalTime;
    private boolean mRecordingTimeCountsDown = false;
    private ImageReader mVideoSnapshotImageReader;
    private Range mHighSpeedFPSRange;
    private boolean mHighSpeedCapture = false;
    private boolean mHighSpeedRecordingMode = false; //HFR-false HSR or SSM-true
    private int mHighSpeedCaptureRate;
    private boolean mSuperSlomoCapture = false;
    private int mInterpFactor = 1;
    private CaptureRequest.Builder mVideoRecordRequestBuilder;
    private CaptureRequest.Builder mVideoPreviewRequestBuilder;
    private Surface mVideoPreviewSurface;
    private Surface mMediaRecorderSurface;
    private boolean mCameraModeSwitcherAllowed = true;

    private static final int STATS_DATA = 768;
    public static int statsdata[] = new int[STATS_DATA];

    private boolean mInTAF = false;

    // BG stats
    private static final int BGSTATS_DATA = 16*16;
    public static final int SCALE_STATS = 20;
    public static final int BGSTATS_WIDTH = 16*SCALE_STATS;
    public static final int BGSTATS_HEIGHT = 16*SCALE_STATS;
    public static int bg_statsdata[]   = new int[BGSTATS_DATA*SCALE_STATS*SCALE_STATS];
    public static int bg_r_statsdata[] = new int[BGSTATS_DATA];
    public static int bg_g_statsdata[] = new int[BGSTATS_DATA];
    public static int bg_b_statsdata[] = new int[BGSTATS_DATA];
    public static String bgstatsdata_string = new String();

    // BE stats
    private static final int BESTATS_DATA = 16*16;
    public static final int BESTATS_WIDTH = 16*SCALE_STATS;
    public static final int BESTATS_HEIGHT = 16*SCALE_STATS;
    public static int be_statsdata[]   = new int[BESTATS_DATA*SCALE_STATS*SCALE_STATS];
    public static int be_r_statsdata[] = new int[BESTATS_DATA];
    public static int be_g_statsdata[] = new int[BESTATS_DATA];
    public static int be_b_statsdata[] = new int[BESTATS_DATA];

    // AWB Info
    private static String[] awbinfo_data = new String[4];
    // AEC Info
    private static String[] aecinfo_data = new String[10];

    private static final int SELFIE_FLASH_DURATION = 680;
    private static final int SESSION_CONFIGURE_TIMEOUT_MS = 3000;

    private SoundClips.Player mSoundPlayer;
    private Size mSupportedMaxPreviewSize;
    private Size mSupportedMaxPictureSize;
    private Size mSupportedRawPictureSize;

    private long mIsoExposureTime;
    private int mIsoSensitivity;

    private CamGLRenderer mRenderer;
    private boolean mDeepPortraitMode = false;
    private boolean mIsCloseCamera = true;

    private boolean mLockAFAE = false;
    private TextView mLockAFAEText;
    private int[] mClickPosition = new int[2];

    private class SelfieThread extends Thread {
        public void run() {
            try {
                Thread.sleep(SELFIE_FLASH_DURATION);
                mActivity.runOnUiThread(new Runnable() {
                    public void run() {
                        takePicture();
                    }
                });
            } catch(InterruptedException e) {
            }
            selfieThread = null;
        }
    }
    private SelfieThread selfieThread;

    private class MediaSaveNotifyThread extends Thread {
        private Uri uri;

        public MediaSaveNotifyThread(Uri uri) {
            this.uri = uri;
        }

        public void setUri(Uri uri) {
            this.uri = uri;
        }

        public void run() {
            mActivity.runOnUiThread(new Runnable() {
                public void run() {
                    if (uri != null)
                        mActivity.notifyNewMedia(uri);
                    mActivity.updateStorageSpaceAndHint();
                    if (mLastJpegData != null) mActivity.updateThumbnail(mLastJpegData);
                }
            });
            mediaSaveNotifyThread = null;
        }
    }

    public void updateThumbnailJpegData(byte[] jpegData) {
        mLastJpegData = jpegData;
    }

    private MediaSaveNotifyThread mediaSaveNotifyThread;

    private final MediaSaveService.OnMediaSavedListener mOnVideoSavedListener =
            new MediaSaveService.OnMediaSavedListener() {
                @Override
                public void onMediaSaved(Uri uri) {
                    if (uri != null) {
                        mActivity.notifyNewMedia(uri);
                        mCurrentVideoUri = uri;
                    }
                }
            };

    private MediaSaveService.OnMediaSavedListener mOnMediaSavedListener =
            new MediaSaveService.OnMediaSavedListener() {
                @Override
                public void onMediaSaved(Uri uri) {
                    if (mLongshotActive) {
                        if (mediaSaveNotifyThread == null) {
                            mediaSaveNotifyThread = new MediaSaveNotifyThread(uri);
                            mediaSaveNotifyThread.start();
                        } else
                            mediaSaveNotifyThread.setUri(uri);
                    } else {
                        if (uri != null) {
                            mActivity.notifyNewMedia(uri);
                        }
                    }
                }
            };

    public MediaSaveService.OnMediaSavedListener getMediaSavedListener() {
        return mOnMediaSavedListener;
    }

    static abstract class ImageAvailableListener implements ImageReader.OnImageAvailableListener {
        int mCamId;

        ImageAvailableListener(int cameraId) {
            mCamId = cameraId;
        }
    }

    static abstract class CameraCaptureCallback extends CameraCaptureSession.CaptureCallback {
        int mCamId;

        CameraCaptureCallback(int cameraId) {
            mCamId = cameraId;
        }
    }

    /**
     * {@link CaptureRequest.Builder} for the camera preview
     */
    private CaptureRequest.Builder[] mPreviewRequestBuilder = new CaptureRequest.Builder[MAX_NUM_CAM];
    /**
     * The current state of camera state for taking pictures.
     *
     * @see #mCaptureCallback
     */
    private int[] mState = new int[MAX_NUM_CAM];
    /**
     * A {@link Semaphore} make sure the camera open callback happens first before closing the
     * camera.
     */
    private Semaphore mCameraOpenCloseLock = new Semaphore(1);


    public Face[] getPreviewFaces() {
        return mPreviewFaces;
    }

    public Face[] getStickyFaces() {
        return mStickyFaces;
    }

    public CaptureResult getPreviewCaptureResult() {
        return mPreviewCaptureResult;
    }

    public Rect getCameraRegion() {
        return mBayerCameraRegion;
    }

    private void detectHDRMode(CaptureResult result, int id) {
        String value = mSettingsManager.getValue(SettingsManager.KEY_SCENE_MODE);
        String autoHdr = mSettingsManager.getValue(SettingsManager.KEY_AUTO_HDR);
        Byte hdrScene = result.get(CaptureModule.isHdr);
        if (value == null || hdrScene == null) return;
        mAutoHdrEnable = false;
        if (autoHdr != null && "enable".equals(autoHdr) && "0".equals(value) && hdrScene == 1) {
            mAutoHdrEnable = true;
            mActivity.runOnUiThread(new Runnable() {
                public void run() {
                    if (mDrawAutoHDR2 != null) {
                        mDrawAutoHDR2.setVisibility(View.VISIBLE);
                        mDrawAutoHDR2.AutoHDR();
                    }
                }
            });
            return;
        } else {
            mActivity.runOnUiThread( new Runnable() {
                public void run () {
                    if (mDrawAutoHDR2 != null) {
                        mDrawAutoHDR2.setVisibility (View.INVISIBLE);
                    }
                }
            });
        }
    }

    /**
     * A {@link CameraCaptureSession.CaptureCallback} that handles events related to JPEG capture.
     */
    private CameraCaptureSession.CaptureCallback mCaptureCallback
            = new CameraCaptureSession.CaptureCallback() {

        private void processCaptureResult(CaptureResult result) {
            int id = (int) result.getRequest().getTag();

            if (!mFirstPreviewLoaded) {
                mActivity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mUI.hidePreviewCover();
                    }
                });
                mFirstPreviewLoaded = true;
            }
            if (id == getMainCameraId()) {
                mPreviewCaptureResult = result;
            }

            if (isSSMEnabled()) {
                updateCaptureStateMachine(id, result);
                Integer ssmStatus = result.get(ssmCaptureComplete);

                if (ssmStatus != null) {
                    Log.d(TAG, "ssmStatus: CaptureComplete is " + ssmStatus);
                    updateProgressBar(true);
                }
                Integer procComplete = result.get(ssmProcessingComplete);

                if (procComplete != null && ++mCaptureCompleteCount == 1) {
                    Log.d(TAG, "ssmStatus: ProcessingComplete is " + procComplete);
                    mCaptureCompleteCount = 0;
                    mSSMCaptureCompleteFlag = true;
                    mActivity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            stopRecordingVideo(getMainCameraId());
                        }
                    });
                }
            }
        }

        @Override
        public void onCaptureProgressed(CameraCaptureSession session,
                                        CaptureRequest request,
                                        CaptureResult partialResult) {
            int id = (int) partialResult.getRequest().getTag();
            if (id == getMainCameraId()) {
                Face[] faces = partialResult.get(CaptureResult.STATISTICS_FACES);
                if (BSGC_DEBUG)
                    Log.d(BSGC_TAG,"onCaptureProgressed Detected Face size = " + Integer.toString(faces == null? 0 : faces.length));
                if (faces != null && mSettingsManager.isFDRenderingAtPreview()){
                    if (isBsgcDetecionOn() || isFacialContourOn() || isFacePointOn()){
                        updateFaceView(faces, getBsgcInfo(partialResult, faces.length));
                    } else {
                        updateFaceView(faces, null);
                    }
                }
            }
        }

        @Override
        public void onCaptureCompleted(CameraCaptureSession session,
                                       CaptureRequest request,
                                       TotalCaptureResult result) {
            int id = (int) result.getRequest().getTag();
            if (id == getMainCameraId()) {
                updateFocusStateChange(result);
                updateAWBCCTAndgains(result);
                updateAECGainAndExposure(result);
                Face[] faces = result.get(CaptureResult.STATISTICS_FACES);
                if (BSGC_DEBUG)
                    Log.d(BSGC_TAG,"onCaptureCompleted Detected Face size = " + Integer.toString(faces == null? 0 : faces.length));
                if (faces != null && mSettingsManager.isFDRenderingAtPreview()){
                    if (isBsgcDetecionOn() || isFacialContourOn() || isFacePointOn()){
                        updateFaceView(faces, getBsgcInfo(result, faces.length));
                    } else {
                        updateFaceView(faces, null);
                    }
                }
                updateT2tTrackerView(result);
            }

            if (mPaused) {
                return;
            }
            detectHDRMode(result, id);
            processCaptureResult(result);
            mPostProcessor.onMetaAvailable(result);
            String stats_visualizer = mSettingsManager.getValue(
                    SettingsManager.KEY_STATS_VISUALIZER_VALUE);
            if (stats_visualizer != null) {
                updateStatsView(stats_visualizer,result);
            } else {
                mUI.updateAWBInfoVisibility(View.GONE);
                mUI.updateAECInfoVisibility(View.GONE);
                updateGraghViewVisibility(View.GONE);
                updateBGStatsVisibility(View.GONE);
                updateBEStatsVisibility(View.GONE);
            }
        }
    };

    private void updateT2TTracking() {
        mT2TFocusRenderer.setOriginalCameraBound(
                mSettingsManager.getSensorActiveArraySize(getMainCameraId()));
        mT2TFocusRenderer.setMirror(mSettingsManager.isFacingFront(getMainCameraId()));
        mT2TFocusRenderer.setDisplayOrientation(mDisplayOrientation);
        mT2TFocusRenderer.setCameraBound(mCropRegion[getMainCameraId()]);
    }

    private void updateTouchFocusState(int t2tTrigger) {
        int id = getMainCameraId();
        try {
            if (mPreviewRequestBuilder[id] != null) {
                mPreviewRequestBuilder[id].set(CaptureModule.t2t_cmd_trigger, t2tTrigger);
                if (mCurrentSceneMode.mode == CameraMode.HFR && mCurrentSession != null &&
                        mCurrentSession instanceof CameraConstrainedHighSpeedCaptureSession) {
                    if (mCurrentSession != null) {
                        List requestList = ((CameraConstrainedHighSpeedCaptureSession) mCurrentSession)
                                .createHighSpeedRequestList(mPreviewRequestBuilder[id].build());
                        if (mCurrentSession != null) {
                            mCurrentSession.setRepeatingBurst(requestList, mCaptureCallback,
                                    mCameraHandler);
                        }
                    }
                } else {
                    if (mCurrentSession != null) {
                        mCurrentSession.setRepeatingRequest(mPreviewRequestBuilder[id].build(),
                                mCaptureCallback, mCameraHandler);
                    }
                }
                if (DEBUG) {
                    Log.v(TAG, "updateTouchFocusState is called");
                }
            }
        } catch (CameraAccessException | IllegalStateException e) {
            e.printStackTrace();
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        }
    }

    private void updateSupportT2TFocus(CaptureResult result) {
        if (mGlobalSharedPref == null) {
            mGlobalSharedPref = mActivity.getSharedPreferences(
                    ComboPreferences.getGlobalSharedPreferencesName(mActivity),
                    Context.MODE_PRIVATE);

        }
        int isSupport = mGlobalSharedPref.getInt(SettingsManager.KEY_SUPPORT_T2T_FOCUS, -1);
        if (isSupport == -1) {
            try {
                result.get(t2t_tracker_status);
            } catch (IllegalArgumentException e) {
                mSupportT2TFocus = false;
            }
            SharedPreferences.Editor editor = mGlobalSharedPref.edit();
            editor.putInt(SettingsManager.KEY_SUPPORT_T2T_FOCUS,
                    (mSupportT2TFocus ? SettingsManager.TOUCH_TRACK_FOCUS_ENABLE :
                            SettingsManager.TOUCH_TRACK_FOCUS_DISABLE));
            editor.apply();
        }
    }

    private void updateT2tTrackerView(CaptureResult result) {
        int[] resultROI = null;
        int trackerScore = -1;
        if (mT2TFocusRenderer == null) {
            mT2TFocusRenderer = getT2TFocusRenderer();
            updateT2TTracking();
        }
        updateSupportT2TFocus(result);
        if (mT2TFocusRenderer.isShown()) {
            updateT2TTracking();
            try{
                if (result.get(t2t_tracker_status) != null) {
                    mT2TTrackState = result.get(t2t_tracker_status);
                    if (mLastT2tTrackState != mT2TTrackState) {
                        if (mT2TTrackState >= TouchTrackFocusRenderer.TRACKER_CMD_REG) {
                            // as long as State is 1 (registering) or 2 (registered) or 3 (tracking)
                            // we should be able to turn the "trigger" back to 0 (SYNC)
                            updateTouchFocusState(TouchTrackFocusRenderer.TRACKER_CMD_SYNC);
                        }
                        mLastT2tTrackState = mT2TTrackState;
                    }
                }
                if (result.get(t2t_tracker_score) != null) {
                    trackerScore = result.get(t2t_tracker_score);
                }
                if (result.get(t2t_tracker_result_roi) != null) {
                    resultROI = result.get(t2t_tracker_result_roi);
                    mT2TFocusRenderer.updateTrackerRect(resultROI, trackerScore);
                }
                if(DEBUG) {
                    Log.v(TAG, "updateT2tTrackerView mT2TTrackState :" + mT2TTrackState +
                            ", trackerScore :" +trackerScore);
                    Log.v(TAG, "updateT2tTrackerView resultROI :" + resultROI);
                }
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
            }
        }
    }

    private void updateStatsView(String stats_visualizer,CaptureResult result) {
        int r, g, b, index;
        if (stats_visualizer.contains("2")) {
            int[] histogramStats = result.get(CaptureModule.histogramStats);
            if (histogramStats != null && mHiston) {
                    /*The first element in the array stores max hist value . Stats data begin
                    from second value*/
                synchronized (statsdata) {
                    System.arraycopy(histogramStats, 0, statsdata, 0, STATS_DATA);
                }
                updateGraghView();
            }
        }

        // BG stats display
        if (stats_visualizer.contains("0")) {
            int[] bgRStats = null;
            int[] bgGStats = null;
            int[] bgBStats = null;
            try{
                bgRStats = result.get(CaptureModule.bgRStats);
                bgGStats = result.get(CaptureModule.bgGStats);
                bgBStats = result.get(CaptureModule.bgBStats);

            } catch (IllegalArgumentException e) {
                e.printStackTrace();
            }
            if (bgRStats != null && bgGStats != null && bgBStats != null && mBGStatson) {
                synchronized (bg_r_statsdata) {
                    System.arraycopy(bgRStats, 0, bg_r_statsdata, 0, BGSTATS_DATA);
                    System.arraycopy(bgGStats, 0, bg_g_statsdata, 0, BGSTATS_DATA);
                    System.arraycopy(bgBStats, 0, bg_b_statsdata, 0, BGSTATS_DATA);

                    for (int el = 0; el < 256; el++) {

                        r = bg_r_statsdata[el];
                        g = bg_g_statsdata[el];
                        b = bg_b_statsdata[el];

                        for (int hi = 0; hi < SCALE_STATS; hi++) {
                            for (int wi = 0; wi < SCALE_STATS; wi++) {
                                index = SCALE_STATS * (int) (el / 16) + 16 * SCALE_STATS * hi + 16 * SCALE_STATS * SCALE_STATS * (el % 16) + wi;
                                bg_statsdata[index] = Color.argb(255, r, g, b);
                            }
                        }
                    }
                }
                updateBGStatsView();
            }
        }

        // BE stats display
        if (stats_visualizer.contains("1")) {
            int[] beRStats = null;
            int[] beGStats = null;
            int[] beBStats = null;
            float norm_roi_x = 0.0f;
            float norm_roi_y = 0.0f;
            float norm_roi_dx = 0.0f;
            float norm_roi_dy = 0.0f;
            try {
                beRStats = result.get(CaptureModule.beRStats);
                beGStats = result.get(CaptureModule.beGStats);
                beBStats = result.get(CaptureModule.beBStats);

                norm_roi_x = result.get(CaptureModule.roiBeX);
                norm_roi_y = result.get(CaptureModule.roiBeY);
                norm_roi_dx = result.get(CaptureModule.roiBeWidth);
                norm_roi_dy = result.get(CaptureModule.roiBeHeight);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "there is no vendor roiBeX/roiBeY/roiBeWidth/roiBeHeight");
            }

            if (beRStats != null && beGStats != null && beBStats != null && mBEStatson) {
                synchronized (be_r_statsdata) {
                    System.arraycopy(beRStats, 0, be_r_statsdata, 0, BESTATS_DATA);
                    System.arraycopy(beGStats, 0, be_g_statsdata, 0, BESTATS_DATA);
                    System.arraycopy(beBStats, 0, be_b_statsdata, 0, BESTATS_DATA);

                    int roi_x = (int)(norm_roi_x * 16.0f);
                    int roi_y = (int)(norm_roi_y * 16.0f);
                    int roi_w = (int)((norm_roi_x + norm_roi_dx) * 16.0f);
                    int roi_h = (int)((norm_roi_y + norm_roi_dy) * 16.0f);

                    for (int el = 0; el < 256; el++) {
                        r = be_r_statsdata[el];
                        g = be_g_statsdata[el];
                        b = be_b_statsdata[el];

                        for (int hi = 0; hi < SCALE_STATS; hi++) {
                            for (int wi = 0; wi < SCALE_STATS; wi++) {
                                index = SCALE_STATS * (int) (el / 16) + 16 * SCALE_STATS * hi + 16 * SCALE_STATS * SCALE_STATS * (el % 16) + wi;
                                be_statsdata[index] = Color.argb(255, r, g, b);
                                if (roi_w > 0 && roi_h > 0 &&
                                        ((el % 16 == roi_x && el / 16 >= roi_y && el / 16 <= roi_h)
                                                || (el % 16 == roi_w && el / 16 >= roi_y && el / 16 <= roi_h) ||
                                                (el / 16 == roi_y && el % 16 >= roi_x && el % 16 <= roi_w) ||
                                                (el / 16 == roi_h && el % 16 >= roi_x && el % 16 <= roi_w))) {
                                    // red color for ROI border
                                    be_statsdata[index] = Color.argb(255, 255, 0, 0);
                                }
                            }
                        }
                    }
                }
                updateBEStatsView();
            }
        }

        // AWB Info display
        if (stats_visualizer.contains("3")) {
            try{
                awbinfo_data[0] = Float.toString(mRGain);
                awbinfo_data[1] = Float.toString(mGGain);
                awbinfo_data[2] = Float.toString(mBGain);
                awbinfo_data[3] = Float.toString(mCctAWB);
                synchronized (awbinfo_data) {
                    mActivity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mUI.updateAWBInfoVisibility(View.VISIBLE);
                            mUI.updateAwbInfoText(awbinfo_data);
                        }
                    });
                }
            } catch (IllegalArgumentException | NullPointerException e) {
                e.printStackTrace();
            }
        } else {
            mUI.updateAWBInfoVisibility(View.GONE);
        }

        // AEC Info display
        if (stats_visualizer.contains("4")) {
            if (mAecFramecontrolLinearGain != null &&
                    mAecFramecontrolSensitivity != null &&
                    mAecFramecontrolExosureTime != null) {
                try {
                    aecinfo_data[0] = Float.toString(mAecFramecontrolLuxIndex);
                    aecinfo_data[1] = String.format("%.5f", mAecFramecontrolLinearGain[0]);
                    aecinfo_data[2] = String.format("%.5f", mAecFramecontrolLinearGain[1]);
                    aecinfo_data[3] = String.format("%.5f", mAecFramecontrolLinearGain[2]);
                    aecinfo_data[4] = String.format("%.2E", mAecFramecontrolSensitivity[0]);
                    aecinfo_data[5] = String.format("%.2E", mAecFramecontrolSensitivity[1]);
                    aecinfo_data[6] = String.format("%.2E", mAecFramecontrolSensitivity[2]);
                    aecinfo_data[7] = Long.toString(mAecFramecontrolExosureTime[0]);
                    aecinfo_data[8] = Long.toString(mAecFramecontrolExosureTime[1]);
                    aecinfo_data[9] = Long.toString(mAecFramecontrolExosureTime[2]);
                    synchronized (aecinfo_data) {
                        mActivity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                mUI.updateAECInfoVisibility(View.VISIBLE);
                                mUI.updateAecInfoText(aecinfo_data);
                            }
                        });
                    }
                } catch (IllegalArgumentException e) {
                    e.printStackTrace();
                }
            }
        } else {
            mUI.updateAECInfoVisibility(View.GONE);
        }
    }


    private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(CameraDevice cameraDevice) {
            int id = Integer.parseInt(cameraDevice.getId());
            Log.d(TAG, "onOpened " + id);
            mCameraOpenCloseLock.release();
            if (mPaused) {
                return;
            }

            mCameraDevice[id] = cameraDevice;
            mCameraOpened[id] = true;

            if (isBackCamera() && getCameraMode() == DUAL_MODE && id == BAYER_ID) {
                Message msg = mCameraHandler.obtainMessage(OPEN_CAMERA, MONO_ID, 0);
                mCameraHandler.sendMessage(msg);
            } else {
                mCamerasOpened = true;
                mActivity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mUI.onCameraOpened(mCurrentSceneMode.getCurrentId());
                    }
                });
                createSessions();
            }
        }

        @Override
        public void onDisconnected(CameraDevice cameraDevice) {
            int id = Integer.parseInt(cameraDevice.getId());
            Log.d(TAG, "onDisconnected " + id);
            cameraDevice.close();
            mCameraDevice[id] = null;
            mCameraOpenCloseLock.release();
            mCamerasOpened = false;
            mIsCloseCamera = true;
        }

        @Override
        public void onError(CameraDevice cameraDevice, int error) {
            int id = Integer.parseInt(cameraDevice.getId());
            Log.e(TAG, "onError " + id + " " + error);
            mCameraOpenCloseLock.release();
            mCamerasOpened = false;

            if (null != mActivity) {
                Toast.makeText(mActivity,"open camera error id =" + id,
                        Toast.LENGTH_LONG).show();
                mActivity.finish();
            }
        }

        @Override
        public void onClosed(CameraDevice cameraDevice) {
            int id = Integer.parseInt(cameraDevice.getId());
            Log.d(TAG, "onClosed " + id);
            mCameraDevice[id] = null;
            mCameraOpenCloseLock.release();
            mCamerasOpened = false;
            mIsCloseCamera = true;
        }

    };

    private void updateCaptureStateMachine(int id, CaptureResult result) {
        Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);
        Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);

        if (afState == null) {
            return;
        }

        switch (mState[id]) {
            case STATE_PREVIEW: {
                break;
            }
            case STATE_WAITING_AF_LOCK: {
                Log.d(TAG, "STATE_WAITING_AF_LOCK id: " + id + " afState:" + afState + " aeState:" + aeState);
                // AF_PASSIVE is added for continous auto focus mode
                if (CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED == afState ||
                        CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED == afState ||
                        CaptureResult.CONTROL_AF_STATE_PASSIVE_FOCUSED == afState ||
                        CaptureResult.CONTROL_AF_STATE_PASSIVE_UNFOCUSED == afState ||
                        (mLockRequestHashCode[id] == result.getRequest().hashCode() &&
                                afState == CaptureResult.CONTROL_AF_STATE_INACTIVE)) {
                    if(id == MONO_ID && getCameraMode() == DUAL_MODE && isBackCamera()) {
                        // in dual mode, mono AE dictated by bayer AE.
                        // if not already locked, wait for lock update from bayer
                        if(aeState == CaptureResult.CONTROL_AE_STATE_LOCKED)
                            checkAfAeStatesAndCapture(id);
                        else
                            mState[id] = STATE_WAITING_AE_LOCK;
                    } else {
                        if ((mLockRequestHashCode[id] == result.getRequest().hashCode()) || (mLockRequestHashCode[id] == 0)) {

                            // CONTROL_AE_STATE can be null on some devices
                            if(aeState == null || (aeState == CaptureResult
                                    .CONTROL_AE_STATE_CONVERGED) && isFlashOff(id)) {
                                lockExposure(id);
                            } else {
                                runPrecaptureSequence(id);
                            }
                        }
                    }
                } else if (mLockRequestHashCode[id] == result.getRequest().hashCode()){
                    Log.i(TAG, "AF lock request result received, but not focused");
                    mLockRequestHashCode[id] = 0;
                } else if (mSettingsManager.isFixedFocus(id)) {
                    // CONTROL_AE_STATE can be null on some devices
                    if(aeState == null || (aeState == CaptureResult
                             .CONTROL_AE_STATE_CONVERGED) && isFlashOff(id)) {
                        lockExposure(id);
                    } else {
                        runPrecaptureSequence(id);
                    }
                }
                break;
            }
            case STATE_WAITING_PRECAPTURE: {
                // CONTROL_AE_STATE can be null on some devices
                Log.d(TAG, "STATE_WAITING_PRECAPTURE id: " + id + " afState: " + afState + " aeState:" + aeState);
                if (aeState == null ||
                        aeState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE ||
                        aeState == CaptureResult.CONTROL_AE_STATE_FLASH_REQUIRED ||
                        aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
                    if ((mPrecaptureRequestHashCode[id] == result.getRequest().hashCode()) || (mPrecaptureRequestHashCode[id] == 0)) {
                        if (mLongshotActive && isFlashOn(id)) {
                            checkAfAeStatesAndCapture(id);
                        } else {
                            lockExposure(id);
                        }
                    }
                } else if (aeState == null ||
                        aeState == CaptureResult.CONTROL_AE_STATE_INACTIVE || aeState == CaptureResult.CONTROL_AE_STATE_LOCKED) {
                    // AE Mode is OFF, the AE state is always CONTROL_AE_STATE_INACTIVE
                    // then begain capture and ignore lock AE.
                    checkAfAeStatesAndCapture(id);
                } else if (mPrecaptureRequestHashCode[id] == result.getRequest().hashCode()) {
                    Log.i(TAG, "AE trigger request result received, but not converged");
                    mPrecaptureRequestHashCode[id] = 0;
                }
                break;
            }
            case STATE_WAITING_AE_LOCK: {
                // CONTROL_AE_STATE can be null on some devices
                Log.d(TAG, "STATE_WAITING_AE_LOCK id: " + id + " afState: " + afState + " aeState:" + aeState);
                if(mLockAFAE){
                    mState[id] = STATE_AF_AE_LOCKED;
                    break;
                }
                if ((aeState == null || aeState == CaptureResult.CONTROL_AE_STATE_LOCKED)) {
                    checkAfAeStatesAndCapture(id);
                }
                break;
            }
            case STATE_AF_AE_LOCKED: {
                Log.d(TAG, "STATE_AF_AE_LOCKED id: " + id + " afState:" + afState + " aeState:" + aeState);
                break;
            }
            case STATE_WAITING_TOUCH_FOCUS: {
                Log.d(TAG, "STATE_WAITING_TOUCH_FOCUS id: " + id + " afState:" + afState + " aeState:" + aeState);
                try {
                    if (mIsAutoFocusStarted) {
                        if (mIsCanceled && mSetAePrecaptureTriggerIdel == 1) {
                            Log.i(TAG, "STATE_WAITING_TOUCH_FOCUS SET CONTROL_AE_PRECAPTURE_TRIGGER_IDLE");
                            mPreviewRequestBuilder[id].set(
                                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_IDLE);
                            mCaptureSession[id].setRepeatingRequest(
                                    mPreviewRequestBuilder[id].build(), mCaptureCallback,
                                    mCameraHandler);
                            mSetAePrecaptureTriggerIdel = 0;
                        }
                        if (mPreviewRequestBuilder[id] != null && mLastAeState != -1
                                && (mLastAeState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE
                                && (aeState == CaptureResult.CONTROL_AE_STATE_FLASH_REQUIRED
                                || aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED))
                                && mIsAutoFlash
                                && !mIsCanceled) {

                            Log.i(TAG, "SET CONTROL_AE_PRECAPTURE_TRIGGER_CANCEL START");
                            mPreviewRequestBuilder[id].set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_CANCEL);
                            mCaptureSession[id].setRepeatingRequest(mPreviewRequestBuilder[id].build(),
                                    mCaptureCallback, mCameraHandler);
                            mSetAePrecaptureTriggerIdel++;
                            mIsCanceled = true;
                            Log.i(TAG, "SET CONTROL_AE_PRECAPTURE_TRIGGER_CANCEL END");

                        }
                    }
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
                break;
            }
            case STATE_WAITING_AF_LOCKING: {
                parallelLockFocusExposure(getMainCameraId());
                break;
            }
            case STATE_WAITING_AE_PRECAPTURE: {
                Log.d(TAG, "STATE_WAITING_AE_PRECAPTURE id: " + id + " aeState:" + aeState);
                if (aeState == null ||
                        aeState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE ||
                        aeState == CaptureResult.CONTROL_AE_STATE_FLASH_REQUIRED ||
                        aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
                    parallelLockFocusExposure(getMainCameraId());
                }
                break;
            }
            case STATE_WAITING_AF_AE_LOCK: {
                Log.d(TAG, "STATE_WAITING_AF_AE_LOCK id: " + id + " afState: " + afState +
                        " aeState:" + aeState);
                if ((aeState == null || aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED)) {
                    if (isFlashOn(id)) {
                        // if flash is on and AE state is CONVERGED then lock AE
                        lockExposure(id);
                    }
                }
                if ((CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED == afState ||
                        CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED == afState) &&
                        (aeState == null || aeState == CaptureResult.CONTROL_AE_STATE_LOCKED)) {
                    checkAfAeStatesAndCapture(id);
                } else if (mSettingsManager.isFixedFocus(id)) {
                    // CONTROL_AE_STATE can be null on some devices
                    if(aeState == null || (aeState == CaptureResult
                            .CONTROL_AE_STATE_CONVERGED) && isFlashOff(id)) {
                        lockExposure(id);
                    } else {
                        runPrecaptureSequence(id);
                    }
                }
                break;
            }
        }
        if (aeState == null) {
            return;
        }
        if (aeState == null) {
            mLastAeState = -1;
        } else {
            mLastAeState = aeState;
        }
    }

    private void checkAfAeStatesAndCapture(int id) {
        if(mPaused || !mCamerasOpened) {
            return;
        }
        if(isBackCamera() && getCameraMode() == DUAL_MODE) {
            mState[id] = STATE_AF_AE_LOCKED;
            try {
                // stop repeating request once we have AF/AE lock
                // for mono when mono preview is off.
                if(id == MONO_ID && !canStartMonoPreview()) {
                    mCaptureSession[id].stopRepeating();
                }
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }

            if(mState[BAYER_ID] == STATE_AF_AE_LOCKED &&
                    mState[MONO_ID] == STATE_AF_AE_LOCKED) {
                mState[BAYER_ID] = STATE_PICTURE_TAKEN;
                mState[MONO_ID] = STATE_PICTURE_TAKEN;
                captureStillPicture(BAYER_ID);
                captureStillPicture(MONO_ID);
            }
        } else {
            mState[id] = STATE_PICTURE_TAKEN;
            captureStillPicture(id);
            captureStillPictureForHDRTest(id);
        }
    }

    private void captureStillPictureForHDRTest(int id) {
        String scene = mSettingsManager.getValue(SettingsManager.KEY_SCENE_MODE);
        if (SettingsManager.getInstance().isCamera2HDRSupport()
                && scene != null && scene.equals("18")){
            mCaptureHDRTestEnable = true;
            captureStillPicture(id);
        }
        mCaptureHDRTestEnable = false;
    }

    private boolean canStartMonoPreview() {
        return getCameraMode() == MONO_MODE ||
                (getCameraMode() == DUAL_MODE && isMonoPreviewOn());
    }

    private boolean isMonoPreviewOn() {
        String value = mSettingsManager.getValue(SettingsManager.KEY_MONO_PREVIEW);
        if (value == null) return false;
        if (value.equals("on")) return true;
        else return false;
    }

    public boolean isBackCamera() {
        String value = mSettingsManager.getValue(SettingsManager.KEY_FRONT_REAR_SWITCHER_VALUE);
        if (value == null) return true;
        return value.equals("rear");
    }

    private boolean isBackCameraId() {
        final SharedPreferences pref = mActivity.getSharedPreferences(
                ComboPreferences.getGlobalSharedPreferencesName(mActivity), Context.MODE_PRIVATE);
        String value = pref.getString(SettingsManager.KEY_FRONT_REAR_SWITCHER_VALUE, null);
        if (value == null) return true;
        return value.equals("rear");
    }

    public int getCameraMode() {
        String value = mSettingsManager.getValue(SettingsManager.KEY_SCENE_MODE);
        if (value != null && value.equals(SettingsManager.SCENE_MODE_DUAL_STRING)) return DUAL_MODE;
        value = mSettingsManager.getValue(SettingsManager.KEY_MONO_ONLY);
        if (value == null || !value.equals("on")) return BAYER_MODE;
        return MONO_MODE;
    }

    private boolean isClearSightOn() {
        String value = mSettingsManager.getValue(SettingsManager.KEY_CLEARSIGHT);
        if (value == null) return false;
        return isBackCamera() && getCameraMode() == DUAL_MODE && value.equals("on");
    }

    private boolean isBsgcDetecionOn() {
        String value = mSettingsManager.getValue(SettingsManager.KEY_BSGC_DETECTION);
        if (value == null) return false;
        return  value.equals("enable");
    }

    private boolean isFacialContourOn() {
        String value = mSettingsManager.getValue(SettingsManager.KEY_FACIAL_CONTOUR);
        if (value == null) return false;
        return  value.equals("enable");
    }

    private boolean isFacePointOn() {
        String value = mSettingsManager.getValue(SettingsManager.KEY_FACE_DETECTION_MODE);
        if (value == null) return false;
        return  value.equals(String.valueOf(CaptureRequest.STATISTICS_FACE_DETECT_MODE_FULL));
    }

    private boolean isRawCaptureOn() {
        String value = mSettingsManager.getValue(SettingsManager.KEY_SAVERAW);
        if (value == null) return  false;
        return value.equals("enable");
    }

    public boolean isDeepPortraitMode() {
        String value = mSettingsManager.getValue(SettingsManager.KEY_SCENE_MODE);
        if (value == null) return false;
        return Integer.valueOf(value) == SettingsManager.SCENE_MODE_DEEPPORTRAIT_INT;
    }

    private boolean isMpoOn() {
        String value = mSettingsManager.getValue(SettingsManager.KEY_MPO);
        if (value == null) return false;
        return isBackCamera() && getCameraMode() == DUAL_MODE && value.equals("on");
    }

    public static int getQualityNumber(String jpegQuality) {
        if (jpegQuality == null) {
            return 85;
        }
        try {
            int qualityPercentile = Integer.parseInt(jpegQuality);
            if (qualityPercentile >= 0 && qualityPercentile <= 100)
                return qualityPercentile;
            else
                return 85;
        } catch (NumberFormatException nfe) {
            //chosen quality is not a number, continue
        }
        int value = 0;
        switch (jpegQuality) {
            case "superfine":
                value = CameraProfile.QUALITY_HIGH;
                break;
            case "fine":
                value = CameraProfile.QUALITY_MEDIUM;
                break;
            case "normal":
                value = CameraProfile.QUALITY_LOW;
                break;
            default:
                return 85;
        }
        return CameraProfile.getJpegEncodingQualityParameter(value);
    }

    public CamGLRenderer getCamGLRender() {
        return  mRenderer;
    }

    public GLCameraPreview getGLCameraPreview() {
        return  mUI.getGLCameraPreview();
    }

    public LocationManager getLocationManager() {
        return mLocationManager;
    }

    private void initializeFirstTime() {
        if (mFirstTimeInitialized || mPaused) {
            return;
        }

        //Todo: test record location. Jack to provide instructions
        // Initialize location service.
        boolean recordLocation = getRecordLocation();
        mLocationManager.recordLocation(recordLocation);

        mUI.initializeFirstTime();
        MediaSaveService s = mActivity.getMediaSaveService();
        // We set the listener only when both service and shutterbutton
        // are initialized.
        if (s != null) {
            s.setListener(this);
            if (isClearSightOn()) {
                ClearSightImageProcessor.getInstance().setMediaSaveService(s);
            }
        }

        mNamedImages = new NamedImages();
        mGraphViewR = (Camera2GraphView) mRootView.findViewById(R.id.graph_view_r);
        mGraphViewGB = (Camera2GraphView) mRootView.findViewById(R.id.graph_view_gb);
        mGraphViewB = (Camera2GraphView) mRootView.findViewById(R.id.graph_view_b);
        bgstats_view = (Camera2BGBitMap) mRootView.findViewById(R.id.bg_stats_graph);
        bestats_view = (Camera2BEBitMap) mRootView.findViewById(R.id.be_stats_graph);
        mBgStatsLabel = (TextView) mRootView.findViewById(R.id.bg_stats_graph_label);
        mBeStatsLabel = (TextView) mRootView.findViewById(R.id.be_stats_graph_label);
        mDrawAutoHDR2 = (DrawAutoHDR2 )mRootView.findViewById(R.id.autohdr_view);
        mMFNRDrawer = (MFNRDrawer )mRootView.findViewById(R.id.mfnr_view);
        mLockAFAEText = (TextView ) mRootView.findViewById(R.id.lock_af_ae_label);

        mGraphViewR.setDataSection(0,256);
        mGraphViewGB.setDataSection(256,512);
        mGraphViewB.setDataSection(512,768);
        if (mGraphViewR != null){
            mGraphViewR.setCaptureModuleObject(this);
        }
        if (mGraphViewGB != null){
            mGraphViewGB.setCaptureModuleObject(this);
        }
        if (mGraphViewB != null){
            mGraphViewB.setCaptureModuleObject(this);
        }
        if (bgstats_view != null){
            bgstats_view.setCaptureModuleObject(this);
        }
        if (bestats_view != null){
            bestats_view.setCaptureModuleObject(this);
        }
        if (mDrawAutoHDR2 != null) {
            mDrawAutoHDR2.setCaptureModuleObject(this);
        }
        if (mMFNRDrawer != null) {
            mMFNRDrawer.setCaptureModuleObject(this);
        }

        mFirstTimeInitialized = true;
    }

    private void initializeSecondTime() {
        // Start location update if needed.
        boolean recordLocation = getRecordLocation();
        mLocationManager.recordLocation(recordLocation);
        MediaSaveService s = mActivity.getMediaSaveService();
        if (s != null) {
            s.setListener(this);
            if (isClearSightOn()) {
                ClearSightImageProcessor.getInstance().setMediaSaveService(s);
            }
        }
        mNamedImages = new NamedImages();
    }

    public ArrayList<ImageFilter> getFrameFilters() {
        if(mFrameProcessor == null) {
            return new ArrayList<ImageFilter>();
        } else {
            return mFrameProcessor.getFrameFilters();
        }
    }

    private void applyFocusDistance(CaptureRequest.Builder builder, String value) {
        if (value == null) return;
        float valueF = Float.valueOf(value);
        if (valueF < 0) return;
        builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF);
        builder.set(CaptureRequest.LENS_FOCUS_DISTANCE, valueF);
    }

    private void createSessions() {
        if(PersistUtil.isTraceEnable())
            Trace.beginSection("createSessions");
        if (mPaused || !mCamerasOpened || mTempHoldVideoInVideoIntent) return;
        final int cameraId = mCurrentSceneMode.getCurrentId();
        Log.d(TAG,"createSessions : Current SceneMode is "+mCurrentSceneMode.mode);
        switch (mCurrentSceneMode.mode) {
            case VIDEO:
                createSessionForVideo(cameraId);
                break;
            case HFR:
                if (!HFR_RATE.equals("")) {
                    mSettingsManager.setValue(SettingsManager.KEY_VIDEO_HIGH_FRAME_RATE, HFR_RATE);
                }
                createSessionForVideo(cameraId);
                break;
            default:
                createSession(cameraId);
        }
        if (PersistUtil.isTraceEnable())
            Trace.endSection();
    }

    private CaptureRequest.Builder getRequestBuilder(int id) throws CameraAccessException {
        CaptureRequest.Builder builder;
        if(mPostProcessor.isZSLEnabled() && id == getMainCameraId()) {
            builder = mCameraDevice[id].createCaptureRequest(CameraDevice.TEMPLATE_ZERO_SHUTTER_LAG);
        } else {
            builder = mCameraDevice[id].createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
        }

        if (builder != null){
            applySessionParameters(builder);
        }

        return builder;
    }

    private CaptureRequest.Builder getRequestBuilder(int templateType,int id)
            throws CameraAccessException{
        CaptureRequest.Builder builder = null;
        builder = mCameraDevice[id].createCaptureRequest(
                    templateType);
        if (builder != null){
            applySessionParameters(builder);
        }
        return builder;
    }

    private void waitForPreviewSurfaceReady() {
        try {
            if (!mSurfaceReady) {
                if (!mSurfaceReadyLock.tryAcquire(2000, TimeUnit.MILLISECONDS)) {
                    if (mPaused) {
                        Log.d(TAG, "mPaused status occur Time out waiting for surface.");
                        throw new IllegalStateException("Paused Time out waiting for surface.");
                    } else {
                        Log.d(TAG, "Time out waiting for surface.");
                        throw new RuntimeException("Time out waiting for surface.");
                    }
                }
                mSurfaceReadyLock.release();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void
    updatePreviewSurfaceReadyState(boolean rdy) {
        if (rdy != mSurfaceReady) {
            if (rdy) {
                Log.i(TAG, "Preview Surface is ready!");
                mSurfaceReadyLock.release();
                mSurfaceReady = true;
            } else {
                try {
                    Log.i(TAG, "Preview Surface is not ready!");
                    mSurfaceReady = false;
                    mSurfaceReadyLock.acquire();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public class DetachClickListener implements DialogInterface.OnClickListener {

        private DialogInterface.OnClickListener mDelegate;

        public DetachClickListener(DialogInterface.OnClickListener delegate) {
            this.mDelegate = delegate;
        }

        @Override
        public void onClick(DialogInterface dialog, int which) {
            if (mDelegate != null) {
                mDelegate.onClick(dialog, which);
            }
        }

        public void clearOnDetach(AlertDialog dialog) {
            dialog.getWindow()
                    .getDecorView()
                    .getViewTreeObserver()
                    .addOnWindowAttachListener(new ViewTreeObserver.OnWindowAttachListener() {
                        @Override
                        public void onWindowAttached() {
                        }

                        @Override
                        public void onWindowDetached() {
                            mDelegate = null;
                        }
                    });
        }
    }
    private void createSession(final int id) {
        Log.d(TAG, "createSession,id: " + id + ",mPaused:" + mPaused + ",mCameraOpened:" + !mCameraOpened[id] + ",mCameraDevice:"+ (mCameraDevice[id] == null));
        if (mPaused || !mCameraOpened[id] || (mCameraDevice[id] == null)) return;
        List<Surface> list = new LinkedList<Surface>();
        mState[id] = STATE_PREVIEW;
        mControlAFMode = CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE;
        try {
            // We set up a CaptureRequest.Builder with the output Surface.
            mPreviewRequestBuilder[id] = getRequestBuilder(id);
            mPreviewRequestBuilder[id].setTag(id);

            CameraCaptureSession.StateCallback captureSessionCallback =
                    new CameraCaptureSession.StateCallback() {

                        @Override
                        public void onConfigured(CameraCaptureSession cameraCaptureSession) {
                            if (mPaused || null == mCameraDevice[id] ||
                                    cameraCaptureSession == null) {
                                return;
                            }
                            Log.i(TAG, "cameracapturesession - onConfigured "+ id);
                            setCameraModeSwitcherAllowed(true);
                            // When the session is ready, we start displaying the preview.
                            mCaptureSession[id] = cameraCaptureSession;
                            if(id == getMainCameraId()) {
                                mCurrentSession = cameraCaptureSession;
                            }
                            initializePreviewConfiguration(id);
                            setDisplayOrientation();
                            updateFaceDetection();
                            try {
                                if (isBackCamera() && getCameraMode() == DUAL_MODE) {
                                    linkBayerMono(id);
                                    mIsLinked = true;
                                }
                                // Finally, we start displaying the camera preview.
                                // for cases where we are in dual mode with mono preview off,
                                // don't set repeating request for mono
                                if(mCaptureSession[id] == null){
                                    return;
                                }
                                if(id == MONO_ID && !canStartMonoPreview()
                                        && getCameraMode() == DUAL_MODE) {
                                    mCaptureSession[id].capture(mPreviewRequestBuilder[id]
                                            .build(), mCaptureCallback, mCameraHandler);
                                } else {
                                    mCaptureSession[id].setRepeatingRequest(mPreviewRequestBuilder[id]
                                            .build(), mCaptureCallback, mCameraHandler);
                                }
                                if (mIntentMode == INTENT_MODE_STILL_IMAGE_CAMERA &&
                                        mIsVoiceTakePhote) {
                                    mHandler.sendEmptyMessageDelayed(VOICE_INTERACTION_CAPTURE, 500);
                                }
                                if (isClearSightOn()) {
                                    ClearSightImageProcessor.getInstance().onCaptureSessionConfigured(id == BAYER_ID, cameraCaptureSession);
                                } else if (mChosenImageFormat == ImageFormat.PRIVATE && id == getMainCameraId()) {
                                    mPostProcessor.onSessionConfigured(mCameraDevice[id], mCaptureSession[id]);
                                }

                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            } catch(IllegalStateException e) {
                                e.printStackTrace();
                            } catch (IllegalArgumentException e) {
                                e.printStackTrace();
                            }
                            mCurrentSessionClosed = false;
                        }

                        @Override
                        public void onConfigureFailed(CameraCaptureSession cameraCaptureSession) {
                            Log.e(TAG, "cameracapturesession - onConfigureFailed "+ id);
                            setCameraModeSwitcherAllowed(true);
                            if (mActivity.isFinishing()) {
                                return;
                            }
                            Toast.makeText(mActivity, "Camera Initialization Failed",
                                    Toast.LENGTH_SHORT).show();
                        }

                        @Override
                        public void onClosed(CameraCaptureSession session) {
                            Log.d(TAG, "cameracapturesession - onClosed");
                            setCameraModeSwitcherAllowed(true);
                        }
                    };

            Surface surface = null;
            try {
                waitForPreviewSurfaceReady();
            } catch (RuntimeException e) {
                Log.v(TAG,
                        "createSession: normal status occur Time out waiting for surface ");
            }
            surface = getPreviewSurfaceForSession(id);

            if(id == getMainCameraId()) {
                mFrameProcessor.setOutputSurface(surface);
                mFrameProcessor.setVideoOutputSurface(null);
            }

            if(isClearSightOn()) {
                if (surface != null) {
                    mPreviewRequestBuilder[id].addTarget(surface);
                    list.add(surface);
                }
                ClearSightImageProcessor.getInstance().createCaptureSession(
                        id == BAYER_ID, mCameraDevice[id], list, captureSessionCallback);
            } else if (id == getMainCameraId()) {
                if(mFrameProcessor.isFrameFilterEnabled() && !mDeepPortraitMode) {
                    mActivity.runOnUiThread(new Runnable() {
                        public void run() {
                            SurfaceHolder surfaceHolder = mUI.getSurfaceHolder();
                            if (surfaceHolder != null) {
                                surfaceHolder.setFixedSize(
                                        mPreviewSize.getHeight(), mPreviewSize.getWidth());
                            }
                        }
                    });
                }
                List<Surface> surfaces = mFrameProcessor.getInputSurfaces();
                for(Surface surs : surfaces) {
                    mPreviewRequestBuilder[id].addTarget(surs);
                    list.add(surs);
                }
                if (!mSettingsManager.isHeifWriterEncoding() && !mYUVCallback) {
                    list.add(mImageReader[id].getSurface());
                }
                if (mSaveRaw) {
                    list.add(mRawImageReader[id].getSurface());
                }

                if (mYUVCallback) {
                    Log.d(TAG,"yuv="+mYUVCount);
                    for(int i = 0; i < mYUVCount; i++){
                        list.add(mYUVImageReader[i].getSurface());
                    }
                }
                mPreviewRequestBuilder[id].addTarget(surface);
                List<OutputConfiguration> outputConfigurations = null;
                outputConfigurations = new ArrayList<OutputConfiguration>();
                for (Surface s : list) {
                    outputConfigurations.add(new OutputConfiguration(s));
                }
                if (mSettingsManager.isHeifWriterEncoding()) {
                    if (mInitHeifWriter != null) {
                        mHeifOutput = new OutputConfiguration(mInitHeifWriter.getInputSurface());
                        mHeifOutput.enableSurfaceSharing();
                        outputConfigurations.add(mHeifOutput);
                    }
                }
                if(mChosenImageFormat == ImageFormat.YUV_420_888 || mChosenImageFormat == ImageFormat.PRIVATE) {
                    if (mPostProcessor.isZSLEnabled()) {
                        mPreviewRequestBuilder[id].addTarget(mImageReader[id].getSurface());
                        list.add(mPostProcessor.getZSLReprocessImageReader().getSurface());
                        if (mSaveRaw) {
                            mPreviewRequestBuilder[id].addTarget(mRawImageReader[id].getSurface());
                        }
                        mCameraDevice[id].createReprocessableCaptureSession(new InputConfiguration(mImageReader[id].getWidth(),
                                mImageReader[id].getHeight(), mImageReader[id].getImageFormat()), list, captureSessionCallback, mCameraHandler);
                    } else {
                        if (mSettingsManager.isHeifWriterEncoding() && outputConfigurations != null) {
                            mCameraDevice[id].createCaptureSessionByOutputConfigurations(outputConfigurations,
                                    captureSessionCallback, mCameraHandler);
                        } else {
                            mCameraDevice[id].createCaptureSession(list, captureSessionCallback, mCameraHandler);
                        }
                    }
                } else {
                    if (ApiHelper.isAndroidPOrHigher() && outputConfigurations != null) {
                        Log.i(TAG,"list size:" + list.size());
                        createCameraSessionWithSessionConfiguration(id, outputConfigurations,
                                captureSessionCallback, mCameraHandler, mPreviewRequestBuilder[id]);
                    } else {
                        mCameraDevice[id].createCaptureSession(list, captureSessionCallback, mCameraHandler);
                    }
                }
            } else {
                if (surface != null) {
                    mPreviewRequestBuilder[id].addTarget(surface);
                    list.add(surface);
                }
                list.add(mImageReader[id].getSurface());
                // Here, we create a CameraCaptureSession for camera preview.
                mCameraDevice[id].createCaptureSession(list, captureSessionCallback, mCameraHandler);
            }
        } catch (CameraAccessException | NullPointerException e) {
            Log.d(TAG, "create session error");
            setCameraModeSwitcherAllowed(true);
            e.printStackTrace();
        } catch (IllegalStateException e) {
            setCameraModeSwitcherAllowed(true);
            Log.v(TAG, "createSession: mPaused status occur Time out waiting for surface ");
        } catch (IllegalArgumentException e) {
            setCameraModeSwitcherAllowed(true);
            e.printStackTrace();
        }
    }

    private void createSessionForVideo(final int cameraId) {
        mNeedSetupMediaRecorder = false;
        try {
            setUpMediaRecorder(cameraId);
            mCameraHandler.removeMessages(CANCEL_TOUCH_FOCUS, mCameraId[cameraId]);
            mState[cameraId] = STATE_PREVIEW;
            if(!mLockAFAE) {
                mControlAFMode = CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO;
            }

            mActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mUI.resetTrackingFocus();
                }
            });

            try {
                waitForPreviewSurfaceReady();
            } catch (RuntimeException e) {
                Log.v(TAG, "createSessionForVideo: normal status occur " +
                        "Time out waiting for surface ");
            }

            List<Surface> surfaces = new ArrayList<>();
            Surface surface = getPreviewSurfaceForSession(cameraId);
            mFrameProcessor.onOpen(getFrameProcFilterId(), mVideoSize);
            if (getFrameProcFilterId().size() == 1 && getFrameProcFilterId().get(0) ==
                    FrameProcessor.FILTER_MAKEUP) {
                setUpVideoPreviewRequestBuilder(mFrameProcessor.getInputSurfaces().get(0),cameraId);
            } else {
                setUpVideoPreviewRequestBuilder(surface, cameraId);
            }
            if(mFrameProcessor.isFrameFilterEnabled()) {
                mActivity.runOnUiThread(new Runnable() {
                    public void run() {
                        SurfaceHolder surfaceHolder = mUI.getSurfaceHolder();
                        if (surfaceHolder != null) {
                            surfaceHolder.setFixedSize(
                                    mVideoSize.getHeight(), mVideoSize.getWidth());
                        }
                    }
                });
            }
            mVideoPreviewSurface = surface;
            mFrameProcessor.setOutputSurface(surface);
            mMediaRecorderSurface = mMediaRecorder.getSurface();
            mFrameProcessor.setVideoOutputSurface(mMediaRecorderSurface);
            createVideoSnapshotImageReader();
            mVideoRecordRequestBuilder = getRequestBuilder(
                    CameraDevice.TEMPLATE_RECORD,cameraId);
            mVideoRecordRequestBuilder.setTag(cameraId);
            if (mHighSpeedCapture) {
                mVideoRecordRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                        mHighSpeedFPSRange);
            }
            addPreviewSurface(mVideoRecordRequestBuilder, surfaces, cameraId);
            setUpVideoCaptureRequestBuilder(mVideoRecordRequestBuilder, cameraId);
            mPreviewRequestBuilder[cameraId] = mVideoRecordRequestBuilder;
            mIsPreviewingVideo = true;
            if (isHighSpeedRateCapture()) {
                int optionMode = isSSMEnabled() ? STREAM_CONFIG_SSM : SESSION_HIGH_SPEED;
                buildConstrainedCameraSession(mCameraDevice[cameraId], optionMode,
                        surfaces, mSessionListener, mCameraHandler, mVideoRecordRequestBuilder);
            } else {
                configureCameraSessionWithParameters(cameraId, surfaces,
                        mSessionListener, mCameraHandler, mVideoRecordRequestBuilder);
            }
        } catch (CameraAccessException | IOException | IllegalArgumentException | NullPointerException | IllegalStateException e) {
            e.printStackTrace();
            quitVideoToPhotoWithError(e.getMessage());
        }
        mCurrentSessionClosed = false;
    }

    private int getSensorTableHFRRange() {
        int optimalSizeIndex = -1;
        int[] table = mSettingsManager.getSensorModeTable(getMainCameraId());
        if (table == null) {
            Log.w(TAG, "Sensor table hfr array got is null");
            return optimalSizeIndex;
        }
        String videoSizeString = mSettingsManager.getValue(SettingsManager.KEY_VIDEO_QUALITY);
        if (videoSizeString == null) {
            Log.w(TAG, "KEY_VIDEO_QUALITY is null");
            return optimalSizeIndex;
        }
        Size videoSize = parsePictureSize(videoSizeString);
        String rateValue = mSettingsManager.getValue(SettingsManager.KEY_VIDEO_HIGH_FRAME_RATE);
        if (rateValue == null || rateValue.substring(0, 3).equals("off")) {
            Log.w(TAG, "KEY_VIDEO_HIGH_FRAME_RATE is null");
            return optimalSizeIndex;
        }
        int frameRate = Integer.parseInt(rateValue.substring(3));
        for (int i = 2; i < table.length; i += table[1]) {
            if (table[i] == videoSize.getWidth()
                    && table[i + 1] == videoSize.getHeight()
                    && table[i + 2] == frameRate) {
                if (i != table.length) {
                    return (i - 2) / table[1] + 1;
                }
            }
        }

        // if does not query the index from (widthxheight, fps),
        // app will find the  closest to set the index according to fps
        int minDiff = Integer.MAX_VALUE;
        Point point = new Point(videoSize.getWidth(), videoSize.getHeight());
        int targetHeight = Math.min(point.x, point.y);
        // Try to find an size match aspect ratio and size
        for (int i = 2; i < table.length; i += table[1]) {
            if (table[i + 2] == frameRate) {
                Point size = new Point(table[i], table[i+1]);
                int miniSize = Math.min(size.x, size.y);
                int heightDiff = Math.abs(miniSize - targetHeight);
                if (heightDiff < minDiff) {
                    if (i != table.length) {
                        optimalSizeIndex = (i - 2) / table[1] + 1;
                    }
                    minDiff = Math.abs(miniSize - targetHeight);
                }
            }
        }

        return optimalSizeIndex;
    }

    public void setAFModeToPreview(int id, int afMode) {
        if (!checkSessionAndBuilder(mCaptureSession[id], mPreviewRequestBuilder[id])) {
            return;
        }
        if (DEBUG) {
            Log.d(TAG, "setAFModeToPreview " + afMode);
        }
        mPreviewRequestBuilder[id].set(CaptureRequest.CONTROL_AF_MODE, afMode);
        applyAFRegions(mPreviewRequestBuilder[id], id);
        applyAERegions(mPreviewRequestBuilder[id], id);
        mPreviewRequestBuilder[id].setTag(id);
        try {
            if (isSSMEnabled() && (mIsPreviewingVideo || mIsRecordingVideo)) {
                mCaptureSession[id].setRepeatingBurst(createSSMBatchRequest(mVideoRecordRequestBuilder),
                        mCaptureCallback, mCameraHandler);
            } else if (mCaptureSession[id] instanceof CameraConstrainedHighSpeedCaptureSession) {
                CameraConstrainedHighSpeedCaptureSession session =
                        (CameraConstrainedHighSpeedCaptureSession) mCaptureSession[id];
                List requestList = session.createHighSpeedRequestList(mVideoRecordRequestBuilder.build());
                session.setRepeatingBurst(requestList, mCaptureCallback, mCameraHandler);
            } else {
                mCaptureSession[id].setRepeatingRequest(mPreviewRequestBuilder[id]
                        .build(), mCaptureCallback, mCameraHandler);
            }
        } catch (CameraAccessException | IllegalStateException e) {
            e.printStackTrace();
        }
    }

    public void setFocusDistanceToPreview(int id, float fd) {
        if (!checkSessionAndBuilder(mCaptureSession[id], mPreviewRequestBuilder[id])) {
            return;
        }
        mPreviewRequestBuilder[id].set(CaptureRequest.LENS_FOCUS_DISTANCE, fd);
        mPreviewRequestBuilder[id].setTag(id);
        try {
            if (id == MONO_ID && !canStartMonoPreview()) {
                mCaptureSession[id].capture(mPreviewRequestBuilder[id]
                        .build(), mCaptureCallback, mCameraHandler);
            } else {
                mCaptureSession[id].setRepeatingRequest(mPreviewRequestBuilder[id]
                        .build(), mCaptureCallback, mCameraHandler);
            }
        } catch (CameraAccessException | IllegalStateException e) {
            e.printStackTrace();
        }
    }

    private void reinitSceneMode() {
        mCurrentSceneMode = mSceneCameraIds.get(mNextModeIndex);
        mCurrentModeIndex = mNextModeIndex;
        CURRENT_MODE = mCurrentSceneMode.mode;
        CURRENT_ID = mCurrentSceneMode.getNextCameraId(CURRENT_MODE);
        Log.d(TAG, "reinitSceneMode: CURRENT_ID :" + CURRENT_ID);
    }

    public void reinit() {
        CURRENT_ID = mCurrentSceneMode.getCurrentId();
        CURRENT_MODE = mCurrentSceneMode.mode;
        Log.d(TAG, "reinit: CURRENT_ID :" + CURRENT_ID);
        mSettingsManager.init();
    }

    private boolean frontIsAllowed() {
        return mCurrentSceneMode.mode == CameraMode.DEFAULT ||
                mCurrentSceneMode.mode == CameraMode.VIDEO ||
                mCurrentSceneMode.mode == CameraMode.HFR;
    }

    public boolean isRefocus() {
        return mIsRefocus;
    }

    public boolean getRecordLocation() {
        String value = mSettingsManager.getValue(SettingsManager.KEY_RECORD_LOCATION);
        if (value == null) value = RecordLocationPreference.VALUE_NONE;
        return RecordLocationPreference.VALUE_ON.equals(value);
    }

    @Override
    public void init(CameraActivity activity, View parent) {
        mActivity = activity;
        mRootView = parent;
        mSettingsManager = SettingsManager.getInstance();
        mSettingsManager.createCaptureModule(this);
        mSettingsManager.registerListener(this);
        if (isBackCameraId()) {
            CURRENT_ID = BACK_MODE;
        } else {
            CURRENT_ID = FRONT_MODE;
        }
        mSettingsManager.init();
        mFirstPreviewLoaded = false;
        Log.d(TAG, "init");
        for (int i = 0; i < MAX_NUM_CAM; i++) {
            mCameraOpened[i] = false;
            mTakingPicture[i] = false;
        }
        for (int i = 0; i < MAX_NUM_CAM; i++) {
            mState[i] = STATE_PREVIEW;
        }
        SceneModule module;
        for (int i = 0; i < mSelectableModes.length; i++) {
            module = new SceneModule();
            module.mode = CameraMode.values()[i];
            mSceneCameraIds.add(module);
        }
        mPostProcessor = new PostProcessor(mActivity, this);
        mFrameProcessor = new FrameProcessor(mActivity, this);

        mContentResolver = mActivity.getContentResolver();
        initModeByIntent();
        initCameraIds();
        mUI = new CaptureUI(activity, this, parent);
        mUI.initializeControlByIntent();

        mFocusStateListener = new FocusStateListener(mUI);
        mLocationManager = new LocationManager(mActivity, this);
    }

    public void restoreCameraIds(){
        CURRENT_ID = mCurrentSceneMode.getCurrentId();
    }

    private void initCameraIds() {
        CameraManager manager = (CameraManager) mActivity.getSystemService(Context.CAMERA_SERVICE);
        boolean isFirstDefault = true;
        boolean[] removeList = new boolean[mSelectableModes.length];
        for (int i = 0; i < mSelectableModes.length; i++) {
            removeList[i] = true;
        }
        String[] cameraIdList = null;
        try {
            cameraIdList = manager.getCameraIdList();
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        if (cameraIdList == null || cameraIdList.length == 0) {
            return;
        }
        for (int i = 0; i < cameraIdList.length; i++) {
            String cameraId = cameraIdList[i];
            mCameraId[i] = cameraId;
            CameraCharacteristics characteristics;
            try {
                characteristics = manager.getCameraCharacteristics(cameraId);
            } catch (CameraAccessException e) {
                e.printStackTrace();
                continue;
            }
            isFirstDefault = setUpLocalMode(i, characteristics, removeList,
                    isFirstDefault, cameraId);
        }
        for (int i = 0; i < removeList.length; i++) {
            if (!removeList[i]) {
                continue;
            }
            for (SceneModule sceneModule : mSceneCameraIds) {
                if (sceneModule.mode.ordinal() == i) {
                    mSceneCameraIds.remove(sceneModule);
                    break;
                }
            }
        }
    }

    private boolean setUpLocalMode(int cameraId, CameraCharacteristics characteristics,
                                boolean[] removeList, boolean isFirstDefault, String physicalId) {
        Byte type = 0;
        try {
            type = characteristics.get(logical_camera_type);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "setUpLocalMode no vendorTag logical_camera_type:" + logical_camera_type);
        }
        Log.d(TAG,"init cameraId " + cameraId + " | logical_camera_type = " + type +
                " | physical id = " + physicalId);
        int facing = characteristics.get(CameraCharacteristics.LENS_FACING);
        switch (type) {
            case TYPE_DEFAULT:// default
                removeList[CameraMode.DEFAULT.ordinal()] = false;
                removeList[CameraMode.VIDEO.ordinal()] = false;
                removeList[CameraMode.PRO_MODE.ordinal()] = false;
                if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    CaptureModule.FRONT_ID = cameraId;
                    mSceneCameraIds.get(CameraMode.DEFAULT.ordinal()).frontCameraId = cameraId;
                    mSceneCameraIds.get(CameraMode.VIDEO.ordinal()).frontCameraId = cameraId;
                    mSceneCameraIds.get(CameraMode.HFR.ordinal()).frontCameraId = cameraId;
                    mSceneCameraIds.get(CameraMode.PRO_MODE.ordinal()).frontCameraId = cameraId;
                } else {
                    if (!isFirstDefault) {
                        mSceneCameraIds.get(CameraMode.DEFAULT.ordinal()).auxCameraId = cameraId;
                        break;
                    }
                    isFirstDefault = false;
                    mSceneCameraIds.get(CameraMode.DEFAULT.ordinal()).rearCameraId = cameraId;
                    mSceneCameraIds.get(CameraMode.VIDEO.ordinal()).rearCameraId = cameraId;
                    mSceneCameraIds.get(CameraMode.PRO_MODE.ordinal()).rearCameraId = cameraId;
                    if (mSettingsManager.isHFRSupported()) { // filter HFR mode
                        removeList[CameraMode.HFR.ordinal()] = false;
                        mSceneCameraIds.get(CameraMode.HFR.ordinal()).rearCameraId = cameraId;
                    }
                    if (mCurrentSceneMode == null) {
                        int index = mIntentMode == INTENT_MODE_VIDEO ?
                                CameraMode.VIDEO.ordinal() : CameraMode.DEFAULT.ordinal();
                        mCurrentModeIndex =  mNextModeIndex = index;
                        mCurrentSceneMode = mSceneCameraIds.get(index);
                    }
                }
                break;
            case TYPE_RTB:// RTB
                removeList[CameraMode.RTB.ordinal()] = false;
                if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    mSceneCameraIds.get(CameraMode.RTB.ordinal()).frontCameraId = cameraId;
                } else {
                    if (mSceneCameraIds.get(CameraMode.RTB.ordinal()).rearCameraId != 0) {
                        break;
                    }
                    mSceneCameraIds.get(CameraMode.RTB.ordinal()).rearCameraId = cameraId;
                    //add default front camera id, need to change if front RTB available
                    mSceneCameraIds.get(CameraMode.RTB.ordinal()).frontCameraId = FRONT_ID;
                }
                break;
            case TYPE_SAT:// SAT
                removeList[CameraMode.SAT.ordinal()] = false;
                if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    mSceneCameraIds.get(CameraMode.SAT.ordinal()).frontCameraId = cameraId;
                } else {
                    if (mSceneCameraIds.get(CameraMode.SAT.ordinal()).rearCameraId != 0) {
                        break;
                    }
                    // if dual camera is enabled, video rear camera will be changed to SAT
                    mSceneCameraIds.get(CameraMode.VIDEO.ordinal()).rearCameraId = cameraId;
                    mSceneCameraIds.get(CameraMode.SAT.ordinal()).rearCameraId = cameraId;
                    //add default front camera id, need to change if front SAT available
                    mSceneCameraIds.get(CameraMode.SAT.ordinal()).frontCameraId = FRONT_ID;
                }
                break;
            case TYPE_VR360:// VR 360
                Log.w(TAG, "VR 360 is not supported from APP side now");
                break;
            default:// indicate error
                Log.w(TAG, "Type error: indicate error");
                break;
        }
        return isFirstDefault;
    }

    private void initModeByIntent() {
        String action = mActivity.getIntent().getAction();
        Log.v(TAG, " initModeByIntent: " + action);
        Bundle bundle = mActivity.getIntent().getExtras();
        if (bundle != null) {
            Log.v(TAG, " initModeByIntent bundle :" + bundle.toString());
        }
        if (MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA.equals(action)) {
            mIntentMode = INTENT_MODE_STILL_IMAGE_CAMERA;
            Set<String> categories = mActivity.getIntent().getCategories();
            if (categories != null) {
                for(String categorie: categories) {
                    Log.v(TAG, " initModeByIntent categorie :" + categorie);
                    if(categorie.equals("android.intent.category.VOICE")) {
                        mIsVoiceTakePhote = true;
                    }
                }
            }
            boolean isOpenOnly = mActivity.getIntent().getBooleanExtra(
                    "com.google.assistant.extra.CAMERA_OPEN_ONLY", false);
            if (isOpenOnly) {
                mIsVoiceTakePhote = false;
            }
            Log.v(TAG, " initModeByIntent isOpenOnly :" + isOpenOnly + ", mIsVoiceTakePhote :"
                    + mIsVoiceTakePhote);
        }
        if (MediaStore.ACTION_IMAGE_CAPTURE.equals(action)) {
            mIntentMode = INTENT_MODE_CAPTURE;
        } else if (CameraActivity.ACTION_IMAGE_CAPTURE_SECURE.equals(action)) {
            mIntentMode = INTENT_MODE_CAPTURE_SECURE;
        } else if (MediaStore.ACTION_VIDEO_CAPTURE.equals(action)) {
            mIntentMode = INTENT_MODE_VIDEO;
        }
        mQuickCapture = mActivity.getIntent().getBooleanExtra(EXTRA_QUICK_CAPTURE, false);
        Bundle myExtras = mActivity.getIntent().getExtras();
        if (myExtras != null) {
            mSaveUri = (Uri) myExtras.getParcelable(MediaStore.EXTRA_OUTPUT);
            mCropValue = myExtras.getString("crop");
        }
    }

    public boolean isQuickCapture() {
        return mQuickCapture;
    }

    public void setJpegImageData(byte[] data) {
        mJpegImageData = data;
    }

    public void showCapturedReview(final byte[] jpegData, final int orientation) {
        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mUI.showCapturedImageForReview(jpegData, orientation);
            }
        });
    }


    public int getCurrentIntentMode() {
        return mIntentMode;
    }

    public void cancelCapture() {
        mActivity.finish();
    }

    /**
     * Initiate a still image capture.
     */
    private void takePicture() {
        Log.d(TAG, "takePicture");
        mUI.enableShutter(false);
        if ((mSettingsManager.isZSLInHALEnabled() || isActionImageCapture()) &&
                !isFlashOn(getMainCameraId()) && (mPreviewCaptureResult != null &&
                mPreviewCaptureResult.get(CaptureResult.CONTROL_AE_STATE) !=
                     CameraMetadata.CONTROL_AE_STATE_FLASH_REQUIRED &&
                (mPreviewCaptureResult.getRequest().get(CaptureRequest.CONTROL_AE_LOCK) != Boolean.TRUE)||mLockAFAE)) {
            takeZSLPictureInHAL();
        } else {
            int cameraId = mCurrentSceneMode.getCurrentId();
            if (takeZSLPicture(cameraId)) {
                return;
            }
            if (mUI.getCurrentProMode() == ProMode.MANUAL_MODE) {
                captureStillPicture(cameraId);
            } else {
                if (mLongshotActive) {
                    parallelLockFocusExposure(cameraId);
                } else {
                    if (mPreviewCaptureResult != null) {
                        Integer aeState = mPreviewCaptureResult.get(CaptureResult.CONTROL_AE_STATE);
                        isFlashRequiredInDriver = aeState != null &&
                                aeState == CameraMetadata.CONTROL_AE_STATE_FLASH_REQUIRED;
                    }
                    lockFocus(cameraId);
                }
            }
        }
    }

    private boolean isActionImageCapture() {
        return mIntentMode == INTENT_MODE_CAPTURE;
    }

    private boolean takeZSLPicture(int cameraId) {
        if(mPostProcessor.isZSLEnabled() && mPostProcessor.takeZSLPicture()) {
            checkAndPlayShutterSound(getMainCameraId());
            mUI.enableShutter(true);
            return true;
        }
        return false;
    }

    private void takeZSLPictureInHAL() {
        Log.d(TAG, "takeHALZSLPicture");
        captureStillPicture(mCurrentSceneMode.getCurrentId());
    }

    public boolean isLongShotActive() {
        return mLongshotActive;
    }

    private void parallelLockFocusExposure(int id) {
        if (mActivity == null || mCameraDevice[id] == null
                || !checkSessionAndBuilder(mCaptureSession[id], mPreviewRequestBuilder[id])) {
            enableShutterAndVideoOnUiThread(id,true);
            warningToast("Camera is not ready yet to take a picture.");
            return;
        }
        Log.d(TAG, "parallelLockFocusExposure " + id);

        if (mState[id] == STATE_WAITING_TOUCH_FOCUS) {
            String value = mSettingsManager.getValue(mCurrentSceneMode.mode == CameraMode.PRO_MODE ?
                    SettingsManager.KEY_VIDEO_FLASH_MODE : SettingsManager.KEY_FLASH_MODE);
            if (value != null && value.equals("on")) {
                mState[id] = STATE_WAITING_AE_PRECAPTURE;
            } else {
                mState[id] = STATE_WAITING_AF_LOCKING;
            }
            mCameraHandler.removeMessages(CANCEL_TOUCH_FOCUS, mCameraId[id]);
            mLockRequestHashCode[id] = 0;
            return;
        }

        try {
            // start repeating request to get AF/AE state updates
            // for mono when mono preview is off.
            if(id == MONO_ID && !canStartMonoPreview()) {
                mCaptureSession[id].setRepeatingRequest(mPreviewRequestBuilder[id]
                        .build(), mCaptureCallback, mCameraHandler);
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        mTakingPicture[id] = true;

        try {
            mState[id] = STATE_WAITING_AF_AE_LOCK;
            CaptureRequest.Builder builder = getRequestBuilder(id);
            builder.setTag(id);
            addPreviewSurface(builder, null, id);
            // lock AF and Precapture
            applySettingsForLockAndPrecapture(builder, id);
            CaptureRequest request = builder.build();
            mLockRequestHashCode[id] = request.hashCode();
            mCaptureSession[id].capture(request, mCaptureCallback, mCameraHandler);

            // if flash is on, does not lock AE until the AE state is CONTROL_AE_STATE_CONVERGED.
            // if flash is off, lock AE now.
            if (!isFlashOn(id)) {
                applySettingsForLockExposure(mPreviewRequestBuilder[id], id);
                mCaptureSession[id].setRepeatingRequest(mPreviewRequestBuilder[id].build(),
                        mCaptureCallback, mCameraHandler);
            } else {
                // for longshot flash, need to re-configure the preview flash mode.
                applyFlash(mPreviewRequestBuilder[id], id);
                mCaptureSession[id].setRepeatingRequest(mPreviewRequestBuilder[id]
                        .build(), mCaptureCallback, mCameraHandler);
            }

            if(mHiston) {
                updateGraghViewVisibility(View.INVISIBLE);
            }

            if(mBGStatson) {
                updateBGStatsVisibility(View.INVISIBLE);
            }

            if(mBEStatson) {
                updateBEStatsVisibility(View.INVISIBLE);
            }

        } catch (CameraAccessException | IllegalStateException e) {
            e.printStackTrace();
        }

    }

    /**
     * Lock the focus as the first step for a still image capture.
     */
    private void lockFocus(int id) {
        if (mActivity == null || mCameraDevice[id] == null
                || !checkSessionAndBuilder(mCaptureSession[id], mPreviewRequestBuilder[id])) {
            enableShutterAndVideoOnUiThread(id,true);
            warningToast("Camera is not ready yet to take a picture.");
            return;
        }
        Log.d(TAG, "lockFocus " + id);

        try {
            // start repeating request to get AF/AE state updates
            // for mono when mono preview is off.
            if(id == MONO_ID && !canStartMonoPreview()) {
                mCaptureSession[id].setRepeatingRequest(mPreviewRequestBuilder[id]
                        .build(), mCaptureCallback, mCameraHandler);
            } else {
                // for longshot flash, need to re-configure the preview flash mode.
                if (mLongshotActive && isFlashOn(id)) {
                    mCaptureSession[id].stopRepeating();
                    applyFlash(mPreviewRequestBuilder[id], id);
                    mCaptureSession[id].setRepeatingRequest(mPreviewRequestBuilder[id]
                                            .build(), mCaptureCallback, mCameraHandler);
                }
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        mTakingPicture[id] = true;
        if (mState[id] == STATE_WAITING_TOUCH_FOCUS) {
            mCameraHandler.removeMessages(CANCEL_TOUCH_FOCUS, mCameraId[id]);
            mState[id] = STATE_WAITING_AF_LOCK;
            mLockRequestHashCode[id] = 0;
            return;
        }

        try {
            CaptureRequest.Builder builder = getRequestBuilder(id);
            builder.setTag(id);
            addPreviewSurface(builder, null, id);

            applySettingsForLockFocus(builder, id);
            CaptureRequest request = builder.build();
            mLockRequestHashCode[id] = request.hashCode();
            mState[id] = STATE_WAITING_AF_LOCK;
            mCaptureSession[id].capture(request, mCaptureCallback, mCameraHandler);
            if (mHiston) {
                updateGraghViewVisibility(View.INVISIBLE);
            }

            if (mBGStatson) {
                updateBGStatsVisibility(View.INVISIBLE);
            }

            if (mBEStatson) {
                updateBEStatsVisibility(View.INVISIBLE);
            }
        } catch (CameraAccessException | IllegalStateException e) {
            e.printStackTrace();
        }
    }

    private void autoFocusTrigger(int id) {
        if (DEBUG) {
            Log.d(TAG, "autoFocusTrigger " + id);
        }
        if (null == mActivity || null == mCameraDevice[id]
                || !checkSessionAndBuilder(mCaptureSession[id], mPreviewRequestBuilder[id])) {
            warningToast("Camera is not ready yet to take a picture.");
            mInTAF = false;
            return;
        }
        try {
            CaptureRequest.Builder builder = getRequestBuilder(id);
            builder.setTag(id);
            addPreviewSurface(builder, null, id);

            mControlAFMode = CaptureRequest.CONTROL_AF_MODE_AUTO;
            mIsAutoFocusStarted = true;
            mIsCanceled = false;
            applySettingsForAutoFocus(builder, id);
            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO);
            mState[id] = STATE_WAITING_TOUCH_FOCUS;
            if (mCurrentSceneMode.mode == CameraMode.VIDEO ||
                    mCurrentSceneMode.mode == CameraMode.HFR) {
                applyVideoFlash(builder, id);
            } else {
                applyFlash(builder, id);//apply flash mode and AEmode for this temp builder
            }
            if (mCurrentSceneMode.mode == CameraMode.HFR && isHighSpeedRateCapture()) {
                List<CaptureRequest> tafBuilderList = isSSMEnabled() ?
                        createSSMBatchRequest(builder) :
                        ((CameraConstrainedHighSpeedCaptureSession) mCaptureSession[id]).
                        createHighSpeedRequestList(builder.build());
                mCaptureSession[id].captureBurst(tafBuilderList, mCaptureCallback, mCameraHandler);
            } else {
                mCaptureSession[id].capture(builder.build(), mCaptureCallback, mCameraHandler);
            }
            setAFModeToPreview(id, mControlAFMode);
            if(!mLockAFAE) {
                Message message =
                        mCameraHandler.obtainMessage(CANCEL_TOUCH_FOCUS, id, 0, mCameraId[id]);
                mCameraHandler.sendMessageDelayed(message, CANCEL_TOUCH_FOCUS_DELAY);
            }
        } catch (CameraAccessException | IllegalStateException | IllegalArgumentException e) {
            e.printStackTrace();
        }
    }

    public void linkBayerMono(int id) {
        Log.d(TAG, "linkBayerMono " + id);
        if (id == BAYER_ID) {
            mPreviewRequestBuilder[id].set(BayerMonoLinkEnableKey, (byte) 1);
            mPreviewRequestBuilder[id].set(BayerMonoLinkMainKey, (byte) 1);
            mPreviewRequestBuilder[id].set(BayerMonoLinkSessionIdKey, MONO_ID);
        } else if (id == MONO_ID) {
            mPreviewRequestBuilder[id].set(BayerMonoLinkEnableKey, (byte) 1);
            mPreviewRequestBuilder[id].set(BayerMonoLinkMainKey, (byte) 0);
            mPreviewRequestBuilder[id].set(BayerMonoLinkSessionIdKey, BAYER_ID);
        }
    }

    public void unLinkBayerMono(int id) {
        Log.d(TAG, "unlinkBayerMono " + id);
        if (id == BAYER_ID) {
            mPreviewRequestBuilder[id].set(BayerMonoLinkEnableKey, (byte) 0);
        } else if (id == MONO_ID) {
            mPreviewRequestBuilder[id].set(BayerMonoLinkEnableKey, (byte) 0);
        }
    }

    public PostProcessor getPostProcessor() {
        return mPostProcessor;
    }

    private void captureStillPicture(final int id) {
        Log.d(TAG, "captureStillPicture " + id);
        mJpegImageData = null;
        mIsRefocus = false;
        if (isDeepZoom()) mSupportZoomCapture = false;
        try {
            if (null == mActivity || null == mCameraDevice[id]
                    || !checkSessionAndBuilder(mCaptureSession[id], mPreviewRequestBuilder[id])) {
                enableShutterAndVideoOnUiThread(id,true);
                mLongshotActive = false;
                warningToast("Camera is not ready yet to take a picture.");
                return;
            }

            CaptureRequest.Builder captureBuilder = getRequestBuilder(
                    CameraDevice.TEMPLATE_STILL_CAPTURE,id);
            if(mLockAFAE){
                applySettingsForLockExposure(captureBuilder, id);
            }
            if (mSettingsManager.isZSLInHALEnabled() || isActionImageCapture()) {
                captureBuilder.set(CaptureRequest.CONTROL_ENABLE_ZSL, true);
            } else {
                captureBuilder.set(CaptureRequest.CONTROL_ENABLE_ZSL, false);
            }

            applySettingsForJpegInformation(captureBuilder, id);
            applyAFRegions(captureBuilder, id);
            applyAERegions(captureBuilder, id);
            applySettingsForCapture(captureBuilder, id);
            if (!mLongshoting) {
                VendorTagUtil.setCdsMode(captureBuilder, 2);// CDS 0-OFF, 1-ON, 2-AUTO
                applyCaptureMFNR(captureBuilder);
            }
            applyCaptureBurstFps(captureBuilder);
            String valueFS2 = mSettingsManager.getValue(SettingsManager.KEY_SENSOR_MODE_FS2_VALUE);
            int fs2Value = 0;
            if (valueFS2 != null) {
                fs2Value = Integer.parseInt(valueFS2);
            }
            if (!(mIsSupportedQcfa || isDeepZoom() || (fs2Value ==1))) {
                addPreviewSurface(captureBuilder, null, id);
            }
            if (mUI.getCurrentProMode() == ProMode.MANUAL_MODE) {
                applyFocusDistance(captureBuilder, String.valueOf(
                        mSettingsManager.getCalculatedFocusDistance()));
            }

            if (isDeepZoom()) mSupportZoomCapture = true;
            if(isClearSightOn()) {
                captureStillPictureForClearSight(id);
            } else if(id == getMainCameraId() && mPostProcessor.isFilterOn()) { // Case of post filtering
                captureStillPictureForFilter(captureBuilder, id);
            } else {
                if (mSaveRaw && mRawImageReader[id] != null) {
                    captureBuilder.addTarget(mRawImageReader[id].getSurface());
                }
                if (mSettingsManager.isHeifWriterEncoding()) {
                    long captureTime = System.currentTimeMillis();
                    mNamedImages.nameNewImage(captureTime);
                    NamedEntity name = mNamedImages.getNextNameEntity();
                    String title = (name == null) ? null : name.title;
                    long date = (name == null) ? -1 : name.date;
                    String pictureFormat = mLongshotActive? "heics":"heic";
                    String path = Storage.generateFilepath(title, pictureFormat);
                    String value = mSettingsManager.getValue(SettingsManager.KEY_JPEG_QUALITY);
                    int quality = getQualityNumber(value);
                    int orientation = CameraUtil.getJpegRotation(id,mOrientation);
                    int imageCount = mLongshotActive? PersistUtil.getLongshotShotLimit(): 1;
                    HeifWriter writer = createHEIFEncoder(path,mPictureSize.getWidth(),mPictureSize.getHeight(),
                            orientation,imageCount,quality);
                    if (writer != null) {
                        mHeifImage = new HeifImage(writer,path,title,date,orientation,quality);
                        Surface input = writer.getInputSurface();
                        mHeifOutput.addSurface(input);
                        try{
                            mCaptureSession[id].updateOutputConfiguration(mHeifOutput);
                            captureBuilder.addTarget(input);
                            writer.start();
                        } catch (IllegalStateException | IllegalArgumentException e) {
                            e.printStackTrace();
                        }
                    }
                } else {
                    if (!mYUVCallback) {
                        if (mImageReader[id] != null) {
                            captureBuilder.addTarget(mImageReader[id].getSurface());
                        }
                    } else {
                        for (int i =0;i<mYUVCount;i++) {
                            Log.i(TAG, "mYUVCount:" + mYUVCount + ",mYUVImageReader:" + mYUVImageReader[i]);
                            captureBuilder.addTarget(mYUVImageReader[i].getSurface());
                        }
                    }
                }

                if(mPaused || !mCamerasOpened) {
                    //for avoid occurring crash when click back before capture finished.
                    //CameraDevice was already closed
                    return;
                }
                if (mLongshotActive) {
                    captureStillPictureForLongshot(captureBuilder, id);
                } else {
                    captureStillPictureForCommon(captureBuilder, id);
                }
            }
        } catch (CameraAccessException e) {
            Log.d(TAG, "Capture still picture has failed");
            e.printStackTrace();
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        }
    }

    private void captureStillPictureForClearSight(int id) throws CameraAccessException{
        CaptureRequest.Builder captureBuilder =
                ClearSightImageProcessor.getInstance().createCaptureRequest(mCameraDevice[id]);

        if(mSettingsManager.isZSLInHALEnabled()) {
            captureBuilder.set(CaptureRequest.CONTROL_ENABLE_ZSL, true);
        }else{
            captureBuilder.set(CaptureRequest.CONTROL_ENABLE_ZSL, false);
        }
        applySettingsForJpegInformation(captureBuilder, id);
        addPreviewSurface(captureBuilder, null, id);
        VendorTagUtil.setCdsMode(captureBuilder, 2); // CDS 0-OFF, 1-ON, 2-AUTO
        applySettingsForCapture(captureBuilder, id);
        applySettingsForLockExposure(captureBuilder, id);
        checkAndPlayShutterSound(id);
        if(mPaused || !mCamerasOpened) {
            //for avoid occurring crash when click back before capture finished.
            //CameraDevice was already closed
            return;
        }
        ClearSightImageProcessor.getInstance().capture(
                id==BAYER_ID, mCaptureSession[id], captureBuilder, mCaptureCallbackHandler);
    }

    private void captureStillPictureForFilter(CaptureRequest.Builder captureBuilder, int id) throws CameraAccessException{
        applySettingsForLockExposure(captureBuilder, id);
        checkAndPlayShutterSound(id);
        if(mPaused || !mCamerasOpened) {
            //for avoid occurring crash when click back before capture finished.
            //CameraDevice was already closed
            return;
        }
        if (!isDeepZoom()) {
            mCaptureSession[id].stopRepeating();
        }
        captureBuilder.addTarget(mImageReader[id].getSurface());
        if (mSaveRaw) {
            captureBuilder.addTarget(mRawImageReader[id].getSurface());
        }
        mPostProcessor.onStartCapturing();
        if(mPostProcessor.isManualMode()) {
            mPostProcessor.manualCapture(captureBuilder, mCaptureSession[id], mCaptureCallbackHandler);
        } else {
            List<CaptureRequest> captureList = mPostProcessor.setRequiredImages(captureBuilder);
            mCaptureSession[id].captureBurst(captureList, mPostProcessor.getCaptureCallback(), mCaptureCallbackHandler);
        }
    }

    public void doShutterAnimation() {
        if (mUI != null) {
            mUI.doShutterAnimation();
        }
    }

    private CameraCaptureSession.CaptureCallback mLongshotCallBack= new CameraCaptureSession.CaptureCallback() {
            @Override
            public void onCaptureCompleted(CameraCaptureSession session,
                                           CaptureRequest request,
                                           TotalCaptureResult result) {
                String requestTag = String.valueOf(request.getTag());
                if (requestTag.equals("preview")) {
                    return;
                }
                Log.d(TAG, "captureStillPictureForLongshot onCaptureCompleted: " + mNumFramesArrived.get() + " " + mShotNum);
                if (mLongshotActive) {
                    checkAndPlayShutterSound(getMainCameraId());
                    mActivity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mUI.doShutterAnimation();
                        }
                    });
                }
                mLongshoting = false;
            }

            @Override
            public void onCaptureStarted(CameraCaptureSession session, CaptureRequest request,
                    long timestamp, long frameNumber) {
                String requestTag = String.valueOf(request.getTag());
                if (requestTag.equals("preview")) {
                    return;
                }
                mLongshoting = true;
                mNumFramesArrived.incrementAndGet();
                if(mNumFramesArrived.get() == mShotNum) {
                    mLastLongshotTimestamp = timestamp;
                }
                Log.d(TAG, "captureStillPictureForLongshot onCaptureStarted: " + mNumFramesArrived.get());
                if (mNumFramesArrived.get() >= mShotNum) {
                    mLongshotActive = false;
                }
            }

            @Override
            public void onCaptureFailed(CameraCaptureSession session,
                                        CaptureRequest request,
                                        CaptureFailure result) {
                Log.d(TAG, "captureStillPictureForLongshot onCaptureFailed.");
                if (mLongshotActive) {
                    mActivity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mUI.doShutterAnimation();
                        }
                    });
                }
                mLongshoting = false;
            }

            @Override
            public void onCaptureSequenceCompleted(CameraCaptureSession session, int
                            sequenceId, long frameNumber) {
                if (mSettingsManager.isHeifWriterEncoding()) {
                    mLongshotActive = false;
                    if (mHeifImage != null) {
                        try {
                            mHeifImage.getWriter().stop(5000);
                            mHeifImage.getWriter().close();
                            mActivity.getMediaSaveService().addHEIFImage(mHeifImage.getPath(),
                                    mHeifImage.getTitle(),mHeifImage.getDate(),null,mPictureSize.getWidth(),mPictureSize.getHeight(),
                                    mHeifImage.getOrientation(),null,mContentResolver,mOnMediaSavedListener,mHeifImage.getQuality(),"heics");
                        } catch (Exception e) {
                            e.printStackTrace();
                        } finally {
                            try{
                                mHeifOutput.removeSurface(mHeifImage.getInputSurface());
                                session.updateOutputConfiguration(mHeifOutput);
                                mHeifImage = null;
                            }catch (CameraAccessException e) {
                                e.printStackTrace();
                            }catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }

                }
                if (mNumFramesArrived.get() < mShotNum && mLongshotActive) {
                    captureStillPicture(CURRENT_ID);
                }else {
                    mLongshoting = false;
                    mNumFramesArrived.getAndSet(0);
                    unlockFocus(getMainCameraId());
                }
            }
        };

    private float calculateMaxFps(){
        float maxFps = mSettingsManager.getmaxBurstShotFPS();
        if(maxFps > 0) {
            double size = mPictureSize.getWidth() * mPictureSize.getHeight();
            double maxsizefloat = mSupportedMaxPictureSize.getWidth() * mSupportedMaxPictureSize.getHeight();
            maxFps = (float)((maxsizefloat * maxFps) / size);
            if (DEBUG) {
                Log.i(TAG, "maxsize:" + mSupportedMaxPictureSize.getWidth() + ",height:" + mSupportedMaxPictureSize.getHeight() + "maxsize:" + maxsizefloat);
                Log.i(TAG, "size:" + mPictureSize.getWidth() + ",height:" + mPictureSize.getHeight() + ",size:" + size);
                Log.i(TAG,"maxFps:" + maxFps);
            }
            maxFps = maxFps > 30 ? 30 : maxFps;
        }
        return maxFps;
    }

    private void captureStillPictureForLongshot(CaptureRequest.Builder captureBuilder, int id) throws CameraAccessException{
        List<CaptureRequest> burstList = new ArrayList<>();
        int burstShotFpsNums = 0;
        if(calculateMaxFps() > 0){
            burstShotFpsNums = (int)(30/calculateMaxFps()) - 1;
        }
        burstShotFpsNums = PersistUtil.isBurstShotFpsNums() > 1 ? PersistUtil.isBurstShotFpsNums() : burstShotFpsNums;
        int totalNums = (int)(PersistUtil.getLongshotShotLimit()/(burstShotFpsNums + 1));
        CaptureRequest.Builder builder = getRequestBuilder(id);
        addPreviewSurface(builder, null, id);
        applyCommonSettings(builder, id);
        applyFlash(builder, id);
        if(totalNums < 60) totalNums = totalNums *2;
        if (DEBUG) Log.i(TAG,"burstShotFpsNums:" + burstShotFpsNums + ",totalNums:" + totalNums);
        for (int i = 0; i < totalNums; i++) {
            for (int j = 0; j < burstShotFpsNums; j++) {
                builder.setTag("preview");
                burstList.add(builder.build());
            }
            captureBuilder.setTag("capture");
            burstList.add(captureBuilder.build());
        }
        mCaptureSession[id].captureBurst(burstList, mLongshotCallBack, mCaptureCallbackHandler);
        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mUI.enableVideo(false);
            }
        });
    }

    private void captureStillPictureForCommon(CaptureRequest.Builder captureBuilder, int id) throws CameraAccessException{
        checkAndPlayShutterSound(id);
        if(mLongshoting) mLongshoting = false;
        if(isMpoOn()) {
            mCaptureStartTime = System.currentTimeMillis();
            mMpoSaveHandler.obtainMessage(MpoSaveHandler.MSG_CONFIGURE,
                    Long.valueOf(mCaptureStartTime)).sendToTarget();
        }
        if(mChosenImageFormat == ImageFormat.YUV_420_888 || mChosenImageFormat == ImageFormat.PRIVATE) { // Case of ZSL, FrameFilter, SelfieMirror
            mPostProcessor.onStartCapturing();
            mCaptureSession[id].capture(captureBuilder.build(), mPostProcessor.getCaptureCallback(), mCaptureCallbackHandler);
        } else {
            mCaptureSession[id].capture(captureBuilder.build(), new CameraCaptureSession.CaptureCallback() {

                @Override
                public void onCaptureCompleted(CameraCaptureSession session,
                                               CaptureRequest request,
                                               TotalCaptureResult result) {
                    Log.d(TAG, "captureStillPictureForCommon onCaptureCompleted: " + id);
                }

                @Override
                public void onCaptureFailed(CameraCaptureSession session,
                                            CaptureRequest request,
                                            CaptureFailure result) {
                    Log.d(TAG, "captureStillPictureForCommon onCaptureFailed: " + id);
                    enableShutterAndVideoOnUiThread(id, true);
                }

                @Override
                public void onCaptureSequenceCompleted(CameraCaptureSession session, int
                        sequenceId, long frameNumber) {
                    Log.d(TAG, "captureStillPictureForCommon onCaptureSequenceCompleted: " + id);
                    if (!captureWaitImageReceive()) {
                        if (mUI.getCurrentProMode() != ProMode.MANUAL_MODE) {
                            unlockFocus(id);
                        } else {
                            enableShutterAndVideoOnUiThread(id,false);
                        }
                        Log.d(TAG,"onShutterButtonRelease");
                    }
                    if (mSettingsManager.isHeifWriterEncoding()) {
                        if (mHeifImage != null) {
                            try {
                                mHeifImage.getWriter().stop(3000);
                                mHeifImage.getWriter().close();
                                mActivity.getMediaSaveService().addHEIFImage(mHeifImage.getPath(),
                                        mHeifImage.getTitle(),mHeifImage.getDate(),null,mPictureSize.getWidth(),mPictureSize.getHeight(),
                                        mHeifImage.getOrientation(),null,mContentResolver,mOnMediaSavedListener,mHeifImage.getQuality(),"heic");
                            } catch (Exception e) {
                                e.printStackTrace();
                            } finally {
                                try{
                                    mHeifOutput.removeSurface(mHeifImage.getInputSurface());
                                    mCaptureSession[id].updateOutputConfiguration(mHeifOutput);
                                    mHeifImage = null;
                                } catch (CameraAccessException e) {
                                    e.printStackTrace();
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                    }
                }
            }, mCaptureCallbackHandler);
        }
    }

    private void captureVideoSnapshot(final int id) {
        Log.d(TAG, "captureVideoSnapshot " + id);
        try {
            if (null == mActivity || null == mCameraDevice[id] || mCurrentSession == null) {
                warningToast("Camera is not ready yet to take a video snapshot.");
                return;
            }
            checkAndPlayShutterSound(id);
            CaptureRequest.Builder captureBuilder = getRequestBuilder(
                    CameraDevice.TEMPLATE_VIDEO_SNAPSHOT,id);

            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, CameraUtil.getJpegRotation(id, mOrientation));
            captureBuilder.set(CaptureRequest.JPEG_THUMBNAIL_SIZE, mVideoSnapshotThumbSize);
            captureBuilder.set(CaptureRequest.JPEG_THUMBNAIL_QUALITY, (byte)80);
            applyVideoSnapshot(captureBuilder, id);
            if(mLockAFAE) {
                applySettingsForLockExposure(captureBuilder, id);
            }
            applyZoom(captureBuilder, id);
            if (mHighSpeedCapture) {
                captureBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                        mHighSpeedFPSRange);
            }

            if (mSettingsManager.isHeifWriterEncoding()) {
                long captureTime = System.currentTimeMillis();
                mNamedImages.nameNewImage(captureTime);
                NamedEntity name = mNamedImages.getNextNameEntity();
                String title = (name == null) ? null : name.title;
                long date = (name == null) ? -1 : name.date;
                String path = Storage.generateFilepath(title, "heif");
                String value = mSettingsManager.getValue(SettingsManager.KEY_JPEG_QUALITY);
                int quality = getQualityNumber(value);
                int orientation = CameraUtil.getJpegRotation(id,mOrientation);
                HeifWriter writer = createHEIFEncoder(path,mVideoSize.getWidth(),
                        mVideoSize.getHeight(),orientation,1,quality);
                if (writer != null) {
                    mLiveShotImage = new HeifImage(writer,path,title,date,orientation,quality);
                    Surface input = writer.getInputSurface();
                    mLiveShotOutput.addSurface(input);
                    try{
                        mCurrentSession.updateOutputConfiguration(mLiveShotOutput);
                        captureBuilder.addTarget(input);
                        writer.start();
                    } catch (IllegalStateException | IllegalArgumentException e) {
                        e.printStackTrace();
                    }
                }
            } else {
                if(mVideoSnapshotImageReader != null) {
                    captureBuilder.addTarget(mVideoSnapshotImageReader.getSurface());
                }
            }
            // send snapshot stream together with preview and video stream for snapshot request
            // stream is the surface for the app
            Surface surface = getPreviewSurfaceForSession(id);
            if (getFrameProcFilterId().size() == 1 && getFrameProcFilterId().get(0) ==
                    FrameProcessor.FILTER_MAKEUP) {
                captureBuilder.addTarget(mFrameProcessor.getInputSurfaces().get(0));
            } else {
                captureBuilder.addTarget(surface);
            }
            List<Surface> surfaces = new ArrayList<>();
            addPreviewSurface(captureBuilder, surfaces, id);

            mCurrentSession.capture(captureBuilder.build(),
                    new CameraCaptureSession.CaptureCallback() {

                        @Override
                        public void onCaptureCompleted(CameraCaptureSession session,
                                                       CaptureRequest request,
                                                       TotalCaptureResult result) {
                            Log.d(TAG, "captureVideoSnapshot onCaptureCompleted: " + id);
                        }

                        @Override
                        public void onCaptureFailed(CameraCaptureSession session,
                                                    CaptureRequest request,
                                                    CaptureFailure result) {
                            Log.d(TAG, "captureVideoSnapshot onCaptureFailed: " + id);
                        }

                        @Override
                        public void onCaptureSequenceCompleted(CameraCaptureSession session, int
                                sequenceId, long frameNumber) {
                            Log.d(TAG, "captureVideoSnapshot onCaptureSequenceCompleted: " + id);
                            if (mSettingsManager.isHeifWriterEncoding()) {
                                if (mLiveShotImage != null) {
                                    try {
                                        mLiveShotImage.getWriter().stop(3000);
                                        mLiveShotImage.getWriter().close();
                                        mLiveShotOutput.removeSurface(mLiveShotImage.getInputSurface());
                                        mCurrentSession.updateOutputConfiguration(mLiveShotOutput);
                                        mActivity.getMediaSaveService().addHEIFImage(mLiveShotImage.getPath(),
                                                mLiveShotImage.getTitle(),mLiveShotImage.getDate(),
                                                null,mVideoSize.getWidth(),mVideoSize.getHeight(),
                                                mLiveShotImage.getOrientation(),null,
                                                mContentResolver,mOnMediaSavedListener,
                                                mLiveShotImage.getQuality(),"heic");
                                        mLiveShotImage = null;
                                    } catch (TimeoutException | IllegalStateException e) {
                                        e.printStackTrace();
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                }
                            }
                        }
                    }, mCaptureCallbackHandler);
        } catch (CameraAccessException e) {
            Log.e(TAG, "captureVideoSnapshot failed: CameraAccessException");
            e.printStackTrace();
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "captureVideoSnapshot failed: IllegalArgumentException");
            e.printStackTrace();
        } catch (IllegalStateException e) {
            Log.e(TAG, "captureVideoSnapshot failed: IllegalStateException");
            e.printStackTrace();
        }
    }

    /**
     * Run the precapture sequence for capturing a still image. This method should be called when
     * we get a response in {@link #mCaptureCallback} from {@link #lockFocus()}.
     */
    private void runPrecaptureSequence(int id) {
        Log.d(TAG, "runPrecaptureSequence: " + id);
        if (!checkSessionAndBuilder(mCaptureSession[id], mPreviewRequestBuilder[id])) {
            return;
        }
        try {
            CaptureRequest.Builder builder = getRequestBuilder(id);
            builder.setTag(id);
            addPreviewSurface(builder, null, id);
            applySettingsForPrecapture(builder, id);
            CaptureRequest request = builder.build();
            mPrecaptureRequestHashCode[id] = request.hashCode();

            mState[id] = STATE_WAITING_PRECAPTURE;
            mCaptureSession[id].capture(request, mCaptureCallback, mCameraHandler);
            isFlashRequiredInDriver = false;
        } catch (CameraAccessException | IllegalStateException e) {
            e.printStackTrace();
        }
    }

    public CameraCharacteristics getMainCameraCharacteristics() {
        return mMainCameraCharacteristics;
    }

    /**
     * Sets up member variables related to camera.
     *
     * @param width  The width of available size for camera preview
     * @param height The height of available size for camera preview
     */
    private void setUpCameraOutputs(int imageFormat) {
        Log.d(TAG, "setUpCameraOutputs");
        CameraManager manager = (CameraManager) mActivity.getSystemService(Context.CAMERA_SERVICE);
        try {
            String[] cameraIdList = manager.getCameraIdList();
            //inti heifWriter and get input surface
            if (mSettingsManager.isHeifWriterEncoding()) {
                String tmpPath = mActivity.getCacheDir().getPath() + "/" + "heif.tmp";
                if (mInitHeifWriter != null) {
                    mInitHeifWriter.close();
                }
                mInitHeifWriter = createHEIFEncoder(tmpPath, mPictureSize.getWidth(),
                        mPictureSize.getHeight(), 0,1, 85);
            }
            for (int i = 0; i < cameraIdList.length; i++) {
                String cameraId = cameraIdList[i];

                CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
                int[] capabilities = characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);

                boolean foundDepth = false;
                for (int capability : capabilities) {
                    if (capability == CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_DEPTH_OUTPUT) {
                        DEPTH_CAM_ID = cameraId;
                        Log.d(TAG, "Found depth camera with id " + cameraId);
                        foundDepth = true;
                    }
                }

                if(foundDepth) {
                    continue;
                }
                if(i == getMainCameraId()) {
                    mBayerCameraRegion = characteristics.get(CameraCharacteristics
                            .SENSOR_INFO_ACTIVE_ARRAY_SIZE);
                    mMainCameraCharacteristics = characteristics;
                }
                StreamConfigurationMap map = characteristics.get(
                        CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (map == null) {
                    continue;
                }
                mCameraId[i] = cameraId;

                if (isClearSightOn()) {
                    if(i == getMainCameraId()) {
                        ClearSightImageProcessor.getInstance().init(map, mActivity,
                                mOnMediaSavedListener);
                        ClearSightImageProcessor.getInstance().setCallback(this);
                    }
                } else {
                    if ((imageFormat == ImageFormat.YUV_420_888 || imageFormat == ImageFormat.PRIVATE)
                            && i == getMainCameraId()) {
                        if (mImageReader[i] == null) {
                            if (mPostProcessor.isZSLEnabled()) {
                                mImageReader[i] = ImageReader.newInstance(
                                        mSupportedMaxPreviewSize.getWidth(),
                                        mSupportedMaxPreviewSize.getHeight(),
                                        imageFormat, MAX_IMAGEREADERS + 2);
                            } else {
                                mImageReader[i] = ImageReader.newInstance(mPictureSize.getWidth(),
                                        mPictureSize.getHeight(), imageFormat,
                                        MAX_IMAGEREADERS + 2);
                            }
                            mImageReader[i].setOnImageAvailableListener(
                                    mPostProcessor.getImageHandler(), mImageAvailableHandler);
                            mPostProcessor.onImageReaderReady(mImageReader[i],
                                    mSupportedMaxPreviewSize, mPictureSize);
                        }
                        if (mSaveRaw) {
                            if (mRawImageReader[i] == null) {
                                mRawImageReader[i] = ImageReader.newInstance(
                                        mSupportedRawPictureSize.getWidth(),
                                        mSupportedRawPictureSize.getHeight(),
                                        ImageFormat.RAW10, MAX_IMAGEREADERS + 2);
                                mPostProcessor.setRawImageReader(mRawImageReader[i]);
                            }
                        }
                    } else if (i == getMainCameraId()) {
                        ImageAvailableListener listener = new ImageAvailableListener(i) {
                            @Override
                            public void onImageAvailable(ImageReader reader) {
                                if (PersistUtil.isTraceEnable())
                                    Trace.beginSection("onImageAvailable save iamge");
                                if (captureWaitImageReceive()) {
                                    mHandler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            Log.d(TAG, "image available for cam enable shutter button " );
                                            mUI.enableShutter(true);
                                        }
                                    });
                                    unlockFocus(mCamId);
                                }
                                Log.d(TAG, "image available for cam: " + mCamId);
                                Image image = reader.acquireNextImage();
                                if (mLongshoting && (!mLongshotActive) &&
                                        image.getTimestamp() > mLastLongshotTimestamp) {
                                    image.close();
                                    Log.d(TAG, "image duplicate mLastLongshotTimestamp ");
                                    return;
                                }
                                if (isMpoOn()) {
                                    mMpoSaveHandler.obtainMessage(
                                            MpoSaveHandler.MSG_NEW_IMG, mCamId, 0, image).sendToTarget();
                                } else {
                                    mCaptureStartTime = System.currentTimeMillis();
                                    mNamedImages.nameNewImage(mCaptureStartTime);
                                    NamedEntity name = mNamedImages.getNextNameEntity();
                                    String title = (name == null) ? null : name.title;
                                    long date = (name == null) ? -1 : name.date;
                                    byte[] bytes = getJpegData(image);
                                    Log.i(TAG, "image format:" + image.getFormat());
                                    if (image.getFormat() == ImageFormat.RAW10) {
                                        mActivity.getMediaSaveService().addRawImage(bytes, title,
                                                "raw");
                                        image.close();
                                    } else if (image.getFormat() == ImageFormat.YUV_420_888) {
                                        Log.d(TAG,"YUV buffer received camera id =" + mCameraId);
                                        image.close();
                                    } else {
                                        int orientation = 0;
                                        ExifInterface exif = null;
                                        if (image.getFormat() != ImageFormat.HEIC) {
                                            exif = Exif.getExif(bytes);
                                            orientation = Exif.getOrientation(exif);
                                        } else {
                                            orientation = CameraUtil.getJpegRotation(getMainCameraId(),mOrientation);
                                        }

                                        if (mIntentMode != CaptureModule.INTENT_MODE_NORMAL &&
                                                mIntentMode != INTENT_MODE_STILL_IMAGE_CAMERA) {
                                            mJpegImageData = bytes;
                                            if (!mQuickCapture) {
                                                showCapturedReview(bytes, orientation);
                                            } else {
                                                onCaptureDone();
                                            }
                                        } else {
                                            String pictureFormat = "jpeg";
                                            if (image.getFormat() == ImageFormat.HEIC) {
                                                pictureFormat = "heic";
                                            }
                                            if (mIntentMode == INTENT_MODE_STILL_IMAGE_CAMERA) {
                                                mIntentMode = INTENT_MODE_NORMAL;
                                            }
                                            mActivity.getMediaSaveService().addImage(bytes, title, date,
                                                    null, image.getWidth(), image.getHeight(), orientation, exif,
                                                    mOnMediaSavedListener, mContentResolver,pictureFormat);

                                            if (mLongshotActive) {
                                                mLastJpegData = bytes;
                                            } else {
                                                if (imageFormat != ImageFormat.HEIC){
                                                    mActivity.updateThumbnail(bytes);
                                                }
                                            }
                                        }
                                        image.close();
                                    }
                                }
                                if (PersistUtil.isTraceEnable())
                                    Trace.endSection();
                            }
                        };
                        if (mImageReader[i] == null) {
                            mImageReader[i] = ImageReader.newInstance(mPictureSize.getWidth(),
                                    mPictureSize.getHeight(), imageFormat, MAX_IMAGEREADERS);
                            mImageReader[i].setOnImageAvailableListener(listener, mImageAvailableHandler);
                        }

                        for (int y = 0; y< mYUVCount; y++){
                            ImageAvailableListener yuvListener = new ImageAvailableListener(y){
                                @Override
                                public void onImageAvailable(ImageReader reader) {
                                    Log.d(TAG,"onImageAvailable cameraId ="+this.mCamId);
                                    if (captureWaitImageReceive()) {
                                        mHandler.post(new Runnable() {
                                            @Override
                                            public void run() {
                                                Log.d(TAG, "YUV image available for cam enable shutter button " );
                                                mUI.enableShutter(true);
                                            }
                                        });
                                    }
                                }
                            };

                            if (mYUVImageReader[y] == null) {
                                mYUVImageReader[y] = ImageReader.newInstance(mYUVsize[y].getWidth(),
                                        mYUVsize[y].getHeight(),
                                        ImageFormat.YUV_420_888, 3);
                                mYUVImageReader[y].setOnImageAvailableListener(yuvListener,
                                        mImageAvailableHandler);
                            }
                        }

                        if (mSaveRaw) {
                            if (mRawImageReader[i] == null) {
                                mRawImageReader[i] = ImageReader.newInstance(
                                        mSupportedRawPictureSize.getWidth(),
                                        mSupportedRawPictureSize.getHeight(),
                                        ImageFormat.RAW10, MAX_IMAGEREADERS);
                                mRawImageReader[i].setOnImageAvailableListener(listener,
                                        mImageAvailableHandler);
                            }
                        }
                    }
                }
            }
            mMediaRecorder = new MediaRecorder();
            mAutoFocusRegionSupported = mSettingsManager.isAutoFocusRegionSupported(mCurrentSceneMode.getCurrentId());
            mAutoExposureRegionSupported = mSettingsManager.isAutoExposureRegionSupported(mCurrentSceneMode.getCurrentId());
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    public static byte[] getYUVFromImage(Image image) {
        try{
            int width = image.getWidth();
            int height = image.getHeight();
            ByteBuffer dataY= image.getPlanes()[0].getBuffer();
            ByteBuffer dataUV = image.getPlanes()[2].getBuffer();
            dataY.rewind();
            dataUV.rewind();
            byte[] bytesY = new byte[dataY.remaining()];
            dataY.get(bytesY);
            byte[] bytesUV = new byte[dataUV.remaining()];
            dataUV.get(bytesUV);
            byte[] data = new byte[bytesY.length+bytesUV.length];
            System.arraycopy(bytesY,0,data,0,bytesY.length);
            System.arraycopy(bytesUV,0,data,bytesY.length,bytesUV.length);
            return data;
        }catch (IllegalStateException e) {
            return null;
        }
    }

    public static HeifWriter createHEIFEncoder(String path, int width, int height,
                                        int orientation, int imageCount, int quality) {
        HeifWriter heifWriter = null;
        try {
            HeifWriter.Builder builder =
                    new HeifWriter.Builder(path, width, height, HeifWriter.INPUT_MODE_SURFACE);
            builder.setQuality(quality);
            builder.setMaxImages(imageCount);
            builder.setPrimaryIndex(0);
            builder.setRotation(orientation);
            builder.setGridEnabled(true);
            heifWriter = builder.build();
        } catch (IOException | IllegalStateException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return heifWriter;
    }

    private void createVideoSnapshotImageReader() {
        if (mVideoSnapshotImageReader != null) {
            mVideoSnapshotImageReader.close();
        }
        if (mSettingsManager.isHeifWriterEncoding()) {
            String tmpPath = mActivity.getCacheDir().getPath() + "/" + "liveshot_heif.tmp";
            mLiveShotInitHeifWriter = createHEIFEncoder(tmpPath,mVideoSize.getWidth(),
                    mVideoSize.getHeight(),0, 1,85);
            return;
        }
        int format = ImageFormat.JPEG;
        if (mSettingsManager.isHeifHALEncoding()) {
            format = ImageFormat.HEIC;
        }
        mVideoSnapshotImageReader = ImageReader.newInstance(mVideoSnapshotSize.getWidth(),
                mVideoSnapshotSize.getHeight(),format, 2);
        mVideoSnapshotImageReader.setOnImageAvailableListener(
                new ImageReader.OnImageAvailableListener() {
                    @Override
                    public void onImageAvailable(ImageReader reader) {
                        Image image = reader.acquireNextImage();
                        mCaptureStartTime = System.currentTimeMillis();
                        mNamedImages.nameNewImage(mCaptureStartTime);
                        NamedEntity name = mNamedImages.getNextNameEntity();
                        String title = (name == null) ? null : name.title;
                        long date = (name == null) ? -1 : name.date;

                        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                        byte[] bytes = new byte[buffer.remaining()];
                        buffer.get(bytes);

                        int orientation = 0;
                        ExifInterface exif = null;
                        if (image.getFormat() != ImageFormat.HEIC){
                            exif = Exif.getExif(bytes);
                            orientation = Exif.getOrientation(exif);
                        } else {
                            orientation = CameraUtil.getJpegRotation(getMainCameraId(),mOrientation);
                        }



                        String saveFormat = image.getFormat() == ImageFormat.HEIC? "heic" : "jpeg";

                        mActivity.getMediaSaveService().addImage(bytes, title, date,
                                null, image.getWidth(), image.getHeight(), orientation, exif,
                                mOnMediaSavedListener, mContentResolver, saveFormat);

                        if (image.getFormat() != ImageFormat.HEIC){
                            mActivity.updateThumbnail(bytes);
                        }
                        image.close();
                    }
                }, mImageAvailableHandler);
    }

    /**
     * Unlock the focus. This method should be called when still image capture sequence is
     * finished.
     */
    public void unlockFocus(int id) {
        Log.d(TAG, "unlockFocus " + id);
        isFlashRequiredInDriver = false;
        if ((mCurrentSceneMode.mode == CameraMode.HFR) && isHighSpeedRateCapture()) {
            Log.d(TAG, "unlockFocus should not be triggered in HFR");
            return;
        }
        if (!checkSessionAndBuilder(mCaptureSession[id], mPreviewRequestBuilder[id])) {
            return;
        }
        try {
            if (mUI.getCurrentProMode() != ProMode.MANUAL_MODE) {
                CaptureRequest.Builder builder = getRequestBuilder(id);
                builder.setTag(id);
                addPreviewSurface(builder, null, id);
                applySettingsForUnlockFocus(builder, id);
                mCaptureSession[id].capture(builder.build(), mCaptureCallback, mCameraHandler);
            }

            mState[id] = STATE_PREVIEW;
            if (id == getMainCameraId()) {
                mActivity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (mUI.getCurrentProMode() != ProMode.MANUAL_MODE && !mLockAFAE)
                            mUI.clearFocus();
                    }
                });
            }
            if(!mLockAFAE) {
                mControlAFMode = CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE;
                mIsAutoFocusStarted = false;
            }
            applyFlash(mPreviewRequestBuilder[id], id);
            if(!mLockAFAE) {
                applySettingsForUnlockExposure(mPreviewRequestBuilder[id], id);
            }
            if (mSettingsManager.isDeveloperEnabled()) {
                applyCommonSettings(mPreviewRequestBuilder[id], id);
            }
            if(!mLockAFAE) {
                int afMode = (mSettingsManager.isDeveloperEnabled() && getDevAfMode() != -1) ?
                        getDevAfMode() : mControlAFMode;
                setAFModeToPreview(id, mUI.getCurrentProMode() == ProMode.MANUAL_MODE ?
                        CaptureRequest.CONTROL_AF_MODE_OFF : afMode);
            }
            mTakingPicture[id] = false;
            enableShutterAndVideoOnUiThread(id,false);
        } catch (NullPointerException | IllegalStateException | CameraAccessException e) {
            Log.w(TAG, "Session is already closed");
        }
    }

    public void enableShutterButtonOnMainThread(int id) {
        if (id == getMainCameraId()) {
            mActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (captureWaitImageReceive()) {
                        Log.d(TAG, "image available then enable shutter button " );
                        mUI.enableShutter(true);
                    }
                }
            });
        }
    }

    private void enableShutterAndVideoOnUiThread(int id,boolean force) {
        if (id == getMainCameraId()) {
            mActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mUI.stopSelfieFlash();
                    if (force || !captureWaitImageReceive()) {
                        mUI.enableShutter(true);
                    }
                    if (mDeepPortraitMode) {
                        mUI.enableVideo(false);
                    } else {
                        mUI.enableVideo(true);
                    }
                }
            });
        }
    }

    private void enableVideoButton(boolean enable) {
        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mUI.enableVideo(enable);
            }
        });
    }

    private boolean isMFNREnabled() {
        boolean mfnrEnable = false;
        if (mSettingsManager != null) {
            String mfnrValue = mSettingsManager.getValue(SettingsManager.KEY_CAPTURE_MFNR_VALUE);
            if (mfnrValue != null) {
                mfnrEnable = mfnrValue.equals("1");
            }
        }
        return mfnrEnable;
    }

    private boolean isHDREnable() {
        boolean hdrEnable = false;
        if (mSettingsManager != null) {
            String value = mSettingsManager.getValue(SettingsManager.KEY_SCENE_MODE);
            if (value != null) {
                int mode = Integer.parseInt(value);
                hdrEnable = (mode == CaptureRequest.CONTROL_SCENE_MODE_HDR);
            }
        }
        return hdrEnable;
    }

    private boolean captureWaitImageReceive() {
        return mIsSupportedQcfa || isMFNREnabled() || isHDREnable();
    }

    private Size parsePictureSize(String value) {
        int indexX = value.indexOf('x');
        int width = Integer.parseInt(value.substring(0, indexX));
        int height = Integer.parseInt(value.substring(indexX + 1));
        return new Size(width, height);
    }

    private void closeProcessors() {
        if(mPostProcessor != null) {
            mPostProcessor.onClose();
        }

        if(mFrameProcessor != null) {
            mFrameProcessor.onClose();
        }
    }

    public boolean isAllSessionClosed() {
        for (int i = MAX_NUM_CAM - 1; i >= 0; i--) {
            if (mCaptureSession[i] != null) {
                return false;
            }
        }
        return true;
    }

    private void closeSessions() {
        for (int i = MAX_NUM_CAM-1; i >= 0; i--) {
            if (null != mCaptureSession[i]) {
                if (mCamerasOpened) {
                    try {
                        if (mCurrentSession instanceof CameraConstrainedHighSpeedCaptureSession) {
                            List requestList = ((CameraConstrainedHighSpeedCaptureSession)
                                    mCurrentSession).createHighSpeedRequestList(
                                            mVideoRecordRequestBuilder.build());
                            mCurrentSession.captureBurst(requestList, null, mCameraHandler);
                        } else {
                            mCaptureSession[i].capture(mPreviewRequestBuilder[i].build(), null,
                                    mCameraHandler);
                        }
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    } catch (IllegalStateException e) {
                        e.printStackTrace();
                    } catch (IllegalArgumentException e) {
                        e.printStackTrace();
                    }
                }
                mCaptureSession[i].close();
                mCaptureSession[i] = null;
            }
        }
    }

    private void resetAudioMute() {
        if (isAudioMute()) {
            setMute(false, true);
        }
    }

    private void closeImageReader() {
        for (int i = MAX_NUM_CAM - 1; i >= 0; i--) {
            if (null != mImageReader[i]) {
                mImageReader[i].close();
                mImageReader[i] = null;
            }
            if (null != mRawImageReader[i]) {
                mRawImageReader[i].close();
                mRawImageReader[i] = null;
            }
        }
        for (int i = mYUVCount -1; i >= 0; i--) {
            if (null != mYUVImageReader[i]) {
                mYUVImageReader[i].close();
                mYUVImageReader[i] = null;
            }
        }
        if (null != mVideoSnapshotImageReader) {
            mVideoSnapshotImageReader.close();
            mVideoSnapshotImageReader = null;
        }
    }

    /**
     * Closes the current {@link CameraDevice}.
     */
    private void closeCamera() {
        Log.d(TAG, "closeCamera");

        closeProcessors();

        /* no need to set this in the callback and handle asynchronously. This is the same
        reason as why we release the semaphore here, not in camera close callback function
        as we don't have to protect the case where camera open() gets called during camera
        close(). The low level framework/HAL handles the synchronization for open()
        happens after close() */

        try {
            // Close camera starting with AUX first
            for (int i = MAX_NUM_CAM-1; i >= 0; i--) {
                if (null != mCameraDevice[i]) {
                    if (!mCameraOpenCloseLock.tryAcquire(2000, TimeUnit.MILLISECONDS)) {
                        Log.d(TAG, "Time out waiting to lock camera closing.");
                        throw new RuntimeException("Time out waiting to lock camera closing");
                    }
                    Log.d(TAG, "Closing camera: " + mCameraDevice[i].getId());

                    // session was closed here if intentMode is INTENT_MODE_VIDEO
                    if (mIntentMode != INTENT_MODE_VIDEO) {
                        try {
                            if (isAbortCapturesEnable() && mCaptureSession[i] != null) {
                                mCaptureSession[i].abortCaptures();
                                Log.d(TAG, "Closing camera call abortCaptures ");
                            }
                            if (isSendRequestAfterFlushEnable() && mCaptureSession[i] != null) {
                                Log.v(TAG, "Closing camera call setRepeatingRequest");
                                mCaptureSession[i].setRepeatingRequest(mPreviewRequestBuilder[i].build(),
                                        mCaptureCallback, mCameraHandler);
                            }
                        } catch (IllegalStateException | CameraAccessException e) {
                            e.printStackTrace();
                        }
                    }
                    mCameraDevice[i].close();
                    mCameraDevice[i] = null;
                    mCameraOpened[i] = false;
                    mCaptureSession[i] = null;
                }
            }

            mIsLinked = false;

            if (null != mMediaRecorder) {
                mMediaRecorder.release();
                mMediaRecorder = null;
            }
        } catch (InterruptedException e) {
            mCameraOpenCloseLock.release();
            throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
        } catch (IllegalStateException e) {
            e.printStackTrace();
        } finally {
            mCurrentSessionClosed = true;
            mCameraOpenCloseLock.release();
        }
    }

    /**
     * Lock the exposure for capture
     */
    private void lockExposure(int id) {
        if (!checkSessionAndBuilder(mCaptureSession[id], mPreviewRequestBuilder[id])) {
            return;
        }
        Log.d(TAG, "lockExposure: " + id);
        try {
            applySettingsForLockExposure(mPreviewRequestBuilder[id], id);
            mState[id] = STATE_WAITING_AE_LOCK;
            if (isHighSpeedRateCapture()) {
                List<CaptureRequest> slowMoRequests = mSuperSlomoCapture ?
                        createSSMBatchRequest(mVideoRecordRequestBuilder) :
                        ((CameraConstrainedHighSpeedCaptureSession) mCurrentSession).
                                createHighSpeedRequestList(mVideoRecordRequestBuilder.build());
                mCaptureSession[id].setRepeatingBurst(slowMoRequests, mCaptureCallback,
                        mCameraHandler);
            } else {
                mCaptureSession[id].setRepeatingRequest(mPreviewRequestBuilder[id].build(),
                        mCaptureCallback, mCameraHandler);
            }
        } catch (CameraAccessException | IllegalStateException e) {
            e.printStackTrace();
        }
    }

    private void applySettingsForLockFocus(CaptureRequest.Builder builder, int id) {
        builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START);
        applyAFRegions(builder, id);
        applyAERegions(builder, id);
        applyCommonSettings(builder, id);
    }

    private void applySettingsForCapture(CaptureRequest.Builder builder, int id) {
        builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE);
        applyJpegQuality(builder);
        applyFlash(builder, id);
        applyCommonSettings(builder, id);
        applySensorModeFS2(builder);
    }

    private void applySettingsForPrecapture(CaptureRequest.Builder builder, int id) {
        String redeye = mSettingsManager.getValue(SettingsManager.KEY_REDEYE_REDUCTION);
        if (redeye != null && redeye.equals("on") && !isFlashRequiredInDriver) {
            if (DEBUG)
            Log.d(TAG, "Red Eye Reduction is On. " +
                    "Don't set CONTROL_AE_PRECAPTURE_TRIGGER to Start");
        } else {
            builder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
        }

        // For long shot, torch mode is used
        if (!mLongshotActive) {
            applyFlash(builder, id);
        }

        applyCommonSettings(builder, id);
    }

    private void applySettingsForLockAndPrecapture(CaptureRequest.Builder builder, int id) {
        builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START);
        applyAFRegions(builder, id);
        applyAERegions(builder, id);
        builder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);

        applyFlash(builder, id);
        applyCommonSettings(builder, id);
    }

    private void applySettingsForLockExposure(CaptureRequest.Builder builder, int id) {
        builder.set(CaptureRequest.CONTROL_AE_LOCK, Boolean.TRUE);
    }

    private void applySettingsForUnlockExposure(CaptureRequest.Builder builder, int id) {
        builder.set(CaptureRequest.CONTROL_AE_LOCK, Boolean.FALSE);
    }

    private void applySettingsForUnlockFocus(CaptureRequest.Builder builder, int id) {
        if(!mLockAFAE) {
            builder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CaptureRequest.CONTROL_AF_TRIGGER_CANCEL);
        }
        applyFlash(builder, id);
        applyCommonSettings(builder, id);
    }

    private void applySettingsForAutoFocus(CaptureRequest.Builder builder, int id) {
        builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest
                .CONTROL_AF_TRIGGER_START);
        if (mCurrentSceneMode.mode == CameraMode.VIDEO ||
                mCurrentSceneMode.mode == CameraMode.HFR) {
            Range fpsRange = mHighSpeedCapture ? mHighSpeedFPSRange : new Range(30, 30);
            builder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, fpsRange);
        }
        applyAFRegions(builder, id);
        applyAERegions(builder, id);
        applyCommonSettings(builder, id);
    }

    private void applySettingsForJpegInformation(CaptureRequest.Builder builder, int id) {
        Location location = mLocationManager.getCurrentLocation();
        if(location != null) {
            // make copy so that we don't alter the saved location since we may re-use it
            location = new Location(location);
            // workaround for Google bug. Need to convert timestamp from ms -> sec
            location.setTime(location.getTime()/1000);
            builder.set(CaptureRequest.JPEG_GPS_LOCATION, location);
            Log.d(TAG, "gps: " + location.toString());
        } else {
            Log.d(TAG, "no location - getRecordLocation: " + getRecordLocation());
        }

        builder.set(CaptureRequest.JPEG_ORIENTATION, CameraUtil.getJpegRotation(id, mOrientation));
        builder.set(CaptureRequest.JPEG_THUMBNAIL_SIZE, mPictureThumbSize);
        builder.set(CaptureRequest.JPEG_THUMBNAIL_QUALITY, (byte)80);
    }

    private void applyVideoSnapshot(CaptureRequest.Builder builder, int id) {
        if(!mLockAFAE) {
            builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
        }else{
            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO);
        }
        applyFaceDetection(builder);
        applyColorEffect(builder);
        applyVideoFlash(builder, id);
        applyVideoStabilization(builder);
        applyVideoEIS(builder);
    }

    private void applySessionParameters(CaptureRequest.Builder builder){
        applyEarlyPCR(builder);
        applyMctf(builder);
    }

    private void applyMctf(CaptureRequest.Builder builder){
        //add for mctf tag
        if(mSettingsManager.isSwMctfSupported() && (mCurrentSceneMode.mode == CameraMode.VIDEO || mCurrentSceneMode.mode == CameraMode.HFR)){
            int mctfVaule = PersistUtil.mctfValue();
            try {
                builder.set(CaptureModule.mctf, (byte)(mctfVaule == 1 ? 0x01 : 0x00));
            } catch (IllegalArgumentException e) {
                Log.d(TAG, "mctf no vendor tag");
            }
        }
    }

    private void applyCommonSettings(CaptureRequest.Builder builder, int id) {
        builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
        builder.set(CaptureRequest.CONTROL_AF_MODE, mControlAFMode);
        applyAfModes(builder);
        applyFaceDetection(builder);
        applyTouchTrackFocus(builder);
        applyWhiteBalance(builder);
        applyExposure(builder);
        applyIso(builder);
        applyColorEffect(builder);
        applySceneMode(builder);
        applyZoom(builder, id);
        applyInstantAEC(builder);
        applySaturationLevel(builder);
        applyAntiBandingLevel(builder);
        applySharpnessControlModes(builder);
        applyExposureMeteringModes(builder);
        applyHistogram(builder);
        applyAWBCCTAndAgain(builder);
        applyBGStats(builder);
        applyBEStats(builder);
        applyWbColorTemperature(builder);
        applyToneMapping(builder);
    }

    /**
     * Starts a background thread and its {@link Handler}.
     */
    private void startBackgroundThread() {
        mCameraThread = new HandlerThread("CameraBackground");
        mCameraThread.start();
        mImageAvailableThread = new HandlerThread("CameraImageAvailable");
        mImageAvailableThread.start();
        mCaptureCallbackThread = new HandlerThread("CameraCaptureCallback");
        mCaptureCallbackThread.start();
        mMpoSaveThread = new HandlerThread("MpoSaveHandler");
        mMpoSaveThread.start();

        mCameraHandler = new MyCameraHandler(mCameraThread.getLooper());
        mImageAvailableHandler = new Handler(mImageAvailableThread.getLooper());
        mCaptureCallbackHandler = new Handler(mCaptureCallbackThread.getLooper());
        mMpoSaveHandler = new MpoSaveHandler(mMpoSaveThread.getLooper());
    }

    /**
     * Stops the background thread and its {@link Handler}.
     */
    private void stopBackgroundThread() {
        mCameraThread.quitSafely();
        mImageAvailableThread.quitSafely();
        mCaptureCallbackThread.quitSafely();
        mMpoSaveThread.quitSafely();

        try {
            mCameraThread.join();
            mCameraThread = null;
            mCameraHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        try {
            mImageAvailableThread.join();
            mImageAvailableThread = null;
            mImageAvailableHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        try {
            mCaptureCallbackThread.join();
            mCaptureCallbackThread = null;
            mCaptureCallbackHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        try {
            mMpoSaveThread.join();
            mMpoSaveThread = null;
            mMpoSaveHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void openCamera(int id) {
        if (mPaused) {
            return;
        }
        Log.d(TAG, "openCamera " + id);
        CameraManager manager;
        try {
            manager = (CameraManager) mActivity.getSystemService(Context.CAMERA_SERVICE);
            mCameraId[id] = manager.getCameraIdList()[id];
            if (!mCameraOpenCloseLock.tryAcquire(5000, TimeUnit.MILLISECONDS)) {
                Log.d(TAG, "Time out waiting to lock camera opening.");
                throw new RuntimeException("Time out waiting to lock camera opening");
            }
            manager.openCamera(mCameraId[id], mStateCallback, mCameraHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onPreviewFocusChanged(boolean previewFocused) {
        mUI.onPreviewFocusChanged(previewFocused);
    }

    @Override
    public void onPauseBeforeSuper() {
        cancelTouchFocus();
        mPaused = true;
        mToast = null;
        mUI.onPause();
        if (mIsRecordingVideo) {
            stopRecordingVideo(getMainCameraId());
        } else if (!mIsCloseCamera){
            if (mIsPreviewingVideo && !mIsRecordingVideo) {
                setVideoFlashOff();
            }
            if (mCurrentSession != null) {
                try {
                    mCurrentSession.abortCaptures();
                    mCurrentSession.stopRepeating();
                } catch (CameraAccessException|IllegalStateException e) {
                    e.printStackTrace();
                }
            }
        }
        if (mIsPreviewingVideo && !mIsRecordingVideo) {
            exitVideoModule();
        }
        mIsPreviewingVideo = false;
        if (mSoundPlayer != null) {
            mSoundPlayer.release();
            mSoundPlayer = null;
        }
        if (selfieThread != null) {
            selfieThread.interrupt();
        }
        resetScreenOn();
        mUI.stopSelfieFlash();
    }

    @Override
    public void onPauseAfterSuper() {
        onPauseAfterSuper(true);
    }

    private void onPauseAfterSuper(boolean isExitCamera) {
        Log.d(TAG, "onPause " + (isExitCamera ? "exit camera" : ""));
        if (isExitCamera) {
            writeXMLForWarmAwb();
            if(mLockAFAE) {
                mLockAFAE = false;
                updateLockAFAEVisibility();
            }
        }
        if (mLocationManager != null) mLocationManager.recordLocation(false);
        if(isClearSightOn()) {
            ClearSightImageProcessor.getInstance().close();
        }
        if (mInitHeifWriter != null) {
            mInitHeifWriter.close();
        }
        if(mIsCloseCamera) {
            closeCamera();
            mUI.showPreviewCover();
            mUI.hideSurfaceView();
        }else{
            closeProcessors();
        }
        resetAudioMute();
        mUI.releaseSoundPool();
        if (mUI.getGLCameraPreview() != null) {
            mUI.getGLCameraPreview().onPause();
        }

        mZoomValue = 1f;
        mUI.updateZoomSeekBar(1.0f);
        mFirstPreviewLoaded = false;
        if (isExitCamera && mIsCloseCamera) {
            stopBackgroundThread();
        }
        setProModeVisible();
        closeVideoFileDescriptor();
        if (mIntentMode != CaptureModule.INTENT_MODE_NORMAL
                && isExitCamera && mJpegImageData != null) {
            mActivity.setResultEx(Activity.RESULT_CANCELED, new Intent());
            mActivity.finish();
        }
        closeImageReader();
        mJpegImageData = null;
    }

    public void onResumeBeforeSuper() {
        // must change cameraId before "mPaused = false;"
        int facingOfIntentExtras = CameraUtil.getFacingOfIntentExtras(mActivity);
        if (facingOfIntentExtras != -1) {
            mCurrentSceneMode.setSwithCameraId(facingOfIntentExtras);
        }
        mPaused = false;
        for (int i = 0; i < MAX_NUM_CAM; i++) {
            if(mIsCloseCamera) {
                mCameraOpened[i] = false;
            }
            mTakingPicture[i] = false;
        }
        for (int i = 0; i < MAX_NUM_CAM; i++) {
            mState[i] = STATE_PREVIEW;
        }
        mLongshotActive = false;
        if(mIsCloseCamera) {
            updatePreviewSurfaceReadyState(false);
        }
    }

    private void cancelTouchFocus() {
        if (getCameraMode() == DUAL_MODE) {
            if(mState[BAYER_ID] == STATE_WAITING_TOUCH_FOCUS) {
                cancelTouchFocus(BAYER_ID);
            } else if (mState[MONO_ID] == STATE_WAITING_TOUCH_FOCUS) {
                cancelTouchFocus(MONO_ID);
            }
        } else {
            if (mState[getMainCameraId()] == STATE_WAITING_TOUCH_FOCUS ||
                    mState[getMainCameraId()] == STATE_PREVIEW || mLockAFAE) {
                cancelTouchFocus(getMainCameraId());
            }
        }
    }

    private ArrayList<Integer> getFrameProcFilterId() {
        ArrayList<Integer> filters = new ArrayList<Integer>();

        if(mDeepPortraitMode) {
            filters.add(FrameProcessor.FILTER_DEEP_PORTRAIT);
            return filters;
        }

        String scene = mSettingsManager.getValue(SettingsManager.KEY_MAKEUP);
        if(scene != null && !scene.equalsIgnoreCase("0")) {
            filters.add(FrameProcessor.FILTER_MAKEUP);
        }
        if(isTrackingFocusSettingOn()) {
            filters.add(FrameProcessor.LISTENER_TRACKING_FOCUS);
        }
        return filters;
    }

    public boolean isT2TFocusSettingOn() {
        try {
            String value = mSettingsManager.getValue(SettingsManager.KEY_TOUCH_TRACK_FOCUS);
            if (value != null && value.equals("on")) {
                return true;
            }
        } catch (Exception e) {
        }
        return false;
    }

    public boolean isTrackingFocusSettingOn() {
        String scene = mSettingsManager.getValue(SettingsManager.KEY_SCENE_MODE);
        try {
            int mode = Integer.parseInt(scene);
            if (mode == SettingsManager.SCENE_MODE_TRACKINGFOCUS_INT) {
                return true;
            }
        } catch (Exception e) {
        }
        return false;
    }

    public void setRefocusLastTaken(final boolean value) {
        mIsRefocus = value;
        mActivity.runOnUiThread(new Runnable() {
            public void run() {
                mUI.showRefocusToast(value);
            }
        });
    }

    private int getPostProcFilterId(int mode) {
        if (mode == SettingsManager.SCENE_MODE_OPTIZOOM_INT) {
            return PostProcessor.FILTER_OPTIZOOM;
        } else if (mode == SettingsManager.SCENE_MODE_NIGHT_INT && StillmoreFilter.isSupportedStatic()) {
            return PostProcessor.FILTER_STILLMORE;
        } else if (mode == SettingsManager.SCENE_MODE_CHROMAFLASH_INT && ChromaflashFilter.isSupportedStatic()) {
            return PostProcessor.FILTER_CHROMAFLASH;
        } else if (mode == SettingsManager.SCENE_MODE_BLURBUSTER_INT && BlurbusterFilter.isSupportedStatic()) {
            return PostProcessor.FILTER_BLURBUSTER;
        } else if (mode == SettingsManager.SCENE_MODE_UBIFOCUS_INT && UbifocusFilter.isSupportedStatic()) {
            return PostProcessor.FILTER_UBIFOCUS;
        } else if (mode == SettingsManager.SCENE_MODE_SHARPSHOOTER_INT && SharpshooterFilter.isSupportedStatic()) {
            return PostProcessor.FILTER_SHARPSHOOTER;
        } else if (mode == SettingsManager.SCENE_MODE_BESTPICTURE_INT) {
            return PostProcessor.FILTER_BESTPICTURE;
        } else if (mode == SettingsManager.SCENE_MODE_DEEPZOOM_INT) {
            return PostProcessor.FILTER_DEEPZOOM;
        }
        return PostProcessor.FILTER_NONE;
    }

    private void initializeValues() {
        initYUVCallbackParam();
        updatePictureSize();
        updateVideoSize();
        updateVideoSnapshotSize();
        updateTimeLapseSetting();
        estimateJpegFileSize();
        updateMaxVideoDuration();
        mSettingsManager.filterPictureFormatByIntent(mIntentMode);
    }

    private void initYUVCallbackParam(){
        if (mCurrentSceneMode.mode == CameraMode.SAT){
            mYUVCount = 3;
        } else if(mCurrentSceneMode.mode == CameraMode.RTB){
            mYUVCount = 2;
        }
        Log.i(TAG,"init, callbackenable:" + PersistUtil.getYUVCallbackEnable() + ",mode:" +  mCurrentSceneMode.mode);
        mYUVCallback = PersistUtil.getYUVCallbackEnable() && (mCurrentSceneMode.mode == CameraMode.SAT || mCurrentSceneMode.mode == CameraMode.RTB);
    }

    private void updatePreviewSize() {
        int width = mPreviewSize.getWidth();
        int height = mPreviewSize.getHeight();

        String makeup = mSettingsManager.getValue(SettingsManager.KEY_MAKEUP);
        boolean makeupOn = makeup != null && !makeup.equals("0");
        if (makeupOn) {
            width = mVideoSize.getWidth();
            height = mVideoSize.getHeight();
        }
        Point previewSize = PersistUtil.getCameraPreviewSize();
        if (previewSize != null) {
            width = previewSize.x;
            height = previewSize.y;
        }

        mPreviewSize = new Size(width, height);
        if (mCurrentSceneMode.mode == CameraMode.VIDEO || mCurrentSceneMode.mode == CameraMode.HFR) {
            mUI.setPreviewSize(mVideoPreviewSize.getWidth(), mVideoPreviewSize.getHeight());
        } else if (!mDeepPortraitMode) {
            mUI.setPreviewSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
        }
    }

    private void openProcessors() {
        String scene = mSettingsManager.getValue(SettingsManager.KEY_SCENE_MODE);
        mIsSupportedQcfa = mSettingsManager.getQcfaPrefEnabled() &&
                mSettingsManager.getIsSupportedQcfa(getMainCameraId());
        // add the judgement condition for special qcfa
        if (mIsSupportedQcfa) {
            Size qcfaSize = mSettingsManager.getQcfaSupportSize();
            if (mPictureSize.getWidth() <= qcfaSize.getWidth() / 2 &&
                    mPictureSize.getHeight() <= qcfaSize.getHeight() / 2) {
                mIsSupportedQcfa = false;
            }
        }
        boolean isFlashOn = false;
        boolean isMakeupOn = false;
        boolean isSelfieMirrorOn = false;
        if(mPostProcessor != null) {
            String selfieMirror = mSettingsManager.getValue(SettingsManager.KEY_SELFIEMIRROR);
            if(selfieMirror != null && selfieMirror.equalsIgnoreCase("on")) {
                isSelfieMirrorOn = true;
            }
            String makeup = mSettingsManager.getValue(SettingsManager.KEY_MAKEUP);
            if(makeup != null && !makeup.equals("0")) {
                isMakeupOn = true;
            }
            String flashMode = mSettingsManager.getValue(SettingsManager.KEY_FLASH_MODE);
            if(flashMode != null && flashMode.equalsIgnoreCase("on")) {
                isFlashOn = true;
            }

            mSaveRaw = isRawCaptureOn();
            int filterMode = PostProcessor.FILTER_NONE;
            if (scene != null) {
                int mode = Integer.parseInt(scene);
                filterMode = getPostProcFilterId(mode);
                Log.d(TAG, "Chosen postproc filter id : " + filterMode);
                if (mode == SettingsManager.SCENE_MODE_DEEPZOOM_INT) {
                    String maxSize = mSettingsManager.getEntryValues(
                            SettingsManager.KEY_PICTURE_SIZE)[0].toString();
                    mSettingsManager.setValue(SettingsManager.KEY_PICTURE_SIZE, maxSize);
                }
            }
            mPostProcessor.onOpen(filterMode, isFlashOn, isTrackingFocusSettingOn(),
                    isT2TFocusSettingOn(), isMakeupOn, isSelfieMirrorOn,mSaveRaw,
                    mIsSupportedQcfa, mDeepPortraitMode);
        }
        if(mFrameProcessor != null) {
            mFrameProcessor.onOpen(getFrameProcFilterId(), mPreviewSize);
        }

        if(mPostProcessor.isZSLEnabled() && !isActionImageCapture()) {
            mChosenImageFormat = ImageFormat.PRIVATE;
        } else if(mPostProcessor.isFilterOn() || getFrameFilters().size() != 0 || mPostProcessor.isSelfieMirrorOn()) {
            mChosenImageFormat = ImageFormat.YUV_420_888;
        } else if(mSettingsManager.isHeifHALEncoding()) {
            mChosenImageFormat = ImageFormat.HEIC;
        } else {
            mChosenImageFormat = ImageFormat.JPEG;
        }
        setUpCameraOutputs(mChosenImageFormat);

    }

    private void loadSoundPoolResource() {
        String timer = mSettingsManager.getValue(SettingsManager.KEY_TIMER);
        int seconds = Integer.parseInt(timer);
        if (seconds > 0) {
            mUI.initCountDownView();
        }
    }

    @Override
    public void onResumeAfterSuper() {
        onResumeAfterSuper(false);
    }

    private void onResumeAfterSuper(boolean resumeFromRestartAll) {
        Log.d(TAG, "onResume " + (mCurrentSceneMode != null ? mCurrentSceneMode.mode : "null")
                + (resumeFromRestartAll ? " isResumeFromRestartAll" : ""));
        reinit();
        setCameraModeSwitcherAllowed(false);
        if(mCurrentSceneMode.mode == CameraMode.VIDEO ||
                mCurrentSceneMode.mode == CameraMode.HFR){
            enableVideoButton(false);//disable the video button before media recorder is ready
        }
        if (mCurrentSceneMode.mode != CameraMode.HFR){
            mHighSpeedCapture = false;
        }
        checkRTBCameraId();
        if (!isBackCamera() && !frontIsAllowed()) {
            Log.d(TAG, "Current Mode " + mCurrentSceneMode.mode + "not support Front camera");
            if (!resumeFromRestartAll && mIsCloseCamera) {
                startBackgroundThread();
            }
            mUI.switchToPhotoModeDueToError(true);
            return;
        }
        mDeepPortraitMode = isDeepPortraitMode();
        initializeValues();
        updatePreviewSize();

        // Set up sound playback for shutter button, video record and video stop
        if (mSoundPlayer == null) {
            mSoundPlayer = SoundClips.getPlayer(mActivity);
        }

        updateSaveStorageState();
        setDisplayOrientation();
        if (!resumeFromRestartAll) {
            startBackgroundThread();
        }
        openProcessors();
        loadSoundPoolResource();
        if(mIsCloseCamera) {
            Message msg = Message.obtain();
            msg.what = OPEN_CAMERA;
            msg.arg1 = mCurrentSceneMode.getCurrentId();
            Log.d(TAG, "open is " + msg.arg1);
            if (mCameraHandler != null) {
                mCameraHandler.sendMessage(msg);
            }
        }
        if (mDeepPortraitMode) {
            mUI.startDeepPortraitMode(mPreviewSize);
            if (mUI.getGLCameraPreview() != null) {
                mUI.getGLCameraPreview().onResume();
            }
            mUI.enableVideo(false);
        } else {
            mUI.showSurfaceView();
            mUI.stopDeepPortraitMode();
        }

        if (!mFirstTimeInitialized) {
            initializeFirstTime();
        } else {
            initializeSecondTime();
        }
        mUI.reInitUI();
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mActivity.updateStorageSpaceAndHint();
            }
        });
        mUI.enableShutter(true);
        setProModeVisible();
        updateZoomSeekBarVisible();
        updateZoom();//update zoom after mSettingsManager init
        String scene = mSettingsManager.getValue(SettingsManager.KEY_SCENE_MODE);
        if (Integer.parseInt(scene) != SettingsManager.SCENE_MODE_UBIFOCUS_INT) {
            setRefocusLastTaken(false);
        }
        updateMFNRText();//this must before showRelatedIcons, color filter based on mfnr
        mUI.showRelatedIcons(mCurrentSceneMode.mode);
        if(isPanoSetting(scene)) {
            if (mIntentMode != CaptureModule.INTENT_MODE_NORMAL) {
                mSettingsManager.setValue(
                        SettingsManager.KEY_SCENE_MODE, ""+SettingsManager.SCENE_MODE_AUTO_INT);
                showToast("Pano Capture is not supported in this mode");
            } else {
                mActivity.onModuleSelected(ModuleSwitcher.PANOCAPTURE_MODULE_INDEX);
            }
        }
        if(!mIsCloseCamera){
            createSessions();
        }
    }

    private void checkRTBCameraId() {
        CameraManager manager = (CameraManager) mActivity.getSystemService(Context.CAMERA_SERVICE);
        CameraCharacteristics characteristics;
        try {
            characteristics = manager.getCameraCharacteristics(String.valueOf(CURRENT_ID));
            Byte cameraType = characteristics.get(CaptureModule.logical_camera_type);
            Log.v(TAG, "checkRTBCameraId cameraType :" + cameraType);
            if (cameraType != null) {
                switch (cameraType) {
                    case CaptureModule.TYPE_DEFAULT:
                    case CaptureModule.TYPE_SAT:
                    case CaptureModule.TYPE_VR360:
                        mIsRTBCameraId = false;
                        break;
                    case CaptureModule.TYPE_RTB:
                        mIsRTBCameraId = true;
                        break;
                }
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "checkRTBCameraId no vendorTag logical_camera_type:" + logical_camera_type);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration config) {
        Log.v(TAG, "onConfigurationChanged");
        setDisplayOrientation();
    }

    @Override
    public void onStop() {

    }

    @Override
    public void onDestroy() {
        if(mFrameProcessor != null){
            mFrameProcessor.onDestory();
        }
        mSettingsManager.unregisterListener(this);
        mSettingsManager.unregisterListener(mUI);
        mSettingsManager.destroyCaptureModule();
    }

    @Override
    public void installIntentFilter() {

    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {

    }

    @Override
    public boolean onBackPressed() {
        return mUI.onBackPressed();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                if (CameraUtil.volumeKeyShutterDisable(mActivity)) {
                    return false;
                }
            case KeyEvent.KEYCODE_FOCUS:
                if (mFirstTimeInitialized) {
                    if (event.getRepeatCount() == 0) {
                        onShutterButtonFocus(true);
                    }
                    return true;
                }
                return false;
            case KeyEvent.KEYCODE_CAMERA:
                if (mFirstTimeInitialized && event.getRepeatCount() == 0) {
                    onShutterButtonClick();
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
                if (mFirstTimeInitialized && event.getRepeatCount() == 0) {
                    onShutterButtonClick();
                }
                return true;
            case KeyEvent.KEYCODE_MEDIA_RECORD:
                if (mFirstTimeInitialized && event.getRepeatCount() == 0) {
                    onVideoButtonClick();
                }
                return true;
        }
        return false;
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                if (mFirstTimeInitialized
                        && !CameraUtil.volumeKeyShutterDisable(mActivity)) {
                    onShutterButtonClick();
                    return true;
                }
                return false;
            case KeyEvent.KEYCODE_FOCUS:
                if (mFirstTimeInitialized) {
                    onShutterButtonFocus(false);
                }
                return true;
        }
        return false;
    }

    @Override
    public int onZoomChanged(int requestedZoom) {
        return 0;
    }

    @Override
    public void onZoomChanged(float requestedZoom) {
        if (mIsRTBCameraId) return;
        mZoomValue = requestedZoom;
        mUI.updateZoomSeekBar(mZoomValue);
        applyZoomAndUpdate();
    }

    public void onZoomEnd() {
        if (mLockAFAE) {
            mUI.setFocusPosition(mClickPosition[0], mClickPosition[1]);
            mUI.onFocusStarted();
            mUI.onFocusSucceeded(false);
        }
    }

    public void updateZoomChanged(float requestedZoom) {
        if (Math.abs(mZoomValue - requestedZoom) > 0.05) {
            mZoomValue = requestedZoom;
            applyZoomAndUpdate();
        }
    }

    private boolean isInMode(int cameraId) {
        if (isBackCamera()) {
            switch (getCameraMode()) {
                case DUAL_MODE:
                    return cameraId == BAYER_ID || cameraId == MONO_ID;
                case BAYER_MODE:
                    return cameraId == BAYER_ID;
                case MONO_MODE:
                    return cameraId == MONO_ID;
                case SWITCH_MODE:
                    return cameraId == SWITCH_ID;
            }
        } else if (SWITCH_ID != -1) {
            return cameraId == SWITCH_ID;
        } else {
            return cameraId == FRONT_ID;
        }
        return false;
    }

    @Override
    public boolean isImageCaptureIntent() {
        return false;
    }

    @Override
    public boolean isCameraIdle() {
        return true;
    }

    @Override
    public void onCaptureDone() {
        if (mPaused) {
            return;
        }

        byte[] data = mJpegImageData;

        if (mCropValue == null) {
            // First handle the no crop case -- just return the value.  If the
            // caller specifies a "save uri" then write the data to its
            // stream. Otherwise, pass back a scaled down version of the bitmap
            // directly in the extras.
            if (mSaveUri != null) {
                OutputStream outputStream = null;
                try {
                    outputStream = mContentResolver.openOutputStream(mSaveUri);
                    outputStream.write(data);
                    outputStream.close();

                    mActivity.setResultEx(Activity.RESULT_OK);
                    mActivity.finish();
                } catch (IOException ex) {
                    // ignore exception
                } finally {
                    CameraUtil.closeSilently(outputStream);
                }
            } else {
                ExifInterface exif = Exif.getExif(data);
                int orientation = Exif.getOrientation(exif);
                Bitmap bitmap = CameraUtil.makeBitmap(data, 50 * 1024);
                bitmap = CameraUtil.rotate(bitmap, orientation);
                mActivity.setResultEx(Activity.RESULT_OK,
                        new Intent("inline-data").putExtra("data", bitmap));
                mActivity.finish();
            }
        } else {
            // Save the image to a temp file and invoke the cropper
            Uri tempUri = null;
            FileOutputStream tempStream = null;
            try {
                File path = mActivity.getFileStreamPath(sTempCropFilename);
                path.delete();
                tempStream = mActivity.openFileOutput(sTempCropFilename, 0);
                tempStream.write(data);
                tempStream.close();
                tempUri = Uri.fromFile(path);
            } catch (FileNotFoundException ex) {
                mActivity.setResultEx(Activity.RESULT_CANCELED);
                mActivity.finish();
                return;
            } catch (IOException ex) {
                mActivity.setResultEx(Activity.RESULT_CANCELED);
                mActivity.finish();
                return;
            } finally {
                CameraUtil.closeSilently(tempStream);
            }

            Bundle newExtras = new Bundle();
            if (mCropValue.equals("circle")) {
                newExtras.putString("circleCrop", "true");
            }
            if (mSaveUri != null) {
                newExtras.putParcelable(MediaStore.EXTRA_OUTPUT, mSaveUri);
            } else {
                newExtras.putBoolean(CameraUtil.KEY_RETURN_DATA, true);
            }
            if (mActivity.isSecureCamera()) {
                newExtras.putBoolean(CameraUtil.KEY_SHOW_WHEN_LOCKED, true);
            }

            // TODO: Share this constant.
            final String CROP_ACTION = "com.android.camera.action.CROP";
            Intent cropIntent = new Intent(CROP_ACTION);

            cropIntent.setData(tempUri);
            cropIntent.putExtras(newExtras);

            mActivity.startActivityForResult(cropIntent, REQUEST_CROP);
        }
    }

    public void onRecordingDone(boolean valid) {
        Intent resultIntent = new Intent();
        int resultCode;
        if (valid) {
            resultCode = Activity.RESULT_OK;
            resultIntent.setData(mCurrentVideoUri);
            resultIntent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } else {
            resultCode = Activity.RESULT_CANCELED;
        }
        mActivity.setResultEx(resultCode, resultIntent);
        mActivity.finish();
    }

    public void onRetakeVideo() {
        mTempHoldVideoInVideoIntent = false;
        createSessions();
    }

    @Override
    public void onCaptureCancelled() {

    }

    @Override
    public void onCaptureRetake() {

    }

    @Override
    public void cancelAutoFocus() {

    }

    @Override
    public void stopPreview() {

    }

    @Override
    public int getCameraState() {
        return 0;
    }

    @Override
    public void onSingleTapUp(View view, int x, int y) {
        if (mPaused || !mCamerasOpened || !mFirstTimeInitialized || !mAutoFocusRegionSupported
                || !mAutoExposureRegionSupported || !isTouchToFocusAllowed()
                || mCaptureSession[getMainCameraId()] == null) {
            return;
        }
        Log.d(TAG, "onSingleTapUp " + x + " " + y);
        int currentId = mCurrentSceneMode.getCurrentId();
        if(mLockAFAE) {
            mLockAFAE = false;
            cancelTouchFocus(mCurrentSceneMode.getCurrentId());
            applySettingsForUnlockExposure(mPreviewRequestBuilder[mCurrentSceneMode.getCurrentId()], mCurrentSceneMode.getCurrentId());
            updateLockAFAEVisibility();
        }
        int[] newXY = {x, y};
        if (mUI.isOverControlRegion(newXY)) return;
        if (!mUI.isOverSurfaceView(newXY)) return;

        if (mT2TFocusRenderer != null && mT2TFocusRenderer.isShown()) {
            mT2TFocusRenderer.onSingleTapUp(x, y);
            triggerTouchFocus(x, y, TouchTrackFocusRenderer.TRACKER_CMD_REG);
            return;
        }

        mUI.setFocusPosition(x, y);
        x = newXY[0];
        y = newXY[1];
        mInTAF = true;
        mUI.onFocusStarted();
        triggerFocusAtPoint(x, y, mCurrentSceneMode.getCurrentId());
    }

    @Override
    public void onLongPress(View view, int x, int y) {
        if (mPaused || !mCamerasOpened || !mFirstTimeInitialized || !mAutoFocusRegionSupported
                || !mAutoExposureRegionSupported || !isTouchToFocusAllowed()) {
            return;
        }
        Log.d(TAG, "onLongPress " + x + " " + y);
        mLockAFAE = true;

        mClickPosition[0] = x;
        mClickPosition[1] = y;
        int[] newXY = {x, y};
        if (mUI.isOverControlRegion(newXY)) return;
        if (!mUI.isOverSurfaceView(newXY)) return;
        mUI.setFocusPosition(x, y);
        mUI.onFocusStarted();
        triggerFocusAtPoint(x, y, mCurrentSceneMode.getCurrentId());
        lockExposure(mCurrentSceneMode.getCurrentId());
    }

    private void updateLockAFAEVisibility() {
        mActivity.runOnUiThread(new Runnable() {
            public void run() {
                mLockAFAEText.setVisibility(mLockAFAE ? View.VISIBLE : View.INVISIBLE);
            }
        });
    }

    public int getMainCameraId() {
        return mCurrentSceneMode.getCurrentId();
    }

    public boolean isTakingPicture() {
        for (int i = 0; i < mTakingPicture.length; i++) {
            if (mTakingPicture[i]) return true;
        }
        return false;
    }

    private boolean isTouchToFocusAllowed() {
        if (isTakingPicture() || isTouchAfEnabledSceneMode()) return false;
        return true;
    }

    private boolean isTouchAfEnabledSceneMode() {
        String scene = mSettingsManager.getValue(SettingsManager.KEY_SCENE_MODE);
        if (scene == null) return false;
        int mode = Integer.parseInt(scene);
        if (mode != CaptureRequest.CONTROL_SCENE_MODE_DISABLED
                && mode < SettingsManager.SCENE_MODE_CUSTOM_START
                && mode != SettingsManager.SCENE_MODE_HDR_INT)
            return true;
        return false;
    }

    private ExtendedFace[] getBsgcInfo(CaptureResult captureResult, int size) {
        if (captureResult == null || size == 0) {
            if(BSGC_DEBUG)
                Log.d(BSGC_TAG,"extendface size ="+size);
            return null;
        }
        ExtendedFace[] extendedFaces = new ExtendedFace[size];
        boolean bsgEnable = isBsgcDetecionOn();
        boolean contourEnable = isFacialContourOn();
        boolean facePointEnable = isFacePointOn();
        try {
            if (bsgEnable) {
                byte[] blinkDetectedArray = captureResult.get(blinkDetected);
                if (BSGC_DEBUG)
                    Log.d(BSGC_TAG,"blinkDetectedArray="+Arrays.toString(blinkDetectedArray));
                byte[] blinkDegreesArray = captureResult.get(blinkDegree);
                if (BSGC_DEBUG)
                    Log.d(BSGC_TAG,"blinkDegreesArray="+Arrays.toString(blinkDegreesArray));
                int[] gazeDirectionArray = captureResult.get(gazeDirection);
                if (BSGC_DEBUG)
                    Log.d(BSGC_TAG,"gazeDirectionArray="+Arrays.toString(gazeDirectionArray));
                byte[] gazeAngleArray = captureResult.get(gazeAngle);
                if (BSGC_DEBUG)
                    Log.d(BSGC_TAG,"gazeAngleArray="+Arrays.toString(gazeAngleArray));
                byte[] smileDegreeArray = captureResult.get(smileDegree);
                if (BSGC_DEBUG)
                    Log.d(BSGC_TAG,"smileDegreeArray="+Arrays.toString(smileDegreeArray));
                byte[] smileConfidenceArray = captureResult.get(smileConfidence);
                if (BSGC_DEBUG)
                    Log.d(BSGC_TAG,"smileConfidenceArray="+Arrays.toString(smileConfidenceArray));
                for (int i = 0; i < size; i++) {
                    ExtendedFace tmp = new ExtendedFace(i);
                    try {
                        tmp.setBlinkDetected(blinkDetectedArray[i]);
                        tmp.setBlinkDegree(blinkDegreesArray[2 * i], blinkDegreesArray[2 * i + 1]);
                        tmp.setGazeDirection(gazeDirectionArray[3 * i], gazeDirectionArray[3 * i + 1], gazeDirectionArray[3 * i + 2]);
                        tmp.setGazeAngle(gazeAngleArray[i]);
                        tmp.setSmileDegree(smileDegreeArray[i]);
                        tmp.setSmileConfidence(smileConfidenceArray[i]);
                    } catch (ArrayIndexOutOfBoundsException e) {}
                    extendedFaces[i] = tmp;
                }
            }
            if (contourEnable || facePointEnable) {
                int[] contourPoints = captureResult.get(CaptureModule.contourPoints);
                if (BSGC_DEBUG)
                    Log.d(BSGC_TAG,"contourPoints="+Arrays.toString(contourPoints));
                int[] landmarkPoints = null;//captureResult.get(CaptureResult.STATISTICS_FACE_LANDMARKS);
                if (BSGC_DEBUG)
                    Log.d(BSGC_TAG,"landmarkPoints="+Arrays.toString(landmarkPoints));
                ExtendedFace tmp;
                if (extendedFaces[0] == null) {
                    tmp = new ExtendedFace(0);
                    extendedFaces[0] = tmp;
                } else {
                    tmp = extendedFaces[0];
                }
                tmp.setContour(contourPoints);
                tmp.setLandMarks(landmarkPoints);
            }
        } catch (IllegalArgumentException|NullPointerException e){
            e.printStackTrace();
        }
        return extendedFaces;
    }

    private void updateFaceView(final Face[] faces, final ExtendedFace[] extendedFaces) {
        mPreviewFaces = faces;
        mExFaces = extendedFaces;
        if (faces != null) {
            if (faces.length != 0) {
                mStickyFaces = faces;
                mStickyExFaces = extendedFaces;
            }
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mUI.onFaceDetection(faces, extendedFaces);
                }
            });
        }
    }

    public boolean isSelfieFlash() {
        String value = mSettingsManager.getValue(SettingsManager.KEY_SELFIE_FLASH);
        return value != null && value.equals("on") && getMainCameraId() == FRONT_ID;
    }

    private void checkSelfieFlashAndTakePicture() {
        if (isSelfieFlash()) {
            mUI.startSelfieFlash();
            if (selfieThread == null) {
                selfieThread = new SelfieThread();
                selfieThread.start();
            }
        } else {
            takePicture();
        }
    }

    @Override
    public void onCountDownFinished() {
        checkSelfieFlashAndTakePicture();
        mUI.showUIAfterCountDown();
    }

    @Override
    public void onScreenSizeChanged(int width, int height) {

    }

    @Override
    public void onPreviewRectChanged(Rect previewRect) {

    }

    @Override
    public void updateCameraOrientation() {
        if (mDisplayRotation != CameraUtil.getDisplayRotation(mActivity)) {
            setDisplayOrientation();
        }
    }

    @Override
    public void waitingLocationPermissionResult(boolean result) {
        mLocationManager.waitingLocationPermissionResult(result);
    }

    @Override
    public void enableRecordingLocation(boolean enable) {
        String value = (enable ? RecordLocationPreference.VALUE_ON
                               : RecordLocationPreference.VALUE_OFF);
        mSettingsManager.setValue(SettingsManager.KEY_RECORD_LOCATION, value);
        mLocationManager.recordLocation(enable);
    }

    @Override
    public void setPreferenceForTest(String key, String value) {
        if (SettingsManager.KEY_ZOOM.equals(key)){
            onZoomChanged(Float.parseFloat(value));
            return;
        }
        mSettingsManager.setValue(key, value);
    }

    @Override
    public void onPreviewUIReady() {
        updatePreviewSurfaceReadyState(true);

        if (mPaused || mIsRecordingVideo) {
            return;
        }
    }

    @Override
    public void onPreviewUIDestroyed() {
        updatePreviewSurfaceReadyState(false);
    }

    @Override
    public void onPreviewTextureCopied() {

    }

    @Override
    public void onCaptureTextureCopied() {

    }

    @Override
    public void onUserInteraction() {

    }

    @Override
    public boolean updateStorageHintOnResume() {
        return false;
    }

    @Override
    public void onOrientationChanged(int orientation) {
        // We keep the last known orientation. So if the user first orient
        // the camera then point the camera to floor or sky, we still have
        // the correct orientation.
        if (orientation == OrientationEventListener.ORIENTATION_UNKNOWN) return;
        int oldOrientation = mOrientation;
        mOrientation = CameraUtil.roundOrientation(orientation, mOrientation);
        if (oldOrientation != mOrientation) {
            mUI.onOrientationChanged();
            mUI.setOrientation(mOrientation, true);
            if (mGraphViewR != null) {
                mGraphViewR.setRotation(-mOrientation);
            }
            if (mGraphViewGB != null) {
                mGraphViewGB.setRotation(-mOrientation);
            }
            if (mGraphViewB != null) {
                mGraphViewB.setRotation(-mOrientation);
            }
        }

        // need to re-initialize mGraphView to show histogram on rotate
        mGraphViewR  = (Camera2GraphView) mRootView.findViewById(R.id.graph_view_r);
        mGraphViewGB = (Camera2GraphView) mRootView.findViewById(R.id.graph_view_gb);
        mGraphViewB  = (Camera2GraphView) mRootView.findViewById(R.id.graph_view_b);
        bgstats_view = (Camera2BGBitMap) mRootView.findViewById(R.id.bg_stats_graph);
        bestats_view = (Camera2BEBitMap) mRootView.findViewById(R.id.be_stats_graph);
        mGraphViewR.setDataSection(0,256);
        mGraphViewGB.setDataSection(256,512);
        mGraphViewB.setDataSection(512,768);
        if(mGraphViewR != null){
            mGraphViewR.setAlpha(0.75f);
            mGraphViewR.setCaptureModuleObject(this);
            mGraphViewR.PreviewChanged();
        }
        if(mGraphViewGB != null){
            mGraphViewGB.setAlpha(0.75f);
            mGraphViewGB.setCaptureModuleObject(this);
            mGraphViewGB.PreviewChanged();
        }
        if(mGraphViewB != null){
            mGraphViewB.setAlpha(0.75f);
            mGraphViewB.setCaptureModuleObject(this);
            mGraphViewB.PreviewChanged();
        }
        if(bgstats_view != null){
            bgstats_view.setAlpha(1.0f);
            bgstats_view.setCaptureModuleObject(this);
            bgstats_view.PreviewChanged();
        }
        if(bestats_view != null){
            bestats_view.setAlpha(1.0f);
            bestats_view.setCaptureModuleObject(this);
            bestats_view.PreviewChanged();
        }
    }

    public int getDisplayOrientation() {
        return mOrientation;
    }

    public int getSensorOrientation() {
        int degree = 0;
        if(getMainCameraCharacteristics() != null) {
            degree = getMainCameraCharacteristics().
                    get(CameraCharacteristics.SENSOR_ORIENTATION);
        }
        return degree;
    }

    @Override
    public void onShowSwitcherPopup() {

    }

    @Override
    public void onMediaSaveServiceConnected(MediaSaveService s) {
        if (mFirstTimeInitialized) {
            s.setListener(this);
            if (isClearSightOn()) {
                ClearSightImageProcessor.getInstance().setMediaSaveService(s);
            }
        }
    }

    @Override
    public boolean arePreviewControlsVisible() {
        return false;
    }

    @Override
    public void resizeForPreviewAspectRatio() {

    }

    @Override
    public void onSwitchSavePath() {
        mSettingsManager.setValue(SettingsManager.KEY_CAMERA_SAVEPATH, "1");
        RotateTextToast.makeText(mActivity, R.string.on_switch_save_path_to_sdcard,
                Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onShutterButtonFocus(boolean pressed) {
        if (!pressed && mLongshotActive) {
            Log.d(TAG, "Longshot button up");
            mLongshotActive = false;
            mPostProcessor.stopLongShot();
            mUI.enableVideo(!mLongshotActive);
        }
    }

    private void updatePictureSize() {
        String pictureSize = mSettingsManager.getValue(SettingsManager.KEY_PICTURE_SIZE);
        mPictureSize = parsePictureSize(pictureSize);
        Size[] prevSizes = mSettingsManager.getSupportedOutputSize(getMainCameraId(),
                SurfaceHolder.class);
        List<Size> prevSizeList = Arrays.asList(prevSizes);
        prevSizeList.sort((o1,o2) -> o2.getWidth()*o2.getHeight() - o1.getWidth()*o1.getHeight());
        mSupportedMaxPreviewSize = prevSizeList.get(0);
        Size[] picSize = mSettingsManager.getSupportedOutputSize(getMainCameraId(), ImageFormat.JPEG);
        Size[] highResSizes = mSettingsManager.getHighResolutionOutputSize(getMainCameraId());
        Size[] allPicSizes = new Size[picSize.length + highResSizes.length];
        System.arraycopy(picSize, 0, allPicSizes, 0, picSize.length);
        System.arraycopy(highResSizes, 0, allPicSizes, picSize.length, highResSizes.length);
        List<Size> allPicSizesList = Arrays.asList(allPicSizes);
        allPicSizesList.sort((o1,o2) -> o2.getWidth()*o2.getHeight() - o1.getWidth()*o1.getHeight());
        mSupportedMaxPictureSize = allPicSizesList.get(0);
        Size[] yuvSizes = mSettingsManager.getSupportedOutputSize(getMainCameraId(), ImageFormat.PRIVATE);
        List<Size> yuvSizeList = Arrays.asList(yuvSizes);
        if(DEBUG)
            Log.i(TAG,"mSupportedMaxPreviewSize:" + mSupportedMaxPreviewSize + ",mSupportedMaxPictureSize:" + mSupportedMaxPictureSize);
        yuvSizeList.sort((o1,o2) -> o2.getWidth()*o2.getHeight() - o1.getWidth()*o1.getHeight());
        for (int i = 0; i< mYUVCount; i++) {
            mYUVsize[i] = yuvSizeList.get(0);
        }

        Size[] rawSize = mSettingsManager.getSupportedOutputSize(getMainCameraId(),
                    ImageFormat.RAW10);
        if (rawSize == null || rawSize.length == 0) {
            mSupportedRawPictureSize = null;
            mSaveRaw = false;
        } else {
            mSupportedRawPictureSize = rawSize[0];
        }
        mPreviewSize = getOptimalPreviewSize(mPictureSize, prevSizes);
        Size[] thumbSizes = mSettingsManager.getSupportedThumbnailSizes(getMainCameraId());
        mPictureThumbSize = getOptimalPreviewSize(mPictureSize, thumbSizes); // get largest thumb size
    }

    public Size getThumbSize() {
        return mPictureThumbSize;
    }

    public boolean isRecordingVideo() {
        return mIsRecordingVideo;
    }

    public void setMute(boolean enable, boolean isValue) {
        AudioManager am = (AudioManager) mActivity.getSystemService(Context.AUDIO_SERVICE);
        am.setMicrophoneMute(enable);
        if (isValue) {
            mIsMute = enable;
        }
    }

    public boolean isAudioMute() {
        return mIsMute;
    }

    private void updateVideoSize() {
        String videoSize = mSettingsManager.getValue(SettingsManager.KEY_VIDEO_QUALITY);
        if (videoSize == null) return;
        mVideoSize = parsePictureSize(videoSize);
        Point videoSize2 = PersistUtil.getCameraVideoSize();
        if (videoSize2 != null) {
            mVideoSize = new Size(videoSize2.x, videoSize2.y);
        }
        if (DEBUG) {
            Log.v(TAG, "updateVideoSize mVideoSize = " + mVideoSize + ", videoSize :" + videoSize);
        }
        Size[] prevSizes = mSettingsManager.getSupportedOutputSize(getMainCameraId(),
                MediaRecorder.class);
        mVideoPreviewSize = getOptimalVideoPreviewSize(mVideoSize, prevSizes);
        Point previewSize = PersistUtil.getCameraPreviewSize();
        if (previewSize != null) {
            mVideoPreviewSize = new Size(previewSize.x, previewSize.y);
        }
        Log.d(TAG, "updateVideoPreviewSize final video Preview size = " + mVideoPreviewSize.getWidth()
                + ", " + mVideoPreviewSize.getHeight());
    }

    private void updateVideoSnapshotSize() {
        mVideoSnapshotSize = mVideoSize;
        if (!is4kSize(mVideoSize) && (mHighSpeedCaptureRate == 0)) {
            mVideoSnapshotSize = getMaxPictureSizeLiveshot();
        }

        if(mSettingsManager.isLiveshotSizeSameAsVideoSize()){
            mVideoSnapshotSize = mVideoSize;
        }
        String videoSnapshot = PersistUtil.getVideoSnapshotSize();
        String[] sourceStrArray = videoSnapshot.split("x");
        if (sourceStrArray != null && sourceStrArray.length >= 2) {
            int width = Integer.parseInt(sourceStrArray[0]);
            int height = Integer.parseInt(sourceStrArray[1]);
            mVideoSnapshotSize = new Size(width, height);
        }
        Log.d(TAG, "updateVideoSnapshotSize final video snapShot size = " +
                mVideoSnapshotSize.getWidth() + ", " + mVideoSnapshotSize.getHeight());
        Size[] thumbSizes = mSettingsManager.getSupportedThumbnailSizes(getMainCameraId());
        mVideoSnapshotThumbSize = getOptimalPreviewSize(mVideoSnapshotSize, thumbSizes); // get largest thumb size
    }

    private boolean is4kSize(Size size) {
        return (size.getHeight() >= 2160 || size.getWidth() >= 3840);
    }

    private Size getMaxPictureSizeLiveshot() {
        Size[] sizes = mSettingsManager.getSupportedOutputSize(getMainCameraId(), ImageFormat.JPEG);
        float ratio = (float) mVideoSize.getWidth() / mVideoSize.getHeight();
        Size optimalSize = null;
        for (Size size : sizes) {
            float pictureRatio = (float) size.getWidth() / size.getHeight();
            if (Math.abs(pictureRatio - ratio) > 0.01) continue;
            if (optimalSize == null || size.getWidth() > optimalSize.getWidth()) {
                optimalSize = size;
            }
        }

        // Cannot find one that matches the aspect ratio. This should not happen.
        // Ignore the requirement.
        if (optimalSize == null) {
            Log.w(TAG, "getMaxPictureSizeLiveshot: no picture size match the aspect ratio");
            for (Size size : sizes) {
                if (optimalSize == null || size.getWidth() > optimalSize.getWidth()) {
                    optimalSize = size;
                }
            }
        }
        return optimalSize;
    }

    private boolean isVideoSize1080P(Size size) {
        return (size.getHeight() == 1080 && size.getWidth() == 1920);
    }

    private void updateMaxVideoDuration() {
        String minutesStr = mSettingsManager.getValue(SettingsManager.KEY_VIDEO_DURATION);
        int minutes = Integer.parseInt(minutesStr);
        if (minutes == -1) {
            // User wants lowest, set 30s */
            mMaxVideoDurationInMs = 30000;
        } else {
            // 1 minute = 60000ms
            mMaxVideoDurationInMs = 60000 * minutes;
        }
    }

    public void updateDeepZoomIndex(float zoom) {
        mZoomValue = zoom;
        applyZoomAndUpdate();
    }

    private void applyZoomAndUpdate() {
        applyZoomAndUpdate(mCurrentSceneMode.getCurrentId());
        mUI.updateFaceViewCameraBound(mCropRegion[getMainCameraId()]);
        mUI.updateT2TCameraBound(mCropRegion[getMainCameraId()]);
    }

    private void updateZoom() {
        String zoomStr = mSettingsManager.getValue(SettingsManager.KEY_ZOOM);
        int zoom = Integer.parseInt(zoomStr);
        if ( zoom !=0 ) {
            mZoomValue = (float)zoom;
        }else{
            mZoomValue = 1.0f;
        }
        mUI.updateZoomSeekBar(mZoomValue);
        if (isDeepZoom()) {
            mZoomValue = mUI.getDeepZoomValue();
        }
    }

    private List<CaptureRequest> createSSMBatchRequest(CaptureRequest.Builder requestBuilder) {
        List<CaptureRequest> ssmRequests = new ArrayList<CaptureRequest>();
        requestBuilder.removeTarget(mVideoPreviewSurface);
        requestBuilder.removeTarget(mMediaRecorderSurface);
        requestBuilder.addTarget(mVideoPreviewSurface);
        ssmRequests.add(requestBuilder.build());
        requestBuilder.removeTarget(mVideoPreviewSurface);
        requestBuilder.addTarget(mMediaRecorderSurface);
        int mSSMBatchSize = CameraUtil.getHighSpeedVideoConfigsLists(getMainCameraId());
        if (DEBUG) {
            Log.d(TAG, "mSSMBatchSize is " + mSSMBatchSize);
        }
        for (int i = 1; i < mSSMBatchSize; i++) {
            ssmRequests.add(requestBuilder.build());
        }
        return ssmRequests;
    }

    private final CameraCaptureSession.StateCallback mCCSSateCallback = new CameraCaptureSession
            .StateCallback() {
        @Override
        public void onConfigured(CameraCaptureSession cameraCaptureSession) {
            Log.d(TAG, "StartRecordingVideo session onConfigured");
            setCameraModeSwitcherAllowed(true);
            int cameraId = getMainCameraId();
            mCurrentSession = cameraCaptureSession;
            mCaptureSession[cameraId] = cameraCaptureSession;
            updateFaceDetection();
            try {
                setUpVideoCaptureRequestBuilder(mVideoRecordRequestBuilder, cameraId);
                mCurrentSession.setRepeatingRequest(mVideoRecordRequestBuilder.build(),
                        mCaptureCallback, mCameraHandler);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            } catch (IllegalStateException e) {
                e.printStackTrace();
            }
            if (!mFrameProcessor.isFrameListnerEnabled() && !startMediaRecorder()) {
                startRecordingFailed();
                return;
            }
        }

        @Override
        public void onConfigureFailed(CameraCaptureSession cameraCaptureSession) {
            setCameraModeSwitcherAllowed(true);
            Toast.makeText(mActivity, "Video Failed", Toast.LENGTH_SHORT).show();
        }
    };

    private final CameraCaptureSession.StateCallback mSessionListener = new CameraCaptureSession
            .StateCallback() {
        @Override
        public void onConfigured(CameraCaptureSession cameraCaptureSession) {
            Log.d(TAG, "mSessionListener session onConfigured");
            enableVideoButton(true);
            setCameraModeSwitcherAllowed(true);
            int cameraId = getMainCameraId();
            mCurrentSession = cameraCaptureSession;
            mCaptureSession[cameraId] = cameraCaptureSession;
            updateFaceDetection();
            // Create slow motion request list
            List<CaptureRequest> slowMoRequests = null;
            try {
                setUpVideoCaptureRequestBuilder(mVideoRecordRequestBuilder, cameraId);
                if (isHighSpeedRateCapture()) {
                    slowMoRequests = mSuperSlomoCapture ?
                            createSSMBatchRequest(mVideoRecordRequestBuilder) :
                            ((CameraConstrainedHighSpeedCaptureSession) mCurrentSession)
                                    .createHighSpeedRequestList(mVideoRecordRequestBuilder.build());
                    mCurrentSession.setRepeatingBurst(slowMoRequests, mCaptureCallback, mCameraHandler);
                } else {
                    int previewFPS = mSettingsManager.getVideoPreviewFPS(mVideoSize,
                            mSettingsManager.getVideoFPS());
                    if (previewFPS == 30 && mHighSpeedCaptureRate == 60) {
                        List<CaptureRequest> burstList = new ArrayList<>();
                        burstList.add(mVideoRecordRequestBuilder.build());
                        mVideoRecordRequestBuilder.removeTarget(mVideoPreviewSurface);
                        burstList.add(mVideoRecordRequestBuilder.build());
                        mCurrentSession.setRepeatingBurst(burstList, mCaptureCallback, mCameraHandler);
                        mVideoRecordRequestBuilder.addTarget(mVideoPreviewSurface);
                    } else {
                        mCurrentSession.setRepeatingRequest(mVideoRecordRequestBuilder.build(),
                                mCaptureCallback, mCameraHandler);
                    }
                }
            } catch (CameraAccessException e) {
                e.printStackTrace();
            } catch (IllegalStateException e) {
                e.printStackTrace();
            } catch (NullPointerException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onConfigureFailed(CameraCaptureSession cameraCaptureSession) {
            enableVideoButton(true);
            setCameraModeSwitcherAllowed(true);
            Toast.makeText(mActivity, "Video Failed", Toast.LENGTH_SHORT).show();
        }
    };

    private void createCameraSessionWithSessionConfiguration(int cameraId,
                 List<OutputConfiguration> outConfigurations, CameraCaptureSession.StateCallback listener,
                 Handler handler, CaptureRequest.Builder initialRequest) {

        int opMode = SESSION_REGULAR;
        String valueFS2 = mSettingsManager.getValue(SettingsManager.KEY_SENSOR_MODE_FS2_VALUE);
        if (valueFS2 != null) {
            int intValue = Integer.parseInt(valueFS2);
            if (intValue == 1) {
                opMode |= STREAM_CONFIG_MODE_FS2;
            }
        }

        Log.v(TAG, " createCameraSessionWithSessionConfiguration opMode: " + opMode);
        Method method_setSessionParameters = null;
        Method method_createCaptureSession = null;
        Object sessionConfig = null;
        try {
            Class clazz = Class.forName("android.hardware.camera2.params.SessionConfiguration");
            sessionConfig = clazz.getConstructors()[0].newInstance(
                    opMode, outConfigurations,
                    new HandlerExecutor(handler), listener);
            if (method_setSessionParameters == null) {
                method_setSessionParameters = clazz.getDeclaredMethod(
                        "setSessionParameters", CaptureRequest.class);
            }
            method_setSessionParameters.invoke(sessionConfig, initialRequest.build());
            method_createCaptureSession = CameraDevice.class.getDeclaredMethod(
                    "createCaptureSession", clazz);
            method_createCaptureSession.invoke(mCameraDevice[cameraId], sessionConfig);
        } catch (Exception exception) {
            Log.w(TAG, "createCameraSessionWithSessionConfiguration method is not exist");
            exception.printStackTrace();
        }
    }

    private void configureCameraSessionWithParameters(int cameraId,
            List<Surface> outputSurfaces, CameraCaptureSession.StateCallback listener,
            Handler handler, CaptureRequest.Builder initialRequest) throws CameraAccessException {
        List<OutputConfiguration> outConfigurations = new ArrayList<>(outputSurfaces.size());
        if (mSettingsManager.isLiveshotSupported(mVideoSize,mSettingsManager.getVideoFPS())){
            if (mSettingsManager.isHeifWriterEncoding() && mLiveShotInitHeifWriter != null) {
                mLiveShotOutput = new OutputConfiguration(
                        mLiveShotInitHeifWriter.getInputSurface());
                mLiveShotOutput.enableSurfaceSharing();
                outConfigurations.add(mLiveShotOutput);
            } else {
                outputSurfaces.add(mVideoSnapshotImageReader.getSurface());
            }
        }

        for (Surface surface : outputSurfaces) {
            outConfigurations.add(new OutputConfiguration(surface));
        }

        String zzHDR = mSettingsManager.getValue(SettingsManager.KEY_VIDEO_HDR_VALUE);
        boolean zzHdrStatue = zzHDR.equals("1");
        // if enable ZZHDR mode, don`t call the setOpModeForVideoStream method.
        /* if (!zzHdrStatue) {
            setOpModeForVideoStream(cameraId);
        }*/
        String value = mSettingsManager.getValue(SettingsManager.KEY_FOVC_VALUE);
        if (value != null && Boolean.parseBoolean(value)) {
            mStreamConfigOptMode = mStreamConfigOptMode | STREAM_CONFIG_MODE_FOVC;
        }
        if (zzHdrStatue) {
            mStreamConfigOptMode = STREAM_CONFIG_MODE_ZZHDR;
        }
        if (DEBUG) {
            Log.v(TAG, "configureCameraSessionWithParameters mStreamConfigOptMode :"
                    + mStreamConfigOptMode);
        }

        Method method_setSessionParameters = null;
        Method method_createCaptureSession = null;
        Object sessionConfig = null;
        try {
            Class clazz = Class.forName("android.hardware.camera2.params.SessionConfiguration");
            sessionConfig = clazz.getConstructors()[0].newInstance(
                    SESSION_REGULAR | mStreamConfigOptMode, outConfigurations,
                    new HandlerExecutor(handler), listener);
            if (method_setSessionParameters == null) {
                method_setSessionParameters = clazz.getDeclaredMethod(
                        "setSessionParameters", CaptureRequest.class);
            }
            method_setSessionParameters.invoke(sessionConfig, initialRequest.build());
            method_createCaptureSession = CameraDevice.class.getDeclaredMethod(
                    "createCaptureSession", clazz);
            method_createCaptureSession.invoke(mCameraDevice[cameraId], sessionConfig);
        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }

    private void buildConstrainedCameraSession(CameraDevice camera, int optionMode,
            List<Surface> outputSurfaces, CameraCaptureSession.StateCallback sessionListener,
        Handler handler, CaptureRequest.Builder initialRequest) throws CameraAccessException {

        List<OutputConfiguration> outConfigurations = new ArrayList<>(outputSurfaces.size());
        for (Surface surface : outputSurfaces) {
            outConfigurations.add(new OutputConfiguration(surface));
        }
        Method method_setSessionParameters = null;
        Method method_createCaptureSession = null;
        Object sessionConfig = null;
        try {
            Class clazz = Class.forName("android.hardware.camera2.params.SessionConfiguration");
            sessionConfig = clazz.getConstructors()[0].newInstance(optionMode,
                    outConfigurations, new HandlerExecutor(handler), sessionListener);
            if (method_setSessionParameters == null) {
                method_setSessionParameters = clazz.getDeclaredMethod(
                        "setSessionParameters", CaptureRequest.class);
            }
            method_setSessionParameters.invoke(sessionConfig, initialRequest.build());

            method_createCaptureSession = CameraDevice.class.getDeclaredMethod(
                    "createCaptureSession", clazz);
            method_createCaptureSession.invoke(camera, sessionConfig);
        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }

    private class HandlerExecutor implements Executor {
        private final Handler ihandler;

        public HandlerExecutor(Handler handler) {
            ihandler = handler;
        }

        @Override
        public void execute(Runnable runCmd) {
            ihandler.post(runCmd);
        }
    }

    private boolean startRecordingVideo(final int cameraId) {
        if (null == mCameraDevice[cameraId]) {
            return false;
        }
        mStartRecordingTime = System.currentTimeMillis();
        Log.d(TAG, "StartRecordingVideo " + cameraId);
        mStartRecPending = true;
        mIsRecordingVideo = true;
        mMediaRecorderPausing = false;
        mIsPreviewingVideo = false;
        mSSMCaptureCompleteFlag = false;

        checkAndPlayRecordSound(cameraId, true);
        mActivity.updateStorageSpaceAndHint();
        if (mActivity.getStorageSpaceBytes() <= Storage.LOW_STORAGE_THRESHOLD_BYTES) {
            Log.w(TAG, "Storage issue, ignore the start request");
            mStartRecPending = false;
            mIsRecordingVideo = false;
            mIsPreviewingVideo = true;
            Toast.makeText(mActivity,R.string.storage_not_enough,Toast.LENGTH_SHORT).show();
            return false;
        }

        try {
            if(!mLockAFAE) {
                mUI.clearFocus();
            }
            mUI.hideUIwhileRecording();
            mCameraHandler.removeMessages(CANCEL_TOUCH_FOCUS, mCameraId[cameraId]);
            if (!mFrameProcessor.isFrameListnerEnabled() && !startMediaRecorder() ||
                    !mIsRecordingVideo) {
                releaseMediaRecorder();
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mUI.showUIafterRecording();
                        mFrameProcessor.setVideoOutputSurface(null);
                        restartSession(false);
                    }
                });
                return false;
            }
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    if(!mLockAFAE) {
                        mUI.clearFocus();
                    }
                    mUI.resetPauseButton();
                    mRecordingTotalTime = 0L;
                    mRecordingStartTime = SystemClock.uptimeMillis();
                    if (mSettingsManager.isLiveshotSupported(mVideoSize,
                            mSettingsManager.getVideoFPS())){
                        mUI.enableShutter(true);
                    } else {
                        mUI.enableShutter(false);
                    }
                    mUI.showRecordingUI(true, false);
                    updateRecordingTime();
                    keepScreenOn();
                }
            });
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            quitRecordingWithError("IllegalArgumentException");
        } catch (IllegalStateException e) {
            e.printStackTrace();
            quitRecordingWithError("IllegalStateException");
        } catch (NullPointerException e) {
            e.printStackTrace();
            quitRecordingWithError("NullPointException");
        }
        mStartRecPending = false;
        return true;
    }

    private void startRecordingFailed() {
        releaseMediaRecorder();
        mHandler.post(new Runnable() {
             @Override
             public void run() {
                 mUI.showUIafterRecording();
                 mFrameProcessor.setVideoOutputSurface(null);
                 restartSession(true);
             }
        });
    }


    private void quitVideoToPhotoWithError(String msg) {
        setCameraModeSwitcherAllowed(true);
        mActivity.runOnUiThread(new Runnable() {
            public void run() {
                Toast.makeText(mActivity,"Could not start media recorder.\n " +
                        msg, Toast.LENGTH_LONG).show();
            }
        });
        releaseMediaRecorder();
        releaseAudioFocus();
        mActivity.runOnUiThread(new Runnable() {
            public void run() {
                mUI.switchToPhotoModeDueToError(true);
            }
        });
    }

    private void quitRecordingWithError(String msg) {
        Toast.makeText(mActivity,"Could not start media recorder.\n " +
                msg, Toast.LENGTH_LONG).show();
        releaseMediaRecorder();
        releaseAudioFocus();
        mStartRecPending = false;
        mIsRecordingVideo = false;
        mUI.showUIafterRecording();
        mFrameProcessor.setVideoOutputSurface(null);
        restartSession(true);
    }
    private boolean startMediaRecorder() {
        if (mUnsupportedResolution == true ) {
            Log.v(TAG, "Unsupported Resolution according to target");
            mStartRecPending = false;
            mIsRecordingVideo = false;
            return false;
        }
        if (mMediaRecorder == null) {
            Log.e(TAG, "Fail to initialize media recorder");
            mStartRecPending = false;
            mIsRecordingVideo = false;
            return false;
        }

        requestAudioFocus();
        try {
            mMediaRecorder.start(); // Recording is now started
            mMediaRecorderStarted = true;
            Log.d(TAG, "StartRecordingVideo done. Time=" +
                    (System.currentTimeMillis() - mStartRecordingTime) + "ms");
        } catch (RuntimeException e) {
            Toast.makeText(mActivity,"Could not start media recorder.\n " +
                    "Can't start video recording.", Toast.LENGTH_LONG).show();
            releaseMediaRecorder();
            releaseAudioFocus();
            mStartRecPending = false;
            mIsRecordingVideo = false;
            return false;
        }
        if (isSSMEnabled()) {
            try {
                mVideoRecordRequestBuilder.set(ssmInterpFactor, mInterpFactor);
                mVideoRecordRequestBuilder.set(ssmCaptureStart, 1);
                mCurrentSession.captureBurst(createSSMBatchRequest(mVideoRecordRequestBuilder),
                        mCaptureCallback, mCameraHandler);
                mVideoRecordRequestBuilder.set(ssmCaptureStart, 0);
                mCurrentSession.setRepeatingBurst(createSSMBatchRequest(mVideoRecordRequestBuilder),
                        mCaptureCallback, mCameraHandler);
            } catch (CameraAccessException | IllegalArgumentException e) {
                e.printStackTrace();
                quitRecordingWithError("SSM starts failed");
                return false;
            }
        }
        return true;
    }

    public void startMediaRecording() {
        if (!startMediaRecorder()) {
            startRecordingFailed();
        }
    }

    private void updateTimeLapseSetting() {
        String value = mSettingsManager.getValue(SettingsManager
                .KEY_VIDEO_TIME_LAPSE_FRAME_INTERVAL);
        if (value == null) return;
        int time = Integer.parseInt(value);
        mTimeBetweenTimeLapseFrameCaptureMs = time;
        mCaptureTimeLapse = mTimeBetweenTimeLapseFrameCaptureMs != 0;
        mUI.showTimeLapseUI(mCaptureTimeLapse);
    }

    private void updateHFRSetting() {
        String value = mSettingsManager.getValue(SettingsManager.KEY_VIDEO_HIGH_FRAME_RATE);
        if (value == null) return;
        if (value.equals("off")) {
            mHighSpeedCapture = false;
            mHighSpeedCaptureRate = 0;
            mSuperSlomoCapture = false;
        } else {
            mHighSpeedCapture = true;
            String mode = value.substring(0, 3);
            mSuperSlomoCapture = mode.equals("2x_") || mode.equals("4x_");
            mHighSpeedRecordingMode = mode.equals("hsr") || mSuperSlomoCapture;
            if (mSuperSlomoCapture) {
                mInterpFactor = Integer.parseInt(value.substring(0, 1));
            }
            mHighSpeedCaptureRate = Integer.parseInt(value.substring(3));
        }
    }

    public boolean isHSRMode() {
        return mHighSpeedRecordingMode && !mSuperSlomoCapture;
    }

    private void updateProgressBar(boolean show) {
        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mUI.toggleProgressBar(show);
            }
        });
    }

    private void setUpVideoCaptureRequestBuilder(CaptureRequest.Builder builder,int cameraId) {
        if(!mLockAFAE) {
            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest
                    .CONTROL_AF_MODE_CONTINUOUS_VIDEO);
            applyVideoCommentSettings(builder, cameraId);
        } else {
            //relock af ae when lock
            autoFocusTrigger(cameraId);
            applyAERegions(builder, cameraId);
            applySettingsForLockExposure(builder, cameraId);
            applyAntiBandingLevel(builder);
            applyVideoStabilization(builder);
            applyNoiseReduction(builder);
            applyColorEffect(builder);
            applyVideoFlash(builder, cameraId);
            applyFaceDetection(builder);
            applyZoom(builder, cameraId);
            applyVideoEncoderProfile(builder);
            applyVideoEIS(builder);
            applyVideoHDR(builder);
            applyEarlyPCR(builder);
            applyTouchTrackFocus(builder);
            applyToneMapping(builder);
        }
    }

    private void lockAfAeForRequestBuilder(CaptureRequest.Builder builder, int id){
        builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest
                .CONTROL_AF_TRIGGER_CANCEL);
        builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest
                .CONTROL_AF_TRIGGER_START);
        applyAFRegions(builder, id);
        applyAERegions(builder, id);
        applySettingsForLockExposure(builder, id);
    }

    private void setUpVideoPreviewRequestBuilder(Surface surface, int cameraId) {
        try {
            mVideoPreviewRequestBuilder = getRequestBuilder(
                    CameraDevice.TEMPLATE_PREVIEW,cameraId);
        } catch (CameraAccessException e) {
            Log.w(TAG, "setUpVideoPreviewRequestBuilder, Camera access failed");
            return;
        }
        mVideoPreviewRequestBuilder.setTag(cameraId);
        mVideoPreviewRequestBuilder.addTarget(surface);
        if (!mLockAFAE) {
            mVideoPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, mControlAFMode);
        }
        if (mHighSpeedCapture) {
            mVideoPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                    mHighSpeedFPSRange);
        } else {
            mHighSpeedFPSRange = new Range(30, 30);
            mVideoPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                    mHighSpeedFPSRange);
        }
        if (!isHighSpeedRateCapture()) {
            applyVideoCommentSettings(mVideoPreviewRequestBuilder, cameraId);
        }
    }

    private void applyVideoCommentSettings(CaptureRequest.Builder builder, int cameraId) {
        if(!mLockAFAE) {
            builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
        }else{
            lockAfAeForRequestBuilder(builder, cameraId);
        }
        applyAntiBandingLevel(builder);
        applyVideoStabilization(builder);
        applyNoiseReduction(builder);
        applyColorEffect(builder);
        applyVideoFlash(builder, cameraId);
        applyFaceDetection(builder);
        applyZoom(builder, cameraId);
        applyVideoEncoderProfile(builder);
        applyVideoEIS(builder);
        applyVideoHDR(builder);
        applyTouchTrackFocus(builder);
        applyToneMapping(builder);
    }

    private void applyVideoHDR(CaptureRequest.Builder builder) {
        String value = mSettingsManager.getValue(SettingsManager.KEY_VIDEO_HDR_VALUE);
        if (value != null) {
            try {
                builder.set(CaptureModule.support_video_hdr_values, Integer.parseInt(value));
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "cannot find vendor tag: " + support_video_hdr_values.toString());
            }
        }
    }

    private void applyCaptureMFNR(CaptureRequest.Builder builder) {
        boolean isMfnrEnable = isMFNREnabled();
        int noiseReduMode = (isMfnrEnable ? CameraMetadata.NOISE_REDUCTION_MODE_HIGH_QUALITY :
                CameraMetadata.NOISE_REDUCTION_MODE_FAST);
        Log.v(TAG, "applyCaptureMFNR mfnrEnable :" + isMfnrEnable + ", noiseReduMode :"
                + noiseReduMode);
        builder.set(CaptureRequest.NOISE_REDUCTION_MODE, noiseReduMode);
        if (isMfnrEnable) {
            try {
                builder.set(custom_noise_reduction, (byte) 0x01);
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "capture can`t find vendor tag: " + custom_noise_reduction.toString());
            }
        }
    }

    private void applyCaptureBurstFps(CaptureRequest.Builder builder) {
        try {
            Log.v(TAG, " applyCaptureBurstFps burst fps mLongshotActive :" + mLongshotActive +
                    ", value :" + (byte)(mLongshotActive ? 0x01 : 0x00));
            builder.set(CaptureModule.capture_burst_fps, (byte)(mLongshotActive ? 0x01 : 0x00));
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "cannot find vendor tag: " + capture_burst_fps.toString());
        }
    }

    private void setOpModeForVideoStream(int cameraId) {
        int index = getSensorTableHFRRange();
        if (index != -1) {
            if (DEBUG) {
                Log.v(TAG, "setOpModeForVideoStream index :" + index);
            }

            Method method_setVendorStreamConfigMode = null;
            try {
                if (method_setVendorStreamConfigMode == null) {
                    method_setVendorStreamConfigMode = CameraDevice.class.getDeclaredMethod(
                            "setVendorStreamConfigMode", int.class);
                }
                method_setVendorStreamConfigMode.invoke(mCameraDevice[cameraId], index);
            } catch (Exception exception) {
                Log.w(TAG, "setOpModeForVideoStream method is not exist");
                exception.printStackTrace();
            }
        }
    }

    private void updateVideoFlash(int id) {
        if (!mIsRecordingVideo && !mIsPreviewingVideo) return;
        applyVideoFlash(mVideoRecordRequestBuilder, id);
        applyVideoFlash(mVideoPreviewRequestBuilder, id);
        CaptureRequest captureRequest = null;
        try {
            if (mMediaRecorderPausing) {
                captureRequest = mVideoPreviewRequestBuilder.build();
            } else {
                captureRequest = mVideoRecordRequestBuilder.build();
            }
            if (mCurrentSession instanceof CameraConstrainedHighSpeedCaptureSession) {
                CameraConstrainedHighSpeedCaptureSession session =
                        (CameraConstrainedHighSpeedCaptureSession) mCurrentSession;
                List requestList = session.createHighSpeedRequestList(captureRequest);
                session.setRepeatingBurst(requestList, mCaptureCallback, mCameraHandler);
            } else if (isSSMEnabled()) {
                mCurrentSession.setRepeatingBurst(createSSMBatchRequest(mIsRecordingVideo ?
                                mVideoRecordRequestBuilder : mVideoPreviewRequestBuilder),
                                mCaptureCallback, mCameraHandler);
            } else {
                mCurrentSession.setRepeatingRequest(captureRequest, mCaptureCallback,
                        mCameraHandler);
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void applyVideoFlash(CaptureRequest.Builder builder, int id) {
        if (mSettingsManager.isFlashSupported(id)) {
            String value = mSettingsManager.getValue(SettingsManager.KEY_VIDEO_FLASH_MODE);
            if (value == null) return;
            builder.set(CaptureRequest.FLASH_MODE, value.equals("on") ?
                    CaptureRequest.FLASH_MODE_TORCH : CaptureRequest.FLASH_MODE_OFF);
        } else {
            builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
        }

    }

    private void applyNoiseReduction(CaptureRequest.Builder builder) {
        String value = mSettingsManager.getValue(SettingsManager.KEY_NOISE_REDUCTION);
        if (value == null) return;
        int noiseReduction = SettingTranslation.getNoiseReduction(value);
        builder.set(CaptureRequest.NOISE_REDUCTION_MODE, noiseReduction);
    }

    private void applyVideoStabilization(CaptureRequest.Builder builder) {
        String value = mSettingsManager.getValue(SettingsManager.KEY_DIS);
        if (value == null) return;
        if (value.equals("on")) {
            builder.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, CaptureRequest
                    .CONTROL_VIDEO_STABILIZATION_MODE_ON);
        } else {
            builder.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, CaptureRequest
                    .CONTROL_VIDEO_STABILIZATION_MODE_OFF);
        }
    }

    private void applyVideoEncoderProfile(CaptureRequest.Builder builder) {
        String profile = mSettingsManager.getValue(SettingsManager.KEY_VIDEO_ENCODER_PROFILE);
        int mode = 0;
        if (profile.equals("HEVCProfileMain10HDR10")) {
            mode = 2;
        } else if (profile.equals("HEVCProfileMain10")) {
            mode = 1;
        }
        Log.d(TAG, "setHDRVideoMode: " + mode);
        VendorTagUtil.setHDRVideoMode(builder, (byte)mode);
    }

    private boolean isVideoEncoderProfileSupported() {
        return !mSettingsManager.getValue(SettingsManager.KEY_VIDEO_ENCODER_PROFILE).equals("off");
    }

    private long getTimeLapseVideoLength(long deltaMs) {
        // For better approximation calculate fractional number of frames captured.
        // This will update the video time at a higher resolution.
        double numberOfFrames = (double) deltaMs / mTimeBetweenTimeLapseFrameCaptureMs;
        return (long) (numberOfFrames / mProfile.videoFrameRate * 1000);
    }

    private void updateRecordingTime() {
        if (!mIsRecordingVideo) {
            return;
        }
        if (mMediaRecorderPausing) {
            return;
        }

        long now = SystemClock.uptimeMillis();
        long delta = now - mRecordingStartTime + mRecordingTotalTime;

        // Starting a minute before reaching the max duration
        // limit, we'll countdown the remaining time instead.
        boolean countdownRemainingTime = (mMaxVideoDurationInMs != 0
                && delta >= mMaxVideoDurationInMs - 60000);

        long deltaAdjusted = delta;
        if (countdownRemainingTime) {
            deltaAdjusted = Math.max(0, mMaxVideoDurationInMs - deltaAdjusted) + 999;
        }
        String text;

        long targetNextUpdateDelay;
        if (!mCaptureTimeLapse) {
            text = CameraUtil.millisecondToTimeString(deltaAdjusted, false);
            targetNextUpdateDelay = 1000;
        } else {
            // The length of time lapse video is different from the length
            // of the actual wall clock time elapsed. Display the video length
            // only in format hh:mm:ss.dd, where dd are the centi seconds.
            text = CameraUtil.millisecondToTimeString(getTimeLapseVideoLength(delta), true);
            targetNextUpdateDelay = mTimeBetweenTimeLapseFrameCaptureMs;
        }

        mUI.setRecordingTime(text);

        if (mRecordingTimeCountsDown != countdownRemainingTime) {
            // Avoid setting the color on every update, do it only
            // when it needs changing.
            mRecordingTimeCountsDown = countdownRemainingTime;

            int color = mActivity.getResources().getColor(countdownRemainingTime
                    ? R.color.recording_time_remaining_text
                    : R.color.recording_time_elapsed_text);

            mUI.setRecordingTimeTextColor(color);
        }

        long actualNextUpdateDelay = targetNextUpdateDelay - (delta % targetNextUpdateDelay);
        mHandler.sendEmptyMessageDelayed(
                UPDATE_RECORD_TIME, actualNextUpdateDelay);
    }

    private void pauseVideoRecording() {
        Log.v(TAG, "pauseVideoRecording");
        mMediaRecorderPausing = true;
        mRecordingTotalTime += SystemClock.uptimeMillis() - mRecordingStartTime;
        String value = mSettingsManager.getValue(SettingsManager.KEY_EIS_VALUE);
        boolean noNeedEndofStreamWhenPause = value != null && value.equals("V3");
        // As EIS is not supported for HFR case (>=120 )
        // and FOVC also currently don’t require this for >=120 case
        // so use noNeedEndOfStreamInHFR to control
        boolean noNeedEndOfStreamInHFR = mHighSpeedCapture &&
                ((int)mHighSpeedFPSRange.getUpper() >= HIGH_SESSION_MAX_FPS);
        if (noNeedEndofStreamWhenPause || noNeedEndOfStreamInHFR) {
            mMediaRecorder.pause();
        } else {
            setEndOfStream(false, false);
        }
    }

    private void resumeVideoRecording() {
        Log.v(TAG, "resumeVideoRecording");
        mMediaRecorderPausing = false;
        mRecordingStartTime = SystemClock.uptimeMillis();
        updateRecordingTime();
        setEndOfStream(true, false);
        if (!ApiHelper.HAS_RESUME_SUPPORTED){
            mMediaRecorder.start();
            Log.d(TAG, "StartRecordingVideo done.");
        } else {
            try {
                Method resumeRec = Class.forName("android.media.MediaRecorder").getMethod("resume");
                resumeRec.invoke(mMediaRecorder);
            } catch (Exception e) {
                Log.v(TAG, "resume method not implemented");
            }
        }
    }

    private void setEndOfStream(boolean isResume, boolean isStopRecord) {
        if (mCurrentSession == null) {
            return;
        }
        CaptureRequest.Builder captureRequestBuilder = null;
        try {
            if (isResume) {
                captureRequestBuilder = mVideoRecordRequestBuilder;
                try {
                    captureRequestBuilder.set(CaptureModule.recording_end_stream, (byte) 0x00);
                } catch(IllegalArgumentException illegalArgumentException) {
                    Log.w(TAG, "can not find vendor tag: org.quic.camera.recording.endOfStream");
                }
            } else {
                // is pause or stopRecord
                if ((mMediaRecorderPausing || mStopRecPending) && (mCurrentSession != null)) {
                    mCurrentSession.stopRepeating();
                    try {
                        mVideoRecordRequestBuilder.set(CaptureModule.recording_end_stream, (byte) 0x01);
                    } catch (IllegalArgumentException illegalArgumentException) {
                        Log.w(TAG, "can not find vendor tag: org.quic.camera.recording.endOfStream");
                    }
                    try {
                        if (mCurrentSession instanceof CameraConstrainedHighSpeedCaptureSession) {
                            List requestList = ((CameraConstrainedHighSpeedCaptureSession) mCurrentSession)
                                    .createHighSpeedRequestList(mVideoRecordRequestBuilder.build());
                            mCurrentSession.captureBurst(requestList, mCaptureCallback, mCameraHandler);
                        } else if (isSSMEnabled()) {
                            mCurrentSession.captureBurst(createSSMBatchRequest(mVideoRecordRequestBuilder),
                                    mCaptureCallback, mCameraHandler);
                        } else {
                            mCurrentSession.capture(mVideoRecordRequestBuilder.build(), mCaptureCallback,
                                    mCameraHandler);
                        }
                    } catch (UnsupportedOperationException exception) {
                        Log.w(TAG, " UnsupportedOperationException ");
                        exception.printStackTrace();
                    }
                    Log.d(TAG, "Set endofstream TAG is done from APP");
                }
                if (!isStopRecord) {
                    //is pause record
                    mMediaRecorder.pause();
                    captureRequestBuilder = mVideoPreviewRequestBuilder;
                    applyVideoCommentSettings(captureRequestBuilder, getMainCameraId());
                }
            }

            // set preview at resume and pause, no need repeating at stop
            if (captureRequestBuilder != null && (mCurrentSession != null) && !isStopRecord) {
                if (mCurrentSession instanceof CameraConstrainedHighSpeedCaptureSession) {
                    List requestList = ((CameraConstrainedHighSpeedCaptureSession) mCurrentSession)
                            .createHighSpeedRequestList(captureRequestBuilder.build());
                    mCurrentSession.setRepeatingBurst(requestList,
                            mCaptureCallback, mCameraHandler);
                } else {
                    mCurrentSession.setRepeatingRequest(captureRequestBuilder.build(),
                            mCaptureCallback, mCameraHandler);
                }
            }
        } catch (CameraAccessException | IllegalStateException | NullPointerException | IllegalArgumentException e) {
            e.printStackTrace();
        }
    }

    public void onButtonPause() {
        if (!isRecorderReady())
            return;
        pauseVideoRecording();
    }

    public void onButtonContinue() {
        if (!isRecorderReady())
            return;
        resumeVideoRecording();
    }

    private boolean isEISDisable() {
        boolean result = true;
        String value = mSettingsManager.getValue(SettingsManager.KEY_EIS_VALUE);
        if (value != null) {
            result = value.equals(mActivity.getResources().getString(
                    R.string.pref_camera2_eis_entry_value_disable));
        } else {
            result = false;
        }
        Log.v(TAG, "isEISDisable :" + result);
        return result;
    }

    private boolean isAbortCapturesEnable() {
        boolean result = true;
        String value = mSettingsManager.getValue(SettingsManager.KEY_ABORT_CAPTURES);
        if (value != null) {
            result = value.equals(mActivity.getResources().getString(
                    R.string.pref_camera2_abort_captures_entry_value_enable));
        } else {
            result = false;
        }
        Log.v(TAG, "isAbortCapturesEnable :" + result);
        return result;
    }

    private boolean isSendRequestAfterFlushEnable() {
        return PersistUtil.isSendRequestAfterFlush();
    }

    private void exitVideoModule(){
        Log.d(TAG, "exitVideoModule ");
        if (!mIsPreviewingVideo) {
            return;
        }
        mFrameProcessor.setVideoOutputSurface(null);
        mFrameProcessor.onClose();
        closePreviewSession();
        // release media recorder
        releaseMediaRecorder();
        releaseAudioFocus();
        mIsPreviewingVideo = false;
    }

    private void stopRecordingVideo(int cameraId) {
        Log.d(TAG, "stopRecordingVideo " + cameraId);
        mStopRecordingTime = System.currentTimeMillis();
        if (isSSMEnabled()) {
            updateProgressBar(false);
            if (!mSSMCaptureCompleteFlag) {
                warningToast("Super Slow Motion is not finished");
            }
        }
        mStopRecPending = true;
        boolean shouldAddToMediaStoreNow = false;
        // Stop recording
        checkAndPlayRecordSound(cameraId, false);
        setEndOfStream(false, true);
        mFrameProcessor.setVideoOutputSurface(null);
        mFrameProcessor.onClose();
        if (mLiveShotInitHeifWriter != null) {
            mLiveShotInitHeifWriter.close();
        }
        mIsRecordingVideo = false;
        if (isEISDisable() && isAbortCapturesEnable() && mCurrentSession != null) {
            try {
                mCurrentSession.abortCaptures();
                Log.d(TAG, "stopRecordingVideo call abortCaptures ");
            } catch (CameraAccessException|IllegalStateException e) {
                e.printStackTrace();
            }
        }
        if (!mPaused && !isAbortCapturesEnable()) {
            setVideoFlashOff();
            closePreviewSession();
        }
        mMediaRecorderStarted = false;
        try {
            mMediaRecorder.setOnErrorListener(null);
            mMediaRecorder.setOnInfoListener(null);
            mMediaRecorder.stop();
            shouldAddToMediaStoreNow = true;
            Log.d(TAG, "stopRecordingVideo done. Time=" +
                    (System.currentTimeMillis() - mStopRecordingTime) + "ms");
            AccessibilityUtils.makeAnnouncement(mUI.getVideoButton(),
                    mActivity.getString(R.string.video_recording_stopped));
            mMediaRecorder.reset();
        } catch (RuntimeException e) {
            Log.w(TAG, "MediaRecoder stop fail",  e);
            if (mVideoFilename != null) deleteVideoFile(mVideoFilename);
        }
        if (shouldAddToMediaStoreNow) {
            saveVideo();
        }
        keepScreenOnAwhile();
        // release media recorder
        releaseMediaRecorder();
        releaseAudioFocus();
        mUI.showRecordingUI(false, false);
        mUI.enableShutter(true);

        if (mIntentMode == INTENT_MODE_VIDEO) {
            if (isQuickCapture()) {
                onRecordingDone(true);
            } else {
                mTempHoldVideoInVideoIntent = true;
                Bitmap thumbnail = getVideoThumbnail();
                mUI.showRecordVideoForReview(thumbnail);
            }
        }
        if(mFrameProcessor != null) {
            mFrameProcessor.onOpen(getFrameProcFilterId(), mPreviewSize);
        }
        if (mIntentMode != INTENT_MODE_VIDEO && !mPaused) {
            createSessions();
        }
        mUI.showUIafterRecording();
        mUI.resetTrackingFocus();
        mStopRecPending = false;
    }

    private void setVideoFlashOff() {
        Log.d(TAG, "setVideoFlashOff: currentsession:" +mCurrentSession +
                ",currentclosed:" + mCurrentSessionClosed );
        if (mCurrentSession == null || mCurrentSessionClosed) {
            return;
        }
        try {
            mVideoRecordRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        }
        try {
            mCurrentSession.stopRepeating();
            if (mCurrentSession instanceof CameraConstrainedHighSpeedCaptureSession) {
                List requestList = ((CameraConstrainedHighSpeedCaptureSession) mCurrentSession)
                        .createHighSpeedRequestList(mVideoRecordRequestBuilder.build());
                mCurrentSession.captureBurst(requestList, mCaptureCallback, mCameraHandler);
            } else if (!isSSMEnabled()){
                mCurrentSession.capture(mVideoRecordRequestBuilder.build(), mCaptureCallback,
                        mCameraHandler);
            }
        } catch (CameraAccessException | IllegalStateException e) {
            e.printStackTrace();
        }
    }

    private void closePreviewSession() {
        Log.d(TAG, "closePreviewSession: currentsession:" +mCurrentSession + ",currentclosed:" + mCurrentSessionClosed );
        if (mCurrentSession == null || mCurrentSessionClosed) {
            return;
        }
        //if have this, video switch to photo, photo will have no preview
        //mCurrentSession.close();
        mCurrentSessionClosed = true;
        mCurrentSession = null;
    }

    private String createName(long dateTaken) {
        Date date = new Date(dateTaken);
        SimpleDateFormat dateFormat = new SimpleDateFormat(
                mActivity.getString(R.string.video_file_name_format));

        return dateFormat.format(date);
    }

    private String generateVideoFilename(int outputFileFormat) {
        long dateTaken = System.currentTimeMillis();
        String title = createName(dateTaken);
        String filename = title + CameraUtil.convertOutputFormatToFileExt(outputFileFormat);
        String mime = CameraUtil.convertOutputFormatToMimeType(outputFileFormat);
        String path;
        if (Storage.isSaveSDCard() && SDCard.instance().isWriteable()) {
            path = SDCard.instance().getDirectory() + '/' + filename;
        } else {
            path = Storage.DIRECTORY + '/' + filename;
        }
        mCurrentVideoValues = new ContentValues(9);
        mCurrentVideoValues.put(MediaStore.Video.Media.TITLE, title);
        mCurrentVideoValues.put(MediaStore.Video.Media.DISPLAY_NAME, filename);
        mCurrentVideoValues.put(MediaStore.Video.Media.DATE_TAKEN, dateTaken);
        mCurrentVideoValues.put(MediaStore.MediaColumns.DATE_MODIFIED, dateTaken / 1000);
        mCurrentVideoValues.put(MediaStore.Video.Media.MIME_TYPE, mime);
        mCurrentVideoValues.put(MediaStore.Video.Media.DATA, path);
        mCurrentVideoValues.put(MediaStore.Video.Media.RESOLUTION,
                "" + mVideoSize.getWidth() + "x" + mVideoSize.getHeight());
        Location loc = mLocationManager.getCurrentLocation();
        if (loc != null) {
            mCurrentVideoValues.put(MediaStore.Video.Media.LATITUDE, loc.getLatitude());
            mCurrentVideoValues.put(MediaStore.Video.Media.LONGITUDE, loc.getLongitude());
        }
        mVideoFilename = path;
        return path;
    }

    private void saveVideo() {
        if (mVideoFileDescriptor == null) {
            File origFile = new File(mVideoFilename);
            if (!origFile.exists() || origFile.length() <= 0) {
                Log.e(TAG, "Invalid file");
                mCurrentVideoValues = null;
                return;
            }

            long duration = 0L;
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();

            try {
                retriever.setDataSource(mVideoFilename);
                duration = Long.valueOf(retriever.extractMetadata(
                        MediaMetadataRetriever.METADATA_KEY_DURATION));
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "cannot access the file");
            }
            retriever.release();

            mActivity.getMediaSaveService().addVideo(mVideoFilename,
                    duration, mCurrentVideoValues,
                    mOnVideoSavedListener, mContentResolver);
        }
        mCurrentVideoValues = null;
    }

    private void updateBitrateForNonHFR(int videoEncoder, int bitRate, int height, int width) {
        MediaCodecList allCodecs = new MediaCodecList(MediaCodecList.ALL_CODECS);
        for (MediaCodecInfo info : allCodecs.getCodecInfos()) {
            if (!info.isEncoder() || info.getName().contains("google")) continue;
            for (String type : info.getSupportedTypes()) {
                if ((videoEncoder == MediaRecorder.VideoEncoder.MPEG_4_SP && type.equalsIgnoreCase(MediaFormat.MIMETYPE_VIDEO_MPEG4))
                        || (videoEncoder == MediaRecorder.VideoEncoder.H263 && type.equalsIgnoreCase(MediaFormat.MIMETYPE_VIDEO_H263)))
                {
                    CodecCapabilities codecCapabilities = info.getCapabilitiesForType(type);
                    VideoCapabilities videoCapabilities = codecCapabilities.getVideoCapabilities();
                    try {
                        if (videoCapabilities != null) {
                            Log.d(TAG, "updateBitrate type is " + type + " " + info.getName());
                            int maxBitRate = videoCapabilities.getBitrateRange().getUpper().intValue();
                            Log.d(TAG, "maxBitRate is " + maxBitRate + ", profileBitRate is " + bitRate);
                            mMediaRecorder.setVideoEncodingBitRate(Math.min(bitRate, maxBitRate));
                            return;
                        }
                    } catch (IllegalArgumentException e) {

                    }
                }
            }
        }
        Log.i(TAG, "updateBitrate video bitrate: "+ bitRate);
        mMediaRecorder.setVideoEncodingBitRate(bitRate);
    }

    private void setUpMediaRecorder(int cameraId) throws IOException {
        Log.d(TAG, "setUpMediaRecorder");
        String videoSize = mSettingsManager.getValue(SettingsManager.KEY_VIDEO_QUALITY);
        int size = CameraSettings.VIDEO_QUALITY_TABLE.get(videoSize);
        Intent intent = mActivity.getIntent();
        if (intent.hasExtra(MediaStore.EXTRA_VIDEO_QUALITY)) {
            int extraVideoQuality =
                    intent.getIntExtra(MediaStore.EXTRA_VIDEO_QUALITY, 0);
            if (extraVideoQuality > 0) {
                size = CamcorderProfile.QUALITY_HIGH;
            } else {  // 0 is mms.
                size = CamcorderProfile.QUALITY_LOW;
            }
        }
        if (mCaptureTimeLapse) {
            size = CameraSettings.getTimeLapseQualityFor(size);
        }

        Bundle myExtras = intent.getExtras();

        if (mMediaRecorder == null) mMediaRecorder = new MediaRecorder();
        mMediaRecorder.reset();

        updateHFRSetting();
        mHighSpeedCapture = mHighSpeedCapture && (myExtras == null); //MMS not support high speed
        boolean hfr = mHighSpeedCapture && !mHighSpeedRecordingMode;

        if (CamcorderProfile.hasProfile(cameraId, size)) {
            mProfile = CamcorderProfile.get(cameraId, size);
        } else {
            warningToast(R.string.error_app_unsupported_profile);
            throw new IllegalArgumentException("error_app_unsupported_profile");
        }

        int videoWidth = mProfile.videoFrameWidth;
        int videoHeight = mProfile.videoFrameHeight;
        mUnsupportedResolution = false;

        int videoEncoder = SettingTranslation
                .getVideoEncoder(mSettingsManager.getValue(SettingsManager.KEY_VIDEO_ENCODER));
        int audioEncoder = SettingTranslation
                .getAudioEncoder(mSettingsManager.getValue(SettingsManager.KEY_AUDIO_ENCODER));

        mProfile.videoCodec = videoEncoder;
        if (!mCaptureTimeLapse && !hfr && !mSuperSlomoCapture) {
            mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mProfile.audioCodec = audioEncoder;
            if (mProfile.audioCodec == MediaRecorder.AudioEncoder.AMR_NB) {
                mProfile.fileFormat = MediaRecorder.OutputFormat.THREE_GPP;
            }
        }

        if ( isVideoEncoderProfileSupported()
                && VendorTagUtil.isHDRVideoModeSupported(mCameraDevice[cameraId])) {
            int videoEncoderProfile = SettingTranslation.getVideoEncoderProfile(
                    mSettingsManager.getValue(SettingsManager.KEY_VIDEO_ENCODER_PROFILE));
            Log.d(TAG, "setVideoEncodingProfileLevel: " + videoEncoderProfile + " " + MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel1);
            mMediaRecorder.setVideoEncodingProfileLevel(videoEncoderProfile,
                    MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel1);
        }

        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);

        mMediaRecorder.setOutputFormat(mProfile.fileFormat);
        closeVideoFileDescriptor();
        setMediaRecorderOutputFile(myExtras);
        mMediaRecorder.setVideoFrameRate(mProfile.videoFrameRate);
        if (!mHighSpeedCapture) {
            updateBitrateForNonHFR(videoEncoder, mProfile.videoBitRate, videoHeight, videoWidth);
        }
        if(mFrameProcessor.isFrameFilterEnabled()) {
            mMediaRecorder.setVideoSize(mProfile.videoFrameHeight, mProfile.videoFrameWidth);
        } else {
            mMediaRecorder.setVideoSize(mProfile.videoFrameWidth, mProfile.videoFrameHeight);
        }
        mMediaRecorder.setVideoEncoder(videoEncoder);
        if (!mCaptureTimeLapse && !hfr && !mSuperSlomoCapture) {
            mMediaRecorder.setAudioEncodingBitRate(mProfile.audioBitRate);
            mMediaRecorder.setAudioChannels(mProfile.audioChannels);
            mMediaRecorder.setAudioSamplingRate(mProfile.audioSampleRate);
            mMediaRecorder.setAudioEncoder(audioEncoder);
        }
        mMediaRecorder.setMaxDuration(mMaxVideoDurationInMs);

        Log.i(TAG, "Profile video frame rate: "+ mProfile.videoFrameRate);
        if (mCaptureTimeLapse) {
            double fps = 1000 / (double) mTimeBetweenTimeLapseFrameCaptureMs;
            mMediaRecorder.setCaptureRate(fps);
        }  else if (mHighSpeedCapture) {
            mHighSpeedFPSRange = new Range(mHighSpeedCaptureRate, mHighSpeedCaptureRate);
            int fps = (int) mHighSpeedFPSRange.getUpper();
            int targetRate = mSuperSlomoCapture ? 30 : (mHighSpeedRecordingMode ? fps : 30);
            mMediaRecorder.setCaptureRate(mSuperSlomoCapture ? 30 : fps);
            mMediaRecorder.setVideoFrameRate(targetRate);
            Log.i(TAG, "Capture rate: "+fps+", Target rate: "+targetRate);
            int scaledBitrate = mSettingsManager.getHighSpeedVideoEncoderBitRate(mProfile, targetRate, fps);
            Log.i(TAG, "Scaled video bitrate : " + scaledBitrate);
            mMediaRecorder.setVideoEncodingBitRate(scaledBitrate);
        }

        long requestedSizeLimit = 0;
        if (isVideoCaptureIntent() && myExtras != null) {
            requestedSizeLimit = myExtras.getLong(MediaStore.EXTRA_SIZE_LIMIT);
        }
        //check if codec supports the resolution, otherwise throw toast
        if (DEBUG) Log.d(TAG,"mProfile.videoCodec="+mProfile.videoCodec);
        String type = SettingTranslation.getVideoEncoderType(mProfile.videoCodec);
        if (DEBUG) Log.d(TAG,"codec type="+type);
        MediaCodecList allCodecs = new MediaCodecList(MediaCodecList.ALL_CODECS);
        MediaFormat format = MediaFormat.createVideoFormat(type,videoWidth,videoHeight);
        try{
            String encodeName = allCodecs.findEncoderForFormat(format);
            if (DEBUG) Log.d(TAG,"encodeName="+encodeName);
        } catch (IllegalArgumentException| NullPointerException e){
            e.printStackTrace();
            if (DEBUG) Log.d(TAG,"error="+e.getLocalizedMessage());
            mUnsupportedResolution = true;
        } finally {
            if (mUnsupportedResolution) {
                warningToast(R.string.error_app_unsupported);
                return;
            }
        }

        // Set maximum file size.
        long maxFileSize = mActivity.getStorageSpaceBytes() - Storage.LOW_STORAGE_THRESHOLD_BYTES;
        if (requestedSizeLimit > 0 && requestedSizeLimit < maxFileSize) {
            maxFileSize = requestedSizeLimit;
        }

        if (Storage.isSaveSDCard() && maxFileSize > SDCARD_SIZE_LIMIT) {
            maxFileSize = SDCARD_SIZE_LIMIT;
        }
        Log.i(TAG, "MediaRecorder setMaxFileSize: " + maxFileSize);
        try {
            mMediaRecorder.setMaxFileSize(maxFileSize);
        } catch (RuntimeException exception) {
            // We are going to ignore failure of setMaxFileSize here, as
            // a) The composer selected may simply not support it, or
            // b) The underlying media framework may not handle 64-bit range
            // on the size restriction.
        }

        Location loc = mLocationManager.getCurrentLocation();
        if (loc != null) {
            mMediaRecorder.setLocation((float) loc.getLatitude(),
                    (float) loc.getLongitude());
        }
        int rotation = CameraUtil.getJpegRotation(cameraId, mOrientation);
        String videoRotation = mSettingsManager.getValue(SettingsManager.KEY_VIDEO_ROTATION);
        if (videoRotation != null) {
            rotation += Integer.parseInt(videoRotation);
            rotation = rotation % 360;
        }
        if(mFrameProcessor.isFrameFilterEnabled()) {
            mMediaRecorder.setOrientationHint(0);
        } else {
            mMediaRecorder.setOrientationHint(rotation);
        }
        prepareMediaRecorder();
        mMediaRecorder.setOnErrorListener(this);
        mMediaRecorder.setOnInfoListener(this);
    }

    private void prepareMediaRecorder() throws IOException {
        try {
            mMediaRecorder.prepare();
        } catch (IOException e) {
            Log.e(TAG, "prepare failed for " + mVideoFilename, e);
            releaseMediaRecorder();
            throw new IOException(e);
        }
    }

    private void setMediaRecorderOutputFile(Bundle myExtras) {
        if (mIntentMode == CaptureModule.INTENT_MODE_VIDEO && myExtras != null) {
            Uri saveUri = (Uri) myExtras.getParcelable(MediaStore.EXTRA_OUTPUT);
            if (saveUri != null) {
                try {
                    mCurrentVideoUri = saveUri;
                    mVideoFileDescriptor =
                            mContentResolver.openFileDescriptor(saveUri, "rw");
                    mCurrentVideoUri = saveUri;
                } catch (java.io.FileNotFoundException ex) {
                    // invalid uri
                    Log.e(TAG, ex.toString());
                }
                mMediaRecorder.setOutputFile(mVideoFileDescriptor.getFileDescriptor());
            } else {
                String fileName = generateVideoFilename(mProfile.fileFormat);
                Log.v(TAG, "New video filename: " + fileName);
                mMediaRecorder.setOutputFile(fileName);
            }
        } else {
            String fileName = generateVideoFilename(mProfile.fileFormat);
            Log.v(TAG, "New video filename: " + fileName);
            mMediaRecorder.setOutputFile(fileName);
        }
    }

    public void onVideoButtonClick() {
        if (PersistUtil.isTraceEnable())
            Trace.beginSection("onVideoButtonClick recording");
        if (!isRecorderReady() || getCameraMode() == DUAL_MODE) return;

        if (!mIsRecordingVideo) {
            if (!startRecordingVideo(getMainCameraId())) {
                // Show ui when start recording failed.
                mUI.showUIafterRecording();
                mFrameProcessor.setVideoOutputSurface(null);
            }
        } else if (mMediaRecorderStarted) {
            stopRecordingVideo(getMainCameraId());
        }
        if (PersistUtil.isTraceEnable())
            Trace.endSection();
    }

    @Override
    public void onShutterButtonClick() {
        if (PersistUtil.isTraceEnable())
            Trace.beginSection("onShutterButtonClick capture");
        if (mActivity.getStorageSpaceBytes() <= Storage.LOW_STORAGE_THRESHOLD_BYTES) {
            Log.i(TAG, "Not enough space or storage not ready. remaining="
                    + mActivity.getStorageSpaceBytes());
            return;
        }

        Log.d(TAG,"onShutterButtonClick");

        if (mCurrentSceneMode.mode == CameraMode.HFR ||
                mCurrentSceneMode.mode == CameraMode.VIDEO) {
            if (mSettingsManager.isLiveshotSupported(mVideoSize,mSettingsManager.getVideoFPS())){
                captureVideoSnapshot(getMainCameraId());
            }
            return;
        }
        if (!mUI.isShutterEnabled()) {
            return;
        }

        String timer = mSettingsManager.getValue(SettingsManager.KEY_TIMER);
        int seconds = Integer.parseInt(timer);
        // When shutter button is pressed, check whether the previous countdown is
        // finished. If not, cancel the previous countdown and start a new one.
        if (mUI.isCountingDown()) {
            mUI.cancelCountDown();
            return;
        }
        if (seconds > 0) {
            mUI.startCountDown(seconds, true);
        } else {
            if (mChosenImageFormat == ImageFormat.YUV_420_888 && mPostProcessor.isItBusy()) {
                warningToast("It's still busy processing previous scene mode request.");
                return;
            }
            checkSelfieFlashAndTakePicture();
        }
        if (PersistUtil.isTraceEnable())
            Trace.endSection();
    }

    private void warningToast(final String msg) {
        mActivity.runOnUiThread(new Runnable() {
            public void run() {
                RotateTextToast.makeText(mActivity, msg,
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void warningToast(final int sourceId) {
        warningToast(sourceId, true);
    }

    private void warningToast(final int sourceId, boolean isLongShow) {
        mActivity.runOnUiThread(new Runnable() {
            public void run() {
                RotateTextToast.makeText(mActivity, sourceId,
                        isLongShow ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT).show();
            }
        });
    }

    public boolean isLongShotSettingEnabled() {
        String longshot = mSettingsManager.getValue(SettingsManager.KEY_LONGSHOT);
        if(longshot.equals("on")) {
            return true;
        }
        return false;
    }

    @Override
    public void onShutterButtonLongClick() {
        if (PersistUtil.isTraceEnable())
            Trace.beginSection("onShutterButtonLongClick longshot");
        if (isBackCamera() && getCameraMode() == DUAL_MODE) return;

        if (isLongShotSettingEnabled()) {
            //Cancel the previous countdown when long press shutter button for longshot.
            if (mUI.isCountingDown()) {
                mUI.cancelCountDown();
            }
            //check whether current memory is enough for longshot.
            mActivity.updateStorageSpaceAndHint();

            long storageSpace = mActivity.getStorageSpaceBytes();
            int mLongShotCaptureCountLimit = PersistUtil.getLongshotShotLimit();

            if (storageSpace <= Storage.LOW_STORAGE_THRESHOLD_BYTES + mLongShotCaptureCountLimit
                    * mJpegFileSizeEstimation) {
                Log.i(TAG, "Not enough space or storage not ready. remaining=" + storageSpace);
                return;
            }

            if (isLongshotNeedCancel()) {
                mLongshotActive = false;
                mUI.enableVideo(!mLongshotActive);
                return;
            }

            Log.d(TAG, "Start Longshot");
            mLongshotActive = true;
            mUI.enableVideo(!mLongshotActive);
            checkSelfieFlashAndTakePicture();
        } else {
            RotateTextToast.makeText(mActivity, "Long shot not support", Toast.LENGTH_SHORT).show();
        }
        if (PersistUtil.isTraceEnable())
            Trace.endSection();
    }

    private void estimateJpegFileSize() {
        String quality = mSettingsManager.getValue(SettingsManager
            .KEY_JPEG_QUALITY);
        int[] ratios = mActivity.getResources().getIntArray(R.array.jpegquality_compression_ratio);
        String[] qualities = mActivity.getResources().getStringArray(
                R.array.pref_camera_jpegquality_entryvalues);
        int ratio = 0;
        for (int i = ratios.length - 1; i >= 0; --i) {
            if (qualities[i].equals(quality)) {
                ratio = ratios[i];
                break;
            }
        }
        String pictureSize = mSettingsManager.getValue(SettingsManager
                .KEY_PICTURE_SIZE);

        Size size = parsePictureSize(pictureSize);
        if (ratio == 0) {
            Log.d(TAG, "mJpegFileSizeEstimation 0");
        } else {
            mJpegFileSizeEstimation =  size.getWidth() * size.getHeight() * 3 / ratio;
            Log.d(TAG, "mJpegFileSizeEstimation " + mJpegFileSizeEstimation);
        }

    }

    private boolean isLongshotNeedCancel() {
        if (PersistUtil.getSkipMemoryCheck()) {
            return false;
        }

        if (Storage.getAvailableSpace() <= Storage.LOW_STORAGE_THRESHOLD_BYTES) {
            Log.w(TAG, "current storage is full");
            return true;
        }

        long totalMemory = Runtime.getRuntime().totalMemory();
        long maxMemory = Runtime.getRuntime().maxMemory();
        long remainMemory = maxMemory - totalMemory;

        if (remainMemory <= LONGSHOT_CANCEL_THRESHOLD) {
            Log.e(TAG, "cancel longshot: free=" + remainMemory
                    + " threshold=" + LONGSHOT_CANCEL_THRESHOLD);
            RotateTextToast.makeText(mActivity, R.string.msg_cancel_longshot_for_limited_memory,
                    Toast.LENGTH_SHORT).show();
            return true;
        }

        if ( mIsRecordingVideo ) {
            Log.e(TAG, " cancel longshot:not supported when recording");
            return true;
        }
        return false;
    }

    private boolean isFlashOff(int id) {
        if (!mSettingsManager.isFlashSupported(id)) return true;
        return mSettingsManager.getValue(SettingsManager.KEY_FLASH_MODE).equals("off");
    }

    private boolean isFlashOn(int id) {
        if (!mSettingsManager.isFlashSupported(id)) return false;
        return mSettingsManager.getValue(SettingsManager.KEY_FLASH_MODE).equals("on");
    }

    private void initializePreviewConfiguration(int id) {
        mPreviewRequestBuilder[id].set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest
                .CONTROL_AF_TRIGGER_IDLE);
        applyFlash(mPreviewRequestBuilder[id], id);
        applyCommonSettings(mPreviewRequestBuilder[id], id);
    }

    public float getZoomValue() {
        return mZoomValue;
    }

    public Rect cropRegionForZoom(int id) {
        if (DEBUG) {
            Log.d(TAG, "cropRegionForZoom " + id);
        }
        Rect activeRegion = mSettingsManager.getSensorActiveArraySize(id);
        Rect cropRegion = new Rect();

        int xCenter = activeRegion.width() / 2;
        int yCenter = activeRegion.height() / 2;
        int xDelta = (int) (activeRegion.width() / (2 * mZoomValue));
        int yDelta = (int) (activeRegion.height() / (2 * mZoomValue));
        cropRegion.set(xCenter - xDelta, yCenter - yDelta, xCenter + xDelta, yCenter + yDelta);
        if (mZoomValue == 1f) {
            mOriginalCropRegion[id] = cropRegion;
        }
        mCropRegion[id] = cropRegion;
        return mCropRegion[id];
    }

    private void applyZoom(CaptureRequest.Builder request, int id) {
        if (!mSupportZoomCapture) return;
        request.set(CaptureRequest.SCALER_CROP_REGION, cropRegionForZoom(id));
    }

    private void applyInstantAEC(CaptureRequest.Builder request) {
        String value = mSettingsManager.getValue(SettingsManager.KEY_INSTANT_AEC);
        if (value == null || value.equals("0"))
            return;
        int intValue = Integer.parseInt(value);
        request.set(CaptureModule.INSTANT_AEC_MODE, intValue);
    }

    private void applySaturationLevel(CaptureRequest.Builder request) {
        String value = mSettingsManager.getValue(SettingsManager.KEY_SATURATION_LEVEL);
        if (value != null) {
            int intValue = Integer.parseInt(value);
            request.set(CaptureModule.SATURATION, intValue);
        }
    }

    private void applyAntiBandingLevel(CaptureRequest.Builder request) {
        String value = mSettingsManager.getValue(SettingsManager.KEY_ANTI_BANDING_LEVEL);
        if (value != null) {
            int intValue = Integer.parseInt(value);
            request.set(CaptureRequest.CONTROL_AE_ANTIBANDING_MODE, intValue);
        }
    }

    private void applySharpnessControlModes(CaptureRequest.Builder request) {
        String value = mSettingsManager.getValue(SettingsManager.KEY_SHARPNESS_CONTROL_MODE);
        if (value != null) {
            int intValue = Integer.parseInt(value);
            try {
                request.set(CaptureModule.sharpness_control, intValue);
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
            }
        }
    }

    private void applyAfModes(CaptureRequest.Builder request) {
        if (getDevAfMode() != -1) {
            request.set(CaptureRequest.CONTROL_AF_MODE, getDevAfMode());
        }
    }

    private int getDevAfMode() {
        String value = mSettingsManager.getValue(SettingsManager.KEY_AF_MODE);
        int intValue = -1;
        if (value != null) {
            intValue = Integer.parseInt(value);
        }
        return intValue;
    }

    private void applyVideoEIS(CaptureRequest.Builder request) {
        if (!mSettingsManager.isDeveloperEnabled()) {
            return;//don't apply if not in dev mode
        }
        String value = mSettingsManager.getValue(SettingsManager.KEY_EIS_VALUE);

        if (DEBUG) {
            Log.d(TAG, "applyVideoEIS EISV select: " + value);
        }
        mStreamConfigOptMode = 0;
        if (value != null) {
            if (value.equals("V2")) {
                mStreamConfigOptMode = STREAM_CONFIG_MODE_QTIEIS_REALTIME;
            } else if (value.equals("V3") || value.equals("V3SetWhenPause")) {
                mStreamConfigOptMode = STREAM_CONFIG_MODE_QTIEIS_LOOKAHEAD;
            }
            byte byteValue = (byte) (value.equals("disable") ? 0x00 : 0x01);
            try {
                request.set(CaptureModule.eis_mode, byteValue);
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
            }
        }
    }

    private void applyEarlyPCR(CaptureRequest.Builder request) {
        try {
            request.set(CaptureModule.earlyPCR, 1);
        } catch (IllegalArgumentException e) {
        }
    }

    private void applySensorModeFS2(CaptureRequest.Builder request) {
        String value = mSettingsManager.getValue(SettingsManager.KEY_SENSOR_MODE_FS2_VALUE);
        if (value != null) {
            int intValue = Integer.parseInt(value);
            byte fs2 =(byte)((intValue == 0) ? 0x00 : 0x01);
            Log.v(TAG, "applySensorModeFS2 intValue : " + intValue + ", fs2 :" + fs2);
            try {
                request.set(CaptureModule.sensor_mode_fs, fs2);
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "hal no vendorTag : " + sensor_mode_fs);
            }
        }
    }

    private void applyExposureMeteringModes(CaptureRequest.Builder request) {
        String value = mSettingsManager.getValue(SettingsManager.KEY_EXPOSURE_METERING_MODE);
        if (value != null) {
            int intValue = Integer.parseInt(value);
            request.set(CaptureModule.exposure_metering, intValue);
        }
    }

    private void applyHistogram(CaptureRequest.Builder request) {
        String value = mSettingsManager.getValue(SettingsManager.KEY_STATS_VISUALIZER_VALUE);
        if (value != null ) {
            if (value.contains("2")) {
                final byte enable = 1;
                request.set(CaptureModule.histMode, enable);
                mHiston = true;
                updateGraghViewVisibility(View.VISIBLE);
                updateGraghView();
                return;
            }
        }
        mHiston = false;
        updateGraghViewVisibility(View.GONE);
    }

    private void applyBGStats(CaptureRequest.Builder request) {
        String value = mSettingsManager.getValue(SettingsManager.KEY_STATS_VISUALIZER_VALUE);
        if (value != null ) {
            if (value.contains("0")){
                final byte enable = 1;
                try{
                    request.set(CaptureModule.bgStatsMode, enable);
                    mBGStatson = true;
                } catch (IllegalArgumentException e) {
                    mBGStatson = false;
                }
                if (mBGStatson) {
                    updateBGStatsVisibility(View.VISIBLE);
                    updateBGStatsView();
                }
                return;
            }
        }
        mBGStatson = false;
        updateBGStatsVisibility(View.GONE);
    }

    private void applyBEStats(CaptureRequest.Builder request) {
        String value = mSettingsManager.getValue(SettingsManager.KEY_STATS_VISUALIZER_VALUE);
        if (value != null ) {
            if (value.contains("1")){
                final byte enable = 1;
                try{
                    request.set(CaptureModule.beStatsMode, enable);
                    mBEStatson = true;
                }catch (IllegalArgumentException e) {
                    mBEStatson = false;
                }
                if (mBEStatson) {
                    updateBEStatsVisibility(View.VISIBLE);
                    updateBEStatsView();
                }
                return;
            }
        }
        mBEStatson = false;
        updateBEStatsVisibility(View.GONE);
    }

    private void applyWbColorTemperature(CaptureRequest.Builder request) {
        final SharedPreferences pref = mActivity.getSharedPreferences(
                ComboPreferences.getLocalSharedPreferencesName(mActivity,
                        mSettingsManager.getCurrentPrepNameKey()), Context.MODE_PRIVATE);
        String manualWBMode = mSettingsManager.getValue(SettingsManager.KEY_MANUAL_WB);
        String cctMode = mActivity.getString(
                R.string.pref_camera_manual_wb_value_color_temperature);
        String gainMode = mActivity.getString(
                R.string.pref_camera_manual_wb_value_rbgb_gains);
        if (manualWBMode.equals(cctMode)) {
            int colorTempValue = Integer.parseInt(pref.getString(
                    SettingsManager.KEY_MANUAL_WB_TEMPERATURE_VALUE, "-1"));
            if (colorTempValue != -1) {
                request.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_OFF);
                VendorTagUtil.setWbColorTemperatureValue(request, colorTempValue);
            }
        } else if (manualWBMode.equals(gainMode)) {
            float rGain = pref.getFloat(SettingsManager.KEY_MANUAL_WB_R_GAIN, -1.0f);
            float gGain = pref.getFloat(SettingsManager.KEY_MANUAL_WB_G_GAIN, -1.0f);
            float bGain = pref.getFloat(SettingsManager.KEY_MANUAL_WB_B_GAIN, -1.0f);
            if (rGain != -1.0 && gGain != -1.0 && bGain != -1.0f) {
                request.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_OFF);
                float[] gains = {rGain, gGain, bGain};
                VendorTagUtil.setMWBGainsValue(request, gains);
            }
        } else {
            VendorTagUtil.setMWBDisableMode(request);
        }
    }

    private void applyToneMapping(CaptureRequest.Builder request) {
        final SharedPreferences pref = mActivity.getSharedPreferences(
                ComboPreferences.getLocalSharedPreferencesName(mActivity,
                        mSettingsManager.getCurrentPrepNameKey()), Context.MODE_PRIVATE);
        String mode = mSettingsManager.getValue(SettingsManager.KEY_TONE_MAPPING);

        String darkBoost = mActivity.getString(R.string.pref_camera_tone_mapping_value_dark_boost_offset);
        String fourthTone = mActivity.getString(R.string.pref_camera_tone_mapping_value_fourth_tone_anchor);
        String userSetting = mActivity.getString(R.string.pref_camera_tone_mapping_value_user_setting);

        float currentDarkBoostValue = -1.0f;
        float currentFourthToneValue = -1.0f;
        if (mode.equals(darkBoost)) {
            currentDarkBoostValue = pref.getFloat(SettingsManager.KEY_TONE_MAPPING_DARK_BOOST, -1.0f);
            VendorTagUtil.setToneMappingDarkBoostValue(request, currentDarkBoostValue);
        } else if (mode.equals(fourthTone)) {
            currentFourthToneValue = pref.getFloat(SettingsManager.KEY_TONE_MAPPING_FOURTH_TONE, -1.0f);
            VendorTagUtil.setToneMappingFourthToneValue(request, currentFourthToneValue);
        }else if (mode.equals(userSetting)){
            currentDarkBoostValue = pref.getFloat(SettingsManager.KEY_TONE_MAPPING_DARK_BOOST, -1.0f);
            VendorTagUtil.setToneMappingDarkBoostValue(request, currentDarkBoostValue);
            currentFourthToneValue = pref.getFloat(SettingsManager.KEY_TONE_MAPPING_FOURTH_TONE, -1.0f);
            VendorTagUtil.setToneMappingFourthToneValue(request, currentFourthToneValue);
        } else {
            VendorTagUtil.setToneMappingDisableMode(request);
        }
        Log.i(TAG,"applyToneMapping, mode:" + mode + ",currentDarkBoostValue:" + currentDarkBoostValue + ",currentFourthToneValue:" + currentFourthToneValue);
    }

    private void updateGraghViewVisibility(final int visibility) {
        mActivity.runOnUiThread(new Runnable() {
            public void run() {
                if(mGraphViewR != null) {
                    mGraphViewR.setVisibility(visibility);
                }
                if(mGraphViewGB != null) {
                    mGraphViewGB.setVisibility(visibility);
                }
                if(mGraphViewB != null) {
                    mGraphViewB.setVisibility(visibility);
                }
            }
        });
    }

    private void updateMFNRText() {
        boolean isMfnrEnable = isMFNREnabled();
        if (isMfnrEnable) {
            mMFNREnable = true;
            if (mMFNRDrawer != null) {
                mMFNRDrawer.setVisibility(View.VISIBLE);
                mMFNRDrawer.refleshMFNR();
            }
        } else {
            mMFNREnable = false;
            if (mMFNRDrawer != null) {
                mMFNRDrawer.setVisibility(View.INVISIBLE);
            }
        }
    }

    private void updateGraghView(){
        mActivity.runOnUiThread(new Runnable() {
            public void run() {
                if(mGraphViewR != null) {
                    mGraphViewR.PreviewChanged();
                }
                if(mGraphViewGB != null) {
                    mGraphViewGB.PreviewChanged();
                }
                if(mGraphViewB != null) {
                    mGraphViewB.PreviewChanged();
                }
            }
        });
    }

    // BG stats
    private void updateBGStatsVisibility(final int visibility) {
        mActivity.runOnUiThread(new Runnable() {
            public void run() {
                if(bgstats_view != null) {
                    bgstats_view.setVisibility(visibility);
                    mBgStatsLabel.setVisibility(visibility);
                }
            }
        });
    }

    private void updateBGStatsView(){
        mActivity.runOnUiThread(new Runnable() {
            public void run() {
                if(bgstats_view != null) {
                    bgstats_view.PreviewChanged();
                }
            }
        });
    }

    //BE stats
    private void updateBEStatsVisibility(final int visibility) {
        mActivity.runOnUiThread(new Runnable() {
            public void run() {
                if(bestats_view != null) {
                    bestats_view.setVisibility(visibility);
                    mBeStatsLabel.setVisibility(visibility);
                }
            }
        });
    }

    private void updateBEStatsView(){
        mActivity.runOnUiThread(new Runnable() {
            public void run() {
                if(bestats_view != null) {
                    bestats_view.PreviewChanged();
                }
            }
        });
    }

    private boolean applyPreferenceToPreview(int cameraId, String key, String value) {
        if (!checkSessionAndBuilder(mCaptureSession[cameraId], mPreviewRequestBuilder[cameraId])) {
            return false;
        }
        boolean updatePreview = false;
        switch (key) {
            case SettingsManager.KEY_WHITE_BALANCE:
                updatePreview = true;
                applyWhiteBalance(mPreviewRequestBuilder[cameraId]);
                break;
            case SettingsManager.KEY_COLOR_EFFECT:
                updatePreview = true;
                applyColorEffect(mPreviewRequestBuilder[cameraId]);
                break;
            case SettingsManager.KEY_SCENE_MODE:
                updatePreview = true;
                applySceneMode(mPreviewRequestBuilder[cameraId]);
                break;
            case SettingsManager.KEY_EXPOSURE:
                updatePreview = true;
                applyExposure(mPreviewRequestBuilder[cameraId]);
                break;
            case SettingsManager.KEY_ISO:
                updatePreview = true;
                applyIso(mPreviewRequestBuilder[cameraId]);
                break;
            case SettingsManager.KEY_FACE_DETECTION:
                updatePreview = true;
                applyFaceDetection(mPreviewRequestBuilder[cameraId]);
                break;
            case SettingsManager.KEY_FOCUS_DISTANCE:
                updatePreview = true;
                if (mUI.getCurrentProMode() == ProMode.MANUAL_MODE) {
                    applyFocusDistance(mPreviewRequestBuilder[cameraId],
                            String.valueOf(mSettingsManager.getCalculatedFocusDistance()));
                } else {
                    //set AF mode when manual mode is off
                    mPreviewRequestBuilder[cameraId].set(
                            CaptureRequest.CONTROL_AF_MODE, mControlAFMode);
                }
                break;
            case SettingsManager.KEY_ZOOM_LEVEL:
                updatePreview = false;
                if (mUI.getCurrentProMode() == ProMode.ZOOM_MODE) {
                    onZoomChanged(mSettingsManager.getCalculatedZoomValue());
                }
                break;
        }
        return updatePreview;
    }

    private void applyZoomAndUpdate(int id) {
        if (!checkSessionAndBuilder(mCaptureSession[id], mPreviewRequestBuilder[id])) {
            return;
        }
        if (mState[id] == STATE_PREVIEW) {
            cancelTouchFocus(id);
        }
        applyZoom(mPreviewRequestBuilder[id], id);
        try {
            if(id == MONO_ID && !canStartMonoPreview()) {
                mCaptureSession[id].capture(mPreviewRequestBuilder[id]
                        .build(), mCaptureCallback, mCameraHandler);
            } else {
                CameraCaptureSession session = mCaptureSession[id];
                if (session instanceof CameraConstrainedHighSpeedCaptureSession) {
                    List list = ((CameraConstrainedHighSpeedCaptureSession)session)
                            .createHighSpeedRequestList(mPreviewRequestBuilder[id].build());
                    ((CameraConstrainedHighSpeedCaptureSession) session).setRepeatingBurst(list
                            , mCaptureCallback, mCameraHandler);
                } else if (isSSMEnabled()) {
                    session.setRepeatingBurst(createSSMBatchRequest(mPreviewRequestBuilder[id]),
                            mCaptureCallback, mCameraHandler);
                } else {
                    mCaptureSession[id].setRepeatingRequest(mPreviewRequestBuilder[id]
                            .build(), mCaptureCallback, mCameraHandler);
                }

            }
        } catch (CameraAccessException | IllegalStateException e) {
            e.printStackTrace();
        }
    }

    private void applyJpegQuality(CaptureRequest.Builder request) {
        String value = mSettingsManager.getValue(SettingsManager.KEY_JPEG_QUALITY);
        int jpegQuality = getQualityNumber(value);
        request.set(CaptureRequest.JPEG_QUALITY, (byte) jpegQuality);
    }

    private void applyAFRegions(CaptureRequest.Builder request, int id) {
        if (mControlAFMode == CaptureRequest.CONTROL_AF_MODE_AUTO) {
            request.set(CaptureRequest.CONTROL_AF_REGIONS, mAFRegions[id]);
        } else {
            request.set(CaptureRequest.CONTROL_AF_REGIONS, ZERO_WEIGHT_3A_REGION);
        }
    }

    private void applyAERegions(CaptureRequest.Builder request, int id) {
        if (mControlAFMode == CaptureRequest.CONTROL_AF_MODE_AUTO) {
            request.set(CaptureRequest.CONTROL_AE_REGIONS, mAERegions[id]);
        } else {
            request.set(CaptureRequest.CONTROL_AE_REGIONS, ZERO_WEIGHT_3A_REGION);
        }
    }

    private void applySceneMode(CaptureRequest.Builder request) {
        String value = mSettingsManager.getValue(SettingsManager.KEY_SCENE_MODE);
        String autoHdr = mSettingsManager.getValue(SettingsManager.KEY_AUTO_HDR);
        if (value == null) return;
        int mode = Integer.parseInt(value);
        if (autoHdr != null && "enable".equals(autoHdr) && "0".equals(value)) {
                request.set(CaptureRequest.CONTROL_SCENE_MODE, CaptureRequest.CONTROL_SCENE_MODE_HDR);
                request.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_USE_SCENE_MODE);
        }
        if(getPostProcFilterId(mode) != PostProcessor.FILTER_NONE || mCaptureHDRTestEnable) {
            request.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_USE_SCENE_MODE);
            request.set(CaptureRequest.CONTROL_SCENE_MODE, mode);
            return;
        }
        if (mode != CaptureRequest.CONTROL_SCENE_MODE_DISABLED
                && mode != SettingsManager.SCENE_MODE_DUAL_INT
                && mode != SettingsManager.SCENE_MODE_PROMODE_INT) {
            request.set(CaptureRequest.CONTROL_SCENE_MODE, mode);
            request.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_USE_SCENE_MODE);
            if (mode == CaptureRequest.CONTROL_SCENE_MODE_HDR) {
                try {
                    request.set(custom_noise_reduction, (byte)0x01);
                } catch (IllegalArgumentException e) {
                    Log.w(TAG, " HDR can`t find vendor tag: " + custom_noise_reduction.toString());
                }
            }
        } else {
            request.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
        }
    }

    private void applyExposure(CaptureRequest.Builder request) {
        String value = mSettingsManager.getValue(SettingsManager.KEY_EXPOSURE);
        if (value == null) return;
        int intValue = Integer.parseInt(value);
        request.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, intValue);
    }

    private void applyIso(CaptureRequest.Builder request) {
        String value = mSettingsManager.getValue(SettingsManager.KEY_ISO);
        if (applyManualIsoExposure(request)) return;
        if (value == null) return;
        boolean promode = mCurrentSceneMode.mode == CameraMode.PRO_MODE;
        if (!promode || value.equals("auto")) {
            VendorTagUtil.setIsoExpPrioritySelectPriority(request, 0);
            VendorTagUtil.setIsoExpPriority(request, 0L);
            if (request.get(CaptureRequest.SENSOR_EXPOSURE_TIME) == null) {
                request.set(CaptureRequest.SENSOR_EXPOSURE_TIME, mIsoExposureTime);
            }
            if (request.get(CaptureRequest.SENSOR_SENSITIVITY) == null) {
                request.set(CaptureRequest.SENSOR_SENSITIVITY, mIsoSensitivity);
            }
        } else {
            long intValue = SettingsManager.KEY_ISO_INDEX.get(value);
            VendorTagUtil.setIsoExpPrioritySelectPriority(request, 0);
            VendorTagUtil.setIsoExpPriority(request, intValue);
            if (request.get(CaptureRequest.SENSOR_EXPOSURE_TIME) != null) {
                mIsoExposureTime = request.get(CaptureRequest.SENSOR_EXPOSURE_TIME);
            }
            if (request.get(CaptureRequest.SENSOR_SENSITIVITY) != null) {
                mIsoSensitivity = request.get(CaptureRequest.SENSOR_SENSITIVITY);
            }
            request.set(CaptureRequest.SENSOR_EXPOSURE_TIME, null);
            request.set(CaptureRequest.SENSOR_SENSITIVITY, null);
        }

    }

    private boolean applyManualIsoExposure(CaptureRequest.Builder request) {
        boolean result = false;
        final SharedPreferences pref = mActivity.getSharedPreferences(
                ComboPreferences.getLocalSharedPreferencesName(mActivity,
                        mSettingsManager.getCurrentPrepNameKey()), Context.MODE_PRIVATE);
        String isoPriority = mActivity.getString(
                R.string.pref_camera_manual_exp_value_ISO_priority);
        String expTimePriority = mActivity.getString(
                R.string.pref_camera_manual_exp_value_exptime_priority);
        String userSetting = mActivity.getString(
                R.string.pref_camera_manual_exp_value_user_setting);
        String gainsPriority = mActivity.getString(
                R.string.pref_camera_manual_exp_value_gains_priority);
        String manualExposureMode = mSettingsManager.getValue(SettingsManager.KEY_MANUAL_EXPOSURE);
        if (manualExposureMode == null) return result;
        if (manualExposureMode.equals(isoPriority)) {
            int isoValue = Integer.parseInt(pref.getString(SettingsManager.KEY_MANUAL_ISO_VALUE,
                    "100"));
            VendorTagUtil.setIsoExpPrioritySelectPriority(request, 0);
            long intValue = SettingsManager.KEY_ISO_INDEX.get(
                    SettingsManager.MAUNAL_ABSOLUTE_ISO_VALUE);
            VendorTagUtil.setIsoExpPriority(request, intValue);
            VendorTagUtil.setUseIsoValues(request, isoValue);
            if (DEBUG) {
                Log.v(TAG, "manual ISO value :" + isoValue);
            }
            if (request.get(CaptureRequest.SENSOR_EXPOSURE_TIME) != null) {
                mIsoExposureTime = request.get(CaptureRequest.SENSOR_EXPOSURE_TIME);
            }
            if (request.get(CaptureRequest.SENSOR_SENSITIVITY) != null) {
                mIsoSensitivity = request.get(CaptureRequest.SENSOR_SENSITIVITY);
            }
            request.set(CaptureRequest.SENSOR_EXPOSURE_TIME, null);
            request.set(CaptureRequest.SENSOR_SENSITIVITY, null);
            result = true;
        } else if (manualExposureMode.equals(expTimePriority)) {
            long newExpTime = -1;
            String expTime = pref.getString(SettingsManager.KEY_MANUAL_EXPOSURE_VALUE, "0");
            try {
                newExpTime = Long.parseLong(expTime);
            } catch (NumberFormatException e) {
                Log.w(TAG, "Input expTime " + expTime + " is invalid");
                newExpTime = Long.parseLong(expTime);
            }

            if (DEBUG) {
                Log.v(TAG, "manual Exposure value :" + newExpTime);
            }
            VendorTagUtil.setIsoExpPrioritySelectPriority(request, 1);
            VendorTagUtil.setIsoExpPriority(request, newExpTime);
            request.set(CaptureRequest.SENSOR_SENSITIVITY, null);
            result = true;
        } else if (manualExposureMode.equals(userSetting)) {
            mSettingsManager.setValue(SettingsManager.KEY_FLASH_MODE, "off");
            request.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF);
            request.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
            int isoValue = Integer.parseInt(pref.getString(SettingsManager.KEY_MANUAL_ISO_VALUE,
                    "100"));
            long newExpTime = -1;
            String expTime = pref.getString(SettingsManager.KEY_MANUAL_EXPOSURE_VALUE, "0");
            try {
                newExpTime = Long.parseLong(expTime);
            } catch (NumberFormatException e) {
                Log.w(TAG, "Input expTime " + expTime + " is invalid");
                newExpTime = Long.parseLong(expTime);
            }
            if (DEBUG) {
                Log.v(TAG, "manual ISO value : " + isoValue + ", Exposure value :" + newExpTime);
            }
            request.set(CaptureRequest.SENSOR_EXPOSURE_TIME, newExpTime);
            request.set(CaptureRequest.SENSOR_SENSITIVITY, isoValue);
            result = true;
        } else if (manualExposureMode.equals(gainsPriority)) {
            float gains = pref.getFloat(SettingsManager.KEY_MANUAL_GAINS_VALUE, 1.0f);
            int[] isoRange = mSettingsManager.getIsoRangeValues(getMainCameraId());
            VendorTagUtil.setIsoExpPrioritySelectPriority(request, 0);
            int isoValue = 100;
            if (isoRange!= null) {
                isoValue  = (int) (gains * isoRange[0]);
            }
            long intValue = SettingsManager.KEY_ISO_INDEX.get(
                    SettingsManager.MAUNAL_ABSOLUTE_ISO_VALUE);
            VendorTagUtil.setIsoExpPriority(request, intValue);
            VendorTagUtil.setUseIsoValues(request, isoValue);
            if (DEBUG) {
                Log.v(TAG, "manual Gain value :" + isoValue);
            }
            if (request.get(CaptureRequest.SENSOR_EXPOSURE_TIME) != null) {
                mIsoExposureTime = request.get(CaptureRequest.SENSOR_EXPOSURE_TIME);
            }
            if (request.get(CaptureRequest.SENSOR_SENSITIVITY) != null) {
                mIsoSensitivity = request.get(CaptureRequest.SENSOR_SENSITIVITY);
            }
            request.set(CaptureRequest.SENSOR_EXPOSURE_TIME, null);
            request.set(CaptureRequest.SENSOR_SENSITIVITY, null);
            result = true;
        }
        return result;
    }

    private boolean applyAWBCCTAndAgain(CaptureRequest.Builder request) {
        boolean result = false;
        final SharedPreferences pref = mActivity.getSharedPreferences(
                ComboPreferences.getLocalSharedPreferencesName(mActivity,
                        mSettingsManager.getCurrentPrepNameKey()), Context.MODE_PRIVATE);
        float awbDefault = -1f;
        float rGain = pref.getFloat(SettingsManager.KEY_AWB_RAGIN_VALUE, awbDefault);
        float gGain = pref.getFloat(SettingsManager.KEY_AWB_GAGIN_VALUE, awbDefault);
        float bGain = pref.getFloat(SettingsManager.KEY_AWB_BAGIN_VALUE, awbDefault);
        float cct = pref.getFloat(SettingsManager.KEY_AWB_CCT_VALUE, awbDefault);
        float tc0 = pref.getFloat(SettingsManager.KEY_AWB_DECISION_AFTER_TC_0, awbDefault);
        float tc1 = pref.getFloat(SettingsManager.KEY_AWB_DECISION_AFTER_TC_1, awbDefault);
        float aec0 = pref.getFloat(SettingsManager.KEY_AEC_SENSITIVITY_0, awbDefault);
        float aec1 = pref.getFloat(SettingsManager.KEY_AEC_SENSITIVITY_1, awbDefault);
        float aec2 = pref.getFloat(SettingsManager.KEY_AEC_SENSITIVITY_2, awbDefault);
        if (rGain != awbDefault && gGain != awbDefault && gGain != bGain) {
            Float[] awbGains = {rGain, gGain, bGain};
            Float[] tcs = {tc0, tc1};
            try {
                request.set(CaptureModule.awbWarmStart_gain, awbGains);
                if (cct != awbDefault) {
                    request.set(CaptureModule.awbWarmStart_cct, cct);
                }
                request.set(CaptureModule.awbWarmStart_decision_after_tc, tcs);
                result = true;
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
            }
        }
        if (aec0 != awbDefault && aec1 != awbDefault && aec2 != awbDefault) {
            Float[] aecGains = {aec0, aec1, aec2};
            try {
                request.set(CaptureModule.aec_start_up_sensitivity, aecGains);
                result = true;
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
            }
        } else {
            Log.v(TAG, " applyAWBCCTAndAgain aec0 :" + aec0 + " " + aec1 + " " + aec2);
        }
        return result;
    }

    private boolean updateAWBCCTAndgains(CaptureResult captureResult) {
        boolean result = false;
        if (captureResult != null) {
            try {
                if (mExistAWBVendorTag) {
                    mRGain = captureResult.get(CaptureModule.awbFrame_control_rgain);
                    mGGain = captureResult.get(CaptureModule.awbFrame_control_ggain);
                    mBGain = captureResult.get(CaptureModule.awbFrame_control_bgain);
                    mCctAWB = captureResult.get(CaptureModule.awbFrame_control_cct);
                    mAWBDecisionAfterTC = captureResult.get(CaptureModule.awbFrame_decision_after_tc);
                }
                if (mExistAECWarmTag) {
                    mAECSensitivity = captureResult.get(CaptureModule.aec_sensitivity);
                }
                result = true;
            } catch (IllegalArgumentException e) {
                mExistAWBVendorTag = false;
                mExistAECWarmTag = false;
                e.printStackTrace();
            } catch(NullPointerException e) {
            }
        }
        return result;
    }

    private boolean updateAECGainAndExposure(CaptureResult captureResult) {
        boolean result = false;
        if (captureResult != null) {
            try {
                if (mExistAECFrameControlTag) {
                    mAecFramecontrolExosureTime = captureResult.get(aec_frame_control_exposure_time);
                    mAecFramecontrolLinearGain = captureResult.get(aec_frame_control_linear_gain);
                    mAecFramecontrolSensitivity = captureResult.get(aec_frame_control_sensitivity);
                    mAecFramecontrolLuxIndex = captureResult.get(aec_frame_control_lux_index);
                }
                result = true;
            } catch (IllegalArgumentException|NullPointerException e) {
                mExistAECFrameControlTag = false;
                e.printStackTrace();
            }
        }
        return result;
    }

    public void writeXMLForWarmAwb() {
        final SharedPreferences pref = mActivity.getSharedPreferences(
                ComboPreferences.getLocalSharedPreferencesName(mActivity,
                        mSettingsManager.getCurrentPrepNameKey()), Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = pref.edit();
        editor.putFloat(SettingsManager.KEY_AWB_RAGIN_VALUE, mRGain);
        editor.putFloat(SettingsManager.KEY_AWB_GAGIN_VALUE, mGGain);
        editor.putFloat(SettingsManager.KEY_AWB_BAGIN_VALUE, mBGain);
        editor.putFloat(SettingsManager.KEY_AWB_CCT_VALUE, mCctAWB);
        editor.putFloat(SettingsManager.KEY_AWB_DECISION_AFTER_TC_0, mAWBDecisionAfterTC[0]);
        editor.putFloat(SettingsManager.KEY_AWB_DECISION_AFTER_TC_1, mAWBDecisionAfterTC[1]);
        if (mAECSensitivity.length == 3) {
            editor.putFloat(SettingsManager.KEY_AEC_SENSITIVITY_0, mAECSensitivity[0]);
            editor.putFloat(SettingsManager.KEY_AEC_SENSITIVITY_1, mAECSensitivity[1]);
            editor.putFloat(SettingsManager.KEY_AEC_SENSITIVITY_2, mAECSensitivity[2]);
        }
        editor.apply();
    }

    private void applyColorEffect(CaptureRequest.Builder request) {
        String value = mSettingsManager.getValue(SettingsManager.KEY_COLOR_EFFECT);
        if (value == null) return;
        int mode = Integer.parseInt(value);
        request.set(CaptureRequest.CONTROL_EFFECT_MODE, mode);
    }

    private void applyWhiteBalance(CaptureRequest.Builder request) {
        String value = mSettingsManager.getValue(SettingsManager.KEY_WHITE_BALANCE);
        if (value == null) return;
        int mode = Integer.parseInt(value);
        request.set(CaptureRequest.CONTROL_AWB_MODE, mode);
    }

    private void applySnapshotFlash(CaptureRequest.Builder request, String value) {
        if(DEBUG) Log.d(TAG, "applySnapshotFlash: " + value);
        String redeye = mSettingsManager.getValue(SettingsManager.KEY_REDEYE_REDUCTION);
        mIsAutoFlash = false;
        if (redeye != null && redeye.equals("on") && !mLongshotActive) {
            request.set(CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH_REDEYE);
        } else if (value != null) {
            setFlashMode(request, value);
        }
    }

    //response to switch flash mode options in UI, repeat request as soon as switching
    private void applyFlashForUIChange(CaptureRequest.Builder request, int id) {
        if (!checkSessionAndBuilder(mCaptureSession[id], request)) {
            return;
        }
        String redeye = mSettingsManager.getValue(SettingsManager.KEY_REDEYE_REDUCTION);
        if (redeye != null && redeye.equals("on") && !mLongshotActive) {
            Log.w(TAG, "redeye mode is on, can't set android.flash.mode");
            return;
        }
        if (!mSettingsManager.isFlashSupported(id)) {
            Log.w(TAG, "flash not supported, can't set android.flash.mode");
            return;
        }
        String value = mSettingsManager.getValue(mCurrentSceneMode.mode == CameraMode.PRO_MODE ?
                SettingsManager.KEY_VIDEO_FLASH_MODE : SettingsManager.KEY_FLASH_MODE);
        mIsAutoFlash = false;
        setFlashMode(request, value);
        try {
            mCaptureSession[id].setRepeatingRequest(request
                    .build(), mCaptureCallback, mCameraHandler);
        } catch (CameraAccessException e) {
            Log.e(TAG, "Camera Access Exception in applyFlashForUIChange, apply failed");
        }
    }

    private void setFlashMode(CaptureRequest.Builder request, String flashMode) {
        if (request == null || flashMode == null) return;
        boolean isCaptureBurst = isCaptureBrustMode();
        switch (flashMode) {
            case "on":
                if (isCaptureBurst) {
                    request.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
                    request.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH);
                } else {
                    request.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH);
                    request.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_SINGLE);
                }
                break;
            case "auto":
                mIsAutoFlash = true;
                if (isCaptureBurst) {
                    // When long shot is active, turn off the flash in auto mode
                    request.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
                    request.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
                } else {
                    request.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
                    request.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_SINGLE);
                }
                break;
            case "off":
                request.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
                request.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
                break;
        }
    }

    private void applyTouchTrackFocus(CaptureRequest.Builder request) {
        boolean t2tSupported = false;
        String value = mSettingsManager.getValue(SettingsManager.KEY_TOUCH_TRACK_FOCUS);
        if (value != null && value.equals("on")) {
            t2tSupported = true;
        } else {
            t2tSupported = false;
        }
        // set vendorTag according to mT2TSupported
        byte t2tValues =(byte)((t2tSupported) ? 0x01 : 0x00);
        try {
            request.set(CaptureModule.t2t_enable, t2tValues );
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "applyTouchTrackFocus hal no vendorTag : " + t2t_enable);
        }
    }

    private void applyFaceDetection(CaptureRequest.Builder request) {
        String value = mSettingsManager.getValue(SettingsManager.KEY_FACE_DETECTION);
        String mode = mSettingsManager.getValue(SettingsManager.KEY_FACE_DETECTION_MODE);
        String bsgc = mSettingsManager.getValue(SettingsManager.KEY_BSGC_DETECTION);
        String facialContour = mSettingsManager.getValue(SettingsManager.KEY_FACIAL_CONTOUR);
        if (value != null) {
            try {
                boolean FdEnable = value.equals("on");
                int modeValue = CaptureRequest.STATISTICS_FACE_DETECT_MODE_SIMPLE;
                if (FdEnable){
                    if (mode != null)
                        modeValue = Integer.valueOf(mode);
                } else {
                    modeValue = CaptureRequest.STATISTICS_FACE_DETECT_MODE_OFF;
                }

                request.set(CaptureRequest.STATISTICS_FACE_DETECT_MODE,
                        modeValue);

                if (bsgc != null) {
                    final byte bsgc_enable;
                    if (bsgc.equals("enable") && FdEnable) {
                        bsgc_enable = 1;
                        request.set(CaptureRequest.STATISTICS_FACE_DETECT_MODE,
                                CaptureRequest.STATISTICS_FACE_DETECT_MODE_FULL);
                    } else {
                        bsgc_enable = 0;
                    }
                    request.set(CaptureModule.smileEnable, bsgc_enable);
                    request.set(CaptureModule.gazeEnable, bsgc_enable);
                    request.set(CaptureModule.blinkEnable, bsgc_enable);
                }

                if (facialContour != null) {
                    final byte facialContour_enable;
                    if (facialContour.equals("enable") && FdEnable) {
                        facialContour_enable = 1;
                    } else {
                        facialContour_enable = 0;
                    }
                    request.set(CaptureModule.facialContourEnable, facialContour_enable);
                }
            } catch (IllegalArgumentException e) {
            }
        }
    }

    private void applyFlash(CaptureRequest.Builder request, int id) {
        if (mSettingsManager.isFlashSupported(id)) {
            String value = mSettingsManager.getValue(mCurrentSceneMode.mode == CameraMode.PRO_MODE ?
                    SettingsManager.KEY_VIDEO_FLASH_MODE : SettingsManager.KEY_FLASH_MODE);
            applySnapshotFlash(request, value);
        } else {
            request.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
        }
    }

    private void applyFlashForAFTrigger(CaptureRequest.Builder request, int id) {
        if (mSettingsManager.isFlashSupported(id)) {
            String value = mSettingsManager.getValue(mCurrentSceneMode.mode == CameraMode.PRO_MODE ?
                    SettingsManager.KEY_VIDEO_FLASH_MODE : SettingsManager.KEY_FLASH_MODE);
            String redeye = mSettingsManager.getValue(SettingsManager.KEY_REDEYE_REDUCTION);
            mIsAutoFlash = false;
            if (redeye != null && redeye.equals("on") && !mLongshotActive) {
                request.set(CaptureRequest.CONTROL_AE_MODE,
                        CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH_REDEYE);
            } else if (value != null) {
                switch (value) {
                    case "on":
                        Log.d(TAG, "applyFlashForAFTrigger change AE and FLASH mode" );
                        request.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH);
                        request.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_SINGLE);
                        break;
                }
            }
        } else {
            request.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
        }
    }

    private void addPreviewSurface(CaptureRequest.Builder builder, List<Surface> surfaceList, int id) {
        if (isBackCamera() && getCameraMode() == DUAL_MODE && id == MONO_ID) {
            if(surfaceList != null) {
                surfaceList.add(mUI.getMonoDummySurface());
            }
            builder.addTarget(mUI.getMonoDummySurface());
            return;
        } else {
            List<Surface> surfaces = mFrameProcessor.getInputSurfaces();
            for(Surface surface : surfaces) {
                if(surfaceList != null) {
                    surfaceList.add(surface);
                }
                builder.addTarget(surface);
            }
            return;
        }
    }

    private void checkAndPlayRecordSound(int id, boolean isStarted) {
        if (!mIsRecordingVideo) {
            return;
        }
        if (id == getMainCameraId()) {
            String value = mSettingsManager.getValue(SettingsManager.KEY_SHUTTER_SOUND);
            if (value != null && value.equals("on") && mSoundPlayer != null) {
                mSoundPlayer.play(isStarted? SoundClips.START_VIDEO_RECORDING
                        : SoundClips.STOP_VIDEO_RECORDING);
            }
        }
    }

    public void checkAndPlayShutterSound(int id) {
        if (id == getMainCameraId()) {
            String value = mSettingsManager.getValue(SettingsManager.KEY_SHUTTER_SOUND);
            if (value != null && value.equals("on") && mSoundPlayer != null) {
                mSoundPlayer.play(SoundClips.SHUTTER_CLICK);
            }
        }
    }

    public Surface getPreviewSurfaceForSession(int id) {
        if (isBackCamera()) {
            if (getCameraMode() == DUAL_MODE && id == MONO_ID) {
                return mUI.getMonoDummySurface();
            } else {
                return mUI.getSurfaceHolder().getSurface();
            }
        } else {
            return mUI.getSurfaceHolder().getSurface();
        }
    }

    @Override
    public void onQueueStatus(final boolean full) {
        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mUI.enableShutter(!full);
            }
        });
    }

    public void triggerTouchFocus(int x, int y, int t2tTrigger) {
        int id = getMainCameraId();
        int[] registerRect = new int[4];
        int[] newXY = {x, y};
        if (mUI.isOverControlRegion(newXY)) return;
        if (!mUI.isOverSurfaceView(newXY)) return;
        x = newXY[0];
        y = newXY[1];
        if (DEBUG) {
            Log.d(TAG, "triggerTouchFocus, after trim: x:" + x + " y:" + y
                    + ", t2tTrigger :" + t2tTrigger);
        }
        transformTouchCoords(x, y, id);
        try {
            if (mPreviewRequestBuilder[id] != null) {
                mPreviewRequestBuilder[id].setTag(id);
                registerRect[0] = mT2TrackRegions[id][0].getX();
                registerRect[1] = mT2TrackRegions[id][0].getY();
                registerRect[2] = mT2TrackRegions[id][0].getWidth();
                registerRect[3] = mT2TrackRegions[id][0].getHeight();
                mPreviewRequestBuilder[id].set(CaptureModule.t2t_register_roi, registerRect);
                mPreviewRequestBuilder[id].set(CaptureModule.t2t_cmd_trigger, t2tTrigger);
                if (mCurrentSceneMode.mode == CameraMode.HFR && mCurrentSession != null &&
                        mCurrentSession instanceof CameraConstrainedHighSpeedCaptureSession) {
                    if (mCurrentSession != null) {
                        List requestList = ((CameraConstrainedHighSpeedCaptureSession)mCurrentSession)
                                .createHighSpeedRequestList(mPreviewRequestBuilder[id].build());
                        mCurrentSession.setRepeatingBurst(requestList, mCaptureCallback,
                                mCameraHandler);
                    }
                } else {
                    if (mCurrentSession != null) {
                        mCurrentSession.setRepeatingRequest(mPreviewRequestBuilder[id].build(),
                                mCaptureCallback, mCameraHandler);
                    }
                }
                if (DEBUG) {
                    Log.v(TAG, "triggerTouchFocus is called. ROI " + registerRect[0] + " "
                            + registerRect[1] + " " + registerRect[2] + " " + registerRect[3]);
                }
            }
        } catch (CameraAccessException | IllegalStateException e) {
            e.printStackTrace();
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        }
    }

    private void transformTouchCoords(float x, float y, int id) {
        if (mCropRegion[id] == null) {
            Log.d(TAG, "transformTouchCoords crop region is null at " + id);
            mInTAF = false;
            return;
        }
        Point p = mUI.getSurfaceViewSize();
        int width = p.x;
        int height = p.y;
        if (DEBUG) {
            Log.d(TAG, "transformTouchCoords crop region w: " + mCropRegion[id].width() +
                    " h: " + mCropRegion[id].height());
            Log.d(TAG, "transformTouchCoords surfaceViewP w " + p.x + " h: " + p.y);
        }
        if (width * mCropRegion[id].width() != height * mCropRegion[id].height()) {
            Point displayPoint = mUI.getDisplaySize();
            if (width > displayPoint.x) {
                height = width * mCropRegion[id].width() / mCropRegion[id].height();
            }
            if (height > displayPoint.y) {
                width = height * mCropRegion[id].height() / mCropRegion[id].width();
            }
        }
        x += (width - p.x) / 2;
        y += (height - p.y) / 2;
        mT2TrackRegions[id] = afaeRectangle(x, y, width, height, 1.25f, mCropRegion[id], id);
        if (DEBUG) {
            Log.d(TAG, "transformTouchCoords " + mT2TrackRegions[id][0].getX() + " " +
                    mT2TrackRegions[id][0].getY() + " " + mT2TrackRegions[id][0].getWidth() +
                    " " + mT2TrackRegions[id][0].getHeight());
        }
    }

    public void triggerFocusAtPoint(float x, float y, int id) {
        if (DEBUG) {
            Log.d(TAG, "triggerFocusAtPoint " + x + " " + y + " " + id);
        }
        if (mCropRegion[id] == null || mOriginalCropRegion[id] == null) {
            Log.d(TAG, "crop region is null at " + id);
            mInTAF = false;
            return;
        }
        Point p = mUI.getSurfaceViewSize();
        int width = p.x;
        int height = p.y;
        if (width * mCropRegion[id].width() != height * mCropRegion[id].height()) {
            Point displayPoint = mUI.getDisplaySize();
            if (width >= displayPoint.x) {
                height = width * mCropRegion[id].width() / mCropRegion[id].height();
            }
            if (height >= displayPoint.y) {
                width = height * mCropRegion[id].height() / mCropRegion[id].width();
            }
        }
        x += (width - p.x) / 2;
        y += (height - p.y) / 2;
        mAFRegions[id] = afaeRectangle(x, y, width, height, 1f, mCropRegion[id], id);
        mAERegions[id] = afaeRectangle(x, y, width, height, 1.5f, mCropRegion[id], id);
        mCameraHandler.removeMessages(CANCEL_TOUCH_FOCUS, mCameraId[id]);
        autoFocusTrigger(id);
    }

    private void cancelTouchFocus(int id) {
        if(mPaused)
            return;

        if (DEBUG) {
            Log.v(TAG, "cancelTouchFocus " + id);
        }
        mInTAF = false;
        mState[id] = STATE_PREVIEW;
        if(mLockAFAE){
            return;
        }
        mControlAFMode = mCurrentSceneMode.mode == CameraMode.VIDEO ?
                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO :
                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE;
        mIsAutoFocusStarted = false;
        setAFModeToPreview(id, (mSettingsManager.isDeveloperEnabled() && getDevAfMode() != -1) ?
                getDevAfMode() : mControlAFMode);
    }

    private MeteringRectangle[] afaeRectangle(float x, float y, int width, int height,
                                              float multiple, Rect cropRegion, int id) {
        int side = (int) (Math.max(width, height) / 8 * multiple);
        RectF meteringRegionF = new RectF(x - side / 2, y - side / 2, x + side / 2, y + side / 2);

        // inverse of matrix1 will translate from touch to (-1000 to 1000), which is camera1
        // coordinates, while accounting for orientation and mirror
        Matrix matrix1 = new Matrix();
        CameraUtil.prepareMatrix(matrix1, !isBackCamera(), mDisplayOrientation, width, height);
        matrix1.invert(matrix1);

        // inverse of matrix2 will translate from (-1000 to 1000) to camera 2 coordinates
        Matrix matrix2 = new Matrix();
        matrix2.preTranslate(-mOriginalCropRegion[id].width() / 2f,
                -mOriginalCropRegion[id].height() / 2f);
        matrix2.postScale(2000f / mOriginalCropRegion[id].width(),
                2000f / mOriginalCropRegion[id].height());
        matrix2.invert(matrix2);

        matrix1.mapRect(meteringRegionF);
        matrix2.mapRect(meteringRegionF);

        meteringRegionF.left = meteringRegionF.left * cropRegion.width()
                / mOriginalCropRegion[id].width() + cropRegion.left;
        meteringRegionF.top = meteringRegionF.top * cropRegion.height()
                / mOriginalCropRegion[id].height() + cropRegion.top;
        meteringRegionF.right = meteringRegionF.right * cropRegion.width()
                / mOriginalCropRegion[id].width() + cropRegion.left;
        meteringRegionF.bottom = meteringRegionF.bottom * cropRegion.height()
                / mOriginalCropRegion[id].height() + cropRegion.top;

        Rect meteringRegion = new Rect((int) meteringRegionF.left, (int) meteringRegionF.top,
                (int) meteringRegionF.right, (int) meteringRegionF.bottom);

        meteringRegion.left = CameraUtil.clamp(meteringRegion.left, cropRegion.left,
                cropRegion.right);
        meteringRegion.top = CameraUtil.clamp(meteringRegion.top, cropRegion.top,
                cropRegion.bottom);
        meteringRegion.right = CameraUtil.clamp(meteringRegion.right, cropRegion.left,
                cropRegion.right);
        meteringRegion.bottom = CameraUtil.clamp(meteringRegion.bottom, cropRegion.top,
                cropRegion.bottom);

        MeteringRectangle[] meteringRectangle = new MeteringRectangle[1];
        meteringRectangle[0] = new MeteringRectangle(meteringRegion, 1);
        return meteringRectangle;
    }

    private void updateFocusStateChange(CaptureResult result) {
        Integer resultAFState = result.get(CaptureResult.CONTROL_AF_STATE);
        if (DEBUG) Log.i(TAG,"updateFocusStateChange, resultAFState:" + resultAFState);
        if (resultAFState == null) return;
        try {
            Byte isDepthFocus = result.get(CaptureModule.is_depth_focus);
            if (isDepthFocus != null) {
                if (isDepthFocus == 1) {
                    mIsDepthFocus = true;
                } else {
                    mIsDepthFocus = false;
                }
            }
            if (DEBUG) Log.d(TAG, "isDepthFocus is " + mIsDepthFocus + ", inTAF is " + mInTAF);
        } catch (IllegalArgumentException e) {
            mIsDepthFocus = false;
            if (DEBUG) e.printStackTrace();
        }

        if(resultAFState == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED && mLockAFAE){
            updateLockAFAEVisibility();
        }
        if (mIsDepthFocus && !mInTAF) {
            resultAFState = CaptureResult.CONTROL_AF_STATE_INACTIVE;
        }
        final Integer afState = resultAFState;
        // Report state change when AF state has changed.
        if (resultAFState != mLastResultAFState && mFocusStateListener != null) {
            mActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mFocusStateListener.onFocusStatusUpdate(afState);
                }
            });
        }
        mLastResultAFState = resultAFState;
    }

    private void setDisplayOrientation() {
        mDisplayRotation = CameraUtil.getDisplayRotation(mActivity);
        mDisplayOrientation = CameraUtil.getDisplayOrientationForCamera2(
                mDisplayRotation, getMainCameraId());
    }

    @Override
    public void onSettingsChanged(List<SettingsManager.SettingState> settings) {
        if (mPaused) return;
        boolean updatePreviewBayer = false;
        boolean updatePreviewMono = false;
        boolean updatePreviewFront = false;
        boolean updatePreviewLogical = false;
        int count = 0;
        for (SettingsManager.SettingState settingState : settings) {
            String key = settingState.key;
            SettingsManager.Values values = settingState.values;
            String value;
            if (values.overriddenValue != null) {
                value = values.overriddenValue;
            } else {
                value = values.value;
            }
            Log.d(TAG, "CaptureModule onSettingsChanged, key = " + key);
            switch (key) {
                case SettingsManager.KEY_CAMERA_SAVEPATH:
                    Storage.setSaveSDCard(value.equals("1"));
                    mActivity.updateStorageSpaceAndHint();
                    continue;
                case SettingsManager.KEY_JPEG_QUALITY:
                    estimateJpegFileSize();
                    continue;
                case SettingsManager.KEY_VIDEO_DURATION:
                    updateMaxVideoDuration();
                    continue;
                case SettingsManager.KEY_VIDEO_QUALITY:
                    updateVideoSize();
                    continue;
                case SettingsManager.KEY_VIDEO_TIME_LAPSE_FRAME_INTERVAL:
                    updateTimeLapseSetting();
                    continue;
                case SettingsManager.KEY_FACE_DETECTION:
                    updateFaceDetection();
                    break;
                case SettingsManager.KEY_MONO_ONLY:
                case SettingsManager.KEY_CLEARSIGHT:
                case SettingsManager.KEY_MONO_PREVIEW:
                case SettingsManager.KEY_FRONT_REAR_SWITCHER_VALUE:
                case SettingsManager.KEY_FORCE_AUX:
                    if (count == 0) restartAll();
                    return;
                case SettingsManager.KEY_VIDEO_FLASH_MODE:
                    switch (mCurrentSceneMode.mode) {
                        case PRO_MODE:
                            applyFlashForUIChange(mPreviewRequestBuilder[getMainCameraId()],
                                    getMainCameraId());
                            break;
                        case VIDEO:
                        case HFR:
                            updateVideoFlash(getMainCameraId());
                            break;
                    }
                    return;
                case SettingsManager.KEY_FLASH_MODE:
                    applyFlashForUIChange(mPreviewRequestBuilder[getMainCameraId()],
                            getMainCameraId());
                    return;
                case SettingsManager.KEY_ZSL:
                case SettingsManager.KEY_AUTO_HDR:
                case SettingsManager.KEY_SAVERAW:
                case SettingsManager.KEY_HDR:
                    if (count == 0) restartSession(false);
                    return;
                case SettingsManager.KEY_SCENE_MODE:
                    restartAll();
                    return;
            }
            updatePreviewLogical |= applyPreferenceToPreview(mCurrentSceneMode.getCurrentId(),
                    key, value);
            count++;
        }
        if (updatePreviewBayer) {
            try {
                if (checkSessionAndBuilder(mCaptureSession[BAYER_ID],
                        mPreviewRequestBuilder[BAYER_ID])) {
                    if (mIsRecordingVideo && mHighSpeedCapture) {
                        List requestList =  ((CameraConstrainedHighSpeedCaptureSession)mCaptureSession[BAYER_ID])
                                .createHighSpeedRequestList(mPreviewRequestBuilder[BAYER_ID].build());
                        mCaptureSession[BAYER_ID].setRepeatingBurst(requestList, mCaptureCallback,
                                mCameraHandler);
                    } else {
                        mCaptureSession[BAYER_ID].setRepeatingRequest(mPreviewRequestBuilder[BAYER_ID]
                                .build(), mCaptureCallback, mCameraHandler);
                    }
                }
            } catch (CameraAccessException | IllegalStateException e) {
                e.printStackTrace();
            }
        }
        if (updatePreviewMono) {
            try {
                if (checkSessionAndBuilder(mCaptureSession[MONO_ID],
                        mPreviewRequestBuilder[MONO_ID])) {
                    if (canStartMonoPreview()) {
                        mCaptureSession[MONO_ID].setRepeatingRequest(mPreviewRequestBuilder[MONO_ID]
                                .build(), mCaptureCallback, mCameraHandler);
                    } else {
                        mCaptureSession[MONO_ID].capture(mPreviewRequestBuilder[MONO_ID]
                                .build(), mCaptureCallback, mCameraHandler);
                    }
                }
            } catch (CameraAccessException | IllegalStateException e) {
                e.printStackTrace();
            }
        }
        if (updatePreviewFront) {
            try {
                if (checkSessionAndBuilder(mCaptureSession[FRONT_ID],
                        mPreviewRequestBuilder[FRONT_ID])) {
                    mCaptureSession[FRONT_ID].setRepeatingRequest(mPreviewRequestBuilder[FRONT_ID]
                            .build(), mCaptureCallback, mCameraHandler);
                }
            } catch (CameraAccessException | IllegalStateException e) {
                e.printStackTrace();
            }
        }

        if (updatePreviewLogical) {
            try {
                int cameraId = mCurrentSceneMode.getCurrentId();
                if (checkSessionAndBuilder(mCaptureSession[cameraId],
                        mPreviewRequestBuilder[cameraId])) {
                    if (mCaptureSession[cameraId] instanceof CameraConstrainedHighSpeedCaptureSession) {
                        List<CaptureRequest> list = ((CameraConstrainedHighSpeedCaptureSession)
                                mCaptureSession[cameraId]).createHighSpeedRequestList(
                                        mPreviewRequestBuilder[cameraId].build());
                        mCaptureSession[cameraId].setRepeatingBurst(list, mCaptureCallback,
                                mCameraHandler);
                    } else if (isSSMEnabled()) {
                        mCaptureSession[cameraId].setRepeatingBurst(createSSMBatchRequest(
                                mPreviewRequestBuilder[cameraId]), mCaptureCallback, mCameraHandler);
                    } else {
                        mCaptureSession[cameraId].setRepeatingRequest(mPreviewRequestBuilder[cameraId]
                                .build(), mCaptureCallback, mCameraHandler);
                    }
                }
            } catch (CameraAccessException | IllegalStateException e) {
                e.printStackTrace();
            }
        }
    }

    private boolean isPanoSetting(String value) {
        try {
            int mode = Integer.parseInt(value);
            if(mode == SettingsManager.SCENE_MODE_PANORAMA_INT) {
                return true;
            }
        } catch(Exception e) {
        }
        return false;
    }

    private boolean isCaptureBrustMode() {
        boolean isCaptureBrustMode = false;
        String value = mSettingsManager.getValue(SettingsManager.KEY_SCENE_MODE);
        if (value != null) {
            int mode = Integer.parseInt(value);
            if(mode == SettingsManager.SCENE_MODE_NIGHT_INT ||
                    mode == SettingsManager.SCENE_MODE_SHARPSHOOTER_INT ||
                    mode == SettingsManager.SCENE_MODE_BLURBUSTER_INT ||
                    mode == SettingsManager.SCENE_MODE_BESTPICTURE_INT ||
                    mode == SettingsManager.SCENE_MODE_OPTIZOOM_INT ||
                    mode == SettingsManager.SCENE_MODE_UBIFOCUS_INT) {
                isCaptureBrustMode = true;
            }
        }

        return isCaptureBrustMode || mLongshotActive;
    }

    public boolean isDeepZoom() {
        String value = mSettingsManager.getValue(SettingsManager.KEY_SCENE_MODE);
        try {
            int mode = Integer.parseInt(value);
            if(mode == SettingsManager.SCENE_MODE_DEEPZOOM_INT) {
                return true;
            }
        } catch(Exception e) {
        }
        return false;
    }

    private void updateFaceDetection() {
        final String value = mSettingsManager.getValue(SettingsManager.KEY_FACE_DETECTION);
        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (value == null || value.equals("off")
                        || !mSettingsManager.isFDRenderingAtPreview())
                    mUI.onStopFaceDetection();
                else {
                    mUI.onStartFaceDetection(mDisplayOrientation,
                            mSettingsManager.isFacingFront(getMainCameraId()),
                            mCropRegion[getMainCameraId()],
                            mSettingsManager.getSensorActiveArraySize(getMainCameraId()));
                }
            }
        });
    }

    public void restartAll() {
        int nextCameraId = getNextScreneModeId(mNextModeIndex);
        Log.d(TAG, "restart all CURRENT_ID :" + CURRENT_ID + " nextCameraId :" + nextCameraId);
        if(CURRENT_ID == nextCameraId && mCameraDevice[nextCameraId] != null){
            mIsCloseCamera = false;
        }else{
            mIsCloseCamera = true;
        }
        if(mLockAFAE) {
            mLockAFAE = false;
            applySettingsForUnlockExposure(mPreviewRequestBuilder[CURRENT_ID], CURRENT_ID);
            updateLockAFAEVisibility();
        }
        onPauseBeforeSuper();
        if(!mIsCloseCamera){
            mUI.showPreviewCover();
        }
        onPauseAfterSuper(false);
        reinitSceneMode();
        onResumeBeforeSuper();
        onResumeAfterSuper(true);
        setRefocusLastTaken(false);
        mIsCloseCamera = true;
    }

    public void restartSession(boolean isSurfaceChanged) {
        Log.d(TAG, "restartSession isSurfaceChanged = " + isSurfaceChanged);
        if (isAllSessionClosed()) return;
        if(!mIsCloseCamera) {
            reinit();
        }
        closeProcessors();
        closeSessions();
        if(isSurfaceChanged) {
            //run in UI thread
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mUI.hideSurfaceView();
                    mUI.showSurfaceView();
                }
            });
        }
        if(!mIsCloseCamera) {
            updatePreviewSurfaceReadyState(false);
        }
        initializeValues();
        updatePreviewSize();
        openProcessors();
        createSessions();

        if(isTrackingFocusSettingOn()) {
            mUI.resetTrackingFocus();
        }
        if (isT2TFocusSettingOn()) {
            mUI.resetTouchTrackingFocus();
        }
        resetStateMachine();
    }

    private void resetStateMachine() {
        for (int i = 0; i < MAX_NUM_CAM; i++) {
            mState[i] = STATE_PREVIEW;
        }
        mUI.enableShutter(true);
    }

    private Size getOptimalPreviewSize(Size pictureSize, Size[] prevSizes) {
        Point[] points = new Point[prevSizes.length];

        double targetRatio = (double) pictureSize.getWidth() / pictureSize.getHeight();
        int index = 0;
        int point_max[]  = mSettingsManager.getMaxPreviewSize();
        int max_size = -1;
        if (point_max != null){
            max_size = point_max[0] * point_max[1];
        }
        for (Size s : prevSizes) {
            if (max_size != -1){
                int size = s.getWidth() * s.getHeight();
                if (s.getWidth() == s.getHeight()){
                    if (s.getWidth() > Math.max(point_max[0],point_max[1]))
                        continue;
                } else if (size > max_size || size == 0) {
                    continue;
                }
            }
            points[index++] = new Point(s.getWidth(), s.getHeight());
        }

        int optimalPickIndex = CameraUtil.getOptimalPreviewSize(mActivity, points, targetRatio);
        return (optimalPickIndex == -1) ? null :
                new Size(points[optimalPickIndex].x,points[optimalPickIndex].y);
    }

    private Size getOptimalVideoPreviewSize(Size VideoSize, Size[] prevSizes) {
        Point[] points = new Point[prevSizes.length];

        int index = 0;
        int point_max[]  = mSettingsManager.getMaxPreviewSize();
        int max_size = -1;
        if (point_max != null){
            max_size = point_max[0] * point_max[1];
        }
        for (Size s : prevSizes) {
            if (max_size != -1){
                int size = s.getWidth() * s.getHeight();
                if (s.getWidth() == s.getHeight()){
                    if (s.getWidth() > Math.max(point_max[0],point_max[1]))
                        continue;
                } else if (size > max_size || size == 0) {
                    continue;
                }
            }
            points[index++] = new Point(s.getWidth(), s.getHeight());
        }

        int optimalPickIndex = CameraUtil.getOptimalVideoPreviewSize(mActivity, points, VideoSize);
        return (optimalPickIndex == -1) ? null :
                new Size(points[optimalPickIndex].x,points[optimalPickIndex].y);
    }

    public TrackingFocusRenderer getTrackingForcusRenderer() {
        return mUI.getTrackingFocusRenderer();
    }

    public TouchTrackFocusRenderer getT2TFocusRenderer() {
        return mUI.getT2TFocusRenderer();
    }

    private class MyCameraHandler extends Handler {

        public MyCameraHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            int id = msg.arg1;
            switch (msg.what) {
                case OPEN_CAMERA:
                    openCamera(id);
                    break;
                case CANCEL_TOUCH_FOCUS:
                    cancelTouchFocus(id);
                    break;
            }
        }
    }

    private class MpoSaveHandler extends Handler {
        static final int MSG_CONFIGURE = 0;
        static final int MSG_NEW_IMG = 1;

        private Image monoImage;
        private Image bayerImage;
        private Long captureStartTime;

        public MpoSaveHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case MSG_CONFIGURE:
                captureStartTime = (Long) msg.obj;
                break;
            case MSG_NEW_IMG:
                processNewImage(msg);
                break;
            }
        }

        private void processNewImage(Message msg) {
            Log.d(TAG, "MpoSaveHandler:processNewImage for cam id: " + msg.arg1);
            if(msg.arg1 == MONO_ID) {
                monoImage = (Image)msg.obj;
            } else if(bayerImage == null){
                bayerImage = (Image)msg.obj;
            }

            if(monoImage != null && bayerImage != null) {
                saveMpoImage();
            }
        }

        private void saveMpoImage() {
            mNamedImages.nameNewImage(captureStartTime);
            NamedEntity namedEntity = mNamedImages.getNextNameEntity();
            String title = (namedEntity == null) ? null : namedEntity.title;
            long date = (namedEntity == null) ? -1 : namedEntity.date;
            int width = bayerImage.getWidth();
            int height = bayerImage.getHeight();
            byte[] bayerBytes = getJpegData(bayerImage);
            byte[] monoBytes = getJpegData(monoImage);

            ExifInterface exif = Exif.getExif(bayerBytes);
            int orientation = Exif.getOrientation(exif);

            mActivity.getMediaSaveService().addMpoImage(
                    null, bayerBytes, monoBytes, width, height, title,
                    date, null, orientation, mOnMediaSavedListener, mContentResolver, "jpeg");

            mActivity.updateThumbnail(bayerBytes);

            bayerImage.close();
            bayerImage = null;
            monoImage.close();
            monoImage = null;
            namedEntity = null;
        }
    }

    @Override
    public void onReleaseShutterLock() {
        Log.d(TAG, "onReleaseShutterLock");
        unlockFocus(BAYER_ID);
        unlockFocus(MONO_ID);
    }

    @Override
    public void onClearSightSuccess(byte[] thumbnailBytes) {
        Log.d(TAG, "onClearSightSuccess");
        onReleaseShutterLock();
        if(thumbnailBytes != null) mActivity.updateThumbnail(thumbnailBytes);
        warningToast(R.string.clearsight_capture_success, false);
    }

    @Override
    public void onClearSightFailure(byte[] thumbnailBytes) {
        Log.d(TAG, "onClearSightFailure");
        if(thumbnailBytes != null) mActivity.updateThumbnail(thumbnailBytes);
        warningToast(R.string.clearsight_capture_fail, false);
        onReleaseShutterLock();
    }

    /**
     * This Handler is used to post message back onto the main thread of the
     * application
     */
    private class MainHandler extends Handler {
        public MainHandler() {
            super(Looper.getMainLooper());
        }
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case CLEAR_SCREEN_DELAY: {
                    mActivity.getWindow().clearFlags(
                            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    break;
                }
                case UPDATE_RECORD_TIME: {
                    updateRecordingTime();
                    break;
                }
                case VOICE_INTERACTION_CAPTURE: {
                    if (mIntentMode == INTENT_MODE_STILL_IMAGE_CAMERA && mIsVoiceTakePhote) {
                        onShutterButtonClick();
                        mIsVoiceTakePhote = false;
                    }
                    break;
                }
            }
        }
    }

    @Override
    public void onErrorListener(int error) {
        enableRecordingLocation(false);
    }

    // from MediaRecorder.OnErrorListener
    @Override
    public void onError(MediaRecorder mr, int what, int extra) {
        Log.e(TAG, "MediaRecorder error. what=" + what + ". extra=" + extra);
        try {
            stopRecordingVideo(getMainCameraId());
        } catch (UnsupportedOperationException e) {
            Log.w(TAG, "stop recording failed: " + e);
        }
        mUI.showUIafterRecording();
        if (what == MediaRecorder.MEDIA_RECORDER_ERROR_UNKNOWN) {
            // We may have run out of space on the sdcard.
            mActivity.updateStorageSpaceAndHint();
        } else {
           warningToast("MediaRecorder error. what=" + what + ". extra=" + extra);
        }
    }

    // from MediaRecorder.OnInfoListener
    @Override
    public void onInfo(MediaRecorder mr, int what, int extra) {
        if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
            if (mIsRecordingVideo) {
                stopRecordingVideo(getMainCameraId());
            }
        } else if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED) {
           if (mIsRecordingVideo) {
               stopRecordingVideo(getMainCameraId());
           }
            // Show the toast.
            RotateTextToast.makeText(mActivity, R.string.video_reach_size_limit,
                    Toast.LENGTH_LONG).show();
        }
    }

    private byte[] getJpegData(Image image) {
        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        return bytes;
    }

    private void updateSaveStorageState() {
        Storage.setSaveSDCard(mSettingsManager.getValue(SettingsManager
                .KEY_CAMERA_SAVEPATH).equals("1"));
    }

    public void startPlayVideoActivity() {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(mCurrentVideoUri,
                CameraUtil.convertOutputFormatToMimeType(mProfile.fileFormat));
        try {
            mActivity
                    .startActivityForResult(intent, CameraActivity.REQ_CODE_DONT_SWITCH_TO_PREVIEW);
        } catch (ActivityNotFoundException ex) {
            Log.e(TAG, "Couldn't view video " + mCurrentVideoUri, ex);
        }
    }

    private void closeVideoFileDescriptor() {
        if (mVideoFileDescriptor != null) {
            try {
                mVideoFileDescriptor.close();
            } catch (IOException e) {
                Log.e(TAG, "Fail to close fd", e);
            }
            mVideoFileDescriptor = null;
        }
    }

    private Bitmap getVideoThumbnail() {
        Bitmap bitmap = null;
        if (mVideoFileDescriptor != null) {
            bitmap = Thumbnail.createVideoThumbnailBitmap(mVideoFileDescriptor.getFileDescriptor(),
                    mVideoPreviewSize.getWidth());
        } else if (mCurrentVideoUri != null) {
            try {
                mVideoFileDescriptor = mContentResolver.openFileDescriptor(mCurrentVideoUri, "r");
                bitmap = Thumbnail.createVideoThumbnailBitmap(
                        mVideoFileDescriptor.getFileDescriptor(), mVideoPreviewSize.getWidth());
            } catch (java.io.FileNotFoundException ex) {
                // invalid uri
                Log.e(TAG, ex.toString());
            }
        }

        if (bitmap != null) {
            // MetadataRetriever already rotates the thumbnail. We should rotate
            // it to match the UI orientation (and mirror if it is front-facing camera).
            boolean mirror = mPostProcessor.isSelfieMirrorOn();
            bitmap = CameraUtil.rotateAndMirror(bitmap, 0, mirror);
        }
        return bitmap;
    }

    private void deleteVideoFile(String fileName) {
        Log.v(TAG, "Deleting video " + fileName);
        File f = new File(fileName);
        if (!f.delete()) {
            Log.v(TAG, "Could not delete " + fileName);
        }
    }

    private void releaseMediaRecorder() {
        Log.v(TAG, "Releasing media recorder.");
        cleanupEmptyFile();
        if (mMediaRecorder != null) {
            try{
                mMediaRecorder.reset();
                mMediaRecorder.release();
            }catch (RuntimeException e) {
                e.printStackTrace();
            }
            mMediaRecorder = null;
        }
    }

    private void cleanupEmptyFile() {
        if (mVideoFilename != null) {
            File f = new File(mVideoFilename);
            if (f.length() == 0 && f.delete()) {
                Log.v(TAG, "Empty video file deleted: " + mVideoFilename);
                mVideoFilename = null;
            }
        }
    }

    private void showToast(String tips) {
        if (mToast == null) {
            mToast = Toast.makeText(mActivity, tips, Toast.LENGTH_LONG);
            mToast.setGravity(Gravity.CENTER, 0, 0);
        }
        mToast.setText(tips);
        mToast.show();
    }

    private boolean isRecorderReady() {
        if ((mStartRecPending == true || mStopRecPending == true))
            return false;
        else
            return true;
    }

    /*
     * Make sure we're not recording music playing in the background, ask the
     * MediaPlaybackService to pause playback.
     */
    private void requestAudioFocus() {
        AudioManager am = (AudioManager)mActivity.getSystemService(Context.AUDIO_SERVICE);
        // Send request to obtain audio focus. This will stop other
        // music stream.
        int result = am.requestAudioFocus(null, AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
        if (result == AudioManager.AUDIOFOCUS_REQUEST_FAILED) {
            Log.v(TAG, "Audio focus request failed");
        }
    }

    private void releaseAudioFocus() {
        AudioManager am = (AudioManager)mActivity.getSystemService(Context.AUDIO_SERVICE);
        int result = am.abandonAudioFocus(null);
        if (result == AudioManager.AUDIOFOCUS_REQUEST_FAILED) {
            Log.v(TAG, "Audio focus release failed");
        }
    }

    private boolean isVideoCaptureIntent() {
        String action = mActivity.getIntent().getAction();
        return (MediaStore.ACTION_VIDEO_CAPTURE.equals(action));
    }

    private void resetScreenOn() {
        mHandler.removeMessages(CLEAR_SCREEN_DELAY);
        mActivity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void keepScreenOnAwhile() {
        mHandler.removeMessages(CLEAR_SCREEN_DELAY);
        mActivity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        mHandler.sendEmptyMessageDelayed(CLEAR_SCREEN_DELAY, SCREEN_DELAY);
    }

    private void keepScreenOn() {
        mHandler.removeMessages(CLEAR_SCREEN_DELAY);
        mActivity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }


    private void setProModeVisible() {
        boolean promode = mCurrentSceneMode.mode == CameraMode.PRO_MODE;
        mUI.initializeProMode(!mPaused && promode);
    }

    boolean checkSessionAndBuilder(CameraCaptureSession session, CaptureRequest.Builder builder) {
        return session != null && builder != null;
    }

    public boolean isSSMEnabled() {
        return mSuperSlomoCapture && (int)mHighSpeedFPSRange.getUpper() > NORMAL_SESSION_MAX_FPS
                && (mCurrentSceneMode.mode == CameraMode.HFR) && PersistUtil.isSSMEnabled();
    }

    /**
     * if it is HFR or HSR recording and rate > 60
     * @return if it is high speed rate recording
     */
    private boolean isHighSpeedRateCapture() {
        return mHighSpeedCapture && (int)mHighSpeedFPSRange.getUpper() > NORMAL_SESSION_MAX_FPS;
    }

    public void onRenderComplete(DPImage dpimage, boolean isError) {
        dpimage.mImage.close();
        if(isError) {
            getGLCameraPreview().requestRender();
        }
    }

    public void onRenderSurfaceCreated() {
        updatePreviewSurfaceReadyState(true);
        mUI.initThumbnail();
        if (getFrameFilters().size() == 0) {
            Log.d(TAG,"DeepPortraitFilter is not in frame filter list.");
            return;
        }
        mRenderer = getGLCameraPreview().getRendererInstance();
        DeepPortraitFilter filter = (DeepPortraitFilter)getFrameFilters().get(0);
        if (filter != null) {
            if (filter.getDPInitialized()) {
                int degree = getSensorOrientation();
                int adjustedRotation = ( degree - getDisplayOrientation() + 360 ) % 360;
                int surfaceRotation =
                        90 * mActivity.getWindowManager().getDefaultDisplay().getRotation();
                mRenderer.setMaskResolution(filter.getDpMaskWidth(),filter.getDpMaskHieght());
                mRenderer.setRotationDegree(
                        adjustedRotation, (degree - surfaceRotation + 360) % 360);
            }
        }
    }
    public void onRenderSurfaceDestroyed() {
        mRenderer = null;
    }

    public static class HeifImage {
        private HeifWriter mWriter;
        private String mPath;
        private String mTitle;
        private long mDate;
        private int mQuality;
        private int mOrientation;
        private Surface mInputSurface;

        public HeifImage(HeifWriter writer, String path, String title, long date, int orientation, int quality) {
            mWriter = writer;
            mPath = path;
            mTitle = title;
            mDate = date;
            mQuality = quality;
            mOrientation = orientation;
            mInputSurface = writer.getInputSurface();
        }

        public HeifWriter getWriter() {
            return mWriter;
        }

        public String getPath() {
            return mPath;
        }

        public String getTitle() {
            return mTitle;
        }

        public long getDate() {
            return mDate;
        }

        public int getQuality() {
            return mQuality;
        }

        public Surface getInputSurface() {
            return mInputSurface;
        }

        public int getOrientation() {
            return mOrientation;
        }
    }

    public CameraMode getCurrenCameraMode() {
        if (mCurrentSceneMode == null) {
            Log.w(TAG, "getCurrenCameraMode mCurrentSceneMode is NULL retrun CameraMode.DEFAULT");
            return CameraMode.DEFAULT;
        } else {
            return mCurrentSceneMode.mode;
        }
    }

    public OnItemClickListener getModeItemClickListener() {
        return new OnItemClickListener() {
            @Override
            public int onItemClick(int mode) {
                if (!getCameraModeSwitcherAllowed()) {
                    return -1;
                }
                return selectCameraMode(mode);
            }
        };
    }

    public int selectCameraMode(int mode) {
        if (mCurrentSceneMode.mode == mSceneCameraIds.get(mode).mode) {
            return -1;
        }
        setCameraModeSwitcherAllowed(false);
        setNextSceneMode(mode);
        SceneModule nextSceneMode = mSceneCameraIds.get(mode);
        String value = mSettingsManager.getValue(SettingsManager.KEY_FRONT_REAR_SWITCHER_VALUE);
        if (value != null && value.equals("front") &&
                (nextSceneMode.mode == CameraMode.RTB ||
                 nextSceneMode.mode == CameraMode.SAT ||
                 nextSceneMode.mode == CameraMode.PRO_MODE)) {
            mSettingsManager.setValue(SettingsManager.KEY_FRONT_REAR_SWITCHER_VALUE, "rear");
        } else {
            restartAll();
        }
        updateZoomSeekBarVisible();
        mUI.updateZoomSeekBar(1.0f);
        updateZoom();
        return 1;
    }

    public void updateZoomSeekBarVisible() {
        if (mCurrentSceneMode.mode == CameraMode.PRO_MODE ||
                mCurrentSceneMode.mode == CameraMode.RTB || mIsRTBCameraId) {
            mUI.hideZoomSeekBar();
        } else {
            mUI.showZoomSeekBar();
        }
    }

    private int getNextScreneModeId(int mode) {
        SceneModule nextSceneModule = mSceneCameraIds.get(mode);
        return nextSceneModule.getNextCameraId(nextSceneModule.mode);
    }

    public void setNextSceneMode(int index) {
        mNextModeIndex = index;
    }

    public int getCurrentModeIndex() {
        return mCurrentModeIndex;
    }

    public List<String> getCameraModeList() {
        ArrayList<String> cameraModes = new ArrayList<>();
        for (SceneModule sceneModule : mSceneCameraIds) {
            cameraModes.add(mSelectableModes[sceneModule.mode.ordinal()]);
        }
        return cameraModes;
    }

    public String[] getSelectableModes() {
        return mSelectableModes;
    }

    private class SceneModule {
        CameraMode mode = CameraMode.DEFAULT;
        public int rearCameraId;
        public int frontCameraId;
        public int auxCameraId = 0;
        public int swithCameraId = -1;
        public int getCurrentId() {
            int cameraId = isBackCamera() ? rearCameraId : frontCameraId;
            cameraId = isForceAUXOn(this.mode) ? auxCameraId : cameraId;
            if ((this.mode == CameraMode.DEFAULT || this.mode == CameraMode.VIDEO ||
                      this.mode == CameraMode.HFR || this.mode == CameraMode.PRO_MODE)
                    && (mSettingsManager.isDeveloperEnabled() || swithCameraId != -1)) {
                String value = mSettingsManager.getValue(SettingsManager.KEY_SWITCH_CAMERA);
                if (value != null && !value.equals("-1")) {
                    cameraId = Integer.valueOf(value);
                }
                if (swithCameraId != -1){
                    cameraId = swithCameraId;
                }
            }
            return cameraId;
        }

        public int getNextCameraId(CameraMode nextMode) {
            int cameraId = isBackCamera() ? rearCameraId : frontCameraId;
            cameraId = isForceAUXOn(this.mode) ? auxCameraId : cameraId;
            if ((this.mode == CameraMode.DEFAULT || this.mode == CameraMode.VIDEO ||
                    this.mode == CameraMode.HFR || this.mode == CameraMode.PRO_MODE)
                    && (mSettingsManager.isDeveloperEnabled() || swithCameraId != -1)) {
                final SharedPreferences pref = mActivity.getSharedPreferences(
                        ComboPreferences.getLocalSharedPreferencesName(mActivity,
                                mSettingsManager.getNextPrepNameKey(nextMode)), Context.MODE_PRIVATE);
                String value = pref.getString(SettingsManager.KEY_SWITCH_CAMERA, null);
                if (value != null && !value.equals("-1")) {
                    cameraId = Integer.valueOf(value);
                }
                if (swithCameraId != -1) {
                    cameraId = swithCameraId;
                }
            }
            return cameraId;
        }

        public void setSwithCameraId(int swithCameraId) {
            this.swithCameraId = swithCameraId;
        }
    }

    private boolean isForceAUXOn(CameraMode mode) {
        if (mode == CameraMode.DEFAULT) {
            String auxValue = mSettingsManager.getValue(SettingsManager.KEY_FORCE_AUX);
            return auxValue != null && auxValue.equals("on");
        }
        return false;
    }

    public void setCameraModeSwitcherAllowed(boolean allow) {
        mCameraModeSwitcherAllowed = allow;
        mUI.updateCameraSwitchEnable(allow);
    }

    public boolean getCameraModeSwitcherAllowed() {
        return mCameraModeSwitcherAllowed;
    }
}

class Camera2GraphView extends View {
    private Bitmap  mBitmap;
    private Paint   mPaint = new Paint();
    private Paint   mPaintRect = new Paint();
    private Canvas  mCanvas = new Canvas();
    private float   mScale = (float)3;
    private float   mWidth;
    private float   mHeight;
    private int mStart, mEnd;
    private CaptureModule mCaptureModule;
    private float scaled;
    private static final int STATS_SIZE = 256;
    private static final String TAG = "GraphView";


    public Camera2GraphView(Context context, AttributeSet attrs) {
        super(context,attrs);

        mPaint.setFlags(Paint.ANTI_ALIAS_FLAG);
        mPaintRect.setColor(0xFFFFFFFF);
        mPaintRect.setStyle(Paint.Style.FILL);
    }

    void setDataSection(int start, int end){
        mStart =  start;
        mEnd = end;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        mBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565);
        mCanvas.setBitmap(mBitmap);
        mWidth = w;
        mHeight = h;
        super.onSizeChanged(w, h, oldw, oldh);
    }
    @Override
    protected void onDraw(Canvas canvas) {
        if(mCaptureModule == null && !mCaptureModule.mHiston) {
            Log.e(TAG, "returning as histogram is off ");
            return;
        }

        if (mBitmap != null) {
            final Paint paint = mPaint;
            final Canvas cavas = mCanvas;
            final float border = 5;
            float graphheight = mHeight - (2 * border);
            float graphwidth = mWidth - (2 * border);
            float left, top, right, bottom;
            float bargap = 0.0f;
            float barwidth = graphwidth / STATS_SIZE;

            cavas.drawColor(0xFFAAAAAA);
            paint.setColor(Color.BLACK);

            for (int k = 0; k <= (graphheight / 32); k++) {
                float y = (float) (32 * k) + border;
                cavas.drawLine(border, y, graphwidth + border, y, paint);
            }
            for (int j = 0; j <= (graphwidth / 32); j++) {
                float x = (float) (32 * j) + border;
                cavas.drawLine(x, border, x, graphheight + border, paint);
            }
            synchronized(CaptureModule.statsdata) {
                int maxValue = Integer.MIN_VALUE;
                for ( int i = mStart ; i < mEnd ; i++ ) {
                    if ( maxValue < CaptureModule.statsdata[i] ) {
                        maxValue = CaptureModule.statsdata[i];
                    }
                }
                mScale = ( float ) maxValue;
                for(int i=mStart ; i < mEnd ; i++)  {
                    scaled = (CaptureModule.statsdata[i]/mScale)*STATS_SIZE;
                    if(scaled >= (float)STATS_SIZE)
                        scaled = (float)STATS_SIZE;
                    left = (bargap * (i - mStart + 1)) + (barwidth * (i - mStart)) + border;
                    top = graphheight + border;
                    right = left + barwidth;
                    bottom = top - scaled;
                    cavas.drawRect(left, top, right, bottom, mPaintRect);
                }
            }
            canvas.drawBitmap(mBitmap, 0, 0, null);
        }
    }
    public void PreviewChanged() {
        invalidate();
    }

    public void setCaptureModuleObject(CaptureModule captureModule) {
        mCaptureModule = captureModule;
    }
}

class Camera2BGBitMap extends View {
    private Bitmap  mBitmap;
    private Paint   mPaint = new Paint();
    private Paint   mPaintRect = new Paint();
    private Canvas  mCanvas = new Canvas();
    private Paint   mTextPaint = new Paint();
    private int   mWidth;
    private int   mHeight;
    private CaptureModule mCaptureModule;
    private static final String TAG = "BG GraphView";


    public Camera2BGBitMap(Context context, AttributeSet attrs) {
        super(context,attrs);
        mWidth = CaptureModule.BGSTATS_WIDTH;
        mHeight = CaptureModule.BGSTATS_HEIGHT;
        mBitmap = Bitmap.createBitmap(mWidth, mHeight, Bitmap.Config.ARGB_8888);
        mCanvas.setBitmap(mBitmap);
        mPaint.setFlags(Paint.ANTI_ALIAS_FLAG);
        mPaintRect.setColor(0xFFFFFFFF);
        mPaintRect.setStyle(Paint.Style.FILL);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if(mCaptureModule == null && !mCaptureModule.mBGStatson) {
            Log.e(TAG, "returning as BG stats is off");
            return;
        }

        if (mBitmap != null) {
            final Canvas cavas = mCanvas;
            cavas.drawColor(0xFFAAAAAA);
            synchronized(CaptureModule.bg_statsdata){
                mBitmap.setPixels(CaptureModule.bg_statsdata, 0, 16*CaptureModule.SCALE_STATS, 0, 0, 16*CaptureModule.SCALE_STATS, 16*CaptureModule.SCALE_STATS);
            }
            canvas.drawBitmap(mBitmap, 0, 0, null);
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams)getLayoutParams();
        params.width = mWidth;
        params.height = mHeight;
        setLayoutParams(params);
        super.onSizeChanged(w, h, oldw, oldh);
    }

    public void PreviewChanged() {
        invalidate();
    }

    public void setCaptureModuleObject(CaptureModule captureModule) {
        mCaptureModule = captureModule;
    }
}

class Camera2BEBitMap extends View {
    private Bitmap  mBitmap;
    private Paint   mPaint = new Paint();
    private Paint   mPaintRect = new Paint();
    private Canvas  mCanvas = new Canvas();
    private int  mWidth;
    private int  mHeight;
    private CaptureModule mCaptureModule;
    private static final String TAG = "BE GraphView";


    public Camera2BEBitMap(Context context, AttributeSet attrs) {
        super(context,attrs);
        mWidth = CaptureModule.BESTATS_WIDTH;
        mHeight = CaptureModule.BESTATS_HEIGHT;
        mBitmap = Bitmap.createBitmap(mWidth, mHeight, Bitmap.Config.ARGB_8888);
        mCanvas.setBitmap(mBitmap);
        mPaint.setFlags(Paint.ANTI_ALIAS_FLAG);
        mPaintRect.setColor(0xFFFFFFFF);
        mPaintRect.setStyle(Paint.Style.FILL);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams)getLayoutParams();
        params.width = mWidth;
        params.height = mHeight;
        setLayoutParams(params);
        super.onSizeChanged(w, h, oldw, oldh);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if(mCaptureModule == null && !mCaptureModule.mBEStatson) {
            Log.e(TAG, "returning as BE stats is off");
            return;
        }

        if (mBitmap != null) {
            final Canvas cavas = mCanvas;
            cavas.drawColor(0xFFAAAAAA);
            synchronized(CaptureModule.be_statsdata){
            mBitmap.setPixels(CaptureModule.be_statsdata, 0, 16*CaptureModule.SCALE_STATS, 0, 0, 16*CaptureModule.SCALE_STATS, 16*CaptureModule.SCALE_STATS);
            }
            canvas.drawBitmap(mBitmap, 0, 0, null);
        }
    }
    public void PreviewChanged() {
        invalidate();
    }

    public void setCaptureModuleObject(CaptureModule captureModule) {
        mCaptureModule = captureModule;
    }
}

class DrawAutoHDR2 extends View {

    private static final String TAG = "AutoHdrView";
    private CaptureModule mCaptureModule;

    public DrawAutoHDR2(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mCaptureModule == null)
            return;
        if (mCaptureModule.mAutoHdrEnable) {
            Paint autoHDRPaint = new Paint();
            autoHDRPaint.setColor(Color.WHITE);
            autoHDRPaint.setAlpha(0);
            canvas.drawPaint(autoHDRPaint);
            autoHDRPaint.setStyle(Paint.Style.STROKE);
            autoHDRPaint.setStrokeWidth(1);
            autoHDRPaint.setTextSize(32);
            autoHDRPaint.setAlpha(255);
            canvas.drawText("HDR On", 200, 100, autoHDRPaint);
        } else {
            super.onDraw(canvas);
            return;
        }
    }

    public void AutoHDR() {
        invalidate();
    }

    public void setCaptureModuleObject(CaptureModule captureModule) {
        mCaptureModule = captureModule;
    }
}

class MFNRDrawer extends View {

    private static final String TAG = "MFNRDrawer";
    private CaptureModule mCaptureModule;

    public MFNRDrawer(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mCaptureModule == null)
            return;
        if (mCaptureModule.mMFNREnable) {
            Paint mfnrPaint = new Paint();
            mfnrPaint.setColor(Color.WHITE);
            mfnrPaint.setAlpha(0);
            canvas.drawPaint(mfnrPaint);
            mfnrPaint.setStyle(Paint.Style.STROKE);
            mfnrPaint.setStrokeWidth(1);
            mfnrPaint.setTextSize(32);
            mfnrPaint.setAlpha(255);
            canvas.drawText("MFNR", 100, 100, mfnrPaint);
        } else {
            super.onDraw(canvas);
            return;
        }
    }

    public void refleshMFNR() {
        invalidate();
    }

    public void setCaptureModuleObject(CaptureModule captureModule) {
        mCaptureModule = captureModule;
    }
}
