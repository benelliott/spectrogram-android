package bge23.spectrogramandroid;

import java.util.concurrent.locks.ReentrantLock;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.util.Log;
import android.view.SurfaceHolder;

class SpectrogramDrawer {
	private final int HORIZONTAL_STRETCH = 2;
	private final float VERTICAL_STRETCH;
	private final int SAMPLES_PER_WINDOW;
	private int SELECT_RECT_COLOUR = Color.argb(127, 255, 255, 255);
	private final ReentrantLock scrollingLock = new ReentrantLock(false);
	private BitmapGenerator bg;
	private LiveSpectrogramSurfaceView lssv;
	private Thread scrollingThread;
	private Canvas displayCanvas;
	private Bitmap buffer;
	private Canvas bufferCanvas;
	private Bitmap buffer2; //need to use this when shifting to the left because of a bug in Android, see http://stackoverflow.com/questions/6115695/android-canvas-gives-garbage-when-shifting-a-bitmap
	private Canvas buffer2Canvas;
	private int width;
	private int height;
	private int windowsDrawn;
	private int leftmostWindow;
	private boolean canScroll = false;
	private int leftmostBitmapAvailable;
	private int rightmostBitmapAvailable;


	public SpectrogramDrawer(LiveSpectrogramSurfaceView lssv) {
		this.lssv = lssv;
		this.width = lssv.getWidth();
		this.height = lssv.getHeight();
		bg = new BitmapGenerator(lssv.getContext());
		bg.start();
		SAMPLES_PER_WINDOW = BitmapGenerator.SAMPLES_PER_WINDOW;
		VERTICAL_STRETCH = ((float)height)/((float)SAMPLES_PER_WINDOW); // stretch spectrogram to all of available height
		Log.d("dim","Height: "+height+", samples per window: "+SAMPLES_PER_WINDOW+", VERTICAL_STRETCH: "+VERTICAL_STRETCH);
		buffer = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
		bufferCanvas = new Canvas(buffer);
		buffer2 = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
		buffer2Canvas = new Canvas(buffer2);
		scrollingThread = new Thread() {
			@Override
			public void run() {
				scroll();
			}
		};
		clearCanvas();
		scrollingThread.start();
	}

	private void clearCanvas() {
		/*
		 * Clear the display by painting it black.
		 */
		SurfaceHolder sh = lssv.getHolder();
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
		while (true) {
			if (scrollingLock.tryLock()) {
				SurfaceHolder sh = lssv.getHolder();
				displayCanvas = sh.lockCanvas(null);
				try {
					quickProgress(); //update buffer bitmap
					synchronized (sh) {
						displayCanvas.drawBitmap(buffer, 0, 0, null); //draw buffer to display
					}
				} finally {
					if (displayCanvas != null) {
						sh.unlockCanvasAndPost(displayCanvas);
					}
					scrollingLock.unlock();
				}
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

			Log.d("Scrolling","Offset: "+offset);

			if (offset > BitmapGenerator.WINDOW_LIMIT/2) offset = BitmapGenerator.WINDOW_LIMIT/2;
			if (offset < -BitmapGenerator.WINDOW_LIMIT/2) offset = -BitmapGenerator.WINDOW_LIMIT/2;
			int leftmostWindowAsIndex = leftmostWindow % BitmapGenerator.WINDOW_LIMIT;
			int rightmostWindow = leftmostWindow + width/HORIZONTAL_STRETCH;
			int rightmostWindowAsIndex = rightmostWindow % BitmapGenerator.WINDOW_LIMIT;
			if (leftmostWindow - offset < 0) offset = leftmostWindow;
			if (rightmostWindow - offset > windowsDrawn) offset = windowsDrawn - rightmostWindow;
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
				if (rightmostWindowAsIndex != rightmostBitmapAvailable) {
				offset = -offset; //change to positive for convenience
				bufferCanvas.drawBitmap(buffer, -HORIZONTAL_STRETCH
						* offset, 0, null);//shift current display to the left by HORIZONTAL_STRETCH*offset pixels
				for (int i = 0; i < offset; i++) {
					drawSingleBitmap((rightmostWindowAsIndex + i)%BitmapGenerator.WINDOW_LIMIT, width
							+ HORIZONTAL_STRETCH * (i - offset)); //draw windows from x=width+HORIZONTAL_STRETCH*(i-offset).
				}
				leftmostWindow += offset;
				}
			}
			SurfaceHolder sh = lssv.getHolder();
			displayCanvas = sh.lockCanvas(null);
			try {
				synchronized (sh) {
					displayCanvas.drawBitmap(buffer, 0, 0, null); //draw buffer to display
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
		int windowsAvailable = bg.getBitmapWindowsAvailable();

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
		Bitmap orig = Bitmap.createBitmap(bg.getNextBitmap(), 0, 1, 1, SAMPLES_PER_WINDOW, Bitmap.Config.ARGB_8888);
		Bitmap scaled = scaleBitmap(orig, HORIZONTAL_STRETCH, SAMPLES_PER_WINDOW * VERTICAL_STRETCH);
		bufferCanvas.drawBitmap(scaled, xCoord, 0f, null);
	}

	private void drawSingleBitmap(int index, int xCoord) {
		/*
		 * Draw the bitmap specified by the provided index from the top of the screen at the provided x-coordinate, 
		 * stretching according to the HORIZONTAL_STRETCH and VERTICAL_STRETCH parameters.
		 */
		Bitmap orig = Bitmap.createBitmap(bg.getBitmapWindow(index), 0, 1, 1, SAMPLES_PER_WINDOW, Bitmap.Config.ARGB_8888);
		Bitmap scaled = scaleBitmap(orig, HORIZONTAL_STRETCH, SAMPLES_PER_WINDOW * VERTICAL_STRETCH);
		bufferCanvas.drawBitmap(scaled, xCoord, 0f, null);
		System.out.println("Window " + index
				+ " drawn with (left, top) coordinate at ("
				+ xCoord + "," + 0 + "), density "
				+ scaled.getDensity());
	}

	public void drawSelectRect(float selectRectL, float selectRectR, float selectRectT, float selectRectB) {
		/*
		 * Draw the select-area rectangle with left, right, top and bottom coordinates at selectRectL, selectRectR, 
		 * selectRectT and selectRectB respectively. Colour according to the SELECT_RECT_COLOUR value.
		 */
		Bitmap buf = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
		Canvas bufCanvas = new Canvas(buf);
		Paint rectPaint = new Paint();

		//draw select-area rectangle
		rectPaint.setColor(SELECT_RECT_COLOUR);

		bufCanvas.drawRect(selectRectL, selectRectT, selectRectR, selectRectB, rectPaint);

		//draw draggable corners
		Paint circPaint = new Paint();
		circPaint.setColor(Color.rgb(255,255,255));
		bufCanvas.drawCircle(selectRectL, selectRectB, 10, circPaint);
		bufCanvas.drawCircle(selectRectL, selectRectT, 10, circPaint);
		bufCanvas.drawCircle(selectRectR, selectRectB, 10, circPaint);
		bufCanvas.drawCircle(selectRectR, selectRectT, 10, circPaint);

		SurfaceHolder sh = lssv.getHolder();
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
		if (!scrollingLock.isHeldByCurrentThread()) {
			scrollingLock.lock();
		}
		bg.stop(); //stop taking in and processing new samples since this will overwrite those you are trying to scroll through
		leftmostBitmapAvailable = bg.getLeftmostBitmapAvailable();
		rightmostBitmapAvailable = bg.getRightmostBitmapAvailable();
	}

	public Bitmap scaleBitmap(Bitmap bitmapToScale, float newWidth, float newHeight) {
		/*
		 * Returns the provided bitmap, scaled to fit the new width and height parameters.
		 */
		if(bitmapToScale == null)
			return null;
		//get the original width and height
		int width = bitmapToScale.getWidth();
		int height = bitmapToScale.getHeight();
		// create a matrix for the manipulation
		Matrix matrix = new Matrix();

		// resize the bit map
		matrix.postScale(newWidth / width, newHeight / height);

		// recreate the new Bitmap and set it back
		return Bitmap.createBitmap(bitmapToScale, 0, 0, bitmapToScale.getWidth(), bitmapToScale.getHeight(), matrix, true);
	}

	public float getScreenFillTime() {
		/*
		 * Returns the amount of time it takes to fill the entire width of the screen with bitmap windows.
		 */

		//no. windows on screen = width/HORIZONTAL_STRETCH,
		//no. samples on screen = no. windows * samplesPerWindow
		//time on screen = no. samples / samples per second [sample rate]
		return ((float)width/(float)HORIZONTAL_STRETCH*(float)SAMPLES_PER_WINDOW)/(float)bg.getSampleRate();
	}

	public float getMaxFrequency() {
		/*
		 * Returns the maximum frequency that can be displayed on the spectrogram, which, due
		 * to the Nyquist limit, is half the sample rate.
		 */
		return 0.5f*bg.getSampleRate();
	}
	
	protected int getWindowAtPixel(float pixelOffset) {
		/*
		 * Returns the window number associated with the horizontal pixel 
		 * offset (pixelOffset = 0 at the left side of the spectrogram)
		 */
		if (pixelOffset < 0) return 0;
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
		if (pixelOffset < 0) return 0;
		if (pixelOffset > width) return width;
		int windowOffset = getWindowAtPixel(pixelOffset)-windowsDrawn;
		float windowsOnScreen = ((float)width)/((float)HORIZONTAL_STRETCH); //number of windows that can fit on entire screen
		return (getScreenFillTime()/windowsOnScreen)*(float)windowOffset; //time per window * windows difference
	}
	
	protected float getTimeFromStartAtPixel(float pixelOffset) {
		float windowsOnScreen = ((float)width)/((float)HORIZONTAL_STRETCH); //number of windows that can fit on entire screen
		return (getScreenFillTime()/windowsOnScreen)*getWindowAtPixel(pixelOffset);
	}
	
	protected int getFrequencyAtPixel(float pixelOffset) {
		/*
		 * Returns the frequency associated with the vertical pixel 
		 * offset (pixelOffset = 0 at the top of the spectrogram)
		 */
		if (pixelOffset < 0) return 0;
		if (pixelOffset > height) pixelOffset = height;
		return (int)(getMaxFrequency() - (pixelOffset/height)*getMaxFrequency());
	}

	public void hideSelectRect() {
		SurfaceHolder sh = lssv.getHolder();
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
		// TODO filtering
		int startWindow;
		int endWindow;
		if (x0 < x1) {
			startWindow = getWindowAtPixel(x0);
			endWindow = getWindowAtPixel(x1);
		} else {
			startWindow = getWindowAtPixel(x1);
			endWindow = getWindowAtPixel(x0);
		}
		return bg.getAudioChunk(startWindow, endWindow);
	}
	
	public int getSampleRate() {
		return bg.getSampleRate();
	}
	
	protected BitmapGenerator getBitmapGenerator() {
		return bg;
	}
	
	
}