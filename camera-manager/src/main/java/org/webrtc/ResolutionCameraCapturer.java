package org.webrtc;

import android.content.Context;
import android.hardware.Camera;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class ResolutionCameraCapturer extends Camera1Capturer{

    protected ResolutionCameraSession cameraSession;
    private boolean captureToTexture;
    private String cameraName;

    public ResolutionCameraCapturer(String cameraName, CameraVideoCapturer.CameraEventsHandler eventsHandler, boolean captureToTexture) {
        super(cameraName, eventsHandler, captureToTexture);
        this.captureToTexture = captureToTexture;
        this.cameraName = cameraName;
    }

    @Override
    protected void createCameraSession(CameraSession.CreateSessionCallback createSessionCallback, CameraSession.Events events, Context applicationContext, SurfaceTextureHelper surfaceTextureHelper, String cameraName, int width, int height, int framerate) {
        CameraSession.CreateSessionCallback myCallback = new CameraSession.CreateSessionCallback() {
            @Override
            public void onDone(CameraSession cameraSession) {
                ResolutionCameraCapturer.this.cameraSession = (ResolutionCameraSession) cameraSession;
                createSessionCallback.onDone(cameraSession);
            }

            @Override
            public void onFailure(CameraSession.FailureType failureType, String s) {
                createSessionCallback.onFailure(failureType, s);
            }
        };

        ResolutionCameraSession.create(myCallback, events, captureToTexture, applicationContext, surfaceTextureHelper, Camera1Enumerator.getCameraIndex(cameraName), width, height, framerate);
    }

    public List<Camera.Size> getSupportedResolutions() {
        Camera camera = Camera.open(Camera1Enumerator.getCameraIndex(cameraName));
        List ret = Collections.EMPTY_LIST;
        if (camera != null) {
            ret = camera.getParameters().getSupportedVideoSizes();
            camera.release();
        }

        return ret;
    }
}
