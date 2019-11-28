package im.youme.video.sample2;


import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.InputType;
import android.text.method.DigitsKeyListener;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;

import com.tencent.bugly.crashreport.CrashReport;
import com.youme.voiceengine.mgr.YouMeManager;


/**
 * Created by lingguodong on 2018/3/19.
 * 这个是ViewDemo的初始界面
 */

public class Video extends AppCompatActivity {
    private Toolbar toolbar;
    private Button start_button;
    private EditText sessionid_edittext1;
    private Spinner AreaSelectionl;
    //bugly
    private static final String YOUME_BUGLY_APP_ID = "428d8b14e2";


    public String local_user_id;
    public String currentRoomID;
    public int choosedServerRegion;

    @Override
    public void onCreate(Bundle savedInstanceState) {

        Context context = getApplicationContext();
        // bugly
        CrashReport.initCrashReport(context, YOUME_BUGLY_APP_ID, false);

        //========= load youme so =========
        YouMeManager.Init(this); //这里需要传this参数，需要Activity实例，使用时用来请求摄像头和麦克风权限

//        NativeEngine.setServerMode(1);

        super.onCreate(savedInstanceState);

//        int communicationModeValue = 0;
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
//            communicationModeValue = AudioManager.MODE_IN_COMMUNICATION;
//        } else {
//            communicationModeValue = AudioManager.MODE_IN_CALL;
//        }
//        AudioManager mAudioManager = (AudioManager) this.getSystemService  (Context.AUDIO_SERVICE);
//        mAudioManager.setMode(communicationModeValue);

        setContentView(R.layout.video);
        toolbar = (Toolbar) findViewById(R.id.toolbar);
        AreaSelectionl = (Spinner) findViewById(R.id.areaSelection);
        //默认初始位置为0
        AreaSelectionl.setSelection(0);
        //设置点击的元素监听
        AreaSelectionl.setOnItemSelectedListener(ItemSelectedListener);

        toolbar.setTitle("  VieoDemo");
        //用toolbar替换actionbar
        setSupportActionBar(toolbar);
        //设置导航Icon，必须在setSupportActionBar(toolbar)之后设置
        // toolbar.setNavigationIcon(R.mipmap.ic_menu_white_24dp);
        //随机userid
        local_user_id = "an"+(int) (Math.random() * 1000000) +"_" + (int) (Math.random() * 1000000)+"_1";


        //找到房间ID输入组件
        sessionid_edittext1 = (EditText) findViewById(R.id.sessionid_edittext1);
        sessionid_edittext1.setInputType(InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
//        String digists = "0123456789abcdefghigklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
//        sessionid_edittext1.setKeyListener(DigitsKeyListener.getInstance(digists));
        sessionid_edittext1.setImeOptions(EditorInfo.IME_ACTION_DONE);
        currentRoomID = "";

        start_button = (Button) findViewById(R.id.start_button);

        start_button.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                //获取输入的频道号
                currentRoomID = sessionid_edittext1.getText().toString().trim();
                //输入不为空
                if (currentRoomID.length() >= 1) {
                    Log.i("currentRoomID", "" + currentRoomID.length());
                    //跳转渲染视频源
                    goVideoDemo();
                }
            }
        });
    }


    private AdapterView.OnItemSelectedListener ItemSelectedListener = new AdapterView.OnItemSelectedListener() {


        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            switch (parent.getId()) {
                //点击区域选择
                case R.id.areaSelection:
                    SelectionPosition(position);
                    break;
            }
        }

        @Override
        public void onNothingSelected(AdapterView<?> parent) {

        }
    };

    private void SelectionPosition(int position) {

        switch (position) {
            case 0:
                choosedServerRegion = 0;
                break;

            case 1:
                choosedServerRegion = 1;
                break;

            default:
                choosedServerRegion = 0;
                break;


        }


    }

    /**
     * 该方法是用来加载菜单布局的
     *
     * @param menu
     * @return
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        //加载菜单文件
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_settings:
                // open setting activity
                Intent intent = new Intent();
                intent.setClass(Video.this, videoSettings.class);
                startActivity(intent);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        YouMeManager.Uninit();
    }


    private void goVideoDemo() {
        //BackgroundMusic.getInstance(this).playBackgroundMusic("call_ringtone.ogg",false);
        Intent intent = new Intent(Video.this, VideoCapturerActivity.class);
        String roomId = sessionid_edittext1.getText().toString();
        intent.putExtra("userid", local_user_id);
        intent.putExtra("roomid", roomId);
        intent.putExtra("Area", choosedServerRegion);
        startActivity(intent);

    }


}
