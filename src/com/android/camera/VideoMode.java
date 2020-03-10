/*
*Copyright (c) 2020, The Linux Foundation. All rights reserved.

*Not a Contribution.

*Copyright 2017 The Android Open Source Project

*Licensed under the Apache License, Version 2.0 (the "License");
*you may not use this file except in compliance with the License.
*You may obtain a copy of the License at

      *http://www.apache.org/licenses/LICENSE-2.0

*Unless required by applicable law or agreed to in writing, software
*distributed under the License is distributed on an "AS IS" BASIS,
*WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
*See the License for the specific language governing permissions and
*limitations under the License.
*/

package com.android.camera;

import android.Manifest;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.Face;
import android.hardware.camera2.params.OutputConfiguration;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.MediaStore.Images;
import android.provider.MediaStore.Images.ImageColumns;
import android.provider.MediaStore.Images.Media;
import android.provider.MediaStore.Video;
import android.provider.MediaStore.Video.VideoColumns;
import android.support.annotation.NonNull;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import com.android.camera.ui.AutoFitSurfaceView;
import com.android.camera.ui.Camera2FaceView;

import co.aospa.camera.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class VideoMode extends android.support.v4.app.Fragment
        implements View.OnClickListener{

    /**
     * key for enabling video stabilization
    **/
    public static final CaptureRequest.Key<Byte> eis_mode =
            new CaptureRequest.Key<>("org.quic.camera.eis3enable.EISV3Enable", byte.class);


    /**
     * Default and rotated orientations of the preview
    **/
    /**
     * Display orientations array
     **/
    private static final int SENSOR_ORIENTATION_DEFAULT_DEGREES = 90;
    private static final SparseIntArray ORIENTATIONS_ARRAY = new SparseIntArray();
    static {
        ORIENTATIONS_ARRAY.append(Surface.ROTATION_0, 90);
        ORIENTATIONS_ARRAY.append(Surface.ROTATION_90, 0);
        ORIENTATIONS_ARRAY.append(Surface.ROTATION_180, 270);
        ORIENTATIONS_ARRAY.append(Surface.ROTATION_270, 180);
    }

    /**
     * Preview stream dimensions
    **/
    private static final int PREVIEW_VIDEO_WIDTH = 1920;
    private static final int PREVIEW_VIDEO_HEIGHT = 1080;
    private static final int ASPECT_W = 16;
    private static final int ASPECT_H = 9;
    private static final int MAX_IMAGES = 2;

    /**
     * Dimensions for the Face detection overlay surface
    **/
    private static final int OVERLAY_WIDTH = 8000;
    private static final int OVERLAY_HEIGHT = 6000;
    private static final int OVERLAY_DISPLAY_WIDTH = 2560;
    private static final int OVERLAY_DISPLAY_HEIGHT = 1920;

    /**
     * Height and width cover ratio by small previews on top
    **/
    private static final double COVERED_WIDTH_ASPECT = 2.5;
    private static final double COVERED_HEIGHT_ASPECT = 3;

    /**
     * Face detection number of cameras running when FD is enabled
    **/
    private static int mNumberOfConcurrentCameras=1;

    /**
     * ID of the current camera
    **/
    private String mCameraId;

    /**
     * array for detected faces when Face detection is enabled
     **/
    private Face[] mFaces;

    /**
     * dimensions of the sensor
     **/
    private Rect mActiveArraySizeRect;
    private Size mSensorResolution;

    /**
     * Image reader and file for image capture
     **/
    private ImageReader mReaderImage;
    private File mFile;

    /**
     * surface for preview display
     **/
    private AutoFitSurfaceView mSurfaceView;

    /**
     * Current camera device
     **/
    private CameraDevice mDeviceCamera;

    /**
     * Characteristics for the current camera
     **/
    private CameraCharacteristics mCharacteristicsCamera;

    /**
     * Current running capture session
     **/
    private CameraCaptureSession mCaptureCameraSession;

    /**
     * Sizes for preview and video
    **/
    private Size mPreviewSize;
    private Size mVideoSize;

    /**
     * Media recorder for handling the video stream
    **/
    private MediaRecorder mRecorderMedia;
    private boolean mIsRecordingVideo;

    /**
     * Thread and handler for the background
     **/
    private HandlerThread mHandlerThreadBackground;
    private Handler mHandlerBackground;

    /**
     * Semaphore for locking the camera device
     **/
    private Semaphore mCameraOpenCloseLock = new Semaphore(1);

    private Integer mDetectedOrientation;
    private String mNextVideoPath;


    private CaptureRequest.Builder mBuilderRequestCapture;

    /**
     * check for requested preview surface
     * 0 - main surface
     * 1 - top left corner
     * 2 - top right corner
     **/
    private int mCameraPosition=0;

    /**
     * Face detection surface
     **/
    private Camera2FaceView mCamera2FaceView;

    /**
     * Flags for noise reduction(video stabilization)
     * face detection
     * and Flag for checking whether this instance of VideoMode is the main camera
     **/
    private boolean mStabilization;
    private boolean mFaceDetection;
    private boolean mIsMainCamera=false;

    /**
     * public constructors for camera session without or with enabled features
     **/

    public VideoMode() {}

    public static VideoMode newInstance(String cameraId,
                                        int cameraPosition,
                                        boolean stabilization) {
        VideoMode currentFragment = new VideoMode();

        Bundle args = new Bundle();
        args.putString("mCameraId", cameraId);
        args.putInt("mainCamera",cameraPosition);
        args.putBoolean("noiseReduction",stabilization);
        currentFragment.setArguments(args);

        return currentFragment;
    }

    public static VideoMode newInstance(String cameraId,
                                        int cameraPosition,
                                        boolean stabilization,
                                        boolean faceDetection,
                                        Camera2FaceView faceDetectionView,
                                        int numberOfRunningCameras) {
        VideoMode currentFragment = new VideoMode();

        Bundle args = new Bundle();
        args.putString("mCameraId", cameraId);
        args.putInt("mainCamera",cameraPosition);
        args.putBoolean("noiseReduction",stabilization);
        args.putBoolean("faceDetection",faceDetection);
        args.putInt("numCamFD",numberOfRunningCameras);
        currentFragment.mCamera2FaceView =faceDetectionView;
        currentFragment.setArguments(args);

        return currentFragment;
    }

    @Override
    public void onCreate (Bundle savedInstanceState){
        Bundle args = getArguments();

        mCameraId=args.getString("mCameraId", "0");
        mCameraPosition=args.getInt("mainCamera",0);
        mStabilization=args.getBoolean("noiseReduction",false);
        mFaceDetection=args.getBoolean("faceDetection",false);
        mNumberOfConcurrentCameras=args.getInt("numCamFD",1);
        if(mCameraPosition==0){
            mIsMainCamera=true;
        }
        if(mFaceDetection) {
            mCamera2FaceView.initMode();
        }

        super.onCreate(savedInstanceState);

    }

    /**
     * Taking picture
     * create unique name for the capture image  and enable capture features
     * - noise rediction and hdr
     **/
    public void captureLivePicture() {
        try {
            mFile = new File(Storage.DIRECTORY, "IMG"+mCameraId + "_" +
                    System.currentTimeMillis() + ".jpg");
            final Activity activity = getActivity();
            if (null == activity || null == mDeviceCamera) {
                return;
            }
            final CaptureRequest.Builder captureBuilder =
                    mDeviceCamera.createCaptureRequest(CameraDevice.TEMPLATE_VIDEO_SNAPSHOT);
            captureBuilder.addTarget(mReaderImage.getSurface());
            captureBuilder.addTarget(mSurfaceView.getHolder().getSurface());
            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            captureBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CameraMetadata.CONTROL_AF_MODE_AUTO);
            int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
            switch (mDetectedOrientation) {
                case SENSOR_ORIENTATION_DEFAULT_DEGREES:
                    captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS_ARRAY.get(rotation));
                    break;
                default:
                    captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, ((ORIENTATIONS_ARRAY.get(rotation)+180))%360);
                    break;
            }
            captureBuilder.set(CaptureModule.recording_end_stream, (byte) 0x01);
            CameraCaptureSession.CaptureCallback CaptureCallback
                    = new CameraCaptureSession.CaptureCallback() {


                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                               @NonNull CaptureRequest request,
                                               @NonNull TotalCaptureResult result) {

                }
            };
            mCaptureCameraSession.capture(captureBuilder.build(), CaptureCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    public void startRecordingVideo() {
        if (null == mDeviceCamera || !mSurfaceView.isEnabled() || null == mPreviewSize) {
            return;
        }
        try {
            closePreviewSession();
            setUpMediaRecorder();
            Size largestAvailable = setUpImageReader();
            mReaderImage = ImageReader.newInstance(largestAvailable.getWidth(), largestAvailable.getHeight(), ImageFormat.JPEG, 2);
            mReaderImage.setOnImageAvailableListener(mListenerImage, mHandlerBackground);
            mBuilderRequestCapture = mDeviceCamera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            List<Surface> surfaces = new ArrayList<>();
            Surface previewSurface = mSurfaceView.getHolder().getSurface();
            surfaces.add(previewSurface);
            mBuilderRequestCapture.addTarget(previewSurface);
            Surface recorderSurface = mRecorderMedia.getSurface();
            surfaces.add(recorderSurface);
            mBuilderRequestCapture.addTarget(recorderSurface);
            surfaces.add(mReaderImage.getSurface());
            if(!mStabilization){
                mDeviceCamera.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {

                    @Override
                    public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                        mCaptureCameraSession = cameraCaptureSession;
                        if(mFaceDetection&&String.valueOf(mCameraPosition).equals(mCameraId)){
                            mBuilderRequestCapture.set(CaptureRequest.STATISTICS_FACE_DETECT_MODE,
                                    CameraMetadata.STATISTICS_FACE_DETECT_MODE_FULL);
                            updateFaceDetection();}
                        updatePreview();
                        getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                mIsRecordingVideo = true;
                                mRecorderMedia.start();
                            }
                        });
                    }

                    @Override
                    public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                        Activity activity = getActivity();
                        if (null != activity) {
                            Log.i("Exception", "Camera configuration failed");
                        }
                    }
                }, mHandlerBackground);}
            else {
                mBuilderRequestCapture.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, CaptureRequest
                        .CONTROL_VIDEO_STABILIZATION_MODE_ON);
                mBuilderRequestCapture.set(eis_mode,(byte)0x01);
                List<OutputConfiguration> outConfigurations = new ArrayList<>(surfaces.size());
                for (Surface sface : surfaces) {
                    outConfigurations.add(new OutputConfiguration(sface));
                }
                mDeviceCamera.createCustomCaptureSession(null, outConfigurations,
                        0xF008, new CameraCaptureSession.StateCallback() {

                            @Override
                            public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                                mCaptureCameraSession = cameraCaptureSession;
                                if(mFaceDetection&&String.valueOf(mCameraPosition).equals(mCameraId)){
                                    mBuilderRequestCapture.set(CaptureRequest.STATISTICS_FACE_DETECT_MODE,
                                            CameraMetadata.STATISTICS_FACE_DETECT_MODE_FULL);
                                    updateFaceDetection();}
                                updatePreview();
                                getActivity().runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        mIsRecordingVideo = true;
                                        mRecorderMedia.start();
                                    }
                                });
                            }

                            @Override
                            public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                                Activity activity = getActivity();
                                if (null != activity) {
                                    Log.i("Exception", "Camera configuration failed");
                                }
                            }
                        }, mHandlerBackground);

            }
        } catch (CameraAccessException | IOException e) {
            e.printStackTrace();
        }

    }

    public void stopRecordingVideo() {
        if(mStabilization){
            stopEIS();
        }
        mIsRecordingVideo = false;
        mRecorderMedia.stop();
        mRecorderMedia.reset();
        Activity activity = getActivity();
        if (null != activity) {

        }
        ContentValues values = new ContentValues();
        values.put(VideoColumns.DATA,mNextVideoPath);
        getActivity().getApplication().getApplicationContext().getContentResolver().insert(Video.Media.EXTERNAL_CONTENT_URI,values);

        mNextVideoPath = null;
        createSession();
    }

    /**
     * inflating the main XML for the current PhotoMode instance
     **/
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.PiPVideoMode, container, false);
    }

    /**
     * Instantiating the preview surface and setting preview stream resolution
     **/
    @Override
    public void onViewCreated(final View view, Bundle savedInstanceState) {
        switch (mCameraPosition) {
            case 0:
                mSurfaceView = (AutoFitSurfaceView) view.findViewById(R.id.texture);
                break;
            case 1:
                mSurfaceView = (AutoFitSurfaceView) view.findViewById(R.id.texture1);
                break;
            default:
                mSurfaceView = (AutoFitSurfaceView) view.findViewById(R.id.texture2);
                break;
        }
        mSurfaceView.getHolder().setFixedSize(PREVIEW_VIDEO_WIDTH, PREVIEW_VIDEO_HEIGHT);
    }


    /**
     * On resume method
     * handling cases for active and inactive state of the preview surface
     **/
    @Override
    public void onResume() {
        super.onResume();
        startBackgroundThread();
        if (mSurfaceView.getHolder().getSurface().isValid()) {
            openCamera(mSurfaceView.getHeight(), mSurfaceView.getWidth());

        } else {
            mSurfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
                @Override
                public void surfaceCreated(SurfaceHolder holder) {
                    openCamera(mSurfaceView.getHeight(), mSurfaceView.getWidth());

                }

                @Override
                public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

                }

                @Override
                public void surfaceDestroyed(SurfaceHolder holder) {

                }
            });
        }
    }

    @Override
    public void onPause() {
        closeCamera();
        stopBackgroundThread();
        super.onPause();
    }

    @Override
    public void onClick(View view) {

    }

    /**
     * Callback for when captured image is available
     * and calling media scanner to display saved image in the Gallery app of the device
     **/
    private final ImageReader.OnImageAvailableListener mListenerImage
            = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            mHandlerBackground.post(new MainActivity.saverImg(reader.acquireNextImage(), mFile));
            ContentValues values = new ContentValues();
            values.put(ImageColumns.DATA,mFile.getPath());
            values.put(ImageColumns.MIME_TYPE,"image/jpeg");
            getActivity().getApplication().getApplicationContext().getContentResolver().insert(Images.Media.EXTERNAL_CONTENT_URI,values);

        }
    };

    /**
     * Callback for processing the frame of each capture request
     * with special case for receiving face data when
     * face detection is enabled and drawing  borders oh the faces on Overlay surface
     **/
    private CameraCaptureSession.CaptureCallback mCaptureCallback
            = new CameraCaptureSession.CaptureCallback() {

        private void process(CaptureResult result) {
            if(mFaceDetection&&mIsMainCamera) {
                Integer mode = result.get(CaptureResult.STATISTICS_FACE_DETECT_MODE);
                mFaces = result.get(CaptureResult.STATISTICS_FACES);
                if (mFaces != null && mode != null) {
                    filterFaces();
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if(mFaces!=null){
                                mCamera2FaceView.setFaces(mFaces,null);
                            }
                        }
                    });
                }
            }

        }

        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession session,
                                        @NonNull CaptureRequest request,
                                        @NonNull CaptureResult partialResult) {
            process(partialResult);
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                       @NonNull CaptureRequest request,
                                       @NonNull TotalCaptureResult result) {
            process(result);
        }

    };

    /**
     * Callback for handling the camera device and its states
     **/
    private CameraDevice.StateCallback mCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            mCameraOpenCloseLock.release();
            mDeviceCamera = cameraDevice;
            createSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            cameraDevice.close();
            mDeviceCamera = null;
            mCameraOpenCloseLock.release();
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int error) {
            mCameraOpenCloseLock.release();
            if (null != getActivity()) {
                Log.i("Error","open camera error id =" + mCameraId);
                getActivity().finish();
            }
        }

    };


    /**
     * Setting options to the Face Detection surface and configuring it with
     * the camera sensor's specifications
     **/
    private void updateFaceDetection() {
        mCamera2FaceView.setBlockDraw(false);
        mCamera2FaceView.clear();
        mCamera2FaceView.setVisibility(View.VISIBLE);
        mCamera2FaceView.setDisplayOrientation(Surface.ROTATION_0);
        mCamera2FaceView.setMirror(false);
        Rect activeRegion = mCharacteristicsCamera.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
        Rect cropRegion = new Rect();
        Rect cropRegion1 = new Rect();

        int xCenter = activeRegion.width() / 2;
        int yCenter = activeRegion.height() / 2;
        int xDelta = (int) (activeRegion.width() / (2 * 1.0));
        int yDelta = (int) (activeRegion.height() / (2 * 1.0));
        cropRegion.set(mActiveArraySizeRect.left,
                mActiveArraySizeRect.top,
                mActiveArraySizeRect.right,
                mActiveArraySizeRect.bottom);
        mCamera2FaceView.setCameraBound(cropRegion);
        mCamera2FaceView.setParamsForPiP(true,OVERLAY_DISPLAY_HEIGHT,OVERLAY_DISPLAY_WIDTH,SENSOR_ORIENTATION_DEFAULT_DEGREES,0,0);
        mCamera2FaceView.setOriginalCameraBound(mCharacteristicsCamera.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE));
        mCamera2FaceView.resume();
    }

    private void startBackgroundThread() {
        mHandlerThreadBackground = new HandlerThread("BackgroundThreadCamera");
        mHandlerThreadBackground.start();
        mHandlerBackground = new Handler(mHandlerThreadBackground.getLooper());
    }

    private void stopBackgroundThread() {
        mHandlerThreadBackground.quitSafely();
        try {
            mHandlerThreadBackground.join();
            mHandlerThreadBackground = null;
            mHandlerBackground = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }


    /**
     * Setup of options for the current camera and opening the camera
     * enabling all needed surfaces - preview and image capture
     **/
    private void openCamera(int width, int height) {

        final Activity activity = getActivity();
        if (null == activity || activity.isFinishing()) {
            return;
        }

        try {
            CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
            if (!mCameraOpenCloseLock.tryAcquire(500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }
            mCharacteristicsCamera
                    = manager.getCameraCharacteristics(mCameraId);
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(mCameraId);
            mDetectedOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
            mActiveArraySizeRect = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
            mSensorResolution = new Size(mActiveArraySizeRect.right-mActiveArraySizeRect.left,mActiveArraySizeRect.bottom-mActiveArraySizeRect.top);


            mPreviewSize = new Size(PREVIEW_VIDEO_WIDTH,PREVIEW_VIDEO_HEIGHT);

            int orientation = getResources().getConfiguration().orientation;
            if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                mSurfaceView.setAspectRatio(ASPECT_W, ASPECT_H);
            } else {
                 mSurfaceView.setAspectRatio(ASPECT_H, ASPECT_W);
            }
            mRecorderMedia = new MediaRecorder();
            manager.openCamera(mCameraId, mCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
            activity.finish();
        } catch (NullPointerException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void closeCamera() {
        try {
            mCameraOpenCloseLock.acquire();
            closePreviewSession();
            if (null != mRecorderMedia) {
                mRecorderMedia.release();
                mRecorderMedia = null;
            }
            if (null != mDeviceCamera) {
                mDeviceCamera.close();
                mDeviceCamera = null;
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            mCameraOpenCloseLock.release();
        }
    }

    /**
     * Set up of Image reader for taking captured image with biggest available resolution
     **/
    private Size setUpImageReader() {
        Size defaultSize = new Size(PREVIEW_VIDEO_WIDTH,PREVIEW_VIDEO_HEIGHT);
        try {
            Activity activity = getActivity();
            CameraManager mCameraManager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
            StreamConfigurationMap map = mCameraManager.getCameraCharacteristics(mCameraId).get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            Size[] availableSizes = map.getOutputSizes(ImageFormat.JPEG);
            Size largestSelected;
            boolean aspectR=false;
            int k = 0;
            do {
                largestSelected = availableSizes[k];
                aspectR = largestSelected.getWidth() * ASPECT_H == largestSelected.getHeight() * ASPECT_W;
                k++;
                if(aspectR) {
                    return largestSelected;
                }
            }
            while (!aspectR||k>=availableSizes.length);
            defaultSize = availableSizes[0];
        }catch(CameraAccessException e)
        {
           e.printStackTrace();
        }
        return defaultSize;
    }

    /**
     * Send capture request for stopping video recording with EIS
    **/
    private void stopEIS() {
        try {

            final Activity activity = getActivity();
            if (null == activity || null == mDeviceCamera) {
                return;
            }
            final CaptureRequest.Builder captureBuilder =
                    mDeviceCamera.createCaptureRequest(CameraDevice.TEMPLATE_VIDEO_SNAPSHOT);
            captureBuilder.addTarget(mSurfaceView.getHolder().getSurface());

            captureBuilder.set(CaptureModule.recording_end_stream, (byte) 0x01);
            CameraCaptureSession.CaptureCallback CaptureCallback
                    = new CameraCaptureSession.CaptureCallback() {

                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                               @NonNull CaptureRequest request,
                                               @NonNull TotalCaptureResult result){}
            };
            mCaptureCameraSession.capture(captureBuilder.build(), CaptureCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private int getOrientation(int rotation) {
        return (ORIENTATIONS_ARRAY.get(rotation) + mDetectedOrientation + 270) % 360;
    }

    /**
     * filtering detected faces when 2 or 3 cameras are running and face detection is enabled
     * for the main camera(current camera)
     * Scores for faces which are hidden by the secondary previews are set to 1 and face is not drawn
     **/
    private void filterFaces()
    {
        double divider=OVERLAY_WIDTH/(double)mSensorResolution.getWidth();
        if(mNumberOfConcurrentCameras==3) {
            for (int i = 0; i < mFaces.length; i++) {
                if (mFaces[i].getBounds().left < (int)((OVERLAY_WIDTH/divider)/COVERED_WIDTH_ASPECT)) {
                    Rect tmp = mFaces[i].getBounds();
                    mFaces[i] = new Face(tmp, 1);
                }
            }
        }
        if(mNumberOfConcurrentCameras==2)
            for(int i=0;i<mFaces.length;i++) {
                if (mFaces[i].getBounds().left < (int)((OVERLAY_WIDTH/divider)/COVERED_WIDTH_ASPECT)&&
                        mFaces[i].getBounds().top>(int)((OVERLAY_HEIGHT/divider)/COVERED_HEIGHT_ASPECT)) {
                    Rect tmp = mFaces[i].getBounds();
                    mFaces[i] = new Face(tmp,1);
                }
            }
    }

    /**
     * creating capture session for the current camera device
     * calling simple create capture session for usual cases
     * calling create custom capture session when EIS is enabled
     **/
    private void createSession() {
        if (null == mDeviceCamera || !mSurfaceView.getHolder().getSurface().isValid() || null == mPreviewSize) {
            return;
        }
        try {

            closePreviewSession();


            Size largestAvailable = setUpImageReader();
            mReaderImage = ImageReader.newInstance(largestAvailable.getWidth(), largestAvailable.getHeight(), ImageFormat.JPEG, MAX_IMAGES);
            mReaderImage.setOnImageAvailableListener(mListenerImage, mHandlerBackground);
            Surface surface = mSurfaceView.getHolder().getSurface();
            mBuilderRequestCapture
                    = mDeviceCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mBuilderRequestCapture.addTarget(surface);
            List<Surface> surfaces = new ArrayList<>();
            surfaces.add(surface);
            surfaces.add(mReaderImage.getSurface());

            if(!mStabilization){
            mDeviceCamera.createCaptureSession(surfaces,
                    new CameraCaptureSession.StateCallback() {


                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            mCaptureCameraSession = session;
                            if(mFaceDetection&&mIsMainCamera){
                                mBuilderRequestCapture.set(CaptureRequest.STATISTICS_FACE_DETECT_MODE,
                                        CameraMetadata.STATISTICS_FACE_DETECT_MODE_FULL);
                                updateFaceDetection();}
                            updatePreview();
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            Activity activity = getActivity();
                            if (null != activity) {
                                Log.i("Exception", "Camera configuration failed");
                            }
                        }
                    }, mHandlerBackground);}
            else {
                mBuilderRequestCapture.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, CaptureRequest
                        .CONTROL_VIDEO_STABILIZATION_MODE_ON);
                mBuilderRequestCapture.set(eis_mode,(byte)0x01);
                List<OutputConfiguration> outConfigurations = new ArrayList<>(surfaces.size());
                for (Surface sface : surfaces) {
                    outConfigurations.add(new OutputConfiguration(sface));
                }
                mDeviceCamera.createCustomCaptureSession(null, outConfigurations,
                        0xF008, new CameraCaptureSession.StateCallback() {

                            @Override
                            public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                                mCaptureCameraSession = cameraCaptureSession;
                                if(mFaceDetection&&String.valueOf(mCameraPosition).equals(mCameraId)){
                                    mBuilderRequestCapture.set(CaptureRequest.STATISTICS_FACE_DETECT_MODE,
                                            CameraMetadata.STATISTICS_FACE_DETECT_MODE_FULL);
                                    updateFaceDetection();}
                                updatePreview();
                            }

                            @Override
                            public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                                Activity activity = getActivity();
                                if (null != activity) {
                                    Log.i("Exception", "Camera configuration failed");
                                }
                            }
                        }, mHandlerBackground);

            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void updatePreview() {
        if (null == mDeviceCamera) {
            return;
        }
        try {
            setUpCaptureRequestBuilder(mBuilderRequestCapture);
            HandlerThread thread = new HandlerThread("Preview");
            thread.start();
            mCaptureCameraSession.setRepeatingRequest(mBuilderRequestCapture.build(), mCaptureCallback, mHandlerBackground);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void setUpCaptureRequestBuilder(CaptureRequest.Builder builder) {
        builder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
    }

    /**
     * set up of Media recorder for video stream
    **/
    private void setUpMediaRecorder() throws IOException {
        final Activity activity = getActivity();
        if (null == activity) {
            return;
        }
        mRecorderMedia.setAudioSource(MediaRecorder.AudioSource.MIC);
        mRecorderMedia.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mRecorderMedia.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        if (mNextVideoPath == null || mNextVideoPath.isEmpty()) {
            mNextVideoPath = getVideoFilePath(getActivity());
        }
        mRecorderMedia.setOutputFile(mNextVideoPath);
        mRecorderMedia.setVideoEncodingBitRate(10000000);
        mRecorderMedia.setVideoFrameRate(30);
        mRecorderMedia.setVideoSize(PREVIEW_VIDEO_WIDTH, PREVIEW_VIDEO_HEIGHT);
        mRecorderMedia.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mRecorderMedia.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        switch (mDetectedOrientation) {
            case SENSOR_ORIENTATION_DEFAULT_DEGREES:
                mRecorderMedia.setOrientationHint(ORIENTATIONS_ARRAY.get(rotation));
                break;
            default:
                mRecorderMedia.setOrientationHint(((ORIENTATIONS_ARRAY.get(rotation)+180))%360);
                break;
        }
        mRecorderMedia.prepare();
    }

    /**
     * creating unique names for each saved video
    **/
    private String getVideoFilePath(Context context) {
        final File dir = new File(Storage.DIRECTORY);
        return (dir == null ? "" : (dir.getAbsolutePath() + "/"))
                +"VID"+ mCameraId +"_"+ System.currentTimeMillis() + ".mp4";
    }

    private void closePreviewSession() {
        if (mCaptureCameraSession != null) {
            mCaptureCameraSession.close();
            mCaptureCameraSession = null;
        }
    }

}
