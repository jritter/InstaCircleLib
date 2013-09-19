package ch.bfh.instacircle.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.util.Arrays;

import android.app.IntentService;
import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import ch.bfh.instacircle.Message;
import ch.bfh.instacircle.db.NetworkDbHelper;

public class ProcessBroadcastMessageIntentService extends IntentService {

	private static final String TAG = NetworkService.class.getSimpleName();
	private static final String PREFS_NAME = "network_preferences";
	private static final Integer[] messagesToSave = { Message.MSG_CONTENT,
		Message.MSG_MSGJOIN, Message.MSG_MSGLEAVE };
	
	private NetworkDbHelper dbHelper;
	private String cipherKey;
	private CipherHandler cipherHandler;
	
	public ProcessBroadcastMessageIntentService() {
		super(TAG);
	}
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		// Initializing the dbHelper in order to get access to the database
		dbHelper = NetworkDbHelper.getInstance(this);
		
		cipherKey = getSharedPreferences(PREFS_NAME, 0).getString("password",
				"N/A");
		cipherHandler = new CipherHandler(cipherKey.getBytes());
		return super.onStartCommand(intent, flags, startId);
	}

	@Override
	protected void onHandleIntent(Intent intent) {
		
		String identification = getSharedPreferences(PREFS_NAME, 0).getString(
				"identification", "N/A");
		Message msg = null;
		
		boolean dataAvailable = false;
		
		if (intent.getBooleanExtra("decrypted", false)){
			msg = (Message) intent.getSerializableExtra("message");
			dataAvailable = true;
		}
		else {
			byte[] encryptedData = intent.getByteArrayExtra("encryptedData");
			byte[] data = cipherHandler.decrypt(encryptedData);
			if (data != null){
				// deserializing the payload into a Message object
				ByteArrayInputStream bis = new ByteArrayInputStream(
						data);
				ObjectInput oin = null;
				try {
					oin = new ObjectInputStream(bis);
					msg = (Message) oin.readObject();
				}
				catch (Exception e){
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
				dataAvailable = true;
			}
		}
		
		// let's try to deserialize the payload only if the
		// decryption process has been successful
		if (dataAvailable) {

			// Use the messagetype to determine what to do with the message
			switch (msg.getMessageType()) {
	
			case Message.MSG_CONTENT:
				// no special action required, just saving later on...
				break;
			case Message.MSG_MSGJOIN:
				// add the new participant to the participants list
				dbHelper.insertParticipant(msg.getMessage(),
						msg.getSenderIPAddress());
	
				// notify the UI
				intent = new Intent("participantJoined");
				LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
				break;
			case Message.MSG_MSGLEAVE:
				// decativate the participant
				dbHelper.updateParticipantState(msg.getSender(), 0);
	
				// Notify the UI, but only if it's not myself who left
				if (!msg.getSender().equals(identification)) {
					intent = new Intent("participantChangedState");
					intent.putExtra("participant", msg.getSenderIPAddress());
					LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
				}
				break;
			case Message.MSG_RESENDREQ:
				// should be handled as unicast
				break;
			case Message.MSG_RESENDRES:
				// should be handled as unicast
				break;
			case Message.MSG_WHOISTHERE:
				// immediately send a unicast response with my own identification
				// back
				Message response = new Message(
						(dbHelper.getNextSequenceNumber() - 1) + "",
						Message.MSG_IAMHERE, identification, -1);
				
				Intent messageIntent = new Intent(this, SendUnicastIntentService.class);
				messageIntent.putExtra("message", response);
				messageIntent.putExtra("ipAddress", msg.getSenderIPAddress());
				startService(messageIntent);
				break;
				
			case Message.MSG_IAMHERE:
				// should be handled as unicast
				break;
			default:
				// shouldn't happen
				break;
			}

			// handling the conversation relevant messages
			if (Arrays.asList(messagesToSave).contains(msg.getMessageType())) {
	
				// checking whether the sequence number is the one which we expect
				// from the participant, otherwise request the missing messages
				if (msg.getSequenceNumber() != -1
						&& msg.getSequenceNumber() > dbHelper
								.getCurrentParticipantSequenceNumber(msg
										.getSender()) + 1) {
					// Request missing messages
					Message resendRequestMessage = new Message("",
							Message.MSG_RESENDREQ, getSharedPreferences(PREFS_NAME,
									0).getString("identification", "N/A"), -1);
					
					Intent messageIntent = new Intent(this, SendUnicastIntentService.class);
					messageIntent.putExtra("message", resendRequestMessage);
					messageIntent.putExtra("ipAddress", msg.getSenderIPAddress());
					startService(messageIntent);
				} else {
					if (dbHelper != null && (!dbHelper.isMessageInDatabase(msg))) {
						// insert the message into the database
						dbHelper.insertMessage(msg);
					}
				}
			}
	
			if (msg.getMessageType() == Message.MSG_MSGLEAVE
					&& msg.getSender().equals(identification)) {
				if (msg.getMessage().equals(Message.DELETE_DB)
						&& msg.getSender().equals(identification)) {
					dbHelper.cleanDatabase();
				}
				Intent stopServiceIntent = new Intent(this, NetworkService.class);
				stopService(stopServiceIntent);
			} else {
				// otherwise notify the UI that a new message has been arrived
				Intent messageArrivedIntent = new Intent("messageArrived");
				messageArrivedIntent.putExtra("message", msg);
				LocalBroadcastManager.getInstance(this).sendBroadcast(messageArrivedIntent);
				Log.d(TAG, "sent broadcast...");
			}
		}
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
	}

}
