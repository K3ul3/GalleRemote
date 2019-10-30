package com.rene_arnold.galleremote.services;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.preference.PreferenceManager;
import android.util.Log;

import com.rene_arnold.galleremote.R;
import com.rene_arnold.galleremote.util.Utilities;

import org.json.JSONArray;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

class HttpRestService {

	private static final String LOG_TAG = HttpRestService.class.getSimpleName();

	private static int READ_TIMEOUT = 10000;
	private static int CONNECT_TIMEOUT = 15000;

	private Context context;

	HttpRestService(Context context) {
		this.context = context;
	}

	/**
	 * Creates a HttpURLConnection to the given action.
	 * 
	 * @param action
	 * @return
	 * @throws IOException
	 */
	private HttpURLConnection createConnection(String action) throws IOException {
		// Create connection and set parameters.
		String deviceIdentifier = getDeviceIdentifier();
		URL url = new URL(getBaseUrl() + action + "?device=" + deviceIdentifier);

		Log.d(LOG_TAG, "createConnection: " + url);
		HttpURLConnection conn = (HttpURLConnection) url.openConnection();
		conn.setReadTimeout(READ_TIMEOUT);
		conn.setConnectTimeout(CONNECT_TIMEOUT);
		conn.setUseCaches(false);
		return conn;
	}

	private String getBaseUrl() {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		String string = prefs.getString("server_url", context.getString(R.string.server_url));
		return string;
	}

	private String getDeviceIdentifier() {
		WifiManager manager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
		WifiInfo info = manager.getConnectionInfo();
		if (info == null || info.getMacAddress() == null) {
			return "noMAC";
		} else
			return info.getMacAddress().replace(":", "");
	}

	/**
	 * make http connection Type=Get
	 * 
	 * @param action
	 * @return http result as String
	 * @throws IOException
	 */
	private String makeGetConnection(String action) throws IOException {
		String result = null;
		int errStatus = 0;
		String errMessage = null;

		HttpURLConnection conn = createConnection(action);
		try {
			conn.setRequestMethod("GET");
			conn.connect();
			int rc = conn.getResponseCode();
			result = Utilities.inputStream2string(conn.getInputStream());
			String logResult = result.length() > 110 ? result.substring(0, 100) : result;
			Log.d(LOG_TAG, "makeGetConnection result: " + rc + " : " + logResult);
		} catch (IOException e) {
			errStatus = conn.getResponseCode();
			errMessage = conn.getResponseMessage();
			Log.w(LOG_TAG, "makeGetConnection error: http-code=" + errStatus + ", exception: " + e);
		} finally {
			conn.disconnect();
		}
		if (errStatus > 0) {
			throw new IOException("HTTP-Error " + errStatus + " " + errMessage);
		}
		return result;
	}

	public List<URL> getImages() {
		try {
			List<URL> urls = new ArrayList<>();
			String action = "getImages.php";
			String result = makeGetConnection(action);
			JSONArray array = new JSONArray(result);
			for (int i = 0; i < array.length(); i++) {
				String value = array.getString(i);
				URL url = new URL(value);
				urls.add(url);
			}
			return urls;
		} catch (Exception e) {
			return null;
		}
	}

	public Long getDelay() {
		try {
			String action = "getDelay.php";
			String result = makeGetConnection(action);
			return Long.valueOf(result);
		} catch (Exception e) {
			return null;
		}
	}

}
