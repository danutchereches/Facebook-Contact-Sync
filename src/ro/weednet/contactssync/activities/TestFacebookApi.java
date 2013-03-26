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
package ro.weednet.contactssync.activities;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

import ro.weednet.ContactsSync;
import ro.weednet.contactssync.Constants;
import ro.weednet.contactssync.R;
import ro.weednet.contactssync.authenticator.AuthenticatorActivity;
import ro.weednet.contactssync.client.NetworkUtilities;
import ro.weednet.contactssync.client.RawContact;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.ProgressBar;

public class TestFacebookApi extends Activity {
	private AsyncTask<View, Void, Pair<Pair<Boolean, String>, Long>> mBackgroundTask;
	
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.test_fb_api);
		
		Button start_btn = (Button)findViewById(R.id.start_test_btn);
		start_btn.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				TextView message = (TextView) findViewById(R.id.test_result);
				message.setVisibility(View.GONE);
				Button btn = (Button) findViewById(R.id.action_btn);
				btn.setVisibility(View.GONE);
				
				ListView list = (ListView) findViewById(R.id.test_list);
				BaseAdapter adapter = new ListAdapter(1);
				list.setAdapter(adapter);
			}
		});
	}
	
	@Override
	public void onStart() {
		super.onStart();
		
		ListView list = (ListView) findViewById(R.id.test_list);
		list.setAdapter(null);
		
		TextView message = (TextView) findViewById(R.id.test_result);
		message.setVisibility(View.GONE);
		Button btn = (Button) findViewById(R.id.action_btn);
		btn.setVisibility(View.GONE);
	}
	
	@Override
	public void onPause() {
		super.onPause();
		
		if (mBackgroundTask != null) {
			mBackgroundTask.cancel(true);
		}
	}
	
	public class ListAdapter extends BaseAdapter {
		private ArrayList<Boolean> mTestStarted = new ArrayList<Boolean>(); 
		
		public ListAdapter(int n) {
			for (int i = 0; i < n; i++) {
				mTestStarted.add(false);
			}
		}
		
		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			if (convertView == null) {
				convertView = getLayoutInflater().inflate(R.layout.test_row, null);
			}
			
			switch (position) {
				case 0:
					((TextView) convertView.findViewById(R.id.row_title)).setText(getString(R.string.test_auth_key));
					if (!mTestStarted.get(position)) {
						if (mBackgroundTask != null) {
							mBackgroundTask.cancel(true);
						}
						
						mTestStarted.set(position, true);
						ProgressBar pb = ((ProgressBar) convertView.findViewById(R.id.row_progress_bar));
						pb.setIndeterminate(true);
						
						mBackgroundTask = new AsyncTask<View, Void, Pair<Pair<Boolean, String>, Long>>() {
							private View view;
							
							@Override
							protected Pair<Pair<Boolean, String>, Long> doInBackground(View... params) {
								view = params[0];
								try {
									long start_time = System.currentTimeMillis();
									AccountManager am = AccountManager.get(TestFacebookApi.this);
									Account account = ContactsSync.getInstance().getAccount();
									String authToken = am.blockingGetAuthToken(account, Constants.AUTHTOKEN_TYPE, true);
									Log.v("TestFB", "token: " + authToken);
									NetworkUtilities nu = new NetworkUtilities(authToken, TestFacebookApi.this);
									if (nu.checkAccessToken()) {
										return new Pair<Pair<Boolean, String>, Long>(new Pair<Boolean, String>(true, "OK"), System.currentTimeMillis() - start_time);
									} else {
										if (authToken != null) {
											am.invalidateAuthToken(Constants.AUTHTOKEN_TYPE, authToken);
										}
										return new Pair<Pair<Boolean, String>, Long>(new Pair<Boolean, String>(false, "Invalid auth token"), 0L);
									}
								} catch (Exception e) {
									return new Pair<Pair<Boolean, String>, Long>(new Pair<Boolean, String>(false, "Exception: " + e.getMessage()), 0L);
								}
							}
							
							@Override
							protected void onPostExecute(Pair<Pair<Boolean, String>, Long> result) {
								ViewGroup v = (ViewGroup) view.findViewById(R.id.status);
								v.removeAllViews();
								TextView status = new TextView(view.getContext());
								if (result.first.first) {
									DecimalFormat twoDForm = new DecimalFormat("#.##");
									status.setText("OK\n" + twoDForm.format((double)result.second / 1000) + "s");
									mTestStarted.add(false);
									notifyDataSetChanged();
								} else {
									status.setText("ERROR");
									TextView message = (TextView) TestFacebookApi.this.findViewById(R.id.test_result);
									message.setText(result.first.second);
									message.setTextColor(Color.RED);
									message.setVisibility(View.VISIBLE);
									if (result.first.second.contains("token")) {
										Button btn = (Button) findViewById(R.id.action_btn);
										btn.setVisibility(View.VISIBLE);
										btn.setText("Request new auth token");
										btn.setOnClickListener(new OnClickListener() {
											@Override
											public void onClick(View v) {
												Intent intent = new Intent(TestFacebookApi.this, AuthenticatorActivity.class);
												try {
													Account account = ContactsSync.getInstance().getAccount();
													intent.putExtra(AuthenticatorActivity.PARAM_USERNAME, account.name);
												} catch (Exception e) { }
												startActivity(intent);
											//	finish();
											}
										});
									}
								}
								v.addView(status);
							}
						}.execute(convertView);
					}
					break;
				case 1:
					((TextView) convertView.findViewById(R.id.row_title)).setText(getString(R.string.test_get_friends));
					if (!mTestStarted.get(position)) {
						if (mBackgroundTask != null) {
							mBackgroundTask.cancel(true);
						}
						
						mTestStarted.set(position, true);
						ProgressBar pb = ((ProgressBar) convertView.findViewById(R.id.row_progress_bar));
						pb.setIndeterminate(true);
						
						mBackgroundTask = new AsyncTask<View, Void, Pair<Pair<Boolean, String>, Long>>() {
							private View view;
							
							@Override
							protected Pair<Pair<Boolean, String>, Long> doInBackground(View... params) {
								view = params[0];
								try {
									long start_time = System.currentTimeMillis();
									AccountManager am = AccountManager.get(TestFacebookApi.this);
									Account account = ContactsSync.getInstance().getAccount();
									String authToken = am.blockingGetAuthToken(account, Constants.AUTHTOKEN_TYPE, true);
									NetworkUtilities nu = new NetworkUtilities(authToken, TestFacebookApi.this);
									List<RawContact> contacts = nu.getContacts(account);
									if (contacts != null) {
										return new Pair<Pair<Boolean, String>, Long>(new Pair<Boolean, String>(true, "Found " + contacts.size() + " friends"), System.currentTimeMillis() - start_time);
									} else {
										return new Pair<Pair<Boolean, String>, Long>(new Pair<Boolean, String>(false, "Invalid response from facebook"), 0L);
									}
								} catch (Exception e) {
									return new Pair<Pair<Boolean, String>, Long>(new Pair<Boolean, String>(false, e.getMessage()), 0L);
								}
							}
							
							@Override
							protected void onPostExecute(Pair<Pair<Boolean, String>, Long> result) {
								ViewGroup v = (ViewGroup) view.findViewById(R.id.status);
								v.removeAllViews();
								TextView status = new TextView(view.getContext());
								if (result.first.first) {
									DecimalFormat twoDForm = new DecimalFormat("#.##");
									status.setText("OK\n" + twoDForm.format((double)result.second / 1000) + "s");
									TextView message = (TextView) TestFacebookApi.this.findViewById(R.id.test_result);
									message.setText(result.first.second);
									message.setTextColor(Color.GREEN);
									message.setVisibility(View.VISIBLE);
								} else {
									status.setText("ERROR");
									TextView message = (TextView) TestFacebookApi.this.findViewById(R.id.test_result);
									message.setText(result.first.second);
									message.setTextColor(Color.RED);
									message.setVisibility(View.VISIBLE);
								}
								v.addView(status);
							}
						}.execute(convertView);
					}
					break;
			}
			
			return convertView;
		}
		
		@Override
		public long getItemId(int position) {
			return position;
		}
		
		@Override
		public Object getItem(int position) {
			return position;
		}
		
		@Override
		public int getCount() {
			return mTestStarted.size();
		}
	}
}
