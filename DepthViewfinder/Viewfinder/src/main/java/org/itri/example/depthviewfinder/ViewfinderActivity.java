package org.itri.example.depthviewfinder;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;

public class ViewfinderActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_viewfinder);
        if (null == savedInstanceState) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.container_root, DepthViewfinderFragment.newInstance(false))
                    .commit();
        }
    }
}
