package com.example.imufinder;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

public class Magnetometer {
    private MotionSensorListener listener;

    public void setListener(MotionSensorListener l) {
        listener = l;
    }

    private SensorManager sensorManager;
    private Sensor sensor;
    private SensorEventListener sensorEventListener;

    Magnetometer(Context context) {
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        sensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        // https://developer.android.com/guide/topics/sensors/sensors_position
        sensorEventListener = new SensorEventListener() {
            @Override
            public void onSensorChanged(final SensorEvent event) {
                if (listener != null) {
                    listener.onTranslation(event.values[0], event.values[1], event.values[2]);
                }
            }

            @Override
            public void onAccuracyChanged(final Sensor sensor, final int accuracy) {

            }
        };
    }

    public void register() {
        sensorManager.registerListener(sensorEventListener, sensor, SensorManager.SENSOR_DELAY_NORMAL);
    }

    public void unregister() {
        sensorManager.unregisterListener(sensorEventListener);
    }
}
