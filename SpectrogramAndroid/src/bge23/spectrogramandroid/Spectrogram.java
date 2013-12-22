package bge23.spectrogramandroid;

import java.io.FileDescriptor;
import java.util.ArrayList;

import android.graphics.Color;
import edu.emory.mathcs.jtransforms.fft.DoubleFFT_1D;

//TODO: find good values for sliceDurationLimit, windowSize, overlap

public class Spectrogram {
	private ArrayList<double[][]> audioSlices = new ArrayList<double[][]>(); //List of 2D arrays of input data with syntax slice[window number][window element]
	private ArrayList<double[][]> spectroSlices = new ArrayList<double[][]>(); //List of 2D arrays of output data, e.g. temp = slices.getFirst(); temp[time][freq]
	private ArrayList<int[]> bitmapWindows = new ArrayList<int[]>(); //list of 1D arrays of pixel values for the bitmap. each element of this list represents the array of pixel values for one [composite] window
	@SuppressWarnings("unused")
	private int fileDuration; //in miliseconds
	private int finalSliceElements;
	private int finalWindowElements;
	private int sampleRate;
	private int bitsPerSample;
	private int elementsPerWindow;
	private int windowsPerSlice;
	private int windowDuration = 32; //window size in miliseconds. Window size will decide the index range for the arrays. TODO: decide an appropriate size
	private int windowSize; //size of window IN BYTES
	private int windowsInFile;
	private int sliceDurationLimit = 4096; //limit of slice duration in miliseconds. Currently 4096ms = 4.096s  -- TODO: decide an appropriate size
	private double audioSliceSizeLimit; //limit to audio slice size in bytes
	private int audioSliceElements; //limit to audio slice size in bytes
	private double[] firstChannelData;
	private double maxAmplitude = 1; //max amplitude seen so far
	private boolean inColour = true; //want the spectrogram to be in colour rather than greyscale
	public int elements; //TODO get rid


	public Spectrogram() {

	}

	public Spectrogram(String filepath) {
		getDataFromWAV(filepath);
		fillAudioList();
		fillSpectroList();
		fillBitmapList();
	}

	private void getDataFromWAV(String filepath) { //fills audioSlices list with 
		//TODO: work with stereo input
		WAVExplorer w = new WAVExplorer(filepath);
		firstChannelData = w.getFirstChannelData();
		sampleRate = w.getSampleRate();
		System.out.println("Sample rate: "+sampleRate);
		bitsPerSample = w.getBitsPerSample();
		fileDuration = w.getDuration()*1000; //given in seconds, want miliseconds
		System.out.println("Bits per sample: "+bitsPerSample);
		windowSize = windowDuration*sampleRate*bitsPerSample/8000; //8000 = 8*1000 since 8 bits in byte and 1000 ms in a sec (rate is 1/sec). Not actually used directly because not an exact multiple of 2
		audioSliceSizeLimit = ((double)sliceDurationLimit)*((double)sampleRate)*((double)bitsPerSample)/8000d;
		audioSliceElements = (int)(audioSliceSizeLimit*8d/((double)bitsPerSample));
		System.out.println("Audio slice size limit (bytes): "+audioSliceSizeLimit+", or (elements): " +audioSliceSizeLimit*8/bitsPerSample); //TODO: remove eventually
		windowsPerSlice = sliceDurationLimit/windowDuration; //no. windows in slice = slice duration / window duration
		System.out.println("Windows per slice: "+windowsPerSlice);
		elementsPerWindow = windowSize*8/bitsPerSample; //no. elements in window = slice size (bytes) / no. windows
		System.out.println("Window size (bytes): "+windowSize);
		System.out.println("Elements per window: "+elementsPerWindow);
	}

	//remember that last 'slice' in list may only be part-full

	private void fillSpectroList() {
		for (double[][] slice : audioSlices) {
			double[][] spectroSlice = new double[slice.length][slice[0].length]; //JTransforms requires that input arrays be padded with as many zeros as there are samples. TODO: check that there are always the same number of samples
			for (int window = 0; window < slice.length; window++) {
				for (int i = 0; i < slice[window].length; i++) {
					spectroSlice[window][i] = slice[window][i];
				}
				hammingWindow(spectroSlice[window]); //Apply windowing function to window 
				spectroTransform(spectroSlice[window]); //store the STFT of the window in the same array once the samples have been populated 
			}
			spectroSlices.add(spectroSlice); //add the transformed slice to the list
			System.out.println("Spectrogram slice added to list.");
		}
	}

	private void fillAudioList() {
		int i = 1;

		System.out.println("Length of WAV audio data is "+ firstChannelData.length + " elements, or "+ firstChannelData.length*bitsPerSample/8 + " bytes." );
		windowsInFile = firstChannelData.length/elementsPerWindow;
		System.out.println("There are "+firstChannelData.length/(elementsPerWindow*windowsPerSlice)+ " full slices in the data, or "+windowsInFile+" full windows.");
		while (i*audioSliceElements < firstChannelData.length) { //takes care of all full slices
			System.out.println("Value of i is "+i+", value of i*audioSliceElements is "+audioSliceElements*i);
			//want to add an array to ArrayList each time we fill one minute's worth
			double[][] singleSlice = new double[windowsPerSlice][elementsPerWindow]; 
			//TODO: allow for overlaps - what is a good overlap size?
			for (int j = 0; j < windowsPerSlice; j++) { //all of this is really inefficient!
				//System.out.println("Value of j is "+j);
				for (int k = 0; k < elementsPerWindow; k++) {
					//System.out.println("Value of k is "+k+", value of i*audioSliceSizeLimit+j*elementsPerWindow+k is "+i*audioSliceSizeLimit+j*elementsPerWindow+k);
					singleSlice[j][k] = firstChannelData[(i-1)*audioSliceElements+j*elementsPerWindow+k];
				}
			}
			audioSlices.add(singleSlice);
			System.out.println("Audio slice added to list.");
			i++;
		}


		//Now deal with the remaining data that is not enough to fill a slice
		int capturedData = (i-1)*audioSliceElements;
		finalSliceElements = firstChannelData.length - capturedData;
		if  (finalSliceElements != 0) {
			int remainingFullWindows = (int) Math.floor(finalSliceElements/elementsPerWindow); //TODO finalSliceLength in BYTES
			System.out.println("Final slice length (elements): "+finalSliceElements+", window size (bytes): "+windowSize);
			System.out.println("Remaining full windows: "+remainingFullWindows);
			finalWindowElements = finalSliceElements-(remainingFullWindows*elementsPerWindow);
			System.out.println("Final window length (elements): "+finalWindowElements);
			double[][] finalSlice = new double[remainingFullWindows+1][elementsPerWindow];
			System.out.println("Elements per window: "+elementsPerWindow);
			if (finalWindowElements == 0) finalSlice = new double[remainingFullWindows][elementsPerWindow]; //don't add room for unfinished window if there aren't any
			for (int j = 0; j < remainingFullWindows; j++) {
				for (int k = 0; k < elementsPerWindow; k++) {
					finalSlice[j][k] = firstChannelData[capturedData+j*elementsPerWindow+k];
				}
			}

			//Now deal with the remaining data that is not enough to fill a window

			if (finalWindowElements != 0) {
				for (int k = 0; k < finalWindowElements; k++) {
					finalSlice[remainingFullWindows][k] = firstChannelData[capturedData+remainingFullWindows*elementsPerWindow+k];
				}
			}

			audioSlices.add(finalSlice);
			System.out.println("Final audio slice added to list.");

		}
	}

	private void fillBitmapList() {
		int i = 0;
		while(true) {
			try {
				double[] spectroData = getCompositeWindow(i); //remember that this array is only half full, as required by JTransforms
				elements = spectroData.length/2;
				int[] toAdd = new int[elements];
				for (int j = 0; j < elements; j++) {
					if (maxAmplitude < spectroData[j]) maxAmplitude = spectroData[j];
					int val = cappedValue(spectroData[j]);
					if (inColour) toAdd[elements-j-1] = heatMap(val); //needs to be 'upside down' because of where y=0 is in graphics packages
					else toAdd[elements-j-1] = greyscale(val);
				}
				bitmapWindows.add(toAdd);
				i++;

			} catch (IndexOutOfBoundsException e) {
				System.out.println("Bitmap list filled. Number of windows: "+bitmapWindows.size());
				break;
			}
		}
	}

	private int heatMap(int val) {
		int toReturn = 255;
		toReturn <<= 8;
		toReturn += val; //red
		toReturn <<= 8; //one byte for each colour, MS byte is alpha
		toReturn += (int)(2*(127.5f-Math.abs(val-127.5f))); //green is 127.5 - |i-127.5| (draw it - peak at 127.5)
		toReturn <<= 8;
		toReturn += 255-val; //blue
		//System.out.println("Alpha: "+Color.alpha(toReturn)+" Red: "+Color.red(toReturn)+" Green: "+Color.green(toReturn)+" Blue: "+Color.blue(toReturn));
		return toReturn;
	}

	private int greyscale(int val) {
		int toReturn = 255;
		toReturn <<= 8;
		toReturn += val; 
		toReturn <<= 8; 
		toReturn += val;
		toReturn <<= 8;
		toReturn += val;
		//System.out.println("Alpha: "+Color.alpha(toReturn)+" Red: "+Color.red(toReturn)+" Green: "+Color.green(toReturn)+" Blue: "+Color.blue(toReturn));
		return toReturn;
	}

	private int cappedValue(double d) {
		//return an integer capped at 255 representing the given double value
		double dAbs = Math.abs(d);
		if (dAbs > maxAmplitude) return 255;
		if (dAbs < 1) return 0;
		double ml = Math.log1p(maxAmplitude);
		double dl = Math.log1p(dAbs);
		return (int)(dl*255/ml); //decibel is a log scale, want something linear
		//return (int) (dAbs*255/maxAmplitude); 
	}

	private void hammingWindow(double[] samples) {
		//generate an appropriate Hamming window for the window size
		int m = samples.length/2;
		double[] hamming = new double[samples.length];
		double r = Math.PI/(m+1);
		for (int i = -m; i < m; i++) {
			hamming[m + i] = 0.5 + 0.5 * Math.cos(i * r);
		}

		//apply windowing function through multiplication with time-domain samples
		for (int i = 0; i < samples.length; i++) {
			samples[i] *= hamming[i]; 
		}
	}

	@SuppressWarnings("unused")
	private void printHammingWindow(int length) {
		double[] samples = new double[length];
		for (int i = 0; i < samples.length; i++) {
			samples[i] = 1;
		}
		System.out.println("Samples: ");
		System.out.println();
		for (int i = 0; i < samples.length; i++) {
			System.out.print(samples[i]+"  ");
		}
		System.out.println();

		hammingWindow(samples);

		System.out.println("Window: ");
		System.out.println();
		for (int i = 0; i < samples.length; i++) {
			System.out.print(samples[i]+"  ");
		}
		System.out.println();
	}


	private void spectroTransform(double[] paddedSamples) { //calculate the squared STFT of the provided time-domain samples
		/* From JTransforms documentation:
		 * 
		 * 
		public void realForward(double[] a)
		Computes 1D forward DFT of real data leaving the result in a . The physical layout of the output data is as follows:
		if n is even then
		 a[2*k] = Re[k], 0<=k<n/2
		 a[2*k+1] = Im[k], 0<k<n/2
		 a[1] = Re[n/2]

		if n is odd then
		 a[2*k] = Re[k], 0<=k<(n+1)/2
		 a[2*k+1] = Im[k], 0<k<(n-1)/2
		 a[1] = Im[(n-1)/2]

		This method computes only half of the elements of the real transform.
		 The other half satisfies the symmetry condition. 
		 If you want the full real forward transform, use realForwardFull.
		  To get back the original data, use realInverse on the output of this method.

		 */
		DoubleFFT_1D d = new DoubleFFT_1D(paddedSamples.length / 2); //initialise with n, where n = data size
		d.realForward(paddedSamples);

		//Now the STFT has been calculated, need to square it:

		for (int i = 0; i < paddedSamples.length / 2; i++) {
			paddedSamples[i] *= paddedSamples[i];
		}
	}

	public double[] getSpectrogramWindow(int windowOffset) { //returns the spectrogram data for the requested window
		int sliceNumber = windowOffset/windowsPerSlice;
		return spectroSlices.get(sliceNumber)[windowOffset%windowsPerSlice];

	}

	public double[] getCompositeWindow(int windowOffset) {
		//assumes hop size is half of window size
		//last window will just throw an ArrayIndexOutOfBoundsException and stop the drawing
		double[] toReturn = new double[elementsPerWindow];

		double[] currentWindow = getSpectrogramWindow(windowOffset);
		double[] nextWindow = getSpectrogramWindow(windowOffset+1);
		double[] prevWindow;
		if (windowOffset == 0) {
			prevWindow = new double[elementsPerWindow]; //no previous window to look at

		}
		else prevWindow = getSpectrogramWindow(windowOffset-1);

		for (int i = 0; i < elementsPerWindow/2; i++) {
			toReturn[i] = 0.5*(currentWindow[i] + prevWindow[i]); //could get rid of 0.5s
		}
		for (int i = elementsPerWindow/2; i < elementsPerWindow; i++) {
			toReturn[i] = 0.5*(currentWindow[i] + nextWindow[i]);
		}


		return toReturn;
	}

	public int[] getBitmapWindow(int windowOffset) {
		//returns the pixel values for the specified composite window
		return bitmapWindows.get(windowOffset);
	}

	public int getWindowDuration() {
		return windowDuration;
	}

	public int getElementsPerWindow() {
		return elementsPerWindow;
	}


	public int getWindowsInFile() {
		return windowsInFile;
	}


}
