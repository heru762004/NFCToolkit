package com.heru.process;

import java.io.IOException;

import com.google.android.gms.gcm.GoogleCloudMessaging;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.AsyncTask;
import android.util.Log;

public class GCMHandler {
	private static String TAG = "NFCToolkit";
	private static final String SENDER_ID = "392439516866";
	private static String TAG_GCM_ID = "GCM_ID";
	private static String TAG_GCM_VERSION = "GCM_VERSION";
	
	private Context context;
	private GoogleCloudMessaging gcm;
	
	private GCMHandler.Callback callback;
	
	public GCMHandler(Context ctx, GCMHandler.Callback callback) {
		this.context = ctx;
		this.callback = callback;
		String gcmId = getRegistrationId();
		if (gcmId.isEmpty()) registerInBackground();
		else this.callback.onGCMRegistrationIdRetrieved(gcmId);
	}
	
	/**
	 * Gets the current registration ID for application on GCM service.
	 * <p>
	 * If result is empty, the app needs to register.
	 *
	 * @return registration ID, or empty string if there is no existing
	 *         registration ID.
	 */
	public String getRegistrationId() {
	    final SharedPreferences prefs = this.context.getSharedPreferences(TAG,
	            Context.MODE_PRIVATE);
	    String registrationId = prefs.getString(TAG_GCM_ID, "");
	    if (registrationId.isEmpty()) {
	        return "";
	    }
	    // Check if app was updated; if so, it must clear the registration ID
	    // since the existing regID is not guaranteed to work with the new
	    // app version.
	    int registeredVersion = prefs.getInt(TAG_GCM_VERSION, Integer.MIN_VALUE);
	    int currentVersion = getAppVersion(this.context.getApplicationContext());
	    if (registeredVersion != currentVersion) {
	        return "";
	    }
	    return registrationId;
	}
	
	/**
	 * Registers the application with GCM servers asynchronously.
	 * <p>
	 * Stores the registration ID and app versionCode in the application's
	 * shared preferences.
	 */
	private void registerInBackground() {
	    new AsyncTask<Void, Void, String>() {
	        @Override
	        protected String doInBackground(Void... params) {
	            String msg = "";
	            try {
	                if (gcm == null) {
	                    gcm  = GoogleCloudMessaging.getInstance(context.getApplicationContext());
	                }
	                // register 3 SENDER_ID to Google Server
	                String regid = gcm.register(SENDER_ID);
	                msg = "GCM Reg ID : " + regid;
	                Log.w(TAG, msg);

	                // Persist the regID - no need to register again.
	                storeRegistrationId(context.getApplicationContext(), regid);
	            } catch (IOException ex) {
	            	Log.e(TAG, "", ex);
	                msg = "Error :" + ex.getMessage();
	                // If there is an error, don't just keep trying to register.
	                // Require the user to click a button again, or perform
	                // exponential back-off.
	            }
	            return msg;
	        }

	        @Override
	        protected void onPostExecute(String msg) {
	            // mDisplay.append(msg + "\n");
	        	// Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show();
	        	String regid = getRegistrationId();
	        	if(regid != null && regid.length() > 0)
        		{
	        		callback.onGCMRegistrationIdRetrieved(regid);
        		}
	        	Log.w(TAG, msg);
	        }
	    }.execute(null, null, null);
	}
	
	/**
	 * Stores the registration ID and app versionCode in the application's
	 * {@code SharedPreferences}.
	 *
	 * @param context application's context.
	 * @param regId registration ID
	 */
	private void storeRegistrationId(Context context, String regId) {
	    final SharedPreferences prefs = this.context.getSharedPreferences(TAG,
	            Context.MODE_PRIVATE);
	    int appVersion = getAppVersion(context);
	    Log.i(TAG, "Saving regId on app version " + appVersion);
	    SharedPreferences.Editor editor = prefs.edit();
	    editor.putString(TAG_GCM_ID, regId);
	    editor.putInt(TAG_GCM_VERSION, appVersion);
	    editor.commit();
	}
	
	/**
	 * @return Application's version code from the {@code PackageManager}.
	 */
	private static int getAppVersion(Context context) {
	    try {
	        PackageInfo packageInfo = context.getPackageManager()
	                .getPackageInfo(context.getPackageName(), 0);
	        return packageInfo.versionCode;
	    } catch (NameNotFoundException e) {
	        // should never happen
	        throw new RuntimeException("Could not get package name: " + e);
	    }
	}
	
	public interface Callback {
		public void onGCMRegistrationIdRetrieved(String gcmId);
	}
}
