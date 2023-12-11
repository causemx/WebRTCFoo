package org.itri.threedimensionviewfinder.util;

import android.content.Context;

import com.android.volley.toolbox.Volley;

import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class LogUtils {
    private Context mContext;
    private String filename;
    public LogUtils(Context context) {
        mContext = context;
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss-SSS");
        filename = formatter.format(new Date(System.currentTimeMillis()));// + "_";
    }

    public void appendLog(String text) {
        File logFile = new File(mContext.getExternalFilesDir(null), filename + ".txt");
        if (!logFile.exists()){
            try {
                logFile.createNewFile();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        try {
            //BufferedWriter for performance, true to set append to file flag
            BufferedWriter buf = new BufferedWriter(new FileWriter(logFile, true));
            buf.append(text);
            buf.newLine();
            buf.close();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public void appendLog(JSONObject obj){//(String text) {
        File logFile = new File(mContext.getExternalFilesDir(null), filename + ".txt");
        if (!logFile.exists()){
            try {
                logFile.createNewFile();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        try {
            //BufferedWriter for performance, true to set append to file flag
            BufferedWriter buf = new BufferedWriter(new FileWriter(logFile, true));
            buf.append(obj.toString());
            buf.newLine();
            buf.close();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
}
