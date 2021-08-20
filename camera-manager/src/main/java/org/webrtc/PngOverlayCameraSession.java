package org.webrtc;

import android.content.Context;
import android.graphics.Bitmap;
import android.hardware.Camera;
import android.os.Handler;
import android.os.SystemClock;

import com.flashphoner.fpwcsapi.util.Utils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import jp.co.cyberagent.android.gpuimage.GPUImageNativeLibrary;

import static org.webrtc.Constants.*;

public class PngOverlayCameraSession implements CameraSession {

    private static final String TAG = "PngOverlayCameraSession";
    private static final Histogram camera1StartTimeMsHistogram = Histogram.createCounts(WEB_RTC_ANDROID_CAMERA_1_START_TIME_MS, WEB_RTC_ANDROID_CAMERA_1_START_TIME_MIN, WEB_RTC_ANDROID_CAMERA_1_START_TIME_MAX, WEB_RTC_ANDROID_CAMERA_1_START_TIME_BUCKET_COUNT);
    private static final Histogram camera1StopTimeMsHistogram = Histogram.createCounts(WEB_RTC_ANDROID_CAMERA_1_STOP_TIME_MS, WEB_RTC_ANDROID_CAMERA_1_STOP_TIME_MIN, WEB_RTC_ANDROID_CAMERA_1_STOP_TIME_MAX, WEB_RTC_ANDROID_CAMERA_1_STOP_TIME_BUCKET_COUNT);
    private static final Histogram camera1ResolutionHistogram;
    private final Handler cameraThreadHandler;
    private final CameraSession.Events events;
    private final boolean captureToTexture;
    private final Context applicationContext;
    private final SurfaceTextureHelper surfaceTextureHelper;
    private final int cameraId;
    private final Camera camera;
    private final Camera.CameraInfo info;
    private final CameraEnumerationAndroid.CaptureFormat captureFormat;
    private final long constructionTimeNs;
    private PngOverlayCameraSession.SessionState state;
    private boolean firstFrameReported;
    private boolean cameraReleased = true;

    private boolean isUsedPngOverlay = false;
    private Bitmap picture;
    private int startX= PNG_START_X;
    private int startY= PNG_START_Y;
    private int pngWidth = PNG_WIDTH;
    private int pngHeight = PNG_HEIGHT;

    public static void create(CameraSession.CreateSessionCallback callback, CameraSession.Events events, boolean captureToTexture, Context applicationContext, SurfaceTextureHelper surfaceTextureHelper, int cameraId, int width, int height, int framerate) {
        long constructionTimeNs = System.nanoTime();
        Logging.d(TAG, OPEN_CAMERA + cameraId);
        events.onCameraOpening();

        Camera camera;
        try {
            camera = Camera.open(cameraId);

        } catch (RuntimeException var19) {
            callback.onFailure(CameraSession.FailureType.ERROR, var19.getMessage());
            return;
        }

        if (camera == null) {
            callback.onFailure(CameraSession.FailureType.ERROR, ANDROID_HARDWARE_CAMERA_OPEN_RETURNED_NULL_FOR_CAMERA_ID + cameraId);
        } else {
            try {
                camera.setPreviewTexture(surfaceTextureHelper.getSurfaceTexture());
            } catch (RuntimeException | IOException var18) {
                camera.release();
                callback.onFailure(CameraSession.FailureType.ERROR, var18.getMessage());
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
                callback.onFailure(CameraSession.FailureType.ERROR, var17.getMessage());
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
            callback.onDone(new PngOverlayCameraSession(events, captureToTexture, applicationContext, surfaceTextureHelper, cameraId, camera, info, captureFormat, constructionTimeNs));
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

    private PngOverlayCameraSession(CameraSession.Events events, boolean captureToTexture, Context applicationContext, SurfaceTextureHelper surfaceTextureHelper, int cameraId, Camera camera, Camera.CameraInfo info, CameraEnumerationAndroid.CaptureFormat captureFormat, long constructionTimeNs) {
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
        if (this.state != PngOverlayCameraSession.SessionState.STOPPED) {
            long stopStartTime = System.nanoTime();
            this.stopInternal();
            int stopTimeMs = (int) TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - stopStartTime);
            camera1StopTimeMsHistogram.addSample(stopTimeMs);
        }

    }

    private void startCapturing() {
        Logging.d(TAG, START_CAPTURING);
        this.checkIsOnCameraThread();
        this.state = PngOverlayCameraSession.SessionState.RUNNING;
        this.camera.setErrorCallback(new Camera.ErrorCallback() {
            public void onError(int error, Camera camera) {
                String errorMessage;
                if (error == CAMERA_DIED_ERROR) {
                    errorMessage = CAMERA_SERVER_DIED_MESSAGE;
                } else {
                    errorMessage = CAMERA_ERROR_MESSAGE + error;
                }

                Logging.e(TAG, errorMessage);
                PngOverlayCameraSession.this.stopInternal();
                if (error == CAMERA_DISCONNECTED_ERROR) {
                    PngOverlayCameraSession.this.events.onCameraDisconnected(PngOverlayCameraSession.this);
                } else {
                    PngOverlayCameraSession.this.events.onCameraError(PngOverlayCameraSession.this, errorMessage);
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
        if (this.state == PngOverlayCameraSession.SessionState.STOPPED) {
            Logging.d(TAG, CAMERA_IS_ALREADY_STOPPED);
        } else {
            this.state = PngOverlayCameraSession.SessionState.STOPPED;
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
            if (this.state != PngOverlayCameraSession.SessionState.RUNNING) {
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
                PngOverlayCameraSession.this.checkIsOnCameraThread();
                if (callbackCamera != PngOverlayCameraSession.this.camera) {
                    Logging.e(TAG, CALLBACK_FROM_A_DIFFERENT_CAMERA_THIS_SHOULD_NEVER_HAPPEN);
                } else if (PngOverlayCameraSession.this.state != PngOverlayCameraSession.SessionState.RUNNING) {
                    Logging.d(TAG, BYTEBUFFER_FRAME_CAPTURED_BUT_CAMERA_IS_NO_LONGER_RUNNING);
                } else {
                    long captureTimeNs = TimeUnit.MILLISECONDS.toNanos(SystemClock.elapsedRealtime());
                    if (!PngOverlayCameraSession.this.firstFrameReported) {
                        int startTimeMs = (int)TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - PngOverlayCameraSession.this.constructionTimeNs);
                        PngOverlayCameraSession.camera1StartTimeMsHistogram.addSample(startTimeMs);
                        PngOverlayCameraSession.this.firstFrameReported = true;
                    }

                    insertPicture(data, PngOverlayCameraSession.this.captureFormat.width, PngOverlayCameraSession.this.captureFormat.height);

                    VideoFrame.Buffer frameBuffer = new NV21Buffer(data, PngOverlayCameraSession.this.captureFormat.width, PngOverlayCameraSession.this.captureFormat.height, () -> {
                        PngOverlayCameraSession.this.cameraThreadHandler.post(() -> {
                            if (PngOverlayCameraSession.this.state == PngOverlayCameraSession.SessionState.RUNNING) {
                                PngOverlayCameraSession.this.camera.addCallbackBuffer(data);
                            }

                        });
                    });
                    VideoFrame frame = new VideoFrame(frameBuffer, PngOverlayCameraSession.this.getFrameOrientation(), captureTimeNs);
                    PngOverlayCameraSession.this.events.onFrameCaptured(PngOverlayCameraSession.this, frame);
                    frame.release();
                }
            }
        });
    }

    public void setPicture(Bitmap picture) {
        if (picture == null) {
            return;
        }
        this.picture = picture;
    }

    public void setStartX(int startX) {
        this.startX = checkingCoordinate(startX, pngWidth, PngOverlayCameraSession.this.captureFormat.height);
    }

    public void setStartY(int startY) {
        this.startY = checkingCoordinate(startY, pngHeight, PngOverlayCameraSession.this.captureFormat.width);
    }

    private int checkingCoordinate(int coordinate, int pngSize, int frameSize) {
        if (coordinate + pngSize <= frameSize) {
            return coordinate;
        }
        return frameSize-pngSize;
    }

    public void setPngWidth(int pngWidth) {
        this.pngWidth = pngWidth;
    }

    public void setPngHeight(int pngHeight) {
        this.pngHeight = pngHeight;
    }

    private void insertPicture(byte[] data, int width, int height) {
        if (picture == null || !isUsedPngOverlay) {
            return;
        }

        Bitmap scaledPicture = rescalingPicture();

        int [] pngArray = new int[scaledPicture.getHeight() * scaledPicture.getWidth()];
        scaledPicture.getPixels(pngArray, 0, scaledPicture.getWidth(), 0, 0, scaledPicture.getWidth(), scaledPicture.getHeight());

        int [] rgbData = new int [width * height];
        GPUImageNativeLibrary.YUVtoARBG(data, width, height, rgbData);

        int pictureW = scaledPicture.getWidth();
        int pictureH = scaledPicture.getHeight();

        for (int c = 0; c < pngArray.length; c++) {
            int pictureColumn = c / pictureW;
            int pictureLine = c - pictureColumn * pictureW;
            int index = (pictureLine * width) + pictureColumn + startX * width + startY;

            if (index >= data.length) {
                break;
            }
            rgbData[index] = pngArray[c];
        }

        byte[] yuvData = Utils.getNV21(width, height, rgbData);
        System.arraycopy(yuvData, 0, data, 0, yuvData.length);
    }

    private void checkingSize() {
        if (this.pngWidth + startX > PngOverlayCameraSession.this.captureFormat.height) {
            this.pngWidth = PngOverlayCameraSession.this.captureFormat.height - startX;
        }
        if (this.pngHeight + startY > PngOverlayCameraSession.this.captureFormat.width) {
            this.pngHeight = PngOverlayCameraSession.this.captureFormat.width - startY;
        }
    }

    private Bitmap rescalingPicture() {
        if (picture == null) {
            return null;
        }
        if (pngHeight == picture.getHeight() && pngWidth == picture.getWidth()) {
            return picture;
        }

        checkingSize();
        return Bitmap.createScaledBitmap(picture, pngWidth, pngHeight, false);
    }

    public boolean isUsedPngOverlay() {
        return isUsedPngOverlay;
    }

    public void setUsedPngOverlay(boolean usedPngOverlay) {
        isUsedPngOverlay = usedPngOverlay;
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

    private static enum SessionState {
        RUNNING,
        STOPPED;

        private SessionState() {
        }
    }
}
