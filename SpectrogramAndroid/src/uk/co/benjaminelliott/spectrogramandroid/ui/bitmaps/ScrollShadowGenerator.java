package uk.co.benjaminelliott.spectrogramandroid.ui.bitmaps;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

public class ScrollShadowGenerator {
    
    /**
     * Draws "fading" scroll shadows on the supplied Bitmap objects according to the width,
     * height and spread parameters supplied.
     */
    public static void generateScrollShadows(Bitmap leftShadow, Bitmap rightShadow, int width, int height, int spread) {
        Canvas leftShadowCanvas = new Canvas(leftShadow);
        Canvas rightShadowCanvas = new Canvas(rightShadow);
        
        Paint paint = new Paint();
        paint.setColor(Color.BLACK);
        
        for (int i = 0; i < spread; i++) {
            paint.setAlpha((spread - i)*255/spread); // becomes more transparent closer to centre
            leftShadowCanvas.drawRect(i, 0, i+1, height, paint); //draw left shadow
            rightShadowCanvas.drawRect(width-i-1, 0, width-i, height, paint); //draw right shadow
        }
    }

}
