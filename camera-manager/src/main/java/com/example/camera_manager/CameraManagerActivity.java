package com.example.camera_manager;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.Html;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;

import com.flashphoner.fpwcsapi.FPSurfaceViewRenderer;
import com.flashphoner.fpwcsapi.Flashphoner;
import com.flashphoner.fpwcsapi.bean.Connection;
import com.flashphoner.fpwcsapi.bean.Data;
import com.flashphoner.fpwcsapi.bean.StreamStatus;
import com.flashphoner.fpwcsapi.camera.CameraCapturerFactory;
import com.flashphoner.fpwcsapi.camera.CustomCameraCapturerOptions;
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

import org.webrtc.CameraVideoCapturer;
import org.webrtc.FlashlightCameraCapturer;
import org.webrtc.GPUImageCameraCapturer;
import org.webrtc.PngOverlayCameraCapturer;
import org.webrtc.RendererCommon;
import org.webrtc.ZoomCameraCapturer;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.List;

public class CameraManagerActivity extends AppCompatActivity {

    private static String TAG = CameraManagerActivity.class.getName();

    private static final int PUBLISH_REQUEST_CODE = 100;

    // UI references.
    private EditText mWcsUrlView;
    private EditText mStreamNameView;
    private TextView mStatusView;
    private Button mStartButton;
    private CheckBox mUseFilter;
    private CheckBox mUsePngOverlay;
    private SeekBar mZoomSeekBar;
    private Button mSwitchFlashlightButton;
    private LabelledSpinner mCameraCapturer;
    private EditText mPngYPosition;
    private EditText mPngXPosition;
    private EditText mPngWidth;
    private EditText mPngHeight;
    private Button mSelectPngButton;
    private EditText mWidth;
    private EditText mHeight;

    private FPSurfaceViewRenderer localRender;
    private TextView mLocalResolutionView;
    private FPSurfaceViewRenderer remoteRender;
    private TextView mRemoteResolutionView;

    private PercentFrameLayout localRenderLayout;
    private PercentFrameLayout remoteRenderLayout;

    private Session session;
    private Stream publishStream;
    private Stream playStream;

    static final int REQUEST_IMAGE_CAPTURE = 1;

    private boolean flashlight = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera_manager);

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
        mCameraCapturer = (LabelledSpinner) findViewById(R.id.camera_capturer);
        mCameraCapturer.setOnItemChosenListener(new LabelledSpinner.OnItemChosenListener() {
            @Override
            public void onItemChosen(View labelledSpinner, AdapterView<?> adapterView, View itemView, int position, long id) {
                String captureType = getResources().getStringArray(R.array.camera_capturer)[position];
                switch (captureType) {
                    case "Flashlight":
                        changeFlashlightCamera();
                        break;
                    case "Zoom":
                        changeZoomCamera();
                        break;
                    case "GPUImage":
                        changeGpuImageCamera();
                        break;
                    case "PNG overlay":
                        changePngOverlayCamera();
                        break;
                }
            }

            @Override
            public void onNothingChosen(View labelledSpinner, AdapterView<?> adapterView) {

            }
        });
        mWidth = (EditText) findViewById(R.id.camera_width);
        mHeight = (EditText) findViewById(R.id.camera_height);

        mUseFilter = (CheckBox) findViewById(R.id.use_filter);
        mUseFilter.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                prepareGpuImage();
            }
        });

        mUsePngOverlay = (CheckBox) findViewById(R.id.use_png_overlay);
        mUsePngOverlay.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                CameraVideoCapturer cameraVideoCapturer = CameraCapturerFactory.getInstance().getCameraVideoCapturer();
                if (cameraVideoCapturer instanceof PngOverlayCameraCapturer) {
                    checkingSelectedPng();
                    ((PngOverlayCameraCapturer) cameraVideoCapturer).setUsedPngOverlay(isChecked);
                }
            }
        });
        mUsePngOverlay.setEnabled(false);

        mSelectPngButton = (Button) findViewById(R.id.select_png_button);
        mSelectPngButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectPng();
            }
        });

        mPngXPosition = (EditText) findViewById(R.id.png_x_position);
        mPngXPosition.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                String x = s.toString();
                if (x == null || x.isEmpty()) {
                    return;
                }
                CameraVideoCapturer cameraVideoCapturer = CameraCapturerFactory.getInstance().getCameraVideoCapturer();
                if (cameraVideoCapturer instanceof PngOverlayCameraCapturer) {
                    ((PngOverlayCameraCapturer) cameraVideoCapturer).setStartX(Integer.parseInt(s.toString()));
                }
            }
        });
        mPngYPosition = (EditText) findViewById(R.id.png_y_position);
        mPngYPosition.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                String y = s.toString();
                if (y == null || y.isEmpty()) {
                    return;
                }
                CameraVideoCapturer cameraVideoCapturer = CameraCapturerFactory.getInstance().getCameraVideoCapturer();
                if (cameraVideoCapturer instanceof PngOverlayCameraCapturer) {
                    ((PngOverlayCameraCapturer) cameraVideoCapturer).setStartY(Integer.parseInt(s.toString()));
                }
            }
        });
        mPngWidth = (EditText) findViewById(R.id.png_width);
        mPngWidth.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                String y = s.toString();
                if (y == null || y.isEmpty()) {
                    return;
                }
                CameraVideoCapturer cameraVideoCapturer = CameraCapturerFactory.getInstance().getCameraVideoCapturer();
                if (cameraVideoCapturer instanceof PngOverlayCameraCapturer) {
                    ((PngOverlayCameraCapturer) cameraVideoCapturer).setPngWidth(Integer.parseInt(s.toString()));
                }
            }
        });
        mPngHeight = (EditText) findViewById(R.id.png_height);
        mPngHeight.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                String y = s.toString();
                if (y == null || y.isEmpty()) {
                    return;
                }
                CameraVideoCapturer cameraVideoCapturer = CameraCapturerFactory.getInstance().getCameraVideoCapturer();
                if (cameraVideoCapturer instanceof PngOverlayCameraCapturer) {
                    ((PngOverlayCameraCapturer) cameraVideoCapturer).setPngHeight(Integer.parseInt(s.toString()));
                }
            }
        });

        mZoomSeekBar = (SeekBar) findViewById(R.id.zoom_seekBar);
        mZoomSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                CameraVideoCapturer cameraVideoCapturer = CameraCapturerFactory.getInstance().getCameraVideoCapturer();
                if (cameraVideoCapturer instanceof ZoomCameraCapturer) {
                    ((ZoomCameraCapturer) cameraVideoCapturer).setZoom(progress);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });
        mZoomSeekBar.setEnabled(false);

        mSwitchFlashlightButton = (Button) findViewById(R.id.switch_flashlight_button);
        mSwitchFlashlightButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (flashlight) {
                    turnOffFlashlight();
                } else {
                    turnOnFlashlight();
                }
            }
        });

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

                                    int cameraId = 0;
                                    List<MediaDevice> videoList = Flashphoner.getMediaDevices().getVideoList();
                                    for (MediaDevice videoDevice : videoList) {
                                        String videoDeviceName = videoDevice.getLabel();
                                        if (Flashphoner.getCameraEnumerator().isBackFacing(videoDeviceName)) {
                                            cameraId = videoDevice.getId();
                                            break;
                                        }
                                    }
                                    StreamOptions streamOptions = new StreamOptions(streamName);
                                    VideoConstraints videoConstraints = new VideoConstraints();
                                    videoConstraints.setVideoFps(25);
                                    if (mWidth.getText().length() > 0 && mHeight.getText().length() > 0) {
                                        videoConstraints.setResolution(Integer.parseInt(mWidth.getText().toString()),
                                                Integer.parseInt(mHeight.getText().toString()));
                                    }
                                    videoConstraints.setCameraId(cameraId);
                                    Constraints constraints = new Constraints(true, true);
                                    constraints.setVideoConstraints(videoConstraints);
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
                                                        prepareFlashlight();
                                                        prepareZoom();
                                                        prepareGpuImage();
                                                        preparePngOverlay();

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

                                    ActivityCompat.requestPermissions(CameraManagerActivity.this,
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

                    SharedPreferences sharedPref = CameraManagerActivity.this.getPreferences(Context.MODE_PRIVATE);
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

    public void selectPng() {
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        try {
            startActivityForResult(Intent.createChooser(intent, "Select Picture"), REQUEST_IMAGE_CAPTURE);
        } catch (ActivityNotFoundException e) {
            Log.e(TAG, "Can't select picture: " + e.getMessage());
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String permissions[], @NonNull int[] grantResults) {
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

    private void changeFlashlightCamera() {
        CameraCapturerFactory.getInstance().setCameraType(CameraCapturerFactory.CameraType.FLASHLIGHT_CAMERA);
        mUseFilter.setEnabled(false);
        mUsePngOverlay.setEnabled(false);
        mPngHeight.setEnabled(false);
        mPngWidth.setEnabled(false);
        mPngXPosition.setEnabled(false);
        mPngYPosition.setEnabled(false);
        mSelectPngButton.setEnabled(false);
    }

    private void changeZoomCamera() {
        CameraCapturerFactory.getInstance().setCustomCameraCapturerOptions(zoomCameraCapturerOptions);
        CameraCapturerFactory.getInstance().setCameraType(CameraCapturerFactory.CameraType.CUSTOM);
        mUseFilter.setEnabled(false);
        mUsePngOverlay.setEnabled(false);
        mPngHeight.setEnabled(false);
        mPngWidth.setEnabled(false);
        mPngXPosition.setEnabled(false);
        mPngYPosition.setEnabled(false);
        mSelectPngButton.setEnabled(false);
    }

    private void changePngOverlayCamera() {
        CameraCapturerFactory.getInstance().setCustomCameraCapturerOptions(pngOverlayCameraCapturerOptions);
        CameraCapturerFactory.getInstance().setCameraType(CameraCapturerFactory.CameraType.CUSTOM);
        mUseFilter.setEnabled(false);
        mUsePngOverlay.setEnabled(true);
        mPngHeight.setEnabled(true);
        mPngWidth.setEnabled(true);
        mPngXPosition.setEnabled(true);
        mPngYPosition.setEnabled(true);
        mSelectPngButton.setEnabled(true);
    }

    private void changeGpuImageCamera() {
        CameraCapturerFactory.getInstance().setCustomCameraCapturerOptions(gpuImageCameraCapturerOptions);
        CameraCapturerFactory.getInstance().setCameraType(CameraCapturerFactory.CameraType.CUSTOM);
        mUseFilter.setEnabled(true);
        mUsePngOverlay.setEnabled(false);
        mPngHeight.setEnabled(false);
        mPngWidth.setEnabled(false);
        mPngXPosition.setEnabled(false);
        mPngYPosition.setEnabled(false);
        mSelectPngButton.setEnabled(false);
    }

    private void prepareFlashlight() {
        CameraVideoCapturer cameraVideoCapturer = CameraCapturerFactory.getInstance().getCameraVideoCapturer();
        if (cameraVideoCapturer instanceof FlashlightCameraCapturer) {
            mSwitchFlashlightButton.setEnabled(true);
            mUseFilter.setEnabled(false);
            mZoomSeekBar.setEnabled(false);
        }
    }

    private void prepareZoom() {
        CameraVideoCapturer cameraVideoCapturer = CameraCapturerFactory.getInstance().getCameraVideoCapturer();
        if (cameraVideoCapturer instanceof ZoomCameraCapturer) {
            int maxZoom = ((ZoomCameraCapturer) cameraVideoCapturer).getMaxZoom();
            mZoomSeekBar.setMax(maxZoom);
            ((ZoomCameraCapturer) cameraVideoCapturer).setZoom(mZoomSeekBar.getProgress());
            mSwitchFlashlightButton.setEnabled(false);
            mUseFilter.setEnabled(false);
            mUsePngOverlay.setEnabled(false);
            mZoomSeekBar.setEnabled(true);
        }
    }

    private void prepareGpuImage() {
        CameraVideoCapturer cameraVideoCapturer = CameraCapturerFactory.getInstance().getCameraVideoCapturer();
        if (cameraVideoCapturer instanceof GPUImageCameraCapturer) {
            ((GPUImageCameraCapturer) cameraVideoCapturer).setUsedFilter(mUseFilter.isChecked());
            mSwitchFlashlightButton.setEnabled(false);
            mUseFilter.setEnabled(true);
            mUsePngOverlay.setEnabled(false);
            mZoomSeekBar.setEnabled(false);
        }
    }

    private void preparePngOverlay() {
        CameraVideoCapturer cameraVideoCapturer = CameraCapturerFactory.getInstance().getCameraVideoCapturer();
        if (!(cameraVideoCapturer instanceof PngOverlayCameraCapturer)) {
            return;
        }

        checkingSelectedPng();
        ((PngOverlayCameraCapturer) cameraVideoCapturer).setUsedPngOverlay(mUsePngOverlay.isChecked());
        ((PngOverlayCameraCapturer) cameraVideoCapturer).setPicture(picture);
        int startX = parseInt(mPngXPosition.getText().toString());
        int startY = parseInt(mPngYPosition.getText().toString());
        int width = parseInt(mPngWidth.getText().toString());
        int height = parseInt(mPngHeight.getText().toString());
        ((PngOverlayCameraCapturer) cameraVideoCapturer).setStartX(startX);
        ((PngOverlayCameraCapturer) cameraVideoCapturer).setStartY(startY);
        if (width > 0) {
            ((PngOverlayCameraCapturer) cameraVideoCapturer).setPngWidth(width);
        }
        if (height > 0) {
            ((PngOverlayCameraCapturer) cameraVideoCapturer).setPngHeight(height);
        }

        mSwitchFlashlightButton.setEnabled(false);
        mUseFilter.setEnabled(false);
        mZoomSeekBar.setEnabled(false);
        mUsePngOverlay.setEnabled(true);
    }

    private int parseInt(String str) {
        return (str == null || str.isEmpty()) ? 0 : Integer.parseInt(str);
    }

    private void checkingSelectedPng() {
        if (picture == null && mUsePngOverlay.isChecked()) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage(R.string.dialog_message)
                    .setTitle(R.string.dialog_title)
                    .setNegativeButton(R.string.select_png, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            selectPng();
                        }
                    });
            AlertDialog dialog = builder.create();
            dialog.show();
        }
    }

    private Bitmap picture;

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            InputStream inputStream = null;
            try {
                inputStream = CameraManagerActivity.this.getBaseContext().getContentResolver().openInputStream(data.getData());
            } catch (FileNotFoundException e) {
                Log.e(TAG, "Can't select picture: " + e.getMessage());
            }
            picture = BitmapFactory.decodeStream(inputStream);
        }

        CameraVideoCapturer cameraVideoCapturer = CameraCapturerFactory.getInstance().getCameraVideoCapturer();
        if (cameraVideoCapturer instanceof PngOverlayCameraCapturer && picture != null) {
            ((PngOverlayCameraCapturer) cameraVideoCapturer).setPicture(picture);
        }
    }

    private void turnOnFlashlight() {
        if (Flashphoner.turnOnFlashlight()) {
            mSwitchFlashlightButton.setText(getResources().getString(R.string.turn_off_flashlight));
            flashlight = true;
        }
    }

    private void turnOffFlashlight() {
        Flashphoner.turnOffFlashlight();
        mSwitchFlashlightButton.setText(getResources().getString(R.string.turn_on_flashlight));
        flashlight = false;
    }

    private CustomCameraCapturerOptions zoomCameraCapturerOptions = new CustomCameraCapturerOptions() {

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
                return "org.webrtc.ZoomCameraCapturer";
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
                return "org.webrtc.ZoomCameraEnumerator";
            }
    };

    private CustomCameraCapturerOptions pngOverlayCameraCapturerOptions = new CustomCameraCapturerOptions() {

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
            return "org.webrtc.PngOverlayCameraCapturer";
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
            return "org.webrtc.PngOverlayCameraEnumerator";
        }
    };

    private CustomCameraCapturerOptions gpuImageCameraCapturerOptions = new CustomCameraCapturerOptions() {

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



    private void muteButton() {
        mStartButton.setEnabled(false);
        mCameraCapturer.getSpinner().setEnabled(false);
    }

    private void onStarted() {
        mStartButton.setText(R.string.action_stop);
        mStartButton.setTag(R.string.action_stop);
        mStartButton.setEnabled(true);
        mCameraCapturer.setEnabled(false);
    }

    private void onStopped() {
        mStartButton.setText(R.string.action_start);
        mStartButton.setTag(R.string.action_start);
        turnOffFlashlight();
        mCameraCapturer.getSpinner().setEnabled(true);
        mStartButton.setEnabled(true);
        mCameraCapturer.setEnabled(true);
        mSwitchFlashlightButton.setEnabled(false);
        mZoomSeekBar.setEnabled(false);
    }
}