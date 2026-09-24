package id.xydesk.remote.cloud;

import org.json.JSONObject;
import org.json.JSONException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * GitHub login untuk XyDesk Remote via OAUTH DEVICE FLOW.
 *
 * Device flow dipilih karena aman untuk app mobile: TIDAK ada client
 * secret di device, user authorize lewat browser.
 *
 * PERSIYATAN SATU-KALI (sebelum fitur ini bisa dipakai):
 *   1. GitHub -> Settings -> Developer settings -> OAuth Apps -> New OAuth App
 *      - Application name: XyDesk Remote
 *      - Homepage URL: https://github.com/xykal/XyDesk-Remote
 *      - Authorization callback URL: https://localhost (tidak dipakai device flow)
 *   2. AKTIFKAN "Device Flow" di setting OAuth App tersebut (PENTING).
 *   3. Salin Client ID ke konstanta CLIENT_ID di bawah.
 *      (Client ID bersifat publik — aman di dalam APK. Client secret TIDAK
 *      dibutuhkan untuk device flow.)
 */
public class GitHubDeviceAuth
{
	/** ISI client_id OAuth App di sini. */
	public static final String CLIENT_ID = "GANTI_DENGAN_CLIENT_ID";

	public static boolean isConfigured()
	{
		return CLIENT_ID != null && !CLIENT_ID.startsWith("GANTI_");
	}

	public static class DeviceCode
	{
		public String deviceCode;
		public String userCode;
		public String verificationUri;
		public int interval = 5;
		public int expiresIn = 900;
	}

	/** Langkah 1: minta device code. (blocking — jalankan di thread latar) */
	public static DeviceCode requestCode() throws IOException, org.json.JSONException
	{
		String resp = postForm("https://github.com/login/device/code",
		                       "client_id=" + enc(CLIENT_ID) + "&scope=repo");
		JSONObject j = new JSONObject(resp);
		DeviceCode d = new DeviceCode();
		d.deviceCode = j.getString("device_code");
		d.userCode = j.getString("user_code");
		d.verificationUri = j.getString("verification_uri");
		if (j.has("interval")) d.interval = j.getInt("interval");
		if (j.has("expires_in")) d.expiresIn = j.getInt("expires_in");
		return d;
	}

	/** Langkah 2: poll hingga user authorize di browser. BLOCKING — thread latar. */
	public static String pollForToken(DeviceCode dc) throws IOException, org.json.JSONException
	{
		long deadline = System.currentTimeMillis() + dc.expiresIn * 1000L;
		int interval = Math.max(1, dc.interval);
		while (System.currentTimeMillis() < deadline)
		{
			sleep(interval * 1000L);
			String resp = postForm("https://github.com/login/oauth/access_token",
			                       "client_id=" + enc(CLIENT_ID)
			                       + "&device_code=" + enc(dc.deviceCode)
			                       + "&grant_type=urn:ietf:params:oauth:grant-type:device_code");
			JSONObject j = new JSONObject(resp);
			if (j.has("access_token"))
			{
				return j.getString("access_token");
			}
			String err = j.optString("error", "");
			if ("authorization_pending".equals(err))
			{
				continue;
			}
			if ("slow_down".equals(err))
			{
				interval += 5;
				continue;
			}
			throw new IOException("Error device flow: " + err
			                     + " " + j.optString("error_description", ""));
		}
		throw new IOException("Timeout — silakan login lagi");
	}

	private static String enc(String s) throws IOException, org.json.JSONException
	{
		return java.net.URLEncoder.encode(s, "UTF-8");
	}

	private static void sleep(long ms)
	{
		try { Thread.sleep(ms); }
		catch (InterruptedException e) { Thread.currentThread().interrupt(); }
	}

	private static String postForm(String urlStr, String formBody) throws IOException, org.json.JSONException
	{
		HttpURLConnection c = (HttpURLConnection) new URL(urlStr).openConnection();
		c.setRequestMethod("POST");
		c.setDoOutput(true);
		c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
		c.setRequestProperty("Accept", "application/json");
		c.setRequestProperty("User-Agent", "XyDeskRemote-Android");
		try (OutputStream os = c.getOutputStream())
		{
			os.write(formBody.getBytes(StandardCharsets.UTF_8));
		}
		return read(c);
	}

	private static String read(HttpURLConnection c) throws IOException, org.json.JSONException
	{
		int code = c.getResponseCode();
		InputStream is = (code >= 400) ? c.getErrorStream() : c.getInputStream();
		if (is == null) return "";
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		byte[] buf = new byte[4096];
		int n;
		while ((n = is.read(buf)) > 0)
		{
			bos.write(buf, 0, n);
		}
		return new String(bos.toByteArray(), StandardCharsets.UTF_8);
	}
}
