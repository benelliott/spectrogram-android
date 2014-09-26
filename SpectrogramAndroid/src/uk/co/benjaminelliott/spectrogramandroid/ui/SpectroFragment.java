package uk.co.benjaminelliott.spectrogramandroid.ui;

import java.math.BigDecimal;

import uk.co.benjaminelliott.spectrogramandroid.R;
import uk.co.benjaminelliott.spectrogramandroid.activities.SpectroActivity;
import uk.co.benjaminelliott.spectrogramandroid.preferences.UiConfig;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

public class SpectroFragment extends Fragment {

    private View rootView;
    private ImageButton resumeButton;
    private Button selectionConfirmButton;
    private Button selectionCancelButton;
    private SpectrogramSurfaceView ssv;
    private TextView leftTimeTextView;
    private TextView rightTimeTextView;
    private TextView bottomFreqTextView;
    private TextView topFreqTextView;
    private TextView selectRectTextView;

    private LinearLayout captureButtonContainer;

    public SpectroFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
	    Bundle savedInstanceState) {
	rootView = inflater.inflate(
		R.layout.fragment_record,
		container, false);
	init();
	return rootView;
    }

    @Override
    public void onPause() {
	super.onPause();
	ssv.pauseScrolling();
    }

    @Override
    public void onResume() {
	super.onResume();
    }

    private void init() {
	ssv = (SpectrogramSurfaceView)rootView.findViewById(R.id.ssv);
	((SpectroActivity)getActivity()).setSpectrogramSurfaceView(ssv); //pass the LSSV back to the activity for location updates

	ssv.setSpectroFragment(this);

	resumeButton = (ImageButton)rootView.findViewById(R.id.button_resume);
	resumeButton.setOnClickListener(new OnClickListener() {
	    @Override 
	    public void onClick(View arg0) {
		ssv.resumeScrolling();
	    }
	});
	resumeButton.setVisibility(View.GONE);

	selectionConfirmButton = (Button)rootView.findViewById(R.id.selection_confirm);
	selectionConfirmButton.setOnClickListener(new OnClickListener() {
	    @Override 
	    public void onClick(View arg0) {
		ssv.confirmSelection();
	    }
	});
	selectionConfirmButton.setEnabled(false);

	selectionCancelButton = (Button)rootView.findViewById(R.id.selection_cancel);
	selectionCancelButton.setOnClickListener(new OnClickListener() {
	    @Override 
	    public void onClick(View arg0) {
		ssv.cancelSelection();
	    }
	});
	selectionCancelButton.setEnabled(false);


	leftTimeTextView = (TextView)rootView.findViewById(R.id.time_text_left);
	rightTimeTextView = (TextView)rootView.findViewById(R.id.time_text_right);
	bottomFreqTextView = (TextView)rootView.findViewById(R.id.freq_text_bottom);
	topFreqTextView = (TextView)rootView.findViewById(R.id.freq_text_top);
	selectRectTextView = (TextView)rootView.findViewById(R.id.text_select_rect);
	selectRectTextView.setVisibility(View.GONE);

	captureButtonContainer = (LinearLayout)rootView.findViewById(R.id.capture_button_container);
	captureButtonContainer.setVisibility(View.INVISIBLE);

	bottomFreqTextView.setText("0 kHz");
	rightTimeTextView.setText("0 sec");
    }

    public void enableCaptureButtonContainer() {
	captureButtonContainer.setVisibility(View.VISIBLE);
	selectionConfirmButton.setEnabled(true);
	selectionCancelButton.setEnabled(true);
    }

    public void disableCaptureButtonContainer() {
	captureButtonContainer.setVisibility(View.GONE);
	selectionConfirmButton.setEnabled(false);
	selectionCancelButton.setEnabled(false);
    }

    protected void moveCaptureButtonContainer(float selectRectL, float selectRectT, float selectRectR, float selectRectB) {
	RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams)captureButtonContainer.getLayoutParams();
	int lowestDimension = (selectRectT < selectRectB) ? (int)selectRectB : (int)selectRectT;
	int highestDimension = (selectRectT < selectRectB) ? (int)selectRectT : (int)selectRectB;
	int leftmostDimension = (selectRectL < selectRectR) ? (int)selectRectL : (int)selectRectR;
	int halfDifference = (int)Math.abs((selectRectL-selectRectR)/2);
	int centred = leftmostDimension + halfDifference - captureButtonContainer.getWidth()/2;
	int yDimension = (ssv.getHeight() - lowestDimension < UiConfig.CAPTURE_BUTTON_CONTAINER_MIN_HEIGHT + 10)? highestDimension - UiConfig.CAPTURE_BUTTON_CONTAINER_MIN_HEIGHT - 10 : lowestDimension + 10;
	if (leftmostDimension + UiConfig.CAPTURE_BUTTON_CONTAINER_MIN_WIDTH > ssv.getWidth()) leftmostDimension = ssv.getWidth()-UiConfig.CAPTURE_BUTTON_CONTAINER_MIN_WIDTH;
	params.setMargins(centred, (int)(yDimension+UiConfig.SELECT_RECT_CORNER_RADIUS/2), 0, 0);
	captureButtonContainer.setLayoutParams(params);
    }

    public void enableResumeButton() {
	resumeButton.setVisibility(View.VISIBLE);
    }

    public void disableResumeButton() {
	resumeButton.setVisibility(View.GONE);
    }

    public void setLeftTimeText(float timeInSec) {
	BigDecimal bd = new BigDecimal(Float.toString(timeInSec));
	bd = bd.setScale(2, BigDecimal.ROUND_HALF_UP); //round to 2 dp
	leftTimeTextView.setText("-"+bd.floatValue()+" sec");
    }

    public void setRightTimeText(float timeInSec) {
	BigDecimal bd = new BigDecimal(Float.toString(timeInSec));
	bd = bd.setScale(2, BigDecimal.ROUND_HALF_UP); //round to 2 dp
	rightTimeTextView.setText("-"+bd.floatValue()+" sec");
    }

    public void setTopFreqText(float freqInKHz) {
	BigDecimal bd = new BigDecimal(Float.toString(freqInKHz));
	bd = bd.setScale(2, BigDecimal.ROUND_HALF_UP); //round to 2 dp
	topFreqTextView.setText(bd.floatValue()+" kHz");
    }

    //Bottom freq is always 0 kHz

}
