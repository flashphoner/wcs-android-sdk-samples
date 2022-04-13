package com.flashphoner.wcsexample.twosessions;

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
import com.flashphoner.fpwcsapi.bean.StreamStatus;
import com.flashphoner.fpwcsapi.constraints.VideoConstraints;
import com.flashphoner.fpwcsapi.layout.PercentFrameLayout;
import com.flashphoner.fpwcsapi.session.Session;
import com.flashphoner.fpwcsapi.session.SessionEvent;
import com.flashphoner.fpwcsapi.session.SessionOptions;
import com.flashphoner.fpwcsapi.session.Stream;
import com.flashphoner.fpwcsapi.session.StreamOptions;
import com.flashphoner.fpwcsapi.session.StreamStatusEvent;

import org.webrtc.RendererCommon;
import org.webrtc.SurfaceViewRenderer;

/**
 * Example with two players.
 * Each of the players can be used to play a different video stream.
 */
public class TwoSessionsActivity extends AppCompatActivity {

    private static String TAG = TwoSessionsActivity.class.getName();

    private static final int PUBLISH_REQUEST_CODE = 100;

    // UI references.
    private EditText mWcsUrl1View;
    private EditText mWcsUrl2View;
    private EditText mPublish1StreamView;
    private EditText mPublish2StreamView;
    private EditText mPlay1StreamView;
    private EditText mPlay2StreamView;
    private TextView mConnect1Status;
    private TextView mConnect2Status;
    private TextView mPlay1Status;
    private TextView mPlay2Status;
    private Button mConnect1Button;
    private Button mConnect2Button;
    private Button mPlay1Button;
    private Button mPlay2Button;
    private Button mPublish1Button;
    private Button mPublish2Button;

    private Session session1;
    private Session session2;

    private Stream play1Stream;
    private Stream play2Stream;
    private Stream publish1Stream;
    private Stream publish2Stream;

    private SurfaceViewRenderer remote1Render;
    private SurfaceViewRenderer remote2Render;
    private SurfaceViewRenderer local1Render;
    private SurfaceViewRenderer local2Render;

    private PercentFrameLayout remote1RenderLayout;
    private PercentFrameLayout remote2RenderLayout;
    private PercentFrameLayout local1RenderLayout;
    private PercentFrameLayout local2RenderLayout;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_2sessons);

        TextView policyTextView = (TextView) findViewById(R.id.privacy_policy);
        policyTextView.setMovementMethod(LinkMovementMethod.getInstance());
        String policyLink ="<a href=https://flashphoner.com/flashphoner-privacy-policy-for-android-tools/>Privacy Policy</a>";
        policyTextView.setText(Html.fromHtml(policyLink));

        /**
         * Initialization of the API.
         */
        Flashphoner.init(this);

        mWcsUrl1View = (EditText) findViewById(R.id.wcs_url1);
        SharedPreferences sharedPref = this.getPreferences(Context.MODE_PRIVATE);
        mWcsUrl1View.setText(sharedPref.getString("wcs_url", getString(R.string.wcs_url)));
        mConnect1Status = (TextView) findViewById(R.id.connect1_status);
        mConnect1Button = (Button) findViewById(R.id.connect1_button);

        mWcsUrl2View = (EditText) findViewById(R.id.wcs_url2);
        mWcsUrl2View.setText(sharedPref.getString("wcs_url", getString(R.string.wcs_url)));
        mConnect2Status = (TextView) findViewById(R.id.connect2_status);
        mConnect2Button = (Button) findViewById(R.id.connect2_button);

        /**
         * Connection to server will be established when Connect button is clicked.
         */
        initFirstConnectionButton();
        initSecondConnectionButton();

        mPublish1StreamView = (EditText) findViewById(R.id.publish1_stream);
        mPublish1Button = (Button) findViewById(R.id.publish1_button);

        mPublish1Button.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                mPublish1Button.setEnabled(false);
                mPublish2Button.setEnabled(false);
                mConnect1Button.setEnabled(false);
                mConnect2Button.setEnabled(false);
                if (mPublish1Button.getTag() == null || Integer.valueOf(R.string.action_publish).equals(mPublish1Button.getTag())) {
                    /**
                     * The options for the stream to play are set.
                     * The stream name is passed when StreamOptions object is created.
                     * SurfaceViewRenderer to be used to display the video stream is set using method StreamOptions.setRenderer().
                     */
                    StreamOptions streamOptions = new StreamOptions(mPublish1StreamView.getText().toString());
                    streamOptions.setRenderer(local1Render);

                    /**
                     * Stream is created with method Session.createStream().
                     */
                    publish1Stream = session1.createStream(streamOptions);

                    /**
                     * Callback function for stream status change is added to make appropriate changes in controls of the interface when stream is being played.
                     */
                    publish1Stream.on(new StreamStatusEvent() {
                        @Override
                        public void onStreamStatus(final Stream stream, final StreamStatus streamStatus) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    if (StreamStatus.PUBLISHING.equals(streamStatus)) {
                                        mPublish1Button.setText(R.string.action_stop);
                                        mPublish1Button.setTag(R.string.action_stop);
                                    } else if (StreamStatus.NOT_ENOUGH_BANDWIDTH.equals(streamStatus)) {
                                        Log.w(TAG, "Not enough bandwidth stream " + stream.getName() + ", consider using lower video resolution or bitrate. " +
                                                "Bandwidth " + (Math.round(stream.getNetworkBandwidth() / 1000)) + " " +
                                                "bitrate " + (Math.round(stream.getRemoteBitrate() / 1000)));
                                    } else {
                                        mPublish1Button.setText(R.string.action_publish);
                                        mPublish1Button.setTag(R.string.action_publish);
                                        if (mConnect2Button.getTag() == null || Integer.valueOf(R.string.action_disconnect).equals(mConnect2Button.getTag())) {
                                            mPublish2Button.setEnabled(true);
                                        }
                                    }
                                    mConnect1Button.setEnabled(true);
                                    mConnect2Button.setEnabled(true);
                                    mPublish1Button.setEnabled(true);
                                    mPlay1Status.setText(streamStatus.toString());
                                }
                            });
                        }
                    });

                    /**
                     * Method Stream.play() is called to start playback of the stream.
                     */
                    publish1Stream.publish();
                } else {
                    /**
                     * Method Stream.stop() is called to stop playback of the stream.
                     */
                    publish1Stream.stop();
                    publish1Stream = null;
                }
                View currentFocus = getCurrentFocus();
                if (currentFocus != null) {
                    InputMethodManager inputManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    inputManager.hideSoftInputFromWindow(currentFocus.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
                }
            }
        });

        mPublish2StreamView = (EditText) findViewById(R.id.publish2_stream);
        mPublish2Button = (Button) findViewById(R.id.publish2_button);

        mPublish2Button.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                mPublish1Button.setEnabled(false);
                mPublish2Button.setEnabled(false);
                mConnect1Button.setEnabled(false);
                mConnect2Button.setEnabled(false);
                if (mPublish2Button.getTag() == null || Integer.valueOf(R.string.action_publish).equals(mPublish2Button.getTag())) {
                    /**
                     * The options for the stream to play are set.
                     * The stream name is passed when StreamOptions object is created.
                     * SurfaceViewRenderer to be used to display the video stream is set using method StreamOptions.setRenderer().
                     */
                    StreamOptions streamOptions = new StreamOptions(mPublish2StreamView.getText().toString());
                    streamOptions.setRenderer(local2Render);

                    /**
                     * Stream is created with method Session.createStream().
                     */
                    publish2Stream = session2.createStream(streamOptions);

                    /**
                     * Callback function for stream status change is added to make appropriate changes in controls of the interface when stream is being played.
                     */
                    publish2Stream.on(new StreamStatusEvent() {
                        @Override
                        public void onStreamStatus(final Stream stream, final StreamStatus streamStatus) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    if (StreamStatus.PUBLISHING.equals(streamStatus)) {
                                        mPublish2Button.setText(R.string.action_stop);
                                        mPublish2Button.setTag(R.string.action_stop);
                                    } else if (StreamStatus.NOT_ENOUGH_BANDWIDTH.equals(streamStatus)) {
                                        Log.w(TAG, "Not enough bandwidth stream " + stream.getName() + ", consider using lower video resolution or bitrate. " +
                                                "Bandwidth " + (Math.round(stream.getNetworkBandwidth() / 1000)) + " " +
                                                "bitrate " + (Math.round(stream.getRemoteBitrate() / 1000)));
                                    } else {
                                        mPublish2Button.setText(R.string.action_publish);
                                        mPublish2Button.setTag(R.string.action_publish);
                                        if (mConnect1Button.getTag() == null || Integer.valueOf(R.string.action_disconnect).equals(mConnect1Button.getTag())) {
                                            mPublish1Button.setEnabled(true);
                                        }
                                    }
                                    mPublish2Button.setEnabled(true);
                                    mConnect1Button.setEnabled(true);
                                    mConnect2Button.setEnabled(true);
                                    mPlay2Status.setText(streamStatus.toString());
                                }
                            });
                        }
                    });

                    /**
                     * Method Stream.play() is called to start playback of the stream.
                     */
                    publish2Stream.publish();
                } else {
                    /**
                     * Method Stream.stop() is called to stop playback of the stream.
                     */
                    publish2Stream.stop();
                    publish2Stream = null;
                }
                View currentFocus = getCurrentFocus();
                if (currentFocus != null) {
                    InputMethodManager inputManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    inputManager.hideSoftInputFromWindow(currentFocus.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
                }
            }
        });

        mPlay1StreamView = (EditText) findViewById(R.id.play1_stream);
        mPlay1Status = (TextView) findViewById(R.id.play1_status);
        mPlay1Button = (Button) findViewById(R.id.play1_button);

        /**
         * Playback in the left player will be started when the left Play button is clicked.
         */
        mPlay1Button.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                mPlay1Button.setEnabled(false);
                if (mPlay1Button.getTag() == null || Integer.valueOf(R.string.action_play).equals(mPlay1Button.getTag())) {
                    /**
                     * The options for the stream to play are set.
                     * The stream name is passed when StreamOptions object is created.
                     * SurfaceViewRenderer to be used to display the video stream is set using method StreamOptions.setRenderer().
                     */
                    StreamOptions streamOptions = new StreamOptions(mPlay1StreamView.getText().toString());
                    streamOptions.setRenderer(remote1Render);

                    /**
                     * Stream is created with method Session.createStream().
                     */
                    play1Stream = session1.createStream(streamOptions);

                    /**
                     * Callback function for stream status change is added to make appropriate changes in controls of the interface when stream is being played.
                     */
                    play1Stream.on(new StreamStatusEvent() {
                        @Override
                        public void onStreamStatus(final Stream stream, final StreamStatus streamStatus) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    if (StreamStatus.PLAYING.equals(streamStatus)) {
                                        mPlay1Button.setText(R.string.action_stop);
                                        mPlay1Button.setTag(R.string.action_stop);
                                    } else if (StreamStatus.NOT_ENOUGH_BANDWIDTH.equals(streamStatus)) {
                                        Log.w(TAG, "Not enough bandwidth stream " + stream.getName() + ", consider using lower video resolution or bitrate. " +
                                                "Bandwidth " + (Math.round(stream.getNetworkBandwidth() / 1000)) + " " +
                                                "bitrate " + (Math.round(stream.getRemoteBitrate() / 1000)));
                                    } else {
                                        mPlay1Button.setText(R.string.action_play);
                                        mPlay1Button.setTag(R.string.action_play);
                                    }
                                    mPlay1Button.setEnabled(true);
                                    mPlay1Status.setText(streamStatus.toString());
                                }
                            });
                        }
                    });

                    /**
                     * Method Stream.play() is called to start playback of the stream.
                     */
                    play1Stream.play();

                    SharedPreferences sharedPref = TwoSessionsActivity.this.getPreferences(Context.MODE_PRIVATE);
                    SharedPreferences.Editor editor = sharedPref.edit();
                    editor.putString("play1_stream", mPlay1StreamView.getText().toString());
                    editor.apply();
                } else {
                    /**
                     * Method Stream.stop() is called to stop playback of the stream.
                     */
                    play1Stream.stop();
                    play1Stream = null;
                }
                View currentFocus = getCurrentFocus();
                if (currentFocus != null) {
                    InputMethodManager inputManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    inputManager.hideSoftInputFromWindow(currentFocus.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
                }
            }
        });

        mPlay2StreamView = (EditText) findViewById(R.id.play2_stream);
        mPlay2Status = (TextView) findViewById(R.id.play2_status);
        mPlay2Button = (Button) findViewById(R.id.play2_button);

        /**
         * Playback in the right player will be started when the right Play button is clicked.
         */
        mPlay2Button.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                mPlay2Button.setEnabled(false);
                if (mPlay2Button.getTag() == null || Integer.valueOf(R.string.action_play).equals(mPlay2Button.getTag())) {
                    /**
                     * The options for the stream to play are set.
                     * The stream name is passed when StreamOptions object is created.
                     * SurfaceViewRenderer to be used to display the video stream is set using method StreamOptions.setRenderer().
                     */
                    StreamOptions streamOptions = new StreamOptions(mPlay2StreamView.getText().toString());
                    streamOptions.setRenderer(remote2Render);

                    /**
                     * Stream is created with method Session.createStream().
                     */
                    play2Stream = session2.createStream(streamOptions);

                    /**
                     * Callback function for stream status change is added to make appropriate changes in controls of the interface when stream is being played.
                     */
                    play2Stream.on(new StreamStatusEvent() {
                        @Override
                        public void onStreamStatus(final Stream stream, final StreamStatus streamStatus) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {

                                    if (StreamStatus.PLAYING.equals(streamStatus)) {
                                        mPlay2Button.setText(R.string.action_stop);
                                        mPlay2Button.setTag(R.string.action_stop);
                                    } else if (StreamStatus.NOT_ENOUGH_BANDWIDTH.equals(streamStatus)) {
                                        Log.w(TAG, "Not enough bandwidth stream " + stream.getName() + ", consider using lower video resolution or bitrate. " +
                                                "Bandwidth " + (Math.round(stream.getNetworkBandwidth() / 1000)) + " " +
                                                "bitrate " + (Math.round(stream.getRemoteBitrate() / 1000)));
                                    } else {
                                        mPlay2Button.setText(R.string.action_play);
                                        mPlay2Button.setTag(R.string.action_play);
                                    }
                                    mPlay2Button.setEnabled(true);
                                    mPlay2Status.setText(streamStatus.toString());
                                }
                            });
                        }
                    });

                    /**
                     * Method Stream.play() is called to start playback of the stream.
                     */
                    play2Stream.play();

                    SharedPreferences sharedPref = TwoSessionsActivity.this.getPreferences(Context.MODE_PRIVATE);
                    SharedPreferences.Editor editor = sharedPref.edit();
                    editor.putString("play2_stream", mPlay2StreamView.getText().toString());
                    editor.apply();
                } else {
                    /**
                     * Method Stream.stop() is called to stop playback of the stream.
                     */
                    play2Stream.stop();
                    play2Stream = null;
                }
                View currentFocus = getCurrentFocus();
                if (currentFocus != null) {
                    InputMethodManager inputManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    inputManager.hideSoftInputFromWindow(currentFocus.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
                }
            }
        });

        remote1Render = (SurfaceViewRenderer) findViewById(R.id.remote_video1_view);
        remote2Render = (SurfaceViewRenderer) findViewById(R.id.remote_video2_view);
        local1Render = (SurfaceViewRenderer) findViewById(R.id.local_video1_view);
        local2Render = (SurfaceViewRenderer) findViewById(R.id.local_video2_view);

        remote1RenderLayout = (PercentFrameLayout) findViewById(R.id.remote_video1_layout);
        remote2RenderLayout = (PercentFrameLayout) findViewById(R.id.remote_video2_layout);
        local1RenderLayout = (PercentFrameLayout) findViewById(R.id.local_video1_layout);
        local2RenderLayout = (PercentFrameLayout) findViewById(R.id.local_video2_layout);

        remote1Render.setZOrderMediaOverlay(true);

        remote2RenderLayout.setPosition(0, 0, 100, 100);
        remote2Render.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);
        remote2Render.setMirror(false);
        remote2Render.requestLayout();

        remote1RenderLayout.setPosition(0, 0, 100, 100);
        remote1Render.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);
        remote1Render.setMirror(false);
        remote1Render.requestLayout();

        local1RenderLayout.setPosition(0, 0, 100, 100);
        local2RenderLayout.setPosition(0, 0, 100, 100);
    }

    private void initSecondConnectionButton() {
        mConnect2Button.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mConnect2Button.getTag() == null || Integer.valueOf(R.string.action_connect).equals(mConnect2Button.getTag())) {
                    /**
                     * The options for connection session are set.
                     * WCS server URL is passed when SessionOptions object is created.
                     */
                    SessionOptions sessionOptions = new SessionOptions(mWcsUrl2View.getText().toString());
                    sessionOptions.setRemoteRenderer(remote2Render);
                    sessionOptions.setLocalRenderer(local2Render);

                    /**
                     * Session for connection to WCS server is created with method createSession().
                     */
                    session2 = Flashphoner.createSession(sessionOptions);

                    /**
                     * Callback functions for session status events are added to make appropriate changes in controls of the interface when connection is established and closed.
                     */
                    session2.on(new SessionEvent() {
                        @Override
                        public void onAppData(Data data) {

                        }

                        @Override
                        public void onConnected(final Connection connection) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    mConnect2Button.setText(R.string.action_disconnect);
                                    mConnect2Button.setTag(R.string.action_disconnect);
                                    mConnect2Button.setEnabled(true);
                                    mConnect2Status.setText(connection.getStatus());
                                    mPlay2Button.setEnabled(true);
                                    if (mPublish1Button.getTag() == null || Integer.valueOf(R.string.action_publish).equals(mPublish1Button.getTag())) {
                                        mPublish2Button.setEnabled(true);
                                    }

                                    ActivityCompat.requestPermissions(TwoSessionsActivity.this,
                                            new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA},
                                            PUBLISH_REQUEST_CODE);
                                }
                            });
                        }

                        @Override
                        public void onRegistered(Connection connection) {

                        }

                        @Override
                        public void onDisconnection(final Connection connection) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    mConnect2Button.setText(R.string.action_connect);
                                    mConnect2Button.setTag(R.string.action_connect);
                                    mConnect2Button.setEnabled(true);
                                    mPlay2Button.setText(R.string.action_play);
                                    mPlay2Button.setTag(R.string.action_play);
                                    mPlay2Button.setEnabled(false);
                                    mPublish2Button.setEnabled(false);
                                    mPublish2Button.setText(R.string.action_publish);
                                    mPublish2Button.setTag(R.string.action_publish);
                                    mConnect2Status.setText(connection.getStatus());
                                    mPlay2Status.setText("");
                                    if (mConnect1Button.getTag() == null || Integer.valueOf(R.string.action_disconnect).equals(mConnect1Button.getTag())) {
                                        mPublish1Button.setEnabled(true);
                                    }
                                }
                            });
                        }
                    });

                    mConnect2Button.setEnabled(false);

                    /**
                     * Connection to WCS server is established with method Session.connect().
                     */
                    session2.connect(new Connection());

                    SharedPreferences sharedPref = TwoSessionsActivity.this.getPreferences(Context.MODE_PRIVATE);
                    SharedPreferences.Editor editor = sharedPref.edit();
                    editor.putString("wcs_url", mWcsUrl2View.getText().toString());
                    editor.apply();
                } else {
                    mConnect2Button.setEnabled(false);

                    /**
                     * Connection to WCS server is closed with method Session.disconnect().
                     */
                    session2.disconnect();
                }

                View currentFocus = getCurrentFocus();
                if (currentFocus != null) {
                    InputMethodManager inputManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    inputManager.hideSoftInputFromWindow(currentFocus.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
                }
            }
        });
    }

    private void initFirstConnectionButton() {
        mConnect1Button.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mConnect1Button.getTag() == null || Integer.valueOf(R.string.action_connect).equals(mConnect1Button.getTag())) {
                    /**
                     * The options for connection session are set.
                     * WCS server URL is passed when SessionOptions object is created.
                     */
                    SessionOptions sessionOptions = new SessionOptions(mWcsUrl1View.getText().toString());
                    sessionOptions.setRemoteRenderer(remote1Render);
                    sessionOptions.setLocalRenderer(local1Render);

                    /**
                     * Session for connection to WCS server is created with method createSession().
                     */
                    session1 = Flashphoner.createSession(sessionOptions);

                    /**
                     * Callback functions for session status events are added to make appropriate changes in controls of the interface when connection is established and closed.
                     */
                    session1.on(new SessionEvent() {
                        @Override
                        public void onAppData(Data data) {

                        }

                        @Override
                        public void onConnected(final Connection connection) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    mConnect1Button.setText(R.string.action_disconnect);
                                    mConnect1Button.setTag(R.string.action_disconnect);
                                    mConnect1Button.setEnabled(true);
                                    mConnect1Status.setText(connection.getStatus());
                                    mPlay1Button.setEnabled(true);
                                    if (mPublish2Button.getTag() == null || Integer.valueOf(R.string.action_publish).equals(mPublish2Button.getTag())) {
                                        mPublish1Button.setEnabled(true);
                                    }

                                    ActivityCompat.requestPermissions(TwoSessionsActivity.this,
                                            new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA},
                                            PUBLISH_REQUEST_CODE);
                                }
                            });
                        }

                        @Override
                        public void onRegistered(Connection connection) {

                        }

                        @Override
                        public void onDisconnection(final Connection connection) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    mConnect1Button.setText(R.string.action_connect);
                                    mConnect1Button.setTag(R.string.action_connect);
                                    mConnect1Button.setEnabled(true);
                                    mPlay1Button.setText(R.string.action_play);
                                    mPlay1Button.setTag(R.string.action_play);
                                    mPlay1Button.setEnabled(false);
                                    mPublish1Button.setEnabled(false);
                                    mPublish1Button.setText(R.string.action_publish);
                                    mPublish1Button.setTag(R.string.action_publish);
                                    mConnect1Status.setText(connection.getStatus());
                                    mPlay1Status.setText("");
                                    if (mConnect2Button.getTag() == null || Integer.valueOf(R.string.action_disconnect).equals(mConnect2Button.getTag())) {
                                        mPublish2Button.setEnabled(true);
                                    }
                                }
                            });
                        }
                    });

                    mConnect1Button.setEnabled(false);

                    /**
                     * Connection to WCS server is established with method Session.connect().
                     */
                    session1.connect(new Connection());

                    SharedPreferences sharedPref = TwoSessionsActivity.this.getPreferences(Context.MODE_PRIVATE);
                    SharedPreferences.Editor editor = sharedPref.edit();
                    editor.putString("wcs_url", mWcsUrl1View.getText().toString());
                    editor.apply();
                } else {
                    mConnect1Button.setEnabled(false);

                    /**
                     * Connection to WCS server is closed with method Session.disconnect().
                     */
                    session1.disconnect();
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
            case PUBLISH_REQUEST_CODE: {
                if (grantResults.length == 0 ||
                        grantResults[0] != PackageManager.PERMISSION_GRANTED ||
                        grantResults[1] != PackageManager.PERMISSION_GRANTED) {
                    if (session1 != null) {
                        session1.disconnect();
                    }
                    if (session2 != null) {
                        session2.disconnect();
                    }
                    Log.i(TAG, "Permission has been denied by user");
                } else {
                    /**
                     * Method Stream.publish() is called to publish stream.
                     */
                    Log.i(TAG, "Permission has been granted by user");
                }
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (session1 != null) {
            session1.disconnect();
        }
        if (session2 != null) {
            session2.disconnect();
        }
    }
}

