/*
 * Copyright (c) 2016, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of The Linux Foundation nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
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

package com.android.camera.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import com.android.camera.CaptureModule;
import com.android.camera.SettingsManager;

import co.aospa.camera.R;

public class FlashToggleButton extends RotateImageView {
    private SettingsManager mSettingsManager;
    private int[] cameraFlashIcon = {R.drawable.flash_off, R.drawable.flash_auto, R.drawable.flash};
    private int[] videoFlashIcon = {R.drawable.flash_off, R.drawable.flash};
    private int mIndex;
    private boolean mIsVideoFlash;
    private Context mContext;

    public FlashToggleButton(Context context) {
        super(context);
        mContext = context;
    }

    public FlashToggleButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
    }

    public void init(boolean videoFlash) {
        mIsVideoFlash = videoFlash;
        String key;
        if (mIsVideoFlash) {
            key = SettingsManager.KEY_VIDEO_FLASH_MODE;
        } else {
            key = SettingsManager.KEY_FLASH_MODE;
        }
        mSettingsManager = SettingsManager.getInstance();
        mIndex = mSettingsManager.getValueIndex(key);
        String redeye = mSettingsManager.getValue(SettingsManager.KEY_REDEYE_REDUCTION);
        String userSetting = mContext.getString(
                R.string.pref_camera_manual_exp_value_user_setting);
        String manualExposureMode = mSettingsManager.getValue(SettingsManager.KEY_MANUAL_EXPOSURE);
        if (mIndex == -1 || (redeye != null && redeye.equals("on")) ||
                manualExposureMode.equals(userSetting) ||
                CaptureModule.CURRENT_MODE == CaptureModule.CameraMode.PRO_MODE ||
                CaptureModule.CURRENT_MODE == CaptureModule.CameraMode.RTB ||
                CaptureModule.CURRENT_MODE == CaptureModule.CameraMode.SAT) {
            setVisibility(GONE);
            return;
        } else {
            setVisibility(VISIBLE);
        }

        update();
        this.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                int[] icons;
                String key;
                if (mIsVideoFlash) {
                    icons = videoFlashIcon;
                    key = SettingsManager.KEY_VIDEO_FLASH_MODE;
                } else {
                    icons = cameraFlashIcon;
                    key = SettingsManager.KEY_FLASH_MODE;
                }
                mIndex = (mIndex + 1) % icons.length;
                mSettingsManager.setValueIndex(key, mIndex);
                update();
            }
        });
    }

    private void update() {
        int[] icons;
        if (mIsVideoFlash) {
            icons = videoFlashIcon;
        } else {
            icons = cameraFlashIcon;
        }
        setImageResource(icons[mIndex]);
    }
}
