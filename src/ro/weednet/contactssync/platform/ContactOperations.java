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
import android.accounts.Account;
import android.content.ContentProviderOperation;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.GroupMembership;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.RawContacts;
import android.text.TextUtils;

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
	
	public ContactOperations addGroupMembership(long groupId) {
		mValues.clear();
		mValues.put(GroupMembership.GROUP_ROW_ID, groupId);
		mValues.put(GroupMembership.MIMETYPE, GroupMembership.CONTENT_ITEM_TYPE);
		addInsertOp();
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
	
	public ContactOperations updateSyncData1(String data, Uri uri) {
		mValues.clear();
		mValues.put(RawContacts.SYNC1, data);
		addUpdateOp(uri);
		return this;
	}
	
	public ContactOperations updateSyncData2(String data, Uri uri) {
		mValues.clear();
		mValues.put(RawContacts.SYNC2, data);
		addUpdateOp(uri);
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
