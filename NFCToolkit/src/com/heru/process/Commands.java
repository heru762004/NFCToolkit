package com.heru.process;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Hashtable;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.simalliance.openmobileapi.Channel;

import com.heru.omatoolkit.ProcessListener;
import com.heru.omatoolkit.cap.AID;
import com.heru.omatoolkit.cap.CapFile;
import com.heru.omatoolkit.util.CryptoUtils;
import com.heru.omatoolkit.util.DFHandler;
import com.heru.omatoolkit.util.Data;
import com.heru.omatoolkit.util.Utils;
import com.otiglobal.copni.Copni;
import com.otiglobal.copni.exception.CopniAccessControlException;
import com.otiglobal.copni.exception.CopniCommunicationException;

import android.content.Context;
import android.service.textservice.SpellCheckerService.Session;

public class Commands 
{
	public static final int defaultLoadSize = 255;
	
	private Context mContext;
	private boolean isInitUpdateCommand;
	private String mRandomData;
	private Hashtable<String, byte[]> mSession;
	private ProcessListener mListener;
	private byte[] mLastMac;
	private String mAID;
	private String mUseReader;
	
	private Channel channel = null;
	private DFHandler handler = null;
	private Copni copni = null;
	private boolean isAppendLe = false;
	
	private byte SCP;
	
	private byte[] arrayPAD = { (byte) 0x80, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
			(byte) 0x00 };
	
	public Commands(Context _context, String useReader, String randomData, String _aid, ProcessListener listener)
	{
		mContext = _context;
		mRandomData = randomData;
		mListener = listener;
		mUseReader = useReader;
		mAID = _aid;
	}
	
	public String generateCommand(String cmd) throws Exception
	{
		String result = "";
		String le = "";
		isInitUpdateCommand = false;
		if(Data.getSettings(mContext, Data.KEY_SECURE_CHANNEL).equalsIgnoreCase("1"))
		{
			if(cmd.equalsIgnoreCase("80500000"))
			{
				this.isInitUpdateCommand = true;
				cmd = generateInitUpdateCommand();
			}
			else if(cmd.equalsIgnoreCase("84820100"))
			{
				cmd = generateExtAuthCommand((byte)1);
			}
			else if(cmd.equalsIgnoreCase("84820300")) {
				cmd = generateExtAuthCommand((byte)3);
			}
			else if(cmd.toLowerCase().startsWith("putkey")) {
				String putKeys = cmd.replace("putkey", "");
				// putKeys[0]
				// 1 - Add key
				// 2 - Modify key
				boolean isAdd = true;
				if(putKeys.charAt(0) == '2') {
					isAdd = false;
				}
				String newKeys[] = new String[3];
				newKeys[0] = Data.getSettings(mContext, Data.KEY_NEW_ENC);
				newKeys[1] = Data.getSettings(mContext, Data.KEY_NEW_MAC);
				newKeys[2] = Data.getSettings(mContext, Data.KEY_NEW_KEK);
				// putKeys[1] - keySet
				int keySet = Integer.parseInt(putKeys.charAt(1)+"");
				mListener.onProgressNotification("isAdd = " + isAdd);
				mListener.onProgressNotification("keySet = " + keySet);
				cmd = generatePutKey(newKeys, isAdd, keySet);
			}
			else if(cmd.startsWith("loadapplet"))
			{
				String cmdTemp = cmd.replaceAll("loadapplet", "");
				loadData(cmdTemp);
			}
			else
			{
				isAppendLe = false;
				if(cmd.contains("|"))
				{
					isAppendLe = true;
					String tmp [] = cmd.split("\\|");
					if(tmp.length > 0)
					{
						cmd = tmp[0];
						le = tmp[1];
					}
				}
				cmd = generateSecureCommand(cmd);
			}
		}
		else 
		{
			if(cmd.equalsIgnoreCase("80500000"))
			{
				this.isInitUpdateCommand = true;
				cmd = generateInitUpdateCommand();
			}
			else if (cmd.equalsIgnoreCase("84820000"))
			{
				cmd = generateExtAuthCommand((byte)0);
			}
			else
			{
				isAppendLe = false;
				if(cmd.contains("|"))
				{
					isAppendLe = true;
					String tmp [] = cmd.split("\\|");
					if(tmp.length > 0)
					{
						cmd = tmp[0];
						le = tmp[1];
					}
				}
			}
		}
		if(isAppendLe)
		{
			cmd = cmd + le;
		}
		result = cmd;
		return result;
	}
	
	private String generateInitUpdateCommand()
	{
		String result = "";
		int mKeyVersion = Integer.parseInt(Data.getSettings(mContext, Data.KEY_VERSION));
		byte[] initCommand = new byte[] { (byte) 0x80, 0x50,
				(byte) (mKeyVersion & 0xFF), 0x00 };
		byte[] randData = CryptoUtils.convertHexStringToByteArray(mRandomData);
		byte[] command = new byte[13];
		System.arraycopy(initCommand, 0, command, 0, initCommand.length);
		command[initCommand.length] = (byte) randData.length;
		System.arraycopy(randData, 0, command, initCommand.length + 1,
				randData.length);
		result = CryptoUtils.convertBytesToHexString(command);
		result += "00";
		return result;
	}
	
	private String generateExtAuthCommand(byte commandMac)
	{
		String result = "";
		byte[] resp = CryptoUtils.convertHexStringToByteArray(Data.getSettings(mContext, Data.KEY_LAST_RESPONSE));
		byte[] cardRandom = new byte[8];
		System.arraycopy(resp, 12, cardRandom, 0, 8);

		byte[] cardCrypto = new byte[8];
		System.arraycopy(resp, 12 + 8, cardCrypto, 0, 8);
		byte[] randData = CryptoUtils.convertHexStringToByteArray(mRandomData);
		mSession = CryptoUtils.getSessionKeys(mContext, randData, cardRandom, cardCrypto);
		mListener.onProgressNotification("Key ENC = " + Data.getSettings(mContext, Data.KEY_ENC));
		mListener.onProgressNotification("Key MAC = " + Data.getSettings(mContext, Data.KEY_MAC));
		mListener.onProgressNotification("Key KEK = " + Data.getSettings(mContext, Data.KEY_KEK));
		mListener.onProgressNotification("Session ENC = " + CryptoUtils.convertBytesToHexString(mSession.get("session_enc")));
		mListener.onProgressNotification("Session MAC = " + CryptoUtils.convertBytesToHexString(mSession.get("session_mac")));
		mListener.onProgressNotification("Session KEK = " + CryptoUtils.convertBytesToHexString(mSession.get("session_kek")));
//		mListener.onProgressNotification("CARD CRYPTO : " + CryptoUtils.convertBytesToHexString(cardCrypto));
		byte[] host_crypto = null;
		try
		{
			host_crypto = CryptoUtils.verifyCrypto(mSession.get("session_enc"),
				randData, cardRandom, cardCrypto);
		}
		catch (Exception e)
		{
			mListener.onProgressNotification("Verify Crypto Failed : " + e.getMessage());
			return "";
		}
		
		
		mLastMac = CryptoUtils.generateMAC(mSession.get("session_mac"), new byte[] { (byte) 0x84, (byte) 0x82, commandMac, 0x00 }, host_crypto, null);
		byte[] extauth = new byte[] { (byte) 0x84, (byte) 0x82, commandMac, 0x00, (byte) (host_crypto.length + mLastMac.length) };
		byte[] command = new byte[extauth.length + host_crypto.length + mLastMac.length];
		System.arraycopy(extauth, 0, command, 0, extauth.length);
		System.arraycopy(host_crypto, 0, command, extauth.length, host_crypto.length);
		System.arraycopy(mLastMac, 0, command, extauth.length + host_crypto.length, mLastMac.length);
		result = CryptoUtils.convertBytesToHexString(command);
		return result;
	}
	
	private String generateSecureCommand(String cmd)
	{
		String result = "";
		if(cmd.toLowerCase().startsWith("delete"))
		{
			String applicationId = cmd.replace("delete", "");
			int lengthAID = applicationId.length();
			int ld = (lengthAID / 2);
			String num1 = Integer.toHexString(ld + 2);
			if(num1.length() == 1)
			{
				num1 = 0 + num1;
			}
			String num2 = Integer.toHexString(ld);
			if(num2.length() == 1)
			{
				num2 = 0 + num2;
			}
			String finalCommand = "80E40000"+num1+"4F"+num2+applicationId;
			cmd = finalCommand;
			String claInsP1P2_str = "84" + cmd.substring(2, 8);
			String aid_str = cmd.substring(14, cmd.length());
			byte[] aid = CryptoUtils.convertHexStringToByteArray(aid_str);
			byte[] del = CryptoUtils.convertHexStringToByteArray(claInsP1P2_str + cmd.substring(8, 14));
			del[4] = (byte) (2 + aid.length);
			del[6] = (byte) (aid.length);
			byte[] nextIV = generateNextIV();
			byte[] command = new byte[7 + aid.length + 8];
			System.arraycopy(del, 0, command, 0, del.length);
			System.arraycopy(aid, 0, command, del.length, aid.length);
			int index = del.length + aid.length;
			byte data[] = new byte[command.length - 5 - 8];
			System.arraycopy(command, 5, data, 0, data.length);
			mLastMac = CryptoUtils.generateMAC(mSession.get("session_mac"), CryptoUtils.convertHexStringToByteArray(claInsP1P2_str), data, nextIV);
			System.arraycopy(mLastMac, 0, command, index, 8);
			command[4] = (byte) (command[4] + 8);
			return CryptoUtils.convertBytesToHexString(command);
		}
		else if(cmd.toLowerCase().startsWith("putkey"))
		{
			boolean isAdd = false;
			if(cmd.toLowerCase().startsWith("putkey1"))
			{
				isAdd = true;
			}
			String newKeys[] = new String[3];
			newKeys[0] = Data.getSettings(mContext, Data.KEY_NEW_ENC);
			newKeys[1] = Data.getSettings(mContext, Data.KEY_NEW_MAC);
			newKeys[2] = Data.getSettings(mContext, Data.KEY_NEW_KEK);
			mListener.onProgressNotification("New ENC Key = " + newKeys[0]);
			mListener.onProgressNotification("New MAC Key = " + newKeys[1]);
			mListener.onProgressNotification("New KEK Key = " + newKeys[2]);
			return generatePutKey(newKeys, isAdd, 1);
		}
		else if(cmd.toLowerCase().startsWith("00ca00cf0a"))
		{
			return cmd;
		}
		else if(cmd.toLowerCase().startsWith("80e60c00"))
		{
			String claInsP1P2_str = "84" + cmd.substring(2, 8);
			String aid_str = cmd.substring(10, cmd.length());
			byte[] headAPDU = CryptoUtils.convertHexStringToByteArray(claInsP1P2_str);
			byte[] bodyAPDU = CryptoUtils.convertHexStringToByteArray(aid_str);
			byte[] command = new byte[headAPDU.length + bodyAPDU.length + 1 + 8];
			System.arraycopy(headAPDU, 0, command, 0, headAPDU.length);
			command[headAPDU.length] = (byte)(bodyAPDU.length + 8);
			System.arraycopy(bodyAPDU, 0, command, headAPDU.length + 1, bodyAPDU.length);
			int index = headAPDU.length + 1 + bodyAPDU.length;
			byte data[] = new byte[command.length - 5 - 8];
			System.arraycopy(command, 5, data, 0, data.length);
			byte[] nextIV = generateNextIV();
			mLastMac = CryptoUtils.generateMAC(mSession.get("session_mac"), CryptoUtils.convertHexStringToByteArray(claInsP1P2_str), data, nextIV);
			System.arraycopy(mLastMac, 0, command, index, 8);
			return CryptoUtils.convertBytesToHexString(command);
		}
		else if(cmd.toLowerCase().startsWith("80"))
		{
			byte tmp[] = CryptoUtils.convertHexStringToByteArray(cmd);
			byte buffer[] = new byte[tmp.length + 8];
			System.arraycopy(tmp, 0, buffer, 0, tmp.length);
			buffer[0] = (byte)0x84;
			byte next_iv[] = generateNextIV();
			byte data[] = new byte[tmp.length - 5];
			try {
				System.arraycopy(tmp, 5, data, 0, data.length);
			} catch (Exception e) {
				// TODO: handle exception
				System.arraycopy(tmp, 5, data, 0, tmp.length);
			}
			byte first_four[] = new byte[4];
			System.arraycopy(buffer, 0, first_four, 0, 4);
			mLastMac = CryptoUtils.generateMAC(mSession.get("session_mac"), first_four, data, next_iv);
			int index = buffer.length - 8;
			System.arraycopy(mLastMac, 0, buffer, index, 8);
			buffer[4] = (byte) (data.length + 8);
			return CryptoUtils.convertBytesToHexString(buffer);
		}
		else if(cmd.toLowerCase().startsWith("84"))
		{
			byte buffer[] = CryptoUtils.convertHexStringToByteArray(cmd);
			byte next_iv[] = generateNextIV();
			byte data[] = new byte[buffer.length - 5 - 8];
			System.arraycopy(buffer, 5, data, 0, data.length);
			byte first_four[] = new byte[4];
			System.arraycopy(buffer, 0, first_four, 0, 4);
			mLastMac = CryptoUtils.generateMAC(mSession.get("session_mac"), first_four, data, next_iv);
			int index = buffer.length - 8;
			System.arraycopy(mLastMac, 0, buffer, index, 8);
			return CryptoUtils.convertBytesToHexString(buffer);
		}
		return result;
	}
	
	protected String generateInstallLoad(String loadFileAID) throws Exception
	{
		byte[] cardMngr = CryptoUtils.convertHexStringToByteArray(mAID);
		byte[] loadArgs = new byte[] { 0x00, 0x00, 0x00 };
		byte[] loadFile = CryptoUtils.convertHexStringToByteArray(loadFileAID);
		byte[] apdu = new byte[] {
			(byte) 0x84,
			(byte) 0xE6,
			0x02,
			0x00,
			(byte) (1 + loadFile.length + 1 + cardMngr.length + loadArgs.length + 8) };
		int index = apdu.length;
		byte[] command = new byte[apdu.length + apdu[apdu.length - 1]];
		System.arraycopy(apdu, 0, command, 0, index);

		command[index] = (byte) loadFile.length;
		System.arraycopy(loadFile, 0, command, index + 1, command[index]);
		index += 1 + loadFile.length;

		command[index] = (byte) cardMngr.length;
		System.arraycopy(cardMngr, 0, command, index + 1, command[index]);
		index += 1 + cardMngr.length;
		
		System.arraycopy(loadArgs, 0, command, index, loadArgs.length);
		index += loadArgs.length;
		
		byte next_iv[] = generateNextIV();
		byte data[] = new byte[command.length - 5 - 8];
		System.arraycopy(command, 5, data, 0, data.length);
		mLastMac = CryptoUtils.generateMAC(mSession.get("session_mac"), new byte[] {(byte) 0x84, (byte) 0xE6, 0x02, 0x00}, data, next_iv);
		System.arraycopy(mLastMac, 0, command, index, 8);
		
		return CryptoUtils.convertBytesToHexString(command);
	}
	
	public void loadData(String fileName) throws Exception
	{
		String capFilePath = Utils.getCapFilePath();
		String capFileName = fileName;
		capFilePath = capFilePath +"/"+capFileName;
		mListener.onProgressNotification("load cap : "+ capFilePath);
		if(capFilePath.endsWith("parsed"))
		{
			File file = new File(capFilePath);
			FileInputStream mFileStream = new FileInputStream(file);
			int tmpRead = 0;
			int counter = 0;
			byte[] temp = new byte[1];
			while (tmpRead != -1)
			{
				tmpRead = mFileStream.read(temp);
				counter++;
			}
			mFileStream = new FileInputStream(file);
			temp = new byte[1];
			mFileStream.read(temp);
			byte[] buffer;
			int read = 0;
			int maxRead = 200;
			mFileStream = new FileInputStream(file);
			buffer = new byte[200];
			byte[] loadHeader = new byte[0];
			byte[] additionalApdu = new byte[0];
			int p = 0;
			while (read != -1)
			{
				int additionalLen = 0;
				if(p == 0)
				{
					String hexStr = Integer.toHexString((counter - 1));
					if(hexStr.length() % 2 != 0)hexStr = "0" + hexStr;
					byte[] addApdu = CryptoUtils.convertHexStringToByteArray(hexStr);
					additionalApdu = new byte[2 + addApdu.length];
					additionalApdu[0] = (byte)0xC4;
					additionalApdu[1] = (byte)0x82;
					for(int i=0; i < addApdu.length; i++)
					{
						additionalApdu[2 + i] = addApdu[i];
					}
					additionalLen = additionalApdu.length;
					buffer = new byte[196];
				}
				else
				{
					buffer = new byte[200];
				}
				read = mFileStream.read(buffer);
				if (read < maxRead && p > 0)
				{
					byte[] last = new byte[read];
					System.arraycopy(buffer, 0, last, 0, last.length);
					buffer = last;
					loadHeader = new byte[] {(byte)0x80, (byte) 0xE8, (byte)0x80, (byte) p, (byte)last.length};
				}
				else
				{
					loadHeader = new byte[] {(byte)0x80, (byte) 0xE8, (byte)0x00, (byte) p, (byte)maxRead};
				}
				byte[] loadCommand = new byte[loadHeader.length + buffer.length + additionalLen];
				int startPos = 0;
				System.arraycopy(loadHeader, 0, loadCommand, startPos, loadHeader.length);
				startPos += loadHeader.length;
				if(additionalLen > 0)
				{
					System.arraycopy(additionalApdu, 0, loadCommand, startPos, additionalApdu.length);
					startPos += additionalApdu.length;
				}
				System.arraycopy(buffer, 0, loadCommand, startPos, buffer.length);
				mListener.onProgressNotification("SecureSession.loadData APDU: "+ p);
				String secureCommand = generateSecureCommand( CryptoUtils.convertBytesToHexString(loadCommand));
				if(this.mUseReader.equalsIgnoreCase("DF"))
				{
					if ( !handler.sendAPDU(secureCommand, false) )
						break;
					if(!handler.getResponse().endsWith("9000"))
					{
						mListener.onProgressNotification("Load Data FAIL: " + handler.getResponse());
						break;
					}
				}
				else if(this.mUseReader.equalsIgnoreCase("OTI"))
				{
					mListener.onProgressLog(">> " + secureCommand);
					byte[] resp = copni.request(CryptoUtils.convertHexStringToByteArray(secureCommand));
					if(!CryptoUtils.convertBytesToHexString(resp).endsWith("9000"))
					{
						mListener.onProgressNotification("Load Data FAIL: " + CryptoUtils.convertBytesToHexString(resp));
						break;
					}
					mListener.onProgressNotification("<< " + CryptoUtils.convertBytesToHexString(resp));
				}
				else
				{
					byte [] resp = channel.transmit(CryptoUtils.convertHexStringToByteArray(secureCommand));
					int length = resp.length;
					if ((resp[length - 2] & 0xFF) != 0x90 || (resp[length - 1] & 0xFF) != 0x00)
					{
						mListener.onProgressNotification("Load Data FAIL: " + CryptoUtils.convertBytesToHexString(resp));
						break;
					}
					mListener.onProgressNotification("Load Data RESP: " + CryptoUtils.convertBytesToHexString(resp));
				}
				if(read < maxRead && p > 0) break;
				p++;
			}
		}
		else
		{
			try
			{
				CapFile cap = null;
				cap = new CapFile(new FileInputStream(capFilePath));
				AID aid = cap.getAppletAIDs().get(0);
				mListener.onProgressNotification("load cap aid : "+ aid.toString());
				mListener.onProgressNotification("Installing applet from package " + cap.getPackageName());
				loadCapFile(cap, false, false, false, false);
			}
			catch (Exception e)
			{
				File file = new File(capFilePath);
				FileInputStream mFileStream = new FileInputStream(file);
				byte[] temp = new byte[1];
				mFileStream.read(temp);
				byte[] buffer;
				int read = 0;
				int maxRead = 0;
				mFileStream = new FileInputStream(file);
				if(temp[0] == 0x84)
				{
					buffer = new byte[213];
					read = mFileStream.read(buffer);
					maxRead = 213;
				}
				else
				{
					buffer = new byte[205];
					read = mFileStream.read(buffer);
					maxRead = 205;
				}
				
				int pos = 0;
					
				while (read != -1)
				{
					if (read < maxRead)
					{
						byte[] last = new byte[read];
						System.arraycopy(buffer, 0, last, 0, last.length);
						buffer = last;
					}
					if(buffer[0] == (byte)0x84)
					{
						byte next_iv[] = generateNextIV();
						byte data[] = new byte[buffer.length - 5 - 8];
						System.arraycopy(buffer, 5, data, 0, data.length);
						byte first_four[] = new byte[4];
						System.arraycopy(buffer, 0, first_four, 0, 4);
						mLastMac = CryptoUtils.generateMAC(mSession.get("session_mac"), first_four, data, next_iv);
						int index = buffer.length - 8;
						System.arraycopy(mLastMac, 0, buffer, index, 8);
						mListener.onProgressNotification("SecureSession.loadData APDU: " + pos);
						if(this.mUseReader.equalsIgnoreCase("DF"))
						{
							if ( !handler.sendAPDU(CryptoUtils.convertBytesToHexString(buffer), false) )
								break;
							if(!handler.getResponse().endsWith("9000"))
							{
								mListener.onProgressNotification("Load Data FAIL: " + handler.getResponse());
								break;
							}
						}
						else if(this.mUseReader.equalsIgnoreCase("OTI"))
						{
							mListener.onProgressLog(">> " + CryptoUtils.convertBytesToHexString(buffer));
							byte[] resp = copni.request(buffer);
							if(!CryptoUtils.convertBytesToHexString(resp).endsWith("9000"))
							{
								mListener.onProgressNotification("Load Data FAIL: " + handler.getResponse());
								break;
							}
							mListener.onProgressNotification("<< " + CryptoUtils.convertBytesToHexString(resp));
						}
						else
						{
							byte [] resp = channel.transmit(buffer);
							int length = resp.length;
							if ((resp[length - 2] & 0xFF) != 0x90 || (resp[length - 1] & 0xFF) != 0x00)
							{
								mListener.onProgressNotification("Load Data FAIL: " + CryptoUtils.convertBytesToHexString(resp));
								break;
							}
							mListener.onProgressNotification("Load Data RESP: " + CryptoUtils.convertBytesToHexString(resp));
						}
					}
					else
					{
						String cmd = CryptoUtils.convertBytesToHexString(buffer);
						String claInsP1P2_str = "84" + cmd.substring(2, 8);
						String aid_str = cmd.substring(10, cmd.length());
						byte[] headAPDU = CryptoUtils.convertHexStringToByteArray(claInsP1P2_str);
						byte[] bodyAPDU = CryptoUtils.convertHexStringToByteArray(aid_str);
						byte[] command = new byte[headAPDU.length + bodyAPDU.length + 1 + 8];
						System.arraycopy(headAPDU, 0, command, 0, headAPDU.length);
						command[headAPDU.length] = (byte)(bodyAPDU.length + 8);
						System.arraycopy(bodyAPDU, 0, command, headAPDU.length + 1, bodyAPDU.length);
						int index = headAPDU.length + 1 + bodyAPDU.length;
						byte data[] = new byte[command.length - 5 - 8];
						System.arraycopy(command, 5, data, 0, data.length);
						byte[] nextIV = generateNextIV();
						mLastMac = CryptoUtils.generateMAC(mSession.get("session_mac"), CryptoUtils.convertHexStringToByteArray(claInsP1P2_str), data, nextIV);
						System.arraycopy(mLastMac, 0, command, index, 8);
						mListener.onProgressNotification("SecureSession.loadData APDU: "+ pos);
						if(this.mUseReader.equalsIgnoreCase("DF"))
						{
							if ( !handler.sendAPDU(CryptoUtils.convertBytesToHexString(command), false) )
								break;
							if(!handler.getResponse().endsWith("9000"))
							{
								mListener.onProgressNotification("Load Data FAIL: " + handler.getResponse());
								break;
							}
						}
						else if(this.mUseReader.equalsIgnoreCase("OTI"))
						{
							mListener.onProgressLog(">> " + CryptoUtils.convertBytesToHexString(command));
							byte[] resp = copni.request(command);
							if(!CryptoUtils.convertBytesToHexString(resp).endsWith("9000"))
							{
								mListener.onProgressNotification("Load Data FAIL: " + CryptoUtils.convertBytesToHexString(resp));
								break;
							}
							mListener.onProgressNotification("<< " + CryptoUtils.convertBytesToHexString(resp));
						}
						else
						{
							byte [] resp = channel.transmit(command);
							int length = resp.length;
							if ((resp[length - 2] & 0xFF) != 0x90 || (resp[length - 1] & 0xFF) != 0x00)
							{
								mListener.onProgressNotification("Load Data FAIL: " + CryptoUtils.convertBytesToHexString(resp));
								break;
							}
							mListener.onProgressNotification("Load Data RESP: " + CryptoUtils.convertBytesToHexString(resp));
						}
					}
					read = mFileStream.read(buffer);
					pos++;
				}
			}
		}
	}
	
	
	private void loadCapFile(CapFile cap, boolean includeDebug, boolean separateComponents, boolean loadParam, boolean useHash) throws IOException
	{
		byte[] hash = useHash ? cap.getLoadFileDataHash(includeDebug) : new byte[0];
		int len = cap.getCodeLength(includeDebug);
		byte[] loadParams = loadParam ? new byte[] { (byte) 0xEF, 0x04, (byte) 0xC6, 0x02, (byte) ((len & 0xFF00) >> 8),
				(byte) (len & 0xFF) } : new byte[0];

		ByteArrayOutputStream bo = new ByteArrayOutputStream();

		try {
			bo.write(cap.getPackageAID().getLength());
			bo.write(cap.getPackageAID().getBytes());

			bo.write((mAID.length()/2));
			bo.write(CryptoUtils.convertHexStringToByteArray(mAID));

			bo.write(hash.length);
			bo.write(hash);

			bo.write(loadParams.length);
			bo.write(loadParams);
			bo.write(0);
		} catch (IOException ioe) {
			throw new RuntimeException(ioe);
		}

		byte[] commandInsForLoad = new byte[] {(byte)0x80, (byte)0xE6, (byte)0x02, 0x00};
		byte[] dataInstall = bo.toByteArray();
		byte[] installForLoad = new byte[commandInsForLoad.length + dataInstall.length];
		System.arraycopy(commandInsForLoad, 0, installForLoad, 0, commandInsForLoad.length);
		System.arraycopy(dataInstall, 0, installForLoad, commandInsForLoad.length, dataInstall.length);
		
		mListener.onProgressNotification("ADPU install for load = " + CryptoUtils.convertBytesToHexString(installForLoad));
		String hexCommand = generateSecureCommand(CryptoUtils.convertBytesToHexString(installForLoad));
		if(this.mUseReader.equalsIgnoreCase("DF"))
		{
			if ( !handler.sendAPDU(hexCommand, false) )
			if(!handler.getResponse().endsWith("9000"))
			{
				mListener.onProgressNotification("Load Data FAIL: " + handler.getResponse());
			}
		}
		else if(this.mUseReader.equalsIgnoreCase("OTI"))
		{
			byte[] resp = {(byte)0x0F, (byte)0xAF};
			try {
				mListener.onProgressLog(">> " + hexCommand);
				resp = copni.request(CryptoUtils.convertHexStringToByteArray(hexCommand));
			} catch (CopniCommunicationException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (CopniAccessControlException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			if(!CryptoUtils.convertBytesToHexString(resp).endsWith("9000"))
			{
				mListener.onProgressNotification("Load Data FAIL: " + CryptoUtils.convertBytesToHexString(resp));
			}
			mListener.onProgressNotification("<< " + CryptoUtils.convertBytesToHexString(resp));
		}
		else
		{
			byte [] resp = channel.transmit(CryptoUtils.convertHexStringToByteArray(hexCommand));
			int length = resp.length;
			if ((resp[length - 2] & 0xFF) != 0x90 || (resp[length - 1] & 0xFF) != 0x00)
			{
				mListener.onProgressNotification("Load Data FAIL: " + CryptoUtils.convertBytesToHexString(resp));
			}
			mListener.onProgressNotification("Load Data RESP: " + CryptoUtils.convertBytesToHexString(resp));
		}

		List<byte[]> blocks = cap.getLoadBlocks(includeDebug, separateComponents, defaultLoadSize);
		for (int i = 0; i < blocks.size(); i++) {
			byte[] loadHeader = new byte[] {(byte)0x80, (byte) 0xE8, (i == (blocks.size() - 1)) ? (byte)0x80 : 0x00, (byte) i};
			byte[] loadBody = blocks.get(i);
			byte[] loadCommand = new byte[loadHeader.length + loadBody.length];
			System.arraycopy(loadHeader, 0, loadCommand, 0, loadHeader.length);
			System.arraycopy(loadBody, 0, loadCommand, loadHeader.length, loadBody.length);
			mListener.onProgressNotification("ADPU = " + CryptoUtils.convertBytesToHexString(loadCommand));
			hexCommand = generateSecureCommand(CryptoUtils.convertBytesToHexString(loadCommand));
			if(this.mUseReader.equalsIgnoreCase("DF"))
			{
				if ( !handler.sendAPDU(hexCommand, false) )
				if(!handler.getResponse().endsWith("9000"))
				{
					mListener.onProgressNotification("Load Data FAIL: " + handler.getResponse());
				}
			}
			else if(this.mUseReader.equalsIgnoreCase("OTI"))
			{
				byte[] resp = {(byte)0x0F, (byte)0xAF};
				try {
					mListener.onProgressLog(">> " + hexCommand);
					resp = copni.request(CryptoUtils.convertHexStringToByteArray(hexCommand));
				} catch (CopniCommunicationException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (CopniAccessControlException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				if(!CryptoUtils.convertBytesToHexString(resp).endsWith("9000"))
				{
					mListener.onProgressNotification("Load Data FAIL: " + CryptoUtils.convertBytesToHexString(resp));
					break;
				}
				mListener.onProgressNotification("<< " + CryptoUtils.convertBytesToHexString(resp));
			}
			else
			{
				byte [] resp = channel.transmit(CryptoUtils.convertHexStringToByteArray(hexCommand));
				int length = resp.length;
				if ((resp[length - 2] & 0xFF) != 0x90 || (resp[length - 1] & 0xFF) != 0x00)
				{
					mListener.onProgressNotification("Load Data FAIL: " + CryptoUtils.convertBytesToHexString(resp));
				}
				mListener.onProgressNotification("Load Data RESP: " + CryptoUtils.convertBytesToHexString(resp));
			}
			
		}
	}
	
	
	/**
	 * Tested
	 * @param newKeys
	 * @param isAdd
	 * @param keySet
	 * @return
	 */
	public String generatePutKey(String[] newKeys, boolean isAdd, int keySet)
	{
		String result = null;
		byte[] changeKeys = new byte[72];
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		baos.write(0x84);
		baos.write(0xD8);
		if (isAdd)
			baos.write(0);
		else
			baos.write(1);
		baos.write((byte) (0x81));
		baos.write(0);
		baos.write(keySet);
		
		byte tt[] = baos.toByteArray();
		System.arraycopy(tt, 0, changeKeys, 0, tt.length);
		byte nk1[] = new byte[24];
		System.arraycopy(CryptoUtils.convertHexStringToByteArray(newKeys[0]), 0, nk1, 0, 16);
		System.arraycopy(CryptoUtils.convertHexStringToByteArray(newKeys[0]), 0, nk1, 16, 8);
		byte nk2[] = new byte[24];
		System.arraycopy(CryptoUtils.convertHexStringToByteArray(newKeys[1]), 0, nk2, 0, 16);
		System.arraycopy(CryptoUtils.convertHexStringToByteArray(newKeys[1]), 0, nk2, 16, 8);
		byte nk3[] = new byte[24];
		System.arraycopy(CryptoUtils.convertHexStringToByteArray(newKeys[2]), 0, nk3, 0, 16);
		System.arraycopy(CryptoUtils.convertHexStringToByteArray(newKeys[2]), 0, nk3, 16, 8);
		byte[] k1;
		byte[] k2;
		byte[] k3;
		
		try
		{
			String currentKEK = CryptoUtils.convertBytesToHexString(mSession.get("session_kek"));
			String KEK_24 = currentKEK;
			while(KEK_24.length() < 48) {
				KEK_24 += currentKEK.substring(0, (48 - KEK_24.length()));
			}
			
			k1 = CryptoUtils.encodeKey(SCP, CryptoUtils.convertBytesToHexString(nk1), KEK_24);
			k2 = CryptoUtils.encodeKey(SCP, CryptoUtils.convertBytesToHexString(nk2), KEK_24);
			k3 = CryptoUtils.encodeKey(SCP, CryptoUtils.convertBytesToHexString(nk3), KEK_24);
			
			System.arraycopy(k1, 0, changeKeys, 6, k1.length);
			System.arraycopy(k2, 0, changeKeys, 28, k2.length);
			System.arraycopy(k3, 0, changeKeys, 50, k3.length);
			changeKeys[4] = (byte) (changeKeys.length - 5);
			// changeKeys[72] = (byte) (0x00);
			byte[] nextIV = generateNextIV();
			byte[] first_four = new byte[4];
			System.arraycopy(changeKeys, 0, first_four, 0, 4);
			byte[] data = new byte[changeKeys.length - 5];
			System.arraycopy(changeKeys, 5, data, 0, data.length);
			mLastMac = CryptoUtils.generateMAC(mSession.get("session_mac"), first_four, data, nextIV);
			
			byte[] changeKeysEnc = new byte[changeKeys.length + 8];
			System.arraycopy(changeKeys, 0, changeKeysEnc, 0, changeKeys.length);
//			System.arraycopy(last8, 0, changeKeysEnc, changeKeys.length, 8);
//			int l = changeKeys.length + 3;
//			changeKeysEnc[4] = (byte)(l & 0xFF);
			int Lc = changeKeysEnc[4] & 0xFF;
			Lc = Lc + 8;
			
			int L = changeKeysEnc[4] & 0xFF;
			byte[] DataField = new byte[L];
			System.arraycopy(changeKeysEnc, 5, DataField, 0, L);
			int toEncLength = L + 1;
			int padLength = 1;
			while (toEncLength%8!=0) {
			toEncLength++;
			padLength++;
			}
			byte[] toEnc = new byte[toEncLength];
			System.arraycopy(DataField, 0, toEnc, 0, L);
			System.arraycopy(arrayPAD, 0, toEnc, L, toEncLength-L);
			String currentENC = CryptoUtils.convertBytesToHexString(mSession.get("session_enc"));
			toEnc = CryptoUtils.encryptTripleDES(CryptoUtils.convertHexStringToByteArray(currentENC), toEnc, null);
			byte[] resp = new byte[5+toEncLength + 8];
			System.arraycopy(changeKeysEnc, 0, resp, 0, 4);
			resp[4] = (byte)((byte)Lc + (byte)padLength);
			System.arraycopy(toEnc, 0, resp, 5, toEncLength);
			System.arraycopy(mLastMac, 0, resp, 5 + toEncLength, 8);
			
//			mListener.onProgressNotification("cmd = " + CryptoUtils.convertBytesToHexString(changeKeysEnc));
			result = CryptoUtils.convertBytesToHexString(resp);
		}
		catch(Exception e)
		{
			mListener.onProgressNotification("PUT KEY FAIL : " + e);
		}
		return result;
	}
	
	
	private byte[] generateNextIV()
	{
		byte first_eight[] = new byte[8];
		System.arraycopy(mSession.get("session_mac"), 0, first_eight, 0, 8);
		byte next_iv[] = CryptoUtils.encryptDESECB(first_eight, mLastMac);
		return next_iv;
	}
	
	public void setHandler(DFHandler _handler)
	{
		handler = _handler;
	}
	
	public void setCopni(Copni _copni)
	{
		copni = _copni;
	}
	
	public void setChannel(Channel _channel)
	{
		channel = _channel;
	}
	
	public void setAID(String _aid)
	{
		mAID = _aid;
	}
	
	public boolean getIsInitUpdateCommand()
	{
		return isInitUpdateCommand;
	}

	public byte getSCP() {
		return SCP;
	}

	public void setSCP(byte sCP) {
		SCP = sCP;
	}
}
