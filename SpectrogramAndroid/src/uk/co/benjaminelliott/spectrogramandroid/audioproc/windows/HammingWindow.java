package uk.co.benjaminelliott.spectrogramandroid.audioproc.windows;

/**
 * A Hamming Window that can be applied to an array of audio samples.
 * @author Ben
 *
 */
public class HammingWindow implements WindowFunction {
	
	private final int windowSize;
	
	public HammingWindow(int windowSize) {
		this.windowSize = windowSize;
		hammingWindow = new double[windowSize];
	}


	/**
	 * Generates a Hamming Window.
	 * @param windowSize - the size of the window (in elements)
	 * @return a {@code double[]} of window coefficients.
	 */
	private static double[] generateHammingWindow(int windowSize) {
		/*
		 * This method generates an appropriately-sized Hamming window to be used later.
		 */
		int m = windowSize/2;
		double r = Math.PI/(m+1);
		double[] window = new double[windowSize];
		for (int i = -m; i < m; i++) {
			window[m + i] = 0.5 + 0.5 * Math.cos(i * r);
		}
		return window;
	}
	
	@Override
	public void applyWindow(double[] samples) {
		double[] hammingWindow = generateHammingWindow(windowSize);
		//apply windowing function through multiplication with time-domain samples
		for (int i = 0; i < windowSize; i++) {
			samples[i] *= hammingWindow[i]; 
		}
	}

}
