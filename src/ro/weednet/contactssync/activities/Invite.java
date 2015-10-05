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

import ro.weednet.contactssync.R;

import com.facebook.CallbackManager;
import com.facebook.FacebookCallback;
import com.facebook.FacebookException;
import com.facebook.FacebookSdk;
import com.facebook.share.model.AppInviteContent;
import com.facebook.share.widget.AppInviteDialog;
import com.google.android.gms.analytics.GoogleAnalytics;
import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.Tracker;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnCancelListener;
import android.os.Bundle;
import android.os.Handler;

public class Invite extends Activity {
	public final Handler mHandler = new Handler();
	private CallbackManager mCallbackManager;
	protected ProgressDialog mLoading;
	private Tracker mTracker;
	
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		FacebookSdk.sdkInitialize(this);
		mCallbackManager = CallbackManager.Factory.create();
		
		mLoading = new ProgressDialog(this);
		mLoading.setTitle(getText(R.string.app_name));
		mLoading.setMessage("Loading ... ");
	//	mLoading.setCancelable(false);
		mLoading.setOnCancelListener(new OnCancelListener() {
			@Override
			public void onCancel(DialogInterface dialog) {
				mHandler.post(new Runnable() {
					public void run() {
						Invite.this.finish();
					}
				});
			}
		});
		
		mTracker = GoogleAnalytics.getInstance(this).newTracker("UA-3393187-13");
		mTracker.enableAdvertisingIdCollection(true);
		mTracker.setScreenName("invite");
		mTracker.send(new HitBuilders.ScreenViewBuilder().build());
		
		final String appLinkUrl = "https://fb.me/471741669617777";
		final String previewImageUrl = "https://contact-sync.weednet.ro/images/icon.png";
		
		if (AppInviteDialog.canShow()) {
			mLoading.show();
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
					if (mLoading != null) {
						try {
							mLoading.dismiss();
						} catch (Exception e) { }
					}
					finish();
				}
				
				@Override
				public void onCancel()
				{
					if (mLoading != null) {
						try {
							mLoading.dismiss();
						} catch (Exception e) { }
					}
					finish();
				}
				
				@Override
				public void onError(FacebookException e)
				{
					if (mLoading != null) {
						try {
							mLoading.dismiss();
						} catch (Exception ex) { }
					}
					finish();
				}
			});
			appInviteDialog.show(content);
		} else {
			finish();
			if (mLoading != null) {
				try {
					mLoading.dismiss();
				} catch (Exception ex) { }
			}
		}
	}
	
	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		
		mCallbackManager.onActivityResult(requestCode, resultCode, data);
	}
}
