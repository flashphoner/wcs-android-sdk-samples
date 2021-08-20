package org.webrtc;

public class ZoomCameraEnumerator extends Camera1Enumerator {
    @Override
    public CameraVideoCapturer createCapturer(String deviceName, CameraVideoCapturer.CameraEventsHandler eventsHandler) {
        return new ZoomCameraCapturer(deviceName, eventsHandler, true);
    }
}