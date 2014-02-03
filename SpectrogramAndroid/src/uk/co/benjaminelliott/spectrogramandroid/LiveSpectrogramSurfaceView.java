package uk.co.benjaminelliott.spectrogramandroid;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.math.BigDecimal;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.media.MediaPlayer;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.v4.view.MotionEventCompat;
import android.text.InputType;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.location.LocationClient;

public class LiveSpectrogramSurfaceView extends SurfaceView implements SurfaceHolder.Callback {

	public static final String STORE_DIR_NAME = "Spectrogram captures";
	public static final String PREF_AUDIO_KEY = "pref_user_test_audio";

	private int width;
	private int height;
	
	private Context context;
	private SpectrogramDrawer sd;
	private int mActivePointerId = MotionEvent.INVALID_POINTER_ID;
	private float lastTouchX;
	private float lastTouchY;
	private Handler handler;
	Runnable onLongPress;
	private float centreX;
	private float centreY;
	private boolean selecting = false; //true if user has entered the selection state
	private int selectedCorner; // indicates which corner is being dragged; 0 is top-left, 1 is top-right, 2 is bottom-left, 3 is bottom-right
	private ImageButton resumeButton;
	private Button selectionConfirmButton;
	private Button selectionCancelButton;

	private LinearLayout captureButtonContainer;
	private int minContainerHeight = 120;
	private int minContainerWidth = 120;

	private TextView leftTimeTextView;
	private TextView topFreqTextView;
	private TextView selectRectTextView;
	private String filename;
	private LocationClient lc;
	private AlertDialog loadingAlert; //used to force user to wait for capture
	private LibraryFragment library;
	private ViewUpdateHandler vuh; //used to send message to library pane to update file list
	private MediaPlayer player;
	private String audioFilepath; // filepath for user test audio TODO remove

	//left, right, top and bottom edge locations for the select-area rectangle:
	private float selectRectL = 0;
	private float selectRectR = 0;
	private float selectRectT = 0;
	private float selectRectB = 0;

	//initial width and height for select-area rectangle:
	private float SELECT_RECT_WIDTH = 200;
	private float SELECT_RECT_HEIGHT = 200;

	private final float CORNER_CIRCLE_RADIUS = 40; //when setting, must think about how large the target can be for the user to hit it accurately 

	
	//allocate memory for reused variables here to reduce GC
	private int action;
	private int pointerIndex;
	private float x;
	private float y;
	private float dx;
	private float dy;
	
	public LiveSpectrogramSurfaceView(Context context) {
		super(context);
		init(context);
	}

	public LiveSpectrogramSurfaceView(Context context, AttributeSet attrs) {
		this(context, attrs,0);
		init(context);
	}

	public LiveSpectrogramSurfaceView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		init(context);
	}

	private void init(Context context) { //Constructor for displaying audio from microphone
		this.context = context;
		handler = new Handler();
		getHolder().addCallback(this);
		onLongPress = new Runnable() {
			public void run() {
				Log.d("", "Long press detected.");
				selecting = true;
				selectRectL = (centreX - SELECT_RECT_WIDTH/2 < 0) ? 0 : centreX - SELECT_RECT_WIDTH/2;
				selectRectR = (centreX + SELECT_RECT_WIDTH/2 > getWidth()) ? getWidth() : centreX + SELECT_RECT_WIDTH/2;
				selectRectT = (centreY - SELECT_RECT_HEIGHT/2 < 0) ? 0 : centreY - SELECT_RECT_HEIGHT/2;
				selectRectB = (centreY + SELECT_RECT_HEIGHT/2 > getHeight()) ? getHeight() : centreY + SELECT_RECT_HEIGHT/2;
				sd.drawSelectRect(selectRectL,selectRectR,selectRectT,selectRectB);
				//moveCaptureButtonContainer();
				selectRectTextView.setVisibility(View.VISIBLE);
				//captureButtonContainer.setVisibility(View.VISIBLE); TODO removed for user test only!!
				selectionConfirmButton.setEnabled(true);
				selectionCancelButton.setEnabled(true);
				updateSelectRectText();
			}
		};
		
		
		AlertDialog.Builder builder = new AlertDialog.Builder(context);
		builder.setTitle("Capture in progress...");
		final ProgressBar pb = new ProgressBar(context);
		builder.setView(pb);
		loadingAlert = builder.create();
		vuh = new ViewUpdateHandler(this);
	}
	
	private static class ViewUpdateHandler extends Handler {
	    private final WeakReference<LiveSpectrogramSurfaceView> wr; 

	    ViewUpdateHandler(LiveSpectrogramSurfaceView lssv) { 
	    	wr = new WeakReference<LiveSpectrogramSurfaceView>(lssv); 
	    } 
		@Override
		public void handleMessage(Message msg) {
			wr.get().updateLibraryFiles();
		}
	};
	
	public void updateLibraryFiles() {
		library.updateFilesList();
	}

	public void setLibraryFragment(LibraryFragment library) {
		this.library = library;
	}

	public void setResumeButton(ImageButton resumeButton) {
		this.resumeButton = resumeButton;
	}

	public void setCaptureButtonContainer(LinearLayout captureButtonContainer) {
		this.captureButtonContainer = captureButtonContainer;

	}

	public void setLeftTimeTextView(TextView leftTimeTextView) {
		this.leftTimeTextView = leftTimeTextView;
	}

	public void setTopFreqTextView(TextView topFreqTextView) {
		this.topFreqTextView = topFreqTextView;
	}

	public void setSelectRectTextView(TextView selectRectTextView) {
		this.selectRectTextView = selectRectTextView;
	}

	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height)  {
		Log.d("LSSV","SURFACE CHANGED");
	}

	@Override
	public void surfaceCreated(SurfaceHolder arg0) {
		width = getWidth();
		height = getHeight();
		//check audio filepath preference when surface created so it updates when setting changed
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		audioFilepath = Environment.getExternalStorageDirectory().getAbsolutePath()+"/"+prefs.getString(PREF_AUDIO_KEY, "NULL");
		Log.d("LSSV","Audio selected: "+audioFilepath);

		try {
			//play wav file simultaneously with showing spectrogram:
			player = new MediaPlayer();
			File f = new File(audioFilepath);
			f.setReadable(true,false); //need to set permissions so that it can be read by the media player
			player.setDataSource(audioFilepath);
			Log.d("","About to play file "+audioFilepath);
			player.prepare();
			sd = new SpectrogramDrawer(this);
			player.start();
			resumeButton.setVisibility(View.GONE);
			updateLeftTimeText();
			updateTopFreqText();
			Log.d("","SURFACE CREATED");
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
		} catch (SecurityException e) {
			e.printStackTrace();
		} catch (IllegalStateException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		

	}

	@Override
	public void surfaceDestroyed(SurfaceHolder arg0) {
		//TODO: worry about interrupted something something something
		Log.d("LSSV","SURFACE DESTROYED");
		if (sd != null) sd.stop();
		sd = null;
	}
	
	public void stop() {
		Log.d("LSSV","STOP");
		if (player != null) { //Android nullifies this on its own, e.g. if a call comes in
			player.stop();
			player.release();
			player = null;
		}
		sd.stop();
		if (selecting) cancelSelection();

	}

	@Override
	public boolean onTouchEvent(MotionEvent ev) {
		// Let the ScaleGestureDetector inspect all events.
		//sgd.onTouchEvent(ev);

		action = MotionEventCompat.getActionMasked(ev); 

		switch (action) {

		case MotionEvent.ACTION_DOWN: { //finger pressed on screen
			if (!selecting) {
				pauseScrolling();
				handler.postDelayed(onLongPress,1000); //run the long-press runnable if not cancelled by move event (1 second timeout) [only if not already selecting]
			}

			pointerIndex = MotionEventCompat.getActionIndex(ev); 
			x = MotionEventCompat.getX(ev, pointerIndex); 
			centreX = MotionEventCompat.getX(ev, pointerIndex);
			centreY = MotionEventCompat.getY(ev, pointerIndex);
			Log.d("","ACTION_DOWN");
			// Remember where we started (for dragging)
			lastTouchX = x;
			lastTouchY = MotionEventCompat.getY(ev, pointerIndex);
			// Save the ID of this pointer [finger], in case of drag 
			mActivePointerId = MotionEventCompat.getPointerId(ev, 0);
			if (selecting) {
				//decide which corner is being dragged based on proximity
				selectedCorner = 0;
				if (Math.abs(centreX-selectRectL) <= CORNER_CIRCLE_RADIUS && Math.abs(centreY-selectRectT) <= CORNER_CIRCLE_RADIUS) {
					//user touched top-left corner
					Log.d("","Top left");
					selectedCorner = 1;
				}
				if (Math.abs(centreX-selectRectR) <= CORNER_CIRCLE_RADIUS && Math.abs(centreY-selectRectT) <= CORNER_CIRCLE_RADIUS) {
					//user touched top-right corner
					Log.d("","Top right");
					selectedCorner = 2;
				}
				if (Math.abs(centreX-selectRectL) <= CORNER_CIRCLE_RADIUS && Math.abs(centreY-selectRectB) <= CORNER_CIRCLE_RADIUS) {
					//user touched bottom-left corner
					Log.d("","Bottom left");
					selectedCorner = 3;
				}
				if (Math.abs(centreX-selectRectR) <= CORNER_CIRCLE_RADIUS && Math.abs(centreY-selectRectB) <= CORNER_CIRCLE_RADIUS) {
					//user touched bottom-right corner
					Log.d("","Bottom right");
					selectedCorner = 4;
				}
			}
			break;
		}

		case MotionEvent.ACTION_MOVE: { //occurs when there is a difference between ACTION_UP and ACTION_DOWN
			// Find the index of the active pointer and fetch its position
			pointerIndex = MotionEventCompat.findPointerIndex(ev,mActivePointerId);
			//final float x = ev.getRawX(); //Note: never care about y axis
			// Calculate the distance moved
			if (!selecting) { //don't allow for scrolling if user is trying to select an area of the spectrogram
				x = MotionEventCompat.getX(ev, pointerIndex); //Note: never care about y axis
				dx = x - lastTouchX;
				if (dx > 5 || dx < -5) { //only if moved more than 5 pixels
					handler.removeCallbacks(onLongPress); //cancel long-press runnable
					System.out.println("Long-press timer cancelled 1.");
					System.out.println("Last touch x: " + lastTouchX + " x: " + x
							+ " dx: " + dx + " (int)dx: " + (int) dx);
					sd.quickSlide((int) dx);
					// Remember this touch position for the next move event
				}
				lastTouchX = x;
			} else { 
				//if selecting mode entered, allow user to move corners to adjust select-area rectangle size
				x = MotionEventCompat.getX(ev, pointerIndex);
				y = MotionEventCompat.getY(ev, pointerIndex);
				x = x - lastTouchX;
				dy = y - lastTouchY;
				moveCorner(selectedCorner, dx, dy);				
				lastTouchX = x;
				lastTouchY = y;
			}
			break;

		}

		case MotionEvent.ACTION_UP: {
			handler.removeCallbacks(onLongPress); //cancel long-press runnable
			System.out.println("Long-press timer cancelled 2.");
			mActivePointerId = MotionEvent.INVALID_POINTER_ID;
			break;
		}

		case MotionEvent.ACTION_CANCEL: {
			handler.removeCallbacks(onLongPress); //cancel long-press runnable
			System.out.println("Long-press timer cancelled 3.");
			mActivePointerId = MotionEvent.INVALID_POINTER_ID;
			break;
		}

		case MotionEvent.ACTION_POINTER_UP: {
			handler.removeCallbacks(onLongPress); //cancel long-press runnable
			System.out.println("Long-press timer cancelled 4.");
			pointerIndex = MotionEventCompat.getActionIndex(ev); 
			final int pointerId = MotionEventCompat.getPointerId(ev, pointerIndex); 

			if (pointerId == mActivePointerId) {
				// This was our active pointer going up. Choose a new
				// active pointer and adjust accordingly.
				final int newPointerIndex = pointerIndex == 0 ? 1 : 0; //TODO dafuq
				lastTouchX = MotionEventCompat.getX(ev, newPointerIndex); 
				mActivePointerId = MotionEventCompat.getPointerId(ev, newPointerIndex);
			}
			break;
		}
		}       
		return true;
	}

	public void moveCorner(int cornerIndex, float dx, float dy) {
		switch(cornerIndex) {
		// if 0 then not near any corner
		case 1:
			//top-left corner moved
			selectRectL += dx;
			selectRectT += dy;
			break;
		case 2:
			//top-right corner moved
			selectRectR += dx;
			selectRectT += dy;
			break;
		case 3:
			//bottom-left corner moved
			selectRectL += dx;
			selectRectB += dy;
			break;
		case 4:
			//bottom-right corner moved
			selectRectR += dx;
			selectRectB += dy;
			break;
		}
		selectRectL = (selectRectL < 0) ? 0 : selectRectL;
		selectRectR = (selectRectR < 0) ? 0 : selectRectR;
		selectRectL = (selectRectL > width) ? width : selectRectL;
		selectRectR = (selectRectR > width) ? width : selectRectR;
		selectRectT = (selectRectT < 0) ? 0 : selectRectT;
		selectRectB = (selectRectB < 0) ? 0 : selectRectB;
		selectRectT = (selectRectT > height) ? height : selectRectT;
		selectRectB = (selectRectB > height) ? height : selectRectB;
		sd.drawSelectRect(selectRectL,selectRectR,selectRectT,selectRectB);
		updateSelectRectText();
		//moveCaptureButtonContainer(); TODO restore after user test!
		//Log.d("","L: "+selectRectL+" R: "+selectRectR+" T: "+selectRectT+" B: "+selectRectB);
	}

	protected void moveCaptureButtonContainer() {
		RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams)captureButtonContainer.getLayoutParams();
		int lowestDimension = (selectRectT < selectRectB) ? (int)selectRectB : (int)selectRectT;
		int highestDimension = (selectRectT < selectRectB) ? (int)selectRectT : (int)selectRectB;
		int leftmostDimension = (selectRectL < selectRectR) ? (int)selectRectL : (int)selectRectR;
		int halfDifference = (int)Math.abs((selectRectL-selectRectR)/2);
		int centred = leftmostDimension + halfDifference - captureButtonContainer.getWidth()/2;
		int yDimension = (getHeight() - lowestDimension < minContainerHeight + 10)? highestDimension - minContainerHeight - 10 : lowestDimension + 10;
		if (leftmostDimension + minContainerWidth > getWidth()) leftmostDimension = getWidth()-minContainerWidth;
		params.setMargins(centred, (int)(yDimension+CORNER_CIRCLE_RADIUS/2), 0, 0);
		Log.d("Button","Margin set to L:"+centred+" T: "+yDimension+" R: "+0+" B:"+0);
		Log.d("","height: "+getHeight()+" fullContainerHeight: "+minContainerHeight+" current height: "+captureButtonContainer.getHeight()+" lowest dimension: "+lowestDimension);
		captureButtonContainer.setLayoutParams(params);
	}

	protected void pauseScrolling() {
		if (sd != null) {
			sd.pauseScrolling();
			resumeButton.setVisibility(View.VISIBLE);
		}
	}

	public void resumeScrolling() {
		if (selecting) cancelSelection();
		sd = new SpectrogramDrawer(this);
		resumeButton.setVisibility(View.GONE);
		selectRectTextView.setVisibility(View.GONE);
	}

	public void resumeFromPause() {
		//if (gc != null) gc.resumeFromPause(); //TODO bit messy
	}

	private void updateLeftTimeText() {
		BigDecimal bd = new BigDecimal(Float.toString(sd.getScreenFillTime()));
		bd = bd.setScale(2, BigDecimal.ROUND_HALF_UP); //round to 2 dp
		leftTimeTextView.setText("-"+bd.floatValue()+" sec");
	}

	private void updateTopFreqText() {
		BigDecimal bd = new BigDecimal(Float.toString(sd.getMaxFrequency()/1000));
		bd = bd.setScale(2, BigDecimal.ROUND_HALF_UP); //round to 2 dp
		topFreqTextView.setText(bd.floatValue()+" kHz");
	}

	private void updateSelectRectText() {
		selectRectTextView.setText("t0: "+sd.getTimeFromStartAtPixel(selectRectL)+" t1: "+sd.getTimeFromStartAtPixel(selectRectR)+" f0: "+sd.getFrequencyAtPixel(selectRectT)+" f1: "+sd.getFrequencyAtPixel(selectRectB));
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
				filename = inputText.getText().toString();
				new AsyncCaptureTask(context).execute(); //execute the capture operations
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
		captureButtonContainer.setVisibility(View.GONE);
		selectionConfirmButton.setEnabled(false);
		selectionCancelButton.setEnabled(false);
		selecting = false;
	}

	public void setSelectionConfirmButton(Button selectionConfirmButton) {
		this.selectionConfirmButton = selectionConfirmButton;

	}

	public void setSelectionCancelButton(Button selectionCancelButton) {
		this.selectionCancelButton = selectionCancelButton;

	}

	protected void setLocationClient(LocationClient lc) {
		this.lc = lc;
	}

	private class AsyncCaptureTask extends AsyncTask<Void, Void, Void> {
		private Context context;
		public AsyncCaptureTask(Context context) {
			this.context = context;
		}

		@Override
		protected void onPostExecute(Void result) {
			super.onPostExecute(result);
			Toast.makeText(context, "Capture completed!", Toast.LENGTH_SHORT).show();
			loadingAlert.dismiss();
			vuh.sendMessage(new Message()); //update library contents (must be done from UI thread)
			Log.d("","Message sent!");
		}
		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			loadingAlert.show();
		}
		@Override
		protected Void doInBackground(Void... arg0) {
			Bitmap bitmapToStore = sd.getBitmapToStore(selectRectL, selectRectR, selectRectT, selectRectB);
			short[] audioToStore = sd.getAudioToStore(selectRectL, selectRectR, selectRectT, selectRectB);
			AudioBitmapConverter abc = new AudioBitmapConverter(filename, STORE_DIR_NAME, bitmapToStore,audioToStore,lc.getLastLocation(),sd.getBitmapGenerator().getSampleRate());
			abc.writeCBAToFile(filename, STORE_DIR_NAME);
			return null;
		}

	}

}
