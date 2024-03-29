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

import android.app.Activity;
import android.app.Fragment;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import androidx.annotation.RequiresApi;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;
import android.widget.TextView;

import org.webrtc.RendererCommon.ScalingType;

import java.io.File;

/**
 * Fragment for call control.
 */
public class CallFragment extends Fragment {
  private TextView contactView;
//  private ImageButton cameraSwitchButton;
//  private ImageButton videoScalingButton;
//  private ImageButton toggleMuteButton;
//  private TextView captureFormatText;
  private SeekBar captureFormatSlider;
  public OnCallEvents callEvents;
  private ScalingType scalingType;
  private boolean videoCallEnabled = true;
  private final static int CAMERA = 66;
  private String mFilePath;

    /**
   * Call control interface for container activity.
   */
  public interface OnCallEvents {
    void onCallHangUp();
    void onCameraSwitch();
    void onVideoScalingSwitch(ScalingType scalingType);
    void onCaptureFormatChange(int width, int height, int framerate);
    boolean onToggleMic();
    //add by ynhuang
    void onBtnSwitchMode();
    }

  @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
  @Override
  public View onCreateView(
      LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    View controlView = inflater.inflate(R.layout.fragment_call, container, false);

//    StrictMode.VmPolicy.Builder builder = new StrictMode.VmPolicy.Builder();
//    StrictMode.setVmPolicy(builder.build());
//    builder.detectFileUriExposure();


    // Create UI controls.
    contactView = controlView.findViewById(R.id.contact_name_call);
//    ImageButton disconnectButton = controlView.findViewById(R.id.button_call_disconnect);
//    Button modeSwitchButton = controlView.findViewById(R.id.button_switch_mode);
//    if(CallActivity.appDef == CallActivity.appRecord){
//      modeSwitchButton.setVisibility(View.GONE);
//    }
//    cameraSwitchButton = controlView.findViewById(R.id.button_call_switch_camera);
//    videoScalingButton = controlView.findViewById(R.id.button_call_scaling_mode);
//    toggleMuteButton = controlView.findViewById(R.id.button_call_toggle_mic);
//    captureFormatText = controlView.findViewById(R.id.capture_format_text_call);
    captureFormatSlider = controlView.findViewById(R.id.capture_format_slider_call);
    mFilePath = Environment.getExternalStorageDirectory().getPath();// 获取SD卡路径
    mFilePath = mFilePath + File.separator + "webrtcPhotos" + File.separator+"temp.png";// 指定路径

//    // Add buttons click events.
//    disconnectButton.setOnClickListener(new View.OnClickListener() {
//      @Override
//      public void onClick(View view) {
//        callEvents.onCallHangUp();
//      }
//    });

//    cameraSwitchButton.setOnClickListener(new View.OnClickListener() {
//      @Override
//      public void onClick(View view) {
//        callEvents.onCameraSwitch();
//      }
//    });

//    videoScalingButton.setOnClickListener(new View.OnClickListener() {
//      @Override
//      public void onClick(View view) {
//        if (scalingType == ScalingType.SCALE_ASPECT_FILL) {
//          videoScalingButton.setBackgroundResource(R.drawable.ic_action_full_screen);
//          scalingType = ScalingType.SCALE_ASPECT_FIT;
//        } else {
//          videoScalingButton.setBackgroundResource(R.drawable.ic_action_return_from_full_screen);
//          scalingType = ScalingType.SCALE_ASPECT_FILL;
//        }
//        callEvents.onVideoScalingSwitch(scalingType);
//      }
//    });
    scalingType = ScalingType.SCALE_ASPECT_FILL;

//    toggleMuteButton.setOnClickListener(new View.OnClickListener() {
//      @Override
//      public void onClick(View view) {
//        boolean enabled = callEvents.onToggleMic();
//        toggleMuteButton.setAlpha(enabled ? 1.0f : 0.3f);
//      }
//    });

    return controlView;
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode,Intent data)
  {
    if (data != null)
    {
      //System.out.println("ynhuang, take photo");
    }

    super.onActivityResult(requestCode, resultCode, data);
  }

  @Override
  public void onStart() {
    super.onStart();

    boolean captureSliderEnabled = false;
    Bundle args = getArguments();
    if (args != null) {
      String contactName = args.getString(CallActivity.EXTRA_ROOMID);
      contactView.setText(contactName);
      videoCallEnabled = args.getBoolean(CallActivity.EXTRA_VIDEO_CALL, true);
      captureSliderEnabled = videoCallEnabled
          && args.getBoolean(CallActivity.EXTRA_VIDEO_CAPTUREQUALITYSLIDER_ENABLED, false);
    }
    if (!videoCallEnabled) {
//      cameraSwitchButton.setVisibility(View.INVISIBLE);
    }
    if (captureSliderEnabled) {
//      captureFormatSlider.setOnSeekBarChangeListener(
//          new CaptureQualityController(captureFormatText, callEvents));
    } else {
//      captureFormatText.setVisibility(View.GONE);
      captureFormatSlider.setVisibility(View.GONE);
    }
  }

  // TODO(sakal): Replace with onAttach(Context) once we only support API level 23+.
  @SuppressWarnings("deprecation")
  @Override
  public void onAttach(Activity activity) {
    super.onAttach(activity);
    callEvents = (OnCallEvents) activity;
  }
}
