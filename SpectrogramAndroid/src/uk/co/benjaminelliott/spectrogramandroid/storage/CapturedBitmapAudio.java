package uk.co.benjaminelliott.spectrogramandroid.storage;

import java.io.Serializable;

/**
 * A serializable class which allows for the audio data, bitmap data and parameters to be packaged up
 * and unpackaged at the other end by the server.
 * 
 * 
 * TODO: Move away from serialisation, which is notoriously slow on Android.
 */

public class CapturedBitmapAudio implements Serializable {

	public static final long serialVersionUID = 2L;
	public static final String EXTENSION = ".cba";
	public final double decLatitude;
	public final double decLongitude;
	public final String filename;
	public final int[] bitmapAsIntArray;
	public final byte[] wavAsByteArray;
	public final int bitmapWidth;
	public final int bitmapHeight;
	
	CapturedBitmapAudio(String filename, int[] bitmapAsIntArray, byte[] wavAsByteArray, int bitmapWidth, int bitmapHeight, double decLatitude, double decLongitude) {
		this.filename = filename;
		this.bitmapAsIntArray = bitmapAsIntArray;
		this.wavAsByteArray = wavAsByteArray;
		this.decLatitude = decLatitude;
		this.decLongitude = decLongitude;
		this.bitmapWidth = bitmapWidth;
		this.bitmapHeight = bitmapHeight;

	}
	
	/**
	 * Creates and returns new array containing the bitmap's 
	 * RGB pixel values.
	 */
	public int[] getBitmapRGBPixels() {
		int[] ret = new int[bitmapAsIntArray.length];
		for (int i = 0; i < bitmapAsIntArray.length; i++) {
			ret[i] = 0xffffff&bitmapAsIntArray[i];
		}
		return ret;
	}

}
