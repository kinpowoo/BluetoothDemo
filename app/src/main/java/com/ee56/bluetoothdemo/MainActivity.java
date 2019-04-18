package com.ee56.bluetoothdemo;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import com.ee56.bluetoothdemo.adapter.DeviceItemAdapter;
import com.ee56.bluetoothdemo.adapter.MessageAdapter;
import com.ee56.bluetoothdemo.custom_ui.NoScrollListView;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class MainActivity extends AppCompatActivity {
    private static final int PERMISSION_REQUEST_COARSE_LOCATION = 0x21;
    //第一条是蓝牙串口通用的UUID，不要更改
    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final UUID MY_UUID_SECURE = UUID.fromString("fa87c0d0-afac-11de-8a39-0800200c9a66");
    private static final UUID MY_UUID_INSECURE = UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66");

    @BindView(R.id.enable_bluetooth)
    Button enableBluetooth;
    @BindView(R.id.search_devices)
    Button searchDeviceBtn;
    @BindView(R.id.connect_status)
    TextView connectStatus;
    @BindView(R.id.current_connected_device)
    TextView currentConnectedDevice;

    @BindView(R.id.msg_input)
    EditText msgEdit;
    @BindView(R.id.send_btn)
    Button sendBtn;
    @BindView(R.id.receive_msg_list)
    NoScrollListView receiveMsgList;

    MessageAdapter msgAdapter;
    List<String> receiveMsgs = new ArrayList<>();

    PopupWindow searchWindow;
    NoScrollListView pairedList;
    NoScrollListView searchResultList;

    DeviceItemAdapter pairedListAdapter;
    DeviceItemAdapter searchResultListAdapter;

    List<BluetoothDevice> pairedDevices = new ArrayList<>();    //已连接设备
    List<BluetoothDevice> searchResultDevices = new ArrayList<>();  //非连接设备

    ProgressDialog alertDialog;   //配对时的dialog
    private AlertDialog.Builder builder;  //取消配对时的dialog

    private BluetoothAdapter btAdapter;   //蓝牙适配器
    private BluetoothDevice chatTo;  //通信的目标设备
    private ConnectThread connectThread;  //蓝牙主动配对线程
    private ListenThread listenThread;   //监听线程
    private int connectSource = -1;   //当前连接的身份，是主动连接方，还是被动连接方

    private Handler handler = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what){
                case -2:
                    //连接远程蓝牙设备失败，即配对失败
                    Toast.makeText(MainActivity.this,"配对失败!",
                            Toast.LENGTH_SHORT).show();
                    if(alertDialog!=null){
                        alertDialog.dismiss();
                    }
                    break;
                case 1:   //主动配对连接成功
                    Toast.makeText(MainActivity.this,"配对成功!",
                            Toast.LENGTH_SHORT).show();
                    if(alertDialog!=null){
                        alertDialog.dismiss();
                    }
                    if(searchWindow!=null&&searchWindow.isShowing()){
                        searchWindow.dismiss();
                    }
                    String macAddress= (String) msg.obj;
                    chatTo = btAdapter.getRemoteDevice(macAddress);
                    currentConnectedDevice.setText(chatTo.getName()+":"+chatTo.getAddress());
                    connectStatus.setText("已连接到一台设备");
                    connectSource = 1;   //连接方，主动发起请求
                    break;
                case 2:   //收到其它设备发送的消息
                    String received = (String) msg.obj;
                    receiveMsgs.add(received);
                    msgAdapter.notifyDataSetChanged();
                    break;
                case 3:   //监听,被动连接成功
                    chatTo = (BluetoothDevice) msg.obj;
                    currentConnectedDevice.setText(chatTo.getName()+":"+chatTo.getAddress());
                    connectStatus.setText("已连接到一台设备");
                    connectSource = 2;   //被连接方，被连接
                    break;
                case 4:   //读取线程中的socket断开连接，有可能是主动方断开连接，有可能是被动方断开连接
                    Toast.makeText(MainActivity.this,"当前连接已断开!",
                            Toast.LENGTH_SHORT).show();
                    chatTo=null;
                    currentConnectedDevice.setText("");
                    connectStatus.setText("");
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Setup the window
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        //获取默认蓝牙适配器
        btAdapter = BluetoothAdapter.getDefaultAdapter();

        // Register for broadcasts when a device is discovered
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND); //找到设备
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);   //开始搜索
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);  //搜索结束
        filter.addAction(BluetoothDevice.ACTION_PAIRING_REQUEST);      //pin码配对请求
        // 通过广播可以监听到输入PIN码的那个页面将要弹出
        registerReceiver(mReceiver,filter);

        msgAdapter = new MessageAdapter(this,receiveMsgs);
        receiveMsgList.setAdapter(msgAdapter);

        //开启监听
        startListen();
    }

    //在onCreate方法中启动监听方法，等待别的设备来连接自己
    private void startListen(){
        if(btAdapter!=null){
            try {
                // name无关紧要，UUID要与主动连接方的UUID一致
                BluetoothServerSocket serverSocket = btAdapter.listenUsingRfcommWithServiceRecord(
                        "Server",SPP_UUID);
                if(listenThread==null){
                    listenThread = new ListenThread(serverSocket,handler);
                }
                listenThread.start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }



    @OnClick({R.id.enable_bluetooth, R.id.disable_bluetooth, R.id.enable_bluetooth_discoverable,
            R.id.search_devices,R.id.interrupt_connect,R.id.send_btn})
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.enable_bluetooth:
                if(btAdapter==null){
                    Toast.makeText(this,"当前设备不支持蓝牙功能",Toast.LENGTH_SHORT).show();
                    return;
                }
                if (btAdapter.isEnabled()) {
                    Toast.makeText(this, "蓝牙已启用", Toast.LENGTH_SHORT).show();
                    return;
                }
                //安卓6.0后需要用这种显示请求来打开蓝牙，通过 btAdapter.enable() 静默打开无效
                Intent turnOn = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(turnOn, 0);
                break;
            case R.id.disable_bluetooth:
                if (!btAdapter.isEnabled()) {
                    Toast.makeText(this, "蓝牙已关闭", Toast.LENGTH_SHORT).show();
                    return;
                }
                if(btAdapter.isDiscovering()){
                    btAdapter.cancelDiscovery();
                }
                btAdapter.disable();
                Toast.makeText(this, "蓝牙关闭", Toast.LENGTH_SHORT).show();
                break;
            case R.id.enable_bluetooth_discoverable:
                if (btAdapter.getScanMode() == BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
                    Toast.makeText(this, "蓝牙Already可见", Toast.LENGTH_SHORT).show();
                    return;
                }
                Intent turnOnDiscover = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
                turnOnDiscover.putExtra(BluetoothAdapter.EXTRA_SCAN_MODE,BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE);
                turnOnDiscover.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 3000);
                startActivityForResult(turnOnDiscover, 1);
                break;
            case R.id.search_devices:
                if (!btAdapter.isEnabled()) {
                    Toast.makeText(this, "蓝牙未打开", Toast.LENGTH_SHORT).show();
                    return;
                }
                //安卓6.0后蓝牙扫描需要用到定位权限
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    // Android M Permission check
                    if (this.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) !=
                            PackageManager.PERMISSION_GRANTED) {
                        requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                                PERMISSION_REQUEST_COARSE_LOCATION);
                        return;
                    }
                }

                if (searchWindow == null) {
                    View content = LayoutInflater.from(this).inflate(R.layout.search_result_layout, null);
                    searchWindow = new PopupWindow(LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.MATCH_PARENT);
                    searchWindow.setContentView(content);
                    searchWindow.setTouchable(true);
                    searchWindow.setFocusable(true);
                    searchWindow.setOutsideTouchable(false);
                    pairedList = content.findViewById(R.id.paired_list);
                    searchResultList = content.findViewById(R.id.search_result_list);

                    pairedListAdapter = new DeviceItemAdapter(this, pairedDevices);
                    pairedList.setAdapter(pairedListAdapter);
                    pairedList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                        @Override
                        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                            //已配对的设备点击后，将其设为通信的目标设备
                            if(alertDialog==null){
                                alertDialog = new ProgressDialog(MainActivity.this);
                                alertDialog.setCancelable(false);
                            }
                            alertDialog.setTitle("正在连接...");
                            alertDialog.show();

                            if(btAdapter.isDiscovering()){
                                btAdapter.cancelDiscovery();
                            }

                            BluetoothDevice device = pairedDevices.get(position);
                            if (BluetoothAdapter.checkBluetoothAddress(device.getAddress())) {
                                //验证蓝牙地址是否有效
                                connectDevice(device);
                            }
                        }
                    });

                    pairedList.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
                        @Override
                        public boolean onItemLongClick(AdapterView<?> parent, View view,final int position, long id) {
                            builder = new AlertDialog.Builder(MainActivity.this).setIcon(R.mipmap.ic_launcher).setTitle("最普通dialog")
                                    .setMessage("是否取消配对当前设备").setPositiveButton("确定",
                                            new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialogInterface, int i) {
                                            //ToDo: 你想做的事情
                                            unpairDevice(pairedDevices.get(position));
                                        }
                                    }).setNegativeButton("取消", new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialogInterface, int i) {
                                            //ToDo: 你想做的事情
                                            dialogInterface.dismiss();
                                        }
                                    });
                            builder.create().show();
                            return true;
                        }
                    });

                    searchResultListAdapter = new DeviceItemAdapter(this, searchResultDevices);
                    searchResultList.setAdapter(searchResultListAdapter);
                    searchResultList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                        @Override
                        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                            //搜索结果列表中的设备点击进行配对请求
                            //点击配对时，需要暂停搜索,因为搜索是一个长耗时，高负载操作，会严重降低匹配速度
                            btAdapter.cancelDiscovery();

                            if(alertDialog==null){
                                alertDialog = new ProgressDialog(MainActivity.this);
                                alertDialog.setCancelable(false);
                            }
                            alertDialog.setTitle("正在配对...");
                            alertDialog.show();

                            BluetoothDevice device = searchResultDevices.get(position);
                            if (BluetoothAdapter.checkBluetoothAddress(device.getAddress())) {
                                //验证蓝牙地址是否有效
                                connectDevice(device);
                            }
                        }
                    });

                    Button stopSearch = content.findViewById(R.id.stop_searching);
                    stopSearch.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            if(btAdapter.isDiscovering()){
                                btAdapter.cancelDiscovery();
                            }
                            searchWindow.dismiss();
                        }
                    });
                }
                searchWindow.showAtLocation(searchDeviceBtn, Gravity.CENTER, 0, 0);

                //已配对设备
                Set<BluetoothDevice> alreadyPaired = btAdapter.getBondedDevices();
                pairedDevices.clear();
                pairedDevices.addAll(alreadyPaired);
                pairedListAdapter.notifyDataSetChanged();


                // If we're already discovering, stop it
                if (btAdapter.isDiscovering()) {
                    btAdapter.cancelDiscovery();
                }
                //开始搜索设备
                btAdapter.startDiscovery();
                searchResultDevices.clear();
                break;
            case R.id.interrupt_connect:   //断开当前连接的设备
                if(chatTo==null || connectSource == -1){
                    Toast.makeText(MainActivity.this,"没有已连接设备",
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                if(connectSource==1){
                    connectThread.exit();
                }else if(connectSource ==2){
                    listenThread.closeCurrentSocket();
                }
                break;
            case R.id.send_btn:   //发送
                if(chatTo==null || connectSource == -1){
                    Toast.makeText(MainActivity.this,"没有已连接设备",
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                String str = msgEdit.getText().toString();
                if(str.length()<=0){
                    Toast.makeText(MainActivity.this,"传输内容不能为空",
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                if(connectSource==1){
                    connectThread.write(str);
                }else if(connectSource ==2){
                    listenThread.write(str);
                }
                msgEdit.setText("");
                msgEdit.clearFocus();
                break;
        }
    }



    /**
     * 主动连接蓝牙设备
     */
    private void connectDevice(BluetoothDevice device) {
        //启动连接线程
        connectThread = new ConnectThread(device,handler);
        connectThread.start();
    }


    //反射来调用BluetoothDevice.removeBond取消设备的配对
    private void unpairDevice(BluetoothDevice device) {
        try {
            Method m = device.getClass().getMethod("removeBond", (Class[]) null);
            m.invoke(device, (Object[]) null);
            Toast.makeText(MainActivity.this,"取消配对成功",
                    Toast.LENGTH_SHORT).show();

            pairedDevices.remove(device);
            pairedListAdapter.notifyDataSetChanged();
        } catch (Exception e) {
            Log.e("mate", e.getMessage());
            Toast.makeText(MainActivity.this,"取消配对失败",
                    Toast.LENGTH_SHORT).show();
        }
    }


    //蓝牙相关事件广播接收
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            //发现新设备
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                //避免重复添加已经绑定过的设备
                if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
                    //此处的adapter是列表的adapter，不是BluetoothAdapter
                    searchResultDevices.add(device);
                    searchResultListAdapter.notifyDataSetChanged();
                    Log.i("bleutooth","发现了蓝牙设备-"+device.getName()+":"+device.getAddress());
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(action)) {
                Toast.makeText(MainActivity.this,"开始搜索",
                        Toast.LENGTH_SHORT).show();
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                Toast.makeText(MainActivity.this,"搜索完毕",
                        Toast.LENGTH_SHORT).show();
                if (searchResultListAdapter.getCount() == 0) {
                    Toast.makeText(MainActivity.this,"没有搜索到设备",Toast.LENGTH_SHORT).show();
                }
            }else if(BluetoothDevice.ACTION_PAIRING_REQUEST.equals(action)){
                if(alertDialog!=null){
                    alertDialog.dismiss();
                }
                try {
                    //(三星)4.3版本测试手机还是会弹出用户交互页面(闪一下)，如果不注释掉下面这句页面不会取消但可以配对成功。(中兴，魅族4(Flyme 6))5.1版本手机两中情况下都正常
                    //ClsUtils.setPairingConfirmation(mBluetoothDevice.getClass(), mBluetoothDevice, true);
                    //abortBroadcast();//如果没有将广播终止，则会出现一个一闪而过的配对框。
                    //3.调用setPin方法进行配对...
                    //boolean ret = ClsUtils.setPin(mBluetoothDevice.getClass(), mBluetoothDevice,
                     //       "你需要设置的PIN码");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }else if(BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)){
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                switch (device.getBondState()) {
                    case BluetoothDevice.BOND_BONDING:
                        Toast.makeText(MainActivity.this,"正在配对......",Toast.LENGTH_SHORT).show();
                        break;
                    case BluetoothDevice.BOND_BONDED:
                        Toast.makeText(MainActivity.this,"配对成功",Toast.LENGTH_SHORT).show();
                        pairedDevices.add(device);
                        pairedListAdapter.notifyDataSetChanged();
                        break;
                    case BluetoothDevice.BOND_NONE:
                        Toast.makeText(MainActivity.this,"取消配对完成",Toast.LENGTH_SHORT).show();
                        currentConnectedDevice.setText("");
                        connectStatus.setText("");
                    default:
                        break;
                }
            }
        }
    };


    //蓝牙打开，和启用可见性回调
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode==0 && resultCode == RESULT_OK){
            Toast.makeText(this, "蓝牙打开完成", Toast.LENGTH_SHORT).show();
        }
        if(requestCode==1 && resultCode == RESULT_OK){
            Toast.makeText(this, "蓝牙已可见", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        // Make sure we're not doing discovery anymore
        if (btAdapter != null) {
            btAdapter.cancelDiscovery();
        }
        unregisterReceiver(mReceiver);

        //关闭蓝牙服务器：
        listenThread.exit();  //退出监听线程
        if(connectThread!=null) {
            connectThread.exit();  //退出连接线程
        }
        super.onDestroy();
    }



    //========================= 请求权限相关 =============================

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        boolean hasPermissionDenied = false;//有权限没有通过
        boolean hasNeverAskAgain = false;    //用户点击了不再询问
        if (requestCode == PERMISSION_REQUEST_COARSE_LOCATION) {
            for (int i = 0; i < grantResults.length; i++) {
                if (grantResults[i] == -1) {
                    hasPermissionDenied = true;
                }
                if(judgePermission(this,permissions[i])){
                    hasNeverAskAgain = true;
                }
            }

            if(hasNeverAskAgain){
                showPermissionDialog();//跳转到系统设置权限页面，或者直接关闭页面，不让他继续访问
                return;
            }

            //如果有权限没有被允许
            if (hasPermissionDenied) {
                Toast.makeText(this,"请打开定位权限，否则无法扫描",
                        Toast.LENGTH_SHORT).show();
            }else{
                //全部权限通过，可以进行下一步操作。。。
                Toast.makeText(this,"定位权限已打开，现在可以开始扫描",
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * 不再提示权限时的展示对话框
     */
    AlertDialog mPermissionDialog;
    String mPackName = "com.ee56.bluetoothdemo";

    private void showPermissionDialog() {
        if (mPermissionDialog == null) {
            mPermissionDialog = new AlertDialog.Builder(this)
                    .setMessage("已禁用定位权限，请手动授予")
                    .setPositiveButton("设置", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            cancelPermissionDialog();

                            Uri packageURI = Uri.parse("package:" + mPackName);
                            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageURI);
                            startActivity(intent);
                            finish();
                        }
                    })
                    .setNegativeButton("取消", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            //关闭页面或者做其他操作
                            cancelPermissionDialog();
                            System.exit(0);
                        }
                    })
                    .create();
        }
        mPermissionDialog.setCanceledOnTouchOutside(false);
        mPermissionDialog.show();
    }

    //关闭对话框
    private void cancelPermissionDialog() {
        mPermissionDialog.cancel();
    }

    /**
     * 判断是否已拒绝过权限
     *
     * @return
     * @describe :如果应用之前请求过此权限但用户拒绝，此方法将返回 true;
     * -----------如果应用第一次请求权限或 用户在过去拒绝了权限请求，
     * -----------并在权限请求系统对话框中选择了 Don't ask again 选项，此方法将返回 false。
     */
    public boolean judgePermission(Context context, String permission) {
        if (ActivityCompat.shouldShowRequestPermissionRationale((Activity) context, permission))
            return true;
        else
            return false;
    }
}
