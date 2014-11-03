package uk.co.benjaminelliott.spectrogramandroid.ui;

import java.math.BigDecimal;

import uk.co.benjaminelliott.spectrogramandroid.R;
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

import com.google.android.gms.location.LocationClient;

/**
 * Fragment that provides the user with the spectrogram interface for recording audio.
 * @author Ben
 *
 */
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
		// pause the spectrogram
		ssv.pauseScrolling();
	}

	@Override
	public void onResume() {
		super.onResume();
	}

	public void pauseScrolling() {
		if (ssv != null) {
			ssv.pauseScrolling();
		}
	}

	private void init() {
		// SpectrogramSurfaceView:
		ssv = (SpectrogramSurfaceView)rootView.findViewById(R.id.ssv);
		ssv.setSpectroFragment(this);
		
		// Resume button:
		resumeButton = (ImageButton)rootView.findViewById(R.id.button_resume);
		
		resumeButton.setOnClickListener(new OnClickListener() {
			@Override 
			public void onClick(View arg0) {
				ssv.resumeScrolling();
			}
		});
		
		resumeButton.setVisibility(View.GONE); // only show when paused

		// Confirm button:
		selectionConfirmButton = (Button)rootView.findViewById(R.id.selection_confirm);
		
		selectionConfirmButton.setOnClickListener(new OnClickListener() {
			@Override 
			public void onClick(View arg0) {
				ssv.confirmSelection();
			}
		});
		
		selectionConfirmButton.setEnabled(false); // only enable when a selection has been made

		// Cancel button:
		selectionCancelButton = (Button)rootView.findViewById(R.id.selection_cancel);
		
		selectionCancelButton.setOnClickListener(new OnClickListener() {
			@Override 
			public void onClick(View arg0) {
				ssv.cancelSelection();
			}
		});
		
		selectionCancelButton.setEnabled(false); // only enable when a selection has been made

		// get references to all text views so they can be updated:
		leftTimeTextView = (TextView)rootView.findViewById(R.id.time_text_left);
		rightTimeTextView = (TextView)rootView.findViewById(R.id.time_text_right);
		bottomFreqTextView = (TextView)rootView.findViewById(R.id.freq_text_bottom);
		topFreqTextView = (TextView)rootView.findViewById(R.id.freq_text_top);

		// Capture button container (invisible until a capture is made):
		captureButtonContainer = (LinearLayout)rootView.findViewById(R.id.capture_button_container);
		captureButtonContainer.setVisibility(View.INVISIBLE);

		// Set the contents of the two invariant text views:
		bottomFreqTextView.setText("0 kHz");
		rightTimeTextView.setText("0 sec");
	}

	/**
	 * Displays and enables the buttons held in the capture button container.
	 */
	public void enableCaptureButtonContainer() {
		captureButtonContainer.setVisibility(View.VISIBLE);
		selectionConfirmButton.setEnabled(true);
		selectionCancelButton.setEnabled(true);
	}

	/**
	 * Hides and disables the buttons held in the capture button container.
	 */
	public void disableCaptureButtonContainer() {
		captureButtonContainer.setVisibility(View.GONE);
		selectionConfirmButton.setEnabled(false);
		selectionCancelButton.setEnabled(false);
	}

	/**
	 * Calculates the new position for the capture button container given the new dimensions
	 * of the selection rectangle.
	 * 
	 * @param selectRectL
	 * @param selectRectT
	 * @param selectRectR
	 * @param selectRectB
	 */
	protected void moveCaptureButtonContainer(float selectRectL, float selectRectT, float selectRectR, float selectRectB) {
		RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams)captureButtonContainer.getLayoutParams();
		
		int lowestDimension = (selectRectT < selectRectB) ? (int)selectRectB : (int)selectRectT;
		int highestDimension = (selectRectT < selectRectB) ? (int)selectRectT : (int)selectRectB;
		int leftmostDimension = (selectRectL < selectRectR) ? (int)selectRectL : (int)selectRectR;
		
		int halfDifference = (int)Math.abs((selectRectL-selectRectR)/2); // half the width of the selection rectangle
		int leftMargin = leftmostDimension + halfDifference - captureButtonContainer.getWidth()/2; // absolute x-coord for container centre
		
		int yDimension;
		if (ssv.getHeight() - lowestDimension < UiConfig.CAPTURE_BUTTON_CONTAINER_MIN_HEIGHT + 10)
			// if y-dimension is getting too low then flip and draw the container on the other side of the selection rectangle
			yDimension = highestDimension - UiConfig.CAPTURE_BUTTON_CONTAINER_MIN_HEIGHT - 10;
		else
			yDimension = lowestDimension + 10;
		
		if (leftmostDimension + UiConfig.CAPTURE_BUTTON_CONTAINER_MIN_WIDTH > ssv.getWidth())
			// if right side is getting too close to the border then shift leftwards until it fits
			leftmostDimension = ssv.getWidth()-UiConfig.CAPTURE_BUTTON_CONTAINER_MIN_WIDTH;
		
		// set margins so that the container is positioned appropriately:
		params.setMargins(leftMargin, (int)(yDimension + UiConfig.SELECT_RECT_CORNER_RADIUS/2) /* make it half-way clear of the corner */, 0, 0);
		captureButtonContainer.setLayoutParams(params);
	}

	public void enableResumeButton() {
		resumeButton.setVisibility(View.VISIBLE);
	}

	public void disableResumeButton() {
		resumeButton.setVisibility(View.GONE);
	}

	/**
	 * Displays the provided time as a decimal in the left time text view.
	 * @param timeInSec
	 */
	public void setLeftTimeText(float timeInSec) {
		BigDecimal bd = new BigDecimal(Float.toString(timeInSec));
		bd = bd.setScale(2, BigDecimal.ROUND_HALF_UP); //round to 2 dp
		leftTimeTextView.setText(bd.floatValue()+" sec");
	}
	
	/**
	 * Displays the provided time as a decimal in the right time text view.
	 * @param timeInSec
	 */
	public void setRightTimeText(float timeInSec) {
		BigDecimal bd = new BigDecimal(Float.toString(timeInSec));
		bd = bd.setScale(2, BigDecimal.ROUND_HALF_UP); //round to 2 dp
		rightTimeTextView.setText(bd.floatValue()+" sec");
	}

	/**
	 * Displays the provided frequency as a decimal in the top frequency text view.
	 * @param timeInSec
	 */
	public void setTopFreqText(float freqInKHz) {
		BigDecimal bd = new BigDecimal(Float.toString(freqInKHz));
		bd = bd.setScale(2, BigDecimal.ROUND_HALF_UP); //round to 2 dp
		topFreqTextView.setText(bd.floatValue()+" kHz");
	}

	public void setLocationClient(LocationClient lc) {
		ssv.setLocationClient(lc); 
	}

	//Bottom freq is always 0 kHz

}
