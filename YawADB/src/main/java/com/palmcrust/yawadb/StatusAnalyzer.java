/* 
   StatusAnalyzer. Analyses current wireless ADB status. 
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

import java.io.Serializable;

import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;

class StatusAnalyzer implements Serializable {
	private static final long serialVersionUID = -872718362949627621L;
	private static final int AnalyzeTimeout = 5000;

	static final int DumbADBPort = -1;
	static final int DefaultADBPort = 5555;


	//public static final String NetworkNameDefault = "wifi";
	enum Status {UNDEFINED, UP, DOWN, NO_NETWORK, NO_ADBD}
	
	private final transient Context context;
	private transient AnalyzerThread anThread = null;
	
	private Status curStatus = Status.UNDEFINED;
	private String ipAddress;
	private int portNumber;
	
	
	StatusAnalyzer(Context context) {
		this.context = context;
	}

	StatusAnalyzer(Context context, StatusAnalyzer wAnalyzer) {
		this(context);
		curStatus = wAnalyzer.curStatus;
		ipAddress = wAnalyzer.ipAddress;
		portNumber = wAnalyzer.portNumber; 
	}

	
	boolean analyze() {
		
		synchronized(this) {
			if (anThread == null || !anThread.isAlive()) { 
				anThread = new AnalyzerThread();
				anThread.start();
			}
		}

		try {
			anThread.join(AnalyzeTimeout);
			return (curStatus != Status.UNDEFINED);
		} catch (InterruptedException ex) {
			return false;
		}
	}

	class AnalyzerThread extends Thread {
		public void run() {
			curStatus = Status.UNDEFINED;
			
			String portNumberStr = Utils.getProp("service.adb.tcp.port");
			portNumber = -1;
			if (!Utils.isEmpty(portNumberStr)) 
				try {
					portNumber = Integer.parseInt(portNumberStr);
				} catch(NumberFormatException ignored) {	}

			ipAddress = ipAddressFromWifiManager();

			if (ipAddress == null) 	
				curStatus = Status.NO_NETWORK; 
			else
			// Is adbd running?
			//if (Utils.getAdbdPid() < 0)\
			if (!Utils.isAdbdRunning())
				curStatus = Status.NO_ADBD;
			// Got IP address and adbd is running
			else
				curStatus =  (portNumber > 0) ? Status.UP : Status.DOWN;
		}
	}
	
	void interrupt() {
		synchronized(this) {
			if (anThread != null)
				anThread.interrupt();
		}
	}

	
	Status getStatus() {
		return curStatus; 
	}
	
	boolean isWirelessActive() {
		return (portNumber != DumbADBPort);
	}
	
	
	String evaluateADBConnectString() {
		if (curStatus != Status.UP) return null; 

		StringBuilder sb = new StringBuilder();
		sb.append("adb connect ");
		sb.append(ipAddress);
		if (portNumber != DefaultADBPort) {
			sb.append(':');
			sb.append(portNumber);
		}

		return sb.toString();
	}
		

	private String ipAddressFromWifiManager() {
		WifiManager wfm = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
		if (wfm == null) return null;
		WifiInfo wfi = wfm.getConnectionInfo();
		if (wfi == null) return null;
		int ipAddr = wfi.getIpAddress();
		if (ipAddr == 0) return null;
		return ipAddrToString(ipAddr);
	}


	private static String ipAddrToString(int ipAddress) {
		try {
			Class<?> formatterClass =  Class.forName("android.text.format.Formatter");
			return  (String) formatterClass.getMethod("formatIpAddress", int.class).invoke(null, ipAddress);
		} catch (Exception ex) {
			StringBuilder sb = new StringBuilder();
			int count = 4;
			int tmp = ipAddress;
			while (--count >= 0) {
				if (sb.length()>0) sb.append('.');
				sb.append(tmp & 0xff);
				tmp >>>= 8;
			}	
			return sb.toString();
		}
	}


	
}

