/* 
   YawAdbProvider. AppWidgetProvide for YawADB (API 3+)   
   Copyright (C) 2013 Michael Glickman  (Australia) <palmcrust@gmail.com>

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
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.widget.Toast;

@TargetApi(Build.VERSION_CODES.CUPCAKE)
public class YawAdbProvider extends AppWidgetProvider {
	private static Intent serviceIntent = null;
	
//	@Override
//	public void onReceive(Context context, Intent intent) {
//		// Patching android bug:
//		// http://groups.google.com/group/android-developers/browse_thread/thread/365d1ed3aac30916?pli=1
//		if (AppWidgetManager.ACTION_APPWIDGET_DELETED.equals(intent.getAction())) {
//			if (Utils.getAPIVersion() <= 3) { // In fact it will be JUST 3
//		      int appWidgetId = intent.getIntExtra(
//		    		  android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID,
//		    		  android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID);
//		      if (appWidgetId != android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID)
//		        this.onDeleted(context, new int[] { appWidgetId });
//			}
//		}
//		else
//			super.onReceive(context, intent);
//	}

	
	
	@Override
	public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
		super.onUpdate(context, appWidgetManager, appWidgetIds);
		
		if (serviceIntent == null) {
			serviceIntent = new Intent();
			serviceIntent.setClassName(context, YawAdbService.class.getName());
			ComponentName myComponentName =  new ComponentName(context, getClass());
			serviceIntent.putExtra(YawAdbConstants.ComponentNameExtra, myComponentName);
			PendingIntent onClickIntent = 
					PendingIntent.getBroadcast(context, 0, new Intent(YawAdbConstants.PopupAction), 0);
			serviceIntent.putExtra(YawAdbConstants.OnClickIntentExtra, onClickIntent);

			if (Utils.startService(context.getApplicationContext(), serviceIntent) == null)
				Utils.showTooltip(context, R.string.msgSrvcStrtFail, Toast.LENGTH_LONG);
		}
		else
			context.sendBroadcast(new Intent(YawAdbConstants.ProviderRefreshAction));
	}


	@Override
	public void onDisabled(Context context) {
		if (serviceIntent != null) {
			context.stopService(serviceIntent);
			serviceIntent = null;
		}
		super.onDisabled(context);
	}



}