package bge23.spectrogramandroid;

import java.io.File;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.media.MediaPlayer;
import android.os.Environment;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

public class SpectrogramSurfaceView extends SurfaceView implements SurfaceHolder.Callback {

	private static final int width = 700; //width of spectrogram component in pixels
	private static final int height = 700; //width of spectrogram component in pixels

	private Spectrogram spec;
	private int windowDuration = 32; //draw a new window in time with the audio file
	private int windowsDrawn = 0; //how many windows have been drawn already
	private SurfaceHolder sh;
	private Context ctx;
	private SpectroThread sth;
	private int h; //TODO rework
	private Canvas displayCanvas;
	private Bitmap buffer;
	private Canvas bufferCanvas;
	private MediaPlayer player;
	

	public SpectrogramSurfaceView(Context context) throws IOException{
		super(context);
		displayCanvas = null;
		buffer = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
		bufferCanvas = new Canvas(buffer);
		ctx = context;
		sh = getHolder();
		sh.addCallback(this);
		String filename = "woodpecker.wav"; //alternatives are cuckoo and lapwing
		String filepath = Environment.getExternalStorageDirectory().getAbsolutePath()+"/"+filename;
		spec = new Spectrogram(filepath);
		windowDuration = spec.getWindowDuration();
		System.out.println("Number of windows in input: "+spec.getWindowsInFile());
		h = spec.elements;

		//play wav file simultaneously with showing spectrogram:
		player = new MediaPlayer();
		File f = new File(filepath);
		f.setReadable(true,false); //need to set permissions so that it can be read by the media player
		player.setDataSource(filepath);
		player.prepare();

	}

	public void surfaceChanged(SurfaceHolder holder, int format, int width,int height)  {      
		sth.setSurfaceSize(width, height);
	}

	@Override
	public void surfaceCreated(SurfaceHolder arg0) {
		sth = new SpectroThread(sh,ctx,new Handler());
		sth.setRunning(true);
		Timer timer = new Timer();
		timer.schedule(sth,0,windowDuration);
		player.start(); //TODO release
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder arg0) {
		sth.setRunning(false);
		//TODO: worry about interrupted something something something
	}

	class SpectroThread extends TimerTask {
		private int width = 500;
		private int height = 500;
		private boolean run = true;
		



		public SpectroThread(SurfaceHolder surfaceHolder, Context context, Handler handler) {
			sh = surfaceHolder;
			handler = handler;
			ctx = context;
		}

		public void doStart() { //TODO
			synchronized(sh){

			}
		}

		public void run() {
			if (run) {
				try {
					displayCanvas = sh.lockCanvas(null);
					synchronized (sh) {
						doDraw(); //update buffer bitmap
						displayCanvas.drawBitmap(buffer, 0, 0, null);
					}
				} finally {
					if (displayCanvas != null) {
						sh.unlockCanvasAndPost(displayCanvas);
					}
				}
			}
		}


		public void setRunning(boolean b) {
			run = b;
		}

		public void setSurfaceSize(int w, int h) {
			synchronized (sh) {
				this.width = w;
				this.height = h;
				doStart();
			}
		}
		
		private void doDraw() {
			try {
				int scaleFactor = 2;
				Bitmap orig = Bitmap.createBitmap(spec.getBitmapWindow(windowsDrawn), 0, 1, 1, h, Bitmap.Config.ARGB_8888);
				Bitmap scaled = scaleBitmap(orig,scaleFactor,h);
				bufferCanvas.drawBitmap(scaled,windowsDrawn*scaleFactor,0f,null);

				System.out.println("Window "+windowsDrawn+" drawn with (left, top) coordinate at ("+(float)windowsDrawn+","+0f+"), density "+scaled.getDensity());
				windowsDrawn++;
			} catch (IndexOutOfBoundsException e) {
				run = false;
			}
		}
		
		public Bitmap scaleBitmap(Bitmap bitmapToScale, float newWidth, float newHeight) {   
			if(bitmapToScale == null)
			    return null;
			//get the original width and height
			int width = bitmapToScale.getWidth();
			int height = bitmapToScale.getHeight();
			// create a matrix for the manipulation
			Matrix matrix = new Matrix();

			// resize the bit map
			matrix.postScale(newWidth / width, newHeight / height);

			// recreate the new Bitmap and set it back
			return Bitmap.createBitmap(bitmapToScale, 0, 0, bitmapToScale.getWidth(), bitmapToScale.getHeight(), matrix, true);  }
		
		private void doChunkDraw(Canvas canvas) { //TODO this is just for testing right now
			int chunkwidth = 4;
			int[] chunkmap = new int[chunkwidth*h];
			for (int i = 0; i < chunkwidth; i++) {
				for (int j = 0; j < h; j++) {
					chunkmap[i*h+j] = spec.getBitmapWindow(windowsDrawn + i)[j];
				}
			}
			Bitmap ch = Bitmap.createBitmap(chunkmap, h, chunkwidth, Bitmap.Config.ARGB_8888);
			ch.setDensity(Bitmap.DENSITY_NONE);
			Bitmap rotatedBitmap = rotateBitmap(ch,90);
			ch.setDensity(Bitmap.DENSITY_NONE);
			canvas.drawBitmap(rotatedBitmap, windowsDrawn, 0, null);
			windowsDrawn += chunkwidth;
		}
		
		public Bitmap rotateBitmap(Bitmap source, float angle){
		      Matrix matrix = new Matrix();
		      matrix.postRotate(angle);
		      return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
		}

		/*private void doDraw(Canvas canvas) {

			double[] spectroData = spec.getCompositeWindow(windowsDrawn); //remember that this array is only half full, as required by JTransforms
			int elements = spectroData.length/2;
			for (int i = elements-1; i >= 0; i--) {
				if (maxAmplitude < spectroData[i]) maxAmplitude = spectroData[i];
				int val = 255-cappedValue(spectroData[i]);
				paint.setColor(heatMap(val)); //colour heat map TODO make this work
				//paint.setColor((int) spectroData[i]);
				canvas.drawRect(width-pixelWidth, (elements-i)*pixelHeight, width, (elements-i)*pixelHeight+pixelHeight,paint);
				System.out.println("draw color"+paint.getColor());
			}
			System.out.println("Windows drawn: "+windowsDrawn);
			windowsDrawn++;
		}
		*/
	}
}
