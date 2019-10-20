/* 
   This is the main activity for YawADB application. 
   Copyright (C) 2013 Michael Glickman (Australia) <palmcrust@gmail.com>

   This program is free software: you can redistribute it and/or modify
   it under the terms of the GNU General Public License as published by
   the Free Software Foundation, either version 3 of the License, or
   (at your option) any later version.

   This program is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
   GNU General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with this program.  If not, see <http://www.gnu.org/licenses/>
*/    


package com.palmcrust.yawadb;

import java.util.Timer;
import java.util.TimerTask;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.palmcrust.yawadb.StatusAnalyzer.Status;

public class PopupActivity extends Activity  {
	private static final int ConfigActivityRequestCode = 666;
	private static final int TurnoffTimeout = 60000;		// 1 minute

	private Timer timer;
	private TimerTask ttask;
	private boolean asWidget = false;

	private Thread adbThread;
	private BroadcastReceiver bcastReceiver;
	private StatusAnalyzer analyzer;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		StatusAnalyzer wAnalyzer = (StatusAnalyzer) getIntent().getSerializableExtra(YawAdbConstants.StatusAnalyzerExtra);
		if (wAnalyzer == null) {
			analyzer = new StatusAnalyzer(this);
			analyzer.analyze();
		} else {
			asWidget = true;
			analyzer = new StatusAnalyzer(this, wAnalyzer);
		}
		
		setContentView(R.layout.list);
		adbThread = null;
		ttask = null;
		
		timer = new Timer();
		
		refreshText(); 

		bcastReceiver= new PopupActivityBroadcastReceiver(this);
		IntentFilter filter = new IntentFilter();
		filter.addAction(YawAdbConstants.AdbModeChangedAction);
		filter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED); 
		filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
		registerReceiver(bcastReceiver, filter);
		

		findViewById(R.id.actConfig).setOnClickListener(clickListener);
		findViewById(R.id.actInfo).setOnClickListener(clickListener);
	}

//	protected void refreshStatus() {
//		Intent bcastedIntent = new Intent(YawAdbConstants.RefreshStatusAction);
//		sendBroadcast(bcastedIntent);
//	}
	
	private void refreshText() {
		analyzer.analyze();
			
		Resources rsrc = getResources();
		
		TextView tv = findViewById(R.id.status);
		boolean toggleModeEnabled = true; 
		int colorResId = R.color.itemDisabledBkgr;

		StatusAnalyzer.Status stat = analyzer.getStatus(); 
		
		switch(stat) {
			case UP:
				tv.setText(rsrc.getString(R.string.actStatUp,
						analyzer.evaluateADBConnectString()));
				colorResId = R.color.itemEnabledBkgr;
				break;
				
			case DOWN:
				tv.setText(R.string.actStatDown);
				break;
				
			case NO_ADBD:
				tv.setText(R.string.actNoAdbd);
				toggleModeEnabled = false;
				break;

			case NO_NETWORK:	
				tv.setText(R.string.actNoNetwork);
				toggleModeEnabled = analyzer.isWirelessActive();
				break;
				
			default:	
				tv.setText(R.string.actUndefined);
				toggleModeEnabled = false;
		}
		
		tv.setOnClickListener(clickListener);
		tv.setBackgroundColor(rsrc.getColor(colorResId));
		
		tv = findViewById(R.id.toggleMode);
		if (toggleModeEnabled) {
			tv.setVisibility(View.VISIBLE);
			boolean tag;
			if (stat == Status.DOWN) {
				tv.setText(R.string.actEnable);
				colorResId = R.color.itemEnabledBkgr;
				tag = true;
			} else { 
				tv.setText(R.string.actDisable);
				colorResId = R.color.itemDisabledBkgr;
				tag = false; 
			}

			tv.setTag(tag);
			tv.setOnClickListener(clickListener);
			tv.setBackgroundColor(rsrc.getColor(colorResId));
		} else
			tv.setVisibility(View.GONE);
	}

	

	@Override
	public void onWindowFocusChanged(boolean hasFocus) {
		super.onWindowFocusChanged(hasFocus);
	
		if (asWidget) {
			if (hasFocus) { 
				ttask = new PopupActivityTimerTask();
				timer.schedule(ttask, TurnoffTimeout);
			} else
			if (ttask != null) {
				ttask.cancel();
				ttask = null;
			}
		}
	}

	class PopupActivityTimerTask  extends TimerTask {
		public void run() {
			 terminate();
			 finish();
		}
	}
	
	

	private final View.OnClickListener clickListener = new View.OnClickListener() {

		@Override
		public void onClick(View view) {
			switch(view.getId()) {
				case R.id.status:
					Utils.showTooltip(
						PopupActivity.this, R.string.msgRefreshing, Toast.LENGTH_SHORT);
					refreshText();
					break;
					
				case R.id.toggleMode:
					changeAdbConnection((Boolean)view.getTag(), true);
					break;
					
				case R.id.actConfig:
					startConfigActivity();
					break;

				case R.id.actInfo:
					startInfoActivity();
			}
		}
		
	};

	private void startConfigActivity() {
		Intent intent = new Intent(getApplicationContext(), ConfigActivity.class);
		intent.putExtra(YawAdbConstants.AsWidgetExtra, asWidget);
		startActivityForResult(intent, ConfigActivityRequestCode);
	}
	
	private void startInfoActivity() {
		Intent intent = new Intent(getApplicationContext(), InfoActivity.class);
		startActivity(intent);
	}
	
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK && adbThread != null) {
			terminate();
			finish();
			return true;
		}
		return super.onKeyDown(keyCode, event);
	}

	
	@Override
	public void onDestroy() {
		terminate();
		super.onDestroy();
	}

	private void terminate() {
		if (ttask != null) {
			ttask.cancel();
			ttask = null;
		}

		if (timer != null) {
			timer.cancel();
			timer = null;
		}

		if (bcastReceiver != null) {
			unregisterReceiver(bcastReceiver);
			bcastReceiver = null;
		}
		
		if (adbThread != null) {
			adbThread.interrupt();
			adbThread = null;
		}

		if (analyzer != null) {
			analyzer.interrupt();
			analyzer = null;
		}
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (requestCode == ConfigActivityRequestCode) {
			sendBroadcast(new Intent(YawAdbConstants.OptionsChangedAction));
			if (data != null) {
				boolean newAutoUsb = data.getBooleanExtra(YawAdbConstants.NewAutoUsbExtra, false);
				boolean newPort= data.getBooleanExtra(YawAdbConstants.NewPortNumberExtra, false);
			
				if (newAutoUsb || newPort) {
					refreshText();
				
					switch(analyzer.getStatus()) {
						case NO_NETWORK:
							if (newAutoUsb && analyzer.isWirelessActive())
								changeAdbConnection(false, false);
							break;
						
						case UP:	
							if (newPort)
								changeAdbConnection(true, false);

						default:	
					}
				}
			}
				
		}

	}

	private synchronized void changeAdbConnection(boolean enable, boolean explicit) {
		if (adbThread == null || !adbThread.isAlive()) {
			adbThread = new AdbModeChanger(this, enable, explicit);
			adbThread.start();
		}
	}		 

	private static class PopupActivityBroadcastReceiver extends BroadcastReceiver {
		@SuppressWarnings("FieldCanBeLocal")
        private static final int AfterUpdateTimeoutUp=3000;
		private static final int AfterUpdateTimeoutDown=1000;
		
		final PopupActivity activity;
		public PopupActivityBroadcastReceiver(PopupActivity activity) {
			this.activity = activity;
		}
		
		@Override
		public void onReceive(Context context, Intent intent) {
			activity.refreshText();
			if (activity.asWidget && !activity.isFinishing() &&
				YawAdbConstants.AdbModeChangedAction.equals(intent.getAction()) &&
				intent.getBooleanExtra(YawAdbConstants.ExplicitExtra, false)) { 
				(new Handler()).postDelayed(new Runnable() {
					public void run() {activity.finish();}},
					(activity.analyzer.getStatus() == Status.UP) ? AfterUpdateTimeoutUp : AfterUpdateTimeoutDown);
			}	
		}
	}

}

