package com.heru.omatoolkit.util;

import android.content.Context;
import android.content.SharedPreferences;

public class Data {
	public static String KEY_READER = "Reader"; 
	public static String KEY_CHANNEL = "Channel";
	public static String KEY_SAVE_MODE = "SaveMode";
	public static String KEY_SECURE_CHANNEL = "SecureChannel";
	public static String KEY_ENC = "SetStaticKeyENC";
	public static String KEY_MAC = "SetStaticKeyMAC";
	public static String KEY_KEK = "SetStaticKeyKEK";
	public static String KEY_MASTER = "SetMasterKey";
	public static String KEY_METHOD_TYPE = "MethodType";
	public static String KEY_VERSION = "KeyVersion";
	public static String KEY_LAST_RESPONSE = "LastResponse";
	public static String KEY_GCM_MESSAGE = "GCMMessage";
	public static String KEY_NEW_ENC = "SetStaticKeyNewENC";
	public static String KEY_NEW_MAC = "SetStaticKeyNewMAC";
	public static String KEY_NEW_KEK = "SetStaticKeyNewKEK";
	
	private static SharedPreferences mSettings;
	
	public static void saveSettings(Context ctx, String []key, String []value)
	{
		mSettings = ctx.getSharedPreferences("com.morpho.omatoolkit", Context.MODE_PRIVATE);
		SharedPreferences.Editor editor = mSettings.edit();
		for(int i=0; i < key.length; i++)
		{
			editor.putString(key[i], value[i]);
		}
		editor.commit();
	}
	
	public static String getSettings(Context ctx, String key)
	{
		String res = "";
		mSettings = ctx.getSharedPreferences("com.morpho.omatoolkit", Context.MODE_PRIVATE);
		if(key.equalsIgnoreCase(KEY_READER))
		{
			res = mSettings.getString(key, "eSE");
		}
		if(key.equalsIgnoreCase(KEY_CHANNEL))
		{
			res = mSettings.getString(key, "Basic");
		}
		if(key.equalsIgnoreCase(KEY_SECURE_CHANNEL))
		{
			res = mSettings.getString(key, "0");
		}
		if(key.equalsIgnoreCase(KEY_SAVE_MODE))
		{
			res = mSettings.getString(key, "OFF");
		}
		if(key.equalsIgnoreCase(KEY_ENC) || key.equalsIgnoreCase(KEY_MAC) || key.equalsIgnoreCase(KEY_KEK))
		{
			res = mSettings.getString(key, "");
		}
		if(key.equalsIgnoreCase(KEY_NEW_ENC) || key.equalsIgnoreCase(KEY_NEW_MAC) || key.equalsIgnoreCase(KEY_NEW_KEK))
		{
			res = mSettings.getString(key, "");
		}
		if(key.equalsIgnoreCase(KEY_VERSION))
		{
			res = mSettings.getString(key, "0");
		}
		if(key.equalsIgnoreCase(KEY_LAST_RESPONSE))
		{
			res = mSettings.getString(key, "9000");
		}
		if(key.equalsIgnoreCase(KEY_GCM_MESSAGE))
		{
			res = mSettings.getString(key, "");
		}
		if(key.equalsIgnoreCase(KEY_MASTER))
		{
			res = mSettings.getString(key, "");
		}
		if(key.equalsIgnoreCase(KEY_METHOD_TYPE))
		{
			res = mSettings.getString(key, "1");
		}
		return res;
	}

}
