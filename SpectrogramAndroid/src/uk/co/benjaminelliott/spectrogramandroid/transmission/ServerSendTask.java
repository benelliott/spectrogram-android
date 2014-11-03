package uk.co.benjaminelliott.spectrogramandroid.transmission;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OptionalDataException;
import java.io.OutputStream;
import java.net.Socket;
import java.net.UnknownHostException;

import uk.co.benjaminelliott.spectrogramandroid.storage.CapturedBitmapAudio;

import android.os.AsyncTask;
import android.util.Log;

/**
 * An AsyncTask that sends the user's capture to a server.
 * TODO: some indication of progress and failure.
 * @author Ben
 *
 */
public class ServerSendTask extends AsyncTask<String, Void, Void> {
	
	private static final String TAG = "ServerSendTask";
	private final String HOST = "172.17.156.36"; //TODO hard-coded server IP!
	private final int PORT = 5353;
	private CapturedBitmapAudio cba;
	
	/**
	 * Send the user's CBA file to the server.
	 */
	protected void sendCBAToServer() {
		try {
			Socket socket = new Socket(HOST, PORT);
			OutputStream os = socket.getOutputStream();
			ObjectOutputStream oos = new ObjectOutputStream(os);
			oos.writeObject(cba);
			oos.close();
			os.close();
			socket.close();
		} catch (UnknownHostException e) {
			Log.e(TAG,"Unknown host: "+HOST+", port: "+PORT);
		} catch (IOException e) {
			Log.e(TAG,"Error when trying to send CBA file to server", e);
		}
	}

	@Override
	protected Void doInBackground(String... params) {
		// params[0] is full directory path, params[1] is filename
		try {
			FileInputStream fis = new FileInputStream(params[0]+"/"+params[1]+".cba");
			ObjectInputStream ois = new ObjectInputStream(fis);
			cba = (CapturedBitmapAudio) ois.readObject();
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
}
