package uk.co.benjaminelliott.spectrogramandroid.ui;

import uk.co.benjaminelliott.spectrogramandroid.activities.SpectroActivity;
import uk.co.benjaminelliott.spectrogramandroid.preferences.DynamicAudioConfig;
import uk.co.benjaminelliott.spectrogramandroid.storage.AudioBitmapConverter;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.text.InputType;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.google.android.gms.location.LocationClient;

/**
 * SurfaceView that holds the spectrogram.
 * @author Ben
 *
 */
public class SpectrogramSurfaceView extends SurfaceView implements SurfaceHolder.Callback {

	private int width;

	private SpectroFragment spectroFragment;
	private DynamicAudioConfig dac;
	private SpectrogramDrawer sd;
	private InteractionHandler interactionHandler;
	private Context context;
	protected boolean selecting = false; //true if user has entered the selection state
	private String filename;
	private LocationClient lc;
	private AlertDialog loadingAlert; //used to force user to wait for capture
	private LibraryFragment library;

	public SpectrogramSurfaceView(Context context) {
		super(context);
		init(context);
	}

	public SpectrogramSurfaceView(Context context, AttributeSet attrs) {
		this(context, attrs,0);
		init(context);
	}

	public SpectrogramSurfaceView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		init(context);
	}

	private void init(Context context) { //Initialiser for displaying audio from microphone
		this.context = context;
		getHolder().addCallback(this);
		interactionHandler = new InteractionHandler(this);

		AlertDialog.Builder builder = new AlertDialog.Builder(context);
		builder.setTitle("Capture in progress...");
		final ProgressBar pb = new ProgressBar(context);
		builder.setView(pb);
		loadingAlert = builder.create();
	}

	@Override
	public void surfaceCreated(SurfaceHolder arg0) {
		width = getWidth();
		try {
			dac = new DynamicAudioConfig(this.getContext());
			sd = new SpectrogramDrawer(dac, this.getWidth(), this.getHeight(), this.getHolder());
			spectroFragment.disableResumeButton();
			spectroFragment.setLeftTimeText(sd.getScreenFillTime());
			spectroFragment.setRightTimeText(sd.getTimeFromStopAtPixel(width));
			spectroFragment.setTopFreqText(sd.getMaxFrequency() / 1000);
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
		} catch (SecurityException e) {
			e.printStackTrace();
		} catch (IllegalStateException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width,
			int height) {
		//Nothing to do?
	}

	public void updateLibraryFiles() {
		library.updateFilesList();
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder arg0) {
		if (sd != null) {
			sd.stop();
		}
		sd = null;
	}

	public void stop() {
		if (sd != null) {
			sd.stop();
		}
		if (selecting) {
			cancelSelection();
		}

	}

	@Override
	public boolean onTouchEvent(MotionEvent ev) {
		interactionHandler.handleTouchEvent(ev);
		spectroFragment.setLeftTimeText(sd.getTimeAtPixel(0)); //TODO update these live
		spectroFragment.setLeftTimeText(sd.getTimeAtPixel(width));
		return true;
	}

	public void pauseScrolling() {
		if (sd != null) {
			sd.pauseScrolling();
			spectroFragment.enableResumeButton();
		}
	}

	public void resumeScrolling() {
		if (selecting) cancelSelection();
		dac = new DynamicAudioConfig(this.getContext());
		sd = new SpectrogramDrawer(dac, this.getWidth(), this.getHeight(), this.getHolder());
		spectroFragment.disableResumeButton();
	}

	public void confirmSelection() {
		//create and display an AlertDialog requesting a filename
		AlertDialog.Builder builder = new AlertDialog.Builder(context);
		builder.setTitle("What did you hear?");
		final EditText inputText = new EditText(context);
		inputText.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
		builder.setView(inputText);

		builder.setPositiveButton("OK", new DialogInterface.OnClickListener() { 
			@Override
			public void onClick(DialogInterface dialog, int which) {
				filename = inputText.getText().toString().trim();
				new CaptureTask(context).execute(); //execute the capture operations
			}
		});
		builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				dialog.cancel();
			}
		});
		builder.show();
	}

	public void cancelSelection() {
		sd.hideSelectRect();
		spectroFragment.disableCaptureButtonContainer();
		selecting = false;
	}

	protected void slideTo(int offset) {
		sd.quickSlide(offset);
	}

	protected void updateSelectRect(float selectRectL, float selectRectT, float selectRectR, float selectRectB) {
		sd.drawSelectRect(selectRectL,selectRectT,selectRectR,selectRectB);
		spectroFragment.moveCaptureButtonContainer(selectRectL, selectRectT, selectRectR, selectRectB);
	}

	protected void enableCaptureButtonContainer() {
		spectroFragment.enableCaptureButtonContainer();
	}

	public void setLocationClient(LocationClient lc) {
		this.lc = lc;
	}

	public void setSpectroFragment(SpectroFragment spectroFragment) {
		this.spectroFragment = spectroFragment;
	}

	private class CaptureTask extends AsyncTask<Void, Void, Void> {
		private Context context;
		public CaptureTask(Context context) {
			this.context = context;
		}

		@Override
		protected void onPostExecute(Void result) {
			super.onPostExecute(result);
			Toast.makeText(context, "Capture completed!", Toast.LENGTH_SHORT).show();
			loadingAlert.dismiss();
			((SpectroActivity)spectroFragment.getActivity()).updateLibraryFiles();
		}
		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			loadingAlert.show();
		}
		@Override
		protected Void doInBackground(Void... arg0) {
			float[] dimens = interactionHandler.getSelectRectDimensions();
			Bitmap bitmapToStore = sd.getBitmapToStore(dimens[0],dimens[1],dimens[2],dimens[3]);
			short[] audioToStore = sd.getAudioToStore(dimens[0],dimens[1],dimens[2],dimens[3]);
			AudioBitmapConverter abc = new AudioBitmapConverter(filename, dac, bitmapToStore,audioToStore,lc.getLastLocation());
			abc.writeThisCbaToFile(filename, DynamicAudioConfig.STORE_DIR_NAME);
			abc.storeJPEGandWAV();
			return null;
		}
	}
}
