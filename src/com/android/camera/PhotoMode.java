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

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
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
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.MediaStore.*;
import android.provider.MediaStore.Images;
import android.provider.MediaStore.Images.ImageColumns;
import android.provider.MediaStore.Images.Media;
import android.provider.MediaStore.Images.Media.*;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.ViewGroup;

import com.android.camera.Storage;
import com.android.camera.ui.AutoFitSurfaceView;
import com.android.camera.ui.Camera2FaceView;

import co.aospa.camera.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;


public class PhotoMode extends Fragment
        implements View.OnClickListener {

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
     * Preview surface requested dimensions
    **/
    private static final int PREVIEW_WIDTH = 1920;
    private static final int PREVIEW_HEIGHT = 1440;
    private static final int ASPECT_W = 4;
    private static final int ASPECT_H = 3;
    private static final int MAX_IMAGES = 2;

    /**
     * dimensions for face detection overlay surface
    **/
    private static final int OVERLAY_WIDTH = 8000;
    private static final int OVERLAY_HEIGHT = 6000;

    /**
     * Height and width cover ratio by small previews on top
     **/
    private static final double COVERED_WIDTH_ASPECT = 4;
    private static final double COVERED_HEIGHT_ASPECT = 3;

    /**
     * Burst captured images limit
    **/
    private static final int BURST_CAPTURE_LIMIT = 50;

    /**
     * flags for features
    **/
    private boolean HDR_MODE = false;
    private boolean FD_MODE = false;
    private boolean QCFA_MODE = false;
    private boolean MFNR_MODE = false;

    /**
     * number of running cameras when Face detection is enabled for the current instance
    **/
    private int mNumberOfConcurrentCameras=1;

    /**
     * Check whether burst capture is ongoing
    **/
    public boolean iSBurstActive = false;

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
     * Flag for checking whether this instance of PhotoMode is the main camera
    **/
    private boolean mIsMainCamera=false;

    /**
     * Id of the camera
    **/
    private String mCameraId;

    /**
     * check for requested preview surface
     * 0 - main surface
     * 1 - top left corner
     * 2 - top right corner
    **/
    private int mCameraPosition=0;

    /**
     * surface for preview display
    **/
    private AutoFitSurfaceView mSurfaceView;

    /**
     * check for device rotation
    **/
    private boolean isSwappedDimens;
    private int mDetectedOrientation;

    /**
     * Characteristics for the current camera
    **/
    private CameraCharacteristics mCharacteristicsCamera;

    /**
     * Current running capture session
    **/
    private CameraCaptureSession mCaptureCameraSession;

    /**
     * Current camera device
    **/
    private CameraDevice mDeviceCamera;

    /**
     * Thread and handler for the background
    **/
    private HandlerThread mHandlerThreadBackground;
    private Handler mHandlerBackground;

    /**
     * Image reader and file for image capture
    **/
    private ImageReader mReaderImage;
    private File mFile;

    /**
     * Capture request and capture builder for current camera
     **/
    private CaptureRequest.Builder mBuilderRequestCapture;
    private CaptureRequest mRequestCapture;

    /**
     * Semaphore for locking the camera device
     **/
    private Semaphore mSemaphoreLock = new Semaphore(1);

    /**
     * counter for burst captures
     **/
    private int mCounterBurst=0;

    /**
     * Surface for face detection
     **/
    private Camera2FaceView mCamera2FaceView;

    /**
     * public constructors for camera session without or with enabled features
    **/

    public PhotoMode() {}

    public static PhotoMode newInstance(String cameraID,
                                        int cameraPosition) {
        PhotoMode currentFragment = new PhotoMode();

        Bundle args = new Bundle();
        args.putString("mCameraId", cameraID);
        args.putInt("cameraPosition",cameraPosition);
        currentFragment.setArguments(args);

        return currentFragment;
    }

    public static PhotoMode newInstance(String cameraID,
                                        int cameraPosition,
                                        Camera2FaceView faceDetectionView,
                                        boolean fd,
                                        boolean hdr,
                                        boolean mfnr,
                                        boolean qcfa,
                                        int numberOfRunningCameras) {
        PhotoMode currentFragment = new PhotoMode();

        Bundle args = new Bundle();
        args.putString("mCameraId", cameraID);
        args.putBoolean("FD_MODE",fd);
        args.putBoolean("HDR_MODE",hdr);
        args.putBoolean("MFNR_MODE",mfnr);
        args.putBoolean("QCFA_MODE",qcfa);
        args.putInt("cameraPosition",cameraPosition);
        args.putInt("numCamFD",numberOfRunningCameras);
        currentFragment.mCamera2FaceView=faceDetectionView;
        currentFragment.setArguments(args);

        return currentFragment;
    }


    @Override
    public void onCreate (Bundle savedInstanceState){
        Bundle args = getArguments();

        mCameraId=args.getString("mCameraId", "0");
        FD_MODE=args.getBoolean("FD_MODE",false);
        HDR_MODE=args.getBoolean("HDR_MODE",false);
        MFNR_MODE=args.getBoolean("MFNR_MODE",false);
        QCFA_MODE=args.getBoolean("QCFA_MODE",false);
        mCameraPosition=args.getInt("cameraPosition",0);
        mNumberOfConcurrentCameras=args.getInt("numCamFD",1);
        if(mCameraPosition==0){
            mIsMainCamera=true;
        }
        if(FD_MODE) {
            mCamera2FaceView.initMode();
        }

        super.onCreate(savedInstanceState);

    }

    /**
     * inflating the main XML for the current PhotoMode instance
     **/
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.PiPPhotoMode, container, false);
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

        mSurfaceView.getHolder().setFixedSize(PREVIEW_WIDTH, PREVIEW_HEIGHT);

    }


    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
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
            openCamera(mSurfaceView.getWidth(), mSurfaceView.getHeight());
        } else {
            mSurfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
                @Override
                public void surfaceCreated(SurfaceHolder holder) {
                    openCamera(mSurfaceView.getWidth(), mSurfaceView.getHeight());
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
     * Taking picture
     * create unique name for the capture image  and enable capture features
     * - noise rediction and hdr
     **/
    public void capturePicture() {
        try {
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(System.currentTimeMillis());
            String time = "";
            time += calendar.get(Calendar.YEAR);
            time += calendar.get(Calendar.MONTH);
            time += calendar.get(Calendar.DAY_OF_MONTH);
            time+="_";
            time += System.currentTimeMillis()%10000000;


            mFile = new File(Storage.DIRECTORY, "IMG" + "_" +
                    time +"_PIP_CAM"+mCameraId+".jpg");
            final Activity activity = getActivity();
            if (null == activity || null == mDeviceCamera) {
                return;
            }
            final CaptureRequest.Builder captureBuilder =
                    mDeviceCamera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);

            captureBuilder.addTarget(mReaderImage.getSurface());
            captureBuilder.addTarget(mSurfaceView.getHolder().getSurface());

            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            if (HDR_MODE) {
                captureBuilder.set(CaptureRequest.CONTROL_SCENE_MODE,CaptureRequest.CONTROL_SCENE_MODE_HDR);
            }
            if (MFNR_MODE) {
                captureBuilder.set(CaptureRequest.NOISE_REDUCTION_MODE,
                        CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY);
                CaptureRequest.Key<Byte> custom_noise_reduction =
                        new CaptureRequest.Key("org.quic.camera.CustomNoiseReduction.CustomNoiseReduction", byte.class);
                captureBuilder.set(custom_noise_reduction,(byte)0x01);
            }
            int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, getOrientation(rotation));

            CameraCaptureSession.CaptureCallback CaptureCallback
                    = new CameraCaptureSession.CaptureCallback() {

                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                               @NonNull CaptureRequest request,
                                               @NonNull TotalCaptureResult result) {
                    Log.d("Camera", mFile.toString());
                }
            };

            mCaptureCameraSession.capture(captureBuilder.build(), CaptureCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * burst counter setter
    **/
    public void setBurstCounter(int bCounter){
        mCounterBurst=bCounter;
    }

    /**
     * Taking picture
     * create unique name for each capture and enable capture features - noise rediction and hdr
     **/
    public void captureBurstPicture() {

        int mPicCount = 0;
        try {
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(System.currentTimeMillis());
            String time = "";
            time += calendar.get(Calendar.YEAR);
            time += calendar.get(Calendar.MONTH);
            time += calendar.get(Calendar.DAY_OF_MONTH);
            time+="_";
            time += System.currentTimeMillis()%10000000;


            mFile = new File(Storage.DIRECTORY, "IMG" + "_" +
                    time +"_PIP_CAM"+mCameraId+".jpg");
            final Activity activity = getActivity();
            if (null == activity || null == mDeviceCamera) {
                return;
            }
            List<CaptureRequest> captureList = new ArrayList<CaptureRequest>();
            final CaptureRequest.Builder captureBuilder =  mDeviceCamera.createCaptureRequest(CameraDevice.TEMPLATE_ZERO_SHUTTER_LAG);
            captureBuilder.addTarget(mReaderImage.getSurface());
            captureBuilder.addTarget(mSurfaceView.getHolder().getSurface());
            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            if(HDR_MODE){
                captureBuilder.set(CaptureRequest.CONTROL_SCENE_MODE,
                        CaptureRequest.CONTROL_SCENE_MODE_HDR);}
            if(MFNR_MODE) {
                captureBuilder.set(CaptureRequest.NOISE_REDUCTION_MODE,
                        CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY);
                CaptureRequest.Key<Byte> custom_noise_reduction =
                        new CaptureRequest.Key("org.quic.camera.CustomNoiseReduction.CustomNoiseReduction", byte.class);
                captureBuilder.set(custom_noise_reduction,(byte)0x01);
            }
            int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, getOrientation(rotation));
            captureList.add(captureBuilder.build());
            captureList.add(captureBuilder.build());
            CameraCaptureSession.CaptureCallback CaptureCallback
                    = new CameraCaptureSession.CaptureCallback() {

                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                               @NonNull CaptureRequest request,
                                               @NonNull TotalCaptureResult result) {

                    if(iSBurstActive){
                        if(mCounterBurst< BURST_CAPTURE_LIMIT) {
                            captureBurstPicture();
                        }
                    }
                    else {
                        mCounterBurst = 0;
                    }
                    mCounterBurst++;
                }
            };

            mCaptureCameraSession.captureBurst(captureList, CaptureCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
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
    private CameraCaptureSession.CaptureCallback captureCallback
            = new CameraCaptureSession.CaptureCallback() {

        private void process(CaptureResult result) {
            if(FD_MODE&&mIsMainCamera) {
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
    private final CameraDevice.StateCallback mCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            mSemaphoreLock.release();
            mDeviceCamera = cameraDevice;
            createPreview();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            mSemaphoreLock.release();
            cameraDevice.close();
            mDeviceCamera = null;
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int error) {
            mSemaphoreLock.release();
            cameraDevice.close();
            mDeviceCamera = null;
            Activity activity = getActivity();
            if (null != activity) {
                activity.finish();
            }
        }

    };

    /**
     * Callback for precapture state of the camera device
    **/
    private CameraCaptureSession.CaptureCallback preCallbackCapture
            = new CameraCaptureSession.CaptureCallback() {

        private void process(CaptureResult result) {
        }

        @Override
        public void onCaptureProgressed(CameraCaptureSession session, CaptureRequest request, CaptureResult partialResult) {
            process(partialResult);
        }

        @Override
        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
            process(result);
            try {
                session.capture(request, this, null);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

    };


    /**
     * Set up of Image reader for taking captured image with biggest available resolution
    **/
    private Size setUpImageReader() {
        Size defaultSize = new Size(PREVIEW_WIDTH,PREVIEW_HEIGHT);
        try {
            Activity activity = getActivity();
            CameraManager mCameraManager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
            StreamConfigurationMap map = mCameraManager.getCameraCharacteristics(mCameraId).get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            Size[] availableSizes = map.getOutputSizes(ImageFormat.JPEG);
            Size largestSelected;
            boolean aspectCheck=false;
            int k = 0;
            do {
                largestSelected = availableSizes[k];
                aspectCheck = largestSelected.getWidth() * ASPECT_H == largestSelected.getHeight() * ASPECT_W;
                k++;
                if(aspectCheck) {
                    return largestSelected;
                }
            }
            while (!aspectCheck||k>=availableSizes.length);
            defaultSize = availableSizes[0];
        }catch(CameraAccessException e)
        {
            e.printStackTrace();
        }
        return defaultSize;

    }

    /**
     * filtering detected faces when 2 or 3 cameras are running and face detection is enabled
     * for the main camera(current camera)
     * Score for faces which are hidden by the secondary previews are set to 1 and face is not drawn
    **/
    private void filterFaces()
    {
        double divider=OVERLAY_WIDTH/(double)mSensorResolution.getWidth();
        if(mNumberOfConcurrentCameras==3) {
            for (int i = 0; i < mFaces.length; i++) {
                if (mFaces[i].getBounds().left < (int)((OVERLAY_WIDTH/divider)/COVERED_WIDTH_ASPECT)) {
                    Rect currentFaceBox = mFaces[i].getBounds();
                    mFaces[i] = new Face(currentFaceBox, 1);
                }
            }
        }
        if(mNumberOfConcurrentCameras==2)
            for(int i = 0; i < mFaces.length; i++) {
                if (mFaces[i].getBounds().left < (int)((OVERLAY_WIDTH/divider)/COVERED_WIDTH_ASPECT)&&
                        mFaces[i].getBounds().top>(int)((OVERLAY_HEIGHT/divider)/COVERED_HEIGHT_ASPECT)) {
                    Rect currentFaceBox = mFaces[i].getBounds();
                    mFaces[i] = new Face(currentFaceBox,1);
                }
            }
    }

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
        mCamera2FaceView.setParamsForPiP(true,PREVIEW_HEIGHT,PREVIEW_WIDTH,SENSOR_ORIENTATION_DEFAULT_DEGREES,0,0);
        mCamera2FaceView.setOriginalCameraBound(mCharacteristicsCamera.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE));
        mCamera2FaceView.resume();
    }

    /**
     * Setup of options for the current camera
     * enabling all needed surfaces - preview and image capture
    **/
    private void cameraSetUp(int width, int height) {
        Activity activity = getActivity();
        CameraManager mCameraManager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        try {
                Size largestDetected = setUpImageReader();
                ImageReader imageRdrNormal = ImageReader.newInstance(largestDetected.getWidth(), largestDetected.getHeight(),
                        ImageFormat.JPEG,MAX_IMAGES);
                mCharacteristicsCamera
                        = mCameraManager.getCameraCharacteristics(mCameraId);
                mActiveArraySizeRect = mCharacteristicsCamera.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
                mSensorResolution = new Size(mActiveArraySizeRect.right-mActiveArraySizeRect.left,mActiveArraySizeRect.bottom-mActiveArraySizeRect.top);
                ImageReader imageRdrQCFA = ImageReader.newInstance(mSensorResolution.getWidth(),mSensorResolution.getHeight(),
                    ImageFormat.JPEG,2);
                System.out.println("cam id " + mCameraId);

                    if(QCFA_MODE){
                        mReaderImage = imageRdrQCFA;
                    }
                    else{
                        mReaderImage = imageRdrNormal;
                    }

                    mReaderImage.setOnImageAvailableListener(mListenerImage, mHandlerBackground);
                    int displayRotation = activity.getWindowManager().getDefaultDisplay().getRotation();
                    mDetectedOrientation = mCharacteristicsCamera.get(CameraCharacteristics.SENSOR_ORIENTATION);

                    int orientation = getResources().getConfiguration().orientation;
                    if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                        mSurfaceView.setAspectRatio(ASPECT_W, ASPECT_H);
                    } else {
                        mSurfaceView.setAspectRatio(ASPECT_H, ASPECT_W);
                    }



        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (NullPointerException e) {
            e.printStackTrace();
        }
    }

    private void openCamera(int width, int height) {
        cameraSetUp(width, height);
        Activity activity = getActivity();
        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);

        try {
            if (!mSemaphoreLock.tryAcquire(500, TimeUnit.MILLISECONDS)) {
               throw new RuntimeException("Time out locking camera opening.");
           }
            manager.openCamera(mCameraId, mCallback, mHandlerBackground);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void closeCamera() {
        try {
            mSemaphoreLock.acquire();
            if (null != mDeviceCamera) {
                mDeviceCamera.close();
                mDeviceCamera = null;
            }
            if (null != mCaptureCameraSession) {
                mCaptureCameraSession.close();
                mCaptureCameraSession = null;
            }
            if (null != mReaderImage) {
                mReaderImage.close();
                mReaderImage = null;
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            mSemaphoreLock.release();
        }
    }

    private void startBackgroundThread() {
        mHandlerThreadBackground = new HandlerThread("BackgroundThread");
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
     * creating capture session for the current camera device
    **/
    private void createPreview() {
        try {
            Surface surface = mSurfaceView.getHolder().getSurface();
            mBuilderRequestCapture
                    = mDeviceCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mBuilderRequestCapture.addTarget(surface);
            mDeviceCamera.createCaptureSession(Arrays.asList(surface, mReaderImage.getSurface()),
                    new CameraCaptureSession.StateCallback() {

                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                            if (null == mDeviceCamera) {
                                return;
                            }
                            mCaptureCameraSession = cameraCaptureSession;
                            try {
                                mBuilderRequestCapture.set(CaptureRequest.CONTROL_AF_MODE,
                                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                                if(FD_MODE&&mIsMainCamera){
                                mBuilderRequestCapture.set(CaptureRequest.STATISTICS_FACE_DETECT_MODE,
                                        CameraMetadata.STATISTICS_FACE_DETECT_MODE_FULL);
                                    updateFaceDetection();}
                                mRequestCapture = mBuilderRequestCapture.build();
                                mCaptureCameraSession.setRepeatingRequest(mRequestCapture, captureCallback, mHandlerBackground);
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(
                                @NonNull CameraCaptureSession cameraCaptureSession) {
                        }
                    }, null
            );
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private int getOrientation(int rotation) {
        return (ORIENTATIONS_ARRAY.get(rotation) + mDetectedOrientation + 270) % 360;
    }

    /**
     * restart of the preview on completed capture
    **/
    private void restartPreview() {
        try {
            mCaptureCameraSession.capture(mBuilderRequestCapture.build(), captureCallback, mHandlerBackground);
            mCaptureCameraSession.setRepeatingRequest(mRequestCapture, captureCallback, mHandlerBackground);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

}
