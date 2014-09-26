package uk.co.benjaminelliott.spectrogramandroid.audioproc;

import java.util.concurrent.Semaphore;

import uk.co.benjaminelliott.spectrogramandroid.audioproc.filters.BandpassButterworth;
import uk.co.benjaminelliott.spectrogramandroid.preferences.HeatMap;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.preference.PreferenceManager;
import android.util.Log;

public class BitmapProvider {

    private final int SAMPLE_RATE; //options are 11025, 16000, 22050, 44100
    private final int SAMPLES_PER_WINDOW; //usually around 300
    private final int NUM_FREQ_BINS;
    public static final String PREF_COLOURMAP_KEY = "pref_colourmap";
    protected static final String PREF_CONTRAST_KEY = "pref_contrast";
    protected static final String PREF_SAMPLE_RATE_KEY = "pref_sample_rate";
    protected static final String PREF_SAMPLES_WINDOW_KEY = "pref_samples_window";
    protected static final String PREF_OVERFILTER_KEY = "pref_overfilter";

    //number of windows that can be held in the arrays at once before older ones are deleted. Time this represents is
    // WINDOW_LIMIT*SAMPLES_PER_WINDOW/SAMPLE_RATE, e.g. 10000*300/16000 = 187.5 seconds.
    public static final int WINDOW_LIMIT = 1000; //usually around 10000 

    //Storage for audio and bitmap windows is pre-allocated, and the quantity is determined by
    // WINDOW_LIMIT*SAMPLES_PER_WINDOW*(bytes per int + bytes per double),
    // e.g. 10000*300*(4+8) = 34MB


    protected static final int BITMAP_STORE_WIDTH_ADJ = 2;
    protected static final int BITMAP_STORE_HEIGHT_ADJ = 2;
    public static final int BITMAP_STORE_QUALITY = 90; //compression quality parameter for storage
    protected static final int BITMAP_FREQ_AXIS_WIDTH = 30; //number of pixels (subject to width adjustment) to use to display frequency axis on stored bitmaps

    private short[][] audioWindows;
    private int[][] bitmapWindows;
    private float contrast = 2.0f;
    private boolean running = false;
    private AudioCollector audioCollector;
    private BitmapCreator bitmapCreator;
    private int[] colours;
    private Semaphore audioReady = new Semaphore(0);
    private Semaphore bitmapsReady = new Semaphore(0);
    private boolean overfilter = false;

    public BitmapProvider(Context context) {
        //bitmapsReady = new Semaphore(0);
        colours = new int[256];

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        SAMPLE_RATE = Integer.parseInt(prefs.getString(PREF_SAMPLE_RATE_KEY, "16000"));
        SAMPLES_PER_WINDOW = Integer.parseInt(prefs.getString(PREF_SAMPLES_WINDOW_KEY, "300"));
        overfilter = prefs.getBoolean(PREF_OVERFILTER_KEY, false);

        NUM_FREQ_BINS = SAMPLES_PER_WINDOW / 2; //lose half because of symmetry

        audioWindows = new short[WINDOW_LIMIT][SAMPLES_PER_WINDOW];
        bitmapWindows = new int[WINDOW_LIMIT][NUM_FREQ_BINS];


        String colMapString = prefs.getString(PREF_COLOURMAP_KEY, "NULL");
        int colourMap = 0;
        if (!colMapString.equals("NULL")) colourMap = Integer.parseInt(prefs.getString(PREF_COLOURMAP_KEY, "NULL"));

        switch (colourMap) {
        case 0: colours = HeatMap.Greys_ColorBrewer(); break;
        case 1: colours = HeatMap.YlOrRd_ColorBrewer();break;
        case 2: colours = HeatMap.PuOr_Backwards_ColorBrewer(); break;
        }

        float newContrast = prefs.getFloat(PREF_CONTRAST_KEY, Float.MAX_VALUE);
        if (newContrast != Float.MAX_VALUE) contrast = newContrast * 3.0f + 1.0f; //slider value must be between 0 and 1, so multiply by 3 and add 1
    }

    /**
     * Start the two threads responsible for bringing in audio samples and for processing them to generate bitmaps.
     */
    public void start() {
        running = true;

        audioCollector = new AudioCollector(audioWindows, audioReady, SAMPLE_RATE, SAMPLES_PER_WINDOW);
        bitmapCreator = new BitmapCreator(this);

        audioCollector.start();
        bitmapCreator.start();
    }

    /**
     * Stop bringing in and processing audio samples.
     */
    public void stop() {
        if (running) {
            running = false;
            audioCollector.running = false;
            bitmapCreator.running = false;
        }
    }

    /**
     * Returns a stand-alone bitmap with time from startWindow to endWindow and band-pass-filtered
     * from bottomFreq to topFreq.
     */
    public Bitmap createEntireBitmap(int startWindow, int endWindow, int bottomFreq, int topFreq) {
        //Hold on to string versions of the frequency values to annotate the bitmap later
        String bottomFreqText = Integer.toString(bottomFreq)+" Hz";
        String topFreqText = Integer.toString(topFreq)+" Hz";

        //convert frequency range into array indices
        bottomFreq = (int) ((2f*(float)bottomFreq/(float)SAMPLE_RATE)*NUM_FREQ_BINS);
        topFreq = (int) ((2f*(float)topFreq/(float)SAMPLE_RATE)*NUM_FREQ_BINS);

        //same for windows
        startWindow %= WINDOW_LIMIT;
        endWindow %= WINDOW_LIMIT;

        Bitmap ret;
        Canvas retCanvas;
        int bitmapWidth;
        int bitmapHeight;
        int[] window = new int[NUM_FREQ_BINS];
        int[] subsection;

        if (endWindow < startWindow) {
            //selection crosses a loop boundary
            bitmapWidth = (WINDOW_LIMIT - startWindow) + endWindow + BITMAP_FREQ_AXIS_WIDTH;
            bitmapHeight = topFreq - bottomFreq;

            subsection = new int[bitmapHeight];

            Log.d("BG", "Start window: "+startWindow+", end window: "+endWindow+", bottom freq as array index: "+bottomFreq+", top freq: "+topFreq);
            Log.d("BG", "Bitmap width: "+bitmapWidth+" bitmap height: "+bitmapHeight);

            ret = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888);
            retCanvas = new Canvas(ret);
            retCanvas.drawColor(Color.BLACK);


            for (int i = startWindow; i < WINDOW_LIMIT; i++) {
                window = new int[NUM_FREQ_BINS];
                bitmapCreator.processAudioWindow(audioWindows[i], window);
                for (int j = 0; j < topFreq - bottomFreq; j++) {
                    subsection[bitmapHeight-j-1] = window[NUM_FREQ_BINS-(j+bottomFreq)-1]; //array was filled backwards
                }
                retCanvas.drawBitmap(subsection, 0, 1, BITMAP_FREQ_AXIS_WIDTH + i - startWindow, 0, 1, bitmapHeight, false, null);
            }

            for (int i = 0; i < endWindow; i++) {
                window = new int[NUM_FREQ_BINS];
                bitmapCreator.processAudioWindow(audioWindows[i], window);
                for (int j = 0; j < topFreq - bottomFreq; j++) {
                    subsection[bitmapHeight-j-1] = window[NUM_FREQ_BINS-(j+bottomFreq)-1]; //array was filled backwards
                }
                retCanvas.drawBitmap(subsection, 0, 1, BITMAP_FREQ_AXIS_WIDTH + i - startWindow, 0, 1, bitmapHeight, false, null);
            }

        }
        else {
            bitmapWidth = endWindow - startWindow + BITMAP_FREQ_AXIS_WIDTH;
            bitmapHeight = topFreq - bottomFreq;

            subsection = new int[bitmapHeight];
            
            ret = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888);
            retCanvas = new Canvas(ret);
            retCanvas.drawColor(Color.BLACK);

            for (int i = startWindow; i < endWindow; i++) {
                window = new int[NUM_FREQ_BINS];
                bitmapCreator.processAudioWindow(audioWindows[i], window);
                for (int j = 0; j < topFreq - bottomFreq; j++) {
                    subsection[bitmapHeight-j-1] = window[NUM_FREQ_BINS-(j+bottomFreq)-1]; //array was filled backwards
                }
                retCanvas.drawBitmap(subsection, 0, 1, BITMAP_FREQ_AXIS_WIDTH + i - startWindow, 0, 1, bitmapHeight, false, null);
            }

        }

        Bitmap scaled = scaleBitmap(ret,bitmapWidth*BITMAP_STORE_WIDTH_ADJ, bitmapHeight*BITMAP_STORE_HEIGHT_ADJ);
        Canvas scaledCanvas = new Canvas(scaled);

        //annotate bitmap with frequency range:
        Paint textStyle = new Paint();
        textStyle.setColor(Color.WHITE);
        textStyle.setTextSize(BITMAP_FREQ_AXIS_WIDTH/3);
        scaledCanvas.drawText(bottomFreqText, BITMAP_FREQ_AXIS_WIDTH/2, bitmapHeight*BITMAP_STORE_HEIGHT_ADJ-5*BITMAP_STORE_HEIGHT_ADJ, textStyle);
        Log.d("Bitmap capture","bottomFreqText drawn at x:"+(BITMAP_FREQ_AXIS_WIDTH*BITMAP_STORE_WIDTH_ADJ/10)+" y: "+(bitmapHeight*BITMAP_STORE_HEIGHT_ADJ-5*BITMAP_STORE_HEIGHT_ADJ));
        scaledCanvas.drawText(topFreqText, BITMAP_FREQ_AXIS_WIDTH/2, BITMAP_FREQ_AXIS_WIDTH/2, textStyle);
        return scaled;
    }

    /**
     * Returns the provided bitmap, scaled to fit the new width and height parameters.
     */
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

    /**
     * Returns an array of PCM audio data based on the window interval supplied to the function.
     */
    public short[] getAudioChunk(int startWindow, int endWindow, int bottomFreq, int topFreq) {
        //convert windows into array indices
        startWindow %= WINDOW_LIMIT;
        endWindow %= WINDOW_LIMIT;


        short[] toReturn;

        if (endWindow < startWindow) {
            //selection crosses a loop boundary
            toReturn = new short[((WINDOW_LIMIT - startWindow) + endWindow)*SAMPLES_PER_WINDOW];
            for (int i = startWindow; i < WINDOW_LIMIT; i++) {
                for (int j = 0; j < SAMPLES_PER_WINDOW; j++) {
                    //Log.d("Audio chunk","i: "+i+", j: "+j+" i*SAMPLES_PER_WINDOW+j: "+(i*SAMPLES_PER_WINDOW+j));
                    toReturn[(i-startWindow)*SAMPLES_PER_WINDOW+j] = audioWindows[i][j];
                }
            }
            for (int i = 0; i < endWindow; i++) {
                for (int j = 0; j < SAMPLES_PER_WINDOW; j++) {
                    //Log.d("Audio chunk","i: "+i+", j: "+j+" i*SAMPLES_PER_WINDOW+j: "+(i*SAMPLES_PER_WINDOW+j));
                    toReturn[(WINDOW_LIMIT-startWindow+i)*SAMPLES_PER_WINDOW+j] = audioWindows[i][j];
                }
            }
        }
        else {
            toReturn = new short[(endWindow-startWindow)*SAMPLES_PER_WINDOW];
            for (int i = startWindow; i < endWindow; i++) {

                for (int j = 0; j < SAMPLES_PER_WINDOW; j++) {
                    //Log.d("Audio chunk","i: "+i+", j: "+j+" i*SAMPLES_PER_WINDOW+j: "+(i*SAMPLES_PER_WINDOW+j));
                    toReturn[(i-startWindow)*SAMPLES_PER_WINDOW+j] = audioWindows[i][j];
                }
            }
        }

        Log.d("","Filtering capture from "+bottomFreq+"Hz to "+topFreq+"Hz. No. bins: "+NUM_FREQ_BINS);
        double minFreq = bottomFreq;
        double maxFreq = topFreq;
        if (overfilter) {
            //User preference for overfiltering is enabled so reduce passband width by 40%
            double difference = (double)(topFreq - bottomFreq)*0.2d;
            minFreq += difference;
            maxFreq -= difference;
        }
        BandpassButterworth butter = new BandpassButterworth(SAMPLE_RATE, 8, minFreq, maxFreq, 1.0);
        butter.applyFilter(toReturn);

        for (int i = 0; i < toReturn.length; i++) toReturn[i] = Short.reverseBytes(toReturn[i]); //must be little-endian for WAV
        return toReturn;
    }
    

    /**
     * Returns the number of bitmaps ready to be drawn.
     */
    public int getBitmapWindowsAvailable() {
        return bitmapsReady.availablePermits();
    }

    public int getOldestBitmapIndex() {
        return bitmapCreator.getOldestBitmapIndex();
    }


    public int getLeftmostBitmapIndex() {
        return bitmapCreator.getLeftmostBitmapIndex();
    }


    public int getRightmostBitmapIndex() {
        return bitmapCreator.getRightmostBitmapIndex(); //just return the index of the last bitmap to have been processed
    }

    /**
     * Returns the bitmap corresponding to the provided index into the array of bitmaps. No bounds checking.
     */
    public int[] getBitmapWindow(int index) {
        return bitmapWindows[index];
    }


    public int[] getNextBitmap() {
        return bitmapCreator.getNextBitmap();
    }

    public int getSampleRate() {
        return SAMPLE_RATE;
    }

    public int getSamplesPerWindow() {
        return SAMPLES_PER_WINDOW;
    }

    public int getNumFreqBins() {
        return NUM_FREQ_BINS;
    }

    public short[][] getAudioWindowArray() {
        return audioWindows;
    }

    public int[][] getBitmapWindowArray() {
        return bitmapWindows;
    }

    public Semaphore getAudioSemaphore() {
        return audioReady;
    }

    public Semaphore getBitmapSemaphore() {
        return bitmapsReady;
    }

    public double getContrast() {
        return contrast;
    }
    
    public int[] getColours() {
        return colours;
    }
}