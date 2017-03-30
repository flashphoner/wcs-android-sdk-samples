package com.flashphoner.wcsexample.phone_min_video;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;

import com.flashphoner.fpwcsapi.Flashphoner;
import com.flashphoner.fpwcsapi.bean.Connection;
import com.flashphoner.fpwcsapi.bean.Data;
import com.flashphoner.fpwcsapi.layout.PercentFrameLayout;
import com.flashphoner.fpwcsapi.session.Call;
import com.flashphoner.fpwcsapi.session.CallOptions;
import com.flashphoner.fpwcsapi.session.CallStatusEvent;
import com.flashphoner.fpwcsapi.session.IncomingCallEvent;
import com.flashphoner.fpwcsapi.session.Session;
import com.flashphoner.fpwcsapi.session.SessionEvent;
import com.flashphoner.fpwcsapi.session.SessionOptions;

import org.webrtc.RendererCommon;
import org.webrtc.SurfaceViewRenderer;

/**
 * Example of a SIP phone built-in into a mobile app
 * Supported two way SIP video calls between the mobile app and a SIP endpoint
 */
public class PhoneMinVideoActivity extends AppCompatActivity {

    private static String TAG = PhoneMinVideoActivity.class.getName();

    private static final int CALL_REQUEST_CODE = 100;
    private static final int INCOMING_CALL_REQUEST_CODE = 101;

    // UI references.
    private EditText mWcsUrlView;
    private EditText mSipLoginView;
    private EditText mSipPasswordView;
    private EditText mSipDomainView;
    private EditText mSipPortView;
    private CheckBox mSipRegisterRequiredView;
    private TextView mConnectStatus;
    private Button mConnectButton;
    private EditText mCalleeView;
    private TextView mCallStatus;
    private Button mCallButton;
    private Button mHoldButton;
    private Switch mMuteAudio;
    private Switch mMuteVideo;

    /**
     * Associated session with WCS
     */
    private Session session;

    /**
     * SIP call
     */
    private Call call;

    /**
     * Processing the call status and displaying UI changes
     */
    private CallStatusEvent callStatusEvent;


    /**
     * UI alert for incoming call
     */
    private AlertDialog incomingCallAlert;

    /**
     * UI renderer for local video captured from the web-cam
     */
    private SurfaceViewRenderer localRender;

    /**
     * UI renderer for remote video received from remote SIP party
     */
    private SurfaceViewRenderer remoteRender;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_streaming_min);


        /**
         * Initialization of the API.
         */
        Flashphoner.init(this);

        /**
         * UI controls
         */
        SharedPreferences sharedPref = this.getPreferences(Context.MODE_PRIVATE);
        mWcsUrlView = (EditText) findViewById(R.id.wcs_url);
        mWcsUrlView.setText(sharedPref.getString("wcs_url", getString(R.string.wcs_url)));
        mSipLoginView = (EditText) findViewById(R.id.sip_login);
        mSipLoginView.setText(sharedPref.getString("sip_login", getString(R.string.sip_login)));
        mSipPasswordView = (EditText) findViewById(R.id.sip_password);
        mSipPasswordView.setText(sharedPref.getString("sip_password", getString(R.string.sip_password)));
        mSipDomainView = (EditText) findViewById(R.id.sip_domain);
        mSipDomainView.setText(sharedPref.getString("sip_domain", getString(R.string.sip_domain)));
        mSipPortView = (EditText) findViewById(R.id.sip_port);
        mSipPortView.setText(sharedPref.getString("sip_port", getString(R.string.sip_port)));
        mSipRegisterRequiredView = (CheckBox) findViewById(R.id.register_required);
        mSipRegisterRequiredView.setChecked(sharedPref.getBoolean("sip_register_required", true));

        callStatusEvent = new CallStatusEvent() {
            /**
             * WCS received SIP 100 TRYING
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
                        mCallStatus.setText(call.getStatus());
                    }
                });
            }

            /**
             * WCS received SIP BUSY_HERE or BUSY_EVERYWHERE from SIP
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
                        mCallStatus.setText(call.getStatus());
                    }
                });
            }

            /**
             * Call is failed
             * @param call
             */
            @Override
            public void onFailed(Call call) {

            }

            /**
             * WCS received SIP 180 RINGING from SIP
             * @param call
             */
            @Override
            public void onRing(final Call call) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mCallButton.setText(R.string.action_hangup);
                        mCallButton.setTag(R.string.action_hangup);
                        mCallButton.setEnabled(true);
                        mCallStatus.setText(call.getStatus());
                    }
                });


            }

            /**
             * Call is set on hold
             * @param call
             */
            @Override
            public void onHold(final Call call) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mCallStatus.setText(call.getStatus());
                    }
                });
            }

            /**
             * Call established
             * @param call
             */
            @Override
            public void onEstablished(final Call call) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mCallStatus.setText(call.getStatus());
                        mHoldButton.setEnabled(true);
                        mMuteAudio.setEnabled(true);
                        mMuteVideo.setEnabled(true);
                    }
                });

            }

            /**
             * Call is terminated
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
                        mCallStatus.setText(call.getStatus());
                        mHoldButton.setText(R.string.action_hold);
                        mHoldButton.setTag(R.string.action_hold);
                        mHoldButton.setEnabled(false);
                        mMuteAudio.setEnabled(false);
                        mMuteAudio.setChecked(false);
                        mMuteVideo.setEnabled(false);
                        mMuteVideo.setChecked(false);
                        if (incomingCallAlert != null) {
                            incomingCallAlert.hide();
                            incomingCallAlert = null;
                        }
                    }
                });

            }
        };

        mConnectStatus = (TextView) findViewById(R.id.connect_status);
        mConnectButton = (Button) findViewById(R.id.connect_button);
        /**
         * Connect button pressed
         */
        mConnectButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {

                if (mConnectButton.getTag() == null || Integer.valueOf(R.string.action_connect).equals(mConnectButton.getTag())) {
                    SessionOptions sessionOptions = new SessionOptions(mWcsUrlView.getText().toString());
                    sessionOptions.setLocalRenderer(localRender);
                    sessionOptions.setRemoteRenderer(remoteRender);
                    session = Flashphoner.createSession(sessionOptions);
                    session.on(new SessionEvent() {
                        @Override
                        public void onAppData(Data data) {

                        }

                        /**
                         * Connection established
                         * @param connection Current connection state
                         */
                        @Override
                        public void onConnected(final Connection connection) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    mConnectButton.setText(R.string.action_disconnect);
                                    mConnectButton.setTag(R.string.action_disconnect);
                                    mConnectButton.setEnabled(true);
                                    if (!mSipRegisterRequiredView.isChecked()) {
                                        mConnectStatus.setText(connection.getStatus());
                                        mCallButton.setEnabled(true);
                                    } else {
                                        mConnectStatus.setText(connection.getStatus() + ". Registering...");
                                    }
                                }
                            });
                        }

                        /**
                         * Phone registered
                         * @param connection Current connection state
                         */
                        @Override
                        public void onRegistered(final Connection connection) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    mConnectStatus.setText(connection.getStatus());
                                    mCallButton.setEnabled(true);
                                }
                            });
                        }

                        /**
                         * Phone disconnected
                         * @param connection Current connection state
                         */
                        @Override
                        public void onDisconnection(final Connection connection) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    mConnectButton.setText(R.string.action_connect);
                                    mConnectButton.setTag(R.string.action_connect);
                                    mConnectButton.setEnabled(true);
                                    mConnectStatus.setText(connection.getStatus());
                                    mCallButton.setText(R.string.action_call);
                                    mCallButton.setTag(R.string.action_call);
                                    mCallButton.setEnabled(false);
                                    mCallStatus.setText("");
                                    mHoldButton.setText(R.string.action_hold);
                                    mHoldButton.setTag(R.string.action_hold);
                                    mHoldButton.setEnabled(false);
                                    mMuteAudio.setEnabled(false);
                                    mMuteAudio.setChecked(false);
                                    mMuteVideo.setEnabled(false);
                                    mMuteVideo.setChecked(false);

                                }
                            });
                        }
                    });

                    /**
                     * Add handler for incoming call
                     */
                    session.on(new IncomingCallEvent() {
                        @Override
                        public void onCall(final Call call) {
                            call.on(callStatusEvent);
                            /**
                             * Display UI alert for the new incoming call
                             */
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    AlertDialog.Builder builder = new AlertDialog.Builder(PhoneMinVideoActivity.this);

                                    builder.setTitle("Incoming call");

                                    builder.setMessage("Incoming call from '" + call.getCaller() + "'");
                                    builder.setPositiveButton("Answer", new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialogInterface, int i) {
                                            PhoneMinVideoActivity.this.call = call;
                                            ActivityCompat.requestPermissions(PhoneMinVideoActivity.this,
                                                    new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA},
                                                    INCOMING_CALL_REQUEST_CODE);
                                        }
                                    });
                                    builder.setNegativeButton("Hangup", new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialogInterface, int i) {
                                            call.hangup();
                                            incomingCallAlert = null;
                                        }
                                    });
                                    incomingCallAlert = builder.show();
                                }
                            });
                        }
                    });
                    mConnectButton.setEnabled(false);
                    /**
                     * Connection containing SIP details
                     */
                    Connection connection = new Connection();
                    connection.setSipLogin(mSipLoginView.getText().toString());
                    connection.setSipPassword(mSipPasswordView.getText().toString());
                    connection.setSipDomain(mSipDomainView.getText().toString());
                    connection.setSipOutboundProxy(mSipDomainView.getText().toString());
                    connection.setSipPort(Integer.parseInt(mSipPortView.getText().toString()));
                    connection.setSipRegisterRequired(mSipRegisterRequiredView.isChecked());
                    session.connect(connection);

                    SharedPreferences sharedPref = PhoneMinVideoActivity.this.getPreferences(Context.MODE_PRIVATE);
                    SharedPreferences.Editor editor = sharedPref.edit();
                    editor.putString("wcs_url", mWcsUrlView.getText().toString());
                    editor.putString("sip_login", mSipLoginView.getText().toString());
                    editor.putString("sip_password", mSipPasswordView.getText().toString());
                    editor.putString("sip_domain", mSipDomainView.getText().toString());
                    editor.putString("sip_port", mSipPortView.getText().toString());
                    editor.putBoolean("sip_register_required", mSipRegisterRequiredView.isChecked());
                    editor.apply();
                } else {
                    mConnectButton.setEnabled(false);
                    session.disconnect();
                }
                View currentFocus = getCurrentFocus();
                if (currentFocus != null) {
                    InputMethodManager inputManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    inputManager.hideSoftInputFromWindow(currentFocus.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
                }
            }
        });

        mCalleeView = (EditText) findViewById(R.id.callee);
        mCalleeView.setText(sharedPref.getString("callee", getString(R.string.default_callee_name)));
        mCallStatus = (TextView) findViewById(R.id.call_status);
        mCallButton = (Button) findViewById(R.id.call_button);
        /**
         * Call button pressed
         */
        mCallButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mCallButton.getTag() == null || Integer.valueOf(R.string.action_call).equals(mCallButton.getTag())) {
                    if ("".equals(mCalleeView.getText().toString())) {
                        return;
                    }
                    ActivityCompat.requestPermissions(PhoneMinVideoActivity.this,
                            new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA},
                            CALL_REQUEST_CODE);


                    SharedPreferences sharedPref = PhoneMinVideoActivity.this.getPreferences(Context.MODE_PRIVATE);
                    SharedPreferences.Editor editor = sharedPref.edit();
                    editor.putString("callee", mCalleeView.getText().toString());
                    editor.apply();
                } else {
                    mCallButton.setEnabled(false);
                    call.hangup();
                    call = null;
                }
                View currentFocus = getCurrentFocus();
                if (currentFocus != null) {
                    InputMethodManager inputManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    inputManager.hideSoftInputFromWindow(currentFocus.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
                }
            }
        });

        mHoldButton = (Button) findViewById(R.id.hold_button);
        /**
         * Hold or Unhold button pressed
         * Hold the call if the call is ESTABLISHED.
         * Unhold the call if the call is on hold.
         */
        mHoldButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mHoldButton.getTag() == null || Integer.valueOf(R.string.action_hold).equals(mHoldButton.getTag())) {
                    call.hold();
                    mHoldButton.setText(R.string.action_unhold);
                    mHoldButton.setTag(R.string.action_unhold);
                } else {
                    call.unhold();
                    mHoldButton.setText(R.string.action_hold);
                    mHoldButton.setTag(R.string.action_hold);
                }

            }
        });

        mMuteAudio = (Switch) findViewById(R.id.mute_audio);
        /**
         * Mute or Unmute audio for the SIP call
         * Mute if it is not muted.
         * Unmute if it is muted.
         */
        mMuteAudio.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (call != null) {
                    if (isChecked) {
                        call.muteAudio();
                    } else {
                        call.unmuteAudio();
                    }
                }
            }
        });
        mMuteVideo = (Switch) findViewById(R.id.mute_video);
        /**
         * Mute or Unmute video for the SIP call
         * Mute if it is not muted.
         * Unmute if it is muted.
         */
        mMuteVideo.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (call != null) {
                    if (isChecked) {
                        call.muteVideo();
                    } else {
                        call.unmuteVideo();
                    }
                }
            }
        });

        /**
         * Set local and remote renderer objects
         */
        localRender = (SurfaceViewRenderer) findViewById(R.id.local_video_view);
        remoteRender = (SurfaceViewRenderer) findViewById(R.id.remote_video_view);

        PercentFrameLayout localRenderLayout = (PercentFrameLayout) findViewById(R.id.local_video_layout);
        PercentFrameLayout remoteRenderLayout = (PercentFrameLayout) findViewById(R.id.remote_video_layout);

        localRender.setZOrderMediaOverlay(true);

        remoteRenderLayout.setPosition(0, 0, 100, 100);
        remoteRender.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);
        remoteRender.setMirror(false);
        remoteRender.requestLayout();

        localRenderLayout.setPosition(0, 0, 100, 100);
        localRender.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);
        localRender.setMirror(true);
        localRender.requestLayout();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String permissions[], @NonNull int[] grantResults) {
        switch (requestCode) {
            case CALL_REQUEST_CODE: {
                if (grantResults.length == 0 ||
                        grantResults[0] != PackageManager.PERMISSION_GRANTED ||
                        grantResults[1] != PackageManager.PERMISSION_GRANTED ) {
                    Log.i(TAG, "Permission has been denied by user");
                } else {
                    mCallButton.setEnabled(false);
                    /**
                     * Get call options from the callee text field
                     */
                    CallOptions callOptions = new CallOptions(mCalleeView.getText().toString());
                    callOptions.getConstraints().updateVideo(true);
                    call = session.createCall(callOptions);
                    call.on(callStatusEvent);
                    /**
                     * Make a new outgoing call
                     */
                    call.call();
                    Log.i(TAG, "Permission has been granted by user");
                }
                break;
            }
            case INCOMING_CALL_REQUEST_CODE: {
                if (grantResults.length == 0 ||
                        grantResults[0] != PackageManager.PERMISSION_GRANTED ||
                        grantResults[1] != PackageManager.PERMISSION_GRANTED ) {
                    call.hangup();
                    incomingCallAlert = null;
                    Log.i(TAG, "Permission has been denied by user");
                } else {
                    mCallButton.setText(R.string.action_hangup);
                    mCallButton.setTag(R.string.action_hangup);
                    mCallButton.setEnabled(true);
                    mCallStatus.setText(call.getStatus());
                    call.getCallOptions().getConstraints().updateVideo(true);
                    call.getCallObject().setHasVideo(true);
                    call.answer();
                    incomingCallAlert = null;
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

