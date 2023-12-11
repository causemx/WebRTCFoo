/*
 *  Copyright 2015 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

package org.itri.threedimensionviewfinder;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.FragmentTransaction;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.Image;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.provider.MediaStore;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.core.widget.NestedScrollView;

import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Size;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.RuntimeException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.itri.threedimensionviewfinder.AppRTCAudioManager.AudioDevice;
import org.itri.threedimensionviewfinder.AppRTCAudioManager.AudioManagerEvents;
import org.itri.threedimensionviewfinder.AppRTCClient.RoomConnectionParameters;
import org.itri.threedimensionviewfinder.AppRTCClient.SignalingParameters;
import org.itri.threedimensionviewfinder.PeerConnectionClient.DataChannelParameters;
import org.itri.threedimensionviewfinder.PeerConnectionClient.PeerConnectionParameters;
import org.itri.threedimensionviewfinder.util.ExifUtils;
import org.itri.threedimensionviewfinder.util.AutoUpload;
import org.json.JSONException;
import org.webrtc.Camera1Enumerator;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.DataChannel;
import org.webrtc.EglBase;
import org.webrtc.FileVideoCapturer;
import org.webrtc.IceCandidate;
import org.webrtc.Logging;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RendererCommon.ScalingType;
import org.webrtc.ScreenCapturerAndroid;
import org.webrtc.SessionDescription;
import org.webrtc.StatsReport;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoFileRenderer;
import org.webrtc.VideoFrame;
import org.webrtc.VideoSink;

import androidx.appcompat.app.AppCompatActivity;

import com.example.android.camera2video.Camera2VideoFragment;
import com.example.imufinder.ScrollingFragment;
import org.itri.usbterminal.TerminalFragment;

import org.itri.example.depthviewfinder.DepthViewfinderFragment;

/**
 * Activity for peer connection call setup, call waiting
 * and call view.
 */
public class CallActivity extends AppCompatActivity implements Camera2BasicFragment.OnTakePictureListener,
        DepthViewfinderFragment.OnTakePictureListener, DepthViewfinderFragment.OnBufferSnapshotListener,
        AppRTCClient.SignalingEvents, PeerConnectionClient.PeerConnectionEvents, CallFragment.OnCallEvents,
        Camera2BasicFragment.CameraStateListener, ScrollingFragment.OnImuListener, DepthViewfinderFragment.OnDepthMinListener,
        TerminalFragment.OnCallEvents {
  private static final String TAG = "CallRTCClient";

  public static final String EXTRA_ROOMID = "org.appspot.apprtc.ROOMID";
  public static final String EXTRA_URLPARAMETERS = "org.appspot.apprtc.URLPARAMETERS";
  public static final String EXTRA_LOOPBACK = "org.appspot.apprtc.LOOPBACK";
  public static final String EXTRA_VIDEO_CALL = "org.appspot.apprtc.VIDEO_CALL";
  public static final String EXTRA_SCREENCAPTURE = "org.appspot.apprtc.SCREENCAPTURE";
  public static final String EXTRA_CAMERA2 = "org.appspot.apprtc.CAMERA2";
  public static final String EXTRA_VIDEO_WIDTH = "org.appspot.apprtc.VIDEO_WIDTH";
  public static final String EXTRA_VIDEO_HEIGHT = "org.appspot.apprtc.VIDEO_HEIGHT";
  public static final String EXTRA_VIDEO_FPS = "org.appspot.apprtc.VIDEO_FPS";
  public static final String EXTRA_VIDEO_CAPTUREQUALITYSLIDER_ENABLED =
          "org.appspot.apprtc.VIDEO_CAPTUREQUALITYSLIDER";
  public static final String EXTRA_VIDEO_BITRATE = "org.appspot.apprtc.VIDEO_BITRATE";
  public static final String EXTRA_VIDEOCODEC = "org.appspot.apprtc.VIDEOCODEC";
  public static final String EXTRA_HWCODEC_ENABLED = "org.appspot.apprtc.HWCODEC";
  public static final String EXTRA_CAPTURETOTEXTURE_ENABLED = "org.appspot.apprtc.CAPTURETOTEXTURE";
  public static final String EXTRA_FLEXFEC_ENABLED = "org.appspot.apprtc.FLEXFEC";
  public static final String EXTRA_AUDIO_BITRATE = "org.appspot.apprtc.AUDIO_BITRATE";
  public static final String EXTRA_AUDIOCODEC = "org.appspot.apprtc.AUDIOCODEC";
  public static final String EXTRA_NOAUDIOPROCESSING_ENABLED =
          "org.appspot.apprtc.NOAUDIOPROCESSING";
  public static final String EXTRA_AECDUMP_ENABLED = "org.appspot.apprtc.AECDUMP";
  public static final String EXTRA_SAVE_INPUT_AUDIO_TO_FILE_ENABLED =
          "org.appspot.apprtc.SAVE_INPUT_AUDIO_TO_FILE";
  public static final String EXTRA_OPENSLES_ENABLED = "org.appspot.apprtc.OPENSLES";
  public static final String EXTRA_DISABLE_BUILT_IN_AEC = "org.appspot.apprtc.DISABLE_BUILT_IN_AEC";
  public static final String EXTRA_DISABLE_BUILT_IN_AGC = "org.appspot.apprtc.DISABLE_BUILT_IN_AGC";
  public static final String EXTRA_DISABLE_BUILT_IN_NS = "org.appspot.apprtc.DISABLE_BUILT_IN_NS";
  public static final String EXTRA_DISABLE_WEBRTC_AGC_AND_HPF =
          "org.appspot.apprtc.DISABLE_WEBRTC_GAIN_CONTROL";
  public static final String EXTRA_DISPLAY_HUD = "org.appspot.apprtc.DISPLAY_HUD";
  public static final String EXTRA_TRACING = "org.appspot.apprtc.TRACING";
  public static final String EXTRA_CMDLINE = "org.appspot.apprtc.CMDLINE";
  public static final String EXTRA_RUNTIME = "org.appspot.apprtc.RUNTIME";
  public static final String EXTRA_VIDEO_FILE_AS_CAMERA = "org.appspot.apprtc.VIDEO_FILE_AS_CAMERA";
  public static final String EXTRA_SAVE_REMOTE_VIDEO_TO_FILE =
          "org.appspot.apprtc.SAVE_REMOTE_VIDEO_TO_FILE";
  public static final String EXTRA_SAVE_REMOTE_VIDEO_TO_FILE_WIDTH =
          "org.appspot.apprtc.SAVE_REMOTE_VIDEO_TO_FILE_WIDTH";
  public static final String EXTRA_SAVE_REMOTE_VIDEO_TO_FILE_HEIGHT =
          "org.appspot.apprtc.SAVE_REMOTE_VIDEO_TO_FILE_HEIGHT";
  public static final String EXTRA_USE_VALUES_FROM_INTENT =
          "org.appspot.apprtc.USE_VALUES_FROM_INTENT";
  public static final String EXTRA_DATA_CHANNEL_ENABLED = "org.appspot.apprtc.DATA_CHANNEL_ENABLED";
  public static final String EXTRA_ORDERED = "org.appspot.apprtc.ORDERED";
  public static final String EXTRA_MAX_RETRANSMITS_MS = "org.appspot.apprtc.MAX_RETRANSMITS_MS";
  public static final String EXTRA_MAX_RETRANSMITS = "org.appspot.apprtc.MAX_RETRANSMITS";
  public static final String EXTRA_PROTOCOL = "org.appspot.apprtc.PROTOCOL";
  public static final String EXTRA_NEGOTIATED = "org.appspot.apprtc.NEGOTIATED";
  public static final String EXTRA_ID = "org.appspot.apprtc.ID";
  public static final String EXTRA_ENABLE_RTCEVENTLOG = "org.appspot.apprtc.ENABLE_RTCEVENTLOG";
  //add by ynhuang @ 20210830
  public static final String PROJECT_ID = "porjId";
  public static final String PROJECT_NAME = "projName";
  public static final String PROJECT_DEVICE_MODE = "projDeviceMode";
  public static final String PROJECT_TAKEPICTURE_MODE = "projTakepictureMode";
  public static final String PROJECT_GROUND = "projGround";
  public static final String USB_DEVICE = "usbDevice";
  public static final int TAKEPICTURE_MANUAL = 1;
  public static final int TAKEPICTURE_AUTO = 2;
  public static final int DEVICE_PHONE = 1;
  public static final int DEVICE_DRONE = 2;

  private static final int CAPTURE_PERMISSION_REQUEST_CODE = 1;

  // List of mandatory application permissions.
  private static final String[] MANDATORY_PERMISSIONS = {"android.permission.MODIFY_AUDIO_SETTINGS",
          "android.permission.RECORD_AUDIO", "android.permission.INTERNET",
          "android.permission.ACCESS_COARSE_LOCATION", "android.permission.ACCESS_FINE_LOCATION"};

  // Peer connection statistics callback period in ms.
  private static final int STAT_CALLBACK_PERIOD = 1000;

  LocationManager locationManager = null;
  Location gps_loc;
  Location network_loc;
  Location final_loc;
  double longitude;
  double latitude;
  double altitude;

  @Override
  public void onCameraOpened(@NonNull Camera2BasicFragment sender) {

  }

  @Override
  public void onCameraDisconnected(@NonNull Camera2BasicFragment sender) {

  }

  @Override
  public void onCameraError(@NonNull Camera2BasicFragment sender, int error) {

  }

  @Override
  public void onCameraClosed(@NonNull Camera2BasicFragment sender) {
    if (mKeepCameraOpen) {
      new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
        @Override
        public void run() {
//          sender.reopenCamera();
//          sendDataChannelMessage("Try to reopen camera.");
        }
      }, 5000);
    }
  }

  @Override
  public void onCameraCaptureStarted(@NonNull Camera2BasicFragment sender) {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        sendDataChannelMessage("Camera capture is started.");
      }
    });
  }

  @Override
  public void onCameraCaptureStopped(@NonNull Camera2BasicFragment sender) {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        sendDataChannelMessage("Camera capture is stopped.");
      }
    });
  }

  public double mYaw = 0, mPitch = 0, mRoll = 0;
  public double mLat, mLon, mAlt;

  @Override
  public void onImu(ScrollingFragment sender, double azimuth, double pitch, double roll) {
//    System.out.println("ynhuang, onimu: (azimuth, pitch, roll): ( + " + azimuth + ", " + pitch + ", " + roll + ")");
    mYaw = azimuth;
    mPitch = pitch;
    mRoll = roll;
//    yawText.setText(String.valueOf(mYaw));
//    pitchText.setText(String.valueOf(mPitch));
//    rollText.setText(String.valueOf(mRoll));
  }


  private String TAG_ACTION_GPS = "gps";
  private String TAG_ACTION_CAMERACTRL = "cameraCtrl";
  private String TAG_ACTION_CAMERAFEEDBACK = "cameraFeedback";
  private String TAG_ACTION_HEARTBEAT = "heartbeat";
  private String TAG_ACTION_STATUS = "status";

  @Override
  public void onMavlink(String tag, String str) {
    if(tag.equals(TAG_ACTION_HEARTBEAT) || tag.equals(TAG_ACTION_STATUS)) {
      String dcMessage = "";
      if(str.equals("connected")){
        dcMessage = "connected";
      } else {
        dcMessage = "disconnected";
      }
      String finalDcMessage = dcMessage;
      runOnUiThread(new Runnable() {
        @Override
        public void run() {
          // Stuff that updates the UI
          String message = str;//tag + ", " + str;//etInput.getText().toString();
          if (TextUtils.isEmpty(message)) {
            return;
          }
          String text = "我: " + message;
          tvContext.append(text + "\n");
          scrollContext.fullScroll(View.FOCUS_DOWN);
          sendDataChannelMessage(finalDcMessage);
          etInput.setText("");
        }
      });
    }
    if(tag.equals(TAG_ACTION_GPS)){
      String[] receiveSplit = str.split(",");
      setGps(Double.valueOf(receiveSplit[0]), Double.valueOf(receiveSplit[1]), Double.valueOf(receiveSplit[2]));
      runOnUiThread(new Runnable() {
        @Override
        public void run() {
          // Stuff that updates the UI
          tvContext.append(tag + ", " + str + "\n");
          scrollContext.fullScroll(View.FOCUS_DOWN);
        }
      });

    }
    if(tag.equals(TAG_ACTION_CAMERACTRL)){
//      mavlinkDigiCamMsg.setText("digiCam: " + str);
//        takePicture();
      flag = true;
//      System.out.println("ynhuang, autoTakepictureThread: " + autoTakepictureThread);
      if (autoTakepictureThread == null) {
        autoTakepictureThread = new AutoTakePictureThread();
        autoTakepictureThread.start();
        //AutoTakePictureThread thread = new AutoTakePictureThread();
        //thread.start();
      }
    }
  }

  @Override
  public void onDepthMin(DepthViewfinderFragment sender, float depthMin) {
//    System.out.println("ynhuang, onDepthMin(depthMin): " + depthMin);
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        // Stuff that updates the UI
        String message = "depthMin: " + depthMin;
        if (TextUtils.isEmpty(message)) {
          return;
        }
        String text = "我: " + message;
        tvContext.append(text + "\n");
        scrollContext.fullScroll(View.FOCUS_DOWN);
        sendDataChannelMessage(message);
        etInput.setText("");
      }
    });
  }

  private static class ProxyVideoSink implements VideoSink {
    private VideoSink target;

    @Override
    synchronized public void onFrame(VideoFrame frame) {
      if (target == null) {
        Logging.d(TAG, "Dropping frame in proxy because target is null.");
        return;
      }

      target.onFrame(frame);
    }

    synchronized public void setTarget(VideoSink target) {
      this.target = target;
    }
  }

  private final ProxyVideoSink remoteProxyRenderer = new ProxyVideoSink();
  private final ProxyVideoSink localProxyVideoSink = new ProxyVideoSink();
  @Nullable
  private PeerConnectionClient peerConnectionClient;
  @Nullable
  private AppRTCClient appRtcClient;
  @Nullable
  private SignalingParameters signalingParameters;
  @Nullable
  private AppRTCAudioManager audioManager;
  @Nullable
  private SurfaceViewRenderer pipRenderer;
  @Nullable
  private SurfaceViewRenderer fullscreenRenderer;
  @Nullable
  private VideoFileRenderer videoFileRenderer;
  private final List<VideoSink> remoteSinks = new ArrayList<>();
  private Toast logToast;
  private boolean commandLineRun;
  private boolean activityRunning;
  private RoomConnectionParameters roomConnectionParameters;
  @Nullable
  private PeerConnectionParameters peerConnectionParameters;
  private boolean connected;
  private boolean isError;
  private boolean callControlFragmentVisible = true;
  private long callStartedTimeMs;
  private boolean micEnabled = true;
  private boolean screencaptureEnabled;
  private static Intent mediaProjectionPermissionResultData;
  private static int mediaProjectionPermissionResultCode;
  // True if local view is in the fullscreen renderer.
  private boolean isSwappedFeeds;

  // Controls
  private CallFragment callFragment;
  private HudFragment hudFragment;
  private CpuMonitor cpuMonitor;

  //datachannel
  public static PeerConnectionClient.WebRtcListener webRtcListener;
  private NestedScrollView scrollContext;
  private TextView tvContext;
  private EditText etInput;
  //videoRecord
  private ImageButton btnRecordVideo;
  private boolean takepictureFlag = false;
  private boolean recordFlag = false;
  //takePicture
  private ImageButton btnTakepicture;//, btnAutoTakepicture;
  private boolean flag = false;
  private boolean autoThreadFlag = true;
  //  private Button btnFlash;
  public boolean depthImgFlag = true;
  public boolean threadFlag = true;
  private Handler handler, autoTakepictureHandler;
  private long tmpTs = 0;

  //add by ynhuang @ 20200629
  private Camera2BasicFragment mCam1Fragment;
  private DepthViewfinderFragment mDepthFragment;
  private Camera2VideoFragment mVideoFragment;
  private static final String FRAGMENT_TAG_CAM1 = "cam1";
  private static final String FRAGMENT_TAG_CAM_DEPTH = "depth";
  private static final String FRAGMENT_TAG_VIDEO = "video";
  private String mSnapshotNamePrefix = "";
  private boolean mKeepCameraOpen = false;
  private ScrollingFragment mScrollingFragment;
  private static final String FRAGMENT_TAG_IMU = "imu";

  //安裝app：如果是取像app appDef = appRecord, 如果是控制app addDef = appControl
  public static int appControl = 1;
  public static int appRecord = 2;
  public static int appDef = BuildConfig.FLAVOR.equalsIgnoreCase("record") ? appRecord : appControl;
  //拍照/錄影模式
  public static int modePicture = 1;
  public static int modeVideo = 2;
  public static int mode;//oncreate再初始化

  //add by ynhuang @ 20201127 new icon
  private ImageButton disconnectButton;
  private ImageButton modeSwitchButton;
  FrameLayout leftLayout, centerLayout, rightLayout, fullscreenBackground;

  //add by ynhuang @20210225
//  private TextView yawText, pitchText, rollText;

//  //add by ynhuang @ 20210129 broadcast
//  BroadcastReceiver receiveBroadCast;
//  private static final String BROADCAST_PERMISSION_DISC = "itri.org.broadcast.permission.MY_BROADCAST";
//  private static final String BROADCAST_ACTION_DISC = "itri.org.broadcast.permission.my_broadcast";
  //  TextView mavlinkMsg, mavlinkCameraFeedbackMsg;
  TextView mavlinkDigiCamMsg;//, gpsData;

  //add by ynhuang @ 20210315
  AutoUpload autoUpload;
  private double ground; //S8:85, S9:86, S10:87
  private int deviceMode;
  private int takepictureMode;
  private String uploadProjId, uploadProjName;
  private String serverpass;
  AutoTakePictureThread autoTakepictureThread;
  DepthThread thread;


  @Override
  // TODO(bugs.webrtc.org/8580): LayoutParams.FLAG_TURN_SCREEN_ON and
  // LayoutParams.FLAG_SHOW_WHEN_LOCKED are deprecated.
  @SuppressWarnings("deprecation")
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    Thread.setDefaultUncaughtExceptionHandler(new UnhandledExceptionHandler(this));

    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

    //add by ynhuang
    SharedPreferences sharedPreferences = getSharedPreferences("loginData", Context.MODE_PRIVATE);
//    String acc = sharedPreferences.getString("account","acc未存任何資料");
//    String pwd = sharedPreferences.getString("password","pwd未存任何資料");
    serverpass = sharedPreferences.getString("serverpass", "serverpass未存任何資料");
//    System.out.println("ynhuang, CallActivity: serverpass: " + serverpass);
//    //broadcast
//    receiveBroadCast = new ReceiveBroadCast();
//    IntentFilter filter = new IntentFilter();
//    filter.addAction(BROADCAST_ACTION_DISC); //只有值相同的action的接受者才能接收此廣播
//    registerReceiver(receiveBroadCast, filter, BROADCAST_PERMISSION_DISC, null);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

    // Set window styles for fullscreen-window size. Needs to be done before
    // adding content.
    requestWindowFeature(Window.FEATURE_NO_TITLE);
    getWindow().addFlags(LayoutParams.FLAG_FULLSCREEN | LayoutParams.FLAG_KEEP_SCREEN_ON
            | LayoutParams.FLAG_SHOW_WHEN_LOCKED | LayoutParams.FLAG_TURN_SCREEN_ON);
    getWindow().getDecorView().setSystemUiVisibility(getSystemUiVisibility());
    setContentView(R.layout.activity_call);

    final Intent intent = getIntent();
    final EglBase eglBase = EglBase.create();

    connected = false;
    signalingParameters = null;

    // Create UI controls.
    // input output message
    scrollContext = findViewById(R.id.scroll_context);
    tvContext = (TextView) findViewById(R.id.tv_context);
    etInput = (EditText) findViewById(R.id.et_input);
    // control button
    btnTakepicture = (ImageButton) findViewById(R.id.btn_takepicture);
//    btnAutoTakepicture = (ImageButton) findViewById(R.id.btn_auto_takepicture);
//    btnFlash = (Button) findViewById(R.id.btn_flash);
//    btnFlash.setVisibility(View.GONE);
    btnRecordVideo = (ImageButton) findViewById(R.id.btn_videoRecord);
    disconnectButton = (ImageButton) findViewById(R.id.btn_call_disconnect);
    modeSwitchButton = (ImageButton) findViewById(R.id.btn_switch_mode);

    leftLayout = (FrameLayout) findViewById(R.id.container_left);
    centerLayout = (FrameLayout) findViewById(R.id.container_center);
    rightLayout = (FrameLayout) findViewById(R.id.container_right);
    fullscreenBackground = (FrameLayout) findViewById(R.id.record_fullscreen_view_background);

//    yawText = (TextView) findViewById(R.id.yaw);
//    pitchText = (TextView) findViewById(R.id.pitch);
//    rollText = (TextView) findViewById(R.id.roll);
//    yawText.setText(String.valueOf(mYaw));
//    pitchText.setText(String.valueOf(mPitch));
//    rollText.setText(String.valueOf(mRoll));
//    mavlinkMsg = (TextView) findViewById(R.id.mavlinkMsg);
    mavlinkDigiCamMsg = (TextView) findViewById(R.id.mavlinkDigiCamMsg);
//    gpsData = (TextView) findViewById(R.id.gps);
//    mavlinkCameraFeedbackMsg = (TextView) findViewById(R.id.mavlinkCameraFeedbackMsg);

    mode = modePicture;//初始畫面為拍照

//    System.out.println("ynhuang, appDef: " + appDef);
    if (appDef == appRecord) {
      fullscreenBackground.setVisibility(View.VISIBLE);
      btnTakepicture.setVisibility(View.GONE);
//      btnAutoTakepicture.setVisibility(View.GONE);
//       btnFlash.setVisibility(View.INVISIBLE);
      btnRecordVideo.setVisibility(View.GONE);
      modeSwitchButton.setVisibility(View.GONE);
      if (mode == modePicture) {
        centerLayout.setVisibility(View.GONE);
      }
      tvContext.setVisibility(View.VISIBLE);
//      findViewById(R.id.record_fullscreen_view_background).setVisibility(View.VISIBLE);
      TextView recordText = (TextView) findViewById(R.id.record_string);
      recordText.setText("等待建立連線");
      recordText.setVisibility(View.VISIBLE);
      //add by ynhuang @ 20210315
      autoUpload = new AutoUpload(this);//, serverpass);
      //add by ynhuang @20211021
//      System.out.println("ynhuang, intent(USB_DEVICE): " + intent.getBundleExtra(USB_DEVICE));
      if(intent.getBundleExtra(USB_DEVICE) != null) {
//        Toast.makeText(CallActivity.this, "intent(USB_DEVICE): " + intent.getBundleExtra(USB_DEVICE), Toast.LENGTH_SHORT).show();
//        Toast.makeText(CallActivity.this, "intent(device, port, baud): " +
//                intent.getBundleExtra(USB_DEVICE).getInt("device") + ", " +
//                intent.getBundleExtra(USB_DEVICE).getInt("port") + ",  " +
//                intent.getBundleExtra(USB_DEVICE).getInt("baud"), Toast.LENGTH_SHORT).show();
        Fragment fragment = TerminalFragment.newInstance();//new TerminalFragment();
        fragment.setArguments(intent.getBundleExtra(USB_DEVICE));
        getSupportFragmentManager().beginTransaction().add(R.id.fragment, fragment, "terminal").commit();
      }
    } else if (appDef == appControl) {
      fullscreenBackground.setVisibility(View.GONE);
      rightLayout.setVisibility(View.GONE);
      centerLayout.setVisibility(View.GONE);
      modeSwitchButton.setVisibility(View.VISIBLE);
      mavlinkDigiCamMsg.setVisibility(View.GONE);
//        findViewById(R.id.record_fullscreen_view_background).setVisibility(View.GONE);
      findViewById(R.id.record_string).setVisibility(View.GONE);
//        findViewById(R.id.camera_view_layout).setVisibility(View.GONE);
      if (mode == modePicture) {
        btnRecordVideo.setVisibility(View.GONE);
        btnTakepicture.setVisibility(View.VISIBLE);
//        btnAutoTakepicture.setVisibility(View.VISIBLE);
      } else if (mode == modeVideo) {
        btnRecordVideo.setVisibility(View.VISIBLE);
        btnTakepicture.setVisibility(View.GONE);
//        btnAutoTakepicture.setVisibility(View.GONE);
      }
      System.out.println("ynhuang, intent (PROJECT_NAME, PROJECT_DEVICE_MODE, PROJECT_TAKEPICTURE_MODE, PROJECT_GROUND): " +
              intent.getStringExtra(PROJECT_NAME) + ", " +
              intent.getIntExtra(PROJECT_DEVICE_MODE, 1) + ", " +
              intent.getIntExtra(PROJECT_TAKEPICTURE_MODE, 1) + ", " +
              intent.getDoubleExtra(PROJECT_GROUND, 0));
      uploadProjId = intent.getStringExtra(PROJECT_ID);
      uploadProjName = intent.getStringExtra(PROJECT_NAME);
      deviceMode = intent.getIntExtra(PROJECT_DEVICE_MODE, 1);
      if (deviceMode == DEVICE_DRONE) {
        takepictureMode = intent.getIntExtra(PROJECT_TAKEPICTURE_MODE, 1);
        ground = intent.getDoubleExtra(PROJECT_GROUND, 0);
      } else if(deviceMode == DEVICE_PHONE){
        takepictureMode = 1;
        ground = 0.0;
      }
    }
    initDatachannelListener();

    // Create UI controls.
    pipRenderer = findViewById(R.id.pip_video_view);
    fullscreenRenderer = findViewById(R.id.fullscreen_video_view);
    callFragment = new CallFragment();
    hudFragment = new HudFragment();

    // Show/hide call control fragment on view click.
    View.OnClickListener listener = new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        toggleCallControlFragmentVisibility();
      }
    };

    // Swap feeds on pip view click.
    pipRenderer.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
//        System.out.println("ynhuang, onclick setSwappedFeeds");
        setSwappedFeeds(!isSwappedFeeds);
      }
    });

    fullscreenRenderer.setOnClickListener(listener);
    remoteSinks.add(remoteProxyRenderer);


    // Create video renderers.
    pipRenderer.init(eglBase.getEglBaseContext(), null);
    pipRenderer.setScalingType(ScalingType.SCALE_ASPECT_FIT);
    String saveRemoteVideoToFile = intent.getStringExtra(EXTRA_SAVE_REMOTE_VIDEO_TO_FILE);

    // When saveRemoteVideoToFile is set we save the video from the remote to a file.
    if (saveRemoteVideoToFile != null) {
      int videoOutWidth = intent.getIntExtra(EXTRA_SAVE_REMOTE_VIDEO_TO_FILE_WIDTH, 0);
      int videoOutHeight = intent.getIntExtra(EXTRA_SAVE_REMOTE_VIDEO_TO_FILE_HEIGHT, 0);
      try {
        videoFileRenderer = new VideoFileRenderer(
                saveRemoteVideoToFile, videoOutWidth, videoOutHeight, eglBase.getEglBaseContext());
        remoteSinks.add(videoFileRenderer);
      } catch (IOException e) {
        throw new RuntimeException(
                "Failed to open video file for output: " + saveRemoteVideoToFile, e);
      }
    }
    fullscreenRenderer.init(eglBase.getEglBaseContext(), null);
    fullscreenRenderer.setScalingType(ScalingType.SCALE_ASPECT_FILL);

    pipRenderer.setZOrderMediaOverlay(true);
    pipRenderer.setEnableHardwareScaler(true /* enabled */);
    fullscreenRenderer.setEnableHardwareScaler(false /* enabled */);
    // Start with local feed in fullscreen and swap it to the pip when the call is connected.
    setSwappedFeeds(true /* isSwappedFeeds */);

    // Check for mandatory permissions.
    for (String permission : MANDATORY_PERMISSIONS) {
      if (checkCallingOrSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
        logAndToast("Permission " + permission + " is not granted");
        setResult(RESULT_CANCELED);
        finish();
        return;
      }
    }

    Uri roomUri = intent.getData();
    if (roomUri == null) {
      logAndToast(getString(R.string.missing_url));
      Log.e(TAG, "Didn't get any URL in intent!");
      setResult(RESULT_CANCELED);
      finish();
      return;
    }

    // Get Intent parameters.
    String roomId = intent.getStringExtra(EXTRA_ROOMID);
    Log.d(TAG, "Room ID: " + roomId);
    if (roomId == null || roomId.length() == 0) {
      logAndToast(getString(R.string.missing_url));
      Log.e(TAG, "Incorrect room ID in intent!");
      setResult(RESULT_CANCELED);
      finish();
      return;
    }

    boolean loopback = intent.getBooleanExtra(EXTRA_LOOPBACK, false);
    boolean tracing = intent.getBooleanExtra(EXTRA_TRACING, false);

    int videoWidth = intent.getIntExtra(EXTRA_VIDEO_WIDTH, 0);
    int videoHeight = intent.getIntExtra(EXTRA_VIDEO_HEIGHT, 0);

    screencaptureEnabled = intent.getBooleanExtra(EXTRA_SCREENCAPTURE, false);
    // If capturing format is not specified for screencapture, use screen resolution.
    if (screencaptureEnabled && videoWidth == 0 && videoHeight == 0) {
      DisplayMetrics displayMetrics = getDisplayMetrics();
      videoWidth = displayMetrics.widthPixels;
      videoHeight = displayMetrics.heightPixels;
    }
    DataChannelParameters dataChannelParameters = null;
    if (intent.getBooleanExtra(EXTRA_DATA_CHANNEL_ENABLED, false)) {
      dataChannelParameters = new DataChannelParameters(intent.getBooleanExtra(EXTRA_ORDERED, true),
              intent.getIntExtra(EXTRA_MAX_RETRANSMITS_MS, -1),
              intent.getIntExtra(EXTRA_MAX_RETRANSMITS, -1), intent.getStringExtra(EXTRA_PROTOCOL),
              intent.getBooleanExtra(EXTRA_NEGOTIATED, false), intent.getIntExtra(EXTRA_ID, -1));
    }
    peerConnectionParameters =
            new PeerConnectionParameters(intent.getBooleanExtra(EXTRA_VIDEO_CALL, true), loopback,
                    tracing, videoWidth, videoHeight, intent.getIntExtra(EXTRA_VIDEO_FPS, 0),
                    intent.getIntExtra(EXTRA_VIDEO_BITRATE, 0), intent.getStringExtra(EXTRA_VIDEOCODEC),
                    intent.getBooleanExtra(EXTRA_HWCODEC_ENABLED, true),
                    intent.getBooleanExtra(EXTRA_FLEXFEC_ENABLED, false),
                    intent.getIntExtra(EXTRA_AUDIO_BITRATE, 0), intent.getStringExtra(EXTRA_AUDIOCODEC),
                    intent.getBooleanExtra(EXTRA_NOAUDIOPROCESSING_ENABLED, false),
                    intent.getBooleanExtra(EXTRA_AECDUMP_ENABLED, false),
                    intent.getBooleanExtra(EXTRA_SAVE_INPUT_AUDIO_TO_FILE_ENABLED, false),
                    intent.getBooleanExtra(EXTRA_OPENSLES_ENABLED, false),
                    intent.getBooleanExtra(EXTRA_DISABLE_BUILT_IN_AEC, false),
                    intent.getBooleanExtra(EXTRA_DISABLE_BUILT_IN_AGC, false),
                    intent.getBooleanExtra(EXTRA_DISABLE_BUILT_IN_NS, false),
                    intent.getBooleanExtra(EXTRA_DISABLE_WEBRTC_AGC_AND_HPF, false),
                    intent.getBooleanExtra(EXTRA_ENABLE_RTCEVENTLOG, false), dataChannelParameters);
    commandLineRun = intent.getBooleanExtra(EXTRA_CMDLINE, false);
    int runTimeMs = intent.getIntExtra(EXTRA_RUNTIME, 0);

    Log.d(TAG, "VIDEO_FILE: '" + intent.getStringExtra(EXTRA_VIDEO_FILE_AS_CAMERA) + "'");

    // Create connection client. Use DirectRTCClient if room name is an IP otherwise use the
    // standard WebSocketRTCClient.
    if (loopback || !DirectRTCClient.IP_PATTERN.matcher(roomId).matches()) {
      appRtcClient = new WebSocketRTCClient(this);
    } else {
      Log.i(TAG, "Using DirectRTCClient because room name looks like an IP.");
      appRtcClient = new DirectRTCClient(this);
    }
    // Create connection parameters.
    String urlParameters = intent.getStringExtra(EXTRA_URLPARAMETERS);
    roomConnectionParameters =
            new RoomConnectionParameters(roomUri.toString(), roomId, loopback, urlParameters);

    // Create CPU monitor
    if (CpuMonitor.isSupported()) {
      cpuMonitor = new CpuMonitor(this);
      hudFragment.setCpuMonitor(cpuMonitor);
    }

    // Send intent arguments to fragments.
    callFragment.setArguments(intent.getExtras());
    hudFragment.setArguments(intent.getExtras());
    // Activate call and HUD fragments and start the call.
    FragmentTransaction ft = getFragmentManager().beginTransaction();
    ft.add(R.id.call_fragment_container, callFragment);
    ft.add(R.id.hud_fragment_container, hudFragment);
    ft.commit();

    // For command line execution run connection for <runTimeMs> and exit.
    if (commandLineRun && runTimeMs > 0) {
      (new Handler()).postDelayed(new Runnable() {
        @Override
        public void run() {
          disconnect();
        }
      }, runTimeMs);
    }

    // Create peer connection client.
    peerConnectionClient = new PeerConnectionClient(
            getApplicationContext(), eglBase, peerConnectionParameters, CallActivity.this);
    PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
    if (loopback) {
      options.networkIgnoreMask = 0;
    }
    peerConnectionClient.createPeerConnectionFactory(options);

    if (screencaptureEnabled) {
      startScreenCapture();
    } else {
      startCall();
    }

    //add by ynhuang @ 20200709 initialize multiple camera
    if (null == savedInstanceState) {
      mCam1Fragment = Camera2BasicFragment.newInstance(1, true);//cameraChoice:1
      if (appDef == appRecord) {
        mDepthFragment = DepthViewfinderFragment.newInstance(true);
        mScrollingFragment = ScrollingFragment.newInstance();
      }
      mVideoFragment = Camera2VideoFragment.newInstance();

      if (appDef == appRecord) {
        getSupportFragmentManager().beginTransaction()
                .add(R.id.container_right, mCam1Fragment, FRAGMENT_TAG_CAM1)
                .add(R.id.container_left, mDepthFragment, FRAGMENT_TAG_CAM_DEPTH)
                .add(R.id.container_center, mScrollingFragment, FRAGMENT_TAG_IMU)
//                    .add(R.id.container_center, mVideoFragment, FRAGMENT_TAG_VIDEO)
                .commit();
      } else if (appDef == appControl) {
        getSupportFragmentManager().beginTransaction()
                .add(R.id.container_right, mCam1Fragment, FRAGMENT_TAG_CAM1)
                .add(R.id.container_center, mVideoFragment, FRAGMENT_TAG_VIDEO)
                .commit();
      }
    } else {
      String[] tags = {FRAGMENT_TAG_CAM1, FRAGMENT_TAG_CAM_DEPTH, FRAGMENT_TAG_VIDEO};
      if (appDef == appControl) {
        tags = new String[]{FRAGMENT_TAG_CAM1, FRAGMENT_TAG_VIDEO};
      }
      mCam1Fragment = null;
      if (appDef == appRecord) {
        mDepthFragment = null;
      }
      mVideoFragment = null;
      for (String tag : tags) {
        Fragment fragment = getSupportFragmentManager().findFragmentByTag(tag);
        if (fragment instanceof Camera2BasicFragment) {
          if (tag.equals(FRAGMENT_TAG_CAM1)) {
            mCam1Fragment = (Camera2BasicFragment) fragment;
          }
        } else if (fragment instanceof DepthViewfinderFragment && tag.equals(FRAGMENT_TAG_CAM_DEPTH)) {
          mDepthFragment = (DepthViewfinderFragment) fragment;
        } else if (fragment instanceof Camera2VideoFragment && tag.equals(FRAGMENT_TAG_VIDEO)) {
          mVideoFragment = (Camera2VideoFragment) fragment;
        }
      }
    }
    mKeepCameraOpen = true;
    initTakePictureListener();
    initImuListenter();
    initDepthMinListener();

    if (appDef == appRecord) {
      handler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(@androidx.annotation.NonNull Message msg) {
          switch (msg.what) {
            case 0:
              mDepthFragment.bufferDepthImg();
              break;
            default:
              break;
          }
          return true;
        }
      });
      thread = new DepthThread();
      thread.start();
    }

    if (appDef == appRecord) {
      autoTakepictureHandler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(@androidx.annotation.NonNull Message msg) {
          switch (msg.what) {
            case 0:
              takePicture();
              break;
            default:
              break;
          }
          return true;
        }
      });
    }
  }

  //add by ynhuang @ 20200721
  class DepthThread extends Thread {
    @Override
    public void run() {
      while (threadFlag) {
        while (true) {
          if (depthImgFlag) {
            handler.sendEmptyMessage(0);
            try {
              Thread.sleep(33);
            } catch (Exception e) {
              e.printStackTrace();
            }
          } else {
//            System.out.println("ynhuang, DepthThread saveDepth");
            mDepthFragment.saveDepthImg(tmpTs);
            depthImgFlag = true;
          }
        }
      }
    }
  }

  int count = 0, countBreak = 3;//countBreak:拍幾張
  long sleepInterval = 700;// Long.parseLong(interval.getText().toString())*1000;

  //add by ynhuang @ 20201026
  class AutoTakePictureThread extends Thread {
    @Override
    public void run() {
      synchronized (this) {
        while (autoThreadFlag) {
          while (true) {
            if (flag) {
              autoTakepictureHandler.sendEmptyMessage(0);
              try {
                count++;
                if (count == countBreak) {
                  flag = false;
                  count = 0;
                }
                Thread.sleep(sleepInterval);
              } catch (Exception e) {
                e.printStackTrace();
              }
            }
          }
        }
      }
    }
  }
  //add by ynhuang end
//  class AutoTakePictureThread extends Thread {
//    @Override
//    public void run() {
//      while (flag) {
//        autoTakepictureHandler.sendEmptyMessage(0);
//        long sleepInterval = 1000;// Long.parseLong(interval.getText().toString())*1000;
//        try {
//          Thread.sleep(sleepInterval);
//        } catch (Exception e) {
//          e.printStackTrace();
//        }
//      }
//    }
//  }
  //add by ynhuang end

  public void initTakePictureListener() {
    boolean flag = false; //control thread for buffer depth image
    if (mCam1Fragment != null) {
      mCam1Fragment.setOnTakePictureListener(this);
    }
    if (mDepthFragment != null) {
      mDepthFragment.setOnTakePictureListener(this);
      //add by ynhuang @ 20200722
      mDepthFragment.setOnBufferSnapshotListener(this);
    }
  }

  public void initImuListenter() {
    if (mScrollingFragment != null) {
      mScrollingFragment.setOnImuListener(this);
    }
  }
  public void initDepthMinListener() {
    if(mDepthFragment != null) {
      mDepthFragment.setOnDepthMinListener(this);
    }
  }

  //  FileOutputStream output = null;
  OutputStream output = null;
  //add by ynhuang @ 20201209 add jpg to DCIM
  File imageFile = null;
  Uri imageUri = null;
  String imageName = "";

  @Override
  public void onTakePicture(Camera2BasicFragment sender, Image image, String header, int dataLength,
                            Size sensorArraySize, Rect preCorrectionArraySize, Rect activeArraySize,
                            float[] distortion, float[] intrinsicCalibration, float[] poseTranslation,
                            float[] poseRotation) {
//    System.out.println("ynhuang, onTakepicture()");
    byte[] byteHeader = header.getBytes();
    byte[] byteDataLength = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(dataLength).array();
    byte[] byteSensorArraySizeWidth = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(sensorArraySize.getWidth()).array();
    byte[] byteSensorArraySizeHeight = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(sensorArraySize.getHeight()).array();
    byte[] bytePreCorrectionArraySizeLeft = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(preCorrectionArraySize.left).array();
    byte[] bytePreCorrectionArraySizeTop = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(preCorrectionArraySize.top).array();
    byte[] bytePreCorrectionArraySizeRight = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(preCorrectionArraySize.right).array();
    byte[] bytePreCorrectionArraySizeBottom = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(preCorrectionArraySize.bottom).array();
    byte[] byteActiveArraySizeLeft = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(activeArraySize.left).array();
    byte[] byteActiveArraySizeTop = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(activeArraySize.top).array();
    byte[] byteActiveArraySizeRight = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(activeArraySize.right).array();
    byte[] byteActiveArraySizeBottom = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(activeArraySize.bottom).array();
    byte[] byteDistortion = floatArrayToByteArray(distortion);
    byte[] byteIntrinsicCalibration = floatArrayToByteArray(intrinsicCalibration);
    byte[] bytePoseTranslation = floatArrayToByteArray(poseTranslation);
    byte[] bytePoseRotation = floatArrayToByteArray(poseRotation);

    ByteBuffer buffer = image.getPlanes()[0].getBuffer();
    byte[] bytes = new byte[buffer.remaining()];
    buffer.get(bytes);

    //add by ynhuang @ 20201209 add jpg to DCIM
//    OutputStream fos;
    ContentResolver resolver = this.getContentResolver();
    ContentValues contentValues = new ContentValues();
    imageName = mSnapshotNamePrefix;//+ sender.getTag() + "_" + image.getTimestamp();
    contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, imageName + ".jpg");//mSnapshotNamePrefix + sender.getTag() + "_" + image.getTimestamp() + ".jpg");
    contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
    contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DCIM + File.separator + "Camera");
    imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues);
    imageFile = ExifUtils.getFileFromContentUri(imageUri, this);
//      System.out.println("ynhuang, getFileFromContentUri: " + ExifUtils.getFileFromContentUri(imageUri, this));
    try {
      output = resolver.openOutputStream(imageUri);
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    }

//    final File jpgFile = new File(getExternalFilesDir(null), mSnapshotNamePrefix + sender.getTag() + "_" + image.getTimestamp() + ".jpg");
//    Log.d(TAG, "onTakePicture: save to " + jpgFile.getPath());
    //FileOutputStream output = null;
    try {
      //add by ynhuang @ 20200716
      depthImgFlag = false;
      tmpTs = image.getTimestamp();
      //add by ynhuang end

//          output = new FileOutputStream(jpgFile);
      output.write(bytes);
      //add by ynhuang @ 20200824 add metadata
      output.write(byteHeader);
      output.write(byteDataLength);
      output.write(byteSensorArraySizeWidth);
      output.write(byteSensorArraySizeHeight);
      output.write(bytePreCorrectionArraySizeLeft);
      output.write(bytePreCorrectionArraySizeTop);
      output.write(bytePreCorrectionArraySizeRight);
      output.write(bytePreCorrectionArraySizeBottom);
      output.write(byteActiveArraySizeLeft);
      output.write(byteActiveArraySizeTop);
      output.write(byteActiveArraySizeRight);
      output.write(byteActiveArraySizeBottom);
      output.write(byteDistortion);
      output.write(byteIntrinsicCalibration);
      output.write(bytePoseTranslation);
      output.write(bytePoseRotation);

      runOnUiThread(new Runnable() {
        @Override
        public void run() {
//                Toast.makeText(CallActivity.this, "Success to save " + contentValues.get(MediaStore.MediaColumns.RELATIVE_PATH) + File.separator + contentValues.get(MediaStore.MediaColumns.DISPLAY_NAME) + ", " + depthImgFlag, Toast.LENGTH_SHORT).show();
//                Toast.makeText(CallActivity.this, "Success to save " + jpgFile.getName() + ", " + depthImgFlag, Toast.LENGTH_SHORT).show();
          Logging.d(TAG, "CallActivity datachannel depthSave");

          String message = "primarySaved";
          if (TextUtils.isEmpty(message)) {
            return;
          }
          String text = "我: " + message;
          tvContext.append(text + "\n");
          scrollContext.fullScroll(View.FOCUS_DOWN);
          sendDataChannelMessage(message);
          etInput.setText("");
        }
      });
    } catch (IOException e) {
      e.printStackTrace();
    } finally {
      image.close();
      try {
        ExifUtils.setImgMake(imageFile);
        ExifUtils.setImgMakerNotes(imageFile, mYaw, mPitch, mRoll);
//        mLat = 24.888812; mLon = 121.284045; mAlt = 100.000;
        if (deviceMode == DEVICE_PHONE) {
//          getLocation();
        }
//        System.out.println("ynhuang, autoupload(mLat, mLon, mAlt): " + mLat + ", " + mLon + ", " + mAlt);
        ExifUtils.setImgGPS(imageFile, mLat, mLon, mAlt);
        autoUpload.uploadImage(imageFile, imageName, serverpass, uploadProjId);
      } catch (IOException | JSONException e) {
        e.printStackTrace();
      }
      if (null != output) {
        try {
          output.close();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }
  }

  @Override
  public void onTakePictureError(Camera2BasicFragment sender, Error error) {
    sendDataChannelMessage("Failed to take picture");
  }

  @Override
  public void onTakePicture(DepthViewfinderFragment sender, @Nullable final Bitmap depthImage, @Nullable final short[] depthRaw, long timestamp) {
    final long currentTimeMillis = System.currentTimeMillis();
    final File jpgFile = new File(getExternalFilesDir(null), mSnapshotNamePrefix + sender.getTag() + "_" + timestamp + ".jpg");
    final File rawFile = new File(getExternalFilesDir(null), mSnapshotNamePrefix + sender.getTag() + "_" + timestamp + ".raw");

    new AsyncTask<Void, Void, Boolean>() {
      @Override
      protected Boolean doInBackground(Void... voids) {
        boolean success = false;

        if (depthImage != null) {
          FileOutputStream output = null;
          try {
            output = new FileOutputStream(jpgFile);
            success = depthImage.compress(Bitmap.CompressFormat.JPEG, 100, output);
            depthImage.recycle();
          } catch (FileNotFoundException e) {
            e.printStackTrace();
          } finally {
            if (output != null) {
              try {
                output.close();
              } catch (IOException e) {
                e.printStackTrace();
              }
            }
          }
        }

        if (depthRaw != null) {
          FileChannel outChannel = null;
          try {
            outChannel = new FileOutputStream(rawFile).getChannel();
            ByteBuffer byteBuffer = ByteBuffer.allocate(depthRaw.length * 2);
            byteBuffer.asShortBuffer().put(depthRaw);
            success &= (byteBuffer.limit() == outChannel.write(byteBuffer));
          } catch (IOException e) {
            e.printStackTrace();
          } finally {
            if (outChannel != null) {
              try {
                outChannel.close();
              } catch (IOException e) {
                e.printStackTrace();
              }
            }
          }
        }

        return success;
      }

      @Override
      protected void onPostExecute(Boolean success) {
        if (success) {
          Toast.makeText(CallActivity.this, "Success to save " + jpgFile.getName(), Toast.LENGTH_SHORT).show();
          //add by ynhuang @ 20200717
          String message = "depthSaved";//etInput.getText().toString();
          if (TextUtils.isEmpty(message)) {
            return;
          }
          String text = "我: " + message;
          tvContext.append(text + "\n");
          scrollContext.fullScroll(View.FOCUS_DOWN);
          sendDataChannelMessage(message);
          etInput.setText("");
        }
      }
    }.execute();
  }

  @Override //add by ynhuang @ 20200722
  public void onBufferSnapshot(DepthViewfinderFragment sender, @Nullable Bitmap depthImage,
                               @Nullable byte[] byteArray, @Nullable short[] depthRaw, long timestamp,
                               Size sensorArraySize, Rect preCorrectionArraySize, Rect activeArraySize,
                               float[] distortion, float[] intrinsicCalibration, float[] poseTranslation,
                               float[] poseRotation, int dimensionX, int dimensionY) {
    final long currentTimeMillis = System.currentTimeMillis();
//      final File jpgFile = new File(getExternalFilesDir(null), mSnapshotNamePrefix + sender.getTag() + "_" + timestamp + ".jpg");
    final File rawFile = new File(getExternalFilesDir(null), mSnapshotNamePrefix + "_" + sender.getTag() + "_" + timestamp + ".raw");
//System.out.println("ynhuang, raw: " + rawFile);
    byte[] byteSensorArraySizeWidth = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(sensorArraySize.getWidth()).array();
    byte[] byteSensorArraySizeHeight = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(sensorArraySize.getHeight()).array();
    byte[] bytePreCorrectionArraySizeLeft = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(preCorrectionArraySize.left).array();
    byte[] bytePreCorrectionArraySizeTop = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(preCorrectionArraySize.top).array();
    byte[] bytePreCorrectionArraySizeRight = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(preCorrectionArraySize.right).array();
    byte[] bytePreCorrectionArraySizeBottom = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(preCorrectionArraySize.bottom).array();
    byte[] byteActiveArraySizeLeft = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(activeArraySize.left).array();
    byte[] byteActiveArraySizeTop = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(activeArraySize.top).array();
    byte[] byteActiveArraySizeRight = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(activeArraySize.right).array();
    byte[] byteActiveArraySizeBottom = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(activeArraySize.bottom).array();
    byte[] byteDistortion = floatArrayToByteArray(distortion);
    byte[] byteIntrinsicCalibration = floatArrayToByteArray(intrinsicCalibration);
    byte[] bytePoseTranslation = floatArrayToByteArray(poseTranslation);
    byte[] bytePoseRotation = floatArrayToByteArray(poseRotation);
    byte[] byteDimensionX = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(dimensionX).array();
    byte[] byteDimensionY = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(dimensionY).array();

    ByteBuffer buffer = ByteBuffer.allocate(depthRaw.length * 2).order(ByteOrder.LITTLE_ENDIAN);
    buffer.asShortBuffer().put(depthRaw);
    byte[] byteDepthRaw = buffer.array();//Arrays.toString(depthRaw).getBytes();

    new AsyncTask<Void, Void, Boolean>() {
      @Override
      protected Boolean doInBackground(Void... voids) {
        boolean success = false;
        if (depthImage != null) {
          //FileOutputStream output = null;
          try {
            //output = new FileOutputStream(jpgFile);
            output.write(byteSensorArraySizeWidth);
            output.write(byteSensorArraySizeHeight);
            output.write(bytePreCorrectionArraySizeLeft);
            output.write(bytePreCorrectionArraySizeTop);
            output.write(bytePreCorrectionArraySizeRight);
            output.write(bytePreCorrectionArraySizeBottom);
            output.write(byteActiveArraySizeLeft);
            output.write(byteActiveArraySizeTop);
            output.write(byteActiveArraySizeRight);
            output.write(byteActiveArraySizeBottom);
            output.write(byteDistortion);
            output.write(byteIntrinsicCalibration);
            output.write(bytePoseTranslation);
            output.write(bytePoseRotation);
            output.write(byteDimensionY);
            output.write(byteDimensionX);
            output.write(byteDepthRaw);

            success = depthImage.compress(Bitmap.CompressFormat.JPEG, 100, output);
            depthImage.recycle();
          } catch (FileNotFoundException e) {
            e.printStackTrace();
          } catch (IOException e) {
            e.printStackTrace();
          } finally {
            if (output != null) {
              try {
                output.close();
              } catch (IOException e) {
                e.printStackTrace();
              }
            }
          }
        }

        if (depthRaw != null) {
          FileChannel outChannel = null;
          try {
            outChannel = new FileOutputStream(rawFile).getChannel();
            ByteBuffer byteBuffer = ByteBuffer.allocate(depthRaw.length * 2);
            byteBuffer.asShortBuffer().put(depthRaw);
            success &= (byteBuffer.limit() == outChannel.write(byteBuffer));
          } catch (IOException e) {
            e.printStackTrace();
          } finally {
            if (outChannel != null) {
              try {
                outChannel.close();
              } catch (IOException e) {
                e.printStackTrace();
              }
            }
          }
        }

        return success;
      }

      @Override
      protected void onPostExecute(Boolean success) {
        if (success) {
//          Toast.makeText(CallActivity.this, "bufferDepth Success to save " /*+ jpgFile.getName()*/, Toast.LENGTH_SHORT).show();
        }
      }
    }.execute();
  }

  public static byte[] floatArrayToByteArray(float[] floats) {
    ByteBuffer buffer = ByteBuffer.allocate(4 * floats.length).order(ByteOrder.LITTLE_ENDIAN);
    buffer.asFloatBuffer().put(floats);
    return buffer.array();
  }

  private void refreshRecordButtonState() {
    if (recordFlag) {
      btnRecordVideo.setImageResource(R.drawable.icon_02);
    } else {
      btnRecordVideo.setImageResource(R.drawable.icon_07);
    }
  }

  private void initDatachannelListener() {
    btnTakepicture.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        String message = "takePicture";//etInput.getText().toString();
        if (TextUtils.isEmpty(message)) {
          return;
        }
        String text = "我: " + message;
        tvContext.append(text + "\n");
        scrollContext.fullScroll(View.FOCUS_DOWN);
        sendDataChannelMessage(message);
        etInput.setText("");
      }
    });
    //switch mode
    modeSwitchButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        onBtnSwitchMode();
        if (mode == modePicture) {
          modeSwitchButton.setImageResource(R.drawable.icon_03click);
          mode = modeVideo;
        } else if (mode == modeVideo) {
          modeSwitchButton.setImageResource(R.drawable.icon_05click);
          mode = modePicture;
        }
      }
    });
    //disconnect
    disconnectButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        callFragment.callEvents.onCallHangUp();
      }
    });

    //auto takepicture
//      btnAutoTakepicture.setOnClickListener(new View.OnClickListener() {
//        @Override
//        public void onClick(View v) {
//          String message = "autoTakePictureStart";
//          btnAutoTakepicture.setImageResource(R.drawable.icon_02);
//          if(takepictureFlag){
//            message = "autoTakePictureStop";
//            btnAutoTakepicture.setImageResource(R.drawable.icon_01);
//          }
//          if (TextUtils.isEmpty(message)){
//            return;
//          }
//          String text = "我: " + message;
//          tvContext.append(text + "\n");
//          scrollContext.fullScroll(View.FOCUS_DOWN);
//          sendDataChannelMessage(message);
//          etInput.setText("");
//          takepictureFlag = !takepictureFlag;
//        }
//      });
    //record video
    btnRecordVideo.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        String message = "videoRecordStart";
        if (recordFlag) {
          message = "videoRecordStop";
        }
        recordFlag = !recordFlag;
        refreshRecordButtonState();
        if (TextUtils.isEmpty(message)) {
          return;
        }
        String text = "我: " + message;
        tvContext.append(text + "\n");
        scrollContext.fullScroll(View.FOCUS_DOWN);
        sendDataChannelMessage(message);
        etInput.setText("");
      }
    });

//      btnFlash.setOnClickListener(new View.OnClickListener() {
//        @Override
//        public void onClick(View v) {
//            String message = "flash";//etInput.getText().toString();
//            if (TextUtils.isEmpty(message)){
//                return;
//            }
//            String text = "我: " + message;
//            tvContext.setText(tvContext.getText().toString() + "\n" + text);
//            sendDataChannelMessage(message);
//            etInput.setText("");
//
//            mCam1Fragment.setFlashOn();
//        }
//      });

    this.setWebRtcListener(new PeerConnectionClient.WebRtcListener() {
      @Override
      public void onReceiveDataChannelMessage(final String message) {
        Logging.d(TAG, "callActivity onReceiveDataChannelMessage");
        runOnUiThread(new Runnable() {
          @Override
          public void run() {
            tvContext.append("對方: " + message + "\n");
            scrollContext.fullScroll(View.FOCUS_DOWN);
//            System.out.println("ynhuang, message.split.length: " + message.split(":").length);
            String[] settingMsg = message.split(":");
            if (settingMsg.length == 2 && settingMsg[0].equals("setting")) {
              if (appDef == appRecord) {
//                        System.out.println("ynhuang, (PROJECT_ID, PROJECT_DEVICE_MODE, PROJECT_TAKEPICTURE_MODE, PROJECT_GROUND): " +
//                                settingMsg[1].split(",")[0] + ", " +
//                                settingMsg[1].split(",")[1] + ", " +
//                                settingMsg[1].split(",")[2] + ", " +
//                                settingMsg[1].split(",")[3]);
                uploadProjId = settingMsg[1].split(",")[0].replaceAll(" ", "");
				uploadProjName = settingMsg[1].split(",")[1].replace(" ", "");
                deviceMode = Integer.parseInt(settingMsg[1].split(",")[2].replaceAll(" ", ""));
                if (deviceMode == DEVICE_DRONE) {
                  takepictureMode = Integer.parseInt(settingMsg[1].split(",")[3].replaceAll(" ", ""));
                  ground = Double.parseDouble(settingMsg[1].split(",")[4].replaceAll(" ", ""));
                } else if(deviceMode == DEVICE_PHONE) {
				  takepictureMode = 1;
				  ground = 0.0;
//                  System.out.println("ynhuang, device mode = phone && appdef = record");
                  locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
//                  System.out.println("ynhuang, oncreate locationmanager: " + locationManager);
                  if (ActivityCompat.checkSelfPermission(CallActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(CallActivity.this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    // TODO: Consider calling
                    //    ActivityCompat#requestPermissions
                    // here to request the missing permissions, and then overriding
                    //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                    //                                          int[] grantResults)
                    // to handle the case where the user grants the permission. See the documentation
                    // for ActivityCompat#requestPermissions for more details.
                    return;
                  }
                  Location lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
//                  gpsData.setText("oncreate 維度:" + lastKnownLocation.getLatitude() + " 經度:" + lastKnownLocation.getLongitude() + " 高度:" + lastKnownLocation.getAltitude());
                  LocationListener locationListener = new LocationListener() {
                    @Override
                    public void onLocationChanged(Location location) {
//                      System.out.println("ynhuang, onLocationChanged");
                      if (location != null) {
//                        gpsData.setText("維度:" + location.getLatitude() + " 經度:" + location.getLongitude() + " 高度:" + location.getAltitude() + " 時間: " + Calendar.getInstance().getTime());
                        getLocation(location);
                      } else {
//                        gpsData.setText("獲取不到資料");
                      }
                    }

                    @Override
                    public void onStatusChanged(String provider, int status, Bundle extras) {
//                      System.out.println("ynhuang, onStatusChanged");
                    }

                    @Override
                    public void onProviderEnabled(String provider) {
//                      System.out.println("ynhuang, onProviderEnabled");
                    }

                    @Override
                    public void onProviderDisabled(String provider) {
//                      System.out.println("ynhuang, onProviderDisabled");
                    }
                  };
                  locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000, 1, locationListener);
                }
              }


              tvContext.append("record device setting(專案, 手持/無人機, 拍照模式, 地面高度): " + uploadProjId + ", " + deviceMode + ", " + takepictureMode + ", " + ground + "\n");
              System.out.println("ynhuang, record device setting(專案ID, 手持/無人機, 拍照模式, 地面高度): " + uploadProjId + ", " + deviceMode + ", " + takepictureMode + ", " + ground);
            }

            if (message.equals("takePicture")) {
//                      peerConnectionClient.stopVideoCapture();
//                      System.out.println("ynhuang, onReceiveDataChannelMessage: " + message);
              //takePicture();
              flag = true;
              count = 0;
//              System.out.println("ynhuang, autoTakepictureThread: " + autoTakepictureThread);
              if (autoTakepictureThread == null) {
                autoTakepictureThread = new AutoTakePictureThread();
                autoTakepictureThread.start();
                //AutoTakePictureThread thread = new AutoTakePictureThread();
                //thread.start();
              }
            } else if (message.equals("autoTakePictureStart")) {
              flag = true;
              AutoTakePictureThread thread = new AutoTakePictureThread();
              thread.start();
//          BufferDepthThread threadBufferDepth = new BufferDepthThread();
//          threadBufferDepth.start();
//          btnTakepicture.performClick();
//          takepictureStatus.setText("true");
            } else if (message.equals("autoTakePictureStop")) {
              flag = false;
              //add by ynhuang @ 20200730
//                mCam1Fragment.setFlashOff();
            } else if (message.equals("primarySaved")) {
//                      logAndToast("toast message: " + message);
            } else if (message.equals("depthSaved")) {
//                      logAndToast("toast message: " + message);
            } else if(message.equals("flash")){
              mCam1Fragment.setFlashOn();
            } else if (message.equals("videoRecordStart")) {
//                        mVideoFragment.startRecordingVideo();
              mCam1Fragment.startRecordingVideo();
            } else if (message.equals("videoRecordStop")) {
//                        mVideoFragment.stopRecordingVideo();
              mCam1Fragment.stopRecordingVideo();
            } else if (message.equals("switchMode")) {
              if (mode == modePicture) {
//                          Logging.d(TAG, "CallActivity 進入錄影模式");
                mode = modeVideo;
                //初始化錄影模式
//                            if(mVideoFragment.isAdded()) {
//                              getSupportFragmentManager().beginTransaction()
//                                      .hide(mCam1Fragment)
//                                      .hide(mDepthFragment)
//                                      .show(mVideoFragment)
//                                      .commit();
//                              leftLayout.setVisibility(View.GONE);
//                              centerLayout.setVisibility(View.VISIBLE);
//                              rightLayout.setVisibility(View.GONE);
//                              return; //or return false/true, based on where you are calling from
//                            } else {
//                              getSupportFragmentManager().beginTransaction()
//                                      .hide(mCam1Fragment)
//                                      .hide(mDepthFragment)
//                                      .add(R.id.container_center, mVideoFragment, FRAGMENT_TAG_VIDEO)
//                                      .commit();
//                              leftLayout.setVisibility(View.GONE);
//                              centerLayout.setVisibility(View.VISIBLE);
//                              rightLayout.setVisibility(View.GONE);
//                            }
              } else if (mode == modeVideo) {
                Logging.d(TAG, "CallActivity 進入拍照模式");
                if (mCam1Fragment.isRecordingVideo()) {
                  mCam1Fragment.stopRecordingVideo();
                }
                mode = modePicture;
                //初始化拍照模式
//                            if(mCam1Fragment.isAdded() || mDepthFragment.isAdded()) {
//                              getSupportFragmentManager().beginTransaction()
//                                      .show(mCam1Fragment)
//                                      .show(mDepthFragment)
//                                      .hide(mVideoFragment)
//                                      .commit();
//                              leftLayout.setVisibility(View.VISIBLE);
//                              centerLayout.setVisibility(View.GONE);
//                              rightLayout.setVisibility(View.VISIBLE);
////                              return; //or return false/true, based on where you are calling from
//                            } else {
//                              getSupportFragmentManager().beginTransaction()
//                                      .add(R.id.container_right, mCam1Fragment, FRAGMENT_TAG_CAM1)
//                                      .add(R.id.container_left, mDepthFragment, FRAGMENT_TAG_CAM_DEPTH)
//                                      .hide(mVideoFragment)
//                                      .commit();
//                              leftLayout.setVisibility(View.VISIBLE);
//                              centerLayout.setVisibility(View.GONE);
//                              rightLayout.setVisibility(View.VISIBLE);
//                            }
              }
            } else {
              logAndToast("toast message: " + message);
            }
          }
        });
      }
    });
  }

  public void setWebRtcListener(PeerConnectionClient.WebRtcListener webRtcListener) {
    Logging.d(TAG, "CallActivity setWebRtcListener");
    this.webRtcListener = webRtcListener;
  }

  /**
   * 發訊息
   *
   * @param message
   */
  public void sendDataChannelMessage(String message) {
    Logging.d(TAG, "CallActivity sendDataChannelMessage: " + message);
    if (PeerConnectionClient.dataChannel == null || PeerConnectionClient.dataChannel.state() != DataChannel.State.OPEN)
      return;
    byte[] msg = message.getBytes();
    DataChannel.Buffer buffer = new DataChannel.Buffer(
            ByteBuffer.wrap(msg),
            false);
    PeerConnectionClient.dataChannel.send(buffer);
  }

  public void takePicture() {
    //mSnapshotNamePrefix = System.currentTimeMillis() + "_";
    //convert timestamp to datetime;
    SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss-SSS");
    mSnapshotNamePrefix = formatter.format(new Date(System.currentTimeMillis()));// + "_";

    if (mCam1Fragment != null) {
      mCam1Fragment.takePicture();
    }
//    if (mDepthFragment != null) {
//      Log.d(TAG, "takePicture() mDepthFragment");
//      mDepthFragment.triggerSnapshot();
//    }
  }

  @TargetApi(17)
  private DisplayMetrics getDisplayMetrics() {
    DisplayMetrics displayMetrics = new DisplayMetrics();
    WindowManager windowManager =
            (WindowManager) getApplication().getSystemService(Context.WINDOW_SERVICE);
    windowManager.getDefaultDisplay().getRealMetrics(displayMetrics);
    return displayMetrics;
  }

  @TargetApi(19)
  private static int getSystemUiVisibility() {
    int flags = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
      flags |= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
    }
    return flags;
  }

  @TargetApi(21)
  private void startScreenCapture() {
    MediaProjectionManager mediaProjectionManager =
            (MediaProjectionManager) getApplication().getSystemService(
                    Context.MEDIA_PROJECTION_SERVICE);
    startActivityForResult(
            mediaProjectionManager.createScreenCaptureIntent(), CAPTURE_PERMISSION_REQUEST_CODE);
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent data) {
    if (requestCode != CAPTURE_PERMISSION_REQUEST_CODE)
      return;
    mediaProjectionPermissionResultCode = resultCode;
    mediaProjectionPermissionResultData = data;
    startCall();
  }

  private boolean useCamera2() {
    return Camera2Enumerator.isSupported(this) && getIntent().getBooleanExtra(EXTRA_CAMERA2, true);
  }

  private boolean captureToTexture() {
    return getIntent().getBooleanExtra(EXTRA_CAPTURETOTEXTURE_ENABLED, false);
  }

  private @Nullable
  VideoCapturer createCameraCapturer(CameraEnumerator enumerator) {
    final String[] deviceNames = enumerator.getDeviceNames();
    // First, try to find back camera
    Logging.d(TAG, "Looking for back cameras.");
    for (String deviceName : deviceNames) {
      if (enumerator.isBackFacing(deviceName) && !deviceName.equals("0")) {
        Logging.d(TAG, "Creating back camera capturer.");
        VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null); //20210503 註解
//        VideoCapturer videoCapturer = null;
        if (videoCapturer != null) {
          return videoCapturer;
        }
      }
    }

    // Front facing camera not found, try something else
    Logging.d(TAG, "Looking for other cameras.");
    for (String deviceName : deviceNames) {
      if (!enumerator.isFrontFacing(deviceName)) {
        Logging.d(TAG, "Creating other camera capturer.");
        VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);

        if (videoCapturer != null) {
          return videoCapturer;
        }
      }
    }

    return null;
  }

  @TargetApi(21)
  private @Nullable
  VideoCapturer createScreenCapturer() {
    if (mediaProjectionPermissionResultCode != Activity.RESULT_OK) {
      reportError("User didn't give permission to capture the screen.");
      return null;
    }
    return new ScreenCapturerAndroid(
            mediaProjectionPermissionResultData, new MediaProjection.Callback() {
      @Override
      public void onStop() {
        reportError("User revoked permission to capture the screen.");
      }
    });
  }

  @Override
  public void onStop() {
    super.onStop();
    activityRunning = false;
    // Don't stop the video when using screencapture to allow user to show other apps to the remote
    // end.
    if (peerConnectionClient != null && !screencaptureEnabled) {
      peerConnectionClient.stopVideoSource();
    }
    if (cpuMonitor != null) {
      cpuMonitor.pause();
    }
  }

  @Override
  public void onStart() {
    super.onStart();
    activityRunning = true;
    // Video is not paused for screencapture. See onPause.
    if (peerConnectionClient != null && !screencaptureEnabled) {
      peerConnectionClient.startVideoSource();
    }
    if (cpuMonitor != null) {
      cpuMonitor.resume();
    }
  }

  @Override
  protected void onDestroy() {
    Thread.setDefaultUncaughtExceptionHandler(null);
    disconnect();
    if (logToast != null) {
      logToast.cancel();
    }
    activityRunning = false;
    mKeepCameraOpen = false;
//    unregisterReceiver(receiveBroadCast);
    super.onDestroy();
  }

  @Override
  protected void onResume() {
    mKeepCameraOpen = true;
    super.onResume();
  }

  @Override
  protected void onPause() {
    mKeepCameraOpen = false;
    super.onPause();
  }

  // CallFragment.OnCallEvents interface implementation.
  @Override
  public void onBtnSwitchMode() {
//    Log.d(TAG, "onBtnSwitchMode: " + mode);
    String message = "switchMode";
    if (TextUtils.isEmpty(message)) {
      return;
    }
    String text = "我: " + message;
    tvContext.append(text + "\n");
    scrollContext.fullScroll(View.FOCUS_DOWN);
    sendDataChannelMessage(message);
    etInput.setText("");
    if (mode == modePicture) {
      recordFlag = false;
      refreshRecordButtonState();
      btnRecordVideo.setVisibility(View.VISIBLE);
      btnTakepicture.setVisibility(View.GONE);
//      btnAutoTakepicture.setVisibility(View.GONE);
    } else if (mode == modeVideo) {
      btnRecordVideo.setVisibility(View.GONE);
      btnTakepicture.setVisibility(View.VISIBLE);
//      btnAutoTakepicture.setVisibility(View.VISIBLE);
    }
  }

  @Override
  public void onCallHangUp() {
    disconnect();
  }

  @Override
  public void onCameraSwitch() {
    if (peerConnectionClient != null) {
      peerConnectionClient.switchCamera();
    }
  }

  @Override
  public void onVideoScalingSwitch(ScalingType scalingType) {
    fullscreenRenderer.setScalingType(scalingType);
  }

  @Override
  public void onCaptureFormatChange(int width, int height, int framerate) {
    if (peerConnectionClient != null) {
      peerConnectionClient.changeCaptureFormat(width, height, framerate);
    }
  }

  @Override
  public boolean onToggleMic() {
    if (peerConnectionClient != null) {
      micEnabled = !micEnabled;
      peerConnectionClient.setAudioEnabled(micEnabled);
    }
    return micEnabled;
  }

  // Helper functions.
  private void toggleCallControlFragmentVisibility() {
    if (!connected || !callFragment.isAdded()) {
      return;
    }
    // Show/hide call control fragment
    callControlFragmentVisible = !callControlFragmentVisible;
    FragmentTransaction ft = getFragmentManager().beginTransaction();
    if (callControlFragmentVisible) {
      ft.show(callFragment);
      ft.show(hudFragment);
    } else {
      ft.hide(callFragment);
      ft.hide(hudFragment);
    }
    ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
    ft.commit();
  }

  private void startCall() {
    if (appRtcClient == null) {
      Log.e(TAG, "AppRTC client is not allocated for a call.");
      return;
    }
    callStartedTimeMs = System.currentTimeMillis();

    // Start room connection.
    logAndToast(getString(R.string.connecting_to, roomConnectionParameters.roomUrl));
    appRtcClient.connectToRoom(roomConnectionParameters);

    // Create and audio manager that will take care of audio routing,
    // audio modes, audio device enumeration etc.
    audioManager = AppRTCAudioManager.create(getApplicationContext());
    // Store existing audio settings and change audio mode to
    // MODE_IN_COMMUNICATION for best possible VoIP performance.
    Log.d(TAG, "Starting the audio manager...");
    audioManager.start(new AudioManagerEvents() {
      // This method will be called each time the number of available audio
      // devices has changed.
      @Override
      public void onAudioDeviceChanged(
              AudioDevice audioDevice, Set<AudioDevice> availableAudioDevices) {
        onAudioManagerDevicesChanged(audioDevice, availableAudioDevices);
      }
    });
  }

  // Should be called from UI thread
  private void callConnected() {
    final long delta = System.currentTimeMillis() - callStartedTimeMs;
    Log.i(TAG, "Call connected: delay=" + delta + "ms");
    if (peerConnectionClient == null || isError) {
      Log.w(TAG, "Call is connected in closed or error state");
      return;
    }
    // Enable statistics callback.
    peerConnectionClient.enableStatsEvents(true, STAT_CALLBACK_PERIOD);
    setSwappedFeeds(false /* isSwappedFeeds */);
  }

  // This method is called when the audio manager reports audio device change,
  // e.g. from wired headset to speakerphone.
  private void onAudioManagerDevicesChanged(
          final AudioDevice device, final Set<AudioDevice> availableDevices) {
    Log.d(TAG, "onAudioManagerDevicesChanged: " + availableDevices + ", "
            + "selected: " + device);
    // TODO(henrika): add callback handler.
  }

  // Disconnect from remote resources, dispose of local resources, and exit.
  private void disconnect() {
    activityRunning = false;
    remoteProxyRenderer.setTarget(null);
    localProxyVideoSink.setTarget(null);
    if (appRtcClient != null) {
      appRtcClient.disconnectFromRoom();
      appRtcClient = null;
    }
    if (pipRenderer != null) {
      pipRenderer.release();
      pipRenderer = null;
    }
    if (videoFileRenderer != null) {
      videoFileRenderer.release();
      videoFileRenderer = null;
    }
    if (fullscreenRenderer != null) {
      fullscreenRenderer.release();
      fullscreenRenderer = null;
    }
    if (peerConnectionClient != null) {
      peerConnectionClient.close();
      peerConnectionClient = null;
    }
    if (audioManager != null) {
      audioManager.stop();
      audioManager = null;
    }
    if (connected && !isError) {
      setResult(RESULT_OK);
    } else {
      setResult(RESULT_CANCELED);
    }
    //add by ynhuang @ 20200803
    threadFlag = false;
    //flag = false;
    autoThreadFlag = false;
    //add by ynhuang @ 20210915
    android.os.Process.killProcess(android.os.Process.myPid());
    System.exit(0);
    //add by ynhuang end
    finish();
  }

  private void disconnectWithErrorMessage(final String errorMessage) {
    if (commandLineRun || !activityRunning) {
      Log.e(TAG, "Critical error: " + errorMessage);
      disconnect();
    } else {
      new AlertDialog.Builder(this)
              .setTitle(getText(R.string.channel_error_title))
              .setMessage(errorMessage)
              .setCancelable(false)
              .setNeutralButton(R.string.ok,
                      new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int id) {
                          dialog.cancel();
                          disconnect();
                        }
                      })
              .create()
              .show();
    }
  }

  // Log |msg| and Toast about it.
  private void logAndToast(String msg) {
    Log.d(TAG, msg);
    if (logToast != null) {
      logToast.cancel();
    }
    logToast = Toast.makeText(this, msg, Toast.LENGTH_SHORT);
    logToast.show();
  }

  private void reportError(final String description) {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        if (!isError) {
          isError = true;
          disconnectWithErrorMessage(description);
        }
      }
    });
  }

  private @Nullable
  VideoCapturer createVideoCapturer() {
    final VideoCapturer videoCapturer;
    String videoFileAsCamera = getIntent().getStringExtra(EXTRA_VIDEO_FILE_AS_CAMERA);
    if (videoFileAsCamera != null) {
      try {
        videoCapturer = new FileVideoCapturer(videoFileAsCamera);
      } catch (IOException e) {
        reportError("Failed to open video file for emulated camera");
        return null;
      }
    } else if (screencaptureEnabled) {
      return createScreenCapturer();
    } else if (useCamera2()) {
      if (!captureToTexture()) {
        reportError(getString(R.string.camera2_texture_only_error));
        return null;
      }

      Logging.d(TAG, "Creating capturer using camera2 API.");
      videoCapturer = createCameraCapturer(new Camera2Enumerator(this));
    } else {
      Logging.d(TAG, "Creating capturer using camera1 API.");
      videoCapturer = createCameraCapturer(new Camera1Enumerator(captureToTexture()));
    }
    if (videoCapturer == null) {
      reportError("Failed to open camera");
      return null;
    }
    return videoCapturer;
  }

  public void setSwappedFeeds(boolean isSwappedFeeds) {
    Logging.d(TAG, "setSwappedFeeds: " + isSwappedFeeds);
    this.isSwappedFeeds = isSwappedFeeds;
    localProxyVideoSink.setTarget(isSwappedFeeds ? fullscreenRenderer : pipRenderer);
    remoteProxyRenderer.setTarget(isSwappedFeeds ? pipRenderer : fullscreenRenderer);
    fullscreenRenderer.setMirror(isSwappedFeeds);
    pipRenderer.setMirror(isSwappedFeeds);//(!isSwappedFeeds);
  }

  // -----Implementation of AppRTCClient.AppRTCSignalingEvents ---------------
  // All callbacks are invoked from websocket signaling looper thread and
  // are routed to UI thread.
  private void onConnectedToRoomInternal(final SignalingParameters params) {
    final long delta = System.currentTimeMillis() - callStartedTimeMs;

    signalingParameters = params;
    logAndToast("Creating peer connection, delay=" + delta + "ms");
    VideoCapturer videoCapturer = null;
    if (peerConnectionParameters.videoCallEnabled) {
      videoCapturer = createVideoCapturer();
    }
    peerConnectionClient.createPeerConnection(
            localProxyVideoSink, remoteSinks, videoCapturer, signalingParameters);

    if (signalingParameters.initiator) {
      logAndToast("Creating OFFER...");
      // Create offer. Offer SDP will be sent to answering client in
      // PeerConnectionEvents.onLocalDescription event.
      peerConnectionClient.createOffer();
    } else {
      if (params.offerSdp != null) {
        peerConnectionClient.setRemoteDescription(params.offerSdp);
        logAndToast("Creating ANSWER...");
        // Create answer. Answer SDP will be sent to offering client in
        // PeerConnectionEvents.onLocalDescription event.
        peerConnectionClient.createAnswer();
      }
      if (params.iceCandidates != null) {
        // Add remote ICE candidates from room.
        for (IceCandidate iceCandidate : params.iceCandidates) {
          peerConnectionClient.addRemoteIceCandidate(iceCandidate);
        }
      }
    }
  }

  @Override
  public void onConnectedToRoom(final SignalingParameters params) {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        onConnectedToRoomInternal(params);
      }
    });
  }

  @Override
  public void onRemoteDescription(final SessionDescription sdp) {
    final long delta = System.currentTimeMillis() - callStartedTimeMs;
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        if (peerConnectionClient == null) {
          Log.e(TAG, "Received remote SDP for non-initilized peer connection.");
          return;
        }
        logAndToast("Received remote " + sdp.type + ", delay=" + delta + "ms");
        peerConnectionClient.setRemoteDescription(sdp);
        if (!signalingParameters.initiator) {
          logAndToast("Creating ANSWER...");
          // Create answer. Answer SDP will be sent to offering client in
          // PeerConnectionEvents.onLocalDescription event.
          peerConnectionClient.createAnswer();
        }
      }
    });
  }

  @Override
  public void onRemoteIceCandidate(final IceCandidate candidate) {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        if (peerConnectionClient == null) {
          Log.e(TAG, "Received ICE candidate for a non-initialized peer connection.");
          return;
        }
        peerConnectionClient.addRemoteIceCandidate(candidate);
      }
    });
  }

  @Override
  public void onRemoteIceCandidatesRemoved(final IceCandidate[] candidates) {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        if (peerConnectionClient == null) {
          Log.e(TAG, "Received ICE candidate removals for a non-initialized peer connection.");
          return;
        }
        peerConnectionClient.removeRemoteIceCandidates(candidates);
      }
    });
  }

  @Override
  public void onChannelClose() {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        logAndToast("Remote end hung up; dropping PeerConnection");
        disconnect();
      }
    });
  }

  @Override
  public void onChannelError(final String description) {
    reportError(description);
  }

  // -----Implementation of PeerConnectionClient.PeerConnectionEvents.---------
  // Send local peer connection SDP and ICE candidates to remote party.
  // All callbacks are invoked from peer connection client looper thread and
  // are routed to UI thread.
  @Override
  public void onLocalDescription(final SessionDescription sdp) {
    final long delta = System.currentTimeMillis() - callStartedTimeMs;
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        if (appRtcClient != null) {
          logAndToast("Sending " + sdp.type + ", delay=" + delta + "ms");
          if (signalingParameters.initiator) {
            appRtcClient.sendOfferSdp(sdp);
          } else {
            appRtcClient.sendAnswerSdp(sdp);
          }
        }
        if (peerConnectionParameters.videoMaxBitrate > 0) {
          Log.d(TAG, "Set video maximum bitrate: " + peerConnectionParameters.videoMaxBitrate);
          peerConnectionClient.setVideoMaxBitrate(peerConnectionParameters.videoMaxBitrate);
        }
      }
    });
  }

  @Override
  public void onIceCandidate(final IceCandidate candidate) {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        if (appRtcClient != null) {
          appRtcClient.sendLocalIceCandidate(candidate);
        }
      }
    });
  }

  @Override
  public void onIceCandidatesRemoved(final IceCandidate[] candidates) {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        if (appRtcClient != null) {
          appRtcClient.sendLocalIceCandidateRemovals(candidates);
        }
      }
    });
  }

  @Override
  public void onIceConnected() {
    final long delta = System.currentTimeMillis() - callStartedTimeMs;
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        logAndToast("ICE connected, delay=" + delta + "ms");
        System.out.println("ynhuang, intent (PROJECT_ID, PROJECT_DEVICE_MODE, PROJECT_TAKEPICTURE_MODE, PROJECT_GROUND): " +
                uploadProjId + ", " +
                deviceMode + ", " +
                takepictureMode + ", " +
                ground);
        if (appDef == appRecord) {
          TextView recordText = (TextView) findViewById(R.id.record_string);
          recordText.setText("三維取像拍攝中");
          recordText.setVisibility(View.VISIBLE);
        }
        if (appDef == appControl) {
          System.out.println("ynhuang, setting for record device");
//          String message = "setting: " + uploadProjId + ", " + deviceMode + ", " + takepictureMode + ", " + ground;
          String message = "setting: " + uploadProjId + ", " + uploadProjName + ",  " + deviceMode + ", " + takepictureMode + ", " + ground;
          if (TextUtils.isEmpty(message)){
            return;
          }
          String text = "我: " + message;
          tvContext.append(text + "\n");
          scrollContext.fullScroll(View.FOCUS_DOWN);
          sendDataChannelMessage(message);
          etInput.setText("");
        }
      }
    });
  }

  @Override
  public void onIceDisconnected() {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        logAndToast("ICE disconnected");
      }
    });
  }

  @Override
  public void onConnected() {
    final long delta = System.currentTimeMillis() - callStartedTimeMs;
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        logAndToast("DTLS connected, delay=" + delta + "ms");
        connected = true;
        callConnected();
      }
    });
  }

  @Override
  public void onDisconnected() {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        logAndToast("DTLS disconnected");
        connected = false;
        disconnect();
      }
    });
  }

  @Override
  public void onPeerConnectionClosed() {
  }

  @Override
  public void onPeerConnectionStatsReady(final StatsReport[] reports) {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        if (!isError && connected) {
          hudFragment.updateEncoderStatistics(reports);
        }
      }
    });
  }

  @Override
  public void onPeerConnectionError(final String description) {
    reportError(description);
  }

//  //add by ynhuang @ 20210128 broadcast
//  public class ReceiveBroadCast extends BroadcastReceiver {
//    @Override
//    public void onReceive(Context context, Intent intent) {
//      System.out.println("ynhuang, get gps: " + intent.getStringExtra("gps"));
//      System.out.println("ynhuang, get cameraCtrl: " + intent.getStringExtra("cameraCtrl"));
//      String receiveStr = "";
//
//      if (intent.getStringExtra("gps") != null) {
//        receiveStr = intent.getStringExtra("gps");
////        mavlinkMsg.setText("GPS: " + receiveStr);
//        String[] receiveSplit = receiveStr.split(",");
//        setGps(Double.valueOf(receiveSplit[0]), Double.valueOf(receiveSplit[1]), Double.valueOf(receiveSplit[2]));
////        for(int i=0; i<receiveSplit.length; i++){
////          System.out.println("ynhuang, (i, item): " + i + "," + receiveSplit[i]);
////        }
//      }
////      if(takepictureMode == TAKEPICTURE_AUTO) {
//      if (intent.getStringExtra("cameraCtrl") != null) {
//        receiveStr = intent.getStringExtra("cameraCtrl");
//        mavlinkDigiCamMsg.setText("digiCam: " + receiveStr);
////        takePicture();
//        flag = true;
//        System.out.println("ynhuang, autoTakepictureThread: " + autoTakepictureThread);
//        if (autoTakepictureThread == null) {
//          autoTakepictureThread = new AutoTakePictureThread();
//          autoTakepictureThread.start();
//          //AutoTakePictureThread thread = new AutoTakePictureThread();
//          //thread.start();
//        }
//      }
////      }
//
////      if(intent.getStringExtra("cameraFeedback") != null){
////        receiveStr = intent.getStringExtra("cameraFeedback");
////        mavlinkCameraFeedbackMsg.setText("feedback: " + receiveStr);
////      }
//    }
//
//  }

  public void setGps(double lat, double lon, double alt) {
//    System.out.println("ynhuang, setGps(lat, lon, alt): " + lat + ", " + lon + ", " + alt);
    if (deviceMode == DEVICE_DRONE) {
      mLat = lat / 10000000L;
      mLon = lon / 10000000L;
      mAlt = (alt / 1000) + ground;
    } else if (deviceMode == DEVICE_PHONE) {
      mLat = lat;
      mLon = lon;
      mAlt = alt;
    }
  }

  private void getLocation(Location loc){
    if(loc != null) {
      setGps(loc.getLatitude(), loc.getLongitude(),loc.getAltitude());
    }
    else {
      Toast.makeText(this, "無法定位座標", Toast.LENGTH_LONG).show();
    }
  }
  private void getLocation() {
//    System.out.println("ynhuang, getLocation");
    try {
      if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
        // TODO: Consider calling
        //    ActivityCompat#requestPermissions
        // here to request the missing permissions, and then overriding
        //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
        //                                          int[] grantResults)
        // to handle the case where the user grants the permission. See the documentation
        // for ActivityCompat#requestPermissions for more details.
//        System.out.println("ynhuang, getLocation return");
        return;
      }
      gps_loc = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
      network_loc = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
    } catch (Exception e) {
      e.printStackTrace();
    }
//    System.out.println("ynhuang, getLocation gps_loc: " + gps_loc);
//    System.out.println("ynhuang, getLocation network_loc: " + network_loc);
    if (gps_loc != null) {
      final_loc = gps_loc;
      latitude = final_loc.getLatitude();
      longitude = final_loc.getLongitude();
      altitude = final_loc.getAltitude();
//      System.out.println("ynhuang, getLocation gps_loc(lat, lon, alt): " + latitude + ", " + longitude + ", " + altitude);
    } else if (network_loc != null) {
      final_loc = network_loc;
      latitude = final_loc.getLatitude();
      longitude = final_loc.getLongitude();
      altitude = final_loc.getAltitude();
//      System.out.println("ynhuang, getLocation network_loc(lat, lon, alt): " + latitude + ", " + longitude + ", " + altitude);
    } else {
      latitude = 0.0;
      longitude = 0.0;
      altitude = 0.0;
    }
//    System.out.println("ynhuang, getLocation(lat, lon): " + latitude + ", " + longitude + ", " + altitude);
//    gpsData.setText("lat: " + latitude + ", lon: " + longitude + ", alt: " + altitude + "update time: ");
    setGps(latitude, longitude,altitude);
  }
}
