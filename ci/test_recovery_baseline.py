"""Freeze the owner-selected connection/native paths while features are reintroduced.
Seleksi 2026-10-31 (arah produk: XyDesk-Remote = klien RDP murni):
jalur yang dilindungi = sesi RDP, surface render, dan native FreeRDP.
These are source regression checks, NOT Android/Windows runtime proof.
"""
import hashlib
import json
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


class RecoveryBaselineTest(unittest.TestCase):
    def test_connect_render_and_native_inputs_match_owner_baseline(self):
        lock = json.loads((ROOT/'docs/recovery/PROTECTED-CONNECTION-PATHS.json').read_text())
        for relative, expected in lock['files'].items():
            with self.subTest(path=relative):
                data = (ROOT/relative).read_bytes()
                if relative.endswith('/app/src/main/AndroidManifest.xml'):
                    # ChatOutboxJobService was removed with the deleted chat
                    # feature. Its manifest declaration must stay absent:
                    # Android kept trying to instantiate the ghost service
                    # from a persisted job and crash-looped (1.1.24 fix).
                    self.assertNotIn(b'ChatOutboxJobService', data)
                self.assertEqual(hashlib.sha256(data).hexdigest(), expected)

    def test_initial_connect_is_not_requeued_inside_pre_started_onstart(self):
        activity = (ROOT/'client/Android/Studio/app/src/main/kotlin/id/xydesk/remote/ui/XyDeskSessionActivity.kt').read_text()
        self.assertNotIn('pendingForegroundProfile', activity)
        self.assertNotIn('captureForegroundReady', activity)
        self.assertIn('manager.connect(profile)', activity)

    def test_failed_connect_never_auto_exits_to_home(self):
        # Kegagalan connect wajib berhenti di layar sesi dengan opsi coba
        # lagi. Menutup dialog error (scrim/Back/tombol Tutup) tidak boleh
        # memanggil exitSession dan Back di Activity tidak boleh finish()
        # pada state Error/Disconnected.
        activity = (ROOT/'client/Android/Studio/app/src/main/kotlin/id/xydesk/remote/ui/XyDeskSessionActivity.kt').read_text()
        self.assertIn('backState is SessionState.Error || backState is SessionState.Disconnected', activity)

    def test_no_size_diet_or_dependency_removal_is_mixed_into_recovery(self):
        gradle = (ROOT/'client/Android/Studio/app/build.gradle').read_text()
        self.assertIn('play-services-ads', gradle)
        external = (ROOT/'client/Android/cmake/ExternalDeps.cmake').read_text()
        self.assertNotIn('StripJniLibs.cmake', external)
        native = (ROOT/'client/Android/Studio/freeRDPCore/src/main/cpp/CMakeLists.txt').read_text()
        self.assertNotIn('--strip-unneeded', native)

    def test_route_motion_never_keeps_two_chat_or_media_trees_alive(self):
        motion = (ROOT/'client/Android/Studio/app/src/main/kotlin/id/xydesk/remote/ui/theme/XyLightMotion.kt').read_text()
        self.assertNotIn('Crossfade', motion)
        self.assertIn('key(target) { content(target) }', motion)
        self.assertIn('Animatable(1f)', motion)
        self.assertNotIn('rememberInfiniteTransition', motion)
