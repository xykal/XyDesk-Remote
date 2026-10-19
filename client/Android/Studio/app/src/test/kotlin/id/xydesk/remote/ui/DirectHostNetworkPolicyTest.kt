package id.xydesk.remote.ui

import org.junit.Assert.*
import org.junit.Test

class DirectHostNetworkPolicyTest {
    @Test fun privateLanAndTailscaleFormsOnly() {
        listOf("10.1.2.3", "192.168.1.10", "172.16.0.2", "172.31.255.254", "100.64.0.1", "100.127.1.2", "pc.tailnet.ts.net")
            .forEach { assertTrue(it, isPrivateDirectHost(it)) }
        listOf("", "8.8.8.8", "127.0.0.1", "0.0.0.0", "172.15.0.1", "172.32.0.1", "100.63.0.1", "100.128.0.1", "192.168.1.999", "https://pc.tailnet.ts.net", "pc.ts.net.evil.test")
            .forEach { assertFalse(it, isPrivateDirectHost(it)) }
    }
}
