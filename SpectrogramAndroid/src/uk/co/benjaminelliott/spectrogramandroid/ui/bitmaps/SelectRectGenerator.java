package uk.co.benjaminelliott.spectrogramandroid.ui.bitmaps;

import uk.co.benjaminelliott.spectrogramandroid.preferences.UiConfig;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;

public class SelectRectGenerator {
    
    /**
     * Draws a selection rectangle on the provided bitmap according to the dimensions supplied.
     */
    public static void generateSelectRect(float selectRectL, float selectRectT, float selectRectR, float selectRectB, Bitmap destBitmap) {
        Canvas destCanvas = new Canvas(destBitmap);
        Paint paint = new Paint();
        // draw sides of rectangle, then draggable corners on top:
        
        //draw sides (outer)
        paint.setColor(UiConfig.SELECT_RECT_OUTER_COLOUR);
        paint.setStrokeWidth(UiConfig.SELECT_RECT_OUTER_STROKE);
        destCanvas.drawLine(selectRectL, selectRectB, selectRectR, selectRectB, paint);
        destCanvas.drawLine(selectRectR, selectRectB, selectRectR, selectRectT, paint);
        destCanvas.drawLine(selectRectL, selectRectT, selectRectR, selectRectT, paint);
        destCanvas.drawLine(selectRectL, selectRectB, selectRectL, selectRectT, paint);
        // draw sides (inner)
        paint.setColor(UiConfig.SELECT_RECT_INNER_COLOUR);
        paint.setStrokeWidth(UiConfig.SELECT_RECT_INNER_STROKE);
        destCanvas.drawLine(selectRectL, selectRectB, selectRectR, selectRectB, paint);
        destCanvas.drawLine(selectRectR, selectRectB, selectRectR, selectRectT, paint);
        destCanvas.drawLine(selectRectL, selectRectT, selectRectR, selectRectT, paint);
        destCanvas.drawLine(selectRectL, selectRectB, selectRectL, selectRectT, paint);
        //draw corners (outer)
        paint.setColor(UiConfig.SELECT_RECT_OUTER_COLOUR);
        destCanvas.drawRect(selectRectL-UiConfig.SELECT_RECT_OUTER_CORNER_WIDTH_HALVED, selectRectB+UiConfig.SELECT_RECT_OUTER_CORNER_WIDTH_HALVED, selectRectL+UiConfig.SELECT_RECT_OUTER_CORNER_WIDTH_HALVED, selectRectB-UiConfig.SELECT_RECT_OUTER_CORNER_WIDTH_HALVED, paint);
        destCanvas.drawRect(selectRectR-UiConfig.SELECT_RECT_OUTER_CORNER_WIDTH_HALVED, selectRectB+UiConfig.SELECT_RECT_OUTER_CORNER_WIDTH_HALVED, selectRectR+UiConfig.SELECT_RECT_OUTER_CORNER_WIDTH_HALVED, selectRectB-UiConfig.SELECT_RECT_OUTER_CORNER_WIDTH_HALVED, paint);
        destCanvas.drawRect(selectRectL-UiConfig.SELECT_RECT_OUTER_CORNER_WIDTH_HALVED, selectRectT+UiConfig.SELECT_RECT_OUTER_CORNER_WIDTH_HALVED, selectRectL+UiConfig.SELECT_RECT_OUTER_CORNER_WIDTH_HALVED, selectRectT-UiConfig.SELECT_RECT_OUTER_CORNER_WIDTH_HALVED, paint);
        destCanvas.drawRect(selectRectR-UiConfig.SELECT_RECT_OUTER_CORNER_WIDTH_HALVED, selectRectT+UiConfig.SELECT_RECT_OUTER_CORNER_WIDTH_HALVED, selectRectR+UiConfig.SELECT_RECT_OUTER_CORNER_WIDTH_HALVED, selectRectT-UiConfig.SELECT_RECT_OUTER_CORNER_WIDTH_HALVED, paint);
        // draw corners (inner)
        paint.setColor(UiConfig.SELECT_RECT_INNER_COLOUR);
        destCanvas.drawRect(selectRectL-UiConfig.SELECT_RECT_INNER_CORNER_WIDTH_HALVED, selectRectB+UiConfig.SELECT_RECT_INNER_CORNER_WIDTH_HALVED, selectRectL+UiConfig.SELECT_RECT_INNER_CORNER_WIDTH_HALVED, selectRectB-UiConfig.SELECT_RECT_INNER_CORNER_WIDTH_HALVED, paint);
        destCanvas.drawRect(selectRectR-UiConfig.SELECT_RECT_INNER_CORNER_WIDTH_HALVED, selectRectB+UiConfig.SELECT_RECT_INNER_CORNER_WIDTH_HALVED, selectRectR+UiConfig.SELECT_RECT_INNER_CORNER_WIDTH_HALVED, selectRectB-UiConfig.SELECT_RECT_INNER_CORNER_WIDTH_HALVED, paint);
        destCanvas.drawRect(selectRectL-UiConfig.SELECT_RECT_INNER_CORNER_WIDTH_HALVED, selectRectT+UiConfig.SELECT_RECT_INNER_CORNER_WIDTH_HALVED, selectRectL+UiConfig.SELECT_RECT_INNER_CORNER_WIDTH_HALVED, selectRectT-UiConfig.SELECT_RECT_INNER_CORNER_WIDTH_HALVED, paint);
        destCanvas.drawRect(selectRectR-UiConfig.SELECT_RECT_INNER_CORNER_WIDTH_HALVED, selectRectT+UiConfig.SELECT_RECT_INNER_CORNER_WIDTH_HALVED, selectRectR+UiConfig.SELECT_RECT_INNER_CORNER_WIDTH_HALVED, selectRectT-UiConfig.SELECT_RECT_INNER_CORNER_WIDTH_HALVED, paint);

    }

}
