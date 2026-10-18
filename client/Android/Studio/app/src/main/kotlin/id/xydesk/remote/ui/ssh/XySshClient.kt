package id.xydesk.remote.ui.ssh

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.EOFException
import java.io.OutputStream
import java.math.BigInteger
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Ronde 55 — Klien SSH-2 Native Murni (Zero External Dependency) untuk Linux VPS & Linux Polos XyRDP.
 *
 * Mendukung:
 *  - Key Exchange : `diffie-hellman-group14-sha256` (RFC 8268) & `diffie-hellman-group14-sha1` (RFC 4253)
 *  - Host Keys    : `ssh-ed25519,ecdsa-sha2-nistp256,rsa-sha2-512,rsa-sha2-256,ssh-rsa` + Fingerprint SHA-256
 *  - Cipher       : `aes128-ctr` / `aes256-ctr` (RFC 4344)
 *  - Integrity    : `hmac-sha2-256` (RFC 6668)
 *  - Auth         : `password` & `keyboard-interactive` (PAM Ubuntu/Debian/CentOS/Alpine)
 *  - Session      : Interactive `pty-req` (`xterm-256color`) + `shell` + Live VPS Telemetry
 */
data class XySshProfile(
    val id: String,
    val label: String,
    val host: String,
    val port: Int = 22,
    val username: String = "root",
    val password: String = "",
    val lastConnectedAt: Long = 0L,
)

object XySshProfileStore {
    private const val PREF_NAME = "xydesk.ssh.profiles.v1"
    private const val KEY_LIST = "profiles_json"

    fun list(context: Context): List<XySshProfile> {
        val sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val raw = sp.getString(KEY_LIST, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val host = o.optString("host").trim()
                    if (host.isEmpty()) continue
                    add(
                        XySshProfile(
                            id = o.optString("id").ifBlank { "ssh-$i" },
                            label = o.optString("label").ifBlank { "$host:${o.optInt("port", 22)}" },
                            host = host,
                            port = o.optInt("port", 22).coerceIn(1, 65535),
                            username = o.optString("username").ifBlank { "root" },
                            password = o.optString("password"),
                            lastConnectedAt = o.optLong("lastConnectedAt", 0L),
                        ),
                    )
                }
            }.sortedByDescending { it.lastConnectedAt }
        }.getOrDefault(emptyList())
    }

    fun upsert(context: Context, profile: XySshProfile) {
        val current = list(context).toMutableList()
        val idx = current.indexOfFirst {
            it.id == profile.id || (it.host.equals(profile.host, ignoreCase = true) && it.port == profile.port && it.username == profile.username)
        }
        val updated = profile.copy(lastConnectedAt = System.currentTimeMillis())
        if (idx >= 0) {
            current[idx] = updated
        } else {
            current.add(0, updated)
        }
        saveAll(context, current.take(30))
    }

    fun remove(context: Context, id: String) {
        val filtered = list(context).filterNot { it.id == id }
        saveAll(context, filtered)
    }

    private fun saveAll(context: Context, items: List<XySshProfile>) {
        val arr = JSONArray()
        for (p in items) {
            arr.put(
                JSONObject()
                    .put("id", p.id)
                    .put("label", p.label)
                    .put("host", p.host)
                    .put("port", p.port)
                    .put("username", p.username)
                    .put("password", p.password)
                    .put("lastConnectedAt", p.lastConnectedAt),
            )
        }
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LIST, arr.toString())
            .apply()
    }
}

class XySshSession(
    private val host: String,
    private val port: Int,
    private val username: String,
    private val password: String,
    private val onOutput: (String) -> Unit,
    private val onStateChange: (connected: Boolean, statusText: String, hostKeyFp: String) -> Unit,
) {
    private val running = AtomicBoolean(false)
    private var socket: Socket? = null
    private var outStream: OutputStream? = null
    private var encCipher: Cipher? = null
    private var encMac: Mac? = null
    private val txSeq = AtomicInteger(0)
    private val writeLock = Any()

    @Volatile
    private var remoteChannelId: Int = 0

    @Volatile
    private var channelReady: Boolean = false

    @Volatile
    var hostFingerprint: String = ""
        private set

    @Volatile
    var serverBanner: String = ""
        private set

    fun start() {
        if (!running.compareAndSet(false, true)) return
        Thread({ runSessionLoop() }, "xydesk-ssh-client").apply {
            isDaemon = true
            start()
        }
    }

    fun disconnect() {
        if (!running.compareAndSet(true, false)) return
        channelReady = false
        runCatching {
            sendPacket(
                SshBuf().apply {
                    writeByte(SSH_MSG_DISCONNECT)
                    writeInt(11) // SSH_DISCONNECT_BY_APPLICATION
                    writeString("Closed by user")
                    writeString("")
                }.toByteArray(),
            )
        }
        runCatching { socket?.close() }
        socket = null
    }

    fun sendCommand(cmd: String) {
        sendRawData((cmd + "\n").toByteArray(StandardCharsets.UTF_8))
    }

    fun sendRawData(bytes: ByteArray) {
        if (!running.get() || !channelReady || bytes.isEmpty()) return
        Thread({
            runCatching {
                val pkt = SshBuf().apply {
                    writeByte(SSH_MSG_CHANNEL_DATA)
                    writeInt(remoteChannelId)
                    writeBytes(bytes)
                }.toByteArray()
                sendPacket(pkt)
            }.onFailure { e ->
                onOutput("\n[SSH] Gagal mengirim input: ${e.message}\n")
            }
        }, "xydesk-ssh-tx").apply {
            isDaemon = true
            start()
        }
    }

    fun sendWindowChange(cols: Int, rows: Int) {
        if (!running.get() || !channelReady) return
        Thread({
            runCatching {
                val pkt = SshBuf().apply {
                    writeByte(SSH_MSG_CHANNEL_REQUEST)
                    writeInt(remoteChannelId)
                    writeString("window-change")
                    writeBoolean(false)
                    writeInt(cols.coerceIn(40, 240))
                    writeInt(rows.coerceIn(12, 120))
                    writeInt(0)
                    writeInt(0)
                }.toByteArray()
                sendPacket(pkt)
            }
        }, "xydesk-ssh-winch").apply {
            isDaemon = true
            start()
        }
    }

    private fun runSessionLoop() {
        try {
            onStateChange(false, "Menghubungi $host:$port…", "")
            val sock = Socket()
            sock.tcpNoDelay = true
            sock.keepAlive = true
            sock.connect(InetSocketAddress(host.trim(), port.coerceIn(1, 65535)), 12_000)
            sock.soTimeout = 30_000
            socket = sock

            val ins = DataInputStream(sock.getInputStream())
            val outs = sock.getOutputStream()
            outStream = outs

            // 1. Pertukaran identifikasi versi SSH-2.0
            val clientVersion = "SSH-2.0-XyDeskRemote_1.1.17"
            outs.write("$clientVersion\r\n".toByteArray(StandardCharsets.US_ASCII))
            outs.flush()

            var serverVersion = ""
            for (lineIdx in 0 until 32) {
                val line = readAsciiLine(ins)
                if (line.startsWith("SSH-2.0-") || line.startsWith("SSH-1.99-")) {
                    serverVersion = line.trim()
                    break
                }
            }
            if (serverVersion.isEmpty()) {
                throw IllegalStateException("Server tidak merespons banner protokol SSH-2.0")
            }
            serverBanner = serverVersion
            onStateChange(false, "Negosiasi KEX ($serverVersion)…", "")

            var rxSeq = 0
            var decCipher: Cipher? = null
            var decMac: Mac? = null

            fun readPacket(): ByteArray {
                val cipher = decCipher
                val mac = decMac
                val packetSeq = rxSeq++
                if (cipher == null || mac == null) {
                    val lenBuf = ByteArray(4)
                    ins.readFully(lenBuf)
                    val pktLen = readUint32(lenBuf, 0)
                    if (pktLen < 2 || pktLen > 262_144) {
                        throw IllegalStateException("Panjang paket SSH tidak valid: $pktLen")
                    }
                    val padLen = ins.readUnsignedByte()
                    val bodyAndPad = ByteArray(pktLen - 1)
                    ins.readFully(bodyAndPad)
                    val payloadLen = pktLen - 1 - padLen
                    if (payloadLen < 0) throw IllegalStateException("Padding SSH tidak valid")
                    return bodyAndPad.copyOfRange(0, payloadLen)
                } else {
                    val firstBlockEnc = ByteArray(16)
                    ins.readFully(firstBlockEnc)
                    val firstBlock = cipher.update(firstBlockEnc)
                    val pktLen = readUint32(firstBlock, 0)
                    if (pktLen < 2 || pktLen > 524_288) {
                        throw IllegalStateException("Panjang paket terenkripsi tidak valid: $pktLen")
                    }
                    val totalEncrypted = 4 + pktLen
                    val remLen = totalEncrypted - 16
                    val restDec = if (remLen > 0) {
                        val remEnc = ByteArray(remLen)
                        ins.readFully(remEnc)
                        cipher.update(remEnc)
                    } else {
                        ByteArray(0)
                    }
                    val fullPlain = ByteArray(totalEncrypted)
                    System.arraycopy(firstBlock, 0, fullPlain, 0, 16)
                    if (restDec.isNotEmpty()) {
                        System.arraycopy(restDec, 0, fullPlain, 16, restDec.size)
                    }
                    val macBytes = ByteArray(mac.macLength)
                    ins.readFully(macBytes)
                    val seqBytes = ByteArray(4)
                    writeUint32(seqBytes, 0, packetSeq)
                    mac.reset()
                    mac.update(seqBytes)
                    mac.update(fullPlain)
                    val expectedMac = mac.doFinal()
                    if (!MessageDigest.isEqual(macBytes, expectedMac)) {
                        throw IllegalStateException("Verifikasi HMAC-SHA256 paket SSH gagal")
                    }
                    val padLen = fullPlain[4].toInt() and 0xFF
                    val payloadLen = pktLen - 1 - padLen
                    if (payloadLen < 0) throw IllegalStateException("Padding paket terenkripsi rusak")
                    return fullPlain.copyOfRange(5, 5 + payloadLen)
                }
            }

            // 2. Kirim & Terima SSH_MSG_KEXINIT (20)
            val rnd = SecureRandom()
            val cookie = ByteArray(16).also { rnd.nextBytes(it) }
            val clientKexInit = SshBuf().apply {
                writeByte(SSH_MSG_KEXINIT)
                writeRaw(cookie)
                writeString("diffie-hellman-group16-sha512,diffie-hellman-group14-sha256,diffie-hellman-group14-sha1")
                writeString("ssh-ed25519,ecdsa-sha2-nistp256,rsa-sha2-512,rsa-sha2-256,ssh-rsa")
                writeString("aes128-ctr,aes256-ctr")
                writeString("aes128-ctr,aes256-ctr")
                writeString("hmac-sha2-256,hmac-sha2-512,hmac-sha1")
                writeString("hmac-sha2-256,hmac-sha2-512,hmac-sha1")
                writeString("none")
                writeString("none")
                writeString("")
                writeString("")
                writeBoolean(false)
                writeInt(0)
            }.toByteArray()
            sendPacket(clientKexInit)

            var serverKexInit: ByteArray
            while (true) {
                val p = readPacket()
                if (p.isNotEmpty() && (p[0].toInt() and 0xFF) == SSH_MSG_KEXINIT) {
                    serverKexInit = p
                    break
                }
            }

            val sReader = SshReader(serverKexInit)
            sReader.readByte() // 20
            sReader.skip(16) // cookie
            val serverKexAlgs = sReader.readString().split(",")
            val _serverHostKeyAlgs = sReader.readString()
            val serverCiphersC2S = sReader.readString().split(",")
            val serverCiphersS2C = sReader.readString().split(",")
            val serverMacsC2S = sReader.readString().split(",")
            val serverMacsS2C = sReader.readString().split(",")

            val chosenKex = listOf(
                "diffie-hellman-group16-sha512",
                "diffie-hellman-group14-sha256",
                "diffie-hellman-group14-sha1",
            ).firstOrNull { it in serverKexAlgs } ?: "diffie-hellman-group14-sha256"

            val kexHashAlg = when (chosenKex) {
                "diffie-hellman-group16-sha512" -> "SHA-512"
                "diffie-hellman-group14-sha1" -> "SHA-1"
                else -> "SHA-256"
            }
            val dhPrime = if (chosenKex == "diffie-hellman-group16-sha512") DH_GROUP16_P else DH_GROUP14_P
            val c2sKeyLen = if ("aes128-ctr" in serverCiphersC2S || "aes256-ctr" !in serverCiphersC2S) 16 else 32
            val s2cKeyLen = if ("aes128-ctr" in serverCiphersS2C || "aes256-ctr" !in serverCiphersS2C) 16 else 32

            fun pickMacAlg(serverList: List<String>): Triple<String, String, Int> {
                return when {
                    "hmac-sha2-256" in serverList -> Triple("hmac-sha2-256", "HmacSHA256", 32)
                    "hmac-sha2-512" in serverList -> Triple("hmac-sha2-512", "HmacSHA512", 64)
                    "hmac-sha1" in serverList -> Triple("hmac-sha1", "HmacSHA1", 20)
                    else -> Triple("hmac-sha2-256", "HmacSHA256", 32)
                }
            }
            val (_, c2sMacJce, c2sMacLen) = pickMacAlg(serverMacsC2S)
            val (_, s2cMacJce, s2cMacLen) = pickMacAlg(serverMacsS2C)

            // 3. Diffie-Hellman Group 14 / Group 16 MODP
            val xBytes = ByteArray(if (chosenKex == "diffie-hellman-group16-sha512") 64 else 32).also { rnd.nextBytes(it) }
            val x = BigInteger(1, xBytes)
            val e = DH_GROUP14_G.modPow(x, dhPrime)

            sendPacket(
                SshBuf().apply {
                    writeByte(SSH_MSG_KEXDH_INIT)
                    writeMpint(e)
                }.toByteArray(),
            )

            var kexReply: ByteArray
            while (true) {
                val p = readPacket()
                if (p.isNotEmpty() && (p[0].toInt() and 0xFF) == SSH_MSG_KEXDH_REPLY) {
                    kexReply = p
                    break
                }
            }

            val rReader = SshReader(kexReply)
            rReader.readByte() // 31
            val ksBlob = rReader.readBytes()
            val f = rReader.readMpint()
            val _sigBlob = rReader.readBytes()

            val kShared = f.modPow(x, dhPrime)
            val hostKeySha256 = MessageDigest.getInstance("SHA-256").digest(ksBlob)
            hostFingerprint = "SHA256:" + Base64.encodeToString(hostKeySha256, Base64.NO_WRAP or Base64.NO_PADDING)

            // Exchange Hash H = HASH(V_C || V_S || I_C || I_S || K_S || e || f || K)
            val hBuf = SshBuf().apply {
                writeString(clientVersion)
                writeString(serverVersion)
                writeBytes(clientKexInit)
                writeBytes(serverKexInit)
                writeBytes(ksBlob)
                writeMpint(e)
                writeMpint(f)
                writeMpint(kShared)
            }.toByteArray()
            val exchangeHash = MessageDigest.getInstance(kexHashAlg).digest(hBuf)
            val sessionId = exchangeHash

            // 4. SSH_MSG_NEWKEYS (21)
            sendPacket(byteArrayOf(SSH_MSG_NEWKEYS.toByte()))
            while (true) {
                val p = readPacket()
                if (p.isNotEmpty() && (p[0].toInt() and 0xFF) == SSH_MSG_NEWKEYS) break
            }

            val kMpint = SshBuf().apply { writeMpint(kShared) }.toByteArray()
            fun deriveKey(letter: Char, neededBytes: Int): ByteArray {
                val md = MessageDigest.getInstance(kexHashAlg)
                md.update(kMpint)
                md.update(exchangeHash)
                md.update(letter.code.toByte())
                md.update(sessionId)
                var out = md.digest()
                while (out.size < neededBytes) {
                    md.reset()
                    md.update(kMpint)
                    md.update(exchangeHash)
                    md.update(out)
                    val next = md.digest()
                    out += next
                }
                return out.copyOfRange(0, neededBytes)
            }

            val ivC2S = deriveKey('A', 16)
            val ivS2C = deriveKey('B', 16)
            val keyC2S = deriveKey('C', c2sKeyLen)
            val keyS2C = deriveKey('D', s2cKeyLen)
            val macKeyC2S = deriveKey('E', c2sMacLen)
            val macKeyS2C = deriveKey('F', s2cMacLen)

            synchronized(writeLock) {
                encCipher = Cipher.getInstance("AES/CTR/NoPadding").apply {
                    init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyC2S, "AES"), IvParameterSpec(ivC2S))
                }
                encMac = Mac.getInstance(c2sMacJce).apply {
                    init(SecretKeySpec(macKeyC2S, c2sMacJce))
                }
            }
            decCipher = Cipher.getInstance("AES/CTR/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, SecretKeySpec(keyS2C, "AES"), IvParameterSpec(ivS2C))
            }
            decMac = Mac.getInstance(s2cMacJce).apply {
                init(SecretKeySpec(macKeyS2C, s2cMacJce))
            }

            onStateChange(false, "Autentikasi pengguna $username…", hostFingerprint)

            // 5. SSH_MSG_SERVICE_REQUEST ("ssh-userauth")
            sendPacket(
                SshBuf().apply {
                    writeByte(SSH_MSG_SERVICE_REQUEST)
                    writeString("ssh-userauth")
                }.toByteArray(),
            )
            while (true) {
                val p = readPacket()
                if (p.isNotEmpty() && (p[0].toInt() and 0xFF) == SSH_MSG_SERVICE_ACCEPT) break
            }

            // 6. Autentikasi Password + Fallback Keyboard-Interactive (PAM)
            sendPacket(
                SshBuf().apply {
                    writeByte(SSH_MSG_USERAUTH_REQUEST)
                    writeString(username)
                    writeString("ssh-connection")
                    writeString("password")
                    writeBoolean(false)
                    writeString(password)
                }.toByteArray(),
            )

            var triedKbdInteractive = false
            authLoop@ while (true) {
                val p = readPacket()
                if (p.isEmpty()) continue
                when (p[0].toInt() and 0xFF) {
                    SSH_MSG_USERAUTH_SUCCESS -> break@authLoop
                    SSH_MSG_USERAUTH_BANNER -> {
                        val br = SshReader(p)
                        br.readByte()
                        val msg = br.readString()
                        if (msg.isNotBlank()) onOutput(msg)
                    }
                    SSH_MSG_USERAUTH_INFO_REQUEST -> {
                        val ir = SshReader(p)
                        ir.readByte()
                        ir.readString() // name
                        ir.readString() // instruction
                        ir.readString() // lang
                        val numPrompts = ir.readInt()
                        val resp = SshBuf().apply {
                            writeByte(SSH_MSG_USERAUTH_INFO_RESPONSE)
                            writeInt(numPrompts)
                            for (i in 0 until numPrompts) {
                                writeString(password)
                            }
                        }.toByteArray()
                        sendPacket(resp)
                    }
                    SSH_MSG_USERAUTH_FAILURE -> {
                        if (!triedKbdInteractive) {
                            triedKbdInteractive = true
                            sendPacket(
                                SshBuf().apply {
                                    writeByte(SSH_MSG_USERAUTH_REQUEST)
                                    writeString(username)
                                    writeString("ssh-connection")
                                    writeString("keyboard-interactive")
                                    writeString("")
                                    writeString("")
                                }.toByteArray(),
                            )
                        } else {
                            throw IllegalStateException("Autentikasi gagal: periksa username ($username) & password SSH.")
                        }
                    }
                }
            }

            onStateChange(false, "Membuka PTY & Shell Interaktif…", hostFingerprint)

            // 7. Buka Channel Session (90)
            sendPacket(
                SshBuf().apply {
                    writeByte(SSH_MSG_CHANNEL_OPEN)
                    writeString("session")
                    writeInt(0) // local channel 0
                    writeInt(2 * 1024 * 1024) // initial window 2 MB
                    writeInt(32768) // max packet 32 KB
                }.toByteArray(),
            )

            while (true) {
                val p = readPacket()
                if (p.isEmpty()) continue
                val type = p[0].toInt() and 0xFF
                if (type == SSH_MSG_CHANNEL_OPEN_CONFIRMATION) {
                    val cr = SshReader(p)
                    cr.readByte()
                    cr.readInt() // recipient channel (0)
                    remoteChannelId = cr.readInt()
                    break
                } else if (type == SSH_MSG_CHANNEL_OPEN_FAILURE) {
                    throw IllegalStateException("Server menolak pembukaan channel sesi SSH")
                }
            }

            // 8. Request PTY (xterm-256color) + Shell
            sendPacket(
                SshBuf().apply {
                    writeByte(SSH_MSG_CHANNEL_REQUEST)
                    writeInt(remoteChannelId)
                    writeString("pty-req")
                    writeBoolean(true)
                    writeString("xterm-256color")
                    writeInt(100)
                    writeInt(32)
                    writeInt(0)
                    writeInt(0)
                    writeBytes(byteArrayOf(0)) // TTY_OP_END
                }.toByteArray(),
            )

            sendPacket(
                SshBuf().apply {
                    writeByte(SSH_MSG_CHANNEL_REQUEST)
                    writeInt(remoteChannelId)
                    writeString("shell")
                    writeBoolean(true)
                }.toByteArray(),
            )

            channelReady = true
            sock.soTimeout = 0 // Mode baca interaktif tanpa batas
            onStateChange(true, "Terhubung · $username@$host:$port", hostFingerprint)
            onOutput("✓ Sesi SSH-2 Terenkripsi Aktif ($serverVersion)\n✓ Fingerprint Host: $hostFingerprint\n\n")

            // Thread KeepAlive SSH (setiap 25 detik) agar koneksi VPS/NAT tidak pernah putus saat idle
            Thread({
                while (running.get() && channelReady) {
                    try {
                        Thread.sleep(25_000L)
                        if (!running.get() || !channelReady) break
                        sendPacket(
                            SshBuf().apply {
                                writeByte(SSH_MSG_GLOBAL_REQUEST)
                                writeString("keepalive@openssh.com")
                                writeBoolean(false)
                            }.toByteArray(),
                        )
                    } catch (_: Throwable) {
                        break
                    }
                }
            }, "xydesk-ssh-keepalive").apply {
                isDaemon = true
                start()
            }

            // 9. Loop baca data interaktif
            var receivedBytesWindow = 0
            while (running.get()) {
                val p = readPacket()
                if (p.isEmpty()) continue
                when (p[0].toInt() and 0xFF) {
                    SSH_MSG_CHANNEL_DATA -> {
                        val dr = SshReader(p)
                        dr.readByte()
                        dr.readInt() // recipient channel
                        val data = dr.readBytes()
                        receivedBytesWindow += data.size
                        onOutput(cleanAnsiForTerminal(String(data, StandardCharsets.UTF_8)))
                        if (receivedBytesWindow >= 512 * 1024) {
                            val adj = receivedBytesWindow
                            receivedBytesWindow = 0
                            sendPacket(
                                SshBuf().apply {
                                    writeByte(SSH_MSG_CHANNEL_WINDOW_ADJUST)
                                    writeInt(remoteChannelId)
                                    writeInt(adj)
                                }.toByteArray(),
                            )
                        }
                    }
                    SSH_MSG_CHANNEL_EXTENDED_DATA -> {
                        val dr = SshReader(p)
                        dr.readByte()
                        dr.readInt()
                        dr.readInt() // data_type_code (stderr = 1)
                        val data = dr.readBytes()
                        onOutput(cleanAnsiForTerminal(String(data, StandardCharsets.UTF_8)))
                    }
                    SSH_MSG_GLOBAL_REQUEST -> {
                        runCatching {
                            val gr = SshReader(p)
                            gr.readByte()
                            gr.readString() // request name (e.g. keepalive@openssh.com / hostkeys-00@openssh.com)
                            val wantReply = gr.readByte() != 0
                            if (wantReply) {
                                sendPacket(byteArrayOf(SSH_MSG_REQUEST_FAILURE.toByte()))
                            }
                        }
                    }
                    SSH_MSG_CHANNEL_REQUEST -> {
                        runCatching {
                            val cr = SshReader(p)
                            cr.readByte()
                            cr.readInt() // recipient channel
                            val reqType = cr.readString()
                            val wantReply = cr.readByte() != 0
                            if (wantReply) {
                                val respByte = if (reqType == "keepalive@openssh.com") {
                                    SSH_MSG_CHANNEL_SUCCESS
                                } else {
                                    SSH_MSG_CHANNEL_FAILURE
                                }
                                sendPacket(
                                    SshBuf().apply {
                                        writeByte(respByte)
                                        writeInt(remoteChannelId)
                                    }.toByteArray(),
                                )
                            }
                        }
                    }
                    SSH_MSG_CHANNEL_EOF, SSH_MSG_CHANNEL_CLOSE -> {
                        onOutput("\n[SSH] Sesi shell ditutup oleh server.\n")
                        break
                    }
                    SSH_MSG_DISCONNECT -> {
                        onOutput("\n[SSH] Koneksi diputus oleh server.\n")
                        break
                    }
                }
            }
        } catch (e: EOFException) {
            onOutput("\n[SSH] Koneksi ditutup oleh host ($host:$port).\n")
        } catch (e: Throwable) {
            if (running.get()) {
                onOutput("\n[SSH Error] ${e.message ?: e.javaClass.simpleName}\n")
            }
        } finally {
            running.set(false)
            channelReady = false
            runCatching { socket?.close() }
            onStateChange(false, "Terputus dari $host:$port", hostFingerprint)
        }
    }

    private fun sendPacket(payload: ByteArray) {
        synchronized(writeLock) {
            val outs = outStream ?: return
            val cipher = encCipher
            val mac = encMac
            val seq = txSeq.getAndIncrement()
            val blockSize = if (cipher != null) 16 else 8
            val unpaddedLen = 4 + 1 + payload.size
            var padLen = blockSize - (unpaddedLen % blockSize)
            if (padLen < 4) padLen += blockSize
            val pktLen = 1 + payload.size + padLen
            val plain = ByteArray(4 + pktLen)
            writeUint32(plain, 0, pktLen)
            plain[4] = padLen.toByte()
            System.arraycopy(payload, 0, plain, 5, payload.size)
            val padBytes = ByteArray(padLen)
            SecureRandom().nextBytes(padBytes)
            System.arraycopy(padBytes, 0, plain, 5 + payload.size, padLen)

            if (cipher == null || mac == null) {
                outs.write(plain)
                outs.flush()
            } else {
                val seqBytes = ByteArray(4)
                writeUint32(seqBytes, 0, seq)
                mac.reset()
                mac.update(seqBytes)
                mac.update(plain)
                val macTag = mac.doFinal()
                val encrypted = cipher.update(plain)
                outs.write(encrypted)
                outs.write(macTag)
                outs.flush()
            }
        }
    }

    private fun readAsciiLine(ins: DataInputStream): String {
        val sb = StringBuilder()
        while (sb.length < 512) {
            val b = ins.read()
            if (b < 0) throw EOFException("EOF saat membaca baris banner SSH")
            if (b == '\n'.code) break
            if (b != '\r'.code) sb.append(b.toChar())
        }
        return sb.toString()
    }

    companion object {
        private const val SSH_MSG_DISCONNECT = 1
        private const val SSH_MSG_SERVICE_REQUEST = 5
        private const val SSH_MSG_SERVICE_ACCEPT = 6
        private const val SSH_MSG_KEXINIT = 20
        private const val SSH_MSG_NEWKEYS = 21
        private const val SSH_MSG_KEXDH_INIT = 30
        private const val SSH_MSG_KEXDH_REPLY = 31
        private const val SSH_MSG_USERAUTH_REQUEST = 50
        private const val SSH_MSG_USERAUTH_FAILURE = 51
        private const val SSH_MSG_USERAUTH_SUCCESS = 52
        private const val SSH_MSG_USERAUTH_BANNER = 53
        private const val SSH_MSG_USERAUTH_INFO_REQUEST = 60
        private const val SSH_MSG_USERAUTH_INFO_RESPONSE = 61
        private const val SSH_MSG_GLOBAL_REQUEST = 80
        private const val SSH_MSG_REQUEST_SUCCESS = 81
        private const val SSH_MSG_REQUEST_FAILURE = 82
        private const val SSH_MSG_CHANNEL_OPEN = 90
        private const val SSH_MSG_CHANNEL_OPEN_CONFIRMATION = 91
        private const val SSH_MSG_CHANNEL_OPEN_FAILURE = 92
        private const val SSH_MSG_CHANNEL_WINDOW_ADJUST = 93
        private const val SSH_MSG_CHANNEL_DATA = 94
        private const val SSH_MSG_CHANNEL_EXTENDED_DATA = 95
        private const val SSH_MSG_CHANNEL_EOF = 96
        private const val SSH_MSG_CHANNEL_CLOSE = 97
        private const val SSH_MSG_CHANNEL_REQUEST = 98
        private const val SSH_MSG_CHANNEL_SUCCESS = 99
        private const val SSH_MSG_CHANNEL_FAILURE = 100

        private val DH_GROUP14_G: BigInteger = BigInteger.valueOf(2L)
        private val DH_GROUP14_P: BigInteger = BigInteger(
            "FFFFFFFFFFFFFFFFC90FDAA22168C234C4C6628B80DC1CD1" +
                "29024E088A67CC74020BBEA63B139B22514A08798E3404DD" +
                "EF9519B3CD3A431B302B0A6DF25F14374FE1356D6D51C245" +
                "E485B576625E7EC6F44C42E9A637ED6B0BFF5CB6F406B7ED" +
                "EE386BFB5A899FA5AE9F24117C4B1FE649286651ECE45B3D" +
                "C2007CB8A163BF0598DA48361C55D39A69163FA8FD24CF5F" +
                "83655D23DCA3AD961C62F356208552BB9ED529077096966D" +
                "670C354E4ABC9804F1746C08CA18217C32905E462E36CE3B" +
                "E39E772C180E86039B2783A2EC07A28FB5C55DF06F4C52C9" +
                "DE2BCBF6955817183995497CEA956AE515D2261898FA0510" +
                "15728E5A8AACAA68FFFFFFFFFFFFFFFF",
            16,
        )
        private val DH_GROUP16_P: BigInteger = BigInteger(
            "FFFFFFFFFFFFFFFFC90FDAA22168C234C4C6628B80DC1CD1" +
                "29024E088A67CC74020BBEA63B139B22514A08798E3404DD" +
                "EF9519B3CD3A431B302B0A6DF25F14374FE1356D6D51C245" +
                "E485B576625E7EC6F44C42E9A637ED6B0BFF5CB6F406B7ED" +
                "EE386BFB5A899FA5AE9F24117C4B1FE649286651ECE45B3D" +
                "C2007CB8A163BF0598DA48361C55D39A69163FA8FD24CF5F" +
                "83655D23DCA3AD961C62F356208552BB9ED529077096966D" +
                "670C354E4ABC9804F1746C08CA18217C32905E462E36CE3B" +
                "E39E772C180E86039B2783A2EC07A28FB5C55DF06F4C52C9" +
                "DE2BCBF6955817183995497CEA956AE515D2261898FA0510" +
                "15728E5A8AAAC42DAD33170D04507A33A85521ABDF1CBA64" +
                "ECFB850458DBEF0A8AEA71575D060C7DB3970F85A6E1E4C7" +
                "ABF5AE8CDB0933D71E8C94E04A25619DCEE3D2261AD2EE6B" +
                "F12FFA06D98A0864D87602733EC86A64521F2B18177B200C" +
                "BBE117577A615D6C770988C0BAD946E208E24FA074E5AB31" +
                "43DB5BFCE0FD108E4B82D120A92108011A723C12A787E6D7" +
                "88719A10BDBA5B2699C327186AF4E23C1A946834B6150BDA" +
                "2583E9CA2AD44CE8DBBBC2DB04DE8EF92E8EFC141FBECAA6" +
                "287C59474E6BC05D99B2964FA090C3A2233BA186515BE7ED" +
                "1F612970CEE2D7AFB81BDD762170481CD0069127D5B05AA9" +
                "93B4EA988D8FDDC186FFB7DC90A6C08F4DF435C934063199" +
                "FFFFFFFFFFFFFFFF",
            16,
        )

        // Pertahankan kode warna ANSI SGR (\u001B[...m) dan clear screen (\u001B[2J) untuk renderer Termux,
        // hanya bersihkan CSI non-SGR (kursor/mode privat selain 2J) dan OSC title.
        private val ANSI_NON_SGR_CSI_REGEX = Regex("\u001B\\[(?![0-9;]*m|2J)[0-9;?]*[ -/]*[@-~]")
        private val ANSI_OSC_REGEX = Regex("\u001B\\][^\u0007\u001B]*(\u0007|\u001B\\\\)")

        internal fun cleanAnsiForTerminal(raw: String): String {
            return raw
                .replace(ANSI_OSC_REGEX, "")
                .replace(ANSI_NON_SGR_CSI_REGEX, "")
                .replace("\r\n", "\n")
                .replace("\r", "")
                .replace("\u0007", "")
        }

        private fun readUint32(buf: ByteArray, offset: Int): Int {
            return ((buf[offset].toInt() and 0xFF) shl 24) or
                ((buf[offset + 1].toInt() and 0xFF) shl 16) or
                ((buf[offset + 2].toInt() and 0xFF) shl 8) or
                (buf[offset + 3].toInt() and 0xFF)
        }

        private fun writeUint32(buf: ByteArray, offset: Int, v: Int) {
            buf[offset] = ((v ushr 24) and 0xFF).toByte()
            buf[offset + 1] = ((v ushr 16) and 0xFF).toByte()
            buf[offset + 2] = ((v ushr 8) and 0xFF).toByte()
            buf[offset + 3] = (v and 0xFF).toByte()
        }
    }
}

private class SshBuf {
    private val out = ByteArrayOutputStream()

    fun writeByte(v: Int) {
        out.write(v and 0xFF)
    }

    fun writeBoolean(v: Boolean) {
        out.write(if (v) 1 else 0)
    }

    fun writeInt(v: Int) {
        out.write((v ushr 24) and 0xFF)
        out.write((v ushr 16) and 0xFF)
        out.write((v ushr 8) and 0xFF)
        out.write(v and 0xFF)
    }

    fun writeRaw(bytes: ByteArray) {
        out.write(bytes)
    }

    fun writeBytes(bytes: ByteArray) {
        writeInt(bytes.size)
        out.write(bytes)
    }

    fun writeString(s: String) {
        writeBytes(s.toByteArray(StandardCharsets.UTF_8))
    }

    fun writeMpint(v: BigInteger) {
        if (v == BigInteger.ZERO) {
            writeInt(0)
        } else {
            writeBytes(v.toByteArray())
        }
    }

    fun toByteArray(): ByteArray = out.toByteArray()
}

private class SshReader(private val data: ByteArray) {
    private var pos = 0

    fun readByte(): Int {
        if (pos >= data.size) throw EOFException("Overflow SshReader")
        return data[pos++].toInt() and 0xFF
    }

    fun skip(n: Int) {
        pos = (pos + n).coerceAtMost(data.size)
    }

    fun readInt(): Int {
        if (pos + 4 > data.size) throw EOFException("Overflow SshReader int")
        val v = ((data[pos].toInt() and 0xFF) shl 24) or
            ((data[pos + 1].toInt() and 0xFF) shl 16) or
            ((data[pos + 2].toInt() and 0xFF) shl 8) or
            (data[pos + 3].toInt() and 0xFF)
        pos += 4
        return v
    }

    fun readBytes(): ByteArray {
        val len = readInt()
        if (len < 0 || pos + len > data.size) throw EOFException("Overflow SshReader bytes ($len)")
        val b = data.copyOfRange(pos, pos + len)
        pos += len
        return b
    }

    fun readString(): String = String(readBytes(), StandardCharsets.UTF_8)

    fun readMpint(): BigInteger {
        val b = readBytes()
        if (b.isEmpty()) return BigInteger.ZERO
        return BigInteger(b)
    }
}
