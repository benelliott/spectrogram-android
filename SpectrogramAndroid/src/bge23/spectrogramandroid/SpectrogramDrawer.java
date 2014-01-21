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
		bg = new BitmapGenerator(1);
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

			int leftmostWindowAsIndex = leftmostWindow % BitmapGenerator.WINDOW_LIMIT;
			int newLeftmostWindow = leftmostWindowAsIndex - offset;
			if (newLeftmostWindow < leftmostBitmapAvailable) offset = leftmostWindowAsIndex - leftmostBitmapAvailable;

			int rightmostWindowAsIndex = (leftmostWindow + width/HORIZONTAL_STRETCH) % BitmapGenerator.WINDOW_LIMIT;
			int newRightmostWindow = rightmostWindowAsIndex - offset;
			if (newRightmostWindow > rightmostBitmapAvailable) offset = rightmostBitmapAvailable - rightmostWindowAsIndex;

			Log.d("Scrolling","Adjusted offset: "+offset+", leftmost window before scroll: "+leftmostWindow+", as index: "+leftmostWindowAsIndex);


			if (offset > 0) { //slide leftwards
				buffer2Canvas.drawBitmap(buffer, HORIZONTAL_STRETCH
						* offset, 0, null);//shift current display to the right by HORIZONTAL_STRETCH*offset pixels
				bufferCanvas.drawBitmap(buffer2, 0, 0, null); //must copy to a second buffer first due to a bug in Android source
				leftmostWindowAsIndex -= offset;
				for (int i = 0; i < offset; i++) {
					drawSingleBitmap(leftmostWindowAsIndex + i, i
							* HORIZONTAL_STRETCH); //draw windows from x = 0 to x = HORIZONTAL_STRETCH*offset
				}
				leftmostWindow -= offset;
			} else { //slide rightwards
				offset = -offset; //change to positive for convenience
				bufferCanvas.drawBitmap(buffer, -HORIZONTAL_STRETCH
						* offset, 0, null);//shift current display to the left by HORIZONTAL_STRETCH*offset pixels
				for (int i = 0; i < offset; i++) {
					drawSingleBitmap(rightmostWindowAsIndex + i, width
							+ HORIZONTAL_STRETCH * (i - offset)); //draw windows from x=width+HORIZONTAL_STRETCH*(i-offset).
				}
				leftmostWindow += offset;
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
	
	protected int windowAt(float pixelOffset) {
		/*
		 * Returns the window number associated with the horizontal pixel 
		 * offset (pixelOffset = 0 at the left side of the spectrogram)
		 */
		if (pixelOffset < 0 || pixelOffset > width) return 0;
		float windowsOnScreen = ((float)width)/((float)HORIZONTAL_STRETCH);
		return (int)(leftmostWindow + (pixelOffset/width * windowsOnScreen));
		
	}
	
	protected float timeAt(float pixelOffset) {
		/*
		 * Returns the time offset associated with the horizontal pixel 
		 * offset (pixelOffset = 0 at the left side of the spectrogram)
		 */
		if (pixelOffset < 0 || pixelOffset > width) return 0;
		return -(getScreenFillTime()*(width-pixelOffset)/width);
		
	}
	
	protected int frequencyAt(float pixelOffset) {
		/*
		 * Returns the frequency associated with the vertical pixel 
		 * offset (pixelOffset = 0 at the top of the spectrogram)
		 */
		if (pixelOffset < 0 || pixelOffset > height) return 0;
		return (int)(getMaxFrequency() - (pixelOffset/height)*getMaxFrequency());
	}
}