package com.flashphoner.wcsexample.conference;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import com.flashphoner.fpwcsapi.Flashphoner;
import com.flashphoner.fpwcsapi.bean.Connection;
import com.flashphoner.fpwcsapi.bean.Data;
import com.flashphoner.fpwcsapi.bean.StreamStatus;
import com.flashphoner.fpwcsapi.layout.PercentFrameLayout;
import com.flashphoner.fpwcsapi.room.Message;
import com.flashphoner.fpwcsapi.room.Participant;
import com.flashphoner.fpwcsapi.room.Room;
import com.flashphoner.fpwcsapi.room.RoomEvent;
import com.flashphoner.fpwcsapi.room.RoomManager;
import com.flashphoner.fpwcsapi.room.RoomManagerEvent;
import com.flashphoner.fpwcsapi.room.RoomManagerOptions;
import com.flashphoner.fpwcsapi.room.RoomOptions;
import com.flashphoner.fpwcsapi.session.RestAppCommunicator;
import com.flashphoner.fpwcsapi.session.Stream;
import com.flashphoner.fpwcsapi.session.StreamStatusEvent;

import org.webrtc.RendererCommon;
import org.webrtc.SurfaceViewRenderer;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Example for video conference.
 * Can be used to participate in video conference for three participants on Web Call Server.
 */
public class ConferenceActivity extends AppCompatActivity {

    // UI references.
    private EditText mWcsUrlView;
    private EditText mLoginView;
    private TextView mConnectStatus;
    private Button mConnectButton;
    private EditText mJoinRoomView;
    private TextView mJoinStatus;
    private Button mJoinButton;

    private SeekBar mParticipantVolume;

    private TextView mParticipant1Name;
    private TextView mParticipant2Name;

    private TextView mPublishStatus;
    private Button mPublishButton;
    private Switch mMuteAudio;
    private Switch mMuteVideo;
    private TextView mMessageHistory;
    private EditText mMessage;
    private Button mSendButton;

    /**
     * RoomManager object is used to manage connection to server and conference room.
     */
    private RoomManager roomManager;

    /**
     * Room object is used for work with the conference room, to which the user is joined.
     */
    private Room room;

    private SurfaceViewRenderer localRenderer;

    private Queue<ParticipantView> freeViews = new LinkedList<>();
    private Map<String, ParticipantView> busyViews = new ConcurrentHashMap<>();

    private Stream stream;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_conference);

        /**
         * Initialization of the API.
         */
        Flashphoner.init(this);

        mParticipantVolume = (SeekBar) findViewById(R.id.participant_volume);
        mParticipantVolume.setMax(Flashphoner.getMaxVolume());
        mParticipantVolume.setProgress(Flashphoner.getVolume());
        mParticipantVolume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                Flashphoner.setVolume(i);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        mWcsUrlView = (EditText) findViewById(R.id.wcs_url);
        SharedPreferences sharedPref = this.getPreferences(Context.MODE_PRIVATE);
        mWcsUrlView.setText(sharedPref.getString("wcs_url", getString(R.string.wcs_url)));
        mLoginView = (EditText) findViewById(R.id.login);
        mLoginView.setText(sharedPref.getString("login", ""));
        mConnectStatus = (TextView) findViewById(R.id.connect_status);
        mConnectButton = (Button) findViewById(R.id.connect_button);

        /**
         * Connection to server will be established when Connect button is clicked.
         */
        mConnectButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mConnectButton.getTag() == null || Integer.valueOf(R.string.action_connect).equals(mConnectButton.getTag())) {
                    /**
                     * The connection options are set.
                     * WCS server URL and user name are passed when RoomManagerOptions object is created.
                     */
                    RoomManagerOptions roomManagerOptions = new RoomManagerOptions(mWcsUrlView.getText().toString(), mLoginView.getText().toString());

                    /**
                     * RoomManager object is created with method createRoomManager().
                     * Connection session is created when RoomManager object is created.
                     */
                    roomManager = Flashphoner.createRoomManager(roomManagerOptions);

                    /**
                     * Callback functions for connection status events are added to make appropriate changes in controls of the interface when connection is established and closed.
                     */
                    roomManager.on(new RoomManagerEvent() {
                        @Override
                        public void onConnected(final Connection connection) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    mConnectButton.setText(R.string.action_disconnect);
                                    mConnectButton.setTag(R.string.action_disconnect);
                                    mConnectButton.setEnabled(true);
                                    mConnectStatus.setText(connection.getStatus());
                                    mJoinButton.setEnabled(true);
                                }
                            });
                        }

                        @Override
                        public void onDisconnection(final Connection connection) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    mConnectButton.setText(R.string.action_connect);
                                    mConnectButton.setTag(R.string.action_connect);
                                    mConnectButton.setEnabled(true);
                                    mJoinButton.setText(R.string.action_join);
                                    mJoinButton.setTag(R.string.action_join);
                                    mJoinButton.setEnabled(false);
                                    mPublishStatus.setText("");
                                    mPublishButton.setText(R.string.action_publish);
                                    mPublishButton.setTag(R.string.action_publish);
                                    mPublishButton.setEnabled(false);
                                    mMuteAudio.setEnabled(false);
                                    mMuteAudio.setChecked(false);
                                    mMuteVideo.setEnabled(false);
                                    mMuteVideo.setChecked(false);
                                    stream = null;
                                    mConnectStatus.setText(connection.getStatus());
                                    Iterator<Map.Entry<String, ParticipantView>> i = busyViews.entrySet().iterator();
                                    while (i.hasNext()) {
                                        Map.Entry<String, ParticipantView> e = i.next();
                                        e.getValue().login.setText("NONE");
                                        e.getValue().surfaceViewRenderer.release();
                                        i.remove();
                                        freeViews.add(e.getValue());
                                    }
                                    mSendButton.setEnabled(false);
                                }
                            });
                        }
                    });

                    mConnectButton.setEnabled(false);

                    SharedPreferences sharedPref = ConferenceActivity.this.getPreferences(Context.MODE_PRIVATE);
                    SharedPreferences.Editor editor = sharedPref.edit();
                    editor.putString("wcs_url", mWcsUrlView.getText().toString());
                    editor.putString("login", mLoginView.getText().toString());
                    editor.apply();
                } else {
                    mConnectButton.setEnabled(false);

                    /**
                     * Connection to WCS server is closed with method RoomManager.disconnect().
                     */
                    roomManager.disconnect();
                }

                View currentFocus = getCurrentFocus();
                if (currentFocus != null) {
                    InputMethodManager inputManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    inputManager.hideSoftInputFromWindow(currentFocus.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
                }
            }
        });

        mJoinRoomView = (EditText) findViewById(R.id.join_room);
        mJoinRoomView.setText(sharedPref.getString("join_room", ""));
        mJoinStatus = (TextView) findViewById(R.id.join_status);
        mJoinButton = (Button) findViewById(R.id.join_button);

        /**
         * The participant will join to conference room when Join button is clicked.
         */
        mJoinButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                mJoinButton.setEnabled(false);
                if (mJoinButton.getTag() == null || Integer.valueOf(R.string.action_join).equals(mJoinButton.getTag())) {
                    /**
                     * Room name is set with method RoomOptions.setName().
                     */
                    RoomOptions roomOptions = new RoomOptions();
                    roomOptions.setName(mJoinRoomView.getText().toString());

                    /**
                     * The participant joins a conference room with method RoomManager.join().
                     * RoomOptions object is passed to the method.
                     * Room object is created and returned by the method.
                     */
                    room = roomManager.join(roomOptions);

                    /**
                     * Callback functions for events occurring in conference room are added.
                     * If the event is related to actions performed by one of the other participants, Participant object with data of that participant is passed to the corresponding function.
                     */
                    room.on(new RoomEvent() {
                        @Override
                        public void onState(final Room room) {
                            /**
                             * After joining, Room object with data of the room is received.
                             * Method Room.getParticipants() is used to check the number of already connected participants.
                             * The method returns collection of Participant objects.
                             * The collection size is determined, and, if the maximum allowed number (in this case, three) has already been reached, the user leaves the room with method Room.leave().
                             */
                            if (room.getParticipants().size() >= 3) {
                                room.leave(null);
                                runOnUiThread(
                                        new Runnable() {
                                            @Override
                                            public void run() {
                                                mJoinStatus.setText("Room is full");
                                                mJoinButton.setEnabled(true);
                                            }
                                        }
                                );
                                return;
                            }


                            final StringBuffer chatState = new StringBuffer("participants: ");

                            /**
                             * Iterating through the collection of the other participants returned by method Room.getParticipants().
                             * There is corresponding Participant object for each participant.
                             */
                            for (final Participant participant : room.getParticipants()) {
                                /**
                                 * A player view is assigned to each of the other participants in the room.
                                 */
                                final ParticipantView participantView = freeViews.poll();
                                if (participantView != null) {
                                    chatState.append(participant.getName()).append(",");
                                    busyViews.put(participant.getName(), participantView);

                                    /**
                                     * Playback of the stream being published by the other participant is started with method Participant.play().
                                     * SurfaceViewRenderer to be used to display the video stream is passed when the method is called.
                                     */
                                    participant.play(participantView.surfaceViewRenderer);
                                    runOnUiThread(
                                            new Runnable() {
                                                @Override
                                                public void run() {
                                                    participantView.login.setText(participant.getName());
                                                }
                                            }
                                    );
                                }
                            }
                            runOnUiThread(
                                    new Runnable() {
                                        @Override
                                        public void run() {
                                            mJoinButton.setText(R.string.action_leave);
                                            mJoinButton.setTag(R.string.action_leave);
                                            mJoinButton.setEnabled(true);
                                            mJoinStatus.setText("");
                                            mPublishButton.setEnabled(true);
                                            mSendButton.setEnabled(true);
                                            if (room.getParticipants().size() == 0) {
                                                addMessageHistory("chat", "room is empty");
                                            } else {
                                                addMessageHistory("chat", chatState.substring(0, chatState.length() - 1));
                                            }
                                        }
                                    }
                            );
                        }

                        @Override
                        public void onJoined(final Participant participant) {
                            /**
                             * When a new participant joins the room, a player view is assigned to that participant.
                             */
                            final ParticipantView participantView = freeViews.poll();
                            if (participantView != null) {
                                runOnUiThread(
                                        new Runnable() {
                                            @Override
                                            public void run() {
                                                participantView.login.setText(participant.getName());
                                                addMessageHistory(participant.getName(), "joined");
                                            }
                                        }
                                );
                                busyViews.put(participant.getName(), participantView);
                            }
                        }

                        @Override
                        public void onLeft(final Participant participant) {
                            /**
                             * When one of the other participants leaves the room, player view assigned to that participant is freed.
                             */
                            final ParticipantView participantView = busyViews.remove(participant.getName());
                            if (participantView != null) {
                                runOnUiThread(
                                        new Runnable() {
                                            @Override
                                            public void run() {
                                                participantView.login.setText("NONE");
                                                addMessageHistory(participant.getName(), "left");
                                                participantView.surfaceViewRenderer.release();
                                            }
                                        }
                                );
                                freeViews.add(participantView);
                            }
                        }

                        @Override
                        public void onPublished(final Participant participant) {
                            /**
                             * When one of the other participants starts publishing, playback of the stream published by that participant is started.
                             */
                            final ParticipantView participantView = busyViews.get(participant.getName());
                            if (participantView != null) {
                                participant.play(participantView.surfaceViewRenderer);
                            }
                        }

                        @Override
                        public void onFailed(Room room, final String info) {
                            room.leave(null);
                            runOnUiThread(
                                    new Runnable() {
                                        @Override
                                        public void run() {
                                            mJoinStatus.setText(info);
                                            mJoinButton.setEnabled(true);
                                        }
                                    }
                            );
                        }

                        @Override
                        public void onMessage(final Message message) {
                            /**
                             * When one of the participants sends a text message, the received message is added to the messages log.
                             */
                            runOnUiThread(
                                    new Runnable() {
                                        @Override
                                        public void run() {
                                            addMessageHistory(message.getFrom(), message.getText());
                                        }
                                    });
                        }
                    });
                    SharedPreferences sharedPref = ConferenceActivity.this.getPreferences(Context.MODE_PRIVATE);
                    SharedPreferences.Editor editor = sharedPref.edit();
                    editor.putString("join_room", mJoinRoomView.getText().toString());
                    editor.apply();
                } else {
                    /**
                     * The participant leaves the conference room with method Room.leave().
                     */
                    room.leave(new RestAppCommunicator.Handler() {
                        @Override
                        public void onAccepted(Data data) {
                            runOnUiThread(new Runnable() {
                                              @Override
                                              public void run() {
                                                  mJoinButton.setEnabled(true);
                                              }
                                          }
                            );
                        }

                        @Override
                        public void onRejected(Data data) {
                            runOnUiThread(new Runnable() {
                                              @Override
                                              public void run() {
                                                  mJoinButton.setEnabled(true);
                                              }
                                          }
                            );
                        }
                    });
                    mJoinButton.setText(R.string.action_join);
                    mJoinButton.setTag(R.string.action_join);
                    mPublishButton.setEnabled(false);
                    mSendButton.setEnabled(false);

                    /**
                     * The player views assigned to the other participants are freed.
                     */
                    Iterator<Map.Entry<String, ParticipantView>> i = busyViews.entrySet().iterator();
                    while (i.hasNext()) {
                        Map.Entry<String, ParticipantView> e = i.next();
                        e.getValue().login.setText("NONE");
                        e.getValue().surfaceViewRenderer.release();
                        i.remove();
                        freeViews.add(e.getValue());
                    }
                }
                View currentFocus = getCurrentFocus();
                if (currentFocus != null) {
                    InputMethodManager inputManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    inputManager.hideSoftInputFromWindow(currentFocus.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
                }
            }
        });

        mPublishStatus = (TextView) findViewById(R.id.publish_status);
        mPublishButton = (Button) findViewById(R.id.publish_button);

        /**
         * The participant starts publishing video stream when Publish button is clicked.
         */
        mPublishButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                mPublishButton.setEnabled(false);
                if (mPublishButton.getTag() == null || Integer.valueOf(R.string.action_publish).equals(mPublishButton.getTag())) {
                    /**
                     * Stream is created and published with method Room.publish().
                     * SurfaceViewRenderer to be used to display video from the camera is passed to the method.
                     */
                    stream = room.publish(localRenderer);

                    /**
                     * Callback function for stream status change is added to make appropriate changes in controls of the interface when stream is being published.
                     */
                    stream.on(new StreamStatusEvent() {
                        @Override
                        public void onStreamStatus(final Stream stream, final StreamStatus streamStatus) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    if (StreamStatus.PUBLISHING.equals(streamStatus)) {
                                        mPublishButton.setText(R.string.action_stop);
                                        mPublishButton.setTag(R.string.action_stop);
                                        mMuteAudio.setEnabled(true);
                                        mMuteVideo.setEnabled(true);
                                    } else {
                                        mPublishButton.setText(R.string.action_publish);
                                        mPublishButton.setTag(R.string.action_publish);
                                        mMuteAudio.setEnabled(false);
                                        mMuteAudio.setChecked(false);
                                        mMuteVideo.setEnabled(false);
                                        mMuteVideo.setChecked(false);
                                        ConferenceActivity.this.stream = null;
                                    }
                                    if (mJoinButton.getTag() == null || Integer.valueOf(R.string.action_join).equals(mJoinButton.getTag())) {
                                        mPublishButton.setEnabled(false);
                                    } else {
                                        mPublishButton.setEnabled(true);
                                    }
                                    mPublishStatus.setText(streamStatus.toString());
                                }
                            });
                        }
                    });
                } else {
                    /**
                     * Stream is unpublished with method Room.unpublish().
                     */
                    room.unpublish();
                }
                View currentFocus = getCurrentFocus();
                if (currentFocus != null) {
                    InputMethodManager inputManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    inputManager.hideSoftInputFromWindow(currentFocus.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
                }
            }
        });

        /**
         * MuteAudio switch is used to mute/unmute audio of the published stream.
         * Audio is muted with method Stream.muteAudio() and unmuted with method Stream.unmuteAudio().
         */
        mMuteAudio = (Switch) findViewById(R.id.mute_audio);
        mMuteAudio.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    stream.muteAudio();
                } else {
                    stream.unmuteAudio();
                }
            }
        });

        /**
         * MuteVideo switch is used to mute/unmute video of the published stream.
         * Video is muted with method Stream.muteVideo() and unmuted with method Stream.unmuteVideo().
         */
        mMuteVideo = (Switch) findViewById(R.id.mute_video);
        mMuteVideo.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    stream.muteVideo();
                } else {
                    stream.unmuteVideo();
                }
            }
        });


        localRenderer = (SurfaceViewRenderer) findViewById(R.id.local_video_view);
        PercentFrameLayout localRenderLayout = (PercentFrameLayout) findViewById(R.id.local_video_layout);
        localRenderLayout.setPosition(0, 0, 100, 100);
        localRenderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);
        localRenderer.setMirror(true);
        localRenderer.requestLayout();


        SurfaceViewRenderer remote1Render = (SurfaceViewRenderer) findViewById(R.id.remote1_video_view);
        PercentFrameLayout remote1RenderLayout = (PercentFrameLayout) findViewById(R.id.remove1_video_layout);
        remote1RenderLayout.setPosition(0, 0, 100, 100);
        remote1Render.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);
        remote1Render.setMirror(false);
        remote1Render.requestLayout();

        SurfaceViewRenderer remote2Render = (SurfaceViewRenderer) findViewById(R.id.remote2_video_view);
        PercentFrameLayout remote2RenderLayout = (PercentFrameLayout) findViewById(R.id.remote2_video_layout);
        remote2RenderLayout.setPosition(0, 0, 100, 100);
        remote2Render.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);
        remote2Render.setMirror(false);
        remote2Render.requestLayout();


        mParticipant1Name = (TextView) findViewById(R.id.participant1_name);
        freeViews.add(new ParticipantView(remote1Render, mParticipant1Name));
        mParticipant2Name = (TextView) findViewById(R.id.participant2_name);
        freeViews.add(new ParticipantView(remote2Render, mParticipant2Name));


        mMessageHistory = (TextView) findViewById(R.id.message_history);
        mMessageHistory.setMovementMethod(new ScrollingMovementMethod());
        mMessage = (EditText) findViewById(R.id.message);
        mSendButton = (Button) findViewById(R.id.send_button);

        mSendButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                String text = mMessage.getText().toString();
                if (!"".equals(text)) {
                    for (Participant participant : room.getParticipants()) {
                        participant.sendMessage(text);
                    }
                    addMessageHistory(mLoginView.getText().toString(), text);
                    mMessage.setText("");
                }
            }
        });
    }

    void addMessageHistory(String login, String text) {
        String message = login + " - " + text + "\n";
        mMessageHistory.setText(message + mMessageHistory.getText());
    }

    private class ParticipantView {

        SurfaceViewRenderer surfaceViewRenderer;
        TextView login;

        public ParticipantView(SurfaceViewRenderer surfaceViewRenderer, TextView login) {
            this.surfaceViewRenderer = surfaceViewRenderer;
            this.login = login;
        }

    }
}

