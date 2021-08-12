package com.flashphoner.wcsexample.mediadevices;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import com.flashphoner.fpwcsapi.FPSurfaceViewRenderer;
import com.flashphoner.fpwcsapi.Flashphoner;
import com.flashphoner.fpwcsapi.bean.Connection;
import com.flashphoner.fpwcsapi.bean.Data;
import com.flashphoner.fpwcsapi.bean.StreamEvent;
import com.flashphoner.fpwcsapi.bean.StreamStatus;
import com.flashphoner.fpwcsapi.constraints.AudioConstraints;
import com.flashphoner.fpwcsapi.constraints.Constraints;
import com.flashphoner.fpwcsapi.constraints.VideoConstraints;
import com.flashphoner.fpwcsapi.handler.CameraSwitchHandler;
import com.flashphoner.fpwcsapi.layout.PercentFrameLayout;
import com.flashphoner.fpwcsapi.session.ConnectionQualityCallback;
import com.flashphoner.fpwcsapi.session.Session;
import com.flashphoner.fpwcsapi.session.SessionEvent;
import com.flashphoner.fpwcsapi.session.SessionOptions;
import com.flashphoner.fpwcsapi.session.Stream;
import com.flashphoner.fpwcsapi.session.StreamEventHandler;
import com.flashphoner.fpwcsapi.session.StreamOptions;
import com.flashphoner.fpwcsapi.session.Transport;
import com.flashphoner.fpwcsapi.webrtc.MediaDevice;
import com.flashphoner.fpwcsapi.ws.ConnectionQuality;
import com.satsuware.usefulviews.LabelledSpinner;

import org.webrtc.RendererCommon;

import java.util.TimerTask;

/**
 * Example of media device manager.
 * Can be used as streamer allowing to select source camera and microphone and specify parameters for the published video: FPS (Frames Per Second) and resolution (width, height).
 */
public class MediaDevicesActivity extends AppCompatActivity {

    private static String TAG = MediaDevicesActivity.class.getName();

    private static final int PUBLISH_REQUEST_CODE = 100;
    private static final int TEST_REQUEST_CODE = 101;

    // UI references.
    private EditText mWcsUrlView;
    private EditText mStreamNameView;
    private TextView mStatusView;
    private CheckBox mSendAudio;
    private Switch mMuteAudio;
    private Switch mMuteVideo;
    private LabelledSpinner mMicSpinner;
    private TextView mMicLevel;
    private SoundMeter soundMeter;
    private LabelledSpinner mCameraSpinner;
    private LabelledSpinner mStripStreamerCodec;
    private LabelledSpinner mStripPlayerCodec;
    private EditText mCameraFPS;
    private EditText mWidth;
    private EditText mHeight;
    private CheckBox mDefaultPublishVideoBitrate;
    private CheckBox mDefaultPublishAudioBitrate;
    private EditText mPublishVideoBitrate;
    private EditText mPublishAudioBitrate;
    private CheckBox mUseFEC;
    private CheckBox mUseStereo;
    private CheckBox mSendVideo;
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
    private LabelledSpinner mAudioOutput;
    private LabelledSpinner mTransportOutput;
    private LabelledSpinner mTransportInput;
    private CheckBox mTrustAllCer;

    private Button mTestButton;
    private Button mConnectButton;
    private Button mPublishButton;
    private Button mPlayButton;
    private Button mSwitchCameraButton;
    private Button mSwitchRendererButton;

    private TextView mUpdateQualityStatus;
    private TextView mDownloadQualityStatus;

    private Session session;

    private Stream publishStream;
    private Stream playStream;

    private FPSurfaceViewRenderer localRender;
    private TextView mLocalResolutionView;
    private FPSurfaceViewRenderer remoteRender;
    private TextView mRemoteResolutionView;
    private FPSurfaceViewRenderer newSurfaceRenderer;
    private Spinner spinner;

    private PercentFrameLayout localRenderLayout;
    private PercentFrameLayout remoteRenderLayout;
    private PercentFrameLayout switchRenderLayout;

    private TextView mAudioMuteStatus;
    private TextView mVideoMuteStatus;
    private TextView mMutedName;

    private boolean isSwitchRemoteRenderer = false;
    private boolean isSwitchLocalRenderer = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_media_devices);

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
        mStreamNameView = (EditText) findViewById(R.id.stream_name);
        mStatusView = (TextView) findViewById(R.id.status);

        mSendAudio = (CheckBox) findViewById(R.id.send_audio);
        /**
         * Method getMediaDevices(), which returns MediaDeviceList object, is used to request list of all available media devices.
         * Then methods MediaDeviceList.getAudioList() and MediaDeviceList.getVideoList() are used to list available microphones and cameras.
         */
        mMicSpinner = (LabelledSpinner) findViewById(R.id.microphone);
        mMicSpinner.setItemsArray(Flashphoner.getMediaDevices().getAudioList());

        mMicLevel = (TextView) findViewById(R.id.microphone_level);

        mCameraSpinner = (LabelledSpinner) findViewById(R.id.camera);
        mCameraSpinner.setItemsArray(Flashphoner.getMediaDevices().getVideoList());

        mStripStreamerCodec = (LabelledSpinner) findViewById(R.id.strip_streamer_codec);
        mStripStreamerCodec.setItemsArray(new String[]{"", "H264", "VP8"});

        mStripPlayerCodec = (LabelledSpinner) findViewById(R.id.strip_player_codec);
        mStripPlayerCodec.setItemsArray(new String[]{"", "H264", "VP8"});

        mCameraFPS = (EditText) findViewById(R.id.camera_fps);
        mWidth = (EditText) findViewById(R.id.camera_width);
        mHeight = (EditText) findViewById(R.id.camera_height);
        mDefaultPublishVideoBitrate = (CheckBox) findViewById(R.id.publish_video_bitrate_default);
        mDefaultPublishVideoBitrate.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                mPublishVideoBitrate.setEnabled(!b);
            }
        });
        mDefaultPublishAudioBitrate = (CheckBox) findViewById(R.id.publish_audio_bitrate_default);
        mDefaultPublishAudioBitrate.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                mPublishAudioBitrate.setEnabled(!b);
            }
        });
        mPublishVideoBitrate = (EditText) findViewById(R.id.publish_video_bitrate);
        mPublishAudioBitrate = (EditText) findViewById(R.id.publish_audio_bitrate);
        mSendVideo = (CheckBox) findViewById(R.id.send_video);
        mUseStereo = (CheckBox) findViewById(R.id.use_stereo);
        mUseFEC = (CheckBox) findViewById(R.id.use_fec);
        mReceiveAudio = (CheckBox) findViewById(R.id.receive_audio);
        mPlayVolume = (SeekBar) findViewById(R.id.play_volume);
        mPlayVolume.setMax(Flashphoner.getMaxVolume());
        mPlayVolume.setProgress(Flashphoner.getVolume());
        mPlayVolume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                if (b) {
                    Flashphoner.setVolume(i);
                }
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
        mAudioOutput = (LabelledSpinner) findViewById(R.id.audio_output);
        mAudioOutput.setOnItemChosenListener(new LabelledSpinner.OnItemChosenListener() {
            @Override
            public void onItemChosen(View labelledSpinner, AdapterView<?> adapterView, View itemView, int position, long id) {
                String audioType = getResources().getStringArray(R.array.audio_output)[position];
                switch (audioType) {
                    case "speakerphone": Flashphoner.getAudioManager().setUseSpeakerPhone(true); break;
                    case "phone":
                        Flashphoner.getAudioManager().setUseBluetoothSco(false);
                        Flashphoner.getAudioManager().setUseSpeakerPhone(false);
                        break;
                    case "bluetooth": Flashphoner.getAudioManager().setUseBluetoothSco(true); break;
                }
            }

            @Override
            public void onNothingChosen(View labelledSpinner, AdapterView<?> adapterView) {

            }
        });

        mTransportOutput = (LabelledSpinner) findViewById(R.id.transport_output);
        mTransportInput = (LabelledSpinner) findViewById(R.id.transport_input);
        mAudioMuteStatus = (TextView) findViewById(R.id.audio_mute_status);
        mVideoMuteStatus = (TextView) findViewById(R.id.video_mute_status);
        mMutedName = (TextView) findViewById(R.id.muted_name);
        mTrustAllCer = (CheckBox) findViewById(R.id.trust_all_certificates_default);

        mConnectButton = (Button) findViewById(R.id.connect_button);
        mConnectButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                connect();
            }
        });

        mPublishButton = (Button) findViewById(R.id.publish_button);
        mPublishButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                publishStream();
            }
        });
        mPublishButton.setEnabled(false);

        mPlayButton = (Button) findViewById(R.id.play_button);
        mPlayButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                playStream();
            }
        });
        mPlayButton.setEnabled(false);

        mTestButton = (Button) findViewById(R.id.test_button);
        mTestButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mTestButton.getTag() == null || Integer.valueOf(R.string.action_test).equals(mTestButton.getTag())) {
                    ActivityCompat.requestPermissions(MediaDevicesActivity.this,
                            new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA},
                            TEST_REQUEST_CODE);
                } else {
                    Flashphoner.releaseLocalMediaAccess();
                    soundMeter.stop();
                    mTestButton.setText(R.string.action_test);
                    mTestButton.setTag(R.string.action_test);
                    onStoppedTest();
                }
            }
        });

        mSwitchCameraButton = (Button) findViewById(R.id.switch_camera_button);
        mSwitchRendererButton = (Button) findViewById(R.id.switch_renderer_button);

        mUpdateQualityStatus = (TextView) findViewById(R.id.upload_quality_status);
        mDownloadQualityStatus = (TextView) findViewById(R.id.download_quality_status);

        /**
         * Connection to server will be established and stream will be published when Start button is clicked.
         */
        mSwitchCameraButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                switchCamera();
            }

        });

        mSwitchRendererButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                switchRender();
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
        mMuteAudio.setEnabled(false);

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
        mMuteVideo.setEnabled(false);

        localRender = (FPSurfaceViewRenderer) findViewById(R.id.local_video_view);
        mLocalResolutionView = (TextView) findViewById(R.id.local_resolution);
        remoteRender = (FPSurfaceViewRenderer) findViewById(R.id.remote_video_view);
        mRemoteResolutionView = (TextView) findViewById(R.id.remote_resolution);
        localRenderLayout = (PercentFrameLayout) findViewById(R.id.local_video_layout);
        switchRenderLayout = (PercentFrameLayout) findViewById(R.id.switch_video_layout);
        remoteRenderLayout = (PercentFrameLayout) findViewById(R.id.remote_video_layout);
        newSurfaceRenderer = (FPSurfaceViewRenderer) findViewById(R.id.new_video_view);

        spinner = (Spinner) findViewById(R.id.spinner);
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<String>(this, R.layout.support_simple_spinner_dropdown_item);
        spinnerAdapter.add("local");
        spinnerAdapter.add("remote");
        spinner.setAdapter(spinnerAdapter);

        localRender.setZOrderMediaOverlay(true);

        remoteRenderLayout.setPosition(0, 0, 100, 100);
        remoteRender.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);
        remoteRender.setMirror(false);
        remoteRender.requestLayout();

        localRenderLayout.setPosition(0, 0, 100, 100);
        localRender.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);
        localRender.setMirror(true);
        localRender.requestLayout();

        switchRenderLayout.setPosition(0, 0, 100, 100);
        newSurfaceRenderer.setZOrderMediaOverlay(true);
        newSurfaceRenderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);
        newSurfaceRenderer.setMirror(true);
        newSurfaceRenderer.requestLayout();
    }

    private void switchRender() {
        if (spinner.getSelectedItemId() == 0){
            if (isSwitchRemoteRenderer) {
                playStream.switchRenderer(remoteRender);
                isSwitchRemoteRenderer = false;
            }
            if (!isSwitchLocalRenderer) {
                publishStream.switchRenderer(newSurfaceRenderer);
                isSwitchLocalRenderer = true;
            } else {
                publishStream.switchRenderer(localRender);
                isSwitchLocalRenderer = false;
            }
        } else {
            if (isSwitchLocalRenderer) {
                publishStream.switchRenderer(localRender);
                isSwitchLocalRenderer = false;
            }
            if (!isSwitchRemoteRenderer) {
                playStream.switchRenderer(newSurfaceRenderer);
                isSwitchRemoteRenderer = true;
            } else {
                playStream.switchRenderer(remoteRender);
                isSwitchRemoteRenderer = false;
            }
        }
    }

    private void switchCamera() {
        if (publishStream != null) {
            muteButton();
            publishStream.switchCamera(new CameraSwitchHandler() {
                @Override
                public void onCameraSwitchDone(boolean var1) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (mConnectButton.getTag() == null || Integer.valueOf(R.string.action_disconnect).equals(mConnectButton.getTag())) {
                                onConnected();
                                onPublished();
                            }
                        }
                    });

                }

                @Override
                public void onCameraSwitchError(String var1) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            onConnected();
                            onPublished();
                        }
                    });
                }
            });
        }
    }

    /**
     * Connection to server will be established and stream will be published when Start button is clicked.
     */
    private void connect() {
        mConnectButton.setEnabled(false);
        mTestButton.setEnabled(false);
        if (mConnectButton.getTag() == null || Integer.valueOf(R.string.action_connect).equals(mConnectButton.getTag())) {
            initRenderers();

            String url = mWcsUrlView.getText().toString();
            SessionOptions sessionOptions = createSessionOptions(url);

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
                            mPublishButton.setEnabled(true);
                            mPlayButton.setEnabled(true);
                            //onStarted();
                            MediaDevicesActivity.this.onConnected();
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
                            updateQualityStatus(ConnectionQuality.UNKNOWN, mUpdateQualityStatus);
                            updateQualityStatus(ConnectionQuality.UNKNOWN, mDownloadQualityStatus);
                            mStatusView.setText(connection.getStatus());
                            mPublishButton.setEnabled(false);
                            mPlayButton.setEnabled(false);
                            //onStopped();
                            MediaDevicesActivity.this.onDisconnected();
                        }
                    });
                }
            });

            /**
             * Connection to WCS server is established with method Session.connect().
             */
            session.connect(new Connection());

            SharedPreferences sharedPref = MediaDevicesActivity.this.getPreferences(Context.MODE_PRIVATE);
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

    private SessionOptions createSessionOptions(String url) {
        /**
         * The options for connection session are set.
         * WCS server URL is passed when SessionOptions object is created.
         * SurfaceViewRenderer to be used to display video from the camera is set with method SessionOptions.setLocalRenderer().
         * SurfaceViewRenderer to be used to display preview stream video received from the server is set with method SessionOptions.setRemoteRenderer().
         */
        SessionOptions sessionOptions = new SessionOptions(url);
        sessionOptions.setLocalRenderer(localRender);
        sessionOptions.setRemoteRenderer(remoteRender);
        sessionOptions.trustAllCertificates(mTrustAllCer.isChecked());

        return sessionOptions;
    }

    private void initRenderers() {
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
            remoteRender.init(Flashphoner.context, new RendererCommon.RendererEvents() {
                @Override
                public void onFirstFrameRendered() {
                }

                @Override
                public void onFrameResolutionChanged(final int i, final int i1, int i2) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mRemoteResolutionView.setText(i + "x" + i1);
                        }
                    });
                }
            });
            newSurfaceRenderer.init(Flashphoner.context, new RendererCommon.RendererEvents() {
                @Override
                public void onFirstFrameRendered() {
                }

                @Override
                public void onFrameResolutionChanged(final int i, final int i1, int i2) {
                }
            });
        } catch (IllegalStateException e) {
            //ignore
        }
    }

    private void publishStream() {
        mPublishButton.setEnabled(false);
        if (mPublishButton.getTag() == null || Integer.valueOf(R.string.action_publish).equals(mPublishButton.getTag())) {
            /**
             * The options for the stream to publish are set.
             * The stream name is passed when StreamOptions object is created.
             * VideoConstraints object is used to set the source camera, FPS and resolution.
             * Stream constraints are set with method StreamOptions.setConstraints().
             */
            final String streamName = mStreamNameView.getText().toString();
            StreamOptions streamOptions = new StreamOptions(streamName);
            Constraints constraints = getConstraints();
            streamOptions.setConstraints(constraints);
            String[] stripCodec = {(String) mStripStreamerCodec.getSpinner().getSelectedItem()};
            streamOptions.setStripCodecs(stripCodec);
            streamOptions.setTransport(Transport.valueOf(mTransportOutput.getSpinner().getSelectedItem().toString()));

            /**
             * Stream is created with method Session.createStream().
             */
            publishStream = session.createStream(streamOptions);
            publishStream.enableConnectionQualityCalculation(true);
            publishStream.setConnectionQualityCallback(new ConnectionQualityCallback() {
                @Override
                public void onVideoRateStat(ConnectionQuality quality, double clientRate, double serverRate) {
                    updateQualityStatus(quality, mUpdateQualityStatus);
                }
            });
            /**
             * Callback function for stream status change is added to play the stream when it is published.
             */
            publishStream.on(new StreamEventHandler() {
                @Override
                public void onStreamStatus(final Stream stream, final StreamStatus streamStatus) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (StreamStatus.PUBLISHING.equals(streamStatus)) {
                                onPublished();
                            } else {
                                onUnpublished();
                                Log.e(TAG, "Can not publish stream " + stream.getName() + " " + streamStatus);
                            }
                            mStatusView.setText(streamStatus.toString());
                        }
                    });
                }

                @Override
                public void onStreamEvent(StreamEvent streamEvent) {

                }
            });

            ActivityCompat.requestPermissions(MediaDevicesActivity.this,
                    new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA},
                    PUBLISH_REQUEST_CODE);
        } else {
            /**
             * Method Stream.stop() is called to stop playback of the stream.
             */
            publishStream.stop();
            publishStream = null;
        }
    }

    private void playStream() {
        mPlayButton.setEnabled(false);
        if (mPlayButton.getTag() == null || Integer.valueOf(R.string.action_play).equals(mPlayButton.getTag())) {
            /**
             * The options for the stream to play are set.
             * The stream name is passed when StreamOptions object is created.
             */
            final String streamName = mStreamNameView.getText().toString();
            StreamOptions streamOptions = new StreamOptions(streamName);

            streamOptions.setConstraints(new Constraints(mReceiveAudio.isChecked(), mReceiveVideo.isChecked()));

            VideoConstraints videoConstraints = null;
            if (mReceiveVideo.isChecked()) {
                videoConstraints = new VideoConstraints();
                if (!mDefaultPlayResolution.isChecked() && mPlayWidth.getText().length() > 0 && mPlayHeight.getText().length() > 0) {
                    videoConstraints.setResolution(Integer.parseInt(mPlayWidth.getText().toString()),
                            Integer.parseInt(mPlayHeight.getText().toString()));
                }
                if (!mDefaultPlayBitrate.isChecked() && mPlayBitrate.getText().length() > 0) {
                    videoConstraints.setBitrate(Integer.parseInt(mPlayBitrate.getText().toString()));
                }
                if (!mDefaultPlayQuality.isChecked() && mPlayQuality.getText().length() > 0) {
                    videoConstraints.setQuality(Integer.parseInt(mPlayQuality.getText().toString()));
                }

            }
            AudioConstraints audioConstraints = null;
            if (mReceiveAudio.isChecked()) {
                audioConstraints = new AudioConstraints();
            }
            streamOptions.setConstraints(new Constraints(audioConstraints, videoConstraints));
            String[] stripCodec = {(String) mStripPlayerCodec.getSpinner().getSelectedItem()};
            streamOptions.setStripCodecs(stripCodec);
            streamOptions.setTransport(Transport.valueOf(mTransportInput.getSpinner().getSelectedItem().toString()));
            /**
             * Stream is created with method Session.createStream().
             */
            playStream = session.createStream(streamOptions);

            playStream.enableConnectionQualityCalculation(true);
            playStream.setConnectionQualityCallback(new ConnectionQualityCallback() {
                @Override
                public void onVideoRateStat(ConnectionQuality quality, double clientRate, double serverRate) {
                    updateQualityStatus(quality, mDownloadQualityStatus);
                }
            });

            /**
             * Callback function for stream status change is added to display the status.
             */
            playStream.on(new StreamEventHandler() {
                @Override
                public void onStreamStatus(final Stream stream, final StreamStatus streamStatus) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (!StreamStatus.PLAYING.equals(streamStatus)) {
                                onStoppedPlay();
                                Log.e(TAG, "Can not play stream " + stream.getName() + " " + streamStatus);
                            } else {
                                onPlayed();
                                Flashphoner.setVolume(mPlayVolume.getProgress());
                            }
                            mStatusView.setText(streamStatus.toString());
                        }
                    });
                }

                @Override
                public void onStreamEvent(final StreamEvent streamEvent) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (streamEvent.getPayload() != null) {
                                mMutedName.setText(getString(R.string.muted_name) + streamEvent.getPayload().getStreamName());
                            }
                            switch (streamEvent.getType()) {
                                case audioMuted: mAudioMuteStatus.setText(getString(R.string.audio_mute_status)+"true"); break;
                                case audioUnmuted: mAudioMuteStatus.setText(getString(R.string.audio_mute_status)+"false"); break;
                                case videoMuted: mVideoMuteStatus.setText(getString(R.string.video_mute_status)+"true"); break;
                                case videoUnmuted: mVideoMuteStatus.setText(getString(R.string.video_mute_status)+"false");
                            }
                        }
                    });
                }
            });

            /**
             * Method Stream.play() is called to start playback of the stream.
             */
            playStream.play();
        } else {
            /**
             * Method Stream.stop() is called to stop playback of the stream.
             */
            playStream.stop();
            playStream = null;
        }
    }

    private void updateQualityStatus(final ConnectionQuality quality, final TextView textView) {
        final int color;
        switch (quality) {
            case BAD: color = Color.RED; break;
            case GOOD: color = Color.YELLOW; break;
            case PERFECT: color = Color.GREEN; break;
            case UPDATE:
            case UNKNOWN:
            default: color = Color.LTGRAY;
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                textView.setText(quality.toString());
                textView.setTextColor(color);
            }
        });
    }

    private Stream getPlayStream() {
        return playStream;
    }

    private void muteButton() {
        mConnectButton.setEnabled(false);
        mPublishButton.setEnabled(false);
        mPlayButton.setEnabled(false);
        mTestButton.setEnabled(false);
        mSwitchCameraButton.setEnabled(false);
        mSwitchRendererButton.setEnabled(false);
    }

    private void onConnected() {
        mConnectButton.setText(R.string.action_disconnect);
        mConnectButton.setTag(R.string.action_disconnect);
        mConnectButton.setEnabled(true);
        mPublishButton.setEnabled(true);
        mPlayButton.setEnabled(true);
    }

    private void onDisconnected() {
        mConnectButton.setText(R.string.action_connect);
        mConnectButton.setTag(R.string.action_connect);
        mConnectButton.setEnabled(true);
        mTestButton.setEnabled(true);
        onUnpublished();
        onStoppedPlay();
        mPublishButton.setEnabled(false);
        mPlayButton.setEnabled(false);
    }

    private void onPublished() {
        mPublishButton.setText(R.string.action_unpublish);
        mPublishButton.setTag(R.string.action_unpublish);
        mPublishButton.setEnabled(true);
        mMuteAudio.setEnabled(true);
        mMuteVideo.setEnabled(true);
        if (mSendVideo.isChecked()) {
            mSwitchRendererButton.setEnabled(true);
            mSwitchCameraButton.setEnabled(true);
        }
    }

    private void onUnpublished() {
        mPublishButton.setText(R.string.action_publish);
        mPublishButton.setTag(R.string.action_publish);
        mPublishButton.setEnabled(true);
        mMuteAudio.setEnabled(false);
        mMuteVideo.setEnabled(false);
        mSwitchRendererButton.setEnabled(false);
        mSwitchCameraButton.setEnabled(false);
        mMuteAudio.setChecked(false);
        mMuteVideo.setChecked(false);
    }

    private void onPlayed() {
        mPlayButton.setText(R.string.action_stop_play);
        mPlayButton.setTag(R.string.action_stop_play);
        mPlayButton.setEnabled(true);
    }

    private void onStoppedPlay() {
        mPlayButton.setText(R.string.action_play);
        mPlayButton.setTag(R.string.action_play);
        mPlayButton.setEnabled(true);
        mMutedName.setText(getString(R.string.muted_name));
        mAudioMuteStatus.setText(getString(R.string.audio_mute_status));
        mVideoMuteStatus.setText(getString(R.string.video_mute_status));
    }

    private void onStoppedTest() {
        mConnectButton.setText(R.string.action_connect);
        mConnectButton.setTag(R.string.action_connect);
        mConnectButton.setEnabled(true);
        mTestButton.setEnabled(true);
        mSwitchCameraButton.setEnabled(false);
        mSwitchRendererButton.setEnabled(false);
        mMuteAudio.setEnabled(false);
        mMuteAudio.setChecked(false);
        mMuteVideo.setEnabled(false);
        mMuteVideo.setChecked(false);
    }

    @NonNull
    private Constraints getConstraints() {
        AudioConstraints audioConstraints = null;
        if (mSendAudio.isChecked()) {
            audioConstraints = new AudioConstraints();
            if (mUseFEC.isChecked()) {
                audioConstraints.setUseFEC(true);
            }
            if (mUseStereo.isChecked()) {
                audioConstraints.setUseStereo(true);
            }
            if (!mDefaultPublishAudioBitrate.isChecked() && mDefaultPublishAudioBitrate.getText().length() > 0) {
                audioConstraints.setBitrate(Integer.parseInt(mPublishAudioBitrate.getText().toString()));
            }
        }
        VideoConstraints videoConstraints = null;
        if (mSendVideo.isChecked()) {
            videoConstraints = new VideoConstraints();
            videoConstraints.setCameraId(((MediaDevice) mCameraSpinner.getSpinner().getSelectedItem()).getId());
            if (mCameraFPS.getText().length() > 0) {
                videoConstraints.setVideoFps(Integer.parseInt(mCameraFPS.getText().toString()));
            }
            if (mWidth.getText().length() > 0 && mHeight.getText().length() > 0) {
                videoConstraints.setResolution(Integer.parseInt(mWidth.getText().toString()),
                        Integer.parseInt(mHeight.getText().toString()));
            }
            if (!mDefaultPublishVideoBitrate.isChecked() && mPublishVideoBitrate.getText().length() > 0) {
                videoConstraints.setBitrate(Integer.parseInt(mPublishVideoBitrate.getText().toString()));
            }
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
                    mTestButton.setText(R.string.action_release);
                    mTestButton.setTag(R.string.action_release);
                    soundMeter = new SoundMeter();
                    soundMeter.start();
                    soundMeter.getTimer().scheduleAtFixedRate(new TimerTask() {
                        @Override
                        public void run() {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    String text = "Level: " + Math.floor(soundMeter.getAmplitude() * 10);
                                    mMicLevel.setText(text);
                                }
                            });
                        }
                    }, 0, 300);
                    mTestButton.setEnabled(true);
                    Log.i(TAG, "Permission has been granted by user");
                }
                break;
            }
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        int currentVolume = Flashphoner.getVolume();
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                if (currentVolume == 1) {
                    Flashphoner.setVolume(0);
                }
                mPlayVolume.setProgress(currentVolume-1);
                break;
            case KeyEvent.KEYCODE_VOLUME_UP:
                if (currentVolume == 0) {
                    Flashphoner.setVolume(1);
                }
                mPlayVolume.setProgress(currentVolume+1);
                break;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (session != null) {
            session.disconnect();
        }
    }
}