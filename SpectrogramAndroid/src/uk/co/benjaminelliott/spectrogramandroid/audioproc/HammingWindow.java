package uk.co.benjaminelliott.spectrogramandroid.audioproc;


public class HammingWindow extends WindowFunction {
	
	private final int windowSize;

	private double[] hammingWindow;
	
	HammingWindow(int windowSize) {
		this.windowSize = windowSize;
		hammingWindow = new double[windowSize];
		generateHammingWindow();
	}
	


	private void generateHammingWindow() {
		/*
		 * This method generates an appropriately-sized Hamming window to be used later.
		 */
		int m = windowSize/2;
		double r = Math.PI/(m+1);
		for (int i = -m; i < m; i++) {
			hammingWindow[m + i] = 0.5 + 0.5 * Math.cos(i * r);
		}
	}
	
	void applyWindow(double[] samples) {

		//apply windowing function through multiplication with time-domain samples
		for (int i = 0; i < windowSize; i++) {
			samples[i] *= hammingWindow[i]; 
		}
	}

}
