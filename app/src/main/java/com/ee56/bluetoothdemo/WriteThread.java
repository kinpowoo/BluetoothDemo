package com.ee56.bluetoothdemo;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WriteThread {
    private static WriteThread instance;
    private ExecutorService executorService;
    private WriteThread(){
        if(instance==null){
            synchronized (WriteThread.class){
                if(instance==null){
                    executorService = Executors.newSingleThreadExecutor();
                }
            }
        }
    }


    public static WriteThread get(){
        return instance;
    }

    private void write(String str, BluetoothSocket socket){
        if (socket.isConnected()) {
            try {
                //获取socket的OutputStream并写入数据
                OutputStream out = socket.getOutputStream();
                out.write(str.getBytes());
                out.close();
            } catch (IOException e) {
                // TODO 自动生成的 catch 块
                e.printStackTrace();
            }
        }
    }

}
