package com.flashphoner.wcsexample.audio_chat;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.flashphoner.fpwcsapi.Flashphoner;
import com.flashphoner.fpwcsapi.bean.Connection;
import com.flashphoner.fpwcsapi.bean.Data;
import com.flashphoner.fpwcsapi.bean.StreamStatus;
import com.flashphoner.fpwcsapi.constraints.Constraints;
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
import com.flashphoner.fpwcsapi.session.StreamOptions;
import com.flashphoner.fpwcsapi.session.StreamStatusEvent;

import org.webrtc.SurfaceViewRenderer;

/**
 * Example for two way audio chat.
 * Can be used to participate in audio chat for two participants on Web Call Server.
 */
public class AudioChatActivity extends AppCompatActivity {

    private static String TAG = AudioChatActivity.class.getName();

    private static final int PUBLISH_REQUEST_CODE = 100;

    // UI references.
    private EditText mWcsUrlView;
    private EditText mLoginView;
    private TextView mConnectStatus;
    private Button mConnectButton;
    private EditText mJoinRoomView;
    private TextView mJoinStatus;
    private Button mJoinButton;

    private SeekBar mParticipantVolume;

    private TextView mParticipantName;

    private TextView mPublishStatus;
    private Button mPublishButton;
    private Switch mMuteAudio;
    private TextView mMessageHistory;
    private EditText mMessage;
    private Button mSendButton;

    /**
     * RoomManager object is used to manage connection to server and audio chat room.
     */
    private RoomManager roomManager;

    /**
     * Room object is used for work with the audio chat room, to which the user is joined.
     */
    private Room room;

    private SurfaceViewRenderer renderer;

    private Stream stream;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_audio_chat);

        renderer = new SurfaceViewRenderer(this);

        TextView policyTextView = (TextView) findViewById(R.id.privacy_policy);
        policyTextView.setMovementMethod(LinkMovementMethod.getInstance());
        String policyLink ="<a href=https://flashphoner.com/flashphoner-privacy-policy-for-android-tools/>Privacy Policy</a>";
        policyTextView.setText(Html.fromHtml(policyLink));

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
                                    stream = null;
                                    mConnectStatus.setText(connection.getStatus());
                                    mParticipantName.setText("NONE");
                                    renderer.release();
                                    mSendButton.setEnabled(false);
                                }
                            });
                        }
                    });

                    mConnectButton.setEnabled(false);

                    SharedPreferences sharedPref = AudioChatActivity.this.getPreferences(Context.MODE_PRIVATE);
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
         * The participant will join to audio chat room when Join button is clicked.
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
                     * The participant joins a audio chat room with method RoomManager.join().
                     * RoomOptions object is passed to the method.
                     * Room object is created and returned by the method.
                     */
                    room = roomManager.join(roomOptions);

                    /**
                     * Callback functions for events occurring in audio chat room are added.
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
                            if (room.getParticipants().size() >= 2) {
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
                                chatState.append(participant.getName()).append(",");
                                runOnUiThread(
                                        new Runnable() {
                                            @Override
                                            public void run() {
                                                participant.play(renderer);
                                                mParticipantName.setText(participant.getName());
                                            }
                                        }
                                );
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
                            runOnUiThread(
                                    new Runnable() {
                                        @Override
                                        public void run() {
                                            mParticipantName.setText(participant.getName());
                                            addMessageHistory(participant.getName(), "joined");
                                        }
                                    }
                            );
                        }

                        @Override
                        public void onLeft(final Participant participant) {
                            /**
                             * When one of the other participants leaves the room, player view assigned to that participant is freed.
                             */
                            runOnUiThread(
                                    new Runnable() {
                                        @Override
                                        public void run() {
                                            addMessageHistory(participant.getName(), "left");
                                            renderer.release();
                                            mParticipantName.setText("NONE");
                                        }
                                    }
                            );
                        }

                        @Override
                        public void onPublished(final Participant participant) {
                            /**
                             * When one of the other participants starts publishing, playback of the stream published by that participant is started.
                             */
                            runOnUiThread(
                                    new Runnable() {
                                        @Override
                                        public void run() {
                                            participant.play(renderer);
                                        }
                                    }
                            );
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
                    SharedPreferences sharedPref = AudioChatActivity.this.getPreferences(Context.MODE_PRIVATE);
                    SharedPreferences.Editor editor = sharedPref.edit();
                    editor.putString("join_room", mJoinRoomView.getText().toString());
                    editor.apply();
                } else {
                    final Runnable action = new Runnable() {
                        @Override
                        public void run() {
                            mJoinButton.setEnabled(true);
                            mJoinButton.setText(R.string.action_join);
                            mJoinButton.setTag(R.string.action_join);
                            mPublishButton.setEnabled(false);
                            mSendButton.setEnabled(false);
                            renderer.release();
                            mParticipantName.setText("NONE");
                        }
                    };
                    /**
                     * The participant leaves the audio chat room with method Room.leave().
                     */
                    room.leave(new RestAppCommunicator.Handler() {
                        @Override
                        public void onAccepted(Data data) {
                            runOnUiThread(action);
                        }

                        @Override
                        public void onRejected(Data data) {
                            runOnUiThread(action);
                        }
                    });
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
         * The participant starts publishing audio stream when Publish button is clicked.
         */
        mPublishButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mPublishButton.getTag() == null || Integer.valueOf(R.string.action_publish).equals(mPublishButton.getTag())) {
                    ActivityCompat.requestPermissions(AudioChatActivity.this,
                            new String[]{Manifest.permission.RECORD_AUDIO},
                            PUBLISH_REQUEST_CODE);
                } else {
                    mPublishButton.setEnabled(false);
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

        mParticipantName = (TextView) findViewById(R.id.participant_name);

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

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String permissions[], @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case PUBLISH_REQUEST_CODE: {
                if (grantResults.length == 0 ||
                        grantResults[0] != PackageManager.PERMISSION_GRANTED) {

                    Log.i(TAG, "Permission has been denied by user");
                } else {
                    /**
                     * Stream is created and published with method Room.publish().
                     */
                    StreamOptions streamOptions = new StreamOptions();
                    streamOptions.setConstraints(new Constraints(true, false));
                    stream = room.publish(null, streamOptions);

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
                                    } else {
                                        mPublishButton.setText(R.string.action_publish);
                                        mPublishButton.setTag(R.string.action_publish);
                                        mMuteAudio.setEnabled(false);
                                        mMuteAudio.setChecked(false);
                                        AudioChatActivity.this.stream = null;
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
                    Log.i(TAG, "Permission has been granted by user");
                }
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (roomManager != null) {
            roomManager.disconnect();
        }
    }

}

