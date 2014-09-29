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

public class AudioBitmapConverter  {

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
        decLatitude = loc.getLatitude();
        decLongitude = loc.getLongitude();
        wavAudio = WavUtils.wavFromAudio(rawWavAudio, dac.SAMPLE_RATE);
        width = bitmap.getWidth();
        height = bitmap.getHeight();
        bitmapAsIntArray = new int[width*height];
        bitmap.getPixels(bitmapAsIntArray, 0, width, 0, 0, width, height);
        cba = new CapturedBitmapAudio(filename, bitmapAsIntArray, wavAudio, width, height, decLatitude, decLongitude);
    }

    public void storeJPEGandWAV() {
        writeBitmapToJpegFile(bitmap, filename);
        geotagJpeg(filename, decLatitude, decLongitude);
        writeWavToFile(wavAudio, filename);
    }

    private static void writeBitmapToJpegFile(Bitmap bitmap, String filename) {
        if (isExternalStorageWritable()) {
            File dir = getAlbumStorageDir(DynamicAudioConfig.STORE_DIR_NAME);
            FileOutputStream fos = null;
            try {
                int suffix = 0;
                File bmpFile = new File(dir.getAbsolutePath()+"/"+filename+".jpg");
                while (bmpFile.exists()) {
                    bmpFile = new File(dir.getAbsolutePath()+"/"+filename+"_"+suffix+".jpg");
                    suffix++;
                }
                fos = new FileOutputStream(bmpFile);
                bitmap.compress(Bitmap.CompressFormat.JPEG, DynamicAudioConfig.BITMAP_STORE_QUALITY, fos);
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

    private static void geotagJpeg(String filename, double decLatitude, double decLongitude) {
        File dir = getAlbumStorageDir(DynamicAudioConfig.STORE_DIR_NAME);
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


    public static String latitudeRef(double latitude) {
        return (latitude < 0) ? "S" : "N";
    }

    public static String longitudeRef(double longitude) {
        return (longitude <0) ? "W" : "E";
    }

    //INSPIRED BY http://stackoverflow.com/questions/5280479/how-to-save-gps-coordinates-in-exif-data-on-android
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
    /* 	======================	*/

    public static int[] getBitmapPixels(int[] bitmapAsIntArray, int width, int height) {
        int[] ret = new int[width*height];
        for (int i = 0; i < bitmapAsIntArray.length; i++) {
            ret[i] = bitmapAsIntArray[i];
        }
        return ret;
    }

    private static void writeCbaToFile(CapturedBitmapAudio cba, String filename, String directory) {
        if (AudioBitmapConverter.isExternalStorageWritable()) {
            File dir = AudioBitmapConverter.getAlbumStorageDir(directory);
            FileOutputStream fos = null;
            ObjectOutputStream oos = null;
            try {
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
