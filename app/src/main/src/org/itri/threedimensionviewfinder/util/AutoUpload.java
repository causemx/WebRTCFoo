package org.itri.threedimensionviewfinder.util;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.NetworkError;
import com.android.volley.NetworkResponse;
import com.android.volley.NoConnectionError;
import com.android.volley.ParseError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.ServerError;
import com.android.volley.TimeoutError;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

public class AutoUpload {
    private String serveIP_test = "60.250.195.13";
    private String serveIP = "211.20.101.178";
    private RequestQueue requestQueue;
    //重傳
    private int DEFAULT_TIMEOUT_MS = 10000;//超過10秒
    private int DEFAULT_MAX_RETRIES = 1;//重傳1次
    LogUtils logUtils;
    private String mServerpass = "123456";
    private Context mContext;

    public AutoUpload(Context context){//}, String serverpass) {
        mContext = context;
        requestQueue = Volley.newRequestQueue(context);
        logUtils = new LogUtils(context);
       // mServerpass = serverpass;
    }

    public void uploadImage(File filepath, String filename, String serverpass, String projId) throws JSONException, IOException {
//        System.out.println("ynhuang, uploadImage(serverpass, projId): " + serverpass + ", " + projId);
        File imgFile = filepath;
        //new File( Environment.getExternalStoragePublicDirectory( Environment.DIRECTORY_DOWNLOADS ), jpgName + ".jpg" );
        byte[] imgBytes = Files.readAllBytes(imgFile.toPath());
//        Bitmap bitmap = BitmapFactory.decodeFile(imgFile.getAbsolutePath());
        String url = "http://" + serveIP + "/thbu2/api/server/imgupload_one.php";
        JSONObject request = new JSONObject();
        request.put("func","add-image");
        request.put("proj_id", projId);
        request.put("serverpass", serverpass);

        VolleyMultipartRequest volleyMultipartRequest = new VolleyMultipartRequest(Request.Method.POST, url,
                new Response.Listener<NetworkResponse>() {
                    @Override
                    public void onResponse(NetworkResponse response) {
//                        String res = new String(response.data);
                        JSONObject res = null;
                        try {
                            res = new JSONObject(new String(response.data));
                            String code = res.getString("code");
                            if(code.equals("0")) {
//                          System.out.println("ynhuang, onResponse: " + res);
                            logUtils.appendLog(res);
                            } else if (code.equals("2017")){ //如果serverpass過期(code=2017) add by ynhuang @ 20210928
                                SharedPreferences sharedPreferences = mContext.getSharedPreferences("loginData", Context.MODE_PRIVATE);
                                String acc = sharedPreferences.getString("account","acc未存任何資料");
                                String pwd = sharedPreferences.getString("password","pwd未存任何資料");
                                mUserLogin(acc, pwd, new VolleyCallback() {
                                    @Override
                                    public void onSuccess(JSONObject result) throws JSONException {
                                        sharedPrefStored(acc, pwd, result.get("serverpass").toString());
                                        try {
                                            uploadImage(filepath, filename, result.get("serverpass").toString(), projId);
                                        } catch (IOException e) {
                                            e.printStackTrace();
                                        }
                                    }

                                    @Override
                                    public void onFail(VolleyError volleyError) {

                                    }
                                });
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
//                        System.out.println("ynhuang, error.getMessage(): " +  error.getMessage());
                        logUtils.appendLog(error.getMessage());
                    }
                }) {

            @Override
            protected Map<String, DataPart> getByteData() {
                Map<String, DataPart> params = new HashMap<>();
                String imagename = filename;
                params.put("files[]", new DataPart(imagename + ".jpg", imgBytes/*bitmapToBytearray(bitmap)*/, "image/jpg"));
                return params;
            }

            @Override
            protected Map<String, String> getParams() {
                Map<String, String> params = new HashMap<>();
                params.put("func", "add-image");
                params.put("proj_id", projId);
                params.put("serverpass", serverpass);

                return params;
            }
        };
        volleyMultipartRequest.setRetryPolicy(new DefaultRetryPolicy(
                DEFAULT_TIMEOUT_MS, DEFAULT_MAX_RETRIES, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
        //adding the request to volley
        requestQueue.add(volleyMultipartRequest);
    }

    public void newProj(String serverpass, String projName, VolleyCallback callback) throws JSONException {
//        System.out.println("ynhuang newProj(projName, serverpass): " + projName + ", " + serverpass);

        String url = "http://" + serveIP + "/thbu2/api/MProjectInfo.php";
        JSONObject request = new JSONObject();
        request.put("func","add-project");
        request.put("proj_name", projName);
        request.put("serverpass", serverpass);

        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.POST, url, request,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject jsonObject) {
//                        System.out.println("ynhuang onResponse: " + jsonObject.toString());
                        try {
                            callback.onSuccess(jsonObject);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError volleyError) {
                String message = null;
                if (volleyError instanceof NetworkError) {
                    message = "NetworkError";//"Cannot connect to Internet...Please check your connection!";
                } else if (volleyError instanceof ServerError) {
                    message = "ServerError";
//                    System.out.println("ynhaung, serverError: " + volleyError.networkResponse.statusCode);
                } else if (volleyError instanceof AuthFailureError) {
                    message = "AuthFailureError";
                } else if (volleyError instanceof ParseError) {
                    message = "ParseError";//"Parsing error! Please try again after some time!!";
                } else if (volleyError instanceof NoConnectionError) {
                    message = "NoConnectionError";
                } else if (volleyError instanceof TimeoutError) {
                    message = "TimeoutError";
                }
//                System.out.println("ynhuang, newProj(error): " + message);
            }
        });
        requestQueue.add(jsonObjectRequest);

    }

    public void getProj(String serverpass, VolleyCallback callback) throws JSONException {
//        System.out.println("ynhuang getProj(serverpass): " + serverpass);

        String url = "http://" + serveIP + "/thbu2/api/MProjectInfo.php";
        JSONObject request = new JSONObject();
        request.put("func","get-project");
        request.put("proj_id", "all");
        request.put("serverpass", serverpass);

        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.POST, url, request,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject jsonObject) {
//                        System.out.println("ynhuang onResponse: " + jsonObject.toString());
                        try {
                            callback.onSuccess(jsonObject);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
//                        projData.setText(jsonObject.toString());
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError volleyError) {
//                System.out.println("ynhuang, onErrorResponse: " + volleyError.getMessage());
            }
        });
        requestQueue.add(jsonObjectRequest);
    }
//    public void getImg() throws JSONException {
//        System.out.println("ynhuang getImg");
//
//        String url = "http://" + serveIP + "/thbu2/api/MProjectInfo.php";
//        JSONObject request = new JSONObject();
//        request.put("func","get-images");
//        request.put("proj_id", "4");
//        request.put("serverpass", mServerpass);
//
//        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.POST, url, request,
//                new Response.Listener<JSONObject>() {
//                    @Override
//                    public void onResponse(JSONObject jsonObject) {
//                        System.out.println("ynhuang onResponse: " + jsonObject);
////                            projData.setText(jsonObject.get("data").toString());
//                    }
//                }, new Response.ErrorListener() {
//            @Override
//            public void onErrorResponse(VolleyError volleyError) {
//                System.out.println("ynhuang, onErrorResponse: " + volleyError.getMessage());
//            }
//        });
//        requestQueue.add(jsonObjectRequest);
//    }

    public void mUserLogin(String acc, String pwd, VolleyCallback mCallback) throws Exception {
        System.out.println("ynhuang, mUserLogin");
        String url = "http://" + serveIP + "/thbu2/api/MUserLogin.php";

        String keyAES = "Streaming0123456";
//    String pwd = "123456";//"streaming";

        String encode = new String(AESUtil.encrypt(keyAES, pwd).getBytes(), "UTF-8");
//        System.out.println("ynhuang, encode: " + encode);

        JSONObject request = new JSONObject();
        request.put("func","login");
        request.put("userid", acc);
        request.put("password", encode);
//        request.put("password", "123456");

        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.POST, url, request,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject jsonObject) {
//                        System.out.println("ynhuang onResponse: " + jsonObject);
                        try {
                            mCallback.onSuccess(jsonObject);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
//                            projData.setText(jsonObject.get("data").toString());
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError volleyError) {
                mCallback.onFail(volleyError);
//                System.out.println("ynhuang, onErrorResponse: " + volleyError.getMessage());
            }
        });
        requestQueue.add(jsonObjectRequest);
    }
    private void sharedPrefStored(String acc, String pwd, String serverpass){
//        System.out.println("ynhuang, sharedPrefStord(acc, pwd, token): " + acc + ", " + pwd + ", " + serverpass);
        SharedPreferences sharedPref= mContext.getSharedPreferences("loginData", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString("account", acc);
        editor.putString("password", pwd);
        editor.putString("serverpass", serverpass);

        editor.apply();
    }
}
