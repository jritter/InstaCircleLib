package ch.bfh.instacircle.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.StreamCorruptedException;
import java.util.ArrayList;
import java.util.Arrays;

import android.app.IntentService;
import android.content.Intent;
import android.database.Cursor;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Base64;
import android.util.Log;
import ch.bfh.instacircle.Message;
import ch.bfh.instacircle.db.NetworkDbHelper;

public class ProcessUnicastMessageIntentService extends IntentService {

	private static final String TAG = NetworkService.class.getSimpleName();
	private static final String PREFS_NAME = "network_preferences";
	private static final Integer[] messagesToSave = { Message.MSG_CONTENT,
			Message.MSG_MSGJOIN, Message.MSG_MSGLEAVE };

	private NetworkDbHelper dbHelper;
	private String identification;
	private String cipherKey;
	private CipherHandler cipherHandler;

	public ProcessUnicastMessageIntentService() {
		super(TAG);
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		// Initializing the dbHelper in order to get access to the database
		dbHelper = NetworkDbHelper.getInstance(this);
		identification = getSharedPreferences(PREFS_NAME, 0).getString(
				"identification", "N/A");
		
		cipherKey = getSharedPreferences(PREFS_NAME, 0).getString("password",
				"N/A");
		cipherHandler = new CipherHandler(cipherKey.getBytes());
		
		return super.onStartCommand(intent, flags, startId);
	}

	@Override
	protected void onHandleIntent(Intent intent) {
		
		byte[] encryptedData = intent.getByteArrayExtra("encryptedData");
		byte[] data = cipherHandler.decrypt(encryptedData);
		
		Message msg = null;

		// let's try to deserialize the payload only if the
		// decryption process has been successful
		if (data != null) {

			// deserializing the payload into a Message object
			ByteArrayInputStream bis = new ByteArrayInputStream(
					data);
			ObjectInput oin = null;
			try {
				oin = new ObjectInputStream(bis);
				msg = (Message) oin.readObject();
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				
				try {
					bis.close();
					oin.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}

			msg.setSenderIPAddress(intent.getStringExtra("senderAddress"));

			// Use the messagetype to determine what to do with the message
			switch (msg.getMessageType()) {
	
			case Message.MSG_CONTENT:
				// no special action required, just saving later on...
				break;
	
			case Message.MSG_RESENDREQ:
				// as soon as a resend request arrives we query the messages, stuff
				// them into an array and send the result back
				Cursor myMessages = dbHelper.queryMyMessages();
				ArrayList<Message> messages = new ArrayList<Message>();
				// iterate over cursor
				for (boolean hasItem = myMessages.moveToFirst(); hasItem; hasItem = myMessages
						.moveToNext()) {
	
					// Assemble new messages from database
					messages.add(new Message(myMessages.getString(myMessages
							.getColumnIndex("message")), myMessages
							.getInt(myMessages.getColumnIndex("message_type")),
							myMessages.getString(myMessages
									.getColumnIndex("identification")), myMessages
									.getInt(myMessages
											.getColumnIndex("sequence_number"))));
				}
	
				// serializing the list to a Base64 String
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				ObjectOutputStream oos;
				try {
					oos = new ObjectOutputStream(baos);
					oos.writeObject(messages);
					oos.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
				String serializedMessages = Base64.encodeToString(
						baos.toByteArray(), Base64.DEFAULT);
	
	
				Message resendMessage = new Message(serializedMessages,
						Message.MSG_RESENDRES, identification, -1);
				
				Intent resendMessageIntent = new Intent(this, SendUnicastIntentService.class);
				resendMessageIntent.putExtra("message", resendMessage);
				resendMessageIntent.putExtra("ipAddress", msg.getSenderIPAddress());
				startService(resendMessageIntent);
				break;
	
			case Message.MSG_RESENDRES:
				// handling the resend response
				try {
					// deserializing the content into an ArrayList containing
					// messages
					ObjectInputStream ois = new ObjectInputStream(
							new ByteArrayInputStream(Base64.decode(
									msg.getMessage(), Base64.DEFAULT)));
					@SuppressWarnings("unchecked")
					ArrayList<Message> deserializedMessages = (ArrayList<Message>) ois
							.readObject();
					// iterate over the list and hanling them as if they had just
					// arrived as broadcasts
					for (Message message : deserializedMessages) {
						Log.d(TAG, "Reprocessing message...");
						message.setSenderIPAddress(msg.getSenderIPAddress());
						Intent processIntent = new Intent(this, ProcessBroadcastMessageIntentService.class);
						processIntent.putExtra("message", message);
						processIntent.putExtra("decrypted", true);
						startService(processIntent);
					}
				} catch (StreamCorruptedException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				} catch (ClassNotFoundException e) {
					e.printStackTrace();
				}
				break;
	
			case Message.MSG_IAMHERE:
	
				// Check if the received sequence number is bigger than the one we
				// have in our db, request messages if true
				if (Integer.parseInt(msg.getMessage()) > dbHelper
						.getCurrentParticipantSequenceNumber(msg.getSender())) {
					// Request missing messages
					Message resendRequestMessage = new Message("",
							Message.MSG_RESENDREQ, getSharedPreferences(PREFS_NAME,
									0).getString("identification", "N/A"), -1);
	
					Intent resendRequestMessageIntent = new Intent(this, SendUnicastIntentService.class);
					resendRequestMessageIntent.putExtra("message", resendRequestMessage);
					resendRequestMessageIntent.putExtra("ipAddress", msg.getSenderIPAddress());
					startService(resendRequestMessageIntent);
				}
	
				break;
			}
	
			// non-Broadcast messages don't get a valid sequence number
			if (Arrays.asList(messagesToSave).contains(msg.getMessageType())) {
				msg.setSequenceNumber(-1);
				dbHelper.insertMessage(msg);
			}
	
			// notify the UI that new message has been arrived
			Intent messageArrivedIntent = new Intent("messageArrived");
			messageArrivedIntent.putExtra("message", msg);
			LocalBroadcastManager.getInstance(this).sendBroadcast(messageArrivedIntent);
		}
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
	}
}
