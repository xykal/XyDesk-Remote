"""Additive safety contracts; verifies direct transport policy and static host build."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]


class CandidateSafetyTest(unittest.TestCase):
    def test_android_direct_transport_policy_guards_entrypoints(self):
        core = ROOT / 'client/Android/Studio/core-rdp/src/main/kotlin/id/xydesk/remote/core'
        policy = (core / 'LegacyDirectTransportPolicy.kt').read_text()
        self.assertIn('fun mayStart(): Boolean', policy)
        for name in ['XyDirectConsoleStreamClient.kt', 'XyRawGameInputBridge.kt', 'QuicAudioBridge.kt']:
            body = (core / name).read_text().split('    fun start() {', 1)[1]
            self.assertLess(body.index('LegacyDirectTransportPolicy.mayStart()'), body.index('running.compareAndSet'))
        discovery = (core / 'XyDirectConsoleStreamClient.kt').read_text().split('fun discoverHosts(', 1)[1]
        self.assertLess(discovery.index('LegacyDirectTransportPolicy.mayStart()'), discovery.index('DatagramSocket('))

    def test_audio_bridge_respects_policy_on_mic_toggle(self):
        audio = (ROOT / 'client/Android/Studio/core-rdp/src/main/kotlin/id/xydesk/remote/core/QuicAudioBridge.kt').read_text()
        setter = audio.split('fun setVoiceMicEnabled(enabled: Boolean) {', 1)[1].split('fun start()', 1)[0]
        self.assertLess(setter.index('LegacyDirectTransportPolicy.mayStart()'), setter.index('micActive = enabled'))
        self.assertIn('micActive = false', setter)

    def test_release_is_draft_only_with_scoped_evidence(self):
        workflow = (ROOT / '.github/workflows/build-apk.yml').read_text()
        self.assertIn('needs: [compile-check, build-release]', workflow)
        self.assertIn('draft: true', workflow)
        self.assertIn('bash ci/build_windows_host.sh', workflow)
        self.assertIn('ci/verify_windows_host.py', workflow)

    def test_builder_is_static_and_contains_readiness(self):
        builder = (ROOT / 'ci/build_windows_host.sh').read_text()
        self.assertIn('-DXYDESK_STATIC', builder)
        self.assertIn('-static -static-libgcc -static-libstdc++', builder)
        self.assertIn('host_readiness.cpp', builder)
        self.assertIn('-luuid -lpropsys', builder)
