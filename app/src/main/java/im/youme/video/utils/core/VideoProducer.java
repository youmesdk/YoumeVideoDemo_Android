package im.youme.video.utils.core;

import android.util.Log;

import com.youme.voiceengine.NativeEngine;

import java.util.ArrayList;

/**
 * Created by bhb on 2017/11/21.
 */

public class VideoProducer extends Thread {

    static private  final String TAG = "VideoProducer";
    private ArrayList<byte[]> yuvArray1 = new ArrayList<byte[]>();
    private ArrayList<byte[]> yuvArray1_free = new ArrayList<byte[]>();
    private ArrayList<byte[]>  yuvArray2 = new ArrayList<byte[]>();
    private ArrayList<byte[]>  yuvArray2_free = new ArrayList<byte[]>();
    private byte[] yuvfull;
    private int mWidth ;
    private int mHeight;
    private int mRotation;
    private boolean isLoops = false;
    private final int mCacheSize = 2;
    private Object mObject = new Object();
    private myThread mThread;
    static private VideoProducer sCombineThread;
    static public VideoProducer getInstance()
    {
        if(sCombineThread == null)
            sCombineThread = new VideoProducer();
        return sCombineThread;
    }
    public VideoProducer() {}

    public void init(int width, int height, int orientation)
    {
        mWidth = width;
        mHeight = height;
        mRotation = orientation;
        int size = width*height*3/2;
        for (int i =0; i < mCacheSize; i++)
        {
            byte[] yuv1 = new byte[size];
            byte[] yuv2 = new byte[size];
            yuvArray1.add(yuv1);
            yuvArray2.add(yuv2);
        }
        yuvfull = new byte[size*2];
        isLoops = true;
        mThread = new myThread();
        mThread.start();
    }

    public void release()
    {
        try {
            isLoops = false;
            mObject.notify();
            mThread.join(200);
            mThread = null;
            synchronized (mObject) {
                yuvArray1.clear();
                yuvArray2.clear();
                yuvArray1_free.clear();
                yuvArray2_free.clear();
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    public void pushFrame(byte [] src_yuv, int cameraid)
    {
        synchronized(this.mObject) {
            if (0 == cameraid) {
                if (yuvArray1_free.size() > 0) {
                    byte[] dest_yuv = yuvArray1_free.get(0);
                    yuvArray1_free.remove(0);
                    System.arraycopy(src_yuv, 0, dest_yuv, 0, mWidth * mHeight * 3 / 2);
                    yuvArray1.add(dest_yuv);
                }

            } else {
                if (yuvArray2_free.size() > 0) {
                    byte[] dest_yuv = yuvArray2_free.get(0);
                    yuvArray2_free.remove(0);
                    System.arraycopy(src_yuv, 0, dest_yuv, 0, mWidth * mHeight * 3 / 2);
                    yuvArray2.add(dest_yuv);
                }
            }
            mObject.notify();
        }


    }

    class myThread extends  Thread {
        @Override
        public void run() {

            int size_y = mWidth * mHeight;
            int size_uv = mWidth * mHeight / 4;
            while (isLoops) {
                synchronized (mObject) {
                    if (yuvArray1.size() > 0 && yuvArray2.size() > 0) {
                        byte[] yuv1 = yuvArray1.get(0);
                        byte[] yuv2 = yuvArray2.get(0);

                        //y
                        System.arraycopy(yuv1, 0, yuvfull, 0, size_y);
                        System.arraycopy(yuv2, 0, yuvfull, size_y, size_y);

                        /*
                        //u
                        System.arraycopy(yuv1, size_y + size_uv, yuvfull, size_y * 2, size_uv);
                        System.arraycopy(yuv2, size_y + size_uv, yuvfull, size_y * 2 + size_uv, size_uv);

                        //v
                        System.arraycopy(yuv1, size_y, yuvfull, size_y * 2 + size_uv * 2, size_uv);
                        System.arraycopy(yuv2, size_y, yuvfull, size_y * 2 + size_uv * 3, size_uv);
                        */

                        System.arraycopy(yuv1, size_y, yuvfull, size_y*2, size_uv*2);
                        System.arraycopy(yuv2, size_y, yuvfull, size_y*2+size_uv*2, size_uv*2);


                        yuvArray1_free.add(yuv1);
                        yuvArray2_free.add(yuv2);
                        yuvArray1.remove(0);
                        yuvArray2.remove(0);
                        NativeEngine.inputVideoFrame(yuvfull, yuvfull.length, mWidth, mHeight * 2, 1, mRotation, 0, System.currentTimeMillis());

                    } else {
                        try {
                            mObject.wait(1000);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
            Log.d(TAG, "combine thread exit--");
        }
    }
}
