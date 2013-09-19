package ch.bfh.instacircle.service;

import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;

public class CipherHandler {
	
	private final byte[] rawSeed;
	
	public CipherHandler (final byte[] rawSeed){
		this.rawSeed = rawSeed;
	}
	
	/**
	 * Method which de data using a key
	 * 
	 * @param clear
	 *            The data which should be encrypted
	 * @return The encrypted bytes
	 */
	public byte[] encrypt(byte[] clear) {
		Cipher cipher;
		MessageDigest digest;
		byte[] encrypted = null;
		try {
			// we need a 256 bit key, let's use a SHA-256 hash of the rawSeed
			// for that
			digest = MessageDigest.getInstance("SHA-256");
			digest.reset();

			SecretKeySpec skeySpec = new SecretKeySpec(digest.digest(rawSeed),
					"AES");
			cipher = Cipher.getInstance("AES");
			cipher.init(Cipher.ENCRYPT_MODE, skeySpec);
			encrypted = cipher.doFinal(clear);
		} catch (NoSuchAlgorithmException e) {
			return null;
		} catch (NoSuchPaddingException e) {
			return null;
		} catch (InvalidKeyException e) {
			return null;
		} catch (IllegalBlockSizeException e) {
			return null;
		} catch (BadPaddingException e) {
			return null;
		}

		return encrypted;
	}
	
	/**
	 * 
	 * Method which encrypts data using a key
	 * 
	 * @param encrypted
	 *            The data to be decrypted
	 * @return A byte array of the decrypted data if decryption was successful,
	 *         null otherwise
	 */
	public byte[] decrypt(byte[] encrypted) {
		Cipher cipher;
		MessageDigest digest;
		byte[] decrypted = null;
		try {
			// we need a 256 bit key, let's use a SHA-256 hash of the rawSeed
			// for that
			digest = MessageDigest.getInstance("SHA-256");
			digest.reset();
			SecretKeySpec skeySpec = new SecretKeySpec(digest.digest(rawSeed),
					"AES");
			cipher = Cipher.getInstance("AES");
			cipher.init(Cipher.DECRYPT_MODE, skeySpec);
			decrypted = cipher.doFinal(encrypted);
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
			return null;
		} catch (NoSuchPaddingException e) {
			e.printStackTrace();
			return null;
		} catch (InvalidKeyException e) {
			e.printStackTrace();
			return null;
		} catch (IllegalBlockSizeException e) {
			e.printStackTrace();
			return null;
		} catch (BadPaddingException e) {
			e.printStackTrace();
			return null;
		}
		return decrypted;
	}
}
