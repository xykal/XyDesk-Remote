"""Source contract checks only. Does NOT compile/run an EXE or verify PE imports."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]

class HostDistributionTest(unittest.TestCase):
    def test_native_mic_change_waits_for_terminal_cleanup_and_uses_guarded_uri(self):
        manager = (ROOT / 'client/Android/Studio/core-rdp/src/main/kotlin/id/xydesk/remote/core/SessionManager.kt').read_text()
        self.assertEqual(manager.count('if (!cleanupTerminalSession(inst)) return\n                if (restartForNativeMicrophoneChange(inst, p)) return'), 2)
        uri = manager.split('private fun sessionUri(', 1)[1].split('fun connect(', 1)[0]
        self.assertLess(uri.index('sessionMicrophoneControl.apply'), uri.index('.withCapturePermissions('))
        self.assertLess(uri.index('.withCapturePermissions('), uri.index('.forCaptureVisibility('))
        self.assertIn('sessionMicrophoneControl.isCurrent(restart)', manager)
        self.assertIn('sessionMicrophoneControl.cancel(suppressActivation = true)', manager)
        request = manager.split('fun requestNativeMicrophoneChange(', 1)[1].split('private fun restartForNativeMicrophoneChange', 1)[0]
        for guard in ('!captureForegroundAllowed', '!isCurrent(expectedInstance)', 'RECORD_AUDIO', 'options.pcConnectMode', 'SessionState.Connected', 'expectedGeneration != sessionMicrophoneControl.generation()'):
            self.assertIn(guard, request)
        self.assertNotIn('RdpOptions.set', request)
        self.assertIn('setVoiceMicEnabled(false)', request)
        panel = (ROOT / 'client/Android/Studio/app/src/main/kotlin/id/xydesk/remote/ui/RdpReadinessPanel.kt').read_text()
        self.assertIn('Konfirmasi sambung ulang', panel)
        self.assertIn('BUKAN live mute', panel)

    def test_reported_cancel_and_security_retry_do_not_hide_failures_or_downgrade(self):
        manager = (ROOT / 'client/Android/Studio/core-rdp/src/main/kotlin/id/xydesk/remote/core/SessionManager.kt').read_text()
        failure = manager.split('override fun onConnectionFailure()', 1)[1].split('override fun onDisconnected()', 1)[0]
        self.assertLess(failure.index('cleanupTerminalSession(inst)'), failure.index('RdpFailure.isExpectedCancellation'))
        self.assertIn('transition(SessionState.Disconnected())', failure)
        self.assertIn('RdpConnectionDiagnostics.unreachableMessage', manager)
        uri = manager.split('private fun sessionUri(', 1)[1].split('fun connect(', 1)[0]
        self.assertIn('.forHandshakeCompatibility(compatHandshakeStage)', uri)
        self.assertNotIn('tlsSecLevel = 0', uri)
        self.assertNotIn('securityProtocol = XySecurityProtocol.TLS', uri)

    def test_one_cmake_application_without_companion_libraries(self):
        cmake = (ROOT / 'host-agent/CMakeLists.txt').read_text()
        self.assertEqual(cmake.count('add_executable('), 1)
        self.assertNotIn('add_library(', cmake)
        for source in ('main_host.cpp', 'xydesk_quic.cpp', 'xydesk_host_core.cpp'):
            self.assertIn(source, cmake)
        self.assertIn('XYDESK_STATIC', cmake)

    def test_release_uses_one_host_executable_no_companions(self):
        workflow = (ROOT / '.github/workflows/build-apk.yml').read_text()
        self.assertIn('release-apks/XyDeskRemoteHost.exe', workflow)
        for removed in ('XyDeskRemoteHost-Standalone.exe', 'xydesk_quic.dll', 'xydesk_host_core.dll', 'XyDesk-Remote-Host-Agent-win64.zip'):
            self.assertNotIn(removed, workflow)
        command = (ROOT / 'ci/build_windows_host.sh').read_text()
        self.assertIn('-DXYDESK_STATIC', command)
        self.assertIn('-static ', command)
        self.assertEqual(command.count('-o "$OUT/XyDeskRemoteHost.exe"'), 1)

    def test_gate_does_not_build_host_binary(self):
        workflow = (ROOT / '.github/workflows/build-apk.yml').read_text()
        gate = workflow.split('  compile-check:', 1)[1].split('  build-debug:', 1)[0]
        self.assertNotIn('mingw32-g++', gate)
        self.assertNotIn('bash ci/build_windows_host.sh', gate)
        self.assertIn('bash -n ci/build_windows_host.sh', gate)

    def test_host_does_not_modify_password_or_run_remote_setup(self):
        source = (ROOT / 'host-agent/src/main_host.cpp').read_text()
        for removed in ('net.exe user', 'irm ', 'onmic.ps1', 'SHA256:', 'g_password', 'xydesk_host_apply_windows_policies();'):
            self.assertNotIn(removed, source)
        self.assertIn('if (g_legacy_private_udp) g_quic_rc =', source)
        self.assertIn('xydesk_show_readiness(hwnd)', source)
        doctor = (ROOT / 'host-agent/src/host_readiness.cpp').read_text()
        self.assertIn('EnumAudioEndpoints', doctor)
        self.assertIn('GetDefaultAudioEndpoint', doctor)
        self.assertIn('Process32FirstW', doctor)
        for forbidden in ('RegSetValue', 'CreateProcess', 'URLDownloadToFile', 'WinHttpSendRequest'):
            self.assertNotIn(forbidden, doctor)

    def test_stream_windows_headers_do_not_replace_std_min_max(self):
        source = (ROOT / 'host-agent/src/xydesk_quic.cpp').read_text()
        self.assertLess(source.index('#define NOMINMAX'), source.index('#include <windows.h>'))

    def test_legacy_web_setup_fails_closed_without_system_changes(self):
        for name in ('onmic.ps1', 'host.ps1', 'XyDesk-Remote-Agent-Setup.ps1',
                     'XyDesk-RDP-OnMic-Setup.bat', 'XyDeskHost-Setup.bat', 'agent.bat'):
            source = (ROOT / 'web' / name).read_text().lower()
            for forbidden in ('invoke-webrequest', 'start-process', 'stop-service', 'set-service',
                              'new-itemproperty', 'set-itemproperty', 'netsh ', 'new-netfirewallrule',
                              'executionpolicy', 'iex', 'runas'):
                self.assertNotIn(forbidden, source, name)
            self.assertIn('exit /b 1' if name.endswith('.bat') else 'exit 1', source)
        for name in ('host.html', 'index.html'):
            source = (ROOT / 'web' / name).read_text()
            for forbidden in ('Enkripsi QUIC TLS 1.3', 'Clean ID &amp; Password',
                              'href="/XyDeskRemoteHost-Standalone.exe"', 'irm https://'):
                self.assertFalse(forbidden in source, f'{name} still advertises {forbidden}')

    def test_rdp_capture_permission_guard_is_at_every_connection_boundary(self):
        base = ROOT / 'client/Android/Studio'
        manager = (base / 'core-rdp/src/main/kotlin/id/xydesk/remote/core/SessionManager.kt').read_text()
        boundary = manager.split('private fun sessionUri(', 1)[1].split('fun connect(', 1)[0]
        self.assertIn('.withCapturePermissions(', boundary)
        for permission in ('RECORD_AUDIO', 'CAMERA'):
            self.assertIn('appContext.checkSelfPermission(android.Manifest.permission.' + permission, boundary)
        activity = (base / 'app/src/main/kotlin/id/xydesk/remote/ui/XyDeskSessionActivity.kt').read_text()
        callback = activity.split('private val permissionLauncher =', 1)[1].split('private fun profileFromIntent', 1)[0]
        self.assertNotIn('.write(', callback)

    def test_native_release_commits_only_after_success(self):
        base = ROOT / 'client/Android/Studio'
        java = base / 'freeRDPCore/src/main/java/com/freerdp/freerdpcore'
        global_app = (java / 'application/GlobalApp.java').read_text()
        self.assertIn('SessionReleaseTransaction.release(sessionMap, instance, LibFreeRDP::freeInstance)', global_app)
        native = (java / 'services/LibFreeRDP.java').read_text()
        self.assertIn('NativeWorkerTermination.releaseAfterTerminal(', native)
        manager = (base / 'core-rdp/src/main/kotlin/id/xydesk/remote/core/SessionManager.kt').read_text()
        terminal = manager.split('private fun cleanupTerminalSession(', 1)[1].split('/** Probe TCP', 1)[0]
        self.assertLess(terminal.index('GlobalApp.freeSession(inst)'), terminal.index('core = null'))
        self.assertIn('"native_cleanup_unconfirmed"', terminal)
        self.assertEqual(manager.count('if (!cleanupTerminalSession(inst)) return'), 3)

    def test_background_capture_gate_is_connected_to_activity_and_native_start(self):
        base = ROOT / 'client/Android/Studio'
        manager = (base / 'core-rdp/src/main/kotlin/id/xydesk/remote/core/SessionManager.kt').read_text()
        self.assertIn('captureForegroundAllowed = false', manager)
        self.assertIn('.forCaptureVisibility(captureForegroundAllowed)', manager)
        start = manager.split('try {\n                synchronized(lifecycleLock)', 1)[1].split('session.connect(appContext)', 1)[0]
        self.assertIn('nativeCaptureRequested && !captureForegroundAllowed', start)
        activity = (base / 'app/src/main/kotlin/id/xydesk/remote/ui/XyDeskSessionActivity.kt').read_text()
        self.assertIn('manager.setCaptureForegroundAllowed(true)', activity.split('override fun onStart()', 1)[1].split('override fun onResume()', 1)[0])
        self.assertIn('manager.setCaptureForegroundAllowed(false)', activity.split('override fun onStop()', 1)[1].split('override fun onDestroy()', 1)[0])
        self.assertIn('pendingForegroundProfile = profile', activity)
        self.assertIn('if (!captureForegroundReady ||', activity)
        self.assertIn('userInitiatedDisconnect ||', start)

    def test_chat_has_no_voice_reader_or_global_mic_switch(self):
        source = (ROOT / 'client/Android/Studio/app/src/main/kotlin/id/xydesk/remote/ui/GlobalChatScreen.kt').read_text()
        for removed in ('voiceEnabled', 'TextToSpeech', 'VOICE ON', 'VOICE OFF', 'KEY_VOICE_ENABLED'):
            self.assertNotIn(removed, source)
        self.assertIn('playChatNotificationTone', source)

if __name__ == '__main__':
    unittest.main()
