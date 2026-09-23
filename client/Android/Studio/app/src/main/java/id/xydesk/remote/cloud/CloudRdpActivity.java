package id.xydesk.remote.cloud;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.freerdp.freerdpcore.presentation.SessionActivity;

import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * CLOUD RDP (v1) — "create RDP from GitHub, langsung dari APK".
 *
 * Flow:
 *   1. Login GitHub (device flow — user authorize di browser).
 *   2. User masukkan NAMA -> nama repo GitHub (private) dibuat otomatis.
 *   3. App push template setup (workflow + skrip Windows) ke repo itu.
 *   4. Workflow di repo menjalankan setup di self-hosted Windows runner
 *      (label 'xydesk-win'): RDP on, join Tailscale, user + password acak.
 *   5. App poll status run; saat sukses, app download artifact
 *      'rdp-credentials' (host ts.net + user + password).
 *   6. App cek konektivitas (host:3389) lalu OTOMATIS connect RDP.
 *
 * Prasyarat (lihat teks hint di UI):
 *   - OAuth App GitHub dengan Device Flow (isi CLIENT_ID di GitHubDeviceAuth)
 *   - Minimal 1 Windows VM self-hosted runner dengan label 'xydesk-win',
 *     env XYDESK_TAILSCALE_AUTH_KEY ter-set, Tailscale terpasang
 *   - Tailscale app di HP, login ke tailnet yang sama
 */
public class CloudRdpActivity extends AppCompatActivity
{
	private static final String TAG = "XyDeskCloud";
	private static final String WORKFLOW_NAME = "XyDesk RDP Setup";
	private static final String ARTIFACT_NAME = "rdp-credentials";

	private Button btnLogin;
	private Button btnCreate;
	private EditText nameInput;
	private TextView loginStatus;
	private TextView statusView;
	private ProgressBar progress;

	private final Handler main = new Handler(Looper.getMainLooper());
	private GitHubClient gh;

	@Override protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_cloud);

		btnLogin = findViewById(R.id.btn_cloud_login);
		btnCreate = findViewById(R.id.btn_cloud_create);
		nameInput = findViewById(R.id.input_repo_name);
		loginStatus = findViewById(R.id.txt_login_status);
		statusView = findViewById(R.id.txt_cloud_status);
		progress = findViewById(R.id.cloud_progress);

		btnLogin.setOnClickListener(v -> doLogin());
		btnCreate.setOnClickListener(v -> doCreate());
	}

	// ------------------------------------------------------------------
	// 1) LOGIN
	// ------------------------------------------------------------------

	private void doLogin()
	{
		if (!GitHubDeviceAuth.isConfigured())
		{
			AlertDialog.Builder b = new AlertDialog.Builder(this);
			b.setTitle("OAuth App belum di-set");
			b.setMessage("Fitur Cloud RDP butuh 1 OAuth App GitHub (sekali saja):\n\n"
			           + "1. GitHub → Settings → Developer settings → OAuth Apps → New OAuth App\n"
			           + "2. Nama: XyDesk Remote, callback: https://localhost\n"
			           + "3. AKTIFKAN 'Device Flow'\n"
			           + "4. Salin Client ID ke GitHubDeviceAuth.CLIENT_ID\n\n"
			           + "Lalu build ulang. (Client secret TIDAK perlu.)");
			b.setPositiveButton("Oke", null);
			b.show();
			return;
		}
		setBusy(true, "Meminta device code...");
		new Thread(() -> {
			try
			{
				GitHubDeviceAuth.DeviceCode dc = GitHubDeviceAuth.requestCode();
				main.post(() -> {
					status("Kode login: " + dc.userCode);
					try
					{
						startActivity(new Intent(Intent.ACTION_VIEW,
						                          Uri.parse(dc.verificationUri)));
					}
					catch (Exception ignored) { }
				});
				String token = GitHubDeviceAuth.pollForToken(dc);
				gh = new GitHubClient(token);
				final String who = gh.login();
				main.post(() -> {
					loginStatus.setText("Terhubung: @" + who);
					status("Login GitHub OK. Isi nama, lalu Create & Setup.");
					setBusy(false, null);
				});
			}
			catch (Exception e)
			{
				main.post(() -> {
					status("GAGAL login: " + e.getMessage());
					setBusy(false, null);
				});
			}
		}).start();
	}

	// ------------------------------------------------------------------
	// 2) CREATE + SETUP + CONNECT
	// ------------------------------------------------------------------

	private void doCreate()
	{
		if (gh == null)
		{
			status("Login GitHub dulu ya.");
			return;
		}
		final String name = nameInput.getText().toString().trim().toLowerCase()
		                                   .replace(' ', '-');
		if (name.isEmpty() || !name.matches("[a-z0-9][a-z0-9_-]{0,38}"))
		{
			status("Nama tidak valid (huruf kecil, angka, -, _; maks 39).");
			return;
		}
		setBusy(true, "Membuat repo...");
		new Thread(() -> runPipeline(name)).start();
	}

	private void runPipeline(String repoName)
	{
		try
		{
			status("1/5 Cek/buat repo " + repoName + "...");
			if (gh.repoExists(repoName))
			{
				status("Repo sudah ada — pakai yang existing.");
			}
			else
			{
				gh.createRepo(repoName, "XyDesk Remote — cloud RDP (setup otomatis)");
			}

			status("2/5 Push template setup ke repo...");
			gh.pushFile(repoName, ".github/workflows/rdp-vm.yml",
			            asset("xydesk-cloud/rdp-vm.yml"),
			            "xydesk: add RDP setup workflow");
			gh.pushFile(repoName, "setup/setup-windows.ps1",
			            asset("xydesk-cloud/setup-windows.ps1"),
			            "xydesk: add Windows setup script");

			status("3/5 Tunggu workflow '" + WORKFLOW_NAME + "' mulai & jalan...");
			String runId = waitForRun(repoName);
			status("Run #" + runId + " — menunggu selesai (bisa beberapa menit)...");

			JSONObject run = waitForCompletion(repoName, runId);
			String conclusion = run.optString("conclusion", "");
			if (!"success".equals(conclusion))
			{
				throw new Exception("Workflow gagal (" + conclusion
				                    + "). Cek Actions tab di repo " + repoName
				                    + " — kemungkinan label runner 'xydesk-win' belum ada.");
			}

			status("4/5 Download kredensial RDP...");
			JSONObject art = gh.findArtifact(repoName, runId, ARTIFACT_NAME);
			if (art == null)
			{
				throw new Exception("Artifact " + ARTIFACT_NAME + " tidak ditemukan di run #"
				                    + runId);
			}
			byte[] zip = gh.downloadArtifactZip(repoName, runId, art.getLong("id"));
			byte[] creds = unzipEntry(zip, "rdp-credentials.json");
			if (creds == null)
			{
				throw new Exception("rdp-credentials.json tidak ada di zip artifact");
			}
			JSONObject c = new JSONObject(new String(creds, StandardCharsets.UTF_8));
			String host = c.getString("host");
			int port = c.optInt("port", 3389);
			String user = c.getString("user");
			String pass = c.getString("password");

			status("5/5 Cek konektivitas " + host + ":" + port + " (butuh Tailscale aktif di HP)...");
			if (!reachable(host, port))
			{
				throw new Exception(host + ":" + port + " belum terjangkau. "
				                   + "Pastikan Tailscale di HP login ke tailnet yang "
				                   + "sama dengan VM, lalu coba lagi.");
			}

			main.post(() -> {
				status("SIAP — connect otomatis ke " + host);
				Intent i = new Intent(this, SessionActivity.class);
				i.setData(buildRdpUri(host, port, user, pass));
				startActivity(i);
				setBusy(false, null);
			});
		}
		catch (final Exception e)
		{
			Log.w(TAG, "pipeline gagal", e);
			main.post(() -> {
				status("GAGAL: " + e.getMessage());
				setBusy(false, null);
			});
		}
	}

	private String waitForRun(String repoName) throws Exception
	{
		long deadline = System.currentTimeMillis() + 3 * 60 * 1000L;
		while (System.currentTimeMillis() < deadline)
		{
			JSONObject run = gh.latestRun(repoName, WORKFLOW_NAME);
			if (run != null)
			{
				return String.valueOf(run.getLong("id"));
			}
			Thread.sleep(5000);
		}
		throw new Exception("Workflow belum mulai dalam 3 menit (cek nama workflow & branch main)");
	}

	private JSONObject waitForCompletion(String repoName, String runId) throws Exception
	{
		long deadline = System.currentTimeMillis() + 25 * 60 * 1000L;
		while (System.currentTimeMillis() < deadline)
		{
			JSONObject run = gh.getRun(repoName, runId);
			String status = run.optString("status", "");
			if ("completed".equals(status))
			{
				return run;
			}
			main.post(() -> {
				if (progress.isindeterminate())
				{
					progress.setIndeterminate(true);
				}
			});
			Thread.sleep(10000);
		}
		throw new Exception("Timeout menunggu workflow selesai (25 menit)");
	}

	// ------------------------------------------------------------------
	// helpers
	// ------------------------------------------------------------------

	private Uri buildRdpUri(String host, int port, String user, String pass)
	{
		Uri.Builder b = new Uri.Builder().scheme("rdp");
		String authority = host;
		if (port != 3389)
		{
			authority = host + ":" + port;
		}
		if (user != null && !user.isEmpty())
		{
			authority = user + "@" + authority;
		}
		b.encodedAuthority(authority);
		if (pass != null && !pass.isEmpty())
		{
			b.appendQueryParameter("p", pass);
		}
		return b.build();
	}

	private boolean reachable(String host, int port)
	{
		try (Socket s = new Socket())
		{
			s.connect(new InetSocketAddress(host, port), 4000);
			return true;
		}
		catch (Exception e)
		{
			return false;
		}
	}

	private String asset(String path) throws Exception
	{
		try (InputStream is = getAssets().open(path))
		{
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			byte[] buf = new byte[8192];
			int n;
			while ((n = is.read(buf)) > 0)
			{
				bos.write(buf, 0, n);
			}
			return new String(bos.toByteArray(), StandardCharsets.UTF_8);
		}
	}

	private byte[] unzipEntry(byte[] zip, String entrySuffix) throws Exception
	{
		try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zip)))
		{
			ZipEntry e;
			while ((e = zis.getNextEntry()) != null)
			{
				if (e.getName().endsWith(entrySuffix))
				{
					ByteArrayOutputStream bos = new ByteArrayOutputStream();
					byte[] buf = new byte[8192];
					int n;
					while ((n = zis.read(buf)) > 0)
					{
						bos.write(buf, 0, n);
					}
					return bos.toByteArray();
				}
			}
		}
		return null;
	}

	private void status(String s)
	{
		statusView.setText(s);
	}

	private void setBusy(boolean busy, String label)
	{
		progress.setVisibility(busy ? View.VISIBLE : View.GONE);
		btnLogin.setEnabled(!busy);
		btnCreate.setEnabled(!busy);
		if (label != null)
		{
			status(label);
		}
	}
}
