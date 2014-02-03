package uk.co.benjaminelliott.spectrogramandroid;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.location.Location;
import android.media.ExifInterface;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.Log;

public class AudioBitmapConverter  {

	private final int SAMPLE_RATE;
	private final double decLatitude;
	private final double decLongitude;
	private final String filename;
	private final int[] bitmapAsIntArray;
	private final byte[] wavAudio;
	private transient String directory;
	private final int width;
	private final int height;
	private CapturedBitmapAudio cba;
	
	AudioBitmapConverter(String filename, String directory, Bitmap bitmap, short[] rawWavAudio, Location loc, int sampleRate) {
		this.filename = filename;
		this.directory = directory;
		this.SAMPLE_RATE = sampleRate;
		decLatitude = loc.getLatitude();
		decLongitude = loc.getLongitude();
		wavAudio = wavAsByteArray(rawWavAudio);
		writeBitmapToJPEG(bitmap);
		geotagJPEG();
		writeAudioToWAV();
		width = bitmap.getWidth();
		height = bitmap.getHeight();
		bitmapAsIntArray = new int[width*height];
		bitmap.getPixels(bitmapAsIntArray, 0, width, 0, 0, width, height);
		cba = new CapturedBitmapAudio(filename, bitmapAsIntArray, wavAudio, width, height, decLatitude, decLongitude);
	}
	
	
	
	private byte[] wavAsByteArray(short[] rawWavAudio) {
		byte[] ret = new byte[44+rawWavAudio.length*2];
		byte[] header = getWAVHeader(SAMPLE_RATE, 16, rawWavAudio.length*2, 1);
		for (int i = 0; i < 44; i++) {
			ret[i] = header[i];
		}
		int j = 44;
		for (int i = 0; i < rawWavAudio.length; i++) {
			ret[j] =  (byte)((rawWavAudio[i] >> 8) & 0xff);
			j++;
			ret[j] = (byte)(rawWavAudio[i] & 0xff);
			j++;
		}
		return ret;
	}
	

	
	private void writeBitmapToJPEG(Bitmap bitmap) {
		if (isExternalStorageWritable()) {
			File dir = getAlbumStorageDir(directory);
			FileOutputStream fos = null;
			try {
				int suffix = 0;
				String fnm = filename;
				File bmpFile = new File(dir.getAbsolutePath()+"/"+fnm+".jpg");
				while (bmpFile.exists()) {
					fnm = fnm+"_"+suffix;
					bmpFile = new File(dir.getAbsolutePath()+"/"+fnm+".jpg");
				}
				fos = new FileOutputStream(bmpFile);
				bitmap.compress(Bitmap.CompressFormat.JPEG, BitmapGenerator.BITMAP_STORE_QUALITY, fos);
				Log.d("","Bitmap stored successfully at path "+bmpFile.getAbsolutePath());
			} catch (FileNotFoundException e) {
				Log.d("","File not found. Path: "+dir.getAbsolutePath()+"/"+filename);
				e.printStackTrace();
			} finally {
				try {
					fos.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}
	
	private void geotagJPEG() {
			File dir = getAlbumStorageDir(directory);
			String jpegFilepath = dir.getAbsolutePath()+"//"+filename+".jpg";
			try {
				Log.d("StoredBitmapAudio","Opening EXIF data for "+jpegFilepath);
				ExifInterface exif = new ExifInterface(jpegFilepath);
				exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, convertDecToDMS(decLatitude));
				exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, latitudeRef(decLatitude));

				exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, convertDecToDMS(decLongitude));
				exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, longitudeRef(decLongitude));
				exif.saveAttributes();
			} catch (IOException e) {
				Log.e("StoredBitmapAudio","Error finding JPEG file for tagging, path: "+jpegFilepath);
				e.printStackTrace();
			}
		}
	

	
	private void writeAudioToWAV() {
		FileOutputStream fos = null;
		if (isExternalStorageWritable()) {
			File dir = getAlbumStorageDir(directory);
			try {
				String fnm = filename;
				File audioFile = new File(dir.getAbsolutePath()+"/"+fnm+".wav");
				int suffix = 0;
				while (audioFile.exists()) {
					fnm = fnm+"_"+suffix;
					audioFile = new File(dir.getAbsolutePath()+"/"+fnm+".wav");
					suffix++;
				}
				fos = new FileOutputStream(audioFile);
				fos.write(wavAudio);
				Log.d("","Audio file stored successfully at path "+audioFile.getAbsolutePath());
			} catch (FileNotFoundException e) {
				Log.d("","File not found. Path: "+dir.getAbsolutePath()+"/"+filename);
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			} finally {
				try {
					fos.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}
	

    private String latitudeRef(double latitude) {
        return (latitude < 0) ? "S" : "N";
    }

    public static String longitudeRef(double longitude) {
        return (longitude <0) ? "W" : "E";
    }

    //INSPIRED BY http://stackoverflow.com/questions/5280479/how-to-save-gps-coordinates-in-exif-data-on-android
    private String convertDecToDMS(double decDegreeCoord) {
    	//decimal degree coordinate could be latitude or longitude.
    	// see http://en.wikipedia.org/wiki/Geographic_coordinate_conversion#Conversion_from_Decimal_Degree_to_DMS
    	if (decDegreeCoord < 0) decDegreeCoord = -decDegreeCoord;
    	
    	//Degrees = whole number portion of coordinate
        int degrees = (int) decDegreeCoord;
        
        //Minutes = whole number portion of (remainder*60)
        decDegreeCoord -= degrees;
        decDegreeCoord *= 60;
        int minutes = (int) decDegreeCoord;
        
        //Seconds = whole number portion of (remainder*60)
        decDegreeCoord -= minutes;
        decDegreeCoord *= 60;
        int seconds = (int) (decDegreeCoord*1000); // convention is deg/1, min/1, sec/1000

        return degrees+"/1,"+minutes+"/1,"+seconds+"/1000";
    }
    
    private byte[] getWAVHeader(int sampleRate, int bitsPerSample, int audioDataLength, int numChannels) {
		byte[] header = new byte[44];
		//NOTE all fields are little-endian unless they contain characters
		//See https://ccrma.stanford.edu/courses/422/projects/WaveFormat/
		
		/* RIFF HEADER */
		//ChunkID:
        header[0] = 'R';  
        header[1] = 'I';
        header[2] = 'F';
        header[3] = 'F';
        //ChunkSide = size of entire file - 8 bytes for ChunkID and ChunkSize
		int totalLength = audioDataLength + 36; //44 bytes for header - 8 bytes
        header[4] = (byte) (totalLength & 0xff); //mask all but LSB
        header[5] = (byte) ((totalLength >> 8) & 0xff); //shift right and mask all but LSB (now 2nd LSB)
        header[6] = (byte) ((totalLength >> 16) & 0xff); //etc
        header[7] = (byte) ((totalLength >> 24) & 0xff);
        //Format:
        header[8] = 'W';
        header[9] = 'A';
        header[10] = 'V';
        header[11] = 'E';
        
        /* "fmt " (note space) SUBCHUNK */
        //SubChunk1ID:
        header[12] = 'f';  
        header[13] = 'm';
        header[14] = 't';
        header[15] = ' ';
        //SubChunk1Size:
        header[16] = 16;  //16 for PCM (remember little-endian)
        header[17] = 0;
        header[18] = 0;
        header[19] = 0;
        //AudioFormat:
        header[20] = 1;  //1 for PCM (linear quantization; no compression) 
        header[21] = 0;
        //NumChannels:
        header[22] = (byte) numChannels;
        header[23] = 0;
        //SampleRate:
        header[24] = (byte) (sampleRate & 0xff);
        header[25] = (byte) ((sampleRate >> 8) & 0xff);
        header[26] = (byte) ((sampleRate >> 16) & 0xff);
        header[27] = (byte) ((sampleRate >> 24) & 0xff);
        //ByteRate = SampleRate*NumChannels*BitsPerSample/8:
		int byteRate = numChannels * sampleRate * bitsPerSample / 8;
        header[28] = (byte) (byteRate & 0xff);
        header[29] = (byte) ((byteRate >> 8) & 0xff);
        header[30] = (byte) ((byteRate >> 16) & 0xff);
        header[31] = (byte) ((byteRate >> 24) & 0xff);
        //BlockAlign = NumChannels*BitsPerSample/8;
        header[32] = (byte) (2 * 16 / 8); 
        header[33] = 0;
        //BitsPerSample:
        header[34] = (byte) bitsPerSample;  
        header[35] = 0;
        
        /* "data" SUBCHUNK */
        //SubChunk2ID:
        header[36] = 'd';
        header[37] = 'a';
        header[38] = 't';
        header[39] = 'a';
        //SubChunk2Size:
        header[40] = (byte) (audioDataLength & 0xff);
        header[41] = (byte) ((audioDataLength >> 8) & 0xff);
        header[42] = (byte) ((audioDataLength >> 16) & 0xff);
        header[43] = (byte) ((audioDataLength >> 24) & 0xff);
        //actual data will go here
		return header;
	}
    
    /* FROM ANDROID DEV DOCUMENTATION */
	public static boolean isExternalStorageWritable() {
	    String state = Environment.getExternalStorageState();
	    if (Environment.MEDIA_MOUNTED.equals(state)) {
	        return true;
	    }
	    return false;
	}
	
	public static File getAlbumStorageDir(String albumName) {
	    // Get the directory for the user's public pictures directory.
	    File file = new File(Environment.getExternalStoragePublicDirectory(
	            Environment.DIRECTORY_PICTURES), albumName);
	    if (!file.mkdirs()) {
	        Log.e("", "Directory not created");
	    }
	    return file;
	}
	/* 		*/
	
	public int[] getBitmapPixels() {
		int[] ret = new int[width*height];
		for (int i = 0; i < bitmapAsIntArray.length; i++) {
			ret[i] = bitmapAsIntArray[i];
		}
		return ret;
	}
	
	public int getWidth() {
		return width;
	}
	
	public int getHeight() {
		return height;
	}
	
	public CapturedBitmapAudio getCBA() {
		return cba;
	}
	
    protected void writeCBAToFile(String filename, String directory) {
		if (AudioBitmapConverter.isExternalStorageWritable()) {
			File dir = AudioBitmapConverter.getAlbumStorageDir(directory);
			FileOutputStream fos = null;
			ObjectOutputStream oos = null;
			try {
				int suffix = 0;
				File cbaFile = new File(dir.getAbsolutePath()+"/"+filename+CapturedBitmapAudio.EXTENSION);
				while (cbaFile.exists()) {
					filename = filename+suffix;
					cbaFile = new File(dir.getAbsolutePath()+"/"+filename+CapturedBitmapAudio.EXTENSION);
				}
				fos = new FileOutputStream(cbaFile);
				oos = new ObjectOutputStream(fos);
				oos.writeObject(cba);
			} catch (FileNotFoundException e) {
				Log.d("","File not found. Path: "+dir.getAbsolutePath()+"/"+filename);
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			} finally {
				try {
					fos.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}

}
