package org.webrtc;

public class Constants {
    public static final String WEB_RTC_ANDROID_CAMERA_1_START_TIME_MS = "WebRTC.Android.Camera1.StartTimeMs";
    public static final String WEB_RTC_ANDROID_CAMERA_1_STOP_TIME_MS = "WebRTC.Android.Camera1.StopTimeMs";
    public static final int WEB_RTC_ANDROID_CAMERA_1_START_TIME_MIN = 1;
    public static final int WEB_RTC_ANDROID_CAMERA_1_START_TIME_MAX = 10000;
    public static final int WEB_RTC_ANDROID_CAMERA_1_START_TIME_BUCKET_COUNT = 50;
    public static final int WEB_RTC_ANDROID_CAMERA_1_STOP_TIME_MIN = 1;
    public static final int WEB_RTC_ANDROID_CAMERA_1_STOP_TIME_MAX = 10000;
    public static final int WEB_RTC_ANDROID_CAMERA_1_STOP_TIME_BUCKET_COUNT = 50;

    public static final int PNG_START_X = 0;
    public static final int PNG_START_Y = 0;
    public static final int PNG_WIDTH = 100;
    public static final int PNG_HEIGHT = 100;

    public static final int CAMERA_DIED_ERROR = 100;
    public static final int CAMERA_DISCONNECTED_ERROR = 2;
    public static final String CAMERA_SERVER_DIED_MESSAGE = "Camera server died!";
    public static final String CAMERA_ERROR_MESSAGE = "Camera error: ";

    public static final String OPEN_CAMERA = "Open camera ";
    public static final String AVAILABLE_FPS_RANGES = "Available fps ranges: ";
    public static final String CREATE_NEW_CAMERA_1_SESSION_ON_CAMERA = "Create new camera1 session on camera ";
    public static final String STOP_CAMERA_1_SESSION_ON_CAMERA = "Stop camera1 session on camera ";
    public static final String START_CAPTURING = "Start capturing";
    public static final String STOP_INTERNAL = "Stop internal";
    public static final String CAMERA_IS_ALREADY_STOPPED = "Camera is already stopped";
    public static final String STOP_DONE = "Stop done";
    public static final String TEXTURE_FRAME_CAPTURED_BUT_CAMERA_IS_NO_LONGER_RUNNING = "Texture frame captured but camera is no longer running.";
    public static final String CALLBACK_FROM_A_DIFFERENT_CAMERA_THIS_SHOULD_NEVER_HAPPEN = "Callback from a different camera. This should never happen.";
    public static final String BYTEBUFFER_FRAME_CAPTURED_BUT_CAMERA_IS_NO_LONGER_RUNNING = "Bytebuffer frame captured but camera is no longer running.";
    public static final String WRONG_THREAD = "Wrong thread";
    public static final String ANDROID_HARDWARE_CAMERA_OPEN_RETURNED_NULL_FOR_CAMERA_ID = "android.hardware.Camera.open returned null for camera id = ";
    public static final String CONTINUOUS_VIDEO = "continuous-video";
    public static final String WEB_RTC_ANDROID_CAMERA_1_RESOLUTION = "WebRTC.Android.Camera1.Resolution";
}
