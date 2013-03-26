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
package ro.weednet.contactssync.syncadapter;

import ro.weednet.ContactsSync;
import ro.weednet.contactssync.Constants;
import ro.weednet.contactssync.R;
import ro.weednet.contactssync.activities.Preferences;
import ro.weednet.contactssync.client.NetworkUtilities;
import ro.weednet.contactssync.client.RawContact;
import ro.weednet.contactssync.platform.ContactManager;

import org.apache.http.ParseException;
import org.apache.http.auth.AuthenticationException;
import org.json.JSONException;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.Intent;
import android.content.SyncResult;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract.RawContacts;
import android.util.Log;

import java.io.IOException;
import java.util.List;

public class SyncAdapter extends AbstractThreadedSyncAdapter {
	private static final String TAG = "SyncAdapter";
	private static final boolean NOTIFY_AUTH_FAILURE = true;
	
	private final AccountManager mAccountManager;
	
	private final Context mContext;
	
	public SyncAdapter(Context context, boolean autoInitialize) {
		super(context, autoInitialize);
		mContext = context;
		mAccountManager = AccountManager.get(context);
	}
	
	@Override
	public void onPerformSync(Account account, Bundle extras, String authority, ContentProviderClient provider, SyncResult syncResult) {
		String authtoken = null;
		try {
		//	ContactManager.setAccountContactsVisibility(getContext(), account, true);
			ContactsSync app = ContactsSync.getInstance();
			
			if (app.getSyncWifiOnly() && !app.wifiConnected()) {
				throw new OperationCanceledException("not on wifi");
			}
			
			authtoken = mAccountManager.blockingGetAuthToken(account, Constants.AUTHTOKEN_TYPE, NOTIFY_AUTH_FAILURE);
			if (authtoken == null) {
				throw new AuthenticationException();
			}
			
			final long groupId = ContactManager.ensureGroupExists(mContext, account);
			final Uri rawContactsUri = RawContacts.CONTENT_URI.buildUpon()
				.appendQueryParameter(RawContacts.ACCOUNT_NAME, account.name)
				.appendQueryParameter(RawContacts.ACCOUNT_TYPE, account.type)
				.build();
			
			List<RawContact> localContacts = ContactManager.getLocalContacts(mContext, rawContactsUri);
			
			if (app.getFullSync()) {
				ContactManager.deleteContacts(mContext, localContacts);
				localContacts.clear();
				app.clearFullSync();
			}
			
			NetworkUtilities nu = new NetworkUtilities(authtoken, mContext);
			List<RawContact> rawContacts = nu.getContacts(account);
			
			List<RawContact> syncedContacts = ContactManager.updateContacts(
				mContext, account, rawContacts, groupId,
				app.getJoinById(), app.getSyncAllContacts());
			
			ContactManager.deleteMissingContacts(mContext, localContacts, syncedContacts);
			
			if (app.getJoinById()) {
				ContactManager.addJoins(mContext, account, rawContacts);
			}
			
			if (app.getSyncStatuses()
			 && app.getSyncType() == ContactsSync.SyncType.LEGACY) {
				ContactManager.updateStatusMessages(mContext, rawContacts);
			}
			
			if (app.getSyncType() == ContactsSync.SyncType.HARD) {
				ContactManager.updateContactDetails(mContext, rawContacts, nu);
			} else if (app.getSyncType() == ContactsSync.SyncType.MEDIUM) {
				List<RawContact> starredContacts = ContactManager.getStarredContacts(mContext, rawContactsUri);
				ContactManager.updateContactDetails(mContext, starredContacts, nu);
			}
			
			NotificationManager mNotificationManager = (NotificationManager) getContext().getSystemService(Context.NOTIFICATION_SERVICE);
			mNotificationManager.cancelAll();
			
		} catch (final AuthenticatorException e) {
			Log.e(TAG, "AuthenticatorException", e);
			syncResult.stats.numParseExceptions++;
			showNotificationError("Error connecting to facebook");
		} catch (final OperationCanceledException e) {
			Log.e(TAG, "OperationCanceledExcetpion", e);
		} catch (final IOException e) {
			Log.e(TAG, "IOException", e);
			syncResult.stats.numIoExceptions++;
			showNotificationError("Error connecting to facebook");
		} catch (final AuthenticationException e) {
			Log.e(TAG, "AuthenticationException", e);
			syncResult.stats.numAuthExceptions++;
			if (authtoken != null) {
				mAccountManager.invalidateAuthToken(account.type, authtoken);
			}
			try {
				authtoken = mAccountManager.blockingGetAuthToken(account, Constants.AUTHTOKEN_TYPE, NOTIFY_AUTH_FAILURE);
			} catch (OperationCanceledException e1) {
				Log.e(TAG, "OperationCanceledExcetpion", e);
			} catch (AuthenticatorException e1) {
				Log.e(TAG, "AuthenticatorException", e);
				syncResult.stats.numParseExceptions++;
			} catch (IOException e1) {
				Log.e(TAG, "IOException", e);
				syncResult.stats.numIoExceptions++;
			}
		} catch (final ParseException e) {
			Log.e(TAG, "ParseException", e);
			syncResult.stats.numParseExceptions++;
			showNotificationError("Error parsing the information from facebook");
		} catch (final JSONException e) {
			Log.e(TAG, "JSONException", e);
			syncResult.stats.numParseExceptions++;
			showNotificationError("Error parsing the information from facebook");
		} catch (final Exception e) {
			Log.e(TAG, "Unknown exception", e);
		}
	}
	
	public void showNotificationError(String message) {
		showNotificationMessage(message, message, "Select to test connection or change settings");
	}
	
	@SuppressWarnings("deprecation")
	public void showNotificationMessage(String tickerText, String title, String desc) {
		if (!ContactsSync.getInstance().getShowNotifications()) {
			return;
		}
		
		NotificationManager mNotificationManager = (NotificationManager) getContext().getSystemService(Context.NOTIFICATION_SERVICE);
		
		Notification notification = new Notification(R.drawable.icon, tickerText, System.currentTimeMillis());
		Context context = ContactsSync.getInstance().getApplicationContext();
		Intent notificationIntent = new Intent(context, Preferences.class);
		PendingIntent contentIntent = PendingIntent.getActivity(context, 0, notificationIntent, 0);
		
		notification.setLatestEventInfo(context, title, desc, contentIntent);
		mNotificationManager.notify(1, notification);
	}
}
