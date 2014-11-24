package uk.co.benjaminelliott.spectrogramandroid.activities;

import java.util.Locale;

import uk.co.benjaminelliott.spectrogramandroid.R;
import uk.co.benjaminelliott.spectrogramandroid.ui.LibraryFragment;
import uk.co.benjaminelliott.spectrogramandroid.ui.SpectroFragment;
import android.app.ActionBar;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.Menu;
import android.view.MenuItem;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesClient;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.location.LocationClient;

/**
 * The main Activity that is presented to the user when they start the application.
 * This houses two fragments: a {@link LibraryFragment} which gives the user access
 * to their previous captures, and a {@link SpectroFragment} which allows them to 
 * make new captures.
 * @author Ben
 *
 */
public class SpectroActivity extends FragmentActivity implements
ActionBar.TabListener, GooglePlayServicesClient.ConnectionCallbacks,
GooglePlayServicesClient.OnConnectionFailedListener {

	/**
	 * The {@link android.support.v4.view.PagerAdapter} that will provide
	 * fragments for each of the sections. We use a
	 * {@link android.support.v4.app.FragmentPagerAdapter} derivative, which
	 * will keep every loaded fragment in memory. If this becomes too memory
	 * intensive, it may be best to switch to a
	 * {@link android.support.v4.app.FragmentStatePagerAdapter}.
	 */
	SectionsPagerAdapter mSectionsPagerAdapter;

	/**
	 * The {@link ViewPager} that will host the section contents.
	 */
	ViewPager mViewPager;
	private final int CONNECTION_FAILURE_RESOLUTION_REQUEST = 9000;
	private final int PREF_REQUEST_CODE = 600;
	private final String PREF_LANDSCAPE_KEY = "pref_landscape";
	private LocationClient lc;
	private SpectroFragment spectroFragment;
	private LibraryFragment library;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		SharedPreferences prefs = PreferenceManager
				.getDefaultSharedPreferences(this);
		boolean landscape = prefs.getBoolean(PREF_LANDSCAPE_KEY, false);
		if (landscape)
			setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
		else
			setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);

		setContentView(R.layout.activity_spectro);

		// Set up the action bar.
		final ActionBar actionBar = getActionBar();
		actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
		// Create the adapter that will return a fragment for each of the three
		// primary sections of the app.
		mSectionsPagerAdapter = new SectionsPagerAdapter(
				getSupportFragmentManager());

		// Set up the ViewPager with the sections adapter.
		mViewPager = (ViewPager) findViewById(R.id.pager);
		mViewPager.setAdapter(mSectionsPagerAdapter);

		// When swiping between different sections, select the corresponding
		// tab. We can also use ActionBar.Tab#select() to do this if we have
		// a reference to the Tab.
		mViewPager
		.setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
			@Override
			public void onPageSelected(int position) {
				if (position != 1 && spectroFragment != null)
					spectroFragment.pauseScrolling();
				actionBar.setSelectedNavigationItem(position);
			}
		});

		// For each of the sections in the app, add a tab to the action bar.
		for (int i = 0; i < mSectionsPagerAdapter.getCount(); i++) {
			// Create a tab with text corresponding to the page title defined by
			// the adapter. Also specify this Activity object, which implements
			// the TabListener interface, as the callback (listener) for when
			// this tab is selected.
			actionBar.addTab(actionBar.newTab()
					.setText(mSectionsPagerAdapter.getPageTitle(i))
					.setTabListener(this));
		}
		mViewPager.setCurrentItem(1); // start on middle item (record screen)
		lc = new LocationClient(this, this, this);
	}

	@Override
	protected void onStart() {
		super.onStart();
		// Connect the location client
		lc.connect();
	}

	@Override
	protected void onStop() {
		// Disconnecting the client invalidates it.
		lc.disconnect();
		super.onStop();
	}

	@Override
	protected void onPause() {
		super.onPause();
		if (spectroFragment != null)
			spectroFragment.pauseScrolling();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.spectro, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		/*
		 * Called when an item is selected by the user from the drop-down menu.
		 * The menu only contains one item; Settings.
		 */
		switch (item.getItemId()) {
		case R.id.action_settings: 
			// if 'Settings' is selected:
			spectroFragment.pauseScrolling(); // pause the moving spectrogram display
			// create an Intent to transition from SpectroActivity to SettingsActivity:
			Intent openSettings = new Intent(SpectroActivity.this,
					SettingsActivity.class); 
			startActivityForResult(openSettings, PREF_REQUEST_CODE);
			// execute the intent, anticipating that the user's settings
			// change will eventually be returned

			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	/**
	 * On the return of data from the user's settings change, extract data from the intent
	 * and update preferences accordingly
	 */
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {

		// check that the request code is what is expected:
		if (requestCode == PREF_REQUEST_CODE && resultCode == RESULT_OK) {
			String key = data.getStringExtra("PREF_KEY");

			if (key.equals(PREF_LANDSCAPE_KEY)) {
				// if orientation lock preference changed, immediately reflect this by
				// rotating the display
				SharedPreferences prefs = PreferenceManager
						.getDefaultSharedPreferences(this);
				boolean landscape = prefs.getBoolean(PREF_LANDSCAPE_KEY, false);
				if (landscape)
					setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
				else
					setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);
			}

		}
	}

	@Override
	public void onTabSelected(ActionBar.Tab tab,
			FragmentTransaction fragmentTransaction) {
		// When the given tab is selected, switch to the corresponding page in
		// the ViewPager.
		mViewPager.setCurrentItem(tab.getPosition());
	}

	@Override
	public void onTabUnselected(ActionBar.Tab tab,
			FragmentTransaction fragmentTransaction) {
	}

	@Override
	public void onTabReselected(ActionBar.Tab tab,
			FragmentTransaction fragmentTransaction) {
	}

	/**
	 * A {@link FragmentPagerAdapter} that returns a fragment corresponding to
	 * one of the sections/tabs/pages.
	 */
	public class SectionsPagerAdapter extends FragmentPagerAdapter {

		public SectionsPagerAdapter(FragmentManager fm) {
			super(fm);
		}

		/**
		 * Returns fragments that populate the pager.
		 */
		@Override
		public Fragment getItem(int position) {
			Fragment fragment = null;
			switch (position) {
			case 0:
				// first fragment is the library fragment
				library = new LibraryFragment();
				fragment = library;
				break;
			case 1:
				// second is the spectrogram/'record' fragment
				spectroFragment = new SpectroFragment();
				fragment = spectroFragment;
				break;
			}
			return fragment;
		}

		@Override
		public int getCount() {
			// Show 2 total pages.
			return 2;
		}

		@Override
		public CharSequence getPageTitle(int position) {
			Locale l = Locale.getDefault();
			switch (position) {
			case 0:
				// first fragment is 'Library':
				return getString(R.string.library).toUpperCase(l);
			case 1:
				// second is 'Record':
				return getString(R.string.record).toUpperCase(l);
			}
			return null;
		}

	}

	/**
	 * Called by Location Services if the attempt to connect to
	 * Location Services fails.
	 */
	@Override
	public void onConnectionFailed(ConnectionResult connectionResult) {
		/*
		 * Google Play services can resolve some errors it detects. If the error
		 * has a resolution, try sending an Intent to start a Google Play
		 * services activity that can resolve error.
		 */
		if (connectionResult.hasResolution()) {
			try {
				// Start an Activity that tries to resolve the error
				connectionResult.startResolutionForResult(this,
						CONNECTION_FAILURE_RESOLUTION_REQUEST);
				/*
				 * Thrown if Google Play services canceled the original
				 * PendingIntent
				 */
			} catch (IntentSender.SendIntentException e) {
				// Log the error
				e.printStackTrace();
			}
		} else {
			/*
			 * If no resolution is available, display a dialog to the user with
			 * the error.
			 */
			int errorCode = GooglePlayServicesUtil
					.isGooglePlayServicesAvailable(this);
			if (errorCode != ConnectionResult.SUCCESS) {
				GooglePlayServicesUtil.getErrorDialog(errorCode, this, 0)
				.show();
			}
		}
	}

	/**
	 * Called by Location Services when the request to connect the client
	 * finishes successfully.
	 */
	@Override
	public void onConnected(Bundle connectionHint) {
		if (spectroFragment != null) {
			// pass the location client to the spectrogram fragment:
			spectroFragment.setLocationClient(lc);
		}
	}

	/*
	 * Called by Location Services if the connection to the location client
	 * drops because of an error.
	 */
	@Override
	public void onDisconnected() {
		// TODO -- display connection status
	}

	/**
	 *  Method that forces an update to the library's file list (called after
	 *  a new spectrogram capture has been made).
	 */
	public void updateLibraryFiles() {
		runOnUiThread(new Runnable() {
			public void run() {
				if (library != null) {
					library.populateFilesList();
				}
			}
		});

	}
}
