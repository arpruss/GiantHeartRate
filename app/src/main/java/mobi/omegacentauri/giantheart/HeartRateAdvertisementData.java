package mobi.omegacentauri.giantheart;

import android.util.Log;

import java.util.List;

public class HeartRateAdvertisementData {
    public static final int MIBAND_MANUFACTURER = 0xFEE0;
    public static final int MIBAND_MANUFACTURER_FF_CODE = 0x0157;
    public static final int POLAR_MANUFACTURER = 0x006B;

    public static final int MY_CHEAP_BAND = 0xFF05;
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
                if (s == MIBAND_MANUFACTURER) {
                    isMiBand = true;
                }
                else if (s == HEART_RATE_SERVICE) {
                    haveHeartService = true;
                }
            }
            else if (item.type == 0x16 && item.data.length == 7 &&
                    getInt16(item.data, 0) == MIBAND_MANUFACTURER) {
                return item.data[6] & 0xFF;
            }
            else if (item.type == 0xFF && item.data.length >= 3) {
                int manufacturer = getInt16(item.data, 0);
                if (manufacturer == MY_CHEAP_BAND && item.data.length == 6) {
                    hrCheapBand = item.data[5] & 0xFF;
                }
                else if (manufacturer == MIBAND_MANUFACTURER_FF_CODE &&
                        item.data.length == 26) {
                    hrMiBand5_7 = item.data[5] & 0xFF;
                    if (hrMiBand5_7 == 0xFF)
                        hrMiBand5_7 = 0;
                }
                else if (manufacturer == POLAR_MANUFACTURER && item.data.length >= 5) {
                    // example from OH1: (0E FF) 6b 00 72 06 7f 44 37 00 00 00 33 00 3c
                    // untested
                    if (item.data.length == 5 || item.data.length == 6)
                        return item.data[item.data.length-1] & 0xFF;
                    int offset = 2;
                    while (offset < item.data.length) {
                        if (0 != (item.data[offset] & 0x40)) {
                            offset += (item.data[offset+1] & 0xFF) + 2;
                        }
                        else {
                            if (offset + 3 == item.data.length || offset + 4 == item.data.length) {
                                return item.data[item.data.length-1] & 0xFF;
                            }
                            else {
                                return 0;
                            }
                        }
                    }
                    return 0;
                }
            }
        }
        if (haveHeartService && hrCheapBand > 0)
            return hrCheapBand;
        if (isMiBand)
            return hrMiBand5_7;
        return 0;
    }
}
