package com.heru.process;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Hashtable;

import org.simalliance.openmobileapi.Channel;
import org.simalliance.openmobileapi.Reader;
import org.simalliance.openmobileapi.SEService;
import org.simalliance.openmobileapi.Session;

import com.heru.omatoolkit.MainActivity;
import com.heru.omatoolkit.ProcessListener;
import com.heru.omatoolkit.util.CryptoUtils;
import com.heru.omatoolkit.util.DFHandler;
import com.heru.omatoolkit.util.Data;
import com.heru.omatoolkit.util.Utils;
import com.otiglobal.copni.Copni;
import com.otiglobal.copni.exception.CopniCommunicationException;

import android.content.Context;
import android.content.Intent;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.os.AsyncTask;
import android.telephony.IccOpenLogicalChannelResponse;
import android.telephony.TelephonyManager;

public class Process extends AsyncTask<Void, String, Void> {
	private static String DERIVATION_METHOD_CPG211     = "0";
	private static String DERIVATION_METHOD_GTO_ORANGE = "1";
	private static String DERIVATION_METHOD_GTO_CPG204 = "2";
	
	private static final int NFC_CONNECTION_TIMEOUT = 50000;
	
	public static final String SCRIPT_DONE = "Process Script Done!";
	
	private SEService mService;
	private ProcessListener mListener;
	
	private String mUseReader, mUseChannel;
	private String mAID, mCmd;
	private Context mContext;
	
	private String randomData = "E457505D7CBBD7E7";
//	private String randomData = "4D13A6A24165CBF8";
	
	private Channel channel = null;
	private DFHandler handler = null;
	
	private Intent mIntent;
	private IsoDep mNfc;
	
	private Copni copni;
	private Commands processCmd;
	
	private boolean isContinue = false;
	
	public Process(Context _context, SEService _seService, ProcessListener _listener, String _useReader, String _useChannel)
	{
		this.mContext = _context;
		this.mService = _seService;
		this.mListener = _listener;
		this.mUseReader = _useReader;
		this.mUseChannel = _useChannel;
		this.isContinue = false;
	}
	
	public void setCopni(Copni cop)
	{
		this.copni = cop;
	}
	
	public void setParameter(String _aid, String _cmd)
	{
		mAID = _aid;
		mCmd = _cmd;
	}
	
	@Override
	protected Void doInBackground(Void... params) {
		// TODO Auto-generated method stub
		executeCommand(mAID, mCmd);
		return null;
	}
	
	public void showMessageToUI(String message)
	{
		mListener.onProgressNotification(message);
	}
	
	public void logMessageToUI(String message)
	{
		mListener.onProgressLog(message);
	}
	
	private void getStaticKey(byte[] resp)
	{
		String masterKey = Data.getSettings(mContext, Data.KEY_MASTER);
		String methodType = Data.getSettings(mContext, Data.KEY_METHOD_TYPE);
		mListener.onProgressNotification("Key Master = " + masterKey);
		String []key = new String[3];
		String []value = new String[3];
		if(methodType.equalsIgnoreCase(DERIVATION_METHOD_GTO_ORANGE))
		{
			byte[] zeros = new byte[] { 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 };
			byte[] mk = CryptoUtils.convertHexStringToByteArray(masterKey);
			TelephonyManager telManager = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
			String iccid = telManager.getSimSerialNumber();
			String strIccId = iccid;
			strIccId = strIccId.replaceAll("f", "");
			strIccId = strIccId.replaceAll("F", "");
			strIccId = strIccId.substring(strIccId.length() - 14);
			
			String aid = mAID.substring(mAID.length() - 16);
			byte [] aidCud = CryptoUtils.convertHexStringToByteArray((strIccId + aid));
			byte bsEnc[] = new byte[1+aidCud.length];
			byte bsMac[] = new byte[1+aidCud.length];
			byte bsKek[] = new byte[1+aidCud.length];
			bsEnc[0] = (byte)0x01;
			bsMac[0] = (byte)0x02;
			bsKek[0] = (byte)0x03;
			System.arraycopy(aidCud, 0, bsEnc, 1, aidCud.length);
			System.arraycopy(aidCud, 0, bsMac, 1, aidCud.length);
			System.arraycopy(aidCud, 0, bsKek, 1, aidCud.length);
			byte[] resultEnc = CryptoUtils.encryptTripleDES(mk, bsEnc, zeros);
			byte[] resultMac = CryptoUtils.encryptTripleDES(mk, bsMac, zeros);
			byte[] resultKek = CryptoUtils.encryptTripleDES(mk, bsKek, zeros);
			
			String encKey = CryptoUtils.convertBytesToHexString(resultEnc).toUpperCase();
//			mListener.onProgressNotification("Key ENC = " + encKey);
			String macKey = CryptoUtils.convertBytesToHexString(resultMac).toUpperCase();
//			mListener.onProgressNotification("Key MAC = " + macKey);
			String kekKey = CryptoUtils.convertBytesToHexString(resultKek).toUpperCase();
//			mListener.onProgressNotification("Key KEK = " + kekKey);
			key[0] = Data.KEY_ENC;
			key[1] = Data.KEY_MAC;
			key[2] = Data.KEY_KEK;
			value[0] = encKey;
			value[1] = macKey;
			value[2] = kekKey;
			
		}
		else if (methodType.equalsIgnoreCase(DERIVATION_METHOD_GTO_CPG204))
		{
			byte[] zeros = new byte[] { 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 };
			byte[] mk = CryptoUtils.convertHexStringToByteArray(masterKey);
			TelephonyManager telManager = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
			String iccid = telManager.getSimSerialNumber();
			String strIccId = iccid;
			strIccId = strIccId.replaceAll("f", "");
			strIccId = strIccId.replaceAll("F", "");
			strIccId = strIccId.substring(strIccId.length() - 14);
			
			String aid = mAID.substring(mAID.length() - 16);
			byte [] aidCud = CryptoUtils.convertHexStringToByteArray((strIccId + aid));
			byte bsEnc[] = new byte[1+aidCud.length];
			byte bsMac[] = new byte[1+aidCud.length];
			byte bsKek[] = new byte[1+aidCud.length];
			bsEnc[aidCud.length] = (byte)0x01;
			bsMac[aidCud.length] = (byte)0x02;
			bsKek[aidCud.length] = (byte)0x03;
			System.arraycopy(aidCud, 0, bsEnc, 0, aidCud.length);
			System.arraycopy(aidCud, 0, bsMac, 0, aidCud.length);
			System.arraycopy(aidCud, 0, bsKek, 0, aidCud.length);
			byte[] resultEnc = CryptoUtils.encryptTripleDES(mk, bsEnc, zeros);
			byte[] resultMac = CryptoUtils.encryptTripleDES(mk, bsMac, zeros);
			byte[] resultKek = CryptoUtils.encryptTripleDES(mk, bsKek, zeros);
			
			String encKey = CryptoUtils.convertBytesToHexString(resultEnc).toUpperCase();
			mListener.onProgressNotification("Key ENC = " + encKey);
			String macKey = CryptoUtils.convertBytesToHexString(resultMac).toUpperCase();
			mListener.onProgressNotification("Key MAC = " + macKey);
			String kekKey = CryptoUtils.convertBytesToHexString(resultKek).toUpperCase();
			mListener.onProgressNotification("Key KEK = " + kekKey);
			key[0] = Data.KEY_ENC;
			key[1] = Data.KEY_MAC;
			key[2] = Data.KEY_KEK;
			value[0] = encKey;
			value[1] = macKey;
			value[2] = kekKey;
		}
		else if(methodType.equalsIgnoreCase(DERIVATION_METHOD_CPG211))
		{
			byte [] keyOfStaticKey = new byte [6];
			System.arraycopy(resp, 4, keyOfStaticKey, 0, 6);
			byte [] byteOfMasterKey = CryptoUtils.convertHexStringToByteArray(masterKey);
			byte[] enc = generateMacKekDes(keyOfStaticKey, new byte[] {(byte)0xF0, (byte)0x01}, new byte[] {(byte)0x0F, (byte)0x01}, byteOfMasterKey);
			byte[] mac = generateMacKekDes(keyOfStaticKey, new byte[] {(byte)0xF0, (byte)0x02}, new byte[] {(byte)0x0F, (byte)0x02}, byteOfMasterKey);
			byte[] kek = generateMacKekDes(keyOfStaticKey, new byte[] {(byte)0xF0, (byte)0x03}, new byte[] {(byte)0x0F, (byte)0x03}, byteOfMasterKey);
			String encKey = CryptoUtils.convertBytesToHexString(enc).toUpperCase();
			mListener.onProgressNotification("Key ENC = " + encKey);
			String macKey = CryptoUtils.convertBytesToHexString(mac).toUpperCase();
			mListener.onProgressNotification("Key MAC = " + macKey);
			String kekKey = CryptoUtils.convertBytesToHexString(kek).toUpperCase();
			mListener.onProgressNotification("Key KEK = " + kekKey);
			key[0] = Data.KEY_ENC;
			key[1] = Data.KEY_MAC;
			key[2] = Data.KEY_KEK;
			value[0] = encKey;
			value[1] = macKey;
			value[2] = kekKey;
		}
		Data.saveSettings(mContext, key, value);
	}
	
	private byte[] generateMacKekDes(byte[] keyOfStaticKey, byte[] key1, byte[] key2, byte[] masterKey)
	{
		byte [] keyTemp = new byte [16];
		int idx = 0;
		System.arraycopy(keyOfStaticKey, 0, keyTemp, 0, keyOfStaticKey.length);
		idx += keyOfStaticKey.length;
		System.arraycopy(key1, 0, keyTemp, idx, key1.length);
		idx += key1.length;
		System.arraycopy(keyOfStaticKey, 0, keyTemp, idx, keyOfStaticKey.length);
		idx += keyOfStaticKey.length;
		System.arraycopy(key2, 0, keyTemp, idx, key2.length);
		mListener.onProgressNotification("MacKekDes Key = " + CryptoUtils.convertBytesToHexString(keyTemp));
		byte[] result = CryptoUtils.encryptTripleDESECB(masterKey, keyTemp);
		return result;
	}
	
	private void executeCommand(String openAID, String listCmd)
	{
		channel = null;
		try {
			if(processCmd == null)
			{
				processCmd = new Commands(mContext, mUseReader, randomData, mAID, mListener);
			}
			
			if(this.mUseReader.equalsIgnoreCase(MainActivity.READER_ISO_DEP))
			{
				String action = mIntent.getAction();

				// 2) Check if it was triggered by a tag discovered interruption.
				if (NfcAdapter.ACTION_TECH_DISCOVERED.equals(action)) {
					// 3) Get an instance of the TAG from the NfcAdapter
					Tag tagFromIntent = mIntent
							.getParcelableExtra(NfcAdapter.EXTRA_TAG);
					// DLog.dl.d("TAG = " + tagFromIntent.toString());
					mNfc = IsoDep.get(tagFromIntent);
					try {
						mNfc.connect();
						mNfc.setTimeout(NFC_CONNECTION_TIMEOUT);
						if (mNfc.isConnected()) {
							byte theAID[] = CryptoUtils.convertHexStringToByteArray(openAID);
							byte newHead[] = { (byte) 0x00, (byte) 0xA4,
									(byte) 0x04, (byte) 0x00, (byte) theAID.length };
							byte newCommand[] = new byte[newHead.length
									+ theAID.length];
							System.arraycopy(newHead, 0, newCommand, 0,
									newHead.length);
							System.arraycopy(theAID, 0, newCommand, newHead.length,
									theAID.length);
							mListener.onProgressNotification("SELECT = " + CryptoUtils.convertBytesToHexString(newCommand));
							byte[] resp = mNfc.transceive(newCommand);
							if (resp[resp.length - 2] == (byte) 0x90
									&& resp[resp.length - 1] == (byte) 0x00) {
								mListener.onProgressNotification("RESP : "+ CryptoUtils.convertBytesToHexString(resp));
							} else {
								mListener.onProgressNotification("RESP : "+ CryptoUtils.convertBytesToHexString(resp));
								return;
							}
						} else {
							mListener.onProgressNotification("NFC is not connected!");
						}
					} catch (IOException e) {
						try {
							if (mNfc != null) {
								mNfc.close();
							}
						} catch (IOException ex) {
						}
						mListener.onProgressNotification("Failed to open NFC " + e);
					}
					if(listCmd.length() > 0)
					{
					
						String listCommand[] = listCmd.split(";");
						for(int i=0; i < listCommand.length; i++)
						{
							String cmd = listCommand[i].replaceAll(" ", "");
							if(cmd.startsWith("loadapplet"))
							{
								String cmdTemp = cmd.replaceAll("loadapplet", "");
								processCmd.loadData(cmdTemp);
							}
							else
							{
								String mAID = isSelectCommand(CryptoUtils.convertHexStringToByteArray(cmd));
								if(mAID != null)
								{
									mListener.onProgressNotification("OPEN AID : "+mAID);
									try {
										if (mNfc != null) {
											mNfc.close();
										}
									} catch (IOException ex) {
									}
									try {
										mNfc.connect();
										mNfc.setTimeout(NFC_CONNECTION_TIMEOUT);
										if (mNfc.isConnected()) {
											byte theAID[] = CryptoUtils.convertHexStringToByteArray(openAID);
											byte newHead[] = { (byte) 0x00, (byte) 0xA4,
													(byte) 0x04, (byte) 0x00, (byte) theAID.length };
											byte newCommand[] = new byte[newHead.length
													+ theAID.length];
											System.arraycopy(newHead, 0, newCommand, 0,
													newHead.length);
											System.arraycopy(theAID, 0, newCommand, newHead.length,
													theAID.length);
											mListener.onProgressNotification("SELECT = " + CryptoUtils.convertBytesToHexString(newCommand));
											byte[] resp = mNfc.transceive(newCommand);
											if (resp[resp.length - 2] == (byte) 0x90
													&& resp[resp.length - 1] == (byte) 0x00) {
												mListener.onProgressNotification("RESP : "+ CryptoUtils.convertBytesToHexString(resp));
											} else {
												mListener.onProgressNotification("RESP : "+ CryptoUtils.convertBytesToHexString(resp));
												return;
											}
										} else {
											mListener.onProgressNotification("NFC is not connected!");
										}
									} catch (IOException e) {
										try {
											if (mNfc != null) {
												mNfc.close();
											}
										} catch (IOException ex) {
										}
										mListener.onProgressNotification("Failed to open NFC " + e);
									}
								}
								else
								{
									cmd = processCmd.generateCommand(cmd);
									if(!cmd.startsWith("loadapplet"))
									{
										mListener.onProgressNotification("EXCHANGE APDU : "+cmd);
										byte []command = CryptoUtils.convertHexStringToByteArray(cmd);
										byte[] resp = mNfc.transceive(command);
										if(processCmd.getIsInitUpdateCommand())
										{
											if(Data.getSettings(mContext, Data.KEY_MASTER).length() > 0)
											{
												getStaticKey(resp);
											}
											byte x[] = resp;
											processCmd.setSCP(x[11]);
										}
										mListener.onProgressNotification("RESP : "+ CryptoUtils.convertBytesToHexString(resp));
										String []key = new String[1];
										key[0] = Data.KEY_LAST_RESPONSE;
										String []value = new String[1];
										value[0] = CryptoUtils.convertBytesToHexString(resp);
										Data.saveSettings(mContext, key, value);
									}
								}
							}
						}
					}
				}
				try {
					if (mNfc != null) {
						mNfc.close();
					}
				} catch (IOException ex) {
				}
			}
			else if(this.mUseReader.equalsIgnoreCase(MainActivity.READER_SIM) || this.mUseReader.equalsIgnoreCase(MainActivity.READER_ESE))
			{
				mListener.onProgressNotification("Getting available readers...");
				Reader[] readers = mService.getReaders();
	
				if (readers.length < 1)
				{
					mListener.onProgressNotification("Cannot retrieve available readers.");
					return;
				}
	
				for(int i=0; i < readers.length; i++) 
				{
					mListener.onProgressNotification("readers "+(i+1)+" = " + readers[i].getName());
				}
				int loc = -1;
				for(int i=0; i < readers.length; i++) {
					if(readers[i].getName().contains(mUseReader)) {
						loc = i;
						break;
					}
				}
				mListener.onProgressNotification("selected readers "+(loc+1)+" = " + readers[loc].getName());
				Session session = readers[loc].openSession();
				mListener.onProgressNotification("session.getATR : "+ CryptoUtils.convertBytesToHexString(session.getATR()));
				
				byte []aid = CryptoUtils.convertHexStringToByteArray(openAID);
				mListener.onProgressNotification("OPEN AID : "+ CryptoUtils.convertBytesToHexString(aid));
				if(mUseChannel.equalsIgnoreCase("Basic"))
				{
					channel = session.openBasicChannel(aid);
				}
				else
				{
					channel = session.openLogicalChannel(aid);
				}
				if(channel == null)
				{
					mListener.onProgressNotification("Error occured : Channel is null");
					return;
				}
				mListener.onProgressNotification("RESP : "+ CryptoUtils.convertBytesToHexString(channel.getSelectResponse()));
				if(listCmd.length() > 0)
				{
				
					String listCommand[] = listCmd.split(";");
					for(int i=0; i < listCommand.length; i++)
					{
						String cmd = listCommand[i].replaceAll(" ", "");
						if(cmd.startsWith("loadapplet"))
						{
							String cmdTemp = cmd.replaceAll("loadapplet", "");
							processCmd.loadData(cmdTemp);
						}
						else
						{
							String mAID = null;
							try {
								mAID = isSelectCommand(CryptoUtils.convertHexStringToByteArray(cmd));
							} catch (Exception e) {
								// TODO: handle exception
							}
							if(mAID != null)
							{
								mListener.onProgressNotification("OPEN AID : "+mAID);
								channel.close();
								if(mUseChannel.equalsIgnoreCase(MainActivity.CHANNEL_BASIC))
								{
									channel = session.openBasicChannel(aid);
								}
								else
								{
									channel = session.openLogicalChannel(aid);
								}
								if(channel == null)
								{
									mListener.onProgressNotification("Error occured : Channel is null");
									return;
								}
								mListener.onProgressNotification("RESP : "+ CryptoUtils.convertBytesToHexString(channel.getSelectResponse()));
							}
							else
							{
								processCmd.setChannel(channel);
								cmd = processCmd.generateCommand(cmd);
								if(!cmd.startsWith("loadapplet"))
								{
									mListener.onProgressNotification("EXCHANGE APDU : "+cmd);
									byte []command = CryptoUtils.convertHexStringToByteArray(cmd);
									byte [] resp = channel.transmit(command);
									if(processCmd.getIsInitUpdateCommand())
									{
										if(Data.getSettings(mContext, Data.KEY_MASTER).length() > 0)
										{
											getStaticKey(resp);
										}
										byte x[] = resp;
										processCmd.setSCP(x[11]);
									}
									mListener.onProgressNotification("RESP : "+ CryptoUtils.convertBytesToHexString(resp));
									String []key = new String[1];
									key[0] = Data.KEY_LAST_RESPONSE;
									String []value = new String[1];
									value[0] = CryptoUtils.convertBytesToHexString(resp);
									Data.saveSettings(mContext, key, value);
								}
							}
						}
					}
				}
				session.closeChannels();
			}
			else if(this.mUseReader.equalsIgnoreCase(MainActivity.READER_SIM_TM)) {
				TelephonyManager tm = (TelephonyManager) this.mContext.getSystemService(Context.TELEPHONY_SERVICE);
				mListener.onProgressNotification("OPEN AID : "+ openAID);
				IccOpenLogicalChannelResponse logicalChannel = null;
				if(mUseChannel.equalsIgnoreCase(MainActivity.CHANNEL_BASIC))
				{
					int len = openAID.length()/2;
					String resp = tm.iccTransmitApduBasicChannel(0, 164, 4, 0, len, openAID);
					mListener.onProgressNotification("RESP : "+ resp);
				}
				else
				{
					logicalChannel = tm.iccOpenLogicalChannel(openAID);
					if(logicalChannel == null)
					{
						mListener.onProgressNotification("Error occured : Channel is null");
						return;
					}
					mListener.onProgressNotification("RESP : "+ CryptoUtils.convertBytesToHexString(logicalChannel.getSelectResponse()));
				}
				if(listCmd.length() > 0)
				{
				
					String listCommand[] = listCmd.split(";");
					for(int i=0; i < listCommand.length; i++)
					{
						String cmd = listCommand[i].replaceAll(" ", "");
						if(cmd.startsWith("loadapplet"))
						{
							String cmdTemp = cmd.replaceAll("loadapplet", "");
							processCmd.loadData(cmdTemp);
						}
						else
						{
							String mAID = null;
							try {
								mAID = isSelectCommand(CryptoUtils.convertHexStringToByteArray(cmd));
							} catch (Exception e) {
								// TODO: handle exception
							}
							if(mAID != null)
							{
								mListener.onProgressNotification("OPEN AID : "+mAID);
								if(this.mUseChannel.equalsIgnoreCase(MainActivity.CHANNEL_LOGICAL))
									tm.iccCloseLogicalChannel(logicalChannel.getChannel());
								if(mUseChannel.equalsIgnoreCase(MainActivity.CHANNEL_BASIC))
								{
									int len = openAID.length()/2;
									String resp = tm.iccTransmitApduBasicChannel(0, 164, 4, 0, len, openAID);
									mListener.onProgressNotification("RESP : "+ resp);
								}
								else
								{
									logicalChannel = tm.iccOpenLogicalChannel(openAID);
									if(logicalChannel == null)
									{
										mListener.onProgressNotification("Error occured : Channel is null");
										return;
									}
									mListener.onProgressNotification("RESP : "+ CryptoUtils.convertBytesToHexString(logicalChannel.getSelectResponse()));
								}
							}
							else
							{
								//processCmd.setChannel(channel);
								/*cmd = processCmd.generateCommand(cmd);
								if(!cmd.startsWith("loadapplet"))
								{
									mListener.onProgressNotification("EXCHANGE APDU : "+cmd);
									byte []command = CryptoUtils.convertHexStringToByteArray(cmd);
									if(mUseChannel.equalsIgnoreCase(MainActivity.CHANNEL_BASIC))
									{
										tm.iccTransmitApduBasicChannel(cla, instruction, p1, p2, p3, data)
									}
									byte [] resp = tm.transmit(command);
									if(processCmd.getIsInitUpdateCommand())
									{
										if(Data.getSettings(mContext, Data.KEY_MASTER).length() > 0)
										{
											getStaticKey(resp);
										}
										byte x[] = resp;
										processCmd.setSCP(x[11]);
									}
									mListener.onProgressNotification("RESP : "+ CryptoUtils.convertBytesToHexString(resp));
									String []key = new String[1];
									key[0] = Data.KEY_LAST_RESPONSE;
									String []value = new String[1];
									value[0] = CryptoUtils.convertBytesToHexString(resp);
									Data.saveSettings(mContext, key, value);
								}*/
							}
						}
					}
				}
				if(this.mUseChannel.equalsIgnoreCase(MainActivity.CHANNEL_LOGICAL))
					tm.iccCloseLogicalChannel(logicalChannel.getChannel());
			}
			else if(this.mUseReader.equalsIgnoreCase(MainActivity.READER_DF))
			{
				handler = new DFHandler(this);
				int ret = handler.openSession();
				if ( ret != 0 )
				{
					mListener.onProgressNotification("\n" + SCRIPT_DONE);
					return;
				}
			        
				ret = handler.enable7816();
				if ( ret == -1 )
				{
					ret = handler.enable7816();
					if ( ret == -1 )
						handler.closeSession();
						return;
				}
				
				if(openAID.length() % 2 != 0)
				{
					throw new Exception("Invalid AID Length");
				}
				int lengthAID = (openAID.length() / 2);
				String aid = Integer.toHexString(lengthAID);
				String selectAID = "";
				if(aid.length() == 1)
				{
					selectAID = "00A404000"+aid+openAID;
				}
				else
				{
					selectAID = "00A40400"+aid+openAID;
				}
				if ( !handler.sendAPDU(selectAID, true ) )
					throw new Exception("Open AID Failed");
				if(listCmd.length() > 0)
				{
					String listCommand[] = listCmd.split(";");
					for(int i=0; i < listCommand.length; i++)
					{
						String cmd = listCommand[i].replaceAll(" ", "");
						processCmd.setHandler(handler);
						cmd = processCmd.generateCommand(cmd);
						if(!cmd.startsWith("loadapplet"))
						{
							if ( !handler.sendAPDU(cmd, true ) )
								throw new Exception("Exchange APDU Failed " + cmd);
						}
						if(processCmd.getIsInitUpdateCommand())
						{
							if(Data.getSettings(mContext, Data.KEY_MASTER).length() > 0)
							{
								getStaticKey(CryptoUtils.convertHexStringToByteArray(handler.getResponse()));
							}
							byte x[] = CryptoUtils.convertHexStringToByteArray(handler.getResponse());
							processCmd.setSCP(x[11]);
						}
						String []key = new String[1];
						key[0] = Data.KEY_LAST_RESPONSE;
						String []value = new String[1];
						value[0] = handler.getResponse();
						Data.saveSettings(mContext, key, value);
					}
				}
			}
			else if(this.mUseReader.equalsIgnoreCase(MainActivity.READER_OTI))
			{
				if(copni == null)
				{
					copni = new Copni(this.mContext);
					mListener.onProgressNotification("Connecting.. Please Wait..");
				}
				if (!copni.isConnected())
				{
					try {
						copni.connect();
					} catch (CopniCommunicationException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					mListener.onProgressNotification("Connected " + copni.getDeviceId());
					if(openAID.length() % 2 != 0)
					{
						throw new Exception("Invalid AID Length");
					}
					int lengthAID = (openAID.length() / 2);
					String aid = Integer.toHexString(lengthAID);
					String selectAID = "";
					if(aid.length() == 1)
					{
						selectAID = "00A404000"+aid+openAID;
					}
					else
					{
						selectAID = "00A40400"+aid+openAID;
					}
					mListener.onProgressNotification(">> " + selectAID);
					byte[] response = copni.request(CryptoUtils.convertHexStringToByteArray(selectAID));
					mListener.onProgressNotification("<< " + CryptoUtils.convertBytesToHexString(response));
				}
				else
				{
					mListener.onProgressNotification("Connected " + copni.getDeviceId());
				}
				
				
				if(listCmd.length() > 0)
				{
					byte[] response = null;
					String listCommand[] = listCmd.split(";");
					for(int i=0; i < listCommand.length; i++)
					{
						String cmd = listCommand[i].replaceAll(" ", "");
						processCmd.setCopni(copni);
						cmd = processCmd.generateCommand(cmd);
						if(!cmd.startsWith("loadapplet"))
						{
							mListener.onProgressNotification(">> " + cmd);
							response = copni.request(CryptoUtils.convertHexStringToByteArray(cmd));
							mListener.onProgressNotification("<< " + CryptoUtils.convertBytesToHexString(response));
						}
						if(processCmd.getIsInitUpdateCommand())
						{
							if(Data.getSettings(mContext, Data.KEY_MASTER).length() > 0)
							{
								getStaticKey(response);
							}
							byte x[] = response;
							processCmd.setSCP(x[11]);
						}
						String []key = new String[1];
						key[0] = Data.KEY_LAST_RESPONSE;
						String []value = new String[1];
						value[0] = CryptoUtils.convertBytesToHexString(response);
						Data.saveSettings(mContext, key, value);
					}
				}
			}
		} catch (Exception e) {
			if(channel != null && !channel.isClosed())
			{
				channel.close();
			}
			if(this.mUseReader.equalsIgnoreCase(MainActivity.READER_DF))
			{
				//  ...Cleanup
				handler.disable7816();
				handler.closeSession();
			}
			if (copni!= null && copni.isConnected())
			{
				mListener.onProgressNotification("Disconnecting copni...");
				copni.disconnect();
				copni = null;
			}
			processCmd = null;
			mListener.onProgressNotification("Error occured : " + e);
			return;
		}
		if(channel != null && !channel.isClosed())
		{
			channel.close();
		}
		if(this.mUseReader.equalsIgnoreCase(MainActivity.READER_DF))
		{
			//  ...Cleanup
			handler.disable7816();
			handler.closeSession();
		}
		if (copni!= null && copni.isConnected())
		{
			if(!isContinue)
			{
				mListener.onProgressNotification("Disconnecting copni...");
				copni.disconnect();
				copni = null;
			}
		}
		if(!isContinue)
		{
			processCmd = null;
		}
		mListener.onProgressNotification("\n" + Process.SCRIPT_DONE);
	}
	
	private String isSelectCommand(byte[] apdu)
	{
		int i = 0;
		String aid = null;

		byte[] cmd = { (byte) 0x00, (byte) 0xA4, (byte) 0x04, (byte) 0x00 };

		while ((i < cmd.length) && (cmd[i] == apdu[i]))
		{
			i++;
		}

		if (i == cmd.length)
		{
			cmd = new byte[(int) apdu[i++]];
			for (int j = 0; i < apdu.length;)
			{
				cmd[j++] = apdu[i++];
			}
			aid = CryptoUtils.convertBytesToHexString(cmd);
		}

		return aid;
	}
	
	public void setIntent(Intent itn)
	{
		this.mIntent = itn;
	}
	
	/**
	 * is there any next command on next script ?
	 * @param isCont
	 */
	public void setIsContinue(boolean isCont)
	{
		this.isContinue = isCont;
	}
	
	public Copni getCurrentCopni()
	{
		return this.copni;
	}
	
	public void setProcessCommands(Commands cmd)
	{
		this.processCmd = cmd;
	}
	
	public Commands getProcessCommands()
	{
		return this.processCmd;
	}

}
