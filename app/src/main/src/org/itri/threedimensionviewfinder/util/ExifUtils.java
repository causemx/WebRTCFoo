package org.itri.threedimensionviewfinder.util;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.text.TextUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

import org.itri.threedimensionviewfinder.util.ExifInterface;

public class ExifUtils {
    public static void setImgMake(File imgFile) throws IOException {
        if(imgFile.exists()) {
            ExifInterface mExif = new ExifInterface(imgFile);
            mExif.setAttribute(ExifInterface.TAG_MAKE, "DJI");
            mExif.saveAttributes();
//            System.out.println("ynhuang, ExifUtils: setImgMake");
        }
    }
    public static void setImgGPS(File imgFile, double lat, double lon, double alt) throws IOException {
        if(imgFile.exists()) {
            ExifInterface mExif = new ExifInterface(imgFile);
            mExif.setLatLong(lat, lon);
            mExif.setAltitude(alt);
//            mExif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, lat);
//            mExif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, lon);
//            mExif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE, alt);
            mExif.saveAttributes();
//            System.out.println("ynhuang, ExifUtils: setImgGPS");
        }
    }
    public static void setImgMakerNotes(File imgFile, double yaw, double pitch, double roll) throws IOException {
        if(imgFile.exists()){
//            System.out.println("ynhuang, yaw, pitch, roll: " + yaw + ",  "+ pitch + ", " + roll);
            ExifInterface mExif = new ExifInterface(imgFile);
            mExif.setAttribute(ExifInterface.TAG_MAKE, "DJI");

//            long pitch = (long) -90;
//            long yaw = (long) 128;
//            long roll = (long) 0;
            //mExif.setGpsInfo();

            byte[] numEntry = ByteBuffer.allocate(2).order(ByteOrder.BIG_ENDIAN).putShort((short) 3).array();
            byte[] cameraYawID = ByteBuffer.allocate(2).order(ByteOrder.BIG_ENDIAN).putShort((short) 10).array();
            byte[] cameraYawFormat = ByteBuffer.allocate(2).order(ByteOrder.BIG_ENDIAN).putShort((short) 9).array();
            byte[] cameraYawNumComponet = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(1).array();
            byte[] cameraYaw = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt((int)yaw).array();
            byte[] cameraPitchID = ByteBuffer.allocate(2).order(ByteOrder.BIG_ENDIAN).putShort((short) 9).array();
            byte[] cameraPitchFormat = ByteBuffer.allocate(2).order(ByteOrder.BIG_ENDIAN).putShort((short) 9).array();
            byte[] cameraPitchNumComponet = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(1).array();
            byte[] cameraPicth = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt((int)pitch).array();
            byte[] cameraRollID = ByteBuffer.allocate(2).order(ByteOrder.BIG_ENDIAN).putShort((short) 11).array();
            byte[] cameraRollFormat = ByteBuffer.allocate(2).order(ByteOrder.BIG_ENDIAN).putShort((short) 9).array();
            byte[] cameraRollNumComponet = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(1).array();
            byte[] cameraRoll = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt((int)roll).array();
            byte[] end = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(0).array();

//            System.out.println("ynhuang, bytes2int cameraYaw: " + bytes2int(cameraYaw));
//            System.out.println("ynhuang, bytes2int cameraPitch: " + bytes2int(cameraPicth));
//            System.out.println("ynhuang, bytes2int cameraRoll: " + bytes2int(cameraRoll));
//            System.out.println("ynhuang, string2bytes2int cameraYaw:" + bytes2int((new String(cameraYaw, StandardCharsets.ISO_8859_1)).getBytes(StandardCharsets.ISO_8859_1)));
//            System.out.println("ynhuang, string2bytes2int cameraPitch:" + bytes2int((new String(cameraPicth, StandardCharsets.ISO_8859_1)).getBytes(StandardCharsets.ISO_8859_1)));
//            System.out.println("ynhuang, string2bytes2int cameraRoll:" + bytes2int((new String(cameraRoll, StandardCharsets.ISO_8859_1)).getBytes(StandardCharsets.ISO_8859_1)));

            StringBuilder sb = new StringBuilder();
            sb
                    .append(new String(numEntry))
                    .append(new String(cameraPitchID)).append(new String(cameraPitchFormat)).append(new String(cameraPitchNumComponet)).append(new String(cameraPicth, StandardCharsets.ISO_8859_1))
                    .append(new String(cameraYawID)).append(new String(cameraYawFormat)).append(new String(cameraYawNumComponet)).append(new String(cameraYaw, StandardCharsets.ISO_8859_1))
                    .append(new String(cameraRollID)).append(new String(cameraRollFormat)).append(new String(cameraRollNumComponet)).append(new String(cameraRoll, StandardCharsets.ISO_8859_1))
                    .append(new String(end));

//            System.out.println("ynhuang, sb: " + sb);
            mExif.setAttribute(ExifInterface.TAG_MAKER_NOTE, sb.toString());
            mExif.saveAttributes();

        }
    }

    public static File getFileFromContentUri(Uri contentUri, Context context) {
        if (contentUri == null) {
            return null;
        }
        File file = null;
        String filePath;
        String fileName;
        String[] filePathColumn = {MediaStore.MediaColumns.DATA, MediaStore.MediaColumns.DISPLAY_NAME};
        ContentResolver contentResolver = context.getContentResolver();
        Cursor cursor = contentResolver.query(contentUri, filePathColumn, null,
                null, null);
        if (cursor != null) {
            cursor.moveToFirst();
            filePath = cursor.getString(cursor.getColumnIndex(filePathColumn[0]));
            fileName = cursor.getString(cursor.getColumnIndex(filePathColumn[1]));
            cursor.close();
            if (!TextUtils.isEmpty(filePath)) {
                file = new File(filePath);
            }
            if (!file.exists() || file.length() <= 0 || TextUtils.isEmpty(filePath)) {
                filePath = getPathFromInputStreamUri(context, contentUri, fileName);
            }
            if (!TextUtils.isEmpty(filePath)) {
                file = new File(filePath);
            }
        }
        return file;
    }
    public static String getPathFromInputStreamUri(Context context, Uri uri, String fileName) {
        InputStream inputStream = null;
        String filePath = null;

        if (uri.getAuthority() != null) {
            try {
                inputStream = context.getContentResolver().openInputStream(uri);
                File file = createTemporalFileFrom(context, inputStream, fileName);
                filePath = file.getPath();

            } catch (Exception e) {
                System.out.println(e);
            } finally {
                try {
                    if (inputStream != null) {
                        inputStream.close();
                    }
                } catch (Exception e) {
                    System.out.println(e);
                }
            }
        }

        return filePath;
    }
    private static File createTemporalFileFrom(Context context, InputStream inputStream, String fileName)
            throws IOException {
        File targetFile = null;

        if (inputStream != null) {
            int read;
            byte[] buffer = new byte[8 * 1024];
            //自己定義拷貝檔案路徑
            targetFile = new File(context.getCacheDir(), fileName);
            if (targetFile.exists()) {
                targetFile.delete();
            }
            OutputStream outputStream = new FileOutputStream(targetFile);

            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
            outputStream.flush();

            try {
                outputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return targetFile;
    }

    public static int bytes2int(byte[] b) {
        byte[] byteArray = b;
        int num = ByteBuffer.wrap(byteArray).getInt();
        return num;
    }
}
