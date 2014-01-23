package bge23.spectrogramandroid;

import java.util.concurrent.Semaphore;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;
import edu.emory.mathcs.jtransforms.fft.DoubleFFT_1D;

public class BitmapGenerator {

	/*
	 * Class which handles most of the 'clever stuff' behind the spectrogram display; taking in audio sample data
	 * from the microphone and processing it to give a list of bitmaps (each representing one window of the audio),
	 * ready to be displayed. Two threads are created: one to pull in data from the microphone ('audioThread') and 
	 * another to convert this data into a bitmap as soon as it becomes available ('bitmapThread').
	 */

	public static final int SAMPLE_RATE = 16000; //options are 11025, 22050, 16000, 44100
	public static final int SAMPLES_PER_WINDOW = 300; //usually around 300
	private final int MIC_BUFFERS = 100; //number of buffers to maintain at once
	private final float CONTRAST = 2.0f;

	//number of windows that can be held in the arrays at once before older ones are deleted. Time this represents is
	// WINDOW_LIMIT*SAMPLES_PER_WINDOW/SAMPLE_RATE, e.g. 10000*300/16000 = 187.5 seconds.
	protected static final int WINDOW_LIMIT = 5000; //usually around 10000 

	//Storage for audio and bitmap windows is pre-allocated, and the quantity is determined by
	// WINDOW_LIMIT*SAMPLES_PER_WINDOW*(bytes per int + bytes per double),
	// e.g. 10000*300*(4+8) = 34MB
	
	
	protected static final int BITMAP_STORE_WIDTH_ADJ = 2;
	protected static final int BITMAP_STORE_HEIGHT_ADJ = 2;
	protected static final int BITMAP_STORE_QUALITY = 90; //compression quality parameter for storage
	protected static final int BITMAP_FREQ_AXIS_WIDTH = 30; //number of pixels (subject to width adjustment) to use to display frequency axis on stored bitmaps

	private short[][] audioWindows = new short[WINDOW_LIMIT][SAMPLES_PER_WINDOW];
	private int[][] bitmapWindows = new int[WINDOW_LIMIT][SAMPLES_PER_WINDOW];

	private boolean running = false;


	//	private int CONTRAST = 400;
	private double maxAmplitude = 1; //max amplitude seen so far
	private short[][] micBuffers = new short[MIC_BUFFERS][SAMPLES_PER_WINDOW];; //array of buffers so that different frames can be being processed while others are read in 
	private AudioRecord mic;
	private Thread audioThread;
	private Thread bitmapThread;
	private int[] colours;
	private Integer audioCurrentIndex = 0; //keep track of where in the audioWindows array we have most recently written to
	private int bitmapCurrentIndex = 0;
	private boolean arraysLooped = false; //true if we have filled the entire array and are now looping round, hence old values can be read from later in the array
	private Semaphore audioReady = new Semaphore(0);
	private Semaphore bitmapsReady = new Semaphore(0);
	private int lastBitmapRequested = 0; //keeps track of the most recently requested bitmap window
	private double[] previousWindow = new double[SAMPLES_PER_WINDOW]; //keep a handle on the previous audio sample window so that values can be averaged across them


	public BitmapGenerator(int colourMap) {
		//bitmapsReady = new Semaphore(0);
		colours = new int[256];

		switch (colourMap) {
		case 0: colours = HeatMap.greyscale(); break;
		case 1: colours = HeatMap.blueGreenRed(); break;
		case 2: colours = HeatMap.bluePinkRed(); break;
		case 3: colours = HeatMap.blueOrangeYellow(); break;
		case 4: colours = HeatMap.yellowOrangeBlue(); break;
		case 5: colours = HeatMap.blackGreen(); break;
		case 6: colours = HeatMap.blueGreenRed2(); break;
		case 7: colours = HeatMap.whiteBlue(); break;
		case 8: colours = HeatMap.hotMetal(); break;
		case 9: colours = HeatMap.whitePurpleGrouped(); break;
		}

		int readSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
		mic = new AudioRecord(MediaRecorder.AudioSource.MIC,SAMPLE_RATE,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT, readSize*2);
	}

	public void start() {
		/*
		 * Start the two threads responsible fro bringing in audio samples and for processing them to generate bitmaps.
		 */
		
		mic.startRecording();
		running = true;
		audioThread = new Thread(new Runnable(){
			public void run() {
				fillAudioList();
			}
		});

		bitmapThread = new Thread(new Runnable(){
			public void run() {
				fillBitmapList();
			}
		});

		audioThread.start();

		bitmapThread.start();
	}

	public void stop() {
		/*
		 * Stop bringing in and processing audio samples.
		 */
		running = false;
		mic.stop();
	}

	public void fillAudioList() {
		/*
		 * When audio data becomes available from the microphone, store it in a 2D array so
		 * that it remains available in case the user chooses to replay certain sections.
		 */
		while (running) {
			int currentBuffer = audioCurrentIndex % MIC_BUFFERS;
			readUntilFull(micBuffers[currentBuffer], 0, SAMPLES_PER_WINDOW); //request samplesPerWindow shorts be written into the next free microphone buffer
			short[] toAdd = new short[SAMPLES_PER_WINDOW]; //create a new double-array to store the double-converted data
			for (int i = 0; i < SAMPLES_PER_WINDOW; i++) { //convert the short data into double
				toAdd[i] = micBuffers[currentBuffer][i];
			}

			if (audioCurrentIndex == audioWindows.length) {
				//if entire array has been filled, loop and start filling from the start
				Log.d("", "Adding audio item "+audioCurrentIndex+" and array full, so looping back to start");
				synchronized(audioCurrentIndex) {
					audioCurrentIndex = 0;
				}
			}
			synchronized(audioWindows) {
				for (int i = 0; i < SAMPLES_PER_WINDOW; i++) {
					audioWindows[audioCurrentIndex][i] = toAdd[i];
				}
			}
			synchronized(audioCurrentIndex) { //don't modify this when it might be being read by another thread
				audioCurrentIndex++;
				audioReady.release();
			}
			Log.d("Audio thread","Audio window "+audioCurrentIndex+" added.");
			//audioWindowsAdded++;
		}
	}

	private void readUntilFull(short[] buffer, int offset, int spaceRemaining) {
		/*
		 * The 'read' method supplied by the AudioRecord class will not necessarily fill the destination
		 * buffer with samples if there is not enough data available. This method always returns a full array by
		 * repeatedly calling the 'read' method until there is no space left.
		 */
		int samplesRead;
		while (spaceRemaining > 0) {
			samplesRead = mic.read(buffer, offset, spaceRemaining);
			spaceRemaining -= samplesRead;
			offset += samplesRead;
		}
	}

	public void fillBitmapList() { 
		/*
		 * When some audio data is ready, perform the short-time Fourier transform on it and 
		 * then convert the results to a bitmap, which is then stored in a 2D array, ready to be displayed.
		 */
		while (running) {

			try {
				audioReady.acquire();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			int[] bitmapToAdd = processAudioWindow(audioWindows[bitmapCurrentIndex]);
			Log.d("Bitmap thread","Audio window "+(bitmapCurrentIndex)+ " processed. ");
			for (int i = 0; i < SAMPLES_PER_WINDOW; i++) {
				bitmapWindows[bitmapCurrentIndex][i] = bitmapToAdd[i];
			}
			
			bitmapCurrentIndex++;
			bitmapsReady.release();

			if (bitmapCurrentIndex == bitmapWindows.length) {
				bitmapCurrentIndex = 0;
				arraysLooped = true;
			}
			
		}

	}

	private int[] processAudioWindow(short[] samples) { //TODO prev and next
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

		int[] ret = new int[SAMPLES_PER_WINDOW];
		for (int i = 0; i < SAMPLES_PER_WINDOW; i++) {
			int val = cappedValue(combinedWindow[i]);
			ret[SAMPLES_PER_WINDOW-i-1] = colours[val]; //fill upside-down because y=0 is at top of screen
		}

		for (int i = 0; i < SAMPLES_PER_WINDOW; i++) {
			previousWindow[i] = fftSamples[i];
		}
		

		return ret;
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
		return (int)( 255*Math.pow((Math.log1p(d)/Math.log1p(maxAmplitude)),CONTRAST));
	}

	private void hammingWindow(double[] samples) {
		/*
		 * This method applies an appropriately-sized Hamming window to the provided array of 
		 * audio sample data.
		 */
		int m = samples.length/2;
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

	public int getSampleRate(){
		return SAMPLE_RATE;
	}

	protected int getBitmapWindowsAvailable() {
		/*
		 * Returns the number of bitmaps ready to be drawn.
		 */
		return bitmapsReady.availablePermits();
	}

	protected int getLeftmostBitmapAvailable() {
		/*
		 * Returns the index of the leftmost bitmap still in memory.
		 */
		if (!arraysLooped) return 0;
		return WINDOW_LIMIT-bitmapCurrentIndex; //if array has looped, leftmost window is at array size - current index
	}

	protected int getRightmostBitmapAvailable() {
		/*
		 *Returns the index of the rightmost bitmap still in memory.
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
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		if (lastBitmapRequested == bitmapWindows.length) lastBitmapRequested = 0; //loop if necessary
		Log.d("Spectro","Bitmap "+lastBitmapRequested+" requested");
		int[] ret = bitmapWindows[lastBitmapRequested];
		lastBitmapRequested++;
		return ret;
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

		int bitmapWidth = BITMAP_STORE_WIDTH_ADJ * (endWindow - startWindow + BITMAP_FREQ_AXIS_WIDTH);
		int bitmapHeight = BITMAP_STORE_HEIGHT_ADJ * (topFreq - bottomFreq);
		
		Bitmap ret = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888);
		Canvas retCanvas = new Canvas(ret);
		retCanvas.drawColor(Color.BLACK);
		
		//TODO filter

		int[] scaledBitmapWindow = new int[bitmapHeight];
		
		Log.d("BG", "Start window: "+startWindow+", end window: "+endWindow+", bottom freq as array index: "+bottomFreq+", top freq: "+topFreq);
		Log.d("BG", "Bitmap width: "+bitmapWidth+" bitmap height: "+bitmapHeight);
				
		int h = 0;
		for (int i = startWindow; i < endWindow; i++) {
			for (int j = 0; j < BITMAP_STORE_WIDTH_ADJ; j++) { //scaling
				int[] orig = processAudioWindow(audioWindows[i]);
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
		//annotate bitmap with frequency range:
		Paint textStyle = new Paint();
		textStyle.setColor(Color.WHITE);
		textStyle.setTextSize(BITMAP_FREQ_AXIS_WIDTH/3);
		retCanvas.drawText(bottomFreqText, BITMAP_FREQ_AXIS_WIDTH/2, bitmapHeight-5*BITMAP_STORE_HEIGHT_ADJ, textStyle);
		Log.d("Bitmap capture","bottomFreqText drawn at x:"+(BITMAP_FREQ_AXIS_WIDTH*BITMAP_STORE_WIDTH_ADJ/10)+" y: "+(bitmapHeight-5*BITMAP_STORE_HEIGHT_ADJ));
		retCanvas.drawText(topFreqText, BITMAP_FREQ_AXIS_WIDTH/2, BITMAP_FREQ_AXIS_WIDTH/2, textStyle);
		return ret;
	}
	
	protected short[] getAudioChunk(int startWindow, int endWindow) {
		/*
		 * Returns an array of PCM audio data based on the window interval supplied to the function.
		 */
		short[] toReturn = new short[(endWindow-startWindow)*SAMPLES_PER_WINDOW];
		for (int i = startWindow; i < endWindow; i++) {
			for (int j = 0; j < SAMPLES_PER_WINDOW; j++) {
				//Log.d("Audio chunk","i: "+i+", j: "+j+" i*SAMPLES_PER_WINDOW+j: "+(i*SAMPLES_PER_WINDOW+j));
				toReturn[(i-startWindow)*SAMPLES_PER_WINDOW+j] = Short.reverseBytes(audioWindows[i][j]); //must be little-endian for WAV
			}
		}
		return toReturn;
	}

}