package com.alarm.cyanAlarm;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.Vibrator;
import android.provider.Settings;
import android.text.Layout;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import static androidx.lifecycle.Lifecycle.State.RESUMED;
import static androidx.lifecycle.Lifecycle.State.STARTED;
import static com.alarm.cyanAlarm.ForegroundService.CHANNEL_ID;
import static com.alarm.cyanAlarm.TcpClient.prev;
import static java.lang.Thread.sleep;

public class MainActivity extends AppCompatActivity {
    static public MainActivity mainActivity;
    static public boolean active = false;
    static public SeekBar sk1;
    static public SeekBar sk2;
    //static public SeekBar sk3;
    //static public SeekBar sk4;
    static public ImageView im;
    static public Button rec1;
    //static public Button rec2;
    static public TextView rmn;
    static public Button onLive;
    Timer timer = new Timer();
    Timer update = new Timer();
    static public Timer countRemaining;
    //static boolean firstTime = true;
    static public int iter = 0;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        //if(active) return;


        //createMainNotification();
        iter++;
        if(!ForegroundService.active) {
            try{
                createNotificationChannel();
                startService();
                ForegroundService.active = true;
            } catch(Exception e){Log.d("mylogs", "Exception in creating Notification");}
            finish();
        }
        active = true;
        mainActivity = this;
        SettingsActivity.getSavedSettings();

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        im = (ImageView)findViewById(R.id.imageView);
        sk1 = (SeekBar)findViewById(R.id.seekBar);
        sk2 = (SeekBar)findViewById(R.id.seekBar2);
        rec1 = (Button)findViewById(R.id.rec_btn1);
        rmn = (TextView)findViewById(R.id.textView5);
        onLive = (Button)findViewById(R.id.onLive);

        InitializeButton(rec1);
        InitializeCountRemaining(false);

        try{
            Initialize(sk1, "Framerate: ");
            Initialize(sk2, "Resolution: ");
        }
        catch(Exception e){Log.d("mylogs", e.getMessage());}
        checkLocationPermission();
        TcpClient.messagesToSend.add("Remaining");

        if(android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.HONEYCOMB)
        { getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE); }
    }


    public static void InitializeCountRemaining(boolean forced){
        if(countRemaining == null || forced) {
            if(countRemaining != null) countRemaining.cancel();
            try{ if(rmn != null) { SetRemainingText(); } }catch (Exception e){}
            countRemaining = new Timer();
            TimerTask task = new remainingTask();
            countRemaining.schedule(task, 1000, 1000);
            uploadRecs();
        }
    }

    public static void SetRemainingText(){
        if(recording == 0) {rmn.setText(""); return;}
        String units = " sec";
        String intest = "";
        int value = recording;
        if(recording>60) {units = " min"; value = recording/60;}
        if(recording>3600) {units = " hrs"; value = recording/3600; intest = ">";}
        rmn.setText(intest + value + units);
    }
    public static void uploadRecs(){
        try{ if(rmn != null) SetRemainingText(); }catch (Exception e){}
        InitializeButton(rec1);
    }
    public static int recording = 0;
    private static void InitializeButton(Button btn){
        try{
            if(btn != null) {
                if(recording>0) btn.setBackgroundColor(Color.RED);
                else btn.setBackgroundColor(Color.argb(100,0,121,107));
            }
        }catch(Exception e){}
    }

    public static boolean mediaInfo = false;
    public static boolean calling = false;
    public static String alarmDate = "";
    public static boolean allowPlay = true;
    public static String text = "onLive";
    public static boolean resumed = true;
    private static void setSeekBarValue(SeekBar sk, String message){
        try{
            if (message.equals("Framerate: ")) {
                if (TcpClient.serverFramerate == -1) sk.setProgress(100);
                else sk.setProgress((int)(TcpClient.serverFramerate * 11.11));
            }
            if (message.equals("Resolution: ")) {
                if (TcpClient.serverResolution == -1) sk.setProgress(100);
                else sk.setProgress((int)(TcpClient.serverResolution * 11.11));
            }
        } catch(Exception e){}
    }

    boolean canSendFramerate = false;
    boolean canSendResolution = false;
    private static boolean touching = false;
    private void Initialize(SeekBar sk, final String message){
        if(sk == null) return;
        setSeekBarValue(sk, message);
        sk.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean b) { }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) { touching = true;}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                try {
                    touching = false;
                    String command = message;
                    int value = (int) ((float) seekBar.getProgress() / 11.11);
                    command += value;
                    if (message.equals("Framerate: ")) {
                        TcpClient.serverFramerate = value;
                        if (TcpClient.serverFramerate != -1) canSendFramerate = true;
                        if (canSendFramerate) TcpClient.messagesToSend.add(command);
                    }
                    if (message.equals("Resolution: ")) {
                        TcpClient.serverResolution = value;
                        if (TcpClient.serverResolution != -1) canSendResolution = true;
                        if (canSendResolution) TcpClient.messagesToSend.add(command);
                    }
                } catch (Exception e) { }
            }
        });
    }




    /////////////////////////////////////////////////////CONFIGURATION
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        Log.d("mylogs", "Configure changed");
        super.onConfigurationChanged(newConfig);
        setContentView(R.layout.activity_main);
    }
    /////////////////////////////////////////////////////START
    @Override
    protected void onStart()
    {
        stopRingtone = true;
        //if(r != null) stopRingtone = true;
        Log.d("mylogs", "Start");
        //if(firstTime){ createNotificationChannel(); }
        // TODO Auto-generated method stub
        TimerTask task = new updateTask();
        update.schedule( task, 100, 100);
        super.onStart();

    }

    private static int iter_update;
    public class updateTask extends TimerTask {
        public void run() {
            try{
                checkConnectivity(onLive);
                updateSeekBarValues();
                if(calling){
                    allowPlay = true;
                    calling = false;
                    Call();
                }
                if(mediaInfo) {
                    boolean itsok = true;
                    if(TcpClient.serverFramerate != -1){
                        try{sk1.setProgress(TcpClient.serverFramerate*11);} catch(Exception e){}
                    } else itsok = false;
                    if(TcpClient.serverResolution != -1){
                        try{sk2.setProgress(TcpClient.serverResolution*11);} catch(Exception e){}
                    } else itsok = false;
                    if(itsok) mediaInfo =false;
                }
            } catch(Exception e){}
        }
    }

    /////////////////////////////////////////////////////RESUME
    @Override
    protected void onResume()
    {
        Log.d("mylogs", "Resume");
        resumed = true;
        try{ connect(null);} catch(Exception e){}
        TimerTask task = new onResumeTask();
        timer.schedule( task, 1000);
        // TODO Auto-generated method stub
        super.onResume();
    }
    public class onResumeTask extends TimerTask {
        public void run() {
            if(stopRingtone && player != null) {allowPlay = false; stopRingtone = false; player = null;}
            //if(resumed) {TcpClient.messagesToSend.clear(); }//TcpClient.messagesToSend.add("StartMedia");}
            //if(r!= null) {r.stop(); r = null;}
            Log.d("mylogs", "after 1s of Resume");
        }
    }
    /////////////////////////////////////////////////////PAUSE
    @Override
    protected void onPause()
    {
        if(player != null) stopRingtone = false;
        Log.d("mylogs", "Pause");
        im.setImageResource(android.R.color.transparent);
        resumed = false;
        super.onPause();
        TimerTask task = new onPauseTask();
        timer.schedule( task, 1000);

        // TODO Auto-generated method stub
    }
    static boolean stopRingtone = false;
    public class onPauseTask extends TimerTask {
        public void run() {
            Log.d("mylogs", "after 1s of Pause");
            if(!resumed) {TcpClient.messagesToSend.clear(); TcpClient.messagesToSend.add("StopMedia");}
            //try{connection.mTcpClient.stopClient();} catch(Exception e){}
        }
    }
    /////////////////////////////////////////////////////DESTROYED
    @Override
    protected void onDestroy() {
        if(player != null) stopRingtone = false;
        Log.d("mylogs", "Pause");
        resumed = false;
        active = false;
        TcpClient.messagesToSend.clear();
        TimerTask task = new onDestroyTask();
        timer.schedule( task, 1000);
        update.cancel();
        //TcpClient.messagesToSend.add("StopMedia");
        super.onDestroy();
    }
    public class onDestroyTask extends TimerTask {
        public void run() {
            Log.d("mylogs", "after 1s of Destroy");
            if(!resumed) {TcpClient.messagesToSend.clear(); TcpClient.messagesToSend.add("StopMedia");}
        }
    }
    /////////////////////////////////////////////////////


    public static void updateSeekBarValues(){
        if(touching) return;
        setSeekBarValue(sk1, "Framerate: ");
        setSeekBarValue(sk2, "Resolution: ");
    }

    public static class remainingTask extends TimerTask {
        public void run() {
            if(recording>0) {
                recording--;
            }
            uploadRecs();
        }
    }

    public static void checkConnectivity(Button onLive){
        boolean online = false;
        if(prev!= null){
            Date now = Calendar.getInstance().getTime();
            long diff = (now.getTime() - prev.getTime());
            if(diff < 6*1000) online = true;
        }
        if(online) onLive.setBackgroundColor(Color.GREEN);
        else onLive.setBackgroundColor(Color.GRAY);
    }





    //public static Ringtone r;
    public static MediaPlayer player;
    public static Vibrator v;
    public void Call(){
        if(player == null){
            createNotification();
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_UNKNOWN)
                    .setLegacyStreamType(AudioManager.STREAM_NOTIFICATION)
                    .setUsage(AudioAttributes.USAGE_ALARM).build();
            AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            int s = audioManager.generateAudioSessionId();
            player = MediaPlayer.create(getBaseContext(), R.raw.alarm,audioAttributes,s);
            player.setLooping(false);
            //mediaPlayer.start();

            //MediaPlayer player = MediaPlayer.create(this, R.raw.alarm);
            //int currentVolume = audio.getStreamVolume(AudioManager.STREAM_ALARM);

           // player.setVolume(currentVolume, currentVolume);
            //player = MediaPlayer.create(this, R.raw.alarm);
        }
        v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        //Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        //r = RingtoneManager.getRingtone(getApplicationContext(), notification);
        startRingtone();

        //Intent intent = new Intent(getApplicationContext(), MainActivity.class);
        //intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK| Intent.FLAG_ACTIVITY_SINGLE_TOP);
        //startActivity(intent);
    }

    static boolean threadRunning = false;
    private void startRingtone(){
        allowPlay = true;
        if(threadRunning) return;
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                threadRunning = true;
                int max = 10;
                if(!SettingsActivity.forReal) max = 1;
                for(int i=0; i<max; i++){
                    if(allowPlay) {v.vibrate(1100); player.start();}
                    else break;
                    //else { player.pause(); try { sleep(10); } catch (InterruptedException e) { }; break; }
                    try { sleep(1100); } catch (InterruptedException e) { }
                    //player.pause();
                    //try { sleep(10); } catch (InterruptedException e) { }
                }
                for(int i=0; i<5; i++){
                    try { player.stop(); } catch (Exception e) { try{sleep(50); }catch(Exception ex){} }
                    //Log.d("mylogs", "tentative");
                }
                player = null;
                allowPlay = true;
                calling = false;
                threadRunning = false;
            }
        });
        t.start();
    }


    public void connect(View v){
        //if(firstTime){ startService(); firstTime = false; }
        if(resumed) TcpClient.messagesToSend.add("StartMedia");
        MyJobService.iterationStepBack(true);
    }
    public void record(View v){
        int value = recording;
        if(value >=1800) value = (value/1800)*1800 + 1800;
        else if(value >=600) value = (value/600)*600 + 600;
        else if(value >=300) value = (value/300)*300 + 300;
        else value += 60;
        if(value>7200) value = 7200;
        String time = String.valueOf(value);
        for(int i=time.length(); i<4; i++) time = "0"+time;
        TcpClient.messagesToSend.add("Save: "+time);
    }
    public void Cam1_btn(View v){
        TcpClient.messagesToSend.add("WantToSeeCam0");
    }
    public void Cam2_btn(View v){
        TcpClient.messagesToSend.add("WantToSeeCam1");
    }
    public void Cam3_btn(View v){
        TcpClient.messagesToSend.add("WantToSeeCam2");
    }
    public void Cam4_btn(View v){
        TcpClient.messagesToSend.add("WantToSeeCam3");
    }
    public void Camall_btn(View v){
        TcpClient.messagesToSend.add("WantToSeeCam-1");
    }

    public void goToSettings(View view){
        openSettings();
    }
    public void openSettings(){
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);
        //setContentView(R.layout.activity_alarm_settings);
    }


    public void startService(){
        Intent serviceIntent = new Intent(this, ForegroundService.class);
        //serviceIntent.putExtra("inputExtra", "Servizio APP");
        startForegroundService(serviceIntent);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Main Notification Channel",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(serviceChannel);

            NotificationChannel serviceChannel_messages = new NotificationChannel(
                    CHANNEL_ID_MESSAGES,
                    "Notification Alert Channel",
                    NotificationManager.IMPORTANCE_HIGH
            );
            manager.createNotificationChannel(serviceChannel_messages);
        }
    }

    private void createMainNotification(){
        Intent notificationIntent = new Intent(this, MainActivity.class);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Alarm is running")
                .setSmallIcon(R.mipmap.ic_launcher_round)
                .setContentIntent(pendingIntent)
                .build()
                ;
        NotificationManager notificationManager = (NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(1, notification);
    }

    static public final String CHANNEL_ID_MESSAGES = "Alert_Channel";
    static public int id = 1;
    public void createNotification(){
        id++;
        Log.d("mylogs", "creating Notification");
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 1 /* Request code */, intent,
                0);
        Calendar c = Calendar.getInstance();
        Date now = c.getTime();
        String date = now.toString();
        try{
            date = new SimpleDateFormat("dd MMM 'at' hh:mm aaa").format(now);
        } catch(Exception e){}
        date = alarmDate;

        NotificationCompat.Builder notificationBuilder = (NotificationCompat.Builder) new NotificationCompat.Builder(this, CHANNEL_ID_MESSAGES)
                .setSmallIcon(R.mipmap.ic_launcher_round)
                .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.drawable.common_google_signin_btn_icon_dark))
                .setContentTitle("ALERT!")
                .setContentText("Alarm triggered at "+ date)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                ;
        NotificationManager notificationManager = (NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(id , notificationBuilder.build());
        Log.d("mylogs", "Notification created");
    }



    public static final int MY_PERMISSIONS_REQUEST_LOCATION = 99;

    public boolean checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.ACCESS_FINE_LOCATION)) {

                // Show an explanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.
                new AlertDialog.Builder(this)
                        .setTitle("Permission in using GPS")
                        .setMessage("CyanAlarm would like to use GPS service in order to implement the " +
                                "dynamic and automized alarming system, based on the relative position " +
                                "between your actual position and your home.")
                        .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                //Prompt the user once explanation has been shown
                                ActivityCompat.requestPermissions(MainActivity.this,
                                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                                        MY_PERMISSIONS_REQUEST_LOCATION);
                            }
                        })
                        .create()
                        .show();


            } else {
                // No explanation needed, we can request the permission.
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        MY_PERMISSIONS_REQUEST_LOCATION);
            }
            return false;
        } else {
            return true;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_LOCATION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    // permission was granted, yay! Do the
                    // location-related task you need to do.
                    /*
                    if (ContextCompat.checkSelfPermission(this,
                            Manifest.permission.ACCESS_FINE_LOCATION)
                            == PackageManager.PERMISSION_GRANTED) {

                        //Request location updates:
                        locationManager.requestLocationUpdates(provider, 400, 1, this);
                    }*/

                } else {

                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.

                }
                return;
            }

        }
    }

}
