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

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

public class Profile extends Activity {
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		Intent intent = getIntent();
		if (intent.getData() != null) {
			@SuppressWarnings("deprecation")
			Cursor cursor = managedQuery(intent.getData(), null, null, null, null);
			if (cursor.moveToNext()) {
				String userId = cursor.getString(cursor.getColumnIndex("DATA1"));
				try {
					intent = new Intent(Intent.ACTION_VIEW, Uri.parse("fb://profile/" + userId));
					startActivity(intent);
				} catch(Exception e) {
					intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.facebook.com/" + userId));
					startActivity(intent);
				}
			}
		}
		finish();
	}
}
