package com.flashphoner.wcsexample.twoplayers;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.flashphoner.fpwcsapi.Flashphoner;
import com.flashphoner.fpwcsapi.bean.Connection;
import com.flashphoner.fpwcsapi.bean.Data;
import com.flashphoner.fpwcsapi.bean.StreamStatus;
import com.flashphoner.fpwcsapi.session.SessionEvent;
import com.flashphoner.fpwcsapi.layout.PercentFrameLayout;
import com.flashphoner.fpwcsapi.session.Session;
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
public class TwoPlayersActivity extends AppCompatActivity {

    // UI references.
    private EditText mWcsUrlView;
    private TextView mConnectStatus;
    private Button mConnectButton;
    private EditText mPlay1StreamView;
    private TextView mPlay1Status;
    private Button mPlay1Button;
    private EditText mPlay2StreamView;
    private TextView mPlay2Status;
    private Button mPlay2Button;

    private Session session;

    private Stream play1Stream;
    private Stream play2Stream;

    private SurfaceViewRenderer remote1Render;
    private SurfaceViewRenderer remote2Render;

    private PercentFrameLayout remote1RenderLayout;
    private PercentFrameLayout remote2RenderLayout;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_2players);

        /**
         * Initialization of the API.
         */
        Flashphoner.init(this);

        mWcsUrlView = (EditText) findViewById(R.id.wcs_url);
        SharedPreferences sharedPref = this.getPreferences(Context.MODE_PRIVATE);
        mWcsUrlView.setText(sharedPref.getString("wcs_url", getString(R.string.wcs_url)));
        mConnectStatus = (TextView) findViewById(R.id.connect_status);
        mConnectButton = (Button) findViewById(R.id.connect_button);

        /**
         * Connection to server will be established when Connect button is clicked.
         */
        mConnectButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mConnectButton.getTag() == null || Integer.valueOf(R.string.action_connect).equals(mConnectButton.getTag())) {
                    /**
                     * The options for connection session are set.
                     * WCS server URL is passed when SessionOptions object is created.
                     */
                    SessionOptions sessionOptions = new SessionOptions(mWcsUrlView.getText().toString());
                    sessionOptions.setRemoteRenderer(remote2Render);

                    /**
                     * Session for connection to WCS server is created with method createSession().
                     */
                    session = Flashphoner.createSession(sessionOptions);

                    /**
                     * Callback functions for session status events are added to make appropriate changes in controls of the interface when connection is established and closed.
                     */
                    session.on(new SessionEvent() {
                        @Override
                        public void onAppData(Data data) {

                        }

                        @Override
                        public void onConnected(final Connection connection) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    mConnectButton.setText(R.string.action_disconnect);
                                    mConnectButton.setTag(R.string.action_disconnect);
                                    mConnectButton.setEnabled(true);
                                    mConnectStatus.setText(connection.getStatus());
                                    mPlay1Button.setEnabled(true);
                                    mPlay2Button.setEnabled(true);
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
                                    mConnectButton.setText(R.string.action_connect);
                                    mConnectButton.setTag(R.string.action_connect);
                                    mConnectButton.setEnabled(true);
                                    mPlay1Button.setText(R.string.action_play);
                                    mPlay1Button.setTag(R.string.action_play);
                                    mPlay1Button.setEnabled(false);
                                    mPlay2Button.setText(R.string.action_play);
                                    mPlay2Button.setTag(R.string.action_play);
                                    mPlay2Button.setEnabled(false);
                                    mConnectStatus.setText(connection.getStatus());
                                    mPlay1Status.setText("");
                                    mPlay2Status.setText("");
                                }
                            });
                        }
                    });

                    mConnectButton.setEnabled(false);

                    /**
                     * Connection to WCS server is established with method Session.connect().
                     */
                    session.connect(new Connection());

                    SharedPreferences sharedPref = TwoPlayersActivity.this.getPreferences(Context.MODE_PRIVATE);
                    SharedPreferences.Editor editor = sharedPref.edit();
                    editor.putString("wcs_url", mWcsUrlView.getText().toString());
                    editor.apply();
                } else {
                    mConnectButton.setEnabled(false);

                    /**
                     * Connection to WCS server is closed with method Session.disconnect().
                     */
                    session.disconnect();
                }

                View currentFocus = getCurrentFocus();
                if (currentFocus != null) {
                    InputMethodManager inputManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    inputManager.hideSoftInputFromWindow(currentFocus.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
                }
            }
        });

        mPlay1StreamView = (EditText) findViewById(R.id.play1_stream);
        mPlay1StreamView.setText(sharedPref.getString("play1_stream", getString(R.string.default_play1_name)));
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
                    play1Stream = session.createStream(streamOptions);

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

                    SharedPreferences sharedPref = TwoPlayersActivity.this.getPreferences(Context.MODE_PRIVATE);
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
        mPlay2StreamView.setText(sharedPref.getString("play2_stream", getString(R.string.default_play2_name)));
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
                    play2Stream = session.createStream(streamOptions);

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

                    SharedPreferences sharedPref = TwoPlayersActivity.this.getPreferences(Context.MODE_PRIVATE);
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

        remote1Render = (SurfaceViewRenderer) findViewById(R.id.remote1_video_view);
        remote2Render = (SurfaceViewRenderer) findViewById(R.id.remote2_video_view);

        remote1RenderLayout = (PercentFrameLayout) findViewById(R.id.remove1_video_layout);
        remote2RenderLayout = (PercentFrameLayout) findViewById(R.id.remote2_video_layout);

        remote1Render.setZOrderMediaOverlay(true);

        remote2RenderLayout.setPosition(0, 0, 100, 100);
        remote2Render.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);
        remote2Render.setMirror(false);
        remote2Render.requestLayout();

        remote1RenderLayout.setPosition(0, 0, 100, 100);
        remote1Render.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);
        remote1Render.setMirror(false);
        remote1Render.requestLayout();
    }
}

