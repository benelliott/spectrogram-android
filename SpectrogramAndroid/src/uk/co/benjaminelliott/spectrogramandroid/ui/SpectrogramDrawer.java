package uk.co.benjaminelliott.spectrogramandroid.ui;

import uk.co.benjaminelliott.spectrogramandroid.audioproc.BitmapProvider;
import uk.co.benjaminelliott.spectrogramandroid.preferences.DynamicAudioConfig;
import uk.co.benjaminelliott.spectrogramandroid.preferences.UiConfig;
import uk.co.benjaminelliott.spectrogramandroid.ui.bitmaps.ScrollShadowGenerator;
import uk.co.benjaminelliott.spectrogramandroid.ui.bitmaps.SelectRectGenerator;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.view.SurfaceHolder;

/**
 * Class responsible for drawing generated bitmaps to the screen.
 * @author Ben
 *
 */
class SpectrogramDrawer {

    private DynamicAudioConfig dac;
    private int width; 
    private int height;
    private SurfaceHolder holder;

    private final float verticalStretch;
    private BitmapProvider bg;
    private Thread scrollingThread;
    private Canvas displayCanvas;
    private Bitmap buffer;
    private Canvas bufferCanvas;
    private Bitmap buffer2; //need to use this when shifting to the left because of a bug in Android, see http://stackoverflow.com/questions/6115695/android-canvas-gives-garbage-when-shifting-a-bitmap
    private Canvas buffer2Canvas;
    private Bitmap leftShadow;
    private Bitmap rightShadow;
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

    public SpectrogramDrawer(DynamicAudioConfig dac, int width, int height, SurfaceHolder holder) {
        this.dac = dac;
        this.width = width;
        this.height = height;
        this.holder = holder;

        bg = new BitmapProvider(dac);
        verticalStretch = ((float)height)/((float)dac.NUM_FREQ_BINS); // stretch spectrogram to all of available height

        init();
    }

    /**
     * Initialise relevant buffers, generate scroll shadows, start the scrolling thread and start
     * bringing in and processing audio samples.
     */
    private void init() {
        buffer = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bufferCanvas = new Canvas(buffer);
        buffer2 = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        buffer2Canvas = new Canvas(buffer2);

        scrollingThread = new Thread() {
            @Override
            public void run() {
                while (running) scroll();
            }
        };
        scrollingThread.setName("Scrolling thread");

        leftShadow = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        rightShadow = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        ScrollShadowGenerator.generateScrollShadows(leftShadow, rightShadow, width, height, UiConfig.SCROLL_SHADOW_SPREAD);
        // generate matrix to scale by horiz/vert scale params:
        scaleMatrix = generateScaleMatrix(1,dac.NUM_FREQ_BINS,UiConfig.HORIZONTAL_STRETCH_FACTOR, dac.NUM_FREQ_BINS * verticalStretch); 
        clearCanvas();
        start();
    }

    /**
     * Clear the display by painting it black.
     */
    private void clearCanvas() {
        displayCanvas = holder.lockCanvas(null);
        try {
            bufferCanvas.drawColor(Color.BLACK);
            synchronized (holder) {
                displayCanvas.drawBitmap(buffer, 0, 0, null); //draw buffer to display
            }
        } finally {
            if (displayCanvas != null) {
                holder.unlockCanvasAndPost(displayCanvas);
            }
        }
    }

    /**
     * Repeatedly run the quickProgress() method to look for new bitmaps, 
     * then draw the result to the display.
     */
    public void scroll() {
        displayCanvas = holder.lockCanvas(null);
        try {
            quickProgress(); //update buffer bitmap
            synchronized (holder) {
                //draw buffer to display
                displayCanvas.drawBitmap(buffer, 0, 0, null);
                //draw scrolling shadow bitmaps on top
                displayCanvas.drawBitmap(leftShadow, 0,  0, null); 
            }
        } finally {
            if (displayCanvas != null) {
                holder.unlockCanvasAndPost(displayCanvas);
            }
        }
    }

    /**
     * Takes a pixel offset, converts it into a number of windows by which to scroll the screen,
     * then scrolls the screen so long as the appropriate windows are available.
     */
    public void quickSlide(int offset) {
        int windowLimit = DynamicAudioConfig.WINDOW_LIMIT;
        int horizontalStretch = UiConfig.HORIZONTAL_STRETCH_FACTOR;
        if (canScroll) { //only scroll if there are more than a screen's worth of windows
            //stop new windows from coming in immediately
            offset /= horizontalStretch; //convert from pixel offset to window offset 
            oldestBitmapAvailable = bg.getOldestBitmapIndex();
            drawLeftShadow = true;
            drawRightShadow = true;
            if (offset > windowLimit/2) offset = windowLimit/2;
            if (offset < -windowLimit/2) offset = -windowLimit/2;
            leftmostWindowAsIndex = leftmostWindow % windowLimit;
            rightmostWindow = leftmostWindow + width/horizontalStretch;
            rightmostWindowAsIndex = rightmostWindow % windowLimit;

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
                    buffer2Canvas.drawBitmap(buffer, horizontalStretch * offset, 0, null);//shift current display to the right by UiConfig.HORIZONTAL_STRETCH_FACTOR*offset pixels
                    bufferCanvas.drawBitmap(buffer2, 0, 0, null); //must copy to a second buffer first due to a bug in Android source
                    leftmostWindowAsIndex = Math.abs(leftmostWindowAsIndex - offset);
                    for (int i = 0; i < offset; i++) {
                        drawSingleBitmap((leftmostWindowAsIndex + i)%windowLimit, i * horizontalStretch); //draw windows from x = 0 to x = UiConfig.HORIZONTAL_STRETCH_FACTOR*offset
                    }
                    leftmostWindow -= offset;
                }
            } else { //slide rightwards
                offset = -offset; //change to positive for convenience
                if (rightmostWindowAsIndex != rightmostBitmapAvailable) {
                    bufferCanvas.drawBitmap(buffer, -horizontalStretch * offset, 0, null);//shift current display to the left by UiConfig.HORIZONTAL_STRETCH_FACTOR*offset pixels
                    for (int i = 0; i < offset; i++) {
                        drawSingleBitmap((rightmostWindowAsIndex + i)%windowLimit, width
                                + horizontalStretch * (i - offset)); //draw windows from x=width+UiConfig.HORIZONTAL_STRETCH_FACTOR*(i-offset).
                    }
                    leftmostWindow += offset;
                }
            }
            displayCanvas = holder.lockCanvas(null);
            try {
                synchronized (holder) {
                    displayCanvas.drawBitmap(buffer, 0, 0, null); //draw buffer to display
                    if (drawLeftShadow) displayCanvas.drawBitmap(leftShadow, 0,  0, null); //draw scrolling shadow bitmaps on top
                    if (drawRightShadow) displayCanvas.drawBitmap(rightShadow, 0,  0, null);
                }
            } finally {
                if (displayCanvas != null) {
                    holder.unlockCanvasAndPost(displayCanvas);
                }
            }
        }
    }



    /**
     * Shifts the bitmap displayed in the previous frame and then draws
     * the new windows on the right hand side.
     */
    private void quickProgress() {
        windowsAvailable = bg.getBitmapWindowsAvailable();

        if ((windowsDrawn+windowsAvailable) * UiConfig.HORIZONTAL_STRETCH_FACTOR >= width) { 
            canScroll = true; //can only scroll if whole screen has been filled
            leftmostWindow += windowsAvailable;
        }
        
      //shift what is currently displayed by (number of new windows ready to be drawn)
      // * UiConfig.HORIZONTAL_STRETCH_FACTOR
        bufferCanvas.drawBitmap(buffer, -UiConfig.HORIZONTAL_STRETCH_FACTOR*windowsAvailable, 0, null);
        for (int i = 0; i < windowsAvailable; i++) {
           //draw new window at width - UiConfig.HORIZONTAL_STRETCH_FACTOR * difference
           // [start of blank area] + i*UiConfig.HORIZONTAL_STRETCH_FACTOR [offset for current window]
            drawNextBitmap(width+UiConfig.HORIZONTAL_STRETCH_FACTOR*(i-windowsAvailable)); 
        }

        windowsDrawn += windowsAvailable;
    }

    /**
     * Retrieve and draw the next bitmap at the provided x-coordinate.
     */
    private void drawNextBitmap(int xCoord) {
        unscaledBitmap = Bitmap.createBitmap(bg.getNextBitmap(), 0, 1, 1, dac.NUM_FREQ_BINS, Bitmap.Config.ARGB_8888);
        bufferCanvas.drawBitmap(scaleBitmap(unscaledBitmap), xCoord, 0f, null);
    }


    /**
     * Draw the bitmap specified by the provided index from the top of the screen
     * at the provided x-coordinate, stretching according to the UiConfig.HORIZONTAL_STRETCH_FACTOR
     * and verticalStretch parameters.
     */
    private void drawSingleBitmap(int index, int xCoord) {
        unscaledBitmap = Bitmap.createBitmap(bg.getBitmapWindow(index), 0, 1, 1, dac.NUM_FREQ_BINS, Bitmap.Config.ARGB_8888);
        bufferCanvas.drawBitmap(scaleBitmap(unscaledBitmap), xCoord, 0f, null);
    }

    private Bitmap rectBitmap;
    /**
     * Draw the select-area rectangle with left, right, top and bottom coordinates at selectRectL,
     * selectRectR, selectRectT and selectRectB respectively.
     */
    public void drawSelectRect(float selectRectL, float selectRectT, float selectRectR, float selectRectB) {
        rectBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

        SelectRectGenerator.generateSelectRect(selectRectL, selectRectT, selectRectR, selectRectB, rectBitmap);

        displayCanvas = holder.lockCanvas(null);
        try {
            synchronized (holder) {
                displayCanvas.drawBitmap(buffer, 0, 0, null); //clean any old rectangles away
                displayCanvas.drawBitmap(rectBitmap, 0, 0, null); //draw new rectangle to display buffer
            }
        } finally {
            if (displayCanvas != null) {
                holder.unlockCanvasAndPost(displayCanvas);
            }
        }
    }

    /**
     * Halt scrolling of the display and the threads which bring in and process 
     * audio samples to generate bitmaps.
     */
    public void pauseScrolling() {
        running = false;
        bg.stop(); //stop taking in and processing new samples since this will overwrite those you are trying to scroll through
        leftmostBitmapAvailable = bg.getLeftmostBitmapIndex();
        rightmostBitmapAvailable = bg.getRightmostBitmapIndex();
        quickSlide(0); //force the shadows to be drawn immediately
    }

    /**
     * Returns a matrix for scaling bitmaps from origWidth, origHeight to newWidth, newHeight.
     */
    private Matrix generateScaleMatrix(float origWidth, float origHeight, float newWidth, float newHeight) {
        Matrix matrix = new Matrix();
        matrix.postScale(newWidth / origWidth, newHeight / origHeight);
        return matrix;
    }

    /**
     * Returns the provided bitmap, scaled to fit the new width and height as determined by
     * the horizontal and vertical scale parameters
     */
    public Bitmap scaleBitmap(Bitmap bitmapToScale) {
        return Bitmap.createBitmap(
                bitmapToScale, 
                0, 
                0, 
                bitmapToScale.getWidth(), 
                bitmapToScale.getHeight(), 
                scaleMatrix, 
                true);
    }

    /**
     * Returns the amount of time it takes to fill the entire width of the
     * screen with bitmap windows.
     */
    public float getScreenFillTime() {
        //no. windows on screen = width/UiConfig.HORIZONTAL_STRETCH_FACTOR,
        //no. samples on screen = no. windows * samplesPerWindow
        //time on screen = no. samples / samples per second [sample rate]
        return ((float)width/(float)UiConfig.HORIZONTAL_STRETCH_FACTOR*(float)dac.SAMPLES_PER_WINDOW)/(float)dac.SAMPLE_RATE;
    }

    /**
     * Returns the maximum frequency that can be displayed on the spectrogram, which, due
     * to the Nyquist limit, is half the sample rate.
     */
    public float getMaxFrequency() {
        return 0.5f*dac.SAMPLE_RATE;
    }

    /**
     * Returns the window number associated with the horizontal pixel 
     * offset provided (pixelOffset = 0 at the left side of the spectrogram).
     */
    protected int getWindowAtPixel(float pixelOffset) {
        if (pixelOffset < 0) return leftmostWindow;
        if (pixelOffset > width) pixelOffset = width;
        //number of windows that can fit on entire screen
        float windowsOnScreen = ((float)width)/((float)UiConfig.HORIZONTAL_STRETCH_FACTOR);
        //screen is filled with samples
        if (canScroll) return (int)(leftmostWindow + ((pixelOffset/width) * windowsOnScreen)); 
        int ret = (int)(windowsDrawn - (windowsOnScreen-(pixelOffset/width) * windowsOnScreen));
        if (ret > 0) return ret;
        return 0;
    }

    /**
     * Returns the time offset associated with the horizontal pixel 
     * offset provided (pixelOffset = 0 at the left side of the spectrogram).
     */
    protected float getTimeAtPixel(float pixelOffset) {
        if (pixelOffset < 0) return getScreenFillTime();
        if (pixelOffset > width) return width;
        int windowOffset = getWindowAtPixel(pixelOffset)-windowsDrawn;
        //number of windows that can fit on entire screen
        float windowsOnScreen = ((float)width)/((float)UiConfig.HORIZONTAL_STRETCH_FACTOR); 
        //time per window * windows difference
        return (getScreenFillTime()/windowsOnScreen)*(float)windowOffset; 
    }

    protected float getTimeFromStartAtPixel(float pixelOffset) {
        //number of windows that can fit on entire screen
        float windowsOnScreen = ((float)width)/((float)UiConfig.HORIZONTAL_STRETCH_FACTOR); 
        return (getScreenFillTime()/windowsOnScreen)*getWindowAtPixel(pixelOffset);
    }

    protected float getTimeFromStopAtPixel(float pixelOffset) {
        //number of windows that can fit on entire screen
        float windowsOnScreen = ((float)width)/((float)UiConfig.HORIZONTAL_STRETCH_FACTOR); 
        return -(getScreenFillTime()/windowsOnScreen)*(windowsDrawn-getWindowAtPixel(pixelOffset));
    }

    /**
     * Returns the frequency associated with the vertical pixel 
     * offset provided (pixelOffset = 0 at the top of the spectrogram).
     */
    protected int getFrequencyAtPixel(float pixelOffset) {
        if (pixelOffset < 0) {
            pixelOffset = 0;
        }
        if (pixelOffset > height) {
            pixelOffset = height;
        }
        return (int)(getMaxFrequency() - (pixelOffset/height)*getMaxFrequency());
    }

    /**
     * Remove any selection rectangles from the display by redrawing the spectrogram
     * bitmap on top.
     */
    public void hideSelectRect() {
        displayCanvas = holder.lockCanvas(null);
        try {
            synchronized (holder) {
                displayCanvas.drawBitmap(buffer, 0, 0, null); //clean any rectangles away
            }
        } finally {
            if (displayCanvas != null) {
                holder.unlockCanvasAndPost(displayCanvas);
            }
        }
    }

    /**
     * Converts a description of a section of the spectrogram display (in pixels) to
     * windows, then requests the appropriate bitmap from the BitmapProvider object.
     * @param x0 - first horizontal point on the display.
     * @param y0 - first vertical point on the display.
     * @param x1 - second horizontal point on the display.
     * @param y1 - second vertical point on the display.
     * @return a Bitmap object containing the requested subsection of the display bitmap.
     */
    protected Bitmap getBitmapToStore(float x0, float y0, float x1, float y1) {

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

        // don't just copy directly from the display canvas since that bitmap 
        // has been stretched depending on device screen size		

        return bg.createEntireBitmap(startWindow, endWindow, bottomFreq, topFreq);
    }

    /**
     * Converts a description of a section of the spectrogram display (in pixels) to
     * windows, then requests the appropriate piece of recorded audio from the BitmapProvider object.
     * @param x0 - first horizontal point on the display.
     * @param y0 - first vertical point on the display.
     * @param x1 - second horizontal point on the display.
     * @param y1 - second vertical point on the display.
     * @return a short-array containing the requested audio samples.
     */
    protected short[] getAudioToStore(float x0, float y0,
            float x1, float y1) {
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

    /**
     * Halts the scrolling thread and prevents new audio samples from being processed.
     */
    public void stop() {
        running = false;
        bg.stop();
    }

    /**
     * (Re)starts the scrolling thread and starts bringing in and processing new audio samples.
     */
    public void start() {
        running = true;
        scrollingThread.start();
        bg.start();
    }
}