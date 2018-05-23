package com.flashphoner.wcsexample.mediadevices;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.TextView;

import com.flashphoner.fpwcsapi.FPSurfaceViewRenderer;
import com.flashphoner.fpwcsapi.webrtc.MediaConnection;
import com.flashphoner.fpwcsapi.webrtc.WebRTCMediaProvider;

import org.webrtc.RendererCommon;

public class NewRenderer extends AppCompatActivity {

    private FPSurfaceViewRenderer newRender;
    private FPSurfaceViewRenderer oldRenderer;
    private MediaConnection mediaConnection;
    private TextView mResolutionView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_new_renderer);

        mResolutionView = (TextView) findViewById(R.id.resolution);
        newRender = (FPSurfaceViewRenderer) findViewById(R.id.test_video_view);
        newRender.setZOrderMediaOverlay(true);
        newRender.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);
        newRender.setMirror(true);
        newRender.requestLayout();
        try {
            newRender.init(null, new RendererCommon.RendererEvents() {
                @Override
                public void onFirstFrameRendered() {
                }

                @Override
                public void onFrameResolutionChanged(final int i, final int i1, int i2) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mResolutionView.setText(i + "x" + i1);
                        }
                    });
                }
            });
        } catch (IllegalStateException e) {
            //ignore
        }

        //newRender.init((((Map<String, Session>)Flashphoner.getSessions()).get(getIntent().getStringExtra("sid"))).getSessionOptions().getSharedContext(), null);
        mediaConnection = WebRTCMediaProvider.getInstance().getMediaConnection(getIntent().getStringExtra("id"));
        oldRenderer = (FPSurfaceViewRenderer) (mediaConnection.getLocalRenderer() != null ? mediaConnection.getLocalRenderer() : mediaConnection.getRemoteRenderer());
        mediaConnection.switchRenderer(newRender);
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    @Override
    public void onBackPressed() {
        mediaConnection.switchRenderer(oldRenderer);
        super.onBackPressed();
    }
}
