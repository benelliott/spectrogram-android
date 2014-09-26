package uk.co.benjaminelliott.spectrogramandroid.ui;

import uk.co.benjaminelliott.spectrogramandroid.preferences.UiConfig;
import android.os.Handler;
import android.support.v4.view.MotionEventCompat;
import android.util.Log;
import android.view.MotionEvent;

public class InteractionHandler {

    private SpectrogramSurfaceView ssv;
    private Runnable onLongPress;
    private Handler handler;
    //allocate memory for reused variables here to reduce GC
    private float lastTouchX;
    private float lastTouchY;
    private int mActivePointerId;
    private float centreX;
    private float centreY;
    private int action;
    private int pointerIndex;
    private float x;
    private float y;
    private float dx;
    private float dy;

    private float selectRectL = 0;
    private float selectRectR = 0;
    private float selectRectT = 0;
    private float selectRectB = 0;

    private int selectedCorner;
    
    protected InteractionHandler (SpectrogramSurfaceView specSurfaceView) {
	ssv = specSurfaceView;
	onLongPress = new Runnable() {
	    public void run() {
		Log.d("", "Long press detected.");
		ssv.selecting = true;
		selectRectL = (centreX - UiConfig.SELECT_RECT_WIDTH/2 < 0) ? 0 : centreX - UiConfig.SELECT_RECT_WIDTH/2;
		selectRectR = (centreX + UiConfig.SELECT_RECT_WIDTH/2 > ssv.getWidth()) ? ssv.getWidth() : centreX + UiConfig.SELECT_RECT_WIDTH/2;
		selectRectT = (centreY - UiConfig.SELECT_RECT_HEIGHT/2 < 0) ? 0 : centreY - UiConfig.SELECT_RECT_HEIGHT/2;
		selectRectB = (centreY + UiConfig.SELECT_RECT_HEIGHT/2 > ssv.getHeight()) ? ssv.getHeight() : centreY + UiConfig.SELECT_RECT_HEIGHT/2;
		ssv.updateSelectRect(selectRectT, selectRectB, selectRectL, selectRectR);
		ssv.enableCaptureButtonContainer();
	    }
	};
	handler = new Handler();
    }

    public void handleTouchEvent(MotionEvent ev) {
	action = MotionEventCompat.getActionMasked(ev); 
	switch (action) {
	case MotionEvent.ACTION_DOWN:  //finger pressed on screen
	    handleDown(ev);
	    break;

	case MotionEvent.ACTION_MOVE:  //occurs when there is a difference between ACTION_UP and ACTION_DOWN
	    handleMove(ev);
	    break;

	case MotionEvent.ACTION_POINTER_UP:
	    handlePointerUp(ev);
	    break;
	
	default: //catch MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL
	    cancelLongpress();
	}

    }


    private void handleDown(MotionEvent ev) {
	if (!ssv.selecting) {
	    ssv.pauseScrolling();
	    handler.postDelayed(onLongPress, 500); //run the long-press runnable if not cancelled by move event (0.5 second timeout) [only if not already selecting]
	}
	pointerIndex = MotionEventCompat.getActionIndex(ev); 
	x = MotionEventCompat.getX(ev, pointerIndex); 
	centreX = MotionEventCompat.getX(ev, pointerIndex);
	centreY = MotionEventCompat.getY(ev, pointerIndex);
	// Remember where we started (for dragging)
	lastTouchX = x;
	lastTouchY = MotionEventCompat.getY(ev, pointerIndex);
	// Save the ID of this pointer [finger], in case of drag 
	mActivePointerId = MotionEventCompat.getPointerId(ev, 0);
	if (ssv.selecting) {
	    //decide which corner is being dragged based on proximity
	    selectedCorner = 0;
	    if (Math.abs(centreX-selectRectL) <= UiConfig.SELECT_RECT_CORNER_RADIUS && Math.abs(centreY-selectRectT) <= UiConfig.SELECT_RECT_CORNER_RADIUS) {
		//user touched top-left corner
		Log.d("","Top left");
		selectedCorner = 1;
	    }
	    if (Math.abs(centreX-selectRectR) <= UiConfig.SELECT_RECT_CORNER_RADIUS && Math.abs(centreY-selectRectT) <= UiConfig.SELECT_RECT_CORNER_RADIUS) {
		//user touched top-right corner
		Log.d("","Top right");
		selectedCorner = 2;
	    }
	    if (Math.abs(centreX-selectRectL) <= UiConfig.SELECT_RECT_CORNER_RADIUS && Math.abs(centreY-selectRectB) <= UiConfig.SELECT_RECT_CORNER_RADIUS) {
		//user touched bottom-left corner
		Log.d("","Bottom left");
		selectedCorner = 3;
	    }
	    if (Math.abs(centreX-selectRectR) <= UiConfig.SELECT_RECT_CORNER_RADIUS && Math.abs(centreY-selectRectB) <= UiConfig.SELECT_RECT_CORNER_RADIUS) {
		//user touched bottom-right corner
		Log.d("","Bottom right");
		selectedCorner = 4;
	    }
	    //if (selectedCorner == 0) cancelSelection(); un-comment this to enable sleection cancelling by tapping outside of rectangle
	}
    }

    private void handleMove(MotionEvent ev) {
	// Find the index of the active pointer and fetch its position
	pointerIndex = MotionEventCompat.findPointerIndex(ev,mActivePointerId);
	// Calculate the distance moved
	if (!ssv.selecting) { //don't allow for scrolling if user is trying to select an area of the spectrogram
	    x = MotionEventCompat.getX(ev, pointerIndex); //Note: never care about y axis
	    dx = x - lastTouchX;
	    if (dx > 5 || dx < -5) { //only if moved more than 5 pixels
		handler.removeCallbacks(onLongPress); //cancel long-press runnable
		ssv.slideTo((int) dx);
		// Remember this touch position for the next move event
	    }
	    lastTouchX = x;
	} else { 
	    //if selecting mode entered, allow user to move corners to adjust select-area rectangle size
	    x = MotionEventCompat.getX(ev, pointerIndex);
	    y = MotionEventCompat.getY(ev, pointerIndex);
	    dx = x - lastTouchX;
	    dy = y - lastTouchY;
	    moveSelectRectCorner(selectedCorner, dx, dy);				
	    lastTouchX = x;
	    lastTouchY = y;
	}
    }

    private void cancelLongpress() {
	handler.removeCallbacks(onLongPress); //cancel long-press runnable
	mActivePointerId = MotionEvent.INVALID_POINTER_ID;
    }

    private void handlePointerUp(MotionEvent ev) {
	cancelLongpress();
	pointerIndex = MotionEventCompat.getActionIndex(ev); 
	final int pointerId = MotionEventCompat.getPointerId(ev, pointerIndex); 

	if (pointerId == mActivePointerId) {
	    // This was our active pointer going up. Choose a new
	    // active pointer and adjust accordingly.
	    final int newPointerIndex = pointerIndex == 0 ? 1 : 0;
	    lastTouchX = MotionEventCompat.getX(ev, newPointerIndex); 
	    mActivePointerId = MotionEventCompat.getPointerId(ev, newPointerIndex);
	}
    }
        
    public void moveSelectRectCorner(int cornerIndex, float dx, float dy) {
	int width = ssv.getWidth();
	int height = ssv.getHeight();
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
	ssv.updateSelectRect(selectRectL, selectRectT, selectRectR, selectRectB);
    }

    /**
     * Returns the dimensions of the selection rectangle.
     * @return A float-array consisting of [selectRectL, selectRectT, selectRectR, selectRectB]
     */
    protected float[] getSelectRectDimensions() {
	float[] dimens = {selectRectL, selectRectT, selectRectR, selectRectB};
	return dimens;
    }

}
