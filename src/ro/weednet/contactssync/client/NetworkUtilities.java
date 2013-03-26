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

import org.apache.http.ParseException;
import org.apache.http.auth.AuthenticationException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import ro.weednet.ContactsSync;
import ro.weednet.contactssync.authenticator.Authenticator;

import com.facebook.AccessToken;
import com.facebook.FacebookException;
import com.facebook.HttpMethod;
import com.facebook.Request;
import com.facebook.Response;
import com.facebook.Session;
import com.facebook.Session.StatusCallback;
import com.facebook.SessionState;

import android.accounts.Account;
import android.accounts.NetworkErrorException;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

/**
 * Provides utility methods for communicating with the server.
 */
final public class NetworkUtilities {
	private Session mSession;
	
	public NetworkUtilities(String token, Context context) {
		AccessToken accessToken = AccessToken.createFromExistingAccessToken(token, null, null, null, null);
		mSession = Session.getActiveSession();
		if (mSession == null) {
			mSession = Session.openActiveSessionWithAccessToken(context, accessToken,
				new StatusCallback() {
				
				@Override
				public void call(Session session, SessionState state, Exception exception) {
					
				}
			});
		}
	}
	
	/**
	 * Connects to the Sync test server, authenticates the provided
	 * username and password.
	 * 
	 * @param username
	 *            The server account username
	 * @param password
	 *            The server account password
	 * @return String The authentication token returned by the server (or null)
	 * @throws NetworkErrorException 
	 */
	public boolean checkAccessToken() throws NetworkErrorException {
		//TODO: try to re-use timeout values (or remove preferences options
		//	params.putInt("timeout", ContactsSync.getInstance().getConnectionTimeout() * 1000);
		
		if (!mSession.isOpened()) {
			return false;
		}
		
		try {
			Request request = new Request(mSession, "me/permissions");
			Response response = request.executeAndWait();
			
			if (response.getError() != null) {
				if (response.getError().getErrorCode() == 190) {
					return false;
				} else {
					throw new NetworkErrorException(response.getError().getErrorMessage());
				}
			}
			
			JSONObject json = response.getGraphObject().getInnerJSONObject();
			JSONObject permissions = json.getJSONArray("data").getJSONObject(0);
			
			for (int i = 0; i < Authenticator.REQUIRED_PERMISSIONS.length; i++) {
				if (permissions.isNull(Authenticator.REQUIRED_PERMISSIONS[i])
				 || permissions.getInt(Authenticator.REQUIRED_PERMISSIONS[i]) == 0) {
					return false;
				}
			}
		} catch (FacebookException e) {
			throw new NetworkErrorException(e.getMessage());
		} catch (JSONException e) {
			throw new NetworkErrorException(e.getMessage());
		}
		
		return true;
	}
	
	@SuppressLint("SimpleDateFormat")
	public List<RawContact> getContacts(Account account)
			throws JSONException, ParseException, IOException, AuthenticationException {
		
		final ArrayList<RawContact> serverList = new ArrayList<RawContact>();
		ContactsSync app = ContactsSync.getInstance();
		int pictureSize = app.getPictureSize();
		String pic_size = null;
		boolean album_picture = false;
		
		if (app.getSyncType() == ContactsSync.SyncType.LEGACY) {
			switch (pictureSize) {
				case RawContact.IMAGE_SIZES.SMALL_SQUARE:
					pic_size = "pic_square";
					break;
				case RawContact.IMAGE_SIZES.SMALL:
					pic_size = "pic_small";
					break;
				case RawContact.IMAGE_SIZES.NORMAL:
					pic_size = "pic";
					break;
				case RawContact.IMAGE_SIZES.SQUARE:
				case RawContact.IMAGE_SIZES.BIG_SQUARE:
				case RawContact.IMAGE_SIZES.HUGE_SQUARE:
				case RawContact.IMAGE_SIZES.MAX:
				case RawContact.IMAGE_SIZES.MAX_SQUARE:
					album_picture = true;
				case RawContact.IMAGE_SIZES.BIG:
					pic_size = "pic_big";
					break;
			}
		} else {
			pic_size = "pic";
			album_picture = false;
		}
		
		String fields = "uid, username, first_name, last_name, " + pic_size;
		
		if (app.getSyncStatuses() && app.getSyncType() == ContactsSync.SyncType.LEGACY) {
			fields += ", status";
		}
		if (app.getSyncBirthdays()) {
			fields += ", birthday_date";
		}
		
		boolean more = true;
		int limit;
		int offset = 0;
		while (more) {
			more = false;
			Bundle params = new Bundle();
			
			if (album_picture) {
				limit = 20;
				String query1 = "SELECT " + fields + " FROM user WHERE uid IN (SELECT uid2 FROM friend WHERE uid1 = me()) LIMIT " + limit + " OFFSET " + offset;
				String query2 = "SELECT owner, src_big, modified FROM photo WHERE pid IN (SELECT cover_pid FROM album WHERE owner IN (SELECT uid FROM #query1) AND type = 'profile')";
				params.putString("method", "fql.multiquery");
				params.putString("queries", "{\"query1\":\"" + query1 + "\", \"query2\":\"" + query2 + "\"}");
			} else {
				limit = 1000;
				String query = "SELECT " + fields + " FROM user WHERE uid IN (SELECT uid2 FROM friend WHERE uid1 = me()) LIMIT " + limit + " OFFSET " + offset;
				params.putString("method", "fql.query");
				params.putString("query", query);
			}
			
			params.putInt("timeout", app.getConnectionTimeout() * 1000);
			Request request = Request.newRestRequest(mSession, "fql.query", params, HttpMethod.GET);
			Response response = request.executeAndWait();
			
			if (response == null) {
				throw new IOException();
			}
			if (response.getGraphObjectList() == null) {
				if (response.getError() != null) {
					if (response.getError().getErrorCode() == 190) {
						throw new AuthenticationException();
					} else {
						throw new ParseException(response.getError().getErrorMessage());
					}
				} else {
					throw new ParseException();
				}
			}
			
			try {
				JSONArray serverContacts;
				HashMap<String, JSONObject> serverImages = new HashMap<String, JSONObject>();
				if (album_picture) {
					JSONArray result = response.getGraphObjectList().getInnerJSONArray();
					serverContacts = result.getJSONObject(0).getJSONArray("fql_result_set");
					JSONArray images = result.getJSONObject(1).getJSONArray("fql_result_set");
					JSONObject image;
					for (int j = 0; j < images.length(); j++) {
						image = images.getJSONObject(j);
						serverImages.put(image.getString("owner"), image);
					}
				} else {
					serverContacts = response.getGraphObjectList().getInnerJSONArray();
				}
				
				JSONObject contact;
				for (int i = 0; i < serverContacts.length(); i++) {
					contact = serverContacts.getJSONObject(i);
					contact.put("picture", !contact.isNull(pic_size) ? contact.getString(pic_size) : null);
					if (album_picture && serverImages.containsKey(contact.getString("uid"))) {
						contact.put("picture", serverImages.get(contact.getString("uid")).getString("src_big"));
					}
					if (contact.has("birthday_date") && contact.getString("birthday_date") != null
					 && app.getSyncBirthdays() && app.getBirthdayFormat() != RawContact.BIRTHDAY_FORMATS.DEFAULT) {
						try {
							DateFormat formatter = new SimpleDateFormat("MM/dd/yyyy");
							Date date = formatter.parse(contact.getString("birthday_date"));
							switch(ContactsSync.getInstance().getBirthdayFormat()) {
								case RawContact.BIRTHDAY_FORMATS.GLOBAL:
									contact.put("birthday_date",new SimpleDateFormat("yyyy-MM-dd").format(date));
									break;
								case RawContact.BIRTHDAY_FORMATS.US:
									//already there
									break;
								case RawContact.BIRTHDAY_FORMATS.EU:
									contact.put("birthday_date",new SimpleDateFormat("dd/MM/yyyy").format(date));
									break;
							}
						} catch (java.text.ParseException e) {
							contact.remove("birthday_date");
						}
					}
					RawContact rawContact = RawContact.valueOf(contact);
					if (rawContact != null) {
						serverList.add(rawContact);
					}
				}
				
				if (serverContacts.length() > limit / 2) {
					offset += limit;
					more = true;
				}
			} catch (FacebookException e) {
				throw new ParseException(e.getMessage());
			} catch (JSONException e) {
				throw new ParseException(e.getMessage());
			}
		}
		
		return serverList;
	}
	
	public ContactPhoto getContactPhotoHD(RawContact contact, int width, int height)
			throws IOException, AuthenticationException, JSONException {
		
		Bundle params = new Bundle();
		ContactsSync app = ContactsSync.getInstance();
		params.putInt("width", width);
		params.putInt("height", height);
		params.putBoolean("redirect", false);
		params.putInt("timeout", app.getConnectionTimeout() * 1000);
		Request request = new Request(mSession, contact.getUid() + "/picture", params, HttpMethod.GET);
		Response response = request.executeAndWait();
		
		if (response == null) {
			throw new IOException();
		}
		if (response.getGraphObject() == null) {
			if (response.getError() != null) {
				if (response.getError().getErrorCode() == 190) {
					throw new AuthenticationException();
				} else {
					throw new ParseException(response.getError().getErrorMessage());
				}
			} else {
				throw new ParseException();
			}
		}
		
		Log.e("FacebookGetPhoto", "response: " + response.getGraphObject().getInnerJSONObject().toString());
		String image = response.getGraphObject().getInnerJSONObject().getJSONObject("data").getString("url");
		
		return new ContactPhoto(contact, image, 0);
	}
	
	/**
	 * Download the avatar image from the server.
	 * 
	 * @param avatarUrl
	 *            the URL pointing to the avatar image
	 * @return a byte array with the raw JPEG avatar image
	 */
	public static byte[] downloadAvatar(final String avatarUrl) {
		// If there is no avatar, we're done
		if (TextUtils.isEmpty(avatarUrl)) {
			return null;
		}
		
		try {
			ContactsSync app = ContactsSync.getInstance();
			URL url = new URL(avatarUrl);
			HttpURLConnection connection = (HttpURLConnection) url.openConnection();
			connection.connect();
			try {
				BitmapFactory.Options options = new BitmapFactory.Options();
				Bitmap originalImage = BitmapFactory.decodeStream(connection.getInputStream(), null, options);
				ByteArrayOutputStream convertStream;
				
				if (app.getPictureSize() == RawContact.IMAGE_SIZES.SQUARE
				 || app.getPictureSize() == RawContact.IMAGE_SIZES.BIG_SQUARE
				 || app.getPictureSize() == RawContact.IMAGE_SIZES.HUGE_SQUARE
				 || app.getPictureSize() == RawContact.IMAGE_SIZES.MAX_SQUARE) {
					int targetWidth, targetHeight;
					switch(app.getPictureSize()) {
						case RawContact.IMAGE_SIZES.MAX_SQUARE:
							targetWidth  = app.getMaxPhotoSize();
							targetHeight = app.getMaxPhotoSize();
							break;
						case RawContact.IMAGE_SIZES.HUGE_SQUARE:
							targetWidth  = Math.min(720, app.getMaxPhotoSize());
							targetHeight = Math.min(720, app.getMaxPhotoSize());
							break;
						case RawContact.IMAGE_SIZES.BIG_SQUARE:
							targetWidth  = Math.min(512, app.getMaxPhotoSize());
							targetHeight = Math.min(512, app.getMaxPhotoSize());
							break;
						case RawContact.IMAGE_SIZES.SQUARE:
						default:
							targetWidth  = Math.min(256, app.getMaxPhotoSize());
							targetHeight = Math.min(256, app.getMaxPhotoSize());
					}
					
					int cropWidth = Math.min(originalImage.getWidth(), originalImage.getHeight());
					int cropHeight = cropWidth;
					int offsetX = Math.round((originalImage.getWidth() - cropWidth) / 2);
					int offsetY = Math.round((originalImage.getHeight() - cropHeight) / 2);
					
					Log.v("pic_size", "w:"+cropWidth + ", h:"+cropHeight);
					
					Bitmap croppedImage = Bitmap.createBitmap(originalImage, offsetX, offsetY, cropWidth, cropHeight);
					Bitmap resizedBitmap = Bitmap.createScaledBitmap(croppedImage, targetWidth, targetHeight, true);
					
					convertStream = new ByteArrayOutputStream(targetWidth * targetHeight * 4);
					resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 95, convertStream);
					
					croppedImage.recycle();
					resizedBitmap.recycle();
				} else {
					Log.v("pic_size", "original: w:"+originalImage.getWidth() + ", h:"+originalImage.getHeight());
					convertStream = new ByteArrayOutputStream(originalImage.getWidth() * originalImage.getHeight() * 4);
					originalImage.compress(Bitmap.CompressFormat.JPEG, 95, convertStream);
				}
				
				convertStream.flush();
				convertStream.close();
				originalImage.recycle();
				return convertStream.toByteArray();
			} finally {
				connection.disconnect();
			}
		} catch (MalformedURLException muex) {
			// A bad URL - nothing we can really do about it here...
			Log.e("network_utils", "Malformed avatar URL: " + avatarUrl);
		} catch (IOException ioex) {
			// If we're unable to download the avatar, it's a bummer but not the
			// end of the world. We'll try to get it next time we sync.
			Log.e("network_utils", "Failed to download user avatar: " + avatarUrl);
		} catch (NullPointerException npe) {
			// probably `avatar` is null
			Log.e("network_utils", "Failed to download user avatar: " + avatarUrl);
		}
		return null;
	}
}
