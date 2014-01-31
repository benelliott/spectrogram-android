package uk.co.benjaminelliott.spectrogramandroid;

import java.util.concurrent.Semaphore;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.preference.PreferenceManager;
import android.util.Log;
import edu.emory.mathcs.jtransforms.fft.DoubleFFT_1D;

public class BitmapGeneratorThread extends Thread {
	private final String PREF_COLOURMAP_KEY = "pref_colourmap";
	private final String PREF_CONTRAST_KEY = "pref_contrast";
	
	private final int BITMAP_STORE_WIDTH_ADJ = 2;
	private final int BITMAP_STORE_HEIGHT_ADJ = 2;
	private final int BITMAP_FREQ_AXIS_WIDTH = 30; //number of pixels (subject to width adjustment) to use to display frequency axis on stored bitmaps


	private final int SAMPLES_PER_WINDOW = AudioConfig.SAMPLES_PER_WINDOW;
	private final int SAMPLE_RATE = AudioConfig.SAMPLE_RATE;
	private final int WINDOW_LIMIT = AudioConfig.WINDOW_LIMIT;
	
	private Context context;

	private boolean running = true;
	private short[][] audioWindows;
	private int[][] bitmapWindows = new int[WINDOW_LIMIT][SAMPLES_PER_WINDOW];
	private int bitmapCurrentIndex = 0;
	private boolean arraysLooped = false;
	private Semaphore audioReady;
	private Semaphore bitmapsReady = new Semaphore(0);
	private int lastBitmapRequested = 0; //keeps track of the most recently requested bitmap window
	private int[] colours;
	private float contrast = 2.0f;
	private double maxAmplitude = 1; //max amplitude seen so far
	private double[] previousWindow = new double[SAMPLES_PER_WINDOW]; //keep a handle on the previous audio sample window so that values can be averaged across them


	
	BitmapGeneratorThread(AudioAcquirerThread audioThread) {
		super();
		setName("Bitmap generator thread");
		this.audioWindows = audioThread.audioWindows;
		this.audioReady = audioThread.audioReady;
	}
	
	@Override
	public void run() {
		while (running) fillBitmapList();
	}
	
	public void fillBitmapList() { 
		/*
		 * When some audio data is ready, perform the short-time Fourier transform on it and 
		 * then convert the results to a bitmap, which is then stored in a 2D array, ready to be displayed.
		 */
		try {
			audioReady.acquire();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		processAudioWindow(audioWindows[bitmapCurrentIndex], bitmapWindows[bitmapCurrentIndex]);
		//Log.d("Bitmap thread","Audio window "+(bitmapCurrentIndex)+ " processed. ");

		bitmapCurrentIndex++;
		bitmapsReady.release();

		if (bitmapCurrentIndex == bitmapWindows.length) {
			bitmapCurrentIndex = 0;
			arraysLooped = true;
		}
	}
	
	private void processAudioWindow(short[] samples, int[] destArray) { //TODO prev and next
		/*
		 * Take the raw audio samples, apply a Hamming window, then perform the Short-Time
		 * Fourier Transform and square the result. Combine the output with that from the previous window
		 * for a smoothing effect. Return the resulting bitmap.
		 */

		double[] fftSamples = new double[SAMPLES_PER_WINDOW*2]; //need half the array to be empty for FFT
		for (int i = 0; i < SAMPLES_PER_WINDOW; i++) {
			fftSamples[i] = (double)(samples[i]);
		}
		hammingWindow(fftSamples); //apply Hamming window before performing STFT
		spectroTransform(fftSamples); //do the STFT on the copied data

		double[] combinedWindow = new double[SAMPLES_PER_WINDOW];
		for (int i = 0; i < SAMPLES_PER_WINDOW; i++) {
			combinedWindow[i] = fftSamples[i] + previousWindow[i];
		}

		for (int i = 0; i < SAMPLES_PER_WINDOW; i++) {
			int val = cappedValue(combinedWindow[i]);
			destArray[SAMPLES_PER_WINDOW-i-1] = colours[val]; //fill upside-down because y=0 is at top of screen
		}
		previousWindow = fftSamples; //keep reference to samples for next process
	}

	private int cappedValue(double d) {
		/*
		 * Returns an integer capped at 255 representing the magnitude of the
		 * given double value, d, relative to the highest amplitude seen so far. The amplitude values
		 * provided use a logarithmic scale but this method converts these back to a linear scale, 
		 * more appropriate for pixel colouring.
		 */
		if (d < 0) return 0;
		if (d > maxAmplitude) {
			maxAmplitude = d;
			return 255;
		}
		return (int)(255*Math.pow((Math.log1p(d)/Math.log1p(maxAmplitude)),contrast));
	}

	private void hammingWindow(double[] samples) {
		/*
		 * This method applies an appropriately-sized Hamming window to the provided array of 
		 * audio sample data.
		 */
		int m = samples.length/4; //divide by 4 not 2 since input array is half-full
		double[] hamming = new double[samples.length];
		double r = Math.PI/(m+1);
		for (int i = -m; i < m; i++) {
			hamming[m + i] = 0.5 + 0.5 * Math.cos(i * r);
		}

		//apply windowing function through multiplication with time-domain samples
		for (int i = 0; i < samples.length; i++) {
			samples[i] *= hamming[i]; 
		}
	}

	private void spectroTransform(double[] paddedSamples) {
		/*
		 * This method modifies the provided array of audio samples in-place, replacing them with 
		 * the result of the short-time Fourier transform of the samples.
		 *
		 * See 'realForward' documentation of JTransforms for more information on the FFT implementation.
		 */
		DoubleFFT_1D d = new DoubleFFT_1D(paddedSamples.length / 2); //DoubleFFT_1D constructor must be supplied with an 'n' value, where n = data size

		d.realForward(paddedSamples);

		//Now the STFT has been calculated, need to square it:

		for (int i = 0; i < paddedSamples.length / 2; i++) {
			paddedSamples[i] *= paddedSamples[i];
		}
	}

	protected int getBitmapWindowsAvailable() {
		/*
		 * Returns the number of bitmaps ready to be drawn.
		 */
		return bitmapsReady.availablePermits();
	}

	protected int getLeftmostBitmapAvailable() {
		/*
		 * Returns the index of the leftmost chronologically usable bitmap still in memory.
		 */
		if (!arraysLooped) return 0;
		return bitmapCurrentIndex+1; //if array has looped, leftmost window is at current index + 1
	}

	protected int getRightmostBitmapAvailable() {
		/*
		 *Returns the index of the rightmost chronologically usable bitmap still in memory.
		 */
		return bitmapCurrentIndex; //just return the index of the last bitmap to have been processed
	}

	protected int[] getBitmapWindow(int index) {
		/*
		 * Returns the bitmap corresponding to the provided index into the array of bitmaps. No bounds checking.
		 */
		return bitmapWindows[index];
	}

	protected int[] getNextBitmap() {
		/*
		 * Returns a REFERENCE to the next bitmap window to be drawn, assuming that the caller will draw it before the bitmap 
		 * creating thread overwrites it (the array size is large - drawing thread would have to be thousands of windows behind the 
		 * creator thread). This potentially dangerous behaviour could be fixed with locks at the cost of performance.
		 */
		try {
			bitmapsReady.acquire(); //block until there is a bitmap to return
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		if (lastBitmapRequested == bitmapWindows.length) lastBitmapRequested = 0; //loop if necessary
		//Log.d("Spectro","Bitmap "+lastBitmapRequested+" requested");
		int[] ret = bitmapWindows[lastBitmapRequested];
		lastBitmapRequested++;
		return ret;
	}
	
	protected void updateColourMapPreference() {
		/*
		 * Called when the colour map preference is changed.
		 */
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		int newMap = Integer.parseInt(prefs.getString(PREF_COLOURMAP_KEY, "NULL"));
		switch(newMap) {
		case 0: colours = HeatMap.whitePurpleGrouped(); break;
		case 1: colours = HeatMap.inverseGreyscale();break;
		case 2: colours = HeatMap.hotMetal(); break;
		case 3: colours = HeatMap.blueGreenRed(); break;
		}
	}

	protected void updateContrastPreference() {
		/*
		 * Called when the colour map preference is changed.
		 */
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		float newContrast = prefs.getFloat(PREF_CONTRAST_KEY, Float.MAX_VALUE);
		if (newContrast != Float.MAX_VALUE) contrast = newContrast * 3.0f + 1.0f; //slider value must be between 0 and 1, so multiply by 3 and add 1
	}
	
	protected Bitmap createEntireBitmap(int startWindow, int endWindow, int bottomFreq, int topFreq) {
		/*
		 * Returns a stand-alone bitmap with time from startWindow to endWindow and band-pass-filtered
		 * from bottomFreq to topFreq.
		 */
		//Hold on to string versions of the frequency values to annotate the bitmap later
		String bottomFreqText = Integer.toString(bottomFreq)+" Hz";
		String topFreqText = Integer.toString(topFreq)+" Hz";

		//convert frequency range into array indices
		bottomFreq = (int) ((2f*(float)bottomFreq/(float)SAMPLE_RATE)*SAMPLES_PER_WINDOW);
		topFreq = (int) ((2f*(float)topFreq/(float)SAMPLE_RATE)*SAMPLES_PER_WINDOW);

		//same for windows
		startWindow %= WINDOW_LIMIT;
		endWindow %= WINDOW_LIMIT;

		//TODO filter

		Bitmap ret;
		Canvas retCanvas;
		int bitmapWidth;
		int bitmapHeight;
		if (endWindow < startWindow) {
			//selection crosses a loop boundary
			bitmapWidth = BITMAP_STORE_WIDTH_ADJ * ((WINDOW_LIMIT - startWindow) + endWindow + BITMAP_FREQ_AXIS_WIDTH);
			bitmapHeight = BITMAP_STORE_HEIGHT_ADJ * (topFreq - bottomFreq);

			Log.d("BG", "Start window: "+startWindow+", end window: "+endWindow+", bottom freq as array index: "+bottomFreq+", top freq: "+topFreq);
			Log.d("BG", "Bitmap width: "+bitmapWidth+" bitmap height: "+bitmapHeight);

			ret = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888);
			retCanvas = new Canvas(ret);
			retCanvas.drawColor(Color.BLACK);
			int[] scaledBitmapWindow = new int[bitmapHeight];


			int h = 0;
			for (int i = startWindow; i < WINDOW_LIMIT; i++) {
				for (int j = 0; j < BITMAP_STORE_WIDTH_ADJ; j++) { //scaling
					int[] orig = new int[SAMPLES_PER_WINDOW];
					processAudioWindow(audioWindows[i],orig);
					int m = 0;
					for (int k = bottomFreq; k < topFreq; k++) {
						for (int l = 0; l < BITMAP_STORE_HEIGHT_ADJ; l++) {
							Log.d("","top freq: "+topFreq+" i: "+i+" j: "+j+ " k: "+k+" l: "+l+" k-bottomFreq+l: "+(k-bottomFreq+l)+", scaled len: "+scaledBitmapWindow.length+", top-bottom:"+(topFreq-bottomFreq)+" height: "+bitmapHeight);
							scaledBitmapWindow[bitmapHeight-m-1] = orig[SAMPLES_PER_WINDOW-k-1]; //remember that array had been filled backwards, and new one should be too
							retCanvas.drawBitmap(scaledBitmapWindow, 0, 1, BITMAP_FREQ_AXIS_WIDTH*BITMAP_STORE_WIDTH_ADJ + h, 0, 1, bitmapHeight, false, null);
							m++;
						}
					}
					h++;
				}
			}

			h = 0;
			for (int i = 0; i < endWindow; i++) {
				for (int j = 0; j < BITMAP_STORE_WIDTH_ADJ; j++) { //scaling
					int[] orig = new int[SAMPLES_PER_WINDOW];
					processAudioWindow(audioWindows[i],orig);
					int m = 0;
					for (int k = bottomFreq; k < topFreq; k++) {
						for (int l = 0; l < BITMAP_STORE_HEIGHT_ADJ; l++) {
							Log.d("","top freq: "+topFreq+" i: "+i+" j: "+j+ " k: "+k+" l: "+l+" k-bottomFreq+l: "+(k-bottomFreq+l)+", scaled len: "+scaledBitmapWindow.length+", top-bottom:"+(topFreq-bottomFreq)+" height: "+bitmapHeight);
							scaledBitmapWindow[bitmapHeight-m-1] = orig[SAMPLES_PER_WINDOW-k-1]; //remember that array had been filled backwards, and new one should be too
							retCanvas.drawBitmap(scaledBitmapWindow, 0, 1, BITMAP_FREQ_AXIS_WIDTH*BITMAP_STORE_WIDTH_ADJ +(WINDOW_LIMIT-startWindow)*BITMAP_STORE_WIDTH_ADJ + h, 0, 1, bitmapHeight, false, null);
							m++;
						}
					}
					h++;
				}
			}
		}
		else {
			bitmapWidth = BITMAP_STORE_WIDTH_ADJ * (endWindow - startWindow + BITMAP_FREQ_AXIS_WIDTH);
			bitmapHeight = BITMAP_STORE_HEIGHT_ADJ * (topFreq - bottomFreq);

			Log.d("BG", "Start window: "+startWindow+", end window: "+endWindow+", bottom freq as array index: "+bottomFreq+", top freq: "+topFreq);
			Log.d("BG", "Bitmap width: "+bitmapWidth+" bitmap height: "+bitmapHeight);

			ret = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888);
			retCanvas = new Canvas(ret);
			retCanvas.drawColor(Color.BLACK);
			int[] scaledBitmapWindow = new int[bitmapHeight];

			int h = 0;
			for (int i = startWindow; i < endWindow; i++) {
				for (int j = 0; j < BITMAP_STORE_WIDTH_ADJ; j++) { //scaling
					int[] orig = new int[SAMPLES_PER_WINDOW];
					processAudioWindow(audioWindows[i],orig);
					int m = 0;
					for (int k = bottomFreq; k < topFreq; k++) {
						for (int l = 0; l < BITMAP_STORE_HEIGHT_ADJ; l++) {
							Log.d("","top freq: "+topFreq+" i: "+i+" j: "+j+ " k: "+k+" l: "+l+" k-bottomFreq+l: "+(k-bottomFreq+l)+", scaled len: "+scaledBitmapWindow.length+", top-bottom:"+(topFreq-bottomFreq)+" height: "+bitmapHeight);
							scaledBitmapWindow[bitmapHeight-m-1] = orig[SAMPLES_PER_WINDOW-k-1]; //remember that array had been filled backwards, and new one should be too
							retCanvas.drawBitmap(scaledBitmapWindow, 0, 1, BITMAP_FREQ_AXIS_WIDTH*BITMAP_STORE_WIDTH_ADJ + h, 0, 1, bitmapHeight, false, null);
							m++;
						}
					}
					h++;
				}
			}
		}
		//annotate bitmap with frequency range:
		Paint textStyle = new Paint();
		textStyle.setColor(Color.WHITE);
		textStyle.setTextSize(BITMAP_FREQ_AXIS_WIDTH/3);
		retCanvas.drawText(bottomFreqText, BITMAP_FREQ_AXIS_WIDTH/2, bitmapHeight-5*BITMAP_STORE_HEIGHT_ADJ, textStyle);
		Log.d("Bitmap capture","bottomFreqText drawn at x:"+(BITMAP_FREQ_AXIS_WIDTH*BITMAP_STORE_WIDTH_ADJ/10)+" y: "+(bitmapHeight-5*BITMAP_STORE_HEIGHT_ADJ));
		retCanvas.drawText(topFreqText, BITMAP_FREQ_AXIS_WIDTH/2, BITMAP_FREQ_AXIS_WIDTH/2, textStyle);
		return ret;
	}


}
