package id.xydesk.remote;

/**
 * XyDesk Remote — application entry point.
 *
 * Extends the FreeRDP core GlobalApp so that session lifecycle handling,
 * LibFreeRDP event dispatch and screen on/off disconnect handling keep
 * working unchanged. Custom XyDesk behaviour (credential vault, HUD
 * registry, telemetry) is layered on top in later milestones.
 */
public class XyApp extends com.freerdp.freerdpcore.application.GlobalApp
{
}
