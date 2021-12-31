package com.alarm.cyanAlarm;
import android.os.AsyncTask;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Array;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.Socket;
import android.util.Log;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.TimerTask;

import static java.lang.Thread.sleep;


public class TcpClient {
    public static final int SERVER_PORT = 10000;
    public static int timeoutClientSocket_connecting = 8*1000;  // socket tries to connect only for this amount of time
    public static int timeoutClientSocket_reading = 3*1000;   // socket reading timeout
    public static int timeoutFinal = 12*1000; // seconds of missing pings after which disconnects

    public static final String TAG = TcpClient.class.getSimpleName();
    public static String SERVER_IP = ""; //server IP address
    public static Socket socket;
    public static boolean toClose = false;
    private String endMessage = "Separator!";
    private OnMessageReceived mMessageListener = null;
    private boolean mRun = false;
    private PrintWriter mBufferOut;
    private BufferedReader mBufferIn;
    public static List<String> messagesToSend = new ArrayList<>();
    public static String messageToSend = "";
    public static int serverResolution = -1;
    public static int serverFramerate = -1;


    /**
     * Constructor of the class. OnMessagedReceived listens for the messages received from server
     */
    //OnMessageReceived listener
    public TcpClient(OnMessageReceived listener) {
        mMessageListener = listener;
    }

    /**
     * Sends the message entered by client to the server
     *
     * @param message text entered by client
     */
    public void sendMessage(final String message) {
        if(connected>0){
            //Log.d("mylogs", "per mandare..");
            Runnable runnable = new Runnable() {
                @Override
                public void run() {
                    if (mBufferOut != null) {
                        String[] messages = new String[messagesToSend.size()];
                        messages = messagesToSend.toArray(messages);
                        messagesToSend.clear();
                        for(int i=0; i<messages.length; i++){
                            String message_ = messages[i];
                            mBufferOut.println(message_);
                            mBufferOut.flush();
                            if(!message_.equals("__Ping__"))
                            Log.d("mylogs", "Message ("+message_.length()+" bytes) sent: " + message_);
                            if(i != messages.length-1) try { sleep(50); } catch (InterruptedException e) {}
                        }

                        /*
                        if(!message.equals("")){
                            mBufferOut.println(message);
                            mBufferOut.flush();
                            messageToSend = "";
                            Log.d("mylogs", "Message ("+message.length()+" bytes) sent: " + message);
                        }
                        */
                    }
                }
            };
            Thread thread = new Thread(runnable);
            thread.start();
        }
    }

    /**
     * Close the connection and release the members
     */
    public void stopClient() {

        mRun = false;

        if (mBufferOut != null) {
            mBufferOut.flush();
            mBufferOut.close();
        }

        mMessageListener = null;
        mBufferIn = null;
        mBufferOut = null;
    }

    static int connected = 0;
    public void run() {


        mRun = true;
        connected = 0;
        try {
            //here you must put your computer's IP address.
            InetAddress serverAddr = InetAddress.getByName(SERVER_IP);

            Log.d("mylogs", "Connecting to serverIP: "+ SERVER_IP);

            //create a socket to make the connection with the server
            if(socket != null) try{
                socket.close();
                sleep(100);
            }
            catch(Exception e){}
            socket = new Socket();
            socket.setSoTimeout(timeoutClientSocket_reading);

            socket.connect(new InetSocketAddress(serverAddr, SERVER_PORT), timeoutClientSocket_connecting);


            MyJobService.fails = 0;
            byte[] bytes;
            boolean debug = false;
            try {
                //while(true){
                //    if(false) break;
                //    try{ sleep(100);} catch(Exception e){}
                //}
                //Thread.sleep(100);
                //sends the message to the server
                mBufferOut = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())), true);

                //receives the message which the server sends back
                mBufferIn = new BufferedReader(new InputStreamReader(socket.getInputStream()));


                //in this while the client listens for the messages sent by the server
                List<byte[]> sequences = new ArrayList<byte[]>();
                List<Integer> readed = new ArrayList<Integer>();
                while (mRun) {
                    //sleep(5);
                    if(connected == 1){ toDo_atConnectionStart();}
                    else if(connected>2) connected = 2;

                    if(!CheckPing()) {
                        //Calendar cal = Calendar.getInstance();
                        prev = null;//cal.getTime();
                        Log.d("mylogs", "disconnected by timeout");break;}
                    //Thread.sleep(30);
                    if(toClose){try{socket.close();} catch(Exception e){} return;}
                    if(!messageToSend.equals("") || messagesToSend.size()!=0) {sendMessage(messageToSend);}
                    bytes = new byte[200000];
                    int read = 0;
                    try{ read = socket.getInputStream().read(bytes);}
                    catch(SocketTimeoutException e) {}
                    readed.add(read);
                    while(readed.size()>10) readed.remove(0);
                    Log.d("mylogs", readed.toString());

                    try{
                        bytes = Arrays.copyOfRange(bytes, 0, read);

                        String message = new String(bytes, "UTF-8");
                        if(read > 50) sequences.add(bytes);
                        else{
                            if(message.contains(endMessage)){
                                byte[] sequence = new byte[0];
                                for(int i=0; i<sequences.size(); i++){
                                    sequence = concatenate(sequence, sequences.get(i));
                                }

                                if (sequence.length != 0 && mMessageListener != null) {
                                    //call the method messageReceived from MyActivity class
                                    mMessageListener.messageReceived(sequence);
                                }
                                sequences = new ArrayList<byte[]>();
                            }
                            else if(read>5) ManageMessage(message);
                        }
                    }
                    catch(Exception e) {}

                    connected++;
                    if(debug) Log.d("mylogs", "qui6");
                }

            }
            catch (Exception e) { Log.e("TCP", "S: Error", e); }
            //finally {socket.close(); }
        }
        catch (Exception e) { Log.e("TCP", "C: Error", e); Log.d("mylogs", e.getMessage());}
        if(connected>1) {
            Log.d("mylogs", "Connection lost");
            }
        toDo_atConnectionEnds();
        //try{ MyJobService.update_gps.cancel();MyJobService.update_gps.purge();} catch(Exception e){}
    }

    static private void toDo_atConnectionStart(){
        //TimerTask task = new MyJobService.updateGPSTask();
        String timeStamp = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss").format(Calendar.getInstance().getTime());
        Log.d("mylogs", "Connected with "+TcpClient.SERVER_IP+" at port "+
                TcpClient.SERVER_PORT+".. time: "+ timeStamp);
        //MyJobService.update_gps.schedule( task, 1000, 10*1000);
        SettingsActivity.sendSettings(false, 5);
    }
    static private void toDo_atConnectionEnds(){
        try{ MyJobService.iterationStepBack(false); } catch(Exception e){}
        prev = null;
    }

    static boolean CheckPing(){
        Calendar cal = Calendar.getInstance();
        now = cal.getTime();
        if(prev == null) prev = now;
        else{
            long diff = (now.getTime() - prev.getTime());
            if(diff > timeoutFinal) return false;
        }
        return true;
    }

    static Date prev;
    static Date now;
    private void ManageMessage(String message){
        try{
            //Log.d("mylogs", "framerate - " + TcpClient.serverFramerate);
            //Log.d("mylogs", "resolution - " + TcpClient.serverResolution);
            Log.d("mylogs", "Message ("+message.length()+" bytes) received: " + message);
            if(message.contains("Resolution: ")){
                MainActivity.mediaInfo = true;
                String[] splitted = message.split(": ");
                serverResolution = Integer.parseInt(splitted[1].substring(0,1));
            }
            if(message.contains("Framerate: ")){
                MainActivity.mediaInfo = true;
                String[] splitted = message.split(": ");
                serverFramerate = Integer.parseInt(splitted[1].substring(0,1));
            }
            if(message.contains("Settings:")){
                SettingsActivity.Interpret(message);
            }
            else if(message.contains("Save: ")){
                try{
                    String[] splitted = message.split(": ");
                    MainActivity.recording = Integer.parseInt(splitted[1]);
                    MainActivity.InitializeCountRemaining(true);
                    //MainActivity.uploadRecs();
                }
                catch(Exception e) {}
            }
            else if(message.contains("Calling!")){
                String[] splitted = message.split("~_~");
                MainActivity.alarmDate = splitted[1];
                if(MainActivity.allowPlay) MainActivity.calling = true;
                for(int i=0; i<5; i++) messagesToSend.add("__PingCalling__");
            }
            else if(message.contains("__Ping__")){
                if(SettingsActivity.sendAtPing) {
                    SettingsActivity.sendAtPing = false;
                    SettingsActivity.sendSettings(false, 3);
                }
                prev = Calendar.getInstance().getTime();
                //messagesToSend.add("__Ping__");
                MyJobService.iterationUpdateIP = 0;
            }
        }
        catch(Exception e){}
    }

    //Declare the interface. The method messageReceived(String message) will must be implemented in the Activity
    //class at on AsyncTask doInBackground
    public interface OnMessageReceived {
        public void messageReceived(byte[] message);
    }

    public static String BinaryToString(byte[] bytes, int initialIndex, int finalIndex){
        String bts = "";
        bytes = Arrays.copyOfRange(bytes, initialIndex, finalIndex);
        try {
            for (byte bt : bytes) {
                if (bt == 0) bts += "0";
                else bts += "1";
            }
            return bts;
        } catch(Exception e){Log.d("test", e.getMessage()); return "";}
    }

    public static <T> T concatenate(T a, T b) {
        if (!a.getClass().isArray() || !b.getClass().isArray()) {
            throw new IllegalArgumentException();
        }

        Class<?> resCompType;
        Class<?> aCompType = a.getClass().getComponentType();
        Class<?> bCompType = b.getClass().getComponentType();

        if (aCompType.isAssignableFrom(bCompType)) {
            resCompType = aCompType;
        } else if (bCompType.isAssignableFrom(aCompType)) {
            resCompType = bCompType;
        } else {
            throw new IllegalArgumentException();
        }

        int aLen = Array.getLength(a);
        int bLen = Array.getLength(b);

        @SuppressWarnings("unchecked")
        T result = (T) Array.newInstance(resCompType, aLen + bLen);
        System.arraycopy(a, 0, result, 0, aLen);
        System.arraycopy(b, 0, result, aLen, bLen);

        return result;
    }
}
