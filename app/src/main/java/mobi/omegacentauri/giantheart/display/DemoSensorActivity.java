package mobi.omegacentauri.giantheart.display;

import mobi.omegacentauri.giantheart.BleAdvertisementData;
import mobi.omegacentauri.giantheart.BleService;
import mobi.omegacentauri.giantheart.DeviceScanActivity;
import mobi.omegacentauri.giantheart.HeartRateAdvertisementData;
import mobi.omegacentauri.giantheart.sensor.BleSensor;
import mobi.omegacentauri.giantheart.sensor.BleSensors;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.MenuItem;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;


/**
 * Created by steven on 9/5/13.
 * Modified by olli on 3/28/2014.
 */
public abstract class DemoSensorActivity extends Activity {
    public static final String EXTRAS_FROM_ADVERTISEMENT = "FROM_ADVERTISEMENT";
    private final static String TAG = DemoSensorActivity.class.getSimpleName();

    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";
    public static final String EXTRAS_SENSOR_UUID = "SERVICE_UUID";

    private BleService bleService;
    protected String serviceUuid;
    protected String deviceAddress;

    // Handles various events fired by the Service.
    // ACTION_GATT_CONNECTED: connected to a GATT server.
    // ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
    // ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
    // ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read
    //                        or notification operations.
    private final BroadcastReceiver gattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BleService.ACTION_GATT_DISCONNECTED.equals(action)) {
                Log.e("hrshow", "disconnected");
                bleService.connect(deviceAddress);
            } else if (BleService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                final BleSensor<?> sensor = BleSensors.getSensor(serviceUuid);
                bleService.enableSensor(sensor, true);
            } else if (BleService.ACTION_DATA_AVAILABLE.equals(action)) {
                final BleSensor<?> sensor = BleSensors.getSensor(serviceUuid);
                onDataReceived(sensor);
            }
        }
    };

    // Code to manage Service lifecycle.
    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            bleService = ((BleService.LocalBinder) service).getService();
            if (!bleService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            // Automatically connects to the device upon successful start-up initialization.
            bleService.connect(deviceAddress);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            Log.e("hrshow", "onServiceDisconnected");
            Toast.makeText(DemoSensorActivity.this,"Service disconnected", Toast.LENGTH_LONG).show();
            //bleService.connect(deviceAddress);
            bleService = null;
            finish();
        }
    };
    protected boolean fromAdvertisement;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner scanner;
    protected SharedPreferences options;

    public abstract void onDataReceived(BleSensor<?> sensor);
    public abstract void onDataReceived(int hr);
    private ScanCallback mLeScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            final int rssi = result.getRssi();
            final BluetoothDevice device = result.getDevice();
            if (device.getAddress().equals(deviceAddress)) {
                int hr = HeartRateAdvertisementData.getHeartRate(
                        BleAdvertisementData.toList(result.getScanRecord()));
                onDataReceived(hr);
            }
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        options = PreferenceManager.getDefaultSharedPreferences(this);
        final Intent intent = getIntent();
        deviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);
        serviceUuid = intent.getStringExtra(EXTRAS_SENSOR_UUID);
        fromAdvertisement = options.getBoolean(Options.PREF_USE_ADVERTISED, true) && intent.getBooleanExtra(EXTRAS_FROM_ADVERTISEMENT, false);
        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        if (fromAdvertisement) {
            bluetoothAdapter = bluetoothManager.getAdapter();
            scanner = bluetoothAdapter.getBluetoothLeScanner();
        }
        else {
            scanner = null;
            final Intent gattServiceIntent = new Intent(this, BleService.class);
            bindService(gattServiceIntent, serviceConnection, BIND_AUTO_CREATE);
        }

        //getActionBar().setDisplayHomeAsUpEnabled(true);
    }

    @SuppressLint("MissingPermission")
    @Override
    protected void onResume() {
        super.onResume();

        if (fromAdvertisement) {
            ScanSettings settings = new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                            .build();
            List<ScanFilter> filters = new ArrayList<ScanFilter>();
            filters.add(new ScanFilter.Builder().setDeviceAddress(deviceAddress).build() );
            scanner.startScan(filters, settings, mLeScanCallback);
        }
        else {
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(gattUpdateReceiver, makeGattUpdateIntentFilter(), RECEIVER_NOT_EXPORTED);
            }
            else {
                registerReceiver(gattUpdateReceiver, makeGattUpdateIntentFilter());
            }
            if (bleService != null) {
                final boolean result = bleService.connect(deviceAddress);
                Log.v("hrshow", "Connect request result=" + result);
            } else {
                Log.e("hrShow", "null bleService");
            }
        }
    }

    @SuppressLint("MissingPermission")
    @Override
    protected void onPause() {
        super.onPause();
        if (fromAdvertisement) {
            scanner.stopScan(mLeScanCallback);
        }
        else {
            unregisterReceiver(gattUpdateReceiver);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (! fromAdvertisement) {
            unbindService(serviceConnection);
            bleService = null;
        }
    }

    /*
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }
     */

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BleService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BleService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BleService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }
}
