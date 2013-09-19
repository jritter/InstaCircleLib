
package ch.bfh.instacircle.wifi;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.DhcpInfo;
import android.net.NetworkInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.util.Log;
import android.view.WindowManager.BadTokenException;
import ch.bfh.instacircle.service.NetworkService;

/**
 * This class implements methods which are used to adjust the wifi configuration
 * of the device
 * 
 * @author Juerg Ritter (rittj1@bfh.ch)
 */
public class AdhocWifiManager {

	private static final String TAG = AdhocWifiManager.class.getSimpleName();
	private static final String PREFS_NAME = "network_preferences";

	private WifiManager wifi;
	private SharedPreferences preferences;
	private SharedPreferences.Editor editor;

	/**
	 * Instatiates a new instance
	 * 
	 * @param wifi
	 *            the android wifi manager which should be used
	 * 
	 */
	public AdhocWifiManager(WifiManager wifi) {
		this.wifi = wifi;
	}

	/**
	 * Connects to a network by applying a WifiConfiguration
	 * 
	 * @param config
	 *            the WifiConfiguration which will be applied
	 * @param context
	 *            the android context from which this functionality is used
	 */
	public void connectToNetwork(WifiConfiguration config, Context context) {
		new ConnectWifiTask(config, context).execute();
	}

	/**
	 * Connects to a network using an SSID and a password
	 * 
	 * @param ssid
	 *            the ssid as string (without double-quotes) to which will be
	 *            connected
	 * @param password
	 *            the network key
	 * @param context
	 *            the android context from which this functionality is used
	 */
	public void connectToNetwork(final String ssid, final String password,
			final Context context) {
		connectToNetwork(ssid, password, context, true);
	}

	/**
	 * Connects to a network using an SSID and a password
	 * 
	 * @param ssid
	 *            the ssid as string (without double-quotes) to which will be
	 *            connected
	 * @param password
	 *            the network key
	 * @param context
	 *            the android context from which this functionality is used
	 * @param startActivity
	 *            defines whether the NetworkActiveActivity should be started
	 *            after connecting
	 */
	public void connectToNetwork(final String ssid, final String password,
			final Context context, final boolean startActivity) {
		Log.d(TAG, "connect using SSID...");
		new ConnectWifiTask(ssid, password, context, startActivity).execute();
	}

	/**
	 * Connects to a network using a predefined networkId.
	 * 
	 * @param networkId
	 *            the id of the predefined (or known) network configuration on
	 *            the device
	 * @param context
	 *            the android context from which this functionality is used
	 */
	public void connectToNetwork(final int networkId, final Context context) {
		Log.d(TAG, "Connect using netid... " + networkId);
		new ConnectWifiTask(networkId, context).execute();
	}

	/**
	 * Restores the previously configured network configuration
	 * 
	 * @param context
	 *            the android context from which this functionality is used
	 */
	public void restoreWifiConfiguration(Context context) {
		new RestoreWifiConfigurationTask(context).execute();
	}

	/**
	 * AsyncTask which encapsulates the pretty time consuming logic to connect
	 * to wifi networks into an independetly running task. During execution, a
	 * progress dialog will be displayed on the screen.
	 * 
	 * @author Juerg Ritter (rittj1@bfh.ch)
	 */
	class ConnectWifiTask extends AsyncTask<Void, Void, Void> {

		private Context context;
		private ProgressDialog d;
		private WifiConfiguration config;
		private boolean success;
		private String password;
		private String ssid;
		private int networkId = -1;

		private boolean startActivity = true;

		/**
		 * Initializing the task by defining an ssid and a password
		 * 
		 * @param ssid
		 *            the SSID to which should be connected
		 * @param password
		 *            the password for the network which is used to connect
		 *            securely to a network
		 * @param context
		 *            the android context from which this functionality is used
		 * @param startActivity
		 *            defines whether the NetworkActiveActivity should be
		 *            started after connecting
		 */
		public ConnectWifiTask(String ssid, String password, Context context,
				boolean startActivity) {

			this.ssid = ssid;
			this.password = password;
			this.context = context;
			this.startActivity = startActivity;

			config = new WifiConfiguration();
			d = new ProgressDialog(context);
		}

		/**
		 * Initializing the task by specifying a WifiConfiguration
		 * 
		 * @param config
		 *            the WifiConfiguration which will be applied
		 * @param context
		 *            the android context from which this functionality is used
		 */
		public ConnectWifiTask(WifiConfiguration config, Context context) {

			this.config = config;
			this.context = context;
			this.password = config.preSharedKey;
			this.ssid = config.SSID;

			d = new ProgressDialog(context);
		}

		/**
		 * Initializing the task by specifying a WifiConfiguration
		 * 
		 * @param networkId
		 *            the id of the predefined (or known) network configuration
		 *            on the device
		 * @param context
		 *            the android context from which this functionality is used
		 */
		public ConnectWifiTask(int networkId, Context context) {
			this.networkId = networkId;
			this.context = context;

			// iterate over all the known network configurations in the device
			// and get the configuration with the corresponding networkId
			for (WifiConfiguration config : wifi.getConfiguredNetworks()) {
				if (config.networkId == networkId) {
					this.config = config;
					break;
				}
			}
			this.ssid = config.SSID;

			d = new ProgressDialog(context);
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see android.os.AsyncTask#onPreExecute()
		 */
		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			d.setTitle("Connecting to Network " + ssid + "...");
			d.setMessage("...please wait a moment.");
			d.show();
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see android.os.AsyncTask#doInBackground(Params[])
		 */
		@Override
		protected Void doInBackground(Void... params) {

			// make sure that wifi on the device is enabled
			wifi.setWifiEnabled(true);

			// extract the current networkId and store it in the preferences
			// file
			preferences = context.getSharedPreferences(PREFS_NAME, 0);
			editor = preferences.edit();
			editor.putInt("originalNetId", wifi.getConnectionInfo()
					.getNetworkId());
			editor.commit();

			// handle the strange habit with the double quotes...
			config.SSID = "\"" + config.SSID + "\"";

			if (networkId != -1) {
				// Configuration already exists, no need to create a new one...
				success = wifi.enableNetwork(networkId, true);
			} else {

				// make sure that we have scan results before we continue
				if (wifi.getScanResults() == null) {
					wifi.startScan();

					// wait until we get scanresults
					int maxLoops = 10;
					int i = 0;
					while (i < maxLoops) {
						if (wifi.getScanResults() != null) {
							break;
						}
						try {
							Thread.sleep(2000);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
						i++;
					}
					if (wifi.getScanResults() == null) {
						success = false;
						return null;
					}
				}

				// Configuration has to be created and added
				config.allowedAuthAlgorithms.clear();
				config.allowedGroupCiphers.clear();
				config.allowedKeyManagement.clear();
				config.allowedPairwiseCiphers.clear();
				config.allowedProtocols.clear();

				// iterate over the scanresults, extract their properties and
				// create a new WifiConfiguration from these parameters
				List<ScanResult> results = wifi.getScanResults();
				for (ScanResult result : results) {
					if (result.SSID.equals(ssid)) {
						config.hiddenSSID = false;
						config.priority = 10000;
						config.SSID = "\"".concat(result.SSID).concat("\"");

						// handling the different types of security
						if (result.capabilities.contains("WPA")) {
							config.preSharedKey = "\"".concat(password).concat(
									"\"");
							config.allowedGroupCiphers
									.set(WifiConfiguration.GroupCipher.TKIP);
							config.allowedGroupCiphers
									.set(WifiConfiguration.GroupCipher.CCMP);
							config.allowedKeyManagement
									.set(WifiConfiguration.KeyMgmt.WPA_PSK);
							config.allowedPairwiseCiphers
									.set(WifiConfiguration.PairwiseCipher.TKIP);
							config.allowedPairwiseCiphers
									.set(WifiConfiguration.PairwiseCipher.CCMP);
							config.allowedProtocols
									.set(WifiConfiguration.Protocol.RSN);
							config.allowedProtocols
									.set(WifiConfiguration.Protocol.WPA);
						} else if (result.capabilities.contains("WEP")) {
							config.wepKeys[0] = "\"" + password + "\"";
							config.wepTxKeyIndex = 0;
							config.allowedKeyManagement
									.set(WifiConfiguration.KeyMgmt.NONE);
							config.allowedGroupCiphers
									.set(WifiConfiguration.GroupCipher.WEP40);
						} else {
							config.allowedKeyManagement
									.set(WifiConfiguration.KeyMgmt.NONE);
						}
						config.status = WifiConfiguration.Status.ENABLED;
						break;
					}
				}

				// add the network to the known network configuration and enable
				// it
				networkId = wifi.addNetwork(config);
				success = wifi.enableNetwork(networkId, true);
			}

			// wait until the connection is actually established
			ConnectivityManager conn = (ConnectivityManager) context
					.getSystemService(Context.CONNECTIVITY_SERVICE);
			NetworkInfo nInfo = null;

			int i = 0;
			int maxLoops = 10;
			while (i < maxLoops) {
				nInfo = conn.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
				Log.d(TAG, nInfo.getDetailedState().toString() + "  "
						+ nInfo.getState().toString());
				if (nInfo.getDetailedState() == NetworkInfo.DetailedState.CONNECTED
						&& nInfo.getState() == NetworkInfo.State.CONNECTED
						&& getBroadcastAddress() != null) {
					Log.d(TAG, "Connected!");
					break;
				}
				try {
					Thread.sleep(3000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				i++;
			}

			// check whether we have been successful
			if (!(nInfo.getDetailedState() == NetworkInfo.DetailedState.CONNECTED
					&& nInfo.getState() == NetworkInfo.State.CONNECTED && getBroadcastAddress() != null)) {
				success = false;
			} else {
				// start the network service if we were successful
				if (startActivity) {
					success = true;
				}
			}
			return null;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see android.os.AsyncTask#onPostExecute(java.lang.Object)
		 */
		@Override
		protected void onPostExecute(Void result) {
			super.onPostExecute(result);
			d.dismiss();
			if (startActivity) {
				if (success) {
					// start the service if we were successful

					// Intent intent = new Intent(context,
					// NetworkActiveActivity.class);
					// intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
					// intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
					// context.startActivity(intent);

					Intent intent = new Intent(context, NetworkService.class);
					context.stopService(intent);
					context.startService(intent);

				} else {

					// display a dialog if the connection was not successful
					AlertDialog.Builder builder = new AlertDialog.Builder(
							context);
					builder.setTitle("InstaCircle - Connect failed");
					builder.setMessage("The attempt to connect to the network failed.");
					builder.setPositiveButton("OK",
							new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog,
										int which) {
									dialog.dismiss();
									return;
								}
							});
					AlertDialog dialog = builder.create();
					try{
						dialog.show();
					} catch (BadTokenException e){
						//other activity was already started
					}
				}
			}
		}
	}

	/**
	 * AsyncTask which restores the restoration of the original network
	 * configuration
	 * 
	 * @author Juerg Ritter (rittj1@bfh.ch)
	 */
	class RestoreWifiConfigurationTask extends AsyncTask<Void, Void, Void> {

		private Context context;

		/**
		 * Initializing the task
		 * 
		 * @param context
		 *            the android context from which this functionality is used
		 */
		public RestoreWifiConfigurationTask(Context context) {
			this.context = context;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see android.os.AsyncTask#onPreExecute()
		 */
		@Override
		protected void onPreExecute() {
			super.onPreExecute();
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see android.os.AsyncTask#doInBackground(Params[])
		 */
		@Override
		protected Void doInBackground(Void... params) {
			preferences = context.getSharedPreferences(PREFS_NAME, 0);
			wifi.enableNetwork(preferences.getInt("originalNetId", 0), true);

			return null;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see android.os.AsyncTask#onPostExecute(java.lang.Object)
		 */
		@Override
		protected void onPostExecute(Void result) {
			super.onPostExecute(result);
		}
	}

	/**
	 * A helper method to find out the broadcast address of the current network
	 * configuration
	 * 
	 * @return the broadcast address
	 */
	public InetAddress getBroadcastAddress() {
		DhcpInfo myDhcpInfo = wifi.getDhcpInfo();
		int broadcast = (myDhcpInfo.ipAddress & myDhcpInfo.netmask)
				| ~myDhcpInfo.netmask;
		byte[] quads = new byte[4];
		for (int k = 0; k < 4; k++) {
			quads[k] = (byte) ((broadcast >> k * 8) & 0xFF);
		}
		try {
			return InetAddress.getByAddress(quads);
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * A helper method to find out the IP address of the current network
	 * configuration
	 * 
	 * @return the IP address
	 */
	public InetAddress getIpAddress() {
		DhcpInfo myDhcpInfo = wifi.getDhcpInfo();
		int ipaddress = myDhcpInfo.ipAddress;
		byte[] quads = new byte[4];
		for (int k = 0; k < 4; k++) {
			quads[k] = (byte) ((ipaddress >> k * 8) & 0xFF);
		}
		try {
			return InetAddress.getByAddress(quads);
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
		return null;
	}
}
