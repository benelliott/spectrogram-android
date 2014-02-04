package uk.co.benjaminelliott.spectrogramandroid;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

public class SpectroFragment extends Fragment {
	
	private View rootView;
	private ImageButton resumeButton;
	private Button selectionConfirmButton;
	private Button selectionCancelButton;
	private LiveSpectrogramSurfaceView lssv;
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
		lssv.pauseScrolling();
	}
	
	@Override
	public void onResume() {
		super.onResume();
		lssv.resumeFromPause();
	}
    
    private void init() {
		lssv = (LiveSpectrogramSurfaceView)rootView.findViewById(R.id.lssv);
		((SpectroActivity)getActivity()).setLiveSpectrogramSurfaceView(lssv); //pass the LSSV back to the activity for location updates
		
		resumeButton = (ImageButton)rootView.findViewById(R.id.button_resume);
		resumeButton.setOnClickListener(new OnClickListener() {
			@Override 
			public void onClick(View arg0) {
				lssv.resumeScrolling();
			}
		});
		resumeButton.setVisibility(View.GONE);
		
		selectionConfirmButton = (Button)rootView.findViewById(R.id.selection_confirm);
		selectionConfirmButton.setOnClickListener(new OnClickListener() {
			@Override 
			public void onClick(View arg0) {
				lssv.confirmSelection();
			}
		});
		selectionConfirmButton.setEnabled(false);
		
		selectionCancelButton = (Button)rootView.findViewById(R.id.selection_cancel);
		selectionCancelButton.setOnClickListener(new OnClickListener() {
			@Override 
			public void onClick(View arg0) {
				lssv.cancelSelection();
			}
		});
		selectionCancelButton.setEnabled(false);
		
		
		leftTimeTextView = (TextView)rootView.findViewById(R.id.time_text_left);
		rightTimeTextView = (TextView)rootView.findViewById(R.id.time_text_right);
		bottomFreqTextView = (TextView)rootView.findViewById(R.id.freq_text_bottom);
		topFreqTextView = (TextView)rootView.findViewById(R.id.freq_text_top);
		selectRectTextView = (TextView)rootView.findViewById(R.id.text_select_rect);
		selectRectTextView.setVisibility(View.GONE);
		
		//TODO
		captureButtonContainer = (LinearLayout)rootView.findViewById(R.id.capture_button_container);
		lssv.setCaptureButtonContainer(captureButtonContainer);
		captureButtonContainer.setVisibility(View.GONE);



		bottomFreqTextView.setText("0 kHz");
		rightTimeTextView.setText("0 sec");
		
		lssv.setLeftTimeTextView(leftTimeTextView);
		lssv.setRightTimeTextView(rightTimeTextView);
		lssv.setTopFreqTextView(topFreqTextView);
		lssv.setResumeButton(resumeButton);
		lssv.setSelectRectTextView(selectRectTextView);
		lssv.setSelectionConfirmButton(selectionConfirmButton);
		lssv.setSelectionCancelButton(selectionCancelButton);
    }
}
