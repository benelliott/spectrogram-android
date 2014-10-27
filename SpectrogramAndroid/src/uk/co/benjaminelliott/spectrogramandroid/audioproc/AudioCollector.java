package uk.co.benjaminelliott.spectrogramandroid.audioproc;

import java.util.concurrent.Semaphore;

import uk.co.benjaminelliott.spectrogramandroid.preferences.DynamicAudioConfig;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

/**
 * Thread which brings in audio samples from the microphone so they can be 
 * processed into bitmaps and later saved if the user makes a capture.
 * @author Ben
 *
 */
public class AudioCollector extends Thread {

    private short[][] audioWindows; // array of audio windows
    private Semaphore audioReady; // semaphore used to indicate when a new window is ready to be processed
    private AudioRecord mic; // access to the microphone
    private int audioCurrentIndex = 0; // current index into the window array
    private int samplesPerWindow; // number of audio samples per audio window
    boolean running = true; // whether or not this thread should be running

    AudioCollector(short[][] audioWindows, DynamicAudioConfig dac, Semaphore audioReady) {
        this.audioWindows = audioWindows;
        this.audioReady = audioReady;
        this.samplesPerWindow = dac.SAMPLES_PER_WINDOW;

        int readSize = AudioRecord.getMinBufferSize(dac.SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        mic = new AudioRecord(MediaRecorder.AudioSource.MIC,dac.SAMPLE_RATE,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT, readSize*2);
    }

    @Override
    public void run() {
    	// start recording from the microphone
        mic.startRecording();
        // loop indefinitely, adding data from the microphone to the list of
        // audio windows
        while (running) {
            fillAudioList();
        }
        // when running is false, stop bringing data from the microphone and release it
        mic.stop();
        mic.release();
        mic = null;
    }

    /**
     * When audio data becomes available from the microphone, store it in a 2D array so
     * that it remains available in case the user chooses to replay certain sections.
     */
    public void fillAudioList() {
        //NOTE: no locking on audioWindows
        //request samplesPerWindow shorts be written into the next free microphone buffer:
        readUntilFull(audioWindows[audioCurrentIndex], 0, samplesPerWindow); 
        audioCurrentIndex++;
        audioReady.release();
        if (audioCurrentIndex == audioWindows.length) {
            //if entire array has been filled, loop and start filling from the start:
            audioCurrentIndex = 0;
        }
    }

    /**
     * The 'read' method supplied by the AudioRecord class will not necessarily fill the destination
     * buffer with samples if there is not enough data available. This method always returns a full array by
     * repeatedly calling the 'read' method until there is no space left.
     */
    private void readUntilFull(short[] buffer, int offset, int spaceRemaining) {
        while (spaceRemaining > 0) {
            int samplesRead = mic.read(buffer, offset, spaceRemaining);
            spaceRemaining -= samplesRead;
            offset += samplesRead;
        }
    }

}
