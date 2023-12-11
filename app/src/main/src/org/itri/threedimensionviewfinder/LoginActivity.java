package org.itri.threedimensionviewfinder;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.android.volley.AuthFailureError;
import com.android.volley.NetworkError;
import com.android.volley.NoConnectionError;
import com.android.volley.ParseError;
import com.android.volley.RequestQueue;
import com.android.volley.ServerError;
import com.android.volley.TimeoutError;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.Volley;

import org.json.JSONException;
import org.json.JSONObject;

import org.itri.threedimensionviewfinder.util.AutoUpload;
import org.itri.threedimensionviewfinder.util.VolleyCallback;

public class LoginActivity extends AppCompatActivity implements View.OnClickListener {
    Button login;
    EditText account, password;
//    String eckServeIP = "35.206.222.169";
    String serveIP = "211.20.101.178";
    private static RequestQueue requestQueue;
    AutoUpload autoUpload;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.login);

        autoUpload = new AutoUpload(this);

        login = (Button) findViewById(R.id.btn_login);
        account = (EditText) findViewById(R.id.et_account);
        password = (EditText) findViewById(R.id.et_password);
        login.setOnClickListener(this);
        requestQueue = Volley.newRequestQueue(getApplicationContext()); //把context作為參數傳遞進去

        SharedPreferences sharedPreferences = getSharedPreferences("loginData", Context.MODE_PRIVATE);
        String acc = sharedPreferences.getString("account","acc未存任何資料");
        String pwd = sharedPreferences.getString("password","pwd未存任何資料");
        String serverpass = sharedPreferences.getString("serverpass","serverpass未存任何資料");
    }

    @Override
    public void onClick(View v) {
        final int id = v.getId();
        try {
            if(id == login.getId()){
//                System.out.println("ynhuang, click login");
                String acc = account.getText().toString();
                String pwd = password.getText().toString();
                login(acc, pwd);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void login(final String acc, final String pwd) throws Exception {
//        System.out.println("ynhuang login(acc, pwd): " + acc + ", " + pwd);

        autoUpload.mUserLogin(acc, pwd, new VolleyCallback() {
            @Override
            public void onSuccess(JSONObject result) throws JSONException {
//                System.out.println("ynhuang, login res: " + result);
                String code = result.getString("code");
                if(code.equals("0")) {
                    sharedPrefStored(acc, pwd, result.get("serverpass").toString());
                    Intent intent = new Intent();
                    intent.setClass(LoginActivity.this, ConnectActivity.class); //若帳密皆符合先前註冊資料則跳頁
                    startActivity(intent);
                } else if(code.equals("2014")){
                    Toast.makeText(LoginActivity.this, "password not match", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFail(VolleyError volleyError) {
                String message = null;
                if (volleyError instanceof NetworkError) {
                    message = "請確認是否有連上網路";//"Cannot connect to Internet...Please check your connection!";
//                } else if (volleyError instanceof ServerError) {
//                    message = "ServerError";
//                } else if (volleyError instanceof AuthFailureError) {
//                    message = "請確認是否有連上網路";
//                } else if (volleyError instanceof ParseError) {
//                    message = "帳號或密碼錯誤";//"Parsing error! Please try again after some time!!";
//                } else if (volleyError instanceof NoConnectionError) {
//                    message = "請確認是否有連上網路";
//                } else if (volleyError instanceof TimeoutError) {
//                    message = "連線逾時，請確認是否有連上網路";
                }

                AlertDialog.Builder builder = new AlertDialog.Builder(LoginActivity.this);
                builder.setMessage(message);//.setTitle(R.string.dialog_title);
                builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                    }
                });
                AlertDialog dialog = builder.create();
                dialog.show();
            }
        });
    }


    private void sharedPrefStored(String acc, String pwd, String serverpass){
//        System.out.println("ynhuang, sharedPrefStord(acc, pwd, token): " + acc + ", " + pwd + ", " + serverpass);
        SharedPreferences sharedPref= getSharedPreferences("loginData", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString("account", acc);
        editor.putString("password", pwd);
        editor.putString("serverpass", serverpass);

        editor.apply();
    }

}
