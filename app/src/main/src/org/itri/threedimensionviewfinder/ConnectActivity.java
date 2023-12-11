/*
 *  Copyright 2014 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

package org.itri.threedimensionviewfinder;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.app.LauncherActivity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import androidx.annotation.Nullable;
import android.util.Log;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.EditorInfo;
import android.webkit.URLUtil;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.android.volley.VolleyError;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

import org.itri.threedimensionviewfinder.util.VolleyCallback;
import org.itri.threedimensionviewfinder.util.AutoUpload;
import org.itri.threedimensionviewfinder.jsonObject.ProjectList;
import org.itri.usbterminal.TerminalFragment;
import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;

import org.itri.usbterminal.DevicesFragment;

/**
 * Handles the initial setup where the user selects which room to join.
 */
public class ConnectActivity extends AppCompatActivity implements DevicesFragment.OnUsbDevicesListener {
  private static final String TAG = "ConnectActivity";
  private static final int CONNECTION_REQUEST = 1;
  private static final int PERMISSION_REQUEST = 2;
  private static final int REMOVE_FAVORITE_INDEX = 0;
  private static boolean commandLineRun;

//  private ImageButton addFavoriteButton;
  private EditText roomEditText;
//  private ListView roomListView;
  private SharedPreferences sharedPref;
  private String keyprefResolution;
  private String keyprefFps;
  private String keyprefVideoBitrateType;
  private String keyprefVideoBitrateValue;
  private String keyprefAudioBitrateType;
  private String keyprefAudioBitrateValue;
  private String keyprefRoomServerUrl;
  private String keyprefRoom;
  private String keyprefRoomList;
  private ArrayList<String> roomList;
  private ArrayAdapter<String> adapter;

  private Spinner projectList, usbDeviceList;
  private ArrayList<DevicesFragment.ListItem> usbDeviceListItems_bak;
  private ArrayList<HashMap<String, DevicesFragment.ListItem>>usbDeviceListItems = new ArrayList<HashMap<String, DevicesFragment.ListItem>>();
  private ArrayList<String>usbDeviceListNameItems = new ArrayList<String>();
//  private ArrayAdapter<String> usbDeviceListAapter;
  private ArrayAdapter<DevicesFragment.ListItem> usbDeviceListAapter_bak;
  private ArrayAdapter<String> usbDeviceListAapter;
  private ArrayList<HashMap<String, String>>spinnerItems = new ArrayList<HashMap<String, String>>();
  private ArrayList<String>projNameItems = new ArrayList<String>();
//  private ArrayAdapter<HashMap<String, String>> projListadapter;
  private ArrayAdapter<String> projListadapter;

  //  private ArrayList<ActivityList> spinnerItems = new ArrayList<ActivityList>();
  private AutoUpload autoUpload;
  private Gson mGson = new Gson();
  private CheckBox cbNewProj;
  private EditText etNewProj, etGround;
  private Button btnNewProj;
  private RadioGroup mRadioGroup, mRadioGroup1;
  private RadioButton radioBtnManual, radioBtnAuto, radioBtnPhone, radioBtnDrone;
  private LinearLayout llNewProj, llTakePicMode, ll_device, llGround, ll_project_list;

  //安裝app：如果是取像app appDef = appRecord, 如果是控制app addDef = appControl
  public static int appControl = 1;
  public static int appRecord = 2;
  public static int appDef = BuildConfig.FLAVOR.equalsIgnoreCase("record") ? appRecord : appControl;

  //usb terminal
//  private MainActivity mUsbMainActivity;
  private DevicesFragment mDevicesFragment;
  private int baudRate = 57600;//19200;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_connect);

    //add by ynhuang
    SharedPreferences sharedPreferences = getSharedPreferences("loginData", Context.MODE_PRIVATE);
    String acc = sharedPreferences.getString("account","acc未存任何資料");
    String pwd = sharedPreferences.getString("password","pwd未存任何資料");
    String serverpass = sharedPreferences.getString("serverpass","serverpass未存任何資料");
//    System.out.println("ynhuang, ConnectActivity: sharedPref(acc, pwd, serverpass): " + acc + ", " + pwd + ", " + serverpass);

    autoUpload = new AutoUpload(this);//, serverpass);
    cbNewProj = findViewById(R.id.cb_new_project);
    etNewProj = findViewById(R.id.et_new_project);
    etGround = findViewById(R.id.et_ground);
    btnNewProj = findViewById(R.id.btn_new_project);
    projectList = findViewById(R.id.spinner_project_list);
    usbDeviceList = findViewById(R.id.spinner_usb_terminal);
    mRadioGroup = findViewById(R.id.mRadioGroup);
    radioBtnManual = findViewById(R.id.radio_manual);
    radioBtnAuto = findViewById(R.id.radio_auto);
    mRadioGroup1 = findViewById(R.id.mRadioGroup1);
    radioBtnPhone = findViewById(R.id.radio_phone);
    radioBtnDrone = findViewById(R.id.radio_drone);
    llTakePicMode = findViewById(R.id.ll_takepicture_mode);
    llGround = findViewById(R.id.ll_ground);
    llNewProj = findViewById(R.id.ll_new_project);
    ll_device = findViewById(R.id.ll_device);
    ll_project_list = findViewById(R.id.ll_project_list);

    if(appDef == appControl) {
      usbDeviceList.setVisibility(View.GONE);
      //手持或無人機
      radioBtnDrone.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
          if (isChecked) {
            llTakePicMode.setVisibility(View.VISIBLE);
            llGround.setVisibility(View.VISIBLE);
          } else {
            llTakePicMode.setVisibility(View.GONE);
            llGround.setVisibility(View.GONE);
          }
        }
      });
      //新增專案
      cbNewProj.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
          if (isChecked) {
            llNewProj.setVisibility(View.VISIBLE);
          } else {
            llNewProj.setVisibility(View.GONE);
          }
        }
      });
      btnNewProj.setOnClickListener(new OnClickListener() {
        @Override
        public void onClick(View v) {
          String projName = etNewProj.getText().toString();
          if (projName.matches("")) {
            Toast.makeText(ConnectActivity.this, "請輸入專案名稱", Toast.LENGTH_SHORT).show();
          } else {
            try {
              autoUpload.newProj(serverpass, projName, new VolleyCallback() {
                @Override
                public void onSuccess(JSONObject result) throws JSONException {
//                  System.out.println("ynhuang, ConnectActivity(result): " + result.toString());
                  projListadapter.notifyDataSetChanged();
                }

                @Override
                public void onFail(VolleyError volleyError) {

                }
              });
            } catch (JSONException e) {
              e.printStackTrace();
            }
          }
        }
      });

      //取得專案列表
      try {
        autoUpload.getProj(serverpass, new VolleyCallback() {
          @Override
          public void onSuccess(JSONObject result) throws JSONException {
//          System.out.println("ynhuang, projectList: " + result.toString());
            if (result.getString("code").equals("0")) {
              JSONArray data = result.getJSONArray("data");
              JSONArray sortedData = new JSONArray();
              List<JSONObject> jsonValues = new ArrayList<JSONObject>();
              for (int i = 0; i < data.length(); i++) {
                jsonValues.add(data.getJSONObject(i));
              }
              Collections.sort(jsonValues, new Comparator<JSONObject>() {
                private static final String KEY_NAME = "proj_id";
                @Override
                public int compare(JSONObject a, JSONObject b) {
                  String valA = new String();
                  String valB = new String();
                  int intA = 0;
                  int intB = 0;
                  try {
                    valA = (String) a.get(KEY_NAME);
                    valB = (String) b.get(KEY_NAME);
                    if(valA.length() == 1){
                      valA = "0" + valA;
                    }
                    if(valB.length() == 1){
                      valB = "0" + valB;
                    }
                  }
                  catch (JSONException e) {
                    //do something
                  }
                  return valA.compareTo(valB);
                  //if you want to change the sort order, simply use the following:
                  //return -valA.compareTo(valB);
                }
              });

              for (int i = 0; i < data.length(); i++) {
                sortedData.put(jsonValues.get(i));
              }
              for (int i = sortedData.length()-1; i > 0; i--) {
//              System.out.println("ynhuang, (i, sortedData): " + i + ", " + sortedData.get(i));
                ProjectList list = mGson.fromJson(String.valueOf((JSONObject) sortedData.get(i)), ProjectList.class);
//              System.out.println("ynhuang, (i, projName): " + i + ", " + list.project_name);
                HashMap<String, String> tmp = new HashMap<String, String>();
                tmp.put(list.proj_id, list.project_name);
                spinnerItems.add(tmp);
                projNameItems.add(list.project_name);
              }

//              projListadapter =
//                      new ArrayAdapter<HashMap<String, String>>(ConnectActivity.this, android.R.layout.simple_spinner_item, spinnerItems);
              projListadapter =
                      new ArrayAdapter<String>(ConnectActivity.this, android.R.layout.simple_spinner_item, projNameItems);

              projListadapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
              projectList.setAdapter(projListadapter);
              projectList.setSelection(0, false);
              projectList.setOnItemSelectedListener(spnOnItemSelected);
            }
          }

          @Override
          public void onFail(VolleyError volleyError) {

          }
        });
      } catch (JSONException e) {
        e.printStackTrace();
      }
    } else {
//      System.out.println("ynhuang, ConnectActivity, appDef==record");
      usbDeviceList.setVisibility(View.VISIBLE);
      mDevicesFragment = DevicesFragment.newInstance();
      getSupportFragmentManager().beginTransaction().add(R.id.fragment, mDevicesFragment, "devices").commit();
//      if(usbDeviceListItems != null && usbDeviceListItems.size()>0) {
//        usbDeviceListAapter =
//                new ArrayAdapter<DevicesFragment.ListItem>(ConnectActivity.this, android.R.layout.simple_spinner_item, usbDeviceListItems);
//        usbDeviceListAapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
//        usbDeviceList.setAdapter(usbDeviceListAapter);
//        usbDeviceList.setSelection(0, false);
//        usbDeviceList.setOnItemSelectedListener(spnOnItemSelected);
//      }

      cbNewProj.setVisibility(View.GONE);
      etNewProj.setVisibility(View.GONE);
      etGround.setVisibility(View.GONE);
      btnNewProj.setVisibility(View.GONE);
      projectList.setVisibility(View.GONE);
      mRadioGroup.setVisibility(View.GONE);
      radioBtnManual.setVisibility(View.GONE);
      radioBtnAuto.setVisibility(View.GONE);
      mRadioGroup1.setVisibility(View.GONE);
      radioBtnPhone.setVisibility(View.GONE);
      radioBtnDrone.setVisibility(View.GONE);
      llTakePicMode.setVisibility(View.GONE);
      llGround.setVisibility(View.GONE);
      llNewProj.setVisibility(View.GONE);
      ll_device.setVisibility(View.GONE);
      ll_project_list.setVisibility(View.GONE);
      initUsbDevicesListenter();
    }
    // Get setting keys.
    PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
    sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
    keyprefResolution = getString(R.string.pref_resolution_key);
    keyprefFps = getString(R.string.pref_fps_key);
    keyprefVideoBitrateType = getString(R.string.pref_maxvideobitrate_key);
    keyprefVideoBitrateValue = getString(R.string.pref_maxvideobitratevalue_key);
    keyprefAudioBitrateType = getString(R.string.pref_startaudiobitrate_key);
    keyprefAudioBitrateValue = getString(R.string.pref_startaudiobitratevalue_key);
    keyprefRoomServerUrl = getString(R.string.pref_room_server_url_key);
    keyprefRoom = getString(R.string.pref_room_key);
    keyprefRoomList = getString(R.string.pref_room_list_key);


    roomEditText = findViewById(R.id.room_edittext);
    roomEditText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
      @Override
      public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
        if (i == EditorInfo.IME_ACTION_DONE) {
//          addFavoriteButton.performClick();
          return true;
        }
        return false;
      }
    });
    roomEditText.requestFocus();

//    roomListView = findViewById(R.id.room_listview);
//    roomListView.setEmptyView(findViewById(android.R.id.empty));
//    roomListView.setOnItemClickListener(roomListClickListener);
//    registerForContextMenu(roomListView);
    Button connectButton = findViewById(R.id.connect_button);
    if(appDef == appRecord){
      connectButton.setTextSize(50);
      connectButton.setLayoutParams(new LinearLayout.LayoutParams(700, 500));
    }
//    ImageButton connectButton = findViewById(R.id.connect_button);
    connectButton.setOnClickListener(connectListener);
//    addFavoriteButton = findViewById(R.id.add_favorite_button);
//    addFavoriteButton.setOnClickListener(addFavoriteListener);

    requestPermissions();
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.connect_menu, menu);
    return true;
  }

  @Override
  public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
//    if (v.getId() == R.id.room_listview) {
//      AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
//      menu.setHeaderTitle(roomList.get(info.position));
//      String[] menuItems = getResources().getStringArray(R.array.roomListContextMenu);
//      for (int i = 0; i < menuItems.length; i++) {
//        menu.add(Menu.NONE, i, i, menuItems[i]);
//      }
//    } else {
      super.onCreateContextMenu(menu, v, menuInfo);
//    }
  }

  @Override
  public boolean onContextItemSelected(MenuItem item) {
    if (item.getItemId() == REMOVE_FAVORITE_INDEX) {
      AdapterView.AdapterContextMenuInfo info =
          (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
      roomList.remove(info.position);
      adapter.notifyDataSetChanged();
      return true;
    }

    return super.onContextItemSelected(item);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    // Handle presses on the action bar items.
    if (item.getItemId() == R.id.action_settings) {
      Intent intent = new Intent(this, SettingsActivity.class);
      startActivity(intent);
      return true;
//    } else if (item.getItemId() == R.id.action_loopback) {
//      connectToRoom(null, false, truae, false, 0);
//      return true;
    } else {
      return super.onOptionsItemSelected(item);
    }
  }

  @Override
  public void onPause() {
    super.onPause();
    String room = roomEditText.getText().toString();
    String roomListJson = new JSONArray(roomList).toString();
    SharedPreferences.Editor editor = sharedPref.edit();
    editor.putString(keyprefRoom, room);
    editor.putString(keyprefRoomList, roomListJson);
    editor.commit();
  }

  @Override
  public void onResume() {
    super.onResume();
    String room = sharedPref.getString(keyprefRoom, "");
    roomEditText.setText(room);
    roomList = new ArrayList<>();
    String roomListJson = sharedPref.getString(keyprefRoomList, null);
    if (roomListJson != null) {
      try {
        JSONArray jsonArray = new JSONArray(roomListJson);
        for (int i = 0; i < jsonArray.length(); i++) {
          roomList.add(jsonArray.get(i).toString());
        }
      } catch (JSONException e) {
        Log.e(TAG, "Failed to load room list: " + e.toString());
      }
    }
    adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, roomList);
    initUsbDevicesListenter();
//    roomListView.setAdapter(adapter);
//    if (adapter.getCount() > 0) {
//      roomListView.requestFocus();
//      roomListView.setItemChecked(0, true);
//    }
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (requestCode == CONNECTION_REQUEST && commandLineRun) {
      Log.d(TAG, "Return: " + resultCode);
      setResult(resultCode);
      commandLineRun = false;
      finish();
    }
  }

  @Override
  public void onRequestPermissionsResult(
      int requestCode, String[] permissions, int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    if (requestCode == PERMISSION_REQUEST) {
      String[] missingPermissions = getMissingPermissions();
      if (missingPermissions.length != 0) {
        // User didn't grant all the permissions. Warn that the application might not work
        // correctly.
        new AlertDialog.Builder(this)
                .setMessage(R.string.missing_permissions_try_again)
                .setPositiveButton(R.string.yes,
                        (dialog, id) -> {
                          // User wants to try giving the permissions again.
                          dialog.cancel();
                          requestPermissions();
                        })
                .setNegativeButton(R.string.no,
                        (dialog, id) -> {
                          // User doesn't want to give the permissions.
                          dialog.cancel();
                          onPermissionsGranted();
                        })
                .show();
      } else {
        // All permissions granted.
        onPermissionsGranted();
      }
    }
  }

  private void onPermissionsGranted() {
    // If an implicit VIEW intent is launching the app, go directly to that URL.
    final Intent intent = getIntent();
    if ("android.intent.action.VIEW".equals(intent.getAction()) && !commandLineRun) {
      boolean loopback = intent.getBooleanExtra(CallActivity.EXTRA_LOOPBACK, false);
      int runTimeMs = intent.getIntExtra(CallActivity.EXTRA_RUNTIME, 0);
      boolean useValuesFromIntent =
          intent.getBooleanExtra(CallActivity.EXTRA_USE_VALUES_FROM_INTENT, false);
      String room = sharedPref.getString(keyprefRoom, "");
      connectToRoom(room, true, loopback, useValuesFromIntent, runTimeMs);
    }
  }

  @TargetApi(Build.VERSION_CODES.M)
  private void requestPermissions() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
      // Dynamic permissions are not required before Android M.
      onPermissionsGranted();
      return;
    }

    String[] missingPermissions = getMissingPermissions();
    if (missingPermissions.length != 0) {
      requestPermissions(missingPermissions, PERMISSION_REQUEST);
    } else {
      onPermissionsGranted();
    }
  }

  @TargetApi(Build.VERSION_CODES.M)
  private String[] getMissingPermissions() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
      return new String[0];
    }

    PackageInfo info;
    try {
      info = getPackageManager().getPackageInfo(getPackageName(), PackageManager.GET_PERMISSIONS);
    } catch (PackageManager.NameNotFoundException e) {
      Log.w(TAG, "Failed to retrieve permissions.");
      return new String[0];
    }

    if (info.requestedPermissions == null) {
      Log.w(TAG, "No requested permissions.");
      return new String[0];
    }

    ArrayList<String> missingPermissions = new ArrayList<>();
    for (int i = 0; i < info.requestedPermissions.length; i++) {
      if ((info.requestedPermissionsFlags[i] & PackageInfo.REQUESTED_PERMISSION_GRANTED) == 0) {
        missingPermissions.add(info.requestedPermissions[i]);
      }
    }
    Log.d(TAG, "Missing permissions: " + missingPermissions);

    return missingPermissions.toArray(new String[missingPermissions.size()]);
  }

  /**
   * Get a value from the shared preference or from the intent, if it does not
   * exist the default is used.
   */
  @Nullable
  private String sharedPrefGetString(
      int attributeId, String intentName, int defaultId, boolean useFromIntent) {
    String defaultValue = getString(defaultId);
    if (useFromIntent) {
      String value = getIntent().getStringExtra(intentName);
      if (value != null) {
        return value;
      }
      return defaultValue;
    } else {
      String attributeName = getString(attributeId);
      return sharedPref.getString(attributeName, defaultValue);
    }
  }

  /**
   * Get a value from the shared preference or from the intent, if it does not
   * exist the default is used.
   */
  private boolean sharedPrefGetBoolean(
      int attributeId, String intentName, int defaultId, boolean useFromIntent) {
    boolean defaultValue = Boolean.parseBoolean(getString(defaultId));
    if (useFromIntent) {
      return getIntent().getBooleanExtra(intentName, defaultValue);
    } else {
      String attributeName = getString(attributeId);
      return sharedPref.getBoolean(attributeName, defaultValue);
    }
  }

  /**
   * Get a value from the shared preference or from the intent, if it does not
   * exist the default is used.
   */
  private int sharedPrefGetInteger(
      int attributeId, String intentName, int defaultId, boolean useFromIntent) {
    String defaultString = getString(defaultId);
    int defaultValue = Integer.parseInt(defaultString);
    if (useFromIntent) {
      return getIntent().getIntExtra(intentName, defaultValue);
    } else {
      String attributeName = getString(attributeId);
      String value = sharedPref.getString(attributeName, defaultString);
      try {
        return Integer.parseInt(value);
      } catch (NumberFormatException e) {
        Log.e(TAG, "Wrong setting for: " + attributeName + ":" + value);
        return defaultValue;
      }
    }
  }

  @SuppressWarnings("StringSplitter")
  private void connectToRoom(String roomId, boolean commandLineRun, boolean loopback,
      boolean useValuesFromIntent, int runTimeMs) {
    ConnectActivity.commandLineRun = commandLineRun;

    // roomId is random for loopback.
    if (loopback) {
      roomId = Integer.toString((new Random()).nextInt(100000000));
    }

    String roomUrl = sharedPref.getString(
        keyprefRoomServerUrl, getString(R.string.pref_room_server_url_default));

    // Video call enabled flag.
    boolean videoCallEnabled = sharedPrefGetBoolean(R.string.pref_videocall_key,
        CallActivity.EXTRA_VIDEO_CALL, R.string.pref_videocall_default, useValuesFromIntent);

    // Use screencapture option.
    boolean useScreencapture = sharedPrefGetBoolean(R.string.pref_screencapture_key,
        CallActivity.EXTRA_SCREENCAPTURE, R.string.pref_screencapture_default, useValuesFromIntent);

    // Use Camera2 option.
    boolean useCamera2 = sharedPrefGetBoolean(R.string.pref_camera2_key, CallActivity.EXTRA_CAMERA2,
        R.string.pref_camera2_default, useValuesFromIntent);

    // Get default codecs.
    String videoCodec = sharedPrefGetString(R.string.pref_videocodec_key,
        CallActivity.EXTRA_VIDEOCODEC, R.string.pref_videocodec_default, useValuesFromIntent);
    String audioCodec = sharedPrefGetString(R.string.pref_audiocodec_key,
        CallActivity.EXTRA_AUDIOCODEC, R.string.pref_audiocodec_default, useValuesFromIntent);

    // Check HW codec flag.
    boolean hwCodec = sharedPrefGetBoolean(R.string.pref_hwcodec_key,
        CallActivity.EXTRA_HWCODEC_ENABLED, R.string.pref_hwcodec_default, useValuesFromIntent);

    // Check Capture to texture.
    boolean captureToTexture = sharedPrefGetBoolean(R.string.pref_capturetotexture_key,
        CallActivity.EXTRA_CAPTURETOTEXTURE_ENABLED, R.string.pref_capturetotexture_default,
        useValuesFromIntent);

    // Check FlexFEC.
    boolean flexfecEnabled = sharedPrefGetBoolean(R.string.pref_flexfec_key,
        CallActivity.EXTRA_FLEXFEC_ENABLED, R.string.pref_flexfec_default, useValuesFromIntent);

    // Check Disable Audio Processing flag.
    boolean noAudioProcessing = sharedPrefGetBoolean(R.string.pref_noaudioprocessing_key,
        CallActivity.EXTRA_NOAUDIOPROCESSING_ENABLED, R.string.pref_noaudioprocessing_default,
        useValuesFromIntent);

    boolean aecDump = sharedPrefGetBoolean(R.string.pref_aecdump_key,
        CallActivity.EXTRA_AECDUMP_ENABLED, R.string.pref_aecdump_default, useValuesFromIntent);

    boolean saveInputAudioToFile =
        sharedPrefGetBoolean(R.string.pref_enable_save_input_audio_to_file_key,
            CallActivity.EXTRA_SAVE_INPUT_AUDIO_TO_FILE_ENABLED,
            R.string.pref_enable_save_input_audio_to_file_default, useValuesFromIntent);

    // Check OpenSL ES enabled flag.
    boolean useOpenSLES = sharedPrefGetBoolean(R.string.pref_opensles_key,
        CallActivity.EXTRA_OPENSLES_ENABLED, R.string.pref_opensles_default, useValuesFromIntent);

    // Check Disable built-in AEC flag.
    boolean disableBuiltInAEC = sharedPrefGetBoolean(R.string.pref_disable_built_in_aec_key,
        CallActivity.EXTRA_DISABLE_BUILT_IN_AEC, R.string.pref_disable_built_in_aec_default,
        useValuesFromIntent);

    // Check Disable built-in AGC flag.
    boolean disableBuiltInAGC = sharedPrefGetBoolean(R.string.pref_disable_built_in_agc_key,
        CallActivity.EXTRA_DISABLE_BUILT_IN_AGC, R.string.pref_disable_built_in_agc_default,
        useValuesFromIntent);

    // Check Disable built-in NS flag.
    boolean disableBuiltInNS = sharedPrefGetBoolean(R.string.pref_disable_built_in_ns_key,
        CallActivity.EXTRA_DISABLE_BUILT_IN_NS, R.string.pref_disable_built_in_ns_default,
        useValuesFromIntent);

    // Check Disable gain control
    boolean disableWebRtcAGCAndHPF = sharedPrefGetBoolean(
        R.string.pref_disable_webrtc_agc_and_hpf_key, CallActivity.EXTRA_DISABLE_WEBRTC_AGC_AND_HPF,
        R.string.pref_disable_webrtc_agc_and_hpf_key, useValuesFromIntent);

    // Get video resolution from settings.
    int videoWidth = 0;
    int videoHeight = 0;
    if (useValuesFromIntent) {
      videoWidth = getIntent().getIntExtra(CallActivity.EXTRA_VIDEO_WIDTH, 0);
      videoHeight = getIntent().getIntExtra(CallActivity.EXTRA_VIDEO_HEIGHT, 0);
    }
    if (videoWidth == 0 && videoHeight == 0) {
      String resolution =
          sharedPref.getString(keyprefResolution, getString(R.string.pref_resolution_default));
      String[] dimensions = resolution.split("[ x]+");
      if (dimensions.length == 2) {
        try {
          videoWidth = Integer.parseInt(dimensions[0]);
          videoHeight = Integer.parseInt(dimensions[1]);
        } catch (NumberFormatException e) {
          videoWidth = 0;
          videoHeight = 0;
          Log.e(TAG, "Wrong video resolution setting: " + resolution);
        }
      }
    }

    // Get camera fps from settings.
    int cameraFps = 0;
    if (useValuesFromIntent) {
      cameraFps = getIntent().getIntExtra(CallActivity.EXTRA_VIDEO_FPS, 0);
    }
    if (cameraFps == 0) {
      String fps = sharedPref.getString(keyprefFps, getString(R.string.pref_fps_default));
      String[] fpsValues = fps.split("[ x]+");
      if (fpsValues.length == 2) {
        try {
          cameraFps = Integer.parseInt(fpsValues[0]);
        } catch (NumberFormatException e) {
          cameraFps = 0;
          Log.e(TAG, "Wrong camera fps setting: " + fps);
        }
      }
    }

    // Check capture quality slider flag.
    boolean captureQualitySlider = sharedPrefGetBoolean(R.string.pref_capturequalityslider_key,
        CallActivity.EXTRA_VIDEO_CAPTUREQUALITYSLIDER_ENABLED,
        R.string.pref_capturequalityslider_default, useValuesFromIntent);

    // Get video and audio start bitrate.
    int videoStartBitrate = 0;
    if (useValuesFromIntent) {
      videoStartBitrate = getIntent().getIntExtra(CallActivity.EXTRA_VIDEO_BITRATE, 0);
    }
    if (videoStartBitrate == 0) {
      String bitrateTypeDefault = getString(R.string.pref_maxvideobitrate_default);
      String bitrateType = sharedPref.getString(keyprefVideoBitrateType, bitrateTypeDefault);
      if (!bitrateType.equals(bitrateTypeDefault)) {
        String bitrateValue = sharedPref.getString(
            keyprefVideoBitrateValue, getString(R.string.pref_maxvideobitratevalue_default));
        videoStartBitrate = Integer.parseInt(bitrateValue);
      }
    }

    int audioStartBitrate = 0;
    if (useValuesFromIntent) {
      audioStartBitrate = getIntent().getIntExtra(CallActivity.EXTRA_AUDIO_BITRATE, 0);
    }
    if (audioStartBitrate == 0) {
      String bitrateTypeDefault = getString(R.string.pref_startaudiobitrate_default);
      String bitrateType = sharedPref.getString(keyprefAudioBitrateType, bitrateTypeDefault);
      if (!bitrateType.equals(bitrateTypeDefault)) {
        String bitrateValue = sharedPref.getString(
            keyprefAudioBitrateValue, getString(R.string.pref_startaudiobitratevalue_default));
        audioStartBitrate = Integer.parseInt(bitrateValue);
      }
    }

    // Check statistics display option.
    boolean displayHud = sharedPrefGetBoolean(R.string.pref_displayhud_key,
        CallActivity.EXTRA_DISPLAY_HUD, R.string.pref_displayhud_default, useValuesFromIntent);

    boolean tracing = sharedPrefGetBoolean(R.string.pref_tracing_key, CallActivity.EXTRA_TRACING,
        R.string.pref_tracing_default, useValuesFromIntent);

    // Check Enable RtcEventLog.
    boolean rtcEventLogEnabled = sharedPrefGetBoolean(R.string.pref_enable_rtceventlog_key,
        CallActivity.EXTRA_ENABLE_RTCEVENTLOG, R.string.pref_enable_rtceventlog_default,
        useValuesFromIntent);

    // Get datachannel options
    boolean dataChannelEnabled = sharedPrefGetBoolean(R.string.pref_enable_datachannel_key,
        CallActivity.EXTRA_DATA_CHANNEL_ENABLED, R.string.pref_enable_datachannel_default,
        useValuesFromIntent);
    boolean ordered = sharedPrefGetBoolean(R.string.pref_ordered_key, CallActivity.EXTRA_ORDERED,
        R.string.pref_ordered_default, useValuesFromIntent);
    boolean negotiated = sharedPrefGetBoolean(R.string.pref_negotiated_key,
        CallActivity.EXTRA_NEGOTIATED, R.string.pref_negotiated_default, useValuesFromIntent);
    int maxRetrMs = sharedPrefGetInteger(R.string.pref_max_retransmit_time_ms_key,
        CallActivity.EXTRA_MAX_RETRANSMITS_MS, R.string.pref_max_retransmit_time_ms_default,
        useValuesFromIntent);
    int maxRetr =
        sharedPrefGetInteger(R.string.pref_max_retransmits_key, CallActivity.EXTRA_MAX_RETRANSMITS,
            R.string.pref_max_retransmits_default, useValuesFromIntent);
    int id = sharedPrefGetInteger(R.string.pref_data_id_key, CallActivity.EXTRA_ID,
        R.string.pref_data_id_default, useValuesFromIntent);
    String protocol = sharedPrefGetString(R.string.pref_data_protocol_key,
        CallActivity.EXTRA_PROTOCOL, R.string.pref_data_protocol_default, useValuesFromIntent);

    // Start AppRTCMobile activity.
    Log.d(TAG, "Connecting to room " + roomId + " at URL " + roomUrl);
    if (validateUrl(roomUrl)) {
      Uri uri = Uri.parse(roomUrl);
      Intent intent = new Intent(this, CallActivity.class);
      intent.setData(uri);
      intent.putExtra(CallActivity.EXTRA_ROOMID, roomId);
      intent.putExtra(CallActivity.EXTRA_LOOPBACK, loopback);
      intent.putExtra(CallActivity.EXTRA_VIDEO_CALL, videoCallEnabled);
      intent.putExtra(CallActivity.EXTRA_SCREENCAPTURE, useScreencapture);
      intent.putExtra(CallActivity.EXTRA_CAMERA2, useCamera2);
      intent.putExtra(CallActivity.EXTRA_VIDEO_WIDTH, videoWidth);
      intent.putExtra(CallActivity.EXTRA_VIDEO_HEIGHT, videoHeight);
      intent.putExtra(CallActivity.EXTRA_VIDEO_FPS, cameraFps);
      intent.putExtra(CallActivity.EXTRA_VIDEO_CAPTUREQUALITYSLIDER_ENABLED, captureQualitySlider);
      intent.putExtra(CallActivity.EXTRA_VIDEO_BITRATE, videoStartBitrate);
      intent.putExtra(CallActivity.EXTRA_VIDEOCODEC, videoCodec);
      intent.putExtra(CallActivity.EXTRA_HWCODEC_ENABLED, hwCodec);
      intent.putExtra(CallActivity.EXTRA_CAPTURETOTEXTURE_ENABLED, captureToTexture);
      intent.putExtra(CallActivity.EXTRA_FLEXFEC_ENABLED, flexfecEnabled);
      intent.putExtra(CallActivity.EXTRA_NOAUDIOPROCESSING_ENABLED, noAudioProcessing);
      intent.putExtra(CallActivity.EXTRA_AECDUMP_ENABLED, aecDump);
      intent.putExtra(CallActivity.EXTRA_SAVE_INPUT_AUDIO_TO_FILE_ENABLED, saveInputAudioToFile);
      intent.putExtra(CallActivity.EXTRA_OPENSLES_ENABLED, useOpenSLES);
      intent.putExtra(CallActivity.EXTRA_DISABLE_BUILT_IN_AEC, disableBuiltInAEC);
      intent.putExtra(CallActivity.EXTRA_DISABLE_BUILT_IN_AGC, disableBuiltInAGC);
      intent.putExtra(CallActivity.EXTRA_DISABLE_BUILT_IN_NS, disableBuiltInNS);
      intent.putExtra(CallActivity.EXTRA_DISABLE_WEBRTC_AGC_AND_HPF, disableWebRtcAGCAndHPF);
      intent.putExtra(CallActivity.EXTRA_AUDIO_BITRATE, audioStartBitrate);
      intent.putExtra(CallActivity.EXTRA_AUDIOCODEC, audioCodec);
      intent.putExtra(CallActivity.EXTRA_DISPLAY_HUD, displayHud);
      intent.putExtra(CallActivity.EXTRA_TRACING, tracing);
      intent.putExtra(CallActivity.EXTRA_ENABLE_RTCEVENTLOG, rtcEventLogEnabled);
      intent.putExtra(CallActivity.EXTRA_CMDLINE, commandLineRun);
      intent.putExtra(CallActivity.EXTRA_RUNTIME, runTimeMs);
      intent.putExtra(CallActivity.EXTRA_DATA_CHANNEL_ENABLED, dataChannelEnabled);
      //add by ynhuang @ 20211019
      if(appDef == appRecord && usbDeviceListItems != null && usbDeviceListItems.size() != 0) {
//        System.out.println("ynhuang, usbDeviceListItems: " + usbDeviceListItems);
//        System.out.println("ynhuang, usbDeviceListItems.size: " + usbDeviceListItems.size());
        Bundle args = new Bundle();
//        System.out.println("ynhuang, usb device list getSelectedItemPosition: " + usbDeviceList.getSelectedItemPosition());
//        System.out.println("ynhuang, usb device name: " + usbDeviceListNameItems.get(usbDeviceList.getSelectedItemPosition()));
        String key = usbDeviceListNameItems.get(usbDeviceList.getSelectedItemPosition());
        DevicesFragment.ListItem value = null;
        for (int i = 0; i < usbDeviceListItems.size(); i++) {
//          String key = null;
//          String value = "";
          for (HashMap.Entry<String, DevicesFragment.ListItem> entry: usbDeviceListItems.get(i).entrySet()) {
//            System.out.println("ynhuang, entry.getValue(): " + entry.getValue());
//            System.out.println("ynhuang, value: " + value);
            if (key.equals(entry.getKey())) {
              value = entry.getValue();
              break;
            }
          }
        }
//        DevicesFragment.ListItem item = usbDeviceListItems.get(usbDeviceList.getSelectedItemPosition());
        args.putInt("device", value.device.getDeviceId());
        args.putInt("port", value.port);
        args.putInt("baud", baudRate);
//        Fragment fragment = new TerminalFragment();
//        fragment.setArguments(args);
//        getFragmentManager().beginTransaction().replace(org.itri.usbterminal.R.id.fragment, fragment, "terminal").addToBackStack(null).commit();
        intent.putExtra(CallActivity.USB_DEVICE, args);
      }
      //add by ynhuang @ 20210830
      if(appDef == appControl) {
//        System.out.println("ynhuang, PROJECT_ID: " + projNameItems.get(projectList.getSelectedItemPosition()));
//        ArrayList<HashMap<String, String>> items = new ArrayList<>();
        String key = null;
        String value = projNameItems.get(projectList.getSelectedItemPosition());
        for (int i = 0; i < spinnerItems.size(); i++) {
//          String key = null;
//          String value = "";
          for (HashMap.Entry<String, String> entry: spinnerItems.get(i).entrySet()) {
//            System.out.println("ynhuang, entry.getValue(): " + entry.getValue());
//            System.out.println("ynhuang, value: " + value);
            if (value.equals(entry.getValue())) {
              key = entry.getKey();
              break;
            }
          }
        }
//        System.out.println("ynhuang, PROJECT_ID(key): " + key);
        intent.putExtra(CallActivity.PROJECT_ID, key);
        intent.putExtra(CallActivity.PROJECT_NAME, value);
//        for ( String key : spinnerItems.get(projectList.getSelectedItemPosition()).keySet() ) {
//          intent.putExtra(CallActivity.PROJECT_ID, key);
//        }
        if(radioBtnPhone.isChecked()){
          intent.putExtra(CallActivity.PROJECT_DEVICE_MODE, CallActivity.DEVICE_PHONE);
          intent.putExtra(CallActivity.PROJECT_TAKEPICTURE_MODE, CallActivity.TAKEPICTURE_MANUAL);
          intent.putExtra(CallActivity.PROJECT_GROUND, Double.parseDouble("0.0"));
        } else if(radioBtnDrone.isChecked()){
          intent.putExtra(CallActivity.PROJECT_DEVICE_MODE, CallActivity.DEVICE_DRONE);
          if(radioBtnManual.isChecked()){
            intent.putExtra(CallActivity.PROJECT_TAKEPICTURE_MODE, CallActivity.TAKEPICTURE_MANUAL);
          } else if(radioBtnAuto.isChecked()){
            intent.putExtra(CallActivity.PROJECT_TAKEPICTURE_MODE, CallActivity.TAKEPICTURE_AUTO);
          }
          intent.putExtra(CallActivity.PROJECT_GROUND, Double.parseDouble(etGround.getText().toString()));
        }
      }
      if (dataChannelEnabled) {
        intent.putExtra(CallActivity.EXTRA_ORDERED, ordered);
        intent.putExtra(CallActivity.EXTRA_MAX_RETRANSMITS_MS, maxRetrMs);
        intent.putExtra(CallActivity.EXTRA_MAX_RETRANSMITS, maxRetr);
        intent.putExtra(CallActivity.EXTRA_PROTOCOL, protocol);
        intent.putExtra(CallActivity.EXTRA_NEGOTIATED, negotiated);
        intent.putExtra(CallActivity.EXTRA_ID, id);
      }

      if (useValuesFromIntent) {
        if (getIntent().hasExtra(CallActivity.EXTRA_VIDEO_FILE_AS_CAMERA)) {
          String videoFileAsCamera =
              getIntent().getStringExtra(CallActivity.EXTRA_VIDEO_FILE_AS_CAMERA);
          intent.putExtra(CallActivity.EXTRA_VIDEO_FILE_AS_CAMERA, videoFileAsCamera);
        }

        if (getIntent().hasExtra(CallActivity.EXTRA_SAVE_REMOTE_VIDEO_TO_FILE)) {
          String saveRemoteVideoToFile =
              getIntent().getStringExtra(CallActivity.EXTRA_SAVE_REMOTE_VIDEO_TO_FILE);
          intent.putExtra(CallActivity.EXTRA_SAVE_REMOTE_VIDEO_TO_FILE, saveRemoteVideoToFile);
        }

        if (getIntent().hasExtra(CallActivity.EXTRA_SAVE_REMOTE_VIDEO_TO_FILE_WIDTH)) {
          int videoOutWidth =
              getIntent().getIntExtra(CallActivity.EXTRA_SAVE_REMOTE_VIDEO_TO_FILE_WIDTH, 0);
          intent.putExtra(CallActivity.EXTRA_SAVE_REMOTE_VIDEO_TO_FILE_WIDTH, videoOutWidth);
        }

        if (getIntent().hasExtra(CallActivity.EXTRA_SAVE_REMOTE_VIDEO_TO_FILE_HEIGHT)) {
          int videoOutHeight =
              getIntent().getIntExtra(CallActivity.EXTRA_SAVE_REMOTE_VIDEO_TO_FILE_HEIGHT, 0);
          intent.putExtra(CallActivity.EXTRA_SAVE_REMOTE_VIDEO_TO_FILE_HEIGHT, videoOutHeight);
        }
      }

      startActivityForResult(intent, CONNECTION_REQUEST);
    }
  }

  private boolean validateUrl(String url) {
    if (URLUtil.isHttpsUrl(url) || URLUtil.isHttpUrl(url)) {
      return true;
    }

    new AlertDialog.Builder(this)
        .setTitle(getText(R.string.invalid_url_title))
        .setMessage(getString(R.string.invalid_url_text, url))
        .setCancelable(false)
        .setNeutralButton(R.string.ok,
            new DialogInterface.OnClickListener() {
              @Override
              public void onClick(DialogInterface dialog, int id) {
                dialog.cancel();
              }
            })
        .create()
        .show();
    return false;
  }

  private final AdapterView.OnItemClickListener roomListClickListener =
      new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
          String roomId = ((TextView) view).getText().toString();
          connectToRoom(roomId, false, false, false, 0);
        }
      };

  private final OnClickListener addFavoriteListener = new OnClickListener() {
    @Override
    public void onClick(View view) {
      String newRoom = roomEditText.getText().toString();
      if (newRoom.length() > 0 && !roomList.contains(newRoom)) {
        adapter.add(newRoom);
        adapter.notifyDataSetChanged();
      }
    }
  };

  private final OnClickListener connectListener = new OnClickListener() {
    @Override
    public void onClick(View view) {
      connectToRoom(roomEditText.getText().toString(), false, false, false, 0);
    }
  };

  private AdapterView.OnItemSelectedListener spnOnItemSelected
          = new AdapterView.OnItemSelectedListener() {
    public void onItemSelected(AdapterView<?> parent, View view,
                               int pos, long id) {
      String sPos=String.valueOf(pos);
      String sInfo=parent.getItemAtPosition(pos).toString();
//      System.out.println("ynhuang, (sPos, sInfo): " + sPos + ", " + sInfo);
      //String sInfo=parent.getSelectedItem().toString();
    }
    public void onNothingSelected(AdapterView<?> parent) {
      //
    }
  };

  public void initUsbDevicesListenter() {
//    System.out.println("ynhuang, initUsbDevicesListenter(mDevicesFragment): " + mDevicesFragment);
    if (mDevicesFragment != null) {
      mDevicesFragment.setOnUsbDevicesListener(this);
    }
  }

  @Override
  public void onUsbDevices(DevicesFragment sender, ArrayList<DevicesFragment.ListItem> listItems) {
//    System.out.println("ynhuang, onUsbDevices: (listItems.size):  " + listItems.size() );
//    usbDeviceListItems = listItems;
    for(int i=0; i<listItems.size(); i++){
      HashMap<String, DevicesFragment.ListItem> tmp = new HashMap<String, DevicesFragment.ListItem>();
      tmp.put(listItems.get(i).driver.getClass().getSimpleName().replace("SerialDriver", "") + ", Port " + listItems.get(i).port, listItems.get(i));
      usbDeviceListItems.add(tmp);
      usbDeviceListNameItems.add(listItems.get(i).driver.getClass().getSimpleName().replace("SerialDriver", "") + ", Port " + listItems.get(i).port);
    }
    if(usbDeviceListItems != null && usbDeviceListItems.size()>0) {
//      ArrayList<String> spinnerItems = new ArrayList<String>();
//      for(int i=0; i<usbDeviceListItems.size(); i++){
//        DevicesFragment.ListItem item = usbDeviceListItems.get(i);
//        if(item.driver == null) {
//          spinnerItems.add("<no driver>");
//        } else {
//          spinnerItems.add(item.driver.getClass().getSimpleName().replace("SerialDriver", ""));
////        spinnerItems.add(item.driver.getClass().getSimpleName().replace("SerialDriver", ""));
//        }
//      }
//      usbDeviceListAapter =
//      usbDeviceListAapter =
//              new ArrayAdapter<String>(ConnectActivity.this, android.R.layout.simple_spinner_item, spinnerItems);
//      usbDeviceListAapter = new ArrayAdapter<DevicesFragment.ListItem>(ConnectActivity.this, android.R.layout.simple_spinner_item, usbDeviceListItems);
      usbDeviceListAapter = new ArrayAdapter<String>(ConnectActivity.this, android.R.layout.simple_spinner_item, usbDeviceListNameItems);
      usbDeviceListAapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
      usbDeviceList.setAdapter(usbDeviceListAapter);
      usbDeviceList.setSelection(0, false);
      usbDeviceList.setOnItemSelectedListener(spnOnItemSelected);
    }
  }
}
