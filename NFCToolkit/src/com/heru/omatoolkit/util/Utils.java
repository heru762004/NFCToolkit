package com.heru.omatoolkit.util;


import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.security.InvalidKeyException;
import java.util.Hashtable;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.DESKeySpec;
import javax.crypto.spec.DESedeKeySpec;
import javax.crypto.spec.IvParameterSpec;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;

public class Utils {
	private static String LOG_ID = "NFCToolkit";
	private static String MAIN_PATH = "/NFCToolkit";
	private static String CAP_FILE_PATH = "/cap";
	private static String SCRIPT_PATH = "/NFCToolkit/script.hp";
	private static String LOG_PATH = "/NFCToolkit/log_oma.txt";
	private static String DEFAULT_SCRIPT_FILENAME = "script.hp";
	
	private static Utils	instance	= new Utils();
	private long			zeroTime	= System.currentTimeMillis();
	private StringBuffer	mLog		= new StringBuffer(100);
	private File cacheDir = null;
	

	public static void d(String text)
	{
		instance.debug(text);
	}

	public static void e(String text)
	{
		instance.debug(text);
	}

	private void debug(String text)
	{
//		mLog.append(text).append('\n');
		Log.d(LOG_ID, text);
	}

	public static String view()
	{
		return instance.getLog();
	}

	private String getLog()
	{
		return mLog.toString();
	}
	
	public static String getLogPath()
	{
		return LOG_PATH;
	}
	
	public static String getCapFilePath()
	{
		String path = Environment.getExternalStorageDirectory()+MAIN_PATH+CAP_FILE_PATH;
		return path;
	}
	
	public static void initScriptFolder(Context context)
	{
		try {

			File fconn = null;
			fconn = new File(Environment.getExternalStorageDirectory(), MAIN_PATH);
			Utils.d("CREATE FOLDER ON "+Environment.getExternalStorageDirectory()+MAIN_PATH);
			if(!fconn.exists())fconn.mkdir();
			fconn = new File(Environment.getExternalStorageDirectory(), MAIN_PATH + CAP_FILE_PATH);
			if(!fconn.exists())fconn.mkdir();
			InputStream in = context.getAssets().open(DEFAULT_SCRIPT_FILENAME);
			if(!new File(Environment.getExternalStorageDirectory()+SCRIPT_PATH).exists())
			{
				FileOutputStream out = new FileOutputStream(Environment.getExternalStorageDirectory()+SCRIPT_PATH);
				byte[] buff = new byte[1024];
				int read = 0;

				try {
					while ((read = in.read(buff)) > 0) {
						out.write(buff, 0, read);
					}
				} finally {
					in.close();

					out.close();
				}
			}
		} catch (Exception ioe) {
			Utils.d("create directory Exception: "+ioe.getMessage());
		}
	}
	
	public static void save(String fileName, Context c)
	{
		instance.saveLog(fileName, c);
	}
	
	public static String readFile(String path, String scheme, Context ctx)
	{
		return instance.readScript(path, scheme, ctx);
	}
	
	public static byte[] nibbleSwap(String in)
	{
		if(in.length() % 2 > 0)
		{
			in += "F";
		}
		String result = "";
		for(int i=0; i < in.length(); i+=2)
		{
			char nibble0 = in.charAt(i);
			if((i + 1) < in.length())
			{
				char nibble1 = in.charAt(i+1);
				result = result + nibble1 + nibble0;
			}
		}
		result += "9000";
		return CryptoUtils.convertHexStringToByteArray(result);
	}

	private String readScript(String path, String scheme, Context ctx)
	{
		String state = Environment.getExternalStorageState();
		String res = null;
		if (Environment.MEDIA_MOUNTED.equals(state))
		{
			try
			{
				res = openScriptFile(path, scheme, ctx);
			}
			catch (Exception e)
			{
				res = null;
				Utils.d("saveLog Exception: " + e.getMessage());
			}
		}
		return res;
	}
	
	private String openScriptFile(String path, String scheme, Context ctx)
	{
		String res = null;
		try {

			File fconn = null;
			if(path == null)
			{
				fconn = new File(Environment.getExternalStorageDirectory(), SCRIPT_PATH);
			}
			else
			{
				if(scheme != null)
				{
					if (ContentResolver.SCHEME_CONTENT.equals(scheme)) {
					    // handle as content uri
						Uri myUri = Uri.parse(path);
						InputStream fl = ctx.getContentResolver().openInputStream(myUri);
						String ret = convertStreamToString(fl);
						res = ret;
					} else {
					    // handle as file uri
						fconn = new File(path);
					}
				}
				else
				{
					fconn = new File(path);
				}
			}
			if(fconn.exists())
			{
				FileInputStream fl = new FileInputStream(fconn);
				String ret = convertStreamToString(fl);
				res = ret;
			}
		} catch (Exception ioe) {
			Utils.d("failed open script file : "+ioe.getMessage());
//			throw ioe;
			res = null;
		}
		return res;
	}
	

	public static String convertStreamToString(InputStream is) throws Exception
	{
		BufferedReader reader = new BufferedReader(new InputStreamReader(is));
		StringBuilder sb = new StringBuilder();
		String line = null;
		while ((line = reader.readLine()) != null) {
			sb.append(line);
		}
		return sb.toString();
	}
	
	private void saveLog(String logName, Context context)
	{
		String filepath = "";
		String state = Environment.getExternalStorageState();
		
		if (Environment.MEDIA_MOUNTED.equals(state))
		{
			try
			{
				d("\n");
//				writeLog(filepath + logName);
				Utils.d("SAVE LOG TO "+Environment.getExternalStorageDirectory()+logName);
			}
			catch (Exception e)
			{
				System.out.println("saveLog Exception: " + e.getMessage());
			}
		}
	}

	public static void writeLog(String stringLog) throws Exception
	{
		try {
			String string = stringLog;
			byte data[] = string.getBytes();


			File fconn = null;
			fconn = new File(Environment.getExternalStorageDirectory(), MAIN_PATH);
			
			if(!fconn.exists())fconn.mkdir();
			fconn = new File(Environment.getExternalStorageDirectory(), LOG_PATH);
			if(fconn.exists()) fconn.delete();
			fconn.createNewFile();
	        OutputStream ops = new FileOutputStream(fconn);
			ops.write(data);
			ops.close();
			fconn = null;
		} catch (IOException ioe) {
			Utils.d("writeLog IOException: "+ioe.getMessage());
//			throw ioe;
		} catch (SecurityException se) {
			Utils.d("writeLog SecurityException: " + se.getMessage());
//			throw se;
		}
	}
	
}
