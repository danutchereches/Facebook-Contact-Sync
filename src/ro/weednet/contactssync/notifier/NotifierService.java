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
package ro.weednet.contactssync.notifier;

import ro.weednet.ContactsSync;
import ro.weednet.contactssync.Constants;
import ro.weednet.contactssync.client.ContactPhoto;
import ro.weednet.contactssync.client.NetworkUtilities;
import ro.weednet.contactssync.client.RawContact;
import ro.weednet.contactssync.platform.BatchOperation;
import ro.weednet.contactssync.platform.ContactManager;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.IntentService;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract.RawContacts;
import android.util.Log;

/**
 * Service to handle view notifications. This allows the sample sync adapter to update the
 * information when the contact is being looked at
 */
public class NotifierService extends IntentService {
	private static final String TAG = "NotifierService";
	
	public NotifierService() {
		super(TAG);
	}
	
	@Override
	protected void onHandleIntent(Intent intent) {
		if (((ContactsSync) getApplication()).getSyncType() == ContactsSync.SyncType.LEGACY) {
			return;
		}
		
		Uri uri = intent.getData();
		final ContentResolver resolver = getContentResolver();
		final Cursor c = resolver. query(uri, null, null, null, null);
		
		if ((c != null) && c.moveToFirst()) {
			Log.i(TAG, "Contact found: " + uri);
			
			String accountName = c.getString(c.getColumnIndex(RawContacts.ACCOUNT_NAME));
			String accountType = c.getString(c.getColumnIndex(RawContacts.ACCOUNT_TYPE));
			long rawContactId = ContentUris.parseId(uri);
			String uid = c.getString(c.getColumnIndex(RawContacts.SOURCE_ID));
			RawContact rawContact = RawContact.create(rawContactId, uid);
			long checkTimestamp = c.getLong(c.getColumnIndex(RawContacts.SYNC1));
			
			if (System.currentTimeMillis() - checkTimestamp < 60000) {
				Log.i(TAG, "contact up to date. quiting");
				return;
			}
			
			Log.i(TAG, "Contact id: " + rawContactId);
			
		//	RawContact rawContact = null;//new RawContact(rawContactId, uid, email, firstName, lastName, birthday, statusMessage, statusTimestamp, avatarUrl, syncState);
			
			AccountManager am = AccountManager.get(this);
			Account[] accounts = am.getAccountsByType(accountType);
			Account account = null;
			
			for (int i = 0; i < accounts.length; i++) {
				if (accounts[i].name.equals(accountName)) {
					account = accounts[i];
					break;
				}
			}
			
			if (account == null) {
				Log.i(TAG, "cannnot find account!!!");
				return;
			}
			
			final BatchOperation batchOperation = new BatchOperation(this, resolver);
			try {
				String authtoken = am.blockingGetAuthToken(account, Constants.AUTHTOKEN_TYPE, true);
				NetworkUtilities nu = new NetworkUtilities(authtoken, this);
				int size = ContactManager.getPhotoPickSize(this);
				//TODO: use selected value
				ContactPhoto photo = nu.getContactPhotoHD(rawContact, size, size);
				ContactManager.updateContactPhotoHd(this, resolver, rawContactId, photo, batchOperation);
				batchOperation.execute();
			} catch (Exception e) {
				Log.i(TAG, "photo update error: " + e.getMessage());
				e.printStackTrace();
			}
			
			//final Uri uri2 = ContentUris.withAppendedId(Data.CONTENT_URI, id);
			
		} else {
			Log.i(TAG, "Contact not found: " + uri);
		}
	}
}
