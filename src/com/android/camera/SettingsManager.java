 /*
 * Copyright (c) 2016-2017, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *  * Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *  * Redistributions in binary form must reproduce the above
 *    copyright notice, this list of conditions and the following
 *    disclaimer in the documentation and/or other materials provided
 *    with the distribution.
 *  * Neither the name of The Linux Foundation nor the names of its
 *    contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.camera;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.ImageFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.MediaCodecInfo;
import android.media.MediaCodecInfo.CodecCapabilities;
import android.media.MediaCodecInfo.VideoCapabilities;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.media.CamcorderProfile;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.Log;
import android.util.Range;
import android.util.Rational;
import android.util.Size;

import com.android.camera.app.CameraApp;
import com.android.camera.imageprocessor.filter.BestpictureFilter;
import com.android.camera.imageprocessor.filter.BlurbusterFilter;
import com.android.camera.imageprocessor.filter.ChromaflashFilter;
import com.android.camera.imageprocessor.filter.DeepPortraitFilter;
import com.android.camera.imageprocessor.filter.OptizoomFilter;
import com.android.camera.imageprocessor.filter.SharpshooterFilter;
import com.android.camera.imageprocessor.filter.TrackingFocusFrameListener;
import com.android.camera.imageprocessor.filter.UbifocusFilter;
import com.android.camera.imageprocessor.filter.DeepZoomFilter;
import com.android.camera.ui.ListMenu;
import com.android.camera.ui.PanoCaptureProcessView;
import com.android.camera.util.PersistUtil;
import com.android.camera.util.ApiHelper;
import com.android.camera.util.SettingTranslation;
import com.android.camera.util.AutoTestUtil;

import org.codeaurora.snapcam.R;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.lang.StringBuilder;

public class SettingsManager implements ListMenu.SettingsListener {
    public static final int RESOURCE_TYPE_THUMBNAIL = 0;
    public static final int RESOURCE_TYPE_LARGEICON = 1;

    public static final int TOUCH_TRACK_FOCUS_DISABLE = 0;
    public static final int TOUCH_TRACK_FOCUS_ENABLE = 1;

    public static final int SCENE_MODE_AUTO_INT = 0;
    public static final int SCENE_MODE_NIGHT_INT = 5;
    public static final int SCENE_MODE_HDR_INT = 18;

    public static final int TALOS_SOCID = 355;
    public static final int MOOREA_SOCID = 365;
    public static final int SAIPAN_SOCID = 400;
    public static final int SM6250_SOCID = 407;
    public static final boolean DEBUG =
            (PersistUtil.getCamera2Debug() == PersistUtil.CAMERA2_DEBUG_DUMP_LOG) ||
            (PersistUtil.getCamera2Debug() == PersistUtil.CAMERA2_DEBUG_DUMP_ALL);

    // Custom-Scenemodes start from 100
    public static final int SCENE_MODE_CUSTOM_START = 100;
    public static final int SCENE_MODE_DUAL_INT = SCENE_MODE_CUSTOM_START;
    public static final int SCENE_MODE_OPTIZOOM_INT = SCENE_MODE_CUSTOM_START + 1;
    public static final int SCENE_MODE_UBIFOCUS_INT = SCENE_MODE_CUSTOM_START + 2;
    public static final int SCENE_MODE_BESTPICTURE_INT = SCENE_MODE_CUSTOM_START + 3;
    public static final int SCENE_MODE_PANORAMA_INT = SCENE_MODE_CUSTOM_START + 4;
    public static final int SCENE_MODE_CHROMAFLASH_INT = SCENE_MODE_CUSTOM_START + 5;
    public static final int SCENE_MODE_BLURBUSTER_INT = SCENE_MODE_CUSTOM_START + 6;
    public static final int SCENE_MODE_SHARPSHOOTER_INT = SCENE_MODE_CUSTOM_START + 7;
    public static final int SCENE_MODE_TRACKINGFOCUS_INT = SCENE_MODE_CUSTOM_START + 8;
    public static final int SCENE_MODE_PROMODE_INT = SCENE_MODE_CUSTOM_START + 9;
    public static final int SCENE_MODE_DEEPZOOM_INT = SCENE_MODE_CUSTOM_START + 10;
	public static final int SCENE_MODE_DEEPPORTRAIT_INT = SCENE_MODE_CUSTOM_START + 11;
    public static final int JPEG_FORMAT = 0;
    public static final int HEIF_FORMAT = 1;
    public static final String SCENE_MODE_DUAL_STRING = "100";
    public static final String SCENE_MODE_SUNSET_STRING = "10";
    public static final String SCENE_MODE_LANDSCAPE_STRING = "4";
    public static final String KEY_CAMERA_SAVEPATH = "pref_camera2_savepath_key";
    public static final String KEY_RECORD_LOCATION = "pref_camera2_recordlocation_key";
    public static final String KEY_JPEG_QUALITY = "pref_camera2_jpegquality_key";
    public static final String KEY_FOCUS_MODE = "pref_camera2_focusmode_key";
    public static final String KEY_FLASH_MODE = "pref_camera2_flashmode_key";
    public static final String KEY_WHITE_BALANCE = "pref_camera2_whitebalance_key";
    public static final String KEY_MAKEUP = "pref_camera2_makeup_key";
    public static final String KEY_MONO_ONLY = "pref_camera2_mono_only_key";
    public static final String KEY_MONO_PREVIEW = "pref_camera2_mono_preview_key";
    public static final String KEY_CLEARSIGHT = "pref_camera2_clearsight_key";
    public static final String KEY_MPO = "pref_camera2_mpo_key";
    public static final String KEY_FILTER_MODE = "pref_camera2_filter_mode_key";
    public static final String KEY_COLOR_EFFECT = "pref_camera2_coloreffect_key";
    public static final String KEY_SCENE_MODE = "pref_camera2_scenemode_key";
    public static final String KEY_SCEND_MODE_INSTRUCTIONAL = "pref_camera2_scenemode_instructional";
    public static final String KEY_REDEYE_REDUCTION = "pref_camera2_redeyereduction_key";
    public static final String KEY_FRONT_REAR_SWITCHER_VALUE = "pref_camera2_switcher_key";
    public static final String KEY_FORCE_AUX = "pref_camera2_force_aux_key";
    public static final String KEY_SWITCH_CAMERA = "pref_camera2_switch_camera_key";
    public static final String KEY_PICTURE_SIZE = "pref_camera2_picturesize_key";
    public static final String KEY_PICTURE_FORMAT = "pref_camera2_picture_format_key";
    public static final String KEY_ISO = "pref_camera2_iso_key";
    public static final String KEY_EXPOSURE = "pref_camera2_exposure_key";
    public static final String KEY_TIMER = "pref_camera2_timer_key";
    public static final String KEY_LONGSHOT = "pref_camera2_longshot_key";
    public static final String KEY_SELFIEMIRROR = "pref_camera2_selfiemirror_key";
    public static final String KEY_VIDEO_DURATION = "pref_camera2_video_duration_key";
    public static final String KEY_VIDEO_QUALITY = "pref_camera2_video_quality_key";
    public static final String KEY_VIDEO_ENCODER = "pref_camera2_videoencoder_key";
    public static final String KEY_AUDIO_ENCODER = "pref_camera2_audioencoder_key";
    public static final String KEY_DIS = "pref_camera2_dis_key";
    public static final String KEY_NOISE_REDUCTION = "pref_camera2_noise_reduction_key";
    public static final String KEY_VIDEO_FLASH_MODE = "pref_camera2_video_flashmode_key";
    public static final String KEY_VIDEO_ROTATION = "pref_camera2_video_rotation_key";
    public static final String KEY_VIDEO_TIME_LAPSE_FRAME_INTERVAL =
            "pref_camera2_video_time_lapse_frame_interval_key";
    public static final String KEY_FACE_DETECTION = "pref_camera2_facedetection_key";
    public static final String KEY_VIDEO_HIGH_FRAME_RATE = "pref_camera2_hfr_key";
    public static final String KEY_SELFIE_FLASH = "pref_selfie_flash_key";
    public static final String KEY_SHUTTER_SOUND = "pref_camera2_shutter_sound_key";
    public static final String KEY_TOUCH_TRACK_FOCUS = "pref_camera2_touch_track_focus_key";
    public static final String KEY_SUPPORT_T2T_FOCUS = "pref_camera2_support_t2t_focus_key";
    public static final String KEY_DEVELOPER_MENU = "pref_camera2_developer_menu_key";
    public static final String KEY_RESTORE_DEFAULT = "pref_camera2_restore_default_key";
    public static final String KEY_FOCUS_DISTANCE = "pref_camera2_focus_distance_key";
    public static final String KEY_ZOOM_LEVEL = "pref_camera2_zoom_level_key";
    public static final String KEY_INSTANT_AEC = "pref_camera2_instant_aec_key";
    public static final String KEY_SATURATION_LEVEL = "pref_camera2_saturation_level_key";
    public static final String KEY_ANTI_BANDING_LEVEL = "pref_camera2_anti_banding_level_key";
    public static final String KEY_AUTO_HDR = "pref_camera2_auto_hdr_key";
    public static final String KEY_HDR = "pref_camera2_hdr_key";
    public static final String KEY_VIDEO_HDR_VALUE = "pref_camera2_video_hdr_key";
    public static final String KEY_CAPTURE_MFNR_VALUE = "pref_camera2_capture_mfnr_key";
    public static final String KEY_SENSOR_MODE_FS2_VALUE = "pref_camera2_fs2_key";
    public static final String KEY_ABORT_CAPTURES = "pref_camera2_abort_captures_key";
    public static final String KEY_SAVERAW = "pref_camera2_saveraw_key";
    public static final String KEY_ZOOM = "pref_camera2_zoom_key";
    public static final String KEY_SHARPNESS_CONTROL_MODE = "pref_camera2_sharpness_control_key";
    public static final String KEY_AF_MODE = "pref_camera2_afmode_key";
    public static final String KEY_EXPOSURE_METERING_MODE = "pref_camera2_exposure_metering_key";
    public static final String KEY_MULTI_CAMERAS_MODE = "pref_camera2_multi_cameras_key";


    //manual 3A keys and parameter strings
    public static final String KEY_MANUAL_EXPOSURE = "pref_camera2_manual_exp_key";
    public static final String KEY_MANUAL_ISO_VALUE = "pref_camera2_manual_iso_key";
    public static final String KEY_MANUAL_GAINS_VALUE = "pref_camera2_manual_gains_key";
    public static final String KEY_MANUAL_EXPOSURE_VALUE = "pref_camera2_manual_exposure_key";

    //tone mapping
    public static final String KEY_TONE_MAPPING = "pref_camera2_tone_mapping_key";
    public static final String KEY_TONE_MAPPING_DARK_BOOST = "pref_camera2_tone_mapping_dark_boost";
    public static final String KEY_TONE_MAPPING_FOURTH_TONE = "pref_camera2_tone_mapping_fourth_tone";

    public static final String AUTO_TEST_WRITE_CONTENT = "auto_test_write_content";

    public static final String KEY_MANUAL_WB = "pref_camera2_manual_wb_key";
    public static final String KEY_MANUAL_WB_TEMPERATURE_VALUE =
            "pref_camera2_manual_temperature_key";
    public static final String KEY_MANUAL_WB_R_GAIN = "pref_camera2_manual_wb_r_gain";
    public static final String KEY_MANUAL_WB_G_GAIN = "pref_camera2_manual_wb_g_gain";
    public static final String KEY_MANUAL_WB_B_GAIN = "pref_camera2_manual_wb_b_gain";

    public static final String KEY_QCFA = "pref_camera2_qcfa_key";
    public static final String KEY_EIS_VALUE = "pref_camera2_eis_key";
    public static final String KEY_FOVC_VALUE = "pref_camera2_fovc_key";
    public static final String KEY_DEEPPORTRAIT_VALUE = "pref_camera2_deepportrait_key";
    public static final String KEY_AWB_RAGIN_VALUE = "pref_camera2_awb_cct_rgain_key";
    public static final String KEY_AWB_GAGIN_VALUE = "pref_camera2_awb_cct_ggain_key";
    public static final String KEY_AWB_BAGIN_VALUE = "pref_camera2_awb_cct_bgain_key";
    public static final String KEY_AWB_CCT_VALUE = "pref_camera2_awb_cct_key";
    public static final String KEY_AWB_DECISION_AFTER_TC_0 = "pref_camera2_awb_decision_after_tc_0";
    public static final String KEY_AWB_DECISION_AFTER_TC_1 = "pref_camera2_awb_decision_after_tc_1";
    public static final String KEY_AEC_SENSITIVITY_0 = "pref_camera2_aec_sensitivity_0";
    public static final String KEY_AEC_SENSITIVITY_1 = "pref_camera2_aec_sensitivity_1";
    public static final String KEY_AEC_SENSITIVITY_2 = "pref_camera2_aec_sensitivity_2";
    public static final String KEY_STATS_VISUALIZER_VALUE = "pref_camera2_stats_visualizer_key";

    public static final HashMap<String, Integer> KEY_ISO_INDEX = new HashMap<String, Integer>();
    public static final String KEY_BSGC_DETECTION = "pref_camera2_bsgc_key";
    public static final String KEY_FACIAL_CONTOUR = "pref_camera2_facial_contour_key";
    public static final String KEY_FACE_DETECTION_MODE = "pref_camera2_face_detection_mode";
    public static final String KEY_ZSL = "pref_camera2_zsl_key";
    public static final String KEY_VIDEO_ENCODER_PROFILE = "pref_camera2_videoencoderprofile_key";
    public static final String MAUNAL_ABSOLUTE_ISO_VALUE = "absolute";

    private static final String TAG = "SnapCam_SettingsManager";

    private static SettingsManager sInstance;
    private CaptureModule mCaptureModule;
    private ArrayList<CameraCharacteristics> mCharacteristics;
    private ArrayList<Listener> mListeners;
    private Map<String, Values> mValuesMap;
    private Context mContext;
    private PreferenceGroup mPreferenceGroup;
    private ComboPreferences mPreferences;
    private Map<String, Set<String>> mDependendsOnMap;
    private boolean mIsMonoCameraPresent = false;
    private boolean mIsFrontCameraPresent = false;
    private boolean mHasMultiCamera = false;
    private boolean mIsHFRSupported = false;
    private JSONObject mDependency;
    private int mCameraId;
    private Set<String> mFilteredKeys;
    private int[] mExtendedHFRSize;//An array of pairs (fps, maxW, maxH)
    private int mDeviceSocId = -1;
    private Map<String,VideoEisConfig> mVideoEisConfigs;
    private ArrayList<String> mPrepNameKeys;
    private float mZoomMaxValue;
    private boolean mHeifWriterSupported = false;

    private static Map<String, Set<String>> VIDEO_ENCODER_PROFILE_TABLE = new HashMap<>();

    public Map<String, Values> getValuesMap() {
        return mValuesMap;
    }

    public Set<String> getFilteredKeys() {
        return mFilteredKeys;
    }

    static {
        //ISO values vendor tag
        KEY_ISO_INDEX.put("auto", 0);
        KEY_ISO_INDEX.put("deblur", 1);
        KEY_ISO_INDEX.put("100", 2);
        KEY_ISO_INDEX.put("200", 3);
        KEY_ISO_INDEX.put("400", 4);
        KEY_ISO_INDEX.put("800", 5);
        KEY_ISO_INDEX.put("1600", 6);
        KEY_ISO_INDEX.put("3200", 7);
        KEY_ISO_INDEX.put(MAUNAL_ABSOLUTE_ISO_VALUE, 8);
        Set<String> h265 = new HashSet<>();
        h265.add("HEVCProfileMain10");
        h265.add("HEVCProfileMain10HDR10");
        VIDEO_ENCODER_PROFILE_TABLE.put("h265", h265);
    }

    private SettingsManager(Context context) {
        mListeners = new ArrayList<>();
        mCharacteristics = new ArrayList<>();
        mPrepNameKeys = new ArrayList<>();
        mContext = context;
        mPreferences = ComboPreferences.get(mContext);
        if (mPreferences == null) {
            mPreferences = new ComboPreferences(mContext);
        }
        upgradeGlobalPreferences(mPreferences.getGlobal(), mContext);

        CameraManager manager = (CameraManager) mContext.getSystemService(Context.CAMERA_SERVICE);
        try {
            String[] cameraIdList = manager.getCameraIdList();
            boolean isFirstBackCameraId = true;
            boolean isRearCameraPresent = false;
            for (int i = 0; i < cameraIdList.length; i++) {
                String cameraId = cameraIdList[i];
                CameraCharacteristics characteristics
                        = manager.getCameraCharacteristics(cameraId);
                Log.d(TAG,"cameraIdList size ="+cameraIdList.length);
                byte monoOnly = 0;
                try {
                    monoOnly = characteristics.get(CaptureModule.MetaDataMonoOnlyKey);
                }catch(Exception e) {
                }
                if (monoOnly == 1) {
                    CaptureModule.MONO_ID = i;
                    mIsMonoCameraPresent = true;
                }
                int facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    CaptureModule.FRONT_ID = i;
                    mIsFrontCameraPresent = true;
                }
                if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                    isRearCameraPresent = true;
                    if (isFirstBackCameraId) {
                        isFirstBackCameraId = false;
                        mHasMultiCamera = true;
                        upgradeCameraId(mPreferences.getGlobal(), i);
                    }
                }
                mCharacteristics.add(i, characteristics);
            }
            if (isRearCameraPresent) {
                initPrepNameKeys(CameraCharacteristics.LENS_FACING_BACK);
            }
            if (mIsFrontCameraPresent) {
                initPrepNameKeys(CameraCharacteristics.LENS_FACING_FRONT);
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        mDependency = parseJson("dependency.json");
        mHeifWriterSupported = isHeifWriterSupported();
    }

    public void reloadCharacteristics(int cameraId){
        CameraManager manager = (CameraManager) mContext.getSystemService(Context.CAMERA_SERVICE);
        try {
            CameraCharacteristics characteristics
                    = manager.getCameraCharacteristics(String.valueOf(cameraId));
            mCharacteristics.set(cameraId, characteristics);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    public static SettingsManager createInstance(Context context) {
        if (sInstance == null) {
            sInstance = new SettingsManager(context.getApplicationContext());
        }
        return sInstance;
    }

    public static SettingsManager getInstance() {
        return sInstance;
    }

    public void createCaptureModule(CaptureModule captureModule){
        mCaptureModule = captureModule;
    }

    public void destroyCaptureModule(){
        if (mCaptureModule != null) {
            mCaptureModule = null;
        }
    }

    public void destroyInstance() {
        if (sInstance != null) {
            sInstance = null;
        }
    }

    private void initPrepNameKeys(int facing) {
        final String rearTag = "rear";
        final String frontTag = "front";
        if (facing == CameraCharacteristics.LENS_FACING_BACK) {
            mPrepNameKeys.add(rearTag + String.valueOf(CaptureModule.CameraMode.DEFAULT));
            mPrepNameKeys.add(rearTag + String.valueOf(CaptureModule.CameraMode.VIDEO));
            mPrepNameKeys.add(rearTag + String.valueOf(CaptureModule.CameraMode.HFR));
            mPrepNameKeys.add(rearTag + String.valueOf(CaptureModule.CameraMode.RTB));
            mPrepNameKeys.add(rearTag + String.valueOf(CaptureModule.CameraMode.SAT));
            mPrepNameKeys.add(rearTag + String.valueOf(CaptureModule.CameraMode.PRO_MODE));
        } else {
            mPrepNameKeys.add(frontTag + String.valueOf(CaptureModule.CameraMode.DEFAULT));
            mPrepNameKeys.add(frontTag + String.valueOf(CaptureModule.CameraMode.VIDEO));
            mPrepNameKeys.add(frontTag + String.valueOf(CaptureModule.CameraMode.HFR));
        }
    }

    private void upgradeGlobalPreferences(SharedPreferences pref, Context context) {
        CameraSettings.upgradeOldVersion(pref, context);
    }

    private void upgradeCameraId(SharedPreferences pref, int id) {
        CameraSettings.writePreferredCameraId(pref, id);
    }

    public List<String> getDisabledList() {
        List<String> list = new ArrayList<>();
        Set<String> keySet = mValuesMap.keySet();
        for (String key : keySet) {
            Values value = mValuesMap.get(key);
            if (value.overriddenValue != null) {
                list.add(key);
            }
        }
        return list;
    }

    @Override
    public void onSettingChanged(ListPreference pref) {
        String key = pref.getKey();
        List changed = checkDependencyAndUpdate(key);
        if (changed == null) return;
        runTimeUpdateDependencyOptions(pref);
        notifyListeners(changed);
    }

    public void updatePictureAndVideoSize() {
        ListPreference picturePref = mPreferenceGroup.findPreference(KEY_PICTURE_SIZE);
        ListPreference videoQualityPref = mPreferenceGroup.findPreference(KEY_VIDEO_QUALITY);
        if (picturePref != null) {
            picturePref.setEntries(mContext.getResources().getStringArray(
                    R.array.pref_camera2_picturesize_entries));
            picturePref.setEntryValues(mContext.getResources().getStringArray(
                    R.array.pref_camera2_picturesize_entryvalues));
            filterUnsupportedOptions(picturePref, getSupportedPictureSize(
                    getCurrentCameraId()));
        }
        if (videoQualityPref != null) {
            videoQualityPref.setEntries(mContext.getResources().getStringArray(
                    R.array.pref_camera2_video_quality_entries));
            videoQualityPref.setEntryValues(mContext.getResources().getStringArray(
                    R.array.pref_camera2_video_quality_entryvalues));
            filterUnsupportedOptions(videoQualityPref,getSupportedVideoSize(
                    getCurrentCameraId()));
        }
    }

    public void init() {
        Log.d(TAG, "SettingsManager init" + CaptureModule.CURRENT_ID);
        final int cameraId = getInitialCameraId();
        setLocalIdAndInitialize(cameraId);
        autoTestBroadcast(cameraId);
        reloadCharacteristics(cameraId);
    }

    public void reinit(int cameraId) {
        Log.d(TAG, "SettingsManager reinit " + cameraId);
        setLocalIdAndInitialize(cameraId);
    }

    private void autoTestBroadcast(int cameraId) {
        final SharedPreferences pref = mContext.getSharedPreferences(
                ComboPreferences.getLocalSharedPreferencesName(mContext, getCurrentPrepNameKey()),
                Context.MODE_PRIVATE);
        final SharedPreferences.Editor editor = pref.edit();
        boolean autoWrite = pref.getBoolean(AUTO_TEST_WRITE_CONTENT, true);
        if (autoWrite) {
            Thread autoTest = new Thread() {
                public void run() {
                    writeAutoTextHelpTxt(editor);
                }
            };
            autoTest.start();
        }
    }

    private void writeAutoTextHelpTxt(SharedPreferences.Editor editor) {
        List<String> supportLists = new ArrayList<String>();
        /* Video Size */
        String[] videoSizes = getEntryValues(R.array.pref_camera2_video_quality_entryvalues);
        /* Picture Size */
        String[] pictSizes = getEntryValues(R.array.pref_camera2_picturesize_entryvalues);
        // back support pictureSizes
        List<String> backPLists = getSupportList(getSupportedPictureSize(0), pictSizes);
        supportLists.add("<Back camera support PictureSizes>");
        supportLists.addAll(backPLists);
        // front support pictureSizes
        if (mCharacteristics.size() > 1) {
            List<String> frontPLists = getSupportList(getSupportedPictureSize(1), pictSizes);
            supportLists.add("<Front camera support PictureSizes>");
            supportLists.addAll(frontPLists);
            /* Video Size */
            List<String> frontVideoLists = getSupportList(getSupportedVideoSize(1), videoSizes);
            supportLists.add("<Front camera support VideoSizes and fps>");
            for (int i=0; i < frontVideoLists.size(); i++) {
                String videoSize = frontVideoLists.get(i);
                List<String> fps = getSupportedHFRForAutoTest(videoSize);
                supportLists.add(videoSize);
                supportLists.addAll(fps);
                List<String> videoEncoders = getSupportedVideoEncoderForAutoTest(videoSize);
                supportLists.addAll(videoEncoders);
                supportLists.add("");
            }
        }
        List<String> backVideoLists = getSupportList(getSupportedVideoSize(0), videoSizes);
        supportLists.add("<Back camera support VideoSizes and fps>");
        for (int i=0; i < backVideoLists.size(); i++) {
            String videoSize = backVideoLists.get(i);
            List<String> fps = getSupportedHFRForAutoTest(videoSize);
            supportLists.add(videoSize);
            supportLists.addAll(fps);
            List<String> videoEncoders = getSupportedVideoEncoderForAutoTest(videoSize);
            supportLists.addAll(videoEncoders);
            supportLists.add("");
        }

        String filePath = AutoTestUtil.createFile(mContext);
        boolean result = AutoTestUtil.writeFileContent(filePath, supportLists);
        editor.putBoolean(AUTO_TEST_WRITE_CONTENT, false);
        editor.apply();
    }

    private List<String> setCharSequenceToListStr(String title, CharSequence[] charSequences) {
        List<String> list = new ArrayList<String>();
        list.add(title);
        for (CharSequence support : charSequences) {
            list.add(support.toString());
        }
        return list;
    }

    private String[] getEntryValues(int id) {
        return mContext.getResources().getStringArray(id);
    }

    public List<String> getSupportList(List<String> supported, String[] supportList) {
        List<String> resultList = new ArrayList<String>();
        for (String item : supportList) {
            if (supported.indexOf(item) >= 0) {
                resultList.add(item);
            }
        }
        return resultList;
    }

    private void setLocalIdAndInitialize(int cameraId) {
        String facing = mPreferences.getGlobal().getString(KEY_FRONT_REAR_SWITCHER_VALUE, "rear");
        mPreferences.setLocalId(mContext, facing, String.valueOf(CaptureModule.CURRENT_MODE));
        mCameraId = cameraId;
        CameraSettings.upgradeLocalPreferences(mPreferences.getLocal());

        PreferenceInflater inflater = new PreferenceInflater(mContext);
        mPreferenceGroup =
                (PreferenceGroup) inflater.inflate(R.xml.capture_preferences);
        mValuesMap = new HashMap<>();
        mDependendsOnMap = new HashMap<>();
        mFilteredKeys = new HashSet<>();
        try {
            if (mCharacteristics.size() > 0) {
                mExtendedHFRSize = mCharacteristics.get(cameraId).get(CaptureModule.hfrFpsTable);
            }
        } catch(IllegalArgumentException exception) {
            exception.printStackTrace();
        }

        filterPreferences(cameraId);
        initDependencyTable();
        initializeValueMap();
        filterChromaflashPictureSizeOptions();
        filterHeifSizeOptions();
        mVideoEisConfigs = getVideoEisConfigs(cameraId);
    }

    public boolean isBurstShotSupported(){
        boolean isBurstShotSupported = true;
        try {
            isBurstShotSupported = mCharacteristics.get(mCameraId).get(CaptureModule.is_burstshot_supported) == 1 ? true : false;
        } catch (IllegalArgumentException | NullPointerException e) {
            Log.e(TAG, "isBurstShotSupported no vendor tag");
        }
        return isBurstShotSupported;
    }

    public boolean isFDRenderingAtPreview(){
        boolean isFDRenderingInUI = false;
        if( CaptureModule.CURRENT_MODE == CaptureModule.CameraMode.VIDEO ||
                CaptureModule.CURRENT_MODE == CaptureModule.CameraMode.HFR) {
            isFDRenderingInUI = isFDRenderingInVideoUISupported();
        }else{
            isFDRenderingInUI = isCameraFDSupported();
        }
        return isFDRenderingInUI;
    }

    public boolean isT2TSupported() {
        boolean supportted = true;
        try {
            supportted =
                    (mCharacteristics.get(mCameraId).get(CaptureModule.is_t2t_supported) == 1);
        } catch (IllegalArgumentException | NullPointerException e) {
            Log.d(TAG, "isT2TSupported no vendor tag");
            supportted = true;
        }
        return supportted;
    }

    public boolean isSwMctfSupported() {
        boolean supportted = false;
        try {
            supportted =
                    (mCharacteristics.get(mCameraId).get(CaptureModule.swmctf) == 1);
        } catch (IllegalArgumentException | NullPointerException e) {
            Log.d(TAG, "swmctf no vendor tag");
            supportted = true;
        }
        Log.i(TAG,"isSwMctfSupported:" + supportted);
        return supportted;
    }

    public boolean isCameraFDSupported(){
        boolean isCameraFDSupported = false;
        isCameraFDSupported = PersistUtil.isCameraFDSupported();
        if(!isCameraFDSupported) {
            try {
                isCameraFDSupported =
                        mCharacteristics.get(mCameraId).get(CaptureModule.is_camera_fd_supported) == 1;
            } catch (IllegalArgumentException | NullPointerException e) {
                if (DEBUG) {
                    Log.w(TAG, "isCameraFDSupported no vendor tag");
                }
                isCameraFDSupported = true;
            }
        }
        return isCameraFDSupported;
    }

    public float getmaxBurstShotFPS(){
        float maxBurstShotFPS = 0;
        try {
            maxBurstShotFPS = mCharacteristics.get(mCameraId).get(CaptureModule.max_burstshot_fps);
        } catch (IllegalArgumentException | NullPointerException e) {
            Log.e(TAG, "getmaxBurstShotFPS no vendorTag maxBurstShotFPS:");
        }
        return maxBurstShotFPS;
    }

    public int[] getMaxPreviewSize(){
        int[] maxPreviewSize = null;
        try {
            maxPreviewSize = mCharacteristics.get(mCameraId).get(CaptureModule.max_preview_size);
        } catch (IllegalArgumentException | NullPointerException e) {
            Log.e(TAG, "getMaxPreviewSize no vendorTag max_preview_size:");
        }
        return maxPreviewSize;
    }

    public boolean isLiveshotSizeSameAsVideoSize(){
        boolean isLiveshotSizeSameAsVideoSize = false;
        try {
            isLiveshotSizeSameAsVideoSize = mCharacteristics.get(mCameraId).get(CaptureModule.is_liveshot_size_same_as_video) == 1 ? true : false;
        } catch (IllegalArgumentException | NullPointerException e) {
            Log.e(TAG, "isLiveshotSizeSameAsVideoSize no vendorTag isLiveshotSizeSameAsVideoSize:");
        }
        return isLiveshotSizeSameAsVideoSize;
    }

    private Size parseSize(String value) {
        int indexX = value.indexOf('x');
        int width = Integer.parseInt(value.substring(0, indexX));
        int height = Integer.parseInt(value.substring(indexX + 1));
        return new Size(width, height);
    }

    private void initDependencyTable() {
        for (int i = 0; i < mPreferenceGroup.size(); i++) {
            ListPreference pref = (ListPreference) mPreferenceGroup.get(i);
            String baseKey = pref.getKey();
            String value = pref.getValue();

            JSONObject dependency = getDependencyList(baseKey, value);
            if (dependency != null) {
                Iterator<String> keys = dependency.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    pref = mPreferenceGroup.findPreference(key);
                    if (pref == null) continue; //filtered?
                    Set set = mDependendsOnMap.get(key);
                    if (set == null) {
                        set = new HashSet<>();
                    }
                    set.add(baseKey);
                    mDependendsOnMap.put(key, set);
                }
            }
        }
    }

    private void initializeValueMap() {
        List<String> processLater = new ArrayList<String>();
        for (int i = 0; i < mPreferenceGroup.size(); i++) {
            ListPreference pref = (ListPreference) mPreferenceGroup.get(i);
            String key = pref.getKey();
            Set<String> set = mDependendsOnMap.get(key);
            if (set != null && set.size() != 0) {
                processLater.add(key);
            }
            Values values = new Values(pref.getValue(), null);
            mValuesMap.put(pref.getKey(), values);
        }

        for (String keyToProcess : processLater) {
            Set<String> dependsOnSet = mDependendsOnMap.get(keyToProcess);
            String dependentKey = dependsOnSet.iterator().next();
            String value = getValue(dependentKey);
            JSONObject dependencyList = getDependencyList(dependentKey, value);

            String newValue = null;
            try {
                newValue = dependencyList.getString(keyToProcess);
            } catch (JSONException e) {
                Log.w(TAG, "initializeValueMap JSONException No value for:" + keyToProcess);
                continue;
            }
            Values values = new Values(getValue(keyToProcess), newValue);
            mValuesMap.put(keyToProcess, values);
        }
    }

    private List<SettingState> checkDependencyAndUpdate(String changedPrefKey) {
        ListPreference changedPref = mPreferenceGroup.findPreference(changedPrefKey);
        if (changedPref == null) return null;

        String value = changedPref.getValue();
        String prevValue = getValue(changedPrefKey);
        if (value.equals(prevValue)) return null;

        List<SettingState> changed = new ArrayList();
        Values values = new Values(value, null);
        mValuesMap.put(changedPrefKey, values);
        changed.add(new SettingState(changedPrefKey, values));

        JSONObject map = getDependencyMapForKey(changedPrefKey);
        if (map == null || getDependencyKey(map, value).equals(getDependencyKey(map,prevValue)))
            return changed;

        Set<String> turnOn = new HashSet<>();
        Set<String> turnOff = new HashSet<>();

        JSONObject dependencyList = getDependencyList(changedPrefKey, value);
        JSONObject originalDependencyList = getDependencyList(changedPrefKey, prevValue);

        Iterator<String> it = null;
        if (originalDependencyList != null) {
            it = originalDependencyList.keys();
            while (it != null && it.hasNext()) {
                turnOn.add(it.next());
            }
        }
        if (dependencyList != null) {
            it = dependencyList.keys();
            while (it != null && it.hasNext()) {
                turnOff.add(it.next());
            }
        }
        if (originalDependencyList != null) {
            it = originalDependencyList.keys();
            while (it != null && it.hasNext()) {
                turnOff.remove(it.next());
            }
        }
        if (dependencyList != null) {
            it = dependencyList.keys();
            while (it != null && it.hasNext()) {
                turnOn.remove(it.next());
            }
        }

        for (String keyToTurnOn: turnOn) {
            Set<String> dependsOnSet = mDependendsOnMap.get(keyToTurnOn);
            if (dependsOnSet == null || dependsOnSet.size() == 0) continue;

                values = mValuesMap.get(keyToTurnOn);
                if (values == null) continue;
                values.overriddenValue = null;
                mValuesMap.put(keyToTurnOn, values);
                changed.add(new SettingState(keyToTurnOn, values));
        }

        for (String keyToTurnOff: turnOff) {
            ListPreference pref = mPreferenceGroup.findPreference(keyToTurnOff);
            if (pref == null) continue;
            values = mValuesMap.get(keyToTurnOff);
            if (values == null) continue;
            if (values != null && values.overriddenValue != null) continue;
            String newValue = null;
            try {
                newValue = dependencyList.getString(keyToTurnOff);
            } catch (JSONException e) {
                Log.w(TAG, "checkDependencyAndUpdate JSONException No value for:" + keyToTurnOff);
                continue;
            }
            if (newValue == null) continue;

            Values newValues = new Values(pref.getValue(), newValue);
            mValuesMap.put(keyToTurnOff, newValues);
            changed.add(new SettingState(keyToTurnOff, newValues));
        }
            updateBackDependency(changedPrefKey, turnOn, turnOff);
        return changed;
    }

    private void updateBackDependency(String key, Set<String> remove, Set<String> add) {
        for (CharSequence c : remove) {
            String currentKey = c.toString();
            Set<String> dependsOnSet = mDependendsOnMap.get(currentKey);
            if (dependsOnSet != null) dependsOnSet.remove(key);
        }
        for (CharSequence c : add) {
            String currentKey = c.toString();
            Set<String> dependsOnSet = mDependendsOnMap.get(currentKey);
            if (dependsOnSet == null) {
                dependsOnSet = new HashSet<>();
                mDependendsOnMap.put(currentKey, dependsOnSet);
            }
            dependsOnSet.add(key);
        }
    }

    public int[] getSensorModeTable(final int cameraId) {
        int[] sensorModeTable = null;
        try {
            sensorModeTable = mCharacteristics.get(cameraId).get(CaptureModule.sensorModeTable);
        } catch (IllegalArgumentException exception) {
            exception.printStackTrace();
        }
        return sensorModeTable;
    }

    public int[] getHighSpeedVideoConfigs(final int cameraId) {
        int[] highSpeedVideoConfigs = null;
        try {
            highSpeedVideoConfigs = mCharacteristics.get(cameraId).get(
                    CaptureModule.highSpeedVideoConfigs);
        } catch (IllegalArgumentException exception) {
            exception.printStackTrace();
        }
        return highSpeedVideoConfigs;
    }

    public void registerListener(Listener listener) {
        mListeners.add(listener);
    }

    public void unregisterListener(Listener listener) {
        mListeners.remove(listener);
    }

    private void notifyListeners(List<SettingState> changes) {
        for (Listener listener : mListeners) {
            listener.onSettingsChanged(changes);
        }
    }

    public int getDeviceSocId() {
        if (mDeviceSocId == -1) {
            String filePath = "/sys/devices/soc0/soc_id";
            File file = new File(filePath);
            if (file.isDirectory()) {
                Log.d(TAG, filePath + " is directory");
            } else {
                try {
                    InputStream is = new FileInputStream(file);
                    if (is != null) {
                        InputStreamReader isr = new InputStreamReader(is);
                        BufferedReader br = new BufferedReader(isr);

                        String line;
                        while ((line = br.readLine()) != null) {
                            mDeviceSocId = Integer.parseInt(line);
                        }
                    }
                } catch (FileNotFoundException e) {
                    Log.d(TAG, filePath + " doesn't found!");
                } catch (IOException e) {
                    Log.d(TAG, filePath + " read exception, " + e.getMessage());
                }
            }
        }
        Log.d(TAG, "getDeviceSocId mDeviceSocId :" + mDeviceSocId);
        return mDeviceSocId;
    }

    public int getCurrentCameraId() {
        return mCameraId;
    }

    public String getCurrentPrepNameKey() {
        String facing = mPreferences.getGlobal().getString(KEY_FRONT_REAR_SWITCHER_VALUE, "rear");
        return facing + String.valueOf(CaptureModule.CURRENT_MODE);
    }

    public String getNextPrepNameKey(CaptureModule.CameraMode nextMode) {
        String facing = mPreferences.getGlobal().getString(KEY_FRONT_REAR_SWITCHER_VALUE, "rear");
        return facing + String.valueOf(nextMode);
    }

    public String getValue(String key) {
        if (mValuesMap == null) return null;
        Values values = mValuesMap.get(key);
        if (values == null) return null;
        if (values.overriddenValue == null) return values.value;
        else return values.overriddenValue;
    }

    public int getValueIndex(String key) {
        ListPreference pref = mPreferenceGroup.findPreference(key);
        String value = getValue(key);
        if ((value == null) || (pref == null)) return -1;
        return pref.findIndexOfValue(value);
    }

    private boolean setFocusValue(String key, float value) {
        boolean result = false;
        String prefName = ComboPreferences.getLocalSharedPreferencesName(mContext,
                getCurrentPrepNameKey());
        SharedPreferences sharedPreferences = mContext.getSharedPreferences(prefName,
                Context.MODE_PRIVATE);
        float prefValue = sharedPreferences.getFloat(key, 0.5f);
        if (prefValue != value) {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putFloat(key, value);
            editor.apply();
            result = true;
        }
        return result;
    }

    public float getProModeSliderValue(String key, float defaultValue) {
        String prefName = ComboPreferences.getLocalSharedPreferencesName(mContext,
                getCurrentPrepNameKey());
        SharedPreferences sharedPreferences = mContext.getSharedPreferences(prefName,
                Context.MODE_PRIVATE);
        return sharedPreferences.getFloat(key, defaultValue);
    }

    public boolean isOverriden(String key) {
        Values values = mValuesMap.get(key);
        return values.overriddenValue != null;
    }

    public boolean setValue(String key, String value) {
        ListPreference pref = mPreferenceGroup.findPreference(key);
        if (pref != null) {
            if (pref.findIndexOfValue(value) < 0) {
                return false;
            } else {
                pref.setValue(value);
                updateMapAndNotify(pref);
                return true;
            }
        } else {
            return false;
        }
    }

    public boolean setValue(String key, Set<String> set) {
        ListPreference pref = mPreferenceGroup.findPreference(key);
        if (pref != null) {
            pref.setFromMultiValues(set);
            updateMapAndNotify(pref);
            return true;
        } else {
            return false;
        }
    }

    public void setValueIndex(String key, int index) {
        ListPreference pref = mPreferenceGroup.findPreference(key);
        if (pref != null) {
            pref.setValueIndex(index);
            updateMapAndNotify(pref);
        }
    }

    public void setProModeSliderValue(String key, boolean forceNotify, float value) {
        boolean isSuccess = false;
        if (value >= 0) {
            isSuccess = setFocusValue(key, value);
        }
        if (isSuccess || forceNotify) {
            List<SettingState> list = new ArrayList<>();
            Values values = new Values("" + value, null);
            SettingState ss = new SettingState(key, values);
            list.add(ss);
            notifyListeners(list);
        }
    }

    public float getCalculatedFocusDistance() {
        float minFocus = getMinimumFocusDistance(mCameraId);
        return getProModeSliderValue(KEY_FOCUS_DISTANCE, 0.5f) * minFocus;
    }

    public float getCalculatedZoomValue() {
        float minZoom = 1.0f;
        float maxZoom = getmZoomMaxValue();
        return getProModeSliderValue(KEY_ZOOM_LEVEL, 0.0f) * (maxZoom - minZoom) + minZoom;
    }

    private void updateMapAndNotify(ListPreference pref) {
        String key = pref.getKey();
        List changed = checkDependencyAndUpdate(key);
        if (changed == null) return;
        runTimeUpdateDependencyOptions(pref);
        notifyListeners(changed);
    }

    public PreferenceGroup getPreferenceGroup() {
        return mPreferenceGroup;
    }

    public CharSequence[] getEntries(String key) {
        if ( mPreferenceGroup != null ) {
            ListPreference pref = mPreferenceGroup.findPreference(key);
            if (pref != null) {
                return pref.getEntries();
            }
        }
        return null;
    }

    public CharSequence[] getEntryValues(String key) {
        ListPreference pref = mPreferenceGroup.findPreference(key);
        if (pref != null) {
            return pref.getEntryValues();
        }
        return null;
    }

    public int[] getResource(String key, int type) {
        IconListPreference pref = (IconListPreference) mPreferenceGroup.findPreference(key);
        switch (type) {
            case RESOURCE_TYPE_THUMBNAIL:
                return pref.getThumbnailIds();
            case RESOURCE_TYPE_LARGEICON:
                return pref.getLargeIconIds();
        }
        return null;
    }

    public int getInitialCameraId() {
        return CaptureModule.CURRENT_ID;
    }

    private void filterPreferences(int cameraId) {
        if (CaptureModule.CURRENT_MODE == CaptureModule.CameraMode.VIDEO) {
            ListPreference hfrPref = mPreferenceGroup.findPreference(KEY_VIDEO_HIGH_FRAME_RATE);
            if (hfrPref != null) {
                hfrPref.setValue("off");
            }
        }
        // filter unsupported preferences
        ListPreference savePath = mPreferenceGroup.findPreference(KEY_CAMERA_SAVEPATH);
        ListPreference forceAUX = mPreferenceGroup.findPreference(KEY_FORCE_AUX);
        ListPreference whiteBalance = mPreferenceGroup.findPreference(KEY_WHITE_BALANCE);
        ListPreference flashMode = mPreferenceGroup.findPreference(KEY_FLASH_MODE);
        ListPreference colorEffect = mPreferenceGroup.findPreference(KEY_COLOR_EFFECT);
        ListPreference sceneMode = mPreferenceGroup.findPreference(KEY_SCENE_MODE);
        ListPreference sceneModeInstructional =
                mPreferenceGroup.findPreference(KEY_SCEND_MODE_INSTRUCTIONAL);

        ListPreference frontRearSwitcherPref =
                mPreferenceGroup.findPreference(KEY_FRONT_REAR_SWITCHER_VALUE);
        ListPreference pictureSize = mPreferenceGroup.findPreference(KEY_PICTURE_SIZE);
        ListPreference exposure = mPreferenceGroup.findPreference(KEY_EXPOSURE);
        ListPreference iso = mPreferenceGroup.findPreference(KEY_ISO);
        ListPreference clearsight = mPreferenceGroup.findPreference(KEY_CLEARSIGHT);
        ListPreference monoPreview = mPreferenceGroup.findPreference(KEY_MONO_PREVIEW);
        ListPreference monoOnly = mPreferenceGroup.findPreference(KEY_MONO_ONLY);
        ListPreference mpo = mPreferenceGroup.findPreference(KEY_MPO);
        ListPreference redeyeReduction = mPreferenceGroup.findPreference(KEY_REDEYE_REDUCTION);
        ListPreference videoQuality = mPreferenceGroup.findPreference(KEY_VIDEO_QUALITY);
        ListPreference videoDuration = mPreferenceGroup.findPreference(KEY_VIDEO_DURATION);
        ListPreference audioEncoder = mPreferenceGroup.findPreference(KEY_AUDIO_ENCODER);
        ListPreference noiseReduction = mPreferenceGroup.findPreference(KEY_NOISE_REDUCTION);
        ListPreference faceDetection = mPreferenceGroup.findPreference(KEY_FACE_DETECTION);
        ListPreference instantAec = mPreferenceGroup.findPreference(KEY_INSTANT_AEC);
        ListPreference saturationLevel = mPreferenceGroup.findPreference(KEY_SATURATION_LEVEL);
        ListPreference antiBandingLevel = mPreferenceGroup.findPreference(KEY_ANTI_BANDING_LEVEL);
        ListPreference stats_visualizer = mPreferenceGroup.findPreference(KEY_STATS_VISUALIZER_VALUE);
        ListPreference hdr = mPreferenceGroup.findPreference(KEY_HDR);
        ListPreference zoom = mPreferenceGroup.findPreference(KEY_ZOOM);
        ListPreference qcfa = mPreferenceGroup.findPreference(KEY_QCFA);
        ListPreference bsgc = mPreferenceGroup.findPreference(KEY_BSGC_DETECTION);
        ListPreference pictureFormat = mPreferenceGroup.findPreference(KEY_PICTURE_FORMAT);
        ListPreference faceDetectionMode = mPreferenceGroup.findPreference(KEY_FACE_DETECTION_MODE);
        ListPreference fsMode = mPreferenceGroup.findPreference(KEY_SENSOR_MODE_FS2_VALUE);

        if (forceAUX != null && !mHasMultiCamera) {
            removePreference(mPreferenceGroup, KEY_FORCE_AUX);
            mFilteredKeys.add(forceAUX.getKey());
        }
        buildCameraId();

        if (savePath != null) {
            if (filterUnsupportedOptions(savePath, getSupportedSavePaths(cameraId))) {
                mFilteredKeys.add(savePath.getKey());
            }
        }


        if (whiteBalance != null) {
            if (filterUnsupportedOptions(whiteBalance, getSupportedWhiteBalanceModes(cameraId))) {
                mFilteredKeys.add(whiteBalance.getKey());
            }
        }

        if (flashMode != null) {
            if (!isFlashAvailable(mCameraId)) {
                removePreference(mPreferenceGroup, KEY_FLASH_MODE);
                removePreference(mPreferenceGroup, KEY_VIDEO_FLASH_MODE);
                mFilteredKeys.add(flashMode.getKey());
            }
            // Front Camera does not support video
            if (mCameraId == CaptureModule.FRONT_ID) {
                removePreference(mPreferenceGroup, KEY_VIDEO_FLASH_MODE);
            }
        }

        if (bsgc != null) {
            if (!isBsgcAvailable(mCameraId)) {
                removePreference(mPreferenceGroup, KEY_BSGC_DETECTION);
                removePreference(mPreferenceGroup, KEY_FACIAL_CONTOUR);
                mFilteredKeys.add(bsgc.getKey());
                mFilteredKeys.add(KEY_FACIAL_CONTOUR);
            }
        }

        if (faceDetectionMode != null) {
            if (!isFaceDetectionModeSupported(mCameraId)) {
                removePreference(mPreferenceGroup, KEY_FACE_DETECTION_MODE);
                mFilteredKeys.add(faceDetectionMode.getKey());
            }
        }

        if (colorEffect != null) {
            if (filterUnsupportedOptions(colorEffect, getSupportedColorEffects(cameraId))) {
                mFilteredKeys.add(colorEffect.getKey());
            }
        }

        if (instantAec != null) {
            if (filterUnsupportedOptions(instantAec,
                    getSupportedInstantAecAvailableModes(cameraId))) {
                mFilteredKeys.add(instantAec.getKey());
            }
        }

        if (saturationLevel != null) {
            if (filterUnsupportedOptions(saturationLevel,
                    getSupportedSaturationLevelAvailableModes(cameraId))) {
                mFilteredKeys.add(saturationLevel.getKey());
            }
        }

        if (antiBandingLevel != null) {
            if (filterUnsupportedOptions(antiBandingLevel,
                    getSupportedAntiBandingLevelAvailableModes(cameraId))) {
                mFilteredKeys.add(antiBandingLevel.getKey());
            }
        }

        if (hdr != null){
            if (filterUnsupportedOptions(hdr,
                    getSupportedHdrAvailableModes(cameraId))) {
                mFilteredKeys.add(hdr.getKey());
            }
        }

        if (sceneMode != null) {
            if (filterUnsupportedOptions(sceneMode, getSupportedSceneModes(cameraId))) {
                mFilteredKeys.add(sceneMode.getKey());
            }
        }

        if ( sceneModeInstructional != null ) {
            if (filterUnsupportedOptions(sceneModeInstructional,
                    getSupportedSceneModes(cameraId)) ){
                mFilteredKeys.add(sceneModeInstructional.getKey());
            }
        }

        if (frontRearSwitcherPref != null && (!mIsFrontCameraPresent)) {
            removePreference(mPreferenceGroup, KEY_FRONT_REAR_SWITCHER_VALUE);
        }

        if (pictureSize != null) {
            if (filterUnsupportedOptions(pictureSize, getSupportedPictureSize(cameraId))) {
                mFilteredKeys.add(pictureSize.getKey());
            } else {
                if (filterSimilarPictureSize(mPreferenceGroup, pictureSize)) {
                    mFilteredKeys.add(pictureSize.getKey());
                }
            }
        }

        if (exposure != null) {
            buildExposureCompensation(cameraId);
        }

        if (iso != null) {
            if (filterUnsupportedOptions(iso, getSupportedIso(cameraId))) {
                mFilteredKeys.add(iso.getKey());
            }
        }

        if (videoQuality != null) {
            if (filterUnsupportedOptions(videoQuality,
                    getSupportedVideoSize(cameraId))) {
                mFilteredKeys.add(videoQuality.getKey());
            }
        }

        if (videoDuration != null) {
            final SharedPreferences pref = mContext.getSharedPreferences(
                    ComboPreferences.getLocalSharedPreferencesName(mContext, getCurrentPrepNameKey()),
                    Context.MODE_PRIVATE);
            String fpsStr = pref.getString(SettingsManager.KEY_VIDEO_HIGH_FRAME_RATE, "off");
            if (fpsStr != null && !fpsStr.equals("off")) {
                int fpsRate = Integer.parseInt(fpsStr.substring(3));
                if (fpsRate == 480) {
                    if (filterUnsupportedOptions(videoDuration, getSupportedVideoDurationFor480())) {
                        mFilteredKeys.add(videoDuration.getKey());
                    }
                } else {
                    if (filterUnsupportedOptions(videoDuration, getSupportedVideoDuration())) {
                        mFilteredKeys.add(videoDuration.getKey());
                    }
                }
            } else {
                if (filterUnsupportedOptions(videoDuration, getSupportedVideoDuration())) {
                    mFilteredKeys.add(videoDuration.getKey());
                }
            }
        }

        if (!mIsMonoCameraPresent) {
            if (clearsight != null) removePreference(mPreferenceGroup, KEY_CLEARSIGHT);
            if (monoPreview != null) removePreference(mPreferenceGroup, KEY_MONO_PREVIEW);
            if (monoOnly != null) removePreference(mPreferenceGroup, KEY_MONO_ONLY);
            if (mpo != null) removePreference(mPreferenceGroup, KEY_MPO);
        }

        if (redeyeReduction != null) {
            if (filterUnsupportedOptions(redeyeReduction, getSupportedRedeyeReduction(cameraId))) {
                mFilteredKeys.add(redeyeReduction.getKey());
            }
        }

        if (audioEncoder != null) {
            if (filterUnsupportedOptions(audioEncoder,
                    getSupportedAudioEncoders(audioEncoder.getEntryValues()))) {
                mFilteredKeys.add(audioEncoder.getKey());
            }
        }

        if (noiseReduction != null) {
            if (filterUnsupportedOptions(noiseReduction,
                    getSupportedNoiseReductionModes(cameraId))) {
                mFilteredKeys.add(noiseReduction.getKey());
            }
        }

        if (faceDetection != null) {
            if (!isFaceDetectionSupported(cameraId)) {
                removePreference(mPreferenceGroup, KEY_FACE_DETECTION);
            }
        }

        // filter dynamic lists.
        // These list can be changed run-time
        filterHFROptions();
        filterVideoEncoderOptions();
        filterVideoEncoderProfileOptions();

        if (!mIsFrontCameraPresent || !isFacingFront(mCameraId)) {
            removePreference(mPreferenceGroup, KEY_SELFIE_FLASH);
            removePreference(mPreferenceGroup, KEY_SELFIEMIRROR);
        }

        if ( zoom != null ) {
            if (filterUnsupportedOptions(zoom,
                    getSupportedZoomLevel(cameraId))) {
                mFilteredKeys.add(zoom.getKey());
            }
        }

        if (pictureFormat != null){
            if (filterUnsupportedOptions(pictureFormat,
                    getSupportedPictureFormat(cameraId))){
                mFilteredKeys.add(pictureSize.getKey());
            }
        }

        if (fsMode != null) {
            if (!isFastShutterModeSupported(cameraId)) {
                removePreference(mPreferenceGroup, KEY_SENSOR_MODE_FS2_VALUE);
            }
        }

    }

    private void runTimeUpdateDependencyOptions(ListPreference pref) {
        // update the supported list
        if (pref.getKey().equals(KEY_VIDEO_QUALITY)) {
            filterHFROptions();
            filterVideoEncoderOptions();
        } else if (pref.getKey().equals(KEY_SCENE_MODE)) {
            filterChromaflashPictureSizeOptions();
        } else if ( pref.getKey().equals(KEY_VIDEO_ENCODER) ) {
            filterVideoEncoderProfileOptions();
        } else if (pref.getKey().equals(KEY_PICTURE_FORMAT)) {
            filterHeifSizeOptions();
        }
    }

    private void buildExposureCompensation(int cameraId) {
        Range<Integer> range = mCharacteristics.get(cameraId).get(CameraCharacteristics
                .CONTROL_AE_COMPENSATION_RANGE);
        int max = range.getUpper();
        int min = range.getLower();
        if (min == 0 && max == 0) {
            removePreference(mPreferenceGroup, KEY_EXPOSURE);
            return;
        }
        ListPreference pref = mPreferenceGroup.findPreference(KEY_EXPOSURE);
        Rational rational = mCharacteristics.get(cameraId).get(CameraCharacteristics
                .CONTROL_AE_COMPENSATION_STEP);
        double step = rational.doubleValue();
        int increment = 1;
        while ((max - min) / increment > 10) {
            increment++;
        }
        int start = min;
        if (start < 0) {
            while (Math.abs(start) % increment != 0) {
                start++;
            }
        }
        int size = 0;
        for (int i = start; i <= max; i += increment) size++;
        CharSequence entries[] = new CharSequence[size];
        CharSequence entryValues[] = new CharSequence[size];
        int count = 0;
        for (int i = start; i <= max; i += increment, count++) {
            entryValues[count] = Integer.toString(i);
            StringBuilder builder = new StringBuilder();
            if (i > 0) builder.append('+');
            DecimalFormat format = new DecimalFormat("#.##");
            entries[count] = builder.append(format.format(i * step)).toString();
        }
        pref.setEntries(entries);
        pref.setEntryValues(entryValues);
    }

    public CharSequence[] getExposureCompensationEntries() {
          ListPreference pref = mPreferenceGroup.findPreference(KEY_EXPOSURE);
        if (pref == null) return null;
        return pref.getEntries();
    }

    public CharSequence[] getExposureCompensationEntryValues() {
        ListPreference pref = mPreferenceGroup.findPreference(KEY_EXPOSURE);
        if (pref == null) return null;
        return pref.getEntryValues();
    }

    public int[] getWBColorTemperatureRangeValues(int cameraId) {
        int[] wbRange = null;
        try {
            wbRange =  mCharacteristics.get(cameraId).get(CaptureModule.WB_COLOR_TEMPERATURE_RANGE);
            if (wbRange == null) {
                Log.w(TAG, "Supported exposure range get null.");
                return null;
            }
        } catch(IllegalArgumentException e) {
            Log.w(TAG, "Supported exposure range modes occur IllegalArgumentException.");
        }
        return wbRange;
    }

    public float[] getWBGainsRangeValues(int cameraId) {
        float[] rgbRange = null;
        try {
            rgbRange =  mCharacteristics.get(cameraId).get(CaptureModule.WB_RGB_GAINS_RANGE);
            if (rgbRange == null) {
                Log.w(TAG, "Supported gains range get null.");
                return null;
            }
        } catch(IllegalArgumentException e) {
            Log.w(TAG, "Supported gains range modes occur IllegalArgumentException.");
        }
        return rgbRange;
    }

    public long[] getExposureRangeValues(int cameraId) {
        long[] exposureRange = null;
        try {
            exposureRange =  mCharacteristics.get(cameraId).get(
                    CaptureModule.EXPOSURE_RANGE);
            if (exposureRange == null) {
                Log.w(TAG, "get exposure range modes is null.");
                return null;
            }
        } catch(IllegalArgumentException e) {
            Log.w(TAG, "IllegalArgumentException Supported exposure range modes is null.");
        }
        return exposureRange;
    }

    public int[] getIsoRangeValues(int cameraId) {
        Range<Integer> range = null;
        int[] result = new int[2];
        try {
            range = mCharacteristics.get(cameraId).get(CameraCharacteristics
                    .SENSOR_INFO_SENSITIVITY_RANGE);
            if (range == null) {
                return null;
            }
            result[0] = range.getLower();
            result[1] = range.getUpper();
        } catch(IllegalArgumentException e) {
            Log.w(TAG, "IllegalArgumentException Supported iso range is null.");
        }
        return result;
    }

    private void buildCameraId() {
        int numOfCameras = mCharacteristics.size();
        CharSequence[] fullEntryValues = new CharSequence[numOfCameras + 1];
        CharSequence[] fullEntries = new CharSequence[numOfCameras + 1];
        for(int i = 0; i < numOfCameras ; i++) {
            int facing = mCharacteristics.get(i).get(CameraCharacteristics.LENS_FACING);
            String cameraIdString = "camera " + i +" facing:" +
                    (facing == CameraCharacteristics.LENS_FACING_FRONT ? "front" : "back");
            fullEntries[i] = "camera " + i +" facing:"+cameraIdString;
            try {
                Byte cameraType = mCharacteristics.get(i).get(CaptureModule.logical_camera_type);
                if (cameraType != null) {
                    switch (cameraType) {
                        case CaptureModule.TYPE_DEFAULT:
                            cameraIdString += " Default";
                            break;
                        case CaptureModule.TYPE_RTB:
                            cameraIdString += " RTB";
                            break;
                        case CaptureModule.TYPE_SAT:
                            cameraIdString += " SAT";
                            break;
                        case CaptureModule.TYPE_VR360:
                            cameraIdString += " VR360";
                            break;
                    }
                }
            } catch(IllegalArgumentException e) {
                Log.e(TAG, "buildCameraId no vendorTag :" + CaptureModule.logical_camera_type);
            }
            fullEntries[i] = cameraIdString;
            fullEntryValues[i] = "" + i;
            Log.d(TAG,"add "+fullEntries[i]+"="+ fullEntryValues[i]);
        }
        fullEntries[numOfCameras] = "disable";
        fullEntryValues[numOfCameras] = "" + -1;
        ListPreference switchPref = mPreferenceGroup.findPreference(KEY_SWITCH_CAMERA);
        switchPref.setEntries(fullEntries);
        switchPref.setEntryValues(fullEntryValues);
    }

    private void filterVideoEncoderOptions() {
        ListPreference videoEncoder = mPreferenceGroup.findPreference(KEY_VIDEO_ENCODER);

        if (videoEncoder != null) {
            videoEncoder.reloadInitialEntriesAndEntryValues();
            if (filterUnsupportedOptions(videoEncoder,
                    getSupportedVideoEncoders())) {
                mFilteredKeys.add(videoEncoder.getKey());
            }
        }
    }

    private void filterChromaflashPictureSizeOptions() {
        String scene = getValue(SettingsManager.KEY_SCENE_MODE);
        ListPreference picturePref = mPreferenceGroup.findPreference(KEY_PICTURE_SIZE);
        if (picturePref == null) return;
        picturePref.reloadInitialEntriesAndEntryValues();
        if (Integer.parseInt(scene) == SCENE_MODE_CHROMAFLASH_INT) {
            if (filterUnsupportedOptions(picturePref, getSupportedChromaFlashPictureSize())) {
                mFilteredKeys.add(picturePref.getKey());
            }
            // if picture size is setted the CIF/QVGA, modify smallest supportted size .
            Size pictureSize = parseSize(getValue(KEY_PICTURE_SIZE));
            if (pictureSize.getWidth() <= 352 && pictureSize.getHeight() <= 288) {
                CharSequence[] entryValues = picturePref.getEntryValues();
                int size = entryValues.length;
                CharSequence smallerSize = entryValues[size -1];
                setValue(KEY_PICTURE_SIZE, smallerSize.toString());
            }
        } else {
            if (filterUnsupportedOptions(picturePref, getSupportedPictureSize(
                    getCurrentCameraId()))) {
                mFilteredKeys.add(picturePref.getKey());
            }
        }
    }

    private void filterHeifSizeOptions() {
        ListPreference picturePref = mPreferenceGroup.findPreference(KEY_PICTURE_SIZE);
        ListPreference videoQualityPref = mPreferenceGroup.findPreference(KEY_VIDEO_QUALITY);
        if(picturePref == null || videoQualityPref == null)
            return;
        if (filterUnsupportedOptions(picturePref, getSupportedPictureSize(
                getCurrentCameraId()))) {
            mFilteredKeys.add(picturePref.getKey());
        }
        if (filterUnsupportedOptions(videoQualityPref, getSupportedVideoSize(
                getCurrentCameraId()))) {
            mFilteredKeys.add(videoQualityPref.getKey());
        }
    }

    private void filterHFROptions() {
        ListPreference hfrPref = mPreferenceGroup.findPreference(KEY_VIDEO_HIGH_FRAME_RATE);
        if (hfrPref != null) {
            hfrPref.reloadInitialEntriesAndEntryValues();
            mIsHFRSupported = !filterUnsupportedOptions(hfrPref,
                    getSupportedHighFrameRate());
            if (!mIsHFRSupported) {
                mFilteredKeys.add(hfrPref.getKey());
            }
        }
    }

    public boolean isHFRSupported() {
        return mIsHFRSupported;
    }

    private void filterVideoEncoderProfileOptions() {
        ListPreference videoEncoderProfilePref =
                mPreferenceGroup.findPreference(KEY_VIDEO_ENCODER_PROFILE);
        ListPreference videoEncoderPref = mPreferenceGroup.findPreference(KEY_VIDEO_ENCODER);
        if ( videoEncoderProfilePref != null && videoEncoderPref != null ) {
            String videoEncoder = videoEncoderPref.getValue();
            videoEncoderProfilePref.reloadInitialEntriesAndEntryValues();
            if ( filterUnsupportedOptions(videoEncoderProfilePref,
                    getSupportedVideoEncoderProfile(videoEncoder)) ) {
                mFilteredKeys.add(videoEncoderProfilePref.getKey());
            }
        }
    }

    private List<String> getSupportedChromaFlashPictureSize() {
        StreamConfigurationMap map = mCharacteristics.get(getCurrentCameraId()).get(
                CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        Size[] sizes = map.getOutputSizes(ImageFormat.JPEG);
        List<String> res = new ArrayList<>();
        if (sizes != null) {
            for (int i = 0; i < sizes.length; i++) {
                if (sizes[i].getWidth() > 352 && sizes[i].getHeight() > 288) {
                    res.add(sizes[i].toString());
                }
            }
        }

        Size[] highResSizes = map.getHighResolutionOutputSizes(ImageFormat.JPEG);
        if (highResSizes != null) {
            for (int i = 0; i < highResSizes.length; i++) {
                if (sizes[i].getWidth() > 352 && sizes[i].getHeight() > 288) {
                    res.add(highResSizes[i].toString());
                }
            }
        }

        return res;
    }

    private List<String> getSupportedVideoEncoderForAutoTest(String videoSizeStr) {
        ArrayList<String> supported = new ArrayList<String>();
        ListPreference videoEncoder = mPreferenceGroup.findPreference(KEY_VIDEO_ENCODER);
        if (videoEncoder == null) return supported;

        if (videoEncoder != null) {
            String str = null;
            MediaCodecList list = new MediaCodecList(MediaCodecList.ALL_CODECS);
            MediaCodecInfo[] codecInfos = list.getCodecInfos();
            for (MediaCodecInfo info: codecInfos) {
                if ( info.isEncoder() ) {
                    int type = SettingTranslation.getVideoEncoderType(info.getSupportedTypes()[0]);
                    if (type != -1){
                        str = SettingTranslation.getVideoEncoder(type);
                        if (isCurrentVideoResolutionSupportedByEncoder(info)) {
                            supported.add(str);
                        }
                    }
                }
            }
        }
        return supported;
    }

    private List<String> getSupportedHFRForAutoTest(String videoSizeStr) {
        ArrayList<String> supported = new ArrayList<String>();
        ListPreference videoEncoder = mPreferenceGroup.findPreference(KEY_VIDEO_ENCODER);
        if (videoEncoder == null) return supported;
        int videoEncoderNum = SettingTranslation.getVideoEncoder(videoEncoder.getValue());
        VideoCapabilities videoCapabilities = null;
        boolean findVideoEncoder = false;
        if (videoSizeStr != null) {
            Size videoSize = parseSize(videoSizeStr);
            MediaCodecList allCodecs = new MediaCodecList(MediaCodecList.ALL_CODECS);
            for (MediaCodecInfo info : allCodecs.getCodecInfos()) {
                if (!info.isEncoder() || info.getName().contains("google")) continue;
                for (String type : info.getSupportedTypes()) {
                    if ((videoEncoderNum == MediaRecorder.VideoEncoder.MPEG_4_SP && type.equalsIgnoreCase(MediaFormat.MIMETYPE_VIDEO_MPEG4))
                            || (videoEncoderNum == MediaRecorder.VideoEncoder.H263 && type.equalsIgnoreCase(MediaFormat.MIMETYPE_VIDEO_H263))
                            || (videoEncoderNum == MediaRecorder.VideoEncoder.H264 && type.equalsIgnoreCase(MediaFormat.MIMETYPE_VIDEO_AVC))
                            || (videoEncoderNum == MediaRecorder.VideoEncoder.HEVC && type.equalsIgnoreCase(MediaFormat.MIMETYPE_VIDEO_HEVC))) {
                        CodecCapabilities codecCapabilities = info.getCapabilitiesForType(type);
                        videoCapabilities = codecCapabilities.getVideoCapabilities();
                        findVideoEncoder = true;
                        break;
                    }
                }
                if (findVideoEncoder) break;
            }

            try {
                Range[] range = getSupportedHighSpeedVideoFPSRange(mCameraId, videoSize);
                for (Range r : range) {
                    // To support HFR for both preview and recording,
                    // minmal FPS needs to be equal to maximum FPS
                    if ((int) r.getUpper() == (int) r.getLower()) {
                        if (videoCapabilities != null) {
                            if (videoCapabilities.areSizeAndRateSupported(
                                    videoSize.getWidth(), videoSize.getHeight(), (int) r.getUpper())) {
                                supported.add("hfr" + String.valueOf(r.getUpper()));
                                supported.add("hsr" + String.valueOf(r.getUpper()));
                            }
                        }
                    }
                }
            } catch (IllegalArgumentException ex) {
                Log.w(TAG, "HFR is not supported for this resolution " + ex);
            }
            if (mExtendedHFRSize != null && mExtendedHFRSize.length >= 3) {
                for (int i = 0; i < mExtendedHFRSize.length; i += 3) {
                    String item = "hfr" + mExtendedHFRSize[i + 2];
                    if (!supported.contains(item)
                            && videoSize.getWidth() <= mExtendedHFRSize[i]
                            && videoSize.getHeight() <= mExtendedHFRSize[i + 1]) {
                        if (videoCapabilities != null) {
                            if (videoCapabilities.areSizeAndRateSupported(
                                    videoSize.getWidth(), videoSize.getHeight(), mExtendedHFRSize[i + 2])) {
                                supported.add(item);
                                supported.add("hsr" + mExtendedHFRSize[i + 2]);
                            }
                        }
                    }
                }
            }
        }
        return supported;
    }

    private List<String> getSupportedHighFrameRate() {
        ArrayList<String> supported = new ArrayList<String>();
        supported.add("off");
        ListPreference videoQuality = mPreferenceGroup.findPreference(KEY_VIDEO_QUALITY);
        ListPreference videoEncoder = mPreferenceGroup.findPreference(KEY_VIDEO_ENCODER);
        if (videoQuality == null || videoEncoder == null) return supported;
        String videoSizeStr = videoQuality.getValue();
        int videoEncoderNum = SettingTranslation.getVideoEncoder(videoEncoder.getValue());
        VideoCapabilities videoCapabilities = null;
        boolean findVideoEncoder = false;
        if (videoSizeStr != null) {
            Size videoSize = parseSize(videoSizeStr);
            boolean above1080p = videoSize.getHeight() * videoSize.getWidth() > 1920*1080;
            MediaCodecList allCodecs = new MediaCodecList(MediaCodecList.ALL_CODECS);
            for (MediaCodecInfo info : allCodecs.getCodecInfos()) {
                if (!info.isEncoder() || info.getName().contains("google")) continue;
                for (String type : info.getSupportedTypes()) {
                    if ((videoEncoderNum == MediaRecorder.VideoEncoder.MPEG_4_SP && type.equalsIgnoreCase(MediaFormat.MIMETYPE_VIDEO_MPEG4))
                            || (videoEncoderNum == MediaRecorder.VideoEncoder.H263 && type.equalsIgnoreCase(MediaFormat.MIMETYPE_VIDEO_H263))
                            || (videoEncoderNum == MediaRecorder.VideoEncoder.H264 && type.equalsIgnoreCase(MediaFormat.MIMETYPE_VIDEO_AVC))
                            || (videoEncoderNum == MediaRecorder.VideoEncoder.HEVC && type.equalsIgnoreCase(MediaFormat.MIMETYPE_VIDEO_HEVC))) {
                        CodecCapabilities codecCapabilities = info.getCapabilitiesForType(type);
                        videoCapabilities = codecCapabilities.getVideoCapabilities();
                        findVideoEncoder = true;
                        break;
                    }
                }
                if (findVideoEncoder) break;
            }

            try {
                Range[] range = getSupportedHighSpeedVideoFPSRange(mCameraId, videoSize);
                String rate;
                for (Range r : range) {
                    // To support HFR for both preview and recording,
                    // minmal FPS needs to be equal to maximum FPS
                    if ((int) r.getUpper() == (int) r.getLower()) {
                        if (videoCapabilities != null) {
                            if (videoCapabilities.areSizeAndRateSupported(
                                    videoSize.getWidth(), videoSize.getHeight(), (int) r.getUpper())) {
                                rate = String.valueOf(r.getUpper());
                                supported.add("hfr" + rate);
                                supported.add("hsr" + rate);
                            }
                        }
                    }
                }
            } catch (IllegalArgumentException ex) {
                Log.w(TAG, "HFR is not supported for this resolution " + ex);
            }
            if (mExtendedHFRSize != null && mExtendedHFRSize.length >= 3) {
                for (int i = 0; i < mExtendedHFRSize.length; i += 3) {
                    String item = "hfr" + mExtendedHFRSize[i + 2];
                    if (!supported.contains(item)
                            && videoSize.getWidth() <= mExtendedHFRSize[i]
                            && videoSize.getHeight() <= mExtendedHFRSize[i + 1]) {
                        if (videoCapabilities != null) {
                            if (videoCapabilities.areSizeAndRateSupported(
                                    videoSize.getWidth(), videoSize.getHeight(), mExtendedHFRSize[i + 2])) {
                                supported.add(item);
                                supported.add("hsr" + mExtendedHFRSize[i + 2]);
                            }
                        }
                    }
                }
            }
        }
        return supported;
    }

    private boolean removePreference(PreferenceGroup group, String key) {
        mFilteredKeys.add(key);
        for (int i = 0, n = group.size(); i < n; i++) {
            CameraPreference child = group.get(i);
            if (child instanceof PreferenceGroup) {
                if (removePreference((PreferenceGroup) child, key)) {
                    return true;
                }
            }
            if (child instanceof ListPreference &&
                    ((ListPreference) child).getKey().equals(key)) {
                group.removePreference(i);
                return true;
            }
        }
        return false;
    }

    public float getMaxZoom(int id) {
        mZoomMaxValue = mCharacteristics.get(id).get(CameraCharacteristics
                .SCALER_AVAILABLE_MAX_DIGITAL_ZOOM);
        return mZoomMaxValue;
    }

    public float getmZoomMaxValue() {
        return mZoomMaxValue;
    }

    public Rect getSensorActiveArraySize(int id) {
        return mCharacteristics.get(id).get(CameraCharacteristics
                .SENSOR_INFO_ACTIVE_ARRAY_SIZE);
    }

    public float getMaxZoom(List<Integer> ids) {
        float zoomMax = Float.MAX_VALUE;
        for (int id : ids) {
            zoomMax = Math.min(getMaxZoom(id), zoomMax);
        }
        return zoomMax;
    }

    public boolean isZoomSupported(int id) {
        return mCharacteristics.get(id).get(CameraCharacteristics
                .SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) > 1f;
    }

    public boolean isAutoFocusRegionSupported(List<Integer> ids) {
        for (int id : ids) {
            if (!isAutoFocusRegionSupported(id))
                return false;
        }
        return true;
    }

    public boolean isAutoExposureRegionSupported(List<Integer> ids) {
        for (int id : ids) {
            if (!isAutoExposureRegionSupported(id))
                return false;
        }
        return true;
    }

    public boolean isZoomSupported(List<Integer> ids) {
        for (int id : ids) {
            if (!isZoomSupported(id))
                return false;
        }
        return true;
    }

    public boolean isZZHDRSupported() {
        int modes[] = null;
        try {
            modes = mCharacteristics.get(getCurrentCameraId())
                    .get(CaptureModule.support_video_hdr_modes);
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "cannot find vendor tag: " +
                    CaptureModule.support_video_hdr_modes.toString());
        }
        return modes != null && modes.length > 1;
    }

    public boolean isAutoExposureRegionSupported(int id) {
        Integer maxAERegions = mCharacteristics.get(id).get(
                CameraCharacteristics.CONTROL_MAX_REGIONS_AE);
        return maxAERegions != null && maxAERegions > 0;
    }

    public boolean isAutoFocusRegionSupported(int id) {
        Integer maxAfRegions = mCharacteristics.get(id).get(
                CameraCharacteristics.CONTROL_MAX_REGIONS_AF);
        return maxAfRegions != null && maxAfRegions > 0;
    }

    public boolean isFixedFocus(int id) {
        Float focusDistance = mCharacteristics.get(id).get(CameraCharacteristics
                .LENS_INFO_MINIMUM_FOCUS_DISTANCE);
        if (focusDistance == null || focusDistance == 0) {
            return true;
        } else {
            return false;
        }
    }

    public boolean isFaceDetectionSupported(int id) {
        int[] faceDetection = mCharacteristics.get(id).get
                (CameraCharacteristics.STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES);
        if (faceDetection != null) {
            for (int value: faceDetection) {
                if (value == CameraMetadata.STATISTICS_FACE_DETECT_MODE_SIMPLE)
                    return true;
            }
        }
        return false;
    }
    private boolean isFDRenderingInVideoUISupported(){
        boolean isFDRenderingInVideoUISupported = false;
        isFDRenderingInVideoUISupported = PersistUtil.isFDRENDERINGSUPPORTED();
        if(!isFDRenderingInVideoUISupported) {
            try {
                isFDRenderingInVideoUISupported = mCharacteristics.get(mCameraId).get(CaptureModule.is_FD_Rendering_In_Video_UI_Supported) == 1;
            } catch (IllegalArgumentException | NullPointerException e) {
                isFDRenderingInVideoUISupported = true;
                if (DEBUG) {
                    Log.w(TAG, "isFDRenderingInVideoUISupported no vendorTag isFDRenderingInVideoUISupported:");
                }
            }
        }
        return isFDRenderingInVideoUISupported;
    }

    public boolean isFaceDetectionModeSupported(int id) {
//        always enable FaceDetectionMode by default
//        int[] faceDetection = mCharacteristics.get(id).get
//                (CameraCharacteristics.STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES);
//        if (faceDetection != null && faceDetection.length > 2) {
//            return true;
//        }
//        return false;
        return true;
    }


    public boolean isBsgcAvailable(int id) {
//        boolean ret = false;
//        try {
//            if (mCharacteristics.size() > 0) {
//                byte bsgc_available = mCharacteristics.get(id).get(CaptureModule.bsgcAvailable);
//                ret = bsgc_available == 1;
//            }
//        } catch (IllegalArgumentException e) {
//            e.printStackTrace();
//        }
//        return ret;
        return true;
    }

    private boolean isFastShutterModeSupported(int id) {
        boolean result = false;
        try {
            byte fastModeSupport = mCharacteristics.get(id).get(CaptureModule.fs_mode_support);
            result = (fastModeSupport == 1);
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        } catch(NullPointerException e) {
            Log.w(TAG, "Supported fs_mode_support is null.");
        }
        return result;
    }

    public boolean isFacingFront(int id) {
        int facing = mCharacteristics.get(id).get(CameraCharacteristics.LENS_FACING);
        return facing == CameraCharacteristics.LENS_FACING_FRONT;
    }

    public boolean isFlashSupported(int id) {
        return mCharacteristics.get(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) &&
                mValuesMap.get(KEY_FLASH_MODE) != null &&
                CaptureModule.CURRENT_MODE != CaptureModule.CameraMode.RTB &&
                CaptureModule.CURRENT_MODE != CaptureModule.CameraMode.SAT;
    }

    private List<String> getSupportedPictureSize(int cameraId) {
        StreamConfigurationMap map = mCharacteristics.get(cameraId).get(
                CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        Size[] sizes = map.getOutputSizes(ImageFormat.JPEG);
        List<String> res = new ArrayList<>();

        boolean isDeepportrait = getDeepportraitEnabled();
        boolean isHeifEnabled = getSavePictureFormat() == HEIF_FORMAT;

        if (getQcfaPrefEnabled() && getIsSupportedQcfa(cameraId)) {
            res.add(getSupportedQcfaDimension(cameraId));
        }

        VideoCapabilities heifCap = null;
        if (isHeifEnabled) {
            MediaCodecList list = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
            for (MediaCodecInfo info :list.getCodecInfos()) {
                if (info.isEncoder() && info.getName().contains("heic")){
                    heifCap = info.getCapabilitiesForType(
                            MediaFormat.MIMETYPE_IMAGE_ANDROID_HEIC).getVideoCapabilities();
                    Log.d(TAG,"supported heif height range ="+heifCap.getSupportedHeights().toString() +
                            " width range ="+heifCap.getSupportedWidths().toString());
                }
            }
        }

        if (sizes != null) {
            for (int i = 0; i < sizes.length; i++) {
                if (isHeifEnabled && heifCap != null ){
                    if (!heifCap.getSupportedWidths().contains(sizes[i].getWidth()) ||
                        !heifCap.getSupportedHeights().contains(sizes[i].getHeight())){
                        continue;
                    }
                }
                if (isDeepportrait &&
                        (Math.min(sizes[i].getWidth(),sizes[i].getHeight()) < 720 ||
                        Math.max(sizes[i].getWidth(),sizes[i].getHeight()) <= 1024)) {
                    //some reslutions are not supported in deepportrait
                    continue;
                }
                res.add(sizes[i].toString());
            }
        }

        Size[] highResSizes = map.getHighResolutionOutputSizes(ImageFormat.JPEG);

        if (highResSizes != null) {
            for (int i = 0; i < highResSizes.length; i++) {
                if (isHeifEnabled && heifCap != null) {
                    if (!heifCap.getSupportedWidths().contains(highResSizes[i].getWidth()) ||
                            !heifCap.getSupportedHeights().contains(highResSizes[i].getHeight())){
                        continue;
                    }
                }
                res.add(highResSizes[i].toString());
            }
        }

        return res;
    }

    public Size[] getSupportedThumbnailSizes(int cameraId) {
        return mCharacteristics.get(cameraId).get(
                CameraCharacteristics.JPEG_AVAILABLE_THUMBNAIL_SIZES);
    }

    public Size[] getSupportedOutputSize(int cameraId, int format) {
        StreamConfigurationMap map = mCharacteristics.get(cameraId).get(
                CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        return map.getOutputSizes(format);
    }

    public Size[] getSupportedOutputSize(int cameraId, Class cl) {
        StreamConfigurationMap map = mCharacteristics.get(cameraId).get(
                CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        Size[] normal = map.getOutputSizes(cl);
        Size[] high = map.getHighResolutionOutputSizes(ImageFormat.PRIVATE);
        Size[] ret = new Size[normal.length+high.length];
        System.arraycopy(normal,0,ret,0,normal.length);
        System.arraycopy(high,0,ret,normal.length,high.length);
        return ret;
    }

    public Size[] getHighResolutionOutputSize(int cameraId){
        StreamConfigurationMap map = mCharacteristics.get(cameraId).get(
                CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        return map.getHighResolutionOutputSizes(ImageFormat.JPEG);
    }

    private List<String> getSupportedVideoDuration() {
        int[] videoDurations = {-1, 10, 30, 0};
        List<String> modes = new ArrayList<>();
        for (int i : videoDurations) {
            modes.add(""+i);
        }
        return  modes;
    }

    private List<String> getSupportedVideoDurationFor480() {
        int[] videoDurations = {48, 144, 0};
        List<String> modes = new ArrayList<>();
        for (int i : videoDurations) {
            modes.add(""+i);
        }
        return  modes;
    }

    private List<String> getSupportedVideoSize(int cameraId) {
        StreamConfigurationMap map = mCharacteristics.get(cameraId).get(
                CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        Size[] sizes = map.getOutputSizes(MediaRecorder.class);
        boolean isHeifEnabled = getSavePictureFormat() == HEIF_FORMAT;
        VideoCapabilities heifCap = null;
        if (isHeifEnabled) {
            MediaCodecList list = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
            for (MediaCodecInfo info :list.getCodecInfos()) {
                if (info.isEncoder() && info.getName().contains("heic")){
                    heifCap = info.getCapabilitiesForType(
                            MediaFormat.MIMETYPE_IMAGE_ANDROID_HEIC).getVideoCapabilities();
                    Log.d(TAG,"supported heif height range ="+heifCap.getSupportedHeights().toString() +
                            " width range ="+heifCap.getSupportedWidths().toString());
                }
            }
        }
        List<String> res = new ArrayList<>();
        for (int i = 0; i < sizes.length; i++) {
            if (isHeifEnabled && heifCap != null ){
                if (!heifCap.getSupportedWidths().contains(sizes[i].getWidth()) ||
                        !heifCap.getSupportedHeights().contains(sizes[i].getHeight())){
                    continue;
                }
            }
            if (CameraSettings.VIDEO_QUALITY_TABLE.containsKey(sizes[i].toString())) {
                Integer profile = CameraSettings.VIDEO_QUALITY_TABLE.get(sizes[i].toString());
                if (profile != null && CamcorderProfile.hasProfile(cameraId, profile)) {
                    res.add(sizes[i].toString());
                }
            }
        }
        return res;
    }

    public Size[] getSupportedHighSpeedVideoSize(int cameraId) {
        StreamConfigurationMap map = mCharacteristics.get(cameraId).get(
                CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        return map.getHighSpeedVideoSizes();
    }

    public Range[] getSupportedHighSpeedVideoFPSRange(int cameraId, Size videoSize) {
        StreamConfigurationMap map = mCharacteristics.get(cameraId).get(
                CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        return map.getHighSpeedVideoFpsRangesFor(videoSize);
    }

    public int getHighSpeedVideoEncoderBitRate(CamcorderProfile profile, int targetRate,
                                               int captureRate) {
        long bitRate;
        String key = profile.videoFrameWidth+"x"+profile.videoFrameHeight+":"+captureRate;
        String resolutionFpsEncoder = key + ":" + profile.videoCodec;
        if (CameraSettings.VIDEO_ENCODER_BITRATE.containsKey(resolutionFpsEncoder)) {
            bitRate = CameraSettings.VIDEO_ENCODER_BITRATE.get(resolutionFpsEncoder);
        } else if (CameraSettings.VIDEO_ENCODER_BITRATE.containsKey(key)) {
            bitRate = CameraSettings.VIDEO_ENCODER_BITRATE.get(key);
        } else {
            Log.i(TAG, "No pre-defined bitrate for "+key);
            bitRate = (profile.videoBitRate * targetRate) / profile.videoFrameRate;
            return (int)bitRate;
        }
        if (targetRate != captureRate) { // HFR use case. Do scaling based on HSR bitrate
            bitRate = (bitRate * targetRate) / captureRate;
        }
        return (int)bitRate;
    }

    private List<String> getSupportedRedeyeReduction(int cameraId) {
        int[] flashModes = mCharacteristics.get(cameraId).get(CameraCharacteristics
                .CONTROL_AE_AVAILABLE_MODES);
        List<String> modes = new ArrayList<>();
        for (int i = 0; i < flashModes.length; i++) {
            if (flashModes[i] == CameraMetadata.CONTROL_AE_MODE_ON_AUTO_FLASH_REDEYE) {
                modes.add("off");
                modes.add("on");
                break;
            }
        }
        return modes;
    }

    public float getMinimumFocusDistance(int cameraId) {
        return mCharacteristics.get(cameraId)
                .get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE);
    }

    private List<String> getSupportedSavePaths(int cameraId) {
        boolean writeable = SDCard.instance().isWriteable();
        List<String> savePaths = new ArrayList<>();
        savePaths.add("" + 0);
        if (writeable) {
            savePaths.add("" + 1);
        }
        return savePaths;
    }

    private List<String> getSupportedWhiteBalanceModes(int cameraId) {
        try {
            List<String> modes = new ArrayList<>();
            if (mCharacteristics.size() > 0) {
                int[] whiteBalanceModes = mCharacteristics.get(cameraId).get(CameraCharacteristics
                        .CONTROL_AWB_AVAILABLE_MODES);
                for (int mode : whiteBalanceModes) {
                    modes.add("" + mode);
                }
            }
            return modes;
        } catch (IndexOutOfBoundsException e) {
            return null;
        }
    }

    private List<String> getSupportedSceneModes(int cameraId) {
        int[] sceneModes = mCharacteristics.get(cameraId).get(CameraCharacteristics
                .CONTROL_AVAILABLE_SCENE_MODES);
        List<String> modes = new ArrayList<>();
        modes.add("0"); // need special case handle for auto scene mode
        if (mIsMonoCameraPresent) modes.add(SCENE_MODE_DUAL_STRING); // need special case handle for dual mode
        if (OptizoomFilter.isSupportedStatic()) modes.add(SCENE_MODE_OPTIZOOM_INT + "");
        if (UbifocusFilter.isSupportedStatic() && cameraId == CaptureModule.BAYER_ID) modes.add(SCENE_MODE_UBIFOCUS_INT + "");
        if (BestpictureFilter.isSupportedStatic() && cameraId == CaptureModule.BAYER_ID) modes.add(SCENE_MODE_BESTPICTURE_INT + "");
        if (PanoCaptureProcessView.isSupportedStatic() && cameraId == CaptureModule.BAYER_ID) modes.add(SCENE_MODE_PANORAMA_INT + "");
        if (ChromaflashFilter.isSupportedStatic() && cameraId == CaptureModule.BAYER_ID) modes.add(SCENE_MODE_CHROMAFLASH_INT + "");
        if (BlurbusterFilter.isSupportedStatic()) modes.add(SCENE_MODE_BLURBUSTER_INT + "");
        if (SharpshooterFilter.isSupportedStatic()) modes.add(SCENE_MODE_SHARPSHOOTER_INT + "");
        if (TrackingFocusFrameListener.isSupportedStatic()) modes.add(SCENE_MODE_TRACKINGFOCUS_INT + "");
        if (DeepZoomFilter.isSupportedStatic()) modes.add(SCENE_MODE_DEEPZOOM_INT + "");
        if (DeepPortraitFilter.isSupportedStatic()) modes.add(SCENE_MODE_DEEPPORTRAIT_INT+"");
        for (int mode : sceneModes) {
            //remove scene mode like "Sunset", "Night" such as, only keep "HDR" mode 	1889
            if (mode == SCENE_MODE_HDR_INT) {
                modes.add("" + mode);
            }
        }
        return modes;
    }

    private List<String> getSupportedFlashModes(int cameraId) {
        int[] flashModes = mCharacteristics.get(cameraId).get(CameraCharacteristics
                .CONTROL_AE_AVAILABLE_MODES);
        List<String> modes = new ArrayList<>();
        for (int mode : flashModes) {
            modes.add("" + mode);
        }
        return modes;
    }

    private boolean isFlashAvailable(int cameraId) {
        if (mCharacteristics.size() > 0) {
            return mCharacteristics.get(cameraId).get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
        } else {
            return false;
        }
    }

    public StreamConfigurationMap getStreamConfigurationMap(int cameraId){
        return mCharacteristics.get(cameraId)
                .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
    }

    public List<String> getSupportedColorEffects(int cameraId) {
        List<String> modes = new ArrayList<>();
        if (mCharacteristics.size() > 0) {
            int[] flashModes = mCharacteristics.get(cameraId).get(CameraCharacteristics
                    .CONTROL_AVAILABLE_EFFECTS);
            for (int mode : flashModes) {
                modes.add("" + mode);
            }
        }
        return modes;
    }

    private List<String> getSupportedIso(int cameraId) {
        CameraCharacteristics cameraCharacteristics = mCharacteristics.get(cameraId);
        List<String> supportedIso = new ArrayList<>();
        supportedIso.add("auto");

        try {
            int[] modes = cameraCharacteristics.get(CaptureModule.ISO_AVAILABLE_MODES);
            if (modes != null) {
                for (int iso : modes) {
                    for (String key : KEY_ISO_INDEX.keySet()) {
                        if (KEY_ISO_INDEX.get(key).equals(iso)) {
                            supportedIso.add(key);
                        }
                    }
                }
            } else {
                Log.w(TAG, "Supported ISO range is null.");
            }
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "IllegalArgumentException Supported ISO_AVAILABLE_MODES is wrong.");

            Range<Integer> range = mCharacteristics.get(cameraId).get(
                    CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE);
            if (range != null) {
                int max = range.getUpper();
                int value = 50;
                while (value <= max) {
                    if (range.contains(value)) {
                        supportedIso.add(String.valueOf(value));
                    }
                    value += 50;
                }
            } else {
                Log.w(TAG, "Supported ISO range is null.");
            }
        }

        return supportedIso;
    }

    private boolean isCurrentVideoResolutionSupportedByEncoder(MediaCodecInfo info) {
        boolean supported = false;
        ListPreference videoQuality = mPreferenceGroup.findPreference(KEY_VIDEO_QUALITY);
        if (videoQuality == null) return supported;
        String videoSizeStr = videoQuality.getValue();
        if (videoSizeStr != null) {
            Size videoSize = parseSize(videoSizeStr);
            String[] supportedTypes = info.getSupportedTypes();
            MediaCodecInfo.VideoCapabilities capabilities = null;
            for (String type : supportedTypes) {
                if (type.equalsIgnoreCase(MediaFormat.MIMETYPE_VIDEO_MPEG4)
                        || type.equalsIgnoreCase(MediaFormat.MIMETYPE_VIDEO_H263)
                        || type.equalsIgnoreCase(MediaFormat.MIMETYPE_VIDEO_AVC)
                        || type.equalsIgnoreCase(MediaFormat.MIMETYPE_VIDEO_HEVC)) {
                    capabilities = info.getCapabilitiesForType(type).getVideoCapabilities();
                    if (capabilities == null ||
                            !capabilities.getSupportedWidths().contains(videoSize.getWidth()) ||
                            !capabilities.getSupportedWidths().contains(videoSize.getHeight())) {
                        return false;
                    } else {
                        supported = true;
                    }
                }
            }
        }
        return supported;
    }

    private List<String> getSupportedVideoEncoders() {
        ArrayList<String> supported = new ArrayList<String>();
        supported.add(SettingTranslation.getVideoEncoder(MediaRecorder.VideoEncoder.DEFAULT));
        String str = null;
        MediaCodecList list = new MediaCodecList(MediaCodecList.ALL_CODECS);
        MediaCodecInfo[] codecInfos = list.getCodecInfos();
        for (MediaCodecInfo info: codecInfos) {
            if (!info.isEncoder() || info.getName().contains("google")) continue;
            Log.d(TAG,"name="+info.getName());
            if (info.getSupportedTypes().length > 0 && info.getSupportedTypes()[0] != null){
                for (String t : info.getSupportedTypes()){
                    Log.d(TAG,"type="+t);
                }
                int type = SettingTranslation.getVideoEncoderType(info.getSupportedTypes()[0]);
                if (type != -1){
                    str = SettingTranslation.getVideoEncoder(type);
                    Log.d(TAG,"type="+type+" str="+str);
                    if (isCurrentVideoResolutionSupportedByEncoder(info)) {
                        supported.add(str);
                    }
                }
            }
        }
        return supported;
    }

    private static List<String> getSupportedAudioEncoders(CharSequence[] strings) {
        ArrayList<String> supported = new ArrayList<>();
        for (CharSequence cs: strings) {
            String s = cs.toString();
            int value = SettingTranslation.getAudioEncoder(s);
            if (value != SettingTranslation.NOT_FOUND) supported.add(s);
        }
        return supported;
    }

    public List<String> getSupportedNoiseReductionModes(int cameraId) {
        int[] noiseReduction = mCharacteristics.get(cameraId).get(CameraCharacteristics
                .NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES);
        List<String> modes = new ArrayList<>();
        if (noiseReduction != null) {
            for (int mode : noiseReduction) {
                String str = SettingTranslation.getNoiseReduction(mode);
                if (str != null) modes.add(str);
            }
        }
        return modes;
    }

    private  List<String> getSupportedZoomLevel(int cameraId) {
        float maxZoom = mCharacteristics.get(cameraId).get(CameraCharacteristics
                .SCALER_AVAILABLE_MAX_DIGITAL_ZOOM);
        ArrayList<String> supported = new ArrayList<String>();
        for (int zoomLevel = 0; zoomLevel <= maxZoom; zoomLevel++) {
            supported.add(String.valueOf(zoomLevel));
        }
        return supported;
    }

    private void resetIfInvalid(ListPreference pref) {
        // Set the value to the first entry if it is invalid.
        String value = pref.getValue();
        if (pref.findIndexOfValue(value) == -1) {
            pref.setValueIndex(0);
        }
    }

    private boolean filterSimilarPictureSize(PreferenceGroup group,
                                                    ListPreference pref) {
        pref.filterDuplicated();
        if (pref.getEntries().length <= 1) {
            removePreference(group, pref.getKey());
            return true;
        }
        resetIfInvalid(pref);
        return false;
    }

    public List<String> getSupportedInstantAecAvailableModes(int cameraId) {
        List<String> modes = new ArrayList<>();

        try {
            if (mCharacteristics.size() > 0) {
                int[] instantAecAvailableModes = mCharacteristics.get(cameraId).get(
                        CaptureModule.InstantAecAvailableModes);
                if (instantAecAvailableModes == null) {
                    return null;
                }
                for (int i : instantAecAvailableModes) {
                    modes.add("" + i);
                }
            }
        } catch(IllegalArgumentException e) {
            Log.w(TAG, "Supported instant aec modes is null.");
        }

        return  modes;
    }

    public boolean getQcfaPrefEnabled() {
        ListPreference qcfaPref = mPreferenceGroup.findPreference(KEY_QCFA);
        String qcfa = qcfaPref.getValue();
        if(qcfa != null && qcfa.equals("enable")) {
            return true;
        }
        return false;
    }

    public boolean getDeepportraitEnabled() {
        String dp = getValue(KEY_SCENE_MODE);
        if( dp!= null && Integer.valueOf(dp) == SCENE_MODE_DEEPPORTRAIT_INT) {
            return true;
        }
        return false;
    }

    public boolean getIsSupportedQcfa (int cameraId) {
        byte isSupportQcfa = 0;
        try {
            isSupportQcfa = mCharacteristics.get(cameraId).get(
                    CaptureModule.IS_SUPPORT_QCFA_SENSOR);
        }catch(Exception e) {
        }
        return isSupportQcfa == 1 ? true : false;
    }

    public String getSupportedQcfaDimension(int cameraId) {
        int[] qcfaDimension = mCharacteristics.get(cameraId).get(
                CaptureModule.QCFA_SUPPORT_DIMENSION);
        if (qcfaDimension == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < qcfaDimension.length; i ++) {
            sb.append(qcfaDimension[i]);
            if (i == 0) {
                sb.append("x");
            }
        }
        return  sb.toString();
    }

    public Size getQcfaSupportSize() {
        String qcfaSize = getSupportedQcfaDimension(mCameraId);
        if (qcfaSize != null) {
            return parseSize(getSupportedQcfaDimension(mCameraId));
        }
        return new Size(0, 0);
    }

    public List<String> getSupportedSaturationLevelAvailableModes(int cameraId) {
        int[] saturationLevelAvailableModes = {0,1,2,3,4,5,6,7,8,9,10};
        List<String> modes = new ArrayList<>();
        for (int i : saturationLevelAvailableModes) {
            modes.add(""+i);
        }
        return  modes;
    }


    public List<String> getSupportedAntiBandingLevelAvailableModes(int cameraId) {
        int[] antiBandingLevelAvailableModes = mCharacteristics.get(cameraId).get(
                CameraCharacteristics.CONTROL_AE_AVAILABLE_ANTIBANDING_MODES);
        List<String> modes = new ArrayList<>();
        for (int i : antiBandingLevelAvailableModes) {
            modes.add(""+i);
        }
        return  modes;
    }

    public List<String> getSupportedHdrAvailableModes(int cameraId) {
        String[] data = {"enable","disable"};
        List<String> modes = new ArrayList<>();
        for (String i : data) {
            modes.add(i);
        }
        return  modes;
    }

    public List<String> getSupportedVideoEncoderProfile(String videoEncoder) {
        List<String> profile = new ArrayList<>();
        profile.add("off");
        if ( VIDEO_ENCODER_PROFILE_TABLE.containsKey(videoEncoder) ) {
            profile.addAll(VIDEO_ENCODER_PROFILE_TABLE.get(videoEncoder));
        }
        return profile;
    }



    public boolean isCamera2HDRSupport(){
        String value = getValue(KEY_HDR);
        return value != null && value.equals("enable");
    }

    public int getSavePictureFormat() {
        String value = getValue(SettingsManager.KEY_PICTURE_FORMAT);
        if (value == null) return 0;
        return Integer.valueOf(value);
    }

    public boolean isHeifWriterSupported() {
        String product = Build.PRODUCT;
        switch (product) {
            case "atoll" :
            case "msmnile" :
            case "sm6150" :
                return true;
        }
        return false;
    }

    public boolean isHeifWriterEncoding() {
        return getSavePictureFormat() == HEIF_FORMAT && mHeifWriterSupported;
    }

    public boolean isHeifHALEncoding() {
        return getSavePictureFormat() == HEIF_FORMAT && !mHeifWriterSupported;
    }

    public List<String> getSupportedPictureFormat(int cameraId) {
        byte supportHeic = 1;
        try {
            supportHeic = mCharacteristics.get(cameraId).get(CaptureModule.heic_support_enable);
        } catch (Exception e) {
        }

        ArrayList<String> ret = new ArrayList<String>();
        ret.add(String.valueOf(SettingsManager.JPEG_FORMAT));
        if (supportHeic == 1) {
            ret.add(String.valueOf(SettingsManager.HEIF_FORMAT));
        }
        return ret;
    }

    public boolean isSATCamera(int cameraId){
        try{
            int type = mCharacteristics.get(cameraId).get(CaptureModule.logical_camera_type);
            return type == CaptureModule.TYPE_SAT;
        }catch (Exception e){
        }
        return false;
    }

    public boolean isZSLInHALEnabled(){
        String value = getValue(KEY_ZSL);
        String halZSLValue = mContext.getString(R.string.pref_camera2_zsl_entryvalue_hal_zsl);
        if ( value != null && value.equals(halZSLValue) ){
            return true;
        }else{
            return false;
        }
    }

    public boolean isZSLInAppEnabled(){
        String value = getValue(KEY_ZSL);
        String appZSLValue = mContext.getString(R.string.pref_camera2_zsl_entryvalue_app_zsl);
        if ( value != null && value.equals(appZSLValue)){
            return true;
        }else{
            return false;
        }
    }

    public void filterVideoDuration() {
        ListPreference videoDuration = mPreferenceGroup.findPreference(KEY_VIDEO_DURATION);
        videoDuration.reloadInitialEntriesAndEntryValues();
        if (filterUnsupportedOptions(videoDuration, getSupportedVideoDuration())) {
            mFilteredKeys.add(videoDuration.getKey());
        }
    }

    public void filterVideoDurationFor480fps() {
        ListPreference videoDuration = mPreferenceGroup.findPreference(KEY_VIDEO_DURATION);
        videoDuration.reloadInitialEntriesAndEntryValues();
        if (filterUnsupportedOptions(videoDuration, getSupportedVideoDurationFor480())) {
            mFilteredKeys.add(videoDuration.getKey());
        }
    }

    public void filterPictureFormatByIntent(int captureMode){
        ListPreference pictureFormat = mPreferenceGroup.findPreference(KEY_PICTURE_FORMAT);
        if (pictureFormat != null){
            if (captureMode != CaptureModule.INTENT_MODE_NORMAL) {
                String[] formats = mContext.getResources().getStringArray(
                        R.array.pref_camera2_picture_format_entryvalues);
                List<String> avaliableFormat = new ArrayList<String>();
                if (formats != null && formats[0] != null){
                    avaliableFormat.add(formats[0]);
                    if (filterUnsupportedOptions(pictureFormat,
                            avaliableFormat)) {
                        mFilteredKeys.add(pictureFormat.getKey());
                    }
                }
            }
        }
    }

    private boolean filterUnsupportedOptions(ListPreference pref, List<String> supported) {
        // Remove the preference if the parameter is not supported
        if (supported == null) {
            removePreference(mPreferenceGroup, pref.getKey());
            return true;
        }
        pref.filterUnsupported(supported);
        if (pref.getEntries().length <= 0) {
            removePreference(mPreferenceGroup, pref.getKey());
            return true;
        }

        resetIfInvalid(pref);
        return false;
    }

    public interface Listener {
        void onSettingsChanged(List<SettingState> settings);
    }

    static class Values {
        String value;
        String overriddenValue;

        Values(String value, String overriddenValue) {
            this.value = value;
            this.overriddenValue = overriddenValue;
        }
    }

    static class SettingState {
        String key;
        Values values;

        SettingState(String key, Values values) {
            this.key = key;
            this.values = values;
        }
    }

    public List<String> getDependentKeys(String key) {
        List<String> list = null;
        String value = getValue(key);
        JSONObject dependencies = getDependencyList(key, value);
        if (dependencies != null) {
            list = new ArrayList<>();
            Iterator<String> it = dependencies.keys();
            while (it.hasNext()) {
                list.add(it.next());
            }
        }
        return list;
    }

    private JSONObject parseJson(String fileName) {
        String json;
        try {
            InputStream is = mContext.getAssets().open(fileName);
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            json = new String(buffer, "UTF-8");
            return new JSONObject(json);
        } catch (IOException ex) {
            ex.printStackTrace();
            return null;
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }

    private JSONObject getDependencyMapForKey(String key) {
        if (mDependency == null) return null;
        try {
            return mDependency.getJSONObject(key);
        } catch (JSONException e) {
            if (DEBUG) {
                Log.w(TAG, "getDependencyMapForKey JSONException No value for:" + key);
            }
            return null;
        }
    }

    private JSONObject getDependencyList(String key, String value) {
        JSONObject dependencyMap = getDependencyMapForKey(key);
        if (dependencyMap == null) return null;
        if (!dependencyMap.has(value)) value = "default";
        if (!dependencyMap.has(value)) return null;
        value = getDependencyKey(dependencyMap, value);
        try {
            return dependencyMap.getJSONObject(value);
        } catch (JSONException e) {
            if (DEBUG) {
                Log.w(TAG, "getDependencyList JSONException No value for:" + key);
            }
            return null;
        }
    }

    private String getDependencyKey(JSONObject dependencyMap, String value) {
        if (!dependencyMap.has(value)) value = "default";
        return value;
    }

    public void restoreSettings() {
        clearPerCameraPreferences();
        mValuesMap.clear();
        if(mValuesMap != null) mValuesMap = null;
        mCaptureModule.restoreCameraIds();
        init();
    }

    public boolean isSWMFNRSupported(){
        boolean isSWMFNRSupported = false;
        try {
            isSWMFNRSupported = mCharacteristics.get(mCameraId).get(CaptureModule.mfnr_type) == CaptureModule.MFNRSupportValues.SWMFNRSupport.ordinal();
            Log.i(TAG,"mfnr type:" + mCharacteristics.get(mCameraId).get(CaptureModule.mfnr_type));
            Log.i(TAG,"total space:" + Storage.getTotalSpace());
            isSWMFNRSupported = isSWMFNRSupported && (Storage.getTotalSpace() > 2*1024*1024);
        } catch (IllegalArgumentException| NullPointerException e) {
            Log.d(TAG, "isSWMFNRSupported no vendor tag");
        }
        return isSWMFNRSupported;
    }

    public boolean isMFNREnabled() {
        boolean mfnrEnable = false;
        String mfnrValue = getValue(SettingsManager.KEY_CAPTURE_MFNR_VALUE);
        if (mfnrValue != null) {
            mfnrEnable = mfnrValue.equals("1");
        }
        return mfnrEnable;
    }

    private void clearPerCameraPreferences() {
        String[] preferencesNames = ComboPreferences.getSharedPreferencesNames(mContext, mPrepNameKeys);
        for ( String name : preferencesNames ) {
            SharedPreferences.Editor editor =
                    mContext.getSharedPreferences(name, Context.MODE_PRIVATE).edit();
            editor.clear();
            editor.commit();
        }

        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(mContext);
        boolean requestPermission = pref.getBoolean(CameraSettings.KEY_REQUEST_PERMISSION, false );
        SharedPreferences.Editor editor =
                PreferenceManager.getDefaultSharedPreferences(mContext).edit();
        editor.clear();
        editor.putBoolean(CameraSettings.KEY_REQUEST_PERMISSION, requestPermission);
        editor.commit();
    }

    public boolean isDeveloperEnabled() {
        SharedPreferences sp = mContext.getSharedPreferences(
                ComboPreferences.getGlobalSharedPreferencesName(mContext),
                Context.MODE_PRIVATE);
        return sp.getBoolean(SettingsManager.KEY_DEVELOPER_MENU, false);
    }

    private HashMap<String,VideoEisConfig> getVideoEisConfigs(int cameraId) {
        int[] configs = null;
        try{
            configs = mCharacteristics.get(cameraId).get(CaptureModule.eis_config_table);
        }catch (IllegalArgumentException e){

        }
        HashMap<String,VideoEisConfig> ret = new HashMap<>();
        if (configs == null || configs.length == 0 || configs.length%6 != 0)
            return null;
        for (int i=0; i < configs.length; i+=6){
            VideoEisConfig videoEisConfig = new VideoEisConfig();
            videoEisConfig.setVideoSize(new Size(configs[i],configs[i+1]));
            videoEisConfig.setMaxPreviewFPS(configs[i+2]);
            videoEisConfig.setVideoFPS(configs[i+3]);
            videoEisConfig.setLiveshotSupported(configs[i+4] == 1);
            videoEisConfig.setEISSupported(configs[i+5] == 1);
            String key =VideoEisConfig.getKey(videoEisConfig.getVideoSize(),videoEisConfig.getVideoFPS());
            ret.put(key,videoEisConfig);
        }
        return ret;
    }

    public VideoEisConfig getVideoEisConfig(Size size,int FPS){
        String key = VideoEisConfig.getKey(size,FPS);
        if (mVideoEisConfigs != null){
            return mVideoEisConfigs.get(key);
        }
        return null;
    }

    public Size getVideoSize(){
        Size videoSize;
        String videoSizeString = getValue(SettingsManager.KEY_VIDEO_QUALITY);
        videoSize = parsePictureSize(videoSizeString);
        Point videoSize2 = PersistUtil.getCameraVideoSize();
        if (videoSize2 != null) {
            videoSize = new Size(videoSize2.x, videoSize2.y);
        }
        return videoSize;
    }

    public Size parsePictureSize(String value) {
        int indexX = value.indexOf('x');
        int width = Integer.parseInt(value.substring(0, indexX));
        int height = Integer.parseInt(value.substring(indexX + 1));
        return new Size(width, height);
    }

    public boolean isLiveshotSupported(Size videoSize, int fps){
        if(CaptureModule.CURRENT_MODE == CaptureModule.CameraMode.HFR && fps > 60){
            return false;
        }
        if (PersistUtil.isPersistVideoLiveshot())
            return true;
        SettingsManager.VideoEisConfig config =
                getVideoEisConfig(videoSize,fps);
        if(config != null ){
            return config.isLiveshotSupported();
        }
        return true;
    }

    public boolean isEISSupported(Size videoSize,int fps){
        if (PersistUtil.isPersistVideoEis())
            return true;
        SettingsManager.VideoEisConfig config =
                getVideoEisConfig(videoSize,fps);
        if(config != null){
            return config.isEISSupported();
        }
        return true;
    }

    public int getVideoPreviewFPS(Size videoSize,int fps) {
        int previewFPS = 60;
        SettingsManager.VideoEisConfig config =
                getVideoEisConfig(videoSize,fps);
        if (config != null)
            previewFPS = config.getMaxPreviewFPS();
        Log.d(TAG,"videoSize="+videoSize.toString()+" fps="+fps+ " previewFPS="+previewFPS);
        return previewFPS;
    }

    public int getVideoFPS(){
        String fpsStr = getValue(SettingsManager.KEY_VIDEO_HIGH_FRAME_RATE);
        int fpsRate = 30;
        if (fpsStr != null && !fpsStr.equals("off")) {
            fpsRate = Integer.parseInt(fpsStr.substring(3));
        }
        return fpsRate;
    }

    public static class VideoEisConfig{
        private Size mVideoSize;
        private int mVideoFPS;
        private int mMaxPreviewFPS;
        private boolean mIsLiveshotSupported;
        private boolean mIsEISSupported;

        public Size getVideoSize() {
            return mVideoSize;
        }

        public void setVideoSize(Size mVideoSize) {
            this.mVideoSize = mVideoSize;
        }

        public int getVideoFPS() {
            return mVideoFPS;
        }

        public void setVideoFPS(int mVideoFPS) {
            this.mVideoFPS = mVideoFPS;
        }

        public int getMaxPreviewFPS() {
            return mMaxPreviewFPS;
        }

        public void setMaxPreviewFPS(int mMaxPreviewFPS) {
            this.mMaxPreviewFPS = mMaxPreviewFPS;
        }

        public boolean isLiveshotSupported() {
            return mIsLiveshotSupported;
        }

        public void setLiveshotSupported(boolean mIsLiveshotSupported) {
            this.mIsLiveshotSupported = mIsLiveshotSupported;
        }

        public boolean isEISSupported() {
            return mIsEISSupported;
        }

        public void setEISSupported(boolean mIsEISSupported) {
            this.mIsEISSupported = mIsEISSupported;
        }

        public static String getKey(Size size,int FPS){
            return size.toString()+"-"+String.valueOf(FPS);
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append(" VideoSize="+mVideoSize.toString());
            builder.append(" VideoFPS="+mVideoFPS);
            builder.append(" LiveshotSupported="+mIsLiveshotSupported);
            builder.append(" EISSupported="+mIsEISSupported);
            return builder.toString();
        }
    }

}
