
package ch.bfh.evoting.instacirclelib.wifi;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.StreamCorruptedException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.util.Base64;
import android.util.Log;
import ch.bfh.evoting.instacirclelib.service.NetworkService;

/**
 * Handle enabling and disabling of WiFi AP
 * 
 * @author http://stackoverflow.com/a/7049074/1233435
 */
public class WifiAPManager {

	private static final String TAG = WifiAPManager.class.getName();
	private static final String PREFS_NAME = "network_preferences";

	private SharedPreferences preferences;
	private SharedPreferences.Editor editor;

	private static int constant = 0;

	private static final int WIFI_AP_STATE_UNKNOWN = -1;
	private static int WIFI_AP_STATE_DISABLING = 0;
	private static int WIFI_AP_STATE_DISABLED = 1;
	public int WIFI_AP_STATE_ENABLING = 2;
	public int WIFI_AP_STATE_ENABLED = 3;
	private static int WIFI_AP_STATE_FAILED = 4;

	private final String[] WIFI_STATE_TEXTSTATE = new String[] { "DISABLING",
			"DISABLED", "ENABLING", "ENABLED", "FAILED" };

	private WifiManager wifi;

	private int stateWifiWasIn = -1;

	private boolean alwaysEnableWifi = true; // set to false if you want to try
												// and set wifi state back to
												// what it was before wifi ap
												// enabling, true will result in
												// the wifi always being enabled
												// after wifi ap is disabled

	private WifiConfiguration config;

	/**
	 * Toggle the WiFi AP state
	 * 
	 * @param wifi
	 *            the android wifi manager which should be used
	 * @param context
	 *            the android context from which this functionality is used
	 */
	public void toggleWiFiAP(WifiManager wifi, Context context) {
		if (this.wifi == null) {
			this.wifi = wifi;
		}

		boolean wifiApIsOn = getWifiAPState() == WIFI_AP_STATE_ENABLED
				|| getWifiAPState() == WIFI_AP_STATE_ENABLING;
		new SetWifiAPTask(!wifiApIsOn, context).execute();
	}

	/**
	 * Enables the tethering hotspot using a given configuration
	 * 
	 * @param wifi
	 *            the android wifi manager which should be used
	 * @param config
	 *            the wifi AP configuration which should be applied
	 * @param context
	 *            the android context from which this functionality is used
	 */
	public void enableHotspot(WifiManager wifi, WifiConfiguration config,
			Context context) {

		if (this.wifi == null) {
			this.wifi = wifi;
		}
		this.config = config;

		// Backing up old configuration
		SerializableWifiConfiguration oldConfiguration = new SerializableWifiConfiguration(
				getWifiApConfiguration(wifi));

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		ObjectOutputStream oos;
		try {
			oos = new ObjectOutputStream(baos);
			oos.writeObject(oldConfiguration);
			oos.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		String serializedAPConfig = Base64.encodeToString(baos.toByteArray(),
				Base64.DEFAULT);

		preferences = context.getSharedPreferences(PREFS_NAME, 0);
		editor = preferences.edit();
		editor.putString("originalApConfig", serializedAPConfig);
		editor.putString("password", config.preSharedKey);
		editor.putString("SSID", config.SSID);
		editor.commit();

		new SetWifiAPTask(true, context).execute();
	}

	/**
	 * Disables the tethering hotspot and restores the previous configuration
	 * 
	 * @param wifi
	 *            the android wifi manager which should be used
	 * @param context
	 *            the android context from which this functionality is used
	 */
	public void disableHotspot(WifiManager wifi, Context context) {

		if (this.wifi == null) {
            this.wifi = wifi;
        }

        String strModel = android.os.Build.MODEL;

        //HTC One crashes when deactiving AP
        if (!strModel.contains("HTC One V")) {
            // restoring the original configuration
            preferences = context.getSharedPreferences(PREFS_NAME, 0);
            String serializedAPConfig = preferences.getString(
                    "originalApConfig", "");

            try {
                ObjectInputStream ois = new ObjectInputStream(
                        new ByteArrayInputStream(Base64.decode(
                                serializedAPConfig, Base64.DEFAULT)));
                SerializableWifiConfiguration oldConfiguration = (SerializableWifiConfiguration) ois
                        .readObject();
                setWifiApConfiguration(wifi,
                        oldConfiguration.getWifiConfiguration());
            } catch (StreamCorruptedException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
            new SetWifiAPTask(false, context).execute();
        }
	}

	/**
	 * This method implements the logic for actually enabling / disabling the
	 * wifi AP
	 * 
	 * @param enabled
	 *            true for enabling, false for disabling
	 * @return the wifi AP state
	 * @author http://stackoverflow.com/a/7049074/1233435
	 */
	private int setWifiApEnabled(boolean enabled) {
		Log.d(TAG, "*** setWifiApEnabled CALLED **** " + enabled);

		// remember wirelesses current state
		if (enabled && stateWifiWasIn == -1) {
			stateWifiWasIn = wifi.getWifiState();
		}

		// disable wireless
		if (enabled && wifi.getConnectionInfo() != null) {
			Log.d(TAG, "disable wifi: calling");
			wifi.setWifiEnabled(false);
			int loopMax = 10;
			while (loopMax > 0
					&& wifi.getWifiState() != WifiManager.WIFI_STATE_DISABLED) {
				Log.d(TAG, "disable wifi: waiting, pass: " + (10 - loopMax));
				try {
					Thread.sleep(500);
					loopMax--;
				} catch (Exception e) {

				}
			}
			Log.d(TAG, "disable wifi: done, pass: " + (10 - loopMax));
		}

		// enable/disable wifi ap
		int state = WIFI_AP_STATE_UNKNOWN;
		try {
			Log.d(TAG, (enabled ? "enabling" : "disabling")
					+ " wifi ap: calling");
			wifi.setWifiEnabled(false);
			Method method1 = wifi.getClass().getMethod("setWifiApEnabled",
					WifiConfiguration.class, boolean.class);
			// method1.invoke(wifi, null, enabled); // true
			method1.invoke(wifi, config, enabled); // true
			Method method2 = wifi.getClass().getMethod("getWifiApState");
			state = (Integer) method2.invoke(wifi);
			Log.d(TAG, "State: " + state);
		} catch (Exception e) {
			Log.e(TAG, ""+e.getMessage());
			// toastText += "ERROR " + e.getMessage();
		}

		// hold thread up while processing occurs
		if (!enabled) {
			int loopMax = 10;
			while (loopMax > 0
					&& (getWifiAPState() == WIFI_AP_STATE_DISABLING
							|| getWifiAPState() == WIFI_AP_STATE_ENABLED || getWifiAPState() == WIFI_AP_STATE_FAILED)) {
				Log.d(TAG, (enabled ? "enabling" : "disabling")
						+ " wifi ap: waiting, pass: " + (10 - loopMax));
				try {
					Thread.sleep(500);
					loopMax--;
				} catch (Exception e) {

				}
			}
			Log.d(TAG, (enabled ? "enabling" : "disabling")
					+ " wifi ap: done, pass: " + (10 - loopMax));

			// enable wifi if it was enabled beforehand
			// this is somewhat unreliable and app gets confused and doesn't
			// turn it back on sometimes so added toggle to always enable if you
			// desire
			if (stateWifiWasIn == WifiManager.WIFI_STATE_ENABLED
					|| stateWifiWasIn == WifiManager.WIFI_STATE_ENABLING
					|| stateWifiWasIn == WifiManager.WIFI_STATE_UNKNOWN
					|| alwaysEnableWifi) {
				Log.d(TAG, "enable wifi: calling");
				wifi.setWifiEnabled(true);
				// don't hold things up and wait for it to get enabled
			}

			stateWifiWasIn = -1;
		} else if (enabled) {
			int loopMax = 10;
			while (loopMax > 0
					&& (getWifiAPState() == WIFI_AP_STATE_ENABLING
							|| getWifiAPState() == WIFI_AP_STATE_DISABLED || getWifiAPState() == WIFI_AP_STATE_FAILED)) {
				Log.d(TAG, (enabled ? "enabling" : "disabling")
						+ " wifi ap: waiting, pass: " + (10 - loopMax));
				try {
					Thread.sleep(500);
					loopMax--;
				} catch (Exception e) {

				}
			}
			Log.d(TAG, (enabled ? "enabling" : "disabling")
					+ " wifi ap: done, pass: " + (10 - loopMax));
		}
		return state;
	}

	/**
	 * Get the wifi AP state
	 * 
	 * @return the wifi AP state
	 * @author http://stackoverflow.com/a/7049074/1233435
	 */
	private int getWifiAPState() {
		int state = WIFI_AP_STATE_UNKNOWN;
		try {
			Method method2 = wifi.getClass().getMethod("getWifiApState");
			state = (Integer) method2.invoke(wifi);
		} catch (Exception e) {

		}

		if (state >= 10) {
			// using Android 4.0+ (or maybe 3+, haven't had a 3 device to test
			// it on) so use states that are +10
			constant = 10;
		}

		// reset these in case was newer device
		WIFI_AP_STATE_DISABLING = 0 + constant;
		WIFI_AP_STATE_DISABLED = 1 + constant;
		WIFI_AP_STATE_ENABLING = 2 + constant;
		WIFI_AP_STATE_ENABLED = 3 + constant;
		WIFI_AP_STATE_FAILED = 4 + constant;

		Log.d(TAG, "getWifiAPState.state "
				+ (state == -1 ? "UNKNOWN" : WIFI_STATE_TEXTSTATE[state
						- constant]));
		return state;
	}

	/**
	 * Get the wifi AP state
	 * 
	 * @param wifi
	 *            the android wifi manager which should be used
	 * 
	 * @return the wifi AP state
	 */
	public int getWifiAPState(WifiManager wifi) {
		if (wifi == null) {
			this.wifi = wifi;
		}
		return getWifiAPState();
	}

	/**
	 * Get the wifi AP state
	 * 
	 * @param wifi
	 *            the android wifi manager which should be used
	 * 
	 * @return true if the wifi AP is enabled, false if it is disabled.
	 */
	public boolean isWifiAPEnabled(WifiManager wifi) {

		if (this.wifi == null) {
			this.wifi = wifi;
		}

		try {
			Method method = wifi.getClass().getMethod("isWifiApEnabled");
			return (Boolean) method.invoke(wifi);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return false;
	}

	/**
	 * the AsyncTask to enable/disable the wifi AP
	 * 
	 * @author http://stackoverflow.com/a/7049074/1233435
	 */
	class SetWifiAPTask extends AsyncTask<Void, Void, Void> {

		private boolean mode; // enable or disable wifi AP
		private ProgressDialog d;
		private Context context;

		/**
		 * enable/disable the wifi ap
		 * 
		 * @param mode
		 *            enable or disable wifi AP
		 * @param context
		 *            the android context from which this functionality is used
		 * @author http://stackoverflow.com/a/7049074/1233435
		 */
		public SetWifiAPTask(boolean mode, Context context) {
			this.context = context;
			this.mode = mode;
			if (mode) {
				d = new ProgressDialog(context);
			}
		}

		/**
		 * do before background task runs
		 * 
		 * @author http://stackoverflow.com/a/7049074/1233435
		 */
		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			if (mode) {
				d.setTitle("Turning WiFi AP " + (mode ? "on" : "off") + "...");
				d.setMessage("...please wait a moment.");
				d.show();
			}
		}

		/**
		 * the background task to run
		 * 
		 * @param params
		 * @author http://stackoverflow.com/a/7049074/1233435
		 */
		@Override
		protected Void doInBackground(Void... params) {
			setWifiApEnabled(mode);
			return null;
		}

		@Override
		protected void onPostExecute(Void result) {
			super.onPostExecute(result);
			if (mode) {
				d.dismiss();
				Intent intent = new Intent(context, NetworkService.class);
				intent.putExtra("action", "createnetwork");
				context.stopService(intent);
				context.startService(intent);
			}
		}
	}

	/**
	 * Get the wifi AP configuration using reflection because the method is
	 * currently hidden
	 * 
	 * @param wifi
	 *            the android wifi manager which should be used
	 * 
	 * @return the wifi configuration of the wifi AP
	 */
	public WifiConfiguration getWifiApConfiguration(WifiManager wifi) {
		try {
			Method method1 = wifi.getClass()
					.getMethod("getWifiApConfiguration");
			WifiConfiguration config = (WifiConfiguration) method1.invoke(wifi);
			return config;
		} catch (NoSuchMethodException e) {
			e.printStackTrace();
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		} catch (InvocationTargetException e) {
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * Set the wifi AP configuration using reflection because the method is
	 * currently hidden
	 * 
	 * @param wifi
	 *            the android wifi manager which should be used
	 * @param config
	 *            the configuration which should be applied
	 * 
	 * @return whether the operation was successful
	 */
	public boolean setWifiApConfiguration(WifiManager wifi,
			WifiConfiguration config) {
		try {
			Method method1 = wifi.getClass().getMethod(
					"setWifiApConfiguration", WifiConfiguration.class);
			return (Boolean) method1.invoke(wifi, config);
		} catch (NoSuchMethodException e) {
			e.printStackTrace();
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		} catch (InvocationTargetException e) {
			e.printStackTrace();
		}
		return false;
	}

}
