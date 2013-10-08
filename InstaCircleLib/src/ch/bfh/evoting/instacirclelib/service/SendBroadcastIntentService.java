package ch.bfh.evoting.instacirclelib.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Enumeration;
import java.util.Random;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.net.DhcpInfo;
import android.net.wifi.WifiManager;
import android.util.Log;
import ch.bfh.evoting.instacirclelib.Message;
import ch.bfh.evoting.instacirclelib.db.NetworkDbHelper;

public class SendBroadcastIntentService extends IntentService {

	private static final String TAG = NetworkService.class.getSimpleName();
	private static final String PREFS_NAME = "network_preferences";

	private InetAddress broadcast;
	private DatagramSocket s;
	private String cipherKey;

	private Random random;

	private CipherHandler cipherHandler;

	public SendBroadcastIntentService() {
		super(TAG);
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {


		// Unfortunately the broadcast address is not available immediately
		// after the network connection is acutally indicated as ready...
		do {
			broadcast = getBroadcastAddress();
			if (broadcast != null) {
				break;
			}
			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		} while (broadcast == null);
		// Reading the cipher key from the preferences file
		cipherKey = getSharedPreferences(PREFS_NAME, 0).getString("password",
				"N/A");

		cipherHandler = new CipherHandler(cipherKey.getBytes());

		random = new SecureRandom();

		return super.onStartCommand(intent, flags, startId);
	}

	@Override
	protected void onHandleIntent(Intent intent) {

		Message msg = (Message) intent.getSerializableExtra("message");


		if (broadcast == null) {
			broadcast = getBroadcastAddress();
		}

		try {
			// serializing the message
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			ObjectOutput out = new ObjectOutputStream(bos);
			out.writeObject(msg);
			byte[] bytes = bos.toByteArray();

			// encrypt the message
			byte[] encryptedBytes = cipherHandler.encrypt(bytes);

			// creating a byte array of 4 bytes to put the lenght of the
			// payload
			ByteBuffer b = ByteBuffer.allocate(4);
			b.putInt(encryptedBytes.length);
			byte[] length = b.array();

			byte[] bytesToSend = new byte[length.length
			                              + encryptedBytes.length];

			// contatenate the length and the payload
			System.arraycopy(length, 0, bytesToSend, 0, length.length);
			System.arraycopy(encryptedBytes, 0, bytesToSend, length.length,
					encryptedBytes.length);

			// setting up the datagram and sending it
			s = new DatagramSocket();
			s.setBroadcast(true);
			s.setReuseAddress(true);

			DatagramPacket p = new DatagramPacket(bytesToSend,
					bytesToSend.length, broadcast, random.nextInt(50) + 12300);
			s.send(p);
			s.close();
		} catch (IOException e) {
			Log.e(this.getClass().getSimpleName(), "Broadcast not sent");
			e.printStackTrace();
			//When broadcast could not be send and it is a LEAVE message, then we end the conversation
			if (msg.getMessageType() == Message.MSG_MSGLEAVE) {
				if (msg.getMessage().equals(Message.DELETE_DB)) {
					try{
						NetworkDbHelper.getInstance(this).cleanDatabase();
						Log.d(TAG, "Database cleaned.");
					} catch (Exception ex){
						Log.e(TAG, "Database coudn't be cleaned.");
					}

				}
				Intent stopServiceIntent = new Intent(this, NetworkService.class);
				stopService(stopServiceIntent);
			} 
		}
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
	}

	/**
	 * Method to extract the broadcastaddress of the current network
	 * configuration
	 * 
	 * @return The broadcast address
	 */
	public InetAddress getBroadcastAddress() {
		InetAddress found_bcast_address = null;

		//TODO no more possible since move in VotingLib => find a solution
		WifiManager wifiManager = (WifiManager) this.getSystemService(Context.WIFI_SERVICE);
//		if(new WifiAPManager().isWifiAPEnabled(wifiManager)){
//
//			System.setProperty("java.net.preferIPv4Stack", "true");
//			try {
//				Enumeration<NetworkInterface> niEnum = NetworkInterface
//						.getNetworkInterfaces();
//				while (niEnum.hasMoreElements()) {
//					NetworkInterface ni = niEnum.nextElement();
//
//					if (ni.getDisplayName().contains("p2p-wlan")) {
//						for (InterfaceAddress interfaceAddress : ni.getInterfaceAddresses()) {
//
//							found_bcast_address = interfaceAddress.getBroadcast();
//						}
//						if (found_bcast_address != null) {
//							break;
//						}
//					}
//				}
//
//				if (found_bcast_address == null) {
//					niEnum = NetworkInterface.getNetworkInterfaces();
//					while (niEnum.hasMoreElements()) {
//						NetworkInterface ni = niEnum.nextElement();
//						if (!ni.isLoopback()) {
//							for (InterfaceAddress interfaceAddress : ni
//									.getInterfaceAddresses()) {
//
//								found_bcast_address = interfaceAddress
//										.getBroadcast();
//							}
//
//							if (found_bcast_address != null) {
//								break;
//							}
//
//						}
//					}
//				}
//			} catch (SocketException e) {
//				e.printStackTrace();
//			}
//
//		} else {
			//source: http://stackoverflow.com/questions/2993874/android-broadcast-address
			WifiManager wifi = (WifiManager) getSystemService(Context.WIFI_SERVICE);
			DhcpInfo dhcp = wifi.getDhcpInfo();

			int broadcast = (dhcp.ipAddress & dhcp.netmask) | ~dhcp.netmask;
			byte[] quads = new byte[4];
			for (int k = 0; k < 4; k++)
				quads[k] = (byte) (broadcast >> (k * 8));
			try {
				found_bcast_address =  InetAddress.getByAddress(quads);
			} catch (UnknownHostException e) {
				e.printStackTrace();
			}

//		}
		Log.d(this.getClass().getSimpleName(), "Broadcast address is "+found_bcast_address);

		return found_bcast_address;


	}



}
