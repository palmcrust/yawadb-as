/* 
   YawAdbOption. Maintains list of ADB options.  
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

import java.io.EOFException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import android.content.Context;
import android.content.res.Resources;

@SuppressWarnings("ALL")
class YawAdbOptions {
	
	//-------------------------------------------------------------------------------
	abstract static class Option {
		private final String key;
		private final int boxId;
		private final int nameResId;
		private final int errMsgId;
		Object curValue;
		final Object defaultValue;
		

		private Option(String key, int boxId, int nameResId, int errMsgId, Object defaultValue) {
			this.key = key;
			this.boxId = boxId;
			this.nameResId = nameResId;
			this.errMsgId = errMsgId;
			curValue = this.defaultValue = defaultValue;
		}
		
		String getKey() {
			return key;
		}

		int getBoxId() {
			return boxId;
		}

		public int getNamResId() {
			return nameResId;
		}

		Object getValue() {
			return curValue;
		}

		Object getDefaultValue() {
			return defaultValue;
		}

		boolean setDefaultValue() {
			return setValue(defaultValue);
		}

		boolean setValue(Object newValue) {
			boolean result = validateValue(newValue);
			if (result) curValue = newValue;
			return result;
		}

		public String getStringValue(Resources rsrc) {
			return String.valueOf(curValue);
		}

		int getErrorMessageId() {
			return errMsgId;
		}

		@SuppressWarnings("static-method")
		boolean validateValue(Object value) {
			return true;
		}
		
	}

	//-------------------------------------------------------------------------------
	@SuppressWarnings("SameParameterValue")
	public static class AlternativesOption extends Option {
		final int[] choiceResIds;
		//int defaultChoice;
		
		AlternativesOption(String key, int boxId, int nameResId, int errMsgId,
					int defaultChoiceIndex, int[] choiceResIds) {
			super(key, boxId, nameResId, errMsgId, defaultChoiceIndex);
			this.choiceResIds = choiceResIds;
		}

		@Override
		public boolean validateValue(Object value) {
			int intValue = (Integer) value;
			return (intValue >= 0 && intValue < choiceResIds.length);
		}

		int getIndex() {
			return (Integer) curValue;
		}

		@Override
		public String getStringValue(Resources rsrc) {
			return rsrc.getString(choiceResIds[(Integer)curValue]);
		}

		void nextValue() {
			int curValueInt = (Integer) curValue;
			if (++curValueInt >= choiceResIds.length) curValueInt = 0;
			curValue = curValueInt;
		}

	}

	//-------------------------------------------------------------------------------
	@SuppressWarnings("SameParameterValue")
	public static class IntegerOption extends Option {
		final int minValue;
		final int maxValue;
		//protected int defaultValue;
		

		IntegerOption(String key, int boxId, int nameResId, int errMsgId,
				int defaultValue, int minValue, int maxValue) {
			super(key, boxId, nameResId, errMsgId, defaultValue);
			this.minValue = minValue;
			this.maxValue = maxValue;
		}

		int getIntValue() {
			return (Integer)curValue;
		}

		@Override
		public boolean validateValue(Object value) {
			int intValue = (Integer) value;
			return  (intValue >= minValue && intValue <= maxValue);
		}

		
	}

	//-------------------------------------------------------------------------------------
	public static class TextOption extends Option {
		TextOption(String key, int boxId,
				int nameResId, int errMsgId, String defaultValue) {
			super(key, boxId, nameResId, errMsgId, defaultValue);
		}

		@Override
		public boolean setValue(Object value) {
			String stringValue = (value==null) ? "" : String.valueOf(value);
			if (stringValue.length() <= 0)	stringValue = (String)defaultValue;
			return super.setValue(stringValue);
		}
		
		public String getString() {
			return (String) curValue;
		}

	}

	static class PathOption extends TextOption {
		PathOption(String key, int boxId,
				int nameResId, int errMsgId, String defaultValue) {
			super(key, boxId, nameResId, errMsgId, defaultValue);
		}
		
		@Override
		public boolean validateValue(Object value) {
			String stringValue = (String) value;
			// We must allow default value to avoid a dead lock.
			return  stringValue.equals(defaultValue) ||
					Utils.validateExecPath(stringValue);
		}
	}

	//=======================================================================================
	
	private static final int[] autoRefrStringIds =
		{R.string.refrNever, R.string.refr3sec, R.string.refr20sec,
		 R.string.refr1min, R.string.refr10min, R.string.refr30min};    

	private static final int[] autoOffStringIds =
		{R.string.disabled, R.string.enabled};

	private static final int[] refrIntervals = 
		{0, 3000, 20000, 60000, 600000, 1800000};	

	
	private static final int[] adbRestartStringIds =
		{R.string.adbdRestartNormal, R.string.adbdRestartForced}; 

	final IntegerOption portNumber =
			new IntegerOption("PN", R.id.optPortNumber, 
					R.string.optPortNumber, R.string.msgPortNumberError, StatusAnalyzer.DefaultADBPort, 1024, 65535); 

	final AlternativesOption autoRefresh =
			new AlternativesOption("AR", R.id.optAutoRefresh, R.string.optAutoRefresh, 0, 0, autoRefrStringIds);

	final AlternativesOption autoUsb =
			new AlternativesOption("AD", R.id.optAutoUsb, R.string.optAutoUsb, 0, 0, autoOffStringIds);
	
	final PathOption shellPath =
			new PathOption("SP", R.id.optShellPath, R.string.optShellPath, R.string.msgInvalidPath, "su");

	final AlternativesOption adbdRestartMethod =
			new AlternativesOption("ARM", R.id.optAdbRestart,  R.string.optAdbdRestart, 0, 0, adbRestartStringIds);   
	
	final Option[] allOptions = {portNumber, autoRefresh, autoUsb, shellPath,  adbdRestartMethod};
	
	//-----------------------------------------------------------------------------------------------------
	
	private static final String SavedOptionsFileName = "options.dat";
	private static final byte[] signature = {'Y', 'A'}; 
	private static final short version = 0; 
//	private static final String SharedPrefsName = "YawAdbOptions"; 
	private final Context context;
	
	YawAdbOptions(Context context) {
		this.context = context;
		loadPreferences();
	}
	
	
	
//	private void loadPreferences() {
//		SharedPreferences sprefs = context.getSharedPreferences(SharedPrefsName, Context.MODE_PRIVATE);
//		for (Option opt : allOptions) {
//			String key = opt.getKey();
//			Object dfltValue = opt.getDefaultValue();
//			opt.setValue((opt instanceof TextOption)
//					? sprefs.getString(key, (String)dfltValue) 
//					: sprefs.getInt(key, (Integer)dfltValue)); 
//		}
//	}

	private void loadPreferences() {
		ObjectInputStream ois = null;
		Map<String, Object> allValues = new HashMap<String, Object>(); 

		try {
			ois = new ObjectInputStream(
					new GZIPInputStream(
						context.openFileInput(SavedOptionsFileName)));
			byte[] sgn = new byte[2];
			if (ois.read(sgn) < 2) throw new EOFException();
			if (!Arrays.equals(sgn, signature))
				throw new IllegalStateException ("Wrong signature");
			//sgn = null;
			
			if (ois.readUnsignedShort() > version)
				throw new IllegalStateException ("Incompatible version");

			//noinspection InfiniteLoopStatement
			for (;;)
				allValues.put(ois.readUTF(), ois.readObject());
			
		} catch (FileNotFoundException ignored) {
		} catch (EOFException ignored) {
		} catch (Exception ex) {
			ex.printStackTrace();
		}
			
		if (ois != null)
			try {ois.close();} catch (IOException ignored) {}
		
		for (Option opt : allOptions) {
			String key = opt.getKey();
			if (allValues.containsKey(key))
				opt.setValue(allValues.get(key));
			else
				opt.setDefaultValue();
		}
	}
		
//	public void savePreferences() {
//		SharedPreferences.Editor editor =
//				context.getSharedPreferences(SharedPrefsName, Context.MODE_PRIVATE).edit();
//		for (Option opt : allOptions) {
//			String key = opt.getKey();
//			Object value = opt.getValue();
//			if (opt instanceof TextOption)
//				editor.putString(key, (String)value);
//			else
//				editor.putInt(key, (Integer)value);
//		}
//		editor.commit();
//	}

	void savePreferences() {
		ObjectOutputStream oos = null;

		try {
			oos = new ObjectOutputStream(
					new GZIPOutputStream(
						context.openFileOutput(SavedOptionsFileName, Context.MODE_PRIVATE)));
			oos.write(signature);
			oos.writeShort(version);

			for (Option opt : allOptions) { 
				oos.writeUTF(opt.getKey());
				oos.writeObject(opt.getValue());
			}
			
		} catch (Exception ex) {
			ex.printStackTrace();
		}
			
		if (oos != null)
			try {oos.close();} catch (IOException ignored) {}
	}

	
	int getRefreshInterval() {
		return refrIntervals[autoRefresh.getIndex()];
	}

	boolean getAutoUsbValue() {
		return (autoUsb.getIndex() != 0);
	}
	
}
