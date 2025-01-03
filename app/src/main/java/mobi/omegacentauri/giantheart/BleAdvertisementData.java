package mobi.omegacentauri.giantheart;

import android.bluetooth.le.ScanRecord;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

public class BleAdvertisementData implements Iterable<BleAdvertisementData.BleAdvertisementItem> {
    byte[] data;
    static final byte[] empty = new byte[0];
    int position;

    public BleAdvertisementData(byte[] _data) {
        data = _data;
        position = 0;
    }

    public static List<BleAdvertisementItem> toList(byte[] data) {
        BleAdvertisementData d = new BleAdvertisementData(data);
        List <BleAdvertisementItem> l = new ArrayList<BleAdvertisementItem>();
        for (BleAdvertisementItem item : d) {
            l.add(item);
        }
        return l;

    }

    public static List<BleAdvertisementItem> toList(ScanRecord scanRecord) {
        return toList(scanRecord.getBytes());
    }

    @Override
    public Iterator<BleAdvertisementItem> iterator() {
        return new Iterator<BleAdvertisementItem>() {

            @Override
            public boolean hasNext() {
                return position+1 < data.length && data[position] != 0; // && (data[position] & 0xFF) + position + 1 < data.length;
            }

            @Override
            public BleAdvertisementItem next() {
                int length = data[position] & 0xFF;
                position++;
                int type = data[position] & 0xFF;
                length--;
                position++;
                if (position+length>data.length)
                    length = data.length - position;
                BleAdvertisementItem item = new BleAdvertisementItem(type,
                        Arrays.copyOfRange(data, position, position + length));
                position += length;
                return item;
            }
        };
    }

    static public class BleAdvertisementItem {
        int type;
        byte[] data;
        public BleAdvertisementItem(int _type, byte[] _data) {
                type = _type;
                data = _data;
        }
    }
}
