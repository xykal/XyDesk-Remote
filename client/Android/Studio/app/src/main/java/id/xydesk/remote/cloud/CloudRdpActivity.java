package id.xydesk.remote.cloud;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.freerdp.freerdpcore.presentation.SessionActivity;

import id.xydesk.remote.R;

import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.crypto.Cipher;

/**
 * CLOUD RDP (v1.4, multi-tenant, repo public-safe) — "create RDP from GitHub,
 * langsung dari APK".
 *
 * Alur:
 *   1. Login GitHub — user authorize akun GitHub MILIK MEREKA SENDIRI
 *      (device flow). Workflow jalan di akun user, bukan akun developer.
 *   2. User isi: nama repo, nama user RDP (opsional), password RDP (opsional,
 *      wajib kuat — kosong = auto-generate), Tailscale auth key (opsional).
 *   3. App auto-create repo PUBLIC di akun user (aturan proyek: tanpa repo
 *      private) — kalau sudah ada, pakai yang existing. App generate kunci
 *      RSA sekali-pakai, push template (workflow + skrip + pubkey.pem).
 *   4. Fase 'prepare': host generate kunci host (sekali per mesin) dan upload
 *      public key-nya. App enkripsi rahasia {user,pass,tskey} ke kunci host
 *      itu -> push setup/secrets.enc (ciphertext; aman walau repo public,
 *      rahasia TIDAK pernah lewat input workflow / log).
 *   5. Fase 'setup': host dekripsi secrets.enc, jalankan setup (RDP on,
 *      Tailscale, user RDP), lalu kredensial di-enskripsi balik ke pubkey.pem
 *      milik app (end-to-end) sbg artifact 'rdp-credentials'.
 *   6. App dekripsi kredensial, cek host:3389, OTOMATIS connect RDP.
 *
 * Untuk RDP yang SUDAH ADA (IP + port + user + pass), pakai form Connect di
 * layar utama — tidak butuh GitHub sama sekali.
 *
 * Host RDP = mesin MILIK USER (self-hosted runner). Jangan pernah pakai
 * GitHub-hosted runner (windows-latest) sebagai RDP host: melanggar ketentuan
 * pemakaian Actions dan bikin akun GitHub user kena suspend.
 * Detail setup: docs/CLOUD-RDP-SETUP.md
 *
 * Prasyarat (lihat teks hint di UI):
 *   - OAuth App GitHub dengan Device Flow (isi CLIENT_ID di GitHubDeviceAuth)
 *   - Minimal 1 Windows self-hosted runner dengan label 'xydesk-win',
 *     Tailscale terpasang (auth key via app atau env runner)
 *   - Tailscale app di HP, login ke tailnet yang sama
 */
public class CloudRdpActivity extends AppCompatActivity
{
	private static final String TAG = "XyDeskCloud";
	private static final String WORKFLOW_NAME = "XyDesk RDP Setup";
	private static final String ARTIFACT_CREDS = "rdp-credentials";
	private static final String ARTIFACT_HOSTKEY = "host-pubkey";

	private Button btnLogin;
	private Button btnCreate;
	private EditText nameInput;
	private EditText userInput;
	private EditText passInput;
	private EditText tsKeyInput;
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
		userInput = findViewById(R.id.input_rdp_user);
		passInput = findViewById(R.id.input_rdp_pass);
		tsKeyInput = findViewById(R.id.input_ts_key);
		loginStatus = findViewById(R.id.txt_login_status);
		statusView = findViewById(R.id.txt_cloud_status);
		progress = findViewById(R.id.cloud_progress);

		btnLogin.setOnClickListener(v -> doLogin());
		btnCreate.setOnClickListener(v -> doCreate());
	}

	// ------------------------------------------------------------------
	// 1) LOGIN (akun GitHub milik user sendiri)
	// ------------------------------------------------------------------

	private void doLogin()
	{
		if (!GitHubDeviceAuth.isConfigured())
		{
			AlertDialog.Builder b = new AlertDialog.Builder(this);
			b.setTitle("OAuth App belum di-set");
			b.setMessage("Fitur Cloud RDP butuh 1 OAuth App GitHub (sekali saja, "
			           + "dibuat developer):\n\n"
			           + "1. GitHub → Settings → Developer settings → OAuth Apps → New OAuth App\n"
			           + "2. Nama: XyDesk Remote, callback: https://localhost\n"
			           + "3. AKTIFKAN 'Device Flow'\n"
			           + "4. Salin Client ID ke GitHubDeviceAuth.CLIENT_ID\n\n"
			           + "Lalu build ulang. (Client secret TIDAK perlu.) User cukup "
			           + "login akun GitHub mereka masing-masing.");
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
					status("Login GitHub OK. Isi form, lalu Create & Setup.");
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
			status("Nama repo tidak valid (huruf kecil, angka, -, _; maks 39).");
			return;
		}
		String u = userInput.getText().toString().trim();
		if (u.isEmpty())
		{
			u = "xydesk";
		}
		if (!u.matches("[A-Za-z][A-Za-z0-9._-]{0,19}"))
		{
			status("Nama user RDP tidak valid (huruf/angka/._-; maks 20, mulai huruf).");
			return;
		}
		if (u.equalsIgnoreCase("Administrator") || u.equalsIgnoreCase("Guest")
		    || u.equalsIgnoreCase("DefaultAccount") || u.equalsIgnoreCase("krbtgt")
		    || u.equalsIgnoreCase("WDAGUtilityAccount"))
		{
			status("Nama user '" + u + "' dipakai Windows — pilih nama lain.");
			return;
		}
		final String rdpUser = u;

		final String rdpPass = passInput.getText().toString();
		if (!rdpPass.isEmpty())
		{
			if (!rdpPass.matches("[A-Za-z0-9!@#$%^&*._-]{12,64}"))
			{
				status("Password: min 12 karakter, boleh huruf/angka/!@#$%^&*._- (tanpa spasi/kutip).");
				return;
			}
			int cls = 0;
			if (rdpPass.matches(".*[a-z].*")) cls++;
			if (rdpPass.matches(".*[A-Z].*")) cls++;
			if (rdpPass.matches(".*[0-9].*")) cls++;
			if (rdpPass.matches(".*[!@#$%^&*._-].*")) cls++;
			if (cls < 3)
			{
				status("Password kurang kuat — campur minimal 3 dari: huruf kecil, huruf besar, angka, simbol.");
				return;
			}
			if (rdpPass.toLowerCase().contains(rdpUser.toLowerCase()))
			{
				status("Password jangan mengandung nama user.");
				return;
			}
		}

		final String tsKey = tsKeyInput.getText().toString().trim();
		if (!tsKey.isEmpty() && !tsKey.startsWith("tskey-"))
		{
			status("Key Tailscale biasanya diawali 'tskey-' (cek tailscale.com/admin/keys).");
			return;
		}

		setBusy(true, "Menyiapkan...");
		new Thread(() -> runPipeline(name, rdpUser, rdpPass, tsKey)).start();
	}

	private void runPipeline(String repoName, String rdpUser, String rdpPass, String tsKey)
	{
		try
		{
			status("1/6 Siapkan repo " + repoName + " di akun GitHub kamu...");
			if (gh.repoExists(repoName))
			{
				status("Repo sudah ada — pakai yang existing.");
			}
			else
			{
				gh.createRepo(repoName, "XyDesk Remote — cloud RDP (setup otomatis)");
				status("Repo public '" + repoName + "' dibuat di akun @" + gh.login() + ".");
			}
			String prevRunId = latestRunId(repoName);

			status("2/6 Generate kunci sekali-pakai + push template setup...");
			KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
			kpg.initialize(2048);
			KeyPair kp = kpg.generateKeyPair();
			PrivateKey appPriv = kp.getPrivate();
			gh.pushFile(repoName, "setup/pubkey.pem", spkiPem(kp.getPublic()),
			            "xydesk: add one-time credential pubkey");
			gh.pushFile(repoName, ".github/workflows/rdp-vm.yml",
			            asset("xydesk-cloud/rdp-vm.yml"),
			            "xydesk: add RDP setup workflow");
			gh.pushFile(repoName, "setup/setup-windows.ps1",
			            asset("xydesk-cloud/setup-windows.ps1"),
			            "xydesk: add Windows setup script");

			status("3/6 Fase prepare — minta kunci host (run cepat)...");
			gh.dispatchWorkflow(repoName, "rdp-vm.yml", "main", phaseInputs("prepare"));
			String runPrep = waitForRun(repoName, prevRunId);
			JSONObject prep = waitForCompletion(repoName, runPrep);
			requireSuccess(prep, repoName, runPrep);
			byte[] hostPub = downloadArtifactEntry(repoName, runPrep, ARTIFACT_HOSTKEY,
			                                       "host-pubkey.pem");
			if (hostPub == null)
			{
				throw new Exception("host-pubkey.pem tidak ditemukan di artifact phase prepare");
			}

			status("4/6 Enkripsi rahasia ke kunci host (repo public tetap aman)...");
			Cipher enc = Cipher.getInstance("RSA/ECB/OAEPWithSHA-1AndMGF1Padding");
			enc.init(Cipher.ENCRYPT_MODE, spkiPublic(new String(hostPub, StandardCharsets.UTF_8)));
			JSONObject blobs = new JSONObject();
			blobs.put("u", rsaB64(enc, rdpUser));
			blobs.put("p", rsaB64(enc, rdpPass == null ? "" : rdpPass));
			blobs.put("t", rsaB64(enc, tsKey == null ? "" : tsKey));
			gh.pushFile(repoName, "setup/secrets.enc", blobs.toString(0) + "\n",
			            "xydesk: add encrypted setup secrets");

			status("5/6 Fase setup — install RDP + Tailscale di host...");
			gh.dispatchWorkflow(repoName, "rdp-vm.yml", "main", phaseInputs("setup"));
			String runSetup = waitForRun(repoName, runPrep);
			status("Run #" + runSetup + " — menunggu selesai (bisa beberapa menit)...");
			JSONObject setupRun = waitForCompletion(repoName, runSetup);
			requireSuccess(setupRun, repoName, runSetup);

			status("6/6 Download kredensial RDP (ter-enskripsi)...");
			byte[] creds = downloadArtifactEntry(repoName, runSetup, ARTIFACT_CREDS,
			                                     "rdp-credentials.enc");
			if (creds != null)
			{
				Cipher dec = Cipher.getInstance("RSA/ECB/OAEPWithSHA-1AndMGF1Padding");
				dec.init(Cipher.DECRYPT_MODE, appPriv);
				creds = dec.doFinal(creds);
			}
			else
			{
				creds = downloadArtifactEntry(repoName, runSetup, ARTIFACT_CREDS,
				                              "rdp-credentials.json");
			}
			if (creds == null)
			{
				throw new Exception("rdp-credentials(.enc/.json) tidak ada di artifact");
			}
			JSONObject c = new JSONObject(new String(creds, StandardCharsets.UTF_8));
			String host = c.getString("host");
			int port = c.optInt("port", 3389);
			String user = c.getString("user");
			String pass = c.getString("password");

			status("Cek konektivitas " + host + ":" + port + " (butuh Tailscale aktif di HP)...");
			if (!reachable(host, port))
			{
				throw new Exception(host + ":" + port + " belum terjangkau. "
				                   + "Pastikan Tailscale di HP login ke tailnet yang "
				                   + "sama dengan host, lalu coba lagi.");
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

	private static JSONObject phaseInputs(String phase) throws Exception
	{
		JSONObject inputs = new JSONObject();
		inputs.put("phase", phase);
		return inputs;
	}

	private static void requireSuccess(JSONObject run, String repoName, String runId)
		throws Exception
	{
		String conclusion = run.optString("conclusion", "");
		if (!"success".equals(conclusion))
		{
			throw new Exception("Workflow gagal (" + conclusion
			                    + "). Cek Actions tab di repo " + repoName
			                    + " — kemungkinan label runner 'xydesk-win' belum ada.");
		}
	}

	private static String spkiPem(PublicKey pub)
	{
		return "-----BEGIN PUBLIC KEY-----\n"
		       + Base64.encodeToString(pub.getEncoded(), Base64.DEFAULT)
		       + "-----END PUBLIC KEY-----\n";
	}

	private static PublicKey spkiPublic(String pem) throws Exception
	{
		String b64 = pem.replace("-----BEGIN PUBLIC KEY-----", "")
		                .replace("-----END PUBLIC KEY-----", "")
		                .replaceAll("\\s", "");
		byte[] der = Base64.decode(b64, Base64.DEFAULT);
		java.security.spec.X509EncodedKeySpec spec =
			new java.security.spec.X509EncodedKeySpec(der);
		return java.security.KeyFactory.getInstance("RSA").generatePublic(spec);
	}

	private static String rsaB64(Cipher enc, String value) throws Exception
	{
		return Base64.encodeToString(
			enc.doFinal(value.getBytes(StandardCharsets.UTF_8)), Base64.NO_WRAP);
	}

	private String latestRunId(String repoName) throws Exception
	{
		JSONObject prev = gh.latestRun(repoName, WORKFLOW_NAME);
		return (prev == null) ? null : String.valueOf(prev.getLong("id"));
	}

	private byte[] downloadArtifactEntry(String repoName, String runId,
	                                     String artifactName, String entry) throws Exception
	{
		JSONObject art = gh.findArtifact(repoName, runId, artifactName);
		if (art == null)
		{
			return null;
		}
		byte[] zip = gh.downloadArtifactZip(repoName, runId, art.getLong("id"));
		return unzipEntry(zip, entry);
	}

	private String waitForRun(String repoName, String skipRunId) throws Exception
	{
		long deadline = System.currentTimeMillis() + 3 * 60 * 1000L;
		while (System.currentTimeMillis() < deadline)
		{
			JSONObject run = gh.latestRun(repoName, WORKFLOW_NAME);
			if (run != null)
			{
				String id = String.valueOf(run.getLong("id"));
				if (!id.equals(skipRunId))
				{
					return id;
				}
			}
			Thread.sleep(5000);
		}
		throw new Exception("Workflow baru belum mulai dalam 3 menit (cek workflow & branch main)");
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
