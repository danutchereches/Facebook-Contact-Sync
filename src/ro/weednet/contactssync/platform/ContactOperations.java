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
package ro.weednet.contactssync.platform;

import ro.weednet.contactssync.R;
import ro.weednet.contactssync.client.NetworkUtilities;
import android.accounts.Account;
import android.content.ContentProviderOperation;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Event;
import android.provider.ContactsContract.CommonDataKinds.GroupMembership;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.Photo;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.RawContacts;
import android.text.TextUtils;
import android.util.Log;

public class ContactOperations {
	private final ContentValues mValues;
	private final BatchOperation mBatchOperation;
	private final Context mContext;
	private boolean mIsSyncOperation;
	private long mRawContactId;
	private int mBackReference;
	private boolean mIsNewContact;
	
	private boolean mIsYieldAllowed;
	
	public static ContactOperations createNewContact(Context context,
			String userId, Account account, boolean isSyncOperation,
			BatchOperation batchOperation) {
		return new ContactOperations(context, userId, account,
				isSyncOperation, batchOperation);
	}
	
	public static ContactOperations updateExistingContact(Context context, long rawContactId, boolean isSyncOperation, BatchOperation batchOperation) {
		return new ContactOperations(context, rawContactId, isSyncOperation, batchOperation);
	}
	
	public ContactOperations(Context context, boolean isSyncOperation, BatchOperation batchOperation) {
		mValues = new ContentValues();
		mIsYieldAllowed = true;
		mIsSyncOperation = isSyncOperation;
		mContext = context;
		mBatchOperation = batchOperation;
	}
	
	public ContactOperations(Context context, String userId, Account account,
			boolean isSyncOperation, BatchOperation batchOperation) {
		this(context, isSyncOperation, batchOperation);
		mBackReference = mBatchOperation.size();
		mIsNewContact = true;
		mValues.put(RawContacts.SOURCE_ID, userId);
		mValues.put(RawContacts.ACCOUNT_TYPE, account.type);
		mValues.put(RawContacts.ACCOUNT_NAME, account.name);
		ContentProviderOperation.Builder builder = newInsertCpo(
				RawContacts.CONTENT_URI, mIsSyncOperation, true).withValues(mValues);
		mBatchOperation.add(builder.build());
	}
	
	public ContactOperations(Context context, long rawContactId,
			boolean isSyncOperation, BatchOperation batchOperation) {
		this(context, isSyncOperation, batchOperation);
		mIsNewContact = false;
		mRawContactId = rawContactId;
	}
	
	public ContactOperations addName(String firstName, String lastName) {
		mValues.clear();
		
		if (!TextUtils.isEmpty(firstName)) {
			mValues.put(StructuredName.GIVEN_NAME, firstName);
			mValues.put(StructuredName.MIMETYPE,
					StructuredName.CONTENT_ITEM_TYPE);
		}
		if (!TextUtils.isEmpty(lastName)) {
			mValues.put(StructuredName.FAMILY_NAME, lastName);
			mValues.put(StructuredName.MIMETYPE,
					StructuredName.CONTENT_ITEM_TYPE);
		}
		if (mValues.size() > 0) {
			addInsertOp();
		}
		return this;
	}
	
	public ContactOperations addEmail(String email) {
		mValues.clear();
		if (!TextUtils.isEmpty(email)) {
			mValues.put(Email.DATA, email);
			mValues.put(Email.TYPE, Email.TYPE_OTHER);
			mValues.put(Email.MIMETYPE, Email.CONTENT_ITEM_TYPE);
			addInsertOp();
		}
		return this;
	}
	
	public ContactOperations addPhone(String phone, int phoneType) {
		mValues.clear();
		if (!TextUtils.isEmpty(phone)) {
			mValues.put(Phone.NUMBER, phone);
			mValues.put(Phone.TYPE, phoneType);
			mValues.put(Phone.MIMETYPE, Phone.CONTENT_ITEM_TYPE);
			addInsertOp();
		}
		return this;
	}
	
	public ContactOperations addBirthday(String birthday) {
		mValues.clear();
		if (!TextUtils.isEmpty(birthday)) {
			mValues.put(Event.START_DATE, birthday);
			mValues.put(Event.TYPE, Event.TYPE_BIRTHDAY);
			mValues.put(Event.MIMETYPE, Event.CONTENT_ITEM_TYPE);
			addInsertOp();
		}
		return this;
	}
	
	public ContactOperations addGroupMembership(long groupId) {
		mValues.clear();
		mValues.put(GroupMembership.GROUP_ROW_ID, groupId);
		mValues.put(GroupMembership.MIMETYPE, GroupMembership.CONTENT_ITEM_TYPE);
		addInsertOp();
		return this;
	}
	
	public ContactOperations addAvatar(String avatarUrl) {
		if (avatarUrl != null) {
			byte[] avatarBuffer = NetworkUtilities.downloadAvatar(avatarUrl);
			if (avatarBuffer != null) {
				Log.e("DownloadPhoto", "image downloaded; addinf");
				mValues.clear();
				mValues.put(Photo.DATA1, avatarUrl);
				mValues.put(Photo.PHOTO, avatarBuffer);
				mValues.put(Photo.MIMETYPE, Photo.CONTENT_ITEM_TYPE);
				addInsertOp();
			} else {
				Log.e("DownloadPhoto", "failed, buffer null");
			}
		}
		return this;
	}
	
	public ContactOperations addProfileAction(String userId) {
		mValues.clear();
		if (userId != null) {
			mValues.put(SyncAdapterColumns.DATA_PID, userId);
			mValues.put(SyncAdapterColumns.DATA_SUMMARY, mContext.getString(R.string.profile_action));
			mValues.put(SyncAdapterColumns.DATA_DETAIL, mContext.getString(R.string.view_profile));
			mValues.put(Data.MIMETYPE, SyncAdapterColumns.MIME_PROFILE);
			addInsertOp();
		}
		return this;
	}
	
	public ContactOperations updateServerId(String serverId, Uri uri) {
		mValues.clear();
		mValues.put(RawContacts.SOURCE_ID, serverId);
		addUpdateOp(uri);
		return this;
	}
	
	public ContactOperations updateEmail(String email, String existingEmail,
			Uri uri) {
		if (!TextUtils.equals(existingEmail, email)) {
			mValues.clear();
			mValues.put(Email.DATA, email);
			addUpdateOp(uri);
		}
		return this;
	}
	
	public ContactOperations updateBirthday(String birthday, String existingBirthday,
			Uri uri) {
		if (!TextUtils.equals(existingBirthday, birthday)) {
			mValues.clear();
			mValues.put(Event.START_DATE, birthday);
			addUpdateOp(uri);
		}
		return this;
	}
	
	public ContactOperations updateName(Uri uri, String existingFirstName,
			String existingLastName, String firstName, String lastName) {
		
		mValues.clear();
		if (!TextUtils.equals(existingFirstName, firstName)) {
			mValues.put(StructuredName.GIVEN_NAME, firstName);
		}
		if (!TextUtils.equals(existingLastName, lastName)) {
			mValues.put(StructuredName.FAMILY_NAME, lastName);
		}
		if (mValues.size() > 0) {
			addUpdateOp(uri);
		}
		return this;
	}
	
	public ContactOperations updateDirtyFlag(boolean isDirty, Uri uri) {
		int isDirtyValue = isDirty ? 1 : 0;
		mValues.clear();
		mValues.put(RawContacts.DIRTY, isDirtyValue);
		addUpdateOp(uri);
		return this;
	}
	
	public ContactOperations updateSyncTimestamp(long checkTimestamp, long photoTimestamp, Uri uri) {
		mValues.clear();
		mValues.put(RawContacts.SYNC1, checkTimestamp);
		mValues.put(RawContacts.SYNC2, photoTimestamp);
		addUpdateOp(uri);
		return this;
	}
	
	public ContactOperations updatePhone(String existingNumber, String phone, Uri uri) {
		if (!TextUtils.equals(phone, existingNumber)) {
			mValues.clear();
			mValues.put(Phone.NUMBER, phone);
			addUpdateOp(uri);
		}
		return this;
	}
	
	public ContactOperations updateAvatar(String existingAvatarUrl, String avatarUrl, Uri uri) {
		if (avatarUrl != null && !TextUtils.equals(existingAvatarUrl, avatarUrl)) {
			byte[] avatarBuffer = NetworkUtilities.downloadAvatar(avatarUrl);
			if (avatarBuffer != null) {
				Log.e("DownloadPhoto", "image downloaded; updating");
				mValues.clear();
				mValues.put(Photo.DATA1, avatarUrl);
				mValues.put(Photo.PHOTO, avatarBuffer);
				mValues.put(Photo.MIMETYPE, Photo.CONTENT_ITEM_TYPE);
				addUpdateOp(uri);
			} else {
				Log.e("DownloadPhoto", "failed, buffer null");
			}
		} else {
			Log.e("DownloadPhoto", "not doing it");
		}
		return this;
	}
	
	public ContactOperations updateProfileAction(Integer userId, Uri uri) {
		mValues.clear();
		mValues.put(SyncAdapterColumns.DATA_PID, userId);
		addUpdateOp(uri);
		return this;
	}
	
	private void addInsertOp() {
		if (!mIsNewContact) {
			mValues.put(Phone.RAW_CONTACT_ID, mRawContactId);
		}
		ContentProviderOperation.Builder builder = newInsertCpo(
				Data.CONTENT_URI, mIsSyncOperation, mIsYieldAllowed);
		builder.withValues(mValues);
		if (mIsNewContact) {
			builder.withValueBackReference(Data.RAW_CONTACT_ID, mBackReference);
		}
		mIsYieldAllowed = false;
		mBatchOperation.add(builder.build());
	}
	
	private void addUpdateOp(Uri uri) {
		ContentProviderOperation.Builder builder = newUpdateCpo(uri,
				mIsSyncOperation, mIsYieldAllowed).withValues(mValues);
		mIsYieldAllowed = false;
		mBatchOperation.add(builder.build());
	}
	
	public static ContentProviderOperation.Builder newInsertCpo(Uri uri,
			boolean isSyncOperation, boolean isYieldAllowed) {
		return ContentProviderOperation.newInsert(
				addCallerIsSyncAdapterParameter(uri, isSyncOperation))
				.withYieldAllowed(isYieldAllowed);
	}
	
	public static ContentProviderOperation.Builder newUpdateCpo(Uri uri,
			boolean isSyncOperation, boolean isYieldAllowed) {
		return ContentProviderOperation.newUpdate(
				addCallerIsSyncAdapterParameter(uri, isSyncOperation))
				.withYieldAllowed(isYieldAllowed);
	}
	
	public static ContentProviderOperation.Builder newDeleteCpo(Uri uri,
			boolean isSyncOperation, boolean isYieldAllowed) {
		return ContentProviderOperation.newDelete(
				addCallerIsSyncAdapterParameter(uri, isSyncOperation))
				.withYieldAllowed(isYieldAllowed);
	}
	
	private static Uri addCallerIsSyncAdapterParameter(Uri uri,
			boolean isSyncOperation) {
		if (isSyncOperation) {
			return uri
					.buildUpon()
					.appendQueryParameter(
							ContactsContract.CALLER_IS_SYNCADAPTER, "true")
					.build();
		}
		return uri;
	}
}
