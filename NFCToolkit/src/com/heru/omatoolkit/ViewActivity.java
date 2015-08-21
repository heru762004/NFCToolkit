package com.heru.omatoolkit;

import java.io.InputStream;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

public class ViewActivity extends Activity {
	
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Uri data = getIntent().getData();
		String scheme = data.getScheme();
		String path = "";
		if(data!=null) {
			path = data.getPath();
		}
		Intent myIntent = new Intent(this, MainActivity.class);
		myIntent.putExtra("FilePath", path);
		myIntent.putExtra("URLScheme", scheme);
		startActivity(myIntent);
		this.finish();
	}
}
