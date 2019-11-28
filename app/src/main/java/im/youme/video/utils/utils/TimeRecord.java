package im.youme.video.utils.utils;

import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Date;

public class TimeRecord {

    private String mStartTime;
    private String mEndTime;

    public TimeRecord(String start, String end) {
        this.mStartTime = start;
        this.mEndTime = end;
    }

    public String getStartTime(){
        long tmp = Long.parseLong(mStartTime)*1000;
        String dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss").format(new Date(tmp));
        Log.e("日期格式", dateFormat);
        return dateFormat;
    }

    public String getEndTime() {
        long tmp = Long.parseLong(mEndTime)*1000;
        String dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss").format(new Date(tmp));
        Log.e("日期格式", dateFormat);
        return dateFormat;
    }
}
