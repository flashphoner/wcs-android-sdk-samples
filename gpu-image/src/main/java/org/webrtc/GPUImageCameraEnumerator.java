package org.webrtc;

public class GPUImageCameraEnumerator extends Camera1Enumerator {
    @Override
    public CameraVideoCapturer createCapturer(String deviceName, CameraVideoCapturer.CameraEventsHandler eventsHandler) {
        return new GPUImageCameraCapturer(deviceName, eventsHandler, true);
    }
}
