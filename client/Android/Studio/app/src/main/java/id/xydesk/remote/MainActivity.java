package id.xydesk.remote;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.freerdp.freerdpcore.presentation.SessionActivity;

/**
 * M0 connection screen — "like MS Remote Desktop": host/IP + port +
 * username + password (+ optional domain).
 *
 * The form builds a standard RDP URI:
 *
 *   rdp://[user]@[host][:port]/[?p=pass][&domain=DOM]
 *
 * and hands it to the FreeRDP core SessionActivity, which maps it to
 * FreeRDP client arguments (/v:, /u:, /p:, /domain:). Any other FreeRDP
 * switch can be appended as a query parameter later.
 */
public class MainActivity extends AppCompatActivity
{
	private static final String TAG = "XyDeskRemote";
	private static final int DEFAULT_PORT = 3389;

	private EditText hostInput;
	private EditText portInput;
	private EditText userInput;
	private EditText passInput;
	private EditText domainInput;

	@Override protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		hostInput = findViewById(R.id.input_host);
		portInput = findViewById(R.id.input_port);
		userInput = findViewById(R.id.input_user);
		passInput = findViewById(R.id.input_pass);
		domainInput = findViewById(R.id.input_domain);

		Button connectButton = findViewById(R.id.btn_connect);
		connectButton.setOnClickListener(this::onConnectClicked);

		Button cloudButton = findViewById(R.id.btn_cloud);
		cloudButton.setOnClickListener(v ->
		    startActivity(new Intent(this, id.xydesk.remote.cloud.CloudRdpActivity.class)));
	}

	private void onConnectClicked(View v)
	{
		String host = hostInput.getText().toString().trim();
		if (host.isEmpty())
		{
			warn(hostInput, "Host / IP wajib diisi");
			return;
		}

		int port = DEFAULT_PORT;
		String portStr = portInput.getText().toString().trim();
		if (!portStr.isEmpty())
		{
			try
			{
				port = Integer.parseInt(portStr);
			}
			catch (NumberFormatException e)
			{
				warn(portInput, "Port tidak valid");
				return;
			}
		}
		if (port < 1 || port > 65535)
		{
			warn(portInput, "Port harus 1-65535");
			return;
		}

		String user = userInput.getText().toString().trim();
		String pass = passInput.getText().toString();
		String domain = domainInput.getText().toString().trim();

		Uri.Builder builder = new Uri.Builder().scheme("rdp");

		// authority = [user@]host[:port]
		// NOTE: di API 37 method host()/port()/userInfo()/encodedUserInfo()
		// dihapus dari Uri.Builder — encodedAuthority() tetap ada (API 1+).
		String authority = host;
		if (port != DEFAULT_PORT)
		{
			authority = host + ":" + port;
		}
		if (!user.isEmpty())
		{
			authority = user + "@" + authority;
		}
		builder.encodedAuthority(authority);

		if (!pass.isEmpty())
		{
			builder.appendQueryParameter("p", pass);
		}
		if (!domain.isEmpty())
		{
			builder.appendQueryParameter("domain", domain);
		}

		Uri uri = builder.build();
		Log.i(TAG, "connect -> " + uri);

		Intent intent = new Intent(this, SessionActivity.class);
		intent.setData(uri);
		startActivity(intent);
	}

	private void warn(EditText field, String msg)
	{
		field.setError(msg);
		Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
	}
}
