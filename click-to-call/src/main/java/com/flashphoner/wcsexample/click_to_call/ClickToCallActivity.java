package com.flashphoner.wcsexample.click_to_call;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.flashphoner.fpwcsapi.Flashphoner;
import com.flashphoner.fpwcsapi.bean.Connection;
import com.flashphoner.fpwcsapi.bean.Data;
import com.flashphoner.fpwcsapi.session.Call;
import com.flashphoner.fpwcsapi.session.CallOptions;
import com.flashphoner.fpwcsapi.session.CallStatusEvent;
import com.flashphoner.fpwcsapi.session.Session;
import com.flashphoner.fpwcsapi.session.SessionEvent;
import com.flashphoner.fpwcsapi.session.SessionOptions;

/**
 * Example of a click-to-call button
 * Can be used as a built-in VoIP call button in the mobile application
 */
public class ClickToCallActivity extends AppCompatActivity {

    private static String TAG = ClickToCallActivity.class.getName();

    private static final int CALL_REQUEST_CODE = 100;


    // UI references.
    private EditText mWcsUrlView;
    private EditText mCalleeView;
    private TextView mCallStatus;
    private Button mCallButton;

    /**
     * Associated session with WCS server
     */
    private Session session;

    /**
     * SIP call
     */
    private Call call;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_click_to_call);

        TextView policyTextView = (TextView) findViewById(R.id.privacy_policy);
        policyTextView.setMovementMethod(LinkMovementMethod.getInstance());
        String policyLink ="<a href=https://flashphoner.com/flashphoner-privacy-policy-for-android-tools/>Privacy Policy</a>";
        policyTextView.setText(Html.fromHtml(policyLink));

        /**
         * Initialization of the API.
         */
        Flashphoner.init(this);

        SharedPreferences sharedPref = this.getPreferences(Context.MODE_PRIVATE);

        mWcsUrlView = (EditText) findViewById(R.id.wcs_url);
        mWcsUrlView.setText(sharedPref.getString("wcs_url", getString(R.string.wcs_url)));
        mCalleeView = (EditText) findViewById(R.id.callee);
        mCalleeView.setText(sharedPref.getString("callee", getString(R.string.callee)));

        mCallStatus = (TextView) findViewById(R.id.status);
        mCallButton = (Button) findViewById(R.id.start_button);
        /**
         * Call on click
         */
        mCallButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mCallButton.getTag() == null || Integer.valueOf(R.string.action_call).equals(mCallButton.getTag())) {
                    SessionOptions sessionOptions = new SessionOptions(mWcsUrlView.getText().toString());
                    session = Flashphoner.createSession(sessionOptions);
                    session.on(new SessionEvent() {
                        @Override
                        public void onAppData(Data data) {

                        }

                        /**
                         * Callback functions for connection status events are added to make appropriate changes in controls of the interface when connection is established and closed.
                         */
                        @Override
                        public void onConnected(final Connection connection) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    mCallButton.setText(R.string.action_hangup);
                                    mCallButton.setTag(R.string.action_hangup);
                                    mCallButton.setEnabled(true);
                                    mCallStatus.setText("Connection: " + connection.getStatus());

                                    /**
                                     * Pass 'callee' to the callOptions and create a new call object
                                     */
                                    CallOptions callOptions = new CallOptions(mCalleeView.getText().toString());
                                    call = session.createCall(callOptions);
                                    call.on(new CallStatusEvent() {
                                        /**
                                         * WCS received 100 TRYING from SIP
                                         * @param call
                                         */
                                        @Override
                                        public void onTrying(final Call call) {
                                            runOnUiThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    mCallButton.setText(R.string.action_hangup);
                                                    mCallButton.setTag(R.string.action_hangup);
                                                    mCallButton.setEnabled(true);
                                                    mCallStatus.setText("Call: " + call.getStatus());
                                                }
                                            });
                                        }

                                        /**
                                         * WCS received BUSY_HERE or BUSY_EVERYWHERE from SIP
                                         * @param call
                                         */
                                        @Override
                                        public void onBusy(final Call call) {
                                            runOnUiThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    mCallButton.setText(R.string.action_call);
                                                    mCallButton.setTag(R.string.action_call);
                                                    mCallButton.setEnabled(true);
                                                    mCallStatus.setText("Call: " + call.getStatus());
                                                }
                                            });
                                        }

                                        /**
                                         * Call failed on server
                                         * @param call
                                         */
                                        @Override
                                        public void onFailed(final Call call) {
                                            runOnUiThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    mCallStatus.setText("Call: " + call.getStatus());
                                                }
                                            });
                                        }

                                        /**
                                         * WCS received 180 RINGING from SIP
                                         * @param call
                                         */
                                        @Override
                                        public void onRing(final Call call) {
                                            runOnUiThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    mCallStatus.setText("Call: " + call.getStatus());
                                                }
                                            });
                                        }

                                        /**
                                         * Call is set on hold by the remote participant
                                         * @param call
                                         */
                                        @Override
                                        public void onHold(final Call call) {
                                            runOnUiThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    mCallStatus.setText("Call: " + call.getStatus());
                                                }
                                            });
                                        }

                                        /**
                                         * Call is established. WCS received 200 OK from SIP on INVITE.
                                         * @param call
                                         */
                                        @Override
                                        public void onEstablished(final Call call) {
                                            runOnUiThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    mCallStatus.setText("Call: " + call.getStatus());
                                                }
                                            });
                                        }

                                        /**
                                         * Call is terminated either by caller or by a SIP remote participant.
                                         * @param call
                                         */
                                        @Override
                                        public void onFinished(final Call call) {
                                            runOnUiThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    mCallButton.setText(R.string.action_call);
                                                    mCallButton.setTag(R.string.action_call);
                                                    mCallButton.setEnabled(true);
                                                    mCallStatus.setText("Call: " + call.getStatus());
                                                }
                                            });
                                        }
                                    });

                                    ActivityCompat.requestPermissions(ClickToCallActivity.this,
                                            new String[]{Manifest.permission.RECORD_AUDIO},
                                            CALL_REQUEST_CODE);

                                    SharedPreferences sharedPref = ClickToCallActivity.this.getPreferences(Context.MODE_PRIVATE);
                                    SharedPreferences.Editor editor = sharedPref.edit();
                                    editor.putString("callee", mCalleeView.getText().toString());
                                    editor.apply();
                                }
                            });
                        }

                        /**
                         * Registered on SIP. WCS received 200 OK on REGISTER request.
                         * @param connection Current connection state
                         */
                        @Override
                        public void onRegistered(Connection connection) {

                        }

                        /**
                         * Disconnected
                         * @param connection Current connection state
                         */
                        @Override
                        public void onDisconnection(final Connection connection) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    mCallButton.setText(R.string.action_call);
                                    mCallButton.setTag(R.string.action_call);
                                    mCallButton.setEnabled(true);
                                    mCallStatus.setText("Connection: " + connection.getStatus());
                                }
                            });
                        }
                    });

                    mCallButton.setEnabled(false);
                    Connection connection = new Connection();
                    connection.setAppKey("clickToCallApp");
                    /**
                     * Connect to WCS server
                     */
                    session.connect(connection);

                    SharedPreferences sharedPref = ClickToCallActivity.this.getPreferences(Context.MODE_PRIVATE);
                    SharedPreferences.Editor editor = sharedPref.edit();
                    editor.putString("wcs_url", mWcsUrlView.getText().toString());
                    editor.apply();
                } else {
                    mCallButton.setEnabled(false);
                    session.disconnect();
                }

                View currentFocus = getCurrentFocus();
                if (currentFocus != null) {
                    InputMethodManager inputManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    inputManager.hideSoftInputFromWindow(currentFocus.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
                }
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String permissions[], @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case CALL_REQUEST_CODE: {
                if (grantResults.length == 0 ||
                        grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    mCallButton.setEnabled(false);
                    session.disconnect();
                    Log.i(TAG, "Permission has been denied by user");
                } else {
                    /**
                     * Make the outgoing call
                     */
                    call.call();
                    Log.i(TAG, "Permission has been granted by user");
                }
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (session != null) {
            session.disconnect();
        }
    }
}

