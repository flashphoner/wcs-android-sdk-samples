package com.flashphoner.wcsexample.mcu_client;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

import com.flashphoner.fpwcsapi.FPSurfaceViewRenderer;
import com.flashphoner.fpwcsapi.Flashphoner;
import com.flashphoner.fpwcsapi.bean.Connection;
import com.flashphoner.fpwcsapi.bean.Data;
import com.flashphoner.fpwcsapi.bean.StreamStatus;
import com.flashphoner.fpwcsapi.constraints.AudioConstraints;
import com.flashphoner.fpwcsapi.constraints.Constraints;
import com.flashphoner.fpwcsapi.constraints.VideoConstraints;
import com.flashphoner.fpwcsapi.layout.PercentFrameLayout;
import com.flashphoner.fpwcsapi.session.Session;
import com.flashphoner.fpwcsapi.session.SessionEvent;
import com.flashphoner.fpwcsapi.session.SessionOptions;
import com.flashphoner.fpwcsapi.session.Stream;
import com.flashphoner.fpwcsapi.session.StreamOptions;
import com.flashphoner.fpwcsapi.session.StreamStatusEvent;
import com.flashphoner.fpwcsapi.session.Transport;
import com.satsuware.usefulviews.LabelledSpinner;

import org.webrtc.RendererCommon;

/**
 * Example of media device manager.
 * Can be used as streamer allowing to select source camera and microphone and specify parameters for the published video: FPS (Frames Per Second) and resolution (width, height).
 */
public class McuClientActivity extends AppCompatActivity {

    private static String TAG = McuClientActivity.class.getName();

    private static final int PUBLISH_REQUEST_CODE = 100;
    private static final int TEST_REQUEST_CODE = 101;

    // UI references.
    private EditText mWcsUrlView;
    private EditText mLoginView;
    private EditText mRoomView;
    private TextView mStatusView;
    private CheckBox mSendAudio;
    private CheckBox mSendVideo;
    private LabelledSpinner mTransportOutput;
    private Button mStartButton;
    private Session session;
    private Stream publishStream;
    private Stream playStream;
    private FPSurfaceViewRenderer localRender;
    private TextView mLocalResolutionView;
    private PercentFrameLayout localRenderLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mcu_client);

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
        mLoginView = (EditText) findViewById(R.id.login);
        mRoomView = (EditText) findViewById(R.id.room_name);
        mStatusView = (TextView) findViewById(R.id.status);
        mSendAudio = (CheckBox) findViewById(R.id.send_audio);
        mSendVideo = (CheckBox) findViewById(R.id.send_video);
        mStartButton = (Button) findViewById(R.id.connect_button);
        mTransportOutput = (LabelledSpinner) findViewById(R.id.transport_output);

        /**
         * Connection to server will be established and stream will be published when Start button is clicked.
         */
        mStartButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                muteButton();
                if (mStartButton.getTag() == null || Integer.valueOf(R.string.action_start).equals(mStartButton.getTag())) {
                    String url = mWcsUrlView.getText().toString();
                    String login = mLoginView.getText().toString();
                    String roomName = mRoomView.getText().toString();
                    String publishStreamName = login + "#" + roomName;

                    try {
                        localRender.init(Flashphoner.context, new RendererCommon.RendererEvents() {
                            @Override
                            public void onFirstFrameRendered() {
                            }

                            @Override
                            public void onFrameResolutionChanged(final int i, final int i1, int i2) {
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        mLocalResolutionView.setText(i + "x" + i1);
                                    }
                                });
                            }
                        });
                    } catch (IllegalStateException e) {
                        //ignore
                    }


                    /**
                     * The options for connection session are set.
                     * WCS server URL is passed when SessionOptions object is created.
                     * SurfaceViewRenderer to be used to display video from the camera is set with method SessionOptions.setLocalRenderer().
                     * SurfaceViewRenderer to be used to display preview stream video received from the server is set with method SessionOptions.setRemoteRenderer().
                     */
                    SessionOptions sessionOptions = new SessionOptions(url);
                    //sessionOptions.setLocalRenderer(localRender);
                    sessionOptions.setRemoteRenderer(localRender);

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
                                    mStatusView.setText(connection.getStatus());

                                    /**
                                     * The options for the stream to publish are set.
                                     * The stream name is passed when StreamOptions object is created.
                                     * VideoConstraints object is used to set the source camera, FPS and resolution.
                                     * Stream constraints are set with method StreamOptions.setConstraints().
                                     */
                                    StreamOptions streamOptions = new StreamOptions(publishStreamName);
                                    Constraints constraints = getConstraints();
                                    streamOptions.setConstraints(constraints);
                                    streamOptions.setTransport(Transport.valueOf(mTransportOutput.getSpinner().getSelectedItem().toString()));

                                    /**
                                     * Stream is created with method Session.createStream().
                                     */
                                    publishStream = session.createStream(streamOptions);

                                    /**
                                     * Callback function for stream status change is added to play the stream when it is published.
                                     */
                                    publishStream.on(new StreamStatusEvent() {
                                        @Override
                                        public void onStreamStatus(final Stream stream, final StreamStatus streamStatus) {
                                            runOnUiThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    if (StreamStatus.PUBLISHING.equals(streamStatus)) {
                                                        /**
                                                         * The options for the stream to play are set.
                                                         * The stream name is passed when StreamOptions object is created.
                                                         */
                                                        String playStreamName = roomName + "-" + login + roomName;
                                                        StreamOptions streamOptions = new StreamOptions(playStreamName);
                                                        streamOptions.setTransport(Transport.valueOf(mTransportOutput.getSpinner().getSelectedItem().toString()));
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
                                                                        } else {
                                                                            onStarted();
                                                                        }
                                                                        mStatusView.setText(streamStatus.toString());
                                                                    }
                                                                });
                                                            }
                                                        });

                                                        /**
                                                         * Method Stream.play() is called to start playback of the stream.
                                                         */
                                                        playStream.play();
                                                    } else {
                                                        Log.e(TAG, "Can not publish stream " + stream.getName() + " " + streamStatus);
                                                    }
                                                    mStatusView.setText(streamStatus.toString());
                                                }
                                            });
                                        }
                                    });

                                    ActivityCompat.requestPermissions(McuClientActivity.this,
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
                                    mStatusView.setText(connection.getStatus());
                                    mStatusView.setText(connection.getStatus());
                                    onStopped();
                                }
                            });
                        }
                    });

                    /**
                     * Connection to WCS server is established with method Session.connect().
                     */
                    session.connect(new Connection());

                    SharedPreferences sharedPref = McuClientActivity.this.getPreferences(Context.MODE_PRIVATE);
                    SharedPreferences.Editor editor = sharedPref.edit();
                    editor.putString("wcs_url", mWcsUrlView.getText().toString());
                    editor.apply();
                } else {
                    /**
                     * Connection to WCS server is closed with method Session.disconnect().
                     */
                    session.disconnect();
                }

                View currentFocus = getCurrentFocus();
                if (currentFocus != null)

                {
                    InputMethodManager inputManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    inputManager.hideSoftInputFromWindow(currentFocus.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
                }
            }
        });

        localRender = (FPSurfaceViewRenderer) findViewById(R.id.local_video_view);
        mLocalResolutionView = (TextView) findViewById(R.id.local_resolution);
        localRenderLayout = (PercentFrameLayout) findViewById(R.id.local_video_layout);

        localRender.setZOrderMediaOverlay(true);

        localRenderLayout.setPosition(0, 0, 100, 100);
        localRender.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);
        localRender.setMirror(true);
        localRender.requestLayout();
    }

    private void muteButton() {
        mStartButton.setEnabled(false);
        mSendAudio.setEnabled(false);
        mSendVideo.setEnabled(false);
        mTransportOutput.setEnabled(false);
        mWcsUrlView.setEnabled(false);
        mLoginView.setEnabled(false);
        mRoomView.setEnabled(false);
    }

    private void unmuteButton() {
        mStartButton.setEnabled(true);
    }

    private void onStarted() {
        mStartButton.setText(R.string.action_stop);
        mStartButton.setTag(R.string.action_stop);
        unmuteButton();
    }

    private void onStopped() {
        mStartButton.setText(R.string.action_start);
        mStartButton.setTag(R.string.action_start);
        mStartButton.setEnabled(true);
        mSendAudio.setEnabled(true);
        mSendVideo.setEnabled(true);
        mTransportOutput.setEnabled(true);
        mWcsUrlView.setEnabled(true);
        mLoginView.setEnabled(true);
        mRoomView.setEnabled(true);
    }

    @NonNull
    private Constraints getConstraints() {
        AudioConstraints audioConstraints = null;
        if (mSendAudio.isChecked()) {
            audioConstraints = new AudioConstraints();
        }
        VideoConstraints videoConstraints = null;
        if (mSendVideo.isChecked()) {
            videoConstraints = new VideoConstraints();
        }
        return new Constraints(audioConstraints, videoConstraints);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String permissions[], @NonNull int[] grantResults) {
        switch (requestCode) {
            case PUBLISH_REQUEST_CODE: {
                if (grantResults.length == 0 ||
                        grantResults[0] != PackageManager.PERMISSION_GRANTED ||
                        grantResults[1] != PackageManager.PERMISSION_GRANTED) {
                    muteButton();
                    session.disconnect();
                    Log.i(TAG, "Permission has been denied by user");
                } else {
                    /**
                     * Method Stream.publish() is called to publish stream.
                     */
                    publishStream.publish();
                    Log.i(TAG, "Permission has been granted by user");
                }
                break;
            }
            case TEST_REQUEST_CODE: {
                if (grantResults.length == 0 ||
                        grantResults[0] != PackageManager.PERMISSION_GRANTED ||
                        grantResults[1] != PackageManager.PERMISSION_GRANTED) {
                    Log.i(TAG, "Permission has been denied by user");
                } else {
                    muteButton();
                    Flashphoner.getLocalMediaAccess(getConstraints(), localRender);
                    Log.i(TAG, "Permission has been granted by user");
                }
                break;
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