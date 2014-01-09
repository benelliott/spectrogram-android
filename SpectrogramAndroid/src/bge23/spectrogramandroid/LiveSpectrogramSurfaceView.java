package bge23.spectrogramandroid;

import android.content.Context;
import android.graphics.Canvas;
import android.support.v4.view.MotionEventCompat;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

public class LiveSpectrogramSurfaceView extends SurfaceView implements SurfaceHolder.Callback {

	private int windowsDrawn = 0; //how many windows have been drawn already
	private SurfaceHolder sh;
	private DrawingThread dt;
	private Canvas displayCanvas;
	//private ScaleGestureDetector sgd;
	private int mActivePointerId = MotionEvent.INVALID_POINTER_ID; //TODO
	private float lastTouchX;


	public LiveSpectrogramSurfaceView(Context context) { //Constructor for displaying audio from microphone
		super(context);
		displayCanvas = null;
		sh = getHolder();
		sh.addCallback(this);
	}


	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height)  {      
		dt.setSurfaceSize(width, height);
	}

	@Override
	public void surfaceCreated(SurfaceHolder arg0) {
		dt = new DrawingThread(sh,displayCanvas, getWidth(), getHeight());
		dt.start();
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder arg0) {
		dt.setRunning(false);
		//TODO: worry about interrupted something something something
	}

	@Override
	public boolean onTouchEvent(MotionEvent ev) {
		// Let the ScaleGestureDetector inspect all events.
		//sgd.onTouchEvent(ev);

		final int action = MotionEventCompat.getActionMasked(ev); 

		switch (action) { 
		case MotionEvent.ACTION_DOWN: { //finger pressed on screen
			final int pointerIndex = MotionEventCompat.getActionIndex(ev); 
			final float x = MotionEventCompat.getX(ev, pointerIndex); 

			// Remember where we started (for dragging)
			lastTouchX = x;
			// Save the ID of this pointer (for dragging) //TODO i think a pointer is a finger. want to save it so we can ignore other pointer (finger) actions
			mActivePointerId = MotionEventCompat.getPointerId(ev, 0);
			dt.setRunning(false); //stop new windows from coming in immediately
			break;
		}

		case MotionEvent.ACTION_MOVE: { //occurs when there is a difference between ACTION_UP and ACTION_DOWN
			// Find the index of the active pointer and fetch its position
			//TODO want to move the display by the same number of horizontal pixels as the difference in the user's action??
			final int pointerIndex = MotionEventCompat.findPointerIndex(ev, mActivePointerId);  

			final float x = MotionEventCompat.getX(ev, pointerIndex); //Note: never care about y axis
			//final float x = ev.getRawX(); //Note: never care about y axis


			// Calculate the distance moved
			final float dx = x - lastTouchX;



//			leftmostWindow += (dx*100);
//			lastTouchX = x;
//
//			if (leftmostWindow < 0) {
//				leftmostWindow = 0; //draw from start if dragged too far to the left
//			}
//			else if (leftmostWindow > windowsDrawn - width/horizontalStretch) {
//				leftmostWindow = windowsDrawn - width/horizontalStretch; //draw most recent if dragged too far to the right
//			}
			System.out.println("Drag event detected, dx is "+dx+", leftmostWindow is DELETED, windowsDrawn is "+windowsDrawn);
//			dt.fillScreenFrom(leftmostWindow); //fill screen from new X
			dt.quickSlide((int)dx);

			// Remember this touch position for the next move event

			break;
		}

		case MotionEvent.ACTION_UP: {
			mActivePointerId = MotionEvent.INVALID_POINTER_ID;
			break;
		}

		case MotionEvent.ACTION_CANCEL: {
			mActivePointerId = MotionEvent.INVALID_POINTER_ID;
			break;
		}

		case MotionEvent.ACTION_POINTER_UP: {

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


}
