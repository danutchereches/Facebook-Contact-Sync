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
package ro.weednet.contactssync.authenticator;

import java.util.Arrays;

import com.facebook.Request;
import com.facebook.Response;
import com.facebook.Session;
import com.facebook.SessionState;
import com.facebook.model.GraphUser;

import ro.weednet.ContactsSync;
import ro.weednet.contactssync.Constants;
import ro.weednet.contactssync.R;
import ro.weednet.contactssync.activities.Preferences;

import android.accounts.Account;
import android.accounts.AccountAuthenticatorActivity;
import android.accounts.AccountManager;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.provider.ContactsContract;
import android.util.Log;

/**
 * Activity which displays login screen to the user.
 */
public class AuthenticatorActivity extends AccountAuthenticatorActivity {
	private AccountManager mAccountManager;
	public static final String PARAM_USERNAME = "fb_email";
	public static final String PARAM_AUTHTOKEN_TYPE = "authtokenType";
	protected boolean mRequestNewAccount = false;
	private String mUsername;
	public final Handler mHandler = new Handler();
	protected ProgressDialog mLoading;
	protected AlertDialog mDialog;
	private Session.StatusCallback mStatusCallback = new SessionStatusCallback();
	private boolean isResumed;
	
	@Override
	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		
		mLoading = new ProgressDialog(this);
		mLoading.setTitle(getText(R.string.app_name));
		mLoading.setMessage("Loading ... ");
	//	mLoading.setCancelable(false);
		mLoading.setOnCancelListener(new OnCancelListener() {
			@Override
			public void onCancel(DialogInterface dialog) {
				mHandler.post(new Runnable() {
					public void run() {
						AuthenticatorActivity.this.finish();
					}
				});
			}
		});
		mAccountManager = AccountManager.get(this);
		
		final Intent intent = getIntent();
		mUsername = intent.getStringExtra(PARAM_USERNAME);
		mRequestNewAccount = mUsername == null;
		
		Session session = Session.getActiveSession();
		
		if (session != null) {
			if (session.isClosed() || session.isOpened()) {
				Log.d("fb-auth", "cannot reuse session, closing ..");
				session.closeAndClearTokenInformation();
				session = null;
			}
		}
		
		if (session == null) {
			if (session == null) {
				session = new Session(this);
			}
			
			Session.setActiveSession(session);
			
			if (Session.getActiveSession().equals(SessionState.CREATED_TOKEN_LOADED)) {
				session.openForRead(new Session.OpenRequest(this).setCallback(mStatusCallback));
			}
		}
	}
	
	@Override
	public void onResume() {
		super.onResume();
		
		isResumed = true;
	}
	
	@Override
	public void onPause() {
		super.onPause();
		
		if (mLoading != null) {
			try {
				mLoading.dismiss();
			} catch (Exception e) { }
		}
		
		isResumed = false;
	}
	
	@Override
	public void onStart() {
		super.onStart();
		Session session = Session.getActiveSession();
		
		session.addCallback(mStatusCallback);
		
		if (!session.isOpened() && !session.isClosed()) {
			Log.d("fb-auth", "session openging");
			Session.OpenRequest or = new Session.OpenRequest(this);
			or.setPermissions(Arrays.asList(Authenticator.REQUIRED_PERMISSIONS));
			session.openForRead(or);
		} else {
			Log.d("fb-auth", "session else");
		}
	}
	
	@Override
	public void onStop() {
		super.onStop();
		
		Session.getActiveSession().removeCallback(mStatusCallback);
	}
	
	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		Session.getActiveSession().onActivityResult(this, requestCode, resultCode, data);
	}
	
	public class getUserInfo implements Request.GraphUserCallback {
		@Override
		public void onCompleted(GraphUser user, Response response) {
			if (user != null) {
				ContactsSync app = ContactsSync.getInstance();
				app.setConnectionTimeout(Preferences.DEFAULT_CONNECTION_TIMEOUT);
				app.savePreferences();
				
				final String username =
					user.getUsername() != null && user.getUsername().length() > 0
					? user.getUsername() : user.getId();
				final String access_token = Session.getActiveSession().getAccessToken();
				final int sync_freq = app.getSyncFrequency() * 3600;
				
				Account account;
				if (mRequestNewAccount) {
					account = new Account(username, Constants.ACCOUNT_TYPE);
					mAccountManager.addAccountExplicitly(account, access_token, null);
				} else {
					account = new Account(mUsername, Constants.ACCOUNT_TYPE);
					mAccountManager.setPassword(account, access_token);
				}
				
				if (sync_freq > 0) {
					ContentResolver.setSyncAutomatically(account, ContactsContract.AUTHORITY, true);
					
					Bundle extras = new Bundle();
					ContentResolver.addPeriodicSync(account, ContactsContract.AUTHORITY, extras, sync_freq);
				} else {
					ContentResolver.setSyncAutomatically(account, ContactsContract.AUTHORITY, false);
				}
				
				mHandler.post(new Runnable() {
					public void run() {
						if (mLoading != null) {
							try {
								mLoading.dismiss();
							} catch (Exception e) { }
						}
						final Intent intent = new Intent();
						intent.putExtra(AccountManager.KEY_ACCOUNT_NAME, username);
						intent.putExtra(AccountManager.KEY_ACCOUNT_TYPE, Constants.ACCOUNT_TYPE);
						intent.putExtra(AccountManager.KEY_AUTHTOKEN, access_token);
						setAccountAuthenticatorResult(intent.getExtras());
						setResult(RESULT_OK, intent);
						finish();
					}
				});
			} else {
				if (response.getError() != null) {
					mHandler.post(new DisplayException(response.getError().getErrorMessage()));
				} else {
					mHandler.post(new DisplayException("Unknown error."));
				}
			}
		}
	}
	
	protected class DisplayException implements Runnable {
		String mMessage;
		
		public DisplayException(String msg) {
			mMessage = msg;
		}
		
		public void run() {
			AlertDialog.Builder builder = new AlertDialog.Builder(AuthenticatorActivity.this);
			builder.setTitle("Facebook Error");
			builder.setMessage(mMessage);
			builder.setOnCancelListener(new OnCancelListener() {
				@Override
				public void onCancel(DialogInterface dialog) {
					mDialog.dismiss();
					mLoading.dismiss();
					AuthenticatorActivity.this.finish();
				}
			});
			try {
				mDialog = builder.create();
				mDialog.show();
			} catch (Exception e) { }
		}
	}
	
	private class SessionStatusCallback implements Session.StatusCallback {
		@Override
		public void call(Session session, SessionState state, Exception exception) {
			switch (state) {
				case CREATED:
					Session.openActiveSession(AuthenticatorActivity.this, true, mStatusCallback);
					break;
				case OPENED:
					mHandler.post(new Runnable() {
						public void run() {
							mLoading.show();
						}
					});
					Request.executeMeRequestAsync(session, new getUserInfo());
					break;
				case CLOSED_LOGIN_FAILED:
					if (isResumed) {
						finish();
					}
					break;
				default:
			}
		}
	}
}
