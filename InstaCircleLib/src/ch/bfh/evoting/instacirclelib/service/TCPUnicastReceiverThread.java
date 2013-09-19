
package ch.bfh.evoting.instacirclelib.service;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;

import android.content.Context;
import android.content.Intent;

/**
 * This class implements a Thread which is waiting for incoming TCP messages and
 * dispatches them to the NetworkService to process them.
 * 
 * @author Juerg Ritter (rittj1@bfh.ch)
 */
public class TCPUnicastReceiverThread extends Thread {

	private static final String TAG = TCPUnicastReceiverThread.class
			.getSimpleName();
	public ServerSocket serverSocket;
	
	private Context context;
	private int port;

	/**
	 * @param service
	 *            the service to which the message is being dispatched after
	 *            receiving it
	 * @param cipherKey
	 *            the cipher key which will be used for decrypting the messages
	 */
	public TCPUnicastReceiverThread(Context context, int port) {
		this.setName(TAG);
		this.context = context;
		this.port = port;
	}

	public void run() {
		Socket clientSocket;
		InputStream in = null;
		try {
			serverSocket = new ServerSocket(port);
			while (!Thread.currentThread().isInterrupted()) {

				try {
					clientSocket = serverSocket.accept();
					in = clientSocket.getInputStream();
					DataInputStream dis = new DataInputStream(in);

					// Reading the first 4 bytes which represent a 32 Bit
					// integer and indicates the length of the encrypted payload
					byte[] lenght = new byte[4];
					dis.read(lenght);

					// Initialize and read an array with the previously
					// determined length
					byte[] encryptedData = new byte[ByteBuffer.wrap(lenght)
							.getInt()];
					dis.readFully(encryptedData);
					
					Intent processIntent = new Intent(context, ProcessUnicastMessageIntentService.class);
					processIntent.putExtra("encryptedData", encryptedData);
					processIntent.putExtra("senderAddress", clientSocket.getInetAddress().getHostAddress());
					context.startService(processIntent);
					
				} catch (IOException e) {
					serverSocket.close();
					Thread.currentThread().interrupt();
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				if (in != null) {
					in.close();
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
}
