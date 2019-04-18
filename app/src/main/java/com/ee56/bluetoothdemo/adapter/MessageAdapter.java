package com.ee56.bluetoothdemo.adapter;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.ee56.bluetoothdemo.R;

import java.util.List;

public class MessageAdapter extends BaseAdapter {
    List<String> msgList;
    Context context;
    LayoutInflater inflater;

    public MessageAdapter(Context c, List<String> msgList){
        this.msgList = msgList;
        this.context = c;
        this.inflater = LayoutInflater.from(c);
    }


    @Override
    public int getCount() {
        return msgList==null?0:msgList.size();
    }

    @Override
    public Object getItem(int position) {
        return msgList.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if(convertView==null){
            convertView = inflater.inflate(R.layout.receive_msg_item,parent,false);
        }
        TextView content = convertView.findViewById(R.id.content);
        String str = msgList.get(position);
        content.setText(str);
        return convertView;
    }
}
