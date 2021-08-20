package org.webrtc;

import android.content.Context;
import android.graphics.Bitmap;

public class PngOverlayCameraCapturer extends Camera1Capturer {

    private PngOverlayCameraSession cameraSession;
    private boolean captureToTexture;

    public PngOverlayCameraCapturer(String cameraName, CameraEventsHandler eventsHandler, boolean captureToTexture) {
        super(cameraName, eventsHandler, captureToTexture);
        this.captureToTexture = captureToTexture;
    }

    @Override
    protected void createCameraSession(CameraSession.CreateSessionCallback createSessionCallback, CameraSession.Events events, Context applicationContext, SurfaceTextureHelper surfaceTextureHelper, String cameraName, int width, int height, int framerate) {
        CameraSession.CreateSessionCallback myCallback = new CameraSession.CreateSessionCallback() {
            @Override
            public void onDone(CameraSession cameraSession) {
                PngOverlayCameraCapturer.this.cameraSession = (PngOverlayCameraSession) cameraSession;
                createSessionCallback.onDone(cameraSession);
            }

            @Override
            public void onFailure(CameraSession.FailureType failureType, String s) {
                createSessionCallback.onFailure(failureType, s);
            }
        };

        PngOverlayCameraSession.create(myCallback, events, captureToTexture, applicationContext, surfaceTextureHelper, Camera1Enumerator.getCameraIndex(cameraName), width, height, framerate);
    }

    public void setPicture(Bitmap picture) {
        if (cameraSession != null) {
            cameraSession.setPicture(picture);
        }
    }

    public void setStartX(int startX) {
        cameraSession.setStartX(startX);
    }

    public void setStartY(int startY) {
        cameraSession.setStartY(startY);
    }

    public void setPngWidth(int pngWidth) {
        cameraSession.setPngWidth(pngWidth);
    }

    public void setPngHeight(int pngHeight) {
        cameraSession.setPngHeight(pngHeight);
    }

    public void setUsedPngOverlay(boolean usedPngOverlay) {
        cameraSession.setUsedPngOverlay(usedPngOverlay);
    }
}