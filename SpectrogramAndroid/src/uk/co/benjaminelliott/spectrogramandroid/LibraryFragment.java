package uk.co.benjaminelliott.spectrogramandroid;

import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import uk.co.benjaminelliott.spectrogramandroid.R;
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
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;

public class LibraryFragment extends Fragment {

	private ArrayList<String> imageFiles;
	private File directory;
	private String IMAGE_SUFFIX = ".jpg";
	private String AUDIO_SUFFIX = ".wav";
	private ListView fileListView;
	private MediaPlayer player;

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		View rootView = inflater.inflate(
				R.layout.fragment_library,
				container, false);
		directory = AudioBitmapConverter.getAlbumStorageDir(LiveSpectrogramSurfaceView.STORE_DIR_NAME);
		directory.mkdirs();
		fileListView = (ListView) rootView.findViewById(R.id.listview_file_library);
		populateFilesList();
		fileListView.setAdapter(new ArrayAdapter<String>(getActivity(),android.R.layout.simple_list_item_1, imageFiles));
		fileListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
				Log.d("lib","item clicked!");
				viewImage(imageFiles.get(position));
			}
		});
		return rootView;
	}
	
	private void viewImage(final String filename) {
		final String filepath = directory.getAbsolutePath()+"/"+filename;
		
		//play wav file simultaneously with showing spectrogram:
		player = new MediaPlayer();
		File f = new File(filepath+AUDIO_SUFFIX);
		f.setReadable(true,false); //need to set permissions so that it can be read by the media player
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
		
		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
		ImageView specImage = new ImageView(getActivity());
		Bitmap imgAsBmp = BitmapFactory.decodeFile(filepath+IMAGE_SUFFIX);
		specImage.setImageBitmap(imgAsBmp);
		builder.setView(specImage);
		builder.setPositiveButton("Show on map", new DialogInterface.OnClickListener() { 
		    @Override
		    public void onClick(DialogInterface dialog, int which) {
		        dialog.cancel();
		        player.stop();
		        player.release();
		        
		        //open Google Maps to display image's geotag
		        double latitude = getConvertedLatitude(filepath+IMAGE_SUFFIX);
		        double longitude = getConvertedLongitude(filepath+IMAGE_SUFFIX);
		        Uri uri = Uri.parse("geo:"+latitude+","+longitude+"?q="+latitude+","+longitude);
		        Intent intent = new Intent(android.content.Intent.ACTION_VIEW, uri);
		        startActivity(intent);
		    }
		});
		builder.setNeutralButton("Send to server", new DialogInterface.OnClickListener() { 
		    @Override
		    public void onClick(DialogInterface dialog, int which) {
		    	new ServerSendTask().execute(directory.getAbsolutePath(), filename);
		    }
		});
		builder.setNegativeButton("Dismiss", new DialogInterface.OnClickListener() { 
		    @Override
		    public void onClick(DialogInterface dialog, int which) {
		        dialog.cancel();
		        player.stop();
		        player.release();
		    }
		});
		builder.show();
		player.start();
		
		
	}
	private void populateFilesList() {
		//inspired by http://stackoverflow.com/questions/5800981/how-to-display-files-on-the-sd-card-in-a-listview
		imageFiles = new ArrayList<String>();
		File[] directoryContents = directory.listFiles();
		for (int i = 0; i < directoryContents.length; i++) {
			String name = directoryContents[i].getName();
			if (name.endsWith(IMAGE_SUFFIX)) {
				name = name.substring(0, name.length() - IMAGE_SUFFIX.length());
				imageFiles.add(name);
			}
		}
	}
	
	public void updateFilesList() {
		populateFilesList();
		((ArrayAdapter<String>) fileListView.getAdapter()).notifyDataSetChanged();
		Log.d("","Files updated");
		//TODO Y U NO WORK
	}
	

	private double getConvertedLongitude(String filepath) {
		ExifInterface exif = null;
		try {
			exif = new ExifInterface(filepath);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return convertDMStoDec(exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE), exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF));
	}

	private double getConvertedLatitude(String filepath) {
		ExifInterface exif = null;
		try {
			exif = new ExifInterface(filepath);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return convertDMStoDec(exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE), exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE_REF));
	}
	
	private double convertDMStoDec(String dms, String direction) {
	    	// see http://en.wikipedia.org/wiki/Geographic_coordinate_conversion#Conversion_from_DMS_to_Decimal_Degree
		 	//dms string will be of the form degrees+"/1,"+minutes+"/1,"+seconds+"/1000"
		Log.d("","DMS: "+dms+" Dir: "+direction);
		 String[] splitDMS = dms.split("/1,");
		 int dec = Integer.parseInt(splitDMS[0]);
		 int min = Integer.parseInt(splitDMS[1]);
		 float sec = Integer.parseInt(splitDMS[2].substring(0, splitDMS[2].length() - 5)) / 1000; //remove "/1000"
		 double total = dec+ (min*60+sec)/3600;
		 if (direction.equals("W") || direction.equals("S")) total = -total;
		 Log.d("","TOTAL: "+total);
		 return total;
	 }
}
