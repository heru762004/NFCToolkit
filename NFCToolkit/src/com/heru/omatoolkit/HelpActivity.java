package com.heru.omatoolkit;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebView;

public class HelpActivity extends Activity {
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.help_activity);
		
		WebView wb = (WebView) findViewById(R.id.helpWebView);
//		wb.loadDataWithBaseURL("file:///android_asset/", "help", "html", "UTF-8", null);
		wb.loadUrl("file:///android_asset/help.html");
	}
}
