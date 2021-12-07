package org.webrtc;

import static org.webrtc.Constants.ANDROID_HARDWARE_CAMERA_OPEN_RETURNED_NULL_FOR_CAMERA_ID;
import static org.webrtc.Constants.AVAILABLE_FPS_RANGES;
import static org.webrtc.Constants.BYTEBUFFER_FRAME_CAPTURED_BUT_CAMERA_IS_NO_LONGER_RUNNING;
import static org.webrtc.Constants.CALLBACK_FROM_A_DIFFERENT_CAMERA_THIS_SHOULD_NEVER_HAPPEN;
import static org.webrtc.Constants.CAMERA_DIED_ERROR;
import static org.webrtc.Constants.CAMERA_DISCONNECTED_ERROR;
import static org.webrtc.Constants.CAMERA_ERROR_MESSAGE;
import static org.webrtc.Constants.CAMERA_IS_ALREADY_STOPPED;
import static org.webrtc.Constants.CAMERA_SERVER_DIED_MESSAGE;
import static org.webrtc.Constants.CONTINUOUS_VIDEO;
import static org.webrtc.Constants.CREATE_NEW_CAMERA_1_SESSION_ON_CAMERA;
import static org.webrtc.Constants.OPEN_CAMERA;
import static org.webrtc.Constants.START_CAPTURING;
import static org.webrtc.Constants.STOP_CAMERA_1_SESSION_ON_CAMERA;
import static org.webrtc.Constants.STOP_DONE;
import static org.webrtc.Constants.STOP_INTERNAL;
import static org.webrtc.Constants.TEXTURE_FRAME_CAPTURED_BUT_CAMERA_IS_NO_LONGER_RUNNING;
import static org.webrtc.Constants.WEB_RTC_ANDROID_CAMERA_1_RESOLUTION;
import static org.webrtc.Constants.WEB_RTC_ANDROID_CAMERA_1_START_TIME_BUCKET_COUNT;
import static org.webrtc.Constants.WEB_RTC_ANDROID_CAMERA_1_START_TIME_MAX;
import static org.webrtc.Constants.WEB_RTC_ANDROID_CAMERA_1_START_TIME_MIN;
import static org.webrtc.Constants.WEB_RTC_ANDROID_CAMERA_1_START_TIME_MS;
import static org.webrtc.Constants.WEB_RTC_ANDROID_CAMERA_1_STOP_TIME_BUCKET_COUNT;
import static org.webrtc.Constants.WEB_RTC_ANDROID_CAMERA_1_STOP_TIME_MAX;
import static org.webrtc.Constants.WEB_RTC_ANDROID_CAMERA_1_STOP_TIME_MIN;
import static org.webrtc.Constants.WEB_RTC_ANDROID_CAMERA_1_STOP_TIME_MS;
import static org.webrtc.Constants.WRONG_THREAD;

import android.content.Context;
import android.hardware.Camera;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class ResolutionCameraSession  implements CameraSession {

    public static final String TAG = "FlashphonerCameraSession";
    private static final Histogram camera1StartTimeMsHistogram = Histogram.createCounts(WEB_RTC_ANDROID_CAMERA_1_START_TIME_MS, WEB_RTC_ANDROID_CAMERA_1_START_TIME_MIN, WEB_RTC_ANDROID_CAMERA_1_START_TIME_MAX, WEB_RTC_ANDROID_CAMERA_1_START_TIME_BUCKET_COUNT);
    private static final Histogram camera1StopTimeMsHistogram = Histogram.createCounts(WEB_RTC_ANDROID_CAMERA_1_STOP_TIME_MS, WEB_RTC_ANDROID_CAMERA_1_STOP_TIME_MIN, WEB_RTC_ANDROID_CAMERA_1_STOP_TIME_MAX, WEB_RTC_ANDROID_CAMERA_1_STOP_TIME_BUCKET_COUNT);
    private static final Histogram camera1ResolutionHistogram;
    private final Handler cameraThreadHandler;
    private final Events events;
    private final boolean captureToTexture;
    private final Context applicationContext;
    private final SurfaceTextureHelper surfaceTextureHelper;
    private final int cameraId;
    protected final Camera camera;
    private final Camera.CameraInfo info;
    private final CameraEnumerationAndroid.CaptureFormat captureFormat;
    private final long constructionTimeNs;
    protected ResolutionCameraSession.SessionState state;
    private boolean firstFrameReported;
    private boolean cameraReleased = true;

    public boolean isCameraActive() {
        if (isCameraReleased() || state.equals(ResolutionCameraSession.SessionState.STOPPED)) {
            Log.w(TAG, "isCameraActive: Camera is being used after Camera.release() was called");
            return false;
        }
        return true;
    }

    public static void create(CreateSessionCallback callback, Events events, boolean captureToTexture, Context applicationContext, SurfaceTextureHelper surfaceTextureHelper, int cameraId, int width, int height, int framerate) {
        long constructionTimeNs = System.nanoTime();
        Logging.d(TAG, OPEN_CAMERA + cameraId);
        events.onCameraOpening();

        Camera camera;
        try {
            camera = Camera.open(cameraId);

        } catch (RuntimeException var19) {
            callback.onFailure(FailureType.ERROR, var19.getMessage());
            return;
        }

        if (camera == null) {
            callback.onFailure(FailureType.ERROR, ANDROID_HARDWARE_CAMERA_OPEN_RETURNED_NULL_FOR_CAMERA_ID + cameraId);
        } else {
            try {
                camera.setPreviewTexture(surfaceTextureHelper.getSurfaceTexture());
            } catch (RuntimeException | IOException var18) {
                camera.release();
                callback.onFailure(FailureType.ERROR, var18.getMessage());
                return;
            }

            Camera.CameraInfo info = new Camera.CameraInfo();
            Camera.getCameraInfo(cameraId, info);

            CameraEnumerationAndroid.CaptureFormat captureFormat;
            try {
                Camera.Parameters parameters = camera.getParameters();
                captureFormat = findClosestCaptureFormat(parameters, width, height, framerate);
                Size pictureSize = findClosestPictureSize(parameters, width, height);
                updateCameraParameters(camera, parameters, captureFormat, pictureSize, captureToTexture);
            } catch (RuntimeException var17) {
                camera.release();
                callback.onFailure(FailureType.ERROR, var17.getMessage());
                return;
            }

            if (!captureToTexture) {
                int frameSize = captureFormat.frameSize();

                //The implementation is taken from the WebRTC library, so the purpose of the three buffers is not entirely known
                for(int i = 0; i < 3; ++i) {
                    ByteBuffer buffer = ByteBuffer.allocateDirect(frameSize);
                    camera.addCallbackBuffer(buffer.array());
                }
            }

            camera.setDisplayOrientation(0);
            callback.onDone(new ResolutionCameraSession(events, captureToTexture, applicationContext, surfaceTextureHelper, cameraId, camera, info, captureFormat, constructionTimeNs));
        }
    }

    private static void updateCameraParameters(Camera camera, Camera.Parameters parameters, CameraEnumerationAndroid.CaptureFormat captureFormat, Size pictureSize, boolean captureToTexture) {
        List<String> focusModes = parameters.getSupportedFocusModes();
        parameters.setPreviewFpsRange(captureFormat.framerate.min, captureFormat.framerate.max);
        parameters.setPreviewSize(captureFormat.width, captureFormat.height);
        parameters.setPictureSize(pictureSize.width, pictureSize.height);
        if (!captureToTexture) {
            Objects.requireNonNull(captureFormat);
            parameters.setPreviewFormat(17);
        }

        if (parameters.isVideoStabilizationSupported()) {
            parameters.setVideoStabilization(true);
        }

        if (focusModes.contains(CONTINUOUS_VIDEO)) {
            parameters.setFocusMode(CONTINUOUS_VIDEO);
        }

        camera.setParameters(parameters);
    }

    private static CameraEnumerationAndroid.CaptureFormat findClosestCaptureFormat(Camera.Parameters parameters, int width, int height, int framerate) {
        List<CameraEnumerationAndroid.CaptureFormat.FramerateRange> supportedFramerates = Camera1Enumerator.convertFramerates(parameters.getSupportedPreviewFpsRange());
        Logging.d(TAG, AVAILABLE_FPS_RANGES + supportedFramerates);
        CameraEnumerationAndroid.CaptureFormat.FramerateRange fpsRange = CameraEnumerationAndroid.getClosestSupportedFramerateRange(supportedFramerates, framerate);
        Size previewSize = CameraEnumerationAndroid.getClosestSupportedSize(Camera1Enumerator.convertSizes(parameters.getSupportedPreviewSizes()), width, height);
        CameraEnumerationAndroid.reportCameraResolution(camera1ResolutionHistogram, previewSize);
        return new CameraEnumerationAndroid.CaptureFormat(previewSize.width, previewSize.height, fpsRange);
    }

    private static Size findClosestPictureSize(Camera.Parameters parameters, int width, int height) {
        return CameraEnumerationAndroid.getClosestSupportedSize(Camera1Enumerator.convertSizes(parameters.getSupportedPictureSizes()), width, height);
    }

    protected ResolutionCameraSession(Events events, boolean captureToTexture, Context applicationContext, SurfaceTextureHelper surfaceTextureHelper, int cameraId, Camera camera, Camera.CameraInfo info, CameraEnumerationAndroid.CaptureFormat captureFormat, long constructionTimeNs) {
        Logging.d(TAG, CREATE_NEW_CAMERA_1_SESSION_ON_CAMERA + cameraId);
        this.cameraThreadHandler = new Handler();
        this.events = events;
        this.captureToTexture = captureToTexture;
        this.applicationContext = applicationContext;
        this.surfaceTextureHelper = surfaceTextureHelper;
        this.cameraId = cameraId;
        this.camera = camera;
        setCameraReleased(false);
        this.info = info;
        this.captureFormat = captureFormat;
        this.constructionTimeNs = constructionTimeNs;
        surfaceTextureHelper.setTextureSize(captureFormat.width, captureFormat.height);
        this.startCapturing();
    }

    public void stop() {
        Logging.d(TAG, STOP_CAMERA_1_SESSION_ON_CAMERA + this.cameraId);
        this.checkIsOnCameraThread();
        if (this.state != ResolutionCameraSession.SessionState.STOPPED) {
            long stopStartTime = System.nanoTime();
            this.stopInternal();
            int stopTimeMs = (int) TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - stopStartTime);
            camera1StopTimeMsHistogram.addSample(stopTimeMs);
        }

    }

    private void startCapturing() {
        Logging.d(TAG, START_CAPTURING);
        this.checkIsOnCameraThread();
        this.state = ResolutionCameraSession.SessionState.RUNNING;
        this.camera.setErrorCallback(new Camera.ErrorCallback() {
            public void onError(int error, Camera camera) {
                String errorMessage;
                if (error == CAMERA_DIED_ERROR) {
                    errorMessage = CAMERA_SERVER_DIED_MESSAGE;
                } else {
                    errorMessage = CAMERA_ERROR_MESSAGE + error;
                }

                Logging.e(TAG, errorMessage);
                ResolutionCameraSession.this.stopInternal();
                if (error == CAMERA_DISCONNECTED_ERROR) {
                    ResolutionCameraSession.this.events.onCameraDisconnected(ResolutionCameraSession.this);
                } else {
                    ResolutionCameraSession.this.events.onCameraError(ResolutionCameraSession.this, errorMessage);
                }

            }
        });
        if (this.captureToTexture) {
            this.listenForTextureFrames();
        } else {
            this.listenForBytebufferFrames();
        }

        try {
            this.camera.startPreview();
        } catch (RuntimeException var2) {
            this.stopInternal();
            this.events.onCameraError(this, var2.getMessage());
        }

    }

    private void stopInternal() {
        Logging.d(TAG, STOP_INTERNAL);
        this.checkIsOnCameraThread();
        if (this.state == ResolutionCameraSession.SessionState.STOPPED) {
            Logging.d(TAG, CAMERA_IS_ALREADY_STOPPED);
        } else {
            this.state = ResolutionCameraSession.SessionState.STOPPED;
            this.surfaceTextureHelper.stopListening();
            this.camera.stopPreview();
            this.camera.release();
            setCameraReleased(true);
            this.events.onCameraClosed(this);
            Logging.d(TAG, STOP_DONE);
        }
    }

    private void setCameraReleased(boolean released) {
        this.cameraReleased = released;
    }

    public boolean isCameraReleased() {
        return cameraReleased;
    }

    private void listenForTextureFrames() {
        this.surfaceTextureHelper.startListening((frame) -> {
            this.checkIsOnCameraThread();
            if (this.state != ResolutionCameraSession.SessionState.RUNNING) {
                Logging.d(TAG, TEXTURE_FRAME_CAPTURED_BUT_CAMERA_IS_NO_LONGER_RUNNING);
            } else {
                if (!this.firstFrameReported) {
                    int startTimeMs = (int)TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - this.constructionTimeNs);
                    camera1StartTimeMsHistogram.addSample(startTimeMs);
                    this.firstFrameReported = true;
                }

                VideoFrame modifiedFrame = new VideoFrame(CameraSession.createTextureBufferWithModifiedTransformMatrix((TextureBufferImpl)frame.getBuffer(), this.info.facing == 1, 0), this.getFrameOrientation(), frame.getTimestampNs());
                this.events.onFrameCaptured(this, modifiedFrame);
                modifiedFrame.release();
            }
        });
    }

    private void listenForBytebufferFrames() {
        this.camera.setPreviewCallbackWithBuffer(new Camera.PreviewCallback() {
            public void onPreviewFrame(byte[] data, Camera callbackCamera) {
                ResolutionCameraSession.this.checkIsOnCameraThread();
                if (callbackCamera != ResolutionCameraSession.this.camera) {
                    Logging.e(TAG, CALLBACK_FROM_A_DIFFERENT_CAMERA_THIS_SHOULD_NEVER_HAPPEN);
                } else if (ResolutionCameraSession.this.state != ResolutionCameraSession.SessionState.RUNNING) {
                    Logging.d(TAG, BYTEBUFFER_FRAME_CAPTURED_BUT_CAMERA_IS_NO_LONGER_RUNNING);
                } else {
                    long captureTimeNs = TimeUnit.MILLISECONDS.toNanos(SystemClock.elapsedRealtime());
                    if (!ResolutionCameraSession.this.firstFrameReported) {
                        int startTimeMs = (int)TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - ResolutionCameraSession.this.constructionTimeNs);
                        ResolutionCameraSession.camera1StartTimeMsHistogram.addSample(startTimeMs);
                        ResolutionCameraSession.this.firstFrameReported = true;
                    }

                    VideoFrame.Buffer frameBuffer = new NV21Buffer(data, ResolutionCameraSession.this.captureFormat.width, ResolutionCameraSession.this.captureFormat.height, () -> {
                        ResolutionCameraSession.this.cameraThreadHandler.post(() -> {
                            if (ResolutionCameraSession.this.state == ResolutionCameraSession.SessionState.RUNNING) {
                                ResolutionCameraSession.this.camera.addCallbackBuffer(data);
                            }

                        });
                    });
                    VideoFrame frame = new VideoFrame(frameBuffer, ResolutionCameraSession.this.getFrameOrientation(), captureTimeNs);
                    ResolutionCameraSession.this.events.onFrameCaptured(ResolutionCameraSession.this, frame);
                    frame.release();
                }
            }
        });
    }

    private int getFrameOrientation() {
        int rotation = CameraSession.getDeviceOrientation(this.applicationContext);
        if (this.info.facing == 0) {
            rotation = 360 - rotation;
        }

        return (this.info.orientation + rotation) % 360;
    }

    private void checkIsOnCameraThread() {
        if (Thread.currentThread() != this.cameraThreadHandler.getLooper().getThread()) {
            throw new IllegalStateException(WRONG_THREAD);
        }
    }

    static {
        camera1ResolutionHistogram = Histogram.createEnumeration(WEB_RTC_ANDROID_CAMERA_1_RESOLUTION, CameraEnumerationAndroid.COMMON_RESOLUTIONS.size());
    }

    protected static enum SessionState {
        RUNNING,
        STOPPED;

        private SessionState() {
        }
    }
}
