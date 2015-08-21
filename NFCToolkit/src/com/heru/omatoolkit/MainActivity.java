package com.heru.omatoolkit;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import org.simalliance.openmobileapi.SEService;
import org.simalliance.openmobileapi.SEService.CallBack;

import com.devicefidelity.lib.SDAPDUConnection;
import com.heru.omatoolkit.services.GcmIntentService;
import com.heru.omatoolkit.util.CryptoUtils;
import com.heru.omatoolkit.util.Data;
import com.heru.omatoolkit.util.Utils;
import com.heru.process.GCMHandler;
import com.heru.process.Process;
import com.otiglobal.copni.Copni;
import com.otiglobal.copni.CopniInterface.Component;
import com.otiglobal.copni.exception.CopniCommunicationException;

import android.net.Uri;
import android.nfc.NfcAdapter;
import android.nfc.tech.IsoDep;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentFilter.MalformedMimeTypeException;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;

public class MainActivity extends Activity implements CallBack, ProcessListener, GCMHandler.Callback {
	public static final String INTENT = "com.morpho.omatoolkit.MainActivity";
	
	public static final String READER_ESE = "eSE";
	public static final String READER_SIM = "SIM";
	public static final String READER_DF = "DF";
	public static final String READER_ISO_DEP = "IsoDep";
	public static final String READER_OTI = "OTI";
	public static final String READER_SIM_TM = "SIM-TM";
	
	public static final String CHANNEL_BASIC = "Basic";
	public static final String CHANNEL_LOGICAL = "Logical";
	
	private static String TAG = "NFCToolkit";
	
	private SEService seService;
	private TextView txtLog;
	private ScrollView textAreaScroller;
	
	private String strLog;
	private String useReader = READER_ESE;//"SIM" // "DF"; // "IsoDep"; // "OTI";
	private String useChannel = CHANNEL_BASIC; // "Logical"
	private boolean saveLog = false; // ON or OFF
	
	MenuItem readerItem, channelItem, saveLogItem;
	
	private String fullPath = null, urlScheme = null;
	
	
	private static String OMA_SERVER_URL = "http://192.168.1.2/oma/reg.php";
	
	private Process mProcess;
	private String appVersion;
	
	private String regid;
	
	private Dialog settingsDialog;
	
	private NfcAdapter nfcAdapter;
	private IntentFilter[] mFilters;
	private String[][] mTechLists;
	private PendingIntent mPendingIntent;
	
	private Intent mIntent;
	AlertDialog isoDepDialog;
	
	boolean isScriptCommand = false;
	boolean isHasNextCommand = false;
	private String fullCommand;
	
	private Copni copni;

	private final String PREFS_OMATOOLKIT = "NFCToolkit";
	private final String PREF_SCRIPT_PATH = "PathToScript";
	
	private int numberOfScript;
	private int numberOfExecutedScript;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		PackageManager manager = this.getPackageManager();
		PackageInfo info = null;
		
		new GCMHandler(this, this);
		
		removeNotification();
		registerReceiver(mHandleMessageReceiver, new IntentFilter(INTENT));
		
		try {
			info = manager.getPackageInfo(this.getPackageName(), 0);
			appVersion = info.versionName;
		} catch (NameNotFoundException e1) {
			// TODO Auto-generated catch block
			appVersion = "1.0";
			e1.printStackTrace();
		}

		SharedPreferences prefs = this.getSharedPreferences(PREFS_OMATOOLKIT,
				Context.MODE_PRIVATE);
		SharedPreferences.Editor prefEditor = prefs.edit();
		Bundle bundle = getIntent().getExtras();
		if (bundle == null) {
			Log.w(TAG, "checking for stored script path");

			fullPath = prefs.getString(PREF_SCRIPT_PATH, "");
			if (fullPath.isEmpty()) {
				Log.w(TAG, "too bad, script path not found in memory either");
				fullPath = null;
			} else {
				Log.i(TAG, "script path found " + fullPath);
			}
		} else {
			fullPath = bundle.getString("FilePath");
			Log.i(TAG, "path to script file is " + fullPath);
			urlScheme = bundle.getString("URLScheme");

			Log.w(TAG, "opening script " + fullPath
					+ "; storing it to the app's memory");
			if (fullPath != null && !fullPath.isEmpty()) {
				/*
				 * Store the script path
				 */
				prefEditor.putString(PREF_SCRIPT_PATH, fullPath);
				prefEditor.commit();
			}
		}
		setTitle(getString(R.string.app_name) +" v"+ appVersion);
		Utils.initScriptFolder(this.getApplicationContext());
		if(Data.getSettings(this, Data.KEY_READER)!= null && Data.getSettings(this, Data.KEY_READER).length() > 0)
		{
			useReader = Data.getSettings(this, Data.KEY_READER);
		}
		
		if(Data.getSettings(this, Data.KEY_SAVE_MODE).equalsIgnoreCase("OFF"))
		{
			saveLog = false;
		}
		else if(Data.getSettings(this, Data.KEY_SAVE_MODE).equalsIgnoreCase("ON"))
		{
			saveLog = true;
		}
		
		if(Data.getSettings(this, Data.KEY_CHANNEL) != null && Data.getSettings(this, Data.KEY_CHANNEL).length() > 0)
		{
			useChannel = Data.getSettings(this, Data.KEY_CHANNEL);
		}
		
		String []key = new String[1];
		key[0] = Data.KEY_MASTER;
		String []value = new String[1];
		value[0] = "";
		Data.saveSettings(this, key, value);
		
		strLog = "";
		try {
			// create API entry point
			seService = new SEService(this, this);
		} catch (SecurityException e) {
			updateLogText("Binding not allowed, uses-permission SMARTCARD?");
		} catch (Exception e) {
			updateLogText("Exception : " + e.getMessage());
		}
		
		nfcAdapter = NfcAdapter.getDefaultAdapter(this);
		
		mPendingIntent = PendingIntent.getActivity(this, 0, new Intent(this,
				getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);
		IntentFilter ndef = new IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED);
		try {
			ndef.addDataType("*/*");
		} catch (MalformedMimeTypeException e) {
			throw new RuntimeException("fail", e);
		}
		mFilters = new IntentFilter[] { ndef, };

		// Setup a tech list for all NfcF tags
		mTechLists = new String[][] { new String[] { IsoDep.class.getName() } };
	}
	
	@Override
	public void onResume() {
		super.onResume();
		// initialiseOnClickListeners();
		
		Log.w(TAG, "onResumse");
		if (nfcAdapter!=null) {
		nfcAdapter.enableForegroundDispatch(this, mPendingIntent, mFilters,
				mTechLists);
		} else {
			Log.e(TAG, "Device does not have NFC capability");
			Log.i(TAG, "Device does not have NFC capability");
		}
	}
	
	@Override
	public void onNewIntent(Intent intent) {
		updateLogText("Discovered Tag with Intent = "+intent);
		String action = intent.getAction();

		/*
		 * user tapped the card
		 */
		if (NfcAdapter.ACTION_TECH_DISCOVERED.equals(action)) {
			mIntent = intent;
			isoDepDialog.dismiss();
			if(!isScriptCommand)
			{
				runTest();
			}
			else
			{
				processScript(fullCommand);
			}
		}
	}
	
	private void registerNotificationToOMAServer() {
		final MainActivity act = this;
	    new AsyncTask<Void, Void, String>() {
	        @Override
	        protected String doInBackground(Void... params) {
	            String msg = "";
	            URL myurl = null;
				try {
					String serverURL = OMA_SERVER_URL + "?regid=" + regid;
					myurl = new URL(serverURL);
				} catch (MalformedURLException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
	            HttpURLConnection urlConnection = null;
				try {
					urlConnection = (HttpURLConnection) myurl.openConnection();
					urlConnection.setRequestMethod("GET");
					urlConnection.setDoOutput(true);
					urlConnection.setReadTimeout(10000);
			                    
					urlConnection.connect();
				    InputStream in = new BufferedInputStream(urlConnection.getInputStream());
				    BufferedReader reader = new BufferedReader(new InputStreamReader(in));
				    StringBuilder total = new StringBuilder();
				    String line;
				    while ((line = reader.readLine()) != null) {
				        total.append(line);
				    }
				    msg = total.toString();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					msg = "FAILED ";
				}
				urlConnection.disconnect();
				return msg;
	        }

	        @Override
	        protected void onPostExecute(String msg) {
	            // mDisplay.append(msg + "\n");
	        	updateLogText("\nServer Response : " + msg);
	        	Log.w(TAG, msg);
	        }
	    }.execute(null, null, null);
	}
	
	 protected void onDestroy()
	 {
		 if(seService != null)
			 seService.shutdown();
		 if(mHandleMessageReceiver != null)
			 unregisterReceiver(mHandleMessageReceiver);
		 Log.i(TAG, "seService destroyed");
		 super.onDestroy();
	 }
	
	 public void onPause()
	 {
		 super.onPause();
		 if(saveLog)
		 {
			 txtLog = (TextView) findViewById(R.id.txtLogView);
			 try {
				Utils.writeLog(strLog);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				System.out.println("Exception saveLog = " + e);
				e.printStackTrace();
			}
		 }
		 nfcAdapter.disableForegroundDispatch(this);
	 }
	 
	 private String getScapiVersion() {
	        try {
	            PackageInfo packageInfo = getPackageManager().getPackageInfo("android.smartcard", 0);
	            return packageInfo.versionName;
	        } catch (PackageManager.NameNotFoundException e1) {
	            try {
	                PackageInfo packageInfo = getPackageManager().getPackageInfo("org.simalliance.openmobileapi.service", 0);
	                return packageInfo.versionName;
	            } catch (PackageManager.NameNotFoundException e2) {
	                return "";
	            }
	        }
	    }
	
	private void runTest()
	{
		EditText txtCommand = (EditText) findViewById(R.id.txtCommand);
		if(txtCommand.getText().toString().equalsIgnoreCase("A0B000000A;"))
		{
			TelephonyManager telManager = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
			 
			String iccid = telManager.getSimSerialNumber();
			byte[] resp = Utils.nibbleSwap(iccid);
			updateLogText("Nibble Swap ICCID " + CryptoUtils.convertBytesToHexString(resp));
			updateLogText("\nProcess Script Done!");
		}
		else
		{
			EditText txtAID = (EditText) findViewById(R.id.txtAID);
			if(txtAID.getText().length() <= 0)
			{
				updateLogText("Error : AID must be filled!");
				return;
			}
			mProcess = new Process(this, seService, this, useReader, useChannel);
			if(useReader.equalsIgnoreCase("IsoDep"))
			{
				mProcess.setIntent(mIntent);
			}
			else if(useReader.equalsIgnoreCase("oti"))
			{
				if(copni == null)
					copni = new Copni(this);
				mProcess.setCopni(copni);
			}
			/**
			 * continue session on next script command.
			 */
			mProcess.setIsContinue(isHasNextCommand);
			mProcess.setParameter(txtAID.getText().toString(), txtCommand.getText().toString());
			mProcess.execute();
		}
	}
	
	private void printDeviceDetails()
	{
		updateLogText("Device Details : ");
//		updateLogText("Device Model : "+Build.BRAND + " " +Build.MODEL);
		updateLogText("Device Model : "+Build.MODEL);
		updateLogText("OS Ver. : "+Build.VERSION.RELEASE);
		updateLogText("SCAPI Ver. : " + getScapiVersion());
		updateLogText("NFCToolkit Ver. : " + appVersion);
//		txtLog = (TextView) findViewById(R.id.txtLogView);
//		txtLog.append("\n" + Html.fromHtml("\u4e00\u5361\u901a\u70ba\u53ef\u63d0\u4f9b\u591a\u7528\u9014\u652f\u4ed8\u4f7f\u7528\u4e4b\u96fb\u5b50\u7968\u8b49\uff0c\u4f7f\u7528\u7bc4\u570d\u5305\u542b\u4ea4\u901a\u904b\u8f38\u3001\u5c0f\u984d\u6d88\u8cbb\u3001\u653f\u5e9c\u898f\u8cbb\u7b49\u901a\\u8a"));
//		TelephonyManager mTelephonyMgr = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
//		String imsi = mTelephonyMgr.getSubscriberId();
//		updateLogText("IMSI : " + imsi);
		Copni cp = new Copni(this);
		try {
			updateLogText("Copni SDK Ver. : " + cp.getVersion(Component.PHONE_LIB));
		} catch (CopniCommunicationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		updateLogText("Device Fidelity API : " + SDAPDUConnection.getInstance().getAPIVersion());
		if(regid != null && regid.length() > 0)
		{
			updateLogText("GCM Reg ID : " + regid);
		}
		updateLogText("\n");
		
//		updateLogText("MAC = " + CryptoUtils.convertBytesToHexString(CryptoUtils.generateMAC(CryptoUtils.convertHexStringToByteArray("F460BB09BEB22A8670360DF8D7ACCD06"), CryptoUtils.convertHexStringToByteArray("80F24000"), CryptoUtils.convertHexStringToByteArray("4f00"), CryptoUtils.convertHexStringToByteArray("81A689894A9B3950"))));
	}
	
	private void updateLogText(String logText)
	{
		Utils.d(logText);
		strLog = strLog + "\n" + logText;
		textAreaScroller = (ScrollView) findViewById(R.id.textAreaScroller);
		txtLog = (TextView) findViewById(R.id.txtLogView);
		txtLog.append("\n" + logText);
		textAreaScroller.post(new Runnable() {
			
			@Override
			public void run() {
				textAreaScroller.scrollTo(0, txtLog.getHeight());
			}
		});
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		readerItem = menu.findItem(R.id.action_channel);
		channelItem = menu.findItem(R.id.action_basic_logical);
		saveLogItem = menu.findItem(R.id.action_save_log_exit);
		
		if(useReader.equalsIgnoreCase(READER_SIM)) {
			readerItem.setTitle(getString(R.string.action_channel_sim));
			channelItem.setVisible(true);
    	} else if(useReader.equalsIgnoreCase(READER_DF)) {
			readerItem.setTitle(getString(R.string.action_channel_df));
			channelItem.setVisible(false);
		} else if(useReader.equalsIgnoreCase(READER_ISO_DEP)) {
			readerItem.setTitle(getString(R.string.action_channel_iso));
			channelItem.setVisible(false);
		} else if(useReader.equalsIgnoreCase(READER_ESE)) {
			readerItem.setTitle(getString(R.string.action_channel_ese));
			channelItem.setVisible(true);
		} else if(useReader.equalsIgnoreCase(READER_OTI)) {
			readerItem.setTitle(getString(R.string.action_channel_oti));
			channelItem.setVisible(false);
		} else if(useReader.equalsIgnoreCase(READER_SIM_TM)) {
			readerItem.setTitle(getString(R.string.action_channel_sim_tm));
		}
		
		if(useChannel.equalsIgnoreCase(CHANNEL_BASIC))
    	{
			channelItem.setTitle(getString(R.string.action_basic_channel));
    	}
		else if(useChannel.equalsIgnoreCase(CHANNEL_LOGICAL))
		{
			channelItem.setTitle(getString(R.string.action_logical_channel));
		}
		
		if(saveLog)
		{
			saveLogItem.setTitle(getString(R.string.action_save_log_on));
		}
		else
		{
			saveLogItem.setTitle(getString(R.string.action_save_log_off));
		}
		return true;
	}
	
	private void processScript(String cmd)
	{
		numberOfScript = 0;
		numberOfExecutedScript = 0;
		isHasNextCommand = false;
		if(cmd != null && cmd.length() > 0)
		{
			String []splitCmd = cmd.split(";");
			EditText txtCommand = (EditText) findViewById(R.id.txtCommand);
			txtCommand.setText("");
			boolean isNeedExecute = false;
			for(int i=0; i < splitCmd.length; i++)
			{
				if(splitCmd[i].toLowerCase().startsWith("select"))
				{
					numberOfScript++;
					if(isNeedExecute == true)
					{
						runTest();
						txtCommand.setText("");
					}
					isNeedExecute = true;
					EditText txtAID = (EditText) findViewById(R.id.txtAID);
					String [] split2 = splitCmd[i].split(" ");
					if(split2.length > 1) txtAID.setText(split2[1]);
				}
				else if(splitCmd[i].toLowerCase().startsWith("sendapdu"))
				{
					String [] split2 = splitCmd[i].split(" ");
					if(split2.length > 1) txtCommand.setText(txtCommand.getText().toString() + split2[1]);
					if(split2.length > 2)
					{
						txtCommand.setText(txtCommand.getText().toString() + "|" + split2[2]);
					}
					txtCommand.setText(txtCommand.getText().toString() + ";");
				}
				else if(splitCmd[i].toLowerCase().equalsIgnoreCase("sendapdu A0B000000A"))
				{
					txtCommand.setText(txtCommand.getText().toString() + "A0B000000A" +";");
				}
				else if(splitCmd[i].toLowerCase().startsWith("setsecurechannel"))
				{
					String [] split2 = splitCmd[i].split(" ");
					if(split2.length > 1)
					{
						String[] key = new String[1];
						key[0] = Data.KEY_SECURE_CHANNEL;
						String [] value = new String[1];
						value[0] = split2[1];
						Data.saveSettings(this, key, value);
					}
				}
				else if(splitCmd[i].toLowerCase().startsWith("setstatickey"))
				{
					String [] split2 = splitCmd[i].split(" ");
					String value1 = "";
					String key1 = "";
					if(split2.length > 1)
					{
						value1 = split2[1];
					}
					if(split2[0].substring(split2[0].length() - 3, split2[0].length()).equalsIgnoreCase("enc"))
					{
						key1 = Data.KEY_ENC;
					}
					else if(split2[0].substring(split2[0].length() - 3, split2[0].length()).equalsIgnoreCase("mac"))
					{
						key1 = Data.KEY_MAC;
					}
					else if(split2[0].substring(split2[0].length() - 3, split2[0].length()).equalsIgnoreCase("kek"))
					{
						key1 = Data.KEY_KEK;
					}
					String []key = new String[1];
					key[0] = key1;
					String []value = new String[1];
					value[0] = value1;
					Data.saveSettings(this, key, value);
				}
				else if(splitCmd[i].toLowerCase().startsWith("setmasterkey"))
				{
					String [] split2 = splitCmd[i].split(" ");
					String masterKey = "";
					// default type a new one
					// the old one that we used on airtag cleaner project
					int methodType = 1;
					if(split2.length > 1)
					{
						masterKey = split2[1];
					}
					if(split2.length  > 2)
					{
						try {
							methodType = Integer.parseInt(split2[2]);
						} catch (Exception e) {
							// TODO: handle exception
							methodType = 1;
						}
					}
					else
					{
						methodType = 1;
					}
					String []key = new String[2];
					key[0] = Data.KEY_MASTER;
					key[1] = Data.KEY_METHOD_TYPE;
					String []value = new String[2];
					value[0] = masterKey;
					value[1] = methodType + "";
					Data.saveSettings(this, key, value);
					
				}
				else if(splitCmd[i].toLowerCase().startsWith("initupdate"))
				{
					String [] split2 = splitCmd[i].split(" ");
					if(split2.length > 1)
					{
						String []key = new String[1];
						key[0] = Data.KEY_VERSION;
						String []value = new String[1];
						value[0] = split2[1];
						Data.saveSettings(this, key, value);
						
						txtCommand.setText(txtCommand.getText().toString() + "80500000" +";");
					}
				}
				else if(splitCmd[i].toLowerCase().startsWith("extauth"))
				{
					String [] split2 = splitCmd[i].split(" ");
					if(split2.length > 1) {
						// if secure channel = 1
						if(split2[1].equalsIgnoreCase("202")) {
							txtCommand.setText(txtCommand.getText().toString() + "84820300" +";");
						} else {
							if(Data.getSettings(this, Data.KEY_SECURE_CHANNEL).equalsIgnoreCase("1"))
								txtCommand.setText(txtCommand.getText().toString() + "84820100" +";");
							else	
								txtCommand.setText(txtCommand.getText().toString() + "84820000" +";");
						}
					}
				}
				else if(splitCmd[i].toLowerCase().startsWith("delete"))
				{
					String [] split2 = splitCmd[i].split(" ");
					if(split2.length > 1)
					{
						txtCommand.setText(txtCommand.getText().toString() + splitCmd[i] + ";");
					}
				}
				else if(splitCmd[i].toLowerCase().startsWith("setnewstatickey"))
				{
					String [] split2 = splitCmd[i].split(" ");
					String value1 = "";
					String key1 = "";
					if(split2.length > 1)
					{
						value1 = split2[1];
					}
					if(split2[0].substring(split2[0].length() - 3, split2[0].length()).equalsIgnoreCase("enc"))
					{
						key1 = Data.KEY_NEW_ENC;
					}
					else if(split2[0].substring(split2[0].length() - 3, split2[0].length()).equalsIgnoreCase("mac"))
					{
						key1 = Data.KEY_NEW_MAC;
					}
					else if(split2[0].substring(split2[0].length() - 3, split2[0].length()).equalsIgnoreCase("kek"))
					{
						key1 = Data.KEY_NEW_KEK;
					}
					String []key = new String[1];
					key[0] = key1;
					String []value = new String[1];
					value[0] = value1;
					Data.saveSettings(this, key, value);
				}
				else if(splitCmd[i].toLowerCase().startsWith("putkey"))
				{
					String [] split2 = splitCmd[i].split(" ");
					if(split2.length > 1)
					{
						txtCommand.setText(txtCommand.getText().toString() + splitCmd[i] + ";");
					}
				}
				else if (splitCmd[i].toLowerCase().startsWith("//"))
				{
					
				}
				else if (splitCmd[i].toLowerCase().startsWith("devicelog"))
				{
					String [] split2 = splitCmd[i].split(" ");
					if(split2.length > 1)
					{
						updateLogText("devicelog "+split2[1]);
						if(split2[1].equalsIgnoreCase("1"))
						{
							saveLog = true;
							setSaveMode(saveLog);
						}
						else
						{
							saveLog = false;
							setSaveMode(saveLog);
						}
					}
					else
					{
						saveLog = false;
						setSaveMode(saveLog);
					}
					if(saveLogItem != null)
					{
						if(saveLog)
						{
							saveLogItem.setTitle(getString(R.string.action_save_log_on));
						}
						else
						{
							saveLogItem.setTitle(getString(R.string.action_save_log_off));
						}
					}
				}
				else if(splitCmd[i].toLowerCase().startsWith("setreader"))
				{
					String [] split2 = splitCmd[i].split(" ");
					if(split2.length > 1)
					{
						updateLogText("setreader "+split2[1]);
						if(split2[1].toLowerCase().equalsIgnoreCase("sim")) {
							setReader(READER_SIM);
						} else if(split2[1].toLowerCase().equalsIgnoreCase("df")) {
							setReader(READER_DF);
						} else if(split2[1].toLowerCase().equalsIgnoreCase("isodep")) {
							setReader(READER_ISO_DEP);
						} else if(split2[1].toLowerCase().equalsIgnoreCase("oti")) {
							setReader(READER_OTI);
						} else if(split2[1].toLowerCase().equalsIgnoreCase("sim-tm")) {
							setReader(READER_SIM_TM);
						} else {
							setReader(READER_ESE);
						}
					}
				}
				else if(splitCmd[i].toLowerCase().startsWith("setchannel"))
				{
					String [] split2 = splitCmd[i].split(" ");
					if(split2.length > 1)
					{
						updateLogText("setchannel "+split2[1]);
						if(split2[1].toLowerCase().equalsIgnoreCase("logical"))
						{
							setChannel(CHANNEL_LOGICAL);
						}
						else
						{
							setChannel(CHANNEL_BASIC);
						}
					}
				}
				else if(splitCmd[i].toLowerCase().startsWith("loadapplet"))
				{
					String [] split2 = splitCmd[i].split(" ");
					if(split2.length > 1)
					{
						updateLogText("loadapplet "+split2[1]);
						String finalCommand = "loadapplet "+split2[1];
						txtCommand.setText(txtCommand.getText().toString() + finalCommand + ";");
					}
					else
					{
						updateLogText("no applet found!");
					}
				}
				else if(splitCmd[i].toLowerCase().startsWith("continue"))
				{
					isHasNextCommand = true;
				}	
				else
				{
					onProgressNotification("Error occured : Unsupported Command '"+ splitCmd[i].toLowerCase()+"'");
					return;
				}
			}
			runTest();
		}
	}
	
	private void showTapCommand()
	{
		isoDepDialog = new AlertDialog.Builder(this).create();
		isoDepDialog.setTitle("");
		isoDepDialog.setMessage("Please Tap the Card!");
		isoDepDialog.setButton("Cancel",  new DialogInterface.OnClickListener() {
    		
			@Override
			public void onClick(DialogInterface dialog, int which)  {
				// TODO Auto-generated method stub
				dialog.dismiss();
				return;
			}
		});
		isoDepDialog.show();
	}
	
	public boolean onOptionsItemSelected(MenuItem item)
    {
		if(item.getItemId() == R.id.action_run_script)
		{
			strLog = "";
			txtLog = (TextView) findViewById(R.id.txtLogView);
			txtLog.setText("");
			if(fullPath != null)
			{
				updateLogText("Script "+fullPath+" Run!\n");
			}
			printDeviceDetails();
			String cmd = Utils.readFile(fullPath, urlScheme, this.getApplicationContext());
			isScriptCommand = true;
			if(useReader.equalsIgnoreCase(READER_ISO_DEP))
			{
				fullCommand = cmd;
				if(cmd.toLowerCase().contains("setreader isodep"))
				{
					showTapCommand();
				}
				else if(!cmd.toLowerCase().contains("setreader"))
				{
					showTapCommand();
				}
				else
				{
					processScript(cmd);
				}
			}
			else
			{
				if(cmd.toLowerCase().contains("setreader isodep"))
				{
					fullCommand = cmd;
					showTapCommand();
				}
				else
				{
					processScript(cmd);
				}
			}
		}
		if(item.getItemId() == R.id.action_channel)
		{
			settingsDialog = new Dialog(this);
			settingsDialog.getWindow().requestFeature(Window.FEATURE_NO_TITLE); 
			settingsDialog.setContentView(getLayoutInflater().inflate(R.layout.button_layout 
			        , null)); 
			TextView txtTitle = (TextView)settingsDialog.findViewById(R.id.textTitle);
			txtTitle.setText("Select Reader\n\nCurrent Reader : " + useReader+"\nChange into : ");
			settingsDialog.show();
		}
		
		if(item.getItemId() == R.id.action_basic_logical)
		{
	    	AlertDialog alertDialog;
	    	alertDialog = new AlertDialog.Builder(this).create();
	    	alertDialog.setTitle("Select Channel");
	    	alertDialog.setMessage("Current Channel : " + useChannel+"\nChange into : ");
	    	if(useChannel.equalsIgnoreCase(CHANNEL_BASIC))
	    	{
		    	alertDialog.setButton(CHANNEL_LOGICAL,  new DialogInterface.OnClickListener() {
		
					@Override
					public void onClick(DialogInterface dialog, int which)  {
						// TODO Auto-generated method stub
						setChannel(CHANNEL_LOGICAL);
						return;
					}
				});
	    	}
	    	else
	    	{
		    	alertDialog.setButton(CHANNEL_BASIC,  new DialogInterface.OnClickListener() {
		
					@Override
					public void onClick(DialogInterface dialog, int which)  {
						// TODO Auto-generated method stub
						setChannel(CHANNEL_BASIC);
						return;
					}
				});
	    	}
	    	alertDialog.setButton2("Cancel", new DialogInterface.OnClickListener() {
		
					@Override
					public void onClick(DialogInterface dialog, int which)  {
						// TODO Auto-generated method stub
						dialog.dismiss();
						return;
					}
			});
	    	alertDialog.show();
	    	alertDialog = null;
		}
		if(item.getItemId() == R.id.action_save_log_exit)
		{
			if(saveLog)
			{
				saveLog = false;
				saveLogItem.setTitle(getString(R.string.action_save_log_off));
			}
			else
			{
				saveLog = true;
				saveLogItem.setTitle(getString(R.string.action_save_log_on));
			}
			setSaveMode(saveLog);
		}
		if(item.getItemId() == R.id.action_send_log)
		{
			txtLog = (TextView) findViewById(R.id.txtLogView);
			 try {
				Utils.writeLog(strLog);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				System.out.println("Exception saveLog = " + e);
				e.printStackTrace();
			}
			String PATH =  Environment.getExternalStorageDirectory()+Utils.getLogPath();

			Uri uri = Uri.parse("file://"+PATH);
			Intent i = new Intent(Intent.ACTION_SEND);
			i.setType("text/plain");
			String address = "heru.prasetia.ext@morpho.com";
			i.putExtra(Intent.EXTRA_EMAIL,new String[] { address });
			i.putExtra(Intent.EXTRA_SUBJECT,"OMA Log");
			i.putExtra(Intent.EXTRA_TEXT,"Attached is OMA Log");
			i.putExtra(Intent.EXTRA_STREAM, uri);
			i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			i.setClassName("com.google.android.gm", "com.google.android.gm.ComposeActivityGmail");//sending email via gmail
			try
			{
				this.startActivity(i);
			}
			catch (Exception e)
			{
				this.startActivity(Intent.createChooser(i, "Select application"));
			}
		}
		if(item.getItemId() == R.id.action_connect_to_oma)
		{
			updateLogText("\nTrying to connect to : " + OMA_SERVER_URL + "?regid=" + regid);
			registerNotificationToOMAServer();
		}
		if(item.getItemId() == R.id.action_help) {
			Intent itn = new Intent(this, HelpActivity.class);
			startActivity(itn);
		}
    	return true;
    }
	
	private void setReader(String reader)
	{
		useReader = reader;
		updateLogText("useReader change into " + reader);
		String []key = new String[1];
		key[0] = Data.KEY_READER;
		String []value = new String[1];
		value[0] = reader;
		Data.saveSettings(this, key, value);
		
		if(readerItem != null && channelItem != null)
		{
			if(useReader.equalsIgnoreCase(READER_SIM)) {
				readerItem.setTitle(getString(R.string.action_channel_sim));
				channelItem.setVisible(true);
	    	} else if(useReader.equalsIgnoreCase(READER_DF)) {
				readerItem.setTitle(getString(R.string.action_channel_df));
				channelItem.setVisible(false);
			} else if(useReader.equalsIgnoreCase(READER_ISO_DEP)) {
				readerItem.setTitle(getString(R.string.action_channel_iso));
				channelItem.setVisible(false);
			} else if(useReader.equalsIgnoreCase(READER_ESE)) {
				readerItem.setTitle(getString(R.string.action_channel_ese));
				channelItem.setVisible(true);
			} else if(useReader.equalsIgnoreCase(READER_SIM_TM)) {
				readerItem.setTitle(getString(R.string.action_channel_sim_tm));
				channelItem.setVisible(true);
			} else {
				readerItem.setTitle(getString(R.string.action_channel_oti));
				channelItem.setVisible(false);
			}
		}
	}
	
	private void setChannel(String channel)
	{
		useChannel = channel;
		updateLogText("useChannel change into " + channel);
		String []key = new String[1];
		key[0] = Data.KEY_CHANNEL;
		String []value = new String[1];
		value[0] = channel;
		Data.saveSettings(this, key, value);
		
		if(channelItem != null)
		{
			if(useChannel.equalsIgnoreCase(CHANNEL_BASIC))
	    	{
				channelItem.setTitle(getString(R.string.action_basic_channel));
	    	}
			else
			{
				channelItem.setTitle(getString(R.string.action_logical_channel));
			}
		}
	}
	
	private void setSaveMode(boolean mode)
	{
		saveLog = mode;
		String []key = new String[1];
		key[0] = Data.KEY_SAVE_MODE;
		String []value = new String[1];
		if(mode)
		{
			value[0] = "ON";
		}
		else
		{
			value[0] = "OFF";
		}
		Data.saveSettings(this, key, value);
	}

	@Override
	public void serviceConnected(SEService arg0) {
		// TODO Auto-generated method stub
		final MainActivity act = this;
		updateLogText("Service Connected\n");
		printDeviceDetails();
		
		if(fullPath != null)
		{
			updateLogText("Script "+fullPath+" loaded..");
		}
		EditText txtAID = (EditText) findViewById(R.id.txtAID);
//		txtAID.setText("a0000000871001ff33ffff8901010100");
//		txtAID.setText("a00000003052010000000001");
//		txtAID.setText("A00000000453504200010501");
//		txtAID.setText("A000000151000000");
		txtAID.setText("A0000000031010");
		EditText txtCommand = (EditText) findViewById(R.id.txtCommand);
//		txtCommand.setText("a0a40000023f00;a0a40000027f20;a0a40000026f07;a0b0000009");
		Button btn = (Button) findViewById(R.id.btnRun);
		btn.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				isScriptCommand = false;
				// reset the key secure channel
				String[] key = new String[1];
				key[0] = Data.KEY_SECURE_CHANNEL;
				String [] value = new String[1];
				value[0] = "0";
				Data.saveSettings(MainActivity.this, key, value);
				
				if(!useReader.equalsIgnoreCase("IsoDep"))
				{
					runTest();
				}
				else
				{
					isoDepDialog = new AlertDialog.Builder(act).create();
					isoDepDialog.setTitle("");
					isoDepDialog.setMessage("Please Tap the Card!");
					isoDepDialog.setButton("Cancel",  new DialogInterface.OnClickListener() {
			    		
						@Override
						public void onClick(DialogInterface dialog, int which)  {
							// TODO Auto-generated method stub
							dialog.dismiss();
							return;
						}
					});
					isoDepDialog.show();
				}
			}
		});
	}

	@Override
	public void onProgressNotification(final String text) {
		// TODO Auto-generated method stub
		runOnUiThread(new Runnable() {
            @Override
            public void run() {
				/*
				 * check if the script is done executing
				 */
            	if(text == null) return;
            	updateLogText(text);
				String str = text.replaceAll("(\\r|\\n)", "");
				if (str.equalsIgnoreCase(Process.SCRIPT_DONE) || str.toLowerCase().startsWith("error occured")) {
					 if(saveLog)
					 {
						 txtLog = (TextView) findViewById(R.id.txtLogView);
						 try {
							Utils.writeLog(strLog);
						} catch (Exception e) {
							// TODO Auto-generated catch block
							System.out.println("Exception saveLog = " + e);
							e.printStackTrace();
						}
					 }
					 if(str.equalsIgnoreCase(Process.SCRIPT_DONE))
					 {
						 numberOfExecutedScript++;
					 }
					 else if(str.toLowerCase().startsWith("error occured"))
					 {
						 numberOfExecutedScript = numberOfScript;
						 return;
					 }
					 if(numberOfExecutedScript == numberOfScript)
						 Utils.d("Process Finished");
					 copni = mProcess.getCurrentCopni();
				}
            }
		});
	}
	
	
	private final BroadcastReceiver mHandleMessageReceiver = new BroadcastReceiver() 
    {
        @Override
        public void onReceive(Context context, Intent intent) 
        {
        	String command = "";
        	strLog = "";
			printDeviceDetails();
			
			Bundle extras = intent.getExtras();
			if(copni == null)
			{
				String []keys = new String[1];
	    		keys[0] = Data.KEY_MASTER;
	    		String []values = new String[1];
	    		values[0] = "";
	    		Data.saveSettings(MainActivity.this, keys, values);
			}
    		
        	for (String key : extras.keySet()) {
        		
        	    Object value = extras.get(key);
        	    if (key.equalsIgnoreCase("CodeText")) {
        	    	updateLogText("Message : " + value.toString());
        	    	command = value.toString();
        	    }
        	    if (key.equalsIgnoreCase("CodeKey")) {
        	    	fullPath = Environment.getExternalStorageDirectory() + "/NFCToolkit/temp.hp";
        	    	String cmd = Utils.readFile(fullPath, urlScheme, MainActivity.this.getApplicationContext());
        	    	command = cmd;
        	    }
        	}
        	if(command.length() > 0) {
        		if(command.toLowerCase().contains("setreader isodep"))
				{
        			isScriptCommand = true;
        			fullCommand = command;
					showTapCommand();
				} else {
					processScript(command);
				}
        	}
        	removeNotification();
        }
    };
    
    private void removeNotification()
    {
    	NotificationManager mNotificationManager = (NotificationManager)
                this.getSystemService(Context.NOTIFICATION_SERVICE);
		mNotificationManager.cancel(GcmIntentService.NOTIFICATION_ID);
    }

	@Override
	public void onProgressLog(String text) {
		// TODO Auto-generated method stub
		Utils.d(text);
		strLog = strLog + "\n" + text;
	}
	
	public void dismissListener(View v)
	{
		Button btn = (Button)v;
		String title = btn.getText().toString();
		if(title.equalsIgnoreCase(getResources().getString(R.string.channel_ese))) {
			setReader(READER_ESE);
		}
		else if(title.equalsIgnoreCase(getResources().getString(R.string.channel_df))) {
			setReader(READER_DF);
		}
		else if(title.equalsIgnoreCase(getResources().getString(R.string.channel_sim))) {
			setReader(READER_SIM);
		}
		else if(title.equalsIgnoreCase(getResources().getString(R.string.channel_iso))) {
			setReader(READER_ISO_DEP);
		}
		else if(title.equalsIgnoreCase(getResources().getString(R.string.channel_oti))) {
			setReader(READER_OTI);
		}
		else if(title.equalsIgnoreCase(getResources().getString(R.string.channel_sim_tm))) {
			setReader(READER_SIM_TM);
		}
		else if(title.equalsIgnoreCase("")) {}
		settingsDialog.dismiss();
	}

	@Override
	public void onGCMRegistrationIdRetrieved(String gcmId) {
		// TODO Auto-generated method stub
		regid = gcmId;
	}
}
