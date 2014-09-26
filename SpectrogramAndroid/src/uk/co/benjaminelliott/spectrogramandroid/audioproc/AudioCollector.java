package uk.co.benjaminelliott.spectrogramandroid.audioproc;

import java.util.concurrent.Semaphore;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

public class AudioCollector extends Thread {

    private short[][] audioWindows;
    private Semaphore audioReady;
    private BitmapGenerator bg;
    private AudioRecord mic;
    private int audioCurrentIndex = 0;
    private int samplesPerWindow;
    private int sampleRate;
    boolean running = false;
    private int samplesRead = 0;

    AudioCollector(short[][] audioWindows, Semaphore audioReady, int sampleRate, int samplesPerWindow) {
        this.audioWindows = audioWindows;
        this.audioReady = audioReady;
        this.bg = bg;
        this.sampleRate = sampleRate;
        this.samplesPerWindow = samplesPerWindow;

        int readSize = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        mic = new AudioRecord(MediaRecorder.AudioSource.MIC,sampleRate,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT, readSize*2);
    }

    @Override
    public void run() {
        mic.startRecording();

        while (running) {
            fillAudioList();
        }

        mic.stop();
        mic.release();
        mic = null;
    }

    /**
     * When audio data becomes available from the microphone, store it in a 2D array so
     * that it remains available in case the user chooses to replay certain sections.
     */
    public void fillAudioList() {
        //note no locking on audioWindows
        //request samplesPerWindow shorts be written into the next free microphone buffer
        readUntilFull(audioWindows[audioCurrentIndex], 0, samplesPerWindow); 
        audioCurrentIndex++;
        audioReady.release();
        if (audioCurrentIndex == audioWindows.length) {
            //if entire array has been filled, loop and start filling from the start
            //Log.d("", "Adding audio item "+audioCurrentIndex+" and array full, so looping back to start");
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
            samplesRead = mic.read(buffer, offset, spaceRemaining);
            spaceRemaining -= samplesRead;
            offset += samplesRead;
        }
    }

}
