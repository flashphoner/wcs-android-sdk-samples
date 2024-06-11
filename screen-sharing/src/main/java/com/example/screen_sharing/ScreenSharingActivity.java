package com.example.screen_sharing;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

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
import com.flashphoner.fpwcsapi.webrtc.MediaDevice;
import com.flashphoner.fpwcsapi.webrtc.WebRTCMediaProvider;

import org.webrtc.RendererCommon;
import org.webrtc.ScreenCapturerAndroid;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoCapturer;

import java.net.URI;
import java.net.URISyntaxException;

public class ScreenSharingActivity extends AppCompatActivity {

    private static final String TAG = ScreenSharingActivity.class.getName();

    private static final int NOTIFICATION_REQUEST_CODE = 200;
    private static final int PUBLISH_REQUEST_CODE = 200;
    private static final int REQUEST_CODE_CAPTURE_PERM = 100;

    // UI references.
    private EditText mWcsUrlView;
    private TextView mStatusView;
    private Button mStartButton;
    private CheckBox mUseAudioCheckBox;
    private RadioButton mUseMicRadioButton;

    private Session session;

    private Stream publishStream;
    private Stream playStream;

    private SurfaceViewRenderer localRender;
    private SurfaceViewRenderer remoteRender;
    private Intent serviceIntent;

    private VideoCapturer videoCapturer;

    private Intent mediaProjectionData;

    public MediaProjectionManager mediaProjectionManager;

    private Handler handler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        IntentFilter filter = new IntentFilter();
        filter.addAction(ScreenSharingService.ACTION_START);
        filter.addAction(ScreenSharingService.ACTION_STOP);
        LocalBroadcastManager.getInstance(getApplicationContext()).registerReceiver(mMessageReceiver, filter);

        setContentView(R.layout.activity_screen_sharing);

        TextView policyTextView = findViewById(R.id.privacy_policy);
        policyTextView.setMovementMethod(LinkMovementMethod.getInstance());
        String policyLink = "<a href=https://flashphoner.com/flashphoner-privacy-policy-for-android-tools/>Privacy Policy</a>";
        policyTextView.setText(Html.fromHtml(policyLink));

        /*
         * Initialization of the API.
         */
        Flashphoner.init(this);

        mWcsUrlView = findViewById(R.id.wcs_url);
        SharedPreferences sharedPref = this.getPreferences(Context.MODE_PRIVATE);
        mWcsUrlView.setText(sharedPref.getString("wcs_url", getString(R.string.wcs_url)));
        mStatusView = findViewById(R.id.status);
        mStartButton = findViewById(R.id.connect_button);
        RadioGroup mAudioRadioGroup = findViewById(R.id.audio_radio_group);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            mAudioRadioGroup.setVisibility(View.VISIBLE);
        } else {
            mAudioRadioGroup.setVisibility(View.GONE);

        }
        mUseAudioCheckBox = findViewById(R.id.use_audio);
        mUseAudioCheckBox.setOnClickListener(v -> {
            if (mUseAudioCheckBox.isChecked()) {
                ActivityCompat.requestPermissions(ScreenSharingActivity.this,
                        new String[]{Manifest.permission.RECORD_AUDIO},
                        PUBLISH_REQUEST_CODE);
            }
        });
        mUseMicRadioButton = findViewById(R.id.use_mic);

        Spinner mMicSpinner = findViewById(R.id.spinner_mic);
        ArrayAdapter<MediaDevice> arrayAdapter = new ArrayAdapter<MediaDevice>(this, android.R.layout.simple_spinner_item, Flashphoner.getMediaDevices().getAudioList());
        arrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mMicSpinner.setAdapter(arrayAdapter);

        /*
         * Connection to server will be established and stream will be published when Start button is clicked.
         */
        mStartButton.setOnClickListener(view -> {

            if (mStartButton.getTag() == null || Integer.valueOf(R.string.action_start).equals(mStartButton.getTag())) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    ActivityCompat.requestPermissions(ScreenSharingActivity.this,
                            new String[]{Manifest.permission.POST_NOTIFICATIONS},
                            NOTIFICATION_REQUEST_CODE);
                }
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
                mStartButton.setEnabled(false);

                try {
                    localRender.init(Flashphoner.context, null);
                } catch (IllegalStateException e) {
                    //ignore
                }
                try {
                    remoteRender.init(Flashphoner.context, null);
                } catch (IllegalStateException e) {
                    //ignore
                }

                handler.post(() -> start(url, streamName));
            } else {
                handler.post(this::stop);
            }

            View currentFocus = getCurrentFocus();
            if (currentFocus != null) {
                InputMethodManager inputManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                inputManager.hideSoftInputFromWindow(currentFocus.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
            }
        });

        localRender = findViewById(R.id.local_video_view);
        remoteRender = findViewById(R.id.remote_video_view);

        PercentFrameLayout localRenderLayout = findViewById(R.id.local_video_layout);
        PercentFrameLayout remoteRenderLayout = findViewById(R.id.remote_video_layout);

        localRender.setZOrderMediaOverlay(true);

        remoteRenderLayout.setPosition(0, 0, 100, 100);
        remoteRender.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);
        remoteRender.requestLayout();

        localRenderLayout.setPosition(0, 0, 100, 100);
        localRender.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);
        localRender.requestLayout();
    }

    private void start(String url, String streamName) {
        /*
         * The options for connection session are set.
         * WCS server URL is passed when SessionOptions object is created.
         * SurfaceViewRenderer to be used to display video from the camera is set with method SessionOptions.setLocalRenderer().
         * SurfaceViewRenderer to be used to display preview stream video received from the server is set with method SessionOptions.setRemoteRenderer().
         */
        SessionOptions sessionOptions = new SessionOptions(url);
        sessionOptions.setLocalRenderer(localRender);
        sessionOptions.setRemoteRenderer(remoteRender);

        /*
         * Session for connection to WCS server is created with method createSession().
         */
        session = Flashphoner.createSession(sessionOptions);

        /*
         * Callback functions for session status events are added to make appropriate changes in controls of the interface and publish stream when connection is established.
         */
        session.on(new SessionEvent() {
            @Override
            public void onAppData(Data data) {

            }

            @Override
            public void onConnected(final Connection connection) {
                runOnUiThread(() -> {
                    mStartButton.setText(R.string.action_stop);
                    mStartButton.setTag(R.string.action_stop);
                    mStatusView.setText(connection.getStatus());
                });
                /*
                 * The options for the stream to publish are set.
                 * The stream name is passed when StreamOptions object is created.
                 */
                StreamOptions streamOptions = new StreamOptions(streamName);
                VideoConstraints videoConstraints = new VideoConstraints();
                DisplayMetrics metrics = getResources().getDisplayMetrics();
                videoConstraints.setResolution(metrics.widthPixels, metrics.heightPixels);
                videoConstraints.setVideoFps(metrics.densityDpi);
                streamOptions.getConstraints().setVideoConstraints(videoConstraints);
                streamOptions.getConstraints().updateAudio(mUseAudioCheckBox.isChecked());

                /*
                 * Stream is created with method Session.createStream().
                 */
                publishStream = session.createStream(streamOptions);

                /*
                 * Callback function for stream status change is added to play the stream when it is published.
                 */
                publishStream.on((stream, streamStatus) -> {
                    if (StreamStatus.PUBLISHING.equals(streamStatus)) {

                        /*
                         * The options for the stream to play are set.
                         * The stream name is passed when StreamOptions object is created.
                         */
                        StreamOptions streamOptions1 = new StreamOptions(streamName);
                        streamOptions1.getConstraints().updateAudio(mUseAudioCheckBox.isChecked());

                        /*
                         * Stream is created with method Session.createStream().
                         */
                        playStream = session.createStream(streamOptions1);

                        /*
                         * Callback function for stream status change is added to display the status.
                         */
                        playStream.on((stream1, streamStatus1) -> runOnUiThread(() -> {
                            if (!StreamStatus.PLAYING.equals(streamStatus1)) {
                                Log.e(TAG, "Can not play stream " + stream1.getName() + " " + streamStatus1);
                            }
                            mStartButton.setEnabled(true);
                            mStatusView.setText(streamStatus1.toString());
                        }));

                        /*
                         * Method Stream.play() is called to start playback of the stream.
                         */
                        playStream.play();
                    } else {
                        stop();
                        Log.e(TAG, "Can not publish stream " + stream.getName() + " " + streamStatus);
                    }
                    runOnUiThread(() -> mStatusView.setText(streamStatus.toString()));
                });

                startScreenCapture();
            }

            @Override
            public void onRegistered(Connection connection) {
            }

            @Override
            public void onDisconnection(final Connection connection) {
                handler.post(() -> stop());
            }
        });

        /*
         * Connection to WCS server is established with method Session.connect().
         */
        session.connect(new Connection());

        SharedPreferences sharedPref1 = ScreenSharingActivity.this.getPreferences(Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref1.edit();
        editor.putString("wcs_url", mWcsUrlView.getText().toString());
        editor.apply();
    }

    private synchronized void stop() {
        if (session != null) {
            session.disconnect();
            session = null;
        }

        WebRTCMediaProvider.getInstance().releaseLocalMediaAccess();

        if (serviceIntent != null) {
            stopService(serviceIntent);
            this.serviceIntent = null;
        }

        runOnUiThread(() -> {
            this.localRender.release();
            this.localRender.clearImage();
            this.remoteRender.release();
            this.remoteRender.clearImage();

            mStartButton.setText(R.string.action_start);
            mStartButton.setTag(R.string.action_start);
            mStartButton.setEnabled(true);
            mStatusView.setText("");
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PUBLISH_REQUEST_CODE) {
            if (grantResults.length == 0 ||
                    grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                mUseAudioCheckBox.setChecked(false);
                Log.i(TAG, "Permission has been denied by user");
            } else {
                Log.i(TAG, "Permission has been granted by user");
            }
        }
    }

    private void startScreenCapture() {
        this.mediaProjectionManager = (MediaProjectionManager) getSystemService(
                Context.MEDIA_PROJECTION_SERVICE);
        Intent permissionIntent = this.mediaProjectionManager.createScreenCaptureIntent();
        startActivityForResult(permissionIntent, REQUEST_CODE_CAPTURE_PERM);
    }

    private final BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null) {
                if (ScreenSharingService.ACTION_START.equals(intent.getAction())) {
                    MediaProjection mediaProjection = null;
                    if (mUseAudioCheckBox.isChecked() && !mUseMicRadioButton.isChecked() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        mediaProjection = mediaProjectionManager.getMediaProjection(Activity.RESULT_OK, mediaProjectionData);
                    }

                    WebRTCMediaProvider.getInstance().setMediaProjection(mediaProjection);
                    videoCapturer = new ScreenCapturerAndroid(mediaProjection, mediaProjectionData, new MediaProjection.Callback() {
                        @Override
                        public void onStop() {
                            super.onStop();
                            handler.post(ScreenSharingActivity.this::stop);
                        }
                    });
                    WebRTCMediaProvider.getInstance().setVideoCapturer(videoCapturer);

                    publishStream.publish();
                } else if (ScreenSharingService.ACTION_STOP.equals(intent.getAction())) {
                    handler.post(ScreenSharingActivity.this::stop);
                }
            }
        }
    };

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (REQUEST_CODE_CAPTURE_PERM == requestCode && resultCode == RESULT_OK) {

            this.mediaProjectionData = data;

            Context context = getApplicationContext();
            this.serviceIntent = new Intent(context, ScreenSharingService.class);
            context.startForegroundService(serviceIntent);
        } else {
            runOnUiThread(() -> mStartButton.setEnabled(false));
            stop();
            Log.i(TAG, "Permission has been denied by user");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stop();
    }
}
