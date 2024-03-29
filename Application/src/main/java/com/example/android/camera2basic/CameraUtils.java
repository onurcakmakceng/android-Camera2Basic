package com.example.android.camera2basic;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import static java.lang.StrictMath.abs;

public class CameraUtils {

    public CameraUtils(Activity activity, Camera2BasicFragment c2bFragment) {
        this.activity = activity;
        this.c2bFragment = c2bFragment;
    }

    private static int takenPictureOnOneAngle = 0;
    private static double totalDeviation = 0.0;
    private static int divideDev = 0;
    private static double motorAngle = 31.5;
    private static double minDeviation = 5000.0;

    private Activity activity;

    private Camera2BasicFragment c2bFragment;

    private double minDeviationOfAllAngles = 0;

    private static int orientationAngle = 0;

    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    private static final int REQUEST_CAMERA_PERMISSION = 1;
    public static final String FRAGMENT_DIALOG = "dialog";

    private static final int MAX_AVAILABLE = 1;
    private static final Semaphore mutex = new Semaphore(MAX_AVAILABLE, true);

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }



    static double minAngle = 0.0;

    static boolean photoTaken = false;
    /**
     * Tag for the {@link Log}.
     */
    private static final String TAG = "Camera2BasicFragment";

    /**
     * Camera state: Showing camera preview.
     */
    private static final int STATE_PREVIEW = 0;

    /**
     * Camera state: Waiting for the focus to be locked.
     */
    private static final int STATE_WAITING_LOCK = 1;

    /**
     * Camera state: Waiting for the exposure to be precapture state.
     */
    private static final int STATE_WAITING_PRECAPTURE = 2;

    /**
     * Camera state: Waiting for the exposure state to be something other than precapture.
     */
    private static final int STATE_WAITING_NON_PRECAPTURE = 3;

    /**
     * Camera state: Picture was taken.
     */
    private static final int STATE_PICTURE_TAKEN = 4;

    /**
     * Max preview width that is guaranteed by Camera2 API
     */
    public static final int MAX_PREVIEW_WIDTH = 1920;

    /**
     * Max preview height that is guaranteed by Camera2 API
     */
    public static final int MAX_PREVIEW_HEIGHT = 1080;

    public final TextureView.SurfaceTextureListener mSurfaceTextureListener
            = new TextureView.SurfaceTextureListener() {

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
            openCamera(width, height);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
            configureTransform(width, height);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture texture) {
        }

    };

    /**
     * ID of the current {@link CameraDevice}.
     */
    private String mCameraId;



    /**
     * An {@link AutoFitTextureView} for camera preview.
     */
    public AutoFitTextureView mTextureView;

    /**
     * A {@link CameraCaptureSession } for camera preview.
     */
    private CameraCaptureSession mCaptureSession;

    /**
     * A reference to the opened {@link CameraDevice}.
     */
    private CameraDevice mCameraDevice;

    /**
     * The {@link android.util.Size} of camera preview.
     */
    private Size mPreviewSize;

    /**
     * {@link CameraDevice.StateCallback} is called when {@link CameraDevice} changes its state.
     */
    private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            // This method is called when the camera is opened.  We start camera preview here.
            mCameraOpenCloseLock.release();
            mCameraDevice = cameraDevice;
            createCameraPreviewSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int error) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
//            Activity activity = getActivity();
            if (null != activity) {
                activity.finish();
            }
        }

    };

    /**
     * An additional thread for running tasks that shouldn't block the UI.
     */
    private HandlerThread mBackgroundThread;

    /**
     * A {@link Handler} for running tasks in the background.
     */
    private Handler mBackgroundHandler;

    /**
     * An {@link ImageReader} that handles still image capture.
     */
    private ImageReader mImageReader;

    /**
     * This is the output file for our picture.
     */
    private File mFile;

    /**
     * This a callback object for the {@link ImageReader}. "onImageAvailable" will be called when a
     * still image is ready to be saved.
     */
    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener
            = new ImageReader.OnImageAvailableListener() {

        @Override
        public void onImageAvailable(ImageReader reader) {
//            mBackgroundHandler.post(new CameraUtils.ImageProcessor(reader.acquireNextImage(), mFile, c2bFragment));
            mBackgroundHandler.post(new CameraUtils.ImageProcessor(reader.acquireNextImage(), c2bFragment, activity));
        }

    };

    /**
     * {@link CaptureRequest.Builder} for the camera preview
     */
    private CaptureRequest.Builder mPreviewRequestBuilder;

    /**
     * {@link CaptureRequest} generated by {@link #mPreviewRequestBuilder}
     */
    private CaptureRequest mPreviewRequest;

    /**
     * The current state of camera state for taking pictures.
     *
     * @see #mCaptureCallback
     */
    private int mState = STATE_PREVIEW;

    /**
     * A {@link Semaphore} to prevent the app from exiting before closing the camera.
     */
    private Semaphore mCameraOpenCloseLock = new Semaphore(1);

    /**
     * Whether the current camera device supports Flash or not.
     */
    private boolean mFlashSupported;

    /**
     * Orientation of the camera sensor
     */
    private static int mSensorOrientation;

    private CameraCaptureSession.CaptureCallback mCaptureCallback
            = new CameraCaptureSession.CaptureCallback() {

        private void process(CaptureResult result) {
            switch (mState) {
                case STATE_PREVIEW: {
                    // We have nothing to do when the camera preview is working normally.

                    // IF it is in 30 shot period
                    if (takenPictureOnOneAngle % 2 == 0 && takenPictureOnOneAngle != 0 && takenPictureOnOneAngle < 30) {
                        mState = STATE_WAITING_PRECAPTURE;
                    }


                    // If it is in angle period
                    if(takenPictureOnOneAngle == 0 && motorAngle >= 31.5 && motorAngle < 50) {
                        //---------- Açı değişecek burda
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            c2bFragment.msg("Error");
                        }

                        if (motorAngle<=38){
                            if (c2bFragment.btSocket!=null)
                        {
                            try
                            {
                                c2bFragment.btSocket.getOutputStream().write("4".toString().getBytes());
                                motorAngle=motorAngle+0.45;
                            }
                            catch (IOException e)
                            {
                                c2bFragment.msg("Error");
                            }
                        }
                        }
                        if (motorAngle>38.0&& motorAngle<=40.0){
                            if (c2bFragment.btSocket!=null)
                            {
                                try
                                {
                                    c2bFragment.btSocket.getOutputStream().write("5".toString().getBytes());
                                    motorAngle=motorAngle+0.225;
                                }
                                catch (IOException e)
                                {
                                    c2bFragment.msg("Error");
                                }
                            }
                        }
                        if (motorAngle>40.0&& motorAngle<=46.0){
                            if (c2bFragment.btSocket!=null)
                            {
                                try
                                {
                                    c2bFragment.btSocket.getOutputStream().write("6".toString().getBytes());
                                    motorAngle=motorAngle+0.1225;
                                }
                                catch (IOException e)
                                {
                                    c2bFragment.msg("Error");
                                }
                            }
                        }
                        if (motorAngle>46.0){
                            if (c2bFragment.btSocket!=null)
                            {
                                try
                                {
                                    c2bFragment.btSocket.getOutputStream().write("4".toString().getBytes());
                                    motorAngle=motorAngle+0.45;
                                }
                                catch (IOException e)
                                {
                                    c2bFragment.msg("Error");
                                }
                            }
                        }


                        //TODO: Ara işlem kısmı
                        //                    if ang<=38.0:
                        //                    ser.write(bytes('4', 'utf-8'))
                        //                    ang=ang+0.45
                        //                    elif ang>38.0 and ang<=40.0:
                        //                    ser.write(bytes('5', 'utf-8'))
                        //                    ang=ang+0.225
                        //                    elif ang>40.0 and ang<=46.0:
                        //                    ser.write(bytes('6', 'utf-8'))
                        //                    ang=ang+0.1125
                        //                    elif ang>46.0:
                        //                    ser.write(bytes('4', 'utf-8'))
                        //                    ang=ang+0.45
                        //                    time.sleep(1)
                        //TODO: DELAY Bu Şekilde Ekleniyor 3000 ms= 3 sn


                        //----------------
                        mState = STATE_WAITING_PRECAPTURE;
                        // 30 burst  yaparken STATE_WAITING_PRECAPTURE stateine düşür, 30lu bitince açı değiştirip yeni 30luyu çekerken STATE_WAITING_LOCK buna düşür tekrar focus ayarlayıp öyle çeksin
                    } else if(motorAngle >= 50) {
                        //TODO: Bitiş kısmı
//                        ser.write(bytes('7', 'utf-8'))
//                        time.sleep(3)
//                        print("Turned to zero position")
//                        ser.write(bytes('8', 'utf-8'))
//                        time.sleep(2)
//                        ser.write(bytes('9', 'utf-8'))
//                        time.sleep(2)
//                        print("turned off")
                        //TODO:
                        motorAngle = 31.5;
                        photoTaken = false;

                        //print("minimum angle of deviation",minang)
                        //print("measured refractive index",2 * math.sin(minang * 0.0174533))
                        new CameraUtils.InformationDialog("Minimum angle of deviation:" + minAngle + "measured refractive index" + 2 * Math.sin(minAngle * 0.0174533))
                                .show(c2bFragment.getChildFragmentManager(), FRAGMENT_DIALOG);
                        if (c2bFragment.btSocket!=null)
                        {
                            try
                            {
                                c2bFragment.btSocket.getOutputStream().write("7".toString().getBytes());

                            }
                            catch (IOException e)
                            {
                                c2bFragment.msg("Error");
                            }
                        }
                        try {
                            Thread.sleep(3000);
                        } catch (InterruptedException e) {
                            c2bFragment.msg("Error");
                        }
                        if (c2bFragment.btSocket!=null)
                        {
                            try
                            {
                                c2bFragment.btSocket.getOutputStream().write("8".toString().getBytes());

                            }
                            catch (IOException e)
                            {
                                c2bFragment.msg("Error");
                            }

                        }
                        try {
                            Thread.sleep(2000);
                        } catch (InterruptedException e) {
                            c2bFragment.msg("Error");
                        }
                        if (c2bFragment.btSocket!=null)
                        {
                            try
                            {
                                c2bFragment.btSocket.getOutputStream().write("9".toString().getBytes());

                            }
                            catch (IOException e)
                            {
                                c2bFragment.msg("Error");
                            }
                        }
//                        try {
//                            Thread.sleep(2000);
//                        } catch (InterruptedException e) {
//                            c2bFragment.msg("Error");
//                        }
                        //gerekli olursa açarsın
                        c2bFragment.msg("Turned Off");
                        minAngle = 0;
                    }
                    break;
                }
                case STATE_WAITING_LOCK: {
                    Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);
                    // For debug afstate showToast("" + afState);
                    if (afState == null) {
                        captureStillPicture();
                    } else if (CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED == afState ||
                            CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED == afState ||
                            2 == afState) {
                        // CONTROL_AE_STATE can be null on some devices
                        Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                        if (aeState == null ||
                                aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
                            mState = STATE_PICTURE_TAKEN;
                            captureStillPicture();
                        } else {
                            runPrecaptureSequence();
                        }
                    }
                    break;
                }
                case STATE_WAITING_PRECAPTURE: {
                    // CONTROL_AE_STATE can be null on some devices
                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                    if (aeState == null ||
                            aeState == 0 ||
                            aeState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE ||
                            aeState == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED) {
                        mState = STATE_WAITING_NON_PRECAPTURE;
                    }
                    break;
                }
                case STATE_WAITING_NON_PRECAPTURE: {
                    // CONTROL_AE_STATE can be null on some devices
                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                    if (aeState == null || aeState != CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
                        mState = STATE_PICTURE_TAKEN;
                        captureStillPicture();
                    }
                    break;
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
     * Shows a {@link Toast} on the UI thread.
     *
     * @param text The message to show
     */
    private void showToast(final String text) {
//        final Activity activity = getActivity();
        if (activity != null) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(activity, text, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private static void showToastStatic(final String text, final Activity activity) {
//        final Activity activity = getActivity();
        if (activity != null) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(activity, text, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    /**
     * Given {@code choices} of {@code Size}s supported by a camera, choose the smallest one that
     * is at least as large as the respective texture view size, and that is at most as large as the
     * respective max size, and whose aspect ratio matches with the specified value. If such size
     * doesn't exist, choose the largest one that is at most as large as the respective max size,
     * and whose aspect ratio matches with the specified value.
     *
     * @param choices           The list of sizes that the camera supports for the intended output
     *                          class
     * @param textureViewWidth  The width of the texture view relative to sensor coordinate
     * @param textureViewHeight The height of the texture view relative to sensor coordinate
     * @param maxWidth          The maximum width that can be chosen
     * @param maxHeight         The maximum height that can be chosen
     * @param aspectRatio       The aspect ratio
     * @return The optimal {@code Size}, or an arbitrary one if none were big enough
     */
    private static Size chooseOptimalSize(Size[] choices, int textureViewWidth,
                                          int textureViewHeight, int maxWidth, int maxHeight, Size aspectRatio) {

        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Size> bigEnough = new ArrayList<>();
        // Collect the supported resolutions that are smaller than the preview Surface
        List<Size> notBigEnough = new ArrayList<>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (Size option : choices) {
            if (option.getWidth() <= maxWidth && option.getHeight() <= maxHeight &&
                    option.getHeight() == option.getWidth() * h / w) {
                if (option.getWidth() >= textureViewWidth &&
                        option.getHeight() >= textureViewHeight) {
                    bigEnough.add(option);
                } else {
                    notBigEnough.add(option);
                }
            }
        }

        // Pick the smallest of those big enough. If there is no one big enough, pick the
        // largest of those not big enough.
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CameraUtils.CompareSizesByArea());
        } else if (notBigEnough.size() > 0) {
            return Collections.max(notBigEnough, new CameraUtils.CompareSizesByArea());
        } else {
            Log.e(TAG, "Couldn't find any suitable preview size");
            return choices[0];
        }
    }

    private void requestCameraPermission() {
        if (c2bFragment.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            new CameraUtils.ConfirmationDialog().show(c2bFragment.getChildFragmentManager(), FRAGMENT_DIALOG);
        } else {
            c2bFragment.requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
        }
    }

    /**
     * Sets up member variables related to camera.
     *
     * @param width  The width of available size for camera preview
     * @param height The height of available size for camera preview
     */
    @SuppressWarnings("SuspiciousNameCombination")
    private void setUpCameraOutputs(int width, int height) {
//        Activity activity = getActivity();
        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String cameraId : manager.getCameraIdList()) {
                CameraCharacteristics characteristics
                        = manager.getCameraCharacteristics(cameraId);

                // We don't use a front facing camera in this sample.
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    continue;
                }

                StreamConfigurationMap map = characteristics.get(
                        CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (map == null) {
                    continue;
                }

                // For still image captures, we use the largest available size.
                Size largest = Collections.max(
                        Arrays.asList(map.getOutputSizes(ImageFormat.YUV_420_888)),
                        new CameraUtils.CompareSizesByArea());
                mImageReader = ImageReader.newInstance(largest.getWidth(), largest.getHeight(),
                        ImageFormat.YUV_420_888, /*maxImages*/30);
                mImageReader.setOnImageAvailableListener(
                        mOnImageAvailableListener, mBackgroundHandler);

                // Find out if we need to swap dimension to get the preview size relative to sensor
                // coordinate.
                int displayRotation = activity.getWindowManager().getDefaultDisplay().getRotation();
                //noinspection ConstantConditions
                mSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
                boolean swappedDimensions = false;
                switch (displayRotation) {
                    case Surface.ROTATION_0:
                    case Surface.ROTATION_180:
                        if (mSensorOrientation == 90 || mSensorOrientation == 270) {
                            swappedDimensions = true;
                        }
                        break;
                    case Surface.ROTATION_90:
                    case Surface.ROTATION_270:
                        if (mSensorOrientation == 0 || mSensorOrientation == 180) {
                            swappedDimensions = true;
                        }
                        break;
                    default:
                        Log.e(TAG, "Display rotation is invalid: " + displayRotation);
                }

                Point displaySize = new Point();
                activity.getWindowManager().getDefaultDisplay().getSize(displaySize);
                int rotatedPreviewWidth = width;
                int rotatedPreviewHeight = height;
                int maxPreviewWidth = displaySize.x;
                int maxPreviewHeight = displaySize.y;

                if (swappedDimensions) {
                    rotatedPreviewWidth = height;
                    rotatedPreviewHeight = width;
                    maxPreviewWidth = displaySize.y;
                    maxPreviewHeight = displaySize.x;
                }

                if (maxPreviewWidth > MAX_PREVIEW_WIDTH) {
                    maxPreviewWidth = MAX_PREVIEW_WIDTH;
                }

                if (maxPreviewHeight > MAX_PREVIEW_HEIGHT) {
                    maxPreviewHeight = MAX_PREVIEW_HEIGHT;
                }

                // Danger, W.R.! Attempting to use too large a preview size could  exceed the camera
                // bus' bandwidth limitation, resulting in gorgeous previews but the storage of
                // garbage capture data.
                mPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
                        rotatedPreviewWidth, rotatedPreviewHeight, maxPreviewWidth,
                        maxPreviewHeight, largest);

                // We fit the aspect ratio of TextureView to the size of preview we picked.
                int orientation = c2bFragment.getResources().getConfiguration().orientation;
                if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    mTextureView.setAspectRatio(
                            mPreviewSize.getWidth(), mPreviewSize.getHeight());
                } else {
                    mTextureView.setAspectRatio(
                            mPreviewSize.getHeight(), mPreviewSize.getWidth());
                }

                // Check if the flash is supported.
                Boolean available = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                mFlashSupported = available == null ? false : available;

                mCameraId = cameraId;
                return;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (NullPointerException e) {
            // Currently an NPE is thrown when the Camera2API is used but not supported on the
            // device this code runs.
            CameraUtils.ErrorDialog.newInstance(c2bFragment.getString(R.string.camera_error))
                    .show(c2bFragment.getChildFragmentManager(), FRAGMENT_DIALOG);
        }
    }

    /**
     * Opens the camera specified by {@link Camera2BasicFragment#}.
     */
    public void openCamera(int width, int height) {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            requestCameraPermission();
            return;
        }
        setUpCameraOutputs(width, height);
        configureTransform(width, height);
//        Activity activity = getActivity();
        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        try {
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }
            manager.openCamera(mCameraId, mStateCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
        }
    }

    /**
     * Closes the current {@link CameraDevice}.
     */
    public void closeCamera() {
        try {
            mCameraOpenCloseLock.acquire();
            if (null != mCaptureSession) {
                mCaptureSession.close();
                mCaptureSession = null;
            }
            if (null != mCameraDevice) {
                mCameraDevice.close();
                mCameraDevice = null;
            }
            if (null != mImageReader) {
                mImageReader.close();
                mImageReader = null;
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
        } finally {
            mCameraOpenCloseLock.release();
        }
    }

    /**
     * Starts a background thread and its {@link Handler}.
     */
    public void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    /**
     * Stops the background thread and its {@link Handler}.
     */
    public void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Creates a new {@link CameraCaptureSession} for camera preview.
     */
    private void createCameraPreviewSession() {
        try {
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            assert texture != null;

            // We configure the size of default buffer to be the size of camera preview we want.
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());

            // This is the output Surface we need to start preview.
            Surface surface = new Surface(texture);

            // We set up a CaptureRequest.Builder with the output Surface.
            mPreviewRequestBuilder
                    = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mPreviewRequestBuilder.addTarget(surface);

            // Here, we create a CameraCaptureSession for camera preview.
            mCameraDevice.createCaptureSession(Arrays.asList(surface, mImageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {

                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                            // The camera is already closed
                            if (null == mCameraDevice) {
                                return;
                            }

                            // When the session is ready, we start displaying the preview.
                            mCaptureSession = cameraCaptureSession;
                            try {
                                // Auto focus should be continuous for camera preview.
                                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                                // Flash is automatically enabled when necessary.
                                //setAutoFlash(mPreviewRequestBuilder);


                                // Finally, we start displaying the camera preview.
                                mPreviewRequest = mPreviewRequestBuilder.build();
                                mCaptureSession.setRepeatingRequest(mPreviewRequest,
                                        mCaptureCallback, mBackgroundHandler);
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(
                                @NonNull CameraCaptureSession cameraCaptureSession) {
                            showToast("Failed");
                        }
                    }, null
            );
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Configures the necessary {@link android.graphics.Matrix} transformation to `mTextureView`.
     * This method should be called after the camera preview size is determined in
     * setUpCameraOutputs and also the size of `mTextureView` is fixed.
     *
     * @param viewWidth  The width of `mTextureView`
     * @param viewHeight The height of `mTextureView`
     */
    private void configureTransform(int viewWidth, int viewHeight) {
//        Activity activity = getActivity();
        if (null == mTextureView || null == mPreviewSize || null == activity) {
            return;
        }
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, mPreviewSize.getHeight(), mPreviewSize.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) viewHeight / mPreviewSize.getHeight(),
                    (float) viewWidth / mPreviewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180, centerX, centerY);
        }
        mTextureView.setTransform(matrix);
    }

    /**
     * Initiate a still image capture.
     */
    public void takePicture() {
//        try {
//            mFile = createImageFile();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
        //@TODO: Bütün ölçümler başlamadan önce (AÇILIŞ)
//        time.sleep(3) Bu optional, konması gerekiyo mu bilinmiyor

//        ser.write(bytes('1', 'utf-8'))
//        print("reset HIGH")
//        time.sleep(1)
//        ser.write(bytes('2', 'utf-8'))
//        print("sleep HIGH")
//        time.sleep(1)
//        ser.write(bytes('3', 'utf-8'))
//        time.sleep(1)
//        print("Motor turned to starting position")
//        try {
//            Thread.sleep(1000);
//        } catch (InterruptedException e) {
//            c2bFragment.msg("Error");
//        }
        //gerekirse burayı açarsın
        if (c2bFragment.btSocket!=null)
        {
            try
            {
                c2bFragment.btSocket.getOutputStream().write("1".toString().getBytes());
                c2bFragment.msg("reset HIGH");
            }
            catch (IOException e)
            {
                c2bFragment.msg("Error");
            }
        }
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            c2bFragment.msg("Error");
        }
        try
        {
            c2bFragment.btSocket.getOutputStream().write("2".toString().getBytes());
            c2bFragment.msg("sleep HIGH");
        }

        catch (IOException e)
        {
            c2bFragment.msg("Error");
        }
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            c2bFragment.msg("Error");
        }
        if (c2bFragment.btSocket!=null)
        {
            try
            {
                c2bFragment.btSocket.getOutputStream().write("3".toString().getBytes());
                c2bFragment.msg("Motor turned to starting position");
                motorAngle=31.5;
            }
            catch (IOException e)
            {
                c2bFragment.msg("Error");
            }
        }

        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        orientationAngle = getOrientation(rotation);
        lockFocus();
    }

    /**
     * Lock the focus as the first step for a still image capture.
     */
    private void lockFocus() {
        try {
            // This is how to tell the camera to lock focus.
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CameraMetadata.CONTROL_AF_TRIGGER_START);
            // Tell #mCaptureCallback to wait for the lock.
            mState = STATE_WAITING_LOCK;
            mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback,
                    mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Run the precapture sequence for capturing a still image. This method should be called when
     * we get a response in {@link #mCaptureCallback} from {@link #lockFocus()}.
     */
    private void runPrecaptureSequence() {
        try {
            // This is how to tell the camera to trigger.
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
            // Tell #mCaptureCallback to wait for the precapture sequence to be set.
            mState = STATE_WAITING_PRECAPTURE;
            mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback,
                    mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Capture a still picture. This method should be called when we get a response in
     * {@link #mCaptureCallback} from both {@link #lockFocus()}.
     */
    private void captureStillPicture() {
        try {
//            final Activity activity = getActivity();
            if (null == activity || null == mCameraDevice) {
                return;
            }
            // This is the CaptureRequest.Builder that we use to take a picture.
            final CaptureRequest.Builder captureBuilder =
                    mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(mImageReader.getSurface());

            // Use the same AE and AF modes as the preview.
            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            //setAutoFlash(captureBuilder);


            // Orientation
//            int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
//            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, getOrientation(rotation));

            CameraCaptureSession.CaptureCallback CaptureCallback
                    = new CameraCaptureSession.CaptureCallback() {

                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                               @NonNull CaptureRequest request,
                                               @NonNull TotalCaptureResult result) {
//                    showToast("Saved: " + mFile);
//                    Log.d(TAG, mFile.toString());
                    unlockFocus();
                }
            };
            List<CaptureRequest> captureList = new ArrayList<CaptureRequest>();
            for (int i = 0; i < 2; i++) {
                captureList.add(captureBuilder.build());
            }


//            Cancel any ongoing repeating capture set by either setRepeatingRequest or setRepeatingBurst(List , CameraCaptureSession.CaptureCallback, Handler). Has no effect on requests submitted through capture or captureBurst.
            mCaptureSession.stopRepeating();
            mCaptureSession.abortCaptures();
            mCaptureSession.captureBurst(captureList, CaptureCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Retrieves the JPEG orientation from the specified screen rotation.
     *
     * @param rotation The screen rotation.
     * @return The JPEG orientation (one of 0, 90, 270, and 360)
     */
    private static int getOrientation(int rotation) {
        // Sensor orientation is 90 for most devices, or 270 for some devices (eg. Nexus 5X)
        // We have to take that into account and rotate JPEG properly.
        // For devices with orientation of 90, we simply return our mapping from ORIENTATIONS.
        // For devices with orientation of 270, we need to rotate the JPEG 180 degrees.
        return (ORIENTATIONS.get(rotation) + mSensorOrientation + 270) % 360;
    }

    /**
     * Unlock the focus. This method should be called when still image capture sequence is
     * finished.
     */
    private void unlockFocus() {
        try {
            // Reset the auto-focus trigger
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
            //setAutoFlash(mPreviewRequestBuilder);

            mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback,
                    mBackgroundHandler);
            // After this, the camera will go back to the normal state of preview.
            mState = STATE_PREVIEW;
            mCaptureSession.setRepeatingRequest(mPreviewRequest, mCaptureCallback,
                    mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void setAutoFlash(CaptureRequest.Builder requestBuilder) {
        if (mFlashSupported) {
            requestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
        }
    }


    /**
     * Saves a JPEG {@link Image} into the specified {@link File}.
     */
    private static class ImageProcessor implements Runnable {

        /**
         * The JPEG image
         */
        private Image mImage;
        /**
         * The file we save the image into.
         */
//        private File mFile;

        private Activity activity;

        private Camera2BasicFragment c2bFragment;

        ImageProcessor(Image image, Camera2BasicFragment c2bFragment, Activity activity) {
            mImage = image;
//            mFile = file;
            this.c2bFragment = c2bFragment;
            this.activity = activity;
        }


        /**
         * Converts YUV420 NV21 to RGB8888
         *
         * @param data   byte array on YUV420 NV21 format.
         * @param width  pixels width
         * @param height pixels height
         * @return a RGB8888 pixels int array. Where each int is a pixels ARGB.
         */
        public static int[] convertYUV420_NV21toRGB8888(byte[] data, int width, int height) {
            int size = width * height;
            int offset = size;
            int[] pixels = new int[size];
            int u, v, y1, y2, y3, y4;

            // i percorre os Y and the final pixels
            // k percorre os pixles U e V
            for (int i = 0, k = 0; i < size; i += 2, k += 2) {
                y1 = data[i] & 0xff;
                y2 = data[i + 1] & 0xff;
                y3 = data[width + i] & 0xff;
                y4 = data[width + i + 1] & 0xff;

                u = data[offset + k] & 0xff;
                v = data[offset + k + 1] & 0xff;
                u = u - 128;
                v = v - 128;

                pixels[i] = convertYUVtoRGB(y1, u, v);
                pixels[i + 1] = convertYUVtoRGB(y2, u, v);
                pixels[width + i] = convertYUVtoRGB(y3, u, v);
                pixels[width + i + 1] = convertYUVtoRGB(y4, u, v);

                if (i != 0 && (i + 2) % width == 0)
                    i += width;
            }

            return pixels;
        }

        private static int convertYUVtoRGB(int y, int u, int v) {
            int r, g, b;

            r = y + (int) (1.772f * u);
            g = y - (int) (0.344f * u + 0.714f * v);
            b = y + (int) (1.402f * v);
            r = r > 255 ? 255 : r < 0 ? 0 : r;
            g = g > 255 ? 255 : g < 0 ? 0 : g;
            b = b > 255 ? 255 : b < 0 ? 0 : b;
            return 0xff000000 | (b << 16) | (g << 8) | r;
        }

        private static File createImageFile(Activity activity) throws IOException {
            // Create an image file name
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            String imageFileName = "PNG_" + timeStamp + "_";
            File storageDir = activity.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
            File image = File.createTempFile(
                    imageFileName,  /* prefix */
                    ".png",         /* suffix */
                    storageDir      /* directory */
            );

            // Save a file: path for use with ACTION_VIEW intents
            String currentPhotoPath = image.getAbsolutePath();
            return image;
        }

        /**
         * This method rotates the matrix 90 degrees clockwise by using extra
         * buffer.
         * @param matrix
         */
        public static int[][] rotateMatrix90Clockwise(int[][] matrix) {
            int[][] rotated = new int[matrix[0].length][matrix.length];

            for (int i = 0; i < matrix[0].length; ++i) {
                for (int j = 0; j < matrix.length; ++j) {


                    rotated[i][j] = matrix[matrix.length - j - 1][i];

                }
            }

            return rotated;
        }

        /**
         * This method rotates the matrix 90 degrees counter clockwise by using extra
         * buffer.
         */
        public static int[][] rotateMatrix90CounterClockwise(int[][] matrix) {
            int[][] rotated = new int[matrix[0].length][matrix.length];

            for (int i = 0; i < matrix[0].length; ++i) {
                for (int j = 0; j < matrix.length; ++j) {

                    rotated[i][j] = matrix[j][matrix[0].length - i - 1];
                }
            }

            return rotated;
        }


        @Override
        public void run() {
            try {
                // Mutex for lock. This lock mechanism is recommended to avoid collusions (Thread Safety)
                mutex.acquire();

                //---------------------------------------
                // 1) Transform YUV to RGB Pixels one Dimensional Array:
                //      Taken mImage is in YUV Format
                //      We need to transform RGB format
                Image.Plane Y = mImage.getPlanes()[0];
                Image.Plane U = mImage.getPlanes()[1];
                Image.Plane V = mImage.getPlanes()[2];

                int Yb = Y.getBuffer().remaining();
                int Ub = U.getBuffer().remaining();
                int Vb = V.getBuffer().remaining();

                byte[] data = new byte[Yb + Ub + Vb];
                //your data length should be this byte array length.

                Y.getBuffer().get(data, 0, Yb);
                U.getBuffer().get(data, Yb, Ub);
                V.getBuffer().get(data, Yb + Ub, Vb);
                int mImageWidth = mImage.getWidth();
                int mImageHeight = mImage.getHeight();
                double pixelsmHeightSum = 0.0;
                double pixelsmWidthSum = 0.0;
                int divide = 0;

                int[] pixels = convertYUV420_NV21toRGB8888(data, mImageWidth, mImageHeight);
                //---------------------------------------


                //---------------------------------------
                // 2) Transform to 2D Array:
                //      Our pixels RGB array is one dimensional. Transform this to 2 dimensional
                int[][] array2D = new int[mImageHeight][mImageWidth];
                for (int y = 0; y < mImageHeight; y++) {
                    for (int x = 0; x < mImageWidth; x++) {
                        int pixelsPlace = x + y * mImageWidth;
                        array2D[y][x] = pixels[pixelsPlace];
                    }
                }
                //---------------------------------------


                //---------------------------------------
                // 3) Detect RED pixels:
                for (int y = 0; y < mImageHeight; ++y) {
                    for (int x = 0; x < mImageWidth; ++x) {
                        int colorDetect = array2D[y][x];
                        if (Color.red(colorDetect) > 236) {

                            pixelsmHeightSum += y;
                            pixelsmWidthSum += x;
                            divide++;

                        }
                    }
                }
                //---------------------------------------

                // This line for image saving into storage (For Debug Exceptions and Unexpected Situations)
                //Bitmap bitmap = Bitmap.createBitmap(pixels, mImageWidth, mImageHeight, Bitmap.Config.ARGB_8888);

                //---------------------------------------
                // 4) Fake Rotate If Its Needed:
                //      If the phone is in Landscape mode (not Portrait mode) we need to rotate (For orientation is 90 or 270)
                //      But we DID NOT ROTATE THE ORIGINAL
                //      Just switch width-height calculations results (All we Need)
                double temp1 = 0;
                int temp2 = 0;
                if (orientationAngle == 90 || orientationAngle == 270) {
                    temp1 = pixelsmWidthSum;
                    pixelsmWidthSum = pixelsmHeightSum;
                    pixelsmHeightSum = temp1;

                    temp2 = mImageWidth;
                    mImageWidth = mImageHeight;
                    pixelsmHeightSum = temp2;
                }
                //---------------------------------------

                //---------------------------------------
                //


                try {
                    if(divide != 0) {
                        double centerRedHeight = pixelsmHeightSum / divide;
                        double centerRedWidth = pixelsmWidthSum / divide;
                        double deviationFromCenter = abs(centerRedWidth - (mImageWidth - 1) / 2.0 );

                        deviationFromCenter = Math.round(deviationFromCenter * 100) / 100.0;
                        totalDeviation += deviationFromCenter;
                        divideDev++;


                        //showToastStatic("Red Dot Deviation from Horizontal Center:" + deviationFromCenter + "pixel" + "   (Total Width" + mImageWidth + " pixel)", activity);
                    } else {
                        showToastStatic("No red light found", activity);
                    }
                } catch (Exception e) {
                    showToastStatic("No red light found", activity);
                } finally {
                    if (takenPictureOnOneAngle >= 29) {
                        
                        if(divideDev != 0) {
                            totalDeviation /= divideDev;
                            totalDeviation = Math.round(totalDeviation * 100) / 100.0;
                            //@TODO: bu show log olcak ve bu açının değeri yazcak, bu açıdaki sapma yazcak ve o ana kadarki tüm açıların en az deviationlısının değerini de yaz
                            showToastStatic("Red Dot Deviation from Horizontal Center:" + totalDeviation + "pixel" + "   (Total Width" + mImageWidth + " pixel) Angle:" + motorAngle, activity);

                            if(totalDeviation < minDeviation){
                                minDeviation = totalDeviation;
                                minAngle = motorAngle;
                            }
                        }
                        else {
                            showToastStatic("No red light found", activity);
                        }

                        // Reset
                        takenPictureOnOneAngle = -1;
                        totalDeviation = 0;
                        divideDev = 0;
                    }
                    ++takenPictureOnOneAngle;
                    //bitmape cast ederek sonra jpeg kaydederken bi sıkıntısı var widthi 1526dan 1200e indiriyo kırpıyo
//                    File mFile = createImageFile(activity);
//                    try (FileOutputStream out = new FileOutputStream(mFile)) {
//                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out); // bmp is your Bitmap instance
//                        // PNG is a lossless format, the compression factor (100) is ignored
//                    } catch (IOException e) {
//                        e.printStackTrace();
//                    }
                    //showToastStatic("Çağırdım:" + c2bFragment.globali, activity);
                    mImage.close();
                    mutex.release();
                }
            } catch (Exception e) {
                showToastStatic("Exception yedik", activity);
                showToastStatic(e.getMessage(), activity);
            }
        }
    }


    //Burdaki kodlar bittikten sonra bu fonksiyonun adını değiştir, save fonksiyonu yerine çekilen fotoları işleme fonksiyonu olsun


    /**
     * Compares two {@code Size}s based on their areas.
     */
    static class CompareSizesByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }

    }

    /**
     * Shows an error message dialog.
     */
    public static class ErrorDialog extends DialogFragment {

        private static final String ARG_MESSAGE = "message";

        public static CameraUtils.ErrorDialog newInstance(String message) {
            CameraUtils.ErrorDialog dialog = new CameraUtils.ErrorDialog();
            Bundle args = new Bundle();
            args.putString(ARG_MESSAGE, message);
            dialog.setArguments(args);
            return dialog;
        }

        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Activity activity = getActivity();
            return new AlertDialog.Builder(activity)
                    .setMessage(getArguments().getString(ARG_MESSAGE))
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            activity.finish();
                        }
                    })
                    .create();
        }

    }

    /**
     * Shows OK/Cancel confirmation dialog about camera permission.
     */
    public static class ConfirmationDialog extends DialogFragment {

        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Fragment parent = getParentFragment();
            return new AlertDialog.Builder(getActivity())
                    .setMessage(R.string.request_permission)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            parent.requestPermissions(new String[]{Manifest.permission.CAMERA},
                                    REQUEST_CAMERA_PERMISSION);
                        }
                    })
                    .setNegativeButton(android.R.string.cancel,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    Activity activity = parent.getActivity();
                                    if (activity != null) {
                                        activity.finish();
                                    }
                                }
                            })
                    .create();
        }
    }

    @SuppressLint("ValidFragment")
    public static class InformationDialog extends DialogFragment {

        public String infoMessage;

        @SuppressLint("ValidFragment")
        public InformationDialog(String infoMessage) {
            this.infoMessage = infoMessage;
        }

        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Fragment parent = getParentFragment();
            return new AlertDialog.Builder(getActivity())
                    .setMessage(infoMessage)
                    .setPositiveButton(android.R.string.ok, null)
                    .create();
        }
    }
}

