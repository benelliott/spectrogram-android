//NOTE NOTE USED - PLANNED REFACTOR

package uk.co.benjaminelliott.spectrogramandroid;

import java.util.concurrent.Semaphore;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

public class AudioAcquirerThread extends Thread {
	private boolean running = true;
	short[][] audioWindows = new short[AudioConfig.WINDOW_LIMIT][AudioConfig.SAMPLES_PER_WINDOW];
	private AudioRecord mic;
	private Integer audioCurrentIndex = 0; //keep track of where in the audioWindows array we have most recently written to
	Semaphore audioReady = new Semaphore(0);

	AudioAcquirerThread() {
		super();
		setName("Audio acquirer thread");
	}
	
	@Override
	public void run() {
		int readSize = AudioRecord.getMinBufferSize(AudioConfig.SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
		mic = new AudioRecord(MediaRecorder.AudioSource.MIC,AudioConfig.SAMPLE_RATE,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT, readSize*2);
		mic.startRecording();
		while (running) fillAudioList();
		mic.stop();
		mic.release();
	}
	
	public void setRunning(boolean running) {
		this.running = running;
	}
	
	public void fillAudioList() {
		/*
		 * When audio data becomes available from the microphone, store it in a 2D array so
		 * that it remains available in case the user chooses to replay certain sections.
		 */
		//note no locking on audioWindows - dangerous but crucial for responsiveness
		readUntilFull(audioWindows[audioCurrentIndex], 0, AudioConfig.SAMPLES_PER_WINDOW); //request samplesPerWindow shorts be written into the next free microphone buffer

		synchronized(audioCurrentIndex) { //don't modify this when it might be being read by another thread
			audioCurrentIndex++;
			audioReady.release();
			if (audioCurrentIndex == audioWindows.length) {
				//if entire array has been filled, loop and start filling from the start
				Log.d("", "Adding audio item "+audioCurrentIndex+" and array full, so looping back to start");
				audioCurrentIndex = 0;

			}
		}
		//Log.d("Audio thread","Audio window "+audioCurrentIndex+" added.");
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

}
