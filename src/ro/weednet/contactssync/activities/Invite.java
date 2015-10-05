/*
 * Copyright (C) 2012 Danut Chereches
 *
 * Contact: Danut Chereches <admin@weednet.ro>
 *
 * This file is part of Facebook Contact Sync.
 * 
 * Facebook Contact Sync is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with Facebook Contact Sync.
 * If not, see <http://www.gnu.org/licenses/>.
 *
 */
package ro.weednet.contactssync.activities;

import com.facebook.CallbackManager;
import com.facebook.FacebookCallback;
import com.facebook.FacebookException;
import com.facebook.FacebookSdk;
import com.facebook.share.model.AppInviteContent;
import com.facebook.share.widget.AppInviteDialog;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

public class Invite extends Activity {
	private CallbackManager mCallbackManager;
	
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		FacebookSdk.sdkInitialize(this);
		mCallbackManager = CallbackManager.Factory.create();
		
		final String appLinkUrl = "https://fb.me/471741669617777";
		final String previewImageUrl = "https://contact-sync.weednet.ro/images/icon.png";
		
		if (AppInviteDialog.canShow()) {
			AppInviteContent content = new AppInviteContent.Builder()
				.setApplinkUrl(appLinkUrl)
				.setPreviewImageUrl(previewImageUrl)
				.build();
			AppInviteDialog appInviteDialog = new AppInviteDialog(this);
			appInviteDialog.registerCallback(mCallbackManager, new FacebookCallback<AppInviteDialog.Result>()
			{
				@Override
				public void onSuccess(AppInviteDialog.Result result)
				{
					finish();
				}
				
				@Override
				public void onCancel()
				{
					finish();
				}
				
				@Override
				public void onError(FacebookException e)
				{
					finish();
				}
			});
			appInviteDialog.show(content);
		} else {
			finish();
		}
	}
	
	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		
		mCallbackManager.onActivityResult(requestCode, resultCode, data);
	}
}
