/* 
   AdbModeChanger. Changing ADB wireless connection mode  
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

import android.content.Context;
import android.content.Intent;
import android.os.Message;
import android.widget.Toast;

class AdbModeChanger extends Thread {
	

	static class ThreadHandler extends android.os.Handler {
//		public static final int WHAT_REFRESH_TEXT = 1;
		static final int WHAT_SHOW_TOOLTIP = 2;
		
		private final Context context;
		ThreadHandler(Context context) {
			super();
			this.context = context;
		}
		@Override
		public void handleMessage(Message msg) {
			if (msg.what == WHAT_SHOW_TOOLTIP)
				Utils.showTooltip(context, msg.arg1, msg.arg2);
		}
	}

	private final Context context;
	private final boolean explicit;
	private String shellPath;
	private boolean forceKill;
	private final AdbModeChanger.ThreadHandler handler;
	private int port;

	// explicit = true, if the thread is called as a result of enable/disable command,
	AdbModeChanger (Context context, boolean enable, boolean explicit) {
		super();
		this.context = context;
		this.explicit = explicit;
		handler = new ThreadHandler(context);
		processOptions(enable);
	}

	private void processOptions(boolean enable) {
		YawAdbOptions options = new YawAdbOptions(context);
		port = enable ? options.portNumber.getIntValue() : StatusAnalyzer.DumbADBPort;
		forceKill = (options.adbdRestartMethod.getIndex() != 0);
		shellPath = options.shellPath.getString();
	}
	
	
	public void run() {
		String[] cmd = new String[3];
		cmd[0] = "setprop service.adb.tcp.port " + port;
//		int pid = 0;
//		if (forceKill) pid = Utils.getAdbdPid();
//		cmd[1] = (pid <= 0) ? "stop adbd" : ("kill -9 " + pid);
		cmd[1] = "stop adbd";
		cmd[2] = "start adbd";
		
		try {
			if (!Utils.runBatchSequence(shellPath, cmd))
				Message.obtain(handler, AdbModeChanger.ThreadHandler.WHAT_SHOW_TOOLTIP, 
						R.string.msgCouldntExecute, Toast.LENGTH_LONG).sendToTarget();
			
			// Refresh anyway (just to be up to date)	

			// Wait no more than 5sec for the daemon to come up
			if (port != StatusAnalyzer.DumbADBPort) {
				int countDown = 25;
				while(--countDown>=0 && !Utils.isAdbdRunning())    //     Utils.getAdbdPid()<0)
					Thread.sleep(200);
			}
			
			Intent intent = new Intent(YawAdbConstants.AdbModeChangedAction);
			intent.putExtra(YawAdbConstants.ExplicitExtra, explicit);
			context.sendBroadcast(intent);
			
		} catch(InterruptedException ignored) {}
		
	}
}