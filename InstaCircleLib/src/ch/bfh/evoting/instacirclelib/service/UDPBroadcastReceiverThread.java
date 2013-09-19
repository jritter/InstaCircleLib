
package ch.bfh.evoting.instacirclelib.service;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.ByteBuffer;

import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.MulticastLock;
import android.util.Log;

/**
 * This class implements a Thread which is waiting for incoming UDP messages and
 * dispatches them to the NetworkService to process them.
 * 
 * @author Juerg Ritter (rittj1@bfh.ch)
 */
public class UDPBroadcastReceiverThread extends Thread {

	private static final String TAG = UDPBroadcastReceiverThread.class
			.getSimpleName();

	public DatagramSocket socket;

	private Context context;
	private int port;

	/**
	 * @param service
	 *            the service to which the message is being dispatched after
	 *            receiving it
	 * @param cipherKey
	 *            the cipher key which will be used for decrypting the messages
	 */
	public UDPBroadcastReceiverThread(Context context, int port) {
		this.setName(TAG);
		this.context = context;
		this.port = port;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Thread#run()
	 */
	public void run() {
		
		WifiManager wifi;
		wifi = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
		MulticastLock ml = wifi.createMulticastLock("Multicast lock");
		ml.acquire();
		
		try {
			socket = new DatagramSocket(port);
			socket.setBroadcast(true);
			while (!Thread.currentThread().isInterrupted()) {
				try {

					DatagramPacket datagram = new DatagramPacket(
							new byte[socket.getReceiveBufferSize()],
							socket.getReceiveBufferSize());
					socket.receive(datagram);
					Log.d(TAG, "got message...");
					byte[] data = datagram.getData();

					// Reading the first 4 bytes which represent a 32 Bit
					// integer and indicates the length of the encrypted payload
					byte[] length = new byte[4];
					System.arraycopy(data, 0, length, 0, length.length);

					// initializing the array for the payload with the length
					// which has been extracted before
					byte[] encryptedData = new byte[ByteBuffer.wrap(length)
							.getInt()];

					System.arraycopy(data, length.length, encryptedData, 0,
							encryptedData.length);
					
					Intent processIntent = new Intent(context, ProcessBroadcastMessageIntentService.class);
					processIntent.putExtra("encryptedData", encryptedData);
					processIntent.putExtra("senderAddress", datagram.getAddress().getHostAddress());
					context.startService(processIntent);

				} catch (IOException e) {
					socket.close();
					Thread.currentThread().interrupt();
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			ml.release();
		}
	}
}
