package uk.co.benjaminelliott.spectrogramandroid.audioproc;

import java.util.concurrent.Semaphore;

import org.jtransforms.fft.DoubleFFT_1D;

import uk.co.benjaminelliott.spectrogramandroid.audioproc.windows.HammingWindow;
import uk.co.benjaminelliott.spectrogramandroid.audioproc.windows.WindowFunction;

public class BitmapCreator extends Thread {

    boolean running = true;
    private int samplesPerWindow;
    private int numFreqBins;
    private short[][] audioWindows;
    private int[][] bitmapWindows;
    private Semaphore audioReady;
    private Semaphore bitmapsReady;
    private WindowFunction window;
    private boolean arraysLooped = false;
    private int bitmapCurrentIndex = 0;
    private int lastBitmapRequested = 0; //keeps track of the most recently requested bitmap window
    private int oldestBitmapAvailable = 0;
    private double maxAmplitude = 1;
    private double contrast;
    private int[] colours;
    
    //allocate memory here rather than in performance-affecting methods:
    private double[] fftSamples;
    private double[] previousWindow; //keep a handle on the previous audio sample window so that values can be averaged across them
    private double[] combinedWindow;
    private DoubleFFT_1D dfft1d; //DoubleFFT_1D constructor must be supplied with an 'n' value, where n = data size
    private int val = 0; //current value for cappedValue function


    BitmapCreator(BitmapProvider bp) {
        this.audioWindows = bp.getAudioWindowArray();
        this.bitmapWindows = bp.getBitmapWindowArray();
        this.audioReady = bp.getAudioSemaphore();
        this.bitmapsReady = bp.getBitmapSemaphore();
        this.contrast = bp.getContrast();
        this.colours = bp.getColours();
        this.samplesPerWindow = bp.getSamplesPerWindow();
        this.numFreqBins = bp.getNumFreqBins();
        window = new HammingWindow(samplesPerWindow);
        
        fftSamples = new double[samplesPerWindow];
        previousWindow = new double[samplesPerWindow];
        combinedWindow = new double[samplesPerWindow];
        dfft1d = new DoubleFFT_1D(samplesPerWindow);

    }

    @Override
    public void run() {
        while (running) {
            fillBitmapList();
        }
    }

    /**
     * When some audio data is ready, perform the short-time Fourier transform on it and 
     * then convert the results to a bitmap, which is then stored in a 2D array, ready to be displayed.
     */
    public void fillBitmapList() { 

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

        if (arraysLooped) {
            oldestBitmapAvailable++;
        }
    }
    
    /**
     * Take the raw audio samples, apply a windowing function, then perform the Short-Time
     * Fourier Transform and square the result. Combine the output with that from the previous window
     * for a smoothing effect. Return the resulting bitmap.
     */
   void processAudioWindow(short[] samples, int[] destArray) {

        for (int i = 0; i < samplesPerWindow; i++) {
            fftSamples[i] = (double)(samples[i]);
        }
        window.applyWindow(fftSamples); //apply Hamming window before performing STFT
        spectroTransform(fftSamples); //do the STFT on the copied data

        for (int i = 0; i < samplesPerWindow; i++) {
            combinedWindow[i] = fftSamples[i] + previousWindow[i];
        }

        for (int i = 0; i < numFreqBins; i++) {
            val = cappedValue(combinedWindow[i]);
            destArray[numFreqBins-i-1] = colours[val]; //fill upside-down because y=0 is at top of screen
        }

        //keep samples for next process
        for (int i = 0; i < samplesPerWindow; i++) previousWindow[i] = fftSamples[i];
    }

    /**
     * Returns an integer capped at 255 representing the magnitude of the
     * given double value, d, relative to the highest amplitude seen so far. The amplitude values
     * provided use a logarithmic scale but this method converts these back to a linear scale, 
     * more appropriate for pixel colouring.
     */
    private int cappedValue(double d) {
        if (d < 0) return 0;
        if (d > maxAmplitude) {
            maxAmplitude = d;
            return 255;
        }
        return (int)(255*Math.pow((Math.log1p(d)/Math.log1p(maxAmplitude)),contrast));
    }

    /**
     * Modifies the provided array of audio samples in-place, replacing them with 
     * the result of the short-time Fourier transform of the samples.
     *
     * See 'realForward' documentation of JTransforms for more information on the FFT implementation.
     */
    private void spectroTransform(double[] paddedSamples) {
        dfft1d.realForward(paddedSamples);
        //Calculate the STFT by using squared magnitudes. Store these in the first half of the array, and the rest will be discarded:
        for (int i = 0; i < numFreqBins; i++) {
            //Note that for frequency k, Re[k] and Im[k] are stored adjacently
            paddedSamples[i] = paddedSamples[2*i] * paddedSamples[2*i] + paddedSamples[2*i+1] * paddedSamples[2*i+1];
        }
    }
    
    public int getOldestBitmapIndex() {
        return oldestBitmapAvailable;
    }

    /**
     * Returns the index of the leftmost chronologically usable bitmap still in memory.
     */
    public int getLeftmostBitmapIndex() {
        if (!arraysLooped) return 0;
        return bitmapCurrentIndex + 1; //if array has looped, leftmost window is at current index + 1
    }

    /**
     *Returns the index of the rightmost chronologically usable bitmap still in memory.
     */
    public int getRightmostBitmapIndex() {
        return bitmapCurrentIndex;
    }

    /**
     * Returns a REFERENCE to the next bitmap window to be drawn, assuming that the caller will draw it before the bitmap 
     * creating thread overwrites it (the array size is large - drawing thread would have to be thousands of windows behind the 
     * creator thread). This potentially dangerous behaviour could be fixed with locks at the cost of performance.
     */
    public int[] getNextBitmap() {
        try {
            bitmapsReady.acquire(); //block until there is a bitmap to return
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        if (lastBitmapRequested == bitmapWindows.length) lastBitmapRequested = 0; //loop if necessary
        //Log.d("Spectro","Bitmap "+lastBitmapRequested+" requested");
        return bitmapWindows[lastBitmapRequested++];
    }

}
