package com.flashphoner.wcsexample.player;

import android.content.Context;
import android.content.SharedPreferences;
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

import androidx.appcompat.app.AppCompatActivity;

import com.flashphoner.fpwcsapi.Flashphoner;
import com.flashphoner.fpwcsapi.bean.Connection;
import com.flashphoner.fpwcsapi.bean.Data;
import com.flashphoner.fpwcsapi.bean.StreamStatus;
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
 * Player example.
 * Can be used to play any of the following types of streams on Web Call Server: RTSP, WebRTC, RTMP, RTMFP.
 */
public class PlayerActivity extends AppCompatActivity {

    private static String TAG = PlayerActivity.class.getName();

    // UI references.
    private EditText mWcsUrlView;
    private EditText mPlayStreamView;
    private TextView mStatusView;
    private Button mStartButton;

    private Session session;

    private Stream playStream;

    private SurfaceViewRenderer remoteRender;

    private PercentFrameLayout remoteRenderLayout;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        TextView policyTextView = (TextView) findViewById(R.id.privacy_policy);
        policyTextView.setMovementMethod(LinkMovementMethod.getInstance());
        String policyLink ="<a href=https://flashphoner.com/flashphoner-privacy-policy-for-android-tools/>Privacy Policy</a>";
        policyTextView.setText(Html.fromHtml(policyLink));

        /**
         * Initialization of the API.
         */
        Flashphoner.init(this);

        mWcsUrlView = (EditText) findViewById(R.id.wcs_url);
        SharedPreferences sharedPref = this.getPreferences(Context.MODE_PRIVATE);
        mWcsUrlView.setText(sharedPref.getString("wcs_url", getString(R.string.wcs_url)));
        mPlayStreamView = (EditText) findViewById(R.id.play_stream);
        mPlayStreamView.setText(sharedPref.getString("play_stream", getString(R.string.default_play_stream)));
        mStatusView = (TextView) findViewById(R.id.status);
        mStartButton = (Button) findViewById(R.id.start_button);

        /**
         * Connection to server will be established and stream playback will be started when Start button is clicked.
         */
        mStartButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mStartButton.getTag() == null || Integer.valueOf(R.string.action_start).equals(mStartButton.getTag())) {
                    /**
                     * The options for connection session are set.
                     * WCS server URL is passed when SessionOptions object is created.
                     * SurfaceViewRenderer to be used to display the video stream is set with method SessionOptions.setRemoteRenderer().
                     */
                    SessionOptions sessionOptions = new SessionOptions(mWcsUrlView.getText().toString());
                    sessionOptions.setRemoteRenderer(remoteRender);

                    /**
                     * Session for connection to WCS server is created with method createSession().
                     */
                    session = Flashphoner.createSession(sessionOptions);

                    /**
                     * Callback functions for session status events are added to make appropriate changes in controls of the interface and play stream when connection is established.
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
                                    mStartButton.setText(R.string.action_stop);
                                    mStartButton.setTag(R.string.action_stop);
                                    mStartButton.setEnabled(true);
                                    mStatusView.setText(connection.getStatus());

                                    /**
                                     * The options for the stream to play are set.
                                     * The stream name is passed when StreamOptions object is created.
                                     */
                                    StreamOptions streamOptions = new StreamOptions(mPlayStreamView.getText().toString());

                                    /**
                                     * Stream is created with method Session.createStream().
                                     */
                                    playStream = session.createStream(streamOptions);

                                    /**
                                     * Callback function for stream status change is added to display the status.
                                     */
                                    playStream.on(new StreamStatusEvent() {
                                        @Override
                                        public void onStreamStatus(final Stream stream, final StreamStatus streamStatus) {
                                            runOnUiThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    if (!StreamStatus.PLAYING.equals(streamStatus)) {
                                                        Log.e(TAG, "Can not play stream " + stream.getName() + " " + streamStatus);
                                                        onStopped(streamStatus.toString());
                                                    } else if (StreamStatus.NOT_ENOUGH_BANDWIDTH.equals(streamStatus)) {
                                                        Log.w(TAG, "Not enough bandwidth stream " + stream.getName() + ", consider using lower video resolution or bitrate. " +
                                                                "Bandwidth " + (Math.round(stream.getNetworkBandwidth() / 1000)) + " " +
                                                                "bitrate " + (Math.round(stream.getRemoteBitrate() / 1000)));
                                                    } else {
                                                        mStatusView.setText(streamStatus.toString());
                                                    }
                                                }
                                            });
                                        }
                                    });

                                    /*
                                     * Method Stream.play() is called to start playback of the stream.
                                     */
                                    playStream.play();

                                    SharedPreferences sharedPref = PlayerActivity.this.getPreferences(Context.MODE_PRIVATE);
                                    SharedPreferences.Editor editor = sharedPref.edit();
                                    editor.putString("play_stream", mPlayStreamView.getText().toString());
                                    editor.apply();
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
                                    onStopped(connection.getStatus());
                                    mStartButton.setEnabled(true);
                                }
                            });
                        }
                    });

                    mStartButton.setEnabled(false);

                    /**
                     * Connection to WCS server is established with method Session.connect().
                     */
                    session.connect(new Connection());

                    SharedPreferences sharedPref = PlayerActivity.this.getPreferences(Context.MODE_PRIVATE);
                    SharedPreferences.Editor editor = sharedPref.edit();
                    editor.putString("wcs_url", mWcsUrlView.getText().toString());
                    editor.apply();
                } else {
                    mStartButton.setEnabled(false);

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

        remoteRender = (SurfaceViewRenderer) findViewById(R.id.remote_video_view);
        remoteRenderLayout = (PercentFrameLayout) findViewById(R.id.remote_video_layout);

        remoteRenderLayout.setPosition(0, 0, 100, 100);
        remoteRender.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);
        remoteRender.setMirror(false);
        remoteRender.requestLayout();
    }

    private void onStopped(String streamStatus) {
        mStatusView.setText(streamStatus);
        mStartButton.setText(R.string.action_start);
        mStartButton.setTag(R.string.action_start);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (session != null) {
            session.disconnect();
        }
    }
}

