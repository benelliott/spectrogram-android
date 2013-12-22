package bge23.spectrogramandroid;

import java.io.IOException;

import android.app.Activity;
import android.os.Bundle;

public class RecordActivity extends Activity {
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    try {
		setContentView(new SpectrogramSurfaceView(this));
	} catch (IOException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	}
  }
} 