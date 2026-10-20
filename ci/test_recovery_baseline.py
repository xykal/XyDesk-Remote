"""Freeze the owner-selected connection/native paths while features are reintroduced.
These are source regression checks, NOT Android/Windows runtime proof.
"""
import hashlib
import json
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

OUTBOX_MANIFEST_ADDITION = '''        <!-- Additive chat outbox only; system JobScheduler requires its signature permission. -->
        <service
            android:name="id.xydesk.remote.ui.ChatOutboxJobService"
            android:permission="android.permission.BIND_JOB_SERVICE"
            android:exported="true" />

'''

class RecoveryBaselineTest(unittest.TestCase):
    def test_connect_render_and_native_inputs_match_owner_baseline(self):
        lock = json.loads((ROOT/'docs/recovery/PROTECTED-CONNECTION-PATHS.json').read_text())
        for relative, expected in lock['files'].items():
            with self.subTest(path=relative):
                data = (ROOT/relative).read_bytes()
                if relative.endswith('/app/src/main/AndroidManifest.xml'):
                    addition = OUTBOX_MANIFEST_ADDITION.encode()
                    self.assertEqual(data.count(addition), 1)
                    data = data.replace(addition, b'', 1)
                self.assertEqual(hashlib.sha256(data).hexdigest(), expected)

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

    def test_route_motion_never_keeps_two_chat_or_media_trees_alive(self):
        motion = (ROOT/'client/Android/Studio/app/src/main/kotlin/id/xydesk/remote/ui/theme/XyLightMotion.kt').read_text()
        self.assertNotIn('Crossfade', motion)
        self.assertIn('key(target) { content(target) }', motion)
        self.assertIn('Animatable(1f)', motion)
        self.assertNotIn('rememberInfiniteTransition', motion)

    def test_queue_controls_do_not_hide_failed_cancellation_or_export_media(self):
        ui = ROOT/'client/Android/Studio/app/src/main/kotlin/id/xydesk/remote/ui'
        screen = (ui/'GlobalChatScreen.kt').read_text()
        local_delete = screen[screen.index('    fun deleteMessageForMe('):screen.index('    fun applyIncomingMutation(')]
        self.assertIn('GlobalChatOutbox.cancelPending(', local_delete)
        self.assertLess(local_delete.index('GlobalChatOutbox.cancelPending('), local_delete.index('hideLocal(setOf(target.id, serverId))'))
        dialog = (ui/'GlobalChatOutboxDialog.kt').read_text()
        self.assertNotIn('attachment_data', dialog)
        self.assertNotIn('Base64', dialog)
        self.assertGreaterEqual(dialog.count('withContext(Dispatchers.IO)'), 5)
        disk = (ui/'AndroidChatOutboxDisk.kt').read_text()
        self.assertIn('root.listFiles() ?: error(', disk)
        outbox = (ui/'GlobalChatOutbox.kt').read_text()
        account_check = outbox[outbox.index('    internal fun checkAccount('):outbox.index('    internal fun retryPending(')]
        self.assertNotIn('resume(', account_check)
        self.assertNotIn('request("send",', account_check)
