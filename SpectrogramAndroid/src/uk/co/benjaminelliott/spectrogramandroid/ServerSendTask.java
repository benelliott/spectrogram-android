package uk.co.benjaminelliott.spectrogramandroid;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OptionalDataException;
import java.io.OutputStream;
import java.net.Socket;
import java.net.UnknownHostException;

import android.os.AsyncTask;
import android.util.Log;

public class ServerSendTask extends AsyncTask<String, Void, Void> {
	
	private final String HOST = "172.21.124.64";
	private final int PORT = 5353;
	private CapturedBitmapAudio cba;
	
	protected void sendCBAToServer() {
		try {
			Socket socket = new Socket(HOST, PORT);
			Log.d("","SOCKET OPENED");
			OutputStream os = socket.getOutputStream();
			ObjectOutputStream oos = new ObjectOutputStream(os);
			Log.d("","WRITING OBJECT TO STREAM...");
			oos.writeObject(cba);
			Log.d("","OBJECT WRITTEN TO OUTPUT STREAM");
			oos.close();
			os.close();
			socket.close();
			Log.d("","SOCKET CLOSED");
		} catch (UnknownHostException e) {
			Log.e("","UNKNOWN HOST: "+HOST+" PORT: "+PORT);
			e.printStackTrace();
		} catch (IOException e) {
			Log.e("","IOEXCEPTION");
			e.printStackTrace();
		}
	}

	@Override
	protected Void doInBackground(String... params) {
		/*
		 * params[0] is full directory path, params[1] is filename
		 */
		try {
			Log.d("","STARTING BG TASK");
			FileInputStream fis = new FileInputStream(params[0]+"/"+params[1]+".cba");
			ObjectInputStream ois = new ObjectInputStream(fis);
			cba = (CapturedBitmapAudio) ois.readObject();
			Log.d("","ABOUT TO SEND TO SERVER");
			sendCBAToServer();
			ois.close();
			fis.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (OptionalDataException e) {
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	
    protected void onPostExecute() {
    	Log.d("","POST EXECUTE");
    }

}
