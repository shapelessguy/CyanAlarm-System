package com.alarm.cyanAlarm;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Calendar;
import java.util.Timer;
import java.util.TimerTask;

import static java.lang.Thread.sleep;


public class MyJobService extends JobService {
    static public Bitmap bmp;
    static public TcpClient mTcpClient;
    static public Timer update_ip = new Timer();
    static public Timer update_gps = new Timer();
    static public long iterationUpdateIP = 601;
    static public int fails = 0;

    private static final String TAG = "mylogs";
    static public boolean job = false;
    @Override
    public boolean onStartJob(JobParameters params) {
        doBackgroundWork(params);
        return true;
    }
    private void doBackgroundWork(final JobParameters params) {
        final Intent startMain = new Intent(this, MainActivity.class);
        new Thread(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "Trying to start the job");
                if(job) {
                    return;
                }
                if(!ForegroundService.active) {startActivity(startMain); return;}
                job = true;
                Log.d(TAG, "Job started");

                //startMainNotification()

                GPSTracker.mGPS = new GPSTracker(MainActivity.mainActivity);
                TimerTask task_gps = new MyJobService.updateGPSTask();
                MyJobService.update_gps.schedule( task_gps, 1000, 90*1000);

                TimerTask task = new MyJobService.updateIPTask();
                update_ip.schedule( task, 0, 1000);

                //we create a TCPClient object
                mTcpClient = new TcpClient(new TcpClient.OnMessageReceived(){
                    @Override
                    //here the messageReceived method is implemented
                    public void messageReceived(byte[] message) {
                        //this method calls the onProgressUpdate
                        onProgressUpdate(message);
                    }
                }
                );

                while(true){
                    try {
                        if(false) break;
                        Cycle();
                    }
                    catch (Exception e) { e.printStackTrace(); try{sleep(2000);} catch(Exception ex){}}
                }
                Log.d(TAG, "Job finished");
                jobFinished(params, false);
                job = false;
            }
        }).start();
    }
    @Override
    public boolean onStopJob(JobParameters params) {
        Log.d(TAG, "Job cancelled before completion");
        //job = false;
        return true;
    }

    public void Cycle(){
        if(TcpClient.SERVER_IP.equals("")) {try{Thread.sleep(50);} catch(Exception e){}; return;}

        if(MainActivity.resumed) { TcpClient.messagesToSend.add("StartMedia"); }
        mTcpClient.run();
        if(fails<999999) fails++;
        //if(fails >= 20) return null;
        MainActivity.recording = 0;
        MainActivity.uploadRecs();
        try{Thread.sleep(TcpClient.timeoutClientSocket_connecting);} catch(Exception e){}; return;
    }

    public static void iterationStepBack(boolean now){
        if(now) {iterationUpdateIP = 601; return;}
        int proposal = 590 - 10*fails;//(int)(Math.sqrt(fails));
        if(proposal<0) proposal = 0;
        if(iterationUpdateIP < proposal) iterationUpdateIP = proposal;
    }

    public static class updateIPTask extends TimerTask {
        public void run() {
            iterationUpdateIP ++;
            //Log.d("mylogs", "mah.. "+iterationUpdateIP);
            if(iterationUpdateIP >= 10 * 60) {new ConnectToFirebase(); iterationUpdateIP = 0;}
        }
    }

    private static int iter=0;
    public static class updateGPSTask extends TimerTask {
        public void run() {
            try{
                iter+= 1;
                GPSTracker.mGPS.getLocation();
                float[] results = new float[1];
                Location.distanceBetween(GPSTracker.mGPS.getLatitude(), GPSTracker.mGPS.getLongitude(),
                        40.8546648, 14.2295371, results);

                float distanceFromHome;
                if(GPSTracker.mGPS.getLongitude()==0 || GPSTracker.mGPS.getLatitude()==0) distanceFromHome = -1;
                else distanceFromHome = results[0];

                //distanceFromHome = -20+(float) Math.sqrt (  Math.pow( (50+50*Math.cos((double)iter)), 2 ) +  Math.pow( (50+50*Math.sin((double)iter)), 2 ));
                Log.d("mylogs","Location: " + distanceFromHome + "");
                MainActivity.text = String.valueOf(distanceFromHome);
                if(distanceFromHome== -1) iter --;
                SettingsActivity.UpdateLocation(distanceFromHome, iter==1);
                //Log.d("mylogs", "dist_fromHome:"+distanceFromHome+"   Lat"+GPSTracker.mGPS.getLatitude()+
                //        "Lon"+GPSTracker.mGPS.getLongitude() + "  distance: "+ results[0]);
            }
            catch(Exception e){}
        }
    }

    protected void onProgressUpdate(byte[]... values) {
        byte[] bytes = values[0];
        bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        try{if(MainActivity.resumed) MainActivity.im.setImageBitmap(bmp);} catch(Exception e){}
    }


}