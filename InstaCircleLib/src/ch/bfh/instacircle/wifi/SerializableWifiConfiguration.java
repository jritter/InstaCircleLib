
package ch.bfh.instacircle.wifi;

import java.io.Serializable;
import java.util.Arrays;
import java.util.BitSet;

import android.net.wifi.WifiConfiguration;

/**
 * This is a wrapper class which is used to serialize a WifiConfiguration
 * object. This is used for backing up the wifi AP configuration.
 * 
 * @author Juerg Ritter (rittj1@bfh.ch)
 */
public class SerializableWifiConfiguration implements Serializable {

	private static final long serialVersionUID = 1L;

	public String BSSID;
	public String SSID;
	public BitSet allowedAuthAlgorithms;
	public BitSet allowedGroupCiphers;
	public BitSet allowedKeyManagement;
	public BitSet allowedPairwiseCiphers;
	public BitSet allowedProtocols;
	public boolean hiddenSSID;
	public String preSharedKey;
	public int priority;
	public int status;
	public String[] wepKeys;
	public int wepTxKeyIndex;

	/**
	 * @param config
	 *            The WifiConfiguration object which should be transfered into a
	 *            serializable object.
	 */
	public SerializableWifiConfiguration(WifiConfiguration config) {
		this.BSSID = config.BSSID;
		this.SSID = config.SSID;
		this.allowedAuthAlgorithms = config.allowedAuthAlgorithms;
		this.allowedGroupCiphers = config.allowedGroupCiphers;
		this.allowedKeyManagement = config.allowedKeyManagement;
		this.allowedPairwiseCiphers = config.allowedPairwiseCiphers;
		this.allowedProtocols = config.allowedProtocols;
		this.hiddenSSID = config.hiddenSSID;
		this.preSharedKey = config.preSharedKey;
		this.priority = config.priority;
		this.wepKeys = config.wepKeys;
		this.wepTxKeyIndex = config.wepTxKeyIndex;
	}

	/**
	 * Transforms the object back into a WifiConfiguration object.
	 * 
	 * @return the newly created WifiConfiguration object
	 */
	public WifiConfiguration getWifiConfiguration() {
		WifiConfiguration config = new WifiConfiguration();
		if (this.BSSID == null) {
			config.BSSID = "";
		} else {
			config.BSSID = this.BSSID;
		}
		config.SSID = this.SSID;
		config.allowedAuthAlgorithms = this.allowedAuthAlgorithms;
		config.allowedGroupCiphers = this.allowedGroupCiphers;
		config.allowedKeyManagement = this.allowedKeyManagement;
		config.allowedPairwiseCiphers = this.allowedPairwiseCiphers;
		config.allowedProtocols = this.allowedProtocols;
		config.hiddenSSID = this.hiddenSSID;
		config.preSharedKey = this.preSharedKey;
		config.priority = this.priority;
		config.wepKeys = this.wepKeys;
		config.wepTxKeyIndex = this.wepTxKeyIndex;

		return config;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "SerializableWifiConfiguration [BSSID=" + BSSID + ", SSID="
				+ SSID + ", allowedAuthAlgorithms=" + allowedAuthAlgorithms
				+ ", allowedGroupCiphers=" + allowedGroupCiphers
				+ ", allowedKeyManagement=" + allowedKeyManagement
				+ ", allowedPairwiseCiphers=" + allowedPairwiseCiphers
				+ ", allowedProtocols=" + allowedProtocols + ", hiddenSSID="
				+ hiddenSSID + ", preSharedKey=" + preSharedKey + ", priority="
				+ priority + ", status=" + status + ", wepKeys="
				+ Arrays.toString(wepKeys) + ", wepTxKeyIndex=" + wepTxKeyIndex
				+ "]";
	}
}
