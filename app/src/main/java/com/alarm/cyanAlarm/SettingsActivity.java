package com.alarm.cyanAlarm;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.RelativeLayout;
import android.widget.Switch;
import android.widget.Toast;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NavUtils;
import androidx.preference.PreferenceFragmentCompat;

import java.util.Calendar;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

import static com.alarm.cyanAlarm.TcpClient.prev;

public class SettingsActivity extends AppCompatActivity {
    static public boolean sendAtPing = false;
    static public boolean allAlarms = false;
    static public boolean Alarm1 = false;
    static public boolean Alarm2 = false;
    static public boolean Alarm3 = false;

    static public boolean forReal = false;

    public static Switch allAlarms_s;
    public static Switch Alarm1_s;
    public static Switch Alarm2_s;
    public static Switch Alarm3_s;
    public static Switch Real_s;
    static public Button onLive;
    //private static boolean bypassListener = false;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if(android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.HONEYCOMB)
        { getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE); }

        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings_activity);
        Real_s = (Switch)findViewById(R.id.Real);
        Real_s.setChecked(forReal);
        Real_s.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if(isChecked) forReal = true;
                else forReal = false;
                sendSettings(true, 1);
                saveSettings();
            }
        });

        allAlarms_s = (Switch)findViewById(R.id.allAlarms);
        Alarm1_s = (Switch)findViewById(R.id.Alarm1);
        Alarm2_s = (Switch)findViewById(R.id.Alarm2);
        Alarm3_s = (Switch)findViewById(R.id.Alarm3);
        onLive = (Button)findViewById(R.id.onLive);
        Update(true);

        addListener(Alarm1_s, 1);
        addListener(Alarm2_s, 2);
        addListener(Alarm3_s, 3);

        allAlarms_s.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                //if(bypassListener) return;
                if(!isChecked) {
                    Alarm1 = false;
                    Alarm2 = false;
                    Alarm3 = false;
                    Update(false);
                }
                sendSettings(true, 1);
            }
        });

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        Timer update = new Timer();
        TimerTask task = new updateTask();
        update.schedule( task, 0, 500);
    }

    public static class updateTask extends TimerTask {
        public void run() {
            MainActivity.checkConnectivity(onLive);
        }
    }

    private void addListener(Switch alarm, final int n){
        alarm.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {

                if(isChecked) {
                    allAlarms = true;
                    allAlarms_s.setChecked(true);
                }
                if(n==1) Alarm1 = isChecked;
                else if(n==2) Alarm2 = isChecked;
                else if(n==3) Alarm3 = isChecked;
                sendSettings(true, 3);
                saveSettings();
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                NavUtils.navigateUpFromSameTask(this);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public static void Update(boolean all_alarms){
        if(allAlarms_s != null && all_alarms) allAlarms_s.setChecked(allAlarms);
        setAlarm(Alarm1_s, Alarm1);
        setAlarm(Alarm2_s, Alarm2);
        setAlarm(Alarm3_s, Alarm3);
        if(Real_s!= null) Real_s.setChecked(forReal);
    }
    public static void UpdateBooleans(){
        if(allAlarms_s != null) allAlarms = allAlarms_s.isChecked();
        if(Alarm1_s != null) Alarm1 = Alarm1_s.isChecked();
        if(Alarm2_s != null) Alarm2 = Alarm2_s.isChecked();
        if(Alarm3_s != null) Alarm3 = Alarm3_s.isChecked();
    }

    public static void setAlarm(Switch alarm, boolean value){
        if(alarm == null) return;
        if(!allAlarms) alarm.setChecked(false);
        else alarm.setChecked(value);
    }

    public static void sendSettings(boolean update, int n){
        if(update) UpdateBooleans();
        String messageToSend = getSettings();
        for(int i=0; i<n; i++) TcpClient.messagesToSend.add(messageToSend);
    }
    private static String getSettings(){
        String message = "Settings:";
        message += toString(allAlarms);
        message += toString(Alarm1);
        message += toString(Alarm2);
        message += toString(Alarm3);
        message += toString(forReal);
        return message;
    }

    private static String toString(boolean value){
        if(value == false) return "0:";
        else return "1:";
    }

    public static void getSavedSettings(){
        try{
            SharedPreferences settings = MainActivity.mainActivity.getSharedPreferences("Default", 0);
            String settings_str = settings.getString("settings", "1:1:1:1:1:");
            Log.d("mylogs", "saved -->"+settings_str);
            Interpret(settings_str);
        }catch(Exception e){ Interpret("Settings:1:1:1:1:1:"); Log.d("mylogs", "exception in importing settings");}
    }
    public static void saveSettings(){
        SharedPreferences settings = MainActivity.mainActivity.getSharedPreferences("Default", 0);
        SharedPreferences.Editor editor = settings.edit();
        String toSave = getSettings();
        Log.d("mylogs", "TOSAVE -->"+toSave);
        editor.putString("settings", toSave);
        editor.commit();
    }

    static public void Interpret(String message){
        String[] splitted = message.split(":");
        for(int i=1; i<splitted.length; i++) {
            int value = Integer.parseInt(splitted[i]);
            if(i==1){
                if(value==0) SettingsActivity.allAlarms = false;
                else SettingsActivity.allAlarms = true;
            }
            if(i==2){
                if(value==0) SettingsActivity.Alarm1 = false;
                else SettingsActivity.Alarm1 = true;
            }
            if(i==3){
                if(value==0) SettingsActivity.Alarm2 = false;
                else SettingsActivity.Alarm2 = true;
            }
            if(i==4){
                if(value==0) SettingsActivity.Alarm3 = false;
                else SettingsActivity.Alarm3 = true;
            }
            if(i==5){
                if(value==0) SettingsActivity.forReal = false;
                else SettingsActivity.forReal = true;
            }
        }
        SettingsActivity.Update(true);
    }

    static final int dist_Max = 60;
    static final int dist_min = 40;
    static public boolean closeArea = false;
    public static void UpdateLocation(float distance, boolean first){
        if(distance==-1) return;

        if(first){
            if(distance>=dist_Max){
                closeArea = false;
                allAlarms = true;
                Alarm1 = true;
                sendSettings(false, 5);
                saveSettings();
            }
            else{
                closeArea = true;
                Alarm1 = false;
                sendSettings(false, 5);
                saveSettings();
            }
        }
        else{
            if(distance > dist_Max && closeArea) {
                closeArea = false;
                allAlarms = true;
                Alarm1 = true;
                sendSettings(false, 3);
                saveSettings();
            }
            else if(distance < dist_min && !closeArea){
                closeArea = true;
                Alarm1 = false;
                sendSettings(false, 3);
                saveSettings();
            }
        }

    }
}