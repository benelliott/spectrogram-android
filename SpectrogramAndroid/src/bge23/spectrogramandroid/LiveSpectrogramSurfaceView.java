package bge23.spectrogramandroid;

import java.math.BigDecimal;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Handler;
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
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.location.LocationClient;

public class LiveSpectrogramSurfaceView extends SurfaceView implements SurfaceHolder.Callback {

	public static final String STORE_DIR_NAME = "Spectrogram captures";
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
	private TextView leftTimeTextView;
	private TextView topFreqTextView;
	private TextView selectRectTextView;
	private String filename;
	private LocationClient lc;
	private AlertDialog loadingAlert; //used to force user to wait for capture


	//left, right, top and bottom edge locations for the select-area rectangle:
	private float selectRectL;
	private float selectRectR;
	private float selectRectT;
	private float selectRectB;

	//initial width and height for select-area rectangle:
	private float SELECT_RECT_WIDTH = 200;
	private float SELECT_RECT_HEIGHT = 200;

	private float CORNER_CIRCLE_RADIUS = 30; //when setting, must think about how large the target can be for the user to hit it accurately 

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
				selectRectL = centreX - SELECT_RECT_WIDTH/2;
				selectRectR = centreX + SELECT_RECT_WIDTH/2;
				selectRectT = centreY - SELECT_RECT_HEIGHT/2;
				selectRectB = centreY + SELECT_RECT_HEIGHT/2;
				sd.drawSelectRect(selectRectL,selectRectR,selectRectT,selectRectB);
				selectRectTextView.setVisibility(View.VISIBLE);
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
	}

	public void setResumeButton(ImageButton resumeButton) {
		this.resumeButton = resumeButton;
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
		//TODO
	}

	@Override
	public void surfaceCreated(SurfaceHolder arg0) {
		sd = new SpectrogramDrawer(this);
		resumeButton.setVisibility(View.GONE);
		updateLeftTimeText();
		updateTopFreqText();
		Log.d("","SURFACE CREATED");
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder arg0) {
		//TODO: worry about interrupted something something something
	}

	@Override
	public boolean onTouchEvent(MotionEvent ev) {
		// Let the ScaleGestureDetector inspect all events.
		//sgd.onTouchEvent(ev);

		final int action = MotionEventCompat.getActionMasked(ev); 

		switch (action) { 
		case MotionEvent.ACTION_DOWN: { //finger pressed on screen
			pauseScrolling();
			final int pointerIndex = MotionEventCompat.getActionIndex(ev); 
			final float x = MotionEventCompat.getX(ev, pointerIndex); 
			centreX = MotionEventCompat.getX(ev, pointerIndex);
			centreY = MotionEventCompat.getY(ev, pointerIndex);
			Log.d("","ACTION_DOWN");
			if (!selecting) handler.postDelayed(onLongPress,1000); //run the long-press runnable if not cancelled by move event (1 second timeout) [only if not already selecting]
			System.out.println("Long-press timer started.");
			// Remember where we started (for dragging)
			lastTouchX = x;
			lastTouchY = MotionEventCompat.getY(ev, pointerIndex);
			System.out.println("Last touch x set to "+x);
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
			final int pointerIndex = MotionEventCompat.findPointerIndex(ev,
					mActivePointerId);
			//final float x = ev.getRawX(); //Note: never care about y axis
			// Calculate the distance moved
			if (!selecting) { //don't allow for scrolling if user is trying to select an area of the spectrogram
				final float x = MotionEventCompat.getX(ev, pointerIndex); //Note: never care about y axis
				final float dx = x - lastTouchX;
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
				float x = MotionEventCompat.getX(ev, pointerIndex);
				float y = MotionEventCompat.getY(ev, pointerIndex);
				float dx = x - lastTouchX;
				float dy = y - lastTouchY;
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
			final int pointerIndex = MotionEventCompat.getActionIndex(ev); 
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
		sd.drawSelectRect(selectRectL,selectRectR,selectRectT,selectRectB);
		updateSelectRectText();
	}

	protected void pauseScrolling() {
		sd.pauseScrolling();
		resumeButton.setVisibility(View.VISIBLE);
	}

	public void resumeScrolling() {
		sd = new SpectrogramDrawer(this);
		resumeButton.setVisibility(View.GONE);
		selectRectTextView.setVisibility(View.GONE);
		selecting = false;
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
    		StoredBitmapAudio sba = new StoredBitmapAudio(filename,STORE_DIR_NAME,bitmapToStore,audioToStore,lc.getLastLocation());
    		sba.store();
			return null;
		}
    }
    
    protected void updateColourMap() {
    	sd.getBitmapGenerator().updateColourMap();
    }
    
    protected void updateContrast() {
    	sd.getBitmapGenerator().updateContrast();
    }
}
