package org.webrtc;

import android.content.Context;

public class ZoomCameraCapturer extends Camera1Capturer{

    protected ZoomCameraSession cameraSession;
    private boolean captureToTexture;

    public ZoomCameraCapturer(String cameraName, CameraVideoCapturer.CameraEventsHandler eventsHandler, boolean captureToTexture) {
        super(cameraName, eventsHandler, captureToTexture);
        this.captureToTexture = captureToTexture;
    }

    @Override
    protected void createCameraSession(CameraSession.CreateSessionCallback createSessionCallback, CameraSession.Events events, Context applicationContext, SurfaceTextureHelper surfaceTextureHelper, String cameraName, int width, int height, int framerate) {
        CameraSession.CreateSessionCallback myCallback = new CameraSession.CreateSessionCallback() {
            @Override
            public void onDone(CameraSession cameraSession) {
                ZoomCameraCapturer.this.cameraSession = (ZoomCameraSession) cameraSession;
                createSessionCallback.onDone(cameraSession);
            }

            @Override
            public void onFailure(CameraSession.FailureType failureType, String s) {
                createSessionCallback.onFailure(failureType, s);
            }
        };

        ZoomCameraSession.create(myCallback, events, captureToTexture, applicationContext, surfaceTextureHelper, Camera1Enumerator.getCameraIndex(cameraName), width, height, framerate);
    }

    public boolean setZoom(int value) {
        return cameraSession.setZoom(value);
    }

    public int getZoom() {
        return cameraSession.getZoom();
    }

    public int getMaxZoom() {
        if (cameraSession == null) {
            return 0;
        }
        return cameraSession.getMaxZoom();
    }
}
