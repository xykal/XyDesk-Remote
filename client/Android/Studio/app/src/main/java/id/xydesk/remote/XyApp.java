package id.xydesk.remote;

import android.os.Bundle;

/**
 * XyDesk Remote — application entry point.
 *
 * Extends the FreeRDP core GlobalApp so that session lifecycle handling,
 * LibFreeRDP event dispatch and screen on/off disconnect handling keep
 * working unchanged. Layers on: global crash logger (diagnosa untuk user).
 */
public class XyApp extends com.freerdp.freerdpcore.application.GlobalApp
{
	@Override public void onCreate()
	{
		super.onCreate();
		id.xydesk.remote.security.CrashLog.INSTANCE.install(this);
		id.xydesk.remote.core.ConnectionLog.INSTANCE.init(this);
	}
}
