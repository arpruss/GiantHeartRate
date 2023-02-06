package mobi.omegacentauri.giantheart.display;

import android.app.Application;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.Toast;


import mobi.omegacentauri.giantheart.DeviceScanActivity;
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
	Handler buttonHideHandler;
	static final long initialTimeout = 30000;
	static final long periodicTimeout = 10000;
	boolean works;

	private BigTextView bigText;
	private SharedPreferences options;
	private Runnable periodicTimeoutRunnable;
	private ImageButton settingsButton;
	private Runnable buttonHideRunnable;
	private static final long buttonHideTime = 8000;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.heart);
		getActionBar().hide();
		options = PreferenceManager.getDefaultSharedPreferences(this);
		if (options.getBoolean(Options.PREF_SCREEN_ON, true))
			getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		else
			getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		bigText = (BigTextView) findViewById(R.id.heartrate);
		works = false;
		timeoutHandler = new Handler();
		periodicTimeoutRunnable = new Runnable() {
			@Override
			public void run() {
				bigText.setText(" ? ");
				timeoutHandler.postDelayed(periodicTimeoutRunnable, periodicTimeout);
			}
		};
		settingsButton = (ImageButton) findViewById(R.id.settings);
		buttonHideHandler = new Handler();
		showButtons();
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

	void showButtons() {
		settingsButton.setVisibility(View.VISIBLE);
		if (!isTV()) {
			if (buttonHideRunnable == null)
				buttonHideRunnable = new Runnable() {
					@Override
					public void run() {
						settingsButton.setVisibility(View.GONE);
						buttonHideHandler.postDelayed(periodicTimeoutRunnable, buttonHideTime);
					}
				};
			buttonHideHandler.removeCallbacksAndMessages(null);
			buttonHideHandler.postDelayed(buttonHideRunnable, buttonHideTime);
		}
	}

	@Override
	public boolean dispatchTouchEvent(MotionEvent me) {
		showButtons();
		return super.dispatchTouchEvent(me);
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

		if (Build.VERSION.SDK_INT > 11 && Build.VERSION.SDK_INT < 19) {
			dv.setSystemUiVisibility(fs ? View.GONE : View.VISIBLE);
		} else if (Build.VERSION.SDK_INT >= 19) {
			int flags = dv.getSystemUiVisibility();
			if (fs) {
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
			if (hr > 0) {
				Log.v("hrshow", "hr=" + hr);
				bigText.setText("" + hr);
				lastValidTime = System.currentTimeMillis();
				updateCache(true);
				timeoutHandler.removeCallbacksAndMessages(null);
				timeoutHandler.postDelayed(periodicTimeoutRunnable, periodicTimeout);
			}
			else {
				bigText.setText(" ? ");
			}
		}
	}

	@Override
	public void onBackPressed() {
		updateCache(false);
		Log.v("hrshow", "onBackPressed");
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
		Log.v("hrshow", "onResume");

		setOrientation();
		setFullScreen();
		showButtons();

		timeoutHandler.removeCallbacksAndMessages(null);
		timeoutHandler.postDelayed(new Runnable() {
			@Override
			public void run() {
				Toast.makeText(HeartRateActivity.this, "Cannot connect to heart rate", Toast.LENGTH_LONG).show();
				updateCache(false);
				Log.e("hrshow", "cannot connect");
				finish();
			}
		}, initialTimeout);
		bigText.setText(" ? ");
	}

	public void onSettingsClick(View view) {
		updateCache(false);
		final Intent i = new Intent();
		i.setClass(this, DeviceScanActivity.class);
		startActivity(i);
	}
}
