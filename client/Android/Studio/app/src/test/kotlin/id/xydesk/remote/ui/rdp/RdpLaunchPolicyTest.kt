package id.xydesk.remote.ui.rdp

import org.junit.Assert.*
import org.junit.Test

class RdpLaunchPolicyTest {
    private fun errors(duration: String = "360", runner: String = "windows-2022", host: String = "rdpfree", user: String = "xyadmin", access: String = "tailscale", portHost: String = "", port: String = "") =
        validateRdpLaunch(duration, runner, host, user, access, portHost, port)

    @Test fun defaultsAndLinuxSshAreValid() {
        assertTrue(errors().isEmpty())
        assertTrue(errors(runner = "ubuntu-24.04", host = "my-linux-desktop", user = "xy_admin").isEmpty())
    }
    @Test fun invalidOrUnsupportedSettingsAreRejected() {
        for (value in listOf("", "NaN", "14", "361", "99999999999999")) assertFalse(errors(duration = value).isEmpty())
        assertFalse(errors(runner = "custom-image").isEmpty())
        assertFalse(errors(access = "unknown").isEmpty())
    }
    @Test fun hostAndUserRejectShellSyntaxAndWindowsLengthOverflow() {
        for (value in listOf("bad host", "x;echo", "-host", "host-", "1234", "abcdefghijklmnop"))
            assertFalse(errors(host = value).isEmpty())
        for (value in listOf("admin;echo", "a\n", "-admin", "name/user", "a".repeat(21)))
            assertFalse(errors(user = value).isEmpty())
    }
    @Test fun optionalPortmapFieldsNeverAcceptUrlOrInvalidPorts() {
        assertTrue(errors(access = "portmap").isEmpty())
        assertTrue(errors(access = "portmap", portHost = "example.org", port = "3389").isEmpty())
        for (host in listOf("https://example.org", "a/b", "a..b", "a.-b", "a:22"))
            assertFalse(errors(access = "portmap", portHost = host).isEmpty())
        for (port in listOf("0", "65536", "abc", "-1")) assertFalse(errors(access = "portmap", port = port).isEmpty())
    }
    @Test fun unknownSecretPermissionsCannotMeanReady() {
        val setup = XyRdpSetupStatus(true, true, true, setOf("RDP_PASSWORD", "TAILSCALE_AUTH_KEY"), emptyList())
        assertFalse(setup.isReadyToStart)
        assertTrue(setup.copy(secretsVerified = true).isReadyToStart)
        assertFalse(setup.copy(secretsVerified = true, missingSecrets = listOf("RDP_PASSWORD")).isReadyToStart)
    }
}
