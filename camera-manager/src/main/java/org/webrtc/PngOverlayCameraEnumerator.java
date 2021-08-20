package org.webrtc;

public class PngOverlayCameraEnumerator  extends Camera1Enumerator {
    @Override
    public CameraVideoCapturer createCapturer(String deviceName, CameraVideoCapturer.CameraEventsHandler eventsHandler) {
        return new PngOverlayCameraCapturer(deviceName, eventsHandler, true);
    }
}