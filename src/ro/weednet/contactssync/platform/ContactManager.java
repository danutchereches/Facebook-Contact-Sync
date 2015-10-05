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

import android.accounts.Account;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.DisplayPhoto;
import android.provider.ContactsContract.Groups;
import android.provider.ContactsContract.RawContacts;
import android.provider.ContactsContract.StreamItems;
import android.util.Log;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.http.auth.AuthenticationException;

import ro.weednet.ContactsSync;
import ro.weednet.contactssync.client.ContactPhoto;
import ro.weednet.contactssync.client.NetworkUtilities;
import ro.weednet.contactssync.client.RawContact;

public class ContactManager {
	public static final String CUSTOM_IM_PROTOCOL = "fb";
	private static final String TAG = "ContactManager";
	public static final String GROUP_NAME = "Friends";
	
	public static long ensureGroupExists(Context context, Account account) {
		final ContentResolver resolver = context.getContentResolver();
		
		// Lookup the group
		long groupId = 0;
		final Cursor cursor = resolver.query(Groups.CONTENT_URI,
				new String[] { Groups._ID },
				Groups.ACCOUNT_NAME + "=? AND " + Groups.ACCOUNT_TYPE
						+ "=? AND " + Groups.TITLE + "=?", new String[] {
						account.name, account.type, GROUP_NAME }, null);
		if (cursor != null) {
			try {
				if (cursor.moveToFirst()) {
					groupId = cursor.getLong(0);
				}
			} finally {
				cursor.close();
			}
		}
		
		if (groupId == 0) {
			// Group doesn't exist yet, so create it
			final ContentValues contentValues = new ContentValues();
			contentValues.put(Groups.ACCOUNT_NAME, account.name);
			contentValues.put(Groups.ACCOUNT_TYPE, account.type);
			contentValues.put(Groups.TITLE, GROUP_NAME);
		//	contentValues.put(Groups.GROUP_IS_READ_ONLY, true);
			contentValues.put(Groups.GROUP_VISIBLE, true);
			
			final Uri newGroupUri = resolver.insert(Groups.CONTENT_URI, contentValues);
			groupId = ContentUris.parseId(newGroupUri);
		}
		return groupId;
	}
	
	public static synchronized List<RawContact> updateContacts(Context context, Account account,
			List<RawContact> rawContacts, long groupId, boolean joinById, boolean allContacts) {
		
		ArrayList<RawContact> syncList = new ArrayList<RawContact>();
		final ContentResolver resolver = context.getContentResolver();
		final BatchOperation batchOperation = new BatchOperation(context, resolver);
		
		Log.d(TAG, "In updateContacts");
		for (final RawContact rawContact : rawContacts) {
			final long rawContactId = lookupRawContact(resolver, account, rawContact.getUid());
			if (rawContactId > 0) {
				rawContact.setRawContactId(rawContactId);
				updateContact(context, resolver, rawContact, true, true, rawContactId, batchOperation);
				syncList.add(rawContact);
			} else {
				long contactId = lookupContact(resolver, rawContact.getFullName(), rawContact.getUid(), joinById, allContacts);
				if (joinById && contactId > 0) {
					rawContact.setRawContactId(0);
					rawContact.setJoinContactId(contactId);
				}
				if (allContacts || contactId >= 0) {
					addContact(context, account, rawContact, groupId, true, batchOperation);
					rawContact.setRawContactId(0);
					syncList.add(rawContact);
				}
			}
			
			if (batchOperation.size() >= 10) {
				batchOperation.execute();
			}
		}
		
		batchOperation.execute();
		
		for (final RawContact rawContact : rawContacts) {
			if (rawContact.getRawContactId() == 0 && rawContact.getAvatarUrl() != null) {
				Log.d(TAG, "adding inital thumbnail for contact " + rawContact.getUid());
				final long rawContactId = lookupRawContact(resolver, account, rawContact.getUid());
				ContactManager.setContactPhoto(context, resolver, rawContactId, rawContact.getAvatarUrl(), batchOperation, false);
				
				if (batchOperation.size() >= 10) {
					batchOperation.execute();
				}
			}
		}
		
		batchOperation.execute();
		
		return syncList;
	}
	
	public static synchronized void updateContactDetails(Context context, List<RawContact> contacts,
			NetworkUtilities nu) throws AuthenticationException, IOException {
		final ContentResolver resolver = context.getContentResolver();
		final BatchOperation batchOperation = new BatchOperation(context, resolver);
		final int pictureSize = getPhotoPickSize(context);
		
		Iterator<RawContact> iterator = contacts.iterator();
		while (iterator.hasNext()) {
			RawContact contact = iterator.next();
			try {
				Log.i(TAG, "checking user: " + contact.getUid() + " av " + contact.getAvatarUrl());
				//TODO: use selected value
				ContactPhoto photo = nu.getContactPhotoHD(contact, pictureSize, pictureSize);
				if (!photo.getPhotoUrl().equals(contact.getAvatarUrl())) {
					ContactManager.setContactPhoto(context, resolver, contact.getRawContactId(), photo.getPhotoUrl(), batchOperation, true);
				}
			} catch (Exception e) {
				Log.e(TAG, e.toString());
			}
			if (batchOperation.size() >= 10) {
				batchOperation.execute();
			}
		}
		
		batchOperation.execute();
	}
	
	public static List<RawContact> getLocalContacts(Context context, Uri uri) {
		Log.i(TAG, "*** Looking for local contacts");
		List<RawContact> localContacts = new ArrayList<RawContact>();
		
		final ContentResolver resolver = context.getContentResolver();
		final Cursor c = resolver.query(uri,
				new String[] { Contacts._ID, RawContacts.SOURCE_ID, RawContacts.SYNC1, RawContacts.SYNC2 },
				null, null, null);
		try {
			while (c.moveToNext()) {
				RawContact rawContact = new RawContact(c.getLong(0), c.getString(1), null, null, c.getString(3), c.getLong(2));
				localContacts.add(rawContact);
			}
		} finally {
			if (c != null) {
				c.close();
			}
		}
		
		Log.i(TAG, "*** ... found " + localContacts.size());
		return localContacts;
	}
	
	public static int getLocalContactsCount(Context context, Account account) {
		Log.i(TAG, "*** Counting local contacts");
		
		final Uri uri = RawContacts.CONTENT_URI.buildUpon()
			.appendQueryParameter(RawContacts.ACCOUNT_NAME, account.name)
			.appendQueryParameter(RawContacts.ACCOUNT_TYPE, account.type)
			.build();
		
		final ContentResolver resolver = context.getContentResolver();
		final Cursor c = resolver.query(uri,
				new String[] { Contacts._ID, RawContacts.SOURCE_ID },
				null, null, null);
		
		int count = 0;
		try {
			count = c.getCount();
		} catch (Exception e) {
		} finally {
			if (c != null) {
				c.close();
			}
		}
		
		Log.i(TAG, "*** ... found " + count);
		return count;
	}
	
	public static List<RawContact> getStarredContacts(Context context, Uri uri) {
		Log.i(TAG, "*** Looking for starred contacts");
		
		final ContentResolver resolver = context.getContentResolver();
		
		Set<Long> contactIds = new HashSet<Long>();
		Cursor c = resolver.query(RawContacts.CONTENT_URI, new String[] { RawContacts.CONTACT_ID },
			RawContacts.STARRED + "!=0", null, null);
		try {
			while (c.moveToNext()) {
				contactIds.add(c.getLong(0));
			}
		} finally {
			if (c != null) {
				c.close();
			}
		}
		Log.i(TAG, "*** ... found " + contactIds.size() + " starred");
		
		int i = 0;
		StringBuilder sb = new StringBuilder();
		for (Long s : contactIds)
		{
			sb.append(s);
			if (++i >= Math.min(contactIds.size(), 50)) {
				break;
			}
			sb.append(",");
		}
		
		List<RawContact> contacts = new ArrayList<RawContact>();
		c = resolver.query(uri, new String[] { Contacts._ID, RawContacts.SOURCE_ID },
			RawContacts.CONTACT_ID + " IN (" + sb.toString() + ")", null, null);
		try {
			while (c.moveToNext()) {
				final long rawContactId = c.getLong(0);
				final String serverContactId = c.getString(1);
				RawContact rawContact = RawContact.create(rawContactId, serverContactId);
				contacts.add(rawContact);
			}
		} catch (Exception e) {
			Log.i(TAG, "failing .. " + e.toString());
		} finally {
			if (c != null) {
				c.close();
			}
		}
		
		Log.i(TAG, "*** ... and " + contacts.size() + " of mine " + sb.toString());
		return contacts;
	}
	
	public static void addJoins(Context context, Account account, List<RawContact> rawContacts) {
		final ContentResolver resolver = context.getContentResolver();
		final BatchOperation batchOperation = new BatchOperation(context, resolver);
		for (RawContact rawContact : rawContacts) {
			if (rawContact.getJoinContactId() > 0) {
				addAggregateException(context, account, rawContact, batchOperation);
			}
		}
		batchOperation.execute();
	}
	
	public static void deleteContacts(Context context, List<RawContact> localContacts) {
		final ContentResolver resolver = context.getContentResolver();
		final BatchOperation batchOperation = new BatchOperation(context, resolver);
		
		for (RawContact rawContact : localContacts) {
			final long rawContactId = rawContact.getRawContactId();
			if (rawContactId > 0) {
				ContactManager.deleteContact(context, rawContactId, batchOperation);
			}
		}
		
		batchOperation.execute();
	}
	
	public static void deleteMissingContacts(Context context, List<RawContact> localContacts, List<RawContact> serverContacts) {
		if (localContacts.size() == 0) {
			return;
		}
		
		final ContentResolver resolver = context.getContentResolver();
		final BatchOperation batchOperation = new BatchOperation(context, resolver);
		
		final HashSet<String> contactsIds = new HashSet<String>();
		for (RawContact rawContact : serverContacts) {
			contactsIds.add(rawContact.getUid());
		}
		
		for (RawContact rawContact : localContacts) {
			if (!contactsIds.contains(rawContact.getUid())) {
				final long rawContactId = rawContact.getRawContactId();
				if (rawContactId > 0) {
					ContactManager.deleteContact(context, rawContactId, batchOperation);
				}
			}
		}
		
		batchOperation.execute();
	}
	
	public static void addContact(Context context, Account account,
			RawContact rawContact, long groupId, boolean inSync,
			BatchOperation batchOperation) {
		
		// Put the data in the contacts provider
		final ContactOperations contactOp = ContactOperations.createNewContact(
				context, rawContact.getUid(), account, inSync, batchOperation);
		
		contactOp
				.addName(rawContact.getFirstName(), rawContact.getLastName())
				.addGroupMembership(groupId);
		
		// If we have a serverId, then go ahead and create our status profile.
		// Otherwise skip it - and we'll create it after we sync-up to the
		// server later on.
		if (rawContact.getUid() != null) {
			contactOp.addProfileAction(rawContact.getUid());
		}
	}
	
	public static void updateContact(Context context, ContentResolver resolver,
			RawContact rawContact, boolean updateAvatar, boolean inSync,
			long rawContactId, BatchOperation batchOperation) {
		
		final Cursor c = resolver.query(DataQuery.CONTENT_URI,
				DataQuery.PROJECTION, DataQuery.SELECTION,
				new String[] { String.valueOf(rawContactId) }, null);
		final ContactOperations contactOp = ContactOperations.updateExistingContact(context, rawContactId, inSync, batchOperation);
		try {
			// Iterate over the existing rows of data, and update each one
			// with the information we received from the server.
			while (c.moveToNext()) {
				final long id = c.getLong(DataQuery.COLUMN_ID);
				final String mimeType = c.getString(DataQuery.COLUMN_MIMETYPE);
				final Uri uri = ContentUris.withAppendedId(Data.CONTENT_URI, id);
				if (mimeType.equals(StructuredName.CONTENT_ITEM_TYPE)) {
					contactOp.updateName(uri,
							c.getString(DataQuery.COLUMN_GIVEN_NAME),
							c.getString(DataQuery.COLUMN_FAMILY_NAME),
							rawContact.getFirstName(),
							rawContact.getLastName());
				}
			} // while
		} finally {
			c.close();
		}
		
		// If we don't have a status profile, then create one. This could
		// happen for contacts that were created on the client - we don't
		// create the status profile until after the first sync...
		final String serverId = rawContact.getUid();
		final long profileId = lookupProfile(resolver, serverId);
		if (profileId <= 0) {
			contactOp.addProfileAction(serverId);
		}
	}
	
	public static void setContactPhoto(Context context, ContentResolver resolver,
			long rawContactId, String avatarUrl, BatchOperation batchOperation, boolean highRes) {
		Uri rawContactUri = ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawContactId);
		Uri outputFileUri = Uri.withAppendedPath(rawContactUri, RawContacts.DisplayPhoto.CONTENT_DIRECTORY);
		try {
			AssetFileDescriptor descriptor = context.getContentResolver().openAssetFileDescriptor(outputFileUri, "rw");
			FileOutputStream stream = descriptor.createOutputStream();
			
			try {
				stream.write(NetworkUtilities.downloadAvatar(avatarUrl));
			} finally {
				stream.close();
				descriptor.close();
			}
		} catch (IOException e) {
			
		}
		
		if (highRes) {
			Log.d(TAG, "updating check timestamp and avatar url");
			final ContactOperations contactOp = ContactOperations.updateExistingContact(context, rawContactId, true, batchOperation);
			final Uri uri = ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawContactId);
			contactOp.updateSyncData1(Long.toString(System.currentTimeMillis()), uri);
			contactOp.updateSyncData2(avatarUrl, uri);
		}
	}
	
	public static void addAggregateException(Context context, Account account,
			RawContact rawContact, BatchOperation batchOperation) {
		final long rawContactId = lookupRawContact(context.getContentResolver(), account, rawContact.getUid());
		
		if (rawContactId <= 0) {
			return;
		}
		
		ContentProviderOperation.Builder builder;
		builder = ContentProviderOperation.newUpdate(ContactsContract.AggregationExceptions.CONTENT_URI)
			.withValue(ContactsContract.AggregationExceptions.RAW_CONTACT_ID1, Long.toString(rawContact.getJoinContactId()))
			.withValue(ContactsContract.AggregationExceptions.RAW_CONTACT_ID2, Long.toString(rawContactId))
			.withValue(ContactsContract.AggregationExceptions.TYPE, ContactsContract.AggregationExceptions.TYPE_KEEP_TOGETHER);
		
		batchOperation.add(builder.build());
	}
	
	private static void deleteContact(Context context, long rawContactId, BatchOperation batchOperation) {
		batchOperation.add(
			ContactOperations.newDeleteCpo(
				ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawContactId),
				true, true
			).build());
	}
	
	private static long lookupRawContact(ContentResolver resolver, Account account,
			String serverContactId) {
		long rawContactId = 0;
		final Cursor c = resolver.query(UserIdQuery.CONTENT_URI,
				UserIdQuery.PROJECTION, UserIdQuery.SELECTION,
				new String[] { account.name, account.type, serverContactId }, null);
		try {
			if ((c != null) && c.moveToFirst()) {
				rawContactId = c.getLong(UserIdQuery.COLUMN_RAW_CONTACT_ID);
			}
		} finally {
			if (c != null) {
				c.close();
			}
		}
		return rawContactId;
	}
	
	private static long lookupContact(ContentResolver resolver, String name, String fb_id, boolean joinById, boolean syncAll) {
		Cursor c;
		
		if (joinById) {
			c = resolver.query(
				ContactsContract.Data.CONTENT_URI, //table
				new String[] { ContactsContract.Data.RAW_CONTACT_ID }, //select (projection)
				ContactsContract.Data.MIMETYPE + "=? AND " + CommonDataKinds.Note.NOTE + " LIKE ?", //where
				new String[] { ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE, "%" + fb_id + "%" }, //params
				null //sort
			);
			try {
				if (c != null && c.moveToFirst()) {
					return c.getLong(0);
				}
			} finally {
				if (c != null) {
					c.close();
				}
			}
		}
		
		if (syncAll) {
			return -1;
		}
		
		c = resolver.query(
			Contacts.CONTENT_URI, //table
			new String[] { Contacts._ID }, //select (projection)
			Contacts.DISPLAY_NAME + "=?", //where
			new String[] { name }, //params
			null //sort
		);
		try {
			if (c != null && c.getCount() > 0) {
				return 0;
			}
		} finally {
			if (c != null) {
				c.close();
			}
		}
		
		return -1;
	}
	
	private static long lookupProfile(ContentResolver resolver, String userId) {
		long profileId = 0;
		final Cursor c = resolver.query(Data.CONTENT_URI,
				ProfileQuery.PROJECTION, ProfileQuery.SELECTION,
				new String[] { userId }, null);
		try {
			if ((c != null) && c.moveToFirst()) {
				profileId = c.getLong(ProfileQuery.COLUMN_ID);
			}
		} finally {
			if (c != null) {
				c.close();
			}
		}
		return profileId;
	}
	
	public static int getPhotoPickSize(Context context) {
		Cursor c = context.getContentResolver().query(DisplayPhoto.CONTENT_MAX_DIMENSIONS_URI,
			new String[]{ DisplayPhoto.DISPLAY_MAX_DIM }, null, null, null);
		
		try {
			c.moveToFirst();
			return c.getInt(0);
		} catch (Exception e) {
		} finally {
			if (c != null) {
				c.close();
			}
		}
		
		return ContactsSync.getInstance().getPictureSize();
	}
	
	public static int getStreamItemLimit(Context context) {
		Cursor c = context.getContentResolver().query(StreamItems.CONTENT_LIMIT_URI,
			new String[]{ StreamItems.MAX_ITEMS }, null, null, null);
		
		try {
			c.moveToFirst();
			return c.getInt(0);
		} catch (Exception e) {
		} finally {
			if (c != null) {
				c.close();
			}
		}
		
		return 1;
	}
	
	final public static class EditorQuery {
		
		private EditorQuery() {
			
		}
		
		public static final String[] PROJECTION = new String[] {
				RawContacts.ACCOUNT_NAME, Data._ID, RawContacts.Entity.DATA_ID,
				Data.MIMETYPE, Data.DATA1, Data.DATA2, Data.DATA3, Data.DATA15,
				Data.SYNC1 };
		
		public static final int COLUMN_ACCOUNT_NAME = 0;
		public static final int COLUMN_RAW_CONTACT_ID = 1;
		public static final int COLUMN_DATA_ID = 2;
		public static final int COLUMN_MIMETYPE = 3;
		public static final int COLUMN_DATA1 = 4;
		public static final int COLUMN_DATA2 = 5;
		public static final int COLUMN_DATA3 = 6;
		public static final int COLUMN_DATA15 = 7;
		public static final int COLUMN_SYNC1 = 8;
		
		public static final int COLUMN_PHONE_NUMBER = COLUMN_DATA1;
		public static final int COLUMN_PHONE_TYPE = COLUMN_DATA2;
		public static final int COLUMN_EMAIL_ADDRESS = COLUMN_DATA1;
		public static final int COLUMN_EMAIL_TYPE = COLUMN_DATA2;
		public static final int COLUMN_GIVEN_NAME = COLUMN_DATA1;
		public static final int COLUMN_FAMILY_NAME = COLUMN_DATA2;
		public static final int COLUMN_BIRTHDAY_DATE = COLUMN_DATA1;
		public static final int COLUMN_AVATAR_IMAGE = COLUMN_DATA15;
		public static final int COLUMN_SYNC_DIRTY = COLUMN_SYNC1;
		
		public static final String SELECTION = Data.RAW_CONTACT_ID + "=?";
	}
	
	final private static class ProfileQuery {
		private ProfileQuery() {
			
		}
		
		public final static String[] PROJECTION = new String[] { Data._ID };
		
		public final static int COLUMN_ID = 0;
		
		public static final String SELECTION = Data.MIMETYPE + "='"
				+ SyncAdapterColumns.MIME_PROFILE + "' AND "
				+ SyncAdapterColumns.DATA_PID + "=?";
	}
	
	final private static class UserIdQuery {
		private UserIdQuery() {
			
		}
		
		public final static String[] PROJECTION = new String[] {
				RawContacts._ID, RawContacts.CONTACT_ID
		};
		
		public final static int COLUMN_RAW_CONTACT_ID = 0;
	//	public final static int COLUMN_LINKED_CONTACT_ID = 1;
		
		public final static Uri CONTENT_URI = RawContacts.CONTENT_URI;
		
		public static final String SELECTION = RawContacts.ACCOUNT_NAME + "=? AND "
				+ RawContacts.ACCOUNT_TYPE + "=? AND "
				+ RawContacts.SOURCE_ID + "=?";
	}
	
	@SuppressWarnings("unused")
	final private static class DataQuery {
		private DataQuery() {
			
		}
		
		public static final String[] PROJECTION = new String[] { Data._ID,
				RawContacts.SOURCE_ID, Data.MIMETYPE, Data.DATA1, Data.DATA2,
				Data.DATA3, Data.DATA15, Data.SYNC1 };
		
		public static final int COLUMN_ID = 0;
		public static final int COLUMN_SERVER_ID = 1;
		public static final int COLUMN_MIMETYPE = 2;
		public static final int COLUMN_DATA1 = 3;
		public static final int COLUMN_DATA2 = 4;
		public static final int COLUMN_DATA3 = 5;
		public static final int COLUMN_DATA15 = 6;
		public static final int COLUMN_SYNC1 = 7;
		
		public static final Uri CONTENT_URI = Data.CONTENT_URI;
		
		public static final int COLUMN_PHONE_NUMBER = COLUMN_DATA1;
		public static final int COLUMN_PHONE_TYPE = COLUMN_DATA2;
		public static final int COLUMN_EMAIL_ADDRESS = COLUMN_DATA1;
		public static final int COLUMN_EMAIL_TYPE = COLUMN_DATA2;
		public static final int COLUMN_BIRTHDAY_DATE = COLUMN_DATA1;
		public static final int COLUMN_BIRTHDAY_TYPE = COLUMN_DATA2;
		public static final int COLUMN_GIVEN_NAME = COLUMN_DATA1;
		public static final int COLUMN_FAMILY_NAME = COLUMN_DATA2;
		public static final int COLUMN_AVATAR_IMAGE = COLUMN_DATA15;
		public static final int COLUMN_SYNC_TIMESTAMP = COLUMN_SYNC1;
		public static final int COLUMN_SYNC_PHOTO_TIMESTAMP = COLUMN_SYNC1;
		
		public static final String SELECTION = Data.RAW_CONTACT_ID + "=?";
	}
	
	final public static class ContactQuery {
		private ContactQuery() {
			
		}
		
		public static final String[] PROJECTION = new String[] { Contacts._ID,
				Contacts.DISPLAY_NAME };
		
		public static final int COLUMN_ID = 0;
		public static final int COLUMN_DISPLAY_NAME = 1;
	}
}
