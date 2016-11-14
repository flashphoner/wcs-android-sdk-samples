package com.flashphoner.wcsexample.mediadevices;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.Switch;
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
import com.flashphoner.fpwcsapi.webrtc.MediaDevice;
import com.satsuware.usefulviews.LabelledSpinner;

import org.webrtc.RendererCommon;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Example of media device manager.
 * Can be used as streamer allowing to select source camera and microphone and specify parameters for the published video: FPS (Frames Per Second) and resolution (width, height).
 */
public class MediaDevicesActivity extends AppCompatActivity {

    private static String TAG = MediaDevicesActivity.class.getName();

    private static final int PUBLISH_REQUEST_CODE = 100;

    // UI references.
    private EditText mWcsUrlView;
    private TextView mStatusView;
    private Switch mMuteAudio;
    private Switch mMuteVideo;
    private LabelledSpinner mMicSpinner;
    private LabelledSpinner mCameraSpinner;
    private EditText mCameraFPS;
    private EditText mWidth;
    private EditText mHeight;
    private CheckBox mDefaultPublishBitrate;
    private EditText mPublishBitrate;
    private CheckBox mReceiveAudio;
    private SeekBar mPlayVolume;
    private CheckBox mReceiveVideo;
    private CheckBox mDefaultPlayResolution;
    private EditText mPlayWidth;
    private EditText mPlayHeight;
    private CheckBox mDefaultPlayBitrate;
    private EditText mPlayBitrate;
    private CheckBox mDefaultPlayQuality;
    private EditText mPlayQuality;


    private Button mStartButton;

    private Session session;

    private Stream publishStream;
    private Stream playStream;

    private FPSurfaceViewRenderer localRender;
    private TextView mLocalResolutionView;
    private FPSurfaceViewRenderer remoteRender;
    private TextView mRemoteResolutionView;

    private PercentFrameLayout localRenderLayout;
    private PercentFrameLayout remoteRenderLayout;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_media_devices);

        /**
         * Initialization of the API.
         */
        Flashphoner.init(this);

        mWcsUrlView = (EditText) findViewById(R.id.wcs_url);
        SharedPreferences sharedPref = this.getPreferences(Context.MODE_PRIVATE);
        mWcsUrlView.setText(sharedPref.getString("wcs_url", getString(R.string.wcs_url)));
        mStatusView = (TextView) findViewById(R.id.status);

        /**
         * Method getMediaDevices(), which returns MediaDeviceList object, is used to request list of all available media devices.
         * Then methods MediaDeviceList.getAudioList() and MediaDeviceList.getVideoList() are used to list available microphones and cameras.
         */
        mMicSpinner = (LabelledSpinner) findViewById(R.id.microphone);
        mMicSpinner.setItemsArray(Flashphoner.getMediaDevices().getAudioList());

        mCameraSpinner = (LabelledSpinner) findViewById(R.id.camera);
        mCameraSpinner.setItemsArray(Flashphoner.getMediaDevices().getVideoList());

        mCameraFPS = (EditText) findViewById(R.id.camera_fps);
        mWidth = (EditText) findViewById(R.id.camera_width);
        mHeight = (EditText) findViewById(R.id.camera_height);
        mDefaultPublishBitrate = (CheckBox) findViewById(R.id.publish_bitrate_default);
        mDefaultPublishBitrate.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                mPublishBitrate.setEnabled(!b);
            }
        });
        mPublishBitrate = (EditText) findViewById(R.id.publish_bitrate);
        mReceiveAudio = (CheckBox) findViewById(R.id.receive_audio);
        mPlayVolume = (SeekBar) findViewById(R.id.play_volume);
        mPlayVolume.setMax(Flashphoner.getMaxVolume());
        mPlayVolume.setProgress(Flashphoner.getVolume());
        mPlayVolume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                Flashphoner.setVolume(i);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });
        mReceiveVideo = (CheckBox) findViewById(R.id.receive_video);
        mDefaultPlayResolution = (CheckBox) findViewById(R.id.play_resolution_default);
        mDefaultPlayResolution.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                mPlayWidth.setEnabled(!b);
                mPlayHeight.setEnabled(!b);
            }
        });
        mPlayWidth = (EditText) findViewById(R.id.play_width);
        mPlayHeight = (EditText) findViewById(R.id.play_height);
        mDefaultPlayBitrate = (CheckBox) findViewById(R.id.play_bitrate_default);
        mDefaultPlayBitrate.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                mPlayBitrate.setEnabled(!b);
            }
        });
        mPlayBitrate = (EditText) findViewById(R.id.play_bitrate);
        mDefaultPlayQuality = (CheckBox) findViewById(R.id.play_quality_default);
        mDefaultPlayQuality.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                mPlayQuality.setEnabled(!b);
            }
        });
        mPlayQuality = (EditText) findViewById(R.id.play_quality);
        mStartButton = (Button) findViewById(R.id.connect_button);

        /**
         * Connection to server will be established and stream will be published when Start button is clicked.
         */
        mStartButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mStartButton.getTag() == null || Integer.valueOf(R.string.action_start).equals(mStartButton.getTag())) {
                    String url;
                    final String streamName;
                    try {
                        URI u = new URI(mWcsUrlView.getText().toString());
                        url = u.getScheme() + "://" + u.getHost() + ":" + u.getPort();
                        streamName = u.getPath().replaceAll("/", "");
                    } catch (URISyntaxException e) {
                        mStatusView.setText("Wrong uri");
                        return;
                    }

                    try {
                        localRender.init(null, new RendererCommon.RendererEvents() {
                            @Override
                            public void onFirstFrameRendered() {
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        mLocalResolutionView.setText(localRender.getFrameHeight() + "x" + localRender.getFrameWidth());
                                    }
                                });

                            }

                            @Override
                            public void onFrameResolutionChanged(int i, int i1, int i2) {

                            }
                        });
                    } catch (IllegalStateException e) {
                        //ignore
                    }
                    try {
                        remoteRender.init(null, new RendererCommon.RendererEvents() {
                            @Override
                            public void onFirstFrameRendered() {
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        mRemoteResolutionView.setText(remoteRender.getFrameHeight() + "x" + remoteRender.getFrameWidth());
                                    }
                                });
                            }

                            @Override
                            public void onFrameResolutionChanged(int i, int i1, int i2) {

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
                    sessionOptions.setLocalRenderer(localRender);
                    sessionOptions.setRemoteRenderer(remoteRender);

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
                                     * VideoConstraints object is used to set the source camera, FPS and resolution.
                                     * Stream constraints are set with method StreamOptions.setConstraints().
                                     */
                                    StreamOptions streamOptions = new StreamOptions(streamName);
                                    VideoConstraints videoConstraints = new VideoConstraints();
                                    videoConstraints.setCameraId(((MediaDevice) mCameraSpinner.getSpinner().getSelectedItem()).getId());
                                    videoConstraints.setVideoFps(Integer.parseInt(mCameraFPS.getText().toString()));
                                    videoConstraints.setResolution(Integer.parseInt(mWidth.getText().toString()),
                                            Integer.parseInt(mHeight.getText().toString()));
                                    if (!mDefaultPublishBitrate.isChecked()) {
                                        videoConstraints.setBitrate(Integer.parseInt(mPublishBitrate.getText().toString()));
                                    }
                                    streamOptions.setConstraints(new Constraints(new AudioConstraints(), videoConstraints));

                                    /**
                                     * Stream is created with method Session.createStream().
                                     */
                                    publishStream = session.createStream(streamOptions);
                                    if (mMuteAudio.isChecked()) {
                                        publishStream.muteAudio();
                                    }
                                    if (mMuteVideo.isChecked()) {
                                        publishStream.muteVideo();
                                    }
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
                                                        StreamOptions streamOptions = new StreamOptions(streamName);

                                                        streamOptions.setConstraints(new Constraints(mReceiveAudio.isChecked(), mReceiveVideo.isChecked()));

                                                        VideoConstraints videoConstraints = null;
                                                        if (mReceiveVideo.isChecked()) {
                                                            videoConstraints = new VideoConstraints();
                                                            if (!mDefaultPlayResolution.isChecked()) {
                                                                videoConstraints.setResolution(Integer.parseInt(mPlayWidth.getText().toString()),
                                                                        Integer.parseInt(mPlayHeight.getText().toString()));
                                                            }
                                                            if (!mDefaultPlayBitrate.isChecked()) {
                                                                videoConstraints.setBitrate(Integer.parseInt(mPlayBitrate.getText().toString()));
                                                            }
                                                            if (!mDefaultPlayQuality.isChecked()) {
                                                                videoConstraints.setQuality(Integer.parseInt(mPlayQuality.getText().toString()));
                                                            }

                                                        }
                                                        AudioConstraints audioConstraints = null;
                                                        if (mReceiveAudio.isChecked()) {
                                                            audioConstraints = new AudioConstraints();
                                                        }
                                                        streamOptions.setConstraints(new Constraints(audioConstraints, videoConstraints));

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

                                    ActivityCompat.requestPermissions(MediaDevicesActivity.this,
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
                                }
                            });
                        }
                    });

                    mStartButton.setEnabled(false);

                    /**
                     * Connection to WCS server is established with method Session.connect().
                     */
                    session.connect(new Connection());

                    SharedPreferences sharedPref = MediaDevicesActivity.this.getPreferences(Context.MODE_PRIVATE);
                    SharedPreferences.Editor editor = sharedPref.edit();
                    editor.putString("wcs_url", mWcsUrlView.getText().toString());
                    editor.apply();
                } else

                {
                    mStartButton.setEnabled(false);

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

        /**
         * MuteAudio switch is used to mute/unmute audio of the published stream.
         * Audio is muted with method Stream.muteAudio() and unmuted with method Stream.unmuteAudio().
         */
        mMuteAudio = (Switch) findViewById(R.id.mute_audio);
        mMuteAudio.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (publishStream != null) {
                    if (isChecked) {
                        publishStream.muteAudio();
                    } else {
                        publishStream.unmuteAudio();
                    }
                }
            }
        });

        /**
         * MuteVideo switch is used to mute/unmute video of the published stream.
         * Video is muted with method Stream.muteVideo() and unmuted with method Stream.unmuteVideo().
         */
        mMuteVideo = (Switch) findViewById(R.id.mute_video);
        mMuteVideo.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (publishStream != null) {
                    if (isChecked) {
                        publishStream.muteVideo();
                    } else {
                        publishStream.unmuteVideo();
                    }
                }
            }
        });

        localRender = (FPSurfaceViewRenderer) findViewById(R.id.local_video_view);
        mLocalResolutionView = (TextView) findViewById(R.id.local_resolution);
        remoteRender = (FPSurfaceViewRenderer) findViewById(R.id.remote_video_view);
        mRemoteResolutionView = (TextView) findViewById(R.id.remote_resolution);
        localRenderLayout = (PercentFrameLayout) findViewById(R.id.local_video_layout);
        remoteRenderLayout = (PercentFrameLayout) findViewById(R.id.remote_video_layout);

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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (session != null) {
            session.disconnect();
        }
    }
}

