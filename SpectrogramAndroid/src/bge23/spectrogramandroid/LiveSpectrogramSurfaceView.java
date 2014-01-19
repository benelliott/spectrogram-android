package bge23.spectrogramandroid;

import java.math.BigDecimal;

import android.content.Context;
import android.os.Handler;
import android.support.v4.view.MotionEventCompat;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class LiveSpectrogramSurfaceView extends SurfaceView implements SurfaceHolder.Callback {

	private SpectrogramDrawer sd;
	//private ScaleGestureDetector sgd;
	private int mActivePointerId = MotionEvent.INVALID_POINTER_ID;
	private float lastTouchX;
	private float lastTouchY;
	private Handler handler;
	Runnable onLongPress;
	private float centreX;
	private float centreY;
	private boolean selecting = false; //true if user has entered the selection state
	private int selectedCorner; // indicates which corner is being dragged; 0 is top-left, 1 is top-right, 2 is bottom-left, 3 is bottom-right
	private Button resumeButton;
	private TextView leftTimeTextView;
	private TextView topFreqTextView;
	
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
			}
		};
	}
	
	public void setResumeButton(Button resumeButton) {
		this.resumeButton = resumeButton;
	}
	
	public void setLeftTimeTextView(TextView leftTimeTextView) {
		this.leftTimeTextView = leftTimeTextView;
	}
	
	public void setTopFreqTextView(TextView topFreqTextView) {
		this.topFreqTextView = topFreqTextView;
	}


	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height)  {
		//TODO
	}

	@Override
	public void surfaceCreated(SurfaceHolder arg0) {
		sd = new SpectrogramDrawer(this);
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
			handler.postDelayed(onLongPress,1000); //run the long-press runnable if not cancelled by move event (1 second timeout)
			System.out.println("Long-press timer started.");
			// Remember where we started (for dragging)
			lastTouchX = x;
			System.out.println("Last touch x set to "+x);
			// Save the ID of this pointer [finger], in case of drag 
			mActivePointerId = MotionEventCompat.getPointerId(ev, 0);
			if (selecting) {
				//decide which corner is being dragged based on proximity
				if (Math.abs(centreX-selectRectL) <= CORNER_CIRCLE_RADIUS && Math.abs(centreY-selectRectT) <= CORNER_CIRCLE_RADIUS) {
					//user touched top-left corner
					Log.d("","Top left");
					selectedCorner = 0;
				}
				if (Math.abs(centreX-selectRectR) <= CORNER_CIRCLE_RADIUS && Math.abs(centreY-selectRectT) <= CORNER_CIRCLE_RADIUS) {
					//user touched top-right corner
					Log.d("","Top right");
					selectedCorner = 1;
				}
				if (Math.abs(centreX-selectRectL) <= CORNER_CIRCLE_RADIUS && Math.abs(centreY-selectRectB) <= CORNER_CIRCLE_RADIUS) {
					//user touched bottom-left corner
					Log.d("","Bottom left");
					selectedCorner = 2;
				}
				if (Math.abs(centreX-selectRectR) <= CORNER_CIRCLE_RADIUS && Math.abs(centreY-selectRectB) <= CORNER_CIRCLE_RADIUS) {
					//user touched bottom-right corner
					Log.d("","Bottom right");
					selectedCorner = 3;
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
				
				//first do historical movements
//				for (int i = 0; i < ev.getHistorySize(); i++) {
//					float x = ev.getHistoricalX(pointerIndex, i);
//					float y = ev.getHistoricalX(pointerIndex, i);
//					float dx = x - lastTouchX;
//					float dy = y - lastTouchX;
//					moveCorner(selectedCorner, dx, dy);				
//					lastTouchX = x;
//					lastTouchY = y;
//				}
				
				//then do current movement
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
		case 0:
			//top-left corner moved
			selectRectL += dx;
			selectRectT += dy;
			break;
		case 1:
			//top-right corner moved
			selectRectR += dx;
			selectRectT += dy;
			break;
		case 2:
			//bottom-left corner moved
			selectRectL += dx;
			selectRectB += dy;
			break;
		case 3:
			//bottom-right corner moved
			selectRectR += dx;
			selectRectB += dy;
			break;
		}
		sd.drawSelectRect(selectRectL,selectRectR,selectRectT,selectRectB);		
	}
	
	protected void pauseScrolling() {
		sd.pauseScrolling();
		resumeButton.setVisibility(View.VISIBLE);
	}
	
	public void resumeScrolling() {
		sd.resumeScrolling();
		resumeButton.setVisibility(View.GONE);
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
}
