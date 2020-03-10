/*
*Copyright (c) 2020, The Linux Foundation. All rights reserved.

*Redistribution and use in source and binary forms, with or without
*modification, are permitted provided that the following conditions are
*met:
    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above
      copyright notice, this list of conditions and the following
      disclaimer in the documentation and/or other materials provided
      with the distribution.
    * Neither the name of The Linux Foundation nor the names of its
      contributors may be used to endorse or promote products derived
      from this software without specific prior written permission.

*THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
*WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
*MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
*ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
*BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
*CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
*SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
*BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
*WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
*OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
*IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/
package com.android.camera;

import android.app.ActionBar;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.MultiSelectListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;

import com.android.camera.SettingsManager;

import co.aospa.camera.R;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class MultiSettingsActivity extends PreferenceActivity {

    /**
     * Keys for menu items
    **/
    public static final String CAMERA_MODULE = "camera_module";
    public static final String KEY_MULTI_CAMERAS_MODE = "pref_camera2_multi_cameras_key";
    public static final String KEY_FACE_DETECTION_MODE = "pref_multicamera_facedetection_key";
    public static final String KEY_HDR_MODE = "pref_multicamera_hdr_key";
    public static final String KEY_BURST_MODE = "pref_camera2_longshot_key";
    public static final String KEY_MFNR_MODE = "pref_camera2_capture_mfnr1_key";
    public static final String KEY_QCFA_MODE = "pref_camera2_qcfa_key";
    public static final String KEY_CAMERAS_TO_START = "pref_camera2_cameras_to_start_key";
    public static final String KEY_EIS_MODE = "pref_multi_camera_eis_key";
    private static final String TAG = "MultiSettingsActivity";
    private static final String VERSION_INFO = "multi_camera_version_info";

    /**
     * Array for data transfer between Menu activity and PIP Main activity
    **/
    short selectedOptions[] = {0,0,0,0,0,0,99,99,99,0};

    /**
     * Array for camera ids transfer between Menu activity and PIP Main activity
    **/
    short idStates[];


    Bundle metaData = new Bundle();
    Activity mActivity;

    /**
     * Camera features flags
    **/
    private boolean fd_check=false;
    private boolean hdr_check=false;
    private boolean mfnr_check=false;
    private boolean qcfa_check=false;
    private boolean burst_check=false;
    private int camera_num = 1;
    private boolean cam0_check=false;
    private boolean cam1_check=false;
    private boolean cam2_check=false;
    private boolean eis_check=false;
    private boolean photo_mode=false;
    private int cameraState = 0;
    private SharedPreferences mSharedPreferences;
    private SettingsManager mSettingsManager;

    /**
     * Listener for menu items actions
    **/
    private SharedPreferences.OnSharedPreferenceChangeListener mSharedPreferenceChangeListener
            = new SharedPreferences.OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
        String key) {
            Preference p = findPreference(key);
            if (p == null){
                return;
            }
            if (key.equals(KEY_FACE_DETECTION_MODE)) {
                Boolean value = ((SwitchPreference) p).isChecked();
                if (value) {
                    selectedOptions[0]=1;
                }
                else {
                    selectedOptions[0]=0;
                }
            }
            if (key.equals(KEY_HDR_MODE)) {
                Boolean value = ((SwitchPreference) p).isChecked();
                if (value) {
                    selectedOptions[1]=1;
                }
                else {
                    selectedOptions[1]=0;
                }
            }
            if (key.equals(KEY_BURST_MODE)) {
                Boolean value = ((SwitchPreference) p).isChecked();
                if (value) {
                    selectedOptions[4]=1;
                }
                else {
                    selectedOptions[4]=0;
                }
            }
            if (key.equals(KEY_MFNR_MODE)) {
                String value = ((ListPreference) p).getValue();
                String mfnrCamerasOff = MultiSettingsActivity.this.getResources().getString(
                        R.string.pref_camera2_capture_mfnr_entry_enable);
                if (value.equals(mfnrCamerasOff)) {
                    selectedOptions[2]=1;
                }
                else {
                    selectedOptions[2]=0;
                }
            }
            if (key.equals(KEY_QCFA_MODE)) {
                String value = ((ListPreference) p).getValue();
                String qcfaCamerasOff = MultiSettingsActivity.this.getResources().getString(
                        R.string.pref_camera_qcfa_value_enable);
                if (value.equals(qcfaCamerasOff)) {
                    selectedOptions[3]=1;
                }
                else {
                    selectedOptions[3]=0;
                }
            }
            if (key.equals(KEY_CAMERAS_TO_START)) {
                CharSequence[] getEntriesSeqence = ((MultiSelectListPreference)p).getEntries();
                Set<String> valueSet = ((MultiSelectListPreference)p).getValues();
                selectedOptions[5]=(short)valueSet.size();

                for(short i=0;i<idStates.length;i++){
                    if(!valueSet.contains(String.valueOf(idStates[i]))){
                        sendIDtoBack(i);
                    }
                }
            }
            if (key.equals(KEY_EIS_MODE)) {
                Boolean value = ((SwitchPreference) p).isChecked();
                if (value) {
                    selectedOptions[9]=1;
                }
                else {
                    selectedOptions[9]=0;
                }
            }
        }
    };

    private void sendIDtoBack(int index){
        for(int i=index;i<idStates.length-1;i++) {
            short swapVar = idStates[i];
            idStates[i]=idStates[i+1];
            idStates[i+1]=swapVar;
        }
    }

    /**
     * Entry-point into Menu activity
     * 1 - check for extra metadata
     * 2 - enable full screen and hide navigation bar
     * 3 - inflate menu members and set their states according to received metadata
    **/
    @Override
    public void onCreate(Bundle savedInstanceState) {
        mActivity = this;
//1
        if(this.getIntent().hasExtra("options")) {
            metaData=this.getIntent().getBundleExtra("options");
            selectedOptions=metaData.getShortArray("options");
            idStates=metaData.getShortArray("idState");

        }
        super.onCreate(savedInstanceState);
//2
        int flag = WindowManager.LayoutParams.FLAG_FULLSCREEN;
        Window window = getWindow();
        window.setFlags(flag, flag);
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle(getResources().getString(R.string.settings_title));
        }

        mSettingsManager = SettingsManager.getInstance();
        if (mSettingsManager == null) {
            finish();
            return;
        }

//3
        addPreferencesFromResource(R.xml.multi_setting_menu_preferences);

        mSharedPreferences = getPreferenceManager().getSharedPreferences();
        mSharedPreferences.registerOnSharedPreferenceChangeListener(mSharedPreferenceChangeListener)
        ;

        if(selectedOptions[0]==(short)0) {
            fd_check=false;
        }
        else {
            fd_check=true;
        }

        if(selectedOptions[1]==(short)0) {
            hdr_check=false;
        }
        else {
            hdr_check=true;
        }

        if(selectedOptions[2]==(short)0) {
            mfnr_check=false;
        }
        else {
            mfnr_check=true;
        }

        if(selectedOptions[3]==(short)0) {
            qcfa_check=false;
        }
        else {
            qcfa_check=true;
        }

        if(selectedOptions[4]==(short)0) {
            burst_check=false;
        }
        else {
            burst_check=true;
        }

        cameraState = selectedOptions[5];

        if(selectedOptions[6]==(short)0) {
            cam0_check=false;
        }
        else {
            cam0_check=true;
        }

        if(selectedOptions[7]==(short)0) {
            cam1_check=false;
        }
        else {
            cam1_check=true;
        }

        if(selectedOptions[8]==(short)0) {
            cam2_check=false;
        }
        else {
            cam2_check=true;
        }

        if(selectedOptions[9]==(short)0) {
            eis_check=false;
        }
        else {
            eis_check=true;
        }

        if(selectedOptions[10]==(short)0) {
            photo_mode=false;
        }
        else {
            photo_mode=true;
        }

        Preference p;
        p=findPreference(KEY_FACE_DETECTION_MODE);
        ((SwitchPreference) p).setChecked(fd_check);
        p=findPreference(KEY_HDR_MODE);
        ((SwitchPreference) p).setChecked(hdr_check);
        p=findPreference(KEY_BURST_MODE);
        ((SwitchPreference) p).setChecked(burst_check);
        p=findPreference(KEY_MFNR_MODE);
        ((ListPreference) p).setValue(mfnr_check ? "enable" : "disable");
        p=findPreference(KEY_QCFA_MODE);
        ((ListPreference) p).setValue(qcfa_check ? "enable" : "disable");

        p=findPreference(KEY_CAMERAS_TO_START);
        Set<String> valueSet = new HashSet<String>();
        CharSequence[] cameraNames = new CharSequence[idStates.length];
        for (int i=0;i<idStates.length;i++) {
            cameraNames[i]="Camera "+i;
            valueSet.add(String.valueOf(idStates[i]));
        }
        Set<String> selectedValueSet = new HashSet<String>();
        for(short j=0;j<selectedOptions[5];j++)
        {
            selectedValueSet.add(String.valueOf(idStates[j]));
        }

        ((MultiSelectListPreference)p).setEntries(cameraNames);
        ((MultiSelectListPreference)p).setValues(selectedValueSet);

        p=findPreference(KEY_EIS_MODE);
        ((SwitchPreference) p).setChecked(eis_check);

        filterPreferences();
        initializePreferences();

    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mSharedPreferences.unregisterOnSharedPreferenceChangeListener(mSharedPreferenceChangeListener);
    }

    @Override
    public void onBackPressed() {

        metaData.putShortArray("options",selectedOptions);
        Activity mActivity = this;
        Intent myIntent = new Intent(mActivity, MainActivity.class);
        myIntent.putExtra("options", metaData);
        myIntent.putExtra("idState", idStates);
        mActivity.startActivity(myIntent);
    }

    private void setShowInLockScreen() {
        // Change the window flags so that secure camera can show when locked 	146
        Window win = getWindow();
        WindowManager.LayoutParams params = win.getAttributes();
        params.flags |= WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED;
        win.setAttributes(params);
    }

    /**
     * Filter menu options to be displayed, according to selected group
    **/
    private void filterPreferences() {
        PreferenceGroup developer = (PreferenceGroup) findPreference("developer");
        PreferenceGroup photoPre = (PreferenceGroup) findPreference("photo");
        PreferenceGroup videoPre = (PreferenceGroup) findPreference("video");
        PreferenceScreen parentPre = getPreferenceScreen();

        if(!photo_mode){
                removePreferenceGroup("video", parentPre);
        } else{
            removePreferenceGroup("photo", parentPre);
        }

    }
    private void initializePreferences() {
        // Version Info
        try {
            String versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName
                    ;
            int index = versionName.indexOf(' ');
            versionName = versionName.substring(0, index);
            findPreference(VERSION_INFO).setSummary(versionName);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
    }

    private boolean removePreferenceGroup(String key, PreferenceScreen parentPreferenceScreen) {
        PreferenceGroup removePreference = (PreferenceGroup) findPreference(key);
        if (removePreference != null && parentPreferenceScreen != null) {
            parentPreferenceScreen.removePreference(removePreference);
            return true;
        }
        return false;
    }

}