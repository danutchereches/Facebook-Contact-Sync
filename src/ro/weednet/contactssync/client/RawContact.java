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
package ro.weednet.contactssync.client;

import android.text.TextUtils;
import android.util.Log;

import org.json.JSONObject;
import org.json.JSONException;

final public class RawContact {
	public class IMAGE_SIZES {
		public final static int SMALL_SQUARE = 0;
		public final static int SMALL = 1;
		public final static int NORMAL = 2;
		public final static int BIG = 3;
		public final static int SQUARE = 4;
		public final static int BIG_SQUARE = 5;
		public final static int HUGE_SQUARE = 6;
		public final static int MAX = 7;
		public final static int MAX_SQUARE = 8;
	};
	public class BIRTHDAY_FORMATS {
		public final static int DEFAULT = 0;
		public final static int GLOBAL = 1;
		public final static int US = 2;
		public final static int EU = 3;
	};
	
	/** The tag used to log to adb console. **/
	private static final String TAG = "RawContact";
	
	private final long mRawContactId;
	private final String mUid;
	private final String mEmail;
	private final String mFirstName;
	private final String mLastName;
	private final String mAvatarUrl;
	private final String mBirthday;
	private final String mStatusMessage;
	private final long mStatusTimestamp;
	private final long mSyncState;
	private long mJoinContactId;
	
	public long getRawContactId() {
		return mRawContactId;
	}
	public String getUid() {
		return mUid;
	}
	public String getEmail() {
		return mEmail;
	}
	public String getFirstName() {
		return mFirstName;
	}
	public String getLastName() {
		return mLastName;
	}
	public String getFullName() {
		return mFirstName + " " + mLastName;
	}
	public String getAvatarUrl() {
		return mAvatarUrl;
	}
	public String getBirthday() {
		return mBirthday;
	}
	public long getSyncState() {
		return mSyncState;
	}
	public String getBestName() {
		return getFullName();
	}
	public String getStatus() {
		return getStatusMessage();
	}
	public String getStatusMessage() {
		return mStatusMessage;
	}
	public long getStatusTimestamp() {
		return mStatusTimestamp * 1000;
	}
	public long getJoinContactId() {
		return mJoinContactId;
	}
	
	public void setJoinContactId(long id) {
		mJoinContactId = id;
	}
	
	public JSONObject toJSONObject() {
		JSONObject json = new JSONObject();
		
		try {
			if (!TextUtils.isEmpty(mUid)) {
				json.put("uid", mUid);
			}
			if (!TextUtils.isEmpty(mFirstName)) {
				json.put("first_name", mFirstName);
			}
			if (!TextUtils.isEmpty(mLastName)) {
				json.put("last_name", mLastName);
			}
			if (!TextUtils.isEmpty(mBirthday)) {
				json.put("birthday_date", mBirthday);
			}
			if (!TextUtils.isEmpty(mStatusMessage)) {
				JSONObject status = new JSONObject();
				status.put("message", mStatusMessage);
				status.put("timestamp", mStatusTimestamp);
				json.put("status", status);
			}
		} catch (final Exception ex) {
			Log.i(TAG, "Error converting RawContact to JSONObject" + ex.toString());
		}
		
		return json;
	}
	
	public RawContact(long rawContactId, String uid, String email, String firstName, String lastName, String birthday,
			String statusMessage, long statusTimestamp, String avatarUrl,
			long syncState) {
		mRawContactId = rawContactId;
		mUid = uid;
		mEmail = email;
		mFirstName = firstName;
		mLastName = lastName;
		mAvatarUrl = avatarUrl;
		mBirthday = birthday;
		mStatusMessage = statusMessage;
		mStatusTimestamp = statusTimestamp;
		mSyncState = syncState;
		mJoinContactId = -1;
	}
	
	public static RawContact valueOf(JSONObject contact) {
		try {
			final String uid = !contact.isNull("uid") ? contact.getString("uid") : null;
			// If we didn't get either a uid for the contact,
			// then we can't do anything with it locally...
			if (uid == null) {
				throw new JSONException("JSON contact missing required 'uid' field");
			}
			
			final String email = !contact.isNull("username") ?
					contact.getString("username") + "@facebook.com" : null;
			final String firstName = !contact.isNull("first_name") ?
					contact.getString("first_name") : null;
			final String lastName = !contact.isNull("last_name") ?
					contact.getString("last_name") : null;
			final String avatarUrl = !contact.isNull("picture") ?
					contact.getString("picture") : null;
			final String birthDay = !contact.isNull("birthday_date") ?
					contact.getString("birthday_date") : null;
			String statusMessage = null;
			long statusTimestamp = 0;
			if (!contact.isNull("status")) {
				try {
					JSONObject status = contact.getJSONObject("status");
					statusMessage = status.getString("message");
					statusTimestamp = status.getLong("time");
				} catch (Exception e) {
					
				}
			}
			final long syncState = !contact.isNull("x") ? contact.getLong("x") : 0;
			return new RawContact(0, uid, email, firstName, lastName, birthDay, statusMessage, statusTimestamp, avatarUrl, syncState);
		} catch (final Exception ex) {
			Log.i(TAG, "Error parsing JSON contact object" + ex.toString());
		}
		return null;
	}
	
	public static RawContact create(String uid, String email, String firstName, String lastName, String birthDay) {
		return new RawContact(0, uid, email, firstName, lastName, birthDay, null, 0, null, -1);
	}
	public static RawContact create(long rawContactId, String uid) {
		return new RawContact(rawContactId, uid, null, null, null, null, null, 0, null, -1);
	}
}
