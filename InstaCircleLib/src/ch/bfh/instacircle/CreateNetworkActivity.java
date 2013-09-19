
package ch.bfh.instacircle;

import java.util.UUID;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.support.v4.app.NavUtils;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import ch.bfh.instacircle.service.NetworkService;
import ch.bfh.instacircle.wifi.WifiAPManager;

/**
 * Activity which provides functionality to set up a new Wifi access point
 * 
 * @author Juerg Ritter (rittj1@bfh.ch)
 * 
 */
public class CreateNetworkActivity extends Activity implements OnClickListener,
		TextWatcher {

	private WifiAPManager wifiapman;
	private WifiManager wifiman;
	private Button btnCreateNetwork;

	private EditText txtNetworkName;
	private EditText txtNetworkPIN;

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Activity#onCreate(android.os.Bundle)
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// apply the layout
		setContentView(R.layout.activity_create_network);
		getActionBar().setDisplayHomeAsUpEnabled(true);

		// extract the control elements from the layout
		txtNetworkName = (EditText) findViewById(R.id.network_name);
		txtNetworkPIN = (EditText) findViewById(R.id.network_pin);

		btnCreateNetwork = (Button) findViewById(R.id.create_network_button);
		btnCreateNetwork.setOnClickListener(this);

		wifiapman = new WifiAPManager();
		wifiman = (WifiManager) getSystemService(Context.WIFI_SERVICE);

		WifiConfiguration config = wifiapman.getWifiApConfiguration(wifiman);
		txtNetworkName.setText(config.SSID);
		txtNetworkPIN.addTextChangedListener(this);
		txtNetworkPIN.setText(config.preSharedKey);

		// make a suggestion of a password
		if (config.preSharedKey == null || config.preSharedKey.length() < 8) {
			txtNetworkPIN.setText(UUID.randomUUID().toString().substring(0, 8));
		}

		// asking if we should use the already running access point
		if (wifiapman.isWifiAPEnabled(wifiman)) {
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setTitle("InstaCircle - WiFi AP");
			builder.setMessage("The Wifi AP already enabled. Use this connection?");
			builder.setPositiveButton("Yes",
					new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {

							Intent intent = new Intent(
									CreateNetworkActivity.this,
									NetworkService.class);
							intent.putExtra("action", "createnetwork");
							stopService(intent);
							startService(intent);

							CreateNetworkActivity.this.finish();
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
		}

		// enable the create button only if the key has a sufficient length
		if (txtNetworkPIN.getText().toString().length() < 8) {
			btnCreateNetwork.setEnabled(false);
		} else {
			btnCreateNetwork.setEnabled(true);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Activity#onCreateOptionsMenu(android.view.Menu)
	 */
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.activity_create_network, menu);
		return true;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Activity#onOptionsItemSelected(android.view.MenuItem)
	 */
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case android.R.id.home:
			NavUtils.navigateUpFromSameTask(this);
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.view.View.OnClickListener#onClick(android.view.View)
	 */
	public void onClick(View view) {
		if (view == btnCreateNetwork) {
			// setting up the the configuration
			if (wifiapman.isWifiAPEnabled(wifiman)) {
				wifiapman.disableHotspot(wifiman, this);
			}

			WifiConfiguration wificonfig = new WifiConfiguration();
			wificonfig.SSID = txtNetworkName.getText().toString();
			wificonfig.preSharedKey = txtNetworkPIN.getText().toString();
			wificonfig.hiddenSSID = false;
			wificonfig.status = WifiConfiguration.Status.ENABLED;

			wificonfig.allowedGroupCiphers
					.set(WifiConfiguration.GroupCipher.TKIP);
			wificonfig.allowedGroupCiphers
					.set(WifiConfiguration.GroupCipher.CCMP);
			wificonfig.allowedKeyManagement
					.set(WifiConfiguration.KeyMgmt.WPA_PSK);
			wificonfig.allowedPairwiseCiphers
					.set(WifiConfiguration.PairwiseCipher.TKIP);
			wificonfig.allowedPairwiseCiphers
					.set(WifiConfiguration.PairwiseCipher.CCMP);
			wificonfig.allowedProtocols.set(WifiConfiguration.Protocol.RSN);

			// enabling the configuration
			wifiapman.enableHotspot(wifiman, wificonfig, this);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.text.TextWatcher#onTextChanged(java.lang.CharSequence, int,
	 * int, int)
	 */
	public void onTextChanged(CharSequence s, int start, int before, int count) {

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.text.TextWatcher#afterTextChanged(android.text.Editable)
	 */
	public void afterTextChanged(Editable s) {
		// always check after editing the password field whether it has a
		// sufficient length and set the status of the create network button
		// accordingly
		if (txtNetworkPIN.getText().toString().length() < 8) {
			btnCreateNetwork.setEnabled(false);
		} else {
			btnCreateNetwork.setEnabled(true);
		}
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
}
