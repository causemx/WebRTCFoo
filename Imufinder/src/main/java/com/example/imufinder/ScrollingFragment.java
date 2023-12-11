package com.example.imufinder;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import androidx.fragment.app.Fragment;

import static android.content.Context.SENSOR_SERVICE;

public class ScrollingFragment extends Fragment implements SensorEventListener {
    private SensorManager mSensorManager;
    Sensor accelerometer;
    Sensor magnetometer;

    public static ScrollingFragment newInstance() {
        return new ScrollingFragment();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_scrolling, container, false);
    }
//    TextView textView, magneticTextView, accetTextView;
    @Override
    public void onViewCreated(final View view, Bundle savedInstanceState) {
        mSensorManager = (SensorManager)getActivity().getSystemService(SENSOR_SERVICE);
        accelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        magnetometer = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
//        textView = (TextView) view.findViewById(R.id.orientationData);
//        textView.setTypeface(null, Typeface.NORMAL);
//        textView.setText("");
//        textView.setTextSize(18);
//        magneticTextView = (TextView) view.findViewById(R.id.magnetometerData);
//        magneticTextView.setTypeface(null, Typeface.NORMAL);
//        magneticTextView.setText("");
//        magneticTextView.setTextSize(18);
//        accetTextView = (TextView) view.findViewById(R.id.accelerometerData);
//        accetTextView.setTypeface(null, Typeface.NORMAL);
//        accetTextView.setText("");
//        accetTextView.setTextSize(18);
    }
    @Override
    public void onResume() {
        super.onResume();
        mSensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
        mSensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_UI);
    }
    @Override
    public void onPause() {
        super.onPause();
        mSensorManager.unregisterListener(this);
    }
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        setContentView(R.layout.activity_scrolling);    // Register the sensor listeners
//        mSensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
//        accelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
//        magnetometer = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
//    }

    public void onAccuracyChanged(Sensor sensor, int accuracy) {  }

    float[] mGravity;
    float[] mGeomagnetic;
    public void onSensorChanged(SensorEvent event) {


        StringBuilder sb = new StringBuilder();


        StringBuilder magneticSb = new StringBuilder();


        StringBuilder acceSb = new StringBuilder();

        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            mGravity = event.values;

//            acceSb
//                    .append("Acceleration force along the x axis: ")
//                    .append(Float.toString(mGravity[0]))
//                    .append(" m/(s^2)")
//                    .append("\n")
//                    .append("Acceleration force along the y axis: ")
//                    .append(Float.toString(mGravity[1]))
//                    .append(" m/(s^2)")
//                    .append("\n")
//                    .append("Acceleration force along the z axis: ")
//                    .append(Float.toString(mGravity[2]))
//                    .append(" m/(s^2)")
//                    .append("\n");
//            accetTextView.append(acceSb.toString());
        }
        if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            mGeomagnetic = event.values;

//            magneticSb
//                    .append("Geomagnetic field strength along the x axis: ")
//                    .append(Float.toString(mGeomagnetic[0]))
//                    .append(" μT")
//                    .append("\n")
//                    .append("Geomagnetic field strength along the y axis: ")
//                    .append(Float.toString(mGeomagnetic[1]))
//                    .append(" μT")
//                    .append("\n")
//                    .append("Geomagnetic field strength along the z axis: ")
//                    .append(Float.toString(mGeomagnetic[2]))
//                    .append(" μT")
//                    .append("\n");
//            magneticTextView.append(magneticSb.toString());
        }
        if (mGravity != null && mGeomagnetic != null) {
            float R[] = new float[9];
            float I[] = new float[9];
            boolean success = SensorManager.getRotationMatrix(R, I, mGravity, mGeomagnetic);
            if (success) {
                //手機直向橫向(SURFACE ROTATION)
                Display display = ((WindowManager)getActivity().getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
                int compensation = display.getRotation() * 90;

                //orientation contains: azimut, pitch and roll
                //values[0]: Azimuth, angle of rotation about the -z axis.
                //values[1]: Pitch, angle of rotation about the x axis.
                //values[2]: Roll, angle of rotation about the y axis.
                float orientation[] = new float[3];
                SensorManager.getOrientation(R, orientation);
                float azimut = orientation[0];
                float pitch = orientation[1];
                float roll = orientation[2];

                float yaw = (float) Math.toDegrees(orientation[0]);
                yaw = yaw + compensation;
                if(yaw >= 360){
                    yaw = yaw - 360;
                }

                float pitch2 = (float) Math.toDegrees(roll);
//                float roll2 = (float) Math.toDegrees(pitch*-1);
                pitch2 = -180-pitch2;//-90-(pitch2+90);

//                sb
//                        //弧度
//                        .append("orientation Azimuth: ")
//                        .append(Float.toString(azimut))
//                        .append("\n")
//                        .append("orientation Pitch: ")
//                        .append(Float.toString(pitch))
//                        .append("\n")
//                        .append("orientation Roll: ")
//                        .append(Float.toString(roll))
//                        .append("\n")
//                        //角度
//                        .append("orientation Azimuth.toDegree: ")
//                        .append(Float.toString((float) Math.toDegrees(azimut)))
//                        .append("\n")
//                        .append("orientation Pitch.toDegree: ")
//                        .append(Float.toString((float) Math.toDegrees(pitch)))
//                        .append("\n")
//                        .append("orientation Roll.toDegree: ")
//                        .append(Float.toString((float) Math.toDegrees(roll)))
//                        .append("\n")
//                        //加上手機直相橫向(SURFACE ROTATION)
//                        .append("orientation mYaw: ")
//                        .append(Float.toString(yaw))
//                        .append("\n")
//                        .append("orientation mPitch:")
//                        .append(Float.toString(pitch2))
//                        .append("\n")
//                        .append("orientation mRoll:")
//                        .append(Float.toString((float) Math.toDegrees(pitch*-1)))
//                        .append("\n");

//                textView.append(sb.toString());

//                mOnImuListener.onImu(ScrollingFragment.this,
//                        Math.round(yaw),
//                        Math.round(Math.toDegrees(roll*-1)),
//                        Math.round(Math.toDegrees(pitch*-1)));
                mOnImuListener.onImu(ScrollingFragment.this, yaw, pitch2, Math.toDegrees(pitch*-1));
            }
        }
    }
    private OnImuListener mOnImuListener;

    public void setOnImuListener(OnImuListener listener) {
        mOnImuListener = listener;
    }
    public interface OnImuListener {
        void onImu(ScrollingFragment sender, double azimuth, double pitch, double roll);
    }
}
