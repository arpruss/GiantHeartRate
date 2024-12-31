package mobi.omegacentauri.giantheart;

import java.util.Arrays;
import java.util.Iterator;

public class BleAdvertisementData implements Iterable<BleAdvertisementData.BleAdvertisementItem> {
    byte[] data;
    int position;

    public BleAdvertisementData(byte[] _data) {
        data = _data;
        position = 0;
    }

    @Override
    public Iterator<BleAdvertisementItem> iterator() {
        return new Iterator<BleAdvertisementItem>() {

            @Override
            public boolean hasNext() {
                return position < data.length && (data[position] & 0xFF) + position + 1 < data.length;
            }

            @Override
            public BleAdvertisementItem next() {
                if (position >= data.length)
                    return null;
                int length = data[position]  & 0xFF;
                position++;
                if (position + length > data.length)
                    return null;
                BleAdvertisementItem item = new BleAdvertisementItem(data[position] & 0xFF,
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
