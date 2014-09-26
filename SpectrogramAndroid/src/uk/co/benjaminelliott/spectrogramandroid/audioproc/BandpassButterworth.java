package uk.co.benjaminelliott.spectrogramandroid.audioproc;

import org.jtransforms.fft.DoubleFFT_1D;

import android.util.Log;

public class BandpassButterworth extends BandpassFilter{
	
	private final double nyquistFreq;
	private final int order; 
	private final double minFreq;	
	private final double maxFreq;
	private final double dcGain; //DC gain
	private DoubleFFT_1D dfft1d; //DoubleFFT_1D constructor must be supplied with an 'n' value, where n = data size

	
	BandpassButterworth(int sampleRate, int order, double minFreq, double maxFreq, double dcGain) {
		nyquistFreq = sampleRate / 2d; //highest possible frequency is 0.5 * sample rate (Nyquist limit)
		this.order = order;
		this.minFreq = minFreq;
		this.maxFreq = maxFreq;
		this.dcGain = dcGain;
		
	}
	
	private double[] generateGain(int bins) {
		/*
		 * Generates and returns an array of gain coefficients, 
		 * given the number of frequency bins in the audio signal.
		 */
		double[] gain = new double[bins];
		double binWidth = nyquistFreq / (double)bins;
		Log.d("FILTER","Min: "+minFreq+" max: "+maxFreq);
		double centre = (minFreq+maxFreq)/2;
		double cutoff = (maxFreq-minFreq)/2;
		Log.d("","Bin width: "+binWidth+" bins: "+bins+" cutoff: "+cutoff+" centre: "+centre+" order: "+order);
		for (int i = 0; i < bins; i++) {
			gain[i] = dcGain/Math.sqrt((1+Math.pow(((double)i*binWidth-centre)/cutoff, 2.0*order))); //real
		}
		return gain;
	}

	
	void applyFilter(short[] samples) {
		int length = samples.length;
		dfft1d = new DoubleFFT_1D(length);
		double[] fftSamples = new double[length];
		int bins = fftSamples.length / 2; //two elements per bin: one real, one imaginary
		double[] gain = generateGain(bins);
		for (int i = 0; i < length; i++) fftSamples[i] = samples[i]; //copy samples
		dfft1d.realForward(fftSamples); //FT samples
		for (int i = 0; i < bins; i++) {
			fftSamples[2*i] *= gain[i]; //apply gain to real...
			fftSamples[2*i+1] *= gain[i]; //and imaginary elements of FT-ed samples
		}
		dfft1d.realInverse(fftSamples, true); //inverse FT samples
		for (int i = 0; i < length; i++) samples[i] = (short)fftSamples[i]; //copy filtered samples back

	}
	

}
