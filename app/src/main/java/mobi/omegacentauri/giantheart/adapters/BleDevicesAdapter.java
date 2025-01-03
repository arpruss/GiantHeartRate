package mobi.omegacentauri.giantheart.adapters;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;

import mobi.omegacentauri.giantheart.R;

/** Adapter for holding devices found through scanning.
 *  Created by steven on 9/5/13.
 *  Modified by olli on 3/28/2014.
 */
public class BleDevicesAdapter extends BaseAdapter {
    private final LayoutInflater inflater;

    private final ArrayList<BluetoothDevice> leDevices;
    private final HashMap<BluetoothDevice, Integer> rssiMap = new HashMap<BluetoothDevice, Integer>();
    private final HashMap<BluetoothDevice, Integer> hrMap = new HashMap<BluetoothDevice, Integer>();
    private final HashMap<BluetoothDevice, Boolean> preferredMap = new HashMap<BluetoothDevice, Boolean>();

    public BleDevicesAdapter(Context context) {
        leDevices = new ArrayList<BluetoothDevice>();
        inflater = LayoutInflater.from(context);
    }

    public void addDevice(BluetoothDevice device, int rssi, boolean preferred, int hr) {
        if (!leDevices.contains(device)) {
            leDevices.add(device);
        }
        hrMap.put(device, hr);
        rssiMap.put(device, rssi);
        preferredMap.put(device, preferred);
    }

    public BluetoothDevice getDevice(int position) {
        return leDevices.get(position);
    }

    public boolean isFromAdvertisement(int position) { return 0 != hrMap.get(leDevices.get(position)); }

    public void clear() {
        leDevices.clear();
    }

    @Override
    public void notifyDataSetChanged() {
        Collections.sort(leDevices,new Comparator<BluetoothDevice>() {
            @Override
            public int compare(BluetoothDevice a, BluetoothDevice b) {
                boolean pa = preferredMap.get(a);
                boolean pb = preferredMap.get(b);
                if (pa && ! pb)
                    return -1;
                if (!pa && pb)
                    return 1;
                String na = a.getName();
                if (na != null && na.equals("Unknown device"))
                    na = null;
                String nb = b.getName();
                if (nb != null && nb.equals("Unknown device"))
                    nb = null;
                if (na != null && nb == null)
                    return -1;
                if (na == null && nb != null)
                    return 1;
                if (na != null && nb != null ) {
                    return (na.compareTo(nb));
                }
                int delta = rssiMap.get(b) - rssiMap.get(a);
                if (delta != 0)
                    return delta;
                return a.getAddress().compareTo(b.getAddress());
            }
        });

        super.notifyDataSetChanged();


    }

    @Override
    public int getCount() {
        return leDevices.size();
    }

    @Override
    public Object getItem(int i) {
        return leDevices.get(i);
    }

    @Override
    public long getItemId(int i) {
        return i;
    }

    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {
        ViewHolder viewHolder;
        // General ListView optimization code.
        if (view == null) {
            view = inflater.inflate(R.layout.listitem_device, null);
            viewHolder = new ViewHolder();
            viewHolder.deviceAddress = (TextView) view.findViewById(R.id.device_address);
            viewHolder.deviceName = (TextView) view.findViewById(R.id.device_name);
            viewHolder.deviceRssi = (TextView) view.findViewById(R.id.device_rssi);
            viewHolder.hr = (TextView) view.findViewById(R.id.device_hr);
            view.setTag(viewHolder);
        } else {
            viewHolder = (ViewHolder) view.getTag();
        }

        BluetoothDevice device = leDevices.get(i);
        final String deviceName = device.getName();
        if (deviceName != null && deviceName.length() > 0)
            viewHolder.deviceName.setText(deviceName);
        else
            viewHolder.deviceName.setText(R.string.unknown_device);
        viewHolder.deviceAddress.setText(device.getAddress());
        viewHolder.deviceRssi.setText(""+rssiMap.get(device)+" dBm");
        viewHolder.deviceName.setTextColor(preferredMap.get(device) ? Color.BLACK : Color.GRAY);
        int hr = hrMap.get(device);
        if (hr != 0)
            viewHolder.hr.setText("   HR:"+hr);
        else
            viewHolder.hr.setText("");

        return view;
    }

    private static class ViewHolder {
        TextView hr;
        TextView deviceName;
        TextView deviceAddress;
        TextView deviceRssi;
    }
}
