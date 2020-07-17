package im.youme.video.sample2;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.IdRes;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.WindowManager;

import android.widget.Button;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.youme.voiceengine.CameraMgr;
import com.youme.voiceengine.MemberChange;
import com.youme.voiceengine.NativeEngine;
import com.youme.voiceengine.ScreenRecorder;
import com.youme.voiceengine.VideoRenderer;
import com.youme.voiceengine.YouMeCallBackInterface;
import com.youme.voiceengine.YouMeConst;
import com.youme.voiceengine.YouMeVideoPreDecodeCallbackInterface;
import com.youme.voiceengine.api;
import com.youme.voiceengine.video.EglBase;
import com.youme.voiceengine.video.RendererCommon;
import com.youme.voiceengine.video.SurfaceViewRenderer;
import com.youme.voiceengine.mgr.YouMeManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import im.youme.video.videoRender.PercentFrameLayout;

import com.tencent.bugly.crashreport.CrashReport;


public class VideoCapturerActivity extends Activity implements YouMeCallBackInterface, View.OnClickListener, YouMeVideoPreDecodeCallbackInterface {

    public class RenderInfo {
        public String userId;
        public int rendViewIndex;
        public boolean RenderStatus;
        public int userIndex;
    }

    private static String TAG = "VideoCapturerActivity";

    private static Boolean DEBUG = false;

    ///声明video设置块相关静态变量
    public static String _serverIp = "0.0.0.0";
    public static int _serverPort = 0;
    public static int _videoWidth = 360;
    public static int _videoHeight = 480;
    public static int _maxBitRate = 0;
    public static int _minBitRate = 0;
    public static int _reportInterval = 3000;
    public static int _farendLevel = 10;
    public static boolean _bHighAudio = false;
    public static boolean _bHWEnable = true;
    public static boolean _bBeautify = false;
    public static boolean _bTcp = false;
    public static boolean _bLandscape = false;
    public static boolean _bVBR = true;
    public static boolean _bTestmode = false;
    public static int _fps = 15;
    public boolean inited = false;
    private ImageButton micBtn;
    private ImageButton speakerBtn;

    private SurfaceViewRenderer[] arrRenderViews = null;
    private static boolean lastTestmode = false;

    private MyHandler youmeVideoEventHandler;

    private TextView avTips = null;
    private Map<String, RenderInfo> renderInfoMap = null;
    private int[] m_UserViewIndexEn = {0, 0, 0, 0};

    String local_user_id = null;
    int local_render_id = -1;
    int mUserCount = 0;

    private boolean isJoinedRoom = false;
    private float beautifyLevel = 0.5f;

    private String currentRoomID = "";
    private int mFullScreenIndex = -1;
    private boolean isOpenCamera = false;
    private boolean needResumeCamera = false;
    private boolean micLock = false;
    private boolean isOpenShare = false;

    /**
     * 接受Video视图选择的服务器
     */
    static int RTC_XX_SERVER = 0;
    /**
     * 该状态是用来判断当前活跃的
     */
    private boolean activity;

    private int[] mAVStatistic = new int[10];

    private float mScaleFactor = 0.0f;
    private float mLastZoomFactor = 1.0f;
    private ScaleGestureDetector mScaleGestureDetector;
    private GestureDetector mGestureDetector;
    private View mFocusView;


    private static FileOutputStream mPreDecodeFos = null;
    private static int mPreDecodeCounter = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        YouMeManager.Init(this);
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_video_capturer);
        initCtrls();

        //取得启动该Activity的Intent对象
        Intent intent1 = getIntent();
        local_user_id = intent1.getStringExtra("userid");
        currentRoomID = intent1.getStringExtra("roomid");

        int area = intent1.getIntExtra("Area", 0);

        if (RTC_XX_SERVER != area || _bTestmode != lastTestmode) {
            inited = false;
            api.unInit();
        }
        RTC_XX_SERVER = area;

        renderInfoMap = new HashMap<>();
        ///初始化界面相关数据
        arrRenderViews = new SurfaceViewRenderer[4];
        getWindow().setFormat(PixelFormat.TRANSLUCENT);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);   //应用运行时，保持屏幕高亮，不锁屏

        youmeVideoEventHandler = new MyHandler(this);
        avTips = (TextView) findViewById(R.id.avtip);

        mFocusView = findViewById(R.id.camera_focus);
        mGestureDetector = new GestureDetector(this, simpleOnGestureListener);
        mScaleGestureDetector = new ScaleGestureDetector(this, scaleGestureListener);

        CameraMgr.setCameraAutoFocusCallBack(cameraFocusCallback);
        youmeVideoEventHandler.postDelayed(logAVStatisticsRunnable, _reportInterval);

        initSDK();

    }

    @Override
    public void onClick(View v) {

    }


    private void initSDK() {
        ///初始化SDK相关设置
        if (_bTestmode) {
            NativeEngine.setServerMode(1);
        } else {
            NativeEngine.setServerMode(0);
        }

        if (_serverIp.compareTo("0.0.0.0") != 0 && _serverPort > 0) {
            Log.d(TAG, "mcu serverip: " + _serverIp + ", port: " + _serverPort);
            NativeEngine.setServerMode(NativeEngine.SERVER_MODE_FIXED_IP_MCU);
            NativeEngine.setServerIpPort(_serverIp, _serverPort);
        }


        lastTestmode = _bTestmode;
        CrashReport.setAppVersion(this, "in." + api.getSdkInfo());
        api.setLogLevel(YouMeConst.YOUME_LOG_LEVEL.LOG_INFO, YouMeConst.YOUME_LOG_LEVEL.LOG_INFO);
        api.SetCallback(this);
        ScreenRecorder.init(this);
        //调用初始化
        int code = api.init(CommonDefines.appKey, CommonDefines.appSecret, RTC_XX_SERVER, "");

        VideoRendererSample.getInstance().setLocalUserId(local_user_id);
        api.setVideoFrameCallback(VideoRendererSample.getInstance());

        if (code == YouMeConst.YouMeErrorCode.YOUME_ERROR_WRONG_STATE) {
            //已经初始化过了，就不等初始化回调了，直接进频道就行
            autoJoinClick();
            inited = true;
        }

        Log.i("区域", "" + RTC_XX_SERVER);
    }


    private void initRender(int index, @IdRes int layoutId, @IdRes int viewId) {
        if (index < 0 || index > arrRenderViews.length || arrRenderViews[index] != null) {
            return;
        }
        SurfaceViewRenderer renderView = (SurfaceViewRenderer) this.findViewById(viewId);
        if (index != 3) {
            PercentFrameLayout layout = (PercentFrameLayout) this.findViewById(layoutId);
            layout.setPosition(0, 0, 100, 100);
        } else {
            renderView.setVisibility(View.INVISIBLE);
        }
        renderView.init(EglBase.createContext(api.sharedEGLContext()), null);
        renderView.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);
        renderView.setMirror(false);
        arrRenderViews[index] = renderView;
    }

    private void releaseRender() {
        for (int i = 0; i < arrRenderViews.length; i++) {
            if (arrRenderViews[i] != null) {
                arrRenderViews[i].release();
            }
        }
    }



    @Override
    protected void onDestroy() {
        super.onDestroy();
        releaseRender();
        VideoRendererSample.getInstance().deleteAllRender();
    }

    private void startMic() {
        api.setMicrophoneMute(false);
    }

    private void stopMic() {
        api.setMicrophoneMute(true);
    }

    void setZoomFactor(float factor) {

        mLastZoomFactor = factor;
    }

    private void startCamera() {
        api.startCapturer();
        isOpenCamera = true;
    }

    private void stopCamera() {
        api.stopCapturer();
        isOpenCamera = false;
    }

    private void switchCamera() {
        api.switchCamera();
    }

    private void switchRotation() {
        String UserId = IndexToUserId(mFullScreenIndex);
        if (UserId != null) {
            VideoRendererSample.getInstance().switchRotation(UserId);
        }
    }

    private void resetAllRender() {
        for (RenderInfo info : renderInfoMap.values()) {
            VideoRenderer.getInstance().deleteRender(info.userId);

            SurfaceViewRenderer renderView = getRenderViewByIndex(info.rendViewIndex);

            if (renderView != null) {
                renderView.clearImage();
            }
        }

        renderInfoMap.clear();
        for (int i = 0; i < m_UserViewIndexEn.length; i++) {
            m_UserViewIndexEn[i] = 0;
        }

        //清理自己/合流的视频显示
        {
            if (local_render_id != -1) {
                VideoRenderer.getInstance().deleteRender(local_user_id);
            }

            SurfaceViewRenderer renderView = getRenderViewByIndex(3);
            if (renderView != null) {
                renderView.clearImage();
            }
        }

    }

    private void resetStatus() {
//    api.setCaptureFrontCameraEnable(true);
        stopCamera();
        startCamera();
    }

    protected void onPause() {
        // 放到后台时完全暂停: api.pauseChannel();
        if (!activity) {
            api.setVideoFrameCallback(null);
            needResumeCamera = isOpenCamera;
            stopCamera();
            activity = true;
        }
//    Object[] obj = renderInfoMap.values().toArray(new Object[renderInfoMap.values().size()]);
//    for (int i= obj.length - 1 ;i > -1; i--) {
//        shutDOWN(((RenderInfo)obj[i]).userId);
//    }
        super.onPause();
    }

    protected void onResume() {
        //设置横屏
        if (_bLandscape && getRequestedOrientation() != ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
            Log.d(TAG, "rotation:" + this.getWindowManager().getDefaultDisplay().getRotation());
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            Log.d(TAG, "rotation end:" + this.getWindowManager().getDefaultDisplay().getRotation());
            api.setScreenRotation(this.getWindowManager().getDefaultDisplay().getRotation());
        }

        super.onResume();

        //切回前台的时候恢复
        //   api.resumeChannel();
        if (activity) {
            if (needResumeCamera) startCamera();
            needResumeCamera = false;
            api.setVideoFrameCallback(VideoRendererSample.getInstance());
            activity = false;
        }

//    Object[] obj = renderInfoMap.values().toArray(new Object[renderInfoMap.values().size()]);
//    for (int i= obj.length - 1 ;i > -1; i--) {
//        shutDOWN(((RenderInfo)obj[i]).userId);
//    }
    }

    private SurfaceViewRenderer getRenderViewByIndex(int index) {
        if (index < 0 || index > arrRenderViews.length) {
            return null;
        } else {
            return arrRenderViews[index];
        }
    }


    private void updateNewView(final String newUserId, final int index) {
        final SurfaceViewRenderer renderView = getRenderViewByIndex(index);
        if (renderView == null) {
            return;
        }

        renderView.setVisibility(View.VISIBLE);
        if (newUserId != local_user_id) {
            renderView.setZOrderOnTop(true);
        }
        //renderView.clearImage();

        VideoRendererSample.getInstance().addRender(newUserId, renderView);
        RenderInfo info = new RenderInfo();
        info.userId = newUserId;
        info.rendViewIndex = index;
        info.RenderStatus = true;
        info.userIndex = ++mUserCount;
        renderInfoMap.put(newUserId, info);
        m_UserViewIndexEn[index] = 1;

    }


    /// 回调数据
    @Override
    public void onEvent(int event, int error, String room, Object param) {
        ///里面会更新界面，所以要在主线程处理
        Log.i(TAG, "event:" + CommonDefines.CallEventToString(event) + ", error:" + error + ", room:" + room + ",param:" + param);
        Message msg = new Message();
        Bundle extraData = new Bundle();
        extraData.putString("channelId", room);
        msg.what = event;
        msg.arg1 = error;
        msg.obj = param;
        msg.setData(extraData);

        youmeVideoEventHandler.sendMessage(msg);
    }

    @Override
    public void onRequestRestAPI(int requestID, int iErrorCode, String strQuery, String strResult) {

    }

    @Override
    public void onMemberChange(String channelID, final MemberChange[] arrChanges, boolean isUpdate) {
        /**
         * 离开频道时移除该对象的聊天画面
         */
        Log.i(TAG, "onMemberChange:" + channelID + ",isUpdate:" + isUpdate);
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                //此时已在主线程中，可以更新UI了
                for (int i = 0; i < arrChanges.length; i++) {
                    if (arrChanges[i].isJoin == false) {
                        String leaveUserId = String.valueOf(arrChanges[i].userID);
                        shutDOWN(leaveUserId);
                    }
                }
            }
        });
    }

    @Override
    public void onBroadcast(int bc, String room, String param1, String param2, String content) {

    }

    @Override
    public void onAVStatistic(int avType, String userID, int value) {
        if (userID.compareTo(local_user_id) == 0 && avType < 10)
            mAVStatistic[avType] = value;
        if (youmeVideoEventHandler != null) {
            youmeVideoEventHandler.removeCallbacks(logAVStatisticsRunnable);
            youmeVideoEventHandler.postDelayed(logAVStatisticsRunnable, 200);
        }
    }

    @Override
    public void onTranslateTextComplete(int errorcode, int requestID, String text, int srcLangCode, int destLangCode)
    {

    }

    @Override
    public void onVideoPreDecode(String userId, byte[] data, int dataSizeInByte,long l) {
        // Log.i(TAG, "onVideoPreDecode:" + userId);
        if (DEBUG) {
            if (mPreDecodeFos != null) {
                try {
                    mPreDecodeFos.write(data);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private final Runnable logAVStatisticsRunnable = new Runnable() {
        @Override
        public void run() {

            final String tips = "VideoFps: " + (mAVStatistic[3]) + "\n" +
                    //"AudioRate: " + mAVStatistic[1] * 8 / 1000 + "kbps\n" +
                    //"VideoRate: " + mAVStatistic[2] * 8 / 1000 + "kbps\n" +
                    //"AudioLossRate: " + mAVStatistic[4] + "‰\n" +
                    "VideoLossRate: " + mAVStatistic[5] + "‰ \n" +
                    "local_uid:" + local_user_id + "\n";

            avTips.setText(tips);
            for (int i = 0; i < 10; i++) {
                mAVStatistic[i] = 0;
            }
        }
    };

    public void joinOK(String roomid) {
        currentRoomID = roomid;
        if (!isJoinedRoom) {
            Log.d(TAG, "进频道成功");
            //api.maskVideoByUserId(userid,block);
            //进频道成功后可以设置视频回调
            api.SetVideoCallback();
            //设置远端语音音量回调
            api.setFarendVoiceLevelCallback(10);
            //开启扬声器
            api.setSpeakerMute(false);

            isJoinedRoom = true;
            //远端视频渲染的view
            initRender(0, R.id.PercentFrameLayout0, R.id.SurfaceViewRenderer0);
            initRender(1, R.id.PercentFrameLayout1, R.id.SurfaceViewRenderer1);
            initRender(2, R.id.PercentFrameLayout2, R.id.SurfaceViewRenderer2);
            //本地视频
            initRender(3, R.id.remote_video_view_twelve1, R.id.remote_video_view_twelve1);
            VideoRendererSample.getInstance().setLocalUserId(local_user_id);
            updateNewView(local_user_id, 3);
            autoOpenStartCamera();
            mFullScreenIndex = 3;
        }
    }

    public void nextViewShow() {

        int validIndex = -1;
        int minIndex = 999999;
        String userId = "";

        for (int i = 0; i < 3; i++) {
            if (m_UserViewIndexEn[i] == 0) {
                validIndex = i;
                break;
            }
        }
        if (validIndex == -1)
            return;

        for (RenderInfo vaule : renderInfoMap.values()) {
            if (!vaule.RenderStatus && vaule.userIndex <= minIndex) {
                minIndex = vaule.userIndex;
                userId = vaule.userId;
            }
        }

        if (minIndex != 999999) {
            renderInfoMap.remove(userId);
            updateNewView(userId, validIndex);
        }

    }

    public void shutDOWN(String userId) {

        RenderInfo info = renderInfoMap.get(userId);
        if (info == null)
            return;

        if (info.RenderStatus) {
            if (mFullScreenIndex == info.rendViewIndex) {
                switchFullScreen(3);
            }
            final SurfaceViewRenderer renderView = getRenderViewByIndex(info.rendViewIndex);
            renderView.clearImage();
            renderView.setVisibility(View.INVISIBLE);
            VideoRendererSample.getInstance().deleteRender(userId);
            api.deleteRenderByUserID(userId);//不移除可能收不到VIDEO_ON事件
            m_UserViewIndexEn[info.rendViewIndex] = 0;
            nextViewShow();
        }
        renderInfoMap.remove(userId);

    }


    public void videoON(String userId) {
        Log.d(TAG, "新加的user ID=" + userId);
        int validIndex = -1;
        if (renderInfoMap.containsKey(userId))
            return;
        if (userId.equals(local_user_id)) {
            updateNewView(local_user_id, 3);
            return;
        }
        for (int i = 0; i < 3; i++) {
            if (m_UserViewIndexEn[i] == 0) {
                validIndex = i;
                break;
            }
        }
        if (validIndex != -1) {
            updateNewView(userId, validIndex);
        } else {
            RenderInfo info = new RenderInfo();
            info.userId = userId;
            info.rendViewIndex = -1;
            info.RenderStatus = false;
            info.userIndex = ++mUserCount;
            renderInfoMap.put(userId, info);
        }

    }


    private static class MyHandler extends Handler {

        private final WeakReference<VideoCapturerActivity> mActivity;

        public MyHandler(VideoCapturerActivity activity) {
            mActivity = new WeakReference<VideoCapturerActivity>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            VideoCapturerActivity activity = mActivity.get();
            if (activity != null) {
                super.handleMessage(msg);
                switch (msg.what) {
                    case YouMeConst.YouMeEvent.YOUME_EVENT_INIT_OK:
                        Log.d(TAG, "初始化成功");
                        ToastMessage.showToast(mActivity.get(),"初始化成功",1000);
                        activity.autoJoinClick();
                        activity.inited = true;
                        break;
                    case YouMeConst.YouMeEvent.YOUME_EVENT_INIT_FAILED:
                        Log.d(TAG, "初始化失败");
                        ToastMessage.showToast(mActivity.get(),"初始化失败，重试",3000);
                        activity.inited = false;
                        mActivity.get().initSDK();
                        break;
                    case YouMeConst.YouMeEvent.YOUME_EVENT_JOIN_OK:
                        String roomId = msg.getData().getString("channelId");
                        activity.joinOK(roomId);
                        TimerTask task = new TimerTask() {
                            public void run() {
                                // 停止播放铃声
                                //BackgroundMusic.getInstance(null).stopBackgroundMusic();
                            }
                        };
                        Timer timer = new Timer();
                        timer.schedule(task, 1000);
                        break;
                    case YouMeConst.YouMeEvent.YOUME_EVENT_LEAVED_ALL:
                        //activity.leavedUI();
                        //activity.resetAllRender();
                        //api.removeMixAllOverlayVideo();
                        //VideoRendererSample.getInstance().deleteAllRender();
                        //activity.isJoinedRoom = false;
                        break;
                    case YouMeConst.YouMeEvent.YOUME_EVENT_OTHERS_VIDEO_INPUT_START:
                        Log.d(TAG, "YOUME_EVENT_OTHERS_VIDEO_INPUT_START");
                        break;
                    case YouMeConst.YouMeEvent.YOUME_EVENT_OTHERS_VIDEO_ON:
                        Log.d(TAG, "YOUME_EVENT_OTHERS_VIDEO_ON");
                        String newUserId1 = String.valueOf(msg.obj);
                        activity.videoON(newUserId1);
                        break;
                    case YouMeConst.YouMeEvent.YOUME_EVENT_OTHERS_VIDEO_INPUT_STOP:
                        Log.d(TAG, "YOUME_EVENT_OTHERS_VIDEO_INPUT_STOP");
                        break;
                    case YouMeConst.YouMeEvent.YOUME_EVENT_OTHERS_VIDEO_SHUT_DOWN:
                        Log.d(TAG, "YOUME_EVENT_OTHERS_VIDEO_SHUT_DOWN");
                        //超过时间收不到新的画面，就先移除掉
                        String leaveUserId = String.valueOf(msg.obj);
                        activity.shutDOWN(leaveUserId);
                        break;
                    case YouMeConst.YouMeEvent.YOUME_EVENT_LOCAL_MIC_OFF:
                        if( msg.arg1 == 0) activity.micBtn.setSelected(true); // 自己关闭麦克风
                        break;
                    case YouMeConst.YouMeEvent.YOUME_EVENT_LOCAL_MIC_ON:
                        if( msg.arg1 == 0) activity.micBtn.setSelected(false); // 自己打开麦克风
                        break;
                    case YouMeConst.YouMeEvent.YOUME_EVENT_MIC_CTR_OFF:
                        activity.micLock = true;
                        activity.micBtn.setSelected(true); // 主持人关闭我的麦克风
                        break;
                    case YouMeConst.YouMeEvent.YOUME_EVENT_MIC_CTR_ON:
                        activity.micLock = false;
                        activity.micBtn.setSelected(false); // 主持人打开麦我的克风
                        break;
                    case YouMeConst.YouMeEvent.YOUME_EVENT_MASK_VIDEO_BY_OTHER_USER:
                        break;
                    case YouMeConst.YouMeEvent.YOUME_EVENT_RESUME_VIDEO_BY_OTHER_USER:
                        break;
                    case YouMeConst.YouMeEvent.YOUME_EVENT_MASK_VIDEO_FOR_USER:
                        break;
                    case YouMeConst.YouMeEvent.YOUME_EVENT_RESUME_VIDEO_FOR_USER:
                        break;
                    case YouMeConst.YouMeEvent.YOUME_EVENT_QUERY_USERS_VIDEO_INFO:
                        break;
                    case YouMeConst.YouMeEvent.YOUME_EVENT_SET_USERS_VIDEO_INFO:

                        break;
                    case 10000: //avStatistic回调
                        break;
                    case YouMeConst.YouMeEvent.YOUME_EVENT_FAREND_VOICE_LEVEL:

                        break;
                }
            }
        }
    }

    public void leaveChannel() {
        api.removeMixAllOverlayVideo();
        api.leaveChannelAll();

        if (DEBUG) {
            if (mPreDecodeFos != null) {
                try {
                    mPreDecodeFos.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        finish();
        Intent intent = new Intent();
        intent.setClass(VideoCapturerActivity.this, Video.class);
    }

    @Override
    public void onBackPressed() {
        //BackgroundMusic.getInstance(this).stopBackgroundMusic();
        leaveChannel();
    }


    /**
     * 自动进入频道方法   在初始化后面使用
     */
    private void autoJoinClick() {
        //每次进入房间都重新走一遍流程，防止上次退出房间超时，导致后面进入相同房间时接口未调用
        api.setTCPMode(_bTcp);
        //加入频道前进行video设置
        api.setVideoFps(_fps);
        //设置本地采集分辨率
        api.setVideoLocalResolution(_videoWidth, _videoHeight);
        //调用这个方法来设置视屏的分辨率
        api.setVideoNetResolution(_videoWidth, _videoHeight);

        int child_width = _videoWidth/2;
        int child_height = _videoHeight/2;

        if (child_width * child_height <= 240*320) {
            child_width = 240;
            child_height = 320;
        } else if (child_width * child_height >= 960 * 540){
            child_width = _videoWidth/4;
            child_height = _videoHeight/4;
        }

        //设置视频小流分辨率
        //api.setVideoNetResolutionForSecond(child_width, child_height);
        api.setVideoFpsForShare(15);
        api.setVideoNetResolutionForShare(720, 1280);

        //调用这个方法来设置时间间隔
        api.setAVStatisticInterval(_reportInterval);
        //设置视频编码比特率
        api.setVideoCodeBitrate(_maxBitRate, _minBitRate);
        //设置远端语音水平回调
        api.setFarendVoiceLevelCallback(_farendLevel);
        //设置视屏是软编还是硬编
        api.setVideoHardwareCodeEnable(_bHWEnable);
        //同步状态给其他人
        api.setAutoSendStatus(true);
        // 设置视频无帧渲染的等待超时时间，超过这个时间会给上层回调YOUME_EVENT_OTHERS_VIDEO_SHUT_DOWN, 单位ms
        api.setVideoNoFrameTimeout(6000);

        if (DEBUG) {
            api.setVideoPreDecodeCallbackEnable(this, true);
            if (mPreDecodeFos == null) {
                String path = String.format(getExternalFilesDir("").toString() + "/predecode_dump_%d.h264", mPreDecodeCounter++);
                File file = new File(path);
                try {
                    if (file.exists()) {
                        file.delete();
                    }
                    mPreDecodeFos = new FileOutputStream(file); //建立一个可以存取字节的文件
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        //api.setVideoFrameRawCbEnabled(true);
        if (_bHighAudio) {
            api.setAudioQuality(1);//高音质，48k，流量要求高
        }
        api.setVBR(_bVBR);

        /* 特殊音频设备适配
          AudioManager mAudioManager =(AudioManager)this.getSystemService(Context.AUDIO_SERVICE);
          mAudioManager.setParameters("mic_group= handhold-amic");
        */
        //api.setReleaseMicWhenMute(true);
        //api.joinChannelSingleMode(local_user_id, currentRoomID, YouMeConst.YouMeUserRole.YOUME_USER_HOST, true);
        api.joinChannelSingleMode( local_user_id,currentRoomID , YouMeConst.YouMeUserRole.YOUME_USER_HOST, true);
    }

    /**
     * 自动打开摄像头
     */
    private void autoOpenStartCamera() {

        //开启摄像头
        startCamera();

        //设置视频无渲染帧超时等待时间，单位毫秒
        api.setVideoNoFrameTimeout(5000);


    }

    private void initCtrls() {
        ButtonClickListener clickListener = new ButtonClickListener();
        ImageButton cameraBtn = (ImageButton) findViewById(R.id.vt_btn_camera);
        cameraBtn.setOnClickListener(clickListener);
        micBtn = (ImageButton) findViewById(R.id.vt_btn_mic);
        micBtn.setSelected(true);
        micBtn.setOnClickListener(clickListener);
        speakerBtn = (ImageButton) findViewById(R.id.vt_btn_speaker);
        speakerBtn.setOnClickListener(clickListener);
        ImageButton closeBtn = (ImageButton) findViewById(R.id.vt_btn_close);
        closeBtn.setOnClickListener(clickListener);
        ImageButton switchCameraBtn = (ImageButton) findViewById(R.id.vt_btn_switch_camera);
        switchCameraBtn.setOnClickListener(clickListener);
        ImageButton swtichRotation = (ImageButton) findViewById(R.id.vt_btn_Render_Rotation);
        swtichRotation.setOnClickListener(clickListener);
        Button shareBtn = (Button)findViewById(R.id.vt_btn_share);
        shareBtn.setOnClickListener(clickListener);
        shareBtn.setText("开始录屏");
        clickListener.onClick(this.findViewById(R.id.vt_btn_mic));
    }

    //设置按钮监听
    private class ButtonClickListener implements View.OnClickListener {
        /**
         * Called when a view has been clicked.
         *
         * @param v The view that was clicked.
         */
        @Override
        public void onClick(View v) {
            Log.i("按钮", "" + v.getId());
            switch (v.getId()) {
                case R.id.vt_btn_camera: {
                    v.setSelected(!v.isSelected());
                    boolean disableCamera = v.isSelected();
                    if (!disableCamera) {
                        startCamera();
                    } else {
                        stopCamera();
                    }

                }
                break;

                case R.id.vt_btn_mic: {
                    if(!micLock) {
//                    v.setSelected(!v.isSelected());
                        boolean disableMic = !v.isSelected();
                        if (disableMic) {
                            //关闭麦克风
                            stopMic();
                        } else {
                            //打开麦克风
                            startMic();
                        }
                    }
                }
                break;

                case R.id.vt_btn_speaker: {
                    v.setSelected(!v.isSelected());
                    boolean disableSpeaker = v.isSelected();
                    if (disableSpeaker) {
                        //关闭声音
                        api.setSpeakerMute(true);
                    } else {
                        //打开声音
                        api.setSpeakerMute(false);
                    }


                }
                break;

                case R.id.vt_btn_close: {
                    leaveChannel();

                }
                break;

                case R.id.vt_btn_switch_camera: {
                    switchCamera();

                }
                break;

                case R.id.vt_btn_Render_Rotation: {
                    switchRotation();

                }
                case R.id.vt_btn_share: {
                    if(!isOpenShare) {
                        ScreenRecorder.startScreenRecorder();
                        ((Button)v).setText("停止录屏");
                    }else {
                        ScreenRecorder.stopScreenRecorder();
                        ((Button)v).setText("开始录屏");
                    }
                    isOpenShare = !isOpenShare;
                }
                break;
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode == ScreenRecorder.SCREEN_RECODER_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                ScreenRecorder.onActivityResult(requestCode, resultCode, data);
            }else {
                isOpenShare = false;
            }
        }
    }

    private String IndexToUserId(int index) {
        for (RenderInfo info : renderInfoMap.values()) {
            if (info.rendViewIndex == index) {
                return info.userId;
            }
        }
        return null;
    }

    private void switchFullScreen(int index) {
        int tempIndex = mFullScreenIndex;
        if (mFullScreenIndex != -1) {
            String fullUserId = IndexToUserId(mFullScreenIndex);
            if (fullUserId != null) {
                VideoRendererSample.getInstance().deleteRender(fullUserId);
                VideoRendererSample.getInstance().deleteRender(local_user_id);
                VideoRendererSample.getInstance().addRender(fullUserId, getRenderViewByIndex(mFullScreenIndex));
                VideoRendererSample.getInstance().addRender(local_user_id, getRenderViewByIndex(3));
            }
            mFullScreenIndex = -1;
        }

        if (index != 3 && index != tempIndex) {
            String userId = IndexToUserId(index);
            if (userId != null) {
                VideoRendererSample.getInstance().deleteRender(local_user_id);
                VideoRendererSample.getInstance().deleteRender(userId);
                VideoRendererSample.getInstance().addRender(userId, getRenderViewByIndex(3));
                VideoRendererSample.getInstance().addRender(local_user_id, getRenderViewByIndex(index));
                mFullScreenIndex = index;
            }
        }

    }

    public void onVideoViewClick(View v) {

        switch (v.getId()) {
            case R.id.SurfaceViewRenderer0:
                switchFullScreen(0);
                break;
            case R.id.SurfaceViewRenderer1:
                switchFullScreen(1);
                break;
            case R.id.SurfaceViewRenderer2:
                switchFullScreen(2);
                break;
            case R.id.remote_video_view_twelve1:
                switchFullScreen(3);
                break;
        }
    }


    //重写onTouchEvent方法 获取手势
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        //识别手势
        mGestureDetector.onTouchEvent(event);
        mScaleGestureDetector.onTouchEvent(event);
        return super.onTouchEvent(event);
    }

    //操作类
    public ScaleGestureDetector.OnScaleGestureListener scaleGestureListener = new ScaleGestureDetector.OnScaleGestureListener() {

        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            if (detector.getCurrentSpan() > mScaleFactor) {
                mLastZoomFactor += 0.3f;
            } else {
                mLastZoomFactor -= 0.3f;
            }
            if (api.isCameraZoomSupported() && mLastZoomFactor >= 1.0f) {
                //Log.i(TAG, "zoom scale:"+mLastZoomFactor);
                api.setCameraZoomFactor(mLastZoomFactor);
            }
            mScaleFactor = detector.getCurrentSpan();
            return false;
        }

        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector) {
            mScaleFactor = detector.getCurrentSpan();
            return true;
        }

        @Override
        public void onScaleEnd(ScaleGestureDetector detector) {
            mScaleFactor = detector.getCurrentSpan();
        }
    };


    private GestureDetector.SimpleOnGestureListener simpleOnGestureListener = new GestureDetector.SimpleOnGestureListener() {
        @Override
        public boolean onDown(MotionEvent e) {
            Log.d(TAG, "onDown");

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH &&
                    api.isCameraFocusPositionInPreviewSupported()) {
                mFocusView.removeCallbacks(timeoutRunnable);
                mFocusView.postDelayed(timeoutRunnable, 1500);
                mFocusView.setVisibility(View.VISIBLE);

                RelativeLayout.LayoutParams focusParams = (RelativeLayout.LayoutParams) mFocusView.getLayoutParams();
                focusParams.leftMargin = (int) e.getX() - focusParams.width / 2;
                focusParams.topMargin = (int) e.getY() - focusParams.height / 2;
                mFocusView.setLayoutParams(focusParams);

                ObjectAnimator scaleX = ObjectAnimator.ofFloat(mFocusView, "scaleX", 1, 0.5f);
                scaleX.setDuration(300);
                ObjectAnimator scaleY = ObjectAnimator.ofFloat(mFocusView, "scaleY", 1, 0.5f);
                scaleY.setDuration(300);
                ObjectAnimator alpha = ObjectAnimator.ofFloat(mFocusView, "alpha", 1f, 0.3f, 1f, 0.3f, 1f, 0.3f, 1f);
                alpha.setDuration(600);
                AnimatorSet animatorSet = new AnimatorSet();
                animatorSet.play(scaleX).with(scaleY).before(alpha);
                animatorSet.start();
                mFocusView.setTag(cameraFocusCallback);


                WindowManager wm1 = getWindowManager();
                int width = wm1.getDefaultDisplay().getWidth();
                int height = wm1.getDefaultDisplay().getHeight();
                float x = e.getX() / width;
                float y = 1 - e.getY() / height;
                api.setCameraFocusPositionInPreview(x, y);
                //Log.i(TAG, "focus x:"+ x + " y:"+y);
            }

            return true;
        }

        /**
         * 前置摄像头可能不会回调对焦成功，因此需要手动隐藏对焦框
         */
        private Runnable timeoutRunnable = new Runnable() {
            @Override
            public void run() {
                if (mFocusView.getVisibility() == View.VISIBLE) {
                    mFocusView.setVisibility(View.INVISIBLE);
                }
            }
        };


    };

    Camera.AutoFocusCallback cameraFocusCallback = new Camera.AutoFocusCallback() {
        @Override
        public void onAutoFocus(final boolean success, Camera camera) {
            Log.d(TAG, "auto focus result: " + success);
            if (mFocusView.getTag() == this && mFocusView.getVisibility() == View.VISIBLE) {
                mFocusView.setVisibility(View.INVISIBLE);
            }
        }
    };
}

