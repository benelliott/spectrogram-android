package uk.co.benjaminelliott.spectrogramandroid.preferences;

import android.graphics.Color;

public class UiConfig {

    // radius in which to accept touches of the selection rectangle corner
    public static final int SELECT_RECT_CORNER_RADIUS = 45;
    //half the width of the outer (black) square of the selection rectangle corner
    public static final int SELECT_RECT_OUTER_CORNER_WIDTH_HALVED = 20;
    //half the width of the inner (white) square of the selection rectangle corner
    public static final int SELECT_RECT_INNER_CORNER_WIDTH_HALVED = 18;
    
    public static final int SELECT_RECT_OUTER_COLOUR = Color.BLACK;
    public static final int SELECT_RECT_INNER_COLOUR = Color.WHITE;
    
    public static final int SELECT_RECT_OUTER_STROKE = 10;
    public static final int SELECT_RECT_INNER_STROKE = 6;
    
    public static final int CAPTURE_BUTTON_CONTAINER_MIN_WIDTH = 120;
    public static final int CAPTURE_BUTTON_CONTAINER_MIN_HEIGHT = 120;
    
    //initial width and height for select-area rectangle
    public static final float SELECT_RECT_WIDTH = 200;
    public static final float SELECT_RECT_HEIGHT = 200;
    
    //Increase spread for a larger scroll shadow
    public static final int SCROLL_SHADOW_SPREAD = 100;
    
    //Number of horizontal pixels to use for each time window
    public static final int HORIZONTAL_STRETCH_FACTOR = 2;

}
