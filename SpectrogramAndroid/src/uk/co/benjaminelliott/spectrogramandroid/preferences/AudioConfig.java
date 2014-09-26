package uk.co.benjaminelliott.spectrogramandroid.preferences;

public class AudioConfig {
	
	public static final int SAMPLE_RATE = 16000; //options are 11025, 22050, 16000, 44100
	public static final int SAMPLES_PER_WINDOW = 300; //usually around 300
	
	//number of windows that can be held in the arrays at once before older ones are deleted. Time this represents is
	// WINDOW_LIMIT*SAMPLES_PER_WINDOW/SAMPLE_RATE, e.g. 10000*300/16000 = 187.5 seconds.
	public static final int WINDOW_LIMIT = 1000; //usually around 10000 

	//Storage for audio and bitmap windows is pre-allocated, and the quantity is determined by
	// WINDOW_LIMIT*SAMPLES_PER_WINDOW*(bytes per int + bytes per double),
	// e.g. 10000*300*(4+8) = 34MB

}
