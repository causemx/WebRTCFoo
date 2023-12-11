package org.itri.usbterminal;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.method.ScrollingMovementMethod;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.EnumSet;
import java.util.Locale;

import io.dronefleet.mavlink.Mavlink2Message;
import io.dronefleet.mavlink.MavlinkConnection;
import io.dronefleet.mavlink.ardupilotmega.CameraFeedback;
import io.dronefleet.mavlink.common.CommandLong;
import io.dronefleet.mavlink.common.GlobalPositionInt;
import io.dronefleet.mavlink.common.Heartbeat;
import io.dronefleet.mavlink.common.MavCmd;
import io.dronefleet.mavlink.common.MavDataStream;

import io.dronefleet.mavlink.common.MissionItemReached;
import io.dronefleet.mavlink.common.RequestDataStream;
import io.dronefleet.mavlink.common.UtmGlobalPosition;
import io.dronefleet.mavlink.protocol.*;
import io.dronefleet.mavlink.MavlinkMessage;

public class TerminalFragment extends Fragment implements ServiceConnection, SerialListener {
    private enum Connected { False, Pending, True }

    private final BroadcastReceiver broadcastReceiver;
    private int deviceId, portNum, baudRate;
    private UsbSerialPort usbSerialPort;
    private SerialService service;

//    public static TextView receiveText;
//    private TextView sendText;
    private ControlLines controlLines;
    private TextUtil.HexWatcher hexWatcher;

    private Connected connected = Connected.False;
    private boolean initialStart = true;
    private boolean hexEnabled = false;
    private boolean controlLinesEnabled = false;
    private boolean pendingNewline = false;
    private String newline = TextUtil.newline_crlf;
//    int count  = 0;

    //add by ynhuang @ 20210303 broadcast
    private String TAG_ACTION_GPS = "gps";
    private String TAG_ACTION_CAMERACTRL = "cameraCtrl";
    private String TAG_ACTION_CAMERAFEEDBACK = "cameraFeedback";
    private String TAG_ACTION_HEARTBEAT = "heartbeat";
    private String TAG_ACTION_STATUS = "status";

    public OnCallEvents callEvents;

    //add by ynhuang @ 20210303
    private HandlerThread mUsb2MavOutputThread, mUsb2MavInputThread;
    private HandlerThread mMav2UsbOutputThread, mMav2UsbInputThread;
    private Handler mUsb2MavHandler1, mUsb2MavHandler2;
    private Handler mMav2UsbHandler1, mMav2UsbHandler2;
    private PipedOutputStream usb2MavOutput;
    private PipedInputStream usb2MavInput;
    private PipedOutputStream mav2UsbOutput;
    private PipedInputStream mav2UsbInput;
    MavlinkConnection connection = null;

    public static TerminalFragment newInstance() {
        return new TerminalFragment();
    }

    //    TextView textView, magneticTextView, accetTextView;
    @Override
    public void onViewCreated(final View view, Bundle savedInstanceState) {
//        System.out.println("ynhuang, TerminalFragment onViewCreated");
        setHasOptionsMenu(true);

        deviceId = getArguments().getInt("device");
        portNum = getArguments().getInt("port");
        baudRate = getArguments().getInt("baud");

    }
    /**
     * Call control interface for container activity.
     */
    public interface OnCallEvents {
        void onMavlink(String tag, String str);
    }

    public TerminalFragment() {
        System.out.println("ynhuang, TerminalFragment");
        broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if(intent.getAction().equals(Constants.INTENT_ACTION_GRANT_USB)) {
                    Boolean granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
                    connect(granted);
                }
            }
        };
    }

//    /*
//     * Lifecycle
//     */
//    @Override
//    public void onCreate(@Nullable Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        System.out.println("ynhuang, TermianlFragment onCreate");
//        Toast.makeText(getActivity(), "TermianlFragment onCreate", Toast.LENGTH_SHORT).show();
//        setHasOptionsMenu(true);
//        setRetainInstance(true);
//        deviceId = getArguments().getInt("device");
//        portNum = getArguments().getInt("port");
//        baudRate = getArguments().getInt("baud");
//    }

    @Override
    public void onDestroy() {
        if (connected != Connected.False)
            disconnect();
        getActivity().stopService(new Intent(getActivity(), SerialService.class));
//        //add by ynhuang @ 20210311
//        try {
//            usb2MavOutput.close();
//            usb2MavInput.close();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//        //add end
        super.onDestroy();
    }

    @Override
    public void onStart() {
        super.onStart();
        if(service != null)
            service.attach(this);
        else
            getActivity().startService(new Intent(getActivity(), SerialService.class)); // prevents service destroy on unbind from recreated activity caused by orientation change
    }

    @Override
    public void onStop() {
//        if(service != null && !getActivity().isChangingConfigurations())
//            service.detach();
//        //add by ynhuang @ 20210311
//        try {
//            usb2MavOutput.close();
//            usb2MavInput.close();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//        //add end
        super.onStop();
    }

    @SuppressWarnings("deprecation") // onAttach(context) was added with API 23. onAttach(activity) works for all API versions
    @Override
    public void onAttach(@NonNull Activity activity) {
        super.onAttach(activity);
        System.out.println("ynhuang, TermianlFragment onAttach");
        getActivity().bindService(new Intent(getActivity(), SerialService.class), this, Context.BIND_AUTO_CREATE);
        callEvents = (OnCallEvents) activity;
    }

    @Override
    public void onDetach() {
        try { getActivity().unbindService(this); } catch(Exception ignored) {}
        super.onDetach();
    }

    @Override
    public void onResume() {
        super.onResume();
        System.out.println("ynhuang, TermianlFragment onResume");
        getActivity().registerReceiver(broadcastReceiver, new IntentFilter(Constants.INTENT_ACTION_GRANT_USB));
        if(initialStart && service != null) {
            initialStart = false;
            getActivity().runOnUiThread(this::connect);
        }
        if(controlLinesEnabled && controlLines != null && connected == Connected.True)
            controlLines.start();

        //add by ynhuang @ 20210310
//        if(initialStart && service != null && controlLinesEnabled && controlLines != null && connected == Connected.True){
//            receiveText.append("initial pip" + '\n');
            try{
//                receiveText.append("output, input: " + usb2MavOutput + ", " + usb2MavInput + '\n');
                usb2MavOutput = new PipedOutputStream();
                usb2MavInput = new PipedInputStream();
                mav2UsbOutput = new PipedOutputStream();
                mav2UsbInput = new PipedInputStream();

//                mav2UsbOutput.connect((usb2MavInput));
                usb2MavOutput.connect(usb2MavInput);
                mav2UsbOutput.connect(mav2UsbInput);

                mUsb2MavOutputThread = new HandlerThread("usb2MavOut");
                mUsb2MavOutputThread.start();
                mUsb2MavInputThread = new HandlerThread("usb2MavIn");
                mUsb2MavInputThread.start();
                mMav2UsbOutputThread = new HandlerThread("mav2UsbOut");
                mMav2UsbOutputThread.start();
                mMav2UsbInputThread = new HandlerThread("mav2UsbIn");
                mMav2UsbInputThread.start();

                mUsb2MavHandler1 = new Handler(mUsb2MavOutputThread.getLooper());
                mUsb2MavHandler2 = new Handler(mUsb2MavInputThread.getLooper());
                mMav2UsbHandler1 = new Handler(mMav2UsbOutputThread.getLooper());
                mMav2UsbHandler2 = new Handler(mMav2UsbInputThread.getLooper());

            } catch (IOException e) {
                e.printStackTrace();
            }
    }

    @Override
    public void onPause() {
//        getActivity().unregisterReceiver(broadcastReceiver);
//        if(controlLines != null)
//            controlLines.stop();
        super.onPause();
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder binder) {
        service = ((SerialService.SerialBinder) binder).getService();
        service.attach(this);
        if(initialStart && isResumed()) {
            initialStart = false;
            getActivity().runOnUiThread(this::connect);
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        service = null;
    }

    /*
     * UI
     */
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_terminal, container, false);
//        receiveText = view.findViewById(R.id.receive_text);                          // TextView performance decreases with number of spans
//        receiveText.setTextColor(getResources().getColor(R.color.colorRecieveText)); // set as default color to reduce number of spans
//        receiveText.setMovementMethod(ScrollingMovementMethod.getInstance());

//        sendText = view.findViewById(R.id.send_text);
//        hexWatcher = new TextUtil.HexWatcher(sendText);
//        hexWatcher.enable(hexEnabled);
//        sendText.addTextChangedListener(hexWatcher);
//        sendText.setHint(hexEnabled ? "HEX mode" : "");

//        View sendBtn = view.findViewById(R.id.send_btn);
//        sendBtn.setOnClickListener(v -> send(sendText.getText().toString()));

        controlLines = new ControlLines(view);

        return view;
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_terminal, menu);
        menu.findItem(R.id.hex).setChecked(hexEnabled);
        menu.findItem(R.id.controlLines).setChecked(controlLinesEnabled);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.clear) {
//            receiveText.setText("");
            return true;
        } else if (id == R.id.newline) {
            String[] newlineNames = getResources().getStringArray(R.array.newline_names);
            String[] newlineValues = getResources().getStringArray(R.array.newline_values);
            int pos = java.util.Arrays.asList(newlineValues).indexOf(newline);
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle("Newline");
            builder.setSingleChoiceItems(newlineNames, pos, (dialog, item1) -> {
                newline = newlineValues[item1];
                dialog.dismiss();
            });
            builder.create().show();
            return true;
        } else if (id == R.id.hex) {
            hexEnabled = !hexEnabled;
//            sendText.setText("");
            hexWatcher.enable(hexEnabled);
//            sendText.setHint(hexEnabled ? "HEX mode" : "");
            item.setChecked(hexEnabled);
            return true;
        } else if (id == R.id.controlLines) {
            controlLinesEnabled = !controlLinesEnabled;
            item.setChecked(controlLinesEnabled);
            if (controlLinesEnabled) {
                controlLines.start();
            } else {
                controlLines.stop();
            }
            return true;
        } else if (id == R.id.sendBreak) {
            try {
                usbSerialPort.setBreak(true);
                Thread.sleep(100);
                status("send BREAK");
                usbSerialPort.setBreak(false);
            } catch (Exception e) {
                status("send BREAK failed: " + e.getMessage());
            }
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    /*
     * Serial + UI
     */
    private void connect() {
        connect(null);
    }

    private void connect(Boolean permissionGranted) {
        UsbDevice device = null;
        UsbManager usbManager = (UsbManager) getActivity().getSystemService(Context.USB_SERVICE);
        for(UsbDevice v : usbManager.getDeviceList().values())
            if(v.getDeviceId() == deviceId)
                device = v;
        if(device == null) {
            status("connection failed: device not found");
            return;
        }
        UsbSerialDriver driver = UsbSerialProber.getDefaultProber().probeDevice(device);
        if(driver == null) {
            driver = CustomProber.getCustomProber().probeDevice(device);
        }
        if(driver == null) {
            status("connection failed: no driver for device");
            return;
        }
        if(driver.getPorts().size() < portNum) {
            status("connection failed: not enough ports at device");
            return;
        }
        usbSerialPort = driver.getPorts().get(portNum);
        UsbDeviceConnection usbConnection = usbManager.openDevice(driver.getDevice());
        if(usbConnection == null && permissionGranted == null && !usbManager.hasPermission(driver.getDevice())) {
            PendingIntent usbPermissionIntent = PendingIntent.getBroadcast(getActivity(), 0, new Intent(Constants.INTENT_ACTION_GRANT_USB), 0);
            usbManager.requestPermission(driver.getDevice(), usbPermissionIntent);
            return;
        }
        if(usbConnection == null) {
            if (!usbManager.hasPermission(driver.getDevice()))
                status("connection failed: permission denied");
            else
                status("connection failed: open failed");
            return;
        }

        connected = Connected.Pending;
        try {
            usbSerialPort.open(usbConnection);
            usbSerialPort.setParameters(baudRate, UsbSerialPort.DATABITS_8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
            SerialSocket socket = new SerialSocket(getActivity().getApplicationContext(), usbConnection, usbSerialPort);
            service.connect(socket);
            // usb connect is not asynchronous. connect-success and connect-error are returned immediately from socket.connect
            // for consistency to bluetooth/bluetooth-LE app use same SerialListener and SerialService classes
            onSerialConnect();
        } catch (Exception e) {
            onSerialConnectError(e);
        }
    }

    private void disconnect() {
        status("disconnect");
        connected = Connected.False;
        controlLines.stop();
        service.disconnect();
        usbSerialPort = null;
    }

    private void send(String str) {
        if(connected != Connected.True) {
            Toast.makeText(getActivity(), "not connected", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            String msg;
            byte[] data;
            if(hexEnabled) {
                StringBuilder sb = new StringBuilder();
                TextUtil.toHexString(sb, TextUtil.fromHexString(str));
                TextUtil.toHexString(sb, newline.getBytes());
                msg = sb.toString();
                data = TextUtil.fromHexString(msg);
            } else {
                msg = str;
                data = (str + newline).getBytes();
            }
            SpannableStringBuilder spn = new SpannableStringBuilder(msg+'\n');
            spn.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.colorSendText)), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
//            receiveText.append(spn);
            service.write(data);
        } catch (Exception e) {
            onSerialIoError(e);
        }
    }

    //add by ynhuang @ 20210312
    class readMav2UsbInputStream extends Thread {
        @Override
        public void run() {
//            try {
                while (true){
                    if(mav2UsbOutput != null && mav2UsbInput != null) {
//                        mMav2UsbHandler1.post(new Runnable() {
//                            @Override
//                            public void run() {
                                try {
                                    byte[] data = new byte[1024];
                                    mav2UsbInput.read(data);
                                    String str = new String(data);
//                                    receiveText.append("data: " + TextUtil.toHexString(data) + '\n');
                                    MavlinkPacketReader packetReader = new MavlinkPacketReader(new ByteArrayInputStream(data));
                                    int msgId = packetReader.next().getMessageId();
//                                    receiveText.append("mav2UsbInput, msgId: " + msgId + '\n');
                                    status("msgID:" + msgId);
                                    service.write(data);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                    }
                }
        }
    }

    int cfCount = 0;
    int digiCount = 0;
    int heartbeatCount = 0;
    //private void receive(byte[] data) throws IOException {
    private void receive() {
        mUsb2MavHandler2.post(new Runnable() {
            @Override
            public void run() {
                try {
                    MavlinkMessage message;
                    while ((message = connection.next()) != null) {
//                        if (message instanceof Mavlink2Message) {
//                            appendLog(">>>Mavlink<<<" + message + '\n');
//                            if(message.getPayload() instanceof MissionItemReached){
//                                MavlinkMessage<MissionItemReached> missionItemReachedMavlinkMessage = (MavlinkMessage<MissionItemReached>) message;
//                                callEvents.onMavlink(TAG_ACTION_CAMERACTRL, "MissionItemReached: " + digiCount);//commandLongMavlinkMessage.getPayload().toString());
//                                digiCount++;
//                            }
                        if (message.getPayload() instanceof Heartbeat) {//0
                            MavlinkMessage<Heartbeat> HeartbeatMavlinkMessage = (MavlinkMessage<Heartbeat>) message;
                            String mavState = String.valueOf(HeartbeatMavlinkMessage.getPayload().systemStatus());
                            //printlog(">>>gps: " + sb.toString());
//                            receiveText.append("HeartbeatMavlinkMessage : " + mavState + System.currentTimeMillis() +'\n');
//                            callEvents.onMavlink(TAG_ACTION_HEARTBEAT, mavState + ", " + System.currentTimeMillis());
                            if(heartbeatCount %10 == 0) {
                                callEvents.onMavlink(TAG_ACTION_HEARTBEAT, "connected");
                                heartbeatCount = 0;
                            }
                            heartbeatCount++;
                        }
                        if (message.getPayload() instanceof CommandLong) {
                                MavlinkMessage<CommandLong> commandLongMavlinkMessage = (MavlinkMessage<CommandLong>) message;
                                if ((commandLongMavlinkMessage.getPayload().command().entry() == MavCmd.MAV_CMD_DO_DIGICAM_CONTROL)
                                        /*&& (commandLongMavlinkMessage.getPayload().param1() == 1.0)*/) { //param1 = 1.0 is DO_DIGICAM_CONTROL, param1 = 0.0 is DO_SET_TRIGG_DIST
                                    callEvents.onMavlink(TAG_ACTION_CAMERACTRL, "MAV_CMD_DO_DIGICAM_CONTROL: " + digiCount);//commandLongMavlinkMessage.getPayload().toString());
//                                    receiveText.append("commandLongMavlinkMessage, : " + TAG_ACTION_CAMERACTRL + ":" + digiCount +'\n');
                                    //printlog(">>>commandlong:" + digiCount + ", " + commandLongMavlinkMessage);
                                    digiCount++;
                                }
                            }
//                            if (message.getPayload() instanceof CameraFeedback) {
//                                MavlinkMessage<CameraFeedback> cameraFeedbackMavlinkMessage = (MavlinkMessage<CameraFeedback>) message;
//                                callEvents.onMavlink(TAG_ACTION_CAMERAFEEDBACK, "cameraFeedback: " + cfCount);
//                                //printlog(">>>feedback: " + cfCount + ", " + cameraFeedbackMavlinkMessage);
//                                cfCount++;
//                            }
                            if (message.getPayload() instanceof GlobalPositionInt) {//33
                                MavlinkMessage<GlobalPositionInt> globalPositionIntMavlinkMessage = (MavlinkMessage<GlobalPositionInt>) message;
                                    String lat = String.valueOf(globalPositionIntMavlinkMessage.getPayload().lat());
                                    String lon = String.valueOf(globalPositionIntMavlinkMessage.getPayload().lon());
//                                    String alt = String.valueOf(globalPositionIntMavlinkMessage.getPayload().alt());
                                    String alt = String.valueOf(globalPositionIntMavlinkMessage.getPayload().relativeAlt());
                                    StringBuilder sb = new StringBuilder();
                                    sb.append(lat).append(",").append(lon).append(",").append(alt);
                                //printlog(">>>gps: " + sb.toString());
                                callEvents.onMavlink(TAG_ACTION_GPS, sb.toString());
                            }
//                            if(message.getPayload() instanceof UtmGlobalPosition){//340
//                                MavlinkMessage<UtmGlobalPosition> utmGlobalPositionMavlinkMessage = (MavlinkMessage<UtmGlobalPosition>) message;
//                                String lat = String.valueOf(utmGlobalPositionMavlinkMessage.getPayload().lat());
//                                String lon = String.valueOf(utmGlobalPositionMavlinkMessage.getPayload().lon());
//                                String alt = String.valueOf(utmGlobalPositionMavlinkMessage.getPayload().relativeAlt());
//                                StringBuilder sb = new StringBuilder();
//                                sb.append(lat).append(",").append(lon).append(",").append(alt);
//                                callEvents.onMavlink(TAG_ACTION_GPS, sb.toString());
//                            }
//                        } else {
                            // This is a Mavlink1 message.
//                            appendLog(">>>Mavlink1<<<" + message + '\n');
//                        }
                    }
                } catch (IOException e) {
//                    appendLog("***Exception***" + e.toString() + '\n');
                    e.printStackTrace();
                }
            }
        });
    }

    void status(String str) {
        SpannableStringBuilder spn = new SpannableStringBuilder(str+'\n');
        spn.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.colorStatusText)), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
//        receiveText.append(spn);
        callEvents.onMavlink(TAG_ACTION_STATUS, spn.toString());
    }

    /*
     * SerialListener
     */
    @Override

    public void onSerialConnect() {
//        receiveText.append("onSerialConnect" + '\n');
        connection = MavlinkConnection.create(usb2MavInput, mav2UsbOutput);//usb2MavOutput);
        readMav2UsbInputStream thread = new readMav2UsbInputStream();
        thread.start();
//        receiveText.append("create connection: " + connection + '\n');
        try {
            connection.send2(
                    1,
                    100,
                    RequestDataStream.builder()
                            .reqStreamId(MavDataStream.MAV_DATA_STREAM_ALL.ordinal())
                            .reqMessageRate(2)
                            .startStop(1)
                            .build());

        } catch(IOException e) {
//            receiveText.append("send error!!!");
//            appendLog("mavlink send exception: " + e);
            e.printStackTrace();
        }



        status("connected");
        connected = Connected.True;
        if(controlLinesEnabled)
            controlLines.start();
    }

    @Override
    public void onSerialConnectError(Exception e) {
        status("connection failed: " + e.getMessage());
        disconnect();
    }

    int count=0; //add by ynhuang @ 20210310
    @Override
    public void onSerialRead(byte[] data)  {
        //add by ynhuang @ 20210310
        if(connection != null) {
            if(usb2MavInput != null && usb2MavOutput != null) {
                mUsb2MavHandler1.post(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            usb2MavOutput.write(data);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                });
                receive();
            }
        }
    }

    @Override
    public void onSerialIoError(Exception e) {
        status("connection lost: " + e.getMessage());
        disconnect();
    }

    class ControlLines {
        private static final int refreshInterval = 200; // msec

        private final Handler mainLooper;
        private final Runnable runnable;
        private final LinearLayout frame;
        private final ToggleButton rtsBtn, ctsBtn, dtrBtn, dsrBtn, cdBtn, riBtn;

        ControlLines(View view) {
            mainLooper = new Handler(Looper.getMainLooper());
            runnable = this::run; // w/o explicit Runnable, a new lambda would be created on each postDelayed, which would not be found again by removeCallbacks

            frame = view.findViewById(R.id.controlLines);
            rtsBtn = view.findViewById(R.id.controlLineRts);
            ctsBtn = view.findViewById(R.id.controlLineCts);
            dtrBtn = view.findViewById(R.id.controlLineDtr);
            dsrBtn = view.findViewById(R.id.controlLineDsr);
            cdBtn = view.findViewById(R.id.controlLineCd);
            riBtn = view.findViewById(R.id.controlLineRi);
            rtsBtn.setOnClickListener(this::toggle);
            dtrBtn.setOnClickListener(this::toggle);
        }

        private void toggle(View v) {
            ToggleButton btn = (ToggleButton) v;
            if (connected != Connected.True) {
                btn.setChecked(!btn.isChecked());
                Toast.makeText(getActivity(), "not connected", Toast.LENGTH_SHORT).show();
                return;
            }
            String ctrl = "";
            try {
                if (btn.equals(rtsBtn)) { ctrl = "RTS"; usbSerialPort.setRTS(btn.isChecked()); }
                if (btn.equals(dtrBtn)) { ctrl = "DTR"; usbSerialPort.setDTR(btn.isChecked()); }
            } catch (IOException e) {
                status("set" + ctrl + " failed: " + e.getMessage());
            }
        }

        private void run() {
            if (connected != Connected.True)
                return;
            try {
                EnumSet<UsbSerialPort.ControlLine> controlLines = usbSerialPort.getControlLines();
                rtsBtn.setChecked(controlLines.contains(UsbSerialPort.ControlLine.RTS));
                ctsBtn.setChecked(controlLines.contains(UsbSerialPort.ControlLine.CTS));
                dtrBtn.setChecked(controlLines.contains(UsbSerialPort.ControlLine.DTR));
                dsrBtn.setChecked(controlLines.contains(UsbSerialPort.ControlLine.DSR));
                cdBtn.setChecked(controlLines.contains(UsbSerialPort.ControlLine.CD));
                riBtn.setChecked(controlLines.contains(UsbSerialPort.ControlLine.RI));
                mainLooper.postDelayed(runnable, refreshInterval);
            } catch (IOException e) {
                status("getControlLines() failed: " + e.getMessage() + " -> stopped control line refresh");
            }
        }

        void start() {
            frame.setVisibility(View.VISIBLE);
            if (connected != Connected.True)
                return;
            try {
                EnumSet<UsbSerialPort.ControlLine> controlLines = usbSerialPort.getSupportedControlLines();
                if (!controlLines.contains(UsbSerialPort.ControlLine.RTS)) rtsBtn.setVisibility(View.INVISIBLE);
                if (!controlLines.contains(UsbSerialPort.ControlLine.CTS)) ctsBtn.setVisibility(View.INVISIBLE);
                if (!controlLines.contains(UsbSerialPort.ControlLine.DTR)) dtrBtn.setVisibility(View.INVISIBLE);
                if (!controlLines.contains(UsbSerialPort.ControlLine.DSR)) dsrBtn.setVisibility(View.INVISIBLE);
                if (!controlLines.contains(UsbSerialPort.ControlLine.CD))   cdBtn.setVisibility(View.INVISIBLE);
                if (!controlLines.contains(UsbSerialPort.ControlLine.RI))   riBtn.setVisibility(View.INVISIBLE);
                run();
            } catch (IOException e) {
                Toast.makeText(getActivity(), "getSupportedControlLines() failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }

        void stop() {
            frame.setVisibility(View.GONE);
            mainLooper.removeCallbacks(runnable);
            rtsBtn.setChecked(false);
            ctsBtn.setChecked(false);
            dtrBtn.setChecked(false);
            dsrBtn.setChecked(false);
            cdBtn.setChecked(false);
            riBtn.setChecked(false);
        }
    }

//    public void appendLog(String text) {
//        File logFile = new File(getActivity().getExternalFilesDir(null), "mavlinkMsg.txt");
////        File logFile = new File("sdcard/log.file");
//        if (!logFile.exists())
//        {
//            try
//            {
//                logFile.createNewFile();
//            }
//            catch (IOException e)
//            {
//                // TODO Auto-generated catch block
//                e.printStackTrace();
//            }
//        }
//        try
//        {
//            //BufferedWriter for performance, true to set append to file flag
//            BufferedWriter buf = new BufferedWriter(new FileWriter(logFile, true));
//            buf.append(text);
//            buf.newLine();
//            buf.close();
//        }
//        catch (IOException e)
//        {
//            // TODO Auto-generated catch block
//            e.printStackTrace();
//        }
//    }

//    public void printlog(String str){
//        receiveText.append(str + '\n');
//    }
}
