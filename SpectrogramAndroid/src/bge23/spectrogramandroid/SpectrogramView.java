package bge23.spectrogramandroid;

import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.media.MediaPlayer;
import android.util.AttributeSet;
import android.view.View;

public class SpectrogramView extends View {

	private static final long serialVersionUID = 1L;
	private static final int width = 1024; //width of spectrogram component in pixels
	private static final int height = 768; //width of spectrogram component in pixels
	private double maxAmplitude = 1;//= 100000000000d; //largest value seen so far, scale colours accordingly
	
	private Bitmap buffer;
	private Bitmap bi;
	private Canvas g2buffer;
	private Canvas g2current; //current buffer to display;
	private Spectrogram spec;
	private int windowDuration; //draw a new window in time with the audio file
	private int windowsDrawn = 0; //how many windows have been drawn already
	private int pixelWidth = 1;
	private Timer timer;
	private int[] heatMap;

	public SpectrogramView(Context context, AttributeSet attrs) throws IOException{
		super(context,attrs);
		String filepath = "/res/raw/woodpecker.wav";
		spec = new Spectrogram(filepath);
		windowDuration = spec.getWindowDuration();
		System.out.println("Number of windows in input: "+spec.getWindowsInFile());
		buffer = Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
		bi = Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
		g2buffer = new Canvas(buffer);
		g2current = new Canvas(bi);
		/*g2buffer.setColor(Color.BLACK);
		g2buffer.clearRect(0,0,width,height);*/
		
		heatMap = new int[256];
		for (int i = 0; i < 256; i++) {
			heatMap[i] = i; //red
			heatMap[i] <<= 8; //one byte for each colour, MS byte is alpha
			heatMap[i] += (int)(2*(127.5f-Math.abs(i-127.5f))); //green is 127.5 - |i-127.5| (draw it - peak at 127.5)
			heatMap[i] <<= 8;
			heatMap[i]  += 255-i; //blue
		}
		
		//play wav file simultaneously with showing spectrogram:
	    MediaPlayer player = new MediaPlayer();
	    player.setDataSource(filepath);
	    player.prepare();
	    
		timer = new Timer();
		timer.schedule(new TimerTask() {
			public void run() { //timer executes 'step()' method on each window-length time interval, beginning immediately [0 delay]
				try{step();} catch (ArrayIndexOutOfBoundsException e) {cancel();}
			}
		}, 0, windowDuration);
		System.out.println("WINDOW DURATION " +windowDuration);
		

	    player.start();
	}

	public void step() { //'public' to allow access from timer thread
		double[] spectroData = spec.getCompositeWindow(windowsDrawn); //remember that this array is only half full, as required by JTransforms
		int elements = spectroData.length/2;
		int pixelHeight = (height/elements > 1) ? height/elements : 1;

		Bitmap shifted = Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
		Canvas g2dshifted = new Canvas(shifted);
		g2dshifted.drawBitmap(bi, -pixelWidth, 0, new Paint());
		g2dshifted.drawBitmap(shifted, 0, 0, new Paint());
		
		Paint p = new Paint();
		for (int i = elements-1; i >= 0; i--) {
			if (maxAmplitude < spectroData[i]) maxAmplitude = spectroData[i];
			int val = 255-cappedValue(spectroData[i]); //TODO: scale this properly!
			//g2current.setColor(new Color(val,val,val)); //greyscale
			p.setColor(heatMap[255-val]); //colour heat map
			//g2current.drawRect(width-pixelWidth, (elements-i)*pixelHeight, pixelWidth, pixelHeight);
			g2current.drawRect(width-pixelWidth, (elements-i)*pixelHeight, width, (elements-i)*pixelHeight+pixelHeight,p);
		}

		g2buffer.drawBitmap(bi, 0, 0, new Paint());

		System.out.println("Windows drawn: "+windowsDrawn);
		windowsDrawn++;
		invalidate();
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
	
	/*
	public void scroll(int offset) {
		try {
			synchronized(timer){
				timer.wait(); //stop the timer from drawing
				c.stop(); //stop the audio -- this does not restart it
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		//TODO: reset display properly - this still doesn't work
		int oldWindowsDrawn = windowsDrawn;
		int currentLeftmostWindow = windowsDrawn-(width/pixelWidth);
		final int newLeftmostWindow = currentLeftmostWindow + offset;
		windowsDrawn = newLeftmostWindow;
		Timer timer2 = new Timer();
		timer2.schedule(new TimerTask() {
			public void run() { //timer executes 'step()' method on each window-length time interval, beginning immediately [0 delay]
				try {
					if (windowsDrawn < newLeftmostWindow+(width/pixelWidth)) step();
					else cancel();
				} catch (ArrayIndexOutOfBoundsException e) {
					cancel();
				}
			}
		}, 0, 10); //TODO: choose a good value for '10'
	}
	
	public void resume() {
		synchronized(timer){
			timer.notify(); 
			c.start(); //stop the audio
		}
	}
*/


}
