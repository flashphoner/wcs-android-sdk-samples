package com.flashphoner.wcsexample.mediadevices;

/**
 * Created by andrey on 28/07/2017.
 */

import android.content.Context;
import android.media.MediaRecorder;

import java.io.IOException;
import java.util.Timer;

public class SoundMeter {
    // This file is used to record voice
    static final private double EMA_FILTER = 0.6;

    private MediaRecorder mRecorder = null;
    private double mEMA = 0.0;
    private Context context;

    private Timer timer;

    public SoundMeter(Context context) {
        this.context = context;
    }

    public void start() {
        if (timer == null) {
            timer = new Timer();
        }

        if (mRecorder == null) {

            mRecorder = new MediaRecorder();
            mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
            mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            String fileName = context.getFilesDir().getAbsolutePath() + "/test.3gp";
            //Path /dev/null throw exception on Android 11
            mRecorder.setOutputFile(fileName);

            try {
                mRecorder.prepare();
            } catch (IllegalStateException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

            mRecorder.start();
            mEMA = 0.0;
        }
    }

    public void stop() {
        if (mRecorder != null) {
            mRecorder.stop();
            mRecorder.release();
            mRecorder = null;
        }
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }

    public double getAmplitude() {
        if (mRecorder != null)
            return (mRecorder.getMaxAmplitude() / 2700.0);
        else
            return 0;

    }

    public double getAmplitudeEMA() {
        double amp = getAmplitude();
        mEMA = EMA_FILTER * amp + (1.0 - EMA_FILTER) * mEMA;
        return mEMA;
    }

    public Timer getTimer() {
        return timer;
    }
}