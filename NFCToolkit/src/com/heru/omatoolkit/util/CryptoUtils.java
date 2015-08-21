package com.heru.omatoolkit.util;

import java.io.ByteArrayOutputStream;
import java.security.GeneralSecurityException;
import java.util.Hashtable;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.DESKeySpec;
import javax.crypto.spec.DESedeKeySpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import android.content.Context;


public class CryptoUtils {
	private static byte[] iv_zero = { (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
		(byte) 0x00 };
	
	public static byte[] convertHexStringToByteArray(String s)
	{
		if (s == null)
			return null;

		int stringLength = s.length();
		if ((stringLength & 0x1) != 0)
		{
			throw new IllegalArgumentException(
											"convertHexStringToByteArray requires an even number of hex characters");
		}

		byte[] b = new byte[stringLength / 2];

		for (int i = 0, j = 0; i < stringLength; i += 2, j++)
		{
			int high = charToNibble(s.charAt(i));
			int low = charToNibble(s.charAt(i + 1));
			b[j] = (byte) ((high << 4) | low);
		}
		return b;
	}
	
	public static int charToNibble(char c)
	{
		if ('0' <= c && c <= '9')
		{
			return c - '0';
		}
		else if ('a' <= c && c <= 'f')
		{
			return c - 'a' + 0xa;
		}
		else if ('A' <= c && c <= 'F')
		{
			return c - 'A' + 0xa;
		}
		else
		{
			throw new IllegalArgumentException("Invalid hex character: " + c);
		}
	}
	
	 public static String convertBytesToHexString(byte[] bytes)
	 {
		 if(null == bytes) return "";
		 StringBuffer str = new StringBuffer("");
		 for(int i=0; i<bytes.length; i++){
			 int hex = bytes[i] & 0xFF;
			 if(hex < 0x10) str.append('0');
			 str.append( Integer.toHexString(hex) );
		 }
		 return str.toString();
	 }
	 
	public static byte[] encryptTripleDES(byte[] key, byte[] data, byte[] iv)
	{
			if (key.length < 24)
			{
				byte[] master = key;
				key = new byte[24];
				System.arraycopy(master, 0, key, 0, master.length);
				System.arraycopy(master, 0, key, master.length, key.length
					- master.length);
				// DLog.d("SecureSession.encryptData KEY: "+convertBytesToHexString(key));
			}
			if (iv == null)
			{
				iv = new byte[8];
			}
			try
			{
				DESedeKeySpec keySpec = new DESedeKeySpec(key);
				SecretKey secret = SecretKeyFactory.getInstance("DESede")
					.generateSecret(keySpec);

				Cipher cipher = Cipher.getInstance("DESede/CBC/NoPadding");
				cipher.init(Cipher.ENCRYPT_MODE, secret, new IvParameterSpec(iv));
				return cipher.doFinal(data);
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
			return null;
	}
	
	public static byte[] encryptTripleDESECB(byte[] key, byte[] data)
	{
			if (key.length < 24)
			{
				byte[] master = key;
				key = new byte[24];
				System.arraycopy(master, 0, key, 0, master.length);
				System.arraycopy(master, 0, key, master.length, key.length
					- master.length);
				// DLog.d("SecureSession.encryptData KEY: "+convertBytesToHexString(key));
			}
			try
			{
				DESedeKeySpec keySpec = new DESedeKeySpec(key);
				SecretKey secret = SecretKeyFactory.getInstance("DESede")
					.generateSecret(keySpec);

				Cipher cipher = Cipher.getInstance("DESede/ECB/NoPadding");
				cipher.init(Cipher.ENCRYPT_MODE, secret);
				return cipher.doFinal(data);
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
			return null;
	}
	
	public static byte[] encryptDES(byte[] key, byte[] data, byte [] iv)
	{
		try
		{
			DESKeySpec keySpec = new DESKeySpec(key);
			SecretKey secret = SecretKeyFactory.getInstance("DES")
				.generateSecret(keySpec);

			Cipher cipher = Cipher.getInstance("DES/CBC/NoPadding");
			if (iv == null)
			{
				cipher.init(Cipher.ENCRYPT_MODE, secret, new IvParameterSpec(
						new byte[8]));
			}
			else
			{
				cipher.init(Cipher.ENCRYPT_MODE, secret, new IvParameterSpec(iv));
			}
			return cipher.doFinal(data);
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		return null;
	}

	public static byte[] encryptDESECB(byte[] key, byte[] data)
	{
		try
		{
			DESKeySpec keySpec = new DESKeySpec(key);
			SecretKey secret = SecretKeyFactory.getInstance("DES")
				.generateSecret(keySpec);

			Cipher cipher = Cipher.getInstance("DES/ECB/NoPadding");
			cipher.init(Cipher.ENCRYPT_MODE, secret);
			return cipher.doFinal(data);
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		return null;
	}
	
	public static Hashtable<String, byte[]> getSessionKeys(Context mContext, byte[] hostRandom,
			byte[] cardRandom, byte[] cardMAC)
	{
		byte[] headEnc = new byte[] { 0x01, (byte) 0x82 };
		byte[] headMac = new byte[] { 0x01, 0x01 };
		byte[] headKek = new byte[] { 0x01, (byte) 0x81 };

		byte[] data = new byte[16];
		// Append Sequence Counter
		System.arraycopy(cardRandom, 0, data, 2, 2);

		byte[] mMasterEnc = convertHexStringToByteArray(Data.getSettings(mContext, Data.KEY_ENC));
		byte[] mMasterMac = convertHexStringToByteArray(Data.getSettings(mContext, Data.KEY_MAC));
		byte[] mMasterKek = convertHexStringToByteArray(Data.getSettings(mContext, Data.KEY_KEK));
		
		// Append ENC data header
		System.arraycopy(headEnc, 0, data, 0, 2);
		// DLog.d("SecureSession.getSessionKeys DATA: "+convertBytesToHexString(derivationData));
		byte[] keyEnc = encryptTripleDES(mMasterEnc, data, null);
			
		// Append MAC data header
		System.arraycopy(headMac, 0, data, 0, 2);
		// DLog.d("SecureSession.getSessionKeys DATA: "+convertBytesToHexString(derivationData));
		byte[] keyMac = encryptTripleDES(mMasterMac, data, null);
			
		// Append KEK data header
		System.arraycopy(headKek, 0, data, 0, 2);
		// DLog.d("SecureSession.getSessionKeys DATA: "+convertBytesToHexString(derivationData));
		byte[] keyKek = encryptTripleDES(mMasterKek, data, null);
			
		Hashtable<String, byte[]> sessionKeys = new Hashtable<String, byte[]>(3);
		sessionKeys.put("session_enc", keyEnc);
		sessionKeys.put("session_mac", keyMac);
		sessionKeys.put("session_kek", keyKek);
		return sessionKeys;
	}
	
	public static byte[] verifyCrypto(byte[] key_enc, byte[] host_random,
			byte[] card_random, byte[] card_crypto) throws Exception
	{
		// Generate card cryptogram
		byte[] data = new byte[host_random.length + card_random.length + 8];
		System.arraycopy(host_random, 0, data, 0, host_random.length);
		System.arraycopy(card_random, 0, data, host_random.length,
			card_random.length);
		data[host_random.length + card_random.length] = (byte) 0x80;
//		mListener.onProgressNotification("SecureSession.verifyCrypto DATA: "+ convertBytesToHexString(data));			

		byte[] last_eight = new byte[8];
		byte[] signature = encryptTripleDES(key_enc, data, null);
		System.arraycopy(signature, signature.length - 8, last_eight, 0, 8);
//		mListener.onProgressNotification("SecureSession.verifyCrypto LAST 8: "+ convertBytesToHexString(last_eight));

		// Verify card cryptogram
		int i = 0;
		while (i < last_eight.length && card_crypto[i] == last_eight[i])
			i++;
		if (i != card_crypto.length)
			throw new Exception("Invalid Card Cryptogram");

		// Generate host cryptogram
		System.arraycopy(card_random, 0, data, 0, card_random.length);
		System.arraycopy(host_random, 0, data, card_random.length,
			host_random.length);
		// DLog.d("SecureSession.verifyCrypto DATA: "+convertBytesToHexString(data));

		signature =  encryptTripleDES(key_enc, data, null);
		System.arraycopy(signature, signature.length - 8, last_eight, 0, 8);

		signature = null;
		data = null;

		return last_eight;
	}
	
	
	public static byte[] generateMAC(byte[] key_mac, byte[] first_four, byte[] data,  byte[] iv)
	{
		int length = first_four.length + 1 + data.length + 1;
		
		int len = ((length + 7) / 8) * 8;
		byte[] apdu = new byte[len];
		// CLA INS P1 P2
		System.arraycopy(first_four, 0, apdu, 0, first_four.length);
		// Secure channel
		apdu[0] = (byte) (apdu[0] | 0x04);
		// Data length + MAC length
		apdu[first_four.length] = (byte)(data.length + 8);
		// Data block
		System.arraycopy(data, 0, apdu, first_four.length + 1, data.length);
		// Start Padding
		apdu[length - 1] = (byte) 0x80;
		
		byte[] first  = new byte[apdu.length - 8];
		System.arraycopy(apdu, 0, first, 0, apdu.length - 8);
		
//		System.out.println("SecureSession.generateMAC DATA: "+convertBytesToHexString(apdu));
		byte[] signature = encryptDES(key_mac, first, iv);
		first = new byte[8];
		if(signature.length >= 8) System.arraycopy(signature, signature.length - 8, first, 0, 8);
//		System.out.println("SecureSession.generateFirst FIRST: "+convertBytesToHexString(first));
		
		byte[] last_eight = new byte[8];
		if(apdu.length >= 8) System.arraycopy(apdu, apdu.length - 8, last_eight, 0, 8);
//		System.out.println("SecureSession.lastEight DATA: "+convertBytesToHexString(last_eight));
		
		if(CryptoUtils.convertBytesToHexString(first).equalsIgnoreCase("0000000000000000"))
			signature = encryptTripleDES(key_mac, last_eight, iv);
		else
			signature = encryptTripleDES(key_mac, last_eight, first);
		if(signature.length >= 8) System.arraycopy(signature, signature.length - 8, last_eight, 0, 8);
		return last_eight;
	}
	
	public static byte[] encodeKey(byte SCP, String key, String _KEKKey) throws Exception 
	{
		ByteArrayOutputStream bytearrayoutputstream = new ByteArrayOutputStream();
		// SCP01
		if(SCP == 1)
			bytearrayoutputstream.write(0x81);
		else
			bytearrayoutputstream.write(0x80);
		
		bytearrayoutputstream.write(0x10);
		Cipher cipher = Cipher.getInstance("DESede/ECB/NoPadding");
		SecretKeySpec kKey = new SecretKeySpec(convertHexStringToByteArray(_KEKKey), "DESede");
		cipher.init(1, kKey);
		bytearrayoutputstream.write(cipher.doFinal(convertHexStringToByteArray(key), 0, 16));
		bytearrayoutputstream.write(3);
		SecretKeySpec ky = new SecretKeySpec(convertHexStringToByteArray(key), "DESede");
		cipher.init(1, ky);
		byte abyte0[] = cipher.doFinal(iv_zero);
		bytearrayoutputstream.write(abyte0, 0, 3);
		return bytearrayoutputstream.toByteArray();
	}
	

}
