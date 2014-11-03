package uk.co.benjaminelliott.spectrogramandroid.storage;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;

import uk.co.benjaminelliott.spectrogramandroid.preferences.DynamicAudioConfig;
import android.graphics.Bitmap;
import android.location.Location;
import android.media.ExifInterface;
import android.os.Environment;
import android.util.Log;

/**
 * Class that helps in writing a capture to disk by saving the bitmap as a geotagged JPEG and the audio as
 * a WAV, as well as serialising all of the captured data as a {@link CapturedBitmapAudio} object.
 * @author Ben
 *
 */
public class AudioBitmapConverter  {

	private static final String TAG = "AudioBitmapConverter";
    private final double decLatitude;
    private final double decLongitude;
    private final String filename;
    private final int[] bitmapAsIntArray;
    private final byte[] wavAudio;
    private final int width;
    private final int height;
    private CapturedBitmapAudio cba;
    private Bitmap bitmap;

    public AudioBitmapConverter(String filename, DynamicAudioConfig dac, Bitmap bitmap, short[] rawWavAudio, Location loc) {
        this.filename = filename;
        this.bitmap = bitmap;
        if (loc != null) {
            decLatitude = loc.getLatitude();
            decLongitude = loc.getLongitude();
        } else {
            decLatitude = 0;
            decLongitude = 0;
        }
        wavAudio = WavUtils.wavFromAudio(rawWavAudio, dac.SAMPLE_RATE);
        width = bitmap.getWidth();
        height = bitmap.getHeight();
        bitmapAsIntArray = new int[width * height];
        bitmap.getPixels(bitmapAsIntArray, 0, width, 0, 0, width, height);
        cba = new CapturedBitmapAudio(filename, bitmapAsIntArray, wavAudio, width, height, decLatitude, decLongitude);
    }

    /**
     * Write the bitmap to a JPEG file and geotag it, then write the audio to a WAV file.
     */
    public void storeJPEGandWAV() {
        writeBitmapToJpegFile(bitmap, filename);
        geotagJpeg(filename, decLatitude, decLongitude);
        writeWavToFile(wavAudio, filename);
    }

    /**
     * Creates a JPEG from the provided bitmap and saves it under the provided filename.
     * @param bitmap - the bitmap to use to create the JPEG
     * @param filename - the name of the file under which it should be stored
     */
    private static void writeBitmapToJpegFile(Bitmap bitmap, String filename) {
        if (isExternalStorageWritable()) {
            File dir = getAlbumStorageDir(DynamicAudioConfig.STORE_DIR_NAME);
            FileOutputStream fos = null;
            try {
            	// keep incrementing filename until one is found that does not clash:
                int suffix = 0;
                File bmpFile = new File(dir.getAbsolutePath()+"/"+filename+".jpg");
                while (bmpFile.exists()) {
                    bmpFile = new File(dir.getAbsolutePath()+"/"+filename+"_"+suffix+".jpg");
                    suffix++;
                }
                fos = new FileOutputStream(bmpFile);
                // save the bitmap into the file:
                bitmap.compress(Bitmap.CompressFormat.JPEG, DynamicAudioConfig.BITMAP_STORE_QUALITY, fos);
            } catch (FileNotFoundException e) {
                Log.e(TAG,"Unable to create file",e);
            } finally {
                try {
                    fos.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } 
        else
        	Log.e(TAG,"External storage is not writable.");
    }

    /**
     * Geotag the JPEG at the specified filename with the specified latitude and longitude
     * @param filename - the filename of the file in the captures directory to geotag
     * @param decLatitude - the latitude (in decimal degrees)
     * @param decLongitude - the longitude (in decimal degrees)
     */
    private static void geotagJpeg(String filename, double decLatitude, double decLongitude) {
        File dir = getAlbumStorageDir(DynamicAudioConfig.STORE_DIR_NAME);
        String jpegFilepath = dir.getAbsolutePath()+"//"+filename+".jpg";
        try {
            ExifInterface exif = new ExifInterface(jpegFilepath);
            // add latitude and longitude to JPEG's EXIF:
            // latitude in degrees-minutes-seconds format:
            exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, convertDecToDMS(decLatitude));
            // North or South:
            exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, latitudeRef(decLatitude));
            // longitude in degrees-minutes-seconds format:
            exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, convertDecToDMS(decLongitude));
            // East or West:
            exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, longitudeRef(decLongitude));
            
            exif.saveAttributes();
        } catch (IOException e) {
            Log.e(TAG,"Error finding JPEG file for tagging: "+jpegFilepath);
            e.printStackTrace();
        }
    }



    /**
     * Write the supplied data (header and samples) to a WAV file.
     * @param data - the data to write
     * @param filename - the filename under which the audio should be stored
     */
    private static void writeWavToFile(byte[] data, String filename) {
        FileOutputStream fos = null;
        if (isExternalStorageWritable()) {
            File dir = getAlbumStorageDir(DynamicAudioConfig.STORE_DIR_NAME);
            try {
                File audioFile = new File(dir.getAbsolutePath()+"/"+filename+".wav");
                int suffix = 0;
                while (audioFile.exists()) {
                    audioFile = new File(dir.getAbsolutePath()+"/"+filename+"_"+suffix+".wav");
                    suffix++;
                }
                fos = new FileOutputStream(audioFile);
                fos.write(data);
                Log.d(TAG,"Audio file stored successfully at path "+audioFile.getAbsolutePath());
            } catch (FileNotFoundException e) {
                Log.d(TAG,"Unable to save audio file: "+dir.getAbsolutePath()+"/"+filename);
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


    /**
     * Return a "S" if latitude is south or "N" if north.
     * @param latitude
     * @return
     */
    public static String latitudeRef(double latitude) {
        return (latitude < 0) ? "S" : "N";
    }

    /**
     * Return a "W" if longitude is west or "E" if east.
     * @param latitude
     * @return
     */
    public static String longitudeRef(double longitude) {
        return (longitude <0) ? "W" : "E";
    }

    /**
     * Convert latitude or longitude expressed in degrees, to a degrees-minutes-seconds string.
     * 
     * INSPIRED BY http://stackoverflow.com/questions/5280479/how-to-save-gps-coordinates-in-exif-data-on-android
     * @param decDegreeCoord
     * @return
     */
    public static String convertDecToDMS(double decDegreeCoord) {
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

    //  --------------------- FROM ANDROID DEV DOCUMENTATION
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
    //  --------------------- 

    /**
     * Returns the provided bitmap's pixels as a new integer array.
     * @param bitmapAsIntArray
     * @param width
     * @param height
     * @return
     */
    public static int[] getBitmapPixels(int[] bitmapAsIntArray, int width, int height) {
        int[] ret = new int[width*height];
        for (int i = 0; i < bitmapAsIntArray.length; i++) {
            ret[i] = bitmapAsIntArray[i];
        }
        return ret;
    }

    /**
     * Serialise the user's capture to file using a {@link CapturedBitmapAudio} object.
     * @param cba
     * @param filename
     * @param directory
     */
    private static void writeCbaToFile(CapturedBitmapAudio cba, String filename, String directory) {
        if (AudioBitmapConverter.isExternalStorageWritable()) {
            File dir = AudioBitmapConverter.getAlbumStorageDir(directory);
            FileOutputStream fos = null;
            ObjectOutputStream oos = null;
            try {
            	// keep incrementing file suffix until file does not clash:
                int suffix = 0;
                File cbaFile = new File(dir.getAbsolutePath()+"/"+filename+CapturedBitmapAudio.EXTENSION);
                while (cbaFile.exists()) {
                    cbaFile = new File(dir.getAbsolutePath()+"/"+filename+"_"+suffix+".cba");
                    suffix++;
                }
                fos = new FileOutputStream(cbaFile);
                oos = new ObjectOutputStream(fos);
                oos.writeObject(cba);
            } catch (FileNotFoundException e) {
            	Log.e(TAG,"Unable to write to file: "+dir.getAbsolutePath()+"/"+filename, e);
            } catch (IOException e) {
            	Log.e(TAG,"Unable to write to file: "+dir.getAbsolutePath()+"/"+filename, e);
            } finally {
                try {
                    fos.close();
                } catch (IOException e) {
                	Log.e(TAG,"Error when closing file output stream");
                }
            }
        }
    }
    
    public void writeThisCbaToFile(String filename, String directory) {
        writeCbaToFile(cba, filename, directory);
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
}
