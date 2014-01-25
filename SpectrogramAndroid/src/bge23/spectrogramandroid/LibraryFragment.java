package bge23.spectrogramandroid;

import java.io.File;
import java.util.ArrayList;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

public class LibraryFragment extends Fragment {

	private ArrayList<String> imageFiles;
	private File directory;
	private String IMAGE_SUFFIX = ".jpg";
	private ListView fileListView;

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		View rootView = inflater.inflate(
				R.layout.fragment_library,
				container, false);
		directory = StoredBitmapAudio.getAlbumStorageDir(LiveSpectrogramSurfaceView.STORE_DIR_NAME);
		directory.mkdirs();
		fileListView = (ListView) rootView.findViewById(R.id.listview_file_library);
		populateFilesList();
		fileListView.setAdapter(new ArrayAdapter<String>(getActivity(),android.R.layout.simple_list_item_1, imageFiles));
		fileListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
				//TODO
				Log.d("lib","item clicked!");
			}
		});
		return rootView;
	}


	private void populateFilesList() {
		//inspired by http://stackoverflow.com/questions/5800981/how-to-display-files-on-the-sd-card-in-a-listview
		imageFiles = new ArrayList<String>();
		File[] directoryContents = directory.listFiles();
		for (int i = 0; i < directoryContents.length; i++) {
			if (directoryContents[i].getName().endsWith(IMAGE_SUFFIX)) imageFiles.add(directoryContents[i].getName());
		}
	}


}
