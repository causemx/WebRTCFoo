package com.example.imufinder;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

public class ImufinderActivity extends AppCompatActivity{
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_imu);
        if (null == savedInstanceState) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.container, ScrollingFragment.newInstance())
                    .commit();
        }
    }
}