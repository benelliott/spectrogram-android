package uk.co.benjaminelliott.spectrogramandroid.ui.bitmaps;

import uk.co.benjaminelliott.spectrogramandroid.preferences.UiConfig;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

public class SelectRectGenerator {
    
    /**
     * Draws a selection rectangle on the provided buffer according to the dimensions supplied.
     */
    public static void generateSelectRect(float selectRectL, float selectRectT, float selectRectR, float selectRectB, Bitmap destBitmap) {
        Canvas destCanvas = new Canvas(destBitmap);
        Paint paint = new Paint();

        //draw select-area rectangle
        paint.setColor(UiConfig.SELECT_RECT_OUTER_COLOUR);
        paint.setStrokeWidth(UiConfig.SELECT_RECT_OUTER_STROKE);
        destCanvas.drawLine(selectRectL, selectRectB, selectRectR, selectRectB, paint);
        destCanvas.drawLine(selectRectR, selectRectB, selectRectR, selectRectT, paint);
        destCanvas.drawLine(selectRectL, selectRectT, selectRectR, selectRectT, paint);
        destCanvas.drawLine(selectRectL, selectRectB, selectRectL, selectRectT, paint);

        paint.setColor(UiConfig.SELECT_RECT_INNER_COLOUR);
        paint.setStrokeWidth(UiConfig.SELECT_RECT_INNER_STROKE);
        destCanvas.drawLine(selectRectL, selectRectB, selectRectR, selectRectB, paint);
        destCanvas.drawLine(selectRectR, selectRectB, selectRectR, selectRectT, paint);
        destCanvas.drawLine(selectRectL, selectRectT, selectRectR, selectRectT, paint);
        destCanvas.drawLine(selectRectL, selectRectB, selectRectL, selectRectT, paint);

        //draw draggable corners
        paint.setColor(Color.BLACK);
        destCanvas.drawRect(selectRectL-UiConfig.SELECT_RECT_OUTER_CORNER_WIDTH_HALVED, selectRectB+UiConfig.SELECT_RECT_OUTER_CORNER_WIDTH_HALVED, selectRectL+UiConfig.SELECT_RECT_OUTER_CORNER_WIDTH_HALVED, selectRectB-UiConfig.SELECT_RECT_OUTER_CORNER_WIDTH_HALVED, paint);
        destCanvas.drawRect(selectRectR-UiConfig.SELECT_RECT_OUTER_CORNER_WIDTH_HALVED, selectRectB+UiConfig.SELECT_RECT_OUTER_CORNER_WIDTH_HALVED, selectRectR+UiConfig.SELECT_RECT_OUTER_CORNER_WIDTH_HALVED, selectRectB-UiConfig.SELECT_RECT_OUTER_CORNER_WIDTH_HALVED, paint);
        destCanvas.drawRect(selectRectL-UiConfig.SELECT_RECT_OUTER_CORNER_WIDTH_HALVED, selectRectT+UiConfig.SELECT_RECT_OUTER_CORNER_WIDTH_HALVED, selectRectL+UiConfig.SELECT_RECT_OUTER_CORNER_WIDTH_HALVED, selectRectT-UiConfig.SELECT_RECT_OUTER_CORNER_WIDTH_HALVED, paint);
        destCanvas.drawRect(selectRectR-UiConfig.SELECT_RECT_OUTER_CORNER_WIDTH_HALVED, selectRectT+UiConfig.SELECT_RECT_OUTER_CORNER_WIDTH_HALVED, selectRectR+UiConfig.SELECT_RECT_OUTER_CORNER_WIDTH_HALVED, selectRectT-UiConfig.SELECT_RECT_OUTER_CORNER_WIDTH_HALVED, paint);

        paint.setColor(Color.WHITE);
        destCanvas.drawRect(selectRectL-UiConfig.SELECT_RECT_INNER_CORNER_WIDTH_HALVED, selectRectB+UiConfig.SELECT_RECT_INNER_CORNER_WIDTH_HALVED, selectRectL+UiConfig.SELECT_RECT_INNER_CORNER_WIDTH_HALVED, selectRectB-UiConfig.SELECT_RECT_INNER_CORNER_WIDTH_HALVED, paint);
        destCanvas.drawRect(selectRectR-UiConfig.SELECT_RECT_INNER_CORNER_WIDTH_HALVED, selectRectB+UiConfig.SELECT_RECT_INNER_CORNER_WIDTH_HALVED, selectRectR+UiConfig.SELECT_RECT_INNER_CORNER_WIDTH_HALVED, selectRectB-UiConfig.SELECT_RECT_INNER_CORNER_WIDTH_HALVED, paint);
        destCanvas.drawRect(selectRectL-UiConfig.SELECT_RECT_INNER_CORNER_WIDTH_HALVED, selectRectT+UiConfig.SELECT_RECT_INNER_CORNER_WIDTH_HALVED, selectRectL+UiConfig.SELECT_RECT_INNER_CORNER_WIDTH_HALVED, selectRectT-UiConfig.SELECT_RECT_INNER_CORNER_WIDTH_HALVED, paint);
        destCanvas.drawRect(selectRectR-UiConfig.SELECT_RECT_INNER_CORNER_WIDTH_HALVED, selectRectT+UiConfig.SELECT_RECT_INNER_CORNER_WIDTH_HALVED, selectRectR+UiConfig.SELECT_RECT_INNER_CORNER_WIDTH_HALVED, selectRectT-UiConfig.SELECT_RECT_INNER_CORNER_WIDTH_HALVED, paint);

    }

}
