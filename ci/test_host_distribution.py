"""Source contract checks only. Does NOT compile/run an EXE or verify PE imports."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]

class HostDistributionTest(unittest.TestCase):
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

    def test_chat_has_no_voice_reader_or_global_mic_switch(self):
        source = (ROOT / 'client/Android/Studio/app/src/main/kotlin/id/xydesk/remote/ui/GlobalChatScreen.kt').read_text()
        for removed in ('voiceEnabled', 'TextToSpeech', 'VOICE ON', 'VOICE OFF', 'KEY_VOICE_ENABLED'):
            self.assertNotIn(removed, source)
        self.assertIn('playChatNotificationTone', source)

if __name__ == '__main__':
    unittest.main()
