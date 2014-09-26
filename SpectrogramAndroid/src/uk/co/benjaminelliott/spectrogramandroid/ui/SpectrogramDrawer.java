package uk.co.benjaminelliott.spectrogramandroid.ui;

import java.util.concurrent.locks.ReentrantLock;

import uk.co.benjaminelliott.spectrogramandroid.audioproc.BitmapGenerator;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.util.Log;
import android.view.SurfaceHolder;

class SpectrogramDrawer {
	private final int SAMPLE_RATE;
	private final int HORIZONTAL_STRETCH = 2;
	private final float VERTICAL_STRETCH;
	private final int SAMPLES_PER_WINDOW;
	private final int NUM_FREQ_BINS;
	private int SCROLL_SHADOW_INV_SPREAD = 8; //decrease for a larger shadow
	private final ReentrantLock scrollingLock = new ReentrantLock(false);
	private BitmapGenerator bg;
	private LiveSpectrogramSurfaceView lssv;
	private Thread scrollingThread;
	private Canvas displayCanvas;
	private Bitmap buffer;
	private Canvas bufferCanvas;
	private Bitmap buffer2; //need to use this when shifting to the left because of a bug in Android, see http://stackoverflow.com/questions/6115695/android-canvas-gives-garbage-when-shifting-a-bitmap
	private Canvas buffer2Canvas;
	private Bitmap leftShadow;
	private Bitmap rightShadow;
	private int width;
	private int height;
	private int windowsDrawn;
	private int leftmostWindow;
	private boolean canScroll = false;
	private int leftmostBitmapAvailable;
	private int rightmostBitmapAvailable;
	private boolean running = true;
	private Matrix scaleMatrix;
	private int windowsAvailable = 0;
	private Bitmap unscaledBitmap;
	private int oldestBitmapAvailable;
	
	//declare reused variables here to reduce GC
	private boolean drawLeftShadow;
	private boolean drawRightShadow;
	private int leftmostWindowAsIndex;
	private int rightmostWindow;
	private int rightmostWindowAsIndex;
	private SurfaceHolder sh;
	
	private final int HALF_CORNER_WIDTH_OUTER = 20;
	private final int HALF_CORNER_WIDTH_INNER = 18;



	public SpectrogramDrawer(LiveSpectrogramSurfaceView lssv) {
		this.lssv = lssv;
		this.width = lssv.getWidth();
		this.height = lssv.getHeight();
		bg = new BitmapGenerator(lssv.getContext());
		SAMPLE_RATE = bg.getSampleRate();
		SAMPLES_PER_WINDOW = bg.getSamplesPerWindow();
		NUM_FREQ_BINS = bg.getNumFreqBins();
		VERTICAL_STRETCH = ((float)height)/((float)NUM_FREQ_BINS); // stretch spectrogram to all of available height
		Log.d("dim","Height: "+height+", num freq bins: "+NUM_FREQ_BINS+", VERTICAL_STRETCH: "+VERTICAL_STRETCH);
		buffer = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
		bufferCanvas = new Canvas(buffer);
		buffer2 = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
		buffer2Canvas = new Canvas(buffer2);
		scrollingThread = new Thread() {
			@Override
			public void run() {
				while (running) scroll();
				Log.d("SCROLL","Running false, scroll terminating");
			}
		};
		scrollingThread.setName("Scrolling thread");
		generateScrollShadow();
		scaleMatrix = generateScaleMatrix(1,NUM_FREQ_BINS,HORIZONTAL_STRETCH, NUM_FREQ_BINS * VERTICAL_STRETCH); //generate matrix to scale by horiz/vert scale params
		clearCanvas();
		scrollingThread.start();
		bg.start(); //start scrolling thread before generator to stop 'jumping' when scrolling is resumed
	}

	private void clearCanvas() {
		/*
		 * Clear the display by painting it black.
		 */
		sh = lssv.getHolder();
		displayCanvas = sh.lockCanvas(null);
		try {
			bufferCanvas.drawColor(Color.BLACK);
			synchronized (sh) {
				displayCanvas.drawBitmap(buffer, 0, 0, null); //draw buffer to display
			}
		} finally {
			if (displayCanvas != null) {
				sh.unlockCanvasAndPost(displayCanvas);
			}
		}
	}

	public void scroll() {
		/*
		 * Repeatedly run the quickProgress() method to look for new bitmaps, then draw the result to the display
		 */
			if (scrollingLock.tryLock()) {
				sh = lssv.getHolder();
				displayCanvas = sh.lockCanvas(null);
				try {
					quickProgress(); //update buffer bitmap
					synchronized (sh) {
						displayCanvas.drawBitmap(buffer, 0, 0, null); //draw buffer to display
						displayCanvas.drawBitmap(leftShadow, 0,  0, null); //draw scrolling shadow bitmaps on top
					}
				} finally {
					if (displayCanvas != null) {
						sh.unlockCanvasAndPost(displayCanvas);
					}
					scrollingLock.unlock();
				}
		}
	}

	public void quickSlide(int offset) {
		/*
		 * Takes a pixel offset, converts it into a number of windows by which to scroll the screen,
		 * then scrolls the screen so long as the appropriate windows are available.
		 */
		if (canScroll) { //only scroll if there are more than a screen's worth of windows
			//stop new windows from coming in immediately
			offset /= HORIZONTAL_STRETCH; //convert from pixel offset to window offset 
			oldestBitmapAvailable = bg.getOldestBitmapAvailable();
			drawLeftShadow = true;
			drawRightShadow = true;
			if (offset > BitmapGenerator.WINDOW_LIMIT/2) offset = BitmapGenerator.WINDOW_LIMIT/2;
			if (offset < -BitmapGenerator.WINDOW_LIMIT/2) offset = -BitmapGenerator.WINDOW_LIMIT/2;
			leftmostWindowAsIndex = leftmostWindow % BitmapGenerator.WINDOW_LIMIT;
			rightmostWindow = leftmostWindow + width/HORIZONTAL_STRETCH;
			rightmostWindowAsIndex = rightmostWindow % BitmapGenerator.WINDOW_LIMIT;

			if (rightmostWindow - offset >= windowsDrawn) {
				offset = -(windowsDrawn - rightmostWindow);
				drawRightShadow = false;
			}
			if (leftmostWindow - offset <= oldestBitmapAvailable) {
				offset = leftmostWindow - oldestBitmapAvailable;
				drawLeftShadow = false;
			}
			if (offset > 0) { //slide leftwards
				if (leftmostWindowAsIndex != leftmostBitmapAvailable) {
				buffer2Canvas.drawBitmap(buffer, HORIZONTAL_STRETCH
						* offset, 0, null);//shift current display to the right by HORIZONTAL_STRETCH*offset pixels
				bufferCanvas.drawBitmap(buffer2, 0, 0, null); //must copy to a second buffer first due to a bug in Android source
				leftmostWindowAsIndex = Math.abs(leftmostWindowAsIndex - offset);
				for (int i = 0; i < offset; i++) {
					drawSingleBitmap((leftmostWindowAsIndex + i)%BitmapGenerator.WINDOW_LIMIT, i
							* HORIZONTAL_STRETCH); //draw windows from x = 0 to x = HORIZONTAL_STRETCH*offset
				}
				leftmostWindow -= offset;
				}
			} else { //slide rightwards
				offset = -offset; //change to positive for convenience
				if (rightmostWindowAsIndex != rightmostBitmapAvailable) {
				bufferCanvas.drawBitmap(buffer, -HORIZONTAL_STRETCH
						* offset, 0, null);//shift current display to the left by HORIZONTAL_STRETCH*offset pixels
				for (int i = 0; i < offset; i++) {
					drawSingleBitmap((rightmostWindowAsIndex + i)%BitmapGenerator.WINDOW_LIMIT, width
							+ HORIZONTAL_STRETCH * (i - offset)); //draw windows from x=width+HORIZONTAL_STRETCH*(i-offset).
				}
				leftmostWindow += offset;
				}
			}
			sh = lssv.getHolder();
			displayCanvas = sh.lockCanvas(null);
			try {
				synchronized (sh) {
					displayCanvas.drawBitmap(buffer, 0, 0, null); //draw buffer to display
					if (drawLeftShadow) displayCanvas.drawBitmap(leftShadow, 0,  0, null); //draw scrolling shadow bitmaps on top
					if (drawRightShadow) displayCanvas.drawBitmap(rightShadow, 0,  0, null);
				}
			} finally {
				if (displayCanvas != null) {
					sh.unlockCanvasAndPost(displayCanvas);
				}
			}
		}
	}
	
	

	private void quickProgress() {
		/*
		 * This method shifts the bitmap displayed in the previous frame
		 * and then draws the new windows on the right hand side.
		 */
		windowsAvailable = bg.getBitmapWindowsAvailable();

		if ((windowsDrawn+windowsAvailable) * HORIZONTAL_STRETCH >= width) { 
			canScroll = true; //can only scroll if whole screen has been filled
			leftmostWindow += windowsAvailable;
		}
		bufferCanvas.drawBitmap(buffer, -HORIZONTAL_STRETCH*windowsAvailable, 0, null);//shift what is currently displayed by (number of new windows ready to be drawn)*HORIZONTAL_STRETCH
		for (int i = 0; i < windowsAvailable; i++) {
			drawNextBitmap(width+HORIZONTAL_STRETCH*(i-windowsAvailable)); //draw new window at width - HORIZONTAL_STRETCH * difference [start of blank area] + i*HORIZONTAL_STRETCH [offset for current window]
		}

		windowsDrawn += windowsAvailable;
	}

	private void drawNextBitmap(int xCoord) {
		/*
		 * Retreive and draw the next bitmap, determined by the BitmapGenerator itself.
		 */
		unscaledBitmap = Bitmap.createBitmap(bg.getNextBitmap(), 0, 1, 1, NUM_FREQ_BINS, Bitmap.Config.ARGB_8888);
		bufferCanvas.drawBitmap(scaleBitmap(unscaledBitmap), xCoord, 0f, null);
	}

	
	private void drawSingleBitmap(int index, int xCoord) {
		/*
		 * Draw the bitmap specified by the provided index from the top of the screen at the provided x-coordinate, 
		 * stretching according to the HORIZONTAL_STRETCH and VERTICAL_STRETCH parameters.
		 */
		unscaledBitmap = Bitmap.createBitmap(bg.getBitmapWindow(index), 0, 1, 1, NUM_FREQ_BINS, Bitmap.Config.ARGB_8888);
		bufferCanvas.drawBitmap(scaleBitmap(unscaledBitmap), xCoord, 0f, null);
	}
	
	float rectL;
	float rectR;
	float rectT;
	float rectB;
	int CORNER_RECT_DISTANCE = 40;
	public void drawAlanSelectRect(float selectRectL, float selectRectR, float selectRectT, float selectRectB) {
		/*
		 * Draw the select-area rectangle with left, right, top and bottom coordinates at selectRectL, selectRectR, 
		 * selectRectT and selectRectB respectively. Colour according to the SELECT_RECT_COLOUR value.
		 */
		Bitmap buf = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
		Canvas bufCanvas = new Canvas(buf);
		Paint rectPaint = new Paint();

		
		Paint cornerPaint = new Paint();
		
		int halfCornerDiam = 20;		
		cornerPaint.setColor(Color.BLACK);
		cornerPaint.setStrokeWidth(5);

		rectL = selectRectL + CORNER_RECT_DISTANCE;
		rectR = selectRectR - CORNER_RECT_DISTANCE;
		rectT = selectRectT + CORNER_RECT_DISTANCE;
		rectB = selectRectB - CORNER_RECT_DISTANCE;
		
		rectPaint.setColor(Color.BLACK);
		rectPaint.setStrokeWidth(10);
		bufCanvas.drawLine(rectL, rectB, rectR, rectB, rectPaint);
		bufCanvas.drawLine(rectR, rectB, rectR, rectT, rectPaint);
		bufCanvas.drawLine(rectL, rectT, rectR, rectT, rectPaint);
		bufCanvas.drawLine(rectL, rectB, rectL, rectT, rectPaint);
		
		
		bufCanvas.drawLine(selectRectL, selectRectB, rectL, rectB, cornerPaint);
		bufCanvas.drawLine(selectRectL, selectRectT, rectL, rectT, cornerPaint);
		bufCanvas.drawLine(selectRectR, selectRectB, rectR, rectB, cornerPaint);
		bufCanvas.drawLine(selectRectR, selectRectT, rectR, rectT, cornerPaint);
		
		rectPaint.setColor(Color.WHITE);
		rectPaint.setStrokeWidth(6);
		bufCanvas.drawLine(rectL, rectB, rectR, rectB, rectPaint);
		bufCanvas.drawLine(rectR, rectB, rectR, rectT, rectPaint);
		bufCanvas.drawLine(rectL, rectT, rectR, rectT, rectPaint);
		bufCanvas.drawLine(rectL, rectB, rectL, rectT, rectPaint);

		
		//draw draggable corners

		bufCanvas.drawRect(selectRectL-halfCornerDiam, selectRectB+halfCornerDiam, selectRectL+halfCornerDiam, selectRectB-halfCornerDiam, cornerPaint);
		bufCanvas.drawRect(selectRectR-halfCornerDiam, selectRectB+halfCornerDiam, selectRectR+halfCornerDiam, selectRectB-halfCornerDiam, cornerPaint);
		bufCanvas.drawRect(selectRectL-halfCornerDiam, selectRectT+halfCornerDiam, selectRectL+halfCornerDiam, selectRectT-halfCornerDiam, cornerPaint);
		bufCanvas.drawRect(selectRectR-halfCornerDiam, selectRectT+halfCornerDiam, selectRectR+halfCornerDiam, selectRectT-halfCornerDiam, cornerPaint);



		halfCornerDiam = 18;
		cornerPaint.setColor(Color.WHITE);
		bufCanvas.drawRect(selectRectL-halfCornerDiam, selectRectB+halfCornerDiam, selectRectL+halfCornerDiam, selectRectB-halfCornerDiam, cornerPaint);
		bufCanvas.drawRect(selectRectR-halfCornerDiam, selectRectB+halfCornerDiam, selectRectR+halfCornerDiam, selectRectB-halfCornerDiam, cornerPaint);
		bufCanvas.drawRect(selectRectL-halfCornerDiam, selectRectT+halfCornerDiam, selectRectL+halfCornerDiam, selectRectT-halfCornerDiam, cornerPaint);
		bufCanvas.drawRect(selectRectR-halfCornerDiam, selectRectT+halfCornerDiam, selectRectR+halfCornerDiam, selectRectT-halfCornerDiam, cornerPaint);

		sh = lssv.getHolder();
		displayCanvas = sh.lockCanvas(null);
		try {
			synchronized (sh) {
				displayCanvas.drawBitmap(buffer, 0, 0, null); //clean any old rectangles away
				displayCanvas.drawBitmap(buf, 0, 0, null); //draw new rectangle to display buffer
			}
		} finally {
			if (displayCanvas != null) {
				sh.unlockCanvasAndPost(displayCanvas);
			}
		}
	}

	
	private Bitmap buf;
	private Canvas bufCanvas;
	private Paint rectPaint;
	
	public void drawSelectRect(float selectRectL, float selectRectR, float selectRectT, float selectRectB) {
		/*
		 * Draw the select-area rectangle with left, right, top and bottom coordinates at selectRectL, selectRectR, 
		 * selectRectT and selectRectB respectively. Colour according to the SELECT_RECT_COLOUR value.
		 */
		buf = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
		bufCanvas = new Canvas(buf);
		rectPaint = new Paint();

		//draw select-area rectangle
		//rectPaint.setColor(SELECT_RECT_COLOUR);
		//bufCanvas.drawRect(selectRectL, selectRectT, selectRectR, selectRectB, rectPaint);
		rectPaint.setColor(Color.BLACK);
		rectPaint.setStrokeWidth(10);
		bufCanvas.drawLine(selectRectL, selectRectB, selectRectR, selectRectB, rectPaint);
		bufCanvas.drawLine(selectRectR, selectRectB, selectRectR, selectRectT, rectPaint);
		bufCanvas.drawLine(selectRectL, selectRectT, selectRectR, selectRectT, rectPaint);
		bufCanvas.drawLine(selectRectL, selectRectB, selectRectL, selectRectT, rectPaint);
		rectPaint.setColor(Color.WHITE);
		rectPaint.setStrokeWidth(6);
		bufCanvas.drawLine(selectRectL, selectRectB, selectRectR, selectRectB, rectPaint);
		bufCanvas.drawLine(selectRectR, selectRectB, selectRectR, selectRectT, rectPaint);
		bufCanvas.drawLine(selectRectL, selectRectT, selectRectR, selectRectT, rectPaint);
		bufCanvas.drawLine(selectRectL, selectRectB, selectRectL, selectRectT, rectPaint);
		
		//draw draggable corners
		Paint cornerPaint = new Paint();
		cornerPaint.setColor(Color.BLACK);
		bufCanvas.drawRect(selectRectL-HALF_CORNER_WIDTH_OUTER, selectRectB+HALF_CORNER_WIDTH_OUTER, selectRectL+HALF_CORNER_WIDTH_OUTER, selectRectB-HALF_CORNER_WIDTH_OUTER, cornerPaint);
		bufCanvas.drawRect(selectRectR-HALF_CORNER_WIDTH_OUTER, selectRectB+HALF_CORNER_WIDTH_OUTER, selectRectR+HALF_CORNER_WIDTH_OUTER, selectRectB-HALF_CORNER_WIDTH_OUTER, cornerPaint);
		bufCanvas.drawRect(selectRectL-HALF_CORNER_WIDTH_OUTER, selectRectT+HALF_CORNER_WIDTH_OUTER, selectRectL+HALF_CORNER_WIDTH_OUTER, selectRectT-HALF_CORNER_WIDTH_OUTER, cornerPaint);
		bufCanvas.drawRect(selectRectR-HALF_CORNER_WIDTH_OUTER, selectRectT+HALF_CORNER_WIDTH_OUTER, selectRectR+HALF_CORNER_WIDTH_OUTER, selectRectT-HALF_CORNER_WIDTH_OUTER, cornerPaint);

		cornerPaint.setColor(Color.WHITE);
		bufCanvas.drawRect(selectRectL-HALF_CORNER_WIDTH_INNER, selectRectB+HALF_CORNER_WIDTH_INNER, selectRectL+HALF_CORNER_WIDTH_INNER, selectRectB-HALF_CORNER_WIDTH_INNER, cornerPaint);
		bufCanvas.drawRect(selectRectR-HALF_CORNER_WIDTH_INNER, selectRectB+HALF_CORNER_WIDTH_INNER, selectRectR+HALF_CORNER_WIDTH_INNER, selectRectB-HALF_CORNER_WIDTH_INNER, cornerPaint);
		bufCanvas.drawRect(selectRectL-HALF_CORNER_WIDTH_INNER, selectRectT+HALF_CORNER_WIDTH_INNER, selectRectL+HALF_CORNER_WIDTH_INNER, selectRectT-HALF_CORNER_WIDTH_INNER, cornerPaint);
		bufCanvas.drawRect(selectRectR-HALF_CORNER_WIDTH_INNER, selectRectT+HALF_CORNER_WIDTH_INNER, selectRectR+HALF_CORNER_WIDTH_INNER, selectRectT-HALF_CORNER_WIDTH_INNER, cornerPaint);


		sh = lssv.getHolder();
		displayCanvas = sh.lockCanvas(null);
		try {
			synchronized (sh) {
				displayCanvas.drawBitmap(buffer, 0, 0, null); //clean any old rectangles away
				displayCanvas.drawBitmap(buf, 0, 0, null); //draw new rectangle to display buffer
			}
		} finally {
			if (displayCanvas != null) {
				sh.unlockCanvasAndPost(displayCanvas);
			}
		}
	}

	public void pauseScrolling() {
		/*
		 * When this method is called, scrolling of the display is halted, as are the threads which bring in and
		 * process audio samples to generate bitmaps.
		 */
		running = false;
		bg.stop(); //stop taking in and processing new samples since this will overwrite those you are trying to scroll through
		leftmostBitmapAvailable = bg.getLeftmostBitmapAvailable();
		rightmostBitmapAvailable = bg.getRightmostBitmapAvailable();
		quickSlide(0); //force the shadows to be drawn immediately
	}
	
	private Matrix generateScaleMatrix(float origWidth, float origHeight, float newWidth, float newHeight) {
		/*
		 * Returns a matrix for scaling bitmaps from origWidth, origHeight to newWidth, newHeight
		 */

		Matrix matrix = new Matrix();
		matrix.postScale(newWidth / origWidth, newHeight / origHeight);

		return matrix;
	}

	public Bitmap scaleBitmap(Bitmap bitmapToScale) {
		/*
		 * Returns the provided bitmap, scaled to fit the new width and height as determined by the horiz/vert scale parameters
		 */

		return Bitmap.createBitmap(bitmapToScale, 0, 0, bitmapToScale.getWidth(), bitmapToScale.getHeight(), scaleMatrix, true);
	}

	public float getScreenFillTime() {
		/*
		 * Returns the amount of time it takes to fill the entire width of the screen with bitmap windows.
		 */

		//no. windows on screen = width/HORIZONTAL_STRETCH,
		//no. samples on screen = no. windows * samplesPerWindow
		//time on screen = no. samples / samples per second [sample rate]
		return ((float)width/(float)HORIZONTAL_STRETCH*(float)SAMPLES_PER_WINDOW)/(float)SAMPLE_RATE;
	}

	public float getMaxFrequency() {
		/*
		 * Returns the maximum frequency that can be displayed on the spectrogram, which, due
		 * to the Nyquist limit, is half the sample rate.
		 */
		return 0.5f*SAMPLE_RATE;
	}
	
	protected int getWindowAtPixel(float pixelOffset) {
		/*
		 * Returns the window number associated with the horizontal pixel 
		 * offset (pixelOffset = 0 at the left side of the spectrogram)
		 */
		if (pixelOffset < 0) return leftmostWindow;
		if (pixelOffset > width) pixelOffset = width;
		float windowsOnScreen = ((float)width)/((float)HORIZONTAL_STRETCH); //number of windows that can fit on entire screen
		if (canScroll) return (int)(leftmostWindow + ((pixelOffset/width) * windowsOnScreen)); //screen is filled with samples
		int ret = (int)(windowsDrawn - (windowsOnScreen-(pixelOffset/width) * windowsOnScreen));
		if (ret > 0) return ret;
		return 0;
	}
	
	protected float getTimeAtPixel(float pixelOffset) {
		/*
		 * Returns the time offset associated with the horizontal pixel 
		 * offset (pixelOffset = 0 at the left side of the spectrogram)
		 */
		if (pixelOffset < 0) return getScreenFillTime();
		if (pixelOffset > width) return width;
		int windowOffset = getWindowAtPixel(pixelOffset)-windowsDrawn;
		float windowsOnScreen = ((float)width)/((float)HORIZONTAL_STRETCH); //number of windows that can fit on entire screen
		return (getScreenFillTime()/windowsOnScreen)*(float)windowOffset; //time per window * windows difference
	}
	
	protected float getTimeFromStartAtPixel(float pixelOffset) {
		float windowsOnScreen = ((float)width)/((float)HORIZONTAL_STRETCH); //number of windows that can fit on entire screen
		return (getScreenFillTime()/windowsOnScreen)*getWindowAtPixel(pixelOffset);
	}
	
	protected float getTimeFromStopAtPixel(float pixelOffset) {
		float windowsOnScreen = ((float)width)/((float)HORIZONTAL_STRETCH); //number of windows that can fit on entire screen
		return -(getScreenFillTime()/windowsOnScreen)*(windowsDrawn-getWindowAtPixel(pixelOffset));
	}
	
	protected int getFrequencyAtPixel(float pixelOffset) {
		/*
		 * Returns the frequency associated with the vertical pixel 
		 * offset (pixelOffset = 0 at the top of the spectrogram)
		 */
		if (pixelOffset < 0) pixelOffset = 0;
		if (pixelOffset > height) pixelOffset = height;
		return (int)(getMaxFrequency() - (pixelOffset/height)*getMaxFrequency());
	}

	public void hideSelectRect() {
		sh = lssv.getHolder();
		displayCanvas = sh.lockCanvas(null);
		try {
			synchronized (sh) {
				displayCanvas.drawBitmap(buffer, 0, 0, null); //clean any rectangles away
			}
		} finally {
			if (displayCanvas != null) {
				sh.unlockCanvasAndPost(displayCanvas);
			}
		}
		
	}

	protected Bitmap getBitmapToStore(float x0, float x1, float y0, float y1) {
		
		int startWindow;
		int endWindow;
		if (x0 < x1) {
			startWindow = getWindowAtPixel(x0);
			endWindow = getWindowAtPixel(x1);
		} else {
			startWindow = getWindowAtPixel(x1);
			endWindow = getWindowAtPixel(x0);
		}
		
		int topFreq;
		int bottomFreq;
		
		if (y0 < y1) {
			//remember that for y-coordinates, higher means lower down screen
			topFreq = getFrequencyAtPixel(y0);
			bottomFreq = getFrequencyAtPixel(y1);
		} else {
			topFreq = getFrequencyAtPixel(y1);
			bottomFreq = getFrequencyAtPixel(y0);
		}
		
		Log.d("SD","Top freq: "+topFreq+" bottomFreq: "+bottomFreq+" y0: "+y0+" y1: "+y1);
		//don't copy directly from the display canvas since that bitmap has been stretched depending on
		//device screen size, and also since we want to filter		

		return bg.createEntireBitmap(startWindow, endWindow, bottomFreq, topFreq);
	}

	protected short[] getAudioToStore(float x0, float x1,
			float y0, float y1) {
		int startWindow;
		int endWindow;
		if (x0 < x1) {
			startWindow = getWindowAtPixel(x0);
			endWindow = getWindowAtPixel(x1);
		} else {
			startWindow = getWindowAtPixel(x1);
			endWindow = getWindowAtPixel(x0);
		}
		int bottomFreq;
		int topFreq;
		if (y0 < y1) {
			//remember that for y-coordinates, higher means lower down screen
			topFreq = getFrequencyAtPixel(y0);
			bottomFreq = getFrequencyAtPixel(y1);
		} else {
			topFreq = getFrequencyAtPixel(y1);
			bottomFreq = getFrequencyAtPixel(y0);
		}
		return bg.getAudioChunk(startWindow, endWindow, bottomFreq, topFreq);
	}
	
	protected BitmapGenerator getBitmapGenerator() {
		return bg;
	}
	
	private void generateScrollShadow() {
		leftShadow = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
		Canvas leftShadowCanvas = new Canvas(leftShadow);
		rightShadow = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
		Canvas rightShadowCanvas = new Canvas(rightShadow);
		Paint paint = new Paint();
		paint.setColor(Color.BLACK);
		int spread = width/SCROLL_SHADOW_INV_SPREAD;
		for (int i = 0; i < spread; i++) {
			paint.setAlpha((spread - i)*255/spread); // becomes more transparent closer to centre
			leftShadowCanvas.drawRect(i, 0, i+1, height, paint); //draw left shadow
			rightShadowCanvas.drawRect(width-i-1, 0, width-i, height, paint); //draw right shadow
		}
	}

	public void stop() {
		running = false;
		bg.stop();
		Log.d("SD","STOPPED");
	}
	
	public void start() {
		running = true;
		scrollingThread.start();
		bg.start();
		Log.d("SD","STARTED");
	}
	
	
}