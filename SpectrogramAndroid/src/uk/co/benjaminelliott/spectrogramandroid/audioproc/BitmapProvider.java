package uk.co.benjaminelliott.spectrogramandroid.audioproc;

import java.util.concurrent.Semaphore;

import uk.co.benjaminelliott.spectrogramandroid.audioproc.filters.BandpassButterworth;
import uk.co.benjaminelliott.spectrogramandroid.preferences.DynamicAudioConfig;
import uk.co.benjaminelliott.spectrogramandroid.preferences.HeatMap;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.util.Log;

/**
 * Class that manages the threads responsible for processing and saving audio and bitmaps, 
 * and that supplies other classes with entire "chunks" of the bitmap display and audio history.
 * @author Ben
 *
 */
public class BitmapProvider {

    private DynamicAudioConfig dac;
    private short[][] audioWindows;
    private int[][] bitmapWindows;
    private boolean running = false;
    private AudioCollector audioCollector;
    private BitmapCreator bitmapCreator;
    private int[] colours;
    private Semaphore audioReady = new Semaphore(0);
    private Semaphore bitmapsReady = new Semaphore(0);

    public BitmapProvider(DynamicAudioConfig dac) {
        this.dac = dac;
        
        audioWindows = new short[DynamicAudioConfig.WINDOW_LIMIT][dac.SAMPLES_PER_WINDOW];
        bitmapWindows = new int[DynamicAudioConfig.WINDOW_LIMIT][dac.NUM_FREQ_BINS];
                
        switch (dac.COLOUR_MAP) {
        case 0: colours = HeatMap.Greys_ColorBrewer(); break;
        case 1: colours = HeatMap.YlOrRd_ColorBrewer();break;
        case 2: colours = HeatMap.PuOr_Backwards_ColorBrewer(); break;
        default: colours = HeatMap.Greys_ColorBrewer();
        }
    }

    /**
     * Start the two threads responsible for bringing in audio samples and for processing them to generate bitmaps.
     */
    public void start() {
        running = true;

        audioCollector = new AudioCollector(audioWindows, dac, audioReady);
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
        bottomFreq = (int) ((2f*(float)bottomFreq/(float)dac.SAMPLE_RATE)*dac.NUM_FREQ_BINS);
        topFreq = (int) ((2f*(float)topFreq/(float)dac.SAMPLE_RATE)*dac.NUM_FREQ_BINS);

        //same for windows
        startWindow %= DynamicAudioConfig.WINDOW_LIMIT;
        endWindow %= DynamicAudioConfig.WINDOW_LIMIT;

        Bitmap ret;
        Canvas retCanvas;
        int bitmapWidth;
        int bitmapHeight;
        int[] window = new int[dac.NUM_FREQ_BINS];
        int[] subsection;

        if (endWindow < startWindow) {
            //selection crosses a loop boundary
            bitmapWidth = (DynamicAudioConfig.WINDOW_LIMIT - startWindow) + endWindow + DynamicAudioConfig.BITMAP_FREQ_AXIS_WIDTH;
            bitmapHeight = topFreq - bottomFreq;

            subsection = new int[bitmapHeight];

            Log.d("BG", "Start window: "+startWindow+", end window: "+endWindow+", bottom freq as array index: "+bottomFreq+", top freq: "+topFreq);
            Log.d("BG", "Bitmap width: "+bitmapWidth+" bitmap height: "+bitmapHeight);

            ret = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888);
            retCanvas = new Canvas(ret);
            retCanvas.drawColor(Color.BLACK);


            for (int i = startWindow; i < DynamicAudioConfig.WINDOW_LIMIT; i++) {
                window = new int[dac.NUM_FREQ_BINS];
                bitmapCreator.processAudioWindow(audioWindows[i], window);
                for (int j = 0; j < topFreq - bottomFreq; j++) {
                    subsection[bitmapHeight-j-1] = window[dac.NUM_FREQ_BINS-(j+bottomFreq)-1]; //array was filled backwards
                }
                retCanvas.drawBitmap(subsection, 0, 1, DynamicAudioConfig.BITMAP_FREQ_AXIS_WIDTH + i - startWindow, 0, 1, bitmapHeight, false, null);
            }

            for (int i = 0; i < endWindow; i++) {
                window = new int[dac.NUM_FREQ_BINS];
                bitmapCreator.processAudioWindow(audioWindows[i], window);
                for (int j = 0; j < topFreq - bottomFreq; j++) {
                    subsection[bitmapHeight-j-1] = window[dac.NUM_FREQ_BINS-(j+bottomFreq)-1]; //array was filled backwards
                }
                retCanvas.drawBitmap(subsection, 0, 1, DynamicAudioConfig.BITMAP_FREQ_AXIS_WIDTH + i - startWindow, 0, 1, bitmapHeight, false, null);
            }

        }
        else {
            bitmapWidth = endWindow - startWindow + DynamicAudioConfig.BITMAP_FREQ_AXIS_WIDTH;
            bitmapHeight = topFreq - bottomFreq;

            subsection = new int[bitmapHeight];

            ret = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888);
            retCanvas = new Canvas(ret);
            retCanvas.drawColor(Color.BLACK);

            for (int i = startWindow; i < endWindow; i++) {
                window = new int[dac.NUM_FREQ_BINS];
                bitmapCreator.processAudioWindow(audioWindows[i], window);
                for (int j = 0; j < topFreq - bottomFreq; j++) {
                    subsection[bitmapHeight-j-1] = window[dac.NUM_FREQ_BINS-(j+bottomFreq)-1]; //array was filled backwards
                }
                retCanvas.drawBitmap(subsection, 0, 1, DynamicAudioConfig.BITMAP_FREQ_AXIS_WIDTH + i - startWindow, 0, 1, bitmapHeight, false, null);
            }

        }

        Bitmap scaled = scaleBitmap(ret,bitmapWidth*DynamicAudioConfig.BITMAP_STORE_WIDTH_ADJ, bitmapHeight*DynamicAudioConfig.BITMAP_STORE_HEIGHT_ADJ);
        Canvas scaledCanvas = new Canvas(scaled);

        //annotate bitmap with frequency range:
        Paint textStyle = new Paint();
        textStyle.setColor(Color.WHITE);
        textStyle.setTextSize(DynamicAudioConfig.BITMAP_FREQ_AXIS_WIDTH/3);
        scaledCanvas.drawText(bottomFreqText, DynamicAudioConfig.BITMAP_FREQ_AXIS_WIDTH/2, bitmapHeight*DynamicAudioConfig.BITMAP_STORE_HEIGHT_ADJ-5*DynamicAudioConfig.BITMAP_STORE_HEIGHT_ADJ, textStyle);
        Log.d("Bitmap capture","bottomFreqText drawn at x:"+(DynamicAudioConfig.BITMAP_FREQ_AXIS_WIDTH*DynamicAudioConfig.BITMAP_STORE_WIDTH_ADJ/10)+" y: "+(bitmapHeight*DynamicAudioConfig.BITMAP_STORE_HEIGHT_ADJ-5*DynamicAudioConfig.BITMAP_STORE_HEIGHT_ADJ));
        scaledCanvas.drawText(topFreqText, DynamicAudioConfig.BITMAP_FREQ_AXIS_WIDTH/2, DynamicAudioConfig.BITMAP_FREQ_AXIS_WIDTH/2, textStyle);
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
        startWindow %= DynamicAudioConfig.WINDOW_LIMIT;
        endWindow %= DynamicAudioConfig.WINDOW_LIMIT;


        short[] toReturn;

        if (endWindow < startWindow) {
            //selection crosses a loop boundary
            toReturn = new short[((DynamicAudioConfig.WINDOW_LIMIT - startWindow) + endWindow)*dac.SAMPLES_PER_WINDOW];
            for (int i = startWindow; i < DynamicAudioConfig.WINDOW_LIMIT; i++) {
                for (int j = 0; j < dac.SAMPLES_PER_WINDOW; j++) {
                    toReturn[(i-startWindow)*dac.SAMPLES_PER_WINDOW+j] = audioWindows[i][j];
                }
            }
            for (int i = 0; i < endWindow; i++) {
                for (int j = 0; j < dac.SAMPLES_PER_WINDOW; j++) {
                    toReturn[(DynamicAudioConfig.WINDOW_LIMIT-startWindow+i)*dac.SAMPLES_PER_WINDOW+j] = audioWindows[i][j];
                }
            }
        }
        else {
            toReturn = new short[(endWindow-startWindow)*dac.SAMPLES_PER_WINDOW];
            for (int i = startWindow; i < endWindow; i++) {
                for (int j = 0; j < dac.SAMPLES_PER_WINDOW; j++) {
                    toReturn[(i-startWindow)*dac.SAMPLES_PER_WINDOW+j] = audioWindows[i][j];
                }
            }
        }

        double minFreq = bottomFreq;
        double maxFreq = topFreq;
        if (dac.OVERFILTER) {
            //User preference for overfiltering is enabled so reduce passband width by 40%
            double difference = (double)(topFreq - bottomFreq)*0.2d;
            minFreq += difference;
            maxFreq -= difference;
        }
        BandpassButterworth butter = new BandpassButterworth(dac.SAMPLE_RATE, 8, minFreq, maxFreq, 1.0);
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

    public int[] getColours() {
        return colours;
    }
    
    public DynamicAudioConfig getDynamicAudioConfig() {
        return dac;
    }
}