/* 
   YawAdbService. Service used by YawADB widget (API 3+)   
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

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;
import android.widget.RemoteViews;

class ForegroundServiceStarter {

	private Service service;

	ForegroundServiceStarter(Service service) {
		this.service = service;
	}

	@TargetApi(26)
	void start() {
		String channelId = BuildConfig.APPLICATION_ID;
		String channelName = service.getString(R.string.app_name);
		NotificationChannel chan = new NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_NONE);

		chan.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
		chan.setImportance(NotificationManager.IMPORTANCE_NONE);
		NotificationManager manager = (NotificationManager) service.getSystemService(Context.NOTIFICATION_SERVICE);
		if (manager != null) manager.createNotificationChannel(chan);

		Notification.Builder notificationBuilder = new Notification.Builder(service, channelId);
		Notification notification = notificationBuilder.setOngoing(true)
				.setSmallIcon(R.drawable.ic_launcher)
				.setContentTitle(channelName)
				.setCategory(Notification.CATEGORY_SERVICE)
				.build();

		service.startForeground((int) ((SystemClock.uptimeMillis() & Integer.MAX_VALUE) | 1), notification);
	}
}


@TargetApi(Build.VERSION_CODES.CUPCAKE)
public class YawAdbService extends Service {
	private AutoRefreshThread refrThread;
	private RemoteViews views;
	private ComponentName providerCompName; 
	private AppWidgetManager appWidgetManager;
	private WidgetServiceBroadcastReceiver bcastReceiver;
	private int oldImageResId;
	private boolean autoUsb;
	private int refrInterval;
	private PendingIntent onClickIntent;
	private AdbModeChanger modeChanger;
	private StatusAnalyzer analyzer;

	
	private static final String LogTag = "YawADB";
	private static final String MsgNullIntent=" Null intent at \'%s\'! Ignoring the call";


	@Override
	public void onCreate() {
		super.onCreate();
		if (Utils.APIVersion >= 26) {
			new ForegroundServiceStarter(this).start();
		}
	}


	@Override
	public void onStart(Intent intent, int startId) {
		if (intent==null)
			Log.i(LogTag, String.format(MsgNullIntent, "onStart"));
		else
			handleStartCommand(intent);
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		// Can you imagine calling onStartCommand with null intent?
		// This is one of numerous unthinkable tricks, our beloved
		// Nova II Advanced (ICS 4.0.3) is capable of, which makes
		// it so special and so great ... for testing. To you, my
		// darling, I dedicate this silly patch, to the others:
		// "Excusez-moi pour ce marasme".
		if (intent==null)
			Log.i(LogTag, String.format(MsgNullIntent, "onStartCommand"));
		else
			handleStartCommand(intent);
	    return START_STICKY;
	}
	
	private void handleStartCommand(Intent intent) {
		analyzer = new StatusAnalyzer(this);
		appWidgetManager = AppWidgetManager.getInstance(this);
		views = new RemoteViews(getPackageName(), R.layout.widget);
		providerCompName = intent.getParcelableExtra(YawAdbConstants.ComponentNameExtra);
		onClickIntent = intent.getParcelableExtra(YawAdbConstants.OnClickIntentExtra);
		oldImageResId = 0;
		modeChanger = null;
		refrThread = null;

		processOptions(true);
		
		bcastReceiver= new WidgetServiceBroadcastReceiver(this);
		IntentFilter filter = new IntentFilter();
		filter.addAction(YawAdbConstants.ProviderRefreshAction);
		filter.addAction(YawAdbConstants.RefreshStatusAction);
		filter.addAction(YawAdbConstants.OptionsChangedAction);
		filter.addAction(YawAdbConstants.AdbModeChangedAction);
		filter.addAction(YawAdbConstants.PopupAction);
		filter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED); 
		filter.addAction(Intent.ACTION_SCREEN_ON); 
		filter.addAction(Intent.ACTION_SCREEN_OFF); 
		filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
		getApplicationContext().registerReceiver(bcastReceiver, filter);

		setIntentOnClickListener();

	}
			
	private void processOptions(boolean forceRefresh) {
		// We ALWAYS restart the thread in order to reset ticks!
		terminateAutoRefresh();
		
		YawAdbOptions options = new YawAdbOptions(this);
		autoUsb = options.getAutoUsbValue();
		updateStatus(forceRefresh); // To disable WADB is necessary
		
		refrInterval = options.getRefreshInterval();
		startAutoRefreshIfRequested();
	}
	

	@Override
	public void onDestroy() {
		
		if (bcastReceiver != null) {
			getApplicationContext().unregisterReceiver(bcastReceiver);
			bcastReceiver = null;
		}

		if (modeChanger != null) {
			modeChanger.interrupt();
			modeChanger = null;
		}
		
		if (refrThread != null) {
			refrThread.terminate();
			refrThread = null;
		}
		super.onDestroy();
	}

	@Override
	public IBinder onBind(Intent arg0) {
		return null;
	}

	private void startAutoRefreshIfRequested() {
		if (refrInterval > 0 && (refrThread==null || !refrThread.isAlive())) {
			refrThread = new AutoRefreshThread(this, refrInterval);
			refrThread.start();
		}
	}

	private void terminateAutoRefresh() {
		if (refrThread != null) {
			refrThread.terminate();
			// Safer not to use Thread.join here 
			refrThread =null;
		}
	}
	
	@SuppressWarnings("UnusedReturnValue")
	private boolean updateStatus(boolean force) {
		boolean success = analyzer.analyze();
		if (success) refreshStatus(force);
		return success;
	}
	
	
	private void refreshStatus(boolean force) {
		StatusAnalyzer.Status stat = analyzer.getStatus();

		int newImageResId = (stat == StatusAnalyzer.Status.UP) ? 
				R.drawable.wireless_up : R.drawable.wireless_down;

		if (force || (newImageResId != oldImageResId)) {
			views.setImageViewResource(R.id.modeImg, newImageResId);
			appWidgetManager.updateAppWidget(providerCompName, views);
			oldImageResId = newImageResId; 
		}

		if (autoUsb && (stat == StatusAnalyzer.Status.NO_NETWORK) &&
				analyzer.isWirelessActive())  
			(new Handler()).postDelayed(new Runnable() {
					public void run() {
						startAdbModeChanger();
					}}, 200);
		
	}

	private void startAdbModeChanger() {
		if (modeChanger == null || !modeChanger.isAlive()) {
			modeChanger = new AdbModeChanger(YawAdbService.this, false, false);
			modeChanger.start();
		}
	}
	
	private synchronized void setIntentOnClickListener() {
		views.setOnClickPendingIntent(R.id.modeImg, onClickIntent);
		appWidgetManager.updateAppWidget(providerCompName, views);
	}
	
	
	private void startPopupActivity() {
		Intent intent = new Intent(this, PopupActivity.class);
		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//		intent.putExtra(YawAdbConstants.AsWidgetExtra, true);
		intent.putExtra(YawAdbConstants.StatusAnalyzerExtra, analyzer);
		startActivity(intent);
	}

	
	//=========================================================================
	private static class WidgetServiceBroadcastReceiver extends BroadcastReceiver {
		private final YawAdbService service;
		public WidgetServiceBroadcastReceiver(YawAdbService service) {
			this.service = service;
		}
		
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();

			if (YawAdbConstants.OptionsChangedAction.equals(action))
				service.processOptions(false);
			else
			if (Intent.ACTION_SCREEN_OFF.equals(action))
				service.terminateAutoRefresh();
			else {
				//  YawAdbConstants.PopupAction
				//  YawAdbConstants.RefreshStatusAction
				//  YawAdbConstants.ProviderRefreshAction
				//	Intent.ACTION_AIRPLANE_MODE_CHANGED 
				//	Intent.ACTION_SCREEN_ON 
				//  ConnectivityManager.CONNECTIVITY_ACTION  
				service.updateStatus(false);

				if (YawAdbConstants.PopupAction.equals(action))
					service.startPopupActivity();
				else 
				if (!YawAdbConstants.RefreshStatusAction.equals(action)) {
					//  YawAdbConstants.ProviderRefreshAction
					//	Intent.ACTION_AIRPLANE_MODE_CHANGED 
					//	Intent.ACTION_SCREEN_ON 
					//  ConnectivityManager.CONNECTIVITY_ACTION  
					service.setIntentOnClickListener();
					service.startAutoRefreshIfRequested();
				}
			}
		}
	}

	//=========================================================================
	private static class WidgetServiceMessageHandler extends android.os.Handler {
		static final int WHAT_SET_APPEARANCE = 1;
		private final YawAdbService service;

		WidgetServiceMessageHandler(YawAdbService service) {
			super();
			this.service = service;
		}

		@Override
		public void handleMessage(Message msg) {
			if (msg.what == WHAT_SET_APPEARANCE) 
				service.refreshStatus(msg.arg1 != 0);
		}
			
	}
	//=========================================================================
	private static class AutoRefreshThread extends Thread {
//		public static enum InterruptReason {UNDEFINED, UPDATE_STATUS, TERMINATE}
//		public InterruptReason reason = InterruptReason.UNDEFINED;
		private final WidgetServiceMessageHandler handler;
		private boolean force=false; 
		private final int sleepTimeout;
		private final StatusAnalyzer analyzer;
		
		AutoRefreshThread(YawAdbService service, int sleepTimeout) {
			super();
			handler = new WidgetServiceMessageHandler(service);
			this.sleepTimeout = sleepTimeout;
			this.analyzer = service.analyzer;
		}

//		public synchronized void updateStatus(boolean force) {
//			this.force = force;
//			reason = InterruptReason.UPDATE_STATUS;
//			interrupt();	
//		}

		void terminate() {
//			reason = InterruptReason.TERMINATE;
			interrupt();	
		}
		
		public void run() {
			try {
				while(analyzer.analyze()) {
					Message msg = Message.obtain(
						handler, WidgetServiceMessageHandler.WHAT_SET_APPEARANCE, force ? 1: 0, 0);
					msg.sendToTarget();
					force = false;
					Thread.sleep(sleepTimeout);
				}
			} catch (InterruptedException ex) {
//					if (reason != InterruptReason.UPDATE_STATUS)
//						break;
			}
		}
	}

}
