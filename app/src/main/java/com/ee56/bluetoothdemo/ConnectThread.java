package com.ee56.bluetoothdemo;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.io.IOException;
import java.io.OutputStream;
import java.util.UUID;

/**
 * 主动发起蓝牙连接配对请求的线程
 */
public class ConnectThread extends Thread {
    //通用UUID
    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");


    private BluetoothDevice mBluetoothDevice;  //蓝牙设备
    //在创建线程的时候我们会传入布尔变量activeConnect做标识，区分是自动连接还是被动连接。
    // 当是自动连接状态，则调用BluetoothSocket的connect()进行连接。
    // 而被动连接的做法主要是开启一个监听线程，监听是否有设备连接到我们的设备上，
    private BluetoothSocket socket;
    private Handler mHandler;
    private ReadThread readThread;
    private OutputStream outputStream;

    public ConnectThread(BluetoothDevice device,Handler handler) {
        this.mBluetoothDevice = device;
        this.mHandler = handler;
    }

    @Override
    public void run() {
        super.run();
        // TODO 自动生成的方法存根
        try {
            //获取BluetoothSocket实例并连接服务器，该处的uuid需与服务器短
            //的uuid一致才能连接成功,connect()是会阻塞当前线程的，直到连接成功。
            //如果连接失败，会报IO异常
            socket = mBluetoothDevice
                    .createRfcommSocketToServiceRecord(SPP_UUID);
            socket.connect();
            Log.i("bluetooth", "连接蓝牙设备成功");
            Message msg = new Message();
            msg.what = 1;
            msg.obj = mBluetoothDevice.getAddress();
            mHandler.sendMessage(msg);
            readThread = new ReadThread(socket,mHandler);
            readThread.start();
        } catch (IOException e) {
            // TODO 自动生成的 catch 块
            e.printStackTrace();
            //说明连接远程设备失败,关闭socket连接
            exit();
            mHandler.sendEmptyMessage(-2);
        }
    }

    public void write(String str){
        //发送数据：
        if (socket.isConnected()) {
            try {
                Log.i("bluetooth","主动方发送数据:"+str);
                //获取socket的OutputStream并写入数据
                outputStream = socket.getOutputStream();
                outputStream.write(str.getBytes());
            } catch (IOException e) {
                // TODO 自动生成的 catch 块
                e.printStackTrace();
            }
        }
    }

    public void exit(){
        if(readThread!=null) {
            readThread.exit();
        }
        if(outputStream!=null){
            try {
                outputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
                Log.i("bluetooth","outStream关闭出错");
            }
        }
    }
}
