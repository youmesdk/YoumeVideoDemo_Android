package im.youme.video.externalSample;


import java.io.IOException;
import java.util.List;

import java.util.ArrayList;
import android.annotation.SuppressLint;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.opengl.GLES11Ext;
import android.os.Build;
import android.util.Log;
import android.view.SurfaceView;
import android.content.Context;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.content.pm.PackageManager;
import android.app.Activity;
import android.Manifest;
import android.view.Surface;

import com.youme.mixers.GLESVideoMixer;
import com.youme.mixers.VideoMixerHelper;
import com.youme.voiceengine.YouMeConst;
import com.youme.voiceengine.api;
import com.youme.voiceengine.video.GlUtil;

import im.youme.video.utils.core.VideoProducer;


@SuppressLint("NewApi")
@SuppressWarnings("deprecation")
public class CameraMgrSample {
    static String tag =  CameraMgrSample.class.getSimpleName();

    private final static int DEFAULE_WIDTH = 640;
    private final static int DEFAULE_HEIGHT = 480;
    private final static int DEFAULE_FPS = 15;

    private SurfaceView svCamera = null;
    private int  mTextureID = 0;
    private SurfaceTexture mSurfaceTexture = null;
    private Camera camera = null;
    Camera.Parameters camPara = null;
    private String camWhiteBalance;
    private String camFocusMode;

    private byte mBuffer[];
    public static boolean isFrontCamera = false;
    public int orientation = 90;
    private static int screenOrientation = 0;
    private static CameraMgrSample instance = new CameraMgrSample();
    private static Context context = null;

    public boolean isDoubleStreamingModel;
    public CameraMgrSample (){}
//    public static CameraMgrSample getInstance() {
//        return instance;
//    }

    private int mFps = 15;
    private int videoWidth = 640;
    private int videoHeight = 480;
    public int preViewWidth = 640;
    public int preViewHeight = 480;
    private boolean needLoseFrame = false;
    private long  lastFrameTime = 0;
    private int  frameCount = 0;
    private long totalTime = 0;
    private int cameraId = 0;
    private boolean mAutoFocusLocked = false;
    private boolean mIsSupportAutoFocus = false;
    private boolean mIsSupportAutoFocusContinuousPicture = false;

    public CameraMgrSample(SurfaceView svCamera) {
        this.svCamera = svCamera;
        //this.svCamera.getHolder().setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        //this.svCamera.getHolder().addCallback(new YMSurfaceHolderCallback());
    }

    public static void init(Context ctx) {
        context = ctx;
        if( context instanceof Activity )
        {
            switch (((Activity)context).getWindowManager().getDefaultDisplay().getRotation())  {
                case Surface.ROTATION_0:
                    screenOrientation = 0;
                    break;
                case Surface.ROTATION_90:
                    screenOrientation = 90;
                    break;
                case Surface.ROTATION_180:
                    screenOrientation = 180;
                    break;
                case Surface.ROTATION_270:
                    screenOrientation = 270;
                    break;
            }
        }
        else
        {
            screenOrientation = 0;
        }
    }

    public void setPreViewFps(int fps)
    {
        mFps = fps;
    }

    public int openCamera( boolean isFront) {
        if(null != camera) {
            closeCamera();
        }

        cameraId = 0;
        int cameraNum = Camera.getNumberOfCameras();
        CameraInfo cameraInfo = new CameraInfo();
        for (int i = 0; i < cameraNum; i++) {
            Camera.getCameraInfo(i, cameraInfo);
            if((isFront) && (cameraInfo.facing == CameraInfo.CAMERA_FACING_FRONT)) {
                cameraId = i;
                orientation = (360 - cameraInfo.orientation + 360 - screenOrientation) % 360;
                orientation = (360 - orientation) % 360;  // compensate the mirror
                Log.d(tag, "i:" + i + "orientation:" + orientation + "screenOrientation:" + screenOrientation);
                break;
            } else if((!isFront) && (cameraInfo.facing == CameraInfo.CAMERA_FACING_BACK)) {
                cameraId = i;
                orientation = (cameraInfo.orientation + 360 - screenOrientation) % 360;
                Log.d(tag, "ii:" + i + "orientation:" + orientation + "screenOrientation:" + screenOrientation);
                break;
            }
        }

        try {
            camera = Camera.open(cameraId);
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            camera = null;
            return -1;
        }

//        dumpCameraInfo(camera, cameraId);

        try {
            camPara = camera.getParameters();
        } catch(Exception e) {
            e.printStackTrace();
            camera = null;
            return -2;
        }

       Camera.Size size = getCloselyPreSize(videoWidth, videoHeight, camPara.getSupportedPreviewSizes(), false);
        if( size == null  ){
            camPara.setPreviewSize(preViewWidth, preViewHeight);

//            Log.d(tag, "could not  getCloselyPreSize ");
//            return -3;
        }
        else
        {
            camPara.setPreviewSize(size.width, size.height);
            preViewWidth = size.width;
            preViewHeight = size.height;
        }

        //设置自动对焦
        List<String> focusModes = camPara.getSupportedFocusModes();
        if (focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
            mIsSupportAutoFocusContinuousPicture = true;
            camPara.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);// 自动连续对焦
        } else if (focusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
            mIsSupportAutoFocus = true;
            camPara.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);// 自动对焦
        } else {
            mIsSupportAutoFocusContinuousPicture = false;
            mIsSupportAutoFocus = false;
        }
       // Log.d(tag, "width = " + size.width + ", height = " + size.height + "; w = " + DEFAULE_WIDTH + ", h = " + DEFAULE_HEIGHT + ", fps = " + DEFAULE_FPS);

        //p.setPreviewSize(352, 288);
        camPara.setPreviewFormat(ImageFormat.NV21);
        //camPara.setPreviewFormat(ImageFormat.YV12);
        List<int[]> fpsRangeList = camPara.getSupportedPreviewFpsRange();

        ///先设置一下，有些机器上设置帧率会失败，所以其他参数先设置吧
        try {
            camera.setParameters(camPara);
        } catch(Exception e) {
            e.printStackTrace();
        }

        camPara.setPreviewFpsRange(mFps*1000, mFps*1000);
        Log.i(tag, "minfps = " + fpsRangeList.get(0)[0]+" maxfps = "+fpsRangeList.get(0)[1]);
        //camera.setDisplayOrientation(90);
        //mCamera.setPreviewCallback(new H264Encoder(352, 288));
        camWhiteBalance = camPara.getWhiteBalance();
        camFocusMode = camPara.getFocusMode();
        Log.d(tag, "white balance = " + camWhiteBalance + ", focus mode = " + camFocusMode);

        try {
            camera.setParameters(camPara);
        } catch(Exception e) {
            needLoseFrame = true;
            setDefPreViewFps();
            e.printStackTrace();
        }
        int mFrameWidth = camPara.getPreviewSize().width;
        int mFrameHeigth = camPara.getPreviewSize().height;
        int frameSize = mFrameWidth * mFrameHeigth;
        frameSize = frameSize * ImageFormat.getBitsPerPixel(camPara.getPreviewFormat())/8;
        mBuffer = new byte[frameSize];
        camera.addCallbackBuffer(mBuffer);
        camera.setPreviewCallback(youmePreviewCallback);

        if((Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) && (null == svCamera)) {
            GLESVideoMixer.SurfaceContext surfaceContext = VideoMixerHelper.getCameraSurfaceContext();
            if(surfaceContext != null) {
                mTextureID = surfaceContext.textureId;
                mSurfaceTexture = surfaceContext.surfaceTexture;
                mSurfaceTexture.setOnFrameAvailableListener(onFrameAvailableListener);

            }
            else
                {
                mTextureID = GlUtil.generateTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES);
                mSurfaceTexture = new SurfaceTexture(mTextureID);
            }
            try {
                camera.setPreviewTexture(mSurfaceTexture);
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        } else {
            try {
                if(null == svCamera) {
                    camera.setPreviewDisplay(null);
                } else {
                    camera.setPreviewDisplay(svCamera.getHolder());
                }
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }

        try {
            camera.startPreview();
            if (mIsSupportAutoFocusContinuousPicture) {
                camera.cancelAutoFocus();
            }
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        totalTime = 0;
        frameCount = 0;
        isFrontCamera = isFront;
        return 0;
    }

    public void setDefPreViewFps() {
        try {
            if (camera == null) {
                return;
            }
            List<int[]> fpsRangeList = camPara.getSupportedPreviewFpsRange();
            Camera.Parameters p = camera.getParameters();
            p.setPreviewFpsRange(fpsRangeList.get(0)[0], fpsRangeList.get(0)[1] );
            camera.setParameters(p);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }



    public int closeCamera() {
        if (camera != null) {
            camera.setPreviewCallback(null);
            camera.stopPreview();
            camera.release();
            camera = null;
        }
        return 0;
    }

    public void setSvCamera(SurfaceView svCamera) {
        this.svCamera = svCamera;
        //this.svCamera.getHolder().addCallback(new YMSurfaceHolderCallback());
        //this.svCamera.getHolder().setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
    }

    Camera.PreviewCallback youmePreviewCallback = new Camera.PreviewCallback() {
        @Override
        public void onPreviewFrame(byte[] data, Camera camera) {

            int height = preViewHeight;
            int width = preViewWidth;
            int rotation = orientation;
            int mirror ;
            if (isFrontCamera) {
                mirror = YouMeConst.YouMeVideoMirrorMode.YOUME_VIDEO_MIRROR_MODE_NEAR;
            }
            else{
                mirror = YouMeConst.YouMeVideoMirrorMode.YOUME_VIDEO_MIRROR_MODE_DISABLED;
            }
            if (isDoubleStreamingModel) {
                VideoProducer.getInstance().pushFrame(data, cameraId);
            } else {
                //Log.i(tag, "onVideoFrameMixedCallback 1. data len:"+data.length+" fmt: " + YouMeConst.YOUME_VIDEO_FMT.VIDEO_FMT_NV21 + " timestamp:" + System.currentTimeMillis());
                api.inputVideoFrame(data, data.length, width, height, YouMeConst.YOUME_VIDEO_FMT.VIDEO_FMT_NV21, rotation, mirror, System.currentTimeMillis());
            }
            if(camera != null) {
                camera.addCallbackBuffer(data);
            }
        }
    };

    private SurfaceTexture.OnFrameAvailableListener onFrameAvailableListener = new SurfaceTexture.OnFrameAvailableListener() {

        @Override
        public void onFrameAvailable(SurfaceTexture surfaceTexture) {
            long timestamp = System.currentTimeMillis();
            int mirror ;
            if (isFrontCamera) {
                mirror = YouMeConst.YouMeVideoMirrorMode.YOUME_VIDEO_MIRROR_MODE_FAR;
            }
            else{
                mirror = YouMeConst.YouMeVideoMirrorMode.YOUME_VIDEO_MIRROR_MODE_DISABLED;
            }
            api.inputVideoFrameGLES(mTextureID, null, preViewWidth, preViewHeight, YouMeConst.YOUME_VIDEO_FMT.VIDEO_FMT_TEXTURE_OES, 90, mirror, timestamp);

        }
    };


    private byte[] switchYV21UV(byte[] data, int width, int height) {
        int len = data.length;
        byte[] dataRet = new byte[width * height * 3 / 2];

        int YSize = width * height;
        int UVSize = YSize / 4;

        System.arraycopy(data, 0, dataRet, 0, YSize);
        System.arraycopy(data, YSize + UVSize, dataRet, YSize, UVSize);
        System.arraycopy(data, YSize, dataRet, YSize + UVSize, UVSize);

        return dataRet;
    }

    public void setPreViewSize(int width, int height)
    {
        videoWidth = width;
        videoHeight = height;
    }

//    public static void setCaptureSize(int width, int height)
//    {
//        getInstance().setPreViewSize(width, height);
//    }
//
//    public static void setFps(int fps)
//    {
//        getInstance().setPreViewFps(fps);
//    }
//
//    public static int startCapture() {
//        Log.e(tag, "start capture is called");
//        isFrontCamera = true;
//       return getInstance().openCamera(isFrontCamera);
//    }
//
//    public static void stopCapture() {
//        Log.e(tag, "stop capture is called.");
//        getInstance().closeCamera();
//    }
//
//    public static void switchCamera() {
//        Log.e(tag, "switchCamera is called.");
//        isFrontCamera = !isFrontCamera;
//        getInstance().closeCamera();
//        getInstance().openCamera(isFrontCamera);
//    }

    private static class PermissionCheckThread extends Thread {
        @Override
        public void run() {
            try {
                Log.i(tag, "PermissionCheck starts...");
                Context mContext = context;
                while(!Thread.interrupted()) {
                    Thread.sleep(1000);
                    Log.i(tag, "PermissionCheck starts...running");
                    if ((mContext != null) && (mContext instanceof Activity)) {
                        int cameraPermission = ContextCompat.checkSelfPermission((Activity)mContext, Manifest.permission.CAMERA);
                        if (cameraPermission == PackageManager.PERMISSION_GRANTED) {
                            // Once the permission is granted, reset the microphone to take effect.
                            break;
                        }
                        int audioPermission = ContextCompat.checkSelfPermission((Activity)mContext, Manifest.permission.RECORD_AUDIO);
                        if (audioPermission == PackageManager.PERMISSION_GRANTED) {
                            // Once the permission is granted, reset the microphone to take effect.
                            break;
                        }
                    }
                }
            } catch (InterruptedException e) {
                Log.i(tag, "PermissionCheck interrupted");
            }catch (Throwable e) {
                Log.e(tag, "PermissionCheck caught a throwable:" + e.getMessage());

            }
            Log.i(tag, "PermissionCheck exit");
        }
    }
    private static PermissionCheckThread mPermissionCheckThread = null;

    public static boolean startRequestPermissionForApi23() {
        boolean isApiLevel23 = false;
        Context mContext = context;//AppPara.getContext();
        try {
            if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) && (mContext != null)
                    && (mContext instanceof Activity) && (mContext.getApplicationInfo().targetSdkVersion >= 23)) {

                isApiLevel23 = true;
                int cameraPermission = ContextCompat.checkSelfPermission((Activity)mContext, Manifest.permission.CAMERA);
                if (cameraPermission != PackageManager.PERMISSION_GRANTED) {
                    Log.e(tag, "Request for camera permission");
                    ActivityCompat.requestPermissions((Activity)mContext,
                            new String[]{Manifest.permission.CAMERA},
                            1);
                    // Start a thread to check if the permission is granted. Once it's granted, reset the microphone to apply it.
                    if (mPermissionCheckThread != null) {
                        mPermissionCheckThread.interrupt();
                        mPermissionCheckThread.join(2000);
                    }
                    mPermissionCheckThread = new PermissionCheckThread();
                    if (mPermissionCheckThread != null) {
                        mPermissionCheckThread.start();
                    }
                } else {
                    Log.i(tag, "Already got camera permission");
                }
            }
        } catch (Throwable e) {
            Log.e(tag, "Exception for startRequirePermiForApi23");
            e.printStackTrace();
        }

        return isApiLevel23;
    }

    public static void stopRequestPermissionForApi23() {
        try {
            if (mPermissionCheckThread != null) {
                mPermissionCheckThread.interrupt();
                mPermissionCheckThread.join(2000);
                mPermissionCheckThread = null;
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }


    /**
     * 通过对比得到与宽高比最接近的尺寸（如果有相同尺寸，优先选择）
     *
     * @param surfaceWidth           需要被进行对比的原宽
     * @param surfaceHeight          需要被进行对比的原高
     * @param preSizeList            需要对比的预览尺寸列表
     * @return  得到与原宽高比例最接近的尺寸
     */
    protected Camera.Size getCloselyPreSize(int surfaceWidth, int surfaceHeight,
                                            List<Camera.Size> preSizeList, boolean mIsPortrait) {

        int ReqTmpWidth;
        int ReqTmpHeight;
        // 当屏幕为垂直的时候需要把宽高值进行调换，保证宽大于高
        switch(orientation) {
            case 90:
            case 270:
                ReqTmpWidth = surfaceHeight;
                ReqTmpHeight = surfaceWidth;
                break;
            default:
                ReqTmpWidth = surfaceWidth;
                ReqTmpHeight = surfaceHeight;
                break;
        }

        //先查找preview中是否存在与surfaceview相同宽高的尺寸
        float wRatio = 1.0f;
        float hRatio = 1.0f;
        List<Camera.Size> tempList = new ArrayList<Camera.Size>();
        for(Camera.Size size : preSizeList){
            wRatio = (((float) size.width) / ReqTmpWidth);
            hRatio = (((float) size.height) / ReqTmpHeight);
            if((wRatio >= 1.0) && (hRatio >= 1.0)) {
                tempList.add(size);
            }
        }

        int pixelCount = 0;
        Camera.Size retSize = null;
        for(Camera.Size size : tempList) {
            if(0 == pixelCount) {
                pixelCount = size.width * size.height;
                retSize = size;
            } else {
                if((size.width * size.height) < pixelCount) {
                    pixelCount = size.width * size.height;
                    retSize = size;
                }
            }
        }

        // 得到与传入的宽高比最接近的size
//        float reqRatio = ((float) ReqTmpWidth) / ReqTmpHeight;
//        float curRatio, deltaRatio;
//        float deltaRatioMin = Float.MAX_VALUE;
//        Camera.Size retSize = null;
//        for (Camera.Size size : preSizeList) {
//            curRatio = ((float) size.width) / size.height;
//            deltaRatio = Math.abs(reqRatio - curRatio);
//            if (deltaRatio < deltaRatioMin) {
//                deltaRatioMin = deltaRatio;
//                retSize = size;
//            }
//        }

        if( retSize != null ){
            Log.i(tag, "w:"+retSize.width+" h:"+retSize.height);
        }

        return retSize;
    }


}
