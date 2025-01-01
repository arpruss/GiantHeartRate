package mobi.omegacentauri.giantheart;

import android.util.Log;

import java.util.List;

public class HeartRateAdvertisementData {
    public static final int MIBAND_SERVICE = 0xFEE0;
    public static final int HEART_RATE_SERVICE = 0x180D;
    static int getInt16(byte[] data, int offset) {
        return ( (data[offset+1] & 0xFF) << 8 ) | ( data[offset] & 0xFF );
    }

    static int findService(List<BleAdvertisementData.BleAdvertisementItem> adv, int[] services) {
        for (BleAdvertisementData.BleAdvertisementItem item : adv) {
            if ((item.type == 2 || item.type == 3) && item.data.length >= 2) {
                int service16 = getInt16(item.data, 0);
                for (int s : services) {
                    if (service16 == s)
                        return service16;
                }
            }
        }
        return 0;
    }

    public static int getHeartRate(List<BleAdvertisementData.BleAdvertisementItem> adv) {
        int hrMiBand5_7 = 0;
        boolean haveHeartService = false;
        boolean isMiBand = false;
        int hrCheapBand = 0;

        for (BleAdvertisementData.BleAdvertisementItem item : adv) {
            if ((item.type == 2 || item.type == 3) && item.data.length >= 2) {
                int s = getInt16(item.data, 0);
                if (s == MIBAND_SERVICE) {
                    isMiBand = true;
                }
                else if (s == HEART_RATE_SERVICE) {
                    haveHeartService = true;
                }
            }
            else if (item.type == 0x16 && item.data.length == 7 &&
                    getInt16(item.data, 0) == MIBAND_SERVICE) {
                    Log.v("giantheartrate", "MiBand3 "+(0xFF&item.data[6]));
                return item.data[6] & 0xFF;
            }
            else if (item.type == 0xFF && item.data.length == 6 &&
                    getInt16(item.data, 0) == 0xFF05) {
                hrCheapBand = item.data[5] & 0xFF;
            }
            else if (item.type == 0xFF && item.data.length == 26 &&
                getInt16(item.data, 0) == 0x0157) {
                hrMiBand5_7 = item.data[5] & 0xFF;
                if (hrMiBand5_7 == 0xFF)
                    hrMiBand5_7 = 0;
            }
        }
        if (haveHeartService && hrCheapBand > 0)
            return hrCheapBand;
        if (isMiBand)
            return hrMiBand5_7;
        return 0;
    }
}
