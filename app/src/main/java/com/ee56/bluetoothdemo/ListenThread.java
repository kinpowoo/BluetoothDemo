package com.ee56.bluetoothdemo;

import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.io.IOException;
import java.io.OutputStream;

/**
 * 被动等待连接的线程，监听其它设备发起连接请求
 */
public class ListenThread extends Thread {
    private BluetoothServerSocket mServerSocket;
    private ReadThread readThread;
    private Handler mHandle;
    private BluetoothSocket socket;
    private boolean isExit = false;
    private OutputStream out;

    public ListenThread(BluetoothServerSocket mServerSocket, Handler handler) {
        this.mServerSocket = mServerSocket;
        this.mHandle = handler;
    }

    @Override
    public void run() {
        super.run();
        try {
            //该方法是服务器阻塞等待客户端的连接，
            //监听到有客户端连接返回一个BluetoothSocket的实例
            while (!isExit) {
                Log.i("Server", "开始等待设备连接");
                socket = mServerSocket.accept();
                Log.i("Server", "有新的设备连接成功");
                Message msg = new Message();
                msg.what = 3;
                msg.obj = socket.getRemoteDevice();
                mHandle.sendMessage(msg);
                //开启读取线程读取客户端发来的数据
                readThread = new ReadThread(socket,mHandle);
                readThread.start();
            }
        } catch (IOException e) {
            // TODO 自动生成的 catch 块
            try {
                socket.close();
            } catch (IOException e1) {
                e1.printStackTrace();
            }finally {
                socket = null;
            }
        }
    }

    //发送数据：
    public void write(String str) {
        if (socket!=null&&socket.isConnected()) {
            try {
                Log.i("bluetooth","被连接方发送数据:"+str);
                //获取socket的OutputStream并写入数据
                out = socket.getOutputStream();
                out.write(str.getBytes());
            } catch (IOException e) {
                // TODO 自动生成的 catch 块
                e.printStackTrace();
            }
        }
    }

    //关闭当前连接
    public void closeCurrentSocket(){
        readThread.exit();
        if(out!=null){
            try {
                out.close();
            } catch (IOException e) {
                e.printStackTrace();
                Log.i("bluetooth","outStream关闭出错");
            }
        }
    }

    //完全退出关闭监听线程，连带关闭读线程，关闭输出流
    public void exit() {
        isExit = true;
        readThread.exit();
        if(out!=null){
            try {
                out.close();
            } catch (IOException e) {
                e.printStackTrace();
                Log.i("bluetooth","outStream关闭出错");
            }
        }
        try {
            //关闭BluetoothServerSocket:
            mServerSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
            Log.i("bluetooth","serverSocket关闭出错");
        }finally {
            mServerSocket = null;
        }
    }

}
