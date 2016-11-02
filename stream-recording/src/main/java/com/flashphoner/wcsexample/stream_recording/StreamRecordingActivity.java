package com.flashphoner.wcsexample.stream_recording;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.util.Linkify;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.MediaController;
import android.widget.TextView;
import android.widget.VideoView;

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

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Stream recorder example.
 * Can be used to publish and record WebRTC stream on Web Call Server.
 */
public class StreamRecordingActivity extends AppCompatActivity {

    private static String TAG = StreamRecordingActivity.class.getName();

    private static final int PUBLISH_REQUEST_CODE = 100;

    // UI references.
    private EditText mWcsUrlView;
    private TextView mStatusView;
    private Button mStartButton;

    private Session session;

    private Stream publishStream;

    private URI uri;
    private String recordFilename;

    private SurfaceViewRenderer localRender;
    private PercentFrameLayout localRenderLayout;

    private TextView mRecordedLink;
    private VideoView mRecordedVideoView;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stream_recording);

        /**
         * Initialization of the API.
         */
        Flashphoner.init(this);

        mWcsUrlView = (EditText) findViewById(R.id.wcs_url);
        SharedPreferences sharedPref = this.getPreferences(Context.MODE_PRIVATE);
        mWcsUrlView.setText(sharedPref.getString("wcs_url", getString(R.string.wcs_url)));
        mStatusView = (TextView) findViewById(R.id.status);
        mStartButton = (Button) findViewById(R.id.connect_button);

        /**
         * Connection to server will be established and stream will be published when Record button is clicked.
         */
        mStartButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mStartButton.getTag() == null || Integer.valueOf(R.string.action_start).equals(mStartButton.getTag())) {
                    String url;
                    final String streamName;
                    try {
                        uri = new URI(mWcsUrlView.getText().toString());
                        url = uri.getScheme() + "://" + uri.getHost() + ":" + uri.getPort();
                        streamName = uri.getPath().replaceAll("/", "");
                    } catch (URISyntaxException e) {
                        mStatusView.setText("Wrong uri");
                        return;
                    }

                    /**
                     * The options for connection session are set.
                     * WCS server URL is passed when SessionOptions object is created.
                     * SurfaceViewRenderer to be used to display video from the camera is set with method SessionOptions.setLocalRenderer().
                     */
                    SessionOptions sessionOptions = new SessionOptions(url);
                    sessionOptions.setLocalRenderer(localRender);

                    /**
                     * Session for connection to WCS server is created with method createSession().
                     */
                    session = Flashphoner.createSession(sessionOptions);

                    /**
                     * Callback functions for session status events are added to make appropriate changes in controls of the interface and publish stream when connection is established.
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
                                     * The options for the stream to publish are set.
                                     * The stream name is passed when StreamOptions object is created.
                                     * To enable stream recording, option 'record' is set to 'true' with method StreamOptions.setRecord().
                                     */
                                    StreamOptions streamOptions = new StreamOptions(streamName);
                                    streamOptions.setRecord(true);

                                    /**
                                     * Stream is created with method Session.createStream().
                                     */
                                    publishStream = session.createStream(streamOptions);

                                    /**
                                     * Callback function for stream status change is added to display the status.
                                     */
                                    publishStream.on(new StreamStatusEvent() {
                                        @Override
                                        public void onStreamStatus(final Stream stream, final StreamStatus streamStatus) {
                                            runOnUiThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    if (StreamStatus.PUBLISHING.equals(streamStatus)) {
                                                        mStatusView.setText("RECORDING");

                                                        /**
                                                         * Filename of the recording is determined.
                                                         */
                                                        recordFilename = stream.getRecordName();
                                                        return;
                                                    } else if (StreamStatus.FAILED.equals(streamStatus)) {
                                                        Log.e(TAG, "Can not publish stream " + stream.getName() + " " + streamStatus);
                                                        recordFilename = null;
                                                    }
                                                    mStatusView.setText(streamStatus.toString());
                                                }
                                            });
                                        }
                                    });

                                    ActivityCompat.requestPermissions(StreamRecordingActivity.this,
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
                                    mStartButton.setText(R.string.action_start);
                                    mStartButton.setTag(R.string.action_start);
                                    mStartButton.setEnabled(true);
                                    mStatusView.setText(connection.getStatus());

                                    /**
                                     * After disconnection, download link for the recording of the published stream is displayed, and the recording can be played in the media player of the application.
                                     */
                                    if (recordFilename != null) {
                                        /**
                                         * Download link is formed.
                                         * Stream recordings are saved to directory WCS_HOME/client/records on the server.
                                         */
                                        String url = "http://" + uri.getHost() +":9091/client/records/" + recordFilename;
                                        mRecordedLink.setText(url);
                                        Linkify.addLinks(mRecordedLink, Linkify.WEB_URLS);

                                        MediaController mediaController = new MediaController(StreamRecordingActivity.this);
                                        mediaController.setAnchorView(mRecordedVideoView);
                                        mRecordedVideoView.setMediaController(mediaController);
                                        mRecordedVideoView.setVideoURI(Uri.parse(url));

                                        /**
                                         * Playback of the recording in the media player is started.
                                         */
                                        mRecordedVideoView.start();
                                    }
                                }
                            });
                        }
                    });
                    mStartButton.setEnabled(false);

                    /**
                     * Connection to WCS server is established with method Session.connect().
                     */
                    session.connect(new Connection());

                    SharedPreferences sharedPref = StreamRecordingActivity.this.getPreferences(Context.MODE_PRIVATE);
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

        localRender = (SurfaceViewRenderer) findViewById(R.id.local_video_view);

        localRenderLayout = (PercentFrameLayout) findViewById(R.id.local_video_layout);
        localRender.setZOrderMediaOverlay(true);
        localRenderLayout.setPosition(0, 0, 100, 100);
        localRender.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);
        localRender.setMirror(true);
        localRender.requestLayout();

        mRecordedLink = (TextView) findViewById(R.id.recorded_link);
        mRecordedVideoView = (VideoView) findViewById(R.id.recorded_video_view);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String permissions[], @NonNull int[] grantResults) {
        switch (requestCode) {
            case PUBLISH_REQUEST_CODE: {
                if (grantResults.length == 0 ||
                        grantResults[0] != PackageManager.PERMISSION_GRANTED ||
                        grantResults[1] != PackageManager.PERMISSION_GRANTED) {
                    mStartButton.setEnabled(false);
                    session.disconnect();
                    Log.i(TAG, "Permission has been denied by user");
                } else {
                    /**
                     * Method Stream.publish() is called to publish stream.
                     */
                    publishStream.publish();
                    Log.i(TAG, "Permission has been granted by user");
                }
            }
        }
    }

}

