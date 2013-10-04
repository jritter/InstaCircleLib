
package ch.bfh.evoting.instacirclelib.service;

import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiManager;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import ch.bfh.evoting.instacirclelib.Message;
import ch.bfh.evoting.instacirclelib.db.NetworkDbHelper;
import ch.bfh.evoting.instacirclelib.wifi.AdhocWifiManager;
import ch.bfh.evoting.instacirclelib.wifi.WifiAPManager;

/**
 * This class implements an Android service which runs in the background. It
 * handles the incoming messages and reacts accordingly.
 * 
 * @author Juerg Ritter (rittj1@bfh.ch)
 */
public class NetworkService extends Service {

	private static final String TAG = NetworkService.class.getSimpleName();
	private static final String PREFS_NAME = "network_preferences";

	private NetworkDbHelper dbHelper;

	private UDPBroadcastReceiverThread[] udpBroadcastReceiverThreads;
	private TCPUnicastReceiverThread[] tcpUnicastReceiverThreads;


	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Service#onBind(android.content.Intent)
	 */
	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Service#onCreate()
	 */
	@Override
	public void onCreate() {

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Service#onDestroy()
	 */
	@Override
	public void onDestroy() {

		// Stop everything if the leave message is coming from myself
		try {
			for (int i = 0; i < tcpUnicastReceiverThreads.length; i++){
				tcpUnicastReceiverThreads[i].interrupt();
				udpBroadcastReceiverThreads[i].interrupt();
				tcpUnicastReceiverThreads[i].serverSocket.close();
				udpBroadcastReceiverThreads[i].socket.close();
			}
		} catch (Exception e) {

		}


		dbHelper.closeConversation();


		WifiManager wifiman = (WifiManager) getSystemService(Context.WIFI_SERVICE);
		new AdhocWifiManager(wifiman)
		.restoreWifiConfiguration(getBaseContext());
		WifiAPManager wifiAP = new WifiAPManager();
		if (wifiAP.isWifiAPEnabled(wifiman)) {
			wifiAP.disableHotspot(wifiman, getBaseContext());
		}
		stopSelf();
		NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		notificationManager.cancelAll();
		//		Intent intent = new Intent(this, MainActivity.class);
		//		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		//		intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
		//		startActivity(intent);
		// Unregister the receiver which listens for messages to be sent
		LocalBroadcastManager.getInstance(this).unregisterReceiver(
				messageSendReceiver);

		// close the DB connection
		dbHelper.close();
		super.onDestroy();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Service#onStartCommand(android.content.Intent, int, int)
	 */
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {

		// Initializing the dbHelper in order to get access to the database
		dbHelper = NetworkDbHelper.getInstance(this);

		/* Commented by Phil: no more needed
		// Create a pending intent which will be invoked after tapping on the
		// Android notification
		Intent notificationIntent = new Intent(this,
				NetworkActiveActivity.class);
		notificationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		notificationIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
		notificationIntent.addFlags(Intent.FLAG_ACTIVITY_TASK_ON_HOME);
		PendingIntent pendingNotificationIntent = PendingIntent.getActivity(
				this, 0, notificationIntent, notificationIntent.getFlags());

		// Setting up the notification which is being displayed
		Notification.Builder notificationBuilder = new Notification.Builder(
				this);
		notificationBuilder.setContentTitle(getResources().getString(
				R.string.app_name));
		notificationBuilder
				.setContentText("An InstaCircle Chat session is running. Tap to bring in front.");
		notificationBuilder
				.setSmallIcon(R.drawable.glyphicons_244_conversation);
		notificationBuilder.setContentIntent(pendingNotificationIntent);
		notificationBuilder.setOngoing(true);
		Notification notification = notificationBuilder.getNotification();

		NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

		notificationManager.notify(TAG, 1, notification);*/

		udpBroadcastReceiverThreads = new UDPBroadcastReceiverThread[100];
		tcpUnicastReceiverThreads = new TCPUnicastReceiverThread[100];

		// starting 100 threads allocating 100 Ports
		for (int i = 0; i < 50; i++){
			udpBroadcastReceiverThreads[i] = new UDPBroadcastReceiverThread(this, i + 12300);
			tcpUnicastReceiverThreads[i] = new TCPUnicastReceiverThread(this, i + 12300);

			udpBroadcastReceiverThreads[i].start();
			tcpUnicastReceiverThreads[i].start();
		}

		// Register a broadcastreceiver in order to get notification from the UI
		// when a message should be sent
		LocalBroadcastManager.getInstance(this).registerReceiver(
				messageSendReceiver, new IntentFilter("messageSend"));

		// Opening a conversation
		dbHelper.openConversation(getSharedPreferences(PREFS_NAME, 0)
				.getString("password", "N/A"));

		// joining the conversation using the identification in the preferences
		// file
		joinNetwork(getSharedPreferences(PREFS_NAME, 0).getString(
				"identification", "N/A"));

		// start the next activity if we were successful
		Intent i = new Intent("NetworkServiceStarted");
		LocalBroadcastManager.getInstance(this.getApplicationContext()).sendBroadcast(i);
		Log.e("NetworkService", "service started");

		/*//Added by Phil
		// start the EvotingMainActivity
		intent = new Intent(this, EvotingMainActivity.class);
		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
		intent.addFlags(Intent.FLAG_ACTIVITY_TASK_ON_HOME);
		startActivity(intent);

		// Create a pending intent which will be invoked after tapping on the
		// Android notification
		Intent notificationIntent = new Intent(this,
				EvotingMainActivity.class);
		notificationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		notificationIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
		notificationIntent.addFlags(Intent.FLAG_ACTIVITY_TASK_ON_HOME);"
		PendingIntent pendingNotificationIntent = PendingIntent.getActivity(
				this, 0, notificationIntent, notificationIntent.getFlags());

		// Setting up the notification which is being displayed
		Notification.Builder notificationBuilder = new Notification.Builder(
				this);
		notificationBuilder.setContentTitle(getResources().getString(
				R.string.app_name));
		notificationBuilder
		.setContentText("An InstaCircle Chat session is running. Tap to bring in front.");
		notificationBuilder
		.setSmallIcon(R.drawable.glyphicons_244_conversation);
		notificationBuilder.setContentIntent(pendingNotificationIntent);
		notificationBuilder.setOngoing(true);
		Notification notification = notificationBuilder.getNotification();

		NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

		notificationManager.notify(TAG, 1, notification);*/

		/* Commented by Phil, no more needed
		// start the NetworkActiveActivity
		intent = new Intent(this, NetworkActiveActivity.class);
		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
		intent.addFlags(Intent.FLAG_ACTIVITY_TASK_ON_HOME);
		startActivity(intent);*/
		return super.onStartCommand(intent, flags, startId);
	}

	/**
	 * Implementation of a BroadcastReceiver in order to receive the
	 * notification that the UI wants to send a message
	 */
	private BroadcastReceiver messageSendReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			// extract the broadcast flag from the intent
			boolean broadcast = intent.getBooleanExtra("broadcast", true);

			// extract the message from the intent
			Message msg = (Message) intent.getSerializableExtra("message");
			if (broadcast) {
				Intent messageIntent = new Intent(NetworkService.this, SendBroadcastIntentService.class);
				messageIntent.putExtra("message", msg);
				startService(messageIntent);
			} else {
				Intent messageIntent = new Intent(NetworkService.this, SendUnicastIntentService.class);
				messageIntent.putExtra("message", msg);
				messageIntent.putExtra("ipAddress", intent.getStringExtra("ipAddress"));
				startService(messageIntent);
			}
		}
	};


	/**
	 * Helper method which assembles and send a join message and a whoisthere
	 * message right afterwards
	 * 
	 * @param identification
	 */
	private void joinNetwork(String identification) {

		Message joinMessage = new Message(identification, Message.MSG_MSGJOIN,
				identification, dbHelper.getNextSequenceNumber());

		Intent joinMessageIntent = new Intent(NetworkService.this, SendBroadcastIntentService.class);
		joinMessageIntent.putExtra("message", joinMessage);
		startService(joinMessageIntent);

		Message whoisthereMessage = new Message(identification,
				Message.MSG_WHOISTHERE, identification, -1);

		Intent whoisthereMessageIntent = new Intent(NetworkService.this, SendBroadcastIntentService.class);
		whoisthereMessageIntent.putExtra("message", whoisthereMessage);
		startService(whoisthereMessageIntent);

	}
}
