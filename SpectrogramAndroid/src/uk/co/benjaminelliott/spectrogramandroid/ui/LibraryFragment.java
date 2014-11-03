package uk.co.benjaminelliott.spectrogramandroid.ui;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import uk.co.benjaminelliott.spectrogramandroid.R;
import uk.co.benjaminelliott.spectrogramandroid.preferences.DynamicAudioConfig;
import uk.co.benjaminelliott.spectrogramandroid.storage.AudioBitmapConverter;
import uk.co.benjaminelliott.spectrogramandroid.transmission.ServerSendTask;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ExifInterface;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;

/**
 * The Fragment that holds the user's previous captures so they can review them, share them and
 * view their locations on a map.
 * 
 * TODO: the review dialog presented here is intended as a placeholder and should eventually be replaced
 * with something more informative.
 * 
 * @author Ben
 *
 */
public class LibraryFragment extends Fragment {

	private ArrayList<String> imageFiles = new ArrayList<String>();
	private File directory;
	private String IMAGE_SUFFIX = ".jpg";
	private String AUDIO_SUFFIX = ".wav";
	private ListView fileListView;
	private ArrayAdapter<String> adapter;
	private MediaPlayer player;

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		View rootView = inflater.inflate(R.layout.fragment_library,container, false);
		// get directory for spectrogram captures:
		directory = AudioBitmapConverter.getAlbumStorageDir(DynamicAudioConfig.STORE_DIR_NAME);
		// create the directory if necessary:
		directory.mkdirs();
		fileListView = (ListView) rootView.findViewById(R.id.listview_file_library);
		// populate the ListView with files found in the captures directory:
		adapter = new ArrayAdapter<String>(getActivity(),android.R.layout.simple_list_item_1, imageFiles);
		fileListView.setAdapter(adapter);
		populateFilesList();
		// when a list item is clicked, present it to the user for review:
		fileListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
				viewImage(imageFiles.get(position));
			}
		});

		return rootView;
	}

	/**
	 * Presents the specified spectrogram capture's image and audio for review.
	 * @param filename - the filename of the capture to review
	 */
	private void viewImage(final String filename) {
		final String filepath = directory.getAbsolutePath()+"/"+filename;

		//play WAV file simultaneously with showing spectrogram image:

		File audioFile = new File(filepath+AUDIO_SUFFIX);
		//need to set permissions so that it can be read by the media player
		audioFile.setReadable(true,false);

		player = new MediaPlayer();
		try {
			player.setDataSource(filepath+AUDIO_SUFFIX);
			player.prepare();
			player.start();
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
		} catch (SecurityException e) {
			e.printStackTrace();
		} catch (IllegalStateException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

		// present the image using an AlertDialog:
		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
		// ImageView to hold the image:
		ImageView specImage = new ImageView(getActivity());
		specImage.setPadding(0, 50, 0, 50);
		Bitmap imgAsBmp = BitmapFactory.decodeFile(filepath+IMAGE_SUFFIX);
		specImage.setImageBitmap(imgAsBmp);
		builder.setView(specImage);

		builder.setTitle(filename);

		// set AlertDialog to have three buttons: "show on map", "upload" and "dismiss"
		builder.setPositiveButton("Show on map", new DialogInterface.OnClickListener() { 
			@Override
			public void onClick(DialogInterface dialog, int which) {
				//open Google Maps using an intent to display image's geotag

				// remove dialog and stop audio from playing:
				dialog.cancel();
				player.stop();
				player.release();

				// get latitude and longitude from EXIF data:
				double latitude = getConvertedLatitude(filepath+IMAGE_SUFFIX);
				double longitude = getConvertedLongitude(filepath+IMAGE_SUFFIX);
				// use an intent to open Google Maps (or similar):
				Uri uri = Uri.parse("geo:"+latitude+","+longitude+"?q="+latitude+","+longitude);
				Intent intent = new Intent(android.content.Intent.ACTION_VIEW, uri);
				startActivity(intent);
			}
		});
		builder.setNeutralButton("Upload", new DialogInterface.OnClickListener() { 
			@Override
			public void onClick(DialogInterface dialog, int which) {
				// upload the user's capture to the server. TODO - feedback
				new ServerSendTask().execute(directory.getAbsolutePath(), filename);
			}
		});
		builder.setNegativeButton("Dismiss", new DialogInterface.OnClickListener() { 
			@Override
			public void onClick(DialogInterface dialog, int which) {
				// remove the dialog and stop the audio from playing. TODO - allow deletion
				dialog.cancel();
				player.stop();
				player.release();
			}
		});

		builder.show();
		player.start();		
	}

	/**
	 * Populates the list of files with the files in the captures directory.
	 */
	public void populateFilesList() {
		File[] directoryContents = directory.listFiles();
		for (int i = 0; i < directoryContents.length; i++) {
			String name = directoryContents[i].getName();
			if (name.endsWith(IMAGE_SUFFIX)) {
				name = name.substring(0, name.length() - IMAGE_SUFFIX.length());
				if (!imageFiles.contains(name)) imageFiles.add(name);
			}
		}
		adapter.notifyDataSetChanged();
	}	

	/**
	 * Returns the longitude (in decimal format) from the EXIF data of a JPEG obtained 
	 * from the provided filepath.
	 * @param filepath - the filepath of the image file
	 * @return the longitude in decimal format
	 */
	private double getConvertedLongitude(String filepath) {
		ExifInterface exif = null;
		try {
			exif = new ExifInterface(filepath);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return convertDMStoDec(exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE), exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF));
	}

	/**
	 * Returns the latitude (in decimal format) from the EXIF data of a JPEG obtained 
	 * from the provided filepath.
	 * @param filepath - the filepath of the image file
	 * @return the latitude in decimal format
	 */
	private double getConvertedLatitude(String filepath) {
		ExifInterface exif = null;
		try {
			exif = new ExifInterface(filepath);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return convertDMStoDec(exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE), exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE_REF));
	}

	/**
	 * Converts a degrees-minutes-seconds latitude/longitude to decimal format.
	 * @param dms - the latitude/longitude in degrees-minutes-seconds format
	 * @param direction - "W", "E", "S" or "N"
	 * @return a double representing the input in decimal format.
	 */
	private static double convertDMStoDec(String dms, String direction) {
		// see http://en.wikipedia.org/wiki/Geographic_coordinate_conversion#Conversion_from_DMS_to_Decimal_Degree

		//dms string will be of the form degrees+"/1,"+minutes+"/1,"+seconds+"/1000"
		String[] splitDMS = dms.split("/1,");
		if (splitDMS.length != 4)
			throw new IllegalArgumentException("DMS string did not have hte expected format.");

		int dec = Integer.parseInt(splitDMS[0]);
		int min = Integer.parseInt(splitDMS[1]);
		float sec = Integer.parseInt(splitDMS[2].substring(0, splitDMS[2].length() - 5)) / 1000; //remove "/1000"

		double total = dec + (min * 60 + sec) / 3600;

		if (direction.equals("W") || direction.equals("S")) total = -total;

		return total;
	}
}
