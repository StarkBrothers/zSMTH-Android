package com.zfdang.zsmth_android;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.ActionBar;

import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.franmontiel.persistentcookiejar.ClearableCookieJar;
import com.zfdang.SMTHApplication;
import com.zfdang.zsmth_android.newsmth.AjaxResponse;
import com.zfdang.zsmth_android.newsmth.SMTHHelper;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.annotations.NonNull;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import okhttp3.Cookie;
import okhttp3.Headers;
import okhttp3.HttpUrl;

/**
 * A login screen that offers login to newsmth forum
 */
public class LoginActivity extends SMTHBaseActivity implements OnClickListener {

    private EditText m_userNameEditText;
    private EditText m_passwordEditText;
    private EditText m_cookieEditText;
    private CheckBox mAutoLogin;

    // these 3 parameters are used by webviewlogin only
    static final int LOGIN_ACTIVITY_REQUEST_CODE = 9528;  // The request code
    static final String USERNAME = "USERNAME";
    static final String PASSWORD = "PASSWORD";

    private final String TAG = "LoginActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // these two variables should be loaded from preference
        Settings setting = Settings.getInstance();
        String username = setting.getUsername();
        String password = setting.getPassword();
        String cookie = setting.getCookie();
        boolean autologin = setting.isAutoLogin();

        m_userNameEditText = (EditText) findViewById(R.id.username_edit);
        m_userNameEditText.setText(username);
        m_passwordEditText = (EditText) findViewById(R.id.password_edit);
        m_passwordEditText.setText(password);
        m_cookieEditText = (EditText) findViewById(R.id.cookie_edit);
        m_cookieEditText.setText(cookie);

        mAutoLogin = (CheckBox) findViewById(R.id.auto_login);
        mAutoLogin.setChecked(autologin);

        TextView registerLink = (TextView) findViewById(R.id.register_link);
        registerLink.setMovementMethod(LinkMovementMethod.getInstance());

        TextView asmHelpLink = (TextView) findViewById(R.id.asm_help_link);
        asmHelpLink.setMovementMethod(LinkMovementMethod.getInstance());

        Button ubutton = (Button) findViewById(R.id.signin_button);
        ubutton.setOnClickListener(this);

        // enable back button in the title barT
        ActionBar bar = getSupportActionBar();
        if (bar != null) {
            bar.setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.signin_button) {
            // login with provided username and password
            String username = m_userNameEditText.getText().toString();
            String password = m_passwordEditText.getText().toString();
            String cookie = m_cookieEditText.getText().toString();

            View focusView = null;

            if (!isInputValueValid(cookie)) {
                // Check for inputted username & password
                if (!isInputValueValid(username)) {
                    focusView = m_userNameEditText;
                } else if (!isInputValueValid(password)) {
                    focusView = m_passwordEditText;
                }
            }

            if (focusView != null) {
                // There was an error; don't attempt login and focus the first field with an alert
                focusView.requestFocus();
                Toast.makeText(SMTHApplication.getAppContext(), "请输入用户名/密码！", Toast.LENGTH_SHORT).show();
            } else {
                Settings.getInstance().setAutoLogin(mAutoLogin.isChecked());
                Settings.getInstance().setLastLoginSuccess(false);
                attemptLoginFromWWW(username, password, cookie);
            }
        }
    }

    // login from WWW, then nforum / www / m are all logined
    private void attemptLoginFromWWW(final String username, final String password, final String cookie) {
        // perform the user login attempt.
        showProgress("登录中...");

        Log.d(TAG, "start login now...");
        // use attempt to login, so set userOnline = true
        Settings.getInstance().setUserOnline(true);

        // RxJava & Retrofit: VERY VERY good article
        // http://gank.io/post/560e15be2dca930e00da1083
        SMTHHelper helper = SMTHHelper.getInstance();
        // clear cookies upon login
        helper.mCookieJar.clear();

        // direct set out cookie to avoid login validate
        if (cookie != null) {
            HttpUrl httpUrl = HttpUrl.get(SMTHHelper.SMTH_WWW_URL);
            List<Cookie> cookieList = new ArrayList();
            for (String perCookie : cookie.split(";")) {
                cookieList.add(Cookie.parse(httpUrl, perCookie));
            }
            helper.mCookieJar.saveFromResponse(httpUrl, cookieList);
            Toast.makeText(getApplicationContext(), "登录成功!", Toast.LENGTH_SHORT).show();

            dismissProgress();
            Settings.getInstance().setUsername(username);
            Settings.getInstance().setCookie(cookie);
            Settings.getInstance().setLastLoginSuccess(true);

            Intent resultIntent = new Intent();
            setResult(Activity.RESULT_OK, resultIntent);
            finish();
            return;
        }

        final String cookieDays = "2";
        helper.wService.login(username, password, cookieDays)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<AjaxResponse>() {
                    @Override
                    public void onSubscribe(@NonNull Disposable disposable) {

                    }

                    @Override
                    public void onNext(@NonNull AjaxResponse ajaxResponse) {
                        dismissProgress();
                        // {"ajax_st":0,"ajax_code":"0101","ajax_msg":"您的用户名并不存在，或者您的密码错误"}
                        // {"ajax_st":0,"ajax_code":"0105","ajax_msg":"请勿频繁登录"}
                        // {"ajax_st":1,"ajax_code":"0005","ajax_msg":"操作成功"}
                        Log.d(TAG, ajaxResponse.toString());
                        switch (ajaxResponse.getAjax_st()) {
                            case AjaxResponse.AJAX_RESULT_OK:
                                Toast.makeText(getApplicationContext(), "登录成功!", Toast.LENGTH_SHORT).show();

                                // save username & passworld
                                Settings.getInstance().setUsername(username);
                                Settings.getInstance().setPassword(password);
                                Settings.getInstance().setLastLoginSuccess(true);

                                Intent resultIntent = new Intent();
                                setResult(Activity.RESULT_OK, resultIntent);
                                finish();
                                break;
                            default:
                                Toast.makeText(SMTHApplication.getAppContext(), ajaxResponse.toString(), Toast.LENGTH_LONG).show();
                                break;
                        }
                    }

                    @Override
                    public void onError(@NonNull Throwable e) {
                        dismissProgress();
                        Toast.makeText(SMTHApplication.getAppContext(), "登录失败!\n" + e.toString(), Toast.LENGTH_LONG).show();
                    }

                    @Override
                    public void onComplete() {

                    }
                });
    }

    // check input value
    // not null, with size > 0
    private boolean isInputValueValid(String value) {
        if (value == null) return false;
        return value.length() > 0;
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onStop() {
        super.onStop();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
}

