/*
 * Copyright (c) 2014, The Linux Foundation. All rights reserved.
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.app.Activity;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import com.android.camera.CameraPreference.OnPreferenceChangedListener;

public class MenuController {

    private static String TAG = "CAM_menucontrol";

    protected Activity mActivity;
    protected PreferenceGroup mPreferenceGroup;
    protected OnPreferenceChangedListener mListener;
    protected List<ListPreference> mPreferences;
    private Map<ListPreference, View> mPreferenceMap;
    private Map<ListPreference, String> mOverrides;

    public void setListener(OnPreferenceChangedListener listener) {
        mListener = listener;
    }

    public MenuController(Activity activity) {
        mActivity = activity;
        mPreferences = new ArrayList<>();
        mPreferenceMap = new HashMap<>();
        mOverrides = new HashMap<>();
    }

    public void initialize(PreferenceGroup group) {
        mPreferenceMap.clear();
        setPreferenceGroup(group);
        mPreferences.clear();
        mOverrides.clear();
    }

    public void onSettingChanged(ListPreference pref) {
        if (mListener != null) {
            mListener.onSharedPreferenceChanged(pref);
        }
    }

    protected void setCameraId(int cameraId) {
        ListPreference pref = mPreferenceGroup.findPreference(CameraSettings.KEY_CAMERA_ID);
        pref.setValue(String.valueOf(cameraId));
    }

    public void setPreferenceGroup(PreferenceGroup group) {
        mPreferenceGroup = group;
    }

    public void reloadPreferences() {
        mPreferenceGroup.reloadValue();
        for (ListPreference pref : mPreferences) {
            if (pref instanceof IconListPreference) {
                reloadPreference((IconListPreference) pref);
            }
        }
    }

    protected void reloadPreference(IconListPreference pref) {
        View switcher = mPreferenceMap.get(pref);
        if (switcher == null || pref.getUseSingleIcon()) return;
        String overrideValue = mOverrides.get(pref);
        int index;
        if (overrideValue == null) {
            index = pref.findIndexOfValue(pref.getValue());
        } else {
            index = pref.findIndexOfValue(overrideValue);
            if (index == -1) {
                // Avoid the crash if camera driver has bugs.
                Log.e(TAG, "Fail to find override value=" + overrideValue);
                pref.print();
                return;
            }
        }
        ((ImageView) switcher).setImageResource(pref.getLargeIconIds()[index]);
    }

    // Scene mode may override other camera settings (ex: flash mode).
    public void overrideSettings(final String... keyvalues) {
        if (keyvalues.length % 2 != 0) {
            throw new IllegalArgumentException();
        }
        for (ListPreference pref : mPreferences) {
            override(pref, keyvalues);
        }
    }

    private void override(ListPreference pref, final String... keyvalues) {
        mOverrides.remove(pref);
        for (int i = 0; i < keyvalues.length; i += 2) {
            String key = keyvalues[i];
            String value = keyvalues[i + 1];
            if (key.equals(pref.getKey())) {
                mOverrides.put(pref, value);
                break;
            }
        }
        if (pref instanceof IconListPreference) {
            reloadPreference((IconListPreference) pref);
        }
    }

    protected void initSwitchItem(final String prefKey, View switcher) {
        final ListPreference pref = mPreferenceGroup.findPreference(prefKey);
        if (pref == null) return;

        if (pref instanceof IconListPreference) {
            IconListPreference iconPref = (IconListPreference) pref;
            int[] iconIds = iconPref.getLargeIconIds();
            int resid;
            int index = iconPref.findIndexOfValue(iconPref.getValue());
            if (!iconPref.getUseSingleIcon() && iconIds != null) {
                // Each entry has a corresponding icon.
                resid = iconIds[index];
            } else {
                // The preference only has a single icon to represent it.
                resid = iconPref.getSingleIcon();
            }
            ((ImageView) switcher).setImageResource(resid);
        }
        switcher.setVisibility(View.VISIBLE);
        mPreferences.add(pref);
        mPreferenceMap.put(pref, switcher);
        switcher.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int index = pref.findIndexOfValue(pref.getValue());
                CharSequence[] values = pref.getEntryValues();
                index = (index + 1) % values.length;
                pref.setValueIndex(index);
                if (pref instanceof IconListPreference) {
                    IconListPreference iconPref = (IconListPreference) pref;
                    if (!iconPref.getUseSingleIcon()) {
                        ((ImageView) v).setImageResource(iconPref.getLargeIconIds()[index]);
                    }
                }
                if (prefKey.equals(CameraSettings.KEY_CAMERA_ID)) {
                    mListener.onCameraPickerClicked(index);
                }
                onSettingChanged(pref);
            }
        });
    }

}
