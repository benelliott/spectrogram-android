package bge23.spectrogramandroid;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.view.SurfaceHolder;

class DrawingThread extends Thread {
	private final int HORIZONTAL_STRETCH = 2;
	private final int VERTICAL_STRETCH = 1;
	private LiveSpectrogram spec;
	private SurfaceHolder sh;
	private boolean run = true;
	private Canvas displayCanvas;
	private Bitmap buffer;
	private Canvas bufferCanvas;
	private int width;
	private int height;
	private int samplesPerWindow;
	private int windowsDrawn;
	private int leftmostWindow;
	
	public DrawingThread(SurfaceHolder sh, Canvas displayCanvas, int width, int height) {
		super();
		System.out.println("Width: "+width+" height: "+height);
		this.sh = sh;
		this.displayCanvas = displayCanvas;
		this.width = width;
		this.height = height;
		spec = new LiveSpectrogram(true);
		spec.start();
		samplesPerWindow = spec.getSamplesPerWindow();
		buffer = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
		bufferCanvas = new Canvas(buffer);
	}


	public void doStart() { //TODO
		synchronized(sh){

		}
	}

	public void run() {
		while (run) {
			try {
				displayCanvas = sh.lockCanvas(null);
				synchronized (sh) {
					quickProgress(); //update buffer bitmap
					displayCanvas.drawBitmap(buffer, 0, 0, null); //draw buffer to display
				}
			} finally {
				if (displayCanvas != null) {
					sh.unlockCanvasAndPost(displayCanvas);
				}
			}
		}
	}


	public void setRunning(boolean b) {
		run = b;
	}

	public void fillScreenFrom(int leftmostBitmap) {
		/*
		 * Fill the screen with bitmaps from the leftmost bitmap specified to the right edge of the display.
		 */
		try {
			displayCanvas = sh.lockCanvas(null);
			synchronized (sh) {
				System.out.println("Filling screen from "+leftmostBitmap+" to "+(leftmostBitmap+(width/HORIZONTAL_STRETCH)+". Windows drawn: "+windowsDrawn));
				drawBitmapGroup(leftmostBitmap, leftmostBitmap+(width/HORIZONTAL_STRETCH)); //update buffer bitmap
				displayCanvas.drawBitmap(buffer, 0, 0, null); //draw buffer to display
			}
		} finally {
			if (displayCanvas != null) {
				sh.unlockCanvasAndPost(displayCanvas);
			}
		}
	}

	public void quickSlide(int offset) {
		//TODO 'too far's
		if (offset < 0) { //slide leftwards
			offset = -offset; //change to positive for convenience
			bufferCanvas.drawBitmap(buffer, HORIZONTAL_STRETCH*offset, 0, null);//shift current display to the right
			for (int i = 0; i < offset; i++) {
				drawSingleBitmap(leftmostWindow-offset+i,i*HORIZONTAL_STRETCH); //draw new windows from x = 0
			}
			leftmostWindow -= offset;
		} else { //slide rightwards
			bufferCanvas.drawBitmap(buffer, -HORIZONTAL_STRETCH*offset, 0, null);//shift current display to the left
			for (int i = 0; i < offset; i++) {
				drawSingleBitmap(leftmostWindow+width/HORIZONTAL_STRETCH,width+HORIZONTAL_STRETCH*(i-offset)); //draw new windows from x=width+HORIZONTAL_STRETCH*(i-offset)
			}
			leftmostWindow += offset;
		}
	}


	private void progress() {
		/*
		 * While recording, scroll the display as new samples come in without any user input
		 */
		int windowsAvailable = spec.getBitmapWindowsAdded(); //TODO concurrency - only read once
		if (windowsDrawn < windowsAvailable) { //new bitmap available to draw
			int difference = windowsAvailable - windowsDrawn;
			//System.out.println("Difference: "+difference+", windows drawn: "+windowsDrawn);
			if (windowsAvailable * HORIZONTAL_STRETCH < width) { //still room to draw new bitmaps without scrolling
				drawBitmapGroup(leftmostWindow, windowsDrawn+difference); //draw from first window to newest window
			}
			else { //no room, must scroll
				leftmostWindow += difference;
				drawBitmapGroup(leftmostWindow, windowsDrawn+difference);
			}
			windowsDrawn+= difference;
		}

	}

	private void quickProgress() {
		/*
		 * A (hopefully) better-performing version of the progress() method. The existing progress() method redraws every
		 * bitmap window that will be displayed, while this version simply shifts the bitmap displayed in the previous frame
		 * and then draws the new windows on the right hand side.
		 */
		int windowsAvailable = spec.getBitmapWindowsAdded(); //TODO concurrency - only read once
		if (windowsDrawn < windowsAvailable) { //new bitmap available to draw
			int difference = windowsAvailable - windowsDrawn;
			//System.out.println("Difference: "+difference+", windows drawn: "+ (windowsDrawn-1) +", windows available: "+windowsAvailable); //TODO note number of windows drawn is actually windowsDrawn-1 cos windowsDrawn is "most recent window drawn"
			if (windowsAvailable * HORIZONTAL_STRETCH < width) { //still room to draw new bitmaps without scrolling
				//leave the existing bitmap intact and just draw the new windows on the portion of the screen that is still blank
				//System.out.println("Room to draw without scrolling.");
				for (int i = windowsDrawn; i < windowsDrawn+difference; i++) {
					//System.out.println("FOR loop: drawing window "+i+" at "+i*HORIZONTAL_STRETCH);
					drawSingleBitmap(i,i*HORIZONTAL_STRETCH);
				}
			}
			else { //no room, must scroll (shift what is currently displayed then draw new windows)
				//System.out.println("Must scroll to draw since windowsAvailable*HORIZONTAL_STRETCH is "+windowsAvailable * HORIZONTAL_STRETCH+" and width is "+width+". Scrolling current display back by "+HORIZONTAL_STRETCH*difference+" pixels.");
				bufferCanvas.drawBitmap(buffer, -HORIZONTAL_STRETCH*difference, 0, null);//shift what is currently displayed by (number of new windows ready to be drawn)*HORIZONTAL_STRETCH
				for (int i = 0; i < difference; i++) {
					//System.out.println("FOR loop: drawing window "+(windowsDrawn+i)+" at "+(width-HORIZONTAL_STRETCH*difference+i));
					drawSingleBitmap(windowsDrawn+i,width+HORIZONTAL_STRETCH*(i-difference)); //draw new window at width - HORIZONTAL_STRETCH * difference [start of blank area] + i*HORIZONTAL_STRETCH [offset for current window]
				}
				leftmostWindow += difference;
			}
			windowsDrawn+= difference;

		}
		//else System.out.println("No windows to draw.");
	}

	private void quickProgress2() {
		/*
		 * 
		 */
		if ((windowsDrawn) * HORIZONTAL_STRETCH < width) { //still room to draw new bitmaps without scrolling
			drawNextBitmap(windowsDrawn);
		}
		else { //no room, must scroll (shift what is currently displayed then draw new windows)
			System.out.println("Must scroll to draw since (windowsDrawn) * HORIZONTAL_STRETCH is "+(windowsDrawn * HORIZONTAL_STRETCH )+" and width is "+width+". Scrolling current display back by "+HORIZONTAL_STRETCH+" pixels.");
			bufferCanvas.drawBitmap(buffer, -HORIZONTAL_STRETCH, 0, null);
			//System.out.println("FOR loop: drawing window "+(windowsDrawn+i)+" at "+(width-HORIZONTAL_STRETCH*difference+i));
			drawNextBitmap(width-HORIZONTAL_STRETCH);
		}

		windowsDrawn++;
	}	

	private void drawBitmapGroup(int leftmostBitmap, int rightmostBitmap) {
		//draw all canvas black, then use for loop to keep drawing bitmaps through interval from left hand side
		/*
		 * Replace the entire display with the bitmaps in the interval
		 * [leftmostBitmap, rightmostBitmap-1],
		 * each drawn vertically from the top of the screen.
		 */

		//System.out.println("Drawing bitmaps from "+leftmostBitmap+" to "+rightmostBitmap-1);
		for (int i = 0; leftmostBitmap + i < rightmostBitmap; i++) { //don't worry about if this will fit as checks on 'width' are done in the progress() method
			Bitmap orig = Bitmap.createBitmap(spec.getBitmapWindow(leftmostBitmap+i), 0, 1, 1, samplesPerWindow, Bitmap.Config.ARGB_8888); //TODO check if null
			Bitmap scaled = scaleBitmap(orig, HORIZONTAL_STRETCH, samplesPerWindow * VERTICAL_STRETCH);
			bufferCanvas.drawBitmap(scaled, i*HORIZONTAL_STRETCH, 0f, null);
		}
	}

	private void drawSingleBitmap(int index, int xCoord) {
		/*
		 * Draw the bitmap specified by the provided index  from the top of the screen at the provided x-coordinate, 
		 * stretching according to the HORIZONTAL_STRETCH and VERTICAL_STRETCH parameters.
		 */

		Bitmap orig = Bitmap.createBitmap(spec.getBitmapWindow(index), 0, 1, 1, samplesPerWindow, Bitmap.Config.ARGB_8888); //TODO check if null
		Bitmap scaled = scaleBitmap(orig, HORIZONTAL_STRETCH, samplesPerWindow * VERTICAL_STRETCH);
		bufferCanvas.drawBitmap(scaled, xCoord, 0f, null);
		/*System.out.println("Window " + index
					+ " drawn with (left, top) coordinate at ("
					+ xCoord + "," + 0 + "), density "
					+ scaled.getDensity());*/
	}

	private void drawNextBitmap(int xCoord) {
		/*
		 * Draw the bitmap specified by the provided index  from the top of the screen at the provided x-coordinate, 
		 * stretching according to the HORIZONTAL_STRETCH and VERTICAL_STRETCH parameters.
		 */

		Bitmap orig = Bitmap.createBitmap(spec.getNextBitmapWindow(), 0, 1, 1, samplesPerWindow, Bitmap.Config.ARGB_8888); //TODO check if null
		Bitmap scaled = scaleBitmap(orig, HORIZONTAL_STRETCH, samplesPerWindow * VERTICAL_STRETCH);
		bufferCanvas.drawBitmap(scaled, xCoord, 0f, null);
		/*System.out.println("Window " + index
					+ " drawn with (left, top) coordinate at ("
					+ xCoord + "," + 0 + "), density "
					+ scaled.getDensity());*/
	}


	public Bitmap scaleBitmap(Bitmap bitmapToScale, float newWidth, float newHeight) {   
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



	public Bitmap rotateBitmap(Bitmap source, float angle){
		Matrix matrix = new Matrix();
		matrix.postRotate(angle);
		return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
	}


	public void setSurfaceSize(int width, int height) {
		this.width = width;
		this.height = height;		
	}
}