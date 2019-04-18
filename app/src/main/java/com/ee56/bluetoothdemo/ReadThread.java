package com.ee56.bluetoothdemo;

import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;

/**
 * 读取数据的线程，无论是主动连接方还是被动连接方，都需要开启
 */
public class ReadThread extends Thread {
    private boolean exit = false;
    private BluetoothSocket socket;
    private Handler mHandle;
    private InputStream reader;

    byte[] buff = new byte[1024];

    public ReadThread(BluetoothSocket socket, Handler handler) throws IOException {
        this.socket = socket;
        this.mHandle = handler;
        reader = socket.getInputStream();
    }

    @Override
    public void run() {
        super.run();
        // TODO 自动生成的方法存根
        try {
            while (socket.isConnected() && !exit) {
                //获取socket的InputStream并不断读取数据
                int len = -1;
                while ((len=reader.read(buff))!=-1){
                    if (len > 0) {
                        String str = new String(buff,0,len);
                        Log.i("bluetooth","socket接收到数据"+str);
                        Message msg = mHandle.obtainMessage();
                        msg.what = 2;
                        msg.obj = str;
                        msg.sendToTarget();
                    }
                }
            }
            reader.close();
        } catch (IOException e) {
            // TODO 自动生成的 catch 块
            Log.i("bluetooth","socket已断开连接!");
            close();
            mHandle.sendEmptyMessage(4);
        }
    }

    private void close(){
        if(socket!=null){
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }finally {
                socket = null;
            }
        }
    }


    public void exit() {
        exit = true;
        close();
    }
}