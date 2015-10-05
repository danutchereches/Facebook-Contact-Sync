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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.sql.Date;

import com.appbrain.AppBrain;
import com.applovin.adview.AppLovinAdView;
import com.applovin.adview.AppLovinInterstitialAd;
import com.applovin.adview.AppLovinInterstitialAdDialog;
import com.applovin.sdk.AppLovinAd;
import com.applovin.sdk.AppLovinAdDisplayListener;
import com.applovin.sdk.AppLovinAdLoadListener;
import com.applovin.sdk.AppLovinAdSize;
import com.applovin.sdk.AppLovinSdk;
import com.google.android.gms.analytics.GoogleAnalytics;
import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.Tracker;
import com.ironsource.mobilcore.CallbackResponse;
import com.ironsource.mobilcore.MobileCore;
import com.ironsource.mobilcore.CallbackResponse.TYPE;
import com.ironsource.mobilcore.MobileCore.LOG_TYPE;

import ro.weednet.ContactsSync;
import ro.weednet.ContactsSync.SyncType;
import ro.weednet.contactssync.R;
import ro.weednet.contactssync.authenticator.AuthenticatorActivity;
import ro.weednet.contactssync.client.RawContact;
import ro.weednet.contactssync.platform.ContactManager;
import ro.weednet.contactssync.preferences.GlobalFragment;
import android.accounts.Account;
import android.app.Activity;
import android.app.Dialog;
import android.app.FragmentTransaction;
import android.app.NotificationManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SyncStatusObserver;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.TextView;

public class Preferences extends Activity {
	public final static ContactsSync.SyncType DEFAULT_SYNC_TYPE = ContactsSync.SyncType.HARD;
	public final static int DEFAULT_SYNC_FREQUENCY = 24;//hours
	public final static int DEFAULT_PICTURE_SIZE = RawContact.IMAGE_SIZES.MAX_SQUARE;
	public final static boolean DEFAULT_SYNC_ALL = true;
	public final static boolean DEFAULT_SYNC_WIFI_ONLY = false;
	public final static boolean DEFAULT_JOIN_BY_ID = false;
	public final static boolean DEFAULT_SHOW_NOTIFICATIONS = false;
	public final static int DEFAULT_CONNECTION_TIMEOUT = 60;
	public final static boolean DEFAULT_DISABLE_ADS = false;
	
	private Account mAccount;
	private Dialog mDialog;
	private GlobalFragment mFragment;
	private SyncStatusObserver mSyncObserver = new SyncStatusObserver() {
		@Override
		public void onStatusChanged(final int which) {
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					updateStatusMessage(which);
				}
			});
		}
	};
	private Object mSyncObserverHandler = null;
	
	private AppLovinAd appLovinAd = null;
	
	private Tracker mTracker;
	
	@Override
	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		
		ContactsSync app = ContactsSync.getInstance();
		
		AppLovinSdk.initializeSdk(this);
		
		setContentView(R.layout.preferences);
		
		final AppLovinSdk applovinSDK = AppLovinSdk.getInstance(this);
		applovinSDK.getAdService().loadNextAd(AppLovinAdSize.BANNER, new AppLovinAdLoadListener() {
			@Override
			public void failedToReceiveAd(int arg0) {
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						View adView = getLayoutInflater().inflate(R.layout.appbrain, null);
						LinearLayout adContainer = (LinearLayout) findViewById(R.id.ad_container);
						adContainer.addView(adView);
					}
				});
			}
			
			@Override
			public void adReceived(final AppLovinAd ad) {
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						AppLovinAdView adView = new AppLovinAdView(applovinSDK, AppLovinAdSize.BANNER, Preferences.this);
						adView.renderAd(ad);
						
						LinearLayout adContainer = (LinearLayout) findViewById(R.id.ad_container);
						adContainer.addView(adView);
					}
				});
			}
		});
		applovinSDK.getAdService().loadNextAd(AppLovinAdSize.INTERSTITIAL, new AppLovinAdLoadListener() {
			@Override
			public void failedToReceiveAd(int arg0) {
				appLovinAd = null;
			}
			
			@Override
			public void adReceived(final AppLovinAd ad) {
				appLovinAd = ad;
			}
		});
		
		MobileCore.init(this, "3QBXU338FKE1M2ZSZEH3WRKIXJ0C5", LOG_TYPE.PRODUCTION, MobileCore.AD_UNITS.INTERSTITIAL); 
		
		FragmentTransaction ft = getFragmentManager().beginTransaction();
		mFragment = new GlobalFragment();
		ft.replace(R.id.settings, mFragment);
		ft.commit();
		
		mTracker = GoogleAnalytics.getInstance(this).newTracker("UA-3393187-13");
		mTracker.enableAdvertisingIdCollection(true);
		mTracker.setScreenName("index");
		mTracker.send(new HitBuilders.ScreenViewBuilder().build());
	}
	
	@Override
	public void onResume() {
		super.onResume();
		
		if (mDialog != null) {
			mDialog.dismiss();
		}
		
		final ContactsSync app = ContactsSync.getInstance();
		
		NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		mNotificationManager.cancelAll();
		
		//TODO: use current/selected account (not the first one)
		mAccount = ContactsSync.getInstance().getAccount();
		
		if (mAccount == null) {
			mDialog = new Dialog(this);
			mDialog.setContentView(R.layout.no_account_actions);
			mDialog.setTitle("Select option");
			((Button) mDialog.findViewById(R.id.add_account_button)).setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					mDialog.dismiss();
					Intent intent = new Intent(Preferences.this, AuthenticatorActivity.class);
					startActivity(intent);
				}
			});
			((Button) mDialog.findViewById(R.id.exit_button)).setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					mDialog.dismiss();
					Preferences.this.finish();
				}
			});
			mDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
				@Override
				public void onCancel(DialogInterface dialog) {
					mDialog.dismiss();
					Preferences.this.finish();
				}
			});
			mDialog.show();
		} else if (!app.getWizardShown()) {
			mDialog = new Dialog(this);
			mDialog.setContentView(R.layout.wizard);
			mDialog.setTitle("Select option");
			mDialog.findViewById(R.id.invite).setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					mDialog.findViewById(R.id.next).callOnClick();
					
					Intent intent = new Intent(Preferences.this, Invite.class);
					startActivity(intent);
				}
			});
			mDialog.findViewById(R.id.next).setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					if (mDialog.findViewById(R.id.page1).getVisibility() == View.VISIBLE) {
						mDialog.findViewById(R.id.page1).setVisibility(View.GONE);
						mDialog.findViewById(R.id.page2).setVisibility(View.VISIBLE);
						mDialog.findViewById(R.id.page3).setVisibility(View.GONE);
						((TextView) mDialog.findViewById(R.id.next)).setText(getString(R.string.close));
					} else if (mDialog.findViewById(R.id.page2).getVisibility() == View.VISIBLE) {
						mDialog.findViewById(R.id.page1).setVisibility(View.GONE);
						mDialog.findViewById(R.id.page2).setVisibility(View.GONE);
						mDialog.findViewById(R.id.page3).setVisibility(View.VISIBLE);
						((TextView) mDialog.findViewById(R.id.next)).setText(getString(R.string.close));
					} else if (mDialog.findViewById(R.id.page3).getVisibility() == View.VISIBLE) {
						
						if (((RadioButton) mDialog.findViewById(R.id.wizard_sync_type_soft)).isChecked()) {
							app.setSyncType(SyncType.SOFT);
						} else if (((RadioButton) mDialog.findViewById(R.id.wizard_sync_type_medium)).isChecked()) {
							app.setSyncType(SyncType.MEDIUM);
						} else if (((RadioButton) mDialog.findViewById(R.id.wizard_sync_type_hard)).isChecked()) {
							app.setSyncType(SyncType.HARD);
						}
						
						app.setSyncAllContacts(((RadioButton) mDialog.findViewById(R.id.wizard_sync_all_contacts_yes)).isChecked());
						app.setJoinById(((CheckBox) mDialog.findViewById(R.id.wizard_sync_join_by_id)).isChecked());
						app.setSyncFrequency(DEFAULT_SYNC_FREQUENCY);
						app.setWizardShown(true);
						app.savePreferences();
						
						ContentResolver.setSyncAutomatically(mAccount, ContactsContract.AUTHORITY, true);
						Bundle extras = new Bundle();
						ContentResolver.addPeriodicSync(mAccount, ContactsContract.AUTHORITY, extras, DEFAULT_SYNC_FREQUENCY * 3600);
						mDialog.dismiss();
						
						init();
						mFragment.updateViews();
					}
				}
			});
			mDialog.show();
		} else {
			init();
		}
	}
	
	
	private void init() {
		final ContactsSync app = ContactsSync.getInstance();
		mFragment.setAccount(mAccount);
		//TODO: check logic
		if (ContentResolver.getSyncAutomatically(mAccount, ContactsContract.AUTHORITY)) {
			if (app.getSyncFrequency() == 0) {
				app.setSyncFrequency(Preferences.DEFAULT_SYNC_FREQUENCY);
				app.savePreferences();
				ContentResolver.addPeriodicSync(mAccount, ContactsContract.AUTHORITY, new Bundle(), Preferences.DEFAULT_SYNC_FREQUENCY * 3600);
			}
		} else {
			if (app.getSyncFrequency() > 0) {
				app.setSyncFrequency(0);
				app.savePreferences();
			}
		}
		updateStatusMessage(0);
		final int mask = ContentResolver.SYNC_OBSERVER_TYPE_ACTIVE | ContentResolver.SYNC_OBSERVER_TYPE_PENDING;
		mSyncObserverHandler = ContentResolver.addStatusChangeListener(mask, mSyncObserver);
	}
	
	@Override
	public void onPause() {
		super.onPause();
		
		if (mSyncObserverHandler != null) {
			ContentResolver.removeStatusChangeListener(mSyncObserverHandler);
		}
	}
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
		
		if (mDialog != null) {
			mDialog.dismiss();
		}
	}
	
	protected final void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		
	}
	
	@Override
	public void onBackPressed() {
		ContactsSync app = ContactsSync.getInstance();
		
		app.setLastAdTimestamp(System.currentTimeMillis());
		app.savePreferences();
		
		if (MobileCore.isInterstitialReady()) {
			MobileCore.showInterstitial(this, new CallbackResponse() {
				@Override
				public void onConfirmation(TYPE arg0) {
					finish();
				}
			});
		} else if (appLovinAd != null) {
			AppLovinInterstitialAdDialog adDialog = AppLovinInterstitialAd.create(AppLovinSdk.getInstance(this), Preferences.this);
			adDialog.setAdDisplayListener(new AppLovinAdDisplayListener() {
				@Override
				public void adHidden(AppLovinAd ad) {
					finish();
				}
				
				@Override
				public void adDisplayed(AppLovinAd arg0) {
					
				}
			});
			adDialog.showAndRender(appLovinAd);
		} else {
			super.onBackPressed();
			
			AppBrain.getAds().showInterstitial(this);
		}
	}
	
	protected void updateStatusMessage(int code) {
		if (mAccount == null) {
			return;
		}
		
		TextView statusView = (TextView) findViewById(R.id.status_message);
		//TODO: add sync disabled message
		if (ContentResolver.isSyncPending(mAccount, ContactsContract.AUTHORITY)) {
			statusView.setText("Sync waiting for other processes");
		} else if (ContentResolver.isSyncActive(mAccount, ContactsContract.AUTHORITY)) {
			statusView.setText("Syncing contacts .. ");
		} else {
			int count = ContactManager.getLocalContactsCount(this, mAccount);
			long syncTimestamp = getLasySyncTime();
			if (syncTimestamp > 0) {
				Date syncDate = new Date(syncTimestamp);
				String date = DateFormat.getDateFormat(this).format(syncDate).toString();
				String time = DateFormat.getTimeFormat(this).format(syncDate).toString();
				statusView.setText("Synced at " + date + " " + time + ". " + count + " contact" + (count == 1 ? "" : "s") + " imported.");
			} else {
				statusView.setText("Synced. " + count + " contact" + (count == 1 ? "" : "s") + " imported.");
			}
		}
	}
	
	private long getLasySyncTime() {
		if (mAccount == null) {
			return -1;
		}
		
		try {
			Method getSyncStatus = ContentResolver.class.getMethod(
					"getSyncStatus", Account.class, String.class);
			Object status = getSyncStatus.invoke(null, mAccount, ContactsContract.AUTHORITY);
			Class<?> statusClass = Class.forName("android.content.SyncStatusInfo");
			boolean isStatusObject = statusClass.isInstance(status);
			if (isStatusObject) {
				Field successTime = statusClass.getField("lastSuccessTime");
				return successTime.getLong(status);
			}
		} catch (Exception e) { }
		
		return -1;
	}
}
