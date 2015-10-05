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

import org.json.JSONException;
import org.json.JSONObject;

import com.facebook.AccessToken;
import com.facebook.CallbackManager;
import com.facebook.FacebookCallback;
import com.facebook.FacebookException;
import com.facebook.FacebookSdk;
import com.facebook.GraphRequest;
import com.facebook.GraphResponse;
import com.facebook.HttpMethod;
import com.facebook.login.LoginManager;
import com.facebook.login.LoginResult;

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
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

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
	protected TextView mMessageView;
	protected Button mCloseButton;
	private SessionStatusCallback mStatusCallback = new SessionStatusCallback();
	private CallbackManager mCallbackManager;
	
	@Override
	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		
		mMessageView = new TextView(this);
		mMessageView.setPadding(15, 10, 15, 15);
		mMessageView.setGravity(Gravity.CENTER);
		
		mCloseButton = new Button(this);
		mCloseButton.setText("Close");
		mCloseButton.setPadding(15, 20, 15, 15);
		mCloseButton.setGravity(Gravity.CENTER);
		mCloseButton.setVisibility(View.GONE);
		mCloseButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				finish();
			}
		});
		
		LinearLayout layout = new LinearLayout(this);
		layout.setOrientation(LinearLayout.VERTICAL);
		layout.addView(mMessageView);
		layout.addView(mCloseButton);
		
		setContentView(layout);
		
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
		
		FacebookSdk.sdkInitialize(getApplicationContext());
		mCallbackManager = CallbackManager.Factory.create();
		
		LoginManager.getInstance().registerCallback(mCallbackManager, mStatusCallback);
		LoginManager.getInstance().logInWithReadPermissions(this, Arrays.asList(Authenticator.REQUIRED_PERMISSIONS));
		mMessageView.setText("Trying to authenticat with Facebook.\nPlease wait ..");
	}
	
	@Override
	public void onResume() {
		super.onResume();
		
	}
	
	@Override
	public void onPause() {
		super.onPause();
		
		if (mLoading != null) {
			try {
				mLoading.dismiss();
			} catch (Exception e) { }
		}
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		
	}
	
	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		
		mCallbackManager.onActivityResult(requestCode, resultCode, data);
	}
	
	public class getUserInfo implements GraphRequest.Callback {
		@Override
		public void onCompleted(GraphResponse response) {
			Log.e("response profile", response.toString());
			
			if (response.getError() == null) {
			try {
				ContactsSync app = ContactsSync.getInstance();
				app.setConnectionTimeout(Preferences.DEFAULT_CONNECTION_TIMEOUT);
				app.savePreferences();
				
				JSONObject userInfo = response.getJSONObject();
				
				final String username =
						userInfo.getString("email") != null && ((String) userInfo.getString("email")).length() > 0
						? (String) userInfo.getString("email") : AccessToken.getCurrentAccessToken().getUserId();
				final String access_token = AccessToken.getCurrentAccessToken().getToken();
				final int sync_freq = app.getSyncFrequency() * 3600;
				
				Account account;
				if (mRequestNewAccount) {
					account = new Account(username, Constants.ACCOUNT_TYPE);
					mAccountManager.addAccountExplicitly(account, access_token, null);
				} else {
					account = new Account(mUsername, Constants.ACCOUNT_TYPE);
					mAccountManager.setPassword(account, access_token);
				}
				
				if (app.getWizardShown() && sync_freq > 0) {
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
			} catch (JSONException e) {
				e.printStackTrace();
				mHandler.post(new DisplayException("API exception."));
			}
			} else {
				mHandler.post(new DisplayException("API error:\n" + response.getError().getErrorMessage()));
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
	
	private class SessionStatusCallback implements FacebookCallback<LoginResult> {
		@Override
		public void onSuccess(LoginResult result) {
			mHandler.post(new Runnable() {
				public void run() {
					mLoading.show();
				}
			});
			Bundle parameters = new Bundle();
			parameters.putString("fields", "id,name,first_name,middle_name,last_name,link,email");
		//	parameters.putString("access_token", accessToken);
			GraphRequest graphRequest = new GraphRequest(AccessToken.getCurrentAccessToken(),
					"me", parameters, HttpMethod.GET, new getUserInfo());
			graphRequest.executeAsync();
		}
		
		@Override
		public void onCancel() {
			mMessageView.setText("Facebook login canceled.");
			mCloseButton.setVisibility(View.VISIBLE);
		}
		
		@Override
		public void onError(FacebookException error) {
			mMessageView.setText("Facebook login failed:\n" + error.getMessage());
			mCloseButton.setVisibility(View.VISIBLE);
		}
	}
}
