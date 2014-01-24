package bge23.spectrogramandroid;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.util.Log;

public class SettingsActivity extends PreferenceActivity {
	
	private SettingsFragment settingsFrag;
	private static Context context;
	
    @Override
    protected void onCreate(final Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        context = this;
        settingsFrag = new SettingsFragment();
        settingsFrag.setActivity(this);
        prefs.registerOnSharedPreferenceChangeListener(settingsFrag);
        getFragmentManager().beginTransaction().replace(android.R.id.content, settingsFrag).commit();
    }

    public static class SettingsFragment extends PreferenceFragment implements OnSharedPreferenceChangeListener
    {
    	private Activity activity;
    	
        @Override
        public void onCreate(final Bundle savedInstanceState)
        {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.preferences);
        }

		@Override
		public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
			Intent returnData = new Intent();
			returnData.putExtra("PREF_KEY", key);
			activity.setResult(RESULT_OK, returnData);
		}
		
		private void setActivity(Activity activity) {
			this.activity = activity;
		}
    }
}