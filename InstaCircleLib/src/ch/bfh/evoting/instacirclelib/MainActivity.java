
package ch.bfh.evoting.instacirclelib;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.app.AlertDialog;
import android.app.DialogFragment;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentFilter.MalformedMimeTypeException;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.nfc.NdefMessage;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.os.Parcelable;
import android.provider.ContactsContract;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;
import ch.bfh.evoting.instacirclelib.db.NetworkDbHelper;
import ch.bfh.evoting.instacirclelib.wifi.AdhocWifiManager;

/**
 * Activity which is displayed when launching the application
 * 
 * @author Juerg Ritter (rittj1@bfh.ch)
 * 
 */
public class MainActivity extends Activity implements OnItemClickListener,
		ConnectNetworkDialogFragment.NoticeDialogListener, TextWatcher {

	private static final String TAG = MainActivity.class.getSimpleName();
	private static final String PREFS_NAME = "network_preferences";

	private NetworkArrayAdapter adapter;
	private AdhocWifiManager adhoc;

	private ArrayList<HashMap<String, Object>> arraylist = new ArrayList<HashMap<String, Object>>();
	private HashMap<String, Object> lastItem = new HashMap<String, Object>();

	private ListView lv;

	private SharedPreferences preferences;
	private List<ScanResult> results;
	private List<WifiConfiguration> configuredNetworks;

	private ScanResult selectedResult;

	private EditText txtIdentification;

	private WifiManager wifi;

	private BroadcastReceiver wifibroadcastreceiver;
	private NfcAdapter nfcAdapter;
	private PendingIntent pendingIntent;
	private IntentFilter nfcIntentFilter;
	private IntentFilter[] intentFiltersArray;
	private boolean nfcAvailable;
	private Parcelable[] rawMsgs;
	private int selectedNetId;

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.text.TextWatcher#afterTextChanged(android.text.Editable)
	 */
	public void afterTextChanged(Editable s) {

		// saving the identification field immediately after changing it
		SharedPreferences.Editor editor = preferences.edit();
		editor.putString("identification", txtIdentification.getText()
				.toString());
		editor.commit();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.text.TextWatcher#beforeTextChanged(java.lang.CharSequence,
	 * int, int, int)
	 */
	public void beforeTextChanged(CharSequence s, int start, int count,
			int after) {

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Activity#onCreate(android.os.Bundle)
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		/*
		Added by Phil: comments. no more needed 
		// redirect immediately to the NetworkActiveActivity if the
		// NetworkService is already running
		if (isServiceRunning()) {
			Log.d(TAG, "going straight to the network Active Activity...");
			Intent intent = new Intent(this, NetworkActiveActivity.class);
			intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
			intent.addFlags(Intent.FLAG_ACTIVITY_TASK_ON_HOME);
			intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
			startActivity(intent);
		}*/

		// applying the layout
		setContentView(R.layout.activity_main);

		// reading the identification from the preferences, if it is not there
		// it will try to read the name of the device owner
		preferences = getSharedPreferences(PREFS_NAME, 0);
		String identification = preferences.getString("identification",
				readOwnerName());

		lv = (ListView) findViewById(R.id.network_listview);

		txtIdentification = (EditText) findViewById(R.id.identification_edittext);
		txtIdentification.setText(identification);

		txtIdentification.addTextChangedListener(this);

		// Handling the WiFi
		wifi = (WifiManager) getSystemService(Context.WIFI_SERVICE);
		adhoc = new AdhocWifiManager(wifi);

		lastItem.put("SSID", "Create new network...");

		adapter = new NetworkArrayAdapter(this, R.layout.list_item_network,
				arraylist);
		adapter.add(lastItem);
		lv.setAdapter(adapter);
		lv.setOnItemClickListener(this);

		// defining what happens as soon as scan results arrive
		wifibroadcastreceiver = new BroadcastReceiver() {

			@Override
			public void onReceive(Context c, Intent intent) {
				results = wifi.getScanResults();
				configuredNetworks = wifi.getConfiguredNetworks();
				arraylist.clear();

				for (ScanResult result : results) {
					HashMap<String, Object> item = new HashMap<String, Object>();

					item.put("known", false);

					// check whether the network is already known, i.e. the
					// password is already stored in the device
					for (WifiConfiguration configuredNetwork : configuredNetworks) {
						if (configuredNetwork.SSID.equals("\"".concat(
								result.SSID).concat("\""))) {
							item.put("known", true);
							item.put("netid", configuredNetwork.networkId);
							break;
						}
					}

					if (result.capabilities.contains("WPA")
							|| result.capabilities.contains("WEP")) {
						item.put("secure", true);
					} else {
						item.put("secure", false);
					}
					item.put("SSID", result.SSID);
					item.put("capabilities", result.capabilities);
					item.put("object", result);
					arraylist.add(item);
					Log.d(TAG, result.SSID + " known: " + item.get("known")
							+ " netid " + item.get("netid"));
				}
				arraylist.add(lastItem);
				adapter.notifyDataSetChanged();

			}
		};

		// register the receiver, subscribing for the SCAN_RESULTS_AVAILABLE
		// action
		registerReceiver(wifibroadcastreceiver, new IntentFilter(
				WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));

		// Handling the NFC part, but only if the device provides this feature
		nfcAvailable = this.getPackageManager().hasSystemFeature(
				PackageManager.FEATURE_NFC);

		if (nfcAvailable) {
			nfcAdapter = NfcAdapter.getDefaultAdapter(this);

			rawMsgs = null;
			rawMsgs = getIntent().getParcelableArrayExtra(
					NfcAdapter.EXTRA_NDEF_MESSAGES);

			if (rawMsgs != null && !isServiceRunning()) {
				processNfcTag();
			}

			// setting up a pending intent which is invoked when a tag is tapped
			// on the back of the device
			if (nfcAdapter.isEnabled()) {

				Intent intent = new Intent(this, getClass());
				intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
				pendingIntent = PendingIntent.getActivity(this, 0, intent, 0);

				nfcIntentFilter = new IntentFilter(
						NfcAdapter.ACTION_NDEF_DISCOVERED);
				try {
					nfcIntentFilter
							.addDataType("application/ch.bfh.instacircle");
				} catch (MalformedMimeTypeException e) {
					throw new RuntimeException("fail", e);
				}
				intentFiltersArray = new IntentFilter[] { nfcIntentFilter };
			} else {
				nfcAvailable = false;
			}

		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Activity#onCreateOptionsMenu(android.view.Menu)
	 */
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.activity_main, menu);
		return true;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Activity#onOptionsItemSelected(android.view.MenuItem)
	 */
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {

		if (item.getItemId() == R.id.capture_qrcode) {
			// launching the QR code capturing
			wifi.startScan();
			try {
				// trying to launch the "Barcode Scanner" application
				Intent intent = new Intent(
						"com.google.zxing.client.android.SCAN");
				intent.setPackage("com.google.zxing.client.android");
				intent.putExtra("SCAN_MODE", "QR_CODE_MODE");
				startActivityForResult(intent, 0);
			} catch (ActivityNotFoundException e) {
				// if the "Barcode Scanner" application is not installed, asking
				// the user if he wants to install it
				AlertDialog alertDialog = new AlertDialog.Builder(this)
						.create();
				alertDialog.setTitle("InstaCircle - Barcode Scanner Required");
				alertDialog
						.setMessage("In order to use this feature, the Application \"Barcode Scanner\" must be installed. Install now?");
				alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, "Yes",
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog,
									int which) {
								dialog.dismiss();
								try {
									startActivity(new Intent(
											Intent.ACTION_VIEW,
											Uri.parse("market://details?id=com.google.zxing.client.android")));
								} catch (Exception e) {
									Log.d(TAG,
											"Unable to find market. User will have to install ZXing himself");
								}
							}
						});
				alertDialog.setButton(AlertDialog.BUTTON_NEGATIVE, "No",
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog,
									int which) {
								dialog.dismiss();
							}
						});
				alertDialog.show();
			}
			return true;

		} else if (item.getItemId() == R.id.rescan_wifi) { 
			// rescanning the WLAN networks
			wifi.startScan();
			Toast.makeText(this, "Rescan initiated", Toast.LENGTH_SHORT).show();
			return true;
			
		} else if (item.getItemId() == R.id.cleanup_conversations) {

			// Display a confirm dialog asking whether really to clean the database
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setTitle("InstaCircle - Clean Database");
			builder.setMessage("Do you really want to clean all your conversations?");
			builder.setPositiveButton("Yes",
					new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							NetworkDbHelper helper = NetworkDbHelper.getInstance(
									MainActivity.this);
							helper.cleanDatabase();
							Toast.makeText(MainActivity.this,
									"Database cleaned", Toast.LENGTH_SHORT)
									.show();
						}
					});
			builder.setNegativeButton("No",
					new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							return;
						}
					});
			AlertDialog dialog = builder.create();
			dialog.show();
			return true;

		}else{
			return super.onOptionsItemSelected(item);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Activity#onActivityResult(int, int,
	 * android.content.Intent)
	 */
	public void onActivityResult(int requestCode, int resultCode, Intent intent) {

		// this will be executed when the "Barcode Scanner" application delivers
		// back a result from its scan.
		if (resultCode == RESULT_OK) {
			String[] config = intent.getStringExtra("SCAN_RESULT").split(
					"\\|\\|");

			// saving the values that we got
			SharedPreferences.Editor editor = preferences.edit();
			editor.putString("SSID", config[0]);
			editor.putString("password", config[1]);
			editor.commit();

			// connect to the network
			connect(config);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Activity#onDestroy()
	 */
	@Override
	protected void onDestroy() {
		super.onDestroy();
		unregisterReceiver(wifibroadcastreceiver);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * ch.bfh.instacircle.ConnectNetworkDialogFragment.NoticeDialogListener#
	 * onDialogNegativeClick(android.app.DialogFragment)
	 */
	public void onDialogNegativeClick(DialogFragment dialog) {
		dialog.dismiss();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * ch.bfh.instacircle.ConnectNetworkDialogFragment.NoticeDialogListener#
	 * onDialogPositiveClick(android.app.DialogFragment)
	 */
	public void onDialogPositiveClick(DialogFragment dialog) {

		if (selectedNetId != -1) {
			adhoc.connectToNetwork(selectedNetId, this);
		} else {
			adhoc.connectToNetwork(selectedResult.SSID,
					((ConnectNetworkDialogFragment) dialog).getNetworkKey(),
					this);
		}

		SharedPreferences.Editor editor = preferences.edit();
		editor.putString("SSID", selectedResult.SSID);
		editor.putString("password",
				((ConnectNetworkDialogFragment) dialog).getPassword());
		editor.commit();

		dialog.dismiss();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * android.widget.AdapterView.OnItemClickListener#onItemClick(android.widget
	 * .AdapterView, android.view.View, int, long)
	 */
	public void onItemClick(AdapterView<?> listview, View view, int position,
			long id) {

		if (listview.getAdapter().getCount() - 1 == position) {
			// handling the last item in the list, which is the "Create network"
			// item
			Intent intent = new Intent(this, CreateNetworkActivity.class);
			startActivity(intent);
		} else {
			// extract the Hashmap assigned to the position which has been
			// clicked
			@SuppressWarnings("unchecked")
			HashMap<String, Object> hash = (HashMap<String, Object>) listview
					.getAdapter().getItem(position);

			selectedResult = (ScanResult) hash.get("object");
			selectedNetId = -1;

			// going through the different connection scenarios
			DialogFragment dialogFragment;
			if ((Boolean) hash.get("secure") && !((Boolean) hash.get("known"))) {
				dialogFragment = new ConnectNetworkDialogFragment(true);
			} else if ((Boolean) hash.get("known")) {
				selectedNetId = (Integer) hash.get("netid");
				dialogFragment = new ConnectNetworkDialogFragment(false);
			} else {
				dialogFragment = new ConnectNetworkDialogFragment(false);
			}

			dialogFragment.show(getFragmentManager(), TAG);

		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Activity#onStart()
	 */
	@Override
	protected void onStart() {
		super.onStart();
		wifi.startScan();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.text.TextWatcher#onTextChanged(java.lang.CharSequence, int,
	 * int, int)
	 */
	public void onTextChanged(CharSequence s, int start, int before, int count) {

	}

	/**
	 * This method is used to extract the name of the device owner
	 * 
	 * @return the name of the device owner
	 */
	public String readOwnerName() {

		Cursor c = getContentResolver().query(
				ContactsContract.Profile.CONTENT_URI, null, null, null, null);
		if (c.getCount() == 0) {
			return "";
		}
		c.moveToFirst();
		String displayName = c.getString(c.getColumnIndex("display_name"));
		c.close();

		return displayName;

	}

	/**
	 * Checks whether the NetworkService is already running or not
	 * 
	 * @return true if service is running, false otherwise
	 */
	public boolean isServiceRunning() {
		ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
		for (RunningServiceInfo service : manager
				.getRunningServices(Integer.MAX_VALUE)) {
			if ("ch.bfh.instacircle.service.NetworkService"
					.equals(service.service.getClassName())) {
				return true;
			}
		}
		return false;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Activity#onNewIntent(android.content.Intent)
	 */
	@Override
	public void onNewIntent(Intent intent) {

		// this method is launched when a NFC tag is tapped on the back of the
		// device
		rawMsgs = intent
				.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
		processNfcTag();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Activity#onPause()
	 */
	@Override
	protected void onPause() {
		super.onPause();
		if (nfcAvailable) {
			nfcAdapter.disableForegroundDispatch(this);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Activity#onResume()
	 */
	@Override
	protected void onResume() {
		super.onResume();

		// make sure that we have priority for processing the NFC tag
		if (nfcAdapter != null && nfcAdapter.isEnabled()) {
			nfcAvailable = true;
		}

		if (nfcAvailable) {
			nfcAdapter.enableForegroundDispatch(this, pendingIntent,
					intentFiltersArray, null);
		}

	}

	/**
	 * This method is used for parsing a tag which is tapped on the back of the
	 * device
	 */
	private void processNfcTag() {
		NdefMessage msg = (NdefMessage) rawMsgs[0];

		String[] config = new String(msg.getRecords()[0].getPayload())
				.split("\\|\\|");

		SharedPreferences.Editor editor = preferences.edit();
		editor.putString("SSID", config[0]);
		editor.putString("password", config[1]);
		editor.commit();

		connect(config);
	}

	/**
	 * This method initiates the connect process
	 * 
	 * @param config
	 *            an array containing the SSID and the password of the network
	 */
	private void connect(String[] config) {
		boolean connectedSuccessful = false;
		// check whether the network is already known, i.e. the password is
		// already stored in the device
		for (WifiConfiguration configuredNetwork : wifi.getConfiguredNetworks()) {
			if (configuredNetwork.SSID.equals("\"".concat(config[0]).concat(
					"\""))) {
				connectedSuccessful = true;
				adhoc.connectToNetwork(configuredNetwork.networkId, this);
				break;
			}
		}
		if (!connectedSuccessful) {
			for (ScanResult result : wifi.getScanResults()) {
				if (result.SSID.equals(config[0])) {
					connectedSuccessful = true;
					adhoc.connectToNetwork(config[0], config[1], this);
					break;
				}
			}
		}

		// display a message if the connection was not successful
		if (!connectedSuccessful) {
			AlertDialog alertDialog = new AlertDialog.Builder(this).create();
			alertDialog.setTitle("InstaCircle - Network not found");
			alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, "OK",
					new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							dialog.dismiss();
						}
					});
			alertDialog.setMessage("The network \"" + config[0]
					+ "\" is not available, cannot connect.");
			alertDialog.show();
		}
	}
}
