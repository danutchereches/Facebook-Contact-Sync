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
package ro.weednet;

import com.appbrain.AppBrain;

import ro.weednet.contactssync.Constants;
import ro.weednet.contactssync.activities.Preferences;
import ro.weednet.contactssync.client.RawContact;
import ro.weednet.contactssync.platform.ContactManager;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

public class ContactsSync extends Application {
	public static enum SyncType {
		SOFT, MEDIUM, HARD, LEGACY
	}
	public final static String NAMESPACE = "ro.weednet.contactssync_preferences";
	
	private SyncType mSyncType;
	private int mSyncFreq;
	private int mPicSize;
	private boolean mSyncAll;
	private boolean mFullSync;
	private boolean mSyncWifiOnly;
	private boolean mJoinById;
	private boolean mSyncBirthdays;
	private int mBirthdayFormat;
	private boolean mSyncStatuses;
	private boolean mSyncEmails;
	private boolean mShowNotifications;
	private int mConnTimeout;
	private boolean mDisableAds;
	private int mMaxPhotoSize;
	private static ContactsSync _instance = null;
	
	public static ContactsSync getInstance() {
		if(_instance != null) {
			return _instance;
		} else {
			return new ContactsSync();
		}
	}
	
	private SharedPreferences getSharedPreferences() {
		return getSharedPreferences(NAMESPACE, MODE_PRIVATE);
	}
	
	public Context getContext() {
		return super.getApplicationContext();
	}
	
	@Override
	public void onCreate() {
		super.onCreate();
		_instance = this;
		
		reloadPreferences();
		mMaxPhotoSize = ContactManager.getPhotoPickSize(this);
		
		AppBrain.initApp(this);
	}
	
	public SyncType getSyncType() {
		return mSyncType;
	}
	public int getSyncFrequency() {
		return mSyncFreq;
	}
	public int getPictureSize() {
		return mPicSize;
	}
	public boolean getSyncAllContacts() {
		return mSyncAll;
	}
	public boolean getSyncWifiOnly() {
		return mSyncWifiOnly;
	}
	public boolean getJoinById() {
		return mJoinById;
	}
	public boolean getSyncBirthdays() {
		return mSyncBirthdays;
	}
	public int getBirthdayFormat() {
		return mBirthdayFormat;
	}
	public boolean getSyncStatuses() {
		return mSyncStatuses;
	}
	public boolean getSyncEmails() {
		return mSyncEmails;
	}
	public boolean getFullSync() {
		return mFullSync;
	}
	public boolean getShowNotifications() {
		return mShowNotifications;
	}
	public int getConnectionTimeout() {
		return mConnTimeout;
	}
	public boolean getDisableAds() {
		return mDisableAds;
	}
	public int getMaxPhotoSize() {
		return mMaxPhotoSize;
	}
	
	public void setSyncType(int value) {
		try {
			mSyncType = SyncType.values()[value];
		} catch (Exception e) { }
	}
	public void setSyncFrequency(int value) {
		if (value >= 0 || value <= 720) {
			mSyncFreq = value;
		}
	}
	public void setPictureSize(int value) {
		if (value == RawContact.IMAGE_SIZES.SMALL_SQUARE
		 || value == RawContact.IMAGE_SIZES.SMALL
		 || value == RawContact.IMAGE_SIZES.NORMAL
		 || value == RawContact.IMAGE_SIZES.BIG
		 || value == RawContact.IMAGE_SIZES.SQUARE
		 || value == RawContact.IMAGE_SIZES.BIG_SQUARE
		 || value == RawContact.IMAGE_SIZES.HUGE_SQUARE
		 || value == RawContact.IMAGE_SIZES.MAX
		 || value == RawContact.IMAGE_SIZES.MAX_SQUARE) {
			mPicSize = value;
		}
	}
	public void setSyncAllContacts(boolean value) {
		mSyncAll = value;
	}
	public void setSyncWifiOnly(boolean value) {
		mSyncWifiOnly = value;
	}
	public void setJoinById(boolean value) {
		mJoinById = value;
	}
	public void setSyncBirthdays(boolean value) {
		mSyncBirthdays = value;
	}
	public void setBirthdayFormat(int value) {
		if (value >= 0 || value <= 4) {
			mBirthdayFormat = value;
		}
	}
	public void setSyncStatuses(boolean value) {
		mSyncStatuses = value;
	}
	public void setSyncEmails(boolean value) {
		mSyncEmails = value;
	}
	public void setShowNotifications(boolean value) {
		mShowNotifications = value;
	}
	public void setConnectionTimeout(int value) {
		mConnTimeout = Math.min(Math.max(value, 0), 3600);
	}
	public void requestFullSync() {
		mFullSync = true;
		SharedPreferences.Editor editor = getSharedPreferences().edit();
		editor.putBoolean("full_sync", mFullSync);
		editor.commit();
	}
	public void clearFullSync() {
		mFullSync = false;
		SharedPreferences.Editor editor = getSharedPreferences().edit();
		editor.putBoolean("full_sync", mFullSync);
		editor.commit();
	}
	public void setDisableAds(boolean value) {
		mDisableAds = value;
	}
	
	public void reloadPreferences() {
		SharedPreferences settings = getSharedPreferences();
		
		try {
			int type = Integer.parseInt(settings.getString("sync_type", Integer.toString(Preferences.DEFAULT_SYNC_TYPE.ordinal())));
			mSyncType = SyncType.values()[type];
		} catch (Exception e) {
			mSyncType = Preferences.DEFAULT_SYNC_TYPE;
		}
		try {
			mSyncFreq = Integer.parseInt(settings.getString("sync_freq", Integer.toString(Preferences.DEFAULT_SYNC_FREQUENCY)));//hours
		} catch (NumberFormatException e) {
			mSyncFreq = Preferences.DEFAULT_SYNC_FREQUENCY;
		}
		try {
			mPicSize = Integer.parseInt(settings.getString("pic_size", Integer.toString(Preferences.DEFAULT_PICTURE_SIZE)));
		} catch (NumberFormatException e) {
			mPicSize = Preferences.DEFAULT_PICTURE_SIZE;
		}
		mSyncAll = settings.getBoolean("sync_all", Preferences.DEFAULT_SYNC_ALL);
		mSyncWifiOnly = settings.getBoolean("sync_wifi_only", Preferences.DEFAULT_SYNC_WIFI_ONLY);
		mJoinById = settings.getBoolean("sync_join_by_id", Preferences.DEFAULT_JOIN_BY_ID);
		mSyncBirthdays = settings.getBoolean("sync_birthdays", Preferences.DEFAULT_SYNC_BIRTHDAYS);
		try {
			mBirthdayFormat = Integer.parseInt(settings.getString("birthday_format", Integer.toString(Preferences.DEFAULT_BIRTHDAY_FORMAT)));
		} catch (NumberFormatException e) {
			mBirthdayFormat = Preferences.DEFAULT_BIRTHDAY_FORMAT;
		}
		mSyncStatuses = settings.getBoolean("sync_statuses", Preferences.DEFAULT_SYNC_STATUSES);
		mSyncEmails = settings.getBoolean("sync_emails", Preferences.DEFAULT_SYNC_EMAILS);
		mFullSync = settings.getBoolean("full_sync", false);
		mShowNotifications = settings.getBoolean("show_notif", Preferences.DEFAULT_SHOW_NOTIFICATIONS);
		try {
			mConnTimeout = Integer.parseInt(settings.getString("conn_timeout", Integer.toString(Preferences.DEFAULT_CONNECTION_TIMEOUT)));
		} catch (NumberFormatException e) {
			mConnTimeout = Preferences.DEFAULT_CONNECTION_TIMEOUT;
		}
		mDisableAds = settings.getBoolean("disable_ads", Preferences.DEFAULT_DISABLE_ADS);
	}
	
	public void savePreferences() {
		SharedPreferences.Editor editor = getSharedPreferences().edit();
		
		editor.putString("sync_type", Integer.toString(mSyncType.ordinal()));
		editor.putString("sync_freq", Integer.toString(mSyncFreq));
		editor.putString("pic_size", Integer.toString(mPicSize));
		editor.putBoolean("sync_all", mSyncAll);
		editor.putBoolean("sync_wifi_only", mSyncWifiOnly);
		editor.putBoolean("sync_join_by_id", mJoinById);
		editor.putBoolean("sync_birthdays", mSyncBirthdays);
		editor.putString("birthday_format", Integer.toString(mBirthdayFormat));
		editor.putBoolean("sync_statuses", mSyncStatuses);
		editor.putBoolean("sync_emails", mSyncEmails);
		editor.putBoolean("full_sync", mFullSync);
		editor.putString("conn_timeout", Integer.toString(mConnTimeout));
		editor.putBoolean("disable_ads", mDisableAds);
		
		editor.commit();
	}
	
	public boolean wifiConnected() {
		ConnectivityManager connectivityManager = (ConnectivityManager) this.getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo networkInfo = null;
		
		if (connectivityManager != null) {
			networkInfo = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
		}
		
		return networkInfo == null ? false : networkInfo.isConnected();
	}
	
	public Account getAccount() {
		AccountManager am = AccountManager.get(this);
		Account[] accounts = am.getAccountsByType(Constants.ACCOUNT_TYPE);
		
		if (accounts.length > 0) {
			return accounts[0];
		}
		
		return null;
	}
}
