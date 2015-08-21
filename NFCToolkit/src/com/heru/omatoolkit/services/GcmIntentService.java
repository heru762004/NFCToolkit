package com.heru.omatoolkit.services;

import com.google.android.gms.gcm.GoogleCloudMessaging;
import com.heru.omatoolkit.MainActivity;
import com.heru.omatoolkit.receivers.GcmBroadcastReceiver;
import com.heru.omatoolkit.util.Data;
import com.heru.omatoolkit.util.Utils;

import android.app.IntentService;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

public class GcmIntentService extends IntentService {
	public static final int NOTIFICATION_ID = 1;
	private static final String TAG = "GcmIntentService";
	
    private NotificationManager mNotificationManager;
    NotificationCompat.Builder builder;

    public GcmIntentService() {
        super("GcmIntentService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Bundle extras = intent.getExtras();
        GoogleCloudMessaging gcm = GoogleCloudMessaging.getInstance(this);
        // The getMessageType() intent parameter must be the intent you received
        // in your BroadcastReceiver.
        String messageType = gcm.getMessageType(intent);

        if (!extras.isEmpty()) {  // has effect of unparcelling Bundle
            /*
             * Filter messages based on message type. Since it is likely that GCM
             * will be extended in the future with new message types, just ignore
             * any message types you're not interested in, or that you don't
             * recognize.
             */
            if (GoogleCloudMessaging.
                    MESSAGE_TYPE_SEND_ERROR.equals(messageType)) {
//                sendNotification(null);
            } else if (GoogleCloudMessaging.
                    MESSAGE_TYPE_DELETED.equals(messageType)) {
//                sendNotification(null);
            // If it's a regular GCM message, do some work.
            } else if (GoogleCloudMessaging.
                    MESSAGE_TYPE_MESSAGE.equals(messageType)) {
            	Log.w(TAG, "Received GCM message");
            	Log.i(TAG, "number of extras are "+extras.size());
               
            	Intent itn = new Intent("com.morpho.omatoolkit.MainActivity");
            	itn.putExtras(extras);
//            	sendNotification(extras, itn);
            	
                // Post notification of received message.
            	
            	sendBroadcast(itn);
                // Log.i(TAG, "Received: " + extras.toString());
            }
        }
        // Release the wake lock provided by the WakefulBroadcastReceiver.
        GcmBroadcastReceiver.completeWakefulIntent(intent);
    }
    
    

    // Put the message into a notification and post it.
    // This is just one simple example of what you might choose to do with
    // a GCM message.
    private void sendNotification(Bundle extras, Intent intent) {
    	if (extras==null) {
    		Log.e(TAG, "error");
    	} else {
    	String message = "NFCToolkit Message";
        mNotificationManager = (NotificationManager)
                this.getSystemService(Context.NOTIFICATION_SERVICE);
//        Intent intent = new Intent(this, ActivatedServicesActivity.class);
//        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtras(extras);// Can I receive these extra in the destiantion activity?
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                intent, PendingIntent.FLAG_UPDATE_CURRENT);
        
        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(this)
        .setSmallIcon(com.heru.omatoolkit.R.drawable.ic_launcher)
        .setContentTitle("NFCToolkit")
        .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
        .setContentText(message);

        mBuilder.setContentIntent(contentIntent);
        mNotificationManager.notify(NOTIFICATION_ID, mBuilder.build());
    	}
    }

}
