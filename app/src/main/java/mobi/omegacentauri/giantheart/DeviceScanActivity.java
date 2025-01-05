/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package mobi.omegacentauri.giantheart;


import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ListActivity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Html;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import mobi.omegacentauri.giantheart.adapters.BleDevicesAdapter;
import mobi.omegacentauri.giantheart.display.HeartRateActivity;
import mobi.omegacentauri.giantheart.display.DemoSensorActivity;
import mobi.omegacentauri.giantheart.display.Options;
import mobi.omegacentauri.giantheart.sensor.BleHeartRateSensor;

/**
 * Activity for scanning and displaying available Bluetooth LE devices.
 */
public class DeviceScanActivity extends ListActivity {

    private static final int REQUEST_ENABLE_BT = 1;

    private BleDevicesAdapter leDeviceListAdapter;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner scanner;
    private SharedPreferences options;

    boolean havePermissions = false;
    private static final int[] DESIRED_SERVICES = { HeartRateAdvertisementData.MIBAND_MANUFACTURER, HeartRateAdvertisementData.HEART_RATE_SERVICE };
//    private static final int[] MIBAND_ONLY = { HeartRateAdvertisementData.MIBAND_SERVICE };

    boolean haveScanPermission() {
        if (Build.VERSION.SDK_INT >= 31) {
            return PackageManager.PERMISSION_GRANTED == checkSelfPermission("android.permission.BLUETOOTH_SCAN");
        }
        else {
            return true;
        }
    }

    boolean haveLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Build.VERSION.SDK_INT < 31) {
            return PackageManager.PERMISSION_GRANTED == checkSelfPermission("android.permission.ACCESS_FINE_LOCATION");
        }
        else {
            return true;
        }
    }

    boolean haveConnectPermission() {
        if (Build.VERSION.SDK_INT >= 31) {
            return PackageManager.PERMISSION_GRANTED == checkSelfPermission("android.permission.BLUETOOTH_CONNECT");
        }
        else {
            return true;
        }
    }

    boolean haveAllPermissions() {
        return haveConnectPermission() && haveLocationPermission() && haveScanPermission();
    }

    @Override
    public void onRequestPermissionsResult (int requestCode,
                                            String[] permissions,
                                            int[] grantResults) {
        boolean haveAll = true;
        for (int i=0; i<permissions.length; i++) {
            if (grantResults[i] == PackageManager.PERMISSION_DENIED) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                    finishAffinity();
                } else {
                    finish();
                }
            }
        }

        if (haveAllPermissions()) {
            getBluetoothAdapter();
        }
    }
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.v("heartlog", "onCreate");

        options = PreferenceManager.getDefaultSharedPreferences(this);

        boolean l = haveLocationPermission();
        boolean c = haveConnectPermission();
        boolean s = haveScanPermission();
        Log.v("heartlog", "permissions "+l+" "+c+" "+s);
        if (!l || !c || !s) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                ArrayList<String> permissions = new ArrayList<>();
                if (!l)
                    permissions.add("android.permission.ACCESS_FINE_LOCATION");
                if (!c)
                    permissions.add("android.permission.BLUETOOTH_CONNECT");
                if (!s)
                    permissions.add("android.permission.BLUETOOTH_SCAN");
                String[] pp = new String[permissions.size()];
                permissions.toArray(pp);
                for (String p : pp)
                    Log.v("heart", "requesting permissions "+p);
                requestPermissions(pp, 0);
                return;
            }
        }

        if (l && s && c) {
            String oldAddress = options.getString(Options.PREF_DEVICE_ADDRESS, "");
            if (oldAddress.length() > 0) {
                String oldService = options.getString(Options.PREF_SERVICE, "");
                if (oldService.length() > 0) {
                    boolean fromAdvertisement = options.getBoolean(Options.PREF_FROM_ADVERTISEMENT, false);
                    monitor(oldAddress, oldService, fromAdvertisement);
                }
            }
        }

        getActionBar().setTitle(R.string.title_devices);

        // Use this check to determine whether BLE is supported on the device.  Then you can
        // selectively disable BLE-related features.
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        getBluetoothAdapter();
    }

    private void getBluetoothAdapter() {
        if (!haveAllPermissions()) {
            bluetoothAdapter = null;
            return;
        }
        // Initializes a Bluetooth adapter.  For API level 18 and above, get a reference to
        // BluetoothAdapter through BluetoothManager.
        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();

        // Checks if Bluetooth is supported on the device.
        if (bluetoothAdapter == null) {
            Toast.makeText(this, R.string.error_bluetooth_not_supported, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.gatt_scan, menu);
        menu.findItem(R.id.menu_stop).setVisible(false);
        menu.findItem(R.id.menu_scan).setVisible(false);
            menu.findItem(R.id.menu_refresh).setActionView(
                    R.layout.actionbar_indeterminate_progress);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_settings:
                final Intent i = new Intent();
                i.setClass(this, Options.class);
                startActivity(i);
                break;
        }
        return true;
    }

    @SuppressLint("MissingPermission")
    @Override
    protected void onResume() {
        super.onResume();

        if (bluetoothAdapter == null)
            return;

        // Ensures Bluetooth is enabled on the device.  If Bluetooth is not currently enabled,
        // fire an intent to display a dialog asking the user to grant permission to enable it.
        if (!bluetoothAdapter.isEnabled()) {
            final Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            return;
        }

        init();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // User chose not to enable Bluetooth.
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == Activity.RESULT_CANCELED) {
                finish();
            } else {
                init();
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @SuppressLint("MissingPermission")
    @Override
    protected void onPause() {
        super.onPause();

        if (scanner != null) {
            scanner.stopScan(mLeScanCallback);
            scanner = null;
        }
    }

    static public String getAssetFile(Context context, String assetName) {
        try {
            return getStreamFile(context.getAssets().open(assetName));
        } catch (IOException e) {
            // TODO Auto-generated catch block
            return "";
        }
    }

    static private String getStreamFile(InputStream stream) {
        BufferedReader reader;
        try {
            reader = new BufferedReader(new InputStreamReader(stream));

            String text = "";
            String line;
            while (null != (line=reader.readLine()))
                text = text + line;
            return text;
        } catch (IOException e) {
            // TODO Auto-generated catch block
            return "";
        }
    }

    public void showLicenses() {
        AlertDialog alertDialog = new AlertDialog.Builder(this).create();
        alertDialog.setTitle("Licenses and copyrights");
        alertDialog.setMessage(Html.fromHtml(getAssetFile(this, "license.html")));
        alertDialog.setButton(DialogInterface.BUTTON_POSITIVE, "OK",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {} });
        alertDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            public void onCancel(DialogInterface dialog) {} });
        alertDialog.show();
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        final BluetoothDevice device = leDeviceListAdapter.getDevice(position);
        if (device == null)
            return;

        monitor(device.getAddress(),BleHeartRateSensor.getServiceUUIDString(),leDeviceListAdapter.isFromAdvertisement(position));

/*        final Intent intent = new Intent(this, DeviceServicesActivity.class);
        intent.putExtra(DeviceServicesActivity.EXTRAS_DEVICE_NAME, device.getName());
        intent.putExtra(DeviceServicesActivity.EXTRAS_DEVICE_ADDRESS, device.getAddress()); */
        //startActivity(intent);
    }

    private void monitor(String address, String service, boolean fromAdvertisement) {
        final Intent demoIntent = new Intent();
        demoIntent.setClass(this, HeartRateActivity.class);
        demoIntent.putExtra(DemoSensorActivity.EXTRAS_DEVICE_ADDRESS, address);
        demoIntent.putExtra(DemoSensorActivity.EXTRAS_SENSOR_UUID, service);
        demoIntent.putExtra(DemoSensorActivity.EXTRAS_FROM_ADVERTISEMENT, fromAdvertisement);
        startActivity(demoIntent);
    }

    static int haveService(byte[] advertisementData, int[] services) {
        for (int i = 0 ; i + 1 < advertisementData.length ; ) {
            int length = advertisementData[i] & 0xFF;
            i++;
            if (i + length > advertisementData.length)
                return 0;
            int type = advertisementData[i] & 0xFF;
            if (type == 2 || type == 3) {
                i++;
                length--;
                while (length>0) {
                    int service16 = (advertisementData[i] & 0xFF) + 256 * (advertisementData[i+1] & 0xFF);
                    for (int j = 0 ; j < services.length ; j++)
                        if (services[j] == service16)
                            return services[j];
                    length -= 2;
                    i += 2;
                }
                if (type == 3)
                    return 0;
            }
            else {
                i += length;
            }
        }
        return 0;
    }

/*    public static int getHeartRate(byte[] advertisementData) {
        if (0 == haveService(advertisementData, HeartRateAdvertisementData.MIBAND_ONLY))
            return 0;
        for (int i = 0 ; i + 1 < advertisementData.length ; ) {
            int length = advertisementData[i] & 0xFF;
            i++;
            if (i + length > advertisementData.length)
                return 0;
            int type = advertisementData[i] & 0xFF;
            if (type == 0xFF && length == 27) {
                i++;
                length--;
                int id = (advertisementData[i] & 0xFF) + 256 * (advertisementData[i+1] & 0xFF);
                i += 2;
                length -= 2;
                if (id == 0x157) {
                    int hr = 0xFF & advertisementData[i+3];
                    if (hr == 0xFF)
                        return 0;
                    else
                        return hr;
                }
                i += length;
            }
            else {
                i += length;
            }
        }
        return 0;
    }
*/

    @SuppressLint("MissingPermission")
    private void init() {
        if (leDeviceListAdapter == null) {
            leDeviceListAdapter = new BleDevicesAdapter(getBaseContext());
            setListAdapter(leDeviceListAdapter);
        }
        leDeviceListAdapter.clear();

        if (scanner == null && bluetoothAdapter != null) {
            scanner = bluetoothAdapter.getBluetoothLeScanner();
        }

        if (scanner != null) {
            ScanSettings settings = new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .build();
            List<ScanFilter> filters = new ArrayList<ScanFilter>();
            scanner.startScan(filters, settings, mLeScanCallback);
//            scanner.startScan(mLeScanCallback);
        }

        invalidateOptionsMenu();
    }

    // Device scan callback.
    private ScanCallback mLeScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            final int rssi = result.getRssi();
            final BluetoothDevice device = result.getDevice();
            List <BleAdvertisementData.BleAdvertisementItem> adv =
                    BleAdvertisementData.toList(result.getScanRecord());
            int service = HeartRateAdvertisementData.findService(adv, DESIRED_SERVICES);
            if (0 != service) {
//                adv = BleAdvertisementData.toList(new byte[] {0x0E, (byte)0xFF, 0x6b, 0x00, 0x72, 0x06, 0x7f, 0x44, 0x37, 0x00, 0x00, 0x00, 0x33, 0x00, 0x3c});
                int hr = HeartRateAdvertisementData.getHeartRate(adv);
                leDeviceListAdapter.addDevice(device, rssi, true, hr);
                leDeviceListAdapter.notifyDataSetChanged();
            }
        }
    };
}