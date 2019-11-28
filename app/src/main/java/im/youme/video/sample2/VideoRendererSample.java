package im.youme.video.sample2;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import com.youme.voiceengine.VideoMgr.VideoFrameCallback;
import com.youme.voiceengine.YouMeConst.YOUME_VIDEO_FMT;
import com.youme.voiceengine.video.SurfaceViewRenderer;
import com.youme.voiceengine.video.VideoBaseRenderer.I420Frame;

import android.util.Log;

/**
 * 摄像头渲染
 * 
 * @author fire
 *
 */
public class VideoRendererSample implements VideoFrameCallback {
	public class RenderInfo {
		public int rotation;
		public SurfaceViewRenderer view;
		public RenderInfo(int rotation, SurfaceViewRenderer view){
			this.rotation = rotation;
			this.view = view;
		}
	}
	private final static String TAG = "VideoRendererSample";
	private Map<String, RenderInfo> renderers = new HashMap<String, RenderInfo>();
	
	private static final VideoRendererSample instance = new VideoRendererSample();
	private String localUserId ="";
	private boolean bPauseRender = false;
	
	private VideoRendererSample()	{
		// 私有构造
	}
	
	public static VideoRendererSample getInstance() {
		return instance;
	}
	
	public void init() {
		renderers.clear();
	}
	
	public void setLocalUserId(String userId){
		localUserId = userId;
	}

	public void pauseRender(){
	    bPauseRender = true;
    }

    public void resumeRender(){
	    bPauseRender = false;
    }
	/**
	 * 添加渲染源
	 * @param view
	 * @return
	 */
	public int addRender(String userId, SurfaceViewRenderer view) {
		
		//int renderId = api.createRender(userId);
		RenderInfo info = new RenderInfo(0, view);
		renderers.put(userId, info);
		Log.d(TAG, "addRender userId:"+userId);
		return 0;
	}
	

	public int deleteRender(String userId) {
		//int ret = api.deleteRender(renderId);
		renderers.remove(userId);
		Log.d(TAG, "deleteRender userId:"+userId);
		return 0;
	}
	
	public void deleteAllRender(){
		renderers.clear();
	}

	public void onVideoFrameCallback(String userId, byte[] data, int len, int width, int height, int fmt, long timestamp){
	    if(!bPauseRender) {
            RenderInfo info = renderers.get(userId);
            if (info != null) {
                int[] yuvStrides = {width, width / 2, width / 2};

                int yLen = width * height;
                int uLen = width * height / 4;
                int vLen = width * height / 4;
                byte[] yPlane = new byte[yLen];
                byte[] uPlane = new byte[uLen];
                byte[] vPlane = new byte[vLen];

                System.arraycopy(data, 0, yPlane, 0, yLen);
                System.arraycopy(data, yLen, uPlane, 0, uLen);
                System.arraycopy(data, (yLen + uLen), vPlane, 0, vLen);

                ByteBuffer[] yuvPlanes = {ByteBuffer.wrap(yPlane), ByteBuffer.wrap(uPlane), ByteBuffer.wrap(vPlane)};

                //rotationDegree = 270; // for android

                I420Frame frame = new I420Frame(width, height, info.rotation, yuvStrides, yuvPlanes);
                info.view.renderFrame(frame);
            } else {
                //Log.e(TAG, "SurfaceViewRenderer not found, sessionId : " + userId);
            }
        }
	}

	public void onVideoFrameMixed(byte[] data, int len, int width, int height, int fmt, long timestamp){
	    if(!bPauseRender) {
            String userId = localUserId;
            RenderInfo info = renderers.get(userId);
            if (info != null) {
                int[] yuvStrides = {width, width / 2, width / 2};

                int yLen = width * height;
                int uLen = width * height / 4;
                int vLen = width * height / 4;
                byte[] yPlane = new byte[yLen];
                byte[] uPlane = new byte[uLen];
                byte[] vPlane = new byte[vLen];

                System.arraycopy(data, 0, yPlane, 0, yLen);
                System.arraycopy(data, yLen, uPlane, 0, uLen);
                System.arraycopy(data, (yLen + uLen), vPlane, 0, vLen);

                ByteBuffer[] yuvPlanes = {ByteBuffer.wrap(yPlane), ByteBuffer.wrap(uPlane), ByteBuffer.wrap(vPlane)};

                //rotationDegree = 270; // for android

                I420Frame frame = new I420Frame(width, height, info.rotation, yuvStrides, yuvPlanes);
                info.view.renderFrame(frame);
            }
        }
	}
	
	public void onVideoFrameCallbackGLES(String userId, int type, int texture, float[] matrix, int width, int height, long timestamp){
	    if(!bPauseRender) {
            RenderInfo info = renderers.get(userId);
            if (info != null) {
                I420Frame frame = new I420Frame(width, height, 0, texture, matrix, type == YOUME_VIDEO_FMT.VIDEO_FMT_TEXTURE_OES);
                frame.rotationDegree = info.rotation;
                info.view.renderFrame(frame);
                //info.view.setVisibility(View.VISIBLE);
            }
        }
	}
	
	public void onVideoFrameMixedGLES(int type, int texture, float[] matrix, int width, int height, long timestamp) {
	    if(!bPauseRender) {
            String userId = localUserId;
            RenderInfo info = renderers.get(userId);
            if (info != null) {
                I420Frame frame = new I420Frame(width, height, 0, texture, matrix, type == YOUME_VIDEO_FMT.VIDEO_FMT_TEXTURE_OES);
                frame.rotationDegree = info.rotation;
                info.view.renderFrame(frame);
            }
        }
	}
	public	int onVideoRenderFilterCallback(int var1, int var2, int var3, int var4, int var5){
			return 0;
	}

	


	public int getRotation(String userId ) {
		RenderInfo info = renderers.get(userId);
		if (info != null) {
		   return  info.rotation;
		}
		return 0;
	}

	public void switchRotation(String userId) {
		RenderInfo info = renderers.get(userId);
		if (info != null) {
		  info.rotation += 90;
		 	info.rotation = info.rotation == 360 ? 0 : info.rotation;
		}
	}



}
