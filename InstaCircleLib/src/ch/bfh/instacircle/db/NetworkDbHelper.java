
package ch.bfh.instacircle.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;
import ch.bfh.instacircle.Message;

/**
 * This class implements a SQLiteOpenHelper which provides the query methods
 * which are used in the application. This is the only component of the
 * application which communicates directly to the SQLite database.
 * 
 * @author Juerg Ritter (rittj1@bfh.ch)
 */
public class NetworkDbHelper extends SQLiteOpenHelper {

	private static final String TAG = NetworkDbHelper.class.getSimpleName();
	private static final String PREFS_NAME = "network_preferences";

	// Basic DB parameters
	private static final String DATABASE_NAME = "network.db";
	private static final int DATABASE_VERSION = 32;

	// Table names
	private static final String TABLE_NAME_MESSAGE = "message";
	private static final String TABLE_NAME_PARTICIPANT = "participant";
	private static final String TABLE_NAME_CONVERSATION = "conversation";

	// Attributes of the messages table
	private static final String MESSAGE_ID = "_id";
	private static final String MESSAGE_MESSAGE = "message";
	private static final String MESSAGE_MESSAGE_TYPE = "message_type";
	private static final String MESSAGE_SENDER_ID = "sender_id";
	private static final String MESSAGES_SEQUENCE_NUMBER = "sequence_number";
	private static final String MESSAGES_SOURCE_IP_ADDRESS = "source_ip_address";
	private static final String MESSAGES_TIMESTAMP = "timestamp";

	// Attributes of the participants table
	private static final String PARTICIPANT_ID = "_id";
	private static final String PARTICIPANT_CONVERSATION_ID = "conversation_id";
	private static final String PARTICIPANT_IDENTIFICATION = "identification";
	private static final String PARTICIPANT_IP_ADDRESS = "ip_address";
	private static final String PARTICIPANT_STATE = "state";

	// Attributes of the conversations table
	private static final String CONVERSATION_ID = "_id";
	private static final String CONVERSATION_KEY = "conversation_key";
	private static final String CONVERSATION_START = "conversation_start";
	private static final String CONVERSATION_END = "conversation_end";
	private static final String CONVERSATION_OPEN = "conversation_open";

	private static NetworkDbHelper mInstance;

	private Context context;
	private String identification;

	/**
	 * Create an instance of the class
	 * @param ctx
	 * 			The context from which it being invoked
	 * @return instance of the object
	 */
	public static NetworkDbHelper getInstance(Context ctx) {

		if (mInstance == null) {
			mInstance = new NetworkDbHelper(ctx);
		}
		return mInstance;
	}

	/**
	 * @param context
	 *            The context from which it being invoked
	 */
	private NetworkDbHelper(Context context) {
		super(context, DATABASE_NAME, null, DATABASE_VERSION);
		this.context = context;
		identification = context.getSharedPreferences(PREFS_NAME, 0).getString(
				"identification", "N/A");
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * android.database.sqlite.SQLiteOpenHelper#onCreate(android.database.sqlite
	 * .SQLiteDatabase)
	 */
	@Override
	public void onCreate(SQLiteDatabase db) {

		// Create the schema if it is not there
		String sql = "CREATE TABLE IF NOT EXISTS " + TABLE_NAME_CONVERSATION
				+ " (" + CONVERSATION_ID + " INTEGER PRIMARY KEY, "
				+ CONVERSATION_KEY + " TEXT, " + CONVERSATION_START
				+ " INTEGER, " + CONVERSATION_END + " INTEGER, "
				+ CONVERSATION_OPEN + " INTEGER);";

		db.execSQL(sql);

		sql = "CREATE TABLE IF NOT EXISTS " + TABLE_NAME_PARTICIPANT + " ("
				+ PARTICIPANT_ID + " INTEGER PRIMARY KEY, "
				+ PARTICIPANT_CONVERSATION_ID + " INTEGER, "
				+ PARTICIPANT_IDENTIFICATION + " TEXT, "
				+ PARTICIPANT_IP_ADDRESS + " TEXT, " + PARTICIPANT_STATE
				+ " INTEGER, " + "FOREIGN KEY(" + PARTICIPANT_CONVERSATION_ID
				+ ") REFERENCES " + TABLE_NAME_CONVERSATION + "("
				+ CONVERSATION_ID + "));";

		db.execSQL(sql);

		sql = "CREATE TABLE IF NOT EXISTS " + TABLE_NAME_MESSAGE + " ("
				+ MESSAGE_ID + " INTEGER PRIMARY KEY, " + MESSAGE_MESSAGE
				+ " TEXT, " + MESSAGE_MESSAGE_TYPE + " INTEGER, "
				+ MESSAGE_SENDER_ID + " INTEGER, " + MESSAGES_SEQUENCE_NUMBER
				+ " INTEGER, " + MESSAGES_SOURCE_IP_ADDRESS + " TEXT, "
				+ MESSAGES_TIMESTAMP + " INTEGER, " + "FOREIGN KEY("
				+ MESSAGE_SENDER_ID + ") REFERENCES " + TABLE_NAME_PARTICIPANT
				+ "(" + PARTICIPANT_ID + "));";

		db.execSQL(sql);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * android.database.sqlite.SQLiteOpenHelper#onUpgrade(android.database.sqlite
	 * .SQLiteDatabase, int, int)
	 */
	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

		// drop the schema if there is a new version
		Log.w(TAG, "DB Upgrade from Version " + oldVersion + " to version "
				+ newVersion);
		db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME_CONVERSATION);
		db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME_PARTICIPANT);
		db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME_MESSAGE);
		onCreate(db);
	}

	/**
	 * Stores a message in the database
	 * 
	 * @param message
	 *            The message which should be inserted
	 * @return the row number which has been created
	 */
	public long insertMessage(Message message) {
		long rowId = -1;

		Log.d(TAG, "Inserting Message: " + message.toString());
		try {
			SQLiteDatabase db = getWritableDatabase();
			ContentValues values = new ContentValues();
			values.put(MESSAGE_MESSAGE, message.getMessage());
			values.put(MESSAGE_MESSAGE_TYPE, message.getMessageType());
			values.put(MESSAGE_SENDER_ID, getParticipantID(message.getSender()));
			values.put(MESSAGES_SEQUENCE_NUMBER, message.getSequenceNumber());
			values.put(MESSAGES_SOURCE_IP_ADDRESS, message.getSenderIPAddress());
			values.put(MESSAGES_TIMESTAMP, message.getTimestamp());
			//if participant exist
			if(getParticipantID(message.getSender())!=-1){
				rowId = db.insert(TABLE_NAME_MESSAGE, null, values);
			}
		} catch (SQLiteException e) {

		} finally {

		}
		return rowId;
	}

	/**
	 * Returns the the primary key of a participant based on the identification
	 * and the primary key of the conversation
	 * 
	 * @param participantIdentification
	 *            the identification of the participant
	 * @param conversationId
	 *            the conversation id which should be considered for searching
	 * @return the participant's primary key
	 */
	public long getParticipantID(String participantIdentification,
			long conversationId) {
		SQLiteDatabase db = getReadableDatabase();
		String sql = "SELECT * FROM participant p WHERE p.identification = '"
				+ participantIdentification + "' AND p.conversation_id = "
				+ conversationId;
		try{
			Cursor c = db.rawQuery(sql, null);
			c.moveToFirst();
			if(c.getCount()==0){
				return -1;
			}
			return c.getLong(c.getColumnIndex("_id"));
		} catch (Exception e){
			return -1;
		}
	}

	/**
	 * Returns the the primary key of a participant based on the identification.
	 * It will search in the currently open conversation.
	 * 
	 * @param participantIdentification
	 *            the identification of the participant
	 * @return the participant's primary key
	 */
	public long getParticipantID(String participantIdentification) {
		return getParticipantID(participantIdentification,
				getOpenConversationId());
	}

	/**
	 * Returns a Cursor object with the details of a given participant
	 * 
	 * @param participantId
	 *            the primary key of the participant
	 * @return the cursor object containing the details of the participant
	 */
	public Cursor queryParticipant(int participantId) {
		SQLiteDatabase db = getReadableDatabase();
		String sql = "SELECT * FROM participant p WHERE _id = " + participantId;
		Cursor c = db.rawQuery(sql, null);
		return c;
	}

	/**
	 * Inserts a participant into the database and relates it to a conversation
	 * 
	 * @param participantIdentification
	 *            the identification of the participant
	 * @param ipAddress
	 *            the IP address of the participant
	 * @param conversationId
	 *            the primary key of the conversation to which this participant
	 *            belongs
	 * @return the row number which has been created
	 */
	public long insertParticipant(String participantIdentification,
			String ipAddress, long conversationId) {
		long rowId = -1;

		SQLiteDatabase db = getWritableDatabase();
		String query = "SELECT * FROM " + TABLE_NAME_PARTICIPANT + " WHERE "
				+ PARTICIPANT_IDENTIFICATION + " = '"
				+ participantIdentification + "' AND "
				+ PARTICIPANT_CONVERSATION_ID + " = " + conversationId;
		Cursor c = db.rawQuery(query, null);
		if (c.getCount() == 0) {
			ContentValues values = new ContentValues();
			values.put(PARTICIPANT_IDENTIFICATION, participantIdentification);
			values.put(PARTICIPANT_CONVERSATION_ID, conversationId);
			values.put(PARTICIPANT_IP_ADDRESS, ipAddress);
			values.put(PARTICIPANT_STATE, 1);
			rowId = db.insert(TABLE_NAME_PARTICIPANT, null, values);
		} else {
			c.moveToLast();
			rowId = c.getLong(c.getColumnIndex(PARTICIPANT_ID));
			ContentValues values = new ContentValues();
			values.put(PARTICIPANT_STATE, 1);
			db.update(TABLE_NAME_PARTICIPANT, values, PARTICIPANT_ID + " = "
					+ rowId, null);
		}
		return rowId;
	}

	/**
	 * Inserts a participant into the database and relates it to the currently
	 * open conversation
	 * 
	 * @param participantIdentification
	 *            the identification of the participant
	 * @param ipAddress
	 *            the IP address of the participant
	 * @return the row number which has been created
	 */
	public long insertParticipant(String participantIdentification,
			String ipAddress) {
		return insertParticipant(participantIdentification, ipAddress,
				getOpenConversationId());
	}

	/**
	 * Assignes a given participant a new state
	 * 
	 * @param participantIdentification
	 *            the identification of the participant which should be updated
	 * @param newState
	 *            The new state as an integer
	 */
	public void updateParticipantState(String participantIdentification,
			int newState) {
		SQLiteDatabase db = getWritableDatabase();
		ContentValues values = new ContentValues();
		values.put(PARTICIPANT_STATE, newState);
		db.update(TABLE_NAME_PARTICIPANT, values, PARTICIPANT_IDENTIFICATION
				+ " = '" + participantIdentification + "'", null);
	}

	/**
	 * Returns a cursor containing all the messages of a given conversation
	 * 
	 * @param conversationId
	 *            the primary key of the conversation which should be queried
	 * @return the cursor object containing all the messages of a given
	 *         conversation
	 */
	public Cursor queryMessages(long conversationId) {
		SQLiteDatabase db = getReadableDatabase();

		String sql = "SELECT * FROM " + TABLE_NAME_PARTICIPANT + " p, "
				+ TABLE_NAME_CONVERSATION + " c, " + TABLE_NAME_MESSAGE
				+ " m WHERE m." + MESSAGE_SENDER_ID + " = p." + PARTICIPANT_ID
				+ " AND p." + PARTICIPANT_CONVERSATION_ID + " = c._id AND c."
				+ CONVERSATION_ID + " = " + conversationId + " ORDER BY "
				+ MESSAGES_TIMESTAMP + " ASC";
		Cursor c = db.rawQuery(sql, null);
		return c;
	}

	/**
	 * Returns a cursor containing all the messages of the currently open
	 * conversation
	 * 
	 * @return the cursor object containing all the messages of the currently
	 *         open conversation
	 */
	public Cursor queryMessages() {
		return queryMessages(getOpenConversationId());
	}

	/**
	 * Returns a cursor containing all the messages of myself in a given
	 * conversation
	 * 
	 * @param conversationId
	 *            the primary key of the conversation which should be queried
	 * @return the cursor object containing all the messages of myself in a
	 *         given conversation
	 */
	public Cursor queryMyMessages(long conversationId) {
		SQLiteDatabase db = getReadableDatabase();
		String myIdentification = context.getSharedPreferences(
				"network_preferences", 0).getString("identification", "N/A");
		String sql = "SELECT * FROM " + TABLE_NAME_PARTICIPANT + " p, "
				+ TABLE_NAME_CONVERSATION + " c, " + TABLE_NAME_MESSAGE
				+ " m WHERE m." + MESSAGE_SENDER_ID + " = p." + PARTICIPANT_ID
				+ " AND p.conversation_id = c._id AND c." + CONVERSATION_ID
				+ " = " + conversationId + " AND p."
				+ PARTICIPANT_IDENTIFICATION + " = '" + myIdentification
				+ "' ORDER BY " + MESSAGES_TIMESTAMP + " ASC;";
		Cursor c = db.rawQuery(sql, null);
		return c;
	}

	/**
	 * Returns a cursor containing all the messages of myself in the currently
	 * open conversation
	 * 
	 * @return the cursor object containing all the messages of myself in the
	 *         currently open conversation
	 */
	public Cursor queryMyMessages() {
		return queryMyMessages(getOpenConversationId());
	}

	/**
	 * Returns a cursor containing all the participants of a given conversation
	 * 
	 * @param conversationId
	 *            the primary key of the conversation which should be queried
	 * @return the cursor object containing all the participants of a given
	 *         conversation
	 */
	public Cursor queryParticipants(long conversationId) {
		SQLiteDatabase db = getReadableDatabase();
		String sql = "SELECT p." + PARTICIPANT_ID + ", p."
				+ PARTICIPANT_IDENTIFICATION + ", p." + PARTICIPANT_IP_ADDRESS
				+ ", p." + PARTICIPANT_STATE + " FROM "
				+ TABLE_NAME_PARTICIPANT + " p, " + TABLE_NAME_CONVERSATION
				+ " c WHERE p." + PARTICIPANT_CONVERSATION_ID + " = c."
				+ CONVERSATION_ID + " AND c." + CONVERSATION_ID + " = "
				+ conversationId;
		Cursor c = db.rawQuery(sql, null);
		return c;
	}

	/**
	 * Returns a cursor containing all the participants of the currently open
	 * conversation
	 * 
	 * @return the cursor object containing all the participants of the
	 *         currently open conversation
	 */
	public Cursor queryParticipants() {
		return queryParticipants(getOpenConversationId());
	}

	/**
	 * Opens a conversation with a certain key. If there is already a
	 * conversation with this key it will reopen this conversation.
	 * 
	 * @param key
	 *            The cipher key which should be used in this conversation
	 * @return the row number which has been created
	 */
	public long openConversation(String key) {
		long rowId = -1;
		closeConversation();

		SQLiteDatabase db = getReadableDatabase();
		String query = "SELECT * FROM " + TABLE_NAME_CONVERSATION + " WHERE "
				+ CONVERSATION_KEY + " = '" + key + "'";
		Cursor c = db.rawQuery(query, null);
		if (c.getCount() == 0) {
			db = getWritableDatabase();
			ContentValues values = new ContentValues();
			values.put(CONVERSATION_KEY, key);
			values.put(CONVERSATION_START, System.currentTimeMillis());
			values.put(CONVERSATION_OPEN, 1);
			rowId = db.insert(TABLE_NAME_CONVERSATION, null, values);
		} else {
			c.moveToLast();
			rowId = c.getLong(c.getColumnIndex(CONVERSATION_ID));
			ContentValues values = new ContentValues();
			values.put(CONVERSATION_OPEN, 1);
			db.update(TABLE_NAME_CONVERSATION, values, CONVERSATION_ID + " = "
					+ rowId, null);
		}

		c.close();
		return rowId;
	}

	/**
	 * Closes all the open conversations
	 */
	public void closeConversation() {
		SQLiteDatabase db = getWritableDatabase();
		ContentValues values = new ContentValues();
		values.put(CONVERSATION_OPEN, 0);
		values.put(CONVERSATION_END, System.currentTimeMillis());
		db.update(TABLE_NAME_CONVERSATION, values, CONVERSATION_OPEN + " = 1",
				null);
	}

	/**
	 * Returns the primary key of the currently open conversation
	 * 
	 * @return the primary key of the currently open conversation
	 */
	public long getOpenConversationId() {
		SQLiteDatabase db = getReadableDatabase();
		String query = "SELECT * FROM " + TABLE_NAME_CONVERSATION + " WHERE "
				+ CONVERSATION_OPEN + " = 1";
		try{
			Cursor c = db.rawQuery(query, null);
			if (c.getCount() == 0) {
				c.close();
				return -1;
			} else {
				c.moveToFirst();
				long conversationId = c.getLong(c.getColumnIndex(CONVERSATION_ID));
				c.close();
				return conversationId;
			}
		} catch (Exception e){
			return -1;
		}
	}

	/**
	 * 
	 * Returns the sequence number which should be used for my next message
	 * 
	 * @return the sequence number which should be used for my next message
	 */
	public int getNextSequenceNumber() {
		long conversationId = getOpenConversationId();

		SQLiteDatabase db = getReadableDatabase();
		String query = "select max(" + MESSAGES_SEQUENCE_NUMBER + ") from "
				+ TABLE_NAME_MESSAGE + " m, " + TABLE_NAME_PARTICIPANT
				+ " p where m." + MESSAGE_SENDER_ID + " = p." + PARTICIPANT_ID
				+ " and p." + PARTICIPANT_IDENTIFICATION + "='"
				+ identification + "' and p." + PARTICIPANT_CONVERSATION_ID
				+ " = " + conversationId + ";";

		Cursor c = db.rawQuery(query, null);
		c.moveToFirst();
		int nextSequenceNumber = c.getInt(0) + 1;
		c.close();
		return nextSequenceNumber;
	}

	/**
	 * Returns the cipher key of a given conversation
	 * 
	 * @param conversationId
	 *            the primary key of the conversation which should be queried
	 * @return the cipher key
	 */
	public String getCipherKey(long conversationId) {
		SQLiteDatabase db = getReadableDatabase();
		String query = "select " + CONVERSATION_KEY + " from "
				+ TABLE_NAME_CONVERSATION + " where " + CONVERSATION_ID + " = "
				+ conversationId;

		Cursor c = db.rawQuery(query, null);
		c.moveToFirst();
		String key = c.getString(0);
		c.close();
		return key;

	}

	/**
	 * Returns the cipher key of the currently open conversation
	 * 
	 * @return the cipher key
	 */
	public String getCipherKey() {
		return getCipherKey(getOpenConversationId());
	}

	/**
	 * 
	 * Returns the currently known sequence number of a given participant
	 * 
	 * @param identification
	 *            the identification of the participant
	 * @return the sequence number
	 */
	public int getCurrentParticipantSequenceNumber(String identification) {

		long conversationId = getOpenConversationId();
		int sequenceNumber = 0;

		SQLiteDatabase db = getReadableDatabase();
		String query = "SELECT max(" + MESSAGES_SEQUENCE_NUMBER + ") from "
				+ TABLE_NAME_MESSAGE + " m, " + TABLE_NAME_PARTICIPANT
				+ " p WHERE m." + MESSAGE_SENDER_ID + " = p." + PARTICIPANT_ID
				+ " and p." + PARTICIPANT_CONVERSATION_ID + " = "
				+ conversationId + " and p." + PARTICIPANT_IDENTIFICATION
				+ " = '" + identification + "';";

		Cursor c = db.rawQuery(query, null);
		if (c.getCount() == 0) {
			sequenceNumber = 0;
		} else {
			c.moveToFirst();
			sequenceNumber = c.getInt(0);
			c.close();
		}
		return sequenceNumber;
	}

	/**
	 * Checks whether a participant is already known in the database
	 * 
	 * @param identification
	 *            the identification of the participant
	 * @return true if known, false if unknown
	 */
	public boolean isParticipantKnown(String identification) {

		boolean participantKnown = false;

		SQLiteDatabase db = getReadableDatabase();
		String query = "SELECT * FROM " + TABLE_NAME_PARTICIPANT
				+ " p WHERE p." + PARTICIPANT_IDENTIFICATION + " = '"
				+ identification + "';";
		Cursor c = db.rawQuery(query, null);
		if (c.getCount() > 0) {
			participantKnown = true;
		}
		c.close();
		return participantKnown;
	}

	/**
	 * Checks whether a message is already in the database
	 * 
	 * @param msg
	 *            the message which needs to be checked
	 * @return true if already saved, false otherwise
	 */
	public boolean isMessageInDatabase(Message msg) {
		boolean messageInDatabase = false;
		try{ 
			SQLiteDatabase db = getReadableDatabase();
			String query = "SELECT * FROM " + TABLE_NAME_MESSAGE + " m, "
					+ TABLE_NAME_PARTICIPANT + " p WHERE p."
					+ PARTICIPANT_IDENTIFICATION + " = '" + msg.getSender()
					+ "' AND m." + MESSAGES_SEQUENCE_NUMBER + " = "
					+ msg.getSequenceNumber() + " AND m." + MESSAGE_SENDER_ID
					+ " = p." + PARTICIPANT_ID + " AND p." + PARTICIPANT_CONVERSATION_ID + " = "  + getOpenConversationId() + ";";
			Cursor c = db.rawQuery(query, null);
			if (c.getCount() > 0) {
				messageInDatabase = true;
			}
			c.close();
			return messageInDatabase;
		} catch (Exception e){
			return false;
		}
	}

	/**
	 * Housekeeping function, cleans all conversations out of the database
	 */
	public void cleanDatabase() {
		SQLiteDatabase db = getWritableDatabase();
		db.execSQL("DELETE FROM " + TABLE_NAME_CONVERSATION);
		db.execSQL("DELETE FROM " + TABLE_NAME_PARTICIPANT);
		db.execSQL("DELETE FROM " + TABLE_NAME_MESSAGE);
	}
}
