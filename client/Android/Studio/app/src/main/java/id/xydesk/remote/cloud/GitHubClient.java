package id.xydesk.remote.cloud;

import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Klien GitHub REST API tipis (tanpa dependensi eksternal) untuk fitur
 * Cloud RDP: buat repo, push template setup, pantau run workflow,
 * download artifact kredensial.
 *
 * Semua request pakai token user (device flow) — scope: repo.
 * Blocking: panggil dari thread latar.
 */
public class GitHubClient
{
	public static class ApiError extends Exception
	{
		public final int code;
		public ApiError(int code, String msg)
		{
			super(msg);
			this.code = code;
		}
	}

	private final String token;
	private String login;

	public GitHubClient(String token)
	{
		this.token = token;
	}

	public String login() throws ApiError, IOException, org.json.JSONException
	{
		if (login == null)
		{
			login = apiGet("/user").getString("login");
		}
		return login;
	}

	public void createRepo(String name, String description) throws ApiError, IOException, org.json.JSONException
	{
		JSONObject body = new JSONObject();
		body.put("name", name);
		body.put("description", description);
		body.put("private", true);
		body.put("auto_init", false);
		apiPost("/user/repos", body);
	}

	public boolean repoExists(String name) throws ApiError, IOException, org.json.JSONException
	{
		HttpURLConnection c = open("GET", "/repos/" + login() + "/" + name, null);
		int code = c.getResponseCode();
		c.disconnect();
		return code == 200;
	}

	/** Push file ke repo (contents API). */
	public void pushFile(String repo, String path, String content, String message)
			throws ApiError, IOException, org.json.JSONException
	{
		JSONObject body = new JSONObject();
		body.put("message", message);
		body.put("content",
		         Base64.encodeToString(content.getBytes(StandardCharsets.UTF_8),
			                          Base64.NO_WRAP));
		apiPut("/repos/" + login() + "/" + repo + "/contents/" + path, body);
	}

	/** Run workflow paling baru (per nama workflow). */
	public JSONObject latestRun(String repo, String workflowName) throws ApiError, IOException, org.json.JSONException
	{
		JSONObject page = apiGet("/repos/" + login() + "/" + repo
		                        + "/actions/runs?per_page=10&branch=main");
		JSONArray runs = page.optJSONArray("workflow_runs");
		if (runs == null) return null;
		for (int i = 0; i < runs.length(); i++)
		{
			JSONObject r = runs.getJSONObject(i);
			JSONObject wf = r.optJSONObject("workflow");
			if (wf != null && workflowName.equals(wf.optString("name")))
			{
				return r;
			}
		}
		return null;
	}

	public JSONObject getRun(String repo, String runId) throws ApiError, IOException, org.json.JSONException
	{
		return apiGet("/repos/" + login() + "/" + repo + "/actions/runs/" + runId);
	}

	public JSONObject findArtifact(String repo, String runId, String artifactName)
			throws ApiError, IOException, org.json.JSONException
	{
		JSONObject page = apiGet("/repos/" + login() + "/" + repo
		                        + "/actions/runs/" + runId + "/artifacts");
		JSONArray arts = page.optJSONArray("artifacts");
		if (arts == null) return null;
		for (int i = 0; i < arts.length(); i++)
		{
			JSONObject a = arts.getJSONObject(i);
			if (artifactName.equals(a.optString("name")))
			{
				return a;
			}
		}
		return null;
	}

	public byte[] downloadArtifactZip(String repo, String runId, long artifactId)
			throws ApiError, IOException, org.json.JSONException
	{
		return rawGet("/repos/" + login() + "/" + repo + "/actions/runs/" + runId
		              + "/artifacts/" + artifactId + "/zip",
		              "application/octet-stream");
	}

	// ------------------------------------------------------------------
	// HTTP low-level
	// ------------------------------------------------------------------

	private JSONObject apiGet(String path) throws ApiError, IOException, org.json.JSONException
	{
		return json(rawGet(path, "application/json"));
	}

	private JSONObject apiPost(String path, JSONObject body) throws ApiError, IOException, org.json.JSONException
	{
		return json(rawSend("POST", path, body));
	}

	private JSONObject apiPut(String path, JSONObject body) throws ApiError, IOException, org.json.JSONException
	{
		return json(rawSend("PUT", path, body));
	}

	private byte[] rawGet(String path, String accept) throws IOException
	{
		return send("GET", path, accept, null);
	}

	private byte[] rawSend(String method, String path, JSONObject body)
			throws ApiError, IOException, org.json.JSONException
	{
		return send(method, path, "application/json", body);
	}

	private byte[] send(String method, String path, String accept, JSONObject body)
			throws IOException
	{
		HttpURLConnection c = open(method, path, body);
		c.setRequestProperty("Accept", accept);
		c.setRequestProperty("User-Agent", "XyDeskRemote-Android");
		return readAll(c);
	}

	private JSONObject json(byte[] data) throws ApiError, IOException, org.json.JSONException
	{
		String s = new String(data, StandardCharsets.UTF_8);
		try
		{
			return new JSONObject(s);
		}
		catch (Exception e)
		{
			throw new ApiError(-1, "Respons bukan JSON: " + s.substring(0, Math.min(200, s.length())));
		}
	}

	private HttpURLConnection open(String method, String path, JSONObject body) throws IOException
	{
		HttpURLConnection c = (HttpURLConnection)
			new URL("https://api.github.com" + path).openConnection();
		c.setRequestMethod(method);
		c.setRequestProperty("Authorization", "Bearer " + token);
		if (body != null)
		{
			c.setDoOutput(true);
			c.setRequestProperty("Content-Type", "application/json");
			try (OutputStream os = c.getOutputStream())
			{
				os.write(body.toString().getBytes(StandardCharsets.UTF_8));
			}
		}
		return c;
	}

	private byte[] readAll(HttpURLConnection c) throws IOException
	{
		int code = c.getResponseCode();
		InputStream is = (code >= 400) ? c.getErrorStream() : c.getInputStream();
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		if (is != null)
		{
			byte[] buf = new byte[8192];
			int n;
			while ((n = is.read(buf)) > 0)
			{
				bos.write(buf, 0, n);
			}
		}
		byte[] data = bos.toByteArray();
		if (code < 200 || code >= 300)
		{
			throw new ApiError(code, "HTTP " + code + ": "
			                   + new String(data, StandardCharsets.UTF_8)
			                     .substring(0, Math.min(300, data.length)));
		}
		return data;
	}
}
