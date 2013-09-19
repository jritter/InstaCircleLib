
package ch.bfh.instacircle;

import java.io.Serializable;

/**
 * This class implements the serializable Messages which are being transfered
 * between the participants
 * 
 * @author Juerg Ritter (rittj1@bfh.ch)
 */
public class Message implements Serializable {

	private static final long serialVersionUID = 1L;

	// Defining message types as constants

	/**
	 * Message type which transfers conversation content
	 */
	public static final int MSG_CONTENT = 1;

	/**
	 * Indicates a participant joining into the conversation
	 */
	public static final int MSG_MSGJOIN = 2;

	/**
	 * Indicates a participant leaving the conversation
	 */
	public static final int MSG_MSGLEAVE = 3;

	/**
	 * Used for asking a participant to resend messages
	 */
	public static final int MSG_RESENDREQ = 4;

	/**
	 * Used to respond to a resend request. Such a resend response contains an
	 * array of the messages for which the requester asked
	 */
	public static final int MSG_RESENDRES = 5;

	/**
	 * Can be used to discover who is around in the network, this is mainly used
	 * after the join of a participant
	 */
	public static final int MSG_WHOISTHERE = 6;

	/**
	 * Response to the WHOISTHERE request. It will be sent directly to the
	 * requestor containing the current sequence number
	 */
	public static final int MSG_IAMHERE = 7;
	
	/**
	 * Content of message of type MSG_MSGLEAVE if we want the DB to be deleted on leave
	 */
	public static final String DELETE_DB = "deleteDB";

	private String message;
	private String sender;
	private String senderIPAddress;
	private int sequenceNumber = -1;
	private int messageType;
	private long timestamp;

	/**
	 * @param message
	 *            the content of the message
	 * @param messageType
	 *            the type of the message as int (constant definitions)
	 * @param sender
	 *            the sender of the message
	 */
	public Message(String message, int messageType, String sender) {
		this.message = message;
		this.messageType = messageType;
		this.sender = sender;
	}

	/**
	 * @param message
	 *            the content of the message
	 * @param messageType
	 *            the type of the message as int (constant definitions)
	 * @param sender
	 *            the sender of the message
	 * @param sequenceNumber
	 *            the sequence number which should be used to send this message
	 */
	public Message(String message, int messageType, String sender,
			int sequenceNumber) {
		this.message = message;
		this.messageType = messageType;
		this.sender = sender;
		this.sequenceNumber = sequenceNumber;
		this.timestamp = System.currentTimeMillis();
	}

	/**
	 * Returns the message
	 * 
	 * @return the message
	 */
	public String getMessage() {
		return message;
	}

	/**
	 * Sets the sequence number
	 * 
	 * @param sequenceNumber
	 *            the sequence number which should be defined
	 */
	public void setSequenceNumber(int sequenceNumber) {
		this.sequenceNumber = sequenceNumber;
	}

	/**
	 * Returns the sequence number
	 * 
	 * @return the sequence number
	 */
	public int getSequenceNumber() {
		return sequenceNumber;
	}

	/**
	 * Returns the sender of the message
	 * 
	 * @return the sender of the message
	 */
	public String getSender() {
		return sender;
	}

	/**
	 * Returns the message type
	 * 
	 * @return the message type
	 */
	public int getMessageType() {
		return messageType;
	}

	/**
	 * Returns the IP address of the sender
	 * 
	 * @return the IP address of the sender
	 */
	public String getSenderIPAddress() {
		return senderIPAddress;
	}

	/**
	 * Sets the IP address of the sender
	 * 
	 * @param senderIPAddress
	 *            the IP address which should be defined
	 */
	public void setSenderIPAddress(String senderIPAddress) {
		this.senderIPAddress = senderIPAddress;
	}

	/**
	 * Returns the timestamp of the message
	 * 
	 * @return the timestamp of the message
	 */
	public long getTimestamp() {
		return timestamp;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "BroadcastMessage [message=" + message + ", sequenceNumber="
				+ sequenceNumber + ", sender=" + sender + ", messageType="
				+ messageType + ", senderIPAddress=" + senderIPAddress + "]";
	}
	
	@Override
	public boolean equals(Object o) {
		if(o==null)return false;
		if(!(o instanceof Message)) return false;
		Message m = (Message)o;
		if(this.message==null || m.message==null) return false;
		if(this.sender==null || m.sender==null) return false;
		if(this.senderIPAddress==null || m.senderIPAddress==null) return false;
		
		return this.message.equals(m.message) && this.messageType==m.messageType
				&& this.sender.equals(m.sender) && this.senderIPAddress.equals(m.senderIPAddress);
	}

}
