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
		// Initialize both durable logs before GlobalApp touches LibFreeRDP/JNI.
		// This preserves the last Java/native startup marker if JNI_OnLoad fails.
		id.xydesk.remote.security.CrashLog.INSTANCE.install(this);
		id.xydesk.remote.core.ConnectionLog.INSTANCE.init(this);
		id.xydesk.remote.core.ConnectionLog.INSTANCE.add("APP: entering GlobalApp.onCreate");
		try
		{
			com.freerdp.freerdpcore.services.LibFreeRDP.setNativeCallbackErrorSink(
			    message -> id.xydesk.remote.core.ConnectionLog.INSTANCE.add("JNI: " + message));
			super.onCreate();
			id.xydesk.remote.core.ConnectionLog.INSTANCE.add("APP: GlobalApp.onCreate completed");
		}
		catch (Throwable t)
		{
			id.xydesk.remote.core.ConnectionLog.INSTANCE.addThrowable("APP: GlobalApp.onCreate FAILED", t);
			id.xydesk.remote.security.CrashLog.INSTANCE.note(this,
			    "GlobalApp.onCreate failed: " + t.getClass().getName() + ": " + t.getMessage());
			if (t instanceof RuntimeException)
				throw (RuntimeException)t;
			if (t instanceof Error)
				throw (Error)t;
			throw new RuntimeException(t);
		}
	}
}
