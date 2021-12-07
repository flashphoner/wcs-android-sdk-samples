package org.webrtc;

public class ResolutionCameraEnumerator extends Camera1Enumerator {
    @Override
    public CameraVideoCapturer createCapturer(String deviceName, CameraVideoCapturer.CameraEventsHandler eventsHandler) {
        return new ResolutionCameraCapturer(deviceName, eventsHandler, true);
    }
}
