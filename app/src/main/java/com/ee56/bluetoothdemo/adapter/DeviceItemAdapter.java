package com.ee56.bluetoothdemo.adapter;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.ee56.bluetoothdemo.R;
import com.ee56.bluetoothdemo.bean.DeviceInfo;

import java.util.List;

public class DeviceItemAdapter extends BaseAdapter {
    List<BluetoothDevice> devices;
    Context context;
    LayoutInflater inflater;

    public DeviceItemAdapter(Context c,List<BluetoothDevice> device){
        this.devices = device;
        this.context = c;
        this.inflater = LayoutInflater.from(c);
    }


    @Override
    public int getCount() {
        return devices==null?0:devices.size();
    }

    @Override
    public Object getItem(int position) {
        return devices.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if(convertView==null){
            convertView = inflater.inflate(R.layout.device_item_layout,parent,false);
        }
        TextView deviceName = convertView.findViewById(R.id.device_name);
        TextView isPaired = convertView.findViewById(R.id.is_paired);
        TextView macAddress = convertView.findViewById(R.id.mac_address);
        BluetoothDevice deviceInfo = devices.get(position);

        deviceName.setText(deviceInfo.getName());
        isPaired.setText(deviceInfo.getBondState()==BluetoothDevice.BOND_BONDED?"已配对":"未配对");
        macAddress.setText(deviceInfo.getAddress());
        return convertView;
    }
}
