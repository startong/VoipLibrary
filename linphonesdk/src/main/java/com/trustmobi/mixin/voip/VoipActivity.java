package com.trustmobi.mixin.voip;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.dds.tbs.linphonesdk.R;
import com.trustmobi.mixin.voip.bean.ChatInfo;
import com.trustmobi.mixin.voip.callback.VoipCallBack;
import com.trustmobi.mixin.voip.frgment.CallAudioFragment;
import com.trustmobi.mixin.voip.frgment.CallVideoFragment;

import org.linphone.core.CallDirection;
import org.linphone.core.LinphoneAddress;
import org.linphone.core.LinphoneCall;
import org.linphone.core.LinphoneCallParams;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneCoreListenerBase;
import org.linphone.core.Reason;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.trustmobi.mixin.voip.VoipService.NOTIFY_INCOMING;
import static com.trustmobi.mixin.voip.VoipService.NOTIFY_OUTGOING;


/**
 * Created by dds on 2018/5/3.
 * android_shuai@163.com
 */

public class VoipActivity extends Activity implements ComButton.onComClick, View.OnClickListener {
    private RelativeLayout voip_rl_audio;
    private RelativeLayout fragmentContainer;

    private LinearLayout voip_chat_incoming;
    private LinearLayout voip_voice_chatting;

    private ComButton voip_chat_mute;
    private ComButton voip_chat_cancel;
    private ComButton voip_chat_hands_free;

    private ComButton voip_accept;
    private ComButton voip_hang_up;

    private Button narrow_button;
    private TextView voip_voice_chat_state_tips;

    private ImageView iv_background;
    private ImageView voip_voice_chat_avatar;
    private TextView voice_chat_friend_name;

    private LinphoneCall mCall;
    private LinphoneCoreListenerBase mListener;

    private HomeWatcherReceiver homeWatcherReceiver;


    private int chatType;
    public static final String CHAT_TYPE = "chatType";
    private boolean isVideo;
    public static final String IS_VIDEO = "isVideo";

    // 0 播出电话 1 接听电话  2 通话中
    public static void openActivity(Context context, int chatType) {
        openActivity(context, chatType, false);
    }

    public static void openActivity(Context context, int chatType, boolean isVideo) {
        if (VoipService.isReady()) {
            Intent intent = new Intent(context, VoipActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            intent.putExtra(CHAT_TYPE, chatType);
            intent.putExtra(IS_VIDEO, isVideo);
            context.startActivity(intent);
        }
    }

    public static final int CALL = 0x001;
    private VoipHandler voipHandler = new VoipHandler();

    private static class VoipHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case CALL:
                    VoipHelper.isInCall = false;
                    LinphoneManager.getInstance().newOutgoingCall(VoipHelper.friendName, VoipHelper.isVideoEnale, VoipHelper.randomKey);
                    break;
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.voip_activity_chat);
        initView();
        initVar();
        initListener();
        initReceiver();
        initFragment();
    }

    private void initFragment() {
        if (fragmentContainer != null) {
            Fragment callFragment;
            if (isVideoEnabled(LinphoneManager.getLc().getCurrentCall()) || isVideo) {
                hideAudio();
                callFragment = new CallVideoFragment();
                videoCallFragment = (CallVideoFragment) callFragment;
                //displayVideoCall(false);
                LinphoneManager.getInstance().routeAudioToSpeaker();
                isSpeakerEnabled = true;
            } else {
                callFragment = new CallAudioFragment();
                audioCallFragment = (CallAudioFragment) callFragment;
            }

//            if (BluetoothManager.getInstance().isBluetoothHeadsetAvailable()) {
//                BluetoothManager.getInstance().routeAudioToBluetooth();
//            }

            // callFragment.setArguments(getIntent().getExtras());
            getFragmentManager().beginTransaction().add(R.id.fragmentContainer, callFragment).commitAllowingStateLoss();
        }
    }

    private void initReceiver() {
        homeWatcherReceiver = new HomeWatcherReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        registerReceiver(homeWatcherReceiver, filter);

    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (Build.VERSION.SDK_INT >= 23) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.RECORD_AUDIO}, 1000);
            }
        }
        LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
        if (lc != null) {
            lc.addListener(mListener);
            isSpeakerEnabled = lc.isSpeakerEnabled();
            voip_chat_hands_free.setImageResource(isSpeakerEnabled ? R.drawable.voip_hands_free : R.drawable.voip_btn_voice_hand_free);
            isMicMuted = lc.isMicMuted();
            voip_chat_mute.setImageResource(isMicMuted ? R.drawable.voip_mute : R.drawable.voip_btn_voice_mute);
            lookupCalling();
        }
        VoipService.instance().removeNarrow();


    }

    @Override
    protected void onPause() {
        LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
        if (lc != null) {
            lc.removeListener(mListener);
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (homeWatcherReceiver != null) {
            unregisterReceiver(homeWatcherReceiver);
        }
        if (voipHandler != null) {
            voipHandler.removeMessages(CALL);
        }
        VoipHelper.isInCall = false;
        super.onDestroy();
    }

    private void initView() {
        voip_rl_audio = (RelativeLayout) findViewById(R.id.voip_rl_audio);
        voip_chat_incoming = (LinearLayout) findViewById(R.id.voip_chat_incoming);
        voip_voice_chatting = (LinearLayout) findViewById(R.id.voip_voice_chatting);
        voip_chat_mute = (ComButton) findViewById(R.id.voip_chat_mute);
        voip_chat_cancel = (ComButton) findViewById(R.id.voip_chat_cancel);
        voip_chat_hands_free = (ComButton) findViewById(R.id.voip_chat_hands_free);
        voip_accept = (ComButton) findViewById(R.id.voip_accept);
        voip_hang_up = (ComButton) findViewById(R.id.voip_hang_up);
        voip_voice_chat_state_tips = (TextView) findViewById(R.id.voip_voice_chat_state_tips);
        voip_voice_chat_avatar = (ImageView) findViewById(R.id.voip_voice_chat_avatar);
        voice_chat_friend_name = (TextView) findViewById(R.id.voice_chat_friend_name);
        narrow_button = (Button) findViewById(R.id.narrow_button);
        iv_background = (ImageView) findViewById(R.id.iv_background);
        fragmentContainer = (RelativeLayout) findViewById(R.id.fragmentContainer);


    }

    private ChatInfo info;


    private void initVar() {
        Intent intent = getIntent();
        chatType = intent.getIntExtra(CHAT_TYPE, 0);
        isVideo = intent.getBooleanExtra(IS_VIDEO, false);
        if (chatType == NOTIFY_OUTGOING) {
            //播出电话
            voip_chat_incoming.setVisibility(View.INVISIBLE);
            voip_voice_chatting.setVisibility(View.VISIBLE);
            voip_chat_mute.setEnable(false);
            lookupOutgoingCall();
            updateChatStateTips(getString(R.string.voice_chat_calling));
            narrow_button.setVisibility(View.GONE);
            voipHandler.sendEmptyMessageDelayed(1, 2000);


        } else if (chatType == NOTIFY_INCOMING) {
            //来电话界面
            voip_chat_incoming.setVisibility(View.VISIBLE);
            voip_voice_chatting.setVisibility(View.INVISIBLE);
            lookupIncomingCall();
            updateChatStateTips(getString(R.string.voice_chat_invite));

        } else {
            //正在通话中
            voip_chat_incoming.setVisibility(View.INVISIBLE);
            voip_voice_chatting.setVisibility(View.VISIBLE);
            voip_chat_mute.setEnable(true);
            voip_voice_chat_state_tips.setVisibility(View.INVISIBLE);
            lookupCalling();
            //显示时间
            if (mCall != null) {
                registerCallDurationTimer(null, mCall);
            }
        }

        //显示头像和昵称
        info = VoipHelper.getInstance().getChatInfo();
        if (!TextUtils.isEmpty(VoipHelper.friendName)) {
            VoipCallBack callBack = VoipService.instance.getCallBack();
            if (callBack != null) {
                info = callBack.getChatInfo(VoipHelper.friendName);
            }
        }
        if (info != null) {
            Glide.with(this).load(info.getRemoteAvatar()).transform(new RoundedCornersTransformation(this, 10)).placeholder(info.getDefaultAvatar()).error(info.getDefaultAvatar()).into(voip_voice_chat_avatar);
            Glide.with(this).load(info.getRemoteAvatar()).placeholder(info.getDefaultAvatar()).error(info.getDefaultAvatar()).into(iv_background);
            voice_chat_friend_name.setText(info.getRemoteNickName());
        }


    }

    private void lookupOutgoingCall() {
        if (LinphoneManager.getLcIfManagerNotDestroyedOrNull() != null) {
            List<LinphoneCall> calls = new ArrayList<>(Arrays.asList(LinphoneManager.getLc().getCalls()));
            LinLog.e(VoipHelper.TAG, "lookupOutgoingCall:" + calls.size());
            for (LinphoneCall call : calls) {
                LinphoneCall.State cstate = call.getState();
                if (LinphoneCall.State.OutgoingInit == cstate || LinphoneCall.State.OutgoingProgress == cstate
                        || LinphoneCall.State.OutgoingRinging == cstate || LinphoneCall.State.OutgoingEarlyMedia == cstate) {
                    mCall = call;
                    break;
                }
            }
        }
    }

    private void lookupIncomingCall() {
        if (LinphoneManager.getLcIfManagerNotDestroyedOrNull() != null) {
            List<LinphoneCall> calls = new ArrayList<>(Arrays.asList(LinphoneManager.getLc().getCalls()));
            for (LinphoneCall call : calls) {
                if (LinphoneCall.State.IncomingReceived == call.getState()) {
                    mCall = call;
                    break;
                }
            }
        }
    }

    private void lookupCalling() {
        if (LinphoneManager.getLcIfManagerNotDestroyedOrNull() != null) {
            List<LinphoneCall> calls = new ArrayList<>(Arrays.asList(LinphoneManager.getLc().getCalls()));
            for (LinphoneCall call : calls) {
                LinphoneCall.State state = call.getState();
                if (LinphoneCall.State.OutgoingInit == state ||
                        LinphoneCall.State.OutgoingProgress == state ||
                        LinphoneCall.State.OutgoingRinging == state ||
                        LinphoneCall.State.OutgoingEarlyMedia == state ||
                        LinphoneCall.State.StreamsRunning == state ||
                        LinphoneCall.State.Paused == state ||
                        LinphoneCall.State.PausedByRemote == state ||
                        LinphoneCall.State.Pausing == state ||
                        LinphoneCall.State.Connected == state) {
                    mCall = call;
                    break;
                }
            }
        }
    }

    private void initListener() {
        voip_chat_mute.setComClickListener(this);
        voip_chat_cancel.setComClickListener(this);
        voip_chat_hands_free.setComClickListener(this);
        voip_accept.setComClickListener(this);
        voip_hang_up.setComClickListener(this);
        narrow_button.setOnClickListener(this);
        mListener = new LinphoneCoreListenerBase() {
            @Override
            public void callState(LinphoneCore lc, LinphoneCall call, LinphoneCall.State state, String message) {
                if (mCall == null) {
                    mCall = call;
                }
                if (call != mCall) {
                    return;
                }
                //来电话
                if (call.getDirection() == CallDirection.Incoming) {
                    if (call == mCall && LinphoneCall.State.CallEnd == state) {
                        //对方挂断电话
                        finish();
                    }
                    //建立通话
                    if (state == LinphoneCall.State.StreamsRunning) {
                        if (isVideoEnabled(call)) {
                            switchVideo();
                        } else {
                            LinphoneManager.getLc().enableSpeaker(false);
                            registerCallDurationTimer(null, call);
                            voip_voice_chat_state_tips.setVisibility(View.INVISIBLE);
                            narrow_button.setVisibility(View.VISIBLE);
                        }

                    }

                    if (state == LinphoneCall.State.Error) {
                        terminateErrorCall(call, message);
                    }
                } else {
                    //去电话
                    if (call == mCall && LinphoneCall.State.OutgoingRinging == state) {
                        updateChatStateTips(getString(R.string.voice_chat_invite_call));
                    } else if (call == mCall && LinphoneCall.State.Connected == state) {
                        //开始接听
                        updateChatStateTips(getString(R.string.voice_chat_connect));
                        voip_chat_mute.setEnable(true);
                        return;
                    } else if (call == mCall && LinphoneCall.State.StreamsRunning == state) {
                        if (isVideoEnabled(call)) {

                        } else {
                            registerCallDurationTimer(null, call);
                            voip_voice_chat_state_tips.setVisibility(View.INVISIBLE);
                            narrow_button.setVisibility(View.VISIBLE);
                        }


                    } else if (state == LinphoneCall.State.CallEnd) {
                        if (call.getErrorInfo().getReason() == Reason.Declined) {
                            declineOutgoing();
                        }
                    } else if (state == LinphoneCall.State.Error) {
                        terminateErrorCall(call, message);
                        declineOutgoing();
                    }

                    if (LinphoneManager.getLc().getCallsNb() == 0) {
                        finish();
                    }
                }
            }
        };
    }

    private void terminateErrorCall(LinphoneCall call, String message) {
        LinphoneAddress remoteAddress = call.getRemoteAddress();
        String userId = remoteAddress.getUserName();
        if (message != null && call.getErrorInfo().getReason() == Reason.NotFound) {
            displayCustomToast(getString(R.string.voice_cha_not_logged_in), Toast.LENGTH_SHORT);
        } else if (message != null && call.getErrorInfo().getReason() == Reason.Busy) {
            displayCustomToast(getString(R.string.voice_chat_busy_toast), Toast.LENGTH_SHORT);
        } else if (message != null && call.getErrorInfo().getReason() == Reason.BadCredentials) {
            displayCustomToast(getString(R.string.voice_chat_setmastkey_err_toast), Toast.LENGTH_SHORT);
        } else if (message != null && call.getErrorInfo().getReason() == Reason.Declined) {
            displayCustomToast(getString(R.string.voice_chat_friend_refused_toast), Toast.LENGTH_SHORT);
        } else if (call.getErrorInfo().getReason() == Reason.Media) {
            displayCustomToast("不兼容的媒体参数", Toast.LENGTH_SHORT);
        } else if (call.getErrorInfo().getReason() == Reason.NotAnswered) {
            displayCustomToast(getString(R.string.voice_cha_not_logged_in), Toast.LENGTH_SHORT);
        } else if (message != null) {
            displayCustomToast(getString(R.string.voice_cha_not_logged_in), Toast.LENGTH_SHORT);
        }
    }

    private boolean isSpeakerEnabled = false, isMicMuted = false;

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.voip_accept) {
            //接听电话
            answer();

        } else if (id == R.id.voip_hang_up) {
            //挂断电话
            declineIncoming();

        } else if (id == R.id.voip_chat_mute) {
            toggleMicro();

        } else if (id == R.id.voip_chat_cancel) {
            if (mCall != null) {
                if (mCall.getState() == LinphoneCall.State.StreamsRunning ||
                        mCall.getState() == LinphoneCall.State.Paused ||
                        mCall.getState() == LinphoneCall.State.Pausing ||
                        mCall.getState() == LinphoneCall.State.PausedByRemote ||
                        mCall.getState() == LinphoneCall.State.Connected
                        ) {
                    hangUp();

                } else {
                    declineOutgoing();
                }
            } else {
                finish();
            }
        } else if (id == R.id.voip_chat_hands_free) {
            toggleSpeaker();

        } else if (id == R.id.narrow_button) {
            openNarrow();
        }

    }

    private boolean alreadyAcceptedOrDeniedCall;

    private void answer() {
        if (mCall != null) {
            if (alreadyAcceptedOrDeniedCall) {
                return;
            }
            alreadyAcceptedOrDeniedCall = true;
            LinphoneCallParams params = LinphoneManager.getLc().createCallParams(mCall);
            boolean isLowBandwidthConnection = !LinphoneUtils.isHighBandwidthConnection(VoipService.instance().getApplicationContext());
            if (params != null) {
                params.enableLowBandwidth(isLowBandwidthConnection);
            } else {
                org.linphone.mediastream.Log.e("Could not create call params for call");
            }

            if (params == null || !LinphoneManager.getInstance().acceptCallWithParams(mCall, params, null)) {
                Toast.makeText(this, getString(R.string.voice_chat_err), Toast.LENGTH_LONG).show();
            } else {
                if (isVideoEnabled(mCall)) {
                    voip_chat_incoming.setVisibility(View.INVISIBLE);
                    replaceFragmentAudioByVideo();
                } else {
                    //成功接听
                    voip_chat_incoming.setVisibility(View.INVISIBLE);
                    voip_voice_chatting.setVisibility(View.VISIBLE);
                    voip_chat_hands_free.setImageResource(R.drawable.voip_btn_voice_hand_free);
                    isSpeakerEnabled = false;
                }

            }
        }

    }

    private void declineIncoming() {
        if (mCall != null) {
            if (alreadyAcceptedOrDeniedCall) {
                return;
            }
            alreadyAcceptedOrDeniedCall = true;

            LinphoneManager.getLc().terminateCall(mCall);
            finish();
        } else {
            finish();
        }

    }

    private void declineOutgoing() {
        if (mCall == null) {
            hangUp();
            return;
        }
        LinphoneManager.getLc().terminateCall(mCall);
        finish();

    }

    private void hangUp() {
        LinphoneCore lc = LinphoneManager.getLc();
        LinphoneCall currentCall = lc.getCurrentCall();
        if (currentCall != null) {
            lc.terminateCall(currentCall);
        } else if (lc.isInConference()) {
            lc.terminateConference();
        } else {
            lc.terminateAllCalls();
        }
    }


    protected void toggleSpeaker() {
        isSpeakerEnabled = !isSpeakerEnabled;
        voip_chat_hands_free.setImageResource(isSpeakerEnabled ? R.drawable.voip_hands_free : R.drawable.voip_btn_voice_hand_free);
        if (isSpeakerEnabled) {
            // 打开扬声器
            LinphoneManager.getInstance().routeAudioToSpeaker();
        } else {
            //关闭扬声器
            LinphoneManager.getInstance().routeAudioToReceiver();
        }


    }

    private void toggleMicro() {
        LinphoneCore lc = LinphoneManager.getLc();
        isMicMuted = !isMicMuted;
        if (isMicMuted) {
            lc.muteMic(true);
        } else {
            lc.muteMic(false);
        }


        voip_chat_mute.setImageResource(isMicMuted ? R.drawable.voip_mute : R.drawable.voip_btn_voice_mute);

    }


    private void registerCallDurationTimer(View v, LinphoneCall call) {
        int callDuration = call.getDuration();
        if (callDuration == 0 && call.getState() != LinphoneCall.State.StreamsRunning) {
            return;
        }
        Chronometer timer = null;
        if (v == null) {
            timer = (Chronometer) findViewById(R.id.voip_voice_chat_time);
            timer.setVisibility(View.VISIBLE);
        }
        timer.setBase(SystemClock.elapsedRealtime() - 1000 * callDuration);
        timer.start();
    }

    private void updateChatStateTips(String tips) {
        voip_voice_chat_state_tips.setVisibility(View.VISIBLE);
        voip_voice_chat_state_tips.setText(tips);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        //屏蔽返回键
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }


    public void displayCustomToast(final String message, final int duration) {
        LayoutInflater inflater = getLayoutInflater();
        View layout = inflater.inflate(R.layout.voip_toast, (ViewGroup) findViewById(R.id.toastRoot));
        TextView toastText = (TextView) layout.findViewById(R.id.toastMessage);
        toastText.setText(message);
        final Toast toast = new Toast(getApplicationContext());
        toast.setGravity(Gravity.CENTER, 0, 0);
        toast.setDuration(duration);
        toast.setView(layout);
        toast.show();
    }


    public class HomeWatcherReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String intentAction = intent.getAction();
            if (TextUtils.equals(intentAction, Intent.ACTION_CLOSE_SYSTEM_DIALOGS)) {
                openNarrow();
            }
        }

    }


    //开启悬浮窗
    private void openNarrow() {
        SettingsCompat.setDrawOverlays(this.getApplicationContext(), true);
        VoipService.instance().createNarrowView();
        VoipActivity.this.finish();
    }


    private boolean isVideoEnabled(LinphoneCall call) {
        if (call != null) {
            return call.getCurrentParams().getVideoEnabled();
        }
        return false;
    }

    private CallAudioFragment audioCallFragment;
    private CallVideoFragment videoCallFragment;

    public void bindVideoFragment(CallVideoFragment fragment) {
        videoCallFragment = fragment;
    }

    public void bindAudioFragment(CallAudioFragment fragment) {
        audioCallFragment = fragment;
    }

    private void switchVideo() {
        final LinphoneCall call = LinphoneManager.getLc().getCurrentCall();
        if (call == null) {
            return;
        }
        //Check if the call is not terminated
        if (call.getState() == LinphoneCall.State.CallEnd || call.getState() == LinphoneCall.State.CallReleased)
            return;
        if (!call.getRemoteParams().isLowBandwidthEnabled()) {
            LinphoneManager.getInstance().addVideo();
            if (videoCallFragment == null || !videoCallFragment.isVisible())
                showVideoView();
        } else {
            displayCustomToast(getString(R.string.error_low_bandwidth), Toast.LENGTH_LONG);
        }

    }

    private void showAudioView() {
//        if (LinphoneManager.getLc().getCurrentCall() != null) {
//            if (!isSpeakerEnabled) {
//                LinphoneManager.getInstance().enableProximitySensing(true);
//            }
//        }
        replaceFragmentVideoByAudio();
        //隐藏视频界面
        //displayAudioCall();
        //showStatusBar();
        //removeCallbacks();


    }

    private void showVideoView() {
        voip_rl_audio.setVisibility(View.GONE);
        narrow_button.setVisibility(View.GONE);
        voip_voice_chatting.setVisibility(View.GONE);
        voip_chat_incoming.setVisibility(View.GONE);
        replaceFragmentAudioByVideo();
    }

    private void replaceFragmentAudioByVideo() {
        videoCallFragment = new CallVideoFragment();

        FragmentTransaction transaction = getFragmentManager().beginTransaction();
        transaction.replace(R.id.fragmentContainer, videoCallFragment);
        try {
            transaction.commitAllowingStateLoss();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void replaceFragmentVideoByAudio() {
        audioCallFragment = new CallAudioFragment();
        FragmentTransaction transaction = getFragmentManager().beginTransaction();
        transaction.replace(R.id.fragmentContainer, audioCallFragment);
        try {
            transaction.commitAllowingStateLoss();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private void hideAudio() {
        voip_rl_audio.setVisibility(View.GONE);
        narrow_button.setVisibility(View.GONE);
        voip_voice_chatting.setVisibility(View.GONE);
    }
}