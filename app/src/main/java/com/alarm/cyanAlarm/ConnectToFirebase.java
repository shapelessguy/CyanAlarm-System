package com.alarm.cyanAlarm;

import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.storage.FileDownloadTask;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.List;

public class ConnectToFirebase {
    private StorageReference mStorageRef;
    public ConnectToFirebase(){
        mStorageRef = FirebaseStorage.getInstance().getReference();
        DownloadFile();
    }
    public void DownloadFile(){

        final long bytes = 15;
        StorageReference pathRef = mStorageRef.child("public/IP.txt");
        pathRef.getBytes(bytes).addOnSuccessListener(new OnSuccessListener<byte[]>() {
            @Override
            public void onSuccess(byte[] bytes) {
                String message = null;
                try {
                    message = new String(bytes, "UTF-8");
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
                message = message.replace("\n","");
                TcpClient.SERVER_IP = message;
                Log.d("mylogs", "IPAddress: --"+message+"--");
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception exception) {
                // Handle any errors
            }
        });
    }
}
