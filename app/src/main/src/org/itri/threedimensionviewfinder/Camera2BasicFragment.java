/*
 * Copyright 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.itri.threedimensionviewfinder;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.Rect;
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
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.provider.MediaStore;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.core.content.ContextCompat;
import android.util.Log;
import android.util.Range;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import org.itri.streaming.vstreamer.AVStreamer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class Camera2BasicFragment extends Fragment
        implements View.OnClickListener, ActivityCompat.OnRequestPermissionsResultCallback {

    @Nullable
    private PeerConnectionClient peerConnectionClient;

    int AEMODE = CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH;
    int FLASHMODE = CameraMetadata.FLASH_MODE_OFF;

    public static final String ARG_CAMERA_CHOICE_KEY = "CameraChoice";
    public static final int ARG_CAMERA_CHOICE_DEFAULT = 1;
    public static final String ARG_HIDE_CONTROL_KEY = "HideControl";
    public static final boolean ARG_HIDE_CONTROL_DEFAULT = false;

    /**
     * Conversion from screen rotation to JPEG orientation.
     */
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    private static final int REQUEST_CAMERA_PERMISSION = 1;
    private static final String FRAGMENT_DIALOG = "dialog";

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    /**
     * Tag for the {@link Log}.
     */
    private static final String TAG = "Camera2BasicFragment";
    private static final boolean LOCAL_DEBUG = true;
    private static final boolean LOCAL_TRACE = true;
    private static final boolean DEBUG = BuildConfig.DEBUG && LOCAL_DEBUG;
    private static final boolean TRACE = BuildConfig.DEBUG && LOCAL_TRACE;

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
    private static final int MAX_PREVIEW_WIDTH = 1920;

    /**
     * Max preview height that is guaranteed by Camera2 API
     */
    private static final int MAX_PREVIEW_HEIGHT = 1080;

    // Default setting for video encoding
    private static final int DEFAULT_VIDEO_WIDTH = 1280;
    private static final int DEFAULT_VIDEO_HEIGHT = 720;
    private static final int DEFAULT_VIDEO_FRAME_RATE = 30;
    private static final int DEFAULT_VIDEO_BIT_RATE = 2000000; //rtsp

    //add by ynhuang @ 20200821 for metadata
    String header = "IDM1";
    int dataLength = 0;
    Size sensorArraySize = null;
    Rect preCorrectionArraySize = null;
    Rect activeArraySize = null;
    float[] distortion = null;
    float[] intrinsicCalibration = null;
    float[] poseTranslation = null;
    float[] poseRotation = null;
    //add by ynhuang end

    /**
     * {@link TextureView.SurfaceTextureListener} handles several lifecycle events on a
     * {@link TextureView}.
     */
    private final TextureView.SurfaceTextureListener mSurfaceTextureListener
            = new TextureView.SurfaceTextureListener() {

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
//            System.out.println("ynhuang, mSurfaceTextureListener openCamera()");
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

    private int mCameraChoice = ARG_CAMERA_CHOICE_DEFAULT;
    private boolean mHideControl = ARG_HIDE_CONTROL_DEFAULT;

    /**
     * ID of the current {@link CameraDevice}.
     */
    private String mCameraId;

    /**
     * An {@link AutoFitTextureView} for camera preview.
     */
    private AutoFitTextureView mTextureView;

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
//            System.out.println("ynhuang, mStateCallback, onOpened");
            // This method is called when the camera is opened. We start camera preview here.
            mCameraOpenCloseLock.release();
            mCameraDevice = cameraDevice;
            createCameraPreviewSession();
//            if (mCameraStateListener != null) {
//                mCameraStateListener.onCameraOpened(Camera2BasicFragment.this);
//            }
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
//            System.out.println("ynhuang, mStateCallback, onDisconnected");
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;

//            if (mCameraStateListener != null) {
//                mCameraStateListener.onCameraDisconnected(Camera2BasicFragment.this);
//            }
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int error) {
//            System.out.println("ynhuang, mStateCallback, onError");
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
            Activity activity = getActivity();
            if (null != activity) {
                activity.finish();
            }
//            if (mCameraStateListener != null) {
//                mCameraStateListener.onCameraError(Camera2BasicFragment.this, error);
//            }
        }

//        @Override
//        public void onClosed(@NonNull CameraDevice camera) {
//            super.onClosed(camera);
//            if (mCameraStateListener != null) {
//                mCameraStateListener.onCameraClosed(Camera2BasicFragment.this);
//            }
//        }
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
        public void onImageAvailable(final ImageReader reader) {
            if (mOnTakePictureListener != null) {
                mBackgroundHandler.post(new Runnable() {
                    @Override
                    public void run() {
//                        System.out.println("ynhuang, onImageAvailable onTakePicture");
                        mOnTakePictureListener.onTakePicture(Camera2BasicFragment.this,
                                reader.acquireNextImage(), header, dataLength, sensorArraySize,
                                preCorrectionArraySize, activeArraySize, distortion, intrinsicCalibration,
                                poseTranslation, poseRotation);
                    }
                });
            }
            else {
                mBackgroundHandler.post(new ImageSaver(reader.acquireNextImage(), mFile));
            }
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
    private int mSensorOrientation;

    /**
     * A {@link CameraCaptureSession.CaptureCallback} that handles events related to JPEG capture.
     */
    private CameraCaptureSession.CaptureCallback mCaptureCallback
            = new CameraCaptureSession.CaptureCallback() {

        private void process(CaptureResult result) {
            switch (mState) {
                case STATE_PREVIEW: {
                    // We have nothing to do when the camera preview is working normally.
                    break;
                }
                case STATE_WAITING_LOCK: {
                    Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);
                    if (afState == null) {
                        captureStillPicture();
                    } else if (CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED == afState ||
                            CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED == afState) {
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

    private CameraStateListener mCameraStateListener;
    private OnTakePictureListener mOnTakePictureListener;

    public void setOnTakePictureListener(OnTakePictureListener listener) {
        mOnTakePictureListener = listener;
    }

    // +++ Related to video coding +++
    private MediaCodec mVideoEncoder;
    private HandlerThread mVideoEncoderThread;
    private final Object mVideoEncoderSync = new Object();
    private boolean mVideoEncoderIsStarted;
    private int mVideoWidthForEncoding = DEFAULT_VIDEO_WIDTH;
    private int mVideoHeightForEncoding = DEFAULT_VIDEO_HEIGHT;
    private int mVideoFpsForEncoding = DEFAULT_VIDEO_FRAME_RATE;
    private int mTargetBitRate = DEFAULT_VIDEO_BIT_RATE;
    private MediaFormat mVideoEncoderMediaFormat = null;

    private final MediaCodec.Callback mVideoEncoderCallback = new MediaCodec.Callback() {
        @Override
        public void onInputBufferAvailable(@NonNull MediaCodec mediaCodec, int index) {
            if (TRACE) Log.v(TAG, "mVideoEncoderCallback.onInputBufferAvailable() called with: mediaCodec = [" + mediaCodec + "], index = [" + index + "]");
        }

        @Override
        public void onOutputBufferAvailable(@NonNull MediaCodec mediaCodec, int index, @NonNull MediaCodec.BufferInfo bufferInfo) {
            final boolean codecConf = ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0);
//            final boolean keyFrame = ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0);
//            final boolean eos = ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0);
//            if (TRACE) Log.v(TAG, "mVideoEncoderCallback.onOutputBufferAvailable() called with: mediaCodec = [" + mediaCodec + "], index = [" + index + "], bufferInfo = [" + bufferInfo + "], codecConf = [" + codecConf + "], keyFrame = [" +  keyFrame + "], eos = [" + eos + "]");

            try {
                synchronized (mVideoEncoderSync) {
                    if (!mVideoEncoderIsStarted) {
                        if (TRACE) Log.d(TAG, "mVideoEncoderCallback.onOutputBufferAvailable: mVideoEncoderIsStarted = [" + mVideoEncoderIsStarted + "]");
                        return;
                    }

                    ByteBuffer outputBuffer = mediaCodec.getOutputBuffer(index);
                    try {
                        if (outputBuffer != null && !codecConf) {
                            int prevPos = outputBuffer.position();

                            if (mVideoPrepared && mAVStreamer != null && (bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                                try {
                                    outputBuffer.position(bufferInfo.offset);
                                    mAVStreamer.sendH264(outputBuffer, bufferInfo);
                                } catch (RuntimeException e) {
                                    if (TRACE) Log.d(TAG, "mOutputBufferCallback.onVideoBufferAvailable: " + e.getMessage(), e);
                                }
                            }

                            if (mMediaMuxer != null) {
                                synchronized (mMediaMuxerSync) {
                                    if (mMediaMuxerIsStarted) {
                                        bufferInfo.presentationTimeUs = System.nanoTime() / 1000L;
//                                        if (DEBUG) Log.v(TAG, "mVideoEncoderCallback.onOutputBufferAvailable: MediaMuxer writeSampleData, timestamp = [" + bufferInfo.presentationTimeUs + "]");
                                        outputBuffer.position(prevPos);
                                        mMediaMuxer.writeSampleData(mVideoTrackIndex, outputBuffer, bufferInfo);
                                    }
                                }
                            }
                        }
                    } finally {
                        mediaCodec.releaseOutputBuffer(index, false);
                    }
                }
            } catch (RuntimeException e) {
                if (TRACE) Log.d(TAG, "mVideoEncoderCallback.onOutputBufferAvailable: " + e.getMessage(), e);
            }
        }

        @Override
        public void onError(@NonNull MediaCodec mediaCodec, @NonNull MediaCodec.CodecException e) {
            if (TRACE) Log.v(TAG, "mVideoEncoderCallback.onError() called with: mediaCodec = [" + mediaCodec + "], e = [" + e + "]");
//            setErrorState();
        }

        @Override
        public void onOutputFormatChanged(@NonNull MediaCodec mediaCodec, @NonNull MediaFormat mediaFormat) {
            if (TRACE) Log.v(TAG, "mVideoEncoderCallback.onOutputFormatChanged() called with: mediaCodec = [" + mediaCodec + "], mediaFormat = [" + mediaFormat + "]");
            if (!mVideoEncoderIsStarted) {
                if (TRACE) Log.d(TAG, "mVideoEncoderCallback.onOutputFormatChanged: mVideoEncoderIsStarted = [" + mVideoEncoderIsStarted + "]");
                return;
            }

            // Store the current media format for later use by MediaMuxer
            mVideoEncoderMediaFormat = mediaFormat;

            try {
                if (mAVStreamer != null) {
                    try {
                        if (mediaFormat.containsKey("csd-0") && mediaFormat.containsKey("csd-1")) {
                            if (DEBUG) Log.v(TAG, "mVideoEncoderCallback.onOutputFormatChanged: csd-0 = [" + mediaFormat.getByteBuffer("csd-0") + "], csd-1 = [" + mediaFormat.getByteBuffer("csd-1") + "]");
                            mAVStreamer.setH264Info(mediaFormat.getByteBuffer("csd-0"), mediaFormat.getByteBuffer("csd-1"));
                        }
                        mVideoPrepared = true;
                        mAVStreamer.prepare();
                    } catch (RuntimeException e) {
                        if (TRACE) Log.d(TAG, "mVideoEncoderCallback.onOutputFormatChanged: " + e.getMessage(), e);
                    }
                }

                if (mMediaMuxer != null) {
                    synchronized (mMediaMuxerSync) {
                        if (!mMediaMuxerIsStarted) {
                            mVideoTrackIndex = mMediaMuxer.addTrack(mediaFormat);
                            if (DEBUG) Log.v(TAG, "mVideoEncoderCallback.onOutputFormatChanged: MediaMuxer add video track [" + mVideoTrackIndex + "], format = [" + mediaFormat + "]");
                            if (mVideoTrackIndex >= 0) {
                                mMediaMuxerIsStarted = true;
                                mMediaMuxer.start();
                                if (DEBUG) Log.v(TAG, "mVideoEncoderCallback.onOutputFormatChanged: MediaMuxer is started");
                            }
                        }
                    }
                }
            } catch (RuntimeException e) {
                if (TRACE) Log.d(TAG, "mVideoEncoderCallback.onOutputFormatChanged: " + e.getMessage(), e);
            }
        }
    };
    // --- Related to video coding ---

    // +++ Related to media muxer +++
    private MediaMuxer mMediaMuxer = null;
    private final Object mMediaMuxerSync = new Object();
    private boolean mMediaMuxerIsStarted;
    private int mVideoTrackIndex = -1; // must set to -1 as un-initialized
    private ParcelFileDescriptor mPfdFile;
    private ContentValues mValuesvideos;
    private Uri mUriSavedVideo;
    // --- Related to media muxer ---

    // AVStreamer related
    private AVStreamer mAVStreamer;
    private boolean mVideoPrepared;

    /**
     * Shows a {@link Toast} on the UI thread.
     *
     * @param text The message to show
     */
    private void showToast(final String text) {
        final Activity activity = getActivity();
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
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else if (notBigEnough.size() > 0) {
            return Collections.max(notBigEnough, new CompareSizesByArea());
        } else {
            Log.e(TAG, "Couldn't find any suitable preview size");
            return choices[0];
        }
    }

    public static Camera2BasicFragment newInstance(int cameraChoice, boolean hideControl) {
//        return new Camera2BasicFragment();
//        System.out.println("ynhuang, newInstance(cameraChoice, hideControl): " + cameraChoice + ", " + hideControl);
        Camera2BasicFragment fragment = new Camera2BasicFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_CAMERA_CHOICE_KEY, cameraChoice);
        args.putBoolean(ARG_HIDE_CONTROL_KEY, hideControl);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        Bundle args = getArguments();
        if (args != null) {
            mCameraChoice =  args.getInt(ARG_CAMERA_CHOICE_KEY, ARG_CAMERA_CHOICE_DEFAULT);
            mHideControl = args.getBoolean(ARG_HIDE_CONTROL_KEY, ARG_HIDE_CONTROL_DEFAULT);
        }

        if (context instanceof CameraStateListener) {
            mCameraStateListener = (CameraStateListener)context;
        }
    }

//    @Override
//    public void onDetach() {
//        super.onDetach();
//        mCameraStateListener = null;
//    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_camera2_basic, container, false);

        if (mHideControl) {
            rootView.findViewById(R.id.control).setVisibility(View.GONE);
        }

        return rootView;
    }

    @Override
    public void onViewCreated(final View view, Bundle savedInstanceState) {
        view.findViewById(R.id.picture).setOnClickListener(this);
        view.findViewById(R.id.info).setOnClickListener(this);
        mTextureView = (AutoFitTextureView) view.findViewById(R.id.texture);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mFile = new File(getActivity().getExternalFilesDir(null), "pic.jpg");
    }

    @Override
    public void onResume() {
        super.onResume();
        startBackgroundThread();

        // When the screen is turned off and turned back on, the SurfaceTexture is already
        // available, and "onSurfaceTextureAvailable" will not be called. In that case, we can open
        // a camera and start preview from here (otherwise, we wait until the surface is ready in
        // the SurfaceTextureListener).
        if (mTextureView.isAvailable()) {
//            System.out.println("ynhuang, onResume if openCamera()");
            openCamera(mTextureView.getWidth(), mTextureView.getHeight());
        } else {
//            System.out.println("ynhuang, onResume else openCamera()");
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }
    }

    @Override
    public void onPause() {
        closeCamera();
        stopBackgroundThread();
        super.onPause();
    }

    private void requestCameraPermission() {
        if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            new ConfirmationDialog().show(getChildFragmentManager(), FRAGMENT_DIALOG);
        } else {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length != 1 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                ErrorDialog.newInstance(getString(R.string.request_permission))
                        .show(getChildFragmentManager(), FRAGMENT_DIALOG);
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
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
        Activity activity = getActivity();
        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        try {
            int currentChoice = 1;
            for (String cameraId : manager.getCameraIdList()) {
                CameraCharacteristics characteristics
                        = manager.getCameraCharacteristics(cameraId);

                // We don't use a front facing camera in this sample.
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    continue;
                }

                //add by ynhuang @ 20200821
                sensorArraySize =  characteristics.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE);
                preCorrectionArraySize = characteristics.get(CameraCharacteristics.SENSOR_INFO_PRE_CORRECTION_ACTIVE_ARRAY_SIZE);
                activeArraySize = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
                distortion = characteristics.get(CameraCharacteristics.LENS_DISTORTION);
                intrinsicCalibration = characteristics.get(CameraCharacteristics.LENS_INTRINSIC_CALIBRATION);
                poseTranslation = characteristics.get(CameraCharacteristics.LENS_POSE_TRANSLATION);
                poseRotation = characteristics.get(CameraCharacteristics.LENS_POSE_ROTATION);
                //add by ynhuang end

                StreamConfigurationMap map = characteristics.get(
                        CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (map == null) {
                    continue;
                }

                if (currentChoice++ < mCameraChoice) {
                    continue;
                }

                // For video encoding
                prepareVideoEncoder();

                // Init AVStreamer
                mVideoPrepared = false;
                mAVStreamer = null;
                try {
                    BufferedReader br = new BufferedReader(new InputStreamReader(getContext().getAssets().open("VStreamer.conf")));
                    StringBuilder confBuf = new StringBuilder();
                    String line;

                    while ((line = br.readLine()) != null) {
                        // The end of the line should always be "\n"
                        confBuf.append(line).append('\n');
                    }
                    br.close();

                    mAVStreamer = AVStreamer.init(confBuf.toString());
                    mAVStreamer.reset();
                    mAVStreamer.setVideoInfo(mVideoWidthForEncoding, mVideoHeightForEncoding, mVideoFpsForEncoding);
                } catch (IOException | RuntimeException e) {
                    if (DEBUG) Log.w(TAG, "setUpCameraOutputs: " + e.getMessage(), e);
                }

                // For still image captures, we use the largest available size.
                Size largest = Collections.max(
                        Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)),
                        new CompareSizesByArea());
                mImageReader = ImageReader.newInstance(largest.getWidth(), largest.getHeight(),
                        ImageFormat.JPEG, /*maxImages*/2);
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

                // Danger, W.R.! Attempting to use too large a preview size could exceed the camera
                // bus' bandwidth limitation, resulting in gorgeous previews but the storage of
                // garbage capture data.
                mPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
                        rotatedPreviewWidth, rotatedPreviewHeight, maxPreviewWidth,
                        maxPreviewHeight, largest);

                // We fit the aspect ratio of TextureView to the size of preview we picked.
                int orientation = getResources().getConfiguration().orientation;
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

                mCameraId = cameraId; //強制設定mCameraId="1"會是前鏡頭並且可以同時webRTC(後鏡頭)+拍照(前鏡頭）
                return;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (NullPointerException e) {
            // Currently an NPE is thrown when the Camera2API is used but not supported on the
            // device this code runs.
            ErrorDialog.newInstance(getString(R.string.camera_error))
                    .show(getChildFragmentManager(), FRAGMENT_DIALOG);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Opens the camera specified by {@link Camera2BasicFragment#mCameraId}.
     */
    private void openCamera(int width, int height) {
        if (ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            requestCameraPermission();
            return;
        }
        setUpCameraOutputs(width, height);
        configureTransform(width, height);
        Activity activity = getActivity();
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

    public void reopenCamera() {
        if (mCameraDevice == null) {
            Log.d(TAG, "Try to reopen camera");
            if (mTextureView.isAvailable()) {
                openCamera(mTextureView.getWidth(), mTextureView.getHeight());
            } else {
                mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
            }
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
            if (mVideoEncoderThread != null) {
                mVideoEncoderThread.quitSafely();
                try {
                    mVideoEncoderThread.join();
                } catch (InterruptedException e) {
                    if (TRACE) Log.d(TAG, "closeCamera: " + e.getMessage(), e);
                }
                mVideoEncoderThread = null;
                if (TRACE) Log.v(TAG, "closeCamera: VideoEncoderThread is quit");
            }

            if (mVideoEncoder != null) {
                mVideoEncoder.release();
                mVideoEncoder = null;
                if (TRACE) Log.v(TAG, "closeCamera: VideoEncoder is released");
            }

            try {
                if (mAVStreamer != null) {
                    mAVStreamer.reset();
                }
            } catch (RuntimeException e) {
                if (TRACE) Log.d(TAG, "closeCamera: " + e.getMessage(), e);
            } finally {
                mVideoPrepared = false;
                mAVStreamer = null;
            }

            // stop recording if the camera is closed
            stopRecordingVideo();
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
        } finally {
            mCameraOpenCloseLock.release();
        }
    }

    /**
     * Starts a background thread and its {@link Handler}.
     */
    private void startBackgroundThread() {
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
//        try {
//            SurfaceTexture texture = mTextureView.getSurfaceTexture();
//            assert texture != null;
//
//            // We configure the size of default buffer to be the size of camera preview we want.
//            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
//
//            // This is the output Surface we need to start preview.
//            Surface surface = new Surface(texture);
//
//            // We set up a CaptureRequest.Builder with the output Surface.
//            mPreviewRequestBuilder
//                    = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
//            mPreviewRequestBuilder.addTarget(surface);
//
//            // Here, we create a CameraCaptureSession for camera preview.
//            mCameraDevice.createCaptureSession(Arrays.asList(surface, mImageReader.getSurface()),
//                    new CameraCaptureSession.StateCallback() {
//
//                        @Override
//                        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
//                            // The camera is already closed
//                            if (null == mCameraDevice) {
//                                return;
//                            }
//
//                            // When the session is ready, we start displaying the preview.
//                            mCaptureSession = cameraCaptureSession;
//                            try {
//                                // Auto focus should be continuous for camera preview.
//                                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
//                                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
//                                // Flash is automatically enabled when necessary.
//                                setAutoFlash(mPreviewRequestBuilder);
//
//                                //add by ynhuang @ 20200730 Flash turn on/off
//                                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, AEMODE);
//                                mPreviewRequestBuilder.set(CaptureRequest.FLASH_MODE, FLASHMODE);
//
//                                // Finally, we start displaying the camera preview.
//                                mPreviewRequest = mPreviewRequestBuilder.build();
//                                System.out.println("ynhuang, 1.Camera2BasicFragment");
//                                System.out.println("ynhuang, mPreviewRequest: " + mPreviewRequest);
//                                System.out.println("ynhuang, mCaptureCallback: " + mCaptureCallback);
//                                System.out.println("ynhuang, mBackgroundHandler: " + mBackgroundHandler);
//                                mCaptureSession.setRepeatingRequest(mPreviewRequest,
//                                        mCaptureCallback, mBackgroundHandler);
//
//                                System.out.println("ynhuang, 2.Camera2BasicFragment");
//                            } catch (CameraAccessException e) {
//                                System.out.println("ynhuang, CameraAccessException: " + e.toString());
//                                e.printStackTrace();
//                            }
//                        }
//
//                        @Override
//                        public void onConfigureFailed(
//                                @NonNull CameraCaptureSession cameraCaptureSession) {
//                            showToast("Failed");
//                        }
//                    }, null
//            );
//        } catch (CameraAccessException e) {
//            e.printStackTrace();
//        }
        try {
            // Reduce the use of camera bandwidth by disabling previews to avoid camera errors when taking photos
//            SurfaceTexture texture = mTextureView.getSurfaceTexture();
//            assert texture != null;
//
//            // We configure the size of default buffer to be the size of camera preview we want.
//            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
//
//            // This is the output Surface we need to start preview.
//            Surface surface = new Surface(texture);
            Surface encoderSurface = mVideoEncoder.createInputSurface();

            // We set up a CaptureRequest.Builder with the output Surface.
            mPreviewRequestBuilder
                    = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
//            mPreviewRequestBuilder.addTarget(surface);
            mPreviewRequestBuilder.addTarget(encoderSurface);

            // Here, we create a CameraCaptureSession for camera preview.
//            mCameraDevice.createCaptureSession(Arrays.asList(surface, mImageReader.getSurface(), encoderSurface),
            mCameraDevice.createCaptureSession(Arrays.asList(mImageReader.getSurface(), encoderSurface),
                    new CameraCaptureSession.StateCallback() {

                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                            // The camera is already closed
                            if (null == mCameraDevice) {
                                return;
                            }

                            // start video encoder
                            synchronized (mVideoEncoderSync) {
                                if (mVideoEncoder != null) {
                                    mVideoEncoder.start();
                                    mVideoEncoderIsStarted = true;
                                }
                            }

                            // When the session is ready, we start displaying the preview.
                            mCaptureSession = cameraCaptureSession;
                            try {
                                // Auto focus should be continuous for camera preview.
                                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                                // Flash is automatically enabled when necessary.
                                setAutoFlash(mPreviewRequestBuilder);

                                //add by ynhuang @ 20200730 Flash turn on/off
                                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, AEMODE);
                                mPreviewRequestBuilder.set(CaptureRequest.FLASH_MODE, FLASHMODE);

                                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, new Range<>(mVideoFpsForEncoding, mVideoFpsForEncoding));

                                // Finally, we start displaying the camera preview.
                                mPreviewRequest = mPreviewRequestBuilder.build();
                                mCaptureSession.setRepeatingRequest(mPreviewRequest,
                                        mCaptureCallback, mBackgroundHandler);

                                if (mCameraStateListener != null) {
                                    mCameraStateListener.onCameraCaptureStarted(Camera2BasicFragment.this);
                                }
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(
                                @NonNull CameraCaptureSession cameraCaptureSession) {
                            showToast("Failed");
                        }

                        @Override
                        public void onClosed(@NonNull CameraCaptureSession session) {
                            super.onClosed(session);
                            // stop video encoder
                            synchronized (mVideoEncoderSync) {
                                mVideoEncoderIsStarted = false;
                                if (mVideoEncoder != null) {
                                    mVideoEncoder.stop();
                                    mVideoEncoderMediaFormat = null;
                                }
                            }

                            if (mCameraStateListener != null) {
                                mCameraStateListener.onCameraCaptureStopped(Camera2BasicFragment.this);
                            }

                            // force to close camera
                            new Handler(Looper.getMainLooper()).post(new Runnable() {
                                @Override
                                public void run() {
                                    closeCamera();
                                }
                            });
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
        Activity activity = getActivity();
        if (null == mTextureView || null == mPreviewSize || null == activity) {
            return;
        }
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect;
        if (mSensorOrientation == 0 || mSensorOrientation == 180) {
            bufferRect = new RectF(0, 0, mPreviewSize.getWidth(), mPreviewSize.getHeight());
        } else {  // 90, 270
            bufferRect = new RectF(0, 0, mPreviewSize.getHeight(), mPreviewSize.getWidth());
        }
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            // Resize the distorted rectangle in viewRect back to the dimensions of the source (bufferRect).
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            // Scale the rectangle back so that it just covers the viewfinder.
            float viewLongEdge = viewWidth > viewHeight ? viewWidth : viewHeight;
            float viewShortEdge = viewWidth <= viewHeight ? viewWidth : viewHeight;
            float scale = Math.max(
                    (float) viewShortEdge / mPreviewSize.getHeight(),
                    (float) viewLongEdge / mPreviewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            // Rotate the rectangle to the correct orientation.
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
//        lockFocus();
        mState = STATE_PICTURE_TAKEN;
        captureStillPicture();
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
            final Activity activity = getActivity();
            if (null == activity || null == mCameraDevice /*|| null == mCaptureSession*/) {
//                mState = STATE_PREVIEW;
//                if (mOnTakePictureListener != null) {
//                    mOnTakePictureListener.onTakePictureError(this, OnTakePictureListener.Error.CAMERA_IS_NOT_OPENED);
//                    System.out.println("ynhuang, CAMERA_IS_NOT_OPENED");
//                }
                return;
            }
            // This is the CaptureRequest.Builder that we use to take a picture.
            final CaptureRequest.Builder captureBuilder =
                    mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(mImageReader.getSurface());

            // Use the same AE and AF modes as the preview.
            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            setAutoFlash(captureBuilder);

            // Orientation
            int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, getOrientation(rotation));
//            Log.d(TAG, "rotation, getOrientation(rotation): " + rotation + ", " + getOrientation(rotation));
            CameraCaptureSession.CaptureCallback captureCallback
                    = new CameraCaptureSession.CaptureCallback() {

                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                               @NonNull CaptureRequest request,
                                               @NonNull TotalCaptureResult result) {
                    if (mOnTakePictureListener == null) {
                        showToast("Saved: " + mFile);
                    }
                    Log.d(TAG, mFile.toString());
                    unlockFocus();
                }

//                @Override
//                public void onCaptureFailed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureFailure failure) {
//                    super.onCaptureFailed(session, request, failure);
//                    if (mOnTakePictureListener != null) {
//                        mOnTakePictureListener.onTakePictureError(Camera2BasicFragment.this, OnTakePictureListener.Error.CAPTURE_IS_FAILED);
//                        System.out.println("ynhuang, CAPTURE_IS_FAILED");
//                    }
//                }
            };

            mCaptureSession.stopRepeating();
            mCaptureSession.abortCaptures();
            mCaptureSession.capture(captureBuilder.build(), captureCallback, null);
        } catch (CameraAccessException /*| IllegalStateException | NullPointerException*/ e) {
            e.printStackTrace();
//            if (mOnTakePictureListener != null) {
//                mOnTakePictureListener.onTakePictureError(Camera2BasicFragment.this, OnTakePictureListener.Error.CAMERA_ERROR);
//                System.out.println("ynhuang, CAMERA_ERROR");
//            }
        }
    }

    /**
     * Retrieves the JPEG orientation from the specified screen rotation.
     *
     * @param rotation The screen rotation.
     * @return The JPEG orientation (one of 0, 90, 270, and 360)
     */
    private int getOrientation(int rotation) {
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
            setAutoFlash(mPreviewRequestBuilder);
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

    //add by ynhuang @ 20200928
    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (hidden) {   // 不在最前端顯示，相當於調用了onPause();
            return;
        }else{  // 在最前端顯示，相當於調用了onResume();
            startBackgroundThread();

            // When the screen is turned off and turned back on, the SurfaceTexture is already
            // available, and "onSurfaceTextureAvailable" will not be called. In that case, we can open
            // a camera and start preview from here (otherwise, we wait until the surface is ready in
            // the SurfaceTextureListener).
            if (mTextureView.isAvailable()) {
                System.out.println("ynhuang, onHiddenChanged if openCamera()");
                openCamera(mTextureView.getWidth(), mTextureView.getHeight());
            } else {
                System.out.println("ynhuang, onHiddenChanged else openCamera()");
                mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
            }
        }
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.picture: {
                takePicture();
                break;
            }
            case R.id.info: {
                Activity activity = getActivity();
                if (null != activity) {
                    new AlertDialog.Builder(activity)
                            .setMessage(R.string.intro_message)
                            .setPositiveButton(android.R.string.ok, null)
                            .show();
                }
                break;
            }
        }
    }

    private void setAutoFlash(CaptureRequest.Builder requestBuilder) {
        if (mFlashSupported) {
            requestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
        }
    }

    //add by ynhuang @ 20200730 Flash turn on
    public void setFlashOn() {
        if((AEMODE == CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH) && (FLASHMODE == CameraMetadata.FLASH_MODE_OFF)) {
            AEMODE = CaptureRequest.CONTROL_AE_MODE_ON;
            FLASHMODE = CameraMetadata.FLASH_MODE_TORCH;
        } else if((AEMODE == CaptureRequest.CONTROL_AE_MODE_ON) && (FLASHMODE == CameraMetadata.FLASH_MODE_TORCH)){
            AEMODE = CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH;
            FLASHMODE = CameraMetadata.FLASH_MODE_OFF;
        }

        if (mTextureView.isAvailable()) {
            openCamera(mTextureView.getWidth(), mTextureView.getHeight());
        } else {
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }
    }

    private void prepareVideoEncoder() throws IOException, IllegalArgumentException {
        if (TRACE) Log.v(TAG, "prepareVideoEncoder() called");
        MediaFormat format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, mVideoWidthForEncoding, mVideoHeightForEncoding);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_BIT_RATE, mTargetBitRate);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2);

        // On LOLLIPOP, format must not contain a frame rate.
//        format.setInteger(MediaFormat.KEY_FRAME_RATE, mConfig.getVideoFrameRate());
        String codecName = new MediaCodecList(MediaCodecList.REGULAR_CODECS).findEncoderForFormat(format);
        if (codecName != null) {
            MediaCodec videoEncoder = MediaCodec.createByCodecName(codecName);
            HandlerThread videoEncoderThread = new HandlerThread("VideoEncoderThread", Process.THREAD_PRIORITY_DISPLAY);
            videoEncoderThread.start();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                videoEncoder.setCallback(mVideoEncoderCallback, new Handler(videoEncoderThread.getLooper()));
            } else {
                videoEncoder.setCallback(mVideoEncoderCallback);
            }

            format.setInteger(MediaFormat.KEY_FRAME_RATE, mVideoFpsForEncoding);
            videoEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);

            mVideoEncoder = videoEncoder;
            mVideoEncoderThread = videoEncoderThread;
        } else {
            throw new IllegalArgumentException("Fail to find suitable encoder for configured video format");
        }
    }

    public void startRecordingVideo() {
        // Make sure the previous recording has stopped
        stopRecordingVideo();

        try {
            long currentTime = System.currentTimeMillis();
            String videoFileName = currentTime + ".mp4";
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                mValuesvideos = new ContentValues();
                mValuesvideos.put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_DCIM + File.separator + "Camera");
                mValuesvideos.put(MediaStore.Video.Media.TITLE, videoFileName);
                mValuesvideos.put(MediaStore.Video.Media.DISPLAY_NAME, videoFileName);
                mValuesvideos.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
                mValuesvideos.put(MediaStore.Video.Media.DATE_ADDED, currentTime / 1000);
                mValuesvideos.put(MediaStore.Video.Media.DATE_TAKEN, currentTime);
                mValuesvideos.put(MediaStore.Video.Media.IS_PENDING, 1);

                ContentResolver resolver = getContext().getContentResolver();
                Uri collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
                mUriSavedVideo = resolver.insert(collection, mValuesvideos);
                mPfdFile = resolver.openFileDescriptor(mUriSavedVideo, "w");

                mMediaMuxer = new MediaMuxer(mPfdFile.getFileDescriptor(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            } else {
//                File outputFilePath = new File(getContext().getExternalFilesDir(Environment.DIRECTORY_DCIM), "Camera" + File.separator + videoFileName);
                File outputFilePath = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Camera" + File.separator + videoFileName);
                mMediaMuxer = new MediaMuxer(outputFilePath.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            }
            if (mVideoEncoderMediaFormat != null) {
                synchronized (mMediaMuxerSync) {
                    mVideoTrackIndex = mMediaMuxer.addTrack(mVideoEncoderMediaFormat);
                    if (DEBUG)
                        Log.v(TAG, "startRecordingVideo: MediaMuxer add video track [" + mVideoTrackIndex + "], format = [" + mVideoEncoderMediaFormat + "]");
                    if (mVideoTrackIndex >= 0) {
                        mMediaMuxerIsStarted = true;
                        mMediaMuxer.start();
                        if (DEBUG) Log.v(TAG, "startRecordingVideo: MediaMuxer is started");
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void stopRecordingVideo() {
        if (mMediaMuxer != null) {
            synchronized (mMediaMuxerSync) {
                mMediaMuxerIsStarted = false;
                mMediaMuxer.stop();
            }
            mMediaMuxer.release();
            if (TRACE) Log.v(TAG, "stopRecordingVideo: MediaMuxer is released");
        }
        mMediaMuxer = null;
        mMediaMuxerIsStarted = false;
        mVideoTrackIndex = -1;// must set to -1 as un-initialized
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (mPfdFile != null) {
                try {
                    mPfdFile.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (mValuesvideos != null) {
                mValuesvideos.clear();
                mValuesvideos.put(MediaStore.Video.Media.IS_PENDING, 0);

                if (mUriSavedVideo != null) {
                    getContext().getContentResolver().update(mUriSavedVideo, mValuesvideos, null, null);
                }
            }
        }
    }

    public boolean isRecordingVideo() {
        return mMediaMuxer != null;
    }

    /**
     * Saves a JPEG {@link Image} into the specified {@link File}.
     */
    private static class ImageSaver implements Runnable {

        /**
         * The JPEG image
         */
        private final Image mImage;
        /**
         * The file we save the image into.
         */
        private final File mFile;

        ImageSaver(Image image, File file) {
            mImage = image;
            mFile = file;
        }

        @Override
        public void run() {
            ByteBuffer buffer = mImage.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            FileOutputStream output = null;
            try {
                output = new FileOutputStream(mFile);
                output.write(bytes);
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                mImage.close();
                if (null != output) {
                    try {
                        output.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

    }

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

        public static ErrorDialog newInstance(String message) {
            ErrorDialog dialog = new ErrorDialog();
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

    public interface OnTakePictureListener {
        enum Error {
            CAMERA_IS_NOT_OPENED,
            CAMERA_ERROR,
            CAPTURE_IS_FAILED
        }
        void onTakePicture(Camera2BasicFragment sender, Image image, String header, int dataLength,
                           Size senorArraySize, Rect preCorrectionArraySize, Rect activeArraySize,
                           float[] distortion, float[] intrinsicCalibration, float[] poseTranslation,
                           float[] poseRotation);
        void onTakePictureError(Camera2BasicFragment sender, Error error);
    }

    public interface CameraStateListener {
        void onCameraOpened(@NonNull Camera2BasicFragment sender);
        void onCameraDisconnected(@NonNull Camera2BasicFragment sender);
        void onCameraError(@NonNull Camera2BasicFragment sender, int error);
        void onCameraClosed(@NonNull Camera2BasicFragment sender);
        void onCameraCaptureStarted(@NonNull Camera2BasicFragment sender);
        void onCameraCaptureStopped(@NonNull Camera2BasicFragment sender);
    }
}
