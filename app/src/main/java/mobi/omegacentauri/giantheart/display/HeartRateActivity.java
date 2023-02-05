package mobi.omegacentauri.giantheart.display;

import android.app.Application;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;


import mobi.omegacentauri.giantheart.R;
import mobi.omegacentauri.giantheart.sensor.BleHeartRateSensor;
import mobi.omegacentauri.giantheart.sensor.BleSensor;

/**
 * Created by olli on 3/28/14.
 */
public class HeartRateActivity extends DemoSensorActivity {
	private final static String TAG = HeartRateActivity.class
			.getSimpleName();

	static public long lastValidTime;
	Handler timeoutHandler;
	static final long initialTimeout = 20000;
	static final long periodicTimeout = 10000;
	boolean works;

	private BigTextView bigText;
	private SharedPreferences options;
	private Runnable periodicTimeoutRunnable;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.demo_opengl);
		getActionBar().hide();
		options = PreferenceManager.getDefaultSharedPreferences(this);
		if (options.getBoolean(Options.PREF_SCREEN_ON, true))
			getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		else
			getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		bigText = (BigTextView) findViewById(R.id.heartrate);
		bigText.setText(" ? ");
		works = false;
		timeoutHandler = new Handler();
		periodicTimeoutRunnable = new Runnable() {
			@Override
			public void run() {
				bigText.setText(" ? ");
				timeoutHandler.postDelayed(periodicTimeoutRunnable, periodicTimeout);
			}
		};
	}

	void updateCache(boolean state) {
		String oldAddress = options.getString(Options.PREF_DEVICE_ADDRESS, "");
		if (!state) {
			if (oldAddress.length() != 0)
				options.edit().putString(Options.PREF_DEVICE_ADDRESS, "").apply();
		}
		else {
			String oldService = options.getString(Options.PREF_SERVICE, "");
			if (oldAddress.equals(deviceAddress) && oldService.equals(serviceUuid))
				return;
			SharedPreferences.Editor ed = options.edit();
			ed.putString(Options.PREF_DEVICE_ADDRESS, deviceAddress);
			ed.putString(Options.PREF_SERVICE, serviceUuid);
			ed.apply();
		}
	}

	public boolean isTV() {
		if (Build.MODEL.startsWith("AFT")) {
			Application app = getApplication();
			String installerName = null;
			if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.ECLAIR) {
				installerName = app.getPackageManager().getInstallerPackageName(app.getPackageName());
			}
			if (installerName != null && installerName.equals("com.amazon.venezia"))
				return true;
		}

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ECLAIR) {
			return getPackageManager().hasSystemFeature("android.hardware.type.television");
		}
		else {
			return false;
		}
	}

	void setOrientation() {
		if (isTV())
			return;
		String o = options.getString(Options.PREF_ORIENTATION, "automatic");
		try {
			if (o.equals("landscape"))
				setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
			else if (o.equals("portrait"))
				setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
			else {
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
					setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);
				}
				else {
					setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
				}
			}
		} catch(Exception e) {
			try {
				setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
			}
			catch(Exception e2) {}
		}
	}

	protected void setFullScreen() {
		boolean fs = options.getBoolean(Options.PREF_FULLSCREEN, true);
		Window w = getWindow();
		WindowManager.LayoutParams attrs = w.getAttributes();

		if (fs) {
			attrs.flags |= WindowManager.LayoutParams.FLAG_FULLSCREEN;
		} else {
			attrs.flags &= ~WindowManager.LayoutParams.FLAG_FULLSCREEN;
		}

		w.setAttributes(attrs);

		View dv = w.getDecorView();

		Log.v("hrshow", "fs test");
		if (Build.VERSION.SDK_INT > 11 && Build.VERSION.SDK_INT < 19) {
			dv.setSystemUiVisibility(fs ? View.GONE : View.VISIBLE);
		} else if (Build.VERSION.SDK_INT >= 19) {
			int flags = dv.getSystemUiVisibility();
			if (fs) {
				Log.v("hrshow", "hide");
				flags |= View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
			}
			else {
				flags &= ~(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
			}
			dv.setSystemUiVisibility(flags);
		}
	}

		@Override
	public void onDataReceived(BleSensor<?> sensor, String text) {
		if (sensor instanceof BleHeartRateSensor) {
			final BleHeartRateSensor heartSensor = (BleHeartRateSensor) sensor;
			int hr = (int)heartSensor.getData()[0];
			bigText.setText(""+hr);
			lastValidTime = System.currentTimeMillis();
			updateCache(true);
			timeoutHandler.removeCallbacksAndMessages(null);
			timeoutHandler.postDelayed(periodicTimeoutRunnable, periodicTimeout);
		}
	}

	@Override
	public void onBackPressed() {
		updateCache(false);
		super.onBackPressed();
	}

	@Override
	public void onPause() {
		super.onPause();

		timeoutHandler.removeCallbacksAndMessages(null);
	}

	@Override
	public void onResume() {
		super.onResume();

		setOrientation();
		setFullScreen();

		timeoutHandler.removeCallbacksAndMessages(null);
		timeoutHandler.postDelayed(new Runnable() {
			@Override
			public void run() {
				Toast.makeText(HeartRateActivity.this, "Cannot connect to heart rate", Toast.LENGTH_LONG).show();
				updateCache(false);
				finish();
			}
		}, initialTimeout);
	}
}