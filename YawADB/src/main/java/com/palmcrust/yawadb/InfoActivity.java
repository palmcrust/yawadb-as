/* 
   InfoActivity. Maintains YawADB information page 
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

import android.app.Activity;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.WebView;
import android.widget.RadioGroup;
import android.widget.TextView;

public class InfoActivity extends Activity {
	private static final String assetUrlPrefix="file:///android_asset/html/";
	private Resources rsrc;
	private WebView wView;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		rsrc = getResources();
		
		setTitle(rsrc.getString(R.string.infoTitle, rsrc.getString(R.string.app_name)));
		
		
		setContentView(R.layout.info);

		{
			TextView versionView = findViewById(R.id.infoVersion);
			try {
				String version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
				versionView.setText(rsrc.getString(R.string.infoVersion, version));
			} catch (NameNotFoundException ex) {
				versionView.setVisibility(View.GONE);
			}
		}
		
		wView = findViewById(R.id.content);
		wView.setOnFocusChangeListener(new View.OnFocusChangeListener() {
			@Override
			public void onFocusChange(View v, boolean hasFocus) {
				((View)v.getParent()).setBackgroundColor(
					Utils.getColour(rsrc, hasFocus ? R.color.strokeCrnt : R.color.strokeOther));
			}
		});

		RadioGroup rg = findViewById(R.id.btnGroup);
		rg.setOnCheckedChangeListener(groupCheckListener);
		rg.check(rg.getChildAt(0).getId());
	}
	
	
	
	@Override
	public boolean dispatchKeyEvent(KeyEvent event) {
		if (event.getAction() == KeyEvent.ACTION_DOWN) {
			int keyCode = event.getKeyCode();
			if (keyCode == KeyEvent.KEYCODE_TAB || keyCode == KeyEvent.KEYCODE_STAR) {
				processTabKey();
				return true;
			}
		}

		return super.dispatchKeyEvent(event);
	}

	private void processTabKey() {
		View focused = getCurrentFocus();
		//noinspection ConstantConditions
		if (focused.getId() == R.id.content)
			findViewById(R.id.infoHowTo).requestFocus();
		else
			findViewById(R.id.content).requestFocus();
	}

	private final RadioGroup.OnCheckedChangeListener groupCheckListener =
			new RadioGroup.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(RadioGroup group, int checkedId) {
				int fileNameId = 0;
				switch(checkedId) {
					case R.id.infoHowTo:
						fileNameId = R.string.infoPathHowTo;
						break;

					case R.id.infoLicense:
						fileNameId = R.string.infoPathLicense;
						break;
				}
				if (fileNameId != 0) {
					String fileName = rsrc.getString(fileNameId);
					wView.loadUrl(assetUrlPrefix+fileName);
				}
			}
		};

}
