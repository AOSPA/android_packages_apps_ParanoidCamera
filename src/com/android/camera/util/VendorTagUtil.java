/*
 * Copyright (c) 2017, The Linux Foundation. All rights reserved.
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
package com.android.camera.util;

import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CaptureRequest;
import android.util.Log;

public class VendorTagUtil {
    private static final String TAG = "VendorTagUtil";

    private static CaptureRequest.Key<Integer> CdsModeKey =
            new CaptureRequest.Key<>("org.codeaurora.qcamera3.CDS.cds_mode",
                    Integer.class);
    private static CaptureRequest.Key<Byte> JpegCropEnableKey =
            new CaptureRequest.Key<>("org.codeaurora.qcamera3.jpeg_encode_crop.enable",
                    Byte.class);
    private static CaptureRequest.Key<int[]> JpegCropRectKey =
            new CaptureRequest.Key<>("org.codeaurora.qcamera3.jpeg_encode_crop.rect",
                    int[].class);
    private static CaptureRequest.Key<int[]> JpegRoiRectKey =
            new CaptureRequest.Key<>("org.codeaurora.qcamera3.jpeg_encode_crop.roi",
                    int[].class);
    private static CaptureRequest.Key<Integer> SELECT_PRIORITY =
            new CaptureRequest.Key<>("org.codeaurora.qcamera3.iso_exp_priority.select_priority",
                    Integer.class);
    private static CaptureRequest.Key<Long> ISO_EXP =
            new CaptureRequest.Key<>("org.codeaurora.qcamera3.iso_exp_priority.use_iso_exp_priority",
                    Long.class);
    private static CaptureRequest.Key<Integer> USE_ISO_VALUE =
            new CaptureRequest.Key<>("org.codeaurora.qcamera3.iso_exp_priority.use_iso_value",
                    Integer.class);
    private static CaptureRequest.Key<Integer> WB_COLOR_TEMPERATURE =
            new CaptureRequest.Key<>("org.codeaurora.qcamera3.manualWB.color_temperature",
                    Integer.class);
    private static CaptureRequest.Key<float[]> MANUAL_WB_GAINS =
            new CaptureRequest.Key<>("org.codeaurora.qcamera3.manualWB.gains", float[].class);
    private static CaptureRequest.Key<Integer> PARTIAL_MANUAL_WB_MODE =
            new CaptureRequest.Key<>("org.codeaurora.qcamera3.manualWB.partial_mwb_mode", Integer.class);
    private static CaptureRequest.Key<Byte> HDRVideoMode =
            new CaptureRequest.Key<>("org.quic.camera2.streamconfigs.HDRVideoMode", Byte.class);
    private static CaptureRequest.Key<Float> TONE_MAPPING_DARK_BOOST =
            new CaptureRequest.Key<>("org.codeaurora.qcamera3.tmcusercontrol.dark_boost_offset", Float.class);
    private static CaptureRequest.Key<Float> TONE_MAPPING_FOURTH_TONE =
            new CaptureRequest.Key<>("org.codeaurora.qcamera3.tmcusercontrol.fourth_tone_anchor", Float.class);

    private static final int MANUAL_WB_DISABLE_MODE = 0;
    private static final int MANUAL_WB_CCT_MODE = 1;
    private static final int MANUAL_WB_GAINS_MODE = 2;

    private static boolean isSupported(CaptureRequest.Builder builder,
                                       CaptureRequest.Key<?> key) {
        boolean supported = true;
        try {
            builder.get(key);
        }catch(IllegalArgumentException exception){
            supported = false;
            Log.d(TAG, "vendor tag " + key.getName() + " is not supported");
            //exception.printStackTrace();
        }
        if ( supported ) {
            Log.d(TAG, "vendor tag " + key.getName() + " is supported");
        }
        return supported;
    }

    // value=0:OFF
    // value=1:ON
    // value=2:AUTO
    public static void setCdsMode(CaptureRequest.Builder builder, Integer value) {
        if ( isCdsModeSupported(builder) ) {
            builder.set(CdsModeKey, value);
        }
    }

    private static boolean isCdsModeSupported(CaptureRequest.Builder builder) {
        return isSupported(builder, CdsModeKey);
    }

    public static void setJpegCropEnable(CaptureRequest.Builder builder, Byte value) {
        if ( isJpegCropEnableSupported(builder) ) {
            builder.set(JpegCropEnableKey, value);
        }
    }

    private static boolean isJpegCropEnableSupported(CaptureRequest.Builder builder) {
        return isSupported(builder, JpegCropEnableKey);
    }

    public static void setJpegCropRect(CaptureRequest.Builder builder, int[] value) {
        if ( isJpegCropRectSupported(builder) ) {
            builder.set(JpegCropRectKey, value);
        }
    }

    private static boolean isJpegCropRectSupported(CaptureRequest.Builder builder) {
        return isSupported(builder, JpegCropRectKey);
    }

    public static void setJpegRoiRect(CaptureRequest.Builder builder, int[] value) {
        if ( isJpegRoiRectSupported(builder) ) {
            builder.set(JpegRoiRectKey, value);
        }
    }

    private static boolean isJpegRoiRectSupported(CaptureRequest.Builder builder) {
        return isSupported(builder, JpegRoiRectKey);
    }

    public static void setIsoExpPrioritySelectPriority(CaptureRequest.Builder builder,
                                                       Integer value) {
        if ( isIsoExpPrioritySelectPrioritySupported(builder) ) {
            builder.set(SELECT_PRIORITY, value);
        }
    }
    private static boolean isIsoExpPrioritySelectPrioritySupported(CaptureRequest.Builder builder) {
        return isSupported(builder, SELECT_PRIORITY);
    }

    public static void setIsoExpPriority(CaptureRequest.Builder builder,Long value) {
        if ( isIsoExpPrioritySupported(builder) ) {
            builder.set(ISO_EXP, value);
        }
    }
    public static void setUseIsoValues(CaptureRequest.Builder builder,int value) {
        if ( isUseIsoValueSupported(builder) ) {
            builder.set(USE_ISO_VALUE, value);
        }
    }
    private static boolean isIsoExpPrioritySupported(CaptureRequest.Builder builder) {
        return isSupported(builder, ISO_EXP);
    }

    private static boolean isUseIsoValueSupported(CaptureRequest.Builder builder) {
        return isSupported(builder, USE_ISO_VALUE);
    }

    private static boolean isPartialWBModeSupported(CaptureRequest.Builder builder) {
        return isSupported(builder, PARTIAL_MANUAL_WB_MODE);
    }

    private static boolean isWBTemperatureSupported(CaptureRequest.Builder builder) {
        return isSupported(builder, WB_COLOR_TEMPERATURE);
    }

    private static boolean isMWBGainsSupported(CaptureRequest.Builder builder) {
        return isSupported(builder, MANUAL_WB_GAINS);
    }

    public static void setWbColorTemperatureValue(CaptureRequest.Builder builder, Integer value) {
        if (isPartialWBModeSupported(builder)) {
            builder.set(PARTIAL_MANUAL_WB_MODE, MANUAL_WB_CCT_MODE);
            if (isWBTemperatureSupported(builder)) {
                builder.set(WB_COLOR_TEMPERATURE, value);
            }
        }
    }

    public static void setMWBGainsValue(CaptureRequest.Builder builder, float[] gains) {
        if (isPartialWBModeSupported(builder)) {
            builder.set(PARTIAL_MANUAL_WB_MODE, MANUAL_WB_GAINS_MODE);
            if (isMWBGainsSupported(builder)) {
                builder.set(MANUAL_WB_GAINS, gains);
            }
        }
    }

    public static void setMWBDisableMode(CaptureRequest.Builder builder) {
        if (isPartialWBModeSupported(builder)) {
            builder.set(PARTIAL_MANUAL_WB_MODE, MANUAL_WB_DISABLE_MODE);
        }
    }

    public static void setToneMappingDisableMode(CaptureRequest.Builder builder) {
        float defaultValue = -1.0f;
        setToneMappingDarkBoostValue(builder, defaultValue);
        setToneMappingFourthToneValue(builder, defaultValue);
    }

    public static void setToneMappingDarkBoostValue(CaptureRequest.Builder builder, float value) {
        if (isSupported(builder, TONE_MAPPING_DARK_BOOST)) {
            builder.set(TONE_MAPPING_DARK_BOOST, value);
        }
    }
    public static void setToneMappingFourthToneValue(CaptureRequest.Builder builder, float value) {
        if (isSupported(builder, TONE_MAPPING_FOURTH_TONE)) {
            builder.set(TONE_MAPPING_FOURTH_TONE, value);
        }
    }

    public static void setHDRVideoMode(CaptureRequest.Builder builder, byte mode) {
        if ( isHDRVideoModeSupported(builder) ) {
            builder.set(HDRVideoMode, mode);
        }
    }

    public static boolean isHDRVideoModeSupported(CaptureRequest.Builder builder) {
        return isSupported(builder, HDRVideoMode);
    }

    public static boolean isHDRVideoModeSupported(CameraDevice camera) {
        try {
            CaptureRequest.Builder builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            return isHDRVideoModeSupported(builder);
        }catch(CameraAccessException exception) {
            exception.printStackTrace();
            return false;
        }
    }
}
