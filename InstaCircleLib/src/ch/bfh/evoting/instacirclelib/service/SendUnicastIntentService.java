package ch.bfh.evoting.instacirclelib.service;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Random;

import android.app.IntentService;
import android.content.Intent;
import ch.bfh.evoting.instacirclelib.Message;

public class SendUnicastIntentService extends IntentService {
	
	private static final String TAG = NetworkService.class.getSimpleName();
	private static final String PREFS_NAME = "network_preferences";

	
	private String cipherKey;
	private Socket socket;
	private CipherHandler cipherHandler;
	
	private Random random;

	public SendUnicastIntentService() {
		super(TAG);
	}
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		
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
		String destinationAddr = intent.getStringExtra("ipAddress");
		
		try {
			
			// serializing the message
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			ObjectOutput out = new ObjectOutputStream(bos);
			out.writeObject(msg);

			byte[] bytes = bos.toByteArray();

			// encrypt the message
			byte[] encryptedBytes = cipherHandler.encrypt(bytes);

			socket = new Socket(destinationAddr, random.nextInt(50) + 12300);
			out.close();

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

			// send the data
			DataOutputStream dOut = new DataOutputStream(
					socket.getOutputStream());
			dOut.write(bytesToSend);
			dOut.flush();
			dOut.close();
			socket.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
	}

}
