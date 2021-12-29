package com.example.gpu_image;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.flashphoner.fpwcsapi.FPSurfaceViewRenderer;
import com.flashphoner.fpwcsapi.Flashphoner;
import com.flashphoner.fpwcsapi.bean.Connection;
import com.flashphoner.fpwcsapi.bean.Data;
import com.flashphoner.fpwcsapi.bean.StreamStatus;
import com.flashphoner.fpwcsapi.camera.CameraCapturerFactory;
import com.flashphoner.fpwcsapi.camera.CustomCameraCapturerOptions;
import com.flashphoner.fpwcsapi.constraints.Constraints;
import com.flashphoner.fpwcsapi.layout.PercentFrameLayout;
import com.flashphoner.fpwcsapi.session.Session;
import com.flashphoner.fpwcsapi.session.SessionEvent;
import com.flashphoner.fpwcsapi.session.SessionOptions;
import com.flashphoner.fpwcsapi.session.Stream;
import com.flashphoner.fpwcsapi.session.StreamOptions;
import com.flashphoner.fpwcsapi.session.StreamStatusEvent;

import org.webrtc.CameraVideoCapturer;
import org.webrtc.GPUImageCameraSession;
import org.webrtc.RendererCommon;

public class GPUImageActivity extends AppCompatActivity {

    private static String TAG = GPUImageActivity.class.getName();

    private static final int PUBLISH_REQUEST_CODE = 100;

    // UI references.
    private EditText mWcsUrlView;
    private EditText mStreamNameView;
    private TextView mStatusView;
    private Button mStartButton;
    private CheckBox mUseFilter;

    private FPSurfaceViewRenderer localRender;
    private TextView mLocalResolutionView;
    private FPSurfaceViewRenderer remoteRender;
    private TextView mRemoteResolutionView;

    private PercentFrameLayout localRenderLayout;
    private PercentFrameLayout remoteRenderLayout;

    private Session session;
    private Stream publishStream;
    private Stream playStream;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gpu_image);

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
        mUseFilter = (CheckBox) findViewById(R.id.use_filter);
        mUseFilter.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                GPUImageCameraSession.setUsedFilter(isChecked);
            }
        });
        GPUImageCameraSession.setUsedFilter(mUseFilter.isChecked());

        CameraCapturerFactory.getInstance().setCustomCameraCapturerOptions(createCustomCameraCapturerOptions());
        CameraCapturerFactory.getInstance().setCameraType(CameraCapturerFactory.CameraType.CUSTOM);

        mStartButton = (Button) findViewById(R.id.connect_button);

        mStartButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                muteButton();
                if (mStartButton.getTag() == null || Integer.valueOf(R.string.action_start).equals(mStartButton.getTag())) {
                    String url = mWcsUrlView.getText().toString();
                    final String streamName = mStreamNameView.getText().toString();

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

                    try {
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
                                    mStatusView.setText(connection.getStatus());

                                    /**
                                     * The options for the stream to publish are set.
                                     * The stream name is passed when StreamOptions object is created.
                                     * VideoConstraints object is used to set the source camera, FPS and resolution.
                                     * Stream constraints are set with method StreamOptions.setConstraints().
                                     */
                                    StreamOptions streamOptions = new StreamOptions(streamName);
                                    Constraints constraints = new Constraints(true, true);
                                    streamOptions.setConstraints(constraints);

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
                                                        StreamOptions streamOptions = new StreamOptions(streamName);
                                                        streamOptions.setConstraints(new Constraints(true, true));

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
                                                                            onStopped();
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
                                                        onStopped();
                                                    }
                                                    mStatusView.setText(streamStatus.toString());
                                                }
                                            });
                                        }
                                    });

                                    ActivityCompat.requestPermissions(GPUImageActivity.this,
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

                    SharedPreferences sharedPref = GPUImageActivity.this.getPreferences(Context.MODE_PRIVATE);
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
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
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
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (session != null) {
            session.disconnect();
        }
    }

    private CustomCameraCapturerOptions createCustomCameraCapturerOptions() {
        return new CustomCameraCapturerOptions() {

            private String cameraName;
            private CameraVideoCapturer.CameraEventsHandler eventsHandler;
            private boolean captureToTexture;

            @Override
            public Class<?>[] getCameraConstructorArgsTypes() {
                return new Class<?>[]{String.class, CameraVideoCapturer.CameraEventsHandler.class, boolean.class};
            }

            @Override
            public Object[] getCameraConstructorArgs() {
                return new Object[]{cameraName, eventsHandler, captureToTexture};
            }

            @Override
            public void setCameraName(String cameraName) {
                this.cameraName = cameraName;
            }

            @Override
            public void setEventsHandler(CameraVideoCapturer.CameraEventsHandler eventsHandler) {
                this.eventsHandler = eventsHandler;
            }

            @Override
            public void setCaptureToTexture(boolean captureToTexture) {
                this.captureToTexture = captureToTexture;
            }

            @Override
            public String getCameraClassName() {
                return "org.webrtc.GPUImageCameraCapturer";
            }

            @Override
            public Class<?>[] getEnumeratorConstructorArgsTypes() {
                return new Class[0];
            }

            @Override
            public Object[] getEnumeratorConstructorArgs() {
                return new Object[0];
            }

            @Override
            public String getEnumeratorClassName() {
                return "org.webrtc.GPUImageCameraEnumerator";
            }
        };
    }

    private void muteButton() {
        mStartButton.setEnabled(false);
    }

    private void onStarted() {
        mStartButton.setText(R.string.action_stop);
        mStartButton.setTag(R.string.action_stop);
        mStartButton.setEnabled(true);
    }

    private void onStopped() {
        mStartButton.setText(R.string.action_start);
        mStartButton.setTag(R.string.action_start);
        mStartButton.setEnabled(true);
    }
}