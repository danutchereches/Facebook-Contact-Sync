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
import com.facebook.FacebookSdk;
import com.facebook.GraphRequest;
import com.facebook.GraphResponse;
import com.facebook.HttpMethod;
import com.facebook.LoggingBehavior;
import com.facebook.GraphResponse.PagingDirection;
import com.facebook.internal.Utility;

import android.accounts.Account;
import android.accounts.NetworkErrorException;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

/**
 * Provides utility methods for communicating with the server.
 */
final public class NetworkUtilities {
	private String mAccessToken;
	
	public NetworkUtilities(String token, Context context) {
		if (Looper.myLooper() == null) {
			Looper.prepare();
		}
		
		FacebookSdk.sdkInitialize(context);
		mAccessToken = token;
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
		
		try {
			Bundle parameters = new Bundle();
			parameters.putString("access_token", mAccessToken);
			parameters.putString("fields", "permission,status");
			GraphRequest graphRequest = new GraphRequest(null, "me/permissions", parameters, HttpMethod.GET, null);
			GraphResponse response = graphRequest.executeAndWait();
			
			if (response.getError() != null) {
				if (response.getError().getErrorCode() == 190) {
					return false;
				} else {
					throw new NetworkErrorException(response.getError().getErrorMessage());
				}
			}
			
			JSONObject json = response.getJSONObject();
			JSONArray permissions = json.getJSONArray("data");
			List<String> aquiredPermissions = new ArrayList<String>();
			
			JSONObject permission;
			for (int i = 0; i < permissions.length(); i++) {
				permission = permissions.getJSONObject(i);
				if (permission != null && permission.getString("status").equals("granted")) {
					aquiredPermissions.add(permission.getString("permission"));
				}
			}
			
			for (int i = 0; i < Authenticator.REQUIRED_PERMISSIONS.length; i++) {
				if (!aquiredPermissions.contains(Authenticator.REQUIRED_PERMISSIONS[i])) {
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
		
		Bundle parameters = new Bundle();
		parameters.putString("access_token", mAccessToken);
		parameters.putString("fields", "id,first_name,last_name,picture");
		GraphRequest friendlistRequest = new GraphRequest(null, "me/friends", parameters, HttpMethod.GET, null);
		GraphResponse response;
		
		do {
			response = friendlistRequest.executeAndWait();
			friendlistRequest = null;
			
			if (response.getError() != null) {
				if (response.getError().getErrorCode() == 190) {
					throw new AuthenticationException();
				} else {
					throw new ParseException(response.getError().getErrorMessage());
				}
			}
			
			JSONObject json = response.getJSONObject();
			JSONArray friends = json.getJSONArray("data");
			
			if (friends != null && friends.length() > 0)
			{
				JSONObject contact;
				for (int i = 0; i < friends.length(); i++) {
					contact = friends.getJSONObject(i);
					if (contact.has("picture"))
						contact.put("picture", contact.getJSONObject("picture").getJSONObject("data").getString("url"));
					RawContact rawContact = RawContact.valueOf(contact);
					
					if (rawContact != null) {
						serverList.add(rawContact);
					}
				}
				friendlistRequest = response.getRequestForPagedResults(PagingDirection.NEXT);
				friendlistRequest.setParameters(parameters);
			}
		} while (friendlistRequest != null);
		
		return serverList;
	}

	private String getDefaultLocale() {
		return Locale.getDefault().toString();
	}

	public ContactPhoto getContactPhotoHD(RawContact contact, int width, int height)
			throws IOException, AuthenticationException, JSONException {
		
		Bundle params = new Bundle();
		ContactsSync app = ContactsSync.getInstance();
		params.putString("access_token", mAccessToken);
		params.putString("fields", "url");
		params.putInt("width", width);
		params.putInt("height", height);
		params.putBoolean("redirect", false);
		params.putInt("timeout", app.getConnectionTimeout() * 1000);
		GraphRequest request = new GraphRequest(null, contact.getUid() + "/picture", params, HttpMethod.GET, null);
		GraphResponse response = request.executeAndWait();
		
		if (response == null) {
			throw new IOException();
		}
		if (response.getJSONObject() == null) {
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
		
		//Log.d("FacebookGetPhoto", "response: " + response.getJSONObject().toString());
		String image = response.getJSONObject().getJSONObject("data").getString("url");
		
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
				
				//TODO: remove all sized as they they are not used
				if (app.getPictureSize() == RawContact.IMAGE_SIZES.SQUARE
				 || app.getPictureSize() == RawContact.IMAGE_SIZES.BIG_SQUARE
				 || app.getPictureSize() == RawContact.IMAGE_SIZES.HUGE_SQUARE
				 || app.getPictureSize() == RawContact.IMAGE_SIZES.MAX_SQUARE) {
					int cropWidth = Math.min(originalImage.getWidth(), originalImage.getHeight());
					int cropHeight = cropWidth;
					int offsetX = Math.round((originalImage.getWidth() - cropWidth) / 2);
					int offsetY = Math.round((originalImage.getHeight() - cropHeight) / 2);
					
					Log.v("pic_size", "w:"+cropWidth + ", h:"+cropHeight);
					
					Bitmap croppedImage = Bitmap.createBitmap(originalImage, offsetX, offsetY, cropWidth, cropHeight);
					
					convertStream = new ByteArrayOutputStream(cropWidth * cropHeight * 4);
					croppedImage.compress(Bitmap.CompressFormat.JPEG, 95, convertStream);
					croppedImage.recycle();
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
