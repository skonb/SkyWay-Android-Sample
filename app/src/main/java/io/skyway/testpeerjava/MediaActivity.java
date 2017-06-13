package io.skyway.testpeerjava;

import io.skyway.Peer.Browser.Canvas;
import io.skyway.Peer.Browser.MediaConstraints;
import io.skyway.Peer.Browser.MediaStream;
import io.skyway.Peer.Browser.Navigator;
import io.skyway.Peer.CallOption;
import io.skyway.Peer.MediaConnection;
import io.skyway.Peer.OnCallback;
import io.skyway.Peer.Peer;
import io.skyway.Peer.PeerError;
import io.skyway.Peer.PeerOption;

import android.Manifest;
import android.app.Activity;
import android.app.FragmentManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.amazonaws.auth.CognitoCachingCredentialsProvider;
import com.amazonaws.mobileconnectors.iot.AWSIotMqttClientStatusCallback;
import com.amazonaws.mobileconnectors.iot.AWSIotMqttManager;
import com.amazonaws.mobileconnectors.iot.AWSIotMqttMessageDeliveryCallback;
import com.amazonaws.mobileconnectors.iot.AWSIotMqttNewMessageCallback;
import com.amazonaws.mobileconnectors.iot.AWSIotMqttQos;
import com.amazonaws.regions.Regions;
import com.github.bassaer.chatmessageview.models.Message;
import com.github.bassaer.chatmessageview.models.User;
import com.github.bassaer.chatmessageview.views.ChatView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.util.HashMap;


/**
 *
 */
public class MediaActivity
        extends Activity {
    private static final String TAG = MediaActivity.class.getSimpleName();

    private Peer _peer;
    private MediaConnection _media;

    private MediaStream _msLocal;
    private MediaStream _msRemote;

    private Handler _handler;

    private String _id;
    private String[] _listPeerIds;
    private boolean _bCalling;
    private boolean _videoEnabled = true;
    private boolean _audioEnabled = true;
    private ChatView mChatView;
    private AWSIotMqttManager _mqttManager;
    private static String ENDPOINT = "a3xyvz7ca0wh8.iot.ap-northeast-1.amazonaws.com";
    private static String IDENTITY_POOL_ID = "ap-northeast-1:8848275d-4dbc-4acb-a0ff-2c70acb57760";
    private CognitoCachingCredentialsProvider _credentialsProvider;

    private String _topic;
    private User _myChatUser = new User(0, "", null);
    private User _peerChatUser = new User(1, "", null);


    protected void initializeMQTTSession(String id) {
        if (_mqttManager == null) {
            _mqttManager = new AWSIotMqttManager(id, ENDPOINT);
            _mqttManager.connect(_credentialsProvider, new AWSIotMqttClientStatusCallback() {
                @Override
                public void onStatusChanged(AWSIotMqttClientStatus status, Throwable throwable) {

                }
            });

        }
    }

    public void updateTopic(String newTopic) {
        if (_topic != null) {
            endListeningMQTTTopic();
        }
        _topic = newTopic;
    }

    protected void startListeningMQTTTopic() {
        if (_mqttManager != null) {
            _mqttManager.subscribeToTopic(_topic, AWSIotMqttQos.QOS0, new AWSIotMqttNewMessageCallback() {
                @Override
                public void onMessageArrived(String topic, byte[] data) {
                    try {
                        String str = new String(data, "UTF-8");
                        JSONObject json = new JSONObject(str);
                        String from = json.getString("from");
                        if (!from.equals(_id)) {
                            String messageStr = json.getString("message");
                            final Message message = new Message.Builder()
                                    .setUser(_peerChatUser)
                                    .setRightMessage(false)
                                    .setMessageText(messageStr)
                                    .build();
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    mChatView.receive(message);
                                }
                            });
                        }
                    } catch (JSONException e) {

                    } catch (UnsupportedEncodingException e) {


                    }
                }
            });
        }
    }

    protected void endListeningMQTTTopic() {
        if (_mqttManager != null) {
            _mqttManager.unsubscribeTopic(_topic);
        }
    }

    protected void sendMessage(String message) {
        if (_mqttManager != null) {
            JSONObject json = new JSONObject();
            try {
                json.put("from", _id);
                json.put("message", message);
            } catch (JSONException e) {

            }
            _mqttManager.publishString(json.toString(), _topic, AWSIotMqttQos.QOS0, new AWSIotMqttMessageDeliveryCallback() {
                @Override
                public void statusChanged(MessageDeliveryStatus status, Object userData) {
                    switch (status) {
                        case Success: {
                            String messageStr = (String) userData;
                            final Message message = new Message.Builder()
                                    .setUser(_myChatUser)
                                    .setRightMessage(true)
                                    .setMessageText(messageStr)
                                    .build();
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    mChatView.send(message);
                                    mChatView.setInputText(null);
                                }
                            });
                        }
                        break;
                        case Fail:
                            break;
                    }
                }
            }, message);
        }
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


        Window wnd = getWindow();
        wnd.addFlags(Window.FEATURE_NO_TITLE);

        setContentView(R.layout.activity_video_chat);

        View v = findViewById(android.R.id.content);
        if (v != null) {
            v.setKeepScreenOn(true);
        }

        _handler = new Handler(Looper.getMainLooper());
        Context context = getApplicationContext();

        _credentialsProvider = new CognitoCachingCredentialsProvider(
                getApplicationContext(),
                IDENTITY_POOL_ID, // Identity Pool ID
                Regions.AP_NORTHEAST_1 // Region
        );


        mChatView = (ChatView) findViewById(R.id.chat_view);

        mChatView.setOnClickSendButtonListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String messageStr = mChatView.getInputText();
                sendMessage(messageStr);
            }

        });

        //////////////////////////////////////////////////////////////////////
        //////////////////  START: Initialize SkyWay Peer ////////////////////
        //////////////////////////////////////////////////////////////////////

        // Please check this page. >> https://skyway.io/ds/
        PeerOption options = new PeerOption();

        //Enter your API Key.
        options.key = "4050f535-fc75-468e-82f8-a4d5c16cf7bf";
        //Enter your registered Domain.
        options.domain = "value-domain.com";


        // SKWPeer has many options. Please check the document. >> http://nttcom.github.io/skyway/docs/

        _peer = new Peer(context, options);
        setPeerCallback(_peer);

        //////////////////////////////////////////////////////////////////////
        ////////////////// END: Initialize SkyWay Peer ///////////////////////
        //////////////////////////////////////////////////////////////////////


        //request permissions
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO}, 0);
        } else {
            startLocalStream(_videoEnabled);
        }


        _bCalling = false;


        //
        // Initialize views
        //
        Button btnAction = (Button) findViewById(R.id.btnAction);
        btnAction.setEnabled(true);
        btnAction.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.setEnabled(false);

                if (!_bCalling) {
                    listingPeers();
                } else {
                    closing();
                }

                v.setEnabled(true);
            }
        });

        //
        Button switchCameraAction = (Button) findViewById(R.id.switchCameraAction);
        switchCameraAction.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (null != _msLocal) {
                    Boolean result = _msLocal.switchCamera();
                    if (true == result) {
                        //Success
                    } else {
                        //Failed
                    }
                }

            }
        });

        final ToggleButton videoOnOffButton = (ToggleButton) findViewById(R.id.videoToggle);
        videoOnOffButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                _videoEnabled = b;
                for (int i = 0; i < _msLocal.getVideoTracks(); ++i) {
                    _msLocal.setEnableVideoTrack(i, _videoEnabled);
                }
            }
        });

        final ToggleButton audioOnOffButton = (ToggleButton) findViewById(R.id.audioToggle);
        audioOnOffButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                _audioEnabled = b;
                for (int i = 0; i < _msLocal.getAudioTracks(); ++i) {
                    _msLocal.setEnableAudioTrack(i, _audioEnabled);
                }
            }
        });

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case 0: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startLocalStream(_videoEnabled);
                } else {
                    Toast.makeText(this, "Failed to access the camera and microphone.\nclick allow when asked for permission.", Toast.LENGTH_LONG).show();
                }
                break;
            }
        }
    }

    void startLocalStream(boolean videoEnabled) {
        Navigator.initialize(_peer);
        MediaConstraints constraints = new MediaConstraints();
        constraints.videoFlag = videoEnabled;
        _msLocal = Navigator.getUserMedia(constraints);

        Canvas canvas = (Canvas) findViewById(R.id.svSecondary);
        canvas.addSrc(_msLocal, 0);
    }


    /**
     * Media connecting to remote peer.
     *
     * @param strPeerId Remote peer.
     */
    void calling(String strPeerId) {
        //////////////////////////////////////////////////////////////////////
        ////////////////// START: Calling SkyWay Peer   //////////////////////
        //////////////////////////////////////////////////////////////////////

        if (null == _peer) {
            return;
        }

        if (null != _media) {
            _media.close();
            _media = null;
        }

        CallOption option = new CallOption();

        _media = _peer.call(strPeerId, _msLocal, option);

        if (null != _media) {
            setMediaCallback(_media);

            _bCalling = true;
        }

        if (_bCalling) {
            updateTopic(_id);
            startListeningMQTTTopic();
        }

        //////////////////////////////////////////////////////////////////////
        /////////////////// END: Calling SkyWay Peer   ///////////////////////
        //////////////////////////////////////////////////////////////////////


        updateUI();
    }


    //////////Start:Set Peer callback////////////////
    ////////////////////////////////////////////////
    private void setPeerCallback(Peer peer) {
        //////////////////////////////////////////////////////////////////////////////////
        ///////////////////// START: Set SkyWay peer callback   //////////////////////////
        //////////////////////////////////////////////////////////////////////////////////

        // !!!: Event/Open
        peer.on(Peer.PeerEventEnum.OPEN, new OnCallback() {
            @Override
            public void onCallback(Object object) {
                Log.d(TAG, "[On/Open]");

                if (object instanceof String) {
                    _id = (String) object;
                    initializeMQTTSession(_id);
                    Log.d(TAG, "ID:" + _id);

                    updateUI();
                }
            }
        });

        // !!!: Event/Call
        peer.on(Peer.PeerEventEnum.CALL, new OnCallback() {
            @Override
            public void onCallback(Object object) {
                Log.d(TAG, "[On/Call]");
                if (!(object instanceof MediaConnection)) {
                    return;
                }

                _media = (MediaConnection) object;

                _media.answer(_msLocal);

                setMediaCallback(_media);

                _bCalling = true;

                updateTopic(_media.peer);
                startListeningMQTTTopic();


                updateUI();
            }
        });

        // !!!: Event/Close
        peer.on(Peer.PeerEventEnum.CLOSE, new OnCallback() {
            @Override
            public void onCallback(Object object) {
                Log.d(TAG, "[On/Close]");

            }
        });

        // !!!: Event/Disconnected
        peer.on(Peer.PeerEventEnum.DISCONNECTED, new OnCallback() {
            @Override
            public void onCallback(Object object) {
                Log.d(TAG, "[On/Disconnected]");
            }
        });

        // !!!: Event/Error
        peer.on(Peer.PeerEventEnum.ERROR, new OnCallback() {
            @Override
            public void onCallback(Object object) {
                PeerError error = (PeerError) object;

                Log.d(TAG, "[On/Error]" + error);

                String strMessage = "" + error;
                String strLabel = getString(android.R.string.ok);

                MessageDialogFragment dialog = new MessageDialogFragment();
                dialog.setPositiveLabel(strLabel);
                dialog.setMessage(strMessage);

                dialog.show(getFragmentManager(), "error");
            }
        });

        //////////////////////////////////////////////////////////////////////////////////
        /////////////////////// END: Set SkyWay peer callback   //////////////////////////
        //////////////////////////////////////////////////////////////////////////////////
    }


    //Unset peer callback
    void unsetPeerCallback(Peer peer) {
        peer.on(Peer.PeerEventEnum.OPEN, null);
        peer.on(Peer.PeerEventEnum.CONNECTION, null);
        peer.on(Peer.PeerEventEnum.CALL, null);
        peer.on(Peer.PeerEventEnum.CLOSE, null);
        peer.on(Peer.PeerEventEnum.DISCONNECTED, null);
        peer.on(Peer.PeerEventEnum.ERROR, null);
    }


    void setMediaCallback(MediaConnection media) {
        //////////////////////////////////////////////////////////////////////////////////
        //////////////  START: Set SkyWay peer Media connection callback   ///////////////
        //////////////////////////////////////////////////////////////////////////////////

        // !!!: MediaEvent/Stream
        media.on(MediaConnection.MediaEventEnum.STREAM, new OnCallback() {
            @Override
            public void onCallback(Object object) {
                _msRemote = (MediaStream) object;

                Canvas canvas = (Canvas) findViewById(R.id.svPrimary);
                canvas.addSrc(_msRemote, 0);
            }
        });

        // !!!: MediaEvent/Close
        media.on(MediaConnection.MediaEventEnum.CLOSE, new OnCallback() {
            @Override
            public void onCallback(Object object) {
                if (null == _msRemote) {
                    return;
                }

                Canvas canvas = (Canvas) findViewById(R.id.svPrimary);
                canvas.removeSrc(_msRemote, 0);

                _msRemote = null;

                _media = null;
                _bCalling = false;

                updateUI();
            }
        });

        // !!!: MediaEvent/Error
        media.on(MediaConnection.MediaEventEnum.ERROR, new OnCallback() {
            @Override
            public void onCallback(Object object) {
                PeerError error = (PeerError) object;

                Log.d(TAG, "[On/MediaError]" + error);

                String strMessage = "" + error;
                String strLabel = getString(android.R.string.ok);

                MessageDialogFragment dialog = new MessageDialogFragment();
                dialog.setPositiveLabel(strLabel);
                dialog.setMessage(strMessage);

                dialog.show(getFragmentManager(), "error");
            }
        });

        //////////////////////////////////////////////////////////////////////////////////
        ///////////////  END: Set SkyWay peer Media connection callback   ////////////////
        //////////////////////////////////////////////////////////////////////////////////
    }


    //Unset media connection event callback.
    void unsetMediaCallback(MediaConnection media) {
        media.on(MediaConnection.MediaEventEnum.STREAM, null);
        media.on(MediaConnection.MediaEventEnum.CLOSE, null);
        media.on(MediaConnection.MediaEventEnum.ERROR, null);
    }

    // Listing all peers
    void listingPeers() {
        if ((null == _peer) || (null == _id) || (0 == _id.length())) {
            return;
        }

        _peer.listAllPeers(new OnCallback() {
            @Override
            public void onCallback(Object object) {
                if (!(object instanceof JSONArray)) {
                    return;
                }

                JSONArray peers = (JSONArray) object;

                StringBuilder sbItems = new StringBuilder();
                for (int i = 0; peers.length() > i; i++) {
                    String strValue = "";
                    try {
                        strValue = peers.getString(i);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    if (0 == _id.compareToIgnoreCase(strValue)) {
                        continue;
                    }

                    if (0 < sbItems.length()) {
                        sbItems.append(",");
                    }

                    sbItems.append(strValue);
                }

                String strItems = sbItems.toString();
                _listPeerIds = strItems.split(",");

                if ((null != _listPeerIds) && (0 < _listPeerIds.length)) {
                    selectingPeer();
                }
            }
        });

    }

    /**
     * Selecting peer
     */
    void selectingPeer() {
        if (null == _handler) {
            return;
        }

        _handler.post(new Runnable() {
            @Override
            public void run() {
                FragmentManager mgr = getFragmentManager();

                PeerListDialogFragment dialog = new PeerListDialogFragment();
                dialog.setListener(
                        new PeerListDialogFragment.PeerListDialogFragmentListener() {
                            @Override
                            public void onItemClick(final String item) {

                                _handler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        calling(item);
                                    }
                                });
                            }
                        });
                dialog.setItems(_listPeerIds);

                dialog.show(mgr, "peerlist");
            }
        });
    }


    /**
     * Closing connection.
     */
    void closing() {
        if (false == _bCalling) {
            return;
        }

        _bCalling = false;

        if (null != _media) {
            _media.close();
            _mqttManager.unsubscribeTopic(_topic);
            _topic = null;
        }
    }

    void updateUI() {
        _handler.post(new Runnable() {
            @Override
            public void run() {
                Button btnAction = (Button) findViewById(R.id.btnAction);
                if (null != btnAction) {
                    if (false == _bCalling) {
                        btnAction.setText("Calling");
                    } else {
                        btnAction.setText("Hang up");
                    }
                }

                TextView tvOwnId = (TextView) findViewById(R.id.tvOwnId);
                if (null != tvOwnId) {
                    if (null == _id) {
                        tvOwnId.setText("");
                    } else {
                        tvOwnId.setText(_id);
                    }
                }
            }
        });
    }


    /**
     * Destroy Peer object.
     */
    private void destroyPeer() {
        closing();

        if (null != _msRemote) {
            Canvas canvas = (Canvas) findViewById(R.id.svPrimary);
            canvas.removeSrc(_msRemote, 0);

            _msRemote.close();

            _msRemote = null;
        }

        if (null != _msLocal) {
            Canvas canvas = (Canvas) findViewById(R.id.svSecondary);
            canvas.removeSrc(_msLocal, 0);

            _msLocal.close();

            _msLocal = null;
        }

        if (null != _media) {
            if (_media.isOpen) {
                _media.close();
            }

            unsetMediaCallback(_media);

            _media = null;
        }

        Navigator.terminate();

        if (null != _peer) {
            unsetPeerCallback(_peer);

            if (false == _peer.isDisconnected) {
                _peer.disconnect();
            }

            if (false == _peer.isDestroyed) {
                _peer.destroy();
            }

            _peer = null;
        }
    }


    @Override
    protected void onStart() {
        super.onStart();

        // Disable Sleep and Screen Lock
        Window wnd = getWindow();
        wnd.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        wnd.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Set volume control stream type to WebRTC audio.
        setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);
    }

    @Override
    protected void onPause() {
        // Set default volume control stream type.
        setVolumeControlStream(AudioManager.USE_DEFAULT_STREAM_TYPE);

        super.onPause();
    }

    @Override
    protected void onStop() {
        // Enable Sleep and Screen Lock
        Window wnd = getWindow();
        wnd.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        wnd.clearFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);

        super.onStop();
    }

    @Override
    protected void onDestroy() {
        destroyPeer();

        _listPeerIds = null;
        _handler = null;

        super.onDestroy();
    }

}
