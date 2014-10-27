package uk.co.benjaminelliott.spectrogramandroid.activities;

import uk.co.benjaminelliott.spectrogramandroid.R;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.util.Log;

/**
 * Conventional settings Activity that allows the user to change their preferences.
 * @author Ben
 *
 */
public class SettingsActivity extends PreferenceActivity {

	private SettingsFragment settingsFrag; 

	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		SharedPreferences prefs = PreferenceManager
				.getDefaultSharedPreferences(this);
		// create a new settings fragment to present to the user
		settingsFrag = new SettingsFragment();
		settingsFrag.setActivity(this);
		// register the fragment as a listener for changes to the user's preferences
		prefs.registerOnSharedPreferenceChangeListener(settingsFrag);
		getFragmentManager().beginTransaction()
			.replace(android.R.id.content, settingsFrag).commit();
	}

	/**
	 * The SettingsActivity houses a single SettingsFragment which itself extends 
	 * PreferenceFragment. The SettingsFragment listens for changes to the user's
	 * preferences, and when a preference is changed it is reflected in the Intent
	 * that is passed back to the main activity once this activity has been left.
	 * @author Ben
	 *
	 */
	public static class SettingsFragment extends PreferenceFragment implements
	OnSharedPreferenceChangeListener {
		private Activity activity;

		@Override
		public void onCreate(final Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);
			// initialise the settings UI from XML:
			addPreferencesFromResource(R.xml.preferences);
		}

		@Override
		public void onSharedPreferenceChanged(
				SharedPreferences sharedPreferences, String key) {
			// create an Intent:
			Intent returnData = new Intent();
			// add the preference change to the intent:
			returnData.putExtra("PREF_KEY", key);
			// set the activity to return the Intent when it is exited:
			activity.setResult(RESULT_OK, returnData);
		}

		private void setActivity(Activity activity) {
			this.activity = activity;
		}
	}
}