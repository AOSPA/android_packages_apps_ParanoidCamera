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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuff.Mode;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Bundle;
import android.media.Image;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.android.camera.MultiSettingsActivity;
import com.android.camera.data.Camera2ModeAdapter;
import com.android.camera.data.Camera2ModeAdapter.OnItemClickListener;
import com.android.camera.ui.Camera2FaceView;
import com.android.camera.ui.CameraRootView;

import co.aospa.camera.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

    public class MainActivity extends FragmentActivity implements View.OnClickListener, View.OnTouchListener{

    /**
     * Key for detecting physical cameras
    **/
    public static CameraCharacteristics.Key<Byte> logical_camera_type =
            new CameraCharacteristics.Key<>("org.codeaurora.qcamera3.logicalCameraType.logical_camera_type", Byte.class);
    /**
     * physical camera type
    **/
    public static final int TYPE_DEFAULT = 0;
    /**
     * Bundle for data transfer when switching to menu and back
    **/
    Bundle receivedData;
    /**
     * array for data transfer
    **/
    short optionsArray[];
    /**
     * arrays for detected cameras
    **/
    String idsDetected[];
    short receivedIds[]=null;
    ArrayList<String> idList = new ArrayList<>();
    ArrayList<String> idListSelectedCameras = new ArrayList<>();
    /**
     * current activity
    **/
    Activity mActivity;

    String switcher;
    /**
     * initial options for first start
    **/
    short initialOptions[] = {0,0,0,0,0,1,1,0,0,0,0};
    // photo  - fd, hdr, mfnr, qcfa, burst, camera num, 0, 1, 2       ,photomode =0
    // video                                                    --eis, videomode =1

    /**
     * Buttons for UI
    **/
    private Button buttonLeftCam;
    private Button buttonRightCam;
    private Button buttonSettings;
    private Button Photo;
    private Button Video;
    private Button Burst;
    private Button buttonVideo;
    private Button captureButton;
    private Button buttonSDCswitch;
    private Button buttonLShot;

    /**
     * Objects for upto 3 concurrent cameras of either photo mode or video mode
    **/
    private PhotoMode Camera0V;
    private PhotoMode Camera1V;
    private PhotoMode Camera2V;
    private VideoMode Camera0Video;
    private VideoMode Camera1Video;
    private VideoMode Camera2Video;
    private TextView mTextView[] = new TextView[3];


    /**
     * flags for tracking of enabled features and video recording
    **/
    private boolean isRecording = false;
    private boolean videoCheck = false;
    private boolean videoClickCheck = false;
    private boolean settingsCheck = false;
    private boolean tripleCamCheck = false;
    private boolean qcfa_pressed=false;
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
    private int cameraNumberToStart = 0;

    /**
     * views for face detection surface and mode switcher container
    **/
    private Camera2FaceView mOverlayView;
    private RecyclerView mModeSelectLayout;
    private Camera2ModeAdapter mCameraModeAdapter;

    /**
     * Listener for mode switching RecyclerView
    **/
    public OnItemClickListener lstnr = new OnItemClickListener() {
            @Override
            public int onItemClick(int mode) {

                if (mode == 0) {Photo.callOnClick();}
                else {Video.callOnClick();}
                return 1;
            }
        };

    /**
     * Entry point of the app
     * 1 - check for extra data passed on start up from menu activity
     * 2 - if no extra data is available, use initial options and get available cameras
     * 3 - Inflate root view and face detection surface
     * 4 - call fullscreen for hiding navigation bar
     * 5 - set all flags with received or initial settings
     * 6 - Creating the slider for camera mode switching
     * 7 - buttons initialization
     * 8 - start cameras
    **/
    @Override
    protected void onCreate(Bundle savedInstanceState) {
//1
        mActivity=this;

        if(this.getIntent().hasExtra("initial_ids")) {
            ArrayList<CharSequence>  physicalCameras = new ArrayList<>();
            Bundle receiveIdsB = new Bundle();
            receiveIdsB=this.getIntent().getBundleExtra("initial_ids");
            physicalCameras=receiveIdsB.getCharSequenceArrayList("ids");
            idList = new ArrayList<>();
            for(int i=0;i<physicalCameras.size();i++) {
                idList.add(String.valueOf(physicalCameras.get(i)));
            }
        }

        if(this.getIntent().hasExtra("options")) {
            receivedData=this.getIntent().getBundleExtra("options");
            optionsArray=receivedData.getShortArray("options");
            receivedIds=receivedData.getShortArray("idState");
        }
//2
        else {
            optionsArray = initialOptions;
        }
        if(receivedIds!=null){
            idList = new ArrayList<>();
            for(int i=0;i<receivedIds.length;i++){
                idList.add(String.valueOf(receivedIds[i]));
            }
        }
//3
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);
        mOverlayView = (Camera2FaceView) findViewById(R.id.overlay_view);
        CameraManager manager = (CameraManager) this.getSystemService(Context.CAMERA_SERVICE);
//4
        FullScreencall();
//5
        if(optionsArray[0]==(short)0) {
            fd_check=false;
        }
        else {
            fd_check=true;
        }

        if(optionsArray[1]==(short)0) {
            hdr_check=false;
        }
        else {
            hdr_check=true;
        }

        if(optionsArray[2]==(short)0) {
            mfnr_check=false;
        }
        else {
            mfnr_check=true;
        }

        if(optionsArray[3]==(short)0) {
            qcfa_check=false;
        }
        else {
            qcfa_check=true;
        }

        if(optionsArray[4]==(short)0) {
            burst_check=false;
        }
        else {
            burst_check=true;
        }

        cameraNumberToStart = optionsArray[5];

        if(optionsArray[6]==(short)0) {
            cam0_check=false;
        }
        else {
            cam0_check=true;
        }

        if(optionsArray[7]==(short)0) {
            cam1_check=false;
        }
        else {
            cam1_check=true;
        }

        if(optionsArray[8]==(short)0) {
            cam2_check=false;
        }
        else {
            cam2_check=true;
        }

        if(optionsArray[9]==(short)0) {
            eis_check=false;
        }
        else {
            eis_check=true;
        }

        if(optionsArray[10]==(short)0) {
            videoCheck=false;
        }
        else {
            videoCheck=true;
        }
//6
        List<String> adapterList = new ArrayList<>();
        adapterList.add("Photo");
        adapterList.add("Video");
        mModeSelectLayout = (RecyclerView) findViewById(R.id.mode_select_layout);
        mModeSelectLayout.setLayoutManager(new LinearLayoutManager(this,
                LinearLayoutManager.HORIZONTAL, false));
        mCameraModeAdapter = new Camera2ModeAdapter(adapterList);
        mCameraModeAdapter.setSelectedPosition(optionsArray[10]);
        mCameraModeAdapter.setOnItemClickListener(lstnr);
        mModeSelectLayout.setAdapter(mCameraModeAdapter);

//7
        mTextView[0] = findViewById(R.id.textview0);
        mTextView[0].setVisibility(View.GONE);

        mTextView[1] = findViewById(R.id.textview1);
        mTextView[1].setVisibility(View.GONE);

        mTextView[2] = findViewById(R.id.textview2);
        mTextView[2].setVisibility(View.GONE);

        buttonLeftCam = (Button) findViewById(R.id.buttonLeftCam);
        buttonLeftCam.setOnClickListener(this);
        buttonLeftCam.setClickable(false);

        buttonRightCam = (Button) findViewById(R.id.buttonRightCam);
        buttonRightCam.setOnClickListener(this);
        buttonRightCam.setClickable(false);


        buttonVideo = (Button) findViewById(R.id.buttonVideo);
        buttonVideo.setOnClickListener(this);
        buttonVideo.setClickable(false);
        buttonVideo.setVisibility(View.GONE);

        buttonSettings = (Button) findViewById(R.id.settings);
        buttonSettings.setOnClickListener(this);
        buttonSettings.setClickable(true);
        buttonSettings.setVisibility(View.VISIBLE);

        buttonSDCswitch = (Button) findViewById(R.id.buttonSDC);
        buttonSDCswitch.setOnClickListener(this);

        captureButton = (Button) findViewById(R.id.shutter_button);
        captureButton.setOnClickListener(this);
        captureButton.setOnTouchListener(this);
        if(burst_check) {
            captureButton.setClickable(false);
            captureButton.setVisibility(View.GONE);
        }
        else
        {
            captureButton.setClickable(true);
            captureButton.setVisibility(View.VISIBLE);
        }

        Burst = (Button) findViewById(R.id.Burst);
        Burst.setOnClickListener(this);
        Burst.setOnTouchListener(this);
        if(!burst_check) {
            Burst.setClickable(false);
            Burst.setVisibility(View.GONE);
        }
        else
        {
            Burst.setClickable(true);
            Burst.setVisibility(View.VISIBLE);
        }

        buttonLShot = (Button) findViewById(R.id.buttonLShot);
        buttonLShot.setOnClickListener(this);
        buttonLShot.setOnTouchListener(this);
        buttonLShot.setClickable(false);
        buttonLShot.setVisibility(View.GONE);

        Photo = (Button) findViewById(R.id.Photo);
        Photo.setOnClickListener(this);
        Photo.setVisibility(View.GONE);

        Video = (Button) findViewById(R.id.Video);
        Video.setOnClickListener(this);
        Video.setVisibility(View.GONE);

//8

        if(optionsArray[10]==(short)0) {
            Photo.callOnClick();
        }
        else {
            Video.callOnClick();
        }
    }

    /**
     * Hiding the navigation bar
    **/
    public void FullScreencall() {
            View decorView = getWindow().getDecorView();
            int uiOptions = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
            decorView.setSystemUiVisibility(
                     View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
            );
    }

    /**
     * back button handle
    **/
    @Override
    public void onBackPressed(){
        FullScreencall();
        super.onBackPressed();
    }

    @Override
    public void  onAttachFragment(Fragment fragment){
        super.onAttachFragment(fragment);
    }

    @Override
    public void  onLowMemory(){
        super.onLowMemory();
    }

        /**
     * Starting and restarting the cameras
     * depending on camera mode and number of cameras
    **/
    public void restartCameras() {

        for (Fragment fragment : getSupportFragmentManager().getFragments()) {

            if (fragment != null) {
                getSupportFragmentManager().beginTransaction().remove(fragment).commitNow();
            }
        }

        switch(cameraNumberToStart){
            case (short)3:
                if(videoCheck){
                    getSupportFragmentManager().beginTransaction()
                            .add(R.id.container, Camera0Video = VideoMode.newInstance(idList.get(0), 0, eis_check, fd_check, mOverlayView,3))
                            .add(R.id.container, Camera1Video = VideoMode.newInstance(idList.get(1), 1, eis_check))
                            .add(R.id.container, Camera2Video = VideoMode.newInstance(idList.get(2), 2, eis_check)).commitNow();
                }
                else{
                    getSupportFragmentManager().beginTransaction()
                            .add(R.id.container, Camera0V = PhotoMode.newInstance(idList.get(0), 0, mOverlayView, fd_check, hdr_check, mfnr_check, qcfa_check, 3))
                            .add(R.id.container, Camera1V = PhotoMode.newInstance(idList.get(1), 1))
                            .add(R.id.container, Camera2V = PhotoMode.newInstance(idList.get(2), 2)).commitNow();
                }
                for(short i=0;i<cameraNumberToStart;i++){
                    mTextView[i].setText("Cam"+idList.get(i));
                    mTextView[i].setVisibility(View.VISIBLE);
                }
                buttonRightCam.setClickable(true);
                buttonLeftCam.setClickable(true);
                break;

            case (short)2:
                if(videoCheck){
                    getSupportFragmentManager().beginTransaction()
                            .add(R.id.container, Camera0Video = VideoMode.newInstance(idList.get(0), 0, eis_check, fd_check, mOverlayView,3))
                            .add(R.id.container, Camera1Video = VideoMode.newInstance(idList.get(1), 1, eis_check)).commitNow();
                }
                else{
                    getSupportFragmentManager().beginTransaction()
                            .add(R.id.container, Camera0V = PhotoMode.newInstance(idList.get(0), 0, mOverlayView, fd_check, hdr_check, mfnr_check, qcfa_check, 3))
                            .add(R.id.container, Camera1V = PhotoMode.newInstance(idList.get(1), 1)).commitNow();
                }
                for(short i=0;i<cameraNumberToStart;i++){
                    mTextView[i].setText("Cam"+idList.get(i));
                    mTextView[i].setVisibility(View.VISIBLE);
                }
                buttonRightCam.setClickable(false);
                buttonLeftCam.setClickable(true);
                break;

            case (short)1:
                if(videoCheck){
                    getSupportFragmentManager().beginTransaction()
                            .add(R.id.container, Camera0Video = VideoMode.newInstance(idList.get(0), 0, eis_check, fd_check, mOverlayView,3)).commitNow();
                }
                else{
                    getSupportFragmentManager().beginTransaction()
                            .add(R.id.container, Camera0V = PhotoMode.newInstance(idList.get(0), 0, mOverlayView, fd_check, hdr_check, mfnr_check, qcfa_check, 3)).commitNow();
                }
                for(short i=0;i<cameraNumberToStart;i++){
                    mTextView[i].setText("Cam"+idList.get(i));
                    mTextView[i].setVisibility(View.VISIBLE);
                }
                buttonRightCam.setClickable(false);
                buttonLeftCam.setClickable(false);
                break;
        }
    }

    /**
     * Helper for camera ID switching
    **/
    private void switchIdList(int positionOne, int positionTwo){
        switcher = idList.get(positionOne);
        idList.set(positionOne,idList.get(positionTwo));
        idList.set(positionTwo,switcher);
    }

    /**
     * Buttons onClick events handling
    **/
    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.buttonLeftCam:

                switchIdList(0,1);
                restartCameras();

                break;

            case R.id.buttonRightCam:

                switchIdList(0,2);
                restartCameras();

                break;

            case R.id.buttonVideo:

                if(isRecording) {
                   buttonVideo.setBackgroundResource(R.drawable.video_capture);
                   buttonLShot.setVisibility(View.GONE);
                   buttonLShot.setClickable(false);
                   Camera0Video.stopRecordingVideo();
                   if(cameraNumberToStart>1) {
                       Camera1Video.stopRecordingVideo();
                   }
                   if(cameraNumberToStart>2) {
                       Camera2Video.stopRecordingVideo();
                   }
                    isRecording = false;
                }
                else {
                    buttonVideo.setBackgroundResource(R.drawable.video_stop);
                    buttonLShot.setVisibility(View.VISIBLE);
                    buttonLShot.setClickable(true);
                    Camera0Video.startRecordingVideo();
                    if(cameraNumberToStart>1) {
                        Camera1Video.startRecordingVideo();
                    }
                    if(cameraNumberToStart>2){
                        Camera2Video.startRecordingVideo();
                    }
                    isRecording = true;
                }

                break;

            case R.id.shutter_button:

                if(cameraNumberToStart == (short)3) {
                    Camera0V.capturePicture();
                    Camera1V.capturePicture();
                    Camera2V.capturePicture();
                }
                else if(cameraNumberToStart == (short)2) {
                    Camera0V.capturePicture();
                    Camera1V.capturePicture();
                }
                else {
                    Camera0V.capturePicture();
                }

                break;

            case R.id.buttonLShot:

                if(cameraNumberToStart == (short)3) {
                    Camera0Video.captureLivePicture();
                    Camera1Video.captureLivePicture();
                    Camera2Video.captureLivePicture();
                }
                else if(cameraNumberToStart == (short)2) {
                    Camera0Video.captureLivePicture();
                    Camera1Video.captureLivePicture();
                }
                else {
                    Camera0Video.captureLivePicture();
                }

                break;


            case R.id.Photo:

                optionsArray[10]=(short)0;
                buttonSettings.setVisibility(View.VISIBLE);
                buttonSettings.setClickable(true);
                videoCheck=false;
                Photo.setTextColor(getResources().getColor(android.R.color.holo_blue_light));
                Video.setTextColor(getResources().getColor(android.R.color.holo_green_light));
                buttonVideo.setClickable(false);
                buttonVideo.setVisibility(View.GONE);
                if(!burst_check) {captureButton.setClickable(true);
                    captureButton.setVisibility(View.VISIBLE);
                    Burst.setClickable(false);
                    Burst.setVisibility(View.GONE);
                }
                else{
                    captureButton.setClickable(false);
                    captureButton.setVisibility(View.GONE);
                    Burst.setClickable(true);
                    Burst.setVisibility(View.VISIBLE);
                }
                restartCameras();
                videoClickCheck=false;


                break;

            case R.id.Video:

                videoClickCheck=true;
                optionsArray[10]=(short)1;
                buttonSettings.setVisibility(View.VISIBLE);
                buttonSettings.setClickable(true);
                videoCheck=true;
                Video.setTextColor(getResources().getColor(android.R.color.holo_blue_light));
                Photo.setTextColor(getResources().getColor(android.R.color.holo_green_light));
                captureButton.setClickable(false);
                captureButton.setVisibility(View.GONE);
                Burst.setClickable(false);
                Burst.setVisibility(View.GONE);
                buttonVideo.setClickable(true);
                buttonVideo.setVisibility(View.VISIBLE);
                restartCameras();


                break;

            case R.id.settings:
                openSettingsMenu();

                break;

            case R.id.buttonSDC:
                Activity mActivity = this;
                Bundle idData = new Bundle();
                idData.putShortArray("options",initialOptions);
                Intent myIntent = new Intent(mActivity, CameraActivity.class);
                myIntent.putExtra("options", idData);
                mActivity.startActivity(myIntent);


        }

    }

    /**
     * Creating new intent to start menu activity
    **/
    private void openSettingsMenu() {
        Bundle metaData = new Bundle();
        metaData.putShortArray("options",optionsArray);
        short idsCurrentState[]=new short[idList.size()];
        for(int i=0;i<idList.size();i++)
        {
            idsCurrentState[i]=Short.valueOf(idList.get(i));
        }
        metaData.putShortArray("idState", idsCurrentState);
        Activity mActivity = this;
        Intent intent = new Intent(mActivity, MultiSettingsActivity.class);
        intent.putExtra("options", metaData);
        mActivity.startActivity(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    /**
     * Buttons onTouch events handling
    **/
    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {


        if(view.getId()==R.id.Burst) {
            if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                view.getBackground().setColorFilter(Color.DKGRAY,PorterDuff.Mode.SRC_ATOP);
                view.invalidate();
                Camera0V.iSBurstActive=true;
                Camera0V.captureBurstPicture();

                return true;
            }
            if (motionEvent.getAction() == MotionEvent.ACTION_UP) {
                view.getBackground().clearColorFilter();
                view.invalidate();
                Camera0V.iSBurstActive=false;
                Camera0V.setBurstCounter(0);

                return true;
            }
        }

        if(view.getId()==R.id.shutter_button||view.getId()==R.id.buttonLShot) {

            if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {

                if(view.getId()==R.id.shutter_button) {
                    captureButton.callOnClick();
                }
                if(view.getId()==R.id.buttonLShot){
                    buttonLShot.callOnClick();
                }
                view.getBackground().setColorFilter(Color.DKGRAY,PorterDuff.Mode.SRC_ATOP);
                view.invalidate();

                return true;
            }
            if (motionEvent.getAction() == MotionEvent.ACTION_UP) {

                view.getBackground().clearColorFilter();
                view.invalidate();

                return true;
            }
        }


        return false;
    }

        public static class saverImg implements Runnable {

            private final Image mSavedImage;
            private final File mSavedFile;
            saverImg(Image img, File f) {
                mSavedImage = img;
                mSavedFile = f;
            }

            @Override
            public void run() {
                ByteBuffer buff;
                byte[] b;
                FileOutputStream outStr = null;
                try{
                    buff = mSavedImage.getPlanes()[0].getBuffer();
                    b = new byte[buff.remaining()];
                    buff.get(b);
                    outStr = new FileOutputStream(mSavedFile);
                    outStr.write(b);
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    mSavedImage.close();
                    if (null != outStr) {
                        try {
                            outStr.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }

        }

}

