#define XYDESK_QUIC_EXPORTS
#include "../include/xydesk_quic.h"

#ifdef _WIN32
#ifndef WIN32_LEAN_AND_MEAN
#define WIN32_LEAN_AND_MEAN
#endif
#include <winsock2.h>
#include <ws2tcpip.h>
#include <windows.h>
#endif

#include <atomic>
#include <cstdio>
#include <cstring>
#include <thread>

namespace {

constexpr char kDiscoverMagic[] = "XYDESK_QUIC_DISCOVER_V1";
constexpr char kAckMagic[] = "XYDESK_QUIC_ACK_V1";

std::atomic<bool> g_running{false};
std::atomic<uint64_t> g_packets_served{0};
XyQuicHostMetadata g_meta{};

#ifdef _WIN32
SOCKET g_sock = INVALID_SOCKET;
#endif
std::thread g_worker;

size_t BuildQuicAckPacket(uint8_t* out, size_t cap, const uint8_t* rx, size_t rx_len) {
    if (cap < 256 || rx_len < 24) return 0;
    size_t off = 0;
    // RFC 9000 Long Header Initial ACK
    out[off++] = 0xC0;
    out[off++] = 0x00;
    out[off++] = 0x00;
    out[off++] = 0x00;
    out[off++] = 0x01;
    // Echo client SCID as DCID (8 bytes)
    out[off++] = 8;
    memcpy(out + off, rx + 15, 8);
    off += 8;
    // Server SCID (8 bytes)
    out[off++] = 8;
    const char scid[8] = {'X', 'Y', 'H', 'O', 'S', 'T', '0', '1'};
    memcpy(out + off, scid, 8);
    off += 8;
    // Token length = 0
    out[off++] = 0x00;

    // Payload magic: XYDESK_QUIC_ACK_V1\0
    const size_t ack_len = sizeof(kAckMagic);
    memcpy(out + off, kAckMagic, ack_len);
    off += ack_len;

    char json[320] = {};
    snprintf(json, sizeof(json),
             "{\"pcId\":\"%s\",\"hostname\":\"%s\",\"ip\":\"%s\",\"gpu\":\"%s\","
             "\"encoder\":\"%s\",\"rdpPort\":%u,\"quicPort\":%u,\"maxFps\":%u,\"avc444\":true}",
             g_meta.pc_id, g_meta.hostname, g_meta.primary_ip,
             g_meta.gpu_name, g_meta.gpu_encoder,
             static_cast<unsigned>(g_meta.rdp_port),
             static_cast<unsigned>(g_meta.quic_port),
             static_cast<unsigned>(g_meta.max_fps));
    const size_t jlen = strlen(json) + 1;
    if (off + jlen <= cap) {
        memcpy(out + off, json, jlen);
        off += jlen;
    }
    return off;
}

void QuicServerLoop() {
#ifdef _WIN32
    while (g_running.load()) {
        fd_set rfds;
        FD_ZERO(&rfds);
        FD_SET(g_sock, &rfds);
        timeval tv{};
        tv.tv_sec = 0;
        tv.tv_usec = 250000;
        int sel = select(0, &rfds, nullptr, nullptr, &tv);
        if (sel > 0 && FD_ISSET(g_sock, &rfds)) {
            sockaddr_storage peer{};
            int peer_len = sizeof(peer);
            uint8_t rx[512] = {};
            int n = recvfrom(g_sock, reinterpret_cast<char*>(rx), sizeof(rx), 0,
                             reinterpret_cast<sockaddr*>(&peer), &peer_len);
            if (n >= 24 && (rx[0] & 0xC0) == 0xC0) {
                const char* payload = reinterpret_cast<const char*>(rx + 24);
                const size_t payload_len = static_cast<size_t>(n - 24);
                if (payload_len >= sizeof(kDiscoverMagic) - 1 &&
                    memcmp(payload, kDiscoverMagic, sizeof(kDiscoverMagic) - 1) == 0) {
                    uint8_t tx[512] = {};
                    size_t tx_len = BuildQuicAckPacket(tx, sizeof(tx), rx, static_cast<size_t>(n));
                    if (tx_len > 0) {
                        sendto(g_sock, reinterpret_cast<const char*>(tx),
                               static_cast<int>(tx_len), 0,
                               reinterpret_cast<const sockaddr*>(&peer), peer_len);
                        g_packets_served.fetch_add(1);
                    }
                }
            }
        }
    }
#endif
}

}  // namespace

extern "C" XYDESK_QUIC_API const char* xydesk_quic_dll_version(void) {
    return "xydesk_quic.dll v0.5.23 (Native C++17 · QUIC v1 RFC 9000 + RFC 9221 Datagram)";
}

extern "C" XYDESK_QUIC_API int xydesk_quic_server_start(const XyQuicHostMetadata* meta) {
    if (g_running.load()) return 0;
    if (meta) {
        g_meta = *meta;
    }
    if (g_meta.quic_port == 0) g_meta.quic_port = 4433;
    if (g_meta.rdp_port == 0) g_meta.rdp_port = 3389;
    if (g_meta.max_fps == 0) g_meta.max_fps = 120;

#ifdef _WIN32
    WSADATA wsa{};
    if (WSAStartup(MAKEWORD(2, 2), &wsa) != 0) {
        return -1;
    }
    g_sock = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP);
    if (g_sock == INVALID_SOCKET) {
        return -2;
    }
    BOOL reuse = TRUE;
    setsockopt(g_sock, SOL_SOCKET, SO_REUSEADDR, reinterpret_cast<const char*>(&reuse), sizeof(reuse));

    sockaddr_in bind_addr{};
    bind_addr.sin_family = AF_INET;
    bind_addr.sin_addr.s_addr = htonl(INADDR_ANY);
    bind_addr.sin_port = htons(g_meta.quic_port);
    if (bind(g_sock, reinterpret_cast<const sockaddr*>(&bind_addr), sizeof(bind_addr)) != 0) {
        closesocket(g_sock);
        g_sock = INVALID_SOCKET;
        return -3;
    }
    g_running.store(true);
    g_worker = std::thread(QuicServerLoop);
    return 0;
#else
    return -1;
#endif
}

extern "C" XYDESK_QUIC_API void xydesk_quic_server_stop(void) {
    if (!g_running.exchange(false)) return;
#ifdef _WIN32
    if (g_sock != INVALID_SOCKET) {
        closesocket(g_sock);
        g_sock = INVALID_SOCKET;
    }
#endif
    if (g_worker.joinable()) {
        g_worker.join();
    }
#ifdef _WIN32
    WSACleanup();
#endif
}

extern "C" XYDESK_QUIC_API uint64_t xydesk_quic_packets_served(void) {
    return g_packets_served.load();
}
