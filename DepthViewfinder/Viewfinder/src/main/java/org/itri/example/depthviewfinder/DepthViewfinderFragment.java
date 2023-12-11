/*
 * Copyright (C) 2014 The Android Open Source Project
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

package org.itri.example.depthviewfinder;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.renderscript.RenderScript;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.UiThread;
import com.google.android.material.snackbar.Snackbar;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

import android.util.Log;
import android.util.Size;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import static android.content.Context.CAMERA_SERVICE;

/**
 * A small demo of advanced camera functionality with the Android camera2 API.
 *
 * <p>This demo implements a real-time depth camera viewfinder,
 * by convert the sensor's depth output to RGB value.</p>
  */
public class DepthViewfinderFragment extends Fragment implements
        SurfaceHolder.Callback, CameraOps.ErrorDisplayer, CameraOps.CameraReadyListener, DepthViewfinderProcessor.StateListener {

    public static final String ARG_HIDE_CONTROL_KEY = "HideControl";
    public static final boolean ARG_HIDE_CONTROL_DEFAULT = false;

    private static final String TAG = "DepthViewfinderDemo";

    private static final String FRAGMENT_DIALOG = "dialog";

    private static final int REQUEST_PERMISSIONS_REQUEST_CODE = 34;

    private static final String[] MODE_DESCRIPTION = { "Gray", "Color", "Confidence" };

    private Context mHost;
    /**
     * View for the camera preview.
     */
    private FixedAspectSurfaceView mPreviewView;

    /**
     * Root view of this activity.
     */
    private View rootView;

    /**
     * This shows the current mode of the app.
     */
    private TextView mModeText;
    private TextView mConfidenceThresholdText;
    private TextView mAutoExposureText;
    private TextView mDepthMinMaxText, mDepthCurrentText;
    private Button mSnapshotButton;

    private Handler mUiHandler;

    private CameraCharacteristics mCameraInfo;

    private Surface mPreviewSurface;
    private Surface mProcessingSurface;

    CaptureRequest mPreviewRequest;

    RenderScript mRS;
    DepthViewfinderProcessor mProcessor;
    CameraManager mCameraManager;
    CameraOps mCameraOps;

    private int mDepthImageMode = 0;
    private int mConfidenceThreshold = 0;

    private FrameLayout mPreviewContainer;
    private View mPOIView;
    private Paint mCustomDrawPaint = new Paint();
    private PointF mPOI; // point of interest in view
    private Point mImagePOI; // point of interest in image
    private int mPOIRange = -1; // depth range of POI
    private float mPOIConfidence = Float.NaN; // depth confidence of POI

    private boolean mHideControl = ARG_HIDE_CONTROL_DEFAULT;

    // Durations in nanoseconds
    private static final long MICRO_SECOND = 1000;
    private static final long MILLI_SECOND = MICRO_SECOND * 1000;
    private static final long ONE_SECOND = MILLI_SECOND * 1000;

    //add by ynhuang @ 20200821 for metadata
    Size sensorArraySize = null;
    Rect preCorrectionArraySize = null;
    Rect activeArraySize = null;
    float[] distortion = null;
    float[] intrinsicCalibration = null;
    float[] poseTranslation = null;
    float[] poseRotation = null;
    int dimensionX = 0;
    int dimensionY = 0;
    //add by ynhuang end

    public static DepthViewfinderFragment newInstance(boolean hideControl) {
        DepthViewfinderFragment fragment = new DepthViewfinderFragment();
        Bundle args = new Bundle();
        args.putBoolean(ARG_HIDE_CONTROL_KEY, hideControl);
        fragment.setArguments(args);

        return fragment;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        mHost = context;

        Bundle args = getArguments();
        if (args != null) {
            mHideControl = args.getBoolean(ARG_HIDE_CONTROL_KEY, ARG_HIDE_CONTROL_DEFAULT);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        setContentView(R.layout.activity_depth_viewfinder);

//        rootView = findViewById(R.id.panels);
        rootView = inflater.inflate(R.layout.activity_depth_viewfinder, container, false);
        setHasOptionsMenu(true);

        if (mHideControl) {
            rootView.findViewById(R.id.control_bar_contents).setVisibility(View.GONE);
        }

        return rootView;
    }

    @RequiresApi(api = Build.VERSION_CODES.P)
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        mPreviewView = view.findViewById(R.id.depth_preview);
        mPreviewView.getHolder().addCallback(this);
        mPreviewView.setGestureListener(mHost, mViewListener);
        mPreviewView.setKeepScreenOn(true);

        mPOIView = new View(mHost) {
            @Override
            protected void onDraw(Canvas canvas) {
//                super.onDraw(canvas);

                if (mPOI == null) return;

                // draw POI
                if (mDepthImageMode == 0) {
                    mCustomDrawPaint.setColor(Color.RED);
                }
                else if (mDepthImageMode == 1) {
                    mCustomDrawPaint.setColor(Color.CYAN);
                }
                else if (mDepthImageMode == 2) {
                    mCustomDrawPaint.setColor(Color.GREEN);
                }
                mCustomDrawPaint.setStrokeCap(Paint.Cap.ROUND);
                mCustomDrawPaint.setStrokeWidth(15.0f);
                canvas.drawPoint(mPOI.x, mPOI.y, mCustomDrawPaint);

                mCustomDrawPaint.setStrokeWidth(3.0f);
                mCustomDrawPaint.setStyle(Paint.Style.STROKE);
                canvas.drawCircle(mPOI.x, mPOI.y, 30.0f, mCustomDrawPaint);

            }
        };

        mPreviewContainer = view.findViewById(R.id.preview_container);
        mPreviewContainer.addView(mPOIView, mPreviewView.getLayoutParams());

        Button helpButton = view.findViewById(R.id.help_button);
        helpButton.setOnClickListener(mHelpButtonListener);

        mModeText = view.findViewById(R.id.mode_label);
        mConfidenceThresholdText = view.findViewById(R.id.confidence_threshold);
        mDepthMinMaxText = view.findViewById(R.id.depth_min_max);
        mDepthCurrentText = view.findViewById(R.id.depth_current);
        mAutoExposureText = view.findViewById(R.id.auto_exposure);

        mConfidenceThresholdText.setText(String.format(Locale.US, "%.1f %%", mConfidenceThreshold * 100.0 / 7.0));

        mUiHandler = new Handler(Looper.getMainLooper());

        mRS = RenderScript.create(mHost);

        mSnapshotButton = view.findViewById(R.id.snapshot_button);
        mSnapshotButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                triggerSnapshot();
            }
        });

        // When permissions are revoked the app is restarted so onCreate is sufficient to check for
        // permissions core to the Activity's functionality.
        if (!checkCameraPermissions()) {
            requestCameraPermissions();
        } else {
            findAndOpenCamera();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();

        // Wait until camera is closed to ensure the next application can open it
        if (mCameraOps != null) {
            mCameraOps.closeCameraAndWait();
            mCameraOps = null;
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mHost = null;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.main, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.info) {
            MessageDialogFragment.newInstance(R.string.depth_viewfinder_intro_message)
                    .show(getChildFragmentManager(), FRAGMENT_DIALOG);
        }
        return super.onOptionsItemSelected(item);
    }

    @UiThread
    public void triggerSnapshot() {
        if (null == mOnTakePictureListener) {
            mSnapshotButton.setEnabled(false);
        }

        mProcessor.triggerSnapshot(new DepthViewfinderProcessor.SnapshotListener() {
            @Override
            public void onSnapshotReady(Bitmap depthImage, short[] depthRaw, long timestamp) {
                if (null == mOnTakePictureListener) {
                    new SnapshotSaver(mHost, depthImage, depthRaw).execute();
                }
                else {
                    mOnTakePictureListener.onTakePicture(DepthViewfinderFragment.this, depthImage, depthRaw, timestamp);
                }
            }

            @Override
            public void onSnapshotFailed(String errorString) {
                if (null == mOnTakePictureListener) {
                    mSnapshotButton.setEnabled(true);
                    Snackbar.make(rootView, errorString, Snackbar.LENGTH_SHORT).show();
                }
                else {
                    mOnTakePictureListener.onTakePicture(DepthViewfinderFragment.this,null, null, 0);
                }
            }
        }, mUiHandler);
    }

    private OnTakePictureListener mOnTakePictureListener;

    public void setOnTakePictureListener(OnTakePictureListener listener) {
        mOnTakePictureListener = listener;
    }

    private GestureDetector.OnGestureListener mViewListener
            = new GestureDetector.SimpleOnGestureListener() {

        @Override
        public boolean onDown(MotionEvent e) {
            return true;
        }

        @Override
        public void onShowPress(MotionEvent e) {
            super.onShowPress(e);

            // clear point of interest
            mPOI = null;
            mImagePOI = null;

            mPOIView.invalidate();
        }

        @Override
        public void onLongPress(MotionEvent e) {
            super.onLongPress(e);

            // save point of interest
            mPOI = new PointF(e.getAxisValue(MotionEvent.AXIS_X), e.getAxisValue(MotionEvent.AXIS_Y));

            int previewWidth = mPreviewView.getWidth();
            int previewHeight = mPreviewView.getHeight();
            Rect frameRect = mPreviewView.getHolder().getSurfaceFrame();
            float imageX = mPOI.x * frameRect.width() / previewWidth;
            float imageY = mPOI.y * frameRect.height() / previewHeight;

            mImagePOI = new Point((int)imageX, (int)imageY);

            Log.d(TAG, "onLongPress: preview = " + previewWidth + " x " + previewHeight + ", POI = (" + mPOI.x + ", " + mPOI.y + ")");
            Log.d(TAG, "onLongPress: image = " + frameRect + ", ImagePOI = (" + mImagePOI.x + ", " + mImagePOI.y + ")");

            // draw POI
            mPOIView.invalidate();
        }

        @Override
        public boolean onDoubleTapEvent(MotionEvent e) {
            if(e.getAction() == MotionEvent.ACTION_UP) {
                mDepthImageMode = setOutputMode((mDepthImageMode + 1) % 3);
                mPOIView.invalidate();
                return true;
            }
            return false;
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            int threshold = mConfidenceThreshold;
            double step = mPreviewView.getHeight() / 32.0;
//            double move = distanceY / step;
//            double move = (e1.getAxisValue(MotionEvent.AXIS_Y) - e2.getAxisValue(MotionEvent.AXIS_Y)) / step;
            double move = (distanceY >= 0) ? Math.sqrt(distanceY / step) : -Math.sqrt(-distanceY / step);

            threshold += (move >= 0) ? Math.floor(move) : -Math.floor(-move);
//            Log.d(TAG, "onScroll: current threshold = " + threshold + ", step = " + step + ", move = " + move);

            threshold = Math.max(0, Math.min(threshold, 7));

            if (mProcessor != null) {
                try {
                    mProcessor.setConfidenceThreshold(threshold);
                    mConfidenceThreshold = threshold;
//                    Log.d(TAG, "onScroll: success to set confidence threshold " + threshold);
                    mConfidenceThresholdText.setText(String.format(Locale.US, "%.1f %%", mConfidenceThreshold * 100.0 / 7.0));
                }
                catch (Exception e) {
                    Log.w(TAG, "onScroll: fail to set confidence threshold " + threshold, e);
                }
            }
            else {
                Log.w(TAG, "onScroll: DepthViewfinderProcessor is null");
            }

            return true;
        }
    };

    /**
     * Show help dialogs.
     */
    private View.OnClickListener mHelpButtonListener = new View.OnClickListener() {
        public void onClick(View v) {
            MessageDialogFragment.newInstance(R.string.depth_viewfinder_help_text)
                    .show(getChildFragmentManager(), FRAGMENT_DIALOG);
        }
    };

    /**
     * Return the current state of the camera permissions.
     */
    private boolean checkCameraPermissions() {
        int permissionState = ActivityCompat.checkSelfPermission(mHost, Manifest.permission.CAMERA);

        // Check if the Camera permission is already available.
        if (permissionState != PackageManager.PERMISSION_GRANTED) {
            // Camera permission has not been granted.
            Log.i(TAG, "CAMERA permission has NOT been granted.");
            return false;
        } else {
            // Camera permissions are available.
            Log.i(TAG, "CAMERA permission has already been granted.");
            return true;
        }
    }

    /**
     * Attempt to initialize the camera.
     */
    private void initializeCamera() {
        mCameraManager = (CameraManager) mHost.getSystemService(CAMERA_SERVICE);
        if (mCameraManager != null) {
            mCameraOps = new CameraOps(mCameraManager,
                /*errorDisplayer*/ this,
                /*readyListener*/ this,
                /*readyHandler*/ mUiHandler);
        } else {
            Log.e(TAG, "Couldn't initialize the camera");
        }
    }

    private void requestCameraPermissions() {
        // Provide an additional rationale to the user. This would happen if the user denied the
        // request previously, but didn't check the "Don't ask again" checkbox.
        if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            Log.i(TAG, "Displaying camera permission rationale to provide additional context.");
            Snackbar.make(rootView, R.string.camera_permission_rationale, Snackbar
                    .LENGTH_INDEFINITE)
                    .setAction(R.string.ok, new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            // Request Camera permission
                            requestPermissions(new String[]{Manifest.permission.CAMERA},
                                    REQUEST_PERMISSIONS_REQUEST_CODE);
                        }
                    })
                    .show();
        } else {
            Log.i(TAG, "Requesting camera permission");
            // Request Camera permission. It's possible this can be auto answered if device policy
            // sets the permission in a given state or the user denied the permission
            // previously and checked "Never ask again".
            requestPermissions(new String[]{Manifest.permission.CAMERA},
                    REQUEST_PERMISSIONS_REQUEST_CODE);
        }
    }

    /**
     * Callback received when a permissions request has been completed.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        Log.i(TAG, "onRequestPermissionResult");
        if (requestCode == REQUEST_PERMISSIONS_REQUEST_CODE) {
            if (grantResults.length <= 0) {
                // If user interaction was interrupted, the permission request is cancelled and you
                // receive empty arrays.
                Log.i(TAG, "User interaction was cancelled.");
            } else if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission was granted.
                findAndOpenCamera();
            } else {
                // Permission denied.

                // In this Activity we've chosen to notify the user that they
                // have rejected a core permission for the app since it makes the Activity useless.
                // We're communicating this message in a Snackbar since this is a sample app, but
                // core permissions would typically be best requested during a welcome-screen flow.

                // Additionally, it is important to remember that a permission might have been
                // rejected without asking the user for permission (device policy or "Never ask
                // again" prompts). Therefore, a user interface affordance is typically implemented
                // when permissions are denied. Otherwise, your app could appear unresponsive to
                // touches or interactions which have required permissions.
                Snackbar.make(rootView, R.string.camera_permission_denied_explanation, Snackbar
                        .LENGTH_INDEFINITE)
                        .setAction(R.string.settings, new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                // Build intent that displays the App settings screen.
                                Intent intent = new Intent();
                                intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                                Uri uri = Uri.fromParts("package", getContext().getPackageName(), null);
                                intent.setData(uri);
                                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                startActivity(intent);
                            }
                        })
                        .show();
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.P)
    private void findAndOpenCamera() {
        boolean cameraPermissions = checkCameraPermissions();
        if (!cameraPermissions) {
            return;
        }
        String errorMessage = "Unknown error";
        boolean foundCamera = false;
        initializeCamera();
        if (mCameraOps != null) {
            try {
                // Find first back-facing camera that has necessary capability.
                String[] cameraIds = mCameraManager.getCameraIdList();
                for (String id : cameraIds) {
                    CameraCharacteristics info = mCameraManager.getCameraCharacteristics(id);
                    Integer facing = info.get(CameraCharacteristics.LENS_FACING);

                    int[] capabilities = info
                            .get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
                    boolean hasDepthOutput = hasCapability(capabilities,
                            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_DEPTH_OUTPUT);

                    // All these are guaranteed by
                    // CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL, but checking
                    // for only the things we care about expands range of devices we can run on.
                    // We want:
                    //  - Back-facing camera
                    //  - Depth output
                    if (Objects.equals(facing, CameraCharacteristics.LENS_FACING_BACK) && hasDepthOutput) {
                        // Found suitable camera - get info, open, and set up outputs
                        mCameraInfo = info;
                        mCameraOps.openCamera(id);
                        configureSurfaces();
                        foundCamera = true;

                        //add by ynhuang @ 20200821
                        sensorArraySize =  info.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE);
                        preCorrectionArraySize = info.get(CameraCharacteristics.SENSOR_INFO_PRE_CORRECTION_ACTIVE_ARRAY_SIZE);
                        activeArraySize = info.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
                        distortion = info.get(CameraCharacteristics.LENS_DISTORTION);
                        intrinsicCalibration = info.get(CameraCharacteristics.LENS_INTRINSIC_CALIBRATION);
                        poseTranslation = info.get(CameraCharacteristics.LENS_POSE_TRANSLATION);
                        poseRotation = info.get(CameraCharacteristics.LENS_POSE_ROTATION);
                        //add by ynhuang end
//                        Log.d(TAG, "tof sensor array size: " + info.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE));
//                        Log.d(TAG, "tof pre-corection array size: " + info.get(CameraCharacteristics.SENSOR_INFO_PRE_CORRECTION_ACTIVE_ARRAY_SIZE));
//                        Log.d(TAG, "of active array size: " + info.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE));
//                        Log.d(TAG, "tof distortion: " + info.get(CameraCharacteristics.LENS_DISTORTION));
//                        Log.d(TAG, "tof intrinsic calibration: " + info.get(CameraCharacteristics.LENS_INTRINSIC_CALIBRATION));
//                        Log.d(TAG, "tof pose translation: " + info.get(CameraCharacteristics.LENS_POSE_TRANSLATION));
//                        Log.d(TAG, "tof pose rotation: " + info.get(CameraCharacteristics.LENS_POSE_ROTATION));

                        break;
                    }
                }
                if (!foundCamera) {
                    errorMessage = getString(R.string.camera_no_good);
                }
            } catch (CameraAccessException e) {
                errorMessage = getErrorString(e);
            }
            if (!foundCamera) {
                showErrorDialog(errorMessage);
            }
        }
    }

    private boolean hasCapability(int[] capabilities, int capability) {
        for (int c : capabilities) {
            if (c == capability) return true;
        }
        return false;
    }

    private int setOutputMode(int mode) {
        int newOutputMode = mDepthImageMode;

        if (mCameraOps != null) {
            if (mProcessor != null) {
                mProcessor.setDepthImageMode(mode);
                newOutputMode = mode;
                mModeText.setText("Mode: " + MODE_DESCRIPTION[mode]);
            }

            mCameraOps.setRepeatingRequest(mPreviewRequest, mCaptureCallback, mUiHandler);
        }

        return newOutputMode;
    }

    /**
     * Configure the surfaceview and RS processing.
     */
    private void configureSurfaces() {
        // Find a good size for output - largest 16:9 aspect ratio that's less than 720p
        final int MAX_WIDTH = 1280;
        final float TARGET_ASPECT = 16.f / 9.f;
        final float ASPECT_TOLERANCE = 0.1f;

        StreamConfigurationMap configs =
                mCameraInfo.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        if (configs == null) {
            throw new RuntimeException("Cannot get available picture/preview sizes.");
        }
//        Size[] outputSizes = configs.getOutputSizes(SurfaceHolder.class);
        Size[] outputSizes = configs.getOutputSizes(ImageFormat.DEPTH16);

        Size outputSize = outputSizes[0];
        float outputAspect = (float) outputSize.getWidth() / outputSize.getHeight();
        for (Size candidateSize : outputSizes) {
            if (candidateSize.getWidth() > MAX_WIDTH) continue;
            float candidateAspect = (float) candidateSize.getWidth() / candidateSize.getHeight();
            boolean goodCandidateAspect =
                    Math.abs(candidateAspect - TARGET_ASPECT) < ASPECT_TOLERANCE;
            boolean goodOutputAspect =
                    Math.abs(outputAspect - TARGET_ASPECT) < ASPECT_TOLERANCE;
            if ((goodCandidateAspect && !goodOutputAspect) ||
                    candidateSize.getWidth() > outputSize.getWidth()) {
                outputSize = candidateSize;
                outputAspect = candidateAspect;
            }
        }
        Log.i(TAG, "Resolution chosen: " + outputSize);

        // Configure processing
        mProcessor = new DepthViewfinderProcessor(mRS, outputSize);
        mProcessor.setStateListener(this, mUiHandler);
        setupProcessor();

        // Configure the output view - this will fire surfaceChanged
        mPreviewView.setAspectRatio(outputAspect);
        mPreviewView.getHolder().setFixedSize(outputSize.getWidth(), outputSize.getHeight());
    }

    /**
     * Once camera is open and output surfaces are ready, configure the RS processing
     * and the camera device inputs/outputs.
     */
    private void setupProcessor() {
        if (mProcessor == null || mPreviewSurface == null) return;

        mProcessor.setOutputSurface(mPreviewSurface);
        mProcessingSurface = mProcessor.getInputSurface();

        List<Surface> cameraOutputSurfaces = new ArrayList<>();
        cameraOutputSurfaces.add(mProcessingSurface);

        mCameraOps.setSurfaces(cameraOutputSurfaces);
    }

    /**
     * Listener for completed captures
     * Invoked on UI thread
     */
    private CameraCaptureSession.CaptureCallback mCaptureCallback
            = new CameraCaptureSession.CaptureCallback() {

        public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                       @NonNull CaptureRequest request,
                                       @NonNull TotalCaptureResult result) {

            // Only update UI every so many frames
            // Use an odd number here to ensure both even and odd exposures get an occasional update
            long frameNumber = result.getFrameNumber();
            if (frameNumber % 3 != 0) return;

            final Long exposureTime = result.get(CaptureResult.SENSOR_EXPOSURE_TIME);
            if (exposureTime == null) {
                throw new RuntimeException("Cannot get exposure time.");
            }

            // Format exposure time nicely
            String exposureText;
            if (exposureTime > ONE_SECOND) {
                exposureText = String.format(Locale.US, "%.2f s", exposureTime / 1e9);
            } else if (exposureTime > MILLI_SECOND) {
                exposureText = String.format(Locale.US, "%.2f ms", exposureTime / 1e6);
            } else if (exposureTime > MICRO_SECOND) {
                exposureText = String.format(Locale.US, "%.2f us", exposureTime / 1e3);
            } else {
                exposureText = String.format(Locale.US, "%d ns", exposureTime);
            }

            final Long frameDuration = result.get(CaptureResult.SENSOR_FRAME_DURATION);
            double frameRate = 1e9 / frameDuration;

            mAutoExposureText.setText(exposureText + String.format(" @ %.1f fps", frameRate));
        }
    };

    /**
     * Callbacks for the FixedAspectSurfaceView
     */

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        mPreviewSurface = holder.getSurface();

        setupProcessor();
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        // ignored
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        mPreviewSurface = null;
    }

    /**
     * Callbacks for CameraOps
     */
    @Override
    public void onCameraReady() {
        // Ready to send requests in, so set them up
        try {
            CaptureRequest.Builder previewBuilder =
                    mCameraOps.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            previewBuilder.addTarget(mProcessingSurface);
            mPreviewRequest = previewBuilder.build();

            mDepthImageMode = setOutputMode(mDepthImageMode);
        } catch (CameraAccessException e) {
            String errorMessage = getErrorString(e);
            showErrorDialog(errorMessage);
        }
    }

    @Override
    public void onProcessingStart(DepthViewfinderProcessor processor, int dropFrames) {
        if (dropFrames > 0) {
            Log.d(TAG, "onProcessingStart: dropFrames = " + dropFrames);
        }
    }

    @Override
    public void onProcessingFinished(DepthViewfinderProcessor processor, DepthViewfinderProcessor.ProcessingResult result) {
        mDepthMinMaxText.setText(String.format(Locale.US, "%d ~ %d mm", result.getDepthMin(), result.getDepthMax()));
        float minRes = ((float)result.getDepthMin())/10;
        mOnDepthMinListener.onDepthMin(DepthViewfinderFragment.this, minRes);

        if (mImagePOI != null) {
            mPOIRange = result.getDepthRange(mImagePOI.x, mImagePOI.y);
            mPOIConfidence = result.getDepthConfidence(mImagePOI.x, mImagePOI.y);
//            Log.d(TAG, "onProcessingFinished: range = " + mPOIRange + ", confidence = " + mPOIConfidence);

            mDepthCurrentText.setText(String.format(Locale.US, "%d mm (%.0f%%)", mPOIRange, (mPOIConfidence * 100.0f)));
        }
        else {
            mDepthCurrentText.setText("");
        }

        // must call close() to release buffer
        result.close();
    }

    @Override
    public void onProcessingError(DepthViewfinderProcessor processor, String errorString) {
        Log.w(TAG, "onProcessingError: " + errorString);
    }

    /**
     * Utility methods
     */
    @Override
    public void showErrorDialog(String errorMessage) {
        MessageDialogFragment.newInstance(errorMessage)
                .show(getChildFragmentManager(), FRAGMENT_DIALOG);
    }

    @SuppressLint({"SwitchIntDef", "StringFormatMatches"})
    @Override
    public String getErrorString(CameraAccessException e) {
        String errorMessage;
        switch (e.getReason()) {
            case CameraAccessException.CAMERA_DISABLED:
                errorMessage = getString(R.string.camera_disabled);
                break;
            case CameraAccessException.CAMERA_DISCONNECTED:
                errorMessage = getString(R.string.camera_disconnected);
                break;
            case CameraAccessException.CAMERA_ERROR:
                errorMessage = getString(R.string.camera_error);
                break;
            default:
                errorMessage = getString(R.string.camera_unknown, e.getReason());
                break;
        }
        return errorMessage;
    }

    private class SnapshotSaver extends AsyncTask<Void, Void, Boolean> {
//        private WeakReference<Context> mActivityReference;
        private Bitmap mDepthImage;
        private short[] mDepthRaw;
        File mSnapFile;
        FileOutputStream mSnapOutput = null;

        public SnapshotSaver(Context context, Bitmap depthImage, short[] depthRaw) {
//            mActivityReference = new WeakReference<>(context);
            mDepthImage = depthImage;
            mDepthRaw = depthRaw;
            mSnapFile = new File(context.getExternalFilesDir(null), String.format(Locale.ENGLISH, "depth_%s.jpg", new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss.SS", Locale.ENGLISH).format(new Date())));
        }

        @Override
        protected Boolean doInBackground(Void... voids) {
            boolean success = false;
            try {
                mSnapOutput = new FileOutputStream(mSnapFile);
                success = mDepthImage.compress(Bitmap.CompressFormat.JPEG, 100, mSnapOutput);
                mDepthImage.recycle();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            finally {
                if (mSnapOutput != null) {
                    try {
                        mSnapOutput.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }

            return success;
        }

        @Override
        protected void onPostExecute(Boolean success) {
//            Context context = mActivityReference.get();
//            if (context == null) return;

            if (success) {
                Snackbar.make(DepthViewfinderFragment.this.rootView, "Success to save " + mSnapFile.getName(), Snackbar.LENGTH_SHORT).show();
            }
            else {
                Snackbar.make(DepthViewfinderFragment.this.rootView, "Fail to save " + mSnapFile.getName(), Snackbar.LENGTH_SHORT).show();
            }
            DepthViewfinderFragment.this.mSnapshotButton.setEnabled(true);
        }
    }

    public interface OnTakePictureListener {
        void onTakePicture(DepthViewfinderFragment sender, @Nullable Bitmap depthImage, @Nullable short[] depthRaw, long timestamp);
    }

    //add by ynhuang @ 20200712
    private OnBufferSnapshotListener mOnBufferSnapshotListener;

    public void setOnBufferSnapshotListener(OnBufferSnapshotListener listener) {
        mOnBufferSnapshotListener = listener;
    }

    public interface OnBufferSnapshotListener {
        void onBufferSnapshot(DepthViewfinderFragment sender, @Nullable Bitmap depthImage,
                              @Nullable byte[] byteArray, @Nullable short[] depthRaw, long timestamp,
                              Size senorArraySize, Rect preCorrectionArraySize, Rect activeArraySize,
                              float[] distortion, float[] intrinsicCalibration, float[] poseTranslation,
                              float[] poseRotation, int dimensionX, int dimensionY);
    }

    public void bufferDepthImg(){
        mProcessor.bufferSnapshot(new DepthViewfinderProcessor.BufferSnapshotListener() {
            @Override
            public void onBufferSnapshotReady(byte[] byteArray, short[] depthRaw, long timestamp, int depthDimensionX, int depthDimensionY) {
                dimensionX = depthDimensionX;
                dimensionY = depthDimensionY;
                if (null == mOnBufferSnapshotListener) {
                    new BufferSnapshotSaver(mHost, byteArray, depthRaw).execute();
                }
                else {
//                    mOnBufferSnapshotListener.onBufferSnapshot(DepthViewfinderFragment.this, byteArray, depthRaw, timestamp);
                }
            }

            @Override
            public void onBufferSnapshotFailed(String errString) {
                if (null == mOnBufferSnapshotListener) {
                    mSnapshotButton.setEnabled(true);
                    Snackbar.make(rootView, errString, Snackbar.LENGTH_SHORT).show();
                }
                else {
//                    mOnBufferSnapshotListener.onBufferSnapshot(DepthViewfinderFragment.this,
//                            null, null, null,0, sensorArraySize,
//                            preCorrectionArraySize, activeArraySize, distortion, intrinsicCalibration,
//                            poseTranslation, poseRotation, -1, -1);
                }
            }
        }, mUiHandler);
    }

    private class BufferSnapshotSaver extends AsyncTask<Void, Void, Boolean> {
        //        private WeakReference<Context> mActivityReference;
        private byte[] mByteDepthImage;
        private short[] mDepthRaw;
//        File mSnapFile;
//        FileOutputStream mSnapOutput = null;

        public BufferSnapshotSaver(Context context, byte[] byteArray, short[] depthRaw) {
//            mActivityReference = new WeakReference<>(context);
            mByteDepthImage = byteArray;
//            mSnapFile = new File(context.getExternalFilesDir(null), String.format(Locale.ENGLISH, "depth_%s.jpg", new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss.SS", Locale.ENGLISH).format(new Date())));
        }

        @Override
        protected Boolean doInBackground(Void... voids) {
            boolean success = false;
            try {
                success = true;
//                mSnapOutput = new FileOutputStream(mSnapFile);
//                success = mDepthImage.compress(Bitmap.CompressFormat.JPEG, 100, mSnapOutput);
//                mDepthImage.recycle();
            } finally {
//                if (mSnapOutput != null) {
//                    try {
//                        mSnapOutput.close();
//                    } catch (IOException e) {
//                        e.printStackTrace();
//                    }
//                }
            }

            return success;
        }

        @Override
        protected void onPostExecute(Boolean success) {
//            Context context = mActivityReference.get();
//            if (context == null) return;

            if (success) {
                Snackbar.make(DepthViewfinderFragment.this.rootView, "buffer Success to save " , Snackbar.LENGTH_SHORT).show();
            }
            else {
                Snackbar.make(DepthViewfinderFragment.this.rootView, "buffer Fail to save " , Snackbar.LENGTH_SHORT).show();
            }
//            DepthViewfinderFragment.this.mSnapshotButton.setEnabled(true);
        }
    }

    public void saveDepthImg(long ts) {
//        Log.d(TAG, "savedepthImg, (ts, keySet()): (" + ts + ", " + mProcessor.bufferTmp.keySet() + ")");
        long depthImgTs = findClosestTimestamp(ts, mProcessor.bufferTmp);
//        Log.d(TAG, "savedepthImg, (depthImgTs, depthImgTs-ts): (" + depthImgTs + ", " + Math.abs(depthImgTs-ts) + ")");
        byte[] depthImgByte = mProcessor.bufferTmp.get(depthImgTs);
        short[] depthImgRaw = mProcessor.rawTmp.get(depthImgTs);
        final Bitmap stitchBmp = Bitmap.createBitmap(mProcessor.mOutputAllocation.getType().getX(), mProcessor.mOutputAllocation.getType().getY(), Bitmap.Config.ARGB_8888);
//        mProcessor.mOutputAllocation.copyTo(stitchBmp);
        stitchBmp.copyPixelsFromBuffer(ByteBuffer.wrap(depthImgByte));

        //add by ynhuang @ 20200724
        mOnBufferSnapshotListener.onBufferSnapshot(DepthViewfinderFragment.this, stitchBmp,
                depthImgByte, depthImgRaw, depthImgTs, sensorArraySize, preCorrectionArraySize,
                activeArraySize, distortion, intrinsicCalibration, poseTranslation, poseRotation,
                dimensionX, dimensionY);

        mProcessor.bufferTmp.clear();
        mProcessor.rawTmp.clear();
    }

    private long findClosestTimestamp(long ts,  HashMap<Long, byte[]> depthMap){

        long diff = Math.abs(ts-depthMap.entrySet().iterator().next().getKey());
        long res = depthMap.entrySet().iterator().next().getKey();
        for(Long key: depthMap.keySet()){
            long tmp = Math.abs(ts-key);
//            Log.d(TAG, "findClosestTimestamp, tmp, diff, key, res: "  + tmp + ", " + diff + ", " + key + ", " + res);
            if(tmp < diff){
                diff = tmp;
                res = key;
            }
        }

        return res;
    }
    //add by ynhuang end
    //add by ynhuang @ 20211124
    private OnDepthMinListener mOnDepthMinListener;

    public void setOnDepthMinListener(OnDepthMinListener listener) {
        mOnDepthMinListener = listener;
    }
    public interface OnDepthMinListener {
        void onDepthMin(DepthViewfinderFragment sender, float depthMin);
    }
}
