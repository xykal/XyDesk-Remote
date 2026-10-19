"""Freeze the owner-selected connection/native paths while features are reintroduced.
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
                self.assertEqual(hashlib.sha256((ROOT/relative).read_bytes()).hexdigest(), expected)

    def test_initial_connect_is_not_requeued_inside_pre_started_onstart(self):
        activity = (ROOT/'client/Android/Studio/app/src/main/kotlin/id/xydesk/remote/ui/XyDeskSessionActivity.kt').read_text()
        self.assertNotIn('pendingForegroundProfile', activity)
        self.assertNotIn('captureForegroundReady', activity)
        self.assertIn('manager.connect(profile)', activity)
        session_ui = (ROOT/'client/Android/Studio/app/src/main/kotlin/id/xydesk/remote/ui/XyDeskSession.kt').read_text()
        self.assertIn('ConnectingScreen(', session_ui)

    def test_no_size_diet_or_dependency_removal_is_mixed_into_recovery(self):
        gradle = (ROOT/'client/Android/Studio/app/build.gradle').read_text()
        self.assertIn('play-services-ads', gradle)
        external = (ROOT/'client/Android/cmake/ExternalDeps.cmake').read_text()
        self.assertNotIn('StripJniLibs.cmake', external)
        native = (ROOT/'client/Android/Studio/freeRDPCore/src/main/cpp/CMakeLists.txt').read_text()
        self.assertNotIn('--strip-unneeded', native)

    def test_chat_polling_tracks_socket_not_http_success_or_quiet_room_age(self):
        screen = (ROOT/'client/Android/Studio/app/src/main/kotlin/id/xydesk/remote/ui/GlobalChatScreen.kt').read_text()
        self.assertIn('LaunchedEffect(verifiedGhLogin, socketConnected)', screen)
        self.assertIn('chatHistoryPollDelayMs(socketConnected = socketConnected)', screen)
        http = screen[screen.index('suspend fun fetchHistoryOverHttp()'):screen.index('    LaunchedEffect(Unit)', screen.index('suspend fun fetchHistoryOverHttp()'))]
        self.assertNotIn('socketConnected =', http)
        self.assertIn('chatAccountSnapshotIsCurrent(requestAccount, verifiedGhLogin)', http)
        self.assertNotIn('lastWsActivityMs', screen)
