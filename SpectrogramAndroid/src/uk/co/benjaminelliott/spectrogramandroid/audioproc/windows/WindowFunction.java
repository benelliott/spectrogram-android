package uk.co.benjaminelliott.spectrogramandroid.audioproc.windows;

/**
 * Interface that represents windowing functions, which can be applied
 * to an input provided as an array of {@code short}s.
 * @author Ben
 *
 */
public interface WindowFunction {
	
	/**
	 * Apply the windowing function to the provided input data in-place.
	 * @param samples - the array of audio samples
	 */
	void applyWindow(double[] samples);
}
